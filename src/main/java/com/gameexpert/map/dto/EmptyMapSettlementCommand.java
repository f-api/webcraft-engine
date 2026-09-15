package com.gameexpert.map.dto;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationKind;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationRequest;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationReservation;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/** 빈 지도 한 개의 정확한 lease 변이와 내구성 map-ID 예약을 묶는 terminal 명령입니다. */
@Getter
public final class EmptyMapSettlementCommand {
    private final AllocationReservation reservation;
    private final PlayerInventory.CompletePersistenceSnapshot sourceInventory;
    private final PlayerInventory.HandRef sourceHand;
    private final PlayerInventory.CompletePersistenceSnapshot committedInventory;
    private final PlayerInventoryMutationSnapshot player;
    private final int centerX;
    private final int centerZ;
    private final byte[] initialColors;
    private final List<GroundItemSnapshot> overflow;
    private final GroundMutationCommand groundMutation;
    private final String fingerprint;

    /** 새 사용의 전체 frozen 입력으로 ID를 만들며 기존 명시적 ID의 재생 형식은 유지합니다. */
    public static AllocationRequest allocationRequestForNewUse(Long playerId, Long worldId,
            PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            PlayerInventory.HandRef sourceHand, int centerX, int centerZ, byte[] initialColors) {
        AllocationRequest identity = allocationRequest(1L, playerId, worldId,
                sourceInventory, sourceHand, centerX, centerZ, initialColors);
        String digest = sha256("empty-map-new-use-identity-v1|" + identity.fingerprint());
        long settlementId = (Long.parseUnsignedLong(digest.substring(0, 16), 16)
                & 0x1fff_ffff_ffff_ffffL) | 0x2000_0000_0000_0000L;
        return new AllocationRequest(settlementId, identity.worldId(), identity.playerId(),
                identity.expectedInventoryRevision(), identity.inventoryLeaseNonce(),
                identity.sourceInventoryDigest(), identity.kind(), identity.retainedMapId(),
                identity.actionFingerprint());
    }

    public static AllocationRequest allocationRequest(long settlementId, Long playerId,
            Long worldId, PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            PlayerInventory.HandRef sourceHand, int centerX, int centerZ, byte[] initialColors) {
        requireLeasedSource(sourceInventory);
        requireSourceHand(sourceInventory, sourceHand);
        if (playerId == null || playerId <= 0 || worldId == null || worldId <= 0
                || Math.floorMod(centerX, WorldMapData.SIZE) != 0
                || Math.floorMod(centerZ, WorldMapData.SIZE) != 0
                || initialColors == null || initialColors.length != WorldMapData.COLOR_COUNT) {
            throw new IllegalArgumentException("complete empty-map allocation input is required");
        }
        String action = sha256("empty-map-action-v2|" + sourceHand.hand() + "|"
                + sourceHand.mainSlot() + "|" + sourceHand.revision() + "|"
                + sourceHand.mutationNonce() + "|" + stackCanonical(sourceHand.capturedStack())
                + "|" + centerX + "|" + centerZ + "|" + sha256(initialColors));
        return new AllocationRequest(settlementId, worldId, playerId,
                sourceInventory.revision(), sourceInventory.leaseNonce(),
                sourceInventory.snapshotDigest(), AllocationKind.EMPTY_MAP, 0, action);
    }

