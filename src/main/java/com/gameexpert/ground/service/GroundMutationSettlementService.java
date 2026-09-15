package com.gameexpert.ground.service;

import com.gameexpert.common.NotFoundException;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
import com.gameexpert.ground.entity.WorldGroundItem;
import com.gameexpert.ground.entity.WorldGroundMutationReceipt;
import com.gameexpert.ground.entity.WorldGroundRevision;
import com.gameexpert.ground.entity.WorldGroundXpOrb;
import com.gameexpert.ground.repository.WorldGroundItemRepository;
import com.gameexpert.ground.repository.WorldGroundMutationReceiptRepository;
import com.gameexpert.ground.repository.WorldGroundRevisionRepository;
import com.gameexpert.ground.repository.WorldGroundXpOrbRepository;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerGroundSettlementBaseline;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.tnt.repository.WorldExplosionSettlementRepository;
import com.gameexpert.api.persistence.WorldStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomic player/ground settlement boundary for pickup, death, drops and experience. */
@Service
public class GroundMutationSettlementService {
    private final PlayerWorldStateService playerStates;
    private final WorldGroundRevisionRepository revisions;
    private final WorldGroundMutationReceiptRepository receipts;
    private final WorldGroundItemRepository items;
    private final WorldGroundXpOrbRepository xpOrbs;
    private final WorldStore worlds;
    private final WorldExplosionSettlementRepository explosionReceipts;

    @PersistenceContext
    private EntityManager entityManager;

    public GroundMutationSettlementService(PlayerWorldStateService playerStates,
            WorldGroundRevisionRepository revisions,
            WorldGroundMutationReceiptRepository receipts,
            WorldGroundItemRepository items, WorldGroundXpOrbRepository xpOrbs,
            WorldStore worlds) {
        this(playerStates, revisions, receipts, items, xpOrbs, worlds, null);
    }

    @Autowired
    public GroundMutationSettlementService(PlayerWorldStateService playerStates,
            WorldGroundRevisionRepository revisions,
            WorldGroundMutationReceiptRepository receipts,
            WorldGroundItemRepository items, WorldGroundXpOrbRepository xpOrbs,
            WorldStore worlds, WorldExplosionSettlementRepository explosionReceipts) {
        this.playerStates = playerStates;
        this.revisions = revisions;
        this.receipts = receipts;
        this.items = items;
        this.xpOrbs = xpOrbs;
        this.worlds = worlds;
        this.explosionReceipts = explosionReceipts;
    }

    @Transactional
    public GroundMutationOutcome settle(GroundMutationCommand command) {
        return settle(command, null);
    }

