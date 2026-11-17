-- MOOD-04: 가벼운 도움 카드를 닫은 시각. 닫은 뒤 7일 동안 띄우지 않는다. erd.md 3절.
CREATE TABLE help_card_dismissals (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    dismissed_at TIMESTAMPTZ NOT NULL
);
