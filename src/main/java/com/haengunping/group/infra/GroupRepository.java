package com.haengunping.group.infra;
import com.haengunping.group.domain.Group;
import com.haengunping.group.domain.GroupType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface GroupRepository extends JpaRepository<Group,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id=:id")
    Optional<Group> lockById(@Param("id") long id);
    List<Group> findByTypeAndArchivedAtIsNull(GroupType type);
}
