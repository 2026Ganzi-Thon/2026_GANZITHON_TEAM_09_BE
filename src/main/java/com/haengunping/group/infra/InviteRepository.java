package com.haengunping.group.infra;
import com.haengunping.group.domain.Invite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InviteRepository extends JpaRepository<Invite,Long> {
    Optional<Invite> findByCode(String code);
    List<Invite> findByGroupIdAndRevokedAtIsNull(long groupId);
    boolean existsByCode(String code);
}
