-- 실서버 비우기(이슈 #57, DEMO-05): 행사 직전에 사용자 데이터를 모두 지우고 시드만 남긴다.
-- 직접 실행하지 않는다. `./deploy/deploy.sh reset`이 백업을 받은 뒤 단일 트랜잭션으로 실행한다(docs/deploy.md 4절).
--
-- 남기는 테이블(아래 목록): 스키마 이력과 시드·운영 기록.
--   flyway_schema_history  마이그레이션 이력. 지우면 다음 기동에서 마이그레이션을 처음부터 다시 돌려 실패한다
--   help_items             도움받을 곳 시드(전화·주소는 사람이 검수한 값)
--   mission_topics         미션 주제 20개 시드
--   service_sync_log       공공서비스 동기화 기록(help_items.synced_at과 짝이라 같이 둔다)
-- 그 밖의 public 테이블은 전부 비운다(계정·그룹·기분·미션·점검·알림 구독·차단·신고·job_runs…).
-- 목록을 켜 두는 쪽이 아니라 남길 것을 적는 방식이라, 나중에 사용자 데이터 테이블이 생겨도 기본으로 지워진다.
--
-- job_runs도 비운다: 다음 00:00 확정 처리는 그룹이 없으면 할 일이 없고, 새 그룹은 만든 날부터 계산한다(#62 리뷰에서 확인).
-- CASCADE를 쓰지 않는다: 남길 테이블이 지울 테이블을 참조하고 있으면 조용히 같이 지우지 않고 오류로 멈춘다.
DO $reset$
DECLARE
    keep   constant text[] := ARRAY['flyway_schema_history', 'help_items', 'mission_topics', 'service_sync_log'];
    doomed text;
BEGIN
    SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename)
      INTO doomed
      FROM pg_tables
     WHERE schemaname = 'public' AND tablename <> ALL (keep);

    IF doomed IS NULL THEN
        RAISE NOTICE '비울 테이블이 없습니다';
        RETURN;
    END IF;

    -- 한 문장으로 비워야 서로 참조하는 테이블(users ← group_members 등)을 CASCADE 없이 지울 수 있다.
    EXECUTE 'TRUNCATE TABLE ' || doomed || ' RESTART IDENTITY';
    RAISE NOTICE '비운 테이블: %', doomed;
END
$reset$;
