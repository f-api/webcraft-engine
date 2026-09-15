package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;

/** 모닥불에 올릴 수 있는 음식과 화로 제련표의 조리 결과를 연결합니다. */
public final class CampfireRules {

    /** 이 프로젝트의 10 TPS에서 Java 600 game ticks(30초)를 보존한 조리 시간입니다. */
    public static final int COOK_TICKS = 300;

    /** 소화 후 Java의 두 배 속도 식힘을 10 TPS 진행 단위로 보존합니다. */
    public static final int COOL_PROGRESS_PER_TICK = 2;

    private CampfireRules() {
    }

    /**
     * 모닥불 조리 결과입니다. 화로와 결과 ID는 공유하지만 음식 9종 외의 제련 입력은 명시적으로 거부합니다.
     *
     * <p>[COOKING] 바닐라 {@code campfire_cooking} 레시피 목록은 {@code smoking} 목록과 원소가
     * 같습니다 — 즉 "모닥불이 받는 것 = 훈연기가 받는 것 = 제련표의 FOOD 분류"입니다. 그래서
     * 목록을 여기서 다시 세지 않고 {@link FurnaceRules#smeltCategory} 에 되물어 봅니다.
     * 사본으로 적어 두면 FOOD 항이 늘 때 한쪽만 갱신돼 조용히 갈립니다(실제로 다시마가 그렇게
     * 빠져 있었습니다). 근거는 {@code docs/research/mc-food-cooking.md} §5 입니다.
     */
    public static short cookOutput(short input) {
        if (FurnaceRules.smeltCategory(input) != FurnaceRules.SmeltCategory.FOOD) {
            return PlayerInventory.EMPTY;
        }
        return FurnaceRules.smeltOutput(input);
    }

    public static boolean isCookable(short input) {
        return cookOutput(input) != PlayerInventory.EMPTY;
    }
}
