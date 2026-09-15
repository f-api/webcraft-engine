package com.gameexpert.terrain.mc.loot;

import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263StructureAuthorityAccess;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.Candidate;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.PlanningState;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.CandidateDecision;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.Outcome;
import com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess;
import com.gameexpert.terrain.mc.surface.McBiomeZoom;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Exact pinned 26.3 exploration-map structure locator. */
public final class Mc263LocatedMapAuthority {
    public static final int SCALE = 2;
    public static final int SEARCH_RADIUS = 50;
    public static final boolean SKIP_EXISTING_CHUNKS = true;
    public static final int BURIED_TREASURE_SCALE = 1;
    public static final boolean BURIED_TREASURE_SKIP_EXISTING_CHUNKS = false;
    public static final int PREVIEW_COLOR_COUNT = 128 * 128;
    /** Stored BIOMES window still queryable by the synchronous locate/map call. */
    static final int LOCATE_RETAINED_BIOME_RADIUS = 3;
    private static final int DEFAULT_LOCATOR_CACHE_CAPACITY = 512;
    private static final int DEFAULT_PREVIEW_CACHE_CAPACITY = 64;

    private static final byte[] LOCATOR_SOURCE_DOMAIN =
            "MC263-LOCATED-MAP-AUTHORITY-SOURCE-V1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FOUND_DOMAIN =
            "MC263-LOCATED-MAP-FOUND-V2\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NOT_FOUND_DOMAIN =
            "MC263-LOCATED-MAP-NOT-FOUND-V2\0".getBytes(StandardCharsets.US_ASCII);
    public static final String OFFICIAL_PREVIEW_SOURCE_RECEIPT =
            "2fcbaf2a2a994c0ff45626211ac9a204b32bb301d9e135265b856ce76bd205e6";
    private static final String OFFICIAL_LOCATOR_SOURCE_RECEIPT =
            "9bc95931450de45e6723672b83f32cf0149b61467327a2f8db2c3ced3f65d58c";
    private static final HexFormat HEX = HexFormat.of();
    public static final String BURIED_TREASURE_EVIDENCE_RECEIPT =
            buriedTreasureEvidenceReceipt();

