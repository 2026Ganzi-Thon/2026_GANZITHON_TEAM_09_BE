package com.haengunping.character.domain;

import java.util.random.RandomGenerator;

/** 캐릭터 종류. shortcut: 디자이너 확정 전 임시 3종(prd.md Q5), 확정되면 값만 바꾼다(DB는 varchar라 마이그레이션 불필요). */
public enum CharacterKind {
    SEED, CLOVER, SPROUT;

    public static CharacterKind random(RandomGenerator random) {
        CharacterKind[] all = values();
        return all[random.nextInt(all.length)];
    }
}
