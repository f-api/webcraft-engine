package com.gameexpert.engine.mob;

/** Terrestrial species identities registered after the original mob roster. */
abstract class VariantWildlifeMob extends AnimalMob {
    VariantWildlifeMob(MobType type, long id, double x, double y, double z, String variant) {
        super(type, id, x, y, z, variant);
    }
}

final class Fox extends VariantWildlifeMob {
    private int crouchTicks;
    private int pounceTicks;
    private int biteCooldown;
    private double pounceX;
    private double pounceZ;

    Fox(long id, double x, double y, double z, String variant) {
        super(MobType.FOX, id, x, y, z, variant);
    }

    @Override public void commitAcceptedMeleeAttack() {
        biteCooldown = 10;
        super.commitAcceptedMeleeAttack();
    }

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (biteCooldown > 0) biteCooldown--;
        Mob prey = preparedSocialTarget();
        if (fleeTimer > 0 || !MobRelationshipPolicy.mayDirectlyTarget(this, prey)
                || !MobRelationshipPolicy.inTargetRange(this, prey)) {
            crouchTicks = 0; pounceTicks = 0;
            if ("crouch".equals(actionKind()) || "pounce".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
            return super.tick(world, rng);
        }
        faceToward(prey.x, prey.z);
        double distance = MobRelationshipPolicy.distanceSquared(this, prey);
        boolean visible = MobRelationshipPolicy.hasLineOfSight(world, this, prey);
        if (pounceTicks > 0) {
            pounceTicks--;
            synchronizeVisualAction("pounce", "active", 2);
            state = MobState.CHASE;
            MobPhysics.tickMove(this, world, pounceX, 0, pounceZ, MoveMode.WALK);
            pounceX *= 0.91; pounceZ *= 0.91;
            if (onGround || pounceTicks == 0) { pounceTicks = 0; crouchTicks = 0; }
        } else if (distance <= 36 && visible && onGround) {
            state = MobState.IDLE;
            synchronizeVisualAction("crouch", "active", 2);
            if (++crouchTicks >= 13) { // crouchAmount += 0.2, capped at 5:25 MC ticks.
                double length = Math.sqrt(distance);
                pounceX = (prey.x - x) / Math.max(length, 1e-9) * 1.6;
                pounceZ = (prey.z - z) / Math.max(length, 1e-9) * 1.6;
                vy = 1.8; onGround = false; pounceTicks = 20;
                synchronizeVisualAction("pounce", "active", 2);
                MobPhysics.tickMove(this, world, pounceX, 0, pounceZ, MoveMode.WALK);
            } else MobPhysics.tickMove(this, world, 0, 0, 0, MoveMode.WALK);
        } else {
            crouchTicks = 0;
            if ("crouch".equals(actionKind()) || "pounce".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
            state = MobState.CHASE;
            double[] movement = towardHoriz(prey.x, prey.z, type.baseSpeed() * 1.5);
            MobPhysics.tickMove(this, world, movement[0], 0, movement[1], MoveMode.WALK);
        }
        if (biteCooldown == 0 && MobRelationshipPolicy.inAttackReach(this, prey)
                && MobRelationshipPolicy.hasLineOfSight(world, this, prey)) {
            return java.util.List.of(MobEvent.AttackMob.direct(prey.id, weakenedDamage(2), null));
        }
        return java.util.List.of();
    }
}

final class Frog extends VariantWildlifeMob {
    private boolean hasConversionTarget;
    private int colonyX;
    private int colonyY;
    private int colonyZ;
    private int hostX;
    private int hostY;
    private int hostZ;
    private int conversionSearchCooldown;

    Frog(long id, double x, double y, double z, String variant) {
        super(MobType.FROG, id, x, y, z, variant);
    }

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (conversionSearchCooldown > 0) conversionSearchCooldown--;
        return super.tick(world, rng);
    }

    @Override protected double[] scheduledWalk(MobWorldView world) {
        if (!hasConversionTarget) return null;
        if (FrogPoisonConversionRules.reached(x, y, z, hostX, hostY, hostZ)) {
            return new double[] {0.0, 0.0};
        }
        synchronizeVisualAction("work", "active", 2);
        return towardHoriz(hostX + 0.5, hostZ + 0.5, type.baseSpeed());
    }

    boolean shouldFindConversionTarget() {
        return !hasConversionTarget && conversionSearchCooldown == 0;
    }

