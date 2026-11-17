package com.haengunping.mission.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.haengunping.mission.application.MissionService.Defaults;
import com.haengunping.mission.application.MissionService.TodayMission;
import com.haengunping.mission.domain.DailyMission.Place;
import com.haengunping.mission.domain.DailyMission.Status;
import com.haengunping.mission.infra.DailyMissionRepository;
import com.haengunping.mood.Mood;
import com.haengunping.support.IntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 PostgreSQL로 배정·시작을 확인한다. 시드가 없어도 돌도록 910번대 테스트 주제를 넣는다.
 * 나중에 시드(1~20)가 들어와도 깨지지 않게 "어떤 주제인가"가 아니라 규칙(우선 기분, 최근 제외)으로 검사한다.
 */
class MissionServiceTest extends IntegrationTest {

    private static final short OKAY_TOPIC = 911;

    @Autowired
    MissionService service;
    @Autowired
    DailyMissionRepository missions;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    Clock clock;

    private long me;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        me = createUser();
        today = LocalDate.now(clock);
        topic(OKAY_TOPIC, "OKAY");
        topic((short) 912, "GOOD");
        topic((short) 913, "GOOD");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", me);
        deleteUser(me);
        jdbc.update("DELETE FROM mission_topics WHERE id BETWEEN 910 AND 919");
    }

    @Test
    void MIS03_같은_날_다시_조회하면_같은_주제() {
        short first = service.today(me).topic().id();
        for (int i = 0; i < 5; i++) {
            assertThat(service.today(me).topic().id()).isEqualTo(first);
        }
    }

    @Test
    void MIS02_기분을_건너뛰면_보통의_우선_주제를_준다() {
        TodayMission mission = service.today(me);

        assertThat(mission.topic().priorityMoods()).contains(Mood.OKAY);
        assertThat(mission.mission().status()).isEqualTo(Status.NOT_STARTED);
    }

    @Test
    void MIS02_최근_3일에_받은_주제는_빼고_고른다() {
        yesterday(OKAY_TOPIC, null, null);

        assertThat(service.today(me).topic().id()).isNotEqualTo(OKAY_TOPIC);
    }

    @Test
    void MIS05_기본값은_어제_고른_시간과_장소() {
        yesterday(OKAY_TOPIC, 20, "INDOOR");

        assertThat(service.today(me).defaults()).isEqualTo(new Defaults(20, Place.INDOOR));
    }

    @Test
    void MIS05_어제_시작하지_않았으면_기본값은_10분_밖() {
        yesterday(OKAY_TOPIC, null, null);

        assertThat(service.today(me).defaults()).isEqualTo(new Defaults(10, Place.OUTDOOR));
    }

    @Test
    void MIS06_조회_없이_바로_시작해도_배정하고_고른_값을_저장한다() {
        TodayMission started = service.start(me, 5, Place.INDOOR, 3);

        assertThat(started.mission().status()).isEqualTo(Status.STARTED);
        assertThat(started.mission().startedAt()).isNotNull();
        TodayMission reloaded = service.today(me);
        assertThat(reloaded.mission().minutes()).isEqualTo(5);
        assertThat(reloaded.mission().place()).isEqualTo(Place.INDOOR);
        assertThat(reloaded.mission().size()).isEqualTo(3);
        assertThat(reloaded.topic().id()).isEqualTo(started.topic().id());
    }

    @Test
    void MIS03_배정이_동시에_두_번_들어와도_먼저_것이_남는다() {
        tx.executeWithoutResult(s -> {
            missions.insertIfAbsent(me, today, OKAY_TOPIC);
            missions.insertIfAbsent(me, today, (short) 912);
        });

        assertThat(service.today(me).topic().id()).isEqualTo(OKAY_TOPIC);
    }

    private void topic(short id, String mood) {
        jdbc.update("""
                INSERT INTO mission_topics (id, category, outdoor_text, indoor_text, size_type, priority_moods)
                VALUES (?, 'ACTIVITY', '밖', '안', 'TIME', ?::varchar[])
                """, id, "{" + mood + "}");
    }

    private void yesterday(short topicId, Integer minutes, String place) {
        jdbc.update("INSERT INTO daily_missions (user_id, date, topic_id, minutes, place) VALUES (?, ?, ?, ?, ?)",
                me, today.minusDays(1), topicId, minutes, place);
    }
}
