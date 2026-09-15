package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Dormant strict typed reader for the authenticated 26.3 Trial Chambers execution grammar. */
final class Mc263TrialChambersGrammar {
    static final String RESOURCE = "mc263/trial-chambers-execution-corpus-v1.json";
    static final String BENT_RESOURCE = "mc263/trial-chambers-bent-evidence-v2.json";
    static final int PAYLOAD_BYTES = 4_992_879;
    static final int BENT_PAYLOAD_BYTES = 13_208;
    static final String BENT_PAYLOAD_SHA256 =
            "e0982cbd3dbd72bc47894cc5221f5512234b7a5f3e689ddc7b94657640045229";
    static final String PAYLOAD_SHA256 =
            "6d4114be756f82d219ba10dc735d6fb5d372fbd45332a0e25be8e25216255e89";
    static final int SCHEMA = 1;
    static final int BENT_SCHEMA = 2;
    static final String RECEIPT_ID = "TRL-G1C";
    static final String BENT_RECEIPT_ID = "TRL-G1L-BENT-V2";
    static final int JAVA_VERSION = 25;
    static final String SERVER_VERSION = "26.3 Snapshot 7";
    static final int POOL_COUNT = 47;
    static final int SINGLE_POOL_ELEMENT_COUNT = 207;
    static final int EMPTY_POOL_ELEMENT_COUNT = 6;
    static final int FEATURE_POOL_ELEMENT_COUNT = 0;
    static final int TEMPLATE_COUNT = 191;
    static final int BLOCK_COUNT = 264_296;
    static final int COMMAND_COUNT = 56_475;
    static final int CONNECTOR_COUNT = 834;
    static final int TEMPLATE_ENTITY_COUNT = 0;
    static final int STATE_COUNT = 202;

    private static final String POOL_SEMANTIC_SHA256 =
            "ed62a0a57a31cae555c8e5bc2410675d9235ab021ed2375a2356aa8e14a41cc5";
    private static final String TEMPLATE_ENCOUNTER_SHA256 =
            "96000cfb35ef7c2872e107ded149b85d8b4e4593e243f7a9c69f0b371c1b4cb8";
    private static final String CONNECTOR_SEMANTIC_SHA256 =
            "4ed7745800bc90538412186eb509b627f560e05f0022b4c42564f225e8f15322";
    private static final String STATE_CLOSURE_SHA256 =
            "6f17ed2a9103dfb21ae2ce5493827794dcfb16d4370c702b26c9676a6f272a1b";
    private static final String CLASS_CLOSURE_SHA256 =
            "de0c3a69acd7382658bfd2818633656dcf69cfa339c337672f41a1e6647c201a";
    private static final String STRUCTURE_CODEC_SHA256 =
            "478137ed0350c99f868edd0ecd27fbbb7b6500c82599ab7cf7086aeaa3dcec8a";
    private static final String STRUCTURE_SET_CODEC_SHA256 =
            "5edeb057ba331489950d3316a12274dc825ae5c31f0d555ec1308b9aac55dc2a";
    private static final String STRUCTURE_KEY = "minecraft:trial_chambers";
    private static final String START_POOL = "minecraft:trial_chambers/chamber/end";
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final String HALLWAY_FALLBACK = "minecraft:trial_chambers/hallway/fallback";
    private static final String BOUNDARY =
            "semantic execution corpus only; no seed/chunk generation probe, generated layout, raw Mojang template NBT, finite coordinate lookup or unauthenticated sidecar";
    private static final String FEATURE_BOUNDARY =
            "the exact 47-pool Trial Chambers closure contains no FeaturePoolElement; no pool-driven configured feature is executable in this corpus";
    private static final String IGNORED_STRUCTURE_BLOCK_REASON =
            "SinglePoolElement prepends BlockIgnoreProcessor.STRUCTURE_BLOCK";
    private static final String BENT_SOURCE_SHA256 = PAYLOAD_SHA256;
    private static final String BENT_RNG_KIND = "LegacyRandomSource";
    private static final String BENT_RNG_DRAW = "RandomSource.nextLong";
    private static final long BENT_PROBE_SEED = 0x54524c47314cL;
    private static final int BENT_COMMAND_COUNT = 45;
    private static final int BENT_RNG_DRAW_COUNT = 24;
    private static final String BENT_ENCOUNTER_RULE =
            "one draw after processed block survives clipping/processors, setBlock succeeds, and resulting block entity is RandomizableContainer; value is written to LootTableSeed before loadWithComponents";
    private static final String BENT_BOUNDARY =
            "canonical COMPOUND preimages are runtime saveWithFullMetadata outputs rebuilt from the accepted semantic command payload at template-local coordinates; x/y/z and caller LootTableSeed are the only placement substitutions, and no raw template NBT is emitted";
    private static final String COPPER_PROCESSORS =
            "minecraft:trial_chambers_copper_bulb_degradation";

    private static final List<String> POOL_KEYS = List.of(
            "minecraft:trial_chambers/atrium",
            "minecraft:trial_chambers/chamber/addon",
            "minecraft:trial_chambers/chamber/assembly",
            "minecraft:trial_chambers/chamber/end",
            "minecraft:trial_chambers/chamber/entrance_cap",
            "minecraft:trial_chambers/chamber/eruption",
            "minecraft:trial_chambers/chamber/pedestal",
            "minecraft:trial_chambers/chamber/slanted",
            "minecraft:trial_chambers/chambers/end",
            "minecraft:trial_chambers/chests/contents/supply",
            "minecraft:trial_chambers/chests/supply",
            "minecraft:trial_chambers/corridor",
            "minecraft:trial_chambers/corridor/slices",
            "minecraft:trial_chambers/corridors/addon/lower",
            "minecraft:trial_chambers/corridors/addon/middle",
            "minecraft:trial_chambers/corridors/addon/middle_upper",
            "minecraft:trial_chambers/decor",
            "minecraft:trial_chambers/decor/bed",
            "minecraft:trial_chambers/decor/chamber",
            "minecraft:trial_chambers/decor/disposal",
            "minecraft:trial_chambers/dispensers/chamber",
            "minecraft:trial_chambers/entrance",
            "minecraft:trial_chambers/hallway",
            "minecraft:trial_chambers/hallway/fallback",
            "minecraft:trial_chambers/reward/all",
            "minecraft:trial_chambers/reward/contents/default",
            "minecraft:trial_chambers/reward/ominous_vault",
            "minecraft:trial_chambers/spawner/all",
            "minecraft:trial_chambers/spawner/breeze",
            "minecraft:trial_chambers/spawner/contents/breeze",
            "minecraft:trial_chambers/spawner/melee",
            "minecraft:trial_chambers/spawner/melee/husk",
            "minecraft:trial_chambers/spawner/melee/spider",
            "minecraft:trial_chambers/spawner/melee/zombie",
            "minecraft:trial_chambers/spawner/ranged",
            "minecraft:trial_chambers/spawner/ranged/poison_skeleton",
            "minecraft:trial_chambers/spawner/ranged/skeleton",
            "minecraft:trial_chambers/spawner/ranged/stray",
            "minecraft:trial_chambers/spawner/slow_ranged",
            "minecraft:trial_chambers/spawner/slow_ranged/poison_skeleton",
            "minecraft:trial_chambers/spawner/slow_ranged/skeleton",
            "minecraft:trial_chambers/spawner/slow_ranged/stray",
            "minecraft:trial_chambers/spawner/small_melee",
            "minecraft:trial_chambers/spawner/small_melee/baby_zombie",
            "minecraft:trial_chambers/spawner/small_melee/cave_spider",
            "minecraft:trial_chambers/spawner/small_melee/silverfish",
            "minecraft:trial_chambers/spawner/small_melee/slime");
    private static final Set<String> ALIAS_POOL_KEYS = Set.of(
            "minecraft:trial_chambers/spawner/contents/ranged",
            "minecraft:trial_chambers/spawner/contents/slow_ranged",
            "minecraft:trial_chambers/spawner/contents/melee",
            "minecraft:trial_chambers/spawner/contents/small_melee");
    private static final List<String> TRIAL_SPAWNER_BASES = List.of(
            "minecraft:trial_chamber/breeze",
            "minecraft:trial_chamber/melee/husk",
            "minecraft:trial_chamber/melee/spider",
            "minecraft:trial_chamber/melee/zombie",
            "minecraft:trial_chamber/ranged/poison_skeleton",
            "minecraft:trial_chamber/ranged/skeleton",
            "minecraft:trial_chamber/ranged/stray",
            "minecraft:trial_chamber/slow_ranged/poison_skeleton",
            "minecraft:trial_chamber/slow_ranged/skeleton",
            "minecraft:trial_chamber/slow_ranged/stray",
            "minecraft:trial_chamber/small_melee/baby_zombie",
            "minecraft:trial_chamber/small_melee/cave_spider",
            "minecraft:trial_chamber/small_melee/silverfish",
            "minecraft:trial_chamber/small_melee/slime");
    private static final Set<String> TRIAL_SPAWNER_BASE_SET = Set.copyOf(TRIAL_SPAWNER_BASES);
    private static final Set<String> JIGSAW_FINAL_STATES = Set.of(
            "minecraft:air",
            "minecraft:chiseled_tuff_bricks",
            "minecraft:polished_tuff",
            "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=false,west=false]",
            "minecraft:tuff_bricks",
            "minecraft:waxed_copper_block",
            "minecraft:waxed_copper_bulb[lit=true,powered=false]",
            "minecraft:waxed_copper_grate[waterlogged=false]",
            "minecraft:waxed_oxidized_copper",
            "minecraft:waxed_oxidized_copper_grate[waterlogged=false]",
            "minecraft:waxed_oxidized_cut_copper");

    private static final List<SidecarIdentity> EXPECTED_BENT = List.of(
            new SidecarIdentity("DECORATED_POT|minecraft:decorated_pot", 1),
            new SidecarIdentity("FIXED_CONTAINER|minecraft:barrel", 1),
            new SidecarIdentity("FIXED_CONTAINER|minecraft:dispenser", 1),
            new SidecarIdentity("FIXED_CONTAINER|minecraft:hopper", 1),
            new SidecarIdentity("LOOT_CONTAINER|minecraft:barrel", 3),
            new SidecarIdentity("LOOT_CONTAINER|minecraft:chest", 12),
            new SidecarIdentity("LOOT_CONTAINER|minecraft:decorated_pot", 4),
            new SidecarIdentity("LOOT_CONTAINER|minecraft:dispenser", 6),
            new SidecarIdentity("TRIAL_SPAWNER|minecraft:trial_spawner", 14),
            new SidecarIdentity("VAULT|minecraft:vault", 2));
    private static final List<SidecarIdentity> EXPECTED_LOOT = List.of(
            new SidecarIdentity("minecraft:chests/trial_chambers/corridor|minecraft:barrel", 1),
            new SidecarIdentity("minecraft:chests/trial_chambers/entrance|minecraft:chest", 7),
            new SidecarIdentity("minecraft:chests/trial_chambers/intersection_barrel|minecraft:barrel", 2),
            new SidecarIdentity("minecraft:chests/trial_chambers/intersection|minecraft:chest", 1),
            new SidecarIdentity("minecraft:chests/trial_chambers/reward|minecraft:chest", 3),
            new SidecarIdentity("minecraft:chests/trial_chambers/supply|minecraft:chest", 1),
            new SidecarIdentity("minecraft:dispensers/trial_chambers/chamber|minecraft:dispenser", 3),
            new SidecarIdentity("minecraft:dispensers/trial_chambers/corridor|minecraft:dispenser", 3),
            new SidecarIdentity("minecraft:pots/trial_chambers/corridor|minecraft:decorated_pot", 4));
    private static final List<SidecarIdentity> EXPECTED_FIXED = List.of(
            new SidecarIdentity("minecraft:barrel|empty", 1),
            new SidecarIdentity("minecraft:dispenser|slot=4|minecraft:water_bucket|count=1", 1),
            new SidecarIdentity("minecraft:hopper|empty|cooldown=0", 1));
    private static final List<SidecarIdentity> EXPECTED_POTS = List.of(
            new SidecarIdentity("item|back=minecraft:brick,front=minecraft:flow_pottery_sherd,left=minecraft:brick,right=minecraft:brick|minecraft:string|count=3", 1),
            new SidecarIdentity("loot|back=minecraft:brick,front=minecraft:brick,left=minecraft:brick,right=minecraft:brick", 1),
            new SidecarIdentity("loot|back=minecraft:flow_pottery_sherd,front=minecraft:brick,left=minecraft:brick,right=minecraft:brick", 1),
            new SidecarIdentity("loot|back=minecraft:guster_pottery_sherd,front=minecraft:brick,left=minecraft:brick,right=minecraft:brick", 1),
            new SidecarIdentity("loot|back=minecraft:scrape_pottery_sherd,front=minecraft:brick,left=minecraft:brick,right=minecraft:brick", 1));
    private static final List<SidecarIdentity> EXPECTED_VAULTS = List.of(
            new SidecarIdentity("minecraft:ominous_trial_key|1|minecraft:chests/trial_chambers/reward_ominous", 1),
            new SidecarIdentity("minecraft:trial_key|1|minecraft:chests/trial_chambers/reward", 1));
    private static final List<SidecarIdentity> EXPECTED_SPAWNERS = TRIAL_SPAWNER_BASES.stream()
            .map(value -> new SidecarIdentity(value, 1)).toList();
    private static final TypedSidecars EXPECTED_SIDECARS = new TypedSidecars(
            EXPECTED_BENT, EXPECTED_LOOT, List.of(), EXPECTED_FIXED, EXPECTED_POTS,
            EXPECTED_VAULTS, EXPECTED_SPAWNERS, 1, 0);
    private static final List<ConnectorPriority> EXPECTED_PRIORITIES = List.of(
            new ConnectorPriority(0, 0, 784),
            new ConnectorPriority(0, 1, 10),
            new ConnectorPriority(0, 2, 2),
            new ConnectorPriority(0, 3, 1),
            new ConnectorPriority(1, 1, 34),
            new ConnectorPriority(2, 2, 3));
    private static final Aggregate EXPECTED_AGGREGATE = new Aggregate(
            POOL_COUNT, SINGLE_POOL_ELEMENT_COUNT, EMPTY_POOL_ELEMENT_COUNT,
            FEATURE_POOL_ELEMENT_COUNT, TEMPLATE_COUNT, BLOCK_COUNT, COMMAND_COUNT,
            CONNECTOR_COUNT, TEMPLATE_ENTITY_COUNT, STATE_COUNT, POOL_SEMANTIC_SHA256,
            TEMPLATE_ENCOUNTER_SHA256, CONNECTOR_SEMANTIC_SHA256, STATE_CLOSURE_SHA256);

