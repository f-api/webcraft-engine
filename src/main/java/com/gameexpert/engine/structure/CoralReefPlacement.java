package com.gameexpert.engine.structure;

/**
 * [CORAL-REEF] 산호초 사이트의 배치 상수와 순수 정수 산술.
 *
 * <p><b>근거 등급.</b> 바닐라 1.21.4 의 산호초는 구조물이 아니라 <b>바이옴 feature</b>다
 * ({@code placed_feature/warm_ocean_vegetation.json} 이 warm ocean 바이옴의 vegetal 단계에
 * 얹힌다) — 옮겨올 {@code structure_set} JSON 이 없다. 이 저장소의 기후 표·{@code generateChunk}
 * 는 1.21.4 스냅샷으로 고정되어 있고 {@code terrain-golden.json} 이 그 해시를 핀하므로
 * 지형 생성 단계에 feature 를 더하면 golden 이 깨진다. 그래서 유황 동굴·딥다크 도시·얼룩덜룩한 숲과
 * <b>같은 판단</b>으로 산호초를 런타임 구조물 사이트 lane 으로 옮겼다(divergence 이며 이
 * 트랙이 새로 만든 문법이 아니다). 배치에 관한 한 근거 등급은 전부 <b>[C](자체 계약)</b>다.
 *
 * <p>지대 안에 <b>무엇을</b> 놓는지는 [A] 1.21.4 원문에서 온다:
 * <ul>
 *   <li>{@code configured_feature/warm_ocean_vegetation.json} 의 세 갈래 —
 *       {@code coral_tree} · {@code coral_claw} · {@code coral_mushroom} 가 1:1:1 이다.</li>
 *   <li>{@code placed_feature/kelp_warm.json} — {@code noise_based_count}
 *       {@code noise_to_count_ratio: 80}.</li>
 *   <li>{@code placed_feature/seagrass_warm.json} — {@code count: 80} 에
 *       {@code configured_feature/seagrass_short.json} 의 {@code probability: 0.3}
 *       (= 큰 해초 30 % · 한 칸 해초 70 %).</li>
 *   <li>{@code placed_feature/sea_pickle.json} — {@code rarity_filter} 1/16 에
 *       {@code configured_feature/sea_pickle.json} 의 {@code count: 20}.</li>
 * </ul>
 * 세 콜로니의 <b>기하</b>는 NBT 템플릿이 아니라 절차적 사본이며, 어휘
 * (형태 이름·개수 비율·밀도 리터럴)만 [A] 다.
 *
 * <p><b>희귀도 계약</b>
 * <ul>
 *   <li>격자 칸: 12 청크(192 블록). 바닐라 산호초는 온수 바다 <b>안에서는</b> 청크마다 나오는
 *       흔한 feature 라, "가끔 만나는 명소" 인 유황 동굴(640)이 아니라 얼룩덜룩한 숲(256)과 같은
 *       촘촘한 리듬을 쓴다.</li>
 *   <li>존재 확률은 AGENTS 38e 그대로다: 기준선 65_536 에
 *       {@code AdmissionScale.UNDERWATER}(1/5)를 곱해 <b>칸당 20 %</b>. 침수 폐허·해저 폐허와
 *       같은 적용이라 이 트랙이 새 확률 문법을 만들지 않는다. 평균 간격은
 *       192/√0.2 ≈ 429 블록이고, 그 위에 온수 바다 관문이 한 번 더 걸린다.</li>
     *   <li>바이옴 관문은 canonical 26.3 바이옴 레지스트리의 warm-ocean-floor 술어를 쓴다.</li>
 * </ul>
 *
 * <p><b>실수를 쓰지 않는다.</b> 반경·감쇠·격자 지터가 전부 정수 산술만 지난다 — Java 와 Rust
 * 의 부동소수점 반올림이 한 좌표에서라도 갈리면 정적판과 온라인이 서로 다른 산호초를 낸다.
 *
 * <p>Rust 사본은 {@code client/wasm/src/mc_structure/coral_reef.rs} 이고 두 권위는 손 사본이다.
 */
