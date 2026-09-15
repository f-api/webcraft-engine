package com.gameexpert.engine.mob;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 디스폰 대상과 사유("far"/"random"). 어댑터가 mobDespawn WS 로 매핑. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class MobDespawn {

    private final Mob mob;
    private final String reason;
}
