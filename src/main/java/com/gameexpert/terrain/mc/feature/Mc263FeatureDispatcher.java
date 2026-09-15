package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Ordered, fail-closed dispatcher for the dormant 26.3 FEATURES region runtime. */
public final class Mc263FeatureDispatcher {
    public static final int STEP_COUNT = Mc263FeatureIndexReceipt.STEP_COUNT;

    private List<List<String>> structuresByStep;
    private final Map<String, StructureExecutor> structureExecutors = new LinkedHashMap<>();
    private final Map<String, FeatureExecutor> featureExecutors = new LinkedHashMap<>();
    private final Map<Integer, StagePreflight> stagePreflights = new LinkedHashMap<>();

    public Mc263FeatureDispatcher() {
        this(emptyStructureSteps());
    }

    /**
     * Creates the dormant exact pinned structure schedule. Callers must register all 52 pure,
     * fail-closed structure executors before dispatch; the feature-only constructor is unsuitable
     * for canonical activation.
     */
    public static Mc263FeatureDispatcher withPinnedStructures() {
        return new Mc263FeatureDispatcher(Mc263StructureIndexReceipt.defaultStructureSteps());
    }

    /** Each inner list is already in the pinned registry/global order for that step. */
    public Mc263FeatureDispatcher(List<List<String>> structuresByStep) {
        Objects.requireNonNull(structuresByStep, "structuresByStep");
        if (structuresByStep.size() != STEP_COUNT) {
            throw new IllegalArgumentException("structure schedule must contain 11 steps: "
                    + structuresByStep.size());
        }
        List<List<String>> copy = new ArrayList<>(STEP_COUNT);
        for (List<String> step : structuresByStep) {
            Objects.requireNonNull(step, "structure step");
            List<String> keys = new ArrayList<>(step.size());
            for (String key : step) keys.add(requireKey(key, "structure"));
            copy.add(List.copyOf(keys));
        }
        this.structuresByStep = List.copyOf(copy);
    }

    public synchronized Mc263FeatureDispatcher registerStructure(
            String structureKey, StructureExecutor executor) {
        register(structureExecutors, requireKey(structureKey, "structure"), executor);
        return this;
    }

    /**
     * One-shot canonical-mode upgrade of a fully registered feature-only dispatcher. The pinned
     * schedule is installed without changing any feature executor or stage preflight.
     */
    synchronized Mc263FeatureDispatcher installPinnedStructureSchedule() {
        if (structuresByStep.stream().anyMatch(step -> !step.isEmpty())) {
            throw new IllegalStateException("structure schedule is already installed");
        }
        structuresByStep = Mc263StructureIndexReceipt.defaultStructureSteps();
        return this;
    }

    public synchronized Mc263FeatureDispatcher registerFeature(
            String featureKey, FeatureExecutor executor) {
        register(featureExecutors, requireKey(featureKey, "feature"), executor);
        return this;
    }

    /** Registers one pure capability/state closure check for a complete FEATURES step. */
    public synchronized Mc263FeatureDispatcher registerStagePreflight(
            int step, StagePreflight preflight) {
        if (step < 0 || step >= STEP_COUNT) {
            throw new IllegalArgumentException("preflight step is outside 0..10: " + step);
        }
        Objects.requireNonNull(preflight, "preflight");
        if (stagePreflights.putIfAbsent(step, preflight) != null) {
            throw new IllegalArgumentException("stage preflight already registered: " + step);
        }
        return this;
    }

    /** Focused-test seam for proving a registered stage fails before execution begins. */
    synchronized Mc263FeatureDispatcher replaceStagePreflightForTest(
            int step, StagePreflight preflight) {
        if (!stagePreflights.containsKey(step)) {
            throw new IllegalArgumentException("stage preflight is not registered: " + step);
        }
        stagePreflights.put(step, Objects.requireNonNull(preflight, "preflight"));
        return this;
    }

    /**
     * Preflights every selected executor before the first write, then executes all nine sources in
     * chunk-X outer/chunk-Z inner order. Every source runs steps 0..10, structures first, followed
     * by the selected biome features in their official global-index order.
     */
    public DispatchResult dispatch(long worldSeed, Mc263FeaturesRegion region) {
        return dispatchThroughStepInternal(STEP_COUNT - 1, worldSeed, region,
                ProductionContextLocator.unavailable());
    }

