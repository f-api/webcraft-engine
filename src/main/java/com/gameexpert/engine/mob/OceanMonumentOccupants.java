package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.structure.OceanMonumentPlacement;
import com.gameexpert.engine.structure.StructureHash;

/**
 * [MONUMENT] 해저 신전 거주자 명단(순수·상태 없음).
 *
 * <p>바닐라 {@code structure/monument.json} 의 {@code spawn_overrides} 는 신전 <b>전체 상자</b>
 * 안에서 몬스터 표를 가디언 하나로 덮는다: {@code weight 1, minCount 2, maxCount 4}. 그리고
 * 엘더 가디언 3기는 스폰 표가 아니라 조각이 고정 좌표에 직접 놓는다
 * (`docs/research/mc-vanilla-1214/MONUMENT.md` §5).
 *
 * <p>이 클래스는 그 두 규칙을 명단으로 만든다 — 엘더 3기는 {@link GuardianSpawnRules#monumentRoster}
 * 를 그대로 쓰고, 일반 가디언은 사이트당 한 무리(2–4)를 바닐라 표의 균등 분포로 뽑는다.
 */
public final class OceanMonumentOccupants {

    private static final int PACK_SALT = 0x5b21_e70f;

    private OceanMonumentOccupants() {}

    /** 바닐라 무리 크기: {@code minCount..maxCount} 균등(2, 3, 4). */
    public static int guardianPackSize(int worldSeed, long siteKey) {
        int span = OceanMonumentPlacement.GUARDIAN_MAX_COUNT
                - OceanMonumentPlacement.GUARDIAN_MIN_COUNT + 1;
        int lane = StructureHash.mix32(
                StructureHash.seedSalt(worldSeed, PACK_SALT) ^ (int) siteKey);
        return OceanMonumentPlacement.GUARDIAN_MIN_COUNT + Integer.remainderUnsigned(lane, span);
    }

    /**
     * 신전 하나가 채택될 때의 명단: 엘더 3기 + 가디언 한 무리.
     * 엘더가 항상 앞이라 고정 좌표 배치와 인덱스가 맞는다.
     */
    public static List<MobType> roster(int worldSeed, long siteKey) {
        List<MobType> roster = new ArrayList<>(
                GuardianSpawnRules.MONUMENT_ELDER_COUNT + OceanMonumentPlacement.GUARDIAN_MAX_COUNT);
        roster.addAll(GuardianSpawnRules.monumentRoster());
        int pack = guardianPackSize(worldSeed, siteKey);
        for (int index = 0; index < pack; index++) roster.add(MobType.GUARDIAN);
        return List.copyOf(roster);
    }
}
