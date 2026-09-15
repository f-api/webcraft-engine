package com.gameexpert.terrain.mc.df;

/** 블록 좌표에서 밀도를 계산하는 순수 함수이다. */
public interface McDensityFunction {
    double compute(Context context);

    default double compute(int x, int y, int z) {
        return compute(new Context(x, y, z));
    }

    /**
     * 청크 평가 중 재사용하는 좌표 값이다. 보간 래퍼는 같은 인스턴스의 좌표를 잠시 바꾼 뒤
     * 반드시 복원하므로, 블록마다 임시 Context를 할당하지 않는다.
     */
    final class Context {
        public int x;
        public int y;
        public int z;
        public final EvaluationCache cache;

        public Context(int x, int y, int z) {
            this(x, y, z, null);
        }

        public Context(int x, int y, int z, EvaluationCache cache) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cache = cache;
        }

        public void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * Density wrapper 캐시는 청크 수명으로 제한된다. NaN은 미조회 표식이며 density 결과로는
     * 생성되지 않는다.
     */
    interface EvaluationCache {
        double get(int slot, int x, int y, int z);
        void put(int slot, int x, int y, int z, double value);
    }
}