    private static final Map<String, Destination> DESTINATIONS = Map.ofEntries(
            entry("bamboo_jungle", "#minecraft:on_abandoned_camp_bamboo_jungle",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_bamboo_jungle"),
            entry("cherry_grove", "#minecraft:on_abandoned_camp_cherry_grove",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_cherry_grove"),
            entry("birch_forest", "#minecraft:on_abandoned_camp_birch_forest",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_birch_forest"),
            entry("dappled_forest", "#minecraft:on_abandoned_camp_dappled_forest",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_dappled_forest"),
            entry("flower_forest", "#minecraft:on_abandoned_camp_flower_forest",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_flower_forest"),
            entry("pale_garden", "#minecraft:on_abandoned_camp_pale_garden",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_pale_garden"),
            entry("swamp", "#minecraft:on_abandoned_camp_swamp",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_swamp"),
            entry("windswept_forest", "#minecraft:on_abandoned_camp_windswept",
                    "minecraft:abandoned_camp", "minecraft:abandoned_camp_windswept_forest"),
            entry("ancient_city", "#minecraft:on_ancient_city_maps",
                    "minecraft:ancient_cities", "minecraft:ancient_city"),
            entry("trial_chambers", "#minecraft:on_trial_chambers_maps",
                    "minecraft:trial_chambers", "minecraft:trial_chambers"),
            entry("mineshaft", "#minecraft:on_mineshaft_maps",
                    "minecraft:mineshafts", "minecraft:mineshaft"),
            entry("desert_pyramid", "#minecraft:on_desert_pyramid_maps",
                    "minecraft:desert_pyramids", "minecraft:desert_pyramid"),
            entry("jungle_temple", "#minecraft:on_jungle_explorer_maps",
                    "minecraft:jungle_temples", "minecraft:jungle_pyramid"),
            entry("ocean_ruin_warm", "#minecraft:on_ocean_ruin_warm_maps",
                    "minecraft:ocean_ruins", "minecraft:ocean_ruin_warm"),
            entry("woodland_mansion", "#minecraft:on_woodland_explorer_maps",
                    "minecraft:woodland_mansions", "minecraft:mansion"),
            entry("buried_treasure", "#minecraft:on_treasure_maps",
                    "minecraft:buried_treasures", BURIED_TREASURE_SCALE, SEARCH_RADIUS,
                    BURIED_TREASURE_SKIP_EXISTING_CHUNKS, "minecraft:buried_treasure"));

    private static final StructureReferenceSnapshot NO_REFERENCE_SNAPSHOT =
            new StructureReferenceSnapshot() {
                private final String receipt = sha256(
                        "MC263-LOCATED-MAP-NO-REFERENCE-SNAPSHOT-V1"
                                .getBytes(StandardCharsets.US_ASCII));
                @Override public String receipt() { return receipt; }
                @Override public int references(String structureKey, int chunkX, int chunkZ) {
                    throw new IllegalStateException(
                            "skip-existing=false locator must not read structure references");
                }
            };

    private final PreviewRenderer previewRenderer;
    private final StructureReferenceAuthority structureReferences;
    private final String locatorSourceReceipt;
    private final BoundedMemo<CacheKey, LocatedMapTarget> cache;
    private final BoundedMemo<PreviewKey, byte[]> previewCache;
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong previewHits = new AtomicLong();
    private final AtomicLong previewMisses = new AtomicLong();

    private Mc263LocatedMapAuthority(PreviewRenderer previewRenderer,
            StructureReferenceAuthority structureReferences, int locatorCapacity,
            int previewCapacity, boolean production) {
        this.previewRenderer = Objects.requireNonNull(previewRenderer, "map preview renderer");
        this.structureReferences = Objects.requireNonNull(
                structureReferences, "structure reference authority");
        requireSha256(previewRenderer.sourceReceipt(), "map preview renderer source receipt");
        this.cache = new BoundedMemo<>(locatorCapacity);
        this.previewCache = new BoundedMemo<>(previewCapacity);
        locatorSourceReceipt = sourceReceipt(previewRenderer.sourceReceipt());
        if (production && !MessageDigest.isEqual(
                HEX.parseHex(OFFICIAL_LOCATOR_SOURCE_RECEIPT),
                HEX.parseHex(locatorSourceReceipt))) {
            throw new IllegalStateException("located-map locator source receipt drift");
        }
    }

    /** Compatibility construction is fail-closed until durable reference state is bound. */
    public static Mc263LocatedMapAuthority pinned(PreviewRenderer previewRenderer) {
        return pinned(previewRenderer, StructureReferenceAuthority.unbound());
    }

    /** Production construction with one exact immutable world-reference snapshot authority. */
    public static Mc263LocatedMapAuthority pinned(PreviewRenderer previewRenderer,
            StructureReferenceAuthority structureReferences) {
        return new Mc263LocatedMapAuthority(previewRenderer, structureReferences,
                DEFAULT_LOCATOR_CACHE_CAPACITY, DEFAULT_PREVIEW_CACHE_CAPACITY, true);
    }

    /** Explicit focused-fixture construction; never a durable-world production binding. */
    public static Mc263LocatedMapAuthority pinnedUnreferencedFixture(
            PreviewRenderer previewRenderer) {
        return pinnedFixture(previewRenderer, StructureReferenceAuthority.unreferencedFixture());
    }

    static Mc263LocatedMapAuthority pinnedFixture(PreviewRenderer previewRenderer,
            StructureReferenceAuthority structureReferences) {
        return new Mc263LocatedMapAuthority(previewRenderer, structureReferences,
                DEFAULT_LOCATOR_CACHE_CAPACITY, DEFAULT_PREVIEW_CACHE_CAPACITY, false);
    }

    static Mc263LocatedMapAuthority pinnedForCacheTest(PreviewRenderer previewRenderer,
            StructureReferenceAuthority structureReferences, int locatorCapacity,
            int previewCapacity) {
        return new Mc263LocatedMapAuthority(previewRenderer, structureReferences,
                locatorCapacity, previewCapacity, false);
    }

    /** Arbitrary-seed canonical renderer used by production; official rows prove sample parity. */
    public static PreviewRenderer biomePreviewRenderer() {
        return new BiomePreviewRenderer();
    }

    public static Mc263LocatedMapAuthority pinnedBiomePreview() {
        return pinned(biomePreviewRenderer());
    }

    public static Mc263LocatedMapAuthority pinnedBiomePreview(
            StructureReferenceAuthority structureReferences) {
        return pinned(biomePreviewRenderer(), structureReferences);
    }

    public static List<Destination> destinations() {
        return DESTINATIONS.values().stream()
                .sorted(java.util.Comparator.comparing(Destination::destination)).toList();
    }

    public LocatedMapTarget locate(long worldSeed, String sourceIdentity, String tableIdentity,
            int originX, int originY, int originZ, String destination) {
        return openSession(worldSeed).locate(sourceIdentity, tableIdentity,
                originX, originY, originZ, destination);
    }

    /** Resolves a complete context under exactly one immutable structure-reference epoch. */
    public Map<String, LocatedMapTarget> locateAll(long worldSeed, String sourceIdentity,
            String tableIdentity, int originX, int originY, int originZ,
            List<String> destinations) {
        return openSession(worldSeed).locateAll(sourceIdentity, tableIdentity,
                originX, originY, originZ, destinations);
    }

    /**
     * Opens one seed-bound locator session. Its durable snapshot is acquired lazily on the first
     * lookup and then reused for every distinct production context emitted by the owning target
     * chunk authority.
     */
    public LocatedMapSession openSession(long worldSeed) {
        return new LocatedMapSession(worldSeed);
    }

    public final class LocatedMapSession {
        private final long worldSeed;
        private StructureReferenceSnapshot referenceSnapshot;

        private LocatedMapSession(long worldSeed) { this.worldSeed = worldSeed; }

        public long worldSeed() { return worldSeed; }

        public LocatedMapTarget locate(String sourceIdentity, String tableIdentity,
                int originX, int originY, int originZ, String destination) {
            Destination requested = requireDestination(destination);
            return locateUnderSnapshot(worldSeed, sourceIdentity, tableIdentity,
                    originX, originY, originZ, destination,
                    requested.skipExistingChunks() ? snapshot() : NO_REFERENCE_SNAPSHOT);
        }

        public Map<String, LocatedMapTarget> locateAll(String sourceIdentity,
                String tableIdentity, int originX, int originY, int originZ,
                List<String> destinations) {
            Objects.requireNonNull(destinations, "located-map destinations");
            List<String> requestedDestinations = List.copyOf(destinations);
            boolean needsReferences = requestedDestinations.stream()
                    .map(Mc263LocatedMapAuthority::requireDestination)
                    .anyMatch(Destination::skipExistingChunks);
            StructureReferenceSnapshot frozen = needsReferences
                    ? snapshot() : NO_REFERENCE_SNAPSHOT;
            LinkedHashMap<String, LocatedMapTarget> results = new LinkedHashMap<>();
            for (String destination : requestedDestinations) {
                if (results.putIfAbsent(destination, locateUnderSnapshot(worldSeed,
                        sourceIdentity, tableIdentity, originX, originY, originZ, destination,
                        frozen)) != null) {
                    throw new IllegalArgumentException("duplicate located-map destination");
                }
            }
            return java.util.Collections.unmodifiableMap(results);
        }

        private synchronized StructureReferenceSnapshot snapshot() {
            if (referenceSnapshot == null) {
                referenceSnapshot = Mc263LocatedMapAuthority.this.snapshot(worldSeed);
            }
            return referenceSnapshot;
        }
    }

    private StructureReferenceSnapshot snapshot(long worldSeed) {
        StructureReferenceSnapshot result = Objects.requireNonNull(
                structureReferences.snapshot(worldSeed), "structure reference snapshot");
        requireSha256(result.receipt(), "structure reference snapshot receipt");
        return result;
    }

    private LocatedMapTarget locateUnderSnapshot(long worldSeed, String sourceIdentity,
            String tableIdentity, int originX, int originY, int originZ, String destination,
            StructureReferenceSnapshot referenceSnapshot) {
        requireSha256(sourceIdentity, "located-map source identity");
        requireMinecraftKey(tableIdentity, "located-map table identity");
        Destination requested = requireDestination(destination);
        StructureReferenceSnapshot effectiveSnapshot = requested.skipExistingChunks()
                ? referenceSnapshot : NO_REFERENCE_SNAPSHOT;
        requireSha256(effectiveSnapshot.receipt(), "structure reference snapshot receipt");
        CacheKey key = new CacheKey(worldSeed, sourceIdentity, tableIdentity,
                originX, originY, originZ, destination, effectiveSnapshot.receipt());
        LocatedMapTarget cached = cache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        cacheMisses.incrementAndGet();
        LocatedMapTarget computed = locateUncached(worldSeed, sourceIdentity, tableIdentity,
                originX, originY, originZ, requested, effectiveSnapshot);
        return cache.putIfAbsent(key, computed);
    }

    private LocatedMapTarget locateUncached(long worldSeed, String sourceIdentity,
            String tableIdentity, int originX, int originY, int originZ,
            Destination requested, StructureReferenceSnapshot referenceSnapshot) {
        String worldIdentity = Long.toString(worldSeed);
        Binding binding = new Binding(requested.destination(), requested.destinationTag(),
                requested.structureSet(), requested.acceptedMembers(), worldIdentity,
                sourceIdentity, tableIdentity, originX, originY, originZ, requested.scale(),
                requested.searchRadius(), requested.skipExistingChunks(), locatorSourceReceipt,
                referenceSnapshot.receipt());

        Mc263StructureSetStartPlanner planner = Mc263StructureSetStartPlanner.pinned();
        Mc263StructureWorldAccess world = Mc263StructureWorldAccess.overworld(worldSeed);
        PlanningState completeState = world.buildState();
        if (!completeState.possibleSetKeys().contains(requested.structureSet())) {
            return notFound(binding);
        }
        PlanningState locateState = new PlanningState(worldSeed,
                List.of(requested.structureSet()), List.of());
        Mc263StructureStartDecisionCoordinator coordinator =
                Mc263StructureStartDecisionCoordinator.pinned();
        // A map locate and its preview share one ServerLevel in vanilla.  In particular, the
        int spacing = planner.gridSpacing(requested.structureSet());
        int originChunkX = Math.floorDiv(originX, 16);
        int originChunkZ = Math.floorDiv(originZ, 16);
        // FULL generation has wider transient dependencies, but the synchronous locate/map call
        // can still query only this retained ServerLevel BIOMES window as stored section grids.
        world.activateFullBiomeNeighborhood(originChunkX, originChunkZ,
                LOCATE_RETAINED_BIOME_RADIUS);
        // FULL biome generation uses worker-local sampler state. The server-thread sampler state
        // established while building the level remains live through locate and preview.
        planner.beginLocatePerimeter(world);
        for (int radius = 0; radius <= requested.searchRadius(); radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) continue;
                    int probeX = Math.addExact(originChunkX,
                            Math.multiplyExact(spacing, offsetX));
                    int probeZ = Math.addExact(originChunkZ,
                            Math.multiplyExact(spacing, offsetZ));
                    Candidate candidate = planner.candidateForGridProbe(worldSeed, probeX, probeZ,
                            requested.structureSet(), locateState, world);
                    Mc263StructureAuthorityAccess authority =
                            Mc263StructureAuthorityAccess.landed(world);
                    var plan = coordinator.decide(worldSeed, candidate.chunkX(), candidate.chunkZ(),
                            locateState, world, authority);
                    CandidateDecision accepted = plan.decisions().stream()
                            .filter(decision -> decision.setKey().equals(requested.structureSet()))
                            .filter(decision -> decision.outcome() == Outcome.GENERATED_VALID
                                    || decision.outcome() == Outcome.EXISTING_VALID)
                            .filter(decision -> requested.acceptedMembers()
                                    .contains(decision.resolvedStructureKey()))
                            .findFirst().orElse(null);
                    if (accepted == null) continue;
                    if (requested.skipExistingChunks()) {
                        int references = referenceSnapshot.references(accepted.resolvedStructureKey(),
                                candidate.chunkX(), candidate.chunkZ());
                        if (references < 0) {
                            throw new IllegalStateException(
                                    "durable structure reference count is negative");
                        }
                        if (references != 0) continue;
                    }
                    int targetX = candidate.locatePos().x();
                    int targetZ = candidate.locatePos().z();
                    int savedCenterX = savedCenter(targetX, requested.scale());
                    int savedCenterZ = savedCenter(targetZ, requested.scale());
                    byte[] preview = renderPreview(world, binding, savedCenterX, savedCenterZ,
                            requested.scale());
                    String previewSha256 = sha256(preview);
                    String receipt = foundReceipt(binding, targetX, targetZ, savedCenterX,
                            savedCenterZ, previewSha256);
                    return new Found(binding, targetX, targetZ, savedCenterX, savedCenterZ,
                            previewSha256, receipt);
                }
            }
        }
        return notFound(binding);
    }

    public CacheStats cacheStats() {
        return new CacheStats(cache.size(), cacheHits.get(), cacheMisses.get());
    }

    public PreviewCacheStats previewCacheStats() {
        return new PreviewCacheStats(previewCache.size(), previewHits.get(),
                previewMisses.get(), previewCache.evictions());
    }

    public byte[] renderVerifiedPreview(Found found) {
        Objects.requireNonNull(found, "located-map found target").requireAuthenticated();
        Binding binding = found.binding();
        long worldSeed = Long.parseLong(binding.worldIdentity());
        PreviewKey key = previewKey(worldSeed, binding, found.savedCenterX(),
                found.savedCenterZ(), binding.scale());
        byte[] cached = previewCache.get(key);
        byte[] preview;
        if (cached != null) {
            previewHits.incrementAndGet();
            preview = cached.clone();
        } else if (key.locateContextIdentity().isEmpty()) {
            preview = renderPreview(worldSeed, binding, found.savedCenterX(), found.savedCenterZ(),
                    binding.scale());
        } else {
            Destination requested = requireDestination(binding.destination());
            StructureReferenceSnapshot references = requested.skipExistingChunks()
                    ? snapshot(worldSeed) : NO_REFERENCE_SNAPSHOT;
            if (!MessageDigest.isEqual(HEX.parseHex(binding.referenceSnapshotReceipt()),
                    HEX.parseHex(references.receipt()))) {
                throw new IllegalStateException(
                        "located-map preview reference snapshot replay drift");
            }
            LocatedMapTarget replay = locateUncached(worldSeed, binding.sourceIdentity(),
                    binding.tableIdentity(), binding.originX(), binding.originY(), binding.originZ(),
                    requested, references);
            if (!found.equals(replay)) {
                throw new IllegalStateException("located-map preview lifecycle replay drift");
            }
            byte[] replayed = previewCache.get(key);
            if (replayed == null) {
                throw new IllegalStateException("located-map lifecycle replay omitted preview");
            }
            preview = replayed.clone();
        }
        if (!MessageDigest.isEqual(HEX.parseHex(found.previewSha256()),
                HEX.parseHex(sha256(preview)))) {
            throw new IllegalStateException("located-map preview renderer replay drift");
        }
        return preview.clone();
    }

    private byte[] renderPreview(long worldSeed, int savedCenterX, int savedCenterZ, int scale) {
        return renderPreview(null, null, worldSeed, savedCenterX, savedCenterZ, scale);
    }

    private byte[] renderPreview(Mc263StructureWorldAccess world, Binding binding,
            int savedCenterX, int savedCenterZ, int scale) {
        return renderPreview(Objects.requireNonNull(world, "locator world"),
                Objects.requireNonNull(binding, "locator binding"), world.worldSeed(),
                savedCenterX, savedCenterZ, scale);
    }

    private byte[] renderPreview(long worldSeed, Binding binding, int savedCenterX,
            int savedCenterZ, int scale) {
        return renderPreview(null, Objects.requireNonNull(binding, "located-map binding"),
                worldSeed, savedCenterX, savedCenterZ, scale);
    }

    private byte[] renderPreview(Mc263StructureWorldAccess world, Binding binding, long worldSeed,
            int savedCenterX, int savedCenterZ, int scale) {
        PreviewKey key = previewKey(worldSeed, binding, savedCenterX, savedCenterZ, scale);
        byte[] cached = previewCache.get(key);
        if (cached != null) {
            previewHits.incrementAndGet();
            return cached.clone();
        }
        previewMisses.incrementAndGet();
        byte[] rendered = world == null
                ? previewRenderer.render(worldSeed, savedCenterX, savedCenterZ, scale)
                : previewRenderer.render(world, savedCenterX, savedCenterZ, scale);
        if (rendered == null || rendered.length != PREVIEW_COLOR_COUNT) {
            throw new IllegalStateException(
                    "map preview renderer must return exactly 16,384 bytes");
        }
        return previewCache.putIfAbsent(key, rendered.clone()).clone();
    }

    private PreviewKey previewKey(long worldSeed, Binding binding, int savedCenterX,
            int savedCenterZ, int scale) {
        return new PreviewKey(worldSeed, savedCenterX, savedCenterZ, scale,
                previewRenderer.sourceReceipt(), binding == null ? ""
                        : previewRenderer.locatePreviewCacheIdentity(binding));
    }

    private static NotFound notFound(Binding binding) {
        return new NotFound(binding, targetReceipt(NOT_FOUND_DOMAIN, binding, null));
    }

    /** Reconstructs the exact pinned NotFound receipt for an authenticated binding vector. */
    public static NotFound authenticatedNotFound(Binding binding) {
        return notFound(Objects.requireNonNull(binding, "located-map NotFound binding"));
    }

    private static int savedCenter(int target, int scale) {
        int span = Math.multiplyExact(128, 1 << scale);
        long section = Math.floorDiv(Math.addExact((long) target, 64L), span);
        return Math.toIntExact(section * span + span / 2L - 64L);
    }

    private static Map.Entry<String, Destination> entry(String destination, String tag,
            String set, String... members) {
        return entry(destination, tag, set, SCALE, SEARCH_RADIUS, SKIP_EXISTING_CHUNKS, members);
    }

    private static Map.Entry<String, Destination> entry(String destination, String tag,
            String set, int scale, int searchRadius, boolean skipExistingChunks,
            String... members) {
        return Map.entry(destination, new Destination(destination, tag, set, List.of(members),
                scale, searchRadius, skipExistingChunks));
    }

    private static Destination requireDestination(String destination) {
        Destination requested = DESTINATIONS.get(destination);
        if (requested == null) {
            throw new IllegalArgumentException("unknown pinned map destination: " + destination);
        }
        return requested;
    }

    private static String sourceReceipt(String previewSourceReceipt) {
        return digest(out -> {
            out.write(LOCATOR_SOURCE_DOMAIN);
            writeString(out, Mc263StructureIndexReceipt.SOURCE_AGGREGATE_SHA256);
            writeString(out, previewSourceReceipt);
            out.writeInt(SCALE);
            out.writeInt(SEARCH_RADIUS);
            out.writeBoolean(SKIP_EXISTING_CHUNKS);
            out.write(HEX.parseHex(BURIED_TREASURE_EVIDENCE_RECEIPT));
            for (Destination destination : destinations()) {
                writeString(out, destination.destination());
                writeString(out, destination.destinationTag());
                writeString(out, destination.structureSet());
                out.writeInt(destination.acceptedMembers().size());
                for (String member : destination.acceptedMembers()) writeString(out, member);
                out.writeInt(destination.scale());
                out.writeInt(destination.searchRadius());
                out.writeBoolean(destination.skipExistingChunks());
            }
        });
    }

    private static String buriedTreasureEvidenceReceipt() {
        String expected = "c6e4d4fca59b259519ed81e65c9cdd1d3febc493ba4afa3c38d0dda7aad489f0";
        String actual = digest(out -> {
            out.write("MC263-BURIED-TREASURE-LOCATED-MAP-EVIDENCE-V1\0"
                    .getBytes(StandardCharsets.US_ASCII));
            for (String field : List.of(
                    "innerServerSha1", "2f1ef79f3cad10138ad18da45b265fe656624026",
                    "data/minecraft/tags/worldgen/structure/on_treasure_maps.json",
                    "c5134300b5af5f00a6e096ddb23782b09124a1146da264b24071041cae5c280e",
                    "data/minecraft/worldgen/structure_set/buried_treasures.json",
                    "ceebbdf4e44c459dff9aaeca1652f1c6919b28ed816179911d3267f12b3bdf96",
                    "data/minecraft/loot_table/chests/underwater_ruin_big.json",
                    "5f766b2f21fa2a76bd5a4fccdba4bf117e07e05696d316d254c36a45a262630a",
                    "data/minecraft/loot_table/chests/underwater_ruin_small.json",
                    "397ca411d551fd9fca2cf873ad7d1b65c79c1f44f73d67a6b181074fbe0ef1b6",
                    "data/minecraft/loot_table/chests/shipwreck_map.json",
                    "751f0709b69458e40dcb5bd8fcdec6872cfc5ba729a7520a98ea724e6c0d6cad",
                    "destination", "#minecraft:on_treasure_maps",
                    "member", "minecraft:buried_treasure",
                    "structureSet", "minecraft:buried_treasures",
                    "spacing", "1", "separation", "0", "salt", "0",
                    "frequency", "0.01", "frequencyReduction", "legacy_type_2",
                    "locateOffset", "9,0,9", "zoom", "1", "searchRadius", "50",
                    "skipExistingChunks", "false", "decoration", "minecraft:red_x")) {
                out.write(field.getBytes(StandardCharsets.UTF_8));
                out.writeByte(0);
            }
        });
        if (!MessageDigest.isEqual(HEX.parseHex(expected), HEX.parseHex(actual))) {
            throw new ExceptionInInitializerError("buried-treasure map evidence preimage drift");
        }
        return actual;
    }

    private static String foundReceipt(Binding binding, int targetX, int targetZ,
            int savedCenterX, int savedCenterZ, String previewSha256) {
        return targetReceipt(FOUND_DOMAIN, binding, out -> {
            out.writeInt(targetX); out.writeInt(targetZ);
            out.writeInt(savedCenterX); out.writeInt(savedCenterZ);
            out.write(HEX.parseHex(previewSha256));
        });
    }

    private static String targetReceipt(byte[] domain, Binding binding, ReceiptWriter suffix) {
        return digest(out -> {
            out.write(domain);
            writeString(out, binding.destination());
            writeString(out, binding.destinationTag());
            writeString(out, binding.structureSet());
            out.writeInt(binding.acceptedMembers().size());
            for (String member : binding.acceptedMembers()) writeString(out, member);
            writeString(out, binding.worldIdentity());
            out.write(HEX.parseHex(binding.sourceIdentity()));
            writeString(out, binding.tableIdentity());
            out.writeInt(binding.originX()); out.writeInt(binding.originY());
            out.writeInt(binding.originZ()); out.writeInt(binding.scale());
            out.writeInt(binding.searchRadius()); out.writeBoolean(binding.skipExistingChunks());
            out.write(HEX.parseHex(binding.locatorSourceReceipt()));
            out.write(HEX.parseHex(binding.referenceSnapshotReceipt()));
            if (suffix != null) suffix.write(out);
        });
    }

    private static String digest(ReceiptWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out);
            out.flush();
            return sha256(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}") || value.equals("0".repeat(64))) {
            throw new IllegalArgumentException(label + " must be a nonzero SHA-256 receipt");
        }
    }

    private static void requireMinecraftKey(String value, String label) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(label + " is not a canonical Minecraft key");
        }
    }

