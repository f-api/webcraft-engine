package com.gameexpert.endgateway.entity;

import com.gameexpert.endgateway.dto.EndGatewayData;
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

/** [END-GATEWAY] 엔드 관문 블록 엔티티의 출구(바닐라 {@code exit_portal}·{@code ExactTeleport}) 한 행. */
@Getter
@Entity
@Table(name = "world_end_gateways",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_end_gateway_xyz",
                columnNames = {"world_id", "x", "y", "z"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldEndGateway {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    @Column(nullable = false) private int x;
    @Column(nullable = false) private int y;
    @Column(nullable = false) private int z;
    @Column(name = "has_exit", nullable = false) private boolean hasExit;
    @Column(name = "exit_x", nullable = false) private int exitX;
    @Column(name = "exit_y", nullable = false) private int exitY;
    @Column(name = "exit_z", nullable = false) private int exitZ;
    @Column(name = "exact_teleport", nullable = false) private boolean exactTeleport;

    public WorldEndGateway(WorldAccess world, EndGatewayData data) {
        if (world == null) throw new IllegalArgumentException("world required");
        this.world = world;
        x = data.x();
        y = data.y();
        z = data.z();
        replace(data);
    }

    /** 좌표는 행의 정체성이라 바뀌지 않는다. 출구만 갱신한다. */
    public void replace(EndGatewayData data) {
        if (data.x() != x || data.y() != y || data.z() != z) {
            throw new IllegalArgumentException("end gateway identity cannot change");
        }
        hasExit = data.hasExit();
        exitX = data.exitX();
        exitY = data.exitY();
        exitZ = data.exitZ();
        exactTeleport = data.exact();
    }

    public EndGatewayData snapshot() {
        return new EndGatewayData(x, y, z, hasExit, exitX, exitY, exitZ, exactTeleport);
    }
}
