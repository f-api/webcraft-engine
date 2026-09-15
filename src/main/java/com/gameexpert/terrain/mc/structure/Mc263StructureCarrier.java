package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Dormant persisted structure-start/reference substrate for Minecraft 26.3-snapshot-7.
 *
 * <p>The carrier preserves registry, start-map, piece, reference-map, and reference-set order. Its
 * typed opaque piece payload is the exact serialization boundary required by a later placement
 * executor; this class never guesses a template, processor, or piece-specific decoder.</p>
 */
public final class Mc263StructureCarrier {
    private static final byte[] RECEIPT_MAGIC =
            "STR263C1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RAW_START_SECTION_MAGIC =
            "STRRAW01".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PRODUCER_GRAPH_SECTION_MAGIC =
            "STRGRF01".getBytes(StandardCharsets.US_ASCII);
    private static final int RAW_START_BINARY_NBT_COMPOUND = 1;
    private static final int MAX_RAW_START_NBT_BYTES = 16 * 1024 * 1024;
    public static final int REFERENCE_RADIUS = 8;
    public static final int MAX_DECORATION_STEP = 10;


    private final Registry registry;
    private final List<ChunkStarts> startChunks;
    private final List<ChunkReferences> referenceChunks;
    private final List<RawStartPayload> rawStartPayloads;
    private final List<ProducerGraphPayload> producerGraphPayloads;
    /**
     * AGENTS rule 10l: point indexes over the window, built on first lookup. Every structure
     * commit publishes a carrier and almost none of them is ever asked for a chunk by
     * coordinate — the window is walked, not probed — so building the two maps eagerly indexed
     * hundreds of records per commit for nobody. Duplicate coordinates are still rejected in the
     * constructor, so a rejected carrier never exists whether or not anybody probes it.
     */
    private volatile Map<Long, ChunkStarts> startsByChunk;
    private volatile Map<Long, ChunkReferences> referencesByChunk;
    private final Map<String, RawStartPayload> rawStartPayloadsByIdentity;
    private final Map<String, ProducerGraphPayload> producerGraphPayloadsByIdentity;

    public Mc263StructureCarrier(Registry registry, List<ChunkStarts> startChunks,
            List<ChunkReferences> referenceChunks) {
        this(registry, startChunks, referenceChunks, List.of(), List.of());
    }

    public Mc263StructureCarrier(Registry registry, List<ChunkStarts> startChunks,
            List<ChunkReferences> referenceChunks, List<RawStartPayload> rawStartPayloads) {
        this(registry, startChunks, referenceChunks, rawStartPayloads, List.of());
    }

    public Mc263StructureCarrier(Registry registry, List<ChunkStarts> startChunks,
            List<ChunkReferences> referenceChunks, List<RawStartPayload> rawStartPayloads,
            List<ProducerGraphPayload> producerGraphPayloads) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.startChunks = List.copyOf(startChunks);
        this.referenceChunks = List.copyOf(referenceChunks);
        this.rawStartPayloads = List.copyOf(rawStartPayloads);
        this.producerGraphPayloads = List.copyOf(producerGraphPayloads);
        if (!this.producerGraphPayloads.isEmpty() && this.rawStartPayloads.isEmpty()) {
            throw new IllegalArgumentException("producer graph payloads require raw start section");
        }
        // AGENTS rule 10l: a structure commit republishes this window having rewritten one of its
        // records, so the window's validation is per record and memoized on the record against
        // the registry object it was validated with. Validation is a pure function of the record
        // and that registry, so a record already validated against this very registry cannot fail
        // now; a record from any other registry, or a record a commit rebuilt, is validated here.
        long[] startCoordinates = new long[this.startChunks.size()];
        for (int index = 0; index < this.startChunks.size(); index++) {
            ChunkStarts chunk = Objects.requireNonNull(this.startChunks.get(index), "start chunk");
            startCoordinates[index] = packChunk(chunk.chunkX(), chunk.chunkZ());
            chunk.validateOnce(registry);
        }
        requireDistinctCoordinates(startCoordinates, "duplicate structure-start chunk");
        long[] referenceCoordinates = new long[this.referenceChunks.size()];
        for (int index = 0; index < this.referenceChunks.size(); index++) {
            ChunkReferences chunk = Objects.requireNonNull(
                    this.referenceChunks.get(index), "reference chunk");
            referenceCoordinates[index] = packChunk(chunk.chunkX(), chunk.chunkZ());
            chunk.validateOnce(registry);
        }
        requireDistinctCoordinates(referenceCoordinates, "duplicate structure-reference chunk");
        HashMap<String, RawStartPayload> rawPayloads = new HashMap<>();
        int previousOrdinal = -1;
        for (RawStartPayload payload : this.rawStartPayloads) {
            Objects.requireNonNull(payload, "raw start payload");
            int ordinal = rawStartOrdinal(payload);
            if (ordinal < 0) {
                throw new IllegalArgumentException("raw start payload has no matching valid start");
            }
            if (ordinal <= previousOrdinal) {
                throw new IllegalArgumentException("raw start payload order/identity mismatch");
            }
            previousOrdinal = ordinal;
            if (rawPayloads.putIfAbsent(payload.identity(), payload) != null) {
                throw new IllegalArgumentException("duplicate raw start payload identity");
            }
        }
        rawStartPayloadsByIdentity = Collections.unmodifiableMap(rawPayloads);

