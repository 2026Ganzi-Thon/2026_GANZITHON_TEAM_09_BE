# AI 생성 코드·외부 의존성 검증 기록

- 2026년 11월 16일 작성 · @고은우
- 요구사항: [prd.md](prd.md) SEC-14 · 보안 전체: [../SECURITY.md](../SECURITY.md)
- 이 문서는 서비스 기능이 아니라 **개발 과정**에서 AI를 어떻게 썼고 어떻게 검증했는지 남기는 곳이다. 서비스 자체는 외부 AI를 쓰지 않는다([decisions.md](decisions.md) D-06).

## 1. 원칙

1. AI가 만든 코드도 사람이 쓴 코드와 같은 기준으로 리뷰한다. **이해하지 못한 코드는 합치지 않는다.**
2. 규칙 계산(성장·점검 판정·미션 크기·도움 순서)은 AI가 짰더라도 **사람이 prd.md 경계값 표로 테스트를 먼저 쓰고** 그 테스트를 통과해야 합친다.
3. 보안 경계(인증 필터, CSRF, 권한 검사, 예외 처리)는 AI 결과를 그대로 쓰지 않고 공식 문서와 대조한다. 대조한 문서 링크를 PR에 남긴다.
4. AI가 제안한 라이브러리는 바로 추가하지 않는다. 아래 3절 기준을 통과해야 한다.
5. AI에게 실제 API 키, DB 비밀번호, `.env` 내용, 사용자 데이터를 넣지 않는다.

## 2. PR마다 하는 확인 (PR 템플릿)

- [ ] AI 사용 여부와 범위(파일·함수)
- [ ] 무엇으로 검증했나: 추가한 테스트 이름 / 직접 실행한 시나리오 / 대조한 공식 문서
- [ ] 새 의존성이 있으면 3절 기준 확인
- [ ] 비밀값·개인정보가 코드·로그·테스트 데이터에 없음

## 3. 외부 라이브러리 추가 기준

| 확인 | 기준 |
| --- | --- |
| 필요성 | 표준 라이브러리·Spring으로 30줄 안에 못 하는 일인가 |
| 출처 | Maven Central 공식 좌표, 유지보수 중(최근 1년 안에 릴리스) |
| 버전 | `gradle/libs.versions.toml`에 정확한 버전 고정 + 의존성 잠금 파일 갱신 |
| 취약점 | CI `dependencies` 작업(OSV-Scanner) 통과. 알려진 취약점이 하나라도 있으면 실패 |
| 기록 | 아래 4절 표에 추가 |

자동 검사: CI가 push·PR마다, 그리고 주 1회 `gradle.lockfile`의 모든 의존성(간접 의존성 포함 약 160개)을 OSV 취약점 DB와 대조한다(OSV-Scanner, 하나라도 있으면 실패). 잠금 파일이 실제 빌드와 다르면 Gradle 빌드가 먼저 실패하므로 검사 대상이 곧 실제 의존성이다. Dependabot이 주 1회 업데이트 PR을 연다.

당장 고칠 수 없는 취약점은 레포 루트 `osv-scanner.toml`에 예외로 적는다. 예외마다 **이유(우리 코드가 그 기능을 쓰지 않는 근거)와 기한**을 쓰고, 기한이 지나면 검사가 다시 실패한다.

```toml
[[IgnoredVulns]]
id = "GHSA-xxxx-xxxx-xxxx"
ignoreUntil = 2026-12-31
reason = "취약한 기능(XML 외부 엔티티)을 호출하는 코드가 없음. 상위 버전 출시 대기"
```

## 4. 사용하는 외부 의존성

