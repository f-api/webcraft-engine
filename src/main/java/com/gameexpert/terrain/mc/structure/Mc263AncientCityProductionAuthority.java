package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compact production authority for the pinned Minecraft 26.3-snapshot-7 Ancient City grammar.
 *
 * <p>The source artifact is derived only from the authenticated E2A execution corpus, but strips
 * receipt prose, per-section JSON hashes, and all bounded witness coordinates. Template programs
 * remain local-coordinate RUN/JIGSAW/BENT/LOOT commands; pools, connectors and processor bindings
 * remain resource identities. The one main-resource payload is shared with the Rust authority.
 * Runtime code never consults the multi-megabyte test corpus.</p>
 */
final class Mc263AncientCityProductionAuthority {
    static final String RESOURCE = "/mc263/ancient-city-production-authority-v1.txt";
    static final int RESOURCE_BYTES = 807_494;
    static final String RESOURCE_SHA256 =
            "0b9d057f4890bdcdac03169707a5c5cbc7af0579912ed5fdb90908102227add1";
    static final String ROTTABLE_BLOCKS_RESOURCE =
            "/mc263/ancient-city-replaceable-tag-authority-v1.txt";
    static final int ROTTABLE_BLOCKS_RESOURCE_BYTES = 705;
    static final String ROTTABLE_BLOCKS_RESOURCE_SHA256 =
            "184a501842f354018326de0dfc854584d4f703c2d564e079f399e7d5edeb09fe";
    static final String ROTTABLE_BLOCKS_MEMBERS_SHA256 =
            "7868e77ba988bf9ce919025c547fa2b9e75999197ad3f431b1de6481e3f5d113";
    static final String ROTTABLE_BLOCKS_SOURCE_PATH =
            "data/minecraft/tags/block/ancient_city_replaceable.json";
    static final String ROTTABLE_BLOCKS_SOURCE_SHA256 =
            "dfe7692dddb25cd1945334741782d2b85048aad2d20fc8e1106585a23ec34acf";
    static final String ROTTABLE_BLOCKS_TAG = "#minecraft:ancient_city_replaceable";
    static final String PROTECTED_BLOCKS_RESOURCE =
            "/mc263/ancient-city-protected-tag-authority-v1.txt";
    static final int PROTECTED_BLOCKS_RESOURCE_BYTES = 485;
    static final String PROTECTED_BLOCKS_RESOURCE_SHA256 =
            "a34693c66aa1fb42f00cc9efbb55f6fcb7d9b12c50489a5a3f9135946f905044";
    static final String PROTECTED_BLOCKS_MEMBERS_SHA256 =
            "67808dcc146a9650d467225228d3829ffad39885b7e25dec8105e1dd18166a81";
    static final String PROTECTED_BLOCKS_SOURCE_PATH =
            "data/minecraft/tags/block/features_cannot_replace.json";
    static final String PROTECTED_BLOCKS_SOURCE_SHA256 =
            "a117e03f857eacf14ff5862ba6f885bb02b476a844b683c2be61211202a9a10d";
    static final String PROTECTED_BLOCKS_TAG = "#minecraft:features_cannot_replace";
    static final String SOURCE_CORPUS_SHA256 = Mc263AncientCityGrammar.RESOURCE_SHA256;
    private static final String FORMAT = "ANC263P1";
    private static final String SOURCE_EVIDENCE_SHA256 =
            "3525fa926c141a98b555a6e931c943d60c2859afc01c73c1bae039b6bf2d24a6";
    private static final String FEATURE_KEY = "minecraft:sculk_patch_ancient_city";
    private static final int POOL_COUNT = 7;
    private static final int TEMPLATE_COUNT = 58;
    private static final int STATE_COUNT = 294;
    private static final String ROTTABLE_BLOCKS_FORMAT = "ANC263TAG1";
    private static final List<String> EXPECTED_ROTTABLE_BLOCKS = List.of(
            "minecraft:deepslate",
            "minecraft:deepslate_bricks",
            "minecraft:deepslate_tiles",
            "minecraft:deepslate_brick_slab",
            "minecraft:deepslate_tile_slab",
            "minecraft:deepslate_brick_stairs",
            "minecraft:deepslate_tile_wall",
            "minecraft:deepslate_brick_wall",
            "minecraft:cobbled_deepslate",
            "minecraft:cracked_deepslate_bricks",
            "minecraft:cracked_deepslate_tiles",
            "minecraft:gray_wool");
    private static final RottableBlocksAuthority ROTTABLE_BLOCKS_AUTHORITY =
            loadRottableBlocksAuthority();
    private static final List<String> EXPECTED_PROTECTED_BLOCKS = List.of(
            "minecraft:bedrock", "minecraft:spawner", "minecraft:chest",
            "minecraft:end_portal_frame", "minecraft:reinforced_deepslate",
            "minecraft:trial_spawner", "minecraft:vault");
    private static final RottableBlocksAuthority PROTECTED_BLOCKS_AUTHORITY =
            loadProtectedBlocksAuthority();

    private Mc263AncientCityProductionAuthority() { }

    static List<String> rottableBlocks() {
        return ROTTABLE_BLOCKS_AUTHORITY.members();
    }

    static List<String> protectedBlocks() {
        return PROTECTED_BLOCKS_AUTHORITY.members();
    }

    static boolean stateInAuthenticatedTag(String exactState, String tagKey) {
        require(exactState != null && tagKey != null,
                "Ancient City authenticated tag query is incomplete");
        int properties = exactState.indexOf('[');
        String block = properties < 0 ? exactState : exactState.substring(0, properties);
        if (ROTTABLE_BLOCKS_TAG.equals(tagKey)) {
            return ROTTABLE_BLOCKS_AUTHORITY.members().contains(block);
        }
        if (PROTECTED_BLOCKS_TAG.equals(tagKey)) {
            return PROTECTED_BLOCKS_AUTHORITY.members().contains(block);
        }
        throw invalid("unknown Ancient City authenticated block tag: " + tagKey);
    }

