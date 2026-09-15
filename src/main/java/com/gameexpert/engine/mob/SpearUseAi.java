package com.gameexpert.engine.mob;

import com.gameexpert.engine.SpearRules;
import com.gameexpert.engine.dragon.DragonMath;
import com.gameexpert.terrain.Blocks;

/**
 * [SPEAR-MOB] 창을 든 몹의 돌진 AI — 핀 26.3 jar {@code SpearUseGoal}(좀비 계열 · 좀비화 피글린, goal 우선순위 2)과
 * 피글린 두뇌의 {@code SpearApproach} · {@code SpearAttack} · {@code SpearRetreat}(같은 단계·같은 상수)를 한 상태기계로
 * 옮겼다. 정적판 사본은 {@code StandaloneSpearUseAi.ts} 다.
 *
 * <p>단계: <b>접근</b>(표적과의 거리² 가 {@code approachDistance²}=100 을 넘는 동안 표적으로 간다) → <b>교전</b>(창을
 * 쓰기 시작하고 {@code reducedTickDelay(delay + damage 창)} goal 틱 동안 표적을 향해 돌진한다. 거리² 가
 * {@code targetInRangeRadius²}=4 미만이 되거나 길이 끝나면 표적에서 6·7(+탑승 2)−거리 떨어진 자리로 물러났다가 다시
 * 돌진한다) → <b>후퇴</b>(교전이 끝나면 창을 내리고 9·11(+2)−거리 떨어진 자리로 가며, 도착하거나
 * {@code reducedTickDelay(100)}=50 goal 틱이 지나면 끝나 다음 goal 틱에 접근부터 다시 한다).
 *
 * <p>이 권위의 몹 AI 는 권위 틱(MC 2 틱)마다 한 번 도는 goal 틱이라 jar 의 {@code reducedTickDelay} 값을 그대로 센다.
 * 경로 탐색이 없으므로 {@code PathNavigation.isDone} 은 목적지와의 수평 거리 {@link #NAV_DONE_DISTANCE} 이내로 본다.
 * 물러날 자리는 {@code LandRandomPos.getPosAway} 의 난수 소비·후보 10 개·밝기 가중을 그대로 옮긴 {@link #posAway} 다.
 */
public final class SpearUseAi {

    private SpearUseAi() {}

    /** {@code SpearUseGoal(mob, 1.0, 1.0, 10.0f, 2.0f)} · {@code SpearApproach(1.0, 10.0f)} 의 접근 거리². */
    public static final float APPROACH_DISTANCE_SQ = 10.0f * 10.0f;
    /** 같은 생성자의 표적 도달 반경². */
    public static final float TARGET_IN_RANGE_RADIUS_SQ = 2.0f * 2.0f;
    /** 돌진·재정렬 속도 배율(둘 다 1.0). */
    public static final double SPEED_MODIFIER = 1.0;
    public static final int MIN_REPOSITION_DISTANCE = 6;
    public static final int MAX_REPOSITION_DISTANCE = 7;
    public static final int MIN_COOLDOWN_DISTANCE = 9;
    public static final int MAX_COOLDOWN_DISTANCE = 11;
    /** {@code LandRandomPos.getPosAway(…, 7, …)} 의 수직 범위. */
    public static final int POS_AWAY_Y_RANGE = 7;
    /** {@code MAX_FLEEING_TIME = reducedTickDelay(100)}. */
    public static final int MAX_FLEEING_GOAL_TICKS = reducedTickDelay(100);
    /** 경로 끝(PathNavigation.isDone) 근사: 목적지와의 수평 거리(블록). */
    public static final double NAV_DONE_DISTANCE = 1.0;
    /** {@code LandRandomPos} 후보 수({@code RandomPos.generateRandomPos} 의 10 회). */
    public static final int POS_AWAY_ATTEMPTS = 10;

    /** {@code Goal.reducedTickDelay(n)} = {@code Mth.positiveCeilDiv(n, 2)}. */
    public static int reducedTickDelay(int ticks) {
        return (ticks + 1) / 2;
    }

