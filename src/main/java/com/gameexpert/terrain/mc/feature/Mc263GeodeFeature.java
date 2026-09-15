package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 amethyst-geode placed-feature leaf.
 *
 * <p>The caller supplies a live mutable five-by-five FEATURES region. Block writes may be clipped
 * by that region, but reads and fluid-tick scheduling must remain available through source radius
 * two. No canonical dispatcher calls this class until block-state and fluid-tick sidecars are
 * integrated and Java/Rust traces are identical.</p>
 */
public final class Mc263GeodeFeature {
    public static final int LOCAL_MODIFICATIONS_STEP = 2;
    public static final int GLOBAL_INDEX = 2;
    public static final String AMETHYST_GEODE = "minecraft:amethyst_geode";

    public static final int STATE_AIR = 0;
    public static final int STATE_AMETHYST_BLOCK = 1;
    public static final int STATE_BUDDING_AMETHYST = 2;
    public static final int STATE_CALCITE = 3;
    public static final int STATE_SMOOTH_BASALT = 4;
    public static final int STATE_SMALL_BUD = 5;
    public static final int STATE_MEDIUM_BUD = 6;
    public static final int STATE_LARGE_BUD = 7;
    public static final int STATE_CLUSTER = 8;

    public static final int BRANCH_SKIP = 0;
    public static final int BRANCH_FILLING = 1;
    public static final int BRANCH_CRACK = 2;
    public static final int BRANCH_INNER = 3;
    public static final int BRANCH_MIDDLE = 4;
    public static final int BRANCH_OUTER = 5;

    public static final int STAGE_FILLING = 0;
    public static final int STAGE_CRACK = 1;
    public static final int STAGE_INNER = 2;
    public static final int STAGE_ALTERNATE_INNER = 3;
    public static final int STAGE_MIDDLE = 4;
    public static final int STAGE_OUTER = 5;
    public static final int STAGE_BUD_BASE = 10;

    public static final int FLUID_EMPTY = 0;
    public static final int FLUID_WATER = 1;
    public static final int FLUID_FLOWING_WATER = 2;
    public static final int FLUID_LAVA = 3;
    public static final int FLUID_FLOWING_LAVA = 4;

    private static final int MIN_OFFSET = -16;
    private static final int MAX_OFFSET = 16;
    private static final int INVALID_BLOCK_THRESHOLD = 1;
    private static final double NOISE_MULTIPLIER = 0.05D;
    private static final double ALTERNATE_CHANCE = 0.083D;
    private static final double POTENTIAL_CHANCE = 0.35D;
    private static final double CRACK_CHANCE = 0.95D;

    private static final String AIR = "minecraft:air";
    private static final String CAVE_AIR = "minecraft:cave_air";
    private static final String VOID_AIR = "minecraft:void_air";
    private static final String WATER = "minecraft:water";
    private static final String AMETHYST_BLOCK = "minecraft:amethyst_block";
    private static final String BUDDING_AMETHYST = "minecraft:budding_amethyst";
    private static final String CALCITE = "minecraft:calcite";
    private static final String SMOOTH_BASALT = "minecraft:smooth_basalt";
    private static final String[] BUD_BLOCKS = {
            "minecraft:small_amethyst_bud",
            "minecraft:medium_amethyst_bud",
            "minecraft:large_amethyst_bud",
            "minecraft:amethyst_cluster"
    };
    private static final int[] BUD_STATE_CODES = {
            STATE_SMALL_BUD, STATE_MEDIUM_BUD, STATE_LARGE_BUD, STATE_CLUSTER
    };
    private static final Set<String> GEODE_INVALID_BLOCKS = Set.of(
            "minecraft:bedrock", "minecraft:water", "minecraft:lava", "minecraft:ice",
            "minecraft:packed_ice", "minecraft:blue_ice");
    private static final Set<String> FEATURES_CANNOT_REPLACE = Set.of(
            "minecraft:bedrock", "minecraft:spawner", "minecraft:chest",
            "minecraft:end_portal_frame", "minecraft:reinforced_deepslate",
            "minecraft:trial_spawner", "minecraft:vault");
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final TraceSink NO_TRACE = (phase, values) -> { };

