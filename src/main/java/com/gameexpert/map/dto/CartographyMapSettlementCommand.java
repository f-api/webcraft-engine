package com.gameexpert.map.dto;

import com.gameexpert.engine.inventory.CartographyRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationKind;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationRequest;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationReservation;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import java.util.Arrays;
import java.util.Objects;
import lombok.Getter;

/** Exact durable cartography mutation bound to one source-map generation and allocator receipt. */
@Getter
public final class CartographyMapSettlementCommand {
    private final AllocationReservation reservation;
    private final PlayerInventory.CompletePersistenceSnapshot sourceInventory;
    private final PlayerInventory.CompletePersistenceSnapshot committedInventory;
    private final PlayerInventoryMutationSnapshot player;
    private final WorldMapData sourceMap;
    private final String sourceMapGeneration;
    private final CartographyRules.Operation operation;
    private final PlayerInventory.CartographyMutation inventoryMutation;
    private final String fingerprint;

    public static AllocationRequest allocationRequest(long settlementId, Long playerId,
            Long worldId, PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            WorldMapData sourceMap, CartographyRules.Operation operation, boolean shift) {
        EmptyMapSettlementCommand.requireLeasedSource(sourceInventory);
        if (playerId == null || playerId <= 0 || worldId == null || worldId <= 0
                || sourceMap == null || !worldId.equals(sourceMap.getWorldId())
                || operation == null) {
            throw new IllegalArgumentException("complete cartography allocation input is required");
        }
        PlayerInventory source = sourceInventory.detachedInventory();
        PlayerInventory.StackSnapshot mapInput = source.craftingStackSnapshot(0);
        PlayerInventory.StackSnapshot additionInput = source.craftingStackSnapshot(1);
        if (source.craftingSlotCount() != 2
                || mapInput.count() <= 0
                || mapInput.mapId() != sourceMap.getMapId()
                || CartographyRules.operation(mapInput, additionInput) != operation
                || (operation == CartographyRules.Operation.EXPAND
                        && (sourceMap.isLocked()
                                || sourceMap.getScale() >= CartographyRules.MAX_SCALE))
                || (operation == CartographyRules.Operation.LOCK && sourceMap.isLocked())) {
            throw new IllegalArgumentException("cartography allocation inputs do not match operation");
        }
        String generation = EmptyMapSettlementCommand.mapGeneration(sourceMap);
        String action = EmptyMapSettlementCommand.sha256("cartography-action-v2|" + operation
                + "|" + shift + "|" + generation + "|"
                + EmptyMapSettlementCommand.stackCanonical(mapInput) + "|"
                + EmptyMapSettlementCommand.stackCanonical(additionInput) + "|"
                + EmptyMapSettlementCommand.stackCanonical(
                        EmptyMapSettlementCommand.cursor(source)));
        AllocationKind kind = switch (operation) {
            case EXPAND -> AllocationKind.CARTOGRAPHY_EXPAND;
            case LOCK -> AllocationKind.CARTOGRAPHY_LOCK;
            case CLONE -> AllocationKind.CARTOGRAPHY_CLONE;
        };
        return new AllocationRequest(settlementId, worldId, playerId,
                sourceInventory.revision(), sourceInventory.leaseNonce(),
                sourceInventory.snapshotDigest(), kind,
                operation == CartographyRules.Operation.CLONE ? sourceMap.getMapId() : 0,
                action);
    }

    public CartographyMapSettlementCommand(AllocationReservation reservation,
            PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            PlayerInventory.CompletePersistenceSnapshot committedInventory,
            PlayerInventoryMutationSnapshot player, WorldMapData sourceMap,
            PlayerInventory.CartographyMutation inventoryMutation) {
        if (reservation == null || sourceInventory == null || committedInventory == null
                || player == null || sourceMap == null || inventoryMutation == null
                || inventoryMutation.operation() == null) {
            throw new IllegalArgumentException("complete cartography settlement is required");
        }
        CartographyRules.Operation operation = inventoryMutation.operation();
        AllocationRequest expectedRequest = allocationRequest(
                reservation.request().settlementId(), player.playerId(), player.worldId(),
                sourceInventory, sourceMap, operation, inventoryMutation.shift());
        if (!reservation.request().equals(expectedRequest)) {
            throw new IllegalArgumentException("cartography allocation reservation does not match");
        }
        EmptyMapSettlementCommand.requireMutationCapability(
                reservation, sourceInventory, committedInventory, player);
        requireExactMutation(reservation, sourceInventory, committedInventory,
                sourceMap, inventoryMutation);

        this.reservation = reservation;
        this.sourceInventory = sourceInventory;
        this.committedInventory = committedInventory;
        this.player = player;
        this.sourceMap = sourceMap;
        this.sourceMapGeneration = EmptyMapSettlementCommand.mapGeneration(sourceMap);
        this.operation = operation;
        this.inventoryMutation = inventoryMutation;
        this.fingerprint = EmptyMapSettlementCommand.sha256("cartography-terminal-v2|"
                + reservation.fingerprint() + "|" + committedInventory.snapshotDigest() + "|"
                + player.fingerprint() + "|" + sourceMapGeneration + "|"
                + inventoryMutation.fingerprint());
    }

    public long getSettlementId() {
        return reservation.request().settlementId();
    }

    public long getExpectedPlayerRevision() {
        return reservation.request().expectedInventoryRevision();
    }

    public int getResultMapId() {
        return reservation.mapId();
    }

