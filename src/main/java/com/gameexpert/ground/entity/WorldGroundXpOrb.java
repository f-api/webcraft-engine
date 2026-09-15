package com.gameexpert.ground.entity;

import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
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

/** Durable row for an experience orb that is live in one world runtime. */
@Getter
@Entity
@Table(name = "world_ground_xp_orbs", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_ground_xp_orb_id", columnNames = {"world_id", "entity_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldGroundXpOrb {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false)
    private Long worldId;
    @Column(name = "entity_id", nullable = false)
    private long entityId;
    @Column(nullable = false)
    private int amount;
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
    private int age;

    public WorldGroundXpOrb(Long worldId, GroundXpOrbSnapshot snapshot) {
        this.worldId = worldId;
        this.entityId = snapshot.entityId();
        apply(snapshot);
    }

    public void apply(GroundXpOrbSnapshot snapshot) {
        if (snapshot.entityId() != entityId) throw new IllegalArgumentException("XP orb identity cannot change");
        this.amount = snapshot.amount();
        this.posX = snapshot.x(); this.posY = snapshot.y(); this.posZ = snapshot.z();
        this.velocityX = snapshot.velocityX();
        this.velocityY = snapshot.velocityY();
        this.velocityZ = snapshot.velocityZ();
        this.age = snapshot.age();
    }

    public GroundXpOrbSnapshot toSnapshot() {
        return new GroundXpOrbSnapshot(entityId, amount, posX, posY, posZ,
                velocityX, velocityY, velocityZ, age);
    }

    public boolean matches(GroundXpOrbSnapshot snapshot) {
        return toSnapshot().equals(snapshot);
    }
}
