package com.haengunping.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** 그날 23:59:59 구성원 수·완료 수(daily_group_progress, CHR-02·04). 00:00 확정 처리만 쓴다. */
@Entity
@Table(name = "daily_group_progress")
@IdClass(DailyGroupProgress.Key.class)
public class DailyGroupProgress {

    @Id
    @Column(name = "group_id")
    private long groupId;

    @Id
    private LocalDate date;

    @Column(name = "member_count")
    private short memberCount;

    @Column(name = "completed_count")
    private short completedCount;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected DailyGroupProgress() {
    }

    public DailyGroupProgress(long groupId, LocalDate date, int memberCount, int completedCount, Instant finalizedAt) {
        Growth.dailyUnits(completedCount, memberCount); // 범위 검사
        if (memberCount == 0) {
            throw new IllegalArgumentException("구성원이 0명인 날은 행을 만들지 않는다");
        }
        this.groupId = groupId;
        this.date = date;
        this.memberCount = (short) memberCount;
        this.completedCount = (short) completedCount;
        this.finalizedAt = finalizedAt;
    }

    public int units() {
        return Growth.dailyUnits(completedCount, memberCount);
    }

    public record Key(long groupId, LocalDate date) implements Serializable {
    }
}
