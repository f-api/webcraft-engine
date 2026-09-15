package com.gameexpert.engine.mob;

/**
 * WebCraft 소 외형용 성별. 바닐라 Minecraft에는 소 성별 구분이 없지만,
 * WebCraft는 서버 권위 외형 상태로 암소/숫소를 구분한다.
 */
public enum CowSex {
    FEMALE,
    MALE;

    /**
     * 단독·특수 스폰의 결정론적 75% 암소 선택.
     * 같은 월드 시드·몹 ID·스폰 좌표는 실행 순서나 RNG 상태와 무관하게 같은 결과를 낸다.
     */
    public static CowSex deterministic(int worldSeed, long mobId, double spawnX, double spawnZ) {
        return MobVariant.deterministicIndex(worldSeed, mobId, spawnX, spawnZ, 4) == 0
                ? MALE : FEMALE;
    }

    public String protocolName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * [FARM-VARIANT] 소 변종 문자열의 성별 판정. 1.21.5 기후 축이 같은 문자열에 접미사로
     * 붙으므로(`female_warm` 등) 성별을 볼 때는 반드시 이 경계를 지나야 한다.
     * 접미사 없는 옛 이름은 temperate 소이고 성별 해석이 그대로다.
     */
    public static boolean isFemale(String cowVariant) {
        return FEMALE.protocolName().equals(FarmAnimalVariantRules.cowSexOf(cowVariant));
    }
}
