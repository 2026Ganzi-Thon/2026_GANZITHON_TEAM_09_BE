# 행운핑 API 명세 (MVP)

- 2026년 11월 16일 작성 · @고은우
- 요구사항: [prd.md](prd.md) · 화면 흐름: [UserFlow.md](UserFlow.md) · 데이터: [erd.md](erd.md) · 구조: [architecture.md](architecture.md)
- 각 엔드포인트에 PRD 요구사항 ID와 담당(A/B/C)을 적었다. 응답 필드를 바꾸면 이 문서를 먼저 고치고 프론트에 알린다.

## 1. 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| 기본 경로 | `/api/v1` |
| 형식 | 요청·응답 모두 JSON(UTF-8), 필드 이름은 camelCase |
| 인증 | 쿠키 `__Host-hp_session`(HttpOnly, Secure, SameSite=Lax, 만료 400일. 로컬 http 개발에서는 `hp_session`). 브라우저가 자동으로 보내므로 프론트는 이름을 다루지 않는다. 🔒 표시가 있는 엔드포인트는 필수 |
| CSRF | 서버가 `XSRF-TOKEN` 쿠키를 주고, 상태 변경 요청(POST·PUT·PATCH·DELETE)은 같은 값을 `X-XSRF-TOKEN` 헤더로 보낸다(Spring Security `CookieCsrfTokenRepository`) |
| 날짜 | 날짜는 KST 기준 `YYYY-MM-DD`, 시각은 ISO-8601 UTC(`2026-11-16T09:00:00Z`) |
| 열거값 | 대문자 영문. 정의 밖 값은 400 |
| 구성원 식별 | 그룹 안에서는 `memberId`만 쓴다. 응답에 `userId`를 넣지 않는다 |

**에러 응답**

```json
{ "code": "GROUP_FULL", "message": "그룹 인원이 가득 찼어요" }
```

| HTTP | code | 언제 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 형식·범위·열거값 오류(열거값 자리의 숫자 포함, 쿼리 파라미터도 같음). `fields` 배열에 필드 이름, 목록 안이면 `items[1].color`처럼 경로 |
| 400 | `MALFORMED_REQUEST` | JSON을 읽을 수 없음(깨진 JSON, 숫자 자리에 문자열 같은 타입 불일치). `fields` 없음 |
| 401 | `UNAUTHENTICATED` | 쿠키가 없거나 무효 |
| 403 | `CSRF_INVALID` | CSRF 토큰 불일치 |
| 403 | `CONSENT_REQUIRED` | 민감정보 동의 없이 기분·점검 저장 |
| 403 | `ADULT_CONFIRMATION_REQUIRED` | 만 19세 확인 없이 온라인 대기열 |
| 404 | `NOT_FOUND` | 없거나, 구성원이 아닌 그룹 |
| 405 | `METHOD_NOT_ALLOWED` | 그 경로에 없는 메서드 |
| 409 | `GROUP_LIMIT` | 그룹 3개 초과 |
| 409 | `GROUP_FULL` | 6명 그룹 참여 |
| 409 | `ALREADY_MEMBER` | 이미 구성원인 그룹 참여 |
| 409 | `ALREADY_QUEUED` | 이미 온라인 대기 중 |
| 409 | `ALREADY_COMPLETED` | 오늘 미션 이미 완료 |
| 409 | `MISSION_NOT_COMPLETED` | 미완료 상태 핑 |
| 409 | `ALREADY_PINGED` | 오늘 핑 이미 보냄 |
| 409 | `CHECK_NOT_DUE` | 점검 기간이 아니거나 이번 주에 이미 함 |
| 409 | `NOT_ALLOWED_FOR_GROUP_TYPE` | 1인·온라인 그룹 초대 코드 등 |
| 410 | `INVITE_EXPIRED` | 만료된 초대 코드 |
| 429 | `RATE_LIMITED` | 요청 횟수 초과. `Retry-After` 헤더 |
| 500 | `INTERNAL_ERROR` | 예상 밖 서버 오류. 메시지는 '잠시 뒤 다시 시도해 주세요'로 고정, 내부 정보 없음 |

