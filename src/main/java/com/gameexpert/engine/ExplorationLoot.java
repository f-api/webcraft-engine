package com.gameexpert.engine;

import java.util.SplittableRandom;

import com.gameexpert.engine.ExplorationDecorator.Kind;
import com.gameexpert.engine.creaking.CreakingHeartRules;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.structure.RuinLootProfile;
import com.gameexpert.engine.structure.ShipwreckVariant;
import com.gameexpert.engine.structure.StructureHash;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.terrain.Blocks;

/** 자연 생성 상자의 첫 개방 시점에만 실행되는, 좌표·시드 결정론적 전리품 표. */
public final class ExplorationLoot {
    public static final class MundaneRoll {
        private final short itemType;
        private final int count;
        private final int durability;
        private final boolean jackpot;

        private MundaneRoll(short itemType, int count, int durability, boolean jackpot) {
            this.itemType = itemType;
            this.count = count;
            this.durability = durability;
            this.jackpot = jackpot;
        }

        public short itemType() { return itemType; }
        public int count() { return count; }
        public int durability() { return durability; }
        public boolean jackpot() { return jackpot; }
    }

    private static final class ItemOption {
        private final short type;
        private final int minimum;
        private final int maximum;

        private ItemOption(short type, int minimum, int maximum) {
            this.type = type;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        private boolean stackable() { return !PlayerInventory.isDurable(type); }
    }

    private static final int WEIGHT_SALT = 0x6a2f71d3;
    private static final int ITEM_SALT = 0x1c83a6e9;
    private static final int AMOUNT_SALT = 0x70b54c21;
    private static final int CONDITION_SALT = 0x3d197fa5;
    private static final int JACKPOT_SALT = 0x55e4b90f;
    private static final int SPECIAL_SALT = 0x5e91c27b;

    /** 목록은 모든 상자에서 고정하고 WEIGHT lane만 상자별 가중치를 흔든다. */
    private static final ItemOption[] MUNDANE = {
        option(PlayerInventory.ROTTEN_FLESH, 1, 3), option(PlayerInventory.BEEF_RAW, 1, 2),
        option(PlayerInventory.PORK_RAW, 1, 2), option(PlayerInventory.CHICKEN_COOKED, 1, 2),
        option(PlayerInventory.WHEAT, 1, 4), option(PlayerInventory.BREAD, 1, 2),
        option(PlayerInventory.WHEAT_SEEDS, 1, 5), option((short) Blocks.DIRT, 2, 8),
        option((short) Blocks.GRAVEL, 2, 8), option((short) Blocks.SUGARCANE, 1, 4),
        option(PlayerInventory.BONE, 1, 4), option(PlayerInventory.STRING, 1, 3),
        option(PlayerInventory.LEATHER, 1, 2), option(PlayerInventory.WOOL, 1, 2),
        option(PlayerInventory.OAK_SAPLING, 1, 1), option(PlayerInventory.BIRCH_SAPLING, 1, 1),
        armor(PlayerInventory.LEATHER_HELMET), armor(PlayerInventory.LEATHER_CHESTPLATE),
        armor(PlayerInventory.LEATHER_LEGGINGS), armor(PlayerInventory.LEATHER_BOOTS),
        armor(PlayerInventory.IRON_HELMET), armor(PlayerInventory.IRON_CHESTPLATE),
        armor(PlayerInventory.IRON_LEGGINGS), armor(PlayerInventory.IRON_BOOTS),
        armor(PlayerInventory.CHAINMAIL_HELMET), armor(PlayerInventory.CHAINMAIL_CHESTPLATE),
        armor(PlayerInventory.CHAINMAIL_LEGGINGS), armor(PlayerInventory.CHAINMAIL_BOOTS),
        option(PlayerInventory.COAL, 1, 5), option(PlayerInventory.IRON_INGOT, 1, 5),
        // [POTION] 양조 재료 두 종. 던전 상자에서만, 그것도 아주 낮은 가중치로 나온다.
        // append-only: 뒤에 붙여야 기존 슬롯 서수가 유지된다.
        option(PlayerInventory.BLAZE_ROD, 1, 1), option(PlayerInventory.NETHER_WART, 1, 2),
        // [QUARTZ] 네더 석영. 같은 이유(원천이 네더 전용)로 던전 상자에서만 나오지만
        // 건축 재료라 양조 재료보다 후하게 잡고 2~5 개씩 준다. append-only.
        option(PlayerInventory.QUARTZ, 2, 5),
        // [COPPER] 밀랍. 같은 이유(바닐라 원천인 벌이 이 게임에 없다)로 던전 상자에서만
        // 나온다. 한 상자로 블록 한 벌을 봉인할 만큼만 주려고 1~3 개다. append-only.
        option(PlayerInventory.HONEYCOMB, 1, 3),
        // [CHEST-FAMILY] 셜커 껍데기. 바닐라 원천(엔드 도시의 셜커)이 이 게임에 없어 신설한
        // 획득 경로다. 셜커 상자 하나가 껍데기 2 개라 1~2 개씩만 흘린다. append-only.
        option(PlayerInventory.SHULKER_SHELL, 1, 2),
        // [GOLD-FOOD] 황금 당근 · 후렴과 · 꿀이 든 병. append-only 라 앞선 슬롯 서수가
        // 유지된다. 황금 당근은 <b>이미 바닐라 제작이 되는</b> 아이템이고 여기는 추가
        // 경로일 뿐이라 가장 낮은 가중치다. 나머지 둘은 바닐라 원천(엔드 후렴화 · 벌집/
        // 벌집 상자)이 이 저장소에 없어 이 상자와 금고가 유일한 경로다.
        option(PlayerInventory.GOLDEN_CARROT, 1, 1),
        option(PlayerInventory.CHORUS_FRUIT, 1, 3),
        option(PlayerInventory.HONEY_BOTTLE, 1, 2),
        // [COOKING] 코코아 콩. 바닐라 원천(자연 생성 정글 통나무의 코코아 꼬투리)이 지형
        // 배치물이라 이 웨이브가 세울 수 없어 신설한 획득 경로다. append-only 라 앞선 슬롯
        // 서수가 유지된다. 쿠키 8개가 콩 1개라 1~3 개면 충분하다.
        option(PlayerInventory.COCOA_BEANS, 1, 3)
    };

    /**
     * [POTION] 던전 상자에서 블레이즈 막대가 갖는 기본 가중치. 바닐라에는 블레이즈 막대가 드는
     * 상자가 아예 없고(블레이즈 몹 드랍이 유일) 이 저장소에는 네더도 블레이즈도 없으므로,
     * 네더 사마귀 가중치의 절반보다도 낮게 잡아 "양조대 한 대 분량"만 흘린다.
     */
    public static final double BLAZE_ROD_DUNGEON_WEIGHT = 0.12;
    /**
     * [GOLD-FOOD] 던전 상자에서 황금 당근이 갖는 기본 가중치. 이 아이템은 <b>이미 바닐라
     * 제작이 되는</b> 유일한 항이라(당근 + 금 조각 8) 상자는 어디까지나 덤이다. 그래서 이
     * 표에서 가장 낮은 블레이즈 막대(0.12)와 같은 층에 둔다 — 상자당 약 1~2% 다.
     */
    public static final double GOLDEN_CARROT_DUNGEON_WEIGHT = 0.12;
    /**
     * [GOLD-FOOD] 던전 상자에서 후렴과가 갖는 기본 가중치. 바닐라 원천인 엔드 섬 후렴화가
     * 이 저장소에 없어 <b>이 상자가 유일한 경로</b>이고, 소비가 1회 1개로 잦은 이동 도구라
     * 네더 사마귀(0.30)와 같은 층에 둔다. 상자당 약 3~4% 다.
     */
    public static final double CHORUS_FRUIT_DUNGEON_WEIGHT = 0.30;
    /**
     * [GOLD-FOOD] 던전 상자에서 꿀이 든 병이 갖는 기본 가중치. 바닐라 원천(벌집/벌집 상자에
     * 유리병 사용)이 이 저장소에 없다. 트라이얼 금고가 또 다른 경로라 상자 쪽은 후렴과보다
     * 낮게 잡고, 독 해제라는 효용이 물약 재료급이라 블레이즈 막대보다는 높은 0.20 이다.
     */
    public static final double HONEY_BOTTLE_DUNGEON_WEIGHT = 0.20;
    /**
     * [COOKING] 던전 상자에서 코코아 콩이 갖는 기본 가중치. 바닐라 Java 의 유일한 원천은
     * 자연 생성 정글 통나무의 코코아 꼬투리인데 꼬투리는 지형 배치물이라 지형 레인 없이
     * 세울 수 없다(바닐라 26.x 는 버려진 야영지 상자에도 넣었다 — 성질이 같은 경로다).
     * 그래서 <b>이 상자가 유일한 경로</b>이고, 콩 1개가 쿠키 8개라 소비가 매우 느린 재료라서
     * 네더 사마귀(0.30)와 같은 층에 둔다. 상자당 약 3~4% 다.
     */
    public static final double COCOA_BEANS_DUNGEON_WEIGHT = 0.30;
    /**
     * [POTION] 던전 상자에서 네더 사마귀가 갖는 기본 가중치. 바닐라 요새(nether_bridge) 상자는
     * 사마귀 가중치 5 / 총합 73 ≈ 6.8%/roll · 2~4 roll 이라 상자당 약 19% 인데, 이 저장소의
     * 던전은 요새보다 훨씬 흔하므로 그 1/5 수준(상자당 약 3~4%)이 되도록 낮췄다.
     */
    public static final double NETHER_WART_DUNGEON_WEIGHT = 0.30;
    /**
     * [QUARTZ] 던전 상자에서 네더 석영이 갖는 기본 가중치. 바닐라 요새(nether_bridge) 상자는
     * 석영 가중치 5 / 총합 73 ≈ 6.8%/roll · 2~4 roll 이라 상자당 약 19% 이고, 여기에 더해
     * 요새 통로(bastion 계열은 이 저장소 범위 밖) 없이도 네더에서는 광석 채굴로 얼마든지
     * 얻는 <b>흔한 건축 재료</b>다. 그래서 사마귀(0.30)보다 후하게 잡되, 던전이 요새보다
     * 훨씬 흔한 이 저장소 사정을 감안해 요새값의 1/3 수준(상자당 약 6~7%)인 0.55 로 둔다.
     * 소비 사슬이 석영 4 → 블록 1 이라 한 상자로는 블록 한두 개밖에 나오지 않는다.
     */
    public static final double QUARTZ_DUNGEON_WEIGHT = 0.55;
    /**
     * [COPPER] 던전 상자에서 밀랍이 갖는 기본 가중치. 바닐라에는 밀랍이 드는 상자가 아예 없고
     * (벌집 채취가 유일) 이 저장소에는 벌도 벌집도 없으므로, 블레이즈 막대(0.12)와 같은
     * "한 대 분량만 흘린다" 계약을 쓰되 소비가 블록 하나당 1 개로 잦은 재료라 네더 사마귀
     * (0.30)와 같은 층에 둔다. 상자당 약 3~4% 다.
     */
    public static final double HONEYCOMB_DUNGEON_WEIGHT = 0.30;
    /**
     * [CHEST-FAMILY] 던전 상자에서 셜커 껍데기가 갖는 기본 가중치. 바닐라에는 껍데기가 드는
     * 상자가 아예 없고(엔드 도시 셜커 처치가 유일) 이 저장소에는 엔드도 셜커도 없다. 소비가
     * 셜커 상자 하나당 2 개로 무겁고 그 상자가 사실상 인벤토리 확장이므로, 블레이즈 막대
     * (0.12)와 같은 "한 대 분량만 흘린다" 층에 두되 그보다도 낮게 잡는다. 상자당 약 1~2% 다.
     */
    public static final double SHULKER_SHELL_DUNGEON_WEIGHT = 0.08;

