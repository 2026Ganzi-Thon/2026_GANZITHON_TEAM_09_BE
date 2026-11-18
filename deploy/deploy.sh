#!/usr/bin/env bash
# 시연 서버에서 실행한다. 두 저장소를 최신으로 받고, 프론트를 빌드하고, 컨테이너를 다시 올린다.
#   ./deploy/deploy.sh          배포
#   ./deploy/deploy.sh backup   DB 백업만(backups/ 아래에 저장)
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
  echo "백업: $file"
}

if [ "${1:-}" = "backup" ]; then
  backup
  exit 0
fi

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
