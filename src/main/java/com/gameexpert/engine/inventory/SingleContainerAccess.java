package com.gameexpert.engine.inventory;

import com.gameexpert.engine.ChestInventory;

/**
 * [CONTAINER-CURSOR] {@link ChestInventory} 한 개를 커서 규약이 보는 컨테이너로 감싼다.
 *
 * <p>{@code slots} 는 개체 상한이 배열 크기보다 작을 수 있는 몹 화물 때문에 따로 받는다
 * (라마는 힘에 따라 3~15칸만 쓴다). {@code carriesIdentity} 가 false 면 인챈트·지도 스택을
 * 받지 않는다 — 화물 영속 행에 그 컬럼이 없어 조용히 사라지기 때문이다.
 */
final class SingleContainerAccess implements ContainerAccess {

    private final ChestInventory inventory;
    private final int slots;
    private final boolean carriesIdentity;
    /** [SHULKER-CONTENTS] 셜커 상자 아이템을 받는가. 놓인 셜커의 27칸만 false 다. */
    private final boolean acceptsShulkerBoxes;
    /**
     * [CONTAINER-MENUS] {@code CrafterSlot.mayPlace}: a disabled crafter slot (bit {@code i} of
     * this mask) takes nothing, by click, drag or quick move. 0 for every other container.
     */
    private final int disabledSlots;

    SingleContainerAccess(ChestInventory inventory, int slots, boolean carriesIdentity,
            boolean acceptsShulkerBoxes) {
        this(inventory, slots, carriesIdentity, acceptsShulkerBoxes, 0);
    }

    SingleContainerAccess(ChestInventory inventory, int slots, boolean carriesIdentity,
            boolean acceptsShulkerBoxes, int disabledSlots) {
        this.inventory = inventory;
        this.slots = Math.max(0, slots);
        this.carriesIdentity = carriesIdentity;
        this.acceptsShulkerBoxes = acceptsShulkerBoxes;
        this.disabledSlots = disabledSlots;
    }

    private boolean outside(int slot) {
        return slot < 0 || slot >= slots;
    }

    private boolean disabled(int slot) {
        return slot >= 0 && slot < 32 && (disabledSlots >>> slot & 1) != 0;
    }

    @Override
    public int slotCount() {
        return slots;
    }

    @Override
    public short itemType(int slot) {
        return outside(slot) ? PlayerInventory.EMPTY : inventory.itemType(slot);
    }

    @Override
    public int count(int slot) {
        return outside(slot) ? 0 : inventory.count(slot);
    }

    @Override
    public int durability(int slot) {
        return outside(slot) ? 0 : inventory.durability(slot);
    }

    @Override
    public long enchantments(int slot) {
        return outside(slot) ? 0 : inventory.enchantments(slot);
    }

    @Override
    public int mapId(int slot) {
        return outside(slot) ? 0 : inventory.mapId(slot);
    }

    @Override
    public int shulkerId(int slot) {
        return outside(slot) ? 0 : inventory.shulkerId(slot);
    }

    @Override
    public String bucketMobData(int slot) {
        return outside(slot) ? null : inventory.bucketMobData(slot);
    }

    @Override
    public String itemComponentData(int slot) {
        return outside(slot) ? null : inventory.itemComponentData(slot);
    }

    @Override
    public boolean acceptsShulkerBoxes() {
        return acceptsShulkerBoxes;
    }

    @Override
    public boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId) {
        return carriesIdentity
                || (itemEnchantments == 0 && itemMapId == 0 && itemShulkerId == 0);
    }

    @Override
    public int take(int slot, int amount) {
        return outside(slot) ? 0 : inventory.take(slot, amount);
    }

    @Override
    public int roomFor(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return roomFor(slot, type, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                null, null);
    }

    @Override
    public int roomFor(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        if (outside(slot) || disabled(slot) || !accepts(type, itemEnchantments, itemMapId,
                itemShulkerId, itemBucketMobData, itemComponentData)) return 0;
        return inventory.roomForSlot(
                slot, type, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, itemComponentData);
    }

    @Override
    public int put(int slot, short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return put(slot, type, amount, itemDurability, itemEnchantments, itemMapId,
                itemShulkerId, null, null);
    }

    @Override
    public int put(int slot, short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        if (outside(slot) || disabled(slot) || !accepts(type, itemEnchantments, itemMapId,
                itemShulkerId, itemBucketMobData, itemComponentData)) return 0;
        return inventory.putInSlot(
                slot, type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, itemComponentData);
    }

    @Override
    public int insert(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return insert(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                null, null);
    }

    @Override
    public int insert(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        if (amount <= 0 || !accepts(type, itemEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, itemComponentData)) return 0;
        int remaining = amount;
        // 같은 스택부터 채우고 남으면 빈 칸(바닐라 Shift 이동 순서). 개체 상한 밖 칸은 건드리지 않는다.
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (inventory.itemType(slot) == PlayerInventory.EMPTY || disabled(slot)) continue;
            remaining -= inventory.putInSlot(slot, type, remaining, itemDurability,
                    itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData,
                    itemComponentData);
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (inventory.itemType(slot) != PlayerInventory.EMPTY || disabled(slot)) continue;
            remaining -= inventory.putInSlot(slot, type, remaining, itemDurability,
                    itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData,
                    itemComponentData);
        }
        return amount - remaining;
    }

    /**
     * [SHULKER-CONTENTS] 정체성 컬럼 판정과 셜커-인-셜커 금지를 한 관문으로 묶는다. 두 축을
     * 호출부마다 따로 물으면 한 곳이 빠졌을 때 그 경로로만 상자가 들어간다.
     */
    private boolean accepts(
            short type, long itemEnchantments, int itemMapId, int itemShulkerId,
            String itemBucketMobData, String itemComponentData) {
        return acceptsStack(itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData,
                itemComponentData)
                && (acceptsShulkerBoxes || !PlayerInventory.isShulkerBox(type));
    }

    @Override
    public boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId,
            String itemBucketMobData, String itemComponentData) {
        return carriesIdentity
                || (itemEnchantments == 0 && itemMapId == 0 && itemShulkerId == 0
                        && itemBucketMobData == null && itemComponentData == null);
    }
}
