package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.PlayerInventory.StackSnapshot;

/**
 * 좌표에 귀속된 화로의 세 슬롯과 진행 상태입니다.
 *
 * <p>월드 틱 스레드만 변경하며, 입력·연료·출력 이동과 제련 진행을 한 곳에서 확정합니다.
 * 메뉴에서 한 논리 동작이 여러 슬롯 연산으로 풀리는 경우에는
 * {@link #beginLogicalCommand()}를 사용해 모든 연산을 분리된 상태에서 검증한 뒤 한 번만
 * 커밋해야 합니다.
 */
public final class FurnaceInventory {

    public static final int INPUT_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;
    public static final int SLOTS = 3;

    /** XP는 저장·전송 모두 signed int로 고정하고, 덧셈은 이 상한을 넘지 않게 한다. */
    public static final int MAX_XP_MILLI = Integer.MAX_VALUE;
    /** 이름을 명시적으로 읽어야 하는 호출부를 위한 동의어입니다. */
    public static final int MAX_PENDING_XP_MILLI = MAX_XP_MILLI;

    /**
     * 이 화로가 어느 변형인가. 좌표에 놓인 블록 ID가 정하며 블록이 바뀌면 그 블록 엔티티는
     * 버려지므로(틱 루프의 {@code dropFurnaceContents}) 생애 동안 불변이다.
     */
    private final FurnaceVariant variant;
    private final short[] itemType = new short[SLOTS];
    private final int[] count = new int[SLOTS];
    private final StackSnapshot[] identities = emptyIdentities();
    private int burnTicks;
    private int burnTotalTicks;
    private int cookTicks;
    private int xpMilli;
    private long persistenceRevision;
    private RestoreState restoreState = RestoreState.OPEN;

    private enum RestoreState {
        OPEN,
        BASELINE_BOUND,
        TERMINAL
    }

    /** Staged commit failure seam; it is package-private and used only by focused tests. */
    static final ThreadLocal<Runnable> AFTER_STAGED_COPY_HOOK = new ThreadLocal<>();

    public synchronized long persistenceRevision() {
        return persistenceRevision;
    }

