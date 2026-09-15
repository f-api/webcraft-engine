package com.gameexpert.engine.enchant;

import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * [SURV-X] 인챈트의 인코딩·적용 대상·효과 크기·인챈트대 제안 알고리즘을 모은 순수 규칙.
 *
 * <p><b>인코딩 규약</b>: 한 아이템의 인챈트 목록은 <b>정수 하나</b>에 담는다.
 * 기존 슬롯 위치를 보존하고 폭발로부터 보호를 추가한 16종 × 3비트 = 48비트다.
 * 슬롯 폭은 원래 4비트였으나 [FISHING-LOOT]가 미끼·바다의 행운을 append 하면서 9종이 되어
 * 4비트로는 36비트가 필요해졌다. 어떤 인챈트의 최대 레벨도 5라 3비트(0~7)면 충분하므로
 * <b>ID 순서는 그대로 두고 슬롯 폭만 3비트로 좁혔다</b>. 마스크는 저장·전송 형식이므로
 * 이 폭이 바뀌면 이전 세이브의 마스크는 다시 읽을 수 없다(CONTRACT §3 · MC-REFERENCE 낚시 절).
 * Java 순수 규칙과 DB/와이어는 {@code long}, Rust는 {@code u64}, TypeScript는 2^53 미만의
 * 안전 정수 {@code number}를 쓴다. 표시용 목록은 클라이언트에서 파생한다.
 *
 * <p><b>[ENCHANT-WIDE] 43종 확장</b>: 바닐라 26.3 의 나머지 27종은 ID 16..42 로 append 되고, 같은
 * 3비트 칸을 48비트 워드 두 개(워드 1·2)에 이어 담는다({@link WideEnchantments}). 위 48비트 마스크는
 * 워드 0 그대로라 옛 세이브·와이어·전리품 정체성은 하나도 다시 묶이지 않는다. 확장 워드는 스택 성분
 * ({@code ItemComponentData})에 살고, 와이어는 확장이 있을 때만 전체 집합을 16진 문자열로 싣는다.
 *
 * <p><b>고정소수 규약</b>: SURV-H {@code HungerRules}가 세운 1/1000 단위 정수(milli) 규약을 그대로 쓴다.
 * 바닐라가 float로 적는 값(날카로움 {@code 0.5*n + 0.5}, 힘, 보호 계수, 행운의 부싯돌 확률)은 모두
 * milli/permille 정수로만 계산한다. 이 파일에 {@code Math.sqrt}나 부동소수 누적은 없다.
 *
 * <p><b>난수</b>: 인챈트 제안은 {@link EnchantRandom}(32비트 xorshift)만 쓴다. 두 권위가 같은 시드에서
 * 비트 단위로 같은 수열을 내야 제안 3줄이 서버·정적판에서 일치한다.
 */
public final class EnchantmentRules {

    private EnchantmentRules() {
    }

    /** 고정소수 배율(1.0 = 1000). */
    public static final int MILLI = 1000;

    // ── 인챈트 ID = 마스크 슬롯 인덱스. 시프트는 id*3 다. 순서는 append-only. ──
    /** 효율(채굴 속도) V. */
    public static final int EFFICIENCY = 0;
    /** 내구성 III. */
    public static final int UNBREAKING = 1;
    /** 날카로움 V. */
    public static final int SHARPNESS = 2;
    /** 보호 IV. */
    public static final int PROTECTION = 3;
    /** 섬세한 손길 I. */
    public static final int SILK_TOUCH = 4;
    /** 행운 III. */
    public static final int FORTUNE = 5;
    /** 힘 V. */
    public static final int POWER = 6;
    /** 미끼 III(낚싯대). 바닐라 lure — 입질 대기를 레벨당 100 바닐라 틱 줄인다. */
    public static final int LURE = 7;
    /** 바다의 행운 III(낚싯대). 바닐라 luck_of_the_sea — 전리품 품질 보정에 쓰는 luck 이다. */
    public static final int LUCK_OF_THE_SEA = 8;
    /**
     * 약탈 III(검). 바닐라 looting — {@code minecraft:equipment_drops} 효과로 몹 드랍 개수와
     * 생성 장비 드랍 확률을 올린다. 바닐라 {@code looting.json} 의
     * {@code supported_items} 는 {@code #minecraft:enchantable/sword} 하나뿐이라 도끼는 대상이 아니다.
     */
    public static final int LOOTING = 9;
    /** 충성 III(삼지창 회수). */
    public static final int LOYALTY = 10;
    /** 급류 III(젖은 사용자 이동). */
    public static final int RIPTIDE = 11;
    /** 집전 I(뇌우 번개 소환). */
    public static final int CHANNELING = 12;
    /**
     * [CURSE] 결속의 저주 I(방어구). 바닐라 binding_curse — 착용한 방어구를 인벤토리에서 벗을 수 없다.
     * 인챈트대·모루 어느 쪽으로도 새로 붙지 않고 전리품(고대 도시 등)으로만 들어온다.
     */
    public static final int BINDING_CURSE = 13;
    /**
     * [CURSE] 소실의 저주 I. 바닐라 vanishing_curse — 사망 시 드랍되지 않고 사라진다.
     * 결속의 저주와 같이 인챈트대·모루로는 붙지 않는다.
     */
    public static final int VANISHING_CURSE = 14;
    /** 폭발로부터 보호 IV. 기존 15개 슬롯 뒤에만 추가한다. */
    public static final int BLAST_PROTECTION = 15;

    // ── [ENCHANT-WIDE] ID 16..42: 바닐라 26.3 의 나머지 27종을 레지스트리(키 알파벳) 순서로 append.
    // 워드 1(ID 16..31)·워드 2(ID 32..42)에 들어간다({@link WideEnchantments}). 근거는 핀 jar
    // data/minecraft/enchantment/*.json 이며 src/test/resources/mc263/enchantment-registry-263.json
    // 이 같은 표를 세 언어 테스트에 공급한다.
    /** 친수성 I(투구). */
    public static final int AQUA_AFFINITY = 16;
    /** 살충 V(무기). */
    public static final int BANE_OF_ARTHROPODS = 17;
    /** 파괴 IV(철퇴 breach). [MACE] 철퇴({@code PlayerInventory.MACE})에 붙는다. */
    public static final int BREACH = 18;
    /** 밀도 V(철퇴). [MACE] 철퇴({@code PlayerInventory.MACE})에 붙는다. */
    public static final int DENSITY = 19;
    /** 물갈퀴 III(장화). */
    public static final int DEPTH_STRIDER = 20;
    /** 가벼운 착지 IV(장화). */
    public static final int FEATHER_FALLING = 21;
    /** 발화 II(근접 무기). */
    public static final int FIRE_ASPECT = 22;
    /** 화염으로부터 보호 IV. */
    public static final int FIRE_PROTECTION = 23;
    /** 화염 I(활). */
    public static final int FLAME = 24;
    /** 차가운 걸음 II(장화, 보물). */
    public static final int FROST_WALKER = 25;
    /** 찌르기 V(삼지창). */
    public static final int IMPALING = 26;
    /** 무한 I(활). */
    public static final int INFINITY = 27;
    /** 밀치기 II(근접 무기). */
    public static final int KNOCKBACK = 28;
    /** 돌진 III(창). */
    public static final int LUNGE = 29;
    /** 수선 I(보물). */
    public static final int MENDING = 30;
    /** 다중 발사 I(석궁). */
    public static final int MULTISHOT = 31;
    /** 관통 IV(석궁). */
    public static final int PIERCING = 32;
    /** 발사체로부터 보호 IV. */
    public static final int PROJECTILE_PROTECTION = 33;
    /** 밀어내기 II(활). */
    public static final int PUNCH = 34;
    /** 빠른 장전 III(석궁). */
    public static final int QUICK_CHARGE = 35;
    /** 호흡 III(투구). */
    public static final int RESPIRATION = 36;
    /** 강타 V(무기). */
    public static final int SMITE = 37;
    /** 영혼 가속 III(장화, 보물). */
    public static final int SOUL_SPEED = 38;
    /** 휘몰아치는 칼날 III(검). */
    public static final int SWEEPING_EDGE = 39;
    /** 신속한 잠행 III(각반, 보물). */
    public static final int SWIFT_SNEAK = 40;
    /** 가시 III(방어구). */
    public static final int THORNS = 41;
    /** 돌풍 III(철퇴, 보물). [MACE] 철퇴({@code PlayerInventory.MACE})에 붙는다. */
    public static final int WIND_BURST = 42;

    /** 바닐라 26.3 인챈트 전체 수(ID 0..42). */
    public static final int ENCHANTMENT_COUNT = 43;
    /** 역사적 48비트 마스크(워드 0)에 담기는 인챈트 수(ID 0..15). */
    public static final int LEGACY_ENCHANTMENT_COUNT = 16;
    /** 인챈트 하나가 쓰는 비트 폭. */
    public static final int ENCHANT_BITS = 3;
    /** 레벨 자리 마스크(0~7). 어떤 인챈트의 최대 레벨도 5라 3비트로 충분하다. */
    public static final int ENCHANT_LEVEL_MASK = 0x7;
    /** 인챈트가 하나도 없는 마스크. */
    public static final int EMPTY_ENCHANTMENTS = 0;
    /**
     * 워드 0 마스크가 쓰는 비트 전부(16종 × 3비트 = 48비트). {@code long enchantments} 칸의 상한이다.
     * 그 위 비트는 와이어·DB 어느 쪽에서 와도 세워져 있으면 안 된다. ID 16 이상은 이 칸이 아니라
     * 스택 성분의 확장 워드({@link WideEnchantments#word1()}·{@link WideEnchantments#word2()})에 산다.
     *
     * <p>[PIGLIN-LOOTING] 약탈이 append 되면서 27비트 → 30비트로 <b>넓어졌다</b>. 상한은
     * 느슨해지기만 하므로 이전 세이브의 27비트 마스크는 그대로 읽히고, 슬롯 폭·ID 순서는
     * 건드리지 않았다(폭이 바뀌면 옛 마스크를 못 읽는다 — 낚시 절의 교훈).
     */
    public static final long ENCHANT_MASK_LIMIT =
            (1L << (LEGACY_ENCHANTMENT_COUNT * ENCHANT_BITS)) - 1L;

