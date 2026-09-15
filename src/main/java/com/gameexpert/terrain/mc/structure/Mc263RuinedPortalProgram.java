package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Grammar;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact start producer and typed raw-NBT carrier for the six pinned Overworld portal keys. */
public final class Mc263RuinedPortalProgram {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String OUTER_SERVER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String PIECE_TYPE = "minecraft:rupo";
    public static final String LOOT_TABLE = "minecraft:chests/ruined_portal";
    private static final long MULTIPLIER = 0x5deece66dL;
    private static final long ADDEND = 0xbL;
    private static final long MASK = (1L << 48) - 1L;
    private static final List<String> NORMAL = numbered("portal_", 10);
    private static final List<String> GIANT = numbered("giant_portal_", 3);
    private static final Map<String, List<Setup>> SETUPS = Map.of(
            "minecraft:ruined_portal", List.of(
                    new Setup(VerticalPlacement.UNDERGROUND, 1F, .2F, false, false, true, .5F),
                    new Setup(VerticalPlacement.ON_LAND_SURFACE, 0F, .2F, false, false, true, .5F)),
            "minecraft:ruined_portal_desert", List.of(
                    new Setup(VerticalPlacement.PARTLY_BURIED, 0F, 0F, false, false, false, 1F)),
            "minecraft:ruined_portal_jungle", List.of(
                    new Setup(VerticalPlacement.ON_LAND_SURFACE, 0F, .8F, true, true, false, 1F)),
            "minecraft:ruined_portal_mountain", List.of(
                    new Setup(VerticalPlacement.IN_MOUNTAIN, 1F, .2F, false, false, true, .5F),
                    new Setup(VerticalPlacement.ON_LAND_SURFACE, 0F, .2F, false, false, true, .5F)),
            "minecraft:ruined_portal_ocean", List.of(
                    new Setup(VerticalPlacement.ON_OCEAN_FLOOR, 0F, .8F, false, false, true, 1F)),
            "minecraft:ruined_portal_swamp", List.of(
                    new Setup(VerticalPlacement.ON_OCEAN_FLOOR, 0F, .5F, false, true, false, 1F)));

    private Mc263RuinedPortalProgram() { }

    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum Mirror { NONE, FRONT_BACK }
    public enum Heightmap { WORLD_SURFACE_WG, OCEAN_FLOOR_WG }
    public enum VerticalPlacement {
        ON_LAND_SURFACE("on_land_surface"), PARTLY_BURIED("partly_buried"),
        ON_OCEAN_FLOOR("on_ocean_floor"), IN_MOUNTAIN("in_mountain"),
        UNDERGROUND("underground");
        private final String serialized;
        VerticalPlacement(String serialized) { this.serialized = serialized; }
        String serialized() { return serialized; }
    }

