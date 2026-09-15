package com.gameexpert.world.entity;

import com.gameexpert.api.persistence.WorldAccess;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 루트와 공간 aggregate의 추가 매핑. 기존 world ID와 generator identity는 그대로 둔다. */
@Getter
@Entity
@Table(name = "world_dimensions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_root_dimension", columnNames = {"root_world_id", "dimension_key"}),
        @UniqueConstraint(name = "uq_dimension_child", columnNames = "child_world_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldDimension {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "root_world_id", nullable = false) private WorldAccess root;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_world_id", nullable = false) private WorldAccess child;
    @Column(nullable = false, length = 32) private String dimensionKey;

    public WorldDimension(WorldAccess root, WorldAccess child, String dimensionKey) {
        if (root == null || child == null || root.getId() == null || child.getId() == null
                || root.getId().equals(child.getId()) || dimensionKey == null
                || !dimensionKey.matches("[a-z][a-z0-9_]{0,31}") || "overworld".equals(dimensionKey)) {
            throw new IllegalArgumentException("distinct durable root/child and registered custom key required");
        }
        this.root = root; this.child = child; this.dimensionKey = dimensionKey;
    }
}
