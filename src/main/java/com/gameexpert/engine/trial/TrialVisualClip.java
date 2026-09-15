package com.gameexpert.engine.trial;

import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.mob.MobWorldView;
import com.gameexpert.terrain.Blocks;

/**
 * [TRIAL-GAP] 바닐라 {@code Level.clip(new ClipContext(from, to, Block.VISUAL, Fluid.NONE,
 * CollisionContext.empty()))} 의 이 저장소 판. 트라이얼 스포너의 플레이어 감지·몹 소환 시선
 * ({@code PlayerDetector.inLineOfSight} · {@code TrialSpawner.inLineOfSight})과 불길한 아이템
 * 소환기 위치({@code TrialSpawnerState.calculatePositionAbove})가 쓴다.
 *
 * <p>순회는 {@code BlockGetter.traverseBlocks} 그대로다: 두 끝점을 서로 반대쪽으로 {@code 1e-7}
 * 밀어 낸 뒤 시작 칸을 먼저 보고, {@code tMax} 가 가장 작은 축으로 한 칸씩 나아가며(동률은 z → y
 * 순으로 미룬다) 칸마다 그 블록의 VISUAL 모양이 원래 선분과 만나는지 본다. VISUAL 모양은
 * {@code BlockBehaviour.getVisualShape} 기본값(= 충돌 모양)에 핀 26.3 의 재정의만 얹는다:
 * {@code TransparentBlock}(유리·색 유리·색유리 없는 착색 유리) · {@code IronBarsBlock}(유리판·
 * 철창·구리 창살) · {@code PowderSnowBlock} 은 빈 모양, {@code MudBlock} 은 온전한 블록,
 * {@code FenceBlock} 은 1.5 높이 충돌 대신 높이 1 의 윤곽, {@code SnowLayerBlock} 은 층 수 × 2/16.
 * 모양과 선분의 교차는 {@code AABB.clip} 처럼 선분이 상자 면으로 <b>들어가는</b> 경우만 치고,
 * {@code VoxelShape.clip} 처럼 시작점(끝점 쪽 0.001 지점)이 모양 안이면 곧장 그 칸이다.
 * 정적판 사본은 {@code StandaloneTrialVisualClip.ts} 다.</p>
 */
public final class TrialVisualClip {

    private TrialVisualClip() {}

