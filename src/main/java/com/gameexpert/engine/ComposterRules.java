package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * [COMPOSTER] 퇴비통(526)의 순수 규칙. 근거 고정본은 {@code docs/research/mc-composter-dye.md}
 * §1 이다(등급 [B] — 고정 스냅샷에 {@code ComposterBlock} 원문이 없다).
 *
 * <p>블록 자체는 이미 있었다(제작 · 경도 0.6 · 농부 작업장 POI · 가연표). <b>메커니즘만</b>
 * 없었으므로 이 클래스가 그 결손을 채운다. 상태 바이트는 바닐라 {@code level} 그대로
 * <b>0..8</b> 이고 8 이 "수거 가능(ready)" 이다.
 *
 * <p><b>WebCraft divergence — 7→8 즉시 전환.</b> 바닐라는 {@code level=7} 이 된 뒤
 * 20 game tick(1초, 이 저장소 10 TPS 로는 10틱) 예약 틱으로 8 이 된다. 이 저장소에는
 * 불 전용 스케줄러 말고 범용 예약 틱이 없어 새 스케줄러 · 새 영속 필드 · 청크 축출 복구
 * 경로를 함께 만들어야 하는데 그것은 이 트랙 범위 밖이다. 그래서 7 에 도달하는 순간 같은
 * 판정 안에서 8 로 넘긴다 — <b>{@code level=7} 은 관측되지 않는 과도 상태</b>다. 투입
 * 횟수 · 성공 확률 · 뼛가루 산출량은 바닐라와 같으므로 한 스택 평균 산출량은 불변이다.
 *
 * <p>순수 함수만 두고 상태·좌표·인벤토리를 모르는 이유는 두 권위(Java {@code WorldTickLoop} ·
 * 정적판 {@code StandaloneComposterRules})가 <b>같은 표</b>를 보게 하기 위해서다.
 */
public final class ComposterRules {

    private ComposterRules() {
    }

    /** 바닐라 {@code level} 최댓값. 이 값이면 우클릭 수거로 뼛가루가 나온다. */
    public static final int READY_LEVEL = 8;
    /** 아이템 투입으로 도달할 수 있는 최대 단계. 여기 닿으면 곧바로 {@link #READY_LEVEL} 이 된다. */
    public static final int MAX_FILL_LEVEL = 7;
    /** 수거 한 번이 내는 뼛가루 개수. 바닐라 {@code ComposterBlock#extractProduce} 와 같다. */
    public static final int PRODUCE_COUNT = 1;