    /** {@code getKineticWeaponUseDuration}: {@code reducedTickDelay(KineticWeapon.computeDamageUseDuration())}. */
    public static int engageGoalTicks(SpearRules.Kinetic kinetic) {
        return reducedTickDelay(kinetic.delayTicks() + kinetic.damageMaxTicks());
    }

    /** {@code Mob.chargeSpeedModifier}: 좀비 말 1.4 · 낙타 허스크 4.0, 그 밖 1. 뿌리 탈것 기준이다. */
    public static float chargeSpeedModifier(MobType rootVehicle) {
        if (rootVehicle == null) return 1.0f;
        return switch (rootVehicle) {
            case ZOMBIE_HORSE -> 1.4f;
            case CAMEL_HUSK -> 4.0f;
            default -> 1.0f;
        };
    }

    /** 몹 한 마리의 {@code SpearUseState}(교전 · 후퇴 틱, 물러날 자리, 끝남). 저장하지 않는다(바닐라 goal 도 그렇다). */
    public static final class State {
        int engageTime = -1;
        int fleeingTime = -1;
        boolean hasAway;
        double awayX;
        double awayY;
        double awayZ;
        boolean done;
        /** 창을 쓰는 중이면 쓰기 시작한 MC 틱(권위 틱 × 2). 아니면 −1. */
        long useStartMcTick = -1;

        public boolean using() { return useStartMcTick >= 0; }
        public long useStartMcTick() { return useStartMcTick; }
        public boolean done() { return done; }
        public boolean hasAway() { return hasAway; }
        public double awayX() { return awayX; }
        public double awayZ() { return awayZ; }
        public int engageTime() { return engageTime; }
        public int fleeingTime() { return fleeingTime; }
    }

    /** 물러날 자리 고르기({@link #posAway}). 없으면 null. */
    public interface AwayPicker {
        double[] pick(double minHorizontal, double maxHorizontal, int yRange, double fromX, double fromY, double fromZ);
    }

    /** goal 틱 하나의 결정: 어디로 얼마나 빨리 갈지(목적지가 없으면 제자리). */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Decision {
        boolean move;
        double toX;
        double toY;
        double toZ;
        double speedModifier;

        static final Decision STAY = new Decision(false, 0, 0, 0, 0);
    }

    /**
     * {@code SpearUseGoal.tick} 한 번. {@code mcTick} 은 이번 권위 틱의 MC 틱(권위 틱 × 2)이다. 교전 시작에서 창을 쓰기
     * 시작({@code startUsingItem})하고 교전이 끝나면 내린다({@code stopUsingItem}).
     */
    public static Decision tick(State s, SpearRules.Kinetic kinetic, long mcTick,
            double mobX, double mobY, double mobZ, double targetX, double targetY, double targetZ,
            boolean passenger, float chargeSpeedModifier, AwayPicker away) {
        double dx = targetX - mobX;
        double dy = targetY - mobY;
        double dz = targetZ - mobZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        int ridingBonus = passenger ? 2 : 0;
        if (s.engageTime < 0) {
            if (distanceSq > APPROACH_DISTANCE_SQ) {
                return new Decision(true, targetX, targetY, targetZ, chargeSpeedModifier * SPEED_MODIFIER);
            }
            s.engageTime = engageGoalTicks(kinetic);
            s.useStartMcTick = mcTick;
        }
        if (s.engageTime > 0) {
            s.engageTime--;
            if (s.engageTime == 0) {
                s.useStartMcTick = -1;
                double distance = Math.sqrt(distanceSq);
                setAway(s, away.pick(Math.max(0.0, MIN_COOLDOWN_DISTANCE + ridingBonus - distance),
                        Math.max(1.0, MAX_COOLDOWN_DISTANCE + ridingBonus - distance), POS_AWAY_Y_RANGE,
                        targetX, targetY, targetZ));
                s.fleeingTime = 1;
            }
        }
        if (s.fleeingTime > 0) {
            s.fleeingTime++;
            if (s.fleeingTime > MAX_FLEEING_GOAL_TICKS) {
                s.done = true;
                return Decision.STAY;
            }
        }
        if (s.hasAway) {
            Decision toAway = new Decision(true, s.awayX, s.awayY, s.awayZ, chargeSpeedModifier * SPEED_MODIFIER);
            if (navDone(mobX, mobZ, s.awayX, s.awayZ)) {
                if (s.fleeingTime > 0) {
                    s.done = true;
                    return toAway;
                }
                s.hasAway = false;
            }
            return toAway;
        }
        Decision charge = new Decision(true, targetX, targetY, targetZ, chargeSpeedModifier * SPEED_MODIFIER);
        if (distanceSq < TARGET_IN_RANGE_RADIUS_SQ || navDone(mobX, mobZ, targetX, targetZ)) {
            double distance = Math.sqrt(distanceSq);
            setAway(s, away.pick(MIN_REPOSITION_DISTANCE + ridingBonus - distance,
                    MAX_REPOSITION_DISTANCE + ridingBonus - distance, POS_AWAY_Y_RANGE, targetX, targetY, targetZ));
        }
        return charge;
    }

