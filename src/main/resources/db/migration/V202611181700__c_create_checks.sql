-- 주간 점검 기록(erd.md 4절 checks, 담당 C). CHK-01~06. 민감정보라 로그에 남기지 않고 본인만 조회한다.
-- 답(answers)은 저장하지 않는다(D-13): 어떤 요구사항도 저장된 답을 다시 쓰지 않으므로 결과만 남긴다.
-- 한 주(월요일 week_start)에 1건. 제출(DONE)이든 닫기(DISMISSED)든 같은 유니크 키라 두 번째는 들어가지 않는다(CHK-05).
CREATE TABLE checks (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users (id),
    week_start   date        NOT NULL CHECK (EXTRACT(ISODOW FROM week_start) = 1),
    status       varchar(10) NOT NULL CHECK (status IN ('DONE', 'DISMISSED')),
    result_codes varchar(15)[] CHECK (result_codes <@ ARRAY['DEPRESSION', 'ANXIETY', 'LOW_MOOD_DAYS', 'STABLE']::varchar(15)[]),
    created_at   timestamptz NOT NULL,
    UNIQUE (user_id, week_start),
    -- 제출한 기록에만 결과가 있고, 결과는 하나 이상이다
    CHECK ((status = 'DONE') = (result_codes IS NOT NULL AND cardinality(result_codes) >= 1))
);
