package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.terrain.Blocks;

/** Durable ownership commands with species-specific terrestrial/flying owner navigation. */
abstract class CompanionMob extends AnimalMob {
    private boolean orderedToSit;
    private boolean followingOwner;
    private boolean leashed;
    private int collarColor;
    private int teleportCooldown;

    CompanionMob(MobType type, long id, double x, double y, double z, String variant) {
        super(type, id, x, y, z, variant);
        collarColor = type == MobType.CAT ? 14 : -1;
    }

    protected boolean mayLieOnBed() {
        return ownerNickname() != null && !orderedToSit && !leashed && !isMobPassenger()
                && !isRidingBoat() && fleeTimer == 0;
    }

    boolean sitting() { return orderedToSit; }
    int collarColor() { return collarColor; }
    void prepareLeashed(boolean value) { leashed = value; }

    boolean tame(String nickname) {
        if (isDead() || ownerNickname() != null || nickname == null || nickname.isBlank()) return false;
        setOwnerNickname(nickname);
        orderedToSit = type == MobType.CAT;
        followingOwner = false;
        clearLoveMode();
        setPersistenceRequired(true);
        return true;
    }

    boolean toggleSitting(String nickname) {
        if (isDead() || nickname == null || !nickname.equals(ownerNickname())
                || type == MobType.PARROT && !onGround) return false;
        orderedToSit = !orderedToSit;
        followingOwner = false;
        setPersistenceRequired(true);
        return true;
    }

    boolean dyeCollar(String nickname, int color) {
        if (type != MobType.CAT || nickname == null || !nickname.equals(ownerNickname())
                || color < 0 || color > 15 || color == collarColor) return false;
        collarColor = color;
        setPersistenceRequired(true);
        return true;
    }

    void restoreCompanion(boolean sitting, int color) {
        if (sitting && ownerNickname() == null
                || (type == MobType.CAT ? color < 0 || color > 15 : color != -1)) {
            throw new IllegalStateException("invalid persisted Cat/Parrot companion state");
        }
        orderedToSit = sitting;
        collarColor = color;
    }

    @Override public int visualFlags() {
        int flags = ownerNickname() == null ? 0 : VISUAL_COMPANION_TAMED;
        if (orderedToSit) flags |= VISUAL_COMPANION_SITTING;
        if (type == MobType.CAT) flags |= collarColor << VISUAL_CAT_COLLAR_SHIFT;
        return flags;
    }

    @Override public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (teleportCooldown > 0) teleportCooldown--;
        boolean water = Fluids.isWaterMedium(world.getBlock(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)) & 0xffff);
        if (orderedToSit && !water && fleeTimer == 0) {
            followingOwner = false;
            state = MobState.IDLE;
            // A command never suspends gravity while an airborne animal is landing.
            MobPhysics.tickMove(this, world, 0, 0, 0, MoveMode.WALK);
            return List.of();
        }
        if (ownerNickname() == null || orderedToSit || leashed || isMobPassenger() || isRidingBoat() || fleeTimer > 0) {
            followingOwner = false;
            return super.tick(world, rng);
        }
        PlayerSnapshot owner = null;
        for (PlayerSnapshot player : world.players()) {
            if (player.alive() && ownerNickname().equals(player.nickname())) { owner = player; break; }
        }
        if (owner == null) { followingOwner = false; return super.tick(world, rng); }
        double dx = owner.x() - x, dy = owner.y() - y, dz = owner.z() - z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        double start = CompanionRules.followStart(type), stop = CompanionRules.followStop(type);
        if (distanceSquared >= start * start) followingOwner = true;
        if (distanceSquared <= stop * stop) followingOwner = false;
        if (!followingOwner) return super.tick(world, rng);
        if (distanceSquared >= 144 && teleportCooldown == 0) {
            teleportCooldown = 5;
            double fromX = x, fromY = y, fromZ = z;
            if (tryTeleport(world, owner, rng)) return List.of(new MobEvent.Teleported(fromX, fromY, fromZ, x, y, z));
        }
        state = MobState.WANDER;
        if (type == MobType.PARROT) {
            double speed = type.baseSpeed() / Math.sqrt(distanceSquared);
            yaw = Math.atan2(dz, dx);
            MobPhysics.tickMove(this, world, dx * speed,
                    dy * speed + MobPhysics.GRAVITY * MobPhysics.FLY_GRAVITY_SCALE, dz * speed, MoveMode.FLY);
        } else {
            double[] movement = towardHoriz(owner.x(), owner.z(), type.baseSpeed());
            yaw = Math.atan2(movement[1], movement[0]);
            MobPhysics.tickMove(this, world, movement[0], 0, movement[1], MoveMode.WALK);
        }
        return List.of();
    }

    private boolean tryTeleport(MobWorldView world, PlayerSnapshot owner, MobRandom rng) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = rng.nextInt(7) - 3, dz = rng.nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            int px = (int) Math.floor(owner.x()) + dx;
            int pz = (int) Math.floor(owner.z()) + dz;
            int py = (int) Math.floor(owner.y()) + rng.nextInt(3) - 1;
            if (!world.isChunkActive(Math.floorDiv(px, 16), Math.floorDiv(pz, 16))) continue;
            int floor = world.getBlock(px, py - 1, pz) & 0xffff;
            if (!TeleportSafety.withinBorder(px, pz)
                    || type == MobType.CAT && TeleportSafety.dangerous(floor, world.blockState(px, py - 1, pz, floor))
                    || floor == Blocks.MAGMA || floor == Blocks.CACTUS
                    || type == MobType.CAT && BlockFamilies.isLeaves(floor)
                    || !BuildingBlockRules.isFullCollisionShape(floor, world.blockState(px, py - 1, pz, floor))
                    || world.getBlock(px, py, pz) != Blocks.AIR) continue;
            x = px + 0.5; y = py; z = pz + 0.5;
            vy = horizontalVx = horizontalVz = 0;
            onGround = true;
            followingOwner = false;
            state = MobState.IDLE;
            return true;
        }
        return false;
    }
}
