package com.gameexpert.engine.persistence.animal;

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

/** 동물과 관련된 블록의 재시작 가능한 예약 tick 하나를 저장합니다. */
@Getter
@Entity
@Table(
        name = "world_animal_block_ticks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_animal_block_tick_position_kind",
                columnNames = { "world_id", "block_x", "block_y", "block_z", "tick_kind" }),
        indexes = @Index(
                name = "idx_world_animal_block_tick_due",
                columnList = "world_id, due_tick"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldAnimalBlockTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "block_x", nullable = false)
    private int x;

    @Column(name = "block_y", nullable = false)
    private int y;

    @Column(name = "block_z", nullable = false)
    private int z;

    @Column(name = "tick_kind", nullable = false, length = 40)
    private String kind;

    /** 월드의 절대 Minecraft tick입니다. */
    @Column(name = "due_tick", nullable = false)
    private long dueTick;

    public WorldAnimalBlockTick(Long worldId, int x, int y, int z, String kind, long dueTick) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.kind = kind;
        this.dueTick = dueTick;
    }

    public void reschedule(long dueTick) {
        this.dueTick = dueTick;
    }
}
