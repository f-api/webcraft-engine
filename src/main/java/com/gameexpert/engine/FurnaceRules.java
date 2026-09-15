package com.gameexpert.engine;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** 화로의 제련표와 연료 시간 계약. */
public final class FurnaceRules {

    /** 연료량의 최소 단위. 1 = 아이템 0.5개 제련분이라 판자 1.5도 반올림 없이 표현된다. */
    public static final int HALF_ITEM_UNITS_PER_SMELT = 2;
    /** 이 프로젝트의 10 TPS에서 아이템 하나를 제련하는 10초. */
    public static final int TICKS_PER_SMELT = 100;

    /**
     * 제련표 한 줄이 어느 바닐라 레시피 종류에 속하는가. 이 분류가 변형별 입력 필터의
     * <b>유일한</b> 원천이다({@link FurnaceVariant#accepts}).
     *
     * <p>바닐라 1.21.4 는 같은 사실을 레시피 파일 세 종류({@code smelting} / {@code blasting} /
     * {@code smoking})로 표현한다. 한 입력이 blasting 에도 있으면 용광로가, smoking 에도 있으면
     * 훈연기가 받는다. 여기서는 표가 하나뿐이라 줄마다 분류를 달아 같은 분할을 만든다.
     */
    public enum SmeltCategory {
        /** 광석·원석. 바닐라 blasting 레시피가 존재하는 입력이다. */
        ORE,
        /** 음식. 바닐라 smoking 레시피가 존재하는 입력이다. */
        FOOD,
        /** 그 밖(모래·조약돌·돌·통나무). 바닐라에서 화로에만 있는 smelting 전용 레시피다. */
        MISC,
    }

    private static final Map<Short, Short> SMELT_OUTPUTS = new LinkedHashMap<>();
    private static final Map<Short, SmeltCategory> SMELT_CATEGORIES = new LinkedHashMap<>();

