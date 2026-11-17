# 행운핑 백엔드

[![CI](https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_09_BE/actions/workflows/ci.yml/badge.svg)](https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_09_BE/actions/workflows/ci.yml)

함께 하는 작은 활동으로 일상을 돌보고, 힘든 날엔 실제 도움으로 이어주는 정신·심리 건강 케어 웹앱 **행운핑**의 API 서버입니다. 2026 간지톤 9팀.

- 로그인 없이 시작합니다. 닉네임만으로 익명 계정을 만듭니다.
- 그룹이 같은 주제의 미션을 각자 기분·시간에 맞는 크기로 하고, 함께 캐릭터를 키웁니다. 그룹 안의 소통은 이모티콘 핑뿐입니다.
- 매일 기분 한 번, 주 1회 마음 점검(PHQ-2·GAD-2)으로 상태를 보고, 필요하면 공공 지원으로 연결합니다. 기분·점검은 본인만 봅니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/prd.md](docs/prd.md) | 요구사항과 수용 기준(ID: ACC·GRP·MOOD·MIS·PING·CHR·CHK·HELP·NOTI·DEMO·NFR·SEC) |
| [docs/api.md](docs/api.md) | API 명세. 프론트와의 계약 원본 |
| [docs/erd.md](docs/erd.md) | 테이블·제약·인덱스·삭제 규칙 |
| [docs/architecture.md](docs/architecture.md) | 모듈 구조, 예약 작업, 배포, 보안 구성, 테스트 |
| [docs/UserFlow.md](docs/UserFlow.md) | 화면 ID(S01~S53)와 흐름 |
| [docs/deploy.md](docs/deploy.md) | 시연 서버(AWS VM 1대) 만들기·배포·백업·배포 후 보안 점검 |
| [docs/decisions.md](docs/decisions.md) | 설계 결정과 이유·대안·한계 |
| [docs/ai-usage.md](docs/ai-usage.md) | AI 생성 코드·외부 의존성 검증 기록 |
| [SECURITY.md](SECURITY.md) | 위협·대응·테스트·남은 위험 |

## 기술 스택

Java 21 · Spring Boot 4.1 · Gradle 9 · PostgreSQL 16 · Flyway · Spring Security · JUnit 5 · Testcontainers · ArchUnit · Docker Compose · Caddy

외부 AI(LLM)는 쓰지 않습니다. 미션·도움 연결은 모두 설명 가능한 규칙 기반입니다([D-06](docs/decisions.md)).

## 구조

모듈형 모놀리스입니다. 기능별 패키지로 나누고, 모듈 사이 규칙은 `ArchitectureTest`가 지킵니다.

```
src/main/java/com/haengunping/
├─ common        시계(Clock)·에러 응답·보안 설정·요청 제한·job_runs
├─ account   (A) 익명 계정, 세션 쿠키, 동의
├─ group     (A) 그룹, 초대 코드, 온라인 대기열, 차단·신고
├─ mood      (B) 기분, 가벼운 도움 카드
├─ mission   (B) 주제, 오늘 미션, 시작·완료
├─ ping      (B) 이모티콘 핑
├─ character (B) 캐릭터 성장·확정·도감
├─ notification (B) 웹 푸시, 알림 설정
├─ check     (C) 주간 마음 점검
├─ help      (C) 도움받을 곳 목록, 공공서비스 동기화
└─ demo      (C) 시연 시계 (demo 프로필에서만)

모듈 안: api(컨트롤러·DTO) / application(서비스) / domain(엔티티·규칙) / infra(리포지토리·외부 API) / XxxQuery.java(공개 인터페이스)
```

## 로컬 실행

필요한 것: JDK 21, Docker

```bash
cp .env.example .env
```

```bash
docker compose up -d db
```

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

API는 `http://localhost:8080/api/v1`, 상태 확인은 `GET /api/v1/health`입니다. 프론트는 Vite 개발 서버의 `/api` 프록시로 이 주소를 씁니다.

## 테스트

```bash
./gradlew test jacocoTestReport
```

- 테스트 이름은 PRD ID로 시작합니다. 예: `CHR02_완료한_구성원이_23시에_나가면_분모와_분자에서_빠진다`. 요구사항 하나의 테스트를 찾으려면 ID로 검색하면 됩니다.
- 통합 테스트는 Testcontainers로 실제 PostgreSQL을 띄웁니다(Docker 필요).
- 커버리지 리포트: `build/reports/jacoco/test/html/index.html`

## 협업 규칙

이 레포는 대회 organization 소유라 우리 팀에는 설정 권한이 없습니다. 그래서 main 보호를 GitHub가 아니라 **팀 규칙과 각자 PC의 Git 훅**으로 지킵니다.

