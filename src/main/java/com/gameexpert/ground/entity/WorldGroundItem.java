package com.gameexpert.ground.entity;

import com.gameexpert.ground.dto.GroundItemSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable row for a dropped item that is live in one world runtime. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_ground_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_ground_item_id", columnNames = {"world_id", "entity_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldGroundItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false)
    private Long worldId;
    @Column(name = "entity_id", nullable = false)
    private long entityId;
    @Column(nullable = false)
    private short itemType;
    @Column(nullable = false)
    private int itemCount;
    @Column(nullable = false)
    private int durability;
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "BIGINT")
    private long enchantments;
    @Column(nullable = false)
    private int mapId;
    @Column(nullable = false)
    private int shulkerId;
    /** WCMB1 can exceed VARCHAR bounds after percent-encoding a maximum custom name. */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String bucketMobData;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String itemComponentData;
    @Column(nullable = false)
    private double posX;
    @Column(nullable = false)
    private double posY;
    @Column(nullable = false)
    private double posZ;
    @Column(nullable = false)
    private double velocityX;
    @Column(nullable = false)
    private double velocityY;
    @Column(nullable = false)
    private double velocityZ;
    @Column(nullable = false)
    private boolean playerThrown;
    @Column(nullable = false)
    private int age;
    @Column(nullable = false)
    private int pickupDelay;
    @Column(nullable = false)
    private long excludedAllayId;

    public WorldGroundItem(Long worldId, GroundItemSnapshot snapshot) {
        requireValidWorldId(worldId);
        this.worldId = worldId;
        this.entityId = snapshot.entityId();
        apply(snapshot);
    }

    public void apply(GroundItemSnapshot snapshot) {
        if (snapshot.entityId() != entityId) throw new IllegalArgumentException("ground item identity cannot change");
        this.itemType = snapshot.itemType();
        this.itemCount = snapshot.count();
        this.durability = snapshot.durability();
        this.enchantments = snapshot.enchantments();
        this.mapId = snapshot.mapId();
        this.shulkerId = snapshot.shulkerId();
        this.bucketMobData = snapshot.bucketMobData();
        this.itemComponentData = snapshot.itemComponentData();
        this.posX = snapshot.x(); this.posY = snapshot.y(); this.posZ = snapshot.z();
        this.velocityX = snapshot.velocityX();
        this.velocityY = snapshot.velocityY();
        this.velocityZ = snapshot.velocityZ();
        this.playerThrown = snapshot.playerThrown();
        this.age = snapshot.age();
        this.pickupDelay = snapshot.pickupDelay();
        this.excludedAllayId = snapshot.excludedAllayId();
    }

    public GroundItemSnapshot toSnapshot() {
        return new GroundItemSnapshot(entityId, itemType, itemCount, durability,
                enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData,
                posX, posY, posZ, velocityX, velocityY, velocityZ,
                playerThrown, age, pickupDelay, excludedAllayId);
    }

    public boolean matches(GroundItemSnapshot snapshot) {
        return toSnapshot().equals(snapshot);
    }

    /** Width-complete persistence accessor for the pending ItemEntity migration. */
    public long getEnchantments() {
        return enchantments;
    }

    @PostLoad
    private void validateHydratedState() {
        requireValidWorldId(worldId);
        toSnapshot();
    }

    private static void requireValidWorldId(Long worldId) {
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("ground item world id is invalid");
        }
    }
}