    @FunctionalInterface
    private interface ReceiptWriter { void write(DataOutputStream out) throws IOException; }

    /** Non-optional exact renderer seam; terrain never fabricates a preview raster. */
    public interface PreviewRenderer {
        byte[] render(long worldSeed, int savedCenterX, int savedCenterZ, int scale);

        /**
         * Renders within the world already used by a locate.  The default preserves the original
         * renderer contract for fixtures and callers that do not retain sampler state.
         */
        default byte[] render(Mc263StructureWorldAccess world, int savedCenterX,
                int savedCenterZ, int scale) {
            Objects.requireNonNull(world, "locator world");
            return render(world.worldSeed(), savedCenterX, savedCenterZ, scale);
        }

        /** Nonempty only when a locate-owned world can affect the raster. */
        default String locatePreviewCacheIdentity(Binding binding) {
            Objects.requireNonNull(binding, "located-map binding");
            return "";
        }

        String sourceReceipt();
    }

    /** Durable authority that freezes one world's reference counts behind a nonzero receipt. */
    @FunctionalInterface
    public interface StructureReferenceAuthority {
        StructureReferenceSnapshot snapshot(long worldSeed);

        static StructureReferenceAuthority unbound() {
            return ignored -> { throw new IllegalStateException(
                    "located-map skip-existing requires a durable structure-reference snapshot"); };
        }

