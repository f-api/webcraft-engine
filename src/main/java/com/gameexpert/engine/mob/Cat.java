package com.gameexpert.engine.mob;

/**
 * 고양이(바닐라 실존 종, stableId 86). 수치 근거는 {@link MobType#CAT} 의 인라인 인용이다
 * (MC Java 1.21.4 {@code EntityType.CAT sized(0.6F, 0.7F)} · {@code Cat.createAttributes()}
 * MAX_HEALTH 10 · MOVEMENT_SPEED 0.3 · ATTACK_DAMAGE 3).
 *
 * <p>길들이기·앉기·소유자 따라가기는 26.3-snapshot-7의 종별 조건을 사용한다.
 *
 * <p><b>크리퍼 회피는 이미 성립한다</b>: 바닐라 {@code Creeper#registerGoals} 의
 * {@code new AvoidEntityGoal<>(this, Cat.class, 6.0F, 1.0, 1.2)} 를 옮긴 자리가
 * {@code Creeper} 쪽이므로, 이 종이 등록되는 것만으로 6블록 회피가 켜진다 — 회피 술어는
 * 종 등록 여부를 보고 서기 때문이다.
 */
public final class Cat extends CompanionMob {

    /** 바닐라 {@code Creeper} 의 고양이 회피 반경 6.0F. */
    public static final double CREEPER_AVOID_RANGE = 6.0;

    public Cat(long id, double x, double y, double z) {
        this(id, x, y, z, MobType.CAT.deterministicVariant(0, id, x, z));
    }

    Cat(long id, double x, double y, double z, String variant) {
        super(MobType.CAT, id, x, y, z, variant);
    }
    private int bedSearchCooldown;
    private int[] bedTarget;

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (!mayLieOnBed() || isDead()) {
            clearBedTarget();
            return super.tick(world, rng);
        }
        if (bedTarget != null && !bedAt(world, bedTarget[0], bedTarget[1], bedTarget[2])) clearBedTarget();
        if (bedSearchCooldown > 0) bedSearchCooldown--;
        if (bedTarget == null && bedSearchCooldown == 0) {
            bedSearchCooldown = 20; // CatLieOnBlockGoal.nextStartTick:40MC ticks.
            double nearest = Double.POSITIVE_INFINITY;
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            for (int dy = -2; dy <= 6; dy++) {
                for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                    if (dx * dx + dz * dz > 64 || !bedAt(world, bx + dx, by + dy, bz + dz)) continue;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearest) { nearest = distance; bedTarget = new int[] { bx + dx, by + dy, bz + dz }; }
                }
            }
        }
        if (bedTarget == null) return super.tick(world, rng);
        double tx = bedTarget[0] + 0.5, ty = bedTarget[1] + 0.5625, tz = bedTarget[2] + 0.5;
        double dx = tx - x, dz = tz - z;
        if (dx * dx + dz * dz < 0.25 && Math.abs(y - ty) < 0.5) {
            synchronizeVisualAction("lie", "active", 2);
            state = MobState.IDLE;
            MobPhysics.tickMove(this, world, 0, 0, 0, MoveMode.WALK);
        } else {
            if ("lie".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
            faceToward(tx, tz);
            state = MobState.WANDER;
            double[] movement = towardHoriz(tx, tz, type.baseSpeed() * 1.1);
            MobPhysics.tickMove(this, world, movement[0], 0, movement[1], MoveMode.WALK);
        }
        return java.util.List.of();
    }

    private static boolean bedAt(MobWorldView world, int x, int y, int z) {
        return com.gameexpert.terrain.Blocks.isBed(world.getBlock(x, y, z))
                && world.getBlock(x, y + 1, z) == com.gameexpert.terrain.Blocks.AIR;
    }

    private void clearBedTarget() {
        bedTarget = null;
        if ("lie".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
    }

}