    static boolean supportsAuthenticatedTag(String tagKey) {
        return ROTTABLE_BLOCKS_TAG.equals(tagKey) || PROTECTED_BLOCKS_TAG.equals(tagKey);
    }

    static List<String> decodeRottableBlocksAuthorityForTest(byte[] bytes) {
        return parseRottableBlocksAuthority(bytes).members();
    }

    static Mc263AncientCityGrammar load() {
        byte[] bytes;
        try (InputStream input = Mc263AncientCityProductionAuthority.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw invalid("Ancient City production authority is absent: " + RESOURCE);
            bytes = input.readAllBytes();
        } catch (IOException error) {
            throw invalid("failed to read Ancient City production authority", error);
        }
        require(bytes.length == RESOURCE_BYTES, "Ancient City production authority byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)),
                "Ancient City production authority SHA-256 drift");
        Rows rows = new Rows(bytes);
        String[] header = rows.take("H", 6);
        require(FORMAT.equals(header[1]) && parseInt(header[2], "schema") == 1,
                "Ancient City production authority format drift");
        require(parseInt(header[3], "source bytes") == Mc263AncientCityGrammar.RESOURCE_BYTES
                        && SOURCE_CORPUS_SHA256.equals(header[4])
                        && SOURCE_EVIDENCE_SHA256.equals(header[5]),
                "Ancient City production authority source receipt drift");

        ArrayList<String> poolKeys = new ArrayList<>(POOL_COUNT);
        for (int index = 0; index < POOL_COUNT; index++) poolKeys.add(rows.take("K", 2)[1]);
        ArrayList<Mc263AncientCityGrammar.Pool> pools = new ArrayList<>(POOL_COUNT);
        for (int poolIndex = 0; poolIndex < POOL_COUNT; poolIndex++) {
            String[] pool = rows.take("P", 5);
            String key = pool[1];
            require(key.equals(poolKeys.get(poolIndex)), "Ancient City production pool order drift");
            int raw = parseInt(pool[2], "raw pool count");
            int expanded = parseInt(pool[3], "expanded pool weight");
            int elementCount = parseInt(pool[4], "pool element count");
            require(raw == elementCount && raw >= 0, "Ancient City production pool cardinality drift");
            ArrayList<Mc263AncientCityGrammar.PoolElement> elements = new ArrayList<>(elementCount);
            int totalWeight = 0;
            for (int ordinal = 0; ordinal < elementCount; ordinal++) {
                String[] element = rows.take("E");
                require(element.length >= 5, "malformed Ancient City production pool element");
                String kind = element[1];
                int actualOrdinal = parseInt(element[2], "pool element ordinal");
                int weight = parseInt(element[3], "pool element weight");
                require(actualOrdinal == ordinal && weight > 0,
                        "Ancient City production pool element order/weight drift");
                totalWeight = Math.addExact(totalWeight, weight);
                switch (kind) {
                    case "S" -> {
                        require(element.length == 8 && "rigid".equals(element[4]),
                                "Ancient City production single element drift");
                        elements.add(new Mc263AncientCityGrammar.SingleElement(actualOrdinal, weight,
                                element[5], element[6], csv(element[7])));
                    }
                    case "L" -> {
                        require(element.length == 6 && "rigid".equals(element[4]),
                                "Ancient City production list element drift");
                        int childCount = parseInt(element[5], "list child count");
                        require(childCount > 0, "Ancient City production list element is empty");
                        ArrayList<Mc263AncientCityGrammar.SingleChild> children =
                                new ArrayList<>(childCount);
                        for (int child = 0; child < childCount; child++) {
                            String[] value = rows.take("C", 6);
                            require(parseInt(value[1], "list child ordinal") == child
                                            && "rigid".equals(value[2]),
                                    "Ancient City production list child drift");
                            children.add(new Mc263AncientCityGrammar.SingleChild(child, value[3],
                                    value[4], csv(value[5])));
                        }
                        elements.add(new Mc263AncientCityGrammar.ListElement(actualOrdinal, weight,
                                children));
                    }
                    case "F" -> {
                        require(element.length == 6 && "rigid".equals(element[4])
                                        && FEATURE_KEY.equals(element[5]),
                                "Ancient City production feature element drift");
                        elements.add(new Mc263AncientCityGrammar.FeatureElement(actualOrdinal, weight,
                                element[5]));
                    }
                    case "X" -> {
                        require(element.length == 5 && "terrain_matching".equals(element[4]),
                                "Ancient City production empty element drift");
                        elements.add(new Mc263AncientCityGrammar.EmptyElement(actualOrdinal, weight));
                    }
                    default -> throw invalid("unknown Ancient City production pool element kind: " + kind);
                }
            }
            require(totalWeight == expanded, "Ancient City production expanded pool weight drift");
            pools.add(new Mc263AncientCityGrammar.Pool(key, raw, expanded, elements));
        }

        ArrayList<Mc263AncientCityGrammar.Template> templates = new ArrayList<>(TEMPLATE_COUNT);
        for (int templateOrdinal = 0; templateOrdinal < TEMPLATE_COUNT; templateOrdinal++) {
            String[] head = rows.take("T", 11);
            boolean present = switch (head[2]) {
                case "P" -> true;
                case "A" -> false;
                default -> throw invalid("unknown Ancient City production template source status");
            };
            int sx = parseInt(head[3], "template size x");
            int sy = parseInt(head[4], "template size y");
            int sz = parseInt(head[5], "template size z");
            int blockCount = parseInt(head[6], "template block count");
            int stateCount = parseInt(head[7], "template state count");
            int commandCount = parseInt(head[8], "template command count");
            int connectorCount = parseInt(head[9], "template connector count");
            int entityCount = parseInt(head[10], "template entity count");
            Optional<Mc263AncientCityGrammar.Size> size = present
                    ? Optional.of(new Mc263AncientCityGrammar.Size(sx, sy, sz)) : Optional.empty();
            require(present ? sx > 0 && sy > 0 && sz > 0 : sx == 0 && sy == 0 && sz == 0,
                    "Ancient City production template size/status drift");
            ArrayList<String> states = new ArrayList<>(stateCount);
            for (int index = 0; index < stateCount; index++) states.add(rows.take("S", 2)[1]);
            ArrayList<Mc263AncientCityGrammar.Command> commands = new ArrayList<>(commandCount);
            int expandedBlocks = 0;
            for (int index = 0; index < commandCount; index++) {
                String[] command = rows.takeAny();
                Mc263AncientCityGrammar.Command parsed;
                switch (command[0]) {
                    case "R" -> {
                        require(command.length == 10, "malformed Ancient City production RUN");
                        int count = parseInt(command[8], "RUN count");
                        parsed = new Mc263AncientCityGrammar.Run(
                                parseInt(command[1], "RUN ordinal"), pos(command, 2),
                                pos(command, 5), count, stateIndex(command[9], states));
                        expandedBlocks = Math.addExact(expandedBlocks, count);
                    }
                    case "J" -> {
                        require(command.length == 5, "malformed Ancient City production JIGSAW");
                        parsed = new Mc263AncientCityGrammar.Jigsaw(
                                parseInt(command[1], "JIGSAW ordinal"), stateIndex(command[2], states),
                                parseInt(command[3], "connector ordinal"), command[4]);
                        expandedBlocks = Math.addExact(expandedBlocks, 1);
                    }
                    case "O" -> {
                        require(command.length == 8, "malformed Ancient City production LOOT");
                        parsed = new Mc263AncientCityGrammar.LootContainer(
                                parseInt(command[1], "LOOT ordinal"), pos(command, 2),
                                stateIndex(command[5], states), command[6], command[7]);
                        expandedBlocks = Math.addExact(expandedBlocks, 1);
                    }
                    case "B" -> {
                        require(command.length == 9, "malformed Ancient City production BENT");
                        parsed = new Mc263AncientCityGrammar.Bent(
                                parseInt(command[1], "BENT ordinal"), pos(command, 2),
                                stateIndex(command[5], states), bent(command[6], command[7], command[8]));
                        expandedBlocks = Math.addExact(expandedBlocks, 1);
                    }
                    default -> throw invalid("unknown Ancient City production command opcode: " + command[0]);
                }
                commands.add(parsed);
            }
            require(expandedBlocks == blockCount,
                    "Ancient City production arithmetic command grammar is lossy");
            ArrayList<Mc263AncientCityGrammar.Connector> connectors =
                    new ArrayList<>(connectorCount);
            for (int index = 0; index < connectorCount; index++) {
                String[] connector = rows.take("N", 13);
                int ordinal = parseInt(connector[1], "connector ordinal");
                require(ordinal == index, "Ancient City production connector order drift");
                connectors.add(new Mc263AncientCityGrammar.Connector(ordinal, pos(connector, 2),
                        connector[5], Mc263AncientCityGrammar.Direction.valueOf(connector[6]),
                        Mc263AncientCityGrammar.Direction.valueOf(connector[7]), connector[8],
                        connector[9], connector[10], parseInt(connector[11], "placement priority"),
                        parseInt(connector[12], "selection priority")));
            }
            if (!present) {
                require(stateCount == 0 && blockCount == 0 && commandCount == 0
                                && connectorCount == 0 && entityCount == 0,
                        "Ancient City expected-absent production template is nonempty");
            }
            templates.add(new Mc263AncientCityGrammar.Template(head[1],
                    present ? Mc263AncientCityGrammar.SourceStatus.PRESENT
                            : Mc263AncientCityGrammar.SourceStatus.EXPECTED_ABSENT,
                    size, states, blockCount, commands, connectors, entityCount));
        }

        ArrayList<String> globalStates = new ArrayList<>(STATE_COUNT);
        for (int index = 0; index < STATE_COUNT; index++) globalStates.add(rows.take("G", 2)[1]);
        String[] aggregateRow = rows.take("A", 18);
        Mc263AncientCityGrammar.Aggregate aggregate = new Mc263AncientCityGrammar.Aggregate(
                parseInt(aggregateRow[1], "pool count"),
                parseInt(aggregateRow[2], "raw pool element count"),
                parseInt(aggregateRow[3], "expanded pool weight"),
                parseInt(aggregateRow[4], "template identity count"),
                parseInt(aggregateRow[5], "present template count"),
                parseInt(aggregateRow[6], "expected absent template count"),
                parseInt(aggregateRow[7], "block count"),
                parseInt(aggregateRow[8], "command count"),
                parseInt(aggregateRow[9], "connector count"),
                parseInt(aggregateRow[10], "state identity count"), aggregateRow[11],
                aggregateRow[12], aggregateRow[13], aggregateRow[14], aggregateRow[15],
                aggregateRow[16], aggregateRow[17]);
        rows.finish();

        List<Mc263AncientCityGrammar.ProcessorListSpec> processorLists = processorLists();
        List<Mc263AncientCityGrammar.ProcessorSpec> processors = processorSemantics();
        Mc263AncientCityGrammar.ConfiguredFeature configuredFeature = configuredFeature();
        Mc263AncientCityGrammar.TypedSidecars sidecars = typedSidecars();
        validateClosure(poolKeys, pools, processorLists, processors, configuredFeature,
                templates, sidecars, globalStates, aggregate);
        return new Mc263AncientCityGrammar(poolKeys, pools, processorLists, processors,
                configuredFeature, templates, sidecars, globalStates, aggregate,
                List.of(), List.of(), List.of());
    }