        static StructureReferenceAuthority unreferencedFixture() {
            String receipt = sha256(
                    "MC263-LOCATED-MAP-UNREFERENCED-FIXTURE-V1".getBytes(
                            StandardCharsets.US_ASCII));
            return ignored -> new StructureReferenceSnapshot() {
                @Override public String receipt() { return receipt; }
                @Override public int references(String structureKey, int chunkX, int chunkZ) {
                    requireMinecraftKey(structureKey, "fixture structure key");
                    return 0;
                }
            };
        }
    }

    /** One immutable point-in-time reference view; its receipt is part of the locator memo key. */
    public interface StructureReferenceSnapshot {
        String receipt();
        int references(String structureKey, int chunkX, int chunkZ);
    }

    private static final class BoundedMemo<K, V> {
        private final int capacity;
        private final LinkedHashMap<K, V> values;
        private long evictions;

        private BoundedMemo(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("memo capacity must be positive");
            this.capacity = capacity;
            this.values = new LinkedHashMap<>(16, 0.75f, true);
        }

        synchronized V get(K key) { return values.get(key); }

        synchronized V putIfAbsent(K key, V value) {
            V existing = values.get(key);
            if (existing != null) return existing;
            values.put(key, value);
            if (values.size() > capacity) {
                var iterator = values.entrySet().iterator();
                iterator.next();
                iterator.remove();
                evictions++;
            }
            return value;
        }

