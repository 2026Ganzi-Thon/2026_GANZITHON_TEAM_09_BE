-- V202611182236(서울 25개 구 센터 연락처) 중 전화 두 곳을 사람이 직접 확인한 대표번호로 고친다(이슈 #56, 2026-11-18).
-- 강북구: 서울시 목록 번호 대신 센터 대표번호. 서대문구: 센터가 안내하는 대표번호(02-337-2176, 보조 2179는 넣지 않음).
-- 주소는 그대로다. 나머지 23곳은 사람이 확인해 바꿀 것이 없었다.

UPDATE help_items
SET phone = '02-901-4860'
WHERE id = 'center-강북구'
  AND kind = 'LOCAL_CENTER';

UPDATE help_items
SET phone = '02-337-2176'
WHERE id = 'center-서대문구'
  AND kind = 'LOCAL_CENTER';