시큐리티 단계의 거절(401·403)과 없는 경로(404)도 같은 모양으로 답한다(SEC-12).
## 2. 계정 — 담당 A

### `POST /accounts` — 익명 계정 만들기 (ACC-01~04)

인증 불필요. IP당 시간당 10회.

```json
// 요청
{ "nickname": "은우" }
// 201 응답 + Set-Cookie: __Host-hp_session=...
{ "nickname": "은우", "adultConfirmed": false, "sensitiveConsent": false }
```

### 🔒 `GET /me` — 내 정보

```json
{ "nickname": "은우", "adultConfirmed": true, "sensitiveConsent": true, "createdAt": "2026-11-16T00:12:00Z", "today": "2026-11-16", "isDemo": false }
```

`today`는 서버 기준 오늘(KST) 날짜다. 기기는 기분 팝업을 오늘 띄웠는지를 이 값으로 판단한다(시연 시계를 바꿔도 맞게 동작).

`isDemo`는 시드로 만든 시연 계정이면 `true`다. 화면은 이 값이 `true`일 때만 마이 탭의 시연 도구를 보인다. `POST /accounts`로 만든 계정은 항상 `false`(DEMO-01, SEC-13).

### 🔒 `PATCH /me` — 닉네임 바꾸기 (ACC-04)

```json
{ "nickname": "은우님" }
```

200, `GET /me`와 같은 응답.

### 🔒 `PUT /me/consents/sensitive` — 민감정보 동의·철회 (ACC-05, 06)

```json
{ "agreed": true }
```

204.

### 🔒 `PUT /me/adult-confirmation` — 만 19세 이상 자기 확인 (ACC-09)

```json
{ "confirmed": true }
```

204. `false`로 바꿀 수 없다(400).

### 🔒 `DELETE /me/health-records` — 기분·점검 기록 삭제 (ACC-07)

204.

### 🔒 `DELETE /me` — 계정 삭제 (ACC-08)

204 + 쿠키 삭제.

## 3. 그룹 — 담당 A

### 🔒 `GET /groups` — 내 그룹 목록

```json
{
  "groups": [
    {
      "groupId": 12,
      "type": "ONLINE",
      "name": "아침 산책단",
      "memberCount": 5,
      "today": { "completed": 4, "total": 5 },
      "character": { "kind": "CLOVER", "stage": 2, "progress": 49.8 },
      "isDemo": true
    }
  ],
  "queue": { "waiting": false, "queuedAt": null }
}
```

- `progress`는 확정 합 + 오늘 임시 성장, 소수 첫째 자리까지, 최대 100.
- `stage`는 1~4, 완성 연출 대상이면 응답에 `"justCompleted": { "kind": "SEED", "completedOn": "2026-11-15" }`를 한 번만 넣는다.

### 🔒 `POST /groups` — 친구·1인 그룹 만들기 (GRP-02, 03)

```json
{ "type": "FRIEND", "name": "우리 넷" }   // SOLO는 name 생략 가능
```

201, `GET /groups/{groupId}`와 같은 응답.

### 🔒 `GET /groups/{groupId}` — 그룹 상세 (CHR-05, 06, 10)

```json
{
  "groupId": 12,
  "type": "ONLINE",
  "name": "아침 산책단",
  "me": { "memberId": 301, "displayName": "산책하는 수달" },
  "members": [
    { "memberId": 301, "displayName": "산책하는 수달" },
    { "memberId": 302, "displayName": "졸린 고양이" }
  ],
  "today": { "date": "2026-11-16", "completed": 4, "total": 5 },
  "character": { "characterId": 7, "kind": "CLOVER", "stage": 2, "progress": 49.8 },
  "isDemo": true
}
```

구성원별 완료 여부는 없다. 구성원이 아니면 404.

### 🔒 `GET /groups/{groupId}/collection` — 그룹 도감 (CHR-07)

