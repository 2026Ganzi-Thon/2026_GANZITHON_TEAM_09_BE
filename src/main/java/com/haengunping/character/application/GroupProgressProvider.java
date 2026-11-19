package com.haengunping.character.application;

import com.haengunping.character.CharacterQuery;
import com.haengunping.character.infra.GroupCharacterRepository;
import com.haengunping.group.GroupProgressSource;
import com.haengunping.group.GroupQuery;
import com.haengunping.mission.MissionQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 목록·상세 응답의 오늘 진행과 캐릭터(api.md 3절, CHR-05·06·10). A의 {@link GroupProgressSource} 구현.
 * 오늘 완료 수는 '지금 구성원 중 오늘 완료한 수'만 준다. 누가 했는지는 주지 않는다(CHR-10).
 */
@Service
@Transactional(readOnly = true)
class GroupProgressProvider implements GroupProgressSource {

    private final GroupQuery groups;
    private final MissionQuery missions;
    private final CharacterQuery characters;
    private final GroupCharacterRepository characterRows;
    private final Clock clock;

    GroupProgressProvider(GroupQuery groups, MissionQuery missions, CharacterQuery characters,
            GroupCharacterRepository characterRows, Clock clock) {
        this.groups = groups;
        this.missions = missions;
        this.characters = characters;
        this.characterRows = characterRows;
        this.clock = clock;
    }

    /** 목록: api.md대로 today에 date, character에 characterId를 넣지 않는다(null → 응답에서 생략). */
    @Override
    public Summary summary(long groupId, LocalDate today) {
        Snapshot s = snapshot(groupId, today);
        CharacterProgress character = s.character() == null ? null
                : new CharacterProgress(null, s.character().kind(), s.character().stage(), s.character().progress());
        return new Summary(new TodayProgress(null, s.completed(), s.total()), character, justCompleted(groupId, today));
    }

    @Override
    public Detail detail(long groupId, LocalDate today) {
        Snapshot s = snapshot(groupId, today);
        return new Detail(new TodayProgress(today, s.completed(), s.total()), s.character());
    }

    private Snapshot snapshot(long groupId, LocalDate today) {
        List<Long> members = groups.activeUserIdsOf(groupId, clock.instant());
        int completed = missions.completedUserIds(members, today).size();
        CharacterProgress character = characters.current(groupId, completed, members.size())
                .map(v -> new CharacterProgress(v.characterId(), v.kind(), v.stage(), v.progress()))
                .orElse(null);
        return new Snapshot(completed, members.size(), character);
    }

    /**
     * 어제 날짜로 완성된 캐릭터(오늘 00:00 확정에서 완성)가 있으면 그날 하루 동안 준다.
     * "한 번만"은 프론트가 그룹을 처음 볼 때 한 번 연출하는 것으로 맞춘다(UserFlow S26).
     * shortcut: 그날 앱을 열지 않으면 연출은 놓친다(도감에는 남는다), 놓치지 않게 하려면 사용자별 본 기록을 둔다.
     */
    private JustCompleted justCompleted(long groupId, LocalDate today) {
        return characterRows.findFirstByGroupIdAndCompletedOn(groupId, today.minusDays(1))
                .map(c -> new JustCompleted(c.kind().name(), c.completedOn()))
                .orElse(null);
    }

    private record Snapshot(int completed, int total, CharacterProgress character) {
    }
}
