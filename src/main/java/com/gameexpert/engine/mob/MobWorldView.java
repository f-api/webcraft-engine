package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.WorldClock;
import com.gameexpert.terrain.Blocks;

/**
 * 몹 로직이 바라보는 "외부 세계"의 포트(추상). P6 본대가 이 인터페이스에 대한
 * 어댑터(TerrainAccessor/WorldRuntime 매핑)를 구현해 연결한다.
 * 몹 로직은 이 포트 외에는 서버 WIP 클래스에 의존하지 않는다.
 */
public interface MobWorldView {
    /** Pinned End natural-spawn rules; other custom dimensions keep their own population. */
    default boolean endDimension() { return false; }
    default boolean endGatewayAvailable(int x, int y, int z) { return false; }

    /** Closest player-placed entity on the projectile segment; terrain/mobs are compared by the caller. */
    @lombok.Getter
    @lombok.experimental.Accessors(fluent = true)
    @lombok.AllArgsConstructor
    final class PlacedProjectileHit {
        private final long entityId;
        private final double t;
    }

    default PlacedProjectileHit placedProjectileHit(double ax, double ay, double az,
            double bx, double by, double bz) { return null; }
    /** ProjectileUtil's per-projectile entity box margin (ShulkerBullet tickCount-dependent value). */
    default PlacedProjectileHit placedProjectileHit(double ax, double ay, double az,
            double bx, double by, double bz, double inflation) {
        return placedProjectileHit(ax, ay, az, bx, by, bz);
    }


    /** 월드 절대 좌표의 블록 ID(CONTRACT §2). 비상주 경계는 음수 sentinel로 막을 수 있다. */
    short getBlock(int x, int y, int z);

    /** 설치 블록의 희소 state. 상태가 없는 순수 테스트 월드는 기본 0을 쓴다. */
    default int blockState(int x, int y, int z, int blockId) { return 0; }

    default void forBlockCollisionBoxes(int x, int y, int z, BuildingBlockRules.CollisionBoxVisitor visitor) {
        int id = getBlock(x,y,z) & 0xffff;
        BuildingBlockRules.forCollisionBoxes(id, BuildingBlockRules.collisionIgnoresState(id) ? 0 : blockState(x,y,z,id), x,z,visitor);
    }

    /** State-aware water volume; production also resolves immutable carrier waterlogging. */
    default boolean waterAt(int x, int y, int z) {
        int block = getBlock(x, y, z) & 0xffff;
        int state = blockState(x, y, z, block);
        return com.gameexpert.engine.Fluids.isWaterMedium(block, state)
                || com.gameexpert.terrain.Blocks.isShelf(block) && (state & 0x80) != 0;
    }

    /** 해당 블록이 충돌 대상(solid)인가. §11.1: 공기·유체 제외, 잎/유리/스포너 포함. */
    boolean isSolid(short blockId);

    /** (x,z) 칼럼에서 현재 가장 높은 점유 블록(유체 포함)의 y. */
    int surfaceHeight(int x, int z);

