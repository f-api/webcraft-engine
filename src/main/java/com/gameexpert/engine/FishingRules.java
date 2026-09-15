package com.gameexpert.engine;

import com.gameexpert.engine.enchant.EnchantRandom;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.NoiseSuite;

/**
 * 낚시(FISHING) 규칙 정본. 전리품 추첨·입질 타이밍은 전부 서버 권위이며, 정적판
 * client/src/backend/standalone/StandaloneFishing.ts 가 이 표를 그대로 복제한다.
 * 두 판의 동일성은 StandaloneFishing.test.ts 가 이 소스 텍스트를 직접 읽어 대조한다.
 *
 * 난수는 NoiseSuite 의 splitmix32 를 쓴다. TS 측 client/src/world/Noise.ts 와
 * 비트 단위로 같은 수열이므로, 같은 시드는 양판에서 같은 전리품·같은 대기 틱을 낸다.
 */
public final class FishingRules {
    private FishingRules() {}

    // ── 타이밍(권위 틱, 10 TPS) ──
    /** 권위 1 틱이 담는 바닐라 틱 수(10 TPS ↔ 20 TPS). */
    public static final int VANILLA_TICKS_PER_TICK = 2;
    /** 권위 틱 레이트(10 TPS). 임펄스를 블록/초로 환산할 때 쓴다. */
    public static final int TICKS_PER_SECOND = 10;
    /** 착수 후 입질까지의 최소 대기(5초). 바닐라 {@code timeUntilLured} 하한 100 바닐라 틱. */
    public static final int MIN_BITE_DELAY_TICKS = 50;
    /** 착수 후 입질까지의 최대 대기(30초). 바닐라 {@code timeUntilLured} 상한 600 바닐라 틱. */
    public static final int MAX_BITE_DELAY_TICKS = 300;
    /** 입질 판정창 최소 길이(1초). 바닐라 {@code nibble} 하한 20 바닐라 틱. */
    public static final int MIN_BITE_WINDOW_TICKS = 10;
    /** 입질 판정창 최대 길이(2초). 바닐라 {@code nibble} 상한 40 바닐라 틱. */
    public static final int MAX_BITE_WINDOW_TICKS = 20;
    /** 캐스팅 후 이 틱 안에 물에 닿지 못하면 캐스팅을 취소한다. */
    public static final int MAX_FLIGHT_TICKS = 60;
    /**
     * 대기가 끝난 뒤 물고기가 찌로 다가오는 접근 단계의 최소 길이(바닐라 틱).
     * 바닐라 {@code FishingHook.catchingFish} 의 {@code timeUntilHooked = Mth.nextInt(random, 20, 80)}
     * 하한이다. 대기와 같은 눈금(바닐라 틱)으로 재고 같은 {@link #waitStepTicks} 로 소모한다.
     */
    public static final int MIN_APPROACH_VANILLA_TICKS = 20;
    /** 접근 단계의 최대 길이(바닐라 {@code Mth.nextInt(random, 20, 80)} 상한). */
    public static final int MAX_APPROACH_VANILLA_TICKS = 80;
    /**
     * 접근 물결이 찌에서 떨어져 있는 거리 = 남은 접근 틱 × 이 계수(블록).
     * 바닐라의 {@code f1 * this.timeUntilHooked * 0.1F} 다. 남은 틱이 줄면 물결이 찌로 다가온다.
     */
    public static final double APPROACH_RADIUS_PER_VANILLA_TICK = 0.1;
    /**
     * 접근 물결 각도가 바닐라 틱마다 흔들리는 폭(도).
     * 바닐라 {@code this.fishAngle += (float) this.random.triangle(0.0, 9.188)} 이다.
     */
    public static final double FISH_ANGLE_DRIFT_DEGREES = 9.188;
    /**
     * 후킹된 엔티티를 회수했을 때 소모하는 낚싯대 내구. 바닐라 {@code FishingHook.retrieve} 의
     * {@code i = this.hookedIn instanceof ItemEntity ? 3 : 5} 중 엔티티 쪽 값이다.
     * WebCraft 는 아이템 엔티티를 후킹하지 않으므로 5 하나만 쓴다.
     */
    public static final int HOOKED_ENTITY_DURABILITY_COST = 5;
    /**
     * 후킹된 엔티티가 캐스터 쪽으로 받는 속도 = (캐스터 − 찌) × 이 계수(블록/바닐라 틱).
     * 바닐라 {@code FishingHook.pullEntity} 의 {@code .scale(0.1)} 이다.
     */
    public static final double ENTITY_PULL_SCALE = 0.1;
    /**
     * 후킹된 엔티티에 붙은 찌의 높이 = 엔티티 발밑 + 키 × 이 비율.
     * 바닐라 {@code this.setPos(hookedIn.getX(), hookedIn.getY(0.8), hookedIn.getZ())} 다.
     */
    public static final double HOOKED_ENTITY_HEIGHT_FRACTION = 0.8;
    /**
     * 찌가 플레이어에게서 이보다 멀어지면 줄이 끊긴다(블록).
     * 바닐라 {@code FishingHook.shouldStopFishing} 의 {@code distanceToSqr > 1024.0} 다.
     */
    public static final double MAX_TETHER_DISTANCE = 32.0;
    /** 캐스팅 초기 속도(블록/틱). 스노우볼과 같은 세기로 던진다. */
    public static final double CAST_POWER = 1.5;
    /** 입질 중 찌가 잠기는 깊이(블록). 별도 메시지 없이 위치 갱신만으로 보인다. */
    public static final double BITE_SINK_DEPTH = 0.25;
    /**
     * 비행 중 찌에 걸리는 중력(블록/틱²). 바닐라 FishingHook 은 화살(0.05)이 아니라
     * 자기 값 0.03 을 쓴다. 다른 투사체와 같이 바닐라 틱당 값을 그대로 옮긴다.
     */
    public static final double BOBBER_GRAVITY = 0.03;
    /** 비행 중 찌의 공기 저항(바닐라 FishingHook 의 0.92). 물약(0.99)보다 훨씬 강하다. */
    public static final double BOBBER_AIR_INERTIA = 0.92;
    /**
     * 물 셀 윗면(=찌가 뜨는 높이). 바닐라 FishingHook 은 BOBBING 상태에서
     * blockY + FluidState.getHeight() 에 수렴하므로 찌는 언제나 "보이는 수면"에 뜬다.
     * WebCraft 의 물 표면은 ChunkMesher.fluidSampleHeight 가 fluidLevelHeight16(=14/16)
     * 로 그리므로 그 높이를 그대로 쓴다. 위에 물이 이어지는 칸은 풀 높이(1.0)다.
     */
    public static final double WATER_SURFACE_HEIGHT = 0.875;
    /** 전리품 획득 1회당 낚싯대 내구 소모. */
    public static final int CATCH_DURABILITY_COST = 1;
    /**
     * [WEBCRAFT 계약] 다이아 낚싯대(481)의 "낚시 속도 +10%". 바닐라 1.21.4 에는 없는 효과다.
     * 의미는 <b>대기시간(바닐라 {@code timeUntilLured}) 10% 단축</b> 하나로 못박는다. 판정창
     * ({@code nibble})·전리품 가중치·경험치·내구 소모는 건드리지 않는다 — 판정창을 줄이면
     * "빨라진다"가 아니라 "어려워진다"가 되고, 가중치를 건드리면 바닐라 Luck of the Sea 자리를
     * 침범하기 때문이다.
     * <p>Lure 인챈트(바닐라 {@code timeUntilLured -= lureSpeed * 20 * 5}, 레벨당 −100 바닐라 틱)와의
     * 결합은 <b>곱연산이되 인챈트 감산 이후</b>에 적용한다: {@code floor((roll − lure) × 0.9)}.
     * 근거는 (1) 바닐라가 Lure 를 절대 틱 감산으로 정의하므로 먼저 감산해야 인챈트 값이 보존되고,
     * (2) 뒤에 곱하면 "남은 대기의 10%"라는 뜻이 유지되어 Lure 가 세도 다이아 보너스가 음수 대기를
     * 만들지 않기 때문이다(합연산으로 −10% 를 상수 틱으로 바꾸면 Lure 와 겹칠 때 이중 감산이 된다).
     */
    public static final double DIAMOND_ROD_WAIT_MULTIPLIER = 0.9;

