package com.haengunping.character.application;

import com.haengunping.character.domain.CharacterKind;
import com.haengunping.character.domain.GroupCharacter;
import com.haengunping.character.infra.GroupCharacterRepository;
import com.haengunping.group.GroupCreatedEvent;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹을 만들면 무작위 종류의 캐릭터를 0에서 시작한다(CHR-01).
 * group의 생성 트랜잭션 안에서 실행한다(MANDATORY). 캐릭터 없는 그룹이 남지 않게 실패하면 그룹 생성도 되돌아간다.
 */
@Component
class CharacterStarter {

    private final GroupCharacterRepository characters;
    private final Clock clock;

    CharacterStarter(GroupCharacterRepository characters, Clock clock) {
        this.characters = characters;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void on(GroupCreatedEvent event) {
        characters.save(new GroupCharacter(event.groupId(), CharacterKind.random(ThreadLocalRandom.current()), LocalDate.now(clock)));
    }
}