    public EmptyMapSettlementCommand(AllocationReservation reservation,
            PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            PlayerInventory.HandRef sourceHand,
            PlayerInventory.CompletePersistenceSnapshot committedInventory,
            PlayerInventoryMutationSnapshot player,
            int centerX, int centerZ, byte[] initialColors,
            List<GroundItemSnapshot> overflow, GroundMutationCommand groundMutation) {
        if (reservation == null || sourceInventory == null || sourceHand == null
                || committedInventory == null || player == null || overflow == null) {
            throw new IllegalArgumentException("complete empty-map settlement is required");
        }
        AllocationRequest expectedRequest = allocationRequest(
                reservation.request().settlementId(), player.playerId(), player.worldId(),
                sourceInventory, sourceHand, centerX, centerZ, initialColors);
        if (!reservation.request().equals(expectedRequest)) {
            throw new IllegalArgumentException("empty-map allocation reservation does not match");
        }
        requireMutationCapability(reservation, sourceInventory, committedInventory, player);

        PlayerInventory expected = sourceInventory.detachedInventory();
        PlayerInventory.MapUseResult expectedResult = expected.completeMapUse(
                sourceHand.hand(), reservation.mapId());
        if (!expectedResult.completed()) {
            throw new IllegalArgumentException("empty-map input capability cannot produce a result");
        }
        PlayerInventory.CompletePersistenceSnapshot expectedCommitted =
                expected.completePersistenceSnapshot();
        if (!sameCompleteState(expectedCommitted, committedInventory)) {
            throw new IllegalArgumentException("empty-map post-state is not the authorized mutation");
        }
        requireOverflow(expectedResult.overflow(), player.worldId(), player.fingerprint(),
                sourceInventory.revision(), overflow, groundMutation);

        this.reservation = reservation;
        this.sourceInventory = sourceInventory;
        this.sourceHand = sourceHand;
        this.committedInventory = committedInventory;
        this.player = player;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.initialColors = initialColors.clone();
        this.overflow = List.copyOf(overflow);
        this.groundMutation = groundMutation;
        this.fingerprint = sha256("empty-map-terminal-v2|" + reservation.fingerprint() + "|"
                + committedInventory.snapshotDigest() + "|" + player.fingerprint() + "|"
                + this.overflow + "|"
                + (groundMutation == null ? "-" : groundMutation.fingerprint()));
    }

    public long getSettlementId() {
        return reservation.request().settlementId();
    }

    public long getExpectedPlayerRevision() {
        return reservation.request().expectedInventoryRevision();
    }

    public int getMapId() {
        return reservation.mapId();
    }

    public byte[] getInitialColors() {
        return initialColors.clone();
    }

