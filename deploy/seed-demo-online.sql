-- DEMO-04 (#58): 행사 직전 온라인 시연 그룹 3개와 캐릭터를 생성한다.
-- deploy.sh reset 이후, Flyway 마이그레이션이 완료된 PostgreSQL 16에서 수동 실행한다.
-- Flyway 마이그레이션으로 등록하지 않는다. 기존 업무 데이터는 수정·삭제하지 않는다.
-- 초기 상태에서만 재실행을 허용하며, 사용자가 참여한 뒤에는 재실행을 중단한다.

BEGIN;

-- OnlineMatchMutex와 동일한 키를 사용해 시연 계정 배정과 충돌하지 않게 한다.
SELECT pg_advisory_xact_lock(2026111709::bigint);
-- 시드 삽입 중 일반 INSERT와도 충돌하지 않도록 대상 테이블을 잠근다.
LOCK TABLE users, groups, group_members, online_queue, characters IN SHARE ROW EXCLUSIVE MODE;

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
    -- 시연 최초 상태 그대로일 때에만 재실행을 아무 변경 없이 끝낸다.
    -- 실제 사용자가 참여한 뒤에는 의도적으로 실행을 거부한다.
    already_seeded :=
        (SELECT count(*) FROM groups) = 3
        AND (SELECT count(*) FROM users) = 12
        AND (SELECT count(*) FROM group_members) = 12
        AND (SELECT count(*) FROM online_queue) = 0
        AND (SELECT count(*) FROM characters) = 3
        AND (SELECT count(DISTINCT name) FROM groups) = 3
        AND (SELECT count(DISTINCT user_id) FROM group_members) = 12
        AND (SELECT count(*) FROM groups
             WHERE is_demo AND type = 'ONLINE' AND archived_at IS NULL
               AND name = ANY(seed_group_names)) = 3
        AND (SELECT count(*) FROM users
             WHERE is_demo AND token_hash IS NULL AND deleted_at IS NULL) = 12
        AND NOT EXISTS (
            SELECT 1 FROM groups g
            WHERE (SELECT count(*) FROM group_members gm
                   WHERE gm.group_id = g.id AND gm.left_at IS NULL) <> 4
               OR (SELECT count(*) FROM characters c
                   WHERE c.group_id = g.id AND c.completed_on IS NULL
                     AND c.kind = 'SEED'
                     AND c.started_on = (g.created_at AT TIME ZONE 'Asia/Seoul')::date) <> 1
        )
        AND NOT EXISTS (
            SELECT 1 FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE NOT u.is_demo OR u.token_hash IS NOT NULL
               OR gm.left_at IS NOT NULL
               OR gm.display_name <> ALL(seed_aliases)
        );

    IF already_seeded THEN
        RAISE NOTICE '시연 시드가 이미 정상 상태입니다. ONLINE 그룹 3개·계정 12개·캐릭터 3개를 유지합니다.';
        RETURN;
    END IF;

    -- 운영 데이터가 있는 DB에는 시연 계정을 추가하지 않는다.
    IF EXISTS (SELECT 1 FROM users)
       OR EXISTS (SELECT 1 FROM groups)
       OR EXISTS (SELECT 1 FROM group_members)
       OR EXISTS (SELECT 1 FROM online_queue)
       OR EXISTS (SELECT 1 FROM characters) THEN
        RAISE EXCEPTION '시연 시드는 빈 users/groups/group_members/online_queue/characters에서만 실행할 수 있습니다. 승인된 reset을 먼저 실행하세요. 기존 데이터는 변경되지 않았습니다.';
    END IF;

    FOR g IN 1..3 LOOP
        INSERT INTO groups (type, name, is_demo, created_at, archived_at)
        VALUES ('ONLINE', seed_group_names[g], TRUE, seeded_at, NULL)
        RETURNING id INTO group_id;

        -- SQL 직접 삽입은 GroupCreatedEvent를 발생시키지 않으므로
        -- CharacterStarter 대신 진행 중 캐릭터를 그룹마다 정확히 하나 생성한다.
        INSERT INTO characters (group_id, kind, started_on)
        VALUES (group_id, 'SEED', (seeded_at AT TIME ZONE 'Asia/Seoul')::date);

        FOR m IN 1..4 LOOP
            -- 토큰을 발급하지 않아 인증할 수 없는 가상 계정이다.
            -- 실제 성인 확인이나 민감정보 동의를 가장하지 않는다.
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

    RAISE NOTICE '시연 시드 생성 완료: ONLINE 그룹 3개, 각 4명(빈자리 2개), 진행 중 캐릭터 각 1개.';
END
$demo_seed$;

COMMIT;

-- 요약 출력은 읽기 전용이며, 강한 검증은 verify-demo-online.sql에서 수행한다.
SELECT g.id AS group_id, g.name,
       g.is_demo AS "isDemo",
       count(m.id) AS active_members,
       6 - count(m.id) AS free_slots,
       (SELECT count(*) FROM characters c
        WHERE c.group_id = g.id AND c.completed_on IS NULL) AS active_characters
FROM groups g
LEFT JOIN group_members m ON m.group_id = g.id AND m.left_at IS NULL
WHERE g.is_demo AND g.type = 'ONLINE' AND g.archived_at IS NULL
GROUP BY g.id, g.name, g.is_demo
ORDER BY g.id;
