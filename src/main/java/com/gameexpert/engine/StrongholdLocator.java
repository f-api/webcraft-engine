package com.gameexpert.engine;

import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess;

import java.util.List;

/**
 * 엔더의 눈이 쓰는 가장 가까운 요새 위치. 바닐라 26.3
 * {@code ServerLevel#findNearestMapStructure(EYE_OF_ENDER_LOCATED, pos, 100, false)} 의 요새 갈래다.
 *
 * <p>{@code #minecraft:eye_of_ender_located} 태그의 유일한 구조물은 {@code minecraft:stronghold}
 * 이고 그 배치는 {@code minecraft:strongholds} 동심원({@code ConcentricRingsStructurePlacement})
 * 하나다. 동심원 갈래({@code ChunkGenerator#getNearestGeneratedStructure(Set, ServerLevel,
 * StructureManager, BlockPos, boolean, ConcentricRingsStructurePlacement)})는 탐색 반경을 쓰지
 * 않고 128 개 링 청크를 목록 순서대로 돌며 청크의 {@code (x·16+8, 32, z·16+8)} 과 호출 좌표의
 * {@code distSqr} 가 지금까지의 최솟값보다 <b>엄격히</b> 작을 때만 바꾼다(동률이면 먼저 나온 링).
 * 결과 좌표는 {@code StructurePlacement#getLocatePos} = 청크 최소 블록 좌표 + locate offset
 * (요새는 빈 offset 이라 (x·16, 0, z·16))이다.
 *
 * <p>링 청크는 청크를 생성하지 않고 월드 시드만으로 계산한다 — 생산 경로와 같은
 * {@link Mc263StructureSetStartPlanner#buildState} 의 {@code strongholdRingPositions}
 * ({@code ChunkGeneratorStructureState#generateRingPositions}; 선호 바이옴 탐색 포함)이며,
 * {@code world-sampler-receipts-v1} 공식 영수증이 세 시드의 384 링을 값 그대로 고정한다.
 * Rust 쪽 같은 계산은 {@code standalone_terrain_stronghold_ring_chunk} 로 정적판에 노출된다.
 *
 * <p>요새 구조물의 {@code biomes} 는 {@code #minecraft:has_structure/stronghold}
 * (= {@code #minecraft:is_overworld}) 라 링 청크의 시작 판정이 바이옴으로 떨어지지 않는다.
 * 그래서 {@code getStructureGeneratingAt} 의 START_PRESENT 확인은 모든 링 청크에서 참이다.
 *
 * <p>엔진 패키지에 둔다 — terrain 패키지의 Java 소스 집합은 월드 생성기 정체성
 * ({@code verify-terrain-source-identity}) 입력이라, 생성기를 읽기만 하는 조회가 그 정체성을
 * 흔들지 않게 한다.
 */
public final class StrongholdLocator {
    /** {@code SectionPos.sectionToBlockCoord(chunk, 8)} 의 8. */
    private static final int CHUNK_CENTER_OFFSET = 8;
    /** 동심원 갈래가 거리 비교에 쓰는 고정 높이. */
    private static final int COMPARISON_Y = 32;

    private StrongholdLocator() {
    }

    /** 월드 시드의 128 요새 링 청크(생성 순서). 생산 planner 와 같은 상태에서 읽는다. */
    public static List<BlockPos> ringChunks(long worldSeed) {
        return Mc263StructureWorldAccess.overworld(worldSeed).buildState().strongholdRingPositions();
    }

    /**
     * 가장 가까운 요새의 locate 좌표. 링이 없으면 null(바닐라는 이때 눈을 소비하지 않는다).
     *
     * @param x,y,z 플레이어 {@code blockPosition()}
     */
    public static BlockPos nearestLocatePos(List<BlockPos> ringChunks, int x, int y, int z) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos chunk : ringChunks) {
            double dx = (double) (chunk.x() * 16 + CHUNK_CENTER_OFFSET) - x;
            double dy = (double) COMPARISON_Y - y;
            double dz = (double) (chunk.z() * 16 + CHUNK_CENTER_OFFSET) - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (best == null || distance < bestDistance) {
                best = chunk;
                bestDistance = distance;
            }
        }
        return best == null ? null : new BlockPos(best.x() * 16, 0, best.z() * 16);
    }
}