    static {
        // ORE — 바닐라 blasting 레시피가 있는 입력. 용광로가 받는 전부다.
        registerSmelt(PlayerInventory.RAW_IRON, PlayerInventory.IRON_INGOT, SmeltCategory.ORE);
        registerSmelt(PlayerInventory.RAW_GOLD, PlayerInventory.GOLD_INGOT, SmeltCategory.ORE);
        registerSmelt(PlayerInventory.RAW_COPPER, PlayerInventory.COPPER_INGOT, SmeltCategory.ORE);
        registerSmelt((short) Blocks.EMERALD_ORE, PlayerInventory.EMERALD, SmeltCategory.ORE);
        registerSmelt((short) Blocks.LAPIS_ORE, PlayerInventory.LAPIS_LAZULI, SmeltCategory.ORE);
        registerSmelt((short) Blocks.REDSTONE_ORE, PlayerInventory.REDSTONE_DUST, SmeltCategory.ORE);
        registerSmelt((short) Blocks.ANCIENT_DEBRIS, PlayerInventory.NETHERITE_SCRAP,
                SmeltCategory.ORE);
        // [MC-263-SULFUR] 옛 창작 유황 광석 · 심층암 유황 광석 → 유황 가루 두 줄은 공식 26.3
        // 유황 계열로 대체되며 사라졌다(광석도 가루도 26.3 에 없다). ID 는 되쓰지 않고
        // Blocks.REMOVED_SULFUR_1301~1310 묘비로만 남는다.
        // MISC — 바닐라에 blasting/smoking 대응이 없는 smelting 전용. 화로만 받는다.
        registerSmelt((short) Blocks.SAND, (short) Blocks.GLASS, SmeltCategory.MISC);
        registerSmelt((short) Blocks.COBBLE, (short) Blocks.STONE, SmeltCategory.MISC);
        // [VILLAGER-STATION] 바닐라 smooth_stone: 돌을 한 번 더 제련한 산출물.
        registerSmelt((short) Blocks.STONE, (short) Blocks.SMOOTH_STONE, SmeltCategory.MISC);
        // [VOID-END] 바닐라 popped_chorus_fruit.json 은 smelting 전용(category misc)이다.
        registerSmelt(PlayerInventory.CHORUS_FRUIT, PlayerInventory.POPPED_CHORUS_FRUIT, SmeltCategory.MISC);
        // [QUARTZ] 바닐라 smooth_quartz.json 은 smelting 전용이라 화로만 받는다.
        registerSmelt((short) Blocks.QUARTZ_BLOCK, (short) Blocks.SMOOTH_QUARTZ,
                SmeltCategory.MISC);
        // [CONCRETE] 점토 블록 → 테라코타, 색 테라코타 → 같은 색 유광 테라코타.
        // 바닐라 terracotta.json · *_glazed_terracotta.json 은 전부 smelting 전용이라
        // 용광로·훈연기가 받지 않는다(MISC). 색 16줄은 CraftRecipe 카탈로그와 반드시 같아야
        // 하며 그 전체 일치는 FurnaceCraftSmeltParityTest 가 고정한다.
        registerSmelt((short) Blocks.CLAY, (short) Blocks.TERRACOTTA, SmeltCategory.MISC);
        for (int color = 0; color < Blocks.TERRACOTTA_BY_COLOR.length; color++) {
            registerSmelt((short) Blocks.TERRACOTTA_BY_COLOR[color],
                    (short) Blocks.GLAZED_TERRACOTTA_BY_COLOR[color], SmeltCategory.MISC);
        }
        // 제련 전용 산출 일곱. CraftRecipe 카탈로그(정적판 정본)에는 있었는데 이 표에 빠져
        // 온라인 화로만 제련하지 못하던 권위 분기를 닫는다 — 두 표의 전체 일치는
        // FurnaceCraftSmeltParityTest 가 고정한다.
        registerSmelt((short) Blocks.DEEPSLATE_BRICKS, (short) Blocks.CRACKED_DEEPSLATE_BRICKS,
                SmeltCategory.MISC);
        registerSmelt((short) Blocks.DEEPSLATE_TILES, (short) Blocks.CRACKED_DEEPSLATE_TILES,
                SmeltCategory.MISC);
        registerSmelt((short) Blocks.STONE_BRICK, (short) Blocks.CRACKED_STONE_BRICKS,
                SmeltCategory.MISC);
        registerSmelt((short) Blocks.SANDSTONE, (short) Blocks.SMOOTH_SANDSTONE,
                SmeltCategory.MISC);
        registerSmelt((short) Blocks.RED_SANDSTONE, (short) Blocks.SMOOTH_RED_SANDSTONE,
                SmeltCategory.MISC);
        registerSmelt(PlayerInventory.CLAY_BALL, (short) Blocks.BRICK, SmeltCategory.MISC);
        registerSmelt((short) Blocks.WET_SPONGE, (short) Blocks.SPONGE, SmeltCategory.MISC);
        // [ARMOR-TRIM] 바닐라 recipe/resin_brick.json 은 smelting 전용(blasting 없음)이라 MISC.
        registerSmelt(PlayerInventory.RESIN_CLUMP, PlayerInventory.RESIN_BRICK, SmeltCategory.MISC);
        registerSmelt((short) Blocks.LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.BIRCH_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.SPRUCE_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.JUNGLE_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.ACACIA_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.DARK_OAK_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.CHERRY_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.MANGROVE_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        // [PALE-GARDEN] 창백한 참나무도 #minecraft:logs_that_burn 이라 숯이 된다. 축 변형은
        // 인벤토리에 들어오지 않으므로(y축 대표 ID 로 접힌다) 통나무·벗긴 원목 두 줄이면 된다.
        registerSmelt((short) Blocks.PALE_OAK_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.STRIPPED_PALE_OAK_LOG, PlayerInventory.CHARCOAL,
                SmeltCategory.MISC);
        // [POPLAR] 26.3 포플러도 #minecraft:logs_that_burn 이라 숯이 된다. 같은 이유로
        // 통나무·벗긴 원목 두 줄이면 된다.
        registerSmelt((short) Blocks.POPLAR_LOG, PlayerInventory.CHARCOAL, SmeltCategory.MISC);
        registerSmelt((short) Blocks.STRIPPED_POPLAR_LOG, PlayerInventory.CHARCOAL,
                SmeltCategory.MISC);
        // [STRIPPED-LOG] 바닐라 charcoal 제련 입력은 #minecraft:logs_that_burn 태그라 벗긴
        // 원목도 그대로 숯이 된다. 축 변형은 인벤토리에 들어오지 않으므로(축은 y축 대표 ID 로
        // 접힌다) 수종당 한 줄이면 충분하다.
        for (int species = 0; species < Blocks.LOG_BY_WOOD_SPECIES.length; species++) {
            registerSmelt((short) Blocks.strippedLog(species, 0), PlayerInventory.CHARCOAL,
                    SmeltCategory.MISC);
        }
        // 염료 제련 둘. 바닐라 green_dye.json(선인장 → 초록 염료) 과
        // lime_dye_from_sea_pickle.json(바다 수세미 → 연두 염료) 은 둘 다 recipe type 이
        // minecraft:smelting 뿐이라(blasting·smoking 대응 없음) 분류는 MISC 이고 화로만 받는다.
        // CraftRecipe 카탈로그에는 있었는데 이 표에 없어 온라인 화로만 두 염료를 제련하지
        // 못하던 권위 분기를 닫는다.
        registerSmelt((short) Blocks.CACTUS, PlayerInventory.GREEN_DYE, SmeltCategory.MISC);
        registerSmelt((short) Blocks.SEA_PICKLE, PlayerInventory.LIME_DYE, SmeltCategory.MISC);
        // [PROP-MATERIAL] 금속 조각 회수 제련(바닐라 *_nugget_from_smelting). 분류는 ORE 다 —
        // 바닐라에 같은 입력의 blasting 레시피가 있어 용광로가 받기 때문이고, 이 저장소의
        // 변형 필터가 보는 값이 이 분류 하나다.
        for (short iron : new short[] {
                PlayerInventory.IRON_PICKAXE, PlayerInventory.IRON_AXE,
                PlayerInventory.IRON_SHOVEL, PlayerInventory.IRON_SWORD,
                PlayerInventory.IRON_SPEAR,
                PlayerInventory.IRON_HELMET, PlayerInventory.IRON_CHESTPLATE,
                PlayerInventory.IRON_LEGGINGS, PlayerInventory.IRON_BOOTS,
                PlayerInventory.CHAINMAIL_HELMET, PlayerInventory.CHAINMAIL_CHESTPLATE,
                PlayerInventory.CHAINMAIL_LEGGINGS, PlayerInventory.CHAINMAIL_BOOTS }) {
            registerSmelt(iron, PlayerInventory.IRON_NUGGET, SmeltCategory.ORE);
        }
        for (short gold : new short[] {
                PlayerInventory.GOLD_PICKAXE, PlayerInventory.GOLD_AXE,
                PlayerInventory.GOLD_SHOVEL, PlayerInventory.GOLD_SWORD,
                PlayerInventory.GOLD_SPEAR,
                PlayerInventory.GOLD_HELMET, PlayerInventory.GOLD_CHESTPLATE,
                PlayerInventory.GOLD_LEGGINGS, PlayerInventory.GOLD_BOOTS }) {
            registerSmelt(gold, PlayerInventory.GOLD_NUGGET, SmeltCategory.ORE);
        }
        // [COPPER-AGE] 1.21.9 의 copper_nugget_from_{smelting,blasting} 태그는 구리
        // 도구 다섯, 방어구 네 부위, 말 갑옷만 받는다. 창과 방패는 이 공식 입력 집합에
        // 없으므로 프로젝트에 아이템이 존재하더라도 회수 제련에 섞지 않는다.
        for (short copper : new short[] {
                PlayerInventory.COPPER_PICKAXE, PlayerInventory.COPPER_SHOVEL,
                PlayerInventory.COPPER_AXE, PlayerInventory.COPPER_HOE,
                PlayerInventory.COPPER_SWORD, PlayerInventory.COPPER_HELMET,
                PlayerInventory.COPPER_CHESTPLATE, PlayerInventory.COPPER_LEGGINGS,
                PlayerInventory.COPPER_BOOTS, PlayerInventory.COPPER_HORSE_ARMOR }) {
            registerSmelt(copper, PlayerInventory.COPPER_NUGGET, SmeltCategory.ORE);
        }
        // FOOD — 바닐라 smoking 레시피가 있는 입력. 훈연기가 받는 전부다.
        registerSmelt(PlayerInventory.BEEF_RAW, PlayerInventory.BEEF_COOKED, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.PORK_RAW, PlayerInventory.PORK_COOKED, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.MUTTON_RAW, PlayerInventory.MUTTON_COOKED, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.CHICKEN_RAW, PlayerInventory.CHICKEN_COOKED, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.RABBIT_RAW, PlayerInventory.RABBIT_COOKED, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.POTATO, PlayerInventory.BAKED_POTATO, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.COD_RAW, PlayerInventory.COD_COOKED, SmeltCategory.FOOD);
        registerSmelt(PlayerInventory.SALMON_RAW, PlayerInventory.SALMON_COOKED, SmeltCategory.FOOD);
        // [COOKING] 다시마 → 말린 다시마. 바닐라 smoking 레시피 9종 중 유일하게 빠져 있던
        // 항이라 훈연기가 받는 집합이 이제 바닐라와 원소가 같다
        // (docs/research/mc-food-cooking.md §5). 입력이 **월드 블록**인 유일한 FOOD 항이다.
        registerSmelt((short) Blocks.KELP, PlayerInventory.DRIED_KELP, SmeltCategory.FOOD);
    }

    private FurnaceRules() {
    }

    private static void registerSmelt(short input, short output, SmeltCategory category) {
        SMELT_OUTPUTS.put(input, output);
        SMELT_CATEGORIES.put(input, category);
    }

    /** 제련 가능한 입력의 산출물. 등록되지 않은 입력이면 0. 변형 무관한 표 전체 조회다. */
    public static short smeltOutput(short input) {
        return SMELT_OUTPUTS.getOrDefault(input, (short) 0);
    }

    /** 입력의 제련 분류. 등록되지 않은 입력이면 {@code null}. */
    public static SmeltCategory smeltCategory(short input) {
        return SMELT_CATEGORIES.get(input);
    }

    /**
     * 이 변형이 실제로 제련해 내는 산출물. 표에 있어도 변형이 받지 않는 분류면 0이다.
     * 용광로에 소고기를, 훈연기에 원철을 넣지 못하게 하는 판정의 정본이다.
     */
    public static short smeltOutput(FurnaceVariant variant, short input) {
        if (variant == null) return 0;
        return variant.accepts(smeltCategory(input)) ? smeltOutput(input) : 0;
    }

    /** 읽기 전용 제련표(입력 → 산출). */
    public static Map<Short, Short> smeltRecipes() {
        return Map.copyOf(SMELT_OUTPUTS);
    }

    public static boolean isSmeltOutput(short itemType) {
        return SMELT_OUTPUTS.containsValue(itemType);
    }

    /**
     * 연료 한 개의 제련량을 0.5개 단위로 반환한다. 석탄·숯=16(8개), 석탄 블록=160(80개),
     * 통나무·판자=3(1.5개), 막대=1(0.5개).
     * 연료가 아니면 0이다.
     */
    public static int fuelHalfItemUnits(short itemType) {
        if (itemType == PlayerInventory.COAL || itemType == PlayerInventory.CHARCOAL) {
            return 8 * HALF_ITEM_UNITS_PER_SMELT;
        }
        if (itemType == Blocks.COAL_BLOCK) return 80 * HALF_ITEM_UNITS_PER_SMELT;
        if (itemType == Blocks.LOG || itemType == Blocks.BIRCH_LOG
                || itemType == Blocks.SPRUCE_LOG || itemType == Blocks.JUNGLE_LOG
                || itemType == Blocks.ACACIA_LOG || itemType == Blocks.DARK_OAK_LOG
                || itemType == Blocks.CHERRY_LOG || itemType == Blocks.MANGROVE_LOG
                // [STRIPPED-LOG] 바닐라 연료 태그도 #logs_that_burn 이라 벗긴 원목이 같은 300틱이다.
                || Blocks.isStrippedLog(itemType)
                // [PALE-GARDEN] 창백한 참나무 통나무·벗긴 원목도 같은 300틱 연료다.
                || itemType == Blocks.PALE_OAK_LOG
                || itemType == Blocks.STRIPPED_PALE_OAK_LOG
                // [POPLAR] 포플러 통나무·벗긴 원목도 같은 300틱 연료다.
                || itemType == Blocks.POPLAR_LOG
                || itemType == Blocks.STRIPPED_POPLAR_LOG
                || itemType == Blocks.POPLAR_WOOD
                || itemType == Blocks.STRIPPED_POPLAR_WOOD) {
            return 3;
        }
        if (itemType == Blocks.PLANK || itemType == Blocks.PALE_OAK_PLANK
                || itemType == Blocks.POPLAR_PLANK) {
            return 3 * HALF_ITEM_UNITS_PER_SMELT / 2;
        }
        if (itemType == PlayerInventory.STICK) return HALF_ITEM_UNITS_PER_SMELT / 2;
        return 0;
    }

    /** 연료 한 개의 프로젝트 틱 수. 석탄/숯 800, 석탄 블록 8000, 통나무/판자 150, 막대 50틱입니다. */
    public static int fuelTicks(short itemType) {
        return fuelHalfItemUnits(itemType) * TICKS_PER_SMELT / HALF_ITEM_UNITS_PER_SMELT;
    }

    /**
     * 제련로(화로·용광로·훈연기)인가. 미점화 및 점화 렌더 상태를 같은 블록으로 취급합니다.
     *
     * <p>변형 판정은 {@link FurnaceVariant#of(int)} 한 곳만 쓴다 — 세 변형 여섯 ID 를 손으로
     * 나열하는 사본이 하나라도 생기면 새 변형이 한쪽에서만 제련로로 보이게 된다.
     */
    public static boolean isFurnace(int id) {
        return FurnaceVariant.of(id) != null;
    }
}
