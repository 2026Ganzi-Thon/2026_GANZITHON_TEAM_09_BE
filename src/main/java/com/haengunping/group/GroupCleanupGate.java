package com.haengunping.group;
import java.time.LocalDate;
/** B·C가 전일 성장 확정 및 job_runs 처리를 보장한 뒤 true를 돌려줘야 한다. */
public interface GroupCleanupGate { boolean mayRemoveInactive(LocalDate previousDate); }