    /** 바닐라 레지스트리 키(ID 순서). */
    private static final String[] KEYS = {
        "efficiency", "unbreaking", "sharpness", "protection", "silk_touch", "fortune", "power",
        "lure", "luck_of_the_sea", "looting", "loyalty", "riptide", "channeling", "binding_curse",
        "vanishing_curse", "blast_protection",
        "aqua_affinity", "bane_of_arthropods", "breach", "density", "depth_strider",
        "feather_falling", "fire_aspect", "fire_protection", "flame", "frost_walker", "impaling",
        "infinity", "knockback", "lunge", "mending", "multishot", "piercing",
        "projectile_protection", "punch", "quick_charge", "respiration", "smite", "soul_speed",
        "sweeping_edge", "swift_sneak", "thorns", "wind_burst",
    };
    /** 인챈트별 최대 레벨(ID 순서, 바닐라 {@code max_level}). */
    private static final int[] MAX_LEVELS = {5, 3, 5, 4, 1, 3, 5, 3, 3, 3, 3, 3, 1, 1, 1, 4,
        1, 5, 4, 5, 3, 4, 2, 4, 1, 2, 5, 1, 2, 3, 1, 1, 4, 4, 2, 3, 3, 5, 3, 3, 3, 3, 3};
    /** 인챈트별 가중치(ID 순서, 바닐라 {@code weight}). */
    private static final int[] WEIGHTS = {10, 5, 10, 10, 1, 2, 10, 2, 2, 2, 5, 2, 1, 1, 1, 2,
        2, 5, 2, 5, 2, 5, 2, 5, 2, 2, 2, 1, 5, 5, 2, 2, 10, 5, 2, 5, 2, 5, 1, 2, 1, 1, 2};
    /** 모루 비용 계수(ID 순서, 바닐라 {@code anvil_cost}). 책 재료는 절반(최소 1)을 쓴다. */
    private static final int[] ANVIL_COSTS = {1, 2, 1, 1, 8, 4, 1, 4, 4, 4, 2, 4, 8, 8, 8, 4,
        4, 2, 4, 2, 4, 2, 4, 2, 4, 4, 4, 8, 2, 2, 4, 4, 1, 2, 4, 2, 4, 2, 8, 4, 8, 8, 4};
    /** {@code min_cost} = base + per_level_above_first × (level - 1). */
    private static final int[] MIN_COST_BASE = {1, 5, 1, 1, 15, 15, 1, 15, 15, 15, 12, 17, 25, 25,
        25, 5, 1, 5, 15, 5, 10, 5, 10, 10, 20, 10, 1, 20, 5, 5, 25, 20, 1, 3, 12, 12, 10, 5, 10, 5,
        25, 10, 15};
    private static final int[] MIN_COST_PER_LEVEL = {10, 8, 11, 11, 0, 9, 10, 9, 9, 9, 7, 7, 0, 0,
        0, 8, 0, 8, 9, 8, 10, 6, 20, 8, 0, 10, 8, 0, 20, 8, 25, 0, 10, 6, 20, 20, 10, 8, 10, 9,
        25, 20, 9};
    /** {@code max_cost} = base + per_level_above_first × (level - 1). */
    private static final int[] MAX_COST_BASE = {51, 55, 21, 12, 65, 65, 16, 65, 65, 65, 50, 50,
        50, 50, 50, 13, 41, 25, 65, 25, 25, 11, 60, 18, 50, 25, 21, 50, 55, 25, 75, 50, 50, 9, 37,
        50, 40, 25, 25, 20, 75, 60, 65};
    private static final int[] MAX_COST_PER_LEVEL = {10, 8, 11, 11, 0, 9, 10, 9, 9, 9, 0, 0, 0,
        0, 0, 8, 0, 8, 9, 8, 10, 6, 20, 8, 0, 10, 8, 0, 20, 8, 25, 0, 0, 6, 20, 0, 10, 8, 10, 9,
        25, 20, 9};
    /**
     * 바닐라 {@code #minecraft:treasure}. 인챈트대·거래 장비·몹 생성 장비에는 나오지 않고
     * 주민 책 가격이 두 배가 된다({@code #double_trade_price}). 저주 둘도 여기 속한다.
     */
    private static final boolean[] TREASURE = new boolean[ENCHANTMENT_COUNT];
    /** 바닐라 {@code #minecraft:on_random_loot} = non_treasure + 두 저주 + 차가운 걸음 + 수선. */
    private static final boolean[] ON_RANDOM_LOOT = new boolean[ENCHANTMENT_COUNT];
    /** 표시용 이름(ID 순서, 한국어 바닐라 번역). UI가 제안 줄·툴팁에 붙인다. */
    private static final String[] NAMES = {
        "효율", "내구성", "날카로움", "보호", "섬세한 손길", "행운", "힘", "미끼", "바다의 행운",
        "약탈", "충성", "급류", "집전", "결속의 저주", "소실의 저주", "폭발로부터 보호",
        "친수성", "살충", "파괴", "밀도", "물갈퀴", "가벼운 착지", "발화", "화염으로부터 보호",
        "화염", "차가운 걸음", "찌르기", "무한", "밀치기", "돌진", "수선", "다중 발사", "관통",
        "발사체로부터 보호", "밀어내기", "빠른 장전", "호흡", "강타", "영혼 가속", "휘몰아치는 칼날",
        "신속한 잠행", "가시", "돌풍",
    };
    /**
     * 바닐라 배타 집합 태그({@code #minecraft:exclusive_set/*}). 같은 집합 번호끼리는 함께 붙지
     * 않는다. 0 은 집합 없음. 여러 집합에 걸친 인챈트(수선: bow, 급류: riptide 의 소유자)는
     * {@link #compatible} 이 따로 다룬다.
     */
    private static final int SET_NONE = 0;
    private static final int SET_ARMOR = 1;
    private static final int SET_BOOTS = 2;
    private static final int SET_CROSSBOW = 3;
    private static final int SET_DAMAGE = 4;
    private static final int SET_MINING = 5;
    private static final int[] EXCLUSIVE_SET = new int[ENCHANTMENT_COUNT];
    /** 바닐라 {@code #minecraft:tooltip_order}. 툴팁은 이 순서로 인챈트를 나열한다. */
    private static final int[] TOOLTIP_ORDER = {
        BINDING_CURSE, VANISHING_CURSE, RIPTIDE, CHANNELING, WIND_BURST, FROST_WALKER, LUNGE,
        SHARPNESS, SMITE, BANE_OF_ARTHROPODS, IMPALING, POWER, DENSITY, BREACH, PIERCING,
        SWEEPING_EDGE, MULTISHOT, FIRE_ASPECT, FLAME, KNOCKBACK, PUNCH, PROTECTION,
        BLAST_PROTECTION, FIRE_PROTECTION, PROJECTILE_PROTECTION, FEATHER_FALLING, FORTUNE,
        LOOTING, SILK_TOUCH, LUCK_OF_THE_SEA, EFFICIENCY, QUICK_CHARGE, LURE, RESPIRATION,
        AQUA_AFFINITY, SOUL_SPEED, SWIFT_SNEAK, DEPTH_STRIDER, THORNS, LOYALTY, UNBREAKING,
        INFINITY, MENDING,
    };

    static {
        for (int id : new int[] {BINDING_CURSE, VANISHING_CURSE, SWIFT_SNEAK, SOUL_SPEED,
                FROST_WALKER, MENDING, WIND_BURST}) {
            TREASURE[id] = true;
        }
        for (int id = 0; id < ENCHANTMENT_COUNT; id++) {
            ON_RANDOM_LOOT[id] = !TREASURE[id];
        }
        for (int id : new int[] {BINDING_CURSE, VANISHING_CURSE, FROST_WALKER, MENDING}) {
            ON_RANDOM_LOOT[id] = true;
        }
        for (int id : new int[] {PROTECTION, BLAST_PROTECTION, FIRE_PROTECTION,
                PROJECTILE_PROTECTION}) {
            EXCLUSIVE_SET[id] = SET_ARMOR;
        }
        EXCLUSIVE_SET[FROST_WALKER] = SET_BOOTS;
        EXCLUSIVE_SET[DEPTH_STRIDER] = SET_BOOTS;
        EXCLUSIVE_SET[MULTISHOT] = SET_CROSSBOW;
        EXCLUSIVE_SET[PIERCING] = SET_CROSSBOW;
        for (int id : new int[] {SHARPNESS, SMITE, BANE_OF_ARTHROPODS, IMPALING, DENSITY, BREACH}) {
            EXCLUSIVE_SET[id] = SET_DAMAGE;
        }
        EXCLUSIVE_SET[FORTUNE] = SET_MINING;
        EXCLUSIVE_SET[SILK_TOUCH] = SET_MINING;
    }

    /**
     * [CURSE] 저주 슬롯만 세운 마스크. 숫돌이 남길 비트와 벗겨낼 비트를 가르는 정본이다.
     * 저주의 최대 레벨은 1이지만 슬롯 폭 전체({@link #ENCHANT_LEVEL_MASK})를 세워 두어야
     * 손상된 마스크의 남는 비트가 숫돌을 통과해 살아남지 않는다.
     */
    private static final long CURSE_SLOT_MASK =
            ((long) ENCHANT_LEVEL_MASK << (BINDING_CURSE * ENCHANT_BITS))
            | ((long) ENCHANT_LEVEL_MASK << (VANISHING_CURSE * ENCHANT_BITS));
    /** [CURSE] 넓은 집합의 저주 칸. 저주는 모두 워드 0 에 있다. */
    private static final WideEnchantments CURSE_SLOTS = WideEnchantments.legacy(CURSE_SLOT_MASK);

    // ── 인챈트대 비용식(바닐라 EnchantmentMenu) ──
    /** 책장 power 상한. */
    public static final int MAX_BOOKSHELF_POWER = 15;
    /** 제안 줄 수. */
    public static final int ENCHANT_OFFER_COUNT = 3;
    /** {@code base}의 고정 가산분. */
    public static final int COST_BASE_MIN = 1;
    /** {@code base}의 첫 난수 상한(바닐라 {@code rand(8)}). */
    public static final int COST_BASE_ROLL_BOUND = 8;

    // ── 레벨 보정(바닐라 EnchantmentHelper.getEnchantmentCost) ──
    /** 삼각분포 난수의 상한(바닐라 {@code nextFloat()} 두 번의 정수판). */
    public static final int TRIANGULAR_ROLL_BOUND = MILLI + 1;
    /** 삼각분포 보정 폭 ±15%(permille). */
    public static final int TRIANGULAR_SPREAD_PERMILLE = 150;
    /** 보정 후 레벨 상한. */
    public static final int MAX_ENCHANT_LEVEL_ROLL = MILLI;
    /** 추가 인챈트 추첨의 난수 상한(바닐라 {@code rand(50) <= level}). */
    public static final int EXTRA_ENCHANT_ROLL_BOUND = 50;

    // ── enchantability(바닐라 아이템 재질별 값) ──
    /** 나무 도구 15. */
    public static final int ENCHANTABILITY_WOOD = 15;
    /** 돌 도구 5. */
    public static final int ENCHANTABILITY_STONE = 5;
    /** 철 도구 14. */
    public static final int ENCHANTABILITY_IRON = 14;
    /** 구리 도구 13(Minecraft 1.21.9 ToolMaterial.COPPER). */
    public static final int ENCHANTABILITY_COPPER = 13;
    /** 다이아 도구·방어구 10. */
    public static final int ENCHANTABILITY_DIAMOND = 10;
    /** 네더라이트 도구·방어구 15. */
    public static final int ENCHANTABILITY_NETHERITE = 15;
    /** 금 도구 22. */
    public static final int ENCHANTABILITY_GOLD = 22;
    /** 가죽 방어구 15. */
    public static final int ENCHANTABILITY_LEATHER = 15;
    /** 사슬 방어구 12. */
    public static final int ENCHANTABILITY_CHAIN = 12;
    /** 철 방어구 9. */
    public static final int ENCHANTABILITY_IRON_ARMOR = 9;
    /** 구리 방어구 8(Minecraft 1.21.9 ArmorMaterials.COPPER). */
    public static final int ENCHANTABILITY_COPPER_ARMOR = 8;
    /** 금 방어구 25. */
    public static final int ENCHANTABILITY_GOLD_ARMOR = 25;
    /** 활 1. */
    public static final int ENCHANTABILITY_BOW = 1;
    /** [ENCHANT-WIDE] 석궁 1(바닐라 {@code Items.CROSSBOW} 의 {@code enchantable(1)}). */
    public static final int ENCHANTABILITY_CROSSBOW = 1;
    /** 낚싯대 1(바닐라 {@code fishing_rod} 의 {@code enchantable.value}). */
    public static final int ENCHANTABILITY_FISHING_ROD = 1;
    /** 삼지창 1. */
    public static final int ENCHANTABILITY_TRIDENT = 1;
    /** [MACE] 철퇴 15(바닐라 {@code Items.MACE} 의 {@code enchantable(15)}). */
    public static final int ENCHANTABILITY_MACE = 15;
    /** 양털 가위 15. */
    public static final int ENCHANTABILITY_SHEARS = 15;

    // ── 효과 크기 ──
    /** 보호 EPF 합 상한. */
    public static final int PROTECTION_EPF_CAP = 20;
    /** 보호 감쇄 분모(바닐라 {@code 1 - epf/25}). */
    public static final int PROTECTION_EPF_DIVISOR = 25;
    /** 날카로움·힘의 레벨당 추가 피해 0.5(milli). */
    public static final int DAMAGE_PER_LEVEL_MILLI = MILLI / 2;
    /** 날카로움·힘의 고정 추가 피해 0.5(milli). */
    public static final int DAMAGE_FLAT_MILLI = MILLI / 2;
    /** 방어구 내구성 판정이 무시되는 확률 60%(permille). 바닐라 {@code nextFloat() < 0.6f}. */
    public static final int UNBREAKING_ARMOR_BYPASS_PERMILLE = 600;
    /** 자갈 → 부싯돌 확률(행운 0/1/2/3, permille). */
    private static final int[] GRAVEL_FLINT_PERMILLE = {100, 140, 250, MILLI};

    // ── 마스크 API ──

    /**
     * 워드 0 마스크에서 인챈트 레벨을 꺼낸다. 없으면 0. ID 16 이상은 이 마스크에 칸이 없어 언제나
     * 0 이다 — 확장 인챈트까지 읽으려면 {@link WideEnchantments#level(int)} 을 쓴다.
     */
    public static int enchantLevel(long mask, int enchantId) {
        if (enchantId < 0 || enchantId >= LEGACY_ENCHANTMENT_COUNT) return 0;
        return (int) ((mask >>> (enchantId * ENCHANT_BITS)) & ENCHANT_LEVEL_MASK);
    }

    /**
     * 워드 0 마스크에 인챈트 레벨을 써 넣은 새 마스크. 레벨은 0~7로 잘린다.
     *
     * <p>ID 16 이상은 워드 0 에 칸이 없다. 예전처럼 조용히 버리면 확장 인챈트가 일반 아이템으로
     * 강등되는 결함이 되살아나므로 예외로 막는다({@link WideEnchantments#with} 를 쓴다).
     */
    public static long withEnchantLevel(long mask, int enchantId, int level) {
        if (enchantId >= LEGACY_ENCHANTMENT_COUNT && enchantId < ENCHANTMENT_COUNT) {
            throw new IllegalArgumentException(
                    "enchantment " + enchantId + " lives outside the 48-bit mask; use WideEnchantments");
        }
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return mask;
        int clamped = level < 0 ? 0 : Math.min(level, ENCHANT_LEVEL_MASK);
        int shift = enchantId * ENCHANT_BITS;
        return (mask & ~((long) ENCHANT_LEVEL_MASK << shift)) | ((long) clamped << shift);
    }

    /**
     * 48비트 레이아웃 안에 들어가는 워드 0 마스크인가. 정적판이 같은 검사를
     * 강제하므로, 여기가 비면 Java 권위만 범위 밖 마스크를 받아 저장하고 같은 세이브를
     * 정적판이 열 때 예외로 거부한다.
     */
    public static boolean isValidEnchantmentMask(long mask) {
        return mask >= 0L && (mask & ~ENCHANT_MASK_LIMIT) == 0L;
    }

    /** 인챈트가 하나라도 걸려 있는가(반짝임 표시 조건). */
    public static boolean hasEnchantments(long mask) {
        return mask != EMPTY_ENCHANTMENTS;
    }

    /** [CURSE] 저주 인챈트인가. 인챈트대 추첨에서 빠지고, 모루 책 부여와 숫돌 보존은 허용한다. */
    public static boolean isCurse(int enchantId) {
        return enchantId == BINDING_CURSE || enchantId == VANISHING_CURSE;
    }

