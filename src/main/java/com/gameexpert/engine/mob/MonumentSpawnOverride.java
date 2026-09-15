package com.gameexpert.engine.mob;

import com.gameexpert.engine.structure.OceanMonumentPlacement;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry.SpawnCategory;

/**
 * [MONUMENT] 해저 신전의 바닐라 {@code spawn_overrides} 를 자연 스폰 루프에 거는 순수 규칙.
 *
 * <p>근거({@code docs/research/mc-vanilla-1214/structure/monument.json}):
 *
 * <pre>
 * "spawn_overrides": {
 *   "axolotls":                   { "bounding_box": "full", "spawns": [] },
 *   "underground_water_creature": { "bounding_box": "full", "spawns": [] },
 *   "monster": { "bounding_box": "full",
 *     "spawns": [{ "type":"minecraft:guardian", "weight":1,
 *                  "minCount":2, "maxCount":4 }] } }
 * </pre>
 *
 * <p>바닐라 {@code ChunkGenerator#getMobsAt} 는 좌표를 감싸는 구조물이 그 카테고리의
 * override 를 들고 있으면 <b>바이옴 표 전체를 그 표로 갈아끼운다</b>. {@code "full"} 은 구조물
 * <b>전체 경계 상자</b>(조각이 아니라)를 뜻한다. 그래서 신전 상자 안에서는 일반 수중/지하수
 * 스폰이 사라지고, 몬스터 자리는 가디언 하나짜리 표(무리 2–4)만 남는다. 이것이 신전을 비워도
 * 가디언이 <b>다시 차오르는</b> 유일한 경로다(엘더 3기는 배치 명단이라 재스폰이 없다).
 *
 * <p><b>상자 판정의 divergence</b>: 바닐라는 저장된 {@code StructureStart} 를 좌표로 조회한다.
 * 이 저장소는 구조물 시작을 영속화하지 않으므로, 같은 판정을 <b>시드에서 다시 유도</b>한다 —
 * {@link OceanMonumentPlacement} 의 격자(= 바닐라 {@code getPotentialFeatureChunk})가 낸 후보
 * 청크와 {@link StructureSiteDescriptor} 의 방위로 본체 58 × 23 × 58 상자를 그대로 계산하고,
 * 후보 청크 중심의 noise 바이옴이 {@code #minecraft:is_deep_ocean} 인지만 함께 본다
 * (= {@code OceanMonumentGenerator.allowsBiome} 의 첫 관문). 생성기가 추가로 거는 반경 29
 * 주변 바이옴 검사·수심 검사·복셀 하한은 이 판정에 넣지 않는다 — 그 셋은 청크 블록을 읽어야
 * 하고, 스폰 판정이 청크 상주 여부에 따라 달라지면 두 권위가 갈리기 때문이다. 남는 차이는
 * "깊은 바다 칸인데 신전이 실제로는 서지 못한 자리"에서 일반 수중 스폰 대신 가디언 표가
 * 걸리는 것뿐이고, 그 자리도 물이라야 가디언이 실제로 놓인다.
 */
public final class MonumentSpawnOverride {

    /** 본체 한 변(바닐라 58). */
    private static final int SIZE = OceanMonumentPlacement.SIZE_XZ;
    /** 본체 원점의 anchor 기준 오프셋(바닐라 -29). */
    private static final int ORIGIN = OceanMonumentPlacement.BASE_OFFSET;
    /** 후보 청크 중심의 anchor 기준 오프셋(바닐라 {@code ChunkPos#getMiddleBlockPosition} = +8). */
    private static final int CHUNK_CENTER = 8;
    /** 상자 y 범위(바닐라 39..61). */
    public static final int MIN_Y = OceanMonumentPlacement.BASE_Y;
    public static final int MAX_Y = OceanMonumentPlacement.BASE_Y + OceanMonumentPlacement.SIZE_Y - 1;

    private MonumentSpawnOverride() {}

    /** {@code spawn_overrides} 가 덮는 카테고리인가. 나머지 카테고리는 바이옴 표를 그대로 쓴다. */
    public static boolean overridesCategory(SpawnCategory category) {
        return category == SpawnCategory.MONSTER
                || category == SpawnCategory.AXOLOTLS
                || category == SpawnCategory.UNDERGROUND_WATER_CREATURE;
    }

    /** 덮인 카테고리의 표 가중치 합. 몬스터만 가디언 하나(weight 1)이고 나머지는 빈 표다. */
    public static int overrideWeightTotal(SpawnCategory category) {
        return category == SpawnCategory.MONSTER ? 1 : 0;
    }

    /** 몬스터 표의 유일한 항목. */
    public static MobType overrideType(SpawnCategory category) {
        return category == SpawnCategory.MONSTER ? MobType.GUARDIAN : null;
    }

    public static int overrideMinCount(SpawnCategory category) {
        return category == SpawnCategory.MONSTER
                ? OceanMonumentPlacement.GUARDIAN_MIN_COUNT : 0;
    }

    public static int overrideMaxCount(SpawnCategory category) {
        return category == SpawnCategory.MONSTER
                ? OceanMonumentPlacement.GUARDIAN_MAX_COUNT : 0;
    }

