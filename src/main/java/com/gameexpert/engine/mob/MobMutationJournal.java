package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gameexpert.terrain.Blocks;

/**
 * 몹이 일으키는 월드 변형의 write-ahead 저널입니다(§39cd).
 *
 * <p>Web Trapper의 그물, Demolisher의 구조물 파괴, Builder의 건설은 모두 이 저널을 통과합니다.
 * 순서는 항상 <b>기록 → (durable 확인) → 적용</b>이며, 적용 전에 크래시·언로드·재접속이 일어나도
 * 복구가 pending 항목을 멱등 재적용합니다. {@code applied} 표시와 실제 durable block diff가
 * 함께 확인된 항목만 적용 완료로 믿으며, 둘 사이에서 끊긴 항목은 같은 최종 셀을 다시 씁니다.
 *
 * <p>출처(provenance) 계약: 플레이어 편집 &gt; 몹 변형 &gt; 생성 구조물 &gt; 자연 overlay.
 * 몹 변형은 플레이어 편집 좌표를 절대 덮지 않고, 적용된 좌표는 플레이어 편집 집합이 아니라 이
 * 저널의 출처 색인이 소유합니다. 나중에 플레이어가 같은 칸을 편집하면 그 항목은 supersede 되어
 * 되감기 대상에서 빠집니다. 구조물 재생성·자연 overlay 경로는 저널 소유 좌표를 덮지 않습니다.
 *
 * <p>이 클래스는 순수 로직이며 Spring·DB에 의존하지 않습니다. 영속은 {@link Store},
 * 월드 접근은 {@link World} 포트가 담당하고 standalone 권위는 같은 의미를 TypeScript로 복제합니다.
 */
public final class MobMutationJournal {

    /** 몹 변형의 출처. 플레이어 편집은 저널에 들어오지 않으므로 값이 없습니다. */
    public enum Origin {
        /** Web Trapper의 거미줄 설치. */
        RAID_TRAP,
        /** Demolisher의 구조물 블록 파괴. */
        RAID_DEMOLISH,
        /** Builder의 방어 구조물 건설. */
        RAID_BUILD
    }

    /** 요청 판정. 거부는 모두 계약 위반이라 조용히 무시하지 않고 호출자에게 알립니다. */
    public enum Result {
        /** durable 기록을 마쳤거나 예약했습니다. 적용은 {@link #pump()}에서 일어납니다. */
        ACCEPTED,
        /** 같은 entry key가 이미 있습니다. 재전송·재접속 재시도의 중복 적용을 막습니다. */
        DUPLICATE,
        /** 플레이어 편집 좌표라 몹 변형이 덮을 수 없습니다. */
        PLAYER_EDIT,
        /** Demolisher가 raid 대상 생성 구조물이 아닌 칸을 부수려 했습니다. */
        NOT_STRUCTURE_OWNED,
        /** 역할이 허용하지 않는 블록 전이입니다(그물 아닌 설치, 컨테이너 파괴, 점유 칸 건설 등). */
        ROLE_FORBIDDEN,
        /** 현재 값과 같아 변형이 없습니다. */
        UNCHANGED,
        /** 월드 높이 밖입니다. */
        OUT_OF_WORLD,
        /** 저장소가 durable 기록을 받지 못했습니다. 기록 없는 적용은 절대 하지 않습니다. */
        STORE_UNAVAILABLE
    }

    /**
     * 저널 한 줄. 값 자체가 되감기에 필요한 이전 상태를 함께 들고 있습니다.
     *
     * <p>{@code actionId}는 호출자(역할 행동)의 안정적 식별자라 재전송·재접속 재시도가 같은 key를
     * 만들고, {@code sequence}는 저널이 매기는 적용/되감기 순서입니다.
     */
    public static final class Entry {
        private final String key;
        private final Origin origin;
        private final long eventId;
        private final long actorId;
        private final long actionId;
        private final long sequence;
        private final int x;
        private final int y;
        private final int z;
        private final short blockType;
        private final short blockState;
        private final short priorBlockType;
        private final short priorBlockState;
        private boolean applied;
        private boolean superseded;