    void setConversionTarget(int rx, int ry, int rz, int bx, int by, int bz) {
        hasConversionTarget = true;
        colonyX = rx; colonyY = ry; colonyZ = rz;
        hostX = bx; hostY = by; hostZ = bz;
    }

    void clearConversionTarget() {
        hasConversionTarget = false;
        conversionSearchCooldown = 10;
    }

    boolean reachedConversionTarget() {
        return hasConversionTarget
                && FrogPoisonConversionRules.reached(x, y, z, hostX, hostY, hostZ);
    }

    int colonyX() { return colonyX; }
    int colonyY() { return colonyY; }
    int colonyZ() { return colonyZ; }
    int conversionHostX() { return hostX; }
    int conversionHostY() { return hostY; }
    int conversionHostZ() { return hostZ; }
}

final class Goat extends VariantWildlifeMob {
    private final GoatLongJump longJump = new GoatLongJump();
    private int hornMask = 3;
    private GoatRamRules.Phase ramPhase = GoatRamRules.Phase.IDLE;
    /** -1 means the spawn/recovery draw has not yet been consumed. */
    private int ramCooldownMcTicks = -1;
    private String ramTargetNickname;
    private double ramTargetX;
    private double ramTargetZ;
    private double ramRunUpX;
    private double ramRunUpZ;
    private double ramDirectionX;
    private double ramDirectionZ;
    private int ramPrepareMcTicks;
    private double ramDistance;
    private long ramSequence;
    private int pendingImpactBlockId;
    private int pendingImpactX;
    private int pendingImpactY;
    private int pendingImpactZ;
    private boolean pendingPreferLeft;

    Goat(long id, double x, double y, double z) {
        super(MobType.GOAT, id, x, y, z, null);
    }

    int hornMask() { return hornMask; }

    /** Species-scoped low bits: bit0 left horn, bit1 right horn. */
    @Override public int visualFlags() { return hornMask & 0x3; }

    GoatRamRules.Phase ramPhase() { return ramPhase; }
    int ramCooldownMcTicks() { return ramCooldownMcTicks; }
    String ramTargetNickname() { return ramTargetNickname; }
    double ramTargetX() { return ramTargetX; }
    double ramTargetZ() { return ramTargetZ; }
    double ramRunUpX() { return ramRunUpX; }
    double ramRunUpZ() { return ramRunUpZ; }
    double ramDirectionX() { return ramDirectionX; }
    double ramDirectionZ() { return ramDirectionZ; }
    int ramPrepareMcTicks() { return ramPrepareMcTicks; }
    double ramDistance() { return ramDistance; }
    long ramSequence() { return ramSequence; }

    record RamImpact(long sequence, int blockId, int x, int y, int z, boolean preferLeft) { }

    RamImpact pendingRamImpact() {
        return ramPhase == GoatRamRules.Phase.IMPACT_PENDING
                ? new RamImpact(ramSequence, pendingImpactBlockId,
                        pendingImpactX, pendingImpactY, pendingImpactZ, pendingPreferLeft)
                : null;
    }

    boolean consumeRamImpact(long sequence) {
        if (ramPhase != GoatRamRules.Phase.IMPACT_PENDING || ramSequence != sequence) return false;
        clearRamTarget();
        ramPhase = GoatRamRules.Phase.IDLE;
        pendingImpactBlockId = 0;
        setPersistenceRequired(true);
        return true;
    }

    short dropHornAfterCommittedRam(boolean preferLeft) {
        if (isDead() || removed || isBaby() || hornMask == 0) return 0;
        int preferred = preferLeft ? 1 : 2;
        int chosen = (hornMask & preferred) != 0 ? preferred : (preferred == 1 ? 2 : 1);
        if ((hornMask & chosen) == 0) return 0;
        hornMask &= ~chosen;
        setPersistenceRequired(true);
        return com.gameexpert.engine.inventory.PlayerInventory.GOAT_HORN;
    }

    void restoreHornMask(int restoredMask) {
        if (restoredMask < 0 || restoredMask > 3) {
            throw new IllegalArgumentException("invalid Goat horn mask " + restoredMask);
        }
        hornMask = restoredMask;
    }

