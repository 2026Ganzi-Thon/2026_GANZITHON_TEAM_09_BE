package com.haengunping.character.application;

import com.haengunping.character.CharacterQuery;
import com.haengunping.character.domain.Growth;
import com.haengunping.character.infra.DailyGroupProgressRepository;
import com.haengunping.character.infra.GroupCharacterRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CharacterQueryService implements CharacterQuery {

    private final GroupCharacterRepository characters;
    private final DailyGroupProgressRepository progress;

    public CharacterQueryService(GroupCharacterRepository characters, DailyGroupProgressRepository progress) {
        this.characters = characters;
        this.progress = progress;
    }

    @Override
    public Optional<CharacterView> current(long groupId, int completedToday, int membersNow) {
        return characters.findByGroupIdAndCompletedOnIsNull(groupId).map(c -> {
            long confirmed = progress.confirmedUnitsSince(groupId, c.startedOn());
            int display = Growth.displayUnits(confirmed, Growth.dailyUnits(completedToday, membersNow));
            return new CharacterView(c.id(), c.kind().name(), Growth.stage(display), Growth.progress(display));
        });
    }
}