        public Entry(Origin origin, long eventId, long actorId, long actionId, long sequence,
                int x, int y, int z, short blockType, short blockState,
                short priorBlockType, short priorBlockState,
                boolean applied, boolean superseded) {
            this.key = entryKey(origin, eventId, actorId, actionId);
            this.origin = origin;
            this.eventId = eventId;
            this.actorId = actorId;
            this.actionId = actionId;
            this.sequence = sequence;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockType = blockType;
            this.blockState = blockState;
            this.priorBlockType = priorBlockType;
            this.priorBlockState = priorBlockState;
            this.applied = applied;
            this.superseded = superseded;
        }

        public String key() { return key; }
        public Origin origin() { return origin; }
        public long eventId() { return eventId; }
        public long actorId() { return actorId; }
        public long actionId() { return actionId; }
        public long sequence() { return sequence; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public short blockType() { return blockType; }
        public short blockState() { return blockState; }
        public short priorBlockType() { return priorBlockType; }
        public short priorBlockState() { return priorBlockState; }
        public boolean applied() { return applied; }
        public boolean superseded() { return superseded; }
    }

    /** 저널의 durable 저장소. Spring은 MySQL 행, standalone은 IndexedDB 레코드를 씁니다. */
    public interface Store {
        /**
         * pending 항목을 durable 하게 남깁니다. 실제 쓰기가 끝난 뒤 {@code onDurable}을 실행해야
         * 하며, 다른 스레드에서 호출해도 됩니다(저널이 tick 스레드에서 안전하게 회수합니다).
         *
         * @return 쓰기를 받았으면 {@code true}. {@code false}면 항목은 존재하지 않은 것으로
         *         취급되고 적용되지 않습니다.
         */
        boolean append(Entry entry, Runnable onDurable);

        /** 적용 완료 표시. 복구가 같은 항목을 다시 적용하지 않게 합니다. */
        void markApplied(Entry entry);

        /** 플레이어 편집이 좌표를 가져갔음을 표시합니다(되감기 대상에서 제외). */
        void markSuperseded(Entry entry);

        /** 되감기·종료로 소비된 항목을 지웁니다. */
        void remove(Entry entry);

        /** 월드 활성화 시 남아 있는 항목 전체. 순서는 저널이 sequence로 다시 세웁니다. */
        List<Entry> loadAll();

        /** 큐 포화나 일시 DB 실패 뒤 남은 쓰기를 다시 제출합니다. tick/dispose 양쪽에서 호출됩니다. */
        default void flushPending() { }

        /** dispose 장벽처럼 지연 없이 한 번 재제출해야 하는 경로입니다. */
        default void flushPendingNow() { flushPending(); }

        /** 아직 durable 완료되지 않은 append/상태 변경/삭제가 있는가. */
        default boolean hasPendingWrites() { return false; }
    }

    /** 저널이 바라보는 월드 포트. */
    public interface World {
        short blockAt(int x, int y, int z);

        short stateAt(int x, int y, int z);

        /** 플레이어 편집으로 보호된 좌표인가(§38f 최상위 우선순위). */
        boolean isPlayerEdit(int x, int y, int z);

        /** 시작 시 MySQL block diff가 실제로 존재하는 좌표인가. */
        boolean hasDurableBlockDiff(int x, int y, int z);

        /** 시작 시 읽은 durable block diff의 타입·상태가 기대값과 정확히 같은가. */
        boolean durableBlockDiffMatches(
                int x, int y, int z, short blockType, short blockState);

        /** durable diff에 함께 저장된 몹 변형 entry key. 플레이어/일반 변경이면 null입니다. */
        default String durableBlockDiffSourceKey(int x, int y, int z) { return null; }

