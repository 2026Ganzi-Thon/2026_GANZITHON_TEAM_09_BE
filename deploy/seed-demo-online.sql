-- DEMO-03/04, SEC-13 (#58)
-- Execute manually AFTER deploy.sh reset and AFTER Flyway has created the tables.
-- NEVER register this as a Flyway migration; this is demonstration-only data.
-- Requires PostgreSQL 16. Transactional and safe to repeat only before real joins.
-- Never deletes or modifies existing application rows.

BEGIN;

-- Match OnlineMatchMutex: prevent a concurrent OnlineMatchService assignment.
SELECT pg_advisory_xact_lock(2026111709::bigint);
-- Also serialize against ordinary inserts during the short seed operation.
LOCK TABLE users, groups, group_members, online_queue IN SHARE ROW EXCLUSIVE MODE;

DO $demo_seed$
DECLARE
    seed_group_names text[] := ARRAY[
        '시연 산책단 1', '시연 산책단 2', '시연 산책단 3'
    ];
    seed_aliases text[] := ARRAY[
        '산책하는 수달', '조용한 토끼', '다정한 판다', '느긋한 고래'
    ];
    seeded_at timestamptz := clock_timestamp();
    group_id bigint;
    user_id bigint;
    g integer;
    m integer;
    already_seeded boolean;
BEGIN
    -- Only allow a no-op rerun if the DB is *exactly* in its original seed state.
    -- After real users join, rerunning this script is intentionally rejected.
    already_seeded :=
        (SELECT count(*) FROM groups) = 3
        AND (SELECT count(*) FROM users) = 12
        AND (SELECT count(*) FROM group_members) = 12
        AND (SELECT count(*) FROM online_queue) = 0
        AND (SELECT count(*) FROM groups
             WHERE is_demo AND type = 'ONLINE' AND archived_at IS NULL
               AND name = ANY(seed_group_names)) = 3
        AND (SELECT count(*) FROM users
             WHERE is_demo AND token_hash IS NULL AND deleted_at IS NULL) = 12
        AND NOT EXISTS (
            SELECT 1 FROM groups g
            WHERE (SELECT count(*) FROM group_members gm
                   WHERE gm.group_id = g.id AND gm.left_at IS NULL) <> 4
        )
        AND NOT EXISTS (
            SELECT 1 FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE NOT u.is_demo OR u.token_hash IS NOT NULL
               OR gm.left_at IS NOT NULL
               OR gm.display_name <> ALL(seed_aliases)
        );

    IF already_seeded THEN
        RAISE NOTICE 'DEMO-04 seed already exists (3 ONLINE groups, 12 users); no changes made.';
        RETURN;
    END IF;

    -- Fail closed instead of inserting demo accounts into a real/dirty database.
    IF EXISTS (SELECT 1 FROM users)
       OR EXISTS (SELECT 1 FROM groups)
       OR EXISTS (SELECT 1 FROM group_members)
       OR EXISTS (SELECT 1 FROM online_queue) THEN
        RAISE EXCEPTION 'Demo seed requires empty users/groups/group_members/online_queue (run approved reset first). Existing data was NOT changed.';
    END IF;

    FOR g IN 1..3 LOOP
        INSERT INTO groups (type, name, is_demo, created_at, archived_at)
        VALUES ('ONLINE', seed_group_names[g], TRUE, seeded_at, NULL)
        RETURNING id INTO group_id;

        FOR m IN 1..4 LOOP
            -- No session token: these demo accounts cannot authenticate.
            -- No adult confirmation is faked; account records are fictional.
            INSERT INTO users (
                nickname, token_hash, adult_confirmed, sensitive_consent_at,
                last_seen_at, is_demo, created_at, deleted_at
            ) VALUES (
                NULL, NULL, FALSE, NULL,
                seeded_at, TRUE, seeded_at, NULL
            ) RETURNING id INTO user_id;

            INSERT INTO group_members (
                group_id, user_id, display_name, joined_at, left_at, left_reason
            ) VALUES (
                group_id, user_id, seed_aliases[m], seeded_at, NULL, NULL
            );
        END LOOP;
    END LOOP;

    RAISE NOTICE 'DEMO-04 seed created: 3 ONLINE demo groups, 4 members each (2 free slots each).';
END
$demo_seed$;

COMMIT;

-- Read-only summary; the verification script performs stronger assertions.
SELECT g.id AS group_id, g.name,
       g.is_demo AS "isDemo",
       count(m.id) AS active_members,
       6 - count(m.id) AS free_slots
FROM groups g
LEFT JOIN group_members m ON m.group_id = g.id AND m.left_at IS NULL
WHERE g.is_demo AND g.type = 'ONLINE' AND g.archived_at IS NULL
GROUP BY g.id, g.name, g.is_demo
ORDER BY g.id;
