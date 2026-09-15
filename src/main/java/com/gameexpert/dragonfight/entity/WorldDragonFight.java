package com.gameexpert.dragonfight.entity;

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

/**
 * [DRAGON] 엔드 차원 월드 하나의 드래곤전 상태(바닐라 {@code EnderDragonFight} saved data 한 개). 값은
 * {@code DragonFightState.encode()} 의 한 줄 문자열이다.
 */
@Getter
@Entity
@Table(name = "world_dragon_fights",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_dragon_fight_world", columnNames = {"world_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldDragonFight {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    @Column(name = "state", nullable = false, columnDefinition = "TEXT")
    private String state;

    public WorldDragonFight(WorldAccess world, String state) {
        if (world == null) throw new IllegalArgumentException("world required");
        this.world = world;
        replace(state);
    }

    public void replace(String state) {
        if (state == null || state.isBlank()) throw new IllegalArgumentException("dragon fight state required");
        this.state = state;
    }
}
