package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.mob.BucketMobPayloadCodec;
import java.util.Arrays;

/**
 * 상자 한 개의 보관 칸(27칸). 플레이어 인벤토리와 같은 배열 구조를 쓰되, 상자는
 * <b>선택 슬롯·방어구·내구도 마모</b> 개념이 없으므로 훨씬 단순합니다.
 *
 * <p><b>스레딩</b>: 상자 내용은 월드 틱 스레드가 소유하지만, 저장 스레드가 읽는
 * {@link #persistenceSnapshot()} 과 모든 변이는 같은 모니터로 직렬화합니다. WS 스레드는 액션
 * 큐로만 요청하므로 게임 변이는 여전히 월드 틱 스레드에서 수행합니다.
 *
 * <p><b>내구도</b>: 종류·개수와 나란히 슬롯별 내구도를 보관합니다. 내구 아이템은 스택이 1이므로
 * 한 슬롯의 값 하나만 옮기면 되고, 일반 아이템은 0(내구 없음)으로 둡니다.
 */
public final class ChestInventory {

    /** 상자 칸 수(MC 일반 상자와 동일). 블록 컨테이너의 기본 크기다. */
    public static final int SLOTS = 27;
    public static final int DOUBLE_SLOTS = SLOTS * 2;

    /**
     * 이 인벤토리의 실제 칸 수. 블록 상자·통은 {@link #SLOTS} 이고, 상자를 단 말 계열 화물처럼
     * 칸 수가 종·개체 스탯에서 오는 컨테이너는 생성자로 자기 크기를 준다
     * ({@code ChestedHorseRules.cargoSlots}). 슬롯 병합·이동·드랍 규칙은 크기와 무관하게 같다.
     */
    private final int slots;
    /** Whole placed-pot item components; null preserves legacy componentless containers. */
    private String potItemComponents;

    public synchronized String potItemComponents() { return potItemComponents; }

    public synchronized void restorePotItemComponents(String components) {
        requireRestoreOpen();
        validatePotItemComponents(components);
        potItemComponents = components;
    }

    public synchronized void setPotItemComponents(String components) {
        validatePotItemComponents(components);
        if (java.util.Objects.equals(potItemComponents, components)) return;
        markBlockEntityMetadataChanged();
        potItemComponents = components;
    }

    private void validatePotItemComponents(String components) {
        if (components == null) return;
        if (slots != 1) throw new IllegalArgumentException("pot metadata requires one slot");
        ItemComponentCodec.decode((short) com.gameexpert.terrain.Blocks.DECORATED_POT, components);
    }

    private final short[] itemType;
    private final int[] count;
    private final int[] durability;
    /** [SURV-X] 슬롯별 인챈트 압축 마스크. 마스크가 다른 스택은 절대 합쳐지지 않는다. */
    private final long[] enchantments;
    /** 채워진 지도만 가지는 월드 지도 ID. */
    private final int[] mapIds;
    /**
     * [SHULKER-CONTENTS] 셜커 상자 스택만 가지는 27칸 참조 ID. 그 밖의 칸은 0이다.
     *
     * <p>이 컨테이너가 <b>놓인 셜커</b>의 27칸일 때도 값은 여기 앉지 않는다 — 놓여 있는 동안
     * 27칸은 좌표가 소유하므로 참조 저장소에는 행이 없다. 여기 담기는 참조는 "이 상자 안에
     * 들어 있는 다른(아이템 상태의) 셜커 상자"의 것이다.
     */
    private final int[] shulkerIds;
    private final String[] bucketMobData;
    /** WCIC2 current-schema item component payload, or {@code null}. */
    private final String[] itemComponentData;
    private long persistenceRevision;
    private RestoreState restoreState = RestoreState.OPEN;

    private enum RestoreState {
        OPEN,
        BASELINE_BOUND,
        TERMINAL
    }

    public synchronized long persistenceRevision() {
        return persistenceRevision;
    }

    /** DB 로드 전용. 실제 변경은 이 기준선의 정확한 다음 세대부터 시작한다. */
    public synchronized void restorePersistenceRevision(long persistedRevision) {
        if (persistedRevision < 0) {
            throw new IllegalArgumentException("chest persistence revision must be non-negative");
        }
        if (restoreState != RestoreState.OPEN) {
            throw new IllegalStateException("chest revision baseline can only be restored once");
        }
        persistenceRevision = persistedRevision;
        restoreState = persistedRevision == Long.MAX_VALUE
                ? RestoreState.TERMINAL : RestoreState.BASELINE_BOUND;
    }

    /** Coordinate-owned block entity metadata uses the same durable row revision as its items. */
    synchronized void markBlockEntityMetadataChanged() {
        preflightRevisionCapacity();
        advancePersistenceRevision();
    }