    DispatchResult dispatchAuthenticated(long worldSeed, Mc263FeaturesRegion region,
            ProductionContextLocator productionContexts) {
        return dispatchThroughStepInternal(STEP_COUNT - 1, worldSeed, region,
                Objects.requireNonNull(productionContexts, "production context locator"));
    }

    /**
     * Dormant integration bridge that executes the exact contiguous FEATURES prefix 0 through
     * {@code terminalStep}. Only executors selected inside that prefix are required; the full
     * {@link #dispatch(long, Mc263FeaturesRegion)} contract remains fail-closed through step 10.
     */
    public DispatchResult dispatchThroughStep(int terminalStep, long worldSeed,
            Mc263FeaturesRegion region) {
        if (terminalStep < 0 || terminalStep >= STEP_COUNT) {
            throw new IllegalArgumentException(
                    "terminal FEATURES step is outside 0..10: " + terminalStep);
        }
        return dispatchThroughStepInternal(terminalStep, worldSeed, region,
                ProductionContextLocator.unavailable());
    }

    private DispatchResult dispatchThroughStepInternal(int terminalStep, long worldSeed,
            Mc263FeaturesRegion region, ProductionContextLocator productionContexts) {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(productionContexts, "production context locator");
        Map<String, StructureExecutor> structures;
        Map<String, FeatureExecutor> features;
        Map<Integer, StagePreflight> stageChecks;
        synchronized (this) {
            structures = Map.copyOf(structureExecutors);
            features = Map.copyOf(featureExecutors);
            stageChecks = Map.copyOf(stagePreflights);
        }

        List<SourcePlan> plans = buildPlans(region, terminalStep);
        preflight(plans, structures, features);
        for (int step = 0; step <= terminalStep; step++) {
            StagePreflight stageCheck = stageChecks.get(step);
            if (stageCheck != null) stageCheck.verify(worldSeed, region);
        }
        List<ScheduleEvent> trace = new ArrayList<>();
        for (SourcePlan plan : plans) {
            int sourceBlockX = plan.chunkX() * Blocks.CHUNK_X;
            int sourceBlockZ = plan.chunkZ() * Blocks.CHUNK_Z;
            WorldgenRandom random = new WorldgenRandom(0L);
            long decorationSeed = random.setDecorationSeed(
                    worldSeed, sourceBlockX, sourceBlockZ);
            region.beginSource(plan.chunkX(), plan.chunkZ());
            try {
                for (int step = 0; step <= terminalStep; step++) {
                    for (IndexedKey structure : plan.structuresByStep().get(step)) {
                        long featureSeed = Mc263DecorationRandom.featureSeed(
                                decorationSeed, structure.index(), step);
                        random.setFeatureSeed(decorationSeed, structure.index(), step);
                        structures.get(structure.key()).place(new StructurePlacementContext(
                                worldSeed, plan.chunkX(), plan.chunkZ(), step,
                                structure.index(), structure.key(), decorationSeed, featureSeed,
                                random, region));
                        trace.add(new ScheduleEvent(plan.chunkX(), plan.chunkZ(), step,
                                Kind.STRUCTURE, structure.index(), structure.key()));
                    }
                    for (IndexedKey feature : plan.featuresByStep().get(step)) {
                        long featureSeed = Mc263DecorationRandom.featureSeed(
                                decorationSeed, feature.index(), step);
                        random.setFeatureSeed(decorationSeed, feature.index(), step);
                        features.get(feature.key()).place(new FeaturePlacementContext(
                                worldSeed, plan.chunkX(), plan.chunkZ(), step,
                                feature.index(), feature.key(), decorationSeed, featureSeed,
                                random, region, productionContexts));
                        trace.add(new ScheduleEvent(plan.chunkX(), plan.chunkZ(), step,
                                Kind.FEATURE, feature.index(), feature.key()));
                    }
                }
            } finally {
                region.endSource();
            }
        }
        return new DispatchResult(region.snapshotCenter(), trace);
    }

