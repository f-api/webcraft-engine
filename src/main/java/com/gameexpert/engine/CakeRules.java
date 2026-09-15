package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * [COOKING] 설치된 케이크의 조각 상태와 취식 판정입니다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneCakeRules.ts} 이고 두 소스의 상수는
 * 파리티 게이트로 묶여 있습니다.
 *
 * <p>근거는 {@code docs/research/mc-food-cooking.md} §3 이며 전부 바닐라 Java 값입니다.
 * 손에 든 케이크는 <b>먹을 수 없고</b>(설치만 된다), 설치한 블록을 먹습니다 — 그래서
 * 회복량이 {@code InventoryRules.foodNutrition} 표가 아니라 여기 있습니다.
 */
public final class CakeRules {

    /** 바닐라 {@code bites} 상태의 상한. 0(온전한 케이크) ~ 6(마지막 한 조각). */
    public static final int MAX_BITES = 6;

    /** 케이크 하나가 담는 조각 수. {@code MAX_BITES + 1} 이며 총 취식 횟수와 같습니다. */
    public static final int SLICES = MAX_BITES + 1;

    /** 한 조각의 허기 회복량(바닐라 nutrition 2). 일곱 조각이면 14 입니다. */
    public static final int SLICE_NUTRITION = 2;

    /**
     * 한 조각의 saturation 회복량(1/1000 = {@code HungerRules.MILLI}).
     * 바닐라 0.4 이며 일곱 조각이면 2.8 입니다.
     */
    public static final int SLICE_SATURATION_MILLI = 400;

    private CakeRules() {
    }

    /** 저장·프로토콜에 실리는 상태값이 케이크의 {@code bites} 로 유효한가. */
    public static boolean isValidBites(int bites) {
        return bites >= 0 && bites <= MAX_BITES;
    }

    /**
     * 케이크를 한 입 먹을 수 있는가. 바닐라 {@code CakeBlock#eat} 는 허기가 가득 차 있으면
     * 아무 일도 하지 않고 상호작용을 통과시킵니다 — 케이크는 {@code alwaysEdible} 이 아닙니다.
     * 이 저장소의 다른 음식과 갈리는 <b>유일한</b> 취식 규칙입니다.
     */
    public static boolean canEat(int blockId, int bites, int food) {
        return blockId == Blocks.CAKE && isValidBites(bites) && food < HungerRules.MAX_FOOD;
    }

    /**
     * 한 입 먹은 뒤의 블록 ID. 마지막 조각(= {@code bites} 가 {@link #MAX_BITES})을 먹으면
     * 케이크가 사라집니다(바닐라도 블록을 제거하고 아무것도 떨구지 않습니다).
     */
    public static int blockAfterBite(int bites) {
        return bites >= MAX_BITES ? Blocks.AIR : Blocks.CAKE;
    }

    /** 한 입 먹은 뒤의 {@code bites} 상태. 블록이 사라지는 경우 0 입니다. */
    public static int bitesAfterBite(int bites) {
        return bites >= MAX_BITES ? 0 : bites + 1;
    }
}
