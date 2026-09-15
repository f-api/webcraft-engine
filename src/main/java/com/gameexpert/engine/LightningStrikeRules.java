package com.gameexpert.engine;

import com.gameexpert.engine.mob.MobType;

/**
 * 낙뢰 효과의 순수 규칙(바닐라 1.21.4 {@code LightningBolt} · {@code Entity#thunderHit}).
 *
 * <p>여기 있는 값은 전부 바닐라에서 유도했고 자작 수치가 없다. 양 권위(Java 서버 ·
 * 정적판 {@code StandaloneLightningRules.ts})가 같은 표를 쓴다.
 */
public final class LightningStrikeRules {

    private LightningStrikeRules() { }

    /**
     * 타격 반경. 바닐라 {@code LightningBolt#tick} 의 피해 대상 AABB:
     * {@code new AABB(getX()-3, getY()-3, getZ()-3, getX()+3, getY()+6+3, getZ()+3)}.
     */
    public static final double IMPACT_HALF_EXTENT = 3.0;
    /** 위 AABB 의 아래 여유. */
    public static final double IMPACT_BELOW = 3.0;
    /** 위 AABB 의 위 여유(6 + 3). */
    public static final double IMPACT_ABOVE = 9.0;

    /** {@code Entity#thunderHit}: {@code hurt(damageSources().lightningBolt(), 5.0F)}. */
    public static final double DAMAGE = 5.0;

    /**
     * {@code Entity#thunderHit}: {@code igniteForSeconds(8.0F)}. 이 서버는 10 TPS 이므로
     * 8초 = 80 틱이다(용암 15초=150틱과 같은 환산).
     */
    public static final int FIRE_TICKS = 80;

    /**
     * {@code LightningBolt#tick} 이 {@code life == 2} 에서 호출하는 {@code spawnFire(4)} —
     * 타격 칸 1회 + 3×3 안 추가 4회 시도.
     */
    public static final int EXTRA_IGNITIONS = 4;

    /**
     * 발화는 바닐라와 같이 NORMAL/HARD 에서만 일어난다({@code LightningBolt#tick}:
     * {@code if (difficulty == NORMAL || difficulty == HARD) spawnFire(4)}).
     */
    public static boolean ignitesAt(Difficulty difficulty) {
        return difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD;
    }

    /**
     * 엔티티가 낙뢰 피해 AABB 안인가. 바닐라는 엔티티 AABB 와 낙뢰 AABB 의 교차를 보므로
     * 발 좌표만이 아니라 몸 높이(height)까지 넣어 판정한다.
     */
    public static boolean withinImpact(LightningStrike strike,
            double x, double footY, double z, double halfWidth, double height) {
        double minX = strike.x() - IMPACT_HALF_EXTENT;
        double maxX = strike.x() + IMPACT_HALF_EXTENT;
        double minZ = strike.z() - IMPACT_HALF_EXTENT;
        double maxZ = strike.z() + IMPACT_HALF_EXTENT;
        double minY = strike.y() - IMPACT_BELOW;
        double maxY = strike.y() + IMPACT_ABOVE;
        return x + halfWidth >= minX && x - halfWidth <= maxX
                && z + halfWidth >= minZ && z - halfWidth <= maxZ
                && footY + height >= minY && footY <= maxY;
    }

    /** 낙뢰가 한 몹에게 하는 일. 바닐라의 {@code thunderHit} 오버라이드 표. */
    public enum ThunderHit {
        /** {@code Entity#thunderHit} 기본: 8초 발화 + 5 피해. */
        DAMAGE_AND_IGNITE,
        /** {@code Villager#thunderHit}: 마녀로 변신하고 피해·발화는 받지 않는다. */
        CONVERT_TO_WITCH,
        /** {@code Creeper#thunderHit}: {@code super.thunderHit} 후 powered 세팅(피해도 받는다). */
        CHARGE_CREEPER,
        /** {@code Pig#thunderHit}: 좀비화 피글린으로 변신(피해·발화 없음). */
        CONVERT_TO_ZOMBIFIED_PIGLIN,
    }

    /** 몹 종류별 {@code thunderHit} 분기. */
    public static ThunderHit thunderHit(MobType type) {
        return switch (type) {
            case VILLAGER -> ThunderHit.CONVERT_TO_WITCH;
            case CREEPER -> ThunderHit.CHARGE_CREEPER;
            case PIG -> ThunderHit.CONVERT_TO_ZOMBIFIED_PIGLIN;
            default -> ThunderHit.DAMAGE_AND_IGNITE;
        };
    }

    /**
     * {@code spawnFire} 의 추가 점화 오프셋: {@code blockPos.offset(random.nextInt(3)-1, 0,
     * random.nextInt(3)-1)}. 두 난수를 호출부가 뽑아 넘긴다(난수 lane 을 규칙에 가두지 않는다).
     */
    public static int ignitionOffset(int roll3) {
        return roll3 - 1;
    }
}