    /** Canonical raw NBT for block entities created by the configured Ancient City sculk feature. */
    static byte[] canonicalFeatureBlockEntityNbt(String blockEntityType,
            Mc263AncientCityGrammar.Pos position) {
        require(position != null, "Ancient City feature BENT position is absent");
        Mc263StructureBlockEntityNbtAuthority.Facts facts = switch (blockEntityType) {
            case "minecraft:sculk_catalyst" ->
                    new Mc263StructureBlockEntityNbtAuthority.SculkCatalyst(blockEntityType);
            case "minecraft:sculk_sensor" ->
                    new Mc263StructureBlockEntityNbtAuthority.SculkSensor(blockEntityType);
            case "minecraft:sculk_shrieker" ->
                    new Mc263StructureBlockEntityNbtAuthority.SculkShrieker(blockEntityType, 0);
            default -> throw invalid(
                    "unknown Ancient City configured-feature BENT type: " + blockEntityType);
        };
        return Mc263StructureBlockEntityNbtAuthority.render(
                position.x(), position.y(), position.z(), facts);
    }

    private static List<Mc263AncientCityGrammar.ProcessorListSpec> processorLists() {
        return List.of(
                new Mc263AncientCityGrammar.ProcessorListSpec(
                        "minecraft:ancient_city_start_degradation", true,
                        List.of("minecraft:rule", "minecraft:protected_blocks"),
                        List.of("minecraft:rule:1770be7ab86fd0249ecce1ee49b86483c15ed67fdcca7fc037e75de99846244b",
                                "minecraft:protected_blocks:5a695f52c8dbf2af06c6b6146fada8fa999b3a3ac2030c9a1fc222b571b8f3e2")),
                new Mc263AncientCityGrammar.ProcessorListSpec(
                        "minecraft:ancient_city_generic_degradation", true,
                        List.of("minecraft:block_rot", "minecraft:rule", "minecraft:protected_blocks"),
                        List.of("minecraft:block_rot:3be74ecfda1988c24fb449bd37e184a09d71aa85738603cf08ced7335c261738",
                                "minecraft:rule:1770be7ab86fd0249ecce1ee49b86483c15ed67fdcca7fc037e75de99846244b",
                                "minecraft:protected_blocks:5a695f52c8dbf2af06c6b6146fada8fa999b3a3ac2030c9a1fc222b571b8f3e2")),
                new Mc263AncientCityGrammar.ProcessorListSpec("inline", false, List.of(), List.of()),
                new Mc263AncientCityGrammar.ProcessorListSpec(
                        "minecraft:ancient_city_walls_degradation", true,
                        List.of("minecraft:block_rot", "minecraft:rule", "minecraft:protected_blocks"),
                        List.of("minecraft:block_rot:3be74ecfda1988c24fb449bd37e184a09d71aa85738603cf08ced7335c261738",
                                "minecraft:rule:7aee803a659d7cf91ca5dc93e68588b21455314fd4b25c04ebc2bac1ee9cf934",
                                "minecraft:protected_blocks:5a695f52c8dbf2af06c6b6146fada8fa999b3a3ac2030c9a1fc222b571b8f3e2")));
    }