    public record BlockPos(int x, int y, int z) {
        public BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(Math.addExact(x, dx), Math.addExact(y, dy), Math.addExact(z, dz));
        }
    }
    public record BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("inverted ruined-portal bounds");
        }
        public boolean contains(BlockPos p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY
                    && p.z() >= minZ && p.z() <= maxZ;
        }
        public BlockPos center() {
            return new BlockPos(minX + (maxX - minX + 1) / 2,
                    minY + (maxY - minY + 1) / 2, minZ + (maxZ - minZ + 1) / 2);
        }
        public BoundingBox atY(int y) {
            return new BoundingBox(minX, y, minZ, maxX, Math.addExact(y, maxY - minY), maxZ);
        }
    }
    public record Setup(VerticalPlacement placement, float airPocketProbability, float mossiness,
            boolean overgrown, boolean vines, boolean canBeCold, float weight) { }
    public record Properties(boolean cold, float mossiness, boolean airPocket,
            boolean overgrown, boolean vines) { }

    public interface Terrain {
        int minY();
        int seaLevel();
        int baseHeight(int x, int z, Heightmap heightmap);
        boolean opaqueInBaseColumn(int x, int y, int z, Heightmap heightmap);
        boolean coldEnoughToSnow(BlockPos position, int seaLevel);
    }

    public record RngContinuation(long state48, int worldgenCount, List<Long> nextLongs) {
        public RngContinuation {
            if (state48 < 0 || state48 > MASK || worldgenCount < 0 || nextLongs.size() != 8)
                throw new IllegalArgumentException("invalid ruined-portal generation continuation");
            nextLongs = List.copyOf(nextLongs);
        }
    }

    public record Plan(String structureKey, long worldSeed, int chunkX, int chunkZ, Setup setup,
            Properties properties, String templateKey, Rotation rotation, Mirror mirror,
            BlockPos pivot, BlockPos templatePosition, BoundingBox boundingBox,
            byte[] pieceNbt, byte[] startNbt, RngContinuation continuation) {
        public Plan {
            if (!SETUPS.containsKey(structureKey)) throw new IllegalArgumentException("unknown portal key");
            Objects.requireNonNull(setup); Objects.requireNonNull(properties);
            Objects.requireNonNull(templateKey); Objects.requireNonNull(rotation);
            Objects.requireNonNull(mirror); Objects.requireNonNull(pivot);
            Objects.requireNonNull(templatePosition); Objects.requireNonNull(boundingBox);
            pieceNbt = pieceNbt.clone(); startNbt = startNbt.clone();
        }
        @Override public byte[] pieceNbt() { return pieceNbt.clone(); }
        @Override public byte[] startNbt() { return startNbt.clone(); }

        /** Builds the exact one-piece STR263C1 start/reference carrier without registry mutation. */
        public Mc263StructureCarrier structureCarrier(Mc263StructureCarrier.Registry registry,
                int references) {
            Objects.requireNonNull(registry, "ruined-portal registry").require(structureKey);
            if (references < 0) throw new IllegalArgumentException("negative portal references");
            Mc263StructureCarrier.BoundingBox carrierBox = new Mc263StructureCarrier.BoundingBox(
                    boundingBox.minX(), boundingBox.minY(), boundingBox.minZ(),
                    boundingBox.maxX(), boundingBox.maxY(), boundingBox.maxZ());
            Mc263StructureCarrier.Piece piece = new Mc263StructureCarrier.Piece(PIECE_TYPE,
                    carrierBox, false, Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0,
                    List.of(), new Mc263StructureCarrier.PiecePayload(pieceNbt));
            Mc263StructureCarrier.ValidStart valid = new Mc263StructureCarrier.ValidStart(
                    structureKey + "@" + chunkX + "," + chunkZ, chunkX, chunkZ, references,
                    carrierBox, List.of(piece));
            Mc263StructureCarrier.ChunkStarts starts = new Mc263StructureCarrier.ChunkStarts(
                    chunkX, chunkZ, List.of(new Mc263StructureCarrier.StartEntry(structureKey, valid)));
            Mc263StructureCarrier.ChunkReferences refs = new Mc263StructureCarrier.ChunkReferences(
                    chunkX, chunkZ, List.of(new Mc263StructureCarrier.ReferenceSet(structureKey,
                            List.of(Mc263StructureCarrier.packChunk(chunkX, chunkZ)))));
            return new Mc263StructureCarrier(registry, List.of(starts), List.of(refs));
        }
    }

    public static Plan plan(String structureKey, long worldSeed, int chunkX, int chunkZ,
            Terrain terrain) {
        return plan(structureKey, worldSeed, chunkX, chunkZ, terrain,
                LegacyRandom.largeFeature(worldSeed, chunkX, chunkZ));
    }

    static Plan plan(String structureKey, long worldSeed, int chunkX, int chunkZ,
            Terrain terrain, LegacyRandom random) {
        Objects.requireNonNull(terrain, "ruined-portal terrain");
        List<Setup> setups = SETUPS.get(structureKey);
        if (setups == null) throw new IllegalArgumentException("unknown Overworld ruined-portal key: " + structureKey);
        LegacyRandom staged = random.copy();
        Setup setup = choose(setups, staged);
        boolean airPocket = sample(staged, setup.airPocketProbability());
        List<String> templates = staged.nextFloat() < .05F ? GIANT : NORMAL;
        String template = templates.get(staged.nextInt(templates.size()));
        Grammar grammar = Mc263RuinedPortalGrammarData.require(template);
        Rotation rotation = Rotation.values()[staged.nextInt(4)];
        Mirror mirror = staged.nextFloat() < .5F ? Mirror.NONE : Mirror.FRONT_BACK;
        BlockPos pivot = new BlockPos(grammar.sizeX() / 2, 0, grammar.sizeZ() / 2);
        BlockPos initial = new BlockPos(Math.multiplyExact(chunkX, 16), 0,
                Math.multiplyExact(chunkZ, 16));
        BoundingBox zero = bounds(initial, grammar, rotation, mirror, pivot);
        Heightmap heightmap = heightmap(setup.placement());
        BlockPos center = zero.center();
        int surface = Math.subtractExact(terrain.baseHeight(center.x(), center.z(), heightmap), 1);
        int candidate = candidateY(staged, setup.placement(), airPocket, surface,
                zero.maxY() - zero.minY() + 1, terrain.minY());
        int projected = settleOnThreeCorners(candidate, zero, heightmap, terrain);
        BlockPos position = new BlockPos(initial.x(), projected, initial.z());
        BoundingBox box = zero.atY(projected);
        Properties properties = new Properties(setup.canBeCold()
                && terrain.coldEnoughToSnow(position, terrain.seaLevel()), setup.mossiness(),
                airPocket, setup.overgrown(), setup.vines());
        byte[] piece = pieceNbt(template, rotation, mirror, position, box, setup.placement(), properties);
        byte[] start = startNbt(structureKey, chunkX, chunkZ, piece);
        Plan result = new Plan(structureKey, worldSeed, chunkX, chunkZ, setup, properties,
                template, rotation, mirror, pivot, position, box, piece, start,
                staged.continuation());
        random.commit(staged);
        return result;
    }

    private static Setup choose(List<Setup> setups, LegacyRandom random) {
        if (setups.size() == 1) return setups.getFirst();
        float total = 0F; for (Setup setup : setups) total += setup.weight();
        float pick = random.nextFloat();
        for (Setup setup : setups) if ((pick -= setup.weight() / total) < 0F) return setup;
        throw new IllegalStateException("ruined-portal weighted setup did not select");
    }
    private static boolean sample(LegacyRandom random, float probability) {
        if (probability == 0F) return false;
        if (probability == 1F) return true;
        return random.nextFloat() < probability;
    }
    private static int candidateY(LegacyRandom random, VerticalPlacement placement,
            boolean airPocket, int surface, int ySpan, int minBuildY) {
        int min = Math.addExact(minBuildY, 15);
        return switch (placement) {
            case IN_MOUNTAIN -> randomWithin(random, 70, surface - ySpan);
            case UNDERGROUND -> randomWithin(random, min, surface - ySpan);
            case PARTLY_BURIED -> surface - ySpan + inclusive(random, 2, 8);
            case ON_LAND_SURFACE, ON_OCEAN_FLOOR -> surface;
        };
    }
    private static int settleOnThreeCorners(int candidate, BoundingBox box, Heightmap map,
            Terrain terrain) {
        int min = terrain.minY() + 15;
        int[][] corners = {{box.minX(), box.minZ()}, {box.maxX(), box.minZ()},
                {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()}};
        for (int y = candidate; y > min; y--) {
            int opaque = 0;
            for (int[] corner : corners)
                if (terrain.opaqueInBaseColumn(corner[0], y, corner[1], map) && ++opaque == 3)
                    return y;
        }
        return min;
    }
    private static int randomWithin(LegacyRandom random, int preferred, int max) {
        return preferred < max ? inclusive(random, preferred, max) : max;
    }
    private static int inclusive(LegacyRandom random, int min, int max) {
        return Math.addExact(min, random.nextInt(Math.addExact(Math.subtractExact(max, min), 1)));
    }
    private static Heightmap heightmap(VerticalPlacement placement) {
        return placement == VerticalPlacement.ON_OCEAN_FLOOR
                ? Heightmap.OCEAN_FLOOR_WG : Heightmap.WORLD_SURFACE_WG;
    }

    static BoundingBox bounds(BlockPos origin, Grammar grammar, Rotation rotation,
            Mirror mirror, BlockPos pivot) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : new int[]{0, grammar.sizeX() - 1}) for (int z : new int[]{0, grammar.sizeZ() - 1}) {
            BlockPos p = transform(origin, new BlockPos(x, 0, z), rotation, mirror, pivot);
            minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z()); maxZ = Math.max(maxZ, p.z());
        }
        return new BoundingBox(minX, origin.y(), minZ, maxX,
                Math.addExact(origin.y(), grammar.sizeY() - 1), maxZ);
    }

    static BlockPos transform(BlockPos origin, BlockPos local, Rotation rotation,
            Mirror mirror, BlockPos pivot) {
        int x = mirror == Mirror.FRONT_BACK ? -local.x() : local.x();
        int z = local.z();
        int tx, tz;
        switch (rotation) {
            case NONE -> { tx = x; tz = z; }
            case CLOCKWISE_90 -> { tx = pivot.x() + pivot.z() - z; tz = pivot.z() - pivot.x() + x; }
            case CLOCKWISE_180 -> { tx = 2 * pivot.x() - x; tz = 2 * pivot.z() - z; }
            case COUNTERCLOCKWISE_90 -> { tx = pivot.x() - pivot.z() + z; tz = pivot.z() + pivot.x() - x; }
            default -> throw new AssertionError(rotation);
        }
        return origin.offset(tx, local.y(), tz);
    }

    static byte[] pieceNbt(String template, Rotation rotation, Mirror mirror, BlockPos position,
            BoundingBox box, VerticalPlacement placement, Properties properties) {
        return nbt(out -> writePiece(out, template, rotation, mirror, position, box,
                placement, properties, true));
    }
    static byte[] startNbt(String key, int chunkX, int chunkZ, byte[] pieceNbt) {
        return nbt(out -> {
            intTag(out, "references", 0); intTag(out, "ChunkZ", chunkZ);
            stringTag(out, "id", key);
            out.writeByte(9); out.writeUTF("Children"); out.writeByte(10); out.writeInt(1);
            out.write(pieceNbt, 3, pieceNbt.length - 3);
            intTag(out, "ChunkX", chunkX);
        });
    }
    static byte[] chestNbt(BlockPos position, long seed) {
        return nbt(out -> {
            stringTag(out, "LootTable", LOOT_TABLE);
            out.writeByte(10); out.writeUTF("components"); out.writeByte(0);
            intTag(out, "x", position.x()); intTag(out, "y", position.y());
            intTag(out, "z", position.z()); stringTag(out, "id", "minecraft:chest");
            out.writeByte(4); out.writeUTF("LootTableSeed"); out.writeLong(seed);
        });
    }
    private static void writePiece(DataOutputStream out, String template, Rotation rotation,
            Mirror mirror, BlockPos position, BoundingBox box, VerticalPlacement placement,
            Properties properties, boolean root) throws IOException {
        intArrayTag(out, "BB", box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        stringTag(out, "VerticalPlacement", placement.serialized());
        stringTag(out, "id", PIECE_TYPE); intTag(out, "TPY", position.y());
        out.writeByte(10); out.writeUTF("Properties");
        byteTag(out, "overgrown", properties.overgrown());
        out.writeByte(5); out.writeUTF("mossiness"); out.writeFloat(properties.mossiness());
        byteTag(out, "replace_with_blackstone", false); byteTag(out, "vines", properties.vines());
        byteTag(out, "cold", properties.cold()); byteTag(out, "air_pocket", properties.airPocket());
        out.writeByte(0);
        intTag(out, "GD", 0); intTag(out, "TPX", position.x());
        stringTag(out, "Rotation", rotation.name()); stringTag(out, "Mirror", mirror.name());
        intTag(out, "O", 2); intTag(out, "TPZ", position.z()); stringTag(out, "Template", template);
    }
    private static byte[] nbt(NbtWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0); writer.write(out); out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void byteTag(DataOutputStream out, String name, boolean value) throws IOException {
        out.writeByte(1); out.writeUTF(name); out.writeByte(value ? 1 : 0);
    }
    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }
    private static void stringTag(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }
    private static void intArrayTag(DataOutputStream out, String name, int... values) throws IOException {
        out.writeByte(11); out.writeUTF(name); out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }
    private static List<String> numbered(String prefix, int count) {
        ArrayList<String> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) result.add("minecraft:ruined_portal/" + prefix + i);
        return List.copyOf(result);
    }
    @FunctionalInterface private interface NbtWriter { void write(DataOutputStream out) throws IOException; }

    /** java.util.Random-compatible source with transactional continuation. */
    static final class LegacyRandom {
        private long state; private int count;
        private LegacyRandom(long seed) { setSeed(seed); }
        private LegacyRandom(long state, int count, boolean raw) { this.state = state; this.count = count; }
        static LegacyRandom largeFeature(long seed, int chunkX, int chunkZ) {
            LegacyRandom result = new LegacyRandom(seed);
            long first = result.nextLong(), second = result.nextLong();
            result.setSeed((long) chunkX * first ^ (long) chunkZ * second ^ seed);
            return result;
        }
        private void setSeed(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
        private int next(int bits) { state = (state * MULTIPLIER + ADDEND) & MASK; count++; return (int) (state >>> (48 - bits)); }
        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive portal random bound");
            if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value; do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0); return value;
        }
        float nextFloat() { return next(24) * 0x1.0p-24F; }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
        LegacyRandom copy() { return new LegacyRandom(state, count, true); }
        void commit(LegacyRandom other) { state = other.state; count = other.count; }
        RngContinuation continuation() {
            LegacyRandom copy = copy(); ArrayList<Long> words = new ArrayList<>(8);
            for (int i = 0; i < 8; i++) words.add(copy.nextLong());
            return new RngContinuation(state, count, words);
        }
    }
}
