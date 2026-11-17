package com.haengunping.mood.domain;

import com.haengunping.mood.Mood;
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

/** 하루 1개 기분(moods 테이블, 민감정보). 저장은 MoodRepository.upsert로만 한다(같은 날 덮어쓰기). */
@Entity
@Table(name = "moods")
@IdClass(DailyMood.Key.class)
public class DailyMood {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Id
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private Mood mood;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DailyMood() {
    }

    public DailyMood(long userId, LocalDate date, Mood mood, Instant updatedAt) {
        this.userId = userId;
        this.date = date;
        this.mood = mood;
        this.updatedAt = updatedAt;
    }

    public LocalDate date() {
        return date;
    }

    public Mood mood() {
        return mood;
    }

    public record Key(long userId, LocalDate date) implements Serializable {
    }
}
