package com.gameexpert.engine.mob;

import java.util.Set;

/**
 * 1.21.5 "Spring to Life" 가 추가한 <b>소·돼지·닭 기후 변종</b>과 여우 눈 변종의 바이옴 판정.
 *
 * <p>수치 정본과 근거는 {@code docs/research/mc-1215/FARM_ANIMAL_VARIANTS.md} 다(등급 [B]:
 * 위키가 옮긴 `#minecraft:spawns_warm_variant_farm_animals` /
 * `#minecraft:spawns_cold_variant_farm_animals` 멤버 목록. 태그 JSON [A] 원문은 미확보).
 * standalone 권위는 같은 표를 {@code StandaloneMobRules.ts} 에 복제하고 두 사본은
 * 자연 스폰 파리티 게이트가 대조한다.
 *
 * <p><b>온도값이 아니라 태그 멤버십이 정본이다.</b> `stony_peaks`(온도 1.0)가 cold 에,
 * `taiga` 계열이 cold 에 들어 있어 온도 임계로는 재현되지 않는다. 그래서 아래 두 집합은
 * 바이옴 id 를 그대로 옮긴 것이고 {@link com.gameexpert.terrain.mc.biome.McBiomeRegistry}
 * 의 온도 필드를 읽지 않는다.
 *
 * <p><b>개구리 표와 공유하지 않는다.</b> `spawns_*_variant_frogs` 는 별개 태그이고 멤버가
 * 다르다(연구 문서 §3). 개구리 표는 {@link MobRuntime#frogVariantForBiome} 가 계속 소유한다.
 */
public final class FarmAnimalVariantRules {
    private FarmAnimalVariantRules() {}

    /** 목록에 없는 모든 바이옴·스폰 알·명령의 기본값(«Pig»: "a temperate pig is spawned"). */
    public static final String TEMPERATE = "temperate";
    public static final String WARM = "warm";
    public static final String COLD = "cold";

    /** 세 기후의 선언 순서. 영속·프로토콜 어휘이므로 순서를 바꾸지 않는다. */
    public static final String[] CLIMATES = {TEMPERATE, WARM, COLD};

    /**
     * `#minecraft:spawns_warm_variant_farm_animals` 를 이 저장소 바이옴 id 로 옮긴 집합(14).
     * 태그 원문의 `#is_nether` 는 이 저장소에 대응 바이옴이 없어 빠진다.
     */
    private static final Set<Integer> WARM_BIOMES = Set.of(
            2,    // desert
            21,   // jungle            (#is_jungle)
            23,   // sparse_jungle     (#is_jungle)
            35,   // savanna           (#is_savanna)
            36,   // savanna_plateau   (#is_savanna)
            37,   // badlands          (#is_badlands)
            38,   // wooded_badlands   (#is_badlands)
            44,   // warm_ocean
            45,   // lukewarm_ocean
            48,   // deep_lukewarm_ocean
            163,  // windswept_savanna (#is_savanna)
            165,  // eroded_badlands   (#is_badlands)
            168,  // bamboo_jungle     (#is_jungle)
            184); // mangrove_swamp

    /**
     * `#minecraft:spawns_cold_variant_farm_animals` 를 이 저장소 바이옴 id 로 옮긴 집합.
     * 태그 원문의 `#is_end` 만 이 Overworld 제품에 대응 바이옴이 없어 빠진다.
     */
    private static final Set<Integer> COLD_BIOMES = Set.of(
            3,    // windswept_hills
            5,    // taiga
            10,   // frozen_ocean
            11,   // frozen_river
            12,   // snowy_plains
            26,   // snowy_beach
            30,   // snowy_taiga
            32,   // old_growth_pine_taiga
            34,   // windswept_forest
            46,   // cold_ocean
            49,   // deep_cold_ocean
            50,   // deep_frozen_ocean
            131,  // windswept_gravelly_hills
            140,  // ice_spikes
            160,  // old_growth_spruce_taiga
            178,  // grove
            179,  // snowy_slopes
            180,  // jagged_peaks
            181,  // frozen_peaks
            182,  // stony_peaks
            183,  // deep_dark
            DappledForestSpawnZone.BIOME_ID); // dappled_forest

