package com.gameexpert.engine;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/** [제공코드] 정수 월드 좌표 한 칸(오버레이/유체 스케줄 키). */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
@EqualsAndHashCode
public class BlockPos {

    private final int x;
    private final int y;
    private final int z;
}
