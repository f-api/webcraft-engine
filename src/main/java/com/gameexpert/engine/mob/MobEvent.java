package com.gameexpert.engine.mob;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 한 틱의 몹/투사체 처리 결과 이벤트. 이동은 Mob 객체 상태 변이로 반영하고,
 * "바깥 세계에 영향을 주는 것"만 이벤트로 낸다. P6 어댑터가 WS 메시지로 매핑한다.
 *
 * 현재 서버 이벤트 매핑:
 *  - AttackPlayer → 플레이어 피해 적용 + playerHurt/healthUpdate
 *  - ShootArrow   → ProjectileSim 생성 + projectileSpawn
 *  - Explode      → 블록 파괴 계산(소비자) + explosion, 범위 내 플레이어 피해
 *  - Despawned    → mobDespawn
 */
public sealed interface MobEvent {
    /** A projectile hit a player-placed stand or minecart (IDs have their own namespace). */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class AttackPlacedEntity implements MobEvent {
        private final long entityId;
        private final ProjectileSim.Kind kind;
        private final float damage;
        private final boolean burning;
        private final double speedSquared;
        private final double sourceX;
        private final double sourceZ;
    }


    /** A nectar-bearing Bee reached a registered nest/hive and requests an atomic entry commit. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BeeHiveEntry implements MobEvent {
        private final int x;
        private final int y;
        private final int z;
    }

    /**
     * An Allay reached the dropped stack selected by the item-system adapter. The consumer must
     * atomically remove up to {@code count} matching items, then call Allay.confirmPickup.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class AllayPickupItem implements MobEvent {
        private final long itemEntityId;
        private final short itemType;
        private final int durability;
        private final int count;
    }

    /**
     * An Allay brought collected matching items back to its liked player. The consumer must add as
     * many as fit, then acknowledge only that amount through Allay.confirmReturnedItems.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class AllayReturnItem implements MobEvent {
        private final String nickname;
        private final short itemType;
        private final int durability;
        private final int count;
    }

    /** Successful birth boundary; the authority awards breeding XP exactly once here. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class BreedingComplete implements MobEvent {
        private final double x;
        private final double y;
        private final double z;
    }

    /** Living-mob gameplay drop such as an Armadillo's periodic scute shed. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class DropItem implements MobEvent {
        private final short itemType;
        private final int count;
        private final double x;
        private final double y;
        private final double z;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class FroglightDrop implements MobEvent {
        private final long frogMobId;
        private final long sulfurCubeMobId;
        private final short itemType;
        private final double x;
        private final double y;
        private final double z;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class CopperGolemStatue implements MobEvent {
        private final int x;
        private final int y;
        private final int z;
        private final int state;
    }

    /** Leash exceeded ten blocks; central returns one exact lead or drops it once. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class LeashBroken implements MobEvent {
        private final String holderNickname;
        private final double x;
        private final double y;
        private final double z;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class GoatRamPlayer implements MobEvent {
        private final String nickname;
        private final int damage;
        private final double directionX;
        private final double directionZ;
        private final double knockback;
    }

    /** 서버가 실제로 수행한 몹 행동의 사운드 의미. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Sound implements MobEvent {
        private final String kind;
    }

    /** 벌의 쏘기 시도. 소비자가 플레이어 피해 성공 뒤에만 sting 사운드를 낸다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class StingPlayer implements MobEvent {
        private final String nickname;
        private final int damage;
        private final double sourceX;
        private final double sourceZ;
    }

    /** 성공한 순간이동. 실패한 시도는 이벤트를 만들지 않는다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Teleported implements MobEvent {
        private final double fromX;
        private final double fromY;
        private final double fromZ;
        private final double toX;
        private final double toY;
        private final double toZ;
    }

    /** 근접 공격이 플레이어에 명중(damage 만큼 피해). 화살 명중도 이 이벤트로 표현한다. */
    @Getter
    @Accessors(fluent = true)
    final class AttackPlayer implements MobEvent {
        private final String nickname;
        private final int damage;
        /** 방패 정면 판정에 쓰는 공격 시작점의 수평 좌표. */
        private final double sourceX;
        private final double sourceZ;
        /** 플레이어가 쏜 화살일 때의 발사자. 근접/몹 화살은 null. */
        private final String shooterNickname;
        /** 몹 투사체의 직접 발사자. 근접/플레이어 투사체는 0. */
        private final long shooterMobId;
        /** 발사 순간의 몹 종류. 발사자가 사라진 뒤에도 사망 원인을 보존한다. */
        private final MobType shooterMobType;
        /** 명중 시 함께 부여할 상태이상(동굴거미 독·팁 화살). 없으면 null. */
        private final ProjectileEffect effect;

