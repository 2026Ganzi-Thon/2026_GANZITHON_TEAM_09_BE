package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="group_members")
public class GroupMember {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="group_id",nullable=false) private long groupId;
    @Column(name="user_id",nullable=false) private long userId;
    @Column(name="display_name",nullable=false,length=20) private String displayName;
    @Column(name="joined_at",nullable=false) private Instant joinedAt;
    @Column(name="left_at") private Instant leftAt;
    @Enumerated(EnumType.STRING) @Column(name="left_reason",length=20) private LeftReason leftReason;
    protected GroupMember() {}
    public GroupMember(long groupId,long userId,String displayName,Instant joinedAt){this.groupId=groupId;this.userId=userId;this.displayName=displayName;this.joinedAt=joinedAt;}
    public Long getId(){return id;}
    public long getGroupId(){return groupId;}
    public long getUserId(){return userId;}
    public String getDisplayName(){return displayName;}
    public void setDisplayName(String value){this.displayName=value;}
    public Instant getLeftAt(){return leftAt;}
    public LeftReason getLeftReason(){return leftReason;}
    public void leave(Instant now,LeftReason reason){ if(leftAt==null){leftAt=now;leftReason=reason;} }
}
