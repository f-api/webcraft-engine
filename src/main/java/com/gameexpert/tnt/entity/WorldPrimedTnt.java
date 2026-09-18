package com.gameexpert.tnt.entity;

import com.gameexpert.tnt.dto.PrimedTntSnapshot;
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

/** A chunk-unloaded PrimedTnt entity, persisted independently from block diffs. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_primed_tnt", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_primed_tnt_id", columnNames = { "world_id", "tnt_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldPrimedTnt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;
    @Column(nullable = false)
    private long tntId;
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
    private int fuse;

    public WorldPrimedTnt(Long worldId, PrimedTntSnapshot snapshot) {
        this.worldId = worldId;
        this.tntId = snapshot.tntId();
        apply(snapshot);
    }

    /** 같은 권위 entity의 최신 위치·운동량·fuse를 제자리 갱신한다. */
    public void apply(PrimedTntSnapshot snapshot) {
        if (snapshot.tntId() != tntId) {
            throw new IllegalArgumentException("primed TNT identity cannot change");
        }
        this.posX = snapshot.x();
        this.posY = snapshot.y();
        this.posZ = snapshot.z();
        this.velocityX = snapshot.velocityX();
        this.velocityY = snapshot.velocityY();
        this.velocityZ = snapshot.velocityZ();
        this.fuse = snapshot.fuse();
    }

    public boolean matches(PrimedTntSnapshot snapshot) {
        return snapshot.tntId() == tntId
                && Double.doubleToLongBits(posX) == Double.doubleToLongBits(snapshot.x())
                && Double.doubleToLongBits(posY) == Double.doubleToLongBits(snapshot.y())
                && Double.doubleToLongBits(posZ) == Double.doubleToLongBits(snapshot.z())
                && Double.doubleToLongBits(velocityX)
                        == Double.doubleToLongBits(snapshot.velocityX())
                && Double.doubleToLongBits(velocityY)
                        == Double.doubleToLongBits(snapshot.velocityY())
                && Double.doubleToLongBits(velocityZ)
                        == Double.doubleToLongBits(snapshot.velocityZ())
                && fuse == snapshot.fuse();
    }

    public PrimedTntSnapshot toSnapshot() {
        return new PrimedTntSnapshot(tntId, posX, posY, posZ,
                velocityX, velocityY, velocityZ, fuse);
    }
}
