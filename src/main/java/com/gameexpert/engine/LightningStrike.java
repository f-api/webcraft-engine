package com.gameexpert.engine;

/**
 * 확정된 낙뢰 타격점. 좌표는 브로드캐스트되는 값과 같은 의미다 — 컬럼 중심(x=블록+0.5),
 * 지면 바로 위 칸의 바닥(y=하이트맵+1), 컬럼 중심(z=블록+0.5).
 *
 * <p>낙뢰 파이프라인은 세 단계다.
 * <ol>
 *   <li>날씨 권위가 후보 타격점을 뽑는다(RNG lane — 양 권위가 같은 순서로 소비).</li>
 *   <li>{@link Hook} 이 후보를 옮길 수 있다. 구리 트랙의 <b>피뢰침 유인</b>이 붙을 자리다.</li>
 *   <li>{@link Effects} 가 <b>보정된</b> 좌표에 게임플레이 효과를 적용하고, 같은 좌표가
 *       {@code lightningEvent} 로 방송된다.</li>
 * </ol>
 * 훅이 좌표를 옮기면 방송·효과가 모두 새 좌표를 쓴다는 것이 이 파이프라인의 계약이다.
 */
public record LightningStrike(double x, double y, double z) {

    /** 타격 칸의 블록 x(내림). */
    public int blockX() {
        return (int) Math.floor(x);
    }

    /** 타격 칸의 블록 y(내림). 지면 블록은 이 값 −1 이다. */
    public int blockY() {
        return (int) Math.floor(y);
    }

    /** 타격 칸의 블록 z(내림). */
    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** 블록 칸을 그 칸의 정본 낙뢰 좌표(수평 중심, 바닥 높이)로 바꾼다. */
    public static LightningStrike atBlock(int x, int y, int z) {
        return new LightningStrike(x + 0.5, y, z + 0.5);
    }

    /**
     * 후보 타격점 보정 훅. 반환값이 최종 타격점이 된다. {@code null} 을 돌려주면 후보를 그대로 쓴다.
     *
     * <p>구리 트랙이 여기에 피뢰침 유인(반경 안에서 가장 가까운 피뢰침으로 이동)을 끼운다.
     * 훅은 <b>RNG lane 밖</b>이다 — 날씨 권위의 난수 소비는 훅 호출 전에 모두 끝나므로,
     * 훅을 붙여도 양 권위의 난수열은 갈라지지 않는다.
     */
    @FunctionalInterface
    public interface Hook {
        LightningStrike redirect(LightningStrike candidate);

        /** 유인이 없는 기본 파이프라인. */
        Hook NONE = candidate -> candidate;
    }

    /** 확정 타격점에 게임플레이 효과(피해·발화·변신)를 적용하는 포트. */
    @FunctionalInterface
    public interface Effects {
        void apply(LightningStrike strike);

        /** 효과 없는 연출 전용 파이프라인(단위 테스트·연출 미러). */
        Effects NONE = strike -> { };
    }
}
