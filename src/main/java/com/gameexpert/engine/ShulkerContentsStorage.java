package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [SHULKER-CONTENTS] 한 월드의 셜커 상자 27칸 <b>간접 참조</b> 저장소.
 *
 * <p>정적판 {@code StandaloneShulkers} 의 짝이며 계약은 문자 그대로 같다. 아이템 칸·드랍
 * 엔티티는 참조 ID 하나만 들고 다니고, 27칸 자체는 이 저장소가 소유한다. 아이템 칸에 27칸을
 * 직접 실으면 인벤토리·컨테이너·드랍·와이어·영속이 전부 가변 크기 레코드가 되기 때문이다.
 *
 * <p><b>놓여 있는 동안에는 행이 없다.</b> 설치된 셜커 상자의 27칸은 다른 상자와 똑같이
 * {@link ChestStorage} 가 좌표로 소유하고, 여기에는 "아이템으로 이동 중인" 27칸만 남는다.
 * 그래서 27칸이 두 곳에 동시에 존재하는 순간이 구조적으로 없다:
 * <ul>
 *   <li>설치 = {@link #take(int)} 로 여기서 떼어 내면서 같은 틱에 좌표 캐시에 붙인다.</li>
 *   <li>채굴 = 좌표 캐시에서 떼어 내면서 같은 틱에 {@link #store(ChestInventory)} 로 붙인다.</li>
 * </ul>
 * 두 lane 의 dirty 는 <b>한 트랜잭션</b>으로 플러시된다
 * ({@code ChestPersistenceService.flushDirty}) — 한쪽만 커밋되면 27칸이 복제되거나 사라진다.
 *
 * <p><b>스레딩</b>: 내용 맵은 월드 틱 스레드 전용이고, dirty 집합만 {@link ChestStorage} 와
 * 같은 이유로 동기화한다(플러시가 다른 스레드에서 배치를 가져간다).
 */
public final class ShulkerContentsStorage {

    static final int MAX_SHULKER_ID = Integer.MAX_VALUE - 1;

    private final Map<Integer, ChestInventory> contents = new HashMap<>();
    /** 다음에 발급할 참조 ID. 월드 입장 시 저장된 최대 ID + 1 로 올라간다. */
    private int nextShulkerId = 1;

    private final Set<Integer> dirtyUpserts = new HashSet<>();
    private final Set<Integer> dirtyRemovals = new HashSet<>();
    private StoreReservation activeStoreReservation;

    /**
     * Reserves one contiguous identity batch without publishing contents, advancing the allocator,
     * or dirtying persistence. The returned entries are safe to embed in a larger explosion plan.
     */
    public synchronized StoreReservation reserveStoreBatch(List<ChestInventory> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("셜커 내용 예약 묶음이 없습니다");
        }
        if (activeStoreReservation != null) {
            throw new IllegalStateException("다른 셜커 내용 예약이 아직 열려 있습니다");
        }
        List<ChestInventory> detached = List.copyOf(candidates);
        Set<ChestInventory> uniqueOwners = java.util.Collections.newSetFromMap(
                new IdentityHashMap<>());
        for (ChestInventory candidate : detached) {
            if (candidate == null) {
                throw new IllegalArgumentException("셜커 27칸 예약에 빈 행이 있습니다");
            }
            if (!uniqueOwners.add(candidate)) {
                throw new IllegalArgumentException("같은 셜커 27칸을 두 번 예약할 수 없습니다");
            }
        }
        if (nextShulkerId <= 0 || nextShulkerId > MAX_SHULKER_ID
                || detached.size() > (long) MAX_SHULKER_ID - nextShulkerId + 1L) {
            throw new IllegalStateException("셜커 참조 ID 공간이 고갈되었습니다");
        }
        List<ReservedContents> entries = new ArrayList<>(detached.size());
        int candidateId = nextShulkerId;
        for (ChestInventory candidate : detached) {
            entries.add(new ReservedContents(candidateId, candidate));
            candidateId = candidateId == MAX_SHULKER_ID ? Integer.MAX_VALUE : candidateId + 1;
        }
        StoreReservation reservation = new StoreReservation(
                this, nextShulkerId, candidateId, List.copyOf(entries));
        activeStoreReservation = reservation;
        return reservation;
    }

    /** Commits every reserved row or none. A committed reservation is idempotent. */
    public synchronized boolean commitStoreBatch(StoreReservation reservation) {
        requireReservationOwner(reservation);
        if (reservation.state == ReservationState.COMMITTED) return false;
        preflightStoreBatch(reservation);

        Set<Integer> dirtyUpsertsBefore = new HashSet<>(dirtyUpserts);
        Set<Integer> dirtyRemovalsBefore = new HashSet<>(dirtyRemovals);
        try {
            for (ReservedContents entry : reservation.entries) {
                contents.put(entry.shulkerId(), entry.contents());
                dirtyRemovals.remove(entry.shulkerId());
                dirtyUpserts.add(entry.shulkerId());
            }
            nextShulkerId = reservation.nextIdAfterBatch;
            reservation.state = ReservationState.COMMITTED;
            activeStoreReservation = null;
            return true;
        } catch (RuntimeException | Error failure) {
            for (ReservedContents entry : reservation.entries) {
                contents.remove(entry.shulkerId());
            }
            dirtyUpserts.clear();
            dirtyUpserts.addAll(dirtyUpsertsBefore);
            dirtyRemovals.clear();
            dirtyRemovals.addAll(dirtyRemovalsBefore);
            nextShulkerId = reservation.expectedFirstId;
            throw failure;
        }
    }

    /** Rechecks ownership, high-water and every target row without changing live state. */
    public synchronized void preflightStoreBatch(StoreReservation reservation) {
        requireReservationOwner(reservation);
        if (reservation.state != ReservationState.OPEN || activeStoreReservation != reservation) {
            throw new IllegalStateException("셜커 내용 예약 소유권이 만료되었습니다");
        }
        if (nextShulkerId != reservation.expectedFirstId) {
            throw new IllegalStateException("셜커 참조 ID 예약이 오래되었습니다");
        }
        Set<Integer> uniqueIds = new HashSet<>();
        for (ReservedContents entry : reservation.entries) {
            if (!uniqueIds.add(entry.shulkerId()) || contents.containsKey(entry.shulkerId())) {
                throw new IllegalStateException("셜커 참조 ID 예약이 충돌했습니다");
            }
        }
    }

    /** Revokes an uncommitted owner token without changing contents, dirty state, or high-water. */
    public synchronized boolean rollbackStoreBatch(StoreReservation reservation) {
        requireReservationOwner(reservation);
        if (reservation.state == ReservationState.ROLLED_BACK) return false;
        if (reservation.state != ReservationState.OPEN || activeStoreReservation != reservation) {
            throw new IllegalStateException("셜커 내용 예약 소유권이 만료되었습니다");
        }
        reservation.state = ReservationState.ROLLED_BACK;
        activeStoreReservation = null;
        return true;
    }

    private void requireReservationOwner(StoreReservation reservation) {
        if (reservation == null || reservation.owner != this) {
            throw new IllegalArgumentException("다른 셜커 저장소의 예약입니다");
        }
    }

    /**
     * 27칸을 통째로 넘겨받아 새 참조 ID 를 발급한다(채굴 경로). <b>사본을 만들지 않는다</b> —
     * 호출부는 같은 틱에 좌표 캐시에서 그 인벤토리를 이미 떼어 냈다.
     */
    public synchronized int store(ChestInventory taken) {
        if (taken == null) throw new IllegalArgumentException("셜커 27칸이 없습니다");
        StoreReservation reservation = reserveStoreBatch(List.of(taken));
        try {
            commitStoreBatch(reservation);
            return reservation.entries().getFirst().shulkerId();
        } catch (RuntimeException | Error failure) {
            if (reservation.state == ReservationState.OPEN) rollbackStoreBatch(reservation);
            throw failure;
        }
    }

    /**
     * 그 참조가 물고 있던 27칸을 떼어 낸다(설치 경로). 행이 없으면 null 이고, 호출부는
     * <b>빈 27칸으로 이어 가야 한다</b> — 여기서 예외를 던지면 설치 액션이 통째로 죽는다.
     */
    public ChestInventory take(int shulkerId) {
        ChestInventory taken = contents.remove(shulkerId);
        if (taken != null) markRemoval(shulkerId);
        return taken;
    }

    /** 그 참조의 27칸(떼어 내지 않는다). 없으면 null. */
    public ChestInventory peek(int shulkerId) {
        return contents.get(shulkerId);
    }

    public boolean has(int shulkerId) {
        return contents.containsKey(shulkerId);
    }

    public int size() {
        return contents.size();
    }

    /** 월드 입장 시 DB 행으로 캐시를 채운다. dirty 로 표시하지 않는다. */
    public void load(int shulkerId, ChestInventory chest) {
        if (shulkerId <= 0 || shulkerId > MAX_SHULKER_ID || chest == null) {
            throw new IllegalArgumentException("셜커 참조 행이 올바르지 않습니다: " + shulkerId);
        }
        contents.put(shulkerId, chest);
        if (shulkerId >= nextShulkerId) nextShulkerId = shulkerId + 1;
    }

    // ── 영속 lane ────────────────────────────────────────

    private synchronized void markUpsert(int shulkerId) {
        dirtyUpserts.add(shulkerId);
    }

    private synchronized void markRemoval(int shulkerId) {
        dirtyUpserts.remove(shulkerId);
        dirtyRemovals.add(shulkerId);
    }

    /** 내용이 바뀐 참조 행(놓인 상자를 다시 아이템으로 만들지 않는 편집 경로용). */
    public void markDirty(int shulkerId) {
        if (contents.containsKey(shulkerId)) markUpsert(shulkerId);
    }

    public synchronized boolean hasDirty() {
        return !dirtyUpserts.isEmpty() || !dirtyRemovals.isEmpty();
    }

    /** 저장 대상을 가져가고 비운다(원자 스왑). 상자 lane 과 <b>같은 배치</b>로 넘어가야 한다. */
    public synchronized Batch drainDirty() {
        if (dirtyUpserts.isEmpty() && dirtyRemovals.isEmpty()) return Batch.EMPTY;
        List<Integer> upserts = new ArrayList<>(dirtyUpserts);
        List<Integer> removals = new ArrayList<>(dirtyRemovals);
        dirtyUpserts.clear();
        dirtyRemovals.clear();
        return new Batch(upserts, removals);
    }

    /** DB 쓰기가 실패했을 때 다음 주기에 다시 스냅샷하도록 되돌린다. */
    public synchronized void restoreDirty(Batch batch) {
        if (batch == null) return;
        dirtyUpserts.addAll(batch.upserts());
        dirtyRemovals.addAll(batch.removals());
    }

    /** 한 번의 플러시가 가져가는 참조 ID 묶음. */
    public record Batch(List<Integer> upserts, List<Integer> removals) {
        public static final Batch EMPTY = new Batch(List.of(), List.of());

        public boolean isEmpty() {
            return upserts.isEmpty() && removals.isEmpty();
        }
    }

    public record ReservedContents(int shulkerId, ChestInventory contents) {
        public ReservedContents {
            if (shulkerId <= 0 || shulkerId > MAX_SHULKER_ID || contents == null) {
                throw new IllegalArgumentException("셜커 내용 예약 행이 올바르지 않습니다");
            }
        }
    }

    private enum ReservationState { OPEN, COMMITTED, ROLLED_BACK }

    public static final class StoreReservation {
        private final ShulkerContentsStorage owner;
        private final int expectedFirstId;
        private final int nextIdAfterBatch;
        private final List<ReservedContents> entries;
        private ReservationState state = ReservationState.OPEN;

        private StoreReservation(ShulkerContentsStorage owner, int expectedFirstId,
                int nextIdAfterBatch, List<ReservedContents> entries) {
            this.owner = owner;
            this.expectedFirstId = expectedFirstId;
            this.nextIdAfterBatch = nextIdAfterBatch;
            this.entries = entries;
        }

        public List<ReservedContents> entries() {
            return entries;
        }
    }
}
