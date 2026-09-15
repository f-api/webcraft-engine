package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263BlockColumnVegetationFeature;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature;
import com.gameexpert.terrain.mc.feature.Mc263ComplexTreeFeature;
import com.gameexpert.terrain.mc.feature.Mc263FeatureWorldAdapter;
import com.gameexpert.terrain.mc.feature.Mc263SimpleVegetationFeature;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.FeaturePlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Position;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact dormant authority for the eight authenticated non-pile Village configured features. */
public final class Mc263VillageNonPileFeatureAuthority {
    public static final String SOURCE_VERSION = "26.3-snapshot-7";

    private static final String RIGID = "rigid";
    private static final Set<String> ROTATIONS = Set.of(
            "NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90");
    private static final List<String> FEATURE_KEYS = List.of(
            "minecraft:patch_cactus",
            "minecraft:oak",
            "minecraft:flower_plain",
            "minecraft:acacia",
            "minecraft:spruce",
            "minecraft:pine",
            "minecraft:patch_taiga_grass",
            "minecraft:patch_berry_bush");
    private static final Mc263BlockColumnVegetationFeature.State CACTUS =
            Mc263BlockColumnVegetationFeature.State.of("minecraft:cactus")
                    .property("age", "0");
    private static final Mc263CommonTreeFeature.TraceSink NO_TREE_TRACE =
            new Mc263CommonTreeFeature.TraceSink() {
                @Override public boolean enabled() { return false; }
                @Override public void record(String phase, long... values) {
                    throw new AssertionError("disabled Village tree trace emitted");
                }
            };

    private Mc263VillageNonPileFeatureAuthority() {
        throw new AssertionError("no instances");
    }

    public enum Kernel {
        SIMPLE_PATCH,
        BLOCK_COLUMN_PATCH,
        CHECKED_TREE
    }

    public enum PatchFilter {
        NONE,
        AIR,
        AIR_GRASS,
        CACTUS_SURVIVAL
    }

    /** Pure authenticated dispatch contract; patch dimensions are zero for checked trees. */
    public record Binding(String featureKey, String configuredTarget, Kernel kernel,
            String configuredBody, int patchCount, int horizontalSpread, int verticalSpread,
            PatchFilter filter) {
        public Binding {
            Objects.requireNonNull(featureKey, "Village non-pile feature key");
            Objects.requireNonNull(configuredTarget, "Village non-pile configured target");
            Objects.requireNonNull(kernel, "Village non-pile kernel");
            Objects.requireNonNull(configuredBody, "Village non-pile configured body");
            Objects.requireNonNull(filter, "Village non-pile patch filter");
            if (patchCount < 0 || horizontalSpread < 0 || verticalSpread < 0) {
                throw new IllegalArgumentException("negative Village non-pile patch contract");
            }
        }
    }

    /**
     * Apply-ready control receipt. Runtime mutation stays owned by the supplied semantic adapter;
     * the exact WGR predecessor/successor pair lets later transaction wiring CAS-rollback the same
     * transaction-owned random if publication fails.
     */
    public record Receipt(Binding binding, Position origin, String rotation, String projection,
            boolean placed, int candidates, int configuredCalls, int configuredSuccesses,
            int attemptedWrites, int retainedWrites,
            Mc263WorldGenRegionRandom.State randomPredecessor,
            Mc263WorldGenRegionRandom.State randomSuccessor) {
        public Receipt {
            Objects.requireNonNull(binding, "Village non-pile binding");
            Objects.requireNonNull(origin, "Village non-pile origin");
            Objects.requireNonNull(rotation, "Village non-pile rotation");
            Objects.requireNonNull(projection, "Village non-pile projection");
            Objects.requireNonNull(randomPredecessor, "Village non-pile WGR predecessor");
            Objects.requireNonNull(randomSuccessor, "Village non-pile WGR successor");
            if (candidates < 0 || configuredCalls < 0 || configuredSuccesses < 0
                    || attemptedWrites < 0 || retainedWrites < 0
                    || configuredCalls > candidates || configuredSuccesses > configuredCalls
                    || retainedWrites > attemptedWrites
                    || randomSuccessor.drawCount() < randomPredecessor.drawCount()) {
                throw new IllegalArgumentException("invalid Village non-pile execution receipt");
            }
        }

        public int randomDraws() {
            return Math.subtractExact(
                    randomSuccessor.drawCount(), randomPredecessor.drawCount());
        }

        public boolean rollbackRandomIfExactSuccessor(Mc263WorldGenRegionRandom random) {
            Objects.requireNonNull(random, "Village non-pile rollback WGR");
            return random.rollbackIfExactSuccessor(randomSuccessor, randomPredecessor);
        }
    }