담당 B(character 모듈, A와 합의 2026-11-18). 완성된 캐릭터만 완성 순. 구성원이 아니면 404.

```json
{ "characters": [ { "characterId": 3, "kind": "SEED", "startedOn": "2026-09-20", "completedOn": "2026-11-08" } ] }
```

### 🔒 `POST /groups/{groupId}/invites` — 초대 코드 만들기 (GRP-04, 16)

친구 그룹 구성원만. 이전 코드는 무효가 된다.

```json
// 201
{ "code": "K7MP2QXR", "expiresAt": "2026-11-23T03:00:00Z" }
```

### 🔒 `POST /invites/{code}/join` — 초대 코드로 참여 (GRP-04~06)

IP당 시간당 20회. 201, 그룹 상세 응답. 404 / 410 / 409(`GROUP_FULL`, `GROUP_LIMIT`, `ALREADY_MEMBER`).

### 🔒 `POST /groups/online-queue` — 온라인 그룹 들어가기 (GRP-07, 13)

요청 본문 없음.

```json
// 바로 배정되면 201
{ "status": "MATCHED", "groupId": 12 }
// 대기하면 202
{ "status": "WAITING", "queuedAt": "2026-11-16T03:00:00Z" }
```

403(`ADULT_CONFIRMATION_REQUIRED`), 409(`GROUP_LIMIT`, `ALREADY_QUEUED`). 대기 중 배정되면 `GET /groups`에 새 그룹이 나타나고 웹 푸시 1회.

### 🔒 `DELETE /groups/online-queue` — 대기 취소

204.

### 🔒 `DELETE /groups/{groupId}/members/me` — 그룹 나가기 (GRP-11)

204.

### 🔒 `POST /groups/{groupId}/members/{memberId}/block` — 차단 (GRP-14)

온라인 그룹만. 204. 자기 자신 → 400.

### 🔒 `DELETE /groups/{groupId}/members/{memberId}/block` — 차단 풀기

204.

### 🔒 `POST /groups/{groupId}/members/{memberId}/reports` — 신고 (GRP-15)

```json
{ "reason": "HARASSMENT" }
```

204. 같은 대상을 두 번 신고해도 1번으로 센다. 사용자당 하루 10회.

## 4. 기분 — 담당 B

### 🔒 `PUT /moods/today` — 오늘 기분 저장 (MOOD-01, 02)

```json
{ "mood": "LONELY" }
```

200 `{ "date": "2026-11-16", "mood": "LONELY" }`. 403(`CONSENT_REQUIRED`).

### 🔒 `GET /moods?from=2026-10-01&to=2026-11-16` — 기분 기록 (MOOD-05)

```json
{ "moods": [ { "date": "2026-11-16", "mood": "LONELY" } ] }
```

기간 93일 초과 → 400.

### 🔒 `GET /help-card` — 가벼운 도움 카드 표시 여부 (MOOD-04)

```json
{ "show": true, "reason": "LOW_MOOD_3_DAYS" }
```

### 🔒 `POST /help-card/dismiss` — 카드 닫기

204. 7일 동안 `show=false`.

## 5. 미션 — 담당 B

### 🔒 `GET /missions/today` — 오늘 미션 (MIS-02~04, 09)

처음 부를 때 배정한다. 기분 팝업을 닫은 뒤(입력·건너뛰기 모두) 부른다.

```json
{
  "date": "2026-11-16",
  "topic": {
    "topicId": 5,
    "category": "NATURE_SUN",
    "outdoorText": "햇빛 받으며 걷기",
    "indoorText": "창가에서 햇빛 쬐기",
    "sizeType": "TIME"
  },
  "defaults": { "minutes": 10, "place": "OUTDOOR" },
  "sizeByMinutes": { "5": 5, "10": 10, "20": 20 },
  "status": "NOT_STARTED",
  "startedAt": null,
  "completedAt": null,
  "minutes": null,
  "place": null,
  "size": null
}
```