    void restoreRamState(String phaseName, int cooldownMcTicks, String targetNickname,
            double targetX, double targetZ, double runUpX, double runUpZ,
            double directionX, double directionZ, int prepareMcTicks, double distance,
            long sequence, int pendingBlockId, int pendingX, int pendingY, int pendingZ,
            boolean preferLeft) {
        GoatRamRules.Phase restored;
        try {
            restored = phaseName == null ? GoatRamRules.Phase.IDLE
                    : GoatRamRules.Phase.valueOf(phaseName);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid Goat ram phase " + phaseName, invalid);
        }
        if (cooldownMcTicks < -1 || cooldownMcTicks > GoatRamRules.MAX_COOLDOWN_MC_TICKS
                || prepareMcTicks < 0 || prepareMcTicks > GoatRamRules.PREPARE_MC_TICKS
                || distance < 0 || sequence < 0
                || restored == GoatRamRules.Phase.IMPACT_PENDING && pendingBlockId <= 0) {
            throw new IllegalArgumentException("invalid persisted Goat ram state");
        }
        ramPhase = restored;
        ramCooldownMcTicks = cooldownMcTicks;
        ramTargetNickname = targetNickname;
        ramTargetX = targetX;
        ramTargetZ = targetZ;
        ramRunUpX = runUpX;
        ramRunUpZ = runUpZ;
        ramDirectionX = directionX;
        ramDirectionZ = directionZ;
        ramPrepareMcTicks = prepareMcTicks;
        ramDistance = distance;
        ramSequence = sequence;
        pendingImpactBlockId = pendingBlockId;
        pendingImpactX = pendingX;
        pendingImpactY = pendingY;
        pendingImpactZ = pendingZ;
        pendingPreferLeft = preferLeft;
    }

    private void clearRamTarget() {
        ramTargetNickname = null;
        ramPrepareMcTicks = 0;
        ramDistance = 0;
        ramDirectionX = 0;
        ramDirectionZ = 0;
    }

    private void cancelRam(MobRandom rng) {
        clearRamTarget();
        ramPhase = GoatRamRules.Phase.IDLE;
        ramCooldownMcTicks = GoatRamRules.sampleCooldown(rng);
        setPersistenceRequired(true);
    }

    boolean finishEntityRam(MobRandom rng) {
        if (ramPhase != GoatRamRules.Phase.CHARGING) return false;
        cancelRam(rng);
        return true;
    }

    private PlayerSnapshot ramTarget(MobWorldView world) {
        if (ramTargetNickname == null) return null;
        for (PlayerSnapshot player : world.players()) {
            if (player.alive() && ramTargetNickname.equals(player.nickname())) return player;
        }
        return null;
    }

