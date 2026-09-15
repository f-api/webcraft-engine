package com.gameexpert.container.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 완료된 컨테이너 명령의 영구 멱등성 영수증. */
@Entity
@Table(name = "world_container_settlements", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_container_settlement", columnNames = {"world_id", "settlement_id"}))
public class WorldContainerSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "settlement_id", nullable = false)
    private long settlementId;

    @Column(name = "command_hash", nullable = false, length = 64)
    private String commandHash;

    protected WorldContainerSettlement() {
    }

    public WorldContainerSettlement(Long worldId, long settlementId, String commandHash) {
        this.worldId = worldId;
        this.settlementId = settlementId;
        this.commandHash = commandHash;
    }

    public boolean matches(String expectedCommandHash) {
        return commandHash.equals(expectedCommandHash);
    }
}
