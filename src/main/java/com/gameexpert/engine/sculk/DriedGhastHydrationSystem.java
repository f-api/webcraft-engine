package com.gameexpert.engine.sculk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.ConcreteRules;

/**
 * [DEEP-DARK] {@link DriedGhastHydration} 을 실제 틱에 태우는 <b>사슬 배선</b>.
 *
 * <p>규칙 클래스는 상태가 없다 — 누적 시간은 월드가 소유한다고 그 클래스 주석이 못박아 두었는데,
 * 두 권위 어디에도 그 "월드" 가 없었다. 그래서 말린 가스트를 물에 담가도 <b>사건 자체가
 * 일어나지 않았다</b>({@code GhastlingRevivalSink} 가 NO_OP 인 것은 종 미등록 때문이라고 적혀
 * 있었지만, 종이 있었어도 부를 사람이 없었다). 이 클래스가 그 원장이다.
 *
 * <p><b>순서</b>(두 권위가 같아야 한다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneDriedGhastHydrationSystem.ts} 다):
 * <ol>
 *   <li>추적 원장의 각 칸이 아직 말린 가스트인지 확인한다(아니면 잊는다).</li>
 *   <li>{@link DriedGhastHydration#submerged} 로 물 접촉을 본다 — 콘크리트 경화와 <b>같은
 *       술어</b>다.</li>
 *   <li>물 접촉 여부가 직전 판정과 <b>바뀌었으면 누적을 0 으로 되돌린다</b>.</li>
 *   <li>누적이 {@link DriedGhastHydration#STAGE_MC_TICKS} 를 채웠으면 규칙에 물어 단계를
 *       올리거나 내리고, 소생이면 블록을 비운 뒤 {@link DriedGhastHydration.GhastlingRevivalSink}
 *       를 부른다.</li>
 * </ol>
 *
 * <p><b>추적 원장은 어떻게 채워지는가</b>: 말린 가스트는 자연 지형에 없다(딥다크 도시 상자
 * 전리품이라 반드시 플레이어가 놓는다). 그래서 두 입구뿐이다 — 이번 틱 변경 좌표
 * ({@link #track})와 청크 적재 시 영속 diff({@code WorldRuntime#activateGeneratedChunk} 가
 * {@code FIRE} 에 이미 쓰고 있는 그 자리). 월드를 훑는 스캐너를 새로 만들지 않는 이유가
 * 이것이다.
 *
 * <p><b>랜덤 틱을 쓰지 않는다.</b> 바닐라 {@code DriedGhastBlock} 은 랜덤 틱을 받은 뒤
 * 5000 틱짜리 지연을 예약한다([B] minecraft.wiki «Dried Ghast»: 250초 지연 + 평균 68초의 랜덤
 * 틱 대기 ≈ 평균 5.3분/단계). 이 저장소는 그 두 항을 이미 착지한 상수 하나
 * ({@code STAGE_MC_TICKS} = 6000 = 5분)에 접어 두었고, 여기서 랜덤 틱을 다시 끌어들이면
 * <b>새 난수 소비가 생겨</b> 기존 종의 확률·난수 프리픽스 감사가 깨진다. 결정적 누적이 그
 * 제약과 이미 있는 상수를 동시에 지키는 유일한 배선이다.
 *
 * <p>시간 단위는 전부 <b>MC 틱</b>이다. 권위 틱(10 TPS)을 MC 틱으로 접는 것은 호출자 몫이며
 * {@code SculkVibrationSystem} · {@code StatusEffects.MC_TICKS_PER_SERVER_TICK} 와 같은 규약이다.
 */
public final class DriedGhastHydrationSystem {

    /**
     * 추적 원장의 상한. 말린 가스트는 딥다크 도시 상자당 1/40 로만 나오는 전리품이라 한 월드에
     * 수십 개를 넘길 일이 없다. 상한을 넘으면 가장 오래 안 건드린 칸부터 버린다 — 버려도
     * 블록과 그 수화 단계(상태 바이트)는 그대로 남고, 다음 편집·재적재가 다시 추적한다.
     */
    static final int TRACKED_LIMIT = 4_096;

    /** 이 시스템이 월드에 요구하는 전부. {@code SculkVibrationSystem.World} 와 같은 좁기다. */
    public interface World {
        /** 비상주·범위 밖이면 음수. */
        int blockAt(int x, int y, int z);

        int stateAt(int x, int y, int z, int blockType);