        synchronized int size() { return values.size(); }
        synchronized long evictions() { return evictions; }
    }

    static int fullWaterWaveColor(int pixelX, int pixelZ) {
        if (pixelZ % 2 != 0) {
            return -1;
        }
        int wave = (pixelX + (int) (BiomePreviewRenderer.mthSin((double) pixelZ) * 7.0D))
                / 8 % 5;
        int brightness = switch (wave) {
            case 0, 4 -> BiomePreviewRenderer.LOW;
            case 1, 3 -> BiomePreviewRenderer.NORMAL;
            case 2 -> BiomePreviewRenderer.HIGH;
            default -> throw new AssertionError(wave);
        };
        return BiomePreviewRenderer.ORANGE << 2 | brightness & 3;
    }

    private static final class BiomePreviewRenderer implements PreviewRenderer {
        private static final int ORANGE = 15;
        private static final int BROWN = 26;
        private static final int LOW = 0;
        private static final int NORMAL = 1;
        private static final int HIGH = 2;
        private static final int LOWEST = 3;
        private static final float[] SIN = sinTable();
        private static final Set<String> WATER_ON_MAP_OUTLINES = Set.of(
                "minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean",
                "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean",
                "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
                "minecraft:river", "minecraft:frozen_river", "minecraft:swamp",
                "minecraft:mangrove_swamp");
        private static final String SOURCE_RECEIPT = officialPreviewSourceReceipt();

