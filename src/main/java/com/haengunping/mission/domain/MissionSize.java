package com.haengunping.mission.domain;

import com.haengunping.mission.domain.MissionTopic.SizeType;
import com.haengunping.mood.Mood;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 기분 매핑표로 추천 크기 정하기(MIS-04). */
public final class MissionSize {

    public static final List<Integer> MINUTES = List.of(5, 10, 20);

    private MissionSize() {
    }

    /** 고른 시간(5·10·20분) → 추천 크기. TIME 주제는 분, COUNT 주제는 횟수. */
    public static Map<Integer, Integer> recommend(MissionTopic topic, Mood mood) {
        Map<Integer, Integer> sizes = new LinkedHashMap<>();
        for (int minutes : MINUTES) {
            int capped = cap(minutes, mood);
            sizes.put(minutes, topic.sizeType() == SizeType.TIME ? capped : topic.countSizes()[MINUTES.indexOf(capped)]);
        }
        return sizes;
    }

    /** 고른 시간이 최대치. 지쳐요·우울해요면 한 단계 낮춘다(10→5, 20→10). */
    static int cap(int minutes, Mood mood) {
        boolean tired = mood == Mood.TIRED || mood == Mood.DEPRESSED;
        return tired && minutes > 5 ? minutes / 2 : minutes;
    }
}