    // ---- [SHIPWRECK] 바닐라 1.21.4 난파선 상자 3종 -------------------------------------
    //
    // 이 세 표만 공유 MUNDANE 풀을 쓰지 않고 바닐라 `loot_table/chests/shipwreck_*.json` 의
    // <b>항목·가중치·개수·roll 수</b>를 그대로 옮긴다. 원문은
    // `docs/research/mc-vanilla-1214/loot_table/chests/` 에 있고 요약은 같은 디렉터리의
    // `SHIPWRECK.md` §7 이다. wasm `client/wasm/src/mc_structure/shipwreck.rs` 가 같은 리터럴의
    // 사본이며 `StandaloneExplorationLootParity.test.ts` 가 두 사본을 비교한다.
    //
    // 이 저장소에 없는 품목은 <b>대체하지 않고 항만 제외</b>한다(사용자 확정 규칙). 제외분:
    // 부패한 감자·수상한 스튜(supply), 경험치병·철 조각(treasure), 탐험 지도(map),
    // 해안 갑옷 장식본(세 표 공통 마지막 pool). 남은 항목의 가중치는 바닐라 값 그대로라
    // 총합만 줄어든다 — 자세한 divergence 는 `docs/EXPLORATION-LOOT.md` 난파선 절에 적는다.

    /**
     * 바닐라 pool 항 하나. {@code enchantOptions} 는 그 항에 붙은 {@code enchant_randomly} 의
     * 후보 인챈트 키 목록(바닐라 태그 순서 그대로)이고, 붙지 않은 항은 {@code null} 이다.
     */
    private record VanillaEntry(short type, int weight, int minimum, int maximum,
            String[] enchantOptions) {}

    // ---- [LOOT-BOOKS] 탐험 상자의 마법이 부여된 책 --------------------------------------
    //
    // 바닐라 표의 책 항은 {@code minecraft:book} + {@code enchant_randomly} 다
    // (`chests/underwater_ruin_big`, `chests/woodland_mansion`, `chests/ancient_city`).
    // 바닐라 {@code EnchantRandomlyFunction} 은 대상이 책이면 호환 여부를 묻지 않고 후보 전부에서
    // 하나를 균등하게 고르고, 레벨은 {@code Mth.nextInt(random, 1, maxLevel)} 로 균등하게
    // 뽑은 뒤 책을 {@code enchanted_book} + {@code stored_enchantments} 로 바꾼다. 여기서는
    // 바닐라 난수열 대신 이 파일의 좌표 해시 lane 두 개(후보 서수 · 레벨)를 쓴다 — 분포는 같다
    // (후보 균등 · 레벨 균등). wasm {@code mc_structure/loot.rs} 가 같은 목록·salt 의 사본이고
    // {@code StandaloneExplorationLootParity.test.ts} 가 두 사본을 글자 그대로 비교한다.

    /**
     * 바닐라 26.3 {@code #minecraft:on_random_loot} 를 태그 순서 그대로 편 목록이다
     * ({@code #minecraft:non_treasure} 36종 뒤에 결속의 저주 · 소실의 저주 · 차가운 걸음 · 수선).
     */
    private static final String[] ON_RANDOM_LOOT_BOOK_OPTIONS = {
        "minecraft:protection", "minecraft:fire_protection", "minecraft:feather_falling",
        "minecraft:blast_protection", "minecraft:projectile_protection", "minecraft:respiration",
        "minecraft:aqua_affinity", "minecraft:thorns", "minecraft:depth_strider",
        "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
        "minecraft:knockback", "minecraft:fire_aspect", "minecraft:looting",
        "minecraft:sweeping_edge", "minecraft:efficiency", "minecraft:silk_touch",
        "minecraft:unbreaking", "minecraft:fortune", "minecraft:power", "minecraft:punch",
        "minecraft:flame", "minecraft:infinity", "minecraft:luck_of_the_sea", "minecraft:lure",
        "minecraft:loyalty", "minecraft:impaling", "minecraft:riptide", "minecraft:channeling",
        "minecraft:multishot", "minecraft:quick_charge", "minecraft:piercing",
        "minecraft:density", "minecraft:breach", "minecraft:lunge", "minecraft:binding_curse",
        "minecraft:vanishing_curse", "minecraft:frost_walker", "minecraft:mending"
    };

    /** 바닐라 `chests/ancient_city` 의 {@code enchant_randomly options minecraft:swift_sneak}. */
    private static final String[] SWIFT_SNEAK_BOOK_OPTIONS = {"minecraft:swift_sneak"};

    // 책 전용 lane 두 개. 기존 pool lane(항 선택 · 개수 · roll 수)과 다른 곱수·salt 라 기존
    // 굴림 결과는 비트 하나 달라지지 않는다.
    private static final int BOOK_ENCHANT_SALT = 0x424b_4531;
    private static final int BOOK_LEVEL_SALT = 0x424b_4c31;

    /** supply pool 1: rolls 3–10 균등. 바닐라 총 가중치 84 중 제외분 17 을 뺀 67. */
    private static final VanillaEntry[] SHIPWRECK_SUPPLY = {
        entry(PlayerInventory.PAPER, 8, 1, 12),
        entry(PlayerInventory.POTATO, 7, 2, 6),
        entry((short) Blocks.MOSS_BLOCK, 7, 1, 4),
        entry(PlayerInventory.CARROT, 7, 4, 8),
        entry(PlayerInventory.WHEAT, 7, 8, 21),
        entry(PlayerInventory.COAL, 6, 2, 8),
        entry(PlayerInventory.ROTTEN_FLESH, 5, 5, 24),
        entry(PlayerInventory.GUNPOWDER, 3, 1, 5),
        entry(PlayerInventory.LEATHER_HELMET, 3, 1, 1),
        entry(PlayerInventory.LEATHER_CHESTPLATE, 3, 1, 1),
        entry(PlayerInventory.LEATHER_LEGGINGS, 3, 1, 1),
        entry(PlayerInventory.LEATHER_BOOTS, 3, 1, 1),
        entry((short) Blocks.PUMPKIN, 2, 1, 3),
        entry((short) Blocks.BAMBOO, 2, 1, 3),
        entry((short) Blocks.TNT, 1, 1, 2)
    };

    /** treasure pool 1: rolls 3–6 균등. 바닐라 총 150 중 경험치병 5 를 뺀 145. */
    private static final VanillaEntry[] SHIPWRECK_TREASURE_METAL = {
        entry(PlayerInventory.IRON_INGOT, 90, 1, 5),
        entry(PlayerInventory.GOLD_INGOT, 10, 1, 5),
        entry(PlayerInventory.EMERALD, 40, 1, 5),
        entry(PlayerInventory.DIAMOND, 5, 1, 1)
    };

    /** treasure pool 2: rolls 2–5 균등. 바닐라 총 80 중 철 조각 50 을 뺀 30. */
    private static final VanillaEntry[] SHIPWRECK_TREASURE_NUGGET = {
        entry(PlayerInventory.GOLD_NUGGET, 10, 1, 10),
        entry(PlayerInventory.LAPIS_LAZULI, 20, 1, 10)
    };

    /** map pool 2: rolls 3 고정. 바닐라 총 38 그대로다(제외분 없음). */
    private static final VanillaEntry[] SHIPWRECK_MAP_SUPPLIES = {
        entry(PlayerInventory.COMPASS, 1, 1, 1),
        entry(PlayerInventory.MAP, 1, 1, 1),
        entry(PlayerInventory.CLOCK, 1, 1, 1),
        entry(PlayerInventory.PAPER, 20, 1, 10),
        entry(PlayerInventory.FEATHER, 10, 1, 5),
        entry(PlayerInventory.BOOK, 5, 1, 1)
    };


    // ---- [OCEAN-RUINS] 바닐라 1.21.4 해저 유적 상자 2종 --------------------------------
    //
    // 원문은 `docs/research/mc-vanilla-1214/loot_table/chests/underwater_ruin_{small,big}.json`
    // 이고 요약은 같은 디렉터리 `OCEAN_RUINS.md` §8 이다. wasm
    // `client/wasm/src/mc_structure/loot.rs` 가 같은 전리품 리터럴의 사본이며
    // `StandaloneExplorationLootParity.test.ts` 가 두 권위를 비교한다.
    //
    // 어느 표를 쓸지는 <b>상자 좌표만으로</b> 정해지며 생성기 상태를 들고 다니지 않는다.
    //
    // 지원하지 않는 항목은 <b>제자리에 EMPTY 로 남긴다</b>(대저택·딥다크와 같은 규칙).
    // 여기서 EMPTY 인 것은 탐험 지도(`exploration_map` buried_treasure) 하나뿐이며,
    // 난파선처럼 pool 을 통째로 뺄 수 없는 <b>공유 pool 안의 한 항</b>이라 자리표로 둔다.
    // 그래서 나머지 항목의 상대 확률과 굴림 순서가 바닐라 그대로 보존된다.

    /** underwater_ruin_small pool 1: rolls 2–8 균등. 바닐라 총 가중치 28 그대로다. */
    private static final VanillaEntry[] OCEAN_RUIN_SMALL_SUPPLIES = {
        entry(PlayerInventory.COAL, 10, 1, 4),
        entry(PlayerInventory.STONE_AXE, 2, 1, 1),
        entry(PlayerInventory.ROTTEN_FLESH, 5, 1, 1),
        entry(PlayerInventory.EMERALD, 1, 1, 1),
        entry(PlayerInventory.WHEAT, 10, 2, 3)
    };