        public AttackPlayer(String nickname, int damage, double sourceX, double sourceZ) {
            this(nickname, damage, sourceX, sourceZ, null, 0, null, null);
        }

        public AttackPlayer(String nickname, int damage, double sourceX, double sourceZ,
                            String shooterNickname) {
            this(nickname, damage, sourceX, sourceZ, shooterNickname, 0, null, null);
        }

        public AttackPlayer(String nickname, int damage, double sourceX, double sourceZ,
                            String shooterNickname, ProjectileEffect effect) {
            this(nickname, damage, sourceX, sourceZ, shooterNickname, 0, null, effect);
        }

        public AttackPlayer(String nickname, int damage, double sourceX, double sourceZ,
                            String shooterNickname, long shooterMobId, ProjectileEffect effect) {
            this(nickname, damage, sourceX, sourceZ, shooterNickname, shooterMobId, null, effect);
        }

        public AttackPlayer(String nickname, int damage, double sourceX, double sourceZ,
                            String shooterNickname, long shooterMobId, MobType shooterMobType,
                            ProjectileEffect effect) {
            this.nickname = nickname;
            this.damage = damage;
            this.sourceX = sourceX;
            this.sourceZ = sourceZ;
            this.shooterNickname = shooterNickname;
            this.shooterMobId = shooterMobId;
            this.shooterMobType = shooterMobType;
            this.effect = effect;
        }
    }

    /** 화살 명중 또는 직접 AI 근접 의도. shooterNickname은 플레이어 발사일 때만 존재합니다. */
    @Getter
    @Accessors(fluent = true)
    final class AttackMob implements MobEvent {
        private final long mobId;
        private final int damage;
        private final String shooterNickname;
        private final long shooterMobId;
        /** 발사 순간의 권위 종류. 발사자가 사라진 뒤에도 아군 판정을 보존한다. */
        private final MobType shooterMobType;
        /** 명중 시 함께 부여할 상태이상(팁 화살). 없으면 null. */
        private final ProjectileEffect effect;
        /** Direct AI intents are resolved by MobSystem after all mob AI has run. */
        private final boolean direct;
        /**
         * [ENCHANT-WIDE] 투사체를 쏜/던진 무기의 인챈트(밀어내기·화염·찌르기). 몹 발사·직접 공격은 빈 집합이다.
         */
        private final com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments;
        /** [ENCHANT-WIDE] 명중 순간 투사체의 수평 속도(밀어내기 방향, 블록/틱). */
        private final double hitVelocityX;
        private final double hitVelocityZ;

        public AttackMob(long mobId, int damage, String shooterNickname) {
            this(mobId, damage, shooterNickname, 0, null, null, false);
        }

        public AttackMob(long mobId, int damage, String shooterNickname, ProjectileEffect effect) {
            this(mobId, damage, shooterNickname, 0, null, effect, false);
        }

        public AttackMob(long mobId, int damage, String shooterNickname,
                         long shooterMobId, MobType shooterMobType, ProjectileEffect effect) {
            this(mobId, damage, shooterNickname, shooterMobId, shooterMobType, effect, false);
        }

        private AttackMob(long mobId, int damage, String shooterNickname,
                          long shooterMobId, MobType shooterMobType,
                          ProjectileEffect effect, boolean direct) {
            this(mobId, damage, shooterNickname, shooterMobId, shooterMobType, effect, direct,
                    com.gameexpert.engine.enchant.WideEnchantments.EMPTY, 0.0, 0.0);
        }

