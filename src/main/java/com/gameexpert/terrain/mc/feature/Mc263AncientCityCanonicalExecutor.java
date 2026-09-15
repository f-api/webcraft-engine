package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityPersistedRuntime;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionEnvironment;
import com.gameexpert.terrain.mc.structure.Mc263AncientCitySettlement;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical host for the accepted Ancient City persisted environment domain. */
final class Mc263AncientCityCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263AncientCitySettlement.STRUCTURE_KEY;
    private static final int STEP = 7;
    private static final int INDEX = 0;
    private static final String PROTECTED_BLOCKS = "minecraft:protected_blocks";

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(STRUCTURE, new Mc263AncientCityCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "Ancient City preflight context");
        requireSchedule(context.entry(), context.carrier().registry());
        requireSource(context.references(), context.clip());
        for (Mc263StructureCarrier.ValidStart start : referencedStarts(
                context.carrier(), context.references())) {
            Mc263WorldGenRegionRandom random = random(context.worldGenRegionRandomState());
            Mc263AncientCityProductionAdapter adapter = adapter(
                    new AncientWorld(null), random, null);
            adapter.preflightPersisted(request(context.carrier(), start,
                    context.references(), context.clip()));
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "Ancient City placement context");
        requireDispatcher(context.dispatcher(), context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(INDEX),
                context.carrier().registry());
        List<String> startKeys = referencedStarts(context.carrier(), context.references())
                .stream().map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            placeReferencedStart(context, startKey);
        }
    }

    /**
     * Places exactly one referenced start against the carrier as it stands after the preceding
     * starts of this source chunk have settled, mirroring the per-start iteration of vanilla
     * {@code ChunkGenerator.applyBiomeDecoration}.
     */
    private static void placeReferencedStart(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context,
            String startKey) {
        Mc263StructureCarrier predecessor = context.carrier();
        Mc263StructureCarrier.ChunkReferences references = context.references();
        Mc263StructureCarrier.ValidStart start = referencedStarts(predecessor, references).stream()
                .filter(candidate -> candidate.startKey().equals(startKey))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "evolved Ancient City carrier lost referenced start"));
        Mc263StructureCarrier successor = successorCarrier(predecessor, start);
        Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork =
                context.forkWorldGenRegionRandom();
        Mc263AncientCityProductionEnvironment.Publisher publisher = (batch, acceptedRandom) -> {
            if (!randomFork.random().snapshot().equals(acceptedRandom.snapshot())) {
                throw new IllegalStateException("Ancient City WGR successor escaped host fork");
            }
            context.commitStructureBatchWithWorldGenRegionRandom(predecessor, successor,
                    Mc263StructureBatchBridge.toRegionBatch(batch, context), randomFork);
        };
        Mc263AncientCityProductionAdapter adapter = adapter(
                new AncientWorld(context.dispatcher().region()), randomFork.random(), publisher);
        adapter.executePersisted(request(predecessor, start, references, context.clip()));
    }

    private static Mc263AncientCityProductionAdapter adapter(
            Mc263AncientCityProductionEnvironment.World world,
            Mc263WorldGenRegionRandom random,
            Mc263AncientCityProductionEnvironment.Publisher publisher) {
        Mc263AncientCityProductionEnvironment environment = publisher == null
                ? new Mc263AncientCityProductionEnvironment(world)
                : new Mc263AncientCityProductionEnvironment(world, publisher);
        return new Mc263AncientCityProductionAdapter(
                new Mc263AncientCityPersistedRuntime(environment, random));
    }

    private static Mc263AncientCityProductionAdapter.PersistedExecutionRequest request(
            Mc263StructureCarrier carrier, Mc263StructureCarrier.ValidStart start,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return new Mc263AncientCityProductionAdapter.PersistedExecutionRequest(start,
                carrier.requireRawStartPayload(STRUCTURE, start), references,
                new Mc263AncientCitySettlement.Clip(references.chunkX(), references.chunkZ(),
                        Mc263AncientCitySettlement.MIN_CLIP_Y,
                        Mc263AncientCitySettlement.MAX_CLIP_Y),
                carrier.requireProducerGraphPayload(STRUCTURE, start));
    }

    private static List<Mc263StructureCarrier.ValidStart> referencedStarts(
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(
                references, STRUCTURE);
        long distinct = starts.stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).distinct().count();
        if (distinct != starts.size()) {
            throw new IllegalArgumentException("Ancient City reference closure repeats a start");
        }
        return starts;
    }

    private static Mc263StructureCarrier successorCarrier(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ValidStart accepted) {
        if (accepted.references() == 1) return carrier;
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        int replacements = 0;
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> entries = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(STRUCTURE) && entry.body().equals(accepted)) {
                    Mc263StructureCarrier.ValidStart successor =
                            new Mc263StructureCarrier.ValidStart(accepted.startKey(),
                                    accepted.originChunkX(), accepted.originChunkZ(), 1,
                                    accepted.adjustedBoundingBox(), accepted.orderedPieces());
                    entries.add(new Mc263StructureCarrier.StartEntry(STRUCTURE, successor));
                    replacements++;
                } else {
                    entries.add(entry);
                }
            }
            chunks.add(chunk.withStarts(entries));
        }
        if (replacements != 1) {
            throw new IllegalArgumentException(
                    "Ancient City successor start replacement cardinality drift");
        }
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureIndexReceipt.Entry expected = Mc263StructureIndexReceipt.step(STEP).get(INDEX);
        Mc263StructureCarrier.StructureDefinition definition = registry.require(STRUCTURE);
        int ordinal = Mc263StructureIndexReceipt.entries().indexOf(expected);
        if (!expected.equals(entry) || !STRUCTURE.equals(entry.key())
                || definition.registryOrdinal() != ordinal
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.BEARD_BOX) {
            throw new IllegalArgumentException("Ancient City schedule/carrier mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        long expectedSeed = Mc263DecorationRandom.featureSeed(
                dispatcher.decorationSeed(), INDEX, STEP);
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !STRUCTURE.equals(dispatcher.structureKey())
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()
                || dispatcher.featureSeed() != expectedSeed) {
            throw new IllegalArgumentException("Ancient City dispatcher/source/RNG mismatch");
        }
        requireSource(references, clip);
    }

    private static void requireSource(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Ancient City source clip mismatch");
        }
    }

    private static Mc263WorldGenRegionRandom random(Mc263WorldGenRegionRandom.State state) {
        return Mc263WorldGenRegionRandom.fromState(state.lo(), state.hi(), state.drawCount(),
                state.gaussianPresent(), state.gaussianBits());
    }

    /** Read-only live region view. The environment itself owns every unpublished write. */
    private static final class AncientWorld implements Mc263AncientCityProductionEnvironment.World {
        private final Mc263FeaturesRegion region;

        private AncientWorld(Mc263FeaturesRegion region) {
            this.region = region;
        }

        @Override public boolean supportsTag(String tagKey) {
            return PROTECTED_BLOCKS.equals(tagKey);
        }

        @Override public boolean stateInTag(String exactState, String tagKey) {
            return Mc263FeatureBlockState.fromExact(exactState).featuresCannotReplace();
        }

        @Override public String biomeKey(int x, int y, int z) {
            requireLive();
            return region.biomeKey(x, y, z);
        }

        @Override public boolean sectionMayContainSculkGrowthInhibitor(
                int sectionX, int sectionY, int sectionZ) {
            requireLive();
            return region.sectionMayContainSculkGrowthInhibitor(sectionX, sectionY, sectionZ);
        }

        @Override public boolean supportsPostprocessing() { return region != null; }
        @Override public boolean supportsSculkPayloads() { return region != null; }

        @Override public int getHeight(String heightmap, int x, int z) {
            requireLive();
            if (!"WORLD_SURFACE_WG".equals(heightmap)) {
                throw new IllegalArgumentException("unknown Ancient City heightmap: " + heightmap);
            }
            return region.worldSurfaceWg(x, z);
        }

        @Override public String getBlockState(Mc263TemplatePlacementExecutor.Vec position) {
            requireLive();
            return region.blockState(position.x(), position.y(), position.z()).exactState();
        }

        @Override public String getFluidKey(Mc263TemplatePlacementExecutor.Vec position) {
            Mc263FeatureBlockState.FluidKind fluid = state(position).fluidKind();
            return switch (fluid) {
                case NONE -> Mc263TemplatePlacementExecutor.EMPTY_FLUID;
                case WATER_SOURCE, WATER_FLOWING -> "minecraft:water";
                case LAVA_SOURCE, LAVA_FLOWING -> "minecraft:lava";
            };
        }

        @Override public boolean isFluidSource(Mc263TemplatePlacementExecutor.Vec position) {
            Mc263FeatureBlockState.FluidKind fluid = state(position).fluidKind();
            return fluid == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                    || fluid == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE;
        }

        @Override public double fluidHeight(Mc263TemplatePlacementExecutor.Vec position) {
            return state(position).fluidKind() == Mc263FeatureBlockState.FluidKind.NONE
                    ? 0.0D : 1.0D;
        }

        @Override public boolean setBlock(Mc263TemplatePlacementExecutor.Vec position,
                String exactState, int flags) {
            throw new AssertionError("Ancient City write escaped environment staging");
        }

        @Override public Mc263TemplatePlacementExecutor.BlockEntityHandle getBlockEntity(
                Mc263TemplatePlacementExecutor.Vec position) {
            throw new IllegalStateException(
                    "Ancient City host block-entity query escaped transaction staging");
        }

        @Override public void scheduleFluidTick(Mc263TemplatePlacementExecutor.Vec position,
                String fluidKey, int delay) {
            throw new IllegalStateException(
                    "Ancient City fluid-tick publication escaped transaction staging");
        }

        private Mc263FeatureBlockState state(Mc263TemplatePlacementExecutor.Vec position) {
            requireLive();
            return region.blockState(position.x(), position.y(), position.z());
        }

        private void requireLive() {
            if (region == null) {
                throw new AssertionError("Ancient City pure preflight queried live world state");
            }
        }
    }
}
