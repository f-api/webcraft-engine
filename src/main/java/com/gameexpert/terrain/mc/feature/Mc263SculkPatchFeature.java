package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.util.function.Predicate;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact, dormant Minecraft Java 26.3-snapshot-7 deep-dark sculk-patch leaf. */
public final class Mc263SculkPatchFeature {
    public static final int UNDERGROUND_DECORATION_STEP = 7;
    public static final int GLOBAL_INDEX = 1;
    public static final int COUNT = 256;
    public static final int HEIGHT_MIN = -64;
    public static final int HEIGHT_MAX = 256;
    public static final int WORLD_MAX_Y = 319;
    public static final int CHARGE_COUNT = 10;
    public static final int AMOUNT_PER_CHARGE = 32;
    public static final int SPREAD_ATTEMPTS = 64;
    public static final int GROWTH_ROUNDS = 0;
    public static final int SPREAD_ROUNDS = 1;
    public static final int MAX_CURSORS = 32;
    public static final int GROWTH_SPAWN_COST = 50;
    public static final int NO_GROWTH_RADIUS = 1;
    public static final int CHARGE_DECAY_RATE = 5;
    public static final int ADDITIONAL_DECAY_RATE = 10;
    public static final int MAX_WORLDGEN_SPREAD = 12;
    public static final int DIRECT_FLAGS = 3;
    public static final int SPREAD_FLAGS = 2;

    public static final String FEATURE = "minecraft:sculk_patch_deep_dark";
    public static final String SCULK = "minecraft:sculk";
    public static final String VEIN = "minecraft:sculk_vein";
    public static final String CATALYST = "minecraft:sculk_catalyst";
    public static final String SENSOR = "minecraft:sculk_sensor";
    public static final String SHRIEKER = "minecraft:sculk_shrieker";
    public static final String WATER = "minecraft:water";
    public static final String FLOWING_WATER = "minecraft:flowing_water";
    public static final String EMPTY_FLUID = "minecraft:empty";

