package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticated, code-generatable Woodland Mansion grammar boundary for the pinned 26.3 build.
 *
 * <p>The accepted v1 grammar remains raw-NBT-free and byte-identical. A separately pinned v2
 * settlement evidence sidecar preserves only official post-placement block-entity and canonicalized
 * runtime-entity NBT bytes, plus exact RNG/destination receipts needed by a lawful settlement.</p>
 */
public final class Mc263WoodlandMansionGrammar {
    public static final String RESOURCE = "/mc263/woodland-mansion-code-grammar-v1.json";
    public static final String FILE_SHA256 =
            "cdc45615029aad2bf39674e4761a44fbf2a1bc25f19eb0abd1e47a073439382e";
    public static final String GRAMMAR_SHA256 =
            "b532d2b62e063d83bd7dbf8886c3bc1f2ef3810cfd74414d424cc0469b720cf9";
    public static final String ORACLE_SHA256 =
            "2134e3b19697abd9dc4655a0578eb11959dc11292a587a8ad64d7a97a44869bc";
    public static final String ORACLE_SOURCE_SHA256 =
            "36f08eee19fef68d9b26b82a48f3ba11c639cb74e6f34e997c2870d126a8ed34";
    public static final String TEMPLATE_ORDERED_SHA256 =
            "2658d0b7fb2fca9be61b9bc272ff7e1c8fb116911a99043e46c8089378fe7fe0";
    public static final String PROCESSOR_SHA256 =
            "01cff40325682975d114a41247f87b7c404cfb9cbf50ea621008aa0c245098c3";
    public static final String SETTLEMENT_EVIDENCE_RESOURCE =
            "/mc263/woodland-mansion-settlement-evidence-v2.json";
    public static final String SETTLEMENT_EVIDENCE_FILE_SHA256 =
            "98f2884e19fbe95679347c7cd1e55e70a8ee3dbc12f8d894be058164e646e03f";
    public static final String SETTLEMENT_EVIDENCE_SHA256 =
            "7b8568894e1293efac8fd492da54c507de431ef2c3dad2a378aa1c657158600d";
    public static final String SETTLEMENT_ORACLE_SHA256 =
            "dc8a4489905101d9aad6a855ff4e7a1fe30deccf23153b844bda46f74de74008";
    public static final String SETTLEMENT_ORACLE_SOURCE_SHA256 =
            "809a4c81f39d09a60e0e8ca89b5c1872ac12d6d5a1ea4044f93b25c0ab4887be";
    public static final String SETTLEMENT_GENERATOR_SOURCE_SHA256 =
            "ee4677829fe6607ba584bbd1783d6f4fd68560dd2449ba5cb0abd8740af4daa3";
    public static final String PERSISTED_NBT_EVIDENCE_RESOURCE =
            "/mc263/woodland-mansion-persisted-nbt-evidence-v1.json";
    public static final String PERSISTED_NBT_EVIDENCE_FILE_SHA256 =
            "d795b0efb483d3acfec5463cb7b1ac5dac1bfb24f0643336261ecf2d5c776a01";
    public static final String PERSISTED_NBT_EVIDENCE_SHA256 =
            "2212d5c17b1a2147629297a10bf83542371591f3cd0f4a18f252fef52db29b7a";
    public static final String PERSISTED_NBT_ORACLE_SHA256 =
            "cc6255544f8443e4bc6d5dcd0f57fe2e3128b51e738c5181cfb2a4c59062dc13";
    public static final String PERSISTED_NBT_ORACLE_SOURCE_SHA256 =
            "89426d265b5fe0ca58fd8ce1c356fbd5eef6db0597e2ded39bd304c45c67f87d";
    public static final String PERSISTED_NBT_ORACLE_CONTRACT_SHA256 =
            "bc2f1a4c2d26ee9e891e88773912bb71b794160c6b7982d14be55899537dbb86";
    public static final String PERSISTED_NBT_JAVA_RELEASE_SHA256 =
            "7c78360a21f71b7836496af79bd4f345d4655c83322dfd73a52eacbb05d25572";

    private static final String PRODUCER = "gameexpert-mc263-woodland-mansion-grammar-v1";
    private static final String PROCESSOR =
            "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor";
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern STATE = Pattern.compile(
            "minecraft:[a-z0-9_]+(?:\\[[a-z0-9_]+=[a-z0-9_]+"
                    + "(?:, ?[a-z0-9_]+=[a-z0-9_]+)*])?");
    private static final Set<String> MARKERS = Set.of(
            "Chest", "ChestWest", "ChestEast", "ChestSouth", "ChestNorth",
            "Mage", "Warrior", "Group of Allays");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final List<String> stateCatalog;
    private final Map<String, Template> templates;
    private final List<Probe> probes;
    private final SettlementEvidence settlementEvidence;
    private final PersistedNbtEvidence persistedNbtEvidence;

    private Mc263WoodlandMansionGrammar(List<String> stateCatalog,
            Map<String, Template> templates, List<Probe> probes) {
        this(stateCatalog, templates, probes, null, null);
    }

    private Mc263WoodlandMansionGrammar(List<String> stateCatalog,
            Map<String, Template> templates, List<Probe> probes, SettlementEvidence settlementEvidence,
            PersistedNbtEvidence persistedNbtEvidence) {
        this.stateCatalog = List.copyOf(stateCatalog);
        this.templates = Map.copyOf(templates);
        this.probes = List.copyOf(probes);
        this.settlementEvidence = settlementEvidence;
        this.persistedNbtEvidence = persistedNbtEvidence;
    }

    /** Loads the accepted grammar plus the independently pinned settlement evidence sidecar. */
    public static Mc263WoodlandMansionGrammar loadAccepted() {
        try (InputStream input = Mc263WoodlandMansionGrammar.class.getResourceAsStream(RESOURCE);
                InputStream settlement = Mc263WoodlandMansionGrammar.class
                        .getResourceAsStream(SETTLEMENT_EVIDENCE_RESOURCE);
                InputStream persisted = Mc263WoodlandMansionGrammar.class
                        .getResourceAsStream(PERSISTED_NBT_EVIDENCE_RESOURCE)) {
            if (input == null) throw new IllegalStateException("missing accepted Mansion grammar " + RESOURCE);
            if (settlement == null) throw new IllegalStateException(
                    "missing accepted Mansion settlement evidence " + SETTLEMENT_EVIDENCE_RESOURCE);
            if (persisted == null) throw new IllegalStateException(
                    "missing accepted Mansion persisted-NBT evidence " + PERSISTED_NBT_EVIDENCE_RESOURCE);
            Mc263WoodlandMansionGrammar grammar = decode(input.readAllBytes());
            SettlementEvidence evidence = decodeSettlementEvidence(settlement.readAllBytes(), grammar.templates);
            PersistedNbtEvidence persistedEvidence = decodePersistedNbtEvidence(
                    persisted.readAllBytes(), grammar.probes);
            return new Mc263WoodlandMansionGrammar(
                    grammar.stateCatalog, grammar.templates, grammar.probes, evidence, persistedEvidence);
        } catch (IOException error) {
            throw new IllegalStateException("cannot read accepted Mansion grammar/evidence", error);
        }
    }

    /** Decodes only the byte-identical accepted resource. */
    public static Mc263WoodlandMansionGrammar decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Mansion grammar bytes");
        if (!FILE_SHA256.equals(sha256(bytes))) {
            throw new IllegalArgumentException("Woodland Mansion grammar file identity drift");
        }
        final JsonNode root;
        try {
            root = JSON.readTree(bytes);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("malformed Woodland Mansion grammar JSON", error);
        }
        requireObjectKeys(root, "root", Set.of("schema", "producer", "source", "policy",
                "stateCatalog", "processorReceipt", "aggregate", "templates", "probes",
                "grammarSha256"));
        require(root.path("schema").asInt(-1) == 1, "Mansion grammar schema drift");
        require(PRODUCER.equals(root.path("producer").asText()), "Mansion producer drift");
        require(GRAMMAR_SHA256.equals(root.path("grammarSha256").asText()),
                "Mansion grammar identity drift");
        JsonNode source = root.path("source");
        require(ORACLE_SHA256.equals(source.path("oracleSha256").asText()),
                "Mansion oracle identity drift");
        require(ORACLE_SOURCE_SHA256.equals(source.path("oracleProducer")
                .path("sourceSha256").asText()), "Mansion oracle source drift");
        require(TEMPLATE_ORDERED_SHA256.equals(source.path("templateOrderedReceiptSha256").asText()),
                "Mansion template receipt drift");
        validatePolicy(root.path("policy"));
        validateProcessor(root.path("processorReceipt"));

        ArrayList<String> states = new ArrayList<>();
        String previous = null;
        for (JsonNode value : root.path("stateCatalog")) {
            String state = value.asText();
            require(STATE.matcher(state).matches(), "malformed Mansion exact state: " + state);
            require(previous == null || previous.compareTo(state) < 0,
                    "Mansion state catalog order/cardinality drift");
            states.add(state); previous = state;
        }
        require(states.size() == 277, "Mansion state catalog cardinality drift");
        Set<String> knownStates = Set.copyOf(states);

        LinkedHashMap<String, Template> templates = new LinkedHashMap<>();
        long blocks = 0; int runs = 0, data = 0;
        for (JsonNode value : root.path("templates")) {
            Template template = decodeTemplate(value, knownStates);
            require(templates.put(template.id(), template) == null,
                    "duplicate Mansion template " + template.id());
            blocks += template.blockCount(); runs += template.runCount(); data += template.dataCount();
        }
        require(templates.size() == 73 && blocks == 66_365 && runs == 11_362 && data == 118,
                "Mansion template aggregate drift");
        JsonNode aggregate = root.path("aggregate");
        require(aggregate.path("templateCount").asInt() == 73
                        && aggregate.path("blockCount").asInt() == 66_365
                        && aggregate.path("runCount").asInt() == 11_362
                        && aggregate.path("dataCount").asInt() == 118,
                "Mansion declared aggregate drift");

