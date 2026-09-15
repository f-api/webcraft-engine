package com.gameexpert.engine.inventory;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.mob.HorseRules;
import com.gameexpert.engine.mob.LlamaRules;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One authoritative Java 1.21.4 horse inventory: SADDLE, BODY, then row-major cargo.
 *
 * <p>Every live operation is planned and committed while holding the same reservation. The
 * reservation is deliberately shared with the codec so a persistence capture cannot observe a
 * half-applied menu mutation. The local inventory revisions remain independent; only the Mob
 * menu revision is shared by one logical operation.
 */
public final class HorseMenuContainerAccess implements ContainerAccess {
    public static final int SADDLE_SLOT = 0;
    public static final int BODY_SLOT = 1;
    public static final int CARGO_START = 2;

    /** The first value which cannot be followed by a durable published generation. */
    public static final long TERMINAL_REVISION = Long.MAX_VALUE - 1L;
    private static final Object TRANSACTION_RESERVATION = new Object();

    private final Mob mob;
    private final MobType mobType;
    private final ChestInventory equipment;
    private final ChestInventory cargo;

    public HorseMenuContainerAccess(Mob mob) {
        if (mob == null || mob.horseEquipment() == null) {
            throw new IllegalArgumentException("horse-family mob required");
        }
        this.mob = mob;
        this.mobType = mob.type;
        this.equipment = mob.horseEquipment();
        this.cargo = mob.horseCargo();
    }

    public HorseMenuContainerAccess(MobType type, ChestInventory equipment,
            ChestInventory cargo) {
        if (type == null || equipment == null || equipment.slots() != 2) {
            throw new IllegalArgumentException("detached horse menu shape required");
        }
        this.mob = null;
        this.mobType = type;
        this.equipment = equipment;
        this.cargo = cargo;
    }

    /** The owner reservation shared by menu mutation and codec capture/install. */
    public static Object transactionReservation() {
        return TRANSACTION_RESERVATION;
    }

    /** Exact revisions captured with one complete horse-menu stack array. */
    public record RevisionToken(long sharedRevision, long equipmentRevision,
            long cargoRevision) {
        public RevisionToken {
            requireReadable(sharedRevision, "horse menu");
            requireReadable(equipmentRevision, "horse equipment");
            requireReadable(cargoRevision, "horse cargo");
        }
    }

    /** Full stack identities and the exact revision tuple used to plan or publish a menu. */
    public record MenuSnapshot(PlayerInventory.StackSnapshot[] stacks, RevisionToken revisions) {
        public MenuSnapshot {
            if (stacks == null || revisions == null) {
                throw new IllegalArgumentException("horse menu snapshot is required");
            }
            stacks = stacks.clone();
            for (PlayerInventory.StackSnapshot stack : stacks) {
                if (stack == null) throw new IllegalArgumentException("horse stack is required");
            }
        }

        @Override public PlayerInventory.StackSnapshot[] stacks() { return stacks.clone(); }

        public PlayerInventory.StackSnapshot stack(int slot) {
            if (slot < 0 || slot >= stacks.length) {
                throw new IndexOutOfBoundsException("horse menu slot " + slot);
            }
            return stacks[slot];
        }
    }

    /** Result returned to the later WorldTick consumer of a staged cross-inventory transaction. */
    public record TransactionResult(boolean committed, int moved,
            PlayerInventory.StackSnapshot displaced) {
        public static TransactionResult rejected() {
            return new TransactionResult(false, 0, PlayerInventory.StackSnapshot.EMPTY);
        }
    }

    private enum TransactionKind { INSERT, TAKE, SWAP }

    /**
     * A detached, retryable cross-inventory plan. Construction does not mutate either inventory;
     * commit rechecks the complete horse menu generation and immutable player source snapshot
     * under the same reservation before acquiring the player settlement lease.
     */
    public final class StagedTransaction {
        private final TransactionKind kind;
        private final PlayerInventory player;
        private final int playerSlot;
        private final PlayerInventory.HandRef playerRef;
        private final int menuSlot;
        private final InsertPlan insertPlan;
        private final MenuSnapshot expectedMenu;
        private final PlayerInventory.CompletePersistenceSnapshot expectedPlayerSnapshot;
        private final PlayerInventory.StackSnapshot expectedPlayerStack;
        private final PlayerInventory.StackSnapshot expectedMenuStack;
        private TransactionResult result;

