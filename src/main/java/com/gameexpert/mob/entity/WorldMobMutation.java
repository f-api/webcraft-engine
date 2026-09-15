package com.gameexpert.mob.entity;

import com.gameexpert.engine.mob.MobMutationJournal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 몹 유발 월드 변형의 write-ahead 저널 행입니다({@link MobMutationJournal}).
 *
 * <p>{@code applied=false} 행은 "기록했지만 아직 월드에 반영하지 않았다"는 뜻이고, 월드 활성화
 * 복구가 그 행만 정확히 한 번 재적용합니다. {@code (world_id, entry_key)} 유일 제약이 재전송·
 * 재접속 재시도의 중복 적용을 DB 수준에서도 막습니다.
 */
@Getter
@Entity
@Table(
        name = "world_mob_mutations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_mob_mutation_entry",
                columnNames = { "world_id", "entry_key" }),
        indexes = @Index(name = "idx_world_mob_mutation_world", columnList = "world_id, sequence"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldMobMutation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false, length = 96)
    private String entryKey;

    @Column(nullable = false, length = 24)
    private String origin;

    @Column(nullable = false)
    private long eventId;

    @Column(nullable = false)
    private long actorId;

    @Column(nullable = false)
    private long actionId;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private int x;

    @Column(nullable = false)
    private int y;

    @Column(nullable = false)
    private int z;

    @Column(nullable = false)
    private short blockType;

    @Column(nullable = false)
    private short blockState;

    @Column(nullable = false)
    private short priorBlockType;

    @Column(nullable = false)
    private short priorBlockState;

    @Column(nullable = false)
    private boolean applied = false;

    @Column(nullable = false)
    private boolean superseded = false;

    public WorldMobMutation(Long worldId, MobMutationJournal.Entry entry) {
        this.worldId = worldId;
        this.entryKey = entry.key();
        this.origin = entry.origin().name();
        this.eventId = entry.eventId();
        this.actorId = entry.actorId();
        this.actionId = entry.actionId();
        this.sequence = entry.sequence();
        this.x = entry.x();
        this.y = entry.y();
        this.z = entry.z();
        this.blockType = entry.blockType();
        this.blockState = entry.blockState();
        this.priorBlockType = entry.priorBlockType();
        this.priorBlockState = entry.priorBlockState();
        this.applied = entry.applied();
        this.superseded = entry.superseded();
    }

    public void markApplied() {
        this.applied = true;
    }

    public void markSuperseded() {
        this.superseded = true;
    }

    /** 저장 행을 런타임 저널 항목으로 되돌립니다. */
    public MobMutationJournal.Entry toEntry() {
        return new MobMutationJournal.Entry(
                MobMutationJournal.Origin.valueOf(origin), eventId, actorId, actionId, sequence,
                x, y, z, blockType, blockState, priorBlockType, priorBlockState,
                applied, superseded);
    }
}
