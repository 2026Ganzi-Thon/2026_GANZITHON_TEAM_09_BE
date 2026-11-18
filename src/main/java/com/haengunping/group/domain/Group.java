package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="groups")
public class Group {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private GroupType type;
    @Column(nullable=false,length=12) private String name;
    @Column(name="is_demo",nullable=false) private boolean demo;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="archived_at") private Instant archivedAt;
    protected Group() {}
    public Group(GroupType type,String name,boolean demo,Instant now){this.type=type;this.name=name;this.demo=demo;this.createdAt=now;}
    public Long getId(){return id;}
    public GroupType getType(){return type;}
    public String getName(){return name;}
    public boolean isDemo(){return demo;}
    public Instant getArchivedAt(){return archivedAt;}
    public void archive(Instant now){archivedAt=now;}
}