- `sizeByMinutes`: 오늘 기분을 반영한 추천 크기. `TIME`이면 분, `COUNT`면 횟수.
- `defaults`: 어제 고른 값(없으면 10분·OUTDOOR).
- `status`: `NOT_STARTED` / `STARTED` / `COMPLETED`.
- `minutes`·`place`·`size`: 시작할 때 고른 값(`daily_missions` 그대로). `NOT_STARTED`면 `null`, `STARTED`·`COMPLETED`면 값이 있다. 새로고침 뒤 타이머 길이와 밖/안 표시를 이 값으로 되살린다.

### 🔒 `POST /missions/today/start` — 시작 (MIS-05, 06)

```json
{ "minutes": 10, "place": "INDOOR", "size": 5 }
```

200, `GET /missions/today`와 같은 응답(`status: STARTED`, `startedAt`·`minutes`·`place`·`size` 채움). 다시 부르면 값과 `startedAt`을 새로 쓴다.

`minutes`가 5·10·20이 아니거나 `size`가 1~100 밖이면 400 `VALIDATION_FAILED`.

### 🔒 `POST /missions/today/complete` — 다 했어요 (MIS-07, 08)

요청 본문 없음.

```json
{
  "completedAt": "2026-11-16T03:20:00Z",
  "groups": [
    { "groupId": 12, "today": { "completed": 5, "total": 5 }, "character": { "stage": 3, "progress": 50.2, "stageChanged": true } }
  ]
}
```

409(`ALREADY_COMPLETED`).

- `groups`: 지금 속한 그룹마다. `today.completed`는 지금 구성원 중 오늘 완료한 수라 완료한 뒤 들어온 사람도 센다(MIS-08). 누가 완료했는지는 없다(CHR-10).
- `character`: 오늘 임시 성장을 더한 표시 값(CHR-05·06). `stageChanged`는 내 완료로 단계가 바뀌었는지. 진행 중 캐릭터가 없는 그룹이면 `null`.

### 🔒 `GET /me/records?month=2026-11` — 내 기록 (참여한 미션)

```json
{ "missions": [ { "date": "2026-11-16", "category": "NATURE_SUN", "text": "창가에서 햇빛 쬐기", "size": 5, "sizeType": "TIME" } ] }
```

## 6. 핑 — 담당 B

### 🔒 `POST /pings` — 핑 보내기 (PING-01, 02)

```json
{ "emoji": "CLOVER" }
```

201 `{ "groupIds": [12, 15] }`. 409(`MISSION_NOT_COMPLETED`, `ALREADY_PINGED`).

### 🔒 `GET /groups/{groupId}/pings` — 오늘 핑 (PING-03, GRP-14)

```json
{ "date": "2026-11-16", "pings": [ { "emoji": "SUN" }, { "emoji": "CLOVER" } ] }
```

보낸 사람·시각 없음, 순서 섞음, 내가 차단한 구성원의 핑은 서버에서 뺀다.

## 7. 주간 점검 — 담당 C

### 🔒 `GET /checks/current` — 이번 주 점검 상태 (CHK-01)

```json
{ "weekStart": "2026-11-10", "due": true, "done": false, "dismissed": false }
```

### 🔒 `POST /checks` — 점검 제출 (CHK-02~06)

```json
{ "answers": [2, 1, 0, 0] }
```

순서는 우울1, 우울2, 불안1, 불안2.

```json
// 201
{
  "results": ["DEPRESSION"],
  "messages": ["요즘 많이 지쳐 있었던 것 같아요"],
  "notice": "진단이 아니라 참고예요",
  "sections": ["CRISIS_24H", "COUNSELING"]
}
```

