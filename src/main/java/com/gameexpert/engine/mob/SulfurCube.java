package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.Fluids;

/** Minecraft Java 26.3-snapshot-7 sulfur cube authority state. */
public final class SulfurCube extends MeleeMob {
    /**
     * Immutable source capability for the existing {@link MobEvent.Explode} action. The owning
     * runtime includes these exact carried-state facts in its durable settlement and acknowledges
     * this capability only after that settlement commits.
     */
    public record PendingExplosionSource(long sourceMobId, MobType sourceType,
            MobEvent.Explode action, short bodyItem, int size, boolean fromBucket,
            int fuseTicks, int maxFuseTicks) {
        public PendingExplosionSource {
            if (sourceMobId <= 0 || sourceType != MobType.SULFUR_CUBE || action == null
                    || bodyItem == 0 || size < SulfurCubeRules.MIN_SIZE
                    || size > SulfurCubeRules.ADULT_SIZE || fuseTicks < 0
                    || maxFuseTicks < fuseTicks) {
                throw new IllegalArgumentException("invalid sulfur cube explosion source");
            }
        }
    }

    private short bodyItem;
    private int pickupCooldownTicks;
    private int fuse = -1;
    private int maxFuse = -1;
    private boolean fromBucket;
    private PendingExplosionSource pendingExplosion;
    private boolean explosionRetryRequested;

    public SulfurCube(long id, double x, double y, double z) {
        super(id, MobType.SULFUR_CUBE, x, y, z);
    }

