package com.gameexpert.engine.persistence.finalcarrier.loot;

import java.util.Set;
import com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess;
import com.gameexpert.terrain.mc.surface.McBiomeZoom;

/** 26.3-rc-3 MapItem.renderBiomePreviewMap: biome relief at overworld sea level (63).
 * Kept outside the snapshot-7 terrain producer so persisted target receipts remain immutable. */
public final class SeaLevelExplorerMapPreview {
    private static final int ORANGE = 15, BROWN = 26, LOW = 0, NORMAL = 1, HIGH = 2, LOWEST = 3;
    private static final float[] SIN = new float[65536];
    static { for (int i = 0; i < SIN.length; i++) SIN[i] = (float)Math.sin(i * Math.PI * 2.0D / 65536.0D); }
    private static final Set<String> WATER = Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean",
      "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
      "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:river", "minecraft:frozen_river",
      "minecraft:swamp", "minecraft:mangrove_swamp");
    private SeaLevelExplorerMapPreview() {}
    public static byte[] render(long seed, int centerX, int centerZ, int scale) {
        return render(Mc263StructureWorldAccess.overworld(seed), centerX, centerZ, scale);
    }
    static byte[] render(Mc263StructureWorldAccess world, int centerX, int centerZ, int scale) {
            if (scale < 0 || scale > 4) {
                throw new IllegalArgumentException("map preview scale drift");
            }
            int factor = 1 << scale;
            int baseX = centerX / factor - 64;
            int baseZ = centerZ / factor - 64;
            long zoomSeed = McBiomeZoom.zoomSeed(world.worldSeed());
            byte[] colors = new byte[16384];
            boolean[] watery = new boolean[16384];
            for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
                for (int pixelX = 0; pixelX < 128; pixelX++) {
                    watery[pixelX + pixelZ * 128] = watery(world, zoomSeed,
                            Math.multiplyExact(baseX + pixelX, factor),
                            Math.multiplyExact(baseZ + pixelZ, factor));
                }
            }
            for (int pixelZ = 1; pixelZ < 127; pixelZ++) {
                for (int pixelX = 1; pixelX < 127; pixelX++) {
                    int wateryNeighbors = 0;
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                            if (deltaX == 0 && deltaZ == 0) continue;
                            if (watery[pixelX + deltaX + (pixelZ + deltaZ) * 128]) {
                                wateryNeighbors++;
                            }
                        }
                    }
                    boolean centerWatery = watery[pixelX + pixelZ * 128];
                    int color = -1;
                    int brightness = LOWEST;
                    if (centerWatery) {
                        color = ORANGE;
                        if (wateryNeighbors > 7) {
                            int waveColor = fullWaterWaveColor(pixelX, pixelZ);
                            if (waveColor >= 0) {
                                color = waveColor >> 2;
                                brightness = waveColor & 3;
                            } else {
                                color = -1;
                            }
                        } else if (wateryNeighbors > 5) {
                            brightness = NORMAL;
                        } else if (wateryNeighbors > 3) {
                            brightness = LOW;
                        } else if (wateryNeighbors > 1) {
                            brightness = LOW;
                        }
                    } else if (wateryNeighbors > 0) {
                        color = BROWN;
                        brightness = wateryNeighbors > 3 ? NORMAL : LOWEST;
                    }
                    if (color >= 0) {
                        colors[pixelX + pixelZ * 128] =
                                (byte) (color << 2 | brightness & 3);
                    }
                }
            }
            return colors;
    }
    private static boolean watery(Mc263StructureWorldAccess world, long zoomSeed, int x, int z) {
        int corner = McBiomeZoom.corner(zoomSeed, x, 63, z);
        return WATER.contains(world.biomeAtQuart(((x - 2) >> 2) + (corner >> 2 & 1),
          ((63 - 2) >> 2) + (corner >> 1 & 1), ((z - 2) >> 2) + (corner & 1)));
    }
    private static int fullWaterWaveColor(int x, int z) {
        if (z % 2 != 0) return -1;
        float sin = SIN[(int)((long)(z * 10430.378350470453D) & 65535L)];
        int wave = (x + (int)(sin * 7.0D)) / 8 % 5;
        int brightness = switch(wave) { case 0,4 -> LOW; case 1,3 -> NORMAL; case 2 -> HIGH; default -> throw new AssertionError(wave); };
        return ORANGE << 2 | brightness & 3;
    }
}