    private static List<Mc263AncientCityGrammar.ProcessorSpec> processorSemantics() {
        Mc263AncientCityGrammar.PositionSeed seed =
                new Mc263AncientCityGrammar.PositionSeed(3_129_871, 116_129_781L,
                        42_317_861L, 11L, 16);
        Mc263AncientCityGrammar.RuleCodec startRules = ruleCodec(List.of(
                rule("minecraft:deepslate_bricks", 0.3D, "minecraft:cracked_deepslate_bricks"),
                rule("minecraft:deepslate_tiles", 0.3D, "minecraft:cracked_deepslate_tiles"),
                rule("minecraft:soul_lantern", 0.05D, "minecraft:air")));
        Mc263AncientCityGrammar.RuleCodec wallRules = ruleCodec(List.of(
                rule("minecraft:deepslate_bricks", 0.3D, "minecraft:cracked_deepslate_bricks"),
                rule("minecraft:deepslate_tiles", 0.3D, "minecraft:cracked_deepslate_tiles"),
                rule("minecraft:deepslate_tile_slab", 0.3D, "minecraft:air"),
                rule("minecraft:soul_lantern", 0.05D, "minecraft:air")));
        Mc263AncientCityGrammar.BlockRotCodec rot = new Mc263AncientCityGrammar.BlockRotCodec(
                ROTTABLE_BLOCKS_AUTHORITY.tag(), ROTTABLE_BLOCKS_AUTHORITY.members(), 0.95D);
        return List.of(
                new Mc263AncientCityGrammar.ProcessorSpec(
                        "minecraft:rule:1770be7ab86fd0249ecce1ee49b86483c15ed67fdcca7fc037e75de99846244b",
                        Mc263AncientCityGrammar.ProcessorKind.RULE,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor",
                        startRules, ruleRng(startRules, seed)),
                new Mc263AncientCityGrammar.ProcessorSpec(
                        "minecraft:protected_blocks:5a695f52c8dbf2af06c6b6146fada8fa999b3a3ac2030c9a1fc222b571b8f3e2",
                        Mc263AncientCityGrammar.ProcessorKind.PROTECTED_BLOCKS,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor",
                        new Mc263AncientCityGrammar.ProtectedBlocksCodec("#minecraft:features_cannot_replace"),
                        new Mc263AncientCityGrammar.NoneRng()),
                new Mc263AncientCityGrammar.ProcessorSpec(
                        "minecraft:block_ignore:b9bb5faed4dbea8a86d1720de4f6cb1e5cad5fec0ec2e98d0386942e3bf377f7",
                        Mc263AncientCityGrammar.ProcessorKind.BLOCK_IGNORE,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor",
                        new Mc263AncientCityGrammar.BlockIgnoreCodec(List.of("minecraft:structure_block")),
                        new Mc263AncientCityGrammar.NoneRng()),
                new Mc263AncientCityGrammar.ProcessorSpec(
                        "minecraft:jigsaw_replacement:9a064af3d024acc4cfada5f54662b7c6abb943a38d5d196e9651c8c3f8430a34",
                        Mc263AncientCityGrammar.ProcessorKind.JIGSAW_REPLACEMENT,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor",
                        new Mc263AncientCityGrammar.JigsawReplacementCodec(),
                        new Mc263AncientCityGrammar.NoneRng()),
                new Mc263AncientCityGrammar.ProcessorSpec(
                        "minecraft:block_rot:3be74ecfda1988c24fb449bd37e184a09d71aa85738603cf08ced7335c261738",
                        Mc263AncientCityGrammar.ProcessorKind.BLOCK_ROT,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor",
                        rot, new Mc263AncientCityGrammar.BlockRotRng(rot.rottableBlocks(), rot.integrity(),
                                seed, "RandomSource.create(positionSeed)", "nextFloat",
                                "draw <= integrity")),
                new Mc263AncientCityGrammar.ProcessorSpec(
                        "minecraft:rule:7aee803a659d7cf91ca5dc93e68588b21455314fd4b25c04ebc2bac1ee9cf934",
                        Mc263AncientCityGrammar.ProcessorKind.RULE,
                        "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor",
                        wallRules, ruleRng(wallRules, seed)));
    }

