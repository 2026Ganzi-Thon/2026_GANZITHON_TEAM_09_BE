-- MOOD-01·02: 하루 1개 기분(민감정보). 같은 날 다시 보내면 덮어쓴다. erd.md 3절.
CREATE TABLE moods (
    user_id BIGINT NOT NULL REFERENCES users(id),
    date DATE NOT NULL,
    mood VARCHAR(10) NOT NULL CHECK (mood IN ('GOOD','CALM','OKAY','TIRED','DEPRESSED','LONELY')),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, date)
);
