package com.gameexpert.engine.mob;

import com.gameexpert.engine.SuspiciousStewRules;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 바닐라 무시룸의 갈색/붉은 변종과 갈색 무시룸의 꽃 효과 상태를 소유하는 좁은 규칙 경계다.
 * 인벤토리 예약과 낙뢰 엔티티 중복 제거는 중앙 권위가 맡고, 이 클래스는 개체 상태만 원자적으로
 * 검사·변경한다.
 */
public final class MooshroomRules {
    public static final String RED = "red";
    public static final String BROWN = "brown";
    public static final int NO_STORED_FLOWER = 0;

    private MooshroomRules() { }

    /** 갈색 성체이고 아직 꽃 효과가 비어 있을 때만 꽃 한 송이를 저장한다. */
    public static boolean feedFlower(Mob mob, int flowerBlockId) {
        if (!(mob instanceof Mooshroom mooshroom) || mob.isDead() || mob.isBaby()
                || !SuspiciousStewRules.isStewFlower(flowerBlockId)) return false;
        return mooshroom.storeFlower(flowerBlockId);
    }

    /** 그릇 상호작용을 예약할 결과. 꽃 효과를 지우지 않는 읽기 전용 단계다. */
    public static short plannedStew(Mob mob) {
        if (!(mob instanceof Mooshroom mooshroom) || mob.isDead() || mob.isBaby()) {
            return PlayerInventory.EMPTY;
        }
        return mooshroom.plannedStew();
    }

    /**
     * 인벤토리에 정확한 스튜가 들어간 뒤 꽃 효과를 커밋한다. 일반 버섯 스튜와 붉은 무시룸은
     * 지울 상태가 없으므로 그대로 성공한다.
     */
    public static boolean confirmStew(Mob mob, short plannedItem) {
        if (!(mob instanceof Mooshroom mooshroom) || mob.isDead() || mob.isBaby()
                || plannedItem == PlayerInventory.EMPTY) return false;
        return mooshroom.confirmStew(plannedItem);
    }

    /** 한 번 커밋된 낙뢰마다 red↔brown 을 뒤집고 저장 대상으로 만든다. */
    public static boolean toggleByLightning(Mob mob) {
        if (!(mob instanceof Mooshroom mooshroom) || mob.isDead()) return false;
        mooshroom.toggleVariant();
        return true;
    }

    public static int storedFlower(Mob mob) {
        return mob instanceof Mooshroom mooshroom
                ? mooshroom.storedFlower() : NO_STORED_FLOWER;
    }

    /** 영속 복원 경계. 다른 종에 무시룸 상태가 붙은 손상 행을 거부한다. */
    public static void restoreStoredFlower(Mob mob, int flowerBlockId) {
        if (mob instanceof Mooshroom mooshroom) {
            mooshroom.restoreStoredFlower(flowerBlockId);
        } else if (flowerBlockId != NO_STORED_FLOWER) {
            throw new IllegalArgumentException("non-Mooshroom row carries stored flower");
        }
    }
}
