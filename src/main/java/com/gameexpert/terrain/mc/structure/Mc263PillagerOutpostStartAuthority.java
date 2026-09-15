package com.gameexpert.terrain.mc.structure;

import java.util.List;
import java.util.Objects;

/** Coordinate-free production binding for the accepted OUT-E3M21 start-authority evidence. */
final class Mc263PillagerOutpostStartAuthority {
    static final String FORMAT = "OUT263GA1";
    static final int TRACE_SCHEMA = 2;
    static final String RESOURCE_SHA256 =
            "ba06409a6baa49bec5e3b0b2866b55ab375dd9ba2ce46ce924f7bfe9b21e49cb";
    static final String RAW_SHA256 =
            "44e926d7666d03160b90f0f4af57d7fc9b2170d6d848ccc608e457975bd6ddd6";
    static final String EVIDENCE_SHA256 =
            "62792c2ffcbfa8375a465ac47c0e65d389e55421b800cb200883a22adfd380f4";
    static final String PRODUCER_SOURCE_SHA256 =
            "6f2ad27969144ffb5403657f795e8e444357de14e1bfa51bb271968aac32519a";
    static final String TRANSCRIPT_SHA256 =
            "09408c3aff98d7e19034225698b85829608ddf6f1d1bd2ff841c56c5089f05f6";

    private static final List<String> POOLS = List.of(
            "minecraft:empty", "minecraft:pillager_outpost/base_plates",
            "minecraft:pillager_outpost/feature_plates", "minecraft:pillager_outpost/features",
            "minecraft:pillager_outpost/towers");
    private static final List<String> TEMPLATES = List.of(
            "minecraft:pillager_outpost/base_plate", "minecraft:pillager_outpost/feature_cage1",
            "minecraft:pillager_outpost/feature_cage2",
            "minecraft:pillager_outpost/feature_cage_with_allays",
            "minecraft:pillager_outpost/feature_logs", "minecraft:pillager_outpost/feature_plate",
            "minecraft:pillager_outpost/feature_targets", "minecraft:pillager_outpost/feature_tent1",
            "minecraft:pillager_outpost/feature_tent2", "minecraft:pillager_outpost/watchtower",
            "minecraft:pillager_outpost/watchtower_overgrown");
    private static final List<String> PROCESSORS =
            List.of("inline:empty", "minecraft:outpost_rot");
    private static final Authority PINNED = new Authority(FORMAT, TRACE_SCHEMA, RESOURCE_SHA256,
            RAW_SHA256, EVIDENCE_SHA256, PRODUCER_SOURCE_SHA256, TRANSCRIPT_SHA256,
            POOLS, TEMPLATES, PROCESSORS, List.of(
                    new Witness(0,
                            "0698805c0e2c417eb3d4b0635af0fe1f5406f01cb8eb34f9584215f4a91d6699",
                            14, 520, 81, 426, 13, 1_202, 1_200,
                            185_822_005_452_950L, 270_784_946_423_770L, List.of(
                                    6_566_370_677_250_557_947L, -2_144_564_990_879_710_198L,
                                    -387_044_335_093_739_731L, -5_024_293_139_724_246_286L,
                                    -6_495_917_820_964_202_998L, -797_065_984_362_703_393L,
                                    -2_842_591_765_728_338_636L, 663_120_134_779_784_562L)),
                    new Witness(1,
                            "887ab750c54deafad225c6493543c1b529d5fc0c3f5d58b289513a6b2c09c54a",
                            8, 575, 80, 488, 7, 1_203, 1_201,
                            120_689_059_041_763L, 57_768_486_071_964L, List.of(
                                    -2_358_833_958_960_528_970L, -7_563_152_357_732_796_213L,
                                    -816_624_963_159_636_883L, -4_399_220_387_357_863_738L,
                                    -6_724_389_876_128_136_313L, 6_074_578_315_225_660_980L,
                                    -5_978_533_722_173_748_701L, 2_606_916_651_184_211_203L)),
                    new Witness(2,
                            "eec7faada85ca9bb0770645c222561337ed15eee9a199bfe087b9a16c8302db3",
                            17, 381, 142, 223, 16, 1_073, 1_071,
                            119_185_856_141_701L, 112_525_197_269_044L, List.of(
                                    -5_010_552_714_427_341_339L, 1_461_758_731_954_773_400L,
                                    6_307_343_887_705_978_305L, -172_755_660_079_647_730L,
                                    4_072_946_814_310_084_369L, -4_855_617_322_897_733_639L,
                                    5_036_919_709_466_404_757L, 1_549_594_683_237_057_692L)),
                    new Witness(3,
                            "9725c768f9b20859e898c2b798df8cb6670355c81bef0a2b5430fe5aea75f938",
                            15, 645, 106, 525, 14, 1_316, 1_314,
                            152_366_078_360_576L, 96_157_489_325_754L, List.of(
                                    6_554_861_542_970_065_806L, 879_225_561_050_703_330L,
                                    -7_120_982_395_684_001_550L, 4_297_450_578_673_480_024L,
                                    -1_291_278_216_714_338_235L, -9_211_301_761_427_119_236L,
                                    -5_658_748_219_586_996_476L, 7_054_385_249_332_523_634L))));

