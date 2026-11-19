#!/usr/bin/env bash
# 시연 서버에서 실행한다. 두 저장소를 최신으로 받고, 프론트를 빌드하고, 컨테이너를 다시 올린다.
#   ./deploy/deploy.sh          배포
#   ./deploy/deploy.sh backup   DB 백업만(backups/ 아래에 저장)
#   ./deploy/deploy.sh reset    사용자 데이터를 모두 지우고 시드만 남긴다(행사 직전, deploy/reset.sql). 백업을 먼저 받는다
# 시연 중에는 돌리지 않는다(프론트 빌드 동안 몇 초 화면이 비고, api가 다시 뜨는 동안 요청이 실패한다).
set -euo pipefail

cd "$(dirname "$0")/.."          # 백엔드 저장소 루트
[ -f .env ] || { echo ".env가 없습니다. deploy/.env.prod.example을 복사해 채워 주세요." >&2; exit 1; }

FRONTEND_DIR=$(grep -E '^FRONTEND_DIR=' .env | cut -d= -f2- || true)
FRONTEND_DIR=${FRONTEND_DIR:-../frontend}
COMPOSE=(docker compose -f compose.prod.yaml)

backup() {
  mkdir -p backups
  local file="backups/haengunping-$(date +%Y%m%d-%H%M%S).sql.gz"
  "${COMPOSE[@]}" exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d haengunping' | gzip > "$file"
  chmod 600 "$file"
  BACKUP_FILE=$file
  echo "백업: $file"
}

# api가 실제로 요청을 받을 때까지 기다린다. `start`가 성공해도 Spring Boot가 떴다는 뜻은 아니다.
# api 포트는 밖에 열지 않으므로 같은 네트워크의 proxy(Caddy, busybox wget)에서 부른다.
wait_api_up() {
  local i
  for i in $(seq 1 30); do
    if "${COMPOSE[@]}" exec -T proxy wget -q -O - http://api:8080/api/v1/health 2>/dev/null | grep -q '"UP"'; then
      echo "api 정상(health UP)"
      return 0
    fi
    sleep 2
  done
  echo "api가 60초 안에 뜨지 않았습니다. docker compose -f compose.prod.yaml logs api 로 사유를 보세요." >&2
  return 1
}

# 사용자 데이터를 모두 지운다(이슈 #57). 지우기 전에 백업을 받고, 그 백업이 온전한지 확인한 뒤에만 진행한다.
reset() {
  [ -n "$("${COMPOSE[@]}" ps -q db 2>/dev/null)" ] \
    || { echo "db 컨테이너가 떠 있지 않습니다. 먼저 ./deploy/deploy.sh 로 올려 주세요." >&2; exit 1; }
  local site
  site=$(grep -E '^SITE_ADDRESS=' .env | cut -d= -f2- | tr -d '\r"' || true)
  [ -n "$site" ] || { echo ".env에 SITE_ADDRESS가 없습니다." >&2; exit 1; }

  echo "이 서버($site)의 사용자 데이터를 모두 지웁니다."
  echo "  지움: 계정·그룹·기분·미션·점검·알림 구독·초대·차단·신고·예약 작업 기록"
  echo "  남음: 도움받을 곳, 미션 주제, 공공서비스 동기화 기록, 스키마 이력"
  echo "  먼저 백업을 받고, 끝나면 api를 다시 시작합니다. 시연 중에는 하지 마세요."
  if [ "${RESET_CONFIRM:-}" != "$site" ]; then
    local answer=""
    read -r -p "계속하려면 서버 주소($site)를 그대로 입력: " answer || true
    [ "$answer" = "$site" ] || { echo "취소했습니다. 아무것도 바뀌지 않았습니다." >&2; exit 1; }
  fi

  echo "== 비우기 전 백업"
  backup
  # 덤프가 중간에 끊겼으면(디스크 부족 등) 되돌릴 수 없으므로 지우지 않는다. pg_dump는 끝에 이 문장을 남긴다.
  if ! gzip -t "$BACKUP_FILE" || ! gzip -dc "$BACKUP_FILE" | grep -q 'PostgreSQL database dump complete'; then
    echo "백업이 온전하지 않습니다($BACKUP_FILE). 지우지 않고 멈춥니다." >&2
    exit 1
  fi

  echo "== api 멈추기 (지우는 동안 새 기록이 들어오지 않게)"
  "${COMPOSE[@]}" stop api
  echo "== 비우기 (한 트랜잭션: 실패하면 아무것도 지워지지 않음)"
  if ! "${COMPOSE[@]}" exec -T db sh -c 'psql -U "$POSTGRES_USER" -d haengunping -v ON_ERROR_STOP=1 -1 -f -' < deploy/reset.sql; then
    echo "비우기에 실패했습니다. 아무것도 지워지지 않았습니다. api를 다시 시작합니다." >&2
    "${COMPOSE[@]}" start api
    wait_api_up || true
    exit 1
  fi
  echo "== api 다시 시작 (메모리의 요청 제한 기록도 비워짐)"
  "${COMPOSE[@]}" start api
  local api_ok=0
  wait_api_up || api_ok=1

  echo "== 결과 (users: 0, 시드: 0보다 커야 함)"
  "${COMPOSE[@]}" exec -T db sh -c 'psql -U "$POSTGRES_USER" -d haengunping -c "SELECT (SELECT count(*) FROM users) AS users, (SELECT count(*) FROM groups) AS groups, (SELECT count(*) FROM help_items) AS help_items, (SELECT count(*) FROM mission_topics) AS mission_topics"'
  echo "되돌리기(잘못 지웠을 때): docs/deploy.md 4절 \"백업 되돌리기\". 방금 백업: $BACKUP_FILE"
  return "$api_ok"
}

case "${1:-}" in
  backup)
    backup
    exit 0
    ;;
  reset)
    reset
    exit 0
    ;;
  "") ;;
  *)
    echo "알 수 없는 명령: $1 (없음 | backup | reset)" >&2
    exit 1
    ;;
esac

echo "== 저장소 최신으로 받기"
git pull --ff-only
git -C "$FRONTEND_DIR" pull --ff-only

echo "== 프론트 빌드 (dist: 실서버 API 모드, dist-demo: 시연용 목업 모드, D-12)"
docker run --rm \
  -v "$(cd "$FRONTEND_DIR" && pwd)":/app -w /app \
  node:24-alpine sh -c 'npm ci --no-audit --no-fund \
    && VITE_API_MODE=http npm run build \
    && VITE_API_MODE=mock npm run build -- --outDir dist-demo'

# DB가 이미 떠 있으면 배포 전에 백업한다(마이그레이션이 실패해도 되돌릴 수 있게).
if [ -n "$("${COMPOSE[@]}" ps -q db 2>/dev/null)" ]; then
  echo "== 배포 전 DB 백업"
  backup
fi

echo "== api 이미지 빌드, 컨테이너 다시 올리기"
"${COMPOSE[@]}" up -d --build --remove-orphans
docker image prune -f > /dev/null

echo "== 상태"
"${COMPOSE[@]}" ps
echo "확인: curl -s https://\$SITE_ADDRESS/api/v1/health  →  {\"status\":\"UP\"}"
