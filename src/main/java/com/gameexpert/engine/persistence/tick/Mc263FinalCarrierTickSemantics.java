package com.gameexpert.engine.persistence.tick;

import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.BlockMutation;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.Lane;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.LiveTypes;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.ScheduledTick;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.TickMutation;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.TickSemantics;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pinned 26.3 semantic dispatcher for the block and fluid tick types that the current world-state
 * carrier can represent without loss.
 *
 * <p>The scheduler owns ordering and the durable transaction. This class owns deterministic block
 * semantics. Fluid evolution remains behind {@link SemanticWorld#planFluidTick}, because the
 * production world must use its complete collision, replacement and slope view; returning the
 * resulting immutable mutation plan lets the scheduler persist that plan in the same transaction
 * that consumes the due row. Unknown types and incomplete world views fail closed.</p>
 */
public final class Mc263FinalCarrierTickSemantics implements TickSemantics {

    /** Exact live-world projection required to plan a durable semantic tick. */
    public interface SemanticWorld extends LiveTypes {
        /** Canonical exact block state, including all represented properties. */
        String exactBlockStateAt(int x, int y, int z);

        /** Encodes one supported exact state into the runtime's u16 block/state pair. */
        BlockMutation encodeExactState(int x, int y, int z, String exactState);

        /**
         * Plans one complete pinned fluid update against a stable world snapshot. The returned
         * writes must include mixing, source/flow changes and spread writes in encounter order.
         */
        TickMutation planFluidTick(ScheduledTick tick);
    }

    @Override
    public TickMutation plan(ScheduledTick tick, LiveTypes liveTypes) {
        Objects.requireNonNull(tick, "tick");
        if (!(liveTypes instanceof SemanticWorld world)) {
            throw new IllegalStateException(
                    "pinned 26.3 tick semantics requires an exact semantic world view");
        }
        return tick.lane() == Lane.BLOCK ? planBlock(tick, world) : planFluid(tick, world);
    }

    private static TickMutation planBlock(ScheduledTick tick, SemanticWorld world) {
        Mc263FeatureBlockState current = exact(world, tick.x(), tick.y(), tick.z());
        if (current.blockId() != tick.expectedBlockId()
                || !current.blockKey().equals(tick.typeKey())) {
            throw new IllegalStateException("live exact block state contradicts due BTIK");
        }
        return switch (tick.typeKey()) {
            case "minecraft:cave_air" -> TickMutation.NONE;
            case "minecraft:pale_hanging_moss" -> planPaleHangingMoss(tick, world, current);
            case "minecraft:sugar_cane" -> planSugarCane(tick, world, current);
            case "minecraft:sulfur_spike", "minecraft:pointed_dripstone" ->
                    planSpeleothem(tick, world, current);
            default -> {
                if (!current.isLeavesTag()) {
                    throw new IllegalArgumentException(
                            "unsupported pinned 26.3 block tick type: " + tick.typeKey());
                }
                yield planLeaves(tick, world, current);
            }
        };
    }

    /**
     * Sugar cane inherits the pinned {@code SugarCaneBlock#tick} contract, which is support-only:
     * vanilla runs {@code if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);} and
     * nothing else. Growth lives in {@code SugarCaneBlock#randomTick} - the {@code i < 3} height
     * cap over {@code AGE_15} - and never reaches the scheduled lane, so it is deliberately absent
     * here; the pinned carrier catalog cannot even represent {@code age > 0}
     * ({@code Mc263FeatureBlockState} registers only {@code minecraft:sugar_cane[age=0]}), so a
     * growth port would have no representable target state on this boundary.
     *
     * <p>Sugar cane is never waterlogged, so {@code Level#destroyBlock}'s
     * {@code fluidstate.createLegacyBlock()} is always air. Drops and the neighbour re-scheduling
     * that {@code destroyBlock} triggers are outside this block-only mutation boundary, exactly as
     * for leaf decay.</p>
     */
    private static TickMutation planSugarCane(ScheduledTick tick, SemanticWorld world,
            Mc263FeatureBlockState current) {
        if (current.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE) {
            throw new IllegalStateException("sugar cane carrier state carries a fluid");
        }
        return sugarCaneSurvives(world, tick.x(), tick.y(), tick.z())
                ? TickMutation.NONE
                : replace(tick, world, "minecraft:air");
    }

    /**
     * Exact port of {@code SugarCaneBlock#canSurvive}: cane on cane always survives; otherwise the
     * block below must support cane and one of its four horizontal neighbours must hold a
     * {@code #water} fluid or be frosted ice.
     *
     * <p>The support closure is the same pinned 26.3 closure the generation-side
     * {@code Mc263FeatureWorldAdapter.sugarCaneSurvives} resolves, and that identity is required,
     * not cosmetic: every due BTIK row in this lane was scheduled by the post lane precisely
     * because that predicate answered false, so a runtime predicate that answered differently
     * would contradict the row it is replaying.</p>
     */
    private static boolean sugarCaneSurvives(SemanticWorld world, int x, int y, int z) {
        Mc263FeatureBlockState below = neighbor(world, x, y - 1, z);
        if (below.blockKey().equals("minecraft:sugar_cane")) return true;
        if (!supportsSugarCane(below.blockKey())) return false;
        // Direction.Plane.HORIZONTAL order: NORTH, EAST, SOUTH, WEST.
        for (int[] offset : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            Mc263FeatureBlockState side = neighbor(world, x + offset[0], y - 1, z + offset[1]);
            // Vanilla reads getBlockState and getFluidState of the same position; the exact
            // carrier state answers both facts from one read.
            if (side.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                    || side.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_FLOWING) {
                return true;
            }
            // Frosted ice is the only remaining member of the pinned closure and still has no
            // protocol identity, exactly as the generation-side adapter records.
            if (side.blockKey().equals("minecraft:frosted_ice")) return true;
        }
        return false;
    }

    /**
     * Pinned sugar-cane support closure. The vegetation half is read from the one public pinned
     * predicate rather than restated. The sand half has no pinned accessor yet and is the exact
     * closure of the private {@code Mc263FeatureWorldAdapter.supportsCactus}; exposing that closure
     * once, so this file can read it too, is a handoff on the unowned feature-state files.
     */
    private static boolean supportsSugarCane(String blockKey) {
        return Mc263FeatureBlockState.supportsVegetationTag(blockKey)
                || blockKey.equals("minecraft:sand")
                || blockKey.equals("minecraft:red_sand")
                || blockKey.equals("minecraft:suspicious_sand");
    }

    /** Scheduled downward ticks fall even if support was restored during the delay. */
    private static TickMutation planSpeleothem(ScheduledTick tick, SemanticWorld world,
            Mc263FeatureBlockState current) {
        Map<String, String> state = properties(current.exactState());
        String direction = state.get("vertical_direction");
        if ("down".equals(direction)) {
            var mutations = new java.util.ArrayList<BlockMutation>();
            for (int y = tick.y(); y >= Blocks.MIN_Y; y--) {
                Mc263FeatureBlockState segment = neighbor(world, tick.x(), y, tick.z());
                if (!SpeleothemFallIntents.isSpeleothem(segment.blockId())) break;
                Map<String, String> properties = properties(segment.exactState());
                if (!"down".equals(properties.get("vertical_direction"))) break;
                mutations.add(world.encodeExactState(tick.x(), y, tick.z(),
                        booleanProperty(properties, "waterlogged") ? "minecraft:water" : "minecraft:air"));
                String thickness = properties.get("thickness");
                if ("tip".equals(thickness) || "tip_merge".equals(thickness)) break;
            }
            return new TickMutation(mutations);
        }
        if (!"up".equals(direction)) throw new IllegalArgumentException("invalid vertical_direction");
        Mc263FeatureBlockState support = neighbor(world, tick.x(), tick.y() - 1, tick.z());
        // SpeleothemBlock#isValidSpeleothemPlacement accepts a directed speleothem support only
        // when it is this same block; the SPELEOTHEMS tag alone is insufficient for upward chains.
        boolean sameDirectedSpike = support.blockId() == current.blockId()
                && "up".equals(properties(support.exactState()).get("vertical_direction"));
        if (support.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP) || sameDirectedSpike) {
            return TickMutation.NONE;
        }
        return replace(tick, world, booleanProperty(state, "waterlogged") ? "minecraft:water" : "minecraft:air");
    }

    private static TickMutation planPaleHangingMoss(ScheduledTick tick, SemanticWorld world,
            Mc263FeatureBlockState current) {
        Mc263FeatureBlockState above = neighbor(world, tick.x(), tick.y() + 1, tick.z());
        boolean supported = above.blockKey().equals("minecraft:pale_hanging_moss")
                || above.isFaceSturdyDown();
        if (!supported) return replace(tick, world, "minecraft:air");

        Mc263FeatureBlockState below = neighbor(world, tick.x(), tick.y() - 1, tick.z());
        boolean tip = !below.blockKey().equals("minecraft:pale_hanging_moss");
        String target = "minecraft:pale_hanging_moss[tip=" + tip + "]";
        return current.exactState().equals(target) ? TickMutation.NONE : replace(tick, world, target);
    }

    private static TickMutation planLeaves(ScheduledTick tick, SemanticWorld world,
            Mc263FeatureBlockState current) {
        Map<String, String> properties = properties(current.exactState());
        int oldDistance = integerProperty(properties, "distance", 1, 7);
        boolean persistent = booleanProperty(properties, "persistent");
        boolean waterlogged = booleanProperty(properties, "waterlogged");
        int distance = 7;
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}};
        for (int[] direction : directions) {
            Mc263FeatureBlockState neighbor = neighbor(world, tick.x() + direction[0],
                    tick.y() + direction[1], tick.z() + direction[2]);
            int candidate = neighbor.isLogsTag() ? 1
                    : neighbor.isLeavesTag()
                            ? Math.min(7, integerProperty(
                                    properties(neighbor.exactState()), "distance", 1, 7) + 1)
                            : 7;
            distance = Math.min(distance, candidate);
        }
        if (distance == 7 && !persistent) {
            return replace(tick, world, waterlogged ? "minecraft:water" : "minecraft:air");
        }
        if (distance == oldDistance) return TickMutation.NONE;
        properties.put("distance", Integer.toString(distance));
        String target = canonical(current.blockKey(), properties);
        Mc263FeatureBlockState.fromExact(target);
        return replace(tick, world, target);
    }

    private static TickMutation planFluid(ScheduledTick tick, SemanticWorld world) {
        if (!switch (tick.typeKey()) {
            case "minecraft:water", "minecraft:flowing_water",
                    "minecraft:lava", "minecraft:flowing_lava" -> true;
            default -> false;
        }) {
            throw new IllegalArgumentException(
                    "unsupported pinned 26.3 fluid tick type: " + tick.typeKey());
        }
        TickMutation mutation = Objects.requireNonNull(world.planFluidTick(tick),
                "fluid tick mutation");
        for (BlockMutation block : mutation.blocks()) {
            if (block.blockId() > Blocks.BLOCK_ID_HIGH_WATER) {
                throw new IllegalStateException(
                        "fluid tick produced an unrepresentable block ID: " + block.blockId());
            }
        }
        return mutation;
    }

    private static TickMutation replace(ScheduledTick tick, SemanticWorld world, String exactState) {
        Mc263FeatureBlockState target = Mc263FeatureBlockState.fromExact(exactState);
        BlockMutation mutation = Objects.requireNonNull(world.encodeExactState(
                tick.x(), tick.y(), tick.z(), target.exactState()), "encoded block mutation");
        if (mutation.x() != tick.x() || mutation.y() != tick.y() || mutation.z() != tick.z()
                || mutation.blockId() != target.blockId()) {
            throw new IllegalStateException("exact-state encoder returned a conflicting mutation");
        }
        return new TickMutation(java.util.List.of(mutation));
    }

    private static Mc263FeatureBlockState exact(SemanticWorld world, int x, int y, int z) {
        return Mc263FeatureBlockState.fromExact(Objects.requireNonNull(
                world.exactBlockStateAt(x, y, z), "exact block state"));
    }

    private static Mc263FeatureBlockState neighbor(SemanticWorld world, int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            return Mc263FeatureBlockState.fromExact("minecraft:bedrock");
        }
        return exact(world, x, y, z);
    }

    private static Map<String, String> properties(String exactState) {
        int open = exactState.indexOf('[');
        if (open < 0 || !exactState.endsWith("]")) return new LinkedHashMap<>();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String body = exactState.substring(open + 1, exactState.length() - 1);
        for (String property : body.split(",")) {
            int equals = property.indexOf('=');
            if (equals <= 0 || equals == property.length() - 1
                    || result.put(property.substring(0, equals),
                            property.substring(equals + 1)) != null) {
                throw new IllegalArgumentException("noncanonical exact-state properties");
            }
        }
        return result;
    }

    private static int integerProperty(Map<String, String> properties, String key, int min, int max) {
        String value = properties.get(key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max || !Integer.toString(parsed).equals(value)) {
                throw new IllegalArgumentException("invalid " + key + " property");
            }
            return parsed;
        } catch (NullPointerException | NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid " + key + " property", invalid);
        }
    }

    private static boolean booleanProperty(Map<String, String> properties, String key) {
        return switch (properties.get(key)) {
            case "true" -> true;
            case "false" -> false;
            case null, default -> throw new IllegalArgumentException("invalid " + key + " property");
        };
    }

    private static String canonical(String blockKey, Map<String, String> properties) {
        return blockKey + "[" + properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }
}
