package com.haengunping.mission.application;

import com.haengunping.mission.domain.DailyMission;
import com.haengunping.mission.domain.DailyMission.Place;
import com.haengunping.mission.domain.MissionSize;
import com.haengunping.mission.domain.MissionTopic;
import com.haengunping.mission.domain.MissionTopic.Category;
import com.haengunping.mission.domain.MissionTopic.SizeType;
import com.haengunping.mission.domain.TopicPicker;
import com.haengunping.mission.infra.DailyMissionRepository;
import com.haengunping.mission.MissionQuery;
import com.haengunping.mission.infra.MissionTopicRepository;
import com.haengunping.mood.Mood;
import com.haengunping.mood.MoodQuery;
import java.time.Clock;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오늘 미션 배정·시작(MIS-02~06). */
@Service
@Transactional
public class MissionService implements MissionQuery {

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

    /** 다 했어요(MIS-07). 시작하지 않은 미션도 완료할 수 있고, 하루 1회만(NFR-09). */
    public Instant complete(long userId) {
        LocalDate today = LocalDate.now(clock);
        findOrAssign(userId, today);
        Instant now = clock.instant();
        if (missions.completeIfNotYet(userId, today, now) == 0) {
            throw new ApiException(ErrorCode.ALREADY_COMPLETED);
        }
        return now;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> completedUserIds(Collection<Long> userIds, LocalDate date) {
        return userIds.isEmpty() ? Set.of() : new HashSet<>(missions.findCompletedUserIds(userIds, date));
    }

    /** 내 기록: 그달 완료한 미션, 날짜순. 문구는 시작할 때 고른 장소 것(시작 없이 완료했으면 밖). */
    @Transactional(readOnly = true)
    public List<Record> records(long userId, YearMonth month) {
        List<DailyMission> done = missions.findCompleted(userId, month.atDay(1), month.atEndOfMonth());
        Map<Short, MissionTopic> byId = topics.findAllById(done.stream().map(DailyMission::topicId).distinct().toList())
                .stream().collect(Collectors.toMap(MissionTopic::id, Function.identity()));
        return done.stream().map(m -> {
            MissionTopic t = byId.get(m.topicId());
            String text = m.place() == Place.INDOOR ? t.indoorText() : t.outdoorText();
            return new Record(m.date(), t.category(), text, m.size(), t.sizeType());
        }).toList();
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

    public record Record(LocalDate date, Category category, String text, Integer size, SizeType sizeType) {
    }

    public record TodayMission(DailyMission mission, MissionTopic topic, Map<Integer, Integer> sizeByMinutes, Defaults defaults) {
    }
}