    private Mc263GeodeFeature() {
    }

    /** Pure exact state/tick closure for every geode layer and bud permutation. */
    public static void preflight(Predicate<String> supportsState, boolean supportsTicks) {
        Mc263LakeFeature.requireOutputs(supportsState, CAVE_AIR, AMETHYST_BLOCK,
                BUDDING_AMETHYST, CALCITE, SMOOTH_BASALT);
        for (String block : BUD_BLOCKS) {
            for (Direction direction : DIRECTIONS) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    Mc263LakeFeature.requireOutputs(supportsState,
                            budState(block, direction, waterlogged));
                }
            }
        }
        if (!supportsTicks) throw new UnsupportedOperationException("geode fluid ticks");
    }

    /** Complete live semantic view required by the official feature. */
    public interface WorldAccess {
        long worldSeed();

        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        BlockStateFacts blockState(int blockX, int blockY, int blockZ);

        /** Equivalent to {@code setBlock(..., flags=2)}; false includes clipped/no-op writes. */
        boolean setBlockState(int blockX, int blockY, int blockZ, String state);

        /** Schedules a fluid tick independently of the block-write radius. */
        void scheduleFluidTick(int blockX, int blockY, int blockZ, String fluidType, int delay);
    }

    /** Live facts for one canonical block state, checked against pinned semantic membership. */
    public record BlockStateFacts(String state, boolean air, boolean geodeInvalid,
                                  boolean featuresCannotReplace, FluidFacts fluid) {
        public BlockStateFacts {
            state = requireCanonicalBlockState(state);
            if (fluid == null) throw new IllegalArgumentException("fluid facts are required");
            String block = Mc263GeodeFeature.blockKey(state);
            boolean expectedAir = block.equals(AIR) || block.equals(CAVE_AIR)
                    || block.equals(VOID_AIR);
            if (air != expectedAir) {
                throw new IllegalArgumentException("air fact contradicts block state: " + state);
            }
            if (geodeInvalid != GEODE_INVALID_BLOCKS.contains(block)) {
                throw new IllegalArgumentException(
                        "geode-invalid fact contradicts block state: " + state);
            }
            if (featuresCannotReplace != FEATURES_CANNOT_REPLACE.contains(block)) {
                throw new IllegalArgumentException(
                        "cannot-replace fact contradicts block state: " + state);
            }
        }

        public String blockKey() {
            return Mc263GeodeFeature.blockKey(state);
        }
    }

    public record FluidFacts(String type, boolean empty, boolean source, int amount) {
        public FluidFacts {
            type = requireMinecraftKey(type, "fluid");
            boolean valid = switch (type) {
                case "minecraft:empty" -> empty && !source && amount == 0;
                case "minecraft:water", "minecraft:lava" ->
                    !empty && source && amount == 8;
                case "minecraft:flowing_water", "minecraft:flowing_lava" ->
                    !empty && !source && amount >= 1 && amount <= 8;
                default -> false;
            };
            if (!valid) {
                throw new IllegalArgumentException("contradictory pinned fluid facts: " + type
                        + " empty=" + empty + " source=" + source + " amount=" + amount);
            }
        }
    }

    /** Stable all-i64 trace stream shared with the Rust leaf. */
    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
    }

    public record BlockPos(int x, int y, int z) {
        BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidates, int configuredAccepted) {
    }

    public record PlacementCounts(int candidates, int configuredAccepted) {
        public PlacementCounts {
            if (candidates < 0 || candidates > 1 || configuredAccepted < 0
                    || configuredAccepted > candidates) {
                throw new IllegalArgumentException("invalid geode placement counts");
            }
        }
    }

    public record ConfiguredResult(boolean placed, int attemptedWrites, int acceptedWrites,
                                   int scheduledTicks, int potentials, int budsAttempted,
                                   int budsAccepted) {
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs rarity, in-square, uniform-height, biome and configured feature on one stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (world.worldSeed() != worldSeed) {
            throw new IllegalArgumentException("world seed and world access seed differ");
        }
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, LOCAL_MODIFICATIONS_STEP, AMETHYST_GEODE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(), GLOBAL_INDEX,
                counts.candidates(), counts.configuredAccepted());
    }

    /** Executes every placed modifier and the configured feature on the supplied feature stream. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random) {
        return placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world, random, NO_TRACE);
    }

    /** Executes every placed modifier and the configured feature on the supplied feature stream. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        boolean tracing = events != NO_TRACE;
        int index = Mc263DecorationRandom.globalIndex(LOCAL_MODIFICATIONS_STEP, AMETHYST_GEODE);
        if (index != GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(LOCAL_MODIFICATIONS_STEP)
                .featureAt(GLOBAL_INDEX).equals(AMETHYST_GEODE)) {
            throw new IllegalStateException("pinned geode feature index changed");
        }

        float rarity = random.nextFloat();
        boolean rarityAccepted = rarity < 1.0F / 24.0F;
        if (tracing) {
            events.record("rarity", unsignedFloatBits(rarity), rarityAccepted ? 1L : 0L);
        }
        if (!rarityAccepted) {
            if (tracing) events.record("placed_result", GLOBAL_INDEX, 0L, 0L, 0L);
            return new PlacementCounts(0, 0);
        }

        int x = sourceBlockX + random.nextInt(16);
        int z = sourceBlockZ + random.nextInt(16);
        int y = -58 + random.nextInt(89);
        if (tracing) events.record("candidate", x, y, z);
        boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z));
        if (tracing) events.record("biome", biomeAccepted ? 1L : 0L);
        if (!biomeAccepted) {
            if (tracing) events.record("placed_result", GLOBAL_INDEX, 1L, 0L, 0L);
            return new PlacementCounts(1, 0);
        }

        ConfiguredResult result = placeConfigured(random, new BlockPos(x, y, z), world, events);
        if (tracing) {
            events.record("placed_result", GLOBAL_INDEX, 1L, result.placed() ? 1L : 0L, 1L);
        }
        return new PlacementCounts(1, result.placed() ? 1 : 0);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes the configured feature after the placed modifiers select an origin. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        boolean tracing = events != NO_TRACE;
        Counters counters = new Counters();

        int numPoints = random.nextInt(2) + 3;
        McNormalNoise noise = McNormalNoise.legacyParity(world.worldSeed(), -4,
                new double[] {1.0D});
        double adjustment = (double) numPoints / 6.0D;
        double airThreshold = invSqrt(1.7D);
        double innerThreshold = invSqrt(2.2D + adjustment);
        double middleThreshold = invSqrt(3.2D + adjustment);
        double outerThreshold = invSqrt(4.2D + adjustment);
        double crackThreshold = invSqrt(2.0D + random.nextDouble() / 2.0D
                + (numPoints > 3 ? adjustment : 0.0D));
        float crackRandom = random.nextFloat();
        boolean shouldCrack = (double) crackRandom < CRACK_CHANCE;
        if (tracing) {
            events.record("configured", numPoints, Double.doubleToRawLongBits(crackThreshold),
                    unsignedFloatBits(crackRandom), shouldCrack ? 1L : 0L);
        }

        List<WeightedPoint> points = new ArrayList<>(numPoints);
        int invalidCount = 0;
        for (int pointIndex = 0; pointIndex < numPoints; pointIndex++) {
            BlockPos point = origin.offset(4 + random.nextInt(3), 4 + random.nextInt(3),
                    4 + random.nextInt(3));
            BlockStateFacts facts = requireFacts(world.blockState(point.x, point.y, point.z));
            if (facts.air() || facts.geodeInvalid()) invalidCount++;
            if (invalidCount > INVALID_BLOCK_THRESHOLD) {
                if (tracing) {
                    events.record("point", pointIndex, point.x, point.y, point.z, invalidCount,
                            -1L);
                }
                ConfiguredResult failed = new ConfiguredResult(false, 0, 0, 0, 0, 0, 0);
                recordResult(events, tracing, failed);
                return failed;
            }
            int pointOffset = 1 + random.nextInt(2);
            points.add(new WeightedPoint(point, pointOffset));
            if (tracing) {
                events.record("point", pointIndex, point.x, point.y, point.z, invalidCount,
                        pointOffset);
            }
        }

        List<BlockPos> crackPoints = new ArrayList<>(3);
        if (shouldCrack) {
            int direction = random.nextInt(4);
            if (tracing) events.record("crack_direction", direction);
            int reach = numPoints * 2 + 1;
            switch (direction) {
                case 0 -> addCrackColumn(crackPoints, origin, reach, 0);
                case 1 -> addCrackColumn(crackPoints, origin, 0, reach);
                case 2 -> addCrackColumn(crackPoints, origin, reach, reach);
                case 3 -> addCrackColumn(crackPoints, origin, 0, 0);
                default -> throw new IllegalStateException("unreachable crack direction");
            }
        }
        if (tracing) {
            events.record("threshold", Double.doubleToRawLongBits(airThreshold),
                    Double.doubleToRawLongBits(innerThreshold),
                    Double.doubleToRawLongBits(middleThreshold),
                    Double.doubleToRawLongBits(outerThreshold));
        }

        List<BlockPos> potentials = new ArrayList<>();
        for (int z = origin.z + MIN_OFFSET; z <= origin.z + MAX_OFFSET; z++) {
            for (int y = origin.y + MIN_OFFSET; y <= origin.y + MAX_OFFSET; y++) {
                for (int x = origin.x + MIN_OFFSET; x <= origin.x + MAX_OFFSET; x++) {
                    float sampledNoise = (float) noise.getValue(x, y, z);
                    double noiseOffset = (double) sampledNoise * NOISE_MULTIPLIER;
                    double shell = 0.0D;
                    for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                        WeightedPoint point = points.get(pointIndex);
                        shell += invSqrt(distanceSquared(x, y, z, point.pos)
                                + (double) point.offset) + noiseOffset;
                    }
                    if (shell < outerThreshold) {
                        recordCell(events, tracing, x, y, z, sampledNoise, shell, BRANCH_SKIP,
                                -1L, -1L);
                        continue;
                    }
                    if (shell >= airThreshold) {
                        recordCell(events, tracing, x, y, z, sampledNoise, shell,
                                BRANCH_FILLING, -1L, -1L);
                        safeSet(world, x, y, z, AIR, STATE_AIR, STAGE_FILLING, events, tracing,
                                counters);
                        continue;
                    }

                    double crackDensity = 0.0D;
                    for (int crackIndex = 0; crackIndex < crackPoints.size(); crackIndex++) {
                        BlockPos crackPoint = crackPoints.get(crackIndex);
                        crackDensity += invSqrt(distanceSquared(x, y, z, crackPoint) + 2.0D)
                                + noiseOffset;
                    }
                    if (shouldCrack && crackDensity >= crackThreshold) {
                        recordCell(events, tracing, x, y, z, sampledNoise, shell, BRANCH_CRACK,
                                -1L, -1L);
                        safeSet(world, x, y, z, AIR, STATE_AIR, STAGE_CRACK, events, tracing,
                                counters);
                        for (Direction direction : DIRECTIONS) {
                            int targetX = x + direction.dx;
                            int targetY = y + direction.dy;
                            int targetZ = z + direction.dz;
                            FluidFacts fluid = requireFacts(world.blockState(
                                    targetX, targetY, targetZ)).fluid();
                            boolean schedule = !fluid.empty();
                            if (tracing) {
                                events.record("fluid_probe", x, y, z, direction.ordinal(),
                                        fluidCode(fluid.type()), fluid.empty() ? 1L : 0L,
                                        fluid.source() ? 1L : 0L, fluid.amount(),
                                        schedule ? 1L : 0L);
                            }
                            if (schedule) {
                                world.scheduleFluidTick(targetX, targetY, targetZ, fluid.type(), 0);
                                counters.scheduledTicks++;
                            }
                        }
                        continue;
                    }

                    if (shell >= innerThreshold) {
                        float alternateRandom = random.nextFloat();
                        boolean alternate = (double) alternateRandom < ALTERNATE_CHANCE;
                        safeSet(world, x, y, z,
                                alternate ? BUDDING_AMETHYST : AMETHYST_BLOCK,
                                alternate ? STATE_BUDDING_AMETHYST : STATE_AMETHYST_BLOCK,
                                alternate ? STAGE_ALTERNATE_INNER : STAGE_INNER,
                                events, tracing, counters);
                        long potentialRaw = -1L;
                        if (alternate) {
                            float potentialRandom = random.nextFloat();
                            potentialRaw = unsignedFloatBits(potentialRandom);
                            if ((double) potentialRandom < POTENTIAL_CHANCE) {
                                BlockPos potential = new BlockPos(x, y, z);
                                potentials.add(potential);
                                if (tracing) events.record("potential", x, y, z);
                            }
                        }
                        recordCell(events, tracing, x, y, z, sampledNoise, shell, BRANCH_INNER,
                                unsignedFloatBits(alternateRandom), potentialRaw);
                        continue;
                    }
                    if (shell >= middleThreshold) {
                        recordCell(events, tracing, x, y, z, sampledNoise, shell, BRANCH_MIDDLE,
                                -1L, -1L);
                        safeSet(world, x, y, z, CALCITE, STATE_CALCITE, STAGE_MIDDLE,
                                events, tracing, counters);
                        continue;
                    }
                    recordCell(events, tracing, x, y, z, sampledNoise, shell, BRANCH_OUTER,
                            -1L, -1L);
                    safeSet(world, x, y, z, SMOOTH_BASALT, STATE_SMOOTH_BASALT, STAGE_OUTER,
                            events, tracing, counters);
                }
            }
        }

        counters.potentials = potentials.size();
        for (int candidateIndex = 0; candidateIndex < potentials.size(); candidateIndex++) {
            BlockPos candidate = potentials.get(candidateIndex);
            int budIndex = random.nextInt(BUD_BLOCKS.length);
            counters.budsAttempted++;
            if (tracing) {
                events.record("bud_candidate", candidateIndex, candidate.x, candidate.y,
                        candidate.z, budIndex);
            }
            for (Direction direction : DIRECTIONS) {
                int targetX = candidate.x + direction.dx;
                int targetY = candidate.y + direction.dy;
                int targetZ = candidate.z + direction.dz;
                BlockStateFacts target = requireFacts(world.blockState(targetX, targetY, targetZ));
                FluidFacts fluid = target.fluid();
                boolean growable = target.air()
                        || target.blockKey().equals(WATER) && fluid.amount() == 8;
                boolean waterlogged = fluid.source();
                if (tracing) {
                    events.record("bud_probe", candidateIndex, direction.ordinal(), targetX,
                            targetY, targetZ, fluidCode(fluid.type()),
                            fluid.empty() ? 1L : 0L, fluid.source() ? 1L : 0L, fluid.amount(),
                            growable ? 1L : 0L, waterlogged ? 1L : 0L);
                }
                if (!growable) continue;
                String state = budState(BUD_BLOCKS[budIndex], direction, waterlogged);
                boolean accepted = safeSet(world, targetX, targetY, targetZ, state,
                        BUD_STATE_CODES[budIndex], STAGE_BUD_BASE + budIndex, events, tracing,
                        counters);
                if (accepted) counters.budsAccepted++;
                break;
            }
        }

        ConfiguredResult result = new ConfiguredResult(true, counters.attemptedWrites,
                counters.acceptedWrites, counters.scheduledTicks, counters.potentials,
                counters.budsAttempted, counters.budsAccepted);
        recordResult(events, tracing, result);
        return result;
    }

    private static boolean safeSet(WorldAccess world, int x, int y, int z, String state,
            int stateCode, int stageCode, TraceSink trace, boolean tracing, Counters counters) {
        BlockStateFacts current = requireFacts(world.blockState(x, y, z));
        boolean eligible = !current.featuresCannotReplace();
        boolean accepted = false;
        if (eligible) {
            counters.attemptedWrites++;
            accepted = world.setBlockState(x, y, z, state);
            if (accepted) counters.acceptedWrites++;
        }
        if (tracing) {
            trace.record("safe_set", stageCode, x, y, z, stateCode, eligible ? 1L : 0L,
                    eligible ? 1L : 0L, accepted ? 1L : 0L);
        }
        return accepted;
    }

    private static void addCrackColumn(List<BlockPos> points, BlockPos origin, int dx, int dz) {
        points.add(origin.offset(dx, 7, dz));
        points.add(origin.offset(dx, 5, dz));
        points.add(origin.offset(dx, 1, dz));
    }

    private static void recordCell(TraceSink trace, boolean tracing, int x, int y, int z,
            float noise, double shell, int branch, long alternateRaw, long potentialRaw) {
        if (tracing) {
            trace.record("cell", x, y, z, unsignedFloatBits(noise),
                    Double.doubleToRawLongBits(shell), branch, alternateRaw, potentialRaw);
        }
    }

    private static void recordResult(TraceSink trace, boolean tracing, ConfiguredResult result) {
        if (tracing) {
            trace.record("configured_result", result.placed ? 1L : 0L, result.attemptedWrites,
                    result.acceptedWrites, result.scheduledTicks, result.potentials,
                    result.budsAttempted, result.budsAccepted);
        }
    }

    private static double distanceSquared(int x, int y, int z, BlockPos other) {
        double dx = (double) x - (double) other.x;
        double dy = (double) y - (double) other.y;
        double dz = (double) z - (double) other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double invSqrt(double value) {
        return 1.0D / Math.sqrt(value);
    }

    private static String budState(String block, Direction direction, boolean waterlogged) {
        return block + "[facing=" + direction.key + ",waterlogged=" + waterlogged + "]";
    }

    public static int fluidCode(String fluidType) {
        return switch (fluidType) {
            case "minecraft:empty" -> FLUID_EMPTY;
            case "minecraft:water" -> FLUID_WATER;
            case "minecraft:flowing_water" -> FLUID_FLOWING_WATER;
            case "minecraft:lava" -> FLUID_LAVA;
            case "minecraft:flowing_lava" -> FLUID_FLOWING_LAVA;
            default -> throw new IllegalArgumentException("unsupported pinned fluid: " + fluidType);
        };
    }

    public static boolean isPinnedGeodeInvalidBlock(String blockKey) {
        return GEODE_INVALID_BLOCKS.contains(requireMinecraftKey(blockKey, "block"));
    }

    public static boolean isPinnedFeaturesCannotReplaceBlock(String blockKey) {
        return FEATURES_CANNOT_REPLACE.contains(requireMinecraftKey(blockKey, "block"));
    }

    private static long unsignedFloatBits(float value) {
        return Integer.toUnsignedLong(Float.floatToRawIntBits(value));
    }

    private static boolean biomeContains(String biomeKey) {
        String key = requireMinecraftKey(biomeKey, "biome");
        List<Mc263FeatureIndexReceipt.FeatureReference> features = Mc263FeatureIndexReceipt
                .biome(key).featuresAtStep(LOCAL_MODIFICATIONS_STEP);
        for (int index = 0; index < features.size(); index++) {
            if (features.get(index).featureKey().equals(AMETHYST_GEODE)) return true;
        }
        return false;
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

    private static BlockStateFacts requireFacts(BlockStateFacts facts) {
        if (facts == null) throw new IllegalArgumentException("world returned null block facts");
        return facts;
    }

    private static String requireCanonicalBlockState(String state) {
        if (state == null) {
            throw new IllegalArgumentException("exact Minecraft namespaced block state is required");
        }
        int properties = state.indexOf('[');
        String block = properties < 0 ? state : state.substring(0, properties);
        requireMinecraftKey(block, "block");
        if (properties < 0) return state;
        if (!state.endsWith("]") || properties == state.length() - 2
                || state.indexOf('[', properties + 1) >= 0
                || state.indexOf(']') != state.length() - 1) {
            throw new IllegalArgumentException("malformed canonical block state: " + state);
        }
        int entryStart = properties + 1;
        while (entryStart < state.length() - 1) {
            int comma = state.indexOf(',', entryStart);
            int entryEnd = comma < 0 ? state.length() - 1 : comma;
            int equals = state.indexOf('=', entryStart);
            if (equals <= entryStart || equals >= entryEnd - 1) {
                throw new IllegalArgumentException("malformed block-state property: " + state);
            }
            requirePropertyToken(state, entryStart, equals, false);
            requirePropertyToken(state, equals + 1, entryEnd, true);
            if (hasEarlierProperty(state, properties + 1, entryStart, equals)) {
                throw new IllegalArgumentException("duplicate block-state property: " + state);
            }
            if (comma < 0) break;
            if (comma == state.length() - 2) {
                throw new IllegalArgumentException("malformed canonical block state: " + state);
            }
            entryStart = comma + 1;
        }
        return state;
    }

    private static String requireMinecraftKey(String key, String kind) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact Minecraft namespaced " + kind
                    + " key is required: " + key);
        }
        for (int index = "minecraft:".length(); index < key.length(); index++) {
            if (!isPathCharacter(key.charAt(index))) {
                throw new IllegalArgumentException("invalid Minecraft " + kind + " key: " + key);
            }
        }
        return key;
    }

    private static void requirePropertyToken(String state, int start, int end,
            boolean allowColon) {
        for (int index = start; index < end; index++) {
            char value = state.charAt(index);
            if (!isPathCharacter(value) && !(allowColon && value == ':')) {
                throw new IllegalArgumentException("invalid block-state property: " + state);
            }
        }
    }

    private static boolean hasEarlierProperty(String state, int bodyStart, int currentStart,
            int currentEnd) {
        int entryStart = bodyStart;
        while (entryStart < currentStart) {
            int equals = state.indexOf('=', entryStart);
            if (equals < 0 || equals >= currentStart) return false;
            if (equals - entryStart == currentEnd - currentStart
                    && state.regionMatches(entryStart, state, currentStart,
                    currentEnd - currentStart)) {
                return true;
            }
            int comma = state.indexOf(',', equals + 1);
            if (comma < 0 || comma >= currentStart) return false;
            entryStart = comma + 1;
        }
        return false;
    }

    private static boolean isPathCharacter(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9'
                || value == '_' || value == '-' || value == '.' || value == '/';
    }

    private static String blockKey(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private record WeightedPoint(BlockPos pos, int offset) {
    }

    private static final class Counters {
        private int attemptedWrites;
        private int acceptedWrites;
        private int scheduledTicks;
        private int potentials;
        private int budsAttempted;
        private int budsAccepted;
    }

    /** Official {@code Direction.values()} order and deltas. */
    public enum Direction {
        DOWN("down", 0, -1, 0),
        UP("up", 0, 1, 0),
        NORTH("north", 0, 0, -1),
        SOUTH("south", 0, 0, 1),
        WEST("west", -1, 0, 0),
        EAST("east", 1, 0, 0);

        private final String key;
        private final int dx;
        private final int dy;
        private final int dz;

        Direction(String key, int dx, int dy, int dz) {
            this.key = key;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
