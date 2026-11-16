package com.haengunping.group.infra;
import com.haengunping.group.domain.OnlineQueueEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OnlineQueueRepository extends JpaRepository<OnlineQueueEntry,Long> {
    List<OnlineQueueEntry> findAllByOrderByQueuedAtAsc();
}
