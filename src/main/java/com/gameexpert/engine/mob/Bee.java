package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.terrain.Blocks;

/** 꿀벌: 비행·꽃 수분·귀환·피격 분노·일회성 침 공격을 처리한다. */
public final class Bee extends Mob {
    public static final int STING_DAMAGE = 2;
    private static final double DETECT_RANGE = 16.0;
    private static final double STING_RANGE = 1.4;
    private static final int ANGER_TICKS = 250; // 25 seconds at WebCraft's 10 TPS.
    private static final int DEATH_AFTER_STING_TICKS = 600; // MC bees die about one minute after stinging.
    private static final int FLOWER_SEARCH_INTERVAL_TICKS = 20;
    private static final int POLLINATION_TICKS = 300; // At least 30 seconds over a flower.
    private static final int FLOWER_RADIUS = 10;
    private static final int FLOWER_VERTICAL_RADIUS = 4;
    private static final int HIVE_SEARCH_RADIUS = 20;
    private static final int HIVE_VERTICAL_RADIUS = 8;
    static final int HIVE_MIN_STAY_MC_TICKS = 600;
    private static final double ARRIVAL_RANGE = 1.1;
    private boolean hasBreedTarget;
    private double breedTargetX, breedTargetY, breedTargetZ;
    private double wanderDy;
    private boolean angry;
    private int angerTicks;
    private boolean hasStung;
    private int deathAfterStingTicks;
    private double homeX, homeY, homeZ;
    private boolean hasNectar;
    private boolean hasFlowerTarget;
    private int flowerX, flowerY, flowerZ;
    private int flowerSearchTicks;
    private int pollinationTicks;
    private int hiveTicks;

    public Bee(long id, double x, double y, double z) {
        super(id, MobType.BEE, x, y, z);
        homeX = x;
        homeY = y;
        homeZ = z;
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        angry = true;
        angerTicks = ANGER_TICKS;
        forceTarget(attackerNickname);
    }

    @Override public String movementMedium() { return "fly"; }

    @Override public int visualFlags() {
        int flags = 0;
        if (hasNectar) flags |= Mob.VISUAL_BEE_NECTAR;
        if (hasStung) flags |= Mob.VISUAL_BEE_STUNG;
        if (angry) flags |= Mob.VISUAL_BEE_ANGRY;
        return flags;
    }

    @Override public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (hiveTicks > 0) {
            // The authority runs at 10 TPS. Advance the pinned 20-TPS BeeData post-increment
            // contract twice: a seed at ticksInHive=598 first becomes releasable on its fourth
            // Minecraft tick, while a seed at zero first becomes releasable on tick 602.
            for (int mcTick = 0; mcTick < 2 && hiveTicks > 0; mcTick++) {
                int ticksInHive = hiveTicks - 1;
                boolean releaseDue = ticksInHive > HIVE_MIN_STAY_MC_TICKS;
                if (hiveTicks < Integer.MAX_VALUE) hiveTicks++;
                if (releaseDue && canExitHive(world)) leaveHive(null);
            }
            if (hiveTicks > 0) return List.of();
        }
        if (hasStung && --deathAfterStingTicks <= 0) {
            kill();
            return List.of();
        }
        if (angry && --angerTicks <= 0) {
            angry = false;
        }

        PlayerSnapshot target = hasStung ? null : trackedTarget(world, DETECT_RANGE, angry);
        if (target != null) {
            double dx = target.x() - x;
            double dy = target.y() + 0.9 - y;
            double dz = target.z() - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            faceToward(target.x(), target.z());
            if (distance <= STING_RANGE && canSeeTargetNow(world, target)) {
                state = MobState.ATTACK;
                hasStung = true;
                deathAfterStingTicks = DEATH_AFTER_STING_TICKS;
                angry = false;
                MobEvent sting = new MobEvent.StingPlayer(
                        target.nickname(), contactDamage(world, STING_DAMAGE), x, z);
                return List.of(sting);
            }
            if (distance > 1e-9) {
                double speed = type.baseSpeed();
                MobPhysics.tickMove(this, world, dx / distance * speed, dy / distance * speed,
                        dz / distance * speed, MoveMode.FLY);
            }
            state = MobState.CHASE;
            return List.of();
        }

