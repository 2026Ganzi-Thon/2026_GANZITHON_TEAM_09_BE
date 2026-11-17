package com.haengunping.mission.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.mission.domain.DailyMission.Place;
import com.haengunping.mission.domain.DailyMission.Status;
import com.haengunping.mission.domain.MissionTopic.Category;
import com.haengunping.mission.domain.MissionTopic.SizeType;
import com.haengunping.mood.Mood;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MissionRulesTest {

    private static final MissionTopic TIME = topic(1, SizeType.TIME, null, Mood.GOOD);
    private static final MissionTopic COUNT = topic(2, SizeType.COUNT, new short[] {1, 2, 3}, Mood.CALM);
    private static final MissionTopic SONG = topic(3, SizeType.COUNT, new short[] {1, 2, 4}, Mood.LONELY);

    // --- 크기 (MIS-04) ---

    @Test
    void MIS04_우울해요와_20분이면_10분() {
        assertThat(MissionSize.recommend(TIME, Mood.DEPRESSED)).isEqualTo(Map.of(5, 5, 10, 5, 20, 10));
    }

    @Test
    void MIS04_지쳐요도_한_단계_낮추고_외로워요는_그대로() {
        assertThat(MissionSize.recommend(TIME, Mood.TIRED)).isEqualTo(Map.of(5, 5, 10, 5, 20, 10));
        assertThat(MissionSize.recommend(TIME, Mood.LONELY)).isEqualTo(Map.of(5, 5, 10, 10, 20, 20));
    }

    @Test
    void MIS04_COUNT_주제는_5_10_20분이_1_2_3회() {
        assertThat(MissionSize.recommend(COUNT, Mood.GOOD)).isEqualTo(Map.of(5, 1, 10, 2, 20, 3));
        assertThat(MissionSize.recommend(COUNT, Mood.DEPRESSED)).isEqualTo(Map.of(5, 1, 10, 1, 20, 2));
    }

    @Test
    void MIS04_노래_주제는_1_2_4곡() {
        assertThat(MissionSize.recommend(SONG, Mood.OKAY)).isEqualTo(Map.of(5, 1, 10, 2, 20, 4));
    }

    // --- 주제 배정 (MIS-02) ---

    @Test
    void MIS02_오늘_기분의_우선_주제에서_고른다() {
        MissionTopic picked = TopicPicker.pick(List.of(TIME, COUNT, SONG), Mood.CALM, Set.of(), new Random(1));
        assertThat(picked).isSameAs(COUNT);
    }

    @Test
    void MIS02_우선_주제가_최근에_나왔으면_전체에서_최근_것을_빼고_고른다() {
        for (int seed = 0; seed < 50; seed++) {
            MissionTopic picked = TopicPicker.pick(List.of(TIME, COUNT, SONG), Mood.CALM, Set.of((short) 2), new Random(seed));
            assertThat(picked).isNotSameAs(COUNT);
        }
    }

    @Test
    void MIS02_모두_최근에_나왔으면_전체에서_고른다() {
        MissionTopic picked = TopicPicker.pick(List.of(TIME), Mood.GOOD, Set.of((short) 1), new Random(1));
        assertThat(picked).isSameAs(TIME);
    }

    @Test
    void MIS02_1000일_시뮬레이션에서_최근_3일_안에_같은_주제가_다시_나오지_않는다() {
        Mood[] moods = Mood.values();
        List<MissionTopic> all = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> topic(i, SizeType.TIME, null, moods[i % moods.length], moods[(i * 7) % moods.length]))
                .toList();
        Random random = new Random(42);
        List<Short> history = new ArrayList<>();

        for (int day = 0; day < 1000; day++) {
            List<Short> recent = history.subList(Math.max(0, history.size() - TopicPicker.RECENT_DAYS), history.size());
            Mood mood = moods[random.nextInt(moods.length)];
            short picked = TopicPicker.pick(all, mood, recent, random).id();
            assertThat(recent).doesNotContain(picked);
            history.add(picked);
        }
    }

    // --- 시작 (MIS-05·06) ---

    @Test
    void MIS06_시작하면_고른_값과_시작_시각을_저장하고_다시_시작하면_새로_쓴다() {
        DailyMission mission = new DailyMission(1, LocalDate.of(2026, 11, 16), (short) 5);
        assertThat(mission.status()).isEqualTo(Status.NOT_STARTED);

        mission.start(10, Place.INDOOR, 5, Instant.parse("2026-11-16T03:00:00Z"));
        mission.start(20, Place.OUTDOOR, 20, Instant.parse("2026-11-16T04:00:00Z"));

        assertThat(mission.status()).isEqualTo(Status.STARTED);
        assertThat(mission.minutes()).isEqualTo(20);
        assertThat(mission.place()).isEqualTo(Place.OUTDOOR);
        assertThat(mission.size()).isEqualTo(20);
        assertThat(mission.startedAt()).isEqualTo(Instant.parse("2026-11-16T04:00:00Z"));
    }

    @Test
    void MIS05_시간은_5_10_20분만_크기는_1에서_100까지() {
        DailyMission mission = new DailyMission(1, LocalDate.of(2026, 11, 16), (short) 5);
        assertThatThrownBy(() -> mission.start(15, Place.INDOOR, 5, Instant.EPOCH))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> mission.start(10, Place.INDOOR, 0, Instant.EPOCH))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> mission.start(10, Place.INDOOR, 101, Instant.EPOCH))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(mission.status()).isEqualTo(Status.NOT_STARTED);

        mission.start(10, Place.INDOOR, 100, Instant.EPOCH);
        assertThat(mission.size()).isEqualTo(100);
    }

    private static MissionTopic topic(int id, SizeType type, short[] counts, Mood... moods) {
        return new MissionTopic((short) id, Category.ACTIVITY, type, counts, List.of(moods));
    }
}
