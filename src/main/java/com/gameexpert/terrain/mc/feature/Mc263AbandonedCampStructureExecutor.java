package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton.SourceClip;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.ElementKind;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Vec;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampProducer;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.BlockEntityAccess;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.Clip;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.FluidState;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.ProductionExecution;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.ProductionWorldTransaction;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.Settlement;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampTemplateExecutor.WorldTransaction;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant exact canonical executor family for all 18 pinned 26.3 Abandoned Camp structures.
 *
 * <p>Shared publication/accounting remains deliberately separate. Every placement starts from the
 * authenticated typed/raw persisted start, runs the accepted production executor on an isolated
 * caller-owned transaction, and publishes only through the canonical skeleton's atomic batch/STR
 * commit.</p>
 */
public final class Mc263AbandonedCampStructureExecutor implements CanonicalStructureExecutor {
    static final int STEP = 4;
    static final int STRUCTURE_COUNT = 18;
    private static final String PREFIX = "minecraft:abandoned_camp_";
    private static final String SPECIAL_9 =
            "minecraft:abandoned_camp/camp/default/campsite_default_special_9";
    private static final String CUSHION = "minecraft:cushion";
    private static final Set<Integer> WRITE_FLAGS = Set.of(2, 3, 18, 19, 820);
    private static final Set<String> LOOT_TABLES = Set.of(
            "minecraft:barrels/abandoned_camp_barrel",
            "minecraft:chests/abandoned_camp_common_chest",
            "minecraft:chests/abandoned_camp_secret_chest");
    private static final Set<String> SUPPORTED_FEATURES = supportedFeatures();
    private static final List<Slot> SLOTS = buildSlots();
    private static final Mc263AbandonedCampProducer PRODUCER = Mc263AbandonedCampProducer.pinned();
    private static final List<CushionSpec> CUSHIONS = List.of(
            new CushionSpec(new Vec(7, 2, 4), 7.5D, 2.9375D, 4.5D),
            new CushionSpec(new Vec(7, 2, 6), 7.5D, 2.9375D, 6.5D));

    private final Slot slot;

    private Mc263AbandonedCampStructureExecutor(Slot slot) {
        this.slot = Objects.requireNonNull(slot, "Camp canonical slot");
    }