    private PlayerSnapshot nearestRamTarget(MobWorldView world) {
        PlayerSnapshot nearest = null;
        double best = GoatRamRules.TARGET_RANGE * GoatRamRules.TARGET_RANGE;
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive()) continue;
            double dx = player.x() - x, dz = player.z() - z;
            double distance = dx * dx + dz * dz;
            if (distance < GoatRamRules.MIN_RUN_UP_BLOCKS * GoatRamRules.MIN_RUN_UP_BLOCKS
                    || distance > best
                    || !world.hasLineOfSight(x, y + height() * 0.75,
                            z, player.x(), player.y() + 0.9, player.z())) continue;
            best = distance;
            nearest = player;
        }
        return nearest;
    }

    private boolean beginPreparing(MobWorldView world, PlayerSnapshot target) {
        double dx = x - target.x(), dz = z - target.z();
        double length = Math.hypot(dx, dz);
        if (length < 1e-9) return false;
        double ux = dx / length, uz = dz / length;
        for (int distance = GoatRamRules.MAX_RUN_UP_BLOCKS;
                distance >= GoatRamRules.MIN_RUN_UP_BLOCKS; distance--) {
            double candidateX = target.x() + ux * distance;
            double candidateZ = target.z() + uz * distance;
            int bx = (int) Math.floor(candidateX), by = (int) Math.floor(y);
            int bz = (int) Math.floor(candidateZ);
            if (!world.isSolid(world.getBlock(bx, by - 1, bz))
                    || world.isSolid(world.getBlock(bx, by, bz))
                    || world.isSolid(world.getBlock(bx, by + 1, bz))) continue;
            ramTargetNickname = target.nickname();
            ramTargetX = target.x();
            ramTargetZ = target.z();
            ramRunUpX = candidateX;
            ramRunUpZ = candidateZ;
            ramPrepareMcTicks = GoatRamRules.PREPARE_MC_TICKS;
            ramPhase = GoatRamRules.Phase.PREPARING;
            setPersistenceRequired(true);
            return true;
        }
        return false;
    }

    private void beginCharge() {
        double dx = ramTargetX - x, dz = ramTargetZ - z;
        double length = Math.hypot(dx, dz);
        if (length < 1e-9) {
            ramPhase = GoatRamRules.Phase.IDLE;
            return;
        }
        ramDirectionX = dx / length;
        ramDirectionZ = dz / length;
        ramDistance = 0;
        ramPhase = GoatRamRules.Phase.CHARGING;
        setPersistenceRequired(true);
    }

    private int[] solidImpactAhead(MobWorldView world) {
        double reach = width() * 0.5 + 0.08;
        int bx = (int) Math.floor(x + ramDirectionX * reach);
        int bz = (int) Math.floor(z + ramDirectionZ * reach);
        int baseY = (int) Math.floor(y);
        for (int by = baseY; by <= baseY + 1; by++) {
            short block = world.getBlock(bx, by, bz);
            if (block > 0 && world.isSolid(block)) return new int[] {block & 0xffff, bx, by, bz};
        }
        return null;
    }

    @Override
    public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return java.util.List.of();
        boolean jumpOwnsTick = longJump.tick(this, world, rng,
                priorityAnimalMovement() || isRidingBoat() || isMobPassenger() || isRidingPlacedVehicle()
                    || world.waterAt((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)),
                ramPhase == GoatRamRules.Phase.IDLE);
        if (jumpOwnsTick) {
            state = longJump.airborne() ? MobState.WANDER : MobState.IDLE;
            synchronizeVisualAction(longJump.airborne() ? "pounce" : "crouch", "active", 4);
            clearAnimalMovementTargets();
            return java.util.List.of();
        }
        if ("pounce".equals(actionKind()) || "crouch".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
        if (priorityAnimalMovement()) return super.tick(world, rng);
        if (ramPhase == GoatRamRules.Phase.IMPACT_PENDING) {
            state = MobState.IDLE;
            return java.util.List.of();
        }
        if (ramCooldownMcTicks < 0) {
            ramCooldownMcTicks = GoatRamRules.sampleCooldown(rng);
            setPersistenceRequired(true);
        }
        if (ramPhase == GoatRamRules.Phase.IDLE) {
            ramCooldownMcTicks = GoatRamRules.advanceMcCursor(ramCooldownMcTicks);
            if (ramCooldownMcTicks > 0) return super.tick(world, rng);
            PlayerSnapshot target = nearestRamTarget(world);
            if (target == null || !beginPreparing(world, target)) return super.tick(world, rng);
        }
        if (ramPhase == GoatRamRules.Phase.PREPARING) {
            PlayerSnapshot target = ramTarget(world);
            if (target == null || Math.hypot(target.x() - ramTargetX, target.z() - ramTargetZ)
                    > GoatRamRules.TARGET_MOVE_TOLERANCE) {
                cancelRam(rng);
                return super.tick(world, rng);
            }
            double toRunUpX = ramRunUpX - x, toRunUpZ = ramRunUpZ - z;
            double distance = Math.hypot(toRunUpX, toRunUpZ);
            if (distance > GoatRamRules.RUN_UP_REACHED_DISTANCE) {
                double speed = type.baseSpeed() * GoatRamRules.PREPARE_SPEED_MODIFIER * 2.0;
                double scale = Math.min(speed, distance) / distance;
                yaw = Math.atan2(toRunUpZ, toRunUpX);
                state = MobState.WANDER;
                MobPhysics.tickMove(this, world, toRunUpX * scale, 0, toRunUpZ * scale,
                        MoveMode.WALK);
                return java.util.List.of();
            }
            ramPrepareMcTicks = GoatRamRules.advanceMcCursor(ramPrepareMcTicks);
            yaw = Math.atan2(ramTargetZ - z, ramTargetX - x);
            state = MobState.IDLE;
            if (ramPrepareMcTicks > 0) return java.util.List.of();
            beginCharge();
        }
        if (ramPhase == GoatRamRules.Phase.CHARGING) {
            double speed = type.baseSpeed() * GoatRamRules.RAM_SPEED_MODIFIER * 2.0;
            double oldX = x, oldZ = z;
            yaw = Math.atan2(ramDirectionZ, ramDirectionX);
            state = MobState.ATTACK;
            MobPhysics.tickMove(this, world, ramDirectionX * speed, 0,
                    ramDirectionZ * speed, MoveMode.WALK);
            ramDistance += Math.hypot(x - oldX, z - oldZ);
            int[] impact = solidImpactAhead(world);
            if (impact != null) {
                ramPhase = GoatRamRules.Phase.IMPACT_PENDING;
                ramSequence++;
                pendingImpactBlockId = impact[0];
                pendingImpactX = impact[1];
                pendingImpactY = impact[2];
                pendingImpactZ = impact[3];
                pendingPreferLeft = hornMask == 3 && rng.nextInt(2) == 0 || hornMask == 1;
                ramCooldownMcTicks = GoatRamRules.sampleCooldown(rng);
                setPersistenceRequired(true);
            } else if (ramDistance >= GoatRamRules.MAX_RUN_UP_BLOCKS + 2.0) {
                cancelRam(rng);
            }
            return java.util.List.of();
        }
        return super.tick(world, rng);
    }

    @Override
    boolean tickBreedingState() {
        boolean wasBaby = isBaby();
        boolean wasInLove = super.tickBreedingState();
        if (wasBaby && !isBaby() && hornMask == 0) hornMask = 3;
        return wasInLove;
    }
}

