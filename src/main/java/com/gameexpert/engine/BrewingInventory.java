package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.gameexpert.engine.effect.PotionCatalog;
import com.gameexpert.engine.inventory.BrewingRules;
import com.gameexpert.engine.inventory.ContainerAccess;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * Coordinate-owned five-slot brewing stand inventory and authoritative brew progress.
 *
 * <p>[BREWING-26.3] 칸마다 스택 성분 문자열(WCIC)을 함께 든다 — 범용 물약 {@code CONTENTS_*} 는
 * {@code potionContents} 가 곧 정체성이라 성분을 버리면 병의 내용물이 사라진다. 틱은 바닐라
 * {@code BrewingStandBlockEntity.serverTick} 순서를 그대로 옮긴다(연료 보충이 먼저이고 양조 가능
 * 여부와 무관하다). 드래곤의 숨결처럼 {@code craftRemainder} 가 있는 재료는 재료 칸이 비면 그
 * 칸으로, 아니면 {@link #drainRemainderDrops()} 로 월드에 떨굴 몫으로 남긴다({@code doBrew}).
 */
public final class BrewingInventory implements ContainerAccess {

    public static final int FUEL_SLOT = 0;
    public static final int INGREDIENT_SLOT = 1;
    public static final int FIRST_BOTTLE_SLOT = 2;
    public static final int LAST_BOTTLE_SLOT = 4;
    public static final int SLOTS = 5;

    private final short[] itemTypes = new short[SLOTS];
    private final int[] counts = new int[SLOTS];
    /** [BREWING-26.3] 칸별 WCIC 성분(없으면 null). */
    private final String[] components = new String[SLOTS];
    /** [BREWING-26.3] {@code doBrew} 가 월드에 떨굴 재료 잔여물(드래곤의 숨결 → 유리병) 수. */
    private int pendingRemainderDrops;
    private short pendingRemainderType;
    /** [BREWING-26.3] 아직 알리지 않은 {@code doBrew} 횟수. 저장하지 않는 틱 산출물이다. */
    private int pendingBrewEvents;
    private int fuel;
    private int brewTicks;
    private short brewingIngredient;
    private long persistenceRevision;

    @Override public int slotCount() { return SLOTS; }
    @Override public short itemType(int slot) { return itemTypes[checkedSlot(slot)]; }
    @Override public int count(int slot) { return counts[checkedSlot(slot)]; }
    @Override public int durability(int slot) { checkedSlot(slot); return PlayerInventory.initialDurability(itemTypes[slot]); }
    @Override public long enchantments(int slot) { checkedSlot(slot); return 0; }
    @Override public int mapId(int slot) { checkedSlot(slot); return 0; }
    @Override public int shulkerId(int slot) { checkedSlot(slot); return 0; }
    @Override public String bucketMobData(int slot) { checkedSlot(slot); return null; }
    @Override public String itemComponentData(int slot) { return components[checkedSlot(slot)]; }
    @Override public boolean acceptsShulkerBoxes() { return false; }

    @Override
    public boolean acceptsStack(long enchantments, int mapId, int shulkerId) {
        return enchantments == 0 && mapId == 0 && shulkerId == 0;
    }

    /** 바닐라 양조대는 물약 내용물 · 이름 같은 컴포넌트를 거부하지 않는다. */
    @Override
    public boolean acceptsStack(long enchantments, int mapId, int shulkerId,
            String bucketMobData, String itemComponentData) {
        return acceptsStack(enchantments, mapId, shulkerId) && bucketMobData == null;
    }

    @Override
    public int take(int slot, int amount) {
        checkedSlot(slot);
        if (amount <= 0) return 0;
        int moved = takeWithoutRevision(slot, amount);
        if (moved > 0) advancePersistenceRevision();
        return moved;
    }

    @Override
    public int roomFor(int slot, short type, int durability, long enchantments,
            int mapId, int shulkerId) {
        return roomFor(slot, type, durability, enchantments, mapId, shulkerId, null, null);
    }

    @Override
    public int roomFor(int slot, short type, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        checkedSlot(slot);
        if (durability != PlayerInventory.initialDurability(type)
                || !acceptsStack(enchantments, mapId, shulkerId,
                bucketMobData, itemComponentData) || !acceptsType(slot, type)) return 0;
        try {
            new PlayerInventory.StackSnapshot(type, 1, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
        } catch (IllegalArgumentException invalid) {
            return 0;
        }
        if (itemTypes[slot] != PlayerInventory.EMPTY && (itemTypes[slot] != type
                || !Objects.equals(components[slot], itemComponentData))) return 0;
        return slotMax(slot, type) - counts[slot];
    }

    @Override
    public int put(int slot, short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId) {
        return put(slot, type, amount, durability, enchantments, mapId, shulkerId, null, null);
    }

    @Override
    public int put(int slot, short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        int moved = putWithoutRevision(slot, type, amount, durability, enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData);
        if (moved == 0) return 0;
        advancePersistenceRevision();
        return moved;
    }

    @Override
    public int insert(short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId) {
        return insert(type, amount, durability, enchantments, mapId, shulkerId, null, null);
    }

    @Override
    public int insert(short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        if (amount <= 0) return 0;
        int remaining = amount;
        if (BrewingRules.isFuel(type)) {
            remaining -= putWithoutRevision(FUEL_SLOT, type, remaining, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
        }
        if (remaining > 0 && BrewingRules.isIngredient(type)) {
            remaining -= putWithoutRevision(INGREDIENT_SLOT, type, remaining, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
        }
        if (remaining > 0 && BrewingRules.isBottle(type)) {
            for (int slot = FIRST_BOTTLE_SLOT; slot <= LAST_BOTTLE_SLOT && remaining > 0; slot++) {
                remaining -= putWithoutRevision(slot, type, remaining, durability, enchantments, mapId,
                        shulkerId, bucketMobData, itemComponentData);
            }
        }
        int moved = amount - remaining;
        if (moved > 0) advancePersistenceRevision();
        return moved;
    }

    public int fuel() { return fuel; }
    public int brewTicks() { return brewTicks; }
    public short brewingIngredient() { return brewingIngredient; }
    public long persistenceRevision() { return persistenceRevision; }

    /** Restores one strict current-schema snapshot without stack components (legacy rows). */
    public void restore(short[] savedTypes, int[] savedCounts, int savedFuel,
            int savedBrewTicks, short savedBrewingIngredient) {
        restore(savedTypes, savedCounts, null, savedFuel, savedBrewTicks, savedBrewingIngredient);
    }

    /** Restores one strict current-schema snapshot; {@code savedComponents} may be null (none). */
    public void restore(short[] savedTypes, int[] savedCounts, String[] savedComponents,
            int savedFuel, int savedBrewTicks, short savedBrewingIngredient) {
        if (savedTypes == null || savedCounts == null
                || savedTypes.length != SLOTS || savedCounts.length != SLOTS
                || savedComponents != null && savedComponents.length != SLOTS) {
            throw new IllegalStateException("brewing snapshot must have exactly five slots");
        }
        String[] parts = savedComponents == null ? new String[SLOTS] : savedComponents.clone();
        for (int slot = 0; slot < SLOTS; slot++) {
            short type = savedTypes[slot];
            int amount = savedCounts[slot];
            if (type == PlayerInventory.EMPTY || amount == 0) {
                if (type != PlayerInventory.EMPTY || amount != 0 || parts[slot] != null) {
                    throw new IllegalStateException("invalid empty brewing slot " + slot);
                }
            } else if (!PlayerInventory.isRegisteredItemType(type)
                    || amount < 1 || amount > slotMax(slot, type)
                    || !acceptsStoredType(slot, type)) {
                throw new IllegalStateException("invalid brewing slot " + slot);
            } else if (parts[slot] != null) {
                try {
                    ItemComponentCodec.decode(type, parts[slot]);
                } catch (IllegalArgumentException invalid) {
                    throw new IllegalStateException("invalid brewing slot components " + slot,
                            invalid);
                }
            }
        }
        if (savedFuel < 0 || savedFuel > BrewingRules.BREWS_PER_BLAZE_POWDER
                || savedBrewTicks < 0 || savedBrewTicks > BrewingRules.BREW_TIME_TICKS
                || savedBrewTicks == 0 && savedBrewingIngredient != PlayerInventory.EMPTY
                || savedBrewTicks > 0 && (savedBrewingIngredient == PlayerInventory.EMPTY
                        || savedBrewingIngredient != savedTypes[INGREDIENT_SLOT]
                        || !canBrew(savedBrewingIngredient, savedTypes, parts))) {
            throw new IllegalStateException("invalid brewing progress");
        }
        System.arraycopy(savedTypes, 0, itemTypes, 0, SLOTS);
        System.arraycopy(savedCounts, 0, counts, 0, SLOTS);
        System.arraycopy(parts, 0, components, 0, SLOTS);
        fuel = savedFuel;
        brewTicks = savedBrewTicks;
        brewingIngredient = savedBrewingIngredient;
    }

    public void restorePersistenceRevision(long revision) {
        if (revision < 0 || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("brewing persistence revision must be non-negative");
        }
        if (persistenceRevision != 0 && persistenceRevision != revision) {
            throw new IllegalStateException("brewing revision baseline can only be restored once");
        }
        persistenceRevision = revision;
    }

    /** Returns an independent state copy with the requested persistence generation. */
    public BrewingInventory copyAtPersistenceRevision(long revision) {
        BrewingInventory copy = new BrewingInventory();
        copy.restore(itemTypes, counts, components, fuel, brewTicks, brewingIngredient);
        copy.restorePersistenceRevision(revision);
        return copy;
    }

    public Snapshot snapshot() {
        return new Snapshot(itemTypes, counts, components, fuel, brewTicks, brewingIngredient,
                persistenceRevision);
    }

    public static BrewingInventory fromSnapshot(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("brewing snapshot is required");
        BrewingInventory inventory = new BrewingInventory();
        inventory.restore(snapshot.itemTypes(), snapshot.counts(), snapshot.components(),
                snapshot.fuel(), snapshot.brewTicks(), snapshot.brewingIngredient());
        inventory.restorePersistenceRevision(snapshot.revision());
        return inventory;
    }

    /**
     * Advances one MC tick of {@code BrewingStandBlockEntity.serverTick} and increments the
     * persistence generation exactly once when anything changed.
     *
     * <p>바닐라 순서: ① 연료가 0 이하이고 연료 칸에 {@code BREWING_FUEL} 이 있으면(양조 가능 여부와
     * 무관하게) 20 회로 채우고 한 개를 쓴다 ② {@code isBrewable} ③ 양조 중이면 한 틱 줄이고, 0 이
     * 되었고 여전히 양조 가능하면 {@code doBrew}, 아니면 양조 불가이거나 재료가 바뀌었을 때 멈춘다
     * ④ 양조 중이 아니고 양조 가능하며 연료가 있으면 연료 1 을 쓰고 400 MC 틱을 시작한다.
     * 이 저장소의 불변식: 양조 시간이 0 이면 기억한 재료도 비어 있다.
     */
    public boolean tick() {
        boolean changed = false;
        // 바닐라의 brewing 판정은 연료 보충이 brewTime 을 건드리지 않으므로 보충 전 값과 같다.
        boolean brewing = brewTicks > 0;
        if (fuel <= 0 && BrewingRules.isFuel(itemTypes[FUEL_SLOT])) {
            fuel = BrewingRules.BREWS_PER_BLAZE_POWDER;
            takeWithoutRevision(FUEL_SLOT, 1);
            changed = true;
        }
        short ingredient = itemTypes[INGREDIENT_SLOT];
        boolean brewable = canBrew(ingredient);
        if (brewing) {
            changed = true;
            if (brewTicks > 0) brewTicks--;
            if (brewTicks == 0 && brewable && ingredient == brewingIngredient) {
                doBrew(ingredient);
            } else if (!brewable || ingredient != brewingIngredient) {
                brewTicks = 0;
            }
            if (brewTicks == 0) brewingIngredient = PlayerInventory.EMPTY;
        } else if (brewable && fuel > 0) {
            fuel--;
            brewTicks = BrewingRules.BREW_TIME_TICKS;
            brewingIngredient = ingredient;
            changed = true;
        }
        if (changed) advancePersistenceRevision();
        return changed;
    }

    /**
     * {@code doBrew}: 병 칸마다 지금 재료의 레시피가 있으면 정본 산출 스택(물약 성분만 —
     * {@code output.create()})으로 바꾸고, 재료를 하나 줄인 뒤 {@code craftRemainder} 를 처리한다.
     */
    private void doBrew(short ingredient) {
        pendingBrewEvents++;
        for (int slot = FIRST_BOTTLE_SLOT; slot <= LAST_BOTTLE_SLOT; slot++) {
            PotionCatalog.Contents output = BrewingRules.output(itemTypes[slot], components[slot],
                    ingredient);
            if (output == null) continue;
            itemTypes[slot] = PotionCatalog.canonicalItemType(output.form(), output.key());
            components[slot] = BrewingRules.canonicalComponentData(output);
        }
        short remainder = craftRemainder(ingredient);
        takeWithoutRevision(INGREDIENT_SLOT, 1);
        if (remainder == PlayerInventory.EMPTY) return;
        if (counts[INGREDIENT_SLOT] == 0) {
            itemTypes[INGREDIENT_SLOT] = remainder;
            counts[INGREDIENT_SLOT] = 1;
            components[INGREDIENT_SLOT] = null;
        } else {
            pendingRemainderType = remainder;
            pendingRemainderDrops++;
        }
    }

    /** {@code Item.getCraftingRemainder} 가 있는 양조 재료. 26.3 에서는 드래곤의 숨결 → 유리병뿐이다. */
    public static short craftRemainder(short ingredient) {
        return ingredient == PlayerInventory.DRAGON_BREATH
                ? PlayerInventory.GLASS_BOTTLE : PlayerInventory.EMPTY;
    }

    /** 틱이 월드에 떨굴 잔여물({@code Containers.dropItemStack})을 꺼내고 비운다. */
    public List<StoredStack> drainRemainderDrops() {
        if (pendingRemainderDrops == 0) return List.of();
        List<StoredStack> drops = List.of(new StoredStack(pendingRemainderType,
                pendingRemainderDrops));
        pendingRemainderDrops = 0;
        pendingRemainderType = PlayerInventory.EMPTY;
        return drops;
    }

    /** 틱들이 끝낸 {@code doBrew} 횟수(레벨 이벤트 1035 양조 완료음)를 꺼내고 비운다. */
    public int drainBrewEvents() {
        int events = pendingBrewEvents;
        pendingBrewEvents = 0;
        return events;
    }

    public boolean needsTick() {
        return brewTicks > 0
                || fuel <= 0 && BrewingRules.isFuel(itemTypes[FUEL_SLOT])
                || fuel > 0 && canBrew(itemTypes[INGREDIENT_SLOT]);
    }

    public boolean isEmpty() {
        for (int count : counts) if (count != 0) return false;
        return fuel == 0 && brewTicks == 0;
    }

    public List<StoredStack> drainAll() {
        List<StoredStack> out = new ArrayList<>(SLOTS);
        boolean changed = fuel != 0 || brewTicks != 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (counts[slot] > 0) {
                out.add(new StoredStack(itemTypes[slot], counts[slot], components[slot]));
                changed = true;
            }
            itemTypes[slot] = PlayerInventory.EMPTY;
            counts[slot] = 0;
            components[slot] = null;
        }
        fuel = 0;
        brewTicks = 0;
        brewingIngredient = PlayerInventory.EMPTY;
        if (changed) advancePersistenceRevision();
        return List.copyOf(out);
    }

    public short[] itemTypesSnapshot() { return itemTypes.clone(); }
    public int[] countsSnapshot() { return counts.clone(); }
    public String[] componentsSnapshot() { return components.clone(); }

    /** 이 다섯 칸 구성으로 양조가 되는가({@code isBrewable}). {@code parts} 는 null 이어도 된다. */
    public static boolean canBrew(short ingredient, short[] types, String[] parts) {
        return ingredient != PlayerInventory.EMPTY && BrewingRules.canBrew(ingredient,
                Arrays.copyOfRange(types, FIRST_BOTTLE_SLOT, LAST_BOTTLE_SLOT + 1),
                parts == null ? null
                        : Arrays.copyOfRange(parts, FIRST_BOTTLE_SLOT, LAST_BOTTLE_SLOT + 1));
    }

    private boolean canBrew(short ingredient) {
        return canBrew(ingredient, itemTypes, components);
    }

    /** 병 칸은 바닐라 {@code PotionSlot.getMaxStackSize} 1, 나머지는 아이템 상한. */
    private static int slotMax(int slot, short type) {
        return slot >= FIRST_BOTTLE_SLOT ? 1 : PlayerInventory.stackMax(type);
    }

    /** 메뉴·깔때기가 새로 넣을 수 있는 종류({@code canPlaceItem}). */
    private static boolean acceptsType(int slot, short type) {
        return switch (slot) {
            case FUEL_SLOT -> BrewingRules.isFuel(type);
            case INGREDIENT_SLOT -> BrewingRules.isIngredient(type);
            case FIRST_BOTTLE_SLOT, FIRST_BOTTLE_SLOT + 1, LAST_BOTTLE_SLOT ->
                    BrewingRules.isBottle(type);
            default -> false;
        };
    }

    /** 저장 행이 담을 수 있는 종류. 재료 칸에는 {@code doBrew} 가 넣은 잔여물(유리병)도 있다. */
    private static boolean acceptsStoredType(int slot, short type) {
        return acceptsType(slot, type)
                || slot == INGREDIENT_SLOT && type == PlayerInventory.GLASS_BOTTLE;
    }

    private int takeWithoutRevision(int slot, int amount) {
        int moved = Math.min(counts[slot], amount);
        counts[slot] -= moved;
        if (counts[slot] == 0) {
            itemTypes[slot] = PlayerInventory.EMPTY;
            components[slot] = null;
        }
        if (moved > 0) settleProgress();
        return moved;
    }

    /**
     * 칸이 바뀐 직후의 진행 정리. 바닐라는 다음 {@code serverTick} 에서 양조 불가이거나 재료가
     * 바뀌면 {@code brewTime = 0} 으로 멈춘다 — 저장 행은 그 반쪽 상태를 담지 못하므로 멈춤을
     * 칸 변경과 함께 적용한다(결과는 다음 틱과 같다).
     */
    private void settleProgress() {
        if (brewTicks > 0 && (itemTypes[INGREDIENT_SLOT] != brewingIngredient
                || !canBrew(itemTypes[INGREDIENT_SLOT]))) {
            brewTicks = 0;
            brewingIngredient = PlayerInventory.EMPTY;
        }
    }

    private int putWithoutRevision(int slot, short type, int amount, int durability,
            long enchantments, int mapId, int shulkerId, String bucketMobData,
            String itemComponentData) {
        int moved = Math.min(Math.max(amount, 0), roomFor(slot, type, durability, enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData));
        if (moved > 0) {
            itemTypes[slot] = type;
            components[slot] = itemComponentData;
            counts[slot] += moved;
            settleProgress();
        }
        return moved;
    }

    private static int checkedSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IndexOutOfBoundsException("brewing slot " + slot);
        return slot;
    }

    private void advancePersistenceRevision() {
        persistenceRevision = Math.incrementExact(persistenceRevision);
    }

    /** 한 칸 분량. {@code componentData} 는 WCIC 성분(없으면 null). */
    public record StoredStack(short itemType, int count, String componentData) {
        public StoredStack(short itemType, int count) {
            this(itemType, count, null);
        }
    }

    public record Snapshot(short[] itemTypes, int[] counts, String[] components, int fuel,
            int brewTicks, short brewingIngredient, long revision) {
        public Snapshot(short[] itemTypes, int[] counts, int fuel, int brewTicks,
                short brewingIngredient, long revision) {
            this(itemTypes, counts, null, fuel, brewTicks, brewingIngredient, revision);
        }

        public Snapshot {
            if (itemTypes == null || itemTypes.length != SLOTS
                    || counts == null || counts.length != SLOTS
                    || components != null && components.length != SLOTS
                    || revision < 0 || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid brewing snapshot");
            }
            itemTypes = itemTypes.clone();
            counts = counts.clone();
            components = components == null ? new String[SLOTS] : components.clone();
        }
        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] counts() { return counts.clone(); }
        @Override public String[] components() { return components.clone(); }
    }
}