    // ── 대기 진행(바닐라 FishingHook.catchingFish 의 i) ──
    /**
     * 비를 맞는 찌 위에서 대기가 두 배로 줄어들 확률의 역수.
     * 바닐라 {@code random.nextFloat() < 0.25F && level.isRainingAt(above)} 이면 {@code i++} 다.
     */
    public static final int RAIN_SPEEDUP_ODDS = 4;
    /**
     * 하늘이 막힌 찌 위에서 대기가 아예 멈출 확률의 역수.
     * 바닐라 {@code random.nextFloat() < 0.5F && !level.canSeeSky(above)} 이면 {@code i--} 다.
     */
    public static final int NO_SKY_SLOWDOWN_ODDS = 2;

    // ── 경험치 ──
    /** 전리품을 건졌을 때 주는 경험치 하한. 바닐라 {@code random.nextInt(6) + 1}. */
    public static final int MIN_CATCH_XP = 1;
    /** 전리품을 건졌을 때 주는 경험치 상한. */
    public static final int MAX_CATCH_XP = 6;

    // ── 미끼(lure) ──
    /**
     * 미끼 1레벨이 줄이는 대기(바닐라 틱). 바닐라 {@code FishingHook.catchingFish} 의
     * {@code this.timeUntilLured -= this.lureSpeed * 20 * 5} 다.
     */
    public static final int LURE_TICK_REDUCTION = 100;
    /** 미끼 최대 레벨(바닐라 lure III). */
    public static final int MAX_LURE_LEVEL = 3;
    /** 바다의 행운 최대 레벨(바닐라 luck_of_the_sea III). */
    public static final int MAX_LUCK_LEVEL = 3;

    // ── open water(바닐라 FishingHook.calculateOpenWater) ──
    /** 검사 정사각형의 반지름(블록). 한 층이 5×5 다. */
    public static final int OPEN_WATER_RADIUS = 2;
    /** 검사 최하층의 상대 Y. 찌 블록의 한 칸 아래다. */
    public static final int OPEN_WATER_MIN_DY = -1;
    /** 검사 최상층의 상대 Y. 찌 블록의 두 칸 위다(총 4개 층 → 5×4×5). */
    public static final int OPEN_WATER_MAX_DY = 2;

    /** open water 판정의 한 칸 분류. 바닐라 {@code FishingHook.OpenWaterType} 이다. */
    public enum OpenWaterType {
        /** 물도 공기도 아닌 칸. 이 칸이 하나라도 있으면 그 층은 실격이다. */
        INVALID,
        /** 공기(또는 수련잎)라 수면 위로 친다. */
        ABOVE_WATER,
        /** 원천 물이라 수면 아래로 친다. */
        INSIDE_WATER,
    }

