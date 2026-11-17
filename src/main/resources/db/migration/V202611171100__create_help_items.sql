-- 도움받을 곳 목록(erd.md 4절 help_items, 담당 C). 사용자와 관계없는 공개 데이터다(HELP-08: 이용 기록은 저장하지 않는다).
-- 선정과 표시 규칙은 사람이 정하고(기획서 「도움받기」), 공공서비스 API는 source_service_id가 있는 행의 최신 정보 유지에만 쓴다(HELP-04).
CREATE TABLE help_items (
    id                varchar(40)  PRIMARY KEY,
    section           varchar(12)  NOT NULL CHECK (section IN ('CRISIS_24H', 'COUNSELING', 'DROP_IN')),
    kind              varchar(15)  NOT NULL CHECK (kind IN ('CHANNEL', 'SERVICE', 'LOCAL_CENTER', 'CENTER_FINDER')),
    name              varchar(60)  NOT NULL,
    summary           varchar(120),
    target            varchar(120),
    note              varchar(200),
    phone             varchar(20),
    url               text,
    address           varchar(120),
    apply_method      varchar(120),
    deadline          varchar(60),
    rules             jsonb        NOT NULL DEFAULT '{}'::jsonb,
    source_service_id varchar(20),
    sort_order        smallint     NOT NULL,
    synced_at         timestamptz,
    updated_at        timestamptz  NOT NULL DEFAULT now()
);