    /**
     * Settles a ground command while accepting a reservation which is owned by a surrounding
     * explosion transaction. The reservation is not committed independently; it is only valid
     * when its immutable ground binding matches the ground command and the caller joins the same
     * world/revision transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroundMutationOutcome settle(GroundMutationCommand command,
            AuthenticatedExternalReservedHighWater externalReservation) {
        return applyPlan(preflightPlan(command, externalReservation));
    }

    /**
     * Collects and locks every ground witness without changing a row. Callers which combine
     * ground with another authority must invoke this and {@link #applyJoiningTransaction} in the
     * same caller-owned transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementPlan preflightJoiningTransaction(GroundMutationCommand command,
            AuthenticatedExternalReservedHighWater externalReservation) {
        return preflightPlan(command, externalReservation);
    }

    /** Applies an already locked ground plan inside the caller-owned transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroundMutationOutcome applyJoiningTransaction(SettlementPlan plan) {
        if (plan == null || plan.owner != this) {
            throw new IllegalArgumentException("ground settlement plan belongs to another service");
        }
        return applyPlan(plan);
    }

    private GroundMutationOutcome applyPlan(SettlementPlan plan) {
        if (plan.state != PlanState.OPEN) {
            throw new IllegalStateException("ground settlement plan is no longer open");
        }
        if (plan.outcome != GroundMutationOutcome.COMMITTED) {
            plan.state = PlanState.APPLIED;
            return plan.outcome;
        }

        GroundMutationCommand command = plan.command;
        WorldGroundRevision revision = plan.revision;
        if (revision == null) {
            revision = new WorldGroundRevision(command.worldId());
        }
        if (!revision.advance(command.expectedGroundRevision(),
                command.committedGroundRevision())) {
            plan.state = PlanState.APPLIED;
            return GroundMutationOutcome.STALE;
        }
        if (plan.revision == null) revisions.saveAndFlush(revision);

        items.deleteAll(plan.removedItems);
        xpOrbs.deleteAll(plan.removedXpOrbs);
        for (GroundItemUpdate update : plan.updatedItems) {
            update.entity().apply(update.replacement());
        }
        if (!plan.newItems.isEmpty()) {
            items.saveAll(plan.newItems.stream()
                    .map(snapshot -> new WorldGroundItem(command.worldId(), snapshot)).toList());
        }
        if (!plan.newXpOrbs.isEmpty()) {
            xpOrbs.saveAll(plan.newXpOrbs.stream()
                    .map(snapshot -> new WorldGroundXpOrb(command.worldId(), snapshot)).toList());
        }
        if (command.committedPlayer() != null) {
            playerStates.replaceExactSnapshotJoiningTransaction(command.committedPlayer());
        }
        receipts.save(new WorldGroundMutationReceipt(
                command.worldId(), command.mutationId(), highestEntityId(command),
                command.fingerprint()));
        plan.state = PlanState.APPLIED;
        return GroundMutationOutcome.COMMITTED;
    }

    private SettlementPlan preflightPlan(GroundMutationCommand command,
            AuthenticatedExternalReservedHighWater externalReservation) {
        if (command == null) throw new IllegalArgumentException("ground mutation is required");
        validateMutationShape(command);
        lockWorld(command.worldId());
        String fingerprint = command.fingerprint();
        validateExternalReservationShape(command, externalReservation, fingerprint);
        var prior = receipts.findByWorldIdAndMutationId(command.worldId(), command.mutationId());
        if (prior.isPresent()) {
            if (!prior.get().matches(fingerprint)) {
                throw new IllegalStateException("ground mutation identity collision");
            }
            return SettlementPlan.idempotent(this, command);
        }
        WorldGroundRevision revision = revisions.findLockedByWorldId(command.worldId()).orElse(null);
        long currentGroundRevision = revision == null ? 0L : revision.getGroundRevision();
        if (currentGroundRevision != command.expectedGroundRevision()) {
            if (GroundPickupAudit.enabled(command)) GroundPickupAudit.event(command,
                    "service", "GROUND_REVISION_STALE", "expectedGroundRevision,actualGroundRevision",
                    command.expectedGroundRevision(), currentGroundRevision);
            return SettlementPlan.result(this, command, GroundMutationOutcome.STALE);
        }
        long reservedHighWater = reservedHighWater(command.worldId());
        validateExternalReservationCurrent(externalReservation, reservedHighWater);
        PlayerGroundSettlementBaseline baseline = null;
        if (command.committedPlayer() != null) {
            baseline = playerStates.lockGroundSettlementBaselineJoiningTransaction(
                    command.committedPlayer().playerId(), command.worldId());
            if (baseline.inventoryPersistenceRevision() != command.expectedPlayerRevision()) {
                if (GroundPickupAudit.enabled(command)) GroundPickupAudit.event(command,
                        "service", "PLAYER_REVISION_STALE", "playerId,expectedPlayerRevision,actualPlayerRevision",
                        command.committedPlayer().playerId(), command.expectedPlayerRevision(),
                        baseline.inventoryPersistenceRevision());
                return SettlementPlan.result(this, command, GroundMutationOutcome.STALE);
            }
        }

        List<WorldGroundItem> removedItems = new ArrayList<>(command.removedItemIds().size());
        List<Long> removedItemIds = command.removedItemIds().stream().sorted().toList();
        for (Long entityId : removedItemIds) {
            WorldGroundItem item = items.findByWorldIdAndEntityId(command.worldId(), entityId)
                    .orElse(null);
            if (item == null) {
                if (GroundPickupAudit.enabled(command)) GroundPickupAudit.event(command,
                        "service", "MISSING_REMOVED_ITEM", "missingItemId", entityId);
                return SettlementPlan.result(this, command, GroundMutationOutcome.STALE);
            }
            if (xpOrbs.findByWorldIdAndEntityId(command.worldId(), entityId).isPresent()) {
                throw new IllegalStateException("ground entity identity collision");
            }
            lockEntity(item);
            removedItems.add(item);
        }
        List<WorldGroundXpOrb> removedXp = new ArrayList<>(command.removedXpOrbIds().size());
        List<Long> removedXpIds = command.removedXpOrbIds().stream().sorted().toList();
        for (Long entityId : removedXpIds) {
            WorldGroundXpOrb orb = xpOrbs.findByWorldIdAndEntityId(command.worldId(), entityId)
                    .orElse(null);
            if (orb == null) return SettlementPlan.result(this, command,
                    GroundMutationOutcome.STALE);
            if (items.findByWorldIdAndEntityId(command.worldId(), entityId).isPresent()) {
                throw new IllegalStateException("ground entity identity collision");
            }
            lockEntity(orb);
            removedXp.add(orb);
        }
        requireSeparateEntityNamespaces(command);
        List<GroundItemUpdate> updatedItems = new ArrayList<>();
        List<GroundItemSnapshot> orderedItems = command.insertedItems().stream()
                .sorted(Comparator.comparingLong(GroundItemSnapshot::entityId)
                        .thenComparingDouble(GroundItemSnapshot::x)
                        .thenComparingDouble(GroundItemSnapshot::y)
                        .thenComparingDouble(GroundItemSnapshot::z))
                .toList();
        List<GroundItemSnapshot> newItems = new ArrayList<>();
        for (var snapshot : orderedItems) {
            if (xpOrbs.findByWorldIdAndEntityId(
                    command.worldId(), snapshot.entityId()).isPresent()) {
                throw new IllegalStateException("ground entity identity collision");
            }
            WorldGroundItem existing = items
                    .findByWorldIdAndEntityId(command.worldId(), snapshot.entityId()).orElse(null);
            if (existing != null) {
                if (command.kind() == GroundMutationCommand.Kind.PLAYER_PICKUP) {
                    if (existing.matches(snapshot)) {
                        throw new IllegalStateException("ground item identity collision");
                    }
                    if (!validPickupReplacement(existing, snapshot)) {
                        if (GroundPickupAudit.enabled(command)) GroundPickupAudit.event(command,
                                "service", "INVALID_PICKUP_REPLACEMENT", "replacementItemId", snapshot.entityId());
                        return SettlementPlan.result(this, command,
                                GroundMutationOutcome.STALE);
                    }
                    lockEntity(existing);
                    updatedItems.add(new GroundItemUpdate(existing, existing.toSnapshot(), snapshot));
                } else {
                    throw new IllegalStateException("ground item identity collision");
                }
            } else if (command.kind() == GroundMutationCommand.Kind.PLAYER_PICKUP) {
                if (GroundPickupAudit.enabled(command)) GroundPickupAudit.event(command,
                        "service", "MISSING_PICKUP_REPLACEMENT", "replacementItemId", snapshot.entityId());
                return SettlementPlan.result(this, command, GroundMutationOutcome.STALE);
            } else {
                requireAboveReservedHighWater(snapshot.entityId(), reservedHighWater);
                newItems.add(snapshot);
            }
        }
        List<GroundXpOrbSnapshot> orderedXp = command.insertedXpOrbs().stream()
                .sorted(Comparator.comparingLong(GroundXpOrbSnapshot::entityId)
                        .thenComparingDouble(GroundXpOrbSnapshot::x)
                        .thenComparingDouble(GroundXpOrbSnapshot::y)
                        .thenComparingDouble(GroundXpOrbSnapshot::z))
                .toList();
        for (var snapshot : orderedXp) {
            requireAboveReservedHighWater(snapshot.entityId(), reservedHighWater);
            if (items.findByWorldIdAndEntityId(
                    command.worldId(), snapshot.entityId()).isPresent()) {
                throw new IllegalStateException("ground entity identity collision");
            }
            WorldGroundXpOrb existing = xpOrbs
                    .findByWorldIdAndEntityId(command.worldId(), snapshot.entityId()).orElse(null);
            if (existing != null) {
                throw new IllegalStateException("ground XP identity collision");
            }
        }
        validateExternalOutputReservation(newItems, orderedXp, externalReservation,
                reservedHighWater);
        validateConservation(command, baseline, removedItems, updatedItems, removedXp);
        if (!isValidRevisionSuccessor(
                command.expectedGroundRevision(), command.committedGroundRevision())) {
            throw new IllegalArgumentException("ground revision must advance by one");
        }
        return SettlementPlan.committed(this, command, revision, baseline, removedItems, removedXp,
                updatedItems, newItems, orderedXp);
    }

    private static void validateExternalReservationShape(GroundMutationCommand command,
            AuthenticatedExternalReservedHighWater externalReservation, String fingerprint) {
        if (externalReservation == null) return;
        if (!Objects.equals(externalReservation.worldId(), command.worldId())
                || externalReservation.mutationId() != command.mutationId()
                || externalReservation.expectedGroundRevision()
                        != command.expectedGroundRevision()
                || externalReservation.committedGroundRevision()
                        != command.committedGroundRevision()
                || !Objects.equals(externalReservation.groundFingerprint(), fingerprint)
                || !isValidGroundHighWater(externalReservation.previousHighWater())
                || !isValidGroundHighWater(externalReservation.reservedHighWater())
                || externalReservation.previousHighWater()
                        > externalReservation.reservedHighWater()
                || externalReservation.reservedHighWater()
                        != Math.max(externalReservation.previousHighWater(),
                                highestEntityId(command))) {
            throw new IllegalStateException("external ground high-water is invalid");
        }
    }

    private static void validateExternalReservationCurrent(
            AuthenticatedExternalReservedHighWater externalReservation, long persistedHighWater) {
        if (externalReservation == null) return;
        if (externalReservation.previousHighWater() != persistedHighWater) {
            throw new IllegalStateException("external ground high-water reservation is stale");
        }
    }

    private static void validateExternalOutputReservation(
            List<GroundItemSnapshot> insertedItems, List<GroundXpOrbSnapshot> insertedXpOrbs,
            AuthenticatedExternalReservedHighWater externalReservation, long persistedHighWater) {
        if (externalReservation == null) return;
        if (externalReservation.reservedHighWater() < persistedHighWater) {
            throw new IllegalStateException("external ground high-water reservation is stale");
        }
        for (GroundItemSnapshot snapshot : insertedItems) {
            requireExternalOutputReservation(snapshot.entityId(), externalReservation,
                    persistedHighWater);
        }
        for (GroundXpOrbSnapshot snapshot : insertedXpOrbs) {
            requireExternalOutputReservation(snapshot.entityId(), externalReservation,
                    persistedHighWater);
        }
    }

    private static void requireExternalOutputReservation(long entityId,
            AuthenticatedExternalReservedHighWater externalReservation, long persistedHighWater) {
        if (entityId <= persistedHighWater
                || entityId > externalReservation.reservedHighWater()) {
            throw new IllegalStateException("external ground high-water reservation is stale");
        }
    }

    private static boolean isValidRevisionSuccessor(long expectedRevision,
            long committedRevision) {
        if (expectedRevision < 0 || expectedRevision >= Long.MAX_VALUE - 2) return false;
        try {
            return Math.addExact(expectedRevision, 1L) == committedRevision;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    private <T> void lockEntity(T entity) {
        if (entityManager == null) {
            throw new IllegalStateException("ground row lock authority is unavailable");
        }
        entityManager.lock(entity, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Exactly-once receipts outlive the ground entities they created. New runtime entity identities
     * must therefore start above the receipt high-water, not merely above currently live rows.
     */
    @Transactional
    public long highestReservedEntityId(Long worldId) {
        lockWorld(worldId);
        return reservedHighWater(worldId);
    }