- `results`: `DEPRESSION`, `ANXIETY`, `LOW_MOOD_DAYS`, `STABLE` 중 하나 이상.
- `sections`: 결과 화면에서 보여줄 도움받기 구역. `CRISIS_24H`, `COUNSELING`, `DROP_IN`.
- 점수는 응답에 없다. 403(`CONSENT_REQUIRED`), 409(`CHECK_NOT_DUE`).
- 검사 순서: 동의 403 → 답 형식 400 → 점검 기간·중복 409. 동의가 없으면 답이 틀려도 403이다.
- 답 형식: 4개, 각 0~3 정수. 어긋나면 `VALIDATION_FAILED`와 `fields`(개수가 다르거나 없으면 `answers`, 값이 틀리면 `answers[3]`처럼 자리). 숫자가 아니거나 소수(`2.5`)는 `MALFORMED_REQUEST`.
- 409는 일요일 18:00~23:59(KST)가 아니거나 이번 주에 이미 제출·닫은 경우다.
- 답은 저장하지 않는다(D-13).

### 🔒 `POST /checks/current/dismiss` — 이번 주 점검 닫기

204. 동의·점검 기간과 무관하게 항상 204이고, 이미 이번 주 기록이 있으면(제출·닫기) 그대로 둔다. 닫은 주에는 제출할 수 없다(409).

## 8. 도움받기 — 담당 C

### `GET /help/items` — 도움받을 곳 전체 목록 (HELP-01~05, 07)

인증 불필요. `ETag` / `If-None-Match` 지원(304). `Cache-Control: no-cache`(캐시는 하되 쓸 때마다 ETag로 확인).

```json
{
  "version": "2026-11-17",
  "syncedAt": null,
  "items": [
    {
      "id": "line-109",
      "section": "CRISIS_24H",
      "kind": "CHANNEL",
      "name": "자살예방상담전화 109",
      "summary": "전화 · 24시간",
      "target": "누구나",
      "note": null,
      "phone": "109",
      "url": null,
      "address": null,
      "applyMethod": "109로 전화",
      "deadline": null,
      "rules": {},
      "sourceServiceId": null
    },
    {
      "id": "voucher",
      "section": "COUNSELING",
      "kind": "SERVICE",
      "name": "정신건강 심리상담 바우처",
      "summary": "상담 8회 · 의뢰서 필요(센터·학교 상담센터에서 발급)",
      "target": "우울·불안 등 정서적 어려움이 있는 만 19세 이상 (나이·소득 기준 없음)",
      "note": "정신건강복지센터나 학교 상담센터에서 의뢰서를 받아야 해요. 앱의 점검 결과는 증빙이 되지 않아요.",
      "phone": null,
      "url": "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/135200005045",
      "address": null,
      "applyMethod": "의뢰서 등 증빙을 받은 뒤 복지로 온라인 신청",
      "deadline": null,
      "rules": { "minAge": 19 },
      "sourceServiceId": "135200005045"
    },
    {
      "id": "center-마포구",
      "section": "COUNSELING",
      "kind": "LOCAL_CENTER",
      "name": "마포구 정신건강복지센터",
      "summary": "마포구 주민 누구나 · 무료 상담",
      "target": "마포구 주민 누구나",
      "note": null,
      "phone": null,
      "url": null,
      "address": null,
      "applyMethod": "센터에 전화 또는 방문 (연락처는 센터 찾기에서 확인)",
      "deadline": null,
      "rules": { "sido": ["서울특별시"], "sigungu": ["마포구"] },
      "sourceServiceId": null
    }
  ]
}
```