    /** One hopper move, including neighbour rejection, retains its exact source baseline. */
    public synchronized void runAtomicTransfer(Runnable transfer) {
        ChestInventory before = detachedInventory();
        RestoreState beforeRestore = restoreState;
        try {
            transfer.run();
        } catch (RuntimeException | Error failure) {
            System.arraycopy(before.itemType, 0, itemType, 0, slots);
            System.arraycopy(before.count, 0, count, 0, slots);
            System.arraycopy(before.durability, 0, durability, 0, slots);
            System.arraycopy(before.enchantments, 0, enchantments, 0, slots);
            System.arraycopy(before.mapIds, 0, mapIds, 0, slots);
            System.arraycopy(before.shulkerIds, 0, shulkerIds, 0, slots);
            System.arraycopy(before.bucketMobData, 0, bucketMobData, 0, slots);
            System.arraycopy(before.itemComponentData, 0, itemComponentData, 0, slots);
            persistenceRevision = before.persistenceRevision;
            potItemComponents = before.potItemComponents;
            restoreState = beforeRestore;
            throw failure;
        }
    }

    private void preflightRevisionCapacity() {
        if (restoreState == RestoreState.TERMINAL || persistenceRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("chest persistence revision is exhausted");
        }
    }

    private void requireRestoreOpen() {
        if (restoreState != RestoreState.OPEN || persistenceRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("chest restore lifecycle is closed");
        }
    }

    private synchronized void advancePersistenceRevision() {
        persistenceRevision = Math.incrementExact(persistenceRevision);
    }

    /** 블록 컨테이너 기본 크기(27칸). */
    public ChestInventory() {
        this(SLOTS);
    }

    /** 칸 수를 호출부가 정하는 컨테이너(말 계열 화물). */
    public ChestInventory(int slots) {
        if (slots <= 0) throw new IllegalArgumentException("컨테이너 칸 수가 올바르지 않습니다: " + slots);
        this.slots = slots;
        this.itemType = new short[slots];
        this.count = new int[slots];
        this.durability = new int[slots];
        this.enchantments = new long[slots];
        this.mapIds = new int[slots];
        this.shulkerIds = new int[slots];
        this.bucketMobData = new String[slots];
        this.itemComponentData = new String[slots];
    }

    /** 이 인벤토리의 칸 수. */
    public synchronized int slots() {
        return slots;
    }

    /**
     * [ENDER-SHULKER] 영속 스레드로 넘길 값 사본.
     *
     * <p>이 컨테이너는 틱 스레드 소유인데 저장은 다른 스레드에서 커밋되므로, 배열을
     * <b>복사</b>해 넘긴다(참조를 넘기면 커밋 도중 틱이 내용을 바꿔 반쯤 옛 값이 저장된다).
     * 플레이어 인벤토리의 {@code PlayerInventory.PersistenceSnapshot} 과 같은 계약이다.
     */
    public synchronized Snapshot snapshot() {
        return new Snapshot(itemType.clone(), count.clone(), durability.clone(),
                enchantments.clone(), mapIds.clone(), shulkerIds.clone(), bucketMobData.clone(),
                itemComponentData.clone());
    }

    /**
     * Captures every persisted slot column and its revision under one monitor acquisition.
     *
     * <p>The revision is read before the canonical snapshot constructor runs so even a test hook
     * or a future callback cannot pair a post-mutation revision with pre-mutation slot arrays.
     */
    public synchronized PersistenceSnapshot persistenceSnapshot() {
        long capturedRevision = persistenceRevision;
        Snapshot captured = new Snapshot(itemType, count, durability, enchantments, mapIds,
                shulkerIds, bucketMobData, itemComponentData);
        return new PersistenceSnapshot(captured, capturedRevision, potItemComponents);
    }

    /** Returns a mutable detached inventory rebuilt from one atomic contents/revision capture. */
    public synchronized ChestInventory detachedInventory() {
        return persistenceSnapshot().detachedInventory();
    }

    /** Load boundary only: clears every slot without creating a gameplay persistence revision. */
    public synchronized void clearForRestore() {
        requireRestoreOpen();
        java.util.Arrays.fill(itemType, PlayerInventory.EMPTY);
        java.util.Arrays.fill(count, 0);
        java.util.Arrays.fill(durability, 0);
        java.util.Arrays.fill(enchantments, 0);
        java.util.Arrays.fill(mapIds, 0);
        java.util.Arrays.fill(shulkerIds, 0);
        java.util.Arrays.fill(bucketMobData, null);
        java.util.Arrays.fill(itemComponentData, null);
        persistenceRevision = 0;
        potItemComponents = null;
    }

    /** Immutable contents plus the exact persistence generation observed with those contents. */
    public static final class PersistenceSnapshot {
        private final Snapshot snapshot;
        private final long revision;
        private final String potItemComponents;

        private PersistenceSnapshot(Snapshot snapshot, long revision, String potItemComponents) {
            this.snapshot = snapshot;
            this.revision = revision;
            this.potItemComponents = potItemComponents;
        }

