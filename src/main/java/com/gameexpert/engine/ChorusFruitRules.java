package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * [GOLD-FOOD] 후렴과 섭취 순간이동의 <b>정본 굴림</b>이다.
 *
 * <p>바닐라 {@code ChorusFruitItem#finishUsingItem} 은 최대 16 회 시도하고, 매 시도마다
 * x/z 를 {@code (random - 0.5) * 16}(= ±8), y 를 {@code randomInt(16) - 8}(= -8..+7) 만큼
 * 흔든 뒤 {@code randomTeleport(.., true)} 로 <b>발밑이 채워질 때까지 아래로 내려</b> 안전한
 * 자리를 찾는다. 하나라도 성공하면 그 자리로 가고, 16 회 모두 실패하면 <b>아무 일도 없다</b>
 * (아이템은 이미 소비된 뒤다). [B] minecraft.wiki «Chorus Fruit».
 *
 * <p><b>양 권위 동일 굴림</b>: 무작위원을 주입받지 않고 <b>좌표·틱에서 유도한 시드</b>만
 * 쓴다. 온라인(Java)과 정적판(TypeScript)이 같은 splitmix64 수열을 읽으므로 같은 상황에서
 * 같은 목적지가 나온다 — {@code RaidLedger.rewardSeed} 가 재접속·재시도에도 같은 보상을
 * 내는 것과 같은 계약이다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneChorusFruit.ts} 다.
 *
 * <p>스레딩: 순수 함수 모음이라 상태가 없다.
 */
public final class ChorusFruitRules {

    /** 바닐라 시도 횟수. 전부 실패하면 순간이동이 일어나지 않는다. */
    public static final int ATTEMPTS = 16;
    /** 세 축 모두 ±8 블록. 바닐라 {@code (random - 0.5) * 16} 과 {@code randomInt(16) - 8}. */
    public static final int SPREAD = 8;

    // [GOLD-FOOD] 후렴과 전용 쿨다운 상수는 **일부러 없다**. 바닐라 사용 쿨다운은 20 MC 틱
    // (= 10 TPS 권위 틱 10회)이지만, 이 저장소의 소비는 전부 {@code PlayerTickState} 의
    // 일반 소비 게이트를 지난다 — {@code CONSUME_INTERVAL_TICKS(16) - CONSUME_TOLERANCE_TICKS(1)}
    // = **권위 틱 15회** 간격이라 후렴과 10 보다 엄격하다. 따라서 별도 상수를 두면 프로덕션
    // 호출부가 0 인 장식 상수가 되고, 두 값이 어긋나도 아무도 실패하지 않는 함정이 된다.
    // 후렴과 쿨다운은 그 일반 게이트에 **위임**한다(더 엄격한 쪽이 이겨 바닐라 하한을 지킨다).

    /** 한 좌표의 블록 ID 를 돌려주는 조회기. 청크 밖이면 {@link Blocks#AIR} 를 준다. */
    @FunctionalInterface
    public interface BlockProbe {
        int blockAt(int x, int y, int z);
    }

    /** 굴림 결과. {@link #found} 가 false 면 16 회 전부 실패해 제자리에 남는다. */
    public record Destination(boolean found, int x, int y, int z) {
        public static final Destination NONE = new Destination(false, 0, 0, 0);
    }

    private ChorusFruitRules() {
    }

    /**
     * 이 섭취가 쓸 굴림 시드. 좌표(정수 블록)와 틱만으로 정해지므로 두 권위가 같은 값을 얻는다.
     * 같은 틱에 같은 칸에서 두 명이 먹으면 같은 시드가 되지만, 결과 좌표가 같아도 바닐라와
     * 마찬가지로 문제가 없다(둘 다 그 자리로 간다).
     */
    public static long seed(long tick, int blockX, int blockY, int blockZ) {
        long z = tick * 0x9E3779B97F4A7C15L
                + Integer.toUnsignedLong(blockX) * 0xBF58476D1CE4E5B9L
                + Integer.toUnsignedLong(blockY) * 0x94D049BB133111EBL
                + Integer.toUnsignedLong(blockZ) * 0xD6E8FEB86659FD93L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** {@code drawIndex} 번째 난수(부호 없는 64비트). splitmix64 를 시드에 한 번 더 돌린다. */
    public static long stream(long seed, int drawIndex) {
        long z = seed + 0x9E3779B97F4A7C15L * (drawIndex + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** {@code [0, bound)} 균등 정수. */
    public static int draw(long seed, int drawIndex, int bound) {
        return (int) Long.remainderUnsigned(stream(seed, drawIndex), bound);
    }

    /**
     * 발 위치가 {@code (feetX, feetY, feetZ)} 인 플레이어의 순간이동 목적지.
     *
     * <p>매 시도는 세 축 오프셋을 뽑아 후보를 만들고, 그 후보에서 <b>아래로</b> 내려가며
     * 처음 만나는 고체 위 칸을 노린다(바닐라 {@code randomTeleport} 의 하강). 후보 칸과 그 위
     * 칸이 모두 비어 있고 유체가 아니며 발밑이 유체가 아닌 고체일 때만 성공이다.
     */
    public static Destination roll(long seed, int feetX, int feetY, int feetZ, BlockProbe probe) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int base = attempt * 3;
            int x = feetX + draw(seed, base, SPREAD * 2) - SPREAD;
            int z = feetZ + draw(seed, base + 2, SPREAD * 2) - SPREAD;
            int startY = feetY + draw(seed, base + 1, SPREAD * 2) - SPREAD;
            if (startY > Blocks.MAX_Y) startY = Blocks.MAX_Y;
            for (int y = startY; y > Blocks.MIN_Y; y--) {
                int support = probe.blockAt(x, y - 1, z);
                if (!Fluids.isSolid(support) || Fluids.isFluid(support)) continue;
                int feet = probe.blockAt(x, y, z);
                int head = probe.blockAt(x, y + 1, z);
                if (Fluids.isSolid(feet) || Fluids.isFluid(feet)) break;
                if (Fluids.isSolid(head) || Fluids.isFluid(head)) break;
                return new Destination(true, x, y, z);
            }
        }
        return Destination.NONE;
    }
}