    /** 바닐라 {@code #minecraft:treasure}(저주 포함)인가. */
    public static boolean isTreasure(int enchantId) {
        return enchantId >= 0 && enchantId < ENCHANTMENT_COUNT && TREASURE[enchantId];
    }

    /** 바닐라 {@code #minecraft:in_enchanting_table}(= non_treasure)인가. */
    public static boolean inEnchantingTable(int enchantId) {
        return enchantId >= 0 && enchantId < ENCHANTMENT_COUNT && !TREASURE[enchantId];
    }

    /** 바닐라 {@code #minecraft:on_random_loot}(non_treasure + 저주 둘 + 차가운 걸음 + 수선)인가. */
    public static boolean onRandomLoot(int enchantId) {
        return enchantId >= 0 && enchantId < ENCHANTMENT_COUNT && ON_RANDOM_LOOT[enchantId];
    }

    /** [CURSE] 마스크에서 저주 슬롯만 남긴다(숫돌이 보존할 부분). */
    public static long curseEnchantments(long mask) {
        return mask & CURSE_SLOT_MASK;
    }

    /** [CURSE] 마스크에서 저주가 아닌 슬롯만 남긴다(숫돌이 벗겨내고 XP 로 돌려줄 부분). */
    public static long nonCurseEnchantments(long mask) {
        return mask & ~CURSE_SLOT_MASK;
    }

    /** [CURSE] 넓은 집합에서 저주만 남긴다. */
    public static WideEnchantments curseEnchantments(WideEnchantments enchantments) {
        return enchantments.intersect(CURSE_SLOTS);
    }

    /** [CURSE] 넓은 집합에서 저주가 아닌 인챈트만 남긴다. */
    public static WideEnchantments nonCurseEnchantments(WideEnchantments enchantments) {
        return enchantments.without(CURSE_SLOTS);
    }

