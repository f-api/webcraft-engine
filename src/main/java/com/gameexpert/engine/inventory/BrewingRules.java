package com.gameexpert.engine.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.effect.PotionCatalog;
import com.gameexpert.engine.effect.PotionCatalog.Contents;
import com.gameexpert.engine.effect.PotionCatalog.Form;
import com.gameexpert.terrain.Blocks;

/**
 * [BREWING-26.3] 양조대 규칙 정본(순수 함수). 바닐라 26.3-snapshot-7 의 양조는 데이터 주도다:
 * {@code data/minecraft/recipe/brewing/*.json} 279 개(물약 혼합 63 × 형태 3 + 화약 45 + 드래곤의
 * 숨결 45)가 {@code BrewingRecipe(input: PotionIngredient, reagent, output: ItemStackTemplate)} 로
 * 등록되고 {@code BrewingStandBlockEntity} 가 병 칸마다 {@code BrewingInput(병, 재료)} 로 찾는다.
 *
 * <p>이 클래스는 그 표를 <b>(형태, 물약 키) 모델</b>로 들고 있다 — 혼합 63 줄은 세 형태에 똑같이
 * 걸리고, 형태 변환 두 줄(화약: 물약 → 투척, 드래곤의 숨결: 투척 → 잔류)은 행운을 뺀 45 키에만
 * 걸린다. {@link #recipes()} 가 그 전개(279)이고 {@code BrewingRulesJarParityTest} 가 고정 jar 의
 * JSON 279 개와 한 줄씩 대조한다(원본 묶음 SHA-256
 * {@code 9671a1f96ebfc0b16efa69d867d6a755f3d5e2d3f2ff26ee7914a92e6a927605}: 파일명 정렬 순서로
 * {@code 이름 \0 원문 \0} 을 이어 해시).
 *
 * <p>입력 병은 {@link PotionCatalog#resolve} 로 (형태, 키)가 되고, 산출은
 * {@link PotionCatalog#canonicalItemType} · {@link PotionCatalog#canonicalPotionContents} 로
 * 정규화된다(전용 ID 가 있는 쌍은 전용 ID, 나머지는 {@code CONTENTS_*} + 컴포넌트). 바닐라
 * {@code BrewingRecipe.assemble} 은 {@code output.create()} 라 입력 병의 다른 컴포넌트(이름 등)를
 * 넘기지 않는다 — 산출 스택은 물약 컴포넌트만 싣는다.
 *
 * <p>칸 규칙({@code BrewingStandBlockEntity.canPlaceItem} · {@code BrewingStandMenu}): 연료 칸은
 * {@code BREWING_FUEL} 컴포넌트(26.3 에서 블레이즈 가루 하나, 사용 20 · 속도 배율 1.0), 재료
 * 칸은 {@code RecipePropertySet.BREWING_REAGENTS}(모든 레시피의 재료 아이템), 병 칸은
 * {@code BREWING_INPUTS}(물약 세 형태 아이템 — 아이템만 보고 내용물은 보지 않는다).
 */
public final class BrewingRules {

    /** {@code BrewingStandBlockEntity}: {@code ceil(400 / speedMultiplier)} MC 틱, 기본 배율 1.0. */
    public static final int BREW_TIME_TICKS = 400;
    /** {@code number_provider/brewing/uses_default.json} = 20. */
    public static final int BREWS_PER_BLAZE_POWDER = 20;

    /** 혼합 한 줄(세 형태 공통). */
    public static final class Mix {
        private final String input;
        private final short reagent;
        private final String output;

        public Mix(String input, short reagent, String output) {
            this.input = input;
            this.reagent = reagent;
            this.output = output;
        }

        public String input() { return input; }
        public short reagent() { return reagent; }
        public String output() { return output; }
    }

    /** 전개된 바닐라 레시피 한 줄({@code recipe/brewing/<id>.json}). */
    public static final class Recipe {
        private final String id;
        private final Form inputForm;
        private final String inputKey;
        private final short reagent;
        private final Form outputForm;
        private final String outputKey;

        public Recipe(String id, Form inputForm, String inputKey, short reagent,
                Form outputForm, String outputKey) {
            this.id = id;
            this.inputForm = inputForm;
            this.inputKey = inputKey;
            this.reagent = reagent;
            this.outputForm = outputForm;
            this.outputKey = outputKey;
        }

        public String id() { return id; }
        public Form inputForm() { return inputForm; }
        public String inputKey() { return inputKey; }
        public short reagent() { return reagent; }
        public Form outputForm() { return outputForm; }
        public String outputKey() { return outputKey; }
    }