    private static Mc263AncientCityGrammar.Rule rule(String block, double probability,
            String output) {
        return new Mc263AncientCityGrammar.Rule(block, probability,
                "minecraft:random_block_match", "minecraft:always_true", output);
    }

    private static Mc263AncientCityGrammar.RuleCodec ruleCodec(
            List<Mc263AncientCityGrammar.Rule> rules) {
        return new Mc263AncientCityGrammar.RuleCodec(rules);
    }

    private static Mc263AncientCityGrammar.RuleRng ruleRng(
            Mc263AncientCityGrammar.RuleCodec codec, Mc263AncientCityGrammar.PositionSeed seed) {
        ArrayList<Mc263AncientCityGrammar.PredicateDraw> all = new ArrayList<>();
        ArrayList<Mc263AncientCityGrammar.PredicateDraw> random = new ArrayList<>();
        for (Mc263AncientCityGrammar.Rule rule : codec.rules()) {
            Mc263AncientCityGrammar.PredicateDraw draw =
                    new Mc263AncientCityGrammar.PredicateDraw(rule.inputPredicateType(),
                            rule.probability());
            random.add(draw); all.add(draw);
            all.add(new Mc263AncientCityGrammar.PredicateDraw(rule.locationPredicateType(),
                    Optional.empty()));
        }
        return new Mc263AncientCityGrammar.RuleRng(all, random,
                "codec order; first matching rule wins",
                "StructurePlaceSettings.getRandom(transformedBlockPosition)",
                "RandomSource.create(Mth.getSeed(transformedBlockPosition))", seed);
    }

    private static Mc263AncientCityGrammar.ConfiguredFeature configuredFeature() {
        Mc263AncientCityGrammar.UniformInt offset = new Mc263AncientCityGrammar.UniformInt(-2, 2);
        Mc263AncientCityGrammar.SculkSequence sequence = new Mc263AncientCityGrammar.SculkSequence(
                new Mc263AncientCityGrammar.SculkPatch(10, 32, 64, 0, 1),
                new Mc263AncientCityGrammar.OverlayFeature(
                        new Mc263AncientCityGrammar.CatalystFeature("minecraft:sculk_catalyst",
                                0.5D, new Mc263AncientCityGrammar.Pos(0, -1, 0)),
                        new Mc263AncientCityGrammar.ShriekerFeature(
                                new Mc263AncientCityGrammar.BlockStateSpec(
                                        "minecraft:sculk_shrieker", "false", "false", "true"),
                                new Mc263AncientCityGrammar.UniformInt(1, 3),
                                new Mc263AncientCityGrammar.OffsetDistribution(offset, offset, offset),
                                "minecraft:air", new Mc263AncientCityGrammar.Pos(0, -1, 0))));
        return new Mc263AncientCityGrammar.ConfiguredFeature(FEATURE_KEY, sequence,
                List.of(
                        new Mc263AncientCityGrammar.FeatureIdentity("minecraft:sculk_patch",
                                "net.minecraft.world.level.levelgen.feature.SculkPatchFeature"),
                        new Mc263AncientCityGrammar.FeatureIdentity("minecraft:overlay",
                                "net.minecraft.world.level.levelgen.feature.OverlayFeature"),
                        new Mc263AncientCityGrammar.FeatureIdentity("minecraft:simple_block",
                                "net.minecraft.world.level.levelgen.feature.SimpleBlockFeature"),
                        new Mc263AncientCityGrammar.FeatureIdentity("minecraft:simple_block",
                                "net.minecraft.world.level.levelgen.feature.SimpleBlockFeature")),
                false, "the same caller RandomSource passed to feature placement");
    }

    private static Mc263AncientCityGrammar.TypedSidecars typedSidecars() {
        return new Mc263AncientCityGrammar.TypedSidecars(
                List.of(
                        sidecar("COMPARATOR_OUTPUT|minecraft:comparator", 19),
                        sidecar("EMPTY_CAMPFIRE|minecraft:campfire", 2),
                        sidecar("EMPTY_LECTERN|minecraft:lectern", 3),
                        sidecar("EMPTY_SCULK_SENSOR|minecraft:sculk_sensor", 10),
                        sidecar("EMPTY_SKELETON_SKULL|minecraft:skull", 2),
                        sidecar("FIXED_CHEST_CONTENTS|minecraft:chest", 1),
                        sidecar("FURNACE_PAYLOAD|minecraft:furnace", 1),
                        sidecar("LOOT_CONTAINER|minecraft:chest", 14)),
                List.of(sidecar("minecraft:chests/ancient_city_ice_box|minecraft:chest", 1),
                        sidecar("minecraft:chests/ancient_city|minecraft:chest", 13)),
                List.of(), List.of());
    }

    private static Mc263AncientCityGrammar.SidecarCount sidecar(String identity, int count) {
        return new Mc263AncientCityGrammar.SidecarCount(identity, count);
    }

