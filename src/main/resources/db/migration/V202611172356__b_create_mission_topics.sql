-- MIS-01: 미션 주제(시드 20행은 R__seed_mission_topics.sql). erd.md 3절.
CREATE TABLE mission_topics (
    id SMALLINT PRIMARY KEY,
    category VARCHAR(20) NOT NULL CHECK (category IN
        ('ACTIVITY','STRETCH','NATURE_SUN','WEAK_TIE','KINDNESS','BREATH','MINDFUL','PLEASURE','MASTERY','GOOD_THING')),
    outdoor_text VARCHAR(60) NOT NULL,
    indoor_text VARCHAR(60) NOT NULL,
    size_type VARCHAR(5) NOT NULL CHECK (size_type IN ('TIME','COUNT')),
    -- COUNT 주제만, 5·10·20분에 대응하는 횟수 3개(예: {1,2,4})
    count_sizes SMALLINT[],
    priority_moods VARCHAR(10)[] NOT NULL,
    CHECK ((size_type = 'COUNT') = (count_sizes IS NOT NULL)),
    CHECK (count_sizes IS NULL OR cardinality(count_sizes) = 3)
);
