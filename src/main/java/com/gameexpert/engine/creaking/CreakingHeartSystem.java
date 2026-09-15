package com.gameexpert.engine.creaking;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.terrain.Blocks;

/**
 * 크리킹 하트를 실제 틱에 태우는 <b>사슬 배선</b>.
 *
 * <p>구조는 {@link com.gameexpert.engine.sculk.DriedGhastHydrationSystem} 과 같은 꼴이다 —
 * 규칙({@link CreakingHeartRules})은 상태가 없고, 이 클래스가 "어느 칸이 하트인지" 를 아는
 * 원장이다. <b>월드를 훑는 스캐너를 만들지 않는다</b>: 크리킹 하트는 자연 지형에 없고(이
 * 저장소에서는 제작으로만 생긴다) 입구가 둘뿐이기 때문이다 — 이번 틱 블록 변경 좌표
 * ({@link #invalidate})와 청크 적재 시 영속 diff({@link #track}).
 *
 * <p><b>한 틱의 순서</b>(두 권위가 같아야 한다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneCreakingHeartSystem.ts} 다):
 * <ol>
 *   <li>추적 칸이 아직 하트인지 확인한다 — 아니면 잊고, 거느리던 크리킹을 죽인다
 *       ([B] "Breaking a creaking heart that is protecting a creaking instantly kills it").</li>
 *   <li>{@link CreakingHeartRules#awake} 로 정렬 + 밤을 본다.</li>
 *   <li>깨어남 여부가 바뀌었으면 겉모습 ID 를 맞바꾼다(화로 점화 쌍둥이와 같은 스왑).</li>
 *   <li>깨어 있으면 소환 싱크를, 잠들었으면 소멸 싱크를 부른다. 중복 방지는 싱크의 책임이다
 *       ({@link CreakingSummon} — 워든 소환이 낸 분업 그대로).</li>
 * </ol>
 *
 * <p><b>난수를 쓰지 않는다.</b> 바닐라는 33×17×33 상자에서 유효한 칸을 뽑지만 그 탐색은
 * 새 난수 소비를 만들어 기존 종의 확률·난수 프리픽스 감사를 깬다. 배치는 하트 바로 위
 * 한 칸으로 고정한다([C] divergence — 근거는 {@link CreakingSummon#summon} 주석).
 */
public final class CreakingHeartSystem {

    /**
     * 추적 원장의 상한. 하트는 제작으로만 생기는 블록이라 한 월드에서 수백 개를 넘길 일이
     * 없다. 넘으면 가장 오래 안 건드린 칸부터 버린다 — 버려도 블록은 그대로 남고 다음
     * 편집·재적재가 다시 추적한다({@code DriedGhastHydrationSystem} 과 같은 규약).
     */
    static final int TRACKED_LIMIT = 4_096;

    /** 이 시스템이 월드에 요구하는 전부. */
    public interface World extends CreakingHeartRules.BlockLookup {
        /** 지금 월드 시각(0~11999 순환). 밤 판정의 유일한 입력이다. */
        long worldTime();

        /**
         * 겉모습 쌍둥이를 맞바꾼다. 종류가 바뀌는 편집이므로 방송·영속이 붙은 단일 변경
         * 경로여야 한다.
         */
        void swapHeartBlock(int x, int y, int z, int blockId);
    }

    /** 깨어난 하트가 크리킹을 요구한다. {@link CreakingSummon#summon} 자리다. */
    @FunctionalInterface
    public interface CreakingSink {
        void summon(int x, int y, int z);
    }

    /** 잠들었거나 부서진 하트가 자기 크리킹을 거둔다. {@link CreakingSummon#dismiss} 자리다. */
    @FunctionalInterface
    public interface DismissSink {
        void dismiss(int x, int y, int z);
    }

    /**
     * 한 칸의 파생 상태 기억. 값 자체는 블록 ID 가 들고 있고 여기엔 전이 판정용 사본만 있다.
     * 좌표를 함께 들고 있어 키에서 비트를 되풀지 않는다 — 키는 그저 유일하기만 하면 된다.
     */
    private static final class HeartState {
        private final int x;
        private final int y;
        private final int z;
        /** 직전 틱의 깨어남 판정. 전이를 알아채 스왑·싱크를 <b>바뀔 때만</b> 부르기 위한 것. */
        private boolean awake;
        /** 아직 한 번도 판정하지 않았는가(첫 틱은 언제나 전이로 본다). */
        private boolean fresh = true;