    /** 인챈트별 최대 레벨. */
    public static int maxLevel(int enchantId) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return 0;
        return MAX_LEVELS[enchantId];
    }

    /** 인챈트별 추첨 가중치. */
    public static int weight(int enchantId) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return 0;
        return WEIGHTS[enchantId];
    }

    /** 바닐라 {@code anvil_cost}. 범위 밖이면 0. */
    public static int anvilCost(int enchantId) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return 0;
        return ANVIL_COSTS[enchantId];
    }

    /** 표시용 인챈트 이름. */
    public static String enchantName(int enchantId) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return "";
        return NAMES[enchantId];
    }

    /** 바닐라 레지스트리 키(네임스페이스 없는 경로, 예: {@code "smite"}). 범위 밖이면 빈 문자열. */
    public static String enchantKey(int enchantId) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return "";
        return KEYS[enchantId];
    }

    /**
     * 바닐라 인챈트 키({@code "minecraft:smite"})의 게임플레이 ID. 모르는 키는 -1.
     * 전리품 투영(상자·시련 금고 등)이 이 한 함수로만 키를 ID 로 바꾼다.
     */
    public static int canonicalEnchantmentId(String namespacedKey) {
        if (namespacedKey == null || !namespacedKey.startsWith("minecraft:")) return -1;
        String path = namespacedKey.substring("minecraft:".length());
        for (int id = 0; id < ENCHANTMENT_COUNT; id++) {
            if (KEYS[id].equals(path)) return id;
        }
        return -1;
    }

    /**
     * 이 바닐라 인챈트 키를 게임플레이 스택이 담을 수 있는가. 26.3 의 43종 전부가 참이다 —
     * 예전에 "표현 불가" 로 강등되던 전리품은 이 판정으로 그대로 지급한다.
     */
    public static boolean isRepresentableEnchantmentKey(String namespacedKey) {
        return canonicalEnchantmentId(namespacedKey) >= 0;
    }

    /** 바닐라 {@code #minecraft:tooltip_order} 에서 이 인챈트의 자리(0..42). */
    public static int tooltipRank(int enchantId) {
        for (int rank = 0; rank < TOOLTIP_ORDER.length; rank++) {
            if (TOOLTIP_ORDER[rank] == enchantId) return rank;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 같은 아이템에 함께 붙을 수 있는 조합인가. 바닐라 {@code Enchantment.areCompatible}:
     * {@code a != b && !a.exclusiveSet.contains(b) && !b.exclusiveSet.contains(a)}.
     * 급류의 집합(riptide)은 충성·집전을, 무한의 집합(bow)은 수선을 담는다(수선 자신은 집합이 없다).
     */
    public static boolean compatible(int enchantIdA, int enchantIdB) {
        if (enchantIdA == enchantIdB) return false;
        if (enchantIdA < 0 || enchantIdA >= ENCHANTMENT_COUNT
                || enchantIdB < 0 || enchantIdB >= ENCHANTMENT_COUNT) {
            return true;
        }
        if (EXCLUSIVE_SET[enchantIdA] != SET_NONE
                && EXCLUSIVE_SET[enchantIdA] == EXCLUSIVE_SET[enchantIdB]) {
            return false;
        }
        boolean riptidePair = enchantIdA == RIPTIDE
                && (enchantIdB == LOYALTY || enchantIdB == CHANNELING)
                || enchantIdB == RIPTIDE
                && (enchantIdA == LOYALTY || enchantIdA == CHANNELING);
        boolean infinityMending = enchantIdA == INFINITY && enchantIdB == MENDING
                || enchantIdA == MENDING && enchantIdB == INFINITY;
        return !riptidePair && !infinityMending;
    }

    /**
     * 저장·전송 경계에서 한 아이템의 마스크가 의미적으로도 유효한지 검사한다.
     * 레이아웃 범위만 확인하면 대상이 아닌 인챈트, 최대 레벨 초과가 그대로 영속될 수 있으므로
     * 세 가지 계약(레이아웃·적용 대상·최대 레벨)을 한 곳에서 함께 적용한다. [MOB-EQUIP] 배타 조합은
     * 스택 유효성이 아니다 — 바닐라 {@code ItemEnchantments} 는 호환을 묻지 않고 전리품
     * ({@code set_enchantments}: 트라이얼 몹 갑옷의 화염·발사체·일반 보호 IV)이 배타 조합을 만든다. 호환은
     * 인챈트를 새로 붙이는 모루·마법부여대가 {@link #compatible} 로 묻는다.
     */
    public static boolean isValidEnchantmentMaskForItem(short itemType, long mask) {
        if (!isValidEnchantmentMask(mask)) return false;
        return isValidEnchantmentsForItem(itemType, WideEnchantments.legacy(mask));
    }

    /** {@link #isValidEnchantmentMaskForItem} 의 43종 판정. 워드 범위는 레코드가 이미 강제한다. */
    public static boolean isValidEnchantmentsForItem(short itemType, WideEnchantments enchantments) {
        if (enchantments == null) return false;
        if (!enchantments.isEmpty() && !canHoldEnchantments(itemType)) return false;
        for (int enchantId = 0; enchantId < ENCHANTMENT_COUNT; enchantId++) {
            int level = enchantments.level(enchantId);
            if (level == 0) continue;
            if (level > maxLevel(enchantId) || !appliesTo(enchantId, itemType)) return false;
        }
        return true;
    }

    // ── 아이템 분류 ──

    /** 곡괭이(전 티어)인가. */
    public static boolean isPickaxeItem(short itemType) {
        return itemType == PlayerInventory.PICKAXE || itemType == PlayerInventory.STONE_PICKAXE
                || itemType == PlayerInventory.IRON_PICKAXE
                || itemType == PlayerInventory.GOLD_PICKAXE
                || itemType == PlayerInventory.DIAMOND_PICKAXE
                || itemType == PlayerInventory.COPPER_PICKAXE
                || itemType == PlayerInventory.NETHERITE_PICKAXE;
    }

    /** 도끼(전 티어)인가. */
    public static boolean isAxeItem(short itemType) {
        return itemType == PlayerInventory.AXE || itemType == PlayerInventory.STONE_AXE
                || itemType == PlayerInventory.IRON_AXE || itemType == PlayerInventory.GOLD_AXE
                || itemType == PlayerInventory.DIAMOND_AXE
                || itemType == PlayerInventory.COPPER_AXE
                || itemType == PlayerInventory.NETHERITE_AXE;
    }

    /** 삽(전 티어)인가. */
    public static boolean isShovelItem(short itemType) {
        return itemType == PlayerInventory.SHOVEL || itemType == PlayerInventory.STONE_SHOVEL
                || itemType == PlayerInventory.IRON_SHOVEL
                || itemType == PlayerInventory.GOLD_SHOVEL
                || itemType == PlayerInventory.DIAMOND_SHOVEL
                || itemType == PlayerInventory.COPPER_SHOVEL
                || itemType == PlayerInventory.NETHERITE_SHOVEL;
    }

    /** 검(전 티어)인가. */
    public static boolean isSwordItem(short itemType) {
        return itemType == PlayerInventory.SWORD_ITEM || itemType == PlayerInventory.STONE_SWORD
                || itemType == PlayerInventory.IRON_SWORD || itemType == PlayerInventory.GOLD_SWORD
                || itemType == PlayerInventory.DIAMOND_SWORD
                || itemType == PlayerInventory.COPPER_SWORD
                || itemType == PlayerInventory.NETHERITE_SWORD;
    }

    /**
     * [SPEAR] 창(전 티어)인가. 핀 §7 이 창에 붙는다고 적은 인챈트 중 이 저장소에 실재하는
     * 것은 <b>날카로움 · 약탈 · 내구성</b> 셋이며 셋 다 이 술어를 지난다. 창 전용
     * <b>돌진(Lunge)</b> 인챈트는 이 저장소의 인챈트 표에 없는 새 항목이라 채택하지 않고
     * 백로그로 남긴다(핀 §7 등급 C 결정).
     */
    public static boolean isSpearItem(short itemType) {
        return PlayerInventory.isSpear(itemType) || itemType == PlayerInventory.NETHERITE_SPEAR;
    }

    /** [MACE] 철퇴인가(바닐라 {@code #enchantable/mace} 의 유일한 값). */
    public static boolean isMaceItem(short itemType) {
        return itemType == PlayerInventory.MACE;
    }

    /** 등록된 괭이인가. */
    public static boolean isHoeItem(short itemType) {
        return itemType == PlayerInventory.WOODEN_HOE || itemType == PlayerInventory.COPPER_HOE
                || itemType == PlayerInventory.DIAMOND_HOE
                || itemType == PlayerInventory.NETHERITE_HOE;
    }

    /** 활인가. */
    public static boolean isBowItem(short itemType) {
        return itemType == PlayerInventory.BOW;
    }

    /** 양털 가위인가. */
    public static boolean isShearsItem(short itemType) {
        return itemType == PlayerInventory.SHEARS;
    }

    /** 낚싯대(기본·다이아)인가. 인챈트대에 올릴 수 있고 미끼·바다의 행운이 붙는 유일한 대상이다. */
    public static boolean isFishingRodItem(short itemType) {
        return PlayerInventory.isFishingRod(itemType);
    }

    /**
     * 마법이 부여된 책인가. 바닐라 {@code enchant_with_levels} 는 책에 <b>아무 인챈트나</b>
     * 붙일 수 있게 하므로 {@link #appliesTo} 의 대상 제한을 받지 않는다.
     */
    public static boolean isEnchantedBookItem(short itemType) {
        return itemType == PlayerInventory.ENCHANTED_BOOK;
    }

    /** 방어구 4부위 중 하나인가. */
    public static boolean isArmorItem(short itemType) {
        return PlayerInventory.isArmor(itemType);
    }

    /** [ENCHANT-WIDE] 석궁인가(바닐라 {@code #enchantable/crossbow}). */
    public static boolean isCrossbowItem(short itemType) {
        return itemType == PlayerInventory.CROSSBOW;
    }

    /** [ENCHANT-WIDE] 투구 부위 방어구인가(바닐라 {@code #head_armor}; 조각한 호박은 아니다). */
    public static boolean isHeadArmorItem(short itemType) {
        return isArmorItem(itemType)
                && PlayerInventory.armorSlot(itemType) == ArmorSlot.HELMET;
    }

    /** [ENCHANT-WIDE] 흉갑 부위 방어구인가(바닐라 {@code #chest_armor}; 겉날개는 아니다). */
    public static boolean isChestArmorItem(short itemType) {
        return isArmorItem(itemType)
                && PlayerInventory.armorSlot(itemType) == ArmorSlot.CHESTPLATE;
    }

    /** [ENCHANT-WIDE] 각반 부위 방어구인가(바닐라 {@code #leg_armor}). */
    public static boolean isLegArmorItem(short itemType) {
        return isArmorItem(itemType)
                && PlayerInventory.armorSlot(itemType) == ArmorSlot.LEGGINGS;
    }

    /** [ENCHANT-WIDE] 장화 부위 방어구인가(바닐라 {@code #foot_armor}). */
    public static boolean isFootArmorItem(short itemType) {
        return isArmorItem(itemType)
                && PlayerInventory.armorSlot(itemType) == ArmorSlot.BOOTS;
    }

    /** 내구도를 가진 아이템인가(바닐라 {@code #enchantable/durability}: 내구성·수선의 대상). */
    public static boolean isDurableItem(short itemType) {
        return isPickaxeItem(itemType) || isAxeItem(itemType) || isShovelItem(itemType)
                || isSwordItem(itemType) || isHoeItem(itemType) || isBowItem(itemType)
                // [SPEAR] 창도 내구를 가진 무기라 내구성(Unbreaking) 대상이다(핀 §7).
                || isSpearItem(itemType)
                || isShearsItem(itemType) || isArmorItem(itemType)
                || isFishingRodItem(itemType)
                || PlayerInventory.isShield(itemType)
                || itemType == PlayerInventory.FLINT_AND_STEEL
                || itemType == PlayerInventory.TRIDENT
                // [MACE] 바닐라 #enchantable/durability 는 minecraft:mace 를 담는다.
                || isMaceItem(itemType)
                // [ENCHANT-WIDE] 바닐라 durability 태그의 석궁·겉날개.
                || isCrossbowItem(itemType) || itemType == PlayerInventory.ELYTRA;
    }

    /**
     * 아이템의 enchantability(바닐라 재질별 값). 인챈트대에서 인챈트할 수 없는 아이템은 0이다.
     * 방패·부싯돌과 부시는 바닐라에서도 인챈트대 대상이 아니라 0이다.
     */
    public static int enchantability(short rawItemType) {
        if (rawItemType == Blocks.FLESH_BONE_SPEAR || rawItemType == Blocks.FLESH_HOOKED_SPEAR)
            return enchantability(PlayerInventory.IRON_SPEAR);
        if (rawItemType == Blocks.FLESH_BONE_CHESTPLATE) return enchantability(PlayerInventory.IRON_CHESTPLATE);
        // [ROTTEN-LEATHER] 썩은 가죽 방어구는 가죽과 같은 재질 등급이다(방어도·내구와 같은 계약).
        short itemType = PlayerInventory.leatherEquivalentArmor(rawItemType);
        // [SPEAR] 창은 기존 티어 구간(STONE_TIER_MIN.. 등) **밖**의 독립 ID 구간이라 아래
        // 구간 검사에 하나도 걸리지 않는다 — 명시 분기가 없으면 조용히 0(인챈트 불가)이 된다.
        // 재질 등급은 같은 재료의 도구와 같은 값을 읽는다(리터럴을 새로 만들지 않는다).
        // 구리 창은 공식판에 없는 프로젝트 창 변형이지만, 재료 계약은 공식 구리 도구와 같은
        // enchantability 13을 사용한다.
        if (itemType == PlayerInventory.NETHERITE_SPEAR) return ENCHANTABILITY_NETHERITE;
        if (PlayerInventory.isSpear(itemType)) {
            if (itemType == PlayerInventory.WOODEN_SPEAR) return ENCHANTABILITY_WOOD;
            if (itemType == PlayerInventory.STONE_SPEAR) return ENCHANTABILITY_STONE;
            if (itemType == PlayerInventory.COPPER_SPEAR) return ENCHANTABILITY_COPPER;
            if (itemType == PlayerInventory.IRON_SPEAR) return ENCHANTABILITY_IRON;
            if (itemType == PlayerInventory.GOLD_SPEAR) return ENCHANTABILITY_GOLD;
            return ENCHANTABILITY_DIAMOND;
        }
        if (itemType >= PlayerInventory.COPPER_PICKAXE
                && itemType <= PlayerInventory.COPPER_HOE) {
            return ENCHANTABILITY_COPPER;
        }
        if (itemType >= PlayerInventory.COPPER_HELMET
                && itemType <= PlayerInventory.COPPER_BOOTS) {
            return ENCHANTABILITY_COPPER_ARMOR;
        }
        if (itemType >= PlayerInventory.NETHERITE_PICKAXE
                && itemType <= PlayerInventory.NETHERITE_BOOTS) {
            return ENCHANTABILITY_NETHERITE;
        }
        if (itemType == PlayerInventory.DIAMOND_HOE) return ENCHANTABILITY_DIAMOND;
        if (itemType == PlayerInventory.SWORD_ITEM || itemType == PlayerInventory.PICKAXE
                || itemType == PlayerInventory.AXE || itemType == PlayerInventory.SHOVEL
                || itemType == PlayerInventory.WOODEN_HOE) {
            return ENCHANTABILITY_WOOD;
        }
        if (itemType >= PlayerInventory.STONE_TIER_MIN
                && itemType <= PlayerInventory.STONE_TIER_MAX) {
            return ENCHANTABILITY_STONE;
        }
        if (itemType >= PlayerInventory.IRON_TIER_MIN
                && itemType <= PlayerInventory.IRON_TIER_MAX) {
            return ENCHANTABILITY_IRON;
        }
        if (itemType >= PlayerInventory.GOLD_TIER_MIN
                && itemType <= PlayerInventory.GOLD_TOOL_MAX) {
            return ENCHANTABILITY_GOLD;
        }
        if (itemType >= PlayerInventory.DIAMOND_TIER_MIN
                && itemType <= PlayerInventory.DIAMOND_TOOL_MAX) {
            return ENCHANTABILITY_DIAMOND;
        }
        if (itemType >= PlayerInventory.LEATHER_HELMET
                && itemType <= PlayerInventory.LEATHER_BOOTS) {
            return ENCHANTABILITY_LEATHER;
        }
        if (itemType >= PlayerInventory.IRON_HELMET && itemType <= PlayerInventory.IRON_BOOTS) {
            return ENCHANTABILITY_IRON_ARMOR;
        }
        if (itemType >= PlayerInventory.CHAINMAIL_HELMET
                && itemType <= PlayerInventory.CHAINMAIL_BOOTS) {
            return ENCHANTABILITY_CHAIN;
        }
        if (itemType >= PlayerInventory.GOLD_ARMOR_MIN
                && itemType <= PlayerInventory.GOLD_ARMOR_MAX) {
            return ENCHANTABILITY_GOLD_ARMOR;
        }
        if (itemType >= PlayerInventory.DIAMOND_ARMOR_MIN
                && itemType <= PlayerInventory.DIAMOND_ARMOR_MAX) {
            return ENCHANTABILITY_DIAMOND;
        }
        if (isBowItem(itemType)) return ENCHANTABILITY_BOW;
        if (isCrossbowItem(itemType)) return ENCHANTABILITY_CROSSBOW;
        if (isShearsItem(itemType)) return ENCHANTABILITY_SHEARS;
        if (isFishingRodItem(itemType)) return ENCHANTABILITY_FISHING_ROD;
        if (itemType == PlayerInventory.TRIDENT) return ENCHANTABILITY_TRIDENT;
        if (isMaceItem(itemType)) return ENCHANTABILITY_MACE;
        // 마법이 부여된 책은 enchantability 0 이다. 바닐라에서도 인챈트대에 올릴 수 있는 것은
        // 평범한 책이지 마법책이 아니며, 낚시 보물의 30레벨 인챈트는 enchantability 보정을
        // 거치지 않는 rollEnchantmentsAt 로 직접 뽑는다(FishingRules.rollLootEnchantments).
        return 0;
    }

    /** 인챈트대에 올릴 수 있는 아이템인가. */
    public static boolean isEnchantable(short itemType) {
        return enchantability(itemType) > 0;
    }

    /**
     * [ENCHANT-WIDE] 인챈트대 입력 칸이 제안을 만드는 아이템인가: {@link #isEnchantable} 에 평범한 책을 더한다.
     * 바닐라 {@code Items.BOOK} 은 {@code enchantable(1)} 이라 인챈트대에 올라가고, 확정하면
     * {@code EnchantmentMenu} 가 {@code transmuteCopy(Items.ENCHANTED_BOOK)} 로 마법이 부여된 책을 만든다.
     * 책은 인챈트를 <b>지니는</b> 아이템이 아니라서 {@link #isEnchantable}·{@link #canHoldEnchantments}
     * 에는 넣지 않는다. 책의 enchantability 1 은 {@code enchantability/4+1 = 1} 로 0 과 같은 굴림이다.
     */
    public static boolean isTableEnchantable(short itemType) {
        return isEnchantable(itemType) || itemType == PlayerInventory.BOOK;
    }

    /** [ENCHANT-WIDE] 인챈트대 확정 결과의 아이템 종류. 책 → 마법이 부여된 책, 그 밖은 그대로. */
    public static short enchantingTableResult(short itemType) {
        return itemType == PlayerInventory.BOOK ? PlayerInventory.ENCHANTED_BOOK : itemType;
    }

    /**
     * 인챈트 마스크를 지닌 채로 인벤토리·컨테이너·드랍을 오갈 수 있는 아이템인가.
     *
     * <p>인챈트대 대상({@link #isEnchantable})보다 넓다. 인챈트된 책은 바닐라에서도
     * 인챈트대에 올릴 수 없지만(책을 올리면 인챈트된 책이 나온다) 마스크를 지닌 채
     * 낚시 보물·상자에서 나오므로 운반 자격만 따로 허용한다.
     */
    public static boolean canHoldEnchantments(short itemType) {
        return isEnchantable(itemType) || isEnchantedBookItem(itemType);
    }

    /**
     * [ENCHANT-WIDE] 손에 든 아이템에서 인챈트 효과(피해·넉백·발화·약탈·행운·섬세한 손길 등)가 도는가.
     * 바닐라 효과는 아이템의 {@code minecraft:enchantments} 성분만 순회하고, 마법이 부여된 책은
     * {@code stored_enchantments} 에 담으므로 들고 휘둘러도 아무 효과가 없다. 그 밖에는 기존 규약대로
     * 인챈트 대상 아이템일 때만 효과를 낸다.
     */
    public static boolean heldEffectApplies(int enchantId, short itemType) {
        return !isEnchantedBookItem(itemType) && appliesTo(enchantId, itemType);
    }

    /** [ENCHANT-WIDE] 손에 든 아이템의 효과용 워드 0 마스크. 마법이 부여된 책이면 비어 있다. */
    public static long heldEffectEnchantments(short itemType, long enchantments) {
        return isEnchantedBookItem(itemType) ? EMPTY_ENCHANTMENTS : enchantments;
    }

    /**
     * 이 인챈트를 이 아이템에 붙일 수 있는가. 바닐라 {@code supported_items} 태그를 이 저장소 아이템
     * 분류로 옮긴 표다(모루·전리품·저장 검증이 쓴다). [MACE] 철퇴는 {@code #enchantable/weapon}(강타·
     * 살충) · {@code #enchantable/fire_aspect}(발화) · {@code #enchantable/mace}(파괴·밀도·돌풍) ·
     * {@code #enchantable/durability}(내구성·수선) 에 든다.
     */
    public static boolean appliesTo(int enchantId, short itemType) {
        // 마법이 부여된 책은 바닐라 stored_enchantments 처럼 종류를 가리지 않고 담는다.
        if (isEnchantedBookItem(itemType)) {
            return enchantId >= 0 && enchantId < ENCHANTMENT_COUNT;
        }
        return switch (enchantId) {
            // #enchantable/mining: 곡괭이·도끼·삽·괭이·가위.
            case EFFICIENCY -> isPickaxeItem(itemType) || isAxeItem(itemType)
                    || isShovelItem(itemType) || isHoeItem(itemType)
                    || isShearsItem(itemType);
            // #enchantable/mining_loot: 곡괭이·도끼·삽·괭이.
            case FORTUNE, SILK_TOUCH -> isPickaxeItem(itemType) || isAxeItem(itemType)
                    || isShovelItem(itemType) || isHoeItem(itemType);
            // #enchantable/sharp_weapon(melee_weapon + axes), #enchantable/weapon(+ mace).
            // [SPEAR] 창도 날카로움 대상이다(핀 §7). 창의 크리티컬 억제는 인챈트와 무관한
            // 별개 축이라(SpearRules.suppressesCritical) 여기서 갈라지지 않는다.
            case SHARPNESS -> isSwordItem(itemType)
                    || isAxeItem(itemType) || isSpearItem(itemType);
            // [MACE] 강타·살충의 supported_items 는 #enchantable/weapon(= sharp_weapon + mace) 다.
            case SMITE, BANE_OF_ARTHROPODS -> isSwordItem(itemType)
                    || isAxeItem(itemType) || isSpearItem(itemType) || isMaceItem(itemType);
            case UNBREAKING, MENDING -> isDurableItem(itemType);
            // #enchantable/armor.
            case PROTECTION, BLAST_PROTECTION, FIRE_PROTECTION, PROJECTILE_PROTECTION, THORNS ->
                    isArmorItem(itemType);
            case AQUA_AFFINITY, RESPIRATION -> isHeadArmorItem(itemType);
            case SWIFT_SNEAK -> isLegArmorItem(itemType);
            case FEATHER_FALLING, DEPTH_STRIDER, FROST_WALKER, SOUL_SPEED ->
                    isFootArmorItem(itemType);
            case POWER, PUNCH, FLAME, INFINITY -> isBowItem(itemType);
            case MULTISHOT, PIERCING, QUICK_CHARGE -> isCrossbowItem(itemType);
            case LURE, LUCK_OF_THE_SEA -> isFishingRodItem(itemType);
            // #enchantable/melee_weapon(검·창). 바닐라 looting 도 이 태그라 도끼는 대상이 아니다.
            // #enchantable/fire_aspect 는 melee_weapon + mace 다.
            case LOOTING, KNOCKBACK -> isSwordItem(itemType) || isSpearItem(itemType);
            case FIRE_ASPECT -> isSwordItem(itemType) || isSpearItem(itemType)
                    || isMaceItem(itemType);
            case SWEEPING_EDGE -> isSwordItem(itemType);
            case LUNGE -> isSpearItem(itemType);
            case LOYALTY, RIPTIDE, CHANNELING, IMPALING -> itemType == PlayerInventory.TRIDENT;
            // #enchantable/mace: 철퇴 하나다.
            case BREACH, DENSITY, WIND_BURST -> isMaceItem(itemType);
            // [CURSE] 결속은 방어구 전용, 소실은 인챈트를 지닐 수 있는 아이템 전부에 붙는다.
            // 이 대상 제한은 모루의 저주 책 부여에도 적용한다. 인챈트대 추첨은 candidateLevel,
            // 같은 아이템 기증자의 저주 전이는 combineEnchantments 가 따로 막는다.
            case BINDING_CURSE -> isArmorItem(itemType);
            case VANISHING_CURSE -> isEnchantable(itemType);
            default -> false;
        };
    }

    /**
     * 인챈트대·{@code enchant_with_levels} 가 이 아이템에서 이 인챈트를 고를 수 있는가. 바닐라
     * {@code Enchantment.isPrimaryItem}: {@code primary_items} 가 있으면 그 태그, 없으면
     * {@code supported_items}. 날카로움·강타·살충·발화의 primary 는 {@code #melee_weapon}(도끼 제외),
     * 가시의 primary 는 {@code #chest_armor} 다. 책은 {@code getAvailableEnchantmentResults} 의
     * {@code isBook} 분기로 모든 후보를 받는다.
     */
    public static boolean isPrimaryItem(int enchantId, short itemType) {
        if (isEnchantedBookItem(itemType) || itemType == PlayerInventory.BOOK) {
            return enchantId >= 0 && enchantId < ENCHANTMENT_COUNT;
        }
        return switch (enchantId) {
            case SHARPNESS, SMITE, BANE_OF_ARTHROPODS, FIRE_ASPECT ->
                    isSwordItem(itemType) || isSpearItem(itemType);
            case THORNS -> isChestArmorItem(itemType);
            default -> appliesTo(enchantId, itemType);
        };
    }

    // ── 비용 구간표(바닐라 Enchantment.getMinCost / getMaxCost) ──

    /**
     * 이 인챈트 레벨이 등장하기 시작하는 보정 레벨. 바닐라
     * {@code min_cost.base + min_cost.per_level_above_first * (level - 1)}.
     */
    public static int minCost(int enchantId, int level) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return Integer.MAX_VALUE;
        return MIN_COST_BASE[enchantId] + MIN_COST_PER_LEVEL[enchantId] * (level - 1);
    }

    /**
     * 이 인챈트 레벨이 등장할 수 있는 보정 레벨 상한. 바닐라
     * {@code max_cost.base + max_cost.per_level_above_first * (level - 1)}. 26.3 의 충성·급류는
     * per_level 0 이라 상한이 50 으로 고정이다.
     */
    public static int maxCost(int enchantId, int level) {
        if (enchantId < 0 || enchantId >= ENCHANTMENT_COUNT) return Integer.MIN_VALUE;
        return MAX_COST_BASE[enchantId] + MAX_COST_PER_LEVEL[enchantId] * (level - 1);
    }

    // ── 효과 크기 ──

    /** 효율 n의 채굴 속도 가산치({@code n*n + 1}). 0레벨이면 0. */
    public static int efficiencySpeedBonus(int level) {
        if (level <= 0) return 0;
        return level * level + 1;
    }

    /** 날카로움 n의 추가 근접 피해(milli). 바닐라 {@code 0.5*n + 0.5}. */
    public static int sharpnessBonusDamageMilli(int level) {
        if (level <= 0) return 0;
        return level * DAMAGE_PER_LEVEL_MILLI + DAMAGE_FLAT_MILLI;
    }

    /** 힘 n의 추가 화살 피해(milli). 바닐라 {@code 0.5*n + 0.5}. */
    public static int powerBonusDamageMilli(int level) {
        if (level <= 0) return 0;
        return level * DAMAGE_PER_LEVEL_MILLI + DAMAGE_FLAT_MILLI;
    }

    /** 방어구 4부위 마스크에서 보호 EPF 합을 구한다(상한 20). */
    public static int protectionEpf(long helmetMask, long chestMask, long leggingsMask, long bootsMask) {
        return protectionEpf(helmetMask, chestMask, leggingsMask, bootsMask, false);
    }

    /** 폭발 피해에만 부위별 Blast Protection 레벨의 두 배를 더하고 합산 상한을 한 번 적용한다. */
    public static int protectionEpf(long helmetMask, long chestMask, long leggingsMask,
            long bootsMask, boolean explosion) {
        int sum = enchantLevel(helmetMask, PROTECTION) + enchantLevel(chestMask, PROTECTION)
                + enchantLevel(leggingsMask, PROTECTION) + enchantLevel(bootsMask, PROTECTION);
        if (explosion) {
            sum += 2 * (enchantLevel(helmetMask, BLAST_PROTECTION)
                    + enchantLevel(chestMask, BLAST_PROTECTION)
                    + enchantLevel(leggingsMask, BLAST_PROTECTION)
                    + enchantLevel(bootsMask, BLAST_PROTECTION));
        }
        return Math.min(sum, PROTECTION_EPF_CAP);
    }

    /**
     * 26.3 explosion_knockback_resistance: 부위별 ADD_VALUE를 합산하고 [0,1]로 제한한 뒤
     * 폭발 충격에 1-resistance를 곱한다. Linear의 float 계산 후 double 승격도 보존한다.
     */
    public static double explosionKnockbackMultiplier(long helmetMask, long chestMask,
            long leggingsMask, long bootsMask) {
        double resistance = blastKnockbackResistance(helmetMask)
                + blastKnockbackResistance(chestMask)
                + blastKnockbackResistance(leggingsMask)
                + blastKnockbackResistance(bootsMask);
        return 1.0 - Math.max(0.0, Math.min(1.0, resistance));
    }

    private static double blastKnockbackResistance(long mask) {
        int level = enchantLevel(mask, BLAST_PROTECTION);
        return level == 0 ? 0.0 : (double) (0.15F + 0.15F * (level - 1));
    }

    // ── [ENCHANT-WIDE] 새로 표현 가능해진 27종의 효과 크기(핀 jar data/minecraft/enchantment/*.json) ──

    /** 강타·살충·찌르기의 레벨당 추가 피해 2.5(milli). {@code linear(base 2.5, per_level 2.5)}. */
    public static final int TYPED_DAMAGE_PER_LEVEL_MILLI = 2500;
    /** 살충이 거는 구속의 증폭(IV = 3). {@code apply_mob_effect min/max_amplifier 3}. */
    public static final int BANE_SLOWNESS_AMPLIFIER = 3;
    /** 살충 구속 지속 하한 1.5초(MC 틱 30). */
    public static final float BANE_SLOWNESS_MIN_SECONDS = 1.5F;
    /** 살충 구속 지속 상한의 레벨당 증가 0.5초. {@code max_duration linear(1.5, 0.5)}. */
    public static final float BANE_SLOWNESS_SECONDS_PER_LEVEL = 0.5F;
    /** 발화의 레벨당 점화 4초. {@code ignite duration linear(4, 4)}. */
    public static final int FIRE_ASPECT_SECONDS_PER_LEVEL = 4;
    /** 화염 화살이 맞힌 대상을 태우는 5초({@code AbstractArrow.onHitEntity igniteForSeconds(5)}). */
    public static final int FLAME_TARGET_IGNITE_SECONDS = 5;
    /** 바닐라 MC 틱/초. */
    public static final int MC_TICKS_PER_SECOND = 20;
    /** 밀어내기의 화살 속도 방향 수평 밀기 계수 0.6({@code AbstractArrow.doKnockback}). */
    public static final double PUNCH_HORIZONTAL_SCALE = 0.6;
    /** 밀어내기의 수직 밀기 0.1 블록/MC틱. */
    public static final double PUNCH_VERTICAL_PUSH = 0.1;
    /** 빠른 장전 없는 석궁 장전 1.25초({@code CrossbowItem.getChargeDuration}). */
    public static final float CROSSBOW_BASE_CHARGE_SECONDS = 1.25F;
    /** 빠른 장전의 레벨당 -0.25초. {@code crossbow_charge_time add linear(-0.25, -0.25)}. */
    public static final float QUICK_CHARGE_SECONDS_PER_LEVEL = 0.25F;
    /** 다중 발사의 추가 발사체 수(레벨당 2). {@code projectile_count add linear(2, 2)}. */
    public static final int MULTISHOT_EXTRA_PROJECTILES_PER_LEVEL = 2;
    /** 다중 발사의 퍼짐 각(도, 레벨당 10). {@code projectile_spread add linear(10, 10)}. */
    public static final float MULTISHOT_SPREAD_DEGREES_PER_LEVEL = 10.0F;
    /** 가시 발동 확률의 레벨당 0.15. {@code random_chance enchantment_level linear(0.15, 0.15)}. */
    public static final float THORNS_CHANCE_PER_LEVEL = 0.15F;
    /** 가시 반사 피해 하한 1·상한 5({@code damage_entity min 1 max 5}). */
    public static final float THORNS_MIN_DAMAGE = 1.0F;
    public static final float THORNS_MAX_DAMAGE = 5.0F;
    /** 가시가 발동할 때 그 방어구가 잃는 내구도 2({@code change_item_damage amount 2}). */
    public static final int THORNS_SELF_DAMAGE = 2;
    /** 화염으로부터 보호의 레벨당 연소 시간 -15%({@code burning_time add_multiplied_base -0.15}). */
    public static final float FIRE_PROTECTION_BURN_REDUCTION_PER_LEVEL = 0.15F;
    /** 친수성: 물속 채굴 속도 기본 0.2 에 {@code add_multiplied_total 4·level}. */
    public static final float SUBMERGED_MINING_BASE = 0.2F;
    public static final float AQUA_AFFINITY_MULTIPLIER_PER_LEVEL = 4.0F;
    /** 물갈퀴: water_movement_efficiency {@code add_value 0.33333334·level}(속성 상한 1). */
    public static final float DEPTH_STRIDER_EFFICIENCY_PER_LEVEL = 0.33333334F;
    /** 신속한 잠행: sneaking_speed 기본 0.3 + 0.15·level(속성 상한 1). */
    public static final float SNEAKING_SPEED_BASE = 0.3F;
    public static final float SWIFT_SNEAK_SPEED_PER_LEVEL = 0.15F;
    /** 수선: XP 1점당 내구 2({@code repair_with_xp multiply 2}). */
    public static final int MENDING_DURABILITY_PER_XP = 2;

    /** 강타·살충·찌르기의 대상별 추가 피해(milli). 레벨 0 이면 0. */
    public static int typedDamageBonusMilli(int level) {
        if (level <= 0) return 0;
        return level * TYPED_DAMAGE_PER_LEVEL_MILLI;
    }

    /**
     * 근접 무기 한 번의 인챈트 추가 피해 합(milli). 바닐라 {@code EnchantmentHelper.modifyDamage} 가
     * 무기의 모든 {@code minecraft:damage} 효과를 더하는 자리다: 날카로움(대상 무관), 강타(#undead),
     * 살충(#arthropod), 찌르기(#aquatic). 무기 대상이 아닌 인챈트는 더하지 않는다(기존 날카로움 규약).
     */
    public static int meleeEnchantmentBonusMilli(short weaponType, WideEnchantments weapon,
            boolean undeadTarget, boolean arthropodTarget, boolean aquaticTarget) {
        int bonus = 0;
        int sharpness = weapon.level(SHARPNESS);
        if (sharpness > 0 && heldEffectApplies(SHARPNESS, weaponType)) {
            bonus += sharpnessBonusDamageMilli(sharpness);
        }
        int smite = weapon.level(SMITE);
        if (undeadTarget && smite > 0 && heldEffectApplies(SMITE, weaponType)) {
            bonus += typedDamageBonusMilli(smite);
        }
        int bane = weapon.level(BANE_OF_ARTHROPODS);
        if (arthropodTarget && bane > 0 && heldEffectApplies(BANE_OF_ARTHROPODS, weaponType)) {
            bonus += typedDamageBonusMilli(bane);
        }
        int impaling = weapon.level(IMPALING);
        if (aquaticTarget && impaling > 0 && heldEffectApplies(IMPALING, weaponType)) {
            bonus += typedDamageBonusMilli(impaling);
        }
        return bonus;
    }

    /**
     * [ENCHANT-WIDE] 휘몰아치는 칼날의 {@code minecraft:sweeping_damage_ratio} 속성 수정치. 26.3
     * {@code sweeping_edge.json}: {@code fraction(linear(1, 1), linear(2, 1))} = {@code L / (L + 1)} 을
     * float 로 나눈다(I 0.5, II 0.6666667, III 0.75). 인챈트가 없으면 속성 기본값 0 이다.
     */
    public static float sweepingDamageRatio(int level) {
        if (level <= 0) return 0.0F;
        return (float) level / (float) (level + 1);
    }

    /**
     * [ENCHANT-WIDE] 휩쓸기 피해의 기본값. 바닐라 {@code Player.doSweepAttack}:
     * {@code 1.0F + (float) getAttributeValue(SWEEPING_DAMAGE_RATIO) * damage} (float 연산). 대상마다
     * {@code getEnchantedDamage} 로 날카로움·강타 등을 더한 뒤 공격 세기를 곱한다.
     */
    public static double sweepBaseDamage(int sweepingLevel, double attackDamage) {
        return 1.0F + sweepingDamageRatio(sweepingLevel) * (float) attackDamage;
    }

    /**
     * [ENCHANT-WIDE] 창 찌르기 충전 판정. 바닐라 창은 {@code MINIMUM_ATTACK_CHARGE 1.0} 이고 서버
     * {@code handlePlayerAction(STAB)} 은 {@code cannotAttackWithItem(stack, 5)} 로 거절한다:
     * {@code (attackStrengthTicker + 5) / getCurrentItemAttackStrengthDelay() < 1.0F} 이면 거절. 지연은
     * {@code (float) (1.0 / ATTACK_SPEED × 20.0)} MC 틱이다. 권위 한 틱은 MC 2틱이다.
     */
    public static boolean spearStabCharged(double attackSpeed, long elapsedServerTicks) {
        if (!(attackSpeed > 0.0)) return false;
        float delay = (float) (1.0 / attackSpeed * 20.0);
        float charge = (float) (elapsedServerTicks * 2L + SPEAR_STAB_GRACE_MC_TICKS) / delay;
        return !(charge < 1.0F);
    }

    /** [ENCHANT-WIDE] {@code handlePlayerAction(STAB)} 의 {@code cannotAttackWithItem(stack, 5)} 여유. */
    public static final int SPEAR_STAB_GRACE_MC_TICKS = 5;
    /** [ENCHANT-WIDE] {@code lunge.json} 요건: 생존 모드 플레이어의 허기 7 이상. */
    public static final int LUNGE_MIN_FOOD = 7;

    /**
     * [ENCHANT-WIDE] {@code lunge.json} 의 {@code post_piercing_attack} 요건: 탈것에 타지 않았고,
     * 활공 중이 아니며, 물에 닿지 않았고, 허기 7 이상(창작 모드면 면제 — 이 저장소엔 창작 모드가 없다).
     */
    public static boolean lungeApplies(int level, boolean riding, boolean gliding, boolean inWater,
            int food) {
        return level > 0 && !riding && !gliding && !inWater && food >= LUNGE_MIN_FOOD;
    }

    /** [ENCHANT-WIDE] 돌진 허기: {@code apply_exhaustion linear(4, 4)} = 4·L (milli 단위 ×1000). */
    public static int lungeExhaustionMilli(int level) {
        return level <= 0 ? 0 : 4000 * level;
    }

    /**
     * [ENCHANT-WIDE] 돌진 충격 크기(블록/MC틱): {@code apply_impulse linear(0.458, 0.458)} float.
     * 방향은 시선 쿼터니언으로 돌린 (0, 0, 1) 에 좌표 배율 (1, 0, 1) — 시선의 수평 성분(피치 cos 배)이다.
     * 이동은 클라 권위라 충격은 클라가 더한다.
     */
    public static float lungeImpulse(int level) {
        if (level <= 0) return 0.0F;
        return 0.458F + 0.458F * (level - 1);
    }

    /**
     * [ENCHANT-WIDE] 차가운 걸음 원판 반지름. {@code frost_walker.json} 의 {@code replace_disk}:
     * {@code clamped(linear(3, 1), 0, 16)} 을 {@code f2i} — I 3, II 4.
     */
    public static int frostWalkerRadius(int level) {
        if (level <= 0) return 0;
        float value = 3.0F + 1.0F * (level - 1);
        return (int) Math.max(0.0F, Math.min(16.0F, value));
    }

    /**
     * [ENCHANT-WIDE] {@code ReplaceDisk.apply} 의 원판 판정: 발밑 한 칸 아래 층에서
     * {@code (bx + 0.5 - x)² + (bz + 0.5 - z)² < r²} 인 칸(높이 1 이라 y 항은 0).
     */
    public static boolean frostWalkerDiskContains(int radius, double x, double z, int bx, int bz) {
        double dx = bx + 0.5 - x;
        double dz = bz + 0.5 - z;
        return dx * dx + dz * dz < (double) radius * radius;
    }

    /** [ENCHANT-WIDE] 살얼음 나이 상한({@code FrostedIceBlock.MAX_AGE}). */
    public static final int FROSTED_ICE_MAX_AGE = 3;
    /** [ENCHANT-WIDE] 살얼음의 {@code getLightDampening()}(얼음과 같은 반투명 블록 1). */
    public static final int FROSTED_ICE_LIGHT_DAMPENING = 1;
    /** [ENCHANT-WIDE] 살얼음 {@code onPlace} 예약 {@code Mth.nextInt(60, 120)} MC 틱. */
    public static final int FROSTED_ICE_PLACE_DELAY_MIN = 60;
    public static final int FROSTED_ICE_PLACE_DELAY_MAX = 120;
    /** [ENCHANT-WIDE] 살얼음 재예약 {@code Mth.nextInt(20, 40)} MC 틱. */
    public static final int FROSTED_ICE_RETRY_DELAY_MIN = 20;
    public static final int FROSTED_ICE_RETRY_DELAY_MAX = 40;
    /** [ENCHANT-WIDE] {@code neighborChanged}: 이웃 살얼음이 이 수 미만이면 곧바로 녹는다. */
    public static final int FROSTED_ICE_NEIGHBORS_TO_MELT = 2;

    /**
     * [ENCHANT-WIDE] {@code FrostedIceBlock.tick} 의 녹기 조건: ({@code nextInt(3) == 0} 이거나 이웃 살얼음
     * 4개 미만) 이고 밝기가 {@code 11 - age - lightDampening} 보다 클 때 한 단계 녹는다.
     */
    public static boolean frostedIceShouldMelt(int roll3, int frostedNeighbours, int brightness,
            int age, int lightDampening) {
        return (roll3 == 0 || frostedNeighbours < 4) && brightness > 11 - age - lightDampening;
    }

    /**
     * [ENCHANT-WIDE] 영혼 가속 이동속도 가산치 {@code linear(0.0405, 0.0105)}(float, 블록/MC틱, 기본 0.1 에
     * {@code add_value}). 영혼 모래·흙({@code #soul_speed_blocks}) 위나 효과가 켜진 채 공중일 때만이며,
     * {@code movement_efficiency} +1 이 영혼 모래의 감속(0.4)을 지운다. 이동은 클라 권위라 클라가 쓴다.
     */
    public static float soulSpeedMovementBonus(int level) {
        if (level <= 0) return 0.0F;
        return 0.0405F + 0.0105F * (level - 1);
    }

    /**
     * [ENCHANT-WIDE] 영혼 가속 장화 마모: 블록 위치가 바뀔 때 땅 위·영혼 블록이면
     * {@code random_chance(enchantment_level × 0.04)} 로 내구 1. {@code roll} 은 {@code nextFloat()}.
     */
    public static boolean soulSpeedWears(int level, float roll) {
        return level > 0 && roll < 0.04F * level;
    }

    /** [ENCHANT-WIDE] {@code Player.isSweepAttack}: 공격 세기가 이 값보다 커야 한다(0.9F). */
    public static final float SWEEP_MIN_ATTACK_STRENGTH = 0.9F;
    /** [ENCHANT-WIDE] {@code isSweepAttack}: 수평 이동²이 {@code (speed × 2.5)²} 보다 작아야 한다. */
    public static final double SWEEP_MAX_SPEED_FACTOR = 2.5;
    /** [ENCHANT-WIDE] 휩쓸기 대상 탐색: 주 대상 AABB 를 수평 1, 수직 0.25 로 부풀린다. */
    public static final double SWEEP_INFLATE_HORIZONTAL = 1.0;
    public static final double SWEEP_INFLATE_VERTICAL = 0.25;
    /** [ENCHANT-WIDE] 휩쓸기 대상은 공격자와의 거리² 가 9 미만이어야 한다. */
    public static final double SWEEP_MAX_DISTANCE_SQ = 9.0;
    /** [ENCHANT-WIDE] 플레이어 {@code MOVEMENT_SPEED} 기본값(블록/MC틱). */
    public static final double PLAYER_BASE_MOVEMENT_SPEED = 0.1;

    /**
     * [ENCHANT-WIDE] 바닐라 {@code Player.isSweepAttack(fullStrength, critical, sprintKnockback)}:
     * 세기 > 0.9 이고 치명타·질주 넉백이 아니며 땅에 서 있고, 수평 이동²(블록/MC틱) 이
     * {@code (getSpeed() × 2.5)²} 미만이고, 주손이 {@code #swords} 일 때.
     */
    public static boolean isSweepAttack(short weapon, float attackStrength, boolean critical,
            boolean sprinting, boolean onGround, double horizontalSpeedPerMcTick,
            double movementSpeed) {
        boolean fullStrength = attackStrength > SWEEP_MIN_ATTACK_STRENGTH;
        boolean sprintKnockback = sprinting && fullStrength;
        if (!fullStrength || critical || sprintKnockback || !onGround) return false;
        double limit = movementSpeed * SWEEP_MAX_SPEED_FACTOR;
        if (!(horizontalSpeedPerMcTick * horizontalSpeedPerMcTick < limit * limit)) return false;
        return isSwordItem(weapon);
    }

    /**
     * 살충이 거는 구속 IV 의 지속(MC 틱). 바닐라 {@code ApplyMobEffect}:
     * {@code round(randomBetween(min, max) * 20)}, {@code randomBetween = min + nextFloat*(max-min)}.
     *
     * @param roll {@code nextFloat()} 한 번 [0,1)
     */
    public static int baneSlownessMcTicks(int level, float roll) {
        if (level <= 0) return 0;
        float max = BANE_SLOWNESS_MIN_SECONDS + BANE_SLOWNESS_SECONDS_PER_LEVEL * (level - 1);
        float seconds = roll * (max - BANE_SLOWNESS_MIN_SECONDS) + BANE_SLOWNESS_MIN_SECONDS;
        return Math.round(seconds * MC_TICKS_PER_SECOND);
    }

    /** 발화의 점화 MC 틱({@code igniteForSeconds(4·level)} → {@code floor(seconds·20)}). */
    public static int fireAspectIgniteMcTicks(int level) {
        if (level <= 0) return 0;
        return level * FIRE_ASPECT_SECONDS_PER_LEVEL * MC_TICKS_PER_SECOND;
    }

    /**
     * 근접 추가 넉백의 세기를 0.5 단위로 센 값. 26.3 {@code Player.attack}: {@code getKnockback()}
     * = {@code (attack_knockback + 밀치기 레벨) / 2}, 질주 공격이면 {@code +0.5}.
     * 호출자는 이 값에 0.5 블록/틱(= {@code CombatRules.KNOCKBACK_BONUS_BPS}) 을 곱한다.
     */
    public static int meleeKnockbackHalfSteps(int knockbackLevel, boolean sprintBonus) {
        return Math.max(0, knockbackLevel) + (sprintBonus ? 1 : 0);
    }

    /** 활 화살의 밀어내기 수평 밀기 크기(블록/MC틱). {@code level × 0.6 × (1 - 넉백 저항)}. */
    public static double punchHorizontalPush(int level, double knockbackResistance) {
        if (level <= 0) return 0.0;
        return level * PUNCH_HORIZONTAL_SCALE * Math.max(0.0, 1.0 - knockbackResistance);
    }

    /** 무한: 활이 일반 화살을 소모하지 않는가({@code ammo_use set 0}, match_tool minecraft:arrow). */
    public static boolean infinitySavesArrow(WideEnchantments bow) {
        return bow.level(INFINITY) > 0;
    }

    /** 석궁 장전 MC 틱. {@code floor((1.25 - 0.25·빠른장전) × 20)}: 0→25, 1→20, 2→15, 3→10. */
    public static int crossbowChargeMcTicks(int quickChargeLevel) {
        float seconds = CROSSBOW_BASE_CHARGE_SECONDS
                - QUICK_CHARGE_SECONDS_PER_LEVEL * Math.max(0, quickChargeLevel);
        return Math.max(0, (int) Math.floor(seconds * MC_TICKS_PER_SECOND));
    }

    /** 다중 발사로 한 번에 나가는 발사체 수(1 + 2·level). 추가분은 화살을 더 쓰지 않는다. */
    public static int multishotProjectileCount(int level) {
        return 1 + MULTISHOT_EXTRA_PROJECTILES_PER_LEVEL * Math.max(0, level);
    }

    /**
     * 다중 발사 {@code index}번째 발사체의 요(도). 바닐라 {@code ProjectileWeaponItem.shoot}:
     * {@code step = n==1 ? 0 : 2·spread/(n-1)}, {@code offset = ((n-1) % 2)·step/2},
     * 부호가 번갈아 {@code offset + sign·((i+1)/2)·step}. 1레벨이면 0, -10, +10.
     */
    public static float multishotYawDegrees(int level, int index) {
        int count = multishotProjectileCount(level);
        float spread = MULTISHOT_SPREAD_DEGREES_PER_LEVEL * Math.max(0, level);
        float step = count == 1 ? 0.0F : 2.0F * spread / (count - 1);
        float offset = (float) ((count - 1) % 2) * step / 2.0F;
        float sign = index % 2 == 0 ? 1.0F : -1.0F;
        return offset + sign * (float) ((index + 1) / 2) * step;
    }

    /** 관통 화살이 뚫고 지나갈 수 있는 대상 수 한도(level + 1 번째 대상에서 사라진다). */
    public static int piercingMaxTargets(int level) {
        return Math.max(0, level) + 1;
    }

    /**
     * [ENCHANT-WIDE] 방어구 4부위의 인챈트 보호 EPF 합(상한 전). 바닐라
     * {@code EnchantmentHelper.getDamageProtection} 의 {@code damage_protection} 효과:
     * 보호 {@code 1·level}(모든 피해), 화염으로부터 보호 {@code 2·level}(#is_fire), 폭발로부터 보호
     * {@code 2·level}(#is_explosion), 발사체로부터 보호 {@code 2·level}(#is_projectile),
     * 가벼운 착지 {@code 3·level}(#is_fall). 기아(#bypasses_effects)는 이 단계 전체를 건너뛴다.
     *
     * @param cause 이 저장소의 피해 원인 문자열(PlayerTickState.damage / StandaloneDamageCause)
     */
    public static int damageProtectionEpf(String cause, WideEnchantments... armor) {
        if (!enchantmentProtectionApplies(cause)) return 0;
        boolean fire = isFireDamage(cause);
        boolean explosion = "explosion".equals(cause);
        boolean projectile = "arrow".equals(cause);
        boolean fall = isFallDamage(cause);
        int sum = 0;
        for (WideEnchantments piece : armor) {
            if (piece == null) continue;
            sum += piece.level(PROTECTION);
            if (fire) sum += 2 * piece.level(FIRE_PROTECTION);
            if (explosion) sum += 2 * piece.level(BLAST_PROTECTION);
            if (projectile) sum += 2 * piece.level(PROJECTILE_PROTECTION);
            if (fall) sum += 3 * piece.level(FEATHER_FALLING);
        }
        return sum;
    }

    /** 이 피해가 인챈트 보호 단계를 거치는가. 바닐라에서 기아({@code starve})만 #bypasses_effects 로 건너뛴다. */
    public static boolean enchantmentProtectionApplies(String cause) {
        return cause != null && !"starve".equals(cause);
    }

    /** 바닐라 #is_fire 에 드는 이 저장소의 피해 원인(모닥불·마그마 접촉도 {@code in_fire} 다). */
    public static boolean isFireDamage(String cause) {
        return "on_fire".equals(cause) || "in_fire".equals(cause) || "lava".equals(cause);
    }

    /** 바닐라 #is_fall 에 드는 이 저장소의 피해 원인. */
    public static boolean isFallDamage(String cause) {
        return "ender_pearl".equals(cause) || "fall_small".equals(cause) || "fall_big".equals(cause);
    }

    /**
     * 화염으로부터 보호가 줄인 연소 틱. 바닐라 {@code LivingEntity.igniteForTicks}:
     * {@code ceil(ticks × burning_time)}, {@code burning_time = max(0, 1 - 0.15·Σlevel)}.
     */
    public static int burningTicksAfterFireProtection(int ticks, WideEnchantments... armor) {
        if (ticks <= 0) return 0;
        // 부위마다 LevelBasedValue.Linear 를 float 로 계산하고 AttributeInstance 는 double 로 더한다.
        double modifiers = 0.0;
        for (WideEnchantments piece : armor) {
            int level = piece == null ? 0 : piece.level(FIRE_PROTECTION);
            if (level > 0) {
                modifiers += linearFloat(-FIRE_PROTECTION_BURN_REDUCTION_PER_LEVEL,
                        -FIRE_PROTECTION_BURN_REDUCTION_PER_LEVEL, level);
            }
        }
        if (modifiers == 0.0) return ticks;
        double multiplier = Math.max(0.0, 1.0 + modifiers);
        return (int) Math.ceil(ticks * multiplier);
    }

    /** 바닐라 {@code LevelBasedValue.Linear}: {@code base + perLevelAboveFirst × (level - 1)}(float). */
    public static float linearFloat(float base, float perLevelAboveFirst, int level) {
        return base + perLevelAboveFirst * (level - 1);
    }

    /**
     * 차가운 걸음 장화가 이 접촉 피해를 막는가. 바닐라 {@code damage_immunity}:
     * #burn_from_stepping(모닥불·마그마 {@code hot_floor}).
     */
    public static boolean frostWalkerImmuneToStepping(WideEnchantments boots) {
        return boots != null && boots.level(FROST_WALKER) > 0;
    }

    /** 가시가 이번 피격에 발동하는가. @param roll {@code nextFloat()} [0,1) */
    public static boolean thornsTriggers(int level, float roll) {
        return level > 0 && roll < THORNS_CHANCE_PER_LEVEL * level;
    }

    /** 가시 반사 피해(float). @param roll {@code nextFloat()} [0,1) */
    public static float thornsDamage(float roll) {
        return roll * (THORNS_MAX_DAMAGE - THORNS_MIN_DAMAGE) + THORNS_MIN_DAMAGE;
    }

    /**
     * 호흡이 이번 MC 틱의 공기 감소를 막는가. 바닐라 {@code LivingEntity.decreaseAirSupply}:
     * {@code oxygen_bonus > 0 && random.nextDouble() >= 1/(oxygen_bonus + 1)} 이면 공기를 그대로 둔다.
     */
    public static boolean respirationKeepsAir(int level, double roll) {
        return level > 0 && roll >= 1.0 / (level + 1.0);
    }

    /** 친수성: 물속 채굴 속도 배율 {@code min(20, 0.2 × (1 + 4·level))}. 1레벨이면 1.0(벌칙 없음). */
    public static float submergedMiningMultiplier(int aquaAffinityLevel) {
        float value = SUBMERGED_MINING_BASE
                * (1.0F + AQUA_AFFINITY_MULTIPLIER_PER_LEVEL * Math.max(0, aquaAffinityLevel));
        return Math.min(20.0F, value);
    }

    /** 물갈퀴: water_movement_efficiency {@code min(1, 0.33333334·level)}. */
    public static float depthStriderEfficiency(int level) {
        if (level <= 0) return 0.0F;
        return Math.min(1.0F, linearFloat(DEPTH_STRIDER_EFFICIENCY_PER_LEVEL,
                DEPTH_STRIDER_EFFICIENCY_PER_LEVEL, level));
    }

    /** 신속한 잠행: 웅크리기 속도 배율 {@code min(1, 0.3 + 0.15·level)}. */
    public static float sneakingSpeed(int swiftSneakLevel) {
        if (swiftSneakLevel <= 0) return SNEAKING_SPEED_BASE;
        return Math.min(1.0F, SNEAKING_SPEED_BASE + linearFloat(SWIFT_SNEAK_SPEED_PER_LEVEL,
                SWIFT_SNEAK_SPEED_PER_LEVEL, swiftSneakLevel));
    }

    /**
     * 수선 한 번의 결과: 이 XP 로 고친 내구도와 남은 XP. 바닐라 {@code ExperienceOrb.repairPlayerItems}
     * (26.3 바이트코드): {@code i = 2·xp; j = min(i, damage); 남은 xp = xp - j·xp/i}(정수 나눗셈).
     * 남은 XP 가 있으면 다른 손상 아이템을 다시 고르고, 고친 뒤 남은 XP 가 0 이하이면 0 이다.
     */
    public static int[] mendingRepair(int xp, int damage) {
        if (xp <= 0 || damage <= 0) return new int[] {0, xp};
        int repairCapacity = MENDING_DURABILITY_PER_XP * xp;
        int repaired = Math.min(repairCapacity, damage);
        int remaining = xp - repaired * xp / repairCapacity;
        return new int[] {repaired, Math.max(0, remaining)};
    }

    /** 몹 종이 바닐라 #sensitive_to_bane_of_arthropods(#arthropod: 벌·좀벌레·거미·동굴 거미·엔더마이트)인가. */
    public static boolean isArthropod(com.gameexpert.engine.mob.MobType type) {
        return switch (type) {
            case BEE, SILVERFISH, SPIDER, CAVE_SPIDER -> true;
            default -> false;
        };
    }

    /** 몹 종이 바닐라 #sensitive_to_impaling(#aquatic)인가. */
    public static boolean isAquatic(com.gameexpert.engine.mob.MobType type) {
        return switch (type) {
            case TURTLE, AXOLOTL, GUARDIAN, ELDER_GUARDIAN, COD, PUFFERFISH, SALMON,
                    TROPICAL_FISH, DOLPHIN, SQUID, GLOW_SQUID, TADPOLE, NAUTILUS,
                    ZOMBIE_NAUTILUS -> true;
            default -> false;
        };
    }

    /** 방어구 감쇄 이후 피해(milli)에 보호 인챈트 감쇄 {@code 1 - epf/25}를 더 먹인다. */
    public static int damageAfterProtectionMilli(int damageMilli, int epf) {
        if (damageMilli <= 0) return 0;
        int capped = Math.min(Math.max(epf, 0), PROTECTION_EPF_CAP);
        return damageMilli * (PROTECTION_EPF_DIVISOR - capped) / PROTECTION_EPF_DIVISOR;
    }

    /**
     * 내구성 n으로 이번 내구 소모를 건너뛰는가.
     *
     * <p>도구는 {@code n/(n+1)} 확률로 건너뛴다. 방어구는 바닐라가 60% 확률로 판정 자체를 건너뛰므로
     * 실제 피해 적용 확률이 {@code 0.6 + 0.4/(n+1)}가 된다.
     *
     * @param bypassRollPermille 방어구 분기용 난수 {@code [0, 1000)}
     * @param levelRoll 음이 아닌 난수. 내부에서 {@code % (level + 1)} 로 사상한다.
     */
    public static boolean unbreakingSkipsDurability(
            int level, boolean armor, int bypassRollPermille, int levelRoll) {
        if (level <= 0) return false;
        if (armor && bypassRollPermille < UNBREAKING_ARMOR_BYPASS_PERMILLE) return false;
        int safe = levelRoll < 0 ? -levelRoll : levelRoll;
        return safe % (level + 1) > 0;
    }

    /**
     * 행운 n의 드랍 개수 배수(바닐라 ordinary multiplier).
     * {@code i = rand(n + 2) - 1; if (i < 0) i = 0; count *= (i + 1)}
     *
     * @param roll 음이 아닌 난수. 내부에서 {@code % (level + 2)} 로 사상한다.
     */
    public static int fortuneOrdinaryMultiplier(int level, int roll) {
        if (level <= 0) return 1;
        int safe = roll < 0 ? -roll : roll;
        int i = safe % (level + 2) - 1;
        if (i < 0) i = 0;
        return i + 1;
    }

    /**
     * 약탈 n 의 드랍 개수 가산분. 바닐라 {@code LootingEnchantFunction}(1.21 의
     * {@code minecraft:enchanted_count_increase}, {@code count: uniform 0..1})은
     * {@code Math.round(level * nextFloat())} 를 스택 개수에 더한다. 레벨이 0이면
     * <b>난수를 소비하지 않는다</b>(바닐라도 {@code i == 0} 이면 즉시 반환) — 그래서 약탈 없는
     * 처치의 드랍 난수열은 이 기능이 붙기 전과 비트 단위로 같다.
     *
     * @param roll {@code nextFloat()} 한 번 [0,1)
     */
    public static int lootingBonusCount(int level, float roll) {
        if (level <= 0) return 0;
        return Math.round(level * roll);
    }

    /**
     * [MACE] {@code minecraft:enchanted_count_increase} 의 {@code count: uniform(min, max)} 일반형
     * ({@code EnchantedCountIncreaseFunction.run}: {@code Math.round(level * value.getFloat(context))},
     * {@code UniformGenerator.getFloat = Mth.randomBetween(r, min, max) = nextFloat()·(max−min)+min},
     * 모두 float 산술). 레벨 0 이면 난수를 소비하지 않는다. 브리즈 막대가 {@code uniform(1, 2)} 를 쓴다.
     *
     * @param roll {@code nextFloat()} 한 번 [0,1)
     */
    public static int lootingUniformBonusCount(int level, float min, float max, float roll) {
        if (level <= 0) return 0;
        float value = roll * (max - min) + min;
        return Math.round(level * value);
    }

    /**
     * 약탈이 확률에 붙는 드랍(좀비 희귀 드랍·드라운드 구리·토끼발 등)의 보정 확률.
     * 바닐라 {@code LootItemRandomChanceWithLootingCondition} 은
     * {@code chance + looting * lootingMultiplier} 를 그대로 쓴다.
     */
    public static double lootingChance(double chance, int level, double perLevel) {
        if (level <= 0) return chance;
        return chance + level * perLevel;
    }

    /** 자갈에서 부싯돌이 나올 확률(permille). 행운 3 이상은 100%다. */
    public static int gravelFlintChancePermille(int fortuneLevel) {
        if (fortuneLevel <= 0) return GRAVEL_FLINT_PERMILLE[0];
        if (fortuneLevel >= GRAVEL_FLINT_PERMILLE.length) {
            return GRAVEL_FLINT_PERMILLE[GRAVEL_FLINT_PERMILLE.length - 1];
        }
        return GRAVEL_FLINT_PERMILLE[fortuneLevel];
    }

    // ── 인챈트대 제안 ──

    /** 인챈트대가 실제로 쓰는 책장 power(0~15). */
    public static int bookshelfPower(int bookshelvesAround) {
        if (bookshelvesAround <= 0) return 0;
        return Math.min(bookshelvesAround, MAX_BOOKSHELF_POWER);
    }

    /**
     * 제안 3줄이 공유하는 기준값.
     *
     * <p><b>바닐라와의 의도적 divergence</b>: 바닐라는 슬롯마다 {@code getEnchantmentCost}를 불러
     * {@code rand(8) + rand(power+1)}을 여섯 번 소비하고 독립된 base 세 개를 얻는다. 여기서는
     * 한 번만 굴려 세 줄이 같은 base 를 공유하므로 비용 셋이 서로의 결정론적 함수가 되고 분산이 좁다.
     * 양판이 같은 식을 쓰는 것이 우선이라 이대로 유지한다({@code docs/MC-REFERENCE.md} 참조).
     */
    public static int enchantCostBase(int enchantSeed, int power) {
        EnchantRandom random = new EnchantRandom(enchantSeed);
        return COST_BASE_MIN + random.nextInt(COST_BASE_ROLL_BOUND) + (power >> 1)
                + random.nextInt(power + 1);
    }

    /**
     * 제안 {@code slot}(0~2)의 요구 레벨.
     *
     * <p>바닐라 {@code EnchantmentMenu.slotsChanged}는 계산한 비용이 {@code slot + 1}(= 실제로 차감할
     * 레벨 수)보다 작으면 그 줄을 통째로 비운다. 그 규칙이 없으면 {@code power=0, base=1}에서 세 줄이
     * 모두 "1"로 표시되는데 3번째 줄은 3레벨을 청구해, 레벨 1 플레이어가 누르는 순간 누적 XP가 전부
     * 소각된다. 비운 줄은 {@link #EMPTY_OFFER_LEVEL_COST}로 표시한다.
     */
    public static int offerLevelCost(int slot, int base, int power) {
        int cost = switch (slot) {
            case 0 -> Math.max(base / 3, 1);
            case 1 -> base * 2 / 3 + 1;
            default -> Math.max(base, power * 2);
        };
        return cost < offerLevelsSpent(slot) ? EMPTY_OFFER_LEVEL_COST : cost;
    }

    /** 제안 {@code slot}이 요구하는 청금석 개수({@code slot + 1}). */
    public static int offerLapisCost(int slot) {
        return slot + 1;
    }

    /**
     * 제안 {@code slot}을 수락할 때 실제로 차감하는 레벨 수({@code slot + 1}).
     * 표시 비용({@link #offerLevelCost})과 다른 값이며 바닐라도 이 둘을 따로 검사한다.
     */
    public static int offerLevelsSpent(int slot) {
        return slot + 1;
    }

    /**
     * 빈 제안 줄의 요구 레벨. 대상 아이템이 없거나 인챈트 대상이 아닐 때도 제안은 언제나
     * {@link #ENCHANT_OFFER_COUNT}줄이며, 빈 줄은 이 값으로 표시한다. 클라이언트
     * ({@code enchantOfferView})가 {@code levelCost <= 0}을 빈 줄로 그리는 규약의 정본이다.
     */
    public static final int EMPTY_OFFER_LEVEL_COST = 0;

    /**
     * 제안을 고를 수 있는가. 레벨과 청금석 개수를 모두 만족해야 한다.
     *
     * <p>바닐라는 표시 비용({@code levelCost}) 외에 <b>실제 차감분 {@code slot + 1} 레벨</b>도 따로
     * 확인한다. 이 확인이 빠지면 표시 비용만 만족한 저레벨 플레이어가 3번째 줄을 눌러
     * {@code totalAfterSpendingLevels}가 0을 돌려주며 누적 XP를 전부 태운다.
     */
    public static boolean offerAffordable(int playerLevel, int lapisCount, int slot, int levelCost) {
        return levelCost > EMPTY_OFFER_LEVEL_COST
                && playerLevel >= levelCost
                && playerLevel >= offerLevelsSpent(slot)
                && lapisCount >= offerLapisCost(slot);
    }

    /**
     * 제안 줄이 실제로 눌릴 수 있는가(프로토콜 {@code EnchantOffer.affordable} 의 정본).
     * 지불 조건에 더해 <b>걸릴 인챈트가 하나라도 있어야</b> 한다 — 마스크가 비면 눌러도 아무 일이
     * 없으므로 두 권위 모두 비활성으로 그린다. 수락 경로도 같은 판정을 다시 쓴다.
     */
    public static boolean offerSelectable(int playerLevel, int lapisCount, int slot,
            int levelCost, long enchantments) {
        return enchantments != EMPTY_ENCHANTMENTS
                && offerAffordable(playerLevel, lapisCount, slot, levelCost);
    }

    /** {@link #offerSelectable(int, int, int, int, long)} 의 43종 집합판. */
    public static boolean offerSelectable(int playerLevel, int lapisCount, int slot,
            int levelCost, WideEnchantments enchantments) {
        return !enchantments.isEmpty()
                && offerAffordable(playerLevel, lapisCount, slot, levelCost);
    }

    /** {@code (a + b/2) / b}. 음이 아닌 정수 전용 반올림 나눗셈이며 양판이 같은 식을 쓴다. */
    public static int roundDiv(int a, int b) {
        if (b <= 0) return 0;
        return (a + b / 2) / b;
    }

    /**
     * 요구 레벨을 바닐라식으로 보정한 실제 추첨 레벨.
     * {@code level = cost + 1 + rand(e/4+1) + rand(e/4+1)} 뒤 ±15% 삼각분포를 곱한다.
     */
    public static int modifiedEnchantLevel(EnchantRandom random, short itemType, int levelCost) {
        int bound = enchantability(itemType) / 4 + 1;
        int level = levelCost + 1 + random.nextInt(bound) + random.nextInt(bound);
        int pctPermille = (random.nextInt(TRIANGULAR_ROLL_BOUND)
                + random.nextInt(TRIANGULAR_ROLL_BOUND) - MILLI) * TRIANGULAR_SPREAD_PERMILLE / MILLI;
        int scaled = roundDiv(level * (MILLI + pctPermille), MILLI);
        if (scaled < 1) return 1;
        return Math.min(scaled, MAX_ENCHANT_LEVEL_ROLL);
    }

    /**
     * [ENCHANT-WIDE] 추첨 후보 풀. 바닐라는 인챈트대가 {@code #minecraft:in_enchanting_table},
     * 전리품·낚시의 {@code enchant_with_levels}/{@code enchant_randomly} 가 대개
     * {@code #minecraft:on_random_loot} 를 쓴다(저주 둘·차가운 걸음·수선 포함).
     */
    public enum EnchantPool { ENCHANTING_TABLE, RANDOM_LOOT }

    /**
     * 이 보정 레벨에서 뽑힐 수 있는 인챈트 레벨. 조건을 만족하는 <b>가장 높은</b> 레벨 하나만 남으며,
     * 후보가 없으면 0이다. 인챈트대 풀이다.
     */
    public static int candidateLevel(int enchantId, short itemType, int level) {
        return candidateLevel(enchantId, itemType, level, EnchantPool.ENCHANTING_TABLE);
    }

    /**
     * 바닐라 {@code EnchantmentHelper.getAvailableEnchantmentResults}: 풀에 있고
     * {@link #isPrimaryItem}(책이면 전부)이며 {@code minCost <= level <= maxCost} 인 가장 높은 레벨.
     */
    public static int candidateLevel(int enchantId, short itemType, int level, EnchantPool pool) {
        boolean inPool = pool == EnchantPool.ENCHANTING_TABLE
                ? inEnchantingTable(enchantId) : onRandomLoot(enchantId);
        // [CURSE] 저주는 인챈트대 풀(non_treasure)에 없으므로 인챈트대가 절대 만들어내지 않는다.
        if (!inPool) return 0;
        if (!isPrimaryItem(enchantId, itemType)) return 0;
        for (int candidate = maxLevel(enchantId); candidate >= 1; candidate--) {
            if (level >= minCost(enchantId, candidate) && level <= maxCost(enchantId, candidate)) {
                return candidate;
            }
        }
        return 0;
    }

    /**
     * 제안 {@code slot}에 걸릴 인챈트 집합. 슬롯마다 독립된 PRNG를 쓰며, 슬롯 시드는
     * {@link EnchantRandom#forSlot(int, int)}이 뒤섞어 만든다(생 {@code enchantSeed + slot}은
     * {@code seed | 1} 때문에 세 줄 중 둘을 같은 수열로 뭉갠다).
     * {@code levelCost}가 비워진 줄(0)은 집합도 비운다.
     */
    public static WideEnchantments rollEnchantments(short itemType, int levelCost, int enchantSeed,
            int slot) {
        if (!isTableEnchantable(itemType)) return WideEnchantments.EMPTY;
        if (levelCost <= EMPTY_OFFER_LEVEL_COST) return WideEnchantments.EMPTY;
        EnchantRandom random = EnchantRandom.forSlot(enchantSeed, slot);
        int level = modifiedEnchantLevel(random, itemType, levelCost);
        int[] picks = new int[ENCHANTMENT_COUNT];
        int[] levels = new int[ENCHANTMENT_COUNT];
        int count = rollPicks(itemType, level, random, EnchantPool.ENCHANTING_TABLE, picks, levels);
        // [ENCHANT-WIDE] 바닐라 EnchantmentMenu.getEnchantmentList: 책이고 뽑힌 것이 둘 이상이면 같은
        // 난수로 뽑힌 순서 목록에서 하나를 뺀다(nextInt(size) 번째).
        int removed = itemType == PlayerInventory.BOOK && count > 1 ? random.nextInt(count) : -1;
        WideEnchantments picked = WideEnchantments.EMPTY;
        for (int index = 0; index < count; index++) {
            if (index != removed) picked = picked.with(picks[index], levels[index]);
        }
        return picked;
    }

    /** 인챈트대 풀의 {@link #rollEnchantmentsAt(short, int, EnchantRandom, EnchantPool)}. */
    public static WideEnchantments rollEnchantmentsAt(short itemType, int level,
            EnchantRandom random) {
        return rollEnchantmentsAt(itemType, level, random, EnchantPool.ENCHANTING_TABLE);
    }

    /**
     * 보정 레벨이 이미 정해진 상태에서의 가중 추첨. 첫 인챈트를 뽑고, 이후
     * {@code rand(50) <= level} 인 동안 레벨을 반으로 줄이며 호환되는 인챈트를 더 붙인다.
     */
    public static WideEnchantments rollEnchantmentsAt(short itemType, int level,
            EnchantRandom random, EnchantPool pool) {
        int[] picks = new int[ENCHANTMENT_COUNT];
        int[] levels = new int[ENCHANTMENT_COUNT];
        int count = rollPicks(itemType, level, random, pool, picks, levels);
        WideEnchantments picked = WideEnchantments.EMPTY;
        for (int index = 0; index < count; index++) picked = picked.with(picks[index], levels[index]);
        return picked;
    }

    /** 뽑힌 순서대로 {@code picks}/{@code levels} 를 채우고 개수를 돌려준다(바닐라 목록 순서). */
    private static int rollPicks(short itemType, int level, EnchantRandom random, EnchantPool pool,
            int[] picks, int[] levels) {
        int[] candidates = new int[ENCHANTMENT_COUNT];
        for (int id = 0; id < ENCHANTMENT_COUNT; id++) {
            candidates[id] = candidateLevel(id, itemType, level, pool);
        }
        int first = pickWeighted(candidates, random);
        if (first < 0) return 0;
        int count = 0;
        picks[count] = first;
        levels[count++] = candidates[first];
        removePicked(candidates, first);

        int remaining = level;
        while (random.nextInt(EXTRA_ENCHANT_ROLL_BOUND) <= remaining) {
            remaining /= 2;
            int extra = pickWeighted(candidates, random);
            if (extra < 0) break;
            picks[count] = extra;
            levels[count++] = candidates[extra];
            removePicked(candidates, extra);
        }
        return count;
    }

    /** 워드 0 마스크의 표시용 목록. {@link #describe(WideEnchantments)} 와 같다. */
    public static String describe(long mask) {
        return describe(WideEnchantments.legacy(mask));
    }

    /**
     * 표시용 이름 목록. 바닐라 툴팁처럼 {@code #minecraft:tooltip_order} 순서로 나열하고, 최대 레벨이
     * 1 이 아닌 인챈트에만 로마 숫자를 붙인다.
     */
    public static String describe(WideEnchantments enchantments) {
        StringBuilder out = new StringBuilder();
        for (int id : TOOLTIP_ORDER) {
            int level = enchantments.level(id);
            if (level <= 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append(enchantName(id));
            if (maxLevel(id) > 1) out.append(' ').append(romanNumeral(level));
        }
        return out.toString();
    }

    /** 1~15의 로마 숫자. 인챈트 레벨 표기 전용이다. */
    public static String romanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            case 13 -> "XIII";
            case 14 -> "XIV";
            case 15 -> "XV";
            default -> "";
        };
    }

    /** 가중 추첨. 후보가 없으면 -1이며 난수를 소비하지 않는다. */
    private static int pickWeighted(int[] candidates, EnchantRandom random) {
        int total = 0;
        for (int id = 0; id < ENCHANTMENT_COUNT; id++) {
            if (candidates[id] > 0) total += WEIGHTS[id];
        }
        if (total <= 0) return -1;
        int roll = random.nextInt(total);
        for (int id = 0; id < ENCHANTMENT_COUNT; id++) {
            if (candidates[id] <= 0) continue;
            roll -= WEIGHTS[id];
            if (roll < 0) return id;
        }
        return -1;
    }

    /** 뽑힌 인챈트와 그것과 배타적인 인챈트를 후보에서 지운다. */
    private static void removePicked(int[] candidates, int picked) {
        candidates[picked] = 0;
        for (int id = 0; id < ENCHANTMENT_COUNT; id++) {
            if (candidates[id] > 0 && !compatible(picked, id)) candidates[id] = 0;
        }
    }
}