    /**
     * Creates the only external reservation accepted by the joining ground API. Its immutable
     * fields bind the reservation to the exact ground command and allocator water.
     */
    public static AuthenticatedExternalReservedHighWater externalReservationFor(
            GroundMutationCommand command, long previousHighWater) {
        if (command == null) {
            throw new IllegalArgumentException("ground reservation requires ground mutation");
        }
        if (!isValidGroundHighWater(previousHighWater)) {
            throw new IllegalArgumentException("ground high-water is invalid");
        }
        long reservedHighWater = Math.max(previousHighWater, highestEntityId(command));
        return new AuthenticatedExternalReservedHighWater(command.worldId(), command.mutationId(),
                command.expectedGroundRevision(), command.committedGroundRevision(),
                command.fingerprint(), previousHighWater, reservedHighWater);
    }

    private void lockWorld(Long worldId) {
        requireValidWorldId(worldId);
        worlds.findByIdForShare(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
    }

    private static void requireValidWorldId(Long worldId) {
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("world id must be positive");
        }
    }

    private long reservedHighWater(Long worldId) {
        long groundHighWater = validateStoredHighWater(
                receipts.findMaximumEntityIdByWorldId(worldId), "ground");
        long explosionHighWater = explosionReceipts == null ? 0L
                : validateStoredHighWater(
                        explosionReceipts.findMaximumGroundEntityIdByWorldId(worldId),
                        "explosion");
        return Math.max(groundHighWater, explosionHighWater);
    }

