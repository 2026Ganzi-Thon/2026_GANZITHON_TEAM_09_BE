package com.haengunping.character;

import com.haengunping.common.job.CatchUpResult;
import java.time.LocalDate;

/** 밀린 확정 처리를 바로 돌리는 창구(CHR-03). 시연 시계를 바꿨을 때(DEMO-01, 담당 C) 부른다. */
public interface ProgressFinalization {

    /** 아직 확정하지 않은 날부터 {@code date}까지 오래된 순으로 확정한다. */
    CatchUpResult finalizeUpTo(LocalDate date);
}
