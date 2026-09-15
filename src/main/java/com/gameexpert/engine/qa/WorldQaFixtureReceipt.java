package com.gameexpert.engine.qa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "world_qa_fixture_receipts", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_qa_fixture", columnNames = {"world_id", "fixture_id"}))
public class WorldQaFixtureReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false)
    private Long worldId;
    @Column(name = "fixture_id", nullable = false, length = 32)
    private String fixtureId;
    @Column(name = "fixture_checksum", nullable = false)
    private long checksum;
    @Column(name = "anchor_x", nullable = false)
    private int anchorX;
    @Column(name = "floor_y", nullable = false)
    private int floorY;
    @Column(name = "anchor_z", nullable = false)
    private int anchorZ;

    protected WorldQaFixtureReceipt() { }

    public WorldQaFixtureReceipt(Long worldId, String fixtureId, long checksum,
            int anchorX, int floorY, int anchorZ) {
        this.worldId = worldId;
        this.fixtureId = fixtureId;
        this.checksum = checksum;
        this.anchorX = anchorX;
        this.floorY = floorY;
        this.anchorZ = anchorZ;
    }

    boolean matches(ContentQaFixturePlan plan) {
        return fixtureId.equals(plan.id()) && checksum == plan.checksum()
                && anchorX == plan.bounds().anchorX() && floorY == plan.bounds().floorY()
                && anchorZ == plan.bounds().anchorZ();
    }
}
