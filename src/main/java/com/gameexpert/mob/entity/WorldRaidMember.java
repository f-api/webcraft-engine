package com.gameexpert.mob.entity;

import com.gameexpert.engine.raid.RaidLedger;
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

/**
 * 레이드 원장의 멤버 한 명. 사망·디스폰으로 은퇴한 멤버도 남겨야 재기동 뒤 total health 와
 * 승리 판정이 같은 값을 낸다.
 */
@Getter
@Entity
@Table(
        name = "world_raid_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_raid_member",
                columnNames = { "world_id", "raid_id", "mob_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRaidMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long raidId;

    @Column(nullable = false)
    private long mobId;

    @Column(nullable = false)
    private int wave;

    @Column(nullable = false)
    private double maxHealth;

    @Column(nullable = false)
    private double health;

    @Column(nullable = false)
    private boolean retired;
    /** [RAID-OMEN] 합류 때 굳힌 레이드 강화 마법 부여 레벨. 옛 행은 null 이고 0 으로 읽는다. */
    private Integer enchantLevel;

    public WorldRaidMember(Long worldId, long raidId, RaidLedger.MemberSnapshot snapshot) {
        this.worldId = worldId;
        this.raidId = raidId;
        this.mobId = snapshot.mobId();
        this.wave = snapshot.wave();
        this.maxHealth = snapshot.maxHealth();
        this.health = snapshot.health();
        this.retired = snapshot.retired();
        this.enchantLevel = snapshot.enchantLevel();
    }

    public RaidLedger.MemberSnapshot toSnapshot() {
        return new RaidLedger.MemberSnapshot(mobId, wave, maxHealth, health, retired,
                enchantLevel == null ? 0 : enchantLevel);
    }
}
