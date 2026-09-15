package com.gameexpert.crafter.entity;

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

/**
 * [CONTAINER-MENUS] The part of the pinned 26.3-snapshot-7 {@code CrafterBlockEntity} that the
 * coordinate container row ({@code world_chests}, nine slots) does not carry: its
 * {@code disabled_slots} list, stored as a nine-bit mask (bit {@code i} = slot {@code i}
 * disabled). A crafter with no disabled slot has no row.
 *
 * <p>{@code triggered} lives in the block state and {@code crafting_ticks_remaining} is a
 * six-game-tick presentation countdown; neither is persisted here.
 */
@Getter
@Entity
@Table(name = "world_crafters", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_crafter_pos", columnNames = {"world_id", "pos_x", "pos_y", "pos_z"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldCrafter {
    /** Every slot disabled. */
    public static final int ALL_SLOTS = 0x1ff;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false) private Long worldId;
    @Column(name = "pos_x", nullable = false) private int posX;
    @Column(name = "pos_y", nullable = false) private int posY;
    @Column(name = "pos_z", nullable = false) private int posZ;
    @Column(name = "disabled_slots", nullable = false) private int disabledSlots;

    public WorldCrafter(Long worldId, int x, int y, int z, int disabledSlots) {
        if (worldId == null) throw new IllegalArgumentException("world id is required");
        this.worldId = worldId;
        posX = x;
        posY = y;
        posZ = z;
        replaceDisabledSlots(disabledSlots);
    }

    public void replaceDisabledSlots(int mask) {
        if ((mask & ~ALL_SLOTS) != 0) {
            throw new IllegalArgumentException("crafter disabled-slot mask out of range: " + mask);
        }
        disabledSlots = mask;
    }
}