    /** underwater_ruin_small pool 2: rolls 1. 바닐라 총 가중치 12 그대로다. */
    private static final VanillaEntry[] OCEAN_RUIN_SMALL_RELIC = {
        entry(PlayerInventory.LEATHER_CHESTPLATE, 1, 1, 1),
        entry(PlayerInventory.GOLD_HELMET, 1, 1, 1),
        entry(PlayerInventory.FISHING_ROD, 5, 1, 1),
        // 탐험 지도(묻힌 보물). 지도에 구조물 표식을 다는 계약이 아직 없어 자리표로 둔다.
        entry(PlayerInventory.EMPTY, 5, 1, 1)
    };

    /** underwater_ruin_big pool 1: rolls 2–8 균등. 바닐라 총 가중치 31 그대로다. */
    private static final VanillaEntry[] OCEAN_RUIN_BIG_SUPPLIES = {
        entry(PlayerInventory.COAL, 10, 1, 4),
        entry(PlayerInventory.GOLD_NUGGET, 10, 1, 3),
        entry(PlayerInventory.EMERALD, 1, 1, 1),
        entry(PlayerInventory.WHEAT, 10, 2, 3)
    };

    /** underwater_ruin_big pool 2: rolls 1. 바닐라 총 가중치 23 그대로다. */
    private static final VanillaEntry[] OCEAN_RUIN_BIG_RELIC = {
        entry(PlayerInventory.GOLDEN_APPLE, 1, 1, 1),
        // 바닐라는 `book` + `enchant_randomly options #minecraft:on_random_loot` 다 — 저장
        // 인챈트 하나가 든 마법이 부여된 책으로 나온다([LOOT-BOOKS]).
        enchantedBook(5, ON_RANDOM_LOOT_BOOK_OPTIONS),
        entry(PlayerInventory.LEATHER_CHESTPLATE, 1, 1, 1),
        entry(PlayerInventory.GOLD_HELMET, 1, 1, 1),
        entry(PlayerInventory.FISHING_ROD, 5, 1, 1),
        // 탐험 지도(묻힌 보물). 소형 표와 같은 이유로 자리표다.
        entry(PlayerInventory.EMPTY, 10, 1, 1)
    };

    // ---- [BURIED-TREASURE] 바닐라 1.21.4 묻힌 보물 상자 --------------------------------
    //
    // 원문은 `docs/research/mc-vanilla-1214/loot_table/chests/buried_treasure.json` 이고
    // 요약은 같은 디렉터리 `BURIED_TREASURE.md` §4 다. pool 6개를 <b>선언 순서대로</b>
    // 굴린다. 이 표에는 <b>제외한 항목이 하나도 없다</b> — 여섯 pool 의 모든 품목이 이
    // 저장소에 이미 있다(바다의 심장은 [CONDUIT] 트랙이 등록한 1950 이다).

    /** pool 1: rolls 1. 바다의 심장이 <b>모든</b> 묻힌 보물에 확정으로 들어간다. */
    private static final VanillaEntry[] BURIED_TREASURE_HEART = {
        entry((short) Blocks.HEART_OF_THE_SEA, 1, 1, 1)
    };

    /** pool 2: rolls 5–8 균등. 바닐라 총 가중치 35 그대로다. */
    private static final VanillaEntry[] BURIED_TREASURE_INGOTS = {
        entry(PlayerInventory.IRON_INGOT, 20, 1, 4),
        entry(PlayerInventory.GOLD_INGOT, 10, 1, 4),
        entry((short) Blocks.TNT, 5, 1, 2)
    };

    /** pool 3: rolls 1–3 균등. 바닐라 총 가중치 15 그대로다. */
    private static final VanillaEntry[] BURIED_TREASURE_GEMS = {
        entry(PlayerInventory.EMERALD, 5, 4, 8),
        entry(PlayerInventory.DIAMOND, 5, 1, 2),
        entry(PlayerInventory.PRISMARINE_CRYSTALS, 5, 1, 5)
    };

    /** pool 4: rolls 0–1 균등. 굴림이 0 이면 아무것도 나오지 않는다(바닐라 그대로). */
    private static final VanillaEntry[] BURIED_TREASURE_GEAR = {
        entry(PlayerInventory.LEATHER_CHESTPLATE, 1, 1, 1),
        entry(PlayerInventory.IRON_SWORD, 1, 1, 1)
    };

    /** pool 5: rolls 2 고정. 익힌 생선 두 종이 1:1 이다. */
    private static final VanillaEntry[] BURIED_TREASURE_FOOD = {
        entry(PlayerInventory.COD_COOKED, 1, 2, 4),
        entry(PlayerInventory.SALMON_COOKED, 1, 2, 4)
    };

    /** pool 6: rolls 0–2 균등. 바닐라 `set_potion: water_breathing` 그대로다. */
    private static final VanillaEntry[] BURIED_TREASURE_POTION = {
        entry(PlayerInventory.POTION_WATER_BREATHING, 1, 1, 1)
    };

    // ---- [NAUTILUS-ARMOR] Minecraft 26.2 탐험 상자 공용 갑옷 pool -----------------------
    //
    // 26.2 원문은 buried_treasure, shipwreck의 supply/treasure/map 세 표, underwater_ruin의
    // small/big 두 표에 아래 1-roll pool을 똑같이 덧붙인다. 이 프로젝트도 그 여섯 profile을
    // 그대로 대응시키되, 좌표로 profile을 합성하는 기존 추상화 때문에 profile별 salt만 나눈다.
    // 기존 pool 뒤에 독립 lane으로 실행하므로 기존 전리품의 RNG와 선언 순서는 변하지 않는다.
    private static final VanillaEntry[] NAUTILUS_ARMOR = {
        entry(PlayerInventory.EMPTY, 148, 1, 1),
        entry(PlayerInventory.COPPER_NAUTILUS_ARMOR, 20, 1, 1),
        entry(PlayerInventory.IRON_NAUTILUS_ARMOR, 10, 1, 1),
        entry(PlayerInventory.GOLD_NAUTILUS_ARMOR, 5, 1, 1),
        entry(PlayerInventory.DIAMOND_NAUTILUS_ARMOR, 2, 1, 1)
    };

    // ---- [WOODLAND MANSION] Java 1.21.4 chests/woodland_mansion --------------------------
    //
    // Unsupported entries remain EMPTY entries at their exact original positions and weights.
    // A selected EMPTY entry produces nothing; it is never replaced and its weight is never
    // removed from the draw total. This preserves the vanilla probability mass and roll order.

    /** Pool 1: rolls 1–3, total vanilla weight 127. */
    private static final VanillaEntry[] MANSION_RARE = {
        entry(PlayerInventory.LEAD, 20, 1, 1),
        entry(PlayerInventory.GOLDEN_APPLE, 15, 1, 1),
        // [GOLD-FOOD] 마법이 부여된 황금 사과가 등록되면서 이 자리가 <b>실물로 되돌아왔다</b>.
        // 가중치 2/127 은 바닐라 원문 그대로이고, EMPTY 자리표를 실물로 바꾸기만 했으므로
        // 다른 항목의 상대 확률과 굴림 순서는 하나도 움직이지 않는다.
        entry(PlayerInventory.ENCHANTED_GOLDEN_APPLE, 2, 1, 1),
        entry(PlayerInventory.EMPTY, 15, 1, 1), // music disc 13
        entry(PlayerInventory.EMPTY, 15, 1, 1), // music disc cat
        entry(PlayerInventory.NAME_TAG, 20, 1, 1),
        entry(PlayerInventory.CHAINMAIL_CHESTPLATE, 10, 1, 1),
        entry(PlayerInventory.EMPTY, 15, 1, 1), // diamond hoe
        entry(PlayerInventory.DIAMOND_CHESTPLATE, 5, 1, 1),
        // [LOOT-BOOKS] 바닐라 `book` + `enchant_randomly options #minecraft:on_random_loot`.
        // 자리표 EMPTY 가 같은 자리 · 같은 가중치 10/127 의 실물로 되돌아왔다.
        enchantedBook(10, ON_RANDOM_LOOT_BOOK_OPTIONS)
    };

    /** Pool 2: rolls 1–4, total vanilla weight 175. */
    private static final VanillaEntry[] MANSION_SUPPLIES = {
        entry(PlayerInventory.IRON_INGOT, 10, 1, 4),
        entry(PlayerInventory.GOLD_INGOT, 5, 1, 4),
        entry(PlayerInventory.BREAD, 20, 1, 1),
        entry(PlayerInventory.WHEAT, 20, 1, 4),
        entry(PlayerInventory.BUCKET, 10, 1, 1),
        entry(PlayerInventory.REDSTONE_DUST, 15, 1, 4),
        entry(PlayerInventory.COAL, 15, 1, 4),
        entry(PlayerInventory.EMPTY, 10, 1, 1), // melon seeds
        entry(PlayerInventory.PUMPKIN_SEEDS, 10, 2, 4),
        entry(PlayerInventory.BEETROOT_SEEDS, 10, 2, 4),
        // [CREAKING] 수지 덩어리가 등록되면서 이 자리가 <b>실물로 되돌아왔다</b>. 가중치
        // 50/175 은 바닐라 원문 그대로이고 EMPTY 자리표를 실물로 바꾸기만 했으므로 다른
        // 항목의 상대 확률과 굴림 순서는 하나도 움직이지 않는다(마법이 부여된 황금 사과가
        // 낸 선례와 같은 수술이다). 개수만 자리표의 1~1 에서 바닐라 값 2~4 로 바로잡는다 —
        // 개수는 좌표 해시 lane 하나로 따로 뽑히므로(vanillaAmount) 이 변경이 다른 항목의
        // 확률·순서에 닿지 않는다. [B] «Resin Clump»: "2–4 items, 53.8% chance"
        // (53.8% = 1~4 굴림에서 가중치 50/175 이 한 번이라도 뽑힐 확률과 정확히 같다).
        entry((short) Blocks.RESIN_CLUMP,
                50, CreakingHeartRules.MANSION_CLUMP_MIN, CreakingHeartRules.MANSION_CLUMP_MAX)
    };

    /** Pool 3: exactly 3 rolls, total vanilla weight 40. */
    private static final VanillaEntry[] MANSION_MOB_DROPS = {
        entry(PlayerInventory.BONE, 10, 1, 8),
        entry(PlayerInventory.GUNPOWDER, 10, 1, 8),
        entry(PlayerInventory.ROTTEN_FLESH, 10, 1, 8),
        entry(PlayerInventory.STRING, 10, 1, 8)
    };

    /** Pool 4: exactly 1 roll; empty and unsupported vex trim are both empty outcomes. */
    private static final VanillaEntry[] MANSION_TRIM = {
        entry(PlayerInventory.EMPTY, 1, 1, 1),
        entry(PlayerInventory.EMPTY, 1, 1, 1) // vex armor trim smithing template
    };


