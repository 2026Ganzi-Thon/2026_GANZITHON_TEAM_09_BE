# 서버 배포

해커톤이 서버를 주지 않아 팀이 AWS에 VM 1대를 만들어 쓴다. 서버 1대에 Docker Compose로 세 컨테이너를 올린다.

```
인터넷 ─443→ proxy(Caddy) ─┬─ SITE_ADDRESS      ─┬─ /api/*  → api(Spring Boot :8080) → db(PostgreSQL :5432)
                           │                     └─ 나머지 → 프론트 빌드 결과(dist, 실서버 API 모드)
                           └─ DEMO_SITE_ADDRESS ─── 프론트 목업 빌드(dist-demo). API 없음, 데이터·시계는 기기 안(D-12)
```

- 밖으로 열리는 포트는 80·443뿐이다. api·db 포트는 열지 않는다.
- 화면과 API가 같은 도메인이라 쿠키·CSRF 설계(D-02)가 그대로 동작한다.
- 실서버는 출시 그대로 돈다(시연 시계 없음). 며칠이 걸리는 시연 장면은 목업 사이트에서 보여 준다(D-12). DuckDNS는 `demo.<이름>.duckdns.org` 같은 하위 이름도 같은 IP로 보내므로 DNS 설정이 따로 필요 없다.
- 예약 작업(자정 확정, 04:00 동기화)과 요청 제한이 서버 안에서 돌므로 서버는 항상 켜져 있어야 한다. 서버리스(Cloud Run 등)·Vercel을 쓰지 않는 이유다.

| 파일 | 역할 |
| --- | --- |
| [Dockerfile](../Dockerfile) | api 이미지. 서버에서 빌드, root가 아닌 사용자로 실행 |
| [compose.prod.yaml](../compose.prod.yaml) | proxy·api·db 정의, 메모리 상한 |
| [deploy/Caddyfile](../deploy/Caddyfile) | HTTPS 자동 발급, `/api` 연결, HSTS, 화면 CSP, 목업 사이트 |
| [deploy/deploy.sh](../deploy/deploy.sh) | 받기 → 프론트 빌드(실서버용·목업용) → 백업 → 다시 올리기. `backup`·`reset` 명령도 있다 |
| [deploy/reset.sql](../deploy/reset.sql) | 행사 직전 비우기: 사용자 데이터를 모두 지우고 시드만 남긴다(`deploy.sh reset`이 실행) |
| [deploy/.env.prod.example](../deploy/.env.prod.example) | 서버 `.env` 양식 |

## 1. 서버 만들기 (처음 한 번, 담당 C)

1. **AWS 계정**: 새 계정은 무료 플랜 크레딧(100~200달러, 6개월)을 받는다. 결제 알림(Budgets)을 10달러로 걸어 둔다.
2. **EC2 인스턴스 시작** (리전: 서울 `ap-northeast-2`)
   - 이미지: Ubuntu Server 24.04 LTS, **64비트(Arm)**
   - 유형: `t4g.small` (2 vCPU, 2GB)
   - 키 페어: 새로 만들고 `.pem`은 각자 PC에만 둔다(저장소·채팅에 올리지 않는다)
   - 저장소: 20GB gp3
   - 보안 그룹 인바운드: `22`(내 IP만), `80`·`443`(어디서나), `443/UDP`(HTTP/3, 선택)