        private StagedTransaction(TransactionKind kind, PlayerInventory player, int playerSlot,
                PlayerInventory.HandRef playerRef, int menuSlot, InsertPlan insertPlan,
                MenuSnapshot expectedMenu,
                PlayerInventory.CompletePersistenceSnapshot expectedPlayerSnapshot,
                PlayerInventory.StackSnapshot expectedPlayerStack,
                PlayerInventory.StackSnapshot expectedMenuStack) {
            this.kind = kind;
            this.player = player;
            this.playerSlot = playerSlot;
            this.playerRef = playerRef;
            this.menuSlot = menuSlot;
            this.insertPlan = insertPlan;
            this.expectedMenu = expectedMenu;
            this.expectedPlayerSnapshot = expectedPlayerSnapshot;
            this.expectedPlayerStack = expectedPlayerStack;
            this.expectedMenuStack = expectedMenuStack;
        }

        public MenuSnapshot expectedMenu() { return expectedMenu; }
        public RevisionToken expectedRevisions() { return expectedMenu.revisions(); }
        /** Immutable complete player source captured when this transaction was staged. */
        public PlayerInventory.CompletePersistenceSnapshot expectedPlayerSnapshot() {
            return expectedPlayerSnapshot;
        }
        /** Compatibility view of the revision carried by the complete source capability. */
        public long expectedPlayerRevision() { return expectedPlayerSnapshot.revision(); }
        public PlayerInventory.StackSnapshot expectedPlayerStack() { return expectedPlayerStack; }
        public PlayerInventory.StackSnapshot expectedMenuStack() { return expectedMenuStack; }
        public int plannedAmount() {
            return insertPlan == null ? expectedMenuStack.count() : insertPlan.moved();
        }

        /** Commits once, or throws a stale-generation error without touching either side. */
        public synchronized TransactionResult commit() {
            if (result != null) return result;
            result = withReservation(player, () -> commitStaged(this));
            return result;
        }
    }

    /** Persistence-load boundary for detached menus only. Live mutation must use a transaction. */
    public void restoreSlot(int slot, short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        if (mob != null) {
            throw new IllegalStateException("restore-only horse menu API cannot mutate a live menu");
        }
        if (!valid(slot) || !accepts(slot, type) || amount <= 0
                || slot < CARGO_START && amount != 1) {
            throw new IllegalArgumentException("illegal persisted horse menu slot " + slot);
        }
        withReservation(() -> {
            inventory(slot).restoreSlot(local(slot), type, amount,
                    PlayerInventory.isDurable(type) ? durability : null,
                    enchantments == 0 ? null : enchantments, mapId == 0 ? null : mapId,
                    shulkerId == 0 ? null : shulkerId, bucketMobData, itemComponentData);
            return null;
        });
    }

    /** Restore-only clearing is intentionally unavailable on a live menu. */
    public void clearForRestore() {
        if (mob != null) {
            throw new IllegalStateException("restore-only horse menu API cannot clear a live menu");
        }
        withReservation(() -> {
            equipment.clearForRestore();
            if (cargo != null) cargo.clearForRestore();
            return null;
        });
    }

    /** Captures every slot and all three revisions while the owner reservation is held. */
    public MenuSnapshot snapshot() {
        synchronized (TRANSACTION_RESERVATION) {
            if (mob == null) return captureStable();
            synchronized (mob) {
                return captureStable();
            }
        }
    }

    @Override public int slotCount() { return CARGO_START + (cargo == null ? 0 : cargo.slots()); }

    /** Each scalar getter is a generation-safe scalar read; callers needing a row use stack(). */
    @Override public short itemType(int slot) { return stack(slot).itemType(); }
    @Override public int count(int slot) { return stack(slot).count(); }
    @Override public int durability(int slot) { return stack(slot).durability(); }
    @Override public long enchantments(int slot) { return stack(slot).enchantments(); }
    @Override public int mapId(int slot) { return stack(slot).mapId(); }
    @Override public int shulkerId(int slot) { return stack(slot).shulkerId(); }
    @Override public String bucketMobData(int slot) { return stack(slot).bucketMobData(); }
    @Override public String itemComponentData(int slot) { return stack(slot).itemComponentData(); }

    /** Returns one complete stack identity rather than independently observed columns. */
    public PlayerInventory.StackSnapshot stack(int slot) {
        return withReservation(() -> snapshotLocked().stack(slot));
    }