    // open water 판정이 읽는 블록 조회는 Fluids.BlockLookup 을 그대로 쓴다.

    // ── 전리품표 ──
    /**
     * 범주 추첨의 가중치 합(바닐라 {@code gameplay/fishing.json} 한 pool 의 entries 합).
     * luck 이 0 이고 open water 일 때의 값이며, 품질 보정·open water 조건이 붙으면 달라진다.
     */
    public static final int LOOT_TOTAL_WEIGHT = 100;
    /** 물고기 범주 가중치(바닐라 85). */
    public static final int FISH_WEIGHT = 85;
    /** 잡동사니 범주 가중치(바닐라 10). */
    public static final int JUNK_WEIGHT = 10;
    /** 보물 범주 가중치(바닐라 5). */
    public static final int TREASURE_WEIGHT = 5;
    /** 물고기 범주 품질(바닐라 quality -1). luck 이 오를수록 물고기가 줄어든다. */
    public static final int FISH_QUALITY = -1;
    /** 잡동사니 범주 품질(바닐라 quality -2). */
    public static final int JUNK_QUALITY = -2;
    /** 보물 범주 품질(바닐라 quality 2). luck 이 오를수록 보물이 늘어난다. */
    public static final int TREASURE_QUALITY = 2;

    /** 하위 표의 가중치 합(바닐라 fishing_fish·fishing_junk 각각 100). */
    public static final int FISH_TABLE_WEIGHT = 100;
    /** 잡동사니 하위 표의 가중치 합. */
    public static final int JUNK_TABLE_WEIGHT = 100;
    /** 보물 하위 표의 가중치 합(항목 6종 × 1). */
    public static final int TREASURE_TABLE_WEIGHT = 6;

    /** 고정소수 배율(1.0 = 1000). 손상 비율·품질 계산을 정수로만 다룬다. */
    public static final int MILLI = 1000;
    /**
     * 보물 인챈트의 요구 레벨. 바닐라 {@code enchant_with_levels {"levels": 30}} 다.
     * 바닐라는 여기에 treasure 인챈트도 포함하지만 WebCraft 인챈트 9종에는 treasure 전용이 없어
     * 일반 추첨을 그대로 쓴다(MC-REFERENCE 낚시 전리품표 절의 WebCraft 계약).
     */
    public static final int TREASURE_ENCHANT_LEVELS = 30;

    /**
     * 전리품 1건.
     *
     * @param itemType 지급할 아이템
     * @param count 개수
     * @param weight 하위 표 안의 가중치
     * @param damageMinPermille 바닐라 {@code set_damage} 의 최소 damage(=남는 내구 비율, permille)
     * @param damageMaxPermille 같은 함수의 최대 damage. 두 값이 모두 0이면 손상 함수가 없다.
     * @param enchantLevels 바닐라 {@code enchant_with_levels} 의 요구 레벨. 0이면 인챈트 없음.
     */
    public record LootEntry(short itemType, int count, int weight,
                            int damageMinPermille, int damageMaxPermille, int enchantLevels) {
        /** 손상 함수가 없는 평범한 항목. */
        public LootEntry(short itemType, int count, int weight) {
            this(itemType, count, weight, 0, 0, 0);
        }

        /** 바닐라 {@code set_damage} 가 붙어 있는가. */
        public boolean damaged() {
            return damageMaxPermille > 0;
        }
    }

    /** 실제로 지급되는 한 건. 내구·인챈트까지 확정한 값이다. */
    public record LootDrop(short itemType, int count, int durability,
            com.gameexpert.engine.enchant.WideEnchantments enchantments) {}

    /**
     * 물고기 하위 표(바닐라 {@code gameplay/fishing_fish.json}). 순서·가중치가 바닐라 그대로다.
     * 정적판과 순서가 완전히 같아야 하므로 항목을 끼워 넣지 말고 항상 끝에 덧붙인다.
     */
    private static final LootEntry[] FISH_TABLE = {
            new LootEntry(PlayerInventory.COD_RAW, 1, 60),
            new LootEntry(PlayerInventory.SALMON_RAW, 1, 25),
            new LootEntry(PlayerInventory.TROPICAL_FISH, 1, 2),
            new LootEntry(PlayerInventory.PUFFERFISH, 1, 13),
    };

    /**
     * 잡동사니 하위 표(바닐라 {@code gameplay/fishing_junk.json}) 12종.
     * 물병은 바닐라 {@code potion{minecraft:water}} 에 대응하는 WebCraft 아이템이다.
     */
    private static final LootEntry[] JUNK_TABLE = {
            new LootEntry((short) Blocks.LILY_PAD, 1, 17),
            new LootEntry(PlayerInventory.LEATHER_BOOTS, 1, 10, 0, 900, 0),
            new LootEntry(PlayerInventory.LEATHER, 1, 10),
            new LootEntry(PlayerInventory.BONE, 1, 10),
            new LootEntry((short) Blocks.WATER_BOTTLE, 1, 10),
            new LootEntry(PlayerInventory.ROTTEN_FLESH, 1, 10),
            new LootEntry(PlayerInventory.STICK, 1, 5),
            new LootEntry(PlayerInventory.STRING, 1, 5),
            new LootEntry(PlayerInventory.FISHING_ROD, 1, 2, 0, 900, 0),
            new LootEntry(PlayerInventory.BOWL, 1, 10),
            new LootEntry(PlayerInventory.TRIPWIRE_HOOK, 1, 10),
            new LootEntry(PlayerInventory.INK_SAC, 10, 1),
    };