3. **고정 IP**: 탄력적 IP를 하나 만들어 인스턴스에 연결한다(재시작해도 IP가 안 바뀜).
4. **도메인**: [duckdns.org](https://www.duckdns.org)에서 이름(예: `haengunping`)을 만들고 3의 IP를 넣는다.

## 2. 서버 준비 (처음 한 번)

```bash
ssh -i 키.pem ubuntu@<탄력적 IP>
```

```bash
# Docker와 Compose·Buildx
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 docker-buildx git
sudo usermod -aG docker ubuntu   # 다시 접속하면 sudo 없이 docker 사용

# 스왑 2GB: 메모리 2GB에서 api가 떠 있는 채로 새 이미지를 빌드하면 모자란다
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

```bash
# 두 저장소를 나란히 받는다
mkdir -p ~/app && cd ~/app
git clone https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_09_BE.git backend
git clone https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_09_FE.git frontend

# 비밀값
cd backend
cp deploy/.env.prod.example .env && chmod 600 .env
openssl rand -base64 24   # 이 값을 .env의 DB_PASSWORD에
nano .env                 # SITE_ADDRESS, DEMO_SITE_ADDRESS, DB_PASSWORD, DATA_GO_KR_KEY 등 채우기
```

`.env`는 서버에만 있다. 채팅·노션·저장소에 올리지 않는다.

## 3. 배포

```bash
cd ~/app/backend && ./deploy/deploy.sh
```

처음에는 이미지 빌드와 인증서 발급 때문에 몇 분 걸린다(PC에서 api 이미지 빌드 약 1분 20초, 서버는 더 느림). 확인:

```bash
curl -s https://haengunping.duckdns.org/api/v1/health
```

`{"status":"UP"}`이 나오고, 휴대폰 브라우저로 주소를 열어 화면이 뜨면 끝이다.

## 4. 운영

| 할 일 | 명령(백엔드 폴더에서) |
| --- | --- |
| 다시 배포(main 반영) | `./deploy/deploy.sh` |
| 로그 보기 | `docker compose -f compose.prod.yaml logs -f api` |
| 상태 | `docker compose -f compose.prod.yaml ps` |
| DB 백업 | `./deploy/deploy.sh backup` → `backups/` |
| 백업 되돌리기 | `gunzip -c backups/<파일> \| docker compose -f compose.prod.yaml exec -T db psql -U haengunping -d haengunping` (빈 DB에) |
| 행사 직전 비우기 | `./deploy/deploy.sh reset` ([아래](#행사-직전-비우기-deploysh-reset)) |
| 멈추기 | `docker compose -f compose.prod.yaml down` (볼륨은 남음) |

- `deploy.sh`는 DB가 떠 있으면 배포 전에 자동으로 백업한다.
- `deploy.sh`는 마지막에 proxy를 매번 새로 만든다. `deploy/Caddyfile`은 파일 하나만 컨테이너에 연결돼 있어서, `git pull`로 바뀌어도 돌던 proxy는 예전 파일을 계속 본다(2026-11-20 CSP의 `blob:`이 반영되지 않아 사진 넣기가 실패한 원인). 스크립트 없이 Caddyfile만 바꿨다면 `docker compose -f compose.prod.yaml up -d --force-recreate --no-deps proxy`.
- **시연 전날** 마지막 배포 후 백업을 하나 받아 둔다(architecture.md 4절).
- **시연 중에는 배포하지 않는다.** 프론트 빌드 동안 몇 초 화면이 비고 api가 다시 뜨는 동안 요청이 실패한다.
- `docker compose down -v`는 DB와 인증서를 지운다. 쓰지 않는다.


### 행사 직전 비우기 (`deploy.sh reset`)

연습·점검으로 쌓인 계정과 기록을 지우고 출시 직후 상태로 만든다(DEMO-03). 시연 중에는 하지 않는다.

```bash
cd ~/app/backend && ./deploy/deploy.sh reset
```

| | 내용 |
| --- | --- |
| 지우는 것 | `public`의 테이블 중 아래 남길 것을 뺀 **전부**: 계정·그룹·구성원·초대·대기열·차단·신고·기분·미션·성장·캐릭터·점검·알림 구독·설정·도움 카드·`job_runs`. 식별자 번호도 1부터 다시 시작 |
| 남기는 것 | `flyway_schema_history`(지우면 다음 기동 때 마이그레이션이 처음부터 다시 돌아 실패), `help_items`(도움받을 곳 시드), `mission_topics`(미션 주제), `service_sync_log`(동기화 기록) |
| 새 테이블 | 남길 목록에 없으면 기본으로 지워진다. 시드나 운영 기록이 생기면 `deploy/reset.sql`의 목록에 더하고 `ResetScriptTest`의 `KEEP`도 같이 고친다 |

순서(스크립트가 한다):

1. **서버 주소 확인**: `.env`의 `SITE_ADDRESS`를 그대로 입력해야 진행한다(엉뚱한 서버에서 실수로 돌리는 것을 막는다). 입력 없이 한 줄로 돌리려면 `RESET_CONFIRM=<주소> ./deploy/deploy.sh reset`.
2. **백업**: `backups/`에 저장하고, 압축이 온전하고 덤프가 끝까지 쓰였는지(`PostgreSQL database dump complete`) 확인한다. 아니면 지우지 않고 멈춘다.
3. **api 멈춤**: 지우는 동안 새 기록이 들어오지 않게 한다. 화면은 잠깐 API 오류가 난다.
4. **비우기**: 한 트랜잭션(`psql -1`, `ON_ERROR_STOP`)이라 실패하면 아무것도 지워지지 않고, api를 다시 시작한 뒤 멈춘다. `CASCADE`를 쓰지 않아 시드 테이블이 지울 테이블을 참조하게 되면 같이 지우지 않고 오류로 멈춘다.
5. **api 다시 시작**: 메모리에만 있는 요청 제한 기록(계정 생성 IP당 한도 등)도 비워진다. 컨테이너 시작 성공이 Spring Boot 기동을 뜻하지 않으므로, proxy 컨테이너에서 `http://api:8080/api/v1/health`가 `UP`일 때까지 최대 60초 기다린다.
6. **결과 출력**: `users`·`groups`는 0, `help_items`·`mission_topics`는 0보다 커야 한다. 5에서 api가 뜨지 않았으면 결과를 보여 준 뒤 실패(종료 코드 1)로 끝난다 — `logs api`로 사유를 본다.

**행사 전에 한 번, 백업으로 되돌릴 수 있는지 확인한다.** 스크립트는 백업이 끝까지 쓰였는지만 보고 실제 복원은 해 보지 않는다. 실서버 DB를 건드리지 않게 같은 db 컨테이너 안의 임시 DB에 넣어 본다.

```bash
F=backups/<방금 받은 파일>.sql.gz
docker compose -f compose.prod.yaml exec -T db sh -c 'createdb -U "$POSTGRES_USER" restore_check'
gunzip -c "$F" | docker compose -f compose.prod.yaml exec -T db sh -c 'psql -U "$POSTGRES_USER" -d restore_check -v ON_ERROR_STOP=1 -q'
docker compose -f compose.prod.yaml exec -T db sh -c 'psql -U "$POSTGRES_USER" -d restore_check -c "SELECT count(*) AS users FROM users"'
docker compose -f compose.prod.yaml exec -T db sh -c 'dropdb -U "$POSTGRES_USER" restore_check'
```

오류 없이 끝나고 `users`가 백업 당시 수와 같으면 된다. 확인 뒤 `restore_check`는 반드시 지운다(위 마지막 줄).

비운 뒤에 할 일: 온라인 그룹 자리 시드(DEMO-04, #58, `./deploy/deploy.sh seed`, 아래 절), 행사용 계정 생성 한도(DEMO-05, #59), 5절 보안 점검표. 잘못 지웠으면 방금 받은 백업을 위 "백업 되돌리기"로 되돌린다.

### 시연용 ONLINE 그룹 시드 (#58, DEMO-04)

행사 직전 `deploy.sh reset`을 완료한 뒤, 실제 계정 생성 전에 수동으로 시드를 삽입한다. `deploy/seed-demo-online.sql`은 시연 계정 12명, ONLINE 그룹 3개(그룹당 4명), 진행 중 `SEED` 캐릭터 3개(그룹당 1개)를 만든다. 각 그룹에는 신규 사용자 2명이 참여할 수 있다. 가상 계정은 `is_demo=true`, `token_hash=NULL`이므로 로그인할 수 없다.

**시드 적용 시점:** 시연 계정의 `last_seen_at`은 최초 생성 시점으로 설정되며 로그인으로 갱신되지 않는다. GRP-12의 00:10 비활성 구성원 정리 작업에 따라 7일이 지나면 시연 그룹에서 제외될 수 있으므로, **행사 시작일 기준 6일 이내에 시드를 적용한다.** 가능하면 행사 전날 또는 당일에 적용한다. 행사가 연기되면 시연 그룹의 정상 상태를 다시 확인하고, 필요한 경우 운영 담당자와 초기화 및 재적용을 협의한다.

시드는 일반 Flyway 마이그레이션이 아니다. 기존 계정·그룹·캐릭터·대기열이 있는 DB에서는 삽입을 거부한다. 최초 시드가 온전한 상태에서 다시 실행하면 데이터를 변경하지 않는다. 신규 사용자가 가입하거나 그룹에 참여한 뒤에는 재실행하지 않는다.

백업과 승인된 reset이 끝난 뒤, 실제 사용자가 들어오기 전에 서버에서 한 번 실행한다. 시드를 넣고 바로 검증 SQL까지 돌린다. 어느 하나라도 실패하면 오류를 내고 멈춘다.

```bash
./deploy/deploy.sh seed
```

`deploy.sh seed`가 하는 일은 아래 두 명령과 같다. 스크립트를 쓸 수 없을 때만 직접 실행한다.

```bash
docker compose -f compose.prod.yaml exec -T db \
  sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d haengunping' \
  < deploy/seed-demo-online.sql
docker compose -f compose.prod.yaml exec -T db \
  sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d haengunping' \
  < deploy/verify-demo-online.sql
```

검증 SQL은 시연 그룹 3개, 로그인 불가 계정 12개, 그룹당 활성 구성원 4명, 진행 중 캐릭터 1개(`kind=SEED`, KST 시작일), 빈 대기열을 확인한다. 표에는 각 그룹의 `active_members=4`, `free_slots=2`, `active_characters=1`이 출력되어야 한다.

`OnlineMatchService`는 빈자리가 있는 인원 적은 그룹부터 채우므로, 초기 상태에서 정상적인 신규 사용자 6명은 3개 그룹에 분산되어 `MATCHED`로 참여할 수 있다. 6개 자리가 모두 차면 그다음 요청은 일반 온라인 대기열 처리에 따른다. 이 동작은 초기 상태를 전제로 하며, 참가자 탈퇴·정리·다른 그룹 생성 이후에는 달라질 수 있다.

SQL 직접 삽입으로는 `GroupCreatedEvent`가 발생하지 않아 캐릭터를 명시적으로 추가한다. 가상 계정의 `daily_missions` 완료 기록은 넣지 않는다. 따라서 새 사용자가 유일한 완료자라면 오늘 완료 수는 `1/5`로 표시된다. 시연에 미션 완료 시나리오가 필요하면 B 담당자와 합의한 후 **시연용 계정에 한해서** 별도 데이터를 설계한다.

시드를 적용한 뒤에는 실제 테스트 계정을 정상 API로 생성하여 온라인 매칭, 그룹 응답의 `isDemo=true` 및 `character` 존재 여부를 확인한다. 00:00 성장 확정, 00:10 비활성 정리의 동작도 검증한다.

PostgreSQL 시드 통합 테스트는 CI에서 검증한다. 다만 실제 운영 서버에서의 시드 적용과 신규 사용자 매칭·성장 동작은 행사 전에 별도로 확인한다.


### 행사 당일 계정 생성 한도 올리기 (DEMO-05)

행사장은 심사위원·관람객이 한 공인 IP를 같이 쓴다. 평소 한도(SEC-08, IP당 10개/시간)로는 10번째부터 가입이 429로 막힌다. 코드 변경 없이 `.env`로 올린다.

1. 서버의 `.env`에서 `ACCOUNT_CREATE_LIMIT`을 올린다(예: `200`). 창은 `ACCOUNT_CREATE_WINDOW`(기본 `PT1H`).
2. api만 다시 띄운다: `docker compose -f compose.prod.yaml up -d api` (`.env`가 바뀌면 컨테이너가 새로 만들어진다). **시연 전에 미리 한다.** 재시작 중에는 요청이 실패한다.
3. 확인: 같은 IP에서 11번째 `POST /api/v1/accounts`가 201인지 본다.
4. **행사가 끝나면 `ACCOUNT_CREATE_LIMIT=10`으로 되돌리고 같은 방법으로 api를 다시 띄운다.** 값을 지우면 기본 10이 쓰인다.

- 창은 `PT1H` 말고 `1h`·`30m` 같은 짧은 형식도 된다(Spring Boot `Duration` 변환).
- 0 이하나 읽을 수 없는 값(`ACCOUNT_CREATE_LIMIT=0`, `ACCOUNT_CREATE_WINDOW=abc` 등)이면 api가 뜨지 않는다. `docker compose -f compose.prod.yaml logs api`로 사유를 본다.
- 한도는 api 프로세스 메모리에 있어 재시작하면 지금까지 센 횟수도 0이 된다.
- Caddy가 붙인 `X-Forwarded-For`로 실제 사용자 IP를 센다(`forward-headers-strategy: native`). 행사장 와이파이 하나는 IP 하나로 보인다.

## 5. 보안 점검표 (배포 후)

| 확인 | 방법 | 기대 |
| --- | --- | --- |
| HTTPS·HSTS | `curl -sI https://<도메인>/` | `strict-transport-security` 있음 |
| 화면 CSP | 같은 명령 | `content-security-policy: default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self' data:; img-src 'self' data: blob: ...`(외부 도메인 없음). 화면 몇 개를 넘기며 브라우저 콘솔에 CSP 위반이 없는지도 본다 |
| 목업 사이트 | `curl -sI https://<목업 도메인>/`, `curl -s -o /dev/null -w '%{http_code}' https://<목업 도메인>/api/v1/health` | 화면 200 + 같은 CSP·`x-robots-tag: noindex`, API는 404 |
| API CSP | `curl -s -D - -o /dev/null https://<도메인>/api/v1/health` (GET이어야 함. `-I`는 HEAD라 401) | `content-security-policy: default-src 'none' ...` |
| 세션 쿠키 | 브라우저 개발자 도구 → 쿠키 | `__Host-hp_session`, HttpOnly·Secure |
| 닫힌 포트 | 내 PC에서 `nc -zv <IP> 5432`, `nc -zv <IP> 8080` | 둘 다 연결 안 됨 |
| 서버 정보 숨김 | `curl -sI` | `server` 헤더 없음 |

### 로컬 검증 결과 (2026-11-17, `SITE_ADDRESS=localhost`)

| 항목 | 결과 |
| --- | --- |
| 기동 | db healthy → api 3.8초 기동, Flyway 마이그레이션 3개 적용, 재시작 0회 |
| `GET /api/v1/health`, `GET /api/v1/help/items` | 200, 시드 목록 응답 |
| http → https | 308 리다이렉트 |
| 화면 `/`, 없는 경로 `/groups/123` | 200 `index.html`, `Cache-Control: no-cache`, 화면 CSP·HSTS 있음 |
| `/assets/*.js` | `max-age=31536000, immutable` |
| api 응답 | Spring CSP(`default-src 'none'`) + Caddy HSTS, `Server` 헤더 없음 |
| api 실행 사용자 | `app`(root 아님) |
| 메모리(기동 직후) | api 251MB / proxy 15MB / db 72MB |
| 프론트 빌드 결과 | 인라인 스크립트 없음(`/assets/*.js`, `/registerSW.js`만) → `script-src 'self'`로 동작 |

## 6. 비용

| 항목 | 월 비용(대략) |
| --- | --- |
| t4g.small 서울 | 약 15달러 |
| 공인 IPv4(탄력적 IP) | 약 3.6달러 |
| EBS 20GB gp3 | 약 2달러 |

무료 플랜 크레딧으로 해커톤 기간을 충분히 덮는다. 끝나면 인스턴스·탄력적 IP·볼륨을 지운다(탄력적 IP는 연결이 끊긴 채로도 요금이 나간다).