    private Mc263TrialChambersGrammar() {
        throw new AssertionError("no instances");
    }

    static Corpus pinned() {
        return PinnedHolder.CORPUS;
    }

    private static final class PinnedHolder {
        private static final Corpus CORPUS = Mc263TrialChambersProductionAuthority.load();
    }

    /**
     * Assembles one authenticated corpus from the distilled production authority.
     *
     * <p>Every fact this reader already pins in Java (structure/structure-set codecs, registry pool
     * order, processor configuration and semantics, typed-sidecar receipt, configured-feature
     * boundary, connector-priority distribution and aggregate digests) is reconstructed here rather
     * than transported, and the assembled evidence then runs the same {@link #validateEvidence}
     * closure the JSON corpus runs, so a drifted projection fails closed.</p>
     */
    static Corpus assemble(List<ClassPin> classClosure, List<Pool> pools,
            List<AliasBinding> aliases, List<Template> templates, BentCorpus bent) {
        Evidence evidence = new Evidence(classClosure,
                new Structure(STRUCTURE_KEY,
                        "net.minecraft.world.level.levelgen.structure.structures.JigsawStructure",
                        START_POOL, 3, STRUCTURE_CODEC_SHA256),
                new StructureSet(STRUCTURE_KEY, 1, STRUCTURE_KEY, 1, STRUCTURE_SET_CODEC_SHA256),
                POOL_KEYS, pools, aliases, expectedProcessorLists(), expectedProcessors(),
                templates, EXPECTED_SIDECARS,
                new ConfiguredFeatureSemantics(0, List.of(), List.of(), FEATURE_BOUNDARY),
                EXPECTED_PRIORITIES, EXPECTED_AGGREGATE, BOUNDARY);
        validateEvidence(evidence);
        validateBentCorpus(bent);
        return attachBentEvidence(new Corpus(SCHEMA, RECEIPT_ID, JAVA_VERSION, SERVER_VERSION,
                evidence, List.of()), bent);
    }

    /** Decodes the authenticated verification corpora; test-only oracle path. */
    static Corpus decodeAcceptedEvidenceForTest(byte[] executionCorpus, byte[] bentEvidence) {
        return attachBentEvidence(decodeAuthenticated(executionCorpus),
                decodeBentAuthenticated(bentEvidence));
    }

    private static Corpus decodeAuthenticated(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers grammar bytes");
        require(bytes.length == PAYLOAD_BYTES, "Trial Chambers grammar payload byte-count drift");
        require(PAYLOAD_SHA256.equals(sha256(bytes)), "Trial Chambers grammar payload identity drift");
        return parseEvidence(bytes);
    }

    private static BentCorpus decodeBentAuthenticated(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers BENT evidence bytes");
        require(bytes.length == BENT_PAYLOAD_BYTES,
                "Trial Chambers BENT evidence payload byte-count drift");
        require(BENT_PAYLOAD_SHA256.equals(sha256(bytes)),
                "Trial Chambers BENT evidence payload identity drift");
        return parseBentEvidence(bytes);
    }