### Git 훅

**훅이란:** `git commit`이나 `git push`를 할 때 Git이 자동으로 먼저 실행하는 스크립트입니다. 스크립트가 실패하면 커밋·푸시가 멈춥니다. 이 레포는 `.githooks/` 폴더에 두 개를 둡니다.

| 훅 | 언제 도나 | 무엇을 막나 |
| --- | --- | --- |
| `pre-commit` | `git commit` 직전 | 커밋하려는 파일에 API 키·비밀번호 같은 비밀값이 있으면 커밋을 거부합니다(gitleaks) |
| `pre-push` | `git push` 직전 | main 브랜치에 바로 푸시하면 거부합니다 |

**켜기: 클론한 뒤 한 번만** 레포 폴더에서 실행합니다. 클론을 새로 받으면 다시 해야 합니다.

```bash
git config core.hooksPath .githooks
```

켜졌는지 확인합니다. `.githooks`가 나오면 켜진 것입니다.

```bash
git config core.hooksPath
```

**필요한 것:** `pre-commit`은 gitleaks가 설치돼 있으면 그것을, 없으면 Docker로 gitleaks를 실행합니다. 둘 다 없으면 경고만 하고 커밋을 통과시킵니다(CI가 다시 검사). Windows에서는 Git for Windows(Git Bash)가 훅을 실행하므로 따로 설치할 것은 없습니다.

**막혔을 때**

1. `main에 직접 푸시할 수 없습니다`가 나왔다 → main에서 커밋한 것입니다. 커밋을 브랜치로 옮깁니다.

   ```bash
   git switch -c feat/<모듈>-<내용>
   ```

   ```bash
   git push -u origin HEAD
   ```

   그다음 내 PC의 main을 원래대로 되돌립니다(방금 만든 브랜치에 커밋이 남아 있으므로 지워지지 않습니다).

   ```bash
   git branch -f main origin/main
   ```

2. `leaks found`가 나왔다 → 출력의 `File:`·`Line:` 위치를 봅니다.
   - 진짜 비밀값이면 코드에서 빼고 `.env`로 옮깁니다. 코드는 `${DB_PASSWORD}`처럼 환경변수 이름만 씁니다.
   - 비밀값이 아닌데 걸렸다면(예: 테스트용 가짜 값) C에게 알려 `.gitleaks.toml` 예외에 넣습니다. 혼자 판단해 훅을 건너뛰지 않습니다.

3. 이미 비밀값을 푸시했다 → 지워도 GitHub 기록에 남습니다. 바로 C에게 알리고 그 키를 **새로 발급**해 바꿉니다.

**우회하지 않습니다.** `git commit --no-verify`, `git push --no-verify`는 훅을 건너뜁니다. 서버가 막아 주지 않으므로 팀 규칙으로 쓰지 않습니다.

### 작업 순서

```bash
git switch main
```

```bash
git pull
```

```bash
git switch -c feat/<모듈>-<내용>
```

작업하고 커밋한 뒤 브랜치를 올립니다.

```bash
git push -u origin HEAD
```

GitHub에서 PR을 열고, 리뷰 1명의 승인과 CI 초록불(test·secrets·dependencies)을 확인한 뒤 **Squash and merge**로 합칩니다.

### 규칙

- main에 직접 푸시하지 않습니다. 훅을 `--no-verify`로 우회하지 않습니다.
- 리뷰는 돌아가며 봅니다: A → B, B → C, C → A.
- CI가 빨간불이면 머지하지 않습니다. 의존성 검사가 실패하면 버전을 올리거나 [docs/ai-usage.md](docs/ai-usage.md) 3절대로 예외를 적습니다.
- 의존성을 바꾸면 잠금 파일을 갱신해 같은 PR에 올립니다: `./gradlew dependencies --write-locks`
- 마이그레이션: `V<yyyyMMddHHmm>__<설명>.sql`, 자기 모듈 테이블만([erd.md](docs/erd.md) 6절). 머지된 마이그레이션은 고치지 않고 새 파일로 바꿉니다.
- 응답 모양을 바꾸면 [docs/api.md](docs/api.md)를 같은 PR에서 고치고 프론트에 알립니다.
- 비밀값은 `.env`에만 둡니다. 이 레포는 공개 레포입니다.
- AI로 만든 코드는 PR 템플릿의 확인 항목을 채웁니다([docs/ai-usage.md](docs/ai-usage.md)).

## 팀

| 담당 | 영역 |
| --- | --- |
| A | 계정·그룹·공통 보안 설정 |
| B | 기분·미션·핑·캐릭터·알림 |
| C | 마음 점검·도움받기·시연·보안 점검 |