final class Mooshroom extends VariantWildlifeMob {
    private String mooshroomVariant;
    private int storedFlower;

    Mooshroom(long id, double x, double y, double z, String variant) {
        super(MobType.MOOSHROOM, id, x, y, z, variant);
        mooshroomVariant = variant;
    }

    @Override
    public String variant() { return mooshroomVariant; }

    int storedFlower() { return storedFlower; }

    boolean storeFlower(int flowerBlockId) {
        if (!MooshroomRules.BROWN.equals(mooshroomVariant)
                || storedFlower != MooshroomRules.NO_STORED_FLOWER) return false;
        storedFlower = flowerBlockId;
        setPersistenceRequired(true);
        return true;
    }

    short plannedStew() {
        if (MooshroomRules.BROWN.equals(mooshroomVariant)
                && storedFlower != MooshroomRules.NO_STORED_FLOWER) {
            return com.gameexpert.engine.SuspiciousStewRules.stewForFlower(storedFlower);
        }
        return com.gameexpert.engine.inventory.PlayerInventory.MUSHROOM_STEW;
    }

    boolean confirmStew(short plannedItem) {
        if (plannedItem != plannedStew()) return false;
        if (plannedItem != com.gameexpert.engine.inventory.PlayerInventory.MUSHROOM_STEW
                && storedFlower != MooshroomRules.NO_STORED_FLOWER) {
            storedFlower = MooshroomRules.NO_STORED_FLOWER;
            setPersistenceRequired(true);
        }
        return true;
    }

    void toggleVariant() {
        mooshroomVariant = MooshroomRules.RED.equals(mooshroomVariant)
                ? MooshroomRules.BROWN : MooshroomRules.RED;
        setPersistenceRequired(true);
    }

    void restoreStoredFlower(int restoredFlower) {
        if (restoredFlower != MooshroomRules.NO_STORED_FLOWER
                && !com.gameexpert.engine.SuspiciousStewRules.isStewFlower(restoredFlower)) {
            throw new IllegalArgumentException("invalid persisted Mooshroom flower");
        }
        storedFlower = restoredFlower;
    }
}

final class Ocelot extends VariantWildlifeMob {
    Ocelot(long id, double x, double y, double z) {
        super(MobType.OCELOT, id, x, y, z, null);
    }

    @Override
    public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (ocelotTrusting() || fleeTimer > 0 || hasBreedTarget && isInLoveMode()) {
            return super.tick(world, rng);
        }
        PlayerSnapshot nearest = nearestAlive(world, 16.0);
        if (nearest == null) return super.tick(world, rng);
        double dx = x - nearest.x();
        double dz = z - nearest.z();
        double distance = Math.hypot(dx, dz);
        if (distance < 1e-9) return super.tick(world, rng);
        double speed = type.baseSpeed() * (distance < 7.0 ? 1.33 : 0.8);
        double moveX = dx / distance * speed;
        double moveZ = dz / distance * speed;
        yaw = Math.atan2(moveZ, moveX);
        state = MobState.FLEE;
        MobPhysics.tickMove(this, world, moveX, 0.0, moveZ, MoveMode.WALK);
        return java.util.List.of();
    }
}

final class Panda extends VariantWildlifeMob {
    /** [MOB-LOOK] The displayed gene variant (PandaGenes). */
    Panda(long id, double x, double y, double z, String variant) {
        super(MobType.PANDA, id, x, y, z, variant);
    }
}

final class Parrot extends CompanionMob {
    Parrot(long id, double x, double y, double z, String variant) {
        super(MobType.PARROT, id, x, y, z, variant);
    }

