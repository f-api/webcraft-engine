package com.gameexpert.engine.blocks;

import com.gameexpert.terrain.Blocks;

/** p3-lush 블록의 서버 규칙 확장점. */
public final class P3Rules {
    public static final int DRIPLEAF_UPRIGHT = 0;
    public static final int DRIPLEAF_UNSTABLE = 1;
    public static final int DRIPLEAF_PARTIAL = 2;
    public static final int DRIPLEAF_FULL = 3;

    private static final int DRIPLEAF_TILT_MASK = 3;
    private static final int DRIPLEAF_FACING_MASK = 12;
    // Java의 20 TPS 지연(10/10/100)을 이 서버의 10 TPS 시간축으로 환산한다.
    public static final int DRIPLEAF_SHORT_DELAY_TICKS = 5;
    public static final int DRIPLEAF_RECOVERY_TICKS = 50;

    private P3Rules() {}

    /** Natural placement facing (0..3) with the vanilla upright tilt state. */
    public static int bigDripleafUprightState(int facing) {
        return (Math.floorMod(facing, 4) << 2) | DRIPLEAF_UPRIGHT;
    }

    public static double collisionHeight(int blockId, int state, double current) {
        if (blockId != Blocks.BIG_DRIPLEAF) return current;
        return (state & DRIPLEAF_TILT_MASK) == DRIPLEAF_FULL ? 0.0 : current;
    }

    public static boolean requiresSupport(int blockId) {
        return blockId >= Blocks.GLOW_LICHEN && blockId <= Blocks.FLOWERING_AZALEA;
    }

    /** 천장·벽·바닥 부착식 식물의 지지 규칙. */
    public static boolean hasRequiredSupport(int blockId, boolean supportedBelow,
            boolean supportedAbove, boolean attachedToWall, boolean current) {
        if (blockId == Blocks.GLOW_LICHEN) return supportedBelow || supportedAbove || attachedToWall;
        if (blockId == Blocks.HANGING_ROOTS || blockId == Blocks.SPORE_BLOSSOM) return supportedAbove;
        if (blockId == Blocks.SMALL_DRIPLEAF || blockId == Blocks.BIG_DRIPLEAF
                || blockId == Blocks.AZALEA || blockId == Blocks.FLOWERING_AZALEA) {
            return supportedBelow;
        }
        return current;
    }

    public static boolean isBoneMealTarget(int blockId, int state) {
        // 진달래 나무 생성은 현재 한 칸 치환 API로 표현할 수 없어 대상이라고 거짓 보고하지 않는다.
        return blockId == Blocks.SMALL_DRIPLEAF;
    }

    /** 뼈티가루 성공 시 현재 칸을 치환할 블록. 0은 무변경이다. */
    public static int boneMealResult(int blockId, int state, int blockAbove) {
        if (blockId == Blocks.SMALL_DRIPLEAF && blockAbove == Blocks.AIR) return Blocks.BIG_DRIPLEAF;
        return 0;
    }

    /** 플레이어가 밟판을 밟은 순간의 큰 흘림잎 상태 전이. */
    public static int dripleafStateAfterStep(int blockId, int state) {
        if (blockId == Blocks.BIG_DRIPLEAF && (state & DRIPLEAF_TILT_MASK) == DRIPLEAF_UPRIGHT) {
            return (state & DRIPLEAF_FACING_MASK) | DRIPLEAF_UNSTABLE;
        }
        return state;
    }

    /** 현재 상태의 지연이 끝난 뒤 다음 상태. */
    public static int dripleafStateAfterDelay(int blockId, int state) {
        if (blockId != Blocks.BIG_DRIPLEAF) return state;
        int facing = state & DRIPLEAF_FACING_MASK;
        return switch (state & DRIPLEAF_TILT_MASK) {
            case DRIPLEAF_UNSTABLE -> facing | DRIPLEAF_PARTIAL;
            case DRIPLEAF_PARTIAL -> facing | DRIPLEAF_FULL;
            case DRIPLEAF_FULL -> facing | DRIPLEAF_UPRIGHT;
            default -> state;
        };
    }

    /** 상태 전이 후 다음 전이까지의 10 TPS 서버 틱. 0은 예약 없음이다. */
    public static int dripleafDelayTicks(int blockId, int state) {
        if (blockId != Blocks.BIG_DRIPLEAF) return 0;
        int tilt = state & DRIPLEAF_TILT_MASK;
        if (tilt == DRIPLEAF_UNSTABLE || tilt == DRIPLEAF_PARTIAL) return DRIPLEAF_SHORT_DELAY_TICKS;
        if (tilt == DRIPLEAF_FULL) return DRIPLEAF_RECOVERY_TICKS;
        return 0;
    }

    /** 가위 조건이 있는 작은 흘림잎 등의 채굴 드랍 보정. */
    /** [SURV-X] 인챈트 마스크를 함께 받는 채굴 드랍(현재 팩 규칙은 마스크에 의존하지 않는다). */
    public static int minedDrop(int blockId, boolean shears, long enchantments,
            double randomRoll, int currentDrop) {
        return minedDrop(blockId, shears, randomRoll, currentDrop);
    }

    public static int minedDrop(int blockId, boolean shears, double randomRoll, int currentDrop) {
        if (blockId == Blocks.GLOW_LICHEN || blockId == Blocks.HANGING_ROOTS
                || blockId == Blocks.SMALL_DRIPLEAF) return shears ? blockId : Blocks.AIR;
        return currentDrop;
    }
}