| 의존성 | 쓰는 곳 | 고른 이유 | 확인한 사람·날짜 |
| --- | --- | --- | --- |
| Spring Boot 4.1.2 (WebMVC, Security 7, Data JPA, Validation, Flyway) | 전체 | 무료 보안 지원 중인 버전(2027-07까지). 3.5·4.0은 지원 종료 또는 임박([D-10](decisions.md)) | 고은우 2026-11-16 |
| PostgreSQL JDBC | DB | Boot BOM이 버전 관리 | 고은우 2026-11-16 |
| Bucket4j | 요청 횟수 제한(SEC-08) | 메모리 버킷, Redis로 확장 가능 | 추가할 때 기록 |
| (추가 안 함) 웹 푸시 암호화·VAPID | 웹 푸시(NOTI) | 후보 `nl.martijndwars:web-push`는 마지막 릴리스가 오래돼 3절 기준에 걸림 → JDK 21 표준(ECDH·HMAC·AES-GCM·ES256·HttpClient)으로 RFC 8291·8292를 직접 구현(`notification.infra.webpush`). 새 의존성 없음 | B 2026-11-17 |
| JUnit 5, Testcontainers(Boot BOM), ArchUnit 1.5.1, JaCoCo | 테스트 | 실제 PostgreSQL로 통합 테스트, 구조 규칙 | 고은우 2026-11-16 |
| Docker 공식 이미지: `eclipse-temurin:21-jdk/jre-noble`, `postgres:16-alpine`, `caddy:2.10-alpine`, `node:24-alpine` | 시연 서버 배포(`Dockerfile`, `compose.prod.yaml`, `deploy.sh`) | 공식 이미지, Arm 서버용 이미지 제공. Java 21·PostgreSQL 16은 개발 환경과 같은 버전, Node 24는 현재 LTS. Dependabot이 주 1회 태그 갱신 | 고은우 2026-11-17 |

## 5. AI 사용·검증 기록