        private AttackMob(long mobId, int damage, String shooterNickname,
                          long shooterMobId, MobType shooterMobType,
                          ProjectileEffect effect, boolean direct,
                          com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments,
                          double hitVelocityX, double hitVelocityZ) {
            this.mobId = mobId;
            this.damage = damage;
            this.shooterNickname = shooterNickname;
            this.shooterMobId = shooterMobId;
            this.shooterMobType = shooterMobType;
            this.effect = effect;
            this.direct = direct;
            this.weaponEnchantments = weaponEnchantments;
            this.hitVelocityX = hitVelocityX;
            this.hitVelocityZ = hitVelocityZ;
        }

        /** [ENCHANT-WIDE] 같은 명중에 발사 무기 인챈트와 명중 순간 수평 속도를 싣는다. */
        public AttackMob withProjectileWeapon(
                com.gameexpert.engine.enchant.WideEnchantments enchantments,
                double velocityX, double velocityZ) {
            return new AttackMob(mobId, damage, shooterNickname, shooterMobId, shooterMobType,
                    effect, direct, enchantments, velocityX, velocityZ);
        }

        public static AttackMob direct(long mobId, int damage) {
            return new AttackMob(mobId, damage, null, 0, null, null, true);
        }

        public static AttackMob direct(long mobId, int damage, ProjectileEffect effect) {
            return new AttackMob(mobId, damage, null, 0, null, effect, true);
        }
    }

    /** 몹이 원거리 투사체를 발사. 좌표=발사 원점, v*=초기 속도(블록/틱). */
    @Getter
    @Accessors(fluent = true)
    final class ShootArrow implements MobEvent {
        private final ProjectileSim.Kind kind;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;
        private final int damage;
        /** 팁 화살·투척 물약이 실어 나르는 상태이상. 일반 화살은 null. */
        private final ProjectileEffect effect;

        public ShootArrow(ProjectileSim.Kind kind, double x, double y, double z,
                          double vx, double vy, double vz, int damage) {
            this(kind, x, y, z, vx, vy, vz, damage, null);
        }