    /** 저장된 revision 기준선을 한 번 결박합니다. {@link Long#MAX_VALUE}는 terminal 상태입니다. */
    public synchronized void restorePersistenceRevision(long persistedRevision) {
        validateRevision(persistedRevision);
        if (persistedRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "furnace persistence revision baseline must be advanceable");
        }
        if (restoreState != RestoreState.OPEN) {
            throw new IllegalStateException("furnace revision baseline can only be restored once");
        }
        persistenceRevision = persistedRevision;
        restoreState = persistedRevision == Long.MAX_VALUE
                ? RestoreState.TERMINAL : RestoreState.BASELINE_BOUND;
    }

    private void preflightRevisionCapacity() {
        if (restoreState == RestoreState.TERMINAL || persistenceRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("furnace persistence revision is exhausted");
        }
    }

    private void advancePersistenceRevision() {
        preflightRevisionCapacity();
        persistenceRevision = Math.incrementExact(persistenceRevision);
        if (persistenceRevision == Long.MAX_VALUE) restoreState = RestoreState.TERMINAL;
    }

    /** 화로 변형. 변형을 모르는 구형 호출부와 테스트를 위한 기본값이다. */
    public FurnaceInventory() {
        this(FurnaceVariant.FURNACE);
    }

    public FurnaceInventory(FurnaceVariant variant) {
        this.variant = variant == null ? FurnaceVariant.FURNACE : variant;
    }

    public FurnaceVariant variant() {
        return variant;
    }

    /** 이 화로에서 제련 한 건에 드는 틱. 화로 100, 용광로·훈연기 50. */
    public int cookTotalTicks() {
        return variant.cookTotalTicks();
    }

    /** 이 변형이 실제로 제련해 내는 산출물. 받지 않는 입력이면 0. */
    public short outputFor(short input) {
        return FurnaceRules.smeltOutput(variant, input);
    }

    public synchronized short itemType(int slot) {
        return itemType[slot];
    }

    public synchronized int count(int slot) {
        return count[slot];
    }

    public synchronized StackSnapshot stack(int slot) {
        return stack(itemType, count, identities, slot);
    }

    private static StackSnapshot[] emptyIdentities() {
        StackSnapshot[] result = new StackSnapshot[SLOTS];
        Arrays.fill(result, StackSnapshot.EMPTY);
        return result;
    }

    private static StackSnapshot plain(short type, int amount) {
        return amount == 0 ? StackSnapshot.EMPTY : new StackSnapshot(type, amount,
                PlayerInventory.initialDurability(type), 0L, 0, 0, null, null);
    }

    private static StackSnapshot stack(short[] types, int[] counts,
            StackSnapshot[] identities, int slot) {
        if (slot < 0 || slot >= SLOTS || counts[slot] == 0) return StackSnapshot.EMPTY;
        return identities[slot].withCount(counts[slot]);
    }

    private static StackSnapshot[] fullStacks(short[] types, int[] counts,
            StackSnapshot[] identities) {
        StackSnapshot[] result = new StackSnapshot[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) result[slot] = stack(types, counts, identities, slot);
        return result;
    }

    public synchronized int burnTicks() {
        return burnTicks;
    }

    public synchronized int burnTotalTicks() {
        return burnTotalTicks;
    }

    public synchronized int cookTicks() {
        return cookTicks;
    }

    public synchronized boolean burning() {
        return burnTicks > 0;
    }

    /**
     * DB 스냅샷 복원 전용 호환 경로입니다. 현재 variant/revision/xp를 유지하며 슬롯과 진행만
     * 교체합니다. 새 persistence 코드는 variant·revision·xpMilli를 함께 받는 아래 overload를
     * 사용해야 합니다.
     *
     * @deprecated 테스트와 아직 마이그레이션되지 않은 구형 호출부만을 위한 호환 overload
     */
    @Deprecated
    public synchronized void restore(short[] types, int[] counts, int savedBurnTicks,
            int savedBurnTotalTicks, int savedCookTicks) {
        preflightRevisionCapacity();
        PreparedState prepared = prepareRestore(types, counts, savedBurnTicks,
                savedBurnTotalTicks, savedCookTicks, variant, persistenceRevision, xpMilli);
        installPreparedState(prepared, false);
    }

    /**
     * 전체 current-schema 스냅샷을 원자적으로 복원합니다. 슬롯·진행·variant·revision·XP를
     * 모두 임시 값으로 검증한 뒤에만 필드와 빈 슬롯을 교체합니다.
     */
    public synchronized void restore(short[] types, int[] counts, int savedBurnTicks,
            int savedBurnTotalTicks, int savedCookTicks, FurnaceVariant savedVariant,
            long savedRevision, int savedXpMilli) {
        preflightRevisionCapacity();
        PreparedState prepared = prepareRestore(types, counts, savedBurnTicks,
                savedBurnTotalTicks, savedCookTicks, savedVariant, savedRevision, savedXpMilli);
        installPreparedState(prepared, true);
    }

    /** Persisted variant-code ingress for the persistence layer. Unknown codes are rejected. */
    public synchronized void restore(short[] types, int[] counts, int savedBurnTicks,
            int savedBurnTotalTicks, int savedCookTicks, int savedVariantCode,
            long savedRevision, int savedXpMilli) {
        restore(types, counts, savedBurnTicks, savedBurnTotalTicks, savedCookTicks,
                variantForCode(savedVariantCode), savedRevision, savedXpMilli);
    }

    /** Snapshot-shaped restore entry point used by persistence and detached world-tick plans. */
    public synchronized void restore(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("furnace snapshot is required");
        preflightRevisionCapacity();
        PreparedState prepared = prepareRestore(snapshot.itemTypes(), snapshot.counts(),
                snapshot.burnTicks(), snapshot.burnTotalTicks(), snapshot.cookTicks(),
                snapshot.variant(), snapshot.revision(), snapshot.xpMilli());
        StackSnapshot[] restored = snapshot.stacks();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (restored[slot] == null || restored[slot].itemType() != prepared.types[slot]
                    || restored[slot].count() != prepared.counts[slot]) {
                throw new IllegalArgumentException("furnace stack identity differs from slot");
            }
        }
        installPreparedState(prepared, true);
        for (int slot = 0; slot < SLOTS; slot++) {
            identities[slot] = restored[slot].isEmpty() ? StackSnapshot.EMPTY : restored[slot].withCount(1);
        }
    }

    private PreparedState prepareRestore(short[] types, int[] counts, int savedBurnTicks,
            int savedBurnTotalTicks, int savedCookTicks, FurnaceVariant savedVariant,
            long savedRevision, int savedXpMilli) {
        if (types == null || counts == null || types.length != SLOTS || counts.length != SLOTS) {
            throw new IllegalStateException("화로 스냅샷은 정확히 3칸이어야 합니다.");
        }
        if (savedVariant == null || savedVariant != variant) {
            throw new IllegalStateException("화로 저장 변형이 현재 블록과 다릅니다.");
        }
        validateRevision(savedRevision);
        validateXpMilli(savedXpMilli);

        // These arrays start empty, so an accepted restore also clears every old slot that is
        // empty in the incoming snapshot. No live field is touched during validation.
        short[] sourceTypes = types.clone();
        int[] sourceCounts = counts.clone();
        short[] preparedTypes = new short[SLOTS];
        int[] preparedCounts = new int[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            short type = sourceTypes[slot];
            int amount = sourceCounts[slot];
            if (type == PlayerInventory.EMPTY || amount == 0) {
                if (type != PlayerInventory.EMPTY || amount != 0) {
                    throw new IllegalStateException("화로 빈 슬롯 값이 올바르지 않습니다: " + slot);
                }
                continue;
            }
            if (!PlayerInventory.isRegisteredItemType(type)
                    || amount < 1 || amount > PlayerInventory.stackMax(type)
                    || slot == INPUT_SLOT && outputFor(type) == PlayerInventory.EMPTY
                    || slot == FUEL_SLOT && FurnaceRules.fuelTicks(type) == 0
                    || slot == OUTPUT_SLOT && !FurnaceRules.isSmeltOutput(type)) {
                throw new IllegalStateException("화로 저장 슬롯이 올바르지 않습니다: slot=" + slot
                        + ", type=" + Short.toUnsignedInt(type) + ", count=" + amount);
            }
            preparedTypes[slot] = type;
            preparedCounts[slot] = amount;
        }
        validateProgress(savedBurnTicks, savedBurnTotalTicks, savedCookTicks, preparedTypes);
        return new PreparedState(preparedTypes, preparedCounts, savedBurnTicks,
                savedBurnTotalTicks, savedCookTicks, savedRevision, savedXpMilli,
                savedRevision == Long.MAX_VALUE ? RestoreState.TERMINAL
                        : RestoreState.BASELINE_BOUND);
    }

    private void validateProgress(int savedBurnTicks, int savedBurnTotalTicks,
            int savedCookTicks, short[] preparedTypes) {
        if (savedBurnTicks < 0 || savedBurnTotalTicks < 0
                || savedBurnTicks > savedBurnTotalTicks
                || savedBurnTicks == 0 && savedBurnTotalTicks != 0
                || savedBurnTicks > 0 && savedBurnTotalTicks == 0
                || savedCookTicks < 0 || savedCookTicks >= cookTotalTicks()
                || preparedTypes[INPUT_SLOT] == PlayerInventory.EMPTY && savedCookTicks != 0) {
            throw new IllegalStateException("화로 저장 진행 값이 올바르지 않습니다.");
        }
    }

    private void installPreparedState(PreparedState prepared, boolean bindRevision) {
        System.arraycopy(prepared.types, 0, itemType, 0, SLOTS);
        System.arraycopy(prepared.counts, 0, count, 0, SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            identities[slot] = plain(itemType[slot], count[slot] == 0 ? 0 : 1);
        }
        burnTicks = prepared.burnTicks;
        burnTotalTicks = prepared.burnTotalTicks;
        cookTicks = prepared.cookTicks;
        xpMilli = prepared.xpMilli;
        if (bindRevision) {
            persistenceRevision = prepared.revision;
            restoreState = prepared.restoreState;
        }
    }

    private static void validateRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("furnace persistence revision must be non-negative");
        }
    }

    private static void validateXpMilli(int value) {
        if (value < 0 || value > MAX_XP_MILLI) {
            throw new IllegalArgumentException("furnace xpMilli is outside its non-negative bound");
        }
    }

    private static FurnaceVariant variantForCode(int code) {
        for (FurnaceVariant candidate : FurnaceVariant.values()) {
            if (candidate.code() == code) return candidate;
        }
        throw new IllegalStateException("화로 저장 변형 코드가 올바르지 않습니다: " + code);
    }

    /** 인벤토리 칸의 종류가 들어갈 화로 칸. 들어갈 수 없으면 -1입니다. */
    public synchronized int destinationSlot(short type) {
        if (outputFor(type) != PlayerInventory.EMPTY) return INPUT_SLOT;
        return FurnaceRules.fuelTicks(type) > 0 ? FUEL_SLOT : -1;
    }

    public synchronized int roomFor(int slot, short type) {
        return roomFor(itemType, count, slot, type);
    }

    private int roomFor(short[] types, int[] counts, int slot, short type) {
        if (slot != INPUT_SLOT && slot != FUEL_SLOT) return 0;
        if (!PlayerInventory.isRegisteredItemType(type) || type == PlayerInventory.EMPTY) return 0;
        if (slot == INPUT_SLOT && outputFor(type) == PlayerInventory.EMPTY) return 0;
        if (slot == FUEL_SLOT && FurnaceRules.fuelTicks(type) == 0) return 0;
        if (types[slot] != PlayerInventory.EMPTY && types[slot] != type) return 0;
        return Math.max(0, PlayerInventory.stackMax(type) - counts[slot]);
    }

    public synchronized int roomFor(int slot, StackSnapshot incoming) {
        if (incoming == null || incoming.isEmpty()) return 0;
        StackSnapshot current = stack(slot);
        return !current.isEmpty() && !current.sameIdentity(incoming) ? 0
                : roomFor(itemType, count, slot, incoming.itemType());
    }

    /** Validated stacks retain their complete identity until actual consumption. */
    public synchronized int add(int slot, short type, int amount) {
        if (amount <= 0 || type == PlayerInventory.EMPTY || !PlayerInventory.isRegisteredItemType(type)) return 0;
        return add(slot, plain(type, Math.min(amount, PlayerInventory.stackMax(type))), amount);
    }

    public synchronized int add(int slot, StackSnapshot incoming, int amount) {
        if (incoming == null) return 0;
        int moved = Math.min(Math.min(Math.max(amount, 0), incoming.count()), roomFor(slot, incoming));
        if (moved == 0) return 0;
        preflightRevisionCapacity();
        itemType[slot] = incoming.itemType();
        identities[slot] = incoming.withCount(1);
        count[slot] += moved;
        advancePersistenceRevision();
        return moved;
    }

    public static boolean isTypeCountCarrier(PlayerInventory.StackSnapshot stack) {
        return stack != null && !stack.isEmpty()
                && stack.durability() == PlayerInventory.initialDurability(stack.itemType())
                && stack.enchantments() == 0 && stack.mapId() == 0 && stack.shulkerId() == 0
                && stack.bucketMobData() == null && stack.itemComponentData() == null;
    }

    /** 지정 슬롯에서 최대 amount개를 꺼냅니다. */
    public synchronized int take(int slot, int amount) {
        if (slot < 0 || slot >= SLOTS || amount <= 0) return 0;
        int moved = Math.min(count[slot], amount);
        if (moved == 0) return 0;
        preflightRevisionCapacity();
        takeWithoutRevision(slot, moved);
        advancePersistenceRevision();
        return moved;
    }

    private int takeWithoutRevision(int slot, int amount) {
        int moved = Math.min(count[slot], amount);
        count[slot] -= moved;
        if (count[slot] == 0) clearSlot(slot);
        if (slot == INPUT_SLOT && moved > 0) cookTicks = 0;
        return moved;
    }

    private void clearSlot(int slot) {
        itemType[slot] = PlayerInventory.EMPTY;
        count[slot] = 0;
        identities[slot] = StackSnapshot.EMPTY;
    }

    /**
     * 한 프로젝트 틱을 진행합니다.
     *
     * @return 슬롯·불·조리 진행 중 하나라도 바뀌었으면 true
     */
    public synchronized boolean tick() {
        preflightRevisionCapacity();
        boolean changed = false;
        short output = outputFor(itemType[INPUT_SLOT]);
        boolean canCook = output != PlayerInventory.EMPTY && canAcceptOutput(output);
        if (burnTicks > 0 && canCook && cookTicks + 1 >= cookTotalTicks()) {
            validateXpAddition(output);
        }

        if (burnTicks > 0) {
            burnTicks--;
            changed = true;
            if (burnTicks == 0) burnTotalTicks = 0;
        }

        if (burnTicks == 0 && canCook) {
            // 26.3 cooking/time_* divides new fuel duration for fast-cooking blocks.
            // Persisted remaining/total timers retain their original actual-tick unit.
            int fuel = FurnaceRules.fuelTicks(itemType[FUEL_SLOT])
                    / (FurnaceRules.TICKS_PER_SMELT / cookTotalTicks());
            if (fuel > 0 && count[FUEL_SLOT] > 0) {
                takeWithoutRevision(FUEL_SLOT, 1);
                burnTicks = fuel;
                burnTotalTicks = fuel;
                changed = true;
            }
        }

        if (burnTicks > 0 && canCook) {
            cookTicks++;
            changed = true;
            if (cookTicks >= cookTotalTicks()) {
                takeWithoutRevision(INPUT_SLOT, 1);
                addOutput(output);
                cookTicks = 0;
            }
        } else if (cookTicks != 0) {
            cookTicks = 0;
            changed = true;
        }
        if (changed) advancePersistenceRevision();
        return changed;
    }

    /** 다시 틱할 가능성이 있는 상태인지 확인합니다. */
    public synchronized boolean needsTick() {
        short output = outputFor(itemType[INPUT_SLOT]);
        return burnTicks > 0
                || output != PlayerInventory.EMPTY
                        && canAcceptOutput(output)
                        && FurnaceRules.fuelTicks(itemType[FUEL_SLOT]) > 0
                        && count[FUEL_SLOT] > 0;
    }

    /**
     * 블록 엔티티를 버려도 되는가. 정산되지 않은 제련 경험치가 남아 있으면 비어 있지 않다 —
     * 마지막 아이템을 꺼낸 순간 폐기하면 1 XP 미만의 나머지가 블록 엔티티와 함께 사라진다.
     */
    public synchronized boolean isEmpty() {
        return count[INPUT_SLOT] == 0 && count[FUEL_SLOT] == 0 && count[OUTPUT_SLOT] == 0
                && burnTicks == 0 && burnTotalTicks == 0 && cookTicks == 0 && xpMilli == 0;
    }

    private boolean canAcceptOutput(short output) {
        return itemType[OUTPUT_SLOT] == PlayerInventory.EMPTY
                || itemType[OUTPUT_SLOT] == output
                        && stack(OUTPUT_SLOT).sameIdentity(plain(output, 1))
                        && count[OUTPUT_SLOT] < PlayerInventory.stackMax(output);
    }

    private void validateXpAddition(short output) {
        int delta = smeltXpDelta(output);
        if (delta > MAX_XP_MILLI - xpMilli) {
            throw new IllegalStateException("furnace xpMilli would exceed its bound");
        }
    }

    private int smeltXpDelta(short output) {
        long scaled = (long) XpRules.smeltXpMilli(output) * variant.xpMultiplierMilli()
                / XpRules.MILLI;
        if (scaled < 0 || scaled > MAX_XP_MILLI) {
            throw new IllegalStateException("furnace smelt XP is outside its bound");
        }
        return (int) scaled;
    }

    private void addOutput(short output) {
        if (itemType[OUTPUT_SLOT] == PlayerInventory.EMPTY) {
            itemType[OUTPUT_SLOT] = output;
            identities[OUTPUT_SLOT] = plain(output, 1);
        }
        count[OUTPUT_SLOT]++;
        // 제련 경험치는 밀리 단위로 화로에 쌓아 두고 결과 칸을 꺼낼 때 정산한다.
        xpMilli += smeltXpDelta(output);
    }

    /** 아직 정산되지 않은 제련 XP(milli)의 정확한 값입니다. */
    public synchronized int xpMilli() {
        return xpMilli;
    }

    /** [SURV-X] 아직 정산되지 않은 제련 경험치(밀리). 기존 호출부 호환 이름입니다. */
    public synchronized int pendingSmeltXpMilli() {
        return xpMilli;
    }

    /**
     * [SURV-X] 쌓인 제련 경험치를 정산합니다. 정수 부분만 지급하고 소수는 화로에 남습니다.
     * 결과 칸에서 아이템을 실제로 꺼낸 뒤 한 번만 호출하세요.
     */
    public synchronized int collectSmeltXp() {
        int awarded = XpRules.collectedSmeltXp(xpMilli);
        if (awarded == 0) return 0;
        preflightRevisionCapacity();
        xpMilli = XpRules.remainingSmeltXpMilli(xpMilli);
        advancePersistenceRevision();
        return awarded;
    }

    /**
     * 슬롯 드랍과 pending XP를 하나의 원자적 결과로 제거합니다. XP는 정수부가 아니라 milli
     * 그대로 반환되므로 마지막 fractional remainder도 유실되지 않습니다.
     */
    public synchronized DrainResult drainAllWithXp() {
        boolean changed = !isEmpty();
        if (!changed) return DrainResult.EMPTY;
        preflightRevisionCapacity();
        List<StoredStack> stacks = drainStacks();
        int drainedXpMilli = xpMilli;
        burnTicks = 0;
        burnTotalTicks = 0;
        cookTicks = 0;
        xpMilli = 0;
        advancePersistenceRevision();
        return new DrainResult(stacks, drainedXpMilli);
    }

    /** Alias used by removal code that wants to make XP ownership explicit. */
    public synchronized DrainResult removeAll() {
        return drainAllWithXp();
    }

    /** Alias for callers that name the returned value rather than the operation. */
    public synchronized DrainResult drainAllResult() {
        return drainAllWithXp();
    }

    /**
     * @deprecated 구형 List 반환 호출부 호환용입니다. XP까지 필요한 production removal은
     * {@link #drainAllWithXp()}를 사용해야 합니다. 이 overload는 XP를 남겨 accessor로 노출해
     * silent loss를 만들지 않습니다.
     */
    @Deprecated
    public synchronized List<StoredStack> drainAll() {
        boolean changed = count[INPUT_SLOT] != 0 || count[FUEL_SLOT] != 0
                || count[OUTPUT_SLOT] != 0 || burnTicks != 0 || burnTotalTicks != 0
                || cookTicks != 0;
        if (!changed) return List.of();
        preflightRevisionCapacity();
        List<StoredStack> out = drainStacks();
        burnTicks = 0;
        burnTotalTicks = 0;
        cookTicks = 0;
        // Keep xpMilli available to an old caller; it must migrate to drainAllWithXp() before
        // the furnace object is discarded.
        advancePersistenceRevision();
        return out;
    }

    private List<StoredStack> drainStacks() {
        List<StoredStack> out = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemType[slot] != PlayerInventory.EMPTY && count[slot] > 0) {
                out.add(new StoredStack(stack(slot)));
            }
            clearSlot(slot);
        }
        return List.copyOf(out);
    }

    /** 전체 상태의 detached 복사본입니다. persistence와 WorldTick 계획 모두 이 값을 사용합니다. */
    public synchronized FurnaceInventory detachedInventory() {
        FurnaceInventory copy = new FurnaceInventory(variant);
        copy.restore(snapshot());
        return copy;
    }

    /** 슬롯·진행·변형·revision·XP를 한 지점에서 캡처합니다. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(itemType, count, burnTicks, burnTotalTicks, cookTicks,
                variant, persistenceRevision, xpMilli, fullStacks(itemType, count, identities));
    }

    /** persistence naming alias. */
    public synchronized Snapshot persistenceSnapshot() {
        return snapshot();
    }

    /** 한 논리 메뉴 명령을 분리된 copy에서 시작합니다. */
    public synchronized StagedCommand beginLogicalCommand() {
        preflightRevisionCapacity();
        return new StagedCommand(itemType, count, burnTicks, burnTotalTicks, cookTicks,
                xpMilli, persistenceRevision, restoreState);
    }

    /** 명시적 stage 이름의 factory alias입니다. */
    public synchronized StagedCommand stageLogicalCommand() {
        return beginLogicalCommand();
    }

    /** 짧은 factory alias입니다. */
    public synchronized StagedCommand stage() {
        return beginLogicalCommand();
    }

    /** Callback 예외는 live furnace에 닿기 전에 staged 상태만 폐기합니다. */
    public boolean applyLogicalCommand(Consumer<StagedCommand> command) {
        Objects.requireNonNull(command, "furnace logical command is required");
        StagedCommand staged = beginLogicalCommand();
        try {
            command.accept(staged);
            return staged.commit();
        } catch (RuntimeException | Error failure) {
            staged.rollback();
            throw failure;
        }
    }

    /** logical-command API의 읽기 쉬운 동의어입니다. */
    public boolean applyStagedCommand(Consumer<StagedCommand> command) {
        return applyLogicalCommand(command);
    }

    /**
     * 슬롯 연산을 local copy에 쌓아 두는 command입니다. {@link #commit()} 전에는 live furnace의
     * 슬롯·XP·revision을 전혀 건드리지 않으며, commit은 변경이 있어도 revision을 정확히 한 번만
     * 증가시킵니다.
     */
    public final class StagedCommand {
        private final short[] originTypes;
        private final int[] originCounts;
        private final short[] stagedTypes;
        private final int[] stagedCounts;
        private final StackSnapshot[] originIdentities;
        private final StackSnapshot[] stagedIdentities;
        private int stagedBurnTicks;
        private int stagedBurnTotalTicks;
        private int stagedCookTicks;
        private int stagedXpMilli;
        private final int originBurnTicks;
        private final int originBurnTotalTicks;
        private final int originCookTicks;
        private final int originXpMilli;
        private final long baseRevision;
        private final RestoreState baseRestoreState;
        private boolean rejected;
        private boolean finished;
        private boolean committed;

        private StagedCommand(short[] types, int[] counts, int burnTicks, int burnTotalTicks,
                int cookTicks, int xpMilli, long baseRevision, RestoreState restoreState) {
            this.originTypes = types.clone();
            this.originCounts = counts.clone();
            this.stagedTypes = types.clone();
            this.stagedCounts = counts.clone();
            this.originIdentities = identities.clone();
            this.stagedIdentities = identities.clone();
            this.stagedBurnTicks = burnTicks;
            this.stagedBurnTotalTicks = burnTotalTicks;
            this.stagedCookTicks = cookTicks;
            this.stagedXpMilli = xpMilli;
            this.originBurnTicks = burnTicks;
            this.originBurnTotalTicks = burnTotalTicks;
            this.originCookTicks = cookTicks;
            this.originXpMilli = xpMilli;
            this.baseRevision = baseRevision;
            this.baseRestoreState = restoreState;
        }

        public int itemCount(int slot) {
            checkOpen();
            return slot < 0 || slot >= SLOTS ? 0 : stagedCounts[slot];
        }

        public short itemType(int slot) {
            checkOpen();
            return slot < 0 || slot >= SLOTS ? PlayerInventory.EMPTY : stagedTypes[slot];
        }

        public int roomFor(int slot, short type) {
            checkOpen();
            return slot < 0 || slot >= SLOTS ? 0
                    : FurnaceInventory.this.roomFor(stagedTypes, stagedCounts, slot, type);
        }

        public StackSnapshot stack(int slot) {
            checkOpen();
            return FurnaceInventory.stack(stagedTypes, stagedCounts, stagedIdentities, slot);
        }

        public int add(int slot, short type, int amount) {
            checkOpen();
            if (amount <= 0 || type == PlayerInventory.EMPTY || !PlayerInventory.isRegisteredItemType(type)) return 0;
            return add(slot, plain(type, Math.min(amount, PlayerInventory.stackMax(type))), amount);
        }

        public int add(int slot, StackSnapshot incoming, int amount) {
            checkOpen();
            if (incoming == null || incoming.isEmpty()) return 0;
            StackSnapshot current = stack(slot);
            if (!current.isEmpty() && !current.sameIdentity(incoming)) return 0;
            int moved = Math.min(Math.min(Math.max(amount, 0), incoming.count()),
                    roomFor(slot, incoming.itemType()));
            if (moved == 0) return 0;
            stagedTypes[slot] = incoming.itemType();
            stagedIdentities[slot] = incoming.withCount(1);
            stagedCounts[slot] += moved;
            return moved;
        }

        public int take(int slot, int amount) {
            checkOpen();
            if (slot < 0 || slot >= SLOTS || amount <= 0) return 0;
            int moved = Math.min(stagedCounts[slot], amount);
            if (moved == 0) return 0;
            stagedCounts[slot] -= moved;
            if (stagedCounts[slot] == 0) {
                stagedTypes[slot] = PlayerInventory.EMPTY;
                stagedIdentities[slot] = StackSnapshot.EMPTY;
            }
            if (slot == INPUT_SLOT) stagedCookTicks = 0;
            return moved;
        }

        /** 지정 슬롯을 검증된 componentless 스택으로 통째로 교체합니다. */
        public boolean replace(int slot, short type, int amount) {
            checkOpen();
            if (slot < 0 || slot >= SLOTS) return false;
            if (type == PlayerInventory.EMPTY) {
                if (amount != 0) return false;
                stagedTypes[slot] = PlayerInventory.EMPTY;
                stagedCounts[slot] = 0;
                stagedIdentities[slot] = StackSnapshot.EMPTY;
                if (slot == INPUT_SLOT) stagedCookTicks = 0;
                return true;
            }
            if (!PlayerInventory.isRegisteredItemType(type) || amount < 1
                    || amount > PlayerInventory.stackMax(type)
                    || slot == INPUT_SLOT && outputFor(type) == PlayerInventory.EMPTY
                    || slot == FUEL_SLOT && FurnaceRules.fuelTicks(type) == 0
                    || slot == OUTPUT_SLOT && !FurnaceRules.isSmeltOutput(type)) return false;
            stagedTypes[slot] = type;
            stagedCounts[slot] = amount;
            stagedIdentities[slot] = plain(type, 1);
            if (slot == INPUT_SLOT) stagedCookTicks = 0;
            return true;
        }

        public boolean replace(int slot, PlayerInventory.StackSnapshot stack) {
            checkOpen();
            if (stack == null || !replace(slot, stack.itemType(), stack.count())) return false;
            stagedIdentities[slot] = stack.isEmpty() ? StackSnapshot.EMPTY : stack.withCount(1);
            return true;
        }

        public boolean clear(int slot) {
            return replace(slot, PlayerInventory.EMPTY, 0);
        }

        /** 명령을 의도적으로 거부하고 live 상태를 보존합니다. */
        public void reject() {
            checkOpen();
            rejected = true;
        }

        public boolean commit() {
            checkOpen();
            synchronized (FurnaceInventory.this) {
                    if (rejected) {
                        finished = true;
                        return false;
                }
                if (!matchesBaseState()) {
                    finished = true;
                    throw new IllegalStateException("furnace staged command is stale");
                }
                if (!hasChanges()) {
                    finished = true;
                    return false;
                }
                preflightRevisionCapacity();
                short[] oldTypes = itemType.clone();
                int[] oldCounts = count.clone();
                StackSnapshot[] oldIdentities = identities.clone();
                int oldBurnTicks = burnTicks;
                int oldBurnTotalTicks = burnTotalTicks;
                int oldCookTicks = cookTicks;
                int oldXpMilli = xpMilli;
                long oldRevision = persistenceRevision;
                RestoreState oldRestoreState = restoreState;
                try {
                    System.arraycopy(stagedTypes, 0, itemType, 0, SLOTS);
                    System.arraycopy(stagedCounts, 0, count, 0, SLOTS);
                    System.arraycopy(stagedIdentities, 0, identities, 0, SLOTS);
                    burnTicks = stagedBurnTicks;
                    burnTotalTicks = stagedBurnTotalTicks;
                    cookTicks = stagedCookTicks;
                    xpMilli = stagedXpMilli;
                    Runnable hook = AFTER_STAGED_COPY_HOOK.get();
                    if (hook != null) hook.run();
                    advancePersistenceRevision();
                    committed = true;
                    finished = true;
                    return true;
                } catch (RuntimeException | Error failure) {
                    System.arraycopy(oldTypes, 0, itemType, 0, SLOTS);
                    System.arraycopy(oldCounts, 0, count, 0, SLOTS);
                    System.arraycopy(oldIdentities, 0, identities, 0, SLOTS);
                    burnTicks = oldBurnTicks;
                    burnTotalTicks = oldBurnTotalTicks;
                    cookTicks = oldCookTicks;
                    xpMilli = oldXpMilli;
                    persistenceRevision = oldRevision;
                    restoreState = oldRestoreState;
                    finished = true;
                    throw failure;
                }
            }
        }

        public void rollback() {
            if (!finished) finished = true;
        }

        public boolean committed() {
            return committed;
        }

        private boolean hasChanges() {
            return !Arrays.equals(originIdentities, stagedIdentities)
                    || !Arrays.equals(originTypes, stagedTypes)
                    || !Arrays.equals(originCounts, stagedCounts)
                    || stagedBurnTicks != originBurnTicks
                    || stagedBurnTotalTicks != originBurnTotalTicks
                    || stagedCookTicks != originCookTicks
                    || stagedXpMilli != originXpMilli;
        }

        private boolean matchesBaseState() {
            return persistenceRevision == baseRevision && restoreState == baseRestoreState
                    && xpMilli == originXpMilli && burnTicks == originBurnTicks
                    && burnTotalTicks == originBurnTotalTicks && cookTicks == originCookTicks
                    && Arrays.equals(itemType, originTypes) && Arrays.equals(count, originCounts)
                    && Arrays.equals(identities, originIdentities);
        }

        private void checkOpen() {
            if (finished) throw new IllegalStateException("furnace staged command is closed");
        }
    }

    /** 전체 furnace 상태의 불변 값 사본입니다. 배열 accessor는 매번 clone을 반환합니다. */
    public record Snapshot(short[] itemTypes, int[] counts, int burnTicks, int burnTotalTicks,
            int cookTicks, FurnaceVariant variant, long revision, int xpMilli, StackSnapshot[] stacks) {
        public Snapshot(short[] itemTypes, int[] counts, int burnTicks, int burnTotalTicks,
                int cookTicks, FurnaceVariant variant, long revision, int xpMilli) {
            this(itemTypes, counts, burnTicks, burnTotalTicks, cookTicks, variant, revision, xpMilli, null);
        }

        public Snapshot withRevision(long nextRevision) {
            return new Snapshot(itemTypes, counts, burnTicks, burnTotalTicks, cookTicks,
                    variant, nextRevision, xpMilli, stacks);
        }

        public Snapshot {
            itemTypes = itemTypes == null ? null : itemTypes.clone();
            counts = counts == null ? null : counts.clone();
            if (stacks == null && itemTypes != null && counts != null
                    && itemTypes.length == SLOTS && counts.length == SLOTS) {
                stacks = new StackSnapshot[SLOTS];
                for (int slot = 0; slot < SLOTS; slot++) stacks[slot] = plain(itemTypes[slot], counts[slot]);
            } else if (stacks != null) {
                if (stacks.length != SLOTS) throw new IllegalArgumentException("three furnace stacks required");
                stacks = stacks.clone();
            }
        }

        @Override public StackSnapshot[] stacks() { return stacks == null ? null : stacks.clone(); }

        @Override public short[] itemTypes() {
            return itemTypes == null ? null : itemTypes.clone();
        }

        @Override public int[] counts() {
            return counts == null ? null : counts.clone();
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Snapshot that)) return false;
            return burnTicks == that.burnTicks && burnTotalTicks == that.burnTotalTicks
                    && cookTicks == that.cookTicks && revision == that.revision
                    && xpMilli == that.xpMilli && variant == that.variant
                    && Arrays.equals(itemTypes, that.itemTypes)
                    && Arrays.equals(counts, that.counts)
                    && Arrays.equals(stacks, that.stacks);
        }

        @Override public int hashCode() {
            int result = Arrays.hashCode(itemTypes);
            result = 31 * result + Arrays.hashCode(counts);
            result = 31 * result + burnTicks;
            result = 31 * result + burnTotalTicks;
            result = 31 * result + cookTicks;
            result = 31 * result + Objects.hashCode(variant);
            result = 31 * result + Long.hashCode(revision);
            return 31 * (31 * result + xpMilli) + Arrays.hashCode(stacks);
        }
    }

    private record PreparedState(short[] types, int[] counts, int burnTicks, int burnTotalTicks,
            int cookTicks, long revision, int xpMilli, RestoreState restoreState) {
    }

    /** 슬롯 드랍과 pending XP를 함께 운반하는 제거 결과입니다. */
    public static final class DrainResult {
        private static final DrainResult EMPTY = new DrainResult(List.of(), 0);
        private final List<StoredStack> stacks;
        private final int xpMilli;

        private DrainResult(List<StoredStack> stacks, int xpMilli) {
            this.stacks = List.copyOf(stacks);
            validateXpMilli(xpMilli);
            this.xpMilli = xpMilli;
        }

        public List<StoredStack> stacks() {
            return stacks;
        }

        public List<StoredStack> contents() {
            return stacks;
        }

        public List<StoredStack> items() {
            return stacks;
        }

        public int xpMilli() {
            return xpMilli;
        }

        public int pendingSmeltXpMilli() {
            return xpMilli;
        }

        public int experienceMilli() {
            return xpMilli;
        }

        public int wholeXp() {
            return XpRules.collectedSmeltXp(xpMilli);
        }

        public int fractionalXpMilli() {
            return XpRules.remainingSmeltXpMilli(xpMilli);
        }

        public boolean isEmpty() {
            return stacks.isEmpty() && xpMilli == 0;
        }
    }

    public static final class StoredStack {
        private final StackSnapshot stack;

        public StoredStack(short itemType, int count) { this(plain(itemType, count)); }
        public StoredStack(StackSnapshot stack) { this.stack = Objects.requireNonNull(stack); }
        public StackSnapshot stack() { return stack; }
        public short itemType() { return stack.itemType(); }
        public int count() { return stack.count(); }
    }
}