        private HeartState(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final World world;
    private final CreakingSink summonSink;
    private final DismissSink dismissSink;

    /** 추적 중인 칸 → 파생 상태. 접근 순서 LRU 라 상한 초과 시 가장 오래된 것부터 버린다. */
    private final Map<Long, HeartState> tracked = new LinkedHashMap<>(64, 0.75f, true);

    public CreakingHeartSystem(World world) {
        this(world, (x, y, z) -> { }, (x, y, z) -> { });
    }

    public CreakingHeartSystem(World world, CreakingSink summonSink, DismissSink dismissSink) {
        this.world = world;
        this.summonSink = summonSink;
        this.dismissSink = dismissSink;
    }

    /**
     * 이 칸을 추적 원장에 넣는다. 하트가 아니면 아무 일도 하지 않는다 — 호출자는 이번 틱
     * 변경 좌표를 그대로 넘기면 되고 종류를 먼저 걸러낼 필요가 없다.
     *
     * @return 새로 추적을 시작했으면 true
     */
    public boolean track(int x, int y, int z) {
        if (!Blocks.isCreakingHeart(world.blockAt(x, y, z))) return false;
        long key = posKey(x, y, z);
        if (tracked.containsKey(key)) return false;
        tracked.put(key, new HeartState(x, y, z));
        if (tracked.size() > TRACKED_LIMIT) {
            Iterator<Long> oldest = tracked.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /**
     * 이 좌표(또는 그 이웃)가 바뀌었다. 정렬을 이루는 원목이 놓이고 사라지는 것이 곧 상태
     * 전환이므로 이웃 여섯 칸의 추적도 함께 깨운다(수화 사슬이 물의 출입을 같은 방법으로
     * 보는 것과 같은 이유다).
     *
     * <p>바뀐 칸 자체가 더 이상 하트가 아니면 원장에서 지우고 <b>거느리던 크리킹을 죽인다</b>.
     * 이것이 "하트를 부수면 크리킹이 즉사한다" 의 유일한 배선 지점이다.
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
        if (tracked.containsKey(key) && !Blocks.isCreakingHeart(world.blockAt(x, y, z))) {
            tracked.remove(key);
            dismissSink.dismiss(x, y, z);
        }
    }

    /** 지금 추적 중인 칸 수(진단·테스트용). */
    public int trackedCount() {
        return tracked.size();
    }

    /**
     * 사슬 한 틱.
     *
     * @return 이번 틱에 깨어남 여부가 <b>바뀐</b> 칸 목록(테스트·진단용). 각 항은
     *         {@code {x, y, z, awake ? 1 : 0}} 이다.
     */
    public List<int[]> tick() {
        if (tracked.isEmpty()) return List.of();
        long worldTime = world.worldTime();
        List<int[]> transitions = new ArrayList<>();
        // 순회 중에 원장을 건드리므로 키를 먼저 뜬다.
        for (Long key : new ArrayList<>(tracked.keySet())) {
            HeartState state = tracked.get(key);
            if (state == null) continue;
            int x = state.x;
            int y = state.y;
            int z = state.z;
            int block = world.blockAt(x, y, z);
            if (!Blocks.isCreakingHeart(block)) {
                // 부서졌거나 청크가 내려갔다. 비상주(음수)도 같은 처리다 — 다시 올라오면
                // 청크 적재 훅이 추적을 새로 시작한다. 어느 쪽이든 크리킹은 남지 않는다.
                tracked.remove(key);
                dismissSink.dismiss(x, y, z);
                continue;
            }
            boolean awake = CreakingHeartRules.awake(world, x, y, z, worldTime);
            boolean changed = state.fresh || awake != state.awake;
            state.fresh = false;
            state.awake = awake;
            int wanted = CreakingHeartRules.blockIdFor(awake);
            if (block != wanted) world.swapHeartBlock(x, y, z, wanted);
            if (changed) transitions.add(new int[] { x, y, z, awake ? 1 : 0 });
            // 싱크는 <b>매 틱</b> 부른다. 중복 방지가 싱크의 책임인 것은 워든 소환이 낸
            // 계약 그대로이고, 그래야 크리킹이 다른 이유로 사라졌을 때 하트가 다시 세운다.
            if (awake) summonSink.summon(x, y, z);
            else dismissSink.dismiss(x, y, z);
        }
        return transitions;
    }

    /**
     * 원장 키. 좌표는 {@link HeartState} 가 들고 있으므로 이 값은 <b>유일하기만</b> 하면
     * 되고 되풀 필요가 없다(월드 폭 ±2^26 · 높이 4096 이면 충돌이 없다).
     */
    private static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3ff_ffff) << 38)
                | ((long) (z & 0x3ff_ffff) << 12)
                | ((y + 64) & 0xfff);
    }
}