    /** 선분이 처음 부딪친 칸 {x,y,z}. 아무것도 없으면 null(바닐라 MISS). */
    public static int[] clip(MobWorldView world, double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ) {
        if (fromX == toX && fromY == toY && fromZ == toZ) return null;
        double endX = lerp(-1.0E-7, toX, fromX);
        double endY = lerp(-1.0E-7, toY, fromY);
        double endZ = lerp(-1.0E-7, toZ, fromZ);
        double startX = lerp(-1.0E-7, fromX, toX);
        double startY = lerp(-1.0E-7, fromY, toY);
        double startZ = lerp(-1.0E-7, fromZ, toZ);
        int x = (int) Math.floor(startX);
        int y = (int) Math.floor(startY);
        int z = (int) Math.floor(startZ);
        if (hitsCell(world, x, y, z, fromX, fromY, fromZ, toX, toY, toZ)) return new int[] {x, y, z};
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        int stepX = sign(dx);
        int stepY = sign(dy);
        int stepZ = sign(dz);
        double deltaX = stepX == 0 ? Double.MAX_VALUE : stepX / dx;
        double deltaY = stepY == 0 ? Double.MAX_VALUE : stepY / dy;
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : stepZ / dz;
        double tMaxX = deltaX * (stepX > 0 ? 1.0 - frac(startX) : frac(startX));
        double tMaxY = deltaY * (stepY > 0 ? 1.0 - frac(startY) : frac(startY));
        double tMaxZ = deltaZ * (stepZ > 0 ? 1.0 - frac(startZ) : frac(startZ));
        while (tMaxX <= 1.0 || tMaxY <= 1.0 || tMaxZ <= 1.0) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += deltaX;
                } else {
                    z += stepZ;
                    tMaxZ += deltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += deltaY;
            } else {
                z += stepZ;
                tMaxZ += deltaZ;
            }
            if (hitsCell(world, x, y, z, fromX, fromY, fromZ, toX, toY, toZ)) {
                return new int[] {x, y, z};
            }
        }
        return null;
    }

    /**
     * 바닐라 {@code inLineOfSight}: {@code from} 에서 {@code to} 로 쏜 선분이 아무것도 치지 않거나,
     * 처음 친 칸이 {@code to} 를 담은 칸이면 true 다.
     */
    public static boolean inLineOfSight(MobWorldView world, double fromX, double fromY,
            double fromZ, double toX, double toY, double toZ) {
        int[] hit = clip(world, fromX, fromY, fromZ, toX, toY, toZ);
        return hit == null || hit[0] == (int) Math.floor(toX) && hit[1] == (int) Math.floor(toY)
                && hit[2] == (int) Math.floor(toZ);
    }

    /** 이 칸의 충돌 모양이 비어 있지 않은가({@code getCollisionShape(...).isEmpty()} 의 부정). */
    public static boolean hasCollision(MobWorldView world, int x, int y, int z) {
        short block = world.getBlock(x, y, z);
        if (block < 0) return true;
        int id = block & 0xffff;
        if (id == Blocks.AIR) return false;
        boolean[] any = {false};
        BuildingBlockRules.forCollisionBoxes(id, world.blockState(x, y, z, id), x, z,
                (x0, y0, z0, x1, y1, z1) -> any[0] = true);
        return any[0];
    }

    private static boolean hitsCell(MobWorldView world, int x, int y, int z,
            double ax, double ay, double az, double bx, double by, double bz) {
        short block = world.getBlock(x, y, z);
        // 비상주 경계는 막힌 칸으로 본다(투사체 DDA 와 같은 보수적 판정).
        if (block < 0) return true;
        int id = block & 0xffff;
        if (id == Blocks.AIR) return false;
        int state = world.blockState(x, y, z, id);
        double[][] boxes = visualBoxes(id, state, x, z);
        if (boxes.length == 0) return false;
        double sx = ax + (bx - ax) * 0.001;
        double sy = ay + (by - ay) * 0.001;
        double sz = az + (bz - az) * 0.001;
        for (double[] box : boxes) {
            if (sx >= x + box[0] && sx < x + box[3] && sy >= y + box[1] && sy < y + box[4]
                    && sz >= z + box[2] && sz < z + box[5]) return true;
        }
        for (double[] box : boxes) {
            if (entersBox(ax, ay, az, bx, by, bz, x + box[0], y + box[1], z + box[2],
                    x + box[3], y + box[4], z + box[5])) return true;
        }
        return false;
    }

    /** 이 블록 상태의 VISUAL 모양(로컬 좌표 상자들). 빈 배열은 빈 모양이다. */
    static double[][] visualBoxes(int id, int state, int blockX, int blockZ) {
        if (Blocks.isGlassBlock(id) || id == Blocks.TINTED_GLASS
                || BuildingBlockRules.isPane(id) || id == Blocks.POWDER_SNOW) {
            return new double[0][];
        }
        if (id == Blocks.MUD) return new double[][] {{0, 0, 0, 1, 1, 1}};
        if (id == Blocks.SNOW) {
            int layers = Math.max(1, Math.min(8, state));
            return new double[][] {{0, 0, 0, 1, layers * 2 / 16.0, 1}};
        }
        java.util.ArrayList<double[]> boxes = new java.util.ArrayList<>(4);
        BuildingBlockRules.forCollisionBoxes(id, state, blockX, blockZ,
                (x0, y0, z0, x1, y1, z1) -> boxes.add(new double[] {
                        x0, y0, z0, x1, Blocks.isFence(id) ? Math.min(1.0, y1) : y1, z1}));
        return boxes.toArray(new double[0][]);
    }

    /** 바닐라 {@code AABB.clip}: 선분이 상자의 면으로 들어가는 점이 있는가(오차 1e-7). */
    private static boolean entersBox(double ax, double ay, double az, double bx, double by,
            double bz, double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double[] best = {1.0};
        boolean hit = false;
        if (dx > 1.0E-7) hit |= clipPoint(best, dx, dy, dz, minX, minY, maxY, minZ, maxZ, ax, ay, az);
        else if (dx < -1.0E-7) hit |= clipPoint(best, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, ax, ay, az);
        if (dy > 1.0E-7) hit |= clipPoint(best, dy, dz, dx, minY, minZ, maxZ, minX, maxX, ay, az, ax);
        else if (dy < -1.0E-7) hit |= clipPoint(best, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, ay, az, ax);
        if (dz > 1.0E-7) hit |= clipPoint(best, dz, dx, dy, minZ, minX, maxX, minY, maxY, az, ax, ay);
        else if (dz < -1.0E-7) hit |= clipPoint(best, dz, dx, dy, maxZ, minX, maxX, minY, maxY, az, ax, ay);
        return hit;
    }

    /** 바닐라 {@code AABB.clipPoint}. */
    private static boolean clipPoint(double[] best, double da, double db, double dc, double plane,
            double minB, double maxB, double minC, double maxC, double oa, double ob, double oc) {
        double t = (plane - oa) / da;
        double b = ob + t * db;
        double c = oc + t * dc;
        if (0.0 < t && t < best[0] && minB - 1.0E-7 < b && b < maxB + 1.0E-7
                && minC - 1.0E-7 < c && c < maxC + 1.0E-7) {
            best[0] = t;
            return true;
        }
        return false;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    private static int sign(double value) {
        return value == 0.0 ? 0 : value > 0.0 ? 1 : -1;
    }
}
