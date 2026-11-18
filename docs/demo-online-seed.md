# DEMO-03/04: 온라인 시연 그룹 시드 (#58)

## 범위

`deploy/seed-demo-online.sql`은 시연 전에 **3개 ONLINE 그룹에 각 4명의 가상 계정**을 넣어 2자리씩 남긴다. `users.is_demo`와 `groups.is_demo`는 `true`, 시연 계정의 `token_hash`는 `NULL`이다. `online_queue`는 건드리지 않는다.

`OnlineMatchService.availableGroup()`은 활성 ONLINE 그룹 중 구성원 1~5명의 그룹을 우선 고려한다. 따라서 새 사용자가 바로 `MATCHED`를 받는다. 동률일 때 어떤 시연 그룹이 선택될지는 보장하지 않는다.

이 스크립트는 **일반 Flyway 마이그레이션이 아니며**, 실사용 계정이 없는 `reset` 직후 한 번 실행하기 위한 별도 SQL이다. 기존 데이터를 지우거나 수정하지 않는다. 최초 상태에서 재실행하면 no-op, 누군가 가입해 DB 상태가 달라졌을 때 재실행하면 중단한다.

## 사전 조건

1. 담당자 승인 후 행사 전 백업 및 `deploy.sh reset` 완료.
2. API/Flyway로 `users`, `groups`, `group_members`, `online_queue`가 생성되어 있음.
3. 실제 사용자 데이터가 없는 상태임.
4. B 담당자의 성장·캐릭터 시드는 아직 별도 구현 대상임. SQL 직접 삽입에서는 `GroupCreatedEvent` / `MemberJoinedEvent`가 발생하지 않으므로 B와 연동하고 최종 PR 전에 검증해야 한다.

## 적용 명령 (실서버 Ubuntu / Docker Compose)

백엔드 저장소 루트에서 다음을 실행한다. `compose.prod.yaml`에서 `db` 서비스의 `POSTGRES_USER` 변수를 읽는다.

```bash
# 먼저 검증된 백업과 reset 완료 여부를 확인한다.
docker compose -f compose.prod.yaml exec -T db \
  sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d haengunping' \
  < deploy/seed-demo-online.sql

# 반드시 신규 사용자 생성 전에 확인한다.
docker compose -f compose.prod.yaml exec -T db \
  sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d haengunping' \
  < deploy/verify-demo-online.sql
```

정상 결과는 `active_members=4`, `free_slots=2`가 각 그룹마다 출력되는 것이다.

## 시연 검증

1. 실제 사용자는 정상 계정 생성 및 인증 과정을 거쳐 온라인 매칭을 요청한다.
2. `MATCHED` 및 `groupId`를 받는지 확인한다.
3. 선택된 시연 그룹에 구성원이 4명에서 5명으로 증가하고 JSON의 `isDemo=true`인지 확인한다.
4. 00:10 정리 잡이 실행되는 행사 일정이라면 시연 계정의 `last_seen_at`이 7일 이상 경과하지 않도록 행사 직전에 시드한다. 오래된 시드는 비활성 정리 대상이 될 수 있다.
5. B 담당 캐릭터·오늘 완료 수의 화면 표시 여부를 별도로 확인한다.

## 유의사항

- 기존 일반 ONLINE 그룹이 있는 DB에 섞어 넣으면 신규 사용자가 다른 그룹에 배정될 수 있으므로 초기 상태 검사를 통과하지 못하도록 만들었다.
- 시연 그룹을 강제로 우선 배정하도록 `OnlineMatchService`의 운영 정책을 변경하지 않았다.
- `deploy.sh reset`은 이 SQL을 자동 실행하지 않는다. 반드시 reset 후 수동으로 실행한다.
- 이 SQL은 시연용 가상 계정만 만들며, 현실 사용자의 계정이나 인증 정보를 변경하지 않는다.
