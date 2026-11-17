-- MIS-01: 미션 주제 20쌍. 기준: 기획서 「미션 주제 목록 (20쌍)」. 문구는 원문 그대로(MIS-09).
-- 횟수형인데 숫자가 없는 주제는 표 머리말의 1·2·3번. '모든 기분'은 6개 전부.
-- daily_missions가 id로 참조하므로 지우지 않고 덮어쓴다(반복 실행).
INSERT INTO mission_topics (id, category, outdoor_text, indoor_text, size_type, count_sizes, priority_moods) VALUES
(1,  'ACTIVITY',   '한 정거장 먼저 내려 걷기',               '제자리 걷기',                          'TIME',  NULL,      ARRAY['GOOD','OKAY']),
(2,  'ACTIVITY',   '점심 후 건물 밖 한 바퀴',                '점심 후 복도·계단 걷기',               'TIME',  NULL,      ARRAY['OKAY']),
(3,  'ACTIVITY',   '엘리베이터 대신 계단',                   '의자에서 일어났다 앉기',               'COUNT', '{1,2,3}', ARRAY['GOOD']),
(4,  'STRETCH',    '벤치에서 목·어깨 스트레칭',              '자리에서 손·목 스트레칭',              'TIME',  NULL,      ARRAY['TIRED']),
(5,  'NATURE_SUN', '햇빛 받으며 걷기',                       '창가에서 햇빛 쬐기',                   'TIME',  NULL,      ARRAY['CALM','DEPRESSED']),
(6,  'NATURE_SUN', '나무 있는 길·공원 걷기',                 '창밖 초록 바라보기',                   'TIME',  NULL,      ARRAY['CALM','TIRED']),
(7,  'NATURE_SUN', '하늘 올려다보며 걷기',                   '창밖 하늘 바라보기',                   'TIME',  NULL,      ARRAY['TIRED']),
(8,  'NATURE_SUN', '꽃·식물 하나 찾아보기',                  '화분 돌보기 또는 자연 소리 듣기',      'TIME',  NULL,      ARRAY['CALM']),
(9,  'WEAK_TIE',   '편의점·카페 직원에게 인사 한마디',       '가족·룸메이트에게 먼저 인사',          'COUNT', '{1,2,3}', ARRAY['LONELY']),
(10, 'WEAK_TIE',   '가게에서 "감사합니다" 말하기',           '오랜만인 사람에게 안부 메시지',        'COUNT', '{1,2,3}', ARRAY['LONELY']),
(11, 'WEAK_TIE',   '사람 있는 공간(도서관·카페)에 머물기',   '좋아하는 사람과 통화',                 'TIME',  NULL,      ARRAY['LONELY']),
(12, 'KINDNESS',   '동료·친구에게 음료 하나 (선택)',         '집안일 하나 대신 하기',                'COUNT', '{1,2,3}', ARRAY['GOOD']),
(13, 'KINDNESS',   '길에서 쓰레기 하나 줍기',                '고마운 사람에게 고맙다고 메시지',      'COUNT', '{1,2,3}', ARRAY['GOOD','LONELY']),
(14, 'BREATH',     '벤치에 앉아 천천히 숨쉬기',              '자리에서 천천히 숨쉬기',               'TIME',  NULL,      ARRAY['TIRED']),
(15, 'MINDFUL',    '발걸음 세며 걷기',                       '몸 훑기 (머리부터 발끝까지)',          'TIME',  NULL,      ARRAY['TIRED','OKAY']),
(16, 'PLEASURE',   '좋아하는 노래 들으며 걷기',              '좋아하는 노래 듣기',                   'COUNT', '{1,2,4}', ARRAY['DEPRESSED']),
(17, 'PLEASURE',   '안 가본 길로 돌아가기',                  '차 한 잔 천천히 마시기',               'TIME',  NULL,      ARRAY['OKAY','DEPRESSED']),
(18, 'MASTERY',    '미뤄둔 작은 볼일 하나(장보기 등)',       '책상·가방 정리',                       'TIME',  NULL,      ARRAY['DEPRESSED']),
(19, 'MASTERY',    '다 쓴 물건 버리러 나가기',               '미뤄둔 메시지·작은 일 하나',           'COUNT', '{1,2,3}', ARRAY['DEPRESSED','OKAY']),
(20, 'GOOD_THING', '걸으며 오늘 좋았던 일 떠올리기',         '오늘 좋았던 일 하나 적기 (본인만 봄)', 'COUNT', '{1,2,3}', ARRAY['GOOD','CALM','OKAY','TIRED','DEPRESSED','LONELY'])
ON CONFLICT (id) DO UPDATE SET
    category = EXCLUDED.category,
    outdoor_text = EXCLUDED.outdoor_text,
    indoor_text = EXCLUDED.indoor_text,
    size_type = EXCLUDED.size_type,
    count_sizes = EXCLUDED.count_sizes,
    priority_moods = EXCLUDED.priority_moods;
