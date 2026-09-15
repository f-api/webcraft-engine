package com.gameexpert.engine.mob;

import java.util.EnumSet;
import java.util.Set;

/**
 * [UTILITY] 바닐라 개체 태그 중 이 저장소가 판정에 쓰는 둘. 고정 26.3-snapshot-7 jar
 * {@code data/minecraft/tags/entity_type/skeletons.json} · {@code zombies.json} · {@code undead.json}
 * 그대로이며, 이 저장소에 없는 위더·위더 스켈레톤·조글린은 빠진다. 정적판
 * {@code StandaloneMobTags.ts} 가 같은 집합이다.
 */
public final class MobTags {

    /** {@code #minecraft:skeletons}(크리퍼 음반 드랍의 killer 조건). */
    private static final Set<MobType> SKELETONS = EnumSet.of(
            MobType.SKELETON, MobType.STRAY, MobType.SKELETON_HORSE, MobType.BOGGED,
            MobType.PARCHED);

    /**
     * {@code #minecraft:zombies}. 이 저장소의 아기 좀비(BABY_ZOMBIE)는 바닐라 좀비의 아기 개체다.
     * 좀비 피그맨 · 좀비 동물(곰·늑대·소 등)은 WebCraft 고유 좀비 계열이라 언데드로 둔다.
     */
    private static final Set<MobType> ZOMBIES = EnumSet.of(
            MobType.ZOMBIE, MobType.BABY_ZOMBIE, MobType.ZOMBIE_VILLAGER, MobType.ZOMBIFIED_PIGLIN,
            MobType.DROWNED, MobType.HUSK, MobType.ZOMBIE_HORSE, MobType.CAMEL_HUSK,
            MobType.ZOMBIE_NAUTILUS, MobType.ZOMBIE_PIGMAN, MobType.ZOMBIE_BEAR,
            MobType.ZOMBIE_WOLF, MobType.ZOMBIE_COW, MobType.ZOMBIE_PIG, MobType.ZOMBIE_SHEEP,
            MobType.ZOMBIE_GOAT, MobType.ZOMBIE_FOX, MobType.ZOMBIE_CHICKEN);

    private MobTags() {
    }

    public static boolean isSkeleton(MobType type) {
        return type != null && SKELETONS.contains(type);
    }

    /** {@code #minecraft:undead} = skeletons + zombies + 팬텀(위더는 없다). */
    public static boolean isUndead(MobType type) {
        return type != null && (SKELETONS.contains(type) || ZOMBIES.contains(type)
                || type == MobType.PHANTOM);
    }
}