    private static long validateStoredHighWater(Long highest, String source) {
        if (highest == null) return 0L;
        if (!isValidGroundHighWater(highest)) {
            throw new IllegalStateException("stored ground entity high-water is invalid");
        }
        return highest;
    }

    private static boolean isValidGroundHighWater(long highWater) {
        return highWater >= 0 && highWater <= GroundMutationCommand.MAX_GROUND_ENTITY_ID;
    }

    private static void requireAboveReservedHighWater(long entityId, long reservedHighWater) {
        if (entityId <= reservedHighWater) {
            throw new IllegalStateException("ground entity identity was already reserved");
        }
    }

    private static void requireSeparateEntityNamespaces(GroundMutationCommand command) {
        Set<Long> itemIds = new HashSet<>(command.removedItemIds());
        for (GroundItemSnapshot snapshot : command.insertedItems()) {
            itemIds.add(snapshot.entityId());
        }
        for (Long entityId : command.removedXpOrbIds()) {
            if (itemIds.contains(entityId)) {
                throw new IllegalStateException("ground entity identity collision");
            }
        }
        for (var snapshot : command.insertedXpOrbs()) {
            if (itemIds.contains(snapshot.entityId())) {
                throw new IllegalStateException("ground entity identity collision");
            }
        }
    }