        public ShootArrow(ProjectileSim.Kind kind, double x, double y, double z,
                          double vx, double vy, double vz, int damage, ProjectileEffect effect) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.damage = damage;
            this.effect = effect;
        }
    }

    /**
     * [CONTAINER-MENUS] 경험치 병 착탄({@code ThrownExperienceBottle.onHit}): 좌표는 명중 지점,
     * 방향은 맞은 면의 바깥 법선(블록) 또는 −속도(개체)다.
     */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class ExperienceBottleHit implements MobEvent  {
        double x;
        double y;
        double z;
        double directionX;
        double directionY;
        double directionZ;
}

    /** 투척 물약 착탄. 반경 안 플레이어·몹에게 거리 감쇠를 적용해 효과를 뿌린다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SplashPotion implements MobEvent {
        private final double x;
        private final double y;
        private final double z;
        private final ProjectileEffect effect;
        /** Player thrower, or {@code null} for witch/mob-thrown potions. */
        private final String shooterNickname;
        /** [UTILITY] 물약 키(있으면 효과 전부를 건다). 마녀 물약은 null 이다. */
        private final String potionKey;

        public SplashPotion(double x, double y, double z, ProjectileEffect effect,
                String shooterNickname) {
            this(x, y, z, effect, shooterNickname, null);
        }
    }

    /**
     * [TRIAL-GAP] 잔류형 물약 착탄({@code ThrownLingeringPotion.onHitAsPotion}): 이 좌표(엔티티
     * 명중이면 그 엔티티의 발 위치)에 효과 구름을 세운다. {@code potionItem} 은 구름 색이다.
     */
    record LingeringPotion(double x, double y, double z, ProjectileEffect effect,
            String shooterNickname, short potionItem, String potionKey) implements MobEvent {}

    /** [TRIAL-GAP] 효과 구름이 이번 틱에 효과를 건 플레이어·몹. 호출자가 효과를 실제로 건다. */
    record CloudApplications(java.util.List<String> players, java.util.List<Long> mobIds,
            ProjectileEffect effect, String ownerNickname, String potionKey) implements MobEvent {}

    /** [TRIAL-GAP] 불길한 아이템 소환기 예고음({@code TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM}). */
    record OminousItemSpawnerSound(double x, double y, double z) implements MobEvent {}

    /** [TRIAL-GAP] 불길한 아이템 소환기가 아이템을 내보낼 차례다({@code spawnItem}). */
    record OminousItemSpawnerRelease(double x, double y, double z, short itemType, int count)
            implements MobEvent {}

    /** [TRIAL-GAP] 작은 화염구가 블록에 맞았다. 이 칸이 비었으면 불을 놓는다. */
    record SmallFireballBlockHit(int x, int y, int z) implements MobEvent {}

    /** [DRAGON] 드래곤 화염구가 개체나 블록에 닿았다({@code DragonFireball.onHit}): 숨결 구름 + level event 2006. */
    record DragonFireballHit(double x, double y, double z) implements MobEvent {}

    /**
     * [EC-MOBS] 셜커가 탄환 하나를 쐈다({@code ShulkerAttackGoal.tick} → {@code new ShulkerBullet(level, shulker,
     * target, axis)}). 좌표는 셜커 상자 중심, 대상은 플레이어 닉네임 또는 몹 id, {@code axis} 는 부착축이다.
     */
    record ShootShulkerBullet(double x, double y, double z, String targetNickname, long targetMobId, int axis)
            implements MobEvent {}

    /**
     * [EC-MOBS] 셜커 탄환에 맞은 셜커가 순간이동에 성공해 옛 자리에 새 셜커를 남긴다({@code Shulker.hitByShulkerBullet}
     * 의 {@code EntityType.SHULKER.create} → {@code moveTo(oldPos)}). 색은 원본을 따른다(이 저장소는 무색뿐).
     */
    record ShulkerClone(double x, double y, double z) implements MobEvent {}

    /** Ravager roar resolution is owned by the runtime because it affects every nearby entity. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class RavagerRoar implements MobEvent {
        private final double x;
        private final double y;
        private final double z;
        private final double radius;
        private final int damage;
    }

    /** Evoker cast completion requests one bounded, authority-owned temporary Vex. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class SummonVex implements MobEvent {
        private final double x;
        private final double y;
        private final double z;
    }

    /**
     * 크리퍼 폭발. 좌표=폭심, radius=클라이언트 효과에도 쓰는 폭발력(power),
     * maxDamage=공식 검산값. 실제 피해 반경은 power×2이고 블록 파괴와 함께 소비자가 계산한다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Explode implements MobEvent {
        private final double x;
        private final double y;
        private final double z;
        private final int radius;
        private final int maxDamage;
    }

    /** 몹 소멸 통보(reason: "far"/"random"/"exploded" 등). */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Despawned implements MobEvent {
        private final String reason;
    }

    /** 변환으로 기존 ID를 닫고 replacementMobId를 연다. 소비자는 외부 장비 메타데이터도 함께 옮긴다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class Converted implements MobEvent {
        private final long replacementMobId;
    }

    /** 갑옷을 무시하는 서버 환경 피해 결과. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class EnvironmentDamage implements MobEvent {
        private final boolean lethal;
    }

    /** 엔더맨 블록 집기/놓기. expectedBlock이 현재 값과 다르면 소비자가 무시한다. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ChangeBlock implements MobEvent {
        private final int x;
        private final int y;
        private final int z;
        private final int expectedBlock;
        private final int replacementBlock;
    }

    /**
     * [GUARDIAN] 반경 안의 모든 플레이어에게 같은 상태이상을 한 번 부여한다. 대상이 한 명이
     * 아니라 "주변 전원"이라 {@link RavagerRoar} 와 같은 이유로 런타임이 해석을 소유한다.
     *
     * <p>바닐라 근거는 {@code ElderGuardian.customServerAiStep} 의
     * {@code MobEffectUtil.addEffectToPlayersAround(level, this, position(), 50.0, effect, 1200)} 이다.
     */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class AreaStatusEffect implements MobEvent {
        private final double x;
        private final double y;
        private final double z;
        private final double radius;
        private final ProjectileEffect effect;
    }

    /** 같은 블록 ID의 서버 권위 상태만 한 단계 바꾸는 조건부 변경. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    final class ChangeBlockState implements MobEvent {
        private final int x;
        private final int y;
        private final int z;
        private final int expectedBlock;
        private final int expectedState;
        private final int replacementState;
    }
}