        ArrayList<Probe> probes = new ArrayList<>();
        for (JsonNode value : root.path("probes")) probes.add(decodeProbe(value, templates));
        require(probes.size() == 2 && probes.get(0).pieces().size() == 600
                        && probes.get(1).pieces().size() == 497,
                "Mansion probe/piece aggregate drift");
        return new Mc263WoodlandMansionGrammar(states, templates, probes);
    }

    public List<String> stateCatalog() { return stateCatalog; }
    public Map<String, Template> templates() { return templates; }
    public List<Probe> probes() { return probes; }
    public SettlementEvidence requireSettlementEvidence() {
        if (settlementEvidence == null) {
            throw new IllegalStateException("Mansion settlement evidence was not attached");
        }
        return settlementEvidence;
    }
    public PersistedNbtEvidence requirePersistedNbtEvidence() {
        if (persistedNbtEvidence == null) {
            throw new IllegalStateException("Mansion persisted-NBT evidence was not attached");
        }
        return persistedNbtEvidence;
    }
    public Template requireTemplate(String id) {
        Template value = templates.get(id);
        if (value == null) throw new IllegalArgumentException("unknown Mansion template: " + id);
        return value;
    }

    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum Mirror { NONE, LEFT_RIGHT, FRONT_BACK }

    public record Pos(int x, int y, int z) {
        public Pos add(Pos other) {
            return new Pos(Math.addExact(x, other.x), Math.addExact(y, other.y),
                    Math.addExact(z, other.z));
        }
    }
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ,
                    "inverted Mansion bounding box");
        }
        public boolean contains(Pos p) {
            return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY
                    && p.z >= minZ && p.z <= maxZ;
        }
    }
    public record NbtReceipt(int binaryLength, String binarySha256) {
        public NbtReceipt {
            require(binaryLength > 0 && SHA.matcher(binarySha256).matches(),
                    "invalid Mansion NBT receipt");
        }
    }

    public record Chunk(int x, int z) { }
    public enum DestinationRelation { LOCAL, FOREIGN }

    public record NbtNamedTag(String name, NbtTypeTree tag) {
        public NbtNamedTag { Objects.requireNonNull(name); Objects.requireNonNull(tag); }
    }
    public record NbtTypeTree(int typeId, String type, List<NbtNamedTag> entries,
            List<NbtTypeTree> values, String snbtValue) {
        public NbtTypeTree {
            require(typeId >= 1 && typeId <= 12, "invalid Mansion NBT type id");
            Objects.requireNonNull(type); entries = List.copyOf(entries); values = List.copyOf(values);
        }
    }
    public static final class CanonicalNbt {
        private final byte[] binary;
        private final String binarySha256;
        private final NbtTypeTree orderedTypedTree;
        CanonicalNbt(byte[] binary, String binarySha256, NbtTypeTree orderedTypedTree) {
            Objects.requireNonNull(binary); Objects.requireNonNull(binarySha256);
            Objects.requireNonNull(orderedTypedTree);
            require(binary.length >= 4 && binary.length <= 1_048_576 && binary[0] == 10,
                    "invalid Mansion canonical NBT binary");
            require(SHA.matcher(binarySha256).matches() && binarySha256.equals(sha256(binary)),
                    "Mansion canonical NBT digest drift");
            require(orderedTypedTree.typeId() == 10 && "COMPOUND".equals(orderedTypedTree.type()),
                    "Mansion canonical NBT root drift");
            this.binary = binary.clone(); this.binarySha256 = binarySha256;
            this.orderedTypedTree = orderedTypedTree;
        }
        public byte[] binary() { return binary.clone(); }
        public int binaryLength() { return binary.length; }
        public String binarySha256() { return binarySha256; }
        public NbtTypeTree orderedTypedTree() { return orderedTypedTree; }
    }

    public record XoroshiroState(int worldgenCount, long seedLo, long seedHi,
            List<Long> continuationNextLong) {
        public XoroshiroState {
            require(worldgenCount >= 0, "negative Mansion Xoroshiro count");
            continuationNextLong = List.copyOf(continuationNextLong);
            require(continuationNextLong.isEmpty() || continuationNextLong.size() == 8,
                    "Mansion Xoroshiro continuation drift");
        }
    }
    public record PlacementDraw(int ordinal, Pos position, Chunk destinationChunk,
            DestinationRelation destinationRelation, int sourceOperationOrdinal,
            String sourceOperation, long signedSeed, XoroshiroState before, XoroshiroState after) {
        public PlacementDraw {
            require(ordinal >= 0 && sourceOperationOrdinal >= 0, "invalid Mansion placement draw ordinal");
            Objects.requireNonNull(position); Objects.requireNonNull(destinationChunk);
            Objects.requireNonNull(destinationRelation); Objects.requireNonNull(sourceOperation);
            Objects.requireNonNull(before); Objects.requireNonNull(after);
        }
    }
    public record PlacementRng(long worldSeed, Chunk chunk, long decorationSeed,
            int structureIndex, int generationStepOrdinal, XoroshiroState initial,
            List<PlacementDraw> draws, XoroshiroState finished) {
        public PlacementRng {
            require(structureIndex >= 0 && generationStepOrdinal >= 0,
                    "invalid Mansion placement RNG identity");
            Objects.requireNonNull(chunk); Objects.requireNonNull(initial); Objects.requireNonNull(finished);
            draws = List.copyOf(draws);
        }
    }
    public sealed interface RandomScalar permits IntScalar, FloatScalar, DoubleScalar { }
    public record IntScalar(int value) implements RandomScalar { }
    public record FloatScalar(int rawBits) implements RandomScalar {
        public float value() { return Float.intBitsToFloat(rawBits); }
    }
    public record DoubleScalar(long rawBits) implements RandomScalar {
        public double value() { return Double.longBitsToDouble(rawBits); }
    }
    /** Rejected P2AB Legacy48 trace retained only as historical evidence; never production RNG authority. */
    public record RejectedLegacy48State(String implementationClass, long state48,
            List<Long> continuationNextLong) {
        public RejectedLegacy48State {
            Objects.requireNonNull(implementationClass);
            require(state48 >= 0 && state48 < (1L << 48), "invalid Mansion Legacy48 state");
            continuationNextLong = List.copyOf(continuationNextLong);
            require(continuationNextLong.size() == 8, "Mansion Legacy48 continuation drift");
        }
    }
    public record RejectedServerRandomDraw(int ordinal, String operation, List<RandomScalar> arguments,
            RandomScalar result, long state48Before, long state48After) {
        public RejectedServerRandomDraw {
            require(ordinal >= 0, "negative Mansion ServerLevel RNG ordinal");
            Objects.requireNonNull(operation); arguments = List.copyOf(arguments); Objects.requireNonNull(result);
        }
    }
    public record RejectedServerLevelRng(long seed, RejectedLegacy48State before,
            List<RejectedServerRandomDraw> draws, RejectedLegacy48State after) {
        public RejectedServerLevelRng {
            Objects.requireNonNull(before); Objects.requireNonNull(after); draws = List.copyOf(draws);
        }
    }
    public record OperationReceipt(int operationCount, Map<String, Integer> operationCounts,
            String orderedOperationSha256) {
        public OperationReceipt {
            require(operationCount >= 0 && SHA.matcher(orderedOperationSha256).matches(),
                    "invalid Mansion operation receipt");
            operationCounts = Map.copyOf(operationCounts);
        }
    }
    public record BlockEntityEvidence(int ordinal, Pos position, Chunk destinationChunk,
            DestinationRelation destinationRelation, String blockIdentity, String blockState,
            String blockEntityType, Integer placementDrawOrdinal, CanonicalNbt canonicalNbt) {
        public BlockEntityEvidence {
            require(ordinal >= 0, "negative Mansion BENT ordinal");
            Objects.requireNonNull(position); Objects.requireNonNull(destinationChunk);
            Objects.requireNonNull(destinationRelation); Objects.requireNonNull(blockIdentity);
            Objects.requireNonNull(blockState); Objects.requireNonNull(blockEntityType);
            Objects.requireNonNull(canonicalNbt);
        }
    }
    public record LootEvidence(int ordinal, Pos position, Chunk destinationChunk,
            DestinationRelation destinationRelation, String table, long signedSeed,
            int bentOrdinal, int placementDrawOrdinal) {
        public LootEvidence {
            require(ordinal >= 0 && bentOrdinal >= 0 && placementDrawOrdinal >= 0,
                    "invalid Mansion LOOT ordinal");
            require("minecraft:chests/woodland_mansion".equals(table), "unknown Mansion loot table");
        }
    }
    public record EntityEvidence(int ordinal, String marker, Pos markerPosition,
            Chunk destinationChunk, DestinationRelation destinationRelation, String entityKey,
            String spawnReason, String runtimeSpawnReason, List<Long> positionBits,
            List<Integer> yawPitchBits, List<Long> velocityBits, Optional<String> lootTable,
            OptionalLong lootSeed, CanonicalNbt canonicalPayload) {
        public EntityEvidence {
            require(ordinal >= 0 && Set.of("Mage", "Warrior", "Group of Allays").contains(marker),
                    "invalid Mansion ENTS marker");
            Objects.requireNonNull(markerPosition); Objects.requireNonNull(destinationChunk);
            Objects.requireNonNull(destinationRelation); Objects.requireNonNull(entityKey);
            Objects.requireNonNull(spawnReason); Objects.requireNonNull(runtimeSpawnReason);
            positionBits = List.copyOf(positionBits); yawPitchBits = List.copyOf(yawPitchBits);
            velocityBits = List.copyOf(velocityBits); Objects.requireNonNull(lootTable);
            Objects.requireNonNull(lootSeed); Objects.requireNonNull(canonicalPayload);
            require(positionBits.size() == 3 && yawPitchBits.size() == 2 && velocityBits.size() == 3,
                    "Mansion ENTS numeric field cardinality drift");
        }
        public double x() { return Double.longBitsToDouble(positionBits.get(0)); }
        public double y() { return Double.longBitsToDouble(positionBits.get(1)); }
        public double z() { return Double.longBitsToDouble(positionBits.get(2)); }
        public float yaw() { return Float.intBitsToFloat(yawPitchBits.get(0)); }
        public float pitch() { return Float.intBitsToFloat(yawPitchBits.get(1)); }
        public double velocityX() { return Double.longBitsToDouble(velocityBits.get(0)); }
        public double velocityY() { return Double.longBitsToDouble(velocityBits.get(1)); }
        public double velocityZ() { return Double.longBitsToDouble(velocityBits.get(2)); }
    }
    public record SettlementSidecars(List<LootEvidence> loot,
            List<BlockEntityEvidence> blockEntities, List<EntityEvidence> entities) {
        public SettlementSidecars {
            loot = List.copyOf(loot); blockEntities = List.copyOf(blockEntities);
            entities = List.copyOf(entities);
        }
    }
    public record SettlementClip(Chunk chunk, Box clip, PlacementRng placementRng,
            RejectedServerLevelRng rejectedServerLevelRng, OperationReceipt operationReceipt,
            SettlementSidecars sidecars) { }
    public record SettlementProbe(Request request, List<SettlementClip> clips) {
        public SettlementProbe { clips = List.copyOf(clips); }
    }
    public record TemplateBentClosure(String templateId, int templateOrdinal, Pos testOrigin,
            Chunk ownerChunk, Box boundingBox, int expectedBent, PlacementRng placementRng,
            OperationReceipt operationReceipt, SettlementSidecars sidecars) {
        public TemplateBentClosure {
            require(templateOrdinal >= 0 && expectedBent > 0, "invalid Mansion BENT closure row");
        }
    }
    public record EntityCanonicalization(String rule, int repeatedOfficialRuns,
            int entityCountPerRun, boolean allRuntimeUuidsDiffer, String canonicalProjectionSha256) {
        public EntityCanonicalization {
            require("remove-top-level-UUID-INT_ARRAY-only".equals(rule)
                            && repeatedOfficialRuns == 2 && entityCountPerRun > 0
                            && allRuntimeUuidsDiffer && SHA.matcher(canonicalProjectionSha256).matches(),
                    "invalid Mansion entity canonicalization proof");
        }
    }
    public record SettlementEvidence(List<TemplateBentClosure> postPlacementBentClosure,
            List<SettlementProbe> probes, EntityCanonicalization entityCanonicalization,
            String evidenceSha256) {
        public SettlementEvidence {
            postPlacementBentClosure = List.copyOf(postPlacementBentClosure); probes = List.copyOf(probes);
            Objects.requireNonNull(entityCanonicalization);
            require(SHA.matcher(evidenceSha256).matches(), "invalid Mansion settlement evidence digest");
        }
    }

    public record PersistedPiece(int ordinal, String templateId, Pos position, Rotation rotation,
            Mirror mirror, Box boundingBox, CanonicalNbt canonicalNbt) {
        public PersistedPiece { Objects.requireNonNull(canonicalNbt); }
    }
    public record ReferenceMutation(int ordinal, Chunk referenceChunk, int referencesBefore,
            int referencesAfter, boolean canBeReferencedBefore, boolean canBeReferencedAfter,
            int pieceCount, String orderedPieceNbtSha256Before, String orderedPieceNbtSha256After,
            boolean piecesByteIdentical) {
        public ReferenceMutation {
            Objects.requireNonNull(referenceChunk);
            require(ordinal == 0 && referencesBefore == 0 && referencesAfter == 1
                            && canBeReferencedBefore && !canBeReferencedAfter && pieceCount > 0
                            && piecesByteIdentical
                            && SHA.matcher(orderedPieceNbtSha256Before).matches()
                            && orderedPieceNbtSha256Before.equals(orderedPieceNbtSha256After),
                    "invalid Mansion reference mutation evidence");
        }
    }
    public record PersistedProbe(Request request, Pos generationStubPosition,
            List<PersistedPiece> pieces, CanonicalNbt structureStart,
            ReferenceMutation referenceMutation, CanonicalNbt mutableSuccessorAfterOneReference) {
        public PersistedProbe {
            pieces = List.copyOf(pieces); Objects.requireNonNull(request);
            Objects.requireNonNull(generationStubPosition); Objects.requireNonNull(structureStart);
            Objects.requireNonNull(referenceMutation);
            Objects.requireNonNull(mutableSuccessorAfterOneReference);
        }
    }
    public record PersistedNbtEvidence(List<PersistedProbe> probes, String evidenceSha256) {
        public PersistedNbtEvidence {
            probes = List.copyOf(probes);
            require(SHA.matcher(evidenceSha256).matches(),
                    "invalid Mansion persisted-NBT evidence digest");
        }
    }

    public sealed interface TypedValue permits MapValue, ListValue, ByteValue, IntValue,
            StringValue { }
    public record MapEntry(String name, TypedValue value) {
        public MapEntry { Objects.requireNonNull(name); Objects.requireNonNull(value); }
    }
    public record MapValue(List<MapEntry> entries) implements TypedValue {
        public MapValue { entries = List.copyOf(entries); }
    }
    public record ListValue(List<TypedValue> values) implements TypedValue {
        public ListValue { values = List.copyOf(values); }
    }
    public record ByteValue(byte value) implements TypedValue { }
    public record IntValue(int value) implements TypedValue { }
    public record StringValue(String value) implements TypedValue {
        public StringValue { Objects.requireNonNull(value); }
    }

    public sealed interface Semantic permits StructureMarker, EmptyContainer, ContainerItems,
            PatternedBanner, MobSpawner { }
    public record StructureMarker(String metadata) implements Semantic {
        public StructureMarker { require(MARKERS.contains(metadata), "unknown Mansion marker"); }
    }
    public record EmptyContainer(String blockEntityType) implements Semantic { }
    public record ContainerItems(String blockEntityType, TypedValue items) implements Semantic { }
    public record PatternedBanner(String blockEntityType, TypedValue patterns) implements Semantic { }
    public record MobSpawner(String blockEntityType, TypedValue spawnData) implements Semantic { }

    public sealed interface Command permits Run, Data {
        int ordinal();
        int stateIndex();
        int expandedCount();
    }
    public record Run(int ordinal, int stateIndex, Pos start, Pos delta, int count)
            implements Command {
        public Run { require(count > 0, "empty Mansion RUN"); }
        @Override public int expandedCount() { return count; }
    }
    public record Data(int ordinal, int stateIndex, Pos position, Semantic semantic)
            implements Command {
        public Data { Objects.requireNonNull(semantic); }
        @Override public int expandedCount() { return 1; }
    }
    public record Template(String id, Pos size, List<String> stateTable, List<Command> commands,
            int blockCount, int runCount, int dataCount, String grammarSha256,
            NbtReceipt templateNbtReceipt) {
        public Template {
            stateTable = List.copyOf(stateTable); commands = List.copyOf(commands);
        }
    }
    public record Marker(int ordinal, String metadata, Pos position) {
        public Marker { require(ordinal >= 0 && MARKERS.contains(metadata), "invalid Mansion marker"); }
    }
    public record Piece(int ordinal, String templateId, Pos position, Rotation rotation,
            Mirror mirror, Box boundingBox, List<Marker> markers, NbtReceipt pieceNbtReceipt) {
        public Piece { markers = List.copyOf(markers); }
    }
    public record HeightQuery(int ordinal, String heightmap, int x, int z, int oracleResult) { }
    public record Request(String structureKey, long worldSeed, int chunkX, int chunkZ) { }
    public record Probe(Request request, Pos generationStubPosition, List<HeightQuery> heightQueries,
            List<Piece> pieces, long layoutState48, long layoutWorldgenCount,
            List<Long> layoutContinuation, String pieceGridSha256,
            String layoutDrawReceiptSha256, NbtReceipt startNbtReceipt) {
        public Probe {
            heightQueries = List.copyOf(heightQueries); pieces = List.copyOf(pieces);
            layoutContinuation = List.copyOf(layoutContinuation);
        }
    }

    private static PersistedNbtEvidence decodePersistedNbtEvidence(byte[] bytes,
            List<Probe> acceptedProbes) {
        Objects.requireNonNull(bytes, "Mansion persisted-NBT evidence bytes");
        require(PERSISTED_NBT_EVIDENCE_FILE_SHA256.equals(sha256(bytes)),
                "Woodland Mansion persisted-NBT evidence file identity drift");
        final JsonNode root;
        try { root = JSON.readTree(bytes); }
        catch (RuntimeException error) {
            throw new IllegalArgumentException("malformed Woodland Mansion persisted-NBT evidence JSON", error);
        }
        requireObjectKeys(root, "persisted-NBT root", Set.of(
                "evidenceSha256", "nbtSchema", "policy", "probes", "producer", "schema", "source"));
        require(root.path("schema").asInt(-1) == 1
                        && "gameexpert-mc263-woodland-mansion-persisted-nbt-evidence-v1"
                                .equals(root.path("producer").asText())
                        && PERSISTED_NBT_EVIDENCE_SHA256.equals(root.path("evidenceSha256").asText()),
                "Mansion persisted-NBT schema/identity drift");
        validatePersistedSource(root.path("source"));
        validatePersistedNbtSchema(root.path("nbtSchema"));
        JsonNode policy = root.path("policy");
        requireObjectKeys(policy, "persisted-NBT policy", Set.of("pieceAndStartNbt",
                "mutableSuccessor", "referenceEncounterOrder", "productionCoordinateLookup",
                "rawMojangTemplateNbt"));
        require("canonical-binary-preimage".equals(policy.path("pieceAndStartNbt").asText())
                        && "official-StructureStart.addReference-once"
                                .equals(policy.path("mutableSuccessor").asText())
                        && "single-ordinal-0-start-chunk"
                                .equals(policy.path("referenceEncounterOrder").asText())
                        && "forbidden".equals(policy.path("productionCoordinateLookup").asText())
                        && "excluded".equals(policy.path("rawMojangTemplateNbt").asText()),
                "Mansion persisted-NBT policy drift");
        require(root.path("probes").isArray() && root.path("probes").size() == acceptedProbes.size(),
                "Mansion persisted-NBT probe cardinality drift");
        ArrayList<PersistedProbe> probes = new ArrayList<>();
        for (int index = 0; index < acceptedProbes.size(); index++) {
            Probe accepted = acceptedProbes.get(index); JsonNode value = root.path("probes").get(index);
            requireObjectKeys(value, "persisted-NBT probe", Set.of("request",
                    "generationStubPosition", "pieceCount", "pieces", "structureStart",
                    "referenceMutation", "mutableSuccessorAfterOneReference"));
            Request request = request(value.path("request"));
            require(request.equals(accepted.request()), "Mansion persisted-NBT substituted request");
            Pos stub = pos(value.path("generationStubPosition"), "persisted-NBT stub");
            require(stub.equals(accepted.generationStubPosition()),
                    "Mansion persisted-NBT substituted start coordinates");
            require(value.path("pieceCount").asInt(-1) == accepted.pieces().size()
                            && value.path("pieces").size() == accepted.pieces().size(),
                    "Mansion persisted-NBT piece count drift");
            ArrayList<PersistedPiece> pieces = new ArrayList<>();
            for (int pieceIndex = 0; pieceIndex < accepted.pieces().size(); pieceIndex++) {
                Piece expected = accepted.pieces().get(pieceIndex); JsonNode piece = value.path("pieces").get(pieceIndex);
                requireObjectKeys(piece, "persisted-NBT piece", Set.of("ordinal", "template",
                        "position", "rotation", "mirror", "boundingBox", "canonicalNbt"));
                PersistedPiece decoded = new PersistedPiece(piece.path("ordinal").asInt(-1),
                        piece.path("template").asText(), pos(piece.path("position"), "persisted piece position"),
                        enumValue(Rotation.class, piece.path("rotation").asText(), "rotation"),
                        enumValue(Mirror.class, piece.path("mirror").asText(), "mirror"),
                        box(piece.path("boundingBox")), compactCanonicalNbt(piece.path("canonicalNbt")));
                require(decoded.ordinal() == pieceIndex && decoded.ordinal() == expected.ordinal()
                                && decoded.templateId().equals(expected.templateId())
                                && decoded.position().equals(expected.position())
                                && decoded.rotation() == expected.rotation() && decoded.mirror() == expected.mirror()
                                && decoded.boundingBox().equals(expected.boundingBox()),
                        "Mansion persisted-NBT piece identity/order drift");
                validatePersistedPieceNbt(decoded); pieces.add(decoded);
            }
            CanonicalNbt start = compactCanonicalNbt(value.path("structureStart"));
            CanonicalNbt successor = compactCanonicalNbt(value.path("mutableSuccessorAfterOneReference"));
            validateStartNbt(start, request, pieces, 0);
            validateStartNbt(successor, request, pieces, 1);
            validateReferenceDelta(start, successor);
            JsonNode mutation = value.path("referenceMutation");
            requireObjectKeys(mutation, "Mansion reference mutation", Set.of("ordinal",
                    "referenceChunk", "referencesBefore", "referencesAfter", "canBeReferencedBefore",
                    "canBeReferencedAfter", "pieceCount", "orderedPieceNbtSha256Before",
                    "orderedPieceNbtSha256After", "piecesByteIdentical"));
            ReferenceMutation reference = new ReferenceMutation(mutation.path("ordinal").asInt(-1),
                    chunk(mutation.path("referenceChunk"), "reference chunk"),
                    mutation.path("referencesBefore").asInt(-1), mutation.path("referencesAfter").asInt(-1),
                    mutation.path("canBeReferencedBefore").asBoolean(false),
                    mutation.path("canBeReferencedAfter").asBoolean(true), mutation.path("pieceCount").asInt(-1),
                    mutation.path("orderedPieceNbtSha256Before").asText(),
                    mutation.path("orderedPieceNbtSha256After").asText(),
                    mutation.path("piecesByteIdentical").asBoolean(false));
            String orderedSha = orderedPieceNbtSha256(pieces);
            require(reference.referenceChunk().equals(new Chunk(request.chunkX(), request.chunkZ()))
                            && reference.pieceCount() == pieces.size()
                            && orderedSha.equals(reference.orderedPieceNbtSha256Before()),
                    "Mansion reference chunk/order/piece invariance drift");
            probes.add(new PersistedProbe(request, stub, pieces, start, reference, successor));
        }
        return new PersistedNbtEvidence(probes, root.path("evidenceSha256").asText());
    }

    private static void validatePersistedNbtSchema(JsonNode schema) {
        requireObjectKeys(schema, "persisted-NBT schema", Set.of("piece", "structureStart"));
        JsonNode piece = schema.path("piece");
        requireObjectKeys(piece, "persisted piece NBT schema", Set.of("tagOrder", "typeIds", "defaults", "derived"));
        requireStringArray(piece.path("tagOrder"), List.of(
                "BB", "Rot", "id", "TPY", "Mi", "GD", "TPX", "O", "TPZ", "Template"),
                "Mansion persisted piece tag order");
        requireIntArray(piece.path("typeIds"), List.of(11, 8, 8, 3, 8, 3, 3, 3, 3, 8),
                "Mansion persisted piece tag types");
        JsonNode defaults = piece.path("defaults");
        requireObjectKeys(defaults, "persisted piece defaults", Set.of("id", "GD", "O"));
        require("minecraft:wmp".equals(defaults.path("id").asText())
                        && defaults.path("GD").asInt(-1) == 0 && defaults.path("O").asInt(-1) == 2,
                "Mansion persisted piece default drift");
        JsonNode derived = piece.path("derived");
        requireObjectKeys(derived, "persisted piece derived fields", Set.of(
                "BB", "Rot", "TPY", "Mi", "TPX", "TPZ", "Template"));
        require("boundingBox".equals(derived.path("BB").asText())
                        && "rotation".equals(derived.path("Rot").asText())
                        && "position.y".equals(derived.path("TPY").asText())
                        && "mirror".equals(derived.path("Mi").asText())
                        && "position.x".equals(derived.path("TPX").asText())
                        && "position.z".equals(derived.path("TPZ").asText())
                        && "template basename".equals(derived.path("Template").asText()),
                "Mansion persisted piece derivation drift");

        JsonNode start = schema.path("structureStart");
        requireObjectKeys(start, "persisted start NBT schema", Set.of("tagOrder", "typeIds", "defaults", "derived"));
        requireStringArray(start.path("tagOrder"), List.of("references", "ChunkZ", "id", "Children", "ChunkX"),
                "Mansion persisted start tag order");
        requireIntArray(start.path("typeIds"), List.of(3, 3, 8, 9, 3),
                "Mansion persisted start tag types");
        JsonNode startDefaults = start.path("defaults");
        requireObjectKeys(startDefaults, "persisted start defaults", Set.of("id"));
        require("minecraft:mansion".equals(startDefaults.path("id").asText()),
                "Mansion persisted start default drift");
        JsonNode startDerived = start.path("derived");
        requireObjectKeys(startDerived, "persisted start derived fields", Set.of(
                "references", "ChunkZ", "Children", "ChunkX"));
        require("0 fresh; 1 successor".equals(startDerived.path("references").asText())
                        && "request.chunkZ".equals(startDerived.path("ChunkZ").asText())
                        && "ordered piece canonical NBT payloads".equals(startDerived.path("Children").asText())
                        && "request.chunkX".equals(startDerived.path("ChunkX").asText()),
                "Mansion persisted start derivation drift");
    }

    private static void requireStringArray(JsonNode value, List<String> expected, String label) {
        require(value.isArray() && value.size() == expected.size(), label + " cardinality drift");
        for (int index = 0; index < expected.size(); index++)
            require(expected.get(index).equals(value.get(index).asText()), label + " drift at " + index);
    }

    private static void requireIntArray(JsonNode value, List<Integer> expected, String label) {
        require(value.isArray() && value.size() == expected.size(), label + " cardinality drift");
        for (int index = 0; index < expected.size(); index++)
            require(expected.get(index) == value.get(index).asInt(Integer.MIN_VALUE), label + " drift at " + index);
    }

    private static void validatePersistedSource(JsonNode source) {
        requireObjectKeys(source, "persisted-NBT source", Set.of("oracleProducer", "oracleSha256",
                "runtime", "templateOrderedReceiptSha256"));
        JsonNode producer = source.path("oracleProducer");
        requireObjectKeys(producer, "persisted-NBT oracle producer", Set.of("contractSha256", "id", "sourceSha256"));
        require("gameexpert-official-26.3-woodland-mansion-oracle-v3".equals(producer.path("id").asText())
                        && PERSISTED_NBT_ORACLE_CONTRACT_SHA256.equals(producer.path("contractSha256").asText())
                        && PERSISTED_NBT_ORACLE_SOURCE_SHA256.equals(producer.path("sourceSha256").asText())
                        && PERSISTED_NBT_ORACLE_SHA256.equals(source.path("oracleSha256").asText())
                        && TEMPLATE_ORDERED_SHA256.equals(source.path("templateOrderedReceiptSha256").asText()),
                "Mansion persisted-NBT oracle/source identity drift");
        JsonNode runtime = source.path("runtime");
        requireObjectKeys(runtime, "persisted-NBT runtime", Set.of("classSha256", "dataPack",
                "innerServerSha1", "javaImplementorVersion", "javaReleaseSha256", "javaRuntimeVersion",
                "javaVersion", "outerServerSha1", "resourcePack", "versionId", "worldVersion"));
        require("26.3-snapshot-7".equals(runtime.path("versionId").asText())
                        && runtime.path("worldVersion").asInt(-1) == 5009
                        && runtime.path("dataPack").asInt(-1) == 115
                        && runtime.path("resourcePack").asInt(-1) == 95
                        && "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61".equals(runtime.path("outerServerSha1").asText())
                        && "2f1ef79f3cad10138ad18da45b265fe656624026".equals(runtime.path("innerServerSha1").asText())
                        && "25.0.1".equals(runtime.path("javaVersion").asText())
                        && "25.0.1+8-LTS".equals(runtime.path("javaRuntimeVersion").asText())
                        && "Corretto-25.0.1.8.1".equals(runtime.path("javaImplementorVersion").asText())
                        && PERSISTED_NBT_JAVA_RELEASE_SHA256.equals(runtime.path("javaReleaseSha256").asText()),
                "Mansion persisted-NBT runtime/JAR/Java identity drift");
        JsonNode classes = runtime.path("classSha256");
        requireObjectKeys(classes, "persisted-NBT class identities", Set.of(
                "net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionStructure.class",
                "net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.class",
                "net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$WoodlandMansionPiece.class",
                "net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$MansionGrid.class",
                "net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$MansionPiecePlacer.class"));
        require("2ecd8faad13cf944b748e2116ecc51490acb8fcd9c820d5098003777f2cfc82a".equals(
                        classes.path("net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionStructure.class").asText())
                        && "28b220239843ef43887dcd96bdb61b66c0773b9efdf1215b50700e4cd83e23cd".equals(
                                classes.path("net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.class").asText())
                        && "baa3eb44c97870e6e456c0c19dc63f67b5ed7ed26d9aed590d92c268bd507007".equals(
                                classes.path("net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$WoodlandMansionPiece.class").asText())
                        && "b228f1263f72db97458f91d86c756d4ca43065ccbe8843172df65357f6bb6628".equals(
                                classes.path("net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$MansionGrid.class").asText())
                        && "62206572a4e3d031afe437b4318af304d35f964bffbc3fbf344509ebd6f4be5e".equals(
                                classes.path("net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces$MansionPiecePlacer.class").asText()),
                "Mansion persisted-NBT class identity drift");
    }

    private static Request request(JsonNode value) {
        requireObjectKeys(value, "Mansion persisted request", Set.of("chunkX", "chunkZ",
                "structureKey", "worldSeedI64"));
        return new Request(value.path("structureKey").asText(),
                parseLong(value.path("worldSeedI64").asText(), "persisted world seed"),
                value.path("chunkX").asInt(), value.path("chunkZ").asInt());
    }

    private static void validatePersistedPieceNbt(PersistedPiece piece) {
        NbtTypeTree tree = piece.canonicalNbt().orderedTypedTree();
        requireTagOrder(tree, List.of("BB", "Rot", "id", "TPY", "Mi", "GD", "TPX", "O", "TPZ", "Template"),
                List.of(11, 8, 8, 3, 8, 3, 3, 3, 3, 8), "Mansion piece NBT");
        require(tag(tree, "BB").snbtValue().equals("[I;" + piece.boundingBox().minX() + ","
                        + piece.boundingBox().minY() + "," + piece.boundingBox().minZ() + ","
                        + piece.boundingBox().maxX() + "," + piece.boundingBox().maxY() + ","
                        + piece.boundingBox().maxZ() + "]")
                        && tag(tree, "Rot").snbtValue().equals("\"" + piece.rotation().name() + "\"")
                        && tag(tree, "id").snbtValue().equals("\"minecraft:wmp\"")
                        && tag(tree, "TPY").snbtValue().equals(Integer.toString(piece.position().y()))
                        && tag(tree, "Mi").snbtValue().equals("\"" + piece.mirror().name() + "\"")
                        && tag(tree, "GD").snbtValue().equals("0")
                        && tag(tree, "TPX").snbtValue().equals(Integer.toString(piece.position().x()))
                        && tag(tree, "O").snbtValue().equals("2")
                        && tag(tree, "TPZ").snbtValue().equals(Integer.toString(piece.position().z()))
                        && tag(tree, "Template").snbtValue().equals("\""
                                + piece.templateId().substring("minecraft:woodland_mansion/".length()) + "\""),
                "Mansion piece NBT typed value/default drift");
    }

    private static void validateStartNbt(CanonicalNbt nbt, Request request,
            List<PersistedPiece> pieces, int references) {
        NbtTypeTree tree = nbt.orderedTypedTree();
        requireTagOrder(tree, List.of("references", "ChunkZ", "id", "Children", "ChunkX"),
                List.of(3, 3, 8, 9, 3), "Mansion structure-start NBT");
        require(tag(tree, "references").snbtValue().equals(Integer.toString(references))
                        && tag(tree, "ChunkZ").snbtValue().equals(Integer.toString(request.chunkZ()))
                        && tag(tree, "id").snbtValue().equals("\"minecraft:mansion\"")
                        && tag(tree, "ChunkX").snbtValue().equals(Integer.toString(request.chunkX())),
                "Mansion structure-start NBT identity/reference drift");
        NbtTypeTree children = tag(tree, "Children");
        require(children.values().size() == pieces.size(), "Mansion structure-start child count drift");
        for (int index = 0; index < pieces.size(); index++) {
            require(children.values().get(index).equals(pieces.get(index).canonicalNbt().orderedTypedTree()),
                    "Mansion structure-start child order/typed NBT drift");
        }
    }

    private static void validateReferenceDelta(CanonicalNbt start, CanonicalNbt successor) {
        byte[] before = start.binary(); byte[] after = successor.binary();
        require(before.length == after.length, "Mansion mutable successor length drift");
        int differences = 0; int changed = -1;
        for (int index = 0; index < before.length; index++) {
            if (before[index] != after[index]) { differences++; changed = index; }
        }
        require(differences == 1 && before[changed] == 0 && after[changed] == 1,
                "Mansion mutable successor changed bytes outside references 0->1");
        List<NbtNamedTag> a = start.orderedTypedTree().entries();
        List<NbtNamedTag> b = successor.orderedTypedTree().entries();
        require(a.size() == b.size(), "Mansion mutable successor typed tag count drift");
        for (int index = 0; index < a.size(); index++) {
            if ("references".equals(a.get(index).name())) continue;
            require(a.get(index).equals(b.get(index)),
                    "Mansion mutable successor changed typed fields other than references");
        }
    }

    private static String orderedPieceNbtSha256(List<PersistedPiece> pieces) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (PersistedPiece piece : pieces) {
                byte[] binary = piece.canonicalNbt().binary(); int length = binary.length;
                digest.update((byte) (length >>> 24)); digest.update((byte) (length >>> 16));
                digest.update((byte) (length >>> 8)); digest.update((byte) length); digest.update(binary);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void requireTagOrder(NbtTypeTree tree, List<String> names, List<Integer> types,
            String label) {
        require(tree.entries().size() == names.size(), label + " tag count drift");
        for (int index = 0; index < names.size(); index++) {
            NbtNamedTag entry = tree.entries().get(index);
            require(entry.name().equals(names.get(index)) && entry.tag().typeId() == types.get(index),
                    label + " tag order/type drift");
        }
    }

    private static NbtTypeTree tag(NbtTypeTree tree, String name) {
        for (NbtNamedTag entry : tree.entries()) if (entry.name().equals(name)) return entry.tag();
        throw new IllegalArgumentException("Mansion NBT missing tag " + name);
    }

    private static SettlementEvidence decodeSettlementEvidence(byte[] bytes,
            Map<String, Template> templates) {
        Objects.requireNonNull(bytes, "Mansion settlement evidence bytes");
        require(SETTLEMENT_EVIDENCE_FILE_SHA256.equals(sha256(bytes)),
                "Woodland Mansion settlement evidence file identity drift");
        final JsonNode root;
        try { root = JSON.readTree(bytes); }
        catch (RuntimeException error) {
            throw new IllegalArgumentException("malformed Woodland Mansion settlement evidence JSON", error);
        }
        requireObjectKeys(root, "settlement evidence root", Set.of("schema", "producer", "source",
                "policy", "postPlacementBentClosure", "probes", "entityCanonicalization",
                "evidenceSha256"));
        require(root.path("schema").asInt(-1) == 2, "Mansion settlement evidence schema drift");
        require("gameexpert-mc263-woodland-mansion-settlement-evidence-v2"
                        .equals(root.path("producer").asText()),
                "Mansion settlement evidence producer drift");
        require(SETTLEMENT_EVIDENCE_SHA256.equals(root.path("evidenceSha256").asText()),
                "Mansion settlement evidence digest drift");
        JsonNode source = root.path("source");
        requireObjectKeys(source, "settlement evidence source", Set.of("oracleProducer",
                "oracleSha256", "templateOrderedReceiptSha256", "generatorSourceSha256"));
        JsonNode oracle = source.path("oracleProducer");
        require("gameexpert-official-26.3-woodland-mansion-oracle-v2".equals(oracle.path("id").asText())
                        && "3390194b6d11f31ab28f397f7330ff1cae47026f01dce3d5e5bf256e4dbad198"
                                .equals(oracle.path("contractSha256").asText())
                        && SETTLEMENT_ORACLE_SOURCE_SHA256.equals(oracle.path("sourceSha256").asText())
                        && SETTLEMENT_ORACLE_SHA256.equals(source.path("oracleSha256").asText())
                        && TEMPLATE_ORDERED_SHA256.equals(source.path("templateOrderedReceiptSha256").asText())
                        && SETTLEMENT_GENERATOR_SOURCE_SHA256.equals(source.path("generatorSourceSha256").asText()),
                "Mansion settlement evidence source identity drift");
        JsonNode policy = root.path("policy");
        requireObjectKeys(policy, "settlement evidence policy", Set.of("postPlacementNbt",
                "rawTemplateNbt", "productionCoordinateReplay", "unknownMarkerEntityTypeHash"));
        require("canonical-binary-preimage".equals(policy.path("postPlacementNbt").asText())
                        && "length-and-sha-only".equals(policy.path("rawTemplateNbt").asText())
                        && "forbidden".equals(policy.path("productionCoordinateReplay").asText())
                        && "fail-closed".equals(policy.path("unknownMarkerEntityTypeHash").asText()),
                "Mansion settlement evidence policy drift");

        ArrayList<TemplateBentClosure> closure = new ArrayList<>();
        int bentTotal = 0, drawTotal = 0, foreignTotal = 0;
        LinkedHashMap<String, Integer> typeCounts = new LinkedHashMap<>();
        for (JsonNode row : root.path("postPlacementBentClosure")) {
            TemplateBentClosure decoded = decodeBentClosure(row, templates);
            closure.add(decoded); bentTotal += decoded.sidecars().blockEntities().size();
            drawTotal += decoded.placementRng().draws().size();
            for (BlockEntityEvidence bent : decoded.sidecars().blockEntities()) {
                if (bent.destinationRelation() == DestinationRelation.FOREIGN) foreignTotal++;
                typeCounts.merge(bent.blockEntityType(), 1, Integer::sum);
            }
        }
        require(closure.size() == 11 && bentTotal == 80 && drawTotal == 73 && foreignTotal == 79,
                "Mansion BENT closure aggregate drift");
        require(typeCounts.equals(Map.of("minecraft:chest", 72, "minecraft:trapped_chest", 1,
                        "minecraft:banner", 6, "minecraft:mob_spawner", 1)),
                "Mansion BENT closure type histogram drift");

        ArrayList<SettlementProbe> probes = new ArrayList<>();
        for (JsonNode row : root.path("probes")) probes.add(decodeSettlementProbe(row));
        require(probes.size() == 2, "Mansion settlement probe cardinality drift");
        require(probes.get(0).request().equals(new Request("minecraft:mansion", 0L, 36, 24))
                        && probes.get(1).request().equals(new Request("minecraft:mansion", 0L, -1, -1)),
                "Mansion settlement probe order/identity drift");
        int clips = 0, loot = 0, entities = 0, serverDraws = 0;
        for (SettlementProbe probe : probes) {
            require(probe.clips().size() == 3, "Mansion settlement clip cardinality drift");
            for (SettlementClip clip : probe.clips()) {
                clips++; loot += clip.sidecars().loot().size(); entities += clip.sidecars().entities().size();
                serverDraws += clip.rejectedServerLevelRng().draws().size();
                require(clip.placementRng().worldSeed() == probe.request().worldSeed(),
                        "Mansion settlement clip world-seed drift");
            }
        }
        EntityCanonicalization canonicalization = decodeCanonicalization(root.path("entityCanonicalization"));
        require(clips == 6 && loot == 2 && entities == 14 && serverDraws == 35
                        && canonicalization.entityCountPerRun() == entities,
                "Mansion settlement LOOT/ENTS/RNG aggregate drift");
        return new SettlementEvidence(closure, probes, canonicalization,
                root.path("evidenceSha256").asText());
    }

    private static TemplateBentClosure decodeBentClosure(JsonNode value, Map<String, Template> templates) {
        requireObjectKeys(value, "BENT closure", Set.of("template", "templateOrdinal", "testOrigin",
                "ownerChunk", "boundingBox", "expectedBent", "placementRng", "operationReceipt", "sidecars"));
        String template = value.path("template").asText();
        require(templates.containsKey(template), "unknown Mansion BENT closure template");
        int ordinal = value.path("templateOrdinal").asInt(-1);
        require(ordinal >= 0 && ordinal < 73, "invalid Mansion BENT closure template ordinal");
        int base = Math.addExact(4096, Math.multiplyExact(ordinal, 128));
        Pos origin = pos(value.path("testOrigin"), "BENT closure test origin");
        require(origin.equals(new Pos(Math.addExact(base, 14), 120, 4110)),
                "Mansion BENT closure arithmetic origin drift");
        Chunk owner = chunk(value.path("ownerChunk"), "BENT closure owner");
        require(owner.equals(chunk(origin)), "Mansion BENT closure owner drift");
        PlacementRng placement = placementRng(value.path("placementRng"));
        require(placement.chunk().equals(owner) && placement.worldSeed() == 0L,
                "Mansion BENT closure placement identity drift");
        SettlementSidecars sidecars = sidecars(value.path("sidecars"), owner, placement);
        int expected = value.path("expectedBent").asInt(-1);
        require(expected == sidecars.blockEntities().size() && sidecars.loot().isEmpty()
                        && sidecars.entities().isEmpty(),
                "Mansion BENT closure sidecar cardinality drift");
        return new TemplateBentClosure(template, ordinal, origin, owner, box(value.path("boundingBox")),
                expected, placement, operationReceipt(value.path("operationReceipt")), sidecars);
    }

    private static SettlementProbe decodeSettlementProbe(JsonNode value) {
        requireObjectKeys(value, "settlement probe", Set.of("request", "clips"));
        JsonNode request = value.path("request");
        requireObjectKeys(request, "settlement request", Set.of("structureKey", "worldSeedI64",
                "chunkX", "chunkZ"));
        Request decoded = new Request(request.path("structureKey").asText(),
                parseLong(request.path("worldSeedI64").asText(), "settlement world seed"),
                request.path("chunkX").asInt(), request.path("chunkZ").asInt());
        require("minecraft:mansion".equals(decoded.structureKey()), "Mansion settlement request key drift");
        ArrayList<SettlementClip> clips = new ArrayList<>();
        for (JsonNode clip : value.path("clips")) clips.add(decodeSettlementClip(clip));
        return new SettlementProbe(decoded, clips);
    }

    private static SettlementClip decodeSettlementClip(JsonNode value) {
        requireObjectKeys(value, "settlement clip", Set.of("chunk", "clip", "placementRng",
                "serverLevelRng", "operationReceipt", "sidecars"));
        Chunk owner = chunk(value.path("chunk"), "settlement clip chunk");
        Box clip = box(value.path("clip"));
        require(clip.minX() == owner.x() * 16 && clip.maxX() == owner.x() * 16 + 15
                        && clip.minZ() == owner.z() * 16 && clip.maxZ() == owner.z() * 16 + 15,
                "Mansion settlement clip/chunk drift");
        PlacementRng placement = placementRng(value.path("placementRng"));
        require(placement.chunk().equals(owner), "Mansion settlement placement owner drift");
        return new SettlementClip(owner, clip, placement, rejectedServerLevelRng(value.path("serverLevelRng")),
                operationReceipt(value.path("operationReceipt")),
                sidecars(value.path("sidecars"), owner, placement));
    }

    private static PlacementRng placementRng(JsonNode value) {
        requireObjectKeys(value, "placement RNG", Set.of("worldSeedI64", "chunk", "decorationSeedI64",
                "structureIndex", "generationStepOrdinal", "initial", "draws", "final"));
        Chunk owner = chunk(value.path("chunk"), "placement RNG chunk");
        XoroshiroState initial = xoroState(value.path("initial"), false);
        require(initial.worldgenCount() == 4, "Mansion placement RNG setup drift");
        ArrayList<PlacementDraw> draws = new ArrayList<>(); XoroshiroState previous = initial;
        for (JsonNode row : value.path("draws")) {
            requireObjectKeys(row, "placement draw", Set.of("ordinal", "position", "destinationChunk",
                    "destinationRelation", "sourceOperationOrdinal", "sourceOperation", "signedSeedI64",
                    "before", "after"));
            int ordinal = row.path("ordinal").asInt(-1); Pos position = pos(row.path("position"), "placement draw position");
            Chunk destination = chunk(row.path("destinationChunk"), "placement draw destination");
            DestinationRelation relation = relation(row.path("destinationRelation").asText());
            require(ordinal == draws.size() && destination.equals(chunk(position))
                            && relation == relation(owner, destination),
                    "Mansion placement draw order/destination drift");
            XoroshiroState before = xoroState(row.path("before"), false);
            XoroshiroState after = xoroState(row.path("after"), false);
            int sourceOrdinal = row.path("sourceOperationOrdinal").asInt(-1);
            String sourceOperation = row.path("sourceOperation").asText();
            require(before.equals(previous) && after.worldgenCount() == before.worldgenCount() + 2
                            && sourceOrdinal >= 0
                            && sourceOperation.startsWith(sourceOrdinal + "|getBlockEntity|pos:["),
                    "Mansion placement draw encounter drift");
            draws.add(new PlacementDraw(ordinal, position, destination, relation, sourceOrdinal,
                    sourceOperation, parseLong(row.path("signedSeedI64").asText(), "placement seed"),
                    before, after));
            previous = after;
        }
        XoroshiroState finished = xoroState(value.path("final"), true);
        require(finished.worldgenCount() == initial.worldgenCount() + draws.size() * 2
                        && sameXoroBody(finished, previous),
                "Mansion placement RNG final state drift");
        return new PlacementRng(parseLong(value.path("worldSeedI64").asText(), "placement world seed"),
                owner, parseLong(value.path("decorationSeedI64").asText(), "decoration seed"),
                value.path("structureIndex").asInt(-1), value.path("generationStepOrdinal").asInt(-1),
                initial, draws, finished);
    }

    private static XoroshiroState xoroState(JsonNode value, boolean finished) {
        requireObjectKeys(value, "Xoroshiro state", finished
                ? Set.of("worldgenCount", "seedLoI64", "seedHiI64", "continuationNextLongI64")
                : Set.of("worldgenCount", "seedLoI64", "seedHiI64"));
        ArrayList<Long> continuation = new ArrayList<>();
        if (finished) for (JsonNode item : value.path("continuationNextLongI64"))
            continuation.add(parseLong(item.asText(), "Xoroshiro continuation"));
        return new XoroshiroState(value.path("worldgenCount").asInt(-1),
                parseLong(value.path("seedLoI64").asText(), "Xoroshiro seed lo"),
                parseLong(value.path("seedHiI64").asText(), "Xoroshiro seed hi"), continuation);
    }

    private static boolean sameXoroBody(XoroshiroState left, XoroshiroState right) {
        return left.worldgenCount() == right.worldgenCount() && left.seedLo() == right.seedLo()
                && left.seedHi() == right.seedHi();
    }

    private static RejectedServerLevelRng rejectedServerLevelRng(JsonNode value) {
        requireObjectKeys(value, "ServerLevel RNG", Set.of("seedI64", "before", "draws", "after"));
        RejectedLegacy48State before = rejectedLegacyState(value.path("before"));
        ArrayList<RejectedServerRandomDraw> draws = new ArrayList<>(); long state = before.state48();
        for (JsonNode row : value.path("draws")) {
            requireObjectKeys(row, "ServerLevel RNG draw", Set.of("ordinal", "operation", "arguments",
                    "result", "state48Before", "state48After"));
            int ordinal = row.path("ordinal").asInt(-1); String operation = row.path("operation").asText();
            require(ordinal == draws.size() && Set.of("triangle", "nextFloat", "nextInt").contains(operation),
                    "Mansion ServerLevel RNG operation/order drift");
            ArrayList<RandomScalar> arguments = new ArrayList<>();
            for (JsonNode argument : row.path("arguments")) arguments.add(randomScalar(argument));
            RandomScalar result = randomScalar(row.path("result"));
            if (operation.equals("triangle")) {
                require(arguments.size() == 2 && arguments.get(0) instanceof DoubleScalar
                                && arguments.get(1) instanceof DoubleScalar && result instanceof DoubleScalar,
                        "Mansion triangle RNG signature drift");
            } else if (operation.equals("nextFloat")) {
                require(arguments.isEmpty() && result instanceof FloatScalar,
                        "Mansion nextFloat RNG signature drift");
            } else {
                require(arguments.size() == 1 && arguments.get(0) instanceof IntScalar bound
                                && bound.value() == 3 && result instanceof IntScalar,
                        "Mansion nextInt RNG signature drift");
            }
            long beforeState = parseLong(row.path("state48Before").asText(), "ServerLevel RNG before");
            long afterState = parseLong(row.path("state48After").asText(), "ServerLevel RNG after");
            require(beforeState == state && afterState >= 0 && afterState < (1L << 48),
                    "Mansion ServerLevel RNG state linkage drift");
            draws.add(new RejectedServerRandomDraw(ordinal, operation, arguments, result, beforeState, afterState));
            state = afterState;
        }
        RejectedLegacy48State after = rejectedLegacyState(value.path("after"));
        require(after.state48() == state, "Mansion ServerLevel RNG final state drift");
        return new RejectedServerLevelRng(parseLong(value.path("seedI64").asText(), "ServerLevel RNG seed"),
                before, draws, after);
    }

    private static RejectedLegacy48State rejectedLegacyState(JsonNode value) {
        requireObjectKeys(value, "Legacy48 state", Set.of("class", "state48", "continuationNextLongI64"));
        ArrayList<Long> continuation = new ArrayList<>();
        for (JsonNode item : value.path("continuationNextLongI64"))
            continuation.add(parseLong(item.asText(), "Legacy48 continuation"));
        return new RejectedLegacy48State(value.path("class").asText(),
                parseLong(value.path("state48").asText(), "Legacy48 state"), continuation);
    }

    private static RandomScalar randomScalar(JsonNode value) {
        return switch (value.path("type").asText()) {
            case "INT" -> {
                requireObjectKeys(value, "INT RNG scalar", Set.of("type", "value"));
                yield new IntScalar(value.path("value").asInt());
            }
            case "FLOAT" -> {
                requireObjectKeys(value, "FLOAT RNG scalar", Set.of("type", "bitsU32", "hex"));
                int bits = parseUnsignedInt(value.path("bitsU32").asText(), "FLOAT bits");
                require(Float.toHexString(Float.intBitsToFloat(bits)).equals(value.path("hex").asText()),
                        "Mansion FLOAT bits/hex drift");
                yield new FloatScalar(bits);
            }
            case "DOUBLE" -> {
                requireObjectKeys(value, "DOUBLE RNG scalar", Set.of("type", "bitsU64", "hex"));
                long bits = parseUnsignedLong(value.path("bitsU64").asText(), "DOUBLE bits");
                require(Double.toHexString(Double.longBitsToDouble(bits)).equals(value.path("hex").asText()),
                        "Mansion DOUBLE bits/hex drift");
                yield new DoubleScalar(bits);
            }
            default -> throw new IllegalArgumentException("unknown Mansion RNG scalar");
        };
    }

    private static OperationReceipt operationReceipt(JsonNode value) {
        requireObjectKeys(value, "operation receipt", Set.of("operationCount", "operationCounts",
                "orderedOperationSha256"));
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>(); int total = 0;
        for (Map.Entry<String, JsonNode> entry : value.path("operationCounts").properties()) {
            int count = entry.getValue().asInt(-1); require(count >= 0, "negative Mansion operation count");
            counts.put(entry.getKey(), count); total = Math.addExact(total, count);
        }
        OperationReceipt result = new OperationReceipt(value.path("operationCount").asInt(-1), counts,
                value.path("orderedOperationSha256").asText());
        require(total == result.operationCount(), "Mansion operation histogram drift");
        return result;
    }

    private static SettlementSidecars sidecars(JsonNode value, Chunk owner, PlacementRng placement) {
        requireObjectKeys(value, "settlement sidecars", Set.of("LOOT", "BENT", "ENTS"));
        ArrayList<BlockEntityEvidence> bent = new ArrayList<>();
        for (JsonNode row : value.path("BENT")) {
            requireObjectKeys(row, "BENT row", Set.of("ordinal", "position", "destinationChunk",
                    "destinationRelation", "blockIdentity", "blockState", "blockEntityType",
                    "placementDrawOrdinal", "canonicalNbt"));
            int ordinal = row.path("ordinal").asInt(-1); Pos position = pos(row.path("position"), "BENT position");
            Chunk destination = chunk(row.path("destinationChunk"), "BENT destination");
            DestinationRelation relation = relation(row.path("destinationRelation").asText());
            require(ordinal == bent.size() && destination.equals(chunk(position))
                            && relation == relation(owner, destination),
                    "Mansion BENT order/destination drift");
            String block = row.path("blockIdentity").asText();
            String blockEntityType = row.path("blockEntityType").asText();
            require(Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:banner",
                    "minecraft:mob_spawner").contains(blockEntityType),
                    "unknown Mansion BENT type");
            Integer drawOrdinal = row.path("placementDrawOrdinal").isNull() ? null
                    : row.path("placementDrawOrdinal").asInt(-1);
            boolean randomizable = Set.of("minecraft:chest", "minecraft:trapped_chest").contains(block);
            if (randomizable) {
                require(drawOrdinal != null && drawOrdinal >= 0 && drawOrdinal < placement.draws().size()
                                && placement.draws().get(drawOrdinal).position().equals(position),
                        "Mansion BENT placement-draw linkage drift");
            } else require(drawOrdinal == null, "Mansion non-randomizable BENT consumed placement draw");
            bent.add(new BlockEntityEvidence(ordinal, position, destination, relation, block,
                    row.path("blockState").asText(), blockEntityType, drawOrdinal,
                    canonicalNbt(row.path("canonicalNbt"))));
        }
        ArrayList<LootEvidence> loot = new ArrayList<>();
        for (JsonNode row : value.path("LOOT")) {
            requireObjectKeys(row, "LOOT row", Set.of("ordinal", "position", "destinationChunk",
                    "destinationRelation", "table", "signedSeedI64", "bentOrdinal", "placementDrawOrdinal"));
            int ordinal = row.path("ordinal").asInt(-1); Pos position = pos(row.path("position"), "LOOT position");
            Chunk destination = chunk(row.path("destinationChunk"), "LOOT destination");
            DestinationRelation relation = relation(row.path("destinationRelation").asText());
            int bentOrdinal = row.path("bentOrdinal").asInt(-1);
            int drawOrdinal = row.path("placementDrawOrdinal").asInt(-1);
            long seed = parseLong(row.path("signedSeedI64").asText(), "Mansion loot seed");
            require(ordinal == loot.size() && destination.equals(chunk(position))
                            && relation == relation(owner, destination)
                            && bentOrdinal >= 0 && bentOrdinal < bent.size()
                            && bent.get(bentOrdinal).position().equals(position)
                            && drawOrdinal >= 0 && drawOrdinal < placement.draws().size()
                            && placement.draws().get(drawOrdinal).position().equals(position)
                            && placement.draws().get(drawOrdinal).signedSeed() == seed,
                    "Mansion LOOT destination/draw linkage drift");
            loot.add(new LootEvidence(ordinal, position, destination, relation,
                    row.path("table").asText(), seed, bentOrdinal, drawOrdinal));
        }
        ArrayList<EntityEvidence> entities = new ArrayList<>();
        for (JsonNode row : value.path("ENTS")) entities.add(entity(row, entities.size(), owner));
        return new SettlementSidecars(loot, bent, entities);
    }

    private static EntityEvidence entity(JsonNode row, int expectedOrdinal, Chunk owner) {
        requireObjectKeys(row, "ENTS row", Set.of("ordinal", "marker", "markerPosition",
                "destinationChunk", "destinationRelation", "entityKey", "spawnReason",
                "runtimeSpawnReason", "positionBitsU64", "positionHex", "yawPitchBitsU32",
                "velocityBitsU64", "velocityHex", "loot", "canonicalPayload"));
        require(row.path("ordinal").asInt(-1) == expectedOrdinal && row.path("loot").isNull(),
                "Mansion ENTS order/loot absence drift");
        String marker = row.path("marker").asText();
        String entityKey = switch (marker) {
            case "Mage" -> "minecraft:evoker";
            case "Warrior" -> "minecraft:vindicator";
            case "Group of Allays" -> "minecraft:allay";
            default -> throw new IllegalArgumentException("unknown Mansion entity marker");
        };
        require(entityKey.equals(row.path("entityKey").asText())
                        && "minecraft:structure".equals(row.path("spawnReason").asText())
                        && "STRUCTURE".equals(row.path("runtimeSpawnReason").asText()),
                "Mansion ENTS identity/spawn-reason drift");
        Pos markerPosition = pos(row.path("markerPosition"), "entity marker position");
        Chunk destination = chunk(row.path("destinationChunk"), "entity destination");
        DestinationRelation relation = relation(row.path("destinationRelation").asText());
        require(destination.equals(chunk(markerPosition)) && relation == relation(owner, destination),
                "Mansion ENTS destination drift");
        ArrayList<Long> positionBits = unsignedLongs(row.path("positionBitsU64"), 3, "entity position");
        ArrayList<Integer> rotationBits = unsignedInts(row.path("yawPitchBitsU32"), 2, "entity rotation");
        ArrayList<Long> velocityBits = unsignedLongs(row.path("velocityBitsU64"), 3, "entity velocity");
        requireHexDoubles(row.path("positionHex"), positionBits, "entity position");
        requireHexDoubles(row.path("velocityHex"), velocityBits, "entity velocity");
        EntityEvidence result = new EntityEvidence(expectedOrdinal, marker, markerPosition, destination, relation,
                entityKey, "minecraft:structure", "STRUCTURE", positionBits, rotationBits, velocityBits,
                Optional.empty(), OptionalLong.empty(), canonicalNbt(row.path("canonicalPayload")));
        require(result.x() == markerPosition.x() + 0.5 && result.y() == markerPosition.y()
                        && result.z() == markerPosition.z() + 0.5
                        && result.yaw() == 0.0f && result.pitch() == 0.0f
                        && result.velocityX() == 0.0 && result.velocityY() == 0.0 && result.velocityZ() == 0.0,
                "Mansion ENTS exact runtime transform drift");
        require(result.canonicalPayload().orderedTypedTree().entries().stream()
                        .noneMatch(entry -> entry.name().equals("UUID")),
                "Mansion ENTS canonical payload retained UUID");
        NbtTypeTree id = result.canonicalPayload().orderedTypedTree().entries().stream()
                .filter(entry -> entry.name().equals("id")).map(NbtNamedTag::tag).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mansion ENTS payload omitted id"));
        require(id.typeId() == 8 && ("\"" + entityKey + "\"").equals(id.snbtValue()),
                "Mansion ENTS canonical payload entity id drift");
        return result;
    }

    private static EntityCanonicalization decodeCanonicalization(JsonNode value) {
        requireObjectKeys(value, "entity canonicalization", Set.of("rule", "repeatedOfficialRuns",
                "entityCountPerRun", "allRuntimeUuidsDiffer", "canonicalProjectionSha256"));
        return new EntityCanonicalization(value.path("rule").asText(),
                value.path("repeatedOfficialRuns").asInt(-1), value.path("entityCountPerRun").asInt(-1),
                value.path("allRuntimeUuidsDiffer").asBoolean(false),
                value.path("canonicalProjectionSha256").asText());
    }

    private static CanonicalNbt compactCanonicalNbt(JsonNode value) {
        requireObjectKeys(value, "compact canonical NBT", Set.of(
                "binaryHex", "binaryLength", "binarySha256"));
        final byte[] binary;
        try { binary = HexFormat.of().parseHex(value.path("binaryHex").asText()); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("malformed Mansion compact canonical NBT hex", error);
        }
        require(binary.length == value.path("binaryLength").asInt(-1),
                "Mansion compact canonical NBT length drift");
        return new CanonicalNbt(binary, value.path("binarySha256").asText(), parseBinaryNbt(binary));
    }

    private static NbtTypeTree parseBinaryNbt(byte[] binary) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(binary))) {
            require(input.readUnsignedByte() == 10 && input.readUTF().isEmpty(),
                    "Mansion canonical NBT root encoding drift");
            NbtTypeTree root = parseBinaryNbtPayload(input, 10);
            require(input.available() == 0, "Mansion canonical NBT trailing bytes");
            return root;
        } catch (IOException error) {
            throw new IllegalArgumentException("malformed Mansion canonical binary NBT", error);
        }
    }

    private static NbtTypeTree parseBinaryNbtPayload(DataInputStream input, int typeId) throws IOException {
        return switch (typeId) {
            case 3 -> new NbtTypeTree(3, "INT", List.of(), List.of(), Integer.toString(input.readInt()));
            case 8 -> new NbtTypeTree(8, "STRING", List.of(), List.of(), quoteSnbt(input.readUTF()));
            case 9 -> {
                int childType = input.readUnsignedByte(); int size = input.readInt();
                require(size >= 0 && size <= 1_000_000 && (size == 0 || childType != 0)
                                && childType >= 0 && childType <= 12,
                        "Mansion canonical NBT LIST header drift");
                ArrayList<NbtTypeTree> values = new ArrayList<>(size);
                for (int index = 0; index < size; index++)
                    values.add(parseBinaryNbtPayload(input, childType));
                yield new NbtTypeTree(9, "LIST", List.of(), values, null);
            }
            case 10 -> {
                ArrayList<NbtNamedTag> entries = new ArrayList<>(); Set<String> names = new HashSet<>();
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == 0) break;
                    require(childType >= 1 && childType <= 12, "Mansion canonical NBT child type drift");
                    String name = input.readUTF();
                    require(names.add(name), "Mansion canonical NBT duplicate tag " + name);
                    entries.add(new NbtNamedTag(name, parseBinaryNbtPayload(input, childType)));
                }
                yield new NbtTypeTree(10, "COMPOUND", entries, List.of(), null);
            }
            case 11 -> {
                int size = input.readInt(); require(size >= 0 && size <= 1_000_000,
                        "Mansion canonical NBT INT[] length drift");
                StringBuilder snbt = new StringBuilder("[I;");
                for (int index = 0; index < size; index++) {
                    if (index != 0) snbt.append(','); snbt.append(input.readInt());
                }
                yield new NbtTypeTree(11, "INT[]", List.of(), List.of(), snbt.append(']').toString());
            }
            default -> throw new IllegalArgumentException(
                    "unsupported Mansion persisted NBT type id " + typeId);
        };
    }

    private static String quoteSnbt(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static CanonicalNbt canonicalNbt(JsonNode value) {
        requireObjectKeys(value, "canonical NBT", Set.of("orderedTypedTree", "binaryHex",
                "binaryLength", "binarySha256"));
        final byte[] binary;
        try { binary = HexFormat.of().parseHex(value.path("binaryHex").asText()); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("malformed Mansion canonical NBT hex", error);
        }
        require(binary.length == value.path("binaryLength").asInt(-1),
                "Mansion canonical NBT length drift");
        return new CanonicalNbt(binary, value.path("binarySha256").asText(),
                nbtTree(value.path("orderedTypedTree")));
    }

    private static NbtTypeTree nbtTree(JsonNode value) {
        int typeId = value.path("typeId").asInt(-1); String type = value.path("type").asText();
        String expectedType = switch (typeId) {
            case 1 -> "BYTE"; case 2 -> "SHORT"; case 3 -> "INT"; case 4 -> "LONG";
            case 5 -> "FLOAT"; case 6 -> "DOUBLE"; case 7 -> "BYTE[]"; case 8 -> "STRING";
            case 9 -> "LIST"; case 10 -> "COMPOUND"; case 11 -> "INT[]"; case 12 -> "LONG[]";
            default -> throw new IllegalArgumentException("unknown Mansion NBT type id " + typeId);
        };
        require(expectedType.equals(type), "Mansion NBT type-name drift");
        ArrayList<NbtNamedTag> entries = new ArrayList<>(); ArrayList<NbtTypeTree> values = new ArrayList<>();
        String snbt = null;
        if (typeId == 10) {
            requireObjectKeys(value, "COMPOUND typed NBT", Set.of("typeId", "type", "entries"));
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (JsonNode entry : value.path("entries")) {
                requireObjectKeys(entry, "typed NBT entry", Set.of("name", "tag"));
                String name = entry.path("name").asText(); require(names.add(name), "duplicate Mansion NBT tag name");
                entries.add(new NbtNamedTag(name, nbtTree(entry.path("tag"))));
            }
        } else if (typeId == 9) {
            requireObjectKeys(value, "LIST typed NBT", Set.of("typeId", "type", "values"));
            for (JsonNode child : value.path("values")) values.add(nbtTree(child));
            if (!values.isEmpty()) {
                int elementType = values.get(0).typeId();
                require(values.stream().allMatch(child -> child.typeId() == elementType),
                        "Mansion NBT LIST element-type drift");
            }
        } else {
            requireObjectKeys(value, "scalar typed NBT", Set.of("typeId", "type", "snbtValue"));
            snbt = value.path("snbtValue").asText();
        }
        return new NbtTypeTree(typeId, type, entries, values, snbt);
    }

    private static ArrayList<Long> unsignedLongs(JsonNode value, int size, String label) {
        require(value.isArray() && value.size() == size, "invalid Mansion " + label + " bits");
        ArrayList<Long> result = new ArrayList<>();
        for (JsonNode item : value) result.add(parseUnsignedLong(item.asText(), label));
        return result;
    }

    private static ArrayList<Integer> unsignedInts(JsonNode value, int size, String label) {
        require(value.isArray() && value.size() == size, "invalid Mansion " + label + " bits");
        ArrayList<Integer> result = new ArrayList<>();
        for (JsonNode item : value) result.add(parseUnsignedInt(item.asText(), label));
        return result;
    }

    private static void requireHexDoubles(JsonNode value, List<Long> bits, String label) {
        require(value.isArray() && value.size() == bits.size(), "invalid Mansion " + label + " hex");
        for (int index = 0; index < bits.size(); index++) {
            require(Double.toHexString(Double.longBitsToDouble(bits.get(index))).equals(value.get(index).asText()),
                    "Mansion " + label + " bits/hex drift");
        }
    }

    private static long parseUnsignedLong(String value, String label) {
        try { return Long.parseUnsignedLong(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("invalid " + label, error); }
    }

    private static int parseUnsignedInt(String value, String label) {
        try { return Integer.parseUnsignedInt(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("invalid " + label, error); }
    }

    private static Chunk chunk(JsonNode value, String label) {
        require(value.isArray() && value.size() == 2, "invalid Mansion " + label);
        return new Chunk(value.get(0).asInt(), value.get(1).asInt());
    }

    private static Chunk chunk(Pos position) {
        return new Chunk(Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
    }

    private static DestinationRelation relation(String value) {
        return enumValue(DestinationRelation.class, value, "destination relation");
    }

    private static DestinationRelation relation(Chunk owner, Chunk destination) {
        return owner.equals(destination) ? DestinationRelation.LOCAL : DestinationRelation.FOREIGN;
    }

    private static Template decodeTemplate(JsonNode value, Set<String> knownStates) {
        String id = value.path("id").asText();
        require(id.startsWith("minecraft:woodland_mansion/"), "invalid Mansion template key");
        Pos size = pos(value.path("size"), "template size");
        require(size.x > 0 && size.y > 0 && size.z > 0, "invalid Mansion template size");
        ArrayList<String> stateTable = new ArrayList<>();
        for (JsonNode state : value.path("stateTable")) {
            String exact = state.asText(); require(knownStates.contains(exact), "unknown Mansion state");
            require(!stateTable.contains(exact), "duplicate Mansion template state");
            stateTable.add(exact);
        }
        ArrayList<Command> commands = new ArrayList<>();
        int ordinal = 0, runs = 0, data = 0;
        for (JsonNode command : value.path("commands")) {
            require(command.path("ordinal").asInt(-1) == ordinal, "Mansion command order drift");
            int state = command.path("state").asInt(-1);
            require(state >= 0 && state < stateTable.size(), "Mansion command state index drift");
            String op = command.path("op").asText();
            Command decoded;
            if (op.equals("RUN")) {
                decoded = new Run(ordinal, state, pos(command.path("start"), "RUN start"),
                        pos(command.path("delta"), "RUN delta"), command.path("count").asInt());
                runs++;
            } else if (op.equals("DATA")) {
                decoded = new Data(ordinal, state, pos(command.path("position"), "DATA position"),
                        semantic(command.path("semantic")));
                data++;
            } else throw new IllegalArgumentException("unknown Mansion opcode: " + op);
            ordinal = Math.addExact(ordinal, decoded.expandedCount()); commands.add(decoded);
        }
        require(ordinal == value.path("blockCount").asInt() && runs == value.path("runCount").asInt()
                        && data == value.path("dataCount").asInt(), "Mansion template command drift");
        String grammar = value.path("grammarSha256").asText();
        require(SHA.matcher(grammar).matches(), "invalid Mansion template grammar receipt");
        return new Template(id, size, stateTable, commands, ordinal, runs, data, grammar,
                receipt(value.path("templateNbtReceipt")));
    }

    private static Probe decodeProbe(JsonNode value, Map<String, Template> templates) {
        JsonNode request = value.path("request");
        Request decodedRequest = new Request(request.path("structureKey").asText(),
                parseLong(request.path("worldSeedI64").asText(), "world seed"),
                request.path("chunkX").asInt(), request.path("chunkZ").asInt());
        require(decodedRequest.structureKey.equals("minecraft:mansion"), "Mansion request key drift");
        ArrayList<HeightQuery> queries = new ArrayList<>();
        JsonNode height = value.path("heightQueries");
        for (JsonNode query : height.path("queryOrder")) {
            int ordinal = query.path("ordinal").asInt(-1);
            require(ordinal == queries.size(), "Mansion height query order drift");
            queries.add(new HeightQuery(ordinal, query.path("heightmap").asText(),
                    query.path("x").asInt(), query.path("z").asInt(),
                    query.path("oracleResult").asInt()));
        }
        require(queries.size() == 4 && height.path("queryCount").asInt() == 4,
                "Mansion height query cardinality drift");
        ArrayList<Piece> pieces = new ArrayList<>();
        for (JsonNode piece : value.path("pieceGrid")) {
            int ordinal = piece.path("ordinal").asInt(-1);
            require(ordinal == pieces.size(), "Mansion piece order drift");
            String template = piece.path("template").asText();
            require(templates.containsKey(template), "unknown Mansion piece template");
            ArrayList<Marker> markers = new ArrayList<>();
            for (JsonNode marker : piece.path("markers")) {
                markers.add(new Marker(marker.path("ordinal").asInt(-1),
                        marker.path("metadata").asText(), pos(marker.path("position"), "marker")));
                require(markers.get(markers.size() - 1).ordinal == markers.size() - 1,
                        "Mansion marker order drift");
            }
            pieces.add(new Piece(ordinal, template, pos(piece.path("position"), "piece position"),
                    enumValue(Rotation.class, piece.path("rotation").asText(), "rotation"),
                    enumValue(Mirror.class, piece.path("mirror").asText(), "mirror"),
                    box(piece.path("boundingBox")), markers,
                    receipt(piece.path("pieceNbtReceipt"))));
        }
        require(pieces.size() == value.path("pieceCount").asInt(), "Mansion piece count drift");
        JsonNode rng = value.path("layoutRng");
        ArrayList<Long> continuation = new ArrayList<>();
        for (JsonNode number : rng.path("continuationNextLongI64")) {
            continuation.add(parseLong(number.asText(), "layout continuation"));
        }
        require(continuation.size() == 8, "Mansion layout continuation drift");
        String pieceSha = value.path("pieceGridSha256").asText();
        String drawSha = value.path("layoutDrawReceiptSha256").asText();
        require(SHA.matcher(pieceSha).matches() && SHA.matcher(drawSha).matches(),
                "invalid Mansion grid/RNG receipt");
        return new Probe(decodedRequest, pos(value.path("generationStubPosition"), "stub"),
                queries, pieces, parseLong(rng.path("state48").asText(), "layout state"),
                rng.path("worldgenCount").asLong(), continuation, pieceSha, drawSha,
                receipt(value.path("startNbtReceipt")));
    }

    private static Semantic semantic(JsonNode value) {
        return switch (value.path("kind").asText()) {
            case "STRUCTURE_MARKER" -> new StructureMarker(value.path("metadata").asText());
            case "EMPTY_CONTAINER" -> new EmptyContainer(entityType(value));
            case "CONTAINER_ITEMS" -> new ContainerItems(entityType(value), typed(value.path("items")));
            case "PATTERNED_BANNER" -> new PatternedBanner(entityType(value), typed(value.path("patterns")));
            case "MOB_SPAWNER" -> new MobSpawner(entityType(value), typed(value.path("spawnData")));
            default -> throw new IllegalArgumentException("unknown Mansion semantic");
        };
    }

    private static String entityType(JsonNode value) {
        String type = value.path("blockEntityType").asText();
        require(Set.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:banner",
                "minecraft:mob_spawner").contains(type), "unknown Mansion block-entity type");
        return type;
    }

    private static TypedValue typed(JsonNode value) {
        return switch (value.path("kind").asText()) {
            case "MAP" -> {
                ArrayList<MapEntry> entries = new ArrayList<>(); String previous = null;
                for (JsonNode entry : value.path("entries")) {
                    String name = entry.path("name").asText();
                    require(previous == null || previous.compareTo(name) < 0,
                            "Mansion typed MAP order/cardinality drift");
                    entries.add(new MapEntry(name, typed(entry.path("value")))); previous = name;
                }
                yield new MapValue(entries);
            }
            case "LIST" -> {
                ArrayList<TypedValue> values = new ArrayList<>();
                for (JsonNode child : value.path("values")) values.add(typed(child));
                yield new ListValue(values);
            }
            case "BYTE" -> new ByteValue(parseByte(value.path("value").asText()));
            case "INT" -> new IntValue(Integer.parseInt(value.path("value").asText()));
            case "STRING" -> new StringValue(quotedString(value.path("value").asText()));
            default -> throw new IllegalArgumentException("unknown Mansion typed value");
        };
    }

    private static byte parseByte(String value) {
        require(value.endsWith("b"), "malformed Mansion BYTE");
        return Byte.parseByte(value.substring(0, value.length() - 1));
    }
    private static String quotedString(String value) {
        require(value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""),
                "malformed Mansion STRING");
        return value.substring(1, value.length() - 1).replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
    private static long parseLong(String value, String label) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("invalid " + label, error); }
    }
    private static Pos pos(JsonNode value, String label) {
        require(value.isArray() && value.size() == 3, "invalid Mansion " + label);
        return new Pos(value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt());
    }
    private static Box box(JsonNode value) {
        require(value.isArray() && value.size() == 6, "invalid Mansion bounding box");
        return new Box(value.get(0).asInt(), value.get(1).asInt(), value.get(2).asInt(),
                value.get(3).asInt(), value.get(4).asInt(), value.get(5).asInt());
    }
    private static NbtReceipt receipt(JsonNode value) {
        requireObjectKeys(value, "NBT receipt", Set.of("binaryLength", "binarySha256"));
        return new NbtReceipt(value.path("binaryLength").asInt(), value.path("binarySha256").asText());
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("unknown Mansion " + label, error); }
    }
    private static void validatePolicy(JsonNode value) {
        require(value.size() == 3 && "RUN/DATA".equals(value.path("templateEncoding").asText())
                        && "length-and-sha-only".equals(value.path("rawNbt").asText())
                        && "fail-closed".equals(value.path("unknownOpcodeStateMarker").asText()),
                "Mansion grammar policy drift");
    }
    private static void validateProcessor(JsonNode value) {
        require(PROCESSOR_SHA256.equals(value.path("sha256").asText())
                        && "IGNORE".equals(value.path("opcode").asText())
                        && value.path("placementOrder").size() == 1
                        && PROCESSOR.equals(value.path("placementOrder").get(0).asText())
                        && value.path("ignoredStates").size() == 1
                        && "minecraft:structure_block".equals(value.path("ignoredStates").get(0).asText()),
                "Mansion processor identity drift");
    }
    private static void requireObjectKeys(JsonNode value, String label, Set<String> expected) {
        require(value.isObject(), label + " must be an object");
        LinkedHashSet<String> actual = new LinkedHashSet<>(); value.propertyNames().forEach(actual::add);
        require(actual.equals(expected) || new HashSet<>(actual).equals(expected), label + " schema drift");
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