    private static final List<Mix> MIXES = new ArrayList<>();
    private static final Map<Long, String> MIX_OUTPUT = new HashMap<>();
    private static final Set<Short> REAGENTS = new LinkedHashSet<>();
    private static final List<Recipe> RECIPES;

    private static void mix(String input, short reagent, String output) {
        if (!PotionCatalog.isKey(input) || !PotionCatalog.isKey(output)) {
            throw new IllegalStateException("unknown brewing potion " + input + " -> " + output);
        }
        MIXES.add(new Mix(input, reagent, output));
        if (MIX_OUTPUT.put(mixKey(input, reagent), output) != null) {
            throw new IllegalStateException("ambiguous brewing mix " + input + "+" + reagent);
        }
        REAGENTS.add(reagent);
    }

    private static long mixKey(String input, short reagent) {
        return (long) PotionCatalog.keys().indexOf(input) << 16 | Short.toUnsignedInt(reagent);
    }

    static {
        // ── recipe/brewing/potion_*.json 의 혼합 63 줄(파일명 정렬 순서). 세 형태가 같다. ──
        mix("awkward", PlayerInventory.BLAZE_POWDER, "strength");
        mix("awkward", PlayerInventory.BREEZE_ROD, "wind_charged");
        mix("awkward", (short) Blocks.COBWEB, "weaving");
        mix("awkward", PlayerInventory.GHAST_TEAR, "regeneration");
        mix("awkward", PlayerInventory.GLISTERING_MELON_SLICE, "healing");
        mix("awkward", PlayerInventory.GOLDEN_CARROT, "night_vision");
        mix("awkward", PlayerInventory.MAGMA_CREAM, "fire_resistance");
        mix("awkward", PlayerInventory.PHANTOM_MEMBRANE, "slow_falling");
        mix("awkward", PlayerInventory.PUFFERFISH, "water_breathing");
        mix("awkward", PlayerInventory.RABBIT_FOOT, "leaping");
        mix("awkward", (short) Blocks.SLIME_BLOCK, "oozing");
        mix("awkward", PlayerInventory.SPIDER_EYE, "poison");
        mix("awkward", (short) Blocks.STONE, "infested");
        mix("awkward", PlayerInventory.SUGAR, "swiftness");
        // turtle_helmet 은 이 저장소의 거북 등딱지 1932 다.
        mix("awkward", PlayerInventory.TURTLE_SHELL, "turtle_master");
        mix("fire_resistance", PlayerInventory.REDSTONE_DUST, "long_fire_resistance");
        mix("harming", PlayerInventory.GLOWSTONE_DUST, "strong_harming");
        mix("healing", PlayerInventory.FERMENTED_SPIDER_EYE, "harming");
        mix("healing", PlayerInventory.GLOWSTONE_DUST, "strong_healing");
        mix("invisibility", PlayerInventory.REDSTONE_DUST, "long_invisibility");
        mix("leaping", PlayerInventory.FERMENTED_SPIDER_EYE, "slowness");
        mix("leaping", PlayerInventory.GLOWSTONE_DUST, "strong_leaping");
        mix("leaping", PlayerInventory.REDSTONE_DUST, "long_leaping");
        mix("long_leaping", PlayerInventory.FERMENTED_SPIDER_EYE, "long_slowness");
        mix("long_night_vision", PlayerInventory.FERMENTED_SPIDER_EYE, "long_invisibility");
        mix("long_poison", PlayerInventory.FERMENTED_SPIDER_EYE, "harming");
        mix("long_swiftness", PlayerInventory.FERMENTED_SPIDER_EYE, "long_slowness");
        mix("night_vision", PlayerInventory.FERMENTED_SPIDER_EYE, "invisibility");
        mix("night_vision", PlayerInventory.REDSTONE_DUST, "long_night_vision");
        mix("poison", PlayerInventory.FERMENTED_SPIDER_EYE, "harming");
        mix("poison", PlayerInventory.GLOWSTONE_DUST, "strong_poison");
        mix("poison", PlayerInventory.REDSTONE_DUST, "long_poison");
        mix("regeneration", PlayerInventory.GLOWSTONE_DUST, "strong_regeneration");
        mix("regeneration", PlayerInventory.REDSTONE_DUST, "long_regeneration");
        mix("slow_falling", PlayerInventory.REDSTONE_DUST, "long_slow_falling");
        mix("slowness", PlayerInventory.GLOWSTONE_DUST, "strong_slowness");
        mix("slowness", PlayerInventory.REDSTONE_DUST, "long_slowness");
        mix("strength", PlayerInventory.GLOWSTONE_DUST, "strong_strength");
        mix("strength", PlayerInventory.REDSTONE_DUST, "long_strength");
        mix("strong_healing", PlayerInventory.FERMENTED_SPIDER_EYE, "strong_harming");
        mix("strong_poison", PlayerInventory.FERMENTED_SPIDER_EYE, "strong_harming");
        mix("swiftness", PlayerInventory.FERMENTED_SPIDER_EYE, "slowness");
        mix("swiftness", PlayerInventory.GLOWSTONE_DUST, "strong_swiftness");
        mix("swiftness", PlayerInventory.REDSTONE_DUST, "long_swiftness");
        mix("turtle_master", PlayerInventory.GLOWSTONE_DUST, "strong_turtle_master");
        mix("turtle_master", PlayerInventory.REDSTONE_DUST, "long_turtle_master");
        mix("water", PlayerInventory.BLAZE_POWDER, "mundane");
        mix("water_breathing", PlayerInventory.REDSTONE_DUST, "long_water_breathing");
        mix("water", PlayerInventory.BREEZE_ROD, "mundane");
        mix("water", (short) Blocks.COBWEB, "mundane");
        mix("water", PlayerInventory.FERMENTED_SPIDER_EYE, "weakness");
        mix("water", PlayerInventory.GHAST_TEAR, "mundane");
        mix("water", PlayerInventory.GLISTERING_MELON_SLICE, "mundane");
        mix("water", PlayerInventory.GLOWSTONE_DUST, "thick");
        mix("water", PlayerInventory.MAGMA_CREAM, "mundane");
        mix("water", PlayerInventory.NETHER_WART, "awkward");
        mix("water", PlayerInventory.RABBIT_FOOT, "mundane");
        mix("water", PlayerInventory.REDSTONE_DUST, "mundane");
        mix("water", (short) Blocks.SLIME_BLOCK, "mundane");
        mix("water", PlayerInventory.SPIDER_EYE, "mundane");
        mix("water", (short) Blocks.STONE, "mundane");
        mix("water", PlayerInventory.SUGAR, "mundane");
        mix("weakness", PlayerInventory.REDSTONE_DUST, "long_weakness");
        REAGENTS.add(PlayerInventory.GUNPOWDER);
        REAGENTS.add(PlayerInventory.DRAGON_BREATH);

        List<Recipe> recipes = new ArrayList<>();
        for (Form form : Form.values()) {
            for (Mix mix : MIXES) {
                recipes.add(new Recipe(form.itemKey() + "_" + mix.input() + "_"
                        + reagentKey(mix.reagent()), form, mix.input(), mix.reagent(),
                        form, mix.output()));
            }
        }
        for (String key : PotionCatalog.keys()) {
            if (!convertible(key)) continue;
            recipes.add(new Recipe("potion_" + key + "_gunpowder", Form.POTION, key,
                    PlayerInventory.GUNPOWDER, Form.SPLASH, key));
            recipes.add(new Recipe("splash_potion_" + key + "_dragon_breath", Form.SPLASH, key,
                    PlayerInventory.DRAGON_BREATH, Form.LINGERING, key));
        }
        RECIPES = List.copyOf(recipes);
    }

