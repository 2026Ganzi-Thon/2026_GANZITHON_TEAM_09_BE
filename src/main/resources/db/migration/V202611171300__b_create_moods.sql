-- MOOD-01·02: 하루 1개 기분(민감정보). 같은 날 다시 보내면 덮어쓴다. erd.md 3절.
-- shortcut: users(id) 외래 키 없음(A의 users 마이그레이션이 아직 main에 없다), A 머지 뒤 새 마이그레이션으로 추가.
CREATE TABLE moods (
    user_id BIGINT NOT NULL,
    date DATE NOT NULL,
    mood VARCHAR(10) NOT NULL CHECK (mood IN ('GOOD','CALM','OKAY','TIRED','DEPRESSED','LONELY')),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, date)
);
