-- 공공서비스 API 동기화 결과 기록(erd.md 4절 service_sync_log, 담당 C). 사용자와 관계없는 운영 기록이다.
-- error에는 API 키·응답 원문을 남기지 않는다(SEC-11). 서버가 만든 짧은 사유만 넣는다.
CREATE TABLE service_sync_log (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz,
    status        varchar(10) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    updated_count integer,
    error         text
);
