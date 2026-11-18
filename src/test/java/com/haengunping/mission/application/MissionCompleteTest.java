package com.haengunping.mission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import com.haengunping.mission.application.MissionService.Record;
import com.haengunping.mission.domain.DailyMission.Place;
import com.haengunping.mission.domain.DailyMission.Status;
import com.haengunping.mission.domain.MissionTopic.Category;
import com.haengunping.mission.domain.MissionTopic.SizeType;
import com.haengunping.support.IntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 다 했어요·내 기록(MIS-07, NFR-09)을 실제 PostgreSQL로 확인한다. 테스트 주제는 930번대. */
class MissionCompleteTest extends IntegrationTest {

    private static final short TOPIC = 931;

    @Autowired
    MissionService service;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private long me;

    @BeforeEach
    void setUp() {
        me = createUser();
        jdbc.update("""
                INSERT INTO mission_topics (id, category, outdoor_text, indoor_text, size_type, priority_moods)
                VALUES (?, 'NATURE_SUN', '햇빛 받으며 걷기', '창가에서 햇빛 쬐기', 'TIME', '{OKAY}'::varchar[])
                """, TOPIC);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_missions WHERE user_id = ?", me);
        jdbc.update("DELETE FROM mission_topics WHERE id = ?", TOPIC);
        deleteUser(me);
    }

    @Test
    void MIS07_시작하지_않은_미션도_완료할_수_있다() {
        assertThat(service.complete(me)).isNotNull();

        assertThat(service.today(me).mission().status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void MIS07_같은_날_두_번째는_409() {
        service.complete(me);

        assertThatThrownBy(() -> service.complete(me))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_COMPLETED));
    }

    @Test
    void MIS07_완료한_뒤에는_다시_시작할_수_없다() {
        service.complete(me);

        assertThatThrownBy(() -> service.start(me, 10, Place.OUTDOOR, 10))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_COMPLETED));
    }

    @Test
    void NFR09_동시에_두_번_눌러도_1건만_완료된다() throws Exception {
        service.today(me); // 배정은 먼저 해 두고 완료만 겨룬다
        CountDownLatch ready = new CountDownLatch(1);
        Callable<Boolean> press = () -> {
            ready.await();
            try {
                service.complete(me);
                return true;
            } catch (ApiException e) {
                assertThat(e.code()).isEqualTo(ErrorCode.ALREADY_COMPLETED);
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>(List.of(pool.submit(press), pool.submit(press)));
            ready.countDown();
            long succeeded = 0;
            for (Future<Boolean> r : results) {
                if (r.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void 내_기록은_그달_완료한_미션만_고른_장소의_문구로_날짜순() {
        YearMonth month = YearMonth.from(LocalDate.now(clock));
        LocalDate first = month.atDay(1);
        mission(first.plusDays(2), "INDOOR", 5, true);
        mission(first, null, null, true);              // 시작 없이 완료: 밖 문구, 크기 없음
        mission(first.plusDays(1), "OUTDOOR", 10, false); // 완료 안 함: 빠짐
        mission(first.minusDays(1), "OUTDOOR", 10, true); // 지난달: 빠짐

        List<Record> records = service.records(me, month);

        assertThat(records).containsExactly(
                new Record(first, Category.NATURE_SUN, "햇빛 받으며 걷기", null, SizeType.TIME),
                new Record(first.plusDays(2), Category.NATURE_SUN, "창가에서 햇빛 쬐기", 5, SizeType.TIME));
    }

    private void mission(LocalDate date, String place, Integer size, boolean completed) {
        jdbc.update("""
                INSERT INTO daily_missions (user_id, date, topic_id, minutes, place, size, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, me, date, TOPIC, place == null ? null : 10, place, size,
                place == null ? null : java.sql.Timestamp.from(clock.instant()),
                completed ? java.sql.Timestamp.from(clock.instant()) : null);
    }
}