    /** Package-visible construction seam used only by the focused authority test. */
    static record WorldViews(Mc263SimpleVegetationFeature.WorldAccess simple,
            Mc263BlockColumnVegetationFeature.WorldAccess blockColumn,
            Mc263ComplexTreeFeature.World tree,
            Mc263ComplexTreeFeature.LeafExecutor treeLeaves) {
        WorldViews {
            Objects.requireNonNull(simple, "Village simple-vegetation world");
            Objects.requireNonNull(blockColumn, "Village block-column world");
            Objects.requireNonNull(tree, "Village complex-tree world");
            Objects.requireNonNull(treeLeaves, "Village complex-tree leaves");
        }

        static WorldViews from(Mc263FeatureWorldAdapter adapter) {
            Objects.requireNonNull(adapter, "Village feature world adapter");
            return new WorldViews(adapter.simpleVegetationWorld(),
                    adapter.blockColumnVegetationWorld(), adapter.complexTreeWorld(),
                    adapter.complexTreeLeaves());
        }
    }

    public static List<String> featureKeys() {
        return FEATURE_KEYS;
    }

    public static Binding preflight(FeaturePlacement placement, Mc263FeatureWorldAdapter adapter) {
        Objects.requireNonNull(placement, "Village non-pile placement");
        return preflight(placement.featureKey(), placement.configuredTarget(), placement.rotation(),
                placement.projection(), adapter);
    }

    public static Binding preflight(String featureKey, String configuredTarget, String rotation,
            String projection, Mc263FeatureWorldAdapter adapter) {
        Spec spec = authenticate(featureKey, configuredTarget, rotation, projection);
        WorldViews worlds = WorldViews.from(adapter);
        preflightKernel(spec, worlds);
        return spec.binding();
    }

    static Binding preflight(String featureKey, String configuredTarget, String rotation,
            String projection, WorldViews worlds) {
        Objects.requireNonNull(worlds, "Village non-pile world views");
        Spec spec = authenticate(featureKey, configuredTarget, rotation, projection);
        preflightKernel(spec, worlds);
        return spec.binding();
    }

    public static Receipt execute(FeaturePlacement placement, Mc263WorldGenRegionRandom random,
            Mc263FeatureWorldAdapter adapter) {
        Objects.requireNonNull(placement, "Village non-pile placement");
        return execute(placement.featureKey(), placement.configuredTarget(), placement.origin(),
                placement.rotation(), placement.projection(), random, adapter);
    }

    public static Receipt execute(String featureKey, String configuredTarget, Position origin,
            String rotation, String projection, Mc263WorldGenRegionRandom random,
            Mc263FeatureWorldAdapter adapter) {
        Objects.requireNonNull(origin, "Village non-pile origin");
        Objects.requireNonNull(random, "Village non-pile WGR");
        Spec spec = authenticate(featureKey, configuredTarget, rotation, projection);
        return executeAuthenticated(spec, origin, rotation, projection, random,
                WorldViews.from(adapter));
    }

    static Receipt execute(String featureKey, String configuredTarget, Position origin,
            String rotation, String projection, Mc263WorldGenRegionRandom random,
            WorldViews worlds) {
        Objects.requireNonNull(origin, "Village non-pile origin");
        Objects.requireNonNull(random, "Village non-pile WGR");
        Objects.requireNonNull(worlds, "Village non-pile world views");
        Spec spec = authenticate(featureKey, configuredTarget, rotation, projection);
        return executeAuthenticated(spec, origin, rotation, projection, random, worlds);
    }

    /** Placement-random execution seam used by the Village placement transaction. */
    static Outcome executePlacement(String featureKey, String configuredTarget, Position origin,
            String rotation, String projection, Mc263WorldgenRandomSource random,
            WorldViews worlds) {
        Objects.requireNonNull(origin, "Village non-pile origin");
        Objects.requireNonNull(random, "Village non-pile placement random");
        Objects.requireNonNull(worlds, "Village non-pile world views");
        Spec spec = authenticate(featureKey, configuredTarget, rotation, projection);
        preflightKernel(spec, worlds);
        return dispatch(spec, origin, random, worlds);
    }

