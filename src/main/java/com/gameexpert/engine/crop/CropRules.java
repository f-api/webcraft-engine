package com.gameexpert.engine.crop;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * 런타임 작물의 작은 정적 정본. 작물 추가 때 틱/상호작용 분기를 늘리지 않고 이 표만 확장한다.
 * ID·성장 형태·수확 수량 분포를 보관하고 실제 난수와 아이템 스폰은 서버 아이템 엔티티 시스템이 담당한다.
 */
public final class CropRules {
    public enum Kind { ORDINARY, STEM }

    public static final class Rule {
        private final short seedItem;
        private final int cropBlock;
        private final int maxAge;
        private final short immatureDrop;
        private final short matureDrop;
        private final int matureDropChanceNumerator;
        private final int matureDropChanceDenominator;
        private final int matureExtraBase;
        private final int matureExtraRolls;
        private final int matureExtraChanceNumerator;
        private final int matureExtraChanceDenominator;
        private final Kind kind;
        private final int fruitBlock;
        private final int[] validFruitFloors;

        private Rule(int seedItem, int cropBlock, int maxAge, int immatureDrop, int matureDrop,
                     int matureDropChanceNumerator, int matureDropChanceDenominator,
                     int matureExtraBase, int matureExtraRolls,
                     int matureExtraChanceNumerator, int matureExtraChanceDenominator,
                     Kind kind, int fruitBlock, int... validFruitFloors) {
            this.seedItem = (short) seedItem;
            this.cropBlock = cropBlock;
            this.maxAge = maxAge;
            this.immatureDrop = (short) immatureDrop;
            this.matureDrop = (short) matureDrop;
            this.matureDropChanceNumerator = matureDropChanceNumerator;
            this.matureDropChanceDenominator = matureDropChanceDenominator;
            this.matureExtraBase = matureExtraBase;
            this.matureExtraRolls = matureExtraRolls;
            this.matureExtraChanceNumerator = matureExtraChanceNumerator;
            this.matureExtraChanceDenominator = matureExtraChanceDenominator;
            this.kind = kind;
            this.fruitBlock = fruitBlock;
            this.validFruitFloors = validFruitFloors;
        }

        public short seedItem() { return seedItem; }
        public int cropBlock() { return cropBlock; }
        public int maxAge() { return maxAge; }
        public short immatureDrop() { return immatureDrop; }
        public short matureDrop() { return matureDrop; }
        public Kind kind() { return kind; }
        public boolean ordinary() { return kind == Kind.ORDINARY; }
        public boolean stem() { return kind == Kind.STEM; }
        public int fruitBlock() { return fruitBlock; }

        /** 성숙 시 주 수확물 한 개의 드랍 여부. 밀만 사용자 지정 1/4이고 나머지는 확정이다. */
        public int matureDropCount(java.util.function.IntUnaryOperator nextInt) {
            if (matureDropChanceNumerator <= 0) return 0;
            if (matureDropChanceNumerator >= matureDropChanceDenominator) return 1;
            return nextInt.applyAsInt(matureDropChanceDenominator) < matureDropChanceNumerator ? 1 : 0;
        }

        /** 성숙 시 별도 씨앗/동종 작물 보너스 드랍. 0이면 주 수확물만 드랍한다. */
        public int matureExtraDropCount(java.util.function.IntUnaryOperator nextInt) {
            int count = matureExtraBase;
            for (int i = 0; i < matureExtraRolls; i++) {
                if (nextInt.applyAsInt(matureExtraChanceDenominator) < matureExtraChanceNumerator) count++;
            }
            return count;
        }

        /** 호박 줄기는 age별 {@code Binomial(3, (age + 1) / 15)} 씨앗만 드랍한다. */
        public int stemSeedDropCount(int age, java.util.function.IntUnaryOperator nextInt) {
            int chanceNumerator = Math.min(maxAge, Math.max(0, age)) + 1;
            int count = 0;
            for (int i = 0; i < 3; i++) {
                if (nextInt.applyAsInt(15) < chanceNumerator) count++;
            }
            return count;
        }

        public boolean isValidFruitFloor(int block) {
            for (int floor : validFruitFloors) if (floor == block) return true;
            return false;
        }
    }

    /** 아이템 ID는 양수 short 전 범위, 블록 규칙은 append-only 정본 테이블 용량을 사용한다. */
    private static final Rule[] BY_SEED = new Rule[Short.MAX_VALUE + 1];
    private static final Rule[] BY_CROP = new Rule[Blocks.BLOCK_ID_TABLE_CAPACITY];