        /** 상태를 쓰고 클라 렌더까지 밀어낸다(수화 단계가 곧 옆면 텍스처다). */
        void setState(int x, int y, int z, int blockType, int state);

        /** 소생한 칸을 비운다. 방송·영속이 붙은 단일 변경 경로여야 한다. */
        void clearBlock(int x, int y, int z);
    }

    /** 한 칸의 누적 회계. 단계 자체는 블록 상태 바이트가 들고 있고 여기엔 없다. */
    private static final class Accrual {
        /** 마지막 단계 변화(또는 추적 시작·물 접촉 전환) 이후 누적된 MC 틱. */
        private long elapsedMcTicks;
        /** 직전 판정의 물 접촉. 전환을 알아채 누적을 되돌리는 데만 쓴다. */
        private boolean submerged;
        /** 아직 한 번도 판정하지 않았는가(첫 틱은 전환으로 보지 않는다). */
        private boolean fresh = true;
    }

    private final World world;
    private final ConcreteRules.BlockLookup lookup;
    private final DriedGhastHydration.GhastlingRevivalSink revivalSink;

    /** 추적 중인 칸 → 누적 회계. 접근 순서 LRU 라 상한 초과 시 가장 오래된 것부터 버린다. */
    private final LinkedHashMap<Long, Accrual> tracked = new LinkedHashMap<>(64, 0.75f, true);

    /** 마지막 {@link #tick} 의 MC 틱. 첫 틱에서 누적을 0 으로 시작하기 위한 기준이다. */
    private long lastMcTick = Long.MIN_VALUE;

    public DriedGhastHydrationSystem(World world) {
        this(world, DriedGhastHydration.GhastlingRevivalSink.NO_OP);
    }

    public DriedGhastHydrationSystem(World world,
            DriedGhastHydration.GhastlingRevivalSink revivalSink) {
        this.world = world;
        this.lookup = world::blockAt;
        this.revivalSink = revivalSink;
    }