    /**
     * 좌표를 감싸는 후보 신전 본체 상자 {@code {minX,minY,minZ,maxX,maxY,maxZ}} 또는 {@code null}.
     * 바이옴 관문은 보지 않는다 — 순수 격자 유도라 두 권위가 블록 없이 같은 값을 낸다.
     */
    public static int[] candidateBounds(int seed, int x, int z) {
        int[] cell = candidateCell(seed, x, z);
        return cell == null ? null : boundsAtCell(seed, cell[0], cell[1]);
    }

    /**
     * 좌표를 덮는 후보 신전의 <b>격자 칸</b> {@code {cellX,cellZ}} 또는 {@code null}.
     * 바이옴 표본점은 상자가 아니라 그 칸의 anchor 에서 나오므로(생성기와 같은 점), 상자만
     * 돌려주면 표본점을 되찾을 수 없다 — 그래서 칸 자체를 이 자리 하나에서 찾는다.
     */
    private static int[] candidateCell(int seed, int x, int z) {
        int cellSize = OceanMonumentPlacement.CELL_SIZE;
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        // 본체는 anchor 에서 최대 29칸 물러나므로(-29) 칸 시작 앞쪽으로만 넘친다 —
        // 즉 이 좌표를 덮을 수 있는 신전은 자기 칸이거나 <b>바로 다음</b> 칸의 것이다.
        for (int stepX = 0; stepX <= 1; stepX++) {
            for (int stepZ = 0; stepZ <= 1; stepZ++) {
                int[] bounds = boundsAtCell(seed, cellX + stepX, cellZ + stepZ);
                if (x >= bounds[0] && x <= bounds[3] && z >= bounds[2] && z <= bounds[5]) {
                    return new int[] {cellX + stepX, cellZ + stepZ};
                }
            }
        }
        return null;
    }

    /** 격자 칸 하나가 내는 후보 신전의 본체 상자. {@code OceanMonumentGenerator.bounds} 와 같다. */
    public static int[] boundsAtCell(int seed, int cellX, int cellZ) {
        StructureSiteDescriptor site = StructureSiteDescriptor.atCell(
                seed, StructureSiteDescriptor.Kind.OCEAN_MONUMENT, cellX, cellZ);
        int[] corner = corner(site.anchorX(), site.anchorZ(), site.direction(), 0, 0);
        int[] opposite = corner(site.anchorX(), site.anchorZ(), site.direction(),
                SIZE - 1, SIZE - 1);
        return new int[] {
            Math.min(corner[0], opposite[0]), MIN_Y, Math.min(corner[1], opposite[1]),
            Math.max(corner[0], opposite[0]), MAX_Y, Math.max(corner[1], opposite[1]),
        };
    }

    /**
     * 후보 <b>청크 중심</b>의 noise 바이옴. 바닐라 {@code has_structure/ocean_monument} 의 첫 관문.
     *
     * <p>표본점은 생성기와 <b>같은 점</b>이어야 한다 — {@code OceanMonumentGenerator.allowsBiome}
     * 는 {@code (anchorX + 8, seaLevel, anchorZ + 8)} 을 읽는다. anchor 는 후보 청크의 원점
     * ({@code chunkX * 16})이므로 {@code +8} 이 곧 바닐라 {@code ChunkPos#getMiddleBlockPosition}
     * 이고, 구조물 배치 판정이 쓰는 그 자리다. 본체 상자(58 × 58)의 중심은 방위 회전에 따라
     * anchor 에서 최대 29칸 떨어진 <b>다른 칸</b>이라 경계 바이옴에서 생성기와 갈렸다.
     */
    public static boolean deepOceanCell(MobWorldView world, int seed, int cellX, int cellZ) {
        StructureSiteDescriptor site = StructureSiteDescriptor.atCell(
                seed, StructureSiteDescriptor.Kind.OCEAN_MONUMENT, cellX, cellZ);
        return OceanMonumentPlacement.isDeepOcean(world.noiseBiomeAtQuart(
                (site.anchorX() + CHUNK_CENTER) >> 2, Blocks.SEA_LEVEL >> 2,
                (site.anchorZ() + CHUNK_CENTER) >> 2));
    }

    /** 이 칸이 신전 {@code bounding_box: "full"} 안인가. */
    public static boolean within(MobWorldView world, int x, int y, int z) {
        if (StructureSiteDescriptor.Kind.OCEAN_MONUMENT.placementStage()
                == StructureSiteDescriptor.PlacementStage.REMOVED) return false;
        if (y < MIN_Y || y > MAX_Y) return false;
        int seed = world.worldSeed();
        int[] cell = candidateCell(seed, x, z);
        return cell != null && deepOceanCell(world, seed, cell[0], cell[1]);
    }

    private static int[] corner(int anchorX, int anchorZ, int direction, int u, int w) {
        int localX = u + ORIGIN;
        int localZ = w + ORIGIN;
        int[] rotated = switch (direction) {
            case 0 -> new int[] {localX, localZ};
            case 1 -> new int[] {-localZ, localX};
            case 2 -> new int[] {-localX, -localZ};
            default -> new int[] {localZ, -localX};
        };
        return new int[] {anchorX + rotated[0], anchorZ + rotated[1]};
    }
}