    @Override public boolean acceptsShulkerBoxes() { return true; }
    @Override public boolean acceptsStack(long enchantments, int mapId, int shulkerId) {
        return true;
    }
    @Override public boolean acceptsStack(long enchantments, int mapId, int shulkerId,
            String bucketMobData, String itemComponentData) {
        return true;
    }

    @Override public int take(int slot, int amount) {
        return withReservation(() -> takeLocked(slot, amount));
    }

    @Override public int roomFor(int slot, short type, int durability, long enchantments,
            int mapId, int shulkerId) {
        return roomFor(slot, type, durability, enchantments, mapId, shulkerId, null, null);
    }

    @Override public int roomFor(int slot, short type, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        return withReservation(() -> roomForLocked(slot, type, durability, enchantments, mapId,
                shulkerId, bucketMobData, itemComponentData));
    }

    @Override public int put(int slot, short type, int amount, int durability,
            long enchantments, int mapId, int shulkerId) {
        return put(slot, type, amount, durability, enchantments, mapId, shulkerId, null, null);
    }

    @Override public int put(int slot, short type, int amount, int durability,
            long enchantments, int mapId, int shulkerId, String bucketMobData,
            String itemComponentData) {
        return withReservation(() -> putLocked(slot, type, amount, durability, enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData));
    }

    @Override public int insert(short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId) {
        return insert(type, amount, durability, enchantments, mapId, shulkerId, null, null);
    }