    /**
     * 보물 하위 표(바닐라 {@code gameplay/fishing_treasure.json}) 6종, 전부 가중치 1이다.
     * 활·낚싯대·마법책은 30레벨 인챈트를 갖고 나오고, 활·낚싯대는 25%까지만 남는 손상도 받는다.
     */
    private static final LootEntry[] TREASURE_TABLE = {
            new LootEntry(PlayerInventory.BOW, 1, 1, 0, 250, TREASURE_ENCHANT_LEVELS),
            new LootEntry(PlayerInventory.ENCHANTED_BOOK, 1, 1, 0, 0, TREASURE_ENCHANT_LEVELS),
            new LootEntry(PlayerInventory.FISHING_ROD, 1, 1, 0, 250, TREASURE_ENCHANT_LEVELS),
            new LootEntry(PlayerInventory.NAME_TAG, 1, 1),
            new LootEntry(PlayerInventory.NAUTILUS_SHELL, 1, 1),
            new LootEntry(PlayerInventory.SADDLE, 1, 1),
    };

    static {
        checkTableWeight("fish", FISH_TABLE, FISH_TABLE_WEIGHT);
        checkTableWeight("junk", JUNK_TABLE, JUNK_TABLE_WEIGHT);
        checkTableWeight("treasure", TREASURE_TABLE, TREASURE_TABLE_WEIGHT);
        if (FISH_WEIGHT + JUNK_WEIGHT + TREASURE_WEIGHT != LOOT_TOTAL_WEIGHT) {
            throw new IllegalStateException("fishing category weights must sum to "
                    + LOOT_TOTAL_WEIGHT);
        }
    }

    private static void checkTableWeight(String label, LootEntry[] table, int expected) {
        int total = 0;
        for (LootEntry entry : table) {
            total += entry.weight();
        }
        if (total != expected) {
            throw new IllegalStateException(
                    "fishing " + label + " weights must sum to " + expected);
        }
    }

    /** 물고기 하위 표 사본. 파리티 테스트와 도감이 읽는다. */
    public static LootEntry[] fishTable() {
        return FISH_TABLE.clone();
    }

    /** 잡동사니 하위 표 사본. */
    public static LootEntry[] junkTable() {
        return JUNK_TABLE.clone();
    }

    /** 보물 하위 표 사본. */
    public static LootEntry[] treasureTable() {
        return TREASURE_TABLE.clone();
    }

    // ── 난수 ──
    // 한 번의 캐스팅에서 여러 값을 뽑아도 서로 상관되지 않도록 용도별 소금을 섞는다.
    private static final int SALT_BITE_DELAY = 1;
    private static final int SALT_BITE_WINDOW = 2;
    private static final int SALT_CATCH_XP = 4;
    /** 대기 진행 판정은 틱마다 뽑으므로 바닐라 서브틱 2개 × (비·하늘) 2종 = 소금 4개를 쓴다. */
    private static final int SALT_WAIT_RAIN = 5;
    private static final int SALT_WAIT_SKY = 7;
    /** 범주(물고기·잡동사니·보물) 추첨. */
    private static final int SALT_LOOT_CATEGORY = 9;
    /** 하위 표 안의 항목 추첨. */
    private static final int SALT_LOOT_ENTRY = 10;
    /** 바닐라 {@code set_damage} 의 damage 비율 추첨. */
    private static final int SALT_LOOT_DAMAGE = 11;
    /** 보물 인챈트 추첨(EnchantRandom 시드). */
    private static final int SALT_LOOT_ENCHANT = 12;
    /** 접근 단계 길이(바닐라 {@code timeUntilHooked}) 추첨. */
    private static final int SALT_APPROACH = 13;
    /** 접근 물결의 최초 각도(바닐라 {@code fishAngle = Mth.nextFloat(random, 0, 360)}). */
    private static final int SALT_FISH_ANGLE_START = 14;
    /**
     * 접근 물결 각도의 틱당 표류. 바닐라 {@code random.triangle} 은 균등 난수 두 개의 차라
     * 서브틱 2개 × 2개 = 소금 4개(15..18)를 쓴다.
     */
    private static final int SALT_FISH_ANGLE_DRIFT = 15;

    /**
     * 찌가 착수한 지점과 틱에서 캐스팅 시드를 만든다. 월드 시드가 같고 같은 자리·같은 틱에
     * 착수하면 양판이 같은 결과를 낸다.
     */
    public static int castSeed(int worldSeed, long tickNo, int x, int y, int z) {
        return NoiseSuite.hash3(worldSeed, x, y, z) ^ NoiseSuite.mix32((int) tickNo);
    }

    private static int roll(int seed, int salt, int bound) {
        return new NoiseSuite.Rng(NoiseSuite.S(seed, salt)).rndInt(bound);
    }

    /** 같은 캐스팅 안에서 틱마다 새로 뽑는 값. 틱 번호까지 섞어 매 틱 독립이 되게 한다. */
    private static int tickRoll(int seed, long tickNo, int salt, int bound) {
        return new NoiseSuite.Rng(NoiseSuite.S(NoiseSuite.S(seed, salt), (int) tickNo)).rndInt(bound);
    }

    /** 착수부터 입질까지 대기할 틱 수(MIN_BITE_DELAY_TICKS ~ MAX_BITE_DELAY_TICKS). */
    public static int biteDelayTicks(int seed) {
        int span = MAX_BITE_DELAY_TICKS - MIN_BITE_DELAY_TICKS + 1;
        return MIN_BITE_DELAY_TICKS + roll(seed, SALT_BITE_DELAY, span);
    }

