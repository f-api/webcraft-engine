package com.gameexpert.map.entity;

import java.util.Arrays;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.api.persistence.WorldAccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 월드가 소유하는 scale-0 채워진 지도 저장 행입니다. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(
        name = "world_maps",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_world_map_id", columnNames = {"world_id", "map_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WorldAccess world;

    @Column(nullable = false)
    private int mapId;

    @Column(nullable = false)
    private int centerX;

    @Column(nullable = false)
    private int centerZ;

    @Column(nullable = false)
    private int scale;

    @Column(nullable = false)
    private boolean locked;

    @Lob
    @Column(nullable = false, length = WorldMapData.COLOR_COUNT)
    private byte[] colors;

    @Column(nullable = false)
    private long revision;

    @Column(length = 128)
    private String targetType;
    private Integer targetX;
    private Integer targetZ;

    public WorldMap(WorldAccess world, WorldMapData data) {
        if (world == null || world.getId() == null || !world.getId().equals(data.getWorldId())) {
            throw new IllegalArgumentException("map world does not match persisted world");
        }
        this.world = world;
        this.mapId = data.getMapId();
        this.centerX = data.getCenterX();
        this.centerZ = data.getCenterZ();
        this.scale = data.getScale();
        this.locked = data.isLocked();
        this.colors = data.getColors();
        this.revision = data.getRevision();
        if (data.getTargetMarker() != null) {
            this.targetType = data.getTargetMarker().type();
            this.targetX = data.getTargetMarker().x();
            this.targetZ = data.getTargetMarker().z();
        }
    }

    public void replaceColors(byte[] nextColors, long nextRevision) {
        if (locked) {
            throw new IllegalStateException("locked map colors cannot change");
        }
        if (nextColors == null || nextColors.length != WorldMapData.COLOR_COUNT) {
            throw new IllegalArgumentException(
                    "map colors must contain exactly " + WorldMapData.COLOR_COUNT + " bytes");
        }
        if (nextRevision != Math.addExact(revision, 1)) {
            throw new IllegalArgumentException("map revision must advance exactly once");
        }
        colors = Arrays.copyOf(nextColors, nextColors.length);
        revision = nextRevision;
    }

    public WorldMapData toData() {
        return new WorldMapData(world.getId(), mapId, centerX, centerZ,
                scale, locked, colors, revision, targetType == null ? null
                        : new WorldMapData.TargetMarker(targetType, targetX, targetZ));
    }
}
