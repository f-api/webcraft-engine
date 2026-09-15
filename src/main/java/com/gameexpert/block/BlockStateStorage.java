package com.gameexpert.block;

import java.util.HashMap;
import java.util.Map;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.engine.BlockPos;
import com.gameexpert.terrain.Blocks;

/**
 * 월드 런타임의 위치별 블록 상태 저장소입니다.
 *
 * 상태 0은 블록 타입의 기본 상태이므로 맵에 넣지 않습니다. 블록 배열과 같은 크기의 상태 배열을
 * 만들지 않아, 상태가 지정된 소수의 설치 블록만 메모리를 사용합니다. 틱 스레드 전용입니다.
 *
 * <p>항목이 없는 칸의 상태는 {@link UnwrittenStateSource} 가 답합니다. 런타임은 그 칸이 아직
 * 생성 블록 그대로면 carrier 가 심은 exact state 의 엔진 투영을 돌려주므로(스냅샷이 클라에 보내는
 * 바이트와 같은 값), 지지·형상 규칙이 매단 랜턴이나 이끼의 부착면을 0 으로 잘못 읽지 않습니다.
 */
public final class BlockStateStorage {

    /** 엔진이 상태를 쓴 적 없는 칸의 상태. 기본값은 0(블록 타입의 기본 상태)입니다. */
    @FunctionalInterface
    public interface UnwrittenStateSource {
        int stateOf(int x, int y, int z, int blockType);
    }

    private static final UnwrittenStateSource DEFAULT_STATE = (x, y, z, blockType) -> 0;

    private static final class StoredState {
        private final short blockType;
        private final byte state;

        private StoredState(int blockType, int state) {
            this.blockType = (short) blockType;
            this.state = (byte) state;
        }
    }

    private final Map<BlockPos, StoredState> states = new HashMap<>();
    private UnwrittenStateSource unwritten = DEFAULT_STATE;

    public BlockStateStorage() {
    }

    /** DB diff를 읽어 재접속 런타임의 희소 상태를 복원합니다. */
    public BlockStateStorage(Iterable<WorldBlockDiff> diffs) {
        for (WorldBlockDiff diff : diffs) {
            set(diff.getX(), diff.getY(), diff.getZ(), diff.getBlockType(), diff.getBlockState());
        }
    }

    /** 항목이 없는 칸의 상태 원천을 연결합니다. {@code null} 이면 기본 상태 0 으로 돌아갑니다. */
    public void setUnwrittenStateSource(UnwrittenStateSource source) {
        unwritten = source == null ? DEFAULT_STATE : source;
    }

    /** 칸의 상태. 엔진이 쓴 값이 없으면 {@link UnwrittenStateSource} 가 답합니다. */
    public int get(int x, int y, int z, int blockType) {
        BlockPos pos = new BlockPos(x, y, z);
        StoredState stored = states.get(pos);
        if (stored == null) {
            return unwritten.stateOf(x, y, z, blockType);
        }
        if (Short.toUnsignedInt(stored.blockType) != blockType) {
            states.remove(pos);
            return unwritten.stateOf(x, y, z, blockType);
        }
        return Byte.toUnsignedInt(stored.state);
    }

    /** AIR 또는 기본 상태 0은 엔트리를 제거해 희소 불변식을 유지합니다. */
    public void set(int x, int y, int z, int blockType, int state) {
        if (state < 0 || state > 255) {
            throw new IllegalArgumentException("블록 상태는 0~255여야 합니다.");
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (blockType == Blocks.AIR || state == 0) {
            states.remove(pos);
        } else {
            states.put(pos, new StoredState(blockType, state));
        }
    }

    public void remove(int x, int y, int z) {
        states.remove(new BlockPos(x, y, z));
    }

    int size() {
        return states.size();
    }

}