    private static void requireExactMutation(AllocationReservation reservation,
            PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            PlayerInventory.CompletePersistenceSnapshot committedInventory,
            WorldMapData sourceMap, PlayerInventory.CartographyMutation mutation) {
        PlayerInventory before = sourceInventory.detachedInventory();
        PlayerInventory after = committedInventory.detachedInventory();
        PlayerInventory.StackSnapshot mapInput = before.craftingStackSnapshot(0);
        PlayerInventory.StackSnapshot additionInput = before.craftingStackSnapshot(1);
        CartographyRules.Plan plan = CartographyRules.plan(mapInput, additionInput,
                sourceMap.getScale(), sourceMap.isLocked(), reservation.mapId());
        if (plan == null || plan.operation() != mutation.operation()
                || mutation.sourceMapRevision() != sourceMap.getRevision()
                || mutation.beforeInventoryRevision() != sourceInventory.revision()
                || mutation.afterInventoryRevision() != committedInventory.revision()
                || !mutation.mapInputBefore().equals(mapInput)
                || !mutation.additionInputBefore().equals(additionInput)
                || !mutation.mapInputAfter().equals(after.craftingStackSnapshot(0))
                || !mutation.additionInputAfter().equals(after.craftingStackSnapshot(1))
                || !mutation.cursorBefore().equals(EmptyMapSettlementCommand.cursor(before))
                || !mutation.cursorAfter().equals(EmptyMapSettlementCommand.cursor(after))
                || !mutation.result().equals(plan.result())
                || !CartographyRules.consumedOne(mapInput, after.craftingStackSnapshot(0))
                || !CartographyRules.consumedOne(additionInput, after.craftingStackSnapshot(1))) {
            throw new IllegalArgumentException("cartography delta is stale or forged");
        }
        requireUnchangedEnvelope(sourceInventory, committedInventory, before, after);
        if (mutation.shift()) {
            requireShiftOutput(sourceInventory, committedInventory, mutation.result());
            if (!mutation.cursorBefore().equals(mutation.cursorAfter())) {
                throw new IllegalArgumentException("shift cartography changed the cursor");
            }
        } else {
            if (!EmptyMapSettlementCommand.sameRawInventory(
                    sourceInventory, committedInventory)
                    || !expectedCursor(mutation.cursorBefore(), mutation.result())
                            .equals(mutation.cursorAfter())) {
                throw new IllegalArgumentException("cartography cursor output is not exact");
            }
        }
    }

    private static void requireUnchangedEnvelope(
            PlayerInventory.CompletePersistenceSnapshot source,
            PlayerInventory.CompletePersistenceSnapshot committed,
            PlayerInventory before, PlayerInventory after) {
        if (source.selectedSlot() != committed.selectedSlot()
                || !Arrays.equals(source.equippedTypes(), committed.equippedTypes())
                || !Arrays.equals(source.equippedDurabilities(), committed.equippedDurabilities())
                || !Arrays.equals(source.equippedEnchantments(), committed.equippedEnchantments())
                || !Arrays.equals(source.equippedItemComponentData(),
                        committed.equippedItemComponentData())
                || !source.offhand().equals(committed.offhand())
                || before.craftingGridSize() != after.craftingGridSize()
                || before.craftingSlotCount() != after.craftingSlotCount()
                || before.craftingStonecutter() != after.craftingStonecutter()
                || !Objects.equals(before.craftingSelection(), after.craftingSelection())) {
            throw new IllegalArgumentException("cartography changed unrelated inventory state");
        }
        for (int slot = 2; slot < 9; slot++) {
            if (!before.craftingStackSnapshot(slot).equals(after.craftingStackSnapshot(slot))) {
                throw new IllegalArgumentException("cartography changed an unrelated grid slot");
            }
        }
    }

    private static void requireShiftOutput(
            PlayerInventory.CompletePersistenceSnapshot source,
            PlayerInventory.CompletePersistenceSnapshot committed,
            PlayerInventory.StackSnapshot result) {
        PlayerInventory expected = EmptyMapSettlementCommand.rawInventory(source);
        int inserted = expected.addItem(result.itemType(), result.count(), result.durability(),
                result.enchantments(), result.mapId(), result.shulkerId(),
                result.bucketMobData(), result.itemComponentData());
        if (inserted != result.count()
                || !EmptyMapSettlementCommand.sameRawInventory(
                        expected.completePersistenceSnapshot(), committed)) {
            throw new IllegalArgumentException("shift cartography output placement is not exact");
        }
    }

    private static PlayerInventory.StackSnapshot expectedCursor(
            PlayerInventory.StackSnapshot before, PlayerInventory.StackSnapshot result) {
        if (before.isEmpty()) return result;
        boolean sameIdentity = before.itemType() == result.itemType()
                && before.durability() == result.durability()
                && before.enchantments() == result.enchantments()
                && before.mapId() == result.mapId()
                && before.shulkerId() == result.shulkerId()
                && Objects.equals(before.bucketMobData(), result.bucketMobData())
                && Objects.equals(before.itemComponentData(), result.itemComponentData());
        int count = Math.addExact(before.count(), result.count());
        if (!sameIdentity || count > PlayerInventory.stackMax(result.itemType())) {
            throw new IllegalArgumentException("cartography cursor cannot accept its result");
        }
        return new PlayerInventory.StackSnapshot(result.itemType(), count, result.durability(),
                result.enchantments(), result.mapId(), result.shulkerId(),
                result.bucketMobData(), result.itemComponentData());
    }

}
