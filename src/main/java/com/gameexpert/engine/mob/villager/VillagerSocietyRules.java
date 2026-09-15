package com.gameexpert.engine.mob.villager;

import com.gameexpert.terrain.Blocks;

/**
 * 상태 없는 주민 사회 규칙: HOME 침대 claim 레인 식별자, 빈 침대 탐색 순서, 철 골렘 소환 위치
 * 탐색이다. {@link VillagerBrainRules}(판정 상수)와 달리 이 계층은 월드를 훑는 순수 탐색만 담는다.
 *
 * <p>근거는 Minecraft Java 1.21.4의 {@code VillagerMakeLove#takeVacantBed}(PoiManager.take,
 * {@code PoiTypes.HOME}, 반경 48)와 {@code Villager#spawnGolemIfNeeded} →
 * {@code SpawnUtil.trySpawnMob(IRON_GOLEM, ..., 10, 8, 6, Strategy.LEGACY_IRON_GOLEM, false)}다.
 *
 * <p>WebCraft divergence(계약):
 * <ul>
 *   <li>빈 침대 탐색은 수평 Chebyshev 링 0..48을 오름차순으로 훑고 수직은
 *       ±{@value #HOME_SEARCH_VERTICAL_REACH}로 제한하며, 후보 서수 예산
 *       {@value #HOME_SCAN_BLOCK_BUDGET}을 소진하면 "빈 침대 없음"으로 끝난다. 완전한 상주
 *       범위는 파생 POI 인덱스가 같은 서수로 고르고, 범위를 모르면 이 원시 탐색으로 돌아온다.</li>
 *   <li>LEGACY_IRON_GOLEM 제외 블록 중 WebCraft에 존재하는 것은 거미줄·선인장·유리·유리판·얼음·
 *       TNT·잎뿐이다(발광석/바다랜턴/신호기/전달체/색유리/서리얼음/색조유리 미구현).</li>
 * </ul>
 */
public final class VillagerSocietyRules {
    /** 침대 claim 레인 이름. 구조물 occupant claim·POI 직업 claim 레인과 겹치지 않는다. */
    public static final String BED_CLAIM_LANE = "VILLAGER_HOME_BED";
    /** standalone 숫자 claim 키가 쓰는 레인 코드. glitch 신호(0x1000_0000)와 다른 값이다. */
    public static final long BED_CLAIM_LANE_CODE = 0x2000_0000L;
    /** claim 자체를 무효화하지 않고 새 결정에만 붙는 정책 판이다. */
    public static final int BED_CLAIM_POLICY_VERSION = 1;

    /** {@code PoiManager.take(..., 48)}와 같은 수평 탐색 반경. */
    public static final int HOME_SEARCH_RADIUS_BLOCKS =
            VillagerBrainRules.POI_SEARCH_RADIUS_BLOCKS;
    /** WebCraft 수직 탐색 반창(divergence). */
    public static final int HOME_SEARCH_VERTICAL_REACH = 8;
    /** WebCraft 탐색 예산(divergence). 소진하면 빈 침대를 못 찾은 것으로 본다. */
    public static final int HOME_SCAN_BLOCK_BUDGET = 32_768;
    /** {@code PoiTypes.HOME.validRange()}=1을 쓰는 침대 사용 거리(맨해튼 아닌 성분별 상한). */
    public static final int HOME_USE_RANGE_BLOCKS = 2;

    /** {@code SpawnUtil.trySpawnMob} attempts. */
    public static final int GOLEM_SPAWN_ATTEMPTS = 10;
    /** {@code SpawnUtil.trySpawnMob} xzSpread. */
    public static final int GOLEM_SPAWN_XZ_SPREAD = 8;
    /** {@code SpawnUtil.trySpawnMob} ySpread. */
    public static final int GOLEM_SPAWN_Y_SPREAD = 6;
    /** 철 골렘 AABB 1.4×2.7이 차지하는 정수 칸 수. */
    public static final int GOLEM_CLEARANCE_CELLS = 3;

    /** 빈 침대 후보 판정. 좌표가 침대 블록이고 아직 claim 되지 않았을 때만 true다. */
    @FunctionalInterface
    public interface VacantBedProbe {
        boolean isVacantBed(int x, int y, int z);
    }

    /** 골렘 배치 탐색이 쓰는 최소 블록 뷰. 두 권위가 같은 분류를 넘긴다. */
    public interface GolemSpawnProbe {
        int blockAt(int x, int y, int z);

        boolean solid(int blockId);

        boolean liquid(int blockId);

        boolean leaves(int blockId);
    }

    /**
     * WebCraft 10TPS 권위 틱 하나는 MC 20TPS 논리 틱 두 개다(MC-REFERENCE 주민 절).
     * 월드 시계는 0..11999라서 MC 일정의 0..23999 축으로 두 배 확대한다.
     */
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;

    private VillagerSocietyRules() {}