        if (hasBreedTarget && isInLoveMode()) {
            double dx = breedTargetX - x;
            double dy = breedTargetY - y;
            double dz = breedTargetZ - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > 1e-9) {
                double speed = type.baseSpeed();
                MobPhysics.tickMove(this, world,
                        dx / distance * speed,
                        dy / distance * speed,
                        dz / distance * speed,
                        MoveMode.FLY);
                yaw = Math.atan2(dz, dx);
                state = MobState.WANDER;
            }
        } else if (hasNectar) {
            findHiveHome(world);
            flyToward(world, homeX, homeY, homeZ);
            if (distanceSquared(homeX, homeY, homeZ) <= ARRIVAL_RANGE * ARRIVAL_RANGE) {
                int hx = (int) Math.floor(homeX);
                int hy = (int) Math.floor(homeY);
                int hz = (int) Math.floor(homeZ);
                if (isHive(world.getBlock(hx, hy, hz))) {
                    return List.of(new MobEvent.BeeHiveEntry(hx, hy, hz));
                }
            }
        } else if (findOrPollinateFlower(world)) {
            flyToward(world, flowerX + 0.5, flowerY + 0.8, flowerZ + 0.5);
            if (distanceSquared(flowerX + 0.5, flowerY + 0.8, flowerZ + 0.5)
                    <= ARRIVAL_RANGE * ARRIVAL_RANGE && isFlower(world.getBlock(flowerX, flowerY, flowerZ))) {
                pollinationTicks++;
                if (pollinationTicks >= POLLINATION_TICKS) {
                    hasNectar = true;
                    hasFlowerTarget = false;
                }
            }
        } else {
            boolean choosingDirection = wanderTimer <= 0;
            double[] move = wander(rng, type.baseSpeed());
            if (choosingDirection) {
                // Cancel the flight model's light gravity, then add a small climb/dive intent.
                wanderDy = MobPhysics.GRAVITY * MobPhysics.FLY_GRAVITY_SCALE
                        + (rng.nextDouble() - 0.5) * type.baseSpeed() * 0.2;
            }
            MobPhysics.tickMove(this, world, move[0], wanderDy, move[1], MoveMode.FLY);
            state = (move[0] != 0.0 || move[1] != 0.0) ? MobState.WANDER : MobState.IDLE;
        }
        hasBreedTarget = false;
        return List.of();
    }

    private boolean canExitHive(MobWorldView world) {
        int hiveX = (int) Math.floor(homeX);
        int hiveY = (int) Math.floor(homeY);
        int hiveZ = (int) Math.floor(homeZ);
        if (com.gameexpert.engine.WorldClock.isNight(world.worldTime())
                || world.isRainingAt(hiveX, hiveY, hiveZ)) return false;
        int block = world.getBlock(hiveX, hiveY, hiveZ) & 0xffff;
        if (block != Blocks.BEE_NEST && block != Blocks.BEEHIVE) return false;
        int facing = world.blockState(hiveX, hiveY, hiveZ, block) & 3;
        int dx = facing == 1 ? 1 : facing == 3 ? -1 : 0;
        int dz = facing == 0 ? -1 : facing == 2 ? 1 : 0;
        return !world.isSolid(world.getBlock(hiveX + dx, hiveY, hiveZ + dz));
    }

    private void findHiveHome(MobWorldView world) {
        int currentX = (int) Math.floor(homeX);
        int currentY = (int) Math.floor(homeY);
        int currentZ = (int) Math.floor(homeZ);
        if (isHive(world.getBlock(currentX, currentY, currentZ))) return;
        int originX = (int) Math.floor(x);
        int originY = (int) Math.floor(y);
        int originZ = (int) Math.floor(z);
        int[] indexed = world.nearestBeeHive(originX, originY, originZ,
                HIVE_SEARCH_RADIUS, HIVE_VERTICAL_RADIUS);
        if (indexed != null) {
            if (indexed.length == 3) {
                homeX = indexed[0] + 0.5;
                homeY = indexed[1] + 0.5;
                homeZ = indexed[2] + 0.5;
            }
            return;
        }
        double nearest = Double.POSITIVE_INFINITY;
        for (int dy = -HIVE_VERTICAL_RADIUS; dy <= HIVE_VERTICAL_RADIUS; dy++) {
            for (int dx = -HIVE_SEARCH_RADIUS; dx <= HIVE_SEARCH_RADIUS; dx++) {
                for (int dz = -HIVE_SEARCH_RADIUS; dz <= HIVE_SEARCH_RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz >= nearest) continue;
                    int candidateX = originX + dx;
                    int candidateY = originY + dy;
                    int candidateZ = originZ + dz;
                    if (!isHive(world.getBlock(candidateX, candidateY, candidateZ))) continue;
                    nearest = dx * dx + dy * dy + dz * dz;
                    homeX = candidateX + 0.5;
                    homeY = candidateY + 0.5;
                    homeZ = candidateZ + 0.5;
                }
            }
        }
    }

    private boolean findOrPollinateFlower(MobWorldView world) {
        if (hasFlowerTarget && isFlower(world.getBlock(flowerX, flowerY, flowerZ))) return true;
        hasFlowerTarget = false;
        pollinationTicks = 0;
        if (flowerSearchTicks-- > 0) return false;
        flowerSearchTicks = FLOWER_SEARCH_INTERVAL_TICKS;

        int originX = (int) Math.floor(x);
        int originY = (int) Math.floor(y);
        int originZ = (int) Math.floor(z);
        double nearest = Double.POSITIVE_INFINITY;
        for (int dy = -FLOWER_VERTICAL_RADIUS; dy <= FLOWER_VERTICAL_RADIUS; dy++) {
            for (int dx = -FLOWER_RADIUS; dx <= FLOWER_RADIUS; dx++) {
                for (int dz = -FLOWER_RADIUS; dz <= FLOWER_RADIUS; dz++) {
                    int candidateX = originX + dx;
                    int candidateY = originY + dy;
                    int candidateZ = originZ + dz;
                    if (!isFlower(world.getBlock(candidateX, candidateY, candidateZ))) continue;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance >= nearest) continue;
                    nearest = distance;
                    flowerX = candidateX;
                    flowerY = candidateY;
                    flowerZ = candidateZ;
                    hasFlowerTarget = true;
                }
            }
        }
        return hasFlowerTarget;
    }

    private void flyToward(MobWorldView world, double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 1e-9) {
            state = MobState.IDLE;
            return;
        }
        double speed = type.baseSpeed();
        MobPhysics.tickMove(this, world, dx / distance * speed, dy / distance * speed,
                dz / distance * speed, MoveMode.FLY);
        faceToward(targetX, targetZ);
        state = MobState.WANDER;
    }

    private double distanceSquared(double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isFlower(short block) {
        int id = block & 0xFFFF;
        return id == Blocks.FLOWER_RED || id == Blocks.FLOWER_YELLOW
                || id == Blocks.FLOWERING_AZALEA || id == Blocks.SPORE_BLOSSOM
                || id == Blocks.CACTUS_FLOWER;
    }

    private static boolean isHive(short block) {
        int id = block & 0xffff;
        return id == Blocks.BEE_NEST || id == Blocks.BEEHIVE;
    }

    boolean confirmHiveEntry(int hiveX, int hiveY, int hiveZ) {
        if (!hasNectar || hiveTicks > 0
                || (int) Math.floor(homeX) != hiveX
                || (int) Math.floor(homeY) != hiveY
                || (int) Math.floor(homeZ) != hiveZ) return false;
        hasNectar = false;
        hasFlowerTarget = false;
        pollinationTicks = 0;
        flowerSearchTicks = FLOWER_SEARCH_INTERVAL_TICKS;
        hiveTicks = 1;
        return true;
    }

    void rejectHiveEntry() {
        if (hiveTicks == 0) flowerSearchTicks = FLOWER_SEARCH_INTERVAL_TICKS;
    }

    void leaveHive(String angerTarget) {
        if (hiveTicks <= 0) return;
        hiveTicks = 0;
        x = homeX;
        y = homeY;
        z = homeZ;
        if (angerTarget != null && !angerTarget.isBlank() && !hasStung) {
            angry = true;
            angerTicks = ANGER_TICKS;
            forceTarget(angerTarget);
        }
    }

    public boolean inHive() { return hiveTicks > 0; }

    int hiveTicks() { return hiveTicks; }

    @Override
    void seekBreedingPartner(Mob partner) {
        hasBreedTarget = true;
        breedTargetX = partner.x;
        breedTargetY = partner.y;
        breedTargetZ = partner.z;
    }

    boolean hasStung() {
        return hasStung;
    }

    int deathAfterStingTicks() {
        return deathAfterStingTicks;
    }

    boolean hasNectar() {
        return hasNectar;
    }

    double homeX() {
        return homeX;
    }

    double homeY() {
        return homeY;
    }

    double homeZ() {
        return homeZ;
    }

    void restorePersistentBeeState(boolean restoredHasStung, int restoredDeathTicks,
            boolean restoredHasNectar, double restoredHomeX, double restoredHomeY,
            double restoredHomeZ, int restoredHiveTicks) {
        if (restoredHasStung
                ? restoredDeathTicks <= 0 || restoredDeathTicks > DEATH_AFTER_STING_TICKS
                : restoredDeathTicks != 0) {
            throw new IllegalStateException("invalid persisted Bee sting countdown");
        }
        if (!Double.isFinite(restoredHomeX) || !Double.isFinite(restoredHomeY)
                || !Double.isFinite(restoredHomeZ)) {
            throw new IllegalStateException("invalid persisted Bee home");
        }
        if (restoredHiveTicks < 0
                || restoredHiveTicks > 0 && restoredHasNectar) {
            throw new IllegalStateException("invalid persisted Bee hive stay");
        }
        hasStung = restoredHasStung;
        deathAfterStingTicks = restoredDeathTicks;
        hasNectar = restoredHasNectar;
        homeX = restoredHomeX;
        homeY = restoredHomeY;
        homeZ = restoredHomeZ;
        hiveTicks = restoredHiveTicks;
    }
}