    private static BentCorpus parseBentEvidence(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers BENT evidence bytes");
        require(bytes.length <= BENT_PAYLOAD_BYTES + 1_024,
                "Trial Chambers BENT evidence parser bound exceeded");
        JsonReader in = new JsonReader(bytes);
        in.beginObject();
        in.field("schema", 0); int schema = in.readInt();
        in.field("receiptId", 1); String receiptId = in.readString();
        in.field("javaVersion", 2); int javaVersion = in.readInt();
        in.field("serverVersion", 3); String serverVersion = in.readString();
        in.field("evidence", 4);
        in.beginObject();
        in.field("sourceExecutionReceipt", 0); String sourceReceipt = in.readString();
        in.field("sourceExecutionPayloadSha256", 1); String sourceSha = in.readString();
        in.field("callerRng", 2);
        in.beginObject();
        in.field("kind", 0); String rngKind = in.readString();
        in.field("probeSeedI64", 1); long probeSeed = parseI64(in.readString(), "probe seed");
        in.field("draw", 2); String draw = in.readString();
        in.field("drawCount", 3); int drawCount = in.readInt();
        in.field("encounterRule", 4); String encounterRule = in.readString();
        in.endObject();
        in.field("rowsInExecutionCorpusOrder", 3);
        in.beginArray();
        ArrayList<BentRow> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < BENT_COMMAND_COUNT + 1, "too many Trial Chambers BENT rows");
            in.beginObject();
            in.field("template", 0); String template = in.readString();
            in.field("commandOrdinal", 1); int commandOrdinal = in.readInt();
            in.field("blockEntityType", 2); BlockEntityType type = BlockEntityType.from(in.readString());
            OptionalInt rngOrdinal = OptionalInt.empty();
            OptionalLong probeNextLong = OptionalLong.empty();
            if (isRandomizable(type)) {
                in.field("callerRngEncounterOrdinal", 3); rngOrdinal = OptionalInt.of(in.readInt());
                in.field("probeNextLongI64", 4);
                probeNextLong = OptionalLong.of(parseI64(in.readString(), "probe nextLong"));
                in.field("canonicalNbtLength", 5);
            } else {
                in.field("canonicalNbtLength", 3);
            }
            int canonicalLength = in.readInt();
            in.field("canonicalNbtSha256", isRandomizable(type) ? 6 : 4);
            String canonicalSha = in.readString();
            in.endObject();
            rows.add(new BentRow(template, commandOrdinal, type, rngOrdinal, probeNextLong,
                    canonicalLength, canonicalSha));
        }
        in.endArray();
        in.field("boundary", 4); String boundary = in.readString();
        in.endObject();
        in.endObject();
        in.finish();
        BentCorpus result = new BentCorpus(schema, receiptId, javaVersion, serverVersion,
                sourceReceipt, sourceSha,
                new BentCallerRng(rngKind, probeSeed, draw, drawCount, encounterRule),
                rows, boundary);
        validateBentCorpus(result);
        return result;
    }

    static Corpus attachBentEvidence(Corpus corpus, BentCorpus bent) {
        ArrayList<CanonicalBentEvidence> attached = new ArrayList<>(BENT_COMMAND_COUNT);
        int rowIndex = 0;
        int rngIndex = 0;
        for (Template template : corpus.evidence().templatesInEncounterOrder()) {
            for (Command command : template.commands()) {
                if (!isBentCommand(command)) continue;
                require(rowIndex < bent.rowsInExecutionCorpusOrder().size(),
                        "Trial Chambers BENT evidence ended before command closure");
                BentRow row = bent.rowsInExecutionCorpusOrder().get(rowIndex++);
                require(row.template().equals(template.key()) && row.commandOrdinal() == command.ordinal(),
                        "Trial Chambers BENT command/order binding drift");
                BlockEntityType type = blockEntityType(command);
                require(row.blockEntityType() == type,
                        "Trial Chambers BENT block-entity type binding drift");
                if (isRandomizable(type)) {
                    require(row.callerRngEncounterOrdinal().isPresent()
                                    && row.callerRngEncounterOrdinal().getAsInt() == rngIndex++,
                            "Trial Chambers BENT caller RNG encounter-order drift");
                    require(row.probeNextLongI64().isPresent(),
                            "Trial Chambers BENT caller RNG preimage absent");
                } else {
                    require(row.callerRngEncounterOrdinal().isEmpty()
                                    && row.probeNextLongI64().isEmpty(),
                            "Trial Chambers non-randomizable BENT claimed caller RNG draw");
                }
                byte[] preimage = canonicalBentNbt(command,
                        row.probeNextLongI64().orElse(0L));
                require(preimage.length == row.canonicalNbtLength(),
                        "Trial Chambers canonical BENT NBT length/preimage drift");
                require(row.canonicalNbtSha256().equals(sha256(preimage)),
                        "Trial Chambers canonical BENT NBT digest/preimage drift " + template.key()
                                + "#" + command.ordinal() + " expected=" + row.canonicalNbtSha256()
                                + " actual=" + sha256(preimage));
                attached.add(new CanonicalBentEvidence(
                        template.key(), command.ordinal(), type,
                        row.callerRngEncounterOrdinal(), row.probeNextLongI64(), preimage));
            }
        }
        require(rowIndex == BENT_COMMAND_COUNT && rowIndex == bent.rowsInExecutionCorpusOrder().size(),
                "Trial Chambers canonical BENT command cardinality drift");
        require(rngIndex == BENT_RNG_DRAW_COUNT,
                "Trial Chambers canonical BENT RNG cardinality drift");
        return new Corpus(corpus.schema(), corpus.receiptId(), corpus.javaVersion(),
                corpus.serverVersion(), corpus.evidence(), attached);
    }

    static void validateBentCorpus(BentCorpus bent) {
        require(bent.schema() == BENT_SCHEMA, "Trial Chambers BENT schema drift");
        require(BENT_RECEIPT_ID.equals(bent.receiptId()), "Trial Chambers BENT receipt drift");
        require(bent.javaVersion() == JAVA_VERSION, "Trial Chambers BENT Java version drift");
        require(SERVER_VERSION.equals(bent.serverVersion()), "Trial Chambers BENT server version drift");
        require(RECEIPT_ID.equals(bent.sourceExecutionReceipt())
                        && BENT_SOURCE_SHA256.equals(bent.sourceExecutionPayloadSha256()),
                "Trial Chambers BENT source execution identity drift");
        require(bent.callerRng().equals(new BentCallerRng(
                        BENT_RNG_KIND, BENT_PROBE_SEED, BENT_RNG_DRAW,
                        BENT_RNG_DRAW_COUNT, BENT_ENCOUNTER_RULE)),
                "Trial Chambers BENT caller RNG contract drift");
        require(bent.rowsInExecutionCorpusOrder().size() == BENT_COMMAND_COUNT,
                "Trial Chambers BENT row cardinality drift");
        for (BentRow row : bent.rowsInExecutionCorpusOrder()) {
            require(row.template().startsWith("minecraft:trial_chambers/")
                            && row.commandOrdinal() >= 0
                            && row.canonicalNbtLength() > 0 && row.canonicalNbtLength() <= 4_096
                            && isSha256(row.canonicalNbtSha256()),
                    "Trial Chambers BENT row shape drift");
        }
        require(BENT_BOUNDARY.equals(bent.boundary()), "Trial Chambers BENT boundary drift");
    }

    private static boolean isBentCommand(Command command) {
        return command instanceof LootContainer || command instanceof FixedContainer
                || command instanceof DecoratedPot || command instanceof Vault
                || command instanceof TrialSpawner;
    }

    private static BlockEntityType blockEntityType(Command command) {
        if (command instanceof LootContainer value) return value.blockEntityType();
        if (command instanceof FixedContainer value) return value.blockEntityType();
        if (command instanceof DecoratedPot value) return value.blockEntityType();
        if (command instanceof Vault value) return value.blockEntityType();
        if (command instanceof TrialSpawner value) return value.blockEntityType();
        throw invalid("command has no Trial Chambers BENT identity");
    }

    private static boolean isRandomizable(BlockEntityType type) {
        return type == BlockEntityType.BARREL || type == BlockEntityType.CHEST
                || type == BlockEntityType.DISPENSER || type == BlockEntityType.HOPPER;
    }

    private static long parseI64(String value, String label) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw invalid("invalid Trial Chambers " + label); }
    }

    /**
     * Canonical BENT facts for one accepted command, at its template-local position.
     *
     * <p>The rendering itself belongs to {@link Mc263StructureBlockEntityNbtAuthority}: this method
     * only translates the accepted Trial Chambers command vocabulary into that family-agnostic
     * fact, so Trial Chambers keeps no block-entity writer of its own.</p>
     */
    static Mc263StructureBlockEntityNbtAuthority.Facts canonicalBentFacts(
            Command command, long lootSeed) {
        BlockEntityType type = blockEntityType(command);
        if (command instanceof LootContainer value) {
            if (type == BlockEntityType.DECORATED_POT) {
                return new Mc263StructureBlockEntityNbtAuthority.LootDecoratedPot(
                        type.id(), value.lootTable().id(), sherds(value.sherds().orElseThrow()));
            }
            return new Mc263StructureBlockEntityNbtAuthority.LootContainer(
                    type.id(), value.lootTable().id(), lootSeed);
        }
        if (command instanceof FixedContainer value) {
            List<Mc263StructureBlockEntityNbtAuthority.ItemStack> items =
                    new ArrayList<>(value.items().size());
            for (SlotItem item : value.items()) {
                items.add(new Mc263StructureBlockEntityNbtAuthority.ItemStack(
                        item.slot(), item.item().id(), item.count()));
            }
            if (type == BlockEntityType.HOPPER) {
                return new Mc263StructureBlockEntityNbtAuthority.Hopper(
                        type.id(), items, value.transferCooldown().orElseThrow());
            }
            return new Mc263StructureBlockEntityNbtAuthority.ItemContainer(type.id(), items);
        }
        if (command instanceof DecoratedPot value) {
            return new Mc263StructureBlockEntityNbtAuthority.DecoratedPot(
                    type.id(), item(value.item()), sherds(value.sherds()));
        }
        if (command instanceof Vault value) {
            return new Mc263StructureBlockEntityNbtAuthority.Vault(
                    type.id(), item(value.keyItem()), value.lootTable().id());
        }
        if (command instanceof TrialSpawner value) {
            return new Mc263StructureBlockEntityNbtAuthority.TrialSpawner(
                    type.id(), value.config().normalConfig(), value.config().ominousConfig());
        }
        throw invalid("unsupported Trial Chambers canonical BENT command");
    }

    private static Mc263StructureBlockEntityNbtAuthority.Item item(ItemStack value) {
        return new Mc263StructureBlockEntityNbtAuthority.Item(value.item().id(), value.count());
    }

    private static Mc263StructureBlockEntityNbtAuthority.PotSherds sherds(PotSherds value) {
        return new Mc263StructureBlockEntityNbtAuthority.PotSherds(
                value.left().id(), value.back().id(), value.right().id(), value.front().id());
    }

    private static byte[] canonicalBentNbt(Command command, long lootSeed) {
        Vec3i position = bentPosition(command);
        return Mc263StructureBlockEntityNbtAuthority.render(position.x(), position.y(),
                position.z(), canonicalBentFacts(command, lootSeed));
    }

    private static Vec3i bentPosition(Command command) {
        if (command instanceof LootContainer value) return value.position();
        if (command instanceof FixedContainer value) return value.position();
        if (command instanceof DecoratedPot value) return value.position();
        if (command instanceof Vault value) return value.position();
        if (command instanceof TrialSpawner value) return value.position();
        throw invalid("unsupported Trial Chambers canonical BENT command");
    }

    private static Corpus parseEvidence(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers grammar bytes");
        require(bytes.length <= PAYLOAD_BYTES + 4_096, "Trial Chambers grammar parser bound exceeded");
        JsonReader in = new JsonReader(bytes);
        in.beginObject();
        in.field("schema", 0); int schema = in.readInt();
        in.field("receiptId", 1); String receiptId = in.readString();
        in.field("javaVersion", 2); int javaVersion = in.readInt();
        in.field("serverVersion", 3); String serverVersion = in.readString();
        in.field("evidence", 4); Evidence evidence = readEvidence(in);
        in.endObject();
        in.finish();
        require(schema == SCHEMA, "Trial Chambers schema drift");
        require(RECEIPT_ID.equals(receiptId), "Trial Chambers receipt drift");
        require(javaVersion == JAVA_VERSION, "Trial Chambers Java version drift");
        require(SERVER_VERSION.equals(serverVersion), "Trial Chambers server version drift");
        validateEvidence(evidence);
        return new Corpus(schema, receiptId, javaVersion, serverVersion, evidence, List.of());
    }

    private static Evidence readEvidence(JsonReader in) {
        in.beginObject();
        in.field("classClosure", 0); List<ClassPin> classes = readClassPins(in);
        in.field("structure", 1); Structure structure = readStructure(in);
        in.field("structureSet", 2); StructureSet structureSet = readStructureSet(in);
        in.field("registryPoolKeysInEncounterOrder", 3);
        List<String> registryPools = readStrings(in, POOL_COUNT + 1, "registry pool keys");
        in.field("poolsInEncounterOrder", 4); List<Pool> pools = readPools(in);
        in.field("aliasesInDeclaredOrder", 5); List<AliasBinding> aliases = readAliases(in);
        in.field("processorListsInEncounterOrder", 6);
        List<ProcessorList> processorLists = readProcessorLists(in);
        in.field("processorsInEncounterOrder", 7); List<Processor> processors = readProcessors(in);
        in.field("templatesInEncounterOrder", 8); List<Template> templates = readTemplates(in);
        in.field("typedSidecars", 9); TypedSidecars sidecars = readTypedSidecars(in);
        in.field("configuredFeatureSemantics", 10);
        ConfiguredFeatureSemantics features = readConfiguredFeatures(in);
        in.field("connectorPriorityDistribution", 11);
        List<ConnectorPriority> priorities = readConnectorPriorities(in);
        in.field("aggregate", 12); Aggregate aggregate = readAggregate(in);
        in.field("boundary", 13); String boundary = in.readString();
        in.endObject();
        return new Evidence(classes, structure, structureSet, registryPools, pools, aliases,
                processorLists, processors, templates, sidecars, features, priorities, aggregate,
                boundary);
    }

    private static List<ClassPin> readClassPins(JsonReader in) {
        in.beginArray();
        ArrayList<ClassPin> values = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 22, "too many Trial Chambers class pins");
            in.beginObject();
            in.field("class", 0); String className = in.readString();
            in.field("sha256", 1); String digest = in.readString();
            in.endObject();
            values.add(new ClassPin(className, digest));
        }
        in.endArray();
        return List.copyOf(values);
    }

    private static Structure readStructure(JsonReader in) {
        in.beginObject();
        in.field("registryKey", 0); String registryKey = in.readString();
        in.field("runtimeClass", 1); String runtimeClass = in.readString();
        in.field("startPool", 2); String startPool = in.readString();
        in.field("aliasBindingCount", 3); int aliases = in.readInt();
        in.field("codecSha256", 4); String codec = in.readString();
        in.endObject();
        return new Structure(registryKey, runtimeClass, startPool, aliases, codec);
    }

    private static StructureSet readStructureSet(JsonReader in) {
        in.beginObject();
        in.field("registryKey", 0); String registryKey = in.readString();
        in.field("entryCount", 1); int entryCount = in.readInt();
        in.field("structure", 2); String structure = in.readString();
        in.field("weight", 3); int weight = in.readInt();
        in.field("codecSha256", 4); String codec = in.readString();
        in.endObject();
        return new StructureSet(registryKey, entryCount, structure, weight, codec);
    }

    private static List<Pool> readPools(JsonReader in) {
        in.beginArray();
        ArrayList<Pool> pools = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < POOL_COUNT + 1, "too many Trial Chambers pools");
            in.beginObject();
            in.field("key", 0); String key = in.readString();
            in.field("fallback", 1); String fallback = in.readString();
            in.field("rawElementCount", 2); int rawCount = in.readInt();
            in.field("expandedWeight", 3); int expandedWeight = in.readInt();
            in.field("elementsInDeclaredOrder", 4); List<PoolElement> elements = readPoolElements(in);
            in.endObject();
            pools.add(new Pool(key, fallback, rawCount, expandedWeight, elements));
        }
        in.endArray();
        return List.copyOf(pools);
    }

    private static List<PoolElement> readPoolElements(JsonReader in) {
        in.beginArray();
        ArrayList<PoolElement> elements = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 64, "too many Trial Chambers pool elements");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("weight", 1); int weight = in.readInt();
            in.field("projection", 2); Projection projection = Projection.from(in.readString());
            in.field("kind", 3); String kind = in.readString();
            PoolElement element;
            if ("single".equals(kind)) {
                in.field("template", 4); String template = in.readString();
                in.field("processorList", 5);
                ProcessorListIdentity processorList = ProcessorListIdentity.from(in.readString());
                in.field("placementProcessorsInOrder", 6);
                List<ProcessorType> placement = readProcessorTypes(in, 8, "placement processors");
                element = new SingleElement(ordinal, weight, projection, template, processorList,
                        placement);
            } else if ("empty".equals(kind)) {
                element = new EmptyElement(ordinal, weight, projection);
            } else {
                throw invalid("unknown Trial Chambers pool element kind: " + kind);
            }
            in.endObject();
            elements.add(element);
        }
        in.endArray();
        return List.copyOf(elements);
    }

    private static List<AliasBinding> readAliases(JsonReader in) {
        in.beginArray();
        ArrayList<AliasBinding> aliases = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 4, "too many Trial Chambers aliases");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("kind", 1); AliasKind kind = AliasKind.from(in.readString());
            in.field("runtimeClass", 2); String runtimeClass = in.readString();
            in.field("allTargetsInDeclaredOrder", 3);
            List<String> allTargets = readStrings(in, 8, "alias targets");
            in.field("orderedCodec", 4);
            AliasBinding alias = switch (kind) {
                case RANDOM_GROUP -> readRandomGroupAliasCodec(
                        in, ordinal, runtimeClass, allTargets);
                case RANDOM -> readRandomAliasCodec(in, ordinal, runtimeClass, allTargets);
            };
            in.endObject();
            aliases.add(alias);
        }
        in.endArray();
        return List.copyOf(aliases);
    }

    private static RandomGroupAlias readRandomGroupAliasCodec(
            JsonReader in, int ordinal, String runtimeClass, List<String> allTargets) {
        in.beginObject();
        in.field("groups", 0);
        in.beginArray();
        ArrayList<AliasGroup> groups = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 4, "too many Trial Chambers alias groups");
            in.beginObject();
            in.field("data", 0);
            in.beginArray();
            ArrayList<DirectAlias> data = new ArrayList<>();
            for (int child = 0; in.nextArrayValue(child); child++) {
                require(child < 3, "too many Trial Chambers direct aliases");
                in.beginObject();
                in.field("alias", 0); String alias = in.readString();
                in.field("target", 1); String target = in.readString();
                in.field("type", 2);
                require("minecraft:direct".equals(in.readString()),
                        "unknown Trial Chambers direct-alias kind");
                in.endObject();
                data.add(new DirectAlias(alias, target));
            }
            in.endArray();
            in.field("weight", 1); int weight = in.readInt();
            in.endObject();
            groups.add(new AliasGroup(data, weight));
        }
        in.endArray();
        in.field("type", 1);
        require("minecraft:random_group".equals(in.readString()),
                "Trial Chambers random-group codec kind drift");
        in.endObject();
        return new RandomGroupAlias(ordinal, runtimeClass, allTargets, groups);
    }

    private static RandomAlias readRandomAliasCodec(
            JsonReader in, int ordinal, String runtimeClass, List<String> allTargets) {
        in.beginObject();
        in.field("alias", 0); String alias = in.readString();
        in.field("targets", 1);
        in.beginArray();
        ArrayList<WeightedTarget> targets = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Trial Chambers random-alias targets");
            in.beginObject();
            in.field("data", 0); String data = in.readString();
            in.field("weight", 1); int weight = in.readInt();
            in.endObject();
            targets.add(new WeightedTarget(data, weight));
        }
        in.endArray();
        in.field("type", 2);
        require("minecraft:random".equals(in.readString()),
                "Trial Chambers random alias codec kind drift");
        in.endObject();
        return new RandomAlias(ordinal, runtimeClass, allTargets, alias, targets);
    }

    private static List<ProcessorList> readProcessorLists(JsonReader in) {
        in.beginArray();
        ArrayList<ProcessorList> lists = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 3, "too many Trial Chambers processor lists");
            in.beginObject();
            in.field("identity", 0); ProcessorListIdentity identity =
                    ProcessorListIdentity.from(in.readString());
            in.field("registered", 1); boolean registered = in.readBoolean();
            in.field("processorsInOrder", 2);
            List<ProcessorType> processors = readProcessorTypes(in, 4, "processor-list order");
            in.field("orderedCodec", 3);
            in.beginObject();
            in.field("processors", 0);
            in.beginArray();
            ArrayList<ProcessorConfig> configs = new ArrayList<>();
            for (int configIndex = 0; in.nextArrayValue(configIndex); configIndex++) {
                require(configIndex < 3, "too many Trial Chambers processor-list codecs");
                if (identity == ProcessorListIdentity.INLINE) {
                    throw invalid("Trial Chambers inline processor list is not empty");
                }
                configs.add(configIndex == 0 ? readRuleConfig(in) : readProtectedBlocksConfig(in));
            }
            in.endArray();
            in.endObject();
            in.endObject();
            lists.add(new ProcessorList(identity, registered, processors, configs));
        }
        in.endArray();
        return List.copyOf(lists);
    }

    private static List<Processor> readProcessors(JsonReader in) {
        in.beginArray();
        ArrayList<Processor> processors = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 5, "too many Trial Chambers processors");
            in.beginObject();
            in.field("type", 0); ProcessorType type = ProcessorType.from(in.readString());
            in.field("runtimeClass", 1); String runtimeClass = in.readString();
            in.field("orderedCodec", 2); ProcessorConfig codec = readProcessorConfig(in, type);
            in.field("semantics", 3); ProcessorSemantics semantics = readProcessorSemantics(in, type);
            in.endObject();
            processors.add(new Processor(type, runtimeClass, codec, semantics));
        }
        in.endArray();
        return List.copyOf(processors);
    }

    private static ProcessorConfig readProcessorConfig(JsonReader in, ProcessorType type) {
        return switch (type) {
            case BLOCK_IGNORE -> {
                in.beginObject();
                in.field("blocks", 0); List<String> blocks = readStrings(in, 4, "ignored blocks");
                in.field("processor_type", 1);
                require(type.id().equals(in.readString()), "Trial Chambers block-ignore codec type drift");
                in.endObject();
                yield new BlockIgnoreConfig(blocks);
            }
            case JIGSAW_REPLACEMENT -> {
                in.beginObject();
                in.field("processor_type", 0);
                require(type.id().equals(in.readString()), "Trial Chambers jigsaw codec type drift");
                in.endObject();
                yield new JigsawReplacementConfig();
            }
            case RULE -> readRuleConfig(in);
            case PROTECTED_BLOCKS -> readProtectedBlocksConfig(in);
        };
    }

    private static RuleProcessorConfig readRuleConfig(JsonReader in) {
        in.beginObject();
        in.field("rules", 0);
        in.beginArray();
        ArrayList<CopperRule> rules = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 4, "too many Trial Chambers copper rules");
            rules.add(readCopperRule(in));
        }
        in.endArray();
        in.field("processor_type", 1);
        require(ProcessorType.RULE.id().equals(in.readString()),
                "Trial Chambers rule codec type drift");
        in.endObject();
        return new RuleProcessorConfig(rules);
    }

    private static CopperRule readCopperRule(JsonReader in) {
        in.beginObject();
        in.field("input_predicate", 0);
        in.beginObject();
        in.field("block", 0); String inputBlock = in.readString();
        in.field("probability", 1); double probability = in.readDouble();
        in.field("predicate_type", 2); String inputPredicate = in.readString();
        in.endObject();
        in.field("location_predicate", 1);
        in.beginObject();
        in.field("predicate_type", 0); String locationPredicate = in.readString();
        in.endObject();
        in.field("output_state", 2);
        in.beginObject();
        in.field("properties", 0);
        in.beginObject();
        in.field("powered", 0); String powered = in.readString();
        in.field("lit", 1); String lit = in.readString();
        in.endObject();
        in.field("id", 1); String output = in.readString();
        in.endObject();
        in.endObject();
        return new CopperRule(inputBlock, probability, inputPredicate, locationPredicate,
                output, powered, lit);
    }

    private static ProtectedBlocksConfig readProtectedBlocksConfig(JsonReader in) {
        in.beginObject();
        in.field("value", 0); String value = in.readString();
        in.field("processor_type", 1);
        require(ProcessorType.PROTECTED_BLOCKS.id().equals(in.readString()),
                "Trial Chambers protected-block codec type drift");
        in.endObject();
        return new ProtectedBlocksConfig(value);
    }

    private static ProcessorSemantics readProcessorSemantics(JsonReader in, ProcessorType type) {
        return switch (type) {
            case BLOCK_IGNORE -> {
                in.beginObject();
                in.field("kind", 0); String kind = in.readString();
                in.field("state", 1); String state = in.readString();
                in.endObject();
                yield new BlockIgnoreSemantics(kind, state);
            }
            case JIGSAW_REPLACEMENT -> {
                in.beginObject();
                in.field("kind", 0); String kind = in.readString();
                in.field("source", 1); String source = in.readString();
                in.field("structureVoid", 2); String structureVoid = in.readString();
                in.field("otherwise", 3); String otherwise = in.readString();
                in.endObject();
                yield new JigsawReplacementSemantics(kind, source, structureVoid, otherwise);
            }
            case RULE -> readRuleSemantics(in);
            case PROTECTED_BLOCKS -> {
                in.beginObject();
                in.field("kind", 0); String kind = in.readString();
                in.field("predicate", 1); String predicate = in.readString();
                in.field("onMatch", 2); String onMatch = in.readString();
                in.field("onMiss", 3); String onMiss = in.readString();
                in.endObject();
                yield new ProtectedBlocksSemantics(kind, predicate, onMatch, onMiss);
            }
        };
    }

    private static RuleSemantics readRuleSemantics(JsonReader in) {
        in.beginObject();
        in.field("kind", 0); String kind = in.readString();
        in.field("rulesInOrder", 1);
        in.beginArray();
        ArrayList<RuleSemanticRow> rules = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 4, "too many Trial Chambers semantic copper rules");
            in.beginObject();
            in.field("inputBlock", 0); String input = in.readString();
            in.field("probability", 1); double probability = in.readDouble();
            in.field("outputState", 2); String output = in.readString();
            in.endObject();
            rules.add(new RuleSemanticRow(input, probability, output));
        }
        in.endArray();
        in.field("inputPredicateRuntimeClass", 2); String inputClass = in.readString();
        in.field("locationPredicateRuntimeClass", 3); String locationClass = in.readString();
        in.field("positionPredicateRuntimeClass", 4); String positionClass = in.readString();
        in.field("blockEntityModifier", 5); String modifier = in.readString();
        in.field("positionalRng", 6);
        in.beginObject();
        in.field("position", 0); String position = in.readString();
        in.field("xMultiply", 1); String xMultiply = in.readString();
        in.field("seedFormula", 2); String formula = in.readString();
        in.field("randomSource", 3); String randomSource = in.readString();
        in.field("lifetime", 4); String lifetime = in.readString();
        in.field("draw", 5); String draw = in.readString();
        in.field("comparison", 6); String comparison = in.readString();
        in.field("shortCircuit", 7); String shortCircuit = in.readString();
        in.endObject();
        in.endObject();
        return new RuleSemantics(kind, rules, inputClass, locationClass, positionClass, modifier,
                new PositionalRng(position, xMultiply, formula, randomSource, lifetime, draw,
                        comparison, shortCircuit));
    }

    private static List<Template> readTemplates(JsonReader in) {
        in.beginArray();
        ArrayList<Template> templates = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < TEMPLATE_COUNT + 1, "too many Trial Chambers templates");
            in.beginObject();
            in.field("template", 0); String key = in.readString();
            in.field("size", 1); Vec3i size = readVec3i(in);
            in.field("stateTable", 2); List<String> states = readStrings(in, 64, "state table");
            in.field("blockCount", 3); int blockCount = in.readInt();
            in.field("commandCount", 4); int commandCount = in.readInt();
            in.field("commands", 5); List<Command> commands = readCommands(in, commandCount);
            in.field("connectorCount", 6); int connectorCount = in.readInt();
            in.field("connectorsInTemplateOrder", 7);
            List<Connector> connectors = readConnectors(in, connectorCount);
            in.field("entityCount", 8); int entityCount = in.readInt();
            in.field("entitySidecarsInTemplateOrder", 9);
            List<EntitySidecar> entities = readEntitySidecars(in);
            in.endObject();
            templates.add(new Template(key, size, states, blockCount, commandCount, commands,
                    connectorCount, connectors, entityCount, entities));
        }
        in.endArray();
        return List.copyOf(templates);
    }

    private static List<Command> readCommands(JsonReader in, int declaredCount) {
        require(declaredCount >= 0 && declaredCount <= COMMAND_COUNT,
                "Trial Chambers command count outside bound");
        in.beginArray();
        ArrayList<Command> commands = new ArrayList<>(Math.min(declaredCount, 4_096));
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < COMMAND_COUNT + 1, "too many Trial Chambers commands");
            in.beginObject();
            in.field("op", 0); CommandOp op = CommandOp.from(in.readString());
            in.field("ordinal", 1); int ordinal = in.readInt();
            Command command = switch (op) {
                case RUN -> readRun(in, ordinal);
                case JIGSAW -> readJigsaw(in, ordinal);
                case IGNORED_STRUCTURE_BLOCK -> readIgnoredStructureBlock(in, ordinal);
                case LOOT_CONTAINER -> readLootContainer(in, ordinal);
                case FIXED_CONTAINER -> readFixedContainer(in, ordinal);
                case DECORATED_POT -> readDecoratedPot(in, ordinal);
                case VAULT -> readVault(in, ordinal);
                case TRIAL_SPAWNER -> readTrialSpawner(in, ordinal);
            };
            in.endObject();
            commands.add(command);
        }
        in.endArray();
        require(commands.size() == declaredCount, "Trial Chambers declared command count drift");
        return List.copyOf(commands);
    }

    private static Run readRun(JsonReader in, int ordinal) {
        in.field("start", 2); Vec3i start = readVec3i(in);
        in.field("delta", 3); Vec3i delta = readVec3i(in);
        in.field("count", 4); int count = in.readInt();
        in.field("state", 5); int state = in.readInt();
        return new Run(ordinal, start, delta, count, state);
    }

    private static Jigsaw readJigsaw(JsonReader in, int ordinal) {
        in.field("state", 2); int state = in.readInt();
        in.field("connectorOrdinal", 3); int connector = in.readInt();
        in.field("finalState", 4); String finalState = in.readString();
        return new Jigsaw(ordinal, state, connector, finalState);
    }

    private static IgnoredStructureBlock readIgnoredStructureBlock(JsonReader in, int ordinal) {
        in.field("position", 2); Vec3i position = readVec3i(in);
        in.field("state", 3); int state = in.readInt();
        in.field("reason", 4); String reason = in.readString();
        return new IgnoredStructureBlock(ordinal, position, state, reason);
    }

    private static LootContainer readLootContainer(JsonReader in, int ordinal) {
        in.field("position", 2); Vec3i position = readVec3i(in);
        in.field("state", 3); int state = in.readInt();
        in.field("blockEntityType", 4); BlockEntityType blockEntity =
                BlockEntityType.from(in.readString());
        in.field("lootTable", 5); LootTable lootTable = LootTable.from(in.readString());
        in.field("placementLootSeedRng", 6); PlacementLootSeedRng rng = readPlacementLootSeedRng(in);
        Optional<PotSherds> sherds = Optional.empty();
        if (blockEntity == BlockEntityType.DECORATED_POT) {
            in.field("sherds", 7); sherds = Optional.of(readPotSherds(in));
        }
        return new LootContainer(ordinal, position, state, blockEntity, lootTable, rng, sherds);
    }

    private static FixedContainer readFixedContainer(JsonReader in, int ordinal) {
        in.field("position", 2); Vec3i position = readVec3i(in);
        in.field("state", 3); int state = in.readInt();
        in.field("blockEntityType", 4); BlockEntityType blockEntity =
                BlockEntityType.from(in.readString());
        in.field("items", 5); List<SlotItem> items = readSlotItems(in);
        in.field("placementLootSeedRng", 6); PlacementLootSeedRng rng = readPlacementLootSeedRng(in);
        OptionalInt cooldown = OptionalInt.empty();
        if (blockEntity == BlockEntityType.HOPPER) {
            in.field("transferCooldown", 7); cooldown = OptionalInt.of(in.readInt());
        }
        return new FixedContainer(ordinal, position, state, blockEntity, items, rng, cooldown);
    }

    private static DecoratedPot readDecoratedPot(JsonReader in, int ordinal) {
        in.field("position", 2); Vec3i position = readVec3i(in);
        in.field("state", 3); int state = in.readInt();
        in.field("blockEntityType", 4); BlockEntityType blockEntity =
                BlockEntityType.from(in.readString());
        in.field("item", 5); ItemStack item = readItemStack(in);
        in.field("sherds", 6); PotSherds sherds = readPotSherds(in);
        return new DecoratedPot(ordinal, position, state, blockEntity, item, sherds);
    }

    private static Vault readVault(JsonReader in, int ordinal) {
        in.field("position", 2); Vec3i position = readVec3i(in);
        in.field("state", 3); int state = in.readInt();
        in.field("blockEntityType", 4); BlockEntityType blockEntity =
                BlockEntityType.from(in.readString());
        in.field("keyItem", 5); ItemStack key = readItemStack(in);
        in.field("lootTable", 6); LootTable loot = LootTable.from(in.readString());
        return new Vault(ordinal, position, state, blockEntity, key, loot);
    }

    private static TrialSpawner readTrialSpawner(JsonReader in, int ordinal) {
        in.field("position", 2); Vec3i position = readVec3i(in);
        in.field("state", 3); int state = in.readInt();
        in.field("blockEntityType", 4); BlockEntityType blockEntity =
                BlockEntityType.from(in.readString());
        in.field("normalConfig", 5); String normal = in.readString();
        in.field("ominousConfig", 6); String ominous = in.readString();
        return new TrialSpawner(ordinal, position, state, blockEntity,
                TrialSpawnerConfig.fromPair(normal, ominous));
    }

    private static PlacementLootSeedRng readPlacementLootSeedRng(JsonReader in) {
        in.beginObject();
        in.field("kind", 0); String kind = in.readString();
        PlacementLootSeedRng rng;
        if ("caller_next_long".equals(kind)) {
            in.field("runtimeGuard", 1); String guard = in.readString();
            in.field("when", 2); String when = in.readString();
            in.field("targetNbtField", 3); String target = in.readString();
            in.field("draw", 4); String draw = in.readString();
            rng = new CallerNextLongRng(guard, when, target, draw);
        } else if ("none".equals(kind)) {
            rng = NoPlacementLootSeedRng.INSTANCE;
        } else {
            throw invalid("unknown Trial Chambers placement loot-seed RNG: " + kind);
        }
        in.endObject();
        return rng;
    }

    private static List<SlotItem> readSlotItems(JsonReader in) {
        in.beginArray();
        ArrayList<SlotItem> items = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 2, "too many Trial Chambers fixed-container items");
            in.beginObject();
            in.field("slot", 0); int slot = in.readInt();
            in.field("item", 1); ItemIdentity item = ItemIdentity.from(in.readString());
            in.field("count", 2); int count = in.readInt();
            in.endObject();
            items.add(new SlotItem(slot, item, count));
        }
        in.endArray();
        return List.copyOf(items);
    }

    private static ItemStack readItemStack(JsonReader in) {
        in.beginObject();
        in.field("item", 0); ItemIdentity item = ItemIdentity.from(in.readString());
        in.field("count", 1); int count = in.readInt();
        in.endObject();
        return new ItemStack(item, count);
    }

    private static PotSherds readPotSherds(JsonReader in) {
        in.beginObject();
        in.field("back", 0); PotSherd back = PotSherd.from(in.readString());
        in.field("front", 1); PotSherd front = PotSherd.from(in.readString());
        in.field("left", 2); PotSherd left = PotSherd.from(in.readString());
        in.field("right", 3); PotSherd right = PotSherd.from(in.readString());
        in.endObject();
        return new PotSherds(back, front, left, right);
    }

    private static List<Connector> readConnectors(JsonReader in, int declaredCount) {
        require(declaredCount >= 0 && declaredCount <= CONNECTOR_COUNT,
                "Trial Chambers connector count outside bound");
        in.beginArray();
        ArrayList<Connector> connectors = new ArrayList<>(declaredCount);
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < CONNECTOR_COUNT + 1, "too many Trial Chambers connectors");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("position", 1); Vec3i position = readVec3i(in);
            in.field("front", 2); Direction front = Direction.from(in.readString());
            in.field("top", 3); Direction top = Direction.from(in.readString());
            in.field("joint", 4); Joint joint = Joint.from(in.readString());
            in.field("name", 5); String name = in.readString();
            in.field("target", 6); String target = in.readString();
            in.field("pool", 7); String pool = in.readString();
            in.field("placementPriority", 8); int placement = in.readInt();
            in.field("selectionPriority", 9); int selection = in.readInt();
            in.endObject();
            connectors.add(new Connector(ordinal, position, front, top, joint, name, target,
                    pool, placement, selection));
        }
        in.endArray();
        require(connectors.size() == declaredCount,
                "Trial Chambers declared connector count drift");
        return List.copyOf(connectors);
    }

    private static List<EntitySidecar> readEntitySidecars(JsonReader in) {
        in.beginArray();
        if (in.nextArrayValue(0)) {
            throw invalid("Trial Chambers template entity sidecar boundary changed");
        }
        in.endArray();
        return List.of();
    }

    private static TypedSidecars readTypedSidecars(JsonReader in) {
        in.beginObject();
        in.field("BENT", 0); List<SidecarIdentity> bent = readSidecars(in, 16, "BENT");
        in.field("LOOT", 1); List<SidecarIdentity> loot = readSidecars(in, 16, "LOOT");
        in.field("ENTS", 2); List<SidecarIdentity> ents = readSidecars(in, 1, "ENTS");
        in.field("fixedContainerSemantics", 3);
        List<SidecarIdentity> fixed = readSidecars(in, 8, "fixed-container semantics");
        in.field("decoratedPotSemantics", 4);
        List<SidecarIdentity> pots = readSidecars(in, 8, "decorated-pot semantics");
        in.field("vaultSemantics", 5);
        List<SidecarIdentity> vaults = readSidecars(in, 4, "vault semantics");
        in.field("trialSpawnerConfigPairs", 6);
        List<SidecarIdentity> spawners = readSidecars(in, 16, "trial-spawner semantics");
        in.field("ignoredStructureBlockCount", 7); int ignored = in.readInt();
        in.field("templateEntityCount", 8); int entityCount = in.readInt();
        in.endObject();
        return new TypedSidecars(bent, loot, ents, fixed, pots, vaults, spawners, ignored,
                entityCount);
    }

    private static List<SidecarIdentity> readSidecars(JsonReader in, int maximum, String label) {
        in.beginArray();
        ArrayList<SidecarIdentity> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < maximum, "too many Trial Chambers " + label + " rows");
            in.beginObject();
            in.field("identity", 0); String identity = in.readString();
            in.field("occurrences", 1); int occurrences = in.readInt();
            in.endObject();
            rows.add(new SidecarIdentity(identity, occurrences));
        }
        in.endArray();
        return List.copyOf(rows);
    }

    private static ConfiguredFeatureSemantics readConfiguredFeatures(JsonReader in) {
        in.beginObject();
        in.field("featurePoolElementCount", 0); int count = in.readInt();
        in.field("placedFeaturesInEncounterOrder", 1);
        List<String> placed = readStrings(in, 1, "placed features");
        in.field("configuredFeaturesInEncounterOrder", 2);
        List<String> configured = readStrings(in, 1, "configured features");
        in.field("boundary", 3); String boundary = in.readString();
        in.endObject();
        return new ConfiguredFeatureSemantics(count, placed, configured, boundary);
    }

    private static List<ConnectorPriority> readConnectorPriorities(JsonReader in) {
        in.beginArray();
        ArrayList<ConnectorPriority> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 7, "too many Trial Chambers connector-priority rows");
            in.beginObject();
            in.field("selectionPriority", 0); int selection = in.readInt();
            in.field("placementPriority", 1); int placement = in.readInt();
            in.field("occurrences", 2); int occurrences = in.readInt();
            in.endObject();
            rows.add(new ConnectorPriority(selection, placement, occurrences));
        }
        in.endArray();
        return List.copyOf(rows);
    }

    private static Aggregate readAggregate(JsonReader in) {
        in.beginObject();
        in.field("poolCount", 0); int pools = in.readInt();
        in.field("singlePoolElementCount", 1); int singles = in.readInt();
        in.field("emptyPoolElementCount", 2); int empties = in.readInt();
        in.field("featurePoolElementCount", 3); int features = in.readInt();
        in.field("templateCount", 4); int templates = in.readInt();
        in.field("blockCount", 5); int blocks = in.readInt();
        in.field("commandCount", 6); int commands = in.readInt();
        in.field("connectorCount", 7); int connectors = in.readInt();
        in.field("templateEntityCount", 8); int entities = in.readInt();
        in.field("stateCount", 9); int states = in.readInt();
        in.field("poolSemanticSha256", 10); String poolSha = in.readString();
        in.field("templateEncounterSha256", 11); String templateSha = in.readString();
        in.field("connectorSemanticSha256", 12); String connectorSha = in.readString();
        in.field("stateClosureSha256", 13); String stateSha = in.readString();
        in.endObject();
        return new Aggregate(pools, singles, empties, features, templates, blocks, commands,
                connectors, entities, states, poolSha, templateSha, connectorSha, stateSha);
    }

    private static List<String> readStrings(JsonReader in, int maximum, String label) {
        in.beginArray();
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < maximum, "too many Trial Chambers " + label);
            values.add(in.readString());
        }
        in.endArray();
        return List.copyOf(values);
    }

    private static List<ProcessorType> readProcessorTypes(
            JsonReader in, int maximum, String label) {
        in.beginArray();
        ArrayList<ProcessorType> values = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < maximum, "too many Trial Chambers " + label);
            values.add(ProcessorType.from(in.readString()));
        }
        in.endArray();
        return List.copyOf(values);
    }

    private static Vec3i readVec3i(JsonReader in) {
        in.beginArray();
        require(in.nextArrayValue(0), "missing Trial Chambers vector x"); int x = in.readInt();
        require(in.nextArrayValue(1), "missing Trial Chambers vector y"); int y = in.readInt();
        require(in.nextArrayValue(2), "missing Trial Chambers vector z"); int z = in.readInt();
        require(!in.nextArrayValue(3), "extra Trial Chambers vector coordinate");
        in.endArray();
        return new Vec3i(x, y, z);
    }

    static void validateEvidence(Evidence evidence) {
        validateClassClosure(evidence.classClosure());
        require(evidence.structure().equals(new Structure(
                        STRUCTURE_KEY,
                        "net.minecraft.world.level.levelgen.structure.structures.JigsawStructure",
                        START_POOL, 3, STRUCTURE_CODEC_SHA256)),
                "Trial Chambers structure semantic drift");
        require(evidence.structureSet().equals(new StructureSet(
                        STRUCTURE_KEY, 1, STRUCTURE_KEY, 1, STRUCTURE_SET_CODEC_SHA256)),
                "Trial Chambers structure-set semantic drift");
        require(evidence.registryPoolKeysInEncounterOrder().equals(POOL_KEYS),
                "Trial Chambers registry pool encounter-order drift");
        List<String> templateOrder = validatePools(evidence.poolsInEncounterOrder());
        validateAliases(evidence.aliasesInDeclaredOrder());
        validateProcessors(evidence.processorListsInEncounterOrder(),
                evidence.processorsInEncounterOrder());
        validateTemplates(evidence.templatesInEncounterOrder(), templateOrder,
                evidence.typedSidecars(), evidence.connectorPriorityDistribution());
        require(evidence.typedSidecars().equals(EXPECTED_SIDECARS),
                "Trial Chambers typed-sidecar receipt drift");
        require(evidence.configuredFeatureSemantics().equals(new ConfiguredFeatureSemantics(
                        0, List.of(), List.of(), FEATURE_BOUNDARY)),
                "Trial Chambers configured-feature boundary drift");
        require(evidence.connectorPriorityDistribution().equals(EXPECTED_PRIORITIES),
                "Trial Chambers connector-priority receipt drift");
        require(evidence.aggregate().equals(EXPECTED_AGGREGATE),
                "Trial Chambers aggregate/digest drift");
        require(BOUNDARY.equals(evidence.boundary()), "Trial Chambers evidence boundary drift");
    }

    private static void validateClassClosure(List<ClassPin> classes) {
        require(classes.size() == 21, "Trial Chambers class-closure cardinality drift");
        StringBuilder transcript = new StringBuilder();
        HashSet<String> unique = new HashSet<>();
        for (ClassPin pin : classes) {
            require(pin.className().startsWith("net.minecraft.") && unique.add(pin.className()),
                    "Trial Chambers class-closure identity drift");
            require(isSha256(pin.sha256()), "invalid Trial Chambers class SHA-256");
            transcript.append(pin.className()).append('|').append(pin.sha256()).append('\n');
        }
        require(CLASS_CLOSURE_SHA256.equals(sha256(transcript.toString()
                        .getBytes(StandardCharsets.UTF_8))),
                "Trial Chambers class-closure order/identity drift");
    }

    private static List<String> validatePools(List<Pool> pools) {
        require(pools.size() == POOL_COUNT, "Trial Chambers pool cardinality drift");
        int singles = 0;
        int empties = 0;
        LinkedHashSet<String> templateOrder = new LinkedHashSet<>();
        StringBuilder transcript = new StringBuilder();
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            Pool pool = pools.get(poolIndex);
            require(POOL_KEYS.get(poolIndex).equals(pool.key()),
                    "Trial Chambers pool encounter-order drift: " + pool.key());
            require(EMPTY_POOL.equals(pool.fallback()) || HALLWAY_FALLBACK.equals(pool.fallback()),
                    "unknown Trial Chambers pool fallback: " + pool.fallback());
            require(pool.rawElementCount() == pool.elementsInDeclaredOrder().size(),
                    "Trial Chambers raw pool-element count drift: " + pool.key());
            int expanded = 0;
            for (int elementIndex = 0; elementIndex < pool.elementsInDeclaredOrder().size();
                    elementIndex++) {
                PoolElement element = pool.elementsInDeclaredOrder().get(elementIndex);
                require(element.ordinal() == elementIndex && element.weight() > 0,
                        "Trial Chambers pool element order/weight drift: " + pool.key());
                expanded = Math.addExact(expanded, element.weight());
                if (element instanceof SingleElement single) {
                    singles++;
                    require(single.projection() == Projection.RIGID,
                            "Trial Chambers single projection drift");
                    require(single.template().startsWith("minecraft:trial_chambers/")
                                    && isIdentifier(single.template()),
                            "unknown Trial Chambers template identity: " + single.template());
                    List<ProcessorType> expectedPipeline = single.processorList()
                            == ProcessorListIdentity.INLINE
                            ? List.of(ProcessorType.BLOCK_IGNORE,
                                    ProcessorType.JIGSAW_REPLACEMENT)
                            : List.of(ProcessorType.BLOCK_IGNORE,
                                    ProcessorType.JIGSAW_REPLACEMENT,
                                    ProcessorType.RULE,
                                    ProcessorType.PROTECTED_BLOCKS);
                    require(single.placementProcessorsInOrder().equals(expectedPipeline),
                            "Trial Chambers placement processor order drift");
                    templateOrder.add(single.template());
                    transcript.append(pool.key()).append('|').append(pool.fallback()).append('|')
                            .append(elementIndex).append('|').append(single.weight())
                            .append("|single|rigid|").append(single.template()).append('|')
                            .append(single.processorList().id()).append('\n');
                } else if (element instanceof EmptyElement empty) {
                    empties++;
                    require(empty.projection() == Projection.TERRAIN_MATCHING,
                            "Trial Chambers empty projection drift");
                    transcript.append(pool.key()).append('|').append(pool.fallback()).append('|')
                            .append(elementIndex).append('|').append(empty.weight())
                            .append("|empty|terrain_matching\n");
                }
            }
            require(expanded == pool.expandedWeight(),
                    "Trial Chambers expanded pool weight drift: " + pool.key());
        }
        require(singles == SINGLE_POOL_ELEMENT_COUNT && empties == EMPTY_POOL_ELEMENT_COUNT,
                "Trial Chambers pool-element aggregate drift");
        require(POOL_SEMANTIC_SHA256.equals(sha256(transcript.toString()
                        .getBytes(StandardCharsets.UTF_8))),
                "Trial Chambers weighted-pool semantic/order digest drift");
        List<String> templates = List.copyOf(templateOrder);
        require(templates.size() == TEMPLATE_COUNT
                        && TEMPLATE_ENCOUNTER_SHA256.equals(sha256Lines(templates)),
                "Trial Chambers template encounter closure drift");
        return templates;
    }

    private static void validateAliases(List<AliasBinding> aliases) {
        require(aliases.size() == 3, "Trial Chambers alias cardinality drift");
        String[] suffixes = {"skeleton", "stray", "poison_skeleton"};
        RandomGroupAlias ranged = requireType(aliases.get(0), RandomGroupAlias.class,
                "Trial Chambers ranged alias kind drift");
        require(ranged.ordinal() == 0
                        && ranged.runtimeClass().equals(
                                "net.minecraft.world.level.levelgen.structure.pools.alias.RandomGroupPoolAlias")
                        && ranged.groups().size() == 3,
                "Trial Chambers ranged alias identity drift");
        ArrayList<String> rangedTargets = new ArrayList<>();
        for (int index = 0; index < suffixes.length; index++) {
            String rangedAlias = "minecraft:trial_chambers/spawner/contents/ranged";
            String slowAlias = "minecraft:trial_chambers/spawner/contents/slow_ranged";
            String target = "minecraft:trial_chambers/spawner/ranged/" + suffixes[index];
            String slowTarget = "minecraft:trial_chambers/spawner/slow_ranged/" + suffixes[index];
            AliasGroup group = ranged.groups().get(index);
            require(group.weight() == 1 && group.data().equals(List.of(
                            new DirectAlias(rangedAlias, target),
                            new DirectAlias(slowAlias, slowTarget))),
                    "Trial Chambers ranged alias group drift");
            rangedTargets.add(target);
            rangedTargets.add(slowTarget);
        }
        require(ranged.allTargetsInDeclaredOrder().equals(rangedTargets),
                "Trial Chambers ranged alias target-order drift");
        validateRandomAlias(aliases.get(1), 1,
                "minecraft:trial_chambers/spawner/contents/melee",
                List.of("minecraft:trial_chambers/spawner/melee/zombie",
                        "minecraft:trial_chambers/spawner/melee/husk",
                        "minecraft:trial_chambers/spawner/melee/spider"));
        validateRandomAlias(aliases.get(2), 2,
                "minecraft:trial_chambers/spawner/contents/small_melee",
                List.of("minecraft:trial_chambers/spawner/small_melee/slime",
                        "minecraft:trial_chambers/spawner/small_melee/cave_spider",
                        "minecraft:trial_chambers/spawner/small_melee/silverfish",
                        "minecraft:trial_chambers/spawner/small_melee/baby_zombie"));
    }

    private static void validateRandomAlias(
            AliasBinding value, int ordinal, String alias, List<String> targets) {
        RandomAlias random = requireType(value, RandomAlias.class,
                "Trial Chambers random alias kind drift");
        List<WeightedTarget> weighted = targets.stream()
                .map(target -> new WeightedTarget(target, 1)).toList();
        require(random.ordinal() == ordinal
                        && random.runtimeClass().equals(
                                "net.minecraft.world.level.levelgen.structure.pools.alias.RandomPoolAlias")
                        && random.alias().equals(alias)
                        && random.targets().equals(weighted)
                        && random.allTargetsInDeclaredOrder().equals(targets),
                "Trial Chambers random alias semantic drift");
    }

    private static void validateProcessors(List<ProcessorList> lists, List<Processor> processors) {
        require(lists.equals(expectedProcessorLists()),
                "Trial Chambers processor-list semantic/order drift");
        require(processors.equals(expectedProcessors()),
                "Trial Chambers processor semantic/order drift");
    }

    private static List<ProcessorList> expectedProcessorLists() {
        return List.of(
                new ProcessorList(ProcessorListIdentity.INLINE, false, List.of(), List.of()),
                new ProcessorList(ProcessorListIdentity.COPPER_BULB_DEGRADATION, true,
                        List.of(ProcessorType.RULE, ProcessorType.PROTECTED_BLOCKS),
                        List.of(expectedRuleConfig(),
                                new ProtectedBlocksConfig("#minecraft:features_cannot_replace"))));
    }

    private static List<Processor> expectedProcessors() {
        RuleProcessorConfig expectedRule = expectedRuleConfig();
        return List.of(
                new Processor(ProcessorType.BLOCK_IGNORE,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor",
                        new BlockIgnoreConfig(List.of("minecraft:structure_block")),
                        new BlockIgnoreSemantics("drop_template_state", "minecraft:structure_block")),
                new Processor(ProcessorType.JIGSAW_REPLACEMENT,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor",
                        new JigsawReplacementConfig(),
                        new JigsawReplacementSemantics("jigsaw_final_state", "JIGSAW final_state",
                                "drop", "parse exact block state and clear template NBT")),
                new Processor(ProcessorType.RULE,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor",
                        expectedRule, expectedRuleSemantics()),
                new Processor(ProcessorType.PROTECTED_BLOCKS,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor",
                        new ProtectedBlocksConfig("#minecraft:features_cannot_replace"),
                        new ProtectedBlocksSemantics("existing_world_guard",
                                "existing destination block is #minecraft:features_cannot_replace",
                                "drop processed template block", "pass through")));
    }

    private static RuleProcessorConfig expectedRuleConfig() {
        return new RuleProcessorConfig(List.of(
                new CopperRule("minecraft:waxed_copper_bulb", 0.1,
                        "minecraft:random_block_match", "minecraft:always_true",
                        "minecraft:waxed_oxidized_copper_bulb", "false", "true"),
                new CopperRule("minecraft:waxed_copper_bulb", 0.33333334,
                        "minecraft:random_block_match", "minecraft:always_true",
                        "minecraft:waxed_weathered_copper_bulb", "false", "true"),
                new CopperRule("minecraft:waxed_copper_bulb", 0.5,
                        "minecraft:random_block_match", "minecraft:always_true",
                        "minecraft:waxed_exposed_copper_bulb", "false", "true")));
    }

    private static RuleSemantics expectedRuleSemantics() {
        return new RuleSemantics("ordered_first_match_rules", List.of(
                        new RuleSemanticRow("minecraft:waxed_copper_bulb", 0.1,
                                "minecraft:waxed_oxidized_copper_bulb[lit=true,powered=false]"),
                        new RuleSemanticRow("minecraft:waxed_copper_bulb", 0.33333334,
                                "minecraft:waxed_weathered_copper_bulb[lit=true,powered=false]"),
                        new RuleSemanticRow("minecraft:waxed_copper_bulb", 0.5,
                                "minecraft:waxed_exposed_copper_bulb[lit=true,powered=false]")),
                "net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest",
                "net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest",
                "net.minecraft.world.level.levelgen.structure.templatesystem.PosAlwaysTrueTest",
                "passthrough",
                new PositionalRng(
                        "processed absolute StructureBlockInfo.pos after template transform",
                        "signed i32 x*3129871 before widening",
                        "v=(long)(x*3129871) ^ (long)z*116129781L ^ (long)y; v=v*v*42317861L+v*11L; seed=v>>16",
                        "RandomSource.create(seed) -> LegacyRandomSource",
                        "one RNG per RuleProcessor.processBlock call", "nextFloat",
                        "draw < probability",
                        "input block mismatch consumes no draw; matching waxed_copper_bulb consumes one draw per attempted rule until first success"));
    }

    private static void validateTemplates(
            List<Template> templates,
            List<String> expectedTemplateOrder,
            TypedSidecars sidecars,
            List<ConnectorPriority> priorityReceipt) {
        require(templates.size() == TEMPLATE_COUNT, "Trial Chambers template cardinality drift");
        require(templates.stream().map(Template::key).toList().equals(expectedTemplateOrder),
                "Trial Chambers template encounter order drift");
        TreeSet<String> stateClosure = new TreeSet<>();
        StringBuilder connectorTranscript = new StringBuilder();
        TreeMap<String, Integer> priorityCounts = new TreeMap<>();
        TreeMap<String, Integer> bent = new TreeMap<>();
        TreeMap<String, Integer> loot = new TreeMap<>();
        TreeMap<String, Integer> fixed = new TreeMap<>();
        TreeMap<String, Integer> pots = new TreeMap<>();
        TreeMap<String, Integer> vaults = new TreeMap<>();
        TreeMap<String, Integer> spawners = new TreeMap<>();
        int ignored = 0;
        int blocks = 0;
        int commands = 0;
        int connectors = 0;
        int entities = 0;
        for (Template template : templates) {
            require(isIdentifier(template.key()) && template.key().startsWith("minecraft:trial_chambers/"),
                    "invalid Trial Chambers template key: " + template.key());
            require(template.size().x() > 0 && template.size().y() > 0 && template.size().z() > 0,
                    "invalid Trial Chambers template size: " + template.key());
            require(!template.stateTable().isEmpty()
                            && new HashSet<>(template.stateTable()).size() == template.stateTable().size(),
                    "Trial Chambers template state-table drift: " + template.key());
            for (String state : template.stateTable()) {
                require(isExactState(state), "invalid Trial Chambers exact state: " + state);
                stateClosure.add(state);
            }
            require(template.commandCount() == template.commands().size()
                            && template.connectorCount() == template.connectors().size()
                            && template.entityCount() == 0
                            && template.entities().isEmpty(),
                    "Trial Chambers declared template aggregate drift: " + template.key());
            boolean[] usedStates = new boolean[template.stateTable().size()];
            boolean[] usedConnectors = new boolean[template.connectors().size()];
            int cursor = 0;
            for (Command command : template.commands()) {
                require(command.ordinal() == cursor,
                        "Trial Chambers command ordinal discontinuity: " + template.key());
                require(command.state() >= 0 && command.state() < usedStates.length,
                        "Trial Chambers command state index drift: " + template.key());
                usedStates[command.state()] = true;
                String state = template.stateTable().get(command.state());
                switch (command) {
                    case Run run -> {
                        require(run.count() > 0 && inside(run.start(), template.size()),
                                "Trial Chambers RUN start/count drift: " + template.key());
                        boolean unitDelta = run.delta().x() >= -1 && run.delta().x() <= 1
                                && run.delta().y() >= -1 && run.delta().y() <= 1
                                && run.delta().z() >= -1 && run.delta().z() <= 1
                                && !run.delta().equals(new Vec3i(0, 0, 0));
                        require(run.count() == 1
                                        ? run.delta().equals(new Vec3i(0, 0, 0))
                                        : unitDelta,
                                "Trial Chambers arithmetic RUN delta/count drift: " + template.key());
                        require(inside(run.lastPosition(), template.size()),
                                "Trial Chambers RUN endpoint escaped template: " + template.key());
                    }
                    case Jigsaw jigsaw -> {
                        require("minecraft:jigsaw".equals(blockId(state)),
                                "Trial Chambers JIGSAW state binding drift: " + template.key());
                        require(jigsaw.connectorOrdinal() >= 0
                                        && jigsaw.connectorOrdinal() < usedConnectors.length
                                        && !usedConnectors[jigsaw.connectorOrdinal()],
                                "Trial Chambers JIGSAW connector binding drift: " + template.key());
                        usedConnectors[jigsaw.connectorOrdinal()] = true;
                        require(JIGSAW_FINAL_STATES.contains(jigsaw.finalState()),
                                "unknown Trial Chambers JIGSAW final state: " + jigsaw.finalState());
                    }
                    case IgnoredStructureBlock ignoredBlock -> {
                        require(inside(ignoredBlock.position(), template.size())
                                        && "minecraft:structure_block".equals(blockId(state))
                                        && IGNORED_STRUCTURE_BLOCK_REASON.equals(ignoredBlock.reason()),
                                "Trial Chambers ignored structure-block semantic drift");
                        ignored++;
                    }
                    case LootContainer container -> {
                        require(inside(container.position(), template.size())
                                        && blockId(state).equals(container.blockEntityType().id()),
                                "Trial Chambers loot block-entity/state drift");
                        require(container.blockEntityType() != BlockEntityType.CHEST
                                        || hasHorizontalFacing(state),
                                "Trial Chambers chest state escaped the authenticated horizontal "
                                        + "palette: " + template.key() + "#" + command.ordinal());
                        validateLootContainer(container);
                        increment(bent, "LOOT_CONTAINER|" + container.blockEntityType().id());
                        increment(loot, container.lootTable().id() + "|"
                                + container.blockEntityType().id());
                        container.sherds().ifPresent(value -> increment(pots,
                                "loot|" + sherdSignature(value)));
                    }
                    case FixedContainer container -> {
                        require(inside(container.position(), template.size())
                                        && blockId(state).equals(container.blockEntityType().id()),
                                "Trial Chambers fixed block-entity/state drift");
                        String signature = validateFixedContainer(container);
                        increment(bent, "FIXED_CONTAINER|" + container.blockEntityType().id());
                        increment(fixed, signature);
                    }
                    case DecoratedPot pot -> {
                        require(inside(pot.position(), template.size())
                                        && pot.blockEntityType() == BlockEntityType.DECORATED_POT
                                        && blockId(state).equals(pot.blockEntityType().id())
                                        && pot.item().equals(new ItemStack(ItemIdentity.STRING, 3)),
                                "Trial Chambers fixed decorated-pot semantic drift");
                        increment(bent, "DECORATED_POT|minecraft:decorated_pot");
                        increment(pots, "item|" + sherdSignature(pot.sherds())
                                + "|minecraft:string|count=3");
                    }
                    case Vault vault -> {
                        require(inside(vault.position(), template.size())
                                        && vault.blockEntityType() == BlockEntityType.VAULT
                                        && blockId(state).equals(vault.blockEntityType().id()),
                                "Trial Chambers vault block-entity/state drift");
                        validateVault(vault);
                        increment(bent, "VAULT|minecraft:vault");
                        increment(vaults, vault.keyItem().item().id() + "|1|"
                                + vault.lootTable().id());
                    }
                    case TrialSpawner spawner -> {
                        require(inside(spawner.position(), template.size())
                                        && spawner.blockEntityType() == BlockEntityType.TRIAL_SPAWNER
                                        && blockId(state).equals(spawner.blockEntityType().id()),
                                "Trial Chambers trial-spawner block-entity/state drift");
                        increment(bent, "TRIAL_SPAWNER|minecraft:trial_spawner");
                        increment(spawners, spawner.config().base());
                    }
                }
                cursor = Math.addExact(cursor, command.expandedCount());
            }
            require(cursor == template.blockCount(),
                    "Trial Chambers block-command expansion drift: " + template.key());
            for (boolean used : usedStates) {
                require(used, "Trial Chambers template state table contains unused state");
            }
            for (boolean used : usedConnectors) {
                require(used, "Trial Chambers connector has no JIGSAW command binding");
            }
            HashSet<Vec3i> connectorPositions = new HashSet<>();
            for (int connectorIndex = 0; connectorIndex < template.connectors().size();
                    connectorIndex++) {
                Connector connector = template.connectors().get(connectorIndex);
                require(connector.ordinal() == connectorIndex
                                && inside(connector.position(), template.size())
                                && connectorPositions.add(connector.position()),
                        "Trial Chambers connector order/position drift: " + template.key());
                require(connector.front().axis() != connector.top().axis(),
                        "Trial Chambers connector orientation drift: " + template.key());
                require(isIdentifier(connector.name()) && isIdentifier(connector.target()),
                        "invalid Trial Chambers connector name/target: " + template.key());
                require(EMPTY_POOL.equals(connector.pool()) || POOL_KEYS.contains(connector.pool())
                                || ALIAS_POOL_KEYS.contains(connector.pool()),
                        "unknown Trial Chambers connector pool: " + connector.pool());
                increment(priorityCounts,
                        connector.selectionPriority() + "," + connector.placementPriority());
                connectorTranscript.append(template.key()).append('|').append(connectorIndex)
                        .append('|').append(connector.position().x()).append(',')
                        .append(connector.position().y()).append(',')
                        .append(connector.position().z()).append('|')
                        .append(connector.front().name()).append('|')
                        .append(connector.top().name()).append('|')
                        .append(connector.joint().name()).append('|')
                        .append(connector.name()).append('|').append(connector.target()).append('|')
                        .append(connector.pool()).append('|').append(connector.placementPriority())
                        .append('|').append(connector.selectionPriority()).append('\n');
            }
            blocks = Math.addExact(blocks, template.blockCount());
            commands = Math.addExact(commands, template.commandCount());
            connectors = Math.addExact(connectors, template.connectorCount());
            entities = Math.addExact(entities, template.entityCount());
        }
        require(blocks == BLOCK_COUNT && commands == COMMAND_COUNT
                        && connectors == CONNECTOR_COUNT && entities == TEMPLATE_ENTITY_COUNT,
                "Trial Chambers template aggregate drift");
        require(stateClosure.size() == STATE_COUNT
                        && STATE_CLOSURE_SHA256.equals(sha256Lines(stateClosure)),
                "Trial Chambers exact-state closure drift");
        require(CONNECTOR_SEMANTIC_SHA256.equals(sha256(connectorTranscript.toString()
                        .getBytes(StandardCharsets.UTF_8))),
                "Trial Chambers connector semantic/order digest drift");
        List<ConnectorPriority> derivedPriorities = priorityCounts.entrySet().stream()
                .map(entry -> {
                    String[] pair = entry.getKey().split(",", -1);
                    return new ConnectorPriority(Integer.parseInt(pair[0]),
                            Integer.parseInt(pair[1]), entry.getValue());
                }).toList();
        require(derivedPriorities.equals(priorityReceipt)
                        && derivedPriorities.equals(EXPECTED_PRIORITIES),
                "Trial Chambers connector priority distribution drift");
        TypedSidecars derived = new TypedSidecars(toRows(bent), toRows(loot), List.of(),
                toRows(fixed), toRows(pots), toRows(vaults), toRows(spawners), ignored, entities);
        require(derived.equals(sidecars) && derived.equals(EXPECTED_SIDECARS),
                "Trial Chambers typed sidecars disagree with executable commands");
    }

    private static void validateLootContainer(LootContainer container) {
        BlockEntityType type = container.blockEntityType();
        require(type == BlockEntityType.BARREL || type == BlockEntityType.CHEST
                        || type == BlockEntityType.DECORATED_POT
                        || type == BlockEntityType.DISPENSER,
                "unknown Trial Chambers loot-container block entity: " + type.id());
        require(container.lootTable().allows(type),
                "unknown Trial Chambers loot table/block-entity pair: "
                        + container.lootTable().id() + " / " + type.id());
        if (type == BlockEntityType.DECORATED_POT) {
            require(container.rng() == NoPlacementLootSeedRng.INSTANCE
                            && container.sherds().isPresent(),
                    "Trial Chambers decorated-pot loot seed/sherd drift");
        } else {
            require(container.rng().equals(expectedCallerNextLongRng())
                            && container.sherds().isEmpty(),
                    "Trial Chambers loot-container placement RNG drift");
        }
    }

    private static boolean hasHorizontalFacing(String state) {
        return state.contains("facing=north") || state.contains("facing=east")
                || state.contains("facing=south") || state.contains("facing=west");
    }

    private static String validateFixedContainer(FixedContainer container) {
        require(container.rng().equals(expectedCallerNextLongRng()),
                "Trial Chambers fixed-container placement RNG drift");
        return switch (container.blockEntityType()) {
            case BARREL -> {
                require(container.items().isEmpty() && container.transferCooldown().isEmpty(),
                        "Trial Chambers fixed barrel payload drift");
                yield "minecraft:barrel|empty";
            }
            case DISPENSER -> {
                require(container.items().equals(List.of(new SlotItem(
                                        4, ItemIdentity.WATER_BUCKET, 1)))
                                && container.transferCooldown().isEmpty(),
                        "Trial Chambers fixed dispenser payload drift");
                yield "minecraft:dispenser|slot=4|minecraft:water_bucket|count=1";
            }
            case HOPPER -> {
                require(container.items().isEmpty()
                                && container.transferCooldown().isPresent()
                                && container.transferCooldown().getAsInt() == 0,
                        "Trial Chambers fixed hopper payload drift");
                yield "minecraft:hopper|empty|cooldown=0";
            }
            default -> throw invalid("unknown Trial Chambers fixed-container block entity: "
                    + container.blockEntityType().id());
        };
    }

    private static void validateVault(Vault vault) {
        require(vault.keyItem().count() == 1, "Trial Chambers vault key count drift");
        if (vault.keyItem().item() == ItemIdentity.TRIAL_KEY) {
            require(vault.lootTable() == LootTable.REWARD,
                    "Trial Chambers normal vault loot-table drift");
        } else if (vault.keyItem().item() == ItemIdentity.OMINOUS_TRIAL_KEY) {
            require(vault.lootTable() == LootTable.REWARD_OMINOUS,
                    "Trial Chambers ominous vault loot-table drift");
        } else {
            throw invalid("unknown Trial Chambers vault key identity: " + vault.keyItem().item().id());
        }
    }

    private static CallerNextLongRng expectedCallerNextLongRng() {
        return new CallerNextLongRng(
                "resulting block entity instanceof net.minecraft.world.RandomizableContainer",
                "processed block survives clipping/processors and setBlock succeeds",
                "LootTableSeed", "RandomSource.nextLong");
    }

    private static String sherdSignature(PotSherds value) {
        return "back=" + value.back().id() + ",front=" + value.front().id()
                + ",left=" + value.left().id() + ",right=" + value.right().id();
    }

    private static void increment(TreeMap<String, Integer> values, String key) {
        values.merge(key, 1, Integer::sum);
    }

    private static List<SidecarIdentity> toRows(TreeMap<String, Integer> values) {
        return values.entrySet().stream()
                .map(entry -> new SidecarIdentity(entry.getKey(), entry.getValue())).toList();
    }

    private static boolean inside(Vec3i position, Vec3i size) {
        return position.x() >= 0 && position.x() < size.x()
                && position.y() >= 0 && position.y() < size.y()
                && position.z() >= 0 && position.z() < size.z();
    }

    private static boolean isExactState(String value) {
        return value != null && value.startsWith("minecraft:") && !value.isBlank()
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    private static String blockId(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static boolean isIdentifier(String value) {
        if (value == null) return false;
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1) return false;
        for (int index = 0; index < value.length(); index++) {
            if (index == colon) continue;
            char ch = value.charAt(index);
            boolean namespace = index < colon;
            boolean allowed = ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9'
                    || ch == '_' || ch == '-' || ch == '.' || !namespace && ch == '/';
            if (!allowed) return false;
        }
        return true;
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f')) return false;
        }
        return true;
    }

    private static String sha256Lines(Iterable<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) text.append(value).append('\n');
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static <T> T requireType(Object value, Class<T> type, String message) {
        require(type.isInstance(value), message);
        return type.cast(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    enum Projection {
        RIGID("rigid"), TERRAIN_MATCHING("terrain_matching");
        private final String id;
        Projection(String id) { this.id = id; }
        String id() { return id; }
        static Projection from(String id) {
            for (Projection value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers projection: " + id);
        }
    }

    enum ProcessorListIdentity {
        INLINE("inline"),
        COPPER_BULB_DEGRADATION(COPPER_PROCESSORS);
        private final String id;
        ProcessorListIdentity(String id) { this.id = id; }
        String id() { return id; }
        static ProcessorListIdentity from(String id) {
            for (ProcessorListIdentity value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers processor list: " + id);
        }
    }

    enum ProcessorType {
        BLOCK_IGNORE("minecraft:block_ignore"),
        JIGSAW_REPLACEMENT("minecraft:jigsaw_replacement"),
        RULE("minecraft:rule"),
        PROTECTED_BLOCKS("minecraft:protected_blocks");
        private final String id;
        ProcessorType(String id) { this.id = id; }
        String id() { return id; }
        static ProcessorType from(String id) {
            for (ProcessorType value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers processor: " + id);
        }
    }

    enum AliasKind {
        RANDOM_GROUP("minecraft:random_group"), RANDOM("minecraft:random");
        private final String id;
        AliasKind(String id) { this.id = id; }
        static AliasKind from(String id) {
            for (AliasKind value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers alias kind: " + id);
        }
    }

    enum Axis { X, Y, Z }
    enum Direction {
        DOWN(Axis.Y), UP(Axis.Y), NORTH(Axis.Z), SOUTH(Axis.Z), WEST(Axis.X), EAST(Axis.X);
        private final Axis axis;
        Direction(Axis axis) { this.axis = axis; }
        Axis axis() { return axis; }
        static Direction from(String id) {
            try { return valueOf(id); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Trial Chambers direction: " + id);
            }
        }
    }

    enum Joint {
        ALIGNED, ROLLABLE;
        static Joint from(String id) {
            try { return valueOf(id); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Trial Chambers joint: " + id);
            }
        }
    }

    enum CommandOp {
        RUN, JIGSAW, IGNORED_STRUCTURE_BLOCK, LOOT_CONTAINER, FIXED_CONTAINER,
        DECORATED_POT, VAULT, TRIAL_SPAWNER;
        static CommandOp from(String id) {
            try { return valueOf(id); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Trial Chambers command op: " + id);
            }
        }
    }

    enum BlockEntityType {
        BARREL("minecraft:barrel"), CHEST("minecraft:chest"),
        DECORATED_POT("minecraft:decorated_pot"), DISPENSER("minecraft:dispenser"),
        HOPPER("minecraft:hopper"), VAULT("minecraft:vault"),
        TRIAL_SPAWNER("minecraft:trial_spawner");
        private final String id;
        BlockEntityType(String id) { this.id = id; }
        String id() { return id; }
        static BlockEntityType from(String id) {
            for (BlockEntityType value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers block-entity identity: " + id);
        }
    }

    enum LootTable {
        CORRIDOR("minecraft:chests/trial_chambers/corridor", BlockEntityType.BARREL),
        ENTRANCE("minecraft:chests/trial_chambers/entrance", BlockEntityType.CHEST),
        INTERSECTION_BARREL("minecraft:chests/trial_chambers/intersection_barrel", BlockEntityType.BARREL),
        INTERSECTION("minecraft:chests/trial_chambers/intersection", BlockEntityType.CHEST),
        REWARD("minecraft:chests/trial_chambers/reward", BlockEntityType.CHEST),
        SUPPLY("minecraft:chests/trial_chambers/supply", BlockEntityType.CHEST),
        DISPENSER_CHAMBER("minecraft:dispensers/trial_chambers/chamber", BlockEntityType.DISPENSER),
        DISPENSER_CORRIDOR("minecraft:dispensers/trial_chambers/corridor", BlockEntityType.DISPENSER),
        POT_CORRIDOR("minecraft:pots/trial_chambers/corridor", BlockEntityType.DECORATED_POT),
        REWARD_OMINOUS("minecraft:chests/trial_chambers/reward_ominous", null);
        private final String id;
        private final BlockEntityType lootContainerType;
        LootTable(String id, BlockEntityType lootContainerType) {
            this.id = id; this.lootContainerType = lootContainerType;
        }
        String id() { return id; }
        boolean allows(BlockEntityType type) { return lootContainerType == type; }
        static LootTable from(String id) {
            for (LootTable value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers loot-table identity: " + id);
        }
    }

    enum ItemIdentity {
        WATER_BUCKET("minecraft:water_bucket"), STRING("minecraft:string"),
        TRIAL_KEY("minecraft:trial_key"), OMINOUS_TRIAL_KEY("minecraft:ominous_trial_key");
        private final String id;
        ItemIdentity(String id) { this.id = id; }
        String id() { return id; }
        static ItemIdentity from(String id) {
            for (ItemIdentity value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers item identity: " + id);
        }
    }

    enum PotSherd {
        BRICK("minecraft:brick"), FLOW("minecraft:flow_pottery_sherd"),
        GUSTER("minecraft:guster_pottery_sherd"), SCRAPE("minecraft:scrape_pottery_sherd");
        private final String id;
        PotSherd(String id) { this.id = id; }
        String id() { return id; }
        static PotSherd from(String id) {
            for (PotSherd value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Trial Chambers decorated-pot sherd: " + id);
        }
    }

    record Corpus(int schema, String receiptId, int javaVersion, String serverVersion,
            Evidence evidence, List<CanonicalBentEvidence> canonicalBentEvidenceInCommandOrder) {
        Corpus { canonicalBentEvidenceInCommandOrder = List.copyOf(canonicalBentEvidenceInCommandOrder); }
    }

    record BentCallerRng(String kind, long probeSeedI64, String draw, int drawCount,
            String encounterRule) { }
    record BentRow(String template, int commandOrdinal, BlockEntityType blockEntityType,
            OptionalInt callerRngEncounterOrdinal, OptionalLong probeNextLongI64,
            int canonicalNbtLength, String canonicalNbtSha256) {
        BentRow {
            callerRngEncounterOrdinal = Objects.requireNonNull(callerRngEncounterOrdinal);
            probeNextLongI64 = Objects.requireNonNull(probeNextLongI64);
        }
    }
    record BentCorpus(int schema, String receiptId, int javaVersion, String serverVersion,
            String sourceExecutionReceipt, String sourceExecutionPayloadSha256, BentCallerRng callerRng,
            List<BentRow> rowsInExecutionCorpusOrder, String boundary) {
        BentCorpus { rowsInExecutionCorpusOrder = List.copyOf(rowsInExecutionCorpusOrder); }
    }
    record CanonicalBentEvidence(String template, int commandOrdinal, BlockEntityType blockEntityType,
            OptionalInt callerRngEncounterOrdinal, OptionalLong probeNextLongI64, byte[] canonicalProbeNbt) {
        CanonicalBentEvidence { canonicalProbeNbt = canonicalProbeNbt.clone(); }
        @Override public byte[] canonicalProbeNbt() { return canonicalProbeNbt.clone(); }
        String canonicalNbtSha256() { return sha256(canonicalProbeNbt); }
    }

    record Evidence(List<ClassPin> classClosure, Structure structure, StructureSet structureSet,
            List<String> registryPoolKeysInEncounterOrder, List<Pool> poolsInEncounterOrder,
            List<AliasBinding> aliasesInDeclaredOrder,
            List<ProcessorList> processorListsInEncounterOrder,
            List<Processor> processorsInEncounterOrder, List<Template> templatesInEncounterOrder,
            TypedSidecars typedSidecars, ConfiguredFeatureSemantics configuredFeatureSemantics,
            List<ConnectorPriority> connectorPriorityDistribution, Aggregate aggregate,
            String boundary) {
        Evidence {
            classClosure = List.copyOf(classClosure);
            registryPoolKeysInEncounterOrder = List.copyOf(registryPoolKeysInEncounterOrder);
            poolsInEncounterOrder = List.copyOf(poolsInEncounterOrder);
            aliasesInDeclaredOrder = List.copyOf(aliasesInDeclaredOrder);
            processorListsInEncounterOrder = List.copyOf(processorListsInEncounterOrder);
            processorsInEncounterOrder = List.copyOf(processorsInEncounterOrder);
            templatesInEncounterOrder = List.copyOf(templatesInEncounterOrder);
            connectorPriorityDistribution = List.copyOf(connectorPriorityDistribution);
        }
        Pool requirePool(String key) {
            for (Pool pool : poolsInEncounterOrder) if (pool.key().equals(key)) return pool;
            throw invalid("unknown Trial Chambers pool: " + key);
        }
        Template requireTemplate(String key) {
            for (Template template : templatesInEncounterOrder) if (template.key().equals(key)) return template;
            throw invalid("unknown Trial Chambers template: " + key);
        }
    }

    record ClassPin(String className, String sha256) { }
    record Structure(String registryKey, String runtimeClass, String startPool,
            int aliasBindingCount, String codecSha256) { }
    record StructureSet(String registryKey, int entryCount, String structure, int weight,
            String codecSha256) { }
    record Vec3i(int x, int y, int z) { }

    record Pool(String key, String fallback, int rawElementCount, int expandedWeight,
            List<PoolElement> elementsInDeclaredOrder) {
        Pool { elementsInDeclaredOrder = List.copyOf(elementsInDeclaredOrder); }
    }
    sealed interface PoolElement permits SingleElement, EmptyElement {
        int ordinal(); int weight(); Projection projection();
    }
    record SingleElement(int ordinal, int weight, Projection projection, String template,
            ProcessorListIdentity processorList, List<ProcessorType> placementProcessorsInOrder)
            implements PoolElement {
        SingleElement { placementProcessorsInOrder = List.copyOf(placementProcessorsInOrder); }
    }
    record EmptyElement(int ordinal, int weight, Projection projection) implements PoolElement { }

    sealed interface AliasBinding permits RandomGroupAlias, RandomAlias {
        int ordinal(); String runtimeClass(); List<String> allTargetsInDeclaredOrder(); AliasKind kind();
    }
    record RandomGroupAlias(int ordinal, String runtimeClass, List<String> allTargetsInDeclaredOrder,
            List<AliasGroup> groups) implements AliasBinding {
        RandomGroupAlias {
            allTargetsInDeclaredOrder = List.copyOf(allTargetsInDeclaredOrder);
            groups = List.copyOf(groups);
        }
        @Override public AliasKind kind() { return AliasKind.RANDOM_GROUP; }
    }
    record RandomAlias(int ordinal, String runtimeClass, List<String> allTargetsInDeclaredOrder,
            String alias, List<WeightedTarget> targets) implements AliasBinding {
        RandomAlias {
            allTargetsInDeclaredOrder = List.copyOf(allTargetsInDeclaredOrder);
            targets = List.copyOf(targets);
        }
        @Override public AliasKind kind() { return AliasKind.RANDOM; }
    }
    record AliasGroup(List<DirectAlias> data, int weight) {
        AliasGroup { data = List.copyOf(data); }
    }
    record DirectAlias(String alias, String target) { }
    record WeightedTarget(String data, int weight) { }

    record ProcessorList(ProcessorListIdentity identity, boolean registered,
            List<ProcessorType> processorsInOrder, List<ProcessorConfig> orderedCodec) {
        ProcessorList {
            processorsInOrder = List.copyOf(processorsInOrder);
            orderedCodec = List.copyOf(orderedCodec);
        }
    }
    record Processor(ProcessorType type, String runtimeClass, ProcessorConfig orderedCodec,
            ProcessorSemantics semantics) { }
    sealed interface ProcessorConfig permits BlockIgnoreConfig, JigsawReplacementConfig,
            RuleProcessorConfig, ProtectedBlocksConfig { }
    record BlockIgnoreConfig(List<String> blocks) implements ProcessorConfig {
        BlockIgnoreConfig { blocks = List.copyOf(blocks); }
    }
    record JigsawReplacementConfig() implements ProcessorConfig { }
    record RuleProcessorConfig(List<CopperRule> rules) implements ProcessorConfig {
        RuleProcessorConfig { rules = List.copyOf(rules); }
    }
    record ProtectedBlocksConfig(String value) implements ProcessorConfig { }
    record CopperRule(String inputBlock, double probability, String inputPredicateType,
            String locationPredicateType, String outputStateId, String powered, String lit) { }

    sealed interface ProcessorSemantics permits BlockIgnoreSemantics,
            JigsawReplacementSemantics, RuleSemantics, ProtectedBlocksSemantics { }
    record BlockIgnoreSemantics(String kind, String state) implements ProcessorSemantics { }
    record JigsawReplacementSemantics(String kind, String source, String structureVoid,
            String otherwise) implements ProcessorSemantics { }
    record RuleSemantics(String kind, List<RuleSemanticRow> rulesInOrder,
            String inputPredicateRuntimeClass, String locationPredicateRuntimeClass,
            String positionPredicateRuntimeClass, String blockEntityModifier,
            PositionalRng positionalRng) implements ProcessorSemantics {
        RuleSemantics { rulesInOrder = List.copyOf(rulesInOrder); }
    }
    record ProtectedBlocksSemantics(String kind, String predicate, String onMatch, String onMiss)
            implements ProcessorSemantics { }
    record RuleSemanticRow(String inputBlock, double probability, String outputState) { }
    record PositionalRng(String position, String xMultiply, String seedFormula,
            String randomSource, String lifetime, String draw, String comparison,
            String shortCircuit) { }

    record Template(String key, Vec3i size, List<String> stateTable, int blockCount,
            int commandCount, List<Command> commands, int connectorCount,
            List<Connector> connectors, int entityCount, List<EntitySidecar> entities) {
        Template {
            stateTable = List.copyOf(stateTable);
            commands = List.copyOf(commands);
            connectors = List.copyOf(connectors);
            entities = List.copyOf(entities);
        }
    }
    record EntitySidecar() { }
    sealed interface Command permits Run, Jigsaw, IgnoredStructureBlock, LootContainer,
            FixedContainer, DecoratedPot, Vault, TrialSpawner {
        int ordinal(); int state(); CommandOp op();
        default int expandedCount() { return 1; }
    }
    record Run(int ordinal, Vec3i start, Vec3i delta, int count, int state) implements Command {
        @Override public int expandedCount() { return count; }
        @Override public CommandOp op() { return CommandOp.RUN; }
        Vec3i lastPosition() {
            return new Vec3i(
                    Math.addExact(start.x(), Math.multiplyExact(delta.x(), count - 1)),
                    Math.addExact(start.y(), Math.multiplyExact(delta.y(), count - 1)),
                    Math.addExact(start.z(), Math.multiplyExact(delta.z(), count - 1)));
        }
    }
    record Jigsaw(int ordinal, int state, int connectorOrdinal, String finalState)
            implements Command {
        @Override public CommandOp op() { return CommandOp.JIGSAW; }
    }
    record IgnoredStructureBlock(int ordinal, Vec3i position, int state, String reason)
            implements Command {
        @Override public CommandOp op() { return CommandOp.IGNORED_STRUCTURE_BLOCK; }
    }
    record LootContainer(int ordinal, Vec3i position, int state, BlockEntityType blockEntityType,
            LootTable lootTable, PlacementLootSeedRng rng, Optional<PotSherds> sherds)
            implements Command {
        LootContainer { sherds = Objects.requireNonNull(sherds); }
        @Override public CommandOp op() { return CommandOp.LOOT_CONTAINER; }
    }
    record FixedContainer(int ordinal, Vec3i position, int state, BlockEntityType blockEntityType,
            List<SlotItem> items, PlacementLootSeedRng rng, OptionalInt transferCooldown)
            implements Command {
        FixedContainer { items = List.copyOf(items); transferCooldown = Objects.requireNonNull(transferCooldown); }
        @Override public CommandOp op() { return CommandOp.FIXED_CONTAINER; }
    }
    record DecoratedPot(int ordinal, Vec3i position, int state, BlockEntityType blockEntityType,
            ItemStack item, PotSherds sherds) implements Command {
        @Override public CommandOp op() { return CommandOp.DECORATED_POT; }
    }
    record Vault(int ordinal, Vec3i position, int state, BlockEntityType blockEntityType,
            ItemStack keyItem, LootTable lootTable) implements Command {
        @Override public CommandOp op() { return CommandOp.VAULT; }
    }
    record TrialSpawner(int ordinal, Vec3i position, int state, BlockEntityType blockEntityType,
            TrialSpawnerConfig config) implements Command {
        @Override public CommandOp op() { return CommandOp.TRIAL_SPAWNER; }
    }

    sealed interface PlacementLootSeedRng permits CallerNextLongRng, NoPlacementLootSeedRng { }
    record CallerNextLongRng(String runtimeGuard, String when, String targetNbtField, String draw)
            implements PlacementLootSeedRng { }
    enum NoPlacementLootSeedRng implements PlacementLootSeedRng { INSTANCE }
    record SlotItem(int slot, ItemIdentity item, int count) { }
    record ItemStack(ItemIdentity item, int count) { }
    record PotSherds(PotSherd back, PotSherd front, PotSherd left, PotSherd right) { }
    record TrialSpawnerConfig(String base) {
        TrialSpawnerConfig {
            require(TRIAL_SPAWNER_BASE_SET.contains(base),
                    "unknown Trial Chambers trial-spawner config base: " + base);
        }
        String normalConfig() { return base + "/normal"; }
        String ominousConfig() { return base + "/ominous"; }
        static TrialSpawnerConfig fromPair(String normal, String ominous) {
            require(normal.endsWith("/normal"), "invalid Trial Chambers normal spawner config");
            String base = normal.substring(0, normal.length() - "/normal".length());
            require(TRIAL_SPAWNER_BASE_SET.contains(base) && ominous.equals(base + "/ominous"),
                    "unknown Trial Chambers trial-spawner config pair");
            return new TrialSpawnerConfig(base);
        }
    }

    record Connector(int ordinal, Vec3i position, Direction front, Direction top, Joint joint,
            String name, String target, String pool, int placementPriority,
            int selectionPriority) { }
    record SidecarIdentity(String identity, int occurrences) { }
    record TypedSidecars(List<SidecarIdentity> bent, List<SidecarIdentity> loot,
            List<SidecarIdentity> ents, List<SidecarIdentity> fixedContainerSemantics,
            List<SidecarIdentity> decoratedPotSemantics, List<SidecarIdentity> vaultSemantics,
            List<SidecarIdentity> trialSpawnerConfigPairs, int ignoredStructureBlockCount,
            int templateEntityCount) {
        TypedSidecars {
            bent = List.copyOf(bent); loot = List.copyOf(loot); ents = List.copyOf(ents);
            fixedContainerSemantics = List.copyOf(fixedContainerSemantics);
            decoratedPotSemantics = List.copyOf(decoratedPotSemantics);
            vaultSemantics = List.copyOf(vaultSemantics);
            trialSpawnerConfigPairs = List.copyOf(trialSpawnerConfigPairs);
        }
    }
    record ConfiguredFeatureSemantics(int featurePoolElementCount,
            List<String> placedFeaturesInEncounterOrder,
            List<String> configuredFeaturesInEncounterOrder, String boundary) {
        ConfiguredFeatureSemantics {
            placedFeaturesInEncounterOrder = List.copyOf(placedFeaturesInEncounterOrder);
            configuredFeaturesInEncounterOrder = List.copyOf(configuredFeaturesInEncounterOrder);
        }
    }
    record ConnectorPriority(int selectionPriority, int placementPriority, int occurrences) { }
    record Aggregate(int poolCount, int singlePoolElementCount, int emptyPoolElementCount,
            int featurePoolElementCount, int templateCount, int blockCount, int commandCount,
            int connectorCount, int templateEntityCount, int stateCount,
            String poolSemanticSha256, String templateEncounterSha256,
            String connectorSemanticSha256, String stateClosureSha256) { }

    private static final class JsonReader {
        private static final int MAX_STRING_CHARS = 32_768;
        private final String text;
        private int offset;

        private JsonReader(byte[] bytes) {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException error) {
                throw invalid("Trial Chambers grammar is not UTF-8");
            }
        }

        private void beginObject() { expect('{'); }
        private void endObject() { expect('}'); }
        private void beginArray() { expect('['); }
        private void endArray() { expect(']'); }

        private void field(String expected, int index) {
            skipWhitespace();
            if (index > 0) expectRaw(',');
            String actual = readString();
            if (!expected.equals(actual)) {
                throw invalid("Trial Chambers JSON field order/schema drift: expected "
                        + expected + ", got " + actual);
            }
            expect(':');
        }

        private boolean nextArrayValue(int index) {
            skipWhitespace();
            if (index == 0) return peek() != ']';
            if (peek() == ']') return false;
            expectRaw(',');
            skipWhitespace();
            if (peek() == ']') throw invalid("Trial Chambers JSON trailing array comma");
            return true;
        }

        private String readString() {
            skipWhitespace();
            expectRaw('"');
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char current = text.charAt(offset++);
                if (current == '"') return value.toString();
                if (current < 0x20) throw invalid("Trial Chambers JSON control character");
                if (current != '\\') {
                    value.append(current);
                } else {
                    if (offset >= text.length()) throw invalid("truncated Trial Chambers JSON escape");
                    char escaped = text.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> value.append(readUnicodeEscape());
                        default -> throw invalid("unknown Trial Chambers JSON escape");
                    }
                }
                if (value.length() > MAX_STRING_CHARS) {
                    throw invalid("Trial Chambers JSON string exceeds bound");
                }
            }
            throw invalid("truncated Trial Chambers JSON string");
        }

        private char readUnicodeEscape() {
            if (offset + 4 > text.length()) throw invalid("truncated Trial Chambers Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(text.charAt(offset++), 16);
                if (digit < 0) throw invalid("malformed Trial Chambers Unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private int readInt() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Trial Chambers JSON integer type drift");
            try { return Integer.parseInt(value); }
            catch (NumberFormatException error) { throw invalid("Trial Chambers integer overflow"); }
        }

        private double readDouble() {
            String value = readNumber();
            try {
                double result = Double.parseDouble(value);
                require(Double.isFinite(result), "non-finite Trial Chambers JSON number");
                return result;
            } catch (NumberFormatException error) {
                throw invalid("malformed Trial Chambers JSON number");
            }
        }

        private String readNumber() {
            skipWhitespace();
            int start = offset;
            if (peek() == '-') offset++;
            if (offset >= text.length()) throw invalid("truncated Trial Chambers JSON number");
            char first = text.charAt(offset);
            if (first == '0') {
                offset++;
                if (offset < text.length() && Character.isDigit(text.charAt(offset))) {
                    throw invalid("Trial Chambers JSON leading zero");
                }
            } else if (first >= '1' && first <= '9') {
                do { offset++; } while (offset < text.length() && Character.isDigit(text.charAt(offset)));
            } else {
                throw invalid("malformed Trial Chambers JSON number");
            }
            if (offset < text.length() && text.charAt(offset) == '.') {
                offset++;
                int fraction = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                if (offset == fraction) throw invalid("malformed Trial Chambers JSON fraction");
            }
            if (offset < text.length() && (text.charAt(offset) == 'e' || text.charAt(offset) == 'E')) {
                offset++;
                if (offset < text.length() && (text.charAt(offset) == '+' || text.charAt(offset) == '-')) offset++;
                int exponent = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                if (offset == exponent) throw invalid("malformed Trial Chambers JSON exponent");
            }
            return text.substring(start, offset);
        }

        private boolean readBoolean() {
            skipWhitespace();
            if (text.startsWith("true", offset)) { offset += 4; return true; }
            if (text.startsWith("false", offset)) { offset += 5; return false; }
            throw invalid("Trial Chambers JSON boolean type drift");
        }

        private void expect(char expected) {
            skipWhitespace();
            expectRaw(expected);
        }

        private void expectRaw(char expected) {
            if (offset >= text.length() || text.charAt(offset) != expected) {
                throw invalid("Trial Chambers JSON syntax drift near offset " + offset
                        + ": expected " + expected);
            }
            offset++;
        }

        private char peek() {
            return offset >= text.length() ? '\0' : text.charAt(offset);
        }

        private void skipWhitespace() {
            while (offset < text.length()) {
                char value = text.charAt(offset);
                if (value != ' ' && value != '\n' && value != '\r' && value != '\t') break;
                offset++;
            }
        }

        private void finish() {
            skipWhitespace();
            require(offset == text.length(), "trailing Trial Chambers JSON data");
        }
    }
}