    private static void validateClosure(List<String> poolKeys,
            List<Mc263AncientCityGrammar.Pool> pools,
            List<Mc263AncientCityGrammar.ProcessorListSpec> processorLists,
            List<Mc263AncientCityGrammar.ProcessorSpec> processors,
            Mc263AncientCityGrammar.ConfiguredFeature feature,
            List<Mc263AncientCityGrammar.Template> templates,
            Mc263AncientCityGrammar.TypedSidecars sidecars, List<String> states,
            Mc263AncientCityGrammar.Aggregate aggregate) {
        require(poolKeys.size() == POOL_COUNT && new HashSet<>(poolKeys).size() == POOL_COUNT,
                "Ancient City production pool-key closure drift");
        require(pools.size() == aggregate.poolCount() && templates.size() == aggregate.templateIdentityCount()
                        && states.size() == aggregate.stateIdentityCount(),
                "Ancient City production aggregate cardinality drift");
        require(aggregate.equals(new Mc263AncientCityGrammar.Aggregate(
                        7, 65, 107, 58, 57, 1, 72_710, 26_176, 156, 294,
                        "84fa93195c5ad6b53c59e67738e1f66e1307c742759c830f3ad387af80d400d6",
                        "3074aa632e1e8a75cf2785d013b0e33d4603f21f02a11b39c173a10b44b9526f",
                        "015e93c6195c9b7acdeb8d5e5e1b35f1b2c1d3f3b29611ffceb4f664342b1ca0",
                        "b06b35c3c219c885516b963e299c46abc42479c123de15a28f12412ff717eff9",
                        "28892d667abfeae5839552fb95d2c835ce8c6d3f4f0b2bd82cb36295d41e40f1",
                        "3d90f4907e13dec2d1c0bddd0ecd26a6540918cff4bc1acda6e70d5c9da4a6b5",
                        "d40ed81bb43dd9f0c94641fe0db2148b548cd2c91e107490cb230511e903b27d")),
                "Ancient City production aggregate receipt drift");
        require(processorLists.size() == 4 && processors.size() == 6
                        && FEATURE_KEY.equals(feature.registryKey()),
                "Ancient City production processor/feature closure drift");
        int blockRotCount = 0;
        for (Mc263AncientCityGrammar.ProcessorSpec processor : processors) {
            if (processor.kind() != Mc263AncientCityGrammar.ProcessorKind.BLOCK_ROT) continue;
            blockRotCount++;
            Mc263AncientCityGrammar.BlockRotCodec codec =
                    (Mc263AncientCityGrammar.BlockRotCodec) processor.codec();
            require(ROTTABLE_BLOCKS_AUTHORITY.tag().equals(codec.rottableBlocks())
                            && ROTTABLE_BLOCKS_AUTHORITY.members().equals(
                                    codec.resolvedRottableBlocks()),
                    "Ancient City block-rot resolved tag authority drift");
        }
        require(blockRotCount == 1, "Ancient City block-rot processor cardinality drift");
        HashSet<String> templateIds = new HashSet<>();
        HashSet<String> globalStates = new HashSet<>(states);
        require(globalStates.size() == states.size(), "Ancient City production state closure contains duplicates");
        int present = 0, absent = 0, blocks = 0, commands = 0, connectors = 0;
        for (Mc263AncientCityGrammar.Template template : templates) {
            require(templateIds.add(template.id()), "duplicate Ancient City production template identity");
            if (template.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT) present++; else absent++;
            blocks = Math.addExact(blocks, template.blockCount());
            commands = Math.addExact(commands, template.commands().size());
            connectors = Math.addExact(connectors, template.connectors().size());
            for (String state : template.stateTable()) require(globalStates.contains(state),
                    "Ancient City production template state escaped closure: " + state);
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.Jigsaw jigsaw) {
                    require(globalStates.contains(jigsaw.finalState()),
                            "Ancient City production jigsaw final state escaped closure");
                }
            }
        }
        require(present == aggregate.presentTemplateCount() && absent == aggregate.expectedAbsentTemplateCount()
                        && blocks == aggregate.blockCount() && commands == aggregate.commandCount()
                        && connectors == aggregate.connectorCount(),
                "Ancient City production template aggregate drift");
        HashSet<String> processorIds = new HashSet<>();
        for (Mc263AncientCityGrammar.ProcessorListSpec processor : processorLists) {
            require(processorIds.add(processor.identity()), "duplicate Ancient City processor list");
        }
        for (Mc263AncientCityGrammar.Pool pool : pools) {
            for (Mc263AncientCityGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263AncientCityGrammar.SingleElement single) {
                    require(templateIds.contains(single.template()) && processorIds.contains(single.processorList()),
                            "Ancient City production single element escaped authority closure");
                } else if (element instanceof Mc263AncientCityGrammar.ListElement list) {
                    for (Mc263AncientCityGrammar.SingleChild child : list.childrenInDeclaredOrder()) {
                        require(templateIds.contains(child.template()) && processorIds.contains(child.processorList()),
                                "Ancient City production list child escaped authority closure");
                    }
                } else if (element instanceof Mc263AncientCityGrammar.FeatureElement elementFeature) {
                    require(FEATURE_KEY.equals(elementFeature.feature()),
                            "Ancient City production pool escaped configured feature closure");
                }
            }
        }
        require(sidecars.bent().size() == 8 && sidecars.loot().size() == 2
                        && sidecars.spwn().isEmpty() && sidecars.ents().isEmpty(),
                "Ancient City production sidecar closure drift");
    }

    private static Mc263AncientCityGrammar.BentPayload bent(
            String semantic, String type, String extra) {
        Mc263AncientCityGrammar.BentSemantic value;
        try { value = Mc263AncientCityGrammar.BentSemantic.valueOf(semantic); }
        catch (IllegalArgumentException error) { throw invalid("unknown Ancient City BENT semantic", error); }
        return switch (value) {
            case COMPARATOR_OUTPUT -> new Mc263AncientCityGrammar.ComparatorOutput(type,
                    parseInt(extra, "comparator output"));
            case EMPTY_SCULK_SENSOR -> empty(extra,
                    new Mc263AncientCityGrammar.EmptySculkSensor(type));
            case EMPTY_LECTERN -> empty(extra, new Mc263AncientCityGrammar.EmptyLectern(type));
            case EMPTY_SKELETON_SKULL -> empty(extra,
                    new Mc263AncientCityGrammar.EmptySkeletonSkull(type));
            case EMPTY_CAMPFIRE -> {
                String[] halves = extra.split(";", -1);
                require(halves.length == 2, "malformed Ancient City campfire payload");
                yield new Mc263AncientCityGrammar.EmptyCampfire(type,
                        ints(halves[0]), ints(halves[1]));
            }
            case FIXED_CHEST_CONTENTS -> new Mc263AncientCityGrammar.FixedChestContents(type,
                    items(extra));
            case FURNACE_PAYLOAD -> {
                String[] values = extra.split("\\|", -1);
                require(values.length == 6, "malformed Ancient City furnace payload");
                yield new Mc263AncientCityGrammar.FurnacePayload(type, items(values[0]),
                        recipes(values[1]), parseInt(values[2], "lit total time"),
                        parseInt(values[3], "cooking time spent"),
                        parseInt(values[4], "cooking total time"),
                        parseInt(values[5], "lit time remaining"));
            }
        };
    }

    private static <T extends Mc263AncientCityGrammar.BentPayload> T empty(String extra, T value) {
        require(extra.isEmpty(), "unexpected Ancient City empty BENT payload data");
        return value;
    }

    private static List<Integer> ints(String value) {
        if (value.isEmpty()) return List.of();
        return Arrays.stream(value.split(",", -1)).map(part -> parseInt(part, "integer list")).toList();
    }

    private static List<Mc263AncientCityGrammar.ItemStack> items(String value) {
        if (value.isEmpty()) return List.of();
        ArrayList<Mc263AncientCityGrammar.ItemStack> result = new ArrayList<>();
        for (String entry : value.split(";", -1)) {
            String[] fields = entry.split(",", -1);
            require(fields.length == 3, "malformed Ancient City item payload");
            result.add(new Mc263AncientCityGrammar.ItemStack(parseInt(fields[0], "item slot"),
                    fields[1], parseInt(fields[2], "item count")));
        }
        return List.copyOf(result);
    }

    private static List<Mc263AncientCityGrammar.RecipeUse> recipes(String value) {
        if (value.isEmpty()) return List.of();
        ArrayList<Mc263AncientCityGrammar.RecipeUse> result = new ArrayList<>();
        for (String entry : value.split(";", -1)) {
            String[] fields = entry.split(",", -1);
            require(fields.length == 2, "malformed Ancient City recipe payload");
            result.add(new Mc263AncientCityGrammar.RecipeUse(fields[0],
                    parseInt(fields[1], "recipe count")));
        }
        return List.copyOf(result);
    }

    private static Mc263AncientCityGrammar.Pos pos(String[] fields, int offset) {
        return new Mc263AncientCityGrammar.Pos(parseInt(fields[offset], "x"),
                parseInt(fields[offset + 1], "y"), parseInt(fields[offset + 2], "z"));
    }

    private static int stateIndex(String value, List<String> states) {
        int index = parseInt(value, "state index");
        require(index >= 0 && index < states.size(), "Ancient City production state index escaped table");
        return index;
    }

    private static List<String> csv(String value) {
        return value.isEmpty() ? List.of() : List.of(value.split(",", -1));
    }

    private static RottableBlocksAuthority loadRottableBlocksAuthority() {
        byte[] bytes;
        try (InputStream input = Mc263AncientCityProductionAuthority.class
                .getResourceAsStream(ROTTABLE_BLOCKS_RESOURCE)) {
            if (input == null) {
                throw invalid("Ancient City rottable-block authority is absent: "
                        + ROTTABLE_BLOCKS_RESOURCE);
            }
            bytes = input.readAllBytes();
        } catch (IOException error) {
            throw invalid("failed to read Ancient City rottable-block authority", error);
        }
        require(bytes.length == ROTTABLE_BLOCKS_RESOURCE_BYTES,
                "Ancient City rottable-block authority byte-count drift");
        require(ROTTABLE_BLOCKS_RESOURCE_SHA256.equals(sha256(bytes)),
                "Ancient City rottable-block authority SHA-256 drift");
        return parseRottableBlocksAuthority(bytes);
    }

    private static RottableBlocksAuthority loadProtectedBlocksAuthority() {
        byte[] bytes;
        try (InputStream input = Mc263AncientCityProductionAuthority.class
                .getResourceAsStream(PROTECTED_BLOCKS_RESOURCE)) {
            if (input == null) {
                throw invalid("Ancient City protected-block authority is absent: "
                        + PROTECTED_BLOCKS_RESOURCE);
            }
            bytes = input.readAllBytes();
        } catch (IOException error) {
            throw invalid("failed to read Ancient City protected-block authority", error);
        }
        require(bytes.length == PROTECTED_BLOCKS_RESOURCE_BYTES,
                "Ancient City protected-block authority byte-count drift");
        require(PROTECTED_BLOCKS_RESOURCE_SHA256.equals(sha256(bytes)),
                "Ancient City protected-block authority SHA-256 drift");
        TagRows rows = new TagRows(bytes, 9);
        String[] header = rows.take("H", 7);
        require(ROTTABLE_BLOCKS_FORMAT.equals(header[1])
                        && parseInt(header[2], "protected schema") == 1
                        && "26.3-snapshot-7".equals(header[3])
                        && "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61".equals(header[4])
                        && parseInt(header[5], "protected world version") == 5009
                        && parseInt(header[6], "protected data-pack version") == 115,
                "Ancient City protected-block source identity drift");
        String[] tag = rows.take("T", 7);
        int memberCount = parseInt(tag[4], "protected member count");
        require(PROTECTED_BLOCKS_TAG.equals(tag[1])
                        && PROTECTED_BLOCKS_SOURCE_PATH.equals(tag[2])
                        && PROTECTED_BLOCKS_SOURCE_SHA256.equals(tag[3])
                        && memberCount == EXPECTED_PROTECTED_BLOCKS.size()
                        && PROTECTED_BLOCKS_MEMBERS_SHA256.equals(tag[5])
                        && parseInt(tag[6], "protected referenced-tag count") == 0,
                "Ancient City protected-block source/tag receipt drift");
        ArrayList<String> members = new ArrayList<>(memberCount);
        HashSet<String> unique = new HashSet<>();
        for (int ordinal = 0; ordinal < memberCount; ordinal++) {
            String[] row = rows.take("M", 3);
            require(parseInt(row[1], "protected member ordinal") == ordinal,
                    "Ancient City protected-block member order drift");
            String member = row[2];
            require(unique.add(member) && EXPECTED_PROTECTED_BLOCKS.get(ordinal).equals(member),
                    "unknown/reordered Ancient City protected-block member");
            members.add(member);
        }
        rows.finish();
        byte[] orderedBytes = (String.join("\n", members) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        require(PROTECTED_BLOCKS_MEMBERS_SHA256.equals(sha256(orderedBytes)),
                "Ancient City protected-block ordered member hash drift");
        return new RottableBlocksAuthority(tag[1], members);
    }

    private static RottableBlocksAuthority parseRottableBlocksAuthority(byte[] bytes) {
        require(bytes != null, "Ancient City rottable-block authority bytes are null");
        TagRows rows = new TagRows(bytes, 14);
        String[] header = rows.take("H", 7);
        require(ROTTABLE_BLOCKS_FORMAT.equals(header[1])
                        && parseInt(header[2], "rottable schema") == 1
                        && "26.3-snapshot-7".equals(header[3])
                        && "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61".equals(header[4])
                        && parseInt(header[5], "rottable world version") == 5009
                        && parseInt(header[6], "rottable data-pack version") == 115,
                "Ancient City rottable-block source identity drift");
        String[] tag = rows.take("T", 7);
        int memberCount = parseInt(tag[4], "rottable member count");
        require(ROTTABLE_BLOCKS_TAG.equals(tag[1])
                        && ROTTABLE_BLOCKS_SOURCE_PATH.equals(tag[2])
                        && ROTTABLE_BLOCKS_SOURCE_SHA256.equals(tag[3])
                        && memberCount == EXPECTED_ROTTABLE_BLOCKS.size()
                        && ROTTABLE_BLOCKS_MEMBERS_SHA256.equals(tag[5])
                        && parseInt(tag[6], "rottable referenced-tag count") == 0,
                "Ancient City rottable-block source/tag receipt drift");
        ArrayList<String> members = new ArrayList<>(memberCount);
        HashSet<String> unique = new HashSet<>();
        for (int ordinal = 0; ordinal < memberCount; ordinal++) {
            String[] row = rows.take("M", 3);
            require(parseInt(row[1], "rottable member ordinal") == ordinal,
                    "Ancient City rottable-block member order drift");
            String member = row[2];
            require(unique.add(member), "duplicate Ancient City rottable-block member");
            require(EXPECTED_ROTTABLE_BLOCKS.get(ordinal).equals(member),
                    "unknown/reordered Ancient City rottable-block member");
            members.add(member);
        }
        rows.finish();
        byte[] orderedBytes = (String.join("\n", members) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        require(ROTTABLE_BLOCKS_MEMBERS_SHA256.equals(sha256(orderedBytes)),
                "Ancient City rottable-block ordered member hash drift");
        return new RottableBlocksAuthority(tag[1], members);
    }

    private record RottableBlocksAuthority(String tag, List<String> members) {
        RottableBlocksAuthority {
            members = List.copyOf(members);
        }
    }

    private static final class TagRows {
        private final String[] lines;
        private int index;

        TagRows(byte[] bytes, int expectedLineCount) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            require(Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8)),
                    "Ancient City rottable-block authority is not canonical UTF-8");
            require(text.endsWith("\n") && text.indexOf('\r') < 0,
                    "Ancient City rottable-block authority newline drift");
            lines = text.substring(0, text.length() - 1).split("\n", -1);
            require(lines.length == expectedLineCount,
                    "Ancient City block-tag authority line-count drift");
        }

        String[] take(String expectedTag, int fields) {
            require(index < lines.length, "Ancient City rottable-block authority ended early");
            String[] row = lines[index++].split("\t", -1);
            require(row.length == fields && expectedTag.equals(row[0]),
                    "Ancient City rottable-block authority row/field-order drift");
            return row;
        }

        void finish() {
            require(index == lines.length,
                    "Ancient City rottable-block authority has trailing rows");
        }
    }

    private static int parseInt(String value, String label) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw invalid("invalid Ancient City production " + label, error); }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static final class Rows {
        private final String[] lines;
        private int index;

        Rows(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            require(text.endsWith("\n"), "Ancient City production authority lost terminal newline");
            lines = text.substring(0, text.length() - 1).split("\n", -1);
            require(lines.length == 28_232, "Ancient City production authority line-count drift");
        }
        String[] take(String tag) {
            String[] row = takeAny();
            require(row.length > 0 && tag.equals(row[0]),
                    "Ancient City production authority row-order drift; expected " + tag);
            return row;
        }
        String[] take(String tag, int fields) {
            String[] row = take(tag);
            require(row.length == fields, "Ancient City production authority field-count drift for " + tag);
            return row;
        }
        String[] takeAny() {
            require(index < lines.length, "Ancient City production authority ended early");
            return lines[index++].split("\t", -1);
        }
        void finish() {
            require(index == lines.length, "Ancient City production authority has trailing rows");
        }
    }
}
