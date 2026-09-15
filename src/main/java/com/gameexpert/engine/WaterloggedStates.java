package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;

/**
 * 최종 carrier 가 심은 exact state 의 {@code waterlogged=true} 를 런타임 물 판정으로 되살린다.
 *
 * <p>바닐라에서 waterlogged 블록은 블록이면서 동시에 <b>레벨 8 수원</b>이다
 * ({@code BlockBehaviour#getFluidState} 가 {@code Fluids.WATER.getSource(false)} 를 돌려주고,
 * {@code FlowingFluid} 는 그 FluidState 만 보고 이웃에 공급한다). 블록을 부수면 물이 남고,
 * 머리가 그 칸에 있으면 익사 판정이 걸린다.
 *
 * <p><b>상태 바이트의 두 이름공간.</b> 이 엔진의 상태 바이트에는 두 종류가 섞여 있다 —
 * carrier 가 심은 {@link Mc263ExactStateCodec} 코드(청크 상주 셀)와, 엔진 규칙이 직접 쓴
 * 의미 바이트({@code BuildingBlockRules} 의 이끼광원 6면 마스크, {@code P3Rules} 의 드립리프
 * 기울기, {@code CopperAgeRules} 의 bit 7 …)다. 후자는 codebook 코드가 아니므로 그대로 디코딩하면
 * 거짓 양성이 난다. 따라서 이 클래스는 <b>엔진이 아직 상태를 쓴 적 없는 셀</b>에서만 carrier
 * 코드를 읽는다 — 권위 상태가 0 이거나, 그 칸의 carrier 코드를 투영한 값인 셀이다
 * ({@link com.gameexpert.block.BlockStateStorage} 는 항목 없는 생성 칸에 그 투영을 돌려준다).
 * 엔진이 그 칸의 상태를 덮어쓰는 순간 waterlogged 정보는 사라지는데, 이는 이름공간이 하나로
 * 합쳐지기 전까지 남는 구조적 잔여물이다.
 */
public final class WaterloggedStates {

    /** 0=미확정, 1=waterlogged state 를 가진 블록, 2=없음. 첫 조회에서만 codebook 을 훑는다. */
    private static final byte[] FAMILY = new byte[Blocks.BLOCK_ID_HIGH_WATER + 1];

    private WaterloggedStates() {
    }

    /** 이 블록 ID 의 exact codebook 에 waterlogged=true 갈래가 하나라도 있는가. */
    public static boolean hasWaterloggedState(int blockId) {
        if (blockId <= Blocks.AIR || blockId >= FAMILY.length) return false;
        byte cached = FAMILY[blockId];
        if (cached != 0) return cached == 1;
        boolean present = scanCodebook(blockId);
        // 순수 함수 결과라 경쟁 기록이 같은 값을 쓴다.
        FAMILY[blockId] = (byte) (present ? 1 : 2);
        return present;
    }

    private static boolean scanCodebook(int blockId) {
        int count;
        try {
            count = Mc263ExactStateCodec.stateCount(blockId);
        } catch (IllegalArgumentException outsideCodebook) {
            return false;
        }
        for (int code = 0; code < count; code++) {
            if (Mc263ExactStateCodec.decode(blockId, code).fluidKind()
                    == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                return true;
            }
        }
        return false;
    }

    /** carrier 이름공간의 (블록, 상태 코드) 쌍이 수원을 품는가. */
    public static boolean isWaterloggedCarrierState(int blockId, int stateCode) {
        if (stateCode <= 0 || Fluids.isFluid(blockId) || !hasWaterloggedState(blockId)) {
            return false;
        }
        try {
            return Mc263ExactStateCodec.decode(blockId, stateCode).fluidKind()
                    == Mc263FeatureBlockState.FluidKind.WATER_SOURCE;
        } catch (IllegalArgumentException outsideCodebook) {
            return false;
        }
    }

    /** 상주 청크의 immutable snapshot 이 들고 있는 exact 상태 코드. 비상주면 0. */
    public static int carrierStateAt(TerrainAccessor accessor, int x, int y, int z) {
        if (accessor == null || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return 0;
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(
                Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        if (source == null) return 0;
        return source.blockStateAt(Blocks.blockIndex(
                Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z)));
    }

    /**
     * 런타임 셀 하나가 waterlogged 인가. {@code engineState} 는 권위가 읽는 상태다.
     *
     * <p>0 이면 carrier 코드를 읽는다. 0 이 아니면 둘 중 하나다 — 엔진이 쓴 값이거나, 엔진이 쓴
     * 적 없는 생성 칸에 권위가 돌려준 carrier 투영({@code CarrierStateProjection}, 비트 7 =
     * waterlogged). 후자는 스냅샷 뷰가 여전히 같은 carrier 코드를 들고 있고 그 투영이 권위 상태와
     * 같다. 엔진이 쓴 값은 carrier 코드로 해석하지 않는다.
     *
     * <p>스냅샷 뷰의 코드 0 은 sparse 평면에 항목이 없는 칸, 곧 carrier 코드 0 이다. 물에 잠긴
     * 산호·바다 피클처럼 코드 0 자체가 {@code waterlogged=true} 인 블록은 권위가 그 투영(비트 7)을
     * 돌려주므로, 투영이 권위 상태와 같고 비트 7 이 서 있으면 물을 품는다.
     */
    public static boolean isWaterloggedAt(TerrainAccessor accessor, int blockId,
            int engineState, int x, int y, int z) {
        if (accessor == null || Fluids.isFluid(blockId)
                || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
        var source = accessor.snapshotSource(Math.floorDiv(x, Blocks.CHUNK_X),
                Math.floorDiv(z, Blocks.CHUNK_Z));
        if (BuildingBlockRules.isPost7Waterloggable(blockId) && source != null) {
            int residentIndex = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                    Math.floorMod(z, Blocks.CHUNK_Z));
            // engineState is the authority compact projection, including persisted player edits.
            // Do not feed these bytes into the immutable old world's generated codebook.
            return source.blockTypeAt(residentIndex) == blockId
                    && BuildingBlockRules.canAcceptWater(blockId, engineState)
                    && (engineState & BuildingBlockRules.WATERLOGGED) != 0;
        }
        var carrier = source == null ? null : source.finalLiveCarrier().orElse(null);
        if (carrier == null) return false;
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        if (source.generatedBlockTypeAt(index) != blockId || source.blockTypeAt(index) != blockId)
            return false;
        var exact = carrier.stateOverrides().get(index);
        if (exact == null) exact = carrier.defaultState(blockId);
        if (exact.blockId() != blockId || source.blockStateAt(index) != exact.stateCode()) return false;
        int projected = com.gameexpert.block.snapshot.CarrierStateProjection.projectState(blockId, exact);
        boolean wet = (projected & com.gameexpert.block.snapshot.CarrierStateProjection.WATERLOGGED) != 0;
        if (engineState == 0) return exact.stateCode() != 0 && wet;
        return wet && projected == engineState;
    }
}