    // ---- [DEEP-DARK] 딥다크 도시 상자 -------------------------------------------------
    //
    // 근거 등급: **[C]** — 바닐라 `chests/ancient_city` 의 <b>품목 구성</b>(전투 장비 · 광물 ·
    // 인챈트 책 · 이름표 · 안장 · 음반 조각 · 메아리 조각)은 위키 본문에서 확인했지만
    // ([B] minecraft.wiki «Ancient City»), 실제 JSON 의 weight/count 는 확인하지 못했다.
    // 그래서 <b>가중치는 이 저장소의 계약</b>이고 그 사실을 여기 명시한다. 확인되면 값만
    // 갈아 끼우면 되도록 표를 한 곳에 모아 둔다.
    //
    // 지원하지 않는 품목(음반 조각 · 메아리 조각 · 방어구 장식)은
    // 대저택 표와 같은 규칙으로 <b>제자리에 EMPTY 로 남긴다</b> — 뽑히면 아무것도 나오지
    // 않고 가중치는 총합에서 빠지지 않는다. 확률 질량과 굴림 순서가 보존된다.

    /** pool 1: rolls 1–3. 딥다크 도시의 "무언가 대단한 것" 자리다. */
    private static final VanillaEntry[] DEEP_DARK_RARE = {
        entry(PlayerInventory.EMPTY, 15, 1, 1), // disc fragment 5
        entry(PlayerInventory.EMPTY, 10, 1, 1), // echo shard
        // [LOOT-BOOKS] 바닐라 `book` + `enchant_randomly options minecraft:swift_sneak`.
        // 자리표 EMPTY 가 같은 자리 · 같은 가중치의 실물로 되돌아왔다.
        enchantedBook(5, SWIFT_SNEAK_BOOK_OPTIONS),
        // 바닐라 `book` + `enchant_randomly options #minecraft:on_random_loot`.
        enchantedBook(10, ON_RANDOM_LOOT_BOOK_OPTIONS),
        entry(PlayerInventory.DIAMOND, 5, 1, 3),
        entry(PlayerInventory.DIAMOND_CHESTPLATE, 3, 1, 1),
        entry(PlayerInventory.DIAMOND_HELMET, 3, 1, 1),
        entry(PlayerInventory.NAME_TAG, 8, 1, 1),
        entry(PlayerInventory.SADDLE, 8, 1, 1),
        entry(PlayerInventory.GOLDEN_APPLE, 6, 1, 1)
    };

    /** pool 2: rolls 1–4. 생존 보급품. */
    private static final VanillaEntry[] DEEP_DARK_SUPPLIES = {
        entry(PlayerInventory.IRON_INGOT, 15, 1, 4),
        entry(PlayerInventory.GOLD_INGOT, 10, 1, 4),
        entry(PlayerInventory.EMERALD, 8, 1, 3),
        entry(PlayerInventory.COAL, 15, 1, 6),
        entry(PlayerInventory.REDSTONE_DUST, 10, 1, 6),
        entry(PlayerInventory.BOOK, 10, 1, 3),
        entry(PlayerInventory.BONE, 12, 1, 6),
        entry(PlayerInventory.STRING, 12, 1, 6),
        entry(PlayerInventory.GUNPOWDER, 8, 1, 4)
    };

    /**
     * pool 3: rolls 정확히 1. <b>말린 가스트 전용 pool</b> 이다.
     *
     * <p>사용자 지시로 해피 가스트 획득 경로를 "딥다크에서 희귀하게" 로 옮기면서, 바닐라
     * 표의 확률 질량을 건드리지 않기 위해 <b>별도 pool</b> 로 분리했다 — 위 두 표에 끼워
     * 넣으면 다른 품목의 상대 확률이 전부 흔들린다. 상자 하나당 정확히 1/40 (2.5%) 이며
     * 방마다 상자가 최대 하나이므로 도시 하나에 평균 0.1~0.2 개가 나온다. [C]
     */
    private static final VanillaEntry[] DEEP_DARK_RELIC = {
        entry((short) Blocks.DRIED_GHAST, 1, 1, 1),
        entry(PlayerInventory.EMPTY, 39, 1, 1)
    };

    /**
     * [SHIELD-FAMILY] 방패 세 티어(돌 2021 · 구리 2022 · 금 2023)의 <b>독립 굴림</b> pool.
     *
     * <p>근거 등급 <b>[C]</b>: 바닐라에는 이 세 방패가 없으므로 대응하는 전리품 원문도 없다.
     *
     * <p><b>기존 확률을 한 칸도 건드리지 않는 방식</b>으로 붙인다 — 위 바닐라 표들에 항목을
     * 끼워 넣으면 총 가중치가 늘어 <b>그 표의 모든 품목</b> 의 상대 확률이 흔들린다. 그래서
     * 말린 가스트({@link #DEEP_DARK_RELIC})가 낸 선례 그대로 <b>자기 salt 를 가진 별도 pool</b>
     * 로 분리한다. 굴림 lane 은 salt 로 갈리므로 이 pool 을 추가해도 기존 pool 이 뽑는 값은
     * 비트 하나 달라지지 않는다(같은 좌표·같은 seed 에서 기존 상자 내용물이 그대로 재현된다).
     *
     * <p>레이드 보상 쪽은 건드리지 않는다 — 그쪽은 {@code POOL_SIZE} 가 고정이라 항목을 넣으면
     * 다른 보상이 밀려난다. <b>대저택도 건드리지 않는다</b> — 그 표는 바닐라 원문에 충실하도록
     * 품목 집합과 굴림 지문까지 고정되어 있어(등급 [A]/[B]) 바닐라에 없는 물건이 들어갈 자리가
     * 아니다. 딥다크 표는 가중치 자체가 이미 이 저장소의 계약([C])이고 말린 가스트라는 WebCraft
     * 전용 pool 을 이미 품고 있어, 이 확장이 들어갈 유일하게 결이 맞는 자리다.
     *
     * <p>가중치 총합 100 · rolls 정확히 1 이라 상자 하나당 <b>6%</b>(돌 3% · 구리 2% · 금 1%)
     * 이고 94% 는 아무것도 나오지 않는다. 제작이 주 입수 경로이고 이쪽은 낮은 확률의 보조
     * 경로다. 가죽 방패는 여기 없다 — 가장 값싼 티어라 탐험 보상으로 줄 이유가 없다.
     */
    private static final VanillaEntry[] SHIELD_CACHE = {
        entry(PlayerInventory.STONE_SHIELD, 3, 1, 1),
        entry(PlayerInventory.COPPER_SHIELD, 2, 1, 1),
        entry(PlayerInventory.GOLD_SHIELD, 1, 1, 1),
        entry(PlayerInventory.EMPTY, 94, 1, 1)
    };

    private static final int SHIPWRECK_SUPPLY_SALT = 0x1f43_b7c5;
    private static final int SHIPWRECK_TREASURE_METAL_SALT = 0x38a2_6d19;
    private static final int SHIPWRECK_TREASURE_NUGGET_SALT = 0x5c07_e2b3;
    private static final int SHIPWRECK_MAP_SALT = 0x2764_d80f;
    private static final int OCEAN_RUIN_SMALL_SUPPLIES_SALT = 0x4f52_5331;
    private static final int OCEAN_RUIN_SMALL_RELIC_SALT = 0x4f52_5332;
    private static final int OCEAN_RUIN_BIG_SUPPLIES_SALT = 0x4f52_4231;
    private static final int OCEAN_RUIN_BIG_RELIC_SALT = 0x4f52_4232;
    private static final int OCEAN_RUIN_CHEST_SIZE_SALT = 0x4f52_5a11;
    private static final int BURIED_TREASURE_HEART_SALT = 0x4254_0001;
    private static final int BURIED_TREASURE_INGOTS_SALT = 0x4254_0002;
    private static final int BURIED_TREASURE_GEMS_SALT = 0x4254_0003;
    private static final int BURIED_TREASURE_GEAR_SALT = 0x4254_0004;
    private static final int BURIED_TREASURE_FOOD_SALT = 0x4254_0005;
    private static final int BURIED_TREASURE_POTION_SALT = 0x4254_0006;
    private static final int NAUTILUS_ARMOR_BURIED_SALT = 0x4e41_4201;
    private static final int NAUTILUS_ARMOR_SHIPWRECK_SUPPLY_SALT = 0x4e41_5301;
    private static final int NAUTILUS_ARMOR_SHIPWRECK_TREASURE_SALT = 0x4e41_5302;
    private static final int NAUTILUS_ARMOR_SHIPWRECK_MAP_SALT = 0x4e41_5303;
    private static final int NAUTILUS_ARMOR_OCEAN_SMALL_SALT = 0x4e41_4f01;
    private static final int NAUTILUS_ARMOR_OCEAN_BIG_SALT = 0x4e41_4f02;
    private static final int VANILLA_ROLLS_SALT = 0x11b9_4e6d;
    private static final int MANSION_RARE_SALT = 0x4d41_4e31;
    private static final int MANSION_SUPPLIES_SALT = 0x4d41_4e32;
    private static final int MANSION_MOB_DROPS_SALT = 0x4d41_4e33;
    private static final int MANSION_TRIM_SALT = 0x4d41_4e34;
    private static final int DEEP_DARK_RARE_SALT = 0x4444_4331;
    private static final int DEEP_DARK_SUPPLIES_SALT = 0x4444_4332;
    private static final int DEEP_DARK_RELIC_SALT = 0x4444_4333;
    // [SHIELD-FAMILY] 방패 독립 굴림의 lane. 기존 어느 salt 와도 겹치지 않아야 굴림이 갈린다.
    private static final int DEEP_DARK_SHIELD_CACHE_SALT = 0x5348_4c32;

    // [NETHERITE-OVERWORLD] The product has no Nether. These append-only pools use independent
    // coordinate lanes so every pre-existing pool keeps its denominator, roll count and fingerprint.
    private static final VanillaEntry[] NETHERRACK_CACHE = {
        entry(PlayerInventory.EMPTY, 7, 1, 1),
        entry((short) Blocks.NETHERRACK, 1, 2, 6)
    };
    private static final VanillaEntry[] ANCIENT_DEBRIS_CACHE = {
        entry(PlayerInventory.EMPTY, 63, 1, 1),
        entry((short) Blocks.ANCIENT_DEBRIS, 1, 1, 1)
    };
    private static final VanillaEntry[] NETHERITE_TEMPLATE_CACHE = {
        entry(PlayerInventory.EMPTY, 127, 1, 1),
        entry(PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1, 1)
    };
    private static final int DUNGEON_NETHERRACK_SALT = 0x4e44_4731;
    private static final int DUNGEON_DEBRIS_SALT = 0x4e44_4732;
    private static final int MINESHAFT_NETHERRACK_SALT = 0x4e4d_5331;
    private static final int MINESHAFT_DEBRIS_SALT = 0x4e4d_5332;
    private static final int DEEP_DARK_NETHERITE_TEMPLATE_SALT = 0x4e44_5431;