        @Override
        public byte[] render(long worldSeed, int centerX, int centerZ, int scale) {
            return render(Mc263StructureWorldAccess.overworld(worldSeed), centerX, centerZ, scale);
        }

        @Override
        public byte[] render(Mc263StructureWorldAccess world, int centerX, int centerZ,
                int scale) {
            if (scale != SCALE && scale != BURIED_TREASURE_SCALE) {
                throw new IllegalArgumentException("map preview scale drift");
            }
            int factor = 1 << scale;
            int baseX = centerX / factor - 64;
            int baseZ = centerZ / factor - 64;
            long zoomSeed = McBiomeZoom.zoomSeed(world.worldSeed());
            byte[] colors = new byte[PREVIEW_COLOR_COUNT];
            boolean[] watery = new boolean[PREVIEW_COLOR_COUNT];
            for (int pixelZ = 0; pixelZ < 128; pixelZ++) {
                for (int pixelX = 0; pixelX < 128; pixelX++) {
                    watery[pixelX + pixelZ * 128] = watery(world, zoomSeed,
                            Math.multiplyExact(baseX + pixelX, factor),
                            Math.multiplyExact(baseZ + pixelZ, factor));
                }
            }
            for (int pixelZ = 1; pixelZ < 127; pixelZ++) {
                for (int pixelX = 1; pixelX < 127; pixelX++) {
                    int wateryNeighbors = 0;
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                            if (deltaX == 0 && deltaZ == 0) continue;
                            if (watery[pixelX + deltaX + (pixelZ + deltaZ) * 128]) {
                                wateryNeighbors++;
                            }
                        }
                    }
                    boolean centerWatery = watery[pixelX + pixelZ * 128];
                    int color = -1;
                    int brightness = LOWEST;
                    if (centerWatery) {
                        color = ORANGE;
                        if (wateryNeighbors > 7) {
                            int waveColor = fullWaterWaveColor(pixelX, pixelZ);
                            if (waveColor >= 0) {
                                color = waveColor >> 2;
                                brightness = waveColor & 3;
                            } else {
                                color = -1;
                            }
                        } else if (wateryNeighbors > 5) {
                            brightness = NORMAL;
                        } else if (wateryNeighbors > 3) {
                            brightness = LOW;
                        } else if (wateryNeighbors > 1) {
                            brightness = LOW;
                        }
                    } else if (wateryNeighbors > 0) {
                        color = BROWN;
                        brightness = wateryNeighbors > 3 ? NORMAL : LOWEST;
                    }
                    if (color >= 0) {
                        colors[pixelX + pixelZ * 128] =
                                (byte) (color << 2 | brightness & 3);
                    }
                }
            }
            return colors;
        }

        @Override
        public String locatePreviewCacheIdentity(Binding binding) {
            Objects.requireNonNull(binding, "located-map binding");
            return binding.destination() + '\0' + binding.sourceIdentity() + '\0'
                    + binding.tableIdentity() + '\0' + binding.originX() + '\0'
                    + binding.originY() + '\0' + binding.originZ();
        }

        @Override public String sourceReceipt() { return SOURCE_RECEIPT; }

        private static String officialPreviewSourceReceipt() {
            String computed = digest(out -> {
                out.write("MC263-OFFICIAL-MAP-PREVIEW-SOURCE-V1\0"
                        .getBytes(StandardCharsets.US_ASCII));
                for (String receipt : List.of(
                        "d8e28d01a49ebd85aa992f9630fd12a1a990746c108a16baff6e95b21abdd082",
                        "e5efad859e05767b507f43cf5adb28b6c8944ef7c0b612527f3e6ebdd2c4ace1",
                        "d2d534035f023aa1338086d3ca34a5b16802669ad279e4d85e8085c06630d6d8",
                        "2b520be0dd014f8c0d95616279540de4b1ee7bcb9c26d60b22fb6c1bce0fe110",
                        "e52a688789a1d4d8a551f414e895a9b745901a1d568d2ad00ea0b0a22d364212",
                        "573adc3cfe56edf4f2fb7f0d0c11c3c184632ad2bf9d08596cd6dfdb55557e2e")) {
                    out.write(HEX.parseHex(receipt));
                }
            });
            if (!MessageDigest.isEqual(HEX.parseHex(OFFICIAL_PREVIEW_SOURCE_RECEIPT),
                    HEX.parseHex(computed))) {
                throw new ExceptionInInitializerError(
                        "official map-preview source receipt preimage drift");
            }
            return computed;
        }

        private static boolean watery(Mc263StructureWorldAccess world, long zoomSeed,
                int blockX, int blockZ) {
            int shiftedX = blockX - 2;
            int shiftedZ = blockZ - 2;
            int corner = McBiomeZoom.corner(zoomSeed, blockX, 0, blockZ);
            return WATER_ON_MAP_OUTLINES.contains(world.biomeAtQuart(
                    (shiftedX >> 2) + (corner >> 2 & 1),
                    -1 + (corner >> 1 & 1),
                    (shiftedZ >> 2) + (corner & 1)));
        }

        private static float mthSin(double angle) {
            return SIN[(int) ((long) (angle * 10430.378350470453D) & 65535L)];
        }

        private static float[] sinTable() {
            float[] table = new float[65536];
            for (int index = 0; index < table.length; index++) {
                table[index] = (float) Math.sin(index * Math.PI * 2.0D / 65536.0D);
            }
            return table;
        }
    }

    public record Destination(String destination, String destinationTag, String structureSet,
            List<String> acceptedMembers, int scale, int searchRadius,
            boolean skipExistingChunks) {
        public Destination {
            if (destination == null || !destination.matches("[a-z0-9_]+")) {
                throw new IllegalArgumentException("invalid map destination");
            }
            requireMinecraftKey(destinationTag.substring(1), "map destination tag");
            requireMinecraftKey(structureSet, "map destination structure set");
            acceptedMembers = List.copyOf(acceptedMembers);
            if (acceptedMembers.isEmpty()) throw new IllegalArgumentException("empty map tag");
            acceptedMembers.forEach(member -> requireMinecraftKey(member, "map structure member"));
            if (scale < 0 || scale > 4 || searchRadius <= 0) {
                throw new IllegalArgumentException("invalid map locator semantics");
            }
        }
    }

    private record CacheKey(long worldSeed, String sourceIdentity, String tableIdentity,
            int originX, int originY, int originZ, String destination,
            String referenceSnapshotReceipt) { }

    private record PreviewKey(long worldSeed, int savedCenterX, int savedCenterZ, int scale,
            String rendererSourceIdentity, String locateContextIdentity) { }

    public record CacheStats(int entries, long hits, long misses) { }
    public record PreviewCacheStats(int entries, long hits, long misses, long evictions) { }

    public record Binding(String destination, String destinationTag, String structureSet,
            List<String> acceptedMembers, String worldIdentity, String sourceIdentity,
            String tableIdentity, int originX, int originY, int originZ, int scale,
            int searchRadius, boolean skipExistingChunks, String locatorSourceReceipt,
            String referenceSnapshotReceipt) {
        public Binding {
            Destination expected = DESTINATIONS.get(destination);
            acceptedMembers = List.copyOf(acceptedMembers);
            if (expected == null || !expected.destinationTag().equals(destinationTag)
                    || !expected.structureSet().equals(structureSet)
                    || !expected.acceptedMembers().equals(acceptedMembers)
                    || !Long.toString(Long.parseLong(worldIdentity)).equals(worldIdentity)
                    || scale != expected.scale() || searchRadius != expected.searchRadius()
                    || skipExistingChunks != expected.skipExistingChunks()) {
                throw new IllegalArgumentException("located-map binding is not pinned 26.3");
            }
            requireSha256(sourceIdentity, "located-map source identity");
            requireMinecraftKey(tableIdentity, "located-map table identity");
            requireSha256(locatorSourceReceipt, "locator source receipt");
            requireSha256(referenceSnapshotReceipt, "reference snapshot receipt");
        }
    }

    public sealed interface LocatedMapTarget permits Found, NotFound {
        Binding binding();
        String targetReceipt();
        default String destination() { return binding().destination(); }
        default void requireAuthenticated() {
            if (this instanceof Found found) found.requireAuthenticated();
            else ((NotFound) this).requireAuthenticated();
        }
    }

    public record Found(Binding binding, int targetX, int targetZ, int savedCenterX,
            int savedCenterZ, String previewSha256, String targetReceipt)
            implements LocatedMapTarget {
        public Found {
            Objects.requireNonNull(binding, "located-map binding");
            requireSha256(previewSha256, "located-map preview receipt");
            requireSha256(targetReceipt, "located-map target receipt");
            String expected = foundReceipt(binding, targetX, targetZ, savedCenterX, savedCenterZ,
                    previewSha256);
            if (!MessageDigest.isEqual(HEX.parseHex(targetReceipt), HEX.parseHex(expected))) {
                throw new IllegalArgumentException("stale located-map Found receipt");
            }
        }
        @Override public void requireAuthenticated() {
            new Found(binding, targetX, targetZ, savedCenterX, savedCenterZ,
                    previewSha256, targetReceipt);
        }
    }

    public record NotFound(Binding binding, String targetReceipt) implements LocatedMapTarget {
        public NotFound {
            Objects.requireNonNull(binding, "located-map binding");
            requireSha256(targetReceipt, "located-map target receipt");
            String expected = Mc263LocatedMapAuthority.targetReceipt(
                    NOT_FOUND_DOMAIN, binding, null);
            if (!MessageDigest.isEqual(HEX.parseHex(targetReceipt), HEX.parseHex(expected))) {
                throw new IllegalArgumentException("stale located-map NotFound receipt");
            }
        }
        @Override public void requireAuthenticated() { new NotFound(binding, targetReceipt); }
    }
}
