package com.haengunping.group.infra;
import com.haengunping.group.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportRepository extends JpaRepository<Report,Long> {
    boolean existsByReporterMemberIdAndTargetMemberId(long reporter,long target);
    long countByGroupIdAndTargetMemberId(long groupId,long target);
}
