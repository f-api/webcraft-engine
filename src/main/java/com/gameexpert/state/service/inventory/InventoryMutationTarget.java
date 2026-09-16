package com.gameexpert.state.service.inventory;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.BrewingInventory;
import com.gameexpert.engine.FurnaceInventory;
import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 플레이어 상태와 같은 커밋에 들어갈 컨테이너 aggregate의 정확한 스냅샷. */
public sealed interface InventoryMutationTarget {

    record Position(int x, int y, int z) implements Comparable<Position> {
        @Override public int compareTo(Position other) {
            int result = Integer.compare(x, other.x);
            if (result == 0) result = Integer.compare(y, other.y);
            return result == 0 ? Integer.compare(z, other.z) : result;
        }
    }

    record ChestHalf(Position position, ChestInventory.Snapshot contents, long revision) {
        public ChestHalf {
            if (position == null || contents == null || revision <= 0
                    || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("chest position, contents and revision are required");
            }
            int length = contents.itemTypes().length;
            // Coordinate container rows: 27-slot chest halves, the 5-slot HopperMenu, the 9-slot
            // DispenserMenu and the one-slot decorated pot filled by use (same persisted domain as
            // WorldChest). Shelves settle through their own three-slot target.
            if (!com.gameexpert.engine.BlockEntityRules.isChestStorageCapacity(length)
                    || length == com.gameexpert.engine.shelf.ShelfRules.SLOTS
                    || contents.counts().length != length
                    || contents.durabilities().length != length
                    || contents.enchantments().length != length
                    || contents.mapIds().length != length
                    || contents.shulkerIds().length != length
                    || contents.bucketMobData().length != length
                    || contents.itemComponentData().length != length) {
                throw new IllegalArgumentException("a chest half snapshot must have eight equal container-capacity arrays");
            }
            contents = new ChestInventory.Snapshot(
                    contents.itemTypes().clone(), contents.counts().clone(),
                    contents.durabilities().clone(), contents.enchantments().clone(),
                    contents.mapIds().clone(), contents.shulkerIds().clone(),
                    contents.bucketMobData().clone(), contents.itemComponentData().clone());
        }

        @Override public ChestInventory.Snapshot contents() {
            return new ChestInventory.Snapshot(
                    contents.itemTypes().clone(), contents.counts().clone(),
                    contents.durabilities().clone(), contents.enchantments().clone(),
                    contents.mapIds().clone(), contents.shulkerIds().clone(),
                    contents.bucketMobData().clone(), contents.itemComponentData().clone());
        }
    }

    record Chests(List<ChestHalf> halves) implements InventoryMutationTarget {
        public Chests {
            if (halves == null || halves.isEmpty() || halves.size() > 2) {
                throw new IllegalArgumentException("a chest mutation needs one or two halves");
            }
            halves = halves.stream().sorted((a, b) -> a.position().compareTo(b.position())).toList();
            if (halves.size() == 2 && halves.get(0).position().equals(halves.get(1).position())) {
                throw new IllegalArgumentException("double chest halves must be distinct");
            }
        }
    }