    static {
        register(new Rule(PlayerInventory.WHEAT_SEEDS, Blocks.WHEAT_CROP, 7,
                PlayerInventory.WHEAT_SEEDS, PlayerInventory.WHEAT,
                1, 4,
                1, 3, 4, 7,
                Kind.ORDINARY, Blocks.AIR));
        register(new Rule(PlayerInventory.CARROT, Blocks.CARROT_CROP, 7,
                PlayerInventory.CARROT, PlayerInventory.CARROT,
                1, 1,
                1, 3, 4, 7,
                Kind.ORDINARY, Blocks.AIR));
        register(new Rule(PlayerInventory.POTATO, Blocks.POTATO_CROP, 7,
                PlayerInventory.POTATO, PlayerInventory.POTATO,
                1, 1,
                1, 3, 4, 7,
                Kind.ORDINARY, Blocks.AIR));
        register(new Rule(Blocks.BEETROOT_SEEDS, Blocks.BEETROOT_CROP, 3,
                Blocks.BEETROOT_SEEDS, Blocks.BEETROOT,
                1, 1,
                1, 3, 4, 7,
                Kind.ORDINARY, Blocks.AIR));
        register(new Rule(Blocks.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM, 7,
                Blocks.PUMPKIN_SEEDS, Blocks.PUMPKIN_SEEDS,
                0, 1,
                0, 0, 0, 1,
                Kind.STEM, Blocks.PUMPKIN,
                Blocks.GRASS, Blocks.DIRT, Blocks.FARMLAND, Blocks.PODZOL));
        // [SNIFFER] 스니퍼가 파낸 두 종자. 바닐라 torchflower_crop 은 age 0..2 이며 마지막 단계에서
        // 횃불꽃 꽃 블록이 된다 — 이 엔진은 성숙 작물을 수확으로 닫으므로 성숙 수확물이 곧 횃불꽃
        // 블록 아이템이다. [PITCHER] pitcher_crop 은 age 0..4 이고 blocks/pitcher_crop.json 대로 성숙
        // 수확(age 4)은 벌레잡이풀(PITCHER_PLANT 2429) 하나, 미성숙은 꼬투리 하나다 — 윗 반은 전리품이
        // 없다(ItemEntitySystem.spawnCropDrops 가 PitcherRules 로 거른다). 횃불꽃 미성숙 파괴는 심은 종자
        // 1개다(InventoryRules 의 파괴 드랍과 같은 값).
        register(new Rule(PlayerInventory.TORCHFLOWER_SEEDS, Blocks.TORCHFLOWER_CROP, 2,
                PlayerInventory.TORCHFLOWER_SEEDS, Blocks.TORCHFLOWER,
                1, 1,
                0, 0, 0, 1,
                Kind.ORDINARY, Blocks.AIR));
        register(new Rule(PlayerInventory.PITCHER_POD, Blocks.PITCHER_CROP, 4,
                PlayerInventory.PITCHER_POD, Blocks.PITCHER_PLANT,
                1, 1,
                0, 0, 0, 1,
                Kind.ORDINARY, Blocks.AIR));
    }

    private CropRules() {
    }

    private static void register(Rule rule) {
        BY_SEED[rule.seedItem] = rule;
        BY_CROP[rule.cropBlock] = rule;
    }

    public static Rule forSeed(int itemId) {
        return itemId >= 0 && itemId < BY_SEED.length ? BY_SEED[itemId] : null;
    }

    public static Rule forCrop(int blockId) {
        return blockId >= 0 && blockId < BY_CROP.length ? BY_CROP[blockId] : null;
    }

    public static boolean isCrop(int blockId) { return forCrop(blockId) != null; }

    public static boolean isSameCrop(int blockId, Rule rule) {
        return rule != null && blockId == rule.cropBlock;
    }

    /** MC 일반 작물 뼛가루 증가량. RNG 호출 순서까지 기존 밀 경로와 같다. */
    public static int boneMealAge(Rule rule, int age, java.util.function.IntUnaryOperator nextInt) {
        return Math.min(rule.maxAge, age + 2 + nextInt.applyAsInt(4));
    }

    // ── [CROP-BERRY] 성숙 수확의 보너스 드랍 ────────────────────────────────────
    //
    // 바닐라 {@code loot_tables/blocks/potatoes.json} 은 풀이 셋이다: ① 감자 1개 ② age=7 일 때
    // 감자 추가 Binomial(3, 4/7) ③ <b>age=7 일 때 확률 0.02 로 독 감자 1개</b>. ①②는 이미 위
    // {@code Rule} 의 matureDrop/matureExtra 항이 담고 있고, 여기가 빠져 있던 ③이다.
    //
    // {@code Rule} 생성자에 항을 더하지 않고 별도 표로 둔 이유: 생성자 인자를 늘리면 이미
    // 등록된 다섯 작물 줄을 전부 고쳐야 하고, 보너스 드랍이 있는 바닐라 작물은 감자 하나뿐이라
    // 다섯 줄 중 넷에 "없음" 을 적게 된다. 표가 하나 더 늘어나는 대신 기존 줄은 그대로다.
    private static final short[] BONUS_DROP = new short[Blocks.BLOCK_ID_TABLE_CAPACITY];
    private static final int[] BONUS_NUMERATOR = new int[Blocks.BLOCK_ID_TABLE_CAPACITY];
    private static final int[] BONUS_DENOMINATOR = new int[Blocks.BLOCK_ID_TABLE_CAPACITY];

    static {
        BONUS_DROP[Blocks.POTATO_CROP] = PlayerInventory.POISONOUS_POTATO;
        BONUS_NUMERATOR[Blocks.POTATO_CROP] = 1;
        BONUS_DENOMINATOR[Blocks.POTATO_CROP] = 50; // 0.02
    }

    /** 성숙 수확 때 주 수확물과 <b>별도로</b> 떨어지는 아이템. 없으면 0. */
    public static short matureBonusDrop(Rule rule) {
        return rule == null ? 0 : BONUS_DROP[rule.cropBlock];
    }

    /**
     * 보너스 드랍 개수(0 또는 1). 호출부는 주 수확물 난수를 <b>전부 뽑은 뒤</b> 마지막에 이것을
     * 부른다 — 바닐라 전리품표의 풀 순서(① ② ③)와 같은 순서라야 두 권위의 난수열이 같다.
     */
    public static int matureBonusDropCount(Rule rule, java.util.function.IntUnaryOperator nextInt) {
        if (rule == null) return 0;
        int denominator = BONUS_DENOMINATOR[rule.cropBlock];
        if (denominator <= 0) return 0;
        return nextInt.applyAsInt(denominator) < BONUS_NUMERATOR[rule.cropBlock] ? 1 : 0;
    }
}
