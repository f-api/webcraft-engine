package com.gameexpert.engine.mob;

/**
 * [ENCHANT-WIDE] 몹 종류별 바닐라 {@code KNOCKBACK_RESISTANCE} 기본값의 단일 표. 근접 넉백과 밀어내기
 * 화살({@code AbstractArrow.doKnockback}) 모두 이 값에 몸통 방어구 몫을 더해 {@code [0, 1]} 로 자른 값을
 * 쓴다. 근거는 핀 26.3 jar 의 {@code createAttributes} 바이트코드다: IronGolem·Warden {@code dconst_1},
 * Ravager {@code 0.75d}, AbstractNautilus(좀비 앵무조개 포함) {@code 0.3f}. 정적판 짝은
 * {@code StandaloneMobRuntime.standaloneMobBaseKnockbackResistance} 이며 두 표는 테스트가 대조한다.
 * 이 표에 없는 종류는 기본값 0 이다. 기본값 위에 {@link Mob#knockbackResistance()} 가 몸통 방어구·
 * 네더라이트 방어구 조각·좀비 계열 스폰 무작위 보너스·유황 큐브 아키타입 수정치를 ADD_VALUE 로 더한다.
 */
public final class MobKnockbackResistance {
    private MobKnockbackResistance() {}

    /** 바닐라 {@code Attributes.KNOCKBACK_RESISTANCE} 의 상한. */
    public static final double MAX = 1.0;
    /**
     * [SULFUR-KB] 바닐라 {@code Attributes.KNOCKBACK_RESISTANCE} 의 하한. 핀 26.3 은
     * {@code RangedAttribute("attribute.name.knockback_resistance", 0, -2.0, 1.0)} 이라 음수가 된다
     * (유황 큐브 탄성·끈적 아키타입 −2 → 넉백 세기 × 3).
     */
    public static final double MIN = -2.0;
    /**
     * [ZOMBIE-KB] {@code Zombie.handleAttributes}: {@code KNOCKBACK_RESISTANCE} 에
     * {@code random.nextDouble() · 0.05000000074505806} 을 {@code RANDOM_SPAWN_BONUS_ID} ADD_VALUE 로 건다.
     */
    public static final double ZOMBIE_RANDOM_SPAWN_BONUS_SCALE = 0.05000000074505806;
    /** 좀비 보너스 굴림 해시의 소금("ZKR1"). 창 무장 굴림({@code SpearRules.spearSpawnRoll})과 갈라 둔다. */
    public static final int ZOMBIE_RANDOM_SPAWN_BONUS_SALT = 0x5a4b5231;
    /** {@code ArmorMaterials.NETHERITE} 의 조각당 {@code knockbackResistance} 0.1f(ADD_VALUE). */
    public static final double NETHERITE_ARMOR_PIECE = (double) 0.1f;

    public static double base(MobType type) {
        return switch (type) {
            case IRON_GOLEM, WARDEN -> 1.0;
            case RAVAGER -> 0.75;
            case NAUTILUS, ZOMBIE_NAUTILUS -> (double) 0.3f;
            default -> 0.0;
        };
    }

    /** 기본값 + 수정치 몫, 바닐라 속성 범위 {@code [-2, 1]} 로 자른다. */
    public static double total(MobType type, double equipment) {
        return Math.max(MIN, Math.min(MAX, base(type) + equipment));
    }

    /** {@code Zombie} 를 상속하는 종류(좀비·새끼 좀비·허스크·드라운드·좀비 주민·좀비 피글린, 복고 좀비 피그맨). */
    public static boolean isZombieFamily(MobType type) {
        return type == MobType.ZOMBIE || type == MobType.BABY_ZOMBIE || type == MobType.HUSK
                || type == MobType.DROWNED || type == MobType.ZOMBIE_VILLAGER
                || type == MobType.ZOMBIFIED_PIGLIN || type == MobType.ZOMBIE_PIGMAN;
    }

    /**
     * [ZOMBIE-KB] 좀비 계열의 스폰 무작위 보너스 {@code [0, 0.05)}. 바닐라는 스폰 순간 몹 난수로
     * 한 번 굴려 속성 수정치로 저장하지만, 이 저장소는 기존 스폰 난수 수열을 밀지 않고 두 권위가 같은
     * 값을 내도록 <b>몹 id 해시</b>의 24비트 균등 소수로 굴린다({@code SpearRules.spearArmed} 선례). id 가
     * 영속되므로 값도 재시작 뒤 그대로다. 정적판 {@code standaloneZombieRandomSpawnBonus} 와 비트 단위로 같다.
     */
    public static double zombieRandomSpawnBonus(MobType type, long mobId) {
        if (!isZombieFamily(type)) return 0.0;
        int bits = ((int) mobId ^ (int) (mobId >>> 32)) ^ ZOMBIE_RANDOM_SPAWN_BONUS_SALT;
        bits ^= bits >>> 16;
        bits *= 0x7feb352d;
        bits ^= bits >>> 15;
        bits *= 0x846ca68b;
        bits ^= bits >>> 16;
        return ((bits >>> 8) / 16777216.0) * ZOMBIE_RANDOM_SPAWN_BONUS_SCALE;
    }

    /** 몹이 입은 네더라이트 방어구 조각 몫(조각당 0.1f). */
    public static double armorPieces(short[] armor) {
        double total = 0.0;
        for (short piece : armor) {
            if (piece >= com.gameexpert.engine.inventory.PlayerInventory.NETHERITE_HELMET
                    && piece <= com.gameexpert.engine.inventory.PlayerInventory.NETHERITE_BOOTS) {
                total += NETHERITE_ARMOR_PIECE;
            }
        }
        return total;
    }
}
