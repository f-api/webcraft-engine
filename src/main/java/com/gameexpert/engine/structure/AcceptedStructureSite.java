package com.gameexpert.engine.structure;

import java.util.Objects;

import com.gameexpert.engine.SurfaceDecorator;

/** Immutable identity and final-plan facts for an accepted independent structure site. */
public final class AcceptedStructureSite {
    private final StructureSiteDescriptor.Kind kind;
    private final int cellX;
    private final int cellZ;
    private final long siteKey;
    private final int anchorX;
    private final int anchorZ;
    private final StructureAabb bounds;
    private final int placementCount;
    /**
     * 이 사이트의 <b>앵커 노이즈 바이옴</b>. 바닐라 {@code Structure#generate} 가 시작 좌표에서
     * 한 번 샘플하는 그 값이며, 생성기의 바이옴 관문이 읽은 것과 같다. 채택 사실과 함께 실려
     * 오므로 거주자 명단이 지형을 다시 읽지 않고도 바이옴별 명단을 낼 수 있다(사막 마을 낙타).
     * 값을 모르는 경량 뷰는 {@link SurfaceDecorator#UNKNOWN_NOISE_BIOME} 을 싣고, 바이옴을 보는
     * 명단은 그 경우 fail-closed 로 아무것도 더하지 않는다.
     */
    private final int anchorBiome;

    /** 바이옴을 모르는 호출부(경량 테스트 뷰)의 호환 경로. */
    public AcceptedStructureSite(StructureSiteDescriptor.Kind kind, int cellX, int cellZ,
            long siteKey, int anchorX, int anchorZ, StructureAabb bounds, int placementCount) {
        this(kind, cellX, cellZ, siteKey, anchorX, anchorZ, bounds, placementCount,
                SurfaceDecorator.UNKNOWN_NOISE_BIOME);
    }

    public AcceptedStructureSite(StructureSiteDescriptor.Kind kind, int cellX, int cellZ,
            long siteKey, int anchorX, int anchorZ, StructureAabb bounds, int placementCount,
            int anchorBiome) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (siteKey < 0 || siteKey > 0xffff_ffffL) {
            throw new IllegalArgumentException("siteKey must be an unsigned 32-bit value");
        }
        if (placementCount <= 0) {
            throw new IllegalArgumentException("accepted site must have placements");
        }
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.siteKey = siteKey;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.placementCount = placementCount;
        this.anchorBiome = anchorBiome;
    }

    public StructureSiteDescriptor.Kind kind() { return kind; }
    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
    public long siteKey() { return siteKey; }
    public int anchorX() { return anchorX; }
    public int anchorZ() { return anchorZ; }
    public StructureAabb bounds() { return bounds; }
    public int placementCount() { return placementCount; }
    public int anchorBiome() { return anchorBiome; }
}