    record Furnace(Position position, short[] itemTypes, int[] counts,
            int burnTicks, int burnTotalTicks, int cookTicks, int variantCode,
            int xpMilli, long revision, com.gameexpert.engine.inventory.PlayerInventory.StackSnapshot[] stacks)
            implements InventoryMutationTarget {
        public Furnace(Position position, short[] itemTypes, int[] counts,
                int burnTicks, int burnTotalTicks, int cookTicks, int variantCode,
                int xpMilli, long revision) {
            this(position, itemTypes, counts, burnTicks, burnTotalTicks, cookTicks, variantCode,
                    xpMilli, revision, null);
        }

        public Furnace {
            if (position == null || revision <= 0 || revision == Long.MAX_VALUE
                    || itemTypes == null || itemTypes.length != 3
                    || counts == null || counts.length != 3) {
                throw new IllegalArgumentException("invalid furnace snapshot");
            }
            if (xpMilli < 0 || xpMilli > FurnaceInventory.MAX_XP_MILLI) {
                throw new IllegalArgumentException(
                        "furnace xpMilli is outside its non-negative bound");
            }
            itemTypes = itemTypes.clone();
            counts = counts.clone();
            FurnaceInventory validation = new FurnaceInventory(
                    com.gameexpert.engine.FurnaceVariant.fromCode(variantCode));
            FurnaceInventory.Snapshot value = new FurnaceInventory.Snapshot(itemTypes, counts,
                    burnTicks, burnTotalTicks, cookTicks, validation.variant(), revision, xpMilli, stacks);
            validation.restore(value);
            stacks = value.stacks();
        }
        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] counts() { return counts.clone(); }
        @Override public com.gameexpert.engine.inventory.PlayerInventory.StackSnapshot[] stacks() {
            return stacks.clone();
        }
    }

    /**
     * [BREWING-26.3] {@code components} 는 칸별 WCIC 성분(없으면 null)이다 — 범용 물약
     * {@code CONTENTS_*} 의 {@code potionContents} 가 여기에 실린다.
     */
    record Brewing(Position position, short[] itemTypes, int[] counts, String[] components,
            int fuel, int brewTicks, short brewingIngredient, long revision)
            implements InventoryMutationTarget {
        /** 성분 없는 칸만 있는 옛 호출 모양. */
        public Brewing(Position position, short[] itemTypes, int[] counts, int fuel,
                int brewTicks, short brewingIngredient, long revision) {
            this(position, itemTypes, counts, null, fuel, brewTicks, brewingIngredient, revision);
        }

        public Brewing {
            if (position == null || revision <= 0 || revision == Long.MAX_VALUE
                    || itemTypes == null || itemTypes.length != 5
                    || counts == null || counts.length != 5
                    || components != null && components.length != 5) {
                throw new IllegalArgumentException("invalid brewing snapshot");
            }
            itemTypes = itemTypes.clone();
            counts = counts.clone();
            components = components == null ? new String[5] : components.clone();
            BrewingInventory validation = new BrewingInventory();
            validation.restore(itemTypes, counts, components, fuel, brewTicks, brewingIngredient);
        }
        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] counts() { return counts.clone(); }
        @Override public String[] components() { return components.clone(); }
    }

    record Campfire(Position position, short[] itemTypes, int[] cookTicks,
            long revision) implements InventoryMutationTarget {
        public Campfire {
            if (position == null || revision <= 0 || revision == Long.MAX_VALUE
                    || itemTypes == null || itemTypes.length != 4
                    || cookTicks == null || cookTicks.length != 4) {
                throw new IllegalArgumentException("invalid campfire snapshot");
            }
            itemTypes = itemTypes.clone();
            cookTicks = cookTicks.clone();
        }
        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] cookTicks() { return cookTicks.clone(); }
    }

    record MobCargo(MobPersistenceSnapshot mob, long revision) implements InventoryMutationTarget {
        public MobCargo {
            if (mob == null || revision <= 0 || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("mob snapshot and revision are required");
            }
        }
    }

    /** 생성 minecart의 불투명 identity와 분리된 sparse 27칸 cargo projection. */
    record GeneratedChestMinecartCargo(long entityId, long revision, List<ChestItem> cargo)
            implements InventoryMutationTarget {
        public GeneratedChestMinecartCargo {
            if (entityId <= 0 || entityId == Long.MAX_VALUE
                    || revision <= 0 || revision == Long.MAX_VALUE || cargo == null) {
                throw new IllegalArgumentException(
                        "generated chest minecart identity and revision are required");
            }
            cargo = cargo.stream().map(Objects::requireNonNull)
                    .sorted(Comparator.comparingInt(ChestItem::getSlot)).toList();
        }
    }
}
