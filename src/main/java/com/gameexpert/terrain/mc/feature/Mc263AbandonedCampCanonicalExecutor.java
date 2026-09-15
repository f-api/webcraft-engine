package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.AtomicBatch;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.PreparedExecution;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.PublishStatus;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampStartGenerator.TraceRandom;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Atomic live-region adapter for the 21 authenticated Abandoned Camp configured features.
 * Registration remains deliberately separate until the complete Camp family promotion closes.
 */
public final class Mc263AbandonedCampCanonicalExecutor {
    private static final byte[] EMPTY_COMPOUND = {10, 0, 0, 0};
    private static final java.util.Set<String> SUPPORTED_FEATURES = supportedFeatures();

    private Mc263AbandonedCampCanonicalExecutor() {}

    private static java.util.Set<String> supportedFeatures() {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        for (var mapping : Mc263AbandonedCampConfiguredFeatureExecutor.mappings()) {
            values.add(mapping.configuredKey());
            values.add(mapping.targetKey());
        }
        values.add("minecraft:pale_moss_patch");
        return java.util.Collections.unmodifiableSet(values);
    }

    /** Authority-owned transaction that persists STR and settles every region lane together. */
    public interface AtomicSettlementSink {
        PublishStatus commit(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, String receiptSha256,
                byte[] canonicalReceipt, Mc263FeaturesRegion.StructureBatch batch);
    }

    /**
     * Prepares the entire configured feature against a staged live view. Capability rejection,
     * carrier rejection, and destination rejection all happen before caller RNG is consumed.
     */
    public static PreparedSettlement prepare(Mc263StructureCarrier predecessor,
            Mc263StructureCarrier successor, String configuredKey, TraceRandom random,
            Mc263AbandonedCampConfiguredFeatureExecutor.Origin origin, long owner,
            Mc263AbandonedCampConfiguredFeatureExecutor.WriteDomain writeDomain,
            Mc263FeaturesRegion region) {
        predecessor = strictCarrier(Objects.requireNonNull(predecessor, "Camp predecessor"));
        successor = strictCarrier(Objects.requireNonNull(successor, "Camp successor"));
        requireCarrierTransition(predecessor, successor);
        Objects.requireNonNull(configuredKey, "Camp configured feature");
        Objects.requireNonNull(random, "Camp feature random");
        Objects.requireNonNull(origin, "Camp feature origin");
        Objects.requireNonNull(writeDomain, "Camp write domain");
        Objects.requireNonNull(region, "Camp FEATURES region");

        RegionWorld world = new RegionWorld(region);
        PreparedExecution execution = Mc263AbandonedCampConfiguredFeatureExecutor.prepare(
                configuredKey, random, origin, owner, writeDomain, world);
        Mc263FeaturesRegion.StructureBatch batch = convert(execution.batch());
        return new PreparedSettlement(predecessor, successor, execution, batch);
    }

    private static Mc263StructureCarrier strictCarrier(Mc263StructureCarrier carrier) {
        return carrier.strictlyDecoded();
    }

    private static void requireCarrierTransition(Mc263StructureCarrier predecessor,
            Mc263StructureCarrier successor) {
        if (!predecessor.registry().definitions().equals(successor.registry().definitions())) {
            throw new IllegalArgumentException("Camp settlement changed pinned registry");
        }
        if (!predecessor.referenceChunks().equals(successor.referenceChunks())) {
            throw new IllegalArgumentException("Camp settlement changed reference maps");
        }
    }

