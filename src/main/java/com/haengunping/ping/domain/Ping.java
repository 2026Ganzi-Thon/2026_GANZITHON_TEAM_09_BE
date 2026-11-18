package com.haengunping.ping.domain;

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

/** 하루 1개 핑(pings). 보낸 사람·시각은 응답에 내보내지 않는다(PING-03). */
@Entity
@Table(name = "pings")
@IdClass(Ping.Key.class)
public class Ping {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Id
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private Emoji emoji;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Ping() {
    }

    public long userId() {
        return userId;
    }

    public Emoji emoji() {
        return emoji;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public record Key(long userId, LocalDate date) implements Serializable {
    }
}