- 모든 키를 항상 보낸다. 값이 없으면 `null`이다.
- 순서: 구역(`CRISIS_24H` → `COUNSELING` → `DROP_IN`) 다음 구역 안 기본 순서. 24시간 채널이 항상 맨 앞이다(HELP-07).
- `kind`: `CHANNEL`(24시간 채널), `SERVICE`(사업), `LOCAL_CENTER`(서울 구별 센터), `CENTER_FINDER`(`mental-center`, 가까운 센터 찾기 안내).
- `rules`: 비어 있으면 항상 보인다. 키: `minAge`, `maxAge`, `sido`, `sigungu`, `jobs`(`STUDENT`, `WORKER`, `JOB_SEEKER`, `OTHER`).
- 기기는 **입력하지 않은 정보에 걸린 규칙은 통과**로 처리한다(HELP-03).
- `LOCAL_CENTER` 항목은 사용자의 시·군·구와 맞을 때만 보이고, 서울에서 구를 고른 사람에게는 `CENTER_FINDER`를 숨긴다.
- `id`는 프론트의 순서 규칙이 쓰므로 바꾸지 않는다: `line-109`, `line-15770199`, `line-1388`, `line-120`, `seoul-youth-mind`, `mental-center`, `voucher`, `school-counsel`, `worker-health`, `eap`, `job-mind`, `mind-store`, `center-<구 이름>`.
- `version`: 목록이 마지막으로 바뀐 날(KST). `syncedAt`: 공공서비스 API 동기화가 마지막으로 성공한 시각. 동기화 전에는 `null`.
- 서울 25개 구 센터의 `phone`·`address`는 검수된 데이터를 넣기 전까지 `null`이다.

### 🔒 `GET /help/signals` — 순서 신호 (HELP-06)

```json
{ "lonely3Days": true, "checkAboveThreshold": false }
```

쿠키가 없으면 401 대신 둘 다 `false`로 200을 준다(온보딩 전 도움받기 탭). 무효·만료된 쿠키도 마찬가지로 200이다. 동의 검사는 없고, 값은 본인 기록에서 나온 참/거짓 두 개뿐이다.

## 9. 알림 — 담당 B

### 🔒 `POST /me/push-subscriptions` — 웹 푸시 구독 (NOTI-01)

```json
{ "endpoint": "https://fcm.googleapis.com/...", "keys": { "p256dh": "...", "auth": "..." } }
```

201. 같은 endpoint는 덮어쓴다.

### 🔒 `DELETE /me/push-subscriptions` — 구독 해지

```json
{ "endpoint": "https://fcm.googleapis.com/..." }
```

204.

### 🔒 `GET /me/notification-settings` / `PUT /me/notification-settings` (NOTI-02, 03)

```json
{ "missionReminderTime": "12:30", "weeklyCheckReminder": true }
```

`missionReminderTime`은 `HH:mm`(30분 단위) 또는 `null`(끔).

### `GET /push/vapid-public-key`

인증 불필요. `{ "publicKey": "..." }`

## 10. 시연 — 담당 C

실서버에는 시연 API가 없다(D-12, 2026-11-18 결정). `PUT /demo/clock`은 만들지 않는다.

- 며칠이 걸리는 장면(일요일 점검, 3일 연속 카드, 캐릭터 단계 상승·완성)은 프론트 목업(`VITE_API_MODE=mock`)의 기기 안 시계로 보여 준다. 목업은 `DEMO_SITE_ADDRESS`(예: `demo.<도메인>`)의 정적 사이트로 올라가며 이 API를 부르지 않는다.
- 실서버 `GET /me`의 `isDemo`는 항상 `false`다. 시드 시연 계정은 로그인할 수 없다(SEC-13).
- 그룹 응답의 `isDemo: true`는 행사용 시드 그룹(온라인 그룹 자리)을 '시연 데이터'로 표시할 때 쓴다(DEMO-03·04).

## 11. 기타

### `GET /health`

인증 불필요. `{ "status": "UP" }`. DB 연결만 확인한다.

## 12. 서버 내부 작업 (API 아님)

| 시각(KST) | 작업 | 담당 | PRD |
| --- | --- | --- | --- |
| 00:00 | 전날 그룹별 확정 성장 계산, 캐릭터 완성 처리 | B | CHR-03, 07 |
| 00:10 | 온라인 그룹 7일 미접속 구성원 내보내기 | A | GRP-12 |
| 04:00 | 공공서비스 API 동기화 | C | HELP-04 |
| 매 30분 | 미션 알림 발송 | B | NOTI-02 |
| 일 18:00 | 점검 알림 발송 | B | NOTI-03 |
| 매 1분 | 온라인 대기열 배정 | A | GRP-07 |
