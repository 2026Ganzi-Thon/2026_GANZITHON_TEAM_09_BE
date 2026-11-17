package com.haengunping.mission.application;

import com.haengunping.mission.domain.DailyMission;
import com.haengunping.mission.domain.DailyMission.Place;
import com.haengunping.mission.domain.MissionSize;
import com.haengunping.mission.domain.MissionTopic;
import com.haengunping.mission.domain.TopicPicker;
import com.haengunping.mission.infra.DailyMissionRepository;
import com.haengunping.mission.infra.MissionTopicRepository;
import com.haengunping.mood.Mood;
import com.haengunping.mood.MoodQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오늘 미션 배정·시작(MIS-02~06). */
@Service
@Transactional
public class MissionService {

    private static final Defaults FIRST_DEFAULTS = new Defaults(10, Place.OUTDOOR);

    private final DailyMissionRepository missions;
    private final MissionTopicRepository topics;
    private final MoodQuery moods;
    private final Clock clock;

    public MissionService(DailyMissionRepository missions, MissionTopicRepository topics, MoodQuery moods, Clock clock) {
        this.missions = missions;
        this.topics = topics;
        this.moods = moods;
        this.clock = clock;
    }

    /** 오늘 미션. 처음 부르면 주제를 배정하고, 이후로는 같은 주제(MIS-03). */
    public TodayMission today(long userId) {
        LocalDate today = LocalDate.now(clock);
        return view(userId, today, findOrAssign(userId, today));
    }

    /** 시작(MIS-05·06). 오늘 미션을 아직 안 불렀어도 배정한 뒤 시작한다. */
    public TodayMission start(long userId, int minutes, Place place, int size) {
        LocalDate today = LocalDate.now(clock);
        DailyMission mission = findOrAssign(userId, today);
        mission.start(minutes, place, size, clock.instant());
        return view(userId, today, mission);
    }

    private DailyMission findOrAssign(long userId, LocalDate today) {
        return missions.findByUserIdAndDate(userId, today).orElseGet(() -> {
            var recent = missions.findTopicIds(userId, today.minusDays(TopicPicker.RECENT_DAYS), today.minusDays(1));
            MissionTopic topic = TopicPicker.pick(topics.findAll(), moodToday(userId, today), recent, ThreadLocalRandom.current());
            missions.insertIfAbsent(userId, today, topic.id());
            return missions.findByUserIdAndDate(userId, today).orElseThrow();
        });
    }

    private TodayMission view(long userId, LocalDate today, DailyMission mission) {
        MissionTopic topic = topics.findById(mission.topicId()).orElseThrow();
        Map<Integer, Integer> sizes = MissionSize.recommend(topic, moodToday(userId, today));
        Defaults defaults = missions.findByUserIdAndDate(userId, today.minusDays(1))
                .filter(y -> y.minutes() != null)
                .map(y -> new Defaults(y.minutes(), y.place()))
                .orElse(FIRST_DEFAULTS);
        return new TodayMission(mission, topic, sizes, defaults);
    }

    // 오늘 기분이 없으면(건너뛰기) 보통으로 본다(MIS-02).
    private Mood moodToday(long userId, LocalDate today) {
        return moods.moodOn(userId, today).orElse(Mood.OKAY);
    }

    public record Defaults(int minutes, Place place) {
    }

    public record TodayMission(DailyMission mission, MissionTopic topic, Map<Integer, Integer> sizeByMinutes, Defaults defaults) {
    }
}