    /**
     * 착수부터 입질까지 채워야 하는 대기량(바닐라 틱 단위). 대기 진행이 날씨·하늘에 따라
     * 틱마다 달라지므로 목표는 바닐라 눈금으로 재고, 진행분만 {@link #waitStepTicks} 가 준다.
     * <p>[훅] Lure 인챈트가 들어오면 감산은 <b>이 함수 안</b>에서 하고, 낚싯대 종류 보정은
     * 건드리지 말 것 — {@link #biteDelayVanillaTicks(int, int, short)} 가 그 결과 위에 곱한다.
     */
    public static int biteDelayVanillaTicks(int seed) {
        return biteDelayVanillaTicks(seed, 0);
    }

    /**
     * 미끼 {@code lureLevel} 을 반영한 대기량(바닐라 틱). 바닐라 {@code FishingHook.catchingFish} 는
     * {@code timeUntilLured -= lureSpeed * 20 * 5} 로 레벨당 100 바닐라 틱을 깎는다.
     * 0 아래로는 내려가지 않는다(그 틱에 바로 입질한다).
     */
    public static int biteDelayVanillaTicks(int seed, int lureLevel) {
        int lure = Math.min(Math.max(lureLevel, 0), MAX_LURE_LEVEL);
        int base = biteDelayTicks(seed) * VANILLA_TICKS_PER_TICK;
        return Math.max(base - lure * LURE_TICK_REDUCTION, 0);
    }

    /**
     * 낚싯대 종류별 대기시간 배율. 다이아 낚싯대만 {@link #DIAMOND_ROD_WAIT_MULTIPLIER} 이고
     * 나머지(기본 낚싯대·손에 든 것이 낚싯대가 아닐 때)는 1.0 이다.
     */
    public static double rodWaitMultiplier(short rodType) {
        return rodType == PlayerInventory.DIAMOND_FISHING_ROD ? DIAMOND_ROD_WAIT_MULTIPLIER : 1.0;
    }

    /**
     * 대기량에 낚싯대 배율을 먹인다. 틱은 정수이므로 내림하고, 0 미만으로는 내려가지 않는다
     * (0 이면 다음 틱에 바로 입질 — 바닐라에서 Lure 로 {@code timeUntilLured} 가 음수가 됐을 때와 같다).
     */
    public static int applyWaitMultiplier(int vanillaTicks, double multiplier) {
        return Math.max(0, (int) Math.floor(vanillaTicks * multiplier));
    }

    /**
     * 이 낚싯대로 낚을 때 채워야 하는 대기량(바닐라 틱). 미끼가 없을 때의 편의 오버로드다.
     */
    public static int biteDelayVanillaTicks(int seed, short rodType) {
        return biteDelayVanillaTicks(seed, 0, rodType);
    }

    /**
     * 미끼 인챈트와 낚싯대 종류를 모두 반영한 최종 대기량(바닐라 틱). 결합 순서는
     * {@link #DIAMOND_ROD_WAIT_MULTIPLIER} 의 계약대로 <b>인챈트 감산이 먼저, 배율이 나중</b>
     * 이다: {@code floor(max(roll − lure × 100, 0) × mult)}. 미끼가 대기를 0 으로 만들면
     * 배율도 0 을 유지하므로 다이아 보너스가 음수 대기를 만들지 않는다.
     */
    public static int biteDelayVanillaTicks(int seed, int lureLevel, short rodType) {
        return applyWaitMultiplier(biteDelayVanillaTicks(seed, lureLevel),
                rodWaitMultiplier(rodType));
    }

    /**
     * 권위 한 틱이 소모하는 대기량(바닐라 틱). 바닐라 {@code FishingHook.catchingFish} 는
     * 바닐라 틱마다 기본 1 을 깎고, 찌 위 칸에 비가 내리면 1/4 확률로 하나 더 깎으며,
     * 찌 위 칸이 하늘에 열려 있지 않으면 1/2 확률로 그 틱을 통째로 건너뛴다. 권위 1 틱 =
     * 바닐라 2 틱이라 같은 판정을 두 번 해서 더한다(맑고 열린 하늘이면 항상 정확히 2).
     */
    public static int waitStepTicks(int seed, long tickNo, boolean rainingOnBobber,
                                    boolean openToSky) {
        int total = 0;
        for (int sub = 0; sub < VANILLA_TICKS_PER_TICK; sub++) {
            int step = 1;
            if (rainingOnBobber
                    && tickRoll(seed, tickNo, SALT_WAIT_RAIN + sub, RAIN_SPEEDUP_ODDS) == 0) {
                step++;
            }
            if (!openToSky
                    && tickRoll(seed, tickNo, SALT_WAIT_SKY + sub, NO_SKY_SLOWDOWN_ODDS) == 0) {
                step--;
            }
            total += step;
        }
        return total;
    }

    /** 전리품을 건졌을 때 함께 나오는 경험치(MIN_CATCH_XP ~ MAX_CATCH_XP). */
    public static int catchXp(int seed) {
        return MIN_CATCH_XP + roll(seed, SALT_CATCH_XP, MAX_CATCH_XP - MIN_CATCH_XP + 1);
    }

    /** 입질 판정창 길이(MIN_BITE_WINDOW_TICKS ~ MAX_BITE_WINDOW_TICKS). */
    public static int biteWindowTicks(int seed) {
        int span = MAX_BITE_WINDOW_TICKS - MIN_BITE_WINDOW_TICKS + 1;
        return MIN_BITE_WINDOW_TICKS + roll(seed, SALT_BITE_WINDOW, span);
    }

    // ── 접근 단계(바닐라 timeUntilHooked) ──

