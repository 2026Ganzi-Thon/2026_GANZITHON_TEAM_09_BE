package com.haengunping.mood.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 도움 카드를 마지막으로 닫은 시각(help_card_dismissals). 저장은 HelpCardDismissalRepository.upsert로만 한다. */
@Entity
@Table(name = "help_card_dismissals")
public class HelpCardDismissal {

    @Id
    @Column(name = "user_id")
    private long userId;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    protected HelpCardDismissal() {
    }

    public Instant dismissedAt() {
        return dismissedAt;
    }
}