    private static Mc263FeaturesRegion.StructureBatch convert(AtomicBatch source) {
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = source.blocks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(value.x(), value.y(),
                        value.z(), value.exactState(), value.owner()))
                .toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = source.bent().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(value.x(), value.y(),
                        value.z(), value.blockIdentity(), value.entityType(), EMPTY_COMPOUND))
                .toList();
        ArrayList<Mc263FeaturesRegion.StructureBlockTick> blockTicks =
                new ArrayList<>(source.blockTicks().size());
        for (int index = 0; index < source.blockTicks().size(); index++) {
            var value = source.blockTicks().get(index);
            blockTicks.add(new Mc263FeaturesRegion.StructureBlockTick(value.x(), value.y(),
                    value.z(), value.blockKey(), value.delay(), 0, index));
        }
        ArrayList<Mc263FeaturesRegion.StructureFluidTick> fluidTicks =
                new ArrayList<>(source.fluidTicks().size());
        for (int index = 0; index < source.fluidTicks().size(); index++) {
            var value = source.fluidTicks().get(index);
            fluidTicks.add(new Mc263FeaturesRegion.StructureFluidTick(value.x(), value.y(),
                    value.z(), value.fluidKey(), value.delay(), 0, index));
        }
        List<Mc263FeaturesRegion.StructurePostprocessMark> postprocess = source.postprocess()
                .stream().map(value -> new Mc263FeaturesRegion.StructurePostprocessMark(
                        value.x(), value.y(), value.z())).toList();
        List<Mc263FeaturesRegion.StructureBee> bees = source.bees().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBee(value.x(), value.y(),
                        value.z(), value.ticksInHive())).toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, List.of(), List.of(), bent,
                List.of(), fluidTicks, postprocess, List.of(), blockTicks, bees);
    }

    public static final class PreparedSettlement {
        private final Mc263StructureCarrier predecessor;
        private final Mc263StructureCarrier successor;
        private final PreparedExecution execution;
        private final Mc263FeaturesRegion.StructureBatch batch;

        private PreparedSettlement(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, PreparedExecution execution,
                Mc263FeaturesRegion.StructureBatch batch) {
            this.predecessor = predecessor;
            this.successor = successor;
            this.execution = execution;
            this.batch = batch;
        }

        public Mc263StructureCarrier predecessor() { return predecessor; }
        public Mc263StructureCarrier successor() { return successor; }
        public PreparedExecution execution() { return execution; }
        Mc263FeaturesRegion.StructureBatch batch() { return batch; }

        public Mc263AbandonedCampConfiguredFeatureExecutor.Publication publish(
                AtomicSettlementSink sink) {
            Objects.requireNonNull(sink, "Camp atomic settlement sink");
            PublishStatus status = Objects.requireNonNull(sink.commit(predecessor, successor,
                    execution.receiptSha256(), execution.canonicalReceipt(), batch),
                    "Camp settlement status");
            return execution.publish((sha, ignored) -> status);
        }
    }

    private static final class RegionWorld
            implements Mc263AbandonedCampConfiguredFeatureExecutor.WorldAccess {
        private final Mc263FeaturesRegion region;

        private RegionWorld(Mc263FeaturesRegion region) { this.region = region; }

        @Override public int minY() { return Blocks.MIN_Y; }
        @Override public int maxY() { return Blocks.MAX_Y; }
        @Override public boolean supportsFeature(String key) {
            return SUPPORTED_FEATURES.contains(key);
        }
        @Override public boolean supportsExactState(String state) {
            try {
                Mc263FeatureBlockState.fromExact(state);
                return true;
            } catch (IllegalArgumentException unsupported) {
                return false;
            }
        }
        @Override public boolean supportsTreeFinalization() { return true; }
        @Override public boolean supportsWriteFlags(int flags) {
            return flags == 2 || flags == 3 || flags == 19;
        }
        @Override public boolean supportsBentPayloads() { return true; }
        @Override public boolean supportsBeePayloads() { return true; }
        @Override public boolean supportsBlockTicks() { return true; }
        @Override public boolean supportsFluidTicks() { return true; }
        @Override public boolean supportsPostprocessing() { return true; }
        @Override public boolean supportsBeneathTreePodzolTag() { return true; }
        @Override public String exactState(int x, int y, int z) {
            return region.blockState(x, y, z).exactState();
        }
        @Override public boolean hasScheduledBlockTick(int x, int y, int z, String key) {
            return region.hasScheduledBlockTick(x, y, z, key);
        }
        @Override public boolean hasScheduledFluidTick(int x, int y, int z, String key) {
            return region.hasScheduledFluidTick(x, y, z, key);
        }
    }
}
