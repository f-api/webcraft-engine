package com.gameexpert.boat.entity;

import com.gameexpert.boat.dto.BoatSnapshot;
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

/** 월드 런타임이 교체돼도 복원할 보트 하나의 최소 상태입니다. */
@Getter
@Entity
@Table(name = "world_boats", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_boat_id", columnNames = { "world_id", "boat_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldBoat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long worldId;
    @Column(nullable = false)
    private long boatId;
    @Column(nullable = false)
    private double posX;
    @Column(nullable = false)
    private double posY;
    @Column(nullable = false)
    private double posZ;
    @Column(nullable = false)
    private double yaw;

    public WorldBoat(Long worldId, BoatSnapshot snapshot) {
        this.worldId = worldId;
        this.boatId = snapshot.getBoatId();
        apply(snapshot);
    }

    /** 보트 식별자는 행의 정체성이라 바뀔 수 없다. 좌표만 갱신한다. */
    public void apply(BoatSnapshot snapshot) {
        if (snapshot.getBoatId() != boatId) {
            throw new IllegalArgumentException("boat identity cannot change");
        }
        posX = snapshot.getX();
        posY = snapshot.getY();
        posZ = snapshot.getZ();
        yaw = snapshot.getYaw();
    }

    /** 같은 좌표를 다시 쓰지 않기 위한 정확 비교(비트 동일성). */
    public boolean matches(BoatSnapshot snapshot) {
        return snapshot.getBoatId() == boatId
                && Double.compare(posX, snapshot.getX()) == 0
                && Double.compare(posY, snapshot.getY()) == 0
                && Double.compare(posZ, snapshot.getZ()) == 0
                && Double.compare(yaw, snapshot.getYaw()) == 0;
    }

    public BoatSnapshot toSnapshot() {
        return new BoatSnapshot(boatId, posX, posY, posZ, yaw);
    }
}
