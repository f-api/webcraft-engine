package com.gameexpert.terrain.mc.ore;

import static com.gameexpert.terrain.Blocks.COPPER_ORE;
import static com.gameexpert.terrain.Blocks.DEEPSLATE_IRON_ORE;
import static com.gameexpert.terrain.Blocks.GRANITE;
import static com.gameexpert.terrain.Blocks.MAX_Y;
import static com.gameexpert.terrain.Blocks.MIN_Y;
import static com.gameexpert.terrain.Blocks.RAW_COPPER_BLOCK;
import static com.gameexpert.terrain.Blocks.RAW_IRON_BLOCK;
import static com.gameexpert.terrain.Blocks.STONE;
import static com.gameexpert.terrain.Blocks.TUFF;

import com.gameexpert.terrain.mc.Mc263NoiseRegistry;
import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.McRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime projection of the pinned Overworld density-stage {@code ore_veins} material rules.
 *
 * <p>The three interpolated fields use the same 4x8x4 NoiseChunk cell lattice as final density.
 * The gap field is deliberately sampled at the block coordinate. Each rule receives a fresh
 * coordinate-derived {@code minecraft:ore} random stream, matching the data-driven sequence's
 * independent filler functions.</p>
 */
public final class Mc263OreMaterialRule {
    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 8;
    private static final int NO_MATERIAL = -1;
    private static final int RESOURCE_CACHE_LIMIT = 8;
    private static final Map<Long, Resources> RESOURCES = new LinkedHashMap<>(16, 0.75F, true);

    private final NoiseField toggle;
    private final NoiseField veinA;
    private final NoiseField veinB;
    private final Resources resources;

    public Mc263OreMaterialRule(long seed) {
        resources = resources(seed);
        toggle = new NoiseField(resources.toggle,
                Mc263OreVeinifier.TOGGLE_XZ_SCALE, Mc263OreVeinifier.TOGGLE_Y_SCALE, 0.0F);
        veinA = new NoiseField(resources.veinA,
                Mc263OreVeinifier.MASK_NOISE_XZ_SCALE, Mc263OreVeinifier.MASK_NOISE_Y_SCALE, 1.0F);
        veinB = new NoiseField(resources.veinB,
                Mc263OreVeinifier.MASK_NOISE_XZ_SCALE, Mc263OreVeinifier.MASK_NOISE_Y_SCALE, 1.0F);
    }

    /** Applies copper, then iron, then the Overworld default block to an aquifer-solid cell. */
    public int blockForSolid(int x, int y, int z) {
        int copper = materialFor(Mc263OreVeinifier.VeinType.COPPER, x, y, z);
        if (copper != NO_MATERIAL) return copper;
        int iron = materialFor(Mc263OreVeinifier.VeinType.IRON, x, y, z);
        return iron == NO_MATERIAL ? STONE : iron;
    }