        public String potItemComponents() { return potItemComponents; }

        public Snapshot snapshot() { return snapshot; }
        public Snapshot contents() { return snapshot; }
        public long revision() { return revision; }
        public int slots() { return snapshot.itemTypes().length; }

        /** Returns a mutable detached inventory with this snapshot's contents and revision. */
        public ChestInventory detachedInventory() {
            return ChestInventory.detachedInventory(this);
        }

        public short[] itemTypes() { return snapshot.itemTypes(); }
        public int[] counts() { return snapshot.counts(); }
        public int[] durabilities() { return snapshot.durabilities(); }
        public long[] enchantments() { return snapshot.enchantments(); }
        public int[] mapIds() { return snapshot.mapIds(); }
        public int[] shulkerIds() { return snapshot.shulkerIds(); }
        public String[] bucketMobData() { return snapshot.bucketMobData(); }
        public String[] itemComponentData() { return snapshot.itemComponentData(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PersistenceSnapshot that)) return false;
            return revision == that.revision && snapshot.equals(that.snapshot)
                    && java.util.Objects.equals(potItemComponents, that.potItemComponents);
        }

        @Override
        public int hashCode() {
            int legacy = 31 * snapshot.hashCode() + Long.hashCode(revision);
            return potItemComponents == null ? legacy : 31 * legacy + potItemComponents.hashCode();
        }