        /** 복구가 검증한 durable 몹 출처를 플레이어 편집 보호 집합에서 되찾습니다. */
        default void claimDurableMobMutation(int x, int y, int z, String sourceKey) { }

        /** raid 대상 생성 구조물이 소유한 좌표인가. Demolisher는 이 칸만 부술 수 있습니다. */
        boolean isStructureOwned(int x, int y, int z);

        /** 저널이 확정한 변형을 실제 월드에 적용합니다(브로드캐스트·영속 포함). */
        void applyMobMutation(int x, int y, int z, int blockType, int blockState);

        /** 새 block diff에 entry key를 함께 기록하는 출처 인지 적용 경로입니다. */
        default void applyMobMutation(
                int x, int y, int z, int blockType, int blockState, String sourceKey) {
            applyMobMutation(x, y, z, blockType, blockState);
        }
    }

    private final Store store;
    private final World world;
    private final Map<String, Entry> entriesByKey = new HashMap<>();
    private final Map<Long, Entry> provenanceByPosition = new HashMap<>();
    private final List<Entry> durableUnapplied = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> durableSignals = new ConcurrentLinkedQueue<>();
    private long nextSequence;
    private long appliedCount;
    private long duplicateCount;
    private long recoveryRepairCount;
    private long recoveryConflictCount;

    public MobMutationJournal(Store store, World world) {
        this.store = store;
        this.world = world;
    }

    /**
     * 월드 활성화 복구. applied 행은 durable block diff와 값까지 일치할 때만 출처를 되살립니다.
     * diff가 없거나 기록된 선행값에 머물렀으면 marker-before-diff 크래시로 보고 다음 pump에서
     * 멱등 재적용하고, 저널 lineage에 없는 값이면 더 새로운 durable 편집으로 보고 supersede 합니다.
     */
    public void recover() {
        List<Entry> stored = new ArrayList<>(store.loadAll());
        stored.sort(Comparator.comparingLong(Entry::sequence));
        Map<Long, List<Entry>> lineageByPosition = new HashMap<>();
        for (Entry entry : stored) {
            if (entriesByKey.putIfAbsent(entry.key, entry) != null) continue;
            nextSequence = Math.max(nextSequence, entry.sequence + 1);
            lineageByPosition.computeIfAbsent(
                    positionKey(entry.x, entry.y, entry.z), ignored -> new ArrayList<>())
                    .add(entry);
            if (!entry.applied && !entry.superseded) durableUnapplied.add(entry);
        }
        for (Map.Entry<Long, List<Entry>> positioned : lineageByPosition.entrySet()) {
            List<Entry> lineage = positioned.getValue();
            Entry latest = latestActive(lineage);
            if (latest == null) continue;
            if (!world.hasDurableBlockDiff(latest.x, latest.y, latest.z)) {
                // applied marker와 block diff는 서로 다른 write-behind 경로다. marker만 먼저
                // durable 된 크래시 창은 set 연산을 다시 실행해 diff 버퍼를 복구한다.
                if (latest.applied) queueRecoveryRepair(latest);
                continue;
            }
            String sourceKey = world.durableBlockDiffSourceKey(
                    latest.x, latest.y, latest.z);
            Entry durableSource = sourceKey == null ? null : entriesByKey.get(sourceKey);
            if (durableSource == null || !lineage.contains(durableSource)
                    || durableSource.superseded
                    || !world.durableBlockDiffMatches(
                            durableSource.x, durableSource.y, durableSource.z,
                            durableSource.blockType, durableSource.blockState)) {
                // 출처 key가 없거나 값과 맞지 않는 durable 행은 플레이어/환경 편집이다. 값만
                // 우연히 저널의 과거 상태와 같아도 출처 증거 없이는 절대 덮어쓰지 않는다.
                supersedeLineage(lineage);
                continue;
            }
            world.claimDurableMobMutation(
                    durableSource.x, durableSource.y, durableSource.z, sourceKey);
            if (durableSource == latest) {
                if (!latest.applied) {
                    // block diff는 commit됐지만 applied marker가 늦은 크래시 창. 재적용하지 않고
                    // marker만 멱등 완료한다.
                    latest.applied = true;
                    durableUnapplied.remove(latest);
                    store.markApplied(latest);
                    recoveryRepairCount++;
                }
                provenanceByPosition.put(positioned.getKey(), latest);
            } else {
                // durable 셀이 같은 lineage의 이전 key를 명시한다. 최신 set만 다시 내보내며,
                // claimDurableMobMutation이 시작 시 플레이어 보호 오분류를 먼저 해제했다.
                queueRecoveryRepair(latest);
            }
        }
        store.flushPending();
    }

