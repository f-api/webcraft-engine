package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;
import java.util.List;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 monster-room placed-feature leaf.
 *
 * <p>The supplied world is the complete decoration region. It must not clip reads or writes to the
 * source chunk. Chest loot and spawner contents are semantic sidecars because neither is a block
 * state. No canonical generator calls this class until full-pipeline promotion is reviewed.</p>
 */
public final class Mc263MonsterRoomFeature {
    public static final int UNDERGROUND_STRUCTURES_STEP = 3;
    public static final String MONSTER_ROOM = "minecraft:monster_room";
    public static final String MONSTER_ROOM_DEEP = "minecraft:monster_room_deep";
    public static final String SIMPLE_DUNGEON_LOOT = "minecraft:chests/simple_dungeon";

    private static final String AIR = "minecraft:cave_air";
    private static final String CHEST = "minecraft:chest";
    private static final String SPAWNER = "minecraft:spawner";
    private static final String COBBLESTONE = "minecraft:cobblestone";
    private static final String MOSSY_COBBLESTONE = "minecraft:mossy_cobblestone";

    /** Pure exact state and block-entity payload closure for both monster-room wrappers. */
    public static void preflight(Predicate<String> supportsState, boolean supportsPayloads) {
        Mc263LakeFeature.requireOutputs(supportsState, AIR, COBBLESTONE, MOSSY_COBBLESTONE,
                SPAWNER);
        for (Horizontal facing : Horizontal.values()) {
            Mc263LakeFeature.requireOutputs(supportsState, chestState(facing));
        }
        if (!supportsPayloads) {
            throw new UnsupportedOperationException("monster-room payloads");
        }
    }
    private static final String[] MOBS = {
            "minecraft:skeleton", "minecraft:zombie", "minecraft:zombie", "minecraft:spider"
    };
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled monster-room trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263MonsterRoomFeature() {
    }

    /** Complete mutable view used by the official feature and its biome placement modifier. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean isSolid(int blockX, int blockY, int blockZ);

        boolean isSolidRender(int blockX, int blockY, int blockZ);

        boolean featuresCannotReplace(int blockX, int blockY, int blockZ);

        void setBlockState(int blockX, int blockY, int blockZ, String state);

        /** True only when the current block entity implements RandomizableContainer. */
        boolean hasRandomizableContainer(int blockX, int blockY, int blockZ);

        /** Assigns the unopened chest's authoritative loot table and exact random seed. */
        void setChestLoot(int blockX, int blockY, int blockZ, String lootTable, long lootSeed);

        /** True only when the current block entity is a SpawnerBlockEntity. */
        boolean hasSpawnerBlockEntity(int blockX, int blockY, int blockZ);

        /** Assigns the authoritative entity type to the newly placed spawner. */
        void setSpawnerMob(int blockX, int blockY, int blockZ, String entityType);

