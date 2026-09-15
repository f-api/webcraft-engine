package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.feature.Mc263StructureBatchBridge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant production API for exact Minecraft 26.3-snapshot-7 Ancient City settlement.
 *
 * <p>This tranche deliberately does not ship a finite-coordinate transcript player. E3I5, E3I7
 * and E3I9 are reduced here to source-bound typed semantic identities. A product implementation of
 * {@link WorldAccess#executeProcedurally(ExecutionRequest)} must derive its operations from the
 * accepted typed grammar/processor/configured-feature implementation on an unpublished fork. E3I13
 * is responsible for proving the complete returned bytes against the accepted natural witnesses.</p>
 *
 * <p>All {@code supports*} methods are pure. {@link #prepare} completes source/version/start/clip/
 * piece/template/processor/state/sidecar preflight before the first call to
 * {@code executeProcedurally}. The returned {@link PreparedSettlement} is immutable. Publication is
 * a single replay-aware atomic call covering payload, mutable successor and caller RNG continuation.</p>
 */
public final class Mc263AncientCitySettlement {
    public static final String STRUCTURE_KEY = "minecraft:ancient_city";
    public static final String FEATURE_KEY = "minecraft:sculk_patch_ancient_city";
    public static final String VERSION_ID = "26.3-snapshot-7";
    public static final String SERVER_VERSION = "26.3 Snapshot 7";
    public static final String JAVA_RUNTIME_VERSION = "25.0.1+8-LTS";
    public static final int FINAL_CHUNK_SCHEMA =
            com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec.SCHEMA;
    public static final int PIECE_COUNT = 83;
    public static final long NATURAL_WORLD_SEED = 62_570L;
    public static final int NATURAL_START_CHUNK_X = -85;
    public static final int NATURAL_START_CHUNK_Z = -37;
    public static final int MIN_CLIP_Y = -63;
    public static final int MAX_CLIP_Y = 319;

    public static final int E3I5_RESOURCE_BYTES = 23_632;
    public static final int E3I5_LOOT_ROWS = 20;
    public static final int E3I5_LOOT_DESTINATION_CHUNKS = 14;
    public static final int E3I5_RANDOMIZABLE_CONTAINER_DRAWS = 20;
    public static final String E3I5_RESOURCE_SHA256 =
            "a0bd942d78f08ddfe5e293317a2066988d9316ed511f363728f30991be5c715e";
    public static final String E3I5_ORACLE_SOURCE_SHA256 =
            "8af4d62fa9815fe2b795bba894c746a8c4fba83130301c1a51d52b9ea7d263ce";
    public static final String E3I5_WRAPPER_SOURCE_SHA256 =
            "2ca041d097f298b008b644bc4207fd68922e56e738db1854ab5fa9f4875d0af5";

    public static final int E3I7_RESOURCE_BYTES = 17_175;
    public static final String E3I7_RESOURCE_SHA256 =
            "aaf600d0067aabeefb1d83718212508ea3b3bc4a16dee4ce5eee611c72e9a403";
    public static final String E3I7_ORACLE_SOURCE_SHA256 =
            "7e7090c5fc48a5489e003265663f79c7a98777e0a73aa0fd8cdbef9693217ca3";
    public static final String E3I7_WRAPPER_SOURCE_SHA256 =
            "80be520f71c81feab7b668398894885ab025ff4dfd8634ec5b7ce1d1b4e177dd";
    public static final int START_NBT_BYTES = 45_069;
    public static final String PREDECESSOR_SHA256 =
            "2c9ce0f54923f193e9fc5a22f3e0ec435a940512597a1c56992e660f5682914a";
    public static final String SUCCESSOR_SHA256 =
            "c188e55ca02e973ea8b00b0ebda947b767f598db4892827c32b9cd3e2a0af8c1";
    public static final int SUCCESSOR_CHANGED_BYTE_OFFSET = 19;
    public static final String E3I7_PIECE_LIST_SHA256 =
            "b8d8302e85916488053d3b932cad20e5423314ee07b043dc6bc0754acb6f07d7";

    public static final int E3I9_RESOURCE_BYTES = 1_689_372;
    public static final String E3I9_RESOURCE_SHA256 =
            "fcbeab12787c601a0134afd9ab168d8977128e572e17b24f63b3bbf98077b4f8";
    public static final String E3I9_ORACLE_SOURCE_SHA256 =
            "c98aa35213505d48b0212f769e59e17f728ccebea7f6b5546925c703977014e9";
    public static final String E3I9_WRAPPER_SOURCE_SHA256 =
            "5712076a9c2b961e0da7623ff68855a60666baf9562957e32bbd4493f2533710";
    public static final int E3I9_RAW_TRANSCRIPT_BYTES = 32_869_128;
    public static final String E3I9_RAW_TRANSCRIPT_SHA256 =
            "b84728ee050d9af6e084205571581629431c46109c95451a1241d2cdb726810f";
    public static final int E3I9_QUERY_COUNT = 351_572;
    public static final int E3I9_WRITE_COUNT = 5_632;
    public static final int E3I9_FOREIGN_WRITE_COUNT = 2_015;
    public static final int E3I9_FINAL_CELL_ROWS = 2_867;
    public static final int E3I9_UNIQUE_FINAL_CELLS = 2_831;
    public static final int E3I9_SOURCE_CHUNKS = 8;
    public static final int E3I9_DESTINATION_CHUNKS = 25;
    public static final String E3I9_SUMMARY_SHA256 =
            "44adb4d267bc713b7895c6b2f296ab1f3870a60559410c560af0dba03074ad72";

    public static final String EXECUTION_CORPUS_SHA256 =
            "54913c8ef950a8ae52b509bf6b517501b0d68d39b08bae8a57fb6da06ac4e4c0";
    public static final String START_GRAPH_CARRIER_SHA256 =
            "15c9c62d614799dcdb6e818d3602d11babc478e2cb723a53613f7f0e985880cf";
    public static final String START_GRAPH_JSON_SHA256 =
            "92c1e788326fe64b654ac9c72ea1579bfd4b6688d4d307a87c521bd500084999";
    public static final String INNER_SERVER_SHA256 =
            "e5efad859e05767b507f43cf5adb28b6c8944ef7c0b612527f3e6ebdd2c4ace1";
    public static final String OUTER_SERVER_SHA256 =
            "d8e28d01a49ebd85aa992f9630fd12a1a990746c108a16baff6e95b21abdd082";
    public static final String PLACED_FEATURE_CODEC_SHA256 =
            "c86331fb1b3a758f3702bc1e4ad22bc416f1e80eb7582f889ce2a0591c02ac18";
    public static final String CONFIGURED_FEATURE_CODEC_SHA256 =
            "bb2707d56041d34cc8c79d480ff734439c7c3c99e5044291fbd3f17ae5d05dbc";

    private static final String FORMAT = "ANC263SET3";
    private static final String E3I5_LOOT_SEMANTICS_SHA256 =
            "f1e4dc0701b66fcbd2ebc799b2da2405a430e0ed2bb4ecce356d5583201cbeec";
    private static final List<Integer> E3I9_FEATURE_PIECES =
            List.of(30, 39, 40, 41, 42, 55, 70, 73, 74, 82);
    private static final Set<String> ALLOWED_LOOT_TABLES = Set.of(
            "minecraft:chests/ancient_city", "minecraft:chests/ancient_city_ice_box");
    public static final List<NbtTagSpec> E3I5_CHEST_NBT_TAGS_IN_ORDER = List.of(
            new NbtTagSpec("LootTable", 8), new NbtTagSpec("components", 10),
            new NbtTagSpec("x", 3), new NbtTagSpec("y", 3), new NbtTagSpec("z", 3),
            new NbtTagSpec("id", 8), new NbtTagSpec("LootTableSeed", 4));
    private static final List<LootBinding> E3I5_LOOT_BINDINGS = List.of(
            loot(0, 19, "minecraft:ancient_city/structures/ice_box_1", "minecraft:chests/ancient_city_ice_box"),
            loot(1, 21, "minecraft:ancient_city/structures/tall_ruin_2", "minecraft:chests/ancient_city"),
            loot(2, 21, "minecraft:ancient_city/structures/tall_ruin_2", "minecraft:chests/ancient_city"),
            loot(3, 22, "minecraft:ancient_city/structures/tall_ruin_4", "minecraft:chests/ancient_city"),
            loot(4, 25, "minecraft:ancient_city/structures/chamber_1", "minecraft:chests/ancient_city"),
            loot(5, 26, "minecraft:ancient_city/structures/barracks", "minecraft:chests/ancient_city"),
            loot(6, 26, "minecraft:ancient_city/structures/barracks", "minecraft:chests/ancient_city"),
            loot(7, 33, "minecraft:ancient_city/structures/tall_ruin_3", "minecraft:chests/ancient_city"),
            loot(8, 37, "minecraft:ancient_city/structures/chamber_3", "minecraft:chests/ancient_city"),
            loot(9, 51, "minecraft:ancient_city/structures/chamber_3", "minecraft:chests/ancient_city"),
            loot(10, 57, "minecraft:ancient_city/structures/chamber_2", "minecraft:chests/ancient_city"),
            loot(11, 60, "minecraft:ancient_city/structures/barracks", "minecraft:chests/ancient_city"),
            loot(12, 60, "minecraft:ancient_city/structures/barracks", "minecraft:chests/ancient_city"),
            loot(13, 68, "minecraft:ancient_city/structures/tall_ruin_2", "minecraft:chests/ancient_city"),
            loot(14, 68, "minecraft:ancient_city/structures/tall_ruin_2", "minecraft:chests/ancient_city"),
            loot(15, 69, "minecraft:ancient_city/structures/chamber_3", "minecraft:chests/ancient_city"),
            loot(16, 75, "minecraft:ancient_city/structures/tall_ruin_2", "minecraft:chests/ancient_city"),
            loot(17, 75, "minecraft:ancient_city/structures/tall_ruin_2", "minecraft:chests/ancient_city"),
            loot(18, 80, "minecraft:ancient_city/structures/barracks", "minecraft:chests/ancient_city"),
            loot(19, 80, "minecraft:ancient_city/structures/barracks", "minecraft:chests/ancient_city"));

    private static final SourcePin E3I5 = new SourcePin("E3I5 natural loot",
            "/mc263/ancient-city-natural-loot-evidence-v1.json", E3I5_RESOURCE_BYTES,
            E3I5_RESOURCE_SHA256, E3I5_ORACLE_SOURCE_SHA256, E3I5_WRAPPER_SOURCE_SHA256);
    private static final SourcePin E3I7 = new SourcePin("E3I7 natural successor",
            "/mc263/ancient-city-natural-successor-evidence-v1.json", E3I7_RESOURCE_BYTES,
            E3I7_RESOURCE_SHA256, E3I7_ORACLE_SOURCE_SHA256, E3I7_WRAPPER_SOURCE_SHA256);
    private static final SourcePin E3I9 = new SourcePin("E3I9 natural feature transcript",
            "/mc263/ancient-city-natural-feature-transcript-evidence-v1.json", E3I9_RESOURCE_BYTES,
            E3I9_RESOURCE_SHA256, E3I9_ORACLE_SOURCE_SHA256, E3I9_WRAPPER_SOURCE_SHA256);

    private Mc263AncientCitySettlement() {
        throw new AssertionError("no instances");
    }

    /** Exact evidence/source identities required by the dormant production seam. */
    public static EvidenceIdentity evidenceIdentity() {
        return new EvidenceIdentity(VERSION_ID, SERVER_VERSION, JAVA_RUNTIME_VERSION,
                INNER_SERVER_SHA256, OUTER_SERVER_SHA256, EXECUTION_CORPUS_SHA256,
                START_GRAPH_CARRIER_SHA256, START_GRAPH_JSON_SHA256,
                PLACED_FEATURE_CODEC_SHA256, CONFIGURED_FEATURE_CODEC_SHA256,
                List.of(E3I5, E3I7, E3I9));
    }

    /**
     * Convenience entry for the owning chunk. E3I13 may use the explicit-clip overload for every
     * intersecting destination chunk without changing this production API.
     */
    public static PreparedSettlement prepare(Mc263AncientCityProducer.Start start,
            WorldAccess world) {
        Objects.requireNonNull(start, "Ancient City start");
        return prepare(start, new Clip(start.chunkX(), start.chunkZ(), MIN_CLIP_Y, MAX_CLIP_Y), world);
    }

    /**
     * Performs the complete pure preflight, then asks the runtime for one procedural isolated-fork
     * execution and freezes its result. No E3I9 query/write coordinate row is consumed here.
     */
    public static PreparedSettlement prepare(Mc263AncientCityProducer.Start start,
            Clip clip, WorldAccess world) {
        Objects.requireNonNull(start, "Ancient City start");
        return prepare(start, clip, acceptedInputs(start), world);
    }

    /**
     * Source-loader entry for E3I13 and product adapters. The caller may supply independently parsed
     * typed E3I5/E3I7/E3I9 inputs; all identities and seals are revalidated before execution.
     */
    public static PreparedSettlement prepare(Mc263AncientCityProducer.Start start, Clip clip,
            AcceptedInputs inputs, WorldAccess world) {
        Objects.requireNonNull(start, "Ancient City start");
        Objects.requireNonNull(clip, "Ancient City clip");
        Objects.requireNonNull(inputs, "Ancient City accepted inputs");
        Objects.requireNonNull(world, "Ancient City world access");
        Preflight preflight = preflight(start, clip, inputs, world);
        ExecutionRequest request = new ExecutionRequest(start, clip, inputs, preflight.grammar());
        ExecutionResult execution = Objects.requireNonNull(world.executeProcedurally(request),
                "Ancient City procedural execution result");
        validateExecution(clip, execution);
        byte[] receipt = canonicalReceipt(request, execution);
        String payloadSha256 = sha256(receipt);
        String transactionKey = STRUCTURE_KEY + "@" + start.chunkX() + "," + start.chunkZ()
                + "->" + clip.chunkX() + "," + clip.chunkZ();
        String fingerprint = sha256((FORMAT + "|" + transactionKey + "|" + payloadSha256)
                .getBytes(StandardCharsets.US_ASCII));
        return new PreparedSettlement(transactionKey, fingerprint, payloadSha256, receipt,
                inputs.start().predecessorStartNbt(), inputs.start().successorStartNbt(),
                execution.orderedOperations(), execution.finalCells(), execution.foreignCells(),
                execution.batch(), execution.callerRngContinuation());
    }

    private static AcceptedInputs acceptedInputs(Mc263AncientCityProducer.Start start) {
        require(start.worldSeed() == NATURAL_WORLD_SEED
                        && start.chunkX() == NATURAL_START_CHUNK_X
                        && start.chunkZ() == NATURAL_START_CHUNK_Z,
                "Ancient City start is not the accepted E3I5/E3I7/E3I9 natural witness");
        require(start.plan().pieces().size() == PIECE_COUNT,
                "Ancient City accepted start piece cardinality drift");
        require(start.carrier().pieces().size() == PIECE_COUNT,
                "Ancient City accepted carrier piece cardinality drift");
        ArrayList<PieceReceipt> pieces = new ArrayList<>(PIECE_COUNT);
        for (int ordinal = 0; ordinal < PIECE_COUNT; ordinal++) {
            pieces.add(new PieceReceipt(ordinal, start.carrier().pieces().get(ordinal).sha256()));
        }
        NaturalStartAuthority startAuthority = new NaturalStartAuthority(pieces,
                start.carrier().structureStart().bytes(),
                start.carrier().structureStart().mutableSuccessor(), 0, 1,
                SUCCESSOR_CHANGED_BYTE_OFFSET);
        return new AcceptedInputs(evidenceIdentity(), NaturalLootAuthority.accepted(),
                startAuthority, NaturalFeatureAuthority.accepted());
    }

    private static Preflight preflight(Mc263AncientCityProducer.Start start, Clip clip,
            AcceptedInputs inputs, WorldAccess world) {
        validateEvidence(inputs.evidence());
        require(world.supportsVersion(VERSION_ID, SERVER_VERSION, JAVA_RUNTIME_VERSION,
                        INNER_SERVER_SHA256, OUTER_SERVER_SHA256),
                "Ancient City runtime/version source preflight failed");
        for (SourcePin source : inputs.evidence().sources()) {
            require(world.supportsSource(source),
                    "Ancient City accepted source unavailable: " + source.label());
        }
        require(world.supportsExecutionCorpus(EXECUTION_CORPUS_SHA256)
                        && world.supportsStartGraphCarrier(START_GRAPH_CARRIER_SHA256,
                                START_GRAPH_JSON_SHA256)
                        && world.supportsFeatureCodecs(PLACED_FEATURE_CODEC_SHA256,
                                CONFIGURED_FEATURE_CODEC_SHA256),
                "Ancient City accepted grammar/start/feature source closure unavailable");
        require(world.supportsFinalChunkSchema(FINAL_CHUNK_SCHEMA)
                        && world.supportsAtomicReplayableSettlementWithRng()
                        && world.supportsForeignDestinations()
                        && world.supportsCallerRngContinuation()
                        && world.supportsClip(clip),
                "Ancient City settlement/clip capability preflight failed");
        for (SidecarLane lane : SidecarLane.values()) {
            require(world.supportsSidecarLane(lane),
                    "Ancient City sidecar lane unavailable: " + lane);
        }
        require(start.aggregateBoundingBox().intersectsChunk(clip.chunkX(), clip.chunkZ()),
                "Ancient City clip does not intersect accepted adjusted start bounds");
        validateStartAuthority(start, inputs.start());

        Mc263AncientCityGrammar grammar = Mc263AncientCityGrammar.loadAccepted();
        require(Mc263AncientCityGrammar.RESOURCE_SHA256.equals(EXECUTION_CORPUS_SHA256),
                "Ancient City grammar source hash constant drift");
        for (Mc263AncientCityGrammar.Template template : grammar.templatesInEncounterOrder()) {
            Mc263AncientCityProducer.TemplateStatus status = switch (template.status()) {
                case PRESENT -> Mc263AncientCityProducer.TemplateStatus.PRESENT;
                case EXPECTED_ABSENT -> Mc263AncientCityProducer.TemplateStatus.EXPECTED_ABSENT;
            };
            require(world.supportsTemplate(template.id(), status),
                    "Ancient City template capability absent: " + template.id());
            for (String exactState : template.stateTable()) {
                require(world.supportsExactState(exactState),
                        "Ancient City exact-state capability absent: " + exactState);
            }
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.Jigsaw jigsaw) {
                    require(world.supportsExactState(jigsaw.finalState()),
                            "Ancient City jigsaw final-state capability absent: " + jigsaw.finalState());
                } else if (command instanceof Mc263AncientCityGrammar.LootContainer loot) {
                    require(ALLOWED_LOOT_TABLES.contains(loot.lootTable())
                                    && world.supportsLootTable(loot.lootTable())
                                    && world.supportsBlockEntity(loot.blockEntityType()),
                            "Ancient City loot capability/source drift");
                } else if (command instanceof Mc263AncientCityGrammar.Bent bent) {
                    require(world.supportsBlockEntity(bent.payload().blockEntityType()),
                            "Ancient City BENT capability absent: " + bent.payload().blockEntityType());
                }
            }
        }
        for (Mc263AncientCityGrammar.ProcessorListSpec processor
                : grammar.processorListsInEncounterOrder()) {
            require(world.supportsProcessorList(processor.identity()),
                    "Ancient City processor-list capability absent: " + processor.identity());
        }
        for (Mc263AncientCityGrammar.ProcessorSpec processor
                : grammar.processorSemanticsInEncounterOrder()) {
            require(world.supportsProcessorSemantic(processor.identity(), processor.runtimeClass()),
                    "Ancient City processor semantic capability absent: " + processor.identity());
        }
        require(FEATURE_KEY.equals(grammar.configuredFeature().registryKey())
                        && world.supportsConfiguredFeature(FEATURE_KEY),
                "Ancient City configured-feature capability/source drift");
        require(world.supportsCanonicalBlockEntityNbt("minecraft:chest",
                        E3I5_CHEST_NBT_TAGS_IN_ORDER),
                "Ancient City E3I5 canonical chest NBT capability absent");
        for (String exactState : grammar.statesInEncounterOrder()) {
            require(world.supportsExactState(exactState),
                    "Ancient City exact-state closure absent: " + exactState);
        }

        validateLootBindings(start, inputs.loot(), world);
        validateFeaturePieces(start, inputs.feature());
        validatePieces(start, grammar, world);
        return new Preflight(grammar);
    }

    private static void validateEvidence(EvidenceIdentity value) {
        require(value.equals(evidenceIdentity()), "Ancient City evidence identity drift");
    }

    private static void validateStartAuthority(Mc263AncientCityProducer.Start start,
            NaturalStartAuthority authority) {
        require(authority.piecesInStoredOrder().size() == PIECE_COUNT,
                "Ancient City E3I7 piece authority cardinality drift");
        for (int ordinal = 0; ordinal < PIECE_COUNT; ordinal++) {
            PieceReceipt expected = authority.piecesInStoredOrder().get(ordinal);
            require(expected.ordinal() == ordinal
                            && expected.binarySha256().equals(
                                    start.carrier().pieces().get(ordinal).sha256()),
                    "Ancient City E3I7 piece receipt mismatch at ordinal " + ordinal);
        }
        require(Arrays.equals(authority.predecessorStartNbt(),
                        start.carrier().structureStart().bytes()),
                "Ancient City E3I7 predecessor/start carrier mismatch");
    }

    private static void validateLootBindings(Mc263AncientCityProducer.Start start,
            NaturalLootAuthority authority, WorldAccess world) {
        require(authority.bindingsInStoredPieceOrder().size() == E3I5_LOOT_ROWS
                        && authority.destinationChunkCount() == E3I5_LOOT_DESTINATION_CHUNKS,
                "Ancient City E3I5 natural loot aggregate drift");
        for (LootBinding binding : authority.bindingsInStoredPieceOrder()) {
            Mc263AncientCityProducer.Piece piece = start.plan().pieces().get(binding.pieceOrdinal());
            require(pieceContainsTemplate(piece, binding.template()),
                    "Ancient City E3I5 loot piece/template binding drift at row "
                            + binding.encounterOrdinal());
            require(world.supportsLootTable(binding.lootTable())
                            && world.supportsBlockEntity(binding.blockEntityType()),
                    "Ancient City E3I5 loot publication capability absent");
        }
    }

    private static void validateFeaturePieces(Mc263AncientCityProducer.Start start,
            NaturalFeatureAuthority authority) {
        require(authority.featurePieceOrdinals().equals(E3I9_FEATURE_PIECES),
                "Ancient City E3I9 feature piece order drift");
        for (int ordinal : authority.featurePieceOrdinals()) {
            Mc263AncientCityProducer.Piece piece = start.plan().pieces().get(ordinal);
            require(piece.kind() == Mc263AncientCityProducer.PieceKind.FEATURE
                            && FEATURE_KEY.equals(piece.elementKey()),
                    "Ancient City E3I9 feature piece identity drift at ordinal " + ordinal);
        }
        ArrayList<Integer> actual = new ArrayList<>();
        for (Mc263AncientCityProducer.Piece piece : start.plan().pieces()) {
            if (piece.kind() == Mc263AncientCityProducer.PieceKind.FEATURE) {
                actual.add(piece.ordinal());
            }
        }
        require(actual.equals(authority.featurePieceOrdinals()),
                "Ancient City accepted start feature topology drift");
    }

    private static void validatePieces(Mc263AncientCityProducer.Start start,
            Mc263AncientCityGrammar grammar, WorldAccess world) {
        for (int ordinal = 0; ordinal < start.plan().pieces().size(); ordinal++) {
            Mc263AncientCityProducer.Piece piece = start.plan().pieces().get(ordinal);
            require(piece.ordinal() == ordinal,
                    "Ancient City stored piece ordinal drift at " + ordinal);
            switch (piece.kind()) {
                case TEMPLATE -> {
                    Mc263AncientCityGrammar.Template template = grammar.requireTemplate(piece.elementKey());
                    require(template.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT,
                            "Ancient City generated expected-absent template");
                    require(world.supportsProcessorList(piece.processor()),
                            "Ancient City piece processor capability absent: " + piece.processor());
                }
                case LIST -> {
                    require(!piece.children().isEmpty(), "Ancient City list piece lost children");
                    for (Mc263AncientCityProducer.ListChild child : piece.children()) {
                        require(grammar.requireTemplate(child.template()).status()
                                        == Mc263AncientCityGrammar.SourceStatus.PRESENT
                                        && world.supportsProcessorList(child.processor()),
                                "Ancient City list child template/processor drift");
                    }
                }
                case FEATURE -> require(FEATURE_KEY.equals(piece.elementKey()),
                        "unknown Ancient City feature piece");
            }
        }
    }

    private static boolean pieceContainsTemplate(Mc263AncientCityProducer.Piece piece,
            String template) {
        if (piece.kind() == Mc263AncientCityProducer.PieceKind.TEMPLATE) {
            return piece.elementKey().equals(template);
        }
        if (piece.kind() == Mc263AncientCityProducer.PieceKind.LIST) {
            return piece.children().stream().anyMatch(child -> child.template().equals(template));
        }
        return false;
    }

    private static void validateExecution(Clip clip, ExecutionResult result) {
        long expectedOrdinal = 0L;
        for (Operation operation : result.orderedOperations()) {
            require(operation.ordinal() == expectedOrdinal++,
                    "Ancient City execution operation order drift");
            require(operation.pieceOrdinal() >= 0 && operation.pieceOrdinal() < PIECE_COUNT,
                    "Ancient City execution operation piece escaped accepted start");
        }
        HashSet<Position> unique = new HashSet<>();
        for (FinalCell cell : result.finalCells()) {
            require(unique.add(cell.position()), "duplicate Ancient City final cell");
            require(cell.pieceOrdinal() >= 0 && cell.pieceOrdinal() < PIECE_COUNT,
                    "Ancient City final cell piece escaped accepted start");
        }
        List<FinalCell> expectedForeign = result.finalCells().stream()
                .filter(cell -> !clip.containsHorizontal(cell.position())).toList();
        require(expectedForeign.equals(result.foreignCells()),
                "Ancient City foreign-cell projection drift");
        List<Mc263StructureBatchBridge.BlockWrite> blocks = result.batch().blocks();
        require(blocks.size() == result.finalCells().size(),
                "Ancient City final-cell/schema-4 block cardinality drift");
        for (int index = 0; index < blocks.size(); index++) {
            Mc263StructureBatchBridge.BlockWrite block = blocks.get(index);
            FinalCell cell = result.finalCells().get(index);
            require(block.blockX() == cell.position().x()
                            && block.blockY() == cell.position().y()
                            && block.blockZ() == cell.position().z()
                            && block.owner() == cell.owner()
                            && block.exactState().equals(cell.exactState()),
                    "Ancient City final-cell/schema-4 block identity drift");
        }
        RngContinuation rng = result.callerRngContinuation();
        require(rng.continuationNextLongI64().size() == 4,
                "Ancient City caller RNG continuation cardinality drift");
    }

    private static byte[] canonicalReceipt(ExecutionRequest request, ExecutionResult execution) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeString(out, FORMAT);
                writeEvidence(out, request.inputs().evidence());
                Clip clip = request.clip();
                out.writeInt(clip.chunkX()); out.writeInt(clip.chunkZ());
                out.writeInt(clip.minYInclusive()); out.writeInt(clip.maxYInclusive());
                writeStartAuthority(out, request.inputs().start());
                writeLootAuthority(out, request.inputs().loot());
                writeFeatureAuthority(out, request.inputs().feature());
                out.writeInt(execution.orderedOperations().size());
                for (Operation operation : execution.orderedOperations()) {
                    out.writeLong(operation.ordinal()); out.writeInt(operation.pieceOrdinal());
                    writeString(out, operation.kind().name()); writePosition(out, operation.position());
                    writeString(out, operation.identity());
                }
                writeCells(out, execution.finalCells());
                writeCells(out, execution.foreignCells());
                writeBatch(out, execution.batch());
                writeRng(out, execution.callerRngContinuation());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("failed to encode Ancient City settlement receipt", impossible);
        }
    }

    private static void writeEvidence(DataOutputStream out, EvidenceIdentity value) throws IOException {
        writeString(out, value.versionId()); writeString(out, value.serverVersion());
        writeString(out, value.javaRuntimeVersion()); writeString(out, value.innerServerSha256());
        writeString(out, value.outerServerSha256()); writeString(out, value.executionCorpusSha256());
        writeString(out, value.startGraphCarrierSha256()); writeString(out, value.startGraphJsonSha256());
        writeString(out, value.placedFeatureCodecSha256());
        writeString(out, value.configuredFeatureCodecSha256());
        out.writeInt(value.sources().size());
        for (SourcePin source : value.sources()) {
            writeString(out, source.label()); writeString(out, source.resourcePath());
            out.writeInt(source.bytes()); writeString(out, source.sha256());
            writeString(out, source.oracleSourceSha256()); writeString(out, source.wrapperSourceSha256());
        }
    }

    private static void writeStartAuthority(DataOutputStream out, NaturalStartAuthority value)
            throws IOException {
        out.writeInt(value.piecesInStoredOrder().size());
        for (PieceReceipt piece : value.piecesInStoredOrder()) {
            out.writeInt(piece.ordinal()); writeString(out, piece.binarySha256());
        }
        writeBytes(out, value.predecessorStartNbt()); writeBytes(out, value.successorStartNbt());
        out.writeInt(value.predecessorReferences()); out.writeInt(value.successorReferences());
        out.writeInt(value.changedByteOffset());
    }

    private static void writeLootAuthority(DataOutputStream out, NaturalLootAuthority value)
            throws IOException {
        out.writeInt(value.destinationChunkCount());
        out.writeInt(value.bindingsInStoredPieceOrder().size());
        for (LootBinding row : value.bindingsInStoredPieceOrder()) {
            out.writeInt(row.encounterOrdinal()); out.writeInt(row.pieceOrdinal());
            writeString(out, row.template()); writeString(out, row.lootTable());
            writeString(out, row.blockEntityType());
        }
    }

    private static void writeFeatureAuthority(DataOutputStream out, NaturalFeatureAuthority value)
            throws IOException {
        writeString(out, value.featureKey()); out.writeInt(value.featurePieceOrdinals().size());
        for (int ordinal : value.featurePieceOrdinals()) out.writeInt(ordinal);
        out.writeInt(value.sourceChunkCount()); out.writeInt(value.destinationChunkCount());
        out.writeInt(value.queryCount()); out.writeInt(value.writeCount());
        out.writeInt(value.foreignWriteCount()); out.writeInt(value.finalCellRows());
        out.writeInt(value.uniqueFinalCells()); out.writeInt(value.rawTranscriptBytes());
        writeString(out, value.rawTranscriptSha256()); writeString(out, value.summarySha256());
    }

    private static void writeCells(DataOutputStream out, List<FinalCell> cells) throws IOException {
        out.writeInt(cells.size());
        for (FinalCell cell : cells) {
            writePosition(out, cell.position()); writeString(out, cell.exactState());
            out.writeLong(cell.owner()); out.writeInt(cell.pieceOrdinal());
            writeString(out, cell.sourceIdentity());
        }
    }

    private static void writeBatch(DataOutputStream out, Mc263StructureBatchBridge.Batch batch)
            throws IOException {
        out.writeInt(batch.blocks().size());
        for (Mc263StructureBatchBridge.BlockWrite value : batch.blocks()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.exactState()); out.writeLong(value.owner());
        }
        out.writeInt(batch.loot().size());
        for (Mc263StructureBatchBridge.Loot value : batch.loot()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.lootTable()); out.writeLong(value.lootSeed());
        }
        out.writeInt(batch.archaeology().size());
        for (Mc263StructureBatchBridge.Archaeology value : batch.archaeology()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.lootTable()); out.writeLong(value.lootSeed());
        }
        out.writeInt(batch.blockEntities().size());
        for (Mc263StructureBatchBridge.BentEvidence value : batch.blockEntities()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.blockIdentity()); writeString(out, value.entityType());
            writeBytes(out, value.canonicalNbt());
        }
        out.writeInt(batch.entities().size());
        for (Mc263FinalChunkSidecars.StructureEntity value : batch.entities()) {
            writeString(out, value.entityKey()); writeString(out, value.spawnReason());
            out.writeLong(Double.doubleToRawLongBits(value.x()));
            out.writeLong(Double.doubleToRawLongBits(value.y()));
            out.writeLong(Double.doubleToRawLongBits(value.z()));
            out.writeInt(Float.floatToRawIntBits(value.yaw()));
            out.writeInt(Float.floatToRawIntBits(value.pitch()));
            out.writeLong(Double.doubleToRawLongBits(value.velocityX()));
            out.writeLong(Double.doubleToRawLongBits(value.velocityY()));
            out.writeLong(Double.doubleToRawLongBits(value.velocityZ()));
            writeString(out, value.lootTable()); out.writeLong(value.lootSeed());
            writeBytes(out, value.canonicalPayload());
        }
        out.writeInt(batch.fluidTicks().size());
        for (Mc263StructureBatchBridge.FluidTick value : batch.fluidTicks()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.fluidKey()); out.writeInt(value.delay());
            out.writeInt(value.priority()); out.writeLong(value.subTickOrder());
        }
        out.writeInt(batch.postprocessMarks().size());
        for (Mc263StructureBatchBridge.PostprocessMark value : batch.postprocessMarks()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
        }
        out.writeInt(batch.spawners().size());
        for (Mc263StructureBatchBridge.Spawner value : batch.spawners()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.entityType());
        }
        out.writeInt(batch.blockTicks().size());
        for (Mc263StructureBatchBridge.BlockTick value : batch.blockTicks()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            writeString(out, value.blockKey()); out.writeInt(value.delay());
            out.writeInt(value.priority()); out.writeLong(value.subTickOrder());
        }
        out.writeInt(batch.bees().size());
        for (Mc263StructureBatchBridge.Bee value : batch.bees()) {
            out.writeInt(value.blockX()); out.writeInt(value.blockY()); out.writeInt(value.blockZ());
            out.writeInt(value.ticksInHive());
        }
    }

    private static void writeRng(DataOutputStream out, RngContinuation value) throws IOException {
        writeString(out, value.algorithm()); out.writeInt(value.worldgenCount());
        out.writeLong(value.seedLoI64()); out.writeLong(value.seedHiI64());
        out.writeInt(value.continuationNextLongI64().size());
        for (long word : value.continuationNextLongI64()) out.writeLong(word);
    }

    private static void writePosition(DataOutputStream out, Position value) throws IOException {
        out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z());
    }

    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length); out.write(value);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static LootBinding loot(int row, int piece, String template, String table) {
        return new LootBinding(row, piece, template, table, "minecraft:chest");
    }

    private static String pieceSeal(List<PieceReceipt> pieces) {
        StringBuilder text = new StringBuilder();
        for (PieceReceipt piece : pieces) {
            text.append(piece.ordinal()).append('|').append(piece.binarySha256()).append('\n');
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String lootSeal(List<LootBinding> rows) {
        StringBuilder text = new StringBuilder();
        for (LootBinding row : rows) {
            text.append(row.encounterOrdinal()).append('|').append(row.pieceOrdinal()).append('|')
                    .append(row.template()).append('|').append(row.lootTable()).append('|')
                    .append(row.blockEntityType()).append('\n');
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static void registryKey(String value, String label) {
        require(value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),
                label + " is not a registry key");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public enum SidecarLane { LOOT, ARCH, BENT, ENTS, FTIK, PPRC, SPWN, BTIK, BEES }

    public record NbtTagSpec(String name, int typeId) {
        public NbtTagSpec {
            require(name != null && !name.isBlank() && typeId >= 1 && typeId <= 12,
                    "Ancient City NBT tag schema malformed");
        }
    }
    public enum OperationKind { QUERY, WRITE, FOREIGN_WRITE, SIDE_EFFECT }
    public enum PublishStatus { COMMITTED, REPLAYED }

    public record SourcePin(String label, String resourcePath, int bytes, String sha256,
            String oracleSourceSha256, String wrapperSourceSha256) {
        public SourcePin {
            require(label != null && !label.isBlank(), "Ancient City source label absent");
            require(resourcePath != null && resourcePath.startsWith("/mc263/"),
                    "Ancient City source resource path drift");
            require(bytes > 0 && hash(sha256) && hash(oracleSourceSha256)
                            && hash(wrapperSourceSha256),
                    "Ancient City source receipt malformed");
        }
    }

    public record EvidenceIdentity(String versionId, String serverVersion, String javaRuntimeVersion,
            String innerServerSha256, String outerServerSha256, String executionCorpusSha256,
            String startGraphCarrierSha256, String startGraphJsonSha256,
            String placedFeatureCodecSha256, String configuredFeatureCodecSha256,
            List<SourcePin> sources) {
        public EvidenceIdentity {
            require(VERSION_ID.equals(versionId) && SERVER_VERSION.equals(serverVersion)
                            && JAVA_RUNTIME_VERSION.equals(javaRuntimeVersion),
                    "Ancient City source version drift");
            require(hash(innerServerSha256) && hash(outerServerSha256)
                            && hash(executionCorpusSha256) && hash(startGraphCarrierSha256)
                            && hash(startGraphJsonSha256) && hash(placedFeatureCodecSha256)
                            && hash(configuredFeatureCodecSha256),
                    "Ancient City source hash malformed");
            sources = List.copyOf(Objects.requireNonNull(sources, "Ancient City sources"));
            require(sources.size() == 3, "Ancient City E3I5/E3I7/E3I9 source cardinality drift");
        }
    }

    public record LootBinding(int encounterOrdinal, int pieceOrdinal, String template,
            String lootTable, String blockEntityType) {
        public LootBinding {
            require(encounterOrdinal >= 0 && pieceOrdinal >= 0 && pieceOrdinal < PIECE_COUNT,
                    "Ancient City loot binding ordinal drift");
            registryKey(template, "Ancient City loot template");
            require(ALLOWED_LOOT_TABLES.contains(lootTable), "unrelated Ancient City loot table");
            require("minecraft:chest".equals(blockEntityType), "unrelated Ancient City loot BENT type");
        }
    }

    public record NaturalLootAuthority(int destinationChunkCount,
            List<LootBinding> bindingsInStoredPieceOrder) {
        public NaturalLootAuthority {
            bindingsInStoredPieceOrder = List.copyOf(Objects.requireNonNull(
                    bindingsInStoredPieceOrder, "Ancient City E3I5 loot bindings"));
            require(destinationChunkCount == E3I5_LOOT_DESTINATION_CHUNKS
                            && bindingsInStoredPieceOrder.size() == E3I5_LOOT_ROWS,
                    "Ancient City E3I5 loot aggregate drift");
            for (int index = 0; index < bindingsInStoredPieceOrder.size(); index++) {
                require(bindingsInStoredPieceOrder.get(index).encounterOrdinal() == index,
                        "Ancient City E3I5 loot encounter order drift");
            }
            require(E3I5_LOOT_SEMANTICS_SHA256.equals(lootSeal(bindingsInStoredPieceOrder)),
                    "Ancient City E3I5 semantic binding seal drift");
        }
        static NaturalLootAuthority accepted() {
            return new NaturalLootAuthority(E3I5_LOOT_DESTINATION_CHUNKS, E3I5_LOOT_BINDINGS);
        }
    }

    public record PieceReceipt(int ordinal, String binarySha256) {
        public PieceReceipt {
            require(ordinal >= 0 && ordinal < PIECE_COUNT && hash(binarySha256),
                    "Ancient City E3I7 piece receipt malformed");
        }
    }

    public static final class NaturalStartAuthority {
        private final List<PieceReceipt> piecesInStoredOrder;
        private final byte[] predecessorStartNbt;
        private final byte[] successorStartNbt;
        private final int predecessorReferences;
        private final int successorReferences;
        private final int changedByteOffset;

        public NaturalStartAuthority(List<PieceReceipt> piecesInStoredOrder,
                byte[] predecessorStartNbt, byte[] successorStartNbt,
                int predecessorReferences, int successorReferences, int changedByteOffset) {
            this.piecesInStoredOrder = List.copyOf(Objects.requireNonNull(
                    piecesInStoredOrder, "Ancient City E3I7 piece receipts"));
            require(this.piecesInStoredOrder.size() == PIECE_COUNT,
                    "Ancient City E3I7 piece receipt cardinality drift");
            for (int index = 0; index < PIECE_COUNT; index++) {
                require(this.piecesInStoredOrder.get(index).ordinal() == index,
                        "Ancient City E3I7 piece receipt order drift");
            }
            require(E3I7_PIECE_LIST_SHA256.equals(pieceSeal(this.piecesInStoredOrder)),
                    "Ancient City E3I7 piece-list seal drift");
            this.predecessorStartNbt = Objects.requireNonNull(
                    predecessorStartNbt, "Ancient City predecessor NBT").clone();
            this.successorStartNbt = Objects.requireNonNull(
                    successorStartNbt, "Ancient City successor NBT").clone();
            this.predecessorReferences = predecessorReferences;
            this.successorReferences = successorReferences;
            this.changedByteOffset = changedByteOffset;
            require(this.predecessorStartNbt.length == START_NBT_BYTES
                            && this.successorStartNbt.length == START_NBT_BYTES
                            && PREDECESSOR_SHA256.equals(sha256(this.predecessorStartNbt))
                            && SUCCESSOR_SHA256.equals(sha256(this.successorStartNbt)),
                    "Ancient City E3I7 predecessor/successor receipt drift");
            require(predecessorReferences == 0 && successorReferences == 1
                            && changedByteOffset == SUCCESSOR_CHANGED_BYTE_OFFSET,
                    "Ancient City E3I7 reference transition drift");
            int differences = 0;
            int offset = -1;
            for (int index = 0; index < START_NBT_BYTES; index++) {
                if (this.predecessorStartNbt[index] == this.successorStartNbt[index]) continue;
                differences++; offset = index;
            }
            require(differences == 1 && offset == changedByteOffset
                            && this.predecessorStartNbt[offset] == 0
                            && this.successorStartNbt[offset] == 1,
                    "Ancient City E3I7 successor is not exact references 0->1");
        }
        public List<PieceReceipt> piecesInStoredOrder() { return piecesInStoredOrder; }
        public byte[] predecessorStartNbt() { return predecessorStartNbt.clone(); }
        public byte[] successorStartNbt() { return successorStartNbt.clone(); }
        public int predecessorReferences() { return predecessorReferences; }
        public int successorReferences() { return successorReferences; }
        public int changedByteOffset() { return changedByteOffset; }
    }

    public record NaturalFeatureAuthority(String featureKey, List<Integer> featurePieceOrdinals,
            int sourceChunkCount, int destinationChunkCount, int queryCount, int writeCount,
            int foreignWriteCount, int finalCellRows, int uniqueFinalCells,
            int rawTranscriptBytes, String rawTranscriptSha256, String summarySha256) {
        public NaturalFeatureAuthority {
            registryKey(featureKey, "Ancient City feature key");
            featurePieceOrdinals = List.copyOf(Objects.requireNonNull(
                    featurePieceOrdinals, "Ancient City feature piece ordinals"));
            require(FEATURE_KEY.equals(featureKey) && featurePieceOrdinals.equals(E3I9_FEATURE_PIECES),
                    "Ancient City E3I9 feature topology drift");
            require(sourceChunkCount == E3I9_SOURCE_CHUNKS
                            && destinationChunkCount == E3I9_DESTINATION_CHUNKS
                            && queryCount == E3I9_QUERY_COUNT && writeCount == E3I9_WRITE_COUNT
                            && foreignWriteCount == E3I9_FOREIGN_WRITE_COUNT
                            && finalCellRows == E3I9_FINAL_CELL_ROWS
                            && uniqueFinalCells == E3I9_UNIQUE_FINAL_CELLS
                            && rawTranscriptBytes == E3I9_RAW_TRANSCRIPT_BYTES
                            && E3I9_RAW_TRANSCRIPT_SHA256.equals(rawTranscriptSha256)
                            && E3I9_SUMMARY_SHA256.equals(summarySha256),
                    "Ancient City E3I9 aggregate/source receipt drift");
        }
        static NaturalFeatureAuthority accepted() {
            return new NaturalFeatureAuthority(FEATURE_KEY, E3I9_FEATURE_PIECES,
                    E3I9_SOURCE_CHUNKS, E3I9_DESTINATION_CHUNKS, E3I9_QUERY_COUNT,
                    E3I9_WRITE_COUNT, E3I9_FOREIGN_WRITE_COUNT, E3I9_FINAL_CELL_ROWS,
                    E3I9_UNIQUE_FINAL_CELLS, E3I9_RAW_TRANSCRIPT_BYTES,
                    E3I9_RAW_TRANSCRIPT_SHA256, E3I9_SUMMARY_SHA256);
        }
    }

    public record AcceptedInputs(EvidenceIdentity evidence, NaturalLootAuthority loot,
            NaturalStartAuthority start, NaturalFeatureAuthority feature) {
        public AcceptedInputs {
            Objects.requireNonNull(evidence, "Ancient City evidence identity");
            Objects.requireNonNull(loot, "Ancient City E3I5 authority");
            Objects.requireNonNull(start, "Ancient City E3I7 authority");
            Objects.requireNonNull(feature, "Ancient City E3I9 authority");
        }
    }

    public record Clip(int chunkX, int chunkZ, int minYInclusive, int maxYInclusive) {
        public Clip {
            require(minYInclusive >= MIN_CLIP_Y && maxYInclusive <= MAX_CLIP_Y
                            && minYInclusive <= maxYInclusive,
                    "Ancient City clip escaped authenticated build-height interval");
        }
        public boolean containsHorizontal(Position position) {
            return Math.floorDiv(position.x(), 16) == chunkX
                    && Math.floorDiv(position.z(), 16) == chunkZ;
        }
    }

    public record Position(int x, int y, int z) { }

    public record Operation(long ordinal, int pieceOrdinal, OperationKind kind,
            Position position, String identity) {
        public Operation {
            require(ordinal >= 0 && pieceOrdinal >= 0 && pieceOrdinal < PIECE_COUNT,
                    "Ancient City operation ordinal drift");
            Objects.requireNonNull(kind, "Ancient City operation kind");
            Objects.requireNonNull(position, "Ancient City operation position");
            require(identity != null && !identity.isBlank(), "Ancient City operation identity absent");
        }
    }

    public record FinalCell(Position position, String exactState, long owner, int pieceOrdinal,
            String sourceIdentity) {
        public FinalCell {
            Objects.requireNonNull(position, "Ancient City final-cell position");
            require(exactState != null && !exactState.isBlank(), "Ancient City final-cell state absent");
            require(pieceOrdinal >= 0 && pieceOrdinal < PIECE_COUNT,
                    "Ancient City final-cell piece ordinal drift");
            require(sourceIdentity != null && !sourceIdentity.isBlank(),
                    "Ancient City final-cell source identity absent");
        }
    }

    public record RngContinuation(String algorithm, int worldgenCount, long seedLoI64,
            long seedHiI64, List<Long> continuationNextLongI64) {
        public RngContinuation {
            require(algorithm != null && !algorithm.isBlank() && worldgenCount >= 0,
                    "Ancient City caller RNG receipt malformed");
            continuationNextLongI64 = List.copyOf(Objects.requireNonNull(
                    continuationNextLongI64, "Ancient City caller RNG continuation"));
            require(continuationNextLongI64.size() == 4,
                    "Ancient City caller RNG continuation must contain four words");
        }
    }

    public record ExecutionResult(List<Operation> orderedOperations, List<FinalCell> finalCells,
            List<FinalCell> foreignCells, Mc263StructureBatchBridge.Batch batch,
            RngContinuation callerRngContinuation) {
        public ExecutionResult {
            orderedOperations = List.copyOf(Objects.requireNonNull(
                    orderedOperations, "Ancient City ordered operations"));
            finalCells = List.copyOf(Objects.requireNonNull(finalCells, "Ancient City final cells"));
            foreignCells = List.copyOf(Objects.requireNonNull(
                    foreignCells, "Ancient City foreign cells"));
            Objects.requireNonNull(batch, "Ancient City complete schema-4 batch");
            Objects.requireNonNull(callerRngContinuation, "Ancient City caller RNG continuation");
        }
    }

    public record ExecutionRequest(Mc263AncientCityProducer.Start start, Clip clip,
            AcceptedInputs inputs, Mc263AncientCityGrammar grammar) {
        public ExecutionRequest {
            Objects.requireNonNull(start, "Ancient City execution start");
            Objects.requireNonNull(clip, "Ancient City execution clip");
            Objects.requireNonNull(inputs, "Ancient City execution inputs");
            Objects.requireNonNull(grammar, "Ancient City execution grammar");
        }
    }

    /**
     * Product/runtime boundary. Every supports* method must be pure. executeProcedurally is invoked
     * only after complete preflight and must use an unpublished fork plus a copy of caller RNG; it
     * must discard/roll back both on any error. It may consult the typed grammar but must not replay
     * E3I5/E3I9 finite coordinate rows. The returned result must be a complete immutable receipt.
     */
    public interface WorldAccess {
        boolean supportsVersion(String versionId, String serverVersion, String javaRuntimeVersion,
                String innerServerSha256, String outerServerSha256);
        boolean supportsSource(SourcePin source);
        boolean supportsExecutionCorpus(String sha256);
        boolean supportsStartGraphCarrier(String carrierSha256, String jsonSha256);
        boolean supportsFeatureCodecs(String placedFeatureCodecSha256,
                String configuredFeatureCodecSha256);
        boolean supportsFinalChunkSchema(int schema);
        boolean supportsAtomicReplayableSettlementWithRng();
        boolean supportsForeignDestinations();
        boolean supportsCallerRngContinuation();
        boolean supportsClip(Clip clip);
        boolean supportsSidecarLane(SidecarLane lane);
        boolean supportsTemplate(String template, Mc263AncientCityProducer.TemplateStatus status);
        boolean supportsProcessorList(String processorIdentity);
        boolean supportsProcessorSemantic(String semanticIdentity, String runtimeClass);
        boolean supportsConfiguredFeature(String featureKey);
        boolean supportsExactState(String exactState);
        boolean supportsLootTable(String lootTable);
        boolean supportsBlockEntity(String blockEntityType);
        boolean supportsCanonicalBlockEntityNbt(String blockEntityType,
                List<NbtTagSpec> tagsInCanonicalOrder);
        ExecutionResult executeProcedurally(ExecutionRequest request);
    }

    /**
     * Replay-aware atomic publication boundary. A sink must install the exact complete payload,
     * successor and caller RNG continuation together. Exact transaction-key/fingerprint/payload
     * replay returns REPLAYED with zero duplicate mutation. Any existing-key mismatch or any error
     * must throw after rolling back every world, sidecar, successor and RNG effect.
     */
    public interface AtomicSettlementSink {
        boolean supportsAtomicReplayableSettlementWithRng();
        PublishStatus commitAtomically(CommitRequest request);
    }

    public static final class CommitRequest {
        private final String transactionKey;
        private final String fingerprintSha256;
        private final String payloadSha256;
        private final byte[] canonicalReceipt;
        private final byte[] predecessorStartNbt;
        private final byte[] successorStartNbt;
        private final List<Operation> orderedOperations;
        private final List<FinalCell> finalCells;
        private final List<FinalCell> foreignCells;
        private final Mc263StructureBatchBridge.Batch batch;
        private final RngContinuation callerRngContinuation;

        public CommitRequest(String transactionKey, String fingerprintSha256, String payloadSha256,
                byte[] canonicalReceipt, byte[] predecessorStartNbt, byte[] successorStartNbt,
                List<Operation> orderedOperations, List<FinalCell> finalCells,
                List<FinalCell> foreignCells, Mc263StructureBatchBridge.Batch batch,
                RngContinuation callerRngContinuation) {
            require(transactionKey != null && !transactionKey.isBlank(),
                    "Ancient City transaction key absent");
            require(hash(fingerprintSha256) && hash(payloadSha256),
                    "Ancient City commit fingerprint/payload hash malformed");
            this.transactionKey = transactionKey;
            this.fingerprintSha256 = fingerprintSha256;
            this.payloadSha256 = payloadSha256;
            this.canonicalReceipt = Objects.requireNonNull(
                    canonicalReceipt, "Ancient City canonical receipt").clone();
            require(payloadSha256.equals(sha256(this.canonicalReceipt)),
                    "Ancient City commit payload hash mismatch");
            String expectedFingerprint = sha256((FORMAT + "|" + transactionKey + "|" + payloadSha256)
                    .getBytes(StandardCharsets.US_ASCII));
            require(fingerprintSha256.equals(expectedFingerprint),
                    "Ancient City commit fingerprint mismatch");
            this.predecessorStartNbt = Objects.requireNonNull(
                    predecessorStartNbt, "Ancient City predecessor NBT").clone();
            this.successorStartNbt = Objects.requireNonNull(
                    successorStartNbt, "Ancient City successor NBT").clone();
            require(this.predecessorStartNbt.length == START_NBT_BYTES
                            && this.successorStartNbt.length == START_NBT_BYTES
                            && PREDECESSOR_SHA256.equals(sha256(this.predecessorStartNbt))
                            && SUCCESSOR_SHA256.equals(sha256(this.successorStartNbt)),
                    "Ancient City commit predecessor/successor drift");
            this.orderedOperations = List.copyOf(Objects.requireNonNull(
                    orderedOperations, "Ancient City commit operations"));
            this.finalCells = List.copyOf(Objects.requireNonNull(finalCells, "Ancient City commit final cells"));
            this.foreignCells = List.copyOf(Objects.requireNonNull(
                    foreignCells, "Ancient City commit foreign cells"));
            this.batch = Objects.requireNonNull(batch, "Ancient City commit batch");
            this.callerRngContinuation = Objects.requireNonNull(
                    callerRngContinuation, "Ancient City commit RNG continuation");
        }
        public String transactionKey() { return transactionKey; }
        public String fingerprintSha256() { return fingerprintSha256; }
        public String payloadSha256() { return payloadSha256; }
        public byte[] canonicalReceipt() { return canonicalReceipt.clone(); }
        public byte[] predecessorStartNbt() { return predecessorStartNbt.clone(); }
        public byte[] successorStartNbt() { return successorStartNbt.clone(); }
        public List<Operation> orderedOperations() { return orderedOperations; }
        public List<FinalCell> finalCells() { return finalCells; }
        public List<FinalCell> foreignCells() { return foreignCells; }
        public Mc263StructureBatchBridge.Batch batch() { return batch; }
        public RngContinuation callerRngContinuation() { return callerRngContinuation; }
    }

    /** Fully immutable prepared settlement; only the atomic sink may make it visible. */
    public static final class PreparedSettlement {
        private final CommitRequest request;
        private PreparedSettlement(String transactionKey, String fingerprintSha256,
                String payloadSha256, byte[] canonicalReceipt, byte[] predecessorStartNbt,
                byte[] successorStartNbt, List<Operation> orderedOperations,
                List<FinalCell> finalCells, List<FinalCell> foreignCells,
                Mc263StructureBatchBridge.Batch batch, RngContinuation callerRngContinuation) {
            this.request = new CommitRequest(transactionKey, fingerprintSha256, payloadSha256,
                    canonicalReceipt, predecessorStartNbt, successorStartNbt, orderedOperations,
                    finalCells, foreignCells, batch, callerRngContinuation);
        }
        public String transactionKey() { return request.transactionKey(); }
        public String fingerprintSha256() { return request.fingerprintSha256(); }
        public String payloadSha256() { return request.payloadSha256(); }
        public byte[] canonicalReceipt() { return request.canonicalReceipt(); }
        public byte[] predecessorStartNbt() { return request.predecessorStartNbt(); }
        public byte[] successorStartNbt() { return request.successorStartNbt(); }
        public List<Operation> orderedOperations() { return request.orderedOperations(); }
        public List<FinalCell> finalCells() { return request.finalCells(); }
        public List<FinalCell> foreignCells() { return request.foreignCells(); }
        public Mc263StructureBatchBridge.Batch batch() { return request.batch(); }
        public RngContinuation callerRngContinuation() { return request.callerRngContinuation(); }
        public CommitRequest commitRequest() {
            return new CommitRequest(request.transactionKey(), request.fingerprintSha256(),
                    request.payloadSha256(), request.canonicalReceipt(),
                    request.predecessorStartNbt(), request.successorStartNbt(),
                    request.orderedOperations(), request.finalCells(), request.foreignCells(),
                    request.batch(), request.callerRngContinuation());
        }
        public PublishStatus publish(AtomicSettlementSink sink) {
            Objects.requireNonNull(sink, "Ancient City atomic settlement sink");
            require(sink.supportsAtomicReplayableSettlementWithRng(),
                    "Ancient City atomic replay/RNG publication capability unavailable");
            PublishStatus status = Objects.requireNonNull(sink.commitAtomically(commitRequest()),
                    "Ancient City atomic publish result");
            require(status == PublishStatus.COMMITTED || status == PublishStatus.REPLAYED,
                    "unknown Ancient City atomic publish status");
            return status;
        }
    }

    private record Preflight(Mc263AncientCityGrammar grammar) { }
}
