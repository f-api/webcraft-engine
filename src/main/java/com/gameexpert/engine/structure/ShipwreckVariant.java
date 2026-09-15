package com.gameexpert.engine.structure;

import com.gameexpert.terrain.Blocks;

/**
 * 난파선의 바닐라 1.21.4 변형·수종 계약. 리터럴 근거는
 * {@code docs/research/mc-vanilla-1214/SHIPWRECK.md} 와 같은 디렉터리의 원문 JSON 이며,
 * 이 파일은 wasm {@code client/wasm/src/mc_structure/shipwreck.rs} 와 <b>같은 리터럴·같은
 * 순서</b>의 손 사본이다. 한쪽만 고치면 같은 시드의 난파선이 온라인과 정적판에서 달라진다.
 */
public final class ShipwreckVariant {
    /** 선체 자세. 바닐라 템플릿 이름의 첫 토큰이다. */
    public enum Posture {
        /** {@code with_mast} — 온전한 직립 선체에 돛대가 남아 있다. */
        WITH_MAST,
        /** {@code rightsideup} — 직립이지만 돛대가 없다. */
        RIGHTSIDEUP,
        /** {@code sideways} — 옆으로 누웠다. */
        SIDEWAYS,
        /** {@code upsidedown} — 뒤집혔다. beached 목록에는 없다. */
        UPSIDEDOWN
    }

    /** 잔존 구획. {@code with_mast} 는 {@code full} 만 있다. */
    public enum Section { FULL, FRONTHALF, BACKHALF }

    /** 상자 3종. 바닐라 템플릿의 metadata 마커 이름과 1:1이다. */
    public enum ChestRole {
        /** {@code supply_chest} — 선수. */
        SUPPLY,
        /** {@code treasure_chest} — 선미 상단. */
        TREASURE,
        /** {@code map_chest} — 선미 하단. */
        MAP
    }

    /** 한 site 의 확정된 변형. */
    public record Variant(Posture posture, Section section, boolean degraded, boolean beached) {}

    /**
     * 바닐라 {@code ShipwreckGenerator.BEACHED_TEMPLATES} 11종을 소스 순서 그대로 옮긴 표다.
     * {@code Util.getRandom} 이므로 각 1/11 균등이다.
     */
    private static final Variant[] BEACHED = {
        beached(Posture.WITH_MAST, Section.FULL, false),
        beached(Posture.SIDEWAYS, Section.FULL, false),
        beached(Posture.SIDEWAYS, Section.FRONTHALF, false),
        beached(Posture.SIDEWAYS, Section.BACKHALF, false),
        beached(Posture.RIGHTSIDEUP, Section.FULL, false),
        beached(Posture.RIGHTSIDEUP, Section.FRONTHALF, false),
        beached(Posture.RIGHTSIDEUP, Section.BACKHALF, false),
        beached(Posture.WITH_MAST, Section.FULL, true),
        beached(Posture.RIGHTSIDEUP, Section.FULL, true),
        beached(Posture.RIGHTSIDEUP, Section.FRONTHALF, true),
        beached(Posture.RIGHTSIDEUP, Section.BACKHALF, true)
    };

    /**
     * 바닐라 {@code ShipwreckGenerator.REGULAR_TEMPLATES} 20종을 소스 순서 그대로 옮긴 표다.
     * 각 1/20 균등이며 {@code data/minecraft/structure/shipwreck/} 의 파일 20개와 정확히 같다.
     */
    private static final Variant[] OCEAN = {
        ocean(Posture.WITH_MAST, Section.FULL, false),
        ocean(Posture.UPSIDEDOWN, Section.FULL, false),
        ocean(Posture.UPSIDEDOWN, Section.FRONTHALF, false),
        ocean(Posture.UPSIDEDOWN, Section.BACKHALF, false),
        ocean(Posture.SIDEWAYS, Section.FULL, false),
        ocean(Posture.SIDEWAYS, Section.FRONTHALF, false),
        ocean(Posture.SIDEWAYS, Section.BACKHALF, false),
        ocean(Posture.RIGHTSIDEUP, Section.FULL, false),
        ocean(Posture.RIGHTSIDEUP, Section.FRONTHALF, false),
        ocean(Posture.RIGHTSIDEUP, Section.BACKHALF, false),
        ocean(Posture.WITH_MAST, Section.FULL, true),
        ocean(Posture.UPSIDEDOWN, Section.FULL, true),
        ocean(Posture.UPSIDEDOWN, Section.FRONTHALF, true),
        ocean(Posture.UPSIDEDOWN, Section.BACKHALF, true),
        ocean(Posture.SIDEWAYS, Section.FULL, true),
        ocean(Posture.SIDEWAYS, Section.FRONTHALF, true),
        ocean(Posture.SIDEWAYS, Section.BACKHALF, true),
        ocean(Posture.RIGHTSIDEUP, Section.FULL, true),
        ocean(Posture.RIGHTSIDEUP, Section.FRONTHALF, true),
        ocean(Posture.RIGHTSIDEUP, Section.BACKHALF, true)
    };