    /** splitmix32 한 값을 [0, 1) 실수로 접는다. TS 사본과 같은 double 을 내야 한다. */
    private static double unitRoll(int seed, int salt) {
        return (new NoiseSuite.Rng(NoiseSuite.S(seed, salt)).next() >>> 1) / 2147483648.0;
    }

    /** 같은 캐스팅 안에서 틱마다 새로 뽑는 [0, 1) 실수. */
    private static double unitTickRoll(int seed, long tickNo, int salt) {
        return (new NoiseSuite.Rng(
                NoiseSuite.S(NoiseSuite.S(seed, salt), (int) tickNo)).next() >>> 1) / 2147483648.0;
    }

    /**
     * 대기가 끝난 뒤 채워야 하는 접근량(바닐라 틱).
     * 바닐라 {@code timeUntilHooked = Mth.nextInt(random, 20, 80)} 의 양 끝 포함 구간이다.
     */
    public static int approachVanillaTicks(int seed) {
        int span = MAX_APPROACH_VANILLA_TICKS - MIN_APPROACH_VANILLA_TICKS + 1;
        return MIN_APPROACH_VANILLA_TICKS + roll(seed, SALT_APPROACH, span);
    }

    /** 접근 물결의 최초 각도(도). 바닐라 {@code Mth.nextFloat(random, 0.0F, 360.0F)} 다. */
    public static double approachStartAngleDegrees(int seed) {
        return unitRoll(seed, SALT_FISH_ANGLE_START) * 360.0;
    }

    /**
     * 이 권위 틱에 각도가 흔들리는 양(도). 바닐라는 바닐라 틱마다
     * {@code random.triangle(0.0, 9.188)}(= 균등 난수 두 개의 차 × 반경)을 더하므로
     * 권위 1 틱 = 바닐라 2 틱만큼 누적한다.
     */
    public static double approachAngleDriftDegrees(int seed, long tickNo) {
        double drift = 0;
        for (int sub = 0; sub < VANILLA_TICKS_PER_TICK; sub++) {
            double low = unitTickRoll(seed, tickNo, SALT_FISH_ANGLE_DRIFT + sub * 2);
            double high = unitTickRoll(seed, tickNo, SALT_FISH_ANGLE_DRIFT + sub * 2 + 1);
            drift += FISH_ANGLE_DRIFT_DEGREES * (high - low);
        }
        return drift;
    }

    /** 접근 물결이 찌에서 떨어져 있는 거리(블록). 남은 접근량이 줄면 찌로 다가온다. */
    public static double approachRadius(int remainingVanillaTicks) {
        return Math.max(remainingVanillaTicks, 0) * APPROACH_RADIUS_PER_VANILLA_TICK;
    }

    /** 접근 물결의 X. 바닐라 {@code getX() + sin(fishAngle) * timeUntilHooked * 0.1} 이다. */
    public static double approachWakeX(double bobberX, double angleDegrees,
                                       int remainingVanillaTicks) {
        return bobberX + Math.sin(Math.toRadians(angleDegrees))
                * approachRadius(remainingVanillaTicks);
    }

    /** 접근 물결의 Z. 바닐라 {@code getZ() + cos(fishAngle) * timeUntilHooked * 0.1} 이다. */
    public static double approachWakeZ(double bobberZ, double angleDegrees,
                                       int remainingVanillaTicks) {
        return bobberZ + Math.cos(Math.toRadians(angleDegrees))
                * approachRadius(remainingVanillaTicks);
    }

    /** 접근 물결이 그려지는 높이. 바닐라 {@code (float) Mth.floor(getY()) + 1.0F} 다. */
    public static double approachWakeY(double bobberY) {
        return Math.floor(bobberY) + 1.0;
    }

    // ── 엔티티 후킹(바닐라 FishingHook.onHitEntity · pullEntity) ──

    /**
     * 후킹된 엔티티가 회수 순간 받는 속도 성분(블록/틱). 바닐라 {@code pullEntity} 는
     * (소유자 − 찌) 벡터에 0.1 을 곱해 그대로 deltaMovement 에 더한다.
     */
    public static double entityPullComponent(double ownerCoordinate, double bobberCoordinate) {
        return (ownerCoordinate - bobberCoordinate) * ENTITY_PULL_SCALE;
    }

    /** 같은 임펄스를 권위 틱 단위(블록/권위 틱)로. 몹 속도장이 쓰는 눈금이다. */
    public static double entityPullPerAuthorityTick(double ownerCoordinate,
                                                    double bobberCoordinate) {
        return entityPullComponent(ownerCoordinate, bobberCoordinate) * VANILLA_TICKS_PER_TICK;
    }

    /** 같은 임펄스를 블록/초로. 플레이어에게 보내는 fishingPull 메시지의 단위다. */
    public static double entityPullBlocksPerSecond(double ownerCoordinate,
                                                   double bobberCoordinate) {
        return entityPullPerAuthorityTick(ownerCoordinate, bobberCoordinate) * TICKS_PER_SECOND;
    }

    /** 엔티티에 붙은 찌의 Y. 바닐라 {@code hookedIn.getY(0.8)} 이다. */
    public static double hookedBobberY(double entityY, double entityHeight) {
        return entityY + entityHeight * HOOKED_ENTITY_HEIGHT_FRACTION;
    }

    // ── 전리품 추첨 ──

    /**
     * 품질 보정을 먹인 실효 가중치. 바닐라 {@code LootPoolEntryContainer.getWeight(float luck)}
     * 의 {@code Math.max(Mth.floor(weight + quality * luck), 0)} 이며, luck 이 정수라 절삭이
     * 곧 정수 연산이다.
     */
    public static int effectiveWeight(int weight, int quality, int luck) {
        return Math.max(weight + quality * luck, 0);
    }

