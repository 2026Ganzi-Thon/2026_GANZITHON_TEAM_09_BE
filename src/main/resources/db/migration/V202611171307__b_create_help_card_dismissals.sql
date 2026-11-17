-- MOOD-04: 가벼운 도움 카드를 닫은 시각. 닫은 뒤 7일 동안 띄우지 않는다. erd.md 3절.
-- shortcut: users(id) 외래 키 없음(A의 users 마이그레이션이 아직 main에 없다), A 머지 뒤 새 마이그레이션으로 추가.
CREATE TABLE help_card_dismissals (
    user_id BIGINT PRIMARY KEY,
    dismissed_at TIMESTAMPTZ NOT NULL
);
