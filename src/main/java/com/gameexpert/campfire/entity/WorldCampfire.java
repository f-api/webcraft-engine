package com.gameexpert.campfire.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.gameexpert.engine.CampfireInventory;
import com.gameexpert.engine.CampfireRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월드 좌표에 귀속된 모닥불 음식 네 칸과 각 조리 진행의 영속 스냅샷입니다. */
@Getter
@Entity
@Table(
        name = "world_campfires",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_campfire_pos",
                columnNames = { "world_id", "pos_x", "pos_y", "pos_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldCampfire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private int posX;

    @Column(nullable = false)
    private int posY;

    @Column(nullable = false)
    private int posZ;

    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long persistenceRevision;

    @Column(nullable = false)
    private short slot0Type;

    @Column(nullable = false)
    private int slot0CookTicks;

    @Column(nullable = false)
    private short slot1Type;

    @Column(nullable = false)
    private int slot1CookTicks;

    @Column(nullable = false)
    private short slot2Type;

    @Column(nullable = false)
    private int slot2CookTicks;

    @Column(nullable = false)
    private short slot3Type;

    @Column(nullable = false)
    private int slot3CookTicks;

    public WorldCampfire(Long worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void replace(short[] itemTypes, int[] cookTicks) {
        validateSnapshot(itemTypes, cookTicks);
        slot0Type = itemTypes[0];
        slot0CookTicks = cookTicks[0];
        slot1Type = itemTypes[1];
        slot1CookTicks = cookTicks[1];
        slot2Type = itemTypes[2];
        slot2CookTicks = cookTicks[2];
        slot3Type = itemTypes[3];
        slot3CookTicks = cookTicks[3];
    }

    public boolean replaceIfNewer(short[] itemTypes, int[] cookTicks, long revision) {
        validateSnapshot(itemTypes, cookTicks);
        if (revision < 0L || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("campfire revision must be non-negative and finite");
        }
        if (revision <= persistenceRevision) return false;
        replace(itemTypes, cookTicks);
        persistenceRevision = revision;
        return true;
    }

    public short[] itemTypes() {
        return new short[] { slot0Type, slot1Type, slot2Type, slot3Type };
    }

    public int[] cookTicks() {
        return new int[] {
                slot0CookTicks, slot1CookTicks, slot2CookTicks, slot3CookTicks
        };
    }

    /** 영속 행에 기록할 수 있는 정본 네 칸 상태를 검증합니다. */
    public static void validateSnapshot(short[] itemTypes, int[] cookTicks) {
        if (itemTypes == null || cookTicks == null
                || itemTypes.length != CampfireInventory.SLOTS
                || cookTicks.length != CampfireInventory.SLOTS) {
            throw new IllegalArgumentException("모닥불 영속 스냅샷은 정확히 4칸이어야 합니다.");
        }
        boolean occupied = false;
        for (int slot = 0; slot < CampfireInventory.SLOTS; slot++) {
            short type = itemTypes[slot];
            int progress = cookTicks[slot];
            if (type == PlayerInventory.EMPTY) {
                if (progress != 0) {
                    throw new IllegalArgumentException(
                            "빈 모닥불 칸의 진행 값은 0이어야 합니다: " + slot);
                }
                continue;
            }
            if (!CampfireRules.isCookable(type)
                    || progress < 0 || progress >= CampfireInventory.COOK_TOTAL_TICKS) {
                throw new IllegalArgumentException(
                        "모닥불 저장 칸이 정본 음식/진행 범위를 벗어났습니다: " + slot);
            }
            // 모닥불 행은 슬롯마다 음식 하나만 보존하므로 점유 슬롯의 정본 count는 1입니다.
            occupied = true;
        }
        if (!occupied) {
            throw new IllegalArgumentException("빈 모닥불은 영속 상태를 가질 수 없습니다.");
        }
    }
}