    /**
     * 소·돼지·닭이 이 바이옴에서 스폰될 때의 기후. 두 태그는 서로소라 우선순위 정수가
     * 없어도 판정이 유일하다(연구 문서 §5).
     */
    public static String climateForBiome(int biome) {
        if (WARM_BIOMES.contains(biome)) return WARM;
        if (COLD_BIOMES.contains(biome)) return COLD;
        return TEMPERATE;
    }

    public static boolean isClimate(String value) {
        return TEMPERATE.equals(value) || WARM.equals(value) || COLD.equals(value);
    }

    /**
     * 눈 여우 바이옴. «Fox»: <i>"snow foxes spawn in groves and snowy taigas"</i> →
     * grove(178) · snowy_taiga(30). 바닐라 `BiomeTags.SPAWNS_SNOW_FOXES` 에 해당한다.
     */
    private static final Set<Integer> SNOW_FOX_BIOMES = Set.of(30, 178);

    public static String foxVariantForBiome(int biome) {
        return SNOW_FOX_BIOMES.contains(biome) ? "snow" : "red";
    }

    // ── 소: 성별 축과 기후 축의 합성 ────────────────────────────────────────────────
    // WebCraft 소는 바닐라에 없는 **성별** 축을 이미 variant 문자열로 쓰고 있다(CowSex).
    // 1.21.5 기후 축을 얹으면서 새 프로토콜 필드를 만들지 않고 같은 문자열에 접미사로
    // 합성한다. 그래서 **temperate 소의 이름은 옛 이름 그대로**이고(`female`/`male`),
    // 도입 전 세이브의 모든 소 행이 재검증 없이 temperate 로 읽힌다
    // (열대어 variant=null 승격과 같은 마이그레이션 계약).
    //
    //   temperate → "female"       / "male"
    //   warm      → "female_warm"  / "male_warm"
    //   cold      → "female_cold"  / "male_cold"
    //
    // 성별 추첨은 손대지 않는다: MobType.COW 의 가중표(male,female,female,female)와
    // CowSex.deterministic 의 4분 해시는 **글자 그대로 그대로**이고, 기후는 그 뒤에
    // 순수 함수로 붙는다. 즉 이 웨이브는 소의 난수 프리픽스를 한 비트도 바꾸지 않는다.

    /** 소 변종 어휘 전체(성별 2 × 기후 3). 선언 순서는 영속·프로토콜 정체성이다. */
    public static final String[] COW_VARIANTS = {
        "female", "male", "female_warm", "male_warm", "female_cold", "male_cold",
    };

    /** {@code sex} 는 {@code "female"}/{@code "male"}, {@code climate} 는 세 기후 중 하나. */
    public static String cowVariant(String sex, String climate) {
        if (climate == null || TEMPERATE.equals(climate)) return sex;
        return sex + '_' + climate;
    }

    /**
     * 스폰 경로가 준 값을 소 변종으로 해석한다. 스포너는 <b>기후 낱말</b>만 주고 성별은
     * {@link MobFactory} 가 결정론 해시로 뽑으므로 여기서 합성한다. 영속 복원처럼 완성된
     * 소 변종이 들어오면 그대로 쓴다.
     */
    public static String resolveCowVariant(String sex, String requested) {
        if (requested == null) return sex;
        if (isClimate(requested)) return cowVariant(sex, requested);
        return requested;
    }

    /** 소 변종에서 성별만 떼어낸다. 밀크·번식 판정은 기후를 보지 않는다. */
    public static String cowSexOf(String cowVariant) {
        if (cowVariant == null) return null;
        int cut = cowVariant.indexOf('_');
        return cut < 0 ? cowVariant : cowVariant.substring(0, cut);
    }

    /** 소 변종의 기후. 접미사가 없으면 temperate 다. */
    public static String cowClimateOf(String cowVariant) {
        if (cowVariant == null) return null;
        int cut = cowVariant.indexOf('_');
        return cut < 0 ? TEMPERATE : cowVariant.substring(cut + 1);
    }

    public static boolean acceptsCowVariant(String variant) {
        for (String candidate : COW_VARIANTS) if (candidate.equals(variant)) return true;
        return false;
    }
}