    /** 0..11999 월드 시계를 MC 일정 축(0..23999)으로 옮긴다. */
    public static long mcDayTime(long worldTime) {
        return Math.floorMod(worldTime, 12_000L) * MC_TICKS_PER_AUTHORITY_TICK;
    }

    /** 단조 증가하는 MC gameTime. 저장된 절대 일수를 그대로 이어 쓴다. */
    public static long mcGameTime(long dayCount, long worldTime) {
        return dayCount * VillagerBrainRules.DAY_LENGTH_TICKS + mcDayTime(worldTime);
    }

    /** 침대 한 칸의 durable claim 키. Java·standalone 이 같은 문자열을 만든다. */
    public static String bedClaimKey(int x, int y, int z) {
        return BED_CLAIM_LANE + ':' + x + ':' + y + ':' + z;
    }

    /**
     * 원점에서 가장 가까운 빈 침대를 찾는다. 수평 Chebyshev 링 0..48 오름차순, 링 안에서는
     * dy(-8..8) → dx → dz 순서이며 예산을 넘기면 즉시 포기한다.
     *
     * @return {@code {x, y, z}} 또는 못 찾으면 {@code null}
     */
    public static int[] nearestVacantBed(int originX, int originY, int originZ,
            VacantBedProbe probe) {
        return nearestMatchingCell(originX, originY, originZ, HOME_SEARCH_RADIUS_BLOCKS,
                HOME_SEARCH_VERTICAL_REACH, HOME_SCAN_BLOCK_BUDGET, probe::isVacantBed);
    }