    private Mc263PillagerOutpostStartAuthority() { }

    static Authority pinned() {
        return PINNED;
    }

    static void authenticateProductionFacts(ProductionFacts facts) {
        authenticateProductionFacts(facts, PINNED);
    }

    static void authenticateProductionFacts(ProductionFacts facts, Authority authority) {
        Objects.requireNonNull(facts, "Pillager Outpost production facts");
        authenticateAuthority(authority);
        require(Mc263PillagerOutpostProducer.STRUCTURE_KEY.equals(facts.structureKey()),
                "E3M21 structure key drift");
        require(facts.pools().equals(authority.pools()), "E3M21 pool capability drift");
        require(facts.templates().equals(authority.templates()), "E3M21 template capability drift");
        require(facts.processors().equals(authority.processors()),
                "E3M21 processor capability drift");
    }

    static void authenticateAuthority(Authority authority) {
        Objects.requireNonNull(authority, "Pillager Outpost start authority");
        require(FORMAT.equals(authority.format()), "unknown E3M21 authority format");
        require(authority.traceSchema() == TRACE_SCHEMA, "unknown E3M21 trace schema");
        require(RESOURCE_SHA256.equals(authority.resourceSha256()), "E3M21 resource hash drift");
        require(RAW_SHA256.equals(authority.rawSha256()), "E3M21 raw evidence hash drift");
        require(EVIDENCE_SHA256.equals(authority.evidenceSha256()), "E3M21 evidence hash drift");
        require(PRODUCER_SOURCE_SHA256.equals(authority.producerSourceSha256()),
                "E3M21 producer source hash drift");
        require(TRANSCRIPT_SHA256.equals(authority.transcriptSha256()),
                "E3M21 transcript hash drift");
        require(authority.pools().equals(POOLS), "E3M21 pool authority drift");
        require(authority.templates().equals(TEMPLATES), "E3M21 template authority drift");
        require(authority.processors().equals(PROCESSORS), "E3M21 processor authority drift");
        require(authority.witnesses().size() == 4, "E3M21 witness cardinality drift");
        require(authority.witnesses().equals(PINNED.witnesses()),
                "E3M21 decision/RNG witness authority drift");
        int pieces = 0;
        int attempts = 0;
        int attach = 0;
        int collision = 0;
        int accepted = 0;
        for (int index = 0; index < authority.witnesses().size(); index++) {
            Witness witness = authority.witnesses().get(index);
            require(witness.ordinal() == index, "E3M21 witness order drift");
            require(witness.generationContinuationNextLongI64().size() == 8,
                    "E3M21 RNG continuation cardinality drift");
            require(witness.attempts() == witness.canAttachRejected()
                            + witness.collisionRejected() + witness.accepted(),
                    "E3M21 decision accounting drift");
            require(witness.accepted() == witness.pieceCount() - 1,
                    "E3M21 accepted-child accounting drift");
            pieces += witness.pieceCount();
            attempts += witness.attempts();
            attach += witness.canAttachRejected();
            collision += witness.collisionRejected();
            accepted += witness.accepted();
        }
        require(List.of(pieces, attempts, attach, collision, accepted)
                        .equals(List.of(54, 2_121, 409, 1_662, 50)),
                "E3M21 aggregate decision authority drift");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    record ProductionFacts(String structureKey, List<String> pools, List<String> templates,
            List<String> processors) {
        ProductionFacts {
            Objects.requireNonNull(structureKey, "Pillager Outpost structure key");
            pools = List.copyOf(pools);
            templates = List.copyOf(templates);
            processors = List.copyOf(processors);
        }
    }

    record Authority(String format, int traceSchema, String resourceSha256, String rawSha256,
            String evidenceSha256, String producerSourceSha256, String transcriptSha256,
            List<String> pools, List<String> templates, List<String> processors,
            List<Witness> witnesses) {
        Authority {
            pools = List.copyOf(pools);
            templates = List.copyOf(templates);
            processors = List.copyOf(processors);
            witnesses = List.copyOf(witnesses);
        }

        Authority withWitnesses(List<Witness> value) {
            return new Authority(format, traceSchema, resourceSha256, rawSha256, evidenceSha256,
                    producerSourceSha256, transcriptSha256, pools, templates, processors, value);
        }
    }

    record Witness(int ordinal, String probeSha256, int pieceCount, int attempts,
            int canAttachRejected, int collisionRejected, int accepted, int generationDrawCount,
            int generationWorldgenCount, long generationFinalState48, long placementFinalState48,
            List<Long> generationContinuationNextLongI64) {
        Witness {
            generationContinuationNextLongI64 = List.copyOf(generationContinuationNextLongI64);
        }
    }
}
