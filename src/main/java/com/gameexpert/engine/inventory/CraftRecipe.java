package com.gameexpert.engine.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.terrain.Blocks;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 서버 권위 제작 레시피. 실제 2×2/3×3 격자의 아이템 배치를 대조하며, 결과 슬롯을 가져갈 때
 * 차지한 각 칸에서 한 개씩 소모합니다. itemType은 공유 블록/아이템 ID 계약을 따릅니다.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class CraftRecipe {

    private final String id;
    private final List<Ingredient> inputs;
    private final short outputType;
    private final int outputCount;
    private final List<Short> pattern;
    private final boolean requiresFurnace;
    private final boolean requiresTable;
    /**
     * [POTION] 양조대 앞에서만 성립하는 레시피인가. 양조대 세션은 오직 이 레시피만 맞추고,
     * 일반 격자(인벤·작업대)는 이 레시피를 절대 맞추지 않는다.
     */
    private final boolean requiresBrewing;
    /**
     * [STONECUT] 석재 절단기 앞에서만 성립하는 레시피인가. 절단 세션은 오직 이 레시피만
     * 맞추고, 일반 격자(인벤·작업대)와 양조대는 이 레시피를 절대 맞추지 않는다.
     *
     * <p>절단 레시피는 재료가 언제나 <b>블록 한 개</b> 하나뿐이라 무형 한 칸으로 표현한다
     * (바닐라 {@code minecraft:stonecutting} 도 ingredient 하나 + result 하나다). 같은 입력에서
     * 여러 산출이 나오므로 격자만으로는 결정되지 않으며, 어느 산출인지는 세션이 들고 있는
     * <b>선택 레시피 id</b> 가 정한다 — 그 선택을 권위가 {@link #matchStonecutting} 으로
     * 재검증한다.
     */
    private final boolean requiresStonecutter;

    /** 레시피 재료 한 종류(아이템 종류 + 필요 개수). */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Ingredient {
        private final short itemType;
        private final int count;
    }

    private static final Map<String, CraftRecipe> BY_ID = new LinkedHashMap<>();
    private static final List<CraftRecipe> ALL;

    /** 무형 제작. 재료 수가 네 개를 넘으면 3×3 작업대가 필요합니다. */
    private static void register(String id, short outputType, int outputCount, Ingredient... inputs) {
        int occupiedSlots = 0;
        for (Ingredient input : inputs) occupiedSlots += input.count;
        BY_ID.put(id, new CraftRecipe(id, List.of(inputs), outputType, outputCount,
                List.of(), false, occupiedSlots > 4, false, false));
    }

    /** 0을 빈 칸으로 쓰는 3×3 행 우선 모양. 실제 차지 영역이 2×2를 넘으면 작업대가 필요합니다. */
    private static void registerShaped(String id, short outputType, int outputCount,
            List<Short> pattern, Ingredient... inputs) {
        if (pattern.size() != 9) {
            throw new IllegalStateException("crafting pattern must contain exactly 9 cells");
        }
        CompactShape shape = compact(pattern, 3);
        if (shape == null) throw new IllegalStateException("crafting pattern cannot be empty");
        BY_ID.put(id, new CraftRecipe(id, List.of(inputs), outputType, outputCount,
                pattern, false, shape.width > 2 || shape.height > 2, false, false));
    }

    /**
     * [POTION] 양조 레시피. 바닐라 양조는 바탕 물약 1 + 재료 1 이라 무형 두 칸으로 표현하고,
     * 양조대 앞에서 연 세션에서만 성립한다. 산출은 언제나 1 개다.
     */
    private static void registerBrew(String id, short outputType, short base, short ingredient) {
        BY_ID.put(id, new CraftRecipe(id,
                List.of(new Ingredient(base, 1), new Ingredient(ingredient, 1)),
                outputType, 1, List.of(), false, false, true, false));
    }

    /** 화로 레시피 카탈로그 메타데이터. 실행은 좌표 화로의 입력/연료/출력 슬롯이 담당합니다. */
    private static void registerSmelt(String id, short outputType, int outputCount, Ingredient... inputs) {
        BY_ID.put(id, new CraftRecipe(id, List.of(inputs), outputType, outputCount,
                List.of(), true, false, false, false));
    }

    /**
     * [STONECUT] 절단 레시피. 재료는 언제나 입력 블록 한 개고 산출량만 형상마다 다르다
     * (바닐라 {@code recipes/*_from_*_stonecutting.json} 의 {@code ingredient}/{@code count}).
     */
    private static void registerStonecut(String id, short outputType, int outputCount,
            short input) {
        BY_ID.put(id, new CraftRecipe(id, List.of(new Ingredient(input, 1)),
                outputType, outputCount, List.of(), false, false, false, true));
    }

    private static List<Short> shape(int... cells) {
        if (cells.length != 9) {
            throw new IllegalArgumentException("crafting shape must contain exactly 9 cells");
        }
        var result = new ArrayList<Short>(cells.length);
        for (int cell : cells) result.add((short) cell);
        return List.copyOf(result);
    }

    /**
     * [ARMOR-TRIM] 갑옷 장식 형판 복제(바닐라 recipe/<x>_armor_trim_smithing_template.json).
     * 모양 "#S#","#C#","###" · 산출 2. id 는 바닐라 레시피 이름(+ 대안 재료 접미사)이다.
     */
    private static void registerTrimTemplateDuplication(String pattern, short template,
            short material) {
        String id = pattern.contains("_from_")
                ? pattern.replace("_from_", "_armor_trim_smithing_template_from_")
                : pattern + "_armor_trim_smithing_template";
        registerShaped(id, template, 2,
                shape(PlayerInventory.DIAMOND, template, PlayerInventory.DIAMOND,
                        PlayerInventory.DIAMOND, material, PlayerInventory.DIAMOND,
                        PlayerInventory.DIAMOND, PlayerInventory.DIAMOND, PlayerInventory.DIAMOND),
                new Ingredient(PlayerInventory.DIAMOND, 7),
                new Ingredient(template, 1),
                new Ingredient(material, 1));
    }

    private static void registerArmorSet(String prefix, short material,
            short helmet, short chestplate, short leggings, short boots) {
        registerShaped(prefix + "_helmet", helmet, 1,
                shape(material, material, material, material, 0, material, 0, 0, 0),
                new Ingredient(material, 5));
        registerShaped(prefix + "_chestplate", chestplate, 1,
                shape(material, 0, material, material, material, material,
                        material, material, material),
                new Ingredient(material, 8));
        registerShaped(prefix + "_leggings", leggings, 1,
                shape(material, material, material, material, 0, material,
                        material, 0, material),
                new Ingredient(material, 7));
        registerShaped(prefix + "_boots", boots, 1,
                shape(material, 0, material, material, 0, material, 0, 0, 0),
                new Ingredient(material, 4));
    }

    /**
     * 계단(재료 6 → 4) · 반 블록(재료 3 → 6) · 담장(재료 6 → 6) 세 변형을 한 재료에서 등록한다.
     * 배치·산출량은 MC Java 1.21.4 의 석재 계열 공용 레시피와 같다.
     */
    private static void registerDeepslateShapes(String prefix, short material,
            short stairs, short slab, short wall) {
        registerShaped(prefix + "_stairs", stairs, 4,
                shape(material, 0, 0, material, material, 0, material, material, material),
                new Ingredient(material, 6));
        registerShaped(prefix + "_slab", slab, 6,
                shape(material, material, material, 0, 0, 0, 0, 0, 0),
                new Ingredient(material, 3));
        registerShaped(prefix + "_wall", wall, 6,
                shape(material, material, material, material, material, material, 0, 0, 0),
                new Ingredient(material, 6));
    }

    /**
     * [STONE-RESIDUAL] 계단·반 블록만 있고 **담장이 없는** 재질(다듬은 화성암 3종·매끄러운
     * 사암 2종)용. 바닐라에 없는 담장을 만들어 내지 않으려고 위 세 형상 헬퍼와 분리한다.
     */
    private static void registerStairsAndSlab(String prefix, short material,
            short stairs, short slab) {
        registerShaped(prefix + "_stairs", stairs, 4,
                shape(material, 0, 0, material, material, 0, material, material, material),
                new Ingredient(material, 6));
        registerShaped(prefix + "_slab", slab, 6,
                shape(material, material, material, 0, 0, 0, 0, 0, 0),
                new Ingredient(material, 3));
    }

    /** [STONE-RESIDUAL] 2×2 가공(재료 4 → 산출 4). 다듬은/잘린 계열이 공유하는 배치다. */
    private static void registerQuarry(String id, short material, short output) {
        registerShaped(id, output, 4,
                shape(material, material, 0, material, material, 0, 0, 0, 0),
                new Ingredient(material, 4));
    }

    /**
     * [STONE-RESIDUAL] 조각된(chiseled) 변형: 같은 재질 반 블록 2개를 **세로로** 쌓아 1개.
     * 바닐라 chiseled_stone_bricks / chiseled_red_sandstone / chiseled_tuff 가 공유한다.
     */
    private static void registerChiseled(String id, short slab, short output) {
        registerShaped(id, output, 1,
                shape(slab, 0, 0, slab, 0, 0, 0, 0, 0),
                new Ingredient(slab, 2));
    }

    /**
     * [QUARTZ] 기둥 변형: 같은 재질 블록 2개를 <b>세로로</b> 쌓아 2개. 바닐라
     * {@code quartz_pillar.json}(그리고 purpur_pillar) 이 쓰는 배치이며, 조각된 변형과
     * 모양은 같지만 재료가 반 블록이 아니라 완전 큐브이고 산출이 2 라 따로 둔다.
     */
    private static void registerPillar(String id, short material, short output) {
        registerShaped(id, output, 2,
                shape(material, 0, 0, material, 0, 0, 0, 0, 0),
                new Ingredient(material, 2));
    }

    /**
     * [QUARTZ] 담장만 있는 재질용(재료 6 → 담장 6). 계단·반 블록이 바닐라에 없는 재질에
     * 담장만 붙이는 자리라 {@link #registerDeepslateShapes} 와 분리한다.
     */
    private static void registerWall(String id, short material, short wall) {
        registerShaped(id, wall, 6,
                shape(material, material, material, material, material, material, 0, 0, 0),
                new Ingredient(material, 6));
    }

    /**
     * [COPPER] 구리 격자: 같은 단계 구리 블록 4 개를 <b>마름모</b>로 놓아 4 개
     * (바닐라 {@code copper_grate.json} 의 {@code " # " / "# #" / " # "} 배치).
     * 2×2 가공({@link #registerQuarry})과 개수는 같지만 배치가 달라 따로 둔다.
     */
    /**
     * [COPPER] 산화 단계별 레시피 id 접두. 바닐라 recipes 파일명과 문자 그대로 같다
     * ({@code cut_copper} / {@code exposed_cut_copper} / …).
     */
    private static final String[] COPPER_STAGE_RECIPE_PREFIX = {
            "", "exposed_", "weathered_", "oxidized_",
    };

    /**
     * 1.21.9 {@code waxed_*_from_honeycomb} 의 아이템 형상군. 점등 전구와 X/Z 사슬은 월드
     * 내부 ID일 뿐 인벤토리 아이템이 아니므로 각각 꺼진 전구와 Y축 사슬 한 행만 등록한다.
     */
    private static final int[] COPPER_WAX_FAMILY_INDEXES = {
            0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 14,
    };
    private static final String[] COPPER_WAX_FAMILY_NAMES = {
            "copper_block", "cut_copper", "cut_copper_stairs", "cut_copper_slab",
            "chiseled_copper", "copper_grate", "copper_bulb", "copper_door",
            "copper_trapdoor", "copper_bars", "copper_chain", "copper_chest",
    };

    private static void registerCopperWaxRecipes() {
        for (int family = 0; family < COPPER_WAX_FAMILY_INDEXES.length; family++) {
            int index = COPPER_WAX_FAMILY_INDEXES[family];
            for (int stage = 0; stage < Blocks.COPPER_OXIDATION_STAGES; stage++) {
                register("waxed_" + COPPER_STAGE_RECIPE_PREFIX[stage]
                                + COPPER_WAX_FAMILY_NAMES[family] + "_from_honeycomb",
                        (short) Blocks.WAXED_COPPER_FAMILIES[index][stage], 1,
                        new Ingredient((short) Blocks.COPPER_OXIDATION_FAMILIES[index][stage], 1),
                        new Ingredient(PlayerInventory.HONEYCOMB, 1));
            }
        }
        int[][] plain = {
                { Blocks.COPPER_LANTERN, Blocks.EXPOSED_COPPER_LANTERN,
                        Blocks.WEATHERED_COPPER_LANTERN, Blocks.OXIDIZED_COPPER_LANTERN },
                { Blocks.LIGHTNING_ROD, Blocks.EXPOSED_LIGHTNING_ROD,
                        Blocks.WEATHERED_LIGHTNING_ROD, Blocks.OXIDIZED_LIGHTNING_ROD },
                { Blocks.COPPER_GOLEM_STATUE, Blocks.EXPOSED_COPPER_GOLEM_STATUE,
                        Blocks.WEATHERED_COPPER_GOLEM_STATUE, Blocks.OXIDIZED_COPPER_GOLEM_STATUE },
        };
        int[][] waxed = {
                { Blocks.WAXED_COPPER_LANTERN, Blocks.WAXED_EXPOSED_COPPER_LANTERN,
                        Blocks.WAXED_WEATHERED_COPPER_LANTERN, Blocks.WAXED_OXIDIZED_COPPER_LANTERN },
                { Blocks.WAXED_LIGHTNING_ROD, Blocks.WAXED_EXPOSED_LIGHTNING_ROD,
                        Blocks.WAXED_WEATHERED_LIGHTNING_ROD, Blocks.WAXED_OXIDIZED_LIGHTNING_ROD },
                { Blocks.WAXED_COPPER_GOLEM_STATUE, Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE,
                        Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE,
                        Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE },
        };
        String[] names = { "copper_lantern", "lightning_rod", "copper_golem_statue" };
        for (int family = 0; family < names.length; family++) {
            for (int stage = 0; stage < Blocks.COPPER_OXIDATION_STAGES; stage++) {
                register("waxed_" + COPPER_STAGE_RECIPE_PREFIX[stage] + names[family]
                                + "_from_honeycomb",
                        (short) waxed[family][stage], 1,
                        new Ingredient((short) plain[family][stage], 1),
                        new Ingredient(PlayerInventory.HONEYCOMB, 1));
            }
        }
    }

    private static void registerGrate(String id, short material, short output) {
        registerShaped(id, output, 4,
                shape(0, material, 0, material, 0, material, 0, material, 0),
                new Ingredient(material, 4));
    }

    /**
     * [COPPER] 구리 전구: 같은 단계 구리 블록 3 + 블레이즈 막대 1 + 레드스톤 가루 1 → 4 개
     * (바닐라 {@code copper_bulb.json} 의 {@code " C " / "CBC" / " R "} 배치).
     *
     * <p>블레이즈 막대는 이 저장소에서 재료 대체 없이 그대로 쓴다 — 네더가 없는 것은 재료가
     * 아니라 획득 경로의 문제라 [POTION] 트랙이 이미 레이드 승리 보상·던전 상자 경로를
     * 신설해 두었다(양조대와 같은 근거).
     */
    private static void registerBulb(String id, short material, short output) {
        registerShaped(id, output, 4,
                shape(0, material, 0,
                        material, PlayerInventory.BLAZE_ROD, material,
                        0, PlayerInventory.REDSTONE_DUST, 0),
                new Ingredient(material, 3),
                new Ingredient(PlayerInventory.BLAZE_ROD, 1),
                new Ingredient(PlayerInventory.REDSTONE_DUST, 1));
    }


    /**
     * [WOOL-COLOR] 색 하나의 양털·카펫 제작식을 한 번에 등록한다(MC Java 1.21.4).
     *   · {@code dye_<color>_wool}   무형 {양털 태그 1 + 염료 1} → 그 색 양털 1
     *   · {@code <color>_carpet}     양털 2 가로 → 카펫 3
     *   · {@code dye_<color>_carpet} 흰 카펫 8 테두리 + 염료 1 → 그 색 카펫 8 (흰색은 없음)
     * 바닐라 양털 염색의 재료는 {@code #minecraft:wool} 태그이므로, 서버 표는 대표값으로
     * {@code WHITE_WOOL} 을 적고 {@link #acceptsItem} 이 {@code dye_} 접두사 레시피에서만
     * 양털 16색 전부를 받아 준다. 태그를 흉내 내는 기존 원목(LOG) 규칙과 같은 방식이다.
     */
    private static void registerWoolColor(String color, short wool, short carpet, short dye) {
        register("dye_" + color + "_wool", wool, 1,
                new Ingredient((short) Blocks.WHITE_WOOL, 1), new Ingredient(dye, 1));
        registerShaped(color + "_carpet", carpet, 3,
                shape(wool, wool, 0, 0, 0, 0, 0, 0, 0),
                new Ingredient(wool, 2));
        if (carpet == (short) Blocks.WHITE_CARPET) return;
        registerShaped("dye_" + color + "_carpet", carpet, 8,
                shape(Blocks.WHITE_CARPET, Blocks.WHITE_CARPET, Blocks.WHITE_CARPET,
                        Blocks.WHITE_CARPET, dye, Blocks.WHITE_CARPET,
                        Blocks.WHITE_CARPET, Blocks.WHITE_CARPET, Blocks.WHITE_CARPET),
                new Ingredient((short) Blocks.WHITE_CARPET, 8), new Ingredient(dye, 1));
    }

    /**
     * [STAINED-GLASS] 색 하나의 색 유리·색 유리판 제작식을 등록한다(MC Java 1.21.4).
     *   · {@code <color>_stained_glass}                        유리 8 테두리 + 염료 1 → 그 색 유리 8
     *   · {@code <color>_stained_glass_pane}                   그 색 유리 6 (2×3) → 그 색 유리판 16
     *   · {@code <color>_stained_glass_pane_from_glass_pane}   유리판 8 테두리 + 염료 1 → 그 색 유리판 8
     *
     * <p>세 배치·산출량 모두 바닐라 {@code recipes/<color>_stained_glass*.json} 그대로다.
     * 재료는 태그가 아니라 {@code minecraft:glass} · {@code minecraft:glass_pane} 정확 일치라
     * {@link #acceptsItem} 확장이 필요 없다 — 색 유리를 다시 염색하는 레시피는 바닐라에 없다.
     */
    private static void registerStainedGlassColor(String color, short glass, short pane, short dye) {
        registerShaped(color + "_stained_glass", glass, 8,
                shape(Blocks.GLASS, Blocks.GLASS, Blocks.GLASS,
                        Blocks.GLASS, dye, Blocks.GLASS,
                        Blocks.GLASS, Blocks.GLASS, Blocks.GLASS),
                new Ingredient((short) Blocks.GLASS, 8), new Ingredient(dye, 1));
        registerShaped(color + "_stained_glass_pane", pane, 16,
                shape(glass, glass, glass, glass, glass, glass, 0, 0, 0),
                new Ingredient(glass, 6));
        registerShaped(color + "_stained_glass_pane_from_glass_pane", pane, 8,
                shape(Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE,
                        Blocks.GLASS_PANE, dye, Blocks.GLASS_PANE,
                        Blocks.GLASS_PANE, Blocks.GLASS_PANE, Blocks.GLASS_PANE),
                new Ingredient((short) Blocks.GLASS_PANE, 8), new Ingredient(dye, 1));
    }

    /**
     * [CONCRETE] 색 인덱스(DyeColor 네트워크 ID) 순 레시피 ID 접미사. 양털·침대·색 유리가
     * 색마다 호출을 한 줄씩 적은 것과 달리 이 트랙은 계열이 셋이라 48줄이 되므로 표로 돈다.
     */
    private static final String[] COLOR_KEYS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    /** 색 인덱스 → 염료 아이템. {@link #COLOR_KEYS} 와 같은 순서다. */
    private static final short[] DYE_BY_COLOR = {
            PlayerInventory.WHITE_DYE, PlayerInventory.ORANGE_DYE, PlayerInventory.MAGENTA_DYE,
            PlayerInventory.LIGHT_BLUE_DYE, PlayerInventory.YELLOW_DYE, PlayerInventory.LIME_DYE,
            PlayerInventory.PINK_DYE, PlayerInventory.GRAY_DYE, PlayerInventory.LIGHT_GRAY_DYE,
            PlayerInventory.CYAN_DYE, PlayerInventory.PURPLE_DYE, PlayerInventory.BLUE_DYE,
            PlayerInventory.BROWN_DYE, PlayerInventory.GREEN_DYE, PlayerInventory.RED_DYE,
            PlayerInventory.BLACK_DYE,
    };

    /**
     * [CONCRETE] 색 하나의 테라코타·유광 테라코타·콘크리트 가루 제작식을 등록한다
     * (MC Java 1.21.4).
     *   · {@code <color>_terracotta}          총칭 테라코타 8 테두리 + 염료 1 → 그 색 8 (3×3)
     *   · {@code <color>_glazed_terracotta}   그 색 테라코타 1 제련 → 유광 1
     *   · {@code <color>_concrete_powder}     무형 {모래 4 + 자갈 4 + 염료 1} → 가루 8
     *
     * <p>바닐라 염색 테라코타는 <b>흰색까지 포함해 16색 전부</b>에 레시피가 있다(총칭
     * 테라코타와 흰색 테라코타는 다른 블록이다). 카펫 염색이 흰색만 빠지는 것과 다르다.
     *
     * <p>콘크리트 가루의 모래·자갈은 바닐라도 태그가 아니라 {@code minecraft:sand} ·
     * {@code minecraft:gravel} 아이템 정확 일치라 붉은 모래는 받지 않는다 — 그대로 옮긴다.
     */
    private static void registerTerracottaColor(String color, short terracotta,
            short glazed, short concretePowder, short dye) {
        registerShaped(color + "_terracotta", terracotta, 8,
                shape(Blocks.TERRACOTTA, Blocks.TERRACOTTA, Blocks.TERRACOTTA,
                        Blocks.TERRACOTTA, dye, Blocks.TERRACOTTA,
                        Blocks.TERRACOTTA, Blocks.TERRACOTTA, Blocks.TERRACOTTA),
                new Ingredient((short) Blocks.TERRACOTTA, 8), new Ingredient(dye, 1));
        registerSmelt(color + "_glazed_terracotta", glazed, 1,
                new Ingredient(terracotta, 1));
        register(color + "_concrete_powder", concretePowder, 8,
                new Ingredient((short) Blocks.SAND, 4),
                new Ingredient((short) Blocks.GRAVEL, 4),
                new Ingredient(dye, 1));
    }

    /**
     * [BED-COLOR] 색 하나의 침대 제작식을 등록한다(MC Java 1.21.4).
     *   · {@code <color>_bed}                같은 색 양털 3 가로 + 판자 3 가로 → 그 색 침대 1
     *   · {@code <color>_bed_from_white_bed} 무형 {흰 침대 1 + 염료 1} → 그 색 침대 1(흰색 제외)
     *
     * <p>바닐라 침대 제작은 양털이 <b>수종·색 정확 일치</b>다(태그가 아니라 색마다 별도 레시피).
     * 판자는 {@code #minecraft:planks} 태그라 어느 수종이든 받는다 — 기존 PLANK 규칙 그대로다.
     * 염색 레시피가 흰 침대만 받는 것도 바닐라 그대로다({@code <color>_bed_from_white_bed}).
     */
    /**
     * MC {@code DyeColor} 네트워크 ID 순서의 색 이름. 레시피 ID 문자열을 색마다 손으로 적으면
     * 표와 순서가 조용히 갈리므로, 색 계열을 표로 도는 등록부는 이 하나만 읽는다.
     * {@link Blocks#WOOL_BY_DYE_COLOR} · {@link Blocks#CUSHION_BY_DYE_COLOR} 와 같은 순서다.
     */
    private static final String[] DYE_COLOR_NAMES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    };

    /**
     * 수종 인덱스 0..7 의 레시피 ID 조각. {@link Blocks#LOG_BY_WOOD_SPECIES} ·
     * {@link Blocks#STRIPPED_LOG_BY_SPECIES_AND_AXIS} · {@link Blocks#SHELF_BY_WOOD_SPECIES}
     * 앞 여덟 칸과 같은 순서다. 수종 계열을 표로 도는 등록부는 이 하나만 읽어, 이름과 표가
     * 조용히 어긋나는 것을 막는다(색 계열의 {@link #DYE_COLOR_NAMES} 와 같은 이유).
     */
    private static final String[] WOOD_SPECIES_NAMES = {
            "oak", "birch", "spruce", "jungle", "acacia", "dark_oak", "cherry", "mangrove",
    };

    /** Snapshot-7: three same-color wool slabs in one row produce one cushion entity item. */
    private static void registerCushionColor(String color, short cushion, short woolSlab,
            short dye) {
        registerShaped(color + "_cushion", cushion, 1,
                shape(woolSlab, woolSlab, woolSlab, 0, 0, 0, 0, 0, 0),
                new Ingredient(woolSlab, 3));
        register("dye_" + color + "_cushion", cushion, 1,
                new Ingredient((short) Blocks.WHITE_CUSHION, 1), new Ingredient(dye, 1));
    }

    private static void registerBedColor(String color, short bed, short wool, short dye) {
        registerShaped(color + "_bed", bed, 1,
                shape(wool, wool, wool, Blocks.PLANK, Blocks.PLANK, Blocks.PLANK, 0, 0, 0),
                new Ingredient(wool, 3), new Ingredient((short) Blocks.PLANK, 3));
        if (bed == (short) Blocks.WHITE_BED) return;
        register(color + "_bed_from_white_bed", bed, 1,
                new Ingredient((short) Blocks.WHITE_BED, 1), new Ingredient(dye, 1));
    }

    /**
     * 한 수종의 목재 건축 여섯 형상. 배치·산출량은 MC Java 1.21.4 의 목재 계열 공용 레시피와
     * 같다: 계단 6→4 · 반 블록 3→6 · 울타리 판자4+막대2→3 · 울타리문 판자2+막대4→1 ·
     * 다락문 6→2 · 문 6→3. 참나무 총칭 세트의 기존 여섯 레시피와 모양이 정확히 같고 재료만
     * 그 수종의 판자다(바닐라도 목재 형상 레시피는 #planks 태그가 아니라 수종 정확 일치다).
     */
    private static void registerWoodSpeciesShapes(String prefix, short plank,
            short stairs, short slab, short fence, short gate, short trapdoor, short door) {
        registerShaped(prefix + "_stairs", stairs, 4,
                shape(plank, 0, 0, plank, plank, 0, plank, plank, plank),
                new Ingredient(plank, 6));
        registerShaped(prefix + "_slab", slab, 6,
                shape(plank, plank, plank, 0, 0, 0, 0, 0, 0),
                new Ingredient(plank, 3));
        registerShaped(prefix + "_fence", fence, 3,
                shape(plank, PlayerInventory.STICK, plank,
                        plank, PlayerInventory.STICK, plank, 0, 0, 0),
                new Ingredient(plank, 4), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped(prefix + "_fence_gate", gate, 1,
                shape(PlayerInventory.STICK, plank, PlayerInventory.STICK,
                        PlayerInventory.STICK, plank, PlayerInventory.STICK, 0, 0, 0),
                new Ingredient(plank, 2), new Ingredient(PlayerInventory.STICK, 4));
        registerShaped(prefix + "_trapdoor", trapdoor, 2,
                shape(plank, plank, plank, plank, plank, plank, 0, 0, 0),
                new Ingredient(plank, 6));
        registerShaped(prefix + "_door", door, 3,
                shape(plank, plank, 0, plank, plank, 0, plank, plank, 0),
                new Ingredient(plank, 6));
    }

    // ── [STONECUT] 절단표 파생 ────────────────────────────────────────────────
    //
    // 바닐라 규약(MC Java 1.21.4 data/minecraft/recipe/*_from_*_stonecutting.json):
    //   · 입력은 언제나 **완전 블록 한 개**이고 산출은 하류 형상 하나다.
    //   · 하류 형상은 **중간 단계를 건너뛸 수 있다** — stone 하나로 chiseled_stone_bricks 가
    //     바로 나온다(stone → stone_bricks → …_slab → chiseled 사슬을 다 건너뛴다).
    //   · 수량은 반 블록만 1:2 이고 계단·담장·가공형은 전부 1:1 이다(제작대의 6→4 와 다르다).
    //   · 예외는 구리 **블록** 하나뿐이다 — 구리 블록을 넣으면 산출이 전부 4배다
    //     (잘린 구리 4 · 잘린 구리 계단 4 · 잘린 구리 반 블록 8 · 조각된 구리 4).
    //     근거 등급 A — minecraft.wiki/w/Stonecutter: "The recipe for cut copper is even more
    //     efficient: with stonecutting, 1 block of copper turns into 4 blocks of cut copper,
    //     allowing the amount of copper blocks to be quadrupled." 같은 문서 역사 절의
    //     "A block of copper can now be converted to 4 blocks of cut copper when using the
    //     stonecutter"(Java 1.18, 21w40a)도 같은 값이다. 잘린 구리부터는 다시 석재 규약이다.
    //
    // 그래서 이 절은 **표를 적지 않는다**. 이미 착지된 형상 정본(=제작대 레시피 표)에서
    // "형상 간선"을 뽑아 도달 가능성을 닫는다. 새 계열이 형상 헬퍼로 등록되는 순간 절단표가
    // 자동으로 그만큼 넓어지고, 여기 코드는 그대로다.
    //
    // 형상 간선의 정의(모두 이미 정본에 있는 값이다):
    //   ① 제련·양조가 아닌 **모양 있는** 레시피이고 재료가 한 종류뿐이다.
    //   ② 배치가 석재 계열 공용 여섯 배치(계단·반·담장·2×2 가공·조각·기둥) 중 하나와
    //      **문자 그대로 같다**(재료만 치환). 배치를 대조하므로 개수만 같은 구리 격자
    //      (마름모 4→4)나 화로(조약돌 8)는 절대 들어오지 않는다.
    //   ③ 입력·산출이 모두 설치 가능한 블록이고 **맨손으로 회수되지 않는다**(= 곡괭이 계열).
    //      판자·양털·모래처럼 도구가 필요 없는 재질이 걸러지는 자리다.
    //
    // 절단기 입력은 바닐라에서 언제나 완전 블록이므로, 간선은 반 블록을 지나갈 수 있어도
    // (조각된 변형은 반 블록에서 나온다) **레시피의 입력**으로는 완전 블록만 세운다.

    /** 석재 계열 공용 형상 여섯 배치. {@code inputs}/{@code outputs} 는 제작대 정본 값이다. */
    private enum CutShape {
        STAIRS(6, 4, 1), SLAB(3, 6, 2), WALL(6, 6, 1),
        QUARRY(4, 4, 1), CHISELED(2, 1, 1), PILLAR(2, 2, 1);

        private final int inputs;
        private final int outputs;
        /** 절단기 산출량(바닐라: 반 블록만 2, 나머지는 1). */
        private final int cutCount;

        CutShape(int inputs, int outputs, int cutCount) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.cutCount = cutCount;
        }

        List<Short> pattern(short m) {
            return switch (this) {
                case STAIRS -> shape(m, 0, 0, m, m, 0, m, m, m);
                case SLAB -> shape(m, m, m, 0, 0, 0, 0, 0, 0);
                case WALL -> shape(m, m, m, m, m, m, 0, 0, 0);
                case QUARRY -> shape(m, m, 0, m, m, 0, 0, 0, 0);
                case CHISELED, PILLAR -> shape(m, 0, 0, m, 0, 0, 0, 0, 0);
            };
        }
    }

    /**
     * 절단 대상 재질인가. 설치 가능한 블록이면서 <b>목재가 아니어야</b> 한다 — 바닐라 절단기는
     * 석재 계열만 받고 목재 형상은 제작대 전용이다(1.21.4 기준 나무 절단기는 없다).
     *
     * <p>"곡괭이가 필요한가"로 가르지 않는 이유는 이 저장소의 {@code BlockEditRules.canHarvest}
     * 가 채굴 티어 표라서 이끼 낀 조약돌·사암처럼 티어 0 인 석재까지 맨손 회수로 답하기
     * 때문이다. 재질 구분에 그 표를 쓰면 정본 계열이 통째로 빠진다.
     */
    private static boolean cuttableMaterial(int blockId) {
        return blockId != Blocks.AIR && Blocks.isPlaceableBlock(blockId)
                && !Blocks.isPlankBlock(blockId)
                // [SHELF-FUNGUS-WOOL-SLAB] 바닐라 절단기 입력은 전부 돌·금속 재질이다.
                // 양털(반 블록의 재료)과 벗긴 원목(선반의 재료)은 "가로 한 줄 3 → 6" 이라는
                // 반 블록 표준 배치를 쓰므로, 재질을 막지 않으면 이 파생기가 두 계열을 조용히
                // 절단표에 넣는다 — 26.3 도 1.21.9 도 절단 레시피를 주지 않았다.
                // (판자를 이미 같은 이유로 막고 있다. 벗긴 원목만 예외로 남아 있었다.)
                && !isWoolMaterial(blockId)
                && !Blocks.isStrippedLog(blockId)
                && !NON_CUT_MATERIALS.contains(blockId);
    }

    /** 양털 16색과 그 반 블록. 절단기 입력·산출 어느 쪽으로도 쓰이지 않는다. */
    private static boolean isWoolMaterial(int blockId) {
        return blockId >= Blocks.WHITE_WOOL && blockId <= Blocks.BLACK_WOOL
                || Blocks.isWoolSlab(blockId) || Blocks.isWoolStairs(blockId);
    }

    /**
     * 석재 계열 배치를 쓰지만 바닐라 절단기가 <b>받지 않는</b> 재질. 바닐라 절단기 입력은
     * 전부 돌·금속 재질이고, 다진 진흙(packed mud)은 흙(dirt) 재질이라 2×2 가공으로 진흙
     * 벽돌이 되어도 절단 경로가 없다 — 바닐라 진흙 절단 레시피는
     * {@code mud_brick_{slab,stairs,wall}_from_mud_bricks_stonecutting.json} 셋뿐이다.
     */
    private static final java.util.Set<Integer> NON_CUT_MATERIALS =
            java.util.Set.of(Blocks.PACKED_MUD);

    /**
     * 입력 하나가 만드는 산출 배수. 바닐라에서 4배가 붙는 입력은 구리 <b>블록</b> 산화
     * 4단계(와 그 밀랍판)뿐이고, 그 아래(잘린 구리 …)는 석재와 같은 1배다.
     * TS 대응은 {@code StonecuttingCatalog.cutMultiplier} 이며 같은 값이어야 한다.
     */
    private static int cutMultiplier(int source) {
        boolean copperBlock =
                source >= Blocks.COPPER_BLOCK
                        && source < Blocks.COPPER_BLOCK + Blocks.COPPER_OXIDATION_STAGES
                || source >= Blocks.WAXED_COPPER_BLOCK
                        && source < Blocks.WAXED_COPPER_BLOCK + Blocks.COPPER_OXIDATION_STAGES;
        return copperBlock ? 4 : 1;
    }

    /** 절단기 입력 자격. 바닐라 절단기 입력은 언제나 완전 블록이다(반 블록·계단을 넣지 못한다). */
    private static boolean cuttableInput(int blockId) {
        return cuttableMaterial(blockId)
                && !com.gameexpert.engine.BuildingBlockRules.isStairs(blockId)
                && !com.gameexpert.engine.BuildingBlockRules.isSlab(blockId)
                && !com.gameexpert.engine.BuildingBlockRules.isWall(blockId)
                && !com.gameexpert.engine.BuildingBlockRules.isPane(blockId)
                && !Blocks.isFence(blockId)
                && !Blocks.isFenceGate(blockId)
                && !Blocks.isTrapdoor(blockId);
    }

    /** 한 제작 레시피가 어떤 형상 간선인가. 형상 간선이 아니면 null. */
    private static CutShape cutShapeOf(CraftRecipe recipe) {
        if (recipe.requiresFurnace || recipe.requiresBrewing || recipe.requiresStonecutter) {
            return null;
        }
        if (recipe.pattern.isEmpty() || recipe.inputs.size() != 1) return null;
        short material = recipe.inputs.get(0).itemType;
        if (!cuttableMaterial(material) || !cuttableMaterial(recipe.outputType)) return null;
        for (CutShape candidate : CutShape.values()) {
            if (recipe.inputs.get(0).count == candidate.inputs
                    && recipe.outputCount == candidate.outputs
                    && recipe.pattern.equals(candidate.pattern(material))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * [COPPER-DIRECT] 형상 간선으로는 뽑히지 않는 바닐라 절단 간선을 손으로 세운다.
     *
     * <p>파생 규칙(형상 배치 대조)이 원리적으로 못 보는 두 부류가 있고, 바닐라 절단표
     * 원문(MC Java 1.21.4 {@code data/minecraft/recipe/*_from_*_stonecutting.json})과 전수
     * 대조해 확정한 결손은 정확히 그 둘뿐이다.
     *
     * <ol>
     *   <li><b>구리 격자</b> — {@code copper_grate_from_copper_block_stonecutting.json}(산화
     *       4단계 각각, 산출 4). 제작대 배치가 마름모(0m0/m0m/0m0)라 석재 공용 여섯 배치 중
     *       어느 것과도 문자열이 같지 않아 형상 간선으로 잡히지 않는다. 격자는 격자에서
     *       파생하는 하류가 없으므로 <b>블록 → 격자</b> 간선 하나면 닫힌다(바닐라도
     *       {@code from_cut_copper} 판이 없다).</li>
     *   <li><b>밀랍 구리 계열 전부</b> — {@code waxed_*_from_waxed_copper_block_stonecutting}
     *       ·{@code _from_waxed_cut_copper_stonecutting}. 이 저장소는 밀랍 변형에 제작식을
     *       등록하지 않는다(도포로만 얻는 계약, CopperFamilyTest)므로 형상 간선의 원천 자체가
     *       없다. 바닐라 절단표는 밀랍판을 원본과 <b>한 줄도 다르지 않게</b> 갖추고 있어
     *       (waxed 블록 → 잘린 4·계단 4·반 블록 8·조각 4·격자 4, waxed 잘린 구리 → 계단 1·
     *       반 블록 2·조각 1) 여기서 같은 간선을 세운다. 4배는 {@link #cutMultiplier} 가
     *       이미 밀랍 블록까지 포함해 붙인다.</li>
     * </ol>
     *
     * <p>값은 <b>배수 전</b> 산출량이다 — 반 블록만 2, 나머지는 1(형상 간선과 같은 규약).
     * TS 대응은 {@code StonecuttingCatalog.DIRECT_CUT_EDGES} 이며 같은 표여야 한다.
     */
    private static void registerDirectCutEdges(Map<Short, Map<Short, Integer>> edges) {
        for (int stage = 0; stage < Blocks.COPPER_OXIDATION_STAGES; stage++) {
            addCutEdge(edges, Blocks.COPPER_BLOCK + stage, Blocks.COPPER_GRATE + stage, 1);

            int waxedBlock = Blocks.WAXED_COPPER_BLOCK + stage;
            int waxedCut = Blocks.WAXED_CUT_COPPER + stage;
            addCutEdge(edges, waxedBlock, waxedCut, 1);
            addCutEdge(edges, waxedBlock, Blocks.WAXED_COPPER_GRATE + stage, 1);
            addCutEdge(edges, waxedCut, Blocks.WAXED_CUT_COPPER_STAIRS + stage, 1);
            addCutEdge(edges, waxedCut, Blocks.WAXED_CUT_COPPER_SLAB + stage, 2);
            addCutEdge(edges, waxedCut, Blocks.WAXED_CHISELED_COPPER + stage, 1);
        }
    }

    private static void addCutEdge(Map<Short, Map<Short, Integer>> edges, int source, int output,
            int cutCount) {
        edges.computeIfAbsent((short) source, key -> new LinkedHashMap<>())
                .putIfAbsent((short) output, cutCount);
    }

    /**
     * 형상 간선을 도달 가능성으로 닫아 절단표를 등록한다. 등록 순서는 입력 블록 ID → 산출
     * 블록 ID 오름차순이라 두 권위(Spring·정적판)와 매니페스트가 언제나 같은 순서를 본다.
     */
    private static void registerDerivedStonecutting() {
        // material → (output → 그 간선의 배수 전 산출량. 반 블록만 2, 나머지는 1)
        Map<Short, Map<Short, Integer>> edges = new LinkedHashMap<>();
        for (CraftRecipe recipe : BY_ID.values()) {
            CutShape shape = cutShapeOf(recipe);
            if (shape == null) continue;
            edges.computeIfAbsent(recipe.inputs.get(0).itemType, key -> new LinkedHashMap<>())
                    .putIfAbsent(recipe.outputType, shape.cutCount);
        }
        registerDirectCutEdges(edges);
        java.util.List<Short> sources = new ArrayList<>(edges.keySet());
        sources.sort(null);
        for (short source : sources) {
            if (!cuttableInput(source)) continue;
            // 폭 우선 도달 — 중간 단계 건너뜀은 이 닫힘 자체가 만든다. 마지막 간선의 형상이
            // 산출량을 정하므로(반 블록만 2) 경로가 여럿이어도 산출량은 산출 블록이 결정한다.
            Map<Short, Integer> reached = new LinkedHashMap<>();
            java.util.ArrayDeque<Short> queue = new java.util.ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty()) {
                short current = queue.poll();
                Map<Short, Integer> next = edges.get(current);
                if (next == null) continue;
                for (Map.Entry<Short, Integer> entry : next.entrySet()) {
                    short output = entry.getKey();
                    if (output == source || reached.containsKey(output)) continue;
                    reached.put(output, entry.getValue());
                    queue.add(output);
                }
            }
            java.util.List<Short> outputs = new ArrayList<>(reached.keySet());
            outputs.sort(null);
            int multiplier = cutMultiplier(Short.toUnsignedInt(source));
            for (short output : outputs) {
                registerStonecut(
                        "stonecutting_" + Short.toUnsignedInt(source)
                                + "_to_" + Short.toUnsignedInt(output),
                        output, reached.get(output) * multiplier, source);
            }
        }
    }

    private static void registerFullBlock(String id, short material, short output) {
        registerShaped(id, output, 1,
                shape(material, material, material, material, material, material,
                        material, material, material),
                new Ingredient(material, 9));
    }

    static {
        // Bundles retain the approved material cost within the ordinary nine-cell table.
        register("flesh_pulse_lamp", (short) Blocks.FLESH_PULSE_LAMP, 1,
                new Ingredient((short) Blocks.HEART_CORE, 1),
                new Ingredient((short) Blocks.FLESH_CLOT_SAC, 4),
                new Ingredient((short) Blocks.FLESH_BONE_BUNDLE, 2));
        register("flesh_bandage", (short) Blocks.FLESH_BANDAGE, 2,
                new Ingredient((short) Blocks.FLESH_MEMBRANE, 2),
                new Ingredient((short) Blocks.FLESH_FIBER, 2));
        register("flesh_detector", (short) Blocks.FLESH_DETECTOR, 1,
                new Ingredient((short) Blocks.HEART_CORE, 1), new Ingredient(PlayerInventory.COMPASS, 1),
                new Ingredient((short) Blocks.FLESH_MEMBRANE, 4));
        register("flesh_reliquary", (short) Blocks.FLESH_RELIQUARY, 1,
                new Ingredient((short) Blocks.HEART_CORE, 1), new Ingredient((short) Blocks.GLASS, 4),
                new Ingredient((short) Blocks.FLESH_BONE_BUNDLE, 2));
        register("flesh_hook_blade", (short) Blocks.FLESH_HOOK_BLADE, 1,
                new Ingredient((short) Blocks.FLESH_HOOK_CLAW, 2),
                new Ingredient(PlayerInventory.BONE, 2), new Ingredient((short) Blocks.FLESH_FIBER, 2));
        register("flesh_hooked_spear", (short) Blocks.FLESH_HOOKED_SPEAR, 1,
                new Ingredient((short) Blocks.FLESH_BONE_SPEAR, 1),
                new Ingredient((short) Blocks.FLESH_HOOK_BLADE, 1));
        register("flesh_bone_chestplate", (short) Blocks.FLESH_BONE_CHESTPLATE, 1,
                new Ingredient((short) Blocks.FLESH_BONE_BUNDLE, 4),
                new Ingredient((short) Blocks.FLESH_BONE_PLATE, 2),
                new Ingredient((short) Blocks.FLESH_FIBER_BUNDLE, 1));
        register("flesh_bone_spear", (short) Blocks.FLESH_BONE_SPEAR, 1,
                new Ingredient((short) Blocks.FLESH_BONE_BUNDLE, 3),
                new Ingredient((short) Blocks.FLESH_FIBER_BUNDLE, 1), new Ingredient(PlayerInventory.DIAMOND, 1));
        register("flesh_fat_lamp", (short) Blocks.FLESH_FAT_LAMP, 4,
                new Ingredient((short) Blocks.FLESH_FAT, 1),
                new Ingredient((short) Blocks.FLESH_FIBER, 2), new Ingredient(PlayerInventory.STICK, 1));
        register("flesh_fiber_bundle", (short) Blocks.FLESH_FIBER_BUNDLE, 1,
                new Ingredient((short) Blocks.FLESH_FIBER, 4));
        register("flesh_fiber_unbundle", (short) Blocks.FLESH_FIBER, 4,
                new Ingredient((short) Blocks.FLESH_FIBER_BUNDLE, 1));
        register("flesh_bone_bundle", (short) Blocks.FLESH_BONE_BUNDLE, 1,
                new Ingredient(PlayerInventory.BONE, 2));
        register("flesh_bone_unbundle", PlayerInventory.BONE, 2,
                new Ingredient((short) Blocks.FLESH_BONE_BUNDLE, 1));
        register("flesh_fiber_boots", (short) Blocks.FLESH_FIBER_BOOTS, 1,
                new Ingredient((short) Blocks.FLESH_FIBER_BUNDLE, 2),
                new Ingredient((short) Blocks.FLESH_MEMBRANE, 4));
        // 통나무 1 → 그 수종의 판자 4 (MC Java 1.21.4). PLANK(7) 은 참나무 판자로 남는다.
        // acceptsItem 의 통나무 계열 확장은 "planks" 로 시작하는 레시피에서 제외되므로
        // 여기 여덟 줄이 수종마다 정확히 하나씩 매칭된다.
        register("planks", (short) Blocks.PLANK, 4, new Ingredient((short) Blocks.LOG, 1));
        register("planks_birch", (short) Blocks.BIRCH_PLANK, 4, new Ingredient((short) Blocks.BIRCH_LOG, 1));
        register("planks_spruce", (short) Blocks.SPRUCE_PLANK, 4, new Ingredient((short) Blocks.SPRUCE_LOG, 1));
        register("planks_jungle", (short) Blocks.JUNGLE_PLANK, 4, new Ingredient((short) Blocks.JUNGLE_LOG, 1));
        register("planks_acacia", (short) Blocks.ACACIA_PLANK, 4, new Ingredient((short) Blocks.ACACIA_LOG, 1));
        register("planks_dark_oak", (short) Blocks.DARK_OAK_PLANK, 4,
                new Ingredient((short) Blocks.DARK_OAK_LOG, 1));
        register("planks_cherry", (short) Blocks.CHERRY_PLANK, 4, new Ingredient((short) Blocks.CHERRY_LOG, 1));
        register("planks_mangrove", (short) Blocks.MANGROVE_PLANK, 4,
                new Ingredient((short) Blocks.MANGROVE_LOG, 1));
        // [PALE-GARDEN] 창백한 참나무도 통나무 1 → 판자 4 로 바닐라 배치 그대로다.
        register("planks_pale_oak", (short) Blocks.PALE_OAK_PLANK, 4,
                new Ingredient((short) Blocks.PALE_OAK_LOG, 1));
        // [POPLAR] 26.3 포플러 #poplar_logs 1 → 판자 4.
        register("planks_poplar", (short) Blocks.POPLAR_PLANK, 4,
                new Ingredient((short) Blocks.POPLAR_LOG, 1));
        registerShaped("anvil", (short) Blocks.ANVIL, 1,
                shape(Blocks.IRON_BLOCK, Blocks.IRON_BLOCK, Blocks.IRON_BLOCK,
                        0, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT),
                new Ingredient((short) Blocks.IRON_BLOCK, 3),
                new Ingredient(PlayerInventory.IRON_INGOT, 4));
        registerShaped("tinted_glass", (short) Blocks.TINTED_GLASS, 2,
                shape(0, PlayerInventory.AMETHYST_SHARD, 0,
                        PlayerInventory.AMETHYST_SHARD, Blocks.GLASS, PlayerInventory.AMETHYST_SHARD,
                        0, PlayerInventory.AMETHYST_SHARD, 0),
                new Ingredient((short) Blocks.GLASS, 1),
                new Ingredient(PlayerInventory.AMETHYST_SHARD, 4));
        registerShaped("glowstone", (short) Blocks.GLOWSTONE, 1,
                shape(PlayerInventory.GLOWSTONE_DUST, PlayerInventory.GLOWSTONE_DUST, 0,
                        PlayerInventory.GLOWSTONE_DUST, PlayerInventory.GLOWSTONE_DUST, 0,
                        0, 0, 0),
                new Ingredient(PlayerInventory.GLOWSTONE_DUST, 4));
        short[] bannerWool = {
            (short) Blocks.WHITE_WOOL, (short) Blocks.ORANGE_WOOL,
            (short) Blocks.MAGENTA_WOOL, (short) Blocks.LIGHT_BLUE_WOOL,
            (short) Blocks.YELLOW_WOOL, (short) Blocks.LIME_WOOL,
            (short) Blocks.PINK_WOOL, (short) Blocks.GRAY_WOOL,
            (short) Blocks.LIGHT_GRAY_WOOL, (short) Blocks.CYAN_WOOL,
            (short) Blocks.PURPLE_WOOL, (short) Blocks.BLUE_WOOL,
            (short) Blocks.BROWN_WOOL, (short) Blocks.GREEN_WOOL,
            (short) Blocks.RED_WOOL, (short) Blocks.BLACK_WOOL,
        };
        String[] bannerColors = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        };
        for (int color = 0; color < bannerWool.length; color++) {
            short wool = bannerWool[color];
            short banner = (short) (Blocks.WHITE_BANNER + color);
            registerShaped(bannerColors[color] + "_banner", banner, 1,
                    shape(wool, wool, wool, wool, wool, wool,
                            0, PlayerInventory.STICK, 0),
                    new Ingredient(wool, 6), new Ingredient(PlayerInventory.STICK, 1));
        }
        register("writable_book", (short) Blocks.WRITABLE_BOOK, 1,
                new Ingredient(PlayerInventory.BOOK, 1),
                new Ingredient(PlayerInventory.INK_SAC, 1),
                new Ingredient(PlayerInventory.FEATHER, 1));
        registerShaped("beehive", (short) Blocks.BEEHIVE, 1,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK,
                        PlayerInventory.HONEYCOMB, PlayerInventory.HONEYCOMB,
                        PlayerInventory.HONEYCOMB,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK),
                new Ingredient((short) Blocks.PLANK, 6),
                new Ingredient(PlayerInventory.HONEYCOMB, 3));
        register("honey_block", (short) Blocks.HONEY_BLOCK, 1,
                new Ingredient(PlayerInventory.HONEY_BOTTLE, 4));
        // 판자 세로 2칸 → 막대 ×4
        registerShaped("stick", PlayerInventory.STICK, 4,
                shape(Blocks.PLANK, 0, 0, Blocks.PLANK, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 2));
        // 도구: 판자 + 막대 → 곡괭이/도끼/삽/검
        registerShaped("pickaxe", PlayerInventory.PICKAXE, 1,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK, 0, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient((short) Blocks.PLANK, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("axe", PlayerInventory.AXE, 1,
                shape(Blocks.PLANK, Blocks.PLANK, 0, Blocks.PLANK, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient((short) Blocks.PLANK, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("shovel", PlayerInventory.SHOVEL, 1,
                shape(Blocks.PLANK, 0, 0, PlayerInventory.STICK, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient((short) Blocks.PLANK, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("sword", PlayerInventory.SWORD_ITEM, 1,
                shape(Blocks.PLANK, 0, 0, Blocks.PLANK, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient((short) Blocks.PLANK, 2), new Ingredient(PlayerInventory.STICK, 1));
        // 석탄 또는 숯 + 막대 → 횃불 ×4
        registerShaped("torch", (short) Blocks.TORCH, 4,
                shape(PlayerInventory.COAL, 0, 0, PlayerInventory.STICK, 0, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.COAL, 1), new Ingredient(PlayerInventory.STICK, 1));
        // 1.21.9 copper_torch.json: 구리 조각 / 석탄 또는 목탄 / 막대를 세로로 → 4.
        // 매니페스트에는 재료 대안 표현이 없어 두 정본 입력을 별도 내부 ID로 편다.
        registerShaped("copper_torch", (short) Blocks.COPPER_TORCH, 4,
                shape(PlayerInventory.COPPER_NUGGET, 0, 0,
                        PlayerInventory.COAL, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.COPPER_NUGGET, 1),
                new Ingredient(PlayerInventory.COAL, 1),
                new Ingredient(PlayerInventory.STICK, 1));
        registerShaped("copper_torch_from_charcoal", (short) Blocks.COPPER_TORCH, 4,
                shape(PlayerInventory.COPPER_NUGGET, 0, 0,
                        PlayerInventory.CHARCOAL, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.COPPER_NUGGET, 1),
                new Ingredient(PlayerInventory.CHARCOAL, 1),
                new Ingredient(PlayerInventory.STICK, 1));
        // 그릇: 판자 3개 V자 배치 → 그릇 4개(2×2 인벤토리에서 제작 가능).
        registerShaped("bowl", PlayerInventory.BOWL, 4,
                shape(Blocks.PLANK, Blocks.PLANK, 0, Blocks.PLANK, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 3));
        // Java 1.21.4 leather_horse_armor.json: X X / XXX / X X.
        registerShaped("leather_horse_armor", PlayerInventory.LEATHER_HORSE_ARMOR, 1,
                shape(PlayerInventory.LEATHER, 0, PlayerInventory.LEATHER,
                        PlayerInventory.LEATHER, PlayerInventory.LEATHER, PlayerInventory.LEATHER,
                        PlayerInventory.LEATHER, 0, PlayerInventory.LEATHER),
                new Ingredient(PlayerInventory.LEATHER, 7));
        // Java 1.21.4 lead.json: string around one slime ball, producing two leads.
        registerShaped("lead", PlayerInventory.LEAD, 2,
                shape(PlayerInventory.STRING, PlayerInventory.STRING, 0,
                        PlayerInventory.STRING, PlayerInventory.SLIME_BALL, 0,
                        0, 0, PlayerInventory.STRING),
                new Ingredient(PlayerInventory.STRING, 4),
                new Ingredient(PlayerInventory.SLIME_BALL, 1));

        // 건축 B1(§2·§10.2-B1): 건축 재료 6종. 블록은 같은 ID, 막대는 아이템 정본 상수를 쓴다.
        // 유리는 아래 시간제 화로 레시피로만 만든다(모래 즉시 제작 경로 없음).
        // 돌 4개 2×2 → 석재 벽돌 4개
        registerShaped("stone_brick", (short) Blocks.STONE_BRICK, 4,
                shape(Blocks.STONE, Blocks.STONE, 0, Blocks.STONE, Blocks.STONE, 0, 0, 0, 0),
                new Ingredient((short) Blocks.STONE, 4));
        // 판자 7 ×3 → 판자 반블록 27 ×6
        registerShaped("plank_slab", (short) Blocks.PLANK_SLAB, 6,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK, 0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 3));
        // 조약돌 8 ×3 → 조약돌 반블록 28 ×6
        registerShaped("cobble_slab", (short) Blocks.COBBLE_SLAB, 6,
                shape(Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE, 0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.COBBLE, 3));
        // 판자 6개 2×3 → 문 3개
        registerShaped("door", (short) Blocks.DOOR_CLOSED, 3,
                shape(Blocks.PLANK, Blocks.PLANK, 0, Blocks.PLANK, Blocks.PLANK, 0,
                        Blocks.PLANK, Blocks.PLANK, 0),
                new Ingredient((short) Blocks.PLANK, 6));
        // 막대 ×7 → 사다리 31 ×3
        registerShaped("ladder", (short) Blocks.LADDER, 3,
                shape(PlayerInventory.STICK, 0, PlayerInventory.STICK,
                        PlayerInventory.STICK, PlayerInventory.STICK, PlayerInventory.STICK,
                        PlayerInventory.STICK, 0, PlayerInventory.STICK),
                new Ingredient(PlayerInventory.STICK, 7));

        // 기본 건축 팔레트: MC 제작대 재료 수/산출량을 그대로 쓴다.
        registerShaped("sandstone", (short) Blocks.SANDSTONE, 1,
                shape(Blocks.SAND, Blocks.SAND, 0, Blocks.SAND, Blocks.SAND, 0, 0, 0, 0),
                new Ingredient((short) Blocks.SAND, 4));
        registerShaped("red_sandstone", (short) Blocks.RED_SANDSTONE, 1,
                shape(Blocks.RED_SAND, Blocks.RED_SAND, 0,
                        Blocks.RED_SAND, Blocks.RED_SAND, 0, 0, 0, 0),
                new Ingredient((short) Blocks.RED_SAND, 4));
        registerShaped("wood_stairs", (short) Blocks.WOOD_STAIRS, 4,
                shape(Blocks.PLANK, 0, 0, Blocks.PLANK, Blocks.PLANK, 0,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK),
                new Ingredient((short) Blocks.PLANK, 6));
        registerShaped("cobble_stairs", (short) Blocks.COBBLE_STAIRS, 4,
                shape(Blocks.COBBLE, 0, 0, Blocks.COBBLE, Blocks.COBBLE, 0,
                        Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE),
                new Ingredient((short) Blocks.COBBLE, 6));
        registerShaped("stone_brick_stairs", (short) Blocks.STONE_BRICK_STAIRS, 4,
                shape(Blocks.STONE_BRICK, 0, 0, Blocks.STONE_BRICK, Blocks.STONE_BRICK, 0,
                        Blocks.STONE_BRICK, Blocks.STONE_BRICK, Blocks.STONE_BRICK),
                new Ingredient((short) Blocks.STONE_BRICK, 6));
        registerShaped("sandstone_stairs", (short) Blocks.SANDSTONE_STAIRS, 4,
                shape(Blocks.SANDSTONE, 0, 0, Blocks.SANDSTONE, Blocks.SANDSTONE, 0,
                        Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE),
                new Ingredient((short) Blocks.SANDSTONE, 6));
        registerShaped("stone_brick_slab", (short) Blocks.STONE_BRICK_SLAB, 6,
                shape(Blocks.STONE_BRICK, Blocks.STONE_BRICK, Blocks.STONE_BRICK,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.STONE_BRICK, 3));
        registerShaped("sandstone_slab", (short) Blocks.SANDSTONE_SLAB, 6,
                shape(Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.SANDSTONE, 3));
        registerShaped("cobble_wall", (short) Blocks.COBBLE_WALL, 6,
                shape(Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                        Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE, 0, 0, 0),
                new Ingredient((short) Blocks.COBBLE, 6));
        registerShaped("mossy_cobble_wall", (short) Blocks.MOSSY_COBBLE_WALL, 6,
                shape(Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE,
                        Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE, 0, 0, 0),
                new Ingredient((short) Blocks.MOSSY_COBBLE, 6));
        registerShaped("stone_brick_wall", (short) Blocks.STONE_BRICK_WALL, 6,
                shape(Blocks.STONE_BRICK, Blocks.STONE_BRICK, Blocks.STONE_BRICK,
                        Blocks.STONE_BRICK, Blocks.STONE_BRICK, Blocks.STONE_BRICK, 0, 0, 0),
                new Ingredient((short) Blocks.STONE_BRICK, 6));
        // [DEEPSLATE-PROC] 심층암 가공 사슬(MC Java 1.21.4). 조약돌 심층암 4 → 다듬은 심층암 4 →
        // 심층암 벽돌 4 → 심층암 타일 4 로 이어지고, 조각된 심층암만 다듬은 심층암 반 블록 2개를
        // 세로로 쌓는다. 금 간 두 변형은 제작식이 없고 아래 제련 레시피로만 만든다.
        registerShaped("polished_deepslate", (short) Blocks.POLISHED_DEEPSLATE, 4,
                shape(Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, 0,
                        Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE, 0, 0, 0, 0),
                new Ingredient((short) Blocks.COBBLED_DEEPSLATE, 4));
        registerShaped("deepslate_bricks", (short) Blocks.DEEPSLATE_BRICKS, 4,
                shape(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, 0,
                        Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, 0, 0, 0, 0),
                new Ingredient((short) Blocks.POLISHED_DEEPSLATE, 4));
        registerShaped("deepslate_tiles", (short) Blocks.DEEPSLATE_TILES, 4,
                shape(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, 0,
                        Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, 0, 0, 0, 0),
                new Ingredient((short) Blocks.DEEPSLATE_BRICKS, 4));
        registerShaped("chiseled_deepslate", (short) Blocks.CHISELED_DEEPSLATE, 1,
                shape(Blocks.POLISHED_DEEPSLATE_SLAB, 0, 0,
                        Blocks.POLISHED_DEEPSLATE_SLAB, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.POLISHED_DEEPSLATE_SLAB, 2));
        registerDeepslateShapes("polished_deepslate", (short) Blocks.POLISHED_DEEPSLATE,
                (short) Blocks.POLISHED_DEEPSLATE_STAIRS, (short) Blocks.POLISHED_DEEPSLATE_SLAB,
                (short) Blocks.POLISHED_DEEPSLATE_WALL);
        registerDeepslateShapes("deepslate_brick", (short) Blocks.DEEPSLATE_BRICKS,
                (short) Blocks.DEEPSLATE_BRICK_STAIRS, (short) Blocks.DEEPSLATE_BRICK_SLAB,
                (short) Blocks.DEEPSLATE_BRICK_WALL);
        registerDeepslateShapes("deepslate_tile", (short) Blocks.DEEPSLATE_TILES,
                (short) Blocks.DEEPSLATE_TILE_STAIRS, (short) Blocks.DEEPSLATE_TILE_SLAB,
                (short) Blocks.DEEPSLATE_TILE_WALL);

        // [STONE-PROC] 석재 가공 계열 제작식(MC Java 1.21.4 recipes 의 *.json 그대로).
        // 계단 6 → 4 · 반 블록 3 → 6 · 담장 6 → 6 은 석재 전 계열이 공유하는 배치라
        // 위 registerDeepslateShapes 를 그대로 쓴다(이름은 먼저 온 트랙에서 유래했을 뿐
        // 재료 하나에서 세 형상을 등록하는 범용 헬퍼다).
        // 바닐라 석재 절단기 경로는 여기서 손으로 적지 않는다 — [STONECUT] 절이 이 형상
        // 등록들에서 절단표를 파생한다(registerDerivedStonecutting).
        registerDeepslateShapes("granite", (short) Blocks.GRANITE,
                (short) Blocks.GRANITE_STAIRS, (short) Blocks.GRANITE_SLAB,
                (short) Blocks.GRANITE_WALL);
        registerDeepslateShapes("diorite", (short) Blocks.DIORITE,
                (short) Blocks.DIORITE_STAIRS, (short) Blocks.DIORITE_SLAB,
                (short) Blocks.DIORITE_WALL);
        registerDeepslateShapes("andesite", (short) Blocks.ANDESITE,
                (short) Blocks.ANDESITE_STAIRS, (short) Blocks.ANDESITE_SLAB,
                (short) Blocks.ANDESITE_WALL);
        registerDeepslateShapes("red_sandstone", (short) Blocks.RED_SANDSTONE,
                (short) Blocks.RED_SANDSTONE_STAIRS, (short) Blocks.RED_SANDSTONE_SLAB,
                (short) Blocks.RED_SANDSTONE_WALL);
        registerDeepslateShapes("mossy_stone_brick", (short) Blocks.MOSSY_STONE_BRICK,
                (short) Blocks.MOSSY_STONE_BRICK_STAIRS, (short) Blocks.MOSSY_STONE_BRICK_SLAB,
                (short) Blocks.MOSSY_STONE_BRICK_WALL);
        // 바닐라에 담장이 없는 재질(돌·이끼 낀 조약돌)과 이미 담장/계단이 있는 재질
        // (사암·잘린 사암)은 빠진 형상만 개별 등록한다.
        registerShaped("stone_stairs", (short) Blocks.STONE_STAIRS, 4,
                shape(Blocks.STONE, 0, 0, Blocks.STONE, Blocks.STONE, 0,
                        Blocks.STONE, Blocks.STONE, Blocks.STONE),
                new Ingredient((short) Blocks.STONE, 6));
        registerShaped("stone_slab", (short) Blocks.STONE_SLAB, 6,
                shape(Blocks.STONE, Blocks.STONE, Blocks.STONE, 0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.STONE, 3));
        registerShaped("mossy_cobble_stairs", (short) Blocks.MOSSY_COBBLE_STAIRS, 4,
                shape(Blocks.MOSSY_COBBLE, 0, 0, Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE, 0,
                        Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE),
                new Ingredient((short) Blocks.MOSSY_COBBLE, 6));
        registerShaped("mossy_cobble_slab", (short) Blocks.MOSSY_COBBLE_SLAB, 6,
                shape(Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE, Blocks.MOSSY_COBBLE,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.MOSSY_COBBLE, 3));
        registerShaped("cut_sandstone_slab", (short) Blocks.CUT_SANDSTONE_SLAB, 6,
                shape(Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.CUT_SANDSTONE, 3));
        registerShaped("sandstone_wall", (short) Blocks.SANDSTONE_WALL, 6,
                shape(Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE,
                        Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE, 0, 0, 0),
                new Ingredient((short) Blocks.SANDSTONE, 6));

        // [STONE-RESIDUAL] 석재 잔여 계열 제작식(MC Java 1.21.4 recipes 의 *.json 그대로).
        // 절단기 경로는 [STONECUT] 절이 이 등록들에서 자동으로 파생한다.
        //
        // 담장이 **있는 재질**과 **없는 재질**이 갈린다. 바닐라에는 다듬은 화성암·매끄러운
        // 사암 계열의 담장이 없으므로 registerStairsAndSlab 을 쓴다.
        registerQuarry("polished_granite", (short) Blocks.GRANITE, (short) Blocks.POLISHED_GRANITE);
        registerQuarry("polished_diorite", (short) Blocks.DIORITE, (short) Blocks.POLISHED_DIORITE);
        registerQuarry("polished_andesite",
                (short) Blocks.ANDESITE, (short) Blocks.POLISHED_ANDESITE);
        registerStairsAndSlab("polished_granite", (short) Blocks.POLISHED_GRANITE,
                (short) Blocks.POLISHED_GRANITE_STAIRS, (short) Blocks.POLISHED_GRANITE_SLAB);
        registerStairsAndSlab("polished_diorite", (short) Blocks.POLISHED_DIORITE,
                (short) Blocks.POLISHED_DIORITE_STAIRS, (short) Blocks.POLISHED_DIORITE_SLAB);
        registerStairsAndSlab("polished_andesite", (short) Blocks.POLISHED_ANDESITE,
                (short) Blocks.POLISHED_ANDESITE_STAIRS, (short) Blocks.POLISHED_ANDESITE_SLAB);

        // 사암 계열. 매끄러운 둘은 아래 제련 구역에서 만들고, 여기서는 형상만 등록한다.
        registerStairsAndSlab("smooth_sandstone", (short) Blocks.SMOOTH_SANDSTONE,
                (short) Blocks.SMOOTH_SANDSTONE_STAIRS, (short) Blocks.SMOOTH_SANDSTONE_SLAB);
        registerStairsAndSlab("smooth_red_sandstone", (short) Blocks.SMOOTH_RED_SANDSTONE,
                (short) Blocks.SMOOTH_RED_SANDSTONE_STAIRS,
                (short) Blocks.SMOOTH_RED_SANDSTONE_SLAB);
        // 흰 사암도 붉은 사암과 똑같이 네 줄(cut · chiseled · 계단 · 담장)을 갖는다. 앞의 둘이
        // 빠져 있어서 CUT_SANDSTONE 을 재료로 쓰는 cut_sandstone_slab 이 영영 만들 수 없는
        // 고아 레시피였고, 이 등록에서 파생하는 [STONECUT] 절단표에도 같은 칸이 비어 있었다.
        //   cut_sandstone.json      — sandstone 2×2 → cut_sandstone 4
        //   chiseled_sandstone.json — sandstone_slab 2개를 세로로 → chiseled_sandstone 1
        registerQuarry("cut_sandstone", (short) Blocks.SANDSTONE, (short) Blocks.CUT_SANDSTONE);
        registerChiseled("chiseled_sandstone",
                (short) Blocks.SANDSTONE_SLAB, (short) Blocks.CHISELED_SANDSTONE);
        registerQuarry("cut_red_sandstone",
                (short) Blocks.RED_SANDSTONE, (short) Blocks.CUT_RED_SANDSTONE);
        registerShaped("cut_red_sandstone_slab", (short) Blocks.CUT_RED_SANDSTONE_SLAB, 6,
                shape(Blocks.CUT_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.CUT_RED_SANDSTONE, 3));
        registerChiseled("chiseled_red_sandstone",
                (short) Blocks.RED_SANDSTONE_SLAB, (short) Blocks.CHISELED_RED_SANDSTONE);

        // 돌 계열. 금 간 석재 벽돌은 제련 전용이고 조각된 석재 벽돌만 제작식이 있다.
        registerChiseled("chiseled_stone_bricks",
                (short) Blocks.STONE_BRICK_SLAB, (short) Blocks.CHISELED_STONE_BRICKS);
        registerShaped("smooth_stone_slab", (short) Blocks.SMOOTH_STONE_SLAB, 6,
                shape(Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.SMOOTH_STONE, 3));

        // 점토 벽돌. 점토 덩이를 구워 만든 벽돌 아이템 4개가 블록 하나가 된다(제련은 아래).
        registerShaped("bricks", (short) Blocks.BRICKS, 1,
                shape(Blocks.BRICK, Blocks.BRICK, 0, Blocks.BRICK, Blocks.BRICK, 0, 0, 0, 0),
                new Ingredient((short) Blocks.BRICK, 4));
        registerDeepslateShapes("brick", (short) Blocks.BRICKS,
                (short) Blocks.BRICK_STAIRS, (short) Blocks.BRICK_SLAB,
                (short) Blocks.BRICK_WALL);

        // 진흙 벽돌. 다진 진흙만 무형 제작이고(진흙 1 + 밀 1), 나머지는 석재와 같은 배치다.
        register("packed_mud", (short) Blocks.PACKED_MUD, 1,
                new Ingredient((short) Blocks.MUD, 1), new Ingredient(PlayerInventory.WHEAT, 1));
        registerQuarry("mud_bricks", (short) Blocks.PACKED_MUD, (short) Blocks.MUD_BRICKS);
        registerDeepslateShapes("mud_brick", (short) Blocks.MUD_BRICKS,
                (short) Blocks.MUD_BRICK_STAIRS, (short) Blocks.MUD_BRICK_SLAB,
                (short) Blocks.MUD_BRICK_WALL);

        // 응회암. 원석 → 다듬은 → 벽돌 사슬이고 조각된 둘은 각 단계의 반 블록에서 나온다.
        registerDeepslateShapes("tuff", (short) Blocks.TUFF,
                (short) Blocks.TUFF_STAIRS, (short) Blocks.TUFF_SLAB, (short) Blocks.TUFF_WALL);
        registerQuarry("polished_tuff", (short) Blocks.TUFF, (short) Blocks.POLISHED_TUFF);
        registerDeepslateShapes("polished_tuff", (short) Blocks.POLISHED_TUFF,
                (short) Blocks.POLISHED_TUFF_STAIRS, (short) Blocks.POLISHED_TUFF_SLAB,
                (short) Blocks.POLISHED_TUFF_WALL);
        registerQuarry("tuff_bricks", (short) Blocks.POLISHED_TUFF, (short) Blocks.TUFF_BRICKS);
        registerDeepslateShapes("tuff_brick", (short) Blocks.TUFF_BRICKS,
                (short) Blocks.TUFF_BRICK_STAIRS, (short) Blocks.TUFF_BRICK_SLAB,
                (short) Blocks.TUFF_BRICK_WALL);
        registerChiseled("chiseled_tuff", (short) Blocks.TUFF_SLAB, (short) Blocks.CHISELED_TUFF);
        registerChiseled("chiseled_tuff_bricks",
                (short) Blocks.TUFF_BRICK_SLAB, (short) Blocks.CHISELED_TUFF_BRICKS);

        // [PRISMARINE] 프리즈머린 계열·바다 랜턴 제작식(MC Java 1.21.4 recipes 의 *.json 그대로).
        // 재료는 앞선 [GUARDIAN] 트랙이 착지시킨 프리즈머린 조각 932 · 수정 933 이며,
        // 재료 대체는 하지 않는다(검은 염료도 바닐라 dark_prismarine.json 그대로다).
        //   prismarine.json        2×2 조각 4      → 프리즈머린 1
        //   prismarine_bricks.json 3×3 조각 9      → 프리즈머린 벽돌 1
        //   dark_prismarine.json   조각 8 + 검은 염료 1(가운데) → 어두운 프리즈머린 1
        //   sea_lantern.json       SCS/CCC/SCS(조각 4 + 수정 5) → 바다 랜턴 1
        registerShaped("prismarine", (short) Blocks.PRISMARINE, 1,
                shape(Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, 0,
                        Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.PRISMARINE_SHARD, 4));
        registerShaped("prismarine_bricks", (short) Blocks.PRISMARINE_BRICKS, 1,
                shape(Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD,
                        Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD,
                        Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD),
                new Ingredient(PlayerInventory.PRISMARINE_SHARD, 9));
        registerShaped("dark_prismarine", (short) Blocks.DARK_PRISMARINE, 1,
                shape(Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD,
                        Blocks.PRISMARINE_SHARD, Blocks.BLACK_DYE, Blocks.PRISMARINE_SHARD,
                        Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_SHARD),
                new Ingredient(PlayerInventory.PRISMARINE_SHARD, 8),
                new Ingredient(PlayerInventory.BLACK_DYE, 1));
        registerShaped("sea_lantern", (short) Blocks.SEA_LANTERN, 1,
                shape(Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_CRYSTALS, Blocks.PRISMARINE_SHARD,
                        Blocks.PRISMARINE_CRYSTALS, Blocks.PRISMARINE_CRYSTALS,
                        Blocks.PRISMARINE_CRYSTALS,
                        Blocks.PRISMARINE_SHARD, Blocks.PRISMARINE_CRYSTALS,
                        Blocks.PRISMARINE_SHARD),
                new Ingredient(PlayerInventory.PRISMARINE_SHARD, 4),
                new Ingredient(PlayerInventory.PRISMARINE_CRYSTALS, 5));
        // [CONDUIT] 콘딧(MC Java 1.21.4 conduit.json 그대로).
        //   conduit.json  NNN/NHN/NNN — 앵무조개 껍데기 8 이 3×3 을 두르고 가운데가
        //                 바다의 심장 1 → 콘딧 1
        // 재료를 하나도 신설하지 않았고 대체하지도 않았다: 앵무조개 껍데기는 이미 낚시
        // 전리품 486 이고 바다의 심장만 이 트랙이 1950 으로 새로 만들었다. 바다의 심장 자체는
        // 바닐라대로 **제작법이 없다**(묻힌 보물 상자가 유일한 입수 경로이고 그 전리품표는
        // 해저 유적 · 보물 트랙의 표면이다) — 여기서 심장을 제작 가능하게 만들면 그쪽이
        // 레시피 무결성 위반이다.
        registerShaped("conduit", (short) Blocks.CONDUIT, 1,
                shape(Blocks.NAUTILUS_SHELL, Blocks.NAUTILUS_SHELL, Blocks.NAUTILUS_SHELL,
                        Blocks.NAUTILUS_SHELL, Blocks.HEART_OF_THE_SEA, Blocks.NAUTILUS_SHELL,
                        Blocks.NAUTILUS_SHELL, Blocks.NAUTILUS_SHELL, Blocks.NAUTILUS_SHELL),
                new Ingredient(PlayerInventory.NAUTILUS_SHELL, 8),
                new Ingredient(PlayerInventory.HEART_OF_THE_SEA, 1));
        // [TURTLE] 거북 등껍질(MC Java 1.21.4 turtle_helmet.json 그대로).
        //   turtle_helmet.json  XXX/X X — 거북 등딱지 조각 5 가 투구 꼴을 그린다 → 등껍질 1
        // 재료를 하나도 신설하지 않았고 대체하지도 않았다: 조각은 이 트랙이 1931 로 만든
        // 새 아이템이고 그 입수 경로는 **새끼 거북의 성장**이다(제작법 없음 — 바닐라도 같다).
        // 거북 알(1930)도 제작법이 없다: 산란이 유일한 생성 경로이고 실크 터치로만 회수된다.
        registerShaped("turtle_helmet", PlayerInventory.TURTLE_SHELL, 1,
                shape(Blocks.TURTLE_SCUTE, Blocks.TURTLE_SCUTE, Blocks.TURTLE_SCUTE,
                        Blocks.TURTLE_SCUTE, 0, Blocks.TURTLE_SCUTE,
                        0, 0, 0),
                new Ingredient(PlayerInventory.TURTLE_SCUTE, 5));
        // [ARCHAEOLOGY] 장식 항아리 8종(MC Java 1.21.4 decorated_pot_simple.json 의 배치 그대로).
        //   decorated_pot_simple.json  " # "/"# #"/" # " — 재료 4 가 마름모를 그린다 → 항아리 1
        // 재료를 하나도 신설하지 않았고 대체하지도 않았다: 점토 벽돌은 이미 765 이고 조각
        // 7종은 이 트랙이 등록하되 입수 경로가 <b>붓질 전용 전리품표</b>다(제작법 없음 —
        // 바닐라도 조각은 발굴로만 얻는다).
        //
        // <p><b>바닐라의 특수 레시피를 8 개의 평범한 shaped 로 접었다.</b> [A] 는
        // {@code crafting_decorated_pot}(CraftingDecoratedPotRecipe)로 서로 다른 조각 넷을
        // 받아 {@code pot_decorations} 컴포넌트를 만든다. 이 저장소에는 아이템 컴포넌트가
        // 없어 <b>같은 재료 넷</b>만 성립시키고 무늬를 ID 로 나눴다(근거는 Blocks.DECORATED_POT
        // 주석과 docs/research/mc-archaeology-1214.md §4). 그래서 조합 규칙이 평범한 shaped
        // 레시피 여덟 줄로 표현되고, 특수 레시피 축을 새로 만들지 않는다.
        for (int pot = Blocks.DECORATED_POT; pot <= Blocks.SNORT_DECORATED_POT; pot++) {
            short ingredient = com.gameexpert.engine.ArchaeologyRules.decoratedPotIngredient(pot);
            int material = Short.toUnsignedInt(ingredient);
            registerShaped("decorated_pot_" + (pot - Blocks.DECORATED_POT), (short) pot, 1,
                    shape(0, material, 0,
                            material, 0, material,
                            0, material, 0),
                    new Ingredient(ingredient, Blocks.DECORATED_POT_SHERD_COUNT));
        }

        // 형상 변형. 담장이 있는 재질은 프리즈머린 하나뿐이라(바닐라에 prismarine_brick_wall ·
        // dark_prismarine_wall 이 없다) 나머지 둘은 계단·반 블록만 등록한다.
        registerDeepslateShapes("prismarine", (short) Blocks.PRISMARINE,
                (short) Blocks.PRISMARINE_STAIRS, (short) Blocks.PRISMARINE_SLAB,
                (short) Blocks.PRISMARINE_WALL);
        registerStairsAndSlab("prismarine_brick", (short) Blocks.PRISMARINE_BRICKS,
                (short) Blocks.PRISMARINE_BRICK_STAIRS, (short) Blocks.PRISMARINE_BRICK_SLAB);
        registerStairsAndSlab("dark_prismarine", (short) Blocks.DARK_PRISMARINE,
                (short) Blocks.DARK_PRISMARINE_STAIRS, (short) Blocks.DARK_PRISMARINE_SLAB);

        // [QUARTZ] 석영 계열 제작식(MC Java 1.21.4 recipes 의 *.json 그대로).
        // 재료 대체는 하지 않는다 — 네더 부재는 재료가 아니라 **획득 경로**로 흡수했다.
        //   quartz_block.json           2×2 석영 4        → 석영 블록 1
        //   quartz_pillar.json          석영 블록 2 세로   → 석영 기둥 2
        //   chiseled_quartz_block.json  석영 반 블록 2 세로 → 조각된 석영 1
        //   quartz_bricks.json          2×2 석영 블록 4    → 석영 벽돌 4
        //   smooth_quartz.json          석영 블록 제련      → 매끄러운 석영 1 (아래 제련 절)
        //   quartz_stairs / smooth_quartz_stairs  6 → 4
        //   quartz_slab   / smooth_quartz_slab    3 → 6
        registerShaped("quartz_block", (short) Blocks.QUARTZ_BLOCK, 1,
                shape(Blocks.QUARTZ, Blocks.QUARTZ, 0,
                        Blocks.QUARTZ, Blocks.QUARTZ, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.QUARTZ, 4));
        registerPillar("quartz_pillar", (short) Blocks.QUARTZ_BLOCK,
                (short) Blocks.QUARTZ_PILLAR);
        registerChiseled("chiseled_quartz_block", (short) Blocks.QUARTZ_SLAB,
                (short) Blocks.CHISELED_QUARTZ_BLOCK);
        registerQuarry("quartz_bricks", (short) Blocks.QUARTZ_BLOCK,
                (short) Blocks.QUARTZ_BRICKS);
        // 형상 변형. 담장 3종은 바닐라에 없는 WebCraft 고유 추가라 기존 담장 계약(6→6)을
        // 그대로 쓴다 — 석영 벽돌은 바닐라에 계단·반 블록도 없으므로 담장 하나만 등록한다.
        registerDeepslateShapes("quartz", (short) Blocks.QUARTZ_BLOCK,
                (short) Blocks.QUARTZ_STAIRS, (short) Blocks.QUARTZ_SLAB,
                (short) Blocks.QUARTZ_WALL);
        registerDeepslateShapes("smooth_quartz", (short) Blocks.SMOOTH_QUARTZ,
                (short) Blocks.SMOOTH_QUARTZ_STAIRS, (short) Blocks.SMOOTH_QUARTZ_SLAB,
                (short) Blocks.SMOOTH_QUARTZ_WALL);
        registerWall("quartz_brick_wall", (short) Blocks.QUARTZ_BRICKS,
                (short) Blocks.QUARTZ_BRICK_WALL);

        // [COPPER] 구리 계열 제작식(MC Java 1.21.4 recipes 의 *.json 그대로).
        //
        // 산화 4단계는 바닐라도 단계마다 별도 레시피가 있다(exposed_cut_copper 는 약간 녹슨
        // 구리 블록에서만 나온다 — 단계가 섞이는 레시피는 바닐라에 없다). 그래서 여기서도
        // 단계 루프 하나로 네 벌을 등록하고, 재료도 언제나 **같은 단계**를 쓴다.
        //
        // 문·다락문·피뢰침은 구리 주괴에서 만들므로 <b>unaffected 단계에만</b> 레시피가 있다.
        // 나머지 세 단계는 바닐라와 같이 산화로만 얻는다.
        //
        // 밀랍 형상도 1.21.9 데이터팩에 같은 일반 제작식이 있다. 밀랍 도포 레시피는 루프 뒤에서
        // 별도로 등록하며, 밀랍 제거는 제작식이 아니라 도끼 상호작용만 존재한다.
        for (int stage = 0; stage < Blocks.COPPER_OXIDATION_STAGES; stage++) {
            String prefix = COPPER_STAGE_RECIPE_PREFIX[stage];
            short block = (short) (Blocks.COPPER_BLOCK + stage);
            short cut = (short) (Blocks.CUT_COPPER + stage);
            short slab = (short) (Blocks.CUT_COPPER_SLAB + stage);
            // 잘린 구리: 구리 블록 4 → 4(2×2 가공). 바닐라 cut_copper.json.
            registerQuarry(prefix + "cut_copper", block, cut);
            // 계단 6→4 · 반 블록 3→6. 바닐라에 잘린 구리 담장은 없으므로 담장은 등록하지 않는다.
            registerStairsAndSlab(prefix + "cut_copper", cut,
                    (short) (Blocks.CUT_COPPER_STAIRS + stage), slab);
            // 조각된 구리(1.21): 잘린 구리 반 블록 2 를 세로로 쌓아 1.
            registerChiseled(prefix + "chiseled_copper", slab,
                    (short) (Blocks.CHISELED_COPPER + stage));
            registerGrate(prefix + "copper_grate", block,
                    (short) (Blocks.COPPER_GRATE + stage));
            registerBulb(prefix + "copper_bulb", block,
                    (short) (Blocks.COPPER_BULB + stage));
            short waxedBlock = (short) (Blocks.WAXED_COPPER_BLOCK + stage);
            short waxedCut = (short) (Blocks.WAXED_CUT_COPPER + stage);
            short waxedSlab = (short) (Blocks.WAXED_CUT_COPPER_SLAB + stage);
            String waxedPrefix = "waxed_" + prefix;
            registerQuarry(waxedPrefix + "cut_copper", waxedBlock, waxedCut);
            registerStairsAndSlab(waxedPrefix + "cut_copper", waxedCut,
                    (short) (Blocks.WAXED_CUT_COPPER_STAIRS + stage), waxedSlab);
            registerChiseled(waxedPrefix + "chiseled_copper", waxedSlab,
                    (short) (Blocks.WAXED_CHISELED_COPPER + stage));
            registerGrate(waxedPrefix + "copper_grate", waxedBlock,
                    (short) (Blocks.WAXED_COPPER_GRATE + stage));
            registerBulb(waxedPrefix + "copper_bulb", waxedBlock,
                    (short) (Blocks.WAXED_COPPER_BULB + stage));
        }
        // 구리 문: 주괴 6(2폭 3단) → 3. 바닐라 금속 문 배치(iron_door)와 같다.
        registerShaped("copper_door", (short) Blocks.COPPER_DOOR, 3,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0,
                        PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0,
                        PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 6));
        // 구리 다락문 copper_trapdoor.json(1.21.9) — 주괴 4(2×2) → 1.
        // 근거 등급 A — minecraft.wiki/w/Copper_Trapdoor 제작 표: {{Crafting}} 격자가 2×2 네
        // 칸(B2/B3/C2/C3)에 구리 주괴만 놓고 산출이 1 이다. **목재 다락문(판자 6, 3폭 2단 → 2)
        // 배치를 물려받지 않는다** — 금속 다락문은 철 다락문과 같은 2×2 → 1 이고, 3폭 2단
        // 여섯 칸은 같은 계열의 **구리 창살**(→16)이 가져간 배치다. 두 레시피가 재료 총량까지
        // 같아지면 격자 매칭이 갈라지지 않으므로(CraftRecipePatternTest) 배치도 반드시 다르다.
        registerShaped("copper_trapdoor", (short) Blocks.COPPER_TRAPDOOR, 1,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0,
                        PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 4));
        // 피뢰침: 주괴 3 을 세로로 → 1. 바닐라 lightning_rod.json 그대로다.
        registerShaped("lightning_rod", (short) Blocks.LIGHTNING_ROD, 1,
                shape(PlayerInventory.COPPER_INGOT, 0, 0,
                        PlayerInventory.COPPER_INGOT, 0, 0,
                        PlayerInventory.COPPER_INGOT, 0, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 3));
        // [PROP-MATERIAL] 1.21.9 copper_lantern.json: 조각 8 + 중앙 구리 횃불 → 1.
        registerShaped("copper_lantern", (short) Blocks.COPPER_LANTERN, 1,
                shape(PlayerInventory.COPPER_NUGGET, PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET, Blocks.COPPER_TORCH,
                        PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET, PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET),
                new Ingredient(PlayerInventory.COPPER_NUGGET, 8),
                new Ingredient((short) Blocks.COPPER_TORCH, 1));
        registerCopperWaxRecipes();

        // [OPENABLE-METAL] 금속 개폐·격자 계열(바닐라 recipes/*.json 그대로).
        //
        // 철 문 iron_door.json — 주괴 6(2폭 3단) → 3. 구리 문과 배치·산출이 같고 재료만 철이다.
        registerShaped("iron_door", (short) Blocks.IRON_DOOR, 3,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 6));
        // 철 다락문 iron_trapdoor.json — 주괴 4(2×2) → 1. 목재·구리 다락문(6→2·6→2)과 달리
        // 바닐라 철 다락문만 2×2 이고 산출이 1 이다. 배치를 다른 다락문에 맞추지 않는다.
        registerShaped("iron_trapdoor", (short) Blocks.IRON_TRAPDOOR, 1,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 4));
        // 구리 창살 copper_bars.json(1.21.9) — 주괴 6(3폭 2단) → 16. 철창과 같은 배치·산출이고
        // 재료만 구리 주괴다. 산화 단계는 바닐라와 같이 **unaffected 에만** 레시피가 있고
        // 나머지 세 단계는 산화로만 얻는다(구리 문·다락문과 같은 규칙).
        registerShaped("copper_bars", (short) Blocks.COPPER_BARS, 16,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT,
                        PlayerInventory.COPPER_INGOT,
                        PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT,
                        PlayerInventory.COPPER_INGOT, 0, 0, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 6));

        // [VILLAGER-STATION] 주민 직업 스테이션 제작식(MC Java 1.21.4 recipes 의 *.json 그대로).
        // [POTION] 양조대만 divergence 다 — 블레이즈 막대(네더)가 미등록이라 그 자리에
        // 철 주괴를 쓴다. 나머지 열두 종은 바닐라 재료 그대로다.
        registerShaped("barrel", (short) Blocks.BARREL, 1,
                shape(Blocks.PLANK, Blocks.PLANK_SLAB, Blocks.PLANK,
                        Blocks.PLANK, 0, Blocks.PLANK,
                        Blocks.PLANK, Blocks.PLANK_SLAB, Blocks.PLANK),
                new Ingredient((short) Blocks.PLANK, 6),
                new Ingredient((short) Blocks.PLANK_SLAB, 2));
        registerShaped("blast_furnace", (short) Blocks.BLAST_FURNACE, 1,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, Blocks.FURNACE, PlayerInventory.IRON_INGOT,
                        Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE),
                new Ingredient(PlayerInventory.IRON_INGOT, 5),
                new Ingredient((short) Blocks.FURNACE, 1),
                new Ingredient((short) Blocks.SMOOTH_STONE, 3));
        // [POTION] 양조대. **바닐라 원본 그대로** 블레이즈 막대 1 + 조약돌 3 이다. 네더가 없는
        // 것은 재료가 아니라 획득 경로의 문제라, 막대를 대체하지 않고 레이드 승리 보상 풀과
        // 던전 상자 전리품으로 얻게 한다(MC-REFERENCE 「물약·양조」 절의 획득 경로 divergence).
        registerShaped("brewing_stand", (short) Blocks.BREWING_STAND, 1,
                shape(0, PlayerInventory.BLAZE_ROD, 0,
                        Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                        0, 0, 0),
                new Ingredient(PlayerInventory.BLAZE_ROD, 1),
                new Ingredient((short) Blocks.COBBLE, 3));
        registerShaped("cartography_table", (short) Blocks.CARTOGRAPHY_TABLE, 1,
                shape(PlayerInventory.PAPER, PlayerInventory.PAPER, 0,
                        Blocks.PLANK, Blocks.PLANK, 0,
                        Blocks.PLANK, Blocks.PLANK, 0),
                new Ingredient(PlayerInventory.PAPER, 2),
                new Ingredient((short) Blocks.PLANK, 4));
        registerShaped("cauldron", (short) Blocks.CAULDRON, 1,
                shape(PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT),
                new Ingredient(PlayerInventory.IRON_INGOT, 7));
        registerShaped("composter", (short) Blocks.COMPOSTER, 1,
                shape(Blocks.PLANK_SLAB, 0, Blocks.PLANK_SLAB,
                        Blocks.PLANK_SLAB, 0, Blocks.PLANK_SLAB,
                        Blocks.PLANK_SLAB, Blocks.PLANK_SLAB, Blocks.PLANK_SLAB),
                new Ingredient((short) Blocks.PLANK_SLAB, 7));
        registerShaped("fletching_table", (short) Blocks.FLETCHING_TABLE, 1,
                shape(PlayerInventory.FLINT, PlayerInventory.FLINT, 0,
                        Blocks.PLANK, Blocks.PLANK, 0,
                        Blocks.PLANK, Blocks.PLANK, 0),
                new Ingredient(PlayerInventory.FLINT, 2),
                new Ingredient((short) Blocks.PLANK, 4));
        // WebCraft divergence: 바닐라는 stone_slab 한 개를 받지만 WebCraft 에 돌 반 블록 ID 가
        // 없다. 같은 석재 계열의 조약돌 반 블록으로 대체하고 나머지 재료·개수는 바닐라대로 둔다.
        registerShaped("grindstone", (short) Blocks.GRINDSTONE, 1,
                shape(PlayerInventory.STICK, Blocks.COBBLE_SLAB, PlayerInventory.STICK,
                        Blocks.PLANK, 0, Blocks.PLANK, 0, 0, 0),
                new Ingredient(PlayerInventory.STICK, 2),
                new Ingredient((short) Blocks.COBBLE_SLAB, 1),
                new Ingredient((short) Blocks.PLANK, 2));
        registerShaped("lectern", (short) Blocks.LECTERN, 1,
                shape(Blocks.PLANK_SLAB, Blocks.PLANK_SLAB, Blocks.PLANK_SLAB,
                        0, Blocks.BOOKSHELF, 0,
                        0, Blocks.PLANK_SLAB, 0),
                new Ingredient((short) Blocks.PLANK_SLAB, 4),
                new Ingredient((short) Blocks.BOOKSHELF, 1));
        registerShaped("loom", (short) Blocks.LOOM, 1,
                shape(PlayerInventory.STRING, PlayerInventory.STRING, 0,
                        Blocks.PLANK, Blocks.PLANK, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.STRING, 2),
                new Ingredient((short) Blocks.PLANK, 2));
        registerShaped("smithing_table", (short) Blocks.SMITHING_TABLE, 1,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0,
                        Blocks.PLANK, Blocks.PLANK, 0,
                        Blocks.PLANK, Blocks.PLANK, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 2),
                new Ingredient((short) Blocks.PLANK, 4));
        registerShaped("smoker", (short) Blocks.SMOKER, 1,
                shape(0, Blocks.LOG, 0,
                        Blocks.LOG, Blocks.FURNACE, Blocks.LOG,
                        0, Blocks.LOG, 0),
                new Ingredient((short) Blocks.LOG, 4),
                new Ingredient((short) Blocks.FURNACE, 1));
        registerShaped("stonecutter", (short) Blocks.STONECUTTER, 1,
                shape(0, PlayerInventory.IRON_INGOT, 0,
                        Blocks.STONE, Blocks.STONE, Blocks.STONE, 0, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 1),
                new Ingredient((short) Blocks.STONE, 3));
        registerShaped("wood_fence", (short) Blocks.WOOD_FENCE, 3,
                shape(Blocks.PLANK, PlayerInventory.STICK, Blocks.PLANK,
                        Blocks.PLANK, PlayerInventory.STICK, Blocks.PLANK, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 4), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("wood_fence_gate", (short) Blocks.WOOD_FENCE_GATE, 1,
                shape(PlayerInventory.STICK, Blocks.PLANK, PlayerInventory.STICK,
                        PlayerInventory.STICK, Blocks.PLANK, PlayerInventory.STICK, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 2), new Ingredient(PlayerInventory.STICK, 4));
        registerShaped("glass_pane", (short) Blocks.GLASS_PANE, 16,
                shape(Blocks.GLASS, Blocks.GLASS, Blocks.GLASS,
                        Blocks.GLASS, Blocks.GLASS, Blocks.GLASS, 0, 0, 0),
                new Ingredient((short) Blocks.GLASS, 6));
        registerShaped("iron_bars", (short) Blocks.IRON_BARS, 16,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        0, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 6));
        registerShaped("wood_trapdoor", (short) Blocks.WOOD_TRAPDOOR, 2,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 6));
        registerShaped("rail", (short) Blocks.RAIL, 16,
                shape(PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, PlayerInventory.STICK, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT),
                new Ingredient(PlayerInventory.IRON_INGOT, 6), new Ingredient(PlayerInventory.STICK, 1));
        registerShaped("campfire", (short) Blocks.CAMPFIRE, 1,
                shape(0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, PlayerInventory.COAL, PlayerInventory.STICK,
                        Blocks.LOG, Blocks.LOG, Blocks.LOG),
                new Ingredient((short) Blocks.LOG, 3), new Ingredient(PlayerInventory.STICK, 3),
                new Ingredient(PlayerInventory.COAL, 1));
        registerShaped("jack_o_lantern", (short) Blocks.JACK_O_LANTERN, 1,
                shape(Blocks.CARVED_PUMPKIN, 0, 0, Blocks.TORCH, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.CARVED_PUMPKIN, 1), new Ingredient((short) Blocks.TORCH, 1));
        registerShaped("glass_bottle", (short) Blocks.GLASS_BOTTLE, 3,
                shape(Blocks.GLASS, 0, Blocks.GLASS, 0, Blocks.GLASS, 0, 0, 0, 0),
                new Ingredient((short) Blocks.GLASS, 3));
        register("gold_nugget", (short) Blocks.GOLD_NUGGET, 9,
                new Ingredient(PlayerInventory.GOLD_INGOT, 1));
        // ── [PROP-MATERIAL] 금속 조각 사슬(바닐라 recipes/*.json 그대로) ──────────────
        //
        // 조각↔주괴는 **양방향**이다. 이 저장소에는 금 조각 1→9 만 있었고 되돌리는
        // gold_ingot_from_nuggets 가 없어 금 조각이 사실상 소각 경로였다(감사 결과).
        // 철·구리는 조각 자체가 없었다. 셋을 같은 틀로 한꺼번에 닫는다.
        //
        // 9→1 은 바닐라 *_from_nuggets 와 같이 3×3 을 가득 채운 shaped 다(shapeless 로 두면
        // 2×2 인벤 격자에서도 성립해 작업대 요구가 사라진다).
        register("iron_nugget", PlayerInventory.IRON_NUGGET, 9,
                new Ingredient(PlayerInventory.IRON_INGOT, 1));
        register("copper_nugget", PlayerInventory.COPPER_NUGGET, 9,
                new Ingredient(PlayerInventory.COPPER_INGOT, 1));
        registerShaped("iron_ingot_from_nuggets", PlayerInventory.IRON_INGOT, 1,
                shape(PlayerInventory.IRON_NUGGET, PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET, PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET, PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET),
                new Ingredient(PlayerInventory.IRON_NUGGET, 9));
        registerShaped("gold_ingot_from_nuggets", PlayerInventory.GOLD_INGOT, 1,
                shape(PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET),
                new Ingredient(PlayerInventory.GOLD_NUGGET, 9));
        registerShaped("copper_ingot_from_nuggets", PlayerInventory.COPPER_INGOT, 1,
                shape(PlayerInventory.COPPER_NUGGET, PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET, PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET, PlayerInventory.COPPER_NUGGET,
                        PlayerInventory.COPPER_NUGGET),
                new Ingredient(PlayerInventory.COPPER_NUGGET, 9));
        // honeycomb_block.json: 벌집 조각 4개 2×2 → 블록 1. 공식 역제작식은 없다.
        registerShaped("honeycomb_block", (short) Blocks.HONEYCOMB_BLOCK, 1,
                shape(PlayerInventory.HONEYCOMB, PlayerInventory.HONEYCOMB, 0,
                        PlayerInventory.HONEYCOMB, PlayerInventory.HONEYCOMB, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.HONEYCOMB, 4));
        // 랜턴 lantern.json — 철 조각 8 이 중앙 횃불 하나를 둘러싼 3×3 → 1. 구리 랜턴이
        // 이 배치를 재료만 바꿔 그대로 쓴다(위 copper_lantern).
        registerShaped("lantern", (short) Blocks.LANTERN, 1,
                shape(PlayerInventory.IRON_NUGGET, PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET, Blocks.TORCH, PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET, PlayerInventory.IRON_NUGGET,
                        PlayerInventory.IRON_NUGGET),
                new Ingredient(PlayerInventory.IRON_NUGGET, 8),
                new Ingredient((short) Blocks.TORCH, 1));
        // 사슬 chain.json — 조각·주괴·조각을 세로로 → 1. 산출은 축 y 대표 ID 다.
        registerShaped("chain", (short) Blocks.CHAIN, 1,
                shape(PlayerInventory.IRON_NUGGET, 0, 0,
                        PlayerInventory.IRON_INGOT, 0, 0,
                        PlayerInventory.IRON_NUGGET, 0, 0),
                new Ingredient(PlayerInventory.IRON_NUGGET, 2),
                new Ingredient(PlayerInventory.IRON_INGOT, 1));
        // [COPPER-CHAIN] 구리 사슬 copper_chain.json(1.21.9) — 철 사슬과 **같은 틀**이고
        // 재료만 구리다(조각 2 + 주괴 1 → 1). 산출은 축 y · 산화 미진행 대표 ID 이며,
        // 산화 세 단계와 밀랍 열두 종에는 제작식이 없다(산화·밀랍 도포로만 얻는다).
        registerShaped("copper_chain", (short) Blocks.COPPER_CHAIN, 1,
                shape(PlayerInventory.COPPER_NUGGET, 0, 0,
                        PlayerInventory.COPPER_INGOT, 0, 0,
                        PlayerInventory.COPPER_NUGGET, 0, 0),
                new Ingredient(PlayerInventory.COPPER_NUGGET, 2),
                new Ingredient(PlayerInventory.COPPER_INGOT, 1));
        // 이름표 name_tag.json(Java 26.1) — 조각 1 + 종이 1 → 1. 바닐라 입력은 태그
        // #minecraft:nuggets(철·금·구리 아무거나)라, 태그가 없는 이 저장소는 세 갈래로 편다.
        // 이름표는 이미 등록된 아이템이었고 **제작 경로만 없었다**(감사 결과 — 종이의 결손).
        register("name_tag_from_iron_nugget", PlayerInventory.NAME_TAG, 1,
                new Ingredient(PlayerInventory.IRON_NUGGET, 1),
                new Ingredient(PlayerInventory.PAPER, 1));
        register("name_tag_from_gold_nugget", PlayerInventory.NAME_TAG, 1,
                new Ingredient(PlayerInventory.GOLD_NUGGET, 1),
                new Ingredient(PlayerInventory.PAPER, 1));
        register("name_tag_from_copper_nugget", PlayerInventory.NAME_TAG, 1,
                new Ingredient(PlayerInventory.COPPER_NUGGET, 1),
                new Ingredient(PlayerInventory.PAPER, 1));
        register("pumpkin_seeds", (short) Blocks.PUMPKIN_SEEDS, 4,
                new Ingredient((short) Blocks.PUMPKIN, 1));
        registerShaped("shears", (short) Blocks.SHEARS, 1,
                shape(0, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, 0, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 2));
        // 바닐라 bucket: 윗줄 양 끝과 그 아래 가운데의 철 주괴 3개.
        registerShaped("bucket", PlayerInventory.BUCKET, 1,
                shape(PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT,
                        0, PlayerInventory.IRON_INGOT, 0,
                        0, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 3));
        // 바닐라 tripwire_hook: 철 주괴·막대·판자를 세로로 쌓아 2개를 만든다.
        registerShaped("tripwire_hook", PlayerInventory.TRIPWIRE_HOOK, 2,
                shape(PlayerInventory.IRON_INGOT, 0, 0,
                        PlayerInventory.STICK, 0, 0,
                        Blocks.PLANK, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 1),
                new Ingredient(PlayerInventory.STICK, 1),
                new Ingredient((short) Blocks.PLANK, 1));

        // S2a: 양털 ×3 + 판자 7 ×3 → 침대 32 ×1
        registerShaped("bed", (short) Blocks.BED, 1,
                shape(PlayerInventory.WOOL, PlayerInventory.WOOL, PlayerInventory.WOOL,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK, 0, 0, 0),
                new Ingredient(PlayerInventory.WOOL, 3), new Ingredient((short) Blocks.PLANK, 3));

        // ── 생존 S2b(§2·§10.2-S2b): 화로 · 제련 · 도구 티어 ──
        // 화로 33: 조약돌 8 ×8 → 33 ×1 (일반 제작).
        registerShaped("furnace", (short) Blocks.FURNACE, 1,
                shape(Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                        Blocks.COBBLE, 0, Blocks.COBBLE,
                        Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE),
                new Ingredient((short) Blocks.COBBLE, 8));
        registerShaped("crafting_table", (short) Blocks.CRAFTING_TABLE, 1,
                shape(Blocks.PLANK, Blocks.PLANK, 0, Blocks.PLANK, Blocks.PLANK, 0, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 4));
        // 판자 8개를 3×3 테두리에 놓아 상자 하나를 만든다.
        registerShaped("chest", (short) Blocks.CHEST, 1,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK,
                        Blocks.PLANK, 0, Blocks.PLANK,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK),
                new Ingredient((short) Blocks.PLANK, 8));
        // [CONTAINER-MENUS] Redstone containers, exactly data/minecraft/recipe/*.json of the pinned
        // 26.3-snapshot-7 jar with no ingredient substituted: every one already exists (cobblestone
        // 8, bow 332, redstone dust 347, iron ingot 298, chest 69, crafting table 74). All are
        // 3x3 and need the crafting table.
        // dispenser.json: "###" / "#X#" / "#R#" (# cobblestone, X bow, R redstone).
        registerShaped("dispenser", (short) Blocks.DISPENSER, 1,
                shape(Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                        Blocks.COBBLE, PlayerInventory.BOW, Blocks.COBBLE,
                        Blocks.COBBLE, PlayerInventory.REDSTONE_DUST, Blocks.COBBLE),
                new Ingredient((short) Blocks.COBBLE, 7),
                new Ingredient(PlayerInventory.BOW, 1),
                new Ingredient(PlayerInventory.REDSTONE_DUST, 1));
        // dropper.json: "###" / "# #" / "#R#".
        registerShaped("dropper", (short) Blocks.DROPPER, 1,
                shape(Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                        Blocks.COBBLE, 0, Blocks.COBBLE,
                        Blocks.COBBLE, PlayerInventory.REDSTONE_DUST, Blocks.COBBLE),
                new Ingredient((short) Blocks.COBBLE, 7),
                new Ingredient(PlayerInventory.REDSTONE_DUST, 1));
        // hopper.json: "I I" / "ICI" / " I " (I iron ingot, C chest).
        registerShaped("hopper", (short) Blocks.HOPPER, 1,
                shape(PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, Blocks.CHEST, PlayerInventory.IRON_INGOT,
                        0, PlayerInventory.IRON_INGOT, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 5),
                new Ingredient((short) Blocks.CHEST, 1));
        // crafter.json: "###" / "#C#" / "RDR" (# iron ingot, C crafting table, R redstone,
        // D dropper).
        registerShaped("crafter", (short) Blocks.CRAFTER, 1,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, Blocks.CRAFTING_TABLE,
                        PlayerInventory.IRON_INGOT,
                        PlayerInventory.REDSTONE_DUST, Blocks.DROPPER,
                        PlayerInventory.REDSTONE_DUST),
                new Ingredient(PlayerInventory.IRON_INGOT, 5),
                new Ingredient((short) Blocks.CRAFTING_TABLE, 1),
                new Ingredient(PlayerInventory.REDSTONE_DUST, 2),
                new Ingredient((short) Blocks.DROPPER, 1));
        // [CONTAINER-MENUS] recipe/minecart.json: "# #" / "###" (# iron ingot). 3 칸 폭이라 작업대 전용.
        registerShaped("minecart", PlayerInventory.MINECART, 1,
                shape(PlayerInventory.IRON_INGOT, 0, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.IRON_INGOT,
                        0, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 5));
        // [CONTAINER-MENUS] recipe/{chest,furnace,hopper,tnt}_minecart.json: 무정형 (블록 + 광산 수레).
        register("chest_minecart", PlayerInventory.CHEST_MINECART, 1,
                new Ingredient((short) Blocks.CHEST, 1), new Ingredient(PlayerInventory.MINECART, 1));
        register("furnace_minecart", PlayerInventory.FURNACE_MINECART, 1,
                new Ingredient((short) Blocks.FURNACE, 1), new Ingredient(PlayerInventory.MINECART, 1));
        register("hopper_minecart", PlayerInventory.HOPPER_MINECART, 1,
                new Ingredient((short) Blocks.HOPPER, 1), new Ingredient(PlayerInventory.MINECART, 1));
        register("tnt_minecart", PlayerInventory.TNT_MINECART, 1,
                new Ingredient((short) Blocks.TNT, 1), new Ingredient(PlayerInventory.MINECART, 1));
        // [CONTAINER-MENUS] recipe/armor_stand.json: "///" / " / " / "/_/" (/ stick, _ smooth stone slab).
        registerShaped("armor_stand", PlayerInventory.ARMOR_STAND, 1,
                shape(PlayerInventory.STICK, PlayerInventory.STICK, PlayerInventory.STICK,
                        0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, Blocks.SMOOTH_STONE_SLAB, PlayerInventory.STICK),
                new Ingredient(PlayerInventory.STICK, 6),
                new Ingredient((short) Blocks.SMOOTH_STONE_SLAB, 1));
        // [CHEST-FAMILY] 덫 상자 — 바닐라 minecraft:trapped_chest 그대로 **무정형**
        // (상자 1 + 철사 덫 갈고리 1). 재료를 하나도 대체하지 않는다: 갈고리는 이미
        // TRIPWIRE_HOOK=488 로 있다. 무정형이라 2×2 인벤토리 격자에서도 만들어진다
        // (재료가 둘이라 occupiedSlots=2 ≤ 4 — 바닐라도 작업대를 요구하지 않는다).
        register("trapped_chest", (short) Blocks.TRAPPED_CHEST, 1,
                new Ingredient((short) Blocks.CHEST, 1),
                new Ingredient((short) Blocks.TRIPWIRE_HOOK, 1));
        // [CHEST-FAMILY] 구리 상자 — 바닐라 minecraft:copper_chest 그대로 가운데 상자 1 을
        // 구리 주괴 8 로 둘러싼다. 3×3 을 다 쓰므로 작업대 전용이다.
        registerShaped("copper_chest", (short) Blocks.COPPER_CHEST, 1,
                shape(Blocks.COPPER_INGOT, Blocks.COPPER_INGOT, Blocks.COPPER_INGOT,
                        Blocks.COPPER_INGOT, Blocks.CHEST, Blocks.COPPER_INGOT,
                        Blocks.COPPER_INGOT, Blocks.COPPER_INGOT, Blocks.COPPER_INGOT),
                new Ingredient((short) Blocks.COPPER_INGOT, 8),
                new Ingredient((short) Blocks.CHEST, 1));
        // [ENDER-SHULKER] 엔더 상자 — 바닐라 minecraft:ender_chest 그대로 가운데 엔더의 눈 1 을
        // 흑요석 8 로 둘러싼다. 재료를 하나도 대체하지 않는다: 흑요석(56)과 엔더의 눈(1604)이
        // 둘 다 이미 있다. 3×3 을 다 쓰므로 작업대 전용이다.
        registerShaped("ender_chest", (short) Blocks.ENDER_CHEST, 1,
                shape(Blocks.OBSIDIAN, Blocks.OBSIDIAN, Blocks.OBSIDIAN,
                        Blocks.OBSIDIAN, Blocks.EYE_OF_ENDER, Blocks.OBSIDIAN,
                        Blocks.OBSIDIAN, Blocks.OBSIDIAN, Blocks.OBSIDIAN),
                new Ingredient((short) Blocks.OBSIDIAN, 8),
                new Ingredient((short) Blocks.EYE_OF_ENDER, 1));
        // [ENDER-SHULKER] 셜커 상자 — 바닐라 minecraft:shulker_box 그대로 **세로 3칸**
        // (껍데기 1 / 상자 1 / 껍데기 1). 세로 3 이라 2×2 를 넘어 작업대 전용이다.
        // 셜커 껍데기(1613)는 앞선 웨이브가 던전 상자 획득 경로와 함께 이미 착지시켰다.
        registerShaped("shulker_box", (short) Blocks.SHULKER_BOX, 1,
                shape(Blocks.SHULKER_SHELL, 0, 0,
                        Blocks.CHEST, 0, 0,
                        Blocks.SHULKER_SHELL, 0, 0),
                new Ingredient((short) Blocks.SHULKER_SHELL, 2),
                new Ingredient((short) Blocks.CHEST, 1));
        // [ENDER-SHULKER] 셜커 상자 염색 16색 — 바닐라 minecraft:<color>_shulker_box 그대로
        // **무정형** {셜커 상자 1 + 그 색 염료 1}. 바닐라 재료 태그는 #minecraft:shulker_boxes
        // 라 **이미 염색된 상자도 다시 염색된다** — 그 확장은 acceptsItem 이 소유한다
        // (양털 재염색이 낸 선례). 무정형이고 재료가 둘이라 2×2 인벤토리 격자에서도 만들어진다.
        //
        // **내용은 염색해도 유지된다**([B]). 그 유지는 레시피 표가 아니라 제작 결과 칸으로
        // shulkerId 간접 참조를 옮기는 제작 경로가 소유한다.
        for (int color = 0; color < DYE_COLOR_NAMES.length; color++) {
            register(DYE_COLOR_NAMES[color] + "_shulker_box",
                    (short) Blocks.shulkerBoxForDyeColor(color), 1,
                    new Ingredient((short) Blocks.SHULKER_BOX, 1),
                    new Ingredient(DYE_BY_COLOR[color], 1));
        }
        // TNT: 3×3 바둑판 모양(화약 5 + 모래 4)이므로 작업대 전용이다.
        registerShaped("tnt", (short) Blocks.TNT, 1,
                shape(PlayerInventory.GUNPOWDER, Blocks.SAND, PlayerInventory.GUNPOWDER,
                        Blocks.SAND, PlayerInventory.GUNPOWDER, Blocks.SAND,
                        PlayerInventory.GUNPOWDER, Blocks.SAND, PlayerInventory.GUNPOWDER),
                new Ingredient(PlayerInventory.GUNPOWDER, 5),
                new Ingredient((short) Blocks.SAND, 4));

        registerSmelt("beef_cooked", PlayerInventory.BEEF_COOKED, 1,
                new Ingredient(PlayerInventory.BEEF_RAW, 1));
        registerSmelt("pork_cooked", PlayerInventory.PORK_COOKED, 1,
                new Ingredient(PlayerInventory.PORK_RAW, 1));
        registerSmelt("mutton_cooked", PlayerInventory.MUTTON_COOKED, 1,
                new Ingredient(PlayerInventory.MUTTON_RAW, 1));
        registerSmelt("chicken_cooked", PlayerInventory.CHICKEN_COOKED, 1,
                new Ingredient(PlayerInventory.CHICKEN_RAW, 1));
        registerSmelt("rabbit_cooked", PlayerInventory.RABBIT_COOKED, 1,
                new Ingredient(PlayerInventory.RABBIT_RAW, 1));
        registerSmelt("baked_potato", PlayerInventory.BAKED_POTATO, 1,
                new Ingredient(PlayerInventory.POTATO, 1));
        registerSmelt("cod_cooked", PlayerInventory.COD_COOKED, 1,
                new Ingredient(PlayerInventory.COD_RAW, 1));
        registerSmelt("salmon_cooked", PlayerInventory.SALMON_COOKED, 1,
                new Ingredient(PlayerInventory.SALMON_RAW, 1));
        registerSmelt("iron_ingot", PlayerInventory.IRON_INGOT, 1,
                new Ingredient(PlayerInventory.RAW_IRON, 1));
        registerSmelt("gold_ingot", PlayerInventory.GOLD_INGOT, 1,
                new Ingredient(PlayerInventory.RAW_GOLD, 1));
        registerSmelt("copper_ingot", PlayerInventory.COPPER_INGOT, 1,
                new Ingredient(PlayerInventory.RAW_COPPER, 1));
        registerSmelt("emerald", PlayerInventory.EMERALD, 1,
                new Ingredient((short) Blocks.EMERALD_ORE, 1));
        registerSmelt("lapis_lazuli", PlayerInventory.LAPIS_LAZULI, 1,
                new Ingredient((short) Blocks.LAPIS_ORE, 1));
        registerSmelt("redstone_dust", PlayerInventory.REDSTONE_DUST, 1,
                new Ingredient((short) Blocks.REDSTONE_ORE, 1));
        registerSmelt("netherite_scrap", PlayerInventory.NETHERITE_SCRAP, 1,
                new Ingredient((short) Blocks.ANCIENT_DEBRIS, 1));
        // [ARMOR-TRIM] recipe/resin_brick.json: smelting resin_clump → resin_brick(경험치 0.1).
        registerSmelt("resin_brick", PlayerInventory.RESIN_BRICK, 1,
                new Ingredient((short) Blocks.RESIN_CLUMP, 1));
        registerSmelt("glass", (short) Blocks.GLASS, 1,
                new Ingredient((short) Blocks.SAND, 1));
        // 금 간 심층암 벽돌·타일은 바닐라와 같이 제련으로만 만든다.
        registerSmelt("cracked_deepslate_bricks", (short) Blocks.CRACKED_DEEPSLATE_BRICKS, 1,
                new Ingredient((short) Blocks.DEEPSLATE_BRICKS, 1));
        registerSmelt("cracked_deepslate_tiles", (short) Blocks.CRACKED_DEEPSLATE_TILES, 1,
                new Ingredient((short) Blocks.DEEPSLATE_TILES, 1));
        // [VILLAGER-STATION] 매끄러운 돌은 돌을 제련해 얻는다(용광로 제작식의 부재료).
        registerSmelt("smooth_stone", (short) Blocks.SMOOTH_STONE, 1,
                new Ingredient((short) Blocks.STONE, 1));
        registerSmelt("stone", (short) Blocks.STONE, 1,
                new Ingredient((short) Blocks.COBBLE, 1));
        // [STONE-RESIDUAL] 제련으로만 나오는 산출 넷. 금 간 석재 벽돌은 제작식이 없고,
        // 매끄러운 사암/붉은 사암은 제련 산출이라 원석과 달리 경도 2.0 · 폭발 저항 6.0 이다.
        registerSmelt("cracked_stone_bricks", (short) Blocks.CRACKED_STONE_BRICKS, 1,
                new Ingredient((short) Blocks.STONE_BRICK, 1));
        registerSmelt("smooth_sandstone", (short) Blocks.SMOOTH_SANDSTONE, 1,
                new Ingredient((short) Blocks.SANDSTONE, 1));
        registerSmelt("smooth_red_sandstone", (short) Blocks.SMOOTH_RED_SANDSTONE, 1,
                new Ingredient((short) Blocks.RED_SANDSTONE, 1));
        // 점토 벽돌 아이템. 벽돌 계열 전체가 점토 덩이 굽기에서 시작한다.
        registerSmelt("brick", (short) Blocks.BRICK, 1,
                new Ingredient(PlayerInventory.CLAY_BALL, 1));
        // [SULFUR] 유황 광석 두 벌 → 유황 가루 1. FurnaceRules 표와 문자 그대로 같아야 하며
        // 그 전체 일치는 FurnaceCraftSmeltParityTest 가 고정한다.
        // [SULFUR] 화약 제작 확장. 바닐라 1.21.4 에는 gunpowder 제작법이 <b>존재하지 않는다</b>
        // — 화약은 크리퍼·가스트·마녀 드랍과 상자 전리품, 행상인 거래로만 나온다. 이 저장소도
        // 그 경로는 하나도 건드리지 않고(기존 화약 획득 경로 불변) <b>새 경로 하나만 더한다</b>:
        // 유황 가루 1 + 숯 1 → 화약 1 의 무형 제작이다.
        //
        // 근거·밸런스는 docs/MC-REFERENCE.md 「유황 — WebCraft original」 절이 소유한다. 요지는
        // 셋이다. (1) 현실 흑색화약이 초석·황·숯이고 그중 둘을 이 게임이 이미 가지고 있다.
        // (2) TNT 한 개가 화약 5 를 먹으므로 광석 한 번(가루 2–4)이 곧바로 TNT 가 되지 않는다.
        // (3) 유황 광석은 지형 생성이 만들지 않고 유황 동굴 지대(칸당 40% · 640 블록 격자)
        //     에서만 나오므로, 이 경로는 "크리퍼 사냥보다 빠른 지름길"이 아니라 "지대를 찾아낸
        //     보상"이다.
        // Java 26.3-snapshot-7 sulfur family. The former fictional ore/powder recipes are
        // intentionally absent: IDs 1301~1310 are append-only tombstones.
        registerQuarry("polished_sulfur", (short) Blocks.SULFUR_BLOCK,
                (short) Blocks.POLISHED_SULFUR);
        registerDeepslateShapes("sulfur", (short) Blocks.SULFUR_BLOCK,
                (short) Blocks.SULFUR_STAIRS, (short) Blocks.SULFUR_SLAB,
                (short) Blocks.SULFUR_WALL);
        registerDeepslateShapes("polished_sulfur", (short) Blocks.POLISHED_SULFUR,
                (short) Blocks.POLISHED_SULFUR_STAIRS, (short) Blocks.POLISHED_SULFUR_SLAB,
                (short) Blocks.POLISHED_SULFUR_WALL);
        registerQuarry("sulfur_bricks", (short) Blocks.POLISHED_SULFUR,
                (short) Blocks.SULFUR_BRICKS);
        registerDeepslateShapes("sulfur_brick", (short) Blocks.SULFUR_BRICKS,
                (short) Blocks.SULFUR_BRICK_STAIRS, (short) Blocks.SULFUR_BRICK_SLAB,
                (short) Blocks.SULFUR_BRICK_WALL);
        registerChiseled("chiseled_sulfur", (short) Blocks.SULFUR_SLAB,
                (short) Blocks.CHISELED_SULFUR);
        register("potent_sulfur", (short) Blocks.POTENT_SULFUR, 1,
                new Ingredient((short) Blocks.SULFUR_BLOCK, 9));
        registerShaped("sulfur_from_sulfur_spikes", (short) Blocks.SULFUR_BLOCK, 1,
                shape(Blocks.SULFUR_SPIKE, Blocks.SULFUR_SPIKE, 0,
                        Blocks.SULFUR_SPIKE, Blocks.SULFUR_SPIKE, 0, 0, 0, 0),
                new Ingredient((short) Blocks.SULFUR_SPIKE, 4));

        int[] sulfurStonecutOutputs = {
                Blocks.CHISELED_SULFUR, Blocks.POLISHED_SULFUR,
                Blocks.POLISHED_SULFUR_SLAB, Blocks.POLISHED_SULFUR_STAIRS,
                Blocks.POLISHED_SULFUR_WALL, Blocks.SULFUR_BRICKS,
                Blocks.SULFUR_BRICK_SLAB, Blocks.SULFUR_BRICK_STAIRS,
                Blocks.SULFUR_BRICK_WALL, Blocks.SULFUR_SLAB,
                Blocks.SULFUR_STAIRS, Blocks.SULFUR_WALL };
        String[] sulfurStonecutNames = {
                "chiseled_sulfur", "polished_sulfur", "polished_sulfur_slab",
                "polished_sulfur_stairs", "polished_sulfur_wall", "sulfur_bricks",
                "sulfur_brick_slab", "sulfur_brick_stairs", "sulfur_brick_wall",
                "sulfur_slab", "sulfur_stairs", "sulfur_wall" };
        for (int index = 0; index < sulfurStonecutOutputs.length; index++) {
            int output = sulfurStonecutOutputs[index];
            int count = output == Blocks.POLISHED_SULFUR_SLAB
                    || output == Blocks.SULFUR_BRICK_SLAB || output == Blocks.SULFUR_SLAB ? 2 : 1;
            registerStonecut(sulfurStonecutNames[index] + "_from_sulfur_stonecutting",
                    (short) output, count, (short) Blocks.SULFUR_BLOCK);
        }
        int[] polishedStonecutOutputs = { Blocks.POLISHED_SULFUR_SLAB,
                Blocks.POLISHED_SULFUR_STAIRS, Blocks.POLISHED_SULFUR_WALL,
                Blocks.SULFUR_BRICKS, Blocks.SULFUR_BRICK_SLAB,
                Blocks.SULFUR_BRICK_STAIRS, Blocks.SULFUR_BRICK_WALL };
        String[] polishedStonecutNames = { "polished_sulfur_slab", "polished_sulfur_stairs",
                "polished_sulfur_wall", "sulfur_bricks", "sulfur_brick_slab",
                "sulfur_brick_stairs", "sulfur_brick_wall" };
        for (int index = 0; index < polishedStonecutOutputs.length; index++) {
            int output = polishedStonecutOutputs[index];
            int count = output == Blocks.POLISHED_SULFUR_SLAB
                    || output == Blocks.SULFUR_BRICK_SLAB ? 2 : 1;
            registerStonecut(polishedStonecutNames[index] + "_from_polished_sulfur_stonecutting",
                    (short) output, count, (short) Blocks.POLISHED_SULFUR);
        }
        int[] brickStonecutOutputs = { Blocks.SULFUR_BRICK_SLAB,
                Blocks.SULFUR_BRICK_STAIRS, Blocks.SULFUR_BRICK_WALL };
        String[] brickStonecutNames = { "sulfur_brick_slab", "sulfur_brick_stairs",
                "sulfur_brick_wall" };
        for (int index = 0; index < brickStonecutOutputs.length; index++) {
            int output = brickStonecutOutputs[index];
            registerStonecut(brickStonecutNames[index] + "_from_sulfur_bricks_stonecutting",
                    (short) output, output == Blocks.SULFUR_BRICK_SLAB ? 2 : 1,
                    (short) Blocks.SULFUR_BRICKS);
        }
        registerQuarry("polished_cinnabar", (short) Blocks.CINNABAR,
                (short) Blocks.POLISHED_CINNABAR);
        registerDeepslateShapes("cinnabar", (short) Blocks.CINNABAR,
                (short) Blocks.CINNABAR_STAIRS, (short) Blocks.CINNABAR_SLAB,
                (short) Blocks.CINNABAR_WALL);
        registerDeepslateShapes("polished_cinnabar", (short) Blocks.POLISHED_CINNABAR,
                (short) Blocks.POLISHED_CINNABAR_STAIRS, (short) Blocks.POLISHED_CINNABAR_SLAB,
                (short) Blocks.POLISHED_CINNABAR_WALL);
        registerQuarry("cinnabar_bricks", (short) Blocks.POLISHED_CINNABAR,
                (short) Blocks.CINNABAR_BRICKS);
        registerDeepslateShapes("cinnabar_brick", (short) Blocks.CINNABAR_BRICKS,
                (short) Blocks.CINNABAR_BRICK_STAIRS, (short) Blocks.CINNABAR_BRICK_SLAB,
                (short) Blocks.CINNABAR_BRICK_WALL);
        registerChiseled("chiseled_cinnabar", (short) Blocks.CINNABAR_SLAB,
                (short) Blocks.CHISELED_CINNABAR);
        int[] cinnabarFromRaw = { Blocks.CHISELED_CINNABAR, Blocks.POLISHED_CINNABAR,
                Blocks.POLISHED_CINNABAR_SLAB, Blocks.POLISHED_CINNABAR_STAIRS,
                Blocks.POLISHED_CINNABAR_WALL, Blocks.CINNABAR_BRICKS,
                Blocks.CINNABAR_BRICK_SLAB, Blocks.CINNABAR_BRICK_STAIRS,
                Blocks.CINNABAR_BRICK_WALL, Blocks.CINNABAR_SLAB,
                Blocks.CINNABAR_STAIRS, Blocks.CINNABAR_WALL };
        String[] cinnabarRawNames = { "chiseled_cinnabar", "polished_cinnabar",
                "polished_cinnabar_slab", "polished_cinnabar_stairs", "polished_cinnabar_wall",
                "cinnabar_bricks", "cinnabar_brick_slab", "cinnabar_brick_stairs",
                "cinnabar_brick_wall", "cinnabar_slab", "cinnabar_stairs", "cinnabar_wall" };
        for (int index = 0; index < cinnabarFromRaw.length; index++) {
            int output = cinnabarFromRaw[index];
            registerStonecut(cinnabarRawNames[index] + "_from_cinnabar_stonecutting",
                    (short) output, output == Blocks.POLISHED_CINNABAR_SLAB
                            || output == Blocks.CINNABAR_BRICK_SLAB
                            || output == Blocks.CINNABAR_SLAB ? 2 : 1,
                    (short) Blocks.CINNABAR);
        }
        int[] cinnabarFromPolished = { Blocks.POLISHED_CINNABAR_SLAB,
                Blocks.POLISHED_CINNABAR_STAIRS, Blocks.POLISHED_CINNABAR_WALL,
                Blocks.CINNABAR_BRICKS, Blocks.CINNABAR_BRICK_SLAB,
                Blocks.CINNABAR_BRICK_STAIRS, Blocks.CINNABAR_BRICK_WALL };
        String[] cinnabarPolishedNames = { "polished_cinnabar_slab", "polished_cinnabar_stairs",
                "polished_cinnabar_wall", "cinnabar_bricks", "cinnabar_brick_slab",
                "cinnabar_brick_stairs", "cinnabar_brick_wall" };
        for (int index = 0; index < cinnabarFromPolished.length; index++) {
            int output = cinnabarFromPolished[index];
            registerStonecut(cinnabarPolishedNames[index] + "_from_polished_cinnabar_stonecutting",
                    (short) output, output == Blocks.POLISHED_CINNABAR_SLAB
                            || output == Blocks.CINNABAR_BRICK_SLAB ? 2 : 1,
                    (short) Blocks.POLISHED_CINNABAR);
        }
        int[] cinnabarFromBricks = { Blocks.CINNABAR_BRICK_SLAB,
                Blocks.CINNABAR_BRICK_STAIRS, Blocks.CINNABAR_BRICK_WALL };
        String[] cinnabarBrickNames = { "cinnabar_brick_slab", "cinnabar_brick_stairs",
                "cinnabar_brick_wall" };
        for (int index = 0; index < cinnabarFromBricks.length; index++) {
            registerStonecut(cinnabarBrickNames[index] + "_from_cinnabar_bricks_stonecutting",
                    (short) cinnabarFromBricks[index],
                    cinnabarFromBricks[index] == Blocks.CINNABAR_BRICK_SLAB ? 2 : 1,
                    (short) Blocks.CINNABAR_BRICKS);
        }
        // [PRISMARINE] 젖은 스펀지 건조. 바닐라 sponge.json(smelting) 그대로 젖은 스펀지 1 →
        // 마른 스펀지 1 이다. 바닐라는 화로 아래 칸에 빈 양동이가 있으면 물 양동이를 함께
        // 돌려주지만 이 저장소의 제련 계약은 산출 1종뿐이라 그 부산물은 없다(divergence).
        registerSmelt("sponge", (short) Blocks.SPONGE, 1,
                new Ingredient((short) Blocks.WET_SPONGE, 1));
        // [QUARTZ] 매끄러운 석영은 제련으로만 나온다(바닐라 smooth_quartz.json smelting).
        // 매끄러운 사암과 달리 석영은 제련해도 물성이 원석과 같은 0.8 / 0.8 이다.
        registerSmelt("smooth_quartz", (short) Blocks.SMOOTH_QUARTZ, 1,
                new Ingredient((short) Blocks.QUARTZ_BLOCK, 1));
        registerSmelt("charcoal_oak", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.LOG, 1));
        registerSmelt("charcoal_birch", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.BIRCH_LOG, 1));
        registerSmelt("charcoal_spruce", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.SPRUCE_LOG, 1));
        registerSmelt("charcoal_jungle", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.JUNGLE_LOG, 1));
        registerSmelt("charcoal_acacia", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.ACACIA_LOG, 1));
        registerSmelt("charcoal_dark_oak", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.DARK_OAK_LOG, 1));
        registerSmelt("charcoal_cherry", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.CHERRY_LOG, 1));
        registerSmelt("charcoal_mangrove", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.MANGROVE_LOG, 1));
        registerSmelt("charcoal_pale_oak", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.PALE_OAK_LOG, 1));
        registerSmelt("charcoal_stripped_pale_oak", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.STRIPPED_PALE_OAK_LOG, 1));
        registerSmelt("charcoal_poplar", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.POPLAR_LOG, 1));
        registerSmelt("charcoal_stripped_poplar", PlayerInventory.CHARCOAL, 1,
                new Ingredient((short) Blocks.STRIPPED_POPLAR_LOG, 1));
        // [STRIPPED-LOG] 벗긴 원목도 #logs_that_burn 이라 숯이 된다. FurnaceRules 에는
        // 있었는데 이 카탈로그(정적판 정본)에 빠져 정적판 화로만 제련하지 못했다 —
        // FurnaceCraftSmeltParityTest 가 두 표의 전체 일치를 고정한다.
        for (int species = 0; species < Blocks.LOG_BY_WOOD_SPECIES.length; species++) {
            registerSmelt("charcoal_stripped_" + species, PlayerInventory.CHARCOAL, 1,
                    new Ingredient((short) Blocks.strippedLog(species, 0), 1));
        }

        // [PROP-MATERIAL] 금속 조각 회수 제련 — 바닐라 iron_nugget_from_smelting.json ·
        // gold_nugget_from_smelting.json 그대로다. 입력이 태그(#iron_tool_materials 상당의
        // 아이템 목록)라 여기서는 그 목록을 그대로 편다. 산출은 손상도·마법부여와 무관하게
        // 언제나 조각 1 이고 경험치는 0.1 이다(경험치 표는 XpRules 가 소유한다).
        // 사슬 갑옷은 제작법이 없는 전리품 전용이라 이 제련이 유일한 재활용 경로다.
        for (short iron : new short[] {
                PlayerInventory.IRON_PICKAXE, PlayerInventory.IRON_AXE,
                PlayerInventory.IRON_SHOVEL, PlayerInventory.IRON_SWORD,
                // [SPEAR] 철 창도 바닐라 iron_nugget 제련 태그에 든다(철 도구·무기 전부).
                PlayerInventory.IRON_SPEAR,
                PlayerInventory.IRON_HELMET, PlayerInventory.IRON_CHESTPLATE,
                PlayerInventory.IRON_LEGGINGS, PlayerInventory.IRON_BOOTS,
                PlayerInventory.CHAINMAIL_HELMET, PlayerInventory.CHAINMAIL_CHESTPLATE,
                PlayerInventory.CHAINMAIL_LEGGINGS, PlayerInventory.CHAINMAIL_BOOTS }) {
            registerSmelt("iron_nugget_from_" + Short.toUnsignedInt(iron),
                    PlayerInventory.IRON_NUGGET, 1, new Ingredient(iron, 1));
        }
        for (short gold : new short[] {
                PlayerInventory.GOLD_PICKAXE, PlayerInventory.GOLD_AXE,
                PlayerInventory.GOLD_SHOVEL, PlayerInventory.GOLD_SWORD,
                // [SPEAR] 금 창도 바닐라 gold_nugget 제련 태그에 든다.
                PlayerInventory.GOLD_SPEAR,
                PlayerInventory.GOLD_HELMET, PlayerInventory.GOLD_CHESTPLATE,
                PlayerInventory.GOLD_LEGGINGS, PlayerInventory.GOLD_BOOTS }) {
            registerSmelt("gold_nugget_from_" + Short.toUnsignedInt(gold),
                    PlayerInventory.GOLD_NUGGET, 1, new Ingredient(gold, 1));
        }
        // 1.21.9 copper_nugget_from_smelting.json. 말 갑옷도 제작식은 없지만 같은 회수
        // 입력 목록에는 포함된다; 구리 창·방패는 공식 입력 목록 밖인 프로젝트 아이템이다.
        for (short copper : new short[] {
                PlayerInventory.COPPER_PICKAXE, PlayerInventory.COPPER_SHOVEL,
                PlayerInventory.COPPER_AXE, PlayerInventory.COPPER_HOE,
                PlayerInventory.COPPER_SWORD, PlayerInventory.COPPER_HELMET,
                PlayerInventory.COPPER_CHESTPLATE, PlayerInventory.COPPER_LEGGINGS,
                PlayerInventory.COPPER_BOOTS, PlayerInventory.COPPER_HORSE_ARMOR }) {
            registerSmelt("copper_nugget_from_" + Short.toUnsignedInt(copper),
                    PlayerInventory.COPPER_NUGGET, 1, new Ingredient(copper, 1));
        }

        // 돌 티어 도구(내구 120·§4): 조약돌 8 + 막대. 곡괭이/도끼=3+2, 삽=1+2, 검=2+1.
        registerShaped("stone_pickaxe", PlayerInventory.STONE_PICKAXE, 1,
                shape(Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE, 0, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient((short) Blocks.COBBLE, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("stone_axe", PlayerInventory.STONE_AXE, 1,
                shape(Blocks.COBBLE, Blocks.COBBLE, 0, Blocks.COBBLE, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient((short) Blocks.COBBLE, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("stone_shovel", PlayerInventory.STONE_SHOVEL, 1,
                shape(Blocks.COBBLE, 0, 0, PlayerInventory.STICK, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient((short) Blocks.COBBLE, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("stone_sword", PlayerInventory.STONE_SWORD, 1,
                shape(Blocks.COBBLE, 0, 0, Blocks.COBBLE, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient((short) Blocks.COBBLE, 2), new Ingredient(PlayerInventory.STICK, 1));

        // 철 티어 도구(내구 240·§4): 철 주괴 + 막대. 동형 배합.
        registerShaped("iron_pickaxe", PlayerInventory.IRON_PICKAXE, 1,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT,
                        0, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("iron_axe", PlayerInventory.IRON_AXE, 1,
                shape(PlayerInventory.IRON_INGOT, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("iron_shovel", PlayerInventory.IRON_SHOVEL, 1,
                shape(PlayerInventory.IRON_INGOT, 0, 0, PlayerInventory.STICK, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("iron_sword", PlayerInventory.IRON_SWORD, 1,
                shape(PlayerInventory.IRON_INGOT, 0, 0, PlayerInventory.IRON_INGOT, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 2), new Ingredient(PlayerInventory.STICK, 1));

        // 1.21.9 구리 도구 다섯 종. #copper_tool_materials 는 구리 주괴 한 종류뿐이다.
        registerShaped("copper_pickaxe", PlayerInventory.COPPER_PICKAXE, 1,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT,
                        PlayerInventory.COPPER_INGOT,
                        0, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 3),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("copper_axe", PlayerInventory.COPPER_AXE, 1,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0,
                        PlayerInventory.COPPER_INGOT, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 3),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("copper_shovel", PlayerInventory.COPPER_SHOVEL, 1,
                shape(PlayerInventory.COPPER_INGOT, 0, 0,
                        PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 1),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("copper_sword", PlayerInventory.COPPER_SWORD, 1,
                shape(PlayerInventory.COPPER_INGOT, 0, 0,
                        PlayerInventory.COPPER_INGOT, 0, 0, PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 2),
                new Ingredient(PlayerInventory.STICK, 1));
        registerShaped("copper_hoe", PlayerInventory.COPPER_HOE, 1,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT, 0,
                        0, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 2),
                new Ingredient(PlayerInventory.STICK, 2));

        // 철 방어구: 투구 5·흉갑 8·레깅스 7·부츠 4.
        registerArmorSet("iron", PlayerInventory.IRON_INGOT,
                PlayerInventory.IRON_HELMET, PlayerInventory.IRON_CHESTPLATE,
                PlayerInventory.IRON_LEGGINGS, PlayerInventory.IRON_BOOTS);
        registerArmorSet("copper", PlayerInventory.COPPER_INGOT,
                PlayerInventory.COPPER_HELMET, PlayerInventory.COPPER_CHESTPLATE,
                PlayerInventory.COPPER_LEGGINGS, PlayerInventory.COPPER_BOOTS);

        // 가죽 방어구: MC의 3×3 배치별 재료 수(모자5·상의8·바지7·신발4).
        registerArmorSet("leather", PlayerInventory.LEATHER,
                PlayerInventory.LEATHER_HELMET, PlayerInventory.LEATHER_CHESTPLATE,
                PlayerInventory.LEATHER_LEGGINGS, PlayerInventory.LEATHER_BOOTS);

        // [ROTTEN-LEATHER] 썩은 가죽 방어구: 배치는 바로 위 가죽 방어구와 **글자 그대로 같고**
        // 재료만 썩은 가죽이다. 같은 registerArmorSet 를 다시 부르는 것이 그 사실의 근거다.
        registerArmorSet("rotten_leather", PlayerInventory.ROTTEN_LEATHER,
                PlayerInventory.ROTTEN_LEATHER_HELMET, PlayerInventory.ROTTEN_LEATHER_CHESTPLATE,
                PlayerInventory.ROTTEN_LEATHER_LEGGINGS, PlayerInventory.ROTTEN_LEATHER_BOOTS);

        // 금·다이아 도구: 각 재료 + 막대, 기존 네 티어와 같은 배합.
        registerShaped("gold_pickaxe", PlayerInventory.GOLD_PICKAXE, 1,
                shape(PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT,
                        0, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("gold_axe", PlayerInventory.GOLD_AXE, 1,
                shape(PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT, 0,
                        PlayerInventory.GOLD_INGOT, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("gold_shovel", PlayerInventory.GOLD_SHOVEL, 1,
                shape(PlayerInventory.GOLD_INGOT, 0, 0, PlayerInventory.STICK, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("gold_sword", PlayerInventory.GOLD_SWORD, 1,
                shape(PlayerInventory.GOLD_INGOT, 0, 0, PlayerInventory.GOLD_INGOT, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 2), new Ingredient(PlayerInventory.STICK, 1));
        registerShaped("diamond_pickaxe", PlayerInventory.DIAMOND_PICKAXE, 1,
                shape(PlayerInventory.DIAMOND, PlayerInventory.DIAMOND, PlayerInventory.DIAMOND,
                        0, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.DIAMOND, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("diamond_axe", PlayerInventory.DIAMOND_AXE, 1,
                shape(PlayerInventory.DIAMOND, PlayerInventory.DIAMOND, 0,
                        PlayerInventory.DIAMOND, PlayerInventory.STICK, 0, 0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.DIAMOND, 3), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("diamond_shovel", PlayerInventory.DIAMOND_SHOVEL, 1,
                shape(PlayerInventory.DIAMOND, 0, 0, PlayerInventory.STICK, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.DIAMOND, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("diamond_sword", PlayerInventory.DIAMOND_SWORD, 1,
                shape(PlayerInventory.DIAMOND, 0, 0, PlayerInventory.DIAMOND, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.DIAMOND, 2), new Ingredient(PlayerInventory.STICK, 1));

        // ── [SPEAR] 창 여섯 티어: 재료 1 + 막대 2 의 **대각선** 배치(핀 §2) ────────────
        //
        // 검(재료 2 + 막대 1, **수직**)과 형상이 다르다는 것이 창 레시피의 핵심이고, 그래서
        // 격자 무형 매칭이 검과 창을 섞지 않는다. 핀 §2 의 배치 그림 그대로다:
        //
        //     . . 재료
        //     . 막대 .
        //     막대 . .
        //
        // 즉 shape(0, 0, 재료, 0, 막대, 0, 막대, 0, 0) 이다.
        //
        // **재료를 하나도 신설하지 않았다**(핀 §2 재료 무결성 확인). 나무·돌 티어는 기존
        // 도구와 **같은 구체 블록**을 읽는다 — 나무 검이 {@code Blocks.PLANK}, 돌 검이
        // {@code Blocks.COBBLE} 인 그 관례를 그대로 따른다(핀은 "판자 아무 종류"·"아무 돌
        // 티어 블록" 이라 적었지만, 그 폭은 이 저장소에서 레시피 매칭 축이 따로 소유하는
        // 문제라 이 트랙이 새로 만들지 않는다). 구리 주괴 393 은 이미 있어 구리 창도
        // 재료를 신설하지 않는다.
        //
        // **네더라이트 창은 등록하지 않는다**(핀 §6) — 바닐라에서도 제작이 아니라 대장장이
        // 틀 업그레이드이고, 이 저장소에는 그 사슬(고대 잔해·조각·주괴·틀)이 통째로 없다.
        registerShaped("wooden_spear", PlayerInventory.WOODEN_SPEAR, 1,
                shape(0, 0, Blocks.PLANK, 0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient((short) Blocks.PLANK, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("stone_spear", PlayerInventory.STONE_SPEAR, 1,
                shape(0, 0, Blocks.COBBLE, 0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient((short) Blocks.COBBLE, 1), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("copper_spear", PlayerInventory.COPPER_SPEAR, 1,
                shape(0, 0, PlayerInventory.COPPER_INGOT, 0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 1),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("iron_spear", PlayerInventory.IRON_SPEAR, 1,
                shape(0, 0, PlayerInventory.IRON_INGOT, 0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 1),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("gold_spear", PlayerInventory.GOLD_SPEAR, 1,
                shape(0, 0, PlayerInventory.GOLD_INGOT, 0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 1),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("diamond_spear", PlayerInventory.DIAMOND_SPEAR, 1,
                shape(0, 0, PlayerInventory.DIAMOND, 0, PlayerInventory.STICK, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.DIAMOND, 1),
                new Ingredient(PlayerInventory.STICK, 2));

        // 금·다이아 방어구: 투구 5·흉갑 8·레깅스 7·부츠 4.
        registerArmorSet("gold", PlayerInventory.GOLD_INGOT,
                PlayerInventory.GOLD_HELMET, PlayerInventory.GOLD_CHESTPLATE,
                PlayerInventory.GOLD_LEGGINGS, PlayerInventory.GOLD_BOOTS);
        registerArmorSet("diamond", PlayerInventory.DIAMOND,
                PlayerInventory.DIAMOND_HELMET, PlayerInventory.DIAMOND_CHESTPLATE,
                PlayerInventory.DIAMOND_LEGGINGS, PlayerInventory.DIAMOND_BOOTS);

        // 황금사과: 사과 1 + 금 주괴 8.
        registerShaped("golden_apple", PlayerInventory.GOLDEN_APPLE, 1,
                shape(PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT,
                        PlayerInventory.GOLD_INGOT, PlayerInventory.APPLE, PlayerInventory.GOLD_INGOT,
                        PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT),
                new Ingredient(PlayerInventory.APPLE, 1), new Ingredient(PlayerInventory.GOLD_INGOT, 8));

        // ── 생존 F2(§2·§10.2-F2): 광물 압축 블록 35~38 양방향 레시피(압축 4 + 해체 4) ──
        // 압축: 재료(광물) 9 → 저장 블록 1.
        registerFullBlock("coal_block", PlayerInventory.COAL, (short) Blocks.COAL_BLOCK);
        registerFullBlock("iron_block", PlayerInventory.IRON_INGOT, (short) Blocks.IRON_BLOCK);
        registerFullBlock("gold_block", PlayerInventory.GOLD_INGOT, (short) Blocks.GOLD_BLOCK);
        registerFullBlock("diamond_block", PlayerInventory.DIAMOND, (short) Blocks.DIAMOND_BLOCK);
        // 해체: 저장 블록 1 → 재료 9. 압축의 정확한 역변환(손실 없음).
        register("coal_block_undo", PlayerInventory.COAL, 9, new Ingredient((short) Blocks.COAL_BLOCK, 1));
        register("iron_block_undo", PlayerInventory.IRON_INGOT, 9, new Ingredient((short) Blocks.IRON_BLOCK, 1));
        register("gold_block_undo", PlayerInventory.GOLD_INGOT, 9, new Ingredient((short) Blocks.GOLD_BLOCK, 1));
        register("diamond_block_undo", PlayerInventory.DIAMOND, 9, new Ingredient((short) Blocks.DIAMOND_BLOCK, 1));
        // [TRIAL-GAP] recipe/emerald_block.json(에메랄드 9 → 블록, 3×3) · recipe/emerald.json(블록 → 9).
        registerFullBlock("emerald_block", PlayerInventory.EMERALD, (short) Blocks.EMERALD_BLOCK);
        register("emerald_block_undo", PlayerInventory.EMERALD, 9,
                new Ingredient((short) Blocks.EMERALD_BLOCK, 1));
        // [UTILITY] 핀 26.3 recipe/beacon.json("GGG","GSG","OOO"; G 유리 · S 네더의 별 · O 흑요석),
        // recipe/jukebox.json("###","#X#","###"; # #planks · X 다이아몬드), recipe/slime_block.json ·
        // slime_ball.json(9 ↔ 1), recipe/music_disc_5.json(음반 조각 5 아홉, 무형),
        // recipe/glistering_melon_slice.json("###","#X#","###"; # 금 조각 · X 수박 조각),
        // recipe/melon.json(수박 조각 9 → 수박), recipe/melon_seeds.json(수박 조각 1 → 씨앗 1).
        registerShaped("beacon", (short) Blocks.BEACON, 1,
                shape(Blocks.GLASS, Blocks.GLASS, Blocks.GLASS,
                        Blocks.GLASS, Blocks.NETHER_STAR, Blocks.GLASS,
                        Blocks.OBSIDIAN, Blocks.OBSIDIAN, Blocks.OBSIDIAN),
                new Ingredient((short) Blocks.GLASS, 5),
                new Ingredient(PlayerInventory.NETHER_STAR, 1),
                new Ingredient((short) Blocks.OBSIDIAN, 3));
        // [UTILITY] recipe/polished_blackstone.json("SS","SS"; S 흑암석 → 4) ·
        // recipe/polished_blackstone_bricks.json("##","##"; # 다듬은 흑암석 → 4). 석공 작업대 레시피는
        // 이 저장소에 석공 작업대 제작 경로가 없어 다른 석재와 같이 등록하지 않는다.
        registerQuarry("polished_blackstone", PlayerInventory.BLACKSTONE, PlayerInventory.POLISHED_BLACKSTONE);
        registerQuarry("polished_blackstone_bricks", PlayerInventory.POLISHED_BLACKSTONE,
                PlayerInventory.POLISHED_BLACKSTONE_BRICKS);
        registerShaped("jukebox", (short) Blocks.JUKEBOX, 1,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK,
                        Blocks.PLANK, PlayerInventory.DIAMOND, Blocks.PLANK,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK),
                new Ingredient((short) Blocks.PLANK, 8),
                new Ingredient(PlayerInventory.DIAMOND, 1));
        registerFullBlock("slime_block", PlayerInventory.SLIME_BALL, (short) Blocks.SLIME_BLOCK);
        register("slime_block_undo", PlayerInventory.SLIME_BALL, 9,
                new Ingredient((short) Blocks.SLIME_BLOCK, 1));
        register("music_disc_5", PlayerInventory.MUSIC_DISC_5, 1,
                new Ingredient(PlayerInventory.DISC_FRAGMENT_5, 9));
        registerShaped("glistering_melon_slice", PlayerInventory.GLISTERING_MELON_SLICE, 1,
                shape(PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.MELON_SLICE, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET),
                new Ingredient(PlayerInventory.GOLD_NUGGET, 8),
                new Ingredient(PlayerInventory.MELON_SLICE, 1));
        registerFullBlock("melon", PlayerInventory.MELON_SLICE, (short) Blocks.MELON);
        register("melon_seeds", PlayerInventory.MELON_SEEDS, 1,
                new Ingredient(PlayerInventory.MELON_SLICE, 1));

        // 1.21.4 자연·지질 확장: 원석/구리 저장 블록과 점적석·눈의 바닐라 무손실 제작.
        registerFullBlock("raw_iron_block", PlayerInventory.RAW_IRON, (short) Blocks.RAW_IRON_BLOCK);
        registerFullBlock("raw_copper_block", PlayerInventory.RAW_COPPER, (short) Blocks.RAW_COPPER_BLOCK);
        registerFullBlock("raw_gold_block", PlayerInventory.RAW_GOLD, (short) Blocks.RAW_GOLD_BLOCK);
        register("raw_iron_block_undo", PlayerInventory.RAW_IRON, 9,
                new Ingredient((short) Blocks.RAW_IRON_BLOCK, 1));
        register("raw_copper_block_undo", PlayerInventory.RAW_COPPER, 9,
                new Ingredient((short) Blocks.RAW_COPPER_BLOCK, 1));
        register("raw_gold_block_undo", PlayerInventory.RAW_GOLD, 9,
                new Ingredient((short) Blocks.RAW_GOLD_BLOCK, 1));
        registerFullBlock("copper_block", PlayerInventory.COPPER_INGOT, (short) Blocks.COPPER_BLOCK);
        register("copper_block_undo", PlayerInventory.COPPER_INGOT, 9,
                new Ingredient((short) Blocks.COPPER_BLOCK, 1));
        registerShaped("dripstone_block", (short) Blocks.DRIPSTONE_BLOCK, 1,
                shape(Blocks.POINTED_DRIPSTONE, Blocks.POINTED_DRIPSTONE, 0,
                        Blocks.POINTED_DRIPSTONE, Blocks.POINTED_DRIPSTONE, 0, 0, 0, 0),
                new Ingredient((short) Blocks.POINTED_DRIPSTONE, 4));
        registerShaped("snow_block", (short) Blocks.SNOW_BLOCK, 1,
                shape(PlayerInventory.SNOWBALL, PlayerInventory.SNOWBALL, 0,
                        PlayerInventory.SNOWBALL, PlayerInventory.SNOWBALL, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.SNOWBALL, 4));
        // 26.3 snapshot-7 data/minecraft/recipe/wind_charge.json: one breeze rod -> four charges.
        register("wind_charge", PlayerInventory.WIND_CHARGE, 4,
                new Ingredient(PlayerInventory.BREEZE_ROD, 1));
        // [MACE] 26.3 snapshot-7 data/minecraft/recipe/mace.json(crafting_shaped, category equipment):
        // 패턴 " # "/" I " — 무거운 핵(#) 아래 브리즈 막대(I). 빈 열을 잘라 1×2 로 줄어든다.
        registerShaped("mace", PlayerInventory.MACE, 1,
                shape(Blocks.HEAVY_CORE, 0, 0, PlayerInventory.BREEZE_ROD, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.HEAVY_CORE, 1),
                new Ingredient(PlayerInventory.BREEZE_ROD, 1));
        registerShaped("amethyst_block", (short) Blocks.AMETHYST_BLOCK, 1,
                shape(PlayerInventory.AMETHYST_SHARD, PlayerInventory.AMETHYST_SHARD, 0,
                        PlayerInventory.AMETHYST_SHARD, PlayerInventory.AMETHYST_SHARD, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.AMETHYST_SHARD, 4));

        // 보트(§2): 판자 7 ×5 → 보트 ×1. 블록이 아니라 엔티티를 스폰하는 배치형 아이템(스택 1).
        registerShaped("boat", PlayerInventory.BOAT, 1,
                shape(Blocks.PLANK, 0, Blocks.PLANK,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK, 0, 0, 0),
                new Ingredient((short) Blocks.PLANK, 5));
        // 뼈 하나는 인벤토리 2x2에서 뼛가루 세 개로 분해한다.
        register("bone_meal", PlayerInventory.BONE_MEAL, 3, new Ingredient(PlayerInventory.BONE, 1));
        // 라이터는 철 주괴 1 + 부싯돌 1의 무형(인벤토리 2x2 가능) 레시피다.
        register("flint_and_steel", PlayerInventory.FLINT_AND_STEEL, 1,
                new Ingredient(PlayerInventory.IRON_INGOT, 1), new Ingredient(PlayerInventory.FLINT, 1));
        // 방패: 판자 6 + 철 주괴 1의 3×3 Y자 배치이므로 작업대가 필요하다.
        registerShaped("shield", PlayerInventory.SHIELD, 1,
                shape(Blocks.PLANK, PlayerInventory.IRON_INGOT, Blocks.PLANK,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK,
                        0, Blocks.PLANK, 0),
                new Ingredient((short) Blocks.PLANK, 6), new Ingredient(PlayerInventory.IRON_INGOT, 1));
        // [DIAMOND-SHIELD] 다이아 방패는 **완성된 방패 + 다이아몬드** 승급 조합이다. 바닐라
        // 방패 레시피는 한 글자도 건드리지 않고(원본 레시피 무결성), 재료 대체가 아니라 위에
        // 얹는 두 번째 레시피로만 붙는다. 재료가 두 칸이라 2×2 인벤토리 격자에서도 성립한다.
        // 마모된 방패로 승급해도 산출은 initialDurability 로 만충이다([C] — 승급이 곧 수리다).
        register("diamond_shield", PlayerInventory.DIAMOND_SHIELD, 1,
                new Ingredient(PlayerInventory.SHIELD, 1), new Ingredient(PlayerInventory.DIAMOND, 1));
        // [SHIELD-FAMILY][C] 가죽·돌·구리·금 방패는 바닐라 방패의 **Y자 배치를 그대로 준용**
        // 하고 판자 자리에만 그 티어의 몸통 재료를 놓는다(재료 6 + 철 주괴 1). 바닐라 방패
        // 레시피는 한 글자도 건드리지 않고(원본 레시피 무결성), 재료 대체가 아니라 옆에 서는
        // 별개 레시피로만 붙는다 — 다이아 방패가 낸 선례와 같은 규율이다.
        //
        // **다이아처럼 승급(방패+재료) 으로 만들지 않는 이유**: 승급이 성립하려면 산출이 재료
        // 방패보다 나아야 하는데, 이 네 티어는 전부 철 방패(내구 336)보다 **낮다**(가죽 80 ·
        // 돌 120 · 구리 190 · 금 30). 철 방패를 넣어 더 나쁜 방패를 받는 조합은 아무도 쓰지
        // 않는 죽은 레시피이고, 무엇보다 철 주괴를 되돌려 받을 수 없어 순손실이다. 그래서
        // 하위 티어는 승급이 아니라 **자기 재료로 직접 만든다** — 다이아(1560 > 336)만이 승급
        // 방향이 맞는 유일한 티어라 그 레시피만 위의 승급 형태로 남는다.
        //
        // 철 주괴 1 은 바닐라 방패 형식의 일부라 그대로 둔다(가죽 방패도 철 주괴를 요구하므로
        // 철 이전 단계의 대체재는 아니다 — 이 네 티어는 값싼 조기 방패가 아니라 같은 성능의
        // 재료 변주다. 막기 성능은 넷 다 철 방패와 완전히 같고 차별점은 내구뿐이다).
        registerShaped("leather_shield", PlayerInventory.LEATHER_SHIELD, 1,
                shape(PlayerInventory.LEATHER, PlayerInventory.IRON_INGOT, PlayerInventory.LEATHER,
                        PlayerInventory.LEATHER, PlayerInventory.LEATHER, PlayerInventory.LEATHER,
                        0, PlayerInventory.LEATHER, 0),
                new Ingredient(PlayerInventory.LEATHER, 6),
                new Ingredient(PlayerInventory.IRON_INGOT, 1));
        registerShaped("stone_shield", PlayerInventory.STONE_SHIELD, 1,
                shape(Blocks.COBBLE, PlayerInventory.IRON_INGOT, Blocks.COBBLE,
                        Blocks.COBBLE, Blocks.COBBLE, Blocks.COBBLE,
                        0, Blocks.COBBLE, 0),
                new Ingredient((short) Blocks.COBBLE, 6),
                new Ingredient(PlayerInventory.IRON_INGOT, 1));
        registerShaped("copper_shield", PlayerInventory.COPPER_SHIELD, 1,
                shape(PlayerInventory.COPPER_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.COPPER_INGOT,
                        PlayerInventory.COPPER_INGOT, PlayerInventory.COPPER_INGOT,
                        PlayerInventory.COPPER_INGOT,
                        0, PlayerInventory.COPPER_INGOT, 0),
                new Ingredient(PlayerInventory.COPPER_INGOT, 6),
                new Ingredient(PlayerInventory.IRON_INGOT, 1));
        registerShaped("gold_shield", PlayerInventory.GOLD_SHIELD, 1,
                shape(PlayerInventory.GOLD_INGOT, PlayerInventory.IRON_INGOT,
                        PlayerInventory.GOLD_INGOT,
                        PlayerInventory.GOLD_INGOT, PlayerInventory.GOLD_INGOT,
                        PlayerInventory.GOLD_INGOT,
                        0, PlayerInventory.GOLD_INGOT, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 6),
                new Ingredient(PlayerInventory.IRON_INGOT, 1));
        // 농사: 판자 2 + 막대 2 → 나무 괭이, 밀 3 가로줄 → 빵 1.
        registerShaped("wooden_hoe", PlayerInventory.WOODEN_HOE, 1,
                shape(Blocks.PLANK, Blocks.PLANK, 0,
                        0, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient((short) Blocks.PLANK, 2), new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("diamond_hoe", PlayerInventory.DIAMOND_HOE, 1,
                shape(PlayerInventory.DIAMOND, PlayerInventory.DIAMOND, 0,
                        0, PlayerInventory.STICK, 0,
                        0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.DIAMOND, 2),
                new Ingredient(PlayerInventory.STICK, 2));
        registerShaped("bread", PlayerInventory.BREAD, 1,
                shape(PlayerInventory.WHEAT, PlayerInventory.WHEAT, PlayerInventory.WHEAT,
                        0, 0, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.WHEAT, 3));
        // 블록 팩: 바닐라 압축/해체와 푸른 얼음 압축 레시피.
        register("hay_block", (short) Blocks.HAY_BLOCK, 1,
                new Ingredient(PlayerInventory.WHEAT, 9));
        register("hay_block_undo", PlayerInventory.WHEAT, 9,
                new Ingredient((short) Blocks.HAY_BLOCK, 1));
        registerFullBlock("bone_block", PlayerInventory.BONE_MEAL, (short) Blocks.BONE_BLOCK);
        register("bone_block_undo", PlayerInventory.BONE_MEAL, 9,
                new Ingredient((short) Blocks.BONE_BLOCK, 1));
        register("blue_ice", (short) Blocks.BLUE_ICE, 1,
                new Ingredient((short) Blocks.PACKED_ICE, 9));
        register("netherite_ingot", PlayerInventory.NETHERITE_INGOT, 1,
                new Ingredient(PlayerInventory.NETHERITE_SCRAP, 4),
                new Ingredient(PlayerInventory.GOLD_INGOT, 4));
        registerShaped("netherite_block", (short) Blocks.NETHERITE_BLOCK, 1,
                shape(PlayerInventory.NETHERITE_INGOT, PlayerInventory.NETHERITE_INGOT,
                        PlayerInventory.NETHERITE_INGOT,
                        PlayerInventory.NETHERITE_INGOT, PlayerInventory.NETHERITE_INGOT,
                        PlayerInventory.NETHERITE_INGOT,
                        PlayerInventory.NETHERITE_INGOT, PlayerInventory.NETHERITE_INGOT,
                        PlayerInventory.NETHERITE_INGOT),
                new Ingredient(PlayerInventory.NETHERITE_INGOT, 9));
        register("netherite_block_undo", PlayerInventory.NETHERITE_INGOT, 9,
                new Ingredient((short) Blocks.NETHERITE_BLOCK, 1));
        registerShaped("netherite_upgrade_smithing_template_duplication",
                PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2,
                shape(PlayerInventory.DIAMOND,
                        PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                        PlayerInventory.DIAMOND,
                        PlayerInventory.DIAMOND, Blocks.NETHERRACK, PlayerInventory.DIAMOND,
                        PlayerInventory.DIAMOND, PlayerInventory.DIAMOND,
                        PlayerInventory.DIAMOND),
                new Ingredient(PlayerInventory.DIAMOND, 7),
                new Ingredient(PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1),
                new Ingredient((short) Blocks.NETHERRACK, 1));
        // [ARMOR-TRIM] 바닐라 26.3 recipe/<x>_armor_trim_smithing_template.json: 모두
        // "#S#","#C#","###"(# = 다이아몬드 7, S = 형판, C = 형판별 재료 블록) → 형판 2개.
        // 18종(snout 의 재료 minecraft:blackstone 은 [UTILITY] 흑암석 2417 로 등록됐다).
        registerTrimTemplateDuplication("bolt", PlayerInventory.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.COPPER_BLOCK);
        // bolt 의 C 는 [copper_block, waxed_copper_block] 두 대안이라 대안마다 한 레시피다.
        registerTrimTemplateDuplication("bolt_from_waxed_copper_block",
                PlayerInventory.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.WAXED_COPPER_BLOCK);
        registerTrimTemplateDuplication("coast", PlayerInventory.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.COBBLE);
        registerTrimTemplateDuplication("dune", PlayerInventory.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.SANDSTONE);
        registerTrimTemplateDuplication("eye", PlayerInventory.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.END_STONE);
        registerTrimTemplateDuplication("flow", PlayerInventory.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
                PlayerInventory.BREEZE_ROD);
        registerTrimTemplateDuplication("host", PlayerInventory.HOST_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.TERRACOTTA);
        registerTrimTemplateDuplication("raiser", PlayerInventory.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.TERRACOTTA);
        registerTrimTemplateDuplication("rib", PlayerInventory.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.NETHERRACK);
        registerTrimTemplateDuplication("sentry", PlayerInventory.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.COBBLE);
        registerTrimTemplateDuplication("shaper", PlayerInventory.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.TERRACOTTA);
        registerTrimTemplateDuplication("silence", PlayerInventory.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.COBBLED_DEEPSLATE);
        registerTrimTemplateDuplication("snout", PlayerInventory.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
                PlayerInventory.BLACKSTONE);
        registerTrimTemplateDuplication("spire", PlayerInventory.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.PURPUR_BLOCK);
        registerTrimTemplateDuplication("tide", PlayerInventory.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.PRISMARINE);
        registerTrimTemplateDuplication("vex", PlayerInventory.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.COBBLE);
        registerTrimTemplateDuplication("ward", PlayerInventory.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.COBBLED_DEEPSLATE);
        registerTrimTemplateDuplication("wayfinder",
                PlayerInventory.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, (short) Blocks.TERRACOTTA);
        registerTrimTemplateDuplication("wild", PlayerInventory.WILD_ARMOR_TRIM_SMITHING_TEMPLATE,
                (short) Blocks.MOSSY_COBBLE);
        // 원거리 전투: 부싯돌+막대+깃털 → 화살4, 막대3+실3 → 활1.
        registerShaped("arrow", PlayerInventory.ARROW, 4,
                shape(PlayerInventory.FLINT, 0, 0,
                        PlayerInventory.STICK, 0, 0,
                        PlayerInventory.FEATHER, 0, 0),
                new Ingredient(PlayerInventory.FLINT, 1),
                new Ingredient(PlayerInventory.STICK, 1),
                new Ingredient(PlayerInventory.FEATHER, 1));
        // [GLOWING] 바닐라 recipes/spectral_arrow.json: 발광석 가루 4 가 화살을 둘러싸 분광 화살 2.
        registerShaped("spectral_arrow", PlayerInventory.SPECTRAL_ARROW, 2,
                shape(0, PlayerInventory.GLOWSTONE_DUST, 0,
                        PlayerInventory.GLOWSTONE_DUST, PlayerInventory.ARROW, PlayerInventory.GLOWSTONE_DUST,
                        0, PlayerInventory.GLOWSTONE_DUST, 0),
                new Ingredient(PlayerInventory.GLOWSTONE_DUST, 4),
                new Ingredient(PlayerInventory.ARROW, 1));
        registerShaped("bow", PlayerInventory.BOW, 1,
                shape(0, PlayerInventory.STICK, PlayerInventory.STRING,
                        PlayerInventory.STICK, 0, PlayerInventory.STRING,
                        0, PlayerInventory.STICK, PlayerInventory.STRING),
                new Ingredient(PlayerInventory.STICK, 3),
                new Ingredient(PlayerInventory.STRING, 3));
        // 석궁(바닐라 crossbow): 막대3·실2·철 주괴1·철사 덫 갈고리1.
        registerShaped("crossbow", PlayerInventory.CROSSBOW, 1,
                shape(PlayerInventory.STICK, PlayerInventory.IRON_INGOT, PlayerInventory.STICK,
                        PlayerInventory.STRING, PlayerInventory.TRIPWIRE_HOOK,
                        PlayerInventory.STRING,
                        0, PlayerInventory.STICK, 0),
                new Ingredient(PlayerInventory.STICK, 3),
                new Ingredient(PlayerInventory.STRING, 2),
                new Ingredient(PlayerInventory.IRON_INGOT, 1),
                new Ingredient(PlayerInventory.TRIPWIRE_HOOK, 1));
        // 낚시: 막대3을 대각선으로 세우고 실2를 낚싯줄처럼 늘어뜨린다(바닐라 배치).
        registerShaped("fishing_rod", PlayerInventory.FISHING_ROD, 1,
                shape(0, 0, PlayerInventory.STICK,
                        0, PlayerInventory.STICK, PlayerInventory.STRING,
                        PlayerInventory.STICK, 0, PlayerInventory.STRING),
                new Ingredient(PlayerInventory.STICK, 3),
                new Ingredient(PlayerInventory.STRING, 2));
        // 바닐라 carrot_on_a_stick: 손상되지 않은 낚싯대와 당근의 무형 조합.
        register("carrot_on_a_stick", PlayerInventory.CARROT_ON_A_STICK, 1,
                new Ingredient(PlayerInventory.FISHING_ROD, 1),
                new Ingredient(PlayerInventory.CARROT, 1));
        // 폭죽 로켓(바닐라 firework_rocket): 무형 종이 1 + 화약 1~3 → 로켓 3개.
        // 화약 개수가 곧 비행 지속 티어이고, 재료가 최대 4칸이라 인벤토리 2×2로도 만든다.
        register("firework_rocket_1", PlayerInventory.FIREWORK_ROCKET_1, 3,
                new Ingredient(PlayerInventory.PAPER, 1),
                new Ingredient(PlayerInventory.GUNPOWDER, 1));
        register("firework_rocket_2", PlayerInventory.FIREWORK_ROCKET_2, 3,
                new Ingredient(PlayerInventory.PAPER, 1),
                new Ingredient(PlayerInventory.GUNPOWDER, 2));
        register("firework_rocket_3", PlayerInventory.FIREWORK_ROCKET_3, 3,
                new Ingredient(PlayerInventory.PAPER, 1),
                new Ingredient(PlayerInventory.GUNPOWDER, 3));
        registerShaped("brush", PlayerInventory.BRUSH, 1,
                shape(PlayerInventory.FEATHER, 0, 0,
                        PlayerInventory.COPPER_INGOT, 0, 0,
                        PlayerInventory.STICK, 0, 0),
                new Ingredient(PlayerInventory.FEATHER, 1),
                new Ingredient(PlayerInventory.COPPER_INGOT, 1),
                new Ingredient(PlayerInventory.STICK, 1));
        registerShaped("wolf_armor", PlayerInventory.WOLF_ARMOR, 1,
                shape(PlayerInventory.ARMADILLO_SCUTE, 0, 0,
                        PlayerInventory.ARMADILLO_SCUTE, PlayerInventory.ARMADILLO_SCUTE,
                        PlayerInventory.ARMADILLO_SCUTE,
                        PlayerInventory.ARMADILLO_SCUTE, 0,
                        PlayerInventory.ARMADILLO_SCUTE),
                new Ingredient(PlayerInventory.ARMADILLO_SCUTE, 6));
        registerShaped("leather_from_rabbit_hide", PlayerInventory.LEATHER, 1,
                shape(PlayerInventory.RABBIT_HIDE, PlayerInventory.RABBIT_HIDE, 0,
                        PlayerInventory.RABBIT_HIDE, PlayerInventory.RABBIT_HIDE, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.RABBIT_HIDE, 4));
        registerShaped("golden_carrot", PlayerInventory.GOLDEN_CARROT, 1,
                shape(PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.CARROT, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET),
                new Ingredient(PlayerInventory.CARROT, 1),
                new Ingredient(PlayerInventory.GOLD_NUGGET, 8));
        // [GOLD-FOOD] 황금 민들레: 민들레 1 + 금 조각 8(바닐라 26.1 원본 그대로).
        // 이 저장소의 민들레는 노란 꽃(FLOWER_YELLOW=13)이라 재료 대체가 없다.
        // 마법이 부여된 황금 사과는 바닐라 1.9 에서 제작이 삭제돼 <b>여기에 줄이 없다</b> —
        // 없는 것이 곧 계약이므로 그 사실을 이 주석이 소유한다.
        registerShaped("golden_dandelion", PlayerInventory.GOLDEN_DANDELION, 1,
                shape(PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, (short) Blocks.FLOWER_YELLOW, PlayerInventory.GOLD_NUGGET,
                        PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET, PlayerInventory.GOLD_NUGGET),
                new Ingredient((short) Blocks.FLOWER_YELLOW, 1),
                new Ingredient(PlayerInventory.GOLD_NUGGET, 8));
        register("rabbit_stew_brown", PlayerInventory.RABBIT_STEW, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient(PlayerInventory.RABBIT_COOKED, 1),
                new Ingredient(PlayerInventory.CARROT, 1),
                new Ingredient(PlayerInventory.BAKED_POTATO, 1),
                new Ingredient((short) Blocks.MUSHROOM_BROWN, 1));
        register("rabbit_stew_red", PlayerInventory.RABBIT_STEW, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient(PlayerInventory.RABBIT_COOKED, 1),
                new Ingredient(PlayerInventory.CARROT, 1),
                new Ingredient(PlayerInventory.BAKED_POTATO, 1),
                new Ingredient((short) Blocks.MUSHROOM_RED, 1));
        register("rabbit_stew_from_shelf_mushroom", PlayerInventory.RABBIT_STEW, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient(PlayerInventory.RABBIT_COOKED, 1),
                new Ingredient(PlayerInventory.CARROT, 1),
                new Ingredient(PlayerInventory.BAKED_POTATO, 1),
                new Ingredient((short) Blocks.SHELF_MUSHROOM, 1));
        register("mushroom_stew", PlayerInventory.MUSHROOM_STEW, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient((short) Blocks.MUSHROOM_BROWN, 1),
                new Ingredient((short) Blocks.MUSHROOM_RED, 1));
        register("beetroot_soup", PlayerInventory.BEETROOT_SOUP, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient(PlayerInventory.BEETROOT, 6));
        // [MAPNAV] 항법 3종. 나침반/시계는 바닐라 십자 배치, 종이는 사탕수수 가로 3, 지도는 종이 8 + 나침반 1.
        registerShaped("paper", PlayerInventory.PAPER, 3,
                shape((short) Blocks.SUGARCANE, (short) Blocks.SUGARCANE, (short) Blocks.SUGARCANE,
                        0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.SUGARCANE, 3));
        registerShaped("compass", PlayerInventory.COMPASS, 1,
                shape(0, PlayerInventory.IRON_INGOT, 0,
                        PlayerInventory.IRON_INGOT, PlayerInventory.REDSTONE_DUST, PlayerInventory.IRON_INGOT,
                        0, PlayerInventory.IRON_INGOT, 0),
                new Ingredient(PlayerInventory.IRON_INGOT, 4),
                new Ingredient(PlayerInventory.REDSTONE_DUST, 1));
        registerShaped("clock", PlayerInventory.CLOCK, 1,
                shape(0, PlayerInventory.GOLD_INGOT, 0,
                        PlayerInventory.GOLD_INGOT, PlayerInventory.REDSTONE_DUST, PlayerInventory.GOLD_INGOT,
                        0, PlayerInventory.GOLD_INGOT, 0),
                new Ingredient(PlayerInventory.GOLD_INGOT, 4),
                new Ingredient(PlayerInventory.REDSTONE_DUST, 1));
        registerShaped("map", PlayerInventory.MAP, 1,
                shape(PlayerInventory.PAPER, PlayerInventory.PAPER, PlayerInventory.PAPER,
                        PlayerInventory.PAPER, PlayerInventory.COMPASS, PlayerInventory.PAPER,
                        PlayerInventory.PAPER, PlayerInventory.PAPER, PlayerInventory.PAPER),
                new Ingredient(PlayerInventory.COMPASS, 1),
                new Ingredient(PlayerInventory.PAPER, 8));

        // ── SURV-X 인챈트 확장(§0.1) ──
        // 종이는 MAPNAV가 이미 같은 1×3 모양으로 등록해 두었으므로 여기서는 소비만 한다.
        // 책은 바닐라대로 무형이며 네 칸만 차지하므로 개인 2×2 격자에서도 만들 수 있다.
        register("book", PlayerInventory.BOOK, 1,
                new Ingredient(PlayerInventory.PAPER, 3),
                new Ingredient(PlayerInventory.LEATHER, 1));
        registerShaped("bookshelf", (short) Blocks.BOOKSHELF, 1,
                shape(Blocks.PLANK, Blocks.PLANK, Blocks.PLANK,
                        PlayerInventory.BOOK, PlayerInventory.BOOK, PlayerInventory.BOOK,
                        Blocks.PLANK, Blocks.PLANK, Blocks.PLANK),
                new Ingredient((short) Blocks.PLANK, 6),
                new Ingredient(PlayerInventory.BOOK, 3));
        registerShaped("enchanting_table", (short) Blocks.ENCHANTING_TABLE, 1,
                shape(0, PlayerInventory.BOOK, 0,
                        PlayerInventory.DIAMOND, Blocks.OBSIDIAN, PlayerInventory.DIAMOND,
                        Blocks.OBSIDIAN, Blocks.OBSIDIAN, Blocks.OBSIDIAN),
                new Ingredient(PlayerInventory.BOOK, 1),
                new Ingredient(PlayerInventory.DIAMOND, 2),
                new Ingredient((short) Blocks.OBSIDIAN, 4));

        // ── [WOOL-COLOR] 양털 16색 · 카펫 16색(MC Java 1.21.4) ──
        // 실 4개 2×2 → 흰 양털 1(바닐라 white_wool). 색은 여기서만 시작되고 나머지는 염색이다.
        registerShaped("white_wool", (short) Blocks.WHITE_WOOL, 1,
                shape(PlayerInventory.STRING, PlayerInventory.STRING, 0,
                        PlayerInventory.STRING, PlayerInventory.STRING, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.STRING, 4));
        // WebCraft divergence: 순수 아이템 WOOL=264 는 저장된 인벤토리·상자 전리품·주민 거래가
        // 이미 쓰고 있어 의미를 바꾸지 않는다. 대신 흰 양털로 1:1 변환해 사장 아이템이 되지
        // 않게 한다(바닐라에는 대응 레시피가 없다 — 바닐라에 무색 양털 아이템이 없기 때문).
        register("white_wool_from_wool", (short) Blocks.WHITE_WOOL, 1,
                new Ingredient(PlayerInventory.WOOL, 1));
        registerWoolColor("white", (short) Blocks.WHITE_WOOL, (short) Blocks.WHITE_CARPET,
                PlayerInventory.WHITE_DYE);
        registerWoolColor("orange", (short) Blocks.ORANGE_WOOL, (short) Blocks.ORANGE_CARPET,
                PlayerInventory.ORANGE_DYE);
        registerWoolColor("magenta", (short) Blocks.MAGENTA_WOOL, (short) Blocks.MAGENTA_CARPET,
                PlayerInventory.MAGENTA_DYE);
        registerWoolColor("light_blue", (short) Blocks.LIGHT_BLUE_WOOL, (short) Blocks.LIGHT_BLUE_CARPET,
                PlayerInventory.LIGHT_BLUE_DYE);
        registerWoolColor("yellow", (short) Blocks.YELLOW_WOOL, (short) Blocks.YELLOW_CARPET,
                PlayerInventory.YELLOW_DYE);
        registerWoolColor("lime", (short) Blocks.LIME_WOOL, (short) Blocks.LIME_CARPET,
                PlayerInventory.LIME_DYE);
        registerWoolColor("pink", (short) Blocks.PINK_WOOL, (short) Blocks.PINK_CARPET,
                PlayerInventory.PINK_DYE);
        registerWoolColor("gray", (short) Blocks.GRAY_WOOL, (short) Blocks.GRAY_CARPET,
                PlayerInventory.GRAY_DYE);
        registerWoolColor("light_gray", (short) Blocks.LIGHT_GRAY_WOOL, (short) Blocks.LIGHT_GRAY_CARPET,
                PlayerInventory.LIGHT_GRAY_DYE);
        registerWoolColor("cyan", (short) Blocks.CYAN_WOOL, (short) Blocks.CYAN_CARPET,
                PlayerInventory.CYAN_DYE);
        registerWoolColor("purple", (short) Blocks.PURPLE_WOOL, (short) Blocks.PURPLE_CARPET,
                PlayerInventory.PURPLE_DYE);
        registerWoolColor("blue", (short) Blocks.BLUE_WOOL, (short) Blocks.BLUE_CARPET,
                PlayerInventory.BLUE_DYE);
        registerWoolColor("brown", (short) Blocks.BROWN_WOOL, (short) Blocks.BROWN_CARPET,
                PlayerInventory.BROWN_DYE);
        registerWoolColor("green", (short) Blocks.GREEN_WOOL, (short) Blocks.GREEN_CARPET,
                PlayerInventory.GREEN_DYE);
        registerWoolColor("red", (short) Blocks.RED_WOOL, (short) Blocks.RED_CARPET,
                PlayerInventory.RED_DYE);
        registerWoolColor("black", (short) Blocks.BLACK_WOOL, (short) Blocks.BLACK_CARPET,
                PlayerInventory.BLACK_DYE);

        // ── [BED-COLOR] 침대 16색(MC Java 1.21.4) ──
        // 총칭 침대 32 의 기존 "bed" 레시피(무색 양털 264 3 + 판자 3)는 저장된 인벤토리·상자
        // 전리품·주민 거래가 아직 264 를 주므로 그대로 남긴다. 색 양털은 이제 그 색 침대가 된다.

        registerBedColor("white", (short) Blocks.WHITE_BED, (short) Blocks.WHITE_WOOL,
                PlayerInventory.WHITE_DYE);
        registerBedColor("orange", (short) Blocks.ORANGE_BED, (short) Blocks.ORANGE_WOOL,
                PlayerInventory.ORANGE_DYE);
        registerBedColor("magenta", (short) Blocks.MAGENTA_BED, (short) Blocks.MAGENTA_WOOL,
                PlayerInventory.MAGENTA_DYE);
        registerBedColor("light_blue", (short) Blocks.LIGHT_BLUE_BED, (short) Blocks.LIGHT_BLUE_WOOL,
                PlayerInventory.LIGHT_BLUE_DYE);
        registerBedColor("yellow", (short) Blocks.YELLOW_BED, (short) Blocks.YELLOW_WOOL,
                PlayerInventory.YELLOW_DYE);
        registerBedColor("lime", (short) Blocks.LIME_BED, (short) Blocks.LIME_WOOL,
                PlayerInventory.LIME_DYE);
        registerBedColor("pink", (short) Blocks.PINK_BED, (short) Blocks.PINK_WOOL,
                PlayerInventory.PINK_DYE);
        registerBedColor("gray", (short) Blocks.GRAY_BED, (short) Blocks.GRAY_WOOL,
                PlayerInventory.GRAY_DYE);
        registerBedColor("light_gray", (short) Blocks.LIGHT_GRAY_BED, (short) Blocks.LIGHT_GRAY_WOOL,
                PlayerInventory.LIGHT_GRAY_DYE);
        registerBedColor("cyan", (short) Blocks.CYAN_BED, (short) Blocks.CYAN_WOOL,
                PlayerInventory.CYAN_DYE);
        registerBedColor("purple", (short) Blocks.PURPLE_BED, (short) Blocks.PURPLE_WOOL,
                PlayerInventory.PURPLE_DYE);
        registerBedColor("blue", (short) Blocks.BLUE_BED, (short) Blocks.BLUE_WOOL,
                PlayerInventory.BLUE_DYE);
        registerBedColor("brown", (short) Blocks.BROWN_BED, (short) Blocks.BROWN_WOOL,
                PlayerInventory.BROWN_DYE);
        registerBedColor("green", (short) Blocks.GREEN_BED, (short) Blocks.GREEN_WOOL,
                PlayerInventory.GREEN_DYE);
        registerBedColor("red", (short) Blocks.RED_BED, (short) Blocks.RED_WOOL,
                PlayerInventory.RED_DYE);
        registerBedColor("black", (short) Blocks.BLACK_BED, (short) Blocks.BLACK_WOOL,
                PlayerInventory.BLACK_DYE);

        // ── [FURNITURE-26.3] 건초 침대 · 쿠션 16색 ──
        //
        // 건초 침대: **건초 더미 3 → 건초 침대 4** [B]. 산출 4 는 위키 서술 그대로다.
        // 배치는 위키에 그림만 있고 격자 좌표가 서술되지 않아 [C] 로 정한다 — 침대와 같은
        // 가로 한 줄이다(재료가 셋이고 침대 계열이라는 두 사실이 같은 모양을 가리킨다).
        registerShaped("straw_bed", (short) Blocks.STRAW_BED, 4,
                shape(Blocks.HAY_BLOCK, Blocks.HAY_BLOCK, Blocks.HAY_BLOCK, 0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.HAY_BLOCK, 3));

        // Snapshot-7 exact recipes: three same-color wool slabs → one cushion; a dye plus any
        // differently colored cushion → the target color. IDs remain the append-only item identities.
        for (int color = 0; color < Blocks.WOOL_BY_DYE_COLOR.length; color++) {
            registerCushionColor(DYE_COLOR_NAMES[color],
                    (short) Blocks.CUSHION_BY_DYE_COLOR[color],
                    (short) (Blocks.WHITE_WOOL_SLAB + color),
                    (short) (PlayerInventory.WHITE_DYE + color));
        }

        // ── [WOOD-SPECIES] 종별 목재 건축 세트 ──
        // 참나무는 기존 총칭 레시피(wood_stairs·plank_slab·wood_fence·wood_fence_gate·
        // wood_trapdoor·door)가 그대로 담당하므로 여기서 다시 등록하지 않는다.
        registerWoodSpeciesShapes("birch", (short) Blocks.BIRCH_PLANK,
                (short) Blocks.BIRCH_STAIRS, (short) Blocks.BIRCH_SLAB,
                (short) Blocks.BIRCH_FENCE, (short) Blocks.BIRCH_FENCE_GATE,
                (short) Blocks.BIRCH_TRAPDOOR, (short) Blocks.BIRCH_DOOR);
        registerWoodSpeciesShapes("spruce", (short) Blocks.SPRUCE_PLANK,
                (short) Blocks.SPRUCE_STAIRS, (short) Blocks.SPRUCE_SLAB,
                (short) Blocks.SPRUCE_FENCE, (short) Blocks.SPRUCE_FENCE_GATE,
                (short) Blocks.SPRUCE_TRAPDOOR, (short) Blocks.SPRUCE_DOOR);
        registerWoodSpeciesShapes("jungle", (short) Blocks.JUNGLE_PLANK,
                (short) Blocks.JUNGLE_STAIRS, (short) Blocks.JUNGLE_SLAB,
                (short) Blocks.JUNGLE_FENCE, (short) Blocks.JUNGLE_FENCE_GATE,
                (short) Blocks.JUNGLE_TRAPDOOR, (short) Blocks.JUNGLE_DOOR);
        registerWoodSpeciesShapes("acacia", (short) Blocks.ACACIA_PLANK,
                (short) Blocks.ACACIA_STAIRS, (short) Blocks.ACACIA_SLAB,
                (short) Blocks.ACACIA_FENCE, (short) Blocks.ACACIA_FENCE_GATE,
                (short) Blocks.ACACIA_TRAPDOOR, (short) Blocks.ACACIA_DOOR);
        registerWoodSpeciesShapes("dark_oak", (short) Blocks.DARK_OAK_PLANK,
                (short) Blocks.DARK_OAK_STAIRS, (short) Blocks.DARK_OAK_SLAB,
                (short) Blocks.DARK_OAK_FENCE, (short) Blocks.DARK_OAK_FENCE_GATE,
                (short) Blocks.DARK_OAK_TRAPDOOR, (short) Blocks.DARK_OAK_DOOR);
        registerWoodSpeciesShapes("cherry", (short) Blocks.CHERRY_PLANK,
                (short) Blocks.CHERRY_STAIRS, (short) Blocks.CHERRY_SLAB,
                (short) Blocks.CHERRY_FENCE, (short) Blocks.CHERRY_FENCE_GATE,
                (short) Blocks.CHERRY_TRAPDOOR, (short) Blocks.CHERRY_DOOR);
        registerWoodSpeciesShapes("mangrove", (short) Blocks.MANGROVE_PLANK,
                (short) Blocks.MANGROVE_STAIRS, (short) Blocks.MANGROVE_SLAB,
                (short) Blocks.MANGROVE_FENCE, (short) Blocks.MANGROVE_FENCE_GATE,
                (short) Blocks.MANGROVE_TRAPDOOR, (short) Blocks.MANGROVE_DOOR);
        // [PALE-GARDEN] 창백한 참나무 일곱 형상. 배치·산출량은 다른 수종과 문자 그대로
        // 같다(핀 §5b — 바닐라도 목재 형상 레시피는 수종 정확 일치다).
        registerWoodSpeciesShapes("pale_oak", (short) Blocks.PALE_OAK_PLANK,
                (short) Blocks.PALE_OAK_STAIRS, (short) Blocks.PALE_OAK_SLAB,
                (short) Blocks.PALE_OAK_FENCE, (short) Blocks.PALE_OAK_FENCE_GATE,
                (short) Blocks.PALE_OAK_TRAPDOOR, (short) Blocks.PALE_OAK_DOOR);
        // [POPLAR] 포플러 일곱 형상. 배치·산출량은 다른 수종과 문자 그대로 같다 —
        // 26.3 은 목재 형상 레시피를 바꾸지 않았고 포플러도 #planks 태그가 아니라
        // 수종 정확 일치다(핀 §2.1).
        registerWoodSpeciesShapes("poplar", (short) Blocks.POPLAR_PLANK,
                (short) Blocks.POPLAR_STAIRS, (short) Blocks.POPLAR_SLAB,
                (short) Blocks.POPLAR_FENCE, (short) Blocks.POPLAR_FENCE_GATE,
                (short) Blocks.POPLAR_TRAPDOOR, (short) Blocks.POPLAR_DOOR);
        // [POPLAR-26.3] 고정 snapshot-7 데이터팩의 recipe JSON 배치·산출량 그대로다.
        register("poplar_button", (short) Blocks.POPLAR_BUTTON, 1,
                new Ingredient((short) Blocks.POPLAR_PLANK, 1));
        registerShaped("poplar_pressure_plate", (short) Blocks.POPLAR_PRESSURE_PLATE, 1,
                shape(Blocks.POPLAR_PLANK, Blocks.POPLAR_PLANK, 0, 0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.POPLAR_PLANK, 2));
        registerShaped("poplar_sign", (short) Blocks.POPLAR_SIGN, 3,
                shape(Blocks.POPLAR_PLANK, Blocks.POPLAR_PLANK, Blocks.POPLAR_PLANK,
                        Blocks.POPLAR_PLANK, Blocks.POPLAR_PLANK, Blocks.POPLAR_PLANK,
                        0, PlayerInventory.STICK, 0),
                new Ingredient((short) Blocks.POPLAR_PLANK, 6),
                new Ingredient(PlayerInventory.STICK, 1));
        registerShaped("poplar_hanging_sign", (short) Blocks.POPLAR_HANGING_SIGN, 6,
                shape(Blocks.CHAIN, 0, Blocks.CHAIN,
                        Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG,
                        Blocks.STRIPPED_POPLAR_LOG,
                        Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG,
                        Blocks.STRIPPED_POPLAR_LOG),
                new Ingredient((short) Blocks.CHAIN, 2),
                new Ingredient((short) Blocks.STRIPPED_POPLAR_LOG, 6));
        registerShaped("poplar_wood", (short) Blocks.POPLAR_WOOD, 3,
                shape(Blocks.POPLAR_LOG, Blocks.POPLAR_LOG, 0,
                        Blocks.POPLAR_LOG, Blocks.POPLAR_LOG, 0, 0, 0, 0),
                new Ingredient((short) Blocks.POPLAR_LOG, 4));
        registerShaped("stripped_poplar_wood", (short) Blocks.STRIPPED_POPLAR_WOOD, 3,
                shape(Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG, 0,
                        Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG, 0, 0, 0, 0),
                new Ingredient((short) Blocks.STRIPPED_POPLAR_LOG, 4));
        registerShaped("poplar_shelf", (short) Blocks.POPLAR_SHELF, 6,
                shape(Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG,
                        Blocks.STRIPPED_POPLAR_LOG,
                        0, 0, 0,
                        Blocks.STRIPPED_POPLAR_LOG, Blocks.STRIPPED_POPLAR_LOG,
                        Blocks.STRIPPED_POPLAR_LOG),
                new Ingredient((short) Blocks.STRIPPED_POPLAR_LOG, 6));
        registerShaped("pale_oak_shelf", (short) Blocks.PALE_OAK_SHELF, 6,
                shape(Blocks.STRIPPED_PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG,
                        Blocks.STRIPPED_PALE_OAK_LOG,
                        0, 0, 0,
                        Blocks.STRIPPED_PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG,
                        Blocks.STRIPPED_PALE_OAK_LOG),
                new Ingredient((short) Blocks.STRIPPED_PALE_OAK_LOG, 6));
        // [PALE-GARDEN] 창백한 이끼 바닥. 바닐라 moss_carpet 과 같은 배치다 —
        // 이끼 블록 2 를 가로로 놓아 3 이 나온다. 이끼 블록 자체는 제작할 수 없다
        // (바닐라도 같다 — 자연 산지·뼛가루뿐이다).
        registerShaped("pale_moss_carpet", (short) Blocks.PALE_MOSS_CARPET, 3,
                shape(Blocks.PALE_MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, 0, 0, 0, 0, 0, 0, 0),
                new Ingredient((short) Blocks.PALE_MOSS_BLOCK, 2));
        // [CREAKING] 수지 사슬 축소판. **바닐라 배치 그대로이고 재료 대체가 하나도 없다.**
        //  · 수지 블록 = 수지 덩어리 9 (바닐라 9칸 저장 블록 규약 — [B] «Resin Clump»).
        //  · 크리킹 하트 = 창백한 참나무 원목 2 + 수지 블록 1 **무형**
        //    ([B] «Creaking Heart» — 1.21.4/24w44a 에서 바닐라가 직접 낸 레시피다).
        // 바닐라에는 수지 블록 → 덩어리 9 해체가 **없으므로** 여기에도 만들지 않는다
        // (건초·뼈 블록의 `_undo` 를 흉내 내지 않는 이유가 이것이다).
        register("resin_block", (short) Blocks.RESIN_BLOCK, 1,
                new Ingredient((short) Blocks.RESIN_CLUMP, 9));
        register("creaking_heart", (short) Blocks.CREAKING_HEART, 1,
                new Ingredient((short) Blocks.PALE_OAK_LOG, 2),
                new Ingredient((short) Blocks.RESIN_BLOCK, 1));

        // ── [POTION] 양조 재료 두 종은 바닐라 제작대 레시피 그대로다 ──
        register("sugar", PlayerInventory.SUGAR, 1,
                new Ingredient((short) Blocks.SUGARCANE, 1));
        register("fermented_spider_eye", PlayerInventory.FERMENTED_SPIDER_EYE, 1,
                new Ingredient(PlayerInventory.SPIDER_EYE, 1),
                new Ingredient(PlayerInventory.SUGAR, 1),
                new Ingredient((short) Blocks.MUSHROOM_BROWN, 1));

        // ── [POTION] 양조대 레시피. 사슬 전체가 **바닐라 원본 그대로**이며 어색한 물약도
        // 물병 + 네더 사마귀다. 네더가 없는 것은 재료가 아니라 획득 경로의 문제라, 사마귀는
        // 레이드 승리 보상 풀과 던전 상자 전리품으로 얻는다(MC-REFERENCE 「물약·양조」 절). ──
        registerBrew("awkward_potion", PlayerInventory.AWKWARD_POTION,
                PlayerInventory.WATER_BOTTLE, PlayerInventory.NETHER_WART);
        registerBrew("potion_weakness", PlayerInventory.POTION_WEAKNESS,
                PlayerInventory.WATER_BOTTLE, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("potion_swiftness", PlayerInventory.POTION_SWIFTNESS,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.SUGAR);
        registerBrew("potion_poison", PlayerInventory.POTION_POISON,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.SPIDER_EYE);
        registerBrew("potion_slowness", PlayerInventory.POTION_SLOWNESS,
                PlayerInventory.POTION_SWIFTNESS, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("potion_harming", PlayerInventory.POTION_HARMING,
                PlayerInventory.POTION_POISON, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("splash_potion_swiftness", PlayerInventory.SPLASH_POTION_SWIFTNESS,
                PlayerInventory.POTION_SWIFTNESS, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_poison", PlayerInventory.SPLASH_POTION_POISON,
                PlayerInventory.POTION_POISON, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_slowness", PlayerInventory.SPLASH_POTION_SLOWNESS,
                PlayerInventory.POTION_SLOWNESS, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_weakness", PlayerInventory.SPLASH_POTION_WEAKNESS,
                PlayerInventory.POTION_WEAKNESS, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_harming", PlayerInventory.SPLASH_POTION_HARMING,
                PlayerInventory.POTION_HARMING, PlayerInventory.GUNPOWDER);

        // ── [POTION-UPGRADE] 강화 양조. 바닐라 `PotionBrewing` 표 그대로 레드스톤은 연장,
        // 발광석 가루는 II 등급이며 한 병에 둘을 같이 걸 수 없다(강화된 물약은 다시 강화 재료를
        // 받지 않는다). 바닐라에 없는 조합은 등록하지 않는다 — 나약함은 II 가 없고 고통은
        // 즉발이라 연장이 없다. 투척형은 기존 규약대로 화약 한 단계로만 만든다. ──
        registerBrew("potion_swiftness_long", PlayerInventory.POTION_SWIFTNESS_LONG,
                PlayerInventory.POTION_SWIFTNESS, PlayerInventory.REDSTONE_DUST);
        registerBrew("potion_poison_long", PlayerInventory.POTION_POISON_LONG,
                PlayerInventory.POTION_POISON, PlayerInventory.REDSTONE_DUST);
        registerBrew("potion_slowness_long", PlayerInventory.POTION_SLOWNESS_LONG,
                PlayerInventory.POTION_SLOWNESS, PlayerInventory.REDSTONE_DUST);
        registerBrew("potion_weakness_long", PlayerInventory.POTION_WEAKNESS_LONG,
                PlayerInventory.POTION_WEAKNESS, PlayerInventory.REDSTONE_DUST);
        registerBrew("potion_swiftness_ii", PlayerInventory.POTION_SWIFTNESS_II,
                PlayerInventory.POTION_SWIFTNESS, PlayerInventory.GLOWSTONE_DUST);
        registerBrew("potion_poison_ii", PlayerInventory.POTION_POISON_II,
                PlayerInventory.POTION_POISON, PlayerInventory.GLOWSTONE_DUST);
        registerBrew("potion_slowness_ii", PlayerInventory.POTION_SLOWNESS_II,
                PlayerInventory.POTION_SLOWNESS, PlayerInventory.GLOWSTONE_DUST);
        registerBrew("potion_harming_ii", PlayerInventory.POTION_HARMING_II,
                PlayerInventory.POTION_HARMING, PlayerInventory.GLOWSTONE_DUST);
        // 발효된 거미 눈은 바닐라에서 **연장** 등급만 보존한 채 부패시킨다
        // (LONG_SWIFTNESS→LONG_SLOWNESS · LONG_POISON→HARMING · STRONG_POISON→STRONG_HARMING).
        // **II 등급 신속·도약은 바닐라에서 부패하지 않는다** — [B] minecraft.wiki «Potion»
        // "The only exception to this is the enhanced potion of Slowness, which cannot be brewed
        // by corrupting an enhanced potion of Swiftness or Leaping." · «Fermented Spider Eye»
        // "Enhanced potions of Swiftness or Leaping cannot be corrupted."
        // 바닐라 `PotionBrewing` 에도 STRONG_SWIFTNESS/STRONG_LEAPING 부패 줄이 없고
        // STRONG_SLOWNESS 는 오직 SLOWNESS + 발광석 가루 하나로만 나온다.
        // 그래서 여기에 STRONG_SWIFTNESS→STRONG_SLOWNESS 줄이 **없는 것이 곧 계약**이다.
        registerBrew("potion_slowness_long_from_swiftness", PlayerInventory.POTION_SLOWNESS_LONG,
                PlayerInventory.POTION_SWIFTNESS_LONG, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("potion_harming_from_long_poison", PlayerInventory.POTION_HARMING,
                PlayerInventory.POTION_POISON_LONG, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("potion_harming_ii_from_poison_ii", PlayerInventory.POTION_HARMING_II,
                PlayerInventory.POTION_POISON_II, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("splash_potion_swiftness_long", PlayerInventory.SPLASH_POTION_SWIFTNESS_LONG,
                PlayerInventory.POTION_SWIFTNESS_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_poison_long", PlayerInventory.SPLASH_POTION_POISON_LONG,
                PlayerInventory.POTION_POISON_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_slowness_long", PlayerInventory.SPLASH_POTION_SLOWNESS_LONG,
                PlayerInventory.POTION_SLOWNESS_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_weakness_long", PlayerInventory.SPLASH_POTION_WEAKNESS_LONG,
                PlayerInventory.POTION_WEAKNESS_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_swiftness_ii", PlayerInventory.SPLASH_POTION_SWIFTNESS_II,
                PlayerInventory.POTION_SWIFTNESS_II, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_poison_ii", PlayerInventory.SPLASH_POTION_POISON_II,
                PlayerInventory.POTION_POISON_II, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_slowness_ii", PlayerInventory.SPLASH_POTION_SLOWNESS_II,
                PlayerInventory.POTION_SLOWNESS_II, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_harming_ii", PlayerInventory.SPLASH_POTION_HARMING_II,
                PlayerInventory.POTION_HARMING_II, PlayerInventory.GUNPOWDER);

        // ── [BRIMSTONE] 화염 저항 사슬. **바닐라 레시피 원본 그대로**다 —
        // 어색한 물약 + 마그마 크림 → 화염 저항, + 레드스톤 → 연장, + 화약 → 투척판.
        // 발광석 가루 강화(II 등급)는 바닐라에도 없으므로 등록하지 않는다.
        registerBrew("potion_fire_resistance", PlayerInventory.POTION_FIRE_RESISTANCE,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.MAGMA_CREAM);
        registerBrew("potion_fire_resistance_long", PlayerInventory.POTION_FIRE_RESISTANCE_LONG,
                PlayerInventory.POTION_FIRE_RESISTANCE, PlayerInventory.REDSTONE_DUST);
        registerBrew("splash_potion_fire_resistance",
                PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE,
                PlayerInventory.POTION_FIRE_RESISTANCE, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_fire_resistance_long",
                PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE_LONG,
                PlayerInventory.POTION_FIRE_RESISTANCE_LONG, PlayerInventory.GUNPOWDER);

        // ── [POTION-GAP] 힘 · 수중 호흡 · 도약 · 야간 투시 사슬. 전부 **바닐라 레시피 원본
        // 그대로**이고 재료를 하나도 대체하지 않았다 — 네 재료가 이미 저장소에 있었다
        // (블레이즈 가루 1603 · 복어 451 · 토끼 발 357 · 황금 당근 409). 강화 규약도 기존
        // [POTION-UPGRADE] 절과 같다: 레드스톤 = 연장, 발광석 가루 = II 이며 바닐라에 없는
        // 조합은 등록하지 않는다(수중 호흡·야간 투시는 II 가 없다).
        registerBrew("potion_strength", PlayerInventory.POTION_STRENGTH,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.BLAZE_POWDER);
        registerBrew("potion_strength_long", PlayerInventory.POTION_STRENGTH_LONG,
                PlayerInventory.POTION_STRENGTH, PlayerInventory.REDSTONE_DUST);
        registerBrew("potion_strength_ii", PlayerInventory.POTION_STRENGTH_II,
                PlayerInventory.POTION_STRENGTH, PlayerInventory.GLOWSTONE_DUST);
        registerBrew("splash_potion_strength", PlayerInventory.SPLASH_POTION_STRENGTH,
                PlayerInventory.POTION_STRENGTH, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_strength_long", PlayerInventory.SPLASH_POTION_STRENGTH_LONG,
                PlayerInventory.POTION_STRENGTH_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_strength_ii", PlayerInventory.SPLASH_POTION_STRENGTH_II,
                PlayerInventory.POTION_STRENGTH_II, PlayerInventory.GUNPOWDER);
        registerBrew("potion_water_breathing", PlayerInventory.POTION_WATER_BREATHING,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.PUFFERFISH);
        registerBrew("potion_water_breathing_long", PlayerInventory.POTION_WATER_BREATHING_LONG,
                PlayerInventory.POTION_WATER_BREATHING, PlayerInventory.REDSTONE_DUST);
        registerBrew("splash_potion_water_breathing",
                PlayerInventory.SPLASH_POTION_WATER_BREATHING,
                PlayerInventory.POTION_WATER_BREATHING, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_water_breathing_long",
                PlayerInventory.SPLASH_POTION_WATER_BREATHING_LONG,
                PlayerInventory.POTION_WATER_BREATHING_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("potion_leaping", PlayerInventory.POTION_LEAPING,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.RABBIT_FOOT);
        registerBrew("potion_leaping_long", PlayerInventory.POTION_LEAPING_LONG,
                PlayerInventory.POTION_LEAPING, PlayerInventory.REDSTONE_DUST);
        registerBrew("potion_leaping_ii", PlayerInventory.POTION_LEAPING_II,
                PlayerInventory.POTION_LEAPING, PlayerInventory.GLOWSTONE_DUST);
        registerBrew("splash_potion_leaping", PlayerInventory.SPLASH_POTION_LEAPING,
                PlayerInventory.POTION_LEAPING, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_leaping_long", PlayerInventory.SPLASH_POTION_LEAPING_LONG,
                PlayerInventory.POTION_LEAPING_LONG, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_leaping_ii", PlayerInventory.SPLASH_POTION_LEAPING_II,
                PlayerInventory.POTION_LEAPING_II, PlayerInventory.GUNPOWDER);
        registerBrew("potion_night_vision", PlayerInventory.POTION_NIGHT_VISION,
                PlayerInventory.AWKWARD_POTION, PlayerInventory.GOLDEN_CARROT);
        registerBrew("potion_night_vision_long", PlayerInventory.POTION_NIGHT_VISION_LONG,
                PlayerInventory.POTION_NIGHT_VISION, PlayerInventory.REDSTONE_DUST);
        registerBrew("splash_potion_night_vision", PlayerInventory.SPLASH_POTION_NIGHT_VISION,
                PlayerInventory.POTION_NIGHT_VISION, PlayerInventory.GUNPOWDER);
        registerBrew("splash_potion_night_vision_long",
                PlayerInventory.SPLASH_POTION_NIGHT_VISION_LONG,
                PlayerInventory.POTION_NIGHT_VISION_LONG, PlayerInventory.GUNPOWDER);
        // 발효된 거미 눈 부패 사슬. 바닐라 `PotionBrewing` 은 힘·수중 호흡에 부패 대응을 두지
        // 않고, 도약에는 기본·연장 둘만 둔다(LEAPING→SLOWNESS · LONG_LEAPING→LONG_SLOWNESS).
        // 도약 II 는 바닐라에도 대응이 없다 — [B] «Fermented Spider Eye» "Enhanced potions of
        // Swiftness or Leaping cannot be corrupted." 그래서 여기서도 도약 둘만 등록한다.
        //
        // 야간 투시는 **바닐라에 대응이 있다** — NIGHT_VISION→INVISIBILITY ·
        // LONG_NIGHT_VISION→LONG_INVISIBILITY([B] minecraft.wiki «Brewing» "Fermented Spider Eye
        // + Potion of Night Vision → Potion of Invisibility"). 이 저장소에는 **투명화 효과 자체가
        // 미구현**이라 생성물을 만들 수 없어 등록하지 않는다. 즉 이 빠짐은 바닐라 준수가 아니라
        // 의도된 **divergence** 다(docs/research/mc-potion-gap.md 4절 · 투명화 구현은 백로그).
        registerBrew("potion_slowness_from_leaping", PlayerInventory.POTION_SLOWNESS,
                PlayerInventory.POTION_LEAPING, PlayerInventory.FERMENTED_SPIDER_EYE);
        registerBrew("potion_slowness_long_from_leaping", PlayerInventory.POTION_SLOWNESS_LONG,
                PlayerInventory.POTION_LEAPING_LONG, PlayerInventory.FERMENTED_SPIDER_EYE);

        // ── [STAINED-GLASS] 색 유리 16 · 색 유리판 16(MC Java 1.21.4) ──
        // 기존 무색 "glass_pane" 레시피(유리 6 → 유리판 16)는 그대로 남는다.
        registerStainedGlassColor("white", (short) Blocks.WHITE_STAINED_GLASS,
                (short) Blocks.WHITE_STAINED_GLASS_PANE, PlayerInventory.WHITE_DYE);
        registerStainedGlassColor("orange", (short) Blocks.ORANGE_STAINED_GLASS,
                (short) Blocks.ORANGE_STAINED_GLASS_PANE, PlayerInventory.ORANGE_DYE);
        registerStainedGlassColor("magenta", (short) Blocks.MAGENTA_STAINED_GLASS,
                (short) Blocks.MAGENTA_STAINED_GLASS_PANE, PlayerInventory.MAGENTA_DYE);
        registerStainedGlassColor("light_blue", (short) Blocks.LIGHT_BLUE_STAINED_GLASS,
                (short) Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, PlayerInventory.LIGHT_BLUE_DYE);
        registerStainedGlassColor("yellow", (short) Blocks.YELLOW_STAINED_GLASS,
                (short) Blocks.YELLOW_STAINED_GLASS_PANE, PlayerInventory.YELLOW_DYE);
        registerStainedGlassColor("lime", (short) Blocks.LIME_STAINED_GLASS,
                (short) Blocks.LIME_STAINED_GLASS_PANE, PlayerInventory.LIME_DYE);
        registerStainedGlassColor("pink", (short) Blocks.PINK_STAINED_GLASS,
                (short) Blocks.PINK_STAINED_GLASS_PANE, PlayerInventory.PINK_DYE);
        registerStainedGlassColor("gray", (short) Blocks.GRAY_STAINED_GLASS,
                (short) Blocks.GRAY_STAINED_GLASS_PANE, PlayerInventory.GRAY_DYE);
        registerStainedGlassColor("light_gray", (short) Blocks.LIGHT_GRAY_STAINED_GLASS,
                (short) Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, PlayerInventory.LIGHT_GRAY_DYE);
        registerStainedGlassColor("cyan", (short) Blocks.CYAN_STAINED_GLASS,
                (short) Blocks.CYAN_STAINED_GLASS_PANE, PlayerInventory.CYAN_DYE);
        registerStainedGlassColor("purple", (short) Blocks.PURPLE_STAINED_GLASS,
                (short) Blocks.PURPLE_STAINED_GLASS_PANE, PlayerInventory.PURPLE_DYE);
        registerStainedGlassColor("blue", (short) Blocks.BLUE_STAINED_GLASS,
                (short) Blocks.BLUE_STAINED_GLASS_PANE, PlayerInventory.BLUE_DYE);
        registerStainedGlassColor("brown", (short) Blocks.BROWN_STAINED_GLASS,
                (short) Blocks.BROWN_STAINED_GLASS_PANE, PlayerInventory.BROWN_DYE);
        registerStainedGlassColor("green", (short) Blocks.GREEN_STAINED_GLASS,
                (short) Blocks.GREEN_STAINED_GLASS_PANE, PlayerInventory.GREEN_DYE);
        registerStainedGlassColor("red", (short) Blocks.RED_STAINED_GLASS,
                (short) Blocks.RED_STAINED_GLASS_PANE, PlayerInventory.RED_DYE);
        registerStainedGlassColor("black", (short) Blocks.BLACK_STAINED_GLASS,
                (short) Blocks.BLACK_STAINED_GLASS_PANE, PlayerInventory.BLACK_DYE);

        // ── [CONCRETE] 콘크리트 가루 · 테라코타 · 유광 테라코타(MC Java 1.21.4) ──
        //
        // 사슬은 바닐라 recipes/*.json 그대로다.
        //   점토 블록 --제련--> 테라코타 --(8 + 염료 1)--> 색 테라코타 8 --제련--> 유광 테라코타
        //   모래 4 + 자갈 4 + 염료 1 --무형--> 색 콘크리트 가루 8 --물 접촉--> 색 콘크리트
        //
        // 콘크리트에는 제작식이 없다. 오직 가루가 물에 닿아 굳는 경로뿐이며 그 판정은
        // ConcreteRules 가 소유한다(재료 대체 없이 바닐라 획득 경로를 그대로 옮긴 것이다).
        // 재료는 전부 이미 존재한다 — 점토 블록 187 은 지형 생성, 모래·자갈은 지형,
        // 염료 16색은 452~467 이라 새 획득 경로를 신설할 필요가 없다.
        registerSmelt("terracotta", (short) Blocks.TERRACOTTA, 1,
                new Ingredient((short) Blocks.CLAY, 1));
        for (int color = 0; color < COLOR_KEYS.length; color++) {
            registerTerracottaColor(COLOR_KEYS[color], (short) Blocks.TERRACOTTA_BY_COLOR[color],
                    (short) Blocks.GLAZED_TERRACOTTA_BY_COLOR[color],
                    (short) Blocks.CONCRETE_POWDER_BY_COLOR[color], DYE_BY_COLOR[color]);
        }

        // ── [DYE-SOURCE] 염료 16색을 **만드는** 레시피(MC Java 1.21.4) ────────────
        //
        // 염료 아이템 16종(452~467)과 염료를 **쓰는** 레시피(양털·카펫·색유리·색유리판·
        // 테라코타·유광 테라코타·콘크리트 가루·침대·쿠션)는 이미 있었는데, 염료를
        // **만드는** 레시피가 하나도 없었다 — 주민 거래 밖에서는 16색이 전부 획득 불가였다.
        // 근거 고정본은 docs/research/mc-composter-dye.md §2 다(등급 [B]).
        //
        // **재료를 대체하지 않는다.** 이 저장소에 없는 꽃(튤립·알리움·수레국화·난초·
        // 은방울꽃·데이지·해바라기·라일락·장미덤불·작약·위더 로즈)에서 나오는 1:1 경로는
        // 그냥 등록하지 않고 비운다. 그래도 16색이 전부 나오는 이유는 혼합 사슬이 닫혀
        // 있기 때문이다(뿌리 7종: 뼛가루·민들레·양귀비/비트·청금석·먹물 주머니·선인장
        // 제련·코코아 콩).
        //
        // 산출 개수와 재료 개수는 바닐라 recipe JSON 그대로다.

        // 1:1 · 원료 → 염료. 이 저장소에 실존하는 원료만 옮긴다.
        register("white_dye", PlayerInventory.WHITE_DYE, 1,
                new Ingredient(PlayerInventory.BONE_MEAL, 1));
        register("yellow_dye", PlayerInventory.YELLOW_DYE, 1,
                new Ingredient((short) Blocks.FLOWER_YELLOW, 1));   // 민들레
        register("red_dye", PlayerInventory.RED_DYE, 1,
                new Ingredient((short) Blocks.FLOWER_RED, 1));      // 양귀비
        register("red_dye_from_beetroot", PlayerInventory.RED_DYE, 1,
                new Ingredient(PlayerInventory.BEETROOT, 1));
        register("blue_dye", PlayerInventory.BLUE_DYE, 1,
                new Ingredient(PlayerInventory.LAPIS_LAZULI, 1));
        register("black_dye", PlayerInventory.BLACK_DYE, 1,
                new Ingredient(PlayerInventory.INK_SAC, 1));
        register("brown_dye", PlayerInventory.BROWN_DYE, 1,
                new Ingredient((short) Blocks.COCOA_BEANS, 1));
        // 선인장 꽃 → 분홍 염료 1:1 은 mc-1215/SPRING_TO_LIFE.md 가 소유한 [B] 값이다.
        register("pink_dye_from_cactus_flower", PlayerInventory.PINK_DYE, 1,
                new Ingredient((short) Blocks.CACTUS_FLOWER, 1));
        // [PITCHER] 바닐라 recipe/cyan_dye_from_pitcher_plant.json: 벌레잡이풀 1 → 청록 염료 2(무형).
        register("cyan_dye_from_pitcher_plant", PlayerInventory.CYAN_DYE, 2,
                new Ingredient((short) Blocks.PITCHER_PLANT, 1));

        // 제련 두 종. 바닐라도 초록·연두의 유일한 비혼합 경로가 제련이다.
        registerSmelt("green_dye", PlayerInventory.GREEN_DYE, 1,
                new Ingredient((short) Blocks.CACTUS, 1));
        registerSmelt("lime_dye_from_sea_pickle", PlayerInventory.LIME_DYE, 1,
                new Ingredient((short) Blocks.SEA_PICKLE, 1));

        // 혼합 12종(무형). 전부 바닐라 원문 그대로의 재료·산출 개수다.
        register("orange_dye", PlayerInventory.ORANGE_DYE, 2,
                new Ingredient(PlayerInventory.RED_DYE, 1),
                new Ingredient(PlayerInventory.YELLOW_DYE, 1));
        register("magenta_dye", PlayerInventory.MAGENTA_DYE, 2,
                new Ingredient(PlayerInventory.PURPLE_DYE, 1),
                new Ingredient(PlayerInventory.PINK_DYE, 1));
        register("magenta_dye_from_blue_red_pink", PlayerInventory.MAGENTA_DYE, 2,
                new Ingredient(PlayerInventory.BLUE_DYE, 1),
                new Ingredient(PlayerInventory.RED_DYE, 1),
                new Ingredient(PlayerInventory.PINK_DYE, 1));
        register("magenta_dye_from_blue_red_white", PlayerInventory.MAGENTA_DYE, 4,
                new Ingredient(PlayerInventory.BLUE_DYE, 1),
                new Ingredient(PlayerInventory.RED_DYE, 2),
                new Ingredient(PlayerInventory.WHITE_DYE, 1));
        register("light_blue_dye", PlayerInventory.LIGHT_BLUE_DYE, 2,
                new Ingredient(PlayerInventory.BLUE_DYE, 1),
                new Ingredient(PlayerInventory.WHITE_DYE, 1));
        register("lime_dye", PlayerInventory.LIME_DYE, 2,
                new Ingredient(PlayerInventory.GREEN_DYE, 1),
                new Ingredient(PlayerInventory.WHITE_DYE, 1));
        register("pink_dye", PlayerInventory.PINK_DYE, 2,
                new Ingredient(PlayerInventory.RED_DYE, 1),
                new Ingredient(PlayerInventory.WHITE_DYE, 1));
        register("gray_dye", PlayerInventory.GRAY_DYE, 2,
                new Ingredient(PlayerInventory.BLACK_DYE, 1),
                new Ingredient(PlayerInventory.WHITE_DYE, 1));
        register("light_gray_dye", PlayerInventory.LIGHT_GRAY_DYE, 2,
                new Ingredient(PlayerInventory.GRAY_DYE, 1),
                new Ingredient(PlayerInventory.WHITE_DYE, 1));
        register("light_gray_dye_from_black_white", PlayerInventory.LIGHT_GRAY_DYE, 3,
                new Ingredient(PlayerInventory.BLACK_DYE, 1),
                new Ingredient(PlayerInventory.WHITE_DYE, 2));
        register("cyan_dye", PlayerInventory.CYAN_DYE, 2,
                new Ingredient(PlayerInventory.BLUE_DYE, 1),
                new Ingredient(PlayerInventory.GREEN_DYE, 1));
        register("purple_dye", PlayerInventory.PURPLE_DYE, 2,
                new Ingredient(PlayerInventory.BLUE_DYE, 1),
                new Ingredient(PlayerInventory.RED_DYE, 1));

        // [CHEST-FAMILY] 상자류 컨테이너 재료. 둘 다 **바닐라 원본 그대로**의 무형 제작이고
        // 재료를 대체하지 않는다(docs/research/mc-26x-chests.md §3).
        //   blaze_powder: 블레이즈 막대 1 → 2. 막대의 획득 경로는 이미 양조대가 연 것과 같다.
        //   ender_eye: 엔더 진주 1 + 블레이즈 가루 1 → 1. 엔드가 없어 던질 곳은 없고 유일한
        //              소비처가 엔더 상자지만, 제작식만은 바닐라 그대로 둔다.
        register("blaze_powder", PlayerInventory.BLAZE_POWDER, 2,
                new Ingredient(PlayerInventory.BLAZE_ROD, 1));
        register("ender_eye", PlayerInventory.EYE_OF_ENDER, 1,
                new Ingredient(PlayerInventory.ENDER_PEARL, 1),
                new Ingredient(PlayerInventory.BLAZE_POWDER, 1));

        // ── [COOKING] 요리 계열 제작법(MC Java) ────────────────────────────────
        //
        // 근거 고정본은 docs/research/mc-food-cooking.md §2 다(등급 [B]).
        // **재료를 대체하지 않는다.** 없던 재료는 코코아 콩 하나뿐이고 그것은 아이템으로
        // 새로 등록해 탐험 전리품 획득 경로를 신설했다(Blocks.COCOA_BEANS 가 근거 소유).
        // 우유 양동이·달걀·설탕·밀·호박·버섯·그릇·꽃은 전부 이미 있는 재료다.
        //
        // 갈색 염료(코코아 콩 1)는 [DYE-SOURCE] 절이 이미 "brown_dye" 로 등록했으므로
        // 여기서 다시 적지 않는다 — 같은 산출을 두 id 로 두면 레시피 북에 두 번 뜬다.
        //
        // 케이크는 이 저장소에서 **처음으로 crafting remainder 가 있는 재료**(우유 양동이)를
        // 쓴다. 빈 양동이 3개가 격자에 남는 바닐라 동작은 InventoryRules.craftingRemainder 와
        // PlayerInventory.consumeCraftingIngredients 가 함께 소유한다.
        registerShaped("cake", PlayerInventory.CAKE, 1,
                shape(PlayerInventory.MILK_BUCKET, PlayerInventory.MILK_BUCKET, PlayerInventory.MILK_BUCKET,
                        PlayerInventory.SUGAR, PlayerInventory.EGG, PlayerInventory.SUGAR,
                        PlayerInventory.WHEAT, PlayerInventory.WHEAT, PlayerInventory.WHEAT),
                new Ingredient(PlayerInventory.MILK_BUCKET, 3),
                new Ingredient(PlayerInventory.SUGAR, 2),
                new Ingredient(PlayerInventory.EGG, 1),
                new Ingredient(PlayerInventory.WHEAT, 3));
        // 쿠키는 밀 2 + 코코아 콩 1 가로 한 줄에 산출 8 이다(바닐라 그대로).
        registerShaped("cookie", PlayerInventory.COOKIE, 8,
                shape(PlayerInventory.WHEAT, PlayerInventory.COCOA_BEANS, PlayerInventory.WHEAT,
                        0, 0, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.WHEAT, 2),
                new Ingredient(PlayerInventory.COCOA_BEANS, 1));
        // 호박 파이는 무형 3칸이라 개인 2×2 격자에서도 만들어진다(바닐라도 그렇다).
        register("pumpkin_pie", PlayerInventory.PUMPKIN_PIE, 1,
                new Ingredient((short) Blocks.PUMPKIN, 1),
                new Ingredient(PlayerInventory.SUGAR, 1),
                new Ingredient(PlayerInventory.EGG, 1));
        // 수상한 스튜는 바닐라에서 꽃 종류를 NBT 로 들고 다니지만 이 저장소의 스택에는 NBT 가
        // 없다 — 물약이 이미 쓰는 "효과마다 아이템 ID" 선례를 그대로 따라 꽃마다 레시피와
        // 산출 ID 를 나눈다. 재료 구성(그릇+갈색 버섯+붉은 버섯+작은 꽃)은 바닐라 그대로다.
        for (int flower : new int[]{Blocks.OPEN_EYEBLOSSOM, Blocks.CLOSED_EYEBLOSSOM}) {
            register(flower == Blocks.OPEN_EYEBLOSSOM ? "suspicious_stew_open_eyeblossom" : "suspicious_stew_closed_eyeblossom",
                    PlayerInventory.SUSPICIOUS_STEW_POPPY, 1,
                    new Ingredient(PlayerInventory.BOWL, 1),
                    new Ingredient((short) Blocks.MUSHROOM_BROWN, 1),
                    new Ingredient((short) Blocks.MUSHROOM_RED, 1), new Ingredient((short) flower, 1));
        }
        register("suspicious_stew_poppy", PlayerInventory.SUSPICIOUS_STEW_POPPY, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient((short) Blocks.MUSHROOM_BROWN, 1),
                new Ingredient((short) Blocks.MUSHROOM_RED, 1),
                new Ingredient((short) Blocks.FLOWER_RED, 1));
        register("suspicious_stew_dandelion", PlayerInventory.SUSPICIOUS_STEW_DANDELION, 1,
                new Ingredient(PlayerInventory.BOWL, 1),
                new Ingredient((short) Blocks.MUSHROOM_BROWN, 1),
                new Ingredient((short) Blocks.MUSHROOM_RED, 1),
                new Ingredient((short) Blocks.FLOWER_YELLOW, 1));
        // 말린 다시마는 제작이 아니라 제련이다 — 산출·분류의 정본은 FurnaceRules 의 FOOD 항
        // (다시마 → 말린 다시마)이고, 레시피 북에 뜨도록 여기에도 제련 레시피를 등록한다.
        registerSmelt("dried_kelp", PlayerInventory.DRIED_KELP, 1,
                new Ingredient((short) Blocks.KELP, 1));

        // ── [SHELF-FUNGUS-WOOL-SLAB] 양털 반 블록 16색 · 선반 8수종 ────────────────
        //
        // 양털 반 블록: **같은 색 양털 3 → 같은 색 반 블록 6** [B]. 배치는 바닐라 반 블록
        // 표준(가로 한 줄 3칸)이고 기존 판자·조약돌 반 블록과 모양이 정확히 같다.
        // 양털은 **색 정확 일치**다 — 침대·쿠션과 같은 이유로 태그가 아니라 색마다 별도
        // 레시피여야 다른 색 양털이 이 색 반 블록으로 흘러가지 않는다.
        //
        // **절단기 레시피는 등록하지 않는다** — 바닐라 절단은 돌 계열 재료에만 있고, 26.3 이
        // 양털·콘크리트 계단/반 블록을 추가하면서도 절단 레시피를 주지 않았다(핀 §4.2).
        // 아래 registerDerivedStonecutting() 은 석재 계열만 훑으므로 자동으로 새지 않는다.
        for (int color = 0; color < Blocks.WOOL_BY_DYE_COLOR.length; color++) {
            short wool = (short) Blocks.WOOL_BY_DYE_COLOR[color];
            short woolStairs = (short) Blocks.WOOL_STAIRS_BY_DYE_COLOR[color];
            registerShaped(DYE_COLOR_NAMES[color] + "_wool_stairs", woolStairs, 4,
                    shape(wool, 0, 0, wool, wool, 0, wool, wool, wool), new Ingredient(wool, 6));
            short concrete = (short) Blocks.CONCRETE_BY_COLOR[color];
            registerShaped(DYE_COLOR_NAMES[color] + "_concrete_stairs", (short) (Blocks.WHITE_CONCRETE_STAIRS + color), 4,
                    shape(concrete, 0, 0, concrete, concrete, 0, concrete, concrete, concrete), new Ingredient(concrete, 6));
            registerShaped(DYE_COLOR_NAMES[color] + "_concrete_slab", (short) (Blocks.WHITE_CONCRETE_SLAB + color), 6,
                    shape(concrete, concrete, concrete, 0, 0, 0, 0, 0, 0), new Ingredient(concrete, 3));
            short woolSlab = (short) (Blocks.WHITE_WOOL_SLAB + color);
            registerShaped(DYE_COLOR_NAMES[color] + "_wool_slab", woolSlab, 6,
                    shape(wool, wool, wool, 0, 0, 0, 0, 0, 0),
                    new Ingredient(wool, 3));
        }

        // 선반: pinned 26.3 recipe JSON의 위/아래 두 줄, 같은 수종 벗긴 원목 6 → 선반 6.
        //
        // 벗긴 원목은 **수종 정확 일치**다(바닐라도 수종마다 별도 레시피다). 축 변형은 재료로
        // 받지 않는다 — 아이템으로 들고 있는 벗긴 원목은 언제나 y축 대표 ID 이기 때문이다.
        for (int species = 0; species < Blocks.SHELF_BY_WOOD_SPECIES.length; species++) {
            short strippedLog = (short) Blocks.STRIPPED_LOG_BY_SPECIES_AND_AXIS[species * 3];
            short shelf = (short) Blocks.SHELF_BY_WOOD_SPECIES[species];
            registerShaped(WOOD_SPECIES_NAMES[species] + "_shelf", shelf, 6,
                    shape(strippedLog, strippedLog, strippedLog, 0, 0, 0,
                            strippedLog, strippedLog, strippedLog),
                    new Ingredient(strippedLog, 6));
        }

        // ── [HARNESS] 하네스 16색 ──────────────────────────────────────────────────
        //
        // [B] minecraft.wiki «Harness» 원문 배치 그대로다:
        //   윗줄   가죽   가죽     가죽
        //   가운뎃줄 유리 <그 색 양털> 유리
        //   아랫줄  (비움)
        // → 그 색 하네스 1. **재료를 하나도 신설하지 않는다** — 가죽 334 · 유리 10 · 색 양털
        // 561~576 이 전부 이미 있다(원본 레시피 무결성).
        //
        // 양털은 **색 정확 일치**다. 침대·쿠션·양털 반 블록과 같은 이유로, 태그로 받으면
        // 다른 색 양털이 이 색 하네스로 흘러가 색마다의 레시피가 무의미해진다
        // (RecipeCatalog.recipeAcceptsItem 의 `dye_` 예외가 여기에 닿지 않는 것이 그 계약이다).
        //
        // 유리는 **무색 유리 정확 일치**다 — 바닐라도 색 유리를 받지 않는다.
        // 재염색 레시피는 없다(바닐라도 하네스 재염색이 없고, 색을 바꾸려면 다시 만든다).
        for (int color = 0; color < Blocks.HARNESS_BY_DYE_COLOR.length; color++) {
            short wool = (short) Blocks.WOOL_BY_DYE_COLOR[color];
            short harness = (short) Blocks.HARNESS_BY_DYE_COLOR[color];
            registerShaped(DYE_COLOR_NAMES[color] + "_harness", harness, 1,
                    shape(PlayerInventory.LEATHER, PlayerInventory.LEATHER,
                            PlayerInventory.LEATHER,
                            (short) Blocks.GLASS, wool, (short) Blocks.GLASS,
                            0, 0, 0),
                    new Ingredient(PlayerInventory.LEATHER, 3),
                    new Ingredient((short) Blocks.GLASS, 2),
                    new Ingredient(wool, 1));
        }

        // [VOID-END] 핀 26.3 data/minecraft/recipe 원문 그대로다(재료 대체 없음).
        //   end_stone_bricks: end_stone 2×2 → 4 · end_stone_brick_{stairs,slab,wall}: 벽돌 6/3/6 → 4/6/6
        //   purpur_block: popped_chorus_fruit 2×2 → 4 · purpur_{stairs,slab}: #[purpur_block, purpur_pillar]
        //   6/3 → 4/6 (acceptsItem 이 기둥을 같은 칸에 받는다) · purpur_pillar: purpur_slab 세로 2 → 1
        //   end_rod: blaze_rod 위 · popped_chorus_fruit 아래 → 4 · popped_chorus_fruit: chorus_fruit 제련.
        // 절단기 간선(end_stone → 벽돌·계단·반 블록·담장, purpur_block → 기둥·계단·반 블록)은 아래
        // 파생 절단표가 이 형상 정본에서 그대로 낸다.
        registerQuarry("end_stone_bricks", (short) Blocks.END_STONE, (short) Blocks.END_STONE_BRICKS);
        registerDeepslateShapes("end_stone_brick", (short) Blocks.END_STONE_BRICKS,
                (short) Blocks.END_STONE_BRICK_STAIRS, (short) Blocks.END_STONE_BRICK_SLAB,
                (short) Blocks.END_STONE_BRICK_WALL);
        registerQuarry("purpur_block", PlayerInventory.POPPED_CHORUS_FRUIT, (short) Blocks.PURPUR_BLOCK);
        registerStairsAndSlab("purpur", (short) Blocks.PURPUR_BLOCK,
                (short) Blocks.PURPUR_STAIRS, (short) Blocks.PURPUR_SLAB);
        registerChiseled("purpur_pillar", (short) Blocks.PURPUR_SLAB, (short) Blocks.PURPUR_PILLAR);
        registerShaped("end_rod", (short) Blocks.END_ROD, 4,
                shape(PlayerInventory.BLAZE_ROD, 0, 0, PlayerInventory.POPPED_CHORUS_FRUIT, 0, 0, 0, 0, 0),
                new Ingredient(PlayerInventory.BLAZE_ROD, 1),
                new Ingredient(PlayerInventory.POPPED_CHORUS_FRUIT, 1));
        registerSmelt("popped_chorus_fruit", PlayerInventory.POPPED_CHORUS_FRUIT, 1,
                new Ingredient(PlayerInventory.CHORUS_FRUIT, 1));

        // [STONECUT] 절단표는 위 형상 정본 전부가 등록된 **뒤에** 파생한다. 이 한 줄이
        // 절단 카테고리의 유일한 등록 지점이며, 새 석재 계열이 붙으면 자동으로 넓어진다.
        registerDerivedStonecutting();

        ALL = List.copyOf(BY_ID.values());
    }

    /** 생성 매니페스트와 레시피 단위 검증에서 ID로 등록 항목을 찾습니다. */
    public static CraftRecipe byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 매니페스트 생성과 검증에 쓰는 변경 불가능한 전체 레시피 목록입니다(등록 순서 유지). */
    public static List<CraftRecipe> all() {
        return ALL;
    }

    /**
     * 실제 2×2/3×3 입력 칸의 배치로 결과를 찾습니다. 입력 스택의 개수는 한 칸에서 반복 제작할 수
     * 있는 횟수이고, 레시피 한 번을 맞출 때는 차지한 칸마다 아이템 한 개를 사용합니다.
     */
    public static CraftRecipe match(short[] grid, int gridSize) {
        return match(grid, gridSize, false);
    }

    /**
     * [POTION] {@code brewing} 이면 양조 레시피만, 아니면 양조가 아닌 레시피만 맞춥니다.
     * 두 목록이 겹치지 않으므로 양조대에서 막대가 나오거나 작업대에서 물약이 나올 수 없습니다.
     */
    public static CraftRecipe match(short[] grid, int gridSize, boolean brewing) {
        if (grid == null || (gridSize != 2 && gridSize != 3)
                || grid.length < gridSize * gridSize) {
            return null;
        }
        short[] visibleGrid = grid.length == gridSize * gridSize
                ? grid : Arrays.copyOf(grid, gridSize * gridSize);
        if (!brewing && gridSize == 3 && matchesPotIngredients(visibleGrid)) {
            boolean same = visibleGrid[1] == visibleGrid[3] && visibleGrid[1] == visibleGrid[5]
                    && visibleGrid[1] == visibleGrid[7];
            int pot = same ? com.gameexpert.engine.ArchaeologyRules.decoratedPotForIngredient(visibleGrid[1]) : -1;
            if (pot < 0) {
            pot = Blocks.DECORATED_POT;
            return new CraftRecipe("decorated_pot_components", java.util.stream.IntStream.of(1, 3, 5, 7).mapToObj(i -> new Ingredient(visibleGrid[i], 1)).toList(), (short) pot, 1,
                    java.util.stream.IntStream.range(0, 9).mapToObj(i -> visibleGrid[i]).toList(),
                    false, true, false, false);
            }
        }
        for (CraftRecipe recipe : ALL) {
            if (recipe.requiresBrewing != brewing) continue;
            // [STONECUT] 절단 레시피는 격자만으로 결정되지 않는다(같은 입력에서 여러 산출).
            // 격자 경로에서는 절대 맞지 않고, 오직 절단 세션의 선택으로만 성립한다.
            if (recipe.requiresStonecutter) continue;
            if (recipe.requiresFurnace || recipe.requiresTable && gridSize != 3) continue;
            if (recipe.matchesGrid(visibleGrid, gridSize)) return recipe;
        }
        if (brewing) return null;
        CraftRecipe mapClone = matchMapClone(visibleGrid, gridSize);
        if (mapClone != null) return mapClone;
        CraftRecipe dye = matchLeatherDye(visibleGrid, gridSize);
        return dye != null ? dye : matchShieldDecoration(visibleGrid, gridSize);
    }

    public static final String MAP_CLONING = "map_cloning";

    private static CraftRecipe matchMapClone(short[] grid, int gridSize) {
        short source = PlayerInventory.EMPTY;
        int blanks = 0;
        for (int slot = 0; slot < gridSize * gridSize; slot++) {
            short type = grid[slot];
            if (type == PlayerInventory.EMPTY) continue;
            if (type == PlayerInventory.MAP) blanks++;
            else if (PlayerInventory.isFilledMapItem(type) && source == PlayerInventory.EMPTY) source = type;
            else return null;
        }
        if (source == PlayerInventory.EMPTY || blanks == 0) return null;
        return new CraftRecipe(MAP_CLONING, List.of(), source, blanks + 1, List.of(),
                false, false, false, false);
    }

    private static boolean matchesPotIngredients(short[] grid) {
        for (int slot = 0; slot < 9; slot++) {
            boolean face = slot == 1 || slot == 3 || slot == 5 || slot == 7;
            if (face ? grid[slot] != PlayerInventory.BRICK && !Blocks.isPotterySherd(grid[slot])
                    : grid[slot] != PlayerInventory.EMPTY) return false;
        }
        return true;
    }

    /** [SHIELD-PATTERN] 방패 장식 특수 레시피 id. */
    public static final String SHIELD_DECORATION = "shield_decoration";

    /**
     * [SHIELD-PATTERN] 바닐라 {@code crafting_special_shielddecoration}
     * ({@code ShieldDecorationRecipe}): 격자에 방패 하나와 현수막 하나만 있으면 성립한다. 방패에
     * 이미 무늬가 있으면 거부하는 판정은 스택 구성요소를 보는 {@code PlayerInventory} 가 한다.
     */
    private static CraftRecipe matchShieldDecoration(short[] grid, int gridSize) {
        int shields = 0;
        int banners = 0;
        for (int slot = 0; slot < gridSize * gridSize; slot++) {
            short type = grid[slot];
            if (type == PlayerInventory.EMPTY) continue;
            if (type == PlayerInventory.SHIELD) shields++;
            else if (com.gameexpert.terrain.Blocks.isBanner(Short.toUnsignedInt(type))) banners++;
            else return null;
        }
        if (shields != 1 || banners != 1) return null;
        return new CraftRecipe(SHIELD_DECORATION, List.of(), PlayerInventory.SHIELD, 1, List.of(),
                false, false, false, false);
    }

    private static CraftRecipe matchLeatherDye(short[] grid, int gridSize) {
        short leather = PlayerInventory.EMPTY;
        int dyes = 0;
        int occupied = 0;
        for (int slot = 0; slot < gridSize * gridSize; slot++) {
            short type = grid[slot];
            if (type == PlayerInventory.EMPTY) continue;
            occupied++;
            if (ItemComponentCodec.isDyeableLeather(type)) {
                if (leather != PlayerInventory.EMPTY) return null;
                leather = type;
            } else if (type >= PlayerInventory.WHITE_DYE
                    && type <= PlayerInventory.BLACK_DYE) dyes++;
            else return null;
        }
        if (leather == PlayerInventory.EMPTY || dyes == 0) return null;
        return new CraftRecipe("dye_leather", List.of(), leather, 1, List.of(), false,
                occupied > 4, false, false);
    }

    /**
     * [STONECUT] 절단 세션의 권위 판정. 클라가 고른 산출이 지금 입력 칸에서 실제로
     * 유도되는지 표로 다시 확인한다 — 선택 인덱스는 신뢰하지 않는다.
     *
     * @param input     절단기 입력 칸의 아이템 종류(빈 칸이면 항상 null)
     * @param recipeId  클라가 고른 절단 레시피 id
     * @return 유도 가능하면 그 레시피, 아니면 null(권위가 거부)
     */
    public static CraftRecipe matchStonecutting(short input, String recipeId) {
        if (input == PlayerInventory.EMPTY || recipeId == null) return null;
        CraftRecipe recipe = BY_ID.get(recipeId);
        if (recipe == null || !recipe.requiresStonecutter) return null;
        if (recipe.inputs.size() != 1) return null;
        return recipe.acceptsItem(recipe.inputs.get(0).itemType, input) ? recipe : null;
    }

    /** [STONECUT] 입력 블록 하나에서 절단기로 얻을 수 있는 산출 전량(등록 순서 유지). */
    public static List<CraftRecipe> stonecuttingFor(short input) {
        if (input == PlayerInventory.EMPTY) return List.of();
        List<CraftRecipe> matches = new ArrayList<>();
        for (CraftRecipe recipe : ALL) {
            if (!recipe.requiresStonecutter) continue;
            if (recipe.acceptsItem(recipe.inputs.get(0).itemType, input)) matches.add(recipe);
        }
        return List.copyOf(matches);
    }

    private boolean matchesGrid(short[] grid, int gridSize) {
        if (pattern.isEmpty()) return matchesShapeless(grid, gridSize);
        CompactShape placed = compact(grid, gridSize);
        CompactShape required = compact(pattern, patternWidth(pattern.size()));
        if (placed == null || required == null
                || placed.width != required.width || placed.height != required.height) {
            return false;
        }
        boolean direct = true;
        boolean mirrored = true;
        for (int y = 0; y < required.height; y++) {
            for (int x = 0; x < required.width; x++) {
                short actual = placed.cells[y * placed.width + x];
                short expected = required.cells[y * required.width + x];
                if (!acceptsItem(expected, actual)) direct = false;
                short mirroredExpected =
                        required.cells[y * required.width + required.width - 1 - x];
                if (!acceptsItem(mirroredExpected, actual)) mirrored = false;
            }
        }
        return direct || mirrored;
    }

    private boolean matchesShapeless(short[] grid, int gridSize) {
        int occupied = 0;
        for (int slot = 0; slot < gridSize * gridSize; slot++) {
            if (grid[slot] != PlayerInventory.EMPTY) occupied++;
        }
        int requiredCount = inputs.stream().mapToInt(Ingredient::count).sum();
        if (occupied != requiredCount) return false;

        int[] remaining = inputs.stream().mapToInt(Ingredient::count).toArray();
        for (int slot = 0; slot < gridSize * gridSize; slot++) {
            short actual = grid[slot];
            if (actual == PlayerInventory.EMPTY) continue;
            boolean consumed = false;
            for (int ingredient = 0; ingredient < inputs.size(); ingredient++) {
                if (remaining[ingredient] > 0
                        && acceptsItem(inputs.get(ingredient).itemType, actual)) {
                    remaining[ingredient]--;
                    consumed = true;
                    break;
                }
            }
            if (!consumed) return false;
        }
        for (int count : remaining) if (count != 0) return false;
        return true;
    }

    /**
     * 바닐라 {@code #minecraft:planks} 태그를 쓰지 <b>않는</b> 레시피들. 목재 형상 여섯 종은
     * 바닐라도 수종 정확 일치라 참나무 레시피가 다른 수종 판자를 삼키면 안 된다
     * (그러면 자작 판자 3개가 참나무 반 블록이 되어 종별 세트를 만들 수 없다).
     */
    private static final java.util.Set<String> OAK_EXACT_SHAPE_RECIPES = java.util.Set.of(
            "wood_stairs", "plank_slab", "wood_fence", "wood_fence_gate", "wood_trapdoor", "door");

    boolean acceptsItem(short expected, short actual) {
        if (expected == (short) Blocks.WHITE_CUSHION
                && id.startsWith("dye_") && id.endsWith("_cushion")) {
            return Blocks.isCushion(actual) && actual != outputType;
        }
        if (expected == actual) return true;
        // 바닐라 #planks 태그. 판자를 요구하는 일반 레시피(막대·작업대·상자·도구 …)는 어느
        // 수종이든 받는다. 목재 형상 여섯 종만 수종 정확 일치다.
        if (expected == (short) Blocks.PLANK) {
            return Blocks.isPlankBlock(actual) && !OAK_EXACT_SHAPE_RECIPES.contains(id);
        }
        // [WOOL-COLOR] 바닐라 양털 염색의 재료는 #minecraft:wool 태그다. 표에는 대표값
        // WHITE_WOOL 하나만 적고 dye_* 레시피에서만 16색 전부를 받는다.
        // [BED-COLOR] 침대는 이제 색마다 자기 레시피가 있으므로(<color>_bed) 총칭 "bed" 는
        // 무색 양털 264 만 받는다. 색 양털이 총칭 침대로 흘러가면 색 침대를 만들 수 없다.
        if (expected == (short) Blocks.WHITE_WOOL && id.startsWith("dye_")) {
            return actual >= (short) Blocks.WHITE_WOOL && actual <= (short) Blocks.BLACK_WOOL;
        }
        // [ENDER-SHULKER] 바닐라 <color>_shulker_box 의 재료 태그는 #minecraft:shulker_boxes
        // 라 **이미 염색된 상자도 다시 염색된다**(색을 바꾸려고 껍데기를 다시 모을 필요가
        // 없다 [B]). 표에는 대표값 SHULKER_BOX 하나만 적고, 확장을 "_shulker_box" 로 끝나는
        // 염색 레시피로만 좁힌다 — 셜커 상자를 재료로 쓰는 다른 레시피(있게 되면)까지 조용히
        // 따라 넓어지지 않게 하는 것은 양털·통나무 태그 확장과 같은 규율이다.
        if (expected == (short) Blocks.SHULKER_BOX && id.endsWith("_shulker_box")) {
            return Blocks.isShulkerBox(actual);
        }
        // Snapshot-7 dye_<color>_cushion accepts the cushions tag except the result's own color.
        // Accepting the result color would create a no-op recolor absent from the official recipe.
        // [SHELF-FUNGUS-WOOL-SLAB] 선반버섯은 스튜 레시피에서 **아무 버섯을 대체한다** [B].
        // 대체를 스튜 두 계열로 좁히는 이유는 양털·통나무 태그 확장과 같다 — 재료를 태그처럼
        // 넓히면 버섯을 쓰는 다른 레시피(있게 되면)까지 조용히 따라 넓어진다.
        // [VOID-END] 바닐라 purpur_stairs·purpur_slab 의 재료 칸은 [purpur_block, purpur_pillar]
        // 목록이다. 표에는 대표값 보라 블록 하나만 적고 이 두 레시피에서만 기둥을 받는다.
        if (expected == (short) Blocks.PURPUR_BLOCK && actual == (short) Blocks.PURPUR_PILLAR
                && (id.equals("purpur_stairs") || id.equals("purpur_slab"))) {
            return true;
        }
        if ((expected == (short) Blocks.MUSHROOM_BROWN || expected == (short) Blocks.MUSHROOM_RED)
                && (actual == (short) Blocks.MUSHROOM_BROWN || actual == (short) Blocks.MUSHROOM_RED
                    || actual == (short) Blocks.SHELF_MUSHROOM)
                && (id.startsWith("mushroom_stew") || id.startsWith("suspicious_stew"))) {
            return true;
        }
        // 통나무 계열 확장은 판자 제작에는 적용하지 않는다. 수종별 판자가 생기면서
        // "planks*" 레시피는 각자 자기 통나무 하나만 받아야 하기 때문이다.
        if (expected == (short) Blocks.LOG && !id.startsWith("planks")) {
            return actual == (short) Blocks.BIRCH_LOG
                    || actual == (short) Blocks.SPRUCE_LOG
                    || actual == (short) Blocks.JUNGLE_LOG
                    || actual == (short) Blocks.ACACIA_LOG
                    || actual == (short) Blocks.DARK_OAK_LOG
                    || actual == (short) Blocks.CHERRY_LOG
                    || actual == (short) Blocks.MANGROVE_LOG
                    // [PALE-GARDEN] 창백한 참나무 통나무도 바닐라 #minecraft:logs 다.
                    || actual == (short) Blocks.PALE_OAK_LOG
                    // [POPLAR] 포플러 통나무도 같은 태그다.
                    || actual == (short) Blocks.POPLAR_LOG;
        }
        // [STRIPPED-LOG] 바닐라 판자 레시피 재료는 수종별 {@code #minecraft:<species>_logs} 태그라
        // 통나무와 **그 수종의 벗긴 원목**을 함께 받는다(산출은 같은 판자 4). 태그가 수종 단위라
        // 다른 수종으로 새지 않으므로 판정도 "기대한 통나무의 벗긴 짝인가" 하나면 된다.
        if (id.startsWith("planks") && Blocks.isStrippedLog(actual)
                && Blocks.LOG_BY_WOOD_SPECIES[Blocks.strippedLogSpecies(actual)] == expected) {
            return true;
        }
        // [PALE-GARDEN] 창백한 참나무는 여덟 수종 표 밖이라 위 산술이 닿지 않는다.
        // 같은 태그 규칙(그 수종의 벗긴 원목도 판자 4 를 낸다)을 명시 갈래로 적는다.
        if (id.equals("planks_pale_oak") && expected == (short) Blocks.PALE_OAK_LOG
                && Blocks.isStrippedPaleOakLog(actual)) {
            return true;
        }
        // [POPLAR] 공식 #poplar_logs 태그는 log·wood·stripped_log·stripped_wood 네 형식이다.
        if (id.equals("planks_poplar") && expected == (short) Blocks.POPLAR_LOG
                && Blocks.isPoplarLogsTag(actual)) {
            return true;
        }
        // [CREAKING] 크리킹 하트의 재료는 바닐라에서 {@code #minecraft:pale_oak_logs} 태그라
        // 통나무 · 벗긴 원목이 모두 들어간다(축 변형 포함). 판자 레시피와 달리 이쪽은 수종
        // 하나에 묶인 태그가 아니라 <b>같은 수종의 원목 계열 전부</b>이므로 갈래를 따로 적는다.
        if (id.equals("creaking_heart") && expected == (short) Blocks.PALE_OAK_LOG
                && (Blocks.isPaleOakLog(actual) || Blocks.isStrippedPaleOakLog(actual))) {
            return true;
        }
        if (expected == PlayerInventory.COAL && actual == PlayerInventory.CHARCOAL) {
            return id.equals("torch") || id.equals("campfire");
        }
        if (expected == (short) Blocks.COBBLE
                && actual == (short) Blocks.COBBLED_DEEPSLATE) {
            return id.equals("furnace") || id.startsWith("stone_");
        }
        return id.equals("tnt")
                && expected == (short) Blocks.SAND
                && actual == (short) Blocks.RED_SAND;
    }

    /**
     * 레시피 북이 사용하는 정방향 기본 배치를 현재 격자 크기로 변환합니다.
     * 3×3 전용·제련 레시피는 개인 2×2 또는 제작 격자에서 배치할 수 없습니다.
     */
    short[] expectedCells(int gridSize) {
        return expectedCells(gridSize, false);
    }

    /** [POTION] 양조 세션 여부까지 맞아야 배치를 돌려준다. */
    short[] expectedCells(int gridSize, boolean brewing) {
        if ((gridSize != 2 && gridSize != 3)
                || requiresBrewing != brewing
                // [STONECUT] 절단 레시피는 격자에 놓는 배치 자체가 없다(입력 한 칸 + 선택).
                || requiresStonecutter
                || requiresFurnace || requiresTable && gridSize != 3) {
            return null;
        }
        short[] result = new short[gridSize * gridSize];
        if (pattern.isEmpty()) {
            int output = 0;
            for (Ingredient ingredient : inputs) {
                for (int count = 0; count < ingredient.count; count++) {
                    if (output >= result.length) return null;
                    result[output++] = ingredient.itemType;
                }
            }
            return result;
        }
        CompactShape shape = compact(pattern, patternWidth(pattern.size()));
        if (shape == null || shape.width > gridSize || shape.height > gridSize) return null;
        int offsetX = (gridSize - shape.width) / 2;
        int offsetY = (gridSize - shape.height) / 2;
        for (int y = 0; y < shape.height; y++) {
            for (int x = 0; x < shape.width; x++) {
                result[(y + offsetY) * gridSize + x + offsetX] =
                        shape.cells[y * shape.width + x];
            }
        }
        return result;
    }

    private static int patternWidth(int cells) {
        int width = (int) Math.sqrt(cells);
        if (width * width != cells) {
            throw new IllegalStateException("crafting pattern must be a square row-major grid");
        }
        return width;
    }

    private static CompactShape compact(List<Short> values, int width) {
        short[] cells = new short[values.size()];
        for (int index = 0; index < values.size(); index++) cells[index] = values.get(index);
        return compact(cells, width);
    }

    private static CompactShape compact(short[] values, int width) {
        int height = values.length / width;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int index = 0; index < values.length; index++) {
            if (values[index] == PlayerInventory.EMPTY) continue;
            int x = index % width;
            int y = index / width;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        if (maxX < 0) return null;
        int compactWidth = maxX - minX + 1;
        int compactHeight = maxY - minY + 1;
        short[] cells = new short[compactWidth * compactHeight];
        int output = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                cells[output++] = values[y * width + x];
            }
        }
        return new CompactShape(compactWidth, compactHeight, cells);
    }

    private static final class CompactShape {
        private final int width;
        private final int height;
        private final short[] cells;

        private CompactShape(int width, int height, short[] cells) {
            this.width = width;
            this.height = height;
            this.cells = cells;
        }
    }
}
