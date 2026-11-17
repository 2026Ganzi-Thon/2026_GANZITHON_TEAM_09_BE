package com.haengunping.mission.infra;

import com.haengunping.mission.domain.MissionTopic;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionTopicRepository extends JpaRepository<MissionTopic, Short> {
}