    /**
     * 퇴비 성공 확률. 0 이면 퇴비 대상이 아니다.
     *
     * <p>바닐라 {@code ComposterBlock.bootStrap()} 의 다섯 계급(0.3/0.5/0.65/0.85/1.0)에서
     * <b>이 저장소에 실존하는 아이템만</b> 남긴 표다. 없는 아이템(분홍 꽃잎 · 네더 사마귀
     * 블록 · 빛나는이끼덩이 · 수박 · 진달래 잎 · 네더 균사류 등)은 다른 재료로 대체하지
     * 않고 그냥 비운다. 1.0 계급은 해당 아이템이 하나도 없어 비어 있다.
     *
     * <p><b>다른 트랙 소유 아이템은 넣지 않는다.</b> 같은 웨이브의 음식 트랙이 코코아 콩
     * (0.65) · 쿠키(0.85) · 케이크 · 호박 파이(1.0) · 마른 다시마(0.3)를 Java 쪽에만 선언해
     * 둔 상태이고 클라 상수는 아직 없다. 여기 Java 표에만 넣으면 정적판 손 사본
     * ({@code StandaloneComposterRules})이 같은 표를 만들 수 없어 두 권위가 갈린다 —
     * <b>그 다섯은 음식 트랙이 클라 상수를 착지시킨 뒤 자기 트랙이 이 표에 append</b> 하는
     * 것이 옳다. 확률 값은 {@code docs/research/mc-composter-dye.md} §1.2 가 이미 고정해 뒀다.
     */
    public static double compostChance(short itemType) {
        return switch (itemType) {
            // ── 0.30 ──────────────────────────────────────────────
            case Blocks.LEAVES, Blocks.BIRCH_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.JUNGLE_LEAVES,
                 Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.CHERRY_LEAVES,
                 Blocks.MANGROVE_LEAVES, Blocks.AZALEA_LEAVES,
                 PlayerInventory.OAK_SAPLING, PlayerInventory.BIRCH_SAPLING,
                 Blocks.SPRUCE_SAPLING, Blocks.JUNGLE_SAPLING, Blocks.ACACIA_SAPLING,
                 Blocks.DARK_OAK_SAPLING, Blocks.CHERRY_SAPLING, Blocks.MANGROVE_PROPAGULE,
                 PlayerInventory.WHEAT_SEEDS, Blocks.BEETROOT_SEEDS, Blocks.PUMPKIN_SEEDS,
                 // 이 저장소의 TALL_GRASS(11) 는 한 칸 풀 다발이라 바닐라 short_grass 에 대응한다.
                 Blocks.RED_SHRUB, Blocks.TALL_GRASS, Blocks.SEAGRASS, Blocks.KELP,
                 Blocks.SWEET_BERRIES, Blocks.GLOW_BERRIES,
                 Blocks.MOSS_CARPET, Blocks.SMALL_DRIPLEAF,
                 Blocks.HANGING_ROOTS, Blocks.MANGROVE_ROOTS,
                 // 야생화 · 낙엽 리터 · 선인장 꽃 30% 는 mc-1215/SPRING_TO_LIFE.md 가 소유한 값이다.
                 Blocks.WILDFLOWERS, Blocks.LEAF_LITTER, Blocks.CACTUS_FLOWER,
                 // [PITCHER] ComposterBlock.bootStrap: PITCHER_POD 0.3.
                 PlayerInventory.PITCHER_POD -> 0.30;
            // ── 0.50 ──────────────────────────────────────────────
            // 꽃 핀 진달래 잎만 COMPOSTABLE_LOW_MEDIUM 이다 — 고정본 inner jar 의
            // Items.<clinit> 가 FLOWERING_AZALEA_LEAVES 에만
            // NumberProviders.COMPOSTABLE_LOW_MEDIUM 을 붙이고(다른 잎은 COMPOSTABLE_LOW),
            // data/minecraft/number_provider/compostable/low_medium.json 의 가중치가 50/50 이다.
            case Blocks.FLOWERING_AZALEA_LEAVES,
                 Blocks.CACTUS, Blocks.SUGARCANE, Blocks.VINE, Blocks.GLOW_LICHEN -> 0.50;
            // ── 0.65 ──────────────────────────────────────────────
            case Blocks.SEA_PICKLE, Blocks.LILY_PAD, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN,
                 PlayerInventory.APPLE, Blocks.BEETROOT, PlayerInventory.CARROT,
                 PlayerInventory.POTATO, PlayerInventory.WHEAT,
                 // [CROP-BERRY] 독 감자 0.65(바닐라 ComposterBlock 표 — 보통 감자와 같은 값).
                 PlayerInventory.POISONOUS_POTATO,
                 Blocks.MUSHROOM_BROWN, Blocks.MUSHROOM_RED, Blocks.MUSHROOM_STEM,
                 // [SHELF-FUNGUS-WOOL-SLAB] 선반버섯도 퇴비 확률 0.65 다 [B]
                 // ("65% chance of raising the compost level by 1"). 다른 버섯과 같은 계급이다.
                 Blocks.SHELF_MUSHROOM,
                 Blocks.NETHER_WART,
                 Blocks.FLOWER_RED, Blocks.FLOWER_YELLOW, Blocks.CLOSED_EYEBLOSSOM, Blocks.OPEN_EYEBLOSSOM,
                 Blocks.MOSS_BLOCK, Blocks.BIG_DRIPLEAF -> 0.65;
            // ── 0.85 ──────────────────────────────────────────────
            case Blocks.HAY_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK,
                 Blocks.AZALEA, Blocks.FLOWERING_AZALEA,
                 PlayerInventory.BREAD, Blocks.BAKED_POTATO,
                 // [PITCHER] ComposterBlock.bootStrap: PITCHER_PLANT 0.85.
                 Blocks.PITCHER_PLANT -> 0.85;
            default -> 0.0;
        };
    }

    /** 퇴비통에 넣을 수 있는 아이템인가. */
    public static boolean isCompostable(short itemType) {
        return compostChance(itemType) > 0.0;
    }

    /** 상태 바이트를 유효한 단계로 자른다. 손상된 상태가 들어와도 표가 무너지지 않게 한다. */
    public static int level(int blockState) {
        if (blockState < 0) return 0;
        return Math.min(blockState, READY_LEVEL);
    }

    /** 우클릭으로 뼛가루를 꺼낼 수 있는 상태인가. */
    public static boolean isReady(int blockState) {
        return level(blockState) == READY_LEVEL;
    }

    /**
     * 아이템 하나를 넣었을 때의 새 단계. <b>넣을 수 없는 아이템이면 현재 단계를 그대로</b>
     * 돌려준다(호출자는 값이 같으면 아무것도 소비하지 않는다).
     *
     * <p>바닐라 판정 그대로다: {@code level==0} 이면 무조건 성공, 그 밖에는 {@code roll < chance}.
     * 실패해도 아이템은 소비된다(바닐라도 소비한다) — 그 소비는 호출자의 몫이다.
     *
     * @param roll {@code [0,1)} 균등 난수
     * @return 새 단계(성공이면 +1, 7 도달 시 곧바로 {@link #READY_LEVEL})
     */
    public static int levelAfterInsert(int blockState, short itemType, double roll) {
        int current = level(blockState);
        double chance = compostChance(itemType);
        if (chance <= 0.0 || current >= MAX_FILL_LEVEL) return current;
        boolean success = current == 0 || roll < chance;
        if (!success) return current;
        int next = current + 1;
        return next >= MAX_FILL_LEVEL ? READY_LEVEL : next;
    }

    /** 투입이 단계를 실제로 올렸는가. 소리·블록 갱신 방송을 나눌 때 쓴다. */
    public static boolean insertFilled(int blockState, short itemType, double roll) {
        return levelAfterInsert(blockState, itemType, roll) != level(blockState);
    }
}