    public static void preflight(Predicate<String> supportsState,
            boolean supportsPostprocessing, boolean supportsPayloads) {
        Mc263LakeFeature.requireOutputs(supportsState, SCULK,
                CATALYST + "[bloom=false]");
        for (int faces = 1; faces <= 63; faces++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                Mc263LakeFeature.requireOutputs(supportsState,
                        exactVein(faces, waterlogged));
            }
        }
        for (boolean waterlogged : new boolean[]{false, true}) {
            Mc263LakeFeature.requireOutputs(supportsState,
                    SENSOR + "[power=0,sculk_sensor_phase=inactive,waterlogged="
                            + waterlogged + "]",
                    SHRIEKER + "[can_summon=true,shrieking=false,waterlogged="
                            + waterlogged + "]");
        }
        if (!supportsPostprocessing) {
            throw new UnsupportedOperationException("sculk-patch postprocessing");
        }
        if (!supportsPayloads) throw new UnsupportedOperationException("sculk payloads");
    }

    private static String exactVein(int faces, boolean waterlogged) {
        return VEIN + "[down=" + ((faces & 1) != 0) + ",east=" + ((faces & 32) != 0)
                + ",north=" + ((faces & 4) != 0) + ",south=" + ((faces & 8) != 0)
                + ",up=" + ((faces & 2) != 0) + ",waterlogged=" + waterlogged
                + ",west=" + ((faces & 16) != 0) + "]";
    }

    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "42d64a121273d090aa86ae8a679efffc129f79bafaf1ba7c58da053dec8e696c";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "aa5839139896659d3b56a150103f63cf3ccf70ccc7a92c85df49b2c90a7b485b";
    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String SCULK_PATCH_FEATURE_CLASS_SHA256 =
            "313a78b9b8153a7de89fa5a55338e81e60be35f7b8b6ccde056d8492cc578124";
    public static final String SCULK_SPREADER_CLASS_SHA256 =
            "3eb87aa404c7bf9d3a9d799a18e8cb338b2ec74f9728d5d10ce4327779ab42ba";
    public static final String CHARGE_CURSOR_CLASS_SHA256 =
            "1db6a8021014046fcfc66a0badd6596aebb55809803490b3a8994d1e6af1355c";
    public static final String SCULK_BLOCK_CLASS_SHA256 =
            "44854ca2894bf9225a346ab76ffcf1669a0b8633e58a2770f02c903b0aa49c98";
    public static final String SCULK_VEIN_BLOCK_CLASS_SHA256 =
            "9667dfbde838b8c5818f69a2b99a96a44419ac29668ad4f4186afe7730e6ec52";
    public static final String SCULK_BEHAVIOUR_CLASS_SHA256 =
            "ab16effccca294206651c979b0b179b3c0041369a221ffdbc1a0041c18d6c2d5";
    public static final String MULTIFACE_SPREADER_CLASS_SHA256 =
            "6f79d7f730145e38465be60fb783c828409752b19e352bb1bdefeaa92e0b178b";
    public static final String SEQUENCE_FEATURE_CLASS_SHA256 =
            "815580c4c95545d9ef924ecbc9300f2cf27bb3996c9a30bf7086189f0cc48306";
    public static final String SIMPLE_BLOCK_FEATURE_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String BLOCK_MATCHER_CLASS_SHA256 =
            "76e7850cb86b5123a644f7e1df4f28092dec893a2432876a418ed8ddc9e6ea4a";
    public static final String BOX_BLOCK_MATCHER_CLASS_SHA256 =
            "681c2240b306a4b7e1037130382c0470dfa03e06e44b4749e9605ecda270f8da";
    public static final String BLOCK_SCAN_UTILS_CLASS_SHA256 =
            "12a8d06946f368fe831855bd749d6d537a55a3255ae2c58e5a13e0a7687b70f1";
    public static final String SCULK_REPLACEABLE_TAG_SHA256 =
            "8f5c608ef4e8c74002bf9d13a50e81804d37f72c59faf80dfcb7367f6e1ec476";
    public static final String SCULK_REPLACEABLE_WORLDGEN_TAG_SHA256 =
            "35a9d8a4e947c8f09d44a9836766d639ac37e647dd5da23ca5b8b8f3e0afcb34";
    public static final String SCULK_GROWTH_INHIBITORS_TAG_SHA256 =
            "86f2dc2d79bfe4f8a25e9877b2453b0b493654af80f9fd1f77d8133c8abd5377";
    public static final int REGULAR_REPLACEABLE_COUNT = 52;
    public static final int WORLDGEN_REPLACEABLE_COUNT = 58;
    public static final int TRACE_MAGIC = 0x53435033; // SCP3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-sculk-patch-trace-v1";

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final List<Vec3> NON_CORNER_NEIGHBOURS = makeNonCornerNeighbours();
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled sculk-patch trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263SculkPatchFeature() {
    }

    /** Minimal live FEATURES-region surface required by the later shared adapter. */
    public interface WorldAccess {
        int minGenerationY();
        int generationDepth();
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        FluidFact fluidState(int x, int y, int z);
        boolean isCollisionShapeFullBlock(int x, int y, int z);
        boolean isFaceSturdy(int x, int y, int z, Direction face);
        boolean canAttachTo(int x, int y, int z, Direction face);
        boolean sectionMayContainGrowthInhibitor(int sectionX, int sectionY, int sectionZ);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
        boolean tryMarkPosForPostProcessing(int x, int y, int z);

        /** Entity displacement runs after the substrate write attempt, even when it returns false. */
        default void pushEntitiesUp(int x, int y, int z, State oldState, State newState) {
        }

        /** Ordered, WorldGenRegion-sunk sound call after a successful spread decision. */
        default void playSculkSpreadSound(int x, int y, int z) {
        }

        /** Ordered species-state place sound emitted after a growth write attempt. */
        /** State identifies the distinct sensor/shrieker placement sound semantic. */
        default void playGrowthPlaceSound(int x, int y, int z, State state) {
        }

        /** Exact SculkSpreader particle/world event, including zero-charge discharge events. */
        default void emitSculkChargeEvent(int x, int y, int z, int data) {
        }
    }

    public enum Capability {
        NONE, CATALYST_BLOCK_ENTITY, SENSOR_BLOCK_ENTITY, SHRIEKER_BLOCK_ENTITY
    }

    public record FluidFact(String fluid, boolean source) {
        public FluidFact {
            requireKey(fluid, "fluid");
        }
        public static FluidFact empty() { return new FluidFact(EMPTY_FLUID, false); }
    }

    /** All exact live facts used by sculk spreading; tag membership is deliberately explicit. */
    public record State(String block, int faces, boolean waterlogged, String fluid,
                        boolean fluidSource, boolean air, boolean replaceable,
                        boolean regularSculkReplaceable, boolean worldgenSculkReplaceable,
                        boolean growthInhibitor, Capability capability,
                        boolean catalystBloom, int sensorPower, int sensorPhase,
                        boolean shrieking, boolean canSummon) {
        public State {
            requireKey(block, "block");
            requireKey(fluid, "fluid");
            if (faces < 0 || faces > 63) throw new IllegalArgumentException("faces must be 0..63");
            if (!block.equals(VEIN) && faces != 0) {
                throw new IllegalArgumentException("only sculk vein carries faces here");
            }
            if (waterlogged && !(block.equals(VEIN) || block.equals(SENSOR)
                    || block.equals(SHRIEKER))) {
                throw new IllegalArgumentException("this block cannot be waterlogged");
            }
            if (waterlogged && (!fluid.equals(WATER) || !fluidSource)) {
                throw new IllegalArgumentException("waterlogged state requires source water");
            }
            if (capability == null) throw new IllegalArgumentException("capability is required");
            if (growthInhibitor != (block.equals(SENSOR) || block.equals(SHRIEKER))) {
                throw new IllegalArgumentException("growth-inhibitor tag fact is inconsistent");
            }
            if (sensorPower < 0 || sensorPower > 15 || sensorPhase < 0 || sensorPhase > 2) {
                throw new IllegalArgumentException("invalid sensor state");
            }
        }

        public static State airState() {
            return basic("minecraft:air", EMPTY_FLUID, false, true, true, false, false, false);
        }

        public static State block(String block, boolean regular, boolean worldgen) {
            return basic(block, EMPTY_FLUID, false, false, false, regular, worldgen, false);
        }

        public static State solid(String block, boolean regular, boolean worldgen) {
            return block(block, regular, worldgen);
        }

        public static State replaceable(String block, boolean regular, boolean worldgen) {
            return basic(block, EMPTY_FLUID, false, false, true, regular, worldgen, false);
        }

        public static State water(boolean source) {
            return basic(WATER, source ? WATER : FLOWING_WATER, source,
                    false, true, false, false, false);
        }

        public static State otherFluid(String block, String fluid) {
            return basic(block, fluid, false, false, true, false, false, false);
        }

        public static State sculk() {
            return basic(SCULK, EMPTY_FLUID, false, false, false, false, false, false);
        }

        public static State vein(int faces, boolean waterlogged) {
            return new State(VEIN, faces, waterlogged, waterlogged ? WATER : EMPTY_FLUID,
                    waterlogged, false, false, false, false, false, Capability.NONE,
                    false, 0, 0, false, false);
        }

        public static State catalyst() {
            return new State(CATALYST, 0, false, EMPTY_FLUID, false, false, false,
                    false, false, false, Capability.CATALYST_BLOCK_ENTITY,
                    false, 0, 0, false, false);
        }

        public static State sensor(String replacedFluid) {
            requireKey(replacedFluid, "sensor replaced fluid");
            boolean waterlogged = !replacedFluid.equals(EMPTY_FLUID);
            return new State(SENSOR, 0, waterlogged, waterlogged ? WATER : EMPTY_FLUID,
                    waterlogged, false, false, false, false, true,
                    Capability.SENSOR_BLOCK_ENTITY, false, 0, 0, false, false);
        }

        public static State sensor(boolean waterlogged) {
            return sensor(waterlogged ? WATER : EMPTY_FLUID);
        }

        public static State shrieker(String replacedFluid) {
            requireKey(replacedFluid, "shrieker replaced fluid");
            boolean waterlogged = !replacedFluid.equals(EMPTY_FLUID);
            return new State(SHRIEKER, 0, waterlogged, waterlogged ? WATER : EMPTY_FLUID,
                    waterlogged, false, false, false, false, true,
                    Capability.SHRIEKER_BLOCK_ENTITY, false, 0, 0, false, true);
        }

        public static State shrieker(boolean waterlogged) {
            return shrieker(waterlogged ? WATER : EMPTY_FLUID);
        }

        private static State basic(String block, String fluid, boolean source, boolean air,
                boolean replaceable, boolean regular, boolean worldgen, boolean inhibitor) {
            return new State(block, 0, false, fluid, source, air, replaceable, regular,
                    worldgen, inhibitor, Capability.NONE, false, 0, 0, false, false);
        }

        public boolean hasFace(Direction direction) {
            return (faces & direction.bit()) != 0;
        }

        public boolean hasWaterFluid() {
            return fluid.equals(WATER) || fluid.equals(FLOWING_WATER);
        }

        public boolean hasEmptyFluid() {
            return fluid.equals(EMPTY_FLUID);
        }

        public String canonical() {
            return switch (capability) {
                case CATALYST_BLOCK_ENTITY -> CATALYST + "[bloom=false]{be=catalyst}";
                case SENSOR_BLOCK_ENTITY -> SENSOR
                        + "[power=0,sculk_sensor_phase=inactive,waterlogged="
                        + waterlogged + "]{be=sensor}";
                case SHRIEKER_BLOCK_ENTITY -> SHRIEKER
                        + "[can_summon=true,shrieking=false,waterlogged="
                        + waterlogged + "]{be=shrieker}";
                case NONE -> block.equals(VEIN) ? VEIN + "[faces=" + faces
                        + ",waterlogged=" + waterlogged + "]" : block;
            };
        }
    }

    public enum Direction {
        DOWN(0, -1, 0, 1), UP(0, 1, 0, 0), NORTH(0, 0, -1, 3),
        SOUTH(0, 0, 1, 2), WEST(-1, 0, 0, 5), EAST(1, 0, 0, 4);

        final int dx;
        final int dy;
        final int dz;
        final int opposite;

        Direction(int dx, int dy, int dz, int opposite) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.opposite = opposite;
        }

        public Direction opposite() { return DIRECTIONS[opposite]; }
        public int bit() { return 1 << ordinal(); }
        public int stepX() { return dx; }
        public int stepY() { return dy; }
        public int stepZ() { return dz; }
        int axis() { return dx != 0 ? 0 : dy != 0 ? 1 : 2; }
    }

    public record BlockPos(int x, int y, int z) {
        public BlockPos relative(Direction direction) {
            return offset(direction.dx, direction.dy, direction.dz);
        }
        BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }
        int manhattan(BlockPos other) {
            return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
        }
        int chessboard(BlockPos other) {
            return Math.max(Math.max(Math.abs(x - other.x), Math.abs(y - other.y)),
                    Math.abs(z - other.z));
        }
        long horizontalDistanceSquared(BlockPos other) {
            long dx = (long) x - other.x;
            long dz = (long) z - other.z;
            return dx * dx + dz * dz;
        }
        double distanceSquared(BlockPos other) {
            long dx = (long) x - other.x;
            long dy = (long) y - other.y;
            long dz = (long) z - other.z;
            return (double) dx * dx + (double) dy * dy + (double) dz * dz;
        }
    }

    public record PlacementCounts(int candidates, int sequenceSuccesses, int patchSuccesses,
                                  int attemptedWrites, int retainedWrites,
                                  int attemptedMarks, int retainedMarks) {
    }

    public record ConfiguredResult(boolean sequencePlaced, boolean patchPlaced,
                                   boolean catalystChancePassed, boolean catalystPredicatePassed,
                                   int attemptedWrites, int retainedWrites,
                                   int attemptedMarks, int retainedMarks) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  PlacementCounts counts) {
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            if (phase == null || phase.isBlank() || values == null) {
                throw new IllegalArgumentException("trace phase and values are required");
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
        default boolean enabled() { return true; }
        static TraceSink disabled() { return NO_TRACE; }
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        requireWorld(world);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_DECORATION_STEP, FEATURE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), NO_TRACE);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts);
    }

    /** Executes constant count, X/Z/Y, biome, then sequence patch -> catalyst on one stream. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, Mc263WorldgenRandomSource random, TraceSink trace) {
        requireWorld(world);
        requireRandomTrace(random, trace);
        if (Mc263DecorationRandom.globalIndex(UNDERGROUND_DECORATION_STEP, FEATURE) != GLOBAL_INDEX) {
            throw new IllegalStateException("pinned sculk patch index changed");
        }
        Counts totals = new Counts();
        totals.candidates = COUNT;
        for (int attempt = 0; attempt < COUNT; attempt++) {
            int x = drawInt(random, 16, 100, trace) + sourceBlockX;
            int z = drawInt(random, 16, 101, trace) + sourceBlockZ;
            int y = drawInt(random, HEIGHT_MAX - HEIGHT_MIN + 1, 102, trace) + HEIGHT_MIN;
            traceRecord(trace, "candidate", attempt, x, y, z);
            String biome = requireKey(world.biomeKey(x, y, z), "biome");
            boolean accepted = biome.equals("minecraft:deep_dark")
                    && Mc263FeatureIndexReceipt.biome(biome)
                    .featuresAtStep(UNDERGROUND_DECORATION_STEP).stream()
                    .anyMatch(ref -> ref.globalIndex() == GLOBAL_INDEX
                            && ref.featureKey().equals(FEATURE));
            traceRecord(trace, "biome", attempt, biomeId(biome), accepted ? 1 : 0);
            if (!accepted) continue;
            ConfiguredResult result = placeConfigured(random, new BlockPos(x, y, z), world, trace);
            if (result.patchPlaced()) totals.patchSuccesses++;
            if (result.sequencePlaced()) totals.sequenceSuccesses++;
            totals.attemptedWrites += result.attemptedWrites();
            totals.retainedWrites += result.retainedWrites();
            totals.attemptedMarks += result.attemptedMarks();
            totals.retainedMarks += result.retainedMarks();
        }
        traceRecord(trace, "placed_result", totals.candidates, totals.sequenceSuccesses,
                totals.patchSuccesses, totals.attemptedWrites, totals.retainedWrites,
                totals.attemptedMarks, totals.retainedMarks);
        return totals.freeze();
    }

    public static ConfiguredResult placeConfigured(Mc263WorldgenRandomSource random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    public static ConfiguredResult placeConfigured(Mc263WorldgenRandomSource random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        requireRandomTrace(random, trace);
        if (origin == null) throw new IllegalArgumentException("origin is required");
        Counts counts = new Counts();
        boolean patch = placePatch(random, origin, world, counts, trace);
        if (!patch) {
            traceRecord(trace, "sequence", 0, 0, 0, 0);
            return counts.configured(false, false, false, false);
        }
        boolean chance = drawFloat(random, 200, trace) < 0.5F;
        traceRecord(trace, "catalyst_chance", chance ? 1 : 0);
        if (!chance) {
            traceRecord(trace, "sequence", 1, 0, 0, 0);
            return counts.configured(false, true, false, false);
        }
        BlockPos below = origin.relative(Direction.DOWN);
        boolean sturdy = world.isFaceSturdy(below.x, below.y, below.z, Direction.UP);
        traceRecord(trace, "sturdy", below.x, below.y, below.z, Direction.UP.ordinal(), sturdy ? 1 : 0);
        if (!sturdy) {
            traceRecord(trace, "sequence", 1, 1, 0, 0);
            return counts.configured(false, true, true, false);
        }
        State catalyst = State.catalyst();
        write(world, origin, catalyst, SPREAD_FLAGS, counts, trace, 4);
        // SimpleBlockFeature returns true after the call regardless of setBlock's boolean.
        traceRecord(trace, "sequence", 1, 1, 1, 1);
        return counts.configured(true, true, true, true);
    }

    private static boolean placePatch(Mc263WorldgenRandomSource random, BlockPos origin, WorldAccess world,
            Counts counts, TraceSink trace) {
        State start = read(world, origin, trace);
        boolean gate = isSculkBehaviour(start);
        if (!gate && (start.air() || start.block().equals(WATER) && start.fluidSource())) {
            for (Direction direction : DIRECTIONS) {
                BlockPos neighbour = origin.relative(direction);
                boolean full = world.isCollisionShapeFullBlock(neighbour.x, neighbour.y, neighbour.z);
                traceRecord(trace, "collision", neighbour.x, neighbour.y, neighbour.z,
                        direction.ordinal(), full ? 1 : 0);
                if (full) {
                    gate = true;
                    break;
                }
            }
        }
        traceRecord(trace, "patch_gate", origin.x, origin.y, origin.z, stateId(start), gate ? 1 : 0);
        if (!gate) return false;

        List<Cursor> cursors = new ArrayList<>(CHARGE_COUNT);
        for (int i = 0; i < CHARGE_COUNT && cursors.size() < MAX_CURSORS; i++) {
            cursors.add(new Cursor(origin, AMOUNT_PER_CHARGE));
            traceRecord(trace, "cursor_add", i, origin.x, origin.y, origin.z, AMOUNT_PER_CHARGE);
        }
        for (int attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++) {
            cursors = updateCursors(cursors, origin, random, world, counts, trace, attempt);
        }
        // The official feature clears all remaining cursor state after every round.
        traceRecord(trace, "patch_result", cursors.size(), counts.attemptedWrites,
                counts.retainedWrites, counts.attemptedMarks, counts.retainedMarks);
        return true;
    }

    private static List<Cursor> updateCursors(List<Cursor> cursors, BlockPos origin,
            Mc263WorldgenRandomSource random, WorldAccess world, Counts counts, TraceSink trace,
            int attempt) {
        if (cursors.isEmpty()) return cursors;
        List<Cursor> processed = new ArrayList<>(cursors.size());
        Map<BlockPos, Integer> chargeMap = new HashMap<>();
        Map<BlockPos, Cursor> representative = new HashMap<>();
        List<BlockPos> insertionOrder = new ArrayList<>();
        for (int index = 0; index < cursors.size(); index++) {
            Cursor cursor = cursors.get(index);
            if (cursor.pos.chessboard(origin) > 1024) continue;
            updateCursor(cursor, origin, random, world, counts, trace);
            traceRecord(trace, "cursor_tick", attempt, index, cursor.pos.x, cursor.pos.y,
                    cursor.pos.z, cursor.charge, cursor.updateDelay, cursor.decayDelay);
            if (cursor.charge <= 0) {
                world.emitSculkChargeEvent(cursor.pos.x, cursor.pos.y, cursor.pos.z, 0);
                traceRecord(trace, "level_event", cursor.pos.x, cursor.pos.y,
                        cursor.pos.z, 3006, 0);
                continue;
            }
            if (!chargeMap.containsKey(cursor.pos)) insertionOrder.add(cursor.pos);
            chargeMap.merge(cursor.pos, cursor.charge, Integer::sum);
            Cursor existing = representative.get(cursor.pos);
            if (existing == null) {
                representative.put(cursor.pos, cursor);
            } else if (cursor.charge < existing.charge) {
                representative.put(cursor.pos, cursor);
            }
            // World-generation cursors deliberately never merge.
            processed.add(cursor);
        }
        for (BlockPos orderedPos : fastutilKeyOrder(insertionOrder)) {
            Map.Entry<BlockPos, Integer> entry = Map.entry(orderedPos, chargeMap.get(orderedPos));
            Cursor cursor = representative.get(entry.getKey());
            if (entry.getValue() <= 0 || cursor == null || !cursor.facingsKnown) continue;
            int data = aggregateEventData(entry.getValue(), cursor.facings);
            BlockPos pos = entry.getKey();
            world.emitSculkChargeEvent(pos.x, pos.y, pos.z, data);
            traceRecord(trace, "level_event", pos.x, pos.y, pos.z, 3006, data);
        }
        return processed;
    }

    /** Exact fastutil 8.5.18 Object2IntOpenHashMap default-table iteration for <=10 cursors. */
    static List<BlockPos> fastutilOrderForTest(List<BlockPos> positions) {
        return fastutilKeyOrder(positions);
    }

    private static List<BlockPos> fastutilKeyOrder(List<BlockPos> insertionOrder) {
        if (insertionOrder.size() > MAX_CURSORS) {
            throw new IllegalArgumentException("sculk cursor map exceeds pinned capacity");
        }
        BlockPos[] table = new BlockPos[32];
        for (BlockPos key : insertionOrder) {
            int slot = fastutilMix(blockPosHash(key)) & 31;
            while (table[slot] != null && !table[slot].equals(key)) slot = slot + 1 & 31;
            if (table[slot] == null) table[slot] = key;
        }
        List<BlockPos> ordered = new ArrayList<>(insertionOrder.size());
        for (int slot = 31; slot >= 0; slot--) {
            if (table[slot] != null) ordered.add(table[slot]);
        }
        return ordered;
    }

    private static int blockPosHash(BlockPos pos) {
        return (pos.y + pos.z * 31) * 31 + pos.x;
    }

    private static int fastutilMix(int hash) {
        int mixed = hash * -1640531527;
        return mixed ^ mixed >>> 16;
    }

    static int aggregateEventData(int totalCharge, int facings) {
        if (totalCharge <= 0 || facings < 0 || facings > 63) {
            throw new IllegalArgumentException("invalid aggregate charge event facts");
        }
        int particles = (int) (Math.log1p(totalCharge) / (double) 2.3F) + 1;
        return (particles << 6) + facings;
    }

    private static void updateCursor(Cursor cursor, BlockPos origin,
            Mc263WorldgenRandomSource random,
            WorldAccess world, Counts counts, TraceSink trace) {
        if (cursor.charge <= 0) return;
        if (cursor.updateDelay > 0) {
            cursor.updateDelay--;
            return;
        }
        State current = read(world, cursor.pos, trace);
        Behaviour behaviour = behaviour(current);
        if (attemptSpreadVein(behaviour, cursor, current, random, world, counts, trace)) {
            if (behaviour != Behaviour.SCULK) {
                current = read(world, cursor.pos, trace);
                behaviour = behaviour(current);
            }
            world.playSculkSpreadSound(cursor.pos.x, cursor.pos.y, cursor.pos.z);
            traceRecord(trace, "sound", 0, cursor.pos.x, cursor.pos.y, cursor.pos.z,
                    stateId(current));
        }
        cursor.charge = attemptUseCharge(behaviour, cursor, origin, random, world, counts, trace);
        if (cursor.charge <= 0) {
            onDischarged(behaviour, current, cursor.pos, random, world, counts, trace);
            return;
        }
        BlockPos transfer = validMovementPos(cursor.pos, origin, random, world, trace);
        if (transfer != null) {
            onDischarged(behaviour, current, cursor.pos, random, world, counts, trace);
            cursor.pos = transfer;
            current = read(world, transfer, trace);
        } else {
            onDischarged(behaviour, current, cursor.pos, random, world, counts, trace);
            cursor.charge = 0;
            return;
        }
        if (isSculkBehaviour(current)) {
            cursor.facings = current.block().equals(VEIN) ? current.faces() : 0;
            cursor.facingsKnown = true;
        }
        // These delays belong to the behaviour at the position being left, not the transferee.
        cursor.decayDelay = behaviour == Behaviour.DEFAULT
                ? Math.max(cursor.decayDelay - 1, 0) : 1;
        cursor.updateDelay = 1;
    }

    private static boolean attemptSpreadVein(Behaviour behaviour, Cursor cursor, State state,
            Mc263WorldgenRandomSource random, WorldAccess world, Counts counts, TraceSink trace) {
        if (behaviour == Behaviour.DEFAULT && cursor.facingsKnown) {
            if (cursor.facings != 0) {
                if (state.air() || state.hasWaterFluid()) {
                    return regrow(cursor.pos, state, cursor.facings, world, counts, trace);
                }
                return false;
            }
            return spreadAll(state, cursor.pos, world, counts, trace, false) > 0;
        }
        if (behaviour == Behaviour.DEFAULT) {
            State fresh = read(world, cursor.pos, trace);
            return spreadAll(fresh, cursor.pos, world, counts, trace, true) > 0;
        }
        return spreadAll(state, cursor.pos, world, counts, trace,
                false) > 0;
    }

    private static boolean regrow(BlockPos pos, State existing, int faces, WorldAccess world,
            Counts counts, TraceSink trace) {
        int attached = 0;
        for (Direction face : DIRECTIONS) {
            if ((faces & face.bit()) == 0) continue;
            boolean canAttach = world.canAttachTo(pos.x, pos.y, pos.z, face);
            traceRecord(trace, "attach", pos.x, pos.y, pos.z, face.ordinal(), canAttach ? 1 : 0);
            if (canAttach) attached |= face.bit();
        }
        if (attached == 0) return false;
        State placed = State.vein(attached, !existing.hasEmptyFluid());
        write(world, pos, placed, DIRECT_FLAGS, counts, trace, 5);
        return true;
    }

    private static long spreadAll(State sourceState, BlockPos sourcePos, WorldAccess world,
            Counts counts, TraceSink trace, boolean sameSpaceOnly) {
        long successes = 0;
        boolean otherSource = !sourceState.block().equals(VEIN);
        for (Direction fromFace : DIRECTIONS) {
            if (!otherSource && !sourceState.hasFace(fromFace)) continue;
            for (Direction spreadDirection : DIRECTIONS) {
                SpreadPos spread = spreadPosition(sourceState, sourcePos, fromFace,
                        spreadDirection, sameSpaceOnly, world, trace);
                if (spread == null) continue;
                State old = read(world, spread.pos, trace);
                State placed = stateForPlacement(old, spread.pos, spread.face, world, trace);
                if (placed == null) continue;
                mark(world, spread.pos, counts, trace, 0);
                if (write(world, spread.pos, placed, SPREAD_FLAGS, counts, trace, 0)) successes++;
            }
        }
        return successes;
    }

    private static SpreadPos spreadPosition(State sourceState, BlockPos sourcePos,
            Direction fromFace, Direction spreadDirection, boolean sameSpaceOnly,
            WorldAccess world, TraceSink trace) {
        if (spreadDirection.axis() == fromFace.axis()) return null;
        if (sourceState.block().equals(VEIN)
                && (!sourceState.hasFace(fromFace) || sourceState.hasFace(spreadDirection))) {
            return null;
        }
        int maxType = sameSpaceOnly ? 1 : 3;
        for (int type = 0; type < maxType; type++) {
            SpreadPos candidate = switch (type) {
                case 0 -> new SpreadPos(sourcePos, spreadDirection);
                case 1 -> new SpreadPos(sourcePos.relative(spreadDirection), fromFace);
                case 2 -> new SpreadPos(sourcePos.relative(spreadDirection).relative(fromFace),
                        spreadDirection.opposite());
                default -> throw new AssertionError(type);
            };
            if (canSpreadInto(sourcePos, candidate, world, trace)) return candidate;
        }
        return null;
    }

    private static boolean canSpreadInto(BlockPos source, SpreadPos spread, WorldAccess world,
            TraceSink trace) {
        State existing = read(world, spread.pos, trace);
        State against = read(world, spread.pos.relative(spread.face), trace);
        if (against.block().equals(SCULK) || against.block().equals(CATALYST)
                || against.block().equals("minecraft:moving_piston")) return false;
        if (source.manhattan(spread.pos) == 2) {
            BlockPos obstruction = source.relative(spread.face.opposite());
            boolean sturdy = world.isFaceSturdy(obstruction.x, obstruction.y, obstruction.z,
                    spread.face);
            traceRecord(trace, "sturdy", obstruction.x, obstruction.y, obstruction.z,
                    spread.face.ordinal(), sturdy ? 1 : 0);
            if (sturdy) return false;
        }
        if (!existing.hasEmptyFluid() && !existing.hasWaterFluid()) return false;
        if (existing.block().equals("minecraft:fire")
                || existing.block().equals("minecraft:soul_fire")) return false;
        boolean replaceable = existing.replaceable() || existing.air()
                || existing.block().equals(VEIN)
                || existing.block().equals(WATER) && existing.fluidSource();
        return replaceable && stateForPlacement(existing, spread.pos, spread.face,
                world, trace) != null;
    }

    private static State stateForPlacement(State old, BlockPos pos, Direction face,
            WorldAccess world, TraceSink trace) {
        if (old.block().equals(VEIN) && old.hasFace(face)) return null;
        boolean attach = world.canAttachTo(pos.x, pos.y, pos.z, face);
        traceRecord(trace, "attach", pos.x, pos.y, pos.z, face.ordinal(), attach ? 1 : 0);
        if (!attach) return null;
        int faces = old.block().equals(VEIN) ? old.faces() : 0;
        boolean waterlogged = old.block().equals(VEIN) ? old.waterlogged()
                : old.fluidSource() && old.fluid().equals(WATER);
        return State.vein(faces | face.bit(), waterlogged);
    }

    private static int attemptUseCharge(Behaviour behaviour, Cursor cursor, BlockPos origin,
            Mc263WorldgenRandomSource random, WorldAccess world, Counts counts, TraceSink trace) {
        return switch (behaviour) {
            case DEFAULT -> cursor.decayDelay > 0 ? cursor.charge : 0;
            case VEIN -> attemptUseVein(cursor, random, world, counts, trace);
            case SCULK -> attemptUseSculk(cursor, origin, random, world, counts, trace);
        };
    }

    private static int attemptUseVein(Cursor cursor, Mc263WorldgenRandomSource random,
            WorldAccess world,
            Counts counts, TraceSink trace) {
        if (attemptPlaceSculk(cursor.pos, random, world, counts, trace)) {
            return cursor.charge - 1;
        }
        return drawInt(random, CHARGE_DECAY_RATE, 300, trace) == 0
                ? (int) Math.floor((float) cursor.charge * 0.5F) : cursor.charge;
    }

    private static boolean attemptPlaceSculk(BlockPos pos, Mc263WorldgenRandomSource random,
            WorldAccess world, Counts counts, TraceSink trace) {
        State vein = read(world, pos, trace);
        for (Direction support : shuffledDirections(random, trace, 400)) {
            if (!vein.hasFace(support)) continue;
            BlockPos supportPos = pos.relative(support);
            State supportState = read(world, supportPos, trace);
            if (!supportState.worldgenSculkReplaceable()) continue;
            State sculk = State.sculk();
            write(world, supportPos, sculk, DIRECT_FLAGS, counts, trace, 1);
            world.pushEntitiesUp(supportPos.x, supportPos.y, supportPos.z, supportState, sculk);
            traceRecord(trace, "push", supportPos.x, supportPos.y, supportPos.z,
                    stateId(supportState), stateId(sculk));
            world.playSculkSpreadSound(supportPos.x, supportPos.y, supportPos.z);
            traceRecord(trace, "sound", 0, supportPos.x, supportPos.y, supportPos.z,
                    stateId(sculk));
            spreadAll(sculk, supportPos, world, counts, trace, false);
            Direction skip = support.opposite();
            for (Direction direction : DIRECTIONS) {
                if (direction == skip) continue;
                BlockPos neighbour = supportPos.relative(direction);
                State possibleVein = read(world, neighbour, trace);
                if (possibleVein.block().equals(VEIN)) {
                    onDischarged(Behaviour.VEIN, possibleVein, neighbour, random,
                            world, counts, trace);
                }
            }
            return true;
        }
        return false;
    }

    private static int attemptUseSculk(Cursor cursor, BlockPos origin,
            Mc263WorldgenRandomSource random,
            WorldAccess world, Counts counts, TraceSink trace) {
        int charge = cursor.charge;
        if (charge == 0 || drawInt(random, CHARGE_DECAY_RATE, 500, trace) != 0) return charge;
        boolean close = cursor.pos.distanceSquared(origin) < 1.0;
        if (close || !canPlaceGrowth(cursor.pos, world, trace)) {
            if (drawInt(random, ADDITIONAL_DECAY_RATE, 501, trace) != 0) return charge;
            return charge - (close ? 1 : decayPenalty(cursor.pos, origin, charge));
        }
        if (drawInt(random, GROWTH_SPAWN_COST, 502, trace) < charge) {
            BlockPos growthPos = cursor.pos.relative(Direction.UP);
            boolean shrieker = drawInt(random, 11, 503, trace) == 0;
            State growth = growthState(shrieker, growthPos, world, trace);
            write(world, growthPos, growth, DIRECT_FLAGS, counts, trace, 2);
            world.playGrowthPlaceSound(cursor.pos.x, cursor.pos.y, cursor.pos.z, growth);
            traceRecord(trace, "sound", 1, cursor.pos.x, cursor.pos.y, cursor.pos.z,
                    stateId(growth));
        }
        return Math.max(0, charge - GROWTH_SPAWN_COST);
    }

    private static boolean canPlaceGrowth(BlockPos pos, WorldAccess world, TraceSink trace) {
        State above = read(world, pos.relative(Direction.UP), trace);
        if (!(above.air() || above.block().equals(WATER) && above.hasWaterFluid())) return false;
        int inhibitors = 0;
        int minX = pos.x - 4;
        int maxX = pos.x + 4;
        int minY = Math.max(HEIGHT_MIN, pos.y);
        int maxY = Math.min(world.minGenerationY() + world.generationDepth() - 1, pos.y + 2);
        int minZ = pos.z - 4;
        int maxZ = pos.z + 4;
        if (minY <= maxY) {
            for (int sectionX = Math.floorDiv(minX, 16);
                    sectionX <= Math.floorDiv(maxX, 16); sectionX++) {
                for (int sectionZ = Math.floorDiv(minZ, 16);
                        sectionZ <= Math.floorDiv(maxZ, 16); sectionZ++) {
                    for (int sectionY = Math.floorDiv(minY, 16);
                            sectionY <= Math.floorDiv(maxY, 16); sectionY++) {
                        boolean mayContain = world.sectionMayContainGrowthInhibitor(
                                sectionX, sectionY, sectionZ);
                        traceRecord(trace, "section", sectionX, sectionY, sectionZ,
                                mayContain ? 1 : 0);
                        if (!mayContain) continue;
                        int fromX = Math.max(minX, sectionX * 16);
                        int toX = Math.min(maxX, sectionX * 16 + 15);
                        int fromY = Math.max(minY, sectionY * 16);
                        int toY = Math.min(maxY, sectionY * 16 + 15);
                        int fromZ = Math.max(minZ, sectionZ * 16);
                        int toZ = Math.min(maxZ, sectionZ * 16 + 15);
                        for (int y = fromY; y <= toY; y++) {
                            for (int z = fromZ; z <= toZ; z++) {
                                for (int x = fromX; x <= toX; x++) {
                                    State state = read(world, new BlockPos(x, y, z), trace);
                                    if (state.growthInhibitor()) {
                                        inhibitors++;
                                        if (inhibitors > 2) {
                                            traceRecord(trace, "inhibitors", inhibitors,
                                                    x, y, z);
                                            return false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        traceRecord(trace, "inhibitors", inhibitors, 5, 3, 5);
        return true;
    }

    static boolean canPlaceGrowthForTest(BlockPos pos, WorldAccess world) {
        return canPlaceGrowth(pos, world, NO_TRACE);
    }

    static boolean movementUnobstructedForTest(BlockPos from, BlockPos to, WorldAccess world) {
        return movementUnobstructed(from, to, world, NO_TRACE);
    }

    static boolean hasSubstrateAccessForTest(State state, BlockPos pos, WorldAccess world) {
        return hasSubstrateAccess(state, pos, world, NO_TRACE);
    }

    static State growthStateForTest(boolean shrieker, BlockPos pos, WorldAccess world) {
        return growthState(shrieker, pos, world, NO_TRACE);
    }

    static State emptyVeinDischargeStateForTest(BlockPos pos, WorldAccess world) {
        return emptyVeinDischargeState(pos, world, NO_TRACE);
    }

    static int defaultNullFacingsReadCountForTest(BlockPos pos, WorldAccess world) {
        Cursor cursor = new Cursor(pos, 1);
        Counts counts = new Counts();
        State first = read(world, pos, NO_TRACE);
        attemptSpreadVein(Behaviour.DEFAULT, cursor, first, new WorldgenRandom(0L),
                world, counts, NO_TRACE);
        return 2;
    }

    static boolean acceptedTargetMutationSkipsSecondAttachForTest(BlockPos source,
            BlockPos target, Direction face, WorldAccess world) {
        SpreadPos spread = new SpreadPos(target, face);
        if (!canSpreadInto(source, spread, world, NO_TRACE)) return false;
        State changed = read(world, target, NO_TRACE);
        return stateForPlacement(changed, target, face, world, NO_TRACE) == null;
    }

    private static int decayPenalty(BlockPos pos, BlockPos origin, int charge) {
        float outerDistanceSquared = square((float) Math.sqrt(pos.distanceSquared(origin))
                - NO_GROWTH_RADIUS);
        int maxReachSquared = (24 - NO_GROWTH_RADIUS) * (24 - NO_GROWTH_RADIUS);
        float factor = Math.min(1.0F, outerDistanceSquared / maxReachSquared);
        return Math.max(1, (int) (charge * factor * 0.5F));
    }

    private static float square(float value) { return value * value; }

    private static BlockPos validMovementPos(BlockPos pos, BlockPos origin,
            Mc263WorldgenRandomSource random, WorldAccess world, TraceSink trace) {
        List<Vec3> offsets = new ArrayList<>(NON_CORNER_NEIGHBOURS);
        shuffle(offsets, random, trace, 600);
        for (Vec3 offset : offsets) {
            BlockPos target = pos.offset(offset.x, offset.y, offset.z);
            if (origin.horizontalDistanceSquared(target) > MAX_WORLDGEN_SPREAD
                    * MAX_WORLDGEN_SPREAD) continue;
            State transferee = read(world, target, trace);
            if (!isSculkBehaviour(transferee)) continue;
            if (!movementUnobstructed(pos, target, world, trace)) continue;
            if (!hasSubstrateAccess(transferee, target, world, trace)) continue;
            return target;
        }
        return null;
    }

    private static boolean movementUnobstructed(BlockPos from, BlockPos to, WorldAccess world,
            TraceSink trace) {
        if (from.manhattan(to) == 1) return true;
        int dx = Integer.compare(to.x, from.x);
        int dy = Integer.compare(to.y, from.y);
        int dz = Integer.compare(to.z, from.z);
        Direction x = dx < 0 ? Direction.WEST : Direction.EAST;
        Direction y = dy < 0 ? Direction.DOWN : Direction.UP;
        Direction z = dz < 0 ? Direction.NORTH : Direction.SOUTH;
        if (dx == 0) return unobstructed(from, y, world, trace)
                || unobstructed(from, z, world, trace);
        if (dy == 0) return unobstructed(from, x, world, trace)
                || unobstructed(from, z, world, trace);
        return unobstructed(from, x, world, trace) || unobstructed(from, y, world, trace);
    }

    private static boolean unobstructed(BlockPos from, Direction direction, WorldAccess world,
            TraceSink trace) {
        BlockPos test = from.relative(direction);
        boolean sturdy = world.isFaceSturdy(test.x, test.y, test.z, direction.opposite());
        traceRecord(trace, "sturdy", test.x, test.y, test.z,
                direction.opposite().ordinal(), sturdy ? 1 : 0);
        return !sturdy;
    }

    private static boolean hasSubstrateAccess(State state, BlockPos pos, WorldAccess world,
            TraceSink trace) {
        if (!state.block().equals(VEIN)) return false;
        for (Direction direction : DIRECTIONS) {
            if (!state.hasFace(direction)) continue;
            State support = read(world, pos.relative(direction), trace);
            if (support.regularSculkReplaceable()) return true;
        }
        return false;
    }

    private static void onDischarged(Behaviour behaviour, State state, BlockPos pos,
            Mc263WorldgenRandomSource random, WorldAccess world, Counts counts, TraceSink trace) {
        if (behaviour != Behaviour.VEIN || !state.block().equals(VEIN)) return;
        int faces = state.faces();
        for (Direction direction : DIRECTIONS) {
            if ((faces & direction.bit()) == 0) continue;
            State neighbour = read(world, pos.relative(direction), trace);
            if (neighbour.block().equals(SCULK)) faces &= ~direction.bit();
        }
        State discharged;
        if (faces == 0) {
            discharged = emptyVeinDischargeState(pos, world, trace);
        } else {
            discharged = State.vein(faces, state.waterlogged());
        }
        write(world, pos, discharged, DIRECT_FLAGS, counts, trace, 3);
    }

    private static boolean write(WorldAccess world, BlockPos pos, State state, int flags,
            Counts counts, TraceSink trace, int kind) {
        counts.attemptedWrites++;
        boolean retained = world.trySetBlockState(pos.x, pos.y, pos.z, state, flags);
        if (retained) counts.retainedWrites++;
        traceRecord(trace, "write", kind, pos.x, pos.y, pos.z, flags, stateId(state),
                state.faces(), state.waterlogged() ? 1 : 0, retained ? 1 : 0);
        return retained;
    }

    private static void mark(WorldAccess world, BlockPos pos, Counts counts, TraceSink trace,
            int kind) {
        counts.attemptedMarks++;
        boolean retained = world.tryMarkPosForPostProcessing(pos.x, pos.y, pos.z);
        if (retained) counts.retainedMarks++;
        traceRecord(trace, "post", kind, pos.x, pos.y, pos.z, retained ? 1 : 0);
    }

    private static State read(WorldAccess world, BlockPos pos, TraceSink trace) {
        State state = world.blockState(pos.x, pos.y, pos.z);
        if (state == null) throw new IllegalArgumentException("null live block state");
        traceRecord(trace, "read", pos.x, pos.y, pos.z, stateId(state));
        return state;
    }

    private static FluidFact readFluid(WorldAccess world, BlockPos pos, TraceSink trace) {
        FluidFact fluid = world.fluidState(pos.x, pos.y, pos.z);
        if (fluid == null) throw new IllegalArgumentException("null live fluid state");
        traceRecord(trace, "fluid", pos.x, pos.y, pos.z, fluidId(fluid));
        return fluid;
    }

    private static State growthState(boolean shrieker, BlockPos pos, WorldAccess world,
            TraceSink trace) {
        FluidFact fluid = readFluid(world, pos, trace);
        return shrieker ? State.shrieker(fluid.fluid()) : State.sensor(fluid.fluid());
    }

    private static State emptyVeinDischargeState(BlockPos pos, WorldAccess world,
            TraceSink trace) {
        FluidFact fluid = readFluid(world, pos, trace);
        return fluid.fluid().equals(EMPTY_FLUID) ? State.airState() : State.water(true);
    }

    private static int drawInt(Mc263WorldgenRandomSource random, int bound, int purpose,
            TraceSink trace) {
        int value = random.nextInt(bound);
        traceRecord(trace, "rng_int", purpose, bound, value);
        return value;
    }

    private static float drawFloat(Mc263WorldgenRandomSource random, int purpose,
            TraceSink trace) {
        float value = random.nextFloat();
        traceRecord(trace, "rng_float", purpose, Float.floatToRawIntBits(value));
        return value;
    }

    private static List<Direction> shuffledDirections(Mc263WorldgenRandomSource random,
            TraceSink trace,
            int purpose) {
        List<Direction> result = new ArrayList<>(List.of(DIRECTIONS));
        shuffle(result, random, trace, purpose);
        return result;
    }

    private static <T> void shuffle(List<T> values, Mc263WorldgenRandomSource random,
            TraceSink trace,
            int purpose) {
        for (int i = values.size(); i > 1; i--) {
            int swap = drawInt(random, i, purpose, trace);
            T previous = values.set(i - 1, values.get(swap));
            values.set(swap, previous);
        }
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        if (events == null) throw new IllegalArgumentException("events are required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeInt(TRACE_MAGIC);
            out.writeShort(TRACE_VERSION);
            out.writeShort(schema.length);
            out.write(schema);
            out.writeInt(events.size());
            for (TraceEvent event : events) {
                if (event == null) throw new IllegalArgumentException("null trace event");
                byte[] phase = event.phase().getBytes(StandardCharsets.UTF_8);
                long[] values = event.values();
                out.writeShort(phase.length);
                out.write(phase);
                out.writeShort(values.length);
                for (long value : values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void traceRecord(TraceSink trace, String phase, long a) {
        if (trace.enabled()) trace.record(phase, new long[]{a});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c,
            long d) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c,
            long d, long e) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c,
            long d, long e, long f) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c,
            long d, long e, long f, long g) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f, g});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c,
            long d, long e, long f, long g, long h) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f, g, h});
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c,
            long d, long e, long f, long g, long h, long i) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f, g, h, i});
    }

    private static List<Vec3> makeNonCornerNeighbours() {
        List<Vec3> result = new ArrayList<>(18);
        // BlockPos.betweenClosed iteration: X fastest, then Y, then Z.
        for (int z = -1; z <= 1; z++) {
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    if ((x == 0 || y == 0 || z == 0) && (x != 0 || y != 0 || z != 0)) {
                        result.add(new Vec3(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static Behaviour behaviour(State state) {
        if (state.block().equals(SCULK)) return Behaviour.SCULK;
        if (state.block().equals(VEIN)) return Behaviour.VEIN;
        return Behaviour.DEFAULT;
    }

    private static boolean isSculkBehaviour(State state) {
        return state.block().equals(SCULK) || state.block().equals(VEIN);
    }

    private static int stateId(State state) {
        return switch (state.block()) {
            case "minecraft:air" -> 0;
            case "minecraft:cave_air" -> 1;
            case "minecraft:void_air" -> 2;
            case WATER -> state.fluidSource() ? 3 : 4;
            case SCULK -> 5;
            case VEIN -> 6 + state.faces() + (state.waterlogged() ? 64 : 0);
            case CATALYST -> 134;
            case SENSOR -> 135 + (state.waterlogged() ? 1 : 0);
            case SHRIEKER -> 137 + (state.waterlogged() ? 1 : 0);
            default -> 139 + Math.floorMod(state.block().hashCode(), 1_000_000);
        };
    }

    private static int fluidId(FluidFact fluid) {
        return switch (fluid.fluid()) {
            case EMPTY_FLUID -> 0;
            case WATER -> fluid.source() ? 1 : 2;
            case FLOWING_WATER -> 2;
            default -> 3 + Math.floorMod(fluid.fluid().hashCode(), 1_000_000);
        };
    }

    private static int biomeId(String biome) {
        int id = 0;
        for (Mc263FeatureIndexReceipt.BiomeFeatureData data : Mc263FeatureIndexReceipt.biomes()) {
            if (data.biomeKey().equals(biome)) return id;
            id++;
        }
        return -1;
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != HEIGHT_MIN
                || world.generationDepth() != 384) {
            throw new IllegalArgumentException("pinned Overworld access is required");
        }
    }

    private static void requireRandomTrace(Mc263WorldgenRandomSource random, TraceSink trace) {
        if (random == null || trace == null) {
            throw new IllegalArgumentException("random and trace are required");
        }
    }

    private static String requireKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:") || key.length() == 10) {
            throw new IllegalArgumentException("minecraft namespaced " + description
                    + " is required: " + key);
        }
        return key;
    }

    private enum Behaviour { DEFAULT, SCULK, VEIN }

    private record Vec3(int x, int y, int z) {
    }

    private record SpreadPos(BlockPos pos, Direction face) {
    }

    private static final class Cursor {
        private BlockPos pos;
        private int charge;
        private int updateDelay;
        private int decayDelay = 1;
        private boolean facingsKnown;
        private int facings;

        private Cursor(BlockPos pos, int charge) {
            this.pos = pos;
            this.charge = Math.min(charge, 1000);
        }
    }

    private static final class Counts {
        private int candidates;
        private int sequenceSuccesses;
        private int patchSuccesses;
        private int attemptedWrites;
        private int retainedWrites;
        private int attemptedMarks;
        private int retainedMarks;

        private PlacementCounts freeze() {
            return new PlacementCounts(candidates, sequenceSuccesses, patchSuccesses,
                    attemptedWrites, retainedWrites, attemptedMarks, retainedMarks);
        }

        private ConfiguredResult configured(boolean sequence, boolean patch, boolean chance,
                boolean predicate) {
            return new ConfiguredResult(sequence, patch, chance, predicate, attemptedWrites,
                    retainedWrites, attemptedMarks, retainedMarks);
        }
    }
}
