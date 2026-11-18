package com.haengunping.mission.application;

import com.haengunping.character.CharacterQuery;
import com.haengunping.character.CharacterQuery.CharacterView;
import com.haengunping.group.GroupQuery;
import com.haengunping.mission.infra.DailyMissionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다 했어요 뒤 내가 속한 그룹마다 오늘 'N명 중 M명 완료'와 캐릭터 변화(MIS-08).
 * 완료 수는 지금 구성원 중 오늘 완료한 사람이라, 완료한 뒤 들어온 사람도 센다. 누가 완료했는지는 내보내지 않는다(CHR-10).
 */
@Service
@Transactional(readOnly = true)
public class GroupTodayService {

    private final GroupQuery groups;
    private final CharacterQuery characters;
    private final DailyMissionRepository missions;
    private final Clock clock;

    public GroupTodayService(GroupQuery groups, CharacterQuery characters, DailyMissionRepository missions, Clock clock) {
        this.groups = groups;
        this.characters = characters;
        this.missions = missions;
        this.clock = clock;
    }

    public List<GroupToday> afterCompletion(long userId) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        return groups.activeGroupIdsOf(userId).stream().map(groupId -> {
            Set<Long> members = new HashSet<>(groups.activeUserIdsOf(groupId, now));
            Set<Long> completed = members.isEmpty() ? Set.of() : new HashSet<>(missions.findCompletedUserIds(members, today));
            int done = completed.size();
            Optional<CharacterView> after = characters.current(groupId, done, members.size());
            Optional<CharacterView> before = characters.current(groupId, completed.contains(userId) ? done - 1 : done, members.size());
            CharacterChange change = after.map(a -> new CharacterChange(a.stage(), a.progress(),
                    before.map(b -> b.stage() != a.stage()).orElse(false))).orElse(null);
            return new GroupToday(groupId, new Today(done, members.size()), change);
        }).toList();
    }

    public record GroupToday(long groupId, Today today, CharacterChange character) {
    }

    public record Today(int completed, int total) {
    }

    /** 진행 중 캐릭터가 없는 그룹이면 null. */
    public record CharacterChange(int stage, double progress, boolean stageChanged) {
    }
}
