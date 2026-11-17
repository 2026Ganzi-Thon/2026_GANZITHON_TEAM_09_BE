-- 예약 작업 실행 기록(erd.md 4절 job_runs, 담당 C). 같은 날짜를 두 번 처리하지 않고(NFR-03) 밀린 날짜를 찾는 데 쓴다(CHR-03).
-- 작업 이름은 코드의 JobName 열거값과 같다.
CREATE TABLE job_runs (
    job_name    varchar(30) NOT NULL,
    run_key     date        NOT NULL,
    status      varchar(10) NOT NULL CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    started_at  timestamptz NOT NULL,
    finished_at timestamptz,
    PRIMARY KEY (job_name, run_key)
);