        HashMap<String, ProducerGraphPayload> graphPayloads = new HashMap<>();
        previousOrdinal = -1;
        for (ProducerGraphPayload payload : this.producerGraphPayloads) {
            Objects.requireNonNull(payload, "producer graph payload");
            int ordinal = validateProducerGraphPayload(payload);
            if (ordinal <= previousOrdinal) {
                throw new IllegalArgumentException("producer graph payload order/identity mismatch");
            }
            previousOrdinal = ordinal;
            if (graphPayloads.putIfAbsent(payload.identity(), payload) != null) {
                throw new IllegalArgumentException("duplicate producer graph payload identity");
            }
        }
        producerGraphPayloadsByIdentity = Collections.unmodifiableMap(graphPayloads);
    }

    private Map<Long, ChunkStarts> startsByChunk() {
        Map<Long, ChunkStarts> index = startsByChunk;
        if (index == null) {
            Map<Long, ChunkStarts> built = new HashMap<>(startChunks.size() * 2);
            for (ChunkStarts chunk : startChunks) {
                built.put(packChunk(chunk.chunkX(), chunk.chunkZ()), chunk);
            }
            index = Collections.unmodifiableMap(built);
            startsByChunk = index;
        }
        return index;
    }

    private Map<Long, ChunkReferences> referencesByChunk() {
        Map<Long, ChunkReferences> index = referencesByChunk;
        if (index == null) {
            Map<Long, ChunkReferences> built = new HashMap<>(referenceChunks.size() * 2);
            for (ChunkReferences chunk : referenceChunks) {
                built.put(packChunk(chunk.chunkX(), chunk.chunkZ()), chunk);
            }
            index = Collections.unmodifiableMap(built);
            referencesByChunk = index;
        }
        return index;
    }

    /**
     * Rejects a repeated chunk coordinate. Sorting a primitive copy costs no hashing, no boxing
     * and no map, and the constructor runs on every structure commit.
     */
    private static void requireDistinctCoordinates(long[] coordinates, String message) {
        if (coordinates.length < 2) return;
        long[] sorted = coordinates.clone();
        java.util.Arrays.sort(sorted);
        for (int index = 1; index < sorted.length; index++) {
            if (sorted[index] == sorted[index - 1]) throw new IllegalArgumentException(message);
        }
    }

    public Registry registry() {
        return registry;
    }

    public List<ChunkStarts> startChunks() {
        return startChunks;
    }

    public List<ChunkReferences> referenceChunks() {
        return referenceChunks;
    }
    public List<RawStartPayload> rawStartPayloads() {
        return rawStartPayloads;
    }
    public List<ProducerGraphPayload> producerGraphPayloads() {
        return producerGraphPayloads;
    }

    public Optional<RawStartPayload> rawStartPayload(String structureId, ValidStart start) {
        Objects.requireNonNull(start, "valid start");
        registry.require(Objects.requireNonNull(structureId, "structureId"));
        return Optional.ofNullable(rawStartPayloadsByIdentity.get(rawStartIdentity(structureId,
                start.startKey(), start.originChunkX(), start.originChunkZ())));
    }

    public RawStartPayload requireRawStartPayload(String structureId, ValidStart start) {
        return rawStartPayload(structureId, start).orElseThrow(() ->
                new IllegalArgumentException("missing authenticated raw structure-start payload"));
    }

    public Optional<ProducerGraphPayload> producerGraphPayload(String structureId,
            ValidStart start) {
        Objects.requireNonNull(start, "valid start");
        registry.require(Objects.requireNonNull(structureId, "structureId"));
        return Optional.ofNullable(producerGraphPayloadsByIdentity.get(rawStartIdentity(structureId,
                start.startKey(), start.originChunkX(), start.originChunkZ())));
    }

    public ProducerGraphPayload requireProducerGraphPayload(String structureId, ValidStart start) {
        return producerGraphPayload(structureId, start).orElseThrow(() ->
                new IllegalArgumentException("missing authenticated producer graph payload"));
    }

    public Mc263StructureCarrier replaceRawStartSuccessor(String structureId, ValidStart start,
            String expectedSuccessorSha256, byte[] replacementSuccessor) {
        RawStartPayload existing = requireRawStartPayload(structureId, start);
        String expected = requireSha256(expectedSuccessorSha256, "expected successor SHA-256");
        if (!existing.successorSha256().equals(expected)) {
            throw new IllegalArgumentException("raw structure-start successor hash conflict");
        }
        RawStartPayload replacement = existing.withSuccessor(replacementSuccessor);
        ArrayList<RawStartPayload> updated = new ArrayList<>(rawStartPayloads);
        int index = updated.indexOf(existing);
        if (index < 0) throw new IllegalStateException("raw start payload index drift");
        updated.set(index, replacement);
        return new Mc263StructureCarrier(registry, startChunks, referenceChunks, updated,
                producerGraphPayloads);
    }

    private int rawStartOrdinal(RawStartPayload payload) {
        int ordinal = 0;
        for (ChunkStarts chunk : startChunks) {
            for (StartEntry entry : chunk.orderedStarts()) {
                if (entry.body() instanceof ValidStart valid) {
                    if (payload.matches(entry.structureId(), valid)) return ordinal;
                    ordinal++;
                }
            }
        }
        return -1;
    }

    private int validateProducerGraphPayload(ProducerGraphPayload payload) {
        int ordinal = 0;
        int matchedOrdinal = -1;
        ValidStart matchedStart = null;
        int matches = 0;
        for (ChunkStarts chunk : startChunks) {
            for (StartEntry entry : chunk.orderedStarts()) {
                if (!(entry.body() instanceof ValidStart valid)) continue;
                if (payload.matches(entry.structureId(), valid)) {
                    matches++;
                    matchedOrdinal = ordinal;
                    matchedStart = valid;
                }
                ordinal++;
            }
        }
        if (matches != 1 || matchedStart == null) {
            throw new IllegalArgumentException(
                    "producer graph payload must match exactly one valid start");
        }
        int pieceCount = matchedStart.orderedPieces().size();
        if (payload.orderedEdges().size() != pieceCount - 1) {
            throw new IllegalArgumentException("producer graph edge count/piece count mismatch");
        }
        for (int index = 0; index < payload.orderedEdges().size(); index++) {
            ProducerEdge edge = payload.orderedEdges().get(index);
            int expectedTarget = index + 1;
            if (edge.targetPieceOrdinal() != expectedTarget) {
                throw new IllegalArgumentException("producer graph target encounter order mismatch");
            }
            if (edge.sourcePieceOrdinal() < 0
                    || edge.sourcePieceOrdinal() >= edge.targetPieceOrdinal()
                    || edge.targetPieceOrdinal() >= pieceCount) {
                throw new IllegalArgumentException("producer graph edge ordinal is out of range");
            }
        }
        return matchedOrdinal;
    }

    /** Returns the persisted reference map for one chunk without synthesizing absent facts. */
    public Optional<ChunkReferences> referenceChunk(int chunkX, int chunkZ) {
        return Optional.ofNullable(referencesByChunk().get(packChunk(chunkX, chunkZ)));
    }

    /** Resolves references in recorded structure-group order and vanilla long-set order. */
    public List<ValidStart> resolveStarts(ChunkReferences references) {
        return resolveStartsMatching(references, null);
    }

    /**
     * Resolves only the requested structure while retaining reference-group order and vanilla
     * long-set order. An unrepresented structure fails before any resolution.
     */
    public List<ValidStart> resolveStarts(ChunkReferences references, String structureId) {
        Objects.requireNonNull(structureId, "structureId");
        registry.require(structureId);
        return resolveStartsMatching(references, structureId);
    }

    /**
     * {@code it.unimi.dsi.fastutil.HashCommon.LONG_PHI}, read from the pinned server jar's
     * {@code META-INF/libraries/it/unimi/dsi/fastutil/8.5.18/fastutil-8.5.18.jar}
     * ({@code HashCommon.mix(long)} loads it as the constant {@code -7046029254386353131L}).
     */
    private static final long FASTUTIL_LONG_PHI = 0x9E3779B97F4A7C15L;
    /** {@code it.unimi.dsi.fastutil.Hash.DEFAULT_INITIAL_SIZE} in that same jar. */
    private static final int FASTUTIL_DEFAULT_INITIAL_SIZE = 16;
    /** {@code it.unimi.dsi.fastutil.Hash.DEFAULT_LOAD_FACTOR} in that same jar. */
    private static final float FASTUTIL_DEFAULT_LOAD_FACTOR = 0.75f;
    /**
     * {@code LongOpenHashSet.n} for the set vanilla actually builds. AGENTS rule 10m: the table
     * size is derived from the two fastutil constants it indexes through fastutil's own
     * {@code arraySize}, never written down beside them as a literal.
     */
    private static final int REFERENCE_SET_TABLE_SIZE =
            fastutilArraySize(FASTUTIL_DEFAULT_INITIAL_SIZE, FASTUTIL_DEFAULT_LOAD_FACTOR);
    /** {@code LongOpenHashSet.mask}. */
    private static final int REFERENCE_SET_MASK = REFERENCE_SET_TABLE_SIZE - 1;
    /** {@code LongOpenHashSet.maxFill}: the last size that still fits without a rehash. */
    private static final int REFERENCE_SET_MAX_FILL =
            fastutilMaxFill(REFERENCE_SET_TABLE_SIZE, FASTUTIL_DEFAULT_LOAD_FACTOR);

    /** Mirrors {@code HashCommon#nextPowerOfTwo(long)}. */
    private static long fastutilNextPowerOfTwo(long value) {
        return 1L << (64 - Long.numberOfLeadingZeros(value - 1L));
    }

    /** Mirrors {@code HashCommon#arraySize(int, float)}, including its own overflow rejection. */
    private static int fastutilArraySize(int expected, float loadFactor) {
        long size = Math.max(2L,
                fastutilNextPowerOfTwo((long) Math.ceil((double) expected / (double) loadFactor)));
        if (size > (1L << 30)) {
            throw new IllegalArgumentException("Too large (" + expected
                    + " expected elements with load factor " + loadFactor + ")");
        }
        return (int) size;
    }

    /** Mirrors {@code HashCommon#maxFill(int, float)}. */
    private static int fastutilMaxFill(int tableSize, float loadFactor) {
        return Math.min((int) Math.ceil((double) tableSize * (double) loadFactor), tableSize - 1);
    }

    /** Mirrors {@code HashCommon#mix(long)}. */
    private static long fastutilMix(long key) {
        long mixed = key * FASTUTIL_LONG_PHI;
        mixed ^= mixed >>> 32;
        return mixed ^ (mixed >>> 16);
    }

    /**
     * Reorders one reference group's origins into the order vanilla actually places them.
     *
     * <p>{@code ChunkGenerator#createReferences} visits source chunks in scan order (X outer, Z
     * inner) and this carrier persists exactly that encounter order, but vanilla never replays it.
     * {@code ChunkAccess#addReferenceForStructure} funnels every origin into a
     * {@code it.unimi.dsi.fastutil.longs.LongOpenHashSet} built by
     * {@code lambda$addReferenceForStructure$0} through the no-argument constructor, and
     * {@code StructureManager#fillStartsForStructure} drains that set through
     * {@code LongSet#iterator()} straight into the placement consumer. Placement order is
     * therefore the set's bucket order: a pure function of the packed origins, independent of the
     * order they were inserted in.</p>
     *
     * <p>Authenticated against fastutil 8.5.18 as shipped inside the pinned server jar.
     * {@code LongOpenHashSet(int, float)} sets {@code n = HashCommon.arraySize(16, 0.75f)},
     * {@code mask = n - 1} and {@code maxFill = HashCommon.maxFill(n, 0.75f)};
     * {@code add(long)} keeps the zero key in the {@code containsNull} flag and otherwise stores a
     * key at {@code (int) HashCommon.mix(k) & mask} with {@code (pos + 1) & mask} linear probing;
     * {@code SetIterator} starts at {@code pos = n}, returns the zero key first and then scans
     * buckets {@code n - 1 .. 0}.</p>
     *
     * <p>Fail-closed: {@code add} rehashes as soon as the pre-increment size reaches
     * {@code maxFill}, so a group larger than {@code maxFill} would be placed out of a table this
     * method does not model. Such a group is rejected instead of being placed in a guessed order.
     * Duplicate origins cannot occur — {@link ReferenceSet} already rejects them.</p>
     */
    private static List<Long> vanillaPlacementOrder(List<Long> insertionOrder) {
        if (insertionOrder.size() < 2) return insertionOrder;
        if (insertionOrder.size() > REFERENCE_SET_MAX_FILL) {
            throw new IllegalArgumentException("reference group of " + insertionOrder.size()
                    + " origins exceeds the authenticated LongOpenHashSet fill of "
                    + REFERENCE_SET_MAX_FILL + "; vanilla rehashes this set and the carrier does"
                    + " not model the rehashed placement order");
        }
        long[] table = new long[REFERENCE_SET_TABLE_SIZE];
        boolean containsNull = false;
        for (long origin : insertionOrder) {
            if (origin == 0L) {
                containsNull = true;
                continue;
            }
            int position = (int) fastutilMix(origin) & REFERENCE_SET_MASK;
            while (table[position] != 0L && table[position] != origin) {
                position = (position + 1) & REFERENCE_SET_MASK;
            }
            table[position] = origin;
        }
        ArrayList<Long> ordered = new ArrayList<>(insertionOrder.size());
        if (containsNull) ordered.add(0L);
        for (int position = REFERENCE_SET_TABLE_SIZE - 1; position >= 0; position--) {
            if (table[position] != 0L) ordered.add(table[position]);
        }
        return List.copyOf(ordered);
    }

    private List<ValidStart> resolveStartsMatching(
            ChunkReferences references, String structureId) {
        Objects.requireNonNull(references, "references");
        references.validate(registry);
        ArrayList<ValidStart> resolved = new ArrayList<>();
        for (ReferenceSet group : references.orderedSets()) {
            if (structureId != null && !structureId.equals(group.structureId())) continue;
            for (long origin : vanillaPlacementOrder(group.orderedOrigins())) {
                ChunkStarts chunk = startsByChunk().get(origin);
                if (chunk == null) continue;
                StartEntry entry = chunk.start(group.structureId());
                if (entry != null && entry.body() instanceof ValidStart valid) {
                    resolved.add(valid);
                }
            }
        }
        return List.copyOf(resolved);
    }

    /** Decodes the pinned structure registry at the cursor. */
    private static Registry decodeRegistry(ReceiptCursor input) {
        int definitionCount = input.count("structure registry", 16);
        ArrayList<StructureDefinition> definitions = new ArrayList<>(definitionCount);
        for (int index = 0; index < definitionCount; index++) {
            definitions.add(new StructureDefinition(input.string(), input.signedInt(),
                    input.signedInt(), terrainAdjustment(input.unsignedByte())));
        }
        return new Registry(definitions);
    }

    /**
     * Decodes exactly one structure-start chunk record at the cursor.
     *
     * <p>The whole-receipt decoder and {@link ChunkStarts#canonicalized()} share this one parser,
     * so a record proven through its own fragment is proven by the same code, in the same order,
     * with the same rejections as a record decoded inside a complete receipt.</p>
     */
    private static ChunkStarts decodeStartChunk(ReceiptCursor input) {
        int chunkX = input.signedInt();
        int chunkZ = input.signedInt();
        int startCount = input.count("structure starts", 8);
        ArrayList<StartEntry> starts = new ArrayList<>(startCount);
        for (int startIndex = 0; startIndex < startCount; startIndex++) {
            String structureId = input.string();
            boolean valid = input.bool();
            if (!valid) {
                starts.add(new StartEntry(structureId, new InvalidStart()));
                continue;
            }
            String startKey = input.string();
            int originChunkX = input.signedInt();
            int originChunkZ = input.signedInt();
            int references = input.signedInt();
            BoundingBox adjusted = input.boundingBox();
            int pieceCount = input.count("structure pieces", 46);
            ArrayList<Piece> pieces = new ArrayList<>(pieceCount);
            for (int pieceIndex = 0; pieceIndex < pieceCount; pieceIndex++) {
                String pieceType = input.string();
                BoundingBox boundingBox = input.boundingBox();
                boolean poolElement = input.bool();
                Projection projection = projection(input.unsignedByte());
                int groundLevelDelta = input.signedInt();
                int junctionCount = input.count("jigsaw junctions", 17);
                ArrayList<Junction> junctions = new ArrayList<>(junctionCount);
                for (int junctionIndex = 0; junctionIndex < junctionCount; junctionIndex++) {
                    junctions.add(new Junction(input.signedInt(), input.signedInt(),
                            input.signedInt(), input.signedInt(),
                            projection(input.unsignedByte())));
                }
                if (input.unsignedByte() != PiecePayload.BINARY_NBT_COMPOUND) {
                    throw new IllegalArgumentException("unknown structure piece payload type");
                }
                PiecePayload payload = new PiecePayload(input.byteArray("piece payload"));
                pieces.add(new Piece(pieceType, boundingBox, poolElement, projection,
                        groundLevelDelta, junctions, payload));
            }
            starts.add(new StartEntry(structureId, new ValidStart(startKey, originChunkX,
                    originChunkZ, references, adjusted, pieces)));
        }
        return new ChunkStarts(chunkX, chunkZ, starts);
    }

    /** Decodes exactly one structure-reference chunk record at the cursor. */
    private static ChunkReferences decodeReferenceChunk(ReceiptCursor input) {
        int chunkX = input.signedInt();
        int chunkZ = input.signedInt();
        int setCount = input.count("structure reference sets", 19);
        ArrayList<ReferenceSet> sets = new ArrayList<>(setCount);
        for (int setIndex = 0; setIndex < setCount; setIndex++) {
            String structureId = input.string();
            int originCount = input.count("structure reference origins", 8);
            ArrayList<Long> origins = new ArrayList<>(originCount);
            for (int originIndex = 0; originIndex < originCount; originIndex++) {
                origins.add(input.signedLong());
            }
            sets.add(new ReferenceSet(structureId, origins));
        }
        return new ChunkReferences(chunkX, chunkZ, sets);
    }

    /** Strictly decodes and validates a canonical STR263C1 receipt. */
    public static Mc263StructureCarrier decode(byte[] receipt) {
        Objects.requireNonNull(receipt, "receipt");
        ReceiptCursor input = new ReceiptCursor(receipt);
        input.requireMagic();

        int registryBegin = input.offset;
        Registry registry = decodeRegistry(input);
        requireCanonicalSlice(receipt, registryBegin, input.offset, registry.fragment());
        registry.markCanonical();

        int startChunkCount = input.count("structure-start chunks", 12);
        ArrayList<ChunkStarts> startChunks = new ArrayList<>(startChunkCount);
        for (int chunkIndex = 0; chunkIndex < startChunkCount; chunkIndex++) {
            int begin = input.offset;
            ChunkStarts chunk = decodeStartChunk(input);
            requireCanonicalSlice(receipt, begin, input.offset, chunk.fragment());
            chunk.markCanonical();
            startChunks.add(chunk);
        }

        int referenceChunkCount = input.count("structure-reference chunks", 12);
        ArrayList<ChunkReferences> referenceChunks = new ArrayList<>(referenceChunkCount);
        for (int chunkIndex = 0; chunkIndex < referenceChunkCount; chunkIndex++) {
            int begin = input.offset;
            ChunkReferences chunk = decodeReferenceChunk(input);
            requireCanonicalSlice(receipt, begin, input.offset, chunk.fragment());
            chunk.markCanonical();
            referenceChunks.add(chunk);
        }
        ArrayList<RawStartPayload> rawStartPayloads = new ArrayList<>();
        ArrayList<ProducerGraphPayload> producerGraphPayloads = new ArrayList<>();
        if (input.remaining() != 0) {
            input.requireBytes(RAW_START_SECTION_MAGIC, "raw start section magic");
            int payloadCount = input.count("raw structure-start payloads", 18);
            if (payloadCount == 0) {
                throw new IllegalArgumentException("noncanonical empty raw start section");
            }
            for (int index = 0; index < payloadCount; index++) {
                String structureId = input.string();
                String startKey = input.string();
                int originChunkX = input.signedInt();
                int originChunkZ = input.signedInt();
                if (input.unsignedByte() != RAW_START_BINARY_NBT_COMPOUND) {
                    throw new IllegalArgumentException("unknown raw start predecessor payload type");
                }
                byte[] predecessor = input.byteArray("raw start predecessor");
                if (input.unsignedByte() != RAW_START_BINARY_NBT_COMPOUND) {
                    throw new IllegalArgumentException("unknown raw start successor payload type");
                }
                byte[] successor = input.byteArray("raw start successor");
                RawStartPayload payload = new RawStartPayload(structureId, startKey, originChunkX,
                        originChunkZ, predecessor, successor);
                payload.markCanonical();
                rawStartPayloads.add(payload);
            }
            if (input.remaining() != 0) {
                input.requireBytes(PRODUCER_GRAPH_SECTION_MAGIC, "producer graph section magic");
                int graphCount = input.count("producer graph payloads", 20);
                if (graphCount == 0) {
                    throw new IllegalArgumentException("noncanonical empty producer graph section");
                }
                for (int index = 0; index < graphCount; index++) {
                    String structureId = input.string();
                    String startKey = input.string();
                    int originChunkX = input.signedInt();
                    int originChunkZ = input.signedInt();
                    int edgeCount = input.count("producer graph edges", 16);
                    ArrayList<ProducerEdge> edges = new ArrayList<>(edgeCount);
                    for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                        edges.add(new ProducerEdge(input.signedInt(), input.signedInt(),
                                input.string(), input.string()));
                    }
                    ProducerGraphPayload graph = new ProducerGraphPayload(structureId, startKey,
                            originChunkX, originChunkZ, edges);
                    graph.markCanonical();
                    producerGraphPayloads.add(graph);
                }
            }
        }
        input.requireEnd();

        Mc263StructureCarrier decoded = new Mc263StructureCarrier(
                registry, startChunks, referenceChunks, rawStartPayloads, producerGraphPayloads);
        if (!java.util.Arrays.equals(receipt, decoded.receiptView())) {
            throw new IllegalArgumentException("noncanonical structure receipt encoding");
        }
        return decoded;
    }

    /** Produces the exact existing Beardifier input without exposing placement payloads. */
    public Mc263Beardifier.ChunkCarrier beardifierCarrier(ChunkReferences references) {
        ArrayList<Mc263Beardifier.ReferenceGroup> groups = new ArrayList<>();
        for (ReferenceSet group : references.orderedSets()) {
            ArrayList<Mc263Beardifier.StructureStart> starts = new ArrayList<>();
            for (long origin : group.orderedOrigins()) {
                ChunkStarts chunk = startsByChunk().get(origin);
                if (chunk == null) continue;
                StartEntry entry = chunk.start(group.structureId());
                if (entry == null || !(entry.body() instanceof ValidStart valid)) continue;
                StructureDefinition definition = registry.require(group.structureId());
                ArrayList<Mc263Beardifier.Piece> pieces = new ArrayList<>();
                for (Piece piece : valid.orderedPieces()) {
                    ArrayList<Mc263Beardifier.Junction> junctions = new ArrayList<>();
                    for (Junction junction : piece.junctions()) {
                        junctions.add(new Mc263Beardifier.Junction(
                                junction.sourceX(), junction.sourceGroundY(), junction.sourceZ(),
                                junction.deltaY(), switch (junction.destinationProjection()) {
                                    case RIGID -> Mc263Beardifier.Projection.RIGID;
                                    case TERRAIN_MATCHING ->
                                            Mc263Beardifier.Projection.TERRAIN_MATCHING;
                                    case NOT_APPLICABLE -> throw new IllegalStateException(
                                            "junction projection is absent");
                                }));
                    }
                    pieces.add(new Mc263Beardifier.Piece(piece.pieceType(), piece.boundingBox().beard(),
                            piece.poolElement(), switch (piece.projection()) {
                                case RIGID -> Mc263Beardifier.Projection.RIGID;
                                case TERRAIN_MATCHING -> Mc263Beardifier.Projection.TERRAIN_MATCHING;
                                case NOT_APPLICABLE -> Mc263Beardifier.Projection.NOT_APPLICABLE;
                            }, piece.groundLevelDelta(), junctions));
                }
                starts.add(new Mc263Beardifier.StructureStart(
                        valid.startKey(), group.structureId(), valid.originChunkX(),
                        valid.originChunkZ(), true, definition.terrainAdjustment().beard(),
                        valid.adjustedBoundingBox().beard(), pieces));
            }
            if (!starts.isEmpty()) {
                groups.add(new Mc263Beardifier.ReferenceGroup(group.structureId(), starts));
            }
        }
        return new Mc263Beardifier.ChunkCarrier(
                references.chunkX(), references.chunkZ(), groups);
    }

    /** SHA-256 of the complete ordered, typed persisted substrate. */
    public String receiptSha256() {
        return sha256(receiptView());
    }

    /**
     * Lazily encoded persisted substrate.
     *
     * <p>AGENTS rule 10l: a carrier is deeply immutable — every list and map is copied on
     * construction and every structure commit publishes a new instance — so its receipt is a
     * constant of the instance, but the FEATURES seam asks for it several times per structure
     * commit (settlement predecessor check, registry/reference comparison, strict re-decode) and
     * once more for the committed product. Encoding walks every start, piece and payload, so the
     * repeats dominated the commit path. The encoded bytes are computed once per instance and
     * handed out as a copy, which keeps the mutable-array contract callers already rely on.</p>
     */
    private volatile byte[] receiptBytes;

    public byte[] receiptBytes() {
        return receiptView().clone();
    }

    /**
     * The cached receipt without the defensive copy, for read-only use inside this authority.
     *
     * <p>AGENTS rule 10l: {@link #receiptBytes()} must keep handing callers their own array, but
     * the seam's own consumers — the digest, the decode round trip and the predecessor comparison
     * — only read it, and a carrier's receipt is tens of kilobytes copied several times per
     * structure commit. Reading the shared array produces the identical bytes.</p>
     */
    private byte[] receiptView() {
        byte[] encoded = receiptBytes;
        if (encoded == null) {
            encoded = encodeReceipt();
            receiptBytes = encoded;
        }
        return encoded;
    }

    /** Whether this carrier and {@code other} encode to the same receipt. */
    public boolean hasSameReceipt(Mc263StructureCarrier other) {
        Objects.requireNonNull(other, "receipt comparison peer");
        return this == other
                || MessageDigest.isEqual(receiptView(), other.receiptView());
    }

    /**
     * This carrier decoded from its own canonical receipt, forcing strict canonical encoding.
     *
     * <p>AGENTS rule 10l: the receipt is the concatenation of its records' own encodings and the
     * strict decoder is a per-record parser over exactly those fragments, so decoding the whole
     * receipt is decoding every record — and a record the decoder already produced, and that no
     * commit has replaced since, decodes to itself. A structure commit republishes a window of
     * hundreds of records having rewritten one of them, so this walks the window, round-trips
     * only the records that are not already proven, and reuses the rest. The published bytes are
     * unchanged: every record's fragment, proven or freshly proven, is the same fragment the
     * whole-receipt round trip would have produced, and the same canonical-encoding law is
     * asserted on each of them. Raw start and producer graph payloads carry the same flag, so a
     * village- or mansion-carrying carrier now takes this path too instead of falling back.</p>
     */
    public Mc263StructureCarrier strictlyDecoded() {
        Registry provenRegistry = registry.canonicalized();
        for (RawStartPayload payload : rawStartPayloads) {
            if (!payload.isCanonical()) return decode(receiptView());
        }
        for (ProducerGraphPayload payload : producerGraphPayloads) {
            if (!payload.isCanonical()) return decode(receiptView());
        }

        boolean rebuilt = provenRegistry != registry;
        ArrayList<ChunkStarts> starts = new ArrayList<>(startChunks.size());
        for (ChunkStarts chunk : startChunks) {
            ChunkStarts proven = chunk.canonicalized();
            rebuilt |= proven != chunk;
            starts.add(proven);
        }
        ArrayList<ChunkReferences> references = new ArrayList<>(referenceChunks.size());
        for (ChunkReferences chunk : referenceChunks) {
            ChunkReferences proven = chunk.canonicalized();
            rebuilt |= proven != chunk;
            references.add(proven);
        }
        if (!rebuilt) return this;
        return new Mc263StructureCarrier(provenRegistry, starts, references, rawStartPayloads,
                producerGraphPayloads);
    }

    /**
     * Begins an in-memory STR successor transaction for one existing product boundary.
     *
     * <p>The three identity arguments are caller-owned canonical identities. This class does not
     * manufacture an origin, provenance, or product authority and never treats equal values as
     * interchangeable: the issued capability retains each exact object identity. The transaction
     * is deliberately separate from {@link #receiptBytes()}; STR263C1 remains append-only and the
     * capability is not a persisted wire field.</p>
     */
    public static SuccessorTransaction beginSuccessorTransaction(
            Mc263StructureCarrier predecessor, Object productTransaction,
            Object originIdentity, Object provenanceIdentity) {
        return new SuccessorTransaction(predecessor, productTransaction, originIdentity,
                provenanceIdentity);
    }

    /**
     * Producer-owned, ordered STR successor capability issuer.
     *
     * <p>Each issued {@link Successor} is constructed only here. It is bound to the transaction's
     * current predecessor object, product transaction object, origin/provenance objects, ordered
     * mutation snapshot, and exact resulting carrier object/digest. A committed capability is
     * removed from the identity map, and {@link #close()} expires every remaining capability.</p>
     */
    public static final class SuccessorTransaction implements AutoCloseable {
        private final Mc263StructureCarrier initialPredecessor;
        private Mc263StructureCarrier currentPredecessor;
        private final Object productTransaction;
        private final Object originIdentity;
        private final Object provenanceIdentity;
        private final String originIdentityDigest;
        private final String provenanceIdentityDigest;
        private final Object ownerToken = new Object();
        private final IdentityHashMap<Successor, SuccessorCapability> issued =
                new IdentityHashMap<>();
        private long nextSequence;
        private boolean closed;

        private SuccessorTransaction(Mc263StructureCarrier predecessor, Object productTransaction,
                Object originIdentity, Object provenanceIdentity) {
            initialPredecessor = Objects.requireNonNull(predecessor,
                    "STR successor predecessor");
            currentPredecessor = predecessor;
            this.productTransaction = Objects.requireNonNull(productTransaction,
                    "STR successor product transaction");
            this.originIdentity = Objects.requireNonNull(originIdentity,
                    "STR successor origin identity");
            this.provenanceIdentity = Objects.requireNonNull(provenanceIdentity,
                    "STR successor provenance identity");
            originIdentityDigest = identityDigest(originIdentity);
            provenanceIdentityDigest = identityDigest(provenanceIdentity);
        }

        /** Equivalent factory retained beside the outer carrier factory for fluent callers. */
        public static SuccessorTransaction begin(Mc263StructureCarrier predecessor,
                Object productTransaction, Object originIdentity, Object provenanceIdentity) {
            return Mc263StructureCarrier.beginSuccessorTransaction(predecessor, productTransaction,
                    originIdentity, provenanceIdentity);
        }

        /** The exact carrier object supplied when this transaction was opened. */
        public Mc263StructureCarrier predecessor() { return initialPredecessor; }

        /** The exact carrier object from which the next successor must be issued. */
        public synchronized Mc263StructureCarrier currentPredecessor() {
            requireOpen();
            return currentPredecessor;
        }

        public synchronized boolean isOpen() { return !closed; }

        /**
         * Issues the next successor in this transaction's ordered chain.
         *
         * <p>The explicit predecessor parameter is intentional: a decoded carrier with identical
         * STR bytes is not the predecessor object and is rejected before a capability exists.</p>
         */
        public synchronized Successor issueSuccessor(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier result, List<?> orderedMutations) {
            requireOpen();
            Objects.requireNonNull(predecessor, "STR successor predecessor");
            Objects.requireNonNull(result, "STR successor result carrier");
            if (predecessor != currentPredecessor) {
                throw new IllegalArgumentException(
                        "STR successor predecessor object is stale or foreign");
            }
            if (!issued.isEmpty()) {
                throw new IllegalStateException(
                        "STR successor transaction already has an uncommitted successor");
            }
            List<?> frozenMutations = copyOrderedMutations(orderedMutations);
            String predecessorDigest = predecessor.receiptSha256();
            String resultDigest = result.receiptSha256();
            String mutationsDigest = orderedMutationsDigest(frozenMutations);
            long sequence;
            try {
                sequence = Math.addExact(nextSequence, 1L);
            } catch (ArithmeticException overflow) {
                throw new IllegalStateException("STR successor transaction sequence exhausted",
                        overflow);
            }
            String receiptDigest = successorReceiptDigest(sequence, predecessorDigest, resultDigest,
                    mutationsDigest, originIdentityDigest, provenanceIdentityDigest);
            SuccessorReceipt receipt = new SuccessorReceipt(ownerToken, productTransaction,
                    originIdentity, provenanceIdentity, predecessor, result, frozenMutations,
                    sequence, predecessorDigest, resultDigest, mutationsDigest,
                    originIdentityDigest, provenanceIdentityDigest, receiptDigest);
            SuccessorCapability capability = new SuccessorCapability(this, ownerToken, receipt,
                    predecessor, result, frozenMutations, predecessorDigest, resultDigest,
                    mutationsDigest);
            Successor successor = new Successor(capability);
            issued.put(successor, capability);
            nextSequence = sequence;
            return successor;
        }

        /** Issues a successor using the current predecessor object. */
        public synchronized Successor issueSuccessor(Mc263StructureCarrier result,
                List<?> orderedMutations) {
            return issueSuccessor(currentPredecessor, result, orderedMutations);
        }

        /** Same operation with mutation order before the resulting carrier for call-site parity. */
        public synchronized Successor issueSuccessor(Mc263StructureCarrier predecessor,
                List<?> orderedMutations, Mc263StructureCarrier result) {
            return issueSuccessor(predecessor, result, orderedMutations);
        }

        /** Commits the exact issued capability with the values already carried by that capability. */
        public synchronized Mc263StructureCarrier commit(Successor successor) {
            Objects.requireNonNull(successor, "STR successor capability");
            return commit(successor, productTransaction, successor.orderedMutations(),
                    successor.resultingCarrier());
        }

        /**
         * Commits after checking caller-supplied product, mutations, and result claims.
         *
         * <p>This overload is the tamper boundary used by producers that forward a capability
         * through another typed handoff. All checks are identity checks for authority objects and
         * value/digest checks for the immutable ordered mutation/result facts.</p>
         */
        public synchronized Mc263StructureCarrier commit(Successor successor,
                Object expectedProductTransaction, List<?> expectedOrderedMutations,
                Mc263StructureCarrier expectedResult) {
            requireOpen();
            Objects.requireNonNull(successor, "STR successor capability");
            Objects.requireNonNull(expectedProductTransaction,
                    "STR successor product transaction");
            Objects.requireNonNull(expectedResult, "STR successor result carrier");
            SuccessorCapability capability = issued.get(successor);
            if (capability == null || successor.capability != capability
                    || capability.owner != this || capability.ownerToken != ownerToken
                    || capability.expired || capability.committed
                    || capability.receipt.ownerToken != ownerToken
                    || capability.receipt.productTransaction != productTransaction
                    || capability.receipt.originIdentity != originIdentity
                    || capability.receipt.provenanceIdentity != provenanceIdentity
                    || capability.receipt.predecessor != capability.predecessor
                    || capability.receipt.result != capability.result
                    || capability.receipt.orderedMutations != capability.orderedMutations
                    || !capability.receipt.predecessorDigest.equals(capability.predecessorDigest)
                    || !capability.receipt.resultDigest.equals(capability.resultDigest)
                    || !capability.receipt.mutationsDigest.equals(capability.mutationsDigest)
                    || !capability.receipt.receiptDigest.equals(successorReceiptDigest(
                            capability.receipt.sequence, capability.predecessorDigest,
                            capability.resultDigest, capability.mutationsDigest,
                            capability.receipt.originDigest, capability.receipt.provenanceDigest))) {
                throw new IllegalArgumentException(
                        "STR successor capability is foreign, forged, stale, or replayed");
            }
            if (capability.predecessor != currentPredecessor) {
                throw new IllegalArgumentException("STR successor predecessor object is stale");
            }
            if (expectedProductTransaction != productTransaction) {
                throw new IllegalArgumentException("STR successor product transaction mismatch");
            }
            if (expectedResult != capability.result
                    || !capability.resultDigest.equals(expectedResult.receiptSha256())) {
                throw new IllegalArgumentException("STR successor result carrier mismatch");
            }
            List<?> suppliedMutations = copyOrderedMutations(expectedOrderedMutations);
            if (!sameOrderedMutations(capability.orderedMutations, suppliedMutations)
                    || !capability.mutationsDigest.equals(orderedMutationsDigest(suppliedMutations))) {
                throw new IllegalArgumentException("STR successor ordered mutation mismatch");
            }
            if (!originIdentityDigest.equals(identityDigest(originIdentity))
                    || !provenanceIdentityDigest.equals(identityDigest(provenanceIdentity))) {
                throw new IllegalArgumentException("STR successor origin/provenance identity drift");
            }
            capability.committed = true;
            capability.expired = true;
            issued.remove(successor);
            currentPredecessor = capability.result;
            return capability.result;
        }

        /** Expires all uncommitted capabilities and prevents any further issue or commit. */
        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            for (SuccessorCapability capability : issued.values()) capability.expired = true;
            issued.clear();
        }

        private void requireOpen() {
            if (closed) throw new IllegalStateException("STR successor transaction is closed");
        }
    }

    /** An issued successor envelope; no public constructor can create one without its capability. */
    public static final class Successor {
        private final SuccessorCapability capability;

        private Successor(SuccessorCapability capability) {
            this.capability = Objects.requireNonNull(capability, "STR successor capability");
        }

        public Mc263StructureCarrier predecessor() { return capability.predecessor; }
        public Mc263StructureCarrier resultingCarrier() { return capability.result; }
        public Mc263StructureCarrier successor() { return capability.result; }
        public Mc263StructureCarrier carrier() { return capability.result; }
        public List<?> orderedMutations() { return copyOrderedMutations(capability.orderedMutations); }
        public SuccessorReceipt receipt() { return capability.receipt; }
        public SuccessorReceipt ownerReceipt() { return capability.receipt; }
        public boolean isExpired() { return capability.expired; }
        public boolean isCommitted() { return capability.committed; }
    }

    /**
     * Read-only receipt view for one issued successor. Its constructor and identity-bearing fields
     * remain private; a caller can inspect canonical digests but cannot mint an accepted receipt.
     */
    public static final class SuccessorReceipt {
        private final Object ownerToken;
        private final Object productTransaction;
        private final Object originIdentity;
        private final Object provenanceIdentity;
        private final Mc263StructureCarrier predecessor;
        private final Mc263StructureCarrier result;
        private final List<?> orderedMutations;
        private final long sequence;
        private final String predecessorDigest;
        private final String resultDigest;
        private final String mutationsDigest;
        private final String originDigest;
        private final String provenanceDigest;
        private final String receiptDigest;

        private SuccessorReceipt(Object ownerToken, Object productTransaction, Object originIdentity,
                Object provenanceIdentity, Mc263StructureCarrier predecessor,
                Mc263StructureCarrier result, List<?> orderedMutations, long sequence,
                String predecessorDigest, String resultDigest, String mutationsDigest,
                String originDigest, String provenanceDigest, String receiptDigest) {
            this.ownerToken = ownerToken;
            this.productTransaction = productTransaction;
            this.originIdentity = originIdentity;
            this.provenanceIdentity = provenanceIdentity;
            this.predecessor = predecessor;
            this.result = result;
            this.orderedMutations = orderedMutations;
            this.sequence = sequence;
            this.predecessorDigest = predecessorDigest;
            this.resultDigest = resultDigest;
            this.mutationsDigest = mutationsDigest;
            this.originDigest = originDigest;
            this.provenanceDigest = provenanceDigest;
            this.receiptDigest = receiptDigest;
        }

        public long sequence() { return sequence; }
        public String predecessorCarrierSha256() { return predecessorDigest; }
        public String resultingCarrierSha256() { return resultDigest; }
        public String orderedMutationsSha256() { return mutationsDigest; }
        public String originIdentitySha256() { return originDigest; }
        public String provenanceIdentitySha256() { return provenanceDigest; }
        public String receiptSha256() { return receiptDigest; }
    }

    private static final class SuccessorCapability {
        private final SuccessorTransaction owner;
        private final Object ownerToken;
        private final SuccessorReceipt receipt;
        private final Mc263StructureCarrier predecessor;
        private final Mc263StructureCarrier result;
        private final List<?> orderedMutations;
        private final String predecessorDigest;
        private final String resultDigest;
        private final String mutationsDigest;
        private volatile boolean committed;
        private volatile boolean expired;

        private SuccessorCapability(SuccessorTransaction owner, Object ownerToken,
                SuccessorReceipt receipt, Mc263StructureCarrier predecessor,
                Mc263StructureCarrier result, List<?> orderedMutations, String predecessorDigest,
                String resultDigest, String mutationsDigest) {
            this.owner = owner;
            this.ownerToken = ownerToken;
            this.receipt = receipt;
            this.predecessor = predecessor;
            this.result = result;
            this.orderedMutations = orderedMutations;
            this.predecessorDigest = predecessorDigest;
            this.resultDigest = resultDigest;
            this.mutationsDigest = mutationsDigest;
        }
    }

    private static List<?> copyOrderedMutations(List<?> orderedMutations) {
        List<?> source = List.copyOf(Objects.requireNonNull(orderedMutations,
                "STR successor ordered mutations"));
        ArrayList<Object> copy = new ArrayList<>(source.size());
        for (Object mutation : source) {
            copy.add(mutation instanceof byte[] bytes ? bytes.clone() : mutation);
        }
        return List.copyOf(copy);
    }

    private static boolean sameOrderedMutations(List<?> left, List<?> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            Object leftValue = left.get(index);
            Object rightValue = right.get(index);
            if (leftValue instanceof byte[] leftBytes && rightValue instanceof byte[] rightBytes) {
                if (!java.util.Arrays.equals(leftBytes, rightBytes)) return false;
            } else if (!Objects.equals(leftValue, rightValue)) {
                return false;
            }
        }
        return true;
    }

    private static String orderedMutationsDigest(List<?> orderedMutations) {
        byte[] encoded = encodeFragment(output -> {
            output.write(RECEIPT_MAGIC);
            output.writeInt(orderedMutations.size());
            for (Object mutation : orderedMutations) {
                writeString(output, mutation.getClass().getName());
                if (mutation instanceof byte[] bytes) {
                    output.writeInt(bytes.length);
                    output.write(bytes);
                } else if (mutation instanceof Mc263StructureCarrier carrier) {
                    writeString(output, carrier.receiptSha256());
                } else {
                    writeString(output, String.valueOf(mutation));
                }
            }
        });
        return sha256(encoded);
    }

    private static String identityDigest(Object identity) {
        if (identity instanceof byte[] bytes) return sha256(bytes);
        if (identity instanceof Mc263StructureCarrier carrier) return carrier.receiptSha256();
        return sha256((identity.getClass().getName() + '\u0000')
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String successorReceiptDigest(long sequence, String predecessorDigest,
            String resultDigest, String mutationsDigest, String originDigest,
            String provenanceDigest) {
        byte[] encoded = encodeFragment(output -> {
            output.write(RECEIPT_MAGIC);
            output.writeLong(sequence);
            writeString(output, predecessorDigest);
            writeString(output, resultDigest);
            writeString(output, mutationsDigest);
            writeString(output, originDigest);
            writeString(output, provenanceDigest);
        });
        return sha256(encoded);
    }

    /** One record's own canonical encoding. */
    private interface FragmentWriter {
        void write(DataOutputStream output) throws IOException;
    }

    /**
     * The strict decoder's canonical-encoding law, applied to one record: what the decoder read
     * must be exactly what re-encoding the decoded record writes. The whole-receipt decoder
     * asserts the same law over the concatenation, and the receipt is that concatenation.
     */
    private static void requireCanonicalFragment(byte[] expected, byte[] reencoded) {
        if (!java.util.Arrays.equals(expected, reencoded)) {
            throw new IllegalArgumentException("noncanonical structure receipt encoding");
        }
    }

    private static void requireCanonicalSlice(byte[] receipt, int begin, int end,
            byte[] reencoded) {
        if (end - begin != reencoded.length
                || java.util.Arrays.mismatch(receipt, begin, end, reencoded, 0,
                        reencoded.length) >= 0) {
            throw new IllegalArgumentException("noncanonical structure receipt encoding");
        }
    }

    private static byte[] encodeFragment(FragmentWriter writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writer.write(output);
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory structure receipt failed", exception);
        }
        return bytes.toByteArray();
    }

    private byte[] encodeReceipt() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(RECEIPT_MAGIC);
                registry.write(output);
                output.writeInt(startChunks.size());
                for (ChunkStarts chunk : startChunks) output.write(chunk.fragment());
                output.writeInt(referenceChunks.size());
                for (ChunkReferences chunk : referenceChunks) output.write(chunk.fragment());
                if (!rawStartPayloads.isEmpty()) {
                    output.write(RAW_START_SECTION_MAGIC);
                    output.writeInt(rawStartPayloads.size());
                    for (RawStartPayload payload : rawStartPayloads) payload.write(output);
                }
                if (!producerGraphPayloads.isEmpty()) {
                    output.write(PRODUCER_GRAPH_SECTION_MAGIC);
                    output.writeInt(producerGraphPayloads.size());
                    for (ProducerGraphPayload payload : producerGraphPayloads) payload.write(output);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory structure receipt failed", exception);
        }
    }

    /**
     * Mirrors {@code ChunkGenerator#createReferences}: source X outer, source Z inner, then the
     * source chunk's observed start-map order. All 17x17 source chunks are required up front.
     */
    public static ChunkReferences createReferences(int targetChunkX, int targetChunkZ,
            Registry registry, List<ChunkStarts> sourceChunks) {
        Objects.requireNonNull(registry, "registry");
        int sourceMinX = Math.subtractExact(targetChunkX, REFERENCE_RADIUS);
        int sourceMaxX = Math.addExact(targetChunkX, REFERENCE_RADIUS);
        int sourceMinZ = Math.subtractExact(targetChunkZ, REFERENCE_RADIUS);
        int sourceMaxZ = Math.addExact(targetChunkZ, REFERENCE_RADIUS);
        Map<Long, ChunkStarts> sources = new HashMap<>();
        for (ChunkStarts source : List.copyOf(sourceChunks)) {
            source.validate(registry);
            if (Math.max(Math.abs((long) source.chunkX() - targetChunkX),
                    Math.abs((long) source.chunkZ() - targetChunkZ)) > REFERENCE_RADIUS) {
                throw new IllegalArgumentException("source chunk outside reference radius");
            }
            if (sources.putIfAbsent(packChunk(source.chunkX(), source.chunkZ()), source) != null) {
                throw new IllegalArgumentException("duplicate reference source chunk");
            }
        }
        int expected = (REFERENCE_RADIUS * 2 + 1) * (REFERENCE_RADIUS * 2 + 1);
        if (sources.size() != expected) {
            throw new IllegalArgumentException("STRUCTURE_REFERENCES requires 17x17 start chunks");
        }
        long minX = Math.multiplyExact((long) targetChunkX, 16L);
        long minZ = Math.multiplyExact((long) targetChunkZ, 16L);
        long maxX = Math.addExact(minX, 15L);
        long maxZ = Math.addExact(minZ, 15L);
        LinkedHashMap<String, ArrayList<Long>> groups = new LinkedHashMap<>();
        for (long sourceXValue = sourceMinX; sourceXValue <= sourceMaxX; sourceXValue++) {
            int sourceX = (int) sourceXValue;
            for (long sourceZValue = sourceMinZ; sourceZValue <= sourceMaxZ; sourceZValue++) {
                int sourceZ = (int) sourceZValue;
                ChunkStarts source = sources.get(packChunk(sourceX, sourceZ));
                if (source == null) {
                    throw new IllegalArgumentException("missing structure-start source chunk");
                }
                for (StartEntry entry : source.orderedStarts()) {
                    if (!(entry.body() instanceof ValidStart valid)
                            || !valid.adjustedBoundingBox().intersectsXZ(minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    groups.computeIfAbsent(entry.structureId(), ignored -> new ArrayList<>())
                            .add(packChunk(sourceX, sourceZ));
                }
            }
        }
        ArrayList<ReferenceSet> sets = new ArrayList<>();
        groups.forEach((structure, origins) ->
                sets.add(new ReferenceSet(structure, origins)));
        return new ChunkReferences(targetChunkX, targetChunkZ, sets);
    }

    public record StructureDefinition(String structureId, int registryOrdinal,
            int decorationStep, TerrainAdjustment terrainAdjustment) {
        public StructureDefinition {
            requireKey(structureId, "structureId");
            if (registryOrdinal < 0) throw new IllegalArgumentException("negative registry ordinal");
            if (decorationStep < 0 || decorationStep > MAX_DECORATION_STEP) {
                throw new IllegalArgumentException("decoration step is outside 0..10");
            }
            Objects.requireNonNull(terrainAdjustment, "terrainAdjustment");
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, structureId);
            output.writeInt(registryOrdinal);
            output.writeInt(decorationStep);
            output.writeByte(terrainAdjustment.code);
        }
    }

    public static final class Registry {
        private final List<StructureDefinition> definitions;
        private final Map<String, StructureDefinition> byKey;
        private volatile byte[] fragment;
        private volatile boolean canonical;

        public Registry(List<StructureDefinition> definitions) {
            this.definitions = List.copyOf(definitions);
            Map<String, StructureDefinition> mapped = new HashMap<>();
            for (int index = 0; index < this.definitions.size(); index++) {
                StructureDefinition definition = Objects.requireNonNull(
                        this.definitions.get(index), "structure definition");
                if (definition.registryOrdinal() != index) {
                    throw new IllegalArgumentException("registry ordinal/order mismatch");
                }
                if (mapped.putIfAbsent(definition.structureId(), definition) != null) {
                    throw new IllegalArgumentException("duplicate structure registry key");
                }
            }
            byKey = Map.copyOf(mapped);
        }

        public List<StructureDefinition> definitions() {
            return definitions;
        }

        public StructureDefinition require(String structureId) {
            StructureDefinition definition = byKey.get(structureId);
            if (definition == null) {
                throw new IllegalArgumentException("unrepresented structure: " + structureId);
            }
            return definition;
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(definitions.size());
            for (StructureDefinition definition : definitions) definition.write(output);
        }

        byte[] fragment() {
            byte[] encoded = fragment;
            if (encoded == null) {
                encoded = encodeFragment(this::write);
                fragment = encoded;
            }
            return encoded;
        }

        void markCanonical() { canonical = true; }

        boolean isCanonical() { return canonical; }

        /**
         * This registry as the strict decoder would rebuild it. The proven form is remembered on
         * the pinned instance, so the whole activation shares one registry object and every
         * record validated against it stays validated.
         */
        Registry canonicalized() {
            if (canonical) return this;
            byte[] encoded = fragment();
            ReceiptCursor cursor = new ReceiptCursor(encoded);
            Registry witness = decodeRegistry(cursor);
            cursor.requireEnd();
            requireCanonicalFragment(encoded, witness.fragment());
            canonical = true;
            return this;
        }
    }

    public enum TerrainAdjustment {
        NONE(0), BURY(1), BEARD_THIN(2), BEARD_BOX(3), ENCAPSULATE(4);
        private final int code;
        TerrainAdjustment(int code) { this.code = code; }
        Mc263Beardifier.TerrainAdjustment beard() {
            return Mc263Beardifier.TerrainAdjustment.valueOf(name());
        }
    }

    public enum Projection {
        RIGID(0), TERRAIN_MATCHING(1), NOT_APPLICABLE(2);
        private final int code;
        Projection(int code) { this.code = code; }
    }

    public record BoundingBox(int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted structure bounding box");
            }
        }

        boolean intersectsXZ(long otherMinX, long otherMinZ, long otherMaxX, long otherMaxZ) {
            return maxX >= otherMinX && minX <= otherMaxX
                    && maxZ >= otherMinZ && minZ <= otherMaxZ;
        }

        BoundingBox encapsulating(BoundingBox other) {
            return new BoundingBox(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }

        BoundingBox inflatedBy(int amount) {
            return new BoundingBox(Math.subtractExact(minX, amount),
                    Math.subtractExact(minY, amount), Math.subtractExact(minZ, amount),
                    Math.addExact(maxX, amount), Math.addExact(maxY, amount),
                    Math.addExact(maxZ, amount));
        }

        Mc263Beardifier.BoundingBox beard() {
            return new Mc263Beardifier.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(minX); output.writeInt(minY); output.writeInt(minZ);
            output.writeInt(maxX); output.writeInt(maxY); output.writeInt(maxZ);
        }
    }

    public record Junction(int sourceX, int sourceGroundY, int sourceZ, int deltaY,
            Projection destinationProjection) {
        public Junction {
            Objects.requireNonNull(destinationProjection, "destinationProjection");
            if (destinationProjection == Projection.NOT_APPLICABLE) {
                throw new IllegalArgumentException("junction projection is absent");
            }
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(sourceX); output.writeInt(sourceGroundY); output.writeInt(sourceZ);
            output.writeInt(deltaY); output.writeByte(destinationProjection.code);
        }
    }

    /** Exact full structure-start predecessor plus mutable successor; raw bytes are never re-encoded. */
    public static final class RawStartPayload {
        private final String structureId;
        private final String startKey;
        private final int originChunkX;
        private final int originChunkZ;
        private final byte[] predecessor;
        private final byte[] successor;
        private final String predecessorSha256;
        private final String successorSha256;
        private volatile boolean canonical;

        public RawStartPayload(String structureId, String startKey, int originChunkX,
                int originChunkZ, byte[] predecessorBinaryNbtCompound,
                byte[] successorBinaryNbtCompound) {
            requireKey(structureId, "structureId");
            String expected = structureId + "@" + originChunkX + "," + originChunkZ;
            if (!expected.equals(startKey)) {
                throw new IllegalArgumentException("raw start key/origin mismatch");
            }
            this.structureId = structureId;
            this.startKey = startKey;
            this.originChunkX = originChunkX;
            this.originChunkZ = originChunkZ;
            predecessor = validateRawStartNbt(predecessorBinaryNbtCompound, structureId,
                    originChunkX, originChunkZ, "predecessor");
            successor = validateRawStartNbt(successorBinaryNbtCompound, structureId,
                    originChunkX, originChunkZ, "successor");
            predecessorSha256 = sha256(predecessor);
            successorSha256 = sha256(successor);
        }

        public String structureId() { return structureId; }
        public String startKey() { return startKey; }
        public int originChunkX() { return originChunkX; }
        public int originChunkZ() { return originChunkZ; }
        public byte[] predecessorBinaryNbtCompound() { return predecessor.clone(); }
        public byte[] successorBinaryNbtCompound() { return successor.clone(); }
        public String predecessorSha256() { return predecessorSha256; }
        public String successorSha256() { return successorSha256; }

        private boolean matches(String structureId, ValidStart start) {
            return this.structureId.equals(structureId) && startKey.equals(start.startKey())
                    && originChunkX == start.originChunkX() && originChunkZ == start.originChunkZ();
        }

        private String identity() {
            return rawStartIdentity(structureId, startKey, originChunkX, originChunkZ);
        }

        private RawStartPayload withSuccessor(byte[] replacement) {
            return new RawStartPayload(structureId, startKey, originChunkX, originChunkZ,
                    predecessor, replacement);
        }

        private void write(DataOutputStream output) throws IOException {
            writeString(output, structureId);
            writeString(output, startKey);
            output.writeInt(originChunkX);
            output.writeInt(originChunkZ);
            output.writeByte(RAW_START_BINARY_NBT_COMPOUND);
            output.writeInt(predecessor.length);
            output.write(predecessor);
            output.writeByte(RAW_START_BINARY_NBT_COMPOUND);
            output.writeInt(successor.length);
            output.write(successor);
        }

        void markCanonical() { canonical = true; }

        boolean isCanonical() { return canonical; }
    }

    public record ProducerEdge(int sourcePieceOrdinal, int targetPieceOrdinal,
            String selectedPool, String resolvedAlias) {
        public ProducerEdge {
            if (sourcePieceOrdinal < 0 || targetPieceOrdinal <= sourcePieceOrdinal) {
                throw new IllegalArgumentException("invalid producer graph edge order");
            }
            requireKey(selectedPool, "selectedPool");
            requireKey(resolvedAlias, "resolvedAlias");
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(sourcePieceOrdinal);
            output.writeInt(targetPieceOrdinal);
            writeString(output, selectedPool);
            writeString(output, resolvedAlias);
        }
    }

    /**
     * One start's producer graph, carrying the strict decoder's per-record proof.
     *
     * <p>AGENTS rule 10l: this was a record, and a record cannot hold the {@code canonical} flag
     * {@link RawStartPayload} uses, so {@link #strictlyDecoded()} had to fall back to a whole
     * receipt round trip for every carrier that carries a producer graph — the Ancient City and
     * Woodland Mansion families, and every village world. It is a final class now for exactly the
     * reason {@code RawStartPayload} is one; its value semantics are unchanged, and the proof flag
     * is derived state that no component participates in.</p>
     */
    public static final class ProducerGraphPayload {
        private final String structureId;
        private final String startKey;
        private final int originChunkX;
        private final int originChunkZ;
        private final List<ProducerEdge> orderedEdges;
        private volatile boolean canonical;

        public ProducerGraphPayload(String structureId, String startKey, int originChunkX,
                int originChunkZ, List<ProducerEdge> orderedEdges) {
            requireKey(structureId, "structureId");
            String expected = structureId + "@" + originChunkX + "," + originChunkZ;
            if (!expected.equals(startKey)) {
                throw new IllegalArgumentException("producer graph start key/origin mismatch");
            }
            List<ProducerEdge> copied = List.copyOf(orderedEdges);
            for (ProducerEdge edge : copied) {
                Objects.requireNonNull(edge, "producer graph edge");
            }
            this.structureId = structureId;
            this.startKey = startKey;
            this.originChunkX = originChunkX;
            this.originChunkZ = originChunkZ;
            this.orderedEdges = copied;
        }

        public String structureId() { return structureId; }
        public String startKey() { return startKey; }
        public int originChunkX() { return originChunkX; }
        public int originChunkZ() { return originChunkZ; }
        public List<ProducerEdge> orderedEdges() { return orderedEdges; }

        void markCanonical() { canonical = true; }

        boolean isCanonical() { return canonical; }

        @Override public boolean equals(Object other) {
            return other instanceof ProducerGraphPayload payload
                    && structureId.equals(payload.structureId)
                    && startKey.equals(payload.startKey)
                    && originChunkX == payload.originChunkX
                    && originChunkZ == payload.originChunkZ
                    && orderedEdges.equals(payload.orderedEdges);
        }

        @Override public int hashCode() {
            return Objects.hash(structureId, startKey, originChunkX, originChunkZ, orderedEdges);
        }

        @Override public String toString() {
            return "ProducerGraphPayload[structureId=" + structureId + ", startKey=" + startKey
                    + ", originChunkX=" + originChunkX + ", originChunkZ=" + originChunkZ
                    + ", orderedEdges=" + orderedEdges + "]";
        }

        private boolean matches(String structureId, ValidStart start) {
            return this.structureId.equals(structureId) && startKey.equals(start.startKey())
                    && originChunkX == start.originChunkX() && originChunkZ == start.originChunkZ();
        }

        private String identity() {
            return rawStartIdentity(structureId, startKey, originChunkX, originChunkZ);
        }

        private void write(DataOutputStream output) throws IOException {
            writeString(output, structureId);
            writeString(output, startKey);
            output.writeInt(originChunkX);
            output.writeInt(originChunkZ);
            output.writeInt(orderedEdges.size());
            for (ProducerEdge edge : orderedEdges) edge.write(output);
        }
    }

    /**
     * Content key for a piece payload. The array it wraps is the {@link PiecePayload}'s own
     * defensive copy, which no other reference can reach, so the key is immutable and its hash
     * is stable.
     */
    private record PayloadKey(byte[] bytes, int hash) {
        PayloadKey(byte[] bytes) {
            this(bytes, java.util.Arrays.hashCode(bytes));
        }

        @Override public boolean equals(Object other) {
            return other instanceof PayloadKey key && hash == key.hash
                    && java.util.Arrays.equals(bytes, key.bytes);
        }

        @Override public int hashCode() { return hash; }
    }

    /**
     * Bounded memo of validated piece metadata, keyed by payload content. The bound holds far
     * more pieces than one FEATURES region carries while keeping a long activation wall from
     * retaining payloads that scrolled out of range; an evicted key simply revalidates.
     */
    private static final Mc263BoundedContentMemo<PayloadKey, NbtMetadata> PIECE_METADATA =
            new Mc263BoundedContentMemo<>(4096);

    /** Exact persisted binary-NBT compound boundary; decoding remains type-registry-owned. */
    public static final class PiecePayload {
        private static final int BINARY_NBT_COMPOUND = 1;
        private final byte[] bytes;
        private final String persistedPieceType;
        private final BoundingBox persistedBoundingBox;

        /**
         * AGENTS rule 10l: the piece metadata is a pure function of the payload bytes, but a
         * carrier is decoded on every structure commit and each decode reparsed and revalidated
         * the full binary-NBT compound of every piece of every structure already in the carrier —
         * work whose result cannot differ from the previous commit's, because the bytes are the
         * same bytes. The bounded memo below answers from the payload content, so the accepted
         * language, the rejection messages and the resulting metadata are unchanged; a malformed
         * payload still throws on its first (and every) construction because failures are never
         * stored.
         */
        public PiecePayload(byte[] binaryNbtCompound) {
            bytes = Objects.requireNonNull(binaryNbtCompound, "binaryNbtCompound").clone();
            NbtMetadata metadata = PIECE_METADATA.resolve(new PayloadKey(bytes),
                    () -> NbtCursor.validatePieceRootCompound(bytes));
            persistedPieceType = metadata.pieceType();
            persistedBoundingBox = metadata.boundingBox();
        }

        public byte[] binaryNbtCompound() { return bytes.clone(); }

        void write(DataOutputStream output) throws IOException {
            output.writeByte(BINARY_NBT_COMPOUND);
            output.writeInt(bytes.length);
            output.write(bytes);
        }

        @Override public boolean equals(Object other) {
            return other instanceof PiecePayload payload
                    && java.util.Arrays.equals(bytes, payload.bytes);
        }

        @Override public int hashCode() { return java.util.Arrays.hashCode(bytes); }
    }

    public record Piece(String pieceType, BoundingBox boundingBox, boolean poolElement,
            Projection projection, int groundLevelDelta, List<Junction> junctions,
            PiecePayload persistedPayload) {
        public Piece {
            requireKey(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(projection, "projection");
            junctions = List.copyOf(junctions);
            Objects.requireNonNull(persistedPayload, "persistedPayload");
            if (!pieceType.equals(persistedPayload.persistedPieceType)
                    || !boundingBox.equals(persistedPayload.persistedBoundingBox)) {
                throw new IllegalArgumentException("piece facts disagree with persisted NBT");
            }
            if (poolElement && projection == Projection.NOT_APPLICABLE) {
                throw new IllegalArgumentException("pool piece projection is absent");
            }
            if (!poolElement && (projection != Projection.NOT_APPLICABLE
                    || groundLevelDelta != 0 || !junctions.isEmpty())) {
                throw new IllegalArgumentException("non-pool piece carries pool-only facts");
            }
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, pieceType); boundingBox.write(output);
            output.writeBoolean(poolElement); output.writeByte(projection.code);
            output.writeInt(groundLevelDelta); output.writeInt(junctions.size());
            for (Junction junction : junctions) junction.write(output);
            persistedPayload.write(output);
        }
    }

    public sealed interface StartBody permits InvalidStart, ValidStart {
        boolean valid();
    }

    public record InvalidStart() implements StartBody {
        @Override public boolean valid() { return false; }
    }

    public record ValidStart(String startKey, int originChunkX, int originChunkZ,
            int references, BoundingBox adjustedBoundingBox, List<Piece> orderedPieces)
            implements StartBody {
        public ValidStart {
            if (startKey == null || startKey.indexOf('@') <= 0) {
                throw new IllegalArgumentException("malformed structure start key");
            }
            requireKey(startKey.substring(0, startKey.indexOf('@')), "startKey");
            if (references < 0) throw new IllegalArgumentException("negative reference count");
            Objects.requireNonNull(adjustedBoundingBox, "adjustedBoundingBox");
            orderedPieces = List.copyOf(orderedPieces);
            if (orderedPieces.isEmpty()) throw new IllegalArgumentException("valid start has no pieces");
        }
        @Override public boolean valid() { return true; }

        /**
         * This start with {@code pieces} in place of its pieces, or {@code this} when every piece
         * is the very object it already holds.
         */
        public ValidStart withPieces(List<Piece> pieces) {
            if (pieces.size() == orderedPieces.size()) {
                boolean same = true;
                for (int index = 0; index < pieces.size(); index++) {
                    if (pieces.get(index) != orderedPieces.get(index)) { same = false; break; }
                }
                if (same) return this;
            }
            return new ValidStart(startKey, originChunkX, originChunkZ, references,
                    adjustedBoundingBox, pieces);
        }
    }

    public record StartEntry(String structureId, StartBody body) {
        /** This entry with {@code replacement} in place of its body, or {@code this} unchanged. */
        public StartEntry withBody(StartBody replacement) {
            return replacement == body ? this : new StartEntry(structureId, replacement);
        }

        public StartEntry {
            requireKey(structureId, "structureId");
            Objects.requireNonNull(body, "body");
            if (body instanceof ValidStart valid) {
                String expected = structureId + "@" + valid.originChunkX() + ","
                        + valid.originChunkZ();
                if (!expected.equals(valid.startKey())) {
                    throw new IllegalArgumentException("structure start key/origin mismatch");
                }
            }
        }

        void validate(Registry registry, int chunkX, int chunkZ) {
            StructureDefinition definition = registry.require(structureId);
            if (!(body instanceof ValidStart valid)) return;
            if (valid.originChunkX() != chunkX || valid.originChunkZ() != chunkZ) {
                throw new IllegalArgumentException("start origin/owning chunk mismatch");
            }
            BoundingBox raw = valid.orderedPieces().get(0).boundingBox();
            for (int index = 1; index < valid.orderedPieces().size(); index++) {
                raw = raw.encapsulating(valid.orderedPieces().get(index).boundingBox());
            }
            BoundingBox expected = definition.terrainAdjustment() == TerrainAdjustment.NONE
                    ? raw : raw.inflatedBy(12);
            if (!expected.equals(valid.adjustedBoundingBox())) {
                throw new IllegalArgumentException("adjusted start bounding box mismatch");
            }
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, structureId);
            output.writeBoolean(body.valid());
            if (!(body instanceof ValidStart valid)) return;
            writeString(output, valid.startKey());
            output.writeInt(valid.originChunkX()); output.writeInt(valid.originChunkZ());
            output.writeInt(valid.references()); valid.adjustedBoundingBox().write(output);
            output.writeInt(valid.orderedPieces().size());
            for (Piece piece : valid.orderedPieces()) piece.write(output);
        }
    }

    /**
     * One chunk's ordered structure starts.
     *
     * <p>AGENTS rule 10l: this is a value class, not a record, because it carries the amortization
     * state a record cannot hold. A structure commit republishes a carrier whose window holds
     * hundreds of these records while touching exactly one of them, and the receipt of an
     * untouched record is a constant of its content. The cached fragment below is that constant;
     * {@link #canonical} records that the fragment was produced by, or verified against, the
     * strict decoder, and {@link #validatedAgainst} records the registry this record was already
     * validated against. Nothing here is part of the value: two records with equal content are
     * equal and encode to the same bytes whether or not either has computed its fragment.</p>
     */
    public static final class ChunkStarts {
        private final int chunkX;
        private final int chunkZ;
        private final List<StartEntry> orderedStarts;
        private volatile byte[] fragment;
        private volatile boolean canonical;
        private volatile Registry validatedAgainst;

        public ChunkStarts(int chunkX, int chunkZ, List<StartEntry> orderedStarts) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.orderedStarts = List.copyOf(orderedStarts);
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public List<StartEntry> orderedStarts() { return orderedStarts; }

        /**
         * This record with {@code starts} in place of its entries, or {@code this} when every
         * entry is the very object it already holds. Reusing the untouched record keeps its
         * canonical fragment and its validation, so a commit that rewrites one chunk of a window
         * pays for one chunk instead of the window.
         */
        public ChunkStarts withStarts(List<StartEntry> starts) {
            if (starts.size() == orderedStarts.size()) {
                boolean same = true;
                for (int index = 0; index < starts.size(); index++) {
                    if (starts.get(index) != orderedStarts.get(index)) { same = false; break; }
                }
                if (same) return this;
            }
            return new ChunkStarts(chunkX, chunkZ, starts);
        }

        /** The canonical encoding of exactly this record, computed once. */
        byte[] fragment() {
            byte[] encoded = fragment;
            if (encoded == null) {
                encoded = encodeFragment(this::write);
                fragment = encoded;
            }
            return encoded;
        }

        /** Marks this record proven canonical by the strict decoder. */
        void markCanonical() { canonical = true; }

        /**
         * This record as the strict decoder would rebuild it. A record the decoder already
         * produced is returned unchanged; any other is round-tripped through its own fragment,
         * which is exactly what decoding the whole receipt would do to it.
         */
        ChunkStarts canonicalized() {
            if (canonical) return this;
            byte[] encoded = fragment();
            ReceiptCursor cursor = new ReceiptCursor(encoded);
            ChunkStarts decoded = decodeStartChunk(cursor);
            cursor.requireEnd();
            requireCanonicalFragment(encoded, decoded.fragment());
            // The witness proved the law; it is this record's own equal, because the encoding is
            // injective over the record's content. Marking this record — the one the region memo
            // and the window still hold — keeps the proof where the reuse is.
            canonical = true;
            return this;
        }

        void validateOnce(Registry registry) {
            if (validatedAgainst == registry) return;
            validate(registry);
            validatedAgainst = registry;
        }

        @Override public boolean equals(Object other) {
            return this == other || (other instanceof ChunkStarts chunk
                    && chunkX == chunk.chunkX && chunkZ == chunk.chunkZ
                    && orderedStarts.equals(chunk.orderedStarts));
        }

        @Override public int hashCode() {
            return (chunkX * 31 + chunkZ) * 31 + orderedStarts.hashCode();
        }

        @Override public String toString() {
            return "ChunkStarts[chunkX=" + chunkX + ", chunkZ=" + chunkZ
                    + ", orderedStarts=" + orderedStarts + "]";
        }

        void validate(Registry registry) {
            Set<String> observed = new HashSet<>();
            for (StartEntry entry : orderedStarts) {
                Objects.requireNonNull(entry, "start entry");
                if (!observed.add(entry.structureId())) {
                    throw new IllegalArgumentException("duplicate start registry key");
                }
                entry.validate(registry, chunkX, chunkZ);
            }
        }

        StartEntry start(String structureId) {
            for (StartEntry entry : orderedStarts) {
                if (entry.structureId().equals(structureId)) return entry;
            }
            return null;
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(chunkX); output.writeInt(chunkZ);
            output.writeInt(orderedStarts.size());
            for (StartEntry entry : orderedStarts) entry.write(output);
        }
    }

    public record ReferenceSet(String structureId, List<Long> orderedOrigins) {
        public ReferenceSet {
            requireKey(structureId, "structureId");
            orderedOrigins = List.copyOf(orderedOrigins);
            if (orderedOrigins.isEmpty()) throw new IllegalArgumentException("empty reference set");
            if (new HashSet<>(orderedOrigins).size() != orderedOrigins.size()) {
                throw new IllegalArgumentException("duplicate origin in reference long set");
            }
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, structureId); output.writeInt(orderedOrigins.size());
            for (long origin : orderedOrigins) output.writeLong(origin);
        }
    }

    /** One chunk's ordered structure reference sets; a value class for the reason {@link
     * ChunkStarts} is one. */
    public static final class ChunkReferences {
        private final int chunkX;
        private final int chunkZ;
        private final List<ReferenceSet> orderedSets;
        private volatile byte[] fragment;
        private volatile boolean canonical;
        private volatile Registry validatedAgainst;

        public ChunkReferences(int chunkX, int chunkZ, List<ReferenceSet> orderedSets) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.orderedSets = List.copyOf(orderedSets);
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public List<ReferenceSet> orderedSets() { return orderedSets; }

        byte[] fragment() {
            byte[] encoded = fragment;
            if (encoded == null) {
                encoded = encodeFragment(this::write);
                fragment = encoded;
            }
            return encoded;
        }

        void markCanonical() { canonical = true; }

        ChunkReferences canonicalized() {
            if (canonical) return this;
            byte[] encoded = fragment();
            ReceiptCursor cursor = new ReceiptCursor(encoded);
            ChunkReferences decoded = decodeReferenceChunk(cursor);
            cursor.requireEnd();
            requireCanonicalFragment(encoded, decoded.fragment());
            canonical = true;
            return this;
        }

        void validateOnce(Registry registry) {
            if (validatedAgainst == registry) return;
            validate(registry);
            validatedAgainst = registry;
        }

        @Override public boolean equals(Object other) {
            return this == other || (other instanceof ChunkReferences chunk
                    && chunkX == chunk.chunkX && chunkZ == chunk.chunkZ
                    && orderedSets.equals(chunk.orderedSets));
        }

        @Override public int hashCode() {
            return (chunkX * 31 + chunkZ) * 31 + orderedSets.hashCode();
        }

        @Override public String toString() {
            return "ChunkReferences[chunkX=" + chunkX + ", chunkZ=" + chunkZ
                    + ", orderedSets=" + orderedSets + "]";
        }

        void validate(Registry registry) {
            Set<String> observed = new HashSet<>();
            for (ReferenceSet set : orderedSets) {
                registry.require(set.structureId());
                if (!observed.add(set.structureId())) {
                    throw new IllegalArgumentException("duplicate structure reference set");
                }
                for (long origin : set.orderedOrigins()) {
                    long distanceX = Math.abs((long) unpackChunkX(origin) - chunkX);
                    long distanceZ = Math.abs((long) unpackChunkZ(origin) - chunkZ);
                    if (Math.max(distanceX, distanceZ) > REFERENCE_RADIUS) {
                        throw new IllegalArgumentException("structure reference exceeds radius 8");
                    }
                }
            }
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(chunkX); output.writeInt(chunkZ); output.writeInt(orderedSets.size());
            for (ReferenceSet set : orderedSets) set.write(output);
        }
    }

    public static long packChunk(int chunkX, int chunkZ) {
        return Integer.toUnsignedLong(chunkX) | Integer.toUnsignedLong(chunkZ) << 32;
    }

    public static int unpackChunkX(long packed) { return (int) packed; }
    public static int unpackChunkZ(long packed) { return (int) (packed >>> 32); }
    private static String rawStartIdentity(String structureId, String startKey,
            int originChunkX, int originChunkZ) {
        return structureId + '\u0000' + startKey + '\u0000' + originChunkX + '\u0000' + originChunkZ;
    }

    private static byte[] validateRawStartNbt(byte[] value, String structureId,
            int originChunkX, int originChunkZ, String label) {
        byte[] bytes = Objects.requireNonNull(value, label + "BinaryNbtCompound").clone();
        if (bytes.length == 0 || bytes.length > MAX_RAW_START_NBT_BYTES) {
            throw new IllegalArgumentException("raw start " + label + " NBT size is out of bounds");
        }
        StartNbtMetadata metadata = NbtCursor.validateStartRootCompound(bytes);
        if (!structureId.equals(metadata.structureId())
                || originChunkX != metadata.originChunkX()
                || originChunkZ != metadata.originChunkZ()) {
            throw new IllegalArgumentException("raw start " + label + " identity mismatch");
        }
        return bytes;
    }

    /**
     * Pinned validators for the two string shapes this carrier accepts.
     *
     * <p>AGENTS rule 10l: {@code String.matches} compiles its regex on every call, and the
     * carrier validates every registry key and receipt digest it decodes — thousands per chunk
     * once the FEATURES seam re-decodes a carrier per structure commit. The compiled patterns
     * accept exactly the same strings; only the compilation is hoisted.</p>
     */
    /**
     * AGENTS rule 10l: these two shapes are validated once per carrier key and per digest, and a
     * carrier is decoded on every structure commit, so the check sits on the warm seam. A
     * precompiled {@link java.util.regex.Pattern} still allocates a {@code Matcher} and walks the
     * regex engine per call; the character scans below decide the identical language
     * ({@code [0-9a-f]{64}} and {@code [a-z0-9_.-]+:[a-z0-9_./-]+}, both fully anchored because
     * the callers used {@code matches()}) with no allocation. Byte-neutral: same accepted set,
     * same rejection message.
     */
    private static final int SHA256_LENGTH = 64;

    private static boolean isLowerHex(char value) {
        return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f');
    }

    /** The {@code [a-z0-9_.-]} namespace class. */
    private static boolean isNamespaceChar(char value) {
        return (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')
                || value == '_' || value == '.' || value == '-';
    }

    /** The {@code [a-z0-9_./-]} path class — the namespace class plus {@code '/'}. */
    private static boolean isPathChar(char value) {
        return isNamespaceChar(value) || value == '/';
    }

    private static boolean isSha256(String value) {
        if (value.length() != SHA256_LENGTH) {
            return false;
        }
        for (int index = 0; index < SHA256_LENGTH; index++) {
            if (!isLowerHex(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRegistryKey(String value) {
        int colon = -1;
        int length = value.length();
        for (int index = 0; index < length; index++) {
            if (value.charAt(index) == ':') {
                colon = index;
                break;
            }
            if (!isNamespaceChar(value.charAt(index))) {
                return false;
            }
        }
        if (colon <= 0 || colon == length - 1) {
            return false;
        }
        for (int index = colon + 1; index < length; index++) {
            if (!isPathChar(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String requireSha256(String value, String label) {
        if (value == null || !isSha256(value)) {
            throw new IllegalArgumentException(label + " is not canonical lowercase SHA-256");
        }
        return value;
    }


    private static void requireKey(String value, String label) {
        if (value == null || !isRegistryKey(value)) {
            throw new IllegalArgumentException(label + " is not a registry key");
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length); output.write(bytes);
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static TerrainAdjustment terrainAdjustment(int code) {
        return switch (code) {
            case 0 -> TerrainAdjustment.NONE;
            case 1 -> TerrainAdjustment.BURY;
            case 2 -> TerrainAdjustment.BEARD_THIN;
            case 3 -> TerrainAdjustment.BEARD_BOX;
            case 4 -> TerrainAdjustment.ENCAPSULATE;
            default -> throw new IllegalArgumentException("unknown terrain adjustment code");
        };
    }

    private static Projection projection(int code) {
        return switch (code) {
            case 0 -> Projection.RIGID;
            case 1 -> Projection.TERRAIN_MATCHING;
            case 2 -> Projection.NOT_APPLICABLE;
            default -> throw new IllegalArgumentException("unknown structure projection code");
        };
    }

    /** Bounds-checking big-endian STR263C1 decoder. */
    private static final class ReceiptCursor {
        private final byte[] bytes;
        private int offset;

        private ReceiptCursor(byte[] bytes) { this.bytes = bytes; }

        private void requireMagic() {
            if (bytes.length < RECEIPT_MAGIC.length) {
                throw malformedReceipt("truncated receipt magic");
            }
            for (byte expected : RECEIPT_MAGIC) {
                if (unsignedByte() != Byte.toUnsignedInt(expected)) {
                    throw malformedReceipt("bad receipt magic");
                }
            }
        }

        private void requireBytes(byte[] expected, String label) {
            if (remaining() < expected.length) throw malformedReceipt("truncated " + label);
            for (byte value : expected) {
                if (unsignedByte() != Byte.toUnsignedInt(value)) {
                    throw malformedReceipt("bad " + label);
                }
            }
        }
        private int count(String label, int minimumWidth) {
            int value = signedInt();
            if (value < 0) throw malformedReceipt("negative " + label + " count");
            if (minimumWidth > 0 && value > remaining() / minimumWidth) {
                throw malformedReceipt(label + " count exceeds remaining receipt");
            }
            return value;
        }

        private String string() {
            byte[] encoded = byteArray("string");
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(encoded)).toString();
            } catch (CharacterCodingException exception) {
                throw malformedReceipt("invalid UTF-8 string");
            }
        }

        private byte[] byteArray(String label) {
            int length = signedInt();
            if (length < 0 || length > remaining()) {
                throw malformedReceipt("invalid or truncated " + label + " length");
            }
            byte[] result = java.util.Arrays.copyOfRange(bytes, offset, offset + length);
            offset += length;
            return result;
        }

        private BoundingBox boundingBox() {
            return new BoundingBox(signedInt(), signedInt(), signedInt(), signedInt(),
                    signedInt(), signedInt());
        }

        private boolean bool() {
            return switch (unsignedByte()) {
                case 0 -> false;
                case 1 -> true;
                default -> throw malformedReceipt("noncanonical boolean");
            };
        }

        private long signedLong() {
            return (Integer.toUnsignedLong(signedInt()) << 32)
                    | Integer.toUnsignedLong(signedInt());
        }

        private int signedInt() {
            return (unsignedByte() << 24) | (unsignedByte() << 16)
                    | (unsignedByte() << 8) | unsignedByte();
        }

        private int unsignedByte() {
            if (offset >= bytes.length) throw malformedReceipt("truncated structure receipt");
            return Byte.toUnsignedInt(bytes[offset++]);
        }

        private int remaining() { return bytes.length - offset; }

        private void requireEnd() {
            if (remaining() != 0) throw malformedReceipt("structure receipt has trailing bytes");
        }

        private static IllegalArgumentException malformedReceipt(String message) {
            return new IllegalArgumentException("malformed structure receipt: " + message);
        }
    }

    /** Allocation-free structural validation for an unnamed-or-named binary NBT root compound. */
    private static final class NbtCursor {
        private static final int MAX_DEPTH = 512;
        private final byte[] bytes;
        private int offset;

        private NbtCursor(byte[] bytes) { this.bytes = bytes; }

        static NbtMetadata validatePieceRootCompound(byte[] bytes) {
            NbtCursor input = new NbtCursor(bytes);
            if (input.unsignedByte() != 10) {
                throw new IllegalArgumentException("piece payload root is not an NBT compound");
            }
            input.modifiedUtf();
            String pieceType = null;
            BoundingBox boundingBox = null;
            while (true) {
                int childType = input.unsignedByte();
                requireType(childType);
                if (childType == 0) break;
                String name = ascii(input.modifiedUtf(), "top-level NBT name");
                if (name.equals("id")) {
                    if (pieceType != null || childType != 8) {
                        throw malformed("invalid or duplicate piece id");
                    }
                    pieceType = ascii(input.modifiedUtf(), "piece id");
                    requireKey(pieceType, "persisted piece id");
                } else if (name.equals("BB")) {
                    if (boundingBox != null || childType != 11 || input.nonnegativeInt() != 6) {
                        throw malformed("invalid or duplicate piece bounding box");
                    }
                    boundingBox = new BoundingBox(input.signedInt(), input.signedInt(),
                            input.signedInt(), input.signedInt(), input.signedInt(),
                            input.signedInt());
                } else {
                    input.payload(childType, 1);
                }
            }
            if (input.offset != bytes.length) {
                throw new IllegalArgumentException("piece payload has trailing NBT bytes");
            }
            if (pieceType == null || boundingBox == null) {
                throw malformed("piece NBT lacks id or BB");
            }
            return new NbtMetadata(pieceType, boundingBox);
        }

        static StartNbtMetadata validateStartRootCompound(byte[] bytes) {
            NbtCursor input = new NbtCursor(bytes);
            if (input.unsignedByte() != 10) {
                throw new IllegalArgumentException("start payload root is not an NBT compound");
            }
            input.modifiedUtf();
            String structureId = null;
            Integer originChunkX = null;
            Integer originChunkZ = null;
            while (true) {
                int childType = input.unsignedByte();
                requireType(childType);
                if (childType == 0) break;
                String name = ascii(input.modifiedUtf(), "top-level NBT name");
                if (name.equals("id")) {
                    if (structureId != null || childType != 8) {
                        throw malformed("invalid or duplicate structure start id");
                    }
                    structureId = ascii(input.modifiedUtf(), "structure start id");
                    requireKey(structureId, "persisted structure start id");
                } else if (name.equals("ChunkX")) {
                    if (originChunkX != null || childType != 3) {
                        throw malformed("invalid or duplicate structure start ChunkX");
                    }
                    originChunkX = input.signedInt();
                } else if (name.equals("ChunkZ")) {
                    if (originChunkZ != null || childType != 3) {
                        throw malformed("invalid or duplicate structure start ChunkZ");
                    }
                    originChunkZ = input.signedInt();
                } else {
                    input.payload(childType, 1);
                }
            }
            if (input.offset != bytes.length) {
                throw new IllegalArgumentException("start payload has trailing NBT bytes");
            }
            if (structureId == null || originChunkX == null || originChunkZ == null) {
                throw malformed("structure start NBT lacks id, ChunkX, or ChunkZ");
            }
            return new StartNbtMetadata(structureId, originChunkX, originChunkZ);
        }

        private void payload(int type, int depth) {
            if (depth > MAX_DEPTH) throw malformed("NBT depth exceeds 512");
            switch (type) {
                case 0 -> { }
                case 1 -> skip(1);
                case 2 -> skip(2);
                case 3, 5 -> skip(4);
                case 4, 6 -> skip(8);
                case 7 -> skipArray(1);
                case 8 -> modifiedUtf();
                case 9 -> {
                    int elementType = unsignedByte();
                    requireType(elementType);
                    int length = nonnegativeInt();
                    if (elementType == 0 && length != 0) {
                        throw malformed("nonempty NBT list has TAG_End elements");
                    }
                    for (int index = 0; index < length; index++) {
                        payload(elementType, depth + 1);
                    }
                }
                case 10 -> {
                    while (true) {
                        int childType = unsignedByte();
                        requireType(childType);
                        if (childType == 0) break;
                        modifiedUtf();
                        payload(childType, depth + 1);
                    }
                }
                case 11 -> skipArray(4);
                case 12 -> skipArray(8);
                default -> throw malformed("unknown NBT tag type");
            }
        }

        private void skipArray(int width) {
            int length = nonnegativeInt();
            long byteCount = (long) length * width;
            if (byteCount > Integer.MAX_VALUE) throw malformed("NBT array is too large");
            skip((int) byteCount);
        }

        private byte[] modifiedUtf() {
            int length = unsignedShort();
            int start = offset;
            int end = checkedEnd(length);
            while (offset < end) {
                int first = Byte.toUnsignedInt(bytes[offset++]);
                int continuationCount;
                if (first >= 1 && first <= 0x7f) continuationCount = 0;
                else if ((first & 0xe0) == 0xc0) continuationCount = 1;
                else if ((first & 0xf0) == 0xe0) continuationCount = 2;
                else throw malformed("invalid modified-UTF byte");
                if (offset + continuationCount > end) {
                    throw malformed("truncated modified-UTF sequence");
                }
                for (int index = 0; index < continuationCount; index++) {
                    if ((Byte.toUnsignedInt(bytes[offset++]) & 0xc0) != 0x80) {
                        throw malformed("invalid modified-UTF continuation");
                    }
                }
            }
            return java.util.Arrays.copyOfRange(bytes, start, end);
        }

        private int nonnegativeInt() {
            int value = signedInt();
            if (value < 0) throw malformed("negative NBT length");
            return value;
        }

        private int signedInt() {
            return (unsignedByte() << 24) | (unsignedByte() << 16)
                    | (unsignedByte() << 8) | unsignedByte();
        }

        private int unsignedShort() { return (unsignedByte() << 8) | unsignedByte(); }

        private int unsignedByte() {
            if (offset >= bytes.length) throw malformed("truncated NBT payload");
            return Byte.toUnsignedInt(bytes[offset++]);
        }

        private void skip(int length) { offset = checkedEnd(length); }

        private int checkedEnd(int length) {
            if (length < 0 || length > bytes.length - offset) {
                throw malformed("truncated NBT payload");
            }
            return offset + length;
        }

        private static void requireType(int type) {
            if (type < 0 || type > 12) throw malformed("unknown NBT tag type");
        }

        private static IllegalArgumentException malformed(String message) {
            return new IllegalArgumentException("malformed piece NBT: " + message);
        }

        private static String ascii(byte[] bytes, String label) {
            for (byte value : bytes) {
                int unsigned = Byte.toUnsignedInt(value);
                if (unsigned < 1 || unsigned > 0x7f) throw malformed(label + " is not ASCII");
            }
            return new String(bytes, StandardCharsets.US_ASCII);
        }
    }

    private record NbtMetadata(String pieceType, BoundingBox boundingBox) {}
    private record StartNbtMetadata(String structureId, int originChunkX, int originChunkZ) {}

    private Mc263StructureCarrier() {
        throw new UnsupportedOperationException("static and value API only");
    }
}
