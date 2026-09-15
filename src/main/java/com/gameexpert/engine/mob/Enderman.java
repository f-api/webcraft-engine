package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.PlayerInteractionRules;
import com.gameexpert.terrain.Blocks;

/** 엔더맨: 응시로 적대화하고 물·투사체·피격에 순간이동하며 블록을 옮긴다. */
public final class Enderman extends MeleeMob {
    public static final double STARE_RANGE = 64.0;
    public static final int STARE_MC_TICKS = 5;
    public static final int MC_TICKS_PER_SERVER_TICK = 2;
    public static final int WATER_DAMAGE_INTERVAL_TICKS = 5;
    public static final double WATER_DAMAGE = 1.0;
    public static final int NORMAL_DAMAGE = 7;
    public static final int BLOCK_TAKE_ROLL = 20;
    public static final int BLOCK_LEAVE_ROLL = 2_000;
    public static final int TELEPORT_ATTEMPTS = 64;
    public static final int TELEPORT_RADIUS = 32;

    private boolean aggressive;
    private boolean teleportRequested;
    private String stareNickname;
    private int stareMcTicks;
    private int wetTicks;
    private short carriedBlock;
    private short pendingCarriedBlock;
    private boolean pendingBlockChange;

    public Enderman(long id, double x, double y, double z) {
        super(id, MobType.ENDERMAN, x, y, z);
    }

