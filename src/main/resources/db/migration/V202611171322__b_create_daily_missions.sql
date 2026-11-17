-- MIS-02~07: 하루 1개 개인 미션. erd.md 3절.
CREATE TABLE daily_missions (
    user_id BIGINT NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    topic_id SMALLINT NOT NULL REFERENCES mission_topics(id),
    minutes SMALLINT CHECK (minutes IN (5, 10, 20)),
    size SMALLINT CHECK (size > 0),
    place VARCHAR(7) CHECK (place IN ('OUTDOOR','INDOOR')),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, date)
);
-- 그날 완료자 집계(캐릭터 성장). 최근 3일 주제·어제 고른 값은 PK (user_id, date)로 찾는다.
CREATE INDEX ix_daily_missions_completed ON daily_missions (date) WHERE completed_at IS NOT NULL;
