package com.gameexpert.engine.mob;

/**
 * 주입식 난수(테스트 재현성). 시드 고정 시 스폰 확률 분포·배회가 결정론적으로 재현된다.
 * 운영에서는 {@link JavaMobRandom} 로 java.util.Random 을 래핑해 쓴다.
 */
public interface MobRandom {
    /** [0, bound) 정수. bound<=0 이면 0 을 반환한다. */
    int nextInt(int bound);
    /** [0.0, 1.0) float. 운영 구현은 바닐라 RandomSource와 같은 24-bit 난수 소비를 재정의한다. */
    default float nextFloat() { return (float) nextDouble(); }
    /** [0.0, 1.0) 실수. */
    double nextDouble();
}