    @Override protected double detectRange() { return STARE_RANGE; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return NORMAL_DAMAGE; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected boolean climbWalls() { return false; }
    @Override protected boolean hostile(MobWorldView world) { return aggressive; }

    public short carriedBlock() { return carriedBlock; }
    @Override public int carriedBlockId() { return Short.toUnsignedInt(carriedBlock); }
    public boolean aggressive() { return aggressive; }

    void restoreCarriedBlock(short block) {
        carriedBlock = block;
        pendingCarriedBlock = 0;
        pendingBlockChange = false;
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        aggressive = true;
        forceTarget(attackerNickname);
        teleportRequested = true;
    }

    /** 화살 충돌 전에 호출한다. 보트에 탔거나 목적지가 없으면 회피에 실패한다. */
    public boolean evadeProjectile(MobWorldView world, MobRandom rng) {
        return evadeProjectileEvent(world, rng) != null;
    }

    public MobEvent.Teleported evadeProjectileEvent(MobWorldView world, MobRandom rng) {
        return teleportRandomEvent(world, rng);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        List<MobEvent> events = acquireByStare(world);

        boolean wet = bodyTouchesWater(world) || world.isRainingAt(
                (int) Math.floor(x), (int) Math.floor(y + height()), (int) Math.floor(z));
        if (wet) {
            wetTicks++;
            teleportRequested = true;
            if (wetTicks >= WATER_DAMAGE_INTERVAL_TICKS) {
                wetTicks = 0;
                damageBypassesArmor(WATER_DAMAGE);
                teleportRequested = true;
                if (isDead()) return List.of(new MobEvent.Despawned("water"));
            }
        } else {
            wetTicks = 0;
        }

        if (teleportRequested) {
            teleportRequested = false;
            MobEvent.Teleported teleport = teleportRandomEvent(world, rng);
            if (teleport != null) events = appendEvent(events, teleport);
        }

        for (MobEvent event : super.tick(world, rng)) events = appendEvent(events, event);
        MobEvent blockEvent = blockMove(world, rng);
        if (blockEvent != null) events = appendEvent(events, blockEvent);
        return events;
    }

    private List<MobEvent> acquireByStare(MobWorldView world) {
        if (aggressive) return List.of();
        PlayerSnapshot staring = null;
        double bestSq = STARE_RANGE * STARE_RANGE;
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive() || player.carvedPumpkinHelmet()
                    || Float.isNaN(player.yaw()) || Float.isNaN(player.pitch())) continue;
            double dx = x - player.x();
            double playerEyeY = player.y() + PlayerInteractionRules.eyeHeight(player.crouching());
            double dy = y + eyeHeight() - playerEyeY;
            double dz = z - player.z();
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > bestSq || !lookingAtFace(player, dx, dy, dz)
                    || !world.hasLineOfSight(player.x(), playerEyeY, player.z(),
                            x, y + eyeHeight(), z)) continue;
            bestSq = distanceSq;
            staring = player;
        }
        if (staring == null) {
            stareNickname = null;
            stareMcTicks = 0;
            return List.of();
        }
        boolean newStare = !staring.nickname().equals(stareNickname);
        if (!newStare) stareMcTicks += MC_TICKS_PER_SERVER_TICK;
        else {
            stareNickname = staring.nickname();
            stareMcTicks = MC_TICKS_PER_SERVER_TICK;
        }
        if (stareMcTicks >= STARE_MC_TICKS) {
            aggressive = true;
            forceTarget(staring.nickname());
            if (newStare) return List.of(new MobEvent.Sound("stare"), new MobEvent.Sound("angry"));
            return List.of(new MobEvent.Sound("angry"));
        }
        return newStare ? List.of(new MobEvent.Sound("stare")) : List.of();
    }

    /** 조준 광선이 엔더맨 얼굴 폭(0.6)·높이(0.7)에 실제로 닿는지 계산한다. */
    static boolean lookingAtFace(PlayerSnapshot player, double dx, double dy, double dz) {
        double yaw = player.yaw();
        double pitch = player.pitch();
        double cosPitch = Math.cos(pitch);
        double vx = -Math.sin(yaw) * cosPitch;
        double vy = Math.sin(pitch);
        double vz = -Math.cos(yaw) * cosPitch;
        double along = dx * vx + dy * vy + dz * vz;
        if (along <= 0.0) return false;
        double ex = dx - vx * along;
        double ey = dy - vy * along;
        double ez = dz - vz * along;
        return ex * ex + ez * ez <= 0.3 * 0.3 && Math.abs(ey) <= 0.35;
    }

    /** 확인된 Java 범위: 현재점 기준 각 축 ±32, 최대 64 후보, 물 없는 3칸 높이. */
    public boolean teleportRandom(MobWorldView world, MobRandom rng) {
        return teleportRandomEvent(world, rng) != null;
    }

    public MobEvent.Teleported teleportRandomEvent(MobWorldView world, MobRandom rng) {
        if (isRidingBoat() || isRidingPlacedVehicle() || isMobPassenger()) return null;
        double fromX = x, fromY = y, fromZ = z;
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int tx = (int) Math.floor(x) + rng.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
            int tz = (int) Math.floor(z) + rng.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
            int startY = Math.min(Blocks.MAX_Y - 3,
                    (int) Math.floor(y) + rng.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS);
            for (int ty = startY; ty > Blocks.MIN_Y; ty--) {
                if (!world.isSolid(world.getBlock(tx, ty - 1, tz))) continue;
                if (safeTeleportCell(world, tx, ty, tz)) {
                    x = tx + 0.5;
                    y = ty;
                    z = tz + 0.5;
                    vy = 0.0;
                    return new MobEvent.Teleported(fromX, fromY, fromZ, x, y, z);
                }
                break;
            }
        }
        return null;
    }

    private static boolean safeTeleportCell(MobWorldView world, int x, int y, int z) {
        if (!TeleportSafety.withinBorder(x, z) || TeleportSafety.endermanAvoids(world, x, y - 1, z)) return false;
        for (int by = y; by < y + 3; by++) {
            short block = world.getBlock(x, by, z);
            if (world.isSolid(block) || isLiquid(block) || TeleportSafety.endermanAvoids(world, x, by, z)) return false;
        }
        return true;
    }

    private MobEvent blockMove(MobWorldView world, MobRandom rng) {
        if (pendingBlockChange) return null;
        if (carriedBlock == 0) {
            if (rng.nextInt(BLOCK_TAKE_ROLL) != 0) return null;
            int bx = (int) Math.floor(x) + rng.nextInt(4) - 2;
            int by = (int) Math.floor(y) + rng.nextInt(3);
            int bz = (int) Math.floor(z) + rng.nextInt(4) - 2;
            short block = world.getBlock(bx, by, bz);
            if (!holdable(block) || world.getBlock(bx, by + 1, bz) != Blocks.AIR
                    || !hasExposedSide(world, bx, by, bz)
                    || !world.hasLineOfSight(x, y + eyeHeight(), z,
                            bx + 0.5, by + 0.5, bz + 0.5)) return null;
            pendingCarriedBlock = block;
            pendingBlockChange = true;
            return new MobEvent.ChangeBlock(bx, by, bz, block, Blocks.AIR);
        }
        if (rng.nextInt(BLOCK_LEAVE_ROLL) != 0) return null;
        int bx = (int) Math.floor(x) + rng.nextInt(2) - 1;
        int by = (int) Math.floor(y) + rng.nextInt(2);
        int bz = (int) Math.floor(z) + rng.nextInt(2) - 1;
        if (world.getBlock(bx, by, bz) != Blocks.AIR
                || !world.isSolid(world.getBlock(bx, by - 1, bz))) return null;
        pendingCarriedBlock = 0;
        pendingBlockChange = true;
        return new MobEvent.ChangeBlock(bx, by, bz, Blocks.AIR, carriedBlock);
    }

    /** MobSystem이 expectedBlock CAS를 성공시킨 뒤에만 보유 상태를 확정한다. */
    public void commitBlockChange() {
        if (!pendingBlockChange) return;
        carriedBlock = pendingCarriedBlock;
        pendingBlockChange = false;
    }

    public void rejectBlockChange() {
        pendingBlockChange = false;
    }

    private static boolean hasExposedSide(MobWorldView world, int x, int y, int z) {
        return !world.isSolid(world.getBlock(x + 1, y, z))
                || !world.isSolid(world.getBlock(x - 1, y, z))
                || !world.isSolid(world.getBlock(x, y, z + 1))
                || !world.isSolid(world.getBlock(x, y, z - 1));
    }

    private static boolean holdable(short block) {
        return block == Blocks.CACTUS || block == Blocks.CLAY || block == Blocks.DIRT
                || block == Blocks.ROOTED_DIRT || block == Blocks.GRASS || block == Blocks.GRAVEL
                || block == Blocks.MOSS_BLOCK || block == Blocks.MUD || block == Blocks.MYCELIUM
                || block == Blocks.PODZOL || block == Blocks.PUMPKIN || block == Blocks.RED_SAND
                || block == Blocks.SAND || block == Blocks.TNT || block == Blocks.MUSHROOM_BROWN
                || block == Blocks.MUSHROOM_RED || block == Blocks.FLOWER_RED
                || block == Blocks.FLOWER_YELLOW || block == Blocks.COARSE_DIRT
                || block == Blocks.MUDDY_MANGROVE_ROOTS;
    }

    private static boolean isLiquid(short block) {
        return block >= Blocks.WATER_SOURCE && block <= Blocks.LAVA_SOURCE + 3;
    }
}