    @Override
    public String movementMedium() { return "fly"; }
}

final class PolarBear extends VariantWildlifeMob {
    PolarBear(long id, double x, double y, double z) {
        super(MobType.POLAR_BEAR, id, x, y, z, null);
    }
}

final class Turtle extends VariantWildlifeMob {
    private static final int EGG_DIG_MC_TICKS = 200;
    private int homeX;
    private int homeY;
    private int homeZ;
    private boolean gravid;
    private boolean travelingHome;
    private boolean eggPlacementPending;
    private int eggDigMcTicks;
    private int eggCount;

    Turtle(long id, double x, double y, double z) {
        super(MobType.TURTLE, id, x, y, z, null);
        homeX = (int) Math.floor(x);
        homeY = (int) Math.floor(y);
        homeZ = (int) Math.floor(z);
    }

    boolean gravid() { return gravid; }

    /**
     * [MOB-LOOK] Species-scoped visual bits: {@code Turtle#hasEgg} (AdultTurtleModel's egg_belly) and
     * {@code Turtle#isLayingEgg} (the digging front-flipper gait) — the gravid state and its dig cursor.
     */
    static final int VISUAL_HAS_EGG = Mob.VISUAL_TURTLE_HAS_EGG;
    static final int VISUAL_LAYING_EGG = Mob.VISUAL_TURTLE_LAYING_EGG;

    @Override public int visualFlags() {
        return (gravid ? VISUAL_HAS_EGG : 0) | (gravid && eggDigMcTicks > 0 ? VISUAL_LAYING_EGG : 0);
    }

    int homeX() { return homeX; }
    int homeY() { return homeY; }
    int homeZ() { return homeZ; }
    boolean travelingHome() { return travelingHome; }
    int eggDigMcTicks() { return eggDigMcTicks; }
    int eggCount() { return eggCount; }

    boolean beginEggPlacement() {
        if (gravid || isBaby()) return false;
        gravid = true;
        travelingHome = true;
        eggCount = deterministicEggCount(id, homeX, homeY, homeZ);
        setPersistenceRequired(true);
        return true;
    }

    boolean armEggPlacementRequest() {
        if (!gravid || eggPlacementPending || eggDigMcTicks < EGG_DIG_MC_TICKS) return false;
        eggPlacementPending = true;
        return true;
    }

    void confirmEggPlacement(boolean placed) {
        if (!eggPlacementPending) return;
        eggPlacementPending = false;
        if (placed) {
            gravid = false;
            travelingHome = false;
            eggDigMcTicks = 0;
            eggCount = 0;
        }
    }

    void restoreEggState(boolean restoredGravid, int restoredHomeX, int restoredHomeY,
            int restoredHomeZ, boolean restoredTravelingHome, int restoredDigMcTicks,
            int restoredEggCount) {
        if (restoredDigMcTicks < 0 || restoredDigMcTicks > EGG_DIG_MC_TICKS) {
            throw new IllegalArgumentException("invalid Turtle egg digging cursor");
        }
        if (restoredGravid != (restoredEggCount >= 1 && restoredEggCount <= 4)
                || !restoredGravid && restoredTravelingHome
                || restoredTravelingHome && restoredDigMcTicks != 0) {
            throw new IllegalArgumentException("invalid Turtle egg travel/clutch state");
        }
        gravid = restoredGravid;
        travelingHome = restoredTravelingHome;
        eggPlacementPending = false;
        eggDigMcTicks = restoredGravid ? restoredDigMcTicks : 0;
        eggCount = restoredGravid ? restoredEggCount : 0;
        homeX = restoredHomeX;
        homeY = restoredHomeY;
        homeZ = restoredHomeZ;
    }

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (!gravid || isDead()) return super.tick(world, rng);
        double targetX = homeX + 0.5, targetZ = homeZ + 0.5;
        double dx = targetX - x, dz = targetZ - z;
        double distance = Math.hypot(dx, dz);
        if (distance > 1.25) {
            travelingHome = true;
            eggDigMcTicks = 0;
            double speed = Math.min(type.baseSpeed(), distance) / distance;
            MobPhysics.tickMove(this, world, dx * speed, 0.0, dz * speed, MoveMode.WALK);
            faceToward(targetX, targetZ);
            state = MobState.WANDER;
            return java.util.List.of();
        }
        travelingHome = false;
        int below = world.getBlock(homeX, homeY - 1, homeZ) & 0xffff;
        int target = world.getBlock(homeX, homeY, homeZ) & 0xffff;
        if (!com.gameexpert.engine.TurtleEggRules.canPlaceOn(below)
                || target != com.gameexpert.terrain.Blocks.AIR) {
            eggDigMcTicks = 0;
            return java.util.List.of();
        }
        eggDigMcTicks = Math.min(EGG_DIG_MC_TICKS,
                eggDigMcTicks + PufferfishRules.MC_TICKS_PER_AUTHORITY_TICK);
        state = MobState.IDLE;
        setPersistenceRequired(true);
        return java.util.List.of();
    }

    private static int deterministicEggCount(long turtleId, int x, int y, int z) {
        long mixed = turtleId * 0x9e3779b97f4a7c15L
                ^ (long) x * 0x632be59bd9b4e019L
                ^ (long) y * 0x94d049bb133111ebL
                ^ (long) z * 0xbf58476d1ce4e5b9L;
        return 1 + (int) Math.floorMod(mixed, 4L);
    }
}

