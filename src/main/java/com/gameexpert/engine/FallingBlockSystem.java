package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.gameexpert.terrain.Blocks;

/**
 * 모래·자갈 중력 갱신. 별도 엔티티 없이 한 서버 틱에 한 칸씩 기존 블록 변경 경로로 이동한다.
 * 월드당 단일 틱 스레드에서만 사용한다.
 */
public final class FallingBlockSystem {

    private static final Comparator<BlockPos> BOTTOM_TO_TOP = Comparator
            .comparingInt(BlockPos::y)
            .thenComparingInt(BlockPos::x)
            .thenComparingInt(BlockPos::z);

    /** 낙하가 만든 두 블록 변경을 방송·영속 경로에 반영하는 포트. */
    @FunctionalInterface
    public interface BlockChange {
        void apply(int x, int y, int z, int blockId);
    }

    private final SupportRules.BlockLookup lookup;
    private final BlockChange changes;
    private final Set<BlockPos> pending = new HashSet<>();
    private final Set<BlockPos> next = new HashSet<>();
    /**
     * 블록으로 근사한 낙하 엔티티가 현재 칸에서 임시로 밀어낸 유체 ID. 실제
     * FallingBlockEntity 는 유체 셀과 공존하지만 이 구현은 한 칸을 점유하므로, 다음 칸으로
     * 전진할 때 이 값을 원래 칸에 복원한다. AIR/장식은 기록하지 않아 장식 파괴 계약을 유지한다.
     */
    private final Map<BlockPos, Integer> displacedFluids = new HashMap<>();
    /**
     * [CONCRETE] 이번 틱 낙하가 만든 두 사실. 바닐라에서는 낙하가 엔티티라 공짜로 알 수 있는
     * 것들이고(아직 떨어지는 중인가 / 착지하며 무엇을 덮었는가), 여기서는 낙하도 블록 쓰기라
     * 이 두 컬렉션이 그 자리를 대신한다. {@link #tick()} 시작마다 새로 채운다.
     */
    private final Set<BlockPos> airborne = new HashSet<>();
    private final Map<BlockPos, Integer> landedOver = new HashMap<>();

    public FallingBlockSystem(SupportRules.BlockLookup lookup, BlockChange changes) {
        this.lookup = lookup;
        this.changes = changes;
    }

    /** 변경된 칸 자체와 바로 위 칸의 현재 중력 블록을 다음 판정 후보로 등록한다. */
    public void onBlockChanged(int x, int y, int z) {
        enqueueGravity(pending, x, y, z);
        enqueueGravity(pending, x, y + 1, z);
    }

    public void onBlocksChanged(Iterable<BlockPos> positions) {
        for (BlockPos position : positions) {
            onBlockChanged(position.x(), position.y(), position.z());
        }
    }

    /**
     * 후보를 아래부터 결정적으로 검사해 각 블록을 최대 한 칸 이동한다. 이동한 블록과 그 위 블록은
     * 다음 틱 후보로 남겨 열 전체가 손실·복제 없이 연속해서 내려오게 한다.
     *
     * @return 검사 상한 때문에 후보 일부가 다음 틱으로 이월됐으면 {@code true}
     */
    public boolean tick() {
        airborne.clear();
        landedOver.clear();
        if (pending.isEmpty()) {
            return false;
        }

        int candidateCount = pending.size();
        int limit = Math.min(candidateCount, SupportRules.MAX_CASCADE_BLOCKS);
        PriorityQueue<BlockPos> selected = new PriorityQueue<>(
                Math.max(1, limit), BOTTOM_TO_TOP.reversed());
        for (BlockPos position : pending) {
            if (selected.size() < limit) {
                selected.add(position);
            } else if (limit > 0 && BOTTOM_TO_TOP.compare(position, selected.peek()) < 0) {
                next.add(selected.remove());
                selected.add(position);
            } else {
                next.add(position);
            }
        }
        pending.clear();
        List<BlockPos> ordered = new ArrayList<>(selected);
        ordered.sort(BOTTOM_TO_TOP);

        for (BlockPos position : ordered) {
            fallOneCell(position);
        }

        boolean capped = candidateCount > SupportRules.MAX_CASCADE_BLOCKS;
        pending.addAll(next);
        next.clear();
        return capped;
    }

    public int pendingCount() {
        return pending.size();
    }

    /**
     * [CONCRETE] 직전 {@link #tick()} 의 낙하 사실을 {@link ConcreteRules} 가 읽는 모양으로
     * 넘긴다. 경화는 낙하 직후 같은 틱에 돌므로 이 뷰가 그 순간의 진실이다.
     */
    public ConcreteRules.FallState fallState() {
        return new ConcreteRules.FallState() {
            @Override
            public boolean airborne(int x, int y, int z) {
                return airborne.contains(new BlockPos(x, y, z));
            }

            @Override
            public int replaced(int x, int y, int z) {
                return landedOver.getOrDefault(new BlockPos(x, y, z), Blocks.AIR);
            }
        };
    }

    private void fallOneCell(BlockPos position) {
        int x = position.x();
        int y = position.y();
        int z = position.z();
        int blockId = lookup.getBlock(x, y, z);
        if (!SupportRules.isGravityBlock(blockId) || y <= Blocks.MIN_Y
                || !SupportRules.canGravityFallInto(lookup.getBlock(x, y - 1, z))) {
            // 외부 변경으로 낙하가 끝났거나 후보가 다른 블록으로 바뀌었다. 현재 점유 칸의
            // 유체는 최종 착지가 밀어낸 것이므로 복원하지 않는다.
            displacedFluids.remove(position);
            return;
        }

        // 원점 제거 후 목적지 치환. 낙하 중 임시로 밀어낸 유체만 원점에 되돌리고,
        // 비고체 장식은 바닐라처럼 파괴된 채 AIR가 된다.
        int replaced = lookup.getBlock(x, y - 1, z);
        int restored = displacedFluids.getOrDefault(position, Blocks.AIR);
        displacedFluids.remove(position);
        changes.apply(x, y, z, restored);
        changes.apply(x, y - 1, z, blockId);

        // [CONCRETE] 바닐라 FallingBlockEntity 는 더 내려갈 수 있으면 아직 엔티티이고, 못
        // 내려가는 그 순간이 onLand 다. "다음 칸으로 갈 수 있는가" 가 그 두 상태의 경계다.
        BlockPos destination = new BlockPos(x, y - 1, z);
        if (y - 1 <= Blocks.MIN_Y
                || !SupportRules.canGravityFallInto(lookup.getBlock(x, y - 2, z))) {
            landedOver.put(destination, replaced);
        } else {
            airborne.add(destination);
            if (Fluids.isFluid(replaced)) {
                displacedFluids.put(destination, replaced);
            }
        }

        enqueueGravity(next, x, y - 1, z); // 이동한 블록은 다음 틱에도 계속 낙하 가능
        enqueueGravity(next, x, y + 1, z); // 비워진 원점 위 열을 순차적으로 깨운다
    }

    private void enqueueGravity(Set<BlockPos> target, int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y || !SupportRules.isGravityBlock(lookup.getBlock(x, y, z))) {
            return;
        }
        target.add(new BlockPos(x, y, z));
    }
}
