package com.gameexpert.terrain.mc.structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded procedural grammar catalog proven by the pinned 26.3-snapshot-7 Shipwreck oracle.
 *
 * <p>No Mojang template payload is retained. The lawful v2 ordinal arithmetic-run grammar,
 * dimensions, palette cardinality and marker opcodes reconstruct every evidenced palette while
 * the per-template digest rejects drift.</p>
 */
public final class Mc263ShipwreckCatalog {
    public static final String ORACLE_SOURCE_SHA256 =
            "51a84a23a13a5bd87c8d95c96b763625dcb589922d4c4feb7987b21a1991483a";
    public static final String ORACLE_CONTRACT_SHA256 =
            "5f4e6ad13a75b69357ac5b44cd6d9c18f0efc931ef0bdbf583fb68a19785f6eb";
    public static final String ORACLE_SHA256 =
            "5a439c9e2d3e96927e9a1cde0cc097fd51baef2456b66ef345f5c80726ea9f31";
    public static final String PROCESSOR = "BlockIgnoreProcessor.STRUCTURE_AND_AIR";
    /** V2 proves a lossless normalized arithmetic-run grammar without copying raw template NBT. */
    public static final boolean FULL_TEMPLATE_BODY_PROVEN = true;
    /** Official scheduling is chunk-local and does not define one global cross-chunk stream. */
    public static final boolean GLOBAL_CROSS_CHUNK_WRITE_ORDER_PROVEN = false;
    /** Registration remains dormant until orchestrator-owned Java/Rust comparison succeeds. */
    public static final boolean CANONICAL_ACTIVATION_ELIGIBLE = false;
    public static final int PIVOT_X = 4;
    public static final int PIVOT_Y = 0;
    public static final int PIVOT_Z = 15;

    public enum MarkerKind {
        SUPPLY("supply_chest", "minecraft:chests/shipwreck_supply"),
        MAP("map_chest", "minecraft:chests/shipwreck_map"),
        TREASURE("treasure_chest", "minecraft:chests/shipwreck_treasure");

        private final String metadata;
        private final String lootTable;
        MarkerKind(String metadata, String lootTable) {
            this.metadata = metadata; this.lootTable = lootTable;
        }
        public String metadata() { return metadata; }
        public String lootTable() { return lootTable; }
        static MarkerKind fromMetadata(String value) {
            for (MarkerKind kind : values()) if (kind.metadata.equals(value)) return kind;
            throw new IllegalArgumentException("unknown shipwreck data marker: " + value);
        }
    }

    public record Marker(int paletteOrdinal, int x, int y, int z, MarkerKind kind) {
        public Marker {
            if (paletteOrdinal < 0) throw new IllegalArgumentException("negative marker ordinal");
            Objects.requireNonNull(kind, "shipwreck marker kind");
        }
    }

