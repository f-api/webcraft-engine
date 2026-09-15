package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Strict typed Ancient City grammar shared by production authority and accepted evidence decoders.
 *
 * <p>{@link #loadAccepted()} reads only the compact coordinate-free main-resource authority. The
 * multi-megabyte execution corpus and BENT/LOOT evidence are accepted test oracles and are decoded
 * only when their bytes are supplied explicitly by focused tests.</p>
 */
final class Mc263AncientCityGrammar {
    static final String RESOURCE = "/mc263/ancient-city-execution-corpus-v1.json";
    static final int RESOURCE_BYTES = 2_379_197;
    static final String RESOURCE_SHA256 =
            "54913c8ef950a8ae52b509bf6b517501b0d68d39b08bae8a57fb6da06ac4e4c0";
    static final String EVIDENCE_SHA256 =
            "3525fa926c141a98b555a6e931c943d60c2859afc01c73c1bae039b6bf2d24a6";

    static final String BENT_LOOT_RESOURCE =
            "/mc263/ancient-city-bent-loot-evidence-v2.json";
    static final int BENT_LOOT_RESOURCE_BYTES = 40_294;
    static final String BENT_LOOT_RESOURCE_SHA256 =
            "d71cade7c5e1e80f5ee99e63b7800df1e138d2583f7da2edc9074aec427f3a93";
    private static final int BENT_LOOT_SCHEMA = 2;
    private static final String BENT_LOOT_RECEIPT_ID = "ANC-E3D-BENT-LOOT-V2";
    private static final int BENT_LOOT_ROW_COUNT = 52;
    private static final int BENT_LOOT_DRAW_COUNT = 15;
    private static final int BENT_LOOT_LOOT_DRAW_COUNT = 14;
    private static final int BENT_LOOT_PLACEMENT_COUNT = 18;
    private static final int BENT_LOOT_STRUCTURE_INDEX = 0;
    private static final int BENT_LOOT_GENERATION_STEP = 7;
    private static final long BENT_LOOT_WORLD_SEED = 0L;
    private static final String BENT_LOOT_RNG_KIND = "WorldgenRandom<XoroshiroRandomSource>";
    private static final String BENT_LOOT_RNG_SETUP =
            "setDecorationSeed(worldSeed, ownerChunk.minBlockX, ownerChunk.minBlockZ); then setFeatureSeed(decorationSeed, registered structure index within generation step, generationStepOrdinal)";
    private static final String BENT_LOOT_RNG_DRAW = "RandomSource.nextLong";
    private static final String BENT_LOOT_ENCOUNTER_RULE =
            "one caller nextLong after a processed block survives clipping/processors, setBlock succeeds, and the resulting block entity is RandomizableContainer; the value is supplied as LootTableSeed before block-entity load; fixed-item containers consume the draw even when saveWithFullMetadata omits LootTableSeed";
    private static final String BENT_LOOT_BOUNDARY =
            "bounded official StructureTemplate placement at arithmetic test-only origins; registered Ancient City processors, full transformed template clipping boxes and the registered structure decoration step/index are executed directly; canonical COMPOUND bytes are runtime saveWithFullMetadata outputs reduced to length/SHA receipts, while the accepted semantic command payload remains the only shipped payload preimage; no raw template NBT, production-coordinate lookup or generated-start replay is emitted";
    private static final List<RuntimeClassPin> BENT_LOOT_CLASS_PINS = List.of(
            new RuntimeClassPin("net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus",
                    "70d31ed88a59ea6a529e60d63a7c32db23ee1e94ae259b6a8ecf9bce78ba57a3"),
            new RuntimeClassPin("net.minecraft.world.level.levelgen.XoroshiroRandomSource",
                    "43c6e33b55c91bd4576d2cf04ba7e5e7d2561c5918e1b3549485639d17906447"),
            new RuntimeClassPin("net.minecraft.world.level.levelgen.WorldgenRandom",
                    "33b2089b54c7bc47c687d2b317d5b2b6948161f12597d63a611a893b8fadadc0"),
            new RuntimeClassPin("net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate",
                    "6730a621dd6b800a769e2c2a5bec797b83c9fd90cf8448245940b7b546234e67"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.BlockEntity",
                    "d78cd22088caba501be3bb15f018082628460c01aadbb1b5ac6360c28ecba9b1"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity",
                    "13838ba547b3367733bac3609491e1a4ab52acfeb557d32159b61cc6530eafdb"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.ChestBlockEntity",
                    "00b11b95bdd44dc991909765080e5fccfb98aa511d9a585c26d4fccf9d4c21d1"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.ComparatorBlockEntity",
                    "9ec89879a22a58f3813f7cf52743813029b108034b7f70eb1661a870d4cb6662"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.SculkSensorBlockEntity",
                    "e50204419153b3dc0436ee8905d6d046892dd84e00fe3dc88e80349b461631fb"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.LecternBlockEntity",
                    "9299040ddd8fd8a3a38ecf2b556ed6ed35f32c764be0289a27e8de84e9083a71"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.CampfireBlockEntity",
                    "23965733553196dd5e4a5d01e75de355b35b776aa4a976c32800740b38cd9e89"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.SkullBlockEntity",
                    "ad865ccf3fe30ba13a2db3d5c56642a3b62a6beb822b7bb82c8075a89468a392"),
            new RuntimeClassPin("net.minecraft.world.level.block.entity.FurnaceBlockEntity",
                    "ac5fd37f9b31536f30f4e25cbc88fcd9ba96c3362780e55cfb9630170dbe8a5e"));

    static final String START_GRAPH_RESOURCE =
            "/mc263/ancient-city-start-graph-v1.json.gz.b64";
    static final int START_GRAPH_RESOURCE_BYTES = 1_649_610;
    static final String START_GRAPH_RESOURCE_SHA256 =
            "15c9c62d614799dcdb6e818d3602d11babc478e2cb723a53613f7f0e985880cf";
    private static final int START_GRAPH_GZIP_BYTES = 1_226_982;
    private static final String START_GRAPH_GZIP_SHA256 =
            "be3fb52dd25184630686caa6b7baba972f4a7400c25e85cd1ad84b97a796f172";
    private static final int START_GRAPH_JSON_BYTES = 6_712_875;
    private static final String START_GRAPH_JSON_SHA256 =
            "92c1e788326fe64b654ac9c72ea1579bfd4b6688d4d307a87c521bd500084999";
    private static final int START_GRAPH_SCHEMA = 1;
    private static final String START_GRAPH_JAVA_VERSION = "25.0.1+8-LTS";
    private static final String START_GRAPH_SERVER_VERSION = "26.3 Snapshot 7";
    static final String MUTABLE_SUCCESSOR_RESOURCE =
            "/mc263/ancient-city-mutable-successor-v1.json";
    static final int MUTABLE_SUCCESSOR_RESOURCE_BYTES = 19_251;
    static final String MUTABLE_SUCCESSOR_RESOURCE_SHA256 =
            "664a86f227a877f6623bbdaff68e17aee47afd853cb94ea0f0ba8ae97a80ecb1";
    private static final String MUTABLE_SUCCESSOR_JAVA_SOURCE_SHA256 =
            "7e7090c5fc48a5489e003265663f79c7a98777e0a73aa0fd8cdbef9693217ca3";
    private static final String MUTABLE_SUCCESSOR_WRAPPER_SOURCE_SHA256 =
            "5da4ec0c2469a63f9edc7a7c5f84d2e9eab8129a6ae8abc5c63b0d6ccb01ec76";
    private static final int MUTABLE_SUCCESSOR_BRIDGE_BYTES = 18_027;
    private static final String MUTABLE_SUCCESSOR_BRIDGE_SHA256 =
            "7d4cafe5c7484f18e8f321341d0bafc9511cdbb4ffaebd6c0f8ce27ae9b163f7";
    private static final String MUTABLE_SUCCESSOR_SHA256 =
            "c2d0aabc17eeeee93c0ecb9f4980de3e494ee9bb049c3a3f107b1710f99a0282";
    private static final String MUTABLE_SUCCESSOR_BOUNDARY =
            "official StructureManager.addReference(StructureStart) then createTag at the canonical start chunk";
    private static final long START_GRAPH_WORLD_SEED = 2_630_007L;
    private static final int START_GRAPH_SPACING = 24;
    private static final int START_GRAPH_SEPARATION = 8;
    private static final int START_GRAPH_SALT = 20_083_232;
    private static final long LEGACY_MULTIPLIER = 0x5DEECE66DL;
    private static final long LEGACY_ADDEND = 0xBL;
    private static final long LEGACY_MASK = (1L << 48) - 1L;
    private static final int MAX_START_NBT_BYTES = 1_048_576;
    private static final int START_GRAPH_PROBE_COUNT = 3;
    private static final int[] START_GRAPH_PIECE_COUNTS = {99, 100, 78};
    private static final int[] START_GRAPH_EDGE_COUNTS = {98, 99, 77};
    private static final int[] START_GRAPH_WORLDGEN_COUNTS = {22_873, 20_572, 14_580};
    private static final int[] START_GRAPH_OPERATION_COUNTS = {22_875, 20_574, 14_582};
    private static final String[] START_GRAPH_RNG_SHA256 = {
            "271d3d71c5d13d61f09c2458da0a4b250085afce7825f2d24efc30efe349484b",
            "f87f7f752370907483de36603e17927c612888c463c2c409772961e7871ee7bd",
            "762f3cd97bc0408121d0f63f59cb3f5c836594198b3b104cb3dbc22441fea42a"};
    private static final String[] START_GRAPH_FINAL_STATE48 = {
            "67161111489579", "179544255485696", "38939723133846"};
    private static final int[] START_GRAPH_START_NBT_LENGTHS = {53_969, 53_432, 41_463};
    private static final String[] START_GRAPH_START_NBT_SHA256 = {
            "7d81c72106fc06e1753e9a9ff750353a428d02576e41a67830a9d302879738ce",
            "146dbe3396cbd8b8256c98fb941d5bde40a6a634d47756c126fcfa683a46f96a",
            "2113982e65040df0d9d915e06dace33c72c0e4c75835aba90f6dd08ac6fa09c1"};
    private static final Set<String> START_GRAPH_ROTATIONS = Set.of(
            "NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90");

    private static final String EMPTY_JSON_SHA256 =
            "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945";
    private static final String EXPECTED_ABSENT_TEMPLATE =
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_5";
    private static final String SCULK_FEATURE = "minecraft:sculk_patch_ancient_city";

    private static final List<String> POOL_KEYS = List.of(
            "minecraft:ancient_city/city_center",
            "minecraft:ancient_city/structures",
            "minecraft:ancient_city/sculk",
            "minecraft:ancient_city/walls",
            "minecraft:ancient_city/walls/no_corners",
            "minecraft:ancient_city/city_center/walls",
            "minecraft:ancient_city/city/entrance");

    private static final List<String> TEMPLATE_KEYS = List.of(
            "minecraft:ancient_city/city_center/city_center_1",
            "minecraft:ancient_city/city_center/city_center_2",
            "minecraft:ancient_city/city_center/city_center_3",
            "minecraft:ancient_city/structures/barracks",
            "minecraft:ancient_city/structures/chamber_1",
            "minecraft:ancient_city/structures/chamber_2",
            "minecraft:ancient_city/structures/chamber_3",
            "minecraft:ancient_city/structures/sauna_1",
            "minecraft:ancient_city/structures/small_statue",
            "minecraft:ancient_city/structures/large_ruin_1",
            "minecraft:ancient_city/structures/tall_ruin_1",
            "minecraft:ancient_city/structures/tall_ruin_2",
            "minecraft:ancient_city/structures/tall_ruin_3",
            "minecraft:ancient_city/structures/tall_ruin_4",
            "minecraft:ancient_city/structures/camp_1",
            "minecraft:ancient_city/structures/camp_2",
            "minecraft:ancient_city/structures/camp_3",
            "minecraft:ancient_city/structures/medium_ruin_1",
            "minecraft:ancient_city/structures/medium_ruin_2",
            "minecraft:ancient_city/structures/small_ruin_1",
            "minecraft:ancient_city/structures/small_ruin_2",
            "minecraft:ancient_city/structures/large_pillar_1",
            "minecraft:ancient_city/structures/medium_pillar_1",
            "minecraft:ancient_city/structures/ice_box_1",
            "minecraft:ancient_city/walls/intact_corner_wall_1",
            "minecraft:ancient_city/walls/intact_intersection_wall_1",
            "minecraft:ancient_city/walls/intact_lshape_wall_1",
            "minecraft:ancient_city/walls/intact_horizontal_wall_1",
            "minecraft:ancient_city/walls/intact_horizontal_wall_2",
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_1",
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_2",
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_3",
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_4",
            "minecraft:ancient_city/walls/intact_horizontal_wall_passage_1",
            "minecraft:ancient_city/walls/ruined_corner_wall_1",
            "minecraft:ancient_city/walls/ruined_corner_wall_2",
            "minecraft:ancient_city/walls/ruined_horizontal_wall_stairs_1",
            "minecraft:ancient_city/walls/ruined_horizontal_wall_stairs_2",
            "minecraft:ancient_city/walls/ruined_horizontal_wall_stairs_3",
            "minecraft:ancient_city/walls/ruined_horizontal_wall_stairs_4",
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_5",
            "minecraft:ancient_city/walls/intact_horizontal_wall_bridge",
            "minecraft:ancient_city/city_center/walls/bottom_1",
            "minecraft:ancient_city/city_center/walls/bottom_2",
            "minecraft:ancient_city/city_center/walls/bottom_left_corner",
            "minecraft:ancient_city/city_center/walls/bottom_right_corner_1",
            "minecraft:ancient_city/city_center/walls/bottom_right_corner_2",
            "minecraft:ancient_city/city_center/walls/left",
            "minecraft:ancient_city/city_center/walls/right",
            "minecraft:ancient_city/city_center/walls/top",
            "minecraft:ancient_city/city_center/walls/top_right_corner",
            "minecraft:ancient_city/city_center/walls/top_left_corner",
            "minecraft:ancient_city/city/entrance/entrance_connector",
            "minecraft:ancient_city/city/entrance/entrance_path_1",
            "minecraft:ancient_city/city/entrance/entrance_path_2",
            "minecraft:ancient_city/city/entrance/entrance_path_3",
            "minecraft:ancient_city/city/entrance/entrance_path_4",
            "minecraft:ancient_city/city/entrance/entrance_path_5");

    private static final List<String> PROCESSOR_LIST_IDENTITIES = List.of(
            "minecraft:ancient_city_start_degradation",
            "minecraft:ancient_city_generic_degradation",
            "inline",
            "minecraft:ancient_city_walls_degradation");

    private static final List<String> PROCESSOR_SEMANTIC_IDENTITIES = List.of(
            "minecraft:rule:1770be7ab86fd0249ecce1ee49b86483c15ed67fdcca7fc037e75de99846244b",
            "minecraft:protected_blocks:5a695f52c8dbf2af06c6b6146fada8fa999b3a3ac2030c9a1fc222b571b8f3e2",
            "minecraft:block_ignore:b9bb5faed4dbea8a86d1720de4f6cb1e5cad5fec0ec2e98d0386942e3bf377f7",
            "minecraft:jigsaw_replacement:9a064af3d024acc4cfada5f54662b7c6abb943a38d5d196e9651c8c3f8430a34",
            "minecraft:block_rot:3be74ecfda1988c24fb449bd37e184a09d71aa85738603cf08ced7335c261738",
            "minecraft:rule:7aee803a659d7cf91ca5dc93e68588b21455314fd4b25c04ebc2bac1ee9cf934");

    private static final Set<String> VALID_LOOT_TABLES = Set.of(
            "minecraft:chests/ancient_city", "minecraft:chests/ancient_city_ice_box");

    private final List<String> registryPoolKeysInExecutionOrder;
    private final List<Pool> poolsInExecutionOrder;
    private final List<ProcessorListSpec> processorListsInEncounterOrder;
    private final List<ProcessorSpec> processorSemanticsInEncounterOrder;
    private final ConfiguredFeature configuredFeature;
    private final List<Template> templatesInEncounterOrder;
    private final TypedSidecars typedSidecars;
    private final List<String> statesInEncounterOrder;
    private final Aggregate aggregate;
    private final List<BentLootPlacement> bentLootPlacementsInExecutionCorpusOrder;
    private final List<CanonicalBlockEntityEvidence> canonicalBlockEntitiesInExecutionOrder;
    private final List<LootSeedEvidence> lootSeedsInExecutionCorpusOrder;

    Mc263AncientCityGrammar(
            List<String> registryPoolKeysInExecutionOrder,
            List<Pool> poolsInExecutionOrder,
            List<ProcessorListSpec> processorListsInEncounterOrder,
            List<ProcessorSpec> processorSemanticsInEncounterOrder,
            ConfiguredFeature configuredFeature,
            List<Template> templatesInEncounterOrder,
            TypedSidecars typedSidecars,
            List<String> statesInEncounterOrder,
            Aggregate aggregate,
            List<BentLootPlacement> bentLootPlacementsInExecutionCorpusOrder,
            List<CanonicalBlockEntityEvidence> canonicalBlockEntitiesInExecutionOrder,
            List<LootSeedEvidence> lootSeedsInExecutionCorpusOrder) {
        this.registryPoolKeysInExecutionOrder = List.copyOf(registryPoolKeysInExecutionOrder);
        this.poolsInExecutionOrder = List.copyOf(poolsInExecutionOrder);
        this.processorListsInEncounterOrder = List.copyOf(processorListsInEncounterOrder);
        this.processorSemanticsInEncounterOrder = List.copyOf(processorSemanticsInEncounterOrder);
        this.configuredFeature = configuredFeature;
        this.templatesInEncounterOrder = List.copyOf(templatesInEncounterOrder);
        this.typedSidecars = typedSidecars;
        this.statesInEncounterOrder = List.copyOf(statesInEncounterOrder);
        this.aggregate = aggregate;
        this.bentLootPlacementsInExecutionCorpusOrder =
                List.copyOf(bentLootPlacementsInExecutionCorpusOrder);
        this.canonicalBlockEntitiesInExecutionOrder =
                List.copyOf(canonicalBlockEntitiesInExecutionOrder);
        this.lootSeedsInExecutionCorpusOrder = List.copyOf(lootSeedsInExecutionCorpusOrder);
    }

    static Mc263AncientCityGrammar loadAccepted() {
        return Mc263AncientCityProductionAuthority.load();
    }

    static Mc263AncientCityGrammar decodeAcceptedEvidenceForTest(
            byte[] executionCorpus, byte[] bentLootEvidence) {
        return attachBentLootEvidence(decodeAuthenticated(executionCorpus),
                decodeBentLootAuthenticated(bentLootEvidence));
    }

    private static Mc263AncientCityGrammar decodeAuthenticated(byte[] bytes) {
        require(bytes != null, "Ancient City grammar bytes are null");
        require(bytes.length == RESOURCE_BYTES,
                "Ancient City grammar resource byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)),
                "Ancient City grammar resource SHA-256 drift");
        return parseEvidence(bytes);
    }

    private static BentLootCorpus decodeBentLootAuthenticated(byte[] bytes) {
        require(bytes != null, "Ancient City BENT/LOOT evidence bytes are null");
        require(bytes.length == BENT_LOOT_RESOURCE_BYTES,
                "Ancient City BENT/LOOT evidence byte-count drift");
        require(BENT_LOOT_RESOURCE_SHA256.equals(sha256(bytes)),
                "Ancient City BENT/LOOT evidence SHA-256 drift");
        return parseBentLootEvidence(bytes);
    }

    private static BentLootCorpus parseBentLootEvidence(byte[] bytes) {
        require(bytes != null, "Ancient City BENT/LOOT evidence bytes are null");
        require(bytes.length <= BENT_LOOT_RESOURCE_BYTES + 4_096,
                "Ancient City BENT/LOOT evidence parser bound exceeded");
        JsonReader reader = new JsonReader(bytes);
        reader.beginObject();
        reader.field("schema", 0); int schema = reader.readInt();
        reader.field("receiptId", 1); String receipt = reader.readString();
        reader.field("javaVersion", 2); String javaVersion = reader.readString();
        reader.field("serverVersion", 3); String serverVersion = reader.readString();
        reader.field("evidence", 4);
        reader.beginObject();
        reader.field("sourceExecutionReceipt", 0); String sourceReceipt = reader.readString();
        reader.field("sourceExecutionPayloadSha256", 1); String sourceSha = reader.readString();
        reader.field("runtimeClassPinsInOrder", 2); List<RuntimeClassPin> pins = readRuntimeClassPins(reader);
        reader.field("callerRng", 3); BentLootCallerRng caller = readBentLootCallerRng(reader);
        reader.field("placementsInExecutionCorpusOrder", 4);
        List<ParsedBentLootPlacement> placements = readBentLootPlacements(reader);
        reader.field("spwn", 5); requireEmptyArray(reader, "Ancient City BENT/LOOT SPWN must remain empty");
        reader.field("ents", 6); requireEmptyArray(reader, "Ancient City BENT/LOOT ENTS must remain empty");
        reader.field("boundary", 7); String boundary = reader.readString();
        reader.endObject();
        reader.endObject();
        reader.finish();
        BentLootCorpus result = new BentLootCorpus(schema, receipt, javaVersion, serverVersion,
                sourceReceipt, sourceSha, pins, caller, placements, boundary);
        validateBentLootCorpusShape(result);
        return result;
    }

    private static List<RuntimeClassPin> readRuntimeClassPins(JsonReader reader) {
        ArrayList<RuntimeClassPin> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < BENT_LOOT_CLASS_PINS.size() + 1,
                    "too many Ancient City BENT/LOOT runtime class pins");
            reader.beginObject();
            reader.field("class", 0); String runtimeClass = reader.readString();
            reader.field("sha256", 1); String digest = reader.readString();
            reader.endObject();
            result.add(new RuntimeClassPin(runtimeClass, digest));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static BentLootCallerRng readBentLootCallerRng(JsonReader reader) {
        reader.beginObject();
        reader.field("kind", 0); String kind = reader.readString();
        reader.field("worldSeedI64", 1); long worldSeed = parseI64(reader.readString(), "world seed");
        reader.field("setup", 2); String setup = reader.readString();
        reader.field("draw", 3); String draw = reader.readString();
        reader.field("totalDrawCount", 4); int drawCount = reader.readInt();
        reader.field("lootDrawCount", 5); int lootDrawCount = reader.readInt();
        reader.field("encounterRule", 6); String encounterRule = reader.readString();
        reader.endObject();
        return new BentLootCallerRng(kind, worldSeed, setup, draw, drawCount, lootDrawCount,
                encounterRule);
    }

    private static List<ParsedBentLootPlacement> readBentLootPlacements(JsonReader reader) {
        ArrayList<ParsedBentLootPlacement> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < BENT_LOOT_PLACEMENT_COUNT + 1,
                    "too many Ancient City BENT/LOOT placements");
            reader.beginObject();
            reader.field("template", 0); String template = reader.readString();
            reader.field("templateOrdinal", 1); int templateOrdinal = reader.readInt();
            reader.field("processorList", 2); String processorList = reader.readString();
            reader.field("origin", 3); Pos origin = readPos(reader);
            reader.field("boundingBox", 4); BentLootBox bounds = readBentLootBox(reader);
            reader.field("ownerChunk", 5); ChunkCoord owner = readChunkCoord(reader);
            reader.field("placementRng", 6); PlacementRng rng = readPlacementRng(reader);
            reader.field("rowsInCommandOrder", 7); List<BentLootRow> rows = readBentLootRows(reader);
            reader.endObject();
            result.add(new ParsedBentLootPlacement(template, templateOrdinal, processorList,
                    origin, bounds, owner, rng, rows));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static BentLootBox readBentLootBox(JsonReader reader) {
        reader.beginArray();
        int[] values = new int[6];
        for (int index = 0; index < values.length; index++) {
            require(reader.nextArrayValue(index), "Ancient City BENT/LOOT box coordinate absent");
            values[index] = reader.readInt();
        }
        require(!reader.nextArrayValue(6), "Ancient City BENT/LOOT box cardinality drift");
        reader.endArray();
        return new BentLootBox(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private static ChunkCoord readChunkCoord(JsonReader reader) {
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City BENT/LOOT chunk x absent");
        int x = reader.readInt();
        require(reader.nextArrayValue(1), "Ancient City BENT/LOOT chunk z absent");
        int z = reader.readInt();
        require(!reader.nextArrayValue(2), "Ancient City BENT/LOOT chunk cardinality drift");
        reader.endArray();
        return new ChunkCoord(x, z);
    }

    private static PlacementRng readPlacementRng(JsonReader reader) {
        reader.beginObject();
        reader.field("worldSeedI64", 0); long worldSeed = parseI64(reader.readString(), "placement world seed");
        reader.field("chunk", 1); ChunkCoord chunk = readChunkCoord(reader);
        reader.field("decorationSeedI64", 2); long decorationSeed = parseI64(reader.readString(), "decoration seed");
        reader.field("structureIndex", 3); int structureIndex = reader.readInt();
        reader.field("generationStepOrdinal", 4); int step = reader.readInt();
        reader.field("initial", 5); XoroshiroState initial = readXoroshiroState(reader, false);
        reader.field("draws", 6);
        ArrayList<PlacementDraw> draws = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < BENT_LOOT_DRAW_COUNT + 1,
                    "too many Ancient City BENT/LOOT placement draws");
            reader.beginObject();
            reader.field("ordinal", 0); int ordinal = reader.readInt();
            reader.field("signedSeedI64", 1); long seed = parseI64(reader.readString(), "placement draw");
            reader.field("before", 2); XoroshiroState before = readXoroshiroState(reader, false);
            reader.field("after", 3); XoroshiroState after = readXoroshiroState(reader, false);
            reader.endObject();
            draws.add(new PlacementDraw(ordinal, seed, before, after));
            index++;
        }
        reader.endArray();
        reader.field("final", 7); XoroshiroState finished = readXoroshiroState(reader, true);
        reader.endObject();
        return new PlacementRng(worldSeed, chunk, decorationSeed, structureIndex, step,
                initial, draws, finished);
    }

    private static XoroshiroState readXoroshiroState(JsonReader reader, boolean finished) {
        reader.beginObject();
        reader.field("worldgenCount", 0); int count = reader.readInt();
        reader.field("seedLoI64", 1); long lo = parseI64(reader.readString(), "Xoroshiro lo");
        reader.field("seedHiI64", 2); long hi = parseI64(reader.readString(), "Xoroshiro hi");
        List<Long> continuation = List.of();
        if (finished) {
            reader.field("continuationNextLongI64", 3);
            ArrayList<Long> values = new ArrayList<>();
            reader.beginArray();
            int index = 0;
            while (reader.nextArrayValue(index)) {
                require(index < 9, "too many Ancient City BENT/LOOT continuation words");
                values.add(parseI64(reader.readString(), "Xoroshiro continuation"));
                index++;
            }
            reader.endArray();
            continuation = List.copyOf(values);
        }
        reader.endObject();
        return new XoroshiroState(count, lo, hi, continuation);
    }

    private static List<BentLootRow> readBentLootRows(JsonReader reader) {
        ArrayList<BentLootRow> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < BENT_LOOT_ROW_COUNT + 1, "too many Ancient City BENT/LOOT rows");
            reader.beginObject();
            reader.field("commandOrdinal", 0); int commandOrdinal = reader.readInt();
            reader.field("op", 1); Opcode op = Opcode.fromWire(reader.readString());
            require(op == Opcode.BENT || op == Opcode.LOOT_CONTAINER,
                    "Ancient City BENT/LOOT row opcode drift");
            reader.field("blockEntityType", 2); String type = reader.readString();
            reader.field("runtimeClass", 3); String runtimeClass = reader.readString();
            reader.field("localPosition", 4); Pos local = readPos(reader);
            reader.field("placedPosition", 5); Pos placed = readPos(reader);
            OptionalInt rngOrdinal = OptionalInt.empty();
            OptionalLong signedSeed = OptionalLong.empty();
            Optional<String> lootTable = Optional.empty();
            int field = 6;
            if ("minecraft:chest".equals(type)) {
                reader.field("callerRngEncounterOrdinal", field++);
                rngOrdinal = OptionalInt.of(reader.readInt());
                reader.field("signedSeedI64", field++);
                signedSeed = OptionalLong.of(parseI64(reader.readString(), "LootTableSeed"));
                if (op == Opcode.LOOT_CONTAINER) {
                    reader.field("lootTable", field++); lootTable = Optional.of(reader.readString());
                }
            }
            reader.field("canonicalNbtLength", field++); int length = reader.readInt();
            reader.field("canonicalNbtSha256", field); String digest = reader.readString();
            reader.endObject();
            result.add(new BentLootRow(commandOrdinal, op, type, runtimeClass, local, placed,
                    rngOrdinal, signedSeed, lootTable, length, digest));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static long parseI64(String value, String label) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw invalid("invalid Ancient City " + label); }
    }

    private static void validateBentLootCorpusShape(BentLootCorpus value) {
        require(value.schema() == BENT_LOOT_SCHEMA, "Ancient City BENT/LOOT schema drift");
        require(BENT_LOOT_RECEIPT_ID.equals(value.receiptId()), "Ancient City BENT/LOOT receipt drift");
        require("25.0.1+8-LTS".equals(value.javaVersion()), "Ancient City BENT/LOOT Java drift");
        require("26.3 Snapshot 7".equals(value.serverVersion()), "Ancient City BENT/LOOT server drift");
        require("ANC-E2A".equals(value.sourceExecutionReceipt())
                        && RESOURCE_SHA256.equals(value.sourceExecutionPayloadSha256()),
                "Ancient City BENT/LOOT source execution identity drift");
        require(value.runtimeClassPinsInOrder().equals(BENT_LOOT_CLASS_PINS),
                "Ancient City BENT/LOOT runtime class closure drift");
        require(value.callerRng().equals(new BentLootCallerRng(BENT_LOOT_RNG_KIND,
                        BENT_LOOT_WORLD_SEED, BENT_LOOT_RNG_SETUP, BENT_LOOT_RNG_DRAW,
                        BENT_LOOT_DRAW_COUNT, BENT_LOOT_LOOT_DRAW_COUNT,
                        BENT_LOOT_ENCOUNTER_RULE)),
                "Ancient City BENT/LOOT caller RNG contract drift");
        require(value.placementsInExecutionCorpusOrder().size() == BENT_LOOT_PLACEMENT_COUNT,
                "Ancient City BENT/LOOT placement cardinality drift");
        require(BENT_LOOT_BOUNDARY.equals(value.boundary()), "Ancient City BENT/LOOT boundary drift");
    }

    private static Mc263AncientCityGrammar attachBentLootEvidence(
            Mc263AncientCityGrammar grammar, BentLootCorpus evidence) {
        ArrayList<BentLootPlacement> placements = new ArrayList<>();
        ArrayList<CanonicalBlockEntityEvidence> canonical = new ArrayList<>();
        ArrayList<LootSeedEvidence> loot = new ArrayList<>();
        int placementIndex = 0;
        int totalDraws = 0;
        for (int templateOrdinal = 0; templateOrdinal < grammar.templatesInEncounterOrder.size();
                templateOrdinal++) {
            Template template = grammar.templatesInEncounterOrder.get(templateOrdinal);
            List<Command> targetCommands = template.commands().stream()
                    .filter(command -> command instanceof Bent || command instanceof LootContainer)
                    .toList();
            if (targetCommands.isEmpty()) continue;
            require(placementIndex < evidence.placementsInExecutionCorpusOrder().size(),
                    "Ancient City BENT/LOOT placement evidence ended early");
            ParsedBentLootPlacement parsed =
                    evidence.placementsInExecutionCorpusOrder().get(placementIndex);
            require(parsed.template().equals(template.id()) && parsed.templateOrdinal() == templateOrdinal,
                    "Ancient City BENT/LOOT template encounter-order drift");
            String expectedProcessor = processorListForTemplate(grammar, template.id());
            require(parsed.processorList().equals(expectedProcessor),
                    "Ancient City BENT/LOOT processor-list drift");
            Pos expectedOrigin = new Pos(8192 + templateOrdinal * 128 + 14, 160, 8192 + 14);
            require(parsed.origin().equals(expectedOrigin), "Ancient City BENT/LOOT arithmetic origin drift");
            Size size = template.size().orElseThrow(() -> invalid("BENT/LOOT template unexpectedly absent"));
            BentLootBox expectedBounds = new BentLootBox(expectedOrigin.x(), expectedOrigin.y(),
                    expectedOrigin.z(), expectedOrigin.x() + size.x() - 1,
                    expectedOrigin.y() + size.y() - 1, expectedOrigin.z() + size.z() - 1);
            require(parsed.boundingBox().equals(expectedBounds),
                    "Ancient City BENT/LOOT transformed clipping box drift");
            ChunkCoord expectedOwner = new ChunkCoord(Math.floorDiv(expectedOrigin.x(), 16),
                    Math.floorDiv(expectedOrigin.z(), 16));
            require(parsed.ownerChunk().equals(expectedOwner),
                    "Ancient City BENT/LOOT owner chunk drift");
            validatePlacementRng(parsed.placementRng(), expectedOwner);
            require(parsed.rowsInCommandOrder().size() == targetCommands.size(),
                    "Ancient City BENT/LOOT row cardinality drift for " + template.id());

            ArrayList<CanonicalBlockEntityEvidence> placementRows = new ArrayList<>();
            int localDraw = 0;
            for (int rowIndex = 0; rowIndex < targetCommands.size(); rowIndex++) {
                Command command = targetCommands.get(rowIndex);
                BentLootRow row = parsed.rowsInCommandOrder().get(rowIndex);
                String expectedType = blockEntityType(command);
                Pos local = commandPosition(command);
                Pos placed = new Pos(expectedOrigin.x() + local.x(), expectedOrigin.y() + local.y(),
                        expectedOrigin.z() + local.z());
                require(row.commandOrdinal() == command.ordinal() && row.opcode() == command.opcode()
                                && row.blockEntityType().equals(expectedType)
                                && row.runtimeClass().equals(runtimeClassForBlockEntity(expectedType))
                                && row.localPosition().equals(local) && row.placedPosition().equals(placed),
                        "Ancient City BENT/LOOT command/type/position binding drift");
                boolean randomizable = "minecraft:chest".equals(expectedType);
                if (randomizable) {
                    require(row.callerRngEncounterOrdinal().isPresent()
                                    && row.callerRngEncounterOrdinal().getAsInt() == localDraw
                                    && row.signedSeedI64().isPresent()
                                    && localDraw < parsed.placementRng().draws().size()
                                    && row.signedSeedI64().getAsLong()
                                            == parsed.placementRng().draws().get(localDraw).signedSeedI64(),
                            "Ancient City BENT/LOOT caller RNG encounter binding drift");
                    localDraw++;
                } else {
                    require(row.callerRngEncounterOrdinal().isEmpty() && row.signedSeedI64().isEmpty(),
                            "Ancient City non-randomizable BENT claimed caller RNG draw");
                }
                if (command instanceof LootContainer container) {
                    require(row.lootTable().equals(Optional.of(container.lootTable())),
                            "Ancient City LOOT table evidence drift");
                } else {
                    require(row.lootTable().isEmpty(), "Ancient City non-LOOT row claimed loot table");
                }
                byte[] preimage = canonicalBlockEntityNbt(command, placed,
                        row.signedSeedI64().orElse(0L));
                require(preimage.length == row.canonicalNbtLength()
                                && row.canonicalNbtSha256().equals(sha256(preimage)),
                        "Ancient City canonical BENT NBT digest/preimage drift " + template.id()
                                + "#" + command.ordinal());
                CanonicalBlockEntityEvidence attached = new CanonicalBlockEntityEvidence(
                        template.id(), command.ordinal(), command.opcode(), expectedType,
                        row.runtimeClass(), local, placed, row.callerRngEncounterOrdinal(),
                        row.signedSeedI64(), row.lootTable(), preimage);
                placementRows.add(attached);
                canonical.add(attached);
                if (command instanceof LootContainer container) {
                    loot.add(new LootSeedEvidence(placementIndex, template.id(), command.ordinal(),
                            placed, container.lootTable(), row.callerRngEncounterOrdinal().orElseThrow(),
                            row.signedSeedI64().orElseThrow()));
                }
            }
            require(localDraw == parsed.placementRng().draws().size(),
                    "Ancient City BENT/LOOT placement draw closure drift");
            totalDraws += localDraw;
            placements.add(new BentLootPlacement(parsed.template(), parsed.templateOrdinal(),
                    parsed.processorList(), parsed.origin(), parsed.boundingBox(), parsed.ownerChunk(),
                    parsed.placementRng(), placementRows));
            placementIndex++;
        }
        require(placementIndex == BENT_LOOT_PLACEMENT_COUNT
                        && placementIndex == evidence.placementsInExecutionCorpusOrder().size()
                        && canonical.size() == BENT_LOOT_ROW_COUNT
                        && totalDraws == BENT_LOOT_DRAW_COUNT
                        && loot.size() == BENT_LOOT_LOOT_DRAW_COUNT,
                "Ancient City BENT/LOOT attached closure cardinality drift");
        return new Mc263AncientCityGrammar(grammar.registryPoolKeysInExecutionOrder,
                grammar.poolsInExecutionOrder, grammar.processorListsInEncounterOrder,
                grammar.processorSemanticsInEncounterOrder, grammar.configuredFeature,
                grammar.templatesInEncounterOrder, grammar.typedSidecars,
                grammar.statesInEncounterOrder, grammar.aggregate, placements, canonical, loot);
    }

    private static String processorListForTemplate(Mc263AncientCityGrammar grammar, String template) {
        String found = null;
        for (Pool pool : grammar.poolsInExecutionOrder) {
            for (PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof SingleElement single && single.template().equals(template)) {
                    found = mergeProcessorIdentity(found, single.processorList(), template);
                } else if (element instanceof ListElement list) {
                    for (SingleChild child : list.childrenInDeclaredOrder()) {
                        if (child.template().equals(template)) {
                            found = mergeProcessorIdentity(found, child.processorList(), template);
                        }
                    }
                }
            }
        }
        require(found != null, "Ancient City BENT/LOOT template processor binding absent: " + template);
        return found;
    }

    private static String mergeProcessorIdentity(String current, String next, String template) {
        require(current == null || current.equals(next),
                "Ancient City BENT/LOOT template processor ambiguity: " + template);
        return next;
    }

    private static String blockEntityType(Command command) {
        if (command instanceof LootContainer value) return value.blockEntityType();
        if (command instanceof Bent value) return value.payload().blockEntityType();
        throw invalid("Ancient City command has no block-entity type");
    }

    private static Pos commandPosition(Command command) {
        if (command instanceof LootContainer value) return value.position();
        if (command instanceof Bent value) return value.position();
        throw invalid("Ancient City command has no block-entity position");
    }

    private static String runtimeClassForBlockEntity(String type) {
        return switch (type) {
            case "minecraft:chest" -> "net.minecraft.world.level.block.entity.ChestBlockEntity";
            case "minecraft:comparator" -> "net.minecraft.world.level.block.entity.ComparatorBlockEntity";
            case "minecraft:sculk_sensor" -> "net.minecraft.world.level.block.entity.SculkSensorBlockEntity";
            case "minecraft:lectern" -> "net.minecraft.world.level.block.entity.LecternBlockEntity";
            case "minecraft:campfire" -> "net.minecraft.world.level.block.entity.CampfireBlockEntity";
            case "minecraft:skull" -> "net.minecraft.world.level.block.entity.SkullBlockEntity";
            case "minecraft:furnace" -> "net.minecraft.world.level.block.entity.FurnaceBlockEntity";
            default -> throw invalid("unknown Ancient City BENT runtime type: " + type);
        };
    }

    private static void validatePlacementRng(PlacementRng rng, ChunkCoord owner) {
        require(rng.worldSeedI64() == BENT_LOOT_WORLD_SEED && rng.chunk().equals(owner)
                        && rng.structureIndex() == BENT_LOOT_STRUCTURE_INDEX
                        && rng.generationStepOrdinal() == BENT_LOOT_GENERATION_STEP,
                "Ancient City BENT/LOOT placement RNG provenance drift");
        PlacementXoroshiro expected = PlacementXoroshiro.forFeature(BENT_LOOT_WORLD_SEED,
                Math.multiplyExact(owner.x(), 16), Math.multiplyExact(owner.z(), 16),
                BENT_LOOT_STRUCTURE_INDEX, BENT_LOOT_GENERATION_STEP);
        require(rng.decorationSeedI64() == expected.decorationSeed
                        && sameXoroshiroBody(rng.initial(), expected.state()),
                "Ancient City BENT/LOOT placement RNG initial preimage drift");
        for (int index = 0; index < rng.draws().size(); index++) {
            PlacementDraw draw = rng.draws().get(index);
            require(draw.ordinal() == index && sameXoroshiroBody(draw.before(), expected.state()),
                    "Ancient City BENT/LOOT placement RNG before-state drift");
            long value = expected.nextLong();
            require(draw.signedSeedI64() == value && sameXoroshiroBody(draw.after(), expected.state()),
                    "Ancient City BENT/LOOT placement RNG draw/after-state drift");
        }
        require(sameXoroshiroBody(rng.finished(), expected.state())
                        && rng.finished().continuationNextLongI64().equals(expected.rawContinuation()),
                "Ancient City BENT/LOOT placement RNG continuation drift");
    }

    private static boolean sameXoroshiroBody(XoroshiroState left, XoroshiroState right) {
        return left.worldgenCount() == right.worldgenCount()
                && left.seedLoI64() == right.seedLoI64() && left.seedHiI64() == right.seedHiI64();
    }

    static byte[] canonicalBlockEntityNbt(Command command, Pos position, long lootSeed) {
        return Mc263StructureBlockEntityNbtAuthority.render(position.x(), position.y(),
                position.z(), canonicalFacts(command, lootSeed));
    }

    /**
     * Maps one authenticated Ancient City template command onto the family-agnostic canonical
     * block-entity facts owned by {@link Mc263StructureBlockEntityNbtAuthority}. The tag program
     * and its field order live there, shared with every other generated family.
     */
    private static Mc263StructureBlockEntityNbtAuthority.Facts canonicalFacts(
            Command command, long lootSeed) {
        if (command instanceof LootContainer loot) {
            return new Mc263StructureBlockEntityNbtAuthority.LootContainer(
                    loot.blockEntityType(), loot.lootTable(), lootSeed);
        }
        if (!(command instanceof Bent bent)) {
            throw invalid("unsupported Ancient City canonical block-entity command");
        }
        BentPayload payload = bent.payload();
        if (payload instanceof ComparatorOutput value) {
            return new Mc263StructureBlockEntityNbtAuthority.Comparator(
                    value.blockEntityType(), value.outputSignal());
        }
        if (payload instanceof EmptySculkSensor value) {
            return new Mc263StructureBlockEntityNbtAuthority.SculkSensor(value.blockEntityType());
        }
        if (payload instanceof EmptyLectern value) {
            return new Mc263StructureBlockEntityNbtAuthority.Positioned(value.blockEntityType());
        }
        if (payload instanceof EmptySkeletonSkull value) {
            return new Mc263StructureBlockEntityNbtAuthority.Positioned(value.blockEntityType());
        }
        if (payload instanceof EmptyCampfire value) {
            return new Mc263StructureBlockEntityNbtAuthority.Campfire(value.blockEntityType(),
                    List.of(), ints(value.cookingTimes()), ints(value.cookingTotalTimes()));
        }
        if (payload instanceof FixedChestContents value) {
            return new Mc263StructureBlockEntityNbtAuthority.ItemContainer(
                    value.blockEntityType(), stacks(value.items()));
        }
        if (payload instanceof FurnacePayload value) {
            java.util.LinkedHashMap<String, Integer> recipes = new java.util.LinkedHashMap<>();
            for (RecipeUse recipe : value.recipesUsed()) {
                recipes.put(recipe.recipe(), recipe.count());
            }
            return new Mc263StructureBlockEntityNbtAuthority.Furnace(value.blockEntityType(),
                    stacks(value.items()), value.litTotalTime(), value.litTimeRemaining(),
                    value.cookingTimeSpent(), value.cookingTotalTime(), recipes);
        }
        throw invalid("unsupported Ancient City canonical BENT semantic");
    }

    private static List<Mc263StructureBlockEntityNbtAuthority.ItemStack> stacks(
            List<ItemStack> items) {
        ArrayList<Mc263StructureBlockEntityNbtAuthority.ItemStack> stacks =
                new ArrayList<>(items.size());
        for (ItemStack item : items) {
            stacks.add(new Mc263StructureBlockEntityNbtAuthority.ItemStack(
                    item.slot(), item.item(), item.count()));
        }
        return stacks;
    }

    private static int[] ints(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < result.length; index++) result[index] = values.get(index);
        return result;
    }


    private static final class PlacementXoroshiro {
        private static final long SILVER = 0x6a09e667f3bcc909L;
        private static final long GOLDEN = 0x9e3779b97f4a7c15L;
        private long lo;
        private long hi;
        private int count;
        private long decorationSeed;

        private PlacementXoroshiro(long lo, long hi, int count) {
            this.lo = lo; this.hi = hi; this.count = count;
            if ((lo | hi) == 0L) { this.lo = GOLDEN; this.hi = SILVER; }
        }
        private static PlacementXoroshiro seeded(long seed) {
            long lo = seed ^ SILVER;
            return new PlacementXoroshiro(mix(lo), mix(lo + GOLDEN), 0);
        }
        private static PlacementXoroshiro forFeature(
                long seed, int x, int z, int structureIndex, int step) {
            PlacementXoroshiro random = seeded(seed);
            long xScale = random.nextLong() | 1L;
            long zScale = random.nextLong() | 1L;
            long decoration = (long) x * xScale + (long) z * zScale ^ seed;
            int count = random.count;
            random = seeded(decoration + structureIndex + (long) step * 10_000L);
            random.count = count;
            random.decorationSeed = decoration;
            return random;
        }
        private static long mix(long value) {
            value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
            value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
            return value ^ value >>> 31;
        }
        private long nextSourceLong() {
            long s0 = lo, s1 = hi;
            long result = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            lo = Long.rotateLeft(s0, 49) ^ s1 ^ s1 << 21;
            hi = Long.rotateLeft(s1, 28);
            return result;
        }
        private int next(int bits) { count++; return (int) (nextSourceLong() >>> (64 - bits)); }
        private long nextLong() { return ((long) next(32) << 32) + next(32); }
        private XoroshiroState state() { return new XoroshiroState(count, lo, hi, List.of()); }
        private List<Long> rawContinuation() {
            PlacementXoroshiro copy = new PlacementXoroshiro(lo, hi, count);
            ArrayList<Long> result = new ArrayList<>();
            for (int index = 0; index < 8; index++) result.add(copy.nextSourceLong());
            return List.copyOf(result);
        }
    }

    private static Mc263AncientCityGrammar parseEvidence(byte[] bytes) {
        require(bytes != null, "Ancient City grammar bytes are null");
        require(bytes.length <= RESOURCE_BYTES + 4_096,
                "Ancient City grammar parser input exceeds bound");
        JsonReader reader = new JsonReader(bytes);
        reader.beginObject();
        reader.field("schema", 0);
        require(reader.readInt() == 1, "Ancient City schema drift");
        reader.field("receiptId", 1);
        require("ANC-E2A".equals(reader.readString()), "Ancient City receipt drift");
        reader.field("javaVersion", 2);
        require("25.0.1+8-LTS".equals(reader.readString()), "Ancient City Java receipt drift");
        reader.field("serverVersion", 3);
        require("26.3 Snapshot 7".equals(reader.readString()), "Ancient City server receipt drift");
        reader.field("evidence", 4);
        int evidenceStart = reader.mark();
        ParsedEvidence parsed = parseEvidenceBody(reader);
        int evidenceEnd = reader.mark();
        reader.field("evidenceBodySha256", 5);
        String evidenceHash = reader.readString();
        require(EVIDENCE_SHA256.equals(evidenceHash), "Ancient City evidence SHA-256 receipt drift");
        require(evidenceHash.equals(reader.rawSha256(evidenceStart, evidenceEnd)),
                "Ancient City evidence body hash mismatch");
        reader.endObject();
        reader.finish();

        validateCrossClosure(parsed);
        return new Mc263AncientCityGrammar(
                parsed.poolKeys(), parsed.pools(), parsed.processorLists(), parsed.processors(),
                parsed.configuredFeature(), parsed.templates(), parsed.sidecars(), parsed.states(),
                parsed.aggregate(), List.of(), List.of(), List.of());
    }

    private static ParsedEvidence parseEvidenceBody(JsonReader reader) {
        reader.beginObject();
        reader.field("artifactIdentity", 0);
        parseArtifactIdentity(reader);
        reader.field("structureSet", 1);
        parseStructureSet(reader);
        reader.field("structure", 2);
        parseStructure(reader);
        reader.field("registryPoolKeysInExecutionOrder", 3);
        List<String> poolKeys = readStringList(reader);
        require(poolKeys.equals(POOL_KEYS), "Ancient City registry pool order drift");

        reader.field("poolsInExecutionOrder", 4);
        int poolsStart = reader.mark();
        List<Pool> pools = parsePools(reader);
        int poolsEnd = reader.mark();

        reader.field("processorListsInEncounterOrder", 5);
        int processorListsStart = reader.mark();
        List<ProcessorListSpec> processorLists = parseProcessorLists(reader);
        int processorListsEnd = reader.mark();

        reader.field("processorSemanticsInEncounterOrder", 6);
        int processorsStart = reader.mark();
        List<ProcessorSpec> processors = parseProcessorSemantics(reader);
        int processorsEnd = reader.mark();

        reader.field("configuredFeature", 7);
        int configuredStart = reader.mark();
        ConfiguredFeature configuredFeature = parseConfiguredFeature(reader);
        int configuredEnd = reader.mark();

        reader.field("templatesInEncounterOrder", 8);
        int templatesStart = reader.mark();
        List<Template> templates = parseTemplates(reader);
        int templatesEnd = reader.mark();

        reader.field("typedSidecars", 9);
        int sidecarsStart = reader.mark();
        TypedSidecars sidecars = parseSidecars(reader);
        int sidecarsEnd = reader.mark();

        reader.field("stateClosure", 10);
        int stateClosureStart = reader.mark();
        List<String> states = parseStateClosure(reader);
        int stateClosureEnd = reader.mark();

        reader.field("aggregate", 11);
        Aggregate aggregate = parseAggregate(reader);
        reader.field("assetBoundary", 12);
        parseAssetBoundary(reader);
        reader.endObject();

        require(aggregate.poolsSha256().equals(reader.rawSha256(poolsStart, poolsEnd)),
                "Ancient City pool aggregate hash mismatch");
        require(aggregate.processorListsSha256().equals(
                        reader.rawSha256(processorListsStart, processorListsEnd)),
                "Ancient City processor-list aggregate hash mismatch");
        require(aggregate.processorSemanticsSha256().equals(
                        reader.rawSha256(processorsStart, processorsEnd)),
                "Ancient City processor semantic aggregate hash mismatch");
        require(aggregate.configuredFeatureSha256().equals(
                        reader.rawSha256(configuredStart, configuredEnd)),
                "Ancient City configured-feature aggregate hash mismatch");
        require(aggregate.templatesSha256().equals(reader.rawSha256(templatesStart, templatesEnd)),
                "Ancient City template aggregate hash mismatch");
        require(aggregate.sidecarsSha256().equals(reader.rawSha256(sidecarsStart, sidecarsEnd)),
                "Ancient City sidecar aggregate hash mismatch");
        require(aggregate.stateClosureSha256().equals(
                        reader.rawSha256(stateClosureStart, stateClosureEnd)),
                "Ancient City state-closure aggregate hash mismatch");

        return new ParsedEvidence(poolKeys, pools, processorLists, processors, configuredFeature,
                templates, sidecars, states, aggregate);
    }

    private static void parseArtifactIdentity(JsonReader reader) {
        reader.beginObject();
        reader.field("classManifestSha256", 0);
        require("b82ca37f4259712ef3436d7e8fa25b2c789f9f517e439554af2e9a3a212c9a60"
                        .equals(reader.readString()),
                "Ancient City class manifest drift");
        reader.field("classEntryCount", 1);
        require(reader.readInt() == 27, "Ancient City class manifest cardinality drift");
        reader.field("sourceManifestSha256", 2);
        require("c0a32d7bf7390d0b873c2f76a68a5ec4934d2716031d2041491e78c9224fb01a"
                        .equals(reader.readString()),
                "Ancient City source manifest drift");
        reader.field("sourceEntryCount", 3);
        require(reader.readInt() == 74, "Ancient City source manifest cardinality drift");
        reader.field("expectedAbsentSource", 4);
        require("data/minecraft/structure/ancient_city/walls/intact_horizontal_wall_stairs_5.nbt"
                        .equals(reader.readString()),
                "Ancient City expected-absent source drift");
        reader.endObject();
    }

    private static void parseStructureSet(JsonReader reader) {
        reader.beginObject();
        reader.field("key", 0);
        require("minecraft:ancient_cities".equals(reader.readString()),
                "Ancient City structure-set identity drift");
        reader.field("entries", 1);
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City structure-set entry absent");
        reader.beginObject();
        reader.field("structureKey", 0);
        require("minecraft:ancient_city".equals(reader.readString()),
                "Ancient City structure-set structure drift");
        reader.field("weight", 1);
        require(reader.readInt() == 1, "Ancient City structure-set weight drift");
        reader.endObject();
        require(!reader.nextArrayValue(1), "Ancient City structure-set entry cardinality drift");
        reader.endArray();
        reader.field("placementType", 2);
        require("minecraft:random_spread".equals(reader.readString()),
                "Ancient City placement type drift");
        reader.field("spacing", 3);
        require(reader.readInt() == 24, "Ancient City spacing drift");
        reader.field("separation", 4);
        require(reader.readInt() == 8, "Ancient City separation drift");
        reader.field("spreadType", 5);
        require("LINEAR".equals(reader.readString()), "Ancient City spread type drift");
        reader.field("salt", 6);
        require(reader.readInt() == 20_083_232, "Ancient City structure salt drift");
        reader.field("frequency", 7);
        require(Double.compare(reader.readDouble(), 1.0D) == 0, "Ancient City frequency drift");
        reader.field("frequencyReductionMethod", 8);
        require("DEFAULT".equals(reader.readString()), "Ancient City frequency method drift");
        reader.field("locateOffset", 9);
        require(readPos(reader).equals(new Pos(0, 0, 0)), "Ancient City locate offset drift");
        reader.field("hasExclusionZone", 10);
        require(!reader.readBoolean(), "Ancient City exclusion-zone drift");
        reader.endObject();
    }

    private static void parseStructure(JsonReader reader) {
        reader.beginObject();
        reader.field("key", 0);
        require("minecraft:ancient_city".equals(reader.readString()),
                "Ancient City structure identity drift");
        reader.field("orderedCodec", 1);
        int codecStart = reader.mark();
        reader.skipValue();
        int codecEnd = reader.mark();
        reader.field("orderedCodecSha256", 2);
        String codecHash = reader.readString();
        require("6b97a44d56f119172e26e65d0fc11b58da89e03baee10fa681bcf3ab66a03dd8"
                        .equals(codecHash),
                "Ancient City structure codec receipt drift");
        require(codecHash.equals(reader.rawSha256(codecStart, codecEnd)),
                "Ancient City structure codec hash mismatch");
        reader.field("startJigsawName", 3);
        require("minecraft:city_anchor".equals(reader.readString()),
                "Ancient City start jigsaw drift");
        reader.field("maxDistance", 4);
        reader.beginObject();
        reader.field("horizontal", 0);
        require(reader.readInt() == 116, "Ancient City horizontal distance drift");
        reader.field("vertical", 1);
        require(reader.readInt() == 116, "Ancient City vertical distance drift");
        reader.endObject();
        reader.field("defaultLiquidSettings", 5);
        require("apply_waterlogging".equals(reader.readString()),
                "Ancient City liquid settings drift");
        reader.endObject();
    }

    private static List<Pool> parsePools(JsonReader reader) {
        ArrayList<Pool> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < POOL_KEYS.size(), "Ancient City pool cardinality overflow");
            result.add(parsePool(reader, POOL_KEYS.get(index), index));
            index++;
        }
        reader.endArray();
        require(index == POOL_KEYS.size(), "Ancient City pool cardinality drift");
        return List.copyOf(result);
    }

    private static Pool parsePool(JsonReader reader, String expectedKey, int poolIndex) {
        reader.beginObject();
        reader.field("key", 0);
        String key = reader.readString();
        require(expectedKey.equals(key), "Ancient City pool encounter order drift");
        reader.field("fallback", 1);
        require("minecraft:empty".equals(reader.readString()), "Ancient City pool fallback drift");
        reader.field("rawElementCount", 2);
        int rawCount = reader.readInt();
        reader.field("expandedWeight", 3);
        int expandedWeight = reader.readInt();
        reader.field("elementsInDeclaredOrder", 4);
        int elementsStart = reader.mark();
        List<PoolElement> elements = parsePoolElements(reader);
        int elementsEnd = reader.mark();
        reader.field("elementsSha256", 5);
        String elementsHash = reader.readString();
        require(isSha256(elementsHash), "Ancient City pool element hash type drift");
        require(elementsHash.equals(reader.rawSha256(elementsStart, elementsEnd)),
                "Ancient City pool element hash mismatch");
        reader.endObject();

        require(rawCount == elements.size(), "Ancient City raw pool element count drift");
        int weight = 0;
        int single = 0;
        int list = 0;
        int feature = 0;
        int empty = 0;
        for (int ordinal = 0; ordinal < elements.size(); ordinal++) {
            PoolElement element = elements.get(ordinal);
            require(element.ordinal() == ordinal, "Ancient City pool element ordinal drift");
            require(element.weight() > 0, "Ancient City pool element weight drift");
            weight = Math.addExact(weight, element.weight());
            if (element instanceof SingleElement) single++;
            else if (element instanceof ListElement) list++;
            else if (element instanceof FeatureElement) feature++;
            else if (element instanceof EmptyElement) empty++;
        }
        require(weight == expandedWeight, "Ancient City expanded pool weight drift");
        validatePoolShape(poolIndex, rawCount, expandedWeight, single, list, feature, empty);
        return new Pool(key, rawCount, expandedWeight, elements);
    }

    private static void validatePoolShape(int index, int raw, int weight,
            int single, int list, int feature, int empty) {
        boolean valid = switch (index) {
            case 0 -> raw == 3 && weight == 3 && single == 3 && list == 0 && feature == 0 && empty == 0;
            case 1 -> raw == 20 && weight == 46 && single == 17 && list == 2 && feature == 0 && empty == 1;
            case 2 -> raw == 2 && weight == 7 && single == 0 && list == 0 && feature == 1 && empty == 1;
            case 3 -> raw == 16 && weight == 27 && single == 16 && list == 0 && feature == 0 && empty == 0;
            case 4 -> raw == 8 && weight == 8 && single == 8 && list == 0 && feature == 0 && empty == 0;
            case 5 -> raw == 10 && weight == 10 && single == 10 && list == 0 && feature == 0 && empty == 0;
            case 6 -> raw == 6 && weight == 6 && single == 6 && list == 0 && feature == 0 && empty == 0;
            default -> false;
        };
        require(valid, "Ancient City weighted pool shape drift at index " + index);
    }

    private static List<PoolElement> parsePoolElements(JsonReader reader) {
        ArrayList<PoolElement> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            result.add(parsePoolElement(reader));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static PoolElement parsePoolElement(JsonReader reader) {
        reader.beginObject();
        reader.field("ordinal", 0);
        int ordinal = reader.readInt();
        reader.field("weight", 1);
        int weight = reader.readInt();
        reader.field("projection", 2);
        String projection = reader.readString();
        reader.field("kind", 3);
        String kind = reader.readString();
        PoolElement result;
        switch (kind) {
            case "single" -> {
                require("rigid".equals(projection), "Ancient City single projection drift");
                reader.field("template", 4);
                String template = requireTemplateIdentity(reader.readString());
                reader.field("processorList", 5);
                String processorList = requireProcessorListIdentity(reader.readString());
                reader.field("placementProcessorsInOrder", 6);
                List<String> placement = readStringList(reader);
                validatePlacementPipeline(processorList, placement);
                result = new SingleElement(ordinal, weight, template, processorList, placement);
            }
            case "list" -> {
                require("rigid".equals(projection), "Ancient City list projection drift");
                reader.field("childrenInDeclaredOrder", 4);
                List<SingleChild> children = parseSingleChildren(reader);
                require(!children.isEmpty(), "Ancient City list pool element is empty");
                result = new ListElement(ordinal, weight, children);
            }
            case "feature" -> {
                require("rigid".equals(projection), "Ancient City feature projection drift");
                reader.field("feature", 4);
                String feature = reader.readString();
                require(SCULK_FEATURE.equals(feature), "unknown Ancient City configured feature identity");
                result = new FeatureElement(ordinal, weight, feature);
            }
            case "empty" -> {
                require("terrain_matching".equals(projection),
                        "Ancient City empty projection drift");
                result = new EmptyElement(ordinal, weight);
            }
            default -> throw invalid("unknown Ancient City pool element kind: " + kind);
        }
        reader.endObject();
        return result;
    }

    private static List<SingleChild> parseSingleChildren(JsonReader reader) {
        ArrayList<SingleChild> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("ordinal", 0);
            int ordinal = reader.readInt();
            require(ordinal == index, "Ancient City list-child ordinal drift");
            reader.field("projection", 1);
            require("rigid".equals(reader.readString()), "Ancient City list-child projection drift");
            reader.field("kind", 2);
            require("single".equals(reader.readString()), "Ancient City list-child kind drift");
            reader.field("template", 3);
            String template = requireTemplateIdentity(reader.readString());
            reader.field("processorList", 4);
            String processorList = requireProcessorListIdentity(reader.readString());
            reader.field("placementProcessorsInOrder", 5);
            List<String> placement = readStringList(reader);
            validatePlacementPipeline(processorList, placement);
            reader.endObject();
            result.add(new SingleChild(ordinal, template, processorList, placement));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static List<ProcessorListSpec> parseProcessorLists(JsonReader reader) {
        reader.beginObject();
        reader.field("rows", 0);
        int rowsStart = reader.mark();
        ArrayList<ProcessorListSpec> rows = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < PROCESSOR_LIST_IDENTITIES.size(),
                    "Ancient City processor-list cardinality overflow");
            rows.add(parseProcessorList(reader, index));
            index++;
        }
        reader.endArray();
        int rowsEnd = reader.mark();
        require(index == PROCESSOR_LIST_IDENTITIES.size(),
                "Ancient City processor-list cardinality drift");
        reader.field("orderSha256", 1);
        String orderHash = reader.readString();
        require("20deaccdddd374a4ae4328a6b576d7ffa92ad81a1704bb587cacd47feaa249a2"
                        .equals(orderHash),
                "Ancient City processor-list order receipt drift");
        require(orderHash.equals(reader.rawSha256(rowsStart, rowsEnd)),
                "Ancient City processor-list order hash mismatch");
        reader.endObject();
        return List.copyOf(rows);
    }

    private static ProcessorListSpec parseProcessorList(JsonReader reader, int index) {
        String expectedIdentity = PROCESSOR_LIST_IDENTITIES.get(index);
        reader.beginObject();
        reader.field("identity", 0);
        String identity = reader.readString();
        require(expectedIdentity.equals(identity), "Ancient City processor-list encounter order drift");
        reader.field("registered", 1);
        boolean registered = reader.readBoolean();
        require(registered == !"inline".equals(identity),
                "Ancient City processor-list registration drift");
        reader.field("orderedCodec", 2);
        int codecStart = reader.mark();
        reader.skipValue();
        int codecEnd = reader.mark();
        reader.field("orderedCodecSha256", 3);
        String codecHash = reader.readString();
        require(expectedProcessorListCodecHash(index).equals(codecHash),
                "Ancient City processor-list codec receipt drift");
        require(codecHash.equals(reader.rawSha256(codecStart, codecEnd)),
                "Ancient City processor-list codec hash mismatch");
        reader.field("processorTypesInOrder", 4);
        List<String> processorTypes = readStringList(reader);
        reader.field("processorSemanticIdentitiesInOrder", 5);
        List<String> semanticIds = readStringList(reader);
        reader.endObject();
        require(processorTypes.equals(expectedProcessorTypes(index)),
                "Ancient City processor-list signature drift");
        require(semanticIds.equals(expectedProcessorSemanticIds(index)),
                "Ancient City processor semantic binding drift");
        return new ProcessorListSpec(identity, registered, processorTypes, semanticIds);
    }

    private static String expectedProcessorListCodecHash(int index) {
        return switch (index) {
            case 0 -> "a6c6ebb9656d84c285e66a60f2b0a7a5ca48f08659dc330b26d36ab78aca293c";
            case 1 -> "39784193f1344ee5c81aa2e3d9f7ed2be36d59015a378f5553f89e6449f42a6f";
            case 2 -> "80732fe8d264c25bcb13bdb27cec96f0080600d6ac1c92b90d5fefea8332e151";
            case 3 -> "daba533db0221bce39be8d761c17b4a5aa37705ec880ac591e82941cd4df885c";
            default -> throw invalid("unknown Ancient City processor-list index");
        };
    }

    private static List<String> expectedProcessorTypes(int index) {
        return switch (index) {
            case 0 -> List.of("minecraft:rule", "minecraft:protected_blocks");
            case 1, 3 -> List.of("minecraft:block_rot", "minecraft:rule",
                    "minecraft:protected_blocks");
            case 2 -> List.of();
            default -> throw invalid("unknown Ancient City processor-list index");
        };
    }

    private static List<String> expectedProcessorSemanticIds(int index) {
        return switch (index) {
            case 0 -> List.of(PROCESSOR_SEMANTIC_IDENTITIES.get(0),
                    PROCESSOR_SEMANTIC_IDENTITIES.get(1));
            case 1 -> List.of(PROCESSOR_SEMANTIC_IDENTITIES.get(4),
                    PROCESSOR_SEMANTIC_IDENTITIES.get(0), PROCESSOR_SEMANTIC_IDENTITIES.get(1));
            case 2 -> List.of();
            case 3 -> List.of(PROCESSOR_SEMANTIC_IDENTITIES.get(4),
                    PROCESSOR_SEMANTIC_IDENTITIES.get(5), PROCESSOR_SEMANTIC_IDENTITIES.get(1));
            default -> throw invalid("unknown Ancient City processor-list index");
        };
    }

    private static List<ProcessorSpec> parseProcessorSemantics(JsonReader reader) {
        reader.beginObject();
        reader.field("rows", 0);
        int rowsStart = reader.mark();
        ArrayList<ProcessorSpec> rows = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < PROCESSOR_SEMANTIC_IDENTITIES.size(),
                    "Ancient City processor semantic cardinality overflow");
            rows.add(parseProcessorSemantic(reader, index));
            index++;
        }
        reader.endArray();
        int rowsEnd = reader.mark();
        require(index == PROCESSOR_SEMANTIC_IDENTITIES.size(),
                "Ancient City processor semantic cardinality drift");
        reader.field("orderSha256", 1);
        String orderHash = reader.readString();
        require("2352b68dc0e7d70a891061d54c2b014134a49b15356ca3dce733b7273ad10347"
                        .equals(orderHash),
                "Ancient City processor semantic order receipt drift");
        require(orderHash.equals(reader.rawSha256(rowsStart, rowsEnd)),
                "Ancient City processor semantic order hash mismatch");
        reader.endObject();
        return List.copyOf(rows);
    }

    private static ProcessorSpec parseProcessorSemantic(JsonReader reader, int index) {
        String expectedIdentity = PROCESSOR_SEMANTIC_IDENTITIES.get(index);
        ProcessorKind expectedKind = switch (index) {
            case 0, 5 -> ProcessorKind.RULE;
            case 1 -> ProcessorKind.PROTECTED_BLOCKS;
            case 2 -> ProcessorKind.BLOCK_IGNORE;
            case 3 -> ProcessorKind.JIGSAW_REPLACEMENT;
            case 4 -> ProcessorKind.BLOCK_ROT;
            default -> throw invalid("unknown Ancient City processor semantic index");
        };
        reader.beginObject();
        reader.field("identity", 0);
        String identity = reader.readString();
        require(expectedIdentity.equals(identity), "unknown Ancient City processor identity");
        reader.field("type", 1);
        String type = reader.readString();
        require(expectedKind.wireName.equals(type), "Ancient City processor type drift");
        reader.field("runtimeClass", 2);
        String runtimeClass = reader.readString();
        require(expectedKind.runtimeClass.equals(runtimeClass),
                "Ancient City processor runtime class drift");
        reader.field("orderedCodec", 3);
        int codecStart = reader.mark();
        ProcessorCodec codec = parseProcessorCodec(reader, expectedKind);
        int codecEnd = reader.mark();
        reader.field("orderedCodecSha256", 4);
        String codecHash = reader.readString();
        require(identity.equals(type + ":" + codecHash),
                "Ancient City processor semantic identity/hash drift");
        require(codecHash.equals(reader.rawSha256(codecStart, codecEnd)),
                "Ancient City processor codec hash mismatch");
        reader.field("rng", 5);
        ProcessorRng rng = parseProcessorRng(reader, expectedKind, codec);
        reader.endObject();
        return new ProcessorSpec(identity, expectedKind, runtimeClass, codec, rng);
    }

    private static ProcessorCodec parseProcessorCodec(JsonReader reader, ProcessorKind kind) {
        reader.beginObject();
        ProcessorCodec result = null;
        switch (kind) {
            case RULE -> {
                reader.field("rules", 0);
                List<Rule> rules = parseRules(reader);
                reader.field("processor_type", 1);
                require(kind.wireName.equals(reader.readString()), "Ancient City rule codec type drift");
                result = new RuleCodec(rules);
            }
            case PROTECTED_BLOCKS -> {
                reader.field("value", 0);
                String tag = reader.readString();
                require("#minecraft:features_cannot_replace".equals(tag),
                        "Ancient City protected-block tag drift");
                reader.field("processor_type", 1);
                require(kind.wireName.equals(reader.readString()),
                        "Ancient City protected-block codec type drift");
                result = new ProtectedBlocksCodec(tag);
            }
            case BLOCK_IGNORE -> {
                reader.field("blocks", 0);
                List<String> blocks = readStringList(reader);
                require(blocks.equals(List.of("minecraft:structure_block")),
                        "Ancient City block-ignore closure drift");
                reader.field("processor_type", 1);
                require(kind.wireName.equals(reader.readString()),
                        "Ancient City block-ignore codec type drift");
                result = new BlockIgnoreCodec(blocks);
            }
            case JIGSAW_REPLACEMENT -> {
                reader.field("processor_type", 0);
                require(kind.wireName.equals(reader.readString()),
                        "Ancient City jigsaw-replacement codec drift");
                result = new JigsawReplacementCodec();
            }
            case BLOCK_ROT -> {
                reader.field("rottable_blocks", 0);
                String tag = reader.readString();
                require("#minecraft:ancient_city_replaceable".equals(tag),
                        "Ancient City block-rot tag drift");
                reader.field("integrity", 1);
                double integrity = reader.readDouble();
                require(Double.compare(integrity, 0.95D) == 0,
                        "Ancient City block-rot integrity drift");
                reader.field("processor_type", 2);
                require(kind.wireName.equals(reader.readString()),
                        "Ancient City block-rot codec type drift");
                result = new BlockRotCodec(tag,
                        Mc263AncientCityProductionAuthority.rottableBlocks(), integrity);
            }
        }
        reader.endObject();
        require(result != null, "Ancient City processor codec dispatch drift");
        return result;
    }

    private static List<Rule> parseRules(JsonReader reader) {
        ArrayList<Rule> rules = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("input_predicate", 0);
            reader.beginObject();
            reader.field("block", 0);
            String block = reader.readString();
            reader.field("probability", 1);
            double probability = reader.readDouble();
            require(probability >= 0.0D && probability <= 1.0D,
                    "Ancient City rule probability out of range");
            reader.field("predicate_type", 2);
            String inputType = reader.readString();
            require("minecraft:random_block_match".equals(inputType),
                    "unknown Ancient City rule input predicate");
            reader.endObject();
            reader.field("location_predicate", 1);
            reader.beginObject();
            reader.field("predicate_type", 0);
            String locationType = reader.readString();
            require("minecraft:always_true".equals(locationType),
                    "unknown Ancient City rule location predicate");
            reader.endObject();
            reader.field("output_state", 2);
            String outputState = reader.readString();
            reader.endObject();
            rules.add(new Rule(block, probability, inputType, locationType, outputState));
            index++;
        }
        reader.endArray();
        require(rules.size() == 3 || rules.size() == 4,
                "Ancient City rule cardinality drift");
        return List.copyOf(rules);
    }

    private static ProcessorRng parseProcessorRng(
            JsonReader reader, ProcessorKind kind, ProcessorCodec codec) {
        return switch (kind) {
            case BLOCK_ROT -> parseBlockRotRng(reader, (BlockRotCodec) codec);
            case RULE -> parseRuleRng(reader, (RuleCodec) codec);
            default -> {
                reader.beginObject();
                reader.field("kind", 0);
                require("none".equals(reader.readString()),
                        "Ancient City non-random processor RNG drift");
                reader.endObject();
                yield new NoneRng();
            }
        };
    }

    private static BlockRotRng parseBlockRotRng(JsonReader reader, BlockRotCodec codec) {
        reader.beginObject();
        reader.field("kind", 0);
        require("position_seeded_next_float".equals(reader.readString()),
                "Ancient City block-rot RNG kind drift");
        reader.field("rottableBlocks", 1);
        require(codec.rottableBlocks().equals(reader.readString()),
                "Ancient City block-rot RNG tag drift");
        reader.field("integrity", 2);
        double integrity = reader.readDouble();
        require(Double.compare(integrity, codec.integrity()) == 0,
                "Ancient City block-rot RNG integrity drift");
        PositionSeed seed = parseInlinePositionSeed(reader, 3);
        reader.field("randomSource", 8);
        require("RandomSource.create(positionSeed)".equals(reader.readString()),
                "Ancient City block-rot random source drift");
        reader.field("draw", 9);
        require("nextFloat".equals(reader.readString()), "Ancient City block-rot draw drift");
        reader.field("keepWhen", 10);
        require("draw <= integrity".equals(reader.readString()),
                "Ancient City block-rot threshold drift");
        reader.endObject();
        return new BlockRotRng(codec.rottableBlocks(), integrity, seed,
                "RandomSource.create(positionSeed)", "nextFloat", "draw <= integrity");
    }

    private static PositionSeed parseInlinePositionSeed(JsonReader reader, int firstFieldIndex) {
        reader.field("xMultiplierI32", firstFieldIndex);
        int x = reader.readInt();
        reader.field("zMultiplierI64", firstFieldIndex + 1);
        long z = reader.readLong();
        reader.field("squareMultiplierI64", firstFieldIndex + 2);
        long square = reader.readLong();
        reader.field("linearMultiplierI64", firstFieldIndex + 3);
        long linear = reader.readLong();
        reader.field("arithmeticRightShift", firstFieldIndex + 4);
        int shift = reader.readInt();
        PositionSeed seed = new PositionSeed(x, z, square, linear, shift);
        require(seed.equals(new PositionSeed(3_129_871, 116_129_781L, 42_317_861L, 11L, 16)),
                "Ancient City position-seed arithmetic drift");
        return seed;
    }

    private static RuleRng parseRuleRng(JsonReader reader, RuleCodec codec) {
        reader.beginObject();
        reader.field("kind", 0);
        require("position_seeded_rule_sequence".equals(reader.readString()),
                "Ancient City rule RNG kind drift");
        reader.field("predicatesInCodecOrder", 1);
        List<PredicateDraw> predicates = parsePredicateDraws(reader, true);
        reader.field("randomPredicatesInCodecOrder", 2);
        List<PredicateDraw> randomPredicates = parsePredicateDraws(reader, false);
        reader.field("ruleOrder", 3);
        String ruleOrder = reader.readString();
        require("codec order; first matching rule wins".equals(ruleOrder),
                "Ancient City rule order drift");
        reader.field("randomSource", 4);
        String randomSource = reader.readString();
        require("StructurePlaceSettings.getRandom(transformedBlockPosition)".equals(randomSource),
                "Ancient City rule random source drift");
        reader.field("unsetSettingsRandom", 5);
        String unsetRandom = reader.readString();
        require("RandomSource.create(Mth.getSeed(transformedBlockPosition))".equals(unsetRandom),
                "Ancient City rule unset RNG drift");
        reader.field("randomPredicateDraw", 6);
        require("nextFloat".equals(reader.readString()), "Ancient City rule draw drift");
        reader.field("randomPredicatePass", 7);
        require("input precondition matches && draw < probability".equals(reader.readString()),
                "Ancient City rule pass predicate drift");
        reader.field("positionSeed", 8);
        reader.beginObject();
        PositionSeed seed = parseInlinePositionSeed(reader, 0);
        reader.endObject();
        reader.endObject();

        ArrayList<PredicateDraw> expectedAll = new ArrayList<>();
        ArrayList<PredicateDraw> expectedRandom = new ArrayList<>();
        for (Rule rule : codec.rules()) {
            PredicateDraw random = new PredicateDraw(rule.inputPredicateType(), rule.probability());
            expectedAll.add(random);
            expectedRandom.add(random);
            expectedAll.add(new PredicateDraw(rule.locationPredicateType(), Optional.empty()));
        }
        require(predicates.equals(expectedAll), "Ancient City rule predicate order drift");
        require(randomPredicates.equals(expectedRandom),
                "Ancient City random predicate order drift");
        return new RuleRng(predicates, randomPredicates, ruleOrder, randomSource, unsetRandom, seed);
    }

    private static List<PredicateDraw> parsePredicateDraws(JsonReader reader, boolean allowNonRandom) {
        ArrayList<PredicateDraw> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("predicateType", 0);
            String type = reader.readString();
            if ("minecraft:random_block_match".equals(type)) {
                reader.field("probability", 1);
                double probability = reader.readDouble();
                require(probability >= 0.0D && probability <= 1.0D,
                        "Ancient City predicate probability out of range");
                result.add(new PredicateDraw(type, Optional.of(probability)));
            } else {
                require(allowNonRandom && "minecraft:always_true".equals(type),
                        "unknown Ancient City RNG predicate identity");
                result.add(new PredicateDraw(type, Optional.empty()));
            }
            reader.endObject();
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static ConfiguredFeature parseConfiguredFeature(JsonReader reader) {
        reader.beginObject();
        reader.field("registryKey", 0);
        require(SCULK_FEATURE.equals(reader.readString()),
                "unknown Ancient City configured feature registry identity");
        reader.field("holderKind", 1);
        require("minecraft:placed_feature".equals(reader.readString()),
                "Ancient City placed feature holder kind drift");
        reader.field("placedOrderedCodec", 2);
        int placedStart = reader.mark();
        reader.beginObject();
        reader.field("feature", 0);
        require(SCULK_FEATURE.equals(reader.readString()),
                "Ancient City placed feature target drift");
        reader.field("placement", 1);
        requireEmptyArray(reader, "Ancient City placement modifier drift");
        reader.endObject();
        int placedEnd = reader.mark();
        reader.field("placedOrderedCodecSha256", 3);
        String placedHash = reader.readString();
        require("c86331fb1b3a758f3702bc1e4ad22bc416f1e80eb7582f889ce2a0591c02ac18"
                        .equals(placedHash),
                "Ancient City placed feature codec receipt drift");
        require(placedHash.equals(reader.rawSha256(placedStart, placedEnd)),
                "Ancient City placed feature codec hash mismatch");
        reader.field("placementModifierTypesInOrder", 4);
        requireEmptyArray(reader, "Ancient City placement modifier type drift");
        reader.field("configuredTargetKey", 5);
        require(SCULK_FEATURE.equals(reader.readString()),
                "Ancient City configured feature target drift");
        reader.field("configuredType", 6);
        require("minecraft:sequence".equals(reader.readString()),
                "unknown Ancient City configured feature type");
        reader.field("configuredImplementationClass", 7);
        require("net.minecraft.world.level.levelgen.feature.SequenceFeature".equals(reader.readString()),
                "Ancient City configured feature implementation drift");
        reader.field("configuredOrderedCodec", 8);
        int configuredStart = reader.mark();
        SculkSequence sequence = parseSculkSequence(reader);
        int configuredEnd = reader.mark();
        reader.field("configuredOrderedCodecSha256", 9);
        String configuredHash = reader.readString();
        require("bb2707d56041d34cc8c79d480ff734439c7c3c99e5044291fbd3f17ae5d05dbc"
                        .equals(configuredHash),
                "Ancient City configured feature codec receipt drift");
        require(configuredHash.equals(reader.rawSha256(configuredStart, configuredEnd)),
                "Ancient City configured feature codec hash mismatch");
        reader.field("transitiveConfiguredFeatures", 10);
        List<FeatureIdentity> transitive = parseTransitiveFeatures(reader);
        reader.field("rng", 11);
        reader.beginObject();
        reader.field("placementModifiersConsumeRandom", 0);
        require(!reader.readBoolean(), "Ancient City placement modifier RNG drift");
        reader.field("configuredFeatureRandom", 1);
        String random = reader.readString();
        require("the same caller RandomSource passed to feature placement".equals(random),
                "Ancient City configured feature RNG source drift");
        reader.field("configurationSource", 2);
        require("configuredOrderedCodec".equals(reader.readString()),
                "Ancient City configured feature configuration-source drift");
        reader.endObject();
        reader.endObject();
        return new ConfiguredFeature(SCULK_FEATURE, sequence, transitive, false, random);
    }

    private static SculkSequence parseSculkSequence(JsonReader reader) {
        reader.beginObject();
        reader.field("features", 0);
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City sculk sequence root absent");
        reader.beginObject();
        reader.field("feature", 0);
        SculkPatch patch = parseSculkPatch(reader);
        reader.field("placement", 1);
        requireEmptyArray(reader, "Ancient City sculk patch placement drift");
        reader.endObject();
        require(reader.nextArrayValue(1), "Ancient City overlay sequence child absent");
        reader.beginObject();
        reader.field("feature", 0);
        OverlayFeature overlay = parseOverlay(reader);
        reader.field("placement", 1);
        requireEmptyArray(reader, "Ancient City overlay placement drift");
        reader.endObject();
        require(!reader.nextArrayValue(2), "Ancient City sequence child cardinality drift");
        reader.endArray();
        reader.field("type", 1);
        require("minecraft:sequence".equals(reader.readString()),
                "Ancient City configured sequence type drift");
        reader.endObject();
        return new SculkSequence(patch, overlay);
    }

    private static SculkPatch parseSculkPatch(JsonReader reader) {
        reader.beginObject();
        reader.field("charge_count", 0);
        int chargeCount = reader.readInt();
        reader.field("amount_per_charge", 1);
        int amountPerCharge = reader.readInt();
        reader.field("spread_attempts", 2);
        int spreadAttempts = reader.readInt();
        reader.field("growth_rounds", 3);
        int growthRounds = reader.readInt();
        reader.field("spread_rounds", 4);
        int spreadRounds = reader.readInt();
        reader.field("type", 5);
        require("minecraft:sculk_patch".equals(reader.readString()),
                "unknown Ancient City sculk patch type");
        reader.endObject();
        SculkPatch patch = new SculkPatch(chargeCount, amountPerCharge, spreadAttempts,
                growthRounds, spreadRounds);
        require(patch.equals(new SculkPatch(10, 32, 64, 0, 1)),
                "Ancient City sculk patch configuration drift");
        return patch;
    }

    private static OverlayFeature parseOverlay(JsonReader reader) {
        reader.beginObject();
        reader.field("features", 0);
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City catalyst overlay child absent");
        CatalystFeature catalyst = parseCatalystPlacedFeature(reader);
        require(reader.nextArrayValue(1), "Ancient City shrieker overlay child absent");
        ShriekerFeature shrieker = parseShriekerPlacedFeature(reader);
        require(!reader.nextArrayValue(2), "Ancient City overlay child cardinality drift");
        reader.endArray();
        reader.field("type", 1);
        require("minecraft:overlay".equals(reader.readString()),
                "unknown Ancient City overlay feature type");
        reader.endObject();
        return new OverlayFeature(catalyst, shrieker);
    }

    private static CatalystFeature parseCatalystPlacedFeature(JsonReader reader) {
        reader.beginObject();
        reader.field("feature", 0);
        reader.beginObject();
        reader.field("to_place", 0);
        reader.beginObject();
        reader.field("state", 0);
        String state = reader.readString();
        require("minecraft:sculk_catalyst".equals(state),
                "Ancient City catalyst state drift");
        reader.field("type", 1);
        require("minecraft:simple_state_provider".equals(reader.readString()),
                "Ancient City catalyst provider drift");
        reader.endObject();
        reader.field("type", 1);
        require("minecraft:simple_block".equals(reader.readString()),
                "Ancient City catalyst feature type drift");
        reader.endObject();
        reader.field("placement", 1);
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City catalyst chance placement absent");
        reader.beginObject();
        reader.field("chance", 0);
        double chance = reader.readDouble();
        require(Double.compare(chance, 0.5D) == 0, "Ancient City catalyst chance drift");
        reader.field("type", 1);
        require("minecraft:random_chance".equals(reader.readString()),
                "Ancient City catalyst chance placement type drift");
        reader.endObject();
        require(reader.nextArrayValue(1), "Ancient City catalyst support predicate absent");
        Pos support = parseSupportPredicatePlacement(reader);
        require(!reader.nextArrayValue(2), "Ancient City catalyst placement cardinality drift");
        reader.endArray();
        reader.endObject();
        return new CatalystFeature(state, chance, support);
    }

    private static ShriekerFeature parseShriekerPlacedFeature(JsonReader reader) {
        reader.beginObject();
        reader.field("feature", 0);
        reader.beginObject();
        reader.field("to_place", 0);
        reader.beginObject();
        reader.field("state", 0);
        BlockStateSpec state = parseShriekerState(reader);
        reader.field("type", 1);
        require("minecraft:simple_state_provider".equals(reader.readString()),
                "Ancient City shrieker provider drift");
        reader.endObject();
        reader.field("type", 1);
        require("minecraft:simple_block".equals(reader.readString()),
                "Ancient City shrieker feature type drift");
        reader.endObject();
        reader.field("placement", 1);
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City shrieker count placement absent");
        UniformInt count = parseCountPlacement(reader);
        require(reader.nextArrayValue(1), "Ancient City shrieker offset placement absent");
        OffsetDistribution offset = parseOffsetPlacement(reader);
        require(reader.nextArrayValue(2), "Ancient City shrieker block predicate absent");
        ShriekerPredicate predicate = parseShriekerPredicatePlacement(reader);
        require(!reader.nextArrayValue(3), "Ancient City shrieker placement cardinality drift");
        reader.endArray();
        reader.endObject();
        return new ShriekerFeature(state, count, offset, predicate.requiredTag(),
                predicate.supportOffset());
    }

    private static BlockStateSpec parseShriekerState(JsonReader reader) {
        reader.beginObject();
        reader.field("properties", 0);
        reader.beginObject();
        reader.field("waterlogged", 0);
        String waterlogged = reader.readString();
        reader.field("shrieking", 1);
        String shrieking = reader.readString();
        reader.field("can_summon", 2);
        String canSummon = reader.readString();
        reader.endObject();
        reader.field("id", 1);
        String id = reader.readString();
        reader.endObject();
        BlockStateSpec state = new BlockStateSpec(id, waterlogged, shrieking, canSummon);
        require(state.equals(new BlockStateSpec(
                        "minecraft:sculk_shrieker", "false", "false", "true")),
                "Ancient City shrieker state drift");
        return state;
    }

    private static UniformInt parseCountPlacement(JsonReader reader) {
        reader.beginObject();
        reader.field("count", 0);
        UniformInt count = parseUniform(reader);
        reader.field("type", 1);
        require("minecraft:count".equals(reader.readString()),
                "Ancient City shrieker count placement type drift");
        reader.endObject();
        require(count.equals(new UniformInt(1, 3)), "Ancient City shrieker count drift");
        return count;
    }

    private static OffsetDistribution parseOffsetPlacement(JsonReader reader) {
        reader.beginObject();
        reader.field("x", 0);
        UniformInt x = parseUniform(reader);
        reader.field("y", 1);
        UniformInt y = parseUniform(reader);
        reader.field("z", 2);
        UniformInt z = parseUniform(reader);
        reader.field("type", 3);
        require("minecraft:offset".equals(reader.readString()),
                "Ancient City shrieker offset placement type drift");
        reader.endObject();
        OffsetDistribution result = new OffsetDistribution(x, y, z);
        UniformInt expected = new UniformInt(-2, 2);
        require(result.equals(new OffsetDistribution(expected, expected, expected)),
                "Ancient City shrieker offset distribution drift");
        return result;
    }

    private static UniformInt parseUniform(JsonReader reader) {
        reader.beginObject();
        reader.field("min_inclusive", 0);
        int min = reader.readInt();
        reader.field("max_inclusive", 1);
        int max = reader.readInt();
        require(min <= max, "Ancient City uniform distribution bounds drift");
        reader.field("type", 2);
        require("minecraft:uniform".equals(reader.readString()),
                "Ancient City uniform distribution type drift");
        reader.endObject();
        return new UniformInt(min, max);
    }

    private static Pos parseSupportPredicatePlacement(JsonReader reader) {
        reader.beginObject();
        reader.field("predicate", 0);
        Pos support = parseSupportPredicate(reader);
        reader.field("type", 1);
        require("minecraft:block_predicate_filter".equals(reader.readString()),
                "Ancient City support filter placement type drift");
        reader.endObject();
        return support;
    }

    private static Pos parseSupportPredicate(JsonReader reader) {
        reader.beginObject();
        reader.field("offset", 0);
        Pos offset = readPos(reader);
        require(offset.equals(new Pos(0, -1, 0)), "Ancient City support predicate offset drift");
        reader.field("direction", 1);
        require("up".equals(reader.readString()), "Ancient City support predicate direction drift");
        reader.field("type", 2);
        require("minecraft:has_sturdy_face".equals(reader.readString()),
                "Ancient City support predicate type drift");
        reader.endObject();
        return offset;
    }

    private static ShriekerPredicate parseShriekerPredicatePlacement(JsonReader reader) {
        reader.beginObject();
        reader.field("predicate", 0);
        reader.beginObject();
        reader.field("predicates", 0);
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City shrieker air predicate absent");
        reader.beginObject();
        reader.field("tag", 0);
        String tag = reader.readString();
        require("minecraft:air".equals(tag), "Ancient City shrieker predicate tag drift");
        reader.field("type", 1);
        require("minecraft:matching_block_tag".equals(reader.readString()),
                "Ancient City shrieker matching predicate type drift");
        reader.endObject();
        require(reader.nextArrayValue(1), "Ancient City shrieker support predicate absent");
        Pos support = parseSupportPredicate(reader);
        require(!reader.nextArrayValue(2), "Ancient City shrieker predicate cardinality drift");
        reader.endArray();
        reader.field("type", 1);
        require("minecraft:all_of".equals(reader.readString()),
                "Ancient City shrieker predicate composition drift");
        reader.endObject();
        reader.field("type", 1);
        require("minecraft:block_predicate_filter".equals(reader.readString()),
                "Ancient City shrieker predicate placement type drift");
        reader.endObject();
        return new ShriekerPredicate(tag, support);
    }

    private static List<FeatureIdentity> parseTransitiveFeatures(JsonReader reader) {
        List<FeatureIdentity> expected = List.of(
                new FeatureIdentity("minecraft:sculk_patch",
                        "net.minecraft.world.level.levelgen.feature.SculkPatchFeature"),
                new FeatureIdentity("minecraft:overlay",
                        "net.minecraft.world.level.levelgen.feature.OverlayFeature"),
                new FeatureIdentity("minecraft:simple_block",
                        "net.minecraft.world.level.levelgen.feature.SimpleBlockFeature"),
                new FeatureIdentity("minecraft:simple_block",
                        "net.minecraft.world.level.levelgen.feature.SimpleBlockFeature"));
        ArrayList<FeatureIdentity> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("type", 0);
            String type = reader.readString();
            reader.field("runtimeClass", 1);
            String runtimeClass = reader.readString();
            reader.endObject();
            result.add(new FeatureIdentity(type, runtimeClass));
            index++;
        }
        reader.endArray();
        require(result.equals(expected), "unknown Ancient City transitive configured feature identity");
        return List.copyOf(result);
    }

    private static List<Template> parseTemplates(JsonReader reader) {
        ArrayList<Template> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < TEMPLATE_KEYS.size(), "Ancient City template cardinality overflow");
            result.add(parseTemplate(reader, TEMPLATE_KEYS.get(index), index));
            index++;
        }
        reader.endArray();
        require(index == TEMPLATE_KEYS.size(), "Ancient City template cardinality drift");
        return List.copyOf(result);
    }

    private static Template parseTemplate(JsonReader reader, String expectedTemplate, int templateIndex) {
        reader.beginObject();
        reader.field("template", 0);
        String template = reader.readString();
        require(expectedTemplate.equals(template), "Ancient City template encounter order drift");
        reader.field("sourceStatus", 1);
        SourceStatus status = SourceStatus.fromWire(reader.readString());
        reader.field("size", 2);
        Optional<Size> size;
        if (status == SourceStatus.EXPECTED_ABSENT) {
            require(EXPECTED_ABSENT_TEMPLATE.equals(template),
                    "unknown Ancient City expected-absent template");
            requireEmptyArray(reader, "Ancient City expected-absent size drift");
            size = Optional.empty();
        } else {
            require(!EXPECTED_ABSENT_TEMPLATE.equals(template),
                    "Ancient City expected-absent template marked present");
            Pos rawSize = readPos(reader);
            require(rawSize.x() > 0 && rawSize.y() > 0 && rawSize.z() > 0,
                    "Ancient City template dimensions must be positive");
            size = Optional.of(new Size(rawSize.x(), rawSize.y(), rawSize.z()));
        }

        reader.field("stateTable", 3);
        int statesStart = reader.mark();
        List<String> states = readStringList(reader);
        int statesEnd = reader.mark();
        reader.field("stateTableSha256", 4);
        String stateHash = reader.readString();
        require(stateHash.equals(reader.rawSha256(statesStart, statesEnd)),
                "Ancient City template state-table hash mismatch");
        require(states.size() == new HashSet<>(states).size(),
                "Ancient City template state table contains duplicates");
        reader.field("blockCount", 5);
        int blockCount = reader.readInt();
        reader.field("commandCount", 6);
        int commandCount = reader.readInt();
        reader.field("commands", 7);
        int commandsStart = reader.mark();
        CommandParse commands = parseCommands(reader, states, size);
        int commandsEnd = reader.mark();
        reader.field("commandStreamSha256", 8);
        String commandHash = reader.readString();
        require(commandHash.equals(reader.rawSha256(commandsStart, commandsEnd)),
                "Ancient City template command-stream hash mismatch");
        reader.field("opcodeCounts", 9);
        EnumMap<Opcode, Integer> declaredOpcodeCounts = parseOpcodeCounts(reader);
        reader.field("connectorCount", 10);
        int connectorCount = reader.readInt();
        reader.field("connectorsInTemplateOrder", 11);
        int connectorsStart = reader.mark();
        List<Connector> connectors = parseConnectors(reader, size);
        int connectorsEnd = reader.mark();
        reader.field("connectorOrderSha256", 12);
        String connectorHash = reader.readString();
        require(connectorHash.equals(reader.rawSha256(connectorsStart, connectorsEnd)),
                "Ancient City connector-order hash mismatch");
        reader.field("entityCount", 13);
        int entityCount = reader.readInt();
        reader.endObject();

        require(blockCount >= 0 && commandCount >= 0 && connectorCount >= 0,
                "Ancient City template count underflow");
        require(commandCount == commands.commands().size(),
                "Ancient City template command count drift");
        require(connectorCount == connectors.size(), "Ancient City connector count drift");
        require(commands.expandedBlocks() == blockCount,
                "Ancient City arithmetic command grammar is lossy");
        require(declaredOpcodeCounts.equals(commands.opcodeCounts()),
                "Ancient City opcode-count drift");
        require(entityCount == 0, "unexpected Ancient City ENTS payload");
        validateJigsawBindings(states, commands.commands(), connectors);

        if (status == SourceStatus.EXPECTED_ABSENT) {
            require(templateIndex == 40, "Ancient City expected-absent template order drift");
            require(size.isEmpty() && states.isEmpty() && blockCount == 0 && commandCount == 0
                            && commands.commands().isEmpty() && declaredOpcodeCounts.isEmpty()
                            && connectors.isEmpty() && entityCount == 0,
                    "Ancient City expected-absent template semantic drift");
            require(EMPTY_JSON_SHA256.equals(stateHash)
                            && EMPTY_JSON_SHA256.equals(commandHash)
                            && EMPTY_JSON_SHA256.equals(connectorHash),
                    "Ancient City expected-absent template empty hash drift");
        } else {
            require(!states.isEmpty() && blockCount > 0 && commandCount > 0,
                    "Ancient City present template is empty");
        }
        return new Template(template, status, size, states, blockCount, commands.commands(),
                connectors, entityCount);
    }

    private static CommandParse parseCommands(
            JsonReader reader, List<String> states, Optional<Size> size) {
        ArrayList<Command> commands = new ArrayList<>();
        EnumMap<Opcode, Integer> counts = new EnumMap<>(Opcode.class);
        int cursor = 0;
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("op", 0);
            Opcode opcode = Opcode.fromWire(reader.readString());
            reader.field("ordinal", 1);
            int ordinal = reader.readInt();
            require(ordinal == cursor, "Ancient City command continuity drift");
            Command command = null;
            int expanded = 1;
            switch (opcode) {
                case RUN -> {
                    reader.field("start", 2);
                    Pos start = readPos(reader);
                    reader.field("delta", 3);
                    Pos delta = readPos(reader);
                    reader.field("count", 4);
                    int count = reader.readInt();
                    require(count > 0, "Ancient City RUN count must be positive");
                    long max = Math.max(Math.max(Math.abs((long) delta.x()), Math.abs((long) delta.y())),
                            Math.abs((long) delta.z()));
                    require((count > 1 && max == 1) || (count == 1 && max <= 1),
                            "Ancient City RUN delta drift");
                    reader.field("state", 5);
                    int state = requireStateIndex(reader.readInt(), states);
                    if (size.isPresent()) {
                        require(size.get().contains(start), "Ancient City RUN start is outside template");
                        Pos end = addScaled(start, delta, count - 1);
                        require(size.get().contains(end), "Ancient City RUN end is outside template");
                    }
                    command = new Run(ordinal, start, delta, count, state);
                    expanded = count;
                }
                case JIGSAW -> {
                    reader.field("state", 2);
                    int state = requireStateIndex(reader.readInt(), states);
                    reader.field("connectorOrdinal", 3);
                    int connectorOrdinal = reader.readInt();
                    require(connectorOrdinal >= 0, "Ancient City JIGSAW connector index underflow");
                    reader.field("finalState", 4);
                    String finalState = reader.readString();
                    require(!finalState.isEmpty(), "Ancient City JIGSAW final state is empty");
                    command = new Jigsaw(ordinal, state, connectorOrdinal, finalState);
                }
                case LOOT_CONTAINER -> {
                    reader.field("position", 2);
                    Pos position = readPos(reader);
                    reader.field("state", 3);
                    int state = requireStateIndex(reader.readInt(), states);
                    reader.field("blockEntityType", 4);
                    String blockEntityType = reader.readString();
                    require("minecraft:chest".equals(blockEntityType),
                            "unknown Ancient City loot block-entity identity");
                    reader.field("lootTable", 5);
                    String lootTable = reader.readString();
                    require(VALID_LOOT_TABLES.contains(lootTable),
                            "unknown Ancient City loot identity");
                    if (size.isPresent()) {
                        require(size.get().contains(position),
                                "Ancient City LOOT position is outside template");
                    }
                    command = new LootContainer(ordinal, position, state, blockEntityType, lootTable);
                }
                case BENT -> {
                    reader.field("position", 2);
                    Pos position = readPos(reader);
                    reader.field("state", 3);
                    int state = requireStateIndex(reader.readInt(), states);
                    reader.field("payload", 4);
                    BentPayload payload = parseBentPayload(reader);
                    if (size.isPresent()) {
                        require(size.get().contains(position),
                                "Ancient City BENT position is outside template");
                    }
                    command = new Bent(ordinal, position, state, payload);
                }
            }
            reader.endObject();
            require(command != null, "Ancient City command dispatch drift");
            commands.add(command);
            counts.merge(opcode, 1, Integer::sum);
            cursor = Math.addExact(cursor, expanded);
            index++;
        }
        reader.endArray();
        return new CommandParse(List.copyOf(commands), cursor, counts);
    }

    private static Pos addScaled(Pos start, Pos delta, int scale) {
        try {
            return new Pos(
                    Math.addExact(start.x(), Math.multiplyExact(delta.x(), scale)),
                    Math.addExact(start.y(), Math.multiplyExact(delta.y(), scale)),
                    Math.addExact(start.z(), Math.multiplyExact(delta.z(), scale)));
        } catch (ArithmeticException error) {
            throw invalid("Ancient City RUN coordinate overflow", error);
        }
    }

    private static BentPayload parseBentPayload(JsonReader reader) {
        reader.beginObject();
        reader.field("semantic", 0);
        String semantic = reader.readString();
        BentPayload result;
        switch (semantic) {
            case "COMPARATOR_OUTPUT" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:comparator".equals(type),
                        "unknown Ancient City comparator block-entity identity");
                reader.field("outputSignal", 2);
                int output = reader.readInt();
                require(output >= 0 && output <= 15, "Ancient City comparator output drift");
                result = new ComparatorOutput(type, output);
            }
            case "EMPTY_SCULK_SENSOR" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:sculk_sensor".equals(type),
                        "unknown Ancient City sculk-sensor block-entity identity");
                result = new EmptySculkSensor(type);
            }
            case "EMPTY_LECTERN" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:lectern".equals(type),
                        "unknown Ancient City lectern block-entity identity");
                result = new EmptyLectern(type);
            }
            case "EMPTY_SKELETON_SKULL" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:skull".equals(type),
                        "unknown Ancient City skull block-entity identity");
                result = new EmptySkeletonSkull(type);
            }
            case "EMPTY_CAMPFIRE" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:campfire".equals(type),
                        "unknown Ancient City campfire block-entity identity");
                reader.field("items", 2);
                requireEmptyArray(reader, "Ancient City campfire items drift");
                reader.field("cookingTimes", 3);
                List<Integer> cookingTimes = readIntList(reader);
                reader.field("cookingTotalTimes", 4);
                List<Integer> cookingTotalTimes = readIntList(reader);
                require(cookingTimes.equals(List.of(0, 0, 0, 0))
                                && cookingTotalTimes.equals(List.of(0, 0, 0, 0)),
                        "Ancient City campfire cooking payload drift");
                result = new EmptyCampfire(type, cookingTimes, cookingTotalTimes);
            }
            case "FIXED_CHEST_CONTENTS" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:chest".equals(type),
                        "unknown Ancient City fixed-chest block-entity identity");
                reader.field("items", 2);
                List<ItemStack> items = parseItems(reader);
                require(items.equals(List.of(new ItemStack(13, "minecraft:golden_apple", 1))),
                        "Ancient City fixed-chest payload drift");
                result = new FixedChestContents(type, items);
            }
            case "FURNACE_PAYLOAD" -> {
                reader.field("blockEntityType", 1);
                String type = reader.readString();
                require("minecraft:furnace".equals(type),
                        "unknown Ancient City furnace block-entity identity");
                reader.field("items", 2);
                List<ItemStack> items = parseItems(reader);
                require(items.equals(List.of(
                                new ItemStack(1, "minecraft:wooden_shovel", 1),
                                new ItemStack(2, "minecraft:deepslate", 24))),
                        "Ancient City furnace inventory drift");
                reader.field("recipesUsed", 3);
                List<RecipeUse> recipes = parseRecipes(reader);
                require(recipes.equals(List.of(new RecipeUse("minecraft:deepslate", 24))),
                        "Ancient City furnace recipe drift");
                reader.field("litTotalTime", 4);
                int litTotal = reader.readInt();
                reader.field("cookingTimeSpent", 5);
                int cookingSpent = reader.readInt();
                reader.field("cookingTotalTime", 6);
                int cookingTotal = reader.readInt();
                reader.field("litTimeRemaining", 7);
                int litRemaining = reader.readInt();
                require(litTotal == 0 && cookingSpent == 0 && cookingTotal == 200
                                && litRemaining == 0,
                        "Ancient City furnace timer drift");
                result = new FurnacePayload(type, items, recipes, litTotal, cookingSpent,
                        cookingTotal, litRemaining);
            }
            default -> throw invalid("unknown Ancient City BENT identity: " + semantic);
        }
        reader.endObject();
        return result;
    }

    private static List<ItemStack> parseItems(JsonReader reader) {
        ArrayList<ItemStack> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("slot", 0);
            int slot = reader.readInt();
            reader.field("item", 1);
            String item = reader.readString();
            reader.field("count", 2);
            int count = reader.readInt();
            reader.endObject();
            require(slot >= 0 && count > 0, "Ancient City item payload range drift");
            result.add(new ItemStack(slot, item, count));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static List<RecipeUse> parseRecipes(JsonReader reader) {
        ArrayList<RecipeUse> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("recipe", 0);
            String recipe = reader.readString();
            reader.field("count", 1);
            int count = reader.readInt();
            reader.endObject();
            require(count > 0, "Ancient City recipe count drift");
            result.add(new RecipeUse(recipe, count));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static EnumMap<Opcode, Integer> parseOpcodeCounts(JsonReader reader) {
        EnumMap<Opcode, Integer> result = new EnumMap<>(Opcode.class);
        reader.beginObject();
        int index = 0;
        String previous = null;
        while (reader.hasNextObjectField(index)) {
            String name = reader.readFieldName(index);
            if (previous != null) {
                require(previous.compareTo(name) < 0, "Ancient City opcode field order drift");
            }
            Opcode opcode = Opcode.fromWire(name);
            require(!result.containsKey(opcode), "duplicate Ancient City opcode count");
            int count = reader.readInt();
            require(count > 0, "Ancient City opcode count must be positive");
            result.put(opcode, count);
            previous = name;
            index++;
        }
        reader.endObject();
        return result;
    }

    private static List<Connector> parseConnectors(JsonReader reader, Optional<Size> size) {
        ArrayList<Connector> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("ordinal", 0);
            int ordinal = reader.readInt();
            require(ordinal == index, "Ancient City connector encounter order drift");
            reader.field("position", 1);
            Pos position = readPos(reader);
            if (size.isPresent()) {
                require(size.get().contains(position), "Ancient City connector is outside template");
            }
            reader.field("state", 2);
            String state = reader.readString();
            reader.field("front", 3);
            Direction front = Direction.fromWire(reader.readString());
            reader.field("top", 4);
            Direction top = Direction.fromWire(reader.readString());
            require(front.axis != top.axis, "Ancient City connector axes overlap");
            reader.field("joint", 5);
            String joint = reader.readString();
            require("ROLLABLE".equals(joint), "unknown Ancient City connector joint");
            reader.field("name", 6);
            String name = reader.readString();
            reader.field("target", 7);
            String target = reader.readString();
            reader.field("pool", 8);
            String pool = reader.readString();
            require("minecraft:empty".equals(pool) || POOL_KEYS.contains(pool),
                    "unknown Ancient City connector pool");
            reader.field("placementPriority", 9);
            int placementPriority = reader.readInt();
            reader.field("selectionPriority", 10);
            int selectionPriority = reader.readInt();
            reader.endObject();
            result.add(new Connector(ordinal, position, state, front, top, name, target, pool,
                    placementPriority, selectionPriority));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static void validateJigsawBindings(
            List<String> states, List<Command> commands, List<Connector> connectors) {
        boolean[] seen = new boolean[connectors.size()];
        int jigsawCount = 0;
        for (Command command : commands) {
            if (!(command instanceof Jigsaw jigsaw)) continue;
            require(jigsaw.connectorOrdinal() >= 0 && jigsaw.connectorOrdinal() < connectors.size(),
                    "Ancient City JIGSAW connector index drift");
            require(!seen[jigsaw.connectorOrdinal()], "duplicate Ancient City JIGSAW connector index");
            seen[jigsaw.connectorOrdinal()] = true;
            Connector connector = connectors.get(jigsaw.connectorOrdinal());
            require(states.get(jigsaw.state()).equals(connector.state()),
                    "Ancient City JIGSAW state/connector drift");
            jigsawCount++;
        }
        require(jigsawCount == connectors.size(), "Ancient City JIGSAW connector binding drift");
        for (boolean value : seen) require(value, "Ancient City connector is unbound");
    }

    private static TypedSidecars parseSidecars(JsonReader reader) {
        reader.beginObject();
        reader.field("BENT", 0);
        List<SidecarCount> bent = parseSidecarCounts(reader);
        reader.field("LOOT", 1);
        List<SidecarCount> loot = parseSidecarCounts(reader);
        reader.field("SPWN", 2);
        requireEmptyArray(reader, "Ancient City SPWN sidecar is not empty");
        reader.field("ENTS", 3);
        requireEmptyArray(reader, "Ancient City ENTS sidecar is not empty");
        reader.endObject();
        TypedSidecars result = new TypedSidecars(bent, loot, List.of(), List.of());
        require(result.equals(expectedSidecars()), "Ancient City typed sidecar closure drift");
        return result;
    }

    private static List<SidecarCount> parseSidecarCounts(JsonReader reader) {
        ArrayList<SidecarCount> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            reader.beginObject();
            reader.field("identity", 0);
            String identity = reader.readString();
            reader.field("occurrences", 1);
            int occurrences = reader.readInt();
            require(occurrences > 0, "Ancient City sidecar occurrence drift");
            reader.endObject();
            result.add(new SidecarCount(identity, occurrences));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static TypedSidecars expectedSidecars() {
        return new TypedSidecars(List.of(
                new SidecarCount("COMPARATOR_OUTPUT|minecraft:comparator", 19),
                new SidecarCount("EMPTY_CAMPFIRE|minecraft:campfire", 2),
                new SidecarCount("EMPTY_LECTERN|minecraft:lectern", 3),
                new SidecarCount("EMPTY_SCULK_SENSOR|minecraft:sculk_sensor", 10),
                new SidecarCount("EMPTY_SKELETON_SKULL|minecraft:skull", 2),
                new SidecarCount("FIXED_CHEST_CONTENTS|minecraft:chest", 1),
                new SidecarCount("FURNACE_PAYLOAD|minecraft:furnace", 1),
                new SidecarCount("LOOT_CONTAINER|minecraft:chest", 14)),
                List.of(
                        new SidecarCount("minecraft:chests/ancient_city_ice_box|minecraft:chest", 1),
                        new SidecarCount("minecraft:chests/ancient_city|minecraft:chest", 13)),
                List.of(), List.of());
    }

    private static List<String> parseStateClosure(JsonReader reader) {
        reader.beginObject();
        reader.field("identityCount", 0);
        int identityCount = reader.readInt();
        reader.field("statesInEncounterOrder", 1);
        int statesStart = reader.mark();
        List<String> states = readStringList(reader);
        int statesEnd = reader.mark();
        reader.field("encounterOrderSha256", 2);
        String orderHash = reader.readString();
        require("c4c25c7e40993384ec1aa8645aaf5e818ab57ef1077f779e9190018832bbbf48"
                        .equals(orderHash),
                "Ancient City state encounter-order receipt drift");
        require(orderHash.equals(reader.rawSha256(statesStart, statesEnd)),
                "Ancient City state encounter-order hash mismatch");
        reader.endObject();
        require(identityCount == 294 && states.size() == 294,
                "Ancient City state identity count drift");
        require(states.size() == new HashSet<>(states).size(),
                "Ancient City state closure contains duplicates");
        return states;
    }

    private static Aggregate parseAggregate(JsonReader reader) {
        reader.beginObject();
        reader.field("poolCount", 0);
        int poolCount = reader.readInt();
        reader.field("rawPoolElementCount", 1);
        int raw = reader.readInt();
        reader.field("expandedPoolWeight", 2);
        int weight = reader.readInt();
        reader.field("templateIdentityCount", 3);
        int templates = reader.readInt();
        reader.field("presentTemplateCount", 4);
        int present = reader.readInt();
        reader.field("expectedAbsentTemplateCount", 5);
        int absent = reader.readInt();
        reader.field("blockCount", 6);
        int blocks = reader.readInt();
        reader.field("commandCount", 7);
        int commands = reader.readInt();
        reader.field("connectorCount", 8);
        int connectors = reader.readInt();
        reader.field("stateIdentityCount", 9);
        int states = reader.readInt();
        reader.field("poolsSha256", 10);
        String poolsHash = requireHash(reader.readString());
        reader.field("templatesSha256", 11);
        String templatesHash = requireHash(reader.readString());
        reader.field("processorListsSha256", 12);
        String processorListsHash = requireHash(reader.readString());
        reader.field("processorSemanticsSha256", 13);
        String processorSemanticsHash = requireHash(reader.readString());
        reader.field("configuredFeatureSha256", 14);
        String configuredHash = requireHash(reader.readString());
        reader.field("sidecarsSha256", 15);
        String sidecarsHash = requireHash(reader.readString());
        reader.field("stateClosureSha256", 16);
        String stateClosureHash = requireHash(reader.readString());
        reader.endObject();
        Aggregate result = new Aggregate(poolCount, raw, weight, templates, present, absent, blocks,
                commands, connectors, states, poolsHash, templatesHash, processorListsHash,
                processorSemanticsHash, configuredHash, sidecarsHash, stateClosureHash);
        require(result.equals(new Aggregate(
                        7, 65, 107, 58, 57, 1, 72_710, 26_176, 156, 294,
                        "84fa93195c5ad6b53c59e67738e1f66e1307c742759c830f3ad387af80d400d6",
                        "3074aa632e1e8a75cf2785d013b0e33d4603f21f02a11b39c173a10b44b9526f",
                        "015e93c6195c9b7acdeb8d5e5e1b35f1b2c1d3f3b29611ffceb4f664342b1ca0",
                        "b06b35c3c219c885516b963e299c46abc42479c123de15a28f12412ff717eff9",
                        "28892d667abfeae5839552fb95d2c835ce8c6d3f4f0b2bd82cb36295d41e40f1",
                        "3d90f4907e13dec2d1c0bddd0ecd26a6540918cff4bc1acda6e70d5c9da4a6b5",
                        "d40ed81bb43dd9f0c94641fe0db2148b548cd2c91e107490cb230511e903b27d")),
                "Ancient City aggregate/subhash drift");
        return result;
    }

    private static void parseAssetBoundary(JsonReader reader) {
        List<String> expected = List.of(
                "no raw Mojang structure NBT or full template payload copy",
                "no world-seed/chunk generation probe, generated piece layout or finite coordinate lookup",
                "template blocks use a lossless state dictionary plus arithmetic RUN/JIGSAW/LOOT_CONTAINER grammar",
                "SPWN and ENTS are authenticated empty; LOOT/BENT are emitted only for exact recognized payloads");
        require(readStringList(reader).equals(expected), "Ancient City asset boundary drift");
    }

    private static void validateCrossClosure(ParsedEvidence evidence) {
        Aggregate aggregate = evidence.aggregate();
        require(evidence.poolKeys().equals(POOL_KEYS), "Ancient City pool key closure drift");
        require(evidence.pools().size() == aggregate.poolCount(), "Ancient City pool aggregate drift");
        int raw = 0;
        int weight = 0;
        for (Pool pool : evidence.pools()) {
            raw = Math.addExact(raw, pool.rawElementCount());
            weight = Math.addExact(weight, pool.expandedWeight());
        }
        require(raw == aggregate.rawPoolElementCount() && weight == aggregate.expandedPoolWeight(),
                "Ancient City weighted pool aggregate drift");

        require(evidence.processorLists().size() == 4 && evidence.processors().size() == 6,
                "Ancient City processor closure cardinality drift");
        Set<String> processorIds = new HashSet<>();
        for (ProcessorSpec processor : evidence.processors()) processorIds.add(processor.identity());
        require(processorIds.equals(Set.copyOf(PROCESSOR_SEMANTIC_IDENTITIES)),
                "Ancient City processor semantic closure drift");
        for (ProcessorListSpec list : evidence.processorLists()) {
            require(processorIds.containsAll(list.processorSemanticIdentitiesInOrder()),
                    "Ancient City processor-list references unknown semantic identity");
        }

        require(evidence.templates().size() == aggregate.templateIdentityCount(),
                "Ancient City template identity aggregate drift");
        int present = 0;
        int absent = 0;
        int blocks = 0;
        int commands = 0;
        int connectors = 0;
        EnumMap<BentSemantic, Integer> bentCounts = new EnumMap<>(BentSemantic.class);
        int lootCount = 0;
        int ancientLoot = 0;
        int iceBoxLoot = 0;
        Set<String> stateClosure = new HashSet<>(evidence.states());
        for (int index = 0; index < evidence.templates().size(); index++) {
            Template template = evidence.templates().get(index);
            require(TEMPLATE_KEYS.get(index).equals(template.id()),
                    "Ancient City template encounter order drift");
            if (template.status() == SourceStatus.PRESENT) present++; else absent++;
            blocks = Math.addExact(blocks, template.blockCount());
            commands = Math.addExact(commands, template.commands().size());
            connectors = Math.addExact(connectors, template.connectors().size());
            require(stateClosure.containsAll(template.stateTable()),
                    "Ancient City template state is outside global state closure");
            for (Command command : template.commands()) {
                if (command instanceof Jigsaw jigsaw) {
                    require(stateClosure.contains(jigsaw.finalState()),
                            "Ancient City JIGSAW final state is outside state closure");
                } else if (command instanceof Bent bent) {
                    bentCounts.merge(bent.payload().semantic(), 1, Integer::sum);
                } else if (command instanceof LootContainer loot) {
                    lootCount++;
                    if ("minecraft:chests/ancient_city".equals(loot.lootTable())) ancientLoot++;
                    else if ("minecraft:chests/ancient_city_ice_box".equals(loot.lootTable())) iceBoxLoot++;
                }
            }
        }
        require(present == aggregate.presentTemplateCount() && absent == aggregate.expectedAbsentTemplateCount()
                        && blocks == aggregate.blockCount() && commands == aggregate.commandCount()
                        && connectors == aggregate.connectorCount(),
                "Ancient City template aggregate drift");
        require(evidence.states().size() == aggregate.stateIdentityCount(),
                "Ancient City state aggregate drift");
        require(bentCounts.getOrDefault(BentSemantic.COMPARATOR_OUTPUT, 0) == 19
                        && bentCounts.getOrDefault(BentSemantic.EMPTY_CAMPFIRE, 0) == 2
                        && bentCounts.getOrDefault(BentSemantic.EMPTY_LECTERN, 0) == 3
                        && bentCounts.getOrDefault(BentSemantic.EMPTY_SCULK_SENSOR, 0) == 10
                        && bentCounts.getOrDefault(BentSemantic.EMPTY_SKELETON_SKULL, 0) == 2
                        && bentCounts.getOrDefault(BentSemantic.FIXED_CHEST_CONTENTS, 0) == 1
                        && bentCounts.getOrDefault(BentSemantic.FURNACE_PAYLOAD, 0) == 1,
                "Ancient City BENT command aggregate drift");
        require(lootCount == 14 && ancientLoot == 13 && iceBoxLoot == 1,
                "Ancient City LOOT command aggregate drift");
        require(evidence.sidecars().equals(expectedSidecars()),
                "Ancient City typed sidecar/command closure drift");
    }

    private static int requireStateIndex(int index, List<String> states) {
        require(index >= 0 && index < states.size(), "Ancient City command state index drift");
        return index;
    }

    private static String requireTemplateIdentity(String identity) {
        require(TEMPLATE_KEYS.contains(identity), "unknown Ancient City template identity: " + identity);
        return identity;
    }

    private static String requireProcessorListIdentity(String identity) {
        require(PROCESSOR_LIST_IDENTITIES.contains(identity),
                "unknown Ancient City processor-list identity: " + identity);
        return identity;
    }

    private static void validatePlacementPipeline(String processorList, List<String> placement) {
        String ignore = PROCESSOR_SEMANTIC_IDENTITIES.get(2);
        String jigsaw = PROCESSOR_SEMANTIC_IDENTITIES.get(3);
        String blockRot = PROCESSOR_SEMANTIC_IDENTITIES.get(4);
        String commonRule = PROCESSOR_SEMANTIC_IDENTITIES.get(0);
        String wallRule = PROCESSOR_SEMANTIC_IDENTITIES.get(5);
        String protectedBlocks = PROCESSOR_SEMANTIC_IDENTITIES.get(1);
        List<String> expected = switch (processorList) {
            case "inline" -> List.of(ignore, jigsaw);
            case "minecraft:ancient_city_start_degradation" ->
                    List.of(ignore, jigsaw, commonRule, protectedBlocks);
            case "minecraft:ancient_city_generic_degradation" ->
                    List.of(ignore, jigsaw, blockRot, commonRule, protectedBlocks);
            case "minecraft:ancient_city_walls_degradation" ->
                    List.of(ignore, jigsaw, blockRot, wallRule, protectedBlocks);
            default -> throw invalid("unknown Ancient City processor-list identity: " + processorList);
        };
        require(placement.equals(expected),
                "Ancient City placement processor pipeline drift for " + processorList);
    }

    private static String requireHash(String value) {
        require(isSha256(value), "Ancient City SHA-256 field type drift");
        return value;
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) return false;
        }
        return true;
    }

    private static Pos readPos(JsonReader reader) {
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City vector x is absent");
        int x = reader.readInt();
        require(reader.nextArrayValue(1), "Ancient City vector y is absent");
        int y = reader.readInt();
        require(reader.nextArrayValue(2), "Ancient City vector z is absent");
        int z = reader.readInt();
        require(!reader.nextArrayValue(3), "Ancient City vector cardinality drift");
        reader.endArray();
        return new Pos(x, y, z);
    }

    private static List<String> readStringList(JsonReader reader) {
        ArrayList<String> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            result.add(reader.readString());
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static List<Integer> readIntList(JsonReader reader) {
        ArrayList<Integer> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            result.add(reader.readInt());
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static void requireEmptyArray(JsonReader reader, String message) {
        reader.beginArray();
        require(!reader.nextArrayValue(0), message);
        reader.endArray();
    }

    List<String> registryPoolKeysInExecutionOrder() { return registryPoolKeysInExecutionOrder; }
    List<Pool> poolsInExecutionOrder() { return poolsInExecutionOrder; }
    List<ProcessorListSpec> processorListsInEncounterOrder() { return processorListsInEncounterOrder; }
    List<ProcessorSpec> processorSemanticsInEncounterOrder() { return processorSemanticsInEncounterOrder; }
    ConfiguredFeature configuredFeature() { return configuredFeature; }
    List<Template> templatesInEncounterOrder() { return templatesInEncounterOrder; }
    TypedSidecars typedSidecars() { return typedSidecars; }
    List<String> statesInEncounterOrder() { return statesInEncounterOrder; }
    Aggregate aggregate() { return aggregate; }
    List<BentLootPlacement> bentLootPlacementsInExecutionCorpusOrder() {
        return bentLootPlacementsInExecutionCorpusOrder;
    }
    List<CanonicalBlockEntityEvidence> canonicalBlockEntitiesInExecutionOrder() {
        return canonicalBlockEntitiesInExecutionOrder;
    }
    List<LootSeedEvidence> lootSeedsInExecutionCorpusOrder() {
        return lootSeedsInExecutionCorpusOrder;
    }

    Template requireTemplate(String identity) {
        for (Template template : templatesInEncounterOrder) {
            if (template.id().equals(identity)) return template;
        }
        throw invalid("unknown Ancient City template identity: " + identity);
    }

    static StartGraphCorpus decodeAcceptedStartGraphForTest(byte[] bytes) {
        return decodeAuthenticatedStartGraph(bytes);
    }

    static MutableSuccessorEvidence decodeAcceptedMutableSuccessorForTest(
            byte[] mutableSuccessorBytes, byte[] startGraphBytes) {
        StartGraphCorpus startGraph = decodeAuthenticatedStartGraph(startGraphBytes);
        MutableSuccessorEvidence evidence =
                decodeAuthenticatedMutableSuccessorEvidence(mutableSuccessorBytes);
        validateMutableSuccessorAgainstStartGraph(evidence, startGraph);
        return evidence;
    }

    private static MutableSuccessorEvidence decodeAuthenticatedMutableSuccessorEvidence(byte[] bytes) {
        require(bytes != null && bytes.length == MUTABLE_SUCCESSOR_RESOURCE_BYTES,
                "Ancient City mutable-successor resource byte length drift");
        require(sha256(bytes).equals(MUTABLE_SUCCESSOR_RESOURCE_SHA256),
                "Ancient City mutable-successor resource SHA-256 drift");
        return parseMutableSuccessorEvidence(bytes);
    }

    private static StartGraphCorpus decodeAuthenticatedStartGraph(byte[] bytes) {
        return parseStartGraphEvidence(decodeStartGraphCarrier(bytes));
    }

    private static byte[] decodeStartGraphCarrier(byte[] bytes) {
        require(bytes != null && bytes.length == START_GRAPH_RESOURCE_BYTES,
                "Ancient City start graph resource byte length drift");
        require(sha256(bytes).equals(START_GRAPH_RESOURCE_SHA256),
                "Ancient City start graph resource SHA-256 drift");
        final byte[] gzip;
        try {
            gzip = Base64.getMimeDecoder().decode(bytes);
        } catch (IllegalArgumentException error) {
            throw invalid("Ancient City start graph resource is not canonical base64", error);
        }
        require(gzip.length == START_GRAPH_GZIP_BYTES
                        && sha256(gzip).equals(START_GRAPH_GZIP_SHA256),
                "Ancient City start graph gzip identity drift");
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip));
                ByteArrayOutputStream output = new ByteArrayOutputStream(START_GRAPH_JSON_BYTES)) {
            byte[] buffer = new byte[16_384];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total = Math.addExact(total, read);
                require(total <= START_GRAPH_JSON_BYTES,
                        "Ancient City start graph inflated JSON exceeds pinned bound");
                output.write(buffer, 0, read);
            }
            byte[] raw = output.toByteArray();
            require(raw.length == START_GRAPH_JSON_BYTES
                            && sha256(raw).equals(START_GRAPH_JSON_SHA256),
                    "Ancient City start graph inflated JSON identity drift");
            return raw;
        } catch (IOException error) {
            throw invalid("failed to inflate accepted Ancient City start graph resource", error);
        }
    }

    private static MutableSuccessorEvidence parseMutableSuccessorEvidence(byte[] bytes) {
        Objects.requireNonNull(bytes, "Ancient City mutable-successor bytes");
        require(bytes.length <= 64 * 1024, "Ancient City mutable-successor parser bound exceeded");
        JsonReader reader = new JsonReader(bytes);
        reader.beginObject();
        reader.field("evidence", 0);
        MutableSuccessorEvidence evidence = readMutableSuccessorEvidence(reader);
        reader.field("regeneration", 1);
        reader.beginObject();
        reader.field("bridgePayloadBytes", 0);
        require(reader.readInt() == MUTABLE_SUCCESSOR_BRIDGE_BYTES,
                "Ancient City mutable-successor bridge byte-count drift");
        reader.field("bridgePayloadSha256", 1);
        require(MUTABLE_SUCCESSOR_BRIDGE_SHA256.equals(reader.readString()),
                "Ancient City mutable-successor bridge SHA-256 drift");
        reader.field("runs", 2);
        require(reader.readInt() == 2, "Ancient City mutable-successor regeneration run-count drift");
        reader.endObject();
        reader.field("schema", 2);
        require(reader.readInt() == 1, "Ancient City mutable-successor schema drift");
        reader.field("sourceAuthority", 3);
        readMutableSuccessorSourceAuthority(reader);
        reader.endObject();
        reader.finish();
        validateMutableSuccessorEvidence(evidence);
        return evidence;
    }

    private static MutableSuccessorEvidence readMutableSuccessorEvidence(JsonReader reader) {
        reader.beginObject();
        reader.field("boundary", 0);
        String boundary = reader.readString();
        reader.field("changedByteCount", 1);
        int changedByteCount = reader.readInt();
        reader.field("changedByteOffset", 2);
        int changedByteOffset = reader.readInt();
        reader.field("encounterOrdinal", 3);
        int encounterOrdinal = reader.readInt();
        reader.field("pieceBinarySha256InOrder", 4);
        List<String> pieceHashes = readBoundedStrings(reader, 512, "mutable-successor piece hash");
        reader.field("pieceBytesInvariant", 5);
        boolean pieceBytesInvariant = reader.readBoolean();
        reader.field("pieceCount", 6);
        int pieceCount = reader.readInt();
        reader.field("predecessor", 7);
        StartNbtReceipt predecessor = readStartNbtReceipt(reader);
        reader.field("predecessorReferences", 8);
        int predecessorReferences = reader.readInt();
        reader.field("referenceChunk", 9);
        StartChunk referenceChunk = readStartChunk(reader);
        reader.field("request", 10);
        StartRequest request = readStartRequest(reader);
        reader.field("selectedStartChunk", 11);
        StartChunk selectedStartChunk = readStartChunk(reader);
        reader.field("successor", 12);
        StartNbtReceipt successor = readStartNbtReceipt(reader);
        reader.field("successorReferences", 13);
        int successorReferences = reader.readInt();
        reader.endObject();
        return new MutableSuccessorEvidence(boundary, changedByteCount, changedByteOffset,
                encounterOrdinal, pieceHashes, pieceBytesInvariant, pieceCount, predecessor,
                predecessorReferences, referenceChunk, request, selectedStartChunk, successor,
                successorReferences);
    }

    private static void readMutableSuccessorSourceAuthority(JsonReader reader) {
        reader.beginObject();
        reader.field("acceptedStartGraphCarrierBytes", 0);
        require(reader.readInt() == START_GRAPH_RESOURCE_BYTES,
                "Ancient City mutable-successor accepted start carrier byte-count drift");
        reader.field("acceptedStartGraphCarrierSha256", 1);
        require(START_GRAPH_RESOURCE_SHA256.equals(reader.readString()),
                "Ancient City mutable-successor accepted start carrier SHA-256 drift");
        reader.field("acceptedStartGraphJsonSha256", 2);
        require(START_GRAPH_JSON_SHA256.equals(reader.readString()),
                "Ancient City mutable-successor accepted start JSON SHA-256 drift");
        reader.field("dataPackVersion", 3);
        require(reader.readInt() == 115, "Ancient City mutable-successor data-pack drift");
        reader.field("innerServerSha1", 4);
        require("2f1ef79f3cad10138ad18da45b265fe656624026".equals(reader.readString()),
                "Ancient City mutable-successor inner JAR SHA-1 drift");
        reader.field("innerServerSha256", 5);
        require("e5efad859e05767b507f43cf5adb28b6c8944ef7c0b612527f3e6ebdd2c4ace1".equals(
                reader.readString()), "Ancient City mutable-successor inner JAR SHA-256 drift");
        reader.field("javaExecutableSha256", 6);
        require("d7a7d5d7728e48138588c0c2c416672d436852799d1352b1cb5543c653c76bb0".equals(
                reader.readString()), "Ancient City mutable-successor java executable drift");
        reader.field("javaRuntimeVersion", 7);
        require(START_GRAPH_JAVA_VERSION.equals(reader.readString()),
                "Ancient City mutable-successor Java runtime drift");
        reader.field("javaSourceSha256", 8);
        require(MUTABLE_SUCCESSOR_JAVA_SOURCE_SHA256.equals(reader.readString()),
                "Ancient City mutable-successor Java source drift");
        reader.field("javacExecutableSha256", 9);
        require("6b8622d5de49e536c38e7277deca69808282061e20186064b5f192b9b32077ef".equals(
                reader.readString()), "Ancient City mutable-successor javac executable drift");
        reader.field("outerServerSha1", 10);
        require("06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61".equals(reader.readString()),
                "Ancient City mutable-successor outer JAR SHA-1 drift");
        reader.field("outerServerSha256", 11);
        require("d8e28d01a49ebd85aa992f9630fd12a1a990746c108a16baff6e95b21abdd082".equals(
                reader.readString()), "Ancient City mutable-successor outer JAR SHA-256 drift");
        reader.field("resourcePackVersion", 12);
        require(reader.readInt() == 95, "Ancient City mutable-successor resource-pack drift");
        reader.field("serverVersion", 13);
        require(START_GRAPH_SERVER_VERSION.equals(reader.readString()),
                "Ancient City mutable-successor server-version drift");
        reader.field("sourcePath", 14);
        require("scripts/mc-263/oracle-src/OfficialAncientCityStartGraphOracle.java".equals(
                reader.readString()), "Ancient City mutable-successor source path drift");
        reader.field("versionId", 15);
        require("26.3-snapshot-7".equals(reader.readString()),
                "Ancient City mutable-successor version-id drift");
        reader.field("worldVersion", 16);
        require(reader.readInt() == 5009, "Ancient City mutable-successor world-version drift");
        reader.field("wrapperSourceSha256", 17);
        require(MUTABLE_SUCCESSOR_WRAPPER_SOURCE_SHA256.equals(reader.readString()),
                "Ancient City mutable-successor wrapper source drift");
        reader.endObject();
    }

    private static void validateMutableSuccessorEvidence(MutableSuccessorEvidence evidence) {
        require(MUTABLE_SUCCESSOR_BOUNDARY.equals(evidence.boundary()),
                "Ancient City mutable-successor boundary drift");
        require(evidence.changedByteCount() == 1 && evidence.changedByteOffset() == 19
                        && evidence.encounterOrdinal() == 0 && evidence.pieceBytesInvariant(),
                "Ancient City mutable-successor delta metadata drift");
        require(evidence.referenceChunk().equals(evidence.selectedStartChunk()),
                "Ancient City mutable-successor reference/start chunk mismatch");
        require(evidence.pieceCount() > 0
                        && evidence.pieceBinarySha256InOrder().size() == evidence.pieceCount(),
                "Ancient City mutable-successor piece cardinality drift");
        for (String hash : evidence.pieceBinarySha256InOrder()) requireHash(hash);
        require(evidence.predecessorReferences() == 0 && evidence.successorReferences() == 1,
                "Ancient City mutable-successor reference-count metadata drift");
        require(evidence.successor().binaryLength() == evidence.predecessor().binaryLength()
                        && MUTABLE_SUCCESSOR_SHA256.equals(evidence.successor().binarySha256()),
                "Ancient City mutable-successor canonical successor identity drift");
        requireStartReferenceValue(evidence.predecessor().canonicalBytes(), 0,
                "Ancient City mutable-successor predecessor");
        requireStartReferenceValue(evidence.successor().canonicalBytes(), 1,
                "Ancient City mutable-successor successor");
        byte[] before = evidence.predecessor().canonicalBytes();
        byte[] after = evidence.successor().canonicalBytes();
        require(before.length == after.length, "Ancient City mutable-successor length drift");
        int differences = 0;
        int differenceOffset = -1;
        for (int index = 0; index < before.length; index++) {
            if (before[index] == after[index]) continue;
            differences++;
            differenceOffset = index;
        }
        require(differences == 1 && differenceOffset == evidence.changedByteOffset()
                        && before[differenceOffset] == 0 && after[differenceOffset] == 1,
                "Ancient City mutable-successor changed fields other than references");
    }

    private static void validateMutableSuccessorAgainstStartGraph(
            MutableSuccessorEvidence evidence, StartGraphCorpus startGraph) {
        require(startGraph != null && !startGraph.probes().isEmpty(),
                "Ancient City mutable-successor start graph is absent");
        StartProbe accepted = startGraph.probes().getFirst();
        require(evidence.request().equals(accepted.request()),
                "Ancient City mutable-successor request/start substitution");
        StartChunk selected = accepted.lawfulStartSelection().selectedChunk();
        require(evidence.referenceChunk().equals(selected)
                        && evidence.selectedStartChunk().equals(selected),
                "Ancient City mutable-successor reference chunk/start identity drift");
        require(evidence.pieceCount() == accepted.persistedNbt().pieces().size(),
                "Ancient City mutable-successor piece cardinality drift");
        for (int index = 0; index < evidence.pieceCount(); index++) {
            require(evidence.pieceBinarySha256InOrder().get(index).equals(
                            accepted.persistedNbt().pieces().get(index).binarySha256()),
                    "Ancient City mutable-successor piece order/bytes drift at " + index);
        }
        require(evidence.predecessor().equals(accepted.persistedNbt().structureStart()),
                "Ancient City mutable-successor predecessor substitution");
    }

    private static void requireStartReferenceValue(byte[] binary, int expected, String label) {
        StartNbtCursor cursor = new StartNbtCursor(binary);
        require(cursor.readU8() == 10 && cursor.readUtf().isEmpty(), label + " root drift");
        require(cursor.readU8() == 3 && "references".equals(cursor.readUtf()),
                label + " references tag/order/type drift");
        require(cursor.readInt() == expected, label + " references value drift");
    }

    private static StartGraphCorpus parseStartGraphEvidence(byte[] bytes) {
        JsonReader reader = new JsonReader(bytes);
        reader.beginObject();
        reader.field("javaVersion", 0);
        String javaVersion = reader.readString();
        reader.field("probes", 1);
        List<StartProbe> probes = readStartProbes(reader);
        reader.field("schema", 2);
        int schema = reader.readInt();
        reader.field("serverVersion", 3);
        String serverVersion = reader.readString();
        reader.endObject();
        reader.finish();
        StartGraphCorpus corpus = new StartGraphCorpus(javaVersion, probes, schema, serverVersion);
        validateStartGraphCorpus(corpus);
        return corpus;
    }

    private static List<StartProbe> readStartProbes(JsonReader reader) {
        ArrayList<StartProbe> probes = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < START_GRAPH_PROBE_COUNT, "too many Ancient City start graph probes");
            probes.add(readStartProbe(reader));
            index++;
        }
        reader.endArray();
        return List.copyOf(probes);
    }

    private static StartProbe readStartProbe(JsonReader reader) {
        reader.beginObject();
        reader.field("acceptedConnectorEdges", 0);
        List<StartEdge> edges = readStartEdges(reader);
        reader.field("aggregateBoundingBox", 1);
        StartBox aggregate = readStartBox(reader);
        reader.field("generationRng", 2);
        StartGenerationRng rng = readStartGenerationRng(reader);
        reader.field("lawfulStartSelection", 3);
        StartSelection selection = readStartSelection(reader);
        reader.field("persistedNbt", 4);
        StartPersistedNbt persisted = readStartPersistedNbt(reader);
        reader.field("piecesInAcceptedOrder", 5);
        List<StartPiece> pieces = readStartPieces(reader);
        reader.field("poolAliasesInAcceptedOrder", 6);
        requireEmptyArray(reader, "Ancient City start graph unexpectedly contains pool aliases");
        reader.field("request", 7);
        StartRequest request = readStartRequest(reader);
        reader.field("rootSample", 8);
        StartRootSample root = readStartRootSample(reader);
        reader.field("terrainProjectionQueries", 9);
        StartTerrainQueries terrain = readStartTerrainQueries(reader);
        reader.endObject();
        return new StartProbe(edges, aggregate, rng, selection, persisted, pieces,
                List.of(), request, root, terrain);
    }

    private static List<StartEdge> readStartEdges(JsonReader reader) {
        ArrayList<StartEdge> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 512, "Ancient City start edge bound exceeded");
            reader.beginObject();
            reader.field("officialCanAttach", 0);
            boolean official = reader.readBoolean();
            reader.field("placementPriority", 1);
            int placementPriority = reader.readInt();
            reader.field("resolvedAlias", 2);
            String resolvedAlias = reader.readString();
            reader.field("selectedPool", 3);
            String selectedPool = reader.readString();
            reader.field("selectionPriority", 4);
            int selectionPriority = reader.readInt();
            reader.field("sourceConnectorOrdinal", 5);
            int sourceConnector = reader.readInt();
            reader.field("sourceName", 6);
            String sourceName = reader.readString();
            reader.field("sourcePiece", 7);
            int sourcePiece = reader.readInt();
            reader.field("sourcePosition", 8);
            StartVec3 sourcePosition = readStartVec3(reader);
            reader.field("sourceTarget", 9);
            String sourceTarget = reader.readString();
            reader.field("targetConnectorOrdinal", 10);
            int targetConnector = reader.readInt();
            reader.field("targetName", 11);
            String targetName = reader.readString();
            reader.field("targetPiece", 12);
            int targetPiece = reader.readInt();
            reader.field("targetPosition", 13);
            StartVec3 targetPosition = readStartVec3(reader);
            reader.field("targetTarget", 14);
            String targetTarget = reader.readString();
            reader.endObject();
            result.add(new StartEdge(official, placementPriority, resolvedAlias, selectedPool,
                    selectionPriority, sourceConnector, sourceName, sourcePiece, sourcePosition,
                    sourceTarget, targetConnector, targetName, targetPiece, targetPosition,
                    targetTarget));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static StartGenerationRng readStartGenerationRng(JsonReader reader) {
        reader.beginObject();
        reader.field("continuationNextLongI64", 0);
        List<String> continuation = readBoundedStrings(reader, 8, "start RNG continuation");
        reader.field("finalLegacy48State", 1);
        String finalState = reader.readString();
        reader.field("finalLegacy48StateHex", 2);
        String finalStateHex = reader.readString();
        reader.field("operationCount", 3);
        int operationCount = reader.readInt();
        reader.field("operationTranscriptSha256", 4);
        String transcriptSha = requireHash(reader.readString());
        reader.field("operations", 5);
        int operationsStart = reader.mark();
        List<StartRngOperation> operations = readStartRngOperations(reader);
        int operationsEnd = reader.mark();
        require(transcriptSha.equals(reader.rawSha256(operationsStart, operationsEnd)),
                "Ancient City start RNG transcript hash drift");
        reader.field("worldgenCount", 6);
        int worldgenCount = reader.readInt();
        reader.endObject();
        return new StartGenerationRng(continuation, finalState, finalStateHex, operationCount,
                transcriptSha, operations, worldgenCount);
    }

    private static List<StartRngOperation> readStartRngOperations(JsonReader reader) {
        ArrayList<StartRngOperation> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 100_000, "Ancient City start RNG operation bound exceeded");
            reader.beginObject();
            reader.field("argument", 0);
            boolean stringArgument = reader.peek() == '"';
            String seedArgument = stringArgument ? reader.readString() : null;
            Integer bitsArgument = stringArgument ? null : reader.readInt();
            reader.field("operation", 1);
            String operation = reader.readString();
            reader.field("ordinal", 2);
            int ordinal = reader.readInt();
            if (operation.equals("setSeed")) {
                require(stringArgument, "Ancient City setSeed argument type drift");
                reader.endObject();
                result.add(new StartSetSeedOperation(seedArgument, operation, ordinal));
            } else if (operation.equals("nextBits")) {
                require(!stringArgument, "Ancient City nextBits argument type drift");
                reader.field("result", 3);
                int value = reader.readInt();
                reader.field("state48After", 4);
                String state = reader.readString();
                reader.endObject();
                result.add(new StartNextBitsOperation(bitsArgument, operation, ordinal, value, state));
            } else {
                throw invalid("unknown Ancient City start RNG operation: " + operation);
            }
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static StartSelection readStartSelection(JsonReader reader) {
        reader.beginObject();
        reader.field("biomeAtStub", 0);
        String biome = reader.readString();
        reader.field("exclusionZonePresent", 1);
        boolean exclusion = reader.readBoolean();
        reader.field("frequency", 2);
        double frequency = reader.readDouble();
        reader.field("frequencyReductionMethod", 3);
        String reduction = reader.readString();
        reader.field("placementAccepted", 4);
        boolean placementAccepted = reader.readBoolean();
        reader.field("salt", 5);
        int salt = reader.readInt();
        reader.field("selectedChunk", 6);
        StartChunk selectedChunk = readStartChunk(reader);
        reader.field("separation", 7);
        int separation = reader.readInt();
        reader.field("spacing", 8);
        int spacing = reader.readInt();
        reader.field("spreadType", 9);
        String spreadType = reader.readString();
        reader.field("structureSet", 10);
        String structureSet = reader.readString();
        reader.field("validBiome", 11);
        boolean validBiome = reader.readBoolean();
        reader.endObject();
        return new StartSelection(biome, exclusion, frequency, reduction, placementAccepted, salt,
                selectedChunk, separation, spacing, spreadType, structureSet, validBiome);
    }

    private static StartPersistedNbt readStartPersistedNbt(JsonReader reader) {
        reader.beginObject();
        reader.field("pieces", 0);
        ArrayList<StartNbtReceipt> pieces = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 512, "Ancient City persisted piece NBT bound exceeded");
            pieces.add(readStartNbtReceipt(reader));
            index++;
        }
        reader.endArray();
        reader.field("structureStart", 1);
        StartNbtReceipt start = readStartNbtReceipt(reader);
        reader.endObject();
        return new StartPersistedNbt(pieces, start);
    }

    private static StartNbtReceipt readStartNbtReceipt(JsonReader reader) {
        reader.beginObject();
        reader.field("binaryLength", 0);
        int binaryLength = reader.readInt();
        reader.field("binarySha256", 1);
        String binarySha = requireHash(reader.readString());
        reader.field("gzipBase64", 2);
        String gzipBase64 = reader.readString();
        reader.field("gzipLength", 3);
        int gzipLength = reader.readInt();
        reader.field("gzipSha256", 4);
        String gzipSha = requireHash(reader.readString());
        reader.endObject();
        byte[] gzip;
        try { gzip = Base64.getDecoder().decode(gzipBase64); }
        catch (IllegalArgumentException error) { throw invalid("malformed Ancient City start NBT base64", error); }
        require(gzip.length == gzipLength && sha256(gzip).equals(gzipSha),
                "Ancient City start NBT gzip receipt drift");
        byte[] binary = gunzipStartNbt(gzip);
        require(binary.length == binaryLength && sha256(binary).equals(binarySha),
                "Ancient City start NBT binary receipt drift");
        return new StartNbtReceipt(binaryLength, binarySha, gzipLength, gzipSha, binary);
    }

    private static byte[] gunzipStartNbt(byte[] gzip) {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total = Math.addExact(total, count);
                require(total <= MAX_START_NBT_BYTES, "Ancient City start NBT decompression bound exceeded");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException | ArithmeticException error) {
            throw invalid("failed to decode Ancient City start NBT", error);
        }
    }

    private static List<StartPiece> readStartPieces(JsonReader reader) {
        ArrayList<StartPiece> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 512, "Ancient City start piece bound exceeded");
            result.add(readStartPiece(reader));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static StartPiece readStartPiece(JsonReader reader) {
        reader.beginObject();
        reader.field("boundingBox", 0);
        StartBox box = readStartBox(reader);
        String second = reader.readFieldName(1);
        if (second.equals("childrenInPlacementOrder")) {
            List<StartPlacementChild> children = readStartPlacementChildren(reader);
            reader.field("depth", 2);
            int depth = reader.readInt();
            reader.field("groundLevelDelta", 3);
            int ground = reader.readInt();
            reader.field("junctionsInAcceptedOrder", 4);
            List<StartJunction> junctions = readStartJunctions(reader);
            reader.field("kind", 5);
            String kind = reader.readString();
            require(kind.equals("list"), "Ancient City list start-piece kind drift");
            reader.field("ordinal", 6);
            int ordinal = reader.readInt();
            reader.field("origin", 7);
            StartVec3 origin = readStartVec3(reader);
            reader.field("projection", 8);
            String projection = reader.readString();
            reader.field("rotation", 9);
            String rotation = reader.readString();
            reader.endObject();
            return new StartListPiece(box, children, depth, ground, junctions, kind, ordinal,
                    origin, projection, rotation);
        }
        require(second.equals("depth"), "Ancient City start-piece field order drift");
        int depth = reader.readInt();
        String third = reader.readFieldName(2);
        if (third.equals("feature")) {
            String feature = reader.readString();
            reader.field("groundLevelDelta", 3);
            int ground = reader.readInt();
            reader.field("junctionsInAcceptedOrder", 4);
            List<StartJunction> junctions = readStartJunctions(reader);
            reader.field("kind", 5);
            String kind = reader.readString();
            require(kind.equals("feature"), "Ancient City feature start-piece kind drift");
            reader.field("ordinal", 6);
            int ordinal = reader.readInt();
            reader.field("origin", 7);
            StartVec3 origin = readStartVec3(reader);
            reader.field("projection", 8);
            String projection = reader.readString();
            reader.field("rotation", 9);
            String rotation = reader.readString();
            reader.endObject();
            return new StartFeaturePiece(box, depth, feature, ground, junctions, kind, ordinal,
                    origin, projection, rotation);
        }
        require(third.equals("groundLevelDelta"), "Ancient City template start-piece field order drift");
        int ground = reader.readInt();
        reader.field("junctionsInAcceptedOrder", 3);
        List<StartJunction> junctions = readStartJunctions(reader);
        reader.field("kind", 4);
        String kind = reader.readString();
        require(kind.equals("template"), "Ancient City template start-piece kind drift");
        reader.field("ordinal", 5);
        int ordinal = reader.readInt();
        reader.field("origin", 6);
        StartVec3 origin = readStartVec3(reader);
        reader.field("processorOrder", 7);
        List<String> processorOrder = readBoundedStrings(reader, 8, "start processor order");
        reader.field("processorRegistryKey", 8);
        String processor = reader.readString();
        reader.field("projection", 9);
        String projection = reader.readString();
        reader.field("rotation", 10);
        String rotation = reader.readString();
        reader.field("template", 11);
        String template = reader.readString();
        reader.endObject();
        return new StartTemplatePiece(box, depth, ground, junctions, kind, ordinal, origin,
                processorOrder, processor, projection, rotation, template);
    }

    private static List<StartPlacementChild> readStartPlacementChildren(JsonReader reader) {
        ArrayList<StartPlacementChild> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 16, "Ancient City list-child bound exceeded");
            reader.beginObject();
            reader.field("kind", 0);
            String kind = reader.readString();
            reader.field("processorOrder", 1);
            List<String> order = readBoundedStrings(reader, 8, "list-child processor order");
            reader.field("processorRegistryKey", 2);
            String processor = reader.readString();
            reader.field("template", 3);
            String template = reader.readString();
            reader.endObject();
            require(kind.equals("template"), "Ancient City list child is not a template element");
            result.add(new StartPlacementChild(kind, order, processor, template));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static List<StartJunction> readStartJunctions(JsonReader reader) {
        ArrayList<StartJunction> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 64, "Ancient City junction bound exceeded");
            reader.beginObject();
            reader.field("deltaY", 0);
            int deltaY = reader.readInt();
            reader.field("destinationProjection", 1);
            String projection = reader.readString();
            reader.field("sourceGroundY", 2);
            int sourceGroundY = reader.readInt();
            reader.field("sourceX", 3);
            int sourceX = reader.readInt();
            reader.field("sourceZ", 4);
            int sourceZ = reader.readInt();
            reader.endObject();
            result.add(new StartJunction(deltaY, projection, sourceGroundY, sourceX, sourceZ));
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static StartRequest readStartRequest(JsonReader reader) {
        reader.beginObject();
        reader.field("regionX", 0);
        int regionX = reader.readInt();
        reader.field("regionZ", 1);
        int regionZ = reader.readInt();
        reader.field("worldSeedI64", 2);
        String worldSeed = reader.readString();
        reader.endObject();
        return new StartRequest(regionX, regionZ, worldSeed);
    }

    private static StartRootSample readStartRootSample(JsonReader reader) {
        reader.beginObject();
        reader.field("origin", 0);
        StartVec3 origin = readStartVec3(reader);
        reader.field("rotation", 1);
        String rotation = reader.readString();
        reader.field("sampledStartHeight", 2);
        int sampled = reader.readInt();
        reader.field("sampledStartPosition", 3);
        StartVec3 sampledPosition = readStartVec3(reader);
        reader.field("startPool", 4);
        String startPool = reader.readString();
        reader.field("stubPosition", 5);
        StartVec3 stub = readStartVec3(reader);
        reader.field("template", 6);
        String template = reader.readString();
        reader.endObject();
        return new StartRootSample(origin, rotation, sampled, sampledPosition, startPool, stub, template);
    }

    private static StartTerrainQueries readStartTerrainQueries(JsonReader reader) {
        reader.beginObject();
        reader.field("boundary", 0);
        String boundary = reader.readString();
        reader.field("queryCount", 1);
        int count = reader.readInt();
        reader.field("queryOrder", 2);
        ArrayList<StartProjectionQuery> queries = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < 4_096, "Ancient City projection-query bound exceeded");
            reader.beginObject();
            reader.field("heightmap", 0);
            String heightmap = reader.readString();
            reader.field("operation", 1);
            String operation = reader.readString();
            reader.field("ordinal", 2);
            int ordinal = reader.readInt();
            reader.field("result", 3);
            int result = reader.readInt();
            reader.field("x", 4);
            int x = reader.readInt();
            reader.field("z", 5);
            int z = reader.readInt();
            reader.endObject();
            queries.add(new StartProjectionQuery(heightmap, operation, ordinal, result, x, z));
            index++;
        }
        reader.endArray();
        reader.endObject();
        return new StartTerrainQueries(boundary, count, queries);
    }

    private static StartChunk readStartChunk(JsonReader reader) {
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City selected chunk x absent");
        int x = reader.readInt();
        require(reader.nextArrayValue(1), "Ancient City selected chunk z absent");
        int z = reader.readInt();
        require(!reader.nextArrayValue(2), "Ancient City selected chunk cardinality drift");
        reader.endArray();
        return new StartChunk(x, z);
    }

    private static StartVec3 readStartVec3(JsonReader reader) {
        reader.beginArray();
        require(reader.nextArrayValue(0), "Ancient City start vector x absent");
        int x = reader.readInt();
        require(reader.nextArrayValue(1), "Ancient City start vector y absent");
        int y = reader.readInt();
        require(reader.nextArrayValue(2), "Ancient City start vector z absent");
        int z = reader.readInt();
        require(!reader.nextArrayValue(3), "Ancient City start vector cardinality drift");
        reader.endArray();
        return new StartVec3(x, y, z);
    }

    private static StartBox readStartBox(JsonReader reader) {
        reader.beginArray();
        int[] values = new int[6];
        for (int index = 0; index < values.length; index++) {
            require(reader.nextArrayValue(index), "Ancient City start box value absent");
            values[index] = reader.readInt();
        }
        require(!reader.nextArrayValue(values.length), "Ancient City start box cardinality drift");
        reader.endArray();
        return new StartBox(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private static List<String> readBoundedStrings(JsonReader reader, int maximum, String label) {
        ArrayList<String> result = new ArrayList<>();
        reader.beginArray();
        int index = 0;
        while (reader.nextArrayValue(index)) {
            require(index < maximum, "Ancient City " + label + " bound exceeded");
            result.add(reader.readString());
            index++;
        }
        reader.endArray();
        return List.copyOf(result);
    }

    private static void validateStartGraphCorpus(StartGraphCorpus corpus) {
        require(corpus.javaVersion().equals(START_GRAPH_JAVA_VERSION),
                "Ancient City start graph Java version drift");
        require(corpus.schema() == START_GRAPH_SCHEMA, "Ancient City start graph schema drift");
        require(corpus.serverVersion().equals(START_GRAPH_SERVER_VERSION),
                "Ancient City start graph server version drift");
        require(corpus.probes().size() == START_GRAPH_PROBE_COUNT,
                "Ancient City start graph probe cardinality drift");
        HashSet<String> roots = new HashSet<>();
        HashSet<StartRequest> requests = new HashSet<>();
        for (int index = 0; index < corpus.probes().size(); index++) {
            validateStartProbe(index, corpus.probes().get(index));
            roots.add(corpus.probes().get(index).rootSample().template());
            require(requests.add(corpus.probes().get(index).request()),
                    "duplicate Ancient City start graph request");
        }
        require(roots.equals(Set.of(
                        "minecraft:ancient_city/city_center/city_center_1",
                        "minecraft:ancient_city/city_center/city_center_2",
                        "minecraft:ancient_city/city_center/city_center_3")),
                "Ancient City start graph root-template coverage drift");
    }

    private static void validateStartProbe(int index, StartProbe probe) {
        require(parseStartLong(probe.request().worldSeedI64(), "start graph world seed")
                        == START_GRAPH_WORLD_SEED,
                "Ancient City start graph world seed drift");
        validateStartSelection(probe.request(), probe.lawfulStartSelection());
        require(probe.poolAliasesInAcceptedOrder().isEmpty(),
                "Ancient City start graph alias surface is not empty");
        require(probe.piecesInAcceptedOrder().size() == START_GRAPH_PIECE_COUNTS[index],
                "Ancient City start piece count drift at " + index);
        require(probe.acceptedConnectorEdges().size() == START_GRAPH_EDGE_COUNTS[index],
                "Ancient City start edge count drift at " + index);
        validateStartRoot(probe);
        validateStartPiecesAndAggregate(probe);
        validateStartEdges(probe);
        validateStartTerrain(probe.terrainProjectionQueries());
        validateStartRng(index, probe);
        validateStartPersistedNbt(index, probe);
    }

    private static void validateStartSelection(StartRequest request, StartSelection selection) {
        require(selection.structureSet().equals("minecraft:ancient_cities")
                        && selection.spacing() == START_GRAPH_SPACING
                        && selection.separation() == START_GRAPH_SEPARATION
                        && selection.salt() == START_GRAPH_SALT
                        && selection.spreadType().equals("LINEAR")
                        && selection.frequency() == 1.0d
                        && selection.frequencyReductionMethod().equals("DEFAULT")
                        && !selection.exclusionZonePresent()
                        && selection.placementAccepted() && selection.validBiome()
                        && selection.biomeAtStub().equals("minecraft:deep_dark"),
                "Ancient City lawful start-selection metadata drift");
        StartChunk expected = potentialAncientChunk(START_GRAPH_WORLD_SEED,
                request.regionX(), request.regionZ());
        require(selection.selectedChunk().equals(expected),
                "Ancient City lawful start-selection chunk drift");
    }

    private static StartChunk potentialAncientChunk(long worldSeed, int regionX, int regionZ) {
        long placementSeed = (long) regionX * 341_873_128_712L
                + (long) regionZ * 132_897_987_541L + worldSeed + START_GRAPH_SALT;
        StartLegacy48 random = new StartLegacy48(placementSeed);
        int bound = START_GRAPH_SPACING - START_GRAPH_SEPARATION;
        return new StartChunk(Math.addExact(Math.multiplyExact(regionX, START_GRAPH_SPACING),
                        random.nextInt(bound)),
                Math.addExact(Math.multiplyExact(regionZ, START_GRAPH_SPACING), random.nextInt(bound)));
    }

    private static void validateStartRoot(StartProbe probe) {
        StartSelection selection = probe.lawfulStartSelection();
        StartRootSample root = probe.rootSample();
        StartChunk chunk = selection.selectedChunk();
        require(root.sampledStartHeight() == -27
                        && root.sampledStartPosition().equals(new StartVec3(
                                Math.multiplyExact(chunk.x(), 16), -27,
                                Math.multiplyExact(chunk.z(), 16)))
                        && root.startPool().equals("minecraft:ancient_city/city_center")
                        && Set.of("minecraft:ancient_city/city_center/city_center_1",
                                "minecraft:ancient_city/city_center/city_center_2",
                                "minecraft:ancient_city/city_center/city_center_3").contains(root.template())
                        && START_GRAPH_ROTATIONS.contains(root.rotation()),
                "Ancient City root sample drift");
        StartPiece first = probe.piecesInAcceptedOrder().getFirst();
        require(first instanceof StartTemplatePiece template
                        && template.ordinal() == 0 && template.depth() == 0
                        && template.template().equals(root.template())
                        && template.rotation().equals(root.rotation())
                        && template.origin().equals(root.origin()),
                "Ancient City root piece/sample identity drift");
        require(probe.aggregateBoundingBox().contains(root.stubPosition()),
                "Ancient City root stub escaped aggregate box");
    }

    private static void validateStartPiecesAndAggregate(StartProbe probe) {
        StartBox union = null;
        int maxDepth = 0;
        for (int index = 0; index < probe.piecesInAcceptedOrder().size(); index++) {
            StartPiece piece = probe.piecesInAcceptedOrder().get(index);
            require(piece.ordinal() == index, "Ancient City start piece ordinal drift");
            require(piece.depth() >= 0 && piece.depth() <= 7,
                    "Ancient City start piece depth outside 0..7");
            require(piece.projection().equals("rigid"), "Ancient City start piece projection drift");
            require(START_GRAPH_ROTATIONS.contains(piece.rotation()),
                    "Ancient City start piece rotation drift");
            require(piece.groundLevelDelta() >= -16 && piece.groundLevelDelta() <= 16,
                    "Ancient City start piece ground-level delta outside bound");
            require(piece.boundingBox().contains(piece.origin()),
                    "Ancient City start piece origin escaped its bounding box");
            for (StartJunction junction : piece.junctionsInAcceptedOrder()) {
                require(junction.destinationProjection().equals("rigid"),
                        "Ancient City start junction projection drift");
            }
            if (piece instanceof StartTemplatePiece template) {
                require(TEMPLATE_KEYS.contains(template.template()),
                        "unknown Ancient City start template: " + template.template());
                validateStartProcessor(template.processorRegistryKey(), template.processorOrder());
            } else if (piece instanceof StartFeaturePiece feature) {
                require(feature.feature().equals(SCULK_FEATURE),
                        "unknown Ancient City start feature: " + feature.feature());
                require(feature.boundingBox().isPoint(),
                        "Ancient City configured-feature start piece geometry drift");
            } else if (piece instanceof StartListPiece list) {
                require(!list.childrenInPlacementOrder().isEmpty(),
                        "Ancient City list start piece has no children");
                for (StartPlacementChild child : list.childrenInPlacementOrder()) {
                    require(TEMPLATE_KEYS.contains(child.template()),
                            "unknown Ancient City list-child template: " + child.template());
                    validateStartProcessor(child.processorRegistryKey(), child.processorOrder());
                }
            } else {
                throw invalid("unknown Ancient City start piece type");
            }
            union = union == null ? piece.boundingBox() : union.enclose(piece.boundingBox());
            maxDepth = Math.max(maxDepth, piece.depth());
        }
        require(maxDepth == 7, "Ancient City bounded start graph no longer reaches depth 7");
        require(union != null && union.inflate(12).equals(probe.aggregateBoundingBox()),
                "Ancient City aggregate bounding-box adjustment drift");
    }

    private static void validateStartProcessor(String processor, List<String> order) {
        require(PROCESSOR_LIST_IDENTITIES.contains(processor),
                "unknown Ancient City start processor list: " + processor);
        List<String> expected = switch (processor) {
            case "minecraft:ancient_city_start_degradation" -> List.of(
                    "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor",
                    "net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor");
            case "minecraft:ancient_city_generic_degradation",
                    "minecraft:ancient_city_walls_degradation" -> List.of(
                    "net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor",
                    "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor",
                    "net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor");
            case "inline" -> List.of();
            default -> throw invalid("unknown Ancient City start processor list: " + processor);
        };
        require(order.equals(expected), "Ancient City start processor runtime order drift for " + processor);
    }

    private static void validateStartEdges(StartProbe probe) {
        int pieceCount = probe.piecesInAcceptedOrder().size();
        int[] depths = new int[pieceCount];
        boolean[] reached = new boolean[pieceCount];
        reached[0] = true;
        for (int index = 0; index < probe.acceptedConnectorEdges().size(); index++) {
            StartEdge edge = probe.acceptedConnectorEdges().get(index);
            require(edge.targetPiece() == index + 1 && edge.sourcePiece() >= 0
                            && edge.sourcePiece() < edge.targetPiece()
                            && edge.targetPiece() < pieceCount && reached[edge.sourcePiece()]
                            && !reached[edge.targetPiece()],
                    "Ancient City start connector graph order drift at edge " + index);
            require(edge.officialCanAttach() && edge.selectionPriority() == 0
                            && edge.placementPriority() == 0
                            && edge.sourceConnectorOrdinal() >= 0 && edge.targetConnectorOrdinal() >= 0,
                    "Ancient City start connector metadata drift at edge " + index);
            require((POOL_KEYS.contains(edge.selectedPool()) || edge.selectedPool().equals("minecraft:empty"))
                            && edge.resolvedAlias().equals(edge.selectedPool()),
                    "unknown or aliased Ancient City start connector pool: " + edge.selectedPool());
            long distance = Math.abs((long) edge.sourcePosition().x() - edge.targetPosition().x())
                    + Math.abs((long) edge.sourcePosition().y() - edge.targetPosition().y())
                    + Math.abs((long) edge.sourcePosition().z() - edge.targetPosition().z());
            require(distance == 1L, "Ancient City start connector endpoints are not adjacent");
            require(probe.piecesInAcceptedOrder().get(edge.sourcePiece()).boundingBox()
                            .contains(edge.sourcePosition())
                            && probe.piecesInAcceptedOrder().get(edge.targetPiece()).boundingBox()
                                    .contains(edge.targetPosition()),
                    "Ancient City start connector endpoint escaped piece bounds");
            depths[edge.targetPiece()] = depths[edge.sourcePiece()] + 1;
            reached[edge.targetPiece()] = true;
        }
        for (int index = 0; index < pieceCount; index++) {
            require(reached[index] && probe.piecesInAcceptedOrder().get(index).depth() == depths[index],
                    "Ancient City start connector/depth closure drift at piece " + index);
        }
    }

    private static void validateStartTerrain(StartTerrainQueries terrain) {
        require(terrain.boundary().equals("official GenerationContext terrain projection queries in encounter order")
                        && terrain.queryCount() == terrain.queryOrder().size(),
                "Ancient City terrain-projection query boundary drift");
        require(terrain.queryOrder().isEmpty(),
                "Ancient City accepted rigid corpus unexpectedly issued terrain-projection queries");
    }

    private static void validateStartRng(int index, StartProbe probe) {
        StartGenerationRng rng = probe.generationRng();
        require(rng.operationCount() == START_GRAPH_OPERATION_COUNTS[index]
                        && rng.worldgenCount() == START_GRAPH_WORLDGEN_COUNTS[index]
                        && rng.operations().size() == rng.operationCount()
                        && rng.operationTranscriptSha256().equals(START_GRAPH_RNG_SHA256[index])
                        && rng.finalLegacy48State().equals(START_GRAPH_FINAL_STATE48[index])
                        && rng.continuationNextLongI64().size() == 8,
                "Ancient City start RNG receipt drift at " + index);
        long state = 0L;
        int nextCount = 0;
        int[] bootstrap = new int[4];
        for (int ordinal = 0; ordinal < rng.operations().size(); ordinal++) {
            StartRngOperation operation = rng.operations().get(ordinal);
            require(operation.ordinal() == ordinal, "Ancient City start RNG operation ordinal drift");
            if (operation instanceof StartSetSeedOperation setSeed) {
                long argument = parseStartLong(setSeed.argument(), "start RNG seed");
                if (ordinal == 0) require(argument == START_GRAPH_WORLD_SEED,
                        "Ancient City start RNG initial seed drift");
                if (ordinal == 5) {
                    long first = ((long) bootstrap[0] << 32) + bootstrap[1];
                    long second = ((long) bootstrap[2] << 32) + bootstrap[3];
                    StartChunk chunk = probe.lawfulStartSelection().selectedChunk();
                    long expected = ((long) chunk.x() * first) ^ ((long) chunk.z() * second)
                            ^ START_GRAPH_WORLD_SEED;
                    require(argument == expected, "Ancient City setLargeFeatureSeed receipt drift");
                }
                state = (argument ^ LEGACY_MULTIPLIER) & LEGACY_MASK;
            } else if (operation instanceof StartNextBitsOperation next) {
                require(next.argument() >= 1 && next.argument() <= 32,
                        "Ancient City nextBits width outside 1..32");
                state = advanceLegacyState(state);
                int expected = (int) (state >>> (48 - next.argument()));
                require(next.result() == expected
                                && parseStartLong(next.state48After(), "start RNG state") == state,
                        "Ancient City start RNG transcript/state drift at " + ordinal);
                if (ordinal >= 1 && ordinal <= 4) bootstrap[ordinal - 1] = next.result();
                nextCount++;
            } else {
                throw invalid("unknown Ancient City start RNG operation type");
            }
        }
        require(nextCount == rng.worldgenCount(), "Ancient City start RNG worldgen count drift");
        require(parseStartLong(rng.finalLegacy48State(), "final start RNG state") == state
                        && rng.finalLegacy48StateHex().equals(String.format("%012x", state)),
                "Ancient City final Legacy48 state drift");
        for (String expected : rng.continuationNextLongI64()) {
            long[] high = nextLegacyInt(state, 32);
            state = high[0];
            long[] low = nextLegacyInt(state, 32);
            state = low[0];
            long actual = ((long) (int) high[1] << 32) + (int) low[1];
            require(actual == parseStartLong(expected, "start RNG continuation"),
                    "Ancient City start RNG continuation drift");
        }
    }

    private static long advanceLegacyState(long state) {
        return (state * LEGACY_MULTIPLIER + LEGACY_ADDEND) & LEGACY_MASK;
    }

    private static long[] nextLegacyInt(long state, int bits) {
        long nextState = advanceLegacyState(state);
        return new long[] {nextState, (int) (nextState >>> (48 - bits))};
    }

    private static long parseStartLong(String value, String label) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw invalid("malformed Ancient City " + label, error); }
    }

    private static void validateStartPersistedNbt(int index, StartProbe probe) {
        StartPersistedNbt persisted = probe.persistedNbt();
        require(persisted.pieces().size() == probe.piecesInAcceptedOrder().size(),
                "Ancient City persisted piece NBT cardinality drift");
        StartNbtReceipt start = persisted.structureStart();
        require(start.binaryLength() == START_GRAPH_START_NBT_LENGTHS[index]
                        && start.binarySha256().equals(START_GRAPH_START_NBT_SHA256[index]),
                "Ancient City persisted structure-start NBT identity drift at " + index);
        for (int pieceIndex = 0; pieceIndex < persisted.pieces().size(); pieceIndex++) {
            validateStartPieceNbt(probe.piecesInAcceptedOrder().get(pieceIndex),
                    persisted.pieces().get(pieceIndex));
        }
        validateStructureStartNbt(probe);
    }

    private static void validateStartPieceNbt(StartPiece piece, StartNbtReceipt receipt) {
        byte[] bytes = receipt.canonicalBytes();
        StartNbtCursor cursor = new StartNbtCursor(bytes);
        require(cursor.readU8() == 10 && cursor.readUtf().isEmpty(),
                "Ancient City persisted piece NBT root drift");
        List<String> names = new ArrayList<>();
        boolean id = false;
        boolean x = false;
        boolean y = false;
        boolean z = false;
        boolean box = false;
        boolean rotation = false;
        boolean ground = false;
        boolean junctions = false;
        while (true) {
            int type = cursor.readU8();
            if (type == 0) break;
            String name = cursor.readUtf();
            names.add(name);
            switch (name) {
                case "BB" -> {
                    require(type == 11, "Ancient City piece BB NBT type drift");
                    int[] values = cursor.readIntArray();
                    require(Arrays.equals(values, piece.boundingBox().toArray()),
                            "Ancient City piece BB NBT value drift");
                    box = true;
                }
                case "PosX" -> { require(type == 3 && cursor.readInt() == piece.origin().x(),
                        "Ancient City piece PosX NBT drift"); x = true; }
                case "PosY" -> { require(type == 3 && cursor.readInt() == piece.origin().y(),
                        "Ancient City piece PosY NBT drift"); y = true; }
                case "PosZ" -> { require(type == 3 && cursor.readInt() == piece.origin().z(),
                        "Ancient City piece PosZ NBT drift"); z = true; }
                case "rotation" -> { require(type == 8 && cursor.readUtf().equals(piece.rotation()),
                        "Ancient City piece rotation NBT drift"); rotation = true; }
                case "id" -> { require(type == 8 && cursor.readUtf().equals("minecraft:jigsaw"),
                        "Ancient City piece id NBT drift"); id = true; }
                case "ground_level_delta" -> { require(type == 3
                                && cursor.readInt() == piece.groundLevelDelta(),
                        "Ancient City piece ground-level NBT drift"); ground = true; }
                case "junctions" -> {
                    require(type == 9, "Ancient City piece junction-list NBT type drift");
                    int elementType = cursor.readU8();
                    int count = cursor.readInt();
                    require(elementType == 10 && count == piece.junctionsInAcceptedOrder().size(),
                            "Ancient City piece junction-list NBT cardinality drift");
                    for (int i = 0; i < count; i++) skipStartNbtPayload(cursor, 10, 0);
                    junctions = true;
                }
                default -> skipStartNbtPayload(cursor, type, 0);
            }
        }
        require(cursor.finished() && id && x && y && z && box && rotation && ground && junctions,
                "Ancient City persisted piece NBT required-field drift");
        require(names.equals(List.of("BB", "PosZ", "PosX", "pool_element", "PosY", "rotation",
                        "id", "GD", "O", "ground_level_delta", "junctions")),
                "Ancient City persisted piece NBT key/order drift");
    }

    private static void validateStructureStartNbt(StartProbe probe) {
        byte[] bytes = probe.persistedNbt().structureStart().canonicalBytes();
        StartNbtCursor cursor = new StartNbtCursor(bytes);
        require(cursor.readU8() == 10 && cursor.readUtf().isEmpty(),
                "Ancient City structure-start NBT root drift");
        List<String> names = new ArrayList<>();
        boolean chunkX = false;
        boolean chunkZ = false;
        boolean id = false;
        boolean references = false;
        boolean children = false;
        while (true) {
            int type = cursor.readU8();
            if (type == 0) break;
            String name = cursor.readUtf();
            names.add(name);
            switch (name) {
                case "references" -> { require(type == 3 && cursor.readInt() == 0,
                        "Ancient City start references NBT drift"); references = true; }
                case "ChunkX" -> { require(type == 3
                                && cursor.readInt() == probe.lawfulStartSelection().selectedChunk().x(),
                        "Ancient City start ChunkX NBT drift"); chunkX = true; }
                case "ChunkZ" -> { require(type == 3
                                && cursor.readInt() == probe.lawfulStartSelection().selectedChunk().z(),
                        "Ancient City start ChunkZ NBT drift"); chunkZ = true; }
                case "id" -> { require(type == 8 && cursor.readUtf().equals("minecraft:ancient_city"),
                        "Ancient City start id NBT drift"); id = true; }
                case "Children" -> {
                    require(type == 9 && cursor.readU8() == 10,
                            "Ancient City start Children NBT type drift");
                    int count = cursor.readInt();
                    require(count == probe.persistedNbt().pieces().size(),
                            "Ancient City start Children NBT cardinality drift");
                    for (int i = 0; i < count; i++) {
                        int begin = cursor.position();
                        skipStartNbtPayload(cursor, 10, 0);
                        int end = cursor.position();
                        byte[] piece = probe.persistedNbt().pieces().get(i).canonicalBytes();
                        StartNbtCursor pieceCursor = new StartNbtCursor(piece);
                        require(pieceCursor.readU8() == 10 && pieceCursor.readUtf().isEmpty(),
                                "Ancient City persisted child piece root drift");
                        byte[] payload = Arrays.copyOfRange(piece, pieceCursor.position(), piece.length);
                        require(Arrays.equals(payload, Arrays.copyOfRange(bytes, begin, end)),
                                "Ancient City start/piece NBT encounter-order drift at " + i);
                    }
                    children = true;
                }
                default -> skipStartNbtPayload(cursor, type, 0);
            }
        }
        require(cursor.finished() && chunkX && chunkZ && id && references && children,
                "Ancient City structure-start NBT required-field drift");
        require(names.equals(List.of("references", "ChunkZ", "id", "Children", "ChunkX")),
                "Ancient City structure-start NBT key/order drift");
    }

    private static void skipStartNbtPayload(StartNbtCursor cursor, int type, int depth) {
        require(depth <= 64, "Ancient City start NBT nesting bound exceeded");
        switch (type) {
            case 1 -> cursor.skip(1);
            case 2 -> cursor.skip(2);
            case 3, 5 -> cursor.skip(4);
            case 4, 6 -> cursor.skip(8);
            case 7 -> cursor.skipArray(1);
            case 8 -> cursor.skipUtf();
            case 9 -> {
                int elementType = cursor.readU8();
                int count = cursor.readInt();
                require(count >= 0 && count <= 1_000_000, "Ancient City start NBT list bound exceeded");
                for (int i = 0; i < count; i++) skipStartNbtPayload(cursor, elementType, depth + 1);
            }
            case 10 -> {
                while (true) {
                    int nestedType = cursor.readU8();
                    if (nestedType == 0) break;
                    cursor.skipUtf();
                    skipStartNbtPayload(cursor, nestedType, depth + 1);
                }
            }
            case 11 -> cursor.skipArray(4);
            case 12 -> cursor.skipArray(8);
            default -> throw invalid("unknown Ancient City start NBT tag type: " + type);
        }
    }

    private static final class StartLegacy48 {
        private long state;
        private StartLegacy48(long seed) { state = (seed ^ LEGACY_MULTIPLIER) & LEGACY_MASK; }
        private int next(int bits) {
            state = advanceLegacyState(state);
            return (int) (state >>> (48 - bits));
        }
        private int nextInt(int bound) {
            require(bound > 0, "Ancient City placement RNG bound must be positive");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }
    }

    private static final class StartNbtCursor {
        private final byte[] bytes;
        private int offset;
        private StartNbtCursor(byte[] bytes) { this.bytes = bytes; }
        private int position() { return offset; }
        private boolean finished() { return offset == bytes.length; }
        private int readU8() { requireAvailable(1); return bytes[offset++] & 0xff; }
        private int readU16() { return (readU8() << 8) | readU8(); }
        private int readInt() {
            requireAvailable(4);
            int value = ((bytes[offset] & 0xff) << 24) | ((bytes[offset + 1] & 0xff) << 16)
                    | ((bytes[offset + 2] & 0xff) << 8) | (bytes[offset + 3] & 0xff);
            offset += 4;
            return value;
        }
        private String readUtf() {
            int length = readU16();
            requireAvailable(length);
            String value = new String(bytes, offset, length, StandardCharsets.UTF_8);
            offset += length;
            return value;
        }
        private void skipUtf() { int length = readU16(); skip(length); }
        private int[] readIntArray() {
            int count = readInt();
            require(count >= 0 && count <= 1_000_000, "Ancient City start NBT int-array bound exceeded");
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = readInt();
            return result;
        }
        private void skipArray(int elementBytes) {
            int count = readInt();
            require(count >= 0 && count <= 1_000_000, "Ancient City start NBT array bound exceeded");
            long bytesToSkip = (long) count * elementBytes;
            require(bytesToSkip <= Integer.MAX_VALUE, "Ancient City start NBT array byte bound exceeded");
            skip((int) bytesToSkip);
        }
        private void skip(int count) { requireAvailable(count); offset += count; }
        private void requireAvailable(int count) {
            require(count >= 0 && offset <= bytes.length - count, "truncated Ancient City start NBT");
        }
    }

    record StartGraphCorpus(String javaVersion, List<StartProbe> probes, int schema,
            String serverVersion) {
        StartGraphCorpus { probes = List.copyOf(probes); }
    }

    record StartProbe(List<StartEdge> acceptedConnectorEdges, StartBox aggregateBoundingBox,
            StartGenerationRng generationRng, StartSelection lawfulStartSelection,
            StartPersistedNbt persistedNbt, List<StartPiece> piecesInAcceptedOrder,
            List<String> poolAliasesInAcceptedOrder, StartRequest request, StartRootSample rootSample,
            StartTerrainQueries terrainProjectionQueries) {
        StartProbe {
            acceptedConnectorEdges = List.copyOf(acceptedConnectorEdges);
            piecesInAcceptedOrder = List.copyOf(piecesInAcceptedOrder);
            poolAliasesInAcceptedOrder = List.copyOf(poolAliasesInAcceptedOrder);
        }
    }

    record StartRequest(int regionX, int regionZ, String worldSeedI64) {}
    record StartChunk(int x, int z) {}
    record StartSelection(String biomeAtStub, boolean exclusionZonePresent, double frequency,
            String frequencyReductionMethod, boolean placementAccepted, int salt,
            StartChunk selectedChunk, int separation, int spacing, String spreadType,
            String structureSet, boolean validBiome) {}
    record StartRootSample(StartVec3 origin, String rotation, int sampledStartHeight,
            StartVec3 sampledStartPosition, String startPool, StartVec3 stubPosition, String template) {}

    sealed interface StartPiece permits StartTemplatePiece, StartFeaturePiece, StartListPiece {
        StartBox boundingBox();
        int depth();
        int groundLevelDelta();
        List<StartJunction> junctionsInAcceptedOrder();
        String kind();
        int ordinal();
        StartVec3 origin();
        String projection();
        String rotation();
    }

    record StartTemplatePiece(StartBox boundingBox, int depth, int groundLevelDelta,
            List<StartJunction> junctionsInAcceptedOrder, String kind, int ordinal, StartVec3 origin,
            List<String> processorOrder, String processorRegistryKey, String projection,
            String rotation, String template) implements StartPiece {
        StartTemplatePiece {
            junctionsInAcceptedOrder = List.copyOf(junctionsInAcceptedOrder);
            processorOrder = List.copyOf(processorOrder);
        }
    }

    record StartFeaturePiece(StartBox boundingBox, int depth, String feature, int groundLevelDelta,
            List<StartJunction> junctionsInAcceptedOrder, String kind, int ordinal, StartVec3 origin,
            String projection, String rotation) implements StartPiece {
        StartFeaturePiece { junctionsInAcceptedOrder = List.copyOf(junctionsInAcceptedOrder); }
    }

    record StartListPiece(StartBox boundingBox, List<StartPlacementChild> childrenInPlacementOrder,
            int depth, int groundLevelDelta, List<StartJunction> junctionsInAcceptedOrder, String kind,
            int ordinal, StartVec3 origin, String projection, String rotation) implements StartPiece {
        StartListPiece {
            childrenInPlacementOrder = List.copyOf(childrenInPlacementOrder);
            junctionsInAcceptedOrder = List.copyOf(junctionsInAcceptedOrder);
        }
    }

    record StartPlacementChild(String kind, List<String> processorOrder, String processorRegistryKey,
            String template) {
        StartPlacementChild { processorOrder = List.copyOf(processorOrder); }
    }
    record StartJunction(int deltaY, String destinationProjection, int sourceGroundY,
            int sourceX, int sourceZ) {}
    record StartEdge(boolean officialCanAttach, int placementPriority, String resolvedAlias,
            String selectedPool, int selectionPriority, int sourceConnectorOrdinal, String sourceName,
            int sourcePiece, StartVec3 sourcePosition, String sourceTarget, int targetConnectorOrdinal,
            String targetName, int targetPiece, StartVec3 targetPosition, String targetTarget) {}

    record StartGenerationRng(List<String> continuationNextLongI64, String finalLegacy48State,
            String finalLegacy48StateHex, int operationCount, String operationTranscriptSha256,
            List<StartRngOperation> operations, int worldgenCount) {
        StartGenerationRng {
            continuationNextLongI64 = List.copyOf(continuationNextLongI64);
            operations = List.copyOf(operations);
        }
    }
    sealed interface StartRngOperation permits StartSetSeedOperation, StartNextBitsOperation {
        String operation();
        int ordinal();
    }
    record StartSetSeedOperation(String argument, String operation, int ordinal)
            implements StartRngOperation {}
    record StartNextBitsOperation(int argument, String operation, int ordinal, int result,
            String state48After) implements StartRngOperation {}

    record StartTerrainQueries(String boundary, int queryCount, List<StartProjectionQuery> queryOrder) {
        StartTerrainQueries { queryOrder = List.copyOf(queryOrder); }
    }
    record StartProjectionQuery(String heightmap, String operation, int ordinal, int result, int x, int z) {}
    record MutableSuccessorEvidence(String boundary, int changedByteCount, int changedByteOffset,
            int encounterOrdinal, List<String> pieceBinarySha256InOrder, boolean pieceBytesInvariant,
            int pieceCount, StartNbtReceipt predecessor, int predecessorReferences,
            StartChunk referenceChunk, StartRequest request, StartChunk selectedStartChunk,
            StartNbtReceipt successor, int successorReferences) {
        MutableSuccessorEvidence {
            pieceBinarySha256InOrder = List.copyOf(pieceBinarySha256InOrder);
        }
    }

    record StartPersistedNbt(List<StartNbtReceipt> pieces, StartNbtReceipt structureStart) {
        StartPersistedNbt { pieces = List.copyOf(pieces); }
    }

    record StartNbtReceipt(int binaryLength, String binarySha256, int gzipLength, String gzipSha256,
            byte[] canonicalBytes) {
        StartNbtReceipt { canonicalBytes = canonicalBytes.clone(); }
        @Override public byte[] canonicalBytes() { return canonicalBytes.clone(); }
        byte[] mutableSuccessor() {
            byte[] successor = canonicalBytes.clone();
            requireStartReferenceValue(successor, 0,
                    "Ancient City persisted start predecessor");
            require(successor.length > 19 && successor[19] == 0,
                    "Ancient City persisted start references byte offset drift");
            successor[19] = 1;
            requireStartReferenceValue(successor, 1,
                    "Ancient City persisted start successor");
            return successor;
        }
        @Override public boolean equals(Object other) {
            return other instanceof StartNbtReceipt value
                    && binaryLength == value.binaryLength && gzipLength == value.gzipLength
                    && binarySha256.equals(value.binarySha256) && gzipSha256.equals(value.gzipSha256)
                    && Arrays.equals(canonicalBytes, value.canonicalBytes);
        }
        @Override public int hashCode() {
            int result = Integer.hashCode(binaryLength);
            result = 31 * result + binarySha256.hashCode();
            result = 31 * result + Integer.hashCode(gzipLength);
            result = 31 * result + gzipSha256.hashCode();
            return 31 * result + Arrays.hashCode(canonicalBytes);
        }
    }

    record RuntimeClassPin(String runtimeClass, String sha256) {}
    record BentLootCallerRng(String kind, long worldSeedI64, String setup, String draw,
            int totalDrawCount, int lootDrawCount, String encounterRule) {}
    record ChunkCoord(int x, int z) {}
    record BentLootBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BentLootBox {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ,
                    "invalid Ancient City BENT/LOOT bounding box");
        }
    }
    record XoroshiroState(int worldgenCount, long seedLoI64, long seedHiI64,
            List<Long> continuationNextLongI64) {
        XoroshiroState { continuationNextLongI64 = List.copyOf(continuationNextLongI64); }
    }
    record PlacementDraw(int ordinal, long signedSeedI64, XoroshiroState before,
            XoroshiroState after) {}
    record PlacementRng(long worldSeedI64, ChunkCoord chunk, long decorationSeedI64,
            int structureIndex, int generationStepOrdinal, XoroshiroState initial,
            List<PlacementDraw> draws, XoroshiroState finished) {
        PlacementRng { draws = List.copyOf(draws); }
    }
    private record BentLootRow(int commandOrdinal, Opcode opcode, String blockEntityType,
            String runtimeClass, Pos localPosition, Pos placedPosition,
            OptionalInt callerRngEncounterOrdinal, OptionalLong signedSeedI64,
            Optional<String> lootTable, int canonicalNbtLength, String canonicalNbtSha256) {
        BentLootRow {
            callerRngEncounterOrdinal = Objects.requireNonNull(callerRngEncounterOrdinal);
            signedSeedI64 = Objects.requireNonNull(signedSeedI64);
            lootTable = Objects.requireNonNull(lootTable);
        }
    }
    private record ParsedBentLootPlacement(String template, int templateOrdinal,
            String processorList, Pos origin, BentLootBox boundingBox, ChunkCoord ownerChunk,
            PlacementRng placementRng, List<BentLootRow> rowsInCommandOrder) {
        ParsedBentLootPlacement { rowsInCommandOrder = List.copyOf(rowsInCommandOrder); }
    }
    private record BentLootCorpus(int schema, String receiptId, String javaVersion,
            String serverVersion, String sourceExecutionReceipt,
            String sourceExecutionPayloadSha256, List<RuntimeClassPin> runtimeClassPinsInOrder,
            BentLootCallerRng callerRng,
            List<ParsedBentLootPlacement> placementsInExecutionCorpusOrder, String boundary) {
        BentLootCorpus {
            runtimeClassPinsInOrder = List.copyOf(runtimeClassPinsInOrder);
            placementsInExecutionCorpusOrder = List.copyOf(placementsInExecutionCorpusOrder);
        }
    }
    record CanonicalBlockEntityEvidence(String template, int commandOrdinal, Opcode opcode,
            String blockEntityType, String runtimeClass, Pos localPosition, Pos placedPosition,
            OptionalInt callerRngEncounterOrdinal, OptionalLong signedSeedI64,
            Optional<String> lootTable, byte[] canonicalNbt) {
        CanonicalBlockEntityEvidence {
            callerRngEncounterOrdinal = Objects.requireNonNull(callerRngEncounterOrdinal);
            signedSeedI64 = Objects.requireNonNull(signedSeedI64);
            lootTable = Objects.requireNonNull(lootTable);
            canonicalNbt = canonicalNbt.clone();
        }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        String canonicalNbtSha256() { return sha256(canonicalNbt); }
    }
    record LootSeedEvidence(int placementOrdinal, String template, int commandOrdinal,
            Pos placedPosition, String lootTable, int callerRngEncounterOrdinal,
            long signedLootTableSeed) {}
    record BentLootPlacement(String template, int templateOrdinal, String processorList,
            Pos origin, BentLootBox boundingBox, ChunkCoord ownerChunk, PlacementRng placementRng,
            List<CanonicalBlockEntityEvidence> rowsInCommandOrder) {
        BentLootPlacement { rowsInCommandOrder = List.copyOf(rowsInCommandOrder); }
    }

    record StartVec3(int x, int y, int z) {}
    record StartBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        StartBox {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ,
                    "invalid Ancient City start bounding box");
        }
        boolean contains(StartVec3 value) {
            return value.x() >= minX && value.x() <= maxX && value.y() >= minY && value.y() <= maxY
                    && value.z() >= minZ && value.z() <= maxZ;
        }
        boolean isPoint() { return minX == maxX && minY == maxY && minZ == maxZ; }
        StartBox enclose(StartBox other) {
            return new StartBox(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        StartBox inflate(int amount) {
            return new StartBox(Math.subtractExact(minX, amount), Math.subtractExact(minY, amount),
                    Math.subtractExact(minZ, amount), Math.addExact(maxX, amount),
                    Math.addExact(maxY, amount), Math.addExact(maxZ, amount));
        }
        int[] toArray() { return new int[] {minX, minY, minZ, maxX, maxY, maxZ}; }
    }

    enum SourceStatus {
        PRESENT("present"), EXPECTED_ABSENT("expected_absent");
        private final String wire;
        SourceStatus(String wire) { this.wire = wire; }
        static SourceStatus fromWire(String wire) {
            for (SourceStatus value : values()) if (value.wire.equals(wire)) return value;
            throw invalid("unknown Ancient City template source status: " + wire);
        }
    }

    enum Opcode {
        BENT, JIGSAW, LOOT_CONTAINER, RUN;
        static Opcode fromWire(String value) {
            try { return valueOf(value); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Ancient City command opcode: " + value);
            }
        }
    }

    enum Axis { X, Y, Z }

    enum Direction {
        DOWN(Axis.Y), UP(Axis.Y), NORTH(Axis.Z), SOUTH(Axis.Z), WEST(Axis.X), EAST(Axis.X);
        private final Axis axis;
        Direction(Axis axis) { this.axis = axis; }
        static Direction fromWire(String value) {
            try { return valueOf(value); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Ancient City connector direction: " + value);
            }
        }
    }

    enum ProcessorKind {
        RULE("minecraft:rule", "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor"),
        PROTECTED_BLOCKS("minecraft:protected_blocks",
                "net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor"),
        BLOCK_IGNORE("minecraft:block_ignore",
                "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor"),
        JIGSAW_REPLACEMENT("minecraft:jigsaw_replacement",
                "net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor"),
        BLOCK_ROT("minecraft:block_rot",
                "net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor");
        private final String wireName;
        private final String runtimeClass;
        ProcessorKind(String wireName, String runtimeClass) {
            this.wireName = wireName;
            this.runtimeClass = runtimeClass;
        }
    }

    enum BentSemantic {
        COMPARATOR_OUTPUT, EMPTY_SCULK_SENSOR, EMPTY_LECTERN, EMPTY_SKELETON_SKULL,
        EMPTY_CAMPFIRE, FIXED_CHEST_CONTENTS, FURNACE_PAYLOAD
    }

    sealed interface PoolElement permits SingleElement, ListElement, FeatureElement, EmptyElement {
        int ordinal();
        int weight();
    }

    record SingleElement(int ordinal, int weight, String template, String processorList,
            List<String> placementProcessorsInOrder) implements PoolElement {
        SingleElement { placementProcessorsInOrder = List.copyOf(placementProcessorsInOrder); }
    }

    record SingleChild(int ordinal, String template, String processorList,
            List<String> placementProcessorsInOrder) {
        SingleChild { placementProcessorsInOrder = List.copyOf(placementProcessorsInOrder); }
    }

    record ListElement(int ordinal, int weight, List<SingleChild> childrenInDeclaredOrder)
            implements PoolElement {
        ListElement { childrenInDeclaredOrder = List.copyOf(childrenInDeclaredOrder); }
    }

    record FeatureElement(int ordinal, int weight, String feature) implements PoolElement {}
    record EmptyElement(int ordinal, int weight) implements PoolElement {}

    record Pool(String key, int rawElementCount, int expandedWeight,
            List<PoolElement> elementsInDeclaredOrder) {
        Pool { elementsInDeclaredOrder = List.copyOf(elementsInDeclaredOrder); }
    }

    record ProcessorListSpec(String identity, boolean registered, List<String> processorTypesInOrder,
            List<String> processorSemanticIdentitiesInOrder) {
        ProcessorListSpec {
            processorTypesInOrder = List.copyOf(processorTypesInOrder);
            processorSemanticIdentitiesInOrder = List.copyOf(processorSemanticIdentitiesInOrder);
        }
    }

    sealed interface ProcessorCodec permits RuleCodec, ProtectedBlocksCodec, BlockIgnoreCodec,
            JigsawReplacementCodec, BlockRotCodec {}

    record RuleCodec(List<Rule> rules) implements ProcessorCodec {
        RuleCodec { rules = List.copyOf(rules); }
    }
    record ProtectedBlocksCodec(String valueTag) implements ProcessorCodec {}
    record BlockIgnoreCodec(List<String> blocks) implements ProcessorCodec {
        BlockIgnoreCodec { blocks = List.copyOf(blocks); }
    }
    record JigsawReplacementCodec() implements ProcessorCodec {}
    record BlockRotCodec(String rottableBlocks, List<String> resolvedRottableBlocks,
            double integrity) implements ProcessorCodec {
        BlockRotCodec {
            resolvedRottableBlocks = List.copyOf(resolvedRottableBlocks);
            require(Mc263AncientCityProductionAuthority.ROTTABLE_BLOCKS_TAG.equals(rottableBlocks),
                    "Ancient City block-rot tag authority drift");
            require(!resolvedRottableBlocks.isEmpty()
                            && new HashSet<>(resolvedRottableBlocks).size()
                                    == resolvedRottableBlocks.size(),
                    "Ancient City block-rot resolved membership drift");
        }

        boolean isRottableExactState(String exactState) {
            String block = exactBlockIdentity(exactState);
            return resolvedRottableBlocks.contains(block);
        }

        boolean keepExactState(String exactState, ProcessorFloatRandom random) {
            if (!isRottableExactState(exactState)) return true;
            require(random != null, "Ancient City block-rot random source is absent");
            return random.nextFloat() <= (float) integrity;
        }
    }

    @FunctionalInterface
    interface ProcessorFloatRandom {
        float nextFloat();
    }

    private static String exactBlockIdentity(String exactState) {
        require(exactState != null && !exactState.isEmpty(),
                "unknown Ancient City block-rot exact state");
        int open = exactState.indexOf('[');
        String block = open < 0 ? exactState : exactState.substring(0, open);
        require(validResourceLocation(block), "unknown Ancient City block-rot block identity");
        if (open < 0) {
            require(exactState.indexOf(']') < 0, "malformed Ancient City block-rot exact state");
            return block;
        }
        require(exactState.endsWith("]") && exactState.indexOf('[', open + 1) < 0
                        && exactState.indexOf(']', open + 1) == exactState.length() - 1
                        && exactState.substring(open + 1, exactState.length() - 1).length() > 0,
                "malformed Ancient City block-rot exact state");
        String properties = exactState.substring(open + 1, exactState.length() - 1);
        HashSet<String> names = new HashSet<>();
        for (String assignment : properties.split(",", -1)) {
            int equals = assignment.indexOf('=');
            require(equals > 0 && equals == assignment.lastIndexOf('=')
                            && equals < assignment.length() - 1,
                    "malformed Ancient City block-rot state property");
            require(names.add(assignment.substring(0, equals)),
                    "duplicate Ancient City block-rot state property");
        }
        return block;
    }

    private static boolean validResourceLocation(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1) {
            return false;
        }
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

    record Rule(String block, double probability, String inputPredicateType,
            String locationPredicateType, String outputState) {}

    sealed interface ProcessorRng permits NoneRng, BlockRotRng, RuleRng {}
    record NoneRng() implements ProcessorRng {}
    record PositionSeed(int xMultiplierI32, long zMultiplierI64, long squareMultiplierI64,
            long linearMultiplierI64, int arithmeticRightShift) {}
    record BlockRotRng(String rottableBlocks, double integrity, PositionSeed positionSeed,
            String randomSource, String draw, String keepWhen) implements ProcessorRng {}
    record PredicateDraw(String predicateType, Optional<Double> probability) {
        PredicateDraw { probability = probability == null ? Optional.empty() : probability; }
        PredicateDraw(String predicateType, double probability) {
            this(predicateType, Optional.of(probability));
        }
    }
    record RuleRng(List<PredicateDraw> predicatesInCodecOrder,
            List<PredicateDraw> randomPredicatesInCodecOrder, String ruleOrder,
            String randomSource, String unsetSettingsRandom, PositionSeed positionSeed)
            implements ProcessorRng {
        RuleRng {
            predicatesInCodecOrder = List.copyOf(predicatesInCodecOrder);
            randomPredicatesInCodecOrder = List.copyOf(randomPredicatesInCodecOrder);
        }
    }
    record ProcessorSpec(String identity, ProcessorKind kind, String runtimeClass,
            ProcessorCodec codec, ProcessorRng rng) {}

    record SculkPatch(int chargeCount, int amountPerCharge, int spreadAttempts,
            int growthRounds, int spreadRounds) {}
    record BlockStateSpec(String id, String waterlogged, String shrieking, String canSummon) {}
    record UniformInt(int minInclusive, int maxInclusive) {}
    record OffsetDistribution(UniformInt x, UniformInt y, UniformInt z) {}
    record CatalystFeature(String state, double chance, Pos supportOffset) {}
    record ShriekerFeature(BlockStateSpec state, UniformInt count, OffsetDistribution offset,
            String requiredTag, Pos supportOffset) {}
    record OverlayFeature(CatalystFeature catalyst, ShriekerFeature shrieker) {}
    record SculkSequence(SculkPatch patch, OverlayFeature overlay) {}
    record FeatureIdentity(String type, String runtimeClass) {}
    record ConfiguredFeature(String registryKey, SculkSequence sequence,
            List<FeatureIdentity> transitiveFeaturesInOrder, boolean placementModifiersConsumeRandom,
            String configuredFeatureRandom) {
        ConfiguredFeature { transitiveFeaturesInOrder = List.copyOf(transitiveFeaturesInOrder); }
    }
    private record ShriekerPredicate(String requiredTag, Pos supportOffset) {}

    sealed interface Command permits Run, Jigsaw, LootContainer, Bent {
        int ordinal();
        Opcode opcode();
    }
    record Run(int ordinal, Pos start, Pos delta, int count, int state) implements Command {
        @Override public Opcode opcode() { return Opcode.RUN; }
    }
    record Jigsaw(int ordinal, int state, int connectorOrdinal, String finalState) implements Command {
        @Override public Opcode opcode() { return Opcode.JIGSAW; }
    }
    record LootContainer(int ordinal, Pos position, int state, String blockEntityType,
            String lootTable) implements Command {
        @Override public Opcode opcode() { return Opcode.LOOT_CONTAINER; }
    }
    record Bent(int ordinal, Pos position, int state, BentPayload payload) implements Command {
        @Override public Opcode opcode() { return Opcode.BENT; }
    }

    sealed interface BentPayload permits ComparatorOutput, EmptySculkSensor, EmptyLectern,
            EmptySkeletonSkull, EmptyCampfire, FixedChestContents, FurnacePayload {
        String blockEntityType();
        BentSemantic semantic();
    }
    record ComparatorOutput(String blockEntityType, int outputSignal) implements BentPayload {
        @Override public BentSemantic semantic() { return BentSemantic.COMPARATOR_OUTPUT; }
    }
    record EmptySculkSensor(String blockEntityType) implements BentPayload {
        @Override public BentSemantic semantic() { return BentSemantic.EMPTY_SCULK_SENSOR; }
    }
    record EmptyLectern(String blockEntityType) implements BentPayload {
        @Override public BentSemantic semantic() { return BentSemantic.EMPTY_LECTERN; }
    }
    record EmptySkeletonSkull(String blockEntityType) implements BentPayload {
        @Override public BentSemantic semantic() { return BentSemantic.EMPTY_SKELETON_SKULL; }
    }
    record EmptyCampfire(String blockEntityType, List<Integer> cookingTimes,
            List<Integer> cookingTotalTimes) implements BentPayload {
        EmptyCampfire {
            cookingTimes = List.copyOf(cookingTimes);
            cookingTotalTimes = List.copyOf(cookingTotalTimes);
        }
        @Override public BentSemantic semantic() { return BentSemantic.EMPTY_CAMPFIRE; }
    }
    record ItemStack(int slot, String item, int count) {}
    record RecipeUse(String recipe, int count) {}
    record FixedChestContents(String blockEntityType, List<ItemStack> items) implements BentPayload {
        FixedChestContents { items = List.copyOf(items); }
        @Override public BentSemantic semantic() { return BentSemantic.FIXED_CHEST_CONTENTS; }
    }
    record FurnacePayload(String blockEntityType, List<ItemStack> items, List<RecipeUse> recipesUsed,
            int litTotalTime, int cookingTimeSpent, int cookingTotalTime, int litTimeRemaining)
            implements BentPayload {
        FurnacePayload {
            items = List.copyOf(items);
            recipesUsed = List.copyOf(recipesUsed);
        }
        @Override public BentSemantic semantic() { return BentSemantic.FURNACE_PAYLOAD; }
    }

    record Pos(int x, int y, int z) {}
    record Size(int x, int y, int z) {
        boolean contains(Pos pos) {
            return pos.x() >= 0 && pos.x() < x && pos.y() >= 0 && pos.y() < y
                    && pos.z() >= 0 && pos.z() < z;
        }
    }
    record Connector(int ordinal, Pos position, String state, Direction front, Direction top,
            String name, String target, String pool, int placementPriority, int selectionPriority) {}
    record Template(String id, SourceStatus status, Optional<Size> size, List<String> stateTable,
            int blockCount, List<Command> commands, List<Connector> connectors, int entityCount) {
        Template {
            size = size == null ? Optional.empty() : size;
            stateTable = List.copyOf(stateTable);
            commands = List.copyOf(commands);
            connectors = List.copyOf(connectors);
        }
    }

    record SidecarCount(String identity, int occurrences) {}
    record TypedSidecars(List<SidecarCount> bent, List<SidecarCount> loot,
            List<SidecarCount> spwn, List<SidecarCount> ents) {
        TypedSidecars {
            bent = List.copyOf(bent);
            loot = List.copyOf(loot);
            spwn = List.copyOf(spwn);
            ents = List.copyOf(ents);
        }
    }

    record Aggregate(int poolCount, int rawPoolElementCount, int expandedPoolWeight,
            int templateIdentityCount, int presentTemplateCount, int expectedAbsentTemplateCount,
            int blockCount, int commandCount, int connectorCount, int stateIdentityCount,
            String poolsSha256, String templatesSha256, String processorListsSha256,
            String processorSemanticsSha256, String configuredFeatureSha256,
            String sidecarsSha256, String stateClosureSha256) {}

    private record ParsedEvidence(List<String> poolKeys, List<Pool> pools,
            List<ProcessorListSpec> processorLists, List<ProcessorSpec> processors,
            ConfiguredFeature configuredFeature, List<Template> templates, TypedSidecars sidecars,
            List<String> states, Aggregate aggregate) {
        ParsedEvidence {
            poolKeys = List.copyOf(poolKeys);
            pools = List.copyOf(pools);
            processorLists = List.copyOf(processorLists);
            processors = List.copyOf(processors);
            templates = List.copyOf(templates);
            states = List.copyOf(states);
        }
    }

    private record CommandParse(List<Command> commands, int expandedBlocks,
            EnumMap<Opcode, Integer> opcodeCounts) {
        CommandParse {
            commands = List.copyOf(commands);
            opcodeCounts = new EnumMap<>(opcodeCounts);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }

    private static IllegalStateException invalid(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static final class JsonReader {
        private static final int MAX_STRING_CHARS = 65_536;
        private final String text;
        private int offset;

        private JsonReader(byte[] bytes) {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException error) {
                throw invalid("Ancient City grammar is not UTF-8");
            }
        }

        private int mark() { skipWhitespace(); return offset; }

        private String rawSha256(int start, int end) {
            require(start >= 0 && end >= start && end <= text.length(),
                    "Ancient City raw hash bounds drift");
            return sha256(text.substring(start, end).getBytes(StandardCharsets.UTF_8));
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
                throw invalid("Ancient City JSON field order/schema drift: expected "
                        + expected + ", got " + actual);
            }
            expect(':');
        }

        private boolean hasNextObjectField(int index) {
            skipWhitespace();
            if (index == 0) return peek() != '}';
            return peek() != '}';
        }

        private String readFieldName(int index) {
            skipWhitespace();
            if (index > 0) {
                expectRaw(',');
                skipWhitespace();
                if (peek() == '}') throw invalid("Ancient City JSON trailing object comma");
            }
            String name = readString();
            expect(':');
            return name;
        }

        private boolean nextArrayValue(int index) {
            skipWhitespace();
            if (index == 0) return peek() != ']';
            if (peek() == ']') return false;
            expectRaw(',');
            skipWhitespace();
            if (peek() == ']') throw invalid("Ancient City JSON trailing array comma");
            return true;
        }

        private String readString() {
            skipWhitespace();
            expectRaw('"');
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char current = text.charAt(offset++);
                if (current == '"') return value.toString();
                if (current < 0x20) throw invalid("Ancient City JSON control character");
                if (current != '\\') {
                    value.append(current);
                } else {
                    if (offset >= text.length()) throw invalid("truncated Ancient City JSON escape");
                    char escaped = text.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> value.append(readUnicodeEscape());
                        default -> throw invalid("unknown Ancient City JSON escape");
                    }
                }
                if (value.length() > MAX_STRING_CHARS) {
                    throw invalid("Ancient City JSON string exceeds bound");
                }
            }
            throw invalid("truncated Ancient City JSON string");
        }

        private char readUnicodeEscape() {
            if (offset + 4 > text.length()) throw invalid("truncated Ancient City Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(text.charAt(offset++), 16);
                if (digit < 0) throw invalid("malformed Ancient City Unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private int readInt() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Ancient City JSON integer type drift");
            try { return Integer.parseInt(value); }
            catch (NumberFormatException error) { throw invalid("Ancient City integer overflow"); }
        }

        private long readLong() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Ancient City JSON long type drift");
            try { return Long.parseLong(value); }
            catch (NumberFormatException error) { throw invalid("Ancient City long overflow"); }
        }

        private double readDouble() {
            String value = readNumber();
            try {
                double result = Double.parseDouble(value);
                require(Double.isFinite(result), "non-finite Ancient City JSON number");
                return result;
            } catch (NumberFormatException error) {
                throw invalid("malformed Ancient City JSON number");
            }
        }

        private String readNumber() {
            skipWhitespace();
            int start = offset;
            if (peek() == '-') offset++;
            if (offset >= text.length()) throw invalid("truncated Ancient City JSON number");
            char first = text.charAt(offset);
            if (first == '0') {
                offset++;
                if (offset < text.length() && Character.isDigit(text.charAt(offset))) {
                    throw invalid("Ancient City JSON leading zero");
                }
            } else if (first >= '1' && first <= '9') {
                do { offset++; } while (offset < text.length() && Character.isDigit(text.charAt(offset)));
            } else {
                throw invalid("malformed Ancient City JSON number");
            }
            if (offset < text.length() && text.charAt(offset) == '.') {
                offset++;
                int fraction = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                if (offset == fraction) throw invalid("malformed Ancient City JSON fraction");
            }
            if (offset < text.length() && (text.charAt(offset) == 'e' || text.charAt(offset) == 'E')) {
                offset++;
                if (offset < text.length() && (text.charAt(offset) == '+' || text.charAt(offset) == '-')) offset++;
                int exponent = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                if (offset == exponent) throw invalid("malformed Ancient City JSON exponent");
            }
            return text.substring(start, offset);
        }

        private boolean readBoolean() {
            skipWhitespace();
            if (text.startsWith("true", offset)) { offset += 4; return true; }
            if (text.startsWith("false", offset)) { offset += 5; return false; }
            throw invalid("Ancient City JSON boolean type drift");
        }

        private void skipValue() {
            skipWhitespace();
            char value = peek();
            if (value == '"') {
                readString();
            } else if (value == '{') {
                beginObject();
                HashSet<String> fields = new HashSet<>();
                int index = 0;
                while (hasNextObjectField(index)) {
                    String field = readFieldName(index);
                    require(fields.add(field), "duplicate Ancient City JSON field: " + field);
                    skipValue();
                    index++;
                }
                endObject();
            } else if (value == '[') {
                beginArray();
                int index = 0;
                while (nextArrayValue(index)) {
                    skipValue();
                    index++;
                }
                endArray();
            } else if (value == 't' || value == 'f') {
                readBoolean();
            } else if (value == '-' || Character.isDigit(value)) {
                readNumber();
            } else {
                throw invalid("unknown Ancient City JSON value near offset " + offset);
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            expectRaw(expected);
        }

        private void expectRaw(char expected) {
            if (offset >= text.length() || text.charAt(offset) != expected) {
                throw invalid("Ancient City JSON syntax drift near offset " + offset
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
            require(offset == text.length(), "trailing Ancient City JSON data");
        }
    }
}
