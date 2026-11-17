-- NOTI-02: 미션 알림을 그날 이미 보냈는지. 같은 시각 작업이 두 번 돌아도(재시작·중복 실행) 하루 1번만 보낸다.
ALTER TABLE notification_settings ADD COLUMN mission_reminded_on DATE;
