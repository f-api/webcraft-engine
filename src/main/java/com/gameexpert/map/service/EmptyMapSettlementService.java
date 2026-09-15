package com.gameexpert.map.service;

import com.gameexpert.engine.inventory.CartographyRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import com.gameexpert.map.dto.CartographyMapSettlementCommand;
import com.gameexpert.map.dto.EmptyMapSettlementCommand;
import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.map.repository.WorldPlayerMapSettlementRepository;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationReservation;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerGroundSettlementBaseline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Player, map, allocator receipt and optional overflow insertion are one terminal transaction. */
@Service
public class EmptyMapSettlementService {
    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    private final PlayerWorldStateService playerStates;
    private final WorldMapPersistenceService maps;
    private final GroundMutationSettlementService ground;
    private final WorldPlayerMapSettlementRepository settlements;

    public EmptyMapSettlementService(PlayerWorldStateService playerStates,
            WorldMapPersistenceService maps,
            GroundMutationSettlementService ground,
            WorldPlayerMapSettlementRepository settlements) {
        this.playerStates = playerStates;
        this.maps = maps;
        this.ground = ground;
        this.settlements = settlements;
    }

    public WorldMapPersistenceService.AllocationReservation reserveMapId(
            WorldMapPersistenceService.AllocationRequest request) {
        return maps.reserveMapId(request);
    }

    @Transactional
    public Outcome settle(EmptyMapSettlementCommand command) {
        if (command == null) throw new IllegalArgumentException("empty-map command is required");
        AllocationReservation reservation = command.getReservation();
        ReceiptStatus status = lockAndAuthenticate(reservation, command.getFingerprint());
        long groundEntityId = command.getOverflow().isEmpty()
                ? 0L : command.getOverflow().getFirst().entityId();
        if (status.terminal()) {
            if (status.state().getGroundEntityId() != groundEntityId) {
                throw new IllegalStateException("map settlement terminal outcome collision");
            }
            requireTerminalMap(new WorldMapData(reservation.request().worldId(),
                    reservation.mapId(), command.getCenterX(), command.getCenterZ(),
                    0, false, command.getInitialColors(), 0));
            return Outcome.IDEMPOTENT;
        }

        PlayerGroundSettlementBaseline baseline = lockSource(command.getSourceInventory(),
                command.getPlayer().playerId(), command.getPlayer().worldId());
        if (baseline == null) return Outcome.STALE;
        if (command.getPlayer().xpTotal() != baseline.xpTotal()) {
            throw new IllegalStateException("empty-map settlement changed player XP");
        }
        int centerX = center(command.getPlayer().x());
        int centerZ = center(command.getPlayer().z());
        if (centerX != command.getCenterX() || centerZ != command.getCenterZ()) {
            throw new IllegalStateException("empty-map center is not derived from player position");
        }
        WorldMapData derived = new WorldMapData(command.getPlayer().worldId(),
                reservation.mapId(), centerX, centerZ, 0, false,
                command.getInitialColors(), 0);

        if (command.getGroundMutation() != null) {
            GroundMutationOutcome groundOutcome = ground.settle(command.getGroundMutation());
            if (groundOutcome == GroundMutationOutcome.STALE) return Outcome.STALE;
            if (groundOutcome != GroundMutationOutcome.COMMITTED) {
                throw new IllegalStateException("pending map has an already-terminal ground mutation");
            }
        }
        playerStates.replaceExactSnapshotJoiningTransaction(command.getPlayer());
        maps.create(derived);
        complete(reservation, groundEntityId, command.getFingerprint());
        return Outcome.COMMITTED;
    }

