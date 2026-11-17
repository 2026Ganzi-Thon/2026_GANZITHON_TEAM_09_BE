package com.haengunping.mood;

import java.util.List;

/** 하루 기분 값(MOOD-01). 다른 모듈(C 점검)도 쓰므로 루트 패키지에 둔다. */
public enum Mood {
    GOOD, CALM, OKAY, TIRED, DEPRESSED, LONELY;

    /** 낮은 기분: 지쳐요·우울해요·외로워요(prd.md 3절). */
    public static final List<Mood> LOW = List.of(TIRED, DEPRESSED, LONELY);

    public boolean isLow() {
        return LOW.contains(this);
    }
}
