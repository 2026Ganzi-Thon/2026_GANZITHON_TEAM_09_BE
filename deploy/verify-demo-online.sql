-- Verify #58 seed in PostgreSQL immediately AFTER seeding, BEFORE test users join.
-- Read-only except for transaction-scoped execution state. Use psql -v ON_ERROR_STOP=1.
DO $verify$
BEGIN
    IF (SELECT count(*) FROM groups WHERE type='ONLINE' AND is_demo AND archived_at IS NULL) <> 3 THEN
        RAISE EXCEPTION 'Expected exactly 3 active ONLINE demo groups';
    END IF;
    IF (SELECT count(*) FROM users WHERE is_demo AND token_hash IS NULL AND deleted_at IS NULL) <> 12 THEN
        RAISE EXCEPTION 'Expected 12 demo accounts with NULL token_hash';
    END IF;
    IF EXISTS (
        SELECT 1 FROM groups g
        WHERE g.type='ONLINE' AND g.is_demo AND g.archived_at IS NULL
          AND (SELECT count(*) FROM group_members gm
               WHERE gm.group_id = g.id AND gm.left_at IS NULL) <> 4
    ) THEN
        RAISE EXCEPTION 'Each demo group must have exactly 4 active members';
    END IF;
    IF EXISTS (
        SELECT 1 FROM group_members gm
        JOIN groups g ON g.id = gm.group_id
        JOIN users u ON u.id = gm.user_id
        WHERE g.is_demo AND (NOT u.is_demo OR u.token_hash IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'Demo group contains a non-demo user or session token';
    END IF;
    IF EXISTS (SELECT 1 FROM online_queue) THEN
        RAISE EXCEPTION 'Expected empty online_queue before demonstration';
    END IF;
    RAISE NOTICE 'PASS: 3 groups x 4 demo accounts, 2 free slots each, no tokens/queue';
END
$verify$;

SELECT g.id AS group_id, g.name, count(gm.id) AS active_members,
       6 - count(gm.id) AS free_slots
FROM groups g LEFT JOIN group_members gm
  ON gm.group_id = g.id AND gm.left_at IS NULL
WHERE g.is_demo AND g.type = 'ONLINE' AND g.archived_at IS NULL
GROUP BY g.id, g.name ORDER BY g.id;
