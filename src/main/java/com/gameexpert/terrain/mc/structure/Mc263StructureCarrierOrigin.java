package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Registry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ProducerGraphPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.PlanningState;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.CandidateDecision;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.DecisionPlan;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.Outcome;
import com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess.BaseHeightSampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Assembles one complete {@link Mc263StructureCarrier} for a 5x5 target block from a world seed.
 *
 * <p>Vanilla builds a chunk's {@code STRUCTURE_REFERENCES} from the 17x17 chunks around it, so a
 * 5x5 target block needs every start decided in the 21x21 window that covers all 25 reference
 * neighbourhoods. This origin decides that whole window once, in a fixed scan order (source X
 * outer, source Z inner), through the landed
 * {@link Mc263StructureStartDecisionCoordinator} over {@link Mc263StructureSetStartPlanner},
 * {@link Mc263StructureWorldAccess} and {@link Mc263StructureAuthorityAccess}, and then projects
 * the result into one carrier: the pinned 52-entry registry, the 441 ordered start chunks, and
 * the 25 {@link Mc263StructureCarrier#createReferences} reference chunks.</p>
 *
 * <p>Height access goes through {@link BaseHeightSampler}: this class binds whatever sampler the
 * caller supplies and never invents a height lane of its own.</p>
 *
 * <p>The assembly is a pure function of {@code (worldSeed, targetChunkX, targetChunkZ, sampler)}:
 * repeating it produces byte-identical {@link Mc263StructureCarrier#receiptBytes()}. An optional
 * {@link RegionMemo} only avoids repeating decided chunks and must never change those bytes.</p>
 *
 * <p>Not thread safe: it drives one {@link Mc263StructureWorldAccess}, which keeps a mutable
 * climate-sampler search hint.</p>
 */
public final class Mc263StructureCarrierOrigin {
    /** Chebyshev radius of the carried target block: 5x5 chunks. */
    public static final int TARGET_RADIUS = 2;
    /** Chebyshev radius of one target's reference sources; mirrors the carrier's own radius. */
    public static final int SOURCE_RADIUS = Mc263StructureCarrier.REFERENCE_RADIUS;
    /** Chebyshev radius of the decided start window: 21x21 chunks. */
    public static final int WINDOW_RADIUS = TARGET_RADIUS + SOURCE_RADIUS;
    /** Side length of one memo region, in chunks. */
    public static final int REGION_CHUNKS = 16;

    private static final int TARGET_SIDE = TARGET_RADIUS * 2 + 1;
    private static final int SOURCE_SIDE = SOURCE_RADIUS * 2 + 1;
    private static final int WINDOW_SIDE = WINDOW_RADIUS * 2 + 1;
    private static final int REGION_SHIFT = 4;

    private Mc263StructureCarrierOrigin() { }

    /** One assembled carrier plus the decision facts that produced it. */
    public static final class Assembly {
        private final long worldSeed;
        private final int targetChunkX;
        private final int targetChunkZ;
        private final Mc263StructureCarrier carrier;
        private final List<ChunkStarts> windowStarts;
        private final List<String> resolvedStructureKeys;

        private Assembly(long worldSeed, int targetChunkX, int targetChunkZ,
                Mc263StructureCarrier carrier, List<ChunkStarts> windowStarts,
                List<String> resolvedStructureKeys) {
            this.worldSeed = worldSeed;
            this.targetChunkX = targetChunkX;
            this.targetChunkZ = targetChunkZ;
            this.carrier = carrier;
            this.windowStarts = List.copyOf(windowStarts);
            this.resolvedStructureKeys = List.copyOf(resolvedStructureKeys);
        }

        public long worldSeed() { return worldSeed; }
        public int targetChunkX() { return targetChunkX; }
        public int targetChunkZ() { return targetChunkZ; }
        public Mc263StructureCarrier carrier() { return carrier; }

        /** The 441 decided start chunks in window scan order (X outer, Z inner). */
        public List<ChunkStarts> windowStarts() { return windowStarts; }

        /** Every structure key that resolved to a valid start in the window, in scan order. */
        public List<String> resolvedStructureKeys() { return resolvedStructureKeys; }

        /** The valid start this assembly decided for one structure in one window chunk. */
        public ValidStart start(String structureKey, int chunkX, int chunkZ) {
            for (ChunkStarts chunk : windowStarts) {
                if (chunk.chunkX() != chunkX || chunk.chunkZ() != chunkZ) continue;
                for (StartEntry entry : chunk.orderedStarts()) {
                    if (entry.structureId().equals(structureKey)
                            && entry.body() instanceof ValidStart valid) {
                        return valid;
                    }
                }
            }
            return null;
        }
    }

    /**
     * One world's prepared carrier-assembly facts.
     *
     * <p>The seed-dependent planning state belongs to the world caller, never to a process-wide
     * cache. It is built once with that world's sampler and then reused for adjacent chunk
     * productions. All process-wide shared objects reached here are immutable pinned catalogs.</p>
     */
    public static final class WorldContext {
        private final long worldSeed;
        private final Mc263StructureWorldAccess world;
        private final PlanningState state;

        private WorldContext(long worldSeed, BaseHeightSampler sampler) {
            this.worldSeed = worldSeed;
            this.world = Mc263StructureWorldAccess.overworld(worldSeed)
                    .bindBaseHeightSampler(Objects.requireNonNull(sampler,
                            "base-height sampler"));
            this.state = world.buildState();
        }

        public long worldSeed() { return worldSeed; }
    }

    /**
     * Bounded per-{@code (worldSeed, regionKey)} LRU over decided chunk starts.
     *
     * <p>Each chunk's decision is a pure function of the seed and that chunk's coordinates, so
     * reusing a decided chunk is byte-neutral. Regions are {@value #REGION_CHUNKS} chunks square;
     * the least recently used region is evicted once {@code capacity} regions are resident.</p>
     */
    public static final class RegionMemo {
        private final int capacity;
        private final LinkedHashMap<Long, Map<Long, CachedStarts>> regions;
        private long hits;
        private long misses;
        private long evictions;

        private RegionMemo(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("non-positive memo capacity");
            this.capacity = capacity;
            this.regions = new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<Long, Map<Long, CachedStarts>> eldest) {
                    boolean evict = size() > RegionMemo.this.capacity;
                    if (evict) evictions++;
                    return evict;
                }
            };
        }

        public static RegionMemo bounded(int capacity) { return new RegionMemo(capacity); }

        public int residentRegions() { return regions.size(); }
        public long hits() { return hits; }
        public long misses() { return misses; }
        public long evictions() { return evictions; }

        private ChunkStarts cached(long worldSeed, int chunkX, int chunkZ) {
            Map<Long, CachedStarts> region = regions.get(regionKey(worldSeed, chunkX, chunkZ));
            CachedStarts cached = region == null ? null
                    : region.get(Mc263StructureCarrier.packChunk(chunkX, chunkZ));
            if (cached == null) misses++; else hits++;
            return cached == null ? null : cached.starts();
        }

        private void store(long worldSeed, ChunkStarts starts,
                Mc263StructureAuthorityAccess authority) {
            List<RawStartPayload> raw = new ArrayList<>();
            List<ProducerGraphPayload> graphs = new ArrayList<>();
            for (StartEntry entry : starts.orderedStarts()) {
                if (entry.body() instanceof ValidStart valid) {
                    authority.rawStartPayload(entry.structureId(), valid).ifPresent(raw::add);
                    authority.producerGraphPayload(entry.structureId(), valid)
                            .ifPresent(graphs::add);
                }
            }
            regions.computeIfAbsent(
                    regionKey(worldSeed, starts.chunkX(), starts.chunkZ()),
                    ignored -> new LinkedHashMap<>())
                    .put(Mc263StructureCarrier.packChunk(starts.chunkX(), starts.chunkZ()),
                            new CachedStarts(starts, raw, graphs));
        }

        private Optional<RawStartPayload> rawStartPayload(long worldSeed, String structureId,
                ValidStart start) {
            Map<Long, CachedStarts> region = regions.get(regionKey(worldSeed,
                    start.originChunkX(), start.originChunkZ()));
            CachedStarts cached = region == null ? null : region.get(
                    Mc263StructureCarrier.packChunk(start.originChunkX(), start.originChunkZ()));
            if (cached == null) return Optional.empty();
            return cached.rawStartPayloads().stream()
                    .filter(payload -> payload.structureId().equals(structureId)
                            && payload.startKey().equals(start.startKey()))
                    .findFirst();
        }

        private Optional<ProducerGraphPayload> producerGraphPayload(long worldSeed,
                String structureId, ValidStart start) {
            Map<Long, CachedStarts> region = regions.get(regionKey(worldSeed,
                    start.originChunkX(), start.originChunkZ()));
            CachedStarts cached = region == null ? null : region.get(
                    Mc263StructureCarrier.packChunk(start.originChunkX(), start.originChunkZ()));
            if (cached == null) return Optional.empty();
            return cached.producerGraphPayloads().stream()
                    .filter(payload -> payload.structureId().equals(structureId)
                            && payload.startKey().equals(start.startKey()))
                    .findFirst();
        }

        private static long regionKey(long worldSeed, int chunkX, int chunkZ) {
            long region = Mc263StructureCarrier.packChunk(chunkX >> REGION_SHIFT,
                    chunkZ >> REGION_SHIFT);
            return worldSeed * 0x9E3779B97F4A7C15L ^ region;
        }

        private record CachedStarts(ChunkStarts starts,
                List<RawStartPayload> rawStartPayloads,
                List<ProducerGraphPayload> producerGraphPayloads) {
            private CachedStarts {
                Objects.requireNonNull(starts, "cached structure starts");
                rawStartPayloads = List.copyOf(rawStartPayloads);
                producerGraphPayloads = List.copyOf(producerGraphPayloads);
            }
        }
    }

    /** Assembles the carrier for one target block without a memo. */
    public static Assembly assemble(long worldSeed, int targetChunkX, int targetChunkZ,
            BaseHeightSampler sampler) {
        return assemble(worldSeed, targetChunkX, targetChunkZ, sampler, null);
    }

    /**
     * Assembles the carrier for one target block, optionally reusing already decided chunks.
     *
     * @param memo bounded region cache, or {@code null} to decide every window chunk fresh
     */
    public static Assembly assemble(long worldSeed, int targetChunkX, int targetChunkZ,
            BaseHeightSampler sampler, RegionMemo memo) {
        return assemble(prepare(worldSeed, sampler), targetChunkX, targetChunkZ, memo);
    }

    /** Prepares the seed-dependent planning state once for one world caller. */
    public static WorldContext prepare(long worldSeed, BaseHeightSampler sampler) {
        return new WorldContext(worldSeed, sampler);
    }

    /** Assembles one carrier using a caller-owned prepared world context. */
    public static Assembly assemble(WorldContext context, int targetChunkX, int targetChunkZ,
            RegionMemo memo) {
        Objects.requireNonNull(context, "world context");
        Mc263StructureWorldAccess world = context.world;
        PlanningState state = context.state;
        long worldSeed = context.worldSeed;
        Mc263StructureAuthorityAccess authority = Mc263StructureAuthorityAccess.landed(world);
        Mc263StructureStartDecisionCoordinator coordinator =
                Mc263StructureStartDecisionCoordinator.pinned();
        Registry registry = authority.registry();

        List<ChunkStarts> windowStarts = new ArrayList<>(WINDOW_SIDE * WINDOW_SIDE);
        List<String> resolved = new ArrayList<>();
        for (int chunkX = targetChunkX - WINDOW_RADIUS; chunkX <= targetChunkX + WINDOW_RADIUS;
                chunkX++) {
            for (int chunkZ = targetChunkZ - WINDOW_RADIUS;
                    chunkZ <= targetChunkZ + WINDOW_RADIUS; chunkZ++) {
                ChunkStarts starts = memo == null ? null : memo.cached(worldSeed, chunkX, chunkZ);
                if (starts == null) {
                    world.beginStructureChunkDecision();
                    try {
                        starts = decideChunk(coordinator, worldSeed, chunkX, chunkZ, state, world,
                                authority);
                    } finally {
                        world.endStructureChunkDecision();
                    }
                    if (memo != null) memo.store(worldSeed, starts, authority);
                }
                try {
                    starts.validateOnce(registry);
                } catch (RuntimeException invalid) {
                    List<String> keys = new ArrayList<>();
                    for (StartEntry entry : starts.orderedStarts()) keys.add(entry.structureId());
                    throw new IllegalStateException("decided start chunk " + chunkX + ","
                            + chunkZ + " is not carriable: " + keys, invalid);
                }
                windowStarts.add(starts);
                for (StartEntry entry : starts.orderedStarts()) {
                    resolved.add(entry.structureId());
                }
            }
        }

        Map<Long, ChunkStarts> byChunk = new LinkedHashMap<>();
        for (ChunkStarts starts : windowStarts) {
            byChunk.put(Mc263StructureCarrier.packChunk(starts.chunkX(), starts.chunkZ()), starts);
        }

        List<ChunkReferences> references = new ArrayList<>(TARGET_SIDE * TARGET_SIDE);
        for (int chunkX = targetChunkX - TARGET_RADIUS; chunkX <= targetChunkX + TARGET_RADIUS;
                chunkX++) {
            for (int chunkZ = targetChunkZ - TARGET_RADIUS;
                    chunkZ <= targetChunkZ + TARGET_RADIUS; chunkZ++) {
                List<ChunkStarts> sources = new ArrayList<>(SOURCE_SIDE * SOURCE_SIDE);
                for (int sourceX = chunkX - SOURCE_RADIUS; sourceX <= chunkX + SOURCE_RADIUS;
                        sourceX++) {
                    for (int sourceZ = chunkZ - SOURCE_RADIUS; sourceZ <= chunkZ + SOURCE_RADIUS;
                            sourceZ++) {
                        ChunkStarts source = byChunk.get(
                                Mc263StructureCarrier.packChunk(sourceX, sourceZ));
                        if (source == null) {
                            throw new IllegalStateException("undecided reference source chunk "
                                    + sourceX + "," + sourceZ);
                        }
                        sources.add(source);
                    }
                }
                references.add(Mc263StructureCarrier.createReferences(chunkX, chunkZ, registry,
                        sources));
            }
        }

        List<RawStartPayload> rawStartPayloads = new ArrayList<>();
        List<ProducerGraphPayload> producerGraphPayloads = new ArrayList<>();
        for (ChunkStarts starts : windowStarts) {
            for (StartEntry entry : starts.orderedStarts()) {
                if (entry.body() instanceof ValidStart valid) {
                    Optional<RawStartPayload> raw = authority.rawStartPayload(
                            entry.structureId(), valid);
                    if (raw.isEmpty() && memo != null) {
                        raw = memo.rawStartPayload(worldSeed, entry.structureId(), valid);
                    }
                    raw.ifPresent(rawStartPayloads::add);
                    Optional<ProducerGraphPayload> graph = authority.producerGraphPayload(
                            entry.structureId(), valid);
                    if (graph.isEmpty() && memo != null) {
                        graph = memo.producerGraphPayload(worldSeed, entry.structureId(), valid);
                    }
                    graph.ifPresent(producerGraphPayloads::add);
                }
            }
        }
        Mc263StructureCarrier carrier = new Mc263StructureCarrier(registry, windowStarts,
                references, rawStartPayloads, producerGraphPayloads);
        return new Assembly(worldSeed, targetChunkX, targetChunkZ, carrier, windowStarts,
                resolved);
    }

    /**
     * Decides one chunk and projects its resolved starts into the chunk's start map. Vanilla
     * stores one start per structure in the order the sets were resolved, which is exactly the
     * planner's possible-set order carried by {@link DecisionPlan#decisions()}.
     */
    private static ChunkStarts decideChunk(Mc263StructureStartDecisionCoordinator coordinator,
            long worldSeed, int chunkX, int chunkZ, PlanningState state,
            Mc263StructureWorldAccess world, Mc263StructureAuthorityAccess authority) {
        DecisionPlan plan = coordinator.decide(worldSeed, chunkX, chunkZ, state, world, authority);
        List<StartEntry> entries = new ArrayList<>();
        for (CandidateDecision decision : plan.decisions()) {
            if (decision.outcome() != Outcome.GENERATED_VALID
                    && decision.outcome() != Outcome.EXISTING_VALID) {
                continue;
            }
            ValidStart start = authority
                    .persistedStart(decision.resolvedStructureKey(), chunkX, chunkZ)
                    .orElseThrow(() -> new IllegalStateException(
                            "resolved start is not persisted: "
                                    + decision.resolvedStructureKey()));
            entries.add(new StartEntry(decision.resolvedStructureKey(), start));
        }
        return new ChunkStarts(chunkX, chunkZ,
                entries.isEmpty() ? Collections.emptyList() : entries);
    }
}