    private List<SourcePlan> buildPlans(Mc263FeaturesRegion region, int terminalStep) {
        List<List<IndexedKey>> structures = indexedStructures(terminalStep);
        List<SourcePlan> plans = new ArrayList<>(Mc263FeaturesRegion.ACTIVE_SOURCE_COUNT);
        for (Mc263FeaturesRegion.SourceChunk source : region.sourceSchedule()) {
            Set<String> biomes = region.biomesForSource(source.chunkX(), source.chunkZ());
            List<List<IndexedKey>> selectedFeatures = new ArrayList<>(terminalStep + 1);
            for (int step = 0; step <= terminalStep; step++) {
                BitSet indices = new BitSet();
                Map<Integer, String> keyByIndex = new HashMap<>();
                for (String biomeKey : biomes) {
                    for (Mc263FeatureIndexReceipt.FeatureReference reference
                            : Mc263FeatureIndexReceipt.biome(biomeKey).featuresAtStep(step)) {
                        indices.set(reference.globalIndex());
                        String previous = keyByIndex.putIfAbsent(
                                reference.globalIndex(), reference.featureKey());
                        if (previous != null && !previous.equals(reference.featureKey())) {
                            throw new IllegalStateException("ambiguous feature global index "
                                    + reference.globalIndex() + " in step " + step);
                        }
                    }
                }
                List<IndexedKey> selected = new ArrayList<>(indices.cardinality());
                for (int index = indices.nextSetBit(0); index >= 0;
                        index = indices.nextSetBit(index + 1)) {
                    String key = keyByIndex.get(index);
                    if (!Mc263FeatureIndexReceipt.step(step).featureAt(index).equals(key)) {
                        throw new IllegalStateException("feature receipt changed during scheduling");
                    }
                    selected.add(new IndexedKey(index, key));
                }
                selectedFeatures.add(List.copyOf(selected));
            }
            plans.add(new SourcePlan(source.chunkX(), source.chunkZ(), structures,
                    List.copyOf(selectedFeatures)));
        }
        return List.copyOf(plans);
    }

    private List<List<IndexedKey>> indexedStructures(int terminalStep) {
        List<List<IndexedKey>> result = new ArrayList<>(terminalStep + 1);
        for (int stepIndex = 0; stepIndex <= terminalStep; stepIndex++) {
            List<String> step = structuresByStep.get(stepIndex);
            List<IndexedKey> indexed = new ArrayList<>(step.size());
            for (int index = 0; index < step.size(); index++) {
                indexed.add(new IndexedKey(index, step.get(index)));
            }
            result.add(List.copyOf(indexed));
        }
        return List.copyOf(result);
    }

    private static void preflight(List<SourcePlan> plans,
            Map<String, StructureExecutor> structures, Map<String, FeatureExecutor> features) {
        for (SourcePlan plan : plans) {
            for (int step = 0; step < plan.featuresByStep().size(); step++) {
                for (IndexedKey structure : plan.structuresByStep().get(step)) {
                    if (!structures.containsKey(structure.key())) {
                        throw missing("structure", structure, plan, step);
                    }
                }
                for (IndexedKey feature : plan.featuresByStep().get(step)) {
                    if (!features.containsKey(feature.key())) {
                        throw missing("feature", feature, plan, step);
                    }
                }
            }
        }
    }

    private static IllegalStateException missing(String kind, IndexedKey entry, SourcePlan plan,
            int step) {
        return new IllegalStateException("missing " + kind + " executor: " + entry.key()
                + " at source " + plan.chunkX() + "," + plan.chunkZ()
                + " step " + step + " global index " + entry.index());
    }

    private static <T> void register(Map<String, T> registry, String key, T executor) {
        Objects.requireNonNull(executor, "executor");
        if (registry.putIfAbsent(key, executor) != null) {
            throw new IllegalArgumentException("executor already registered: " + key);
        }
    }

    private static String requireKey(String key, String kind) {
        return Mc263FeatureBlockState.requireCanonicalResourceKey(key, kind + " key");
    }

    /** Test-only dormant bridge for the byte-identical Java/Rust direct carrier vector. */
    static DispatchResult directCarrierReceipt(Mc263FeaturesRegion.CenterSnapshot center,
            List<ScheduleEvent> scheduleTrace) {
        return new DispatchResult(center, scheduleTrace);
    }

    private static List<List<String>> emptyStructureSteps() {
        List<List<String>> result = new ArrayList<>(STEP_COUNT);
        for (int step = 0; step < STEP_COUNT; step++) result.add(List.of());
        return result;
    }

    @FunctionalInterface
    public interface StructureExecutor {
        void place(StructurePlacementContext context);
    }

    @FunctionalInterface
    public interface FeatureExecutor {
        void place(FeaturePlacementContext context);
    }