    private static void validateMutationShape(GroundMutationCommand command) {
        boolean valid = switch (command.kind()) {
            case PLAYER_PICKUP -> command.committedPlayer() != null
                    && command.insertedXpOrbs().isEmpty()
                    && command.removedXpOrbIds().isEmpty();
            case PLAYER_DROP -> command.committedPlayer() != null
                    && !command.insertedItems().isEmpty()
                    && command.removedItemIds().isEmpty()
                    && command.insertedXpOrbs().isEmpty()
                    && command.removedXpOrbIds().isEmpty();
            case PLAYER_DEATH -> command.committedPlayer() != null
                    && command.removedItemIds().isEmpty()
                    && command.removedXpOrbIds().isEmpty();
            case XP_PICKUP -> command.committedPlayer() != null
                    && command.insertedItems().isEmpty()
                    && command.removedItemIds().isEmpty()
                    && command.insertedXpOrbs().isEmpty()
                    && !command.removedXpOrbIds().isEmpty();
            case BLOCK_DROP -> command.removedItemIds().isEmpty()
                    && command.removedXpOrbIds().isEmpty();
        };
        if (!valid) {
            throw new IllegalArgumentException("ground mutation kind payload is invalid");
        }
    }

    private static boolean validPickupReplacement(WorldGroundItem existing,
            GroundItemSnapshot replacement) {
        var current = existing.toSnapshot();
        // The locked ground/player revisions and conservation check protect the transaction.
        // Live item motion and age advance every tick, but their checkpoints are only periodic;
        // requiring their equality rejects a valid partial pickup even while its stack is unchanged.
        return replacement.count() > 0
                && replacement.count() < current.count()
                && current.matchesPickupIdentity(replacement);
    }

    private static void validateConservation(GroundMutationCommand command,
            PlayerGroundSettlementBaseline baseline, List<WorldGroundItem> removedItems,
            List<GroundItemUpdate> updatedItems, List<WorldGroundXpOrb> removedXp) {
        if (command.committedPlayer() == null) {
            if (command.kind() == GroundMutationCommand.Kind.PLAYER_PICKUP
                    || command.kind() == GroundMutationCommand.Kind.PLAYER_DROP
                    || command.kind() == GroundMutationCommand.Kind.PLAYER_DEATH
                    || command.kind() == GroundMutationCommand.Kind.XP_PICKUP
                    || !removedXp.isEmpty()) {
                throw new IllegalArgumentException("player-backed ground conservation is required");
            }
            return;
        }
        if (baseline == null || command.committedPlayer().xpTotal() < 0) {
            throw new IllegalArgumentException("player ground settlement baseline is invalid");
        }

        Map<StackIdentity, Long> before = inventoryTotals(baseline);
        Map<StackIdentity, Long> after = inventoryTotals(command.committedPlayer());
        switch (command.kind()) {
            case PLAYER_PICKUP -> requireExactPickup(before, after,
                    expectedPickup(removedItems, updatedItems));
            case PLAYER_DROP -> requireExactDrop(before, after,
                    expectedDrop(command.insertedItems()));
            case PLAYER_DEATH -> requireExactDeathDrop(before, after,
                    expectedDrop(command.insertedItems()));
            case XP_PICKUP -> requireUnchangedItems(before, after);
            case BLOCK_DROP -> requireExactBlockDropPlayerTransition(before, after);
        }

        // Death resets XP while creating its XP drops. All other player-backed mutations either
        // pick up or preserve XP, and accumulate only the removed orb amounts.
        if (command.kind() == GroundMutationCommand.Kind.PLAYER_DEATH) {
            if (command.committedPlayer().xpTotal() != 0) {
                throw new IllegalStateException("ground death XP reset mismatch");
            }
        } else {
            int expectedXp = saturatingXpTotal(baseline.xpTotal(), removedXp);
            if (command.committedPlayer().xpTotal() != expectedXp) {
                throw new IllegalStateException("ground XP conservation mismatch");
            }
        }
    }

