package com.gameexpert.engine.inventory;

/**
 * [CONTAINER-CURSOR] 큰 상자. 앞 반쪽의 칸이 먼저 오고 그 뒤에 뒤 반쪽이 붙는다
 * (바닐라 {@code DoubleBlockCombiner} 가 정한 6줄 순서를 상위에서 이미 결정해 넘긴다).
 */
final class DoubleContainerAccess implements ContainerAccess {

    private final ContainerAccess first;
    private final ContainerAccess second;

    DoubleContainerAccess(ContainerAccess first, ContainerAccess second) {
        this.first = first;
        this.second = second;
    }

    private ContainerAccess half(int slot) {
        return slot < first.slotCount() ? first : second;
    }

    private int local(int slot) {
        return slot < first.slotCount() ? slot : slot - first.slotCount();
    }

    @Override
    public int slotCount() {
        return first.slotCount() + second.slotCount();
    }

    @Override
    public short itemType(int slot) {
        return half(slot).itemType(local(slot));
    }

    @Override
    public int count(int slot) {
        return half(slot).count(local(slot));
    }

    @Override
    public int durability(int slot) {
        return half(slot).durability(local(slot));
    }

    @Override
    public long enchantments(int slot) {
        return half(slot).enchantments(local(slot));
    }

    @Override
    public int mapId(int slot) {
        return half(slot).mapId(local(slot));
    }

    @Override
    public int shulkerId(int slot) {
        return half(slot).shulkerId(local(slot));
    }

    @Override
    public String bucketMobData(int slot) {
        return half(slot).bucketMobData(local(slot));
    }

    @Override
    public String itemComponentData(int slot) {
        return half(slot).itemComponentData(local(slot));
    }

    @Override
    public boolean acceptsShulkerBoxes() {
        return first.acceptsShulkerBoxes();
    }

    @Override
    public boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId) {
        return first.acceptsStack(itemEnchantments, itemMapId, itemShulkerId);
    }

    @Override
    public int take(int slot, int amount) {
        return half(slot).take(local(slot), amount);
    }

    @Override
    public int roomFor(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return half(slot).roomFor(local(slot), type, itemDurability, itemEnchantments,
                itemMapId, itemShulkerId);
    }

    @Override
    public int roomFor(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        return half(slot).roomFor(local(slot), type, itemDurability, itemEnchantments,
                itemMapId, itemShulkerId, itemBucketMobData, itemComponentData);
    }

    @Override
    public int put(int slot, short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return half(slot).put(local(slot), type, amount, itemDurability, itemEnchantments,
                itemMapId, itemShulkerId);
    }

    @Override
    public int put(int slot, short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        return half(slot).put(local(slot), type, amount, itemDurability, itemEnchantments,
                itemMapId, itemShulkerId, itemBucketMobData, itemComponentData);
    }

    @Override
    public int insert(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        int inserted = first.insert(
                type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId);
        if (inserted >= amount) return inserted;
        return inserted + second.insert(type, amount - inserted, itemDurability,
                itemEnchantments, itemMapId, itemShulkerId);
    }

    @Override
    public int insert(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        int inserted = first.insert(type, amount, itemDurability, itemEnchantments, itemMapId,
                itemShulkerId, itemBucketMobData, itemComponentData);
        if (inserted >= amount) return inserted;
        return inserted + second.insert(type, amount - inserted, itemDurability,
                itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData,
                itemComponentData);
    }

    @Override
    public boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId,
            String itemBucketMobData, String itemComponentData) {
        return first.acceptsStack(itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData,
                itemComponentData);
    }
}
