package com.gameexpert.engine.sculk;

import com.gameexpert.engine.ConcreteRules;
import com.gameexpert.terrain.Blocks;

/**
 * [DEEP-DARK] 말린 가스트의 수화 규칙(바닐라 1.21.6 {@code DriedGhastBlock}).
 *
 * <p>수화 단계는 0~3 이고 상태 바이트 하위 2비트에 담는다(비트 2..3 은 facing). 물에 잠긴 채로 한 단계마다
 * {@link #STAGE_MC_TICKS} 가 지나면 한 칸 오르고, 3 단계에서 한 번 더 지나면 가스틀링으로
 * 소생한다(총 네 구간 ≈ 20분). 물이 빠지면 <b>같은 속도로 되돌아간다</b>.
 * [B] minecraft.wiki «Dried Ghast» — 단계당 약 5분, 약 20분 뒤 가스틀링, 물이 사라지면 역행.
 *
 * <p>"물에 닿았는가" 는 새 술어를 만들지 않고 {@link ConcreteRules#touchesWater} 를 그대로
 * 쓴다. 콘크리트 경화와 <b>완전히 같은 트리거 합류점</b>(한 틱의 변경 좌표 목록)을 공유해야
 * "가루 옆에 물을 놓았을 때" 와 "말린 가스트 옆에 물을 놓았을 때" 가 어긋나지 않는다.
 *
 * <p>획득 경로만 divergence 다: 바닐라는 네더 화석·피글린 물물교환·제작으로 얻지만 이
 * 저장소에는 네더 지형이 없어 딥다크 도시 상자의 희귀 전리품으로 옮겼다(사용자 지시).
 * 수화 규약 자체는 바닐라 그대로다.
 *
 * <p>상태가 없는 순수 규칙 클래스다 — 누적 시간은 월드가 소유하고 이 클래스는 그 값을
 * 단계로 바꾸는 방법만 정한다.
 */
public final class DriedGhastHydration {

    /** 수화 단계를 담는 상태 비트. */
    public static final int HYDRATION_MASK = 0x03;
    /**
     * 바닐라 {@code facing}(N 0 · E 1 · S 2 · W 3)을 담는 상태 비트 2..3. 설치가 정하고
     * ({@code getHorizontalDirection().getOpposite()}) 수화 전이는 건드리지 않는다.
     */
    public static final int FACING_MASK = 0x0c;
    /** 최대 수화 단계. 이 단계에서 한 구간을 더 채우면 소생한다. */
    public static final int MAX_HYDRATION = 3;
    /** 한 단계에 걸리는 시간(5분 = 300초 = 6000 MC 틱). [B] */
    public static final int STAGE_MC_TICKS = 6_000;
    /** 0 단계에서 소생까지의 총 시간(네 구간 ≈ 20분). [B] */
    public static final int REVIVAL_MC_TICKS = STAGE_MC_TICKS * (MAX_HYDRATION + 1);

    private DriedGhastHydration() {}

    /**
     * 가스틀링 소생 훅. 이 웨이브는 종 등록을 하지 않으므로(MobType 은 다른 트랙 소유)
     * 인터페이스만 열어 두고 {@link #NO_OP} 를 기본 구현으로 쓴다. 다음 웨이브가 가스틀링
     * 스폰을 붙일 때 이 한 지점만 갈아 끼우면 된다.
     */
    public interface GhastlingRevivalSink {
        /** 말린 가스트가 있던 칸에서 가스틀링 소생을 요청한다. */
        void reviveGhastling(int x, int y, int z);

        /** 종 등록 전의 기본 구현. 소생 조건 판정 자체는 이미 권위에서 돌고 있다. */
        GhastlingRevivalSink NO_OP = (x, y, z) -> { };
    }

    /**
     * 이 수화 단계에서 가스틀링까지 남은 MC 틱. 물에 잠긴 채로 이만큼 더 있어야 소생한다.
     * 0 단계에서는 {@link #REVIVAL_MC_TICKS} 그대로다.
     */
    public static long remainingMcTicksToRevival(int currentHydration) {
        return REVIVAL_MC_TICKS - (long) clamp(currentHydration) * STAGE_MC_TICKS;
    }

    /** 상태 바이트 → 수화 단계(0~3). */
    public static int hydration(int state) {
        return state & HYDRATION_MASK;
    }

    /** 수화 단계 → 상태 바이트. 범위를 벗어난 값은 잘라 넣는다. */
    public static int stateFor(int hydration) {
        return clamp(hydration) & HYDRATION_MASK;
    }

    /** 상태의 facing 비트를 지킨 채 수화 단계만 바꾼 상태 바이트. */
    public static int withHydration(int state, int hydration) {
        return state & FACING_MASK | stateFor(hydration);
    }

    /** 이 블록이 말린 가스트인가. */
    public static boolean isDriedGhast(int blockType) {
        return blockType == Blocks.DRIED_GHAST;
    }

    /** 여섯 이웃 중 하나라도 물인가. 콘크리트 경화와 같은 술어를 쓴다. */
    public static boolean submerged(ConcreteRules.BlockLookup lookup, int x, int y, int z) {
        return ConcreteRules.touchesWater(lookup, x, y, z);
    }

    /**
     * 누적 시간이 만드는 다음 수화 단계.
     *
     * <p>물에 잠겨 있으면 {@link #STAGE_MC_TICKS} 마다 1 오르고, 잠겨 있지 않으면 같은 속도로
     * 내려간다. 소생 판정은 {@link #revives} 가 따로 본다 — 단계 산술과 소생 판정을 한
     * 함수에 섞으면 "3 에서 멈춘 것" 과 "3 을 넘겨 소생할 것" 이 구분되지 않는다.
     *
     * @param elapsedMcTicks 마지막 단계 변화 이후 누적된 MC 틱. 음수는 0 으로 본다.
     */
    public static int nextHydration(int currentHydration, boolean submerged, long elapsedMcTicks) {
        int current = clamp(currentHydration);
        if (elapsedMcTicks <= 0) return current;
        long steps = elapsedMcTicks / STAGE_MC_TICKS;
        if (steps == 0) return current;
        long next = submerged ? current + steps : current - steps;
        if (next < 0) return 0;
        return next > MAX_HYDRATION ? MAX_HYDRATION : (int) next;
    }

    /**
     * 이번 누적으로 가스틀링이 되는가. 바닐라는 <b>3 단계에 도달한 뒤 한 구간을 더</b>
     * 물에 잠겨 있어야 소생한다 — 3 단계에 막 올라선 순간에는 아직 소생하지 않는다.
     */
    public static boolean revives(int currentHydration, boolean submerged, long elapsedMcTicks) {
        if (!submerged || elapsedMcTicks <= 0) return false;
        long steps = elapsedMcTicks / STAGE_MC_TICKS;
        return clamp(currentHydration) + steps > MAX_HYDRATION;
    }

    private static int clamp(int value) {
        if (value < 0) return 0;
        return Math.min(value, MAX_HYDRATION);
    }
}