    @FunctionalInterface
    public interface ProductionContextLocator {
        LootProductionContext locate(Mc263FeaturesRegion region, int blockX, int blockY,
                int blockZ, String lootTable);

        static ProductionContextLocator unavailable() {
            return (region, blockX, blockY, blockZ, lootTable) -> {
                throw new IllegalStateException(
                        "authenticated production context is unavailable in feature-only mode");
            };
        }
    }

    /** Must not consume feature RNG, query live block state, write, or begin a source. */
    @FunctionalInterface
    public interface StagePreflight {
        void verify(long worldSeed, Mc263FeaturesRegion region);
    }

    public record StructurePlacementContext(long worldSeed, int sourceChunkX, int sourceChunkZ,
            int step, int globalIndex, String structureKey, long decorationSeed, long featureSeed,
            WorldgenRandom random, Mc263FeaturesRegion region) {
        public StructurePlacementContext {
            Objects.requireNonNull(structureKey, "structureKey");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(region, "region");
        }
    }

    public record FeaturePlacementContext(long worldSeed, int sourceChunkX, int sourceChunkZ,
            int step, int globalIndex, String featureKey, long decorationSeed, long featureSeed,
            WorldgenRandom random, Mc263FeaturesRegion region,
            ProductionContextLocator productionContexts) {
        public FeaturePlacementContext {
            Objects.requireNonNull(featureKey, "featureKey");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(productionContexts, "productionContexts");
        }
    }

    public enum Kind {
        STRUCTURE,
        FEATURE
    }

    public record ScheduleEvent(int sourceChunkX, int sourceChunkZ, int step, Kind kind,
                                int globalIndex, String key) {
        public ScheduleEvent {
            Objects.requireNonNull(kind, "kind");
            if (step < 0 || step >= STEP_COUNT) {
                throw new IllegalArgumentException("schedule step is outside 0..10: " + step);
            }
            if (globalIndex < 0 || globalIndex > 0xffff) {
                throw new IllegalArgumentException(
                        "schedule global index is outside unsigned-16: " + globalIndex);
            }
            key = requireKey(key, kind == Kind.STRUCTURE ? "structure" : "feature");
        }
    }

    public static final class DispatchResult {
        private final Mc263FeaturesRegion.CenterSnapshot center;
        private final List<ScheduleEvent> scheduleTrace;
        private volatile byte[] binaryReceipt;
        private volatile String binaryReceiptSha256;

        private DispatchResult(Mc263FeaturesRegion.CenterSnapshot center,
                List<ScheduleEvent> scheduleTrace) {
            this.center = Objects.requireNonNull(center, "center");
            Objects.requireNonNull(scheduleTrace, "scheduleTrace");
            this.scheduleTrace = List.copyOf(scheduleTrace);
        }

        public Mc263FeaturesRegion.CenterSnapshot center() {
            return center;
        }

        public List<ScheduleEvent> scheduleTrace() {
            return scheduleTrace;
        }

        public String scheduleTraceSha256() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (ScheduleEvent event : scheduleTrace) {
                    String line = event.sourceChunkX() + "," + event.sourceChunkZ() + ","
                            + event.step() + "," + event.kind() + "," + event.globalIndex()
                            + "," + event.key() + "\n";
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 unavailable", failure);
            }
        }

        /** Returns the versioned Java/Rust FEATURES carrier receipt as an isolated byte array. */
        public byte[] binaryReceipt() {
            byte[] value = binaryReceipt;
            if (value == null) {
                synchronized (this) {
                    value = binaryReceipt;
                    if (value == null) {
                        value = Mc263FeaturesReceipt.encode(this);
                        binaryReceipt = value;
                    }
                }
            }
            return value.clone();
        }

        public String binaryReceiptSha256() {
            String value = binaryReceiptSha256;
            if (value == null) {
                synchronized (this) {
                    value = binaryReceiptSha256;
                    if (value == null) {
                        byte[] receipt = binaryReceipt;
                        if (receipt == null) {
                            receipt = Mc263FeaturesReceipt.encode(this);
                            binaryReceipt = receipt;
                        }
                        value = Mc263FeaturesReceipt.sha256(receipt);
                        binaryReceiptSha256 = value;
                    }
                }
            }
            return value;
        }
    }

    private record IndexedKey(int index, String key) {
    }

    private record SourcePlan(int chunkX, int chunkZ,
            List<List<IndexedKey>> structuresByStep,
            List<List<IndexedKey>> featuresByStep) {
    }
}