    private static Map<StackIdentity, Long> inventoryTotals(PlayerGroundSettlementBaseline baseline) {
        Map<StackIdentity, Long> totals = new HashMap<>();
        addStacks(totals, baseline.mainSlots());
        addStacks(totals, baseline.equippedSlots());
        addStack(totals, baseline.offhand());
        return totals;
    }

    private static Map<StackIdentity, Long> inventoryTotals(PlayerInventoryMutationSnapshot snapshot) {
        Map<StackIdentity, Long> totals = new HashMap<>();
        short[] types = snapshot.itemTypes();
        int[] counts = snapshot.counts();
        int[] durabilities = snapshot.durabilities();
        long[] enchantments = snapshot.enchantments();
        int[] mapIds = snapshot.mapIds();
        int[] shulkerIds = snapshot.shulkerIds();
        String[] bucketMobData = snapshot.bucketMobData();
        String[] itemComponentData = snapshot.itemComponentData();
        for (int slot = 0; slot < types.length; slot++) {
            addStack(totals, new PlayerInventory.StackSnapshot(types[slot], counts[slot],
                    durabilities[slot], enchantments[slot], mapIds[slot], shulkerIds[slot],
                    bucketMobData[slot], itemComponentData[slot]));
        }

        short[] equippedTypes = snapshot.equippedTypes();
        int[] equippedDurabilities = snapshot.equippedDurabilities();
        long[] equippedEnchantments = snapshot.equippedEnchantments();
        String[] equippedComponents = snapshot.equippedItemComponentData();
        for (int slot = 0; slot < equippedTypes.length; slot++) {
            int count = equippedTypes[slot] == PlayerInventory.EMPTY ? 0 : 1;
            addStack(totals, new PlayerInventory.StackSnapshot(equippedTypes[slot], count,
                    equippedDurabilities[slot], equippedEnchantments[slot], 0, 0, null,
                    equippedComponents[slot]));
        }
        addStack(totals, snapshot.offhand());
        return totals;
    }

    private static void addStacks(Map<StackIdentity, Long> totals,
            List<PlayerInventory.StackSnapshot> stacks) {
        for (PlayerInventory.StackSnapshot stack : stacks) addStack(totals, stack);
    }

    private static void addStack(Map<StackIdentity, Long> totals,
            PlayerInventory.StackSnapshot stack) {
        if (stack == null) throw new IllegalArgumentException("player stack is required");
        if (stack.isEmpty()) return;
        StackIdentity identity = StackIdentity.of(stack);
        Long previous = totals.get(identity);
        totals.put(identity, checkedAdd(previous == null ? 0L : previous, stack.count()));
    }

    private static Map<StackIdentity, Long> expectedPickup(List<WorldGroundItem> removedItems,
            List<GroundItemUpdate> updatedItems) {
        Map<StackIdentity, Long> expected = new HashMap<>();
        for (WorldGroundItem item : removedItems) {
            GroundItemSnapshot snapshot = item.toSnapshot();
            addGroundAmount(expected, snapshot, snapshot.count());
        }
        for (GroundItemUpdate update : updatedItems) {
            long removed = checkedSubtract(update.original().count(), update.replacement().count());
            if (removed <= 0) throw new IllegalStateException("ground pickup amount is invalid");
            addGroundAmount(expected, update.original(), removed);
        }
        return expected;
    }

    private static Map<StackIdentity, Long> expectedDrop(List<GroundItemSnapshot> insertedItems) {
        Map<StackIdentity, Long> expected = new HashMap<>();
        for (GroundItemSnapshot snapshot : insertedItems) {
            addGroundAmount(expected, snapshot, snapshot.count());
        }
        return expected;
    }

    private static void addGroundAmount(Map<StackIdentity, Long> totals,
            GroundItemSnapshot snapshot, long amount) {
        StackIdentity identity = StackIdentity.of(snapshot);
        Long previous = totals.get(identity);
        totals.put(identity, checkedAdd(previous == null ? 0L : previous, amount));
    }

