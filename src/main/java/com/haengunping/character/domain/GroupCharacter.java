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

    /** 확정 합이 100에 닿은 날로 완성 처리한다(CHR-07). 이 날까지의 성장이 이 캐릭터 몫이다. */
    public void complete(LocalDate day) {
        if (completedOn != null) {
            throw new IllegalStateException("이미 완성된 캐릭터");
        }
        this.completedOn = day;
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