    private BrewingRules() {}

    /**
     * 형태 변환(화약 · 드래곤의 숨결)이 걸리는 키. 바닐라 {@code VanillaBrewingProvider} 는
     * 양조로 얻을 수 없는 행운({@code luck})에만 변환 레시피를 만들지 않는다(26.3 jar 45 키).
     */
    public static boolean convertible(String key) {
        return PotionCatalog.isKey(key) && !"luck".equals(key);
    }

    /** 레시피 id 에 쓰는 바닐라 재료 아이템 경로. */
    public static String reagentKey(short reagent) {
        if (reagent == PlayerInventory.BLAZE_POWDER) return "blaze_powder";
        if (reagent == PlayerInventory.BREEZE_ROD) return "breeze_rod";
        if (reagent == (short) Blocks.COBWEB) return "cobweb";
        if (reagent == PlayerInventory.GHAST_TEAR) return "ghast_tear";
        if (reagent == PlayerInventory.GLISTERING_MELON_SLICE) return "glistering_melon_slice";
        if (reagent == PlayerInventory.GOLDEN_CARROT) return "golden_carrot";
        if (reagent == PlayerInventory.MAGMA_CREAM) return "magma_cream";
        if (reagent == PlayerInventory.PHANTOM_MEMBRANE) return "phantom_membrane";
        if (reagent == PlayerInventory.PUFFERFISH) return "pufferfish";
        if (reagent == PlayerInventory.RABBIT_FOOT) return "rabbit_foot";
        if (reagent == (short) Blocks.SLIME_BLOCK) return "slime_block";
        if (reagent == PlayerInventory.SPIDER_EYE) return "spider_eye";
        if (reagent == (short) Blocks.STONE) return "stone";
        if (reagent == PlayerInventory.SUGAR) return "sugar";
        if (reagent == PlayerInventory.TURTLE_SHELL) return "turtle_helmet";
        if (reagent == PlayerInventory.REDSTONE_DUST) return "redstone";
        if (reagent == PlayerInventory.GLOWSTONE_DUST) return "glowstone_dust";
        if (reagent == PlayerInventory.FERMENTED_SPIDER_EYE) return "fermented_spider_eye";
        if (reagent == PlayerInventory.NETHER_WART) return "nether_wart";
        if (reagent == PlayerInventory.GUNPOWDER) return "gunpowder";
        if (reagent == PlayerInventory.DRAGON_BREATH) return "dragon_breath";
        throw new IllegalArgumentException("not a brewing reagent " + reagent);
    }

