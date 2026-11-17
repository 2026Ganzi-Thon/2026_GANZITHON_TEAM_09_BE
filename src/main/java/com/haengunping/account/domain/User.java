package com.haengunping.account.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
@Entity @Table(name="users")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=true,length=10) private String nickname;
    @Column(name="token_hash",columnDefinition="bytea",unique=true) private byte[] tokenHash;
    @Column(name="adult_confirmed",nullable=false) private boolean adultConfirmed;
    @Column(name="sensitive_consent_at") private Instant sensitiveConsentAt;
    @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
    @Column(name="is_demo",nullable=false) private boolean demo;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="deleted_at") private Instant deletedAt;
    protected User() {}
    public User(String nickname,byte[] tokenHash,Instant now,boolean demo) {
        this.nickname=nickname;this.tokenHash=tokenHash.clone();this.createdAt=now;this.lastSeenAt=now;this.demo=demo;
    }
    public Long getId(){return id;}
    public String getNickname(){return nickname;}
    public boolean isAdultConfirmed(){return adultConfirmed;}
    public boolean hasSensitiveConsent(){return sensitiveConsentAt!=null;}
    public Instant getCreatedAt(){return createdAt;}
    public boolean isDemo(){return demo;}
    public boolean isActive(){return deletedAt==null;}
    public Instant getLastSeenAt(){return lastSeenAt;}
    public void rename(String name){nickname=name;}
    public void consent(boolean agreed,Instant now){sensitiveConsentAt=agreed?now:null;}
    public void confirmAdult(){adultConfirmed=true;}
    public void touch(Instant now){if(lastSeenAt.plusSeconds(3600).isBefore(now) || lastSeenAt.plusSeconds(3600).equals(now))lastSeenAt=now;}
    public void delete(Instant now){deletedAt=now;nickname=null;tokenHash=null;sensitiveConsentAt=null;}
}