        default boolean isEmptyBlock(int blockX, int blockY, int blockZ) {
            String block = blockKey(blockState(blockX, blockY, blockZ));
            return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                    || block.equals("minecraft:void_air");
        }
    }

    /** Numeric event stream kept stable for direct Java/Rust parity probes. */
    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }

        static TraceSink disabled() {
            return NO_TRACE;
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int placedRoomCount) {
        public PlacementResult {
            if (candidateCount < 0 || placedRoomCount < 0 || placedRoomCount > candidateCount) {
                throw new IllegalArgumentException("invalid monster-room placement counts");
            }
        }
    }

    public record PlacementCounts(int candidates, int rooms) {
        public PlacementCounts {
            if (candidates < 0 || rooms < 0 || rooms > candidates) {
                throw new IllegalArgumentException("invalid monster-room placement counts");
            }
        }
    }

    /** Runs one placed feature with its own independently derived feature stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, placedFeatureKey, world, NO_TRACE);
    }

    /** Runs one placed feature with its own independently derived feature stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        PlacedSpec spec = placedSpec(placedFeatureKey);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_STRUCTURES_STEP, spec.key());
        PlacementCounts counts = placeWithFeatureRandom(spec.key(), sourceBlockX, sourceBlockZ,
                world, seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.rooms());
    }

    /**
     * Executes count, in-square, uniform-height, biome, and configured-feature stages on one stream.
     * This overload exists for stream-composition and parity probes.
     */
    public static PlacementCounts placeWithFeatureRandom(String placedFeatureKey,
            int sourceBlockX, int sourceBlockZ, WorldAccess world, WorldgenRandom random,
            TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        PlacedSpec spec = placedSpec(placedFeatureKey);
        int globalIndex = Mc263DecorationRandom.globalIndex(UNDERGROUND_STRUCTURES_STEP, spec.key());
        if (globalIndex != spec.globalIndex()
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_STRUCTURES_STEP)
                .featureAt(globalIndex).equals(spec.key())) {
            throw new IllegalStateException("pinned monster-room feature index changed");
        }

        trace(events, "count", spec.attempts());
        int rooms = 0;
        for (int attempt = 0; attempt < spec.attempts(); attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = spec.minY() + random.nextInt(spec.maxY() - spec.minY() + 1);
            trace(events, "candidate", attempt, x, y, z);
            boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z), spec.key());
            trace(events, "biome", attempt, biomeAccepted ? 1L : 0L);
            if (biomeAccepted && placeConfigured(random, new BlockPos(x, y, z), world, events)) {
                rooms++;
            }
        }
        trace(events, "placed_result", spec.globalIndex(), spec.attempts(), rooms);
        return new PlacementCounts(spec.attempts(), rooms);
    }

    /** Executes the configured {@code MonsterRoomFeature} after modifiers select an origin. */
    public static boolean placeConfigured(WorldgenRandom random, BlockPos origin, WorldAccess world,
            TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        int xRadius = random.nextInt(2) + 2;
        int minX = -xRadius - 1;
        int maxX = xRadius + 1;
        int zRadius = random.nextInt(2) + 2;
        int minZ = -zRadius - 1;
        int maxZ = zRadius + 1;
        trace(events, "room_shape", xRadius, zRadius, minX, maxX, minZ, maxZ);

        int openings = 0;
        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = -1; dy <= 4; dy++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    int x = origin.x() + dx;
                    int y = origin.y() + dy;
                    int z = origin.z() + dz;
                    boolean solid = world.isSolid(x, y, z);
                    if ((dy == -1 || dy == 4) && !solid) {
                        trace(events, "support_reject", dx, dy, dz, openings);
                        return false;
                    }
                    boolean perimeter = dx == minX || dx == maxX || dz == minZ || dz == maxZ;
                    if (perimeter && dy == 0 && world.isEmptyBlock(x, y, z)
                            && world.isEmptyBlock(x, y + 1, z)) {
                        openings++;
                    }
                }
            }
        }
        boolean gateAccepted = openings >= 1 && openings <= 5;
        trace(events, "opening_gate", openings, gateAccepted ? 1L : 0L);
        if (!gateAccepted) return false;

        for (int dx = minX; dx <= maxX; dx++) {
            for (int dy = 3; dy >= -1; dy--) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    int x = origin.x() + dx;
                    int y = origin.y() + dy;
                    int z = origin.z() + dz;
                    String state = world.blockState(x, y, z);
                    boolean shell = dx == minX || dy == -1 || dz == minZ
                            || dx == maxX || dy == 4 || dz == maxZ;
                    if (shell) {
                        if (y >= world.minGenerationY() && !world.isSolid(x, y - 1, z)) {
                            world.setBlockState(x, y, z, AIR);
                            trace(events, "shell_write", x, y, z, 0L);
                        } else if (world.isSolid(x, y, z) && !isBlock(state, CHEST)) {
                            if (dy == -1) {
                                boolean mossy = random.nextInt(4) != 0;
                                safeSetBlock(world, x, y, z,
                                        mossy ? MOSSY_COBBLESTONE : COBBLESTONE, events,
                                        mossy ? 1L : 2L);
                            } else {
                                safeSetBlock(world, x, y, z, COBBLESTONE, events, 2L);
                            }
                        }
                    } else if (!isBlock(state, CHEST) && !isBlock(state, SPAWNER)) {
                        safeSetBlock(world, x, y, z, AIR, events, 0L);
                    }
                }
            }
        }

        chestLoop:
        for (int chest = 0; chest < 2; chest++) {
            for (int attempt = 0; attempt < 3; attempt++) {
                int x = origin.x() + random.nextInt(xRadius * 2 + 1) - xRadius;
                int y = origin.y();
                int z = origin.z() + random.nextInt(zRadius * 2 + 1) - zRadius;
                if (!world.isEmptyBlock(x, y, z)) {
                    trace(events, "chest_candidate", chest, attempt, x, y, z, 0L, -1L);
                    continue;
                }
                int solidNeighbors = 0;
                for (Horizontal direction : Horizontal.OFFICIAL_ORDER) {
                    if (world.isSolid(x + direction.dx, y, z + direction.dz)) solidNeighbors++;
                }
                trace(events, "chest_candidate", chest, attempt, x, y, z, 1L,
                        solidNeighbors);
                if (solidNeighbors != 1) continue;

                Horizontal facing = reorientChest(world, x, y, z);
                safeSetBlock(world, x, y, z, chestState(facing), events, 3L);
                if (world.hasRandomizableContainer(x, y, z)) {
                    long lootSeed = random.nextLong();
                    world.setChestLoot(x, y, z, SIMPLE_DUNGEON_LOOT, lootSeed);
                    trace(events, "chest_loot", x, y, z, facing.ordinal(), lootSeed);
                } else {
                    trace(events, "chest_container_missing", x, y, z);
                }
                continue chestLoop;
            }
        }

        safeSetBlock(world, origin.x(), origin.y(), origin.z(), SPAWNER, events, 4L);
        if (world.hasSpawnerBlockEntity(origin.x(), origin.y(), origin.z())) {
            int mobIndex = random.nextInt(MOBS.length);
            String mob = MOBS[mobIndex];
            world.setSpawnerMob(origin.x(), origin.y(), origin.z(), mob);
            trace(events, "spawner", origin.x(), origin.y(), origin.z(), mobIndex);
        } else {
            trace(events, "spawner_missing", origin.x(), origin.y(), origin.z());
        }
        return true;
    }

    public static boolean placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    private static void safeSetBlock(WorldAccess world, int x, int y, int z, String state,
            TraceSink trace, long stateCode) {
        if (world.featuresCannotReplace(x, y, z)) {
            trace(trace, "protected_skip", x, y, z, stateCode);
            return;
        }
        world.setBlockState(x, y, z, state);
        trace(trace, "safe_write", x, y, z, stateCode);
    }

    private static Horizontal reorientChest(WorldAccess world, int x, int y, int z) {
        Horizontal solidNeighbor = null;
        for (Horizontal direction : Horizontal.OFFICIAL_ORDER) {
            if (isBlock(world.blockState(x + direction.dx, y, z + direction.dz), CHEST)) {
                return Horizontal.NORTH;
            }
            if (!world.isSolidRender(x + direction.dx, y, z + direction.dz)) continue;
            if (solidNeighbor == null) {
                solidNeighbor = direction;
            } else {
                solidNeighbor = null;
                break;
            }
        }
        if (solidNeighbor != null) return solidNeighbor.opposite();

        Horizontal facing = Horizontal.NORTH;
        if (world.isSolidRender(x + facing.dx, y, z + facing.dz)) facing = facing.opposite();
        if (world.isSolidRender(x + facing.dx, y, z + facing.dz)) facing = facing.clockwise();
        if (world.isSolidRender(x + facing.dx, y, z + facing.dz)) facing = facing.opposite();
        return facing;
    }

    private static String chestState(Horizontal facing) {
        return CHEST + "[facing=" + facing.key + ",type=single,waterlogged=false]";
    }

    private static boolean biomeContains(String biomeKey, String featureKey) {
        if (biomeKey == null || biomeKey.isBlank()) {
            throw new IllegalArgumentException("biome key is required");
        }
        String biome = biomeKey.startsWith("minecraft:") ? biomeKey : "minecraft:" + biomeKey;
        return Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(UNDERGROUND_STRUCTURES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(featureKey));
    }

    private static PlacedSpec placedSpec(String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            throw new IllegalArgumentException("placed feature key is required");
        }
        String key = featureKey.startsWith("minecraft:") ? featureKey : "minecraft:" + featureKey;
        return switch (key) {
            case MONSTER_ROOM -> new PlacedSpec(MONSTER_ROOM, 2, 10, 0, 319);
            case MONSTER_ROOM_DEEP -> new PlacedSpec(MONSTER_ROOM_DEEP, 3, 4, -58, -1);
            default -> throw new IllegalArgumentException("unsupported monster-room feature: " + key);
        };
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace sink is required");
        return trace;
    }

    private static void trace(TraceSink sink, String phase, long value1) {
        if (sink.enabled()) sink.record(phase, value1);
    }

    private static void trace(TraceSink sink, String phase, long value1, long value2) {
        if (sink.enabled()) sink.record(phase, value1, value2);
    }

    private static void trace(TraceSink sink, String phase, long value1, long value2, long value3) {
        if (sink.enabled()) sink.record(phase, value1, value2, value3);
    }

    private static void trace(TraceSink sink, String phase, long value1, long value2, long value3,
            long value4) {
        if (sink.enabled()) sink.record(phase, value1, value2, value3, value4);
    }

    private static void trace(TraceSink sink, String phase, long value1, long value2, long value3,
            long value4, long value5) {
        if (sink.enabled()) sink.record(phase, value1, value2, value3, value4, value5);
    }

    private static void trace(TraceSink sink, String phase, long value1, long value2, long value3,
            long value4, long value5, long value6) {
        if (sink.enabled()) sink.record(phase, value1, value2, value3, value4, value5, value6);
    }

    private static void trace(TraceSink sink, String phase, long value1, long value2, long value3,
            long value4, long value5, long value6, long value7) {
        if (sink.enabled()) {
            sink.record(phase, value1, value2, value3, value4, value5, value6, value7);
        }
    }

    private static boolean isBlock(String state, String block) {
        return blockKey(state).equals(block);
    }

    private static String blockKey(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("world returned an empty block state");
        }
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private record PlacedSpec(String key, int globalIndex, int attempts, int minY, int maxY) {
    }

    private enum Horizontal {
        NORTH("north", 0, -1),
        EAST("east", 1, 0),
        SOUTH("south", 0, 1),
        WEST("west", -1, 0);

        private static final List<Horizontal> OFFICIAL_ORDER = List.of(NORTH, EAST, SOUTH, WEST);
        private final String key;
        private final int dx;
        private final int dz;

        Horizontal(String key, int dx, int dz) {
            this.key = key;
            this.dx = dx;
            this.dz = dz;
        }

        private Horizontal opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case EAST -> WEST;
                case SOUTH -> NORTH;
                case WEST -> EAST;
            };
        }

        private Horizontal clockwise() {
            return switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
            };
        }
    }
}
