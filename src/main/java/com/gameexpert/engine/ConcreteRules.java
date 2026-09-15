package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * [CONCRETE] 콘크리트 가루가 물에 닿아 굳는 규칙(MC Java 1.21.4 ConcretePowderBlock).
 *
 * <p>바닐라는 이 사실을 <b>세 곳</b>에서 확인한다: 가루가 낙하해 착지할 때
 * ({@code FallingBlockEntity#onLand} → {@code shouldSolidify}), 손으로 놓을 때
 * ({@code getStateForPlacement}), 그리고 이웃이 바뀔 때({@code neighborChanged} — 실제로는
 * "물이 흘러와 접촉" 하는 경우다). 셋 다 같은 술어 {@code touchesLiquid} 를 본다.
 *
 * <p>이 저장소에서도 술어는 하나이고, <b>호출 지점도 권위당 한 곳</b>이다. 두 권위 모두
 * 모든 블록 변경이 한 틱 변경 목록으로 모이므로(온라인 {@code WorldRuntime.tickBlockChanges} ·
 * 정적판 {@code pendingSupportChanges}), 그 목록을 한 번 훑으면 세 트리거가 전부 잡힌다:
 * <ul>
 *   <li>낙하 착지 — {@code FallingBlockSystem} 이 {@code fluidSim::applyChange} 로 쓴 가루</li>
 *   <li>손 설치 — {@code WorldTickLoop} 의 blockEdit 이 쓴 가루</li>
 *   <li>물이 흘러옴 — {@code FluidSimulator} 가 쓴 물(이웃 가루를 굳힌다)</li>
 * </ul>
 * 트리거마다 갈고리를 따로 다는 대신 "변경된 칸" 이라는 이미 있는 합류점을 쓰는 것이
 * 세 지점이 서로 어긋나지 않게 하는 근거다.
 *
 * <p><b>합류점을 쓰기 때문에 되살려야 하는 두 사실</b>이 있다. 바닐라에서 낙하는 블록이
 * 아니라 {@code FallingBlockEntity} 이므로 (ⓐ) 낙하 중인 가루는 어떤 칸에서도 경화 판정을
 * 받지 않고, (ⓑ) 착지 때 {@code onLand} 가 "가루가 덮은 칸의 상태" 를 함께 넘긴다. 이
 * 저장소는 낙하도 블록 쓰기라 두 사실이 합류점에서 지워지므로 {@link FallState} 포트로
 * 되돌려 받는다.
 */
public final class ConcreteRules {

    private ConcreteRules() {
    }

    /** 좌표의 블록 ID 조회 포트. {@link SupportRules.BlockLookup} 와 같은 모양이다. */
    @FunctionalInterface
    public interface BlockLookup {
        int getBlock(int x, int y, int z);
    }

    /** 굳은 칸 하나를 쓰는 포트. 온라인은 {@code fluidSim::applyChange} 를 넘긴다. */
    @FunctionalInterface
    public interface Hardened {
        void set(int x, int y, int z, int blockId);
    }

    /**
     * 이번 틱에 "블록으로 존재하지 않았던" 사실을 되돌려 주는 포트.
     *
     * <p>바닐라는 낙하를 엔티티로 표현하므로 두 가지가 공짜로 따라온다. 이 저장소는 낙하도
     * 블록 쓰기라 그 둘을 여기서 받는다.
     * <ul>
     *   <li>{@link #airborne} — 이 칸의 가루가 <b>아직 떨어지는 중</b>인가. 바닐라라면 블록이
     *       아니라 {@code FallingBlockEntity} 라서 어떤 경화 판정도 지나지 않는다. 이 칸을
     *       걸러 주지 않으면 옆으로 물을 스쳐 지나가기만 해도 공중에서 굳는다.</li>
     *   <li>{@link #replaced} — 이 칸이 이번 틱에 <b>무엇을 덮었는가</b>. 바닐라
     *       {@code FallingBlockEntity#onLand} 와 {@code getStateForPlacement} 가
     *       {@code shouldSolidify} 에 넘기는 "대체될 칸의 상태" 다.</li>
     * </ul>
     */
    public interface FallState {

        /** 낙하도 대체도 없었던 변경(유체 확산·이웃 갱신 등)이 보는 기본값. */
        FallState NONE = new FallState() {
            @Override
            public boolean airborne(int x, int y, int z) {
                return false;
            }

            @Override
            public int replaced(int x, int y, int z) {
                return Blocks.AIR;
            }
        };

        boolean airborne(int x, int y, int z);

        int replaced(int x, int y, int z);
    }

    /**
     * 바닐라 {@code ConcretePowderBlock#canSolidify(BlockState)}: 그 칸의 유체 상태가
     * {@code FluidTags.WATER} 인가. {@code shouldSolidify} 의 첫 갈래이며, 인자는 <b>가루가
     * 놓일 칸에 원래 있던 블록</b>이다(가루 자신이 아니다).
     */
    public static boolean canSolidify(int replacedBlockId) {
        return Fluids.isWater(replacedBlockId);
    }

    /**
     * 바닐라 {@code ConcretePowderBlock#shouldSolidify} 두 갈래를 그대로 옮긴 것:
     * {@code canSolidify(replaced) || touchesLiquid(pos)}.
     *
     * <p>첫 갈래가 없으면 "사방이 막힌 한 칸짜리 물웅덩이에 가루를 떨어뜨리거나 놓았을 때"
     * 가 통째로 빠진다 — 가루가 그 물을 덮어 없앴으므로 이웃에는 물이 하나도 남지 않고,
     * 여섯 이웃만 보는 두 번째 갈래로는 영영 굳지 않는다.
     */
    public static boolean shouldSolidify(
            BlockLookup lookup, FallState fall, int x, int y, int z) {
        return canSolidify(fall.replaced(x, y, z)) || touchesWater(lookup, x, y, z);
    }

    /**
     * 여섯 이웃 중 하나라도 물이면 굳는다. 바닐라 {@code ConcretePowderBlock#touchesLiquid} 는
     * 여섯 방향을 모두 보고 {@code FluidTags.WATER} 인지만 따진다 — 흐르는 물도 포함이고
     * 용암은 아니다. {@link Fluids#isWater} 가 그 태그의 이 저장소 대응이다.
     */
    public static boolean touchesWater(BlockLookup lookup, int x, int y, int z) {
        return Fluids.isWater(lookup.getBlock(x + 1, y, z))
                || Fluids.isWater(lookup.getBlock(x - 1, y, z))
                || Fluids.isWater(lookup.getBlock(x, y, z + 1))
                || Fluids.isWater(lookup.getBlock(x, y, z - 1))
                || Fluids.isWater(lookup.getBlock(x, y + 1, z))
                || Fluids.isWater(lookup.getBlock(x, y - 1, z));
    }

    /**
     * 변경된 칸 하나가 만드는 경화를 전부 적용한다.
     *
     * <p>두 방향을 모두 본다. 바뀐 칸 자체가 가루면 그 칸이 물에 닿았는지 보고(낙하 착지·
     * 손 설치), 바뀐 칸이 물이면 여섯 이웃의 가루를 본다(물이 흘러옴). 한 쪽만 두면
     * "가루 위로 물이 흘러왔을 때" 또는 "물 옆에 가루를 놓았을 때" 중 하나가 조용히 빠진다.
     *
     * @return 실제로 굳힌 칸 수
     */
    public static int hardenAround(BlockLookup lookup, Hardened hardened, int x, int y, int z) {
        return hardenAround(lookup, hardened, FallState.NONE, x, y, z);
    }

    /** 낙하 사실({@link FallState})까지 아는 판. 틱 루프는 언제나 이 쪽을 부른다. */
    public static int hardenAround(
            BlockLookup lookup, Hardened hardened, FallState fall, int x, int y, int z) {
        int count = 0;
        int changed = lookup.getBlock(x, y, z);
        if (Blocks.isConcretePowder(changed)) {
            count += harden(lookup, hardened, fall, x, y, z);
        } else if (Fluids.isWater(changed)) {
            count += harden(lookup, hardened, fall, x + 1, y, z);
            count += harden(lookup, hardened, fall, x - 1, y, z);
            count += harden(lookup, hardened, fall, x, y, z + 1);
            count += harden(lookup, hardened, fall, x, y, z - 1);
            count += harden(lookup, hardened, fall, x, y + 1, z);
            count += harden(lookup, hardened, fall, x, y - 1, z);
        }
        return count;
    }

    /** 이 칸이 (낙하 중이 아닌) 가루이고 굳어야 하면 같은 색 콘크리트로 바꾼다. */
    private static int harden(
            BlockLookup lookup, Hardened hardened, FallState fall, int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return 0;
        int id = lookup.getBlock(x, y, z);
        if (!Blocks.isConcretePowder(id)) return 0;
        // 아직 떨어지는 중이면 바닐라에서는 블록이 아니다 — 물 옆을 스쳐도 굳지 않는다.
        if (fall.airborne(x, y, z)) return 0;
        if (!shouldSolidify(lookup, fall, x, y, z)) return 0;
        hardened.set(x, y, z, Blocks.concreteForPowder(id));
        return 1;
    }
}