    private static Entry latestActive(List<Entry> lineage) {
        for (int index = lineage.size() - 1; index >= 0; index--) {
            Entry entry = lineage.get(index);
            if (!entry.superseded) return entry;
        }
        return null;
    }

    private void supersedeLineage(List<Entry> lineage) {
        boolean changed = false;
        for (Entry entry : lineage) {
            if (entry.superseded) continue;
            entry.superseded = true;
            store.markSuperseded(entry);
            changed = true;
        }
        if (changed) recoveryConflictCount++;
    }

    private void queueRecoveryRepair(Entry entry) {
        entry.applied = false;
        if (!durableUnapplied.contains(entry)) durableUnapplied.add(entry);
        recoveryRepairCount++;
    }

    /**
     * 몹 변형 요청. 계약 검사에 통과하면 durable 기록만 예약하고 즉시 적용하지 않습니다.
     * 적용은 durable 확인 뒤 {@link #pump()}가 sequence 순서로 수행합니다.
     */
    public Result request(Origin origin, long eventId, long actorId, long actionId,
            int x, int y, int z, int blockType, int blockState) {
        // 중복은 월드 상태보다 먼저 본다. 재접속 재시도가 그 사이 바뀐 지형 때문에 다른 판정을
        // 받고 두 번째 항목을 만드는 일이 없어야 한다.
        if (entriesByKey.containsKey(entryKey(origin, eventId, actorId, actionId))) {
            duplicateCount++;
            return Result.DUPLICATE;
        }
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return Result.OUT_OF_WORLD;
        short current = world.blockAt(x, y, z);
        short currentState = world.stateAt(x, y, z);
        if (current < 0) return Result.OUT_OF_WORLD;
        if (world.isPlayerEdit(x, y, z)) return Result.PLAYER_EDIT;
        Result role = roleCheck(origin, x, y, z, current, blockType);
        if (role != null) return role;
        if (current == (short) blockType && currentState == (short) blockState) return Result.UNCHANGED;
        Entry entry = new Entry(origin, eventId, actorId, actionId, nextSequence, x, y, z,
                (short) blockType, (short) blockState, current, currentState, false, false);
        entriesByKey.put(entry.key, entry);
        if (!store.append(entry, () -> durableSignals.add(entry.key))) {
            entriesByKey.remove(entry.key);
            return Result.STORE_UNAVAILABLE;
        }
        nextSequence++;
        return Result.ACCEPTED;
    }

