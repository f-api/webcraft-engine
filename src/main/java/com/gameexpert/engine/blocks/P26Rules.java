package com.gameexpert.engine.blocks;

import com.gameexpert.terrain.Blocks;

/**
 * [SPRING-TO-LIFE] 1.21.5 자연 요소 블록(1350~1355 + 기존 {@link Blocks#BUSH})의 서버 규칙.
 *
 * <p>근거 등급은 {@code docs/research/mc-1215/SPRING_TO_LIFE.md} 와 같다 —
 * [A]=1.21.5-rc1 원문 에셋 · [B]=위키 본문 · [C]=이 저장소 자체 계약.
 *
 * <p>이 클래스는 <b>순수 함수만</b> 담는다. 난수 소비와 월드 쓰기는 호출자
 * ({@code RandomTickSystem} / 정적판 {@code StandaloneNaturalEnvironment})가 하고,
 * 두 권위는 같은 판정을 여기서만 읽는다.
 */
public final class P26Rules {

    /** 뼛가루 번식이 이웃 칸을 고를 때의 시도 횟수. 잔디 확산({@code spreadGrass})과 같은 형태다. [C] */
    public static final int SPREAD_ATTEMPTS = 8;

    private P26Rules() {}

    // ── 지지 바닥 ──────────────────────────────────────────────────────────────

    /**
     * 야생화·반딧불 수풀·수풀이 놓일 수 있는 바닥인가.
     *
     * <p>[B] 위키가 세 블록 모두에 대해 같은 목록을 준다: grass block · mycelium · podzol ·
     * dirt · coarse dirt · rooted dirt · farmland · mud · muddy mangrove roots · moss block ·
     * pale moss block. 이 저장소에는 pale moss 가 없고, 나머지는 이미
     * {@link P6Rules#isSaplingSoil(int)} 이 같은 집합을 들고 있으므로 <b>그 술어를 재사용</b>하고
     * 목록에만 있는 경작지를 더한다(같은 흙 목록을 두 번 적지 않는다).
     */
    public static boolean isPlantSoil(int belowId) {
        return P6Rules.isSaplingSoil(belowId) || belowId == Blocks.FARMLAND;
    }

    /**
     * 마른 풀 두 종이 놓일 수 있는 바닥인가. [B] 위키 목록은 위 흙 목록에 <b>terracotta ·
     * sand · suspicious sand · red sand</b> 를 더한 것이다. 이 저장소에는 suspicious sand 가
     * 없고, 테라코타는 무색 원본과 16색을 모두 포함한다.
     *
     * <p>색 테라코타는 <b>연속 ID 가 아니다</b>(기존 6색 237~243 + 콘크리트 트랙 10색
     * 1108~1117). 그래서 범위 비교를 손으로 적지 않고 그 둘을 잇는 유일한 지점인
     * {@link Blocks#isColoredTerracotta(int)} 을 쓴다.
     */
    public static boolean isDryGrassSoil(int belowId) {
        return isPlantSoil(belowId)
                || belowId == Blocks.SAND || belowId == Blocks.RED_SAND
                || belowId == Blocks.TERRACOTTA || Blocks.isColoredTerracotta(belowId);
    }

    /**
     * 선인장 꽃이 놓일 수 있는 바닥인가. [B] "cactus blocks, farmland, or any block which
     * provides center support at the top". 윗면 중앙 지지 판정은 호출자의 고체 상면 판정이
     * 맡고, 여기서는 그 판정에 <b>선인장을 더하는</b> 갈래만 소유한다 — 선인장은 XZ 1픽셀
     * 인셋 박스라 일반 고체 상면 판정에 걸리지 않기 때문이다.
     */
    public static boolean isCactusFlowerSupport(int belowId, boolean solidTopBelow) {
        return belowId == Blocks.CACTUS || belowId == Blocks.FARMLAND || solidTopBelow;
    }