    /**
     * 위키 «Shipwreck»(Java 판)이 정리한, 난파선이 쓰는 <b>주+부 수종 8조합</b>이다.
     * 바닐라는 이 조합을 <i>템플릿</i>에 고정해 두지만(각 NBT 가 자기 수종을 갖는다) 이
     * 저장소의 난파선은 NBT 가 아니라 절차적 조립이라 조합을 <b>site 에 고정</b>한다 —
     * `STRUCTURE-PALETTE.md` 의 "site 당 wood 1계열" 요구와 같은 자리다. 목록·개수는
     * 바닐라 그대로이고 무엇이 divergence 인지는 `STRUCTURE-PALETTE.md` 난파선 절에 적는다.
     */
    private static final Species[][] WOOD_PAIRS = {
        {Species.DARK_OAK, Species.JUNGLE},
        {Species.DARK_OAK, Species.SPRUCE},
        {Species.JUNGLE, Species.SPRUCE},
        {Species.OAK, Species.BIRCH},
        {Species.OAK, Species.SPRUCE},
        {Species.SPRUCE, Species.DARK_OAK},
        {Species.SPRUCE, Species.JUNGLE},
        {Species.SPRUCE, Species.OAK}
    };

    private static final int VARIANT_SALT = 0x5a17_c3e9;
    private static final int WOOD_SALT = 0x2b96_4d07;
    private static final int CHEST_ROLE_SALT = 0x64f3_10bd;

    private ShipwreckVariant() {}

    /** 이 저장소에 착지한 수종별 세트 중 난파선이 쓰는 5종. */
    public enum Species {
        OAK(Blocks.LOG, Blocks.PLANK, Blocks.WOOD_STAIRS, Blocks.PLANK_SLAB,
                Blocks.WOOD_FENCE, Blocks.WOOD_TRAPDOOR),
        BIRCH(Blocks.BIRCH_LOG, Blocks.BIRCH_PLANK, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB,
                Blocks.BIRCH_FENCE, Blocks.BIRCH_TRAPDOOR),
        SPRUCE(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANK, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB,
                Blocks.SPRUCE_FENCE, Blocks.SPRUCE_TRAPDOOR),
        JUNGLE(Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANK, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB,
                Blocks.JUNGLE_FENCE, Blocks.JUNGLE_TRAPDOOR),
        DARK_OAK(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANK, Blocks.DARK_OAK_STAIRS,
                Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_TRAPDOOR);

        private final int log;
        private final int plank;
        private final int stairs;
        private final int slab;
        private final int fence;
        private final int trapdoor;

        Species(int log, int plank, int stairs, int slab, int fence, int trapdoor) {
            this.log = log;
            this.plank = plank;
            this.stairs = stairs;
            this.slab = slab;
            this.fence = fence;
            this.trapdoor = trapdoor;
        }

        public int log() { return log; }
        public int plank() { return plank; }
        public int stairs() { return stairs; }
        public int slab() { return slab; }
        public int fence() { return fence; }
        public int trapdoor() { return trapdoor; }
    }

    private static Variant beached(Posture posture, Section section, boolean degraded) {
        return new Variant(posture, section, degraded, true);
    }

    private static Variant ocean(Posture posture, Section section, boolean degraded) {
        return new Variant(posture, section, degraded, false);
    }

    public static int beachedVariantCount() { return BEACHED.length; }

    public static int oceanVariantCount() { return OCEAN.length; }

    public static int woodPairCount() { return WOOD_PAIRS.length; }

    /** 균등 1/11(beached) 또는 1/20(해양) 추첨. */
    public static Variant variant(StructureSiteDescriptor site, boolean beached) {
        return (beached ? BEACHED : OCEAN)[variantIndex(site, beached)];
    }

    /** 뽑힌 바닐라 템플릿의 표 index. 분포 회귀가 읽는다. */
    public static int variantIndex(StructureSiteDescriptor site, boolean beached) {
        Variant[] table = beached ? BEACHED : OCEAN;
        return bounded(StructureHash.mix32(site.siteKey() ^ VARIANT_SALT), table.length);
    }

    /** 표의 index 번째 변형. 회귀가 목록 자체를 검사할 때 쓴다. */
    public static Variant variantAt(int index, boolean beached) {
        return (beached ? BEACHED : OCEAN)[index];
    }

    /** 균등 1/8 로 뽑은 주 수종. 통나무와 외장에 쓴다. */
    public static Species primarySpecies(StructureSiteDescriptor site) {
        return WOOD_PAIRS[woodPairIndex(site)][0];
    }

    /** 같은 추첨의 부 수종. 갑판·내장에 쓴다. */
    public static Species secondarySpecies(StructureSiteDescriptor site) {
        return WOOD_PAIRS[woodPairIndex(site)][1];
    }

    public static int woodPairIndex(StructureSiteDescriptor site) {
        return bounded(StructureHash.mix32(site.siteKey() ^ WOOD_SALT), WOOD_PAIRS.length);
    }

    /**
     * 상자 하나의 역할. 좌표만으로 재계산할 수 있어야 전리품 채움 경로(상자 첫 개방)가
     * 생성기 없이도 같은 표를 고른다. 생성기는 세 구획마다 이 값이 원하는 역할이 나오는
     * 칸을 골라 상자를 놓으므로, 한 난파선에는 바닐라처럼 역할이 겹치지 않는 상자가 놓인다.
     */
    public static ChestRole chestRole(int seed, int x, int y, int z) {
        int lane = StructureHash.hash3(StructureHash.seedSalt(seed, CHEST_ROLE_SALT), x, y, z);
        return ChestRole.values()[bounded(lane, ChestRole.values().length)];
    }

    private static int bounded(int value, int bound) {
        return (int) Long.remainderUnsigned(StructureHash.unsigned(value), bound);
    }
}
