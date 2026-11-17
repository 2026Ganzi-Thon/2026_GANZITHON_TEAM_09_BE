package com.haengunping.mission.domain;

import com.haengunping.mood.Mood;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.Immutable;

/** 미션 주제(MIS-01). 시드로만 채우고 앱은 읽기만 한다. 문구는 그대로 쓴다(MIS-09). */
@Entity
@Immutable
@Table(name = "mission_topics")
public class MissionTopic {

    public enum Category { ACTIVITY, STRETCH, NATURE_SUN, WEAK_TIE, KINDNESS, BREATH, MINDFUL, PLEASURE, MASTERY, GOOD_THING }

    public enum SizeType { TIME, COUNT }

    @Id
    private short id;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "outdoor_text")
    private String outdoorText;

    @Column(name = "indoor_text")
    private String indoorText;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_type")
    private SizeType sizeType;

    /** COUNT 주제의 5·10·20분 대응 횟수. TIME 주제는 null. */
    @Column(name = "count_sizes")
    private short[] countSizes;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_moods")
    private Mood[] priorityMoods;

    protected MissionTopic() {
    }

    public short id() {
        return id;
    }

    public Category category() {
        return category;
    }

    public String outdoorText() {
        return outdoorText;
    }

    public String indoorText() {
        return indoorText;
    }

    public SizeType sizeType() {
        return sizeType;
    }

    public short[] countSizes() {
        return countSizes == null ? null : countSizes.clone();
    }

    public List<Mood> priorityMoods() {
        return List.of(priorityMoods);
    }
}
