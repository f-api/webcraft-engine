package com.gameexpert.mob.entity;

import com.gameexpert.mob.dto.VillagerSocietySnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주민 한 명의 번식·수면·골렘 기억 durable 상태. */
@Getter
@Entity
@Table(
        name = "world_villager_society_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_villager_society_state",
                columnNames = { "world_id", "villager_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldVillagerSocietyState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long villagerId;

    @Column(nullable = false)
    private int foodPoints;

    @Column(nullable = false, length = 128)
    private String foodInventory;

    @Column(nullable = false)
    private long lastSleptTick;

    @Column(nullable = false)
    private long golemMemoryTick;

    @Column(nullable = false)
    private long mateId;

    @Column(nullable = false)
    private long birthTick;

    @Column(nullable = false)
    private long courtshipEndTick;

    public WorldVillagerSocietyState(Long worldId, VillagerSocietySnapshot snapshot) {
        this.worldId = worldId;
        this.villagerId = snapshot.villagerId();
        apply(snapshot);
    }

    /** 매 flush 마다 같은 행을 제자리 갱신한다. */
    public void apply(VillagerSocietySnapshot snapshot) {
        this.foodPoints = snapshot.foodPoints();
        this.foodInventory = snapshot.foodInventory();
        this.lastSleptTick = snapshot.lastSleptTick();
        this.golemMemoryTick = snapshot.golemMemoryTick();
        this.mateId = snapshot.mateId();
        this.birthTick = snapshot.birthTick();
        this.courtshipEndTick = snapshot.courtshipEndTick();
    }

    public VillagerSocietySnapshot toSnapshot() {
        return new VillagerSocietySnapshot(villagerId, foodPoints, lastSleptTick,
                golemMemoryTick, mateId, birthTick, courtshipEndTick, foodInventory);
    }
}
