package com.haengunping.mission.domain;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/** 하루 1개 개인 미션(daily_missions). 배정은 DailyMissionRepository.insertIfAbsent로만 한다. */
@Entity
@Table(name = "daily_missions")
@IdClass(DailyMission.Key.class)
public class DailyMission {

    public enum Place { OUTDOOR, INDOOR }

    public enum Status { NOT_STARTED, STARTED, COMPLETED }

    @Id
    @Column(name = "user_id")
    private long userId;

    @Id
    private LocalDate date;

    @Column(name = "topic_id")
    private short topicId;

    private Short minutes;

    private Short size;

    @Enumerated(EnumType.STRING)
    private Place place;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DailyMission() {
    }

    public DailyMission(long userId, LocalDate date, short topicId) {
        this.userId = userId;
        this.date = date;
        this.topicId = topicId;
    }

    /** 사용자가 크기를 바꿔도(MIS-05) 넘지 못하는 값. smallint 넘침으로 500이 나지 않게 도메인에서 400으로 막는다. */
    public static final int MAX_SIZE = 100;

    /** 시작(MIS-05·06). 다시 부르면 값과 시작 시각을 새로 쓴다. 완료한 뒤에는 바꿀 수 없다. */
    public void start(int minutes, Place place, int size, Instant now) {
        if (completedAt != null) {
            throw new ApiException(ErrorCode.ALREADY_COMPLETED);
        }
        if (!MissionSize.MINUTES.contains(minutes) || size < 1 || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        this.minutes = (short) minutes;
        this.place = place;
        this.size = (short) size;
        this.startedAt = now;
    }

    public Status status() {
        return completedAt != null ? Status.COMPLETED : startedAt != null ? Status.STARTED : Status.NOT_STARTED;
    }

    public LocalDate date() {
        return date;
    }

    public short topicId() {
        return topicId;
    }

    public Integer minutes() {
        return minutes == null ? null : minutes.intValue();
    }

    public Integer size() {
        return size == null ? null : size.intValue();
    }

    public Place place() {
        return place;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public record Key(long userId, LocalDate date) implements Serializable {
    }
}