public final class CoralReefPlacement {

    private CoralReefPlacement() {}

    // ── 격자 ──────────────────────────────────────────────────────

    /** 산호초 격자 칸 한 변(청크). */
    public static final int SPACING_CHUNKS = 12;
    /** 산호초 격자 칸 한 변(블록). */
    public static final int CELL_SIZE = SPACING_CHUNKS * StructureSiteDescriptor.CHUNK_SIZE;
    /** 산호초 전용 site salt. 다른 kind 와 겹치지 않는 새 값이다. */
    public static final int SITE_SALT = 0x2b7e_5c19;
    /**
     * 보수적 수평 상한. 가장 큰 규모의 반경 17 에 콜로니 가지가 뻗는 최대 3 칸과 부착 장식
     * 1 칸, 여유 1 칸을 더한 값이다.
     */
    public static final int MAX_REACH = 22;

    // ── 규모 분포([C]) ────────────────────────────────────────────
    //
    // 유황 동굴·얼룩덜룩한 숲과 같은 부호 없는 백분위 50 / 35 / 15 다. 새 분포 문법을 만들지 않는다.

    /** 규모 백분위 경계(작음 미만). */
    public static final int SMALL_PERCENTILE = 50;
    /** 규모 백분위 경계(보통 미만). */
    public static final int MEDIUM_PERCENTILE = 85;

    /** 규모별 반경(작음/보통/큼). */
    public static final int[] RADII = {9, 13, 17};
    /** 규모별 최소 feature 수. 이 아래면 사이트를 통째로 버린다(민무늬 해저 방지). */
    public static final int[] MINIMUM_FEATURES = {20, 40, 70};

    /**
     * 콜로니 후보를 뽑는 하위 격자 한 변. 콜로니끼리 겹치지 않게 하는 유일한 장치이며 이웃
     * 검색을 하지 않는다 — 하위 칸마다 정확히 한 후보만 나오므로 두 권위가 같은 순서로 같은
     * 자리를 얻는다. 4 는 가장 큰 콜로니(발톱)의 수평 지름 5 보다 촘촘해 산호가 서로 얽히는
     * 실제 산호초의 밀집을 만든다([C]).
     */
    public static final int COLONY_GRID = 4;

    /**
     * 중심/가장자리 콜로니 밀도(256 분모). 사이트 경계가 칼로 자른 원이 되지 않도록 반경
     * 제곱에 선형으로 감쇠시킨다 — 전부 정수 나눗셈이다.
     */
    public static final int COLONY_CORE_DENSITY = 208;
    /** 가장자리 콜로니 밀도. */
    public static final int COLONY_EDGE_DENSITY = 72;

    // ── [A] 바닐라 밀도 리터럴 ────────────────────────────────────

    /** 한 청크의 열 수. 아래 [A] "청크당 개수" 를 열 확률로 옮기는 분모다. */
    public static final int COLUMNS_PER_CHUNK = 256;
    /** 열 굴림의 분모. 두 권위가 같은 정수 비교를 하도록 1024 로 고정한다. */
    public static final int DENSITY_SCALE = 1024;

    /** [A] {@code kelp_warm.json} 의 {@code noise_to_count_ratio: 80}. */
    public static final int KELP_COUNT_PER_CHUNK = 80;
    /** [A] {@code seagrass_warm.json} 의 {@code count: 80}. */
    public static final int SEAGRASS_COUNT_PER_CHUNK = 80;
    /**
     * [A] {@code seagrass_short.json} 의 {@code probability: 0.3} — 큰 해초 비율이다.
     * 바닐라 {@code SeagrassFeature} 에서 이 확률은 "배치 확률" 이 아니라 <b>두 칸짜리 큰
     * 해초</b>가 될 확률이므로, 나머지 70 % 가 한 칸 해초다.
     */
    public static final int SEAGRASS_TALL_PERCENT = 30;

