package com.haengunping.mission.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haengunping.mission.domain.MissionTopic;
import com.haengunping.mission.domain.MissionTopic.Category;
import com.haengunping.mission.domain.MissionTopic.SizeType;
import com.haengunping.mood.Mood;
import com.haengunping.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 배열 컬럼(count_sizes, priority_moods) 매핑과 DB 제약을 확인한다. 시드(1~20)와 겹치지 않는 id를 쓴다. */
class MissionTopicRepositoryTest extends IntegrationTest {

    private static final short TIME_ID = 901;
    private static final short COUNT_ID = 902;

    @Autowired
    MissionTopicRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mission_topics WHERE id >= 900");
    }

    @Test
    void MIS01_TIME_주제는_횟수_없이_우선_기분_배열을_읽는다() {
        insert(TIME_ID, "NATURE_SUN", "TIME", null, "{GOOD,TIRED}");

        MissionTopic topic = repository.findById(TIME_ID).orElseThrow();

        assertThat(topic.id()).isEqualTo(TIME_ID);
        assertThat(topic.outdoorText()).isEqualTo("밖 문구");
        assertThat(topic.indoorText()).isEqualTo("안 문구");
        assertThat(topic.category()).isEqualTo(Category.NATURE_SUN);
        assertThat(topic.sizeType()).isEqualTo(SizeType.TIME);
        assertThat(topic.countSizes()).isNull();
        assertThat(topic.priorityMoods()).containsExactly(Mood.GOOD, Mood.TIRED);
    }

    @Test
    void MIS01_COUNT_주제는_5_10_20분에_대응하는_횟수를_읽는다() {
        insert(COUNT_ID, "PLEASURE", "COUNT", "{1,2,4}", "{LONELY}");

        assertThat(repository.findById(COUNT_ID).orElseThrow().countSizes()).containsExactly((short) 1, (short) 2, (short) 4);
    }

    @Test
    void MIS01_COUNT_주제에_횟수가_없으면_DB가_거부한다() {
        assertThatThrownBy(() -> insert(COUNT_ID, "PLEASURE", "COUNT", null, "{LONELY}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void MIS01_횟수가_3개가_아니면_DB가_거부한다() {
        assertThatThrownBy(() -> insert(COUNT_ID, "PLEASURE", "COUNT", "{1,2}", "{LONELY}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(short id, String category, String sizeType, String countSizes, String moods) {
        jdbc.update("""
                INSERT INTO mission_topics (id, category, outdoor_text, indoor_text, size_type, count_sizes, priority_moods)
                VALUES (?, ?, '밖 문구', '안 문구', ?, ?::smallint[], ?::varchar[])
                """, id, category, sizeType, countSizes, moods);
    }
}
