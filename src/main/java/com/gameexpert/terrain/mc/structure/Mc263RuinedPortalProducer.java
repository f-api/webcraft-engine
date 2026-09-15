package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact start producer for the six pinned 26.3 Overworld ruined-portal structures. */
public final class Mc263RuinedPortalProducer {
    public static final String ORACLE_SHA256 = Mc263RuinedPortalGrammarData.ORACLE_SHA256;
    public static final String ORACLE_CONTRACT_SHA256 = Mc263RuinedPortalGrammarData.CONTRACT_SHA256;
    public static final String ORACLE_SOURCE_SHA256 = Mc263RuinedPortalGrammarData.SOURCE_SHA256;
    public static final String LOOT_TABLE = "minecraft:chests/ruined_portal";
    public static final int GENERATION_STEP = 4;

    private static final List<String> KEYS = List.of(
            "minecraft:ruined_portal", "minecraft:ruined_portal_desert",
            "minecraft:ruined_portal_jungle", "minecraft:ruined_portal_mountain",
            "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_swamp");
    private static final List<String> NORMAL = java.util.stream.IntStream.rangeClosed(1, 10)
            .mapToObj(i -> "minecraft:ruined_portal/portal_" + i).toList();
    private static final List<String> GIANT = java.util.stream.IntStream.rangeClosed(1, 3)
            .mapToObj(i -> "minecraft:ruined_portal/giant_portal_" + i).toList();

    private Mc263RuinedPortalProducer() { }

    public enum VerticalPlacement {
        ON_LAND_SURFACE("on_land_surface", Heightmap.WORLD_SURFACE_WG),
        PARTLY_BURIED("partly_buried", Heightmap.WORLD_SURFACE_WG),
        ON_OCEAN_FLOOR("on_ocean_floor", Heightmap.OCEAN_FLOOR_WG),
        IN_MOUNTAIN("in_mountain", Heightmap.WORLD_SURFACE_WG),
        UNDERGROUND("underground", Heightmap.WORLD_SURFACE_WG);

        private final String persisted;
        private final Heightmap heightmap;
        VerticalPlacement(String persisted, Heightmap heightmap) {
            this.persisted = persisted; this.heightmap = heightmap;
        }
        public String persisted() { return persisted; }
        public Heightmap heightmap() { return heightmap; }
    }
    public enum Heightmap { WORLD_SURFACE_WG, OCEAN_FLOOR_WG }
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum Mirror { NONE, FRONT_BACK }

