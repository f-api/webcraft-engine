package com.gameexpert.engine.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.gameexpert.engine.WorldMapRuntime;
import com.gameexpert.engine.FurnaceInventory;
import com.gameexpert.engine.FurnaceRules;
import com.gameexpert.engine.EnchantingInventory;
import com.gameexpert.engine.PlayerAction.EnchantArea;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.engine.mob.BucketMobPayloadCodec;
import com.gameexpert.terrain.Blocks;

/**
 * 플레이어 한 명의 서버 권위 인벤토리(36칸). 틱 스레드 전용 상태입니다.
 *
 * 규약(§4): 총 36슬롯 = 핫바(0~8) + 가방(9~35). 슬롯 0 고정 검 규정은 폐지되어, 검·도구는
 * 일반 아이템처럼 자유롭게 이동할 수 있습니다. 블록·막대·채굴 재료는 스택 상한 64, 검·도구는
 * 스택 1이며 내구도(durability)를 가집니다(시작 60). 비어 있는 칸은 itemType 0·count 0 입니다.
 *
 * 이 클래스는 "담는 그릇 + 저수준 변이(제작·이동·내구 소모)"를 담당하고, 어떤 블록이 무엇을 드랍하는지·
 * 설치가 가능한지 같은 게임 규칙 판정은 {@link InventoryRules} 가 합니다.
 */
public final class PlayerInventory {

    public enum Hand {
        MAIN,
        OFFHAND
    }

    /** 긴 사용 시작 시 주손 슬롯 또는 보조손 위치를 고정하는 불변 참조입니다. */
    public static final class HandRef {
        private final long inventoryIdentity;
        private final Hand hand;
        private final int mainSlot;
        private final long revision;
        private final long mutationNonce;
        private final StackSnapshot capturedStack;

        private HandRef(long inventoryIdentity, Hand hand, int mainSlot, long revision,
                long mutationNonce, StackSnapshot capturedStack) {
            this.inventoryIdentity = inventoryIdentity;
            this.hand = hand;
            this.mainSlot = mainSlot;
            this.revision = revision;
            this.mutationNonce = mutationNonce;
            this.capturedStack = capturedStack;
        }

        public Hand hand() { return hand; }
        public int mainSlot() { return mainSlot; }
        public long revision() { return revision; }
        public long mutationNonce() { return mutationNonce; }
        public StackSnapshot capturedStack() { return capturedStack; }
    }

    /**
     * 한 칸짜리 손/장비 경계를 오갈 때 쓰는 손실 없는 스택 값입니다. 빈 스택은 모든 필드가 0입니다.
     */
    public static final class StackSnapshot {
        public static final StackSnapshot EMPTY = new StackSnapshot(
                PlayerInventory.EMPTY, 0, 0, EnchantmentRules.EMPTY_ENCHANTMENTS,
                0, 0, null, null);

        private final short itemType;
        private final int count;
        private final int durability;
        private final long enchantments;
        private final int mapId;
        private final int shulkerId;
        private final String bucketMobData;
        private final String itemComponentData;

        public StackSnapshot(short itemType, int count, int durability, long enchantments,
                int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
            if (itemType == PlayerInventory.EMPTY || count <= 0) {
                if (itemType != PlayerInventory.EMPTY || count != 0 || durability != 0
                        || enchantments != EnchantmentRules.EMPTY_ENCHANTMENTS
                        || mapId != 0 || shulkerId != 0 || bucketMobData != null
                        || itemComponentData != null) {
                    throw new IllegalArgumentException("empty stack must contain only zero values");
                }
            } else {
                if (!isRegisteredItemType(itemType) || count > stackMax(itemType)) {
                    throw new IllegalArgumentException("invalid stack item or count");
                }
                if (isDurable(itemType)
                        ? durability <= 0 || durability > initialDurability(itemType)
                        : durability != 0) {
                    throw new IllegalArgumentException("invalid stack durability");
                }
                if (!EnchantmentRules.isValidEnchantmentMaskForItem(itemType, enchantments)
                        || !isValidMapIdentity(itemType, mapId)
                        || !isValidShulkerIdentity(itemType, shulkerId)
                        || shulkerId != 0 && count != 1
                        || !BucketMobPayloadCodec.validForItem(itemType, bucketMobData)) {
                    throw new IllegalArgumentException("invalid stack components");
                }
                ItemComponentData components = ItemComponentCodec.decode(itemType, itemComponentData);
                // [ENCHANT-WIDE] 확장 인챈트는 성분에 살지만 배타·대상 판정은 워드 0 과 한 집합이다.
                if (components.hasExtendedEnchantments()
                        && !EnchantmentRules.isValidEnchantmentsForItem(
                                itemType, components.enchantments(enchantments))) {
                    throw new IllegalArgumentException("invalid stack components");
                }
            }
            this.itemType = itemType;
            this.count = count;
            this.durability = durability;
            this.enchantments = enchantments;
            this.mapId = mapId;
            this.shulkerId = shulkerId;
            this.bucketMobData = bucketMobData;
            this.itemComponentData = itemComponentData;
        }

        public short itemType() { return itemType; }
        public int count() { return count; }
        public int durability() { return durability; }
        public long enchantments() { return enchantments; }
        public int mapId() { return mapId; }
        public int shulkerId() { return shulkerId; }
        public String bucketMobData() { return bucketMobData; }
        public String itemComponentData() { return itemComponentData; }
        public ItemComponentData itemComponents() {
            return ItemComponentCodec.decode(itemType, itemComponentData);
        }
        /** [ENCHANT-WIDE] 워드 0 마스크와 성분의 확장 워드를 합친 43종 집합. */
        public WideEnchantments wideEnchantments() {
            return wideEnchantmentsOf(enchantments, itemComponentData);
        }

        public boolean isEmpty() {
            return itemType == PlayerInventory.EMPTY;
        }

        public StackSnapshot withCount(int amount) {
            return amount == 0 ? EMPTY : new StackSnapshot(itemType, amount, durability,
                    enchantments, mapId, shulkerId, bucketMobData, itemComponentData);
        }

        public boolean sameIdentity(StackSnapshot other) {
            return other != null && (isEmpty() || other.isEmpty()
                    ? isEmpty() && other.isEmpty()
                    : withCount(1).equals(other.withCount(1)));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof StackSnapshot stack)) return false;
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
    }

    /** 사망 시 아이템 엔티티로 옮길 한 스택. 종류·수량·내구도를 함께 보존합니다. */
    public static final class DroppedStack {
        private final short itemType;
        private final int count;
        private final int durability;
        /** [SURV-X] 스택별 인챈트 압축 마스크. 인챈트가 없으면 0이다. */
        private final long enchantments;
        /** 채워진 지도만 가지는 월드 지도 ID. 그 밖의 스택은 0이다. */
        private final int mapId;
        /**
         * [SHULKER-CONTENTS] 셜커 상자 스택만 가지는 27칸 참조 ID. 그 밖의 스택은 0이다.
         *
         * <p>지도({@link #mapId})가 낸 선례를 문자 그대로 따른다 — 드랍은 ID 하나만 물고
         * 다니고 27칸 자체는 {@code shulker_contents} 가 소유한다. 27칸을 드랍에 실으면
         * 드랍·와이어·영속이 전부 가변 크기 레코드가 된다.
         */
        private final int shulkerId;
        private final String bucketMobData;
        private final String itemComponentData;

        private DroppedStack(short itemType, int count, int durability) {
            this(itemType, count, durability, EnchantmentRules.EMPTY_ENCHANTMENTS, 0);
        }

        private DroppedStack(short itemType, int count, int durability, long enchantments) {
            this(itemType, count, durability, enchantments, 0);
        }

        private DroppedStack(short itemType, int count, int durability, long enchantments, int mapId) {
            this(itemType, count, durability, enchantments, mapId, 0);
        }

        private DroppedStack(short itemType, int count, int durability, long enchantments,
                int mapId, int shulkerId) {
            this(itemType, count, durability, enchantments, mapId, shulkerId, null);
        }

        private DroppedStack(short itemType, int count, int durability, long enchantments,
                int mapId, int shulkerId, String bucketMobData) {
            this(itemType, count, durability, enchantments, mapId, shulkerId,
                    bucketMobData, null);
        }

        private DroppedStack(short itemType, int count, int durability, long enchantments,
                int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
            StackSnapshot validated;
            try {
                validated = new StackSnapshot(itemType, count, durability, enchantments, mapId,
                        shulkerId, bucketMobData, itemComponentData);
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("invalid dropped stack", malformed);
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

        public static DroppedStack exact(short itemType, int count, int durability,
                long enchantments, int mapId, int shulkerId, String bucketMobData,
                String itemComponentData) {
            return new DroppedStack(itemType, count, durability, enchantments, mapId, shulkerId,
                    bucketMobData, itemComponentData);
        }

        public short itemType() {
            return itemType;
        }

        public int count() {
            return count;
        }

        public int durability() {
            return durability;
        }

        /** [SURV-X] 이 스택이 지닌 인챈트 마스크. */
        public long enchantments() {
            return enchantments;
        }

        public int mapId() {
            return mapId;
        }

        /** [SHULKER-CONTENTS] 이 스택이 물고 가는 27칸 참조 ID(내용이 없으면 0). */
        public int shulkerId() {
            return shulkerId;
        }

        public String bucketMobData() { return bucketMobData; }
        public String itemComponentData() { return itemComponentData; }
    }

    /** 내구화가 끝난 빈 지도 사용을 인벤토리에 반영한 결과입니다. */
    public static final class MapUseResult {
        private final boolean completed;
        private final DroppedStack overflow;

        private MapUseResult(boolean completed, DroppedStack overflow) {
            this.completed = completed;
            this.overflow = overflow;
        }

        public boolean completed() {
            return completed;
        }

        public DroppedStack overflow() {
            return overflow;
        }
    }

    public enum CraftArea {
        INVENTORY,
        GRID,
        RESULT,
        ARMOR,
        OFFHAND
    }

    /**
     * Opaque durable-map capability required by cartography. Its constructor deliberately has no
     * caller-visible ingress: the durable map-row owner must be wired as the issuer before this
     * inventory can accept a cartography command. Caller-supplied scale/lock/map IDs are not
     * authority.
     */
    public static final class CartographyAuthorityWitness {
        private final CartographyRules.Operation operation;
        private final int sourceMapId;
        private final int currentScale;
        private final boolean locked;
        private final int allocatedMapId;
        private final long mapRevision;
        private final boolean settlementAuthorized;

        private CartographyAuthorityWitness(CartographyRules.Operation operation,
                int sourceMapId, int currentScale, boolean locked,
                int allocatedMapId, long mapRevision, boolean settlementAuthorized) {
            this.operation = operation;
            this.sourceMapId = sourceMapId;
            this.currentScale = currentScale;
            this.locked = locked;
            this.allocatedMapId = allocatedMapId;
            this.mapRevision = mapRevision;
            this.settlementAuthorized = settlementAuthorized;
        }

        /** Durable map authority is the sole ingress; raw map metadata is never accepted here. */
        public static CartographyAuthorityWitness from(
                WorldMapRuntime.CartographyAuthority authority) {
            if (authority == null) throw new NullPointerException("cartography authority is required");
            return new CartographyAuthorityWitness(authority.operation(), authority.sourceMapId(),
                    authority.currentScale(), authority.locked(), authority.resultMapId(),
                    authority.sourceRevision(), authority.settlementAuthorized());
        }

        public CartographyRules.Operation operation() { return operation; }
        public int sourceMapId() { return sourceMapId; }
        public int currentScale() { return currentScale; }
        public boolean locked() { return locked; }
        public int resultMapId() { return allocatedMapId; }
        public long sourceRevision() { return mapRevision; }
        public boolean settlementAuthorized() { return settlementAuthorized; }

        private boolean sameAuthority(CartographyAuthorityWitness other) {
            return other != null && operation == other.operation
                    && sourceMapId == other.sourceMapId
                    && currentScale == other.currentScale
                    && locked == other.locked
                    && allocatedMapId == other.allocatedMapId
                    && mapRevision == other.mapRevision
                    && settlementAuthorized == other.settlementAuthorized;
        }
    }

    /**
     * Exact logical inventory delta submitted with one durable cartography settlement. The two
     * input slots, cursor, result and both revisions are frozen together so persistence can reject
     * a post-snapshot that did not consume precisely the witnessed inputs.
     */
    public static final class CartographyMutation {
        private final CartographyRules.Operation operation;
        private final long sourceMapRevision;
        private final long beforeInventoryRevision;
        private final long afterInventoryRevision;
        private final StackSnapshot mapInputBefore;
        private final StackSnapshot additionInputBefore;
        private final StackSnapshot mapInputAfter;
        private final StackSnapshot additionInputAfter;
        private final StackSnapshot cursorBefore;
        private final StackSnapshot cursorAfter;
        private final StackSnapshot result;
        private final boolean shift;
        private final String fingerprint;

        private CartographyMutation(CartographyRules.Operation operation,
                long sourceMapRevision, long beforeInventoryRevision,
                long afterInventoryRevision, StackSnapshot mapInputBefore,
                StackSnapshot additionInputBefore, StackSnapshot mapInputAfter,
                StackSnapshot additionInputAfter, StackSnapshot cursorBefore,
                StackSnapshot cursorAfter, StackSnapshot result, boolean shift) {
            this.operation = operation;
            this.sourceMapRevision = sourceMapRevision;
            this.beforeInventoryRevision = beforeInventoryRevision;
            this.afterInventoryRevision = afterInventoryRevision;
            this.mapInputBefore = mapInputBefore;
            this.additionInputBefore = additionInputBefore;
            this.mapInputAfter = mapInputAfter;
            this.additionInputAfter = additionInputAfter;
            this.cursorBefore = cursorBefore;
            this.cursorAfter = cursorAfter;
            this.result = result;
            this.shift = shift;
            DigestBuilder digest = new DigestBuilder("cartography-inventory-delta-v1");
            digest.intValue(operation.ordinal());
            digest.longValue(sourceMapRevision);
            digest.longValue(beforeInventoryRevision);
            digest.longValue(afterInventoryRevision);
            appendStack(digest, mapInputBefore);
            appendStack(digest, additionInputBefore);
            appendStack(digest, mapInputAfter);
            appendStack(digest, additionInputAfter);
            appendStack(digest, cursorBefore);
            appendStack(digest, cursorAfter);
            appendStack(digest, result);
            digest.booleanValue(shift);
            fingerprint = digest.finish();
        }

        public CartographyRules.Operation operation() { return operation; }
        public long sourceMapRevision() { return sourceMapRevision; }
        public long beforeInventoryRevision() { return beforeInventoryRevision; }
        public long afterInventoryRevision() { return afterInventoryRevision; }
        public StackSnapshot mapInputBefore() { return mapInputBefore; }
        public StackSnapshot additionInputBefore() { return additionInputBefore; }
        public StackSnapshot mapInputAfter() { return mapInputAfter; }
        public StackSnapshot additionInputAfter() { return additionInputAfter; }
        public StackSnapshot cursorBefore() { return cursorBefore; }
        public StackSnapshot cursorAfter() { return cursorAfter; }
        public StackSnapshot result() { return result; }
        public boolean shift() { return shift; }
        public String fingerprint() { return fingerprint; }
    }

    public enum CraftButton {
        LEFT,
        RIGHT
    }

    public enum FurnaceArea {
        INVENTORY,
        FURNACE
    }

    /** [CONTAINER-CURSOR] 보관 컨테이너 메뉴의 두 영역. CONTAINER 는 상자·화물 격자다. */
    public enum ContainerArea {
        INVENTORY,
        CONTAINER
    }

    public static final class CraftClickResult {
        private final boolean changed;
        private final boolean crafted;

        private CraftClickResult(boolean changed, boolean crafted) {
            this.changed = changed;
            this.crafted = crafted;
        }

        public boolean changed() {
            return changed;
        }

        public boolean crafted() {
            return crafted;
        }
    }

    /**
     * 제작 칸과 커서를 일반 36칸으로 정규화한 영속 스냅샷입니다. 세 배열은 한 번의 잠금 안에서
     * 함께 만들어지므로 주기 저장이 서로 다른 제작 상태를 섞지 않습니다.
     */
    public static final class PersistenceSnapshot {
        private final short[] itemTypes;
        private final int[] counts;
        private final int[] durabilities;
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;
        private final StackSnapshot offhand;
        private final long revision;

        private PersistenceSnapshot(InventoryArrays arrays, StackSnapshot offhand, long revision) {
            itemTypes = arrays.types;
            counts = arrays.counts;
            durabilities = arrays.durabilities;
            enchantments = arrays.enchantments;
            mapIds = arrays.mapIds;
            shulkerIds = arrays.shulkerIds;
            bucketMobData = arrays.bucketMobData;
            itemComponentData = arrays.itemComponentData;
            this.offhand = offhand;
            this.revision = revision;
        }

        public short[] itemTypes() {
            return itemTypes.clone();
        }

        public int[] counts() {
            return counts.clone();
        }

        public int[] durabilities() {
            return durabilities.clone();
        }

        /** [SURV-X] 슬롯별 인챈트 마스크(길이 36). 인챈트 없는 칸은 0이다. */
        public long[] enchantments() {
            return enchantments.clone();
        }

        public int[] mapIds() {
            return mapIds.clone();
        }

        /** [SHULKER-CONTENTS] 슬롯별 27칸 참조 ID(길이 36). 셜커가 아닌 칸은 0이다. */
        public int[] shulkerIds() {
            return shulkerIds.clone();
        }

        public String[] bucketMobData() { return bucketMobData.clone(); }
        public String[] itemComponentData() { return itemComponentData.clone(); }

        public StackSnapshot offhand() {
            return offhand;
        }

        public long revision() {
            return revision;
        }
    }

    /**
     * 보상·외부 정산이 사용하는 한 잠금의 완전한 인벤토리 상태입니다. 보이는 36칸뿐 아니라
     * 보조손·장비·선택 슬롯·제작 격자/커서까지 함께 포착해 서로 다른 revision의 배열이 섞이지 않습니다.
     */
    public static final class CompletePersistenceSnapshot {
        private final short[] itemTypes;
        private final int[] counts;
        private final int[] durabilities;
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;
        private final short[] equippedTypes;
        private final int[] equippedDurabilities;
        private final long[] equippedEnchantments;
        private final String[] equippedItemComponentData;
        private final StackSnapshot offhand;
        private final int selectedSlot;
        private final long revision;
        private final short[] craftingTypes;
        private final int[] craftingCounts;
        private final int[] craftingDurabilities;
        private final long[] craftingEnchantments;
        private final int[] craftingMapIds;
        private final int[] craftingShulkerIds;
        private final String[] craftingBucketMobData;
        private final String[] craftingItemComponentData;
        private final short cursorType;
        private final int cursorCount;
        private final int cursorDurability;
        private final long cursorEnchantments;
        private final int cursorMapId;
        private final int cursorShulkerId;
        private final String cursorBucketMobData;
        private final String cursorItemComponentData;
        private final int craftingGridSize;
        private final int craftingSlotCount;
        private final boolean craftingStonecutter;
        private final String craftingSelection;
        private final long leaseNonce;
        private final String snapshotDigest;
        private final String sourceLineageDigest;

        private final StackSnapshot[] merchantPayments;

        private final PersistenceSnapshot persistenceSnapshot;

        private CompletePersistenceSnapshot(PlayerInventory inventory, InventoryArrays normalized) {
            this(inventory, normalized, 0L);
        }

        private CompletePersistenceSnapshot(PlayerInventory inventory, InventoryArrays normalized,
                long leaseNonce) {
            merchantPayments = inventory.merchantPaymentSnapshot();
            itemTypes = inventory.itemType.clone();
            counts = inventory.count.clone();
            durabilities = inventory.durability.clone();
            enchantments = inventory.enchantments.clone();
            mapIds = inventory.mapIds.clone();
            shulkerIds = inventory.shulkerIds.clone();
            bucketMobData = inventory.bucketMobData.clone();
            itemComponentData = inventory.itemComponentData.clone();
            equippedTypes = inventory.equippedType.clone();
            equippedDurabilities = inventory.equippedDurability.clone();
            equippedEnchantments = inventory.equippedEnchantments.clone();
            equippedItemComponentData = inventory.equippedItemComponentData.clone();
            offhand = inventory.offhand;
            selectedSlot = inventory.selectedSlot;
            revision = inventory.revision;
            craftingTypes = inventory.craftingType.clone();
            craftingCounts = inventory.craftingCount.clone();
            craftingDurabilities = inventory.craftingDurability.clone();
            craftingEnchantments = inventory.craftingEnchantments.clone();
            craftingMapIds = inventory.craftingMapIds.clone();
            craftingShulkerIds = inventory.craftingShulkerIds.clone();
            craftingBucketMobData = inventory.craftingBucketMobData.clone();
            craftingItemComponentData = inventory.craftingItemComponentData.clone();
            cursorType = inventory.cursorType;
            cursorCount = inventory.cursorCount;
            cursorDurability = inventory.cursorDurability;
            cursorEnchantments = inventory.cursorEnchantments;
            cursorMapId = inventory.cursorMapId;
            cursorShulkerId = inventory.cursorShulkerId;
            cursorBucketMobData = inventory.cursorBucketMobData;
            cursorItemComponentData = inventory.cursorItemComponentData;
            craftingGridSize = inventory.craftingGridSize;
            craftingSlotCount = inventory.craftingSlotCount;
            craftingStonecutter = inventory.craftingStonecutter;
            craftingSelection = inventory.craftingSelection;
            persistenceSnapshot = new PersistenceSnapshot(normalized, offhand, revision);
            this.leaseNonce = leaseNonce;
            snapshotDigest = digestCompleteState(inventory.inventoryIdentity, this);
            sourceLineageDigest = leaseNonce != 0 || inventory.persistenceLineageDigest == null
                    ? snapshotDigest : inventory.persistenceLineageDigest;
        }

        public long revision() { return revision; }
        /** Unique capability nonce for a settlement lease; zero for ordinary snapshots. */
        public long leaseNonce() { return leaseNonce; }
        /** Compatibility alias for integrations that call the settlement capability a nonce. */
        public long settlementNonce() { return leaseNonce; }
        /** Digest of this exact complete state, bound to its source inventory identity. */
        public String snapshotDigest() { return snapshotDigest; }
        /** Digest of the source snapshot from which this state was detached and planned. */
        public String sourceLineageDigest() { return sourceLineageDigest; }
        /** Compatibility alias for source lineage binding. */
        public String lineageDigest() { return sourceLineageDigest; }
        public int selectedSlot() { return selectedSlot; }
        public StackSnapshot offhand() { return offhand; }
        public short[] itemTypes() { return itemTypes.clone(); }
        public int[] counts() { return counts.clone(); }
        public int[] durabilities() { return durabilities.clone(); }
        public long[] enchantments() { return enchantments.clone(); }
        public int[] mapIds() { return mapIds.clone(); }
        public int[] shulkerIds() { return shulkerIds.clone(); }
        public String[] bucketMobData() { return bucketMobData.clone(); }
        public String[] itemComponentData() { return itemComponentData.clone(); }
        public short[] equippedTypes() { return equippedTypes.clone(); }
        public int[] equippedDurabilities() { return equippedDurabilities.clone(); }
        public long[] equippedEnchantments() { return equippedEnchantments.clone(); }
        public String[] equippedItemComponentData() { return equippedItemComponentData.clone(); }
        public PersistenceSnapshot persistenceSnapshot() { return persistenceSnapshot; }

        /**
         * XP처럼 아이템 배열은 그대로지만 플레이어 영속 상태가 바뀌는 정산용 다음 세대입니다.
         * 원본/실시간 인벤토리는 건드리지 않고 분리된 계획 사본에서 정확히 한 번만 증가시킵니다.
         */
        public CompletePersistenceSnapshot nextSettlementRevision() {
            PlayerInventory detached = detachedInventory();
            detached.advancePersistenceRevision();
            return detached.completePersistenceSnapshot();
        }

        /**
         * [ENCHANT-WIDE] XP 구슬 정산의 다음 세대에 수선을 함께 싣는다. 분리 사본에서
         * {@link #applyMending} 을 돌리고, 고친 것이 없을 때만 세대를 따로 올려 언제나 정확히 한 세대 앞선다.
         */
        public MendingSettlement nextSettlementRevisionWithMending(int xp,
                java.util.function.IntUnaryOperator randomIndex) {
            PlayerInventory detached = detachedInventory();
            long before = detached.revision;
            int leftover = detached.applyMending(xp, randomIndex);
            if (detached.revision == before) detached.advancePersistenceRevision();
            return new MendingSettlement(detached.completePersistenceSnapshot(), leftover,
                    leftover != xp);
        }

        public PlayerInventory detachedInventory() {
            PlayerInventory inventory = new PlayerInventory(itemTypes, counts, durabilities,
                    enchantments, mapIds, shulkerIds, bucketMobData, itemComponentData,
                    equippedTypes, equippedDurabilities,
                    equippedEnchantments, equippedItemComponentData, offhand, selectedSlot,
                    revision);
            System.arraycopy(craftingTypes, 0, inventory.craftingType, 0, craftingTypes.length);
            System.arraycopy(craftingCounts, 0, inventory.craftingCount, 0, craftingCounts.length);
            System.arraycopy(craftingDurabilities, 0, inventory.craftingDurability, 0,
                    craftingDurabilities.length);
            System.arraycopy(craftingEnchantments, 0, inventory.craftingEnchantments, 0,
                    craftingEnchantments.length);
            System.arraycopy(craftingMapIds, 0, inventory.craftingMapIds, 0, craftingMapIds.length);
            System.arraycopy(craftingShulkerIds, 0, inventory.craftingShulkerIds, 0,
                    craftingShulkerIds.length);
            System.arraycopy(craftingBucketMobData, 0, inventory.craftingBucketMobData, 0,
                    craftingBucketMobData.length);
            System.arraycopy(craftingItemComponentData, 0, inventory.craftingItemComponentData, 0,
                    craftingItemComponentData.length);
            inventory.cursorType = cursorType;
            inventory.cursorCount = cursorCount;
            inventory.cursorDurability = cursorDurability;
            inventory.cursorEnchantments = cursorEnchantments;
            inventory.cursorMapId = cursorMapId;
            inventory.cursorShulkerId = cursorShulkerId;
            inventory.cursorBucketMobData = cursorBucketMobData;
            inventory.cursorItemComponentData = cursorItemComponentData;
            inventory.craftingGridSize = craftingGridSize;
            inventory.craftingSlotCount = craftingSlotCount;
            inventory.craftingStonecutter = craftingStonecutter;
            inventory.craftingSelection = craftingSelection;
            inventory.restoreMerchantPayments(merchantPayments);
            inventory.persistenceLineageDigest = sourceLineageDigest;
            return inventory;
        }
    }

    private static String digestCompleteState(long inventoryIdentity,
            CompletePersistenceSnapshot source) {
        DigestBuilder digest = new DigestBuilder("player-complete-state-v1");
        digest.longValue(inventoryIdentity);
        digest.longValue(source.revision);
        digest.shortArray(source.itemTypes);
        digest.intArray(source.counts);
        digest.intArray(source.durabilities);
        digest.longArray(source.enchantments);
        digest.intArray(source.mapIds);
        digest.intArray(source.shulkerIds);
        digest.stringArray(source.bucketMobData);
        digest.stringArray(source.itemComponentData);
        digest.shortArray(source.equippedTypes);
        digest.intArray(source.equippedDurabilities);
        digest.longArray(source.equippedEnchantments);
        digest.stringArray(source.equippedItemComponentData);
        appendStack(digest, source.offhand);
        digest.intValue(source.selectedSlot);
        digest.shortArray(source.craftingTypes);
        digest.intArray(source.craftingCounts);
        digest.intArray(source.craftingDurabilities);
        digest.longArray(source.craftingEnchantments);
        digest.intArray(source.craftingMapIds);
        digest.intArray(source.craftingShulkerIds);
        digest.stringArray(source.craftingBucketMobData);
        digest.stringArray(source.craftingItemComponentData);
        appendStack(digest, source.cursorType, source.cursorCount, source.cursorDurability,
                source.cursorEnchantments, source.cursorMapId, source.cursorShulkerId,
                source.cursorBucketMobData, source.cursorItemComponentData);
        digest.intValue(source.craftingGridSize);
        digest.intValue(source.craftingSlotCount);
        digest.booleanValue(source.craftingStonecutter);
        digest.stringValue(source.craftingSelection);
        for (StackSnapshot payment : source.merchantPayments) appendStack(digest, payment);
        return digest.finish();
    }

    private static void appendStack(DigestBuilder digest, StackSnapshot stack) {
        appendStack(digest, stack.itemType(), stack.count(), stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData());
    }

    private static void appendStack(DigestBuilder digest, short type, int count, int durability,
            long enchantments, int mapId, int shulkerId, String bucketMobData,
            String itemComponentData) {
        digest.shortValue(type);
        digest.intValue(count);
        digest.intValue(durability);
        digest.longValue(enchantments);
        digest.intValue(mapId);
        digest.intValue(shulkerId);
        digest.stringValue(bucketMobData);
        digest.stringValue(itemComponentData);
    }

    private static final class DigestBuilder {
        private final MessageDigest digest;

        private DigestBuilder(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new ExceptionInInitializerError(impossible);
            }
            stringValue(domain);
        }

        private void shortValue(short value) {
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        private void intValue(int value) {
            digest.update((byte) (value >>> 24));
            digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        private void longValue(long value) {
            for (int shift = 56; shift >= 0; shift -= 8) {
                digest.update((byte) (value >>> shift));
            }
        }

        private void booleanValue(boolean value) {
            digest.update((byte) (value ? 1 : 0));
        }

        private void stringValue(String value) {
            if (value == null) {
                digest.update((byte) 0);
                return;
            }
            digest.update((byte) 1);
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            intValue(encoded.length);
            digest.update(encoded, 0, encoded.length);
        }

        private void shortArray(short[] values) {
            intValue(values.length);
            for (short value : values) shortValue(value);
        }

        private void intArray(int[] values) {
            intValue(values.length);
            for (int value : values) intValue(value);
        }

        private void longArray(long[] values) {
            intValue(values.length);
            for (long value : values) longValue(value);
        }

        private void stringArray(String[] values) {
            intValue(values.length);
            for (String value : values) stringValue(value);
        }

        private String finish() {
            byte[] bytes = digest.digest();
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(value & 0x0f, 16));
            }
            return result.toString();
        }
    }

    /** 전체 슬롯 수(핫바 9 + 가방 27). */
    public static final int SLOTS = 36;
    public static final int EQUIPPED_SLOT_BASE = SLOTS;
    public static final int PERSISTED_SLOTS = SLOTS + ArmorSlot.values().length;
    /**
     * [ENDER-SHULKER] 플레이어 엔더 상자 27칸이 앉는 슬롯 밴드의 시작(= 40).
     *
     * <p>엔더 상자 내용은 좌표가 아니라 플레이어가 소유하므로 {@code world_chests} 가 아니라
     * <b>플레이어 상태</b>에 저장한다. 새 테이블을 만들지 않고 이미 있는
     * {@code player_inventory_items} 의 <b>슬롯 번호를 append</b> 하는 이유는 착용 방어구가
     * {@link #EQUIPPED_SLOT_BASE} 로 낸 선례와 같다: 옛 세이브에는 이 밴드의 행이 아예 없어
     * 빈 27칸으로 복원되므로 {@code DATABASE_VERSION} 을 올릴 필요가 없다.
     */
    public static final int ENDER_SLOT_BASE = PERSISTED_SLOTS;
    /** [ENDER-SHULKER] 엔더 상자 칸 수. 바닐라도 27칸이고 큰 상자를 이루지 않는다. */
    public static final int ENDER_CHEST_SLOTS = 27;
    /** [ENDER-SHULKER] 엔더 상자 밴드까지 포함한 영속 슬롯 상한(= 67). */
    public static final int PERSISTED_SLOTS_WITH_ENDER = ENDER_SLOT_BASE + ENDER_CHEST_SLOTS;
    /** 보조손은 엔더 상자 바로 뒤의 append-only 영속 슬롯 한 칸을 사용합니다. */
    public static final int OFFHAND_SLOT = PERSISTED_SLOTS_WITH_ENDER;
    /** 보조손까지 포함한 player_inventory_items 슬롯 상한(exclusive). */
    public static final int PERSISTED_SLOTS_WITH_OFFHAND = OFFHAND_SLOT + 1;
    /** Minecraft 플레이어 인벤토리에서 보조손이 갖는 논리 슬롯 번호. */
    public static final int OFFHAND_INVENTORY_SLOT = 40;
    /** 핫바 슬롯 수. 선택 슬롯(selectSlot)은 핫바 안에서만 고릅니다. */
    public static final int HOTBAR_SLOTS = 9;
    /** 블록·막대 등 일반 아이템의 스택 상한. */
    public static final int STACK_MAX = 64;
    /** 바닐라 egg 의 max_stack_size. 64 도 1 도 아닌 유일한 등록 아이템이다. */
    public static final int EGG_STACK_MAX = 16;
    public static final short EMPTY = 0;

    // 순수 아이템 ID(§2): 블록 byte 공간과 분리한 256+ 연속 범위다.
    public static final short SWORD_ITEM = 256;
    public static final short PICKAXE = 257;
    public static final short AXE = 258;
    public static final short SHOVEL = 259;
    public static final short STICK = 260;
    public static final short BEEF_RAW = 261;
    public static final short PORK_RAW = 262;
    public static final short MUTTON_RAW = 263;
    public static final short WOOL = 264;
    public static final short CHICKEN_RAW = 265;
    public static final short BEEF_COOKED = 266;
    public static final short PORK_COOKED = 267;
    public static final short MUTTON_COOKED = 268;
    public static final short CHICKEN_COOKED = 269;
    /** 보트 아이템(§2): 블록이 아니라 엔티티를 스폰하는 배치형 아이템. 스택 1(내구 없음). */
    public static final short BOAT = 278;
    /** 양동이 4종(§6): 빈/물/용암/우유. 모두 개별 아이템으로 스택 1(내구 없음). */
    public static final short BUCKET = 279;
    public static final short WATER_BUCKET = 280;
    public static final short LAVA_BUCKET = 281;
    /** 자갈 채굴 희귀 드랍 재료. 블록이 아니며 64개까지 스택된다. */
    public static final short FLINT = 282;
    /** MC 광석·식물 드랍. 모두 블록이 아닌 64 스택 아이템이다. */
    public static final short COAL = 283;
    public static final short RAW_IRON = 284;
    public static final short RAW_GOLD = 285;
    public static final short DIAMOND = 286;
    public static final short OAK_SAPLING = 133;
    public static final short BIRCH_SAPLING = 134;
    public static final short APPLE = 287;
    public static final short WHEAT_SEEDS = 288;
    // 방어구(가죽·철): 헬멧·흉갑·레깅스·부츠 순서.
    public static final short LEATHER_HELMET = 289;
    public static final short LEATHER_CHESTPLATE = 290;
    public static final short LEATHER_LEGGINGS = 291;
    public static final short LEATHER_BOOTS = 292;
    public static final short IRON_HELMET = 293;
    public static final short IRON_CHESTPLATE = 294;
    public static final short IRON_LEGGINGS = 295;
    public static final short IRON_BOOTS = 296;
    /** 묘목 성장을 촉진하는 뼛가루. 블록이 아닌 64 스택 소비 아이템이다. */
    public static final short BONE_MEAL = 297;
    /** 제련·고급 식량 재료. 기존 114/115 익명 값 대신 충돌 없는 신규 ID를 사용한다. */
    public static final short IRON_INGOT = 298;
    public static final short GOLD_INGOT = 299;
    public static final short CHARCOAL = 300;
    public static final short GOLDEN_APPLE = 301;
    // 금·다이아 티어: 곡괭이·도끼·삽·검·투구·흉갑·레깅스·부츠 순서.
    public static final short GOLD_TIER_MIN = 302;
    public static final short GOLD_PICKAXE = 302;
    public static final short GOLD_AXE = 303;
    public static final short GOLD_SHOVEL = 304;
    public static final short GOLD_MINING_TOOL_MAX = GOLD_SHOVEL;
    public static final short GOLD_SWORD = 305;
    public static final short GOLD_TOOL_MAX = GOLD_SWORD;
    public static final short GOLD_HELMET = 306;
    public static final short GOLD_ARMOR_MIN = GOLD_HELMET;
    public static final short GOLD_CHESTPLATE = 307;
    public static final short GOLD_LEGGINGS = 308;
    public static final short GOLD_BOOTS = 309;
    public static final short GOLD_ARMOR_MAX = GOLD_BOOTS;
    public static final short GOLD_TIER_MAX = 309;
    public static final short DIAMOND_TIER_MIN = 310;
    public static final short DIAMOND_PICKAXE = 310;
    public static final short DIAMOND_AXE = 311;
    public static final short DIAMOND_SHOVEL = 312;
    public static final short DIAMOND_MINING_TOOL_MAX = DIAMOND_SHOVEL;
    public static final short DIAMOND_SWORD = 313;
    public static final short DIAMOND_TOOL_MAX = DIAMOND_SWORD;
    public static final short DIAMOND_HELMET = 314;
    public static final short DIAMOND_ARMOR_MIN = DIAMOND_HELMET;
    public static final short DIAMOND_CHESTPLATE = 315;
    public static final short DIAMOND_LEGGINGS = 316;
    public static final short DIAMOND_BOOTS = 317;
    public static final short DIAMOND_ARMOR_MAX = DIAMOND_BOOTS;
    public static final short DIAMOND_TIER_MAX = 317;
    /** 스켈레톤 드랍. 뼛가루 제작 재료인 64 스택 일반 아이템. */
    public static final short BONE = 318;
    /** 암소에게 빈 양동이를 사용해 얻는 우유 양동이. 마시면 빈 양동이로 돌아간다. */
    public static final short MILK_BUCKET = 319;
    /** 철 주괴와 부싯돌로 만드는 라이터. 성공적인 점화마다 1씩 닳는다. */
    public static final short FLINT_AND_STEEL = 320;
    /** 주손으로 우클릭 홀드해 정면 피해를 막는 방패. */
    public static final short SHIELD = 321;
    /**
     * [DIAMOND-SHIELD] 다이아 방패(1858). <b>바닐라에 없는 WebCraft 창작</b>이다 —
     * 마인크래프트에는 방패 티어가 하나뿐이다. 막기 판정·무력화·정면 각도·넉백 무효화는
     * {@link #SHIELD} 와 <b>완전히 같고</b>(바닐라 방패도 정면 100% 차단이라 올릴 여지가 없다)
     * 유일한 차별점은 {@link #DIAMOND_SHIELD_DURABILITY} 다. 그래서 방패 여부를 묻는 자리는
     * 값 비교를 복제하지 않고 전부 {@link #isShield(short)} 하나만 지난다.
     */
    public static final short DIAMOND_SHIELD = (short) Blocks.DIAMOND_SHIELD;
    /**
     * [TRIDENT] 삼지창(1920). 근접 9 · 투척 8 · 내구 250 이고 <b>수리 재료가 없다</b>
     * (근거는 {@link Blocks#TRIDENT} 주석). client {@code items.ts} 의 {@code TRIDENT} 와
     * 같은 값이어야 한다.
     */
    public static final short TRIDENT = (short) Blocks.TRIDENT;
    /**
     * [MACE] 철퇴(2370). 근접 6(바닐라 attack_damage +5 에 플레이어 기본 1) · attack speed 0.6
     * (-3.4 + 4) · 내구 500 · 수리 재료 브리즈 막대(바닐라 {@code repairable(BREEZE_ROD)}).
     * client {@code items.ts} 의 {@code MACE} 와 같은 값이어야 한다.
     */
    public static final short MACE = (short) Blocks.MACE;
    /** [MACE][A] 바닐라 {@code Items.MACE} 의 {@code durability(500)}. */
    public static final int MACE_DURABILITY = 500;
    /**
     * [SPEAR] 창 여섯 티어(2000~2005). 값 정본은 {@link Blocks} 이고 client
     * {@code items.ts} 의 같은 이름과 같은 값이어야 한다. 잽 피해·공속·돌진 배율은
     * {@link com.gameexpert.engine.SpearRules} 가 표의 정본으로 소유한다.
     */
    public static final short WOODEN_SPEAR = (short) Blocks.WOODEN_SPEAR;
    /** [SPEAR] 돌 창(2001). */
    public static final short STONE_SPEAR = (short) Blocks.STONE_SPEAR;
    /** [SPEAR] 구리 창(2002). 이 저장소에서 구리가 무기 티어로 들어가는 유일한 자리다. */
    public static final short COPPER_SPEAR = (short) Blocks.COPPER_SPEAR;
    /** [SPEAR] 철 창(2003). 좀비 계열 자연 무장(핀 §5). */
    public static final short IRON_SPEAR = (short) Blocks.IRON_SPEAR;
    /** [SPEAR] 금 창(2004). 피글린 계열 자연 무장(핀 §5). */
    public static final short GOLD_SPEAR = (short) Blocks.GOLD_SPEAR;
    /** [SPEAR] 다이아몬드 창(2005). 등록된 마지막 티어다(네더라이트는 §6 대로 미등록). */
    public static final short DIAMOND_SPEAR = (short) Blocks.DIAMOND_SPEAR;
    /**
     * [SHIELD-FAMILY] 가죽 방패(2020). 값 정본은 {@link Blocks} 이고 client {@code items.ts} 의
     * 같은 이름과 같은 값이어야 한다.
     *
     * <p>이 네 티어는 막기 판정·무력화·정면 각도·넉백 무효화가 {@link #SHIELD} 와 <b>완전히
     * 같고</b> 차별점은 내구뿐이다(다이아 방패 1858 이 낸 선례 그대로). 그래서 "방패인가" 를
     * 묻는 자리는 값 비교를 복제하지 않고 전부 {@link #isShield(short)} 하나만 지난다.
     *
     * <p><b>철·다이아 방패는 여기 없다</b> — 321 과 1858 이 이미 그 자리이고 같은 물건에 ID 를
     * 둘 주지 않는다.
     */
    public static final short LEATHER_SHIELD = (short) Blocks.LEATHER_SHIELD;
    /** [SHIELD-FAMILY] 돌 방패(2021). */
    public static final short STONE_SHIELD = (short) Blocks.STONE_SHIELD;
    /** [SHIELD-FAMILY] 구리 방패(2022). */
    public static final short COPPER_SHIELD = (short) Blocks.COPPER_SHIELD;
    /** [SHIELD-FAMILY] 금 방패(2023). 이 저장소의 금 티어 규약대로 내구가 가장 낮다. */
    public static final short GOLD_SHIELD = (short) Blocks.GOLD_SHIELD;
    /**
     * [NAUTILUS-MOUNT] 구리 노틸러스 갑옷(2040). 값 정본은 {@link Blocks} 이고 client
     * {@code items.ts} 의 같은 이름과 같은 값이어야 한다.
     *
     * <p>이 넷은 <b>플레이어 방어구가 아니다</b> — 길들인 노틸러스의 갑옷 슬롯에만 들어가고
     * 플레이어 방어구 슬롯 계산({@code armorPoints})에는 한 점도 얹지 않는다. "노틸러스
     * 갑옷인가" 를 묻는 자리는 값 비교를 복제하지 않고 전부
     * {@link Blocks#isNautilusArmorTier(int)} 하나만 지난다.
     */
    public static final short COPPER_NAUTILUS_ARMOR = (short) Blocks.COPPER_NAUTILUS_ARMOR;
    /** [NAUTILUS-MOUNT] 철 노틸러스 갑옷(2041). */
    public static final short IRON_NAUTILUS_ARMOR = (short) Blocks.IRON_NAUTILUS_ARMOR;
    /** [NAUTILUS-MOUNT] 금 노틸러스 갑옷(2042). */
    public static final short GOLD_NAUTILUS_ARMOR = (short) Blocks.GOLD_NAUTILUS_ARMOR;
    /**
     * [NAUTILUS-MOUNT] 다이아몬드 노틸러스 갑옷(2043). 원문 [B] 의 다섯 티어 중 네더라이트는
     * 이 저장소에 재료가 없어 신설하지 않았다(divergence, 연구 핀 §8-2 참조).
     */
    public static final short DIAMOND_NAUTILUS_ARMOR = (short) Blocks.DIAMOND_NAUTILUS_ARMOR;
    public static final short NETHERITE_NAUTILUS_ARMOR = (short) Blocks.NETHERITE_NAUTILUS_ARMOR;
    public static final short COPPER_PICKAXE = (short) Blocks.COPPER_PICKAXE;
    public static final short COPPER_AXE = (short) Blocks.COPPER_AXE;
    public static final short COPPER_SHOVEL = (short) Blocks.COPPER_SHOVEL;
    public static final short COPPER_SWORD = (short) Blocks.COPPER_SWORD;
    public static final short COPPER_HOE = (short) Blocks.COPPER_HOE;
    public static final short COPPER_HELMET = (short) Blocks.COPPER_HELMET;
    public static final short COPPER_CHESTPLATE = (short) Blocks.COPPER_CHESTPLATE;
    public static final short COPPER_LEGGINGS = (short) Blocks.COPPER_LEGGINGS;
    public static final short COPPER_BOOTS = (short) Blocks.COPPER_BOOTS;
    public static final short COPPER_HORSE_ARMOR = (short) Blocks.COPPER_HORSE_ARMOR;
    public static final short DIAMOND_HOE = (short) Blocks.DIAMOND_HOE;
    public static final short NETHERITE_SCRAP = (short) Blocks.NETHERITE_SCRAP;
    public static final short NETHERITE_INGOT = (short) Blocks.NETHERITE_INGOT;
    public static final short NETHERITE_UPGRADE_SMITHING_TEMPLATE = (short) Blocks.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
    public static final short NETHERITE_PICKAXE = (short) Blocks.NETHERITE_PICKAXE;
    public static final short NETHERITE_AXE = (short) Blocks.NETHERITE_AXE;
    public static final short NETHERITE_SHOVEL = (short) Blocks.NETHERITE_SHOVEL;
    public static final short NETHERITE_SWORD = (short) Blocks.NETHERITE_SWORD;
    public static final short NETHERITE_HOE = (short) Blocks.NETHERITE_HOE;
    public static final short NETHERITE_HELMET = (short) Blocks.NETHERITE_HELMET;
    public static final short NETHERITE_CHESTPLATE = (short) Blocks.NETHERITE_CHESTPLATE;
    public static final short NETHERITE_LEGGINGS = (short) Blocks.NETHERITE_LEGGINGS;
    public static final short NETHERITE_BOOTS = (short) Blocks.NETHERITE_BOOTS;
    public static final short NETHERITE_SPEAR = (short) Blocks.NETHERITE_SPEAR;
    public static final short NETHERITE_HORSE_ARMOR = (short) Blocks.NETHERITE_HORSE_ARMOR;
    public static final short GOAT_HORN = (short) Blocks.GOAT_HORN;
    public static final short TADPOLE_BUCKET = (short) Blocks.TADPOLE_BUCKET;
    public static final short LEATHER_HORSE_ARMOR = (short) Blocks.LEATHER_HORSE_ARMOR;
    public static final short IRON_HORSE_ARMOR = (short) Blocks.IRON_HORSE_ARMOR;
    public static final short GOLDEN_HORSE_ARMOR = (short) Blocks.GOLDEN_HORSE_ARMOR;
    public static final short DIAMOND_HORSE_ARMOR = (short) Blocks.DIAMOND_HORSE_ARMOR;
    public static final short TORCHFLOWER_SEEDS = (short) Blocks.TORCHFLOWER_SEEDS;
    public static final short PITCHER_POD = (short) Blocks.PITCHER_POD;
    public static final short LEAD = (short) Blocks.LEAD;
    public static final short AXOLOTL_BUCKET = (short) Blocks.AXOLOTL_BUCKET;
    public static final short ACTIVATOR_RAIL = (short) Blocks.ACTIVATOR_RAIL;
    public static final short DETECTOR_RAIL = (short) Blocks.DETECTOR_RAIL;
    public static final short POWERED_RAIL = (short) Blocks.POWERED_RAIL;
    public static final short MELON_SEEDS = (short) Blocks.MELON_SEEDS;
    public static final short MUSIC_DISC_13 = (short) Blocks.MUSIC_DISC_13;
    public static final short MUSIC_DISC_CAT = (short) Blocks.MUSIC_DISC_CAT;
    public static final short MUSIC_DISC_OTHERSIDE = (short) Blocks.MUSIC_DISC_OTHERSIDE;
    public static final short MUSIC_DISC_BOUNCE = (short) Blocks.MUSIC_DISC_BOUNCE;
    public static final short DUNE_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short WILD_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.WILD_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short EYE_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.EYE_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short BURN_POTTERY_SHERD = (short) Blocks.BURN_POTTERY_SHERD;
    public static final short DANGER_POTTERY_SHERD = (short) Blocks.DANGER_POTTERY_SHERD;
    public static final short FRIEND_POTTERY_SHERD = (short) Blocks.FRIEND_POTTERY_SHERD;
    public static final short HEART_POTTERY_SHERD = (short) Blocks.HEART_POTTERY_SHERD;
    public static final short HEARTBREAK_POTTERY_SHERD = (short) Blocks.HEARTBREAK_POTTERY_SHERD;
    public static final short HOWL_POTTERY_SHERD = (short) Blocks.HOWL_POTTERY_SHERD;
    public static final short SHEAF_POTTERY_SHERD = (short) Blocks.SHEAF_POTTERY_SHERD;
    public static final short FLOW_POTTERY_SHERD = (short) Blocks.FLOW_POTTERY_SHERD;
    public static final short GUSTER_POTTERY_SHERD = (short) Blocks.GUSTER_POTTERY_SHERD;
    public static final short SCRAPE_POTTERY_SHERD = (short) Blocks.SCRAPE_POTTERY_SHERD;
    public static final short WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short RAISER_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short HOST_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.HOST_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short MUSIC_DISC_RELIC = (short) Blocks.MUSIC_DISC_RELIC;
    /**
     * [CONDUIT] 바다의 심장(1950). [A] max_stack_size 64 의 평범한 재료 아이템이고
     * <b>제작법이 없다</b> — 묻힌 보물 상자가 유일한 입수 경로이며 그 전리품표의 소유자는
     * 해저 유적 · 보물 트랙이다. 소비처는 콘딧 제작 하나뿐이다.
     * client {@code items.ts} 의 {@code HEART_OF_THE_SEA} 와 같은 값이어야 한다.
     */
    public static final short HEART_OF_THE_SEA = (short) Blocks.HEART_OF_THE_SEA;
    // 콘딧(1951)은 <b>월드 블록</b>이라 여기에 별칭 상수를 두지 않는다 — 제작 산출은
    // 프리즈머린·바다 랜턴과 같이 {@code (short) Blocks.CONDUIT} 로 직접 쓴다(월드 블록을
    // PlayerInventory 에 다시 선언하면 같은 ID 가 두 정본을 갖는다).
    /**
     * [TURTLE] 거북 등딱지 조각(1931). 새끼 거북의 성장이 유일한 입수 경로이고 소비처는
     * 거북 등껍질 제작 하나뿐이다. 아르마딜로 인갑(446)과는 <b>다른 아이템</b>이다.
     * client {@code items.ts} 의 {@code TURTLE_SCUTE} 와 같은 값이어야 한다.
     */
    public static final short TURTLE_SCUTE = (short) Blocks.TURTLE_SCUTE;
    /**
     * [TURTLE] 거북 등껍질(1932). 투구 부위 방어구이며 [A] {@code Items.TURTLE_HELMET} 의
     * armor 2 · max_damage 275 를 그대로 쓴다. 티어가 한 조각뿐이라
     * {@link #armorSlot} 의 구간 산술이 서지 않아 낱개로 분기한다.
     * client {@code items.ts} 의 {@code TURTLE_SHELL} 과 같은 값이어야 한다.
     */
    public static final short TURTLE_SHELL = (short) Blocks.TURTLE_SHELL;
    /** 적대 몹 드랍 재료. 모두 내구 없는 64 스택 아이템이다. */
    public static final short ROTTEN_FLESH = 322;
    public static final short MYSTERY_FLESH = (short) Blocks.MYSTERY_FLESH;
    public static final short HEART_CORE = (short) Blocks.HEART_CORE;
    public static final short STRING = 323;
    public static final short GUNPOWDER = 324;
    public static final short SPIDER_EYE = 325;
    public static final short ARROW = 326;
    /** 엔더맨·슬라임 드랍 수집품. 던지기·끈끈이 제작 등 사용처는 현재 스코프 밖이다. */
    public static final short ENDER_PEARL = (short) Blocks.ENDER_PEARL;
    public static final short SLIME_BALL = (short) Blocks.SLIME_BALL;
    public static final short CARROT = 327;
    public static final short POTATO = 328;
    /** 농사 루프: 나무 괭이, 수확 밀, 섭취 빵. */
    public static final short WOODEN_HOE = 329;
    public static final short WHEAT = 330;
    public static final short BREAD = 331;
    /** 원거리 전투 축: 활(내구 384), 화살 제작용 깃털, 방어구 제작용 가죽. */
    public static final short BOW = 332;
    public static final short FEATHER = 333;
    public static final short LEATHER = 334;
    /** 제작법이 없는 전리품 전용 사슬 방어구. 헬멧·흉갑·레깅스·부츠 순서. */
    public static final short CHAINMAIL_HELMET = (short) Blocks.CHAINMAIL_HELMET;
    public static final short CHAINMAIL_CHESTPLATE = (short) Blocks.CHAINMAIL_CHESTPLATE;
    public static final short CHAINMAIL_LEGGINGS = (short) Blocks.CHAINMAIL_LEGGINGS;
    public static final short CHAINMAIL_BOOTS = (short) Blocks.CHAINMAIL_BOOTS;
    /** ores: 광석 블록 223–225의 비블록 채굴·전리품 산출. */
    public static final short EMERALD = 345;
    public static final short LAPIS_LAZULI = 346;
    public static final short REDSTONE_DUST = 347;
    public static final short CLAY_BALL = (short) Blocks.CLAY_BALL;
    public static final short GLASS_BOTTLE = (short) Blocks.GLASS_BOTTLE;
    public static final short WATER_BOTTLE = (short) Blocks.WATER_BOTTLE;
    public static final short GOLD_NUGGET = (short) Blocks.GOLD_NUGGET;
    public static final short PUMPKIN_SEEDS = (short) Blocks.PUMPKIN_SEEDS;
    public static final short SHEARS = (short) Blocks.SHEARS;
    /** cropengine 착지로 추가된 순수 아이템 10종. 월드 블록 229–232와 분리한다. */
    public static final short BEETROOT = (short) Blocks.BEETROOT;
    public static final short BEETROOT_SEEDS = (short) Blocks.BEETROOT_SEEDS;
    public static final short BOWL = (short) Blocks.BOWL;
    public static final short MUSHROOM_STEW = (short) Blocks.MUSHROOM_STEW;
    public static final short BEETROOT_SOUP = (short) Blocks.BEETROOT_SOUP;
    public static final short RABBIT_STEW = (short) Blocks.RABBIT_STEW;
    public static final short RABBIT_RAW = (short) Blocks.RABBIT_RAW;
    public static final short RABBIT_COOKED = (short) Blocks.RABBIT_COOKED;
    public static final short RABBIT_HIDE = (short) Blocks.RABBIT_HIDE;
    public static final short RABBIT_FOOT = (short) Blocks.RABBIT_FOOT;
    /** 자연/지질 확장의 순수 아이템. 월드 블록 high-water 뒤 append-only로 배정한다. */
    public static final short RAW_COPPER = (short) Blocks.RAW_COPPER;
    public static final short COPPER_INGOT = (short) Blocks.COPPER_INGOT;
    public static final short GLOW_BERRIES = (short) Blocks.GLOW_BERRIES;
    public static final short SNOWBALL = (short) Blocks.SNOWBALL;
    public static final short POWDER_SNOW_BUCKET = (short) Blocks.POWDER_SNOW_BUCKET;
    public static final short AMETHYST_SHARD = (short) Blocks.AMETHYST_SHARD;
    public static final short BAKED_POTATO = (short) Blocks.BAKED_POTATO;
    public static final short GOLDEN_CARROT = (short) Blocks.GOLDEN_CARROT;
    public static final short INK_SAC = (short) Blocks.INK_SAC;
    public static final short GLOW_INK_SAC = (short) Blocks.GLOW_INK_SAC;
    public static final short COD_RAW = (short) Blocks.COD_RAW;
    public static final short COD_COOKED = (short) Blocks.COD_COOKED;
    public static final short SALMON_RAW = (short) Blocks.SALMON_RAW;
    public static final short SALMON_COOKED = (short) Blocks.SALMON_COOKED;
    public static final short COD_BUCKET = (short) Blocks.COD_BUCKET;
    public static final short SALMON_BUCKET = (short) Blocks.SALMON_BUCKET;
    public static final short TROPICAL_FISH_BUCKET = (short) Blocks.TROPICAL_FISH_BUCKET;
    /** SURV-X 인챈트 확장 순수 아이템. 내구도 없이 64개까지 쌓인다(기본 규칙). 종이는 MAPNAV 것을 쓴다. */
    public static final short BOOK = (short) Blocks.BOOK;
    /** [MAPNAV] 나침반·시계·지도와 지도 재료인 종이. 전부 스택 64의 소비 없는 휴대품이다. */
    public static final short COMPASS = (short) Blocks.COMPASS;
    public static final short CLOCK = (short) Blocks.CLOCK;
    public static final short MAP = (short) Blocks.MAP;
    public static final short FILLED_MAP = (short) Blocks.FILLED_MAP;
    public static final short ABANDONED_CAMPSITE_MAP = (short) Blocks.ABANDONED_CAMPSITE_MAP;
    public static final short ANCIENT_CITY_MAP = (short) Blocks.ANCIENT_CITY_MAP;
    public static final short BURIED_TREASURE_MAP = (short) Blocks.BURIED_TREASURE_MAP;
    public static final short DESERT_PYRAMID_MAP = (short) Blocks.DESERT_PYRAMID_MAP;
    public static final short DESERT_VILLAGE_MAP = (short) Blocks.DESERT_VILLAGE_MAP;
    public static final short JUNGLE_EXPLORER_MAP = (short) Blocks.JUNGLE_EXPLORER_MAP;
    public static final short MINESHAFT_MAP = (short) Blocks.MINESHAFT_MAP;
    public static final short OCEAN_EXPLORER_MAP = (short) Blocks.OCEAN_EXPLORER_MAP;
    public static final short PLAINS_VILLAGE_MAP = (short) Blocks.PLAINS_VILLAGE_MAP;
    public static final short SAVANNA_VILLAGE_MAP = (short) Blocks.SAVANNA_VILLAGE_MAP;
    public static final short SNOWY_VILLAGE_MAP = (short) Blocks.SNOWY_VILLAGE_MAP;
    public static final short SWAMP_EXPLORER_MAP = (short) Blocks.SWAMP_EXPLORER_MAP;
    public static final short TAIGA_VILLAGE_MAP = (short) Blocks.TAIGA_VILLAGE_MAP;
    public static final short TRIAL_EXPLORER_MAP = (short) Blocks.TRIAL_EXPLORER_MAP;
    public static final short WARM_OCEAN_RUINS_MAP = (short) Blocks.WARM_OCEAN_RUINS_MAP;
    public static final short WOODLAND_EXPLORER_MAP = (short) Blocks.WOODLAND_EXPLORER_MAP;
    public static boolean isExplorerMap(int type) {
        return type >= Blocks.ABANDONED_CAMPSITE_MAP && type <= Blocks.WOODLAND_EXPLORER_MAP;
    }
    public static boolean isFilledMapItem(int type) {
        return type == FILLED_MAP || isExplorerMap(type);
    }

    public static final short TOTEM_OF_UNDYING = (short) Blocks.TOTEM_OF_UNDYING;
    /**
     * [ROTTEN-LEATHER] 썩은 가죽 재료 + 방어구 4부위. 부위 순서는 다른 티어와 같은
     * 투구·흉갑·레깅스·부츠라 {@link #armorSlot} 의 {@code (type - tierMin) % 4} 산술이 그대로 선다.
     */
    public static final short ROTTEN_LEATHER = (short) Blocks.ROTTEN_LEATHER;
    public static final short ROTTEN_LEATHER_HELMET = (short) Blocks.ROTTEN_LEATHER_HELMET;
    public static final short ROTTEN_LEATHER_ARMOR_MIN = ROTTEN_LEATHER_HELMET;
    public static final short ROTTEN_LEATHER_CHESTPLATE = (short) Blocks.ROTTEN_LEATHER_CHESTPLATE;
    public static final short ROTTEN_LEATHER_LEGGINGS = (short) Blocks.ROTTEN_LEATHER_LEGGINGS;
    public static final short ROTTEN_LEATHER_BOOTS = (short) Blocks.ROTTEN_LEATHER_BOOTS;
    public static final short ROTTEN_LEATHER_ARMOR_MAX = ROTTEN_LEATHER_BOOTS;
    /**
     * [TRIAL] 금고를 1인 1회 여는 열쇠. 스택 64 · 내구 없음이며 트라이얼 스포너 배출 표
     * spawners/trial_chamber/key 와 입구 상자 전리품이 얻는 경로다
     * ({@code engine/trial/TrialSpawnerContract.ejectedReward}).
     */
    public static final short TRIAL_KEY = (short) Blocks.TRIAL_KEY;
    /** [TRIAL] 불길한 금고를 여는 열쇠(minecraft:ominous_trial_key). */
    public static final short OMINOUS_TRIAL_KEY = (short) Blocks.OMINOUS_TRIAL_KEY;
    // [TRIAL-GAP] 트라이얼 챔버 표의 순수 아이템 2314~2330(정본 Blocks).
    public static final short POTION_REGENERATION = (short) Blocks.POTION_REGENERATION;
    public static final short LINGERING_POTION_WIND_CHARGED =
            (short) Blocks.LINGERING_POTION_WIND_CHARGED;
    public static final short LINGERING_POTION_OOZING = (short) Blocks.LINGERING_POTION_OOZING;
    public static final short LINGERING_POTION_WEAVING = (short) Blocks.LINGERING_POTION_WEAVING;
    public static final short LINGERING_POTION_INFESTED = (short) Blocks.LINGERING_POTION_INFESTED;
    public static final short LINGERING_POTION_STRENGTH = (short) Blocks.LINGERING_POTION_STRENGTH;
    public static final short LINGERING_POTION_SWIFTNESS =
            (short) Blocks.LINGERING_POTION_SWIFTNESS;
    public static final short LINGERING_POTION_SLOW_FALLING =
            (short) Blocks.LINGERING_POTION_SLOW_FALLING;
    public static final short TIPPED_ARROW_POISON = (short) Blocks.TIPPED_ARROW_POISON;
    public static final short TIPPED_ARROW_STRONG_SLOWNESS =
            (short) Blocks.TIPPED_ARROW_STRONG_SLOWNESS;
    /** [GLOWING] 분광 화살. 활·석궁 탄약이며 명중에 발광 200 MC 틱을 싣는다. */
    public static final short SPECTRAL_ARROW = (short) Blocks.SPECTRAL_ARROW;
    /** [CONTAINER-MENUS] 경험치 병 · 갑옷 거치대 · 광산 수레 다섯 종({@link Blocks} 가 정본). */
    public static final short EXPERIENCE_BOTTLE = (short) Blocks.EXPERIENCE_BOTTLE;
    public static final short ARMOR_STAND = (short) Blocks.ARMOR_STAND;
    public static final short MINECART = (short) Blocks.MINECART;
    public static final short CHEST_MINECART = (short) Blocks.CHEST_MINECART;
    public static final short HOPPER_MINECART = (short) Blocks.HOPPER_MINECART;
    public static final short FURNACE_MINECART = (short) Blocks.FURNACE_MINECART;
    public static final short TNT_MINECART = (short) Blocks.TNT_MINECART;
    public static final short FIRE_CHARGE = (short) Blocks.FIRE_CHARGE;
    public static final short BOLT_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short FLOW_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short GUSTER_BANNER_PATTERN = (short) Blocks.GUSTER_BANNER_PATTERN;
    public static final short FLOW_BANNER_PATTERN = (short) Blocks.FLOW_BANNER_PATTERN;
    public static final short MUSIC_DISC_PRECIPICE = (short) Blocks.MUSIC_DISC_PRECIPICE;
    public static final short MUSIC_DISC_CREATOR = (short) Blocks.MUSIC_DISC_CREATOR;
    public static final short EMERALD_BLOCK = (short) Blocks.EMERALD_BLOCK;
    public static final short HEAVY_CORE = (short) Blocks.HEAVY_CORE;
    // [UTILITY] 유틸리티 체계 레인 2380~2416(정본 Blocks). 2380~2382 는 월드 블록, 나머지는 순수 아이템.
    public static final short BEACON = (short) Blocks.BEACON;
    public static final short JUKEBOX = (short) Blocks.JUKEBOX;
    public static final short SLIME_BLOCK = (short) Blocks.SLIME_BLOCK;
    public static final short BLACKSTONE = (short) Blocks.BLACKSTONE;
    public static final short POLISHED_BLACKSTONE = (short) Blocks.POLISHED_BLACKSTONE;
    public static final short POLISHED_BLACKSTONE_BRICKS = (short) Blocks.POLISHED_BLACKSTONE_BRICKS;
    public static final short NETHER_STAR = (short) Blocks.NETHER_STAR;
    public static final short DRAGON_BREATH = (short) Blocks.DRAGON_BREATH;
    public static final short GHAST_TEAR = (short) Blocks.GHAST_TEAR;
    public static final short MELON_SLICE = (short) Blocks.MELON_SLICE;
    public static final short GLISTERING_MELON_SLICE = (short) Blocks.GLISTERING_MELON_SLICE;
    public static final short RESIN_BRICK = (short) Blocks.RESIN_BRICK;
    public static final short CONTENTS_POTION = (short) Blocks.CONTENTS_POTION;
    public static final short CONTENTS_SPLASH_POTION = (short) Blocks.CONTENTS_SPLASH_POTION;
    public static final short CONTENTS_LINGERING_POTION = (short) Blocks.CONTENTS_LINGERING_POTION;
    public static final short MUSIC_DISC_11 = (short) Blocks.MUSIC_DISC_11;
    public static final short MUSIC_DISC_5 = (short) Blocks.MUSIC_DISC_5;
    public static final short MUSIC_DISC_BLOCKS = (short) Blocks.MUSIC_DISC_BLOCKS;
    public static final short MUSIC_DISC_CHIRP = (short) Blocks.MUSIC_DISC_CHIRP;
    public static final short MUSIC_DISC_CREATOR_MUSIC_BOX =
            (short) Blocks.MUSIC_DISC_CREATOR_MUSIC_BOX;
    public static final short MUSIC_DISC_FAR = (short) Blocks.MUSIC_DISC_FAR;
    public static final short MUSIC_DISC_LAVA_CHICKEN = (short) Blocks.MUSIC_DISC_LAVA_CHICKEN;
    public static final short MUSIC_DISC_MALL = (short) Blocks.MUSIC_DISC_MALL;
    public static final short MUSIC_DISC_MELLOHI = (short) Blocks.MUSIC_DISC_MELLOHI;
    public static final short MUSIC_DISC_PIGSTEP = (short) Blocks.MUSIC_DISC_PIGSTEP;
    public static final short MUSIC_DISC_STAL = (short) Blocks.MUSIC_DISC_STAL;
    public static final short MUSIC_DISC_STRAD = (short) Blocks.MUSIC_DISC_STRAD;
    public static final short MUSIC_DISC_TEARS = (short) Blocks.MUSIC_DISC_TEARS;
    public static final short MUSIC_DISC_WAIT = (short) Blocks.MUSIC_DISC_WAIT;
    public static final short MUSIC_DISC_WARD = (short) Blocks.MUSIC_DISC_WARD;
    public static final short DISC_FRAGMENT_5 = (short) Blocks.DISC_FRAGMENT_5;
    public static final short COAST_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short RIB_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.RIB_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short TIDE_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short VEX_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.VEX_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short WARD_ARMOR_TRIM_SMITHING_TEMPLATE =
            (short) Blocks.WARD_ARMOR_TRIM_SMITHING_TEMPLATE;
    public static final short PAPER = (short) Blocks.PAPER;
    public static final short FISHING_ROD = (short) Blocks.FISHING_ROD;
    public static final short OMINOUS_BOTTLE = (short) Blocks.OMINOUS_BOTTLE;

    /**
     * [TRIAL-GAP] 증폭 {@code amplifier}(0..4)인 불길한 병 스택. 증폭은 바닐라
     * {@code minecraft:ominous_bottle_amplifier} 컴포넌트이며 {@link ItemComponentData} 에 실린다
     * (0 은 기본값이라 컴포넌트 없음).
     */
    public static StackSnapshot ominousBottleStack(int count, int amplifier) {
        return new StackSnapshot(OMINOUS_BOTTLE, count, 0, EnchantmentRules.EMPTY_ENCHANTMENTS,
                0, 0, null, ominousBottleComponents(amplifier));
    }

    /** [TRIAL-GAP] 증폭 {@code amplifier} 불길한 병의 영속 컴포넌트 문자열(0 이면 null). */
    public static String ominousBottleComponents(int amplifier) {
        return ItemComponentCodec.encode(OMINOUS_BOTTLE, ItemComponentData.ominousBottle(amplifier));
    }
    public static final short ARMADILLO_SCUTE = (short) Blocks.ARMADILLO_SCUTE;
    public static final short BRUSH = (short) Blocks.BRUSH;
    public static final short WOLF_ARMOR = (short) Blocks.WOLF_ARMOR;
    public static final short SWEET_BERRIES = (short) Blocks.SWEET_BERRIES;
    public static final short TROPICAL_FISH = (short) Blocks.TROPICAL_FISH;
    public static final short PUFFERFISH = (short) Blocks.PUFFERFISH;
    public static final short WHITE_DYE = (short) Blocks.WHITE_DYE;
    public static final short ORANGE_DYE = (short) Blocks.ORANGE_DYE;
    public static final short MAGENTA_DYE = (short) Blocks.MAGENTA_DYE;
    public static final short LIGHT_BLUE_DYE = (short) Blocks.LIGHT_BLUE_DYE;
    public static final short YELLOW_DYE = (short) Blocks.YELLOW_DYE;
    public static final short LIME_DYE = (short) Blocks.LIME_DYE;
    public static final short PINK_DYE = (short) Blocks.PINK_DYE;
    public static final short GRAY_DYE = (short) Blocks.GRAY_DYE;
    public static final short LIGHT_GRAY_DYE = (short) Blocks.LIGHT_GRAY_DYE;
    public static final short CYAN_DYE = (short) Blocks.CYAN_DYE;
    public static final short PURPLE_DYE = (short) Blocks.PURPLE_DYE;
    public static final short BLUE_DYE = (short) Blocks.BLUE_DYE;
    public static final short BROWN_DYE = (short) Blocks.BROWN_DYE;
    public static final short GREEN_DYE = (short) Blocks.GREEN_DYE;
    public static final short RED_DYE = (short) Blocks.RED_DYE;
    public static final short BLACK_DYE = (short) Blocks.BLACK_DYE;
    public static final short PUFFERFISH_BUCKET = (short) Blocks.PUFFERFISH_BUCKET;
    /** 겉날개. 흉갑 부위에 장착하며 방어도는 0이고 활공 1초마다 내구 1을 쓴다. */
    public static final short ELYTRA = (short) Blocks.ELYTRA;
    /** 폭죽 로켓 비행 지속 1~3 티어. 화약 개수가 티어를 정한다(바닐라 레시피). */
    public static final short FIREWORK_ROCKET_1 = (short) Blocks.FIREWORK_ROCKET_1;
    public static final short FIREWORK_ROCKET_2 = (short) Blocks.FIREWORK_ROCKET_2;
    public static final short FIREWORK_ROCKET_3 = (short) Blocks.FIREWORK_ROCKET_3;
    // ── 레이드 승리 전리품 순수 아이템 477~481. 전부 승리 보상 지급 경로에서만 생긴다. ──
    /** 기념 주화. 스택 가능한 순수 수집품이며 전투·경제 효과가 0이다. */
    public static final short COMMEMORATIVE_COIN = (short) Blocks.COMMEMORATIVE_COIN;
    /** 파성추의 뿔피리. 사용해도 소모되지 않고 소리만 낸다. */
    public static final short BATTERING_HORN = (short) Blocks.BATTERING_HORN;
    /** 꽃잎 주머니. 사용하면 한 개가 소모되고 벚꽃잎 분출 이벤트만 발생한다. */
    public static final short PETAL_POUCH = (short) Blocks.PETAL_POUCH;
    /** 우민 오르골. 사용해도 소모되지 않고 멜로디만 재생한다. */
    public static final short ILLAGER_MUSIC_BOX = (short) Blocks.ILLAGER_MUSIC_BOX;
    /** 다이아 낚싯대. 낚시 규칙은 낚싯대와 같고 내구만 512다. */
    public static final short DIAMOND_FISHING_ROD = (short) Blocks.DIAMOND_FISHING_ROD;
    // ── 낚시 전리품 순수 아이템 484~488(MC Java 1.21.4 fishing_junk · fishing_treasure). ──
    /** 이름표(fishing_treasure 가중치 1). 바닐라 max_stack_size 64. 사용처는 아직 없다. */
    public static final short NAME_TAG = (short) Blocks.NAME_TAG;
    /** 안장(fishing_treasure 가중치 1). 바닐라 max_stack_size 1. 사용처는 아직 없다. */
    public static final short SADDLE = (short) Blocks.SADDLE;
    /** 앵무조개 껍데기(fishing_treasure 가중치 1). 바닐라 max_stack_size 64 이며 제작 재료다. */
    public static final short NAUTILUS_SHELL = (short) Blocks.NAUTILUS_SHELL;
    /** 마법이 부여된 책(fishing_treasure 가중치 1). 스택 1이며 30레벨 인챈트를 갖고 나온다. */
    public static final short ENCHANTED_BOOK = (short) Blocks.ENCHANTED_BOOK;
    /** 철사 덫 갈고리(fishing_junk 가중치 10). 바닐라 max_stack_size 64. 설치할 수 없다. */
    public static final short TRIPWIRE_HOOK = (short) Blocks.TRIPWIRE_HOOK;
    public static final short WHITE_BANNER = (short) Blocks.WHITE_BANNER;
    public static final short BLACK_BANNER = (short) Blocks.BLACK_BANNER;
    public static final short WRITABLE_BOOK = (short) Blocks.WRITABLE_BOOK;
    public static final short WRITTEN_BOOK = (short) Blocks.WRITTEN_BOOK;
    // ── 동물 상호작용 순수 아이템 489~490(MC Java 1.21.4). ──
    /** 달걀. 바닐라 max_stack_size 16 이고 던지면 1/8 로 병아리가 나온다. */
    public static final short EGG = (short) Blocks.EGG;
    public static final short BLUE_EGG = (short) Blocks.BLUE_EGG;
    public static final short BROWN_EGG = (short) Blocks.BROWN_EGG;
    /** 26.3 wind charge. Stack 64; right click consumes one and creates a player wind charge. */
    public static final short WIND_CHARGE = (short) Blocks.WIND_CHARGE;
    public static final short SULFUR_CUBE_BUCKET = (short) Blocks.SULFUR_CUBE_BUCKET;
    /** 당근 낚싯대. 바닐라 max_stack_size 1 · max_damage 25 이며 부스트 1회에 7 이 닳는다. */
    public static final short CARROT_ON_A_STICK = (short) Blocks.CARROT_ON_A_STICK;
    // ── [ZOMBIE-ANIMAL] 상한 달걀 1410. 좀비 닭 전용 순수 아이템이다. ──
    /**
     * 상한 달걀. 던질 수 있고(달걀과 같은 투척 경로) 제련할 수 없으며 <b>부화 확률은 0</b> 이다.
     * 스택 상한도 달걀과 같은 {@link #EGG_STACK_MAX} 를 쓴다 — 같은 손맛의 투척물이라야
     * "달걀인데 썩었다"가 읽히기 때문이다. 계약 정본은 {@code ZombieChickenRules} 다.
     */
    public static final short SPOILED_EGG = (short) Blocks.SPOILED_EGG;
    // ── 피글린 장비 순수 아이템 501(MC Java 1.21.4). ──
    /** 석궁. 바닐라 max_stack_size 1 · max_damage 465. 장전 25 바닐라 틱, 발사에 1 이 닳는다. */
    public static final short CROSSBOW = (short) Blocks.CROSSBOW;
    // ── [STONE-RESIDUAL] 점토 벽돌 765(MC Java 1.21.4). 벽돌 계열의 유일한 순수 아이템이다. ──
    /** 점토 벽돌. 점토 덩이를 구워 얻고, 넷을 2×2 로 모으면 점토 벽돌 블록이 된다. */
    public static final short BRICK = (short) Blocks.BRICK;

    // [ARCHAEOLOGY] 도자기 조각 7종 1962~1968. 값 정본은 {@link Blocks} 이고 여기서는
    // 인벤토리 축이 쓰는 사본만 둔다. 붓은 이미 {@link #BRUSH}(447) 로 있어 새로 만들지 않았다.
    /** [ARCHAEOLOGY] 낚시꾼 도자기 조각(온수 유적). 조각 7종 구간의 시작이다. */
    public static final short ANGLER_POTTERY_SHERD = (short) Blocks.ANGLER_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 칼날 도자기 조각(냉수 유적). */
    public static final short BLADE_POTTERY_SHERD = (short) Blocks.BLADE_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 탐험가 도자기 조각(냉수 유적). */
    public static final short EXPLORER_POTTERY_SHERD = (short) Blocks.EXPLORER_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 애도자 도자기 조각(냉수 유적). */
    public static final short MOURNER_POTTERY_SHERD = (short) Blocks.MOURNER_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 풍요 도자기 조각(냉수 유적). */
    public static final short PLENTY_POTTERY_SHERD = (short) Blocks.PLENTY_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 안식처 도자기 조각(온수 유적). */
    public static final short SHELTER_POTTERY_SHERD = (short) Blocks.SHELTER_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 스니퍼 도자기 조각(온수 유적). 조각 7종 구간의 끝이다. */
    public static final short SNORT_POTTERY_SHERD = (short) Blocks.SNORT_POTTERY_SHERD;
    /** [ARCHAEOLOGY] 사막 피라미드·우물 도자기 조각 6종(append-only 2219~2224). */
    public static final short ARCHER_POTTERY_SHERD = (short) Blocks.ARCHER_POTTERY_SHERD;
    public static final short MINER_POTTERY_SHERD = (short) Blocks.MINER_POTTERY_SHERD;
    public static final short PRIZE_POTTERY_SHERD = (short) Blocks.PRIZE_POTTERY_SHERD;
    public static final short SKULL_POTTERY_SHERD = (short) Blocks.SKULL_POTTERY_SHERD;
    public static final short ARMS_UP_POTTERY_SHERD = (short) Blocks.ARMS_UP_POTTERY_SHERD;
    public static final short BREWER_POTTERY_SHERD = (short) Blocks.BREWER_POTTERY_SHERD;
    // ── [POTION] 양조 순수 아이템 801~815(MC Java 1.21.4). 물약 계열은 전부 스택 1 이고
    // 재료(설탕·발효된 거미 눈·블레이즈 막대·네더 사마귀)는 바닐라와 같이 스택 64 다. ──
    /** 설탕. 사탕수수 1 → 설탕 1. 신속의 물약 재료이자 발효된 거미 눈 재료다. */
    public static final short SUGAR = (short) Blocks.SUGAR;
    /** 발효된 거미 눈. 거미 눈 + 설탕 + 갈색 버섯(바닐라 그대로). */
    public static final short FERMENTED_SPIDER_EYE = (short) Blocks.FERMENTED_SPIDER_EYE;
    /** 어색한 물약. 효과가 없는 양조 중간재다. */
    public static final short AWKWARD_POTION = (short) Blocks.AWKWARD_POTION;
    public static final short POTION_SWIFTNESS = (short) Blocks.POTION_SWIFTNESS;
    public static final short POTION_POISON = (short) Blocks.POTION_POISON;
    public static final short POTION_SLOWNESS = (short) Blocks.POTION_SLOWNESS;
    public static final short POTION_WEAKNESS = (short) Blocks.POTION_WEAKNESS;
    public static final short POTION_HARMING = (short) Blocks.POTION_HARMING;
    public static final short SPLASH_POTION_SWIFTNESS = (short) Blocks.SPLASH_POTION_SWIFTNESS;
    public static final short SPLASH_POTION_POISON = (short) Blocks.SPLASH_POTION_POISON;
    public static final short SPLASH_POTION_SLOWNESS = (short) Blocks.SPLASH_POTION_SLOWNESS;
    public static final short SPLASH_POTION_WEAKNESS = (short) Blocks.SPLASH_POTION_WEAKNESS;
    public static final short SPLASH_POTION_HARMING = (short) Blocks.SPLASH_POTION_HARMING;
    // ── [POTION] 바닐라 원본 레시피 복구용 재료 814~815. 네더가 없어 재료를 대체하는 대신
    // 재료는 바닐라 그대로 두고 **획득 경로만** divergence 다(레이드 승리 보상 · 던전 상자). ──
    /** 블레이즈 막대. 바닐라 양조대 제작식(블레이즈 막대 1 + 조약돌 3)의 재료. 스택 64. */
    public static final short BLAZE_ROD = (short) Blocks.BLAZE_ROD;
    /** 네더 사마귀. 바닐라 어색한 물약(물병 + 네더 사마귀)의 재료. 스택 64. */
    public static final short NETHER_WART = (short) Blocks.NETHER_WART;
    // ── [POTION-UPGRADE] 강화 물약 순수 아이템 816~832(MC Java 1.21.4). 바닐라 강화는
    // 레드스톤(연장) · 발광석 가루(II 등급) 둘뿐이고 한 병에 동시에 걸 수 없다. 어떤 강화가
    // 어떤 물약에 있는지는 바닐라 `PotionBrewing` 표 그대로다 — 나약함은 연장만, 고통은 II 만,
    // 신속·독·감속은 둘 다다. ──
    /** 발광석 가루. II 등급 강화 재료. 발광석 블록이 없어 바닐라 마녀 전리품표로만 얻는다. */
    public static final short GLOWSTONE_DUST = (short) Blocks.GLOWSTONE_DUST;
    public static final short POTION_SWIFTNESS_LONG = (short) Blocks.POTION_SWIFTNESS_LONG;
    public static final short POTION_POISON_LONG = (short) Blocks.POTION_POISON_LONG;
    public static final short POTION_SLOWNESS_LONG = (short) Blocks.POTION_SLOWNESS_LONG;
    public static final short POTION_WEAKNESS_LONG = (short) Blocks.POTION_WEAKNESS_LONG;
    public static final short POTION_SWIFTNESS_II = (short) Blocks.POTION_SWIFTNESS_II;
    public static final short POTION_POISON_II = (short) Blocks.POTION_POISON_II;
    public static final short POTION_SLOWNESS_II = (short) Blocks.POTION_SLOWNESS_II;
    public static final short POTION_HARMING_II = (short) Blocks.POTION_HARMING_II;
    public static final short SPLASH_POTION_SWIFTNESS_LONG =
            (short) Blocks.SPLASH_POTION_SWIFTNESS_LONG;
    public static final short SPLASH_POTION_POISON_LONG =
            (short) Blocks.SPLASH_POTION_POISON_LONG;
    public static final short SPLASH_POTION_SLOWNESS_LONG =
            (short) Blocks.SPLASH_POTION_SLOWNESS_LONG;
    public static final short SPLASH_POTION_WEAKNESS_LONG =
            (short) Blocks.SPLASH_POTION_WEAKNESS_LONG;
    public static final short SPLASH_POTION_SWIFTNESS_II =
            (short) Blocks.SPLASH_POTION_SWIFTNESS_II;
    public static final short SPLASH_POTION_POISON_II = (short) Blocks.SPLASH_POTION_POISON_II;
    public static final short SPLASH_POTION_SLOWNESS_II =
            (short) Blocks.SPLASH_POTION_SLOWNESS_II;
    public static final short SPLASH_POTION_HARMING_II =
            (short) Blocks.SPLASH_POTION_HARMING_II;
    // ── [GUARDIAN] 해저 신전 전리품 931~933. 젖은 스펀지는 설치 가능한 월드 블록이고
    // 프리즈머린 조각·수정은 바닐라와 같은 순수 아이템이다. 셋 다 스택 64. ──
    /** 젖은 스펀지. 엘더 가디언 100% 드랍(바닐라 elder_guardian loot table 마지막 pool). */
    public static final short WET_SPONGE = (short) Blocks.WET_SPONGE;
    /** 프리즈머린 조각. 가디언·엘더 가디언 공통 0~2(약탈 레벨당 +0~1). */
    public static final short PRISMARINE_SHARD = (short) Blocks.PRISMARINE_SHARD;
    /** 프리즈머린 수정. 가디언 두 번째 pool 의 가중치 2 항목(생대구 2 : 수정 2 : 없음 1). */
    public static final short PRISMARINE_CRYSTALS = (short) Blocks.PRISMARINE_CRYSTALS;
    /**
     * [QUARTZ] 네더 석영 961. 바닐라 원천인 네더 석영 광석이 이 게임에 없어 획득 경로가
     * divergence 다 — 레이드 승리 보상 풀과 던전 탐험 상자에서만 나온다. 스택 64.
     */
    public static final short QUARTZ = (short) Blocks.QUARTZ;
    /**
     * [COPPER] 밀랍 986. 벌이 없어 바닐라 원천(벌집/벌집 상자)이 이 게임에 없으므로 획득
     * 경로가 divergence 다 — 레이드 승리 보상 풀과 던전 탐험 상자에서만 나온다. 스택 64.
     * 구리 계열에 밀랍을 발라 산화를 멈추는 데 쓴다.
     */
    public static final short HONEYCOMB = (short) Blocks.HONEYCOMB;
    /**
     * [PROP-MATERIAL] 철 조각 1340. 바닐라 {@code iron_nugget} — 주괴 1/9 값의 재료다.
     * 스택 64 · 내구 없음.
     */
    public static final short IRON_NUGGET = (short) Blocks.IRON_NUGGET;
    /**
     * [PROP-MATERIAL] 구리 조각 1341. 바닐라 1.21.9 {@code copper_nugget} 이며 구리 랜턴의
     * 바닐라 레시피 틀(조각 8 + 횃불)을 성립시키는 재료다. 스택 64 · 내구 없음.
     */
    public static final short COPPER_NUGGET = (short) Blocks.COPPER_NUGGET;
    /**
     * [BRIMSTONE] 마그마 크림 1462. <b>바닐라 실존 아이템</b>이고 화염 저항 물약의 유일한
     * 재료다. 이 저장소에는 네더가 없어 원천이 마그마 큐브가 아니라 <b>황린 잠복자</b> 라는
     * 획득 경로 divergence 를 갖는다({@link Blocks#MAGMA_CREAM} 이 근거를 소유). 스택 64.
     */
    public static final short MAGMA_CREAM = (short) Blocks.MAGMA_CREAM;
    /** [BRIMSTONE] 화염 저항 물약 3:00. 어색한 물약 + 마그마 크림(바닐라 레시피 원본). */
    public static final short POTION_FIRE_RESISTANCE = (short) Blocks.POTION_FIRE_RESISTANCE;
    /** [BRIMSTONE] 화염 저항 물약 (연장) 8:00. 화염 저항 물약 + 레드스톤. */
    public static final short POTION_FIRE_RESISTANCE_LONG =
            (short) Blocks.POTION_FIRE_RESISTANCE_LONG;
    /** [BRIMSTONE] 투척용 화염 저항 물약. */
    public static final short SPLASH_POTION_FIRE_RESISTANCE =
            (short) Blocks.SPLASH_POTION_FIRE_RESISTANCE;
    /** [BRIMSTONE] 투척용 화염 저항 물약 (연장). */
    public static final short SPLASH_POTION_FIRE_RESISTANCE_LONG =
            (short) Blocks.SPLASH_POTION_FIRE_RESISTANCE_LONG;

    // ── [POTION-GAP] 힘 · 수중 호흡 · 도약 · 야간 투시 물약 1760~1779 ────────────────
    // 네 사슬 전부 바닐라 `PotionBrewing` 표 그대로이고 재료를 하나도 신설하지 않았다
    // (블레이즈 가루 1603 · 복어 451 · 토끼 발 357 · 황금 당근 409 가 이미 있었다).
    /** [POTION-GAP] 힘의 물약 3:00. 어색한 물약 + 블레이즈 가루. */
    public static final short POTION_STRENGTH = (short) Blocks.POTION_STRENGTH;
    /** [POTION-GAP] 힘의 물약 (연장) 8:00. */
    public static final short POTION_STRENGTH_LONG = (short) Blocks.POTION_STRENGTH_LONG;
    /** [POTION-GAP] 힘의 물약 II 1:30. */
    public static final short POTION_STRENGTH_II = (short) Blocks.POTION_STRENGTH_II;
    /** [POTION-GAP] 투척용 힘의 물약. */
    public static final short SPLASH_POTION_STRENGTH = (short) Blocks.SPLASH_POTION_STRENGTH;
    /** [POTION-GAP] 투척용 힘의 물약 (연장). */
    public static final short SPLASH_POTION_STRENGTH_LONG =
            (short) Blocks.SPLASH_POTION_STRENGTH_LONG;
    /** [POTION-GAP] 투척용 힘의 물약 II. */
    public static final short SPLASH_POTION_STRENGTH_II =
            (short) Blocks.SPLASH_POTION_STRENGTH_II;
    /** [POTION-GAP] 수중 호흡 물약 3:00. 어색한 물약 + 복어. */
    public static final short POTION_WATER_BREATHING = (short) Blocks.POTION_WATER_BREATHING;
    /** [POTION-GAP] 수중 호흡 물약 (연장) 8:00. */
    public static final short POTION_WATER_BREATHING_LONG =
            (short) Blocks.POTION_WATER_BREATHING_LONG;
    /** [POTION-GAP] 투척용 수중 호흡 물약. */
    public static final short SPLASH_POTION_WATER_BREATHING =
            (short) Blocks.SPLASH_POTION_WATER_BREATHING;
    /** [POTION-GAP] 투척용 수중 호흡 물약 (연장). */
    public static final short SPLASH_POTION_WATER_BREATHING_LONG =
            (short) Blocks.SPLASH_POTION_WATER_BREATHING_LONG;
    /** [POTION-GAP] 도약의 물약 3:00. 어색한 물약 + 토끼 발. */
    public static final short POTION_LEAPING = (short) Blocks.POTION_LEAPING;
    /** [POTION-GAP] 도약의 물약 (연장) 8:00. */
    public static final short POTION_LEAPING_LONG = (short) Blocks.POTION_LEAPING_LONG;
    /** [POTION-GAP] 도약의 물약 II 1:30. */
    public static final short POTION_LEAPING_II = (short) Blocks.POTION_LEAPING_II;
    /** [POTION-GAP] 투척용 도약의 물약. */
    public static final short SPLASH_POTION_LEAPING = (short) Blocks.SPLASH_POTION_LEAPING;
    /** [POTION-GAP] 투척용 도약의 물약 (연장). */
    public static final short SPLASH_POTION_LEAPING_LONG =
            (short) Blocks.SPLASH_POTION_LEAPING_LONG;
    /** [POTION-GAP] 투척용 도약의 물약 II. */
    public static final short SPLASH_POTION_LEAPING_II = (short) Blocks.SPLASH_POTION_LEAPING_II;
    /** [POTION-GAP] 야간 투시 물약 3:00. 어색한 물약 + 황금 당근. */
    public static final short POTION_NIGHT_VISION = (short) Blocks.POTION_NIGHT_VISION;
    /** [POTION-GAP] 야간 투시 물약 (연장) 8:00. */
    public static final short POTION_NIGHT_VISION_LONG =
            (short) Blocks.POTION_NIGHT_VISION_LONG;
    /** [POTION-GAP] 투척용 야간 투시 물약. */
    public static final short SPLASH_POTION_NIGHT_VISION =
            (short) Blocks.SPLASH_POTION_NIGHT_VISION;
    /** [POTION-GAP] 투척용 야간 투시 물약 (연장). */
    public static final short SPLASH_POTION_NIGHT_VISION_LONG =
            (short) Blocks.SPLASH_POTION_NIGHT_VISION_LONG;
    /**
     * [HARNESS] 흰색 하네스 1790. <b>바닐라 실존 아이템</b>(1.21.6 {@code harness})이며 획득
     * 경로도 바닐라와 같은 제작 하나뿐이다 — 가죽 3 + 유리 2 + 그 색 양털 1
     * ({@link Blocks#WHITE_HARNESS} 이 구간과 근거를 소유). 안장과 같은 <b>스택 1</b>이다.
     */
    public static final short WHITE_HARNESS = (short) Blocks.WHITE_HARNESS;
    public static final short ORANGE_HARNESS = (short) Blocks.ORANGE_HARNESS;
    public static final short MAGENTA_HARNESS = (short) Blocks.MAGENTA_HARNESS;
    public static final short LIGHT_BLUE_HARNESS = (short) Blocks.LIGHT_BLUE_HARNESS;
    public static final short YELLOW_HARNESS = (short) Blocks.YELLOW_HARNESS;
    public static final short LIME_HARNESS = (short) Blocks.LIME_HARNESS;
    public static final short PINK_HARNESS = (short) Blocks.PINK_HARNESS;
    public static final short GRAY_HARNESS = (short) Blocks.GRAY_HARNESS;
    public static final short LIGHT_GRAY_HARNESS = (short) Blocks.LIGHT_GRAY_HARNESS;
    public static final short CYAN_HARNESS = (short) Blocks.CYAN_HARNESS;
    public static final short PURPLE_HARNESS = (short) Blocks.PURPLE_HARNESS;
    public static final short BLUE_HARNESS = (short) Blocks.BLUE_HARNESS;
    public static final short BROWN_HARNESS = (short) Blocks.BROWN_HARNESS;
    public static final short GREEN_HARNESS = (short) Blocks.GREEN_HARNESS;
    public static final short RED_HARNESS = (short) Blocks.RED_HARNESS;
    /** [HARNESS] 검은색 하네스 1805. 16색 구간의 마지막 ID 다. */
    public static final short BLACK_HARNESS = (short) Blocks.BLACK_HARNESS;

    /** [HARNESS] 하네스 아이템인가. 색 구간 하나라 {@link Blocks#isHarnessItem} 위임이다. */
    public static boolean isHarness(short type) {
        return Blocks.isHarnessItem(Short.toUnsignedInt(type));
    }

    /**
     * [HARNESS] 하네스 아이템 → MC {@code DyeColor} 네트워크 ID(0..15). 하네스가 아니면 -1.
     * 해피 가스트가 저장하는 하네스 색과 <b>같은 어휘</b>다.
     */
    public static int harnessDyeColor(short type) {
        return Blocks.harnessDyeColor(Short.toUnsignedInt(type));
    }

    /**
     * [WAVE-86-97] 브리즈 막대 1480. <b>바닐라 실존 아이템</b>이고 획득 경로도 바닐라와 같은
     * 브리즈 처치 드랍 하나뿐이다({@link Blocks#BREEZE_ROD} 이 근거를 소유). 스택 64.
     */
    public static final short BREEZE_ROD = (short) Blocks.BREEZE_ROD;
    /**
     * [WAVE-86-97] 팬텀 막 1481. <b>바닐라 실존 아이템</b>이고 획득 경로는 바닐라의 원천 중
     * 하나인 <b>팬텀 처치</b>(플레이어 처치 0~1)다({@link Blocks#PHANTOM_MEMBRANE}). 스택 64.
     */
    public static final short PHANTOM_MEMBRANE = (short) Blocks.PHANTOM_MEMBRANE;
    /**
     * [WAVE-86-97] 스컬크 촉매. 워든 처치 드랍(1개)이 새로 여는 획득 경로이며 아이템 ID 는
     * 이미 있는 <b>월드 블록</b> 1202 를 그대로 쓴다(같은 블록에 두 ID 를 만들지 않는다).
     */
    public static final short SCULK_CATALYST = (short) Blocks.SCULK_CATALYST;

    /** 수지 덩어리 1511. 설치되지 않는 크리킹 하트 제작 재료다. */
    public static final short RESIN_CLUMP = (short) Blocks.RESIN_CLUMP;

    // ── [CHEST-FAMILY] 상자류 컨테이너 재료. 근거는 {@link Blocks} 의 같은 이름이 소유한다. ──
    /** 블레이즈 가루 1603. 블레이즈 막대 1 → 2(바닐라 그대로). 엔더의 눈 재료. */
    public static final short BLAZE_POWDER = (short) Blocks.BLAZE_POWDER;
    /** 엔더의 눈 1604. 엔더 진주 1 + 블레이즈 가루 1 → 1(바닐라 그대로). 엔더 상자 재료. */
    public static final short EYE_OF_ENDER = (short) Blocks.EYE_OF_ENDER;
    /**
     * 셜커 껍데기 1613. 엔드도 셜커도 없어 획득 경로가 <b>던전 탐험 상자</b>로 신설된다
     * ({@link Blocks#SHULKER_SHELL} 이 근거와 레이드 풀 보류 사유를 소유). 셜커 상자 재료.
     */
    public static final short SHULKER_SHELL = (short) Blocks.SHULKER_SHELL;

    // ── [COOKING] 요리 계열. 근거는 전부 {@link Blocks} 의 같은 이름 상수가 소유한다. ──
    /** 케이크 1630. 유일하게 <b>월드 블록</b>이라 설치되며, 아이템으로는 회수되지 않는다. */
    public static final short CAKE = (short) Blocks.CAKE;
    /** 코코아 콩 1631. 쿠키·갈색 염료 재료. 획득 경로는 탐험 전리품뿐이다. */
    public static final short COCOA_BEANS = (short) Blocks.COCOA_BEANS;
    /** 쿠키 1632. 허기 2 · 포화 0.4. */
    public static final short COOKIE = (short) Blocks.COOKIE;
    /** 호박 파이 1633. 허기 8 · 포화 4.8. */
    public static final short PUMPKIN_PIE = (short) Blocks.PUMPKIN_PIE;
    /** 수상한 스튜(양귀비) 1634. 허기 6 · 포화 7.2 · 그릇 반환 · 야간 투시 5초. */
    public static final short SUSPICIOUS_STEW_POPPY = (short) Blocks.SUSPICIOUS_STEW_POPPY;
    /** 수상한 스튜(민들레) 1635. 허기 6 · 포화 7.2 · 그릇 반환 · 포만감 0.35초. */
    public static final short SUSPICIOUS_STEW_DANDELION = (short) Blocks.SUSPICIOUS_STEW_DANDELION;
    /** 말린 다시마 1636. 허기 1 · 포화 0.6. 다시마 제련 산출이다. */
    public static final short DRIED_KELP = (short) Blocks.DRIED_KELP;

    /**
     * [CROP-BERRY] 독 감자 1700. 허기 2 · 포화 1.2 · 60% 확률 독 I 5초([A] 원문).
     * 다 자란 감자 작물 수확 시 2% 확률로 하나 더 나오는 것이 유일한 획득 경로다.
     */
    public static final short POISONOUS_POTATO = (short) Blocks.POISONOUS_POTATO;

    // ── [GOLD-FOOD] 황금·특수 식품 1730~1733. 근거는 {@link Blocks} 의 같은 이름이 소유한다. ──
    /**
     * 마법이 부여된 황금 사과 1730. 허기 4 · 포화 9.6 · alwaysEdible 이고 <b>제작식이 없다</b>
     * (바닐라 1.9 에서 삭제). 효과는 {@code PlayerTickState.consumeEnchantedGoldenApple}.
     */
    public static final short ENCHANTED_GOLDEN_APPLE = (short) Blocks.ENCHANTED_GOLDEN_APPLE;
    /**
     * 후렴과 1731. 허기 4 · 포화 2.4 · alwaysEdible 이며 먹으면 ±8 블록 안 안전 지점으로
     * 순간이동한다. 스택 64.
     */
    public static final short CHORUS_FRUIT = (short) Blocks.CHORUS_FRUIT;
    /** [VOID-END] 튀긴 후렴과 2301. 후렴과 제련 산물(바닐라 smelting 0.1xp)이며 먹을 수 없다. */
    public static final short POPPED_CHORUS_FRUIT = (short) Blocks.POPPED_CHORUS_FRUIT;
    /** [END-CITY] 강한 치유의 물약 2309(마시는 물약, 스택 1). 즉시 회복 II. */
    public static final short POTION_STRONG_HEALING = (short) Blocks.POTION_STRONG_HEALING;
    /** [END-CITY] 아이템 액자 2303(순수 아이템, 스택 64). 블록 면에 아이템 액자 개체를 건다. */
    public static final short ITEM_FRAME = (short) Blocks.ITEM_FRAME;
    /** [END-CITY] 드래곤 머리 2304(월드 블록 아이템, 스택 64). 머리 칸에 쓸 수 있다(Equippable HEAD). */
    public static final short DRAGON_HEAD = (short) Blocks.DRAGON_HEAD;
    /** [DRAGON] 드래곤 알 2306(월드 블록 아이템, 스택 64). */
    public static final short DRAGON_EGG = (short) Blocks.DRAGON_EGG;
    /** [DRAGON] 엔드 수정 2307(순수 아이템, 스택 64). 흑요석·기반암 위에 엔드 수정 개체를 놓는다. */
    public static final short END_CRYSTAL = (short) Blocks.END_CRYSTAL;
    /** [DRAGON] 드래곤의 숨결 2308(순수 아이템, 스택 64). 투척 물약을 잔류형으로 양조하는 재료다. */
    /**
     * 꿀이 든 병 1732. 허기 6 · 포화 1.2 · 마시는 아이템이며 독을 해제하고 빈 유리병을
     * 돌려준다. 바닐라 max_stack_size 16.
     */
    public static final short HONEY_BOTTLE = (short) Blocks.HONEY_BOTTLE;
    /** 황금 민들레 1733. 월드 블록(교차 평면 꽃)이며 민들레 1 + 금 조각 8 로 만든다. */
    public static final short GOLDEN_DANDELION = (short) Blocks.GOLDEN_DANDELION;

    // 도구 티어(§2·§10.2-S2b): 돌·철 각 곡괭이/도끼/삽/검. 전부 스택 1·내구 있음.
    public static final short STONE_TIER_MIN = 270;
    public static final short STONE_PICKAXE = STONE_TIER_MIN;
    public static final short STONE_AXE = 271;
    public static final short STONE_SHOVEL = 272;
    public static final short STONE_SWORD = 273;
    public static final short STONE_TIER_MAX = STONE_SWORD;
    public static final short IRON_TIER_MIN = 274;
    public static final short IRON_PICKAXE = IRON_TIER_MIN;
    public static final short IRON_AXE = 275;
    public static final short IRON_SHOVEL = 276;
    public static final short IRON_SWORD = 277;
    public static final short IRON_TIER_MAX = IRON_SWORD;
    /** 나무 티어 초기 내구도(§4). 0 이 되면 아이템이 사라집니다. */
    public static final int INITIAL_DURABILITY = 60;
    /** 돌 티어 초기 내구도(§10.2-S2b). */
    public static final int STONE_DURABILITY = 120;
    /** 철 티어 초기 내구도(§10.2-S2b). */
    public static final int IRON_DURABILITY = 240;
    public static final int GOLD_DURABILITY = 30;
    public static final int DIAMOND_DURABILITY = 1560;
    public static final int COPPER_DURABILITY = 190;
    public static final int NETHERITE_DURABILITY = 2031;
    public static final int FLINT_AND_STEEL_DURABILITY = 64;
    public static final int SHIELD_DURABILITY = 336;
    /**
     * [DIAMOND-SHIELD][C] 다이아 방패 초기 내구도. 바닐라에 대응물이 없으므로 이 저장소의
     * 기존 티어 논리에서 파생한다: <b>다이아 티어 장비의 내구는 {@link #DIAMOND_DURABILITY}
     * 하나가 정본</b>(곡괭이·도끼·삽·검이 전부 이 값을 읽는다)이므로 다이아 방패도 표를
     * 늘리지 않고 같은 값을 읽는다. 결과적으로 철 방패 336 의 약 4.64 배이며, 리터럴을 새로
     * 만들지 않는 것 자체가 계약의 일부다 — 다이아 티어 내구가 바뀌면 방패도 함께 따라간다.
     */
    public static final int DIAMOND_SHIELD_DURABILITY = DIAMOND_DURABILITY;
    /**
     * [SHIELD-FAMILY][C] 가죽 방패 초기 내구도. 바닐라에 대응물이 없어 이 저장소의 기존 티어
     * 논리에서 파생한다 — 다이아 방패가 {@link #DIAMOND_DURABILITY} 를 그대로 읽은 선례와
     * 같은 수술이다.
     *
     * <p>다만 <b>가죽에는 스칼라 티어 상수가 없다</b>: 이 저장소의 가죽 장비는 방어구 네 조각
     * 뿐이고 그 내구는 {@link #initialDurability} 의 switch 에 55 · 80 · 75 · 65 로 조각마다
     * 따로 적혀 있다(가죽 티어를 한 값으로 요약한 상수가 없다). 그래서 <b>티어의 몸통 조각</b>
     * 인 가죽 튜닉({@link #LEATHER_CHESTPLATE})의 80 을 읽는다 — 방패도 몸을 가리는 한 장이라
     * 조각 중 몸통이 가장 가까운 대응물이고, 투구·장화의 값을 고르면 그쪽이 임의 선택이 된다.
     */
    public static final int LEATHER_SHIELD_DURABILITY = 80;
    /**
     * [SHIELD-FAMILY] 돌 방패 초기 내구도. 돌 티어에는 스칼라 정본
     * {@link #STONE_DURABILITY} 가 있으므로 리터럴을 새로 만들지 않고 그대로 읽는다
     * (다이아 방패와 완전히 같은 파생이며, 티어 내구가 바뀌면 방패도 함께 따라간다).
     */
    public static final int STONE_SHIELD_DURABILITY = STONE_DURABILITY;
    /**
     * [SHIELD-FAMILY][C] 구리 방패 초기 내구도. <b>구리에는 티어 스칼라 정본이 없다</b> —
     * 이 저장소에 구리 도구·방어구가 통째로 없고, 구리 티어의 내구가 적힌 자리는
     * {@link #SPEAR_DURABILITY} 의 구리 창 190 <b>하나뿐</b>이다([B] 창 핀 §1 표).
     * 그래서 그 값을 이 티어의 내구로 삼는다.
     *
     * <p>창 표를 참조하지 않고 자기 리터럴로 두는 이유: 창 표는 뒤 버전 위키에서 옮긴 [B]
     * 값이라 핀이 정정되면 움직일 수 있는데, 그때 <b>창의 정정이 방패를 조용히 끌고 가면
     * 안 된다</b>. 두 값이 같다는 사실은 {@code ShieldFamilyTest} 가 못박아 둔다.
     */
    public static final int COPPER_SHIELD_DURABILITY = 190;
    /**
     * [SHIELD-FAMILY] 금 방패 초기 내구도. 금 티어에는 스칼라 정본 {@link #GOLD_DURABILITY}
     * 가 있으므로 그대로 읽는다. 결과적으로 이 가족에서 <b>가장 낮은</b> 내구이며, 금이 가장
     * 잘 닳는다는 이 저장소(와 바닐라)의 금 티어 규약과 같은 방향이다.
     */
    public static final int GOLD_SHIELD_DURABILITY = GOLD_DURABILITY;
    /**
     * [TRIDENT][A] 삼지창 초기 내구도(1.21.4 {@code trident} 의 {@code max_damage} 250).
     * 투척 1회가 {@link #TRIDENT_THROW_DAMAGE} 를 깎는다.
     */
    public static final int TRIDENT_DURABILITY = 250;
    /** [TURTLE][A] {@code turtle_helmet} 의 max_damage. */
    public static final int TURTLE_SHELL_DURABILITY = 275;
    /** [TRIDENT][A] 삼지창 투척 1회가 깎는 내구. 바닐라 {@code ThrownTrident} 와 같은 1 이다. */
    public static final int TRIDENT_THROW_DAMAGE = 1;
    /**
     * [TRIDENT][B] 투척이 성립하는 최소 충전 시간(바닐라 {@code TridentItem.releaseUsing} 의
     * {@code if (i < 10) return}). 미달이면 아무 일도 일어나지 않고 삼지창은 손에 남는다.
     */
    public static final int TRIDENT_THROW_MC_TICKS = 10;
    /**
     * 10 TPS 권위 틱으로 환산한 투척 문턱. 10 MC tick = 5 권위 틱이라 나머지가 없다
     * (석궁 25 MC tick 과 달리 반 틱 divergence 가 생기지 않는다).
     */
    public static final int TRIDENT_THROW_AUTHORITY_TICKS =
            (TRIDENT_THROW_MC_TICKS + 1) / 2;
    /**
     * [TRIDENT][B] 던진 삼지창의 초기 속력 배율. 바닐라 {@code TridentItem} 은
     * {@code shootFromRotation(..., 2.5F, 1.0F)} 로 2.5, 완전히 당긴 활은 3.0 이라
     * 완충 활 대비 {@code 2.5/3.0} 배다. 석궁({@link #CROSSBOW_VELOCITY_RATIO})과 같은 규약으로
     * {@code Skeleton.ARROW_SPEED} 에 곱해 같은 탄도 모델 위에서 바닐라 속도비를 보존한다.
     */
    public static final double TRIDENT_VELOCITY_RATIO = 2.5 / 3.0;
    /**
     * [SPEAR][B] 창 여섯 티어의 초기 내구도(핀 §1 표의 내구도 열). ID 순서
     * (나무 · 돌 · 구리 · 철 · 금 · 다이아)이며 {@link #initialDurability(short)} 가
     * {@code type - WOODEN_SPEAR} 로 색인한다.
     *
     * <p>검 티어 내구와 대체로 같지만 <b>구리 190 은 이 저장소에 없던 값</b>이고
     * <b>다이아 1561 은 다이아 검 1560 과 1 다르다</b> — 위키 표가 그렇게 적었으므로
     * 기존 티어 상수로 접지 않는다(접으면 그쪽이 divergence 다). 그래서 이 표는 다이아
     * 방패({@link #DIAMOND_SHIELD_DURABILITY})처럼 기존 상수를 재사용하지 못하고 자기
     * 리터럴을 가진다.
     */
    public static final int[] SPEAR_DURABILITY = { 59, 131, 190, 250, 32, 1561 };
    public static final int BOW_DURABILITY = 384;
    public static final int SHEARS_DURABILITY = 238;
    /** 낚싯대 초기 내구도(MC Java 1.21.4 fishing_rod max_damage 64). */
    public static final int FISHING_ROD_DURABILITY = 64;
    public static final int BRUSH_DURABILITY = 64;
    public static final int WOLF_ARMOR_DURABILITY = 64;
    /** 겉날개 초기 내구도(MC Java 1.21.4 elytra max_damage 432). */
    public static final int ELYTRA_DURABILITY = 432;
    /** 바닐라 carrot_on_a_stick 의 max_damage. 부스트 1회가 7 을 깎으므로 4회에 파괴된다. */
    public static final int CARROT_ON_A_STICK_DURABILITY = 25;
    /** 바닐라 Pig 부스트 1회의 {@code hurtAndBreak} 량. */
    public static final int CARROT_ON_A_STICK_BOOST_DAMAGE = 7;
    /** 석궁 초기 내구도(MC Java 1.21.4 crossbow max_damage 465). */
    public static final int CROSSBOW_DURABILITY = 465;
    /**
     * 석궁 장전에 필요한 바닐라 틱({@code CrossbowItem.getChargeDuration} = 25 - 5*QuickCharge).
     * 인챈트 없는 장전 시간은 25 MC 틱이다([ENCHANT-WIDE] 빠른 장전은 EnchantmentRules.crossbowChargeMcTicks).
     */
    public static final int CROSSBOW_CHARGE_MC_TICKS = 25;
    /**
     * 10 TPS 권위 틱으로 환산한 장전 시간. 25 MC tick = 12.5 권위 틱이라 올림해 13 틱이며
     * 반 틱(+1 MC tick)은 권위 틱 격자가 만드는 divergence 다(MC-REFERENCE 「피글린」 절).
     */
    public static final int CROSSBOW_CHARGE_AUTHORITY_TICKS =
            (CROSSBOW_CHARGE_MC_TICKS + 1) / 2;
    /** 장전된 석궁을 한 번 발사할 때 닳는 내구({@code CrossbowItem.shootProjectile} 화살 1). */
    public static final int CROSSBOW_SHOT_DAMAGE = 1;
    /**
     * 석궁 볼트 초기 속력 배율. 바닐라 석궁 화살은 3.15, 완전히 당긴 활은 3.0 이라
     * 완충 활 대비 1.05 배다. WebCraft 는 활 완충을 {@code Skeleton.ARROW_SPEED} 로 쓰므로
     * 같은 비율을 곱해 같은 탄도 모델 위에서 바닐라 속도비를 보존한다.
     */
    public static final double CROSSBOW_VELOCITY_RATIO = 1.05;
    /**
     * 다이아 낚싯대 초기 내구도. 바닐라에는 없는 WebCraft 계약값이라
     * {@code docs/MC-REFERENCE.md} 의 레이드 보상 절이 근거를 소유한다.
     */
    public static final int DIAMOND_FISHING_ROD_DURABILITY = 512;
    /**
     * 뿔피리·오르골 재사용 대기 시간(authority tick, 10 TPS 기준 10초). 효과가 0 이라
     * 밸런스가 아니라 순수 스팸 방지값이며 정적판이 같은 리터럴을 쓴다.
     */
    public static final int TROPHY_INSTRUMENT_COOLDOWN_TICKS = 100;
    /**
     * 활공을 계속하려면 남아 있어야 하는 최소 내구도. 바닐라 {@code ElytraItem.isFlyEnabled} 는
     * {@code damage < max_damage - 1} 일 때만 활공을 허용하므로 내구 1 에서 파손 직전에 멈춘다.
     */
    public static final int ELYTRA_MIN_FLYABLE_DURABILITY = 2;

    private static long nextInventoryIdentity;

    private static synchronized long allocateInventoryIdentity() {
        if (nextInventoryIdentity == Long.MAX_VALUE) {
            throw new IllegalStateException("inventory identity space is exhausted");
        }
        return ++nextInventoryIdentity;
    }

    private final long inventoryIdentity = allocateInventoryIdentity();
    private String persistenceLineageDigest;
    private final short[] itemType = new short[SLOTS];
    private final int[] count = new int[SLOTS];
    // 내구도: 도구/검 슬롯만 1~60 의미. 그 외(빈 칸·블록·막대)는 0(=내구 없음).
    private final int[] durability = new int[SLOTS];
    // [SURV-X] 스택별 인챈트 압축 마스크(7종 × 4비트). durability 와 완전히 같은 경로로 흐른다.
    private final long[] enchantments = new long[SLOTS];
    // 채워진 지도 스택이 가리키는 월드 지도 ID. 다른 아이템과 빈 칸은 반드시 0이다.
    private final int[] mapIds = new int[SLOTS];
    // [SHULKER-CONTENTS] 셜커 상자 스택이 가리키는 27칸 참조 ID. 다른 아이템과 빈 칸은 반드시 0이다.
    // mapIds 와 완전히 같은 경로로 흐르므로 다섯 배열 헬퍼가 둘을 나란히 옮긴다.
    private final int[] shulkerIds = new int[SLOTS];
    private final String[] bucketMobData = new String[SLOTS];
    private final String[] itemComponentData = new String[SLOTS];
    private StackSnapshot offhand = StackSnapshot.EMPTY;
    // 플레이 인벤토리는 36칸이고 착용 상태는 별도 4칸이다. DB는 같은 element collection의
    // 슬롯 36~39에 착용품을 저장하지만, 클라이언트 인벤토리 프로토콜에는 섞지 않는다.
    private final short[] equippedType = new short[ArmorSlot.values().length];
    private final int[] equippedDurability = new int[ArmorSlot.values().length];
    private final long[] equippedEnchantments = new long[ArmorSlot.values().length];
    private final String[] equippedItemComponentData = new String[ArmorSlot.values().length];
    private final short[] craftingType = new short[9];
    private final int[] craftingCount = new int[9];
    private final int[] craftingDurability = new int[9];
    private final long[] craftingEnchantments = new long[9];
    private final int[] craftingMapIds = new int[9];
    private final int[] craftingShulkerIds = new int[9];
    private final String[] craftingBucketMobData = new String[9];
    private final String[] craftingItemComponentData = new String[9];
    private int craftingGridSize;
    private int craftingSlotCount;
    /**
     * [STONECUT] 석재 절단기 앞에서 연 세션인가. 격자는 입력 한 칸(슬롯 0)만 쓰고, 결과는
     * 격자가 아니라 아래 선택 레시피가 정한다(같은 입력에서 하류 형상 여럿이 나온다).
     */
    private boolean craftingStonecutter;
    /** [STONECUT] 절단 세션이 지금 고른 레시피 id. 없으면 null 이고 결과 칸은 비어 있다. */
    private String craftingSelection;
    private short cursorType;
    private int cursorCount;
    private int cursorDurability;
    private long cursorEnchantments;
    private int cursorMapId;
    private int cursorShulkerId;
    private String cursorBucketMobData;
    private String cursorItemComponentData;
    // [SURV-X] 내구성(Unbreaking) 판정용 권위 난수. 테스트는 고정 시드를 주입한다.
    private Random durabilityRandom = new Random();
    // 제작 결과가 가득 찬 인벤토리의 커서에 올라가 36칸으로 잠시 접히지 않을 때는 직전의
    // 손실 없는 상태를 저장한다. 정상 종료 시 실제 아이템 또는 월드 드랍으로 정산된다.
    private int selectedSlot;
    // 영속 대상(itemType/count/durability/selectedSlot) 변경 카운터. 주기 저장의 dirty 판정에 쓴다.
    // over-count(무변경 시 증가)는 불필요 저장일 뿐 안전하고, under-count 만 위험하다.
    private long revision;
    private long handMutationNonce;
    /** 비동기 보상 정산이 포착한 persistence revision. 해제/설치 전에는 일반 변이를 승인하지 않습니다. */
    private long settlementLeaseRevision = -1L;
    private long nextSettlementLeaseNonce;
    private long settlementLeaseNonce;
    private String settlementLeaseSourceDigest;
    private enum PersistenceLifecycle { OPEN, BASELINE_BOUND, TERMINAL }
    private PersistenceLifecycle persistenceLifecycle = PersistenceLifecycle.OPEN;
    private boolean persistenceRevisionBound;

    private enum StationPlanKind { ANVIL, GRINDSTONE, LOOM, CARTOGRAPHY }
    private StationPlanKind stationPlanKind;
    private long stationPlanRevision = -1L;
    private String stationPlanInputDigest;
    private String stationPlanName;
    private boolean stationPlanCreative;
    private LoomRules.Pattern stationPlanPattern;
    private CartographyAuthorityWitness stationPlanMapWitness;

    private void bindPersistenceRevision(long persistedRevision) {
        if (persistedRevision < 0) {
            throw new IllegalArgumentException("inventory persistence revision must be non-negative");
        }
        if (persistenceRevisionBound || persistenceLifecycle != PersistenceLifecycle.OPEN) {
            throw new IllegalStateException("inventory revision baseline can only be restored once");
        }
        revision = persistedRevision;
        persistenceRevisionBound = true;
        persistenceLifecycle = persistedRevision == Long.MAX_VALUE
                ? PersistenceLifecycle.TERMINAL : PersistenceLifecycle.BASELINE_BOUND;
    }

    private void preflightRevisionCapacity() {
        requireMutationLeaseFree();
        if (persistenceLifecycle == PersistenceLifecycle.TERMINAL
                || revision == Long.MAX_VALUE) {
            throw new IllegalStateException("inventory persistence revision is exhausted");
        }
    }

    private void advancePersistenceRevision() {
        preflightRevisionCapacity();
        revision = Math.incrementExact(revision);
        handMutationNonce = Math.incrementExact(handMutationNonce);
        persistenceRevisionBound = true;
        if (persistenceLifecycle == PersistenceLifecycle.OPEN) {
            persistenceLifecycle = PersistenceLifecycle.BASELINE_BOUND;
        }
        if (revision == Long.MAX_VALUE) persistenceLifecycle = PersistenceLifecycle.TERMINAL;
    }

    private boolean settlementMutationBlocked() {
        return settlementLeaseRevision >= 0;
    }

    private void requireMutationLeaseFree() {
        if (settlementMutationBlocked()) {
            throw new IllegalStateException("inventory mutation is blocked by settlement lease");
        }
    }

    /** 기본 인벤토리: 슬롯 0 에 검 1개(내구 60), 나머지 비어 있음, 선택 슬롯 0. */
    private com.gameexpert.engine.ChestInventory merchantPayments = new com.gameexpert.engine.ChestInventory(2);
    private final ContainerAccess merchantPaymentAccess = new MerchantPaymentAccess();
    private boolean merchantMutationInProgress;

    public synchronized ContainerAccess merchantPayments() {
        return merchantPaymentAccess;
    }

    private StackSnapshot[] merchantPaymentSnapshot() {
        return java.util.stream.IntStream.range(0, 2).mapToObj(slot ->
                merchantPayments.count(slot) == 0 ? StackSnapshot.EMPTY : new StackSnapshot(
                        merchantPayments.itemType(slot), merchantPayments.count(slot),
                        merchantPayments.durability(slot), merchantPayments.enchantments(slot),
                        merchantPayments.mapId(slot), merchantPayments.shulkerId(slot),
                        merchantPayments.bucketMobData(slot), merchantPayments.itemComponentData(slot)))
                .toArray(StackSnapshot[]::new);
    }

    private void restoreMerchantPayments(StackSnapshot[] stacks) {
        merchantPayments = new com.gameexpert.engine.ChestInventory(2);
        ContainerAccess target = ContainerAccess.of(merchantPayments);
        for (int slot = 0; slot < 2; slot++) {
            StackSnapshot stack = stacks[slot];
            if (stack.isEmpty()) continue;
            target.put(slot, stack.itemType(), stack.count(), stack.durability(), stack.enchantments(),
                    stack.mapId(), stack.shulkerId(), stack.bucketMobData(), stack.itemComponentData());
        }
    }

    /** A merchant transfer changes visible slots and the private payment slots as one mutation. */
    public synchronized boolean mutateMerchantPayments(java.util.function.BooleanSupplier mutation) {
        if (settlementMutationBlocked()) return false;
        if (merchantMutationInProgress) return mutation.getAsBoolean();
        preflightRevisionCapacity();
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        long beforeRevision = revision;
        long beforeHandNonce = handMutationNonce;
        boolean beforeBound = persistenceRevisionBound;
        PersistenceLifecycle beforeLifecycle = persistenceLifecycle;
        merchantMutationInProgress = true;
        try {
            boolean changed = mutation.getAsBoolean();
            merchantMutationInProgress = false;
            if (!changed || currentNormalizedInventory() == null) {
                before.restore(this);
                revision = beforeRevision;
                handMutationNonce = beforeHandNonce;
                persistenceRevisionBound = beforeBound;
                persistenceLifecycle = beforeLifecycle;
                return false;
            }
            if (revision == beforeRevision) advancePersistenceRevision();
            return true;
        } catch (RuntimeException | Error failure) {
            before.restore(this);
            revision = beforeRevision;
            handMutationNonce = beforeHandNonce;
            persistenceRevisionBound = beforeBound;
            persistenceLifecycle = beforeLifecycle;
            throw failure;
        } finally {
            merchantMutationInProgress = false;
        }
    }

    private final class MerchantPaymentAccess implements ContainerAccess {
        private ContainerAccess delegate() { return ContainerAccess.of(merchantPayments); }
        @Override public int slotCount() {
            synchronized (PlayerInventory.this) { return delegate().slotCount(); }
        }
        @Override public short itemType(int slot) {
            synchronized (PlayerInventory.this) { return delegate().itemType(slot); }
        }
        @Override public int count(int slot) {
            synchronized (PlayerInventory.this) { return delegate().count(slot); }
        }
        @Override public int durability(int slot) {
            synchronized (PlayerInventory.this) { return delegate().durability(slot); }
        }
        @Override public long enchantments(int slot) {
            synchronized (PlayerInventory.this) { return delegate().enchantments(slot); }
        }
        @Override public int mapId(int slot) {
            synchronized (PlayerInventory.this) { return delegate().mapId(slot); }
        }
        @Override public int shulkerId(int slot) {
            synchronized (PlayerInventory.this) { return delegate().shulkerId(slot); }
        }
        @Override public String bucketMobData(int slot) {
            synchronized (PlayerInventory.this) { return delegate().bucketMobData(slot); }
        }
        @Override public String itemComponentData(int slot) {
            synchronized (PlayerInventory.this) { return delegate().itemComponentData(slot); }
        }
        @Override public boolean acceptsShulkerBoxes() {
            synchronized (PlayerInventory.this) { return delegate().acceptsShulkerBoxes(); }
        }
        @Override public boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId) {
            synchronized (PlayerInventory.this) { return delegate().acceptsStack(itemEnchantments, itemMapId, itemShulkerId); }
        }
        @Override public boolean acceptsStack(long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData, String itemComponentData) {
            synchronized (PlayerInventory.this) { return delegate().acceptsStack(itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponentData); }
        }
        @Override public int roomFor(int slot, short type, int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId) {
            synchronized (PlayerInventory.this) { return delegate().roomFor(slot, type, itemDurability, itemEnchantments, itemMapId, itemShulkerId); }
        }
        @Override public int roomFor(int slot, short type, int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData, String itemComponentData) {
            synchronized (PlayerInventory.this) { return delegate().roomFor(slot, type, itemDurability, itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponentData); }
        }
        @Override public int take(int slot, int amount) {
            synchronized (PlayerInventory.this) {
                if (settlementMutationBlocked()) return 0;
                if (merchantMutationInProgress) return delegate().take(slot, amount);
                int[] changed = new int[1];
                boolean accepted = mutateMerchantPayments(() -> {
                    changed[0] = delegate().take(slot, amount);
                    return changed[0] > 0;
                });
                return accepted ? changed[0] : 0;
            }
        }
        @Override public int put(int slot, short type, int amount, int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId) {
            synchronized (PlayerInventory.this) {
                if (settlementMutationBlocked()) return 0;
                if (merchantMutationInProgress) return delegate().put(slot, type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId);
                int[] changed = new int[1];
                boolean accepted = mutateMerchantPayments(() -> {
                    changed[0] = delegate().put(slot, type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId);
                    return changed[0] > 0;
                });
                return accepted ? changed[0] : 0;
            }
        }
        @Override public int put(int slot, short type, int amount, int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData, String itemComponentData) {
            synchronized (PlayerInventory.this) {
                if (settlementMutationBlocked()) return 0;
                if (merchantMutationInProgress) return delegate().put(slot, type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponentData);
                int[] changed = new int[1];
                boolean accepted = mutateMerchantPayments(() -> {
                    changed[0] = delegate().put(slot, type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponentData);
                    return changed[0] > 0;
                });
                return accepted ? changed[0] : 0;
            }
        }
        @Override public int insert(short type, int amount, int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId) {
            synchronized (PlayerInventory.this) {
                if (settlementMutationBlocked()) return 0;
                if (merchantMutationInProgress) return delegate().insert(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId);
                int[] changed = new int[1];
                boolean accepted = mutateMerchantPayments(() -> {
                    changed[0] = delegate().insert(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId);
                    return changed[0] > 0;
                });
                return accepted ? changed[0] : 0;
            }
        }
        @Override public int insert(short type, int amount, int itemDurability, long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData, String itemComponentData) {
            synchronized (PlayerInventory.this) {
                if (settlementMutationBlocked()) return 0;
                if (merchantMutationInProgress) return delegate().insert(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponentData);
                int[] changed = new int[1];
                boolean accepted = mutateMerchantPayments(() -> {
                    changed[0] = delegate().insert(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId, itemBucketMobData, itemComponentData);
                    return changed[0] > 0;
                });
                return accepted ? changed[0] : 0;
            }
        }
    }

    public PlayerInventory() {
        itemType[0] = SWORD_ITEM;
        count[0] = 1;
        durability[0] = INITIAL_DURABILITY;
    }

    /** Bucket-mob identity component까지 포함한 일반 인벤토리·방어구·보조손 완전 복원. */
    public PlayerInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantmentMasks, int[] savedMapIds, int[] savedShulkerIds,
            String[] savedBucketMobData, String[] savedItemComponentData,
            short[] equippedTypes, int[] equippedDurabilities,
            long[] equippedEnchantmentMasks, StackSnapshot offhand, int selectedSlot,
            long persistedRevision) {
        this(itemTypes, counts, durabilities, enchantmentMasks, savedMapIds, savedShulkerIds,
                savedBucketMobData, savedItemComponentData, equippedTypes, equippedDurabilities,
                equippedEnchantmentMasks, new String[ArmorSlot.values().length], offhand,
                selectedSlot, persistedRevision);
    }

    /** 구성요소를 포함한 일반 인벤토리·방어구·보조손 완전 복원. */
    public PlayerInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantmentMasks, int[] savedMapIds, int[] savedShulkerIds,
            String[] savedBucketMobData, String[] savedItemComponentData,
            short[] equippedTypes, int[] equippedDurabilities,
            long[] equippedEnchantmentMasks, String[] equippedComponentData,
            StackSnapshot offhand, int selectedSlot, long persistedRevision) {
        if (savedShulkerIds == null || savedShulkerIds.length != SLOTS) {
            throw new IllegalStateException("inventory snapshot must contain exactly " + SLOTS + " slots");
        }
        if (itemTypes == null || counts == null || durabilities == null
                || itemTypes.length != SLOTS || counts.length != SLOTS || durabilities.length != SLOTS
                || enchantmentMasks == null || enchantmentMasks.length != SLOTS
                || savedMapIds == null || savedMapIds.length != SLOTS
                || savedBucketMobData == null || savedBucketMobData.length != SLOTS
                || savedItemComponentData == null || savedItemComponentData.length != SLOTS
                || equippedTypes == null || equippedDurabilities == null
                || equippedEnchantmentMasks == null
                || equippedTypes.length != ArmorSlot.values().length
                || equippedDurabilities.length != ArmorSlot.values().length
                || equippedEnchantmentMasks.length != ArmorSlot.values().length
                || equippedComponentData == null
                || equippedComponentData.length != ArmorSlot.values().length) {
            throw new IllegalStateException("inventory snapshot must contain exactly " + SLOTS + " slots");
        }
        for (int i = 0; i < SLOTS; i++) {
            if (itemTypes[i] == EMPTY || counts[i] <= 0) {
                if (itemTypes[i] != EMPTY || counts[i] != 0 || durabilities[i] != 0
                        || enchantmentMasks[i] != EnchantmentRules.EMPTY_ENCHANTMENTS
                        || savedMapIds[i] != 0 || savedShulkerIds[i] != 0
                        || savedBucketMobData[i] != null || savedItemComponentData[i] != null) {
                    throw new IllegalStateException("invalid empty inventory slot " + i);
                }
                continue;
            }
            if (!isRegisteredItemType(itemTypes[i])) {
                throw new IllegalStateException("unknown item ID " + Short.toUnsignedInt(itemTypes[i])
                        + " in inventory slot " + i);
            }
            if (counts[i] > stackMax(itemTypes[i])) {
                throw new IllegalStateException("item count " + counts[i] + " exceeds stack maximum "
                        + stackMax(itemTypes[i]) + " in inventory slot " + i);
            }
            if (!EnchantmentRules.isValidEnchantmentMask(enchantmentMasks[i])) {
                throw new IllegalStateException("enchantment mask " + enchantmentMasks[i]
                        + " is out of the enchantment mask layout in inventory slot " + i);
            }
            if (!EnchantmentRules.isValidEnchantmentMaskForItem(
                    itemTypes[i], enchantmentMasks[i])) {
                throw new IllegalStateException("enchantment mask " + enchantmentMasks[i]
                        + " is not semantically valid for item ID "
                        + Short.toUnsignedInt(itemTypes[i]) + " in inventory slot " + i);
            }
            if (!isValidMapIdentity(itemTypes[i], savedMapIds[i])) {
                throw new IllegalStateException("invalid map ID " + savedMapIds[i]
                        + " for item ID " + Short.toUnsignedInt(itemTypes[i])
                        + " in inventory slot " + i);
            }
            if (!isValidShulkerIdentity(itemTypes[i], savedShulkerIds[i])) {
                throw new IllegalStateException("invalid shulker ID " + savedShulkerIds[i]
                        + " for item ID " + Short.toUnsignedInt(itemTypes[i])
                        + " in inventory slot " + i);
            }
            // [SHULKER-CONTENTS] 27칸을 물고 있는 상자는 반드시 한 칸에 하나다 — 스택 2 를
            // 허용하면 같은 참조 ID 가 두 칸에 앉아 27칸이 복제된다(정적판과 같은 계약).
            if (savedShulkerIds[i] != 0 && counts[i] != 1) {
                throw new IllegalStateException("shulker stack in inventory slot " + i
                        + " must contain exactly one box");
            }
            if (!BucketMobPayloadCodec.validForItem(itemTypes[i], savedBucketMobData[i])) {
                throw new IllegalStateException("invalid bucket mob payload in inventory slot " + i);
            }
            ItemComponentCodec.decode(itemTypes[i], savedItemComponentData[i]);
            itemType[i] = itemTypes[i];
            count[i] = counts[i];
            durability[i] = durabilities[i];
            enchantments[i] = enchantmentMasks[i];
            mapIds[i] = savedMapIds[i];
            shulkerIds[i] = savedShulkerIds[i];
            bucketMobData[i] = savedBucketMobData[i];
            itemComponentData[i] = savedItemComponentData[i];
            if (isDurable(itemType[i])) {
                if (durability[i] <= 0 || durability[i] > initialDurability(itemType[i])) {
                    throw new IllegalStateException("invalid durability " + durability[i]
                            + " for item ID " + itemType[i] + " in inventory slot " + i);
                }
            } else if (durability[i] != 0) {
                throw new IllegalStateException("non-durable item ID " + itemType[i]
                        + " has durability in inventory slot " + i);
            }
        }
        if (offhand == null) throw new IllegalStateException("offhand snapshot is required");
        this.offhand = offhand;
        for (ArmorSlot armorSlot : ArmorSlot.values()) {
            int slot = armorSlot.ordinal();
            short type = equippedTypes[slot];
            int itemDurability = equippedDurabilities[slot];
            long itemEnchantments = equippedEnchantmentMasks[slot];
            String componentData = equippedComponentData[slot];
            if (type == EMPTY) {
                if (itemDurability != 0
                        || itemEnchantments != EnchantmentRules.EMPTY_ENCHANTMENTS
                        || componentData != null) {
                    throw new IllegalStateException("empty equipped slot has durability");
                }
                continue;
            }
            if (!EnchantmentRules.isValidEnchantmentMask(itemEnchantments)) {
                throw new IllegalStateException("equipped enchantment mask " + itemEnchantments
                        + " is out of the enchantment mask layout");
            }
            if (!isRegisteredItemType(type) || armorSlot(type) != armorSlot) {
                throw new IllegalStateException("invalid equipped item ID "
                        + Short.toUnsignedInt(type) + " for " + armorSlot.name());
            }
            if (!EnchantmentRules.isValidEnchantmentMaskForItem(type, itemEnchantments)) {
                throw new IllegalStateException("equipped enchantment mask " + itemEnchantments
                        + " is not semantically valid for item ID "
                        + Short.toUnsignedInt(type));
            }
            if (isDurable(type)) {
                if (itemDurability <= 0 || itemDurability > initialDurability(type)) {
                    throw new IllegalStateException("invalid equipped durability " + itemDurability
                            + " for item ID " + Short.toUnsignedInt(type));
                }
            } else if (itemDurability != 0) {
                throw new IllegalStateException("non-durable equipped item has durability");
            }
            ItemComponentCodec.decode(type, componentData);
            equippedType[slot] = type;
            equippedDurability[slot] = itemDurability;
            equippedEnchantments[slot] = itemEnchantments;
            equippedItemComponentData[slot] = componentData;
        }
        if (selectedSlot < 0 || selectedSlot >= HOTBAR_SLOTS) {
            throw new IllegalStateException("selected inventory slot is out of range: " + selectedSlot);
        }
        this.selectedSlot = selectedSlot;
        bindPersistenceRevision(persistedRevision);
    }

    /**
     * 월드 블록이지만 아이템 형태가 없는 ID인가. 살점 지옥 지형 블록은 캐면 심장 핵·의문의 살점만
     * 떨구고 자기 자신은 인벤토리에 들어오지 않는다.
     */
    public static boolean isItemlessWorldBlock(int id) {
        return id == Blocks.REDSTONE_TORCH_OFF || id == Blocks.REDSTONE_WALL_TORCH || id == Blocks.REDSTONE_WALL_TORCH_OFF
                || id == Blocks.REDSTONE_LAMP_LIT || id == Blocks.PISTON_HEAD || id == Blocks.MOVING_PISTON
                || id == Blocks.REDSTONE_WIRE || id == Blocks.TRIPWIRE || id == Blocks.TRIPWIRE_HOOK_BLOCK
                || id == Blocks.ABYSS_STONE || id == Blocks.FLESH_HEART
                || id == Blocks.FLESH_BLOCK || id == Blocks.FLESH_COCOON || Blocks.isFleshTissue(id);
    }

    /** 현재 공유 프로토콜에 등록된 월드 블록 또는 순수 아이템 ID인가. */
    public static boolean isRegisteredItemType(short type) {
        int id = Short.toUnsignedInt(type);
        if (isItemlessWorldBlock(id)) return false;
        return id != EMPTY && (id == Blocks.MYSTERY_FLESH || id == Blocks.FLESH_FIBER
                || id == Blocks.FLESH_BONE_CHESTPLATE
                || id == Blocks.FLESH_HOOK_BLADE
                || id == Blocks.FLESH_DETECTOR
                || id == Blocks.FLESH_BANDAGE
                || id >= Blocks.FLESH_BONE_PLATE && id <= Blocks.FLESH_BONE_BUNDLE
                || id >= Blocks.FLESH_CLOT_SAC && id <= Blocks.FLESH_MEMBRANE || Blocks.isWorldBlockId(id)
                || id >= SWORD_ITEM && id <= RABBIT_FOOT
                || id >= RAW_COPPER && id <= POWDER_SNOW_BUCKET
                // 419 엔더 진주 · 420 슬라임 볼은 418 다음 칸이라 같은 절로 덮는다. 몹 드랍 트랙이
                // 실제로 떨어뜨리므로 여기서 빠지면 주운 뒤 재접속 때 인벤토리 복원이 예외로 터진다.
                || id >= AMETHYST_SHARD && id <= SLIME_BALL
                || id == BOOK
                || id >= COMPASS && id <= PAPER
                || id == Short.toUnsignedInt(FISHING_ROD)
                || id >= Short.toUnsignedInt(OMINOUS_BOTTLE)
                    && id <= Short.toUnsignedInt(PUFFERFISH_BUCKET)
                || id >= Short.toUnsignedInt(ELYTRA)
                    && id <= Short.toUnsignedInt(FIREWORK_ROCKET_3)
                || id >= Short.toUnsignedInt(COMMEMORATIVE_COIN)
                    && id <= Short.toUnsignedInt(DIAMOND_FISHING_ROD)
                || id >= Short.toUnsignedInt(NAME_TAG)
                    && id <= Short.toUnsignedInt(TRIPWIRE_HOOK)
                || id >= Short.toUnsignedInt(WRITABLE_BOOK)
                    && id <= Short.toUnsignedInt(WRITTEN_BOOK)
                || id >= Short.toUnsignedInt(EGG)
                    && id <= Short.toUnsignedInt(CARROT_ON_A_STICK)
                || id == Short.toUnsignedInt(CROSSBOW)
                // 점토 벽돌 765 는 석재 잔여 계열(722~764) 뒤의 단독 순수 아이템이다.
                || id == Short.toUnsignedInt(BRICK)
                // 발광석 가루 816 부터 강화 투척판 832 까지는 물약 II·연장 트랙의 연속 배정이라
                // 801~815(물약 기본) 절과 한 구간으로 덮는다. 여기서 빠지면 강화 물약을 주운 뒤
                // 재접속할 때 인벤토리 복원이 예외로 터진다.
                || id >= Short.toUnsignedInt(SUGAR)
                    && id <= Short.toUnsignedInt(SPLASH_POTION_HARMING_II)
                // [PRISMARINE] 프리즈머린 조각·수정 932~933. 순수 아이템이라
                // Blocks.isWorldBlockId 가 잡아 주지 않으므로 여기서 명시한다.
                || id >= Short.toUnsignedInt(PRISMARINE_SHARD)
                    && id <= Short.toUnsignedInt(PRISMARINE_CRYSTALS)
                // [QUARTZ] 네더 석영 961 도 순수 아이템이라 여기서 명시한다.
                || id == Short.toUnsignedInt(QUARTZ)
                // [COPPER] 밀랍 986 도 순수 아이템이라 여기서 명시한다.
                || id == Short.toUnsignedInt(HONEYCOMB)
                || isExplorerMap(id)
                || id >= Short.toUnsignedInt(FILLED_MAP)
                    && id <= Short.toUnsignedInt(TOTEM_OF_UNDYING)
                // [ROTTEN-LEATHER] 썩은 가죽 재료·방어구 1179~1183. 여기서 빠지면 세트를 주운 뒤
                // 재접속할 때 인벤토리 복원이 예외로 터진다.
                || id >= Short.toUnsignedInt(ROTTEN_LEATHER)
                    && id <= Short.toUnsignedInt(ROTTEN_LEATHER_BOOTS)
                // [TRIAL] 금고 열쇠 1253 도 순수 아이템이라 여기서 명시한다. 빠지면 열쇠를
                // 든 채 재접속할 때 인벤토리 복원이 예외로 터진다
                // ({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡는다).
                || id == Short.toUnsignedInt(TRIAL_KEY)
                || id == Short.toUnsignedInt(OMINOUS_TRIAL_KEY)
                // [TRIAL-GAP] 2314~2330 은 Blocks.classifyPureItemRange(POTION_REGENERATION, …) 와 짝이다.
                || id >= Short.toUnsignedInt(POTION_REGENERATION)
                    && id <= Short.toUnsignedInt(MUSIC_DISC_CREATOR)
                // [GLOWING] 분광 화살 2338. 빠지면 주운 뒤 재접속할 때 인벤토리 복원이 터진다.
                || id == Short.toUnsignedInt(SPECTRAL_ARROW)
                // [CONTAINER-MENUS] 경험치 병 2339, 갑옷 거치대 · 광산 수레 2430~2435.
                || id == Short.toUnsignedInt(EXPERIENCE_BOTTLE)
                || id >= Short.toUnsignedInt(ARMOR_STAND) && id <= Short.toUnsignedInt(TNT_MINECART)
                // [SULFUR] 유황 가루 1310 도 순수 아이템이라 여기서 명시한다.
                // [PROP-MATERIAL] 철·구리 조각 1340~1341 도 순수 아이템이라 여기서 명시한다.
                || id >= Short.toUnsignedInt(IRON_NUGGET)
                    && id <= Short.toUnsignedInt(COPPER_NUGGET)
                // [ZOMBIE-ANIMAL] 상한 달걀 1410 도 순수 아이템이라 여기서 명시한다. 여기서
                // 빠지면 좀비 닭 드랍을 주운 뒤 재접속할 때 인벤토리 복원이 예외로 터진다.
                || id == Short.toUnsignedInt(SPOILED_EGG)
                || id >= Short.toUnsignedInt(BLUE_EGG)
                    && id <= Short.toUnsignedInt(BROWN_EGG)
                || id == Short.toUnsignedInt(WIND_CHARGE)
                || id == Short.toUnsignedInt(SULFUR_CUBE_BUCKET)
                // [BRIMSTONE] 마그마 크림 + 화염 저항 물약 네 종 1462~1466 도 순수 아이템이라
                // 여기서 명시한다. 빠지면 잠복자 드랍이나 양조 산출을 주운 뒤 재접속할 때
                // 인벤토리 복원이 예외로 터진다.
                || id >= Short.toUnsignedInt(MAGMA_CREAM)
                    && id <= Short.toUnsignedInt(SPLASH_POTION_FIRE_RESISTANCE_LONG)
                // [WAVE-86-97] 브리즈 막대 1480 · 팬텀 막 1481 도 순수 아이템이라 여기서
                // 명시한다. 빠지면 신종 드랍을 주운 뒤 재접속할 때 복원이 예외로 터진다.
                || id >= Short.toUnsignedInt(BREEZE_ROD)
                    && id <= Short.toUnsignedInt(PHANTOM_MEMBRANE)
                // [COOKING] 코코아 콩 1631 ~ 말린 다시마 1636 도 순수 아이템이라 여기서
                // 명시한다. 빠지면 요리 산출을 든 채 재접속할 때 복원이 예외로 터진다.
                // 케이크 1630 은 월드 블록이라 Blocks.isWorldBlockId 가 이미 잡는다.
                || id >= Short.toUnsignedInt(COCOA_BEANS)
                    && id <= Short.toUnsignedInt(DRIED_KELP)
                // [CHEST-FAMILY] 블레이즈 가루 1603 · 엔더의 눈 1604 · 셜커 껍데기 1613 도
                // 순수 아이템이라 여기서 명시한다. 빠지면 상자 재료를 든 채 재접속할 때
                // 복원이 예외로 터진다. 줄 갈고리는 이미 488 이라 위 낚시 전리품 구간이 맡는다.
                || id >= Short.toUnsignedInt(BLAZE_POWDER)
                    && id <= Short.toUnsignedInt(EYE_OF_ENDER)
                || id == Short.toUnsignedInt(SHULKER_SHELL)
                // [CROP-BERRY] 독 감자 1700 도 순수 아이템이라 여기서 명시한다. 빠지면 감자밭
                // 수확물을 든 채 재접속할 때 인벤토리 복원이 예외로 터진다.
                || id == Short.toUnsignedInt(POISONOUS_POTATO)
                // [CREAKING] 수지 덩어리 1511 — classifyPureItemRange 에는 있었지만 이 게이트에
                // 빠져, 수지를 든 채 재접속하면 복원이 예외로 터졌다(다이아 방패 트랙이 적발).
                || id == Short.toUnsignedInt(RESIN_CLUMP)
                // [TURTLE] 거북 등딱지 조각 1931·등껍질 투구 1932 — 삼지창 1920 과 같은 유형의
                // 누락(분류만 있고 복원 게이트 부재). 파치드 교정 트랙이 적발.
                || id == Short.toUnsignedInt(TURTLE_SCUTE)
                || id == Short.toUnsignedInt(TURTLE_SHELL)
                // [CONDUIT] 바다의 심장 1950 · [ARCHAEOLOGY] 도자기 조각 1962~1968 — 같은 유형
                // 누락 4·5번째 사례(분류만 있고 복원 게이트 부재). 통합 게이트에서 일괄 적발.
                || id == Short.toUnsignedInt(HEART_OF_THE_SEA)
                || id >= Short.toUnsignedInt(ANGLER_POTTERY_SHERD)
                    && id <= Short.toUnsignedInt(SNORT_POTTERY_SHERD)
                // [GOLD-FOOD] 마법이 부여된 황금 사과 1730 · 후렴과 1731 · 꿀이 든 병 1732 도
                // 순수 아이템이라 여기서 명시한다. 빠지면 전리품을 든 채 재접속할 때 인벤토리
                // 복원이 예외로 터진다. 황금 민들레 1733 은 월드 블록이라
                // Blocks.isWorldBlockId 가 이미 잡는다.
                || id >= Short.toUnsignedInt(ENCHANTED_GOLDEN_APPLE)
                    && id <= Short.toUnsignedInt(HONEY_BOTTLE)
                // [POTION-GAP] 힘·수중 호흡·도약·야간 투시 물약 20종 1760~1779 도 순수
                // 아이템이라 여기서 명시한다. 빠지면 양조 산출을 든 채 재접속할 때 인벤토리
                // 복원이 예외로 터진다.
                || id >= Short.toUnsignedInt(POTION_STRENGTH)
                    && id <= Short.toUnsignedInt(SPLASH_POTION_NIGHT_VISION_LONG)
                // [HARNESS] 하네스 16색 1790~1805 도 순수 아이템이라 여기서 명시한다. 빠지면
                // 하네스를 만들어 든 채 재접속할 때 인벤토리 복원이 예외로 터진다
                // ({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡는다).
                || id >= Short.toUnsignedInt(WHITE_HARNESS)
                    && id <= Short.toUnsignedInt(BLACK_HARNESS)
                // [DIAMOND-SHIELD] 다이아 방패 1858 도 순수 아이템이라 여기서 명시한다. 빠지면
                // 승급한 방패를 든 채 재접속할 때 인벤토리 복원이 예외로 터진다
                // ({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡는다).
                || id == Short.toUnsignedInt(DIAMOND_SHIELD)
                // [SHIELD-FAMILY] 방패 네 티어 2020~2023 도 순수 아이템이라 여기서 명시한다.
                // 이 절은 Blocks.classifyPureItemRange(LEATHER_SHIELD, GOLD_SHIELD) 와 **짝**
                // 이다 — 한쪽만 있으면 방패를 만들거나 상자에서 주운 뒤 재접속할 때 인벤토리
                // 복원이 예외로 터진다({@code RegisteredItemRestoreContractTest} 가 이 누락을
                // 잡는다). 철 방패 321 은 위 순수 아이템 구간이, 다이아 방패 1858 은 바로 윗
                // 줄이 이미 맡고 있어 여기서 다시 세지 않는다.
                || Blocks.isShieldFamilyTier(id)
                // [NAUTILUS-MOUNT] 노틸러스 갑옷 네 티어 2040~2043 도 순수 아이템이라 여기서
                // 명시한다. 이 절은 Blocks.classifyPureItemRange(COPPER_NAUTILUS_ARMOR,
                // DIAMOND_NAUTILUS_ARMOR) 와 **짝**이다 — 한쪽만 있으면 갑옷을 만들거나
                // 주운 뒤 재접속할 때 인벤토리 복원이 예외로 터진다
                // ({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡는다).
                || Blocks.isNautilusArmorTier(id)
                || id >= Short.toUnsignedInt(COPPER_PICKAXE)
                    && id <= Short.toUnsignedInt(AXOLOTL_BUCKET)
                // [SPEAR] 창 여섯 티어 2000~2005 도 순수 아이템이라 여기서 명시한다. 이 절은
                // Blocks.classifyPureItemRange(WOODEN_SPEAR, DIAMOND_SPEAR) 와 **짝**이다 —
                // 한쪽만 있으면 창을 만들거나 주운 뒤 재접속할 때 인벤토리 복원이 예외로
                // 터진다({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡는다).
                // 삼지창 1920 이 실제로 그 짝을 빠뜨린 선례라 여기서 반복하지 않는다.
                || Blocks.isSpear(id)
                // [ARCHAEOLOGY] 해안판 7종(1962~1968)과 사막판 6종(2219~2224)은 순수 아이템이다.
                // 이 절은 Blocks 의 두 classifyPureItemRange 호출과 **짝**이다 — 한쪽만 있으면 조각을 든 채
                // 재접속할 때 인벤토리 복원이 예외로 터진다
                // ({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡는다).
                // 장식 항아리 8종 1970~1977 은 월드 블록이라 Blocks.isWorldBlockId 가 잡는다.
                || Blocks.isPotterySherd(id)
                || id >= Short.toUnsignedInt(ARCHER_POTTERY_SHERD)
                    && id <= Short.toUnsignedInt(BREWER_POTTERY_SHERD)
                || id >= Short.toUnsignedInt(ACTIVATOR_RAIL)
                    && id <= Short.toUnsignedInt(EYE_ARMOR_TRIM_SMITHING_TEMPLATE)
                || id >= Short.toUnsignedInt(BURN_POTTERY_SHERD)
                    && id <= Short.toUnsignedInt(MUSIC_DISC_RELIC)
                // [TRIDENT] 삼지창 1920 도 순수 아이템이라 여기서 명시한다. 이 절은
                // Blocks.classifyPureItemRange(TRIDENT, TRIDENT) 와 **짝**이다 — 한쪽만
                // 있으면 드라운드가 떨군 삼지창을 주운 뒤 재접속할 때 인벤토리 복원이
                // 예외로 터진다({@code RegisteredItemRestoreContractTest} 가 이 누락을 잡았다).
                || id == Short.toUnsignedInt(TRIDENT)
                // [MACE] 철퇴 2370 은 Blocks.classifyPureItemRange(MACE, MACE) 와 짝이다.
                || id == Short.toUnsignedInt(MACE)
                // [VOID-END] 튀긴 후렴과 2301 은 Blocks.classifyPureItemRange(POPPED_CHORUS_FRUIT, …) 와 짝이다.
                || id == Short.toUnsignedInt(POPPED_CHORUS_FRUIT)
                // [DRAGON] 엔드 수정·드래곤의 숨결 2307~2308 은 Blocks.classifyPureItemRange 와 짝이다.
                || id == Short.toUnsignedInt(END_CRYSTAL) || id == Short.toUnsignedInt(DRAGON_BREATH)
                // [END-CITY] 아이템 액자 2303 · 강한 치유의 물약 2309 는 Blocks.classifyPureItemRange 와 짝이다.
                || id == Short.toUnsignedInt(ITEM_FRAME) || id == Short.toUnsignedInt(POTION_STRONG_HEALING)
                // [UTILITY] 2383~2416 은 Blocks.classifyPureItemRange 두 줄과 짝이다. 2384 는 [DRAGON] 드래곤의
                // 숨결(2308)과 겹쳐 비워 둔 칸이다. 월드 블록 2380~2382 · 2417~2419 는 Blocks.isWorldBlockId 가 잡는다.
                || id >= Short.toUnsignedInt(NETHER_STAR)
                    && id <= Short.toUnsignedInt(WARD_ARMOR_TRIM_SMITHING_TEMPLATE)
                    && id != Blocks.REMOVED_UTILITY_2384);
    }

    /**
     * Resolves one canonical {@code minecraft:} item key that carries no potion or map identity.
     * The registry itself remains the authority: a reflected constant is accepted only when its
     * resulting protocol ID is registered by this inventory.
     */
    public static short resolveCanonicalSimpleItemType(String itemKey) {
        if (itemKey == null || !itemKey.startsWith("minecraft:")) {
            throw new IllegalArgumentException("unsupported canonical item " + itemKey);
        }
        String constant = switch (itemKey) {
            case "minecraft:golden_leggings" -> "GOLD_LEGGINGS";
            case "minecraft:golden_boots" -> "GOLD_BOOTS";
            default -> itemKey.substring("minecraft:".length())
                    .toUpperCase(java.util.Locale.ROOT);
        };
        try {
            int id;
            try {
                id = Blocks.class.getField(constant).getInt(null);
            } catch (NoSuchFieldException blockAlias) {
                id = Short.toUnsignedInt(PlayerInventory.class.getField(constant).getShort(null));
            }
            if (id < 0 || id > 0xffff) {
                throw new IllegalArgumentException("canonical item ID out of range");
            }
            short type = (short) id;
            if (!isRegisteredItemType(type) || isPotion(type) || type == MAP || isFilledMapItem(type)) {
                throw new IllegalArgumentException("canonical item requires a non-simple identity");
            }
            return type;
        } catch (ReflectiveOperationException | IllegalArgumentException missing) {
            throw new IllegalArgumentException("unsupported canonical simple item " + itemKey, missing);
        }
    }

    /** FILLED_MAP은 양의 mapId가 필수이고, 나머지 모든 스택은 mapId 0이어야 합니다. */
    public static boolean isValidMapIdentity(short type, int mapId) {
        return isFilledMapItem(type) ? mapId > 0 : mapId == 0;
    }

    /**
     * [SHULKER-CONTENTS] 셜커 상자 17종 중 하나인가. {@link Blocks#isShulkerBox(int)} 의
     * short 관문이며 client {@code blocks.ts} 의 {@code isShulkerBox} 와 같은 판정이다.
     */
    public static boolean isShulkerBox(short type) {
        return Blocks.isShulkerBox(Short.toUnsignedInt(type));
    }

    /**
     * [SHULKER-CONTENTS] 셜커 상자만 27칸 참조 ID 를 가질 수 있고, 그 밖의 모든 스택은 0이어야
     * 합니다. 정적판 {@code validStandaloneShulkerId} 의 짝이며 0 이 그쪽 null 과 같은
     * "내용 없음"입니다 — 빈 상자는 행을 만들지 않으므로 참조도 발급받지 않는다.
     */
    public static boolean isValidShulkerIdentity(short type, int shulkerId) {
        return isShulkerBox(type) ? shulkerId >= 0 : shulkerId == 0;
    }

    /**
     * [COOKING] 수상한 스튜 두 종 중 하나인가. 꽃마다 ID 를 나눈 대신, "스튜인가" 를 묻는
     * 자리는 전부 이 판정 하나를 거치게 해서 새 꽃이 추가돼도 호출부가 갈리지 않게 한다.
     */
    public static boolean isSuspiciousStew(short type) {
        return type == SUSPICIOUS_STEW_POPPY || type == SUSPICIOUS_STEW_DANDELION;
    }

    /**
     * [COOKING] 먹으면 빈 그릇을 돌려주는 음식인가. 그릇 음식은 스택 1 이라 "겹쳐 든 그릇
     * 음식은 먹지 않는다" 는 계약이 붙는다({@code InventoryRules.consumeSelectedFood}).
     */
    public static boolean isBowlFood(short type) {
        return type == MUSHROOM_STEW || type == BEETROOT_SOUP || type == RABBIT_STEW
                || isSuspiciousStew(type);
    }

    /**
     * [SPEAR] 등록된 창 여섯 티어(2000~2005) 중 하나인가. {@link Blocks#isSpear(int)} 의
     * short 관문이며 client {@code items.ts} 의 {@code isSpear} 와 같은 판정이다.
     *
     * <p>"창인가" 를 묻는 자리(리치 상·하한 분기 · 크리티컬/스프린트 넉백 억제 · 돌진 판정 ·
     * 인챈트 대상 · 내구 표)는 전부 이 술어 하나만 지난다. 값 비교를 복제하면 한 곳만
     * 갱신됐을 때 그 경로만 조용히 갈린다.
     */
    public static boolean isSpear(short type) {
        return Blocks.isSpear(Short.toUnsignedInt(type));
    }

    /**
     * [SPEAR] 창의 티어 서열(0=나무 … 5=다이아). 창이 아니면 -1.
     * client {@code items.ts} 의 {@code spearTier} 와 같은 값이어야 한다.
     */
    public static int spearTier(short type) {
        if (type == Blocks.FLESH_BONE_SPEAR || type == Blocks.FLESH_HOOKED_SPEAR) return 3;
        if (type == NETHERITE_SPEAR) return 6;
        return type >= WOODEN_SPEAR && type <= DIAMOND_SPEAR
                ? Short.toUnsignedInt(type) - Short.toUnsignedInt(WOODEN_SPEAR) : -1;
    }

    /** 낚시 규칙이 적용되는 낚싯대(기본·다이아)인가. */
    /**
     * [DIAMOND-SHIELD] 방패(기본 321 · 다이아 1858)인가. 막기 판정·내구 소모·손 자세·
     * 인챈트 대상 판정처럼 "방패인가"를 묻는 자리는 값 비교를 복제하지 않고 전부 이 술어
     * 하나만 본다 — 한 곳이라도 리터럴 비교로 남으면 그 경로만 조용히 다이아를 빠뜨린다.
     * client {@code items.ts} 의 {@code isShield} 와 같은 판정이어야 한다.
     */
    public static boolean isShield(short type) {
        // [SHIELD-FAMILY] 가죽·돌·구리·금 네 티어(2020~2023)도 여기서 합류한다. 철(321)과
        // 다이아(1858)는 구간 밖의 낱개라 앞의 두 비교가 그대로 맡는다 — 네 티어를 위해 그
        // 둘의 ID 를 옮기거나 새로 만들지 않는다.
        return type == SHIELD || type == DIAMOND_SHIELD
                || Blocks.isShieldFamilyTier(Short.toUnsignedInt(type));
    }

    public static boolean isFishingRod(short type) {
        return type == FISHING_ROD || type == DIAMOND_FISHING_ROD;
    }

    /** 폭죽 로켓 세 티어 중 하나인가. */
    public static boolean isFireworkRocket(short type) {
        return type >= FIREWORK_ROCKET_1 && type <= FIREWORK_ROCKET_3;
    }

    /**
     * 폭죽 로켓의 비행 지속 티어(1~3). 로켓이 아니면 0.
     * 바닐라 {@code fireworks.flight_duration} 과 같은 의미이며 화약 개수가 그대로 티어다.
     */
    public static int fireworkFlightDuration(short type) {
        return isFireworkRocket(type) ? type - FIREWORK_ROCKET_1 + 1 : 0;
    }

    public short itemType(int slot) {
        return itemType[slot];
    }

    public int count(int slot) {
        return count[slot];
    }

    /** 슬롯의 내구도(도구/검만 의미 있음, 그 외 0). */
    public int durability(int slot) {
        return durability[slot];
    }

    /** [SURV-X] 슬롯의 인챈트 압축 마스크(인챈트가 없으면 0). */
    public long enchantments(int slot) {
        return slot < 0 || slot >= SLOTS ? EnchantmentRules.EMPTY_ENCHANTMENTS : enchantments[slot];
    }

    public int mapId(int slot) {
        return slot < 0 || slot >= SLOTS ? 0 : mapIds[slot];
    }

    /** [SHULKER-CONTENTS] 슬롯이 물고 있는 27칸 참조 ID(없으면 0). */
    public int shulkerId(int slot) {
        return slot < 0 || slot >= SLOTS ? 0 : shulkerIds[slot];
    }

    public String bucketMobData(int slot) {
        return slot < 0 || slot >= SLOTS ? null : bucketMobData[slot];
    }

    public String itemComponentData(int slot) {
        return slot < 0 || slot >= SLOTS ? null : itemComponentData[slot];
    }

    /** [ENCHANT-WIDE] 슬롯의 43종 인챈트 집합(워드 0 칸 + 성분의 확장 워드). */
    public WideEnchantments wideEnchantments(int slot) {
        if (slot < 0 || slot >= SLOTS) return WideEnchantments.EMPTY;
        return wideEnchantmentsOf(enchantments[slot], itemComponentData[slot]);
    }

    /** [ENCHANT-WIDE] 착용 방어구 한 부위의 43종 인챈트 집합. */
    public synchronized WideEnchantments equippedWideEnchantments(ArmorSlot slot) {
        int index = slot.ordinal();
        return wideEnchantmentsOf(equippedEnchantments[index], equippedItemComponentData[index]);
    }

    /**
     * [ENCHANT-WIDE] 워드 0 마스크와 검증된 성분 문자열에서 43종 집합을 만든다. 확장 워드는
     * {@code WCIC4} 문자열에만 있으므로 그 밖의 성분은 파싱하지 않는다.
     */
    public static WideEnchantments wideEnchantmentsOf(long mask, String itemComponentData) {
        return WideEnchantments.withExtendedHex(mask,
                ItemComponentCodec.extendedEnchantmentHex(itemComponentData));
    }

    public synchronized StackSnapshot offhand() {
        return offhand;
    }

    /** 현재 선택 주손 또는 보조손의 손실 없는 스택을 읽습니다. */
    public synchronized StackSnapshot stack(Hand hand) {
        if (hand == null) throw new IllegalArgumentException("hand is required");
        if (hand == Hand.OFFHAND) return offhand;
        int slot = selectedSlot;
        return new StackSnapshot(itemType[slot], count[slot], durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
    }

    public synchronized HandRef capture(Hand hand) {
        if (hand == null) throw new IllegalArgumentException("hand is required");
        int slot = hand == Hand.MAIN ? selectedSlot : -1;
        return new HandRef(inventoryIdentity, hand, slot, revision, handMutationNonce,
                hand == Hand.OFFHAND ? offhand : stackSnapshotAt(slot));
    }

    /**
     * 주손이면 지정한 핫바 칸, 보조손이면 보조손을 지금 내용으로 잡습니다. 현재 선택 칸과 무관합니다.
     * 액션이 도착할 때 캡처한 칸을 나중에 정산하는 경로(보류 재적용·FIFO·detached 사본)가 그 사이의
     * 스크롤에 끌려가지 않고 같은 칸을 쓰게 합니다. 칸 내용은 그 사이 바뀌었을 수 있으므로 호출자가
     * 기대 아이템을 다시 검증합니다.
     */
    public synchronized HandRef capture(Hand hand, int mainSlot) {
        if (hand == null) throw new IllegalArgumentException("hand is required");
        if (hand == Hand.MAIN && (mainSlot < 0 || mainSlot >= HOTBAR_SLOTS)) {
            throw new IllegalArgumentException("invalid hotbar slot");
        }
        return captureExactHand(hand, mainSlot);
    }

    public synchronized StackSnapshot stack(HandRef ref) {
        if (!currentHandRefMatches(ref)) return StackSnapshot.EMPTY;
        if (ref.hand == Hand.OFFHAND) return offhand;
        int slot = ref.mainSlot;
        return new StackSnapshot(itemType[slot], count[slot], durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
    }

    /** Exact hotbar stack used by powered shelves. */
    public synchronized StackSnapshot hotbarStack(int slot) {
        if (slot < 0 || slot >= HOTBAR_SLOTS) {
            throw new IllegalArgumentException("invalid hotbar slot");
        }
        return new StackSnapshot(itemType[slot], count[slot], durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
    }

    /** Exact hotbar replacement used by powered shelves. */
    public synchronized boolean setHotbarStack(int slot, StackSnapshot stack) {
        if (settlementMutationBlocked()) return false;
        if (slot < 0 || slot >= HOTBAR_SLOTS || stack == null) {
            throw new IllegalArgumentException("invalid hotbar replacement");
        }
        StackSnapshot before = hotbarStack(slot);
        if (before.equals(stack)) return true;
        if (normalizedInventory(stack, slot) == null) return false;
        preflightRevisionCapacity();
        writeSlot(slot, stack);
        advancePersistenceRevision();
        return true;
    }

    /** 원자 규칙이 실패를 되돌릴 때 캡처된 같은 손에 정확한 스택을 복원합니다. */
    public synchronized boolean setStack(HandRef ref, StackSnapshot stack) {
        if (settlementMutationBlocked()) return false;
        if (!currentHandRefMatches(ref)) return false;
        if (stack == null) throw new IllegalArgumentException("stack is required");
        if (ref.hand == Hand.OFFHAND) {
            setOffhand(stack);
            return true;
        }
        StackSnapshot before = stack(ref);
        if (before.equals(stack)) return true;
        if (normalizedInventory(stack, ref.mainSlot) == null) return false;
        preflightRevisionCapacity();
        writeSlot(ref.mainSlot, stack);
        advancePersistenceRevision();
        return true;
    }

    private static void requireWellFormedHandRef(HandRef ref) {
        if (ref == null || ref.hand == null
                || ref.hand == Hand.MAIN && (ref.mainSlot < 0 || ref.mainSlot >= SLOTS)
                || ref.hand == Hand.OFFHAND && ref.mainSlot != -1
                || ref.inventoryIdentity <= 0 || ref.revision < 0 || ref.capturedStack == null) {
            throw new IllegalArgumentException("invalid hand reference");
        }
    }

    private boolean currentHandRefMatches(HandRef ref) {
        try {
            requireWellFormedHandRef(ref);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        if (ref.inventoryIdentity != inventoryIdentity
                || ref.mutationNonce != handMutationNonce) return false;
        StackSnapshot current = ref.hand == Hand.OFFHAND ? offhand : stackSnapshotAt(ref.mainSlot);
        return current.equals(ref.capturedStack);
    }

    /**
     * 다른 인벤토리(보통 라이브 인벤토리)에서 잡은 손 참조를 이 detached 사본에 다시 묶는다.
     * 정산 lane 은 detached 사본 위에서 소비하므로, identity 와 nonce 가 다른 원본 참조로는
     * {@link #consumeOne(HandRef, short)} 가 항상 거절된다. 같은 손에 같은 스택이 있을 때만 성공한다.
     */
    public synchronized HandRef rebindHand(HandRef ref) {
        if (ref == null || ref.capturedStack == null) return null;
        if (ref.hand == Hand.MAIN && (ref.mainSlot < 0 || ref.mainSlot >= SLOTS)) return null;
        HandRef rebound = captureExactHand(ref.hand, ref.mainSlot);
        return rebound.capturedStack.equals(ref.capturedStack) ? rebound : null;
    }

    private HandRef captureExactHand(Hand hand, int mainSlot) {
        int slot = hand == Hand.MAIN ? mainSlot : -1;
        return new HandRef(inventoryIdentity, hand, slot, revision, handMutationNonce,
                hand == Hand.OFFHAND ? offhand : stackSnapshotAt(slot));
    }

    /** 저장 복원 및 권위 명령이 검증된 보조손 스택을 설치합니다. */
    public synchronized void setOffhand(StackSnapshot stack) {
        if (settlementMutationBlocked()) return;
        if (stack == null) throw new IllegalArgumentException("offhand stack is required");
        if (offhand.equals(stack)) return;
        preflightRevisionCapacity();
        offhand = stack;
        advancePersistenceRevision();
    }

    /** 선택 주손과 보조손을 임시 컨테이너 상태까지 무손실일 때만 교환합니다. */
    public synchronized boolean swapSelectedWithOffhand() {
        return swapSlotWithOffhand(selectedSlot);
    }

    /** 선택 주손 교환이 현재 격자·커서를 포함해 저장 가능한지 변이 없이 검사합니다. */
    public synchronized boolean canSwapSelectedWithOffhand() {
        return canSwapSlotWithOffhand(selectedSlot);
    }

    /** 지정 슬롯 교환이 현재 격자·커서를 포함해 저장 가능한지 변이 없이 검사합니다. */
    public synchronized boolean canSwapSlotWithOffhand(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("invalid inventory slot");
        return normalizedInventoryAfterOffhandSwap(slot) != null;
    }

    /** 메뉴 F가 지정한 일반 인벤토리 슬롯과 보조손 전체 구성요소를 원자적으로 교환합니다. */
    public synchronized boolean swapSlotWithOffhand(int slot) {
        if (settlementMutationBlocked()) return false;
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("invalid inventory slot");
        InventoryArrays normalizedAfterSwap = normalizedInventoryAfterOffhandSwap(slot);
        if (normalizedAfterSwap == null) return false;
        StackSnapshot selected = new StackSnapshot(itemType[slot], count[slot], durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
        preflightRevisionCapacity();
        writeSlot(slot, offhand);
        offhand = selected;
        advancePersistenceRevision();
        return true;
    }

    private void writeSlot(int slot, StackSnapshot stack) {
        itemType[slot] = stack.itemType();
        count[slot] = stack.count();
        durability[slot] = stack.durability();
        enchantments[slot] = stack.enchantments();
        mapIds[slot] = stack.mapId();
        shulkerIds[slot] = stack.shulkerId();
        bucketMobData[slot] = stack.bucketMobData();
        itemComponentData[slot] = stack.itemComponentData();
    }

    /** [SURV-X] 인챈트 테이블이 확정한 마스크를 슬롯에 기록합니다. 빈 칸/비대상은 거부합니다. */
    public synchronized boolean setEnchantments(int slot, long mask) {
        if (settlementMutationBlocked()) return false;
        if (slot < 0 || slot >= SLOTS || itemType[slot] == EMPTY || count[slot] <= 0) return false;
        if (!EnchantmentRules.isValidEnchantmentMaskForItem(itemType[slot], mask)) {
            return false;
        }
        if (enchantments[slot] == mask) return false;
        short[] nextTypes = itemType.clone();
        int[] nextCounts = count.clone();
        int[] nextDurabilities = durability.clone();
        long[] nextEnchantments = enchantments.clone();
        int[] nextMapIds = mapIds.clone();
        int[] nextShulkerIds = shulkerIds.clone();
        nextEnchantments[slot] = mask;
        if (normalizedInventory(nextTypes, nextCounts, nextDurabilities, nextEnchantments,
                nextMapIds, nextShulkerIds) == null) return false;
        preflightRevisionCapacity();
        enchantments[slot] = mask;
        advancePersistenceRevision();
        return true;
    }

    /** [SURV-X] Unbreaking 판정 난수를 테스트에서 고정합니다. */
    public synchronized void setDurabilityRandom(Random random) {
        if (settlementMutationBlocked()) return;
        if (random != null) this.durabilityRandom = random;
    }

    /** 이 슬롯이 내구도를 가지는 도구/검인가(welcome/inventoryUpdate 의 durability 노출 판정). */
    public boolean isDurableSlot(int slot) {
        return isDurable(itemType[slot]);
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    /** 영속 대상(itemType/count/durability) 변경 카운터. 주기 저장 dirty 판정용(값이 바뀔 때마다 증가). */
    public synchronized long revision() {
        return revision;
    }

    /** 선택 슬롯 변경(핫바 0~8 범위 밖은 무시). 실제 변경만 영속 revision 을 올린다. */
    public synchronized void select(int slot) {
        if (settlementMutationBlocked()) return;
        if (slot < 0 || slot >= HOTBAR_SLOTS || selectedSlot == slot) return;
        preflightRevisionCapacity();
        this.selectedSlot = slot;
        revision = Math.incrementExact(revision);
        persistenceRevisionBound = true;
        if (persistenceLifecycle == PersistenceLifecycle.OPEN) {
            persistenceLifecycle = PersistenceLifecycle.BASELINE_BOUND;
        }
        if (revision == Long.MAX_VALUE) persistenceLifecycle = PersistenceLifecycle.TERMINAL;
    }

    // ── 아이템 종류 판정(§2·§4) ──

    /** 내구도를 가지는 도구/검·방어구인가. */
    public static boolean isDurable(short type) {
        return (type >= SWORD_ITEM && type <= SHOVEL)
                || (type >= STONE_TIER_MIN && type <= IRON_TIER_MAX)
                || (type >= GOLD_TIER_MIN && type <= GOLD_TIER_MAX)
                || (type >= DIAMOND_TIER_MIN && type <= DIAMOND_TIER_MAX)
                || type == FLINT_AND_STEEL
                || isShield(type)
                || type == BOW
                || type == WOODEN_HOE
                || type == (short) Blocks.SHEARS
                || isFishingRod(type)
                || type == BRUSH
                || type == WOLF_ARMOR
                || type == ELYTRA
                || type == CARROT_ON_A_STICK
                || type == CROSSBOW
                // [TRIDENT] 삼지창도 내구 250 짜리 도구다. 이 줄이 빠지면 드라운드가 손에
                // 쥐는 순간 장비 내구 검증이 터진다.
                || type == TRIDENT
                // [MACE] 철퇴도 내구 500 짜리 무기다(스택 1 도 이 판정에서 나온다).
                || type == MACE
                // [SPEAR] 창 여섯 티어도 내구를 가진 무기다. 이 줄이 빠지면 좀비·피글린이
                // 창을 쥐는 순간 장비 내구 검증이 터진다(삼지창이 겪은 것과 같은 자리다).
                || isSpear(type)
                || type >= COPPER_PICKAXE && type <= COPPER_BOOTS
                || type == DIAMOND_HOE
                || type >= NETHERITE_PICKAXE && type <= NETHERITE_SPEAR
                || isArmor(type);
    }

    /**
     * [ROTTEN-LEATHER] 썩은 가죽 방어구 → 같은 부위의 가죽 방어구. 그 외는 입력 그대로다.
     *
     * <p>썩은 가죽 세트의 보상은 언데드 무적대라 **방어 성능은 최저 티어(가죽)와 같다**는 것이
     * 이 트랙의 자체 계약이다(docs/MC-REFERENCE.md 썩은 가죽 절). 내구·방어도·인챈트 가능성처럼
     * "가죽과 같다"고 정한 값은 리터럴을 복제하는 대신 전부 이 사상 하나를 지나 가죽 표를 읽는다.
     */
    public static short leatherEquivalentArmor(short type) {
        return type >= ROTTEN_LEATHER_ARMOR_MIN && type <= ROTTEN_LEATHER_ARMOR_MAX
                ? (short) (LEATHER_HELMET + type - ROTTEN_LEATHER_ARMOR_MIN)
                : type;
    }

    /** 티어별 초기 내구도(나무 60 · 돌 120 · 철 240). 내구 없는 아이템은 0. */
    public static int initialDurability(short rawType) {
        if (rawType == Blocks.FLESH_FIBER_BOOTS) return 195;
        if (rawType == Blocks.FLESH_BONE_CHESTPLATE) return 240;
        short type = leatherEquivalentArmor(rawType);
        if (type >= STONE_TIER_MIN && type <= STONE_TIER_MAX) {
            return STONE_DURABILITY;
        }
        if (type >= IRON_TIER_MIN && type <= IRON_TIER_MAX) {
            return IRON_DURABILITY;
        }
        if (type >= GOLD_TIER_MIN && type <= GOLD_TOOL_MAX) return GOLD_DURABILITY;
        if (type >= DIAMOND_TIER_MIN && type <= DIAMOND_TOOL_MAX) return DIAMOND_DURABILITY;
        if (type >= SWORD_ITEM && type <= SHOVEL) {
            return INITIAL_DURABILITY;
        }
        if (type == FLINT_AND_STEEL) return FLINT_AND_STEEL_DURABILITY;
        if (type == SHIELD) return SHIELD_DURABILITY;
        if (type == DIAMOND_SHIELD) return DIAMOND_SHIELD_DURABILITY;
        // [SHIELD-FAMILY] 네 티어는 기존 티어 구간(STONE_TIER_MIN.. 등) 밖의 독립 구간이라 위
        // 구간 검사에 걸리지 않는다. 각 값의 파생 근거는 상수 주석이 소유한다.
        if (type == LEATHER_SHIELD) return LEATHER_SHIELD_DURABILITY;
        if (type == STONE_SHIELD) return STONE_SHIELD_DURABILITY;
        if (type == COPPER_SHIELD) return COPPER_SHIELD_DURABILITY;
        if (type == GOLD_SHIELD) return GOLD_SHIELD_DURABILITY;
        if (type == BOW) return BOW_DURABILITY;
        if (type == WOODEN_HOE) return INITIAL_DURABILITY;
        if (type == (short) Blocks.SHEARS) return SHEARS_DURABILITY;
        if (type == FISHING_ROD) return FISHING_ROD_DURABILITY;
        if (type == DIAMOND_FISHING_ROD) return DIAMOND_FISHING_ROD_DURABILITY;
        if (type == BRUSH) return BRUSH_DURABILITY;
        if (type == WOLF_ARMOR) return WOLF_ARMOR_DURABILITY;
        if (type == ELYTRA) return ELYTRA_DURABILITY;
        if (type == CARROT_ON_A_STICK) return CARROT_ON_A_STICK_DURABILITY;
        if (type == CROSSBOW) return CROSSBOW_DURABILITY;
        if (type == TRIDENT) return TRIDENT_DURABILITY;
        if (type == MACE) return MACE_DURABILITY;
        // [TURTLE][A] turtle_helmet 의 max_damage 275. 방어구 티어 구간 밖의 한 조각이라
        // 아래 switch 의 티어 나열이 아니라 여기서 낱개로 가른다.
        if (type == TURTLE_SHELL) return TURTLE_SHELL_DURABILITY;
        if (type >= COPPER_PICKAXE && type <= COPPER_HOE) return COPPER_DURABILITY;
        if (type == DIAMOND_HOE) return 1561;
        if (type >= NETHERITE_PICKAXE && type <= NETHERITE_HOE) return NETHERITE_DURABILITY;
        if (type == NETHERITE_SPEAR) return NETHERITE_DURABILITY;
        // [SPEAR] 창은 기존 티어 구간(STONE_TIER_MIN.. 등) 밖의 독립 구간이라 위 구간 검사에
        // 걸리지 않는다. 표는 SPEAR_DURABILITY 하나가 정본이다.
        if (isSpear(type)) return SPEAR_DURABILITY[spearTier(type)];
        return switch (type) {
            case LEATHER_HELMET -> 55;
            case LEATHER_CHESTPLATE -> 80;
            case LEATHER_LEGGINGS -> 75;
            case LEATHER_BOOTS -> 65;
            case IRON_HELMET -> 165;
            case IRON_CHESTPLATE -> 240;
            case IRON_LEGGINGS -> 225;
            case IRON_BOOTS -> 195;
            case CHAINMAIL_HELMET -> 165;
            case CHAINMAIL_CHESTPLATE -> 240;
            case CHAINMAIL_LEGGINGS -> 225;
            case CHAINMAIL_BOOTS -> 195;
            case GOLD_HELMET -> 77;
            case GOLD_CHESTPLATE -> 112;
            case GOLD_LEGGINGS -> 105;
            case GOLD_BOOTS -> 91;
            case DIAMOND_HELMET -> 363;
            case DIAMOND_CHESTPLATE -> 528;
            case DIAMOND_LEGGINGS -> 495;
            case DIAMOND_BOOTS -> 429;
            case COPPER_HELMET -> 143;
            case COPPER_CHESTPLATE -> 208;
            case COPPER_LEGGINGS -> 195;
            case COPPER_BOOTS -> 169;
            case NETHERITE_HELMET -> 407;
            case NETHERITE_CHESTPLATE -> 592;
            case NETHERITE_LEGGINGS -> 555;
            case NETHERITE_BOOTS -> 481;
            default -> 0;
        };
    }

    public static boolean isArmor(short type) {
        return (type >= LEATHER_HELMET && type <= IRON_BOOTS)
                || (type >= GOLD_ARMOR_MIN && type <= GOLD_ARMOR_MAX)
                || (type >= DIAMOND_ARMOR_MIN && type <= DIAMOND_ARMOR_MAX)
                || (type >= CHAINMAIL_HELMET && type <= CHAINMAIL_BOOTS)
                || (type >= COPPER_HELMET && type <= COPPER_BOOTS)
                || (type >= NETHERITE_HELMET && type <= NETHERITE_BOOTS)
                // [TURTLE] 거북 등껍질은 투구 한 조각뿐인 "티어"라 구간이 아니라 낱개다.
                || type == TURTLE_SHELL || type == Blocks.FLESH_FIBER_BOOTS
                || type == Blocks.FLESH_BONE_CHESTPLATE
                || isRottenLeatherArmor(type);
    }

    /** [ROTTEN-LEATHER] 썩은 가죽 방어구 4부위 중 하나인가. 재료(썩은 가죽)는 방어구가 아니다. */
    public static boolean isRottenLeatherArmor(short type) {
        return type >= ROTTEN_LEATHER_ARMOR_MIN && type <= ROTTEN_LEATHER_ARMOR_MAX;
    }

    public static ArmorSlot armorSlot(short type) {
        if (type == Blocks.FLESH_FIBER_BOOTS) return ArmorSlot.BOOTS;
        if (type == Blocks.FLESH_BONE_CHESTPLATE) return ArmorSlot.CHESTPLATE;
        return switch (type) {
            case LEATHER_HELMET, IRON_HELMET, GOLD_HELMET, DIAMOND_HELMET,
                    CHAINMAIL_HELMET, ROTTEN_LEATHER_HELMET, COPPER_HELMET,
                    NETHERITE_HELMET, TURTLE_SHELL -> ArmorSlot.HELMET;
            case LEATHER_CHESTPLATE, IRON_CHESTPLATE, GOLD_CHESTPLATE,
                    DIAMOND_CHESTPLATE, CHAINMAIL_CHESTPLATE,
                    ROTTEN_LEATHER_CHESTPLATE, COPPER_CHESTPLATE,
                    NETHERITE_CHESTPLATE, ELYTRA -> ArmorSlot.CHESTPLATE;
            case LEATHER_LEGGINGS, IRON_LEGGINGS, GOLD_LEGGINGS, DIAMOND_LEGGINGS,
                    CHAINMAIL_LEGGINGS, ROTTEN_LEATHER_LEGGINGS, COPPER_LEGGINGS,
                    NETHERITE_LEGGINGS -> ArmorSlot.LEGGINGS;
            case LEATHER_BOOTS, IRON_BOOTS, GOLD_BOOTS, DIAMOND_BOOTS,
                    CHAINMAIL_BOOTS, ROTTEN_LEATHER_BOOTS, COPPER_BOOTS,
                    NETHERITE_BOOTS -> ArmorSlot.BOOTS;
            default -> type == (short) Blocks.CARVED_PUMPKIN ? ArmorSlot.HELMET : null;
        };
    }

    public static int armorPoints(short rawType) {
        if (rawType == Blocks.FLESH_FIBER_BOOTS) return 2;
        if (rawType == Blocks.FLESH_BONE_CHESTPLATE) return 8;
        // [ROTTEN-LEATHER] 방어도는 가죽과 같다 — 표를 늘리지 않고 가죽 행을 그대로 읽는다.
        short type = leatherEquivalentArmor(rawType);
        return switch (type) {
            case LEATHER_HELMET, LEATHER_BOOTS -> 1;
            case LEATHER_CHESTPLATE -> 3;
            case LEATHER_LEGGINGS, IRON_HELMET, IRON_BOOTS -> 2;
            case IRON_CHESTPLATE -> 6;
            case IRON_LEGGINGS -> 5;
            case CHAINMAIL_HELMET -> 2;
            case CHAINMAIL_CHESTPLATE -> 5;
            case CHAINMAIL_LEGGINGS -> 4;
            case CHAINMAIL_BOOTS -> 1;
            case GOLD_HELMET -> 2;
            case GOLD_CHESTPLATE -> 5;
            case GOLD_LEGGINGS -> 3;
            case GOLD_BOOTS -> 1;
            // [TURTLE][A] turtle_helmet 의 armor 는 2 로 철 투구와 같다.
            case TURTLE_SHELL -> 2;
            case DIAMOND_HELMET, DIAMOND_BOOTS -> 3;
            case DIAMOND_CHESTPLATE -> 8;
            case DIAMOND_LEGGINGS -> 6;
            case COPPER_HELMET -> 2;
            case COPPER_CHESTPLATE -> 4;
            case COPPER_LEGGINGS -> 3;
            case COPPER_BOOTS -> 1;
            case NETHERITE_HELMET, NETHERITE_BOOTS -> 3;
            case NETHERITE_CHESTPLATE -> 8;
            case NETHERITE_LEGGINGS -> 6;
            default -> 0;
        };
    }

    public static int armorToughness(short type) {
        return type >= NETHERITE_HELMET && type <= NETHERITE_BOOTS ? 3
                : type >= DIAMOND_ARMOR_MIN && type <= DIAMOND_ARMOR_MAX ? 2 : 0;
    }

    public synchronized short equippedType(ArmorSlot slot) {
        return equippedType[slot.ordinal()];
    }

    /**
     * [ROTTEN-LEATHER] 썩은 가죽 4부위를 **전부** 착용 중인가. 언데드 무적대의 유일한 장비 조건이며
     * 정적판 사본은 client `StandaloneEquipment.standaloneWearingRottenLeatherSet` 이다.
     * 부위마다 그 부위의 조각이어야 한다 — 투구 자리에 부츠를 넣는 식은 애초에 성립하지 않는다.
     */
    public synchronized boolean wearingRottenLeatherSet() {
        for (ArmorSlot slot : ArmorSlot.values()) {
            if (equippedType[slot.ordinal()] != (short) (ROTTEN_LEATHER_ARMOR_MIN + slot.ordinal())) {
                return false;
            }
        }
        return true;
    }

    public synchronized int equippedDurability(ArmorSlot slot) {
        return equippedDurability[slot.ordinal()];
    }

    /** [SURV-X] 착용 방어구 한 부위의 인챈트 마스크. 보호(Protection) 합산에 쓴다. */
    public synchronized long equippedEnchantments(ArmorSlot slot) {
        return equippedEnchantments[slot.ordinal()];
    }

    /** 장착 한 칸의 전체 아이템 정체성. 장착품에 불법인 참조 구성요소는 항상 0/null입니다. */
    public synchronized StackSnapshot equippedStack(ArmorSlot slot) {
        int index = slot.ordinal();
        if (equippedType[index] == EMPTY) return StackSnapshot.EMPTY;
        return new StackSnapshot(equippedType[index], 1, equippedDurability[index],
                equippedEnchantments[index], 0, 0, null, equippedItemComponentData[index]);
    }

    private void writeEquipped(ArmorSlot slot, StackSnapshot stack) {
        int index = slot.ordinal();
        equippedType[index] = stack.itemType();
        equippedDurability[index] = stack.durability();
        equippedEnchantments[index] = stack.enchantments();
        equippedItemComponentData[index] = stack.itemComponentData();
    }

    /**
     * [CURSE] 결속의 저주가 걸린 착용품은 인벤토리 조작으로 벗을 수 없다(바닐라 binding_curse).
     * 사망 정산({@link #drainForDeath})은 이 잠금을 통과하며, 빈 부위는 당연히 잠기지 않는다.
     */
    private boolean bindingCurseLocks(ArmorSlot armor) {
        int index = armor.ordinal();
        return equippedType[index] != EMPTY && EnchantmentRules.enchantLevel(
                equippedEnchantments[index], EnchantmentRules.BINDING_CURSE) > 0;
    }

    /** 인벤토리의 방어구와 같은 부위의 착용품을 맞바꿉니다. 빈 부위면 해당 인벤토리 칸이 비어집니다. */
    public synchronized boolean equip(int slot) {
        if (settlementMutationBlocked()) return false;
        if (slot < 0 || slot >= SLOTS || armorSlot(itemType[slot]) == null || count[slot] < 1) return false;
        ArmorSlot armorSlot = armorSlot(itemType[slot]);
        if (bindingCurseLocks(armorSlot)) return false;
        int e = armorSlot.ordinal();
        // 조각된 호박은 방어구와 달리 쌓인다. 뭉치에서 한 개만 덜어 착용하며,
        // 벗은 것을 돌려놓을 자리가 없으므로 빈 부위일 때만 허용한다.
        if (count[slot] > 1) {
            if (equippedType[e] != EMPTY) return false;
            preflightRevisionCapacity();
            writeEquipped(armorSlot, new StackSnapshot(itemType[slot], 1, durability[slot],
                    enchantments[slot], 0, 0, null, itemComponentData[slot]));
            count[slot]--;
            advancePersistenceRevision();
            return true;
        }
        StackSnapshot displaced = equippedStack(armorSlot);
        if (normalizedInventory(displaced, slot) == null) return false;
        preflightRevisionCapacity();
        writeEquipped(armorSlot, new StackSnapshot(itemType[slot], 1, durability[slot],
                enchantments[slot], 0, 0, null, itemComponentData[slot]));
        writeSlot(slot, displaced);
        advancePersistenceRevision();
        return true;
    }

    /**
     * [CONTAINER-MENUS] {@code EquipmentDispenseItemBehavior.dispenseEquipment} onto a player:
     * {@code canEquipWithDispenser} needs the stack's equipment slot to be empty; the dispenser
     * then {@code setItemSlot}s one item of the stack (every component kept).
     */
    public synchronized boolean equipFromDispenser(StackSnapshot one) {
        if (settlementMutationBlocked() || one == null || one.isEmpty()) return false;
        ArmorSlot armor = armorSlot(one.itemType());
        if (armor == null || equippedType[armor.ordinal()] != EMPTY) return false;
        preflightRevisionCapacity();
        writeEquipped(armor, new StackSnapshot(one.itemType(), 1, one.durability(),
                one.enchantments(), 0, 0, null, one.itemComponentData()));
        advancePersistenceRevision();
        return true;
    }

    /** 캡처된 손의 방어구를 착용 칸과 한 임계 구역에서 손실 없이 맞바꿉니다. */
    public synchronized boolean equip(HandRef hand) {
        if (settlementMutationBlocked()) return false;
        if (!currentHandRefMatches(hand)) return false;
        StackSnapshot incoming = stack(hand);
        ArmorSlot armor = incoming.isEmpty() ? null : armorSlot(incoming.itemType());
        if (armor == null || bindingCurseLocks(armor)) return false;
        int equippedSlot = armor.ordinal();
        if (incoming.count() > 1) {
            if (equippedType[equippedSlot] != EMPTY) return false;
            preflightRevisionCapacity();
            writeEquipped(armor, new StackSnapshot(incoming.itemType(), 1,
                    incoming.durability(), incoming.enchantments(), 0, 0, null,
                    incoming.itemComponentData()));
            writeHand(hand, new StackSnapshot(incoming.itemType(), incoming.count() - 1,
                    incoming.durability(), incoming.enchantments(), incoming.mapId(),
                    incoming.shulkerId(), incoming.bucketMobData(), incoming.itemComponentData()));
            advancePersistenceRevision();
            return true;
        }
        StackSnapshot displaced = equippedStack(armor);
        if (hand.hand == Hand.MAIN
                && normalizedInventory(displaced, hand.mainSlot) == null) return false;
        preflightRevisionCapacity();
        writeEquipped(armor, new StackSnapshot(incoming.itemType(), 1,
                incoming.durability(), incoming.enchantments(), 0, 0, null,
                incoming.itemComponentData()));
        writeHand(hand, displaced);
        advancePersistenceRevision();
        return true;
    }

    /** 인벤토리 한 칸과 지정 착용 부위를 원자적으로 맞바꿉니다. */
    public synchronized boolean moveArmor(int slot, ArmorSlot armor) {
        if (settlementMutationBlocked() || slot < 0 || slot >= SLOTS || armor == null) return false;
        if (bindingCurseLocks(armor)) return false;
        short incomingType = itemType[slot];
        int incomingCount = count[slot];
        if (incomingType != EMPTY && (armorSlot(incomingType) != armor || incomingCount < 1)) {
            return false;
        }
        int equippedSlot = armor.ordinal();
        if (incomingCount > 1) {
            if (equippedType[equippedSlot] != EMPTY) return false;
            preflightRevisionCapacity();
            writeEquipped(armor, new StackSnapshot(incomingType, 1, durability[slot],
                    enchantments[slot], 0, 0, null, itemComponentData[slot]));
            count[slot]--;
            advancePersistenceRevision();
            return true;
        }
        StackSnapshot previous = equippedStack(armor);
        preflightRevisionCapacity();
        writeEquipped(armor, incomingType == EMPTY ? StackSnapshot.EMPTY
                : new StackSnapshot(incomingType, 1, durability[slot], enchantments[slot],
                        0, 0, null, itemComponentData[slot]));
        writeSlot(slot, previous);
        advancePersistenceRevision();
        return true;
    }

    private void writeHand(HandRef hand, StackSnapshot stack) {
        if (hand.hand == Hand.OFFHAND) offhand = stack;
        else writeSlot(hand.mainSlot, stack);
    }

    public synchronized int armorPoints() {
        int total = 0;
        for (short type : equippedType) total += armorPoints(type);
        return total;
    }

    public synchronized int armorToughness() {
        int total = 0;
        for (short type : equippedType) total += armorToughness(type);
        return total;
    }

    /** 방어 가능한 피격 1회에 모든 착용 방어구를 같은 양만큼 마모시키고, 0이면 파괴합니다. */
    public synchronized List<Short> damageArmor(int incomingDamage) {
        if (settlementMutationBlocked()) return List.of();
        int wear = Math.max(1, incomingDamage / 4);
        preflightRevisionCapacity();
        boolean changed = false;
        List<Short> broken = new ArrayList<>();
        for (int i = 0; i < equippedType.length; i++) {
            // 조각된 호박과 겉날개는 피격으로 마모되지 않는다. 겉날개 내구는 활공만 소비한다.
            if (equippedType[i] == EMPTY || equippedType[i] == (short) Blocks.CARVED_PUMPKIN
                    || equippedType[i] == ELYTRA) {
                continue;
            }
            // [SURV-X] degrade 를 거치지 않는 방어구 마모 경로에도 같은 내구성 판정을 건다.
            int unbreaking = EnchantmentRules.enchantLevel(
                    equippedEnchantments[i], EnchantmentRules.UNBREAKING);
            int applied = wear;
            if (unbreaking > 0) {
                applied = 0;
                for (int tick = 0; tick < wear; tick++) {
                    if (!EnchantmentRules.unbreakingSkipsDurability(unbreaking, true,
                            durabilityRandom.nextInt(EnchantmentRules.MILLI),
                            durabilityRandom.nextInt(Integer.MAX_VALUE))) {
                        applied++;
                    }
                }
            }
            if (applied == 0) continue;
            equippedDurability[i] -= applied;
            changed = true;
            if (equippedDurability[i] <= 0) {
                broken.add(equippedType[i]);
                equippedType[i] = EMPTY;
                equippedDurability[i] = 0;
                equippedEnchantments[i] = EnchantmentRules.EMPTY_ENCHANTMENTS;
                equippedItemComponentData[i] = null;
            }
        }
        if (changed) advancePersistenceRevision();
        return broken;
    }

    /**
     * [ENCHANT-WIDE] 착용 방어구 한 부위를 {@code points} 만큼 닳게 한다(가시 {@code change_item_damage 2}).
     * 바닐라 {@code hurtAndBreak} 처럼 한 점마다 내구성 판정을 거친다. 부서지면 그 종류를 돌려준다(없으면 0).
     */
    public synchronized short damageEquippedPiece(ArmorSlot slot, int points) {
        if (settlementMutationBlocked() || points <= 0) return EMPTY;
        int i = slot.ordinal();
        if (equippedType[i] == EMPTY || equippedType[i] == (short) Blocks.CARVED_PUMPKIN
                || !isDurable(equippedType[i])) {
            return EMPTY;
        }
        int unbreaking = wideEnchantmentsOf(equippedEnchantments[i], equippedItemComponentData[i])
                .level(EnchantmentRules.UNBREAKING);
        int applied = 0;
        for (int point = 0; point < points; point++) {
            if (unbreaking > 0 && EnchantmentRules.unbreakingSkipsDurability(unbreaking, true,
                    durabilityRandom.nextInt(EnchantmentRules.MILLI),
                    durabilityRandom.nextInt(Integer.MAX_VALUE))) {
                continue;
            }
            applied++;
        }
        if (applied == 0) return EMPTY;
        preflightRevisionCapacity();
        short broken = EMPTY;
        equippedDurability[i] -= applied;
        if (equippedDurability[i] <= 0) {
            broken = equippedType[i];
            equippedType[i] = EMPTY;
            equippedDurability[i] = 0;
            equippedEnchantments[i] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            equippedItemComponentData[i] = null;
        }
        advancePersistenceRevision();
        return broken;
    }

    /**
     * [ENCHANT-WIDE] 수선. 26.3 {@code ExperienceOrb.repairPlayerItems}: 주손·보조손·장화·각반·흉갑·투구
     * 순서({@code EquipmentSlot.values()})로 수선이 붙고 닳은 아이템을 모아 하나를 무작위로 고르고,
     * {@link EnchantmentRules#mendingRepair} 로 고친 뒤 남은 XP 로 다시 고른다. 고칠 것이 없으면 남은 XP 를
     * 그대로 돌려주고, 그 값만 플레이어 경험치로 들어간다.
     *
     * @param randomIndex {@code bound -> [0, bound)} 한 번의 선택 굴림({@code Util.getRandomSafe})
     * @return 경험치로 들어갈 남은 XP
     */
    public synchronized int applyMending(int xp, java.util.function.IntUnaryOperator randomIndex) {
        if (settlementMutationBlocked() || xp <= 0) return xp;
        int remaining = xp;
        boolean changed = false;
        while (remaining > 0) {
            int[] candidates = new int[6];
            int found = 0;
            if (mendable(itemType[selectedSlot], durability[selectedSlot],
                    enchantments[selectedSlot], itemComponentData[selectedSlot])) {
                candidates[found++] = -1;
            }
            if (!offhand.isEmpty() && mendable(offhand.itemType(), offhand.durability(),
                    offhand.enchantments(), offhand.itemComponentData())) {
                candidates[found++] = -2;
            }
            for (ArmorSlot armor : new ArmorSlot[] {ArmorSlot.BOOTS, ArmorSlot.LEGGINGS,
                    ArmorSlot.CHESTPLATE, ArmorSlot.HELMET}) {
                int i = armor.ordinal();
                if (mendable(equippedType[i], equippedDurability[i], equippedEnchantments[i],
                        equippedItemComponentData[i])) {
                    candidates[found++] = i;
                }
            }
            if (found == 0) break;
            int pick = candidates[randomIndex.applyAsInt(found)];
            if (!changed) preflightRevisionCapacity();
            changed = true;
            if (pick == -1) {
                int[] result = EnchantmentRules.mendingRepair(remaining,
                        initialDurability(itemType[selectedSlot]) - durability[selectedSlot]);
                durability[selectedSlot] += result[0];
                remaining = result[1];
            } else if (pick == -2) {
                int[] result = EnchantmentRules.mendingRepair(remaining,
                        initialDurability(offhand.itemType()) - offhand.durability());
                offhand = new StackSnapshot(offhand.itemType(), offhand.count(),
                        offhand.durability() + result[0], offhand.enchantments(),
                        offhand.mapId(), offhand.shulkerId(), offhand.bucketMobData(),
                        offhand.itemComponentData());
                remaining = result[1];
            } else {
                int[] result = EnchantmentRules.mendingRepair(remaining,
                        initialDurability(equippedType[pick]) - equippedDurability[pick]);
                equippedDurability[pick] += result[0];
                remaining = result[1];
            }
        }
        if (changed) advancePersistenceRevision();
        return remaining;
    }

    private static boolean mendable(short type, int currentDurability, long mask,
            String componentData) {
        return type != EMPTY && isDurable(type)
                && currentDurability < initialDurability(type)
                && wideEnchantmentsOf(mask, componentData).level(EnchantmentRules.MENDING) > 0;
    }

    /** [ENCHANT-WIDE] 수선을 실은 XP 정산 세대, 경험치로 들어갈 남은 XP, 아이템이 고쳐졌는가. */
    public record MendingSettlement(CompletePersistenceSnapshot committed, int leftoverXp,
            boolean repaired) {}

    /** 흉갑 부위에 활공 가능한 겉날개가 있는가(내구 1 은 파손 직전이라 활공하지 못한다). */
    public synchronized boolean elytraFlyable() {
        int slot = ArmorSlot.CHESTPLATE.ordinal();
        return equippedType[slot] == ELYTRA
                && equippedDurability[slot] >= ELYTRA_MIN_FLYABLE_DURABILITY;
    }

    /**
     * 활공 1초에 해당하는 겉날개 마모 1 을 적용합니다. 바닐라는 내구 1 에서 활공만 막고
     * 아이템을 부수지 않으므로 여기서도 1 밑으로 내리지 않습니다.
     *
     * @return 실제로 내구가 줄었으면 true
     */
    public synchronized boolean wearEquippedElytra() {
        if (settlementMutationBlocked()) return false;
        int slot = ArmorSlot.CHESTPLATE.ordinal();
        if (equippedType[slot] != ELYTRA || equippedDurability[slot] <= 1) return false;
        preflightRevisionCapacity();
        equippedDurability[slot]--;
        advancePersistenceRevision();
        return true;
    }

    /**
     * [CURSE] 소실의 저주가 걸린 스택은 사망 드랍에 실리지 않는다(바닐라 vanishing_curse).
     * 칸을 비우는 것은 호출부가 이미 무조건 하므로, 여기서 거르면 아이템은 그대로 사라진다.
     */
    private static void addDeathDrop(List<DroppedStack> drops, DroppedStack stack) {
        if (EnchantmentRules.enchantLevel(stack.enchantments(),
                EnchantmentRules.VANISHING_CURSE) > 0) return;
        drops.add(stack);
    }

    /**
     * 사망 순간 핫바·가방·착용 방어구를 모두 꺼내고 인벤토리를 비웁니다.
     * 한 번에 스냅샷과 비우기를 처리해 같은 사망을 두 번 드랍하지 않습니다.
     *
     * <p>[CURSE] 소실의 저주가 걸린 스택은 드랍 목록에서 빠지고 그대로 사라집니다
     * (바닐라 vanishing_curse). 칸을 비우는 것은 저주와 무관하게 언제나 같습니다 —
     * 결속의 저주가 걸린 착용품도 사망으로는 벗겨집니다.
     */
    public synchronized List<DroppedStack> drainForDeath() {
        if (settlementMutationBlocked()) return List.of();
        preflightRevisionCapacity();
        List<DroppedStack> drops = new ArrayList<>();
        for (StackSnapshot payment : merchantPaymentSnapshot()) {
            if (!payment.isEmpty()) addDeathDrop(drops, DroppedStack.exact(payment.itemType(), payment.count(),
                    payment.durability(), payment.enchantments(), payment.mapId(), payment.shulkerId(),
                    payment.bucketMobData(), payment.itemComponentData()));
        }
        restoreMerchantPayments(new StackSnapshot[]{StackSnapshot.EMPTY, StackSnapshot.EMPTY});
        if (cursorType != EMPTY) {
            addDeathDrop(drops, new DroppedStack(cursorType, cursorCount, cursorDurability,
                    cursorEnchantments, cursorMapId, cursorShulkerId, cursorBucketMobData,
                    cursorItemComponentData));
            clearCursor();
        }
        int craftingCells = craftingSlotCount;
        for (int slot = 0; slot < craftingCells; slot++) {
            if (craftingType[slot] != EMPTY) {
                addDeathDrop(drops, new DroppedStack(craftingType[slot], craftingCount[slot],
                        craftingDurability[slot], craftingEnchantments[slot], craftingMapIds[slot],
                        craftingShulkerIds[slot], craftingBucketMobData[slot],
                        craftingItemComponentData[slot]));
            }
            clearCraftingSlot(slot);
        }
        craftingGridSize = 0;
        craftingSlotCount = 0;
        craftingStonecutter = false;
        craftingSelection = null;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemType[slot] != EMPTY && count[slot] > 0) {
                addDeathDrop(drops, new DroppedStack(itemType[slot], count[slot], durability[slot],
                        enchantments[slot], mapIds[slot], shulkerIds[slot],
                        bucketMobData[slot], itemComponentData[slot]));
            }
            itemType[slot] = EMPTY;
            count[slot] = 0;
            durability[slot] = 0;
            enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
        for (int slot = 0; slot < equippedType.length; slot++) {
            if (equippedType[slot] != EMPTY) {
                addDeathDrop(drops, new DroppedStack(equippedType[slot], 1, equippedDurability[slot],
                        equippedEnchantments[slot], 0, 0, null,
                        equippedItemComponentData[slot]));
            }
            equippedType[slot] = EMPTY;
            equippedDurability[slot] = 0;
            equippedEnchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            equippedItemComponentData[slot] = null;
        }
        if (!offhand.isEmpty()) {
            addDeathDrop(drops, new DroppedStack(offhand.itemType(), offhand.count(), offhand.durability(),
                    offhand.enchantments(), offhand.mapId(), offhand.shulkerId(),
                    offhand.bucketMobData(), offhand.itemComponentData()));
            offhand = StackSnapshot.EMPTY;
        }
        selectedSlot = 0;
        advancePersistenceRevision();
        return List.copyOf(drops);
    }

    /** [CONTAINER-MENUS] 광산 수레 아이템 다섯 종(2431~2435) 중 하나인가. */
    public static boolean isMinecartItem(short type) {
        int id = Short.toUnsignedInt(type);
        return id >= Blocks.MINECART && id <= Blocks.TNT_MINECART;
    }

    /** 아이템 종류별 스택 상한(도구/검·보트·양동이·스튜 1, 그 외 64). */
    public static int stackMax(short type) {
        if (isDurable(type) || type == BOAT || isBucket(type) || type == MILK_BUCKET
                || type == COD_BUCKET || type == SALMON_BUCKET || type == TROPICAL_FISH_BUCKET
                || type == SULFUR_CUBE_BUCKET
                || type == POWDER_SNOW_BUCKET
                || type == MUSHROOM_STEW || type == BEETROOT_SOUP || type == RABBIT_STEW
                // [COOKING] 수상한 스튜도 바닐라 max_stack_size 1 인 그릇 음식이다.
                || isSuspiciousStew(type)
                // [TRIAL-GAP] 불길한 병은 바닐라 Items.OMINOUS_BOTTLE 이 stacksTo 를 부르지 않아
                // 기본 64 다. 증폭 컴포넌트가 다른 병은 itemComponentData 가 달라 합쳐지지 않는다.
                || type == (short) Blocks.WATER_BOTTLE
                // 뿔피리·오르골은 소모되지 않는 단일 연주 도구라 겹쳐 들 이유가 없다.
                || type == BATTERING_HORN || type == ILLAGER_MUSIC_BOX
                || type >= MUSIC_DISC_13 && type <= MUSIC_DISC_BOUNCE
                || type == MUSIC_DISC_RELIC
                // [TRIAL-GAP] 음반 둘 · 현수막 무늬 둘도 바닐라 max_stack_size 1 이다.
                || type == MUSIC_DISC_PRECIPICE || type == MUSIC_DISC_CREATOR
                // [UTILITY] 범용 물약 세 형태와 음반 15종도 바닐라 max_stack_size 1 이다.
                || type >= CONTENTS_POTION && type <= MUSIC_DISC_WARD
                || type == GUSTER_BANNER_PATTERN || type == FLOW_BANNER_PATTERN
                // 바닐라 saddle · enchanted_book 의 max_stack_size 는 1 이다.
                || type == SADDLE || type == ENCHANTED_BOOK || type == TOTEM_OF_UNDYING
                || type == WRITABLE_BOOK
                // [HARNESS] 하네스도 탈것에 <b>한 벌만</b> 얹는 장비라 바닐라 max_stack_size 가
                // 1 이다(안장과 같은 계약). 16색 전부 같다.
                || isHarness(type)
                // 바닐라 potion · splash_potion 의 max_stack_size 는 1 이다(어색한 물약 포함).
                || isPotion(type)
                // [SHULKER-CONTENTS] 바닐라 셜커 상자 17종의 max_stack_size 도 1 이다 — 27칸을
                // 물고 다니는 상자가 겹쳐 쌓이면 한 참조 ID 가 여러 아이템에 붙어 27칸이
                // 복제된다. 정적판 `stackMax` 도 같은 값이다.
                || isShulkerBox(type)
                || Blocks.isNautilusArmorTier(Short.toUnsignedInt(type))
                || type == COPPER_HORSE_ARMOR || type == NETHERITE_HORSE_ARMOR
                || type >= TADPOLE_BUCKET && type <= DIAMOND_HORSE_ARMOR
                || type == GOAT_HORN || type == AXOLOTL_BUCKET
                // [CONTAINER-MENUS] 광산 수레 다섯 종의 바닐라 max_stack_size 는 1 이다.
                || isMinecartItem(type)) {
            return 1;
        }
        // 바닐라 egg 의 max_stack_size 는 16 이다(눈덩이와 같은 투척 스택).
        // [ZOMBIE-ANIMAL] 상한 달걀도 같은 투척물이라 같은 상한을 쓴다.
        // [GOLD-FOOD] 꿀이 든 병의 바닐라 max_stack_size 도 16 이다(달걀과 같은 상한 값).
        if (type == EGG || type == BLUE_EGG || type == BROWN_EGG
                || type == SPOILED_EGG || type == HONEY_BOTTLE
                || type == WRITTEN_BOOK || Blocks.isBanner(Short.toUnsignedInt(type))
                // [CONTAINER-MENUS] 갑옷 거치대의 바닐라 max_stack_size 도 16 이다.
                || type == ARMOR_STAND
                || type == (short) Blocks.SPRUCE_HANGING_SIGN
                || type == (short) Blocks.OAK_HANGING_SIGN
                || type == (short) Blocks.BAMBOO_HANGING_SIGN) {
            return EGG_STACK_MAX;
        }
        return STACK_MAX;
    }

    /**
     * 마실 수 있는 물약(어색한 물약 포함)인가. 투척 물약은 포함하지 않는다.
     * [POTION-UPGRADE] 기본 다섯 종 803~808 과 강화 여덟 종 817~824 두 구간이다.
     */
    public static boolean isDrinkablePotion(short type) {
        return type >= AWKWARD_POTION && type <= POTION_HARMING
                || type >= POTION_SWIFTNESS_LONG && type <= POTION_HARMING_II
                // [BRIMSTONE] 화염 저항 두 종 1463~1464.
                || type >= POTION_FIRE_RESISTANCE && type <= POTION_FIRE_RESISTANCE_LONG
                // [POTION-GAP] 힘 셋 1760~1762 · 수중 호흡 둘 1766~1767 ·
                // 도약 셋 1770~1772 · 야간 투시 둘 1776~1777.
                || type >= POTION_STRENGTH && type <= POTION_STRENGTH_II
                || type >= POTION_WATER_BREATHING && type <= POTION_WATER_BREATHING_LONG
                || type >= POTION_LEAPING && type <= POTION_LEAPING_II
                || type >= POTION_NIGHT_VISION && type <= POTION_NIGHT_VISION_LONG
                // [TRIAL-GAP] 재생의 물약 2314.
                || type == POTION_REGENERATION || type == POTION_STRONG_HEALING
                // [UTILITY] 범용 물약(potion_contents 가 종류를 싣는다).
                || type == CONTENTS_POTION;
    }

    /** [TRIAL-GAP] 잔류형 물약 일곱 종 2315~2321. 정적판 {@code isLingeringPotion} 과 같다. */
    public static boolean isLingeringPotion(short type) {
        return type >= LINGERING_POTION_WIND_CHARGED && type <= LINGERING_POTION_SLOW_FALLING
                // [UTILITY] 범용 잔류형 물약.
                || type == CONTENTS_LINGERING_POTION;
    }

    /** [TRIAL-GAP] 효과 화살 두 종 2322~2323. 정적판 {@code isTippedArrow} 와 같다. */
    public static boolean isTippedArrow(short type) {
        return type == TIPPED_ARROW_POISON || type == TIPPED_ARROW_STRONG_SLOWNESS;
    }

    /**
     * 우클릭으로 던지는 투척 물약인가.
     * [POTION-UPGRADE] 기본 다섯 종 809~813 과 강화 여덟 종 825~832 두 구간이다.
     */
    public static boolean isSplashPotion(short type) {
        return type >= SPLASH_POTION_SWIFTNESS && type <= SPLASH_POTION_HARMING
                || type >= SPLASH_POTION_SWIFTNESS_LONG && type <= SPLASH_POTION_HARMING_II
                // [BRIMSTONE] 화염 저항 투척 두 종 1465~1466.
                || type >= SPLASH_POTION_FIRE_RESISTANCE
                    && type <= SPLASH_POTION_FIRE_RESISTANCE_LONG
                // [POTION-GAP] 힘 셋 1763~1765 · 수중 호흡 둘 1768~1769 ·
                // 도약 셋 1773~1775 · 야간 투시 둘 1778~1779.
                || type >= SPLASH_POTION_STRENGTH && type <= SPLASH_POTION_STRENGTH_II
                || type >= SPLASH_POTION_WATER_BREATHING
                    && type <= SPLASH_POTION_WATER_BREATHING_LONG
                || type >= SPLASH_POTION_LEAPING && type <= SPLASH_POTION_LEAPING_II
                || type >= SPLASH_POTION_NIGHT_VISION
                    && type <= SPLASH_POTION_NIGHT_VISION_LONG
                // [UTILITY] 범용 투척용 물약.
                || type == CONTENTS_SPLASH_POTION;
    }

    /** 물약 계열(마시는 물약 + 투척 물약 + 잔류형 물약) 전체. 스택 상한 1의 근거다. */
    public static boolean isPotion(short type) {
        return isDrinkablePotion(type) || isSplashPotion(type) || isLingeringPotion(type);
    }

    /** 블록 유체 상호작용에 쓰는 빈/물/용암 양동이 중 하나인가. 우유는 포함하지 않는다. */
    public static boolean isBucket(short type) {
        return type >= BUCKET && type <= LAVA_BUCKET || type == POWDER_SNOW_BUCKET;
    }

    // ── 제작 컨테이너·이동·내구(틱 스레드에서 호출, 저장 스냅샷과 잠금 공유) ──

    public synchronized boolean craftingOpen() {
        return craftingGridSize != 0;
    }

    public synchronized int craftingGridSize() {
        return craftingGridSize;
    }

    public synchronized int craftingSlotCount() { return craftingSlotCount; }

    /**
     * 개인 2×2 또는 작업대 3×3 컨테이너를 엽니다. 기존 제작 상태가 있으면 먼저 인벤토리로
     * 반환하며, 반환할 수 없는 아이템만 호출자가 월드 드랍으로 내보냅니다.
     */
    public synchronized List<DroppedStack> openCrafting(int gridSize) {
        return openCrafting(gridSize, gridSize * gridSize, false);
    }

    /** Opens either an ordinary grid or the one-input stonecutter grid. */
    public synchronized List<DroppedStack> openCrafting(int gridSize, boolean stonecutter) {
        return openCrafting(gridSize, stonecutter ? 1 : gridSize * gridSize, stonecutter);
    }

    public synchronized List<DroppedStack> openCrafting(int gridSize, int slotCount,
            boolean stonecutter) {
        if (settlementMutationBlocked()) return List.of();
        if (gridSize != 2 && gridSize != 3) return List.of();
        if (slotCount < 1 || slotCount > gridSize * gridSize) return List.of();
        preflightRevisionCapacity();
        boolean wasOpen = craftingOpen();
        if (wasOpen) {
            InventoryArrays normalized = currentNormalizedInventory();
            if (normalized == null) {
                throw new IllegalStateException("crafting session cannot be reopened losslessly");
            }
            // Close and reopen as one state transition. A close at MAX_VALUE - 1
            // must not advance to terminal and then write the new session fields.
            commitInventoryArrays(normalized);
            clearCursor();
            for (int slot = 0; slot < craftingSlotCount; slot++) {
                clearCraftingSlot(slot);
            }
            craftingGridSize = 0;
            craftingSlotCount = 0;
            craftingStonecutter = false;
            craftingSelection = null;
        }
        craftingGridSize = gridSize;
        craftingSlotCount = slotCount;
        craftingStonecutter = stonecutter;
        craftingSelection = null;
        if (wasOpen) advancePersistenceRevision();
        else {
            persistenceRevisionBound = true;
            persistenceLifecycle = PersistenceLifecycle.BASELINE_BOUND;
        }
        requireLosslessTransientFold();
        return List.of();
    }

    /** [STONECUT] 열린 제작 세션이 절단기 세션인가. */
    public synchronized boolean craftingStonecutter() {
        return craftingStonecutter;
    }

    /** [STONECUT] 지금 고른 절단 레시피 id. 없거나 더 이상 유도 불가면 null. */
    public synchronized String craftingSelection() {
        return craftingStonecutter && currentRecipe() != null ? craftingSelection : null;
    }

    /**
     * [STONECUT] 클라가 고른 절단 산출을 세션에 세운다. 권위는 선택 id 를 그대로 믿지 않고
     * 지금 입력 칸에서 그 산출이 실제로 유도되는지 표로 재검증한다(거부하면 false).
     */
    public synchronized boolean selectStonecutterRecipe(String recipeId) {
        if (settlementMutationBlocked()) return false;
        if (!craftingOpen() || !craftingStonecutter) return false;
        if (recipeId == null) {
            if (craftingSelection == null) return false;
            preflightRevisionCapacity();
            craftingSelection = null;
            advancePersistenceRevision();
            return true;
        }
        if (CraftRecipe.matchStonecutting(craftingType[0], recipeId) == null) return false;
        if (recipeId.equals(craftingSelection)) return false;
        preflightRevisionCapacity();
        craftingSelection = recipeId;
        advancePersistenceRevision();
        return true;
    }

    public synchronized short craftingItemType(int slot) {
        return validCraftingSlot(slot) ? craftingType[slot] : EMPTY;
    }

    public synchronized int craftingCount(int slot) {
        return validCraftingSlot(slot) ? craftingCount[slot] : 0;
    }

    public synchronized int craftingDurability(int slot) {
        return validCraftingSlot(slot) ? craftingDurability[slot] : 0;
    }

    /** [SURV-X] 제작 격자 한 칸의 인챈트 마스크. */
    public synchronized long craftingEnchantments(int slot) {
        return validCraftingSlot(slot)
                ? craftingEnchantments[slot] : EnchantmentRules.EMPTY_ENCHANTMENTS;
    }

    public synchronized int craftingMapId(int slot) {
        return validCraftingSlot(slot) ? craftingMapIds[slot] : 0;
    }

    public synchronized int craftingShulkerId(int slot) {
        return validCraftingSlot(slot) ? craftingShulkerIds[slot] : 0;
    }

    public synchronized String craftingBucketMobData(int slot) {
        return validCraftingSlot(slot) ? craftingBucketMobData[slot] : null;
    }

    public synchronized String craftingItemComponentData(int slot) {
        return validCraftingSlot(slot) ? craftingItemComponentData[slot] : null;
    }

    public synchronized StackSnapshot craftingStackSnapshot(int slot) {
        return validCraftingSlot(slot) ? craftingStack(slot) : StackSnapshot.EMPTY;
    }

    public synchronized short cursorType() {
        return cursorType;
    }

    public synchronized int cursorCount() {
        return cursorCount;
    }

    public synchronized int cursorDurability() {
        return cursorDurability;
    }

    /** [SURV-X] 커서 스택의 인챈트 마스크. */
    public synchronized long cursorEnchantments() {
        return cursorEnchantments;
    }

    public synchronized int cursorMapId() {
        return cursorMapId;
    }

    public synchronized int cursorShulkerId() {
        return cursorShulkerId;
    }

    public synchronized String cursorBucketMobData() {
        return cursorBucketMobData;
    }

    public synchronized String cursorItemComponentData() { return cursorItemComponentData; }

    /** Merchant result pickup preflight for the shared container cursor. */
    public synchronized boolean canAcceptPlainContainerResult(short type, int amount) {
        if (settlementMutationBlocked() || craftingOpen() || type == EMPTY || amount <= 0) return false;
        if (cursorType == EMPTY) return amount <= stackMax(type);
        return cursorType == type && cursorDurability == initialDurability(type)
                && cursorEnchantments == EnchantmentRules.EMPTY_ENCHANTMENTS
                && cursorMapId == 0 && cursorShulkerId == 0 && cursorBucketMobData == null
                && cursorItemComponentData == null && cursorCount + amount <= stackMax(type);
    }

    /** Moves an already granted component-free merchant result from inventory onto the cursor. */
    public synchronized boolean movePlainInventoryResultToCursor(short type, int amount) {
        if (!canAcceptPlainContainerResult(type, amount)) return false;
        int available = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemType[slot] == type && durability[slot] == initialDurability(type)
                    && enchantments[slot] == EnchantmentRules.EMPTY_ENCHANTMENTS
                    && mapIds[slot] == 0 && shulkerIds[slot] == 0
                    && bucketMobData[slot] == null && itemComponentData[slot] == null) {
                available += count[slot];
            }
        }
        if (available < amount) return false;
        preflightRevisionCapacity();
        short[] nextTypes = itemType.clone();
        int[] nextCounts = count.clone();
        int[] nextDurabilities = durability.clone();
        long[] nextEnchantments = enchantments.clone();
        int[] nextMapIds = mapIds.clone();
        int[] nextShulkerIds = shulkerIds.clone();
        String[] nextBucket = bucketMobData.clone();
        String[] nextComponents = itemComponentData.clone();
        int remaining = amount;
        for (int slot = 0; slot < SLOTS && remaining > 0; slot++) {
            if (nextTypes[slot] != type || nextDurabilities[slot] != initialDurability(type)
                    || nextEnchantments[slot] != EnchantmentRules.EMPTY_ENCHANTMENTS
                    || nextMapIds[slot] != 0 || nextShulkerIds[slot] != 0
                    || nextBucket[slot] != null || nextComponents[slot] != null) continue;
            int taken = Math.min(remaining, nextCounts[slot]);
            nextCounts[slot] -= taken;
            remaining -= taken;
            if (nextCounts[slot] == 0) {
                clearComponentSlot(nextTypes, nextCounts, nextDurabilities, nextEnchantments,
                        nextMapIds, nextShulkerIds, nextBucket, nextComponents, slot);
            }
        }
        if (remaining != 0) return false;
        System.arraycopy(nextTypes, 0, itemType, 0, SLOTS);
        System.arraycopy(nextCounts, 0, count, 0, SLOTS);
        System.arraycopy(nextDurabilities, 0, durability, 0, SLOTS);
        System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
        if (cursorType == EMPTY) {
            setCursor(type, amount, initialDurability(type), EnchantmentRules.EMPTY_ENCHANTMENTS,
                    0, 0, null, null);
        } else {
            cursorCount += amount;
        }
        advancePersistenceRevision();
        return true;
    }

    public synchronized boolean appendContainerResult(StackSnapshot prototype,int amount) {
        if(!canAcceptContainerResult(prototype,amount)) return false;
        preflightRevisionCapacity();
        if(cursorType==EMPTY) setCursor(prototype.itemType(),amount,prototype.durability(),prototype.enchantments(),prototype.mapId(),prototype.shulkerId(),prototype.bucketMobData(),prototype.itemComponentData());
        else cursorCount+=amount;
        advancePersistenceRevision();
        return true;
    }

    public synchronized boolean canAcceptContainerResult(StackSnapshot prototype,int amount) {
        if(settlementMutationBlocked() || craftingOpen() || prototype.itemType()==EMPTY || amount<=0) return false;
        return (cursorType==EMPTY || stack(cursorStack(),prototype)) && cursorCount+amount<=stackMax(prototype.itemType());
    }

    /** Moves an already granted exact merchant result from inventory onto the cursor. */
    public synchronized boolean moveInventoryResultToCursor(StackSnapshot prototype, int amount) {
        short type=prototype.itemType();
        if (!canAcceptContainerResult(prototype, amount)) return false;
        int available = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (stack(new StackSnapshot(itemType[slot],count[slot],durability[slot],enchantments[slot],mapIds[slot],shulkerIds[slot],bucketMobData[slot],itemComponentData[slot]),prototype)) {
                available += count[slot];
            }
        }
        if (available < amount) return false;
        preflightRevisionCapacity();
        short[] nextTypes = itemType.clone();
        int[] nextCounts = count.clone();
        int[] nextDurabilities = durability.clone();
        long[] nextEnchantments = enchantments.clone();
        int[] nextMapIds = mapIds.clone();
        int[] nextShulkerIds = shulkerIds.clone();
        String[] nextBucket = bucketMobData.clone();
        String[] nextComponents = itemComponentData.clone();
        int remaining = amount;
        for (int slot = 0; slot < SLOTS && remaining > 0; slot++) {
            if (!stack(new StackSnapshot(nextTypes[slot],nextCounts[slot],nextDurabilities[slot],nextEnchantments[slot],nextMapIds[slot],nextShulkerIds[slot],nextBucket[slot],nextComponents[slot]),prototype)) continue;
            int taken = Math.min(remaining, nextCounts[slot]);
            nextCounts[slot] -= taken;
            remaining -= taken;
            if (nextCounts[slot] == 0) {
                clearComponentSlot(nextTypes, nextCounts, nextDurabilities, nextEnchantments,
                        nextMapIds, nextShulkerIds, nextBucket, nextComponents, slot);
            }
        }
        if (remaining != 0) return false;
        System.arraycopy(nextTypes, 0, itemType, 0, SLOTS);
        System.arraycopy(nextCounts, 0, count, 0, SLOTS);
        System.arraycopy(nextDurabilities, 0, durability, 0, SLOTS);
        System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
        if (cursorType == EMPTY) {
            setCursor(type, amount, prototype.durability(), prototype.enchantments(),
                    prototype.mapId(), prototype.shulkerId(), prototype.bucketMobData(), prototype.itemComponentData());
        } else {
            cursorCount += amount;
        }
        advancePersistenceRevision();
        return true;
    }

    public synchronized AnvilRules.Plan planAnvilResult(String requestedName, boolean creative) {
        if (settlementMutationBlocked()) return null;
        clearStationPlan();
        if (!craftingOpen() || craftingSlotCount != 2 || craftingStonecutter) return null;
        try {
            StackSnapshot left = craftingStack(0);
            StackSnapshot right = craftingStack(1);
            AnvilRules.Plan plan = AnvilRules.plan(left, left.itemComponents(), right,
                    right.itemComponents(), requestedName, creative);
            if (plan != null) {
                stationPlanKind = StationPlanKind.ANVIL;
                stationPlanRevision = revision;
                stationPlanInputDigest = stationInputDigest();
                stationPlanName = requestedName;
                stationPlanCreative = creative;
            }
            return plan;
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    public synchronized boolean takeAnvilResult(
            AnvilRules.Plan plan, boolean shift) {
        if (settlementMutationBlocked() || plan == null || plan.tooExpensive()
                || !stationPlanMatches(StationPlanKind.ANVIL)
                || !java.util.Objects.equals(stationPlanName, plan.customName())) return false;
        AnvilRules.Plan current;
        try {
            StackSnapshot left = craftingStack(0);
            StackSnapshot right = craftingStack(1);
            current = AnvilRules.plan(left, left.itemComponents(), right, right.itemComponents(),
                    stationPlanName, stationPlanCreative);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (current == null || current.tooExpensive() || !sameAnvilPlan(current, plan)) return false;
        String encoded = ItemComponentCodec.encode(
                current.result().itemType(), current.resultComponents());
        StackSnapshot result = new StackSnapshot(current.result().itemType(), current.result().count(),
                current.result().durability(), current.result().enchantments(), current.result().mapId(),
                current.result().shulkerId(), current.result().bucketMobData(), encoded);
        return takeWorkstationResult(result, craftingCount[0], current.rightConsumed(), 0, shift);
    }

    public synchronized GrindstoneRules.Plan planGrindstoneResult() {
        if (settlementMutationBlocked()) return null;
        clearStationPlan();
        if (!craftingOpen() || craftingSlotCount != 2) return null;
        GrindstoneRules.Plan plan = GrindstoneRules.plan(craftingStack(0), craftingStack(1));
        if (plan != null) {
            stationPlanKind = StationPlanKind.GRINDSTONE;
            stationPlanRevision = revision;
            stationPlanInputDigest = stationInputDigest();
        }
        return plan;
    }

    public synchronized boolean takeGrindstoneResult(
            GrindstoneRules.Plan plan, boolean shift) {
        if (settlementMutationBlocked() || plan == null
                || !stationPlanMatches(StationPlanKind.GRINDSTONE)) return false;
        GrindstoneRules.Plan current = GrindstoneRules.plan(craftingStack(0), craftingStack(1));
        if (current == null || !sameGrindstonePlan(current, plan)) return false;
        ItemComponentData source;
        try {
            source = craftingStack(0).itemComponents();
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        ItemComponentData reset = new ItemComponentData(source.customName(),
                source.bannerPatterns(), source.book(), 0, null);
        StackSnapshot raw = current.result();
        StackSnapshot result = new StackSnapshot(raw.itemType(), raw.count(), raw.durability(),
                raw.enchantments(), raw.mapId(), raw.shulkerId(), raw.bucketMobData(),
                ItemComponentCodec.encode(raw.itemType(), reset));
        return takeWorkstationResult(result, 1,
                craftingType[1] == EMPTY ? 0 : 1, 0, shift);
    }

    public synchronized StackSnapshot planLoomResult(LoomRules.Pattern pattern) {
        if (settlementMutationBlocked()) return null;
        clearStationPlan();
        if (!craftingOpen() || craftingSlotCount != 3 || pattern == null) return null;
        // [TRIAL-GAP] 무늬 칸(2)의 무늬 아이템이 고를 수 있는 무늬를 정한다. 무늬 아이템은 소모되지 않는다.
        ItemComponentData result = LoomRules.plan(craftingType[0], craftingType[1], craftingType[2],
                pattern, craftingStack(0).itemComponents());
        if (result == null) return null;
        StackSnapshot banner = craftingStack(0);
        StackSnapshot planned = new StackSnapshot(banner.itemType(), 1, banner.durability(), banner.enchantments(),
                banner.mapId(), banner.shulkerId(), banner.bucketMobData(),
                ItemComponentCodec.encode(banner.itemType(), result));
        stationPlanKind = StationPlanKind.LOOM;
        stationPlanRevision = revision;
        stationPlanInputDigest = stationInputDigest();
        stationPlanPattern = pattern;
        return planned;
    }

    public synchronized CartographyRules.Plan planCartographyResult(
            CartographyAuthorityWitness witness) {
        if (settlementMutationBlocked()) return null;
        clearStationPlan();
        if (!craftingOpen() || craftingSlotCount != 2 || witness == null
                || witness.sourceMapId <= 0 || witness.mapRevision < 0
                || craftingMapIds[0] != witness.sourceMapId) return null;
        if (CartographyRules.operation(craftingStack(0), craftingStack(1))
                != witness.operation) return null;
        CartographyRules.Plan plan = CartographyRules.plan(craftingStack(0), craftingStack(1),
                witness.currentScale, witness.locked, witness.allocatedMapId);
        if (plan != null) {
            stationPlanKind = StationPlanKind.CARTOGRAPHY;
            stationPlanRevision = revision;
            stationPlanInputDigest = stationInputDigest();
            stationPlanMapWitness = witness;
        }
        return plan;
    }

    public synchronized boolean takeCartographyResult(
            CartographyRules.Plan plan, boolean shift) {
        return takeCartographyResult(plan, stationPlanMapWitness, shift);
    }

    /**
     * Receives a cartography result only when pickup presents the same current durable-map
     * generation that produced the plan. A preview witness from an older raster revision cannot
     * authorize consumption after that map has advanced.
     */
    public synchronized boolean takeCartographyResult(
            CartographyRules.Plan plan, CartographyAuthorityWitness currentWitness,
            boolean shift) {
        if (settlementMutationBlocked() || plan == null
                || !stationPlanMatches(StationPlanKind.CARTOGRAPHY)
                || stationPlanMapWitness == null
                || currentWitness == null
                || !currentWitness.settlementAuthorized
                || !stationPlanMapWitness.sameAuthority(currentWitness)) return false;
        CartographyRules.Plan current = CartographyRules.plan(
                craftingStack(0), craftingStack(1), stationPlanMapWitness.currentScale,
                stationPlanMapWitness.locked, stationPlanMapWitness.allocatedMapId);
        if (current == null || current.operation() != stationPlanMapWitness.operation
                || !sameCartographyPlan(current, plan)) return false;
        return takeWorkstationResult(current.result(), 1, 1, 0, shift);
    }

    /**
     * Applies one exact cartography delta to a detached inventory. The caller owns the live
     * inventory lease; this method neither sees nor mutates that live instance.
     */
    public synchronized CartographyMutation stageCartographyResult(
            CartographyAuthorityWitness currentWitness, boolean shift) {
        if (currentWitness == null) return null;
        long beforeRevision = revision;
        StackSnapshot mapBefore = craftingStack(0);
        StackSnapshot additionBefore = craftingStack(1);
        StackSnapshot cursorBefore = cursorStack();
        CartographyRules.Plan plan = planCartographyResult(currentWitness);
        if (plan == null || !takeCartographyResult(plan, currentWitness, shift)) return null;
        if (revision != Math.addExact(beforeRevision, 1)) {
            throw new IllegalStateException("cartography must advance inventory exactly once");
        }
        StackSnapshot mapAfter = craftingStack(0);
        StackSnapshot additionAfter = craftingStack(1);
        if (!CartographyRules.consumedOne(mapBefore, mapAfter)
                || !CartographyRules.consumedOne(additionBefore, additionAfter)) {
            throw new IllegalStateException("cartography did not consume its exact two inputs");
        }
        return new CartographyMutation(plan.operation(), currentWitness.mapRevision,
                beforeRevision, revision, mapBefore, additionBefore, mapAfter, additionAfter,
                cursorBefore, cursorStack(), plan.result(), shift);
    }

    private StackSnapshot cursorStack() {
        return new StackSnapshot(cursorType, cursorCount, cursorDurability, cursorEnchantments,
                cursorMapId, cursorShulkerId, cursorBucketMobData, cursorItemComponentData);
    }

    public synchronized boolean takeLoomResult(StackSnapshot result, boolean shift) {
        if (settlementMutationBlocked() || result == null
                || !stationPlanMatches(StationPlanKind.LOOM)
                || stationPlanPattern == null) return false;
        ItemComponentData plannedComponents;
        try {
            plannedComponents = LoomRules.plan(craftingType[0], craftingType[1], craftingType[2],
                    stationPlanPattern, craftingStack(0).itemComponents());
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (plannedComponents == null) return false;
        StackSnapshot banner = craftingStack(0);
        StackSnapshot current = new StackSnapshot(banner.itemType(), 1, banner.durability(),
                banner.enchantments(), banner.mapId(), banner.shulkerId(), banner.bucketMobData(),
                ItemComponentCodec.encode(banner.itemType(), plannedComponents));
        if (!current.equals(result)) return false;
        return takeWorkstationResult(current, 1, 1, 0, shift);
    }

    private boolean takeWorkstationResult(StackSnapshot result, int consume0, int consume1,
            int consume2, boolean shift) {
        if (settlementMutationBlocked() || result == null
                || consume0 < 0 || consume1 < 0 || consume2 < 0
                || craftingCount[0] < consume0 || craftingCount[1] < consume1
                || craftingCount[2] < consume2) return false;
        preflightRevisionCapacity();
        if (shift) {
            short[] nextTypes = itemType.clone();
            int[] nextCounts = count.clone();
            int[] nextDurabilities = durability.clone();
            long[] nextEnchantments = enchantments.clone();
            int[] nextMapIds = mapIds.clone();
            int[] nextShulkerIds = shulkerIds.clone();
            String[] nextBucket = bucketMobData.clone();
            String[] nextComponents = itemComponentData.clone();
            if (!foldComponentStackRange(nextTypes, nextCounts, nextDurabilities,
                    nextEnchantments, nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    result, 0, SLOTS)) return false;
            System.arraycopy(nextTypes, 0, itemType, 0, SLOTS);
            System.arraycopy(nextCounts, 0, count, 0, SLOTS);
            System.arraycopy(nextDurabilities, 0, durability, 0, SLOTS);
            System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
            System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
            System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
            System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
            System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
        } else if (cursorType == EMPTY) {
            setCursor(result.itemType(), result.count(), result.durability(),
                    result.enchantments(), result.mapId(), result.shulkerId(),
                    result.bucketMobData(), result.itemComponentData());
        } else if (stack(new StackSnapshot(cursorType, cursorCount, cursorDurability,
                cursorEnchantments, cursorMapId, cursorShulkerId, cursorBucketMobData,
                cursorItemComponentData), result)
                && cursorCount + result.count() <= stackMax(result.itemType())) {
            cursorCount += result.count();
        } else return false;
        consumeCraftingSlotExact(0, consume0);
        consumeCraftingSlotExact(1, consume1);
        consumeCraftingSlotExact(2, consume2);
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return true;
    }

    private void clearStationPlan() {
        stationPlanKind = null;
        stationPlanRevision = -1L;
        stationPlanInputDigest = null;
        stationPlanName = null;
        stationPlanCreative = false;
        stationPlanPattern = null;
        stationPlanMapWitness = null;
    }

    private boolean stationPlanMatches(StationPlanKind kind) {
        return stationPlanKind == kind && stationPlanRevision == revision
                && stationPlanInputDigest != null
                && stationPlanInputDigest.equals(stationInputDigest());
    }

    private String stationInputDigest() {
        DigestBuilder digest = new DigestBuilder("player-station-input-v1");
        digest.longValue(inventoryIdentity);
        digest.longValue(revision);
        digest.intValue(craftingGridSize);
        digest.intValue(craftingSlotCount);
        digest.booleanValue(craftingStonecutter);
        digest.stringValue(craftingSelection);
        for (int slot = 0; slot < craftingType.length; slot++) {
            appendStack(digest, craftingType[slot], craftingCount[slot],
                    craftingDurability[slot], craftingEnchantments[slot], craftingMapIds[slot],
                    craftingShulkerIds[slot], craftingBucketMobData[slot],
                    craftingItemComponentData[slot]);
        }
        appendStack(digest, cursorType, cursorCount, cursorDurability, cursorEnchantments,
                cursorMapId, cursorShulkerId, cursorBucketMobData, cursorItemComponentData);
        return digest.finish();
    }

    private static boolean sameAnvilPlan(AnvilRules.Plan first, AnvilRules.Plan second) {
        return first.levelCost() == second.levelCost()
                && first.rightConsumed() == second.rightConsumed()
                && first.tooExpensive() == second.tooExpensive()
                && java.util.Objects.equals(first.customName(), second.customName())
                && first.result().equals(second.result())
                && java.util.Objects.equals(
                        ItemComponentCodec.encode(first.result().itemType(), first.resultComponents()),
                        ItemComponentCodec.encode(second.result().itemType(), second.resultComponents()));
    }

    private static boolean sameGrindstonePlan(
            GrindstoneRules.Plan first, GrindstoneRules.Plan second) {
        return first.minimumXp() == second.minimumXp()
                && first.maximumXp() == second.maximumXp()
                && first.result().equals(second.result());
    }

    private static boolean sameCartographyPlan(
            CartographyRules.Plan first, CartographyRules.Plan second) {
        return first.operation() == second.operation() && first.scale() == second.scale()
                && first.locked() == second.locked() && first.result().equals(second.result());
    }

    private static boolean stack(StackSnapshot left, StackSnapshot right) {
        return left.itemType() == right.itemType() && left.durability() == right.durability()
                && left.enchantments() == right.enchantments() && left.mapId() == right.mapId()
                && left.shulkerId() == right.shulkerId()
                && java.util.Objects.equals(left.bucketMobData(), right.bucketMobData())
                && java.util.Objects.equals(left.itemComponentData(), right.itemComponentData());
    }

    private void consumeCraftingSlotExact(int slot, int amount) {
        if (amount == 0) return;
        craftingCount[slot] -= amount;
        if (craftingCount[slot] == 0) clearCraftingSlot(slot);
    }

    public synchronized DroppedStack craftingResult() {
        CraftRecipe recipe = currentRecipe();
        if (recipe == null) return null;
        return new DroppedStack(recipe.outputType(), recipe.outputCount(),
                craftingOutputDurability(recipe),
                craftingOutputEnchantments(recipe), craftingOutputMapId(recipe),
                inheritedCraftingShulkerId(recipe.outputType()), null,
                craftingOutputItemComponentData(recipe));
    }

    /**
     * 대장장이의 template/base/addition(격자 0/1/2)을 손실 없는 순수 변환 계획으로 미리 봅니다.
     * 일반 제작 결과는 구성요소를 새로 초기화하므로 대장장이 결과는 반드시 이 경로를 씁니다.
     */
    public synchronized SmithingTransformRules.Plan planSmithingResult() {
        if (!craftingOpen() || craftingGridSize != 3 || craftingStonecutter) {
            return null;
        }
        try {
            // [ARMOR-TRIM] 한 세션이 두 바닐라 레시피 종류(네더라이트 변환 · 갑옷 장식)를 받는다.
            // 입력 조합이 서로 겹치지 않으므로 먼저 성립하는 쪽이 유일한 결과다.
            SmithingTransformRules.Plan transform = SmithingTransformRules.plan(
                    craftingStack(0), craftingStack(1), craftingStack(2));
            return transform != null ? transform
                    : SmithingTrimRules.plan(craftingStack(0), craftingStack(1), craftingStack(2));
        } catch (IllegalArgumentException malformedInput) {
            return null;
        }
    }

    /**
     * 대장장이 결과를 커서 또는 인벤토리로 옮기고 세 입력을 한 원자적 변경으로 소비합니다.
     * Shift 경로는 결과 전량을 먼저 복제 배열에 수납할 수 있을 때만 입력을 건드립니다.
     */
    public synchronized boolean takeSmithingResult(boolean shift) {
        if (settlementMutationBlocked()) return false;
        SmithingTransformRules.Plan plan = planSmithingResult();
        if (plan == null) return false;
        preflightRevisionCapacity();

        if (shift) {
            short[] nextType = itemType.clone();
            int[] nextCount = count.clone();
            int[] nextDurability = durability.clone();
            long[] nextEnchantments = enchantments.clone();
            int[] nextMapIds = mapIds.clone();
            int[] nextShulkerIds = shulkerIds.clone();
            String[] nextBucket = bucketMobData.clone();
            String[] nextComponents = itemComponentData.clone();
            StackSnapshot result = plan.result();
            int inserted = addToArrays(nextType, nextCount, nextDurability, nextEnchantments,
                    nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    result.itemType(), result.count(),
                    result.durability(), result.enchantments(), result.mapId(), result.shulkerId(),
                    result.bucketMobData(), result.itemComponentData(), 0, SLOTS);
            if (inserted != result.count()) return false;
            System.arraycopy(nextType, 0, itemType, 0, SLOTS);
            System.arraycopy(nextCount, 0, count, 0, SLOTS);
            System.arraycopy(nextDurability, 0, durability, 0, SLOTS);
            System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
            System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
            System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
            System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
            System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
        } else {
            if (cursorType != EMPTY) return false;
            StackSnapshot result = plan.result();
            setCursor(result.itemType(), result.count(), result.durability(),
                    result.enchantments(), result.mapId(), result.shulkerId(),
                    result.bucketMobData(), result.itemComponentData());
        }

        writeCraftingStack(0, plan.templateAfter());
        writeCraftingStack(1, plan.baseAfter());
        writeCraftingStack(2, plan.additionAfter());
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return true;
    }

    private StackSnapshot craftingStack(int slot) {
        return new StackSnapshot(craftingType[slot], craftingCount[slot],
                craftingDurability[slot], craftingEnchantments[slot], craftingMapIds[slot],
                craftingShulkerIds[slot], craftingBucketMobData[slot],
                craftingItemComponentData[slot]);
    }

    private void writeCraftingStack(int slot, StackSnapshot stack) {
        craftingType[slot] = stack.itemType();
        craftingCount[slot] = stack.count();
        craftingDurability[slot] = stack.durability();
        craftingEnchantments[slot] = stack.enchantments();
        craftingMapIds[slot] = stack.mapId();
        craftingShulkerIds[slot] = stack.shulkerId();
        craftingBucketMobData[slot] = stack.bucketMobData();
        craftingItemComponentData[slot] = stack.itemComponentData();
    }

    /**
     * [SHULKER-CONTENTS] 산출이 셜커 상자일 때 격자에서 물려받을 27칸 참조 ID.
     *
     * <p>염색은 <b>내용을 유지한 채 색만 바꾼다</b>([A] 바닐라 {@code ShulkerBoxColoring} 은
     * 입력 상자의 {@code BlockEntityTag} 를 산출로 복사한다). 그 유지는 레시피 표가 아니라
     * 이 경로가 소유한다 — 표에는 색만 있고 내용 개념이 없기 때문이다.
     *
     * <p>격자에 셜커 상자가 <b>정확히 하나</b> 있을 때만 물려받는다. 둘 이상이면 어느 27칸을
     * 살릴지 임의로 고르게 되고, 바닐라에도 셜커 둘을 넣는 레시피는 없다. 정적판
     * {@code inheritedShulkerId} 와 같은 판정이다.
     */
    private int inheritedCraftingShulkerId(short outputType) {
        return inheritedShulkerId(outputType, craftingType, craftingShulkerIds, craftingSlotCount);
    }

    private static int inheritedShulkerId(
            short outputType, short[] types, int[] shulkers, int cells) {
        if (!isShulkerBox(outputType)) return 0;
        int boxes = 0;
        int found = 0;
        for (int slot = 0; slot < cells; slot++) {
            if (types[slot] == EMPTY || !isShulkerBox(types[slot])) continue;
            boxes++;
            found = shulkers[slot];
        }
        return boxes == 1 ? found : 0;
    }

    private static String inheritedItemComponentData(
            short outputType, short[] types, String[] components, int cells) {
        if (!isShulkerBox(outputType)) return null;
        int foundSlot = -1;
        for (int slot = 0; slot < cells; slot++) {
            if (types[slot] == EMPTY || !isShulkerBox(types[slot])) continue;
            if (foundSlot >= 0) return null;
            foundSlot = slot;
        }
        if (foundSlot < 0) return null;
        ItemComponentData source = ItemComponentCodec.decode(
                types[foundSlot], components[foundSlot]);
        return ItemComponentCodec.encode(outputType, source);
    }

    private int craftingOutputMapId(CraftRecipe recipe) {
        return inheritedMapId(recipe, craftingType, craftingMapIds, craftingSlotCount);
    }

    private static int inheritedMapId(CraftRecipe recipe, short[] types, int[] maps, int cells) {
        if (!CraftRecipe.MAP_CLONING.equals(recipe.id())) return 0;
        for (int slot = 0; slot < cells; slot++) {
            if (isFilledMapItem(types[slot])) return maps[slot];
        }
        throw new IllegalStateException("map cloning recipe has no source map");
    }

    private String craftingOutputItemComponentData(CraftRecipe recipe) {
        return outputItemComponentData(
                recipe, craftingType, craftingItemComponentData, craftingSlotCount);
    }

    /**
     * [CONTAINER-MENUS] {@code CrafterBlock#dispenseFrom} over a positioned 3x3 grid (row-major,
     * the {@code CrafterBlockEntity} slot order): the recipe {@link CraftRecipe#match} finds, the
     * assembled result with the same component inheritance a player's crafting table applies,
     * and {@code CraftingRecipe#getRemainingItems} (one {@link InventoryRules#craftingRemainder}
     * per occupied cell that has one). {@code null} when no recipe matches.
     */
    public static CrafterCraft crafterCraft(StackSnapshot[] grid) {
        if (grid == null || grid.length != 9) return null;
        short[] types = new short[9];
        int[] shulkers = new int[9];
        int[] sourceMaps = new int[9];
        String[] components = new String[9];
        for (int slot = 0; slot < 9; slot++) {
            StackSnapshot stack = grid[slot];
            if (stack == null || stack.isEmpty()) {
                types[slot] = EMPTY;
                continue;
            }
            types[slot] = stack.itemType();
            shulkers[slot] = stack.shulkerId();
            sourceMaps[slot] = stack.mapId();
            components[slot] = stack.itemComponentData();
        }
        CraftRecipe recipe = CraftRecipe.match(types, 3, false);
        if (recipe == null) return null;
        short output = recipe.outputType();
        StackSnapshot result = new StackSnapshot(output, recipe.outputCount(),
                initialDurability(output), EnchantmentRules.EMPTY_ENCHANTMENTS, inheritedMapId(recipe, types, sourceMaps, 9),
                inheritedShulkerId(output, types, shulkers, 9), null,
                outputItemComponentData(recipe, types, components, 9));
        if (output == Blocks.FLESH_HOOKED_SPEAR) {
            for (StackSnapshot source : grid) {
                if (source == null || source.itemType() != Blocks.FLESH_BONE_SPEAR) continue;
                result = new StackSnapshot(output, 1, source.durability(), source.enchantments(),
                        source.mapId(), source.shulkerId(), source.bucketMobData(),
                        outputItemComponentData(recipe, types, components, 9));
                break;
            }
        }
        List<StackSnapshot> remainders = new java.util.ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            if (types[slot] == EMPTY) continue;
            short remainder = InventoryRules.craftingRemainder(types[slot]);
            if (remainder == EMPTY) continue;
            remainders.add(new StackSnapshot(remainder, 1, initialDurability(remainder),
                    EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null));
        }
        return new CrafterCraft(recipe, result, List.copyOf(remainders));
    }

    /** One crafter craft: the matched recipe, its assembled result and the remaining items. */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class CrafterCraft {
        CraftRecipe recipe;
        StackSnapshot result;
        List<StackSnapshot> remainders;
}

    private static String outputItemComponentData(
            CraftRecipe recipe, short[] types, String[] components, int cells) {
        short outputType = recipe.outputType();
        if (recipe.id().equals("suspicious_stew_open_eyeblossom") || recipe.id().equals("suspicious_stew_closed_eyeblossom")) {
            int flower = recipe.id().equals("suspicious_stew_open_eyeblossom") ? Blocks.OPEN_EYEBLOSSOM : Blocks.CLOSED_EYEBLOSSOM;
            return ItemComponentCodec.encode(outputType, com.gameexpert.engine.SuspiciousStewRules.flowerComponents(flower));
        }
        if (CraftRecipe.MAP_CLONING.equals(recipe.id())) {
            for (int slot = 0; slot < cells; slot++) {
                if (isFilledMapItem(types[slot])) return components[slot];
            }
            throw new IllegalStateException("map cloning recipe has no source components");
        }
        if (Blocks.isDecoratedPot(outputType) && cells == 9) {
            List<ItemComponentData.PotDecoration> faces = new java.util.ArrayList<>();
            for (int slot : new int[]{1, 3, 5, 7}) {
                ItemComponentData face = ItemComponentCodec.decode(types[slot], components[slot]);
                faces.add(new ItemComponentData.PotDecoration(types[slot], face.customName(), face.anvilUseCount()));
            }
            return ItemComponentCodec.encode(outputType, ItemComponentData.EMPTY.withPotDecorations(faces));
        }
        if (outputType == Blocks.FLESH_HOOKED_SPEAR) {
            for (int slot = 0; slot < cells; slot++) {
                if (types[slot] != Blocks.FLESH_BONE_SPEAR) continue;
                return ItemComponentCodec.encode(outputType,
                        ItemComponentCodec.decode(types[slot], components[slot]));
            }
            return null;
        }
        if (outputType == WRITABLE_BOOK) {
            return ItemComponentCodec.encode(outputType, ItemComponentData.EMPTY.withBook(
                    new ItemComponentData.BookData(null, null, List.of(""))));
        }
        if (CraftRecipe.SHIELD_DECORATION.equals(recipe.id())) {
            return shieldDecorationComponentData(types, components, cells);
        }
        if (!"dye_leather".equals(recipe.id())) {
            return inheritedItemComponentData(outputType, types, components, cells);
        }
        int red = 0, green = 0, blue = 0, brightness = 0, contributors = 0;
        ItemComponentData source = null;
        for (int slot = 0; slot < cells; slot++) {
            short type = types[slot];
            if (ItemComponentCodec.isDyeableLeather(type)) {
                source = ItemComponentCodec.decode(type, components[slot]);
                Integer existing = source.leatherColor();
                if (existing != null) {
                    int r = existing >>> 16 & 0xff;
                    int g = existing >>> 8 & 0xff;
                    int b = existing & 0xff;
                    red += r; green += g; blue += b;
                    brightness += Math.max(r, Math.max(g, b));
                    contributors++;
                }
            } else if (type >= WHITE_DYE && type <= BLACK_DYE) {
                int dye = DYE_RGB[type - WHITE_DYE];
                int r = dye >>> 16 & 0xff;
                int g = dye >>> 8 & 0xff;
                int b = dye & 0xff;
                red += r; green += g; blue += b;
                brightness += Math.max(r, Math.max(g, b));
                contributors++;
            }
        }
        if (source == null || contributors == 0) return null;
        red /= contributors; green /= contributors; blue /= contributors;
        int averageBrightness = brightness / contributors;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum > 0) {
            red = red * averageBrightness / maximum;
            green = green * averageBrightness / maximum;
            blue = blue * averageBrightness / maximum;
        }
        int color = Math.min(255, red) << 16
                | Math.min(255, green) << 8 | Math.min(255, blue);
        return ItemComponentCodec.encode(outputType, source.withLeatherColor(color));
    }

    /**
     * [SHIELD-PATTERN] 바닐라 {@code ShieldDecorationRecipe.assemble}: 방패의 구성요소를 그대로 두고
     * {@code BASE_COLOR}(현수막 색, 맨 앞 BASE 층)와 현수막의 {@code BANNER_PATTERNS} 를 싣는다.
     */
    private static String shieldDecorationComponentData(short[] types, String[] components, int cells) {
        ItemComponentData shield = null;
        short bannerType = EMPTY;
        ItemComponentData banner = null;
        for (int slot = 0; slot < cells; slot++) {
            short type = types[slot];
            if (type == SHIELD) {
                shield = ItemComponentCodec.decode(type, components[slot]);
            } else if (type != EMPTY && Blocks.isBanner(Short.toUnsignedInt(type))) {
                bannerType = type;
                banner = ItemComponentCodec.decode(type, components[slot]);
            }
        }
        if (shield == null || banner == null) return null;
        java.util.ArrayList<ItemComponentData.BannerLayer> layers = new java.util.ArrayList<>();
        layers.add(new ItemComponentData.BannerLayer(LoomRules.Pattern.BASE,
                Short.toUnsignedInt(bannerType) - Blocks.WHITE_BANNER));
        layers.addAll(banner.bannerPatterns());
        return ItemComponentCodec.encode(SHIELD, shield.withBannerPatterns(layers));
    }

    /**
     * [SHIELD-PATTERN] 이미 무늬가 있는 방패는 다시 장식할 수 없다(바닐라 {@code matches} 는
     * {@code BANNER_PATTERNS} 가 비어 있어야 한다). 무늬 없이 기본색만 있는 방패는 다시 된다.
     */
    private boolean shieldAlreadyPatterned() {
        for (int slot = 0; slot < craftingSlotCount; slot++) {
            if (craftingType[slot] != SHIELD) continue;
            return ItemComponentCodec.decode(SHIELD, craftingItemComponentData[slot])
                    .bannerPatterns().size() > 1;
        }
        return false;
    }

    /**
     * 산출이 입력 한 칸의 복사본인 특수 레시피(가죽 염색 {@code ArmorDyeRecipe}, 방패 장식
     * {@code ShieldDecorationRecipe} — 둘 다 입력 스택을 복사한다)는 그 칸의 내구도·인챈트를 물려받는다.
     * 해당하지 않으면 -1.
     */
    private int craftingCopySourceSlot(CraftRecipe recipe) {
        if (recipe.outputType() == Blocks.FLESH_HOOKED_SPEAR) {
            for (int slot = 0; slot < craftingSlotCount; slot++)
                if (craftingType[slot] == Blocks.FLESH_BONE_SPEAR) return slot;
            return -1;
        }
        boolean dye = "dye_leather".equals(recipe.id());
        boolean shield = CraftRecipe.SHIELD_DECORATION.equals(recipe.id());
        if (!dye && !shield) return -1;
        for (int slot = 0; slot < craftingSlotCount; slot++) {
            short type = craftingType[slot];
            if (dye && ItemComponentCodec.isDyeableLeather(type) || shield && type == SHIELD) return slot;
        }
        return -1;
    }

    private int craftingOutputDurability(CraftRecipe recipe) {
        int source = craftingCopySourceSlot(recipe);
        return source < 0 ? initialDurability(recipe.outputType()) : craftingDurability[source];
    }

    private long craftingOutputEnchantments(CraftRecipe recipe) {
        int source = craftingCopySourceSlot(recipe);
        return source < 0 ? EnchantmentRules.EMPTY_ENCHANTMENTS : craftingEnchantments[source];
    }

    private static final int[] DYE_RGB = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA,
            0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA,
            0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21,
    };

    /**
     * 바닐라 생존 컨테이너의 기본 클릭 규칙입니다. 일반 클릭은 커서와 대상 슬롯을 집기/놓기/
     * 합치기/교환하고, Shift 클릭은 격자↔인벤토리 또는 가방↔핫바로 빠르게 옮깁니다.
     * 결과 슬롯은 실제 배치가 맞을 때만 가져가며 그 순간 각 재료 칸에서 한 개씩 소모합니다.
     */
    public synchronized CraftClickResult clickCrafting(
            CraftArea area, int slot, CraftButton button, boolean shift) {
        if (settlementMutationBlocked()) return new CraftClickResult(false, false);
        if (!craftingOpen() || area == null || button == null) {
            return new CraftClickResult(false, false);
        }
        preflightRevisionCapacity();
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        boolean changed;
        boolean crafted = false;
        if (area == CraftArea.RESULT) {
            changed = shift ? shiftCraftResult() : takeCraftResult();
            crafted = changed;
        } else if (area == CraftArea.INVENTORY) {
            if (slot < 0 || slot >= SLOTS) return new CraftClickResult(false, false);
            changed = shift
                    ? quickMoveSurvivalInventory(slot)
                    : clickStack(itemType, count, durability, enchantments, mapIds, shulkerIds,
                            bucketMobData, itemComponentData, slot, button);
        } else if (area == CraftArea.ARMOR) {
            if (slot < 0 || slot >= ArmorSlot.values().length) {
                return new CraftClickResult(false, false);
            }
            changed = clickArmorSlot(ArmorSlot.values()[slot], shift);
        } else if (area == CraftArea.OFFHAND) {
            if (slot != 0) return new CraftClickResult(false, false);
            changed = clickOffhandSlot(shift);
        } else {
            if (!validCraftingSlot(slot)) return new CraftClickResult(false, false);
            changed = shift
                    ? quickMoveGrid(slot)
                    : clickStack(craftingType, craftingCount, craftingDurability,
                            craftingEnchantments, craftingMapIds, craftingShulkerIds,
                            craftingBucketMobData, craftingItemComponentData, slot, button);
        }
        if (changed) {
            if (currentNormalizedInventory() == null) {
                before.restore(this);
                return new CraftClickResult(false, false);
            }
            advancePersistenceRevision();
            requireLosslessTransientFold();
        }
        return new CraftClickResult(changed, crafted);
    }

    // ── [BEACON] 신호기 결제 칸 ─────────────────────────────────────────────────
    // BeaconMenu.PaymentSlot: mayPlace = #minecraft:beacon_payment_items, getMaxStackSize = 1.
    // 신호기 세션은 한 칸짜리 제작 격자(슬롯 0)를 결제 칸으로 쓴다. 판정은 WorldTickLoop 가 세션
    // 스테이션으로 고르고, 이 메서드들은 칸 규칙(한 개 · 결제 아이템만)만 소유한다.

    /** 결제 칸이 차 있는가({@code BeaconMenu.hasPayment}). */
    public synchronized boolean beaconPaymentPresent() {
        return craftingOpen() && craftingSlotCount >= 1 && craftingType[0] != EMPTY;
    }

    /**
     * 결제 칸 클릭({@code AbstractContainerMenu.doClick} PICKUP 을 최대 1 칸에 옮긴 것). 빈 칸에는 결제
     * 아이템만 한 개 들어가고, 찬 칸은 커서가 비었으면 집히며, 다른 결제 아이템 한 개면 맞바꾼다.
     * shift 는 칸의 아이템을 인벤토리로 옮긴다({@code quickMoveStack} index 0 → 1..37 역순).
     */
    public synchronized boolean clickBeaconPayment(CraftButton button, boolean shift) {
        if (settlementMutationBlocked() || !craftingOpen() || craftingSlotCount < 1 || button == null) {
            return false;
        }
        preflightRevisionCapacity();
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        boolean changed = shift ? quickMoveGrid(0) : clickBeaconPaymentSlot();
        return finishBeaconMutation(before, changed);
    }

    /**
     * 인벤토리 칸 shift 클릭. 결제 칸이 비었고 그 칸이 결제 아이템 <b>한 개짜리</b> 스택이면 결제 칸으로
     * 옮기고({@code quickMoveStack} 의 {@code stack.getCount() == 1} 조건), 아니면 가방↔핫바 이동이다.
     */
    public synchronized boolean quickMoveToBeaconPayment(int slot) {
        if (settlementMutationBlocked() || !craftingOpen() || craftingSlotCount < 1
                || slot < 0 || slot >= SLOTS || itemType[slot] == EMPTY) {
            return false;
        }
        preflightRevisionCapacity();
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        boolean changed;
        if (craftingType[0] == EMPTY && com.gameexpert.engine.BeaconRules.isPaymentItem(itemType[slot])
                && count[slot] == 1) {
            craftingType[0] = itemType[slot];
            craftingCount[0] = 1;
            craftingDurability[0] = durability[slot];
            craftingEnchantments[0] = enchantments[slot];
            craftingMapIds[0] = mapIds[slot];
            craftingShulkerIds[0] = shulkerIds[slot];
            craftingBucketMobData[0] = bucketMobData[slot];
            craftingItemComponentData[0] = itemComponentData[slot];
            clearComponentSlot(itemType, count, durability, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData, slot);
            changed = true;
        } else {
            changed = quickMoveInventory(slot);
        }
        return finishBeaconMutation(before, changed);
    }

    /**
     * [ARMOR-TRIM] 대장장이 화면의 인벤토리 칸 shift 클릭({@code ItemCombinerMenu.quickMoveStack}):
     * {@code SmithingMenu.canMoveIntoInputSlots} — 형판/기본 장비/추가 재료 중 받아 줄 빈 입력 칸이 있으면 —
     * 참이면 입력 칸 0..2 로 {@code moveItemStackTo}(같은 스택 합치기 → 첫 빈 칸 하나)하고, 한 개도 못
     * 옮기면 아무것도 바꾸지 않는다. 거짓이면 가방↔핫바 이동이다.
     */
    public synchronized boolean quickMoveToSmithing(int slot) {
        if (settlementMutationBlocked() || !craftingOpen() || craftingSlotCount < 3
                || slot < 0 || slot >= SLOTS || itemType[slot] == EMPTY) {
            return false;
        }
        preflightRevisionCapacity();
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        short type = itemType[slot];
        boolean[] accepts = {
            SmithingTrimRules.isSmithingTemplateItem(type),
            SmithingTrimRules.isSmithingBaseItem(type),
            SmithingTrimRules.isSmithingAdditionItem(type),
        };
        boolean canMove = false;
        for (int input = 0; input < 3; input++) {
            if (accepts[input] && craftingType[input] == EMPTY) canMove = true;
        }
        boolean changed;
        if (!canMove) {
            changed = quickMoveInventory(slot);
        } else {
            int maximum = stackMax(type);
            int moved = 0;
            for (int input = 0; input < 3 && count[slot] > 0; input++) {
                if (!accepts[input] || craftingType[input] == EMPTY
                        || !sameStack(type, durability[slot], enchantments[slot], mapIds[slot], shulkerIds[slot],
                                craftingType[input], craftingDurability[input], craftingEnchantments[input],
                                craftingMapIds[input], craftingShulkerIds[input])
                        || !java.util.Objects.equals(bucketMobData[slot], craftingBucketMobData[input])
                        || !java.util.Objects.equals(itemComponentData[slot], craftingItemComponentData[input])
                        || craftingCount[input] >= maximum) continue;
                int amount = Math.min(count[slot], maximum - craftingCount[input]);
                craftingCount[input] += amount;
                count[slot] -= amount;
                moved += amount;
            }
            for (int input = 0; input < 3 && count[slot] > 0; input++) {
                if (!accepts[input] || craftingType[input] != EMPTY) continue;
                int amount = Math.min(count[slot], maximum);
                craftingType[input] = type;
                craftingCount[input] = amount;
                craftingDurability[input] = durability[slot];
                craftingEnchantments[input] = enchantments[slot];
                craftingMapIds[input] = mapIds[slot];
                craftingShulkerIds[input] = shulkerIds[slot];
                craftingBucketMobData[input] = bucketMobData[slot];
                craftingItemComponentData[input] = itemComponentData[slot];
                count[slot] -= amount;
                moved += amount;
                break;
            }
            if (count[slot] == 0) {
                clearComponentSlot(itemType, count, durability, enchantments, mapIds, shulkerIds,
                        bucketMobData, itemComponentData, slot);
            }
            changed = moved > 0;
        }
        return finishBeaconMutation(before, changed);
    }

    /** {@code BeaconMenu.updateEffects} 의 {@code paymentSlot.remove(1)}. 결제 칸이 비었으면 false. */
    public synchronized boolean consumeBeaconPayment() {
        if (settlementMutationBlocked() || !beaconPaymentPresent()) return false;
        preflightRevisionCapacity();
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        if (--craftingCount[0] <= 0) clearCraftingSlot(0);
        return finishBeaconMutation(before, true);
    }

    /**
     * 신호기 세션 닫기({@code BeaconMenu.removed}). 커서는 보통 닫기처럼 인벤토리로 접고, 결제 칸의
     * 아이템은 인벤토리로 접지 않고 돌려준다 — 호출자가 {@code player.drop(stack, false)} 처럼 플레이어
     * 앞으로 던진다.
     */
    public synchronized List<DroppedStack> closeBeaconCrafting() {
        if (settlementMutationBlocked() || !craftingOpen()) return List.of();
        List<DroppedStack> dropped = new ArrayList<>(1);
        for (int slot = 0; slot < craftingSlotCount; slot++) {
            if (craftingType[slot] == EMPTY) continue;
            dropped.add(new DroppedStack(craftingType[slot], craftingCount[slot],
                    craftingDurability[slot], craftingEnchantments[slot], craftingMapIds[slot],
                    craftingShulkerIds[slot], craftingBucketMobData[slot],
                    craftingItemComponentData[slot]));
            clearCraftingSlot(slot);
        }
        List<DroppedStack> overflow = closeCrafting();
        if (!overflow.isEmpty()) {
            List<DroppedStack> all = new ArrayList<>(overflow);
            all.addAll(dropped);
            return List.copyOf(all);
        }
        return List.copyOf(dropped);
    }

    private boolean clickBeaconPaymentSlot() {
        short target = craftingType[0];
        if (target == EMPTY) {
            if (cursorType == EMPTY || !com.gameexpert.engine.BeaconRules.isPaymentItem(cursorType)) {
                return false;
            }
            craftingType[0] = cursorType;
            craftingCount[0] = 1;
            craftingDurability[0] = cursorDurability;
            craftingEnchantments[0] = cursorEnchantments;
            craftingMapIds[0] = cursorMapId;
            craftingShulkerIds[0] = cursorShulkerId;
            craftingBucketMobData[0] = cursorBucketMobData;
            craftingItemComponentData[0] = cursorItemComponentData;
            if (--cursorCount == 0) clearCursor();
            return true;
        }
        if (cursorType == EMPTY) {
            setCursor(target, craftingCount[0], craftingDurability[0], craftingEnchantments[0],
                    craftingMapIds[0], craftingShulkerIds[0], craftingBucketMobData[0],
                    craftingItemComponentData[0]);
            clearCraftingSlot(0);
            return true;
        }
        // 같은 아이템이면 칸이 이미 최대(1)라 옮길 것이 없다. 다른 결제 아이템 한 개면 맞바꾼다.
        if (!com.gameexpert.engine.BeaconRules.isPaymentItem(cursorType) || cursorCount > 1
                || sameStack(cursorType, cursorDurability, cursorEnchantments, cursorMapId,
                        cursorShulkerId, target, craftingDurability[0], craftingEnchantments[0],
                        craftingMapIds[0], craftingShulkerIds[0])
                        && java.util.Objects.equals(cursorItemComponentData, craftingItemComponentData[0])) {
            return false;
        }
        StackSnapshot slotStack = new StackSnapshot(target, craftingCount[0], craftingDurability[0],
                craftingEnchantments[0], craftingMapIds[0], craftingShulkerIds[0],
                craftingBucketMobData[0], craftingItemComponentData[0]);
        craftingType[0] = cursorType;
        craftingCount[0] = cursorCount;
        craftingDurability[0] = cursorDurability;
        craftingEnchantments[0] = cursorEnchantments;
        craftingMapIds[0] = cursorMapId;
        craftingShulkerIds[0] = cursorShulkerId;
        craftingBucketMobData[0] = cursorBucketMobData;
        craftingItemComponentData[0] = cursorItemComponentData;
        setCursor(slotStack.itemType(), slotStack.count(), slotStack.durability(),
                slotStack.enchantments(), slotStack.mapId(), slotStack.shulkerId(),
                slotStack.bucketMobData(), slotStack.itemComponentData());
        return true;
    }

    private boolean finishBeaconMutation(TransientMutationSnapshot before, boolean changed) {
        if (!changed) return false;
        if (currentNormalizedInventory() == null) {
            before.restore(this);
            return false;
        }
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return true;
    }

    /** 제작 클릭의 다중 배열 변경을 접기 검증 전까지 되돌릴 수 있게 잡는 방어적 복사입니다. */
    private static final class TransientMutationSnapshot {
        private final short[] itemTypes;
        private final int[] counts;
        private final int[] durabilities;
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;
        private final short[] craftingTypes;
        private final int[] craftingCounts;
        private final int[] craftingDurabilities;
        private final long[] craftingEnchantments;
        private final int[] craftingMapIds;
        private final int[] craftingShulkerIds;
        private final String[] craftingBucketMobData;
        private final String[] craftingItemComponentData;
        private final short cursorType;
        private final int cursorCount;
        private final int cursorDurability;
        private final long cursorEnchantments;
        private final int cursorMapId;
        private final int cursorShulkerId;
        private final String cursorBucketMobData;
        private final String cursorItemComponentData;
        private final short[] equippedTypes;
        private final int[] equippedDurabilities;
        private final long[] equippedEnchantments;
        private final String[] equippedItemComponentData;
        private final StackSnapshot offhand;

        private final StackSnapshot[] merchantPayments;

        private TransientMutationSnapshot(PlayerInventory inventory) {
            merchantPayments = inventory.merchantPaymentSnapshot();
            itemTypes = inventory.itemType.clone();
            counts = inventory.count.clone();
            durabilities = inventory.durability.clone();
            enchantments = inventory.enchantments.clone();
            mapIds = inventory.mapIds.clone();
            shulkerIds = inventory.shulkerIds.clone();
            bucketMobData = inventory.bucketMobData.clone();
            itemComponentData = inventory.itemComponentData.clone();
            craftingTypes = inventory.craftingType.clone();
            craftingCounts = inventory.craftingCount.clone();
            craftingDurabilities = inventory.craftingDurability.clone();
            craftingEnchantments = inventory.craftingEnchantments.clone();
            craftingMapIds = inventory.craftingMapIds.clone();
            craftingShulkerIds = inventory.craftingShulkerIds.clone();
            craftingBucketMobData = inventory.craftingBucketMobData.clone();
            craftingItemComponentData = inventory.craftingItemComponentData.clone();
            cursorType = inventory.cursorType;
            cursorCount = inventory.cursorCount;
            cursorDurability = inventory.cursorDurability;
            cursorEnchantments = inventory.cursorEnchantments;
            cursorMapId = inventory.cursorMapId;
            cursorShulkerId = inventory.cursorShulkerId;
            cursorBucketMobData = inventory.cursorBucketMobData;
            cursorItemComponentData = inventory.cursorItemComponentData;
            equippedTypes = inventory.equippedType.clone();
            equippedDurabilities = inventory.equippedDurability.clone();
            equippedEnchantments = inventory.equippedEnchantments.clone();
            equippedItemComponentData = inventory.equippedItemComponentData.clone();
            offhand = inventory.offhand;
        }

        private void restore(PlayerInventory inventory) {
            inventory.restoreMerchantPayments(merchantPayments);
            System.arraycopy(itemTypes, 0, inventory.itemType, 0, SLOTS);
            System.arraycopy(counts, 0, inventory.count, 0, SLOTS);
            System.arraycopy(durabilities, 0, inventory.durability, 0, SLOTS);
            System.arraycopy(enchantments, 0, inventory.enchantments, 0, SLOTS);
            System.arraycopy(mapIds, 0, inventory.mapIds, 0, SLOTS);
            System.arraycopy(shulkerIds, 0, inventory.shulkerIds, 0, SLOTS);
            System.arraycopy(bucketMobData, 0, inventory.bucketMobData, 0, SLOTS);
            System.arraycopy(itemComponentData, 0, inventory.itemComponentData, 0, SLOTS);
            System.arraycopy(craftingTypes, 0, inventory.craftingType, 0,
                    inventory.craftingType.length);
            System.arraycopy(craftingCounts, 0, inventory.craftingCount, 0,
                    inventory.craftingCount.length);
            System.arraycopy(craftingDurabilities, 0, inventory.craftingDurability, 0,
                    inventory.craftingDurability.length);
            System.arraycopy(craftingEnchantments, 0, inventory.craftingEnchantments, 0,
                    inventory.craftingEnchantments.length);
            System.arraycopy(craftingMapIds, 0, inventory.craftingMapIds, 0,
                    inventory.craftingMapIds.length);
            System.arraycopy(craftingShulkerIds, 0, inventory.craftingShulkerIds, 0,
                    inventory.craftingShulkerIds.length);
            System.arraycopy(craftingBucketMobData, 0, inventory.craftingBucketMobData, 0,
                    inventory.craftingBucketMobData.length);
            System.arraycopy(craftingItemComponentData, 0, inventory.craftingItemComponentData, 0,
                    inventory.craftingItemComponentData.length);
            inventory.cursorType = cursorType;
            inventory.cursorCount = cursorCount;
            inventory.cursorDurability = cursorDurability;
            inventory.cursorEnchantments = cursorEnchantments;
            inventory.cursorMapId = cursorMapId;
            inventory.cursorShulkerId = cursorShulkerId;
            inventory.cursorBucketMobData = cursorBucketMobData;
            inventory.cursorItemComponentData = cursorItemComponentData;
            System.arraycopy(equippedTypes, 0, inventory.equippedType, 0,
                    equippedTypes.length);
            System.arraycopy(equippedDurabilities, 0, inventory.equippedDurability, 0,
                    equippedDurabilities.length);
            System.arraycopy(equippedEnchantments, 0, inventory.equippedEnchantments, 0,
                    equippedEnchantments.length);
            System.arraycopy(equippedItemComponentData, 0, inventory.equippedItemComponentData, 0,
                    equippedItemComponentData.length);
            inventory.offhand = offhand;
        }
    }

    private boolean quickMoveSurvivalInventory(int slot) {
        if (craftingGridSize == 2 && craftingSlotCount == 4) {
            ArmorSlot armor = armorSlot(itemType[slot]);
            if (armor != null && equippedType[armor.ordinal()] == EMPTY) {
                StackSnapshot source = stackSnapshotAt(slot);
                writeEquipped(armor, new StackSnapshot(source.itemType(), 1, source.durability(),
                        source.enchantments(), 0, 0, null, source.itemComponentData()));
                if (--count[slot] == 0) clearComponentSlot(itemType, count, durability,
                        enchantments, mapIds, shulkerIds, bucketMobData, itemComponentData, slot);
                return true;
            }
        }
        return quickMoveInventory(slot);
    }

    private boolean clickArmorSlot(ArmorSlot armor, boolean shift) {
        // [CURSE] 결속의 저주가 걸려 있으면 shift 도, 커서 집기/교환도 모두 거절한다.
        if (bindingCurseLocks(armor)) return false;
        int index = armor.ordinal();
        StackSnapshot equipped = equippedStack(armor);
        if (shift) {
            if (equipped.isEmpty()) return false;
            InventoryArrays next = inventoryWithInserted(equipped);
            if (next == null) return false;
            commitInventoryArrays(next);
            writeEquipped(armor, StackSnapshot.EMPTY);
            return true;
        }
        if (cursorType == EMPTY) {
            if (equipped.isEmpty()) return false;
            setCursor(equipped.itemType(), 1, equipped.durability(), equipped.enchantments(),
                    0, 0, null, equipped.itemComponentData());
            writeEquipped(armor, StackSnapshot.EMPTY);
            return true;
        }
        if (armorSlot(cursorType) != armor) return false;
        if (equipped.isEmpty()) {
            writeEquipped(armor, new StackSnapshot(cursorType, 1, cursorDurability,
                    cursorEnchantments, 0, 0, null, cursorItemComponentData));
            if (--cursorCount == 0) clearCursor();
            return true;
        }
        if (cursorCount != 1) return false;
        writeEquipped(armor, new StackSnapshot(cursorType, 1, cursorDurability,
                cursorEnchantments, 0, 0, null, cursorItemComponentData));
        setCursor(equipped.itemType(), 1, equipped.durability(), equipped.enchantments(),
                0, 0, null, equipped.itemComponentData());
        return true;
    }

    private boolean clickOffhandSlot(boolean shift) {
        if (shift) {
            if (offhand.isEmpty()) return false;
            InventoryArrays next = inventoryWithInserted(offhand);
            if (next == null) return false;
            commitInventoryArrays(next);
            offhand = StackSnapshot.EMPTY;
            return true;
        }
        StackSnapshot target = offhand;
        if (cursorType == EMPTY) {
            if (target.isEmpty()) return false;
            setCursor(target.itemType(), target.count(), target.durability(), target.enchantments(),
                    target.mapId(), target.shulkerId(), target.bucketMobData(),
                    target.itemComponentData());
            offhand = StackSnapshot.EMPTY;
            return true;
        }
        StackSnapshot cursor = new StackSnapshot(cursorType, cursorCount, cursorDurability,
                cursorEnchantments, cursorMapId, cursorShulkerId, cursorBucketMobData,
                cursorItemComponentData);
        if (target.isEmpty()) {
            offhand = cursor;
            clearCursor();
            return true;
        }
        if (stack(cursor, target) && target.count() < stackMax(target.itemType())) {
            int moved = Math.min(cursor.count(), stackMax(target.itemType()) - target.count());
            offhand = new StackSnapshot(target.itemType(), target.count() + moved,
                    target.durability(), target.enchantments(), target.mapId(), target.shulkerId(),
                    target.bucketMobData(), target.itemComponentData());
            cursorCount -= moved;
            if (cursorCount == 0) clearCursor();
            return moved > 0;
        }
        offhand = cursor;
        setCursor(target.itemType(), target.count(), target.durability(), target.enchantments(),
                target.mapId(), target.shulkerId(), target.bucketMobData(),
                target.itemComponentData());
        return true;
    }

    private InventoryArrays inventoryWithInserted(StackSnapshot stack) {
        short[] types = itemType.clone();
        int[] counts = count.clone();
        int[] durabilities = durability.clone();
        long[] masks = enchantments.clone();
        int[] copiedMapIds = mapIds.clone();
        int[] copiedShulkerIds = shulkerIds.clone();
        String[] copiedBucket = bucketMobData.clone();
        String[] copiedComponents = itemComponentData.clone();
        if (!foldComponentStack(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, copiedBucket, copiedComponents, stack.itemType(), stack.count(),
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData())) return null;
        return new InventoryArrays(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, copiedBucket, copiedComponents);
    }

    /**
     * 레시피 북 선택을 인벤토리와 열린 격자 사이의 실제 아이템 재배치로 적용합니다.
     * 일반 선택은 한 번분, Shift 선택은 각 격자 스택 한도 안에서 가능한 최대분을 채웁니다.
     */
    public synchronized boolean placeCraftingRecipe(String recipeId, boolean maximum) {
        if (settlementMutationBlocked()) return false;
        if (!craftingOpen() || cursorType != EMPTY) return false;
        // [STONECUT] 절단 세션의 레시피 북 클릭은 격자 채우기가 아니라 산출 선택이다.
        if (craftingStonecutter) return selectStonecutterRecipe(recipeId);
        CraftRecipe recipe = CraftRecipe.byId(recipeId);
        if (recipe == null) return false;
        short[] expected = recipe.expectedCells(craftingGridSize, false);
        if (expected == null) return false;

        short[] poolTypes = new short[SLOTS + 9];
        int[] poolCounts = new int[SLOTS + 9];
        StackSnapshot[] poolStacks = new StackSnapshot[SLOTS + 9];
        int poolSize = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            poolSize = addRecipePoolEntry(
                    poolTypes, poolCounts, poolStacks, poolSize, stackSnapshotAt(slot));
        }
        int cells = craftingSlotCount;
        for (int slot = 0; slot < cells; slot++) {
            poolSize = addRecipePoolEntry(
                    poolTypes, poolCounts, poolStacks, poolSize, craftingStack(slot));
        }

        RecipePlacementPlan plan;
        if (maximum) {
            int low = 1;
            int high = STACK_MAX;
            plan = null;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                RecipePlacementPlan candidate = recipePlacement(
                        recipe, expected, poolTypes, poolCounts, poolStacks, poolSize, middle);
                if (candidate == null) {
                    high = middle - 1;
                } else {
                    plan = candidate;
                    low = middle + 1;
                }
            }
        } else {
            plan = recipePlacement(recipe, expected, poolTypes, poolCounts, poolStacks,
                    poolSize, 1);
        }
        if (plan == null) return false;
        preflightRevisionCapacity();

        short[] nextType = itemType.clone();
        int[] nextCount = count.clone();
        int[] nextDurability = durability.clone();
        long[] nextEnchantments = enchantments.clone();
        int[] nextMapIds = mapIds.clone();
        int[] nextShulkerIds = shulkerIds.clone();
        String[] nextBucket = bucketMobData.clone();
        String[] nextComponents = itemComponentData.clone();
        short[] nextCraftingType = craftingType.clone();
        int[] nextCraftingCount = craftingCount.clone();
        int[] nextCraftingDurability = craftingDurability.clone();
        long[] nextCraftingEnchantments = craftingEnchantments.clone();
        int[] nextCraftingMapIds = craftingMapIds.clone();
        int[] nextCraftingShulkerIds = craftingShulkerIds.clone();
        String[] nextCraftingBucket = craftingBucketMobData.clone();
        String[] nextCraftingComponents = craftingItemComponentData.clone();
        for (int slot = 0; slot < plan.stacks.length; slot++) {
            StackSnapshot supplied = plan.stacks[slot];
            if (supplied == null || supplied.isEmpty()) continue;
            int remaining = plan.count;
            remaining -= consumeExact(nextCraftingType, nextCraftingCount,
                    nextCraftingDurability, nextCraftingEnchantments, nextCraftingMapIds,
                    nextCraftingShulkerIds, nextCraftingBucket, nextCraftingComponents,
                    cells, supplied, remaining);
            remaining -= consumeExact(nextType, nextCount, nextDurability, nextEnchantments,
                    nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    SLOTS, supplied, remaining);
            if (remaining != 0) return false;
        }
        for (int slot = 0; slot < cells; slot++) {
            if (nextCraftingType[slot] == EMPTY) continue;
            int inserted = addToArrays(nextType, nextCount, nextDurability, nextEnchantments,
                    nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    nextCraftingType[slot], nextCraftingCount[slot],
                    nextCraftingDurability[slot], nextCraftingEnchantments[slot],
                    nextCraftingMapIds[slot], nextCraftingShulkerIds[slot],
                    nextCraftingBucket[slot], nextCraftingComponents[slot], 0, SLOTS);
            if (inserted != nextCraftingCount[slot]) return false;
        }

        System.arraycopy(nextType, 0, itemType, 0, SLOTS);
        System.arraycopy(nextCount, 0, count, 0, SLOTS);
        System.arraycopy(nextDurability, 0, durability, 0, SLOTS);
        System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
        for (int slot = 0; slot < craftingType.length; slot++) clearCraftingSlot(slot);
        for (int slot = 0; slot < plan.stacks.length; slot++) {
            StackSnapshot supplied = plan.stacks[slot];
            if (supplied == null || supplied.isEmpty()) continue;
            craftingType[slot] = supplied.itemType();
            craftingCount[slot] = plan.count;
            craftingDurability[slot] = supplied.durability();
            craftingEnchantments[slot] = supplied.enchantments();
            craftingMapIds[slot] = supplied.mapId();
            craftingShulkerIds[slot] = supplied.shulkerId();
            craftingBucketMobData[slot] = supplied.bucketMobData();
            craftingItemComponentData[slot] = supplied.itemComponentData();
        }
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return true;
    }

    /**
     * 화로 메뉴의 일반 좌/우클릭과 Shift 빠른 이동을 같은 플레이어 커서로 적용합니다.
     * 출력 칸에는 넣을 수 없고 입력·연료 칸은 각각의 화로 규칙을 만족하는 아이템만 받습니다.
     */
    public synchronized boolean clickFurnace(
            FurnaceInventory furnace, FurnaceArea area, int slot,
            CraftButton button, boolean shift) {
        if (settlementMutationBlocked()) return false;
        if (furnace == null || area == null || button == null || craftingOpen()) return false;
        preflightRevisionCapacity();
        if (area == FurnaceArea.INVENTORY) {
            if (slot < 0 || slot >= SLOTS) return false;
            if (shift) {
                if (cursorType != EMPTY || itemType[slot] == EMPTY) return false;
                StackSnapshot source = stackSnapshotAt(slot);
                short type = source.itemType();
                int destination = furnace.destinationSlot(type);
                FurnaceInventory.StagedCommand staged = furnace.beginLogicalCommand();
                int moved = staged.add(destination, source,
                        Math.min(source.count(), staged.roomFor(destination, type)));
                if (moved == 0) return false;
                CompletePersistenceSnapshot before = completePersistenceSnapshot();
                count[slot] -= moved;
                if (count[slot] == 0) {
                    clearComponentSlot(itemType, count, durability, enchantments, mapIds,
                            shulkerIds, bucketMobData, itemComponentData, slot);
                }
                advancePersistenceRevision();
                return commitFurnaceAtomically(staged, before);
            }
            boolean changed = clickStack(
                    itemType, count, durability, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData, slot, button);
            if (changed) advancePersistenceRevision();
            return changed;
        }
        if (slot < 0 || slot >= FurnaceInventory.SLOTS) return false;
        FurnaceInventory.StagedCommand staged = furnace.beginLogicalCommand();
        short targetType = staged.itemType(slot);
        int targetCount = staged.itemCount(slot);
        StackSnapshot target = staged.stack(slot);
        if (shift) {
            if (cursorType != EMPTY || targetType == EMPTY) return false;
            CompletePersistenceSnapshot before = completePersistenceSnapshot();
            int inserted = addToArrays(itemType, count, durability, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData,
                    targetType, targetCount, target.durability(), target.enchantments(),
                    target.mapId(), target.shulkerId(), target.bucketMobData(), target.itemComponentData(), 0, SLOTS);
            if (inserted == 0) return false;
            if (staged.take(slot, inserted) != inserted) {
                installCompleteSnapshot(before);
                return false;
            }
            advancePersistenceRevision();
            return commitFurnaceAtomically(staged, before);
        }
        if (cursorType == EMPTY) {
            if (targetType == EMPTY) return false;
            int picked = button == CraftButton.RIGHT
                    ? (targetCount + 1) / 2 : targetCount;
            if (!containerCursorFits(target.withCount(picked))) return false;
            if (staged.take(slot, picked) != picked) return false;
            CompletePersistenceSnapshot before = completePersistenceSnapshot();
            setCursor(target.withCount(picked));
            advancePersistenceRevision();
            return commitFurnaceAtomically(staged, before);
        }
        StackSnapshot held = cursorStackSnapshot();
        if (slot == FurnaceInventory.OUTPUT_SLOT) {
            if (!held.sameIdentity(target)) return false;
            int moved = Math.min(
                    button == CraftButton.RIGHT ? 1 : targetCount,
                    stackMax(cursorType) - cursorCount);
            if (moved <= 0
                    || !containerCursorFits(held.withCount(cursorCount + moved))) {
                return false;
            }
            if (staged.take(slot, moved) != moved) return false;
            CompletePersistenceSnapshot before = completePersistenceSnapshot();
            cursorCount += moved;
            advancePersistenceRevision();
            return commitFurnaceAtomically(staged, before);
        }
        if (!validFurnaceSlotItem(furnace, slot, cursorType)) return false;
        if (targetType == EMPTY || target.sameIdentity(held)) {
            int requested = button == CraftButton.RIGHT ? 1 : cursorCount;
            int moved = staged.add(slot, held,
                    Math.min(requested, staged.roomFor(slot, cursorType)));
            if (moved == 0) return false;
            CompletePersistenceSnapshot before = completePersistenceSnapshot();
            cursorCount -= moved;
            if (cursorCount == 0) clearCursor();
            advancePersistenceRevision();
            return commitFurnaceAtomically(staged, before);
        }
        int heldCount = cursorCount;
        if (!containerCursorFits(target)) return false;
        if (!staged.replace(slot, held) || staged.itemCount(slot) != heldCount) return false;
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        setCursor(target);
        advancePersistenceRevision();
        return commitFurnaceAtomically(staged, before);
    }

    private boolean commitFurnaceAtomically(FurnaceInventory.StagedCommand staged,
            CompletePersistenceSnapshot before) {
        try {
            if (staged.commit()) return true;
        } catch (RuntimeException | Error failure) {
            installCompleteSnapshot(before);
            throw failure;
        }
        installCompleteSnapshot(before);
        return false;
    }

    public synchronized boolean dragFurnace(FurnaceInventory furnace, FurnaceArea[] areas,
            int[] slots, CraftButton button) {
        if (settlementMutationBlocked() || furnace == null || areas == null || slots == null
                || areas.length != slots.length || areas.length == 0 || button == null || cursorType == EMPTY
                || craftingOpen()) return false;
        preflightRevisionCapacity();
        FurnaceInventory.StagedCommand staged = furnace.beginLogicalCommand();
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        int perSlot = button == CraftButton.RIGHT ? 1
                : Math.max(1, cursorCount / Math.min(cursorCount, areas.length));
        boolean[] seenInventory = new boolean[SLOTS];
        boolean[] seenFurnace = new boolean[FurnaceInventory.SLOTS];
        boolean changed = false;
        boolean furnaceChanged = false;
        for (int index = 0; index < areas.length && cursorType != EMPTY; index++) {
            int slot = slots[index];
            int moved = 0;
            if (areas[index] == FurnaceArea.INVENTORY && slot >= 0 && slot < SLOTS
                    && !seenInventory[slot] && canReceive(itemType, count, durability,
                            enchantments, mapIds, shulkerIds, bucketMobData,
                            itemComponentData, slot)) {
                seenInventory[slot] = true;
                moved = Math.min(Math.min(perSlot, stackMax(cursorType) - count[slot]), cursorCount);
                if (moved > 0) {
                    if (itemType[slot] == EMPTY) {
                        itemType[slot] = cursorType; durability[slot] = cursorDurability;
                        enchantments[slot] = cursorEnchantments;
                        mapIds[slot] = cursorMapId;
                        shulkerIds[slot] = cursorShulkerId;
                        bucketMobData[slot] = cursorBucketMobData;
                        itemComponentData[slot] = cursorItemComponentData;
                    }
                    count[slot] += moved;
                }
            } else if (areas[index] == FurnaceArea.FURNACE && slot >= 0
                    && slot < FurnaceInventory.OUTPUT_SLOT && !seenFurnace[slot]
                    && validFurnaceSlotItem(furnace, slot, cursorType)) {
                seenFurnace[slot] = true;
                moved = staged.add(slot, cursorStackSnapshot(),
                        Math.min(perSlot, cursorCount));
                furnaceChanged |= moved > 0;
            }
            if (moved > 0) {
                cursorCount -= moved;
                if (cursorCount == 0) clearCursor();
                changed = true;
            }
        }
        if (!changed) return false;
        advancePersistenceRevision();
        return !furnaceChanged || commitFurnaceAtomically(staged, before);
    }

    public synchronized boolean collectFurnace(FurnaceInventory furnace) {
        if (settlementMutationBlocked() || furnace == null || craftingOpen()
                || cursorType == EMPTY || cursorCount >= stackMax(cursorType)) return false;
        preflightRevisionCapacity();
        FurnaceInventory.StagedCommand staged = furnace.beginLogicalCommand();
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        boolean changed = false;
        boolean furnaceChanged = false;
        {
            for (int slot = 0; slot < FurnaceInventory.SLOTS
                    && cursorCount < stackMax(cursorType); slot++) {
                if (!cursorStackSnapshot().sameIdentity(staged.stack(slot))) continue;
                int moved = staged.take(slot, Math.min(stackMax(cursorType) - cursorCount,
                        staged.itemCount(slot)));
                cursorCount += moved;
                changed |= moved > 0;
                furnaceChanged |= moved > 0;
            }
        }
        changed |= collectVisibleInventoryToCursor();
        if (!changed) return false;
        advancePersistenceRevision();
        return !furnaceChanged || commitFurnaceAtomically(staged, before);
    }

    /** 인챈트 메뉴는 전체 스택을 계획하고 표 commit 실패 시 플레이어 변경도 되돌립니다. */
    public synchronized boolean clickEnchanting(EnchantingInventory table, EnchantArea area,
            int slot, CraftButton button, boolean shift) {
        if (settlementMutationBlocked() || table == null || area == null || button == null
                || craftingOpen()) return false;
        if (slot < 0 || slot >= (area == EnchantArea.INVENTORY ? SLOTS : EnchantingInventory.SLOTS)) {
            return false;
        }
        if (area == EnchantArea.INVENTORY && !shift) return clickContainerInventory(slot, button);
        if (shift && cursorType != EMPTY) return false;
        preflightRevisionCapacity();
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        EnchantingInventory.StagedResult staged;
        if (area == EnchantArea.INVENTORY) {
            StackSnapshot source = stackSnapshotAt(slot);
            staged = table.stageQuickMoveInto(table.revision(), source, -1, source.count());
            if (!staged.accepted()) return false;
            count[slot] -= staged.moved();
            if (count[slot] == 0) clearComponentSlot(itemType, count, durability, enchantments,
                    mapIds, shulkerIds, bucketMobData, itemComponentData, slot);
        } else {
            StackSnapshot source = table.stack(slot);
            if (shift) {
                if (source.isEmpty()) return false;
                int inserted = addToArrays(itemType, count, durability, enchantments, mapIds,
                        shulkerIds, bucketMobData, itemComponentData, source.itemType(),
                        source.count(), source.durability(), source.enchantments(), source.mapId(),
                        source.shulkerId(), source.bucketMobData(), source.itemComponentData(), 0, SLOTS);
                if (inserted == 0) return false;
                staged = table.stageTake(table.revision(), slot, source, inserted);
            } else if (cursorType == EMPTY) {
                if (source.isEmpty()) return false;
                int picked = button == CraftButton.RIGHT ? (source.count() + 1) / 2 : source.count();
                if (!containerCursorFits(source.itemType(), picked, source.durability(),
                        source.enchantments(), source.mapId(), source.shulkerId(),
                        source.bucketMobData(), source.itemComponentData())) return false;
                staged = table.stageTake(table.revision(), slot, source, picked);
                if (!staged.accepted()) return false;
                setCursor(source.itemType(), picked, source.durability(), source.enchantments(),
                        source.mapId(), source.shulkerId(), source.bucketMobData(), source.itemComponentData());
            } else {
                StackSnapshot held = cursorStackSnapshot();
                if (!source.isEmpty() && !stack(source, held)) return false;
                int moved = Math.min(button == CraftButton.RIGHT ? 1 : cursorCount,
                        EnchantingInventory.slotCapacity(slot, cursorType) - source.count());
                if (moved <= 0) return false;
                staged = table.stageAdd(table.revision(), slot, held, source, moved);
                if (!staged.accepted()) return false;
                cursorCount -= moved;
                if (cursorCount == 0) clearCursor();
            }
        }
        advancePersistenceRevision();
        return commitEnchantingAtomically(staged, before);
    }

    private boolean commitEnchantingAtomically(EnchantingInventory.StagedResult staged,
            CompletePersistenceSnapshot before) {
        try {
            if (staged.commit().committed()) return true;
        } catch (RuntimeException | Error failure) {
            installCompleteSnapshot(before);
            throw failure;
        }
        installCompleteSnapshot(before);
        return false;
    }

    public synchronized boolean dragEnchanting(EnchantingInventory table, EnchantArea[] areas,
            int[] slots, CraftButton button) {
        if (settlementMutationBlocked() || table == null || areas == null || slots == null
                || areas.length != slots.length || areas.length == 0 || button == null || cursorType == EMPTY
                || craftingOpen()) return false;
        preflightRevisionCapacity();
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        List<EnchantingInventory.LogicalCommand> commands = new ArrayList<>();
        int perSlot = button == CraftButton.RIGHT ? 1
                : Math.max(1, cursorCount / Math.min(cursorCount, areas.length));
        boolean[] seenInventory = new boolean[SLOTS];
        boolean[] seenTable = new boolean[EnchantingInventory.SLOTS];
        boolean changed = false;
        for (int index = 0; index < areas.length && cursorType != EMPTY; index++) {
            int slot = slots[index];
            int moved = 0;
            if (areas[index] == EnchantArea.INVENTORY && slot >= 0 && slot < SLOTS
                    && !seenInventory[slot] && canReceive(itemType, count, durability,
                            enchantments, mapIds, shulkerIds, bucketMobData,
                            itemComponentData, slot)) {
                seenInventory[slot] = true;
                moved = Math.min(Math.min(perSlot, stackMax(cursorType) - count[slot]), cursorCount);
                if (moved > 0) {
                    if (itemType[slot] == EMPTY) {
                        itemType[slot] = cursorType; durability[slot] = cursorDurability;
                        enchantments[slot] = cursorEnchantments;
                        mapIds[slot] = cursorMapId; shulkerIds[slot] = cursorShulkerId;
                        bucketMobData[slot] = cursorBucketMobData;
                        itemComponentData[slot] = cursorItemComponentData;
                    }
                    count[slot] += moved;
                }
            } else if (areas[index] == EnchantArea.ENCHANT && slot >= 0
                    && slot < EnchantingInventory.SLOTS && !seenTable[slot]
                    && EnchantingInventory.accepts(slot, cursorStackSnapshot())
                    && (table.itemType(slot) == EMPTY || stack(table.stack(slot), cursorStackSnapshot()))) {
                seenTable[slot] = true;
                int room = EnchantingInventory.slotCapacity(slot, cursorType) - table.count(slot);
                moved = Math.min(Math.min(perSlot, room), cursorCount);
                if (moved > 0) commands.add(EnchantingInventory.LogicalCommand.add(
                        table.revision(), slot, cursorStackSnapshot(), table.stack(slot), moved));
            }
            if (moved > 0) {
                cursorCount -= moved;
                if (cursorCount == 0) clearCursor();
                changed = true;
            }
        }
        if (changed) advancePersistenceRevision();
        return changed && (commands.isEmpty() || commitEnchantingAtomically(
                table.stageBatch(table.revision(), commands), before));
    }

    public synchronized boolean collectEnchanting(EnchantingInventory table) {
        if (settlementMutationBlocked() || table == null || craftingOpen()
                || cursorType == EMPTY || cursorCount >= stackMax(cursorType)) return false;
        preflightRevisionCapacity();
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        List<EnchantingInventory.LogicalCommand> commands = new ArrayList<>();
        boolean changed = false;
        for (int slot = 0; slot < EnchantingInventory.SLOTS
                && cursorCount < stackMax(cursorType); slot++) {
            StackSnapshot source = table.stack(slot);
            if (source.isEmpty() || !stack(source, cursorStackSnapshot())) continue;
            int moved = Math.min(stackMax(cursorType) - cursorCount, source.count());
            commands.add(EnchantingInventory.LogicalCommand.take(table.revision(), slot, source, moved));
            cursorCount += moved;
            changed = true;
        }
        changed |= collectVisibleInventoryToCursor();
        if (changed) advancePersistenceRevision();
        return changed && (commands.isEmpty() || commitEnchantingAtomically(
                table.stageBatch(table.revision(), commands), before));
    }

    private boolean collectVisibleInventoryToCursor() {
        boolean changed = false;
        for (int slot = 0; slot < SLOTS && cursorCount < stackMax(cursorType); slot++) {
            if (!cursorMatches(itemType[slot], durability[slot], enchantments[slot], mapIds[slot],
                    shulkerIds[slot], bucketMobData[slot], itemComponentData[slot])) continue;
            int moved = Math.min(stackMax(cursorType) - cursorCount, count[slot]);
            cursorCount += moved;
            count[slot] -= moved;
            if (count[slot] == 0) clearComponentSlot(itemType, count, durability, enchantments,
                    mapIds, shulkerIds, bucketMobData, itemComponentData, slot);
            changed |= moved > 0;
        }
        return changed;
    }

    /** 화로 레시피 북 선택을 입력 칸과 인벤토리 사이의 실제 재료 이동으로 적용합니다. */
    public synchronized boolean placeFurnaceRecipe(
            FurnaceInventory furnace, String recipeId, boolean maximum) {
        if (settlementMutationBlocked()) return false;
        if (furnace == null || craftingOpen() || cursorType != EMPTY) return false;
        CraftRecipe recipe = CraftRecipe.byId(recipeId);
        if (recipe == null || !recipe.requiresFurnace()
                || recipe.inputs().size() != 1 || recipe.inputs().get(0).count() != 1) {
            return false;
        }
        short input = recipe.inputs().get(0).itemType();
        // [FURNACE-VARIANT] 레시피 북 배치도 변형 필터를 거친다. 용광로 UI 에서 고기 레시피를
        // 눌러 입력 칸을 채우는 우회로가 남으면 필터가 클릭 경로에만 있는 셈이 된다.
        if (furnace.outputFor(input) != recipe.outputType()) return false;
        preflightRevisionCapacity();
        FurnaceInventory.StagedCommand staged = furnace.beginLogicalCommand();

        short[] nextType = itemType.clone();
        int[] nextCount = count.clone();
        int[] nextDurability = durability.clone();
        long[] nextEnchantments = enchantments.clone();
        int[] nextMapIds = mapIds.clone();
        int[] nextShulkerIds = shulkerIds.clone();
        String[] nextBucket = bucketMobData.clone();
        String[] nextComponents = itemComponentData.clone();
        short currentType = staged.itemType(FurnaceInventory.INPUT_SLOT);
        int currentCount = staged.itemCount(FurnaceInventory.INPUT_SLOT);
        StackSnapshot current = staged.stack(FurnaceInventory.INPUT_SLOT);
        if (currentType != EMPTY && addToArrays(
                nextType, nextCount, nextDurability, nextEnchantments, nextMapIds, nextShulkerIds,
                nextBucket, nextComponents,
                currentType, currentCount, current.durability(), current.enchantments(),
                current.mapId(), current.shulkerId(), current.bucketMobData(), current.itemComponentData(), 0, SLOTS) != currentCount) {
            return false;
        }
        StackSnapshot selected = null;
        int available = 0;
        for (int inventorySlot = 0; inventorySlot < SLOTS; inventorySlot++) {
            if (nextType[inventorySlot] != input) continue;
            StackSnapshot candidate = new StackSnapshot(input, nextCount[inventorySlot],
                    nextDurability[inventorySlot], nextEnchantments[inventorySlot], nextMapIds[inventorySlot],
                    nextShulkerIds[inventorySlot], nextBucket[inventorySlot], nextComponents[inventorySlot]);
            int compatible = 0;
            for (int other = 0; other < SLOTS; other++) {
                if (nextType[other] != input) continue;
                StackSnapshot value = new StackSnapshot(input, nextCount[other], nextDurability[other],
                        nextEnchantments[other], nextMapIds[other], nextShulkerIds[other], nextBucket[other], nextComponents[other]);
                if (candidate.sameIdentity(value)) compatible += value.count();
            }
            if (selected == null || maximum && compatible > available) {
                selected = candidate;
                available = compatible;
            }
            if (!maximum) break;
        }
        int desired = maximum ? Math.min(stackMax(input), available) : Math.min(1, available);
        StackSnapshot plainInput = selected == null ? null : selected.withCount(desired);
        if (desired == 0
                || consumeExact(nextType, nextCount, nextDurability, nextEnchantments,
                        nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                        SLOTS, plainInput, desired) != desired) {
            return false;
        }
        if (!staged.replace(FurnaceInventory.INPUT_SLOT, plainInput)) return false;
        CompletePersistenceSnapshot before = completePersistenceSnapshot();
        System.arraycopy(nextType, 0, itemType, 0, SLOTS);
        System.arraycopy(nextCount, 0, count, 0, SLOTS);
        System.arraycopy(nextDurability, 0, durability, 0, SLOTS);
        System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
        advancePersistenceRevision();
        return commitFurnaceAtomically(staged, before);
    }

    /**
     * [SURV-X] 제작 격자가 없는 컨테이너(인챈트 테이블 등)에서 인벤토리 한 칸을 커서로 클릭합니다.
     * 규칙은 제작 컨테이너의 클릭과 완전히 같습니다.
     */
    public synchronized boolean clickContainerInventory(int slot, CraftButton button) {
        if (settlementMutationBlocked()) return false;
        if (craftingOpen() || button == null || slot < 0 || slot >= SLOTS) return false;
        preflightRevisionCapacity();
        boolean changed = clickStack(
                itemType, count, durability, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData, slot, button);
        if (changed) advancePersistenceRevision();
        return changed;
    }

    /**
     * [CONTAINER-CURSOR] 보관 컨테이너(상자·큰 상자·통·몹 화물)의 바닐라 클릭 한 번.
     *
     * <p>바닐라 1.21.4 {@code AbstractContainerMenu#doClick(PICKUP)} 규약을 그대로 따른다:
     * 좌클릭은 스택 집기/놓기(다른 스택이면 맞바꿈), 우클릭은 반쪽 집기/한 개 놓기,
     * Shift 클릭은 반대편으로의 전량 빠른 이동이다. 커서는 화로·인챈트와 같은 플레이어 커서라
     * 화면을 옮겨 다녀도 하나뿐이며, 집는 스택은 언제나 인벤토리로 되돌릴 수 있을 때만 든다.
     *
     * @return 실제로 상태가 바뀌었으면 true(거부는 false — 호출부가 스냅샷만 되돌려 보낸다).
     */
    public synchronized boolean clickContainer(ContainerAccess container, ContainerArea area, int slot,
            CraftButton button, boolean shift) {
        if (container == merchantPaymentAccess) {
            return mutateMerchantPayments(() -> clickContainerInternal(container, area, slot, button, shift));
        }
        return clickContainerInternal(container, area, slot, button, shift);
    }

    private boolean clickContainerInternal(ContainerAccess container, ContainerArea area, int slot,
            CraftButton button, boolean shift) {
        if (settlementMutationBlocked()) return false;
        if (container == null || area == null || button == null || craftingOpen()) return false;
        preflightRevisionCapacity();
        if (area == ContainerArea.INVENTORY) {
            if (slot < 0 || slot >= SLOTS) return false;
            if (shift) {
                // 기존 moveChestItem/moveMobCargoItem 과 같은 동작(한 슬롯 전량 → 반대편).
                if (cursorType != EMPTY || itemType[slot] == EMPTY) return false;
                // [SHULKER-CONTENTS] 놓인 셜커의 27칸은 셜커 상자를 받지 않는다(바닐라).
                // 빈 상자도 마찬가지라 참조 유무가 아니라 **아이템 종류**로 막는다.
                if (isShulkerBox(itemType[slot]) && !container.acceptsShulkerBoxes()) return false;
                int moved = container.insert(itemType[slot], count[slot], durability[slot],
                        enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                        itemComponentData[slot]);
                if (moved <= 0) return false;
                count[slot] -= moved;
                if (count[slot] == 0) {
                    clearComponentSlot(itemType, count, durability, enchantments, mapIds,
                            shulkerIds, bucketMobData, itemComponentData, slot);
                }
                advancePersistenceRevision();
                return true;
            }
            boolean changed = clickStack(
                    itemType, count, durability, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData, slot, button);
            if (changed) advancePersistenceRevision();
            return changed;
        }
        if (slot < 0 || slot >= container.slotCount()) return false;
        short targetType = container.itemType(slot);
        int targetCount = container.count(slot);
        int targetDurability = container.durability(slot);
        long targetEnchantments = container.enchantments(slot);
        int targetMapId = container.mapId(slot);
        int targetShulkerId = container.shulkerId(slot);
        String targetBucketMobData = container.bucketMobData(slot);
        String targetItemComponentData = container.itemComponentData(slot);
        if (targetType != EMPTY) {
            try {
                new StackSnapshot(targetType, targetCount, targetDurability, targetEnchantments,
                        targetMapId, targetShulkerId, targetBucketMobData, targetItemComponentData);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        if (shift) {
            if (cursorType != EMPTY || targetType == EMPTY) return false;
            StackSnapshot target = new StackSnapshot(targetType, targetCount, targetDurability,
                    targetEnchantments, targetMapId, targetShulkerId, targetBucketMobData,
                    targetItemComponentData);
            short[] nextTypes = itemType.clone();
            int[] nextCounts = count.clone();
            int[] nextDurabilities = durability.clone();
            long[] nextEnchantments = enchantments.clone();
            int[] nextMapIds = mapIds.clone();
            int[] nextShulkerIds = shulkerIds.clone();
            String[] nextBucket = bucketMobData.clone();
            String[] nextComponents = itemComponentData.clone();
            if (!foldComponentStackRange(nextTypes, nextCounts, nextDurabilities,
                    nextEnchantments, nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    target, 0, SLOTS)) return false;
            if (container.take(slot, targetCount) != targetCount) return false;
            System.arraycopy(nextTypes, 0, itemType, 0, SLOTS);
            System.arraycopy(nextCounts, 0, count, 0, SLOTS);
            System.arraycopy(nextDurabilities, 0, durability, 0, SLOTS);
            System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
            System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
            System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
            System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
            System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
            advancePersistenceRevision();
            return true;
        }
        if (cursorType == EMPTY) {
            if (targetType == EMPTY) return false;
            int picked = button == CraftButton.RIGHT ? (targetCount + 1) / 2 : targetCount;
            if (picked <= 0
                    || !containerCursorFits(targetType, picked, targetDurability,
                            targetEnchantments, targetMapId, targetShulkerId,
                            targetBucketMobData, targetItemComponentData)) {
                return false;
            }
            if (container.take(slot, picked) != picked) return false;
            setCursor(targetType, picked, targetDurability, targetEnchantments,
                    targetMapId, targetShulkerId, targetBucketMobData,
                    targetItemComponentData);
            advancePersistenceRevision();
            return true;
        }
        // 화물처럼 정체성 컬럼이 없는 컨테이너는 인챈트·지도 스택을 아예 받지 않는다.
        if (!container.acceptsStack(cursorEnchantments, cursorMapId, cursorShulkerId,
                cursorBucketMobData, cursorItemComponentData)) return false;
        // [SHULKER-CONTENTS] 커서 놓기도 같은 자리에서 막는다(빈 상자 포함).
        if (isShulkerBox(cursorType) && !container.acceptsShulkerBoxes()) return false;
        if (targetType == EMPTY
                || sameStack(cursorType, cursorDurability, cursorEnchantments, cursorMapId,
                        cursorShulkerId, targetType, targetDurability, targetEnchantments,
                        targetMapId, targetShulkerId)
                        && java.util.Objects.equals(cursorBucketMobData, targetBucketMobData)
                        && java.util.Objects.equals(
                                cursorItemComponentData, targetItemComponentData)) {
            int requested = button == CraftButton.RIGHT ? 1 : cursorCount;
            int moved = container.put(slot, cursorType, requested,
                    cursorDurability, cursorEnchantments, cursorMapId, cursorShulkerId,
                    cursorBucketMobData, cursorItemComponentData);
            if (moved <= 0) return false;
            cursorCount -= moved;
            if (cursorCount == 0) clearCursor();
            advancePersistenceRevision();
            return true;
        }
        // 이종 스택 맞바꿈. 커서로 올라올 스택이 인벤토리로 되돌아갈 수 있을 때만 허용한다.
        if (!containerCursorFits(targetType, targetCount, targetDurability, targetEnchantments,
                targetMapId, targetShulkerId, targetBucketMobData, targetItemComponentData)) {
            return false;
        }
        int removed = container.take(slot, targetCount);
        int inserted = container.put(slot, cursorType, cursorCount,
                cursorDurability, cursorEnchantments, cursorMapId, cursorShulkerId,
                cursorBucketMobData, cursorItemComponentData);
        if (removed != targetCount || inserted != cursorCount) {
            // 단일 틱 소유라 정상 경로에서는 불가능하지만, 실패해도 아이템이 사라지지 않게 되돌린다.
            if (inserted > 0) container.take(slot, inserted);
            container.put(slot, targetType, removed,
                    targetDurability, targetEnchantments, targetMapId, targetShulkerId,
                    targetBucketMobData, targetItemComponentData);
            return false;
        }
        setCursor(targetType, targetCount, targetDurability, targetEnchantments,
                targetMapId, targetShulkerId, targetBucketMobData,
                targetItemComponentData);
        advancePersistenceRevision();
        return true;
    }

    /**
     * [CONTAINER-CURSOR] 커서 스택을 여러 칸에 드래그 분배한다. 왼쪽 드래그는 대상마다 균등
     * 수량, 오른쪽 드래그는 대상마다 한 개다(바닐라 QUICK_CRAFT). 받을 수 없는 칸과 중복
     * 대상은 세지 않으므로 균등 분배의 분모가 실제 대상 수와 같다.
     */
    public synchronized boolean dragContainer(ContainerAccess container, ContainerArea[] areas, int[] slots, CraftButton button) {
        if (container == merchantPaymentAccess) {
            return mutateMerchantPayments(() -> dragContainerInternal(container, areas, slots, button));
        }
        return dragContainerInternal(container, areas, slots, button);
    }

    private boolean dragContainerInternal(ContainerAccess container, ContainerArea[] areas, int[] slots, CraftButton button) {
        if (settlementMutationBlocked()) return false;
        if (container == null || button == null || areas == null || slots == null
                || areas.length != slots.length || craftingOpen() || cursorType == EMPTY) {
            return false;
        }
        // 화물이 받지 못하는 정체성이면 인벤토리 칸만 대상이 된다(권위가 조용히 좁힌다).
        boolean containerAllowed = container.acceptsStack(cursorEnchantments, cursorMapId,
                cursorShulkerId, cursorBucketMobData, cursorItemComponentData)
                && (!isShulkerBox(cursorType) || container.acceptsShulkerBoxes());
        int containerSlots = container.slotCount();
        int[] targets = new int[areas.length];
        int targetCount = 0;
        boolean[] seenInventory = new boolean[SLOTS];
        boolean[] seenContainer = new boolean[Math.max(1, containerSlots)];
        for (int index = 0; index < areas.length; index++) {
            int slot = slots[index];
            if (areas[index] == ContainerArea.INVENTORY) {
                if (slot < 0 || slot >= SLOTS || seenInventory[slot]) continue;
                if (!canReceive(itemType, count, durability, enchantments, mapIds, shulkerIds,
                        bucketMobData, itemComponentData, slot)
                        || itemType[slot] != EMPTY
                                && (!java.util.Objects.equals(
                                        bucketMobData[slot], cursorBucketMobData)
                                    || !java.util.Objects.equals(
                                            itemComponentData[slot], cursorItemComponentData))) {
                    continue;
                }
                seenInventory[slot] = true;
                targets[targetCount++] = slot;
            } else if (areas[index] == ContainerArea.CONTAINER && containerAllowed) {
                if (slot < 0 || slot >= containerSlots || seenContainer[slot]) continue;
                if (container.roomFor(slot, cursorType, cursorDurability,
                        cursorEnchantments, cursorMapId, cursorShulkerId,
                        cursorBucketMobData, cursorItemComponentData) <= 0) continue;
                seenContainer[slot] = true;
                targets[targetCount++] = SLOTS + slot;
            }
        }
        if (targetCount == 0) return false;
        preflightRevisionCapacity();
        int amountPerSlot = button == CraftButton.RIGHT
                ? 1 : Math.max(1, cursorCount / Math.min(cursorCount, targetCount));
        boolean changed = false;
        for (int index = 0; index < targetCount && cursorType != EMPTY; index++) {
            int target = targets[index];
            int moved;
            if (target < SLOTS) {
                int capacity = stackMax(cursorType) - count[target];
                moved = Math.min(Math.min(amountPerSlot, capacity), cursorCount);
                if (moved <= 0) continue;
                if (itemType[target] == EMPTY) {
                    itemType[target] = cursorType;
                    durability[target] = cursorDurability;
                    enchantments[target] = cursorEnchantments;
                    mapIds[target] = cursorMapId;
                    shulkerIds[target] = cursorShulkerId;
                    bucketMobData[target] = cursorBucketMobData;
                    itemComponentData[target] = cursorItemComponentData;
                }
                count[target] += moved;
            } else {
                moved = container.put(target - SLOTS, cursorType,
                        Math.min(amountPerSlot, cursorCount),
                        cursorDurability, cursorEnchantments, cursorMapId, cursorShulkerId,
                        cursorBucketMobData, cursorItemComponentData);
                if (moved <= 0) continue;
            }
            cursorCount -= moved;
            if (cursorCount == 0) clearCursor();
            changed = true;
        }
        if (changed) advancePersistenceRevision();
        return changed;
    }

    /** Double-click collection over one open menu, preserving complete stack identity. */
    public synchronized boolean collectContainer(ContainerAccess container) {
        if (container == merchantPaymentAccess) {
            return mutateMerchantPayments(() -> collectContainerInternal(container));
        }
        return collectContainerInternal(container);
    }

    private boolean collectContainerInternal(ContainerAccess container) {
        if (settlementMutationBlocked() || craftingOpen() || container == null
                || cursorType == EMPTY || cursorCount >= stackMax(cursorType)) return false;
        preflightRevisionCapacity();
        boolean changed = false;
        for (int slot = 0; slot < container.slotCount()
                && cursorCount < stackMax(cursorType); slot++) {
            if (!cursorMatches(container.itemType(slot), container.durability(slot),
                    container.enchantments(slot), container.mapId(slot), container.shulkerId(slot),
                    container.bucketMobData(slot), container.itemComponentData(slot))) continue;
            int moved = container.take(slot,
                    Math.min(stackMax(cursorType) - cursorCount, container.count(slot)));
            cursorCount += moved;
            changed |= moved > 0;
        }
        for (int slot = 0; slot < SLOTS && cursorCount < stackMax(cursorType); slot++) {
            if (!cursorMatches(itemType[slot], durability[slot], enchantments[slot], mapIds[slot],
                    shulkerIds[slot], bucketMobData[slot], itemComponentData[slot])) continue;
            int moved = Math.min(stackMax(cursorType) - cursorCount, count[slot]);
            cursorCount += moved;
            count[slot] -= moved;
            if (count[slot] == 0) clearComponentSlot(itemType, count, durability, enchantments,
                    mapIds, shulkerIds, bucketMobData, itemComponentData, slot);
            changed |= moved > 0;
        }
        if (changed) advancePersistenceRevision();
        return changed;
    }

    private boolean cursorMatches(short type, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String sourceItemComponentData) {
        return sameStack(cursorType, cursorDurability, cursorEnchantments, cursorMapId,
                cursorShulkerId, type, itemDurability, itemEnchantments, itemMapId, itemShulkerId)
                && java.util.Objects.equals(cursorBucketMobData, itemBucketMobData)
                && java.util.Objects.equals(cursorItemComponentData, sourceItemComponentData);
    }

    /** [SURV-X] 컨테이너 슬롯에서 집어 든 스택을 커서에 올립니다. 인벤토리로 되돌릴 공간이 없으면 거부합니다. */
    public synchronized boolean setContainerCursor(
            short type, int amount, int itemDurability, long itemEnchantments) {
        return setContainerCursor(type, amount, itemDurability, itemEnchantments, 0);
    }

    public synchronized boolean setContainerCursor(
            short type, int amount, int itemDurability, long itemEnchantments, int itemMapId) {
        return setContainerCursor(type, amount, itemDurability, itemEnchantments, itemMapId, 0);
    }

    /** [SHULKER-CONTENTS] 27칸 참조까지 실어 커서에 올린다. */
    public synchronized boolean setContainerCursor(short type, int amount, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId) {
        return setContainerCursor(type, amount, itemDurability, itemEnchantments, itemMapId,
                itemShulkerId, null, null);
    }

    public synchronized boolean setContainerCursor(short type, int amount, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData,
            String sourceItemComponentData) {
        if (settlementMutationBlocked()) return false;
        if (craftingOpen() || cursorType != EMPTY || type == EMPTY || amount <= 0) return false;
        if (!isRegisteredItemType(type)
                || !EnchantmentRules.isValidEnchantmentMaskForItem(type, itemEnchantments)
                || !isValidMapIdentity(type, itemMapId)
                || !isValidShulkerIdentity(type, itemShulkerId)
                || !BucketMobPayloadCodec.validForItem(type, itemBucketMobData)
                || !containerCursorFits(type, amount, itemDurability, itemEnchantments,
                        itemMapId, itemShulkerId, itemBucketMobData, sourceItemComponentData)) {
            return false;
        }
        try {
            new StackSnapshot(type, amount, itemDurability, itemEnchantments, itemMapId,
                    itemShulkerId, itemBucketMobData, sourceItemComponentData);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        preflightRevisionCapacity();
        setCursor(type, amount, itemDurability,
                itemEnchantments,
                itemMapId, itemShulkerId, itemBucketMobData, sourceItemComponentData);
        advancePersistenceRevision();
        return true;
    }

    /** [SURV-X] 커서에서 amount 개를 덜어 컨테이너 슬롯으로 넘겼음을 반영합니다. */
    public synchronized boolean shrinkContainerCursor(int amount) {
        if (settlementMutationBlocked()) return false;
        if (craftingOpen() || cursorType == EMPTY || amount <= 0 || amount > cursorCount) return false;
        preflightRevisionCapacity();
        cursorCount -= amount;
        if (cursorCount == 0) clearCursor();
        advancePersistenceRevision();
        return true;
    }

    /** 제작 이외 컨테이너에서 들고 있는 스택을 월드 드랍으로 꺼냅니다. */
    public synchronized DroppedStack dropContainerCursor(boolean one) {
        if (settlementMutationBlocked()) return null;
        if (craftingOpen() || cursorType == EMPTY || cursorCount <= 0) return null;
        int amount = one ? 1 : cursorCount;
        preflightRevisionCapacity();
        DroppedStack dropped = new DroppedStack(
                cursorType, amount, cursorDurability, cursorEnchantments, cursorMapId,
                cursorShulkerId, cursorBucketMobData, cursorItemComponentData);
        cursorCount -= amount;
        if (cursorCount == 0) clearCursor();
        advancePersistenceRevision();
        return dropped;
    }

    /** 컨테이너 커서를 검증된 정규화 결과로 한 번에 접어 넣습니다. */
    public synchronized List<DroppedStack> closeContainerCursor() {
        if (settlementMutationBlocked()) return List.of();
        if (craftingOpen() || cursorType == EMPTY) return List.of();
        InventoryArrays normalized = currentNormalizedInventory();
        if (normalized == null) {
            throw new IllegalStateException("container cursor cannot be closed losslessly");
        }
        preflightRevisionCapacity();
        commitInventoryArrays(normalized);
        clearCursor();
        advancePersistenceRevision();
        return List.of();
    }

    /**
     * 커서 스택이 그 화로 칸에 들어갈 수 있는가. 입력 칸 판정은 화로 변형이 소유한다 —
     * 표를 직접 읽으면 용광로에 소고기가, 훈연기에 원철이 들어간다.
     */
    private static boolean validFurnaceSlotItem(
            FurnaceInventory furnace, int slot, short itemType) {
        return slot == FurnaceInventory.INPUT_SLOT
                ? furnace.outputFor(itemType) != EMPTY
                : slot == FurnaceInventory.FUEL_SLOT
                        && FurnaceRules.fuelTicks(itemType) > 0;
    }

    private boolean containerCursorFits(short type, int amount, int itemDurability) {
        return containerCursorFits(type, amount, itemDurability, 0, 0);
    }

    private boolean containerCursorFits(
            short type, int amount, int itemDurability, int itemMapId) {
        return containerCursorFits(type, amount, itemDurability, itemMapId, 0);
    }

    private boolean containerCursorFits(
            short type, int amount, int itemDurability, int itemMapId, int itemShulkerId) {
        return containerCursorFits(type, amount, itemDurability, itemMapId, itemShulkerId, null);
    }

    private boolean containerCursorFits(StackSnapshot stack) {
        return containerCursorFits(stack.itemType(), stack.count(), stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData());
    }

    private void setCursor(StackSnapshot stack) {
        setCursor(stack.itemType(), stack.count(), stack.durability(), stack.enchantments(),
                stack.mapId(), stack.shulkerId(), stack.bucketMobData(), stack.itemComponentData());
    }

    private boolean containerCursorFits(short type, int amount, int itemDurability,
            int itemMapId, int itemShulkerId, String itemBucketMobData) {
        return containerCursorFits(type, amount, itemDurability, itemMapId, itemShulkerId,
                itemBucketMobData, null);
    }

    private boolean containerCursorFits(short type, int amount, int itemDurability,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String sourceItemComponentData) {
        return containerCursorFits(type, amount, itemDurability,
                EnchantmentRules.EMPTY_ENCHANTMENTS, itemMapId, itemShulkerId,
                itemBucketMobData, sourceItemComponentData);
    }

    private boolean containerCursorFits(short type, int amount, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData,
            String sourceItemComponentData) {
        short[] types = itemType.clone();
        int[] counts = count.clone();
        int[] durabilities = durability.clone();
        long[] masks = enchantments.clone();
        int[] copiedMapIds = mapIds.clone();
        int[] copiedShulkerIds = shulkerIds.clone();
        String[] copiedBucketMobData = bucketMobData.clone();
        String[] copiedComponents = itemComponentData.clone();
        return foldComponentStack(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, copiedBucketMobData, copiedComponents, type, amount,
                itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, sourceItemComponentData);
    }

    /**
     * 커서 스택을 여러 슬롯에 드래그 배분합니다. 왼쪽 드래그는 대상마다 균등 수량,
     * 오른쪽 드래그는 대상마다 한 개를 놓으며 결과 슬롯과 중복 대상은 제외합니다.
     */
    public synchronized boolean dragCrafting(
            CraftArea[] areas, int[] slots, CraftButton button) {
        if (settlementMutationBlocked()) return false;
        if (!craftingOpen() || cursorType == EMPTY || button == null
                || areas == null || slots == null || areas.length != slots.length) {
            return false;
        }
        int[] targets = new int[areas.length];
        int targetCount = 0;
        boolean[] seenInventory = new boolean[SLOTS];
        boolean[] seenGrid = new boolean[9];
        boolean[] seenArmor = new boolean[ArmorSlot.values().length];
        boolean seenOffhand = false;
        for (int index = 0; index < areas.length; index++) {
            CraftArea area = areas[index];
            int slot = slots[index];
            if (area == CraftArea.INVENTORY && slot >= 0 && slot < SLOTS) {
                if (seenInventory[slot]
                        || !canReceive(
                                itemType, count, durability, enchantments, mapIds, shulkerIds,
                                bucketMobData, itemComponentData, slot)) continue;
                seenInventory[slot] = true;
                targets[targetCount++] = slot;
            } else if (area == CraftArea.GRID && validCraftingSlot(slot)) {
                if (seenGrid[slot]
                        || !canReceive(craftingType, craftingCount, craftingDurability,
                                craftingEnchantments, craftingMapIds, craftingShulkerIds,
                                craftingBucketMobData, craftingItemComponentData, slot)) continue;
                seenGrid[slot] = true;
                targets[targetCount++] = SLOTS + slot;
            } else if (area == CraftArea.ARMOR && slot >= 0
                    && slot < ArmorSlot.values().length && !seenArmor[slot]
                    && equippedType[slot] == EMPTY
                    && armorSlot(cursorType) == ArmorSlot.values()[slot]) {
                seenArmor[slot] = true;
                targets[targetCount++] = SLOTS + 9 + slot;
            } else if (area == CraftArea.OFFHAND && slot == 0 && !seenOffhand
                    && (offhand.isEmpty() || cursorMatches(offhand.itemType(),
                            offhand.durability(), offhand.enchantments(), offhand.mapId(),
                            offhand.shulkerId(), offhand.bucketMobData(),
                            offhand.itemComponentData()))
                    && offhand.count() < stackMax(cursorType)) {
                seenOffhand = true;
                targets[targetCount++] = SLOTS + 9 + ArmorSlot.values().length;
            }
        }
        if (targetCount == 0) return false;
        preflightRevisionCapacity();
        int amountPerSlot = button == CraftButton.RIGHT
                ? 1 : Math.max(1, cursorCount / Math.min(cursorCount, targetCount));
        boolean changed = false;
        for (int index = 0; index < targetCount && cursorType != EMPTY; index++) {
            int target = targets[index];
            if (target >= SLOTS + 9) {
                int special = target - SLOTS - 9;
                int moved = Math.min(amountPerSlot, cursorCount);
                if (special < ArmorSlot.values().length) {
                    moved = Math.min(1, moved);
                    writeEquipped(ArmorSlot.values()[special], new StackSnapshot(cursorType, moved,
                            cursorDurability, cursorEnchantments, 0, 0, null,
                            cursorItemComponentData));
                } else {
                    int existing = offhand.count();
                    moved = Math.min(moved, stackMax(cursorType) - existing);
                    offhand = new StackSnapshot(cursorType, existing + moved, cursorDurability,
                            cursorEnchantments, cursorMapId, cursorShulkerId,
                            cursorBucketMobData, cursorItemComponentData);
                }
                cursorCount -= moved;
                if (cursorCount == 0) clearCursor();
                changed |= moved > 0;
                continue;
            }
            short[] types = target < SLOTS ? itemType : craftingType;
            int[] counts = target < SLOTS ? count : craftingCount;
            int[] durabilities = target < SLOTS ? durability : craftingDurability;
            long[] masks = target < SLOTS ? enchantments : craftingEnchantments;
            int[] targetMapIds = target < SLOTS ? mapIds : craftingMapIds;
            int[] targetShulkerIds = target < SLOTS ? shulkerIds : craftingShulkerIds;
            String[] targetBucketMobData = target < SLOTS ? bucketMobData : craftingBucketMobData;
            String[] targetItemComponentData = target < SLOTS
                    ? itemComponentData : craftingItemComponentData;
            int slot = target < SLOTS ? target : target - SLOTS;
            int capacity = stackMax(cursorType) - counts[slot];
            int moved = Math.min(Math.min(amountPerSlot, capacity), cursorCount);
            if (moved <= 0) continue;
            if (types[slot] == EMPTY) {
                types[slot] = cursorType;
                durabilities[slot] = cursorDurability;
                masks[slot] = cursorEnchantments;
                targetMapIds[slot] = cursorMapId;
                targetShulkerIds[slot] = cursorShulkerId;
                targetBucketMobData[slot] = cursorBucketMobData;
                targetItemComponentData[slot] = cursorItemComponentData;
            }
            counts[slot] += moved;
            cursorCount -= moved;
            if (cursorCount == 0) clearCursor();
            changed = true;
        }
        if (changed) {
            advancePersistenceRevision();
            requireLosslessTransientFold();
        }
        return changed;
    }

    /** 더블클릭: 커서와 같은 아이템을 인벤토리와 제작 칸에서 한 스택 한도까지 모읍니다. */
    public synchronized boolean collectCrafting() {
        return collectCrafting(false);
    }

    public synchronized boolean collectCrafting(boolean includeEquipment) {
        if (settlementMutationBlocked()) return false;
        if (!craftingOpen() || cursorType == EMPTY) return false;
        preflightRevisionCapacity();
        boolean changed = collectFromArrays(
                craftingType, craftingCount, craftingDurability, craftingEnchantments,
                craftingMapIds, craftingShulkerIds, craftingBucketMobData,
                craftingItemComponentData, craftingSlotCount);
        if (cursorType != EMPTY) {
            changed |= collectFromArrays(
                    itemType, count, durability, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData, SLOTS);
        }
        for (ArmorSlot armor : includeEquipment ? ArmorSlot.values() : new ArmorSlot[0]) {
            if (cursorType == EMPTY || cursorCount >= stackMax(cursorType)) break;
            StackSnapshot equipped = equippedStack(armor);
            if (!equipped.isEmpty() && cursorMatches(equipped.itemType(), equipped.durability(),
                    equipped.enchantments(), equipped.mapId(), equipped.shulkerId(),
                    equipped.bucketMobData(), equipped.itemComponentData())) {
                cursorCount++;
                writeEquipped(armor, StackSnapshot.EMPTY);
                changed = true;
            }
        }
        if (includeEquipment && !offhand.isEmpty() && cursorType != EMPTY
                && cursorCount < stackMax(cursorType)
                && cursorMatches(offhand.itemType(), offhand.durability(), offhand.enchantments(),
                        offhand.mapId(), offhand.shulkerId(), offhand.bucketMobData(),
                        offhand.itemComponentData())) {
            int moved = Math.min(stackMax(cursorType) - cursorCount, offhand.count());
            cursorCount += moved;
            offhand = moved == offhand.count() ? StackSnapshot.EMPTY
                    : new StackSnapshot(offhand.itemType(), offhand.count() - moved,
                            offhand.durability(), offhand.enchantments(), offhand.mapId(),
                            offhand.shulkerId(), offhand.bucketMobData(),
                            offhand.itemComponentData());
            changed |= moved > 0;
        }
        if (changed) {
            advancePersistenceRevision();
            requireLosslessTransientFold();
        }
        return changed;
    }

    /** 컨테이너 바깥 클릭: 왼쪽은 커서 전체, 오른쪽은 한 개를 월드 드랍으로 꺼냅니다. */
    public synchronized DroppedStack dropCraftingCursor(boolean one) {
        if (settlementMutationBlocked()) return null;
        if (!craftingOpen() || cursorType == EMPTY || cursorCount <= 0) return null;
        int amount = one ? 1 : cursorCount;
        preflightRevisionCapacity();
        DroppedStack dropped = new DroppedStack(
                cursorType, amount, cursorDurability, cursorEnchantments, cursorMapId,
                cursorShulkerId, cursorBucketMobData, cursorItemComponentData);
        cursorCount -= amount;
        if (cursorCount == 0) clearCursor();
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return dropped;
    }

    /**
     * 커서와 제작 칸을 사전 검증한 정규화 배열로 한 번에 접습니다. 외부 인벤토리 변이는
     * 반환 공간을 예약하므로 정상 상태는 항상 무손실이며, 불가능한 상태는 변이 전에 거부합니다.
     */
    public synchronized List<DroppedStack> closeCrafting() {
        if (settlementMutationBlocked()) return List.of();
        if (!craftingOpen()) return List.of();
        InventoryArrays normalized = currentNormalizedInventory();
        if (normalized == null) {
            throw new IllegalStateException("crafting session cannot be closed losslessly");
        }
        preflightRevisionCapacity();
        commitInventoryArrays(normalized);
        clearCursor();
        for (int slot = 0; slot < craftingSlotCount; slot++) {
            clearCraftingSlot(slot);
        }
        craftingGridSize = 0;
        craftingSlotCount = 0;
        craftingStonecutter = false;
        craftingSelection = null;
        advancePersistenceRevision();
        return List.of();
    }

    /**
     * 두 슬롯의 내용을 맞바꿉니다(swap). 핫바·가방 어디든 0~35 범위이고 서로 다른 슬롯이어야 합니다.
     * @return 유효한 이동이면 true, 범위 밖·동일 슬롯이면 false(거부).
     */
    public synchronized boolean moveSlot(int from, int to) {
        if (settlementMutationBlocked()) return false;
        if (from < 0 || from >= SLOTS || to < 0 || to >= SLOTS || from == to) {
            return false;
        }
        preflightRevisionCapacity();
        short st = itemType[from];
        int sc = count[from];
        int sd = durability[from];
        long se = enchantments[from];
        int sm = mapIds[from];
        int ss = shulkerIds[from];
        String sb = bucketMobData[from];
        String scd = itemComponentData[from];
        itemType[from] = itemType[to];
        count[from] = count[to];
        durability[from] = durability[to];
        enchantments[from] = enchantments[to];
        mapIds[from] = mapIds[to];
        shulkerIds[from] = shulkerIds[to];
        bucketMobData[from] = bucketMobData[to];
        itemComponentData[from] = itemComponentData[to];
        itemType[to] = st;
        count[to] = sc;
        durability[to] = sd;
        enchantments[to] = se;
        mapIds[to] = sm;
        shulkerIds[to] = ss;
        bucketMobData[to] = sb;
        itemComponentData[to] = scd;
        advancePersistenceRevision();
        return true;
    }

    /**
     * 아이템을 인벤토리 전체(0~35)에 담습니다(드랍 엔티티 획득용, S2a). 같은 종류 스택 여유부터
     * 채우고 남으면 빈 칸에 새 스택으로 놓습니다. 새로 생성된 도구는 종류별 최대 내구도로 들어갑니다.
     * @return 실제로 담긴 개수(0 ~ count). count 보다 작으면 공간이 부족해 나머지는 담기지 못한 것입니다.
     */
    public synchronized int addItem(short type, int count) {
        return addItem(type, count, initialDurability(type));
    }

    /**
     * 드랍·상자에서 옮겨 온 아이템을 원래 내구도와 함께 담습니다. 내구 아이템은 스택 1이므로
     * 전달된 값 하나가 한 엔티티/슬롯에 정확히 대응합니다.
     */
    public synchronized int addItem(short type, int count, int itemDurability) {
        return addItem(type, count, itemDurability, EnchantmentRules.EMPTY_ENCHANTMENTS);
    }

    /** [SURV-X] 인챈트된 드랍/상자 아이템은 원래 마스크를 그대로 실어 담습니다. */
    public synchronized int addItem(short type, int count, int itemDurability, long itemEnchantments) {
        return addItem(type, count, itemDurability, itemEnchantments, 0);
    }

    public synchronized int addItem(
            short type, int count, int itemDurability, long itemEnchantments, int itemMapId) {
        return addItem(type, count, itemDurability, itemEnchantments, itemMapId, 0);
    }

    /**
     * [SHULKER-CONTENTS] 27칸을 물고 있는 셜커 상자를 그 참조 ID 째로 담는다. 참조가 붙은
     * 상자는 스택 1 이므로 한 번에 한 개만 들어온다(스택 2 면 27칸이 복제된다).
     */
    public synchronized int addItem(short type, int count, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId) {
        return addItem(type, count, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                null);
    }

    /** Ground/container pickup including the strict aquatic bucket identity component. */
    public synchronized int addItem(short type, int count, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData) {
        return addItem(type, count, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, null);
    }

    public synchronized int addItem(short type, int count, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData,
            String sourceItemComponentData) {
        if (settlementMutationBlocked()) return 0;
        if (!isRegisteredItemType(type) || count <= 0
                || !EnchantmentRules.isValidEnchantmentMaskForItem(type, itemEnchantments)
                || !isValidMapIdentity(type, itemMapId)
                || !isValidShulkerIdentity(type, itemShulkerId)
                || (itemShulkerId != 0 && count != 1)
                || !BucketMobPayloadCodec.validForItem(type, itemBucketMobData)) {
            return 0;
        }
        try {
            ItemComponentData components = ItemComponentCodec.decode(type, sourceItemComponentData);
            // [ENCHANT-WIDE] 확장 인챈트는 워드 0 과 한 집합으로 대상·배타 판정을 받는다.
            if (components.hasExtendedEnchantments()
                    && !EnchantmentRules.isValidEnchantmentsForItem(
                            type, components.enchantments(itemEnchantments))) {
                return 0;
            }
        }
        catch (IllegalArgumentException invalid) { return 0; }
        if (isDurable(type)) {
            if (itemDurability <= 0 || itemDurability > initialDurability(type)) return 0;
        } else if (itemDurability != 0) {
            return 0;
        }
        int safeDurability = itemDurability;

        // 제작 칸/커서를 닫을 때 되돌릴 공간까지 포함한 실제 수용량만 허용합니다.
        InventoryArrays normalized = currentNormalizedInventory();
        if (normalized == null) return 0;
        long safeEnchantments = itemEnchantments;
        int accepted = addToArrays(normalized.types, normalized.counts, normalized.durabilities,
                normalized.enchantments, normalized.mapIds, normalized.shulkerIds,
                normalized.bucketMobData, normalized.itemComponentData,
                type, count, safeDurability, safeEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, sourceItemComponentData, 0, SLOTS);
        if (accepted == 0) return 0;
        preflightRevisionCapacity();
        int inserted = addToArrays(itemType, this.count, durability, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData, type, accepted, safeDurability,
                safeEnchantments, itemMapId, itemShulkerId, itemBucketMobData,
                sourceItemComponentData, 0, SLOTS);
        if (inserted != accepted) {
            throw new IllegalStateException("visible inventory cannot hold normalized pickup");
        }
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return inserted;
    }

    /** 상자/드랍 이동 전에 슬롯의 종류·개수·내구도를 한 번에 읽는 스냅샷입니다. */
    public synchronized DroppedStack stackAt(int slot) {
        if (slot < 0 || slot >= SLOTS || itemType[slot] == EMPTY || count[slot] <= 0) return null;
        return new DroppedStack(itemType[slot], count[slot], durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
    }

    /**
     * 던지기 요청 한 번을 원자적으로 검증·차감합니다. 슬롯에 실제로 든 수량보다 많이 요청하면
     * 일부만 빼지 않고 거부하며, 성공 결과에는 해당 슬롯의 남은 내구도를 함께 보존합니다.
     */
    public synchronized DroppedStack dropFromSlot(int slot, int amount) {
        if (settlementMutationBlocked()) return null;
        if (slot < 0 || slot >= SLOTS || amount <= 0
                || itemType[slot] == EMPTY || count[slot] < amount) {
            return null;
        }
        DroppedStack dropped = new DroppedStack(itemType[slot], amount, durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
        preflightRevisionCapacity();
        count[slot] -= amount;
        if (count[slot] == 0) {
            itemType[slot] = EMPTY;
            durability[slot] = 0;
            enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
        advancePersistenceRevision();
        return dropped;
    }

    /** 상자로 옮길 때 지정 슬롯에서 최대 amount개를 꺼냅니다. */
    public synchronized int takeFromSlot(int slot, int amount) {
        if (settlementMutationBlocked()) return 0;
        if (slot < 0 || slot >= SLOTS || amount <= 0 || itemType[slot] == EMPTY
                || bucketMobData[slot] != null || itemComponentData[slot] != null) return 0;
        int taken = Math.min(count[slot], amount);
        if (taken <= 0) return 0;
        preflightRevisionCapacity();
        count[slot] -= taken;
        if (count[slot] == 0) {
            itemType[slot] = EMPTY;
            durability[slot] = 0;
            enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
        advancePersistenceRevision();
        return taken;
    }

    /**
     * 지정 슬롯의 도구/검 내구도를 1 깎습니다. 0 에 도달하면 아이템이 사라집니다(빈 칸).
     * 내구 없는 슬롯(빈 칸/블록/막대)에는 아무 효과가 없습니다.
     */
    public synchronized void degrade(int slot) {
        if (settlementMutationBlocked()) return;
        if (slot < 0 || slot >= SLOTS || !isDurable(itemType[slot])) {
            return;
        }
        // [SURV-X] 인벤토리 내구 소모의 단일 관문. 내구성(Unbreaking) 판정은 여기 한 곳에서만 한다.
        int unbreaking = EnchantmentRules.enchantLevel(
                enchantments[slot], EnchantmentRules.UNBREAKING);
        if (unbreaking > 0 && EnchantmentRules.unbreakingSkipsDurability(unbreaking,
                isArmor(itemType[slot]), durabilityRandom.nextInt(EnchantmentRules.MILLI),
                durabilityRandom.nextInt(Integer.MAX_VALUE))) {
            return;
        }
        preflightRevisionCapacity();
        advancePersistenceRevision();
        durability[slot]--;
        if (durability[slot] <= 0) {
            itemType[slot] = EMPTY;
            count[slot] = 0;
            durability[slot] = 0;
            enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
    }

    /** 현재 손의 내구 아이템을 기존 Unbreaking 관문을 통해 한 번 마모합니다. */
    public synchronized void degrade(Hand hand) {
        if (settlementMutationBlocked()) return;
        if (hand == null) throw new IllegalArgumentException("hand is required");
        if (hand == Hand.MAIN) {
            degrade(selectedSlot);
            return;
        }
        if (offhand.isEmpty() || !isDurable(offhand.itemType())) return;
        int unbreaking = EnchantmentRules.enchantLevel(
                offhand.enchantments(), EnchantmentRules.UNBREAKING);
        if (unbreaking > 0 && EnchantmentRules.unbreakingSkipsDurability(unbreaking,
                isArmor(offhand.itemType()), durabilityRandom.nextInt(EnchantmentRules.MILLI),
                durabilityRandom.nextInt(Integer.MAX_VALUE))) {
            return;
        }
        int remaining = offhand.durability() - 1;
        preflightRevisionCapacity();
        offhand = remaining <= 0 ? StackSnapshot.EMPTY
                : new StackSnapshot(offhand.itemType(), offhand.count(), remaining,
                        offhand.enchantments(), offhand.mapId(), offhand.shulkerId(),
                        offhand.bucketMobData(), offhand.itemComponentData());
        advancePersistenceRevision();
    }

    public synchronized void degrade(HandRef ref) {
        if (settlementMutationBlocked()) return;
        if (!currentHandRefMatches(ref)) return;
        if (ref.hand == Hand.MAIN) degrade(ref.mainSlot);
        else degrade(Hand.OFFHAND);
    }

    /**
     * [ENCHANT-WIDE] {@link #consumeOne(short)} 이 소비할 수 있는 칸이 있는가(소비하지 않는다). 무한 활이
     * 탄약을 쓰지 않으면서도 바닐라처럼 화살 한 개는 지니고 있어야 쏠 수 있게 하는 판정이다.
     */
    public synchronized boolean containsItem(short type) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemType[slot] == type && count[slot] > 0) return true;
        }
        return false;
    }

    /** 인벤토리 전체에서 지정 일반 아이템 하나를 원자적으로 소비합니다. */
    public synchronized boolean consumeOne(short type) {
        if (settlementMutationBlocked()) return false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemType[slot] != type || count[slot] <= 0) continue;
            preflightRevisionCapacity();
            count[slot]--;
            if (count[slot] == 0) {
                itemType[slot] = EMPTY;
                durability[slot] = 0;
                enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
                mapIds[slot] = 0;
                shulkerIds[slot] = 0;
                bucketMobData[slot] = null;
                itemComponentData[slot] = null;
            }
            advancePersistenceRevision();
            return true;
        }
        return false;
    }

    /** [TRIAL-GAP] 활·석궁 탄약(바닐라 {@code #arrows} 중 이 저장소에 있는 화살 · 효과 화살 · 분광 화살). */
    public static boolean isArrowAmmo(short type) {
        return type == ARROW || isTippedArrow(type) || type == SPECTRAL_ARROW;
    }

    /**
     * [TRIAL-GAP] 바닐라 {@code Player.getProjectile}: 보조손 → 주손({@code ProjectileWeaponItem
     * .getHeldProjectile}) → 인벤토리 0..35 순서로 첫 화살류 하나를 소비하고 그 종류를 돌려준다.
     * 없으면 {@link #EMPTY}.
     */
    /**
     * [ENCHANT-WIDE] {@link #consumeOneArrowAmmo} 가 먹을 탄약 종류(보조손 → 선택 칸 → 첫 칸 순)를 먹지 않고
     * 알려 준다. 무한 인챈트는 보통 화살만 아끼므로 먼저 종류를 봐야 한다. 없으면 {@link #EMPTY}.
     */
    public synchronized short peekArrowAmmo() {
        if (isArrowAmmo(offhand.itemType()) && offhand.count() > 0) return offhand.itemType();
        short selected = itemType[selectedSlot];
        if (isArrowAmmo(selected) && count[selectedSlot] > 0) return selected;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (isArrowAmmo(itemType[slot]) && count[slot] > 0) return itemType[slot];
        }
        return EMPTY;
    }

    public synchronized short consumeOneArrowAmmo() {
        if (settlementMutationBlocked()) return EMPTY;
        if (isArrowAmmo(offhand.itemType()) && offhand.count() > 0) {
            short type = offhand.itemType();
            return consumeOne(Hand.OFFHAND, type) ? type : EMPTY;
        }
        short selected = itemType[selectedSlot];
        if (isArrowAmmo(selected) && count[selectedSlot] > 0) {
            return consumeSelectedOne(selected) ? selected : EMPTY;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            short type = itemType[slot];
            if (!isArrowAmmo(type) || count[slot] <= 0) continue;
            preflightRevisionCapacity();
            count[slot]--;
            if (count[slot] == 0) {
                clearComponentSlot(itemType, count, durability, enchantments, mapIds,
                        shulkerIds, bucketMobData, itemComponentData, slot);
            }
            advancePersistenceRevision();
            return type;
        }
        return EMPTY;
    }

    /** 선택한 주손 슬롯에서 지정 일반 아이템 하나를 원자적으로 소비합니다. */
    public synchronized boolean consumeSelectedOne(short type) {
        if (settlementMutationBlocked()) return false;
        int slot = selectedSlot;
        if (itemType[slot] != type || count[slot] <= 0) return false;
        preflightRevisionCapacity();
        count[slot]--;
        if (count[slot] == 0) {
            itemType[slot] = EMPTY;
            durability[slot] = 0;
            enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            mapIds[slot] = 0;
            shulkerIds[slot] = 0;
            bucketMobData[slot] = null;
            itemComponentData[slot] = null;
        }
        advancePersistenceRevision();
        return true;
    }

    /** 현재 손에서 예상 종류 하나만 소비합니다. */
    public synchronized boolean consumeOne(Hand hand, short type) {
        if (settlementMutationBlocked()) return false;
        if (hand == null) throw new IllegalArgumentException("hand is required");
        if (hand == Hand.MAIN) return consumeSelectedOne(type);
        if (offhand.itemType() != type || offhand.count() <= 0) return false;
        int remaining = offhand.count() - 1;
        preflightRevisionCapacity();
        offhand = remaining == 0 ? StackSnapshot.EMPTY
                : new StackSnapshot(type, remaining, offhand.durability(), offhand.enchantments(),
                        offhand.mapId(), offhand.shulkerId(), offhand.bucketMobData(),
                        offhand.itemComponentData());
        advancePersistenceRevision();
        return true;
    }

    public synchronized boolean consumeOne(HandRef ref, short type) {
        if (settlementMutationBlocked()) return false;
        if (!currentHandRefMatches(ref)) return false;
        if (ref.hand == Hand.OFFHAND) return consumeOne(Hand.OFFHAND, type);
        int slot = ref.mainSlot;
        if (itemType[slot] != type || count[slot] <= 0) return false;
        preflightRevisionCapacity();
        count[slot]--;
                if (count[slot] == 0) {
                    clearComponentSlot(itemType, count, durability, enchantments, mapIds,
                            shulkerIds, bucketMobData, itemComponentData, slot);
        }
        advancePersistenceRevision();
        return true;
    }

    /**
     * 선택 슬롯의 개별 아이템을 같은 슬롯의 다른 개별 아이템으로 원자적 교체합니다.
     * 예상 종류가 아니거나 count가 1이 아니면 아무것도 바꾸지 않습니다.
     */
    public synchronized boolean replaceSelectedSingle(short expectedType, short replacementType) {
        if (settlementMutationBlocked()) return false;
        int slot = selectedSlot;
        if (itemType[slot] != expectedType || count[slot] != 1) {
            return false;
        }
        StackSnapshot replacement = new StackSnapshot(replacementType, 1,
                isDurable(replacementType) ? initialDurability(replacementType) : 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
        if (normalizedInventory(replacement, slot) == null) return false;
        preflightRevisionCapacity();
        writeSlot(slot, replacement);
        advancePersistenceRevision();
        return true;
    }

    /** 현재 손의 단일 아이템을 같은 손의 새 단일 아이템으로 교체합니다. */
    public synchronized boolean replaceSingle(Hand hand, short expectedType, short replacementType) {
        if (settlementMutationBlocked()) return false;
        if (hand == null) throw new IllegalArgumentException("hand is required");
        if (hand == Hand.MAIN) return replaceSelectedSingle(expectedType, replacementType);
        if (offhand.itemType() != expectedType || offhand.count() != 1) {
            return false;
        }
        preflightRevisionCapacity();
        offhand = new StackSnapshot(replacementType, 1,
                isDurable(replacementType) ? initialDurability(replacementType) : 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
        advancePersistenceRevision();
        return true;
    }

    public synchronized boolean replaceSingle(
            HandRef ref, short expectedType, short replacementType) {
        if (settlementMutationBlocked()) return false;
        return replaceSingle(ref, expectedType, replacementType, null);
    }

    /** 포획한 개체의 엄격한 identity component와 함께 같은 손의 단일 양동이를 교체합니다. */
    public synchronized boolean replaceSingle(
            HandRef ref, short expectedType, short replacementType, String replacementBucketMobData) {
        if (settlementMutationBlocked()) return false;
        if (!currentHandRefMatches(ref)) return false;
        StackSnapshot replacement = new StackSnapshot(replacementType, 1,
                isDurable(replacementType) ? initialDurability(replacementType) : 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, replacementBucketMobData, null);
        if (ref.hand == Hand.OFFHAND) {
            if (offhand.itemType() != expectedType || offhand.count() != 1) return false;
            preflightRevisionCapacity();
            offhand = replacement;
            advancePersistenceRevision();
            return true;
        }
        int slot = ref.mainSlot;
        if (itemType[slot] != expectedType || count[slot] != 1) {
            return false;
        }
        if (normalizedInventory(replacement, slot) == null) return false;
        preflightRevisionCapacity();
        writeSlot(slot, replacement);
        advancePersistenceRevision();
        return true;
    }

    /** 영속 지도가 생성된 뒤 해당 빈 지도 한 개를 같은 ID의 채워진 지도로 확정합니다. */
    public synchronized MapUseResult completeMapUse(int sourceSlot, int newMapId) {
        if (settlementMutationBlocked()) return new MapUseResult(false, null);
        if (sourceSlot < 0 || sourceSlot >= SLOTS || newMapId <= 0
                || itemType[sourceSlot] != MAP || count[sourceSlot] <= 0) {
            return new MapUseResult(false, null);
        }
        if (count[sourceSlot] == 1) {
            StackSnapshot filled = new StackSnapshot(FILLED_MAP, 1, 0,
                    EnchantmentRules.EMPTY_ENCHANTMENTS, newMapId, 0, null,
                    itemComponentData[sourceSlot]);
            if (normalizedInventory(filled, sourceSlot) == null) {
                return new MapUseResult(false, null);
            }
            preflightRevisionCapacity();
            itemType[sourceSlot] = FILLED_MAP;
            count[sourceSlot] = 1;
            durability[sourceSlot] = 0;
            enchantments[sourceSlot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
            mapIds[sourceSlot] = newMapId;
            shulkerIds[sourceSlot] = 0;
            bucketMobData[sourceSlot] = null;
            advancePersistenceRevision();
            requireLosslessTransientFold();
            return new MapUseResult(true, null);
        }
        preflightRevisionCapacity();
        count[sourceSlot]--;
        String sourceComponents = itemComponentData[sourceSlot];
        boolean inserted = foldComponentStack(itemType, count, durability, enchantments,
                mapIds, shulkerIds, bucketMobData, itemComponentData, FILLED_MAP, 1, 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, newMapId, 0, null, sourceComponents);
        DroppedStack overflow = inserted ? null
                : new DroppedStack(FILLED_MAP, 1, 0, EnchantmentRules.EMPTY_ENCHANTMENTS,
                        newMapId, 0, null, sourceComponents);
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return new MapUseResult(true, overflow);
    }

    /** 현재 손의 빈 지도를 완성하고, 분리된 결과가 넘치면 기존 월드 드롭 계약으로 반환합니다. */
    public synchronized MapUseResult completeMapUse(Hand hand, int newMapId) {
        if (settlementMutationBlocked()) return new MapUseResult(false, null);
        if (hand == null) throw new IllegalArgumentException("hand is required");
        if (hand == Hand.MAIN) return completeMapUse(selectedSlot, newMapId);
        if (newMapId <= 0 || offhand.itemType() != MAP || offhand.count() <= 0) {
            return new MapUseResult(false, null);
        }
        if (offhand.count() == 1) {
            preflightRevisionCapacity();
            offhand = new StackSnapshot(FILLED_MAP, 1, 0,
                    EnchantmentRules.EMPTY_ENCHANTMENTS, newMapId, 0, null,
                    offhand.itemComponentData());
            advancePersistenceRevision();
            requireLosslessTransientFold();
            return new MapUseResult(true, null);
        }
        preflightRevisionCapacity();
        String sourceComponents = offhand.itemComponentData();
        offhand = new StackSnapshot(MAP, offhand.count() - 1, 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null,
                offhand.itemComponentData());
        boolean inserted = foldComponentStack(itemType, count, durability, enchantments,
                mapIds, shulkerIds, bucketMobData, itemComponentData, FILLED_MAP, 1, 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, newMapId, 0, null, sourceComponents);
        DroppedStack overflow = inserted ? null
                : new DroppedStack(FILLED_MAP, 1, 0, EnchantmentRules.EMPTY_ENCHANTMENTS,
                        newMapId, 0, null, sourceComponents);
        advancePersistenceRevision();
        requireLosslessTransientFold();
        return new MapUseResult(true, overflow);
    }

    public synchronized MapUseResult completeMapUse(HandRef ref, int newMapId) {
        if (settlementMutationBlocked()) return new MapUseResult(false, null);
        if (!currentHandRefMatches(ref)) return new MapUseResult(false, null);
        return ref.hand == Hand.OFFHAND
                ? completeMapUse(Hand.OFFHAND, newMapId)
                : completeMapUse(ref.mainSlot, newMapId);
    }

    /** 선택 유리병 하나를 소비하고 비중첩 물병 하나를 지급한다. 공간 부족이면 완전 무변경이다. */
    public synchronized boolean fillSelectedGlassBottle() {
        if (settlementMutationBlocked()) return false;
        int slot = selectedSlot;
        if (itemType[slot] != (short) Blocks.GLASS_BOTTLE || count[slot] <= 0) return false;
        short[] t = itemType.clone();
        int[] c = count.clone();
        int[] d = durability.clone();
        long[] e = enchantments.clone();
        int[] m = mapIds.clone();
        int[] sk = shulkerIds.clone();
        String[] b = bucketMobData.clone();
        String[] components = itemComponentData.clone();
        c[slot]--;
        if (c[slot] == 0) {
            clearComponentSlot(t, c, d, e, m, sk, b, components, slot);
        }
        if (!give(t, c, d, e, m, sk, b, components, (short) Blocks.WATER_BOTTLE, 1)) return false;
        if (normalizedInventory(t, c, d, e, m, sk, b, components) == null) return false;
        preflightRevisionCapacity();
        System.arraycopy(t, 0, itemType, 0, SLOTS);
        System.arraycopy(c, 0, count, 0, SLOTS);
        System.arraycopy(d, 0, durability, 0, SLOTS);
        System.arraycopy(e, 0, enchantments, 0, SLOTS);
        System.arraycopy(m, 0, mapIds, 0, SLOTS);
        System.arraycopy(sk, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(b, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(components, 0, itemComponentData, 0, SLOTS);
        advancePersistenceRevision();
        return true;
    }

    /**
     * Captured-hand container exchange (one input consumed, one output returned) as one visible
     * inventory mutation. It plans against a detached complete snapshot, so a full inventory or an
     * incompatible crafting/container cursor rejects without consuming the input.
     */
    public synchronized boolean exchangeOne(HandRef ref, short expectedType, short replacementType) {
        if (settlementMutationBlocked()) return false;
        if (!currentHandRefMatches(ref)) return false;
        StackSnapshot before = ref.capturedStack;
        if (before.itemType() != expectedType || before.count() <= 0) return false;
        preflightRevisionCapacity();
        PlayerInventory detached = completePersistenceSnapshot().detachedInventory();
        HandRef detachedRef = detached.captureExactHand(ref.hand, ref.mainSlot);
        if (before.count() == 1) {
            StackSnapshot replacement = new StackSnapshot(replacementType, 1,
                    isDurable(replacementType) ? initialDurability(replacementType) : 0,
                    EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
            if (!detached.setStack(detachedRef, replacement)) return false;
        } else if (!detached.consumeOne(detachedRef, expectedType)
                || detached.addItem(replacementType, 1) != 1) return false;
        CompletePersistenceSnapshot planned;
        try {
            planned = detached.completePersistenceSnapshot();
        } catch (IllegalStateException impossibleFold) {
            return false;
        }
        installCompleteSnapshot(planned);
        return true;
    }

    /** Atomically transforms one exact held stack while preserving overflow/fold safety. */
    public synchronized boolean transformOne(
            HandRef ref, StackSnapshot expected, StackSnapshot replacement) {
        if (settlementMutationBlocked() || ref == null || expected == null || replacement == null
                || replacement.count() != 1 || !currentHandRefMatches(ref)
                || !ref.capturedStack.equals(expected)) return false;
        preflightRevisionCapacity();
        PlayerInventory detached = completePersistenceSnapshot().detachedInventory();
        HandRef detachedRef = detached.captureExactHand(ref.hand, ref.mainSlot);
        if (expected.count() == 1) {
            if (!detached.setStack(detachedRef, replacement)) return false;
        } else {
            if (!detached.consumeOne(detachedRef, expected.itemType())
                    || detached.addItem(replacement.itemType(), 1, replacement.durability(),
                            replacement.enchantments(), replacement.mapId(), replacement.shulkerId(),
                            replacement.bucketMobData(), replacement.itemComponentData()) != 1) {
                return false;
            }
        }
        CompletePersistenceSnapshot planned;
        try { planned = detached.completePersistenceSnapshot(); }
        catch (IllegalStateException impossibleFold) { return false; }
        installCompleteSnapshot(planned);
        return true;
    }

    /** Advances the exact persistence token for a state-only settlement using this inventory. */
    public synchronized boolean advanceStateSettlementRevision() {
        if (settlementMutationBlocked()) return false;
        advancePersistenceRevision();
        return true;
    }

    public synchronized boolean editWritableBook(HandRef ref, java.util.List<String> pages) {
        if (settlementMutationBlocked() || ref == null) return false;
        StackSnapshot current = stack(ref);
        if (current.itemType() != WRITABLE_BOOK || current.count() != 1) return false;
        ItemComponentData components = current.itemComponents();
        try {
            ItemComponentData next = components.withBook(
                    new ItemComponentData.BookData(null, null, pages));
            return setStack(ref, new StackSnapshot(WRITABLE_BOOK, 1, 0,
                    current.enchantments(), current.mapId(), current.shulkerId(),
                    current.bucketMobData(), ItemComponentCodec.encode(WRITABLE_BOOK, next)));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public synchronized boolean signWritableBook(
            HandRef ref, String title, String author) {
        if (settlementMutationBlocked() || ref == null) return false;
        StackSnapshot current = stack(ref);
        if (current.itemType() != WRITABLE_BOOK || current.count() != 1) return false;
        ItemComponentData components = current.itemComponents();
        if (components.book() == null) return false;
        try {
            ItemComponentData next = components.withBook(new ItemComponentData.BookData(
                    title, author, components.book().pages()));
            return setStack(ref, new StackSnapshot(WRITTEN_BOOK, 1, 0,
                    current.enchantments(), current.mapId(), current.shulkerId(),
                    current.bucketMobData(), ItemComponentCodec.encode(WRITTEN_BOOK, next)));
        } catch (IllegalArgumentException invalid) { return false; }
    }

    // ── InventoryRules 가 사용하는 저수준 변이(드랍 수납·설치 소비) ──

    synchronized boolean set(int slot, short type, int amount) {
        if (settlementMutationBlocked()) return false;
        if (slot < 0 || slot >= SLOTS) return false;
        if (isFilledMapItem(type) && amount > 0) {
            throw new IllegalArgumentException("filled maps require a map ID");
        }
        int nextDurability = durability[slot];
        long nextEnchantments = enchantments[slot];
        int nextMapId = mapIds[slot];
        int nextShulkerId = shulkerIds[slot];
        String nextBucketMobData = bucketMobData[slot];
        String nextItemComponentData = itemComponentData[slot];
        if (itemType[slot] != type) {
            nextDurability = 0;
            nextEnchantments = EnchantmentRules.EMPTY_ENCHANTMENTS;
            nextMapId = 0;
            nextShulkerId = 0;
            nextBucketMobData = null;
            nextItemComponentData = null;
        }
        if (amount <= 0) {
            type = EMPTY;
            amount = 0;
            nextDurability = 0;
            nextEnchantments = EnchantmentRules.EMPTY_ENCHANTMENTS;
            nextMapId = 0;
            nextShulkerId = 0;
            nextBucketMobData = null;
            nextItemComponentData = null;
        } else if (!isDurable(type)) {
            nextDurability = 0;
        } else if (nextDurability <= 0) {
            // 내구 있는 아이템(검·도구·방어구)을 새로 넣을 때는 기본 내구도를 부여한다.
            // 이 초기화가 없으면 방어구가 내구 0 으로 착용되어 첫 피격에 사라진다.
            nextDurability = initialDurability(type);
        }
        short[] nextTypes = itemType.clone();
        int[] nextCounts = count.clone();
        int[] nextDurabilities = durability.clone();
        long[] nextMasks = enchantments.clone();
        int[] nextMapIds = mapIds.clone();
        int[] nextShulkerIds = shulkerIds.clone();
        String[] nextBucketMobDataArray = bucketMobData.clone();
        String[] nextItemComponentDataArray = itemComponentData.clone();
        nextTypes[slot] = type;
        nextCounts[slot] = amount;
        nextDurabilities[slot] = nextDurability;
        nextMasks[slot] = nextEnchantments;
        nextMapIds[slot] = nextMapId;
        nextShulkerIds[slot] = nextShulkerId;
        nextBucketMobDataArray[slot] = nextBucketMobData;
        nextItemComponentDataArray[slot] = nextItemComponentData;
        if (normalizedInventory(nextTypes, nextCounts, nextDurabilities, nextMasks,
                nextMapIds, nextShulkerIds, nextBucketMobDataArray,
                nextItemComponentDataArray) == null) return false;
        preflightRevisionCapacity();
        itemType[slot] = type;
        count[slot] = amount;
        durability[slot] = nextDurability;
        enchantments[slot] = nextEnchantments;
        mapIds[slot] = nextMapId;
        shulkerIds[slot] = nextShulkerId;
        bucketMobData[slot] = nextBucketMobData;
        itemComponentData[slot] = nextItemComponentData;
        advancePersistenceRevision();
        return true;
    }

    synchronized void add(int slot, int amount) {
        if (settlementMutationBlocked()) return;
        if (slot < 0 || slot >= SLOTS) return;
        advancePersistenceRevision();
        count[slot] += amount;
    }

    // ── 제작 컨테이너/임시 배열 헬퍼 ──

    private boolean validCraftingSlot(int slot) {
        if (!craftingOpen() || slot < 0) return false;
        // [STONECUT] 절단기 화면의 재료 칸은 바닐라와 같이 <b>하나</b>다(슬롯 0).
        if (craftingStonecutter) return slot == 0;
        return slot < craftingSlotCount;
    }

    private CraftRecipe currentRecipe() {
        // [STONECUT] 절단 세션의 결과는 격자 배치가 아니라 "입력 칸 + 선택"이 정한다.
        // 선택이 없거나 입력이 바뀌어 더 이상 유도되지 않으면 결과 칸은 비어 있다.
        if (craftingStonecutter) {
            return CraftRecipe.matchStonecutting(craftingType[0], craftingSelection);
        }
        CraftRecipe recipe = CraftRecipe.match(craftingType, craftingGridSize, false);
        if (recipe != null && CraftRecipe.SHIELD_DECORATION.equals(recipe.id())
                && shieldAlreadyPatterned()) return null;
        return recipe;
    }

    private boolean takeCraftResult() {
        CraftRecipe recipe = currentRecipe();
        if (recipe == null) return false;
        short output = recipe.outputType();
        int amount = recipe.outputCount();
        int outputDurability = craftingOutputDurability(recipe);
        long outputEnchantments = craftingOutputEnchantments(recipe);
        // [SHULKER-CONTENTS] 염색은 내용을 유지한다 — 산출 칸이 입력 상자의 참조를 물려받는다.
        int outputMapId = craftingOutputMapId(recipe);
        int outputShulkerId = inheritedCraftingShulkerId(output);
        String outputComponents = craftingOutputItemComponentData(recipe);
        TransientMutationSnapshot before = new TransientMutationSnapshot(this);
        if (cursorType == EMPTY) {
            setCursor(output, amount, outputDurability, outputEnchantments,
                    outputMapId, outputShulkerId, null, outputComponents);
        } else if (cursorType == output && cursorDurability == outputDurability
                && cursorEnchantments == outputEnchantments
                // 참조가 다른 상자는 합치지 않는다(합치면 한 27칸이 버려진다).
                && cursorMapId == outputMapId
                && cursorShulkerId == outputShulkerId
                && cursorBucketMobData == null
                && java.util.Objects.equals(cursorItemComponentData, outputComponents)
                && cursorCount + amount <= stackMax(output)) {
            cursorCount += amount;
        } else {
            return false;
        }
        if (!consumeCraftingIngredients()) {
            before.restore(this);
            return false;
        }
        return true;
    }

    private boolean shiftCraftResult() {
        CraftRecipe initial = currentRecipe();
        if (initial == null) return false;
        short output = initial.outputType();
        int transferred = 0;
        while (true) {
            CraftRecipe recipe = currentRecipe();
            if (recipe == null || recipe.outputType() != output
                    || recipe.outputCount() != initial.outputCount()) break;
            TransientMutationSnapshot before = new TransientMutationSnapshot(this);
            short[] nextType = itemType.clone();
            int[] nextCount = count.clone();
            int[] nextDurability = durability.clone();
            long[] nextEnchantments = enchantments.clone();
            int[] nextMapIds = mapIds.clone();
            int[] nextShulkerIds = shulkerIds.clone();
            String[] nextBucket = bucketMobData.clone();
            String[] nextComponents = itemComponentData.clone();
            StackSnapshot result = new StackSnapshot(output, recipe.outputCount(),
                    craftingOutputDurability(recipe), craftingOutputEnchantments(recipe), craftingOutputMapId(recipe),
                    inheritedCraftingShulkerId(output), null,
                    craftingOutputItemComponentData(recipe));
            if (!foldComponentStackRange(nextType, nextCount, nextDurability,
                    nextEnchantments, nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    result, 0, SLOTS)) break;
            System.arraycopy(nextType, 0, itemType, 0, SLOTS);
            System.arraycopy(nextCount, 0, count, 0, SLOTS);
            System.arraycopy(nextDurability, 0, durability, 0, SLOTS);
            System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
            System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
            System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
            System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
            System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
            if (!consumeCraftingIngredients() || currentNormalizedInventory() == null) {
                before.restore(this);
                break;
            }
            transferred += recipe.outputCount();
        }
        return transferred > 0;
    }

    private boolean consumeCraftingIngredients() {
        int cells = craftingSlotCount;
        for (int slot = 0; slot < cells; slot++) {
            short type = craftingType[slot];
            if (type == EMPTY) continue;
            short remainder = InventoryRules.craftingRemainder(type);
            craftingCount[slot]--;
            if (craftingCount[slot] == 0) {
                if (remainder == EMPTY) clearCraftingSlot(slot);
                else setCraftingSlot(slot, remainder, 1);
            } else if (remainder != EMPTY && !give(itemType, count, durability,
                    enchantments, mapIds, shulkerIds, bucketMobData, itemComponentData,
                    remainder, 1)) {
                // The caller restores its pre-craft snapshot if returned containers cannot fit.
                return false;
            }
        }
        return true;
    }

    private boolean clickStack(short[] types, int[] counts, int[] durabilities,
            long[] masks, int[] stackMapIds, int[] stackShulkerIds,
            String[] stackBucketMobData, String[] stackItemComponentData,
            int slot, CraftButton button) {
        short targetType = types[slot];
        int targetCount = counts[slot];
        int targetDurability = durabilities[slot];
        long targetEnchantments = masks[slot];
        int targetMapId = stackMapIds[slot];
        int targetShulkerId = stackShulkerIds[slot];
        String targetBucketMobData = stackBucketMobData[slot];
        String targetItemComponentData = stackItemComponentData[slot];
        if (button == CraftButton.LEFT) {
            if (cursorType == EMPTY) {
                if (targetType == EMPTY) return false;
                setCursor(targetType, targetCount, targetDurability, targetEnchantments,
                        targetMapId, targetShulkerId, targetBucketMobData,
                        targetItemComponentData);
                clearComponentSlot(types, counts, durabilities, masks, stackMapIds,
                        stackShulkerIds, stackBucketMobData, stackItemComponentData, slot);
                return true;
            }
            if (targetType == EMPTY) {
                types[slot] = cursorType;
                counts[slot] = cursorCount;
                durabilities[slot] = cursorDurability;
                masks[slot] = cursorEnchantments;
                stackMapIds[slot] = cursorMapId;
                stackShulkerIds[slot] = cursorShulkerId;
                stackBucketMobData[slot] = cursorBucketMobData;
                stackItemComponentData[slot] = cursorItemComponentData;
                clearCursor();
                return true;
            }
            if (sameStack(cursorType, cursorDurability, cursorEnchantments, cursorMapId,
                    cursorShulkerId, targetType, targetDurability, targetEnchantments,
                    targetMapId, targetShulkerId)
                    && java.util.Objects.equals(cursorBucketMobData, targetBucketMobData)
                    && java.util.Objects.equals(cursorItemComponentData, targetItemComponentData)) {
                int moved = Math.min(stackMax(targetType) - targetCount, cursorCount);
                if (moved <= 0) return false;
                counts[slot] += moved;
                cursorCount -= moved;
                if (cursorCount == 0) clearCursor();
                return true;
            }
            types[slot] = cursorType;
            counts[slot] = cursorCount;
            durabilities[slot] = cursorDurability;
            masks[slot] = cursorEnchantments;
            stackMapIds[slot] = cursorMapId;
            stackShulkerIds[slot] = cursorShulkerId;
            stackBucketMobData[slot] = cursorBucketMobData;
            stackItemComponentData[slot] = cursorItemComponentData;
            setCursor(targetType, targetCount, targetDurability, targetEnchantments,
                    targetMapId, targetShulkerId, targetBucketMobData,
                    targetItemComponentData);
            return true;
        }

        if (cursorType == EMPTY) {
            if (targetType == EMPTY) return false;
            int picked = (targetCount + 1) / 2;
            setCursor(targetType, picked, targetDurability, targetEnchantments,
                    targetMapId, targetShulkerId, targetBucketMobData,
                    targetItemComponentData);
            counts[slot] -= picked;
            if (counts[slot] == 0) {
                clearComponentSlot(types, counts, durabilities, masks, stackMapIds,
                        stackShulkerIds, stackBucketMobData, stackItemComponentData, slot);
            }
            return true;
        }
        if (targetType == EMPTY) {
            types[slot] = cursorType;
            counts[slot] = 1;
            durabilities[slot] = cursorDurability;
            masks[slot] = cursorEnchantments;
            stackMapIds[slot] = cursorMapId;
            stackShulkerIds[slot] = cursorShulkerId;
            stackBucketMobData[slot] = cursorBucketMobData;
            stackItemComponentData[slot] = cursorItemComponentData;
            cursorCount--;
            if (cursorCount == 0) clearCursor();
            return true;
        }
        if (sameStack(cursorType, cursorDurability, cursorEnchantments, cursorMapId, cursorShulkerId,
                targetType, targetDurability, targetEnchantments, targetMapId, targetShulkerId)
                && java.util.Objects.equals(cursorBucketMobData, targetBucketMobData)
                && java.util.Objects.equals(cursorItemComponentData, targetItemComponentData)
                && targetCount < stackMax(targetType)) {
            counts[slot]++;
            cursorCount--;
            if (cursorCount == 0) clearCursor();
            return true;
        }
        types[slot] = cursorType;
        counts[slot] = cursorCount;
        durabilities[slot] = cursorDurability;
        masks[slot] = cursorEnchantments;
        stackMapIds[slot] = cursorMapId;
        stackShulkerIds[slot] = cursorShulkerId;
        stackBucketMobData[slot] = cursorBucketMobData;
        stackItemComponentData[slot] = cursorItemComponentData;
        setCursor(targetType, targetCount, targetDurability, targetEnchantments,
                targetMapId, targetShulkerId, targetBucketMobData, targetItemComponentData);
        return true;
    }

    private boolean canReceive(
            short[] types, int[] counts, int[] durabilities, long[] masks,
            int[] stackMapIds, int[] stackShulkerIds, String[] stackBucketMobData,
            String[] stackItemComponentData, int slot) {
        return types[slot] == EMPTY
                || sameStack(cursorType, cursorDurability, cursorEnchantments, cursorMapId,
                        cursorShulkerId, types[slot], durabilities[slot], masks[slot],
                        stackMapIds[slot], stackShulkerIds[slot])
                        && java.util.Objects.equals(cursorBucketMobData, stackBucketMobData[slot])
                        && java.util.Objects.equals(cursorItemComponentData,
                                stackItemComponentData[slot])
                        && counts[slot] < stackMax(cursorType);
    }

    private boolean collectFromArrays(
            short[] types, int[] counts, int[] durabilities, long[] masks,
            int[] stackMapIds, int[] stackShulkerIds, String[] stackBucketMobData,
            String[] stackItemComponentData, int end) {
        boolean changed = false;
        for (int slot = 0; slot < end && cursorCount < stackMax(cursorType); slot++) {
            if (!cursorMatches(types[slot], durabilities[slot], masks[slot], stackMapIds[slot],
                    stackShulkerIds[slot], stackBucketMobData[slot],
                    stackItemComponentData[slot])) continue;
            int moved = Math.min(stackMax(cursorType) - cursorCount, counts[slot]);
            cursorCount += moved;
            counts[slot] -= moved;
            if (counts[slot] == 0) {
                clearComponentSlot(types, counts, durabilities, masks, stackMapIds,
                        stackShulkerIds, stackBucketMobData, stackItemComponentData, slot);
            }
            changed = true;
        }
        return changed;
    }

    private boolean quickMoveGrid(int slot) {
        short type = craftingType[slot];
        if (type == EMPTY) return false;
        if (craftingBucketMobData[slot] != null || craftingItemComponentData[slot] != null) {
            short[] nextTypes = itemType.clone();
            int[] nextCounts = count.clone();
            int[] nextDurabilities = durability.clone();
            long[] nextEnchantments = enchantments.clone();
            int[] nextMapIds = mapIds.clone();
            int[] nextShulkerIds = shulkerIds.clone();
            String[] nextBucket = bucketMobData.clone();
            String[] nextComponents = itemComponentData.clone();
            boolean inserted = foldComponentStack(nextTypes, nextCounts, nextDurabilities,
                    nextEnchantments, nextMapIds, nextShulkerIds, nextBucket, nextComponents, type,
                    craftingCount[slot], craftingDurability[slot], craftingEnchantments[slot],
                    craftingMapIds[slot], craftingShulkerIds[slot], craftingBucketMobData[slot],
                    craftingItemComponentData[slot]);
            if (!inserted) return false;
            System.arraycopy(nextTypes, 0, itemType, 0, SLOTS);
            System.arraycopy(nextCounts, 0, count, 0, SLOTS);
            System.arraycopy(nextDurabilities, 0, durability, 0, SLOTS);
            System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
            System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
            System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
            System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
            System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
            clearCraftingSlot(slot);
            return true;
        }
        int inserted = addToArrays(itemType, count, durability, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData,
                type, craftingCount[slot], craftingDurability[slot],
                craftingEnchantments[slot], craftingMapIds[slot], craftingShulkerIds[slot],
                craftingBucketMobData[slot], craftingItemComponentData[slot], 0, SLOTS);
        if (inserted == 0) return false;
        craftingCount[slot] -= inserted;
        if (craftingCount[slot] == 0) clearCraftingSlot(slot);
        return true;
    }

    private boolean quickMoveInventory(int slot) {
        short type = itemType[slot];
        if (type == EMPTY) return false;
        int start = slot < HOTBAR_SLOTS ? HOTBAR_SLOTS : 0;
        int end = slot < HOTBAR_SLOTS ? SLOTS : HOTBAR_SLOTS;
        if (bucketMobData[slot] != null || itemComponentData[slot] != null) {
            short[] nextTypes = itemType.clone();
            int[] nextCounts = count.clone();
            int[] nextDurability = durability.clone();
            long[] nextEnchantments = enchantments.clone();
            int[] nextMapIds = mapIds.clone();
            int[] nextShulkerIds = shulkerIds.clone();
            String[] nextBucket = bucketMobData.clone();
            String[] nextComponents = itemComponentData.clone();
            if (!foldComponentStackRange(nextTypes, nextCounts, nextDurability,
                    nextEnchantments, nextMapIds, nextShulkerIds, nextBucket, nextComponents,
                    stackSnapshotAt(slot), start, end)) return false;
            clearComponentSlot(nextTypes, nextCounts, nextDurability, nextEnchantments,
                    nextMapIds, nextShulkerIds, nextBucket, nextComponents, slot);
            System.arraycopy(nextTypes, 0, itemType, 0, SLOTS);
            System.arraycopy(nextCounts, 0, count, 0, SLOTS);
            System.arraycopy(nextDurability, 0, durability, 0, SLOTS);
            System.arraycopy(nextEnchantments, 0, enchantments, 0, SLOTS);
            System.arraycopy(nextMapIds, 0, mapIds, 0, SLOTS);
            System.arraycopy(nextShulkerIds, 0, shulkerIds, 0, SLOTS);
            System.arraycopy(nextBucket, 0, bucketMobData, 0, SLOTS);
            System.arraycopy(nextComponents, 0, itemComponentData, 0, SLOTS);
            return true;
        }
        int inserted = addToArrays(itemType, count, durability, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData,
                type, count[slot], durability[slot], enchantments[slot], mapIds[slot],
                shulkerIds[slot], bucketMobData[slot], itemComponentData[slot], start, end);
        if (inserted == 0) return false;
        count[slot] -= inserted;
        if (count[slot] == 0) {
            clearComponentSlot(itemType, count, durability, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData, slot);
        }
        return true;
    }

    private StackSnapshot stackSnapshotAt(int slot) {
        return new StackSnapshot(itemType[slot], count[slot], durability[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                itemComponentData[slot]);
    }

    private static boolean foldComponentStackRange(short[] types, int[] counts,
            int[] durabilities, long[] enchantments, int[] mapIds, int[] shulkerIds,
            String[] bucketPayloads, String[] components, StackSnapshot stack,
            int start, int end) {
        int remaining = stack.count();
        for (int slot = start; slot < end && remaining > 0; slot++) {
            if (types[slot] != stack.itemType() || durabilities[slot] != stack.durability()
                    || enchantments[slot] != stack.enchantments() || mapIds[slot] != stack.mapId()
                    || shulkerIds[slot] != stack.shulkerId()
                    || !java.util.Objects.equals(bucketPayloads[slot], stack.bucketMobData())
                    || !java.util.Objects.equals(components[slot], stack.itemComponentData())) continue;
            int moved = Math.min(remaining, stackMax(stack.itemType()) - counts[slot]);
            counts[slot] += moved;
            remaining -= moved;
        }
        for (int slot = start; slot < end && remaining > 0; slot++) {
            if (types[slot] != EMPTY) continue;
            int moved = Math.min(remaining, stackMax(stack.itemType()));
            types[slot] = stack.itemType();
            counts[slot] = moved;
            durabilities[slot] = stack.durability();
            enchantments[slot] = stack.enchantments();
            mapIds[slot] = stack.mapId();
            shulkerIds[slot] = stack.shulkerId();
            bucketPayloads[slot] = stack.bucketMobData();
            components[slot] = stack.itemComponentData();
            remaining -= moved;
        }
        return remaining == 0;
    }

    private static void clearComponentSlot(short[] types, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketPayloads,
            String[] components, int slot) {
        clearSlot(types, counts, durabilities, enchantments, mapIds, shulkerIds, slot);
        bucketPayloads[slot] = null;
        components[slot] = null;
    }

    private static int addToArrays(short[] types, int[] counts, int[] durabilities, long[] masks,
            int[] stackMapIds, int[] stackShulkerIds, String[] stackBucketMobData,
            String[] stackItemComponentData, short type, int amount, int itemDurability,
            long itemEnchantments, int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData, int start, int end) {
        if (!isRegisteredItemType(type) || amount <= 0
                || !EnchantmentRules.isValidEnchantmentMaskForItem(type, itemEnchantments)
                || !isValidMapIdentity(type, itemMapId)
                || !isValidShulkerIdentity(type, itemShulkerId)
                || isDurable(type) && (itemDurability <= 0
                        || itemDurability > initialDurability(type))
                || !isDurable(type) && itemDurability != 0
                || !BucketMobPayloadCodec.validForItem(type, itemBucketMobData)) return 0;
        try { ItemComponentCodec.decode(type, itemComponentData); }
        catch (IllegalArgumentException invalid) { return 0; }
        int remaining = amount;
        if (!isDurable(type)) {
            int max = stackMax(type);
            for (int slot = start; slot < end && remaining > 0; slot++) {
                // 모든 identity column이 같을 때만 기존 스택에 합친다.
                if (types[slot] == type && counts[slot] < max
                        && masks[slot] == itemEnchantments && stackMapIds[slot] == itemMapId
                        && stackShulkerIds[slot] == itemShulkerId
                        && java.util.Objects.equals(stackBucketMobData[slot], itemBucketMobData)
                        && java.util.Objects.equals(stackItemComponentData[slot], itemComponentData)) {
                    int moved = Math.min(max - counts[slot], remaining);
                    counts[slot] += moved;
                    remaining -= moved;
                }
            }
        }
        for (int slot = start; slot < end && remaining > 0; slot++) {
            if (types[slot] != EMPTY) continue;
            int moved = Math.min(stackMax(type), remaining);
            types[slot] = type;
            counts[slot] = moved;
            durabilities[slot] = isDurable(type) ? itemDurability : 0;
            masks[slot] = itemEnchantments;
            stackMapIds[slot] = itemMapId;
            stackShulkerIds[slot] = itemShulkerId;
            stackBucketMobData[slot] = itemBucketMobData;
            stackItemComponentData[slot] = itemComponentData;
            remaining -= moved;
        }
        return amount - remaining;
    }

    /**
     * [SURV-X] 두 스택이 완전히 같은가. 인챈트 마스크가 다르면 서로 다른 스택이므로 합쳐지지 않는다.
     */
    private static boolean sameStack(
            short leftType, int leftDurability, long leftEnchantments, int leftMapId,
            short rightType, int rightDurability, long rightEnchantments, int rightMapId) {
        return sameStack(leftType, leftDurability, leftEnchantments, leftMapId, 0,
                rightType, rightDurability, rightEnchantments, rightMapId, 0);
    }

    /**
     * [SHULKER-CONTENTS] 27칸 참조까지 포함한 정체성 비교. 참조 ID 가 다르면 다른 스택이므로
     * 서로 다른 27칸을 든 두 상자가 절대 합쳐지지 않는다.
     */
    private static boolean sameStack(
            short leftType, int leftDurability, long leftEnchantments, int leftMapId, int leftShulkerId,
            short rightType, int rightDurability, long rightEnchantments, int rightMapId,
            int rightShulkerId) {
        return leftType == rightType && leftDurability == rightDurability
                && leftEnchantments == rightEnchantments && leftMapId == rightMapId
                && leftShulkerId == rightShulkerId;
    }

    private void setCursor(short type, int amount, int itemDurability, long itemEnchantments) {
        setCursor(type, amount, itemDurability, itemEnchantments, 0, 0);
    }

    private void setCursor(
            short type, int amount, int itemDurability, long itemEnchantments, int itemMapId) {
        setCursor(type, amount, itemDurability, itemEnchantments, itemMapId, 0);
    }

    private void setCursor(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId) {
        setCursor(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId, null);
    }

    private void setCursor(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData) {
        setCursor(type, amount, itemDurability, itemEnchantments, itemMapId, itemShulkerId,
                itemBucketMobData, null);
    }

    private void setCursor(short type, int amount, int itemDurability, long itemEnchantments,
            int itemMapId, int itemShulkerId, String itemBucketMobData,
            String itemComponentData) {
        if (!isValidMapIdentity(type, itemMapId)) {
            throw new IllegalArgumentException("invalid cursor map identity");
        }
        if (!isValidShulkerIdentity(type, itemShulkerId)) {
            throw new IllegalArgumentException("invalid cursor shulker identity");
        }
        if (!BucketMobPayloadCodec.validForItem(type, itemBucketMobData)) {
            throw new IllegalArgumentException("invalid cursor bucket mob payload");
        }
        ItemComponentCodec.decode(type, itemComponentData);
        cursorType = type;
        cursorCount = amount;
        cursorDurability = itemDurability;
        cursorEnchantments = itemEnchantments;
        cursorMapId = itemMapId;
        cursorShulkerId = itemShulkerId;
        cursorBucketMobData = itemBucketMobData;
        cursorItemComponentData = itemComponentData;
    }

    private void clearCursor() {
        cursorType = EMPTY;
        cursorCount = 0;
        cursorDurability = 0;
        cursorEnchantments = EnchantmentRules.EMPTY_ENCHANTMENTS;
        cursorMapId = 0;
        cursorShulkerId = 0;
        cursorBucketMobData = null;
        cursorItemComponentData = null;
    }

    private StackSnapshot cursorStackSnapshot() {
        return new StackSnapshot(cursorType, cursorCount, cursorDurability,
                cursorEnchantments, cursorMapId, cursorShulkerId, cursorBucketMobData,
                cursorItemComponentData);
    }

    private void clearCraftingSlot(int slot) {
        clearComponentSlot(craftingType, craftingCount, craftingDurability,
                craftingEnchantments, craftingMapIds, craftingShulkerIds,
                craftingBucketMobData, craftingItemComponentData, slot);
    }

    /**
     * [COOKING] 제작 격자 한 칸을 통째로 갈아 끼운다. crafting remainder(우유 양동이 →
     * 빈 양동이)가 유일한 호출부라 내구·인챈트·지도 정체성은 전부 초기 상태로 되돌린다.
     */
    private void setCraftingSlot(int slot, short type, int amount) {
        craftingType[slot] = type;
        craftingCount[slot] = amount;
        craftingDurability[slot] = initialDurability(type);
        craftingEnchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
        craftingMapIds[slot] = 0;
        craftingShulkerIds[slot] = 0;
        craftingBucketMobData[slot] = null;
        craftingItemComponentData[slot] = null;
    }

    private static void clearSlot(
            short[] types, int[] counts, int[] durabilities, long[] masks,
            int[] stackMapIds, int[] stackShulkerIds, int slot) {
        types[slot] = EMPTY;
        counts[slot] = 0;
        durabilities[slot] = 0;
        masks[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
        stackMapIds[slot] = 0;
        stackShulkerIds[slot] = 0;
    }

    private static boolean give(
            short[] t, int[] c, int[] d, long[] e, int[] m, int[] s,
            String[] b, String[] components, short type, int amount) {
        return addToArrays(t, c, d, e, m, s, b, components, type, amount,
                initialDurability(type), EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0,
                null, null, 0, SLOTS) == amount;
    }

    private static int addRecipePoolEntry(
            short[] types, int[] counts, StackSnapshot[] stacks, int size,
            StackSnapshot stack) {
        if (stack == null || stack.isEmpty()) return size;
        for (int index = 0; index < size; index++) {
            if (stack(stacks[index], stack)) {
                counts[index] += stack.count();
                return size;
            }
        }
        types[size] = stack.itemType();
        counts[size] = stack.count();
        stacks[size] = stack;
        return size + 1;
    }

    private static RecipePlacementPlan recipePlacement(
            CraftRecipe recipe, short[] expected, short[] poolTypes,
            int[] poolCounts, StackSnapshot[] poolStacks, int poolSize, int crafts) {
        int[] remaining = poolCounts.clone();
        StackSnapshot[] placed = new StackSnapshot[expected.length];
        for (int slot = 0; slot < expected.length; slot++) {
            short required = expected[slot];
            if (required == EMPTY) continue;
            int selected = -1;
            for (int candidate = 0; candidate < poolSize; candidate++) {
                short actual = poolTypes[candidate];
                if (remaining[candidate] < crafts || stackMax(actual) < crafts
                        || !recipe.acceptsItem(required, actual)) {
                    continue;
                }
                if (selected < 0
                        || remaining[candidate] > remaining[selected]
                        || remaining[candidate] == remaining[selected]
                                && Short.toUnsignedInt(actual)
                                        < Short.toUnsignedInt(poolTypes[selected])) {
                    selected = candidate;
                }
            }
            if (selected < 0) return null;
            StackSnapshot source = poolStacks[selected];
            placed[slot] = new StackSnapshot(source.itemType(), crafts, source.durability(),
                    source.enchantments(), source.mapId(), source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData());
            remaining[selected] -= crafts;
        }
        return new RecipePlacementPlan(placed, crafts);
    }

    private static int consumeExact(
            short[] types, int[] counts, int[] durabilities, long[] masks, int[] stackMapIds,
            int[] stackShulkerIds, String[] stackBucketMobData, String[] stackItemComponentData,
            int end, short type, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < end && remaining > 0; slot++) {
            if (types[slot] != type) continue;
            int consumed = Math.min(counts[slot], remaining);
            counts[slot] -= consumed;
            remaining -= consumed;
            if (counts[slot] == 0) {
                clearComponentSlot(types, counts, durabilities, masks, stackMapIds,
                        stackShulkerIds, stackBucketMobData, stackItemComponentData, slot);
            }
        }
        return amount - remaining;
    }

    private static int consumeExact(
            short[] types, int[] counts, int[] durabilities, long[] masks, int[] stackMapIds,
            int[] stackShulkerIds, String[] stackBucketMobData, String[] stackItemComponentData,
            int end, StackSnapshot expected, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < end && remaining > 0; slot++) {
            if (!sameStack(types[slot], durabilities[slot], masks[slot], stackMapIds[slot],
                    stackShulkerIds[slot], expected.itemType(), expected.durability(),
                    expected.enchantments(), expected.mapId(), expected.shulkerId())
                    || !java.util.Objects.equals(stackBucketMobData[slot], expected.bucketMobData())
                    || !java.util.Objects.equals(
                            stackItemComponentData[slot], expected.itemComponentData())) continue;
            int consumed = Math.min(counts[slot], remaining);
            counts[slot] -= consumed;
            remaining -= consumed;
            if (counts[slot] == 0) {
                clearComponentSlot(types, counts, durabilities, masks, stackMapIds,
                        stackShulkerIds, stackBucketMobData, stackItemComponentData, slot);
            }
        }
        return amount - remaining;
    }

    private static final class RecipePlacementPlan {
        private final StackSnapshot[] stacks;
        private final int count;

        private RecipePlacementPlan(StackSnapshot[] stacks, int count) {
            this.stacks = stacks;
            this.count = count;
        }
    }

    private InventoryArrays currentNormalizedInventory() {
        return normalizedInventory(null, -1);
    }

    /** 교환 뒤의 보이는 36칸을 실제로 바꾸지 않고 격자·커서까지 접어 봅니다. */
    private InventoryArrays normalizedInventoryAfterOffhandSwap(int slot) {
        return normalizedInventory(offhand, slot);
    }

    private InventoryArrays normalizedInventory(StackSnapshot replacement, int replacementSlot) {
        short[] types = itemType.clone();
        int[] counts = count.clone();
        int[] durabilities = durability.clone();
        long[] masks = enchantments.clone();
        int[] copiedMapIds = mapIds.clone();
        int[] copiedShulkerIds = shulkerIds.clone();
        String[] copiedBucketMobData = bucketMobData.clone();
        String[] copiedItemComponentData = itemComponentData.clone();
        if (replacement != null) {
            types[replacementSlot] = replacement.itemType();
            counts[replacementSlot] = replacement.count();
            durabilities[replacementSlot] = replacement.durability();
            masks[replacementSlot] = replacement.enchantments();
            copiedMapIds[replacementSlot] = replacement.mapId();
            copiedShulkerIds[replacementSlot] = replacement.shulkerId();
            copiedBucketMobData[replacementSlot] = replacement.bucketMobData();
            copiedItemComponentData[replacementSlot] = replacement.itemComponentData();
        }
        return normalizedInventory(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, copiedBucketMobData, copiedItemComponentData);
    }

    private InventoryArrays normalizedInventory(short[] types, int[] counts, int[] durabilities,
            long[] masks, int[] copiedMapIds, int[] copiedShulkerIds) {
        return normalizedInventory(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, bucketMobData, itemComponentData);
    }

    private InventoryArrays normalizedInventory(short[] types, int[] counts, int[] durabilities,
            long[] masks, int[] copiedMapIds, int[] copiedShulkerIds,
            String[] copiedBucketMobData, String[] copiedItemComponentData) {
        types = types.clone();
        counts = counts.clone();
        durabilities = durabilities.clone();
        masks = masks.clone();
        copiedMapIds = copiedMapIds.clone();
        copiedShulkerIds = copiedShulkerIds.clone();
        copiedBucketMobData = copiedBucketMobData.clone();
        copiedItemComponentData = copiedItemComponentData.clone();
        for (int slot = 0; slot < SLOTS; slot++) {
            try {
                new StackSnapshot(types[slot], counts[slot], durabilities[slot], masks[slot],
                        copiedMapIds[slot], copiedShulkerIds[slot], copiedBucketMobData[slot],
                        copiedItemComponentData[slot]);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        }
        if (cursorType != EMPTY && addToArrays(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, copiedBucketMobData, copiedItemComponentData,
                cursorType, cursorCount, cursorDurability, cursorEnchantments,
                cursorMapId, cursorShulkerId, cursorBucketMobData, cursorItemComponentData,
                0, SLOTS) != cursorCount) {
            return null;
        }
        int cells = craftingSlotCount;
        for (int slot = 0; slot < cells; slot++) {
            if (craftingType[slot] != EMPTY && addToArrays(
                    types, counts, durabilities, masks, copiedMapIds, copiedShulkerIds,
                    copiedBucketMobData, copiedItemComponentData,
                    craftingType[slot], craftingCount[slot], craftingDurability[slot],
                    craftingEnchantments[slot], craftingMapIds[slot], craftingShulkerIds[slot],
                    craftingBucketMobData[slot], craftingItemComponentData[slot],
                    0, SLOTS)
                    != craftingCount[slot]) {
                return null;
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            try {
                new StackSnapshot(types[slot], counts[slot], durabilities[slot], masks[slot],
                        copiedMapIds[slot], copiedShulkerIds[slot], copiedBucketMobData[slot],
                        copiedItemComponentData[slot]);
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        }
        if (!merchantMutationInProgress) {
            for (StackSnapshot payment : merchantPaymentSnapshot()) {
                if (payment.isEmpty()) continue;
                if (addToArrays(types, counts, durabilities, masks, copiedMapIds, copiedShulkerIds,
                        copiedBucketMobData, copiedItemComponentData, payment.itemType(), payment.count(),
                        payment.durability(), payment.enchantments(), payment.mapId(), payment.shulkerId(),
                        payment.bucketMobData(), payment.itemComponentData(), 0, SLOTS) != payment.count()) return null;
            }
        }
        InventoryArrays normalized = new InventoryArrays(types, counts, durabilities, masks, copiedMapIds,
                copiedShulkerIds, copiedBucketMobData, copiedItemComponentData);
        normalized.merchantPaymentsFolded = !merchantMutationInProgress;
        return normalized;
    }

    private static boolean foldComponentStack(short[] types, int[] counts, int[] durabilities,
            long[] masks, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData, short type, int amount, int durability,
            long enchantments, int mapId, int shulkerId, String bucketPayload,
            String components) {
        if (!isRegisteredItemType(type) || amount <= 0
                || !EnchantmentRules.isValidEnchantmentMaskForItem(type, enchantments)
                || isDurable(type) && (durability <= 0
                        || durability > initialDurability(type))
                || !isDurable(type) && durability != 0
                || !isValidMapIdentity(type, mapId)
                || !isValidShulkerIdentity(type, shulkerId)
                || !BucketMobPayloadCodec.validForItem(type, bucketPayload)) return false;
        try { ItemComponentCodec.decode(type, components); }
        catch (IllegalArgumentException invalid) { return false; }
        int remaining = amount;
        for (int slot = 0; slot < types.length && remaining > 0; slot++) {
            if (types[slot] != type || durabilities[slot] != durability
                    || masks[slot] != enchantments || mapIds[slot] != mapId
                    || shulkerIds[slot] != shulkerId
                    || !java.util.Objects.equals(bucketMobData[slot], bucketPayload)
                    || !java.util.Objects.equals(itemComponentData[slot], components)) continue;
            int moved = Math.min(remaining, stackMax(type) - counts[slot]);
            counts[slot] += moved;
            remaining -= moved;
        }
        for (int slot = 0; slot < types.length && remaining > 0; slot++) {
            if (types[slot] != EMPTY) continue;
            int moved = Math.min(remaining, stackMax(type));
            types[slot] = type;
            counts[slot] = moved;
            durabilities[slot] = durability;
            masks[slot] = enchantments;
            mapIds[slot] = mapId;
            shulkerIds[slot] = shulkerId;
            bucketMobData[slot] = bucketPayload;
            itemComponentData[slot] = components;
            remaining -= moved;
        }
        return remaining == 0;
    }

    private static boolean foldBucketComponent(short[] types, int[] counts, int[] durabilities,
            long[] masks, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            short type, int count, int durability, long enchantments, int mapId, int shulkerId,
            String payload) {
        if (count != 1 || !isRegisteredItemType(type)
                || !EnchantmentRules.isValidEnchantmentMaskForItem(type, enchantments)
                || !BucketMobPayloadCodec.validForItem(type, payload)) return false;
        for (int slot = 0; slot < types.length; slot++) {
            if (types[slot] != EMPTY) continue;
            types[slot] = type;
            counts[slot] = 1;
            durabilities[slot] = durability;
            masks[slot] = enchantments;
            mapIds[slot] = mapId;
            shulkerIds[slot] = shulkerId;
            bucketMobData[slot] = payload;
            return true;
        }
        return false;
    }

    private void requireLosslessTransientFold() {
        if (currentNormalizedInventory() == null) {
            throw new IllegalStateException("inventory mutation made transient state unpersistable");
        }
    }

    private static final class InventoryArrays {
        private boolean merchantPaymentsFolded;
        private final short[] types;
        private final int[] counts;
        private final int[] durabilities;
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;

        private InventoryArrays(
                short[] types, int[] counts, int[] durabilities, long[] enchantments,
                int[] mapIds, int[] shulkerIds, String[] bucketMobData,
                String[] itemComponentData) {
            this.types = types;
            this.counts = counts;
            this.durabilities = durabilities;
            this.enchantments = enchantments;
            this.mapIds = mapIds;
            this.shulkerIds = shulkerIds;
            this.bucketMobData = bucketMobData;
            this.itemComponentData = itemComponentData;
        }
    }

    private void commitInventoryArrays(InventoryArrays source) {
        preflightRevisionCapacity();
        if (source.merchantPaymentsFolded) restoreMerchantPayments(new StackSnapshot[]{StackSnapshot.EMPTY, StackSnapshot.EMPTY});
        System.arraycopy(source.types, 0, itemType, 0, SLOTS);
        System.arraycopy(source.counts, 0, count, 0, SLOTS);
        System.arraycopy(source.durabilities, 0, durability, 0, SLOTS);
        System.arraycopy(source.enchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(source.mapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(source.shulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(source.bucketMobData, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(source.itemComponentData, 0, itemComponentData, 0, SLOTS);
    }

    // ── 스냅샷(저장/welcome 용) ──
    public synchronized CompletePersistenceSnapshot completePersistenceSnapshot() {
        InventoryArrays normalized = currentNormalizedInventory();
        if (normalized == null) {
            throw new IllegalStateException("inventory has no lossless persistence snapshot");
        }
        return new CompletePersistenceSnapshot(this, normalized);
    }

    private long nextSettlementLeaseNonce() {
        if (nextSettlementLeaseNonce == Long.MAX_VALUE) {
            throw new IllegalStateException("settlement lease nonce space is exhausted");
        }
        return ++nextSettlementLeaseNonce;
    }

    private boolean settlementLeaseMatches(long sourceRevision, long leaseNonce,
            String sourceSnapshotDigest) {
        return leaseNonce != 0 && sourceSnapshotDigest != null
                && settlementLeaseRevision == sourceRevision
                && settlementLeaseNonce == leaseNonce
                && java.util.Objects.equals(settlementLeaseSourceDigest, sourceSnapshotDigest)
                && revision == sourceRevision
                && currentSettlementSourceDigestMatches();
    }

    private boolean currentSettlementSourceDigestMatches() {
        if (settlementLeaseSourceDigest == null) return false;
        InventoryArrays normalized = currentNormalizedInventory();
        if (normalized == null) return false;
        CompletePersistenceSnapshot current = new CompletePersistenceSnapshot(this, normalized);
        return settlementLeaseSourceDigest.equals(current.snapshotDigest());
    }

    private void clearSettlementLease() {
        settlementLeaseRevision = -1L;
        settlementLeaseNonce = 0L;
        settlementLeaseSourceDigest = null;
    }

    /** 현재 완전 상태를 포착하고 비동기 정산이 끝날 때까지 일반 변이를 잠급니다. */
    public synchronized CompletePersistenceSnapshot acquireSettlementLease() {
        if (settlementLeaseRevision >= 0) return null;
        InventoryArrays normalized = currentNormalizedInventory();
        if (normalized == null) {
            throw new IllegalStateException("inventory has no lossless persistence snapshot");
        }
        long nonce = nextSettlementLeaseNonce();
        CompletePersistenceSnapshot snapshot = new CompletePersistenceSnapshot(this, normalized, nonce);
        settlementLeaseRevision = snapshot.revision();
        settlementLeaseNonce = nonce;
        settlementLeaseSourceDigest = snapshot.snapshotDigest();
        return snapshot;
    }

    public synchronized boolean settlementLeased() {
        return settlementLeaseRevision >= 0;
    }

    /** Releases only the exact immutable lease capability returned by acquireSettlementLease(). */
    public synchronized boolean releaseSettlementLease(CompletePersistenceSnapshot source) {
        if (source == null || !settlementLeaseMatches(source.revision(), source.leaseNonce(),
                source.snapshotDigest())) return false;
        clearSettlementLease();
        return true;
    }

    /** Cancellation is a capability operation; an old callback cannot clear its successor. */
    public synchronized boolean cancelSettlementLease(CompletePersistenceSnapshot source) {
        return releaseSettlementLease(source);
    }

    /** Installs a result only when its opaque lease nonce and source lineage both match. */
    public synchronized boolean installCommittedSettlement(
            CompletePersistenceSnapshot source, CompletePersistenceSnapshot committed) {
        if (source == null || committed == null
                || !settlementLeaseMatches(source.revision(), source.leaseNonce(),
                        source.snapshotDigest())
                || committed.revision() <= source.revision()
                || !java.util.Objects.equals(committed.sourceLineageDigest(),
                        settlementLeaseSourceDigest)) return false;
        installCompleteSnapshot(committed);
        persistenceLineageDigest = null;
        clearSettlementLease();
        return true;
    }

    /** Nested transforms and rollbacks retain this inventory's detached source lineage. */
    private void installCompleteSnapshot(CompletePersistenceSnapshot source) {
        if (handMutationNonce == Long.MAX_VALUE) {
            throw new IllegalStateException("hand capability nonce space is exhausted");
        }
        restoreMerchantPayments(source.merchantPayments);
        System.arraycopy(source.itemTypes, 0, itemType, 0, SLOTS);
        System.arraycopy(source.counts, 0, count, 0, SLOTS);
        System.arraycopy(source.durabilities, 0, durability, 0, SLOTS);
        System.arraycopy(source.enchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(source.mapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(source.shulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(source.bucketMobData, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(source.itemComponentData, 0, itemComponentData, 0, SLOTS);
        System.arraycopy(source.equippedTypes, 0, equippedType, 0, equippedType.length);
        System.arraycopy(source.equippedDurabilities, 0, equippedDurability, 0,
                equippedDurability.length);
        System.arraycopy(source.equippedEnchantments, 0, equippedEnchantments, 0,
                equippedEnchantments.length);
        System.arraycopy(source.equippedItemComponentData, 0, equippedItemComponentData, 0,
                equippedItemComponentData.length);
        System.arraycopy(source.craftingTypes, 0, craftingType, 0, craftingType.length);
        System.arraycopy(source.craftingCounts, 0, craftingCount, 0, craftingCount.length);
        System.arraycopy(source.craftingDurabilities, 0, craftingDurability, 0,
                craftingDurability.length);
        System.arraycopy(source.craftingEnchantments, 0, craftingEnchantments, 0,
                craftingEnchantments.length);
        System.arraycopy(source.craftingMapIds, 0, craftingMapIds, 0, craftingMapIds.length);
        System.arraycopy(source.craftingShulkerIds, 0, craftingShulkerIds, 0,
                craftingShulkerIds.length);
        System.arraycopy(source.craftingBucketMobData, 0, craftingBucketMobData, 0,
                craftingBucketMobData.length);
        System.arraycopy(source.craftingItemComponentData, 0, craftingItemComponentData, 0,
                craftingItemComponentData.length);
        offhand = source.offhand;
        selectedSlot = source.selectedSlot;
        cursorType = source.cursorType;
        cursorCount = source.cursorCount;
        cursorDurability = source.cursorDurability;
        cursorEnchantments = source.cursorEnchantments;
        cursorMapId = source.cursorMapId;
        cursorShulkerId = source.cursorShulkerId;
        cursorBucketMobData = source.cursorBucketMobData;
        cursorItemComponentData = source.cursorItemComponentData;
        craftingGridSize = source.craftingGridSize;
        craftingSlotCount = source.craftingSlotCount;
        craftingStonecutter = source.craftingStonecutter;
        craftingSelection = source.craftingSelection;
        revision = source.revision;
        handMutationNonce++;
        persistenceRevisionBound = true;
        persistenceLifecycle = source.revision == Long.MAX_VALUE
                ? PersistenceLifecycle.TERMINAL : PersistenceLifecycle.BASELINE_BOUND;
        clearStationPlan();
    }

    public synchronized PersistenceSnapshot persistenceSnapshot() {
        InventoryArrays current = currentNormalizedInventory();
        if (current == null) {
            throw new IllegalStateException("crafting state has no lossless persistence baseline");
        }
        return new PersistenceSnapshot(current, offhand, revision);
    }

    public synchronized short[] equippedTypes() {
        return equippedType.clone();
    }

    public synchronized int[] equippedDurabilities() {
        return equippedDurability.clone();
    }

    /** [SURV-X] 착용 4칸의 인챈트 마스크 스냅샷. */
    public synchronized long[] equippedEnchantments() {
        return equippedEnchantments.clone();
    }

    public synchronized String[] equippedItemComponentData() {
        return equippedItemComponentData.clone();
    }
}