    // ---- [HORSE-ARMOR] supported Overworld chest profiles -------------------------------
    // 1.21.9 adds Copper Horse Armor at weight 15 while retaining the 1.21.4 Iron/Gold/Diamond
    // entries. Unsupported/non-horse entries stay EMPTY at their source weights, so these isolated
    // append-only lanes reproduce the exact armor probabilities without disturbing MUNDANE RNG.
    // [LOOT-BOOKS] These lanes are horse-armor-only by design, so their "enchanted book" rows stay
    // EMPTY; the rest of a dungeon/desert-tomb chest comes from the MUNDANE pool, not these tables.
    private static final VanillaEntry[] DUNGEON_HORSE_ARMOR = {
        entry(PlayerInventory.EMPTY, 20, 1, 5), // leather
        entry(PlayerInventory.EMPTY, 15, 1, 1), // golden apple
        entry(PlayerInventory.EMPTY, 2, 1, 1),  // enchanted golden apple
        entry(PlayerInventory.EMPTY, 2, 1, 1),  // music disc otherside
        entry(PlayerInventory.EMPTY, 15, 1, 1), // music disc 13
        entry(PlayerInventory.EMPTY, 15, 1, 1), // music disc cat
        entry(PlayerInventory.EMPTY, 20, 1, 1), // name tag
        entry(PlayerInventory.GOLDEN_HORSE_ARMOR, 10, 1, 1),
        entry(PlayerInventory.COPPER_HORSE_ARMOR, 15, 1, 1),
        entry(PlayerInventory.IRON_HORSE_ARMOR, 15, 1, 1),
        entry(PlayerInventory.DIAMOND_HORSE_ARMOR, 5, 1, 1),
        entry(PlayerInventory.EMPTY, 10, 1, 1)  // enchanted book
    };
    private static final VanillaEntry[] DESERT_TOMB_HORSE_ARMOR = {
        entry(PlayerInventory.EMPTY, 5, 1, 3),  // diamond
        entry(PlayerInventory.EMPTY, 15, 1, 5), // iron ingot
        entry(PlayerInventory.EMPTY, 15, 2, 7), // gold ingot
        entry(PlayerInventory.EMPTY, 15, 1, 3), // emerald
        entry(PlayerInventory.EMPTY, 25, 4, 6), // bone
        entry(PlayerInventory.EMPTY, 25, 1, 3), // spider eye
        entry(PlayerInventory.EMPTY, 25, 3, 7), // rotten flesh
        entry(PlayerInventory.EMPTY, 20, 1, 5), // leather (1.21.9 saddle replacement)
        entry(PlayerInventory.COPPER_HORSE_ARMOR, 15, 1, 1),
        entry(PlayerInventory.IRON_HORSE_ARMOR, 15, 1, 1),
        entry(PlayerInventory.GOLDEN_HORSE_ARMOR, 10, 1, 1),
        entry(PlayerInventory.DIAMOND_HORSE_ARMOR, 5, 1, 1),
        entry(PlayerInventory.EMPTY, 20, 1, 1), // enchanted book
        entry(PlayerInventory.EMPTY, 20, 1, 1), // golden apple
        entry(PlayerInventory.EMPTY, 2, 1, 1),  // enchanted golden apple
        entry(PlayerInventory.EMPTY, 15, 1, 1)
    };
    private static final int DUNGEON_HORSE_ARMOR_SALT = 0x4841_4431;
    private static final int DESERT_TOMB_HORSE_ARMOR_SALT = 0x4841_4454;

    private ExplorationLoot() {}

    public static void fill(ChestInventory chest, int seed, int x, int y, int z, Kind kind) {
        if (kind == Kind.SHIPWRECK) {
            fillShipwreck(chest, seed, x, y, z);
            return;
        }
        if (kind == Kind.WOODLAND_MANSION) {
            fillWoodlandMansion(chest, seed, x, y, z);
            return;
        }
        if (kind == Kind.DEEP_DARK_CITY) {
            fillDeepDarkCity(chest, seed, x, y, z);
            return;
        }
        if (kind == Kind.OCEAN_RUINS) {
            fillOceanRuin(chest, seed, x, y, z);
            return;
        }
        if (kind == Kind.BURIED_TREASURE) {
            fillBuriedTreasure(chest, seed, x, y, z);
            return;
        }
        RuinLootProfile.Profile profile = profile(kind);
        RuinLootProfile.Tier tier = RuinLootProfile.contentTier(seed, x, z, profile);
        if (kind == Kind.TOMB_RUIN) addBurialEvidence(chest, seed, x, y, z);
        if (tier == RuinLootProfile.Tier.SPECIAL) {
            addSpecialLoot(chest, seed, x, y, z);
        } else {
            int rolls = rollCount(seed, x, y, z, kind);
            for (int roll = 0; roll < rolls; roll++) {
                add(chest, mundaneRoll(seed, x, y, z, kind, roll));
            }
        }
        fillNetheriteOverworldCache(chest, seed, x, y, z, kind);
        fillHorseArmorLoot(chest, seed, x, y, z, kind);
    }

    /**
     * [SHIPWRECK] 상자 하나를 바닐라 표로 채운다. 어느 표인지는 상자 좌표만으로 정해지므로
     * (생성기 없이도 재계산된다) 첫 개방 경로가 생성기 상태를 들고 다니지 않아도 된다.
     */
    public static void fillShipwreck(ChestInventory chest, int seed, int x, int y, int z) {
        ShipwreckVariant.ChestRole role = ShipwreckVariant.chestRole(seed, x, y, z);
        switch (role) {
            case SUPPLY -> fillVanillaPool(chest, seed, x, y, z, SHIPWRECK_SUPPLY, 3, 10,
                    SHIPWRECK_SUPPLY_SALT);
            case TREASURE -> {
                fillVanillaPool(chest, seed, x, y, z, SHIPWRECK_TREASURE_METAL, 3, 6,
                        SHIPWRECK_TREASURE_METAL_SALT);
                fillVanillaPool(chest, seed, x, y, z, SHIPWRECK_TREASURE_NUGGET, 2, 5,
                        SHIPWRECK_TREASURE_NUGGET_SALT);
            }
            // 바닐라 map pool 1(탐험 지도)은 이 저장소에 지도 기능이 없어 통째로 빠졌다.
            // 대체 전리품을 넣지 않고 남은 pool 2 만 굴린다.
            case MAP -> fillVanillaPool(chest, seed, x, y, z, SHIPWRECK_MAP_SUPPLIES, 3, 3,
                    SHIPWRECK_MAP_SALT);
        }
        // 26.2는 세 shipwreck chest profile 모두에 같은 표를 둔다. profile별 독립 salt다.
        fillNautilusArmorPool(chest, seed, x, y, z, switch (role) {
            case SUPPLY -> NautilusArmorLootProfile.SHIPWRECK_SUPPLY;
            case TREASURE -> NautilusArmorLootProfile.SHIPWRECK_TREASURE;
            case MAP -> NautilusArmorLootProfile.SHIPWRECK_MAP;
        });
    }


    /**
     * [OCEAN-RUINS] 상자 하나를 바닐라 표로 채운다. 소형/대형 표 선택은 상자 좌표만으로
     * 정해지므로 첫 개방 경로가 canonical terrain 생성기 상태를 들고 다니지 않아도 된다.
     */
    public static void fillOceanRuin(ChestInventory chest, int seed, int x, int y, int z) {
        if (isBigOceanRuinChest(seed, x, y, z)) {
            fillVanillaPool(chest, seed, x, y, z, OCEAN_RUIN_BIG_SUPPLIES, 2, 8,
                    OCEAN_RUIN_BIG_SUPPLIES_SALT);
            fillVanillaPool(chest, seed, x, y, z, OCEAN_RUIN_BIG_RELIC, 1, 1,
                    OCEAN_RUIN_BIG_RELIC_SALT);
            fillNautilusArmorPool(chest, seed, x, y, z,
                    NautilusArmorLootProfile.OCEAN_RUIN_BIG);
            return;
        }
        fillVanillaPool(chest, seed, x, y, z, OCEAN_RUIN_SMALL_SUPPLIES, 2, 8,
                OCEAN_RUIN_SMALL_SUPPLIES_SALT);
        fillVanillaPool(chest, seed, x, y, z, OCEAN_RUIN_SMALL_RELIC, 1, 1,
                OCEAN_RUIN_SMALL_RELIC_SALT);
        fillNautilusArmorPool(chest, seed, x, y, z,
                NautilusArmorLootProfile.OCEAN_RUIN_SMALL);
    }

    /** Canonical ocean-ruin chest profile metadata; this does not plan or place a structure. */
    static boolean isBigOceanRuinChest(int seed, int x, int y, int z) {
        int lane = StructureHash.hash3(
                StructureHash.seedSalt(seed, OCEAN_RUIN_CHEST_SIZE_SALT), x, y, z);
        return (StructureHash.unsigned(lane) & 1) != 0;
    }

    /**
     * [BURIED-TREASURE] 묻힌 보물 상자 하나를 채운다. 여섯 pool 을 <b>선언 순서대로</b>
     * 굴린다. 첫 pool 이 바다의 심장을 확정으로 넣는 것이 이 표의 핵심 계약이다.
     */
    public static void fillBuriedTreasure(ChestInventory chest, int seed, int x, int y, int z) {
        fillVanillaPool(chest, seed, x, y, z, BURIED_TREASURE_HEART, 1, 1,
                BURIED_TREASURE_HEART_SALT);
        fillVanillaPool(chest, seed, x, y, z, BURIED_TREASURE_INGOTS, 5, 8,
                BURIED_TREASURE_INGOTS_SALT);
        fillVanillaPool(chest, seed, x, y, z, BURIED_TREASURE_GEMS, 1, 3,
                BURIED_TREASURE_GEMS_SALT);
        fillVanillaPool(chest, seed, x, y, z, BURIED_TREASURE_GEAR, 0, 1,
                BURIED_TREASURE_GEAR_SALT);
        fillVanillaPool(chest, seed, x, y, z, BURIED_TREASURE_FOOD, 2, 2,
                BURIED_TREASURE_FOOD_SALT);
        fillVanillaPool(chest, seed, x, y, z, BURIED_TREASURE_POTION, 0, 2,
                BURIED_TREASURE_POTION_SALT);
        fillNautilusArmorPool(chest, seed, x, y, z,
                NautilusArmorLootProfile.BURIED_TREASURE);
    }