    @Transactional
    public Outcome settleCartography(CartographyMapSettlementCommand command) {
        if (command == null) throw new IllegalArgumentException("cartography command is required");
        AllocationReservation reservation = command.getReservation();
        ReceiptStatus status = lockAndAuthenticate(reservation, command.getFingerprint());
        if (status.terminal()) {
            if (status.state().getGroundEntityId() != 0) {
                throw new IllegalStateException("cartography terminal outcome collision");
            }
            WorldMapData expected = derive(command, command.getSourceMap());
            if (expected == null) {
                throw new IllegalStateException("terminal cartography metadata is invalid");
            }
            requireTerminalMap(expected);
            return Outcome.IDEMPOTENT;
        }

        PlayerGroundSettlementBaseline baseline = lockSource(command.getSourceInventory(),
                command.getPlayer().playerId(), command.getPlayer().worldId());
        if (baseline == null) return Outcome.STALE;
        if (command.getPlayer().xpTotal() != baseline.xpTotal()) {
            throw new IllegalStateException("cartography changed player XP");
        }
        WorldMapData stored = maps.lockMapJoiningTransaction(
                command.getPlayer().worldId(), command.getSourceMap().getMapId());
        if (stored == null || !sameMap(stored, command.getSourceMap())) return Outcome.STALE;

        WorldMapData derived = derive(command, stored);
        if (derived == null) return Outcome.STALE;
        playerStates.replaceExactSnapshotJoiningTransaction(command.getPlayer());
        if (command.getOperation() != CartographyRules.Operation.CLONE) maps.create(derived);
        complete(reservation, 0L, command.getFingerprint());
        return Outcome.COMMITTED;
    }

    private ReceiptStatus lockAndAuthenticate(
            AllocationReservation reservation, String terminalFingerprint) {
        maps.lockWorldJoiningTransaction(reservation.request().worldId());
        settlements.findForUpdate(
                        reservation.request().worldId(), reservation.request().settlementId())
                .orElseThrow(() -> new IllegalStateException("durable map reservation is missing"));
        WorldPlayerMapSettlementRepository.SettlementState state = settlements.findState(
                        reservation.request().worldId(), reservation.request().settlementId())
                .orElseThrow(() -> new IllegalStateException("locked map reservation disappeared"));
        long expected = reservation.request().expectedInventoryRevision();
        boolean pending = state.getCommittedInventoryRevision() == expected
                && state.getGroundEntityId() == WorldMapPersistenceService.PENDING_GROUND_ENTITY_ID;
        boolean terminal = state.getCommittedInventoryRevision() == Math.addExact(expected, 1)
                && state.getGroundEntityId() >= 0;
        if (!state.getPlayerId().equals(reservation.request().playerId())
                || state.getSourceInventoryRevision() != expected
                || state.getMapId() != reservation.mapId()
                || (pending && !state.getCommandFingerprint().equals(reservation.fingerprint()))
                || (terminal && !state.getCommandFingerprint().equals(terminalFingerprint))
                || (!pending && !terminal)) {
            throw new IllegalStateException("map settlement identity collision");
        }
        return new ReceiptStatus(state, terminal);
    }

    private PlayerGroundSettlementBaseline lockSource(
            PlayerInventory.CompletePersistenceSnapshot source, Long playerId, Long worldId) {
        PlayerGroundSettlementBaseline baseline =
                playerStates.lockGroundSettlementBaselineJoiningTransaction(playerId, worldId);
        if (baseline.inventoryPersistenceRevision() != source.revision()) return null;
        if (!sameSourceInventory(baseline, source)) {
            throw new IllegalStateException(
                    "inventory lease capability does not match the durable source");
        }
        return baseline;
    }

    private void complete(AllocationReservation reservation, long groundEntityId,
            String terminalFingerprint) {
        long sourceRevision = reservation.request().expectedInventoryRevision();
        int changed = settlements.completePending(
                reservation.request().worldId(), reservation.request().settlementId(),
                reservation.request().playerId(), sourceRevision,
                Math.addExact(sourceRevision, 1), reservation.mapId(),
                WorldMapPersistenceService.PENDING_GROUND_ENTITY_ID,
                groundEntityId, reservation.fingerprint(), terminalFingerprint);
        if (changed != 1) {
            throw new IllegalStateException("map settlement terminal transition was lost");
        }
    }