    /** Local-only registration for exactly the 18 pinned Camp entries. */
    static void register(CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "Camp canonical registry");
        for (Slot slot : SLOTS) {
            registry.register(slot.key(), new Mc263AbandonedCampStructureExecutor(slot));
        }
    }

    static List<String> structureKeys() {
        return SLOTS.stream().map(Slot::key).toList();
    }

    static List<Slot> slots() {
        return SLOTS;
    }

    static CanonicalStructureExecutor executorForTest(String key) {
        return SLOTS.stream().filter(value -> value.key().equals(key)).findFirst()
                .<CanonicalStructureExecutor>map(Mc263AbandonedCampStructureExecutor::new)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown Camp canonical executor key: " + key));
    }

    @Override
    public void preflight(StructurePreflightContext context) {
        Objects.requireNonNull(context, "Camp canonical preflight context");
        requireSchedule(context.entry(), context.carrier());
        requireSourceBinding(context.references(), context.clip());
        validatePersistedStarts(context.worldSeed(), context.carrier());
        validateReferencedStarts(context.worldSeed(), context.carrier(), context.references());
    }

    @Override
    public void place(StructurePlacementInput context) {
        Objects.requireNonNull(context, "Camp canonical placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(slot.index()), context.carrier());
        Mc263StructureCarrier predecessor = context.carrier();
        validatePersistedStarts(dispatcher.worldSeed(), predecessor);
        List<PreparedStart> preparedStarts = prepareReferencedStarts(
                dispatcher.worldSeed(), predecessor, context.references());

        Mc263StructureCarrier successor = predecessor;
        ArrayList<PreparedStart> pending = new ArrayList<>();
        LinkedHashSet<Long> owners = new LinkedHashSet<>();
        for (PreparedStart prepared : preparedStarts) {
            ValidStart persisted = prepared.persisted();
            if (persisted.references() == 1) continue;
            if (persisted.references() != 0) {
                throw new IllegalArgumentException("Camp typed reference count is not canonical: "
                        + persisted.references());
            }
            pending.add(prepared);
            successor = advanceReferences(successor, persisted);
            owners.add(Mc263StructureOwner.owner(slot.key(), persisted.startKey()));
        }
        if (pending.isEmpty()) return;

        StagedProductionTransaction transaction = new StagedProductionTransaction(
                dispatcher.region(), owners, context);
        ArrayList<Mc263FinalChunkSidecars.StructureEntity> entities = new ArrayList<>();
        try {
            for (PreparedStart prepared : pending) {
                ValidStart persisted = prepared.persisted();
                long owner = Mc263StructureOwner.owner(slot.key(), persisted.startKey());
                transaction.selectOwner(owner);
                ProductionExecution execution = Mc263AbandonedCampTemplateExecutor.executeProduction(
                        prepared.restored(), clip(context.clip()), owner, transaction);
                if (execution.stepLocalIndex() != slot.index()) {
                    throw new IllegalStateException("Camp production schedule slot drift for "
                            + slot.key());
                }
                entities.addAll(special9Entities(prepared.restored(), context.clip()));
            }
            Mc263FeaturesRegion.StructureBatch batch = transaction.requireBatch(entities);
            context.commitStructureBatch(predecessor, successor, batch);
        } catch (RuntimeException | Error failure) {
            transaction.rollback();
            throw failure;
        }
    }

    private void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier carrier) {
        if (!slot.entry().equals(entry) || entry.step() != STEP || entry.index() != slot.index()
                || !entry.key().equals(slot.key())
                || entry.biomeMask() != slot.fact().possibleBiomeMask()) {
            throw new IllegalArgumentException("Camp canonical schedule mismatch: " + slot.key());
        }
        Mc263StructureCarrier.StructureDefinition definition = carrier.registry().require(slot.key());
        int globalOrdinal = Mc263StructureIndexReceipt.entries().indexOf(slot.entry());
        if (globalOrdinal < 0 || definition.registryOrdinal() != globalOrdinal
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.BEARD_THIN) {
            throw new IllegalArgumentException("Camp carrier registry mismatch: " + slot.key());
        }
    }

    private void requireDispatcher(Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != slot.index()
                || !dispatcher.structureKey().equals(slot.key())
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("Camp dispatcher/source mismatch: " + slot.key());
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            SourceClip clip) {
        if (!SourceClip.chunk(references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Camp source clip mismatch");
        }
    }

    private void validatePersistedStarts(long worldSeed, Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (!slot.key().equals(entry.structureId())) continue;
                if (!(entry.body() instanceof ValidStart valid)) continue;
                PRODUCER.restorePersistedStart(valid,
                        carrier.requireRawStartPayload(slot.key(), valid), worldSeed);
            }
        }
    }

    private void validateReferencedStarts(long worldSeed, Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        prepareReferencedStarts(worldSeed, carrier, references);
    }

    private List<PreparedStart> prepareReferencedStarts(long worldSeed,
            Mc263StructureCarrier carrier, Mc263StructureCarrier.ChunkReferences references) {
        int expected = 0;
        for (Mc263StructureCarrier.ReferenceSet set : references.orderedSets()) {
            if (slot.key().equals(set.structureId())) expected += set.orderedOrigins().size();
        }
        List<ValidStart> resolved = carrier.resolveStarts(references, slot.key());
        if (resolved.size() != expected) {
            throw new IllegalArgumentException("Camp referenced start resolution mismatch: "
                    + slot.key() + " " + resolved.size() + "/" + expected);
        }
        ArrayList<PreparedStart> prepared = new ArrayList<>(resolved.size());
        for (ValidStart valid : resolved) {
            RawStartPayload raw = carrier.requireRawStartPayload(slot.key(), valid);
            prepared.add(new PreparedStart(valid, PRODUCER.restorePersistedStart(valid, raw,
                    worldSeed)));
        }
        return List.copyOf(prepared);
    }

    private Mc263StructureCarrier advanceReferences(Mc263StructureCarrier predecessor,
            ValidStart target) {
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        int changed = 0;
        for (Mc263StructureCarrier.ChunkStarts chunk : predecessor.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(slot.key())
                        && entry.body() instanceof ValidStart valid
                        && valid.startKey().equals(target.startKey())) {
                    if (valid.references() != 0) {
                        throw new IllegalArgumentException(
                                "Camp successor requires references=0 predecessor");
                    }
                    starts.add(new Mc263StructureCarrier.StartEntry(slot.key(),
                            new ValidStart(valid.startKey(), valid.originChunkX(),
                                    valid.originChunkZ(), 1, valid.adjustedBoundingBox(),
                                    valid.orderedPieces())));
                    changed++;
                } else {
                    starts.add(entry);
                }
            }
            chunks.add(chunk.withStarts(starts));
        }
        if (changed != 1) {
            throw new IllegalArgumentException("Camp successor start identity mismatch: "
                    + target.startKey());
        }
        return new Mc263StructureCarrier(predecessor.registry(), chunks,
                predecessor.referenceChunks(), predecessor.rawStartPayloads(),
                predecessor.producerGraphPayloads());
    }

    private static Clip clip(SourceClip clip) {
        return new Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    static List<Mc263FinalChunkSidecars.StructureEntity> special9EntitiesForTest(
            Mc263AbandonedCampProducer.Start start, SourceClip clip) {
        return special9Entities(start, clip);
    }

    private static List<Mc263FinalChunkSidecars.StructureEntity> special9Entities(
            Mc263AbandonedCampProducer.Start start, SourceClip clip) {
        ArrayList<Mc263FinalChunkSidecars.StructureEntity> entities = new ArrayList<>();
        for (Mc263AbandonedCampProducer.Piece piece : start.piecesInAcceptedOrder()) {
            if (piece.kind() != ElementKind.TEMPLATE || !SPECIAL_9.equals(piece.templateKey())) {
                continue;
            }
            for (CushionSpec spec : CUSHIONS) {
                Vec block = Mc263AbandonedCampTemplateExecutor.transform(
                        spec.blockPosition(), piece.rotation());
                int blockX = Math.addExact(piece.position().x(), block.x());
                int blockY = Math.addExact(piece.position().y(), block.y());
                int blockZ = Math.addExact(piece.position().z(), block.z());
                if (!contains(clip, blockX, blockY, blockZ)) continue;

                DoubleXZ precise = transformEntity(spec.x(), spec.z(), piece.rotation());
                double x = piece.position().x() + precise.x();
                double y = piece.position().y() + spec.y();
                double z = piece.position().z() + precise.z();
                float yaw = rotateYaw(90.0F, piece.rotation());
                entities.add(new Mc263FinalChunkSidecars.StructureEntity(
                        CUSHION, "minecraft:structure", x, y, z, yaw, 0.0F,
                        0.0D, 0.0D, 0.0D,
                        cushionPayload(blockX, blockY, blockZ, x, y, z, yaw)));
            }
        }
        return List.copyOf(entities);
    }

    private static boolean contains(SourceClip clip, int x, int y, int z) {
        return x >= clip.minX() && x <= clip.maxX()
                && y >= clip.minY() && y <= clip.maxY()
                && z >= clip.minZ() && z <= clip.maxZ();
    }

    private static DoubleXZ transformEntity(double x, double z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new DoubleXZ(x, z);
            case CLOCKWISE_90 -> new DoubleXZ(1.0D - z, x);
            case CLOCKWISE_180 -> new DoubleXZ(1.0D - x, 1.0D - z);
            case COUNTERCLOCKWISE_90 -> new DoubleXZ(z, 1.0D - x);
        };
    }

    private static float rotateYaw(float yaw, Rotation rotation) {
        float result = (yaw + switch (rotation) {
            case NONE -> 0.0F;
            case CLOCKWISE_90 -> 90.0F;
            case CLOCKWISE_180 -> 180.0F;
            case COUNTERCLOCKWISE_90 -> 270.0F;
        }) % 360.0F;
        return result < 0.0F ? result + 360.0F : result;
    }

    private static byte[] cushionPayload(int blockX, int blockY, int blockZ,
            double x, double y, double z, float yaw) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(257);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF("");
                out.writeByte(9); out.writeUTF("Motion"); out.writeByte(6); out.writeInt(3);
                out.writeDouble(0.0D); out.writeDouble(0.0D); out.writeDouble(0.0D);
                out.writeByte(8); out.writeUTF("color"); out.writeUTF("lime");
                out.writeByte(11); out.writeUTF("block_pos"); out.writeInt(3);
                out.writeInt(blockX); out.writeInt(blockY); out.writeInt(blockZ);
                out.writeByte(1); out.writeUTF("Invulnerable"); out.writeByte(0);
                out.writeByte(6); out.writeUTF("fall_distance"); out.writeDouble(0.0D);
                out.writeByte(2); out.writeUTF("Air"); out.writeShort(300);
                out.writeByte(1); out.writeUTF("OnGround"); out.writeByte(0);
                out.writeByte(3); out.writeUTF("PortalCooldown"); out.writeInt(0);
                out.writeByte(9); out.writeUTF("Rotation"); out.writeByte(5); out.writeInt(2);
                out.writeFloat(yaw); out.writeFloat(0.0F);
                out.writeByte(9); out.writeUTF("Pos"); out.writeByte(6); out.writeInt(3);
                out.writeDouble(x); out.writeDouble(y); out.writeDouble(z);
                out.writeByte(2); out.writeUTF("Fire"); out.writeShort(0);
                out.writeByte(8); out.writeUTF("id"); out.writeUTF(CUSHION);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory Camp Cushion NBT encoding failed", impossible);
        }
    }

    private static Set<String> supportedFeatures() {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Mc263AbandonedCampConfiguredFeatureExecutor.Mapping mapping
                : Mc263AbandonedCampConfiguredFeatureExecutor.mappings()) {
            values.add(mapping.configuredKey());
            values.add(mapping.targetKey());
        }
        values.add("minecraft:pale_moss_patch");
        return java.util.Collections.unmodifiableSet(values);
    }

    private static List<Slot> buildSlots() {
        List<Mc263AbandonedCampStartGenerator.StructureFact> facts =
                Mc263AbandonedCampStartGenerator.structureFacts();
        if (facts.size() != STRUCTURE_COUNT) {
            throw new ExceptionInInitializerError("Camp official structure count drift");
        }
        Map<String, Mc263AbandonedCampStartGenerator.StructureFact> byKey = new LinkedHashMap<>();
        for (Mc263AbandonedCampStartGenerator.StructureFact fact : facts) {
            if (byKey.putIfAbsent(fact.structureKey(), fact) != null) {
                throw new ExceptionInInitializerError("duplicate Camp official structure key");
            }
        }
        List<Mc263StructureIndexReceipt.Entry> step = Mc263StructureIndexReceipt.step(STEP);
        if (step.size() < STRUCTURE_COUNT) {
            throw new ExceptionInInitializerError("Camp pinned step is incomplete");
        }
        ArrayList<Slot> slots = new ArrayList<>(STRUCTURE_COUNT);
        for (int index = 0; index < STRUCTURE_COUNT; index++) {
            Mc263StructureIndexReceipt.Entry entry = step.get(index);
            Mc263AbandonedCampStartGenerator.StructureFact fact = byKey.remove(entry.key());
            if (fact == null || !entry.key().startsWith(PREFIX)
                    || entry.step() != STEP || entry.index() != index
                    || fact.stepLocalIndex() != index) {
                throw new ExceptionInInitializerError(
                        "Camp fixed schedule slot drift at index " + index + ": " + entry.key());
            }
            slots.add(new Slot(index, entry.key(), entry, fact));
        }
        if (!byKey.isEmpty()
                || (step.size() > STRUCTURE_COUNT
                        && step.get(STRUCTURE_COUNT).key().startsWith(PREFIX))) {
            throw new ExceptionInInitializerError("Camp all-18 schedule coverage drift");
        }
        return List.copyOf(slots);
    }

    record Slot(int index, String key, Mc263StructureIndexReceipt.Entry entry,
                Mc263AbandonedCampStartGenerator.StructureFact fact) { }

    private record PreparedStart(ValidStart persisted,
            Mc263AbandonedCampProducer.Start restored) { }

    private record CushionSpec(Vec blockPosition, double x, double y, double z) { }
    private record DoubleXZ(double x, double z) { }
    private record TickKey(int x, int y, int z, String key) { }

    private static final class StagedProductionTransaction implements ProductionWorldTransaction {
        private final Mc263FeaturesRegion region;
        private final StagedProductionTransaction outer;
        private final Set<Long> allowedOwners;
        private final long owner;
        private final StructurePlacementInput placement;
        private final boolean root;
        private final LinkedHashMap<Vec, String> overlay;
        private final ArrayList<Mc263FeaturesRegion.StructureBlockWrite> blocks = new ArrayList<>();
        private final LinkedHashMap<Vec, StagedBlockEntity> blockEntities = new LinkedHashMap<>();
        private final ArrayList<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = new ArrayList<>();
        private final ArrayList<Mc263FeaturesRegion.StructureBlockTick> blockTicks = new ArrayList<>();
        private final LinkedHashSet<TickKey> blockTickKeys = new LinkedHashSet<>();
        private final ArrayList<Mc263FeaturesRegion.StructureBee> bees = new ArrayList<>();
        private final ArrayList<Mc263FeaturesRegion.StructurePostprocessMark> postprocess =
                new ArrayList<>();
        private final ArrayList<StagedProductionTransaction> children;
        private Long selectedOwner;
        private Settlement published;
        private boolean publishedOnce;
        private boolean batchPrepared;
        private boolean rolledBack;

        private StagedProductionTransaction(Mc263FeaturesRegion region, Set<Long> owners,
                StructurePlacementInput placement) {
            this(region, 0L, true, placement, null, owners);
        }

        private StagedProductionTransaction(Mc263FeaturesRegion region, long owner, boolean root,
                StructurePlacementInput placement, StagedProductionTransaction outer,
                Set<Long> owners) {
            this.region = Objects.requireNonNull(region, "Camp FEATURES region");
            this.owner = owner;
            this.root = root;
            this.placement = Objects.requireNonNull(placement, "Camp structure placement input");
            this.outer = root ? this : Objects.requireNonNull(outer, "Camp outer transaction");
            this.overlay = root ? new LinkedHashMap<>() : this.outer.overlay;
            this.allowedOwners = root
                    ? Collections.unmodifiableSet(new LinkedHashSet<>(owners))
                    : this.outer.allowedOwners;
            this.children = root ? new ArrayList<>() : new ArrayList<>();
        }

        @Override public boolean supportsAtomicForkPublish() { return true; }
        @Override public boolean supportsFluidStateQueries() { return true; }
        @Override public boolean supportsBlockStateQueries() { return true; }
        @Override public boolean supportsSetBlockAndUpdate() { return true; }
        @Override public boolean supportsWriteFlags(int flags) { return WRITE_FLAGS.contains(flags); }
        @Override public boolean supportsExactState(String exactState) {
            return Mc263FeatureBlockState.supportsExactState(exactState);
        }
        @Override public boolean supportsBentPayloads() { return true; }
        @Override public boolean supportsLootPayloads() { return true; }
        @Override public boolean supportsBlockEntity(String blockIdentity, String entityType) {
            return ("minecraft:barrel".equals(blockIdentity)
                            && "minecraft:barrel".equals(entityType))
                    || (("minecraft:chest".equals(blockIdentity)
                            || "minecraft:oxidized_copper_chest".equals(blockIdentity))
                            && "minecraft:chest".equals(entityType))
                    || ("minecraft:campfire".equals(blockIdentity)
                            && "minecraft:campfire".equals(entityType))
                    || ("minecraft:oxidized_copper_golem_statue".equals(blockIdentity)
                            && "minecraft:copper_golem_statue".equals(entityType));
        }
        @Override public boolean supportsLootTable(String lootTable) {
            return LOOT_TABLES.contains(lootTable);
        }
        @Override public boolean supportsFluidTick(String fluidKey, int delay) {
            return "minecraft:water".equals(fluidKey) && delay == 5;
        }
        @Override public int minY() { return Blocks.MIN_Y; }
        @Override public boolean supportsFeature(String configuredKey) {
            return SUPPORTED_FEATURES.contains(configuredKey);
        }
        @Override public boolean supportsTreeFinalization() { return true; }
        @Override public boolean supportsBeePayloads() { return true; }
        @Override public boolean supportsBlockTicks() { return true; }
        @Override public boolean supportsFluidTicks() { return true; }
        @Override public boolean supportsPostprocessing() { return true; }
        @Override public boolean supportsBeneathTreePodzolTag() { return true; }
        @Override public boolean supportsWorldHeightQueries() { return true; }
        @Override public boolean supportsInternalTreePredicates() { return true; }
        @Override public boolean supportsOwnership(long candidate) {
            return root ? allowedOwners.contains(candidate) : candidate == owner;
        }
        @Override public int worldHeight() { return Blocks.MAX_Y - Blocks.MIN_Y + 1; }

        private void selectOwner(long candidate) {
            if (!root || rolledBack || batchPrepared || !allowedOwners.contains(candidate)) {
                throw new IllegalStateException("Camp production owner selection mismatch");
            }
            selectedOwner = candidate;
        }

        @Override
        public ProductionWorldTransaction fork() {
            if (!root || rolledBack || batchPrepared || selectedOwner == null) {
                throw new IllegalStateException("Camp production transaction fork misuse");
            }
            StagedProductionTransaction child = new StagedProductionTransaction(
                    region, selectedOwner, false, placement, this, allowedOwners);
            children.add(child);
            selectedOwner = null;
            return child;
        }

        @Override
        public FluidState getFluidState(Vec position) {
            Mc263FeatureBlockState state = state(position.x(), position.y(), position.z());
            if (state.fluidAmount() == 0) return FluidState.emptyState();
            boolean source = state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                    || state.fluidKind() == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE;
            double height = source ? 1.0D : state.fluidAmount() / 9.0D;
            String key = state.fluidTypeKey();
            if ("minecraft:flowing_water".equals(key)) key = "minecraft:water";
            if ("minecraft:flowing_lava".equals(key)) key = "minecraft:lava";
            return new FluidState(key, source, height);
        }

        @Override public String getBlockState(Vec position) {
            return exactState(position.x(), position.y(), position.z());
        }

        @Override public boolean setBlock(Vec position, String exactState, int flags) {
            return write(position.x(), position.y(), position.z(), exactState, flags, owner);
        }

        @Override public boolean setBlockAndUpdate(Vec position, String exactState) {
            return write(position.x(), position.y(), position.z(), exactState, 3, owner);
        }

        @Override
        public BlockEntityAccess getBlockEntity(Vec position) {
            requireChild();
            String identity = blockKey(exactState(position.x(), position.y(), position.z()));
            String entityType = switch (identity) {
                case "minecraft:barrel" -> "minecraft:barrel";
                case "minecraft:chest", "minecraft:oxidized_copper_chest" -> "minecraft:chest";
                case "minecraft:campfire" -> "minecraft:campfire";
                case "minecraft:oxidized_copper_golem_statue" ->
                        "minecraft:copper_golem_statue";
                default -> null;
            };
            if (entityType == null || !supportsBlockEntity(identity, entityType)) return null;
            return blockEntities.computeIfAbsent(position,
                    ignored -> new StagedBlockEntity(identity, entityType));
        }

        @Override public void scheduleFluidTick(Vec position, String fluidKey, int delay) {
            scheduleFluidTick(position.x(), position.y(), position.z(), fluidKey, delay);
        }

        @Override
        public void publish(WorldTransaction isolated, Settlement settlement) {
            if (!root || rolledBack || batchPrepared
                    || !(isolated instanceof StagedProductionTransaction candidate)
                    || candidate.outer != this || !children.contains(candidate)
                    || candidate.publishedOnce) {
                throw new IllegalStateException("Camp production publish mismatch");
            }
            candidate.published = Objects.requireNonNull(settlement, "Camp production settlement");
            candidate.publishedOnce = true;
        }

        @Override public String exactState(int x, int y, int z) {
            return state(x, y, z).exactState();
        }

        @Override public String internalTreePredicateState(int x, int y, int z) {
            return exactState(x, y, z);
        }

        @Override
        public String internalTreePredicateFluidState(int x, int y, int z) {
            Mc263FeatureBlockState state = state(x, y, z);
            if (state.fluidAmount() == 0) return "minecraft:empty";
            if (state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                return "minecraft:water[level=0]";
            }
            throw new UnsupportedOperationException("Camp unauthenticated tree fluid predicate: "
                    + state.exactState());
        }

        @Override public boolean setBlock(int x, int y, int z, String exactState,
                int flags, long candidateOwner) {
            return write(x, y, z, exactState, flags, candidateOwner);
        }

        @Override
        public void storeBent(int x, int y, int z, String blockIdentity, String entityType) {
            requireChild();
            if (!"minecraft:bee_nest".equals(blockIdentity)
                    || !"minecraft:beehive".equals(entityType)) {
                throw new UnsupportedOperationException("unsupported Camp direct BENT pair: "
                        + blockIdentity + "/" + entityType);
            }
        }

        @Override public void storeBee(int x, int y, int z, int ticksInHive) {
            requireChild();
            bees.add(new Mc263FeaturesRegion.StructureBee(x, y, z, ticksInHive));
        }

        @Override public void scheduleBlockTick(int x, int y, int z, String blockKey, int delay) {
            requireChild();
            TickKey key = new TickKey(x, y, z, Objects.requireNonNull(blockKey, "Camp block tick"));
            if (!blockTickKeys.add(key)) return;
            blockTicks.add(new Mc263FeaturesRegion.StructureBlockTick(
                    x, y, z, blockKey, delay, 0, blockTicks.size()));
        }

        @Override public void scheduleFluidTick(int x, int y, int z,
                String fluidKey, int delay) {
            requireChild();
            fluidTicks.add(new Mc263FeaturesRegion.StructureFluidTick(
                    x, y, z, fluidKey, delay, 0, fluidTicks.size()));
        }

        @Override public void markPostprocess(int x, int y, int z) {
            requireChild();
            postprocess.add(new Mc263FeaturesRegion.StructurePostprocessMark(x, y, z));
        }

        private Mc263FeatureBlockState state(int x, int y, int z) {
            String staged = overlay.get(new Vec(x, y, z));
            return staged == null ? region.blockState(x, y, z)
                    : Mc263FeatureBlockState.fromExact(staged);
        }

        private boolean write(int x, int y, int z, String exactState,
                int flags, long candidateOwner) {
            requireChild();
            if (candidateOwner != owner) {
                throw new UnsupportedOperationException("Camp caller ownership mismatch");
            }
            if (!WRITE_FLAGS.contains(flags)) {
                throw new UnsupportedOperationException("Camp unsupported write flags " + flags);
            }
            String canonical = Mc263FeatureBlockState.fromExact(
                    Objects.requireNonNull(exactState, "Camp exact state")).exactState();
            if (!region.ensureCanWrite(x, y, z)) return false;
            Vec position = new Vec(x, y, z);
            overlay.put(position, canonical);
            blocks.add(new Mc263FeaturesRegion.StructureBlockWrite(
                    x, y, z, canonical, owner));
            return true;
        }

        private void requireChild() {
            if (root) throw new IllegalStateException("Camp production mutated root transaction");
        }

        private Mc263FeaturesRegion.StructureBatch requireBatch(
                List<Mc263FinalChunkSidecars.StructureEntity> entities) {
            if (!root || rolledBack || batchPrepared) {
                throw new IllegalStateException("Camp outer transaction is not active");
            }
            Objects.requireNonNull(entities, "Camp structure entities");
            if (children.isEmpty()) {
                if (!entities.isEmpty()) {
                    throw new IllegalStateException("Camp entities lack a published settlement");
                }
                batchPrepared = true;
                return new Mc263FeaturesRegion.StructureBatch(List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of());
            }

            ArrayList<Mc263FeaturesRegion.StructureBlockWrite> blocks = new ArrayList<>();
            ArrayList<Mc263FeaturesRegion.StructureBentEvidence> bent = new ArrayList<>();
            ArrayList<Mc263FeaturesRegion.StructureLoot> loot = new ArrayList<>();
            ArrayList<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = new ArrayList<>();
            ArrayList<Mc263FeaturesRegion.StructurePostprocessMark> postprocess =
                    new ArrayList<>();
            ArrayList<Mc263FeaturesRegion.StructureBlockTick> blockTicks = new ArrayList<>();
            ArrayList<Mc263FeaturesRegion.StructureBee> bees = new ArrayList<>();
            long fluidSubTickOrder = 0;
            long blockSubTickOrder = 0;
            for (StagedProductionTransaction child : children) {
                if (!child.publishedOnce || child.published == null) {
                    throw new IllegalStateException(
                            "Camp production did not publish exact settlement");
                }
                Settlement settlement = child.published;
                for (Mc263AbandonedCampTemplateExecutor.BentPayload value : settlement.bent()) {
                    StagedBlockEntity staged = child.blockEntities.get(value.position());
                    if (staged == null || !staged.blockIdentity().equals(value.blockIdentity())
                            || !staged.entityType().equals(value.entityType())
                            || !staged.changed
                            || !Arrays.equals(staged.canonicalNbt, value.canonicalNbt())) {
                        throw new IllegalStateException("Camp staged BENT/settlement mismatch at "
                                + value.position());
                    }
                    bent.add(new Mc263FeaturesRegion.StructureBentEvidence(
                            value.position().x(), value.position().y(), value.position().z(),
                            value.blockIdentity(), value.entityType(), value.canonicalNbt()));
                }
                for (Mc263AbandonedCampTemplateExecutor.LootPayload value : settlement.loot()) {
                    if (!placement.clip().contains(value.position().x(), value.position().y(),
                            value.position().z())) {
                        continue;
                    }
                    LootProductionContext productionContext = placement.productionContext(
                            value.position().x(), value.position().y(), value.position().z(),
                            value.table());
                    loot.add(new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                            value.position().y(), value.position().z(), value.table(),
                            value.signedLootSeed(), productionContext));
                }
                blocks.addAll(child.blocks);
                for (Mc263FeaturesRegion.StructureFluidTick value : child.fluidTicks) {
                    fluidTicks.add(new Mc263FeaturesRegion.StructureFluidTick(
                            value.blockX(), value.blockY(), value.blockZ(), value.fluidKey(),
                            value.delay(), value.priority(), fluidSubTickOrder++));
                }
                postprocess.addAll(child.postprocess);
                for (Mc263FeaturesRegion.StructureBlockTick value : child.blockTicks) {
                    blockTicks.add(new Mc263FeaturesRegion.StructureBlockTick(
                            value.blockX(), value.blockY(), value.blockZ(), value.blockKey(),
                            value.delay(), value.priority(), blockSubTickOrder++));
                }
                bees.addAll(child.bees);
            }
            batchPrepared = true;
            return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent,
                    entities, fluidTicks, postprocess, List.of(), blockTicks, bees);
        }

        private void rollback() {
            if (!root || rolledBack) return;
            rolledBack = true;
            batchPrepared = true;
            selectedOwner = null;
            overlay.clear();
            children.clear();
        }

        private static String blockKey(String exactState) {
            int bracket = exactState.indexOf('[');
            return bracket < 0 ? exactState : exactState.substring(0, bracket);
        }
    }

    private static final class StagedBlockEntity implements BlockEntityAccess {
        private final String blockIdentity;
        private final String entityType;
        private byte[] canonicalNbt;
        private boolean changed;

        private StagedBlockEntity(String blockIdentity, String entityType) {
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
        }

        @Override public String blockIdentity() { return blockIdentity; }
        @Override public String entityType() { return entityType; }
        @Override public void loadCanonicalNbt(byte[] canonicalNbt) {
            this.canonicalNbt = Objects.requireNonNull(
                    canonicalNbt, "Camp canonical block entity NBT").clone();
        }
        @Override public void setChanged() {
            if (canonicalNbt == null) {
                throw new IllegalStateException(
                        "Camp block entity changed before canonical load");
            }
            changed = true;
        }
    }
}
