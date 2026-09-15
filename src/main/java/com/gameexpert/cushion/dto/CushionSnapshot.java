package com.gameexpert.cushion.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Durable authority state for one 26.3 cushion entity. Riders are session state, not persisted. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class CushionSnapshot {
    private final long cushionId;
    private final short itemType;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final String itemComponentData;
}
