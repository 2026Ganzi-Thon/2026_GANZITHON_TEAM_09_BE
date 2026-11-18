-- #58 시연 시드를 최초 삽입한 직후, 실제 사용자가 참여하기 전에 검증한다.
-- 읽기 전용이다. psql -X -v ON_ERROR_STOP=1로 실행한다.
DO $verify$
BEGIN
    IF (SELECT count(*) FROM groups WHERE type='ONLINE' AND is_demo AND archived_at IS NULL) <> 3 THEN
        RAISE EXCEPTION '활성 ONLINE 시연 그룹은 정확히 3개여야 합니다';
    END IF;
    IF (SELECT count(*) FROM users WHERE is_demo AND token_hash IS NULL AND deleted_at IS NULL) <> 12 THEN
        RAISE EXCEPTION 'token_hash가 NULL인 시연 계정은 정확히 12개여야 합니다';
    END IF;
    IF EXISTS (
        SELECT 1 FROM groups g
        WHERE g.type='ONLINE' AND g.is_demo AND g.archived_at IS NULL
          AND (SELECT count(*) FROM group_members gm
               WHERE gm.group_id = g.id AND gm.left_at IS NULL) <> 4
    ) THEN
        RAISE EXCEPTION '시연 그룹마다 활성 구성원은 4명이어야 합니다';
    END IF;
    IF EXISTS (
        SELECT 1 FROM group_members gm
        JOIN groups g ON g.id = gm.group_id
        JOIN users u ON u.id = gm.user_id
        WHERE g.is_demo AND (NOT u.is_demo OR u.token_hash IS NOT NULL)
    ) THEN
        RAISE EXCEPTION '시연 그룹에 일반 계정 또는 세션 토큰이 있는 계정이 포함되어 있습니다';
    END IF;
    IF EXISTS (SELECT 1 FROM online_queue) THEN
        RAISE EXCEPTION '시연 시작 전 온라인 대기열은 비어 있어야 합니다';
    END IF;
    IF (SELECT count(*) FROM characters) <> 3 THEN
        RAISE EXCEPTION '전체 캐릭터 수는 정확히 3개여야 합니다';
    END IF;
    IF EXISTS (
        SELECT 1 FROM groups g
        WHERE g.type = 'ONLINE' AND g.is_demo AND g.archived_at IS NULL
          AND (SELECT count(*) FROM characters c
               WHERE c.group_id = g.id AND c.completed_on IS NULL
                 AND c.kind = 'SEED'
                 AND c.started_on = (g.created_at AT TIME ZONE 'Asia/Seoul')::date) <> 1
    ) THEN
        RAISE EXCEPTION '시연 그룹마다 시작일이 일치하는 진행 중 SEED 캐릭터가 정확히 1개 있어야 합니다';
    END IF;
    RAISE NOTICE '검증 성공: 그룹 3개·계정 12개·그룹당 빈자리 2개·진행 중 캐릭터 각 1개·세션/대기열 없음';
END
$verify$;

SELECT g.id AS group_id, g.name, count(gm.id) AS active_members,
       6 - count(gm.id) AS free_slots,
       (SELECT count(*) FROM characters c
        WHERE c.group_id = g.id AND c.completed_on IS NULL) AS active_characters
FROM groups g LEFT JOIN group_members gm
  ON gm.group_id = g.id AND gm.left_at IS NULL
WHERE g.is_demo AND g.type = 'ONLINE' AND g.archived_at IS NULL
GROUP BY g.id, g.name ORDER BY g.id;