    private static Receipt executeAuthenticated(Spec spec, Position origin, String rotation,
            String projection, Mc263WorldGenRegionRandom random, WorldViews worlds) {
        Mc263WorldGenRegionRandom.State predecessor = random.snapshot();
        preflightKernel(spec, worlds);
        Outcome outcome = dispatch(spec, origin, random, worlds);
        Mc263WorldGenRegionRandom.State successor = random.snapshot();
        return new Receipt(spec.binding(), origin, rotation, projection, outcome.placed,
                outcome.candidates, outcome.configuredCalls, outcome.configuredSuccesses,
                outcome.attemptedWrites, outcome.retainedWrites, predecessor, successor);
    }

    private static Spec authenticate(String featureKey, String configuredTarget, String rotation,
            String projection) {
        Objects.requireNonNull(featureKey, "Village non-pile feature key");
        Objects.requireNonNull(configuredTarget, "Village non-pile configured target");
        Objects.requireNonNull(rotation, "Village non-pile rotation");
        Objects.requireNonNull(projection, "Village non-pile projection");
        Spec spec = requireSpec(featureKey);
        Mc263VillageProductionAuthority.Feature authenticated =
                Mc263VillageProductionAuthority.pinned().requireFeature(featureKey);
        require(authenticated.registryKey().equals(featureKey),
                "Village non-pile authenticated registry-key drift: " + featureKey);
        require(authenticated.configuredTarget().equals(spec.configuredTarget),
                "Village non-pile authenticated configured-target drift: " + featureKey);
        require(configuredTarget.equals(spec.configuredTarget),
                "Village non-pile configured-target mismatch: " + featureKey);
        require(ROTATIONS.contains(rotation),
                "unknown Village non-pile rotation: " + rotation);
        require(RIGID.equals(projection),
                "Village non-pile projection must be authenticated rigid: " + projection);
        return spec;
    }

    private static void preflightKernel(Spec spec, WorldViews worlds) {
        switch (spec.kernel) {
            case SIMPLE_PATCH ->
                    Mc263SimpleVegetationFeature.preflight(spec.kernelIndex, worlds.simple);
            case BLOCK_COLUMN_PATCH ->
                    Mc263BlockColumnVegetationFeature.preflight(
                            spec.kernelIndex, worlds.blockColumn);
            case CHECKED_TREE ->
                    Mc263ComplexTreeFeature.preflightConfigured(
                            spec.configuredBody, worlds.tree, worlds.treeLeaves);
        }
    }

    private static Outcome dispatch(Spec spec, Position origin, Mc263WorldgenRandomSource random,
            WorldViews worlds) {
        return switch (spec.kernel) {
            case SIMPLE_PATCH -> executeSimplePatch(spec, origin, random, worlds.simple);
            case BLOCK_COLUMN_PATCH ->
                    executeBlockColumnPatch(spec, origin, random, worlds.blockColumn);
            case CHECKED_TREE -> executeCheckedTree(spec, origin, random, worlds);
        };
    }

    private static Outcome executeSimplePatch(Spec spec, Position origin,
            Mc263WorldgenRandomSource random, Mc263SimpleVegetationFeature.WorldAccess world) {
        Outcome total = new Outcome();
        for (int candidate = 0; candidate < spec.patchCount; candidate++) {
            int x = origin.x() + triangle(random, spec.horizontalSpread);
            int y = origin.y() + triangle(random, spec.verticalSpread);
            int z = origin.z() + triangle(random, spec.horizontalSpread);
            total.candidates++;
            if (!simpleFilter(spec.filter, world, x, y, z)) continue;
            total.configuredCalls++;
            Mc263SimpleVegetationFeature.Result result =
                    Mc263SimpleVegetationFeature.placeConfigured(
                            spec.kernelIndex, random, x, y, z, world);
            total.configuredSuccesses += result.successes();
            total.attemptedWrites += result.attemptedWrites();
            total.retainedWrites += result.retainedWrites();
        }
        total.placed = total.configuredSuccesses > 0;
        return total;
    }

    private static Outcome executeBlockColumnPatch(Spec spec, Position origin,
            Mc263WorldgenRandomSource random,
            Mc263BlockColumnVegetationFeature.WorldAccess world) {
        Outcome total = new Outcome();
        for (int candidate = 0; candidate < spec.patchCount; candidate++) {
            int x = origin.x() + triangle(random, spec.horizontalSpread);
            int y = origin.y() + triangle(random, spec.verticalSpread);
            int z = origin.z() + triangle(random, spec.horizontalSpread);
            total.candidates++;
            Mc263BlockColumnVegetationFeature.State current = world.blockState(x, y, z);
            if (!current.airTag() || !world.canSurvive(CACTUS, x, y, z)) continue;
            total.configuredCalls++;
            Mc263BlockColumnVegetationFeature.Result result =
                    Mc263BlockColumnVegetationFeature.placeConfigured(
                            spec.kernelIndex, random, x, y, z, world);
            total.configuredSuccesses += result.configuredSuccesses();
            total.attemptedWrites += result.attemptedWrites();
            total.retainedWrites += result.retainedWrites();
        }
        total.placed = total.configuredSuccesses > 0;
        return total;
    }

