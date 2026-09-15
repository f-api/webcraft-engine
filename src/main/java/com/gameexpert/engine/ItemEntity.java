package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.ItemComponentCodec;

/**
 * 서버 권위 드랍 아이템 엔티티(S2a). 채굴 드랍·몹 사망 드랍·크리퍼 폭발 드랍이 이 엔티티로 스폰되고,
 * 중력·지면 안착·병합·근접 획득·수명을 {@link ItemEntitySystem} 이 매 틱 처리한다.
 *
 * <p>좌표/속도 단위는 블록/틱. position 은 아이템의 중심 근사(점 취급). age 는 스폰 후 경과 틱 수로,
 * 근접 획득(10틱 이후)·수명(6000틱=10분) 판정에 쓴다.
 */
final class ItemEntity {
    long id;
    short itemType;
    int count;
    // 내구 아이템은 남은 내구도, 일반 아이템은 0. 프로토콜에서는 일반 아이템을 null로 직렬화한다.
    int durability;
    // [SURV-X] 스택별 인챈트 압축 마스크. 마스크가 다른 드랍은 서로 병합되지 않는다.
    long enchantments;
    // 채워진 지도만 가지는 월드 지도 ID. 다른 드랍은 0.
    int mapId;
    // [SHULKER-CONTENTS] 셜커 상자 드랍만 가지는 27칸 참조 ID. 다른 드랍은 0.
    // 27칸을 물고 있는 드랍은 스택 1 이라 병합 후보에서 자동으로 빠진다(stackMax 1).
    int shulkerId;
    String bucketMobData;
    String itemComponentData;
    double x, y, z;
    double vx, vy, vz;
    // Q 투척은 바닐라 20 TPS 속도 단위를 유지하고 서버 틱마다 두 물리 스텝을 수행한다.
    boolean playerThrown;
    int age;
    // 플레이어 투척은 2초 동안 누구도 줍거나 다른 스택에 병합할 수 없다(매 틱 1 감소).
    int pickupDelay;
    // 좋아하는 플레이어에게 돌려줬지만 인벤토리에 못 들어간 스택은 같은 알레이가 다시
    // 주워 무한 왕복하지 않는다. 다른 플레이어/알레이의 정상 획득에는 영향을 주지 않는다.
    long excludedAllayId;

    // 마지막으로 브로드캐스트한 위치와 수량(itemUpdates 변경분 판정용).
    double lastX, lastY, lastZ;
    int lastCount;

    ItemEntity(short itemType, int count, double x, double y, double z,
               double vx, double vy, double vz) {
        this(itemType, count, PlayerInventory.initialDurability(itemType), x, y, z, vx, vy, vz);
    }

    ItemEntity(short itemType, int count, int durability, double x, double y, double z,
               double vx, double vy, double vz) {
        this(itemType, count, durability, 0, x, y, z, vx, vy, vz);
    }

    ItemEntity(short itemType, int count, int durability, int mapId,
               double x, double y, double z, double vx, double vy, double vz) {
        this(itemType, count, durability, mapId, 0, x, y, z, vx, vy, vz);
    }

    ItemEntity(short itemType, int count, int durability, int mapId, int shulkerId,
               double x, double y, double z, double vx, double vy, double vz) {
        this(itemType, count, durability, mapId, shulkerId, null, null,
                x, y, z, vx, vy, vz);
    }

    ItemEntity(short itemType, int count, int durability, int mapId, int shulkerId,
               String bucketMobData, String itemComponentData, double x, double y, double z,
               double vx, double vy, double vz) {
        if (!PlayerInventory.isValidMapIdentity(itemType, mapId)) {
            throw new IllegalArgumentException("invalid dropped map identity");
        }
        if (!PlayerInventory.isValidShulkerIdentity(itemType, shulkerId)) {
            throw new IllegalArgumentException("invalid dropped shulker identity");
        }
        if (!com.gameexpert.engine.mob.BucketMobPayloadCodec
                .validForItem(itemType, bucketMobData)) {
            throw new IllegalArgumentException("invalid dropped bucket mob payload");
        }
        ItemComponentCodec.decode(itemType, itemComponentData);
        this.shulkerId = shulkerId;
        this.bucketMobData = bucketMobData;
        this.itemComponentData = itemComponentData;
        this.itemType = itemType;
        this.count = count;
        this.durability = PlayerInventory.isDurable(itemType) ? durability : 0;
        this.mapId = mapId;
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.lastX = x; this.lastY = y; this.lastZ = z;
        this.lastCount = count;
    }
}
