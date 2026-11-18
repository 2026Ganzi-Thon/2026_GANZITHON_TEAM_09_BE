package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="online_queue")
public class OnlineQueueEntry {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="queued_at",nullable=false) private Instant queuedAt;
    protected OnlineQueueEntry() {}
    public OnlineQueueEntry(long userId,Instant now){this.userId=userId;this.queuedAt=now;}
    public Long getUserId(){return userId;}
    public Instant getQueuedAt(){return queuedAt;}
}
