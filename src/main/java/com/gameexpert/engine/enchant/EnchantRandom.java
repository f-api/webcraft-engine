package com.gameexpert.engine.enchant;

/**
 * [SURV-X] 인챈트 제안 전용 32비트 xorshift 난수기.
 *
 * <p>{@code java.util.Random}은 48비트 LCG라 정적판(JS)에서 같은 수열을 재현하려면 64비트 정수 연산이
 * 필요하다. JS의 {@code number}는 53비트 정수까지만 정확하므로 그 경로는 파리티 위험이다.
 * 그래서 <b>32비트 xorshift</b>만 쓴다. Java {@code int}와 JS의 {@code |0}/{@code >>>} 연산은
 * 둘 다 32비트 2의 보수라 비트 단위로 같은 값을 낸다.
 *
 * <p>상태는 {@code seed | 1}로 시작한다. xorshift는 상태 0에서 영원히 0이므로 홀수로 강제해 이를 막는다.
 *
 * <p>{@link #nextInt(int)}는 부호 없는 32비트 값을 {@code bound}로 나눈 나머지다.
 * 나머지 편향(modulo bias)은 <b>의도적</b>이다. 바닐라와 완전히 같은 분포를 재현하는 것이 목적이 아니라
 * 두 권위가 <b>같은 수열</b>을 내는 것이 목적이며, 실제 bound(8, 50, 1001 …)에서 편향은 무시할 수준이다.
 *
 * <p><b>슬롯 시드는 반드시 {@link #forSlot(int, int)} 로 만든다.</b> {@code seed | 1}은 이웃한 두 시드를
 * 같은 상태로 뭉개므로, 제안 3줄이 {@code enchantSeed + slot}을 그대로 쓰면 세 줄 중 둘이 항상 같은
 * 수열이 된다(바닐라는 {@code Random.setSeed}의 {@code 0x5DEECE66D} 뒤섞기가 이를 막는다).
 */
public final class EnchantRandom {

    /** 슬롯 시드 뒤섞기의 Weyl 증분(황금비). */
    private static final int SLOT_SEED_INCREMENT = 0x9E3779B9;
    /** 뒤섞기 1단 곱수(murmur3 fmix32). */
    private static final int SLOT_SEED_MIX_A = 0x85EBCA6B;
    /** 뒤섞기 2단 곱수(murmur3 fmix32). */
    private static final int SLOT_SEED_MIX_B = 0xC2B2AE35;

    private int state;

    public EnchantRandom(int seed) {
        this.state = seed | 1;
    }

    /**
     * 제안 {@code slot}의 시드. {@code enchantSeed + slot * 0x9E3779B9}를 murmur3 fmix32로 뒤섞어
     * 이웃 슬롯이 독립된 수열을 갖게 한다. 곱셈은 양판 모두 32비트로 래핑되므로
     * (Java {@code int}, JS {@code Math.imul}) 비트 단위로 같은 값을 낸다.
     */
    public static int slotSeed(int enchantSeed, int slot) {
        int z = enchantSeed + slot * SLOT_SEED_INCREMENT;
        z ^= z >>> 16;
        z *= SLOT_SEED_MIX_A;
        z ^= z >>> 13;
        z *= SLOT_SEED_MIX_B;
        z ^= z >>> 16;
        return z;
    }

    /** 제안 {@code slot} 전용 난수기. 슬롯 시드는 {@link #slotSeed(int, int)}가 정본이다. */
    public static EnchantRandom forSlot(int enchantSeed, int slot) {
        return new EnchantRandom(slotSeed(enchantSeed, slot));
    }

    /** 현재 내부 상태. 디버그·테스트 증빙용이다. */
    public int state() {
        return state;
    }

    /** 다음 32비트 난수(부호 있는 {@code int} 그대로). */
    public int next() {
        int s = state;
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        state = s;
        return s;
    }

    /** {@code [0, bound)} 구간의 정수. {@code bound <= 1}이면 언제나 0이며 난수를 소비하지 않는다. */
    public int nextInt(int bound) {
        if (bound <= 1) return 0;
        return (int) (Integer.toUnsignedLong(next()) % bound);
    }
}