    enum NautilusArmorLootProfile {
        BURIED_TREASURE,
        SHIPWRECK_SUPPLY,
        SHIPWRECK_TREASURE,
        SHIPWRECK_MAP,
        OCEAN_RUIN_SMALL,
        OCEAN_RUIN_BIG
    }

    private static void fillNautilusArmorPool(ChestInventory chest, int seed, int x, int y, int z,
            NautilusArmorLootProfile profile) {
        fillVanillaPool(chest, seed, x, y, z, NAUTILUS_ARMOR, 1, 1,
                nautilusArmorPoolSalt(profile));
    }

    static int nautilusArmorPoolWeight() {
        return totalWeight(NAUTILUS_ARMOR);
    }

    static int nautilusArmorPoolSize() {
        return NAUTILUS_ARMOR.length;
    }

    static int[] nautilusArmorPoolEntry(int index) {
        VanillaEntry entry = NAUTILUS_ARMOR[index];
        return new int[] {entry.type(), entry.weight(), entry.minimum(), entry.maximum()};
    }

    static short nautilusArmorRollType(int seed, int x, int y, int z,
            NautilusArmorLootProfile profile) {
        return vanillaEntry(seed, x, y, z, NAUTILUS_ARMOR,
                nautilusArmorPoolSalt(profile), 0).type();
    }

    private static int nautilusArmorPoolSalt(NautilusArmorLootProfile profile) {
        return switch (profile) {
            case BURIED_TREASURE -> NAUTILUS_ARMOR_BURIED_SALT;
            case SHIPWRECK_SUPPLY -> NAUTILUS_ARMOR_SHIPWRECK_SUPPLY_SALT;
            case SHIPWRECK_TREASURE -> NAUTILUS_ARMOR_SHIPWRECK_TREASURE_SALT;
            case SHIPWRECK_MAP -> NAUTILUS_ARMOR_SHIPWRECK_MAP_SALT;
            case OCEAN_RUIN_SMALL -> NAUTILUS_ARMOR_OCEAN_SMALL_SALT;
            case OCEAN_RUIN_BIG -> NAUTILUS_ARMOR_OCEAN_BIG_SALT;
        };
    }

    /**
     * [DEEP-DARK] 딥다크 도시 상자 하나를 채운다. 세 pool 을 선언 순서대로 굴리며, 어느
     * 표를 쓰는지는 상자 좌표만으로 정해진다(생성기 상태를 들고 다니지 않아도 재계산된다).
     */
    public static void fillDeepDarkCity(ChestInventory chest, int seed, int x, int y, int z) {
        fillVanillaPool(chest, seed, x, y, z, DEEP_DARK_RARE, 1, 3, DEEP_DARK_RARE_SALT);
        fillVanillaPool(chest, seed, x, y, z, DEEP_DARK_SUPPLIES, 1, 4, DEEP_DARK_SUPPLIES_SALT);
        fillVanillaPool(chest, seed, x, y, z, DEEP_DARK_RELIC, 1, 1, DEEP_DARK_RELIC_SALT);
        // [SHIELD-FAMILY] 독립 굴림이라 위 세 pool 의 결과를 바꾸지 않는다(salt 가 lane 을 가른다).
        fillVanillaPool(chest, seed, x, y, z, SHIELD_CACHE, 1, 1, DEEP_DARK_SHIELD_CACHE_SALT);
        fillNetheriteOverworldCache(chest, seed, x, y, z, Kind.DEEP_DARK_CITY);
    }

    /** Executes the four vanilla mansion pools in source order with isolated coordinate lanes. */
    public static void fillWoodlandMansion(ChestInventory chest, int seed, int x, int y, int z) {
        fillVanillaPool(chest, seed, x, y, z, MANSION_RARE, 1, 3, MANSION_RARE_SALT);
        fillVanillaPool(chest, seed, x, y, z,
                MANSION_SUPPLIES, 1, 4, MANSION_SUPPLIES_SALT);
        fillVanillaPool(chest, seed, x, y, z,
                MANSION_MOB_DROPS, 3, 3, MANSION_MOB_DROPS_SALT);
        fillVanillaPool(chest, seed, x, y, z, MANSION_TRIM, 1, 1, MANSION_TRIM_SALT);
        // [SHIELD-FAMILY] 대저택에는 방패 독립 굴림을 붙이지 않는다. 이 표는 바닐라 원문에
        // 충실하도록 **품목 집합까지 고정**되어 있고({@code WoodlandMansionLootContractTest} 가
        // 지원 품목 화이트리스트와 굴림 지문을 못박는다) 바닐라에 없는 물건이 섞이면 그 계약이
        // 깨진다. 방패는 [C] 등급이라 가중치가 이미 이 저장소의 계약인 딥다크 쪽에만 붙는다.
    }

    /** 바닐라 loot pool 한 개: rolls 를 균등으로 뽑고 매 roll 마다 가중치 표에서 1종을 고른다. */
    private static void fillVanillaPool(ChestInventory chest, int seed, int x, int y, int z,
            VanillaEntry[] pool, int minimumRolls, int maximumRolls, int poolSalt) {
        int rolls = vanillaRollCount(
                seed, x, y, z, minimumRolls, maximumRolls, poolSalt);
        for (int roll = 0; roll < rolls; roll++) {
            VanillaEntry chosen = vanillaEntry(seed, x, y, z, pool, poolSalt, roll);
            int count = vanillaAmount(seed, x, y, z, chosen, poolSalt, roll);
            if (chosen.type() == PlayerInventory.EMPTY) continue;
            if (chosen.enchantOptions() != null) {
                // [LOOT-BOOKS] 바닐라 enchant_randomly: 저장 인챈트 정확히 하나. 워드 0 은 스택
                // 칸, ID 16 이상은 성분 문자열(WCIC4)로 싣는다(트라이얼 금고와 같은 경로).
                int[] stored = vanillaBookEnchantment(seed, x, y, z, chosen, poolSalt, roll);
                WideEnchantments enchantments = WideEnchantments.EMPTY.with(stored[0], stored[1]);
                chest.add(chosen.type(), count, PlayerInventory.initialDurability(chosen.type()),
                        enchantments.word0(), 0, 0, null, enchantments.hasExtended()
                                ? ItemComponentCodec.withEnchantments(
                                        chosen.type(), null, enchantments)
                                : null);
                continue;
            }
            if (PlayerInventory.isDurable(chosen.type())) {
                // 이 표가 선택한 내구 장비는 온전한 한 개로 지급한다. 별도 enchant function 이
                // 명시되지 않은 항목에 프로젝트 인챈트를 임의로 붙이지 않는다.
                chest.add(chosen.type(), 1, PlayerInventory.initialDurability(chosen.type()));
            } else {
                chest.add(chosen.type(), count);
            }
        }
    }

    private static void fillNetheriteOverworldCache(
            ChestInventory chest, int seed, int x, int y, int z, Kind kind) {
        if (kind == Kind.DUNGEON) {
            fillVanillaPool(chest, seed, x, y, z, NETHERRACK_CACHE, 1, 1,
                    DUNGEON_NETHERRACK_SALT);
            fillVanillaPool(chest, seed, x, y, z, ANCIENT_DEBRIS_CACHE, 1, 1,
                    DUNGEON_DEBRIS_SALT);
        } else if (kind == Kind.MINESHAFT) {
            fillVanillaPool(chest, seed, x, y, z, NETHERRACK_CACHE, 1, 1,
                    MINESHAFT_NETHERRACK_SALT);
            fillVanillaPool(chest, seed, x, y, z, ANCIENT_DEBRIS_CACHE, 1, 1,
                    MINESHAFT_DEBRIS_SALT);
        } else if (kind == Kind.DEEP_DARK_CITY) {
            fillVanillaPool(chest, seed, x, y, z, NETHERITE_TEMPLATE_CACHE, 1, 1,
                    DEEP_DARK_NETHERITE_TEMPLATE_SALT);
        }
    }

    private static void fillHorseArmorLoot(
            ChestInventory chest, int seed, int x, int y, int z, Kind kind) {
        if (kind == Kind.DUNGEON) {
            fillVanillaPool(chest, seed, x, y, z, DUNGEON_HORSE_ARMOR, 1, 3,
                    DUNGEON_HORSE_ARMOR_SALT);
        } else if (kind == Kind.DESERT_TOMB) {
            fillVanillaPool(chest, seed, x, y, z, DESERT_TOMB_HORSE_ARMOR, 2, 4,
                    DESERT_TOMB_HORSE_ARMOR_SALT);
        }
    }

    static int horseArmorPoolWeight(Kind kind) {
        VanillaEntry[] pool = horseArmorPool(kind);
        return pool == null ? 0 : totalWeight(pool);
    }

    static int horseArmorPoolSize(Kind kind) {
        VanillaEntry[] pool = horseArmorPool(kind);
        return pool == null ? 0 : pool.length;
    }

    static int[] horseArmorPoolEntry(Kind kind, int index) {
        VanillaEntry[] pool = horseArmorPool(kind);
        if (pool == null) throw new IllegalArgumentException("unsupported horse armor pool " + kind);
        VanillaEntry entry = pool[index];
        return new int[] {entry.type(), entry.weight(), entry.minimum(), entry.maximum()};
    }

    static int horseArmorRollCount(int seed, int x, int y, int z, Kind kind) {
        if (kind == Kind.DUNGEON) {
            return vanillaRollCount(seed, x, y, z, 1, 3, DUNGEON_HORSE_ARMOR_SALT);
        }
        if (kind == Kind.DESERT_TOMB) {
            return vanillaRollCount(seed, x, y, z, 2, 4, DESERT_TOMB_HORSE_ARMOR_SALT);
        }
        return 0;
    }

    static short horseArmorRollType(
            int seed, int x, int y, int z, Kind kind, int roll) {
        VanillaEntry[] pool = horseArmorPool(kind);
        if (pool == null) return PlayerInventory.EMPTY;
        return vanillaEntry(seed, x, y, z, pool, horseArmorPoolSalt(kind), roll).type();
    }

    private static VanillaEntry[] horseArmorPool(Kind kind) {
        if (kind == Kind.DUNGEON) return DUNGEON_HORSE_ARMOR;
        if (kind == Kind.DESERT_TOMB) return DESERT_TOMB_HORSE_ARMOR;
        return null;
    }

    private static int horseArmorPoolSalt(Kind kind) {
        return kind == Kind.DUNGEON ? DUNGEON_HORSE_ARMOR_SALT
                : DESERT_TOMB_HORSE_ARMOR_SALT;
    }