    /**
     * durable 이 확인된 항목을 sequence 순서로 적용합니다. 월드 tick 소유 스레드에서만 부릅니다.
     * 이미 적용된 항목과 그 사이 플레이어가 가져간 좌표는 건너뜁니다.
     */
    public int pump() {
        store.flushPending();
        String signal;
        while ((signal = durableSignals.poll()) != null) {
            Entry entry = entriesByKey.get(signal);
            if (entry != null && !entry.applied) durableUnapplied.add(entry);
        }
        if (durableUnapplied.isEmpty()) return 0;
        durableUnapplied.sort(Comparator.comparingLong(Entry::sequence));
        int applied = 0;
        for (Entry entry : durableUnapplied) {
            if (entry.applied || entry.superseded) continue;
            if (world.isPlayerEdit(entry.x, entry.y, entry.z)) {
                // 기록 뒤 적용 전에 플레이어가 가져간 칸이다. 플레이어 편집 우선이라 적용하지 않고
                // 저널에서도 지워 되감기가 그 칸을 건드리지 않게 한다.
                entriesByKey.remove(entry.key);
                store.remove(entry);
                continue;
            }
            world.applyMobMutation(
                    entry.x, entry.y, entry.z, entry.blockType, entry.blockState, entry.key);
            // apply가 셀을 바꾼 뒤 예외를 던져도 false를 유지해 다음 tick/복구가 같은 set을
            // 멱등 재시도한다. 완료 표시는 apply가 정상 반환한 뒤에만 세운다.
            entry.applied = true;
            provenanceByPosition.put(positionKey(entry.x, entry.y, entry.z), entry);
            store.markApplied(entry);
            appliedCount++;
            applied++;
        }
        durableUnapplied.clear();
        return applied;
    }

    /**
     * 플레이어가 좌표를 편집했음을 알립니다. 그 칸의 몹 출처는 즉시 사라지고(플레이어 편집 우선),
     * 저널 항목은 supersede 되어 되감기에서 제외됩니다.
     */
    public void notePlayerEdit(int x, int y, int z) {
        Entry entry = provenanceByPosition.remove(positionKey(x, y, z));
        if (entry == null || entry.superseded) return;
        entry.superseded = true;
        store.markSuperseded(entry);
    }

    /** 좌표를 소유한 몹 변형 출처. 없으면 null(플레이어 편집·구조물·자연 중 하나). */
    public Origin originAt(int x, int y, int z) {
        Entry entry = provenanceByPosition.get(positionKey(x, y, z));
        return entry == null ? null : entry.origin;
    }

    /** 구조물 재생성·자연 overlay가 이 칸을 덮으면 안 되는가. */
    public boolean ownsPosition(int x, int y, int z) {
        return provenanceByPosition.containsKey(positionKey(x, y, z));
    }

    /**
     * 이벤트 종료 되감기. 적용 순서의 역순으로 이전 값을 복원하고 항목을 지웁니다.
     * 플레이어가 이미 가져간 칸과 현재 값이 저널 값과 다른 칸은 건드리지 않습니다(§39cd 안전 복원).
     */
    public int rewind(long eventId) {
        List<Entry> targets = new ArrayList<>();
        for (Entry entry : entriesByKey.values()) {
            if (entry.eventId == eventId) targets.add(entry);
        }
        targets.sort(Comparator.comparingLong(Entry::sequence).reversed());
        int restored = 0;
        for (Entry entry : targets) {
            if (!entry.applied || entry.superseded) {
                entriesByKey.remove(entry.key);
                store.remove(entry);
                continue;
            }
            long position = positionKey(entry.x, entry.y, entry.z);
            Entry predecessor = previousAppliedEntry(position, entry.sequence);
            String predecessorKey = predecessor != null
                    && predecessor.blockType == entry.priorBlockType
                    && predecessor.blockState == entry.priorBlockState
                    ? predecessor.key : null;
            boolean intact = world.blockAt(entry.x, entry.y, entry.z) == entry.blockType
                    && world.stateAt(entry.x, entry.y, entry.z) == entry.blockState
                    && !world.isPlayerEdit(entry.x, entry.y, entry.z);
            boolean alreadyRestored = world.blockAt(entry.x, entry.y, entry.z) == entry.priorBlockType
                    && world.stateAt(entry.x, entry.y, entry.z) == entry.priorBlockState
                    && !world.isPlayerEdit(entry.x, entry.y, entry.z);
            if (intact) {
                world.applyMobMutation(entry.x, entry.y, entry.z,
                        entry.priorBlockType, entry.priorBlockState, predecessorKey);
                restored++;
            } else if (alreadyRestored) {
                // 직전 시도가 셀을 되돌린 뒤 영속/브로드캐스트 경계에서 예외를 던진 경우다.
                // set 연산은 이미 목표값이므로 행 제거만 멱등 완료한다.
                restored++;
            }
            entriesByKey.remove(entry.key);
            if (provenanceByPosition.get(position) == entry) {
                provenanceByPosition.remove(position);
                if ((intact || alreadyRestored) && predecessor != null) {
                    provenanceByPosition.put(position, predecessor);
                }
            }
            store.remove(entry);
        }
        return restored;
    }