    /**
     * POI 색인이 없는 월드(순수 테스트)의 {@link VillagerPoiIndex#homesWithin} 원시 판본: 원점
     * 블록과의 {@code distSqr <= radius²} 인 침대 칸을 같은 후보 서수 오름차순으로 돌려준다.
     */
    public static java.util.List<int[]> homesWithin(int originX, int originY, int originZ,
            int radius, VacantBedProbe probe) {
        java.util.List<long[]> ranked = new java.util.ArrayList<>();
        long limit = (long) radius * radius;
        int minY = Math.max(Blocks.MIN_Y, originY - radius);
        int maxY = Math.min(Blocks.MAX_Y, originY + radius);
        for (int x = originX - radius; x <= originX + radius; x++) {
            for (int z = originZ - radius; z <= originZ + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    long dx = x - (long) originX;
                    long dy = y - (long) originY;
                    long dz = z - (long) originZ;
                    if (dx * dx + dy * dy + dz * dz > limit) continue;
                    if (!probe.isVacantBed(x, y, z)) continue;
                    ranked.add(new long[] { VillagerPoiIndex.candidateOrdinal(originX, originY,
                            originZ, x, y, z, radius, radius), x, y, z });
                }
            }
        }
        ranked.sort((left, right) -> Long.compare(left[0], right[0]));
        java.util.List<int[]> homes = new java.util.ArrayList<>(ranked.size());
        for (long[] row : ranked) homes.add(new int[] { (int) row[1], (int) row[2], (int) row[3] });
        return homes;
    }

    /**
     * POI 탐색의 단일 스캔 순서다. 침대 lane 과 직업지 lane 이 같은 함수를 쓰므로 두 레인이,
     * 그리고 두 권위가, 같은 후보를 같은 순서로 본다: 수평 Chebyshev 링 0..radius 오름차순,
     * 링 안에서는 dy(-verticalReach..verticalReach) → dx → dz 순서이며 예산을 넘기면 포기한다.
     *
     * @return {@code {x, y, z}} 또는 못 찾으면 {@code null}
     */
    public static int[] nearestMatchingCell(int originX, int originY, int originZ,
            int radius, int verticalReach, int blockBudget, VacantBedProbe probe) {
        int budget = blockBudget;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dy = -verticalReach; dy <= verticalReach; dy++) {
                int y = originY + dy;
                if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) continue;
                for (int dx = -ring; dx <= ring; dx++) {
                    int lastDz = Math.abs(dx) == ring ? ring : -ring;
                    for (int dz = -ring; dz <= lastDz; dz++) {
                        if (budget-- <= 0) return null;
                        if (probe.isVacantBed(originX + dx, y, originZ + dz)) {
                            return new int[] { originX + dx, y, originZ + dz };
                        }
                    }
                    // Interior x columns contribute only the positive-z edge after the negative
                    // edge. Keep the former dx->dz order without walking the discarded interior.
                    if (Math.abs(dx) != ring && ring != 0) {
                        if (budget-- <= 0) return null;
                        if (probe.isVacantBed(originX + dx, y, originZ + ring)) {
                            return new int[] { originX + dx, y, originZ + ring };
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 이미 claim 한 침대를 지금 쓸 수 있는 거리인지. HOME validRange 1 + 침대 두 칸 여유다. */
    public static boolean withinHomeUseRange(double x, double y, double z,
            int bedX, int bedY, int bedZ) {
        return Math.abs(x - (bedX + 0.5)) <= HOME_USE_RANGE_BLOCKS
                && Math.abs(y - bedY) <= HOME_USE_RANGE_BLOCKS
                && Math.abs(z - (bedZ + 0.5)) <= HOME_USE_RANGE_BLOCKS;
    }

    /** {@code SpawnUtil.trySpawnMob}가 소비하는 난수 개수(attempt 당 x,z 두 개). */
    public static int golemSpawnRandomLaneLength() {
        return GOLEM_SPAWN_ATTEMPTS * 2;
    }

    /** {@code Mth.randomBetweenInclusive(random, -8, 8)} 한 번. */
    public static int golemSpawnOffset(int nextInt17) {
        if (nextInt17 < 0 || nextInt17 >= GOLEM_SPAWN_XZ_SPREAD * 2 + 1) {
            throw new IllegalArgumentException("golem spawn offset must be in [0,17)");
        }
        return nextInt17 - GOLEM_SPAWN_XZ_SPREAD;
    }

    /**
     * {@code SpawnUtil.trySpawnMob(..., 10, 8, 6, LEGACY_IRON_GOLEM, false)} 이식.
     * 각 시도는 {@code (origin + (dx, +6, dz))}에서 시작해 아래로 13칸을 훑는다.
     *
     * @param offsets {@code golemSpawnRandomLaneLength()} 길이의 {@code nextInt(17)} 결과
     * @return 골렘 발 위치 {@code {x, y, z}} 또는 실패 시 {@code null}
     */
    public static int[] golemSpawnPosition(GolemSpawnProbe probe,
            int originX, int originY, int originZ, int[] offsets) {
        if (offsets == null || offsets.length != golemSpawnRandomLaneLength()) {
            throw new IllegalArgumentException("golem spawn lane must supply "
                    + golemSpawnRandomLaneLength() + " draws");
        }
        for (int attempt = 0; attempt < GOLEM_SPAWN_ATTEMPTS; attempt++) {
            int dx = golemSpawnOffset(offsets[attempt * 2]);
            int dz = golemSpawnOffset(offsets[attempt * 2 + 1]);
            int x = originX + dx;
            int z = originZ + dz;
            int y = moveToPossibleSpawnPosition(probe, x, originY + GOLEM_SPAWN_Y_SPREAD, z);
            if (y == Integer.MIN_VALUE) continue;
            if (!golemClearance(probe, x, y, z)) continue;
            return new int[] { x, y, z };
        }
        return null;
    }

    /** LEGACY_IRON_GOLEM 지지면 판정. 위 칸이 공기/유체고 아래가 고체(또는 가루눈)여야 한다. */
    public static boolean legacyIronGolemGround(GolemSpawnProbe probe,
            int groundBlock, int aboveBlock) {
        // [STAINED-GLASS] 색 유리·색 유리판도 무색 재질과 같은 checkSpawnObstruction 이다.
        if (groundBlock == Blocks.COBWEB || groundBlock == Blocks.CACTUS
                || Blocks.isGlassPane(groundBlock) || Blocks.isGlassBlock(groundBlock)
                || groundBlock == Blocks.ICE || groundBlock == Blocks.TNT
                // [FROST-SOUL] SpawnUtil$Strategy.LEGACY_IRON_GOLEM 도 살얼음을 명시 제외한다.
                || groundBlock == Blocks.FROSTED_ICE
                || probe.leaves(groundBlock)) {
            return false;
        }
        boolean aboveOpen = aboveBlock == Blocks.AIR || probe.liquid(aboveBlock);
        boolean groundSupports = probe.solid(groundBlock) || groundBlock == Blocks.POWDER_SNOW;
        return aboveOpen && groundSupports;
    }

    /** 골렘 발 위치의 세 칸이 비어 있어야 {@code checkSpawnObstruction}을 통과한다. */
    public static boolean golemClearance(GolemSpawnProbe probe, int x, int y, int z) {
        for (int dy = 0; dy < GOLEM_CLEARANCE_CELLS; dy++) {
            int block = probe.blockAt(x, y + dy, z);
            if (probe.solid(block)) return false;
        }
        return true;
    }

    private static int moveToPossibleSpawnPosition(GolemSpawnProbe probe, int x, int startY,
            int z) {
        int aboveBlock = probe.blockAt(x, startY, z);
        for (int step = GOLEM_SPAWN_Y_SPREAD; step >= -GOLEM_SPAWN_Y_SPREAD; step--) {
            int y = startY - (GOLEM_SPAWN_Y_SPREAD - step) - 1;
            if (y < Blocks.MIN_Y) return Integer.MIN_VALUE;
            int groundBlock = probe.blockAt(x, y, z);
            if (legacyIronGolemGround(probe, groundBlock, aboveBlock)) return y + 1;
            aboveBlock = groundBlock;
        }
        return Integer.MIN_VALUE;
    }
}