final class Wolf extends VariantWildlifeMob {
    static final int DEFAULT_COLLAR_COLOR = 14;
    static final int BODY_ARMOR_MAX_DURABILITY = 64;
    static final int BODY_ARMOR_REPAIR_PER_SCUTE = 8;
    static final int MELEE_DAMAGE = 4;
    static final int MELEE_COOLDOWN_AUTHORITY_TICKS = 10;
    private static final double FOLLOW_START_DISTANCE_SQUARED = 100.0;
    private static final double FOLLOW_STOP_DISTANCE_SQUARED = 4.0;
    private static final double OWNER_DEFENSE_REACH = 2.0;

    private boolean followingOwner;
    private int collarColor = DEFAULT_COLLAR_COLOR;
    private int bodyArmorDurability;
    private int attackCooldown;

    Wolf(long id, double x, double y, double z, String variant) {
        super(MobType.WOLF, id, x, y, z, variant);
    }

    int collarColor() { return collarColor; }
    int bodyArmorDurability() { return bodyArmorDurability; }

    boolean dyeCollar(String nickname, int dyeColorId) {
        if (!isOwnedBy(nickname) || dyeColorId < 0 || dyeColorId > 15 || collarColor == dyeColorId) return false;
        collarColor = dyeColorId;
        setPersistenceRequired(true);
        return true;
    }

    boolean equipBodyArmor(String nickname) {
        if (!isOwnedBy(nickname) || isBaby() || bodyArmorDurability != 0) return false;
        bodyArmorDurability = BODY_ARMOR_MAX_DURABILITY;
        setPersistenceRequired(true);
        return true;
    }

    boolean repairBodyArmor(String nickname) {
        if (!isOwnedBy(nickname) || !wolfSitting() || bodyArmorDurability <= 0
                || bodyArmorDurability >= BODY_ARMOR_MAX_DURABILITY) return false;
        bodyArmorDurability = Math.min(BODY_ARMOR_MAX_DURABILITY,
                bodyArmorDurability + BODY_ARMOR_REPAIR_PER_SCUTE);
        setPersistenceRequired(true);
        return true;
    }

    int removeBodyArmor(String nickname) {
        if (!isOwnedBy(nickname) || bodyArmorDurability <= 0) return 0;
        int durability = bodyArmorDurability;
        bodyArmorDurability = 0;
        setPersistenceRequired(true);
        return durability;
    }

    void restoreWolfState(int restoredCollarColor, int restoredArmorDurability) {
        if (restoredCollarColor < 0 || restoredCollarColor > 15
                || restoredArmorDurability < 0 || restoredArmorDurability > BODY_ARMOR_MAX_DURABILITY
                || restoredArmorDurability > 0 && ownerNickname() == null) {
            throw new IllegalArgumentException("invalid persisted Wolf collar/body armor state");
        }
        collarColor = restoredCollarColor;
        bodyArmorDurability = restoredArmorDurability;
    }

    boolean mayDefendOwnerAgainst(Mob target) {
        if (ownerNickname() == null || target == null || target == this
                || target.isDead() || target.removed || target.type == MobType.CREEPER) return false;
        return target.ownerNickname() == null || !ownerNickname().equals(target.ownerNickname());
    }

    private boolean isOwnedBy(String nickname) {
        return nickname != null && ownerNickname() != null && ownerNickname().equals(nickname);
    }