    @Override protected double detectRange() { return 0; }
    @Override protected double attackRange() { return 0; }
    @Override protected int attackDamage() { return 0; }
    @Override protected int attackCooldownTicks() { return 1; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected boolean hostile(MobWorldView world) { return false; }
    /**
     * 큐브 크기 계단. {@code SulfurCube#setSpawnSize}·{@code setSize} 가 크기 1 을 곧바로
     * 새끼로 못 박으므로 <b>크기 1 ⟺ 새끼</b> 다. {@code Frog#canEat} 는 이 값이 1 일 때만
     * 먹는다 — {@code isBaby()} 를 상수 false 로 덮으면 개구리가 영원히 먹지 못한다.
     */
    public int size() {
        return isBaby() ? SulfurCubeRules.MIN_SIZE : SulfurCubeRules.ADULT_SIZE;
    }
    @Override public int maxHp() { return SulfurCubeRules.MAX_HEALTH; }
    @Override public double width() { return SulfurCubeRules.BASE_WIDTH * size(); }
    @Override public double height() { return SulfurCubeRules.BASE_HEIGHT * size(); }
    @Override public boolean fireImmune() { return SulfurCubeRules.FIRE_IMMUNE; }
    @Override public int carriedBlockId() { return Short.toUnsignedInt(bodyItem); }
    @Override public boolean canEverPickupEquipment() { return true; }
    @Override public boolean canPickupEquipment(short itemType) {
        return bodyItem == 0 && pickupCooldownTicks == 0
                && SulfurCubeRules.archetypeFor(Short.toUnsignedInt(itemType)) != null;
    }
    @Override public boolean tryPickupEquipment(short itemType, int durability) {
        if (durability != 0 || !canPickupEquipment(itemType)) return false;
        bodyItem = itemType;
        setPersistenceRequired(true);
        return true;
    }

    public boolean hasBodyItem() { return bodyItem != 0; }
    public short bodyItem() { return bodyItem; }
    public SulfurCubeRules.Archetype archetype() {
        return SulfurCubeRules.archetypeFor(Short.toUnsignedInt(bodyItem));
    }
    public boolean buoyant() { return archetype() != null && archetype().buoyant; }
    public boolean canExplode() { return archetype() == SulfurCubeRules.Archetype.EXPLOSIVE; }
    public boolean isPrimed() { return fuse >= 0; }
    public int fuseTicks() { return fuse; }
    public int maxFuseTicks() { return maxFuse; }
    public int pickupCooldownTicks() { return pickupCooldownTicks; }
    public boolean fromBucket() { return fromBucket; }

    /** Exact immutable source facts currently waiting for the owning settlement lane. */
    public PendingExplosionSource pendingExplosionSource() { return pendingExplosion; }

    /**
     * Acknowledges the exact staged source. Rejection changes no live source state and requests
     * one identical retry emission; commit retires the mob once. A stale or repeated acknowledgement
     * is rejected without changing the entity.
     */
    public boolean acknowledgeExplosionSettlement(PendingExplosionSource source,
            boolean committed) {
        if (pendingExplosion == null || source != pendingExplosion
                || source.sourceMobId() != id || source.sourceType() != type
                || isDead() || removed) return false;
        if (!committed) {
            explosionRetryRequested = true;
            return true;
        }
        pendingExplosion = null;
        explosionRetryRequested = false;
        kill();
        removed = true;
        return true;
    }

    /** Convenience form for the owner after looking the live mob up by the emitted mob ID. */
    public boolean acknowledgeExplosionSettlement(boolean committed) {
        return acknowledgeExplosionSettlement(pendingExplosion, committed);
    }

    public boolean prime(boolean shortFuse, MobRandom rng) {
        if (!canExplode() || isPrimed() || isDead()) return false;
        int ordinary = SulfurCubeRules.EXPLOSIVE_FUSE_TICKS;
        fuse = shortFuse ? ordinary / 8 + rng.nextInt(Math.max(1, ordinary / 4)) : ordinary;
        maxFuse = fuse;
        return true;
    }

    /** Shears eject the exact swallowed stack and impose the official 100-MC-tick pickup delay. */
    public short shearBodyItem() {
        if (!hasBodyItem() || pendingExplosion != null) return 0;
        short ejected = bodyItem;
        bodyItem = 0;
        fuse = -1;
        maxFuse = -1;
        pickupCooldownTicks = SulfurCubeRules.PICKUP_COOLDOWN_TICKS;
        return ejected;
    }

    public void restoreSulfurCubeState(short restoredBodyItem, int restoredPickupCooldown,
            int restoredFuse, int restoredMaxFuse, boolean restoredFromBucket) {
        if (restoredBodyItem != 0
                && SulfurCubeRules.archetypeFor(Short.toUnsignedInt(restoredBodyItem)) == null) {
            throw new IllegalArgumentException("invalid sulfur cube body item");
        }
        if (restoredPickupCooldown < 0 || restoredPickupCooldown > SulfurCubeRules.PICKUP_COOLDOWN_TICKS
                || restoredFuse < -1 || restoredMaxFuse < -1
                || (restoredFuse >= 0 && restoredMaxFuse < restoredFuse)
                || (restoredFuse >= 0 && restoredBodyItem != (short) com.gameexpert.terrain.Blocks.TNT)) {
            throw new IllegalArgumentException("invalid sulfur cube persisted state");
        }
        bodyItem = restoredBodyItem;
        pickupCooldownTicks = restoredPickupCooldown;
        fuse = restoredFuse;
        maxFuse = restoredMaxFuse;
        fromBucket = restoredFromBucket;
        if (bodyItem != 0 || fromBucket) setPersistenceRequired(true);
    }

    public void setFromBucket(boolean value) {
        if (pendingExplosion != null) return;
        fromBucket = value;
        if (value) setPersistenceRequired(true);
    }

    /**
     * Pinned {@code FloatGoal} gate: sample the maximum fluid height intersecting the cube's
     * footprint and start the ordinary liquid jump only past the fixed 20%-height threshold.
     */
    boolean submergedPastFluidJumpThreshold(MobWorldView world) {
        double half = width() / 2.0;
        int x0 = (int) Math.floor(x - half + 1e-6);
        int x1 = (int) Math.floor(x + half - 1e-6);
        int y0 = (int) Math.floor(y + 1e-6);
        int y1 = (int) Math.floor(y + height() - 1e-6);
        int z0 = (int) Math.floor(z - half + 1e-6);
        int z1 = (int) Math.floor(z + half - 1e-6);
        double fluidHeight = 0.0;
        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                for (int bz = z0; bz <= z1; bz++) {
                    short block = world.getBlock(bx, by, bz);
                    if (block < 0) continue;
                    int id = block & 0xffff;
                    if (!Fluids.isWaterMedium(id) && !Fluids.isLava(id)) continue;
                    fluidHeight = Math.max(fluidHeight,
                            Math.min(y + height(), by + 1.0) - y);
                }
            }
        }
        return fluidHeight > SulfurCubeRules.FLUID_JUMP_THRESHOLD;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead() || removed) return List.of();
        if (pendingExplosion != null) {
            if (!explosionRetryRequested) return List.of();
            explosionRetryRequested = false;
            return List.of(pendingExplosion.action());
        }
        if (pickupCooldownTicks > 0) pickupCooldownTicks--;
        if (fuse >= 0) {
            if (fuse > 0) fuse--;
            if (fuse == 0) {
                MobEvent.Explode action = new MobEvent.Explode(
                        x, y + 0.0625, z, SulfurCubeRules.EXPLOSIVE_POWER, 43);
                pendingExplosion = new PendingExplosionSource(
                        id, type, action, bodyItem, size(), fromBucket, fuse, maxFuse);
                explosionRetryRequested = false;
                setPersistenceRequired(true);
                return List.of(action);
            }
        }
        List<MobEvent> events = super.tick(world, rng);
        SulfurCubeRules.Archetype a = archetype();
        if (a != null && a.contactDamage > 0) {
            for (PlayerSnapshot player : world.players()) {
                double dx = player.x() - x, dz = player.z() - z;
                if (dx * dx + dz * dz <= width() * width()
                        && Math.abs(player.y() - y) <= height()) {
                    events = appendEvent(events, new MobEvent.AttackPlayer(
                            player.nickname(), a.contactDamage, x, z, null, null));
                }
            }
        }
        return events;
    }
}
