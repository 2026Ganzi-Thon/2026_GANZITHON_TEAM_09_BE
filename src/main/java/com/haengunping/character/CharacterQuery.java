package com.haengunping.character;

import java.util.Optional;

/** character 모듈이 다른 모듈에 여는 읽기 창구(D-03). 성장 계산은 서버만 한다(CHR-11). */
public interface CharacterQuery {

    /**
     * 진행 중 캐릭터의 표시 값 = 확정 합 + 오늘 임시 성장(오늘 완료 수 ÷ 지금 구성원 수), 최대 100(CHR-05·06).
     * 진행 중 캐릭터가 없으면 비어 있다.
     */
    Optional<CharacterView> current(long groupId, int completedToday, int membersNow);

    record CharacterView(long characterId, String kind, int stage, double progress) {
    }
}