    private int materialFor(Mc263OreVeinifier.VeinType type, int x, int y, int z) {
        if (y < type.minYInclusive() || y >= type.maxYExclusive()) return NO_MATERIAL;

        float toggleValue = toggle.at(x, y, z);
        McRandom random = resources.oreRandom.at(x, y, z);
        Mc263OreVeinifier.Result result = Mc263OreVeinifier.decide(type, y, toggleValue,
                () -> veinA.at(x, y, z), () -> veinB.at(x, y, z),
                () -> (float) resources.gap.getValue(
                        x * (double) Mc263OreVeinifier.GAP_NOISE_XZ_SCALE,
                        y * (double) Mc263OreVeinifier.GAP_NOISE_Y_SCALE,
                        z * (double) Mc263OreVeinifier.GAP_NOISE_XZ_SCALE),
                random::nextFloat);
        return switch (result) {
            case NONE -> NO_MATERIAL;
            case FILLER -> type == Mc263OreVeinifier.VeinType.COPPER ? GRANITE : TUFF;
            case ORE -> type == Mc263OreVeinifier.VeinType.COPPER
                    ? COPPER_ORE : DEEPSLATE_IRON_ORE;
            case RAW_ORE -> type == Mc263OreVeinifier.VeinType.COPPER
                    ? RAW_COPPER_BLOCK : RAW_IRON_BLOCK;
        };
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static Resources resources(long seed) {
        synchronized (RESOURCES) {
            Resources cached = RESOURCES.get(seed);
            if (cached != null) return cached;
            McRandom.PositionalFactory root = new McRandom(seed).forkPositional();
            Resources created = new Resources(
                    Mc263NoiseRegistry.create(root, "minecraft:ore_veininess"),
                    Mc263NoiseRegistry.create(root, "minecraft:ore_vein_a"),
                    Mc263NoiseRegistry.create(root, "minecraft:ore_vein_b"),
                    Mc263NoiseRegistry.create(root, "minecraft:ore_gap"),
                    root.fromHashOf("minecraft:ore").forkPositional());
            RESOURCES.put(seed, created);
            if (RESOURCES.size() > RESOURCE_CACHE_LIMIT) {
                var iterator = RESOURCES.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            return created;
        }
    }

    private record Resources(McNormalNoise toggle, McNormalNoise veinA, McNormalNoise veinB,
            McNormalNoise gap, McRandom.PositionalFactory oreRandom) {
    }

    /** One interpolated density-function field with chunk-local lattice-column reuse. */
    private static final class NoiseField {
        private final McNormalNoise noise;
        private final float xzScale;
        private final float yScale;
        private final float outsideValue;
        private final Map<Long, float[]> columns = new HashMap<>();

        private NoiseField(McNormalNoise noise, float xzScale, float yScale, float outsideValue) {
            this.noise = noise;
            this.xzScale = xzScale;
            this.yScale = yScale;
            this.outsideValue = outsideValue;
        }

        private float at(int x, int y, int z) {
            int x0 = Math.floorDiv(x, CELL_WIDTH) * CELL_WIDTH;
            int z0 = Math.floorDiv(z, CELL_WIDTH) * CELL_WIDTH;
            int yIndex = Math.floorDiv(y - MIN_Y, CELL_HEIGHT);
            int nextY = Math.min(yIndex + 1, sampleCount() - 1);
            float deltaX = Math.floorMod(x, CELL_WIDTH) / (float) CELL_WIDTH;
            float deltaY = Math.floorMod(y - MIN_Y, CELL_HEIGHT) / (float) CELL_HEIGHT;
            float deltaZ = Math.floorMod(z, CELL_WIDTH) / (float) CELL_WIDTH;

            float[] c00 = column(x0, z0);
            float[] c10 = column(x0 + CELL_WIDTH, z0);
            float[] c01 = column(x0, z0 + CELL_WIDTH);
            float[] c11 = column(x0 + CELL_WIDTH, z0 + CELL_WIDTH);
            float x00 = lerp(deltaX, c00[yIndex], c10[yIndex]);
            float x10 = lerp(deltaX, c00[nextY], c10[nextY]);
            float x01 = lerp(deltaX, c01[yIndex], c11[yIndex]);
            float x11 = lerp(deltaX, c01[nextY], c11[nextY]);
            return lerp(deltaZ, lerp(deltaY, x00, x10), lerp(deltaY, x01, x11));
        }

        private float[] column(int x, int z) {
            long key = ((long) x << 32) ^ (z & 0xffff_ffffL);
            return columns.computeIfAbsent(key, ignored -> {
                float[] samples = new float[sampleCount()];
                for (int index = 0; index < samples.length; index++) {
                    int y = MIN_Y + index * CELL_HEIGHT;
                    samples[index] = y >= Mc263OreVeinifier.TOGGLE_NOISE_MIN_Y_INCLUSIVE
                            && y < Mc263OreVeinifier.TOGGLE_NOISE_MAX_Y_EXCLUSIVE
                            ? (float) noise.getValue(x * (double) xzScale,
                                    y * (double) yScale, z * (double) xzScale)
                            : outsideValue;
                }
                return samples;
            });
        }

        private static int sampleCount() {
            return (MAX_Y + 1 - MIN_Y) / CELL_HEIGHT + 1;
        }
    }
}
