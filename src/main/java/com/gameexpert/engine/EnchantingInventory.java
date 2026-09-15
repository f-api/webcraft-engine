package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * [SURV-X] 인챈트 테이블 한 좌표의 입력 두 칸입니다(틱 스레드 전용). 슬롯 0은 인챈트 대상 아이템,
 * 슬롯 1은 청금석입니다. 화로({@link FurnaceInventory})와 같은 좌표 단위 블록 엔티티 취급이며,
 * 블록이 파괴되면 내용물이 그대로 월드에 쏟아집니다.
 *
 * <p>게임 플레이와 영속 복원 모두 이름·수리 이력을 포함한 스택의 전체 정체성을 보존합니다.
 * 실제 클릭은 {@link LogicalCommand}를 만들고 {@link #stage(LogicalCommand)}한 뒤 결과를 나중에
 * commit합니다. 계획과 commit 사이에 표가 바뀌면 commit은 예외 대신 거부 결과를 반환합니다.
 */
public final class EnchantingInventory {

    public static final int SLOTS = 2;
    public static final int ITEM_SLOT = 0;
    public static final int LAPIS_SLOT = 1;

    /** 이 값은 다음 durable publication을 표현할 수 없는 terminal 경계입니다. */
    public static final long TERMINAL_REVISION = Long.MAX_VALUE - 1L;

    /** Legacy 저장/드랍용 한 칸 스냅샷. 새 영속 경계는 component-complete Snapshot을 사용합니다. */
    public record StoredStack(int slot, short itemType, int count, int durability, long enchantments) {
    }

    /** 한 번에 읽고 계획한 두 칸과 그 revision입니다. 배열을 외부에 노출하지 않습니다. */
    public static final class Snapshot {
        private final PlayerInventory.StackSnapshot[] stacks;
        private final long revision;

        public Snapshot(PlayerInventory.StackSnapshot[] stacks, long revision) {
            if (stacks == null || stacks.length != SLOTS) {
                throw new IllegalArgumentException("enchanting snapshot must contain two slots");
            }
            this.stacks = stacks.clone();
            for (PlayerInventory.StackSnapshot stack : this.stacks) {
                if (stack == null) throw new IllegalArgumentException("enchanting stack is required");
            }
            this.revision = revision;
        }

        public PlayerInventory.StackSnapshot[] stacks() {
            return stacks.clone();
        }

        public PlayerInventory.StackSnapshot stack(int slot) {
            if (!valid(slot)) throw new IndexOutOfBoundsException("enchanting slot " + slot);
            return stacks[slot];
        }

        public long revision() {
            return revision;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot snapshot)) return false;
            return revision == snapshot.revision && Arrays.equals(stacks, snapshot.stacks);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(stacks) + Long.hashCode(revision);
        }
    }

    /** 하나의 table-side logical command입니다. 입력 stack은 항상 full StackSnapshot입니다. */
    public enum Operation {
        ADD,
        TAKE,
        SWAP,
        QUICK_MOVE,
        DRAG,
        REPLACE,
        DRAIN,
        BATCH
    }

    /** 계획 또는 commit이 실패했을 때 caller가 cross-inventory mutation을 취소할 이유입니다. */
    public enum RejectionReason {
        NONE,
        INVALID_COMMAND,
        INVALID_SLOT,
        INVALID_AMOUNT,
        SLOT_REJECTED,
        SLOT_OCCUPIED,
        CAPACITY,
        EMPTY,
        INSUFFICIENT_ITEMS,
        UNSUPPORTED_COMPONENTS,
        STALE_REVISION,
        STALE_SLOT,
        STALE_STATE,
        TERMINAL_RESERVE,
        NO_CHANGE,
        INJECTED_FAILURE,
        COMMIT_FAILURE
    }

    /** A deterministic placement produced by a quick/drag/batch plan. */
    public record Placement(int slot, int amount) {
        public Placement {
            if (!valid(slot) || amount <= 0) {
                throw new IllegalArgumentException("invalid enchanting placement");
            }
        }
    }

    /**
     * Immutable logical command. The convenience factories below are the preferred construction
     * surface; the public record is intentionally available to the later WorldTick handoff.
     *
     * <p>{@code QUICK_MOVE} with {@code intoTable=true} inserts {@code stack}; with false it takes
     * {@code amount} from {@code slot}. A DRAG command uses {@code amount} as the per-slot amount
     * when {@code amounts} is null, or uses the exact per-slot amounts otherwise.
     */
    public record LogicalCommand(Operation operation, long expectedRevision, int slot,
            PlayerInventory.StackSnapshot stack, PlayerInventory.StackSnapshot expectedSlot,
            int amount, int[] slots, int[] amounts, int preferredSlot, boolean intoTable,
            List<LogicalCommand> commands) {

        public LogicalCommand {
            if (slots != null) slots = slots.clone();
            if (amounts != null) amounts = amounts.clone();
            if (commands != null) commands = List.copyOf(commands);
        }

        @Override
        public int[] slots() {
            return slots == null ? null : slots.clone();
        }

        @Override
        public int[] amounts() {
            return amounts == null ? null : amounts.clone();
        }

        @Override
        public List<LogicalCommand> commands() {
            return commands == null ? null : List.copyOf(commands);
        }

        public static LogicalCommand add(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot stack, int amount) {
            return new LogicalCommand(Operation.ADD, expectedRevision, slot, stack, null,
                    amount, null, null, -1, true, null);
        }

        public static LogicalCommand add(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot stack, PlayerInventory.StackSnapshot expectedSlot,
                int amount) {
            return new LogicalCommand(Operation.ADD, expectedRevision, slot, stack, expectedSlot,
                    amount, null, null, -1, true, null);
        }

        public static LogicalCommand take(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot expectedSlot, int amount) {
            return new LogicalCommand(Operation.TAKE, expectedRevision, slot, null, expectedSlot,
                    amount, null, null, -1, false, null);
        }

        public static LogicalCommand take(long expectedRevision, int slot, int amount) {
            return take(expectedRevision, slot, null, amount);
        }

        public static LogicalCommand swap(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot replacement,
                PlayerInventory.StackSnapshot expectedSlot) {
            return new LogicalCommand(Operation.SWAP, expectedRevision, slot, replacement,
                    expectedSlot, replacement == null ? 0 : replacement.count(), null, null,
                    -1, false, null);
        }

        public static LogicalCommand swap(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot replacement) {
            return swap(expectedRevision, slot, replacement, null);
        }

        public static LogicalCommand quickMoveInto(long expectedRevision,
                PlayerInventory.StackSnapshot stack, int preferredSlot, int amount) {
            return new LogicalCommand(Operation.QUICK_MOVE, expectedRevision, -1, stack, null,
                    amount, null, null, preferredSlot, true, null);
        }

        public static LogicalCommand quickMoveInto(long expectedRevision,
                PlayerInventory.StackSnapshot stack, int preferredSlot) {
            return quickMoveInto(expectedRevision, stack, preferredSlot,
                    stack == null ? 0 : stack.count());
        }

        public static LogicalCommand quickMoveOut(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot expectedSlot, int amount) {
            return new LogicalCommand(Operation.QUICK_MOVE, expectedRevision, slot, null,
                    expectedSlot, amount, null, null, -1, false, null);
        }

        public static LogicalCommand quickMoveOut(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot expectedSlot) {
            return quickMoveOut(expectedRevision, slot, expectedSlot,
                    expectedSlot == null ? 0 : expectedSlot.count());
        }

        public static LogicalCommand drag(long expectedRevision,
                PlayerInventory.StackSnapshot stack, int[] slots, int perSlot) {
            return new LogicalCommand(Operation.DRAG, expectedRevision, -1, stack, null,
                    perSlot, slots, null, -1, true, null);
        }

        public static LogicalCommand dragExact(long expectedRevision,
                PlayerInventory.StackSnapshot stack, int[] slots, int[] amounts) {
            return new LogicalCommand(Operation.DRAG, expectedRevision, -1, stack, null,
                    0, slots, amounts, -1, true, null);
        }

        public static LogicalCommand replace(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot replacement,
                PlayerInventory.StackSnapshot expectedSlot) {
            return new LogicalCommand(Operation.REPLACE, expectedRevision, slot, replacement,
                    expectedSlot, 0, null, null, -1, false, null);
        }

        public static LogicalCommand replace(long expectedRevision, int slot,
                PlayerInventory.StackSnapshot replacement) {
            return replace(expectedRevision, slot, replacement, null);
        }

        public static LogicalCommand drain(long expectedRevision) {
            return new LogicalCommand(Operation.DRAIN, expectedRevision, -1, null, null,
                    0, null, null, -1, false, null);
        }

        public static LogicalCommand batch(long expectedRevision, List<LogicalCommand> commands) {
            return new LogicalCommand(Operation.BATCH, expectedRevision, -1, null, null,
                    0, null, null, -1, false, commands);
        }

        public static LogicalCommand quickMove(long expectedRevision,
                PlayerInventory.StackSnapshot stack, int preferredSlot) {
            return quickMoveInto(expectedRevision, stack, preferredSlot);
        }

        public static LogicalCommand quick(long expectedRevision,
                PlayerInventory.StackSnapshot stack, int preferredSlot) {
            return quickMoveInto(expectedRevision, stack, preferredSlot);
        }
    }

    /** Immutable outcome returned by a staged plan's no-throw commit. */
    public static final class CommandResult {
        private final boolean committed;
        private final int moved;
        private final int requested;
        private final long revisionBefore;
        private final long revisionAfter;
        private final RejectionReason reason;
        private final PlayerInventory.StackSnapshot transferred;
        private final PlayerInventory.StackSnapshot displaced;
        private final List<Placement> placements;
        private final Snapshot snapshot;

        private CommandResult(boolean committed, int moved, int requested, long revisionBefore,
                long revisionAfter, RejectionReason reason,
                PlayerInventory.StackSnapshot transferred,
                PlayerInventory.StackSnapshot displaced, List<Placement> placements,
                Snapshot snapshot) {
            this.committed = committed;
            this.moved = moved;
            this.requested = requested;
            this.revisionBefore = revisionBefore;
            this.revisionAfter = revisionAfter;
            this.reason = reason;
            this.transferred = transferred == null ? PlayerInventory.StackSnapshot.EMPTY : transferred;
            this.displaced = displaced == null ? PlayerInventory.StackSnapshot.EMPTY : displaced;
            this.placements = placements == null ? List.of() : List.copyOf(placements);
            this.snapshot = snapshot;
        }

        public boolean committed() { return committed; }
        public boolean changed() { return committed; }
        public boolean accepted() { return committed; }
        public boolean success() { return committed; }
        public boolean rejected() { return !committed; }
        public int moved() { return moved; }
        public int requested() { return requested; }
        public int remaining() { return Math.max(0, requested - moved); }
        public long revisionBefore() { return revisionBefore; }
        public long revisionAfter() { return revisionAfter; }
        public long revision() { return revisionAfter; }
        public RejectionReason reason() { return reason; }
        public PlayerInventory.StackSnapshot transferred() { return transferred; }
        public PlayerInventory.StackSnapshot movedStack() { return transferred; }
        public PlayerInventory.StackSnapshot displaced() { return displaced; }
        public List<Placement> placements() { return placements; }
        public Snapshot snapshot() { return snapshot; }
    }

    /**
     * A detached plan. It is both the staged result and the commit handle so a later caller can
     * preflight its player/cursor half before publishing the table half. Neither stage nor commit
     * throws for ordinary invalid, stale, terminal, or injected-failure outcomes.
     */
    public final class StagedResult {
        private final EnchantingInventory owner = EnchantingInventory.this;
        private final LogicalCommand command;
        private final State beforeState;
        private final State afterState;
        private final Snapshot before;
        private final Snapshot after;
        private final boolean planned;
        private final RejectionReason planningReason;
        private final int moved;
        private final int requested;
        private final PlayerInventory.StackSnapshot transferred;
        private final PlayerInventory.StackSnapshot displaced;
        private final List<Placement> placements;
        private CommandResult completion;

        private StagedResult(LogicalCommand command, State beforeState, State afterState,
                Snapshot before, Snapshot after, boolean planned, RejectionReason planningReason,
                int moved, int requested, PlayerInventory.StackSnapshot transferred,
                PlayerInventory.StackSnapshot displaced, List<Placement> placements) {
            this.command = command;
            this.beforeState = beforeState;
            this.afterState = afterState;
            this.before = before;
            this.after = after;
            this.planned = planned;
            this.planningReason = planningReason;
            this.moved = moved;
            this.requested = requested;
            this.transferred = transferred == null ? PlayerInventory.StackSnapshot.EMPTY : transferred;
            this.displaced = displaced == null ? PlayerInventory.StackSnapshot.EMPTY : displaced;
            this.placements = placements == null ? List.of() : List.copyOf(placements);
        }

        public LogicalCommand command() { return command; }
        public boolean accepted() { return planned; }
        public boolean planned() { return planned; }
        public boolean committed() { return completion != null && completion.committed(); }
        public boolean success() { return committed(); }
        public boolean rejected() {
            return completion == null ? !planned : completion.rejected();
        }
        public int moved() { return completion == null ? moved : completion.moved(); }
        public int requested() { return requested; }
        public int remaining() { return Math.max(0, requested - moved()); }
        public long expectedRevision() { return before.revision(); }
        public long plannedRevision() { return after.revision(); }
        public RejectionReason reason() {
            return completion == null ? planningReason : completion.reason();
        }
        public Snapshot before() { return before; }
        public Snapshot after() { return after; }
        public Snapshot plannedSnapshot() { return after; }
        public PlayerInventory.StackSnapshot transferred() { return transferred; }
        public PlayerInventory.StackSnapshot displaced() { return displaced; }
        public List<Placement> placements() { return placements; }

        /** Commits once, or returns a stable rejection without mutating the table. */
        public synchronized CommandResult commit() {
            if (completion != null) return completion;
            if (!planned) {
                completion = rejectedResult(planningReason, captureState());
                return completion;
            }
            try {
                completion = commitStaged(this);
            } catch (RuntimeException failure) {
                // The public staged boundary is deliberately no-throw. The owner state was not
                // touched by this fallback path; commitStaged also rolls back its write window.
                completion = rejectedResult(RejectionReason.COMMIT_FAILURE, captureState());
            }
            return completion;
        }

        /** Alias for callers that name the final step apply. */
        public CommandResult apply() { return commit(); }
        public CommandResult result() { return commit(); }
    }

    private static final class State {
        private final short[] itemType;
        private final int[] count;
        private final int[] durability;
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;
        private final long revision;
        private final RevisionState revisionState;

        private State(short[] itemType, int[] count, int[] durability, long[] enchantments,
                int[] mapIds, int[] shulkerIds, String[] bucketMobData,
                String[] itemComponentData, long revision, RevisionState revisionState) {
            this.itemType = itemType;
            this.count = count;
            this.durability = durability;
            this.enchantments = enchantments;
            this.mapIds = mapIds;
            this.shulkerIds = shulkerIds;
            this.bucketMobData = bucketMobData;
            this.itemComponentData = itemComponentData;
            this.revision = revision;
            this.revisionState = revisionState;
        }

        private State copy() {
            return new State(itemType.clone(), count.clone(), durability.clone(),
                    enchantments.clone(), mapIds.clone(), shulkerIds.clone(),
                    bucketMobData.clone(), itemComponentData.clone(), revision, revisionState);
        }
    }

    private enum RevisionState { OPEN, BASELINE_BOUND, TERMINAL }

    private static final class PlanData {
        private final boolean planned;
        private final RejectionReason reason;
        private final State after;
        private final int moved;
        private final int requested;
        private final PlayerInventory.StackSnapshot transferred;
        private final PlayerInventory.StackSnapshot displaced;
        private final List<Placement> placements;

        private PlanData(boolean planned, RejectionReason reason, State after, int moved,
                int requested, PlayerInventory.StackSnapshot transferred,
                PlayerInventory.StackSnapshot displaced, List<Placement> placements) {
            this.planned = planned;
            this.reason = reason;
            this.after = after;
            this.moved = moved;
            this.requested = requested;
            this.transferred = transferred;
            this.displaced = displaced;
            this.placements = placements == null ? List.of() : List.copyOf(placements);
        }

        private static PlanData rejected(RejectionReason reason, State current) {
            return new PlanData(false, reason, current.copy(), 0, 0,
                    PlayerInventory.StackSnapshot.EMPTY, PlayerInventory.StackSnapshot.EMPTY, List.of());
        }

        private static PlanData noChange(State current, int requested) {
            return new PlanData(false, RejectionReason.NO_CHANGE, current.copy(), 0, requested,
                    PlayerInventory.StackSnapshot.EMPTY, PlayerInventory.StackSnapshot.EMPTY, List.of());
        }

        private static PlanData planned(State after, int moved, int requested,
                PlayerInventory.StackSnapshot transferred,
                PlayerInventory.StackSnapshot displaced, List<Placement> placements) {
            return new PlanData(true, RejectionReason.NONE, after, moved, requested,
                    transferred, displaced, placements);
        }
    }

    private final short[] itemType = new short[SLOTS];
    private final int[] count = new int[SLOTS];
    private final int[] durability = new int[SLOTS];
    private final long[] enchantments = new long[SLOTS];
    private final int[] mapIds = new int[SLOTS];
    private final int[] shulkerIds = new int[SLOTS];
    private final String[] bucketMobData = new String[SLOTS];
    private final String[] itemComponentData = new String[SLOTS];
    private long persistenceRevision;
    private RevisionState revisionState = RevisionState.OPEN;
    private Runnable commitFailureHook;

    public EnchantingInventory() {
    }

    public synchronized long persistenceRevision() {
        return persistenceRevision;
    }

    /** Alias used by callers that treat every table generation as a revision. */
    public synchronized long revision() {
        return persistenceRevision;
    }

    /** DB load boundary. MAX-1 and above are retained as terminal, never as live mutation space. */
    public synchronized void restorePersistenceRevision(long persistedRevision) {
        if (persistedRevision < 0) {
            throw new IllegalArgumentException("enchanting persistence revision must be non-negative");
        }
        if (revisionState != RevisionState.OPEN) {
            throw new IllegalStateException("enchanting revision baseline can only be restored once");
        }
        persistenceRevision = persistedRevision;
        revisionState = persistedRevision >= TERMINAL_REVISION
                ? RevisionState.TERMINAL : RevisionState.BASELINE_BOUND;
    }

    /**
     * 인증된 저장 행을 위한 load-only 경계입니다. 모든 슬롯과 revision을 먼저 검증한 뒤 한 번에
     * 설치하므로 실패 시 기존 내용과 baseline 상태가 그대로 남습니다. 게임 플레이 mutation은
     * 이 경계를 사용하지 않습니다.
     */
    public synchronized void restorePersistenceSnapshot(
            PlayerInventory.StackSnapshot[] persistedStacks, long persistedRevision) {
        if (persistedStacks == null || persistedStacks.length != SLOTS) {
            throw new IllegalArgumentException("enchanting persistence requires exactly two slots");
        }
        if (persistedRevision < 0 || persistedRevision > TERMINAL_REVISION) {
            throw new IllegalArgumentException("enchanting persistence revision is out of range");
        }
        if (revisionState != RevisionState.OPEN) {
            throw new IllegalStateException("enchanting persistence can only be restored once");
        }

        State restored = new State(new short[SLOTS], new int[SLOTS], new int[SLOTS],
                new long[SLOTS], new int[SLOTS], new int[SLOTS], new String[SLOTS],
                new String[SLOTS], persistedRevision,
                persistedRevision >= TERMINAL_REVISION
                        ? RevisionState.TERMINAL : RevisionState.BASELINE_BOUND);
        for (int slot = 0; slot < SLOTS; slot++) {
            PlayerInventory.StackSnapshot stack = persistedStacks[slot];
            if (stack == null || !restorableInTable(slot, stack)) {
                throw new IllegalArgumentException("invalid persisted enchanting slot " + slot);
            }
            writeStack(restored, slot, stack);
        }

        installArrays(restored);
        persistenceRevision = restored.revision;
        revisionState = restored.revisionState;
    }

    /** Convenience overload for the immutable table snapshot transport. */
    public synchronized void restorePersistenceSnapshot(Snapshot persisted) {
        if (persisted == null) throw new IllegalArgumentException("enchanting snapshot is required");
        restorePersistenceSnapshot(persisted.stacks(), persisted.revision());
    }

    /** A full two-slot read suitable for a persistence or cross-inventory preflight. */
    public synchronized Snapshot snapshot() {
        return snapshotOf(captureState());
    }

    public synchronized Snapshot persistenceSnapshot() {
        return snapshot();
    }

    public synchronized PlayerInventory.StackSnapshot stack(int slot) {
        return stackAtState(captureState(), slot);
    }

    public synchronized PlayerInventory.StackSnapshot stackAt(int slot) {
        return stack(slot);
    }

    public synchronized short itemType(int slot) {
        return valid(slot) ? itemType[slot] : PlayerInventory.EMPTY;
    }

    public synchronized int count(int slot) {
        return valid(slot) ? count[slot] : 0;
    }

    public synchronized int durability(int slot) {
        return valid(slot) ? durability[slot] : 0;
    }

    public synchronized long enchantments(int slot) {
        return valid(slot) ? enchantments[slot] : EnchantmentRules.EMPTY_ENCHANTMENTS;
    }

    public synchronized String itemComponentData(int slot) {
        return valid(slot) ? itemComponentData[slot] : null;
    }

    /** [ENCHANT-WIDE] 칸의 43종 인챈트 집합. */
    public synchronized WideEnchantments wideEnchantments(int slot) {
        if (!valid(slot)) return WideEnchantments.EMPTY;
        return PlayerInventory.wideEnchantmentsOf(enchantments[slot], itemComponentData[slot]);
    }

    public synchronized boolean isEmpty() {
        return itemType[ITEM_SLOT] == PlayerInventory.EMPTY
                && itemType[LAPIS_SLOT] == PlayerInventory.EMPTY;
    }

    /**
     * 이 칸이 받아들이는 아이템인가(0=아직 인챈트되지 않은 인챈트 가능 장비, 1=청금석).
     * 이미 인챈트된 아이템은 표에 올리지 않습니다.
     */
    public static boolean accepts(int slot, short type, long itemEnchantments) {
        if (slot == ITEM_SLOT) {
            // [ENCHANT-WIDE] 평범한 책도 올린다(바닐라 Items.BOOK enchantable(1)).
            return EnchantmentRules.isTableEnchantable(type)
                    && !EnchantmentRules.hasEnchantments(itemEnchantments);
        }
        return slot == LAPIS_SLOT && type == PlayerInventory.LAPIS_LAZULI;
    }

    public static boolean accepts(int slot, PlayerInventory.StackSnapshot stack) {
        return stack != null && !stack.isEmpty() && supportedComponents(stack)
                && accepts(slot, stack.itemType(), stack.enchantments())
                // [ENCHANT-WIDE] 확장 인챈트(ID 16 이상)만 지닌 아이템도 이미 인챈트된 아이템이다.
                && (slot != ITEM_SLOT || ItemComponentCodec.extendedEnchantmentHex(
                        stack.itemComponentData()) == null);
    }

    /**
     * [ENCHANT-WIDE] 칸 용량. 바닐라 인챈트 칸(EnchantmentMenu 슬롯 0)의 {@code getMaxStackSize} 는 1 이라
     * 책 더미를 올려도 한 권만 들어간다. 청금석 칸은 아이템 최대 개수다.
     */
    public static int slotCapacity(int slot, short type) {
        return slot == ITEM_SLOT ? Math.min(1, PlayerInventory.stackMax(type))
                : PlayerInventory.stackMax(type);
    }

    /** Legacy scalar callers can use this to identify stacks without additional identity. */
    public static boolean componentless(PlayerInventory.StackSnapshot stack) {
        return stack != null && (stack.isEmpty() || stack.mapId() == 0 && stack.shulkerId() == 0
                && stack.bucketMobData() == null && stack.itemComponentData() == null);
    }

    private static boolean supportedComponents(PlayerInventory.StackSnapshot stack) {
        return stack != null && (stack.isEmpty() || stack.mapId() == 0 && stack.shulkerId() == 0
                && stack.bucketMobData() == null);
    }

    /**
     * Stages one exact logical command without touching either table array. All invalid/stale
     * inputs are converted to a rejection result so WorldTick can decide its player-side action.
     */
    public synchronized StagedResult stage(LogicalCommand command) {
        State beforeState = captureState();
        try {
            if (command == null || command.operation() == null) {
                return rejectedStage(command, beforeState, RejectionReason.INVALID_COMMAND);
            }
            if (command.expectedRevision() < 0) {
                return rejectedStage(command, beforeState, RejectionReason.INVALID_COMMAND);
            }
            if (command.expectedRevision() != beforeState.revision) {
                return rejectedStage(command, beforeState, RejectionReason.STALE_REVISION);
            }
            PlanData plan = command.operation() == Operation.BATCH
                    ? planBatch(beforeState, command)
                    : planSingle(beforeState, command);
            if (!plan.planned) {
                return rejectedStage(command, beforeState, plan.reason);
            }
            if (!hasTerminalReserve(beforeState.revision)) {
                return rejectedStage(command, beforeState, RejectionReason.TERMINAL_RESERVE);
            }
            long nextRevision = Math.incrementExact(beforeState.revision);
            return new StagedResult(command, beforeState, plan.after,
                    snapshotOf(beforeState), snapshotOf(plan.after, nextRevision), true,
                    RejectionReason.NONE, plan.moved, plan.requested, plan.transferred,
                    plan.displaced, plan.placements);
        } catch (RuntimeException failure) {
            return rejectedStage(command, beforeState, RejectionReason.COMMIT_FAILURE);
        }
    }

    /** Stages a batch against the current generation; each child is planned in the same temporary. */
    public synchronized StagedResult stageBatch(long expectedRevision,
            List<LogicalCommand> commands) {
        try {
            if (commands == null || commands.isEmpty()) {
                return stage(LogicalCommand.batch(expectedRevision, List.of()));
            }
            return stage(LogicalCommand.batch(expectedRevision, commands));
        } catch (RuntimeException invalid) {
            return rejectedStage(null, captureState(), RejectionReason.INVALID_COMMAND);
        }
    }

    /** Convenience batch overload that captures the exact current revision. */
    public synchronized StagedResult stage(List<LogicalCommand> commands) {
        return stageBatch(persistenceRevision, commands);
    }

    public synchronized StagedResult stageCommand(LogicalCommand command) {
        return stage(command);
    }

    public synchronized StagedResult stageLogicalCommand(LogicalCommand command) {
        return stage(command);
    }

    /** Outer-owner spelling for callers that keep the staged handle in a generic result slot. */
    public CommandResult commit(StagedResult staged) {
        if (staged == null) return rejectedResult(RejectionReason.INVALID_COMMAND, captureState());
        if (staged.owner != this) {
            return rejectedResult(RejectionReason.INVALID_COMMAND, captureState());
        }
        return staged.commit();
    }

    public synchronized StagedResult stageAdd(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot stack, int amount) {
        return stage(LogicalCommand.add(expectedRevision, slot, stack, amount));
    }

    public synchronized StagedResult stageAdd(long expectedRevision, int slot,
            int amount, PlayerInventory.StackSnapshot stack) {
        return stageAdd(expectedRevision, slot, stack, amount);
    }

    public synchronized StagedResult stageAdd(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot stack, PlayerInventory.StackSnapshot expectedSlot,
            int amount) {
        return stage(LogicalCommand.add(expectedRevision, slot, stack, expectedSlot, amount));
    }

    public synchronized StagedResult stageAdd(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot stack, int amount,
            PlayerInventory.StackSnapshot expectedSlot) {
        return stageAdd(expectedRevision, slot, stack, expectedSlot, amount);
    }

    public synchronized StagedResult stageAdd(long expectedRevision, int slot, short type,
            int amount, int itemDurability, long itemEnchantments, int mapId, int shulkerId,
            String bucketMobData, String itemComponentData) {
        PlayerInventory.StackSnapshot stack;
        try {
            stack = new PlayerInventory.StackSnapshot(type, amount, itemDurability,
                    itemEnchantments, mapId, shulkerId, bucketMobData, itemComponentData);
        } catch (IllegalArgumentException invalid) {
            return rejectedStage(null, captureState(),
                    itemComponentData != null || mapId != 0 || shulkerId != 0 || bucketMobData != null
                            ? RejectionReason.UNSUPPORTED_COMPONENTS : RejectionReason.INVALID_COMMAND);
        }
        return stageAdd(expectedRevision, slot, stack, amount);
    }

    public synchronized StagedResult stageTake(long expectedRevision, int slot, int amount) {
        return stage(LogicalCommand.take(expectedRevision, slot,
                valid(slot) ? stackAtState(captureState(), slot) : PlayerInventory.StackSnapshot.EMPTY,
                amount));
    }

    public synchronized StagedResult stageTake(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot expectedSlot, int amount) {
        return stage(LogicalCommand.take(expectedRevision, slot, expectedSlot, amount));
    }

    public synchronized StagedResult stageSwap(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot replacement) {
        return stage(LogicalCommand.swap(expectedRevision, slot, replacement,
                valid(slot) ? stackAtState(captureState(), slot) : PlayerInventory.StackSnapshot.EMPTY));
    }

    public synchronized StagedResult stageSwap(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot replacement, PlayerInventory.StackSnapshot expectedSlot) {
        return stage(LogicalCommand.swap(expectedRevision, slot, replacement, expectedSlot));
    }

    public synchronized StagedResult stageReplace(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot replacement) {
        return stage(LogicalCommand.replace(expectedRevision, slot, replacement,
                valid(slot) ? stackAtState(captureState(), slot) : PlayerInventory.StackSnapshot.EMPTY));
    }

    public synchronized StagedResult stageQuickMoveInto(long expectedRevision,
            PlayerInventory.StackSnapshot stack, int preferredSlot, int amount) {
        return stage(LogicalCommand.quickMoveInto(expectedRevision, stack, preferredSlot, amount));
    }

    public synchronized StagedResult stageQuickMoveOut(long expectedRevision, int slot) {
        State current = captureState();
        PlayerInventory.StackSnapshot expected = valid(slot)
                ? stackAtState(current, slot) : PlayerInventory.StackSnapshot.EMPTY;
        return stage(LogicalCommand.quickMoveOut(expectedRevision, slot, expected, expected.count()));
    }

    public synchronized StagedResult stageQuickMoveOut(long expectedRevision, int slot,
            PlayerInventory.StackSnapshot expectedSlot, int amount) {
        return stage(LogicalCommand.quickMoveOut(expectedRevision, slot, expectedSlot, amount));
    }

    public synchronized StagedResult stageDrag(long expectedRevision,
            PlayerInventory.StackSnapshot stack, int[] slots, int perSlot) {
        return stage(LogicalCommand.drag(expectedRevision, stack, slots, perSlot));
    }

    public synchronized StagedResult stageDragExact(long expectedRevision,
            PlayerInventory.StackSnapshot stack, int[] slots, int[] amounts) {
        return stage(LogicalCommand.dragExact(expectedRevision, stack, slots, amounts));
    }

    public synchronized StagedResult stageDrain(long expectedRevision) {
        return stage(LogicalCommand.drain(expectedRevision));
    }

    /** Test-only failure seam: the hook runs after the planned arrays are installed. */
    public synchronized void setCommitFailureHookForTests(Runnable hook) {
        commitFailureHook = hook;
    }

    /** Compatibility spelling for focused atomicity tests. */
    public synchronized void setFailureHookForTests(Runnable hook) {
        setCommitFailureHookForTests(hook);
    }

    public synchronized void setCommitFailureForTests(Runnable hook) {
        setCommitFailureHookForTests(hook);
    }

    /**
     * Legacy/test-compatible setter. It is implemented as one staged replacement, not a direct
     * mutate-then-check path.
     */
    public synchronized void set(int slot, short type, int amount, int itemDurability,
            long itemEnchantments) {
        if (!valid(slot)) return;
        if (type == PlayerInventory.EMPTY || amount <= 0) {
            clear(slot);
            return;
        }
        try {
            set(slot, new PlayerInventory.StackSnapshot(type, amount,
                    PlayerInventory.isDurable(type) ? itemDurability : 0,
                    EnchantmentRules.isEnchantable(type)
                            ? itemEnchantments : EnchantmentRules.EMPTY_ENCHANTMENTS,
                    0, 0, null, null));
        } catch (IllegalArgumentException invalid) {
            // The old void setter has no result channel; malformed test/setup input is a no-op.
        }
    }

    public synchronized void set(int slot, PlayerInventory.StackSnapshot stack) {
        if (!valid(slot) || stack == null) return;
        stageReplace(persistenceRevision, slot, stack).commit();
    }

    public synchronized void clear(int slot) {
        if (!valid(slot)) return;
        stageReplace(persistenceRevision, slot, PlayerInventory.StackSnapshot.EMPTY).commit();
    }

    /** Adds to one exact table slot through a staged single command. Returns moved amount. */
    public synchronized int add(int slot, PlayerInventory.StackSnapshot stack, int amount) {
        if (stack == null) return 0;
        return stageAdd(persistenceRevision, slot, stack, amount).commit().moved();
    }

    public synchronized int add(int slot, PlayerInventory.StackSnapshot stack) {
        return add(slot, stack, stack == null ? 0 : stack.count());
    }

    public synchronized int add(int slot, short type, int amount, int itemDurability,
            long itemEnchantments) {
        try {
            return add(slot, new PlayerInventory.StackSnapshot(type, amount, itemDurability,
                    EnchantmentRules.isEnchantable(type)
                            ? itemEnchantments : EnchantmentRules.EMPTY_ENCHANTMENTS,
                    0, 0, null, null), amount);
        } catch (IllegalArgumentException invalid) {
            return 0;
        }
    }

    public synchronized int add(int slot, short type, int amount) {
        return add(slot, type, amount, PlayerInventory.initialDurability(type),
                EnchantmentRules.EMPTY_ENCHANTMENTS);
    }

    /** Adds to the only table slot whose ingress rule accepts the exact full identity. */
    public synchronized int add(PlayerInventory.StackSnapshot stack, int amount) {
        int slot = destinationSlot(stack);
        return slot < 0 ? 0 : add(slot, stack, amount);
    }

    public synchronized int add(short type, int amount, int itemDurability,
            long itemEnchantments) {
        try {
            return add(new PlayerInventory.StackSnapshot(type, amount, itemDurability,
                    EnchantmentRules.isEnchantable(type)
                            ? itemEnchantments : EnchantmentRules.EMPTY_ENCHANTMENTS,
                    0, 0, null, null), amount);
        } catch (IllegalArgumentException invalid) {
            return 0;
        }
    }

    public synchronized int add(short type, int amount) {
        return add(type, amount, PlayerInventory.initialDurability(type),
                EnchantmentRules.EMPTY_ENCHANTMENTS);
    }

    /** Legacy/test-compatible take wrapper over a staged exact single command. */
    public synchronized int take(int slot, int amount) {
        return stageTake(persistenceRevision, slot, amount).commit().moved();
    }

    public synchronized int quickMove(PlayerInventory.StackSnapshot stack, int preferredSlot) {
        return stageQuickMoveInto(persistenceRevision, stack, preferredSlot,
                stack == null ? 0 : stack.count()).commit().moved();
    }

    public synchronized int quickMove(int slot) {
        return stageQuickMoveOut(persistenceRevision, slot).commit().moved();
    }

    public synchronized int drag(PlayerInventory.StackSnapshot stack, int[] slots, int perSlot) {
        return stageDrag(persistenceRevision, stack, slots, perSlot).commit().moved();
    }

    /** Applies an enchantment result through the same staged replacement boundary. */
    public synchronized boolean applyEnchantments(long mask) {
        return applyEnchantments(WideEnchantments.legacy(mask));
    }

    /**
     * [ENCHANT-WIDE] 43종 집합을 대상 칸에 확정한다. 워드 0 은 스택 칸, 워드 1·2 는 성분 문자열이
     * 싣는다(이름·배너 등 다른 성분은 보존).
     */
    public synchronized boolean applyEnchantments(WideEnchantments enchantments) {
        State current = captureState();
        PlayerInventory.StackSnapshot existing = stackAtState(current, ITEM_SLOT);
        if (existing.isEmpty() || !EnchantmentRules.isTableEnchantable(existing.itemType())) return false;
        try {
            // [ENCHANT-WIDE] 책은 마법이 부여된 책으로 바뀐다(바닐라 transmuteCopy — 다른 성분 보존).
            short resultType = EnchantmentRules.enchantingTableResult(existing.itemType());
            PlayerInventory.StackSnapshot replacement = new PlayerInventory.StackSnapshot(
                    resultType, existing.count(), existing.durability(),
                    enchantments.word0(), existing.mapId(), existing.shulkerId(),
                    existing.bucketMobData(), ItemComponentCodec.withEnchantments(
                            resultType, existing.itemComponentData(), enchantments));
            return stageReplace(persistenceRevision, ITEM_SLOT, replacement).commit().committed();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Blocks destruction drains both slots through one staged batch revision. */
    public synchronized List<StoredStack> drainAll() {
        State before = captureState();
        StagedResult staged = stageDrain(before.revision);
        CommandResult result = staged.commit();
        if (!result.committed()) return List.of();
        List<StoredStack> drained = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            PlayerInventory.StackSnapshot stack = stackAtState(before, slot);
            if (stack.isEmpty()) continue;
            drained.add(new StoredStack(slot, stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments()));
        }
        return List.copyOf(drained);
    }

    private PlanData planSingle(State current, LogicalCommand command) {
        return switch (command.operation()) {
            case ADD -> planAdd(current, command);
            case TAKE -> planTake(current, command, false);
            case SWAP -> planSwap(current, command);
            case QUICK_MOVE -> command.intoTable()
                    ? planQuickMoveInto(current, command)
                    : planTake(current, command, true);
            case DRAG -> planDrag(current, command);
            case REPLACE -> planReplace(current, command);
            case DRAIN -> planDrain(current, command);
            case BATCH -> planBatch(current, command);
        };
    }

    private PlanData planBatch(State original, LogicalCommand command) {
        List<LogicalCommand> commands = command.commands();
        if (commands == null || commands.isEmpty()) return PlanData.noChange(original, 0);
        State working = original.copy();
        int moved = 0;
        int requested = 0;
        PlayerInventory.StackSnapshot transferred = PlayerInventory.StackSnapshot.EMPTY;
        PlayerInventory.StackSnapshot displaced = PlayerInventory.StackSnapshot.EMPTY;
        List<Placement> placements = new ArrayList<>();
        for (LogicalCommand child : commands) {
            if (child == null || child.operation() == Operation.BATCH) {
                return PlanData.rejected(RejectionReason.INVALID_COMMAND, original);
            }
            if (child.expectedRevision() != original.revision) {
                return PlanData.rejected(RejectionReason.STALE_REVISION, original);
            }
            PlanData part = planSingle(working, child);
            if (!part.planned) {
                if (part.reason == RejectionReason.NO_CHANGE) continue;
                return PlanData.rejected(part.reason, original);
            }
            working = part.after;
            moved += part.moved;
            requested += part.requested;
            transferred = part.transferred;
            if (!part.displaced.isEmpty()) displaced = part.displaced;
            placements.addAll(part.placements);
        }
        return moved == 0 && workingEquals(original, working)
                ? PlanData.noChange(original, requested)
                : PlanData.planned(working, moved, requested, transferred, displaced, placements);
    }

    private PlanData planAdd(State current, LogicalCommand command) {
        int slot = command.slot();
        PlayerInventory.StackSnapshot incoming = command.stack();
        int amount = command.amount();
        if (!valid(slot)) return PlanData.rejected(RejectionReason.INVALID_SLOT, current);
        if (incoming == null) return PlanData.rejected(RejectionReason.INVALID_COMMAND, current);
        PlayerInventory.StackSnapshot existing = stackAtState(current, slot);
        if (command.expectedSlot() != null && !existing.equals(command.expectedSlot())) {
            return PlanData.rejected(RejectionReason.STALE_SLOT, current);
        }
        if (!supportedComponents(incoming)) {
            return PlanData.rejected(RejectionReason.UNSUPPORTED_COMPONENTS, current);
        }
        if (incoming.isEmpty() || amount <= 0 || amount > incoming.count()) {
            return PlanData.rejected(RejectionReason.INVALID_AMOUNT, current);
        }
        if (!accepts(slot, incoming)) return PlanData.rejected(RejectionReason.SLOT_REJECTED, current);
        int room;
        if (existing.isEmpty()) {
            room = slotCapacity(slot, incoming.itemType());
        } else if (!sameIdentity(existing, incoming)) {
            return PlanData.rejected(RejectionReason.SLOT_OCCUPIED, current);
        } else {
            room = slotCapacity(slot, incoming.itemType()) - existing.count();
        }
        if (room < amount) return PlanData.rejected(RejectionReason.CAPACITY, current);
        State after = current.copy();
        writeStack(after, slot, withCount(incoming, existing.isEmpty() ? amount : existing.count() + amount));
        return PlanData.planned(after, amount, amount, withCount(incoming, amount),
                PlayerInventory.StackSnapshot.EMPTY, List.of(new Placement(slot, amount)));
    }

    private PlanData planTake(State current, LogicalCommand command, boolean quick) {
        int slot = command.slot();
        int amount = command.amount();
        if (!valid(slot)) return PlanData.rejected(RejectionReason.INVALID_SLOT, current);
        if (amount <= 0) return PlanData.rejected(RejectionReason.INVALID_AMOUNT, current);
        PlayerInventory.StackSnapshot existing = stackAtState(current, slot);
        if (command.expectedSlot() != null && !existing.equals(command.expectedSlot())) {
            return PlanData.rejected(RejectionReason.STALE_SLOT, current);
        }
        if (existing.isEmpty()) return PlanData.rejected(RejectionReason.EMPTY, current);
        int moved = Math.min(amount, existing.count());
        if (moved <= 0) return PlanData.rejected(RejectionReason.INSUFFICIENT_ITEMS, current);
        State after = current.copy();
        int remaining = existing.count() - moved;
        if (remaining == 0) clearState(after, slot);
        else writeStack(after, slot, withCount(existing, remaining));
        return PlanData.planned(after, moved, quick ? existing.count() : amount,
                withCount(existing, moved), PlayerInventory.StackSnapshot.EMPTY,
                List.of(new Placement(slot, moved)));
    }

    private PlanData planSwap(State current, LogicalCommand command) {
        int slot = command.slot();
        PlayerInventory.StackSnapshot replacement = command.stack();
        if (!valid(slot)) return PlanData.rejected(RejectionReason.INVALID_SLOT, current);
        if (replacement == null) return PlanData.rejected(RejectionReason.INVALID_COMMAND, current);
        PlayerInventory.StackSnapshot existing = stackAtState(current, slot);
        if (command.expectedSlot() != null && !existing.equals(command.expectedSlot())) {
            return PlanData.rejected(RejectionReason.STALE_SLOT, current);
        }
        if (!replacement.isEmpty() && !supportedComponents(replacement)) {
            return PlanData.rejected(RejectionReason.UNSUPPORTED_COMPONENTS, current);
        }
        if (!replacement.isEmpty() && !accepts(slot, replacement)) {
            return PlanData.rejected(RejectionReason.SLOT_REJECTED, current);
        }
        if (existing.equals(replacement)) return PlanData.noChange(current, replacement.count());
        State after = current.copy();
        writeStack(after, slot, replacement);
        return PlanData.planned(after, replacement.count(), replacement.count(), replacement,
                existing, replacement.isEmpty() ? List.of() : List.of(new Placement(slot, replacement.count())));
    }

    private PlanData planReplace(State current, LogicalCommand command) {
        int slot = command.slot();
        PlayerInventory.StackSnapshot replacement = command.stack();
        if (!valid(slot)) return PlanData.rejected(RejectionReason.INVALID_SLOT, current);
        if (replacement == null) return PlanData.rejected(RejectionReason.INVALID_COMMAND, current);
        PlayerInventory.StackSnapshot existing = stackAtState(current, slot);
        if (command.expectedSlot() != null && !existing.equals(command.expectedSlot())) {
            return PlanData.rejected(RejectionReason.STALE_SLOT, current);
        }
        if (!storedInTable(slot, replacement)) {
            return supportedComponents(replacement)
                    ? PlanData.rejected(RejectionReason.SLOT_REJECTED, current)
                    : PlanData.rejected(RejectionReason.UNSUPPORTED_COMPONENTS, current);
        }
        if (existing.equals(replacement)) return PlanData.noChange(current, replacement.count());
        State after = current.copy();
        writeStack(after, slot, replacement);
        return PlanData.planned(after, replacement.count(), replacement.count(), replacement,
                existing, replacement.isEmpty() ? List.of() : List.of(new Placement(slot, replacement.count())));
    }

    private PlanData planQuickMoveInto(State current, LogicalCommand command) {
        PlayerInventory.StackSnapshot incoming = command.stack();
        int amount = command.amount();
        int preferred = command.preferredSlot();
        if (incoming == null) return PlanData.rejected(RejectionReason.INVALID_COMMAND, current);
        if (!supportedComponents(incoming)) {
            return PlanData.rejected(RejectionReason.UNSUPPORTED_COMPONENTS, current);
        }
        if (incoming.isEmpty() || amount <= 0 || amount > incoming.count()) {
            return PlanData.rejected(RejectionReason.INVALID_AMOUNT, current);
        }
        if (preferred < -1 || preferred >= SLOTS) {
            return PlanData.rejected(RejectionReason.INVALID_SLOT, current);
        }
        int[] order = preferred < 0
                ? new int[] { ITEM_SLOT, LAPIS_SLOT }
                : new int[] { preferred, preferred == ITEM_SLOT ? LAPIS_SLOT : ITEM_SLOT };
        State after = current.copy();
        int remaining = amount;
        List<Placement> placements = new ArrayList<>();
        for (int slot : order) {
            if (!accepts(slot, incoming) || remaining == 0) continue;
            PlayerInventory.StackSnapshot existing = stackAtState(after, slot);
            if (!existing.isEmpty() && !sameIdentity(existing, incoming)) continue;
            int room = existing.isEmpty()
                    ? slotCapacity(slot, incoming.itemType())
                    : slotCapacity(slot, incoming.itemType()) - existing.count();
            int moved = Math.min(remaining, Math.max(0, room));
            if (moved == 0) continue;
            writeStack(after, slot, withCount(incoming,
                    existing.isEmpty() ? moved : existing.count() + moved));
            placements.add(new Placement(slot, moved));
            remaining -= moved;
        }
        int moved = amount - remaining;
        if (moved == 0) {
            boolean legalSlot = accepts(preferred < 0 ? ITEM_SLOT : preferred, incoming)
                    || accepts(preferred == ITEM_SLOT ? LAPIS_SLOT : ITEM_SLOT, incoming);
            return PlanData.rejected(legalSlot ? RejectionReason.CAPACITY : RejectionReason.SLOT_REJECTED,
                    current);
        }
        return PlanData.planned(after, moved, amount, withCount(incoming, moved),
                PlayerInventory.StackSnapshot.EMPTY, placements);
    }

    private PlanData planDrag(State current, LogicalCommand command) {
        PlayerInventory.StackSnapshot incoming = command.stack();
        int[] slots = command.slots();
        int[] amounts = command.amounts();
        if (incoming == null || slots == null || slots.length == 0) {
            return PlanData.rejected(RejectionReason.INVALID_COMMAND, current);
        }
        if (!supportedComponents(incoming)) {
            return PlanData.rejected(RejectionReason.UNSUPPORTED_COMPONENTS, current);
        }
        if (amounts != null && amounts.length != slots.length) {
            return PlanData.rejected(RejectionReason.INVALID_COMMAND, current);
        }
        if (amounts == null && command.amount() <= 0) {
            return PlanData.rejected(RejectionReason.INVALID_AMOUNT, current);
        }
        long requestedTotal = 0L;
        for (int index = 0; index < slots.length; index++) {
            if (!valid(slots[index])) return PlanData.rejected(RejectionReason.INVALID_SLOT, current);
            int requestedForSlot = amounts == null ? command.amount() : amounts[index];
            if (requestedForSlot <= 0) {
                return PlanData.rejected(RejectionReason.INVALID_AMOUNT, current);
            }
            requestedTotal += requestedForSlot;
            if (requestedTotal > Integer.MAX_VALUE) {
                return PlanData.rejected(RejectionReason.INVALID_AMOUNT, current);
            }
        }
        State after = current.copy();
        boolean[] seen = new boolean[SLOTS];
        int remaining = incoming.count();
        int requested = 0;
        List<Placement> placements = new ArrayList<>();
        for (int index = 0; index < slots.length && remaining > 0; index++) {
            int slot = slots[index];
            if (seen[slot]) continue;
            seen[slot] = true;
            int requestedForSlot = amounts == null ? command.amount() : amounts[index];
            requested += requestedForSlot;
            if (!accepts(slot, incoming)) continue;
            PlayerInventory.StackSnapshot existing = stackAtState(after, slot);
            if (!existing.isEmpty() && !sameIdentity(existing, incoming)) continue;
            int room = existing.isEmpty()
                    ? slotCapacity(slot, incoming.itemType())
                    : slotCapacity(slot, incoming.itemType()) - existing.count();
            int moved = Math.min(remaining, Math.min(requestedForSlot, Math.max(0, room)));
            if (moved == 0) continue;
            writeStack(after, slot, withCount(incoming,
                    existing.isEmpty() ? moved : existing.count() + moved));
            placements.add(new Placement(slot, moved));
            remaining -= moved;
        }
        int moved = incoming.count() - remaining;
        if (moved == 0) {
            return PlanData.rejected(placements.isEmpty() && requested > 0
                    ? RejectionReason.CAPACITY : RejectionReason.SLOT_REJECTED, current);
        }
        return PlanData.planned(after, moved, requested,
                withCount(incoming, moved), PlayerInventory.StackSnapshot.EMPTY, placements);
    }

    private PlanData planDrain(State current, LogicalCommand command) {
        State after = current.copy();
        int moved = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            PlayerInventory.StackSnapshot stack = stackAtState(current, slot);
            if (stack.isEmpty()) continue;
            moved += stack.count();
            clearState(after, slot);
        }
        return moved == 0 ? PlanData.noChange(current, 0)
                : PlanData.planned(after, moved, moved, PlayerInventory.StackSnapshot.EMPTY,
                        PlayerInventory.StackSnapshot.EMPTY, List.of());
    }

    private synchronized CommandResult commitStaged(StagedResult staged) {
        State current = captureState();
        if (current.revision != staged.beforeState.revision) {
            return rejectedResult(RejectionReason.STALE_REVISION, current);
        }
        if (!sameState(current, staged.beforeState)) {
            return rejectedResult(RejectionReason.STALE_STATE, current);
        }
        if (!hasTerminalReserve(current.revision)) {
            return rejectedResult(RejectionReason.TERMINAL_RESERVE, current);
        }
        State rollback = current.copy();
        Runnable hook = commitFailureHook;
        commitFailureHook = null;
        try {
            installArrays(staged.afterState);
            if (hook != null) hook.run();
            persistenceRevision = Math.incrementExact(current.revision);
            if (persistenceRevision >= TERMINAL_REVISION) revisionState = RevisionState.TERMINAL;
            Snapshot committedSnapshot = snapshotOf(captureState());
            return new CommandResult(true, staged.moved, staged.requested, current.revision,
                    persistenceRevision, RejectionReason.NONE, staged.transferred, staged.displaced,
                    staged.placements, committedSnapshot);
        } catch (RuntimeException failure) {
            installArrays(rollback);
            persistenceRevision = rollback.revision;
            revisionState = rollback.revisionState;
            return rejectedResult(hook == null ? RejectionReason.COMMIT_FAILURE
                    : RejectionReason.INJECTED_FAILURE, captureState());
        }
    }

    private synchronized CommandResult rejectedResult(RejectionReason reason, State current) {
        Snapshot snapshot = snapshotOf(current);
        return new CommandResult(false, 0, 0, current.revision, current.revision, reason,
                PlayerInventory.StackSnapshot.EMPTY, PlayerInventory.StackSnapshot.EMPTY,
                List.of(), snapshot);
    }

    private StagedResult rejectedStage(LogicalCommand command, State current,
            RejectionReason reason) {
        Snapshot snapshot = snapshotOf(current);
        return new StagedResult(command, current, current.copy(), snapshot, snapshot, false, reason,
                0, 0, PlayerInventory.StackSnapshot.EMPTY, PlayerInventory.StackSnapshot.EMPTY,
                List.of());
    }

    private synchronized State captureState() {
        return new State(itemType.clone(), count.clone(), durability.clone(), enchantments.clone(),
                mapIds.clone(), shulkerIds.clone(), bucketMobData.clone(),
                itemComponentData.clone(), persistenceRevision, revisionState);
    }

    private Snapshot snapshotOf(State state) {
        return snapshotOf(state, state.revision);
    }

    private Snapshot snapshotOf(State state, long revision) {
        PlayerInventory.StackSnapshot[] stacks = new PlayerInventory.StackSnapshot[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) stacks[slot] = stackAtState(state, slot);
        return new Snapshot(stacks, revision);
    }

    private static PlayerInventory.StackSnapshot stackAtState(State state, int slot) {
        if (!valid(slot) || state.itemType[slot] == PlayerInventory.EMPTY || state.count[slot] <= 0) {
            return PlayerInventory.StackSnapshot.EMPTY;
        }
        return new PlayerInventory.StackSnapshot(state.itemType[slot], state.count[slot],
                state.durability[slot], state.enchantments[slot], state.mapIds[slot],
                state.shulkerIds[slot], state.bucketMobData[slot], state.itemComponentData[slot]);
    }

    private static PlayerInventory.StackSnapshot withCount(PlayerInventory.StackSnapshot stack,
            int count) {
        if (stack == null || stack.isEmpty() || count <= 0) return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(stack.itemType(), count, stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData());
    }

    private static void writeStack(State state, int slot, PlayerInventory.StackSnapshot stack) {
        if (stack == null || stack.isEmpty()) {
            clearState(state, slot);
            return;
        }
        state.itemType[slot] = stack.itemType();
        state.count[slot] = stack.count();
        state.durability[slot] = stack.durability();
        state.enchantments[slot] = stack.enchantments();
        state.mapIds[slot] = stack.mapId();
        state.shulkerIds[slot] = stack.shulkerId();
        state.bucketMobData[slot] = stack.bucketMobData();
        state.itemComponentData[slot] = stack.itemComponentData();
    }

    private static void clearState(State state, int slot) {
        state.itemType[slot] = PlayerInventory.EMPTY;
        state.count[slot] = 0;
        state.durability[slot] = 0;
        state.enchantments[slot] = EnchantmentRules.EMPTY_ENCHANTMENTS;
        state.mapIds[slot] = 0;
        state.shulkerIds[slot] = 0;
        state.bucketMobData[slot] = null;
        state.itemComponentData[slot] = null;
    }

    private void installArrays(State state) {
        System.arraycopy(state.itemType, 0, itemType, 0, SLOTS);
        System.arraycopy(state.count, 0, count, 0, SLOTS);
        System.arraycopy(state.durability, 0, durability, 0, SLOTS);
        System.arraycopy(state.enchantments, 0, enchantments, 0, SLOTS);
        System.arraycopy(state.mapIds, 0, mapIds, 0, SLOTS);
        System.arraycopy(state.shulkerIds, 0, shulkerIds, 0, SLOTS);
        System.arraycopy(state.bucketMobData, 0, bucketMobData, 0, SLOTS);
        System.arraycopy(state.itemComponentData, 0, itemComponentData, 0, SLOTS);
    }

    private static boolean sameIdentity(PlayerInventory.StackSnapshot left,
            PlayerInventory.StackSnapshot right) {
        return left.itemType() == right.itemType()
                && left.durability() == right.durability()
                && left.enchantments() == right.enchantments()
                && left.mapId() == right.mapId()
                && left.shulkerId() == right.shulkerId()
                && Objects.equals(left.bucketMobData(), right.bucketMobData())
                && Objects.equals(left.itemComponentData(), right.itemComponentData());
    }

    /** 인챈트 칸에 머물 수 있는 종류: 인챈트대 입력 또는 그 결과(책을 인챈트한 마법이 부여된 책). */
    private static boolean itemSlotHolds(short type) {
        return EnchantmentRules.isTableEnchantable(type) || EnchantmentRules.isEnchantedBookItem(type);
    }

    private static boolean storedInTable(int slot, PlayerInventory.StackSnapshot stack) {
        if (stack == null) return false;
        if (stack.isEmpty()) return true;
        return supportedComponents(stack)
                && stack.count() <= slotCapacity(slot, stack.itemType())
                && (slot == ITEM_SLOT && itemSlotHolds(stack.itemType())
                        && EnchantmentRules.isValidEnchantmentMaskForItem(
                                stack.itemType(), stack.enchantments())
                        || slot == LAPIS_SLOT && stack.itemType() == PlayerInventory.LAPIS_LAZULI
                                && stack.enchantments() == EnchantmentRules.EMPTY_ENCHANTMENTS);
    }

    private static boolean workingEquals(State left, State right) {
        return Arrays.equals(left.itemType, right.itemType)
                && Arrays.equals(left.count, right.count)
                && Arrays.equals(left.durability, right.durability)
                && Arrays.equals(left.enchantments, right.enchantments)
                && Arrays.equals(left.mapIds, right.mapIds)
                && Arrays.equals(left.shulkerIds, right.shulkerIds)
                && Arrays.equals(left.bucketMobData, right.bucketMobData)
                && Arrays.equals(left.itemComponentData, right.itemComponentData);
    }

    private static boolean restorableInTable(int slot, PlayerInventory.StackSnapshot stack) {
        if (stack.isEmpty()) return true;
        return stack.count() <= slotCapacity(slot, stack.itemType())
                && (slot == ITEM_SLOT && itemSlotHolds(stack.itemType())
                        && EnchantmentRules.isValidEnchantmentMaskForItem(
                                stack.itemType(), stack.enchantments())
                        || slot == LAPIS_SLOT && stack.itemType() == PlayerInventory.LAPIS_LAZULI
                                && stack.enchantments() == EnchantmentRules.EMPTY_ENCHANTMENTS);
    }

    private static boolean sameState(State left, State right) {
        return left.revision == right.revision && left.revisionState == right.revisionState
                && workingEquals(left, right);
    }

    /** One table commit plus one future durable publication must leave TERMINAL_REVISION unused. */
    private static boolean hasTerminalReserve(long revision) {
        return revision >= 0 && revision < TERMINAL_REVISION - 1L;
    }

    private static int destinationSlot(PlayerInventory.StackSnapshot stack) {
        if (stack == null || stack.isEmpty() || !supportedComponents(stack)) return -1;
        if (accepts(ITEM_SLOT, stack)) return ITEM_SLOT;
        return accepts(LAPIS_SLOT, stack) ? LAPIS_SLOT : -1;
    }

    private static boolean valid(int slot) {
        return slot >= 0 && slot < SLOTS;
    }
}
