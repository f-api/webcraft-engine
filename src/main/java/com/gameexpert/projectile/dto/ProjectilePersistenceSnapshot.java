package com.gameexpert.projectile.dto;

/** Complete live-projectile state required to resume server-authoritative simulation. */
public record ProjectilePersistenceSnapshot(
        long projectileId, String kind, String ownerNickname, long shooterMobId,
        String shooterMobType, double x, double y, double z,
        double velocityX, double velocityY, double velocityZ, int age,
        boolean landed, int lifetimeTicks, int damage,
        String effect, int effectAmplifier, int effectDurationTicks,
        String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
        int recoverableDurability, int noDeflectMcTicks,
        short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks,
        String weaponEnchantments, Long recoverEnchantments, String recoverComponentData,
        boolean inGround, int pickup, int shakeMcTicks, int groundLifeMcTicks,
        int groundBlockId, int groundBlockState,
        String potionKey, String steeringData, long hookedPlacedEntityId) {
    /** Existing saved/projectile-construction shape: no placed fishing target. */
    public ProjectilePersistenceSnapshot(
        long projectileId, String kind, String ownerNickname, long shooterMobId,
        String shooterMobType, double x, double y, double z,
        double velocityX, double velocityY, double velocityZ, int age,
        boolean landed, int lifetimeTicks, int damage,
        String effect, int effectAmplifier, int effectDurationTicks,
        String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
        int recoverableDurability, int noDeflectMcTicks,
        short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks,
        String weaponEnchantments, Long recoverEnchantments, String recoverComponentData,
        boolean inGround, int pickup, int shakeMcTicks, int groundLifeMcTicks,
        int groundBlockId, int groundBlockState,
        String potionKey, String steeringData) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z, velocityX, velocityY,
                velocityZ, age, landed, lifetimeTicks, damage, effect, effectAmplifier, effectDurationTicks,
                hookedPlayer, hookedMobId, spoiledEgg, eggVariant, recoverableDurability, noDeflectMcTicks,
                payloadItemType, payloadCount, cloudRadius, spawnItemAfterMcTicks, weaponEnchantments,
                recoverEnchantments, recoverComponentData, inGround, pickup, shakeMcTicks, groundLifeMcTicks,
                groundBlockId, groundBlockState, potionKey, steeringData, 0L);
    }

    /**
     * [EC-MOBS] 조향 상태 칸 이전의 모양. {@code steeringData} 는 셜커 탄환의 조향(대상 · 방향 · 남은 걸음 · 목표 변위 ·
     * 난수 상태, {@code ProjectileSim.steeringData})이고 다른 종류는 null 이다.
     */
    public ProjectilePersistenceSnapshot(
            long projectileId, String kind, String ownerNickname, long shooterMobId,
            String shooterMobType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, int age,
            boolean landed, int lifetimeTicks, int damage,
            String effect, int effectAmplifier, int effectDurationTicks,
            String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
            int recoverableDurability, int noDeflectMcTicks,
            short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks,
            String weaponEnchantments, Long recoverEnchantments, String recoverComponentData,
            boolean inGround, int pickup, int shakeMcTicks, int groundLifeMcTicks,
            int groundBlockId, int groundBlockState,
            String potionKey) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z,
                velocityX, velocityY, velocityZ, age, landed, lifetimeTicks, damage, effect,
                effectAmplifier, effectDurationTicks, hookedPlayer, hookedMobId, spoiledEgg,
                eggVariant, recoverableDurability, noDeflectMcTicks, payloadItemType, payloadCount,
                cloudRadius, spawnItemAfterMcTicks, weaponEnchantments, recoverEnchantments,
                recoverComponentData, inGround, pickup, shakeMcTicks,
                groundLifeMcTicks, groundBlockId, groundBlockState, potionKey, null);
    }

    /** [UTILITY] 물약 키 이전의 모양(박힘 필드는 있고 물약 키는 없다). */
    public ProjectilePersistenceSnapshot(
            long projectileId, String kind, String ownerNickname, long shooterMobId,
            String shooterMobType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, int age,
            boolean landed, int lifetimeTicks, int damage,
            String effect, int effectAmplifier, int effectDurationTicks,
            String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
            int recoverableDurability, int noDeflectMcTicks,
            short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks,
            String weaponEnchantments, Long recoverEnchantments, String recoverComponentData,
            boolean inGround, int pickup, int shakeMcTicks, int groundLifeMcTicks,
            int groundBlockId, int groundBlockState) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z,
                velocityX, velocityY, velocityZ, age, landed, lifetimeTicks, damage, effect,
                effectAmplifier, effectDurationTicks, hookedPlayer, hookedMobId, spoiledEgg,
                eggVariant, recoverableDurability, noDeflectMcTicks, payloadItemType, payloadCount,
                cloudRadius, spawnItemAfterMcTicks, weaponEnchantments, recoverEnchantments,
                recoverComponentData, inGround, pickup, shakeMcTicks,
                groundLifeMcTicks, groundBlockId, groundBlockState, null);
    }

    /** [UTILITY] 박힘 필드 이전 · 물약 키가 있는 모양(날고 있는 줍기 불가 화살과 같다). */
    public ProjectilePersistenceSnapshot(
            long projectileId, String kind, String ownerNickname, long shooterMobId,
            String shooterMobType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, int age,
            boolean landed, int lifetimeTicks, int damage,
            String effect, int effectAmplifier, int effectDurationTicks,
            String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
            int recoverableDurability, int noDeflectMcTicks,
            short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks,
            String weaponEnchantments, Long recoverEnchantments, String recoverComponentData,
            String potionKey) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z,
                velocityX, velocityY, velocityZ, age, landed, lifetimeTicks, damage, effect,
                effectAmplifier, effectDurationTicks, hookedPlayer, hookedMobId, spoiledEgg,
                eggVariant, recoverableDurability, noDeflectMcTicks, payloadItemType, payloadCount,
                cloudRadius, spawnItemAfterMcTicks, weaponEnchantments, recoverEnchantments,
                recoverComponentData, false, 0, 0, 0, 0, 0, potionKey);
    }

    /**
     * [ARROW-GROUND] 박힘 필드 이전의 모양. 옛 행은 날고 있는 화살이며 줍기 규칙이 없었다
     * ({@code DISALLOWED} = 0).
     */
    public ProjectilePersistenceSnapshot(
            long projectileId, String kind, String ownerNickname, long shooterMobId,
            String shooterMobType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, int age,
            boolean landed, int lifetimeTicks, int damage,
            String effect, int effectAmplifier, int effectDurationTicks,
            String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
            int recoverableDurability, int noDeflectMcTicks,
            short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks,
            String weaponEnchantments, Long recoverEnchantments, String recoverComponentData) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z,
                velocityX, velocityY, velocityZ, age, landed, lifetimeTicks, damage, effect,
                effectAmplifier, effectDurationTicks, hookedPlayer, hookedMobId, spoiledEgg,
                eggVariant, recoverableDurability, noDeflectMcTicks, payloadItemType, payloadCount,
                cloudRadius, spawnItemAfterMcTicks, weaponEnchantments, recoverEnchantments,
                recoverComponentData, false, 0, 0, 0, 0, 0, null);
    }

    /** 인챈트 필드 이전의 모양. 새 세 필드는 모두 null(인챈트 없음)이다. */
    public ProjectilePersistenceSnapshot(
            long projectileId, String kind, String ownerNickname, long shooterMobId,
            String shooterMobType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, int age,
            boolean landed, int lifetimeTicks, int damage,
            String effect, int effectAmplifier, int effectDurationTicks,
            String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
            int recoverableDurability, int noDeflectMcTicks,
            short payloadItemType, int payloadCount, double cloudRadius, int spawnItemAfterMcTicks) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z,
                velocityX, velocityY, velocityZ, age, landed, lifetimeTicks, damage, effect,
                effectAmplifier, effectDurationTicks, hookedPlayer, hookedMobId, spoiledEgg,
                eggVariant, recoverableDurability, noDeflectMcTicks, payloadItemType, payloadCount,
                cloudRadius, spawnItemAfterMcTicks, null, null, null, null);
    }

    public ProjectilePersistenceSnapshot {
        // [UTILITY] 투척·잔류형 물약과 그 효과 구름만 물약 키(바닐라 potion_contents)를 싣는다.
        if (potionKey != null && (!com.gameexpert.engine.effect.PotionCatalog.isKey(potionKey)
                || !"SPLASH_POTION".equals(kind) && !"LINGERING_POTION".equals(kind)
                        && !"AREA_EFFECT_CLOUD".equals(kind))) {
            throw new IllegalArgumentException("invalid projectile potion key");
        }
        // [TRIAL-GAP] 잔류형 물약 · 효과 구름 · 불길한 아이템 소환기 · 효과 화살이 싣는 칸.
        if (payloadItemType < 0 || payloadCount < 0 || spawnItemAfterMcTicks < 0
                || !Double.isFinite(cloudRadius) || cloudRadius < 0.0) {
            throw new IllegalArgumentException("invalid projectile payload");
        }
        if (projectileId <= 0 || kind == null || kind.isBlank() || age < 0
                || lifetimeTicks < 0 || damage < 0 || effectAmplifier < 0
                || effectDurationTicks < 0 || hookedMobId < 0 || hookedPlacedEntityId < 0 || recoverableDurability < 0
                || noDeflectMcTicks < 0
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                || !Double.isFinite(velocityZ)) {
            throw new IllegalArgumentException("invalid projectile persistence snapshot");
        }
        if (hookedPlacedEntityId != 0L && (!"FISHING_BOBBER".equals(kind) || hookedMobId != 0L || hookedPlayer != null)) {
            throw new IllegalArgumentException("placed fishing target requires an otherwise unhooked bobber");
        }
        boolean egg = "EGG".equals(kind);
        boolean validVariant = "temperate".equals(eggVariant)
                || "warm".equals(eggVariant) || "cold".equals(eggVariant);
        if (egg ? spoiledEgg ? eggVariant != null : !validVariant
                : spoiledEgg || eggVariant != null) {
            throw new IllegalArgumentException("invalid thrown egg identity");
        }
        // [ENCHANT-WIDE] 발사 무기 인챈트는 정규 16진, 회수 인챈트·성분은 삼지창 회수물에만 있다.
        if (weaponEnchantments != null) {
            com.gameexpert.engine.enchant.WideEnchantments parsed =
                    com.gameexpert.engine.enchant.WideEnchantments.fromHex(weaponEnchantments);
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("empty projectile weapon enchantments");
            }
        }
        if ((recoverEnchantments != null || recoverComponentData != null)
                && (!"TRIDENT".equals(kind) || recoverableDurability <= 0)) {
            throw new IllegalArgumentException("recovery components require a recoverable trident");
        }
        // [ARROW-GROUND] 박힘·줍기는 화살만 가진다. 줍기 값은 바닐라 Pickup ordinal 0..2 다.
        if (pickup < 0 || pickup > 2 || shakeMcTicks < 0 || groundLifeMcTicks < 0
                || groundBlockId < 0 || groundBlockState < 0) {
            throw new IllegalArgumentException("invalid arrow ground state");
        }
        if ((inGround || pickup != 0 || shakeMcTicks != 0 || groundLifeMcTicks != 0
                || groundBlockId != 0 || groundBlockState != 0) && !"ARROW".equals(kind)) {
            throw new IllegalArgumentException("arrow ground state requires an arrow");
        }
        if (!inGround && (shakeMcTicks != 0 || groundBlockId != 0 || groundBlockState != 0)) {
            throw new IllegalArgumentException("ground facts require an arrow in ground");
        }
        // [EC-MOBS] 조향 상태는 셜커 탄환에만 있다.
        if (steeringData != null && !"SHULKER_BULLET".equals(kind)) {
            throw new IllegalArgumentException("steering data requires a shulker bullet");
        }
        if (recoverEnchantments != null && (recoverEnchantments == 0L
                || !com.gameexpert.engine.enchant.EnchantmentRules.isValidEnchantmentMask(
                        recoverEnchantments))) {
            throw new IllegalArgumentException("invalid recovered trident enchantment mask");
        }
    }

    /** [TRIAL-GAP] 이전 스키마 행(실은 칸 없음). */
    public ProjectilePersistenceSnapshot(
            long projectileId, String kind, String ownerNickname, long shooterMobId,
            String shooterMobType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ, int age,
            boolean landed, int lifetimeTicks, int damage,
            String effect, int effectAmplifier, int effectDurationTicks,
            String hookedPlayer, long hookedMobId, boolean spoiledEgg, String eggVariant,
            int recoverableDurability, int noDeflectMcTicks) {
        this(projectileId, kind, ownerNickname, shooterMobId, shooterMobType, x, y, z,
                velocityX, velocityY, velocityZ, age, landed, lifetimeTicks, damage, effect,
                effectAmplifier, effectDurationTicks, hookedPlayer, hookedMobId, spoiledEgg,
                eggVariant, recoverableDurability, noDeflectMcTicks, (short) 0, 0, 0.0, 0);
    }
}
