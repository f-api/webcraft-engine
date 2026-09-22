package com.gameexpert.engine;

import com.gameexpert.engine.blocks.CandleRules;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import com.gameexpert.engine.blocks.P1Rules;
import com.gameexpert.engine.blocks.P2Rules;
import com.gameexpert.engine.blocks.P3Rules;
import com.gameexpert.engine.blocks.P4Rules;
import com.gameexpert.engine.blocks.P5Rules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.crop.CropRules;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.MobEvent;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.service.GroundPickupAudit;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages.InventoryUpdate;
import com.gameexpert.ws.dto.WsMessages.ItemEntityDto;
import com.gameexpert.ws.dto.WsMessages.ItemPickup;
import com.gameexpert.ws.dto.WsMessages.ItemPos;
import com.gameexpert.ws.dto.WsMessages.ItemRemove;
import com.gameexpert.ws.dto.WsMessages.ItemSpawn;
import com.gameexpert.ws.dto.WsMessages.ItemUpdates;

/**
 * 서버 권위 드랍 아이템 엔티티 시스템(월드당 1개, 틱 스레드 전용). 틱 순서 ③.6(몹 뒤·환경 앞)에서
 * {@link #tick} 이 호출된다: 스폰 정산 → 물리(중력·지면 안착·수평 마찰) → 열 소실 → 병합 → 근접 획득 → 수명 소멸 →
 * 변경분 브로드캐스트.
 *
 * <p>드랍 소스는 세 곳: 채굴 성공({@link WorldTickLoop#applyEdit}), 몹 사망({@link MobSystem} — 동물 드랍),
 * 크리퍼 폭발 파괴 블록(1/power, 일반 크리퍼는 1/3 확률). 플레이어 채굴만 {@link #spawnMinedBlockDrop}에서 자갈의 부싯돌 확률을
 * 판정하고, 폭발 등은 결정적인 {@link #spawnBlockDrop}을 사용한다. 모두 tick(③.6)에서 정산·방송된다.
 */
final class ItemEntitySystem {

    /** 중력(블록/틱). CONTRACT §10.2-S2a. 10TPS 실시간에서 공중 지연 없이 즉시 안착하도록 상향. */
    static final double GRAVITY = 0.08;
    static final double TERMINAL = 3.0;
    /** Vanilla Player.tick: player AABB inflated by (1.0, 0.5, 1.0). */
    static final double PLAYER_PICKUP_HORIZONTAL = 1.0 + 0.3 + 0.125;
    static final double PLAYER_PICKUP_BELOW = 0.5 + 0.25;
    static final double PLAYER_PICKUP_ABOVE = 1.8 + 0.5;
    static final double MOB_PICKUP_RANGE = 1.75;
    /** 몹 장비 탐색은 0.5초마다만 수행한다(46몹 상한에서 매 틱 전수 검색 방지). */
    static final int MOB_PICKUP_INTERVAL = 5;
    /** 채굴·전리품 드랍 10 game ticks(0.5초)를 WebCraft 10 TPS의 5틱으로 환산. */
    static final int PICKUP_MIN_AGE = 5;
    /** 플레이어 투척 40 game ticks(2초)를 WebCraft 10 TPS의 20틱으로 환산. */
    static final int THROWN_PICKUP_DELAY = 20;
    static final int DESPAWN_AGE = 6000;        // 10분(6000틱) 수명
    static final double MERGE_RANGE = 1.0;      // 같은 종류 병합 반경
    /** 병합용 공간 그리드 셀 크기. MERGE_RANGE(1.0) 를 인접 셀로 온전히 덮도록 2 로 둔다. */
    static final double MERGE_CELL = 2.0;
    /** 이 개수 미만이면 그리드 준비 비용이 아까우니 기존 O(n²) 경로 사용(결과는 두 경로 완전 동일). */
    static final int MERGE_GRID_THRESHOLD = 32;
    private static final double POS_EPS = 1e-3;
    /** Minecraft 1.21.4 ItemEntity 물리 스텝(20 TPS)을 10 TPS 서버 틱마다 두 번 실행한다. */
    static final int THROWN_PHYSICS_STEPS = 2;
    static final double MINECRAFT_ITEM_GRAVITY = 0.04;
    static final double MINECRAFT_ITEM_AIR_DRAG = 0.98;
    static final double MINECRAFT_DEFAULT_BLOCK_FRICTION = 0.6;
    private static final double MINECRAFT_TICKS_PER_SECOND = 20.0;
    private static final double SERVER_TICKS_PER_SECOND = 10.0;
    /** 스폰 위치 XZ 지터 폭(±SPAWN_JITTER/2). MC처럼 블록 중심 근처에 흩뿌려 겹침을 줄인다. */
    private static final double SPAWN_JITTER = 0.4;
    private static final double THROWN_POSITION_JITTER = 0.05;
    private static final double DEATH_POSITION_JITTER = 0.2;
    /** 정확한 바닐라 난수식은 이번 검색에서 미확인. WebCraft 사망 산포 매핑. */
    static final double MOB_DROP_HORIZONTAL_SPEED = 0.1;
    static final double MOB_DROP_UPWARD_SPEED = 0.2;

    /** 실제 블록과 상태를 함께 읽는 충돌 조회. 단순 테스트 지형은 기본 상태 0을 쓴다. */
    interface CollisionLookup {
        int block(int x, int y, int z);
        default int state(int x, int y, int z, int block) { return 0; }
        default void boxes(int x,int y,int z,BuildingBlockRules.CollisionBoxVisitor visitor) {
            int id=block(x,y,z); BuildingBlockRules.forCollisionBoxes(id,BuildingBlockRules.collisionIgnoresState(id)?0:state(x,y,z,id),x,z,visitor);
        }
    }

    static CollisionLookup collisionLookup(WorldRuntime runtime) {
        return new CollisionLookup() {
            public int block(int x, int y, int z) {
                return WorldTickLoop.residentBlockType(runtime.accessor(), x, y, z);
            }
            public void boxes(int x,int y,int z,BuildingBlockRules.CollisionBoxVisitor visitor) {
                runtime.tickLoop().forRedstoneCollisionBoxes(x,y,z,visitor);
            }
            public int state(int x, int y, int z, int block) {
                return runtime.blockState(x, y, z, block);
            }
        };
    }

    /** Q 투척의 지상 활주에 쓰는 블록별 Minecraft friction 조회(테스트 주입 가능). */
    interface FrictionFn {
        double friction(int x, int y, int z);
    }

    /** WebCraft 자연 화재는 별도 블록 ID가 아니라 좌표 상태이므로 조회 함수를 주입합니다. */
    interface FireLookup {
        boolean burning(int x, int y, int z);
    }

    private final WorldRuntime rt;
    private final Random rng;
    private final ArrayDeque<RandomDraw> replayRandomDraws = new ArrayDeque<>();
    private ExplosionDropPlan activeExplosionDropPlan;
    private ExplosionGroundReservation activeExplosionGroundReservation;
    private final WorldRuntime.GroundEntityIdAllocator entityIds;
    private final Fluids.BlockLookup fluidBlocks;
    private FireLookup fireLookup = (x, y, z) -> false;

    private final List<ItemEntity> entities = new ArrayList<>();
    private final List<ItemEntity> pending = new ArrayList<>(); // 이번 틱 이전에 예약된 신규 스폰
    private final Set<Long> pendingPlayerSettlements = new HashSet<>();
    private List<ItemEntity> readyPlayerPickups = List.of();
    private final double[] fluidFlow = new double[3]; // 틱 스레드에서 엔티티마다 재사용
    /** Set by identity/count lifecycle changes; motion/age are checkpointed at a bounded cadence. */
    private long persistenceRevision;
    private long persistedRevision;

    // WS 스레드가 welcome 구성 시 읽는 현재 스냅샷(불변).
    private volatile List<ItemEntityDto> welcomeSnapshot = List.of();

    private enum RandomKind { DOUBLE, FLOAT, INT }

    private record RandomDraw(RandomKind kind, int bound, long bits) { }

    private double nextDouble() {
        RandomDraw draw = nextRandomDraw(RandomKind.DOUBLE, 0);
        return Double.longBitsToDouble(draw.bits());
    }

    private float nextFloat() {
        RandomDraw draw = nextRandomDraw(RandomKind.FLOAT, 0);
        return Float.intBitsToFloat((int) draw.bits());
    }

    private int nextInt(int bound) {
        RandomDraw draw = nextRandomDraw(RandomKind.INT, bound);
        return (int) draw.bits();
    }

    private RandomDraw nextRandomDraw(RandomKind kind, int bound) {
        if (activeExplosionDropPlan != null) {
            throw new IllegalStateException("폭발 드랍 계획이 난수 순서를 예약하고 있습니다");
        }
        if (!replayRandomDraws.isEmpty()) {
            RandomDraw replay = replayRandomDraws.removeFirst();
            if (replay.kind() != kind || replay.bound() != bound) {
                throw new IllegalStateException("아이템 난수 재생 순서가 달라졌습니다");
            }
            return replay;
        }
        return switch (kind) {
            case DOUBLE -> new RandomDraw(kind, 0,
                    Double.doubleToRawLongBits(rng.nextDouble()));
            case FLOAT -> new RandomDraw(kind, 0,
                    Integer.toUnsignedLong(Float.floatToRawIntBits(rng.nextFloat())));
            case INT -> new RandomDraw(kind, bound, rng.nextInt(bound));
        };
    }

    private final class RandomTransaction {
        private final List<RandomDraw> draws = new ArrayList<>();

        double nextDouble() {
            RandomDraw draw = nextRandomDraw(RandomKind.DOUBLE, 0);
            draws.add(draw);
            return Double.longBitsToDouble(draw.bits());
        }

        int nextInt(int bound) {
            RandomDraw draw = nextRandomDraw(RandomKind.INT, bound);
            draws.add(draw);
            return (int) draw.bits();
        }

        void restore() {
            for (int index = draws.size() - 1; index >= 0; index--) {
                replayRandomDraws.addFirst(draws.get(index));
            }
        }
    }

    ItemEntitySystem(WorldRuntime rt) {
        this(rt, new Random(), rt == null
                ? new WorldRuntime.GroundEntityIdAllocator()
                : rt.groundEntityIdAllocator());
    }

    /** 테스트에서만 난수 시드를 고정하기 위한 패키지 생성자. */
    ItemEntitySystem(WorldRuntime rt, Random rng) {
        this(rt, rng, rt == null
                ? new WorldRuntime.GroundEntityIdAllocator()
                : rt.groundEntityIdAllocator());
    }

    ItemEntitySystem(WorldRuntime rt, WorldRuntime.GroundEntityIdAllocator entityIds) {
        this(rt, new Random(), entityIds);
    }

    ItemEntitySystem(WorldRuntime rt, Random rng,
            WorldRuntime.GroundEntityIdAllocator entityIds) {
        if (entityIds == null) throw new IllegalArgumentException("ground entity allocator is required");
        if (rt != null && entityIds != rt.groundEntityIdAllocator()) {
            throw new IllegalArgumentException("world ground entity allocator cannot be bypassed");
        }
        this.rt = rt;
        this.rng = rng;
        this.entityIds = entityIds;
        this.fluidBlocks = rt == null ? null
                : (x, y, z) -> WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
    }

    List<ItemEntityDto> welcomeSnapshot() {
        return welcomeSnapshot;
    }

    /** Restores the last complete durable tick before the runtime is exposed to a joining client. */
    void restore(List<GroundItemSnapshot> snapshots) {
        List<ItemEntity> restored = prepareRestore(snapshots);
        entityIds.restoreIdentities(restored.stream().map(item -> item.id).toList());
        installPreparedRestore(restored);
    }

