package com.gameexpert.engine.mob;

/**
 * 자연 생성 장비(피글린 석궁·금 검·금 갑옷 등)의 사망 드랍 규칙.
 * 근거는 MC Java 1.21.4 {@code LivingEntity.dropCustomDeathLoot} 와
 * {@code Mob.armorDropChances/handDropChances} 기본값이다. 땅에서 주운 장비는 100% 반환하는
 * 별도 규칙({@code Mob.pickedEquipmentDrops})이 소유하므로 여기 오지 않는다.
 * 정적판 사본은 {@code StandaloneEquipmentDropRules.ts} 하나뿐이다.
 */
public final class EquipmentDropRules {
    private EquipmentDropRules() {}

    /** {@code Mob} 기본 {@code armorDropChances}/{@code handDropChances} = 0.085f. */
    public static final float NATURAL_DROP_CHANCE = 0.085f;
    /** 약탈(looting) 레벨당 드랍 확률 가산분. 바닐라 판정은 {@code roll - looting*0.01f < chance} 다. */
    public static final float LOOTING_BONUS_PER_LEVEL = 0.01f;

    /**
     * 장비 한 칸이 떨어지는가. 바닐라와 같이 플레이어에게 맞아 죽은 개체
     * ({@code recentlyHit})에만 적용한다.
     *
     * @param roll         {@code random.nextFloat()} 한 번
     * @param lootingLevel 처치 무기의 약탈 레벨(WebCraft 는 아직 약탈 인챈트가 없어 항상 0)
     */
    public static boolean drops(float roll, int lootingLevel) {
        return roll - lootingLevel * LOOTING_BONUS_PER_LEVEL < NATURAL_DROP_CHANCE;
    }

    /**
     * 손상 상태로 떨어지는 장비의 남은 내구도. 바닐라는
     * {@code setDamageValue(max - nextInt(1 + nextInt(max(max - 3, 1))))} 이므로
     * 남은 내구가 곧 {@code nextInt(1 + nextInt(max(max - 3, 1)))} 이다.
     * WebCraft 인벤토리는 내구 0 인 도구를 표현할 수 없어 최소 1로 올린다(divergence).
     */
    public static int droppedDurability(int maxDurability, MobRandom rng) {
        if (maxDurability <= 1) return Math.max(1, maxDurability);
        int span = Math.max(maxDurability - 3, 1);
        int remaining = rng.nextInt(1 + rng.nextInt(span));
        return Math.max(1, Math.min(maxDurability, remaining));
    }
}