    /**
     * 이 칸을 추적 원장에 넣는다. 말린 가스트가 아니면 아무 일도 하지 않는다 — 호출자는
     * 이번 틱 변경 좌표를 그대로 넘기면 되고 종류를 먼저 걸러낼 필요가 없다.
     *
     * @return 새로 추적을 시작했으면 true
     */
    public boolean track(int x, int y, int z) {
        if (!DriedGhastHydration.isDriedGhast(world.blockAt(x, y, z))) return false;
        long key = posKey(x, y, z);
        if (tracked.containsKey(key)) return false;
        tracked.put(key, new Accrual());
        if (tracked.size() > TRACKED_LIMIT) {
            Iterator<Long> oldest = tracked.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /**
     * 이 좌표(또는 그 이웃)가 바뀌었다. 물이 들어오고 나가는 것이 곧 방향 전환이므로
     * <b>이웃 여섯 칸의 추적도 함께 깨운다</b> — 콘크리트 경화가 같은 합류점에서 "물이
     * 흘러왔다" 를 보는 것과 같은 이유다. 바뀐 칸 자체가 말린 가스트면 추적을 시작한다.
     */
    public void invalidate(int x, int y, int z) {
        track(x, y, z);
        track(x + 1, y, z);
        track(x - 1, y, z);
        track(x, y + 1, z);
        track(x, y - 1, z);
        track(x, y, z + 1);
        track(x, y, z - 1);
        long key = posKey(x, y, z);
        if (tracked.containsKey(key)
                && !DriedGhastHydration.isDriedGhast(world.blockAt(x, y, z))) {
            tracked.remove(key);
        }
    }

    /** QA 호출자가 범위를 검증한 기존의 젖은 칸만 한 구간 직전으로 옮긴다. */
    public boolean qaStageNextBoundary(int x, int y, int z, int expectedState) {
        Accrual accrual = tracked.get(posKey(x, y, z));
        if (expectedState < 0 || expectedState > 3 || accrual == null || accrual.fresh
                || !accrual.submerged || lastMcTick == Long.MIN_VALUE
                || !DriedGhastHydration.isDriedGhast(world.blockAt(x, y, z))
                || world.stateAt(x, y, z, world.blockAt(x, y, z)) != expectedState
                || !DriedGhastHydration.submerged(lookup, x, y, z)
                || accrual.elapsedMcTicks >= DriedGhastHydration.STAGE_MC_TICKS - 1) return false;
        accrual.elapsedMcTicks = DriedGhastHydration.STAGE_MC_TICKS - 1;
        return true;
    }

    /** 지금 추적 중인 칸 수(진단·테스트용). */
    public int trackedCount() {
        return tracked.size();
    }

    /**
     * 사슬 한 틱.
     *
     * @param mcTick 월드가 시작된 뒤 누적된 MC 틱. 단조 증가여야 한다.
     * @return 이번 틱에 소생한 칸 목록(테스트·진단용). 없으면 빈 목록.
     */
    public List<int[]> tick(long mcTick) {
        if (tracked.isEmpty()) {
            lastMcTick = mcTick;
            return List.of();
        }
        long delta = lastMcTick == Long.MIN_VALUE ? 0 : mcTick - lastMcTick;
        lastMcTick = mcTick;
        if (delta < 0) delta = 0;

        List<int[]> revived = null;
        // 순회 중에 원장을 건드리므로 키를 먼저 뜬다(소생이 clearBlock → invalidate 를 부를 수
        // 있고, LRU 맵은 조회만으로도 순서가 바뀐다).
        List<Long> keys = new ArrayList<>(tracked.keySet());
        for (long key : keys) {
            Accrual accrual = tracked.get(key);
            if (accrual == null) continue;
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            int block = world.blockAt(x, y, z);
            if (!DriedGhastHydration.isDriedGhast(block)) {
                // 부서졌거나 청크가 내려갔다. 비상주(음수)도 같은 처리다 — 다시 올라올 때
                // 청크 적재 훅이 추적을 새로 시작한다.
                tracked.remove(key);
                continue;
            }
            boolean submerged = DriedGhastHydration.submerged(lookup, x, y, z);
            if (accrual.fresh) {
                accrual.fresh = false;
                accrual.submerged = submerged;
                accrual.elapsedMcTicks = 0;
                continue;
            }
            if (submerged != accrual.submerged) {
                // 방향이 바뀌었다. 바닐라도 블록 상태가 바뀌면 예약 틱을 다시 잡으므로
                // 절반 채운 구간이 반대 방향으로 그대로 이어지지 않는다.
                accrual.submerged = submerged;
                accrual.elapsedMcTicks = 0;
                continue;
            }
            accrual.elapsedMcTicks += delta;
            if (accrual.elapsedMcTicks < DriedGhastHydration.STAGE_MC_TICKS) continue;

            int state = world.stateAt(x, y, z, block);
            int hydration = DriedGhastHydration.hydration(state);
            long elapsed = accrual.elapsedMcTicks;
            // 한 번에 여러 구간을 채웠어도 규칙이 그 산술을 갖는다. 나머지는 다음 구간으로
            // 넘겨 긴 delta(랙 스파이크·일시정지 복귀)가 시간을 삼키지 않게 한다.
            accrual.elapsedMcTicks = elapsed % DriedGhastHydration.STAGE_MC_TICKS;

            if (DriedGhastHydration.revives(hydration, submerged, elapsed)) {
                tracked.remove(key);
                world.clearBlock(x, y, z);
                revivalSink.reviveGhastling(x, y, z);
                if (revived == null) revived = new ArrayList<>();
                revived.add(new int[] { x, y, z });
                continue;
            }
            int next = DriedGhastHydration.nextHydration(hydration, submerged, elapsed);
            if (next == hydration) continue;
            world.setState(x, y, z, block, DriedGhastHydration.withHydration(state, next));
        }
        return revived == null ? List.of() : revived;
    }

    // ── 좌표 패킹. SculkVibrationSystem 과 같은 비트 배치다. ──────────────

    static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3F_FFFF) << 42)
                | ((long) (y & 0xFFFFF) << 22)
                | (z & 0x3F_FFFF);
    }

    static int unpackX(long key) {
        return signExtend((int) (key >>> 42), 22);
    }

    static int unpackY(long key) {
        return signExtend((int) ((key >>> 22) & 0xFFFFF), 20);
    }

    static int unpackZ(long key) {
        return signExtend((int) (key & 0x3F_FFFF), 22);
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return value << shift >> shift;
    }

    /** 추적 원장 스냅샷(진단·테스트용). 좌표 배열은 삽입 순서다. */
    Map<Long, Long> elapsedSnapshot() {
        Map<Long, Long> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Long, Accrual> entry : tracked.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().elapsedMcTicks);
        }
        return snapshot;
    }
}