    /** WORLD_SURFACE semantics: fluids are included in the highest occupied column cell. */
    default int worldSurfaceHeight(int x, int z) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            if (getBlock(x, y, z) != 0) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    /** MOTION_BLOCKING_NO_LEAVES: 잎은 제외하고 이동 차단 블록과 유체는 포함한다. */
    default int motionBlockingNoLeavesHeight(int x, int z) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            short block = getBlock(x, y, z);
            if (block < 0) return Blocks.MAX_Y;
            int id = block & 0xffff;
            if (!BlockFamilies.isLeaves(id)
                    && (BuildingBlockRules.blocksMotion(id, blockState(x, y, z, id))
                            || Fluids.isWaterMedium(id) || Fluids.isLava(id))) {
                return y;
            }
        }
        return Blocks.MIN_Y - 1;
    }

    /**
     * 몹 유발 월드 변형의 write-ahead 저널(§39cd). 그물·파괴·건설은 이 저널로만 월드를 바꾼다.
     * 순수 단위 테스트 월드는 저널이 없어 어떤 역할 변형도 일어나지 않는다.
     */
    default MobMutationJournal mobMutationJournal() { return null; }

    /** 해당 청크가 실제 플레이 대상으로 활성화됐는가. 순수 단위 테스트 월드는 항상 활성으로 본다. */
    default boolean isChunkActive(int chunkX, int chunkZ) { return true; }

    /**
     * 이번 틱 이 주민이 향해야 할 직업지({@code {x,y,z}}) 또는 {@code null}.
     * 정본은 {@code VillagerJobAssignment} 이며, 몹 틱은 결정된 목표를 읽기만 한다.
     * 주민 lane 이 배선되지 않은 순수 테스트 월드는 목표가 없다.
     */
    default int[] villagerJobWalkTarget(long mobId) { return null; }

    /**
     * 이번 틱 이 주민의 활동 원장 결정(현재 활동·은신처 걷기 목표·배회 배속) 또는 {@code null}.
     * 정본은 {@code VillagerActivityLedger} 이며, 원장이 배선되지 않은 순수 테스트 월드는
     * {@code null} 이라 주민이 일정표 값으로 움직인다.
     */
    default com.gameexpert.engine.mob.villager.VillagerActivityLedger.Snapshot villagerActivity(
            long mobId) {
        return null;
    }

    /** Cached deterministic nearest Bee hive; null lets pure rule tests use their bounded scan. */
    default int[] nearestBeeHive(int x, int y, int z, int horizontalRadius, int verticalRadius) {
        return null;
    }

    /** Indexed Rafflesia position packed by RafflesiaRules, or zero when no odor source is near. */
    default long nearestRafflesia(int x, int y, int z,
            int horizontalRadius, int verticalRadius) {
        return 0L;
    }

    /** Packed indexed Firefly Bush position, or zero. Test doubles may return zero and use fallback. */
    default long nearestFireflyBush(double x, double y, double z,
            int horizontalRadius, int verticalRadius) { return Long.MIN_VALUE; }

    /**
     * Writes Rafflesia xyz then Firefly Bush xyz into the caller-owned six-int buffer.
     * Production uses the resident chunk index; pure worlds return false unless explicitly wired.
     */
    default boolean nearestPoisonFrogColony(double x, double y, double z, int[] out) {
        return false;
    }

    /**
     * [PHANTOM] 이 상자 안에 <b>살아 있는 고양이</b>가 하나라도 있는가.
     * 바닐라 {@code Phantom.PhantomSweepAttackGoal.canContinueToUse} 의
     * {@code getEntitiesOfClass(Cat.class, getBoundingBox().inflate(16.0),
     * EntitySelector.ENTITY_STILL_ALIVE)} 와 같은 자리다 — 구(球)가 아니라 <b>상자</b>이고
     * 시야 차폐를 보지 않는다(docs/research/mc-phantom-insomnia.md §2).
     *
     * <p>몹은 서로를 직접 보지 못하므로 권위 어댑터가 이번 틱의 고양이 좌표를 실어 준다.
     * 고양이 lane 이 배선되지 않은 순수 테스트 월드는 언제나 false 를 본다.
     */
    default boolean catWithinBox(double minX, double minY, double minZ,
                                 double maxX, double maxY, double maxZ) {
        return false;
    }

    /**
     * [CAT] 지정한 회피 상자 안에서 기준점에 가장 가까운 살아 있는 고양이 발좌표.
     * 크리퍼 {@code AvoidEntityGoal<Cat>} 이 경로의 반대 방향을 정할 때 쓴다.
     */
    default double[] nearestCat(double x, double y, double z,
                                double horizontalRange, double verticalRange) {
        return null;
    }

    /** BiomeManager fuzzy zoom을 한 번 적용한 블록 좌표의 Overworld 바이옴 ID. */
    default int biomeAt(int x, int y, int z) {
        return MobSpawner.fuzzyBiomeAt(this, x, y, z);
    }

    /** Fuzzy zoom이 선택한 quart에서 읽는 원시 기후/noise 바이옴. */
    default int noiseBiomeAtQuart(int quartX, int quartY, int quartZ) { return 1; }

    /** 현재 월드 시간(0~11999, §3). 6500~11499 가 밤. */
    long worldTime();

    /**
     * [SULFUR] 시간 스케일과 무관한 <b>절대 월드 틱</b>. 유황 간헐천의 분출 위상
     * ({@code SulfurGeyserRules.erupting})이 읽는 유일한 시계이며, 황린 잠복자의 간헐천 연동
     * 등장이 <b>플레이어를 밀어올리는 것과 같은 틱</b>을 보려면 이 값이 환경 틱의
     * {@code tickNo} 와 같은 원천이어야 한다. 순수 단위 테스트 월드는 0 에서 멈춘 시계를 본다.
     */
    default long worldTick() { return 0; }

    /** Absolute vanilla 20-TPS game time; independent from daylight scale and sleep. */
    default long gameTimeMcTicks() { return Math.multiplyExact(worldTick(), 2L); }

    /**
     * 월드 난이도. 순수 단위 테스트 월드는 바닐라 기본값과 같은 normal 을 본다.
     * 지역 난이도(local difficulty)는 도입하지 않는다.
     */
    default Difficulty difficulty() { return Difficulty.DEFAULT; }

    /** 월드 생성 뒤 지난 절대 일수. 달 위상과 장기 주기의 정본입니다. */
    default long dayCount() { return 0; }

    /** 슬라임 청크 등 좌표 결정적 규칙에 쓰는 월드 시드. */
    default int worldSeed() { return 0; }

    /** BiomeManager block zoom에 쓰는 SHA-256 난독화 시드. 운영 어댑터는 월드당 한 번 캐시한다. */
    default long biomeZoomSeed() { return MobSpawner.biomeZoomSeed(worldSeed()); }

    /** Shared deterministic world spawn used by player entry and natural-spawn exclusion. */
    default int[] worldSpawn() { return new int[] {0, surfaceHeight(0, 0) + 1, 0}; }

    /** 날씨 시스템이 연결된 월드는 해당 좌표에 비가 직접 닿을 때 true. */
    default boolean isRainingAt(int x, int y, int z) { return false; }

    /**
     * 순수 테스트용 결정론적 기본값. 운영 어댑터는 상주 블록의 하늘광·블록광 전파를 재정의한다.
     * 기본값은 낮에 지표 위로 노출된 칸을 15, 밤/지하를 0으로 본다.
     */
    default int lightLevel(int x, int y, int z) {
        return localBrightness(x, y, z);
    }

    /** Raw propagated sky light before the time-of-day darkening is applied. */
    default int rawSkyLight(int x, int y, int z) {
        return y > worldSurfaceHeight(x, z) ? 15 : 0;
    }

    /** Raw propagated block-emission light. */
    default int blockLight(int x, int y, int z) { return 0; }

    /** Local raw brightness after the world's sky-darkening value. */
    default int localBrightness(int x, int y, int z) {
        int skyDarken = WorldClock.isNight(worldTime()) ? 11 : 0;
        return Math.max(blockLight(x, y, z), Math.max(0, rawSkyLight(x, y, z) - skyDarken));
    }

    /**
     * 햇빛 연소용 sky light 근사. 열린 칼럼은 15이고, 공기 경로를 따라 한 칸마다 1씩
     * 감쇠한다. 연소 임계값이 12라서 최대 세 칸만 탐색하면 충분하다.
     */
    default boolean sunlightAbove(int x, int y, int z, int threshold) {
        return sunlightLevel(x, y, z) > threshold;
    }

    default int sunlightLevel(int x, int y, int z) {
        if (WorldClock.isNight(worldTime()) || isSolid(getBlock(x, y, z))) return 0;
        if (openToSky(x, y, z)) return 15;

        int[] qx = new int[63], qy = new int[63], qz = new int[63], qd = new int[63];
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int head = 0, tail = 0;
        qx[tail] = x; qy[tail] = y; qz[tail] = z; qd[tail++] = 0;
        while (head < tail) {
            int cx = qx[head], cy = qy[head], cz = qz[head], distance = qd[head++];
            if (distance == 3) continue;
            for (int[] direction : directions) {
                int nx = cx + direction[0], ny = cy + direction[1], nz = cz + direction[2];
                int nextDistance = distance + 1;
                if (ny < Blocks.MIN_Y || ny > Blocks.MAX_Y || isSolid(getBlock(nx, ny, nz))) continue;
                if (alreadyQueued(qx, qy, qz, tail, nx, ny, nz)) continue;
                if (openToSky(nx, ny, nz)) return 15 - nextDistance;
                qx[tail] = nx; qy[tail] = ny; qz[tail] = nz; qd[tail++] = nextDistance;
            }
        }
        return 0;
    }

    /**
     * 두 눈 좌표 사이를 복셀 DDA로 훑어 고체 블록이 시야를 막는지 확인한다.
     * 거리 필터를 통과한 타겟에만 호출되며, 시작/끝 복셀은 관찰자와 대상의 몸이므로 제외한다.
     */
    default boolean hasLineOfSight(double ax, double ay, double az,
                                   double bx, double by, double bz) {
        int vx = floor(ax), vy = floor(ay), vz = floor(az);
        int endX = floor(bx), endY = floor(by), endZ = floor(bz);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int stepX = sign(dx), stepY = sign(dy), stepZ = sign(dz);
        double tMaxX = boundaryT(ax, dx, stepX);
        double tMaxY = boundaryT(ay, dy, stepY);
        double tMaxZ = boundaryT(az, dz, stepZ);
        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        while (vx != endX || vy != endY || vz != endZ) {
            // Do not cross an endpoint grid plane on an axis whose destination cell was reached.
            // A t=1 tie would otherwise overshoot it and prevent traversal from terminating.
            if (vx == endX) tMaxX = Double.POSITIVE_INFINITY;
            if (vy == endY) tMaxY = Double.POSITIVE_INFINITY;
            if (vz == endZ) tMaxZ = Double.POSITIVE_INFINITY;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                vx += stepX; tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                vy += stepY; tMaxY += tDeltaY;
            } else {
                vz += stepZ; tMaxZ += tDeltaZ;
            }
            if ((vx != endX || vy != endY || vz != endZ) && isSolid(getBlock(vx, vy, vz))) {
                return false;
            }
        }
        return true;
    }

    /** 현재 블록 상태를 직접 읽어 (x,y,z)에서 세계 상단까지 하늘이 열려 있는지 판정한다. */
    default boolean openToSky(int x, int y, int z) {
        for (int scanY = y + 1; scanY <= Blocks.MAX_Y; scanY++) {
            if (isSolid(getBlock(x, scanY, z))) return false;
        }
        return true;
    }

    private static boolean alreadyQueued(int[] xs, int[] ys, int[] zs, int size,
                                         int x, int y, int z) {
        for (int i = 0; i < size; i++) {
            if (xs[i] == x && ys[i] == y && zs[i] == z) return true;
        }
        return false;
    }

    /** 현재 접속 플레이어 스냅샷 목록. */
    List<PlayerSnapshot> players();

    private static int floor(double v) { return (int) Math.floor(v); }
    private static int sign(double v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); }
    private static double boundaryT(double origin, double delta, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double next = step > 0 ? Math.floor(origin) + 1.0 : Math.floor(origin);
        return (next - origin) / delta;
    }
}
