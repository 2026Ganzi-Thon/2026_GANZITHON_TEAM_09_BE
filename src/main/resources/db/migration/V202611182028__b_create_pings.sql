-- PING-01·02: 하루 1개 핑. erd.md 3절. 그룹마다 복사하지 않고 보낸 시각의 구성원 여부로 그룹 목록을 구한다.
CREATE TABLE pings (
    user_id BIGINT NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    emoji VARCHAR(6) NOT NULL CHECK (emoji IN ('CLAP','CLOVER','MUSCLE','SUN')),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, date)
);
CREATE INDEX ix_pings_date ON pings (date);
