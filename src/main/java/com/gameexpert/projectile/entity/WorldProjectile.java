package com.gameexpert.projectile.entity;

import com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot;
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

/** Durable row for one live server-authoritative projectile. */
@Getter
@Entity
@Table(name = "world_projectiles", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_projectile_id", columnNames = {"world_id", "projectile_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldProjectile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false) private Long worldId;
    @Column(name = "projectile_id", nullable = false) private long projectileId;
    @Column(nullable = false) private String kind;
    private String ownerNickname;
    @Column(nullable = false) private long shooterMobId;
    private String shooterMobType;
    @Column(nullable = false) private double posX;
    @Column(nullable = false) private double posY;
    @Column(nullable = false) private double posZ;
    @Column(nullable = false) private double velocityX;
    @Column(nullable = false) private double velocityY;
    @Column(nullable = false) private double velocityZ;
    @Column(nullable = false) private int age;
    @Column(nullable = false) private boolean landed;
    @Column(nullable = false) private int lifetimeTicks;
    @Column(nullable = false) private int damage;
    private String effect;
    @Column(nullable = false) private int effectAmplifier;
    @Column(nullable = false) private int effectDurationTicks;
    private String hookedPlayer;
    @Column(nullable = false) private long hookedMobId;
    /** Nullable for pre-placed-hook persisted rows. */
    private Long hookedPlacedEntityId;
    @Column(nullable = false) private boolean spoiledEgg;
    private String eggVariant;
    @Column(nullable = false) private int recoverableDurability;
    @Column(nullable = false) private int noDeflectMcTicks;
    // [TRIAL-GAP] 잔류형 물약 · 효과 구름 · 불길한 아이템 소환기 · 효과 화살 칸. 옛 행은 null 이다.
    private Integer payloadItemType;
    private Integer payloadCount;
    private Double cloudRadius;
    private Integer spawnItemAfterMcTicks;
    /** [ENCHANT-WIDE] 발사 무기 인챈트(정규 16진). 없으면 null — 옛 행이 그대로 읽힌다. */
    private String weaponEnchantments;
    /** [ENCHANT-WIDE] 회수될 삼지창의 워드 0 인챈트 마스크와 성분 문자열(WCIC2/3). */
    private Long recoverEnchantments;
    @Column(length = 8192)
    private String recoverComponentData;
    /**
     * [ARROW-GROUND] 박힌 화살 상태(바닐라 {@code inGround}·{@code pickup}·{@code shake}·{@code life}·
     * {@code inBlockState}). 옛 행은 모두 null 이라 날고 있는 줍기 불가 화살로 읽힌다.
     */
    private Boolean inGround;
    private Integer arrowPickup;
    private Integer shakeMcTicks;
    private Integer groundLifeMcTicks;
    private Integer groundBlockId;
    private Integer groundBlockState;
    /** [UTILITY] 투척·잔류형 물약과 효과 구름의 물약 키. 옛 행은 null 이다. */
    private String potionKey;
    /** [EC-MOBS] 셜커 탄환 조향 상태(ProjectileSim.steeringData). 다른 종류와 옛 행은 null 이다. */
    @Column(length = 1024)
    private String steeringData;

    public WorldProjectile(Long worldId, ProjectilePersistenceSnapshot snapshot) {
        this.worldId = worldId;
        this.projectileId = snapshot.projectileId();
        apply(snapshot);
    }

    public void apply(ProjectilePersistenceSnapshot s) {
        if (projectileId != s.projectileId()) throw new IllegalArgumentException("projectile identity cannot change");
        kind = s.kind(); ownerNickname = s.ownerNickname(); shooterMobId = s.shooterMobId();
        shooterMobType = s.shooterMobType(); posX = s.x(); posY = s.y(); posZ = s.z();
        velocityX = s.velocityX(); velocityY = s.velocityY(); velocityZ = s.velocityZ();
        age = s.age(); landed = s.landed(); lifetimeTicks = s.lifetimeTicks(); damage = s.damage();
        effect = s.effect(); effectAmplifier = s.effectAmplifier();
        effectDurationTicks = s.effectDurationTicks(); hookedPlayer = s.hookedPlayer();
        hookedPlacedEntityId = s.hookedPlacedEntityId();
        hookedMobId = s.hookedMobId(); spoiledEgg = s.spoiledEgg(); eggVariant = s.eggVariant();
        recoverableDurability = s.recoverableDurability();
        noDeflectMcTicks = s.noDeflectMcTicks();
        payloadItemType = (int) s.payloadItemType();
        payloadCount = s.payloadCount();
        cloudRadius = s.cloudRadius();
        spawnItemAfterMcTicks = s.spawnItemAfterMcTicks();
        weaponEnchantments = s.weaponEnchantments();
        recoverEnchantments = s.recoverEnchantments();
        recoverComponentData = s.recoverComponentData();
        inGround = s.inGround();
        arrowPickup = s.pickup();
        shakeMcTicks = s.shakeMcTicks();
        groundLifeMcTicks = s.groundLifeMcTicks();
        groundBlockId = s.groundBlockId();
        groundBlockState = s.groundBlockState();
        potionKey = s.potionKey();
        steeringData = s.steeringData();
    }

    public ProjectilePersistenceSnapshot toSnapshot() {
        return new ProjectilePersistenceSnapshot(projectileId, kind, ownerNickname, shooterMobId,
                shooterMobType, posX, posY, posZ, velocityX, velocityY, velocityZ, age,
                landed, lifetimeTicks, damage, effect, effectAmplifier, effectDurationTicks,
                hookedPlayer, hookedMobId, spoiledEgg, eggVariant,
                recoverableDurability, noDeflectMcTicks,
                payloadItemType == null ? 0 : (short) (int) payloadItemType,
                payloadCount == null ? 0 : payloadCount,
                cloudRadius == null ? 0.0 : cloudRadius,
                spawnItemAfterMcTicks == null ? 0 : spawnItemAfterMcTicks,
                weaponEnchantments, recoverEnchantments, recoverComponentData,
                inGround != null && inGround, arrowPickup == null ? 0 : arrowPickup,
                shakeMcTicks == null ? 0 : shakeMcTicks,
                groundLifeMcTicks == null ? 0 : groundLifeMcTicks,
                groundBlockId == null ? 0 : groundBlockId,
                groundBlockState == null ? 0 : groundBlockState,
                potionKey, steeringData, hookedPlacedEntityId == null ? 0L : hookedPlacedEntityId);
    }

    public boolean matches(ProjectilePersistenceSnapshot snapshot) {
        return toSnapshot().equals(snapshot);
    }
}