    static short netheriteOverworldRollType(
            int seed, int x, int y, int z, Kind kind, int pool) {
        VanillaEntry[] entries;
        int salt;
        if (pool == 0 && kind == Kind.DUNGEON) {
            entries = NETHERRACK_CACHE;
            salt = DUNGEON_NETHERRACK_SALT;
        } else if (pool == 1 && kind == Kind.DUNGEON) {
            entries = ANCIENT_DEBRIS_CACHE;
            salt = DUNGEON_DEBRIS_SALT;
        } else if (pool == 0 && kind == Kind.MINESHAFT) {
            entries = NETHERRACK_CACHE;
            salt = MINESHAFT_NETHERRACK_SALT;
        } else if (pool == 1 && kind == Kind.MINESHAFT) {
            entries = ANCIENT_DEBRIS_CACHE;
            salt = MINESHAFT_DEBRIS_SALT;
        } else if (pool == 2 && kind == Kind.DEEP_DARK_CITY) {
            entries = NETHERITE_TEMPLATE_CACHE;
            salt = DEEP_DARK_NETHERITE_TEMPLATE_SALT;
        } else {
            return PlayerInventory.EMPTY;
        }
        return vanillaEntry(seed, x, y, z, entries, salt, 0).type();
    }

    private static VanillaEntry vanillaEntry(int seed, int x, int y, int z,
            VanillaEntry[] pool, int poolSalt, int roll) {
        int pick = bounded(lane(seed, x, y, z,
                poolSalt ^ roll * 0x9e3779b9), totalWeight(pool));
        int cumulative = 0;
        for (VanillaEntry option : pool) {
            cumulative += option.weight();
            if (pick < cumulative) return option;
        }
        throw new IllegalStateException("vanilla loot weight selection escaped its pool");
    }

    private static int vanillaAmount(int seed, int x, int y, int z,
            VanillaEntry chosen, int poolSalt, int roll) {
        return chosen.minimum() + bounded(lane(seed, x, y, z,
                poolSalt ^ roll * 0x85ebca6b ^ 0x5bf0_3635),
                chosen.maximum() - chosen.minimum() + 1);
    }

    /**
     * [LOOT-BOOKS] 책 항 하나의 저장 인챈트 {@code {id, level}}. 후보 서수와 레벨을 서로 다른 lane
     * 에서 균등하게 뽑는다 — 바닐라 {@code EnchantRandomlyFunction} 의 책 경로(후보 균등,
     * {@code Mth.nextInt(random, 1, maxLevel)})와 같은 분포다.
     */
    private static int[] vanillaBookEnchantment(int seed, int x, int y, int z,
            VanillaEntry chosen, int poolSalt, int roll) {
        String[] options = chosen.enchantOptions();
        int index = bounded(lane(seed, x, y, z,
                poolSalt ^ roll * 0xc2b2ae35 ^ BOOK_ENCHANT_SALT), options.length);
        int id = EnchantmentRules.canonicalEnchantmentId(options[index]);
        if (id < 0) throw new IllegalStateException("unknown loot book enchantment " + options[index]);
        int level = 1 + bounded(lane(seed, x, y, z,
                poolSalt ^ roll * 0x27d4eb2f ^ BOOK_LEVEL_SALT), EnchantmentRules.maxLevel(id));
        return new int[] {id, level};
    }

    private static int vanillaRollCount(int seed, int x, int y, int z,
            int minimumRolls, int maximumRolls, int poolSalt) {
        return minimumRolls + bounded(lane(seed, x, y, z,
                poolSalt ^ VANILLA_ROLLS_SALT), maximumRolls - minimumRolls + 1);
    }

    private static int totalWeight(VanillaEntry[] pool) {
        int total = 0;
        for (VanillaEntry option : pool) total += option.weight();
        return total;
    }

    static int woodlandMansionPoolWeight(int pool) {
        return totalWeight(woodlandMansionPool(pool));
    }

    static int[] woodlandMansionPoolEntry(int pool, int index) {
        VanillaEntry entry = woodlandMansionPool(pool)[index];
        return new int[] {entry.type(), entry.weight(), entry.minimum(), entry.maximum()};
    }

    static int woodlandMansionRollCount(
            int seed, int x, int y, int z, int pool) {
        return switch (pool) {
            case 0 -> vanillaRollCount(seed, x, y, z, 1, 3, MANSION_RARE_SALT);
            case 1 -> vanillaRollCount(seed, x, y, z, 1, 4, MANSION_SUPPLIES_SALT);
            case 2 -> vanillaRollCount(seed, x, y, z, 3, 3, MANSION_MOB_DROPS_SALT);
            case 3 -> vanillaRollCount(seed, x, y, z, 1, 1, MANSION_TRIM_SALT);
            default -> throw new IllegalArgumentException("unknown mansion loot pool " + pool);
        };
    }

    static short woodlandMansionRollType(
            int seed, int x, int y, int z, int pool, int roll) {
        VanillaEntry[] entries = woodlandMansionPool(pool);
        return vanillaEntry(seed, x, y, z, entries,
                woodlandMansionPoolSalt(pool), roll).type();
    }

    static int woodlandMansionRollAmount(
            int seed, int x, int y, int z, int pool, int roll) {
        VanillaEntry[] entries = woodlandMansionPool(pool);
        int salt = woodlandMansionPoolSalt(pool);
        VanillaEntry chosen = vanillaEntry(seed, x, y, z, entries, salt, roll);
        return vanillaAmount(seed, x, y, z, chosen, salt, roll);
    }

    private static VanillaEntry[] woodlandMansionPool(int pool) {
        return switch (pool) {
            case 0 -> MANSION_RARE;
            case 1 -> MANSION_SUPPLIES;
            case 2 -> MANSION_MOB_DROPS;
            case 3 -> MANSION_TRIM;
            default -> throw new IllegalArgumentException("unknown mansion loot pool " + pool);
        };
    }

    private static int woodlandMansionPoolSalt(int pool) {
        return switch (pool) {
            case 0 -> MANSION_RARE_SALT;
            case 1 -> MANSION_SUPPLIES_SALT;
            case 2 -> MANSION_MOB_DROPS_SALT;
            case 3 -> MANSION_TRIM_SALT;
            default -> throw new IllegalArgumentException("unknown mansion loot pool " + pool);
        };
    }

    private static VanillaEntry entry(short type, int weight, int minimum, int maximum) {
        return new VanillaEntry(type, weight, minimum, maximum, null);
    }

    /** [LOOT-BOOKS] {@code book} + {@code enchant_randomly options} 항. 책은 언제나 1권이다. */
    private static VanillaEntry enchantedBook(int weight, String[] enchantOptions) {
        return new VanillaEntry(PlayerInventory.ENCHANTED_BOOK, weight, 1, 1, enchantOptions);
    }

    /** [LOOT-BOOKS] 테스트용: 대저택 pool 한 굴림의 저장 인챈트, 책 항이 아니면 {@code null}. */
    static int[] woodlandMansionRollBookEnchantment(
            int seed, int x, int y, int z, int pool, int roll) {
        int salt = woodlandMansionPoolSalt(pool);
        VanillaEntry chosen = vanillaEntry(seed, x, y, z, woodlandMansionPool(pool), salt, roll);
        return chosen.enchantOptions() == null ? null
                : vanillaBookEnchantment(seed, x, y, z, chosen, salt, roll);
    }

    /** [LOOT-BOOKS] 테스트용: 책 항의 후보 목록 사본(바닐라 태그 순서). */
    static String[] onRandomLootBookOptions() { return ON_RANDOM_LOOT_BOOK_OPTIONS.clone(); }

    static String[] swiftSneakBookOptions() { return SWIFT_SNEAK_BOOK_OPTIONS.clone(); }

    public static int mundanePoolSize() { return MUNDANE.length; }

    public static short mundaneItemType(int index) { return MUNDANE[index].type; }

    public static double[] weightVector(int seed, int x, int y, int z, Kind kind) {
        double[] weights = new double[MUNDANE.length];
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            int lane = lane(seed, x, y, z, WEIGHT_SALT ^ i * 0x632be5ab);
            double shake = .45 + StructureHash.unsigned(lane) / 4294967295.0 * 1.10;
            weights[i] = baseWeight(kind, MUNDANE[i].type) * shake;
            sum += weights[i];
        }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        return weights;
    }