    private Entry previousAppliedEntry(long position, long beforeSequence) {
        Entry previous = null;
        for (Entry candidate : entriesByKey.values()) {
            if (!candidate.applied || candidate.superseded
                    || candidate.sequence >= beforeSequence
                    || positionKey(candidate.x, candidate.y, candidate.z) != position) {
                continue;
            }
            if (previous == null || candidate.sequence > previous.sequence) previous = candidate;
        }
        return previous;
    }

    public long appliedCount() { return appliedCount; }

    public long duplicateCount() { return duplicateCount; }

    public long recoveryRepairCount() { return recoveryRepairCount; }

    public long recoveryConflictCount() { return recoveryConflictCount; }

    public int pendingCount() {
        int pending = 0;
        for (Entry entry : entriesByKey.values()) {
            if (!entry.applied) pending++;
        }
        return pending;
    }

    /** 월드 dispose 장벽이 저널의 마지막 상태 쓰기까지 기다릴 때 사용합니다. */
    public boolean hasPendingPersistence() {
        return store.hasPendingWrites();
    }

    /** tick이 멈춘 dispose 단계에서도 거부·실패한 저장을 다시 제출합니다. */
    public void flushPendingPersistence() {
        store.flushPendingNow();
    }

    public List<Entry> entriesForTest() {
        List<Entry> entries = new ArrayList<>(entriesByKey.values());
        entries.sort(Comparator.comparingLong(Entry::sequence));
        return entries;
    }

    /** 역할별 허용 전이. null 이면 통과입니다. */
    private Result roleCheck(Origin origin, int x, int y, int z, short current, int blockType) {
        switch (origin) {
            case RAID_TRAP -> {
                // 그물사는 빈 칸에 거미줄만 놓는다. 어떤 기존 블록도 지우지 않는다.
                if (blockType != Blocks.COBWEB) return Result.ROLE_FORBIDDEN;
                if (current != Blocks.AIR) return Result.ROLE_FORBIDDEN;
            }
            case RAID_DEMOLISH -> {
                // 파괴는 raid 대상 생성 구조물 블록만, 그리고 항상 공기로만 만든다.
                if (blockType != Blocks.AIR) return Result.ROLE_FORBIDDEN;
                if (isContainerBlock(current)) return Result.ROLE_FORBIDDEN;
                if (!world.isStructureOwned(x, y, z)) return Result.NOT_STRUCTURE_OWNED;
            }
            case RAID_BUILD -> {
                // 건설은 빈 칸에만 놓는다. 자연 지형·구조물·플레이어 편집을 대체하지 않는다.
                if (blockType == Blocks.AIR) return Result.ROLE_FORBIDDEN;
                if (current != Blocks.AIR) return Result.ROLE_FORBIDDEN;
            }
        }
        return null;
    }

    private static boolean isContainerBlock(int blockType) {
        return Blocks.isChestShaped(blockType) || blockType == Blocks.FURNACE
                || blockType == Blocks.CAMPFIRE;
    }

    public static String entryKey(Origin origin, long eventId, long actorId, long actionId) {
        return origin.name() + ':' + eventId + ':' + actorId + ':' + actionId;
    }

    private static long positionKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) (y + 2048) & 0xfffL) << 26
                | ((long) z & 0x3ffffffL);
    }
}
