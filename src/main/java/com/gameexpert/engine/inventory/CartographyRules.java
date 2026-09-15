package com.gameexpert.engine.inventory;

/** Pure cartography input classification; map metadata mutation remains owned by map persistence. */
public final class CartographyRules {
    public enum Operation { EXPAND, CLONE, LOCK }
    public static final int MAX_SCALE = 4;
    private CartographyRules() {}

    public static final class Plan {
        private final Operation operation;
        private final PlayerInventory.StackSnapshot result;
        private final int scale;
        private final boolean locked;
        private Plan(Operation operation, PlayerInventory.StackSnapshot result,
                int scale, boolean locked) {
            this.operation = operation;
            this.result = result;
            this.scale = scale;
            this.locked = locked;
        }
        public Operation operation() { return operation; }
        public PlayerInventory.StackSnapshot result() { return result; }
        public int scale() { return scale; }
        public boolean locked() { return locked; }
    }

    public static Operation operation(PlayerInventory.StackSnapshot map,
            PlayerInventory.StackSnapshot addition) {
        // 지도 칸은 바닐라와 같이 **스택 그대로** 받는다. 공식 `CartographyTableMenu.slotsChanged`
        // 는 두 입력이 비어 있지 않기만 요구하고, 산출은 한 장을 복사해 만든다(수령 시 지도 1 +
        // 재료 1 만 소모되고 나머지 스택은 그대로 남는다). 개수 1 을 강제하면 정상 입력이 거부된다.
        if (map == null || !PlayerInventory.isFilledMapItem(map.itemType()) || map.count() <= 0
                || addition == null || addition.count() <= 0) return null;
        if (addition.itemType() == PlayerInventory.PAPER)
            return PlayerInventory.isExplorerMap(map.itemType()) ? null : Operation.EXPAND;
        if (addition.itemType() == PlayerInventory.MAP) return Operation.CLONE;
        if (addition.itemType() == (short) com.gameexpert.terrain.Blocks.GLASS_PANE)
            return Operation.LOCK;
        return null;
    }

    /** Verifies one consumed item while retaining every component of a remaining stack. */
    public static boolean consumedOne(PlayerInventory.StackSnapshot before,
            PlayerInventory.StackSnapshot after) {
        if (before == null || after == null || before.isEmpty()) return false;
        if (before.count() == 1) return after.isEmpty();
        return new PlayerInventory.StackSnapshot(before.itemType(), before.count() - 1,
                before.durability(), before.enchantments(), before.mapId(), before.shulkerId(),
                before.bucketMobData(), before.itemComponentData()).equals(after);
    }

    /** Plans stack output and the map-row metadata which must commit in the same transaction. */
    public static Plan plan(PlayerInventory.StackSnapshot map,
            PlayerInventory.StackSnapshot addition, int currentScale, boolean currentlyLocked,
            int allocatedMapId) {
        if (currentScale < 0 || currentScale > MAX_SCALE) return null;
        Operation operation = operation(map, addition);
        if (operation == null) return null;
        if (operation == Operation.EXPAND) {
            if (currentlyLocked || currentScale == MAX_SCALE || allocatedMapId <= 0) return null;
            return new Plan(operation, filledMap(map, allocatedMapId, 1), currentScale + 1, false);
        }
        if (operation == Operation.LOCK) {
            if (currentlyLocked || allocatedMapId <= 0) return null;
            return new Plan(operation, filledMap(map, allocatedMapId, 1), currentScale, true);
        }
        return new Plan(operation, filledMap(map, map.mapId(), 2), currentScale, currentlyLocked);
    }

    private static PlayerInventory.StackSnapshot filledMap(
            PlayerInventory.StackSnapshot source, int mapId, int count) {
        return new PlayerInventory.StackSnapshot(source.itemType(), count, 0,
                source.enchantments(), mapId, 0, null, source.itemComponentData());
    }
}