    public static MundaneRoll mundaneRoll(int seed, int x, int y, int z, Kind kind, int roll) {
        double[] weights = weightVector(seed, x, y, z, kind);
        double target = unit(lane(seed, x, y, z, ITEM_SALT ^ roll * 0x45d9f3b));
        int index = weights.length - 1;
        double cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (target < cumulative) { index = i; break; }
        }
        ItemOption option = MUNDANE[index];
        if (!option.stackable()) {
            int maximum = PlayerInventory.initialDurability(option.type);
            int durability = 1 + bounded(lane(seed, x, y, z,
                    CONDITION_SALT ^ roll * 0x27d4eb2d), maximum);
            return new MundaneRoll(option.type, 1, durability, false);
        }
        boolean jackpot = !isJackpotExcluded(option.type) && jackpotLane(seed, x, y, z, roll);
        int count = jackpot
                ? 10 + bounded(lane(seed, x, y, z, AMOUNT_SALT ^ roll * 0x165667b1), 15)
                : option.minimum + bounded(lane(seed, x, y, z,
                        AMOUNT_SALT ^ roll * 0x165667b1), option.maximum - option.minimum + 1);
        // 10~24개의 한 덩어리는 창고 자루가 터져 쏟아진 흔적이라는 환경 서사다.
        return new MundaneRoll(option.type, count, 0, jackpot);
    }

    public static boolean jackpotLane(int seed, int x, int y, int z, int roll) {
        return jackpotRoll(seed, x, y, z, roll) < 500;
    }

    /**
     * JACKPOT(10~24개 흔적 묶음) 대상에서 빠지는 종. 광물에 더해 [POTION] 양조 재료 두 종도
     * 제외한다 — 한 상자에서 사마귀 24개가 쏟아지면 "낮은 확률 획득 경로"라는 계약이 깨진다.
     */
    public static boolean isJackpotExcluded(short type) {
        return isMineral(type) || type == PlayerInventory.BLAZE_ROD
                || type == PlayerInventory.NETHER_WART
                // [QUARTZ] 석영도 같은 이유로 제외한다 — 한 상자에서 24개가 쏟아지면
                // "낮은 확률 획득 경로"라는 계약이 깨진다(석영 24 = 석영 블록 6).
                || type == PlayerInventory.QUARTZ
                // [COPPER] 밀랍도 같은 이유로 제외한다 — 24개면 구리 건축 전체를 한 번에
                // 봉인해 버려 산화라는 시스템 자체가 무의미해진다.
                || type == PlayerInventory.HONEYCOMB
                // [CHEST-FAMILY] 셜커 껍데기도 같은 이유로 제외한다 — 24개면 셜커 상자
                // 12개가 한 상자에서 쏟아져 "낮은 확률 획득 경로"라는 계약이 깨진다.
                || type == PlayerInventory.SHULKER_SHELL
                // [GOLD-FOOD] 황금 식품 셋도 같은 이유로 제외한다 — 후렴과 24개면 순간이동이
                // 소모품이 아니게 되고, 꿀이 든 병은 스택 상한이 16 이라 24개를 담을 수도 없다.
                || type == PlayerInventory.GOLDEN_CARROT
                || type == PlayerInventory.CHORUS_FRUIT
                || type == PlayerInventory.HONEY_BOTTLE
                // [COOKING] 코코아 콩도 같은 이유로 제외한다 — 24개면 쿠키 192개가 한
                // 상자에서 나와 "낮은 확률 획득 경로"라는 계약이 깨진다.
                || type == PlayerInventory.COCOA_BEANS;
    }

    /** 광물은 특별/평범 층 모두 JACKPOT 대상이 아니다. */
    public static boolean isMineral(short type) {
        return type == PlayerInventory.COAL || type == PlayerInventory.IRON_INGOT
                || type == PlayerInventory.GOLD_INGOT || type == PlayerInventory.LAPIS_LAZULI
                || type == PlayerInventory.REDSTONE_DUST || type == PlayerInventory.EMERALD
                || type == PlayerInventory.DIAMOND;
    }

    /** SPECIAL 내부 확정 비중(금10·청금8·레드스톤8·에메랄드5·다이아10·기존59)의 실제 산출. */
    public static short specialItemType(int seed, int x, int y, int z) {
        int choice = bounded(lane(seed, x, y, z, SPECIAL_SALT), 100);
        if (choice < 10) return PlayerInventory.GOLD_INGOT;
        if (choice < 18) return PlayerInventory.LAPIS_LAZULI;
        if (choice < 26) return PlayerInventory.REDSTONE_DUST;
        if (choice < 31) return PlayerInventory.EMERALD;
        if (choice < 41) return PlayerInventory.DIAMOND;
        return PlayerInventory.EMPTY;
    }

    public static int jackpotRoll(int seed, int x, int y, int z, int roll) {
        return bounded(lane(seed, x, y, z, JACKPOT_SALT ^ roll * 0x9e3779b9), 10_000);
    }

    public static int rollCount(int seed, int x, int y, int z, Kind kind) {
        int lane = lane(seed, x, y, z, 0x24ad83f1);
        if (kind == Kind.DUNGEON) return 3 + bounded(lane, 3);
        if (kind == Kind.MINESHAFT) return 2 + bounded(lane, 2);
        return 1 + bounded(lane, 3);
    }

    /** 실제 상자 지급 경로에서 10개 이상 stack이 생기는지 환경 표식과 공유한다. */
    public static boolean hasJackpotPayout(int seed, int x, int y, int z, Kind kind) {
        // [SHIPWRECK] 바닐라 표에는 10~24개 흔적 묶음이라는 개념이 없다.
        // [DEEP-DARK] 딥다크 도시도 전용 pool 표라 mundane jackpot lane 을 쓰지 않는다.
        // [OCEAN-RUINS] · [BURIED-TREASURE] 도 바닐라 전용 pool 표라 jackpot lane 이 없다.
        if (kind == Kind.SHIPWRECK || kind == Kind.WOODLAND_MANSION
                || kind == Kind.DEEP_DARK_CITY || kind == Kind.OCEAN_RUINS
                || kind == Kind.BURIED_TREASURE) return false;
        RuinLootProfile.Profile profile = profile(kind);
        if (RuinLootProfile.contentTier(seed, x, z, profile) == RuinLootProfile.Tier.SPECIAL) {
            return false;
        }
        int rolls = rollCount(seed, x, y, z, kind);
        for (int roll = 0; roll < rolls; roll++) {
            if (mundaneRoll(seed, x, y, z, kind, roll).jackpot()) return true;
        }
        return false;
    }

    public static RuinLootProfile.Profile profile(Kind kind) {
        return RuinLootProfile.profile(ExplorationDecorator.descriptorKind(kind));
    }

    private static void add(ChestInventory chest, MundaneRoll roll) {
        if (roll.durability > 0) chest.add(roll.itemType, 1, roll.durability);
        else chest.add(roll.itemType, roll.count);
    }

    private static void addBurialEvidence(ChestInventory chest, int seed, int x, int y, int z) {
        chest.add(PlayerInventory.BONE, 2 + bounded(lane(seed, x, y, z, 0x24a11d8b), 4));
        chest.add(PlayerInventory.GOLD_INGOT, 1 + bounded(lane(seed, x, y, z, 0x38d74f09), 2));
        chest.add((short) ((lane(seed, x, y, z, 0x47a90b65) & 1) == 0
                ? Blocks.FLOWER_RED : Blocks.FLOWER_YELLOW), 1);
    }

    private static void addSpecialLoot(ChestInventory chest, int seed, int x, int y, int z) {
        int specialLane = lane(seed, x, y, z, SPECIAL_SALT);
        SplittableRandom random = new SplittableRandom(specialLane);
        short mineral = specialItemType(seed, x, y, z);
        if (mineral != PlayerInventory.EMPTY) {
            chest.add(mineral, 1 + random.nextInt(5));
            return;
        }
        // 59% 기존 특별 풀은 광물 비중을 중복시키지 않도록 장식/장비만 유지한다.
        switch (random.nextInt(5)) {
            case 0 -> chest.add(PlayerInventory.WOOL, 1 + random.nextInt(2));
            case 1 -> chest.add(random.nextBoolean()
                    ? PlayerInventory.OAK_SAPLING : PlayerInventory.BIRCH_SAPLING, 1);
            case 2 -> addNearlyBroken(chest, random, PlayerInventory.IRON_TIER_MIN,
                    PlayerInventory.IRON_BOOTS);
            case 3 -> addNearlyBroken(chest, random, PlayerInventory.GOLD_TIER_MIN,
                    PlayerInventory.GOLD_TIER_MAX);
            default -> addNearlyBroken(chest, random, PlayerInventory.DIAMOND_TIER_MIN,
                    PlayerInventory.DIAMOND_TIER_MAX);
        }
    }

    private static void addNearlyBroken(ChestInventory chest, SplittableRandom random,
            short minimum, short maximum) {
        short type = (short) (minimum + random.nextInt(maximum - minimum + 1));
        int maximumDamage = PlayerInventory.initialDurability(type);
        chest.add(type, 1, 1 + random.nextInt(Math.max(1, maximumDamage / 10)));
    }

    private static double baseWeight(Kind kind, short type) {
        // [POTION] 양조 재료 두 종은 던전 상자에서만 나온다. 다른 프로필에서는 가중치 0 이라
        // 목록에 있어도 절대 뽑히지 않는다(풀 구조·슬롯 서수는 그대로 둔 채 확률만 0 이다).
        if (type == PlayerInventory.BLAZE_ROD) {
            return kind == Kind.DUNGEON ? BLAZE_ROD_DUNGEON_WEIGHT : 0.0;
        }
        if (type == PlayerInventory.NETHER_WART) {
            return kind == Kind.DUNGEON ? NETHER_WART_DUNGEON_WEIGHT : 0.0;
        }
        // [QUARTZ] 석영도 던전 상자 전용이다. 다른 프로필에서는 가중치 0 이라 목록에 있어도
        // 절대 뽑히지 않는다(풀 구조·슬롯 서수는 그대로 둔 채 확률만 0 이다).
        if (type == PlayerInventory.QUARTZ) {
            return kind == Kind.DUNGEON ? QUARTZ_DUNGEON_WEIGHT : 0.0;
        }
        // [COPPER] 밀랍도 던전 상자 전용이다.
        if (type == PlayerInventory.HONEYCOMB) {
            return kind == Kind.DUNGEON ? HONEYCOMB_DUNGEON_WEIGHT : 0.0;
        }
        // [CHEST-FAMILY] 셜커 껍데기도 던전 상자 전용이다.
        if (type == PlayerInventory.SHULKER_SHELL) {
            return kind == Kind.DUNGEON ? SHULKER_SHELL_DUNGEON_WEIGHT : 0.0;
        }
        // [GOLD-FOOD] 황금 당근 · 후렴과 · 꿀이 든 병도 던전 상자 전용이다. 다른 프로필에서는
        // 가중치 0 이라 목록에 있어도 절대 뽑히지 않는다.
        if (type == PlayerInventory.GOLDEN_CARROT) {
            return kind == Kind.DUNGEON ? GOLDEN_CARROT_DUNGEON_WEIGHT : 0.0;
        }
        if (type == PlayerInventory.CHORUS_FRUIT) {
            return kind == Kind.DUNGEON ? CHORUS_FRUIT_DUNGEON_WEIGHT : 0.0;
        }
        if (type == PlayerInventory.HONEY_BOTTLE) {
            return kind == Kind.DUNGEON ? HONEY_BOTTLE_DUNGEON_WEIGHT : 0.0;
        }
        // [COOKING] 코코아 콩도 던전 상자 전용이다.
        if (type == PlayerInventory.COCOA_BEANS) {
            return kind == Kind.DUNGEON ? COCOA_BEANS_DUNGEON_WEIGHT : 0.0;
        }
        if (type == PlayerInventory.COAL) return 2.60;
        if (type == PlayerInventory.IRON_INGOT) return 1.65;
        if (kind == Kind.DUNGEON && (type == PlayerInventory.ROTTEN_FLESH
                || type == PlayerInventory.BONE || type == PlayerInventory.STRING)) return 1.35;
        if ((kind == Kind.MINESHAFT || kind == Kind.MINER_CAMP)
                && (type == Blocks.DIRT || type == Blocks.GRAVEL)) return 1.35;
        if (kind == Kind.FLOODED_RUIN && type == Blocks.SUGARCANE) return .45;
        if (kind == Kind.TOMB_RUIN && (type == PlayerInventory.BONE
                || (type >= PlayerInventory.CHAINMAIL_HELMET
                && type <= PlayerInventory.CHAINMAIL_BOOTS))) return 1.25;
        return 1.0;
    }

    private static ItemOption option(short type, int minimum, int maximum) {
        return new ItemOption(type, minimum, maximum);
    }

    private static ItemOption armor(short type) { return option(type, 1, 1); }

    private static int lane(int seed, int x, int y, int z, int salt) {
        int chestKey = StructureHash.hash3(StructureHash.seedSalt(seed, 0x2c7495a1), x, y, z);
        return StructureHash.mix32(chestKey ^ salt);
    }

    private static double unit(int value) {
        return StructureHash.unsigned(value) / 4294967296.0;
    }

    private static int bounded(int value, int bound) {
        return (int) Long.remainderUnsigned(StructureHash.unsigned(value), bound);
    }
}