    /** 혼합 63 줄(세 형태 공통). */
    public static List<Mix> mixes() {
        return List.copyOf(MIXES);
    }

    /** 전개된 바닐라 레시피 279 줄. */
    public static List<Recipe> recipes() {
        return RECIPES;
    }

    /** (형태, 키) 병에 재료 하나를 넣었을 때의 산출. 레시피가 없으면 null. */
    public static Contents output(Contents input, short reagent) {
        if (input == null) return null;
        if (reagent == PlayerInventory.GUNPOWDER) {
            return input.form() == Form.POTION && convertible(input.key())
                    ? new Contents(Form.SPLASH, input.key()) : null;
        }
        if (reagent == PlayerInventory.DRAGON_BREATH) {
            return input.form() == Form.SPLASH && convertible(input.key())
                    ? new Contents(Form.LINGERING, input.key()) : null;
        }
        String output = MIX_OUTPUT.get(mixKey(input.key(), reagent));
        return output == null ? null : new Contents(input.form(), output);
    }

    /** 병 스택(아이템 + 성분 문자열)과 재료의 산출. 레시피가 없으면 null. */
    public static Contents output(short bottleType, String bottleComponentData, short reagent) {
        return output(PotionCatalog.resolve(bottleType,
                potionContents(bottleType, bottleComponentData)), reagent);
    }

    /** 성분 문자열에서 물약 키만 꺼낸다(없거나 해석 불가면 null). */
    public static String potionContents(short itemType, String componentData) {
        if (componentData == null || !PotionCatalog.isContentsItem(itemType)) return null;
        try {
            return ItemComponentCodec.decode(itemType, componentData).potionContents();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** 산출 (형태, 키)의 정본 성분 문자열(전용 ID 쌍이면 null). */
    public static String canonicalComponentData(Contents contents) {
        String key = PotionCatalog.canonicalPotionContents(contents.form(), contents.key());
        if (key == null) return null;
        short item = PotionCatalog.canonicalItemType(contents.form(), contents.key());
        return ItemComponentCodec.encode(item, ItemComponentData.EMPTY.withPotionContents(key));
    }

    /** 병 칸({@code BREWING_INPUTS}): 물약 세 형태의 아이템이면 내용물과 무관하게 받는다. */
    public static boolean isBottle(short itemType) {
        return itemType == PlayerInventory.GLASS_BOTTLE || PotionCatalog.isPotionItem(itemType);
    }

    /** 재료 칸({@code BREWING_REAGENTS}): 279 레시피 중 하나의 재료. */
    public static boolean isIngredient(short itemType) {
        return REAGENTS.contains(itemType);
    }

    /** 재료 아이템 전량(21 종). */
    public static Set<Short> reagents() {
        return Set.copyOf(REAGENTS);
    }

    /** 연료 칸: {@code BREWING_FUEL} 을 가진 아이템(26.3 은 블레이즈 가루뿐). */
    public static boolean isFuel(short itemType) {
        return itemType == PlayerInventory.BLAZE_POWDER;
    }

    /** 병 칸 셋 중 하나라도 이 재료로 양조되는가({@code isBrewable}). */
    public static boolean canBrew(short ingredient, short[] bottleTypes, String[] bottleComponents) {
        if (ingredient == PlayerInventory.EMPTY || !isIngredient(ingredient)) return false;
        for (int index = 0; index < bottleTypes.length; index++) {
            String components = bottleComponents == null ? null : bottleComponents[index];
            if (output(bottleTypes[index], components, ingredient) != null) return true;
        }
        return false;
    }
}
