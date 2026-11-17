package com.haengunping.account;
import java.time.Instant;
import java.util.Optional;
/** Other modules must not access account.domain / account.infra directly. */
public interface AccountQuery {
    boolean hasSensitiveConsent(long userId);
    boolean isAdultConfirmed(long userId);
    boolean isActive(long userId);
    String nicknameOf(long userId);
    Optional<Instant> lastSeenAt(long userId);
    void requireSensitiveConsent(long userId);
    /** Serialize group/queue membership changes on the users row (caller must have a transaction). */
    void lockForGroupChange(long userId);
}
