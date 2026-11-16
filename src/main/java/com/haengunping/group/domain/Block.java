package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="blocks")
public class Block {
    @EmbeddedId private BlockId id;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected Block() {}
    public Block(BlockId id,Instant now){this.id=id;this.createdAt=now;}
}
