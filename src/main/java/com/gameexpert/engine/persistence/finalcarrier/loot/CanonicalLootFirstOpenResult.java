package com.gameexpert.engine.persistence.finalcarrier.loot;

import com.gameexpert.map.dto.WorldMapData;
import java.util.List;

/** Terminal result of a locked canonical container first-open transaction. */
public final class CanonicalLootFirstOpenResult {
    public enum Status { RESOLVED, REJECTED }

    private final Status status;
    private final String definitionFingerprint;
    private final String resultFingerprint;
    private final CanonicalLootStoredResolution resolution;
    private final List<WorldMapData> materializedMaps;

    private CanonicalLootFirstOpenResult(Status status, String definitionFingerprint,
            String resultFingerprint, CanonicalLootStoredResolution resolution,
            List<WorldMapData> materializedMaps) {
        this.status = status;
        this.definitionFingerprint = definitionFingerprint;
        this.resultFingerprint = resultFingerprint;
        this.resolution = resolution;
        this.materializedMaps = List.copyOf(materializedMaps);
    }

    public static CanonicalLootFirstOpenResult resolved(WorldCanonicalLootAssignment assignment,
            CanonicalLootStoredResolution resolution) {
        return new CanonicalLootFirstOpenResult(Status.RESOLVED,
                assignment.getDefinitionFingerprint(), assignment.getResultFingerprint(), resolution,
                List.of());
    }

    public static CanonicalLootFirstOpenResult resolved(WorldCanonicalLootAssignment assignment,
            CanonicalLootStoredResolution resolution, List<WorldMapData> materializedMaps) {
        return new CanonicalLootFirstOpenResult(Status.RESOLVED,
                assignment.getDefinitionFingerprint(), assignment.getResultFingerprint(), resolution,
                materializedMaps);
    }

    public static CanonicalLootFirstOpenResult rejected(WorldCanonicalLootAssignment assignment) {
        return new CanonicalLootFirstOpenResult(Status.REJECTED,
                assignment.getDefinitionFingerprint(), null, null, List.of());
    }

    public Status status() { return status; }
    public String definitionFingerprint() { return definitionFingerprint; }
    public String resultFingerprint() { return resultFingerprint; }
    public CanonicalLootStoredResolution resolution() { return resolution; }
    public List<WorldMapData> materializedMaps() { return materializedMaps; }
}
