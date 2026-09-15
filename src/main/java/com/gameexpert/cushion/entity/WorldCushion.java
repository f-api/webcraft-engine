package com.gameexpert.cushion.entity;

import com.gameexpert.cushion.dto.CushionSnapshot;
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

/** Persisted cushion entity. The color is the append-only colored cushion item id. */
@Getter
@Entity
@Table(name = "world_cushion", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_cushion_id", columnNames = { "world_id", "cushion_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldCushion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long worldId;
    @Column(nullable = false)
    private long cushionId;
    @Column(nullable = false)
    private short itemType;
    @Column(nullable = false)
    private double posX;
    @Column(nullable = false)
    private double posY;
    @Column(nullable = false)
    private double posZ;
    @Column(nullable = false)
    private float yaw;
    private String itemComponentData;

    public WorldCushion(Long worldId, CushionSnapshot snapshot) {
        this.worldId = worldId;
        this.cushionId = snapshot.getCushionId();
        apply(snapshot);
    }

    public void apply(CushionSnapshot snapshot) {
        if (snapshot.getCushionId() != cushionId) {
            throw new IllegalArgumentException("cushion identity cannot change");
        }
        itemType = snapshot.getItemType();
        posX = snapshot.getX();
        posY = snapshot.getY();
        posZ = snapshot.getZ();
        yaw = snapshot.getYaw();
        itemComponentData = snapshot.getItemComponentData();
    }

    public CushionSnapshot toSnapshot() {
        return new CushionSnapshot(cushionId, itemType, posX, posY, posZ, yaw,
                itemComponentData);
    }
}
