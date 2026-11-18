package com.haengunping.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 그룹 캐릭터(characters). 진행도 컬럼은 없고 started_on 이후 확정 성장 합으로 구한다(CHR-04).
 * 이름이 Character면 java.lang.Character와 겹쳐 GroupCharacter로 둔다.
 */
@Entity
@Table(name = "characters")
public class GroupCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id")
    private long groupId;

    @Enumerated(EnumType.STRING)
    private CharacterKind kind;

    @Column(name = "started_on")
    private LocalDate startedOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    protected GroupCharacter() {
    }

    public GroupCharacter(long groupId, CharacterKind kind, LocalDate startedOn) {
        this.groupId = groupId;
        this.kind = kind;
        this.startedOn = startedOn;
    }

    public Long id() {
        return id;
    }

    public long groupId() {
        return groupId;
    }

    public CharacterKind kind() {
        return kind;
    }

    public LocalDate startedOn() {
        return startedOn;
    }

    public LocalDate completedOn() {
        return completedOn;
    }
}