    /** 바다의 행운 레벨을 0..{@link #MAX_LUCK_LEVEL} 로 자른 luck 값. */
    public static int luckFromEnchantments(long enchantments) {
        int level = EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.LUCK_OF_THE_SEA);
        return Math.min(Math.max(level, 0), MAX_LUCK_LEVEL);
    }

    /** 미끼 레벨을 0..{@link #MAX_LURE_LEVEL} 로 자른 값. */
    public static int lureFromEnchantments(long enchantments) {
        int level = EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.LURE);
        return Math.min(Math.max(level, 0), MAX_LURE_LEVEL);
    }

    /** 전리품 범주. 추첨 순서는 바닐라 {@code gameplay/fishing.json} 의 entries 순서다. */
    public enum LootCategory {
        /** 물고기 4종. */
        FISH,
        /** 잡동사니 12종. */
        JUNK,
        /** 보물 6종. open water 에서만 나온다. */
        TREASURE,
    }

    /** 범주별 하위 표. */
    public static LootEntry[] tableOf(LootCategory category) {
        return switch (category) {
            case FISH -> FISH_TABLE;
            case JUNK -> JUNK_TABLE;
            case TREASURE -> TREASURE_TABLE;
        };
    }

    /**
     * 범주를 고른다. 바닐라 LootPool 은 조건을 통과한 entry 만 총합에 넣으므로,
     * open water 가 아니면 보물 entry 가 통째로 빠지고 총합도 그만큼 줄어든다.
     */
    public static LootCategory rollCategory(int seed, int luck, boolean openWater) {
        int fish = effectiveWeight(FISH_WEIGHT, FISH_QUALITY, luck);
        int junk = effectiveWeight(JUNK_WEIGHT, JUNK_QUALITY, luck);
        int treasure = openWater ? effectiveWeight(TREASURE_WEIGHT, TREASURE_QUALITY, luck) : 0;
        int total = fish + junk + treasure;
        if (total <= 0) return LootCategory.FISH;
        int pick = roll(seed, SALT_LOOT_CATEGORY, total);
        if (pick < fish) return LootCategory.FISH;
        if (pick < fish + junk) return LootCategory.JUNK;
        return LootCategory.TREASURE;
    }

    /**
     * 판정창 안에서 회수했을 때 지급할 전리품 1건(내구·인챈트 확정 전).
     *
     * @param luck 바다의 행운 레벨(0~3). 범주 품질 보정에만 쓰인다.
     * @param openWater 바닐라 {@code in_open_water} 조건. false 면 보물이 나오지 않는다.
     */
    public static LootEntry rollLoot(int seed, int luck, boolean openWater) {
        LootCategory category = rollCategory(seed, luck, openWater);
        LootEntry[] table = tableOf(category);
        int total = 0;
        for (LootEntry entry : table) {
            total += entry.weight();
        }
        int pick = roll(seed, SALT_LOOT_ENTRY, total);
        int cursor = 0;
        for (LootEntry entry : table) {
            cursor += entry.weight();
            if (pick < cursor) return entry;
        }
        // 가중치 합이 static 초기화에서 검증되어 있으므로 도달하지 않는다.
        return table[table.length - 1];
    }

    /**
     * 항목의 내구도(남은 값). 바닐라 {@code SetItemDamageFunction} 은
     * {@code damageValue = floor((1 - d) * maxDamage)} 로 적고 d 를 {@code [min, max]} 에서
     * 균등 추첨한다. WebCraft 는 남은 내구를 들고 있으므로 그 여집합을 쓴다. 손상 함수가 없는
     * 항목·내구가 없는 항목은 초기 내구(내구 없으면 0)를 그대로 돌려준다.
     */
    public static int rollDurability(LootEntry entry, int seed) {
        int max = PlayerInventory.initialDurability(entry.itemType());
        if (max <= 0 || !entry.damaged()) return max;
        int span = entry.damageMaxPermille() - entry.damageMinPermille() + 1;
        int remainingPermille = entry.damageMinPermille() + roll(seed, SALT_LOOT_DAMAGE, span);
        int damageValue = (MILLI - remainingPermille) * max / MILLI;
        int remaining = max - damageValue;
        // 바닐라는 내구가 0 인 스택도 만들 수 있지만 WebCraft 인벤토리는 1 이상만 담는다.
        return Math.max(remaining, 1);
    }

    /**
     * 항목의 인챈트 집합. 바닐라 {@code enchant_with_levels {"levels": 30,
     * "options": "#minecraft:on_random_loot"}}(gameplay/fishing/treasure) 을 WebCraft 인챈트 추첨
     * ({@code EnchantmentRules.rollEnchantmentsAt})으로 옮긴 것이다. [ENCHANT-WIDE] 후보 풀은 바닐라
     * 그대로 on_random_loot 라 수선·소실의 저주도 나온다.
     */
    public static com.gameexpert.engine.enchant.WideEnchantments rollLootEnchantments(
            LootEntry entry, int seed) {
        if (entry.enchantLevels() <= 0) return com.gameexpert.engine.enchant.WideEnchantments.EMPTY;
        EnchantRandom random = new EnchantRandom(NoiseSuite.S(seed, SALT_LOOT_ENCHANT));
        return EnchantmentRules.rollEnchantmentsAt(
                entry.itemType(), entry.enchantLevels(), random,
                EnchantmentRules.EnchantPool.RANDOM_LOOT);
    }

    /** 전리품 1건을 내구·인챈트까지 확정해 돌려준다. */
    public static LootDrop rollLootDrop(int seed, int luck, boolean openWater) {
        LootEntry entry = rollLoot(seed, luck, openWater);
        return new LootDrop(entry.itemType(), entry.count(),
                rollDurability(entry, seed), rollLootEnchantments(entry, seed));
    }

    // ── open water(바닐라 FishingHook.calculateOpenWater) ──

    /**
     * 한 칸의 분류. 바닐라 {@code getOpenWaterTypeForBlock} 은 공기·수련잎을 ABOVE_WATER 로,
     * 충돌이 없는 <b>원천</b> 물을 INSIDE_WATER 로 보고 나머지를 INVALID 로 본다.
     */
    public static OpenWaterType openWaterTypeForBlock(int blockId) {
        if (blockId == Blocks.AIR || blockId == Blocks.LILY_PAD) return OpenWaterType.ABOVE_WATER;
        if (blockId == Fluids.WATER_SOURCE) return OpenWaterType.INSIDE_WATER;
        return OpenWaterType.INVALID;
    }

    /**
     * 한 층(5×5)의 분류. 바닐라 {@code getOpenWaterTypeForArea} 처럼 층 안이 한 종류로
     * 통일되어 있어야 하고, 섞이면 INVALID 다.
     */
    public static OpenWaterType openWaterTypeForLayer(Fluids.BlockLookup blocks, int bx, int y,
            int bz) {
        OpenWaterType merged = null;
        for (int dx = -OPEN_WATER_RADIUS; dx <= OPEN_WATER_RADIUS; dx++) {
            for (int dz = -OPEN_WATER_RADIUS; dz <= OPEN_WATER_RADIUS; dz++) {
                OpenWaterType type = openWaterTypeForBlock(blocks.get(bx + dx, y, bz + dz));
                if (merged == null) {
                    merged = type;
                } else if (merged != type) {
                    return OpenWaterType.INVALID;
                }
            }
        }
        return merged == null ? OpenWaterType.INVALID : merged;
    }

    /**
     * 찌가 open water 위에 있는가. 바닐라 {@code calculateOpenWater} 를 그대로 옮겼다.
     * 찌 블록 기준 아래 1층 ~ 위 2층(총 4층)의 5×5 를 아래에서 위로 훑으면서
     * <b>물 층이 먼저, 공기 층이 나중</b>인 순서만 통과시킨다. 한 층이라도 섞이거나
     * 물·공기가 아닌 블록이 있으면 실패이고, 공기 층 뒤에 물 층이 다시 나와도 실패다.
     */
    public static boolean isOpenWater(Fluids.BlockLookup blocks, int bx, int by, int bz) {
        OpenWaterType previous = OpenWaterType.INVALID;
        for (int dy = OPEN_WATER_MIN_DY; dy <= OPEN_WATER_MAX_DY; dy++) {
            OpenWaterType type = openWaterTypeForLayer(blocks, bx, by + dy, bz);
            switch (type) {
                case INVALID -> {
                    return false;
                }
                case ABOVE_WATER -> {
                    // 첫 층이 공기면(=아직 물을 못 봤으면) 찌가 물 위에 뜬 상태가 아니다.
                    if (previous == OpenWaterType.INVALID) return false;
                }
                case INSIDE_WATER -> {
                    // 공기 층 뒤에 물이 다시 나오면 뚜껑 덮인 물이다.
                    if (previous == OpenWaterType.ABOVE_WATER) return false;
                }
                default -> {
                    return false;
                }
            }
            previous = type;
        }
        return true;
    }

    /**
     * 찌가 뜰 수면의 절대 Y. 바닐라 FishingHook 은 blockY + 유체 높이에 뜨고, WebCraft 의
     * 물 표면도 셀 높이(원천 14/16, 흐르는 물 level×2/16)로 그려진다. 위에 물이 이어지는
     * 칸은 렌더러와 같게 풀 높이로 본다.
     */
    public static double waterSurfaceY(int cellY, int waterId, int aboveId) {
        if (Fluids.isWater(aboveId)) {
            return cellY + 1.0;
        }
        if (waterId == Fluids.WATER_SOURCE) {
            return cellY + WATER_SURFACE_HEIGHT;
        }
        return cellY + ((waterId - Fluids.WATER_SOURCE) * 2) / 16.0;
    }

    /** 줄이 끊길 만큼 멀어졌는가. 수평·수직 모두 본다. */
    public static boolean tetherBroken(double playerX, double playerY, double playerZ,
                                       double bobberX, double bobberY, double bobberZ) {
        double dx = playerX - bobberX;
        double dy = playerY - bobberY;
        double dz = playerZ - bobberZ;
        return dx * dx + dy * dy + dz * dz > MAX_TETHER_DISTANCE * MAX_TETHER_DISTANCE;
    }

    /**
     * 낚아 올린 전리품이 플레이어 쪽으로 날아가는 초기 속도(블록/틱). 바닐라와 같은
     * 계수를 쓴다: 수평은 거리 비례, 수직은 거리의 제곱근에 비례해 포물선을 만든다.
     */
    public static double[] catchVelocity(double bobberX, double bobberY, double bobberZ,
                                         double playerX, double playerY, double playerZ) {
        double dx = playerX - bobberX;
        double dy = playerY - bobberY;
        double dz = playerZ - bobberZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[] {
                dx * 0.1,
                dy * 0.1 + Math.sqrt(distance) * 0.08,
                dz * 0.1,
        };
    }
}
