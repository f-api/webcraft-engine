package com.gameexpert.frog.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Durable absolute-tick cooldown for one Rafflesia-hosted frog colony. */
@Entity
@Table(name = "world_rafflesia_frog_colonies", uniqueConstraints = @UniqueConstraint(
        name = "uk_rafflesia_frog_colony_position",
        columnNames = {"world_id", "rafflesia_x", "rafflesia_y", "rafflesia_z"}))
public class WorldRafflesiaFrogColony {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "rafflesia_x", nullable = false)
    private int rafflesiaX;

    @Column(name = "rafflesia_y", nullable = false)
    private int rafflesiaY;

    @Column(name = "rafflesia_z", nullable = false)
    private int rafflesiaZ;

    @Column(name = "next_ready_mc_tick", nullable = false)
    private long nextReadyMcTick;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    protected WorldRafflesiaFrogColony() {
    }

    public WorldRafflesiaFrogColony(Long worldId, int rafflesiaX, int rafflesiaY,
            int rafflesiaZ, long nextReadyMcTick, int schemaVersion) {
        this.worldId = worldId;
        this.rafflesiaX = rafflesiaX;
        this.rafflesiaY = rafflesiaY;
        this.rafflesiaZ = rafflesiaZ;
        this.nextReadyMcTick = nextReadyMcTick;
        this.schemaVersion = schemaVersion;
    }

    public Long getWorldId() {
        return worldId;
    }

    public int getRafflesiaX() {
        return rafflesiaX;
    }

    public int getRafflesiaY() {
        return rafflesiaY;
    }

    public int getRafflesiaZ() {
        return rafflesiaZ;
    }

    public long getNextReadyMcTick() {
        return nextReadyMcTick;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void reserveUntil(long nextReadyMcTick) {
        this.nextReadyMcTick = nextReadyMcTick;
    }
}