    /** 이 트랙이 뼛가루 번식으로 <b>덮어써도 되는</b> 목표 칸인가. 공기와 풀 다발만 허용한다. [C] */
    public static boolean isSpreadTarget(int blockId) {
        return blockId == Blocks.AIR || blockId == Blocks.TALL_GRASS;
    }

    // ── FlowerBedBlock 누적 설치 ───────────────────────────────────────────────

    /**
     * 이미 같은 {@code FlowerBedBlock} 이 있는 칸에 한 조각을 더 놓을 수 있는가.
     * [A] 블록스테이트가 사분면 넷을 누적하므로 상한은 {@link Blocks#FLOWER_BED_MAX_AMOUNT} 다.
     */
    public static boolean canAddSegment(int existingBlock, int existingState, int placedBlock) {
        return Blocks.isFlowerBedBlock(placedBlock) && existingBlock == placedBlock
                && Blocks.flowerBedAmount(existingState) < Blocks.FLOWER_BED_MAX_AMOUNT;
    }

    /**
     * 설치 결과 상태. 빈 칸이면 조각 1 + 플레이어 facing 이고, 같은 블록 위에 겹쳐 놓으면
     * 조각 수만 1 늘리고 <b>facing 은 첫 조각의 것을 유지한다</b>([A] 블록스테이트가 한 칸에
     * facing 하나만 두므로 나중 조각이 이미 놓인 조각을 돌려세우면 안 된다).
     */
    public static int placementState(int existingBlock, int existingState, int placedBlock, int facing) {
        if (canAddSegment(existingBlock, existingState, placedBlock)) {
            return Blocks.flowerBedState(
                    Blocks.flowerBedAmount(existingState) + 1, Blocks.flowerBedFacing(existingState));
        }
        return Blocks.flowerBedState(1, facing);
    }

    // ── 뼛가루 ────────────────────────────────────────────────────────────────

    /**
     * 이 블록이 뼛가루 대상인가.
     *
     * <p>갈래 넷 [B]:
     * <ul>
     *   <li>야생화 — 조각 수가 4 <b>미만</b>일 때만. 아래 divergence 주석을 볼 것.</li>
     *   <li>수풀 · 반딧불 수풀 — 이웃 칸에 같은 블록을 하나 더 틔운다.</li>
     *   <li>짧은 마른 풀 — 제자리에서 큰 마른 풀이 된다.</li>
     *   <li>큰 마른 풀 — 이웃 칸에 짧은 마른 풀을 놓는다.</li>
     * </ul>
     *
     * <p><b>낙엽 리터는 대상이 아니다</b>: 위키 «Leaf Litter» 에 뼛가루 서술이 없어 근거가
     * 없다. 추측으로 넣지 않는다(P3Rules 가 진달래를 대상이라고 거짓 보고하지 않는 것과 같은 이유).
     *
     * <p><b>[C] divergence — 포화 야생화</b>: 바닐라는 조각이 이미 4 면 뼛가루를 소비하고
     * 자기 자신 1개를 아이템으로 떨군다 [B]. 이 저장소의 뼛가루 계약에는 <b>아이템 드랍
     * 채널이 없다</b>({@code applyBoneMeal} 은 블록/상태 치환만 돌려준다). 그래서 포화 상태를
     * "대상 아님" 으로 보고해 <b>뼛가루를 소비하지 않는다</b> — 아무 일도 없이 소비만 하는
     * 쪽보다 플레이어에게 손해가 없다. 드랍 채널이 생기면 이 한 줄만 되돌리면 된다.
     */
    public static boolean isBoneMealTarget(int blockId, int state) {
        if (blockId == Blocks.WILDFLOWERS) {
            return Blocks.flowerBedAmount(state) < Blocks.FLOWER_BED_MAX_AMOUNT;
        }
        return blockId == Blocks.BUSH || blockId == Blocks.FIREFLY_BUSH
                || Blocks.isDryGrass(blockId);
    }