| 날짜 | 무엇을 | 도구 | 검증 방법 | 발견한 문제 → 조치 |
| --- | --- | --- | --- | --- |
| 2026-11-16 | 기획 문서 초안(prd·api·erd·architecture·UserFlow) | Claude Code | 팀 검토, 미션·점검 근거 논문을 원문과 대조 | 인용 연도 오류 1건 → 수정. 문서 사이 수치 불일치(시연 성장 수치) → 수정 |
| 2026-11-16 | 프론트 전달 사항 대조, CSRF 설정 검토 | Claude Code | Spring Security 문서와 대조 | 기본 CSRF 설정으로는 첫 가입 요청이 403 → SPA 방식 + 쿠키 발급 필터로 문서 수정 |
| 2026-11-16 | 백엔드 뼈대: Gradle 설정, 공통 에러 처리·보안 설정, ArchUnit·에러 응답·통합 테스트 | Claude Code | 버전은 Maven Central 메타데이터로, 클래스 위치는 Spring Boot 4.1·Testcontainers 2.0 API 문서로 대조. 테스트 16개 통과(실제 PostgreSQL 16 포함) | AI가 처음 고른 Boot 4.0은 지원 6주 남음 → 4.1.2. 쿠키 발급 테스트가 다른 테스트 뒤에서만 실패 → 테스트 도구 `csrf()`의 부작용으로 확인, 실제 흐름으로 테스트 교체 |
| 2026-11-16 | 의존성 검사를 OSV-Scanner로 교체, main 보호·비밀값 검사 Git 훅 | Claude Code | 취약한 log4j 2.14.1 가짜 잠금 파일 → 검사 실패 확인. main 푸시·가짜 비밀값 커밋 → 훅이 거부하는 것 확인 | 레포 권한이 write뿐이라 GitHub 의존성 검토를 쓸 수 없음 → 레포 설정이 필요 없는 도구로 교체. 액션·이미지는 1개월 지난 릴리스를 다이제스트로 고정 |
| 2026-11-17 | 공용 예약 작업 기록 `job_runs`(`common.job.JobRuns`) | Claude Code | 테스트를 먼저 쓰고(같은 날짜 한 번만, 실패 후 재실행, 8개 스레드 동시 호출, 죽은 실행 재실행, 밀린 날짜 순서) 실제 PostgreSQL로 10개 통과, 3회 반복해도 통과. 보완한 조건을 일부러 빼고 돌려 테스트가 실패하는 것 확인 | 오래 걸린 옛 실행이 끝나면서 새 실행의 기록을 DONE으로 덮어쓸 수 있었음 → 실행마다 started_at을 표식으로 써서 자기 기록만 끝내게 수정. 처음 쓴 테스트는 이 버그를 못 잡는 순서였음 → 위험한 순서로 테스트를 다시 씀 |
| 2026-11-17 | 도움받을 곳 목록 API `GET /help/items`, 시드(채널·상담 12개 + 서울 25개 구 센터) | Claude Code | 항목 ID를 프론트 목업과 대조, 실제 PostgreSQL로 통합 테스트 7개(쿠키 없이 조회, 24시간 채널 우선 정렬, 구 조건, 규칙 키, ETag 304) | 웹 슬라이스 테스트가 새 컨트롤러까지 올려 기존 에러 응답 테스트가 깨짐 → 테스트 대상 컨트롤러를 지정. 구 센터 전화·주소는 시연 전 다시 확인 필요(남은 일) |
| 2026-11-17 | 시연 서버 배포 설정(Dockerfile, 운영 Compose, Caddyfile, deploy.sh, deploy.md) | Claude Code | 로컬에서 이미지 빌드 후 Caddy+api+db를 실제로 띄워 확인: 헬스·목록 200, http→https 308, 화면·API CSP와 HSTS, 정적 파일 캐시, api가 root가 아님, 메모리 사용량. 빌드된 화면에 인라인 스크립트가 없는 것 확인 | Compose 오류 문구 속 콜론 때문에 YAML이 깨짐 → 문구 수정. 처음 쓴 점검표의 `curl -I`(HEAD)는 API에서 401이 나와 헤더 확인이 안 됨 → GET으로 수정. 프론트가 Google Fonts와 서비스 워커 글꼴 캐시를 써서 `'self'`만으로는 막힘 → 글꼴 도메인만 허용 |
| 2026-11-17 | 웹 푸시 암호화(RFC 8291)·VAPID(RFC 8292)·발송 | Claude Code | RFC 8291 부록 A 공식 테스트 벡터와 결과를 바이트 단위로 대조(일치). VAPID JWT는 공개키로 서명 검증, 로컬 HTTP 서버로 실제 발송해 헤더·410 구독 삭제 확인 | 라이브러리 대신 JDK 표준으로 구현. 테스트 기댓값을 처음에 base64 문자열을 이어 붙여 만듦(86바이트라 잘못된 값) → 헤더·암호문을 따로 풀어 잇도록 수정 |
| 2026-11-18 | 사후 점검으로 찾은 main 문제 수정: 앱 시계 UTC → KST, 계정·기록 삭제 리스너(mood·notification), dependabot 메이저 업데이트 제외 | Claude Code | 앱을 띄워 등록된 `Clock` 빈과 시간대를 직접 출력해 UTC 하나뿐인 것 확인. 새 통합 테스트 3개를 고치기 전 코드에서 돌려 모두 실패, 고친 뒤 통과 확인. dependabot.yml은 YAML 파서로 읽어 확인 | 리뷰 없이 머지되어 CI(빈 DB·시간대 미검사)가 못 잡은 문제들. 리스너는 다른 모듈 담당(B) 코드라 B 리뷰 요청 |
| 2026-11-18 | 공공서비스 API 동기화(HELP-04): `GovServiceClient`, `ServiceSyncService`, 04:00 스케줄러, `service_sync_log` | Claude Code | 외부 API는 가짜 서버(MockRestServiceServer)로만 호출. 값 검증 규칙·클라이언트·동기화(실패 시 기존 값 유지, 같은 날 한 번, 키 없음) 테스트 25개 추가, 전체 58개 통과·`*.domain` 커버리지 통과. 실제 키로 serviceList를 9개 서비스 ID 전부 호출해 확인(DB는 건드리지 않음) | 처음 쓴 `Authorization: Infuser` 헤더 인증은 실제로 400 → `serviceKey` 쿼리 파라미터로 수정(URL에 키가 들어가므로 URL을 로그·예외에 남기지 않음). 필드명·`cond` 필터는 맞음. 전화문의는 `기관명/번호\|\|기관명/번호` 형식(1388은 지역 번호), 서비스명·신청방법은 시드의 손질한 문구와 다름 → 사람이 결정해 **신청 기한·상세 URL만 덮어쓰기로** 줄임(테스트 57개 통과) |
| 2026-11-18 | 열거값 오류를 api.md대로 `VALIDATION_FAILED`+필드 경로로, 숫자 열거값 거부 | Claude Code | Jackson 3 예외 API(`MismatchedInputException.getTargetType`, `JacksonException.getPath`)를 jar에서 직접 확인. 테스트를 먼저 써서 고치기 전 4개 실패 확인, 숫자 거부 설정을 끄면 다시 실패하는 것 확인. 짝으로 타입 불일치는 `MALFORMED_REQUEST` 유지 테스트 | 테스트를 쓰다가 열거값 자리의 숫자가 순서로 받아들여져 200이 나는 것을 발견(SEC-05 구멍) → 같은 PR에서 설정으로 막음 |

구현을 시작하면 PR 단위로 이 표에 한 줄씩 추가한다.
