package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * 서버 권위 블록 상호작용 검증(순수 함수). 틱 루프가 이동 반영 뒤의 플레이어 위치로 호출합니다.
 *
 * 문·울타리 문·트랩도어의 재질 ID는 바꾸지 않고 state의 open bit만 토글합니다.
 * 리치는 블록 편집과 같은 바닐라 블록 AABB 판정을 사용합니다.
 */
public final class InteractRules {

    private InteractRules() {
    }

    /** 2칸 목재 문의 단일 재질 ID(29)인가. */
    public static boolean isDoor(int id) {
        return Blocks.isDoor(id);
    }

    /** 침대 블록(총칭 32 · 색 851~866)인가. 판정 정본은 {@link Blocks#isBed(int)} 하나다. */
    public static boolean isBed(int id) {
        return Blocks.isBed(id);
    }

    /**
     * 위치 키 보관함(상자·통)인가. 바닐라 barrel 은 단일 상자와 같은 27칸 컨테이너이고
     * WebCraft 의 보관 저장소는 좌표로만 키를 잡으므로 블록 종류 판정 하나만 넓히면 된다.
     *
     * <p>모양은 공유하지 않는다: 바닐라 상자는 14/16 인셋 박스지만 barrel 은 풀 큐브라
     * {@code BuildingBlockRules} 의 상자 전용 형상 분기에는 넣지 않는다.
     */
    public static boolean isContainer(int id) {
        // [CHEST-FAMILY] 덫 상자·구리 상자도 같은 27칸 좌표 키 저장소를 쓴다.
        // [SHULKER-CONTENTS] 셜커 상자 17종의 봉인 해제. 놓여 있는 동안 27칸은 다른 상자와
        // 똑같이 좌표 키 저장소가 소유하고, 아이템으로 옮겨 갈 때만 `shulker_contents` 참조
        // 행이 된다. 짝은 이루지 않는다 — `isChestShaped` 밖이라 `openChestAccess` 가 언제나
        // 단일 컨테이너 갈래로 뺀다(통 BARREL 과 같은 이유).
        //
        // 이 줄은 정적판 `StandaloneBlockRules.isStandaloneContainerBlock` 의 같은 줄과
        // **동시에** 풀렸다. 한쪽만 풀면 두 분포의 플레이어 가시 동작이 갈린다(AGENTS 6).
        // [HOPPER] HopperBlock#useWithoutItem opens the five-slot HopperMenu (no sound, no
        // piglin anger). Released together with the standalone container set.
        // [CONTAINER-MENUS] DropperBlock opens the same nine-slot DispenserMenu; CrafterBlock
        // opens the nine-slot CrafterMenu over the same coordinate lane.
        return Blocks.isChestShaped(id) || id == Blocks.BARREL || Blocks.isShulkerBox(id)
                || id == Blocks.DISPENSER || Blocks.isDecoratedPot(id) || id == Blocks.HOPPER
                || id == Blocks.DROPPER || id == Blocks.CRAFTER;
    }

    /**
     * Coordinate containers whose use opens a container menu. {@code DecoratedPotBlock}
     * never opens one: {@code useItemOn} inserts the held stack and {@code useWithoutItem}
     * wobbles with {@code decorated_pot.insert_fail} (javap 26.3-snapshot-7). The standalone twin
     * is {@code StandaloneBlockRules.standaloneOpensContainerMenu}.
     */
    public static boolean opensContainerMenu(int id) {
        return isContainer(id) && !Blocks.isDecoratedPot(id);
    }

    /**
     * Whether opening/closing this container menu uses the shared {@code chest_open}/
     * {@code chest_close} lane. The barrel plays its own {@code barrel_open/close} from its
     * opener counter; {@code HopperBlock}, {@code DispenserBlock} (dispenser and dropper) and
     * {@code CrafterBlock} only open the menu ({@code useWithoutItem}: {@code openMenu} plus an
     * inspect stat, no sound and no {@code PiglinAi.angerNearbyPiglins}; their block entities have
     * no opener counter), javap 26.3-snapshot-7. The standalone twin is
     * {@code StandaloneBlockRules.standaloneContainerUsesChestSound}.
     */
    public static boolean containerUsesChestSound(int id) {
        return opensContainerMenu(id) && id != Blocks.BARREL && !isSilentContainerMenu(id);
    }

    /** Menus opened without any container sound or piglin anger (see above). */
    public static boolean isSilentContainerMenu(int id) {
        return id == Blocks.HOPPER || id == Blocks.DISPENSER || id == Blocks.DROPPER
                || id == Blocks.CRAFTER;
    }

    public static boolean isOpenable(int id) {
        return isDoor(id) || Blocks.isFenceGate(id) || Blocks.isTrapdoor(id);
    }

    /** 재질 ID를 유지한 채 해당 블록의 open state bit를 반전한다. */
    public static int toggledState(int id, int state) {
        return BuildingBlockRules.toggleOpen(id, state);
    }

    public static boolean withinReach(double px, double py, double pz, boolean crouching,
            int x, int y, int z) {
        return PlayerInteractionRules.canInteractWithBlock(px, py, pz, crouching, x, y, z);
    }

    public static boolean withinContainerReach(double px, double py, double pz, boolean crouching,
            int x, int y, int z) {
        return PlayerInteractionRules.canUseContainer(px, py, pz, crouching, x, y, z);
    }
}
