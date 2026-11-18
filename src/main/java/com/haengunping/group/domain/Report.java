package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="reports")
public class Report {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="group_id",nullable=false) private long groupId;
    @Column(name="reporter_member_id",nullable=false) private long reporterMemberId;
    @Column(name="target_member_id",nullable=false) private long targetMemberId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private ReportReason reason;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected Report() {}
    public Report(long groupId,long reporter,long target,ReportReason reason,Instant now){this.groupId=groupId;this.reporterMemberId=reporter;this.targetMemberId=target;this.reason=reason;this.createdAt=now;}
}