    /** Validates and detaches a restore without mutating either the system or the shared allocator. */
    List<ItemEntity> prepareRestore(List<GroundItemSnapshot> snapshots) {
        if (!entities.isEmpty() || !pending.isEmpty()) {
            throw new IllegalStateException("ground items can only be restored into an empty runtime");
        }
        if (snapshots == null) {
            throw new IllegalArgumentException("persisted ground items are required");
        }
        List<ItemEntity> restored = new ArrayList<>(snapshots.size());
        Set<Long> restoredIds = new HashSet<>(snapshots.size());
        for (GroundItemSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("persisted ground item is required");
            }
            if (snapshot.entityId() <= 0
                    || snapshot.entityId() > WorldRuntime.GroundEntityIdAllocator.MAX_ALLOCATABLE_ID
                    || snapshot.count() <= 0 || snapshot.age() < 0
                    || snapshot.pickupDelay() < 0) {
                throw new IllegalArgumentException("invalid persisted ground item");
            }
            if (!restoredIds.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate persisted ground item identity");
            }
            ItemEntity item = itemFromSnapshot(snapshot);
            restored.add(item);
        }
        return List.copyOf(restored);
    }

    /** Installs a batch already validated together with the world's XP restore. */
    void installPreparedRestore(List<ItemEntity> restored) {
        entities.addAll(restored);
        publishSnapshot();
        persistenceRevision = 0;
        persistedRevision = 0;
    }

    List<GroundItemSnapshot> persistenceSnapshot() {
        return entities.stream().filter(item -> item.count > 0)
                .map(item -> new GroundItemSnapshot(item.id, item.itemType, item.count,
                        item.durability, item.enchantments, item.mapId, item.shulkerId,
                        item.bucketMobData, item.itemComponentData,
                        item.x, item.y, item.z, item.vx, item.vy, item.vz,
                        item.playerThrown, item.age, item.pickupDelay, item.excludedAllayId))
                .toList();
    }

    synchronized long persistenceRevision() {
        return persistenceRevision;
    }

    synchronized boolean persistenceDirty() {
        return persistenceRevision != persistedRevision;
    }

    synchronized void acknowledgePersistence(long revision) {
        persistedRevision = Math.max(persistedRevision, Math.min(revision, persistenceRevision));
    }

    void reserveEntityIdThrough(long entityId) {
        requireNoActiveExplosionReservation();
        entityIds.reserveThrough(entityId);
    }

    void setFireLookup(FireLookup fireLookup) {
        this.fireLookup = fireLookup;
    }

    // ── 스폰 예약(어느 단계에서든 호출 가능, 정산은 tick 에서) ──

    /** 임의 아이템 드랍 스폰(몹 사망 드랍 등). 미세 수평 확산 + 최소 상향 팝(제자리 안착). */
    void spawnDrop(short itemType, int count, double x, double y, double z) {
        spawnDrop(itemType, count, PlayerInventory.initialDurability(itemType), x, y, z);
    }

    /** 상자·인벤토리에서 나온 기존 아이템은 생성 당시 값이 아니라 전달받은 내구도를 그대로 운반합니다. */
    void spawnDrop(short itemType, int count, int durability, double x, double y, double z) {
        spawnDrop(itemType, count, durability, EnchantmentRules.EMPTY_ENCHANTMENTS, x, y, z);
    }

    /** [SURV-X] 인챈트된 스택이 드랍될 때 마스크를 잃지 않게 함께 운반합니다. */
    void spawnDrop(short itemType, int count, int durability, long enchantments,
            double x, double y, double z) {
        spawnDrop(itemType, count, durability, enchantments, 0, x, y, z);
    }

    void spawnDrop(short itemType, int count, int durability, long enchantments, int mapId,
            double x, double y, double z) {
        spawnDrop(itemType, count, durability, enchantments, mapId, 0, x, y, z);
    }

    /**
     * [SHULKER-CONTENTS] 27칸을 물고 있는 셜커 상자 드랍. 채굴 경로가 블록 엔티티의 27칸을
     * 참조 저장소로 회수한 <b>같은 트랜잭션</b>에서 발급받은 ID 를 그대로 실어 보낸다.
     */
    void spawnDrop(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, double x, double y, double z) {
        spawnDrop(itemType, count, durability, enchantments, mapId, shulkerId, null,
                null, x, y, z);
    }

    void spawnDrop(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData,
            double x, double y, double z) {
        if (itemType == 0 || count <= 0) {
            return;
        }
        if (!PlayerInventory.isValidMapIdentity(itemType, mapId)) {
            throw new IllegalArgumentException("드랍 지도 ID가 올바르지 않습니다.");
        }
        if (!PlayerInventory.isValidShulkerIdentity(itemType, shulkerId)) {
            throw new IllegalArgumentException("드랍 셜커 참조 ID가 올바르지 않습니다.");
        }
        if (!com.gameexpert.engine.mob.BucketMobPayloadCodec.validForItem(
                itemType, bucketMobData)) {
            throw new IllegalArgumentException("드랍 양동이 생물 데이터가 올바르지 않습니다.");
        }
        com.gameexpert.engine.inventory.ItemComponentCodec.decode(itemType, itemComponentData);
        if (PlayerInventory.isDurable(itemType) && durability <= 0) {
            throw new IllegalArgumentException("내구 아이템 드랍은 1 이상의 durability가 필요합니다.");
        }
        // 스폰 위치를 블록 중심 근처(±0.2)로 살짝 흩뿌려 같은 칸 연속 드랍의 겹침을 줄인다.
        double sx = x + (nextDouble() - 0.5) * SPAWN_JITTER;
        double sz = z + (nextDouble() - 0.5) * SPAWN_JITTER;
        // 확산·팝을 최소화해 파괴한 자리에 거의 그대로 떨어지게(공중 지연·튀김 방지).
        double vx = (nextDouble() - 0.5) * 0.04;
        double vz = (nextDouble() - 0.5) * 0.04;
        double vy = 0.04;
        ItemEntity spawned = new ItemEntity(
                itemType, count, durability, mapId, shulkerId, bucketMobData, itemComponentData,
                sx, y, sz, vx, vy, vz);
        spawned.enchantments = enchantments;
        queue(spawned);
    }

    /**
     * 알레이가 플레이어에게 돌려줬지만 인벤토리에 들어가지 못한 스택. 일반 드랍과 같은 수명·
     * 획득 규칙을 쓰되, 방금 던진 알레이만 다시 후보로 삼지 않아 전달 왕복을 막는다.
     */
    void spawnAllayReturnDrop(long allayId, short itemType, int count, int durability,
            double x, double y, double z) {
        int before = pending.size();
        spawnDrop(itemType, count, durability, x, y, z);
        if (pending.size() == before) return;
        pending.getLast().excludedAllayId = allayId;
    }

    /**
     * 한 알레이의 pickup 요청을 live 아이템 엔티티와 다시 맞춘 뒤 한 번만 확정한다. 알레이
     * 상태 승인이 먼저 성공해야 count/remove 및 피드백이 발생하므로 낡은 이벤트는 아이템을
     * 지우지 않는다.
     *
     * @return 실제로 옮긴 개수, 요청이 낡았거나 대상이 달라졌으면 0
     */
    int confirmAllayPickup(MobRuntime mobs, long allayId, MobEvent.AllayPickupItem request) {
        for (int index = 0; index < entities.size(); index++) {
            ItemEntity item = entities.get(index);
            if (item.id != request.itemEntityId()) continue;
            if (pendingPlayerSettlements.contains(item.id)
                    || !allayPickupEligible(item, allayId)
                    || item.itemType != request.itemType()
                    || item.durability != request.durability()) return 0;
            int moved = Math.min(item.count, request.count());
            if (moved <= 0 || !mobs.confirmAllayPickup(allayId, item.id, moved)) return 0;

            item.count -= moved;
            markPersistenceDirty();
            broadcast(new ItemPickup(item.x, item.y, item.z));
            if (item.count == 0) {
                entities.remove(index);
                broadcast(new ItemRemove(List.of(item.id), "pickup", "mob:" + allayId));
            } else {
                broadcastItemUpdate(item);
            }
            publishSnapshot();
            return moved;
        }
        return 0;
    }

    static boolean allayPickupEligible(ItemEntity item, long allayId) {
        return allayPickupSearchEligible(item)
                && item.excludedAllayId != allayId;
    }

    static boolean allayPickupSearchEligible(ItemEntity item) {
        return item.count > 0 && item.age >= PICKUP_MIN_AGE && item.pickupDelay <= 0
                // 현재 Allay hand/persistence 형식이 보존하는 정체성은 종류+내구도다. 더 풍부한
                // 컴포넌트 스택을 주우면 지도 ID/인챈트가 사라지므로 후보 자체에서 제외한다.
                // [SHULKER-CONTENTS] 27칸 참조도 같은 이유로 후보에서 뺀다 — 알레이 손이
                // 참조를 잃으면 그 27칸이 어느 상자에도 붙지 않는 고아 행이 된다.
                && item.enchantments == EnchantmentRules.EMPTY_ENCHANTMENTS && item.mapId == 0
                && item.shulkerId == 0 && item.bucketMobData == null
                && item.itemComponentData == null;
    }

    /** Collected items entering the liked player's inventory use the ordinary pickup cue once. */
    void broadcastAllayDelivery(double x, double y, double z) {
        broadcast(new ItemPickup(x, y, z));
    }

    /** 작물 age와 단일 정본 규칙으로 수확 드랍을 판정해 같은 아이템은 한 스택으로 방출한다. */
    void spawnCropDrops(CropRules.Rule crop, int age, double x, double y, double z) {
        // [PITCHER] The state carries the half: only the lower half has loot (blocks/pitcher_crop.json).
        if (crop.cropBlock() == Blocks.PITCHER_CROP) {
            if (PitcherRules.isUpper(age)) return;
            age = PitcherRules.age(age);
        }
        if (crop.stem()) {
            spawnDrop(crop.seedItem(), crop.stemSeedDropCount(age, rng::nextInt), x, y, z);
            return;
        }
        if (age < crop.maxAge()) {
            spawnDrop(crop.immatureDrop(), 1, x, y, z);
            return;
        }

        int extraCount = crop.matureExtraDropCount(rng::nextInt);
        int matureCount = crop.matureDropCount(rng::nextInt);
        if (crop.seedItem() == crop.matureDrop()) {
            spawnDrop(crop.matureDrop(), extraCount + matureCount, x, y, z);
        } else {
            spawnDrop(crop.seedItem(), extraCount, x, y, z);
            spawnDrop(crop.matureDrop(), matureCount, x, y, z);
        }
        // [CROP-BERRY] 바닐라 전리품표의 마지막 풀. 감자만 여기서 독 감자를 2% 로 하나 더
        // 떨군다 — 주 수확물 난수를 전부 뽑은 뒤라야 정적판과 난수열이 같다.
        short bonus = CropRules.matureBonusDrop(crop);
        if (bonus != 0) {
            spawnDrop(bonus, CropRules.matureBonusDropCount(crop, rng::nextInt), x, y, z);
        }
    }

    /** 몹이 장비로 주울 수 있는 스택 모양인가(성분 검증과 칸 판정은 {@code Mob} 이 한다). */
    static boolean mobEquipmentCanCarry(ItemEntity e) {
        // [MOB-EQUIP] 몹 장비 칸은 인챈트·성분 문자열을 플레이어 스택과 같은 표현으로 담는다. 지도·셜커·
        // 양동이 몹 정체성은 장비 품목에 없으므로 그런 스택만 제외한다.
        return e.mapId == 0 && e.shulkerId == 0 && e.bucketMobData == null;
    }

    /**
     * [TRIDENT] 종결한 <b>플레이어 투척</b> 삼지창의 회수물. 바닐라의 던진 삼지창은 맞은 자리에
     * 꽂혀 그대로 주울 수 있으므로 산포도 팝도 주지 않고 종결 좌표에 그대로 둔다 — 난수를 한 번도
     * 쓰지 않으므로 정적판({@code reserveTridentRecovery})과 좌표가 정확히 같다.
     *
     * <p>드라운드가 던진 삼지창은 이 경로에 오지 않는다({@code ProjectileSim.recoverableDurability}
     * 가 0 이라 호출 자체가 없다).
     */
    void spawnTridentRecovery(int durability, double x, double y, double z) {
        spawnTridentRecovery(durability, EnchantmentRules.EMPTY_ENCHANTMENTS, null, x, y, z);
    }

    /**
     * [ENCHANT-WIDE] 던진 삼지창의 워드 0 인챈트와 성분 문자열(확장 인챈트·이름 등)까지 되돌린다. 예전에는
     * 내구만 돌아와 인챈트가 사라졌다.
     */
    void spawnTridentRecovery(int durability, long enchantments, String itemComponentData,
            double x, double y, double z) {
        if (durability <= 0) {
            throw new IllegalArgumentException("삼지창 회수는 1 이상의 durability가 필요합니다.");
        }
        ItemEntity recovered = new ItemEntity(PlayerInventory.TRIDENT, 1, durability, 0,
                x, y, z, 0, 0, 0);
        recovered.enchantments = enchantments;
        recovered.itemComponentData = itemComponentData;
        queue(recovered);
    }

    /** 몹 사망 드랍 전용: 드랍표/수량은 유지하고 초기 속도와 수평 위치를 살짝 흩뿌린다. */
    void spawnMobDrop(short itemType, int count, double x, double y, double z) {
        if (itemType == 0 || count <= 0) return;
        double vx = (nextDouble() * 2.0 - 1.0) * MOB_DROP_HORIZONTAL_SPEED;
        double vz = (nextDouble() * 2.0 - 1.0) * MOB_DROP_HORIZONTAL_SPEED;
        double sx = x + (nextDouble() - 0.5) * DEATH_POSITION_JITTER;
        double sz = z + (nextDouble() - 0.5) * DEATH_POSITION_JITTER;
        queue(new ItemEntity(itemType, count, sx, y, sz, vx, MOB_DROP_UPWARD_SPEED, vz));
    }

    long reserveSettlementEntityId() {
        requireNoActiveExplosionReservation();
        return entityIds.allocate();
    }

    GroundItemSnapshot settlementDropSnapshot(long entityId, short itemType,
            double x, double y, double z) {
        return settlementDropSnapshot(entityId, itemType, 1, x, y, z);
    }

    GroundItemSnapshot settlementDropSnapshot(long entityId, short itemType, int count,
            double x, double y, double z) {
        if (count <= 0) throw new IllegalArgumentException("settlement drop count must be positive");
        return new GroundItemSnapshot(entityId, itemType, count, 0, 0, 0, 0,
                null, null,
                x + settlementScatter(entityId, 0) * SPAWN_JITTER, y,
                z + settlementScatter(entityId, 1) * SPAWN_JITTER,
                settlementScatter(entityId, 2) * 0.04, MOB_DROP_UPWARD_SPEED,
                settlementScatter(entityId, 3) * 0.04, false, 0, PICKUP_MIN_AGE, 0);
    }

    GroundItemSnapshot settlementDropSnapshot(long entityId,
            PlayerInventory.StackSnapshot stack, double x, double y, double z) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("settlement drop stack is required");
        }
        return new GroundItemSnapshot(entityId, stack.itemType(), stack.count(),
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData(),
                x + settlementScatter(entityId, 0) * SPAWN_JITTER, y,
                z + settlementScatter(entityId, 1) * SPAWN_JITTER,
                settlementScatter(entityId, 2) * 0.04, MOB_DROP_UPWARD_SPEED,
                settlementScatter(entityId, 3) * 0.04, false, 0, PICKUP_MIN_AGE, 0);
    }

    /** 재시도에서 같은 영수증 ID를 재구성해도 위치·속도가 정확히 같아야 한다. */
    private static double settlementScatter(long entityId, int component) {
        int bits = (int) entityId + (int) (entityId >>> 32) * 0x632be5ab
                + 0x9e3779b9 * (component + 1);
        bits = (bits ^ (bits >>> 16)) * 0x7feb352d;
        bits = (bits ^ (bits >>> 15)) * 0x846ca68b;
        bits ^= bits >>> 16;
        return Integer.toUnsignedLong(bits) / 4294967296.0 - 0.5;
    }

    GroundItemSnapshot settlementThrownDropSnapshot(long entityId,
            PlayerInventory.DroppedStack stack, double x, double y, double z,
            boolean crouching, float yaw, float pitch) {
        if (stack == null) throw new IllegalArgumentException("dropped stack is required");
        double cp = Math.cos(pitch);
        double speed = 0.3;
        float spreadAngle = nextFloat() * (float) (Math.PI * 2.0);
        float spreadMagnitude = 0.02f * nextFloat();
        double vx = -Math.sin(yaw) * cp * speed + Math.cos(spreadAngle) * spreadMagnitude;
        double vy = Math.sin(pitch) * speed + 0.1 + (nextFloat() - nextFloat()) * 0.1;
        double vz = -Math.cos(yaw) * cp * speed + Math.sin(spreadAngle) * spreadMagnitude;
        // 시선/속도 산포 난수 뒤에 위치 산포를 추가해 기존 발사 궤적을 보존한다.
        double sx = x + (nextDouble() - 0.5) * THROWN_POSITION_JITTER;
        double sz = z + (nextDouble() - 0.5) * THROWN_POSITION_JITTER;
        return new GroundItemSnapshot(entityId, stack.itemType(), stack.count(),
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData(), sx,
                y + PlayerInteractionRules.eyeHeight(crouching) - 0.3, sz,
                vx, vy, vz, true, 0, THROWN_PICKUP_DELAY, 0);
    }

    GroundItemSnapshot settlementDeathDropSnapshot(long entityId,
            PlayerInventory.DroppedStack stack, double x, double y, double z) {
        if (stack == null) throw new IllegalArgumentException("death stack is required");
        double vx = (nextDouble() * 2.0 - 1.0) * MOB_DROP_HORIZONTAL_SPEED;
        double vz = (nextDouble() * 2.0 - 1.0) * MOB_DROP_HORIZONTAL_SPEED;
        double sx = x + (nextDouble() - 0.5) * DEATH_POSITION_JITTER;
        double sz = z + (nextDouble() - 0.5) * DEATH_POSITION_JITTER;
        return new GroundItemSnapshot(entityId, stack.itemType(), stack.count(),
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData(), sx, y, sz,
                vx, MOB_DROP_UPWARD_SPEED, vz,
                false, 0, PICKUP_MIN_AGE, 0);
    }

    /** Installs an already durable deterministic item identity; replay is idempotent. */
    void commitSettlementDrop(GroundItemSnapshot snapshot) {
        requireNoActiveExplosionReservation();
        if (snapshot == null) {
            throw new IllegalArgumentException("settlement drop is required");
        }
        ItemEntity item = itemFromSnapshot(snapshot);
        if (rt != null) rt.requireGroundItemIdentityAvailable(snapshot.entityId());
        for (ItemEntity installed : entities) {
            if (installed.id != snapshot.entityId()) continue;
            if (!exactSnapshot(installed, snapshot)) {
                throw new IllegalStateException("ground item identity collision");
            }
            return;
        }
        for (ItemEntity reserved : pending) {
            if (reserved.id == snapshot.entityId()) {
                throw new IllegalStateException("ground item identity is already reserved");
            }
        }
        entityIds.reserveThrough(item.id);
        entities.add(item);
        markPersistenceDirty();
        broadcast(new ItemSpawn(item.id, item.itemType, item.mapId, item.x, item.y, item.z,
                renderVelocity(item, item.vx), renderVelocity(item, item.vy),
                renderVelocity(item, item.vz), enchantmentWire(item), item.count).withPotDecorations(
                        com.gameexpert.engine.inventory.ItemComponentCodec.decode(item.itemType, item.itemComponentData).potDecorations()));
        publishSnapshot();
    }

    /** 플레이어 사망 드랍도 몹 드랍과 같은 수평 ±0.1, 수직 +0.2 산포를 사용합니다. */
    void spawnDeathDrop(short itemType, int count, double x, double y, double z) {
        spawnDeathDrop(itemType, count, PlayerInventory.initialDurability(itemType), x, y, z);
    }

    /** 플레이어/몹 장비 사망 드랍의 남은 내구도를 보존합니다. */
    void spawnDeathDrop(short itemType, int count, int durability, double x, double y, double z) {
        spawnDeathDrop(itemType, count, durability,
                EnchantmentRules.EMPTY_ENCHANTMENTS, x, y, z);
    }

    /** [SURV-X] 사망 드랍도 인챈트 마스크를 보존합니다. */
    void spawnDeathDrop(short itemType, int count, int durability, long enchantments,
            double x, double y, double z) {
        spawnDeathDrop(itemType, count, durability, enchantments, 0, x, y, z);
    }

    void spawnDeathDrop(short itemType, int count, int durability, long enchantments, int mapId,
            double x, double y, double z) {
        spawnDeathDrop(itemType, count, durability, enchantments, mapId, 0, x, y, z);
    }

    /** 사망한 플레이어의 셜커 상자도 27칸 내용 참조를 잃지 않고 땅 엔티티로 옮깁니다. */
    void spawnDeathDrop(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, double x, double y, double z) {
        spawnDeathDrop(itemType, count, durability, enchantments, mapId, shulkerId,
                null, null, x, y, z);
    }

    void spawnDeathDrop(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData,
            double x, double y, double z) {
        // 아이템 체력 계층이 없으므로 불/용암 접촉 틱에 소실한다. 스폰과 기존 엔티티가 같은 단일 규칙을 쓴다.
        if (rt != null && !survivesHeatAt(itemType, x, y, z)) {
            return;
        }
        if (itemType == 0 || count <= 0) return;
        if (!PlayerInventory.isValidMapIdentity(itemType, mapId)) {
            throw new IllegalArgumentException("사망 드랍 지도 ID가 올바르지 않습니다.");
        }
        if (!PlayerInventory.isValidShulkerIdentity(itemType, shulkerId)) {
            throw new IllegalArgumentException("사망 드랍 셜커 ID가 올바르지 않습니다.");
        }
        com.gameexpert.engine.inventory.ItemComponentCodec.decode(itemType, itemComponentData);
        if (PlayerInventory.isDurable(itemType) && durability <= 0) {
            throw new IllegalArgumentException("내구 아이템 사망 드랍은 1 이상의 durability가 필요합니다.");
        }
        double vx = (nextDouble() * 2.0 - 1.0) * MOB_DROP_HORIZONTAL_SPEED;
        double vz = (nextDouble() * 2.0 - 1.0) * MOB_DROP_HORIZONTAL_SPEED;
        double sx = x + (nextDouble() - 0.5) * DEATH_POSITION_JITTER;
        double sz = z + (nextDouble() - 0.5) * DEATH_POSITION_JITTER;
        ItemEntity dropped =
                new ItemEntity(itemType, count, durability, mapId, shulkerId,
                        bucketMobData, itemComponentData,
                        sx, y, sz, vx, MOB_DROP_UPWARD_SPEED, vz);
        dropped.enchantments = enchantments;
        queue(dropped);
    }

    static boolean deathDropSurvives(int blockType) {
        return deathDropSurvives(blockType, false);
    }

    /** 사망 드랍 스폰과 매 틱 기존 엔티티 검사에서 공유하는 유일한 열 소실 판정. */
    static boolean deathDropSurvives(int blockType, boolean burning) {
        return Fluids.typeOf(blockType) != Fluids.LAVA && !burning;
    }

    static boolean deathDropSurvives(short itemType, int blockType, boolean burning) {
        return isFireResistantItem(itemType) || deathDropSurvives(blockType, burning);
    }

    static boolean isFireResistantItem(short itemType) {
        int id = Short.toUnsignedInt(itemType);
        return id == Blocks.ANCIENT_DEBRIS || id == Blocks.NETHERITE_BLOCK
                || id == Blocks.NETHERITE_SCRAP || id == Blocks.NETHERITE_INGOT
                || id >= Blocks.NETHERITE_PICKAXE && id <= Blocks.NETHERITE_BOOTS
                || id == Blocks.NETHERITE_SPEAR || id == Blocks.NETHERITE_NAUTILUS_ARMOR
                || id == Blocks.NETHERITE_HORSE_ARMOR;
    }

    /** Q 투척 전용. 기존 눈높이·시선 속도·난수 산포를 유지하고 수평 위치만 살짝 흩뿌린다. */
    void spawnThrownDrop(short itemType, int count, int durability,
            double x, double y, double z, boolean crouching, float yaw, float pitch) {
        spawnThrownDrop(itemType, count, durability, EnchantmentRules.EMPTY_ENCHANTMENTS,
                x, y, z, crouching, yaw, pitch);
    }

    /** [SURV-X] Q 투척도 인챈트 마스크를 보존합니다. */
    void spawnThrownDrop(short itemType, int count, int durability, long enchantments,
            double x, double y, double z, boolean crouching, float yaw, float pitch) {
        spawnThrownDrop(itemType, count, durability, enchantments, 0,
                x, y, z, crouching, yaw, pitch);
    }

    void spawnThrownDrop(short itemType, int count, int durability, long enchantments, int mapId,
            double x, double y, double z, boolean crouching, float yaw, float pitch) {
        spawnThrownDrop(itemType, count, durability, enchantments, mapId, 0,
                x, y, z, crouching, yaw, pitch);
    }

    /**
     * [SHULKER-CONTENTS] 내용을 지닌 셜커 상자를 Q 로 던져도 27칸 참조는 아이템 엔티티를
     * 따라간다. 참조를 떨어뜨리면 그 27칸은 어떤 경로로도 다시 열 수 없다.
     */
    void spawnThrownDrop(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, double x, double y, double z, boolean crouching, float yaw,
            float pitch) {
        spawnThrownDrop(itemType, count, durability, enchantments, mapId, shulkerId,
                null, null, x, y, z, crouching, yaw, pitch);
    }

    void spawnThrownDrop(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData,
            double x, double y, double z,
            boolean crouching, float yaw, float pitch) {
        if (itemType == 0 || count <= 0) return;
        if (!PlayerInventory.isValidMapIdentity(itemType, mapId)) {
            throw new IllegalArgumentException("투척 지도 ID가 올바르지 않습니다.");
        }
        if (!PlayerInventory.isValidShulkerIdentity(itemType, shulkerId)) {
            throw new IllegalArgumentException("투척 셜커 참조 ID가 올바르지 않습니다.");
        }
        com.gameexpert.engine.inventory.ItemComponentCodec.decode(itemType, itemComponentData);
        if (PlayerInventory.isDurable(itemType) && durability <= 0) {
            throw new IllegalArgumentException("내구 아이템 투척은 1 이상의 durability가 필요합니다.");
        }
        double cp = Math.cos(pitch);
        double speed = 0.3;
        float spreadAngle = nextFloat() * (float) (Math.PI * 2.0);
        float spreadMagnitude = 0.02f * nextFloat();
        double vx = -Math.sin(yaw) * cp * speed + Math.cos(spreadAngle) * spreadMagnitude;
        double vy = Math.sin(pitch) * speed + 0.1 + (nextFloat() - nextFloat()) * 0.1;
        double vz = -Math.cos(yaw) * cp * speed + Math.sin(spreadAngle) * spreadMagnitude;
        double sx = x + (nextDouble() - 0.5) * THROWN_POSITION_JITTER;
        double sz = z + (nextDouble() - 0.5) * THROWN_POSITION_JITTER;
        ItemEntity e = new ItemEntity(itemType, count, durability, mapId, shulkerId,
                bucketMobData, itemComponentData,
                sx, y + PlayerInteractionRules.eyeHeight(crouching) - 0.3, sz, vx, vy, vz);
        // 바닐라 PickupDelay처럼 모두에게 적용한다. 물리는 age 규칙대로 다음 틱부터 정상 진행하고,
        // 유예 중 병합도 막아 오래된 스택에 흡수되어 즉시 획득되는 우회를 차단한다.
        e.pickupDelay = THROWN_PICKUP_DELAY;
        e.playerThrown = true;
        e.enchantments = enchantments;
        queue(e);
    }

    /**
     * [CONTAINER-MENUS] {@code DefaultDispenseItemBehavior.spawnItem}: one exact stack (every
     * identity component) at the dispense position with the vanilla motion in blocks per game
     * tick ({@link com.gameexpert.engine.dispenser.DispenserRules#spawnItem}). The item rides the
     * same 20 TPS physics as a thrown item and, like {@code new ItemEntity(level, x, y, z, stack)},
     * carries no pickup delay beyond the common minimum age.
     */
    void spawnDispensed(short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData,
            double x, double y, double z, double vx, double vy, double vz) {
        if (itemType == 0 || count <= 0) return;
        if (!PlayerInventory.isValidMapIdentity(itemType, mapId)) {
            throw new IllegalArgumentException("dispensed map id is invalid");
        }
        if (!PlayerInventory.isValidShulkerIdentity(itemType, shulkerId)) {
            throw new IllegalArgumentException("dispensed shulker id is invalid");
        }
        com.gameexpert.engine.inventory.ItemComponentCodec.decode(itemType, itemComponentData);
        ItemEntity e = new ItemEntity(itemType, count, durability, mapId, shulkerId,
                bucketMobData, itemComponentData, x, y, z, vx, vy, vz);
        e.playerThrown = true;
        e.enchantments = enchantments;
        queue(e);
    }

    /**
     * 낚시 전리품 전용. 찌 자리에서 낚싯줄을 따라 플레이어 쪽으로 날아오게 한다. 속도는
     * FishingRules.catchVelocity 가 정하며 난수를 쓰지 않으므로 양판 결과가 같다.
     * 던진 아이템과 같은 20 TPS 물리를 타도록 playerThrown 으로 스폰하되, 내가 낚은
     * 것이므로 투척 유예(THROWN_PICKUP_DELAY)는 주지 않고 공통 PICKUP_MIN_AGE만 따른다.
     */
    void spawnFishingCatch(short itemType, int count,
            double bobberX, double bobberY, double bobberZ,
            double playerX, double playerY, double playerZ) {
        spawnFishingCatch(itemType, count, PlayerInventory.initialDurability(itemType),
                com.gameexpert.engine.enchant.WideEnchantments.EMPTY,
                bobberX, bobberY, bobberZ, playerX, playerY, playerZ);
    }

    /**
     * 낚아 올린 전리품을 내구·인챈트까지 확정해 스폰한다. 바닐라 낚시 전리품표의
     * {@code set_damage}·{@code enchant_with_levels} 결과가 여기로 들어온다.
     */
    void spawnFishingCatch(short itemType, int count, int durability,
            com.gameexpert.engine.enchant.WideEnchantments enchantments,
            double bobberX, double bobberY, double bobberZ,
            double playerX, double playerY, double playerZ) {
        if (itemType == 0 || count <= 0) return;
        double[] velocity = FishingRules.catchVelocity(bobberX, bobberY, bobberZ,
                playerX, playerY, playerZ);
        ItemEntity e = new ItemEntity(itemType, count, durability,
                bobberX, bobberY, bobberZ, velocity[0], velocity[1], velocity[2]);
        e.enchantments = enchantments.word0();
        // [ENCHANT-WIDE] ID 16 이상은 성분 문자열(WCIC4)이 싣는다. 없으면 null 그대로다.
        e.itemComponentData = com.gameexpert.engine.inventory.ItemComponentCodec.withEnchantments(
                itemType, null, enchantments);
        e.playerThrown = true;
        queue(e);
    }

    /** 폭발 등 일반 블록 파괴의 결정적 드랍 결과를 스폰(자갈은 자갈, 무드랍이면 아무것도 안 함). */
    void spawnBlockDrop(short brokenBlockType, double x, double y, double z) {
        short drop = InventoryRules.dropFor(brokenBlockType);
        if (drop != 0) {
            spawnDrop(drop, 1, x, y, z);
        }
    }

    private void queue(ItemEntity item) {
        requireNoActiveExplosionReservation();
        if (item.id == 0) item.id = entityIds.allocate();
        pending.add(item);
        markPersistenceDirty();
    }

    private static ItemEntity itemFromSnapshot(GroundItemSnapshot snapshot) {
        ItemEntity item = new ItemEntity(snapshot.itemType(), snapshot.count(), snapshot.durability(),
                snapshot.mapId(), snapshot.shulkerId(), snapshot.bucketMobData(),
                snapshot.itemComponentData(), snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
        item.enchantments = snapshot.enchantments();
        item.id = snapshot.entityId();
        item.playerThrown = snapshot.playerThrown();
        item.age = snapshot.age();
        item.pickupDelay = snapshot.pickupDelay();
        item.excludedAllayId = snapshot.excludedAllayId();
        return item;
    }

    private synchronized void markPersistenceDirty() {
        persistenceRevision++;
    }

    /**
     * 폭발 생존 판정을 통과한 블록의 도구 없는 loot table 결과를 스폰한다.
     * 폭발 호출부가 이미 1/power decay를 판정하므로 여기서는 블록별 종류와 개수만 계산한다.
     */
    void spawnExplodedBlockDrop(short brokenBlockType, double x, double y, double z) {
        spawnExplodedBlockDrop(brokenBlockType, 0, x, y, z);
    }

    void spawnExplodedBlockDrop(short brokenBlockType, int brokenState, double x, double y, double z) {
        PlayerInventory.DroppedStack exact = CandleRules.isCandle(brokenBlockType)
                ? simpleStack(brokenBlockType, CandleRules.count(brokenState)) : null;
        ExplosionDropPlan plan = planExplodedBlockDrops(List.of(
                new ExplodedBlockDropRequest(brokenBlockType, exact, x, y, z)));
        commitExplodedBlockDrops(plan);
    }

    /** One destroyed block plus an optional exact carried stack (used by placed shulkers). */
    record ExplodedBlockDropRequest(short brokenBlockType,
            PlayerInventory.DroppedStack exactDrop, double x, double y, double z) {
        ExplodedBlockDropRequest {
            if (brokenBlockType == 0 || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z)) {
                throw new IllegalArgumentException("폭발 블록 드랍 요청이 올바르지 않습니다");
            }
            if (exactDrop != null && exactDrop.itemType() != brokenBlockType) {
                throw new IllegalArgumentException("폭발 블록과 정확 드랍의 종류가 다릅니다");
            }
        }
    }

    /**
     * Reserves one explosion's durable identity window. When the current command needs a receipt,
     * its identity is the first slot; item IDs then XP IDs occupy the same allocator window in
     * declaration order.
     */
    static final class ExplosionGroundReservation {
        private enum State { OPEN, COMMITTED, ROLLED_BACK }

        private final ItemEntitySystem itemOwner;
        private final XpOrbSystem xpOwner;
        private final WorldRuntime.GroundEntityIdAllocator entityIds;
        private final long settlementId;
        private final long expectedAllocatorId;
        private final long nextAllocatorIdAfter;
        private final long itemCount;
        private final long xpCount;
        private final ItemReservationView itemView;
        private final XpReservationView xpView;
        private ExplosionDropPlan itemPlan;
        private XpOrbSystem.ExplosionXpPlan xpPlan;
        private State state = State.OPEN;

        private ExplosionGroundReservation(ItemEntitySystem itemOwner, XpOrbSystem xpOwner,
                long settlementId, long expectedAllocatorId, long nextAllocatorIdAfter,
                long itemCount, long xpCount) {
            this.itemOwner = itemOwner;
            this.xpOwner = xpOwner;
            this.entityIds = itemOwner.entityIds;
            this.settlementId = settlementId;
            this.expectedAllocatorId = expectedAllocatorId;
            this.nextAllocatorIdAfter = nextAllocatorIdAfter;
            this.itemCount = itemCount;
            this.xpCount = xpCount;
            long firstItemId = Math.addExact(expectedAllocatorId, 1L);
            this.itemView = new ItemReservationView(this, firstItemId,
                    Math.addExact(firstItemId, itemCount));
            this.xpView = new XpReservationView(this,
                    Math.addExact(firstItemId, itemCount), nextAllocatorIdAfter);
        }

        private void attachPlans(ExplosionDropPlan itemPlan,
                XpOrbSystem.ExplosionXpPlan xpPlan) {
            if (itemPlan == null || xpPlan == null || this.itemPlan != null || this.xpPlan != null) {
                throw new IllegalStateException("폭발 unified plan이 이미 연결되었습니다");
            }
            this.itemPlan = itemPlan;
            this.xpPlan = xpPlan;
        }

        long settlementId() {
            return settlementId;
        }

        long expectedAllocatorId() {
            return expectedAllocatorId;
        }

        long expectedAllocatorNextId() {
            return expectedAllocatorId;
        }

        long nextAllocatorIdAfter() {
            return nextAllocatorIdAfter;
        }

        long itemCount() {
            return itemCount;
        }

        long xpCount() {
            return xpCount;
        }

        ItemReservationView itemView() {
            return itemView;
        }

        XpReservationView xpView() {
            return xpView;
        }

        ExplosionDropPlan itemPlan() {
            return itemPlan;
        }

        XpOrbSystem.ExplosionXpPlan xpPlan() {
            return xpPlan;
        }

        List<GroundItemSnapshot> itemSnapshots() {
            return itemPlan == null ? List.of() : itemPlan.snapshots();
        }

        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpSnapshots() {
            return xpPlan == null ? List.of() : xpPlan.snapshots();
        }

        boolean allocatorWindowIsOpen() {
            return state == State.OPEN && entityIds.nextId() == expectedAllocatorId
                    && entityIds.preflightBatch(Math.addExact(1L,
                            Math.addExact(itemCount, xpCount)))
                            == nextAllocatorIdAfter;
        }

        boolean preflight() {
            requireOpen();
            itemOwner.preflightExplodedBlockDrops(itemPlan);
            xpOwner.preflightExplodedXpPlan(xpPlan);
            requireCrossKindIdentityAvailability();
            return true;
        }

        boolean commit() {
            return commitAfterDurableCommit();
        }

        /** Called only after the immutable explosion/ground receipt has durably committed. */
        boolean commitAfterDurableCommit() {
            if (state != State.OPEN) {
                throw new IllegalStateException("종료된 폭발 unified reservation은 다시 확정할 수 없습니다");
            }
            requireOpen();
            List<ItemEntity> items = itemOwner.preflightExplodedBlockDrops(itemPlan);
            List<XpOrb> xp = xpOwner.preflightExplodedXpPlan(xpPlan);
            requireCrossKindIdentityAvailability();

            long total = Math.addExact(1L, Math.addExact(itemCount, xpCount));
            if (total > 0) {
                entityIds.commitBatch(expectedAllocatorId, nextAllocatorIdAfter);
            }
            itemOwner.installExplosionDropPlan(itemPlan, items);
            xpOwner.installExplosionXpPlan(xpPlan, xp);
            state = State.COMMITTED;
            itemOwner.activeExplosionGroundReservation = null;
            return true;
        }

        boolean rollback() {
            if (state != State.OPEN) {
                throw new IllegalStateException("종료된 폭발 unified reservation은 다시 되돌릴 수 없습니다");
            }
            requireOpen();
            itemOwner.rollbackExplosionDropPlan(itemPlan);
            xpOwner.rollbackExplosionXpPlan(xpPlan);
            state = State.ROLLED_BACK;
            itemOwner.activeExplosionGroundReservation = null;
            return true;
        }

        private void requireOpen() {
            if (state != State.OPEN || itemPlan == null || xpPlan == null
                    || itemOwner.activeExplosionGroundReservation != this
                    || itemOwner.activeExplosionDropPlan != itemPlan
                    || !xpOwner.explosionPlanIsActive(xpPlan)) {
                throw new IllegalStateException("폭발 unified reservation 소유권이 만료되었습니다");
            }
        }

        private void requireCrossKindIdentityAvailability() {
            Set<Long> ids = new HashSet<>();
            for (GroundItemSnapshot snapshot : itemPlan.snapshots()) {
                if (!ids.add(snapshot.entityId())
                        || xpOwner.containsIdentity(snapshot.entityId())) {
                    throw new IllegalStateException("폭발 ground identity가 중복되었습니다");
                }
            }
            for (com.gameexpert.ground.dto.GroundXpOrbSnapshot snapshot : xpPlan.snapshots()) {
                if (!ids.add(snapshot.entityId())
                        || itemOwner.containsIdentity(snapshot.entityId())) {
                    throw new IllegalStateException("폭발 ground identity가 중복되었습니다");
                }
            }
        }

        static final class ItemReservationView {
            private final ExplosionGroundReservation reservation;
            private final long firstId;
            private final long nextIdAfterBatch;

            private ItemReservationView(ExplosionGroundReservation reservation,
                    long firstId, long nextIdAfterBatch) {
                this.reservation = reservation;
                this.firstId = firstId;
                this.nextIdAfterBatch = nextIdAfterBatch;
            }

            private boolean belongsTo(ItemEntitySystem owner) {
                return reservation.itemOwner == owner && reservation.state == State.OPEN;
            }

            private boolean belongsTo(ExplosionGroundReservation expected,
                    ItemEntitySystem owner) {
                return reservation == expected && belongsTo(owner);
            }

            private ExplosionGroundReservation reservation() {
                return reservation;
            }

            long firstId() {
                return firstId;
            }

            long nextIdAfterBatch() {
                return nextIdAfterBatch;
            }

            long count() {
                return nextIdAfterBatch - firstId;
            }

            boolean allocatorWindowIsOpen() {
                return reservation.allocatorWindowIsOpen();
            }
        }

        /** Typed handoff consumed by XP planning; it cannot carry an independent allocator. */
        static final class XpReservationView {
            private final ExplosionGroundReservation reservation;
            private final long firstId;
            private final long nextIdAfterBatch;

            private XpReservationView(ExplosionGroundReservation reservation,
                    long firstId, long nextIdAfterBatch) {
                this.reservation = reservation;
                this.firstId = firstId;
                this.nextIdAfterBatch = nextIdAfterBatch;
            }

            boolean belongsTo(XpOrbSystem owner) {
                return reservation.xpOwner == owner && reservation.state == State.OPEN;
            }

            long firstId() {
                return firstId;
            }

            long nextIdAfterBatch() {
                return nextIdAfterBatch;
            }

            long count() {
                return nextIdAfterBatch - firstId;
            }

            boolean allocatorWindowIsOpen() {
                return reservation.allocatorWindowIsOpen();
            }
        }
    }

    ExplosionGroundReservation planExplosionGroundReservation(XpOrbSystem xpSystem,
            List<ExplodedBlockDropRequest> itemRequests,
            List<XpOrbSystem.ExplosionXpOrbRequest> xpRequests) {
        return planExplosionGroundReservation(xpSystem, 0L, itemRequests, xpRequests);
    }

    ExplosionGroundReservation planExplosionGroundReservation(long settlementId,
            XpOrbSystem xpSystem, List<ExplodedBlockDropRequest> itemRequests,
            List<XpOrbSystem.ExplosionXpOrbRequest> xpRequests) {
        return planExplosionGroundReservation(xpSystem, settlementId, itemRequests, xpRequests);
    }

    ExplosionGroundReservation planExplodedGroundReservation(XpOrbSystem xpSystem,
            long settlementId, List<ExplodedBlockDropRequest> itemRequests,
            List<XpOrbSystem.ExplosionXpOrbRequest> xpRequests) {
        return planExplosionGroundReservation(xpSystem, settlementId, itemRequests, xpRequests);
    }

    ExplosionGroundReservation planExplosionGroundReservation(XpOrbSystem xpSystem,
            long settlementId, List<ExplodedBlockDropRequest> itemRequests,
            List<XpOrbSystem.ExplosionXpOrbRequest> xpRequests) {
        if (xpSystem == null || !xpSystem.usesAllocator(entityIds)) {
            throw new IllegalArgumentException("폭발 item/XP 시스템은 같은 ground allocator를 사용해야 합니다");
        }
        if (activeExplosionDropPlan != null || activeExplosionGroundReservation != null
                || !xpSystem.explosionPlanningAvailable()) {
            throw new IllegalStateException("다른 폭발 unified plan이 아직 열려 있습니다");
        }
        List<ExplodedBlockDropRequest> immutableItems = immutableExplosionRequests(itemRequests);
        List<XpOrbSystem.ExplosionXpOrbRequest> immutableXp =
                xpSystem.immutableExplosionXpRequests(xpRequests);
        validateRequestedExplosionSettlementId(settlementId);
        long expectedAllocatorId = entityIds.nextId();
        long resolvedSettlementId = resolveExplosionSettlementId(
                settlementId, expectedAllocatorId);
        long xpCount = xpSystem.preflightExplosionXpCount(immutableXp);
        long maximumItems = maximumExplosionItemCount(immutableItems);
        // Conservative capacity preflight happens before either RNG transaction starts.
        entityIds.preflightBatch(Math.addExact(1L, Math.addExact(maximumItems, xpCount)));

        ExplosionStackPlan itemCandidate = planExplosionStacks(immutableItems);
        long itemCount = itemCandidate.stacks().size();
        long total = Math.addExact(1L, Math.addExact(itemCount, xpCount));
        long nextAllocatorIdAfter = entityIds.preflightBatch(total);
        long itemNextId = Math.addExact(Math.addExact(expectedAllocatorId, 1L), itemCount);
        if (itemNextId > nextAllocatorIdAfter) {
            throw new IllegalStateException("폭발 item/XP ID 범위가 겹쳤습니다");
        }
        ExplosionGroundReservation reservation = new ExplosionGroundReservation(this, xpSystem,
                resolvedSettlementId, expectedAllocatorId, nextAllocatorIdAfter,
                itemCount, xpCount);
        ExplosionDropPlan itemPlan = null;
        XpOrbSystem.ExplosionXpPlan xpPlan = null;
        try {
            itemPlan = createExplosionDropPlan(itemCandidate, reservation.itemView(), reservation);
            xpPlan = xpSystem.planExplodedXpOrbs(immutableXp, reservation.xpView());
            reservation.attachPlans(itemPlan, xpPlan);
            activeExplosionGroundReservation = reservation;
            return reservation;
        } catch (RuntimeException | Error failure) {
            if (xpPlan != null) xpSystem.rollbackExplosionXpPlan(xpPlan);
            if (itemPlan != null) rollbackExplosionDropPlan(itemPlan);
            throw failure;
        }
    }

    private static long maximumExplosionItemCount(List<ExplodedBlockDropRequest> requests) {
        long maximum = 0;
        for (ExplodedBlockDropRequest request : requests) {
            long perRequest = request.exactDrop() != null
                    || !InventoryRules.isWoodLeaves(request.brokenBlockType()) ? 1L : 3L;
            maximum = Math.addExact(maximum, perRequest);
        }
        return maximum;
    }

    private static long resolveExplosionSettlementId(long requested, long expectedAllocatorId) {
        if (expectedAllocatorId <= 0
                || expectedAllocatorId > WorldRuntime.GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            throw new IllegalStateException("폭발 settlement identity allocator가 소진되었습니다");
        }
        if (requested == 0L) return expectedAllocatorId;
        if (requested <= 0 || requested > WorldRuntime.GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            throw new IllegalArgumentException("폭발 settlement identity가 올바르지 않습니다");
        }
        if (requested != expectedAllocatorId) {
            throw new IllegalStateException("폭발 settlement identity가 allocator 예약과 다릅니다");
        }
        return requested;
    }

    private static void validateRequestedExplosionSettlementId(long requested) {
        if (requested < 0
                || requested > WorldRuntime.GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            throw new IllegalArgumentException("폭발 settlement identity가 올바르지 않습니다");
        }
    }

    boolean preflightExplosionGroundReservation(ExplosionGroundReservation reservation) {
        requireExplosionReservationOwner(reservation);
        return reservation.preflight();
    }

    boolean commitExplosionGroundReservation(ExplosionGroundReservation reservation) {
        requireExplosionReservationOwner(reservation);
        return reservation.commitAfterDurableCommit();
    }

    boolean rollbackExplosionGroundReservation(ExplosionGroundReservation reservation) {
        requireExplosionReservationOwner(reservation);
        return reservation.rollback();
    }

    private void requireExplosionReservationOwner(ExplosionGroundReservation reservation) {
        if (reservation == null || reservation.itemOwner != this) {
            throw new IllegalArgumentException("다른 아이템 시스템의 폭발 unified reservation입니다");
        }
    }

    /**
     * Freezes loot, full stack components, motion and one contiguous entity-ID window. Planning is
     * observational: it changes neither the allocator nor pending/live drops, and speculative RNG
     * draws are replayable until this exact owner token commits.
     */
    ExplosionDropPlan planExplodedBlockDrops(List<ExplodedBlockDropRequest> requests) {
        return planExplodedBlockDrops(requests, null, null);
    }

    /** Plans item output against an already assigned part of a unified explosion reservation. */
    ExplosionDropPlan planExplodedBlockDrops(List<ExplodedBlockDropRequest> requests,
            ExplosionGroundReservation.ItemReservationView assignedRange) {
        if (assignedRange == null) return planExplodedBlockDrops(requests, null, null);
        if (!assignedRange.belongsTo(this)) {
            throw new IllegalArgumentException("다른 아이템 시스템의 폭발 ID 범위입니다");
        }
        return planExplodedBlockDrops(requests, assignedRange, assignedRange.reservation());
    }

    private ExplosionDropPlan planExplodedBlockDrops(List<ExplodedBlockDropRequest> requests,
            ExplosionGroundReservation.ItemReservationView assignedRange,
            ExplosionGroundReservation reservation) {
        if (requests == null) {
            throw new IllegalArgumentException("폭발 드랍 요청 묶음이 없습니다");
        }
        if (activeExplosionDropPlan != null || activeExplosionGroundReservation != null) {
            throw new IllegalStateException("다른 폭발 드랍 계획이 아직 열려 있습니다");
        }
        List<ExplodedBlockDropRequest> immutableRequests = immutableExplosionRequests(requests);
        ExplosionStackPlan candidate = planExplosionStacks(immutableRequests);
        return createExplosionDropPlan(candidate, assignedRange, reservation);
    }

    private ExplosionStackPlan planExplosionStacks(
            List<ExplodedBlockDropRequest> immutableRequests) {
        RandomTransaction randomTransaction = new RandomTransaction();
        try {
            List<PlannedExplosionStack> stacks = new ArrayList<>();
            for (ExplodedBlockDropRequest request : immutableRequests) {
                if (request == null) {
                    throw new IllegalArgumentException("폭발 드랍 요청에 빈 행이 있습니다");
                }
                // The historical path consumes one table roll before every block family branch.
                double lootRoll = randomTransaction.nextDouble();
                if (request.exactDrop() != null) {
                    stacks.add(plannedExplosionStack(request.exactDrop(), request,
                            randomTransaction));
                } else if (InventoryRules.isWoodLeaves(request.brokenBlockType())) {
                    planExplodedLeafDrops(request, randomTransaction, stacks);
                } else {
                    PlayerInventory.DroppedStack drop = explodedLoot(
                            request.brokenBlockType(), lootRoll);
                    if (drop != null) {
                            stacks.add(plannedExplosionStack(drop, request, randomTransaction));
                    }
                }
            }
            List<RandomDraw> randomDraws = List.copyOf(randomTransaction.draws);
            randomTransaction.restore();
            return new ExplosionStackPlan(List.copyOf(stacks), randomDraws);
        } catch (RuntimeException | Error failure) {
            randomTransaction.restore();
            throw failure;
        }
    }

    private ExplosionDropPlan createExplosionDropPlan(ExplosionStackPlan candidate,
            ExplosionGroundReservation.ItemReservationView assignedRange,
            ExplosionGroundReservation reservation) {
        long firstId;
        long nextIdAfterBatch;
        if (assignedRange == null) {
            firstId = entityIds.nextId();
            nextIdAfterBatch = entityIds.preflightBatch(candidate.stacks().size());
        } else {
            if (reservation == null || !assignedRange.belongsTo(reservation, this)
                    || candidate.stacks().size() != assignedRange.count()) {
                throw new IllegalArgumentException("폭발 아이템 ID 범위와 계획 수량이 다릅니다");
            }
            firstId = assignedRange.firstId();
            nextIdAfterBatch = assignedRange.nextIdAfterBatch();
        }
        List<GroundItemSnapshot> snapshots = new ArrayList<>(candidate.stacks().size());
        long candidateId = firstId;
        for (PlannedExplosionStack stack : candidate.stacks()) {
            PlayerInventory.DroppedStack exact = stack.stack();
            snapshots.add(new GroundItemSnapshot(candidateId, exact.itemType(), exact.count(),
                    exact.durability(), exact.enchantments(), exact.mapId(), exact.shulkerId(),
                    exact.bucketMobData(), exact.itemComponentData(),
                    stack.x(), stack.y(), stack.z(), stack.vx(), stack.vy(), stack.vz(),
                    false, 0, PICKUP_MIN_AGE, 0));
            candidateId++;
        }
        if (candidateId != nextIdAfterBatch) {
            throw new IllegalStateException("폭발 드랍 ID 일괄 사전검증이 달라졌습니다");
        }
        ExplosionDropPlan plan = new ExplosionDropPlan(this, firstId, nextIdAfterBatch,
                List.copyOf(snapshots), candidate.randomDraws(), reservation);
        activeExplosionDropPlan = plan;
        return plan;
    }

    private static List<ExplodedBlockDropRequest> immutableExplosionRequests(
            List<ExplodedBlockDropRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("폭발 드랍 요청 묶음이 없습니다");
        }
        for (ExplodedBlockDropRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("폭발 드랍 요청에 빈 행이 있습니다");
            }
        }
        return List.copyOf(requests);
    }

    /** Installs every planned drop or none; replaying the same committed token is a no-op. */
    boolean commitExplodedBlockDrops(ExplosionDropPlan plan) {
        requireExplosionPlanOwner(plan);
        if (plan.state == ExplosionPlanState.COMMITTED) return false;
        if (plan.reservation != null) {
            throw new IllegalStateException("폭발 item 계획은 unified reservation으로만 확정할 수 있습니다");
        }
        List<ItemEntity> staged = preflightExplodedBlockDrops(plan);
        if (!staged.isEmpty()) {
            entityIds.commitBatch(plan.expectedFirstId, plan.nextIdAfterBatch);
        }
        installExplosionDropPlan(plan, staged);
        return true;
    }

    /** Rechecks the complete ID/RNG window and constructs all entities without live mutation. */
    List<ItemEntity> preflightExplodedBlockDrops(ExplosionDropPlan plan) {
        requireExplosionPlanOwner(plan);
        if (plan.state != ExplosionPlanState.OPEN || activeExplosionDropPlan != plan) {
            throw new IllegalStateException("폭발 드랍 계획 소유권이 만료되었습니다");
        }
        if (plan.reservation != null) {
            if (!plan.reservation.allocatorWindowIsOpen()) {
                throw new IllegalStateException("폭발 드랍 ID 예약이 오래되었습니다");
            }
        } else if (entityIds.nextId() != plan.expectedFirstId
                || entityIds.preflightBatch(plan.snapshots.size()) != plan.nextIdAfterBatch) {
            throw new IllegalStateException("폭발 드랍 ID 예약이 오래되었습니다");
        }
        Set<Long> ids = new HashSet<>();
        List<ItemEntity> staged = new ArrayList<>(plan.snapshots.size());
        for (GroundItemSnapshot snapshot : plan.snapshots) {
            if (!ids.add(snapshot.entityId())) {
                throw new IllegalStateException("폭발 드랍 계획에 중복 ID가 있습니다");
            }
            // 사전검증에서 settlement ID를 claim하면 allocator가 먼저 전진해 commitBatch가 실패한다.
            if (plan.reservation == null && rt != null
                    && rt.xpOrbSystem().containsIdentity(snapshot.entityId())) {
                throw new IllegalStateException("ground entity identity collision");
            }
            if (containsIdentity(snapshot.entityId())) {
                throw new IllegalStateException("폭발 드랍 ID가 이미 사용 중입니다");
            }
            staged.add(itemFromSnapshot(snapshot));
        }
        requireReservedRandomPrefix(plan.randomDraws);
        return List.copyOf(staged);
    }

    /** Releases a plan while leaving its speculative random sequence available to the next action. */
    boolean rollbackExplodedBlockDrops(ExplosionDropPlan plan) {
        requireExplosionPlanOwner(plan);
        if (plan.state == ExplosionPlanState.ROLLED_BACK) return false;
        if (plan.state != ExplosionPlanState.OPEN || activeExplosionDropPlan != plan) {
            throw new IllegalStateException("폭발 드랍 계획 소유권이 만료되었습니다");
        }
        if (plan.reservation != null) {
            throw new IllegalStateException("폭발 item 계획은 unified reservation으로만 되돌릴 수 있습니다");
        }
        rollbackExplosionDropPlan(plan);
        return true;
    }

    private void installExplosionDropPlan(ExplosionDropPlan plan, List<ItemEntity> staged) {
        if (!staged.isEmpty()) {
            pending.addAll(staged);
            for (int index = 0; index < staged.size(); index++) markPersistenceDirty();
        }
        consumeReservedRandomDraws(plan.randomDraws);
        plan.state = ExplosionPlanState.COMMITTED;
        activeExplosionDropPlan = null;
    }

    private void rollbackExplosionDropPlan(ExplosionDropPlan plan) {
        plan.state = ExplosionPlanState.ROLLED_BACK;
        activeExplosionDropPlan = null;
    }

    private void requireExplosionPlanOwner(ExplosionDropPlan plan) {
        if (plan == null || plan.owner != this) {
            throw new IllegalArgumentException("다른 아이템 시스템의 폭발 드랍 계획입니다");
        }
    }

    private void requireNoActiveExplosionReservation() {
        if (activeExplosionGroundReservation != null) {
            throw new IllegalStateException("폭발 unified reservation이 allocator를 예약하고 있습니다");
        }
    }

    private void requireReservedRandomPrefix(List<RandomDraw> reserved) {
        if (replayRandomDraws.size() < reserved.size()) {
            throw new IllegalStateException("폭발 드랍 난수 예약이 사라졌습니다");
        }
        Iterator<RandomDraw> actual = replayRandomDraws.iterator();
        for (RandomDraw expected : reserved) {
            if (!actual.next().equals(expected)) {
                throw new IllegalStateException("폭발 드랍 난수 예약이 오래되었습니다");
            }
        }
    }

    private void consumeReservedRandomDraws(List<RandomDraw> reserved) {
        requireReservedRandomPrefix(reserved);
        for (int index = 0; index < reserved.size(); index++) {
            replayRandomDraws.removeFirst();
        }
    }

    private PlayerInventory.DroppedStack explodedLoot(short brokenBlockType, double roll) {
        int drop = InventoryRules.explosionDropFor(brokenBlockType, () -> roll);
        drop = P1Rules.minedDrop(brokenBlockType, PlayerInventory.EMPTY, roll, drop);
        if (brokenBlockType >= Blocks.BAMBOO && brokenBlockType <= Blocks.MANGROVE_ROOTS) {
            drop = P2Rules.minedDrop(brokenBlockType, false, roll);
        }
        drop = P3Rules.minedDrop(brokenBlockType, false, roll, drop);
        drop = P4Rules.minedDrop(brokenBlockType, PlayerInventory.EMPTY, roll, drop);
        drop = P5Rules.minedDrop(brokenBlockType, PlayerInventory.EMPTY, roll, drop);
        drop = P6Rules.minedDrop(brokenBlockType, PlayerInventory.EMPTY, roll, drop);
        int count = P1Rules.minedDropCount(brokenBlockType, PlayerInventory.EMPTY, roll);
        count *= P5Rules.minedDropCount(brokenBlockType, PlayerInventory.EMPTY, roll);
        count *= OreRules.minedDropCount(brokenBlockType, roll);
        count *= P6Rules.minedDropCount(brokenBlockType, PlayerInventory.EMPTY, roll);
        // [SULFUR] 유황 광석·군집 수량. 유황 계열이 아니면 1 이라 다른 블록에 영향이 없다.
        // [CREAKING] 크리킹 하트의 수지 덩어리 수량. 폭발에는 인챈트가 없으므로 행운 폭이
        // 붙지 않는 1~3 이다([B] «Resin Clump» — 폭발로 부서져도 같은 표를 쓴다).
        count *= com.gameexpert.engine.creaking.CreakingHeartRules.minedDropCount(
                brokenBlockType, EnchantmentRules.EMPTY_ENCHANTMENTS, roll);
        count = Math.min(count, OreRules.dropCountLimit(brokenBlockType));
        return drop == 0 || count <= 0 ? null : PlayerInventory.DroppedStack.exact(
                (short) drop, count, PlayerInventory.initialDurability((short) drop),
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
    }

    private void planExplodedLeafDrops(ExplodedBlockDropRequest request,
            RandomTransaction random, List<PlannedExplosionStack> target) {
        boolean sapling = random.nextDouble() < 0.05;
        boolean stick = random.nextDouble() < 0.02;
        int stickCount = stick ? 1 + random.nextInt(2) : 0;
        boolean apple = InventoryRules.leavesDropApple(request.brokenBlockType())
                && random.nextDouble() < 0.005;
        if (sapling) target.add(plannedExplosionStack(simpleStack(
                InventoryRules.saplingForLeaves(request.brokenBlockType()), 1), request, random));
        if (stick) target.add(plannedExplosionStack(
                simpleStack(PlayerInventory.STICK, stickCount), request, random));
        if (apple) target.add(plannedExplosionStack(
                simpleStack(PlayerInventory.APPLE, 1), request, random));
    }

    private static PlayerInventory.DroppedStack simpleStack(short itemType, int count) {
        return PlayerInventory.DroppedStack.exact(itemType, count,
                PlayerInventory.initialDurability(itemType),
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
    }

    private static PlannedExplosionStack plannedExplosionStack(PlayerInventory.DroppedStack stack,
            ExplodedBlockDropRequest request, RandomTransaction random) {
        double sx = request.x() + (random.nextDouble() - 0.5) * SPAWN_JITTER;
        double sz = request.z() + (random.nextDouble() - 0.5) * SPAWN_JITTER;
        double vx = (random.nextDouble() - 0.5) * 0.04;
        double vz = (random.nextDouble() - 0.5) * 0.04;
        return new PlannedExplosionStack(stack, sx, request.y(), sz, vx, 0.04, vz);
    }

    private record PlannedExplosionStack(PlayerInventory.DroppedStack stack,
            double x, double y, double z, double vx, double vy, double vz) { }

    private record ExplosionStackPlan(List<PlannedExplosionStack> stacks,
            List<RandomDraw> randomDraws) { }

    private enum ExplosionPlanState { OPEN, COMMITTED, ROLLED_BACK }

    static final class ExplosionDropPlan {
        private final ItemEntitySystem owner;
        private final long expectedFirstId;
        private final long nextIdAfterBatch;
        private final List<GroundItemSnapshot> snapshots;
        private final List<RandomDraw> randomDraws;
        private final ExplosionGroundReservation reservation;
        private ExplosionPlanState state = ExplosionPlanState.OPEN;

        private ExplosionDropPlan(ItemEntitySystem owner, long expectedFirstId,
                long nextIdAfterBatch, List<GroundItemSnapshot> snapshots,
                List<RandomDraw> randomDraws, ExplosionGroundReservation reservation) {
            this.owner = owner;
            this.expectedFirstId = expectedFirstId;
            this.nextIdAfterBatch = nextIdAfterBatch;
            this.snapshots = snapshots;
            this.randomDraws = randomDraws;
            this.reservation = reservation;
        }

        List<GroundItemSnapshot> snapshots() {
            return snapshots;
        }
    }

    /**
     * 실제 플레이어 채굴 드랍을 스폰. 선택 도구 티어를 검사하고, 자격이 있는 경우에만
     * 자갈의 10% 부싯돌 등 채굴 난수를 굴린다.
     */
    void spawnMinedBlockDrop(short brokenBlockType, short selectedItemType,
            double x, double y, double z) {
        spawnMinedBlockDrop(brokenBlockType, selectedItemType,
                EnchantmentRules.EMPTY_ENCHANTMENTS, x, y, z);
    }

    /**
     * [SURV-X] 선택 도구의 인챈트 마스크까지 받는 채굴 드랍. 섬세한 손길이면 loot 표를 건너뛰고
     * 블록 자신 한 개만 내보내고, 행운이면 광석류 개수에 ordinary 배율을 곱한다.
     */
    void spawnDecoratedPotDrop(short potType, boolean cracked, String components,
            short[] generatedFaces, double x, double y, double z) {
        var data = com.gameexpert.engine.inventory.ItemComponentCodec.decode(potType, components);
        if (data.potDecorations().isEmpty() && generatedFaces != null) {
            var faces = new java.util.ArrayList<com.gameexpert.engine.inventory.ItemComponentData.PotDecoration>(4);
            for (short face : generatedFaces) faces.add(
                    new com.gameexpert.engine.inventory.ItemComponentData.PotDecoration(face, null, 0));
            data = data.withPotDecorations(faces);
        }
        if (cracked) {
            if (data.potDecorations().isEmpty()) {
                ArchaeologyRules.PotDrop plain = ArchaeologyRules.decoratedPotDrop(potType, true);
                spawnDrop(plain.itemType(), plain.count(), x, y, z);
            } else {
                for (var face : data.potDecorations()) spawnDrop(face.itemType(), 1, 0, 0L, 0, 0,
                        null, face.componentData(), x, y, z);
            }
        } else {
            spawnDrop(potType, 1, 0, 0L, 0, 0, null,
                    com.gameexpert.engine.inventory.ItemComponentCodec.encode(potType, data), x, y, z);
        }
    }

    void spawnMinedBlockDrop(short brokenBlockType, short selectedItemType, long enchantments,
            double x, double y, double z) {
        // [VANILLA-SOUNDS] 도구로 부순 장식 항아리는 금이 가(DecoratedPotBlock.playerWillDestroy) 재료 넷을 낸다.
        if (Blocks.isDecoratedPot(brokenBlockType)
                && ArchaeologyRules.shattersDecoratedPot(selectedItemType, enchantments)) {
            ArchaeologyRules.PotDrop sherds = ArchaeologyRules.decoratedPotDrop(brokenBlockType, true);
            spawnDrop(sherds.itemType(), sherds.count(), x, y, z);
            return;
        }
        short silk = InventoryRules.silkTouchDropFor(brokenBlockType, enchantments);
        if (silk != 0 && BlockEditRules.canHarvest(brokenBlockType, selectedItemType)) {
            spawnDrop(silk, 1, x, y, z);
            return;
        }
        if (InventoryRules.isWoodLeaves(brokenBlockType)) {
            spawnMinedLeafDrops(brokenBlockType, selectedItemType, x, y, z);
            return;
        }
        double roll = nextDouble();
        int drop = InventoryRules.minedDropFor(
                brokenBlockType, selectedItemType, enchantments, () -> roll);
        drop = P1Rules.minedDrop(brokenBlockType, selectedItemType, enchantments, roll, drop);
        boolean shears = selectedItemType == (short) Blocks.SHEARS;
        if (brokenBlockType >= Blocks.BAMBOO && brokenBlockType <= Blocks.MANGROVE_ROOTS) {
            drop = P2Rules.minedDrop(brokenBlockType, shears, enchantments, roll);
        }
        drop = P3Rules.minedDrop(brokenBlockType, shears, enchantments, roll, drop);
        drop = P4Rules.minedDrop(brokenBlockType, selectedItemType, enchantments, roll, drop);
        drop = P5Rules.minedDrop(brokenBlockType, selectedItemType, enchantments, roll, drop);
        drop = P6Rules.minedDrop(brokenBlockType, selectedItemType, enchantments, roll, drop);
        int count = P1Rules.minedDropCount(brokenBlockType, selectedItemType, enchantments, roll);
        count *= P5Rules.minedDropCount(brokenBlockType, selectedItemType, enchantments, roll);
        count *= OreRules.minedDropCount(brokenBlockType, enchantments, roll);
        count *= P6Rules.minedDropCount(brokenBlockType, selectedItemType, enchantments, roll);
        // [CREAKING] 크리킹 하트의 수지 덩어리 수량(1~3, 행운 레벨당 폭 +1). 행운 갈래가
        // 바닐라 uniform_bonus_count 라 아래 ordinary 배율과 겹치지 않는다 —
        // OreRules.fortuneApplies 가 하트에 false 이므로 곱셈이 두 번 걸리지 않는다.
        count *= com.gameexpert.engine.creaking.CreakingHeartRules.minedDropCount(
                brokenBlockType, enchantments, roll);
        if (brokenBlockType == Blocks.SNOW_BLOCK && drop == PlayerInventory.SNOWBALL) count = 4;
        // 행운 난수는 실제로 행운이 붙었을 때만 뽑아, 기존 채굴 난수열을 흔들지 않는다.
        if (EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.FORTUNE) > 0) {
            count *= OreRules.fortuneMultiplier(
                    brokenBlockType, enchantments, nextInt(Integer.MAX_VALUE));
        }
        // [PRISMARINE] 바닐라 limit_count 는 행운 보너스 **뒤에** 걸린다(바다 랜턴 최대 5).
        count = Math.min(count, OreRules.dropCountLimit(brokenBlockType));
        if (drop != 0 && count > 0) {
            spawnDrop((short) drop, count, x, y, z);
        }
    }

    /**
     * 나뭇잎은 묘목 5%, 막대 1~2개 2%, 참나무 계열 사과 0.5%를 서로 독립적으로 판정한다.
     * 가위는 확률 풀을 건너뛰고 잎 블록 자신 하나만 회수한다.
     */
    private void spawnMinedLeafDrops(short leaves, short selectedItem, double x, double y, double z) {
        if (selectedItem == (short) Blocks.SHEARS) {
            spawnDrop(leaves, 1, x, y, z);
            return;
        }
        boolean sapling = nextDouble() < 0.05;
        boolean stick = nextDouble() < 0.02;
        int stickCount = stick ? 1 + nextInt(2) : 0;
        boolean apple = InventoryRules.leavesDropApple(leaves) && nextDouble() < 0.005;
        if (sapling) {
            spawnDrop(InventoryRules.saplingForLeaves(leaves), 1, x, y, z);
        }
        if (stick) {
            spawnDrop(PlayerInventory.STICK, stickCount, x, y, z);
        }
        if (apple) {
            spawnDrop(PlayerInventory.APPLE, 1, x, y, z);
        }
    }

    // ── 틱 처리 ──
    void tick(long tickNo) {
        boolean changed = false;

        // 1) 신규 스폰 정산 + itemSpawn 브로드캐스트(스폰 틱은 이동/획득 없음: age 0).
        if (!pending.isEmpty() && rt != null && rt.groundMutationSettlements() != null) {
            rt.settlePendingGroundSpawns();
        } else if (!pending.isEmpty()) {
            for (ItemEntity e : pending) {
                entities.add(e);
                broadcast(new ItemSpawn(e.id, e.itemType, e.mapId, e.x, e.y, e.z,
                        renderVelocity(e, e.vx), renderVelocity(e, e.vy), renderVelocity(e, e.vz),
                        enchantmentWire(e), e.count).withPotDecorations(
                        com.gameexpert.engine.inventory.ItemComponentCodec.decode(e.itemType, e.itemComponentData).potDecorations()));
            }
            pending.clear();
            changed = true;
        }

        // 유휴 early-out: 스폰도 없고 엔티티도 없으면 이하 물리/병합/획득/수명/방송/스냅샷을 전부 스킵.
        if (entities.isEmpty()) {
            readyPlayerPickups = List.of();
            return;
        }

        // 2) 물리(살아온 지 1틱 이상인 것만 이동).
        CollisionLookup solid = collisionLookup(rt);
        FrictionFn friction = this::blockFrictionAt;
        for (ItemEntity e : entities) {
            if (pendingPlayerSettlements.contains(e.id)) continue;
            if (e.age >= 1) {
                boolean inFluid = applyFluidPush(e, fluidBlocks, fluidFlow);
                if (e.playerThrown && !inFluid) {
                    integrateMinecraftThrow(e, solid, friction);
                } else {
                    // applyFluidPush가 20 TPS 속도를 기존 10 TPS 유체 단위로 환산한다.
                    integrate(e, solid);
                }
            }
        }


        // 2.5) 이동 뒤 실제 점유 셀을 검사한다. 기존 아이템이 유체에 밀려 용암으로 들어가도 이 틱에 소실된다.
        List<Long> burned = null;
        for (ItemEntity e : entities) {
            if (pendingPlayerSettlements.contains(e.id)) continue;
            if (e.count > 0 && !survivesHeatAt(e.itemType, e.x, e.y, e.z)) {
                if (burned == null) burned = new ArrayList<>();
                burned.add(e.id);
                e.count = 0; // 뒤의 병합·획득에서 다시 처리되지 않게 즉시 사망 표시
            }
        }

        // 3) 병합(같은 종류, 1블록 이내). 흡수된 엔티티는 merge 로 count 0 이 되어 removeDead 가 제거.
        //    병합으로 count 가 바뀌면 스냅샷도 갱신해야 하므로 변화 여부를 반영한다.
        List<Long> merged = merge(entities, pendingPlayerSettlements);
        if (!merged.isEmpty()) {
            changed = true;
        }

        // 4) 근접 획득. 비동기 정산 뒤 남은 획득은 owner 연속 작업에서 이어간다.
        // 틱 끝의 age 증가/유예 감소로 막 조건을 채운 아이템은 다음 정규 틱까지 기다린다.
        readyPlayerPickups = entities.stream()
                .filter(item -> !pendingPlayerSettlements.contains(item.id)
                        && item.count > 0 && item.age >= PICKUP_MIN_AGE && item.pickupDelay <= 0)
                .toList();
        Map<String, List<Long>> pickedBy = null;
        for (ItemEntity e : entities) {
            if (pendingPlayerSettlements.contains(e.id)) continue;
            if (e.count <= 0 || e.age < PICKUP_MIN_AGE || e.pickupDelay > 0) {
                auditPickupEligibility(e, null, tickNo,
                        e.count <= 0 ? "EMPTY" : e.age < PICKUP_MIN_AGE ? "TOO_YOUNG" : "PICKUP_DELAY");
                continue;
            }
            PlayerTickState p = nearestPickup(e);
            auditPickupEligibility(e, p, tickNo, p == null ? "NO_ELIGIBLE_PLAYER" : "ELIGIBLE");
            if (p == null) {
                continue;
            }
            if (rt != null && rt.groundMutationSettlements() != null) {
                boolean available = rt.groundSettlementAvailable();
                rt.auditGroundPickupLane(e.id, available ? "AVAILABLE" : "BLOCKED");
                if (available && submitPlayerPickup(e, p)) break;
                continue;
            }
            int added = p.inventory().addItem(
                    e.itemType, e.count, e.durability, e.enchantments, e.mapId, e.shulkerId,
                    e.bucketMobData, e.itemComponentData);
            if (added <= 0) {
                continue; // 인벤 가득 → 엔티티 유지
            }
            sendTo(p, WorldTickLoop.inventoryMessage(p));
            e.count -= added; // 부분 획득도 count 변화 → 스냅샷 갱신 필요
            markPersistenceDirty();
            broadcast(new ItemPickup(e.x, e.y, e.z));
            changed = true;
            if (e.count <= 0) {
                if (pickedBy == null) {
                    pickedBy = new LinkedHashMap<>();
                }
                pickedBy.computeIfAbsent(p.nickname(), k -> new ArrayList<>()).add(e.id);
            }
        }

        // 플레이어의 기존 획득 우선순위는 그대로 둔다. 남은 장비만 0.5초마다 인간형 적대 몹이 평가한다.
        if (tickNo % MOB_PICKUP_INTERVAL == 0) {
            for (ItemEntity e : entities) {
                if (pendingPlayerSettlements.contains(e.id)
                        || e.count <= 0 || e.age < PICKUP_MIN_AGE || e.pickupDelay > 0) continue;
                if (!mobEquipmentCanCarry(e)) continue;
                long mobId = rt.mobSystem().tryPickupEquipment(e.itemType, e.durability,
                        e.enchantments, e.itemComponentData, e.x, e.y, e.z, MOB_PICKUP_RANGE);
                if (mobId == 0) continue;
                e.count--;
                markPersistenceDirty();
                changed = true;
                if (e.count <= 0) {
                    if (pickedBy == null) pickedBy = new LinkedHashMap<>();
                    pickedBy.computeIfAbsent("mob:" + mobId, k -> new ArrayList<>()).add(e.id);
                }
            }
        }

        // 5) 수명 소멸(age>=6000, 소멸 발생 시에만 리스트 할당).
        List<Long> despawned = null;
        for (ItemEntity e : entities) {
            if (!pendingPlayerSettlements.contains(e.id)
                    && e.age >= DESPAWN_AGE && e.count > 0) {
                if (despawned == null) {
                    despawned = new ArrayList<>();
                }
                despawned.add(e.id);
            }
        }

        // 6) 제거 반영(병합/획득/수명으로 사라진 것).
        changed |= removeDead(pickedBy, despawned, burned);
        if (!merged.isEmpty() || pickedBy != null && !pickedBy.isEmpty()
                || despawned != null && !despawned.isEmpty()
                || burned != null && !burned.isEmpty()) {
            markPersistenceDirty();
        }

        // 7) 나이 증가.
        for (ItemEntity e : entities) {
            if (pendingPlayerSettlements.contains(e.id)) continue;
            e.age++;
            if (e.pickupDelay > 0) e.pickupDelay--;
        }

        // 다음 몹 틱이 최신 위치에서 가장 가까운 matching stack을 고르도록, 플레이어/장비
        // 획득과 제거가 모두 끝난 live 엔티티만 제공한다.
        rt.mobSystem().offerAllayDroppedItems(entities);

        // 8) 위치 또는 수량이 바뀐 엔티티 상태 배칭.
        changed |= broadcastUpdates();

        // 9) welcome 스냅샷 게시: 집합/위치/수량이 실제로 바뀐 틱에만(매틱 전량 재할당 제거).
        if (changed) {
            publishSnapshot();
        }
    }

    /**
     * [HOPPER] {@code HopperBlockEntity.getItemsAtAndAbove}: live entities whose 0.25 box
     * intersects the hopper's SUCK_AABB. {@code getEntitiesOfClass} has no stable order; both
     * authorities use ascending entity id. Stacks still settling into a player's inventory are
     * not live for this purpose.
     */
    /**
     * [CONTAINER-MENUS] Live item entities whose 0.25 x 0.25 box intersects the given box (a hopper
     * minecart's suck area), by entity ID.
     */
    Iterable<ItemEntity> redstoneItems() { return entities; }

    List<ItemEntity> itemsIntersecting(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        List<ItemEntity> found = null;
        for (ItemEntity entity : entities) {
            if (entity.count <= 0 || pendingPlayerSettlements.contains(entity.id)) continue;
            if (entity.x + 0.125 <= minX || entity.x - 0.125 >= maxX || entity.y + 0.25 <= minY
                    || entity.y >= maxY || entity.z + 0.125 <= minZ || entity.z - 0.125 >= maxZ) {
                continue;
            }
            if (found == null) found = new ArrayList<>();
            found.add(entity);
        }
        if (found == null) return List.of();
        found.sort(java.util.Comparator.comparingLong(entity -> entity.id));
        return found;
    }

    List<ItemEntity> hopperSuckCandidates(int hopperX, int hopperY, int hopperZ, boolean inside) {
        List<ItemEntity> found = null;
        for (ItemEntity entity : entities) {
            if (entity.count <= 0 || pendingPlayerSettlements.contains(entity.id)) continue;
            boolean hit = inside
                    ? com.gameexpert.engine.hopper.HopperRules.itemInsideHopper(
                            hopperX, hopperY, hopperZ, entity.x, entity.y, entity.z)
                    : com.gameexpert.engine.hopper.HopperRules.itemInSuckArea(
                            hopperX, hopperY, hopperZ, entity.x, entity.y, entity.z);
            if (!hit) continue;
            if (found == null) found = new ArrayList<>();
            found.add(entity);
        }
        if (found == null) return List.of();
        found.sort(java.util.Comparator.comparingLong(entity -> entity.id));
        return found;
    }

    /**
     * [HOPPER] {@code ItemEntity.setItem(remaining)} / {@code discard()} after a hopper absorbed
     * part or all of the stack. The identity columns never change; only the count shrinks.
     */
    void applyHopperAbsorption(ItemEntity entity, int remaining) {
        if (remaining >= entity.count) return;
        if (remaining <= 0) {
            entity.count = 0;
            entities.remove(entity);
            broadcast(new ItemRemove(List.of(entity.id), "despawn", null));
        } else {
            entity.count = remaining;
            broadcastItemUpdate(entity);
        }
        markPersistenceDirty();
        publishSnapshot();
    }

    boolean hasPendingSpawns() {
        return !pending.isEmpty();
    }

    List<GroundItemSnapshot> pendingSpawnSnapshots() {
        return pending.stream().map(item -> snapshot(item, item.count)).toList();
    }

    void validatePendingSpawns(List<GroundItemSnapshot> snapshots) {
        Map<Long, GroundItemSnapshot> current = pendingSpawnSnapshots().stream()
                .collect(java.util.stream.Collectors.toMap(GroundItemSnapshot::entityId,
                        java.util.function.Function.identity()));
        for (GroundItemSnapshot snapshot : snapshots) {
            if (!snapshot.equals(current.get(snapshot.entityId()))) {
                throw new IllegalStateException("committed item spawn diverged from frozen batch");
            }
        }
    }

    /** The shared item/XP transaction has committed; publish only its frozen members. */
    void commitPendingSpawns(List<GroundItemSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        Set<Long> ids = snapshots.stream().map(GroundItemSnapshot::entityId)
                .collect(java.util.stream.Collectors.toSet());
        List<ItemEntity> batch = pending.stream().filter(item -> ids.contains(item.id)).toList();
        pending.removeAll(batch);
        for (ItemEntity item : batch) {
            entities.add(item);
            broadcast(new ItemSpawn(item.id, item.itemType, item.mapId,
                    item.x, item.y, item.z, renderVelocity(item, item.vx),
                    renderVelocity(item, item.vy), renderVelocity(item, item.vz), enchantmentWire(item), item.count).withPotDecorations(
                        com.gameexpert.engine.inventory.ItemComponentCodec.decode(item.itemType, item.itemComponentData).potDecorations()));
        }
        publishSnapshot();
    }

    private void auditPickupEligibility(ItemEntity item, PlayerTickState selected,
            long tickNo, String reason) {
        if (rt == null || !GroundPickupAudit.enabled(rt.worldId(), item.id)) return;
        // A non-selected witness exposes dead/out-of-range rejection without changing selection.
        PlayerTickState witness = selected != null ? selected
                : rt.players().values().stream().findFirst().orElse(null);
        GroundPickupAudit.event(rt.worldId(), item.id, "eligibility", reason,
                "tick,count,age,pickupDelay,pending,players,playerId,selected,dead,withinBox,px,py,pz,ix,iy,iz,leased,inventoryRevision",
                tickNo, item.count, item.age, item.pickupDelay,
                pendingPlayerSettlements.contains(item.id) ? 1 : 0, rt.players().size(),
                witness == null ? -1L : witness.playerId(), selected == null ? 0 : 1,
                witness == null ? -1 : witness.isDead() ? 1 : 0,
                witness != null && withinPlayerPickupBox(item, witness) ? 1 : 0,
                witness == null ? 0.0 : witness.x(), witness == null ? 0.0 : witness.y(),
                witness == null ? 0.0 : witness.z(), item.x, item.y, item.z,
                witness != null && witness.inventory().settlementLeased() ? 1 : 0,
                witness == null ? -1L : witness.inventory().revision());
    }

    /**
     * 저장 한 건이 끝나면 다음 100ms 틱을 기다리지 않고 남은 근접 아이템을 다시 판정한다.
     * owner 드레인은 입장 시 callback 수를 고정하므로 매번 한 정산만 제출하며 재귀하지 않는다.
     * 물리·나이·픽업 유예는 정규 틱만 진행하고, 위치와 인벤토리는 매번 현재 상태를 사용한다.
     */
    private void continuePlayerPickups() {
        if (!rt.ownerTurnMayContinue() || !rt.groundSettlementAvailable()) return;
        for (ItemEntity item : readyPlayerPickups) {
            if (!entities.contains(item) || pendingPlayerSettlements.contains(item.id)
                    || item.count <= 0 || item.age < PICKUP_MIN_AGE || item.pickupDelay > 0) continue;
            PlayerTickState player = nearestPickup(item);
            if (player != null && submitPlayerPickup(item, player)) return;
        }
    }

    private boolean submitPlayerPickup(ItemEntity item, PlayerTickState player) {
        PlayerInventory inventory = player.inventory();
        boolean audit = GroundPickupAudit.enabled(rt.worldId(), item.id);
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (audit) GroundPickupAudit.event(rt.worldId(), item.id, "lease-acquire",
                source == null ? "REFUSED" : "ACQUIRED", "playerId,inventoryRevision,leased",
                player.playerId(), inventory.revision(), inventory.settlementLeased() ? 1 : 0);
        if (source == null) return false;
        PlayerInventory planned = source.detachedInventory();
        int added = planned.addItem(item.itemType, item.count, item.durability,
                item.enchantments, item.mapId, item.shulkerId, item.bucketMobData,
                item.itemComponentData);
        if (audit) GroundPickupAudit.event(rt.worldId(), item.id, "inventory-add",
                added <= 0 ? "REFUSED" : "ACCEPTED",
                "added,itemType,count,durability,enchantments,mapId,shulkerId,bucketPresent,componentsPresent,sourceRevision",
                added, item.itemType, item.count, item.durability, item.enchantments, item.mapId,
                item.shulkerId, item.bucketMobData == null ? 0 : 1,
                item.itemComponentData == null ? 0 : 1, source.revision());
        if (added <= 0) {
            boolean released = inventory.releaseSettlementLease(source);
            if (audit) GroundPickupAudit.event(rt.worldId(), item.id, "lease-release",
                    released ? "RELEASED_AFTER_ADD_REFUSAL" : "RELEASE_MISSED_AFTER_ADD_REFUSAL",
                    "sourceRevision,inventoryRevision,leased", source.revision(), inventory.revision(),
                    inventory.settlementLeased() ? 1 : 0);
            return false;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        int expectedCount = item.count;
        int remaining = expectedCount - added;
        GroundItemSnapshot replacement = remaining == 0 ? null
                : snapshot(item, remaining);
        long expectedGroundRevision = rt.groundRevision();
        GroundMutationCommand command = new GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(item.id, 1,
                        expectedGroundRevision, source.revision()),
                GroundMutationCommand.Kind.PLAYER_PICKUP, rt.worldId(),
                expectedGroundRevision, expectedGroundRevision + 1, source.revision(),
                rt.playerInventoryMutationSnapshot(player, committed),
                replacement == null ? List.of() : List.of(replacement),
                replacement == null ? List.of(item.id) : List.of(), List.of(), List.of());
        pendingPlayerSettlements.add(item.id);
        Runnable rejected = () -> {
            pendingPlayerSettlements.remove(item.id);
            boolean released = inventory.releaseSettlementLease(source);
            if (audit) GroundPickupAudit.event(rt.worldId(), item.id, "lease-release",
                    released ? "RELEASED_AFTER_REJECTION" : "RELEASE_MISSED_AFTER_REJECTION",
                    "mutationId,sourceRevision,inventoryRevision,leased", command.mutationId(),
                    source.revision(), inventory.revision(), inventory.settlementLeased() ? 1 : 0);
        };
        Runnable installed = () -> {
            pendingPlayerSettlements.remove(item.id);
            ItemEntity live = entity(item.id);
            if (live == null || live.count != expectedCount) {
                throw new IllegalStateException("committed pickup diverged from frozen item");
            }
            live.count = remaining;
            if (remaining == 0) entities.remove(live);
            markPersistenceDirty();
            boolean playerInstalled = rt.players().get(player.nickname()) == player
                    && inventory.installCommittedSettlement(source, committed);
            if (playerInstalled) sendTo(player, WorldTickLoop.inventoryMessage(player));
            else {
                boolean released = inventory.releaseSettlementLease(source);
                if (audit) GroundPickupAudit.event(rt.worldId(), item.id, "lease-release",
                        released ? "RELEASED_AFTER_INSTALL_MISS" : "RELEASE_MISSED_AFTER_INSTALL_MISS",
                        "sourceRevision,inventoryRevision,leased", source.revision(), inventory.revision(),
                        inventory.settlementLeased() ? 1 : 0);
            }
            if (audit) GroundPickupAudit.event(rt.worldId(), item.id, "item-install",
                    playerInstalled ? "PLAYER_AND_GROUND_INSTALLED" : "GROUND_ONLY_INSTALLED",
                    "mutationId,remaining,sourceRevision,inventoryRevision,leased", command.mutationId(),
                    remaining, source.revision(), inventory.revision(), inventory.settlementLeased() ? 1 : 0);
            broadcast(new ItemPickup(live.x, live.y, live.z));
            if (remaining == 0) {
                broadcast(new ItemRemove(List.of(live.id), "pickup", player.nickname()));
            } else {
                broadcastItemUpdate(live);
            }
            publishSnapshot();
            if (playerInstalled) rt.enqueuePersistenceCompletion(this::continuePlayerPickups);
        };
        if (!rt.submitGroundSettlement(command, installed, rejected)) {
            rejected.run();
            return false;
        }
        return true;
    }

    private ItemEntity entity(long entityId) {
        for (ItemEntity item : entities) if (item.id == entityId) return item;
        return null;
    }

    private static GroundItemSnapshot snapshot(ItemEntity item, int count) {
        return new GroundItemSnapshot(item.id, item.itemType, count, item.durability,
                item.enchantments, item.mapId, item.shulkerId, item.bucketMobData,
                item.itemComponentData,
                item.x, item.y, item.z, item.vx, item.vy, item.vz,
                item.playerThrown, item.age, item.pickupDelay, item.excludedAllayId);
    }

    /** Stable identity replay is accepted only when every durable field is bit-exact. */
    private static boolean exactSnapshot(ItemEntity item, GroundItemSnapshot snapshot) {
        return item.id == snapshot.entityId()
                && item.itemType == snapshot.itemType()
                && item.count == snapshot.count()
                && item.durability == snapshot.durability()
                && item.enchantments == snapshot.enchantments()
                && item.mapId == snapshot.mapId()
                && item.shulkerId == snapshot.shulkerId()
                && Objects.equals(item.bucketMobData, snapshot.bucketMobData())
                && Objects.equals(item.itemComponentData, snapshot.itemComponentData())
                && Double.doubleToLongBits(item.x) == Double.doubleToLongBits(snapshot.x())
                && Double.doubleToLongBits(item.y) == Double.doubleToLongBits(snapshot.y())
                && Double.doubleToLongBits(item.z) == Double.doubleToLongBits(snapshot.z())
                && Double.doubleToLongBits(item.vx)
                        == Double.doubleToLongBits(snapshot.velocityX())
                && Double.doubleToLongBits(item.vy)
                        == Double.doubleToLongBits(snapshot.velocityY())
                && Double.doubleToLongBits(item.vz)
                        == Double.doubleToLongBits(snapshot.velocityZ())
                && item.playerThrown == snapshot.playerThrown()
                && item.age == snapshot.age()
                && item.pickupDelay == snapshot.pickupDelay()
                && item.excludedAllayId == snapshot.excludedAllayId();
    }

    boolean containsIdentity(long entityId) {
        for (ItemEntity item : entities) if (item.id == entityId) return true;
        for (ItemEntity item : pending) if (item.id == entityId) return true;
        return false;
    }

    // ── 물리: 중력 + 지면 안착(비고체 통과) + 수평 마찰(순수 함수, 테스트 주입 가능) ──
    static void integrate(ItemEntity e, CollisionLookup solid) {
        e.vy -= GRAVITY;
        if (e.vy < -TERMINAL) {
            e.vy = -TERMINAL;
        }
        PointSweep vertical = sweepPoint(solid, e.x, e.y, e.z, e.vy, 1);
        boolean grounded = e.vy <= 0 && vertical.hit;
        e.y = vertical.value;
        if (vertical.hit) e.vy = 0;
        PointSweep horizontal = sweepPoint(solid, e.x, e.y, e.z, e.vx, 0);
        e.x = horizontal.value;
        if (horizontal.hit) e.vx = 0;
        PointSweep depth = sweepPoint(solid, e.x, e.y, e.z, e.vz, 2);
        e.z = depth.value;
        if (depth.hit) e.vz = 0;

        double friction = grounded ? 0.5 : 0.92;
        e.vx *= friction;
        e.vz *= friction;
        if (Math.abs(e.vx) < POS_EPS) e.vx = 0;
        if (Math.abs(e.vz) < POS_EPS) e.vz = 0;
    }

    /** Q 투척의 바닐라 20 TPS 궤적을 10 TPS 서버 틱 하나에 두 하위 스텝으로 재현한다. */
    static void integrateMinecraftThrow(ItemEntity e, CollisionLookup solid) {
        integrateMinecraftThrow(e, solid, (x, y, z) -> MINECRAFT_DEFAULT_BLOCK_FRICTION);
    }

    static void integrateMinecraftThrow(ItemEntity e, CollisionLookup solid, FrictionFn friction) {
        for (int i = 0; i < THROWN_PHYSICS_STEPS; i++) {
            integrateMinecraftItemStep(e, solid, friction);
        }
    }

    private static void integrateMinecraftItemStep(ItemEntity e, CollisionLookup solid, FrictionFn friction) {
        e.vy -= MINECRAFT_ITEM_GRAVITY;
        PointSweep vertical = sweepPoint(solid, e.x, e.y, e.z, e.vy, 1);
        boolean grounded = e.vy <= 0 && vertical.hit;
        e.y = vertical.value;
        if (vertical.hit && !grounded) e.vy = 0;
        PointSweep horizontal = sweepPoint(solid, e.x, e.y, e.z, e.vx, 0);
        e.x = horizontal.value;
        if (horizontal.hit) e.vx = 0;
        PointSweep depth = sweepPoint(solid, e.x, e.y, e.z, e.vz, 2);
        e.z = depth.value;
        if (depth.hit) e.vz = 0;

        double horizontalDrag = grounded
                ? friction.friction(floor(e.x), floor(e.y - 1e-9), floor(e.z))
                        * MINECRAFT_ITEM_AIR_DRAG
                : MINECRAFT_ITEM_AIR_DRAG;
        e.vx *= horizontalDrag;
        e.vy *= MINECRAFT_ITEM_AIR_DRAG;
        e.vz *= horizontalDrag;
        if (grounded && e.vy < 0) e.vy *= -0.5;
        if (Math.abs(e.vx) < POS_EPS) e.vx = 0;
        if (Math.abs(e.vz) < POS_EPS) e.vz = 0;
    }

    /** 기존 점 위치 모델을 유지하되 이동 구간 전체와 실제 상태 AABB를 교차한다. */
    static PointSweep sweepPoint(CollisionLookup blocks, double x, double y, double z,
            double delta, int axis) {
        PointSweep sweep = new PointSweep(x, y, z, delta, axis);
        if (delta == 0) return sweep;
        double endX = x + (axis == 0 ? delta : 0);
        double endY = y + (axis == 1 ? delta : 0);
        double endZ = z + (axis == 2 ? delta : 0);
        // 선반버섯의 수평 돌출과 울타리/담장의 위쪽 돌출을 포함한다.
        double outset = 3.5 / 16;
        for (int bx = floor(Math.min(x, endX) - outset); bx <= floor(Math.max(x, endX) + outset); bx++) {
            for (int by = floor(Math.min(y, endY)) - 1; by <= floor(Math.max(y, endY)); by++) {
                for (int bz = floor(Math.min(z, endZ) - outset); bz <= floor(Math.max(z, endZ) + outset); bz++) {
                    sweep.cellX = bx; sweep.cellY = by; sweep.cellZ = bz;
                    int block = blocks.block(bx, by, bz);
                    if (block < 0) sweep.visit(0, 0, 0, 1, 1, 1);
                    else blocks.boxes(bx, by, bz, sweep);
                }
            }
        }
        return sweep;
    }

    static final class PointSweep implements BuildingBlockRules.CollisionBoxVisitor {
        final double x, y, z, delta, origin;
        final int axis;
        int cellX, cellY, cellZ;
        double value;
        boolean hit;

        PointSweep(double x, double y, double z, double delta, int axis) {
            this.x = x; this.y = y; this.z = z; this.delta = delta; this.axis = axis;
            origin = axis == 0 ? x : axis == 1 ? y : z;
            value = origin + delta;
        }

        public void visit(double x0, double y0, double z0, double x1, double y1, double z1) {
            x0 += cellX; x1 += cellX; y0 += cellY; y1 += cellY; z0 += cellZ; z1 += cellZ;
            if (axis != 0 && (x < x0 || x >= x1)
                    || axis != 1 && (y < y0 || y >= y1)
                    || axis != 2 && (z < z0 || z >= z1)) return;
            double near = axis == 0 ? x0 : axis == 1 ? y0 : z0;
            double far = axis == 0 ? x1 : axis == 1 ? y1 : z1;
            if (delta > 0 && origin <= near && value >= near) {
                value = near - 1e-9;
                hit = true;
            } else if (delta < 0 && origin >= far && value <= far) {
                value = far + (axis == 1 ? 0 : 1e-9);
                hit = true;
            } else if (origin > near && origin < far) {
                // 기존 스폰/복원 위치가 바닥 안에 있으면 위쪽 면으로 안착시킨다.
                value = axis == 1 && delta < 0 ? Math.max(value, far) : origin;
                hit = true;
            }
        }
    }

    /** WS 렌더 예측용 블록/초 속도. 내부 물리 단위 차이는 프로토콜 경계에서 숨긴다. */
    private static double renderVelocity(ItemEntity e, double velocity) {
        return velocity * (e.playerThrown ? MINECRAFT_TICKS_PER_SECOND : SERVER_TICKS_PER_SECOND);
    }

    /** 점 엔티티가 점유한 유체 셀에서만 기존 속도에 흐름 가속도를 더한다. */
    static boolean applyFluidPush(ItemEntity e, Fluids.BlockLookup blocks, double[] flow) {
        int bx = floor(e.x);
        int by = floor(e.y);
        int bz = floor(e.z);
        int fluidType = Fluids.typeOf(blocks.get(bx, by, bz));
        if (fluidType == Fluids.NONE || !Fluids.flowVector(blocks, bx, by, bz, flow)) {
            return false;
        }
        if (e.playerThrown) {
            // 흐름 가속도는 서버 틱 단위다. 단위를 바꾸기 전에 기존 속도부터 환산한다.
            double tickScale = MINECRAFT_TICKS_PER_SECOND / SERVER_TICKS_PER_SECOND;
            e.vx *= tickScale;
            e.vy *= tickScale;
            e.vz *= tickScale;
            e.playerThrown = false;
        }
        double push = Fluids.pushPerTick(fluidType);
        e.vx += flow[0] * push;
        e.vy += flow[1] * push;
        e.vz += flow[2] * push;
        return true;
    }

    /**
     * 같은 종류를 1블록 이내에서 스택 상한(64)까지 병합. 흡수돼 count 0 이 된 엔티티 id 목록 반환.
     * 소규모는 기존 O(n²), 대규모(≥{@link #MERGE_GRID_THRESHOLD})는 공간 그리드로 근방 후보만 훑는다.
     * 두 경로는 <b>완전히 동일한 결과</b>를 낸다(그리드는 후보 축소만; 아래 {@link #mergeGrid} 주석 참조).
     */
    static List<Long> merge(List<ItemEntity> entities) {
        return merge(entities, Set.of());
    }

    private static List<Long> merge(List<ItemEntity> entities, Set<Long> excludedIds) {
        int n = entities.size();
        if (n < 2) {
            return List.of();
        }
        return n < MERGE_GRID_THRESHOLD
                ? mergeLinear(entities, excludedIds) : mergeGrid(entities, excludedIds);
    }

    /** 기존 O(n²) 병합(참조 구현). 바깥 i 오름차순, 안쪽 j=i+1.. 오름차순으로 흡수한다. */
    static List<Long> mergeLinear(List<ItemEntity> entities) {
        return mergeLinear(entities, Set.of());
    }

    private static List<Long> mergeLinear(List<ItemEntity> entities, Set<Long> excludedIds) {
        List<Long> mergedIds = null;
        for (int i = 0; i < entities.size(); i++) {
            ItemEntity a = entities.get(i);
            if (excludedIds.contains(a.id)
                    || a.count <= 0 || a.pickupDelay > 0 || PlayerInventory.isDurable(a.itemType)) {
                continue;
            }
            int max = PlayerInventory.stackMax(a.itemType);
            if (a.count >= max) continue;
            for (int j = i + 1; j < entities.size(); j++) {
                ItemEntity b = entities.get(j);
                if (excludedIds.contains(b.id)
                        || b.count <= 0 || b.pickupDelay > 0 || b.itemType != a.itemType
                        || b.enchantments != a.enchantments || b.mapId != a.mapId
                        || b.shulkerId != a.shulkerId
                        || !java.util.Objects.equals(b.bucketMobData, a.bucketMobData)
                        || !java.util.Objects.equals(b.itemComponentData, a.itemComponentData)
                        || b.excludedAllayId != a.excludedAllayId) {
                    continue;
                }
                if (a.count >= max) {
                    break;
                }
                if (dist3dSq(a, b) > MERGE_RANGE * MERGE_RANGE) {
                    continue;
                }
                int move = Math.min(max - a.count, b.count);
                a.count += move;
                b.count -= move;
                if (b.count <= 0) {
                    if (mergedIds == null) {
                        mergedIds = new ArrayList<>();
                    }
                    mergedIds.add(b.id);
                }
            }
        }
        return mergedIds == null ? List.of() : mergedIds;
    }

    /**
     * 공간 그리드 기반 병합. {@link #mergeLinear} 와 결과가 완전히 동일하도록 다음을 지킨다:
     *  ① 바깥 i 는 여전히 리스트 오름차순으로 순회한다.
     *  ② a 의 근방 셀에서 모은 후보 인덱스를 <b>오름차순 정렬</b>해, 원래 안쪽 j 루프의 방문 순서를
     *     그대로 재현한다(누적/break/흡수 대상 선택이 순서에 의존하므로 필수).
     *  ③ 후보에는 j≤i 나 사거리 밖(코너 셀)이 섞일 수 있으나, {@code j<=i} 스킵과 원래의
     *     {@code dist3dSq>MERGE_RANGE²} 재검증이 원본과 동일하게 걸러낸다.
     * 사거리 밖 엔티티는 원본에서도 상태를 바꾸지 않으므로 제외해도 산출은 불변이다.
     * 병합 중 좌표는 변하지 않으므로 그리드는 시작 시 한 번만 채우면 유효하다.
     */
    static List<Long> mergeGrid(List<ItemEntity> entities) {
        return mergeGrid(entities, Set.of());
    }

    private static List<Long> mergeGrid(List<ItemEntity> entities, Set<Long> excludedIds) {
        int n = entities.size();
        SpatialGrid grid = new SpatialGrid(MERGE_CELL);
        grid.reset(n);
        for (int i = 0; i < n; i++) {
            ItemEntity e = entities.get(i);
            grid.add(i, e.x, e.y, e.z);
        }

        List<Long> mergedIds = null;
        IntBuf cand = new IntBuf();
        for (int i = 0; i < n; i++) {
            ItemEntity a = entities.get(i);
            if (excludedIds.contains(a.id)
                    || a.count <= 0 || a.pickupDelay > 0 || PlayerInventory.isDurable(a.itemType)) {
                continue;
            }
            int max = PlayerInventory.stackMax(a.itemType);
            if (a.count >= max) continue;
            cand.size = 0;
            grid.forEachNear(a.x, a.y, a.z, MERGE_RANGE, cand);
            boolean linear = SpatialGrid.preferLinearTraversal(cand.size, n);
            if (!linear) {
                java.util.Arrays.sort(cand.a, 0, cand.size); // 원래 j 오름차순 방문 순서 재현
            }
            int candidateCount = linear ? n : cand.size;
            for (int k = 0; k < candidateCount; k++) {
                int j = linear ? k : cand.a[k];
                if (j <= i) {
                    continue;
                }
                ItemEntity b = entities.get(j);
                if (excludedIds.contains(b.id)
                        || b.count <= 0 || b.pickupDelay > 0 || b.itemType != a.itemType
                        || b.enchantments != a.enchantments || b.mapId != a.mapId
                        || b.shulkerId != a.shulkerId
                        || !java.util.Objects.equals(b.bucketMobData, a.bucketMobData)
                        || !java.util.Objects.equals(b.itemComponentData, a.itemComponentData)
                        || b.excludedAllayId != a.excludedAllayId) {
                    continue;
                }
                if (a.count >= max) {
                    break;
                }
                if (dist3dSq(a, b) > MERGE_RANGE * MERGE_RANGE) {
                    continue;
                }
                int move = Math.min(max - a.count, b.count);
                a.count += move;
                b.count -= move;
                if (b.count <= 0) {
                    if (mergedIds == null) {
                        mergedIds = new ArrayList<>();
                    }
                    mergedIds.add(b.id);
                }
            }
        }
        return mergedIds == null ? List.of() : mergedIds;
    }

    /** forEachNear 후보를 담는 재사용 int 버퍼(할당 최소화). */
    private static final class IntBuf implements SpatialGrid.IntSink {
        int[] a = new int[16];
        int size;

        @Override
        public void accept(int idx) {
            if (size == a.length) {
                a = java.util.Arrays.copyOf(a, size * 2);
            }
            a[size++] = idx;
        }
    }

    private PlayerTickState nearestPickup(ItemEntity e) {
        PlayerTickState best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (PlayerTickState p : rt.players().values()) {
            if (p.isDead()) {
                continue;
            }
            if (!withinPlayerPickupBox(e, p)) continue;
            double dx = p.x() - e.x;
            double dy = p.y() - e.y;
            double dz = p.z() - e.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean withinPlayerPickupBox(ItemEntity e, PlayerTickState p) {
        return Math.abs(e.x - p.x()) <= PLAYER_PICKUP_HORIZONTAL
                && Math.abs(e.z - p.z()) <= PLAYER_PICKUP_HORIZONTAL
                && e.y >= p.y() - PLAYER_PICKUP_BELOW
                && e.y <= p.y() + PLAYER_PICKUP_ABOVE;
    }

    /**
     * 병합/획득/수명으로 사라진 엔티티를 리스트에서 제거하고 사유별 itemRemove 를 방송한다.
     * pickedBy·despawned 는 해당 사유가 없으면 null(호출부가 발생 시에만 할당). 무언가 제거됐으면 true.
     */
    private boolean removeDead(Map<String, List<Long>> pickedBy, List<Long> despawned, List<Long> burned) {
        // 병합으로 count 0 이 된 것은 merge 가 count 만 0 으로 만든다 → 여기서 merge 사유로 제거.
        java.util.Set<Long> pickedIds = null;
        if (pickedBy != null) {
            pickedIds = new java.util.HashSet<>();
            for (List<Long> ids : pickedBy.values()) {
                pickedIds.addAll(ids);
            }
        }
        java.util.Set<Long> despawnIds = (despawned == null) ? null : new java.util.HashSet<>(despawned);
        java.util.Set<Long> burnedIds = (burned == null) ? null : new java.util.HashSet<>(burned);
        List<Long> mergeRemoved = null;
        boolean removedAny = false;

        for (Iterator<ItemEntity> it = entities.iterator(); it.hasNext(); ) {
            ItemEntity e = it.next();
            boolean isDespawn = despawnIds != null && despawnIds.contains(e.id);
            boolean isBurned = burnedIds != null && burnedIds.contains(e.id);
            if (e.count > 0 && !isDespawn && !isBurned) {
                continue;
            }
            if ((pickedIds != null && pickedIds.contains(e.id)) || isDespawn || isBurned) {
                it.remove(); // 획득/수명 사유는 아래에서 이미 배칭 대상
                removedAny = true;
            } else if (e.count <= 0) {
                if (mergeRemoved == null) {
                    mergeRemoved = new ArrayList<>();
                }
                mergeRemoved.add(e.id);
                it.remove();
                removedAny = true;
            }
        }

        if (pickedBy != null) {
            for (Map.Entry<String, List<Long>> en : pickedBy.entrySet()) {
                broadcast(new ItemRemove(en.getValue(), "pickup", en.getKey()));
            }
        }
        if (despawned != null && !despawned.isEmpty()) {
            broadcast(new ItemRemove(despawned, "despawn", null));
        }
        if (burned != null && !burned.isEmpty()) {
            broadcast(new ItemRemove(burned, "burn", null));
        }
        if (mergeRemoved != null) {
            broadcast(new ItemRemove(mergeRemoved, "merge", null));
        }
        return removedAny;
    }

    /** 위치 또는 수량이 바뀐 엔티티를 배칭 방송한다(welcome 스냅샷 갱신 신호). */
    private boolean broadcastUpdates() {
        List<ItemPos> changed = null;
        for (ItemEntity e : entities) {
            if (Math.abs(e.x - e.lastX) > POS_EPS
                    || Math.abs(e.y - e.lastY) > POS_EPS
                    || Math.abs(e.z - e.lastZ) > POS_EPS
                    || e.count != e.lastCount) {
                if (changed == null) {
                    changed = new ArrayList<>();
                }
                changed.add(itemUpdate(e));
            }
        }
        if (changed != null) {
            broadcast(new ItemUpdates(changed));
            return true;
        }
        return false;
    }

    /** 틱 배칭 이후 확정되는 부분 획득도 즉시 같은 권위 상태로 알린다. */
    private void broadcastItemUpdate(ItemEntity item) {
        broadcast(new ItemUpdates(List.of(itemUpdate(item))));
    }

    private static ItemPos itemUpdate(ItemEntity item) {
        item.lastX = item.x; item.lastY = item.y; item.lastZ = item.z;
        item.lastCount = item.count;
        return new ItemPos(item.id, item.x, item.y, item.z, item.count);
    }

    private void publishSnapshot() {
        List<ItemEntityDto> snap = new ArrayList<>(entities.size());
        for (ItemEntity e : entities) {
            snap.add(new ItemEntityDto(e.id, e.itemType, e.mapId, e.x, e.y, e.z,
                    renderVelocity(e, e.vx), renderVelocity(e, e.vy), renderVelocity(e, e.vz),
                    enchantmentWire(e), e.count).withPotDecorations(
                        com.gameexpert.engine.inventory.ItemComponentCodec.decode(e.itemType, e.itemComponentData).potDecorations()));
        }
        welcomeSnapshot = List.copyOf(snap);
    }

    /**
     * [GLINT] 드랍 스택의 인챈트 와이어 값(없으면 null — 필드를 싣지 않는다). ID 16 이상은 성분 문자열(WCIC4)의
     * 확장 16진 값이 싣는다.
     */
    static Object enchantmentWire(ItemEntity item) {
        return com.gameexpert.engine.inventory.ItemComponentCodec.enchantmentWireOrNull(
                item.enchantments, item.itemComponentData);
    }

    private double blockFrictionAt(int x, int y, int z) {
        int block = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
        if (block == Blocks.BLUE_ICE) return 0.989;
        // [FROST-SOUL] 살얼음도 friction(0.98f) 이다.
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.FROSTED_ICE) return 0.98;
        // [ENCHANT-WIDE] 영혼 모래는 friction 기본값에 Entity.move 의 speedFactor(0.4f)가 곱해진다.
        if (block == Blocks.SOUL_SAND) {
            return MINECRAFT_DEFAULT_BLOCK_FRICTION * com.gameexpert.engine.mob.MobPhysics.SOUL_SAND_SPEED_FACTOR;
        }
        return MINECRAFT_DEFAULT_BLOCK_FRICTION;
    }

    private boolean survivesHeatAt(short itemType, double x, double y, double z) {
        return survivesHeatAt(itemType, x, y, z, fluidBlocks, fireLookup);
    }

    /** 매 틱 열 검사 핫패스: 점유 셀과 바로 아래 접촉 셀을 각각 한 번만 조회합니다. */
    static boolean survivesHeatAt(double x, double y, double z,
            Fluids.BlockLookup blocks, FireLookup fires) {
        return survivesHeatAt((short) 0, x, y, z, blocks, fires);
    }

    static boolean survivesHeatAt(short itemType, double x, double y, double z,
            Fluids.BlockLookup blocks, FireLookup fires) {
        int bx = floor(x), by = floor(y), bz = floor(z);
        int occupied = blocks.get(bx, by, bz);
        if (!deathDropSurvives(itemType, occupied, fires.burning(bx, by, bz))) return false;
        // 자연 화재는 가연 블록 자체에 상태가 붙으므로 그 위에 안착한 아이템의 접촉도 검사한다.
        return deathDropSurvives(itemType, blocks.get(bx, by - 1, bz),
                fires.burning(bx, by - 1, bz));
    }

    private void sendTo(PlayerTickState player, InventoryUpdate message) {
        var session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
        }
    }

    private void broadcast(Object message) {
        if (rt != null) {
            rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
        }
    }

    // 병합 반경 판정용 제곱거리(임계 비교 전 sqrt 회피). 호출부가 MERGE_RANGE^2 와 비교한다.
    private static double dist3dSq(ItemEntity a, ItemEntity b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

}