    private static void requireOverflow(PlayerInventory.DroppedStack expected, Long worldId,
            String playerFingerprint, long expectedPlayerRevision,
            List<GroundItemSnapshot> overflow, GroundMutationCommand groundMutation) {
        if (expected == null) {
            if (!overflow.isEmpty() || groundMutation != null) {
                throw new IllegalArgumentException("empty-map settlement has unexpected overflow");
            }
            return;
        }
        if (overflow.size() != 1 || !sameStack(expected, overflow.getFirst())) {
            throw new IllegalArgumentException("empty-map overflow stack is not exact");
        }
        if (groundMutation == null || groundMutation.kind() != GroundMutationCommand.Kind.BLOCK_DROP
                || !worldId.equals(groundMutation.worldId())
                || groundMutation.expectedPlayerRevision() != null
                || groundMutation.committedPlayer() != null
                || !groundMutation.insertedItems().equals(overflow)
                || !groundMutation.removedItemIds().isEmpty()
                || !groundMutation.insertedXpOrbs().isEmpty()
                || !groundMutation.removedXpOrbIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "map overflow must use the player-free ground insertion primitive");
        }
        // Keep these values in the terminal fingerprint even though ground owns no player write.
        if (playerFingerprint == null || expectedPlayerRevision < 0) {
            throw new IllegalArgumentException("empty-map player authority is incomplete");
        }
    }

    static void requireMutationCapability(AllocationReservation reservation,
            PlayerInventory.CompletePersistenceSnapshot source,
            PlayerInventory.CompletePersistenceSnapshot committed,
            PlayerInventoryMutationSnapshot player) {
        requireLeasedSource(source);
        if (reservation.request().expectedInventoryRevision() != source.revision()
                || reservation.request().inventoryLeaseNonce() != source.leaseNonce()
                || !reservation.request().sourceInventoryDigest().equals(source.snapshotDigest())
                || committed.leaseNonce() != 0
                || committed.revision() != Math.addExact(source.revision(), 1)
                || !source.snapshotDigest().equals(committed.sourceLineageDigest())
                || !reservation.request().playerId().equals(player.playerId())
                || !reservation.request().worldId().equals(player.worldId())
                || player.revision() != committed.revision()) {
            throw new IllegalArgumentException("inventory mutation capability is stale or forged");
        }
        requirePlayerSnapshotMatches(committed, player);
    }

    static void requireLeasedSource(PlayerInventory.CompletePersistenceSnapshot source) {
        if (source == null || source.leaseNonce() <= 0 || source.leaseNonce() == Long.MAX_VALUE
                || source.revision() < 0 || source.revision() >= Long.MAX_VALUE - 1
                || !isSha256(source.snapshotDigest())
                || !source.snapshotDigest().equals(source.sourceLineageDigest())) {
            throw new IllegalArgumentException("exact inventory settlement lease is required");
        }
    }

    private static void requireSourceHand(PlayerInventory.CompletePersistenceSnapshot source,
            PlayerInventory.HandRef hand) {
        if (hand == null || hand.revision() != source.revision()
                || hand.capturedStack().itemType() != PlayerInventory.MAP
                || hand.capturedStack().count() <= 0) {
            throw new IllegalArgumentException("exact empty-map input hand is required");
        }
        PlayerInventory.StackSnapshot captured;
        if (hand.hand() == PlayerInventory.Hand.OFFHAND) {
            if (hand.mainSlot() != -1) {
                throw new IllegalArgumentException("offhand capability has a main-hand slot");
            }
            captured = source.offhand();
        } else {
            int slot = hand.mainSlot();
            if (slot < 0 || slot >= PlayerInventory.SLOTS || slot != source.selectedSlot()) {
                throw new IllegalArgumentException("main-hand capability slot is stale");
            }
            captured = stack(source, slot);
        }
        if (!captured.equals(hand.capturedStack())) {
            throw new IllegalArgumentException("empty-map hand capability does not match its lease");
        }
    }

    static void requirePlayerSnapshotMatches(
            PlayerInventory.CompletePersistenceSnapshot complete,
            PlayerInventoryMutationSnapshot player) {
        PlayerInventory.PersistenceSnapshot persisted = complete.persistenceSnapshot();
        if (player.revision() != complete.revision()
                || !Arrays.equals(player.itemTypes(), persisted.itemTypes())
                || !Arrays.equals(player.counts(), persisted.counts())
                || !Arrays.equals(player.durabilities(), persisted.durabilities())
                || !Arrays.equals(player.enchantments(), persisted.enchantments())
                || !Arrays.equals(player.mapIds(), persisted.mapIds())
                || !Arrays.equals(player.shulkerIds(), persisted.shulkerIds())
                || !Arrays.equals(player.bucketMobData(), persisted.bucketMobData())
                || !Arrays.equals(player.itemComponentData(), persisted.itemComponentData())
                || !Arrays.equals(player.equippedTypes(), complete.equippedTypes())
                || !Arrays.equals(player.equippedDurabilities(), complete.equippedDurabilities())
                || !Arrays.equals(player.equippedEnchantments(), complete.equippedEnchantments())
                || !Arrays.equals(player.equippedItemComponentData(),
                        complete.equippedItemComponentData())
                || !player.offhand().equals(complete.offhand())
                || player.selectedSlot() != complete.selectedSlot()) {
            throw new IllegalArgumentException("committed player does not match complete inventory");
        }
    }

    static boolean sameCompleteState(PlayerInventory.CompletePersistenceSnapshot left,
            PlayerInventory.CompletePersistenceSnapshot right) {
        if (left.revision() != right.revision() || left.selectedSlot() != right.selectedSlot()
                || !Arrays.equals(left.itemTypes(), right.itemTypes())
                || !Arrays.equals(left.counts(), right.counts())
                || !Arrays.equals(left.durabilities(), right.durabilities())
                || !Arrays.equals(left.enchantments(), right.enchantments())
                || !Arrays.equals(left.mapIds(), right.mapIds())
                || !Arrays.equals(left.shulkerIds(), right.shulkerIds())
                || !Arrays.equals(left.bucketMobData(), right.bucketMobData())
                || !Arrays.equals(left.itemComponentData(), right.itemComponentData())
                || !Arrays.equals(left.equippedTypes(), right.equippedTypes())
                || !Arrays.equals(left.equippedDurabilities(), right.equippedDurabilities())
                || !Arrays.equals(left.equippedEnchantments(), right.equippedEnchantments())
                || !Arrays.equals(left.equippedItemComponentData(),
                        right.equippedItemComponentData())
                || !left.offhand().equals(right.offhand())) return false;
        PlayerInventory a = left.detachedInventory();
        PlayerInventory b = right.detachedInventory();
        if (a.craftingGridSize() != b.craftingGridSize()
                || a.craftingSlotCount() != b.craftingSlotCount()
                || a.craftingStonecutter() != b.craftingStonecutter()
                || !Objects.equals(a.craftingSelection(), b.craftingSelection())
                || !cursor(a).equals(cursor(b))) return false;
        for (int slot = 0; slot < 9; slot++) {
            if (!a.craftingStackSnapshot(slot).equals(b.craftingStackSnapshot(slot))) return false;
        }
        return true;
    }

    static boolean sameRawInventory(PlayerInventory.CompletePersistenceSnapshot left,
            PlayerInventory.CompletePersistenceSnapshot right) {
        return Arrays.equals(left.itemTypes(), right.itemTypes())
                && Arrays.equals(left.counts(), right.counts())
                && Arrays.equals(left.durabilities(), right.durabilities())
                && Arrays.equals(left.enchantments(), right.enchantments())
                && Arrays.equals(left.mapIds(), right.mapIds())
                && Arrays.equals(left.shulkerIds(), right.shulkerIds())
                && Arrays.equals(left.bucketMobData(), right.bucketMobData())
                && Arrays.equals(left.itemComponentData(), right.itemComponentData());
    }

    static PlayerInventory rawInventory(PlayerInventory.CompletePersistenceSnapshot source) {
        return new PlayerInventory(source.itemTypes(), source.counts(), source.durabilities(),
                source.enchantments(), source.mapIds(), source.shulkerIds(),
                source.bucketMobData(), source.itemComponentData(), source.equippedTypes(),
                source.equippedDurabilities(), source.equippedEnchantments(),
                source.equippedItemComponentData(), source.offhand(), source.selectedSlot(),
                source.revision());
    }

    static PlayerInventory.StackSnapshot cursor(PlayerInventory inventory) {
        return new PlayerInventory.StackSnapshot(inventory.cursorType(), inventory.cursorCount(),
                inventory.cursorDurability(), inventory.cursorEnchantments(),
                inventory.cursorMapId(), inventory.cursorShulkerId(),
                inventory.cursorBucketMobData(), inventory.cursorItemComponentData());
    }

    static PlayerInventory.StackSnapshot stack(
            PlayerInventory.CompletePersistenceSnapshot source, int slot) {
        short[] types = source.itemTypes();
        int[] counts = source.counts();
        int[] durabilities = source.durabilities();
        long[] enchantments = source.enchantments();
        int[] mapIds = source.mapIds();
        int[] shulkerIds = source.shulkerIds();
        String[] buckets = source.bucketMobData();
        String[] components = source.itemComponentData();
        return new PlayerInventory.StackSnapshot(types[slot], counts[slot], durabilities[slot],
                enchantments[slot], mapIds[slot], shulkerIds[slot], buckets[slot], components[slot]);
    }

    static String mapGeneration(WorldMapData map) {
        if (map == null) throw new IllegalArgumentException("source map generation is required");
        return sha256("world-map-generation-v1|" + map.getWorldId() + "|" + map.getMapId()
                + "|" + map.getCenterX() + "|" + map.getCenterZ() + "|" + map.getScale()
                + "|" + map.isLocked() + "|" + map.getRevision() + "|"
                + sha256(map.getColors()));
    }

    static String stackCanonical(PlayerInventory.StackSnapshot stack) {
        return stack.itemType() + ":" + stack.count() + ":" + stack.durability() + ":"
                + stack.enchantments() + ":" + stack.mapId() + ":" + stack.shulkerId() + ":"
                + stack.bucketMobData() + ":" + stack.itemComponentData();
    }

    static String sha256(String canonical) {
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    private static boolean sameStack(
            PlayerInventory.DroppedStack expected, GroundItemSnapshot actual) {
        return actual != null && expected.itemType() == actual.itemType()
                && expected.count() == actual.count()
                && expected.durability() == actual.durability()
                && expected.enchantments() == actual.enchantments()
                && expected.mapId() == actual.mapId()
                && expected.shulkerId() == actual.shulkerId()
                && Objects.equals(expected.bucketMobData(), actual.bucketMobData())
                && Objects.equals(expected.itemComponentData(), actual.itemComponentData());
    }
}