    private void requireTerminalMap(WorldMapData expected) {
        WorldMapData stored = maps.lockMapJoiningTransaction(
                expected.getWorldId(), expected.getMapId());
        if (stored == null) {
            throw new IllegalStateException("terminal map settlement has no durable map row");
        }
        if (!sameMapIdentity(stored, expected)) {
            throw new IllegalStateException("terminal map settlement metadata collision");
        }
    }

    private static WorldMapData derive(
            CartographyMapSettlementCommand command, WorldMapData source) {
        return switch (command.getOperation()) {
            case CLONE -> source;
            case EXPAND -> source.isLocked() || source.getTargetMarker() != null || source.getScale() >= CartographyRules.MAX_SCALE
                    ? null
                    : new WorldMapData(source.getWorldId(), command.getResultMapId(),
                            source.getCenterX(), source.getCenterZ(), source.getScale() + 1,
                            false, new byte[WorldMapData.COLOR_COUNT], 0, source.getTargetMarker());
            case LOCK -> source.isLocked() ? null
                    : new WorldMapData(source.getWorldId(), command.getResultMapId(),
                            source.getCenterX(), source.getCenterZ(), source.getScale(), true,
                            source.getColors(), 0, source.getTargetMarker());
        };
    }

    private static boolean sameSourceInventory(PlayerGroundSettlementBaseline baseline,
            PlayerInventory.CompletePersistenceSnapshot source) {
        PlayerInventory.PersistenceSnapshot persisted = source.persistenceSnapshot();
        if (!baseline.mainSlots().equals(stacks(persisted.itemTypes(), persisted.counts(),
                persisted.durabilities(), persisted.enchantments(), persisted.mapIds(),
                persisted.shulkerIds(), persisted.bucketMobData(),
                persisted.itemComponentData()))) return false;
        short[] types = source.equippedTypes();
        int[] counts = new int[types.length];
        Arrays.setAll(counts, slot -> types[slot] == PlayerInventory.EMPTY ? 0 : 1);
        List<PlayerInventory.StackSnapshot> equipped = stacks(types, counts,
                source.equippedDurabilities(), source.equippedEnchantments(),
                new int[types.length], new int[types.length], new String[types.length],
                source.equippedItemComponentData());
        return baseline.equippedSlots().equals(equipped)
                && baseline.offhand().equals(source.offhand());
    }

    private static List<PlayerInventory.StackSnapshot> stacks(short[] types, int[] counts,
            int[] durabilities, long[] enchantments, int[] mapIds, int[] shulkerIds,
            String[] buckets, String[] components) {
        ArrayList<PlayerInventory.StackSnapshot> result = new ArrayList<>(types.length);
        for (int slot = 0; slot < types.length; slot++) {
            result.add(new PlayerInventory.StackSnapshot(types[slot], counts[slot],
                    durabilities[slot], enchantments[slot], mapIds[slot], shulkerIds[slot],
                    buckets[slot], components[slot]));
        }
        return List.copyOf(result);
    }

    private static boolean sameMap(WorldMapData stored, WorldMapData expected) {
        return sameMapIdentity(stored, expected)
                && stored.getRevision() == expected.getRevision()
                && Arrays.equals(stored.getColors(), expected.getColors());
    }

    private static boolean sameMapIdentity(WorldMapData stored, WorldMapData expected) {
        return stored.getWorldId().equals(expected.getWorldId())
                && stored.getMapId() == expected.getMapId()
                && stored.getScale() == expected.getScale()
                && stored.isLocked() == expected.isLocked()
                && stored.getCenterX() == expected.getCenterX()
                && stored.getCenterZ() == expected.getCenterZ()
                && java.util.Objects.equals(stored.getTargetMarker(), expected.getTargetMarker());
    }

    private static int center(double coordinate) {
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException("map coordinate must be finite");
        }
        long block = (long) Math.floor(coordinate);
        long centered = Math.floorDiv(block + 64L, (long) WorldMapData.SIZE)
                * WorldMapData.SIZE;
        if (centered < Integer.MIN_VALUE || centered > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("map center is outside the integer range");
        }
        return (int) centered;
    }

    private record ReceiptStatus(
            WorldPlayerMapSettlementRepository.SettlementState state, boolean terminal) { }
}
