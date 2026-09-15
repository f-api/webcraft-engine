package com.gameexpert.block.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.BlockPos;

import lombok.Getter;

/**
 * [제공코드] 월드별 블록 변경 버퍼(최신 값 승리). 틱 스레드가 넣고, {@link BlockDiffFlusher}가 원자 스왑으로 비웁니다.
 *
 * P4의 즉시 DB upsert를 대체합니다. 한 좌표를 여러 번 바꿔도 마지막 값만 남으며, 주기적으로 한 번에
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 배치로 영속화됩니다.
 */
@Component
public class BlockDiffBuffer {

    @Getter
    public static class Change {
        private final short blockType;
        private final short state;
        /** null이면 플레이어/환경 변경, 값이 있으면 durable 몹 저널 entry key입니다. */
        private final String mobMutationKey;

        public Change(short blockType, short state) {
            this(blockType, state, null);
        }

        public Change(short blockType, short state, String mobMutationKey) {
            this.blockType = blockType;
            this.state = state;
            this.mobMutationKey = mobMutationKey;
        }
    }

    private final Map<Long, Map<BlockPos, Change>> buffers = new ConcurrentHashMap<>();

    public void put(Long worldId, int x, int y, int z, int blockType) {
        put(worldId, x, y, z, blockType, 0);
    }

    public void put(Long worldId, int x, int y, int z, int blockType, int state) {
        put(worldId, x, y, z, blockType, state, null);
    }

    public void put(Long worldId, int x, int y, int z, int blockType, int state,
            String mobMutationKey) {
        buffers.compute(worldId, (key, current) -> {
            Map<BlockPos, Change> buffer = current == null ? new HashMap<>() : current;
            BlockPos position = new BlockPos(x, y, z);
            Change existing = buffer.get(position);
            if (existing != null
                    && existing.blockType == (short) blockType
                    && existing.state == (short) state
                    && java.util.Objects.equals(existing.mobMutationKey, mobMutationKey)) {
                return buffer;
            }
            buffer.put(position, new Change(
                    (short) blockType, (short) state, mobMutationKey));
            return buffer;
        });
    }

    /** 원자 스왑: 현재 버퍼를 떼어내 돌려주고 비웁니다. */
    public Map<BlockPos, Change> drain(Long worldId) {
        AtomicReference<Map<BlockPos, Change>> drained = new AtomicReference<>();
        buffers.computeIfPresent(worldId, (key, current) -> {
            drained.set(current);
            return null;
        });
        return drained.get() == null ? Map.of() : drained.get();
    }

    /** 배치 쓰기 실패 시 되돌립니다(그 사이 새로 들어온 최신 값은 덮지 않도록 putIfAbsent). */
    public void restore(Long worldId, Map<BlockPos, Change> entries) {
        if (entries.isEmpty()) {
            return;
        }
        buffers.compute(worldId, (key, current) -> {
            Map<BlockPos, Change> buffer = current == null ? new HashMap<>() : current;
            entries.forEach(buffer::putIfAbsent);
            return buffer;
        });
    }

    public Set<Long> worldIds() {
        return Set.copyOf(buffers.keySet());
    }

    public boolean hasPending(Long worldId) {
        Map<BlockPos, Change> buffer = buffers.get(worldId);
        return buffer != null && !buffer.isEmpty();
    }
}
