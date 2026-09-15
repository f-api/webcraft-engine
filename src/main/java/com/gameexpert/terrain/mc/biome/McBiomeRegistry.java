package com.gameexpert.terrain.mc.biome;

import com.gameexpert.terrain.mc.McTerrainDataPin;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** The exact 56-biome Overworld registry emitted by 26.3-snapshot-7. */
public final class McBiomeRegistry {
    public static final int DAPPLED_FOREST = 187;
    public static final int SULFUR_CAVES = 188;
    public static final int NO_COLOR = -1;
    private static final Biome[] BY_ID = new Biome[256];
    private static final int[] IDS = load();

    private McBiomeRegistry() { }

    private static int[] load() {
        try {
            byte[] compressed = Base64.getDecoder().decode(McBiomeRegistryData.GZIP_BASE64);
            byte[] raw;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                raw = gzip.readAllBytes();
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            if (!McBiomeRegistryData.RAW_SHA256.equals(hash)) {
                throw new IOException("26.3 biome registry SHA-256 mismatch: " + hash);
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
                if (input.readInt() != McBiomeRegistryData.MAGIC
                        || input.readUnsignedShort() != McBiomeRegistryData.VERSION) {
                    throw new IOException("unsupported 26.3 biome registry header");
                }
                int count = input.readUnsignedShort();
                if (count != McTerrainDataPin.OVERWORLD_BIOME_COUNT) {
                    throw new IOException("26.3 Overworld biome count mismatch: " + count);
                }
                int[] ids = new int[count];
                for (int i = 0; i < count; i++) ids[i] = readBiome(input);
                if (input.read() != -1) throw new IOException("trailing 26.3 biome payload bytes");
                return ids;
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static int readBiome(DataInputStream input) throws IOException {
        String name = input.readUTF();
        int id = input.readUnsignedShort();
        float temperature = input.readFloat();
        float downfall = input.readFloat();
        boolean frozen = input.readUnsignedByte() != 0;
        boolean precipitation = input.readUnsignedByte() != 0;
        int dryFoliage = input.readInt();
        int foliage = input.readInt();
        int grass = input.readInt();
        int modifierOrdinal = input.readUnsignedByte();
        if (modifierOrdinal >= Modifier.values().length) throw new IOException("bad grass modifier");
        int water = input.readInt();
        float spawnProbability = input.readFloat();
        List<SpawnEntry>[] spawns = new List[SpawnCategory.values().length];
        for (SpawnCategory category : SpawnCategory.values()) {
            int count = input.readUnsignedShort();
            if (count > 512) throw new IOException("too many biome spawn entries");
            SpawnEntry[] entries = new SpawnEntry[count];
            for (int i = 0; i < count; i++) {
                entries[i] = new SpawnEntry("minecraft:" + input.readUTF(),
                        input.readUnsignedShort(), input.readUnsignedShort(),
                        input.readUnsignedShort());
            }
            spawns[category.ordinal()] = List.of(entries);
        }
        if (id >= BY_ID.length || BY_ID[id] != null) throw new IOException("duplicate biome id " + id);
        BY_ID[id] = new Biome(id, "minecraft:" + name, temperature, downfall, frozen,
                precipitation, dryFoliage, foliage, grass, Modifier.values()[modifierOrdinal],
                water, spawnProbability, spawns);
        return id;
    }

    public static Biome get(int id) {
        if (id < 0 || id >= BY_ID.length || BY_ID[id] == null) {
            throw new IllegalArgumentException("26.3 climate table does not emit biome id " + id);
        }
        return BY_ID[id];
    }

    public static int[] ids() { return Arrays.copyOf(IDS, IDS.length); }

    /** Compatibility name retained for tests; runtime reads zero external JSON files. */
    public static int loadedJsonCount() { return 0; }

    public static boolean usesForestVegetation(int id) {
        return switch (id) {
            case 4, 21, 23, 27, 29, 34, 132, 155, 168, 175, 185, 186,
                    DAPPLED_FOREST -> true;
            default -> false;
        };
    }
    public static boolean usesTaigaVegetation(int id) {
        return switch (id) { case 5, 30, 32, 160, 178 -> true; default -> false; };
    }
    public static boolean usesSnowyPlainsVegetation(int id) { return id == 12 || id == 140; }
    public static boolean usesSavannaVegetation(int id) { return id == 35 || id == 36 || id == 163; }
    public static boolean usesPlainsVegetation(int id) {
        return switch (id) { case 1, 6, 7, 11, 129, 174, 177, 184 -> true; default -> false; };
    }
    public static boolean isMountainTerrain(int id) {
        return switch (id) { case 3, 131, 179, 180, 181, 182, 183 -> true; default -> false; };
    }
    public static boolean isDesertLike(int id) { return id == 2 || id == 37 || id == 38 || id == 165; }
    public static boolean isOcean(int id) {
        return switch (id) { case 0, 10, 24, 44, 45, 46, 48, 49, 50 -> true; default -> false; };
    }
    public static boolean isFrozenOcean(int id) { return id == 10 || id == 50; }
    public static boolean isWarmOcean(int id) { return id == 44; }
    public static boolean isWarmOceanFloor(int id) { return id == 44 || id == 45 || id == 48; }

    public enum Modifier {
        NONE,
        DARK_FOREST,
        SWAMP
    }

    /** Names are the exact natural-spawn category keys understood by the shared authority. */
    public enum SpawnCategory {
        MONSTER("monster"),
        CREATURE("creature"),
        AMBIENT("ambient"),
        WATER_CREATURE("water_creature"),
        WATER_AMBIENT("water_ambient"),
        UNDERGROUND_WATER_CREATURE("underground_water_creature"),
        AXOLOTLS("axolotls"),
        MISC("misc");

        private final String jsonName;

        SpawnCategory(String jsonName) {
            this.jsonName = jsonName;
        }
    }

    /** One immutable weighted entry copied verbatim from the pinned biome data. */
    public static final class SpawnEntry {
        private final String type;
        private final int weight;
        private final int minCount;
        private final int maxCount;

        private SpawnEntry(String type, int weight, int minCount, int maxCount) {
            this.type = type;
            this.weight = weight;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }

        public String type() { return type; }
        public int weight() { return weight; }
        public int minCount() { return minCount; }
        public int maxCount() { return maxCount; }
    }

    public static final class Biome {
        private final int id;
        private final String name;
        private final float temperature;
        private final float downfall;
        private final boolean frozenModifier;
        private final boolean precipitation;
        private final int dryFoliageColor;
        private final int foliageColor;
        private final int grassColor;
        private final Modifier grassModifier;
        private final int waterColor;
        private final float creatureSpawnProbability;
        private final List<SpawnEntry>[] spawnSettings;

        private Biome(int id, String name, float temperature, float downfall,
                boolean frozenModifier, boolean precipitation, int dryFoliageColor,
                int foliageColor, int grassColor, Modifier grassModifier, int waterColor,
                float creatureSpawnProbability, List<SpawnEntry>[] spawnSettings) {
            this.id = id;
            this.name = name;
            this.temperature = temperature;
            this.downfall = downfall;
            this.frozenModifier = frozenModifier;
            this.precipitation = precipitation;
            this.dryFoliageColor = dryFoliageColor;
            this.foliageColor = foliageColor;
            this.grassColor = grassColor;
            this.grassModifier = grassModifier;
            this.waterColor = waterColor;
            this.creatureSpawnProbability = creatureSpawnProbability;
            this.spawnSettings = spawnSettings;
        }

        public int id() { return id; }
        public String name() { return name; }
        public float temperature() { return temperature; }
        public float downfall() { return downfall; }
        public boolean frozenModifier() { return frozenModifier; }
        public boolean precipitation() { return precipitation; }
        public int dryFoliageColor() { return dryFoliageColor; }
        public int waterColor() { return waterColor; }
        public float creatureSpawnProbability() { return creatureSpawnProbability; }
        public Modifier grassModifier() { return grassModifier; }
        public List<SpawnEntry> spawns(SpawnCategory category) {
            return spawnSettings[category.ordinal()];
        }

        /** Explicit JSON RGB when present; otherwise the precise vanilla colour-map lookup. */
        public Tint grassTint() {
            return Tint.of(grassColor, temperature, downfall, grassModifier);
        }

        /** Explicit JSON RGB when present; otherwise the precise vanilla colour-map lookup. */
        public Tint foliageTint() {
            return Tint.of(foliageColor, temperature, downfall, Modifier.NONE);
        }
    }

    /**
     * An RGB override or a vanilla map coordinate.  An absent JSON override must be resolved
     * against the matching vanilla colour-map texel, not approximated from a hand-made gradient.
     */
    public static final class Tint {
        private final int rgb;
        private final int x;
        private final int y;
        private final Modifier modifier;

        private Tint(int rgb, int x, int y, Modifier modifier) {
            this.rgb = rgb;
            this.x = x;
            this.y = y;
            this.modifier = modifier;
        }

        private static Tint of(int rgb, float temperature, float downfall, Modifier modifier) {
            if (rgb != NO_COLOR) return new Tint(rgb, -1, -1, modifier);
            // Vanilla map formula: T=clamp(temp), D=clamp(downfall)*T,
            // x=floor((1-T)*255), y=floor((1-D)*255).  Preserve this order.
            double t = clamp(temperature);
            double d = clamp(downfall) * t;
            int x = (int) ((1.0D - t) * 255.0D);
            int y = (int) ((1.0D - d) * 255.0D);
            return new Tint(NO_COLOR, x, y, modifier);
        }

        private static double clamp(float value) {
            return Math.max(0.0D, Math.min(1.0D, (double) value));
        }

        public boolean hasRgbOverride() { return rgb != NO_COLOR; }
        public int rgbOverride() { return rgb; }
        public int colorMapX() { return x; }
        public int colorMapY() { return y; }
        public Modifier modifier() { return modifier; }
    }
}
