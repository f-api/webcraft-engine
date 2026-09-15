package com.gameexpert.terrain.mc.surface;

import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.McRandom;
import java.util.Arrays;

/** Vanilla 1.21.4 SurfaceSystem의 badlands terracotta-band palette다. */
final class McBadlandsSurface {
    private static final int BAND_COUNT = 192;
    private static final String TERRACOTTA = "minecraft:terracotta";
    private static final String ORANGE_TERRACOTTA = "minecraft:orange_terracotta";
    private static final String YELLOW_TERRACOTTA = "minecraft:yellow_terracotta";
    private static final String BROWN_TERRACOTTA = "minecraft:brown_terracotta";
    private static final String RED_TERRACOTTA = "minecraft:red_terracotta";
    private static final String WHITE_TERRACOTTA = "minecraft:white_terracotta";
    private static final String LIGHT_GRAY_TERRACOTTA = "minecraft:light_gray_terracotta";

    private final String[] bands;
    private final McNormalNoise clayBandsOffsetNoise;

    McBadlandsSurface(McRandom.PositionalFactory randomDeriver, McNormalNoise clayBandsOffsetNoise) {
        this.bands = createBands(randomDeriver.fromHashOf("minecraft:clay_bands"));
        this.clayBandsOffsetNoise = clayBandsOffsetNoise;
    }

    String stateAt(int blockX, int blockY, int blockZ) {
        int offset = (int) Math.round(clayBandsOffsetNoise.getValue(blockX, 0.0, blockZ) * 4.0);
        return bands[Math.floorMod(blockY + offset, BAND_COUNT)];
    }

    private static String[] createBands(McRandom random) {
        String[] result = new String[BAND_COUNT];
        Arrays.fill(result, TERRACOTTA);

        int index = 0;
        while (index < result.length) {
            index += random.nextInt(5) + 1;
            if (index >= result.length) break;
            result[index] = ORANGE_TERRACOTTA;
            index++;
        }

        addBands(random, result, 1, YELLOW_TERRACOTTA);
        addBands(random, result, 2, BROWN_TERRACOTTA);
        addBands(random, result, 1, RED_TERRACOTTA);

        int bandCount = nextIntInclusive(random, 9, 15);
        int currentBand = 0;
        index = 0;
        while (currentBand < bandCount && index < result.length) {
            result[index] = WHITE_TERRACOTTA;
            if (index > 1 && random.nextBoolean()) result[index - 1] = LIGHT_GRAY_TERRACOTTA;
            if (index + 1 < result.length && random.nextBoolean()) {
                result[index + 1] = LIGHT_GRAY_TERRACOTTA;
            }
            index += random.nextInt(16) + 4;
            currentBand++;
        }
        return result;
    }

    private static void addBands(McRandom random, String[] bands, int minBandSize, String state) {
        int bandCount = nextIntInclusive(random, 6, 15);
        for (int i = 0; i < bandCount; i++) {
            int width = minBandSize + random.nextInt(3);
            int start = random.nextInt(bands.length);
            for (int offset = 0; offset < width && start + offset < bands.length; offset++) {
                bands[start + offset] = state;
            }
        }
    }

    private static int nextIntInclusive(McRandom random, int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}