    private static Outcome executeCheckedTree(Spec spec, Position origin,
            Mc263WorldgenRandomSource random, WorldViews worlds) {
        Mc263CommonTreeFeature.Result result = Mc263ComplexTreeFeature.placeConfigured(
                spec.configuredBody, random,
                new Mc263CommonTreeFeature.Pos(origin.x(), origin.y(), origin.z()),
                worlds.tree, worlds.treeLeaves, NO_TREE_TRACE);
        Outcome total = new Outcome();
        total.placed = result.placed();
        total.candidates = 1;
        total.configuredCalls = 1;
        total.configuredSuccesses = result.placed() ? 1 : 0;
        total.attemptedWrites = result.writes();
        total.retainedWrites = result.retained();
        return total;
    }

    private static boolean simpleFilter(PatchFilter filter,
            Mc263SimpleVegetationFeature.WorldAccess world, int x, int y, int z) {
        Mc263SimpleVegetationFeature.State current = world.blockState(x, y, z);
        if (!current.airTag()) return false;
        return switch (filter) {
            case AIR -> true;
            case AIR_GRASS ->
                    world.blockState(x, y - 1, z).block().equals("minecraft:grass_block");
            default -> throw new IllegalStateException(
                    "invalid simple Village patch filter: " + filter);
        };
    }

    private static int triangle(Mc263WorldgenRandomSource random, int range) {
        return random.nextInt(range + 1) - random.nextInt(range + 1);
    }

    private static Spec requireSpec(String featureKey) {
        return switch (featureKey) {
            case "minecraft:patch_cactus" ->
                    new Spec(featureKey, "minecraft:cactus", Kernel.BLOCK_COLUMN_PATCH,
                            "minecraft:cactus", 86, 10, 7, 3, PatchFilter.CACTUS_SURVIVAL);
            case "minecraft:oak" ->
                    new Spec(featureKey, "minecraft:oak", Kernel.CHECKED_TREE,
                            "minecraft:oak_checked", -1, 0, 0, 0, PatchFilter.NONE);
            case "minecraft:flower_plain" ->
                    new Spec(featureKey, "minecraft:flower_plain", Kernel.SIMPLE_PATCH,
                            "minecraft:flower_plain", 56, 64, 6, 2, PatchFilter.AIR);
            case "minecraft:acacia" ->
                    new Spec(featureKey, "minecraft:acacia", Kernel.CHECKED_TREE,
                            "minecraft:acacia_checked", -1, 0, 0, 0, PatchFilter.NONE);
            case "minecraft:spruce" ->
                    new Spec(featureKey, "minecraft:spruce", Kernel.CHECKED_TREE,
                            "minecraft:spruce_checked", -1, 0, 0, 0, PatchFilter.NONE);
            case "minecraft:pine" ->
                    new Spec(featureKey, "minecraft:pine", Kernel.CHECKED_TREE,
                            "minecraft:pine_checked", -1, 0, 0, 0, PatchFilter.NONE);
            case "minecraft:patch_taiga_grass" ->
                    new Spec(featureKey, "minecraft:taiga_grass", Kernel.SIMPLE_PATCH,
                            "minecraft:taiga_grass", 61, 32, 7, 3, PatchFilter.AIR);
            case "minecraft:patch_berry_bush" ->
                    new Spec(featureKey, "minecraft:berry_bush", Kernel.SIMPLE_PATCH,
                            "minecraft:berry_bush", 97, 96, 7, 3, PatchFilter.AIR_GRASS);
            default -> throw new IllegalArgumentException(
                    "unknown Village non-pile configured feature: " + featureKey);
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Spec(String featureKey, String configuredTarget, Kernel kernel,
            String configuredBody, int kernelIndex, int patchCount, int horizontalSpread,
            int verticalSpread, PatchFilter filter) {
        private Binding binding() {
            return new Binding(featureKey, configuredTarget, kernel, configuredBody, patchCount,
                    horizontalSpread, verticalSpread, filter);
        }
    }

    static final class Outcome {
        private boolean placed;
        private int candidates;
        private int configuredCalls;
        private int configuredSuccesses;
        private int attemptedWrites;
        private int retainedWrites;
    }
}
