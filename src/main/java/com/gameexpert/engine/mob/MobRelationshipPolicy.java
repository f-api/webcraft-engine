package com.gameexpert.engine.mob;

/**
 * Explicit social-combat matrix for the currently registered village/illager mobs.
 * This policy models target legality only; raid execution, ownership, and effects stay elsewhere.
 */
public final class MobRelationshipPolicy {
    private static final double PILLAGER_TARGET_RANGE = 16.0;
    private static final double VINDICATOR_TARGET_RANGE = 12.0;
    private static final double RAVAGER_TARGET_RANGE = 32.0;
    private static final double ILLUSIONER_TARGET_RANGE = 18.0;
    private static final double IRON_GOLEM_TARGET_RANGE = 16.0;
    private static final double PILLAGER_ATTACK_REACH = 2.5;
    private static final double VINDICATOR_ATTACK_REACH = 2.5;
    private static final double RAVAGER_ATTACK_REACH = 2.8;
    private static final double IRON_GOLEM_ATTACK_REACH = 2.0;

    private MobRelationshipPolicy() {}

    public static boolean isSocialAttacker(Mob mob) {
        return mob != null && (mob.type == MobType.IRON_GOLEM
                || mob.type == MobType.SNOW_GOLEM || mob.type == MobType.FOX || isRaiderFaction(mob.type));
    }

    /**
     * Returns the directed relationship. Illagers are allies with one another, so the
     * matrix deliberately contains no Pillager↔Vindicator edge.
     */
    public static boolean mayDirectlyTarget(Mob attacker, Mob target) {
        if (attacker == null || target == null || attacker == target
                || attacker.isDead() || attacker.removed
                || target.isDead() || target.removed) return false;
        return switch (attacker.type) {
            case PILLAGER, VINDICATOR, EVOKER, ILLUSIONER, RAVAGER, VEX, WITCH,
                    STANDARD_BEARER, WEB_TRAPPER, BREACHER, DEMOLISHER, BUILDER ->
                    target.type == MobType.IRON_GOLEM
                            || target.type == MobType.VILLAGER && !target.isBaby();
            case IRON_GOLEM -> isRaiderFaction(target.type);
            // SnowGolem registers NearestAttackableTargetGoal<Monster>. In this authority the
            // MONSTER category is the exact class boundary represented by MobType.hostile().
            case SNOW_GOLEM -> target.type.hostile();
            case FOX -> target.type == MobType.CHICKEN || target.type == MobType.RABBIT;
            case WOLF -> attacker instanceof Wolf wolf && wolf.mayDefendOwnerAgainst(target);
            // [NAUTILUS-BEHAVIOR] 두 종 모두 [B] 가 같은 문장으로 "occasionally dash toward and
            // attack nearby pufferfish" 를 적는다. 길들이면 passive 가 되므로 표적 자체가 없다.
            case NAUTILUS, ZOMBIE_NAUTILUS ->
                    attacker.ownerNickname() == null && target.type == MobType.PUFFERFISH;
            default -> false;
        };
    }

    public static double targetRange(Mob attacker) {
        if (attacker == null) return 0.0;
        return switch (attacker.type) {
            case PILLAGER, WITCH, STANDARD_BEARER, WEB_TRAPPER,
                    BREACHER, DEMOLISHER, BUILDER -> PILLAGER_TARGET_RANGE;
            case VINDICATOR -> VINDICATOR_TARGET_RANGE;
            case RAVAGER -> RAVAGER_TARGET_RANGE;
            case ILLUSIONER -> ILLUSIONER_TARGET_RANGE;
            case EVOKER -> 12.0;
            case VEX -> 32.0;
            case IRON_GOLEM -> IRON_GOLEM_TARGET_RANGE;
            case SNOW_GOLEM -> SnowGolem.THROW_RANGE;
            case FOX, WOLF -> 16.0;
            case NAUTILUS, ZOMBIE_NAUTILUS -> NautilusFamilyMob.CHARGE_TARGET_RANGE;
            default -> 0.0;
        };
    }

    public static double attackReach(Mob attacker) {
        if (attacker == null) return 0.0;
        return switch (attacker.type) {
            case PILLAGER, ILLUSIONER, STANDARD_BEARER, WEB_TRAPPER,
                    BREACHER, DEMOLISHER, BUILDER -> PILLAGER_ATTACK_REACH;
            case VINDICATOR -> VINDICATOR_ATTACK_REACH;
            case RAVAGER -> RAVAGER_ATTACK_REACH;
            case EVOKER -> 12.0;
            case WITCH -> 10.0;
            case VEX -> 1.2;
            case IRON_GOLEM -> IRON_GOLEM_ATTACK_REACH;
            case FOX, WOLF -> 2.0;
            case NAUTILUS, ZOMBIE_NAUTILUS -> 1.2;
            default -> 0.0;
        };
    }

    /** 현재 등록된 우민 진영. 표적 선택과 달리 투사체는 아군만 통과하고 다른 몹에는 맞는다. */
    public static boolean areAllied(MobType source, MobType target) {
        if (source == null || target == null) return false;
        return isRaiderFaction(source) && isRaiderFaction(target);
    }

    public static boolean isRaiderFaction(MobType type) {
        if (type == null) return false;
        return switch (type) {
            case PILLAGER, VINDICATOR, EVOKER, ILLUSIONER, RAVAGER, VEX, WITCH,
                    STANDARD_BEARER, WEB_TRAPPER, BREACHER, DEMOLISHER, BUILDER -> true;
            default -> false;
        };
    }

    /**
     * Species allowed to carry a persisted raid membership. Vex is a raider faction member but is
     * summoned by an Evoker rather than released by a wave, and vanilla keeps it out of the raid
     * roster, total health and boss bar, so it can never own a raid id. The two illager companion
     * animals do join, because they replace one released illager in the threat budget.
     */
    public static boolean isRaidMembershipType(MobType type) {
        if (type == null || type == MobType.VEX) return false;
        return isRaiderFaction(type)
                || type == MobType.BRIARBACK || type == MobType.GLOAMKITE;
    }

    /** 발사 순간의 종류 스냅샷으로 지연 명중도 같은 아군 판정을 사용한다. */
    public static boolean mayProjectileDamage(MobType shooterType, Mob target) {
        return target != null && !target.isDead() && !target.removed
                && !areAllied(shooterType, target.type);
    }

    public static boolean inTargetRange(Mob attacker, Mob target) {
        double range = targetRange(attacker);
        return range > 0.0 && distanceSquared(attacker, target) <= range * range;
    }

    public static boolean inAttackReach(Mob attacker, Mob target) {
        double reach = attackReach(attacker);
        return reach > 0.0 && distanceSquared(attacker, target) <= reach * reach;
    }

    public static boolean hasLineOfSight(MobWorldView world, Mob attacker, Mob target) {
        if (world == null || attacker == null || target == null) return false;
        return world.hasLineOfSight(
                attacker.x, attacker.y + attacker.eyeHeight(), attacker.z,
                target.x, target.y + target.eyeHeight(), target.z);
    }

    static double distanceSquared(Mob first, Mob second) {
        if (first == null || second == null) return Double.POSITIVE_INFINITY;
        double dx = second.x - first.x;
        double dy = second.y - first.y;
        double dz = second.z - first.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