    /** 켈프의 열 확률(1024 분모). [A] 청크당 80 개를 그대로 옮긴 값이다. */
    public static final int KELP_DENSITY =
            KELP_COUNT_PER_CHUNK * DENSITY_SCALE / COLUMNS_PER_CHUNK;
    /** 해초의 열 확률(1024 분모). [A] 청크당 80 개를 그대로 옮긴 값이다. */
    public static final int SEAGRASS_DENSITY =
            SEAGRASS_COUNT_PER_CHUNK * DENSITY_SCALE / COLUMNS_PER_CHUNK;

    /** [A] {@code kelp} 줄기 높이 상한. 이 저장소의 기존 수중 매핑과 같은 1~10 이다. */
    public static final int KELP_MAX_HEIGHT = 10;

    /** [A] {@code sea_pickle.json} 의 {@code rarity_filter} 분모 16. */
    public static final int SEA_PICKLE_RARITY = 16;
    /**
     * [A] {@code configured_feature/sea_pickle.json} 의 {@code count: 20} — 한 번 뽑힌
     * 군집이 흩뿌리는 시도 횟수다. 이 저장소는 sea pickle 을 한 칸 ID 로만 표현하므로 군집
     * 하나가 최대 이만큼의 <b>열</b>을 차지한다.
     */
    public static final int SEA_PICKLE_CLUSTER_ATTEMPTS = 20;
    /** 군집이 흩어지는 반경(칸). [C] — 바닐라 {@code SeaPickleFeature} 의 흩뿌림 상자다. */
    public static final int SEA_PICKLE_CLUSTER_SPREAD = 4;

    // ── 콜로니 종류 비율([A] warm_ocean_vegetation.json) ──────────

    /**
     * {@code simple_random_selector} 의 세 갈래는 가중치가 없다 — 1:1:1 이다. 두 권위가 같은
     * 정수 나눗셈으로 고르도록 3 을 상수로 둔다.
     */
    public static final int COLONY_SHAPE_COUNT = 3;

    /** 산호 색 수. {@code Blocks.CORAL_SPECIES_COUNT} 와 같은 값이며 그쪽이 정본이다. */
    public static final int SPECIES_COUNT = com.gameexpert.terrain.Blocks.CORAL_SPECIES_COUNT;

    // ── lane salt ────────────────────────────────────────────────

    /** 규모 표를 고르는 lane salt. */
    public static final int SCALE_SALT = 0x00c0_1a01;

    /** 이 kind 가 산호초인가. */
    public static boolean isCoralReef(StructureSiteDescriptor.Kind kind) {
        return kind == StructureSiteDescriptor.Kind.CORAL_REEF;
    }

    /**
     * 온수 바다 관문. canonical terrain product의 바이옴 레지스트리가 유일한 입력이다.
     */
    public static boolean isWarmOcean(int biome) {
        return com.gameexpert.terrain.mc.biome.McBiomeRegistry.isWarmOceanFloor(biome);
    }

    /** 규모 등급 0(작음)/1(보통)/2(큼). 유황 동굴·얼룩덜룩한 숲과 같은 백분위 표다. */
    public static int scaleIndex(StructureSiteDescriptor site) {
        int percentile = Integer.remainderUnsigned(site.partLane0(0, SCALE_SALT), 100);
        if (percentile < SMALL_PERCENTILE) return 0;
        return percentile < MEDIUM_PERCENTILE ? 1 : 2;
    }

    /** 이 site 의 사이트 반경. */
    public static int radius(StructureSiteDescriptor site) {
        return RADII[scaleIndex(site)];
    }

    /** 이 site 가 계획을 유지하기 위한 최소 feature 수. */
    public static int minimumFeatures(StructureSiteDescriptor site) {
        return MINIMUM_FEATURES[scaleIndex(site)];
    }
}
