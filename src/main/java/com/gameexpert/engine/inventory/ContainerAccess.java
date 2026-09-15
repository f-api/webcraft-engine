package com.gameexpert.engine.inventory;

import com.gameexpert.engine.ChestInventory;

/**
 * [CONTAINER-CURSOR] 커서 클릭 규약이 볼 수 있는 "보관 컨테이너" 한 개.
 *
 * <p>상자·큰 상자(두 반쪽)·통·몹 화물은 칸 수와 정체성 허용 범위만 다르고 클릭 규칙은 완전히
 * 같다. {@link PlayerInventory#clickContainer} 가 이 인터페이스만 보게 하면 규칙 사본이 화면
 * 수만큼 생기지 않는다(화로·인챈트가 각자 클릭을 갖게 된 전례를 여기서 끊는다).
 *
 * <p><b>스레딩</b>: 구현은 전부 월드 틱 스레드 소유 상태를 감싸므로 동기화가 없다.
 */
public interface ContainerAccess {

    /** 이 컨테이너의 칸 수. 큰 상자는 두 반쪽의 합, 몹 화물은 개체별 상한이다. */
    int slotCount();

    short itemType(int slot);

    int count(int slot);

    int durability(int slot);

    long enchantments(int slot);

    int mapId(int slot);

    /** [SHULKER-CONTENTS] 그 칸의 셜커 27칸 참조 ID(없으면 0). */
    int shulkerId(int slot);

    /** Captured mob identity for fish/axolotl/tadpole bucket stacks, otherwise {@code null}. */
    String bucketMobData(int slot);

    /** Current WCIC2 encoded item components for the slot, otherwise {@code null}. */
    String itemComponentData(int slot);

    /**
     * [SHULKER-CONTENTS] 이 컨테이너가 셜커 상자 <b>아이템</b>을 받는가.
     *
     * <p>바닐라는 셜커 안에 셜커를 넣지 못한다. 참조 유무가 아니라 아이템 종류로 막으므로
     * <b>빈 상자도</b> 거부된다 — 정적판 {@code acceptsShulkerBoxes: !isShulkerBox(...)} 와
     * 같은 계약이다. 정체성 컬럼 판정({@link #acceptsStack})과는 다른 축이라 따로 둔다.
     */
    boolean acceptsShulkerBoxes();

    /**
     * 이 컨테이너가 그 정체성을 실을 수 있는가. 몹 화물 영속 행은 인챈트·지도 ID 컬럼이 없어
     * 그런 스택을 받으면 조용히 사라진다({@code ChestedHorseRules.cargoAcceptsStack}).
     */
    default boolean acceptsStack(long itemEnchantments, int itemMapId) {
        return acceptsStack(itemEnchantments, itemMapId, 0);
    }

    /** [SHULKER-CONTENTS] 화물 행에는 참조 열도 없으므로 내용을 지닌 상자를 받지 않는다. */
    boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId);

    boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId,
            String itemBucketMobData, String itemComponentData);

    /** 그 칸에서 최대 amount 개를 꺼낸다. 실제로 꺼낸 개수. */
    int take(int slot, int amount);

    /** 그 칸이 같은 정체성의 스택을 몇 개 더 받을 수 있는가(다른 스택이 있으면 0). */
    default int roomFor(
            int slot, short type, int itemDurability, long itemEnchantments, int itemMapId) {
        return roomFor(slot, type, itemDurability, itemEnchantments, itemMapId, 0);
    }

    int roomFor(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId);

    int roomFor(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData);

    /** 그 칸에 amount 개를 넣는다(빈 칸이면 정체성도 함께 심는다). 실제로 넣은 개수. */
    default int put(int slot, short type, int amount,
            int itemDurability, long itemEnchantments, int itemMapId) {
        return put(slot, type, amount, itemDurability, itemEnchantments, itemMapId, 0);
    }

    int put(int slot, short type, int amount,
            int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId);

    int put(int slot, short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData);

    /** 컨테이너 전체에 병합 삽입(Shift 빠른 이동). 실제로 넣은 개수. */
    default int insert(
            short type, int amount, int itemDurability, long itemEnchantments, int itemMapId) {
        return insert(type, amount, itemDurability, itemEnchantments, itemMapId, 0);
    }

    int insert(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId);

    int insert(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData);

    /** 좌표 상자·통 한 칸짜리 시야. 정체성 제한이 없다. */
    static ContainerAccess of(ChestInventory inventory) {
        return new SingleContainerAccess(inventory, inventory.slots(), true, true);
    }

    /**
     * [SHULKER-CONTENTS] 놓여 있는 셜커 상자의 27칸 시야. 저장소·클릭 규칙은 상자와 완전히
     * 같고 <b>셜커 상자 아이템만</b> 받지 않는다(바닐라 셜커-인-셜커 금지).
     */
    static ContainerAccess ofShulker(ChestInventory inventory) {
        return new SingleContainerAccess(inventory, inventory.slots(), true, false);
    }

    /**
     * 몹 화물 시야. 칸 수는 개체 스탯이 정하고(라마 3~15칸) 인챈트·지도 스택은 받지 않는다.
     */
    static ContainerAccess ofCargo(ChestInventory inventory, int slots) {
        return new SingleContainerAccess(
                inventory, Math.min(slots, inventory.slots()), false, true);
    }

    /**
     * [CONTAINER-MENUS] The nine {@code CrafterSlot}s of a {@code CrafterMenu}. Storage and click
     * rules are the chest's; a slot whose bit is set in {@code disabledSlots} takes nothing
     * ({@code CrafterSlot.mayPlace = !menu.isSlotDisabled(index)}).
     */
    static ContainerAccess ofCrafter(ChestInventory inventory, int disabledSlots) {
        return new SingleContainerAccess(inventory, inventory.slots(), true, true, disabledSlots);
    }

    /** 큰 상자. 앞 반쪽의 칸이 먼저 오고 그 다음이 뒤 반쪽이다(바닐라 6줄 순서). */
    static ContainerAccess ofDouble(ChestInventory first, ChestInventory second) {
        return second == null ? of(first) : new DoubleContainerAccess(of(first), of(second));
    }
}