    @Override public int insert(short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        return withReservation(() -> {
            InsertPlan plan = planInsertLocked(type, amount, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
            if (plan.moved() <= 0) return 0;
            preflightHorseMutationLocked(plan.equipmentMutations(), plan.cargoMutations());
            return applyInsertPlanLocked(plan, type, durability, enchantments, mapId, shulkerId,
                    bucketMobData, itemComponentData);
        });
    }

    /** Exact direct horse-menu exchange. */
    public PlayerInventory.StackSnapshot swap(int slot, PlayerInventory.StackSnapshot replacement) {
        return withReservation(() -> swapLocked(slot, replacement));
    }

    public boolean swapSlot(int slot, PlayerInventory.StackSnapshot replacement) {
        return swap(slot, replacement) != null;
    }

    public PlayerInventory.StackSnapshot swap(int slot, short type, int amount, int durability,
            long enchantments, int mapId, int shulkerId, String bucketMobData,
            String itemComponentData) {
        if (amount <= 0) return null;
        try {
            return swap(slot, new PlayerInventory.StackSnapshot(type, amount, durability,
                    enchantments, mapId, shulkerId, bucketMobData, itemComponentData));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Stages a player-slot to horse-menu transfer without mutating either inventory. */
    public StagedTransaction stageInsert(PlayerInventory player, int playerSlot) {
        if (player == null || playerSlot < 0 || playerSlot >= PlayerInventory.SLOTS) return null;
        return withReservation(player, () -> {
            PlayerInventory.CompletePersistenceSnapshot complete =
                    player.completePersistenceSnapshot();
            PlayerInventory.StackSnapshot source = playerStackAtLocked(player, playerSlot);
            if (source.isEmpty()) return null;
            InsertPlan plan = planInsertLocked(source.itemType(), source.count(), source.durability(),
                    source.enchantments(), source.mapId(), source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData());
            if (plan.moved() <= 0) return null;
            preflightPlayerMutationLocked(player);
            preflightHorseMutationLocked(plan.equipmentMutations(), plan.cargoMutations());
            return new StagedTransaction(TransactionKind.INSERT, player, playerSlot, null, -1,
                    plan, snapshotLocked(), complete, source,
                    PlayerInventory.StackSnapshot.EMPTY);
        });
    }

    /** Stages a horse-menu slot to player transfer without mutating either inventory. */
    public StagedTransaction stageTake(PlayerInventory player, int menuSlot) {
        if (player == null || !valid(menuSlot) || !active(menuSlot)) return null;
        return withReservation(player, () -> {
            PlayerInventory.StackSnapshot source = stackLocked(menuSlot);
            if (source.isEmpty()) return null;
            PlayerInventory.CompletePersistenceSnapshot complete =
                    player.completePersistenceSnapshot();
            PlayerInventory detached = complete.detachedInventory();
            if (detached.addItem(source.itemType(), source.count(), source.durability(),
                    source.enchantments(), source.mapId(), source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData()) != source.count()) {
                return null;
            }
            preflightPlayerMutationLocked(player);
            preflightHorseMutationLocked(menuSlot < CARGO_START ? 1 : 0,
                    menuSlot >= CARGO_START ? 1 : 0);
            return new StagedTransaction(TransactionKind.TAKE, player, -1, null, menuSlot,
                    null, snapshotLocked(), complete,
                    PlayerInventory.StackSnapshot.EMPTY, source);
        });
    }

    /** Stages a full-identity player hand/menu exchange. */
    public StagedTransaction stageSwap(PlayerInventory player, PlayerInventory.HandRef playerRef,
            int menuSlot) {
        if (player == null || playerRef == null || !valid(menuSlot) || !active(menuSlot)) return null;
        return withReservation(player, () -> {
            final PlayerInventory.StackSnapshot incoming;
            try {
                incoming = player.stack(playerRef);
            } catch (IllegalArgumentException invalidRef) {
                return null;
            }
            PlayerInventory.StackSnapshot previous = stackLocked(menuSlot);
            if (incoming.equals(previous) || !validReplacement(menuSlot, incoming)) return null;
            PlayerInventory.CompletePersistenceSnapshot complete =
                    player.completePersistenceSnapshot();
            preflightPlayerMutationLocked(player);
            preflightHorseMutationLocked(menuSlot < CARGO_START ? 1 : 0,
                    menuSlot >= CARGO_START ? 1 : 0);
            return new StagedTransaction(TransactionKind.SWAP, player, -1, playerRef, menuSlot,
                    null, snapshotLocked(), complete, incoming, previous);
        });
    }

    /** Compatibility entry point; the staged transaction owns the actual cross-inventory commit. */
    public int insert(PlayerInventory player, int playerSlot) {
        StagedTransaction transaction = stageInsert(player, playerSlot);
        return transaction == null ? 0 : transaction.commit().moved();
    }

    public int take(PlayerInventory player, int menuSlot) {
        StagedTransaction transaction = stageTake(player, menuSlot);
        return transaction == null ? 0 : transaction.commit().moved();
    }

    public boolean swap(PlayerInventory player, PlayerInventory.HandRef playerRef, int menuSlot) {
        StagedTransaction transaction = stageSwap(player, playerRef, menuSlot);
        return transaction != null && transaction.commit().committed();
    }

    public int quickMove(PlayerInventory player, PlayerInventory.ContainerArea area, int slot) {
        if (area == null) return 0;
        return area == PlayerInventory.ContainerArea.INVENTORY
                ? insert(player, slot) : take(player, slot);
    }

    public int quickMove(short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        return insert(type, amount, durability, enchantments, mapId, shulkerId, bucketMobData,
                itemComponentData);
    }

    /** Executes a codec/load operation under this menu's owner reservation. */
    public <T> T withReservation(Supplier<T> operation) {
        Objects.requireNonNull(operation, "horse menu operation");
        synchronized (TRANSACTION_RESERVATION) {
            if (mob == null) return withInventoryReservation(operation);
            synchronized (mob) {
                return withInventoryReservation(operation);
            }
        }
    }

    /** Executes a cross-inventory operation under the horse and player reservations. */
    public <T> T withReservation(PlayerInventory player, Supplier<T> operation) {
        Objects.requireNonNull(player, "player inventory");
        return withReservation(() -> {
            synchronized (player) {
                return operation.get();
            }
        });
    }

    private <T> T withInventoryReservation(Supplier<T> operation) {
        requireLiveSourcesLocked();
        synchronized (equipment) {
            if (cargo == null) return operation.get();
            synchronized (cargo) {
                return operation.get();
            }
        }
    }

    private void requireLiveSourcesLocked() {
        if (mob != null && (mob.horseEquipment() != equipment || mob.horseCargo() != cargo)) {
            throw staleTransaction();
        }
    }

    private MenuSnapshot snapshotLocked() {
        ChestInventory.PersistenceSnapshot equipmentSnapshot = equipment.persistenceSnapshot();
        ChestInventory.PersistenceSnapshot cargoSnapshot = cargo == null
                ? null : cargo.persistenceSnapshot();
        PlayerInventory.StackSnapshot[] stacks = stackSnapshots(equipmentSnapshot, cargoSnapshot);
        long shared = mob == null ? 0L : mob.horseMenuPersistenceRevision();
        return new MenuSnapshot(stacks, new RevisionToken(shared, equipmentSnapshot.revision(),
                cargoSnapshot == null ? 0L : cargoSnapshot.revision()));
    }

    /**
     * Captures the two independently synchronized containers with a causal before/after check.
     * The cargo monitor is not held while the equipment snapshot is cloned: this keeps an
     * externally injected direct container mutation from deadlocking the capture callback. A
     * revision/reference change simply retries with the next complete generation.
     */
    private MenuSnapshot captureStable() {
        for (int attempt = 0; attempt < 8; attempt++) {
            requireLiveSourcesLocked();
            ChestInventory liveEquipment = equipment;
            ChestInventory liveCargo = cargo;
            long sharedBefore = mob == null ? 0L : mob.horseMenuPersistenceRevision();
            long equipmentBefore = liveEquipment.persistenceRevision();
            long cargoBefore = liveCargo == null ? 0L : liveCargo.persistenceRevision();
            ChestInventory.PersistenceSnapshot equipmentSnapshot =
                    liveEquipment.persistenceSnapshot();
            ChestInventory.PersistenceSnapshot cargoSnapshot = liveCargo == null
                    ? null : liveCargo.persistenceSnapshot();
            long sharedAfter = mob == null ? 0L : mob.horseMenuPersistenceRevision();
            long equipmentAfter = liveEquipment.persistenceRevision();
            long cargoAfter = liveCargo == null ? 0L : liveCargo.persistenceRevision();
            if (mob != null && (mob.horseEquipment() != liveEquipment
                    || mob.horseCargo() != liveCargo)) continue;
            if (sharedBefore != sharedAfter || equipmentBefore != equipmentAfter
                    || cargoBefore != cargoAfter
                    || equipmentSnapshot.revision() != equipmentBefore
                    || cargoSnapshot != null && cargoSnapshot.revision() != cargoBefore) continue;
            PlayerInventory.StackSnapshot[] stacks = stackSnapshots(equipmentSnapshot, cargoSnapshot);
            return new MenuSnapshot(stacks, new RevisionToken(sharedBefore, equipmentBefore,
                    cargoBefore));
        }
        throw new IllegalStateException("horse menu changed during persistence capture");
    }

    private int takeLocked(int slot, int amount) {
        if (!valid(slot) || !active(slot) || amount <= 0) return 0;
        PlayerInventory.StackSnapshot expected = stackLocked(slot);
        if (expected.isEmpty()) return 0;
        int taken = Math.min(amount, expected.count());
        preflightHorseMutationLocked(slot < CARGO_START ? 1 : 0,
                slot >= CARGO_START ? 1 : 0);
        int actual = inventory(slot).take(local(slot), taken);
        if (actual != taken) throw staleTransaction();
        advanceSharedRevisionLocked();
        return actual;
    }

    private int roomForLocked(int slot, short type, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData) {
        if (!valid(slot) || !active(slot) || !accepts(slot, type)) return 0;
        int room = inventory(slot).roomForSlot(local(slot), type, durability, enchantments, mapId,
                shulkerId, bucketMobData, itemComponentData);
        return slot < CARGO_START ? Math.min(1, room) : room;
    }

    private int putLocked(int slot, short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        if (amount <= 0) return 0;
        int accepted = Math.min(amount, roomForLocked(slot, type, durability, enchantments, mapId,
                shulkerId, bucketMobData, itemComponentData));
        if (accepted <= 0) return 0;
        preflightHorseMutationLocked(slot < CARGO_START ? 1 : 0,
                slot >= CARGO_START ? 1 : 0);
        int moved = inventory(slot).putInSlot(local(slot), type, accepted, durability, enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData);
        if (moved != accepted) throw staleTransaction();
        advanceSharedRevisionLocked();
        return moved;
    }

    private PlayerInventory.StackSnapshot swapLocked(int slot,
            PlayerInventory.StackSnapshot replacement) {
        if (!valid(slot) || !active(slot) || replacement == null
                || !validReplacement(slot, replacement)) return null;
        PlayerInventory.StackSnapshot previous = stackLocked(slot);
        if (previous.equals(replacement)) return null;
        preflightHorseMutationLocked(slot < CARGO_START ? 1 : 0,
                slot >= CARGO_START ? 1 : 0);
        PlayerInventory.StackSnapshot actual = inventory(slot).swapExact(local(slot), replacement);
        advanceSharedRevisionLocked();
        return actual;
    }

    private boolean validReplacement(int slot, PlayerInventory.StackSnapshot replacement) {
        return replacement.isEmpty()
                || accepts(slot, replacement.itemType())
                && (slot >= CARGO_START || replacement.count() == 1)
                && acceptsStack(replacement.enchantments(), replacement.mapId(), replacement.shulkerId(),
                        replacement.bucketMobData(), replacement.itemComponentData());
    }

    private TransactionResult commitStaged(StagedTransaction transaction) {
        if (!sameMenuGeneration(transaction.expectedMenu)
                || !samePlayerGeneration(transaction)
                || !expectedPlayerStack(transaction)) {
            throw staleTransaction();
        }
        preflightPlayerMutationLocked(transaction.player);
        PlayerInventory.CompletePersistenceSnapshot leased =
                transaction.player.acquireSettlementLease();
        if (leased == null || !sameCompleteSnapshot(leased, transaction.expectedPlayerSnapshot)) {
            if (leased != null) transaction.player.releaseSettlementLease(leased);
            throw staleTransaction();
        }

        try {
            PlayerInventory detached = leased.detachedInventory();
            int moved;
            PlayerInventory.StackSnapshot displaced = PlayerInventory.StackSnapshot.EMPTY;
            switch (transaction.kind) {
                case INSERT -> {
                    PlayerInventory.DroppedStack removed = detached.dropFromSlot(
                            transaction.playerSlot, transaction.insertPlan.moved());
                    if (removed == null || !sameDroppedStack(removed, transaction.expectedPlayerStack)
                            || removed.count() != transaction.insertPlan.moved()) {
                        throw staleTransaction();
                    }
                    applyInsertPlanLocked(transaction.insertPlan,
                            transaction.expectedPlayerStack.itemType(),
                            transaction.expectedPlayerStack.durability(),
                            transaction.expectedPlayerStack.enchantments(),
                            transaction.expectedPlayerStack.mapId(),
                            transaction.expectedPlayerStack.shulkerId(),
                            transaction.expectedPlayerStack.bucketMobData(),
                            transaction.expectedPlayerStack.itemComponentData());
                    moved = transaction.insertPlan.moved();
                }
                case TAKE -> {
                    moved = detached.addItem(transaction.expectedMenuStack.itemType(),
                            transaction.expectedMenuStack.count(), transaction.expectedMenuStack.durability(),
                            transaction.expectedMenuStack.enchantments(), transaction.expectedMenuStack.mapId(),
                            transaction.expectedMenuStack.shulkerId(),
                            transaction.expectedMenuStack.bucketMobData(),
                            transaction.expectedMenuStack.itemComponentData());
                    if (moved != transaction.expectedMenuStack.count()) throw staleTransaction();
                    int removed = inventory(transaction.menuSlot).take(local(transaction.menuSlot), moved);
                    if (removed != moved) throw staleTransaction();
                    advanceSharedRevisionLocked();
                }
                case SWAP -> {
                    PlayerInventory.HandRef detachedRef = detached.capture(
                            transaction.playerRef.hand());
                    if (!detached.setStack(detachedRef, transaction.expectedMenuStack)) {
                        throw staleTransaction();
                    }
                    displaced = inventory(transaction.menuSlot).swapExact(local(transaction.menuSlot),
                            transaction.expectedPlayerStack);
                    advanceSharedRevisionLocked();
                    moved = transaction.expectedPlayerStack.count();
                }
                default -> throw new AssertionError(transaction.kind);
            }
            PlayerInventory.CompletePersistenceSnapshot committed =
                    detached.completePersistenceSnapshot();
            if (!transaction.player.installCommittedSettlement(leased, committed)) {
                throw staleTransaction();
            }
            return new TransactionResult(true, moved, displaced);
        } catch (RuntimeException failure) {
            transaction.player.releaseSettlementLease(leased);
            throw failure;
        }
    }

    private boolean samePlayerGeneration(StagedTransaction transaction) {
        try {
            return sameCompleteSnapshot(transaction.player.completePersistenceSnapshot(),
                    transaction.expectedPlayerSnapshot);
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    private static boolean sameCompleteSnapshot(
            PlayerInventory.CompletePersistenceSnapshot actual,
            PlayerInventory.CompletePersistenceSnapshot expected) {
        return actual != null && expected != null
                && actual.revision() == expected.revision()
                && Objects.equals(actual.snapshotDigest(), expected.snapshotDigest());
    }

    private boolean expectedPlayerStack(StagedTransaction transaction) {
        PlayerInventory.StackSnapshot current;
        if (transaction.kind == TransactionKind.SWAP) {
            current = transaction.player.stack(transaction.playerRef);
        } else {
            current = playerStackAtLocked(transaction.player, transaction.playerSlot);
        }
        return current.equals(transaction.expectedPlayerStack);
    }

    private boolean sameMenuGeneration(MenuSnapshot expected) {
        MenuSnapshot current = snapshotLocked();
        return current.revisions().equals(expected.revisions())
                && Arrays.equals(current.stacks(), expected.stacks());
    }

    private int applyInsertPlanLocked(InsertPlan plan, short type, int durability,
            long enchantments, int mapId, int shulkerId, String bucketMobData,
            String itemComponentData) {
        for (Placement placement : plan.placements()) {
            int moved = inventory(placement.slot()).putInSlot(local(placement.slot()), type,
                    placement.amount(), durability, enchantments, mapId, shulkerId,
                    bucketMobData, itemComponentData);
            if (moved != placement.amount()) throw staleTransaction();
        }
        advanceSharedRevisionLocked();
        return plan.moved();
    }

    private InsertPlan planInsertLocked(short type, int amount, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        if (amount <= 0 || identity(type, durability, enchantments, mapId, shulkerId,
                bucketMobData, itemComponentData) == null
                || !acceptsStack(enchantments, mapId, shulkerId, bucketMobData,
                        itemComponentData)) return InsertPlan.EMPTY;
        List<Placement> placements = new ArrayList<>();
        int remaining = amount;
        int equipmentMutations = 0;
        int cargoMutations = 0;
        int[] priority = new int[slotCount()];
        int index = 0;
        priority[index++] = BODY_SLOT;
        priority[index++] = SADDLE_SLOT;
        for (int slot = CARGO_START; slot < slotCount(); slot++) priority[index++] = slot;
        for (int slot : priority) {
            if (remaining <= 0 || !valid(slot) || !active(slot) || !accepts(slot, type)) continue;
            int room = roomForLocked(slot, type, durability, enchantments, mapId, shulkerId,
                    bucketMobData, itemComponentData);
            int moved = Math.min(remaining, room);
            if (moved <= 0) continue;
            placements.add(new Placement(slot, moved));
            remaining -= moved;
            if (slot < CARGO_START) equipmentMutations++;
            else cargoMutations++;
        }
        return new InsertPlan(amount - remaining, List.copyOf(placements), equipmentMutations,
                cargoMutations);
    }

    private PlayerInventory.StackSnapshot stackLocked(int slot) {
        return snapshotLocked().stack(slot);
    }

    private static PlayerInventory.StackSnapshot playerStackAtLocked(PlayerInventory player,
            int slot) {
        PlayerInventory.DroppedStack stack = player.stackAt(slot);
        return stack == null ? PlayerInventory.StackSnapshot.EMPTY : new PlayerInventory.StackSnapshot(
                stack.itemType(), stack.count(), stack.durability(), stack.enchantments(),
                stack.mapId(), stack.shulkerId(), stack.bucketMobData(), stack.itemComponentData());
    }

    private void preflightHorseMutationLocked(int equipmentMutations, int cargoMutations) {
        if (equipmentMutations > 0) {
            requireNextRevision(equipment.persistenceRevision(), equipmentMutations,
                    "horse equipment");
        }
        if (cargoMutations > 0) {
            if (cargo == null) throw new IllegalStateException("horse cargo is absent");
            requireNextRevision(cargo.persistenceRevision(), cargoMutations, "horse cargo");
        }
        if (mob != null) requireNextRevision(mob.horseMenuPersistenceRevision(), 1, "horse menu");
    }

    private static void preflightPlayerMutationLocked(PlayerInventory player) {
        if (player.settlementLeased()) {
            throw new IllegalStateException("player inventory is settlement leased");
        }
        requireNextRevision(player.revision(), 1, "player inventory");
        player.completePersistenceSnapshot();
    }

    private void advanceSharedRevisionLocked() {
        if (mob != null) mob.advanceHorseMenuPersistenceRevision();
    }

    private static boolean sameDroppedStack(PlayerInventory.DroppedStack actual,
            PlayerInventory.StackSnapshot expected) {
        return actual.itemType() == expected.itemType() && actual.count() > 0
                && actual.durability() == expected.durability()
                && actual.enchantments() == expected.enchantments()
                && actual.mapId() == expected.mapId() && actual.shulkerId() == expected.shulkerId()
                && Objects.equals(actual.bucketMobData(), expected.bucketMobData())
                && Objects.equals(actual.itemComponentData(), expected.itemComponentData());
    }

    private static void requireNextRevision(long revision, int advances, String label) {
        if (advances <= 0) return;
        if (revision < 0 || revision >= TERMINAL_REVISION - advances) {
            throw new IllegalStateException(label + " persistence revision is exhausted");
        }
    }

    private static void requirePublishable(long revision, String label) {
        if (revision < 0 || revision >= TERMINAL_REVISION) {
            throw new IllegalStateException(label + " persistence revision is exhausted");
        }
    }

    private static void requireReadable(long revision, String label) {
        if (revision < 0) throw new IllegalStateException(label + " persistence revision is invalid");
    }

    private static IllegalStateException staleTransaction() {
        return new IllegalStateException("horse menu transaction is stale");
    }

    private static PlayerInventory.StackSnapshot identity(short type, int durability,
            long enchantments, int mapId, int shulkerId, String bucketMobData,
            String itemComponentData) {
        try {
            return new PlayerInventory.StackSnapshot(type, 1, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private boolean valid(int slot) { return slot >= 0 && slot < slotCount(); }
    private int local(int slot) { return slot < CARGO_START ? slot : slot - CARGO_START; }
    private ChestInventory inventory(int slot) {
        if (!valid(slot)) throw new IndexOutOfBoundsException("horse menu slot " + slot);
        return slot < CARGO_START ? equipment : cargo;
    }

    private boolean accepts(int slot, short type) {
        if (slot == SADDLE_SLOT) {
            return mobType != MobType.LLAMA && mobType != MobType.TRADER_LLAMA
                    && type == PlayerInventory.SADDLE;
        }
        if (slot == BODY_SLOT) {
            return mobType == MobType.HORSE ? HorseRules.isArmorItem(type)
                    : (mobType == MobType.LLAMA || mobType == MobType.TRADER_LLAMA)
                            && LlamaRules.isCarpet(type);
        }
        return slot >= CARGO_START && cargo != null;
    }

    private static PlayerInventory.StackSnapshot[] stackSnapshots(
            ChestInventory.PersistenceSnapshot equipmentSnapshot,
            ChestInventory.PersistenceSnapshot cargoSnapshot) {
        int cargoSlots = cargoSnapshot == null ? 0 : cargoSnapshot.slots();
        PlayerInventory.StackSnapshot[] stacks = new PlayerInventory.StackSnapshot[CARGO_START
                + cargoSlots];
        fillStackSnapshots(stacks, 0, equipmentSnapshot);
        if (cargoSnapshot != null) fillStackSnapshots(stacks, CARGO_START, cargoSnapshot);
        return stacks;
    }

    private static void fillStackSnapshots(PlayerInventory.StackSnapshot[] destination, int offset,
            ChestInventory.PersistenceSnapshot source) {
        short[] types = source.itemTypes();
        int[] counts = source.counts();
        int[] durabilities = source.durabilities();
        long[] enchantments = source.enchantments();
        int[] mapIds = source.mapIds();
        int[] shulkerIds = source.shulkerIds();
        String[] bucketMobData = source.bucketMobData();
        String[] itemComponents = source.itemComponentData();
        for (int local = 0; local < types.length; local++) {
            destination[offset + local] = new PlayerInventory.StackSnapshot(types[local], counts[local],
                    durabilities[local], enchantments[local], mapIds[local], shulkerIds[local],
                    bucketMobData[local], itemComponents[local]);
        }
    }

    private record Placement(int slot, int amount) { }

    private record InsertPlan(int moved, List<Placement> placements, int equipmentMutations,
            int cargoMutations) {
        private static final InsertPlan EMPTY = new InsertPlan(0, List.of(), 0, 0);
    }

    private boolean active(int slot) {
        if (slot == SADDLE_SLOT) {
            return mobType != MobType.LLAMA && mobType != MobType.TRADER_LLAMA;
        }
        if (slot == BODY_SLOT) {
            return mobType == MobType.HORSE || mobType == MobType.LLAMA
                    || mobType == MobType.TRADER_LLAMA;
        }
        return slot >= CARGO_START && cargo != null;
    }
}
