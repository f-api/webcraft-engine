package com.gameexpert.state.entity;

import com.gameexpert.api.persistence.PlayerAccess;
import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 위치는 root PlayerWorldState의 현재 차원 좌표다. 이 행은 그 좌표의 차원/세대를 결박한다. */
@Getter
@Entity
@Table(name = "player_dimension_travel", uniqueConstraints = @UniqueConstraint(
        name = "uq_dimension_root_player", columnNames = {"root_world_id", "player_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerDimensionTravel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "root_world_id", nullable = false) private WorldAccess root;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false) private PlayerAccess player;
    @Column(nullable = false) private long activeWorldId;
    @Column(nullable = false) private long travelRevision;
    @Column(nullable = false, length = 32) private String dimension = "overworld";
    private Double returnX;
    private Double returnY;
    private Double returnZ;
    private Float returnYaw;
    private Float returnPitch;

    public PlayerDimensionTravel(WorldAccess root, PlayerAccess player) {
        if (root == null || root.getId() == null || player == null || player.getId() == null) {
            throw new IllegalArgumentException("durable root/player required");
        }
        this.root = root;
        this.player = player;
        this.activeWorldId = root.getId();
    }

    public void enter(long childId, String targetDimension, double x, double y, double z, float yaw, float pitch) {
        if (activeWorldId != root.getId() || childId <= 0 || childId == root.getId()) {
            throw new IllegalStateException("source is not the root dimension");
        }
        if (targetDimension == null || !targetDimension.matches("[a-z][a-z0-9_]{0,31}")
                || "overworld".equals(targetDimension)) {
            throw new IllegalArgumentException("custom dimension key required");
        }
        requirePose(x, y, z, yaw, pitch);
        dimension = targetDimension;
        returnX = x; returnY = y; returnZ = z; returnYaw = yaw; returnPitch = pitch;
        activeWorldId = childId;
        travelRevision = Math.incrementExact(travelRevision);
    }

    public void returnToRoot() {
        if (activeWorldId == root.getId() || returnX == null || returnY == null
                || returnZ == null || returnYaw == null || returnPitch == null) {
            throw new IllegalStateException("no durable return origin");
        }
        activeWorldId = root.getId();
        dimension = "overworld";
        travelRevision = Math.incrementExact(travelRevision);
    }

    private static void requirePose(double x, double y, double z, float yaw, float pitch) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("finite return pose required");
        }
    }
}
