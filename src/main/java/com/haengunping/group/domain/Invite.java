package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="invites")
public class Invite {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="group_id",nullable=false) private long groupId;
    @Column(nullable=false,unique=true,length=8) private String code;
    @Column(name="created_by_member_id",nullable=false) private long createdByMemberId;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    protected Invite() {}
    public Invite(long groupId,String code,long memberId,Instant expiresAt){this.groupId=groupId;this.code=code;this.createdByMemberId=memberId;this.expiresAt=expiresAt;}
    public long getGroupId(){return groupId;}
    public String getCode(){return code;}
    public Instant getExpiresAt(){return expiresAt;}
    public boolean revoked(){return revokedAt!=null;}
    public boolean expired(Instant now){return !now.isBefore(expiresAt);}
    public void revoke(Instant now){ if(revokedAt==null)revokedAt=now; }
}
