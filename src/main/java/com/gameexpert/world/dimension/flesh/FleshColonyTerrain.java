package com.gameexpert.world.dimension.flesh;

import com.gameexpert.engine.FleshNetherRules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.dimension.DimensionChunk;

/** Converts the colony's anatomical layout to append-only block IDs, outside the overworld pipeline. */
public final class FleshColonyTerrain {
    private FleshColonyTerrain() { }

    public static int block(FleshColonyLayout.TissueKind tissue) {
        return switch (tissue) {
            case AIR -> Blocks.AIR;
            case FLESH -> Blocks.FLESH_BLOCK;
            case MYOCARDIUM -> Blocks.FLESH_HEART;
            case ARTERY -> Blocks.FLESH_ARTERY;
            case ANCHOR -> Blocks.FLESH_ANCHOR;
            case FAT -> Blocks.FLESH_FAT_SAC;
            case MEMBRANE -> Blocks.FLESH_MEMBRANE_BLOCK;
            case BONE -> Blocks.FLESH_BONE_SPUR;
            case NECROSIS -> Blocks.FLESH_NECROSIS;
            case CORE -> Blocks.HEART_CORE;
            case NORMAL_COCOON -> Blocks.FLESH_COCOON;
            case LARGE_COCOON -> Blocks.FLESH_LARGE_COCOON;
        };
    }

    /** Low two bits hold maturity; bits 2..3 hold the bottom/middle/top segment. */
    public static int state(FleshColonyLayout.Layout layout, int x, int y, int z) {
        var sample = layout.sample(x, y, z);
        if (sample.kind() != FleshColonyLayout.TissueKind.LARGE_COCOON) return sample.stage();
        int segment = 0;
        while (segment < 2 && layout.sample(x, y - segment - 1, z).kind()
                == FleshColonyLayout.TissueKind.LARGE_COCOON) segment++;
        return sample.stage() | segment << 2;
    }

    /** Packed block/state for durable-source validation without creating a chunk. */
    public static int cell(int seed, int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return Blocks.AIR;
        if (y <= FleshNetherRules.FLOOR_Y) return Blocks.ABYSS_STONE;
        if (z == 8 && x >= 7 && x <= 10 && y <= 68) {
            return x == 7 || x == 10 || y == 64 || y == 68 ? Blocks.OBSIDIAN : Blocks.NETHER_PORTAL;
        }
        var layout = FleshColonyLayout.forCoordinate(seed, x, z);
        var sample = layout.sample(x, y, z);
        int state = sample.kind() == FleshColonyLayout.TissueKind.LARGE_COCOON
                ? state(layout, x, y, z) : sample.stage();
        return block(sample.kind()) | state << 16;
    }

    public static DimensionChunk generate(int seed, int cx, int cz) {
        int minX = Math.multiplyExact(cx, 16), minZ = Math.multiplyExact(cz, 16);
        Math.addExact(minX, 15); Math.addExact(minZ, 15);
        var layout = FleshColonyLayout.forCoordinate(seed, minX, minZ);
        short[] blocks = new short[Blocks.CHUNK_BLOCKS];
        byte[] states = new byte[blocks.length];
        java.util.Arrays.fill(blocks, 0, (FleshNetherRules.FLOOR_Y - Blocks.MIN_Y + 1) * 256,
                (short) Blocks.ABYSS_STONE);
        // 16-block chunks do not straddle a 256-block ownership boundary, including negative coordinates.
        int maxY = Math.max(68, layout.bounds().maxY());
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int wx = minX + x, wz = minZ + z;
            for (int y = 64; y <= maxY; y++) {
                int index = Blocks.blockIndex(x, y, z);
                if (wz == 8 && wx >= 7 && wx <= 10 && y <= 68) {
                    blocks[index] = (short) (wx == 7 || wx == 10 || y == 64 || y == 68
                            ? Blocks.OBSIDIAN : Blocks.NETHER_PORTAL);
                } else {
                    var sample = layout.sample(wx, y, wz);
                    blocks[index] = (short) block(sample.kind());
                    states[index] = (byte) (sample.kind() == FleshColonyLayout.TissueKind.LARGE_COCOON
                            ? state(layout, wx, y, wz) : sample.stage());
                }
            }
        }
        return new DimensionChunk(blocks, states);
    }
}