    public record Pos(int x, int y, int z) { }
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted ruined-portal box");
            }
        }
        public Pos center() {
            return new Pos(Math.floorDiv(minX + maxX, 2), Math.floorDiv(minY + maxY, 2),
                    Math.floorDiv(minZ + maxZ, 2));
        }
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
        public boolean intersectsChunk(int chunkX, int chunkZ) {
            long x = (long) chunkX * 16L, z = (long) chunkZ * 16L;
            return maxX >= x && minX <= x + 15L && maxZ >= z && minZ <= z + 15L;
        }
    }

    public record Setup(VerticalPlacement placement, float airPocketProbability,
            float mossiness, boolean overgrown, boolean vines, boolean canBeCold,
            float weight) { }
    public record Properties(boolean cold, float mossiness, boolean airPocket,
            boolean overgrown, boolean vines) { }

    /** Read-only projection boundary used before any persisted or world mutation is possible. */
    public interface Projection {
        boolean supportsHeightmap(Heightmap heightmap);
        boolean supportsColumnState();
        boolean supportsColdness();
        int minY();
        int seaLevel();
        int height(Heightmap heightmap, int x, int z);
        String blockState(int x, int y, int z);
        boolean opaque(Heightmap heightmap, String exactState);
        boolean coldEnoughToSnow(int x, int y, int z, int seaLevel);
    }

    public record GenerationRandom(long state48, int worldgenCount,
            long[] continuationNextLong) {
        public GenerationRandom { continuationNextLong = continuationNextLong.clone(); }
        @Override public long[] continuationNextLong() { return continuationNextLong.clone(); }
        public byte[] canonicalBytes() {
            ByteBuffer result = ByteBuffer.allocate(8 + 4 + continuationNextLong.length * 8);
            result.putLong(state48).putInt(worldgenCount);
            for (long value : continuationNextLong) result.putLong(value);
            return result.array();
        }
    }

    public record Plan(String structureKey, long worldSeed, int chunkX, int chunkZ,
            int stepLocalIndex, Setup setup, Properties properties, String template,
            Rotation rotation, Mirror mirror, Pos pivot, Pos origin, Box boundingBox,
            Mc263RuinedPortalGrammarData.Grammar grammar, byte[] pieceNbt,
            byte[] structureStartNbt, GenerationRandom generationRandom) {
        public Plan {
            pieceNbt = pieceNbt.clone(); structureStartNbt = structureStartNbt.clone();
        }
        @Override public byte[] pieceNbt() { return pieceNbt.clone(); }
        @Override public byte[] structureStartNbt() { return structureStartNbt.clone(); }
    }

    public static Plan generate(String structureKey, long worldSeed, int chunkX, int chunkZ,
            int stepLocalIndex, Projection projection) {
        requireKey(structureKey);
        Objects.requireNonNull(projection, "ruined-portal projection");
        if (stepLocalIndex < 0) throw new IllegalArgumentException("negative step-local index");
        for (Heightmap heightmap : Heightmap.values()) {
            if (!projection.supportsHeightmap(heightmap)) {
                throw new UnsupportedOperationException("missing ruined-portal heightmap: " + heightmap);
            }
        }
        if (!projection.supportsColumnState() || !projection.supportsColdness()) {
            throw new UnsupportedOperationException("complete ruined-portal projection is required");
        }
        int minY = projection.minY();
        if (minY > 0 || projection.seaLevel() <= minY) {
            throw new IllegalArgumentException("invalid ruined-portal height domain");
        }

        Legacy48 random = featureRandom(worldSeed, chunkX, chunkZ);
        Setup setup = chooseSetup(structureKey, random);
        boolean airPocket = sample(random, setup.airPocketProbability());
        List<String> templates = random.nextFloat() < 0.05F ? GIANT : NORMAL;
        String template = templates.get(random.nextInt(templates.size()));
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        Mirror mirror = random.nextFloat() < 0.5F ? Mirror.NONE : Mirror.FRONT_BACK;
        Mc263RuinedPortalGrammarData.Grammar grammar =
                Mc263RuinedPortalGrammarData.require(template);
        Pos pivot = new Pos(grammar.sizeX() / 2, 0, grammar.sizeZ() / 2);
        int baseX = Math.multiplyExact(chunkX, 16), baseZ = Math.multiplyExact(chunkZ, 16);
        Box zero = transformedBox(baseX, 0, baseZ, grammar, pivot, rotation, mirror);
        Pos center = zero.center();
        int surfaceY = Math.subtractExact(
                projection.height(setup.placement().heightmap(), center.x(), center.z()), 1);
        int initialY = initialY(random, setup.placement(), airPocket, surfaceY,
                grammar.sizeY(), minY);
        int projectedY = groundedY(projection, setup.placement(), zero, initialY, minY);
        Pos origin = new Pos(baseX, projectedY, baseZ);
        Box box = transformedBox(baseX, projectedY, baseZ, grammar, pivot, rotation, mirror);
        boolean cold = setup.canBeCold() && projection.coldEnoughToSnow(
                origin.x(), origin.y(), origin.z(), projection.seaLevel());
        Properties properties = new Properties(cold, setup.mossiness(), airPocket,
                setup.overgrown(), setup.vines());
        byte[] pieceNbt = pieceNbt(template, rotation, mirror, setup.placement(), properties,
                origin, box);
        byte[] startNbt = startNbt(structureKey, chunkX, chunkZ, pieceNbt);
        return new Plan(structureKey, worldSeed, chunkX, chunkZ, stepLocalIndex, setup,
                properties, template, rotation, mirror, pivot, origin, box, grammar,
                pieceNbt, startNbt, random.receipt());
    }

    private static Setup chooseSetup(String key, Legacy48 random) {
        List<Setup> setups = setups(key);
        if (setups.size() == 1) return setups.get(0);
        float total = 0F;
        for (Setup setup : setups) total += setup.weight();
        float choice = random.nextFloat();
        for (Setup setup : setups) {
            choice -= setup.weight() / total;
            if (choice < 0F) return setup;
        }
        throw new IllegalStateException("ruined-portal setup selection exhausted");
    }

    private static List<Setup> setups(String key) {
        return switch (key) {
            case "minecraft:ruined_portal" -> List.of(
                    new Setup(VerticalPlacement.UNDERGROUND, 1F, .2F, false, false, true, .5F),
                    new Setup(VerticalPlacement.ON_LAND_SURFACE, 0F, .2F, false, false, true, .5F));
            case "minecraft:ruined_portal_desert" -> List.of(
                    new Setup(VerticalPlacement.PARTLY_BURIED, 0F, 0F, false, false, false, 1F));
            case "minecraft:ruined_portal_jungle" -> List.of(
                    new Setup(VerticalPlacement.ON_LAND_SURFACE, 0F, .8F, true, true, false, 1F));
            case "minecraft:ruined_portal_mountain" -> List.of(
                    new Setup(VerticalPlacement.IN_MOUNTAIN, 1F, .2F, false, false, true, .5F),
                    new Setup(VerticalPlacement.ON_LAND_SURFACE, 0F, .2F, false, false, true, .5F));
            case "minecraft:ruined_portal_ocean" -> List.of(
                    new Setup(VerticalPlacement.ON_OCEAN_FLOOR, 0F, .8F, false, false, true, 1F));
            case "minecraft:ruined_portal_swamp" -> List.of(
                    new Setup(VerticalPlacement.ON_OCEAN_FLOOR, 0F, .5F, false, true, false, 1F));
            default -> throw new IllegalArgumentException("unsupported ruined-portal key: " + key);
        };
    }

    private static boolean sample(Legacy48 random, float probability) {
        if (probability == 0F) return false;
        if (probability == 1F) return true;
        return random.nextFloat() < probability;
    }

    private static int initialY(Legacy48 random, VerticalPlacement placement,
            boolean airPocket, int surfaceY, int ySpan, int minWorldY) {
        int minY = Math.addExact(minWorldY, 15);
        return switch (placement) {
            case IN_MOUNTAIN -> randomWithin(random, 70, surfaceY - ySpan);
            case UNDERGROUND -> randomWithin(random, minY, surfaceY - ySpan);
            case PARTLY_BURIED -> surfaceY - ySpan + between(random, 2, 8);
            case ON_OCEAN_FLOOR, ON_LAND_SURFACE -> surfaceY;
        };
    }

    private static int groundedY(Projection projection, VerticalPlacement placement,
            Box zero, int initialY, int minWorldY) {
        int minimum = Math.addExact(minWorldY, 15);
        int[][] corners = {{zero.minX(), zero.minZ()}, {zero.maxX(), zero.minZ()},
                {zero.minX(), zero.maxZ()}, {zero.maxX(), zero.maxZ()}};
        Heightmap heightmap = placement.heightmap();
        for (int y = initialY; y > minimum; y--) {
            int solid = 0;
            for (int[] corner : corners) {
                String state = projection.blockState(corner[0], y, corner[1]);
                if (projection.opaque(heightmap, state) && ++solid == 3) return y;
            }
        }
        return minimum;
    }

    public static Pos transform(Pos local, Pos pivot, Rotation rotation, Mirror mirror) {
        int x = local.x(), z = local.z();
        if (mirror == Mirror.FRONT_BACK) x = -x;
        return switch (rotation) {
            case NONE -> new Pos(x, local.y(), z);
            case CLOCKWISE_180 -> new Pos(pivot.x() * 2 - x, local.y(), pivot.z() * 2 - z);
            case COUNTERCLOCKWISE_90 -> new Pos(pivot.x() - pivot.z() + z, local.y(),
                    pivot.x() + pivot.z() - x);
            case CLOCKWISE_90 -> new Pos(pivot.x() + pivot.z() - z, local.y(),
                    pivot.z() - pivot.x() + x);
        };
    }

    private static Box transformedBox(int originX, int originY, int originZ,
            Mc263RuinedPortalGrammarData.Grammar grammar, Pos pivot, Rotation rotation,
            Mirror mirror) {
        Pos a = transform(new Pos(0, 0, 0), pivot, rotation, mirror);
        Pos b = transform(new Pos(grammar.sizeX() - 1, grammar.sizeY() - 1,
                grammar.sizeZ() - 1), pivot, rotation, mirror);
        return new Box(Math.addExact(originX, Math.min(a.x(), b.x())), originY,
                Math.addExact(originZ, Math.min(a.z(), b.z())),
                Math.addExact(originX, Math.max(a.x(), b.x())),
                Math.addExact(originY, grammar.sizeY() - 1),
                Math.addExact(originZ, Math.max(a.z(), b.z())));
    }

    private static int randomWithin(Legacy48 random, int preferredMin, int max) {
        return preferredMin < max ? between(random, preferredMin, max) : max;
    }
    private static int between(Legacy48 random, int min, int max) {
        return Math.addExact(min, random.nextInt(Math.addExact(Math.subtractExact(max, min), 1)));
    }

    private static Legacy48 featureRandom(long seed, int chunkX, int chunkZ) {
        Legacy48 root = new Legacy48(seed);
        long x = root.nextLong(), z = root.nextLong();
        return new Legacy48((long) chunkX * x ^ (long) chunkZ * z ^ seed, root.count);
    }

    private static byte[] pieceNbt(String template, Rotation rotation, Mirror mirror,
            VerticalPlacement placement, Properties properties, Pos origin, Box box) {
        return nbt(out -> {
            compoundRoot(out);
            ints(out, "BB", box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            string(out, "VerticalPlacement", placement.persisted());
            string(out, "id", "minecraft:rupo");
            integer(out, "TPY", origin.y());
            compound(out, "Properties");
            bool(out, "overgrown", properties.overgrown());
            floating(out, "mossiness", properties.mossiness());
            bool(out, "replace_with_blackstone", false);
            bool(out, "vines", properties.vines());
            bool(out, "cold", properties.cold());
            bool(out, "air_pocket", properties.airPocket());
            out.writeByte(0);
            integer(out, "GD", 0); integer(out, "TPX", origin.x());
            string(out, "Rotation", rotation.name()); string(out, "Mirror", mirror.name());
            integer(out, "O", 2); integer(out, "TPZ", origin.z());
            string(out, "Template", template); out.writeByte(0);
        });
    }

    private static byte[] startNbt(String key, int chunkX, int chunkZ, byte[] piece) {
        return nbt(out -> {
            compoundRoot(out); integer(out, "references", 0); integer(out, "ChunkZ", chunkZ);
            string(out, "id", key); out.writeByte(9); out.writeUTF("Children");
            out.writeByte(10); out.writeInt(1); out.write(piece, 3, piece.length - 3);
            integer(out, "ChunkX", chunkX); out.writeByte(0);
        });
    }

    private static byte[] nbt(NbtWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes); writer.write(out); out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void compoundRoot(DataOutputStream out) throws IOException { out.writeByte(10); out.writeUTF(""); }
    private static void compound(DataOutputStream out, String name) throws IOException { out.writeByte(10); out.writeUTF(name); }
    private static void integer(DataOutputStream out, String name, int value) throws IOException { out.writeByte(3); out.writeUTF(name); out.writeInt(value); }
    private static void floating(DataOutputStream out, String name, float value) throws IOException { out.writeByte(5); out.writeUTF(name); out.writeFloat(value); }
    private static void bool(DataOutputStream out, String name, boolean value) throws IOException { out.writeByte(1); out.writeUTF(name); out.writeByte(value ? 1 : 0); }
    private static void string(DataOutputStream out, String name, String value) throws IOException { out.writeByte(8); out.writeUTF(name); out.writeUTF(value); }
    private static void ints(DataOutputStream out, String name, int... values) throws IOException { out.writeByte(11); out.writeUTF(name); out.writeInt(values.length); for (int value : values) out.writeInt(value); }
    @FunctionalInterface private interface NbtWriter { void write(DataOutputStream out) throws IOException; }

    private static void requireKey(String key) {
        if (!KEYS.contains(key)) throw new IllegalArgumentException("unsupported ruined-portal key: " + key);
    }

    private static final class Legacy48 {
        private static final long MULTIPLIER = 0x5deece66dL, ADDEND = 0xbL;
        private static final long MASK = (1L << 48) - 1;
        private long state; private int count;
        Legacy48(long seed) { this(seed, 0); }
        Legacy48(long seed, int priorCount) { state = (seed ^ MULTIPLIER) & MASK; count = priorCount; }
        int next(int bits) { count++; state = (state * MULTIPLIER + ADDEND) & MASK; return (int) (state >>> (48 - bits)); }
        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive ruined-portal RNG bound");
            if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value; do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0); return value;
        }
        float nextFloat() { return next(24) * 0x1.0p-24F; }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
        GenerationRandom receipt() {
            Legacy48 copy = copy(); long[] continuation = new long[8];
            for (int i = 0; i < continuation.length; i++) continuation[i] = copy.nextLong();
            return new GenerationRandom(state, count, continuation);
        }
        Legacy48 copy() { Legacy48 value = new Legacy48(0); value.state = state; value.count = count; return value; }
    }
}
