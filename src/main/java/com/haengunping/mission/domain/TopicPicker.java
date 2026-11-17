package com.haengunping.mission.domain;

import com.haengunping.mood.Mood;
import java.util.Collection;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 오늘 주제 고르기(MIS-02): 오늘 기분의 우선 주제 중 최근 3일(어제~사흘 전)에 받은 주제를 빼고 무작위.
 * 후보가 없으면 전체 주제에서 같은 규칙으로 고른다.
 */
public final class TopicPicker {

    public static final int RECENT_DAYS = 3;

    private TopicPicker() {
    }

    public static MissionTopic pick(List<MissionTopic> all, Mood mood, Collection<Short> recentTopicIds, RandomGenerator random) {
        List<MissionTopic> fresh = all.stream().filter(t -> !recentTopicIds.contains(t.id())).toList();
        List<MissionTopic> preferred = fresh.stream().filter(t -> t.priorityMoods().contains(mood)).toList();
        List<MissionTopic> candidates = !preferred.isEmpty() ? preferred : !fresh.isEmpty() ? fresh : all;
        if (candidates.isEmpty()) {
            throw new IllegalStateException("미션 주제 시드가 비어 있다");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
