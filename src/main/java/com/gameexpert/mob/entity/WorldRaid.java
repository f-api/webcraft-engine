package com.gameexpert.mob.entity;

import com.gameexpert.engine.raid.RaidLedger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서버를 다시 시작해도 복원할 레이드 인스턴스 하나의 원장 상태입니다. */
@Getter
@Entity
@Table(
        name = "world_raids",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_raid_id",
                columnNames = { "world_id", "raid_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRaid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long raidId;

    @Column(nullable = false)
    private int ledgerVersion;

    @Column(nullable = false)
    private long anchorMobId;

    @Column(nullable = false)
    private double centerX;

    @Column(nullable = false)
    private double centerY;

    @Column(nullable = false)
    private double centerZ;

    private String heroNickname;

    @Column(nullable = false)
    private long armedTick;

    @Column(nullable = false)
    private long rewardSeed;

    @Column(nullable = false)
    private int waveCount;

    @Column(nullable = false)
    private int releasedWaves;

    @Column(nullable = false)
    private int activeTicks;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private long resolvedTick;
    /** [RAID-OMEN] 습격의 징조 레벨(1..5). 옛 행은 null 이고 1 로 읽는다. */
    private Integer omenLevel;
    /** [RAID-OMEN] 마을의 영웅 닉네임(쉼표 구분, 들어온 순서). 옛 행은 null. */
    @Column(length = 1024)
    private String heroes;

    public WorldRaid(Long worldId, RaidLedger.InstanceSnapshot snapshot) {
        this.worldId = worldId;
        this.raidId = snapshot.raidId();
        this.ledgerVersion = snapshot.ledgerVersion();
        this.anchorMobId = snapshot.anchorMobId();
        this.centerX = snapshot.centerX();
        this.centerY = snapshot.centerY();
        this.centerZ = snapshot.centerZ();
        this.heroNickname = snapshot.heroNickname();
        this.armedTick = snapshot.armedTick();
        this.rewardSeed = snapshot.rewardSeed();
        this.waveCount = snapshot.waveCount();
        this.releasedWaves = snapshot.releasedWaves();
        this.activeTicks = snapshot.activeTicks();
        this.status = snapshot.status();
        this.resolvedTick = snapshot.resolvedTick();
        this.omenLevel = snapshot.omenLevel();
        this.heroes = snapshot.heroes() == null || snapshot.heroes().isEmpty()
                ? null : String.join(",", snapshot.heroes());
    }

    public RaidLedger.InstanceSnapshot toSnapshot(List<RaidLedger.MemberSnapshot> members) {
        return new RaidLedger.InstanceSnapshot(ledgerVersion, raidId, anchorMobId,
                centerX, centerY, centerZ, heroNickname, armedTick, rewardSeed,
                waveCount, releasedWaves, activeTicks, status, resolvedTick,
                members == null ? List.of() : members, omenLevel == null ? 1 : omenLevel,
                heroes == null || heroes.isEmpty() ? List.of() : List.of(heroes.split(",")));
    }
}
