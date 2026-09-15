package com.gameexpert.engine.persistence.animal;

import com.gameexpert.mob.dto.MobPersistenceSnapshot;
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

/** Exact live-golem identity retained while its authoritative form is a statue block. */
@Getter
@Entity
@Table(name = "world_copper_golem_statues", uniqueConstraints = {
        @UniqueConstraint(name = "uk_copper_statue_position",
                columnNames = {"world_id", "x", "y", "z"}),
        @UniqueConstraint(name = "uk_copper_statue_mob",
                columnNames = {"world_id", "mob_id"}) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldCopperGolemStatue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "world_id", nullable = false) private Long worldId;
    @Column(nullable = false) private int x;
    @Column(nullable = false) private int y;
    @Column(nullable = false) private int z;
    @Column(name = "mob_id", nullable = false) private long mobId;
    @Column(name = "custom_name", length = 64) private String customName;
    @Column(name = "held_item", nullable = false) private short heldItem;
    @Column(name = "held_item_durability", nullable = false) private int heldItemDurability;
    @Column(nullable = false) private int pose;

    public WorldCopperGolemStatue(Long worldId, int x, int y, int z,
            MobPersistenceSnapshot mob) {
        if (!"COPPER_GOLEM".equals(mob.getType())) {
            throw new IllegalArgumentException("statue source must be Copper Golem");
        }
        this.worldId = worldId; this.x = x; this.y = y; this.z = z;
        this.mobId = mob.getMobId(); this.customName = mob.getCustomName();
        this.heldItem = mob.getHeldItem(); this.heldItemDurability = mob.getHeldItemDurability();
        this.pose = mob.getCopperGolemPose();
    }
}