        @Override
        public String toString() {
            return "PersistenceSnapshot{revision=" + revision + ", contents=" + snapshot + "}";
        }
    }

    private static ChestInventory detachedInventory(PersistenceSnapshot source) {
        Snapshot contents = source.snapshot;
        ChestInventory copy = new ChestInventory(contents.itemTypes.length);
        System.arraycopy(contents.itemTypes, 0, copy.itemType, 0, copy.slots);
        System.arraycopy(contents.counts, 0, copy.count, 0, copy.slots);
        System.arraycopy(contents.durabilities, 0, copy.durability, 0, copy.slots);
        System.arraycopy(contents.enchantments, 0, copy.enchantments, 0, copy.slots);
        System.arraycopy(contents.mapIds, 0, copy.mapIds, 0, copy.slots);
        System.arraycopy(contents.shulkerIds, 0, copy.shulkerIds, 0, copy.slots);
        System.arraycopy(contents.bucketMobData, 0, copy.bucketMobData, 0, copy.slots);
        System.arraycopy(contents.itemComponentData, 0, copy.itemComponentData, 0, copy.slots);
        copy.potItemComponents = source.potItemComponents;
        copy.persistenceRevision = source.revision;
        copy.restoreState = source.revision == Long.MAX_VALUE
                ? RestoreState.TERMINAL : RestoreState.BASELINE_BOUND;
        return copy;
    }

    /** {@link #snapshot()} 이 내는 값 사본. 길이는 모두 그 컨테이너의 칸 수와 같다. */
    public record Snapshot(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData) {
        /** Test-only seam for proving source mutation happens after ownership is established. */
        private static final ThreadLocal<Runnable> AFTER_CLONE_HOOK = new ThreadLocal<>();

        public Snapshot {
            short[] ownedItemTypes = itemTypes == null ? null : itemTypes.clone();
            int[] ownedCounts = counts == null ? null : counts.clone();
            int[] ownedDurabilities = durabilities == null ? null : durabilities.clone();
            long[] ownedEnchantments = enchantments == null ? null : enchantments.clone();
            int[] ownedMapIds = mapIds == null ? null : mapIds.clone();
            int[] ownedShulkerIds = shulkerIds == null ? null : shulkerIds.clone();
            String[] ownedBucketMobData = bucketMobData == null ? null : bucketMobData.clone();
            String[] ownedItemComponentData = itemComponentData == null
                    ? null : itemComponentData.clone();
            Runnable afterCloneHook = AFTER_CLONE_HOOK.get();
            if (afterCloneHook != null) afterCloneHook.run();

            if (ownedItemTypes == null || ownedCounts == null || ownedDurabilities == null
                    || ownedEnchantments == null || ownedMapIds == null || ownedShulkerIds == null
                    || ownedBucketMobData == null || ownedItemComponentData == null) {
                throw new IllegalArgumentException("chest snapshot arrays are required");
            }
            int length = ownedItemTypes.length;
            if (ownedCounts.length != length || ownedDurabilities.length != length
                    || ownedEnchantments.length != length || ownedMapIds.length != length
                    || ownedShulkerIds.length != length || ownedBucketMobData.length != length
                    || ownedItemComponentData.length != length) {
                throw new IllegalArgumentException("chest snapshot arrays must have equal lengths");
            }
            for (int slot = 0; slot < length; slot++) {
                new PlayerInventory.StackSnapshot(ownedItemTypes[slot], ownedCounts[slot],
                        ownedDurabilities[slot], ownedEnchantments[slot], ownedMapIds[slot],
                        ownedShulkerIds[slot], ownedBucketMobData[slot],
                        ownedItemComponentData[slot]);
            }
            itemTypes = ownedItemTypes;
            counts = ownedCounts;
            durabilities = ownedDurabilities;
            enchantments = ownedEnchantments;
            mapIds = ownedMapIds;
            shulkerIds = ownedShulkerIds;
            bucketMobData = ownedBucketMobData;
            itemComponentData = ownedItemComponentData;
        }

        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] counts() { return counts.clone(); }
        @Override public int[] durabilities() { return durabilities.clone(); }
        @Override public long[] enchantments() { return enchantments.clone(); }
        @Override public int[] mapIds() { return mapIds.clone(); }
        @Override public int[] shulkerIds() { return shulkerIds.clone(); }
        @Override public String[] bucketMobData() { return bucketMobData.clone(); }
        @Override public String[] itemComponentData() { return itemComponentData.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot snapshot)) return false;
            return Arrays.equals(itemTypes, snapshot.itemTypes)
                    && Arrays.equals(counts, snapshot.counts)
                    && Arrays.equals(durabilities, snapshot.durabilities)
                    && Arrays.equals(enchantments, snapshot.enchantments)
                    && Arrays.equals(mapIds, snapshot.mapIds)
                    && Arrays.equals(shulkerIds, snapshot.shulkerIds)
                    && Arrays.equals(bucketMobData, snapshot.bucketMobData)
                    && Arrays.equals(itemComponentData, snapshot.itemComponentData);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(itemTypes);
            result = 31 * result + Arrays.hashCode(counts);
            result = 31 * result + Arrays.hashCode(durabilities);
            result = 31 * result + Arrays.hashCode(enchantments);
            result = 31 * result + Arrays.hashCode(mapIds);
            result = 31 * result + Arrays.hashCode(shulkerIds);
            result = 31 * result + Arrays.hashCode(bucketMobData);
            result = 31 * result + Arrays.hashCode(itemComponentData);
            return result;
        }

        @Override
        public String toString() {
            return "Snapshot{slots=" + itemTypes.length
                    + ", itemTypes=" + Arrays.toString(itemTypes)
                    + ", counts=" + Arrays.toString(counts)
                    + ", durabilities=" + Arrays.toString(durabilities)
                    + ", enchantments=" + Arrays.toString(enchantments)
                    + ", mapIds=" + Arrays.toString(mapIds)
                    + ", shulkerIds=" + Arrays.toString(shulkerIds)
                    + ", bucketMobData=<redacted>, itemComponentData=<redacted>}";
        }

        private static String redactStrings(String[] values) {
            return "<redacted>";
        }
    }

    public synchronized short itemType(int slot) {
        return itemType[slot];
    }

    public synchronized int count(int slot) {
        return count[slot];
    }

    /** 슬롯의 내구도. 내구 없는 아이템과 빈 칸은 0입니다. */
    public synchronized int durability(int slot) {
        return durability[slot];
    }

    /** [SURV-X] 슬롯의 인챈트 마스크(없으면 0). */
    public synchronized long enchantments(int slot) {
        return slot < 0 || slot >= slots ? 0 : enchantments[slot];
    }

    public synchronized int mapId(int slot) {
        return slot < 0 || slot >= slots ? 0 : mapIds[slot];
    }

    /** [SHULKER-CONTENTS] 그 칸에 든 셜커 상자의 27칸 참조 ID(없으면 0). */
    public synchronized int shulkerId(int slot) {
        return slot < 0 || slot >= slots ? 0 : shulkerIds[slot];
    }

    public synchronized String bucketMobData(int slot) {
        return slot < 0 || slot >= slots ? null : bucketMobData[slot];
    }

    public synchronized String itemComponentData(int slot) {
        return slot < 0 || slot >= slots ? null : itemComponentData[slot];
    }

    /** 인챈트 컬럼이 없던 세이브 복원 경로. */
    public synchronized void restoreSlot(int slot, short type, int amount, Integer savedDurability) {
        restoreSlot(slot, type, amount, savedDurability, null);
    }

    /** DB 스냅샷 복원 전용. 빈 칸은 호출하지 않는다. */
    public synchronized void restoreSlot(int slot, short type, int amount, Integer savedDurability,
            Long savedEnchantments) {
        restoreSlot(slot, type, amount, savedDurability, savedEnchantments, null);
    }

    public synchronized void restoreSlot(int slot, short type, int amount, Integer savedDurability,
            Long savedEnchantments, Integer savedMapId) {
        restoreSlot(slot, type, amount, savedDurability, savedEnchantments, savedMapId, null);
    }

    /** [SHULKER-CONTENTS] 27칸 참조 ID 열까지 포함한 복원. 옛 세이브는 이 열이 null 이다. */
    public synchronized void restoreSlot(int slot, short type, int amount, Integer savedDurability,
            Long savedEnchantments, Integer savedMapId, Integer savedShulkerId) {
        restoreSlot(slot, type, amount, savedDurability, savedEnchantments, savedMapId,
                savedShulkerId, null, null);
    }

    public synchronized void restoreSlot(int slot, short type, int amount, Integer savedDurability,
            Long savedEnchantments, Integer savedMapId, Integer savedShulkerId,
            String savedBucketMobData, String savedItemComponentData) {
        requireRestoreOpen();
        if (slot < 0 || slot >= slots) {
            throw new IllegalStateException("상자 슬롯 범위가 올바르지 않습니다: " + slot);
        }
        if (!PlayerInventory.isRegisteredItemType(type) || amount <= 0) {
            throw new IllegalStateException("상자 아이템 스택이 올바르지 않습니다: type="
                    + Short.toUnsignedInt(type) + ", amount=" + amount);
        }
        int restoredMapId = savedMapId == null ? 0 : savedMapId;
        if (!PlayerInventory.isValidMapIdentity(type, restoredMapId)) {
            throw new IllegalStateException("상자 지도 ID가 올바르지 않습니다: type="
                    + Short.toUnsignedInt(type) + ", mapId=" + restoredMapId);
        }
        int stackMaximum = PlayerInventory.stackMax(type);
        if (amount > stackMaximum) {
            throw new IllegalStateException("상자 아이템 수량이 스택 상한을 넘었습니다: type="
                    + Short.toUnsignedInt(type) + ", amount=" + amount + ", max=" + stackMaximum);
        }
        int restoredDurability = 0;
        if (PlayerInventory.isDurable(type)) {
            int maximum = PlayerInventory.initialDurability(type);
            if (savedDurability == null || savedDurability <= 0 || savedDurability > maximum) {
                throw new IllegalStateException("내구 아이템의 저장 내구도가 올바르지 않습니다: type="
                        + Short.toUnsignedInt(type) + ", durability=" + savedDurability);
            }
            restoredDurability = savedDurability;
        } else if (savedDurability != null) {
            throw new IllegalStateException("내구 없는 아이템에 저장 내구도가 있습니다: type="
                    + Short.toUnsignedInt(type));
        }
        long restoredEnchantments = savedEnchantments == null ? 0L : savedEnchantments;
        if (!com.gameexpert.engine.enchant.EnchantmentRules.isValidEnchantmentMask(
                restoredEnchantments)) {
            throw new IllegalStateException("상자 인챈트 마스크가 올바르지 않습니다");
        }
        if (!com.gameexpert.engine.enchant.EnchantmentRules.isValidEnchantmentMaskForItem(
                type, restoredEnchantments)) {
            throw new IllegalStateException("상자 아이템 인챈트가 의미적으로 올바르지 않습니다: type="
                    + Short.toUnsignedInt(type));
        }
        int restoredShulkerId = savedShulkerId == null ? 0 : savedShulkerId;
        if (!PlayerInventory.isValidShulkerIdentity(type, restoredShulkerId)) {
            throw new IllegalStateException("상자 셜커 참조 ID가 올바르지 않습니다: type="
                    + Short.toUnsignedInt(type) + ", shulkerId=" + restoredShulkerId);
        }
        // [SHULKER-CONTENTS] 참조가 붙은 상자는 반드시 한 칸에 하나다(27칸 복제 방지).
        if (restoredShulkerId != 0 && amount != 1) {
            throw new IllegalStateException("셜커 상자 스택은 한 개여야 합니다: amount=" + amount);
        }
        if (!BucketMobPayloadCodec.validForItem(type, savedBucketMobData)) {
            throw new IllegalStateException("상자 양동이 생물 payload가 올바르지 않습니다");
        }
        PlayerInventory.StackSnapshot prepared;
        try {
            prepared = new PlayerInventory.StackSnapshot(type, amount, restoredDurability,
                    restoredEnchantments, restoredMapId, restoredShulkerId,
                    savedBucketMobData, savedItemComponentData);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("상자 아이템 component payload가 올바르지 않습니다",
                    malformed);
        }
        itemType[slot] = prepared.itemType();
        count[slot] = prepared.count();
        durability[slot] = prepared.durability();
        enchantments[slot] = prepared.enchantments();
        mapIds[slot] = prepared.mapId();
        shulkerIds[slot] = prepared.shulkerId();
        bucketMobData[slot] = prepared.bucketMobData();
        itemComponentData[slot] = prepared.itemComponentData();
    }

    /** 비어 있으면 true — 상자를 부술 때 드랍이 필요한지 판단합니다. */
    public synchronized boolean isEmpty() {
        for (short type : itemType) {
            if (type != PlayerInventory.EMPTY) return false;
        }
        return true;
    }

    /** Validates a direct-ingress identity without allowing any caller-specific normalization. */
    private static PlayerInventory.StackSnapshot canonicalDirectStack(short type, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponents) {
        try {
            return new PlayerInventory.StackSnapshot(type, 1, itemDurability, itemEnchantments,
                    itemMapId, itemShulkerId, itemBucketMobData, itemComponents);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * 같은 종류부터 채우고 남으면 빈 칸에 넣습니다. 넣지 못한 개수를 돌려주므로
     * 호출부가 "일부만 들어갔다"를 판단할 수 있습니다.
     */
    public synchronized int add(short type, int amount, int itemDurability) {
        return add(type, amount, itemDurability, 0);
    }

    /** [SURV-X] 인챈트된 스택은 마스크를 함께 보관하고, 마스크가 다르면 기존 스택에 합치지 않는다. */
    public synchronized int add(short type, int amount, int itemDurability, long itemEnchantments) {
        return add(type, amount, itemDurability, itemEnchantments, 0);
    }

    public synchronized int add(
            short type, int amount, int itemDurability, long itemEnchantments, int itemMapId) {
        return add(type, amount, itemDurability, itemEnchantments, itemMapId, 0);
    }

    /** [SHULKER-CONTENTS] 27칸을 물고 있는 상자를 참조 ID 째로 담는다(스택 1 고정). */
    public synchronized int add(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return add(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                null, null);
    }

    public synchronized int add(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponents) {
        if (amount <= 0) return amount;
        PlayerInventory.StackSnapshot incoming = canonicalDirectStack(type, itemDurability,
                itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponents);
        if (incoming == null) return amount;
        if (incoming.shulkerId() != 0 && amount != 1) return amount;
        long mask = incoming.enchantments();
        if (PlayerInventory.isDurable(type)) {
            for (int slot = 0; slot < slots; slot++) {
                if (itemType[slot] != PlayerInventory.EMPTY) continue;
                preflightRevisionCapacity();
                itemType[slot] = type;
                count[slot] = 1;
                // 이동 프로토콜은 값을 반드시 전달한다. 유효한 기존 아이템만 들어오므로 최대값 초기화는 하지 않는다.
                durability[slot] = incoming.durability();
                enchantments[slot] = mask;
                mapIds[slot] = incoming.mapId();
                shulkerIds[slot] = incoming.shulkerId();
                bucketMobData[slot] = incoming.bucketMobData();
                itemComponentData[slot] = incoming.itemComponentData();
                advancePersistenceRevision();
                return amount - 1;
            }
            return amount;
        }
        int remaining = amount;
        int stack = PlayerInventory.stackMax(type);
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (itemType[slot] != type || count[slot] >= stack
                    || durability[slot] != incoming.durability()
                    || enchantments[slot] != mask || mapIds[slot] != incoming.mapId()
                    || shulkerIds[slot] != incoming.shulkerId()
                    || !java.util.Objects.equals(bucketMobData[slot], incoming.bucketMobData())
                    || !java.util.Objects.equals(itemComponentData[slot],
                            incoming.itemComponentData())) continue;
            int room = stack - count[slot];
            int moved = Math.min(room, remaining);
            remaining -= moved;
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (itemType[slot] != PlayerInventory.EMPTY) continue;
            int moved = Math.min(stack, remaining);
            remaining -= moved;
        }
        if (remaining == amount) return remaining;

        preflightRevisionCapacity();
        remaining = amount;
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (itemType[slot] != type || count[slot] >= stack
                    || durability[slot] != incoming.durability()
                    || enchantments[slot] != mask || mapIds[slot] != incoming.mapId()
                    || shulkerIds[slot] != incoming.shulkerId()
                    || !java.util.Objects.equals(bucketMobData[slot], incoming.bucketMobData())
                    || !java.util.Objects.equals(itemComponentData[slot],
                            incoming.itemComponentData())) continue;
            int room = stack - count[slot];
            int moved = Math.min(room, remaining);
            count[slot] += moved;
            remaining -= moved;
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (itemType[slot] != PlayerInventory.EMPTY) continue;
            int moved = Math.min(stack, remaining);
            itemType[slot] = type;
            count[slot] = moved;
            enchantments[slot] = mask;
            mapIds[slot] = incoming.mapId();
            shulkerIds[slot] = incoming.shulkerId();
            bucketMobData[slot] = incoming.bucketMobData();
            itemComponentData[slot] = incoming.itemComponentData();
            remaining -= moved;
        }
        advancePersistenceRevision();
        return remaining;
    }

    /** 새 일반 아이템 지급용. 내구 아이템 이동에는 {@link #add(short, int, int)}를 사용합니다. */
    public synchronized int add(short type, int amount) {
        return add(type, amount, PlayerInventory.initialDurability(type));
    }

    /**
     * [CONTAINER-CURSOR] 그 칸이 같은 정체성의 스택을 몇 개 더 받을 수 있는가.
     * 다른 스택이 들어 있으면 0이고, 빈 칸이면 스택 상한(내구 아이템은 1)입니다.
     */
    public synchronized int roomForSlot(
            int slot, short type, int itemDurability, long itemEnchantments, int itemMapId) {
        return roomForSlot(slot, type, itemDurability, itemEnchantments, itemMapId, 0);
    }

    /** [SHULKER-CONTENTS] 27칸 참조까지 같아야 같은 스택으로 본다. */
    public synchronized int roomForSlot(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        return roomForSlot(slot, type, itemDurability, itemEnchantments, itemMapId,
                itemShulkerId, null, null);
    }

    public synchronized int roomForSlot(int slot, short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponents) {
        if (!PlayerInventory.isRegisteredItemType(type)
                || slot < 0 || slot >= slots || type == PlayerInventory.EMPTY) return 0;
        PlayerInventory.StackSnapshot incoming = canonicalDirectStack(type, itemDurability,
                itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponents);
        if (incoming == null) return 0;
        long mask = incoming.enchantments();
        int stack = PlayerInventory.stackMax(type);
        if (itemType[slot] == PlayerInventory.EMPTY) return stack;
        if (itemType[slot] != type || enchantments[slot] != mask
                || mapIds[slot] != incoming.mapId()
                || shulkerIds[slot] != incoming.shulkerId()
                || durability[slot] != incoming.durability()
                || !java.util.Objects.equals(bucketMobData[slot], incoming.bucketMobData())
                || !java.util.Objects.equals(itemComponentData[slot],
                        incoming.itemComponentData())) {
            return 0;
        }
        return Math.max(0, stack - count[slot]);
    }

    /**
     * [CONTAINER-CURSOR] 그 칸에 amount 개까지 넣습니다(빈 칸이면 정체성도 함께 심습니다).
     * 실제로 넣은 개수를 돌려주므로 호출부가 커서를 그만큼만 줄이면 됩니다.
     */
    public synchronized int putInSlot(int slot, short type, int amount,
            int itemDurability, long itemEnchantments, int itemMapId) {
        return putInSlot(slot, type, amount, itemDurability, itemEnchantments, itemMapId, 0);
    }

    /** [SHULKER-CONTENTS] 빈 칸에는 27칸 참조 ID 도 함께 심는다. */
    public synchronized int putInSlot(int slot, short type, int amount, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId) {
        return putInSlot(slot, type, amount, itemDurability, itemEnchantments, itemMapId,
                itemShulkerId, null, null);
    }

    public synchronized int putInSlot(int slot, short type, int amount, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponents) {
        if (amount <= 0) return 0;
        PlayerInventory.StackSnapshot incoming = canonicalDirectStack(type, itemDurability,
                itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponents);
        if (incoming == null) return 0;
        int room = roomForSlot(slot, type, itemDurability, itemEnchantments, itemMapId,
                itemShulkerId, itemBucketMobData, itemComponents);
        int moved = Math.min(room, amount);
        if (moved <= 0) return 0;
        long mask = incoming.enchantments();
        preflightRevisionCapacity();
        if (itemType[slot] == PlayerInventory.EMPTY) {
            itemType[slot] = type;
            durability[slot] = incoming.durability();
            enchantments[slot] = mask;
            mapIds[slot] = incoming.mapId();
            shulkerIds[slot] = incoming.shulkerId();
            bucketMobData[slot] = incoming.bucketMobData();
            itemComponentData[slot] = incoming.itemComponentData();
        }
        count[slot] += moved;
        advancePersistenceRevision();
        return moved;
    }

    /** 지정 칸에서 최대 amount 개를 꺼냅니다. 실제로 꺼낸 개수를 돌려줍니다. */
    public synchronized int take(int slot, int amount) {
        if (slot < 0 || slot >= slots || amount <= 0) return 0;
        int taken = Math.min(count[slot], amount);
        if (taken <= 0) return 0;
        preflightRevisionCapacity();
        count[slot] -= taken;
        if (count[slot] <= 0) {
            itemType[slot] = PlayerInventory.EMPTY;
            count[slot] = 0;
            durability[slot] = 0;
            enchantments[slot] = 0;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
        advancePersistenceRevision();
        return taken;
    }

    /** ShelfBlock direct exchange: atomically replaces one slot and returns its exact old stack. */
    public synchronized PlayerInventory.StackSnapshot swapExact(
            int slot, PlayerInventory.StackSnapshot replacement) {
        if (slot < 0 || slot >= slots || replacement == null) {
            throw new IllegalArgumentException("invalid shelf exchange");
        }
        PlayerInventory.StackSnapshot previous = new PlayerInventory.StackSnapshot(
                itemType[slot], count[slot], durability[slot], enchantments[slot], mapIds[slot],
                shulkerIds[slot], bucketMobData[slot], itemComponentData[slot]);
        preflightRevisionCapacity();
        if (replacement.itemType() == PlayerInventory.EMPTY) {
            itemType[slot] = PlayerInventory.EMPTY;
            count[slot] = durability[slot] = mapIds[slot] = shulkerIds[slot] = 0;
            enchantments[slot] = 0;
            bucketMobData[slot] = itemComponentData[slot] = null;
        } else {
            itemType[slot] = replacement.itemType();
            count[slot] = replacement.count();
            durability[slot] = replacement.durability();
            enchantments[slot] = replacement.enchantments();
            mapIds[slot] = replacement.mapId();
            shulkerIds[slot] = replacement.shulkerId();
            bucketMobData[slot] = replacement.bucketMobData();
            itemComponentData[slot] = replacement.itemComponentData();
        }
        advancePersistenceRevision();
        return previous;
    }

    /** 상자를 부술 때 내용물을 전부 드랍하기 위해 비우고 (종류, 개수) 목록을 돌려줍니다. */
    public synchronized java.util.List<StoredStack> drainAll() {
        boolean occupied = false;
        for (int slot = 0; slot < slots; slot++) {
            if (itemType[slot] != PlayerInventory.EMPTY && count[slot] > 0) {
                occupied = true;
                break;
            }
        }
        java.util.List<StoredStack> out = new java.util.ArrayList<>();
        if (!occupied) return out;
        preflightRevisionCapacity();
        for (int slot = 0; slot < slots; slot++) {
            if (itemType[slot] == PlayerInventory.EMPTY || count[slot] <= 0) continue;
            out.add(new StoredStack(itemType[slot], count[slot], durability[slot],
                    enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                    itemComponentData[slot]));
            itemType[slot] = PlayerInventory.EMPTY;
            count[slot] = 0;
            durability[slot] = 0;
            enchantments[slot] = 0;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
        advancePersistenceRevision();
        return out;
    }

    /** 상자 파괴 드랍까지 종류·개수·내구도·인챈트를 함께 운반하는 단순 값 객체입니다. */
    public static final class StoredStack {
        private final short itemType;
        private final int count;
        private final int durability;
        private final long enchantments;
        private final int mapId;
        /** [SHULKER-CONTENTS] 이 스택이 물고 가는 27칸 참조 ID(없으면 0). */
        private final int shulkerId;
        private final String bucketMobData;
        private final String itemComponentData;

        public StoredStack(short itemType, int count, int durability) {
            this(itemType, count, durability, 0);
        }

        public StoredStack(short itemType, int count, int durability, long enchantments) {
            this(itemType, count, durability, enchantments, 0);
        }

        public StoredStack(
                short itemType, int count, int durability, long enchantments, int mapId) {
            this(itemType, count, durability, enchantments, mapId, 0);
        }

        public StoredStack(short itemType, int count, int durability, long enchantments,
                int mapId, int shulkerId) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, null, null);
        }

        public StoredStack(short itemType, int count, int durability, long enchantments,
                int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
            PlayerInventory.StackSnapshot validated;
            try {
                validated = new PlayerInventory.StackSnapshot(itemType, count, durability,
                        enchantments, mapId, shulkerId, bucketMobData, itemComponentData);
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("invalid stored stack", malformed);
            }
            this.itemType = validated.itemType();
            this.count = validated.count();
            this.durability = validated.durability();
            this.enchantments = validated.enchantments();
            this.mapId = validated.mapId();
            this.shulkerId = validated.shulkerId();
            this.bucketMobData = validated.bucketMobData();
            this.itemComponentData = validated.itemComponentData();
        }

        public short itemType() { return itemType; }
        public int count() { return count; }
        public int durability() { return durability; }
        /** [SURV-X] 이 스택이 지닌 인챈트 마스크. */
        public long enchantments() { return enchantments; }
        public int mapId() { return mapId; }
        /** [SHULKER-CONTENTS] 이 스택이 물고 가는 27칸 참조 ID(없으면 0). */
        public int shulkerId() { return shulkerId; }
        public String bucketMobData() { return bucketMobData; }
        public String itemComponentData() { return itemComponentData; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StoredStack stack)) return false;
            return itemType == stack.itemType && count == stack.count
                    && durability == stack.durability && enchantments == stack.enchantments
                    && mapId == stack.mapId && shulkerId == stack.shulkerId
                    && java.util.Objects.equals(bucketMobData, stack.bucketMobData)
                    && java.util.Objects.equals(itemComponentData, stack.itemComponentData);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(itemType, count, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
        }

        @Override
        public String toString() {
            return "StoredStack{itemType=" + Short.toUnsignedInt(itemType)
                    + ", count=" + count + ", durability=" + durability
                    + ", enchantments=" + enchantments + ", mapId=" + mapId
                    + ", shulkerId=" + shulkerId
                    + ", bucketMobData="
                    + Snapshot.redactStrings(new String[] {bucketMobData})
                    + ", itemComponentData="
                    + Snapshot.redactStrings(new String[] {itemComponentData}) + "}";
        }
    }
}
