-- 도움받을 곳 시드(HELP-02). 기준: 기획서 「도움받을 곳 데이터」 MVP 사업(2026-11-15 공공서비스 API 재조회·큐레이션).
-- id·문구는 프론트 목업(src/data/helpServices.ts)과 같다. 화면의 순서 규칙이 id(mental-center, line-120, voucher 등)를 쓴다.
-- 서울 25개 구 센터의 전화번호·주소는 검수된 데이터가 오기 전까지 비워 둔다(기관 CSV는 2022-03 기준이고 오류가 있음).

INSERT INTO help_items (id, section, kind, name, summary, target, note, phone, url, apply_method, rules, source_service_id, sort_order) VALUES
-- 24시간 채널: 어떤 신호에서도 맨 위(HELP-07)
('line-109', 'CRISIS_24H', 'CHANNEL', '자살예방상담전화 109', '전화 · 24시간', '누구나', NULL,
 '109', NULL, '109로 전화', '{}', NULL, 10),
('line-15770199', 'CRISIS_24H', 'CHANNEL', '정신건강위기상담 1577-0199', '전화 · 24시간', '누구나', NULL,
 '1577-0199', NULL, '1577-0199로 전화', '{}', NULL, 20),
('line-1388', 'CRISIS_24H', 'CHANNEL', '청소년상담1388', '만 24세 이하 · 전화·문자·카톡', '만 9~24세', NULL,
 '1388', 'https://www.cyber1388.kr', '1388 전화, 카카오톡·문자·웹 채팅 (24시간)', '{"maxAge": 24}', 'WII000000770', 30),
('line-120', 'CRISIS_24H', 'CHANNEL', '외로움안녕120', '서울 · 120 누르고 5번 · 카톡', '외로움을 느끼는 서울시민 누구나', NULL,
 '120', 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/611000019639', '120 전화 후 5번, 카카오톡 (24시간)', '{"sido": ["서울특별시"]}', '611000019639', 40),

-- 상담받을 곳
('seoul-youth-mind', 'COUNSELING', 'SERVICE', '서울 청년 마음건강 지원', '서울 만 19~39세 · 1·3·7·9월 모집 · 청년몽땅정보통', '서울 거주 만 19~39세', NULL,
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/611000019617', '분기별 모집(1·3·7·9월), 청년몽땅정보통 온라인 신청',
 '{"sido": ["서울특별시"], "minAge": 19, "maxAge": 39}', '611000019617', 10),
('mental-center', 'COUNSELING', 'CENTER_FINDER', '정신건강복지센터', '지역 주민 누구나 · 전화 또는 방문', '지역 주민 누구나', NULL,
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/GMW000000140', '사는 곳의 정신건강복지센터에 전화 또는 방문', '{}', 'GMW000000140', 20),
-- (서울 25개 구 센터는 아래에서 sort_order 21~45로 넣는다)
('voucher', 'COUNSELING', 'SERVICE', '정신건강 심리상담 바우처', '상담 8회 · 의뢰서 필요(센터·학교 상담센터에서 발급)',
 '우울·불안 등 정서적 어려움이 있는 만 19세 이상 (나이·소득 기준 없음)',
 '정신건강복지센터나 학교 상담센터에서 의뢰서를 받아야 해요. 앱의 점검 결과는 증빙이 되지 않아요.',
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/135200005045', '의뢰서 등 증빙을 받은 뒤 복지로 온라인 신청', '{"minAge": 19}', '135200005045', 50),
('school-counsel', 'COUNSELING', 'SERVICE', '학교 상담센터', '재학생 누구나 · 바우처 의뢰서도 받을 수 있어요', '대학생·대학원생', NULL,
 NULL, NULL, '다니는 학교의 학생상담센터에 문의', '{"jobs": ["STUDENT"]}', NULL, 60),
('worker-health', 'COUNSELING', 'SERVICE', '근로자건강센터', '50인 미만 사업장 근로자 · 센터 방문', '50인 미만 사업장 근로자, 만 18~64세', NULL,
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/149200000043', '가까운 근로자건강센터 방문',
 '{"jobs": ["WORKER"], "minAge": 18, "maxAge": 64}', '149200000043', 70),
('eap', 'COUNSELING', 'SERVICE', '근로자 지원 프로그램(EAP)', '회사에 EAP가 있는지 확인해 보세요', '300인 미만 중소기업 사업장 (회사가 신청)', NULL,
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/B49000100210', '회사가 신청하는 사업이라 개인은 신청할 수 없어요. 회사 가입 여부를 확인해 보세요.',
 '{"jobs": ["WORKER"]}', 'B49000100210', 80),
('job-mind', 'COUNSELING', 'SERVICE', '심리안정지원 프로그램', '구직 중 · 고용센터 방문',
 '실직으로 불안·우울을 겪는 만 18~64세 (국민취업지원제도 참여자·실업급여 수급자 등)', NULL,
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/149200000146', '가까운 고용센터 방문',
 '{"jobs": ["JOB_SEEKER"], "minAge": 18, "maxAge": 64}', '149200000146', 90),

-- 가볍게 들를 곳
('mind-store', 'DROP_IN', 'SERVICE', '서울마음편의점', '외로운 서울시민 누구나 · 지점 방문', '외로움을 느끼는 서울시민 누구나', NULL,
 NULL, 'https://www.gov.kr/portal/rcvfvrSvc/dtlEx/611000019640', '가까운 지점 방문 후 이용자 등록', '{"sido": ["서울특별시"]}', '611000019640', 10);

-- 서울 25개 구 정신건강복지센터(LOCAL_CENTER). 그 구를 고른 사람에게만 보인다(기기 필터).
-- 전국 사업 '정신건강복지센터'(GMW000000140)의 구별 창구지만, 동기화가 25행을 같은 값으로 덮어쓰지 않도록 서비스 ID는 비운다.
INSERT INTO help_items (id, section, kind, name, summary, target, apply_method, rules, source_service_id, sort_order)
SELECT 'center-' || gu,
       'COUNSELING',
       'LOCAL_CENTER',
       gu || ' 정신건강복지센터',
       gu || ' 주민 누구나 · 무료 상담',
       gu || ' 주민 누구나',
       '센터에 전화 또는 방문 (연락처는 센터 찾기에서 확인)',
       jsonb_build_object('sido', jsonb_build_array('서울특별시'), 'sigungu', jsonb_build_array(gu)),
       NULL,
       (20 + ord)::smallint
FROM unnest(ARRAY[
    '강남구', '강동구', '강북구', '강서구', '관악구', '광진구', '구로구', '금천구', '노원구',
    '도봉구', '동대문구', '동작구', '마포구', '서대문구', '서초구', '성동구', '성북구', '송파구',
    '양천구', '영등포구', '용산구', '은평구', '종로구', '중구', '중랑구'
]) WITH ORDINALITY AS t(gu, ord);
