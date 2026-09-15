package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * <b>유혹(temptation) 계약의 종 비의존 정본.</b> 손에 든 아이템 때문에 몹이 플레이어를 따라오는
 * 규칙을 한 곳에 둔다 — 바닐라 {@code TemptGoal} 에 해당하는 축이다.
 *
 * <p><b>이 저장소에서 유혹 계약을 갖는 첫 종은 노틸러스다.</b> 그전까지 몹 AI 가 참조하는
 * 플레이어 사실({@link PlayerSnapshot})에는 <b>손에 든 아이템이 실려 있지 않았고</b>, 그래서
 * 유혹은 종별 코드가 아니라 <b>공용 사실 축</b>을 새로 여는 일이었다
 * ({@code docs/research/mc-nautilus-1-21-11.md} §8-1 이 그 판단을 적는다).
 *
 * <h2>계약</h2>
 * <ol>
 *   <li>권위는 {@link PlayerSnapshot#heldItemType()} 하나만 읽는다 — 인벤토리 전체가 아니라
 *       <b>선택 슬롯의 아이템 타입</b>이고, 어댑터가 그 자리에서 파생시킨다(금 방어구·썩은
 *       가죽 세트 사실과 같은 자리).</li>
 *   <li>{@link #tempts(MobType, int)} 가 "이 종이 이 아이템에 유혹되는가" 의 <b>유일한</b>
 *       술어다. 종이 늘어도 호출부는 늘지 않는다.</li>
 *   <li>거리 관문은 {@link #temptationRadius(MobType)} 이고 <b>구(sphere)</b>다
 *       ({@link #withinTemptationRange}). 바닐라 {@code TemptGoal} 도 구 거리로 본다.</li>
 *   <li><b>난수를 하나도 쓰지 않는다.</b> 유혹은 순수 술어라 이 축이 열려도 어떤 종의 난수
 *       수열도 밀리지 않는다.</li>
 * </ol>
 *
 * <h2>첫 소비자 — 노틸러스 [B]</h2>
 * [B] minecraft.wiki «Nautilus» · «Zombie Nautilus»: "follow players holding any fish or
 * bucket of fish within a 10-block radius". 두 종이 <b>같은 문장</b>으로 적혀 있어
 * {@code NautilusFamilyMob} 한 몸통과 같은 분업을 여기서도 쓴다 — 종이 갈리는 자리가 없다.
 *
 * <p>"any fish or bucket of fish" 의 집합은 {@link #isFishOrFishBucket(int)} 이 소유하고,
 * 이는 {@code MobSystem} 의 노틸러스 먹이 집합과 <b>같은 여덟 ID</b> 다(대구·연어 생/구운 ·
 * 열대어 · 복어 · 그 넷의 양동이). 같은 집합을 두 곳이 각자 열거하면 한쪽만 갱신됐을 때
 * "먹이는 되는데 유혹은 안 되는" 조용한 갈림이 생기므로, 먹이 쪽이 이 술어를 위임한다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneTemptationRules.ts} 이고,
 * 두 사본의 상수·판정 동일성은 {@code StandaloneTemptationRules.test.ts} 가 이 파일 원문을
 * 읽어 강제한다.
 */
public final class TemptationRules {

    private TemptationRules() {
    }

    /**
     * 노틸러스 계열의 유혹 반경(블록). [B] «Nautilus»: "within a 10-block radius".
     *
     * <p>10 TPS 환산이 필요 없는 <b>거리</b> 값이라 바닐라 숫자를 그대로 옮긴다.
     */
    public static final double NAUTILUS_TEMPTATION_RADIUS = 10.0;

    /** 유혹 계약이 없는 종의 반경. 0 이면 어떤 거리에서도 관문이 열리지 않는다. */
    public static final double NO_TEMPTATION = 0.0;

    /**
     * 이 종의 유혹 반경(블록). 유혹 계약이 없는 종은 {@link #NO_TEMPTATION} 이다.
     *
     * <p>종이 늘어나면 이 switch 에 줄을 더한다 — 반경을 호출부에 흩어 두지 않는 것이 이
     * 계약의 핵심이다.
     */
    public static double temptationRadius(MobType type) {
        return switch (type) {
            case NAUTILUS, ZOMBIE_NAUTILUS -> NAUTILUS_TEMPTATION_RADIUS;
            default -> NO_TEMPTATION;
        };
    }

    /** 이 종이 유혹 계약을 갖는가. */
    public static boolean temptable(MobType type) {
        return temptationRadius(type) > NO_TEMPTATION;
    }

    /**
     * 이 종이 이 손 아이템에 유혹되는가. 아이템이 비었으면(0) 언제나 거짓이다.
     *
     * <p>노틸러스 계열은 [B] 그대로 <b>아무 생선/생선 양동이</b>다. 종이 늘면 여기에 갈래를
     * 더하고, 반경과 마찬가지로 호출부는 늘지 않는다.
     */
    public static boolean tempts(MobType type, int heldItemType) {
        if (!temptable(type)) return false;
        return switch (type) {
            case NAUTILUS, ZOMBIE_NAUTILUS -> isFishOrFishBucket(heldItemType);
            default -> false;
        };
    }

    /**
     * "아무 생선 또는 생선 양동이" 인가 — [B] 의 "any fish or bucket of fish" 집합이다.
     * 대구·연어는 <b>날것과 구운 것 둘 다</b> 생선이다(바닐라 {@code #minecraft:fishes} 태그).
     *
     * <p>이 술어가 노틸러스 먹이 집합의 정본이기도 하다 — {@code MobSystem} 의 먹이 판정이
     * 이것을 위임하므로 "먹이는 되는데 유혹은 안 되는" 갈림이 구조적으로 생기지 않는다.
     */
    public static boolean isFishOrFishBucket(int itemType) {
        return itemType == PlayerInventory.COD_RAW
                || itemType == PlayerInventory.COD_COOKED
                || itemType == PlayerInventory.SALMON_RAW
                || itemType == PlayerInventory.SALMON_COOKED
                || itemType == PlayerInventory.TROPICAL_FISH
                || itemType == PlayerInventory.PUFFERFISH
                || isFishBucket(itemType);
    }

    /** 생선 양동이 넷. 소비 시 빈 양동이가 아니라 <b>물 양동이</b>를 돌려주는 갈래다. */
    public static boolean isFishBucket(int itemType) {
        return itemType == PlayerInventory.COD_BUCKET
                || itemType == PlayerInventory.SALMON_BUCKET
                || itemType == PlayerInventory.TROPICAL_FISH_BUCKET
                || itemType == PlayerInventory.PUFFERFISH_BUCKET;
    }

    /**
     * 유혹 거리 관문. 구 거리이며 좌표 규약은 §11.1(발밑 중심)이라 몹·플레이어 좌표를 그대로
     * 뺀다. 반경이 {@link #NO_TEMPTATION} 인 종은 언제나 거짓이다.
     */
    public static boolean withinTemptationRange(MobType type,
            double mobX, double mobY, double mobZ,
            double playerX, double playerY, double playerZ) {
        double radius = temptationRadius(type);
        if (radius <= NO_TEMPTATION) return false;
        double dx = playerX - mobX;
        double dy = playerY - mobY;
        double dz = playerZ - mobZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * 이 플레이어가 지금 이 몹을 유혹하고 있는가 — 종 관문 · 손 아이템 · 거리를 <b>한 번에</b>
     * 본다. 죽은 플레이어는 유혹하지 않는다(추적 대상 제외 규약과 같은 자리).
     */
    public static boolean tempting(MobType type, PlayerSnapshot player,
            double mobX, double mobY, double mobZ) {
        if (player == null || !player.alive()) return false;
        if (!tempts(type, player.heldItemType())
                && !tempts(type, player.offhandItemType())) return false;
        return withinTemptationRange(type, mobX, mobY, mobZ, player.x(), player.y(), player.z());
    }
}