    @Override protected double adjustIncomingDamage(double rawAmount) {
        if (bodyArmorDurability <= 0 || rawAmount <= 0.0) return rawAmount;
        bodyArmorDurability = Math.max(0, bodyArmorDurability - (int) Math.ceil(rawAmount));
        return 0.0;
    }

    private int armorCrackLevel() {
        if (bodyArmorDurability <= 0) return 0;
        float fraction = (float) bodyArmorDurability / BODY_ARMOR_MAX_DURABILITY;
        if (fraction < 0.32F) return 3;
        if (fraction < 0.69F) return 2;
        return fraction < 0.95F ? 1 : 0;
    }

    @Override public int visualFlags() {
        int flags = ownerNickname() == null ? 0 : Mob.VISUAL_WOLF_TAMED;
        if (wolfSitting()) flags |= Mob.VISUAL_WOLF_SITTING;
        // 마스크로 잘라 넣어 두 인접 비트 구간이 서로 침범하지 않음을 코드에서 보장한다.
        flags |= collarColor << Mob.VISUAL_WOLF_COLLAR_SHIFT & Mob.VISUAL_WOLF_COLLAR_MASK;
        if (bodyArmorDurability > 0) {
            flags |= Mob.VISUAL_WOLF_BODY_ARMOR;
            flags |= armorCrackLevel() << Mob.VISUAL_WOLF_ARMOR_CRACK_SHIFT
                    & Mob.VISUAL_WOLF_ARMOR_CRACK_MASK;
        }
        return flags;
    }

    @Override public void commitAcceptedMeleeAttack() {
        attackCooldown = MELEE_COOLDOWN_AUTHORITY_TICKS;
        super.commitAcceptedMeleeAttack();
    }

    private boolean wetCoat;

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        boolean wet = bodyTouchesWater(world) || world.isRainingAt(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (wet) {
            wetCoat = true;
            if ("shake".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
        } else if (wetCoat && onGround && state == MobState.IDLE && !"shake".equals(actionKind())) {
            wetCoat = false;
            markVisualAction("shake", 21); // Wolf shake:0.05 per MC tick, ends after 2.0.
        }

        if (attackCooldown > 0) attackCooldown--;
        if (wolfSitting()) {
            followingOwner = false;
            state = MobState.IDLE;
            return java.util.List.of();
        }
        Mob defenseTarget = preparedSocialTarget();
        if (mayDefendOwnerAgainst(defenseTarget) && MobRelationshipPolicy.inTargetRange(this, defenseTarget)) {
            followingOwner = false;
            faceToward(defenseTarget.x, defenseTarget.z);
            if (MobRelationshipPolicy.distanceSquared(this, defenseTarget) <= OWNER_DEFENSE_REACH * OWNER_DEFENSE_REACH
                    && MobRelationshipPolicy.hasLineOfSight(world, this, defenseTarget)) {
                state = MobState.ATTACK;
                if (attackCooldown == 0) {
                    return java.util.List.of(MobEvent.AttackMob.direct(
                            defenseTarget.id, weakenedDamage(MELEE_DAMAGE), null));
                }
                return java.util.List.of();
            }
            double[] movement = towardHoriz(defenseTarget.x, defenseTarget.z, type.baseSpeed());
            state = MobState.CHASE;
            MobPhysics.tickMove(this, world, movement[0], 0.0, movement[1], MoveMode.WALK);
            return java.util.List.of();
        }
        if (ownerNickname() == null || fleeTimer > 0 || hasBreedTarget && isInLoveMode()) {
            followingOwner = false;
            return super.tick(world, rng);
        }
        PlayerSnapshot owner = null;
        for (PlayerSnapshot player : world.players()) {
            if (player.alive() && ownerNickname().equals(player.nickname())) { owner = player; break; }
        }
        if (owner == null) {
            followingOwner = false;
            return super.tick(world, rng);
        }
        double dx = owner.x() - x, dy = owner.y() - y, dz = owner.z() - z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (!followingOwner && distanceSquared > FOLLOW_START_DISTANCE_SQUARED) followingOwner = true;
        if (followingOwner && distanceSquared <= FOLLOW_STOP_DISTANCE_SQUARED) followingOwner = false;
        if (!followingOwner) return super.tick(world, rng);
        double[] movement = towardHoriz(owner.x(), owner.z(), type.baseSpeed());
        yaw = Math.atan2(movement[1], movement[0]);
        state = MobState.WANDER;
        MobPhysics.tickMove(this, world, movement[0], 0.0, movement[1], MoveMode.WALK);
        return java.util.List.of();
    }
}
