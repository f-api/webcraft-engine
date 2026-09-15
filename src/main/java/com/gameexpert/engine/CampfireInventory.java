package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.gameexpert.engine.inventory.PlayerInventory;

/** 좌표에 귀속된 모닥불의 서로 독립적인 음식 네 칸과 조리 진행 상태입니다. */
public final class CampfireInventory {

    public static final int SLOTS = 4;
    public static final int COOK_TOTAL_TICKS = CampfireRules.COOK_TICKS;

    private static final TickResult UNCHANGED =
            new TickResult(false, false, List.of());
    private static final TickResult PROGRESSED =
            new TickResult(true, false, List.of());
    private static final TickResult FINISHED_COOLING =
            new TickResult(true, true, List.of());

    private final short[] itemTypes = new short[SLOTS];
    private final int[] cookTicks = new int[SLOTS];
    private long persistenceRevision;

    public long persistenceRevision() {
        return persistenceRevision;
    }

    public void restorePersistenceRevision(long persistedRevision) {
        if (persistedRevision < 0 || persistedRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("campfire persistence revision must be non-negative");
        }
        if (persistenceRevision != 0 && persistenceRevision != persistedRevision) {
            throw new IllegalStateException("campfire revision baseline can only be restored once");
        }
        persistenceRevision = persistedRevision;
    }

    private void advancePersistenceRevision() {
        persistenceRevision = Math.incrementExact(persistenceRevision);
    }

    public short itemType(int slot) {
        return itemTypes[slot];
    }

    public int cookTicks(int slot) {
        return cookTicks[slot];
    }

    /** DB 스냅샷 복원 전용. 빈 모닥불 행과 허용되지 않은 음식은 손상으로 취급합니다. */
    public void restore(short[] savedItemTypes, int[] savedCookTicks) {
        if (savedItemTypes == null || savedCookTicks == null
                || savedItemTypes.length != SLOTS || savedCookTicks.length != SLOTS) {
            throw new IllegalStateException("모닥불 스냅샷은 정확히 4칸이어야 합니다.");
        }
        Arrays.fill(itemTypes, PlayerInventory.EMPTY);
        Arrays.fill(cookTicks, 0);
        boolean occupied = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            short type = savedItemTypes[slot];
            int progress = savedCookTicks[slot];
            if (type == PlayerInventory.EMPTY) {
                if (progress != 0) {
                    throw new IllegalStateException("빈 모닥불 칸의 진행 값이 올바르지 않습니다: " + slot);
                }
                continue;
            }
            if (!CampfireRules.isCookable(type)
                    || progress < 0 || progress >= COOK_TOTAL_TICKS) {
                throw new IllegalStateException("모닥불 저장 칸이 올바르지 않습니다: slot=" + slot
                        + ", type=" + Short.toUnsignedInt(type) + ", cookTicks=" + progress);
            }
            itemTypes[slot] = type;
            cookTicks[slot] = progress;
            occupied = true;
        }
        if (!occupied) {
            throw new IllegalStateException("빈 모닥불은 영속 상태를 가질 수 없습니다.");
        }
    }

    public int firstFreeSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemTypes[slot] == PlayerInventory.EMPTY) return slot;
        }
        return -1;
    }

    /** 첫 빈 칸에 음식 하나를 올리고 슬롯 번호를 반환합니다. 불가하거나 가득 찼으면 -1입니다. */
    public int addFirst(short itemType) {
        if (!CampfireRules.isCookable(itemType)) return -1;
        int slot = firstFreeSlot();
        if (slot < 0) return -1;
        itemTypes[slot] = itemType;
        cookTicks[slot] = 0;
        advancePersistenceRevision();
        return slot;
    }

    public boolean occupied() {
        return occupiedCount() > 0;
    }

    public int occupiedCount() {
        int count = 0;
        for (short itemType : itemTypes) {
            if (itemType != PlayerInventory.EMPTY) count++;
        }
        return count;
    }

    /** 소화 뒤 아직 식혀야 할 조리 진행도가 남아 있는지 반환합니다. */
    public boolean needsCooldown() {
        for (int progress : cookTicks) {
            if (progress > 0) return true;
        }
        return false;
    }

    /**
     * 한 프로젝트 틱을 진행합니다. 점화 중에는 네 칸이 독립 진행하고, 소화 뒤에는 바닐라처럼
     * 진행도가 두 배 속도로 서서히 감소합니다.
     */
    public TickResult tick(boolean lit) {
        if (!lit) {
            boolean changed = false;
            for (int slot = 0; slot < SLOTS; slot++) {
                if (cookTicks[slot] == 0) continue;
                cookTicks[slot] = Math.max(
                        0, cookTicks[slot] - CampfireRules.COOL_PROGRESS_PER_TICK);
                changed = true;
            }
            if (!changed) return UNCHANGED;
            advancePersistenceRevision();
            return needsCooldown() ? PROGRESSED : FINISHED_COOLING;
        }

        List<CompletedStack> completed = null;
        boolean progressed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            short input = itemTypes[slot];
            if (input == PlayerInventory.EMPTY) continue;
            progressed = true;
            cookTicks[slot]++;
            if (cookTicks[slot] < COOK_TOTAL_TICKS) continue;

            short output = CampfireRules.cookOutput(input);
            if (completed == null) completed = new ArrayList<>(SLOTS);
            completed.add(new CompletedStack(slot, output));
            itemTypes[slot] = PlayerInventory.EMPTY;
            cookTicks[slot] = 0;
        }
        if (completed != null) {
            advancePersistenceRevision();
            return new TickResult(true, false, List.copyOf(completed));
        }
        if (progressed) advancePersistenceRevision();
        return progressed ? PROGRESSED : UNCHANGED;
    }

    /** 파괴 시 아직 조리 중인 원재료를 모두 꺼냅니다. */
    public List<StoredStack> drainAll() {
        List<StoredStack> out = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            if (itemTypes[slot] != PlayerInventory.EMPTY) {
                out.add(new StoredStack(itemTypes[slot], 1));
            }
            itemTypes[slot] = PlayerInventory.EMPTY;
            cookTicks[slot] = 0;
        }
        if (!out.isEmpty()) advancePersistenceRevision();
        return out;
    }

    public static final class TickResult {
        private final boolean changed;
        private final boolean finishedCooling;
        private final List<CompletedStack> completed;

        private TickResult(boolean changed, boolean finishedCooling, List<CompletedStack> completed) {
            this.changed = changed;
            this.finishedCooling = finishedCooling;
            this.completed = completed;
        }

        public boolean changed() {
            return changed;
        }

        public boolean finishedCooling() {
            return finishedCooling;
        }

        public List<CompletedStack> completed() {
            return completed;
        }
    }

    public static final class CompletedStack {
        private final int slot;
        private final short itemType;

        private CompletedStack(int slot, short itemType) {
            this.slot = slot;
            this.itemType = itemType;
        }

        public int slot() {
            return slot;
        }

        public short itemType() {
            return itemType;
        }
    }

    public static final class StoredStack {
        private final short itemType;
        private final int count;

        private StoredStack(short itemType, int count) {
            this.itemType = itemType;
            this.count = count;
        }

        public short itemType() {
            return itemType;
        }

        public int count() {
            return count;
        }
    }
}
