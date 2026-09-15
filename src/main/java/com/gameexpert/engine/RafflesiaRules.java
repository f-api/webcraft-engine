package com.gameexpert.engine;

import com.gameexpert.engine.mob.MobType;
import com.gameexpert.terrain.Blocks;

/** Server-authoritative ecology for WebCraft's original parasitic Rafflesia. */
public final class RafflesiaRules {
    public static final long NO_POSITION = 0L;
    public static final int ACQUISITION_ROLL_BOUND = 32;
    public static final int SPREAD_ATTEMPTS = 8;
    public static final int ODOR_HORIZONTAL_RADIUS = 8;
    public static final int ODOR_VERTICAL_RADIUS = 3;
    public static final int ODOR_RESCAN_TICKS = 20;

    public enum OdorResponse { ATTRACT, REPEL, NONE }

    private RafflesiaRules() {
    }

    /** The flower consumes a damp, root-rich substrate rather than surviving on generic dirt. */
    public static boolean isHostSoil(int blockId) {
        return blockId == Blocks.ROOTED_DIRT || blockId == Blocks.PODZOL
                || blockId == Blocks.MUD || blockId == Blocks.MANGROVE_ROOTS
                || blockId == Blocks.MUDDY_MANGROVE_ROOTS
                || blockId == Blocks.MOSS_BLOCK;
    }

    /** A host tree must remain within two horizontal blocks of the flower's root cell. */
    public static boolean hasLivingHost(int x, int y, int z, SupportRules.BlockLookup blocks) {
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int block = blocks.getBlock(x + dx, y + dy, z + dz);
                    if (block == Blocks.MANGROVE_ROOTS || block == Blocks.MUDDY_MANGROVE_ROOTS
                            || BlockFamilies.isWoodLog(block) && !Blocks.isStrippedLog(block)
                            && !Blocks.isStrippedPoplarLog(block)
                            && !Blocks.isStrippedPaleOakLog(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isSupported(int x, int y, int z, SupportRules.BlockLookup blocks) {
        return isHostSoil(blocks.getBlock(x, y - 1, z))
                && hasLivingHost(x, y - 1, z, blocks);
    }

    /** Bone meal can very rarely reveal a dormant parasite only in a humid living root bed. */
    public static boolean canAcquireAt(int x, int y, int z, boolean humid,
            SupportRules.BlockLookup blocks) {
        return humid && y < Blocks.MAX_Y && blocks.getBlock(x, y + 1, z) == Blocks.AIR
                && isHostSoil(blocks.getBlock(x, y, z)) && hasLivingHost(x, y, z, blocks);
    }

    public static OdorResponse odorResponse(MobType type) {
        if (type == null) return OdorResponse.NONE;
        return switch (type) {
            case BEE, SPIDER, CAVE_SPIDER, CARRION_CROW, CARRION_STAG, CARRION_BOAR ->
                    OdorResponse.ATTRACT;
            default -> type.category() == com.gameexpert.engine.mob.MobCategory.CREATURE
                    ? OdorResponse.REPEL : OdorResponse.NONE;
        };
    }

    /**
     * Reference-only deterministic scan for rule tests. Production mob AI uses SpawnerIndex and
     * the allocation-free packed lookup exposed through MobWorldView.
     */
    public static int[] nearestFlower(double mobX, double mobY, double mobZ,
            SupportRules.BlockLookup blocks) {
        int originX = (int) Math.floor(mobX);
        int originY = (int) Math.floor(mobY);
        int originZ = (int) Math.floor(mobZ);
        int[] nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (int dy = -ODOR_VERTICAL_RADIUS; dy <= ODOR_VERTICAL_RADIUS; dy++) {
            int y = originY + dy;
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) continue;
            for (int dx = -ODOR_HORIZONTAL_RADIUS; dx <= ODOR_HORIZONTAL_RADIUS; dx++) {
                for (int dz = -ODOR_HORIZONTAL_RADIUS; dz <= ODOR_HORIZONTAL_RADIUS; dz++) {
                    if (blocks.getBlock(originX + dx, y, originZ + dz) != Blocks.RAFFLESIA) {
                        continue;
                    }
                    double distance = dx * (double) dx + dy * (double) dy + dz * (double) dz;
                    if (distance >= best) continue;
                    best = distance;
                    nearest = new int[] { originX + dx, y, originZ + dz };
                }
            }
        }
        return nearest;
    }

    /** 26/12/26 signed world position; zero is impossible for the supported world Y range. */
    public static long packPosition(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38
                | ((long) (y + 2048) & 0xfffL) << 26
                | ((long) z & 0x3ffffffL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38 << 6) >> 6;
    }

    public static int unpackY(long packed) {
        return (int) ((packed >>> 26) & 0xfffL) - 2048;
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 38 >> 38);
    }
}
