package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.CoralReefPlacement;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureAabb;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * [CORAL-REEF] 온수 바다 산호초 사이트.
 *
 * <p>배치 희귀도·규모 분포·밀도 리터럴은 {@link CoralReefPlacement} 가 소유하고, 이 파일은
 * <b>무엇을 어떤 순서로 놓는지</b>만 소유한다.
 *
 * <h2>두 단계로 갈라 둔 이유</h2>
 * 계획은 <b>레이아웃</b>과 <b>배출</b> 두 단계다.
 * <ul>
 *   <li>{@link #layout(StructureSiteDescriptor)} 는 <b>지형을 한 칸도 읽지 않는</b> 순수
 *       정수 산술이다. 어느 국소 좌표에 어떤 feature 가 어떤 매개변수로 서는지를 순서까지
 *       정해 돌려준다. 파리티 게이트가 비교하는 것이 바로 이 목록이라 golden terrain 해시와
 *       완전히 독립인 <b>placement-only 증명</b>이 된다.</li>
 *   <li>{@link #plan} 은 그 목록을 훑으며 열마다 해저면 높이를 한 번 물어 복셀로 편다.
 *       지형을 읽는 것은 이 단계뿐이다.</li>
 * </ul>
 *
 * <p><b>물을 파괴하지 않는다.</b> 배출은 <b>물칸과 빈 칸에만</b> 쓴다 — 해저면을
 * 파헤치지 않고, 물기둥을 끊지도 않는다.
 * 이 저장소의 수중 장식은 waterlogged state 없이 물칸 하나의 의미를 대신하는 단일-ID 모델이라
 * (docs/MC-REFERENCE.md §12) 켈프·해초·불우렁쉥이 한 칸이 그대로 물 매질로 남는다.
 *
 * <p><b>죽은 산호는 상태가 아니라 물이다.</b> [A] 바닐라에서 물에 닿지 않은 산호는 곧바로
 * 죽은 변형이 된다. 그래서 이 생성기는 죽은 변형을 따로 굴리지 않는다 — 배출 시점에 그 칸이
 * 물이면 살아있는 ID, 공기면 {@code + Blocks.CORAL_DEAD_OFFSET} 다. 수면 위로 솟은 얕은
 * 산호초의 꼭대기만 자연히 표백된다.
 *
 * <p><b>회전을 쓰지 않는다.</b> 산호초에는 정면이 없다. {@code site.direction()} 으로 국소
 * 좌표를 돌리면 부착 장식의 방위까지 함께 돌려야 하고, 그 한 줄이 두 손 사본이 갈릴 가장 좋은
 * 자리다(얼룩덜룩한 숲과 같은 계약이며 divergence 가 아니다).
 *
 * <p><b>먼저 놓은 것이 이긴다.</b> {@link Planner#put} 은 이미 계획된 좌표를 덮지 않는다.
 * 그래서 순서가 계약이다: 콜로니 → 바다 피클 군집 → 켈프·해초. 식생을 먼저 돌리면 해초가
 * 산호 밑동 자리를 차지해 feature 수와 지문이 갈린다.
 *
 * <p>Rust 사본은 {@code client/wasm/src/mc_structure/coral_reef.rs} 다.
 */
public final class CoralReefGenerator implements StructureOverlayGenerator {

    // ── feature 종류. 파리티 게이트가 이 정수를 그대로 싣는다 ──────
    /** 켈프 한 줄기. {@code param} 은 줄기 높이(1..10)다. */
    public static final int FEATURE_KELP = 0;
    /** 해초 한 칸. {@code param} 이 1 이면 두 칸짜리 큰 해초다. */
    public static final int FEATURE_SEAGRASS = 1;
    /** 바다 피클 한 칸. */
    public static final int FEATURE_SEA_PICKLE = 2;
    /** 산호 나무([A] {@code coral_tree}). */
    public static final int FEATURE_CORAL_TREE = 3;
    /** 산호 발톱([A] {@code coral_claw}). */
    public static final int FEATURE_CORAL_CLAW = 4;
    /** 산호 버섯([A] {@code coral_mushroom}). */
    public static final int FEATURE_CORAL_MUSHROOM = 5;

    private static final int COLONY_SALT = 0x00c0_1a02;
    private static final int PICKLE_SALT = 0x00c0_1a03;
    private static final int VEGETATION_SALT = 0x00c0_1a04;

    /** 부착 장식·콜로니 가지가 쓰는 4 방위. 순서는 계약이다(0=−Z, 1=+X, 2=+Z, 3=−X). */
    private static final int[][] SIDES_4 = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * 사이트가 피하는 <b>해저</b> 구조물 kind. 지상·지하 kind 는 산호초와 자리를 다투지
     * 않으므로 판정 비용만 늘고 결과가 바뀌지 않는다. 묻힌 보물은 해변 모래 <b>속</b> 한
     * 칸이라 여기 들지 않는다.
     *
     * <p>이 목록의 순서는 계약이다 — Rust 사본이 같은 순서로 같은 상자를 쌓아야 한다.
     */
    private static final List<StructureSiteDescriptor.Kind> UNDERWATER_CONFLICT_KINDS = List.of(
            StructureSiteDescriptor.Kind.FLOODED_RUIN,
            StructureSiteDescriptor.Kind.UNDERWATER_RUIN);

    /** 기존 구조물 점유 상자를 넓히는 여유(블록). */
    private static final int CLEARANCE_MARGIN = 2;

    /** 이 아래로 떨어지면 사이트를 통째로 버린다(민무늬 해저에 남는 부스러기 방지). */
    private static final int MINIMUM_VOXELS = 24;

    /**
     * 한 사이트의 feature 하나. 좌표는 <b>site anchor 기준 국소</b>이고 y 가 없다 — y 는 배출
     * 단계가 해저면에서 얻는다. {@code param} 의 비트 배치는 {@link #colonyParam} 이 소유한다.
     */
    public record Feature(int localX, int localZ, int type, int param) {}

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        if (!CoralReefPlacement.isCoralReef(site.kind())) return List.of();
        if (!warmOcean(site, world)) return List.of();
        List<Feature> features = layout(site);
        if (features.isEmpty()) return List.of();
        Planner planner = new Planner(site, world);
        for (Feature feature : features) planner.emit(feature);
        return planner.voxels();
    }

    @Override
    public StructureAabb possibleHorizontalBounds(StructureSiteDescriptor site) {
        int reach = site.kind().maxReach();
        return new StructureAabb(site.anchorX() - reach, Blocks.MIN_Y, site.anchorZ() - reach,
                site.anchorX() + reach, Blocks.MAX_Y - 1, site.anchorZ() + reach);
    }

    /**
     * 바닐라 바이옴 관문. 산호초는 온수 바다 전용이고, 온수 술어는 해저 유적 트랙의 것을
     * 그대로 재사용한다({@link CoralReefPlacement#isWarmOcean}).
     *
     * <p>바이옴을 모르는 경량 fixture view 는 <b>fail-close</b> 다 — 이 사이트의 계약 자체가
     * "온수 바다에만" 이고, 알 수 없는 뷰가 산호를 찍어 내면 그 계약이 사라진다(묻힌 보물이
     * fail-close 한 이유와 같다).
     */
    private boolean warmOcean(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
        int biome = world.noiseBiomeAt(site.anchorX(), Blocks.SEA_LEVEL, site.anchorZ());
        if (biome == SurfaceDecorator.UNKNOWN_NOISE_BIOME) return false;
        return CoralReefPlacement.isWarmOcean(biome);
    }

    // ── 레이아웃(지형을 읽지 않는다) ──────────────────────────────

    /**
     * 이 site 의 feature 목록. <b>지형을 한 칸도 읽지 않는</b> 순수 정수 산술이고, 순서까지
     * 계약이다. Rust 사본 {@code coral_reef.rs::layout} 이 같은 목록을 낸다.
     */
    public static List<Feature> layout(StructureSiteDescriptor site) {
        if (!CoralReefPlacement.isCoralReef(site.kind())) return List.of();
        int radius = CoralReefPlacement.radius(site);
        List<int[]> blocked = clearanceBoxes(site, radius);
        List<Feature> features = new ArrayList<>();
        // 한 열에는 feature 하나뿐이다. 콜로니가 먼저 열을 잡고, 바다 피클이 그 다음,
        // 켈프·해초가 남은 열만 채운다 — 배출의 first-wins 와 같은 우선순위를 레이아웃에서
        // 미리 확정해 두면 두 권위가 비교할 목록 자체가 한 벌로 정해진다.
        Set<Long> occupied = new HashSet<>();
        colonies(site, radius, blocked, occupied, features);
        seaPickles(site, radius, blocked, occupied, features);
        vegetation(site, radius, blocked, occupied, features);
        return features.size() >= CoralReefPlacement.minimumFeatures(site)
                ? List.copyOf(features) : List.of();
    }

    /**
     * 이미 다른 해저 구조물이 잡은 자리. {@link #UNDERWATER_CONFLICT_KINDS} 의 후보 사이트
     * 해시만 읽으므로 지형과 무관하고, 두 권위가 같은 상자를 얻는다.
     *
     * <p>상자는 {@code [minX, minZ, maxX, maxZ]} 월드 좌표(포함)다.
     */
    private static List<int[]> clearanceBoxes(StructureSiteDescriptor site, int radius) {
        List<int[]> boxes = new ArrayList<>();
        for (StructureSiteDescriptor.Kind other : UNDERWATER_CONFLICT_KINDS) {
            int cellSize = other.cellSize();
            int reach = other.maxReach() + CLEARANCE_MARGIN;
            int span = reach + radius;
            int minCellX = Math.floorDiv(site.anchorX() - span, cellSize);
            int maxCellX = Math.floorDiv(site.anchorX() + span, cellSize);
            int minCellZ = Math.floorDiv(site.anchorZ() - span, cellSize);
            int maxCellZ = Math.floorDiv(site.anchorZ() + span, cellSize);
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    StructureSiteDescriptor occupant =
                            StructureSiteDescriptor.atCell(site.seed(), other, cellX, cellZ);
                    if (!occupant.provisionallyExists()) continue;
                    boxes.add(new int[] {
                            occupant.anchorX() - reach, occupant.anchorZ() - reach,
                            occupant.anchorX() + reach, occupant.anchorZ() + reach});
                }
            }
        }
        return boxes;
    }

    private static boolean blocked(StructureSiteDescriptor site, List<int[]> boxes,
            int localX, int localZ) {
        int worldX = site.anchorX() + localX;
        int worldZ = site.anchorZ() + localZ;
        for (int[] box : boxes) {
            if (worldX >= box[0] && worldX <= box[2] && worldZ >= box[1] && worldZ <= box[3]) {
                return true;
            }
        }
        return false;
    }

    private static long column(int localX, int localZ) {
        return (long) localX << 32 | localZ & 0xffff_ffffL;
    }

    /**
     * 산호 콜로니. 하위 격자 칸마다 후보 하나를 지터로 흔들어 뽑고, 중심에서 멀수록 성기게
     * 채택한다. 세 형태는 [A] {@code warm_ocean_vegetation.json} 의
     * {@code simple_random_selector} 대로 가중치 없이 1:1:1 이다.
     */
    private static void colonies(StructureSiteDescriptor site, int radius, List<int[]> boxes,
            Set<Long> occupied, List<Feature> out) {
        int grid = CoralReefPlacement.COLONY_GRID;
        int half = radius / grid;
        long radiusSq = (long) radius * radius;
        int core = CoralReefPlacement.COLONY_CORE_DENSITY;
        int edge = CoralReefPlacement.COLONY_EDGE_DENSITY;
        for (int cellZ = -half; cellZ <= half; cellZ++) {
            for (int cellX = -half; cellX <= half; cellX++) {
                int lane = site.voxelLane(site.anchorX() + cellX, 2,
                        site.anchorZ() + cellZ, COLONY_SALT);
                int x = cellX * grid + ((((lane >>> 8) & 255) * grid) >>> 8);
                int z = cellZ * grid + ((((lane >>> 16) & 255) * grid) >>> 8);
                long distanceSq = (long) x * x + (long) z * z;
                if (distanceSq > radiusSq) continue;
                if (blocked(site, boxes, x, z)) continue;
                int density = edge + (int) ((core - edge) * (radiusSq - distanceSq) / radiusSq);
                if ((lane & 255) >= density) continue;
                int shape = Integer.remainderUnsigned(lane >>> 24,
                        CoralReefPlacement.COLONY_SHAPE_COUNT);
                if (!occupied.add(column(x, z))) continue;
                out.add(new Feature(x, z, FEATURE_CORAL_TREE + shape, colonyParam(lane)));
            }
        }
    }

    /**
     * 콜로니의 매개변수 비트.
     *
     * <pre>
     * 0..2   산호 색(0..4, [A] 레지스트리 순서 tube · brain · bubble · fire · horn)
     * 3..5   줄기 높이(2..4)
     * 6..8   가지 수 / 발톱 길이 / 갓 반경 + 1 (2..4)
     * 9..12  부착 장식 마스크(0..2 = 옆면 부채 셋, 3 = 꼭대기 산호 식물)
     * 13..14 시작 방위
     * </pre>
     */
    private static int colonyParam(int lane) {
        int species = Integer.remainderUnsigned(lane >>> 4, CoralReefPlacement.SPECIES_COUNT);
        int stem = 2 + Integer.remainderUnsigned(lane >>> 7, 3);
        int arms = 2 + Integer.remainderUnsigned(lane >>> 11, 3);
        int decor = (lane >>> 14) & 15;
        int turn = (lane >>> 19) & 3;
        return species | stem << 3 | arms << 6 | decor << 9 | turn << 13;
    }

    /**
     * 바다 피클 군집. [A] {@code placed_feature/sea_pickle.json} 의 {@code rarity_filter}
     * 1/16 로 사이트 하나가 군집을 갖고, 갖는다면 {@code count: 20} 번 흩뿌린다. 이 저장소는
     * 피클을 한 칸 ID 로만 표현하므로 "colony 1~4개" 상태 대신 열 개수로 흩어진다.
     */
    private static void seaPickles(StructureSiteDescriptor site, int radius, List<int[]> boxes,
            Set<Long> occupied, List<Feature> out) {
        int gate = site.partLane0(0, PICKLE_SALT);
        if (Integer.remainderUnsigned(gate, CoralReefPlacement.SEA_PICKLE_RARITY) != 0) return;
        long radiusSq = (long) radius * radius;
        int inner = Math.max(1, radius - CoralReefPlacement.SEA_PICKLE_CLUSTER_SPREAD);
        int centerX = Integer.remainderUnsigned(gate >>> 8, 2 * inner + 1) - inner;
        int centerZ = Integer.remainderUnsigned(gate >>> 20, 2 * inner + 1) - inner;
        int spread = CoralReefPlacement.SEA_PICKLE_CLUSTER_SPREAD;
        for (int attempt = 0; attempt < CoralReefPlacement.SEA_PICKLE_CLUSTER_ATTEMPTS;
                attempt++) {
            int lane = site.partLane0(attempt + 1, PICKLE_SALT);
            int x = centerX + Integer.remainderUnsigned(lane, 2 * spread + 1) - spread;
            int z = centerZ + Integer.remainderUnsigned(lane >>> 8, 2 * spread + 1) - spread;
            if ((long) x * x + (long) z * z > radiusSq) continue;
            if (blocked(site, boxes, x, z)) continue;
            if (!occupied.add(column(x, z))) continue;
            out.add(new Feature(x, z, FEATURE_SEA_PICKLE, 0));
        }
    }

    /**
     * 켈프·해초 밀도 보충. [A] {@code kelp_warm.json}(청크당 80) ·
     * {@code seagrass_warm.json}(청크당 80, 그중 30 % 가 두 칸짜리 큰 해초)을 열 확률로 옮긴
     * 값이며, <b>이 사이트 반경 안에서만</b> 굴린다 — {@code SurfaceDecorator} 의 기존 수중
     * 흩뿌림(칼럼 해시 mod 37/17/11/19)은 한 줄도 건드리지 않는다.
     */
    private static void vegetation(StructureSiteDescriptor site, int radius, List<int[]> boxes,
            Set<Long> occupied, List<Feature> out) {
        long radiusSq = (long) radius * radius;
        int kelp = CoralReefPlacement.KELP_DENSITY;
        int seagrass = CoralReefPlacement.SEAGRASS_DENSITY;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                long distanceSq = (long) x * x + (long) z * z;
                if (distanceSq > radiusSq) continue;
                if (blocked(site, boxes, x, z)) continue;
                if (occupied.contains(column(x, z))) continue;
                int lane = site.voxelLane(site.anchorX() + x, 0, site.anchorZ() + z,
                        VEGETATION_SALT);
                int roll = lane & 1023;
                if (roll < kelp) {
                    int height = 1 + Integer.remainderUnsigned(lane >>> 10,
                            CoralReefPlacement.KELP_MAX_HEIGHT);
                    out.add(new Feature(x, z, FEATURE_KELP, height));
                } else if (roll < kelp + seagrass) {
                    int tall = Integer.remainderUnsigned(lane >>> 20, 100)
                            < CoralReefPlacement.SEAGRASS_TALL_PERCENT ? 1 : 0;
                    out.add(new Feature(x, z, FEATURE_SEAGRASS, tall));
                }
            }
        }
    }

    // ── 배출(지형을 읽는 유일한 단계) ─────────────────────────────

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int reach;
        private final int width;
        private final int[] floor;
        private final Map<BlockPos, RuinGenerator.Voxel> plan = new LinkedHashMap<>();

        Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
            this.site = site;
            this.world = world;
            this.reach = site.kind().maxReach();
            this.width = reach * 2 + 1;
            this.floor = new int[width * width];
            java.util.Arrays.fill(floor, Integer.MIN_VALUE);
        }

        void emit(Feature feature) {
            switch (feature.type()) {
                case FEATURE_KELP -> kelp(feature);
                case FEATURE_SEAGRASS -> seagrass(feature);
                case FEATURE_SEA_PICKLE -> seaPickle(feature);
                case FEATURE_CORAL_CLAW -> claw(feature);
                case FEATURE_CORAL_MUSHROOM -> mushroom(feature);
                default -> tree(feature);
            }
        }

        private void kelp(Feature feature) {
            int floorY = floorY(feature.localX(), feature.localZ());
            if (floorY < Blocks.MIN_Y) return;
            for (int step = 1; step <= feature.param(); step++) {
                if (!putSubmerged(feature.localX(), floorY + step, feature.localZ(),
                        Blocks.KELP)) return;
            }
        }

        private void seagrass(Feature feature) {
            int floorY = floorY(feature.localX(), feature.localZ());
            if (floorY < Blocks.MIN_Y) return;
            if (!putSubmerged(feature.localX(), floorY + 1, feature.localZ(), Blocks.SEAGRASS)) {
                return;
            }
            if (feature.param() != 0) {
                putSubmerged(feature.localX(), floorY + 2, feature.localZ(), Blocks.SEAGRASS);
            }
        }

        private void seaPickle(Feature feature) {
            int floorY = floorY(feature.localX(), feature.localZ());
            if (floorY < Blocks.MIN_Y) return;
            putSubmerged(feature.localX(), floorY + 1, feature.localZ(), Blocks.SEA_PICKLE);
        }

        /**
         * [A] {@code coral_tree}: 곧은 줄기 하나에서 가지가 갈라져 오르며 뻗는다. 가지 수는
         * {@code arms}(2..4)이고 각 가지가 두 칸 나아가며 한 칸 오른다([C] 절차 사본).
         */
        private void tree(Feature feature) {
            int x = feature.localX();
            int z = feature.localZ();
            int floorY = floorY(x, z);
            if (floorY < Blocks.MIN_Y) return;
            int param = feature.param();
            int species = param & 7;
            int stem = (param >>> 3) & 7;
            int arms = (param >>> 6) & 7;
            int turn = (param >>> 13) & 3;
            for (int step = 1; step <= stem; step++) {
                coral(x, floorY + step, z, Blocks.TUBE_CORAL_BLOCK, species);
            }
            int top = floorY + stem;
            for (int arm = 0; arm < arms; arm++) {
                int[] side = SIDES_4[(turn + arm) & 3];
                int branchX = x;
                int branchZ = z;
                int branchY = top;
                for (int step = 0; step < 2; step++) {
                    branchX += side[0];
                    branchZ += side[1];
                    coral(branchX, branchY, branchZ, Blocks.TUBE_CORAL_BLOCK, species);
                    branchY++;
                    coral(branchX, branchY, branchZ, Blocks.TUBE_CORAL_BLOCK, species);
                }
            }
            decorate(param, x, z, floorY, top, species);
        }

        /**
         * [A] {@code coral_claw}: 짧은 기둥 위에서 네 발톱이 바깥으로 휘어 오른다. 발톱 길이는
         * {@code arms}(2..4)다([C] 절차 사본).
         */
        private void claw(Feature feature) {
            int x = feature.localX();
            int z = feature.localZ();
            int floorY = floorY(x, z);
            if (floorY < Blocks.MIN_Y) return;
            int param = feature.param();
            int species = param & 7;
            int stem = (param >>> 3) & 7;
            int arms = (param >>> 6) & 7;
            int turn = (param >>> 13) & 3;
            for (int step = 1; step <= stem; step++) {
                coral(x, floorY + step, z, Blocks.TUBE_CORAL_BLOCK, species);
            }
            int top = floorY + stem;
            for (int index = 0; index < SIDES_4.length; index++) {
                int[] side = SIDES_4[(turn + index) & 3];
                int clawX = x;
                int clawZ = z;
                for (int step = 1; step <= arms; step++) {
                    clawX += side[0];
                    clawZ += side[1];
                    coral(clawX, top, clawZ, Blocks.TUBE_CORAL_BLOCK, species);
                    coral(clawX, top + 1, clawZ, Blocks.TUBE_CORAL_BLOCK, species);
                }
            }
            decorate(param, x, z, floorY, top + 1, species);
        }

        /**
         * [A] {@code coral_mushroom}: 짧은 기둥 위에 둥근 갓이 얹힌다. 갓 반경은
         * {@code arms − 1}(1..3)이다([C] 절차 사본).
         */
        private void mushroom(Feature feature) {
            int x = feature.localX();
            int z = feature.localZ();
            int floorY = floorY(x, z);
            if (floorY < Blocks.MIN_Y) return;
            int param = feature.param();
            int species = param & 7;
            int stem = (param >>> 3) & 7;
            int capRadius = ((param >>> 6) & 7) - 1;
            for (int step = 1; step <= stem; step++) {
                coral(x, floorY + step, z, Blocks.TUBE_CORAL_BLOCK, species);
            }
            int top = floorY + stem;
            for (int layer = 0; layer <= 1; layer++) {
                int layerRadius = capRadius - layer;
                if (layerRadius < 0) continue;
                for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                    for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                        if (dx * dx + dz * dz > layerRadius * layerRadius) continue;
                        coral(x + dx, top + layer, z + dz, Blocks.TUBE_CORAL_BLOCK, species);
                    }
                }
            }
            decorate(param, x, z, floorY, top + 1, species);
        }

        /**
         * 부착 장식. 옆면 셋에는 해저 바닥의 산호 부채가, 꼭대기에는 식물형 산호 한 칸이
         * 붙는다. 부채·식물은 이 저장소에서 {@code SOLID_BELOW} 지지라 반드시 꽉 찬 칸 위에만
         * 앉는다({@code Blocks} 의 지지 표).
         */
        private void decorate(int param, int x, int z, int floorY, int topY, int species) {
            int decor = (param >>> 9) & 15;
            int turn = (param >>> 13) & 3;
            for (int index = 0; index < 3; index++) {
                if (((decor >>> index) & 1) == 0) continue;
                int[] side = SIDES_4[(turn + index) & 3];
                coral(x + side[0], floorY + 1, z + side[1], Blocks.TUBE_CORAL_FAN, species);
            }
            if (((decor >>> 3) & 1) != 0) {
                coral(x, topY + 1, z, Blocks.TUBE_CORAL, species);
            }
        }

        /**
         * 산호 한 칸. [A] 바닐라에서 물에 닿지 않은 산호는 죽으므로, 물칸이면 살아있는 ID 를
         * 공기칸이면 죽은 변형을 쓴다. 그 밖의 칸에는 아무것도 쓰지 않는다.
         */
        private void coral(int localX, int y, int localZ, int base, int species) {
            if (Math.abs(localX) > reach || Math.abs(localZ) > reach
                    || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
            int current = world.getBlock(site.anchorX() + localX, y, site.anchorZ() + localZ);
            if (current != Blocks.AIR && !isWater(current)) return;
            int block = base + species + (isWater(current) ? 0 : Blocks.CORAL_DEAD_OFFSET);
            put(localX, y, localZ, block);
        }

        /** 물칸에만 쓰는 수중 장식. 물이 아니면 아무것도 쓰지 않고 false 를 돌려준다. */
        private boolean putSubmerged(int localX, int y, int localZ, int block) {
            if (Math.abs(localX) > reach || Math.abs(localZ) > reach
                    || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
            if (!isWater(world.getBlock(site.anchorX() + localX, y, site.anchorZ() + localZ))) {
                return false;
            }
            put(localX, y, localZ, block);
            return true;
        }

        /**
         * 이 열의 해저면 y. 꽉 찬 지형 위에 물이 있는 가장 높은 칸이며, 물 밑이 아닌 열은
         * {@code MIN_Y − 1} 이다 — 해저 유적과 같은 판정이다.
         */
        private int floorY(int localX, int localZ) {
            if (Math.abs(localX) > reach || Math.abs(localZ) > reach) return Blocks.MIN_Y - 1;
            int index = (localZ + reach) * width + localX + reach;
            if (floor[index] != Integer.MIN_VALUE) return floor[index];
            int worldX = site.anchorX() + localX;
            int worldZ = site.anchorZ() + localZ;
            floor[index] = Blocks.MIN_Y - 1;
            for (int y = Blocks.MAX_Y - 2; y >= Blocks.MIN_Y; y--) {
                if (!StructureTerrainRules.isStableGround(world.getBlock(worldX, y, worldZ))) {
                    continue;
                }
                if (isWater(world.getBlock(worldX, y + 1, worldZ))) floor[index] = y;
                break;
            }
            return floor[index];
        }

        private void put(int localX, int y, int localZ, int block) {
            int worldX = site.anchorX() + localX;
            int worldZ = site.anchorZ() + localZ;
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (plan.containsKey(pos)) return;
            plan.put(pos, RuinGenerator.Voxel.at(worldX, y, worldZ, block));
        }

        private List<RuinGenerator.Voxel> voxels() {
            return plan.size() < MINIMUM_VOXELS ? List.of() : List.copyOf(plan.values());
        }
    }

    private static boolean isWater(int block) {
        return block >= Blocks.WATER_SOURCE && block < Blocks.LAVA_SOURCE;
    }
}