    private static void setAway(State s, double[] pos) {
        if (pos == null) {
            s.hasAway = false;
            return;
        }
        s.hasAway = true;
        s.awayX = pos[0];
        s.awayY = pos[1];
        s.awayZ = pos[2];
    }

    // ── 피글린 두뇌: SpearApproach · SpearAttack · SpearRetreat(FIGHT 활동, 두뇌는 MC 틱마다 돈다) ──

    /** {@code SpearAttack.SpearStatus}. 기억이 없으면(ABSENT) null 이다. */
    public enum SpearStatus { APPROACH, CHARGING, RETREAT }

    /** {@code SpearRetreat} 의 {@code MAX_FLEEING_TIME}(MC 틱) · {@code Behavior} 지속 상한. */
    public static final int BRAIN_MAX_FLEEING_MC_TICKS = 100;

    /** 피글린 한 마리의 창 두뇌 기억(SPEAR_STATUS · ENGAGE_TIME · CHARGE/FLEEING_POSITION · FLEEING_TIME). 저장하지 않는다. */
    public static final class BrainState {
        SpearStatus status;
        boolean approachRunning;
        boolean attackRunning;
        boolean retreatRunning;
        int engageMcTicks;
        boolean hasCharge;
        double chargeX;
        double chargeY;
        double chargeZ;
        boolean hasFlee;
        double fleeX;
        double fleeY;
        double fleeZ;
        int fleeingMcTicks;
        /** 창을 쓰는 중이면 쓰기 시작한 MC 틱. 아니면 −1. */
        long useStartMcTick = -1;
        /** 지난 {@code navigation.moveTo} 의 목적지(탐색이 멈추면 없음). */
        Decision navigation = Decision.STAY;

        public SpearStatus status() { return status; }
        public boolean using() { return useStartMcTick >= 0; }
        public long useStartMcTick() { return useStartMcTick; }
        public boolean attackRunning() { return attackRunning; }
        public boolean retreatRunning() { return retreatRunning; }
        public boolean approachRunning() { return approachRunning; }
        public int engageMcTicks() { return engageMcTicks; }
        public int fleeingMcTicks() { return fleeingMcTicks; }
    }

