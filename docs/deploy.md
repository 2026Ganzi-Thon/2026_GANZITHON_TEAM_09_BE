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
| [deploy/deploy.sh](../deploy/deploy.sh) | 받기 → 프론트 빌드(실서버용·목업용) → 백업 → 다시 올리기 |
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
git clone https://github.com/thdms1/Todax.git frontend

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
| 멈추기 | `docker compose -f compose.prod.yaml down` (볼륨은 남음) |

- `deploy.sh`는 DB가 떠 있으면 배포 전에 자동으로 백업한다.
- **시연 전날** 마지막 배포 후 백업을 하나 받아 둔다(architecture.md 4절).
- **시연 중에는 배포하지 않는다.** 프론트 빌드 동안 몇 초 화면이 비고 api가 다시 뜨는 동안 요청이 실패한다.
- `docker compose down -v`는 DB와 인증서를 지운다. 쓰지 않는다.

### 행사 당일 계정 생성 한도 올리기 (DEMO-05)

행사장은 심사위원·관람객이 한 공인 IP를 같이 쓴다. 평소 한도(SEC-08, IP당 10개/시간)로는 10번째부터 가입이 429로 막힌다. 코드 변경 없이 `.env`로 올린다.

1. 서버의 `.env`에서 `ACCOUNT_CREATE_LIMIT`을 올린다(예: `200`). 창은 `ACCOUNT_CREATE_WINDOW`(기본 `PT1H`).
2. api만 다시 띄운다: `docker compose -f compose.prod.yaml up -d api` (`.env`가 바뀌면 컨테이너가 새로 만들어진다). **시연 전에 미리 한다.** 재시작 중에는 요청이 실패한다.
3. 확인: 같은 IP에서 11번째 `POST /api/v1/accounts`가 201인지 본다.
4. **행사가 끝나면 `ACCOUNT_CREATE_LIMIT=10`으로 되돌리고 같은 방법으로 api를 다시 띄운다.** 값을 지우면 기본 10이 쓰인다.

- 0 이하나 잘못된 값(`ACCOUNT_CREATE_WINDOW=1h` 등)이면 api가 뜨지 않는다. `docker compose -f compose.prod.yaml logs api`로 사유를 본다.
- 한도는 api 프로세스 메모리에 있어 재시작하면 지금까지 센 횟수도 0이 된다.
- Caddy가 붙인 `X-Forwarded-For`로 실제 사용자 IP를 센다(`forward-headers-strategy: native`). 행사장 와이파이 하나는 IP 하나로 보인다.

## 5. 보안 점검표 (배포 후)

| 확인 | 방법 | 기대 |
| --- | --- | --- |
| HTTPS·HSTS | `curl -sI https://<도메인>/` | `strict-transport-security` 있음 |
| 화면 CSP | 같은 명령 | `content-security-policy: default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self' ...`(외부 도메인 없음) |
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
