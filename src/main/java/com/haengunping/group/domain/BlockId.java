package com.haengunping.group.domain;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class BlockId implements Serializable {
    @Column(name="blocker_member_id") private Long blockerMemberId;
    @Column(name="blocked_member_id") private Long blockedMemberId;
    protected BlockId() {}
    public BlockId(long blocker,long blocked){blockerMemberId=blocker;blockedMemberId=blocked;}
    @Override public boolean equals(Object value){return value instanceof BlockId b && Objects.equals(blockerMemberId,b.blockerMemberId)&&Objects.equals(blockedMemberId,b.blockedMemberId);}
    @Override public int hashCode(){return Objects.hash(blockerMemberId,blockedMemberId);}
}