    /**
     * 두뇌 MC 틱 하나({@code Brain.tick}: 멈춘 행동을 목록 순서로 시작해 보고 → 도는 행동을 {@code tickOrStop}). {@code able}
     * 은 {@code ableToAttack}(공격 표적과 {@code KINETIC_WEAPON}). 결과는 이 MC 틱 뒤의 탐색 목적지다.
     */
    public static Decision tickBrain(BrainState s, SpearRules.Kinetic kinetic, long mcTick, boolean able,
            double mobX, double mobY, double mobZ, double targetX, double targetY, double targetZ,
            boolean passenger, float chargeSpeedModifier, AwayPicker away) {
        double dx = targetX - mobX;
        double dy = targetY - mobY;
        double dz = targetZ - mobZ;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        int ridingBonus = passenger ? 2 : 0;
        double speed = chargeSpeedModifier * SPEED_MODIFIER;
        if (able) {
            // startEachNonRunningBehavior: SpearApproach → SpearAttack → SpearRetreat.
            if (!s.approachRunning && s.status == null && !s.using()) {
                s.approachRunning = true;
                s.status = SpearStatus.APPROACH;
            }
            if (!s.attackRunning && s.status == SpearStatus.CHARGING && !s.using()) {
                s.attackRunning = true;
                s.engageMcTicks = kinetic.delayTicks() + kinetic.damageMaxTicks();
                s.hasCharge = false;
                s.useStartMcTick = mcTick;
            }
            if (!s.retreatRunning && s.status == SpearStatus.RETREAT && !s.using()) {
                double distance = Math.sqrt(distanceSq);
                double[] flee = away.pick(Math.max(0.0, MIN_COOLDOWN_DISTANCE + ridingBonus - distance),
                        Math.max(1.0, MAX_COOLDOWN_DISTANCE + ridingBonus - distance), POS_AWAY_Y_RANGE,
                        targetX, targetY, targetZ);
                if (flee != null) {
                    s.hasFlee = true;
                    s.fleeX = flee[0];
                    s.fleeY = flee[1];
                    s.fleeZ = flee[2];
                    s.retreatRunning = true;
                    s.fleeingMcTicks = 0;
                }
            }
        }
        // tickEachRunningBehavior(같은 순서): canStillUse 면 tick, 아니면 stop.
        if (s.approachRunning) {
            if (able && distanceSq > APPROACH_DISTANCE_SQ) {
                s.navigation = new Decision(true, targetX, targetY, targetZ, speed);
            } else {
                s.approachRunning = false;
                s.navigation = Decision.STAY;
                s.status = SpearStatus.CHARGING;
            }
        }
        if (s.attackRunning) {
            if (able && s.engageMcTicks > 0) {
                s.engageMcTicks--;
                if (s.hasCharge) {
                    s.navigation = new Decision(true, s.chargeX, s.chargeY, s.chargeZ, speed);
                    if (navDone(mobX, mobZ, s.chargeX, s.chargeZ)) s.hasCharge = false;
                } else {
                    s.navigation = new Decision(true, targetX, targetY, targetZ, speed);
                    if (distanceSq < TARGET_IN_RANGE_RADIUS_SQ || navDone(mobX, mobZ, targetX, targetZ)) {
                        double distance = Math.sqrt(distanceSq);
                        double[] charge = away.pick(MIN_REPOSITION_DISTANCE + ridingBonus - distance,
                                MAX_REPOSITION_DISTANCE + ridingBonus - distance, POS_AWAY_Y_RANGE,
                                targetX, targetY, targetZ);
                        if (charge != null) {
                            s.hasCharge = true;
                            s.chargeX = charge[0];
                            s.chargeY = charge[1];
                            s.chargeZ = charge[2];
                        }
                    }
                }
            } else {
                s.attackRunning = false;
                s.navigation = Decision.STAY;
                s.useStartMcTick = -1;
                s.hasCharge = false;
                s.engageMcTicks = 0;
                s.status = SpearStatus.RETREAT;
            }
        }
        if (s.retreatRunning) {
            if (able && s.fleeingMcTicks < BRAIN_MAX_FLEEING_MC_TICKS && s.hasFlee
                    && !navDone(mobX, mobZ, s.fleeX, s.fleeZ)) {
                s.fleeingMcTicks++;
                s.navigation = new Decision(true, s.fleeX, s.fleeY, s.fleeZ, speed);
            } else {
                s.retreatRunning = false;
                s.navigation = Decision.STAY;
                s.useStartMcTick = -1;
                s.fleeingMcTicks = 0;
                s.hasFlee = false;
                s.status = null;
            }
        }
        return s.navigation;
    }

    /** {@code PathNavigation.isDone} 근사. */
    public static boolean navDone(double x, double z, double toX, double toZ) {
        double dx = toX - x;
        double dz = toZ - z;
        return dx * dx + dz * dz <= NAV_DONE_DISTANCE * NAV_DONE_DISTANCE;
    }

