package com.gameexpert.engine.raid;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 레이드 승리 보상 추첨. 입력은 {@link RaidLedger#rewardSeed(long, long)} 가 arm 시점에 고정해
 * 원장과 receipt 에 함께 영속한 <b>보상 롤 시드 하나</b>뿐이라, 재접속·재시도·크래시 복구가
 * 언제나 같은 결과를 낸다(재롤 불가). receipt 의 {@code PENDING → GRANTED} 조건부 전이는
 * 중복 청구를 막고, 이 클래스는 그 전이에 성공한 호출이 "무엇을 주는가"만 결정한다.
 *
 * <p><b>확률 계약</b>: 만분율 기준 {@code 0..9999} 한 번 뽑아 {@code <100} 이면 겉날개(1%),
 * {@code <3000} 이면 아래 전리품 풀 15종 균등(29%), 그 밖은 무지급(70%)이다. 풀은
 * 밸런스 무해(전투·경제 효과 0)를 원칙으로 하고, 인챈트 장비만 기존 낮은 티어 장비에
 * 인챈트 하나를 붙인다.
 *
 * <p>정적판 {@code client/src/backend/standalone/StandaloneRaidVictoryReward.ts} 가 같은
 * 리터럴·같은 draw 순서를 쓰며, 두 권위의 동일성은
 * {@code RaidVictoryRewardTest.PARITY_TRACE} 와 같은 문자열을 쓰는 vitest 추적이 고정한다.
 */
public final class RaidVictoryReward {

    private RaidVictoryReward() {
    }

    /** 만분율 추첨 기준. */
    public static final int ROLL_BASIS = 10_000;
    /** 겉날개 당첨 상한(1%). */
    public static final int ELYTRA_THRESHOLD = 100;
    /** 전리품 풀 당첨 상한(누적 30% = 겉날개 1% + 풀 29%). */
    public static final int POOL_THRESHOLD = 3_000;
    /** 전리품 풀 종 수. 15종 균등이다. */
    public static final int POOL_SIZE = 15;

    // ── draw 인덱스. 스트림 자리마다 고유해야 두 권위가 같은 수열을 읽는다. ──
    private static final int DRAW_TIER = 0;
    private static final int DRAW_POOL_SLOT = 1;
    private static final int DRAW_COIN_COUNT = 2;
    private static final int DRAW_POUCH_COUNT = 3;
    private static final int DRAW_GEAR_BASE = 4;
    private static final int DRAW_GEAR_ENCHANT = 5;
    private static final int DRAW_GEAR_LEVEL = 6;
    /** [ARMOR-TRIM] 잃어버린 갑옷 장식 형판 세 종 중 하나를 고르는 자리. */
    private static final int DRAW_TRIM_TEMPLATE = 7;
    /** [UTILITY] 원천이 없는 유틸리티 재료 여섯 중 하나를 고르는 자리. */
    private static final int DRAW_UTILITY_RARE = 8;

    /** 기념 주화 지급 개수 범위 1..3. */
    public static final int COIN_MIN_COUNT = 1;
    public static final int COIN_COUNT_SPREAD = 3;
    /** 꽃잎 주머니 지급 개수 범위 2..4. */
    public static final int POUCH_MIN_COUNT = 2;
    public static final int POUCH_COUNT_SPREAD = 3;

    /** 풀 슬롯 서수. 순서는 append-only이며 바꾸면 과거 시드의 보상이 달라진다. */
    public static final int SLOT_TATTERED_BANNER = 0;
    public static final int SLOT_CHERRY_BONSAI = 1;
    public static final int SLOT_COMMEMORATIVE_COIN = 2;
    public static final int SLOT_BATTERING_HORN = 3;
    public static final int SLOT_PETAL_POUCH = 4;
    public static final int SLOT_ILLAGER_MUSIC_BOX = 5;
    public static final int SLOT_ENCHANTED_GEAR = 6;
    public static final int SLOT_DIAMOND_FISHING_ROD = 7;
    /** [POTION] 블레이즈 막대 1 개. 양조대 제작식(바닐라 원본)의 유일한 네더 재료다. */
    public static final int SLOT_BLAZE_ROD = 8;
    /** [POTION] 네더 사마귀 2 개. 어색한 물약(바닐라 원본)의 재료다. */
    public static final int SLOT_NETHER_WART = 9;
    /**
     * [QUARTZ] 네더 석영 6 개. 바닐라 원천인 네더 석영 광석이 이 게임에 없어 신설한 획득
     * 경로다(재료 대체가 아니라 획득 경로 divergence).
     */
    public static final int SLOT_QUARTZ = 10;
    /**
     * [COPPER] 밀랍 4 개. 벌이 없어 바닐라 원천(벌집/벌집 상자)이 이 게임에 없으므로
     * 신설한 획득 경로다(재료 대체가 아니라 획득 경로 divergence — 네더 석영과 같은 근거).
     */
    public static final int SLOT_HONEYCOMB = 11;
    /**
     * [GOLD-FOOD] 마법이 부여된 황금 사과 1 개. 바닐라 1.9 부터 <b>제작이 불가능한</b> 전리품
     * 전용 아이템이라 획득 경로를 열지 않으면 게임 안에서 영영 볼 수 없다. 이 풀은 원래
     * "밸런스 무해" 를 원칙으로 하지만, 이 항만은 <b>사용자 지시</b>에 따라 예외로 둔다 —
     * 대신 레이드 승리 자체가 드물고 이 슬롯은 승리 1회당 29% / 13 ≈ <b>2.2%</b> 라
     * 바닐라 대저택(3.7%)보다도 낮은 실효 확률이다.
     */
    public static final int SLOT_ENCHANTED_GOLDEN_APPLE = 12;
    /**
     * [ARMOR-TRIM] 갑옷 장식 형판 1 개 — 갈비뼈(rib)·주둥이(snout) 중 균등 하나.
     * 바닐라 원천(네더 요새 · 보루 잔해 상자)이 이 제품에서 생성되지 않는다
     * ({@code Mc263ZeroOverworldStructureExecutors}: fortress · bastion_remnant). 첨탑(spire)은
     * [END-CITY] 엔드 도시 보물 상자가 바닐라대로 주므로 이 목록에 없다.
     * 재료 대체 대신 획득 경로 divergence 로 둔다(블레이즈 막대와 같은 근거). 승리당 이
     * 슬롯 29% / 15 ≈ 1.9%, 형판 한 종은 약 1% 다. 한 장을 얻으면 바닐라 복제 레시피
     * (다이아몬드 7 + 형판 + 재료 블록 → 2)로 늘릴 수 있다.
     */
    public static final int SLOT_ARMOR_TRIM_TEMPLATE = 13;
    /**
     * [UTILITY] 바닐라 원천이 이 제품에 없는 유틸리티 재료 1 개 — 네더의 별(위더) · 가스트 눈물
     * (가스트) · 음반 Tears(가스트가 반사 화염구에 죽을 때) · 음반 Pigstep(보루 잔해 상자) · 음반
     * Lava Chicken(치킨 조키 아기 좀비) · 흑암석 8(보루 잔해) 중 균등 하나. 드래곤의 숨결은 [DRAGON]
     * 엔더 드래곤전이 바닐라대로 주므로 이 목록에 없다. 재료를 대체하지 않고 획득 경로만 divergence 로
     * 둔다(블레이즈 막대와 같은 근거, docs/EXPLORATION-LOOT.md). 승리당 이 슬롯 29% / 15 ≈ 1.9%,
     * 한 종은 약 0.32% 다.
     */
    public static final int SLOT_UTILITY_RARE = 14;
    /** [UTILITY] 원천이 없는 유틸리티 재료 여섯. 순서가 곧 draw 결과라 배포 뒤로는 append-only 다. */
    private static final short[] LOST_UTILITY_ITEMS = {
        PlayerInventory.NETHER_STAR,
        PlayerInventory.GHAST_TEAR,
        PlayerInventory.MUSIC_DISC_TEARS,
        PlayerInventory.MUSIC_DISC_PIGSTEP,
        PlayerInventory.MUSIC_DISC_LAVA_CHICKEN,
        // [UTILITY] 흑암석(주둥이 형판 복제 재료). 네더 보루 잔해가 없어 이 자리가 유일한 원천이다.
        PlayerInventory.BLACKSTONE,
    };
    /** {@link #LOST_UTILITY_ITEMS} 와 같은 순서의 개수. 흑암석만 한 묶음(8)이고 나머지는 1개다. */
    private static final int[] LOST_UTILITY_COUNTS = {1, 1, 1, 1, 1, 8};
    /** [ARMOR-TRIM] 원천 구조물이 없는 형판 두 종. 순서가 곧 draw 결과라 배포 뒤로는 append-only 다. */
    private static final short[] LOST_TRIM_TEMPLATES = {
        PlayerInventory.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
        PlayerInventory.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
    };

    /** 블레이즈 막대 지급 개수. 양조대 하나가 막대 1 개라 고정 1 이다(새 draw 자리를 쓰지 않는다). */
    public static final int BLAZE_ROD_COUNT = 1;
    /** 네더 사마귀 지급 개수. 어색한 물약 두 병 분량으로 고정 2 다(새 draw 자리를 쓰지 않는다). */
    public static final int NETHER_WART_COUNT = 2;
    /**
     * [QUARTZ] 네더 석영 지급 개수. 건축 재료라 낱개가 아니라 <b>뭉치</b>로 준다 — 석영 4 개가
     * 석영 블록 1 개이므로 6 은 석영 블록 1 개 + 다음 블록의 절반이고, 승리를 두 번 하면
     * 석영 블록 3 개(= 계단 2 개 또는 반 블록 6 개)를 만들 수 있는 최소 단위다. 고정 개수라
     * 새 draw 자리를 쓰지 않는다.
     */
    public static final int QUARTZ_COUNT = 6;
    /**
     * [COPPER] 밀랍 지급 개수. 밀랍 1 개가 블록 1 개를 봉인하므로 4 는 <b>산화 4단계 한 벌</b>
     * 또는 건축 한 면(2×2)을 봉인할 수 있는 최소 단위다. 고정 개수라 새 draw 자리를 쓰지 않는다.
     */
    public static final int HONEYCOMB_COUNT = 4;
    /**
     * [GOLD-FOOD] 마법이 부여된 황금 사과 지급 개수. 바닐라 전리품 표도 대부분 1 개라 1 로
     * 고정한다(고정 개수라 새 draw 자리를 쓰지 않는다).
     */
    public static final int ENCHANTED_GOLDEN_APPLE_COUNT = 1;

    /**
     * 인챈트 장비 후보. 새 아이템 ID 를 쓰지 않도록 기존 낮은 티어 장비만 담는다.
     * 각 행은 {@code {itemType, enchantId, maxLevel}} 이며 인챈트는 그 아이템에 실제로 붙는
     * 종류만 나열한다({@link EnchantmentRules#appliesTo}). 표를 여기에 명시적으로 두는 이유는
     * 정적판이 {@code appliesTo} 분기를 복제하지 않고도 같은 후보를 얻게 하기 위해서다.
     */
    private static final int[][] GEAR_BASES = {
        {PlayerInventory.IRON_SWORD, EnchantmentRules.SHARPNESS, 5, EnchantmentRules.UNBREAKING, 3},
        {PlayerInventory.IRON_PICKAXE, EnchantmentRules.EFFICIENCY, 5, EnchantmentRules.FORTUNE, 3},
        {PlayerInventory.IRON_AXE, EnchantmentRules.EFFICIENCY, 5, EnchantmentRules.SHARPNESS, 5},
        {PlayerInventory.IRON_SHOVEL, EnchantmentRules.EFFICIENCY, 5, EnchantmentRules.SILK_TOUCH, 1},
        {PlayerInventory.STONE_SWORD, EnchantmentRules.SHARPNESS, 5, EnchantmentRules.UNBREAKING, 3},
        {PlayerInventory.BOW, EnchantmentRules.POWER, 5, EnchantmentRules.UNBREAKING, 3},
    };

    /** 인챈트 장비 후보 수. */
    public static final int GEAR_BASE_COUNT = GEAR_BASES.length;
    /** 후보 한 종마다 붙을 수 있는 인챈트 수. */
    public static final int GEAR_ENCHANT_COUNT = 2;

    /**
     * 한 번의 승리가 주는 전부. {@code itemType == 0} 이면 무지급이다.
     *
     * @param itemType     지급할 아이템/블록 프로토콜 ID
     * @param count        지급 개수
     * @param durability   내구가 있는 아이템의 초기 내구(없으면 0)
     * @param enchantments {@link EnchantmentRules} 압축 마스크(없으면 0)
     */
    public record Prize(short itemType, int count, int durability, long enchantments) {

        public boolean isNothing() {
            return itemType == PlayerInventory.EMPTY || count <= 0;
        }
    }

    /** 무지급 결과. */
    public static final Prize NOTHING = new Prize((short) PlayerInventory.EMPTY, 0, 0, 0);

    /**
     * 시드 하나에서 draw 자리마다 독립 난수를 만든다. splitmix64 를 쓰므로 시드가 인접해도
     * 자리별 값이 상관되지 않고, 두 언어가 같은 64비트 연산만으로 재현할 수 있다.
     */
    static long stream(long rewardSeed, int drawIndex) {
        long z = rewardSeed + 0x9E3779B97F4A7C15L * (drawIndex + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** {@code [0, bound)} 균등 추첨. 부호 없는 나머지를 쓰므로 음수 시드도 같은 분포다. */
    static int draw(long rewardSeed, int drawIndex, int bound) {
        return (int) Long.remainderUnsigned(stream(rewardSeed, drawIndex), bound);
    }

    /** 이 시드가 뽑는 보상. 같은 시드는 언제나 같은 {@link Prize} 다. */
    public static Prize roll(long rewardSeed) {
        int tier = draw(rewardSeed, DRAW_TIER, ROLL_BASIS);
        if (tier < ELYTRA_THRESHOLD) return stack(PlayerInventory.ELYTRA, 1);
        if (tier >= POOL_THRESHOLD) return NOTHING;
        return poolPrize(rewardSeed, draw(rewardSeed, DRAW_POOL_SLOT, POOL_SIZE));
    }

    /** 풀 슬롯 하나가 주는 실제 아이템. */
    static Prize poolPrize(long rewardSeed, int slot) {
        return switch (slot) {
            case SLOT_TATTERED_BANNER -> stack((short) com.gameexpert.terrain.Blocks.TATTERED_BANNER, 1);
            case SLOT_CHERRY_BONSAI -> stack((short) com.gameexpert.terrain.Blocks.CHERRY_BONSAI, 1);
            case SLOT_COMMEMORATIVE_COIN -> stack(PlayerInventory.COMMEMORATIVE_COIN,
                    COIN_MIN_COUNT + draw(rewardSeed, DRAW_COIN_COUNT, COIN_COUNT_SPREAD));
            case SLOT_BATTERING_HORN -> stack(PlayerInventory.BATTERING_HORN, 1);
            case SLOT_PETAL_POUCH -> stack(PlayerInventory.PETAL_POUCH,
                    POUCH_MIN_COUNT + draw(rewardSeed, DRAW_POUCH_COUNT, POUCH_COUNT_SPREAD));
            case SLOT_ILLAGER_MUSIC_BOX -> stack(PlayerInventory.ILLAGER_MUSIC_BOX, 1);
            case SLOT_ENCHANTED_GEAR -> enchantedGear(rewardSeed);
            case SLOT_DIAMOND_FISHING_ROD -> stack(PlayerInventory.DIAMOND_FISHING_ROD, 1);
            case SLOT_BLAZE_ROD -> stack(PlayerInventory.BLAZE_ROD, BLAZE_ROD_COUNT);
            case SLOT_NETHER_WART -> stack(PlayerInventory.NETHER_WART, NETHER_WART_COUNT);
            case SLOT_QUARTZ -> stack(PlayerInventory.QUARTZ, QUARTZ_COUNT);
            case SLOT_HONEYCOMB -> stack(PlayerInventory.HONEYCOMB, HONEYCOMB_COUNT);
            case SLOT_ENCHANTED_GOLDEN_APPLE ->
                    stack(PlayerInventory.ENCHANTED_GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE_COUNT);
            case SLOT_ARMOR_TRIM_TEMPLATE -> stack(LOST_TRIM_TEMPLATES[
                    draw(rewardSeed, DRAW_TRIM_TEMPLATE, LOST_TRIM_TEMPLATES.length)], 1);
            case SLOT_UTILITY_RARE -> {
                int choice = draw(rewardSeed, DRAW_UTILITY_RARE, LOST_UTILITY_ITEMS.length);
                yield stack(LOST_UTILITY_ITEMS[choice], LOST_UTILITY_COUNTS[choice]);
            }
            default -> NOTHING;
        };
    }

    private static Prize stack(short itemType, int count) {
        return new Prize(itemType, count, PlayerInventory.initialDurability(itemType), 0);
    }

    /** 기존 장비 하나에 인챈트 하나. 인챈트 종류와 레벨도 같은 롤 시드가 정한다. */
    private static Prize enchantedGear(long rewardSeed) {
        int[] base = GEAR_BASES[draw(rewardSeed, DRAW_GEAR_BASE, GEAR_BASE_COUNT)];
        int choice = draw(rewardSeed, DRAW_GEAR_ENCHANT, GEAR_ENCHANT_COUNT);
        int enchantId = base[1 + choice * 2];
        int maxLevel = base[2 + choice * 2];
        int level = 1 + draw(rewardSeed, DRAW_GEAR_LEVEL, maxLevel);
        short itemType = (short) base[0];
        return new Prize(itemType, 1, PlayerInventory.initialDurability(itemType),
                EnchantmentRules.withEnchantLevel(EnchantmentRules.EMPTY_ENCHANTMENTS,
                        enchantId, level));
    }

    /** 가드 테스트가 후보 표를 그대로 검증할 수 있게 노출한다. */
    public static int[][] gearBases() {
        int[][] copy = new int[GEAR_BASES.length][];
        for (int index = 0; index < GEAR_BASES.length; index++) {
            copy[index] = GEAR_BASES[index].clone();
        }
        return copy;
    }

    /**
     * 이 아이템이 레이드 승리 보상으로만 얻을 수 있는 종인가.
     *
     * <p>[POTION] 블레이즈 막대·네더 사마귀는 풀에 있지만 <b>승리 전용이 아니다</b> — 탐험 상자
     * 전리품에서도 나오므로 봉인 집합(겉날개 + 전리품 7종)에 넣지 않는다.
     * [QUARTZ] 네더 석영도 같은 이유로 승리 전용이 아니다(던전 상자에서도 나온다).
     * [COPPER] 밀랍도 같은 이유로 승리 전용이 아니다.
     * [GOLD-FOOD] 마법이 부여된 황금 사과도 승리 전용이 아니다 — 대저택 상자에서도 나온다.
     * [ARMOR-TRIM] 갑옷 장식 형판도 승리 전용이 아니다 — 복제 레시피가 있다.
     */
    public static boolean isVictoryOnlyItem(int itemType) {
        return itemType == PlayerInventory.ELYTRA
                || itemType >= com.gameexpert.terrain.Blocks.TATTERED_BANNER
                        && itemType <= com.gameexpert.terrain.Blocks.DIAMOND_FISHING_ROD;
    }
}