    public record Template(String id, int sizeX, int sizeY, int sizeZ, int paletteCount,
            int blocksPerPalette, List<Marker> markers, String grammarSha256) {
        public Template {
            requireResource(id); requireSha(grammarSha256);
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || paletteCount <= 0
                    || blocksPerPalette <= 0) {
                throw new IllegalArgumentException("invalid shipwreck template dimensions");
            }
            markers = List.copyOf(markers);
            int previous = -1;
            for (Marker marker : markers) {
                if (marker.paletteOrdinal() <= previous || marker.paletteOrdinal() >= blocksPerPalette
                        || marker.x() < 0 || marker.x() >= sizeX || marker.y() < 0
                        || marker.y() >= sizeY || marker.z() < 0 || marker.z() >= sizeZ) {
                    throw new IllegalArgumentException("invalid shipwreck marker grammar");
                }
                previous = marker.paletteOrdinal();
            }
        }
        public boolean degraded() { return id.endsWith("_degraded"); }
        public String shape() {
            String value = id.substring("minecraft:shipwreck/".length());
            return degraded() ? value.substring(0, value.length() - "_degraded".length()) : value;
        }
    }

    private static Marker m(int ordinal, int x, int y, int z, MarkerKind kind) {
        return new Marker(ordinal, x, y, z, kind);
    }
    private static Template t(String name, int x, int y, int z, int blocks, String hash,
            Marker... markers) {
        return new Template("minecraft:shipwreck/" + name, x, y, z, 8, blocks,
                List.of(markers), hash);
    }

    private static final List<Template> ALL = List.of(
            t("with_mast",9,21,28,729,"0407bb2915988e4a707610e042d46316ebc0b11e03d0fae70e20957d946f4bd0",m(725,4,3,9,MarkerKind.SUPPLY),m(726,5,3,18,MarkerKind.MAP),m(728,6,5,24,MarkerKind.TREASURE)),
            t("upsidedown_full",9,9,28,601,"1ded5411b5d96bc528b3db75d97617b86d70a0302ab1ce781d83e9b7843c98dc",m(596,2,3,24,MarkerKind.TREASURE),m(599,3,6,17,MarkerKind.MAP),m(600,4,6,8,MarkerKind.SUPPLY)),
            t("upsidedown_fronthalf",9,9,22,332,"d8cec4f9fa6e8e360a23672bccf39098b40b99368843da1d90ccba9a96ba4437",m(330,3,6,17,MarkerKind.MAP),m(331,4,6,8,MarkerKind.SUPPLY)),
            t("upsidedown_backhalf",9,9,16,387,"533e11e65f8066b3f037cedc58796870c5e4815a5312c3a935fa7a1ec7435e8d",m(384,2,3,12,MarkerKind.TREASURE),m(386,3,6,5,MarkerKind.MAP)),
            t("sideways_full",9,9,28,641,"1708b22365223d0e486d94f94eb1a6068df85d6735eff3473fe983957e45b736",m(636,3,3,24,MarkerKind.TREASURE),m(639,5,4,8,MarkerKind.SUPPLY),m(640,6,4,19,MarkerKind.MAP)),
            t("sideways_fronthalf",9,9,24,321,"f4c2c33e9248bf6a96c327ae4bd36f8cc1b05f0269c42bd1fd79ddef74094ee2",m(320,5,4,8,MarkerKind.SUPPLY)),
            t("sideways_backhalf",9,9,17,381,"eac75b9ff316b38d1279268b5f13a0e883be65a5c5da0396d255e4e85ec989d3",m(378,3,3,13,MarkerKind.TREASURE),m(380,6,4,8,MarkerKind.MAP)),
            t("rightsideup_full",9,9,28,662,"c85da4393fbb4a0587556f285327d59ceaf7feb853099be580efa09b6c43409e",m(658,4,3,8,MarkerKind.SUPPLY),m(659,5,3,18,MarkerKind.MAP),m(661,6,5,24,MarkerKind.TREASURE)),
            t("rightsideup_fronthalf",9,9,24,355,"1e8b2aa1cefcacaa28047745c9bfe94ae3f6bb4ec33fccdd886484588dda8547",m(354,4,3,8,MarkerKind.SUPPLY)),
            t("rightsideup_backhalf",9,9,16,414,"d49f315e31cf2b0a19b5c8702b1623a64e99c8ab38ff5d58735e562f3f7119d9",m(411,5,3,6,MarkerKind.MAP),m(413,6,5,12,MarkerKind.TREASURE)),
            t("with_mast_degraded",9,21,28,652,"4cf47181723dca4190c119f468f70f9414dfef43024a7f2ebe7d8854cb153975",m(648,4,3,9,MarkerKind.SUPPLY),m(649,5,3,18,MarkerKind.MAP),m(651,6,5,24,MarkerKind.TREASURE)),
            t("upsidedown_full_degraded",9,9,28,546,"c9353f3ec45464785a27f979b27c379991dad677766d025200c20fcac6741382",m(541,2,3,24,MarkerKind.TREASURE),m(544,3,6,17,MarkerKind.MAP),m(545,4,6,8,MarkerKind.SUPPLY)),
            t("upsidedown_fronthalf_degraded",9,9,22,300,"0fe1980469d241e061351217ebd15d1e6e18bcf0bbddb1b22fc2f52861190575",m(298,3,6,17,MarkerKind.MAP),m(299,4,6,8,MarkerKind.SUPPLY)),
            t("upsidedown_backhalf_degraded",9,9,16,362,"2b5e106bfdcf6dc0ebcca2fc37d539d562f72430430c84cb742e606fc718bbe0",m(359,2,3,12,MarkerKind.TREASURE),m(361,3,6,5,MarkerKind.MAP)),
            t("sideways_full_degraded",9,9,28,583,"6670c2a20c0a1292ae26563228a1ddebcee6ddf1c023bf60fe5bceaa3581c33a",m(578,3,3,24,MarkerKind.TREASURE),m(581,5,4,8,MarkerKind.SUPPLY),m(582,6,4,19,MarkerKind.MAP)),
            t("sideways_fronthalf_degraded",9,9,24,246,"8ff6f96e14eaf16fa027b48fa392a2aef849e6f9290356a0ff183f857d20d3bf",m(245,5,4,8,MarkerKind.SUPPLY)),
            t("sideways_backhalf_degraded",9,9,17,340,"94af5574ad79379f2ef02292b466363c7cc8d94625d2a3f87b01fe5db4f32719",m(337,3,3,13,MarkerKind.TREASURE),m(339,6,4,8,MarkerKind.MAP)),
            t("rightsideup_full_degraded",9,9,28,619,"29d9d67dbbb52ee6c5be3da4ad83b4454386ca92970f5f138231a77aa6d997c2",m(615,4,3,8,MarkerKind.SUPPLY),m(616,5,3,18,MarkerKind.MAP),m(618,6,5,24,MarkerKind.TREASURE)),
            t("rightsideup_fronthalf_degraded",9,9,24,299,"08792a93f1787d5cd6d622f1dc72fd591e520449b2a071d528bec10ebdafeb25",m(298,4,3,8,MarkerKind.SUPPLY)),
            t("rightsideup_backhalf_degraded",9,9,16,385,"4e44eefedf55177b15285f1a7a77356f132a4555d30752de6182e37f49f91846",m(382,5,3,6,MarkerKind.MAP),m(384,6,5,12,MarkerKind.TREASURE)));

    private static final List<String> OCEAN = ALL.stream().map(Template::id).toList();
    private static final List<String> BEACHED = List.of(ALL.get(0).id(), ALL.get(4).id(),
            ALL.get(5).id(), ALL.get(6).id(), ALL.get(7).id(), ALL.get(8).id(),
            ALL.get(9).id(), ALL.get(10).id(), ALL.get(17).id(), ALL.get(18).id(),
            ALL.get(19).id());
    private static final Map<String, Template> BY_ID = index();

    private Mc263ShipwreckCatalog() { }

    public static List<Template> all() { return ALL; }
    public static List<String> oceanOrder() { return OCEAN; }
    public static List<String> beachedOrder() { return BEACHED; }
    public static Template require(String id) {
        Template value = BY_ID.get(id);
        if (value == null) throw new IllegalArgumentException("unknown shipwreck variant: " + id);
        return value;
    }
    public static void requireProcessor(String processor) {
        if (!PROCESSOR.equals(processor)) {
            throw new IllegalArgumentException("unknown shipwreck processor: " + processor);
        }
    }

    private static Map<String, Template> index() {
        LinkedHashMap<String, Template> result = new LinkedHashMap<>();
        for (Template template : ALL) {
            if (result.put(template.id(), template) != null) {
                throw new ExceptionInInitializerError("duplicate shipwreck template");
            }
        }
        if (result.size() != 20 || OCEAN.size() != 20 || BEACHED.size() != 11) {
            throw new ExceptionInInitializerError("shipwreck catalog cardinality drift");
        }
        return Map.copyOf(result);
    }

    private static void requireResource(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid resource key: " + value);
        }
    }
    private static void requireSha(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid shipwreck receipt SHA-256");
        }
    }
}