    /**
     * {@code LandRandomPos.getPosAway(mob, minHorizontal, maxHorizontal, yRange, from)}: 표적에서 멀어지는 방향 ±90° 안에서
     * {@code RandomPos.generateRandomPos} 가 후보 10 개를 뽑아 걷기 값({@code Monster.getWalkTargetValue} =
     * {@code −getPathfindingCostFromLightLevels})이 가장 큰 칸의 바닥 중심을 준다. 후보 하나는
     * {@code generateRandomDirectionWithinRadians}(nextFloat · nextDouble · 범위 안이면 nextInt) → 몹 위치 더하기 → 세계 높이
     * 밖 · 발밑이 단단하지 않음(isStableDestination)이면 버림 → 단단한 칸이면 위로 올린다(movePosUpOutOfSolid).
     */
    public static double[] posAway(MobRandom random, MobWorldView world, double mobX, double mobY, double mobZ,
            double minHorizontal, double maxHorizontal, int yRange, double fromX, double fromY, double fromZ) {
        double vx = mobX - fromX;
        double vy = mobY - fromY;
        double vz = mobZ - fromZ;
        if (Math.sqrt(vx * vx + vy * vy + vz * vz) == 0.0) {
            vx = random.nextDouble() - 0.5;
            vz = random.nextDouble() - 0.5;
        }
        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        boolean found = false;
        double bestValue = Double.NEGATIVE_INFINITY;
        // RandomPos.generateRandomPos: 후보마다 walk 값이 지금까지의 최댓값보다 커야 바꾼다(같으면 먼저 뽑힌 칸).
        for (int attempt = 0; attempt < POS_AWAY_ATTEMPTS; attempt++) {
            int[] direction = randomDirectionWithinRadians(random, minHorizontal, maxHorizontal, yRange, 0,
                    vx, vz, 1.5707963705062866);
            if (direction == null) continue;
            int x = floor(direction[0] + mobX);
            int y = floor(direction[1] + mobY);
            int z = floor(direction[2] + mobZ);
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) continue;
            if (!world.isSolid(world.getBlock(x, y - 1, z))) continue;
            if (world.isSolid(world.getBlock(x, y, z))) {
                y++;
                while (y <= Blocks.MAX_Y && world.isSolid(world.getBlock(x, y, z))) y++;
            }
            double value = walkTargetValue(world.lightLevel(x, y, z));
            if (value > bestValue) {
                bestValue = value;
                bestX = x;
                bestY = y;
                bestZ = z;
                found = true;
            }
        }
        return found ? new double[] {bestX + 0.5, bestY, bestZ + 0.5} : null;
    }

    /**
     * {@code RandomPos.generateRandomDirectionWithinRadians(random, min, max, yRange, yOffset, x, z, maxAngle)}: 칸 좌표
     * (정수로 내림한 x · y · z). 범위를 벗어나면 null(이때 수직 난수는 소비하지 않는다).
     */
    static int[] randomDirectionWithinRadians(MobRandom random, double minHorizontal, double maxHorizontal,
            int yRange, int yOffset, double x, double z, double maxAngle) {
        double angle = DragonMath.atan2(z, x) - 1.5707963705062866;
        angle += (double) (2.0f * random.nextFloat() - 1.0f) * maxAngle;
        double distance = DragonMath.lerp(Math.sqrt(random.nextDouble()), minHorizontal, maxHorizontal)
                * (double) 1.4142135f;
        double dx = -distance * Math.sin(angle);
        double dz = distance * Math.cos(angle);
        if (Math.abs(dx) > maxHorizontal || Math.abs(dz) > maxHorizontal) return null;
        int dy = random.nextInt(2 * yRange + 1) - yRange + yOffset;
        return new int[] {floor(dx), dy, floor(dz)};
    }

    /**
     * {@code Monster.getWalkTargetValue} = {@code −getPathfindingCostFromLightLevels} = {@code 0.5 − 밝기}(어두운 칸을
     * 고른다). 오버월드 밝기는 {@code f = light/15, f / (4 − 3f)}(주변광 0)다.
     */
    static float walkTargetValue(int lightLevel) {
        float f = lightLevel / 15.0f;
        float brightness = f / (4.0f - 3.0f * f);
        return -(brightness - 0.5f);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