    private static void requireExactPickup(Map<StackIdentity, Long> before,
            Map<StackIdentity, Long> after, Map<StackIdentity, Long> expected) {
        Map<StackIdentity, Long> actual = positiveDelta(before, after);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("ground pickup conservation mismatch");
        }
    }

    private static void requireExactDrop(Map<StackIdentity, Long> before,
            Map<StackIdentity, Long> after, Map<StackIdentity, Long> expected) {
        Map<StackIdentity, Long> actual = positiveDelta(after, before);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("ground drop conservation mismatch");
        }
    }

    private static void requireExactDeathDrop(Map<StackIdentity, Long> before,
            Map<StackIdentity, Long> after, Map<StackIdentity, Long> expected) {
        // Death clears every carried stack; only vanishing-cursed identities are annihilated.
        Map<StackIdentity, Long> drops = new HashMap<>();
        for (var entry : before.entrySet()) {
            if (EnchantmentRules.enchantLevel(entry.getKey().enchantments(),
                    EnchantmentRules.VANISHING_CURSE) == 0) {
                drops.put(entry.getKey(), entry.getValue());
            }
        }
        if (!after.isEmpty() || !drops.equals(expected)) {
            throw new IllegalStateException("ground drop conservation mismatch");
        }
    }

    private static Map<StackIdentity, Long> positiveDelta(Map<StackIdentity, Long> before,
            Map<StackIdentity, Long> after) {
        Map<StackIdentity, Long> delta = new HashMap<>();
        Set<StackIdentity> identities = new HashSet<>(before.keySet());
        identities.addAll(after.keySet());
        for (StackIdentity identity : identities) {
            long amount = checkedSubtract(after.getOrDefault(identity, 0L),
                    before.getOrDefault(identity, 0L));
            if (amount < 0) {
                throw new IllegalStateException("ground stack conservation has an unbacked loss");
            }
            if (amount > 0) delta.put(identity, amount);
        }
        return delta;
    }

    private static void requireUnchangedItems(Map<StackIdentity, Long> before,
            Map<StackIdentity, Long> after) {
        if (!before.equals(after)) {
            throw new IllegalStateException("ground item conservation changed during XP pickup");
        }
    }

    private static void requireExactBlockDropPlayerTransition(Map<StackIdentity, Long> before,
            Map<StackIdentity, Long> after) {
        if (before.equals(after)) return;
        StackIdentity lost = null;
        StackIdentity gained = null;
        Set<StackIdentity> identities = new HashSet<>(before.keySet());
        identities.addAll(after.keySet());
        for (StackIdentity identity : identities) {
            long delta = checkedSubtract(after.getOrDefault(identity, 0L),
                    before.getOrDefault(identity, 0L));
            if (delta == -1 && lost == null) lost = identity;
            else if (delta == 1 && gained == null) gained = identity;
            else if (delta != 0) {
                throw new IllegalStateException("ground block-drop player conservation mismatch");
            }
        }
        if (lost == null || !PlayerInventory.isDurable(lost.itemType())
                || gained == null && lost.durability() != 1
                || gained != null && (!lost.sameExceptDurability(gained)
                        || gained.durability() != lost.durability() - 1)) {
            throw new IllegalStateException("ground block-drop player conservation mismatch");
        }
    }

    private static int saturatingXpTotal(int baselineXp, List<WorldGroundXpOrb> removedXp) {
        long removed = 0L;
        for (WorldGroundXpOrb orb : removedXp) {
            removed = checkedAdd(removed, orb.toSnapshot().amount());
        }
        long total = checkedAdd(baselineXp, removed);
        return (int) Math.min((long) Integer.MAX_VALUE, total);
    }

    private static long checkedAdd(long left, long right) {
        return Math.addExact(left, right);
    }

    private static long checkedSubtract(long left, long right) {
        return Math.subtractExact(left, right);
    }

    private static long highestEntityId(GroundMutationCommand command) {
        long highest = 0L;
        for (GroundItemSnapshot snapshot : command.insertedItems()) {
            highest = Math.max(highest, snapshot.entityId());
        }
        for (Long entityId : command.removedItemIds()) highest = Math.max(highest, entityId);
        for (var snapshot : command.insertedXpOrbs()) {
            highest = Math.max(highest, snapshot.entityId());
        }
        for (Long entityId : command.removedXpOrbIds()) highest = Math.max(highest, entityId);
        return highest;
    }

    private record GroundItemUpdate(WorldGroundItem entity, GroundItemSnapshot original,
            GroundItemSnapshot replacement) { }

    public static final class AuthenticatedExternalReservedHighWater {
        private final Long worldId;
        private final long mutationId;
        private final long expectedGroundRevision;
        private final long committedGroundRevision;
        private final String groundFingerprint;
        private final long previousHighWater;
        private final long reservedHighWater;

        private AuthenticatedExternalReservedHighWater(Long worldId,
                long mutationId, long expectedGroundRevision, long committedGroundRevision,
                String groundFingerprint, long previousHighWater, long reservedHighWater) {
            if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE
                    || mutationId <= 0 || mutationId == Long.MAX_VALUE
                    || !isValidRevisionSuccessor(expectedGroundRevision, committedGroundRevision)
                    || groundFingerprint == null || !groundFingerprint.matches("[0-9a-f]{64}")
                    || !isValidGroundHighWater(previousHighWater)
                    || !isValidGroundHighWater(reservedHighWater)
                    || previousHighWater > reservedHighWater) {
                throw new IllegalArgumentException("external ground high-water binding is invalid");
            }
            this.worldId = worldId;
            this.mutationId = mutationId;
            this.expectedGroundRevision = expectedGroundRevision;
            this.committedGroundRevision = committedGroundRevision;
            this.groundFingerprint = groundFingerprint;
            this.previousHighWater = previousHighWater;
            this.reservedHighWater = reservedHighWater;
        }

        public Long worldId() { return worldId; }
        public long mutationId() { return mutationId; }
        public long expectedGroundRevision() { return expectedGroundRevision; }
        public long committedGroundRevision() { return committedGroundRevision; }
        public String groundFingerprint() { return groundFingerprint; }
        public long previousHighWater() { return previousHighWater; }
        public long reservedHighWater() { return reservedHighWater; }
    }

    public static final class SettlementPlan {
        private final GroundMutationSettlementService owner;
        private final GroundMutationCommand command;
        private final GroundMutationOutcome outcome;
        private final WorldGroundRevision revision;
        @SuppressWarnings("unused")
        private final PlayerGroundSettlementBaseline baseline;
        private final List<WorldGroundItem> removedItems;
        private final List<WorldGroundXpOrb> removedXpOrbs;
        private final List<GroundItemUpdate> updatedItems;
        private final List<GroundItemSnapshot> newItems;
        private final List<GroundXpOrbSnapshot> newXpOrbs;
        private PlanState state = PlanState.OPEN;

        private SettlementPlan(GroundMutationSettlementService owner,
                GroundMutationCommand command, GroundMutationOutcome outcome,
                WorldGroundRevision revision, PlayerGroundSettlementBaseline baseline,
                List<WorldGroundItem> removedItems, List<WorldGroundXpOrb> removedXpOrbs,
                List<GroundItemUpdate> updatedItems, List<GroundItemSnapshot> newItems,
                List<GroundXpOrbSnapshot> newXpOrbs) {
            this.owner = owner;
            this.command = command;
            this.outcome = outcome;
            this.revision = revision;
            this.baseline = baseline;
            this.removedItems = List.copyOf(removedItems);
            this.removedXpOrbs = List.copyOf(removedXpOrbs);
            this.updatedItems = List.copyOf(updatedItems);
            this.newItems = List.copyOf(newItems);
            this.newXpOrbs = List.copyOf(newXpOrbs);
        }

        private static SettlementPlan idempotent(GroundMutationSettlementService owner,
                GroundMutationCommand command) {
            return result(owner, command, GroundMutationOutcome.IDEMPOTENT);
        }

        private static SettlementPlan result(GroundMutationSettlementService owner,
                GroundMutationCommand command, GroundMutationOutcome outcome) {
            return new SettlementPlan(owner, command, outcome, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }

        private static SettlementPlan committed(GroundMutationSettlementService owner,
                GroundMutationCommand command, WorldGroundRevision revision,
                PlayerGroundSettlementBaseline baseline, List<WorldGroundItem> removedItems,
                List<WorldGroundXpOrb> removedXpOrbs, List<GroundItemUpdate> updatedItems,
                List<GroundItemSnapshot> newItems, List<GroundXpOrbSnapshot> newXpOrbs) {
            return new SettlementPlan(owner, command, GroundMutationOutcome.COMMITTED, revision,
                    baseline, removedItems, removedXpOrbs, updatedItems, newItems, newXpOrbs);
        }

        public GroundMutationOutcome outcome() { return outcome; }
    }

    private enum PlanState { OPEN, APPLIED }

    private record StackIdentity(short itemType, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData) {
        private boolean sameExceptDurability(StackIdentity other) {
            return other != null && itemType == other.itemType
                    && enchantments == other.enchantments && mapId == other.mapId
                    && shulkerId == other.shulkerId
                    && java.util.Objects.equals(bucketMobData, other.bucketMobData)
                    && java.util.Objects.equals(itemComponentData, other.itemComponentData);
        }

        private static StackIdentity of(PlayerInventory.StackSnapshot stack) {
            return new StackIdentity(stack.itemType(), stack.durability(), stack.enchantments(),
                    stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                    stack.itemComponentData());
        }

        private static StackIdentity of(GroundItemSnapshot snapshot) {
            return new StackIdentity(snapshot.itemType(), snapshot.durability(),
                    snapshot.enchantments(), snapshot.mapId(), snapshot.shulkerId(),
                    snapshot.bucketMobData(), snapshot.itemComponentData());
        }
    }
}