    /**
     * 제자리 상태만 바뀌는 뼛가루 결과. {@code -1} 은 이 갈래가 아님을 뜻한다.
     * 야생화만 해당하며 조각 수를 하나 올린다 [B].
     */
    public static int boneMealStateResult(int blockId, int state) {
        if (blockId != Blocks.WILDFLOWERS) return -1;
        int amount = Blocks.flowerBedAmount(state);
        if (amount >= Blocks.FLOWER_BED_MAX_AMOUNT) return -1;
        return Blocks.flowerBedState(amount + 1, Blocks.flowerBedFacing(state));
    }

    /**
     * 제자리 블록이 통째로 바뀌는 뼛가루 결과. {@code 0} 은 이 갈래가 아님을 뜻한다.
     * [B] "Bone meal can be used on short dry grass to turn it into tall dry grass."
     */
    public static int boneMealBlockResult(int blockId) {
        return blockId == Blocks.SHORT_DRY_GRASS ? Blocks.TALL_DRY_GRASS : 0;
    }

    /**
     * 뼛가루가 <b>이웃 칸</b>에 놓는 블록. {@code 0} 은 이 갈래가 아님을 뜻한다.
     * [B] 수풀·반딧불 수풀은 자기 자신을, 큰 마른 풀은 <b>짧은</b> 마른 풀을 퍼뜨린다.
     */
    public static int boneMealSpreadBlock(int blockId) {
        if (blockId == Blocks.BUSH || blockId == Blocks.FIREFLY_BUSH) return blockId;
        if (blockId == Blocks.TALL_DRY_GRASS) return Blocks.SHORT_DRY_GRASS;
        return 0;
    }

    /** 번식으로 놓이는 블록이 요구하는 바닥인가. 마른 풀만 모래·테라코타까지 허용한다. */
    public static boolean canPlantOn(int plantId, int belowId) {
        if (Blocks.isDryGrass(plantId)) return isDryGrassSoil(belowId);
        return isPlantSoil(belowId);
    }

    // ── 채굴 드랍 ─────────────────────────────────────────────────────────────

    /**
     * 가위/실크 터치 조건이 있는 채굴 드랍 보정. [B] 마른 풀 두 종은 가위(또는 실크 터치)가
     * 아니면 <b>아무것도</b> 떨구지 않는다. 수풀({@link Blocks#BUSH})의 같은 규칙은 이미
     * {@link P2Rules#minedDrop} 이 소유하므로 여기서 다시 적지 않는다.
     *
     * <p>야생화·낙엽 리터·반딧불 수풀·선인장 꽃은 언제나 자신을 떨군다 [B] — 기본 드랍이
     * 그대로라 갈래가 없다.
     */
    public static int minedDrop(int blockId, boolean shears, int currentDrop) {
        if (Blocks.isDryGrass(blockId)) return shears ? blockId : Blocks.AIR;
        return currentDrop;
    }

    /**
     * 드랍 수량. [B] {@code FlowerBedBlock} 두 종은 "그 칸에 있던 조각 수만큼" 떨군다.
     * 그 밖에는 입력 그대로다.
     */
    public static int minedDropCount(int blockId, int state, int currentCount) {
        return Blocks.isFlowerBedBlock(blockId) ? Blocks.flowerBedAmount(state) : currentCount;
    }

    // ── 형상 ──────────────────────────────────────────────────────────────────

    /**
     * 서버 충돌 높이 보정. [A] {@code template_leaf_litter_1} 의 원소는 두께 0 인 평면
     * ({@code from.y == to.y == 0.25})이라 바닐라도 충돌이 없다. 이 저장소는 렌더를 1/16
     * 부분 높이로 내지만 <b>충돌은 0 으로</b> 둬서 걸어 지날 때 걸리지 않게 한다.
     */
    public static double collisionHeight(int blockId, int state, double current) {
        return blockId == Blocks.LEAF_LITTER ? 0.0 : current;
    }

    /** 반딧불 수풀의 바닐라 광량. [B] "Light Level: Yes (2)". */
    public static final int FIREFLY_BUSH_LIGHT = 2;
}
