package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * [SPEAR-MOB] 창을 쓰는 몹의 {@code KineticWeapon.damageEntities}(권위 틱마다 MC 틱 둘) — 대상 선택과 단계 조건만 정하는 순수
 * 함수다. 몹은 {@code damageEntities} 의 속도 배율이 0.2 이고 기본 공격력은 그 몹의 {@code ATTACK_DAMAGE} 속성 기본값이다.
 * 정적판 사본은 {@code evaluateStandaloneKineticTick}(같은 인수) 다.
 */
public final class MobKinetic {

    private MobKinetic() {}

    /** {@code damageEntities} 의 쓰는 개체 속도 배율: 플레이어가 아니면 0.2f. */
    public static final float MOB_SPEED_FACTOR = 0.2f;

    /** 쓰는 몹: 발 위치 · 눈 높이 · 시선(단위 벡터) · 이번 권위 틱의 MC 틱당 이동. */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Attacker {
        double x;
        double y;
        double z;
        double eyeY;
        double lookX;
        double lookY;
        double lookZ;
        double moveX;
        double moveY;
        double moveZ;
}

    /**
     * 찌를 수 있는 대상. {@code key} 는 접촉 쿨다운 표의 열쇠(플레이어 {@code p:닉네임}, 몹 {@code m:id}), 상자는
     * {@code [minX, minY, minZ, maxX, maxY, maxZ]}(부위가 여럿이면 여러 상자), 속도는 권위 틱당 블록이다.
     */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Target {
        String key;
        double[][] boxes;
        double vx;
        double vy;
        double vz;
}

    /** 찌르기 한 건. */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Stab {
        String key;
        long useMcTicks;
        float amount;
        boolean dealsDamage;
        boolean knockback;
        boolean dismount;
}

    /** 선분의 가시선(블록). */
    public interface LineOfSight {
        boolean clear(double ax, double ay, double az, double bx, double by, double bz);
    }

    /**
     * 권위 틱 한 번. {@code elapsedMcTicks} 는 쓰기 시작 뒤 흐른 MC 틱(이번 권위 틱의 첫 MC 틱), 두 번째 MC 틱은 +1 이다.
     * 찔린 대상은 조건이 맞지 않아도 쿨다운에 들어간다({@code rememberStabbedEntity}).
     */
    public static List<Stab> evaluate(SpearRules.Kinetic kinetic, long elapsedMcTicks, double baseAttackDamage,
            double factor, Attacker attacker, List<Target> targets, LineOfSight lineOfSight,
            Map<String, Long> stabbedAt) {
        List<Stab> stabs = new ArrayList<>();
        double along = attacker.lookX() * attacker.moveX() + attacker.lookY() * attacker.moveY()
                + attacker.lookZ() * attacker.moveZ();
        double attackerSpeed = along * 20.0;
        for (int sub = 0; sub < 2; sub++) {
            long useTicks = elapsedMcTicks + sub;
            if (useTicks < kinetic.delayTicks()) continue;
            int t = (int) (useTicks - kinetic.delayTicks());
            double forward = Math.max(0.0, along);
            double minX = attacker.x() + attacker.lookX() * SpearRules.SPEAR_MIN_REACH;
            double minY = attacker.eyeY() + attacker.lookY() * SpearRules.SPEAR_MIN_REACH;
            double minZ = attacker.z() + attacker.lookZ() * SpearRules.SPEAR_MIN_REACH;
            double maxReach = SpearRules.SPEAR_REACH + forward;
            double maxX = attacker.x() + attacker.lookX() * maxReach;
            double maxY = attacker.eyeY() + attacker.lookY() * maxReach;
            double maxZ = attacker.z() + attacker.lookZ() * maxReach;
            if (lineOfSight != null && !lineOfSight.clear(attacker.x(), attacker.eyeY(), attacker.z(),
                    minX, minY, minZ)) continue;
            for (Target target : targets) {
                double hitT = -1.0;
                double pad = SpearRules.SPEAR_HITBOX_INFLATION;
                for (double[] box : target.boxes()) {
                    double candidate = CombatSystem.segmentBoxT(minX, minY, minZ, maxX, maxY, maxZ,
                            box[0] - pad, box[1] - pad, box[2] - pad, box[3] + pad, box[4] + pad, box[5] + pad);
                    if (candidate >= 0.0 && (hitT < 0.0 || candidate < hitT)) hitT = candidate;
                }
                if (hitT < 0.0) continue;
                if (lineOfSight != null && !lineOfSight.clear(attacker.x(), attacker.eyeY(), attacker.z(),
                        minX + (maxX - minX) * hitT, minY + (maxY - minY) * hitT, minZ + (maxZ - minZ) * hitT)) {
                    continue;
                }
                Long last = stabbedAt.get(target.key());
                if (last != null && useTicks - last < SpearRules.KINETIC_CONTACT_COOLDOWN_TICKS) continue;
                stabbedAt.put(target.key(), useTicks);
                double targetSpeed = (attacker.lookX() * target.vx() + attacker.lookY() * target.vy()
                        + attacker.lookZ() * target.vz()) * 10.0;
                double relative = Math.max(0.0, attackerSpeed - targetSpeed);
                boolean dismount = SpearRules.kineticDismounts(kinetic, t, attackerSpeed, factor);
                boolean knockback = SpearRules.kineticKnocksBack(kinetic, t, attackerSpeed, factor);
                boolean damage = SpearRules.kineticDamages(kinetic, t, relative, factor);
                if (!dismount && !knockback && !damage) continue;
                stabs.add(new Stab(target.key(), useTicks,
                        SpearRules.kineticDamage(baseAttackDamage, relative, kinetic.damageMultiplier()),
                        damage, knockback, dismount));
            }
        }
        return stabs;
    }
}
