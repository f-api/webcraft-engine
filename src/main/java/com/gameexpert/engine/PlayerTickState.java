package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages.PlayerPose;

/**
 * [제공코드] 접속 중 플레이어의 서버 권위 상태(틱 스레드 전용 필드 + 크로스스레드 읽기용 pose).
 *
 * <b>스레딩</b>: 위치/체력/추적기 필드는 <b>틱 스레드에서만 변경</b>합니다. pose(x,y,z,yaw,pitch)와 health는
 * volatile이라 welcome/playerMoves 구성 시 WS/틱 스레드가 안전하게 <i>읽기</i>만 합니다.
 * 낙하/잠수/용암/재생 누적기는 {@link EnvironmentSystem}이 사용합니다.
 */
public final class PlayerTickState {
    private final com.gameexpert.world.dimension.PortalDwell portalDwell =
            new com.gameexpert.world.dimension.PortalDwell();
    public com.gameexpert.world.dimension.PortalDwell portalDwell() { return portalDwell; }

    private com.gameexpert.state.service.PlayerDimensionIdentity dimensionIdentity;

    public void bindDimension(com.gameexpert.state.service.PlayerDimensionIdentity identity) {
        if (identity == null || dimensionIdentity != null) throw new IllegalStateException("dimension already bound");
        dimensionIdentity = identity;
    }

    public com.gameexpert.state.service.PlayerDimensionIdentity dimensionIdentity(long runtimeWorldId) {
        if (dimensionIdentity == null) return new com.gameexpert.state.service.PlayerDimensionIdentity(runtimeWorldId, runtimeWorldId, 0);
        if (dimensionIdentity.runtimeWorldId() != runtimeWorldId) throw new IllegalStateException("player in wrong runtime");
        return dimensionIdentity;
    }


    static final long SHIELD_RAISE_NANOS = 250_000_000L;
    static final long SHIELD_DISABLE_NANOS = 5_000_000_000L;
    /** Vanilla Bad Omen duration: 100 minutes at 20 MC TPS, expressed in 10 TPS authority ticks. */
    static final int BAD_OMEN_DURATION_TICKS = 60_000;
    /** [PHANTOM] 서버 틱(10 TPS) 하나가 담는 바닐라 game tick(20 TPS) 수. */
    static final int MC_TICKS_PER_SERVER_TICK = 2;
    /** 계약의 소비 사용 시간 32 game ticks(20 TPS) = 서버 16틱(10 TPS). */
    public static final long CONSUME_INTERVAL_TICKS = 16;
    /** 도착 지연·틱 경계 여유. */
    private static final long CONSUME_TOLERANCE_TICKS = 1;

    enum DirectionalDamageResult {
        REJECTED,
        BLOCKED,
        DAMAGED;

        boolean damaged() {
            return this == DAMAGED;
        }
    }

    private final Long playerId;
    private final String nickname;

    /** 마지막으로 승인한 소비의 틱 번호(입장 직후 첫 소비는 기다리지 않게 충분히 과거로 둔다). */
    private long lastConsumeTickNo = Long.MIN_VALUE / 4;

    // 서버 권위 핫바(9칸). 전투 피해·설치 소비·드랍 수납의 기준(틱 스레드 전용).
    private final PlayerInventory inventory;

    /**
     * [ENDER-SHULKER] 이 플레이어의 엔더 상자 27칸(틱 스레드 전용).
     *
     * <p>월드 안 <b>모든</b> 엔더 상자가 이 한 벌을 비춘다 — 좌표 키 저장소({@code ChestStorage})
     * 를 아예 타지 않으므로 상자를 부숴도 내용이 사라지지 않고, 같은 좌표를 다른 플레이어가
     * 열어도 자기 것을 본다. 컨테이너 규칙(넣기·꺼내기·병합·드랍)은 좌표 상자와 <b>완전히 같은</b>
     * {@link ChestInventory} 계약이고, 갈리는 것은 <b>어디서 오는가</b> 하나뿐이다.
     */
    private final ChestInventory enderChest = new ChestInventory(PlayerInventory.ENDER_CHEST_SLOTS);

    // ── pose (volatile: 크로스스레드 읽기 허용, 쓰기는 틱 스레드) ──
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile float yaw;
    private volatile float pitch;
    private boolean crouching;
    private volatile int health;

    private boolean dead;

    /** [RAID-REWARD] 전리품 악기 재사용 가능 tick. 스팸 방지 전용이라 영속하지 않는다. */
    private long trophyInstrumentReadyTick;

    // 클라이언트가 보낸 주손 사용 입력을 그대로 반영한다. 위치 이력·속도와 대조하지 않는다.
    private boolean shieldInput;
    private PlayerInventory.HandRef shieldHand;
    private long shieldRaisedAtNanos;
    private long shieldDisabledUntilNanos;
    /**
     * [DIAMOND-SHIELD] 이번 틱에 막아 낸 방패의 <b>종류</b> 목록. 개수만 세면 소리 방송이
     * 기본 방패 ID 를 하드코딩할 수밖에 없어 다이아 방패가 남의 소리를 낸다.
     */
    private final List<Short> shieldBlockedItems = new ArrayList<>();
    private final List<Short> brokenSoundItems = new ArrayList<>();

    // ── 침대(S2a): 개인 리스폰 지점(설정 시 침대 블록 좌표). 없으면 hasBedSpawn=false → 월드 스폰. ──
    private boolean hasBedSpawn;
    private int bedSpawnX;
    private int bedSpawnY;
    private int bedSpawnZ;

    // 밤 수면 상태(침대 상호작용으로 진입, 이동 시 취소). 전원 수면이면 틱 루프가 아침으로.
    private boolean sleeping;
    // [FURNITURE-26.3] 지금 자고 있는 침대의 **머리 칸** 좌표. 리스폰 지점과 별개다 —
    // 건초 침대는 리스폰을 설정하지 않으므로(바닐라 [B]) 수면 유효성·점유·기상 시 자멸을
    // bedSpawn 으로 판정하면 건초 침대 수면이 첫 틱에 취소된다. 그래서 "어느 침대에서 자는가"
    // 를 리스폰 지점과 분리해 이 셋이 소유한다. sleeping=false 면 의미가 없다.
    private int sleepBedX;
    private int sleepBedY;
    private int sleepBedZ;
    /**
     * [PHANTOM] 마지막 휴식 이후 경과 시간. 단위는 <b>MC 틱(20 TPS)</b> 이라 바닐라 임계
     * {@link com.gameexpert.engine.mob.Phantom#SLEEPLESS_TICKS_THRESHOLD} 72000 을 환산 없이
     * 그대로 비교한다 — 환산은 증가폭 한 곳({@link #advanceTimeSinceRest()} 의 +2)에만 있다.
     *
     * <p>바닐라 원문은 {@code docs/research/mc-phantom-insomnia.md} §3 이다:
     * {@code Player#tick} 이 <b>자고 있지 않은</b> 매 game tick 마다 {@code Stats.TIME_SINCE_REST}
     * 를 +1 하고, {@code ServerPlayer#startSleeping}(침대 <b>진입</b>)과 {@code ServerPlayer#die}
     * 가 0 으로 되돌린다. 잠들어 밤을 넘겼는지는 보지 않는다.
     *
     * <p>가입 시각 기준 0 에서 시작하고, 재접속은 {@code player_world_states} 의
     * nullable 컬럼에서 이어받는다(컬럼이 없던 세이브는 0).
     */
    private long timeSinceRestMcTicks;
    private int sleepInStrawBed;

    public int sleepInStrawBed() { return sleepInStrawBed; }

    public void restoreStrawBedSleepCount(int count) {
        if (count < 0) throw new IllegalArgumentException("negative straw bed statistic");
        sleepInStrawBed = count;
    }
    private BlockPos openChest;
    private BlockPos openChestPartner;
    private BlockPos openFurnace;
    /** [SURV-X] 서버가 확정한 열린 인챈트 테이블 좌표(없으면 null). */
    private BlockPos openEnchanting;
    /**
     * [MOUNT] 열려 있는 몹 화물 패널의 대상 mobId(없으면 0). 좌표 컨테이너와 달리 대상이
     * 스스로 움직이므로 사거리는 매 이동·매 틱 다시 본다.
     */
    private long openMobCargo;
    /** Live authority-owned menu identity for one generated Chest Minecart. */
    private long generatedCargoSessionSequence;
    private GeneratedEntityCargoSession openGeneratedEntityCargo;
    /** One in-flight H12f equipment settlement, fenced by both durable revisions. */
    private long generatedEquipmentSettlementSequence;
    private GeneratedEntityEquipmentSettlement generatedEquipmentSettlement;
    private PlayerAction.CraftStation openCraftingStation;
    private BlockPos openCraftingTable;
    /** Monotonic identity for the currently open crafting menu on this live connection. */
    private long craftingSessionSequence;
    private long openCraftingSessionId;
    private Long openCraftingRequestId;
    /** A deferred open belongs to this exact live player and request object. */
    private PlayerAction.OpenCrafting pendingCraftingOpen;
    private String anvilRename;
    private com.gameexpert.engine.inventory.LoomRules.Pattern loomSelection;
    private BlockPos openLectern;
    private Long openLecternRequestId;
    private PlayerInventory.HandRef openBookHand;
    private PlayerInventory.StackSnapshot openBookStack;
    private Long openBookRequestId;

    // ── 겉날개 활공(이동은 클라 권위이므로 클라가 보고한 사실을 그대로 보존한다) ──
    private boolean gliding;
    /** 활공 중 누적 서버 틱. 10틱(=1초)마다 겉날개 내구 1을 소비한다. */
    private int glideWearTicks;
    /**
     * 마지막으로 클라이언트에 보낸 겉날개 내구도. 클라이언트도 미착용 0 에서 시작하므로
     * 초기값 0 은 "이미 같은 상태를 알고 있다"는 뜻이고, 실제로 달라질 때만 메시지를 보낸다.
     */
    private int sentElytraDurability;
    /**
     * [HUD-VANILLA] 마지막으로 보낸 방어 점수·흡수. 둘 다 0 에서 시작하므로 방어구를 입은 채
     * 접속한 플레이어는 첫 틱에 한 번 갱신을 받는다 — welcome 이 실어 보내는 값은 저장 스냅샷에서
     * 온 것이라, 이 한 번이 살아 있는 권위 값과의 일치를 보장한다. 이후로는 실제로 달라질 때만 보낸다.
     */
    private int sentArmorPoints;
    private int sentAbsorption;

    // ── 낙하 추적 ──
    private boolean airborne;
    /** [ENCHANT-WIDE] 휩쓸기 속도 판정: 마지막 이동 틱과 그 틱의 수평 이동 합(블록). */
    private long horizontalStepTick = Long.MIN_VALUE;
    private double horizontalStepDistance;
    private double fallPeakY;
    private boolean resetFallReferenceOnAir;
    private double lastFallPoseY;
    private boolean descendedSinceFallReference;
    private boolean glideFallSampleActive;
    private boolean glideFallSampleMoved;
    private double glideFallSampleStartY;
    private double glideFallSampleLastY;

    // ── [TURTLE] 밟기 추적 ──
    // [A] TurtleEggBlock.stepOn 은 "이동 중 딛는 칸이 바뀔 때" 불린다. 이 저장소의 환경 층은
    // 틱마다 자세만 받으므로 **지난 틱에 딛고 선 칸**을 들고 있다가 달라졌을 때 한 번 굴린다
    // (client StandalonePlayerVitals.observeTurtleEggStep 과 같은 옮김). 아직 한 번도 접지한
    // 적이 없으면 lastStepCellKnown 이 거짓이라 굴리지 않는다 — 접속 직후 서 있던 알은
    // 지나간 것이 아니다.
    private boolean lastStepCellKnown;
    private int lastStepCellX;
    private int lastStepCellY;
    private int lastStepCellZ;

    // ── 잠수/용암 누적기(틱 단위) ──
    private int submergedTicks;
    private int drownAccum;
    private int lavaAccum;
    private int powderSnowTicks;
    private int freezeDamageAccum;
    private int hurtProtectionTicks;
    /** 26.3 WindCharge item use_cooldown 0.5s, represented at the 10 TPS authority cadence. */
    private int windChargeCooldownTicks;
    private int previousHurtDamage;

    // ── 허기(SURV-H). 자연 회복·굶주림은 전부 이 상태가 게이팅한다. ──
    // saturation·exhaustion 은 부동소수 누적으로 양판이 갈리지 않도록 HungerRules 고정소수(milli) 정수로 둔다.
    private int food = HungerRules.INITIAL_FOOD;
    private int saturationMilli = HungerRules.INITIAL_SATURATION_MILLI;
    private int exhaustionMilli;
    // 0.01/블록의 sub-milli를 이동 메시지 경계에서 버리지 않도록 1/100 milli 나머지를 이월한다.
    private int movementExhaustionRemainder;
    // 바닐라 FoodData.tickTimer 를 서버 10 TPS 틱 단위로 환산한 값. 세 분기가 공유하고 else 에서 0으로 돌아간다.
    private int foodTickTimer;
    // 포화 회복은 1회 회복량이 1 HP 미만일 수 있어 소수분을 milli 로 이월한다.
    private int regenHealMilli;
    private boolean foodDirty;
    // [SURV-X] 누적 경험치. 레벨·바 진행은 XpRules 로 파생하므로 정본은 이 정수 하나다.
    private int xpTotal;
    private boolean xpDirty;
    // [SURV-X] 인챈트 제안 추첨 시드. 저장하지 않고 접속·인챈트 성공마다 재추첨한다.
    private int enchantSeed;

    // ── 황금사과 상태 효과(프로젝트 10 TPS, MC 시간은 20 TPS 단위로 누적) ──
    private static final int GOLDEN_APPLE_REGEN_MC_TICKS = 5 * 20;
    private static final int GOLDEN_APPLE_ABSORPTION_TICKS = 2 * 60 * 10;
    private static final int REGEN_II_INTERVAL_MC_TICKS = 25;
    // ── [GOLD-FOOD] 마법이 부여된 황금 사과. 바닐라 네 효과의 원문 지속시간을 옮긴다.
    /** 재생 II 0:20 = 400 MC 틱. 황금사과(5초=100)의 네 배다. */
    private static final int ENCHANTED_GOLDEN_APPLE_REGEN_MC_TICKS = 20 * 20;
    /** 흡수 IV 2:00. 물리 축은 <b>권위 틱(10 TPS)</b> 단위라 황금사과와 같은 1200 이다. */
    private static final int ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS = 2 * 60 * 10;
    /** 흡수 IV = 레벨당 4 점 × 4 = 16 점(하트 8). 황금사과의 흡수 I 은 4 점이다. */
    private static final int ENCHANTED_GOLDEN_APPLE_ABSORPTION_POINTS = 16;
    /** 화염 저항 I · 저항 I 5:00 = 3000 권위 틱(10 TPS). */
    private static final int ENCHANTED_GOLDEN_APPLE_LONG_EFFECT_TICKS = 5 * 60 * 10;
    /** Totem component: regeneration II 900 MC ticks. */
    static final int TOTEM_REGEN_MC_TICKS = 900;
    /** Totem component: absorption II 100 MC ticks = 50 authority ticks. */
    static final int TOTEM_ABSORPTION_TICKS = 50;
    /** Totem component: fire resistance I 800 MC ticks = 400 authority ticks. */
    static final int TOTEM_FIRE_RESISTANCE_TICKS = 400;
    static final int TOTEM_ABSORPTION_POINTS = 8;
    private int regenerationMcTicksRemaining;
    private int regenerationMcTickAccum;
    private int absorptionTicksRemaining;
    private int absorptionPoints;
    private int fireResistanceTicksRemaining;

    // ── 상태이상(FX A1): 독·감속·나약. 즉발(고통)은 목록에 남지 않는다. ──
    private final StatusEffects statusEffects = new StatusEffects();

    // ── 화상(§11.5-S3f) ──
    // fireTicks>0 이면 불타는 중(용암 노출 = 최대치로 재설정, 매 틱 −1). fireAccum 은 1초당 1 화상 피해 타이밍.
    private int fireTicks;
    private int fireAccum;

    // ── 주기 저장(SAVE_INTERVAL) dirty 가드 ──
    // 성공한 DB 커밋의 revision만 승인한다. 제출 직후 깨끗하다고 표시하면 실패한 비동기 저장을 재시도할 수 없다.
    // 초기 stateRevision=1·savedInvRevision=-1 이라 접속 후 첫 주기 저장은 항상 수행된다.
    private long stateRevision = 1;
    private long savedStateRevision;
    private long savedInvRevision = -1;
    private boolean persistenceInFlight;

    // ── 이번 틱 브로드캐스트 플래그 ──
    private boolean poseDirty;
    private short broadcastSelectedItemId;
    private WideEnchantments broadcastSelectedEnchantments = WideEnchantments.EMPTY;
    /** [ARMOR-TRIM] 마지막으로 방송한 착용 갑옷 네 칸(종류 + 성분). 바뀌면 pose 를 다시 싣는다. */
    private String broadcastArmorSignature = "";
    private boolean healthDirty;
    private boolean justDamaged;
    private String healthCause;
    private String damageCause;
    private String hurtKiller; // 사망 메시지용 가해 몹 종류(몹 근접일 때만, 그 외 null)
    private double hurtKbX;
    private double hurtKbY;
    private double hurtKbZ;
    private double hurtKbBonusX;
    private double hurtKbBonusZ;
    private boolean justDied;
    private boolean justRespawned;
    private boolean respawnRequested;
    private int totemUseSounds;
    private final List<BlockPos> shelfMushroomBounceSounds = new ArrayList<>();
    private final List<BlockFallSound> blockFallSounds = new ArrayList<>();
    /**
     * QA 전용 완전 무적({@code game.qa-godmode}). 기본 false 이고 QA 서버에서만 켜집니다.
     *
     * <p>{@code game.qa-invulnerable} 은 환경 피해(EnvironmentSystem)만 껐기 때문에 몹이 도는
     * 월드에서 장시간 QA 를 돌면 근접·투사체·폭발로 계속 죽었다. 이 플래그는 그 구멍을 메우되
     * <b>검사 지점을 {@link #damage(int, String, String)} 한 곳</b>으로 둔다 — 전투(CombatSystem ·
     * ProjectileSim · 폭발)든 환경이든 플레이어 체력이 줄어드는 경로는 전부 이 메서드를 거치므로,
     * 프로덕션 전투 코드에는 QA 분기가 하나도 생기지 않는다.
     */
    private boolean qaGodmode;

    public PlayerTickState(Long playerId, String nickname,
            double x, double y, double z, float yaw, float pitch, int health) {
        this(playerId, nickname, x, y, z, yaw, pitch, health, new PlayerInventory());
    }

    public PlayerTickState(Long playerId, String nickname,
            double x, double y, double z, float yaw, float pitch, int health, PlayerInventory inventory) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.inventory = inventory;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = Math.max(0, Math.min(20, health));
        this.dead = this.health <= 0;
        this.fallPeakY = y;
        this.lastFallPoseY = y;
        this.broadcastSelectedItemId = selectedItemId();
        this.broadcastSelectedEnchantments = selectedEnchantments();
        // 접속 직후 한 번은 반드시 방송한다. 아바타는 PlayerPose 로만 만들어지므로,
        // 이게 없으면 가만히 서 있는 접속자가 다른 화면에 영영 안 보인다.
        this.poseDirty = true;
    }

    public Long playerId() {
        return playerId;
    }

    public String nickname() {
        return nickname;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean crouching() {
        return crouching;
    }

    public int health() {
        return health;
    }

    public boolean isDead() {
        return dead;
    }

    /** 서버 권위 핫바(틱 스레드 전용). */
    public PlayerInventory inventory() {
        return inventory;
    }

    /**
     * [ENDER-SHULKER] 이 플레이어의 엔더 상자 27칸(틱 스레드 전용). 좌표와 무관하게 언제나
     * 같은 인스턴스라, 두 위치의 엔더 상자를 번갈아 열어도 같은 내용을 본다.
     */
    public ChestInventory enderChest() {
        return enderChest;
    }

    /**
     * [ENDER-SHULKER] 저장된 엔더 상자 27칸을 접속 시 한 번 복원한다.
     *
     * <p>{@link #restoreTimeSinceRest} 와 같은 이유로 {@code addPlayer} 서명을 넓히지 않고
     * 접속 직후 따로 호출한다 — 그 서명은 이미 네 겹 호환 오버로드 사슬이다.
     * 빈 칸은 건너뛰므로 이 밴드의 행이 없던 옛 세이브는 빈 27칸으로 남는다.
     */
    public void restoreEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds) {
        restoreEnderChest(itemTypes, counts, durabilities, enchantments, mapIds,
                new int[itemTypes.length], new String[itemTypes.length],
                new String[itemTypes.length]);
    }

    /** [SHULKER-CONTENTS] 엔더 상자에 넣어 둔 셜커 상자의 27칸 참조 ID 까지 되돌린다. */
    public void restoreEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds) {
        restoreEnderChest(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                new String[itemTypes.length], new String[itemTypes.length]);
    }

    public void restoreEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData) {
        for (int slot = 0; slot < itemTypes.length && slot < enderChest.slots(); slot++) {
            if (itemTypes[slot] == PlayerInventory.EMPTY || counts[slot] <= 0) continue;
            enderChest.restoreSlot(slot, itemTypes[slot], counts[slot],
                    PlayerInventory.isDurable(itemTypes[slot]) ? durabilities[slot] : null,
                    enchantments[slot] == 0 ? null : enchantments[slot],
                    mapIds[slot] == 0 ? null : mapIds[slot],
                    shulkerIds[slot] == 0 ? null : shulkerIds[slot], bucketMobData[slot],
                    itemComponentData[slot]);
        }
    }

    public PlayerPose pose() {
        // [UTILITY] 착용 갑옷과 장식을 타 플레이어 3인칭에 싣는다(모두 비면 필드 생략).
        java.util.List<Short> armorIds = new java.util.ArrayList<>(4);
        java.util.List<com.gameexpert.ws.dto.WsMessages.TrimComponent> trims =
                new java.util.ArrayList<>(4);
        // [EQUIPMENT] 같은 네 칸의 인챈트(갑옷 광택)와 염색 가죽 색(DyedItemColor)도 싣는다(모두 없으면 생략).
        java.util.List<Object> armorEnchantments = new java.util.ArrayList<>(4);
        java.util.List<Integer> leatherColors = new java.util.ArrayList<>(4);
        boolean anyArmor = false;
        boolean anyTrim = false;
        boolean anyEnchanted = false;
        boolean anyDyed = false;
        for (com.gameexpert.engine.inventory.ArmorSlot slot
                : com.gameexpert.engine.inventory.ArmorSlot.values()) {
            PlayerInventory.StackSnapshot stack = inventory.equippedStack(slot);
            armorIds.add(stack.itemType());
            anyArmor |= stack.itemType() != PlayerInventory.EMPTY;
            com.gameexpert.engine.inventory.ItemComponentData.ArmorTrim trim =
                    stack.itemType() == PlayerInventory.EMPTY ? null : stack.itemComponents().trim();
            trims.add(com.gameexpert.ws.dto.WsMessages.TrimComponent.of(trim));
            anyTrim |= trim != null;
            com.gameexpert.engine.enchant.WideEnchantments enchantments =
                    stack.itemType() == PlayerInventory.EMPTY
                            ? com.gameexpert.engine.enchant.WideEnchantments.EMPTY
                            : inventory.equippedWideEnchantments(slot);
            armorEnchantments.add(enchantments.wireValue());
            anyEnchanted |= !enchantments.isEmpty();
            Integer leather = stack.itemType() == PlayerInventory.EMPTY ? null
                    : stack.itemComponents().leatherColor();
            leatherColors.add(leather);
            anyDyed |= leather != null;
        }
        return new PlayerPose(nickname, x, y, z, yaw, pitch, fireTicks > 0, selectedItemId(),
                inventory.offhand().itemType(), selectedEnchantments().wireValue(),
                inventory.offhand().wideEnchantments().wireValue(),
                stuckArrowCount == 0 ? null : stuckArrowCount,
                anyArmor ? java.util.List.copyOf(armorIds) : null,
                anyTrim ? java.util.Collections.unmodifiableList(trims) : null,
                anyEnchanted ? java.util.Collections.unmodifiableList(armorEnchantments) : null,
                anyDyed ? java.util.Collections.unmodifiableList(leatherColors) : null,
                handBannerPatterns(PlayerInventory.Hand.MAIN),
                handBannerPatterns(PlayerInventory.Hand.OFFHAND), spearUsing ? Boolean.TRUE : null,
                inventory.stack(PlayerInventory.Hand.MAIN).itemComponents().potDecorations(),
                inventory.stack(PlayerInventory.Hand.OFFHAND).itemComponents().potDecorations(), crouching);
    }

    /**
     * [SHIELD-PATTERN] 손에 든 스택의 무늬 층(무늬 있는 방패·현수막), 무늬가 없으면 null(필드 생략). 정적판
     * `standaloneHandBannerPatterns` 와 같은 투영이다.
     */
    java.util.List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> handBannerPatterns(
            PlayerInventory.Hand hand) {
        PlayerInventory.StackSnapshot stack = inventory.stack(hand);
        if (stack.itemType() == PlayerInventory.EMPTY || stack.itemComponentData() == null) {
            return null;
        }
        java.util.List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> layers =
                com.gameexpert.engine.inventory.ItemComponentCodec
                        .decode(stack.itemType(), stack.itemComponentData()).bannerPatterns().stream()
                        .map(layer -> new com.gameexpert.ws.dto.WsMessages.BannerPatternLayer(
                                layer.pattern().wireName(), layer.color()))
                        .toList();
        return layers.isEmpty() ? null : layers;
    }

    // ── [SPEAR-KINETIC] 창 쓰기(바닐라 isUsingItem + KINETIC_WEAPON) — 원격 3인칭 창 자세 ──
    private boolean spearUsing;

    boolean spearUsing() {
        return spearUsing;
    }

    /** 창 돌진 쓰기 시작·끝. 바뀌면 playerMoves 로 퍼져 원격 3인칭이 창 자세(SpearAnimations)를 취한다. */
    void setSpearUsing(boolean using) {
        if (spearUsing == using) return;
        spearUsing = using;
        poseDirty = true;
    }

    /** [GLINT] 선택 슬롯 스택의 43종 인챈트. 바뀌면 pose 를 다시 방송한다(원격 손 광택). */
    private WideEnchantments selectedEnchantments() {
        return inventory.wideEnchantments(inventory.selectedSlot());
    }

    // ── [ARROW-GROUND] 몸에 박힌 화살(바닐라 LivingEntity DATA_ARROW_COUNT_ID · removeArrowTime) ──
    /** 박힌 화살 수. 바닐라도 저장하지 않으며 새 몸(리스폰)은 0 에서 시작한다. */
    private int stuckArrowCount;
    /** 다음 한 발이 빠질 때까지 남은 MC 틱({@code removeArrowTime}). */
    private int removeArrowMcTicks;

    int stuckArrowCount() {
        return stuckArrowCount;
    }

    /** {@code setArrowCount(getArrowCount() + 1)}. 바뀐 수는 playerMoves 로 퍼진다. */
    void addStuckArrow() {
        stuckArrowCount++;
        poseDirty = true;
    }

    /**
     * {@code LivingEntity.tick}: 수가 있으면 {@code removeArrowTime <= 0} 일 때 {@code 20 × (30 − 수)} 로
     * 다시 세고, MC 틱마다 1 씩 줄여 0 이 되면 한 발을 뺀다. 권위 1틱 = MC 2틱이다.
     */
    void tickStuckArrows() {
        for (int mcTick = 0; mcTick < 2; mcTick++) {
            if (stuckArrowCount <= 0) return;
            if (removeArrowMcTicks <= 0) removeArrowMcTicks = 20 * (30 - stuckArrowCount);
            removeArrowMcTicks--;
            if (removeArrowMcTicks <= 0) {
                stuckArrowCount--;
                poseDirty = true;
            }
        }
    }

    private short selectedItemId() {
        return inventory.itemType(inventory.selectedSlot());
    }

    /** 화상 여부(fireTicks>0). welcome/playerMoves 브로드캐스트로 전 클라 전파(§11.5). */
    public boolean onFire() {
        return fireTicks > 0;
    }

    // ── 이동 ──
    void applyPose(double nx, double ny, double nz, float nyaw, float npitch, boolean crouching) {
        applyPose(nx, ny, nz, nyaw, npitch, crouching, false);
    }

    void applyPose(double nx, double ny, double nz, float nyaw, float npitch, boolean crouching,
            boolean gliding) {
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.yaw = nyaw;
        this.pitch = npitch;
        this.crouching = crouching;
        this.gliding = gliding;
        this.poseDirty = true;
        this.stateRevision++;
    }

    // 서버가 위치를 강제(리스폰)할 때.
    void forcePose(double nx, double ny, double nz, float nyaw, float npitch) {
        applyPose(nx, ny, nz, nyaw, npitch, false);
        resetFallTracking(ny);
    }

    /** 클라이언트가 보고한 현재 활공 여부. */
    public boolean gliding() {
        return gliding;
    }

    void setGliding(boolean value) {
        this.gliding = value;
    }

    /**
     * 흉갑 부위 겉날개 내구도가 마지막 전송값과 달라졌으면 새 값을, 같으면 -1 을 돌려준다.
     * 착용 장비는 프로토콜에 실리지 않으므로 이 값이 클라이언트 활공 자격 판정의 유일한 입력이다.
     */
    int consumeElytraDurabilityChange() {
        int current = inventory.equippedType(ArmorSlot.CHESTPLATE) == PlayerInventory.ELYTRA
                ? inventory.equippedDurability(ArmorSlot.CHESTPLATE)
                : 0;
        if (current == sentElytraDurability) return -1;
        sentElytraDurability = current;
        return current;
    }

    /** 흉갑 부위 겉날개 내구도(미착용·활공 불가면 0). 전송 여부와 무관한 현재값이다. */
    int elytraDurability() {
        return inventory.equippedType(ArmorSlot.CHESTPLATE) == PlayerInventory.ELYTRA
                ? inventory.equippedDurability(ArmorSlot.CHESTPLATE)
                : 0;
    }

    /**
     * [HUD-VANILLA] 방어 점수·흡수 중 하나라도 마지막 전송값과 달라졌으면 true 를 돌려주고
     * 전송 커서를 지금 값으로 옮긴다. 겉날개 내구와 같은 개인 메시지에 함께 실린다.
     */
    boolean consumeDerivedVitalsChange() {
        int armorPoints = inventory.armorPoints();
        if (armorPoints == sentArmorPoints && absorptionPoints == sentAbsorption) return false;
        sentArmorPoints = armorPoints;
        sentAbsorption = absorptionPoints;
        return true;
    }


    /**
     * 활공 중 누적 틱을 한 틱 진행하고 겉날개 마모 1회가 도래했는지 알린다.
     * 바닐라는 20 TPS 기준 20틱(1초)마다 내구 1을 소비하므로 서버 10 TPS 에서는 10틱이다.
     */
    boolean advanceGlideWear() {
        if (!gliding) {
            glideWearTicks = 0;
            return false;
        }
        glideWearTicks++;
        if (glideWearTicks < GLIDE_WEAR_INTERVAL_TICKS) return false;
        glideWearTicks = 0;
        return true;
    }

    /** 활공 1초에 해당하는 서버 틱 수(10 TPS). */
    static final int GLIDE_WEAR_INTERVAL_TICKS = 10;

    /**
     * 바닐라 {@code Entity.checkSlowFallDistance}: 활공 중 수직 속도가 -0.5 블록/틱(20 TPS)보다
     * 완만하면 누적 낙하 거리를 1.0 블록으로 잘라 완만한 활공이 착지 피해를 만들지 않게 한다.
     * 서버 환경 틱마다 모은 구간의 하강 한도는 1.0 블록이다. 추가 이동 메시지는 구간을 나누지 않는다.
     */
    static final double GLIDE_FALL_RESET_DESCENT = 1.0;
    /** 잘라 낸 뒤 남는 낙하 거리(바닐라 fallDistance = 1.0F). */
    static final double GLIDE_CLAMPED_FALL_DISTANCE = 1.0;

    /** 이동은 한 환경 틱의 활공 구간에 모으고, 속도 판정은 구간을 닫을 때 한 번만 한다. */
    void observeGlideAirborne(double y) {
        if (!glideFallSampleActive) {
            glideFallSampleActive = true;
            glideFallSampleStartY = lastFallPoseY;
            glideFallSampleLastY = lastFallPoseY;
            glideFallSampleMoved = false;
        }
        observeGlideFallSample(y);
        observeAirborne(y);
    }

    /** 착지·활공 종료는 환경 틱을 기다리지 않고 남은 구간을 피해 계산 전에 정산한다. */
    void finishGlideFallSample(double y, boolean continuing) {
        if (!glideFallSampleActive) return;
        observeGlideFallSample(y);
        if (glideFallSampleMoved
                && glideFallSampleStartY - y <= GLIDE_FALL_RESET_DESCENT
                && fallPeakY - y > GLIDE_CLAMPED_FALL_DISTANCE) {
            fallPeakY = y + GLIDE_CLAMPED_FALL_DISTANCE;
        }
        glideFallSampleActive = continuing;
        glideFallSampleStartY = y;
        glideFallSampleLastY = y;
        glideFallSampleMoved = false;
    }

    private void observeGlideFallSample(double y) {
        glideFallSampleMoved |= Math.abs(glideFallSampleLastY - y) > 1e-6;
        glideFallSampleLastY = y;
    }

    private void resetGlideFallSample() {
        glideFallSampleActive = false;
        glideFallSampleMoved = false;
    }

    // ── 낙하 추적기 접근 ──
    boolean airborne() {
        return airborne;
    }

    /** [ENCHANT-WIDE] 이동 pose 한 건의 수평 이동을 그 서버 틱에 누적한다. */
    void observeHorizontalStep(double distance, long tickNo) {
        if (tickNo != horizontalStepTick) {
            horizontalStepTick = tickNo;
            horizontalStepDistance = 0.0;
        }
        if (distance > 0.0) horizontalStepDistance += distance;
    }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code getKnownMovement().horizontalDistance()} 에 해당하는 최근 수평 속도
     * (블록/MC틱). 이 틱이나 직전 틱에 이동 pose 가 없으면 서 있는 것으로 본다. 한 서버 틱은 MC 2틱이다.
     */
    double recentHorizontalSpeedPerMcTick(long tickNo) {
        if (horizontalStepTick == Long.MIN_VALUE || tickNo - horizontalStepTick > 1) return 0.0;
        return horizontalStepDistance / 2.0;
    }

    void setAirborne(boolean value) {
        this.airborne = value;
        if (!value) resetGlideFallSample();
    }

    /**
     * 상승이 이 높이를 넘으면 점프로는 설명되지 않는다고 보고 낙하 기준을 그 지점으로 옮긴다.
     * 점프 최고점은 JUMP_SPEED 8.4 / GRAVITY 28 에서 약 1.26블록이다(client physics/constants.ts).
     */
    static final double ASCENT_REBASE_MARGIN = 1.5;

    void startAirborne(double y) {
        this.airborne = true;
        if (resetFallReferenceOnAir) {
            this.fallPeakY = y;
            this.resetFallReferenceOnAir = false;
        }
        this.descendedSinceFallReference = y < fallPeakY - 1e-6;
        this.lastFallPoseY = y;
    }

    /**
     * 일반 점프의 상승분은 낙하 높이에 더하지 않되, 실제 하강 뒤 위로 밀린 경우에는 그 상승부터
     * 새 낙하 구간으로 삼는다.
     */
    void observeAirborne(double y) {
        if (!airborne) {
            startAirborne(y);
            return;
        }
        if (y < lastFallPoseY - 1e-6) {
            descendedSinceFallReference = true;
        } else if (
            y > lastFallPoseY + 1e-6 &&
            (descendedSinceFallReference || y > fallPeakY + ASCENT_REBASE_MARGIN)
        ) {
            fallPeakY = y;
        }
        lastFallPoseY = y;
    }

    /** 점프 상승분을 제외하는 낙하 피해 기준과 달리, 실제 하강을 치명타에 반영한다. */
    boolean canCriticalStrike(boolean sprinting) {
        return airborne && (descendedSinceFallReference || fallPeakY > y) && !sprinting;
    }

    double fallPeakY() {
        return fallPeakY;
    }

    /**
     * [MACE] 권위가 아는 현재 낙하 거리(바닐라 {@code fallDistance}). 공중이면 낙하 기준점에서 지금
     * 높이까지 내려온 거리, 땅 위(또는 물·사다리로 추적이 끝난 상태)면 0 이다. 착지 피해·치명타가 읽는
     * 같은 낙하 추적기({@link #fallPeakY})를 그대로 쓴다.
     */
    double currentFallDistance() {
        return airborne ? Math.max(0.0, fallPeakY - y) : 0.0;
    }

    /**
     * [MACE] {@code MaceItem.postHurtEnemy → resetFallDistance} 와 {@code setIgnoreFallDamageFromCurrentImpulse}
     * (충돌 지점 = 지금 위치): 낙하 기준점을 지금 높이로 옮겨 이미 떨어진 거리의 착지 피해를 지운다.
     * 공중 상태는 유지하므로 여기서부터 다시 떨어지는 거리는 평소대로 센다.
     */
    void resetFallDistanceAfterSmash() {
        resetGlideFallSample();
        this.fallPeakY = y;
        this.lastFallPoseY = y;
        this.descendedSinceFallReference = false;
        this.resetFallReferenceOnAir = false;
    }

    /**
     * [TURTLE] 딛고 선 칸을 기록하고 <b>지난 틱과 달라졌는지</b>를 돌려준다. 제자리에 선 채로는
     * 거짓이라 밟기 굴림이 일어나지 않는다(바닐라도 그렇다).
     */
    boolean observeStepCell(int x, int y, int z) {
        boolean moved = lastStepCellKnown
                && (x != lastStepCellX || y != lastStepCellY || z != lastStepCellZ);
        lastStepCellKnown = true;
        lastStepCellX = x;
        lastStepCellY = y;
        lastStepCellZ = z;
        return moved;
    }

    void setFallPeakY(double value) {
        this.fallPeakY = value;
    }

    /** 착지·물·사다리·강제 이동에서 낙하 구간을 끝내고 현재 높이로 peak를 재설정한다. */
    void resetFallTracking(double y) {
        resetGlideFallSample();
        this.airborne = false;
        this.fallPeakY = y;
        this.resetFallReferenceOnAir = false;
        this.lastFallPoseY = y;
        this.descendedSinceFallReference = false;
    }

    /** 물·사다리 안의 이동은 무시하고, 빠져나온 첫 공중 위치부터 새 낙하 구간을 시작한다. */
    void resetFallTrackingFromMedium(double y) {
        resetGlideFallSample();
        this.airborne = false;
        this.fallPeakY = y;
        this.resetFallReferenceOnAir = true;
        this.lastFallPoseY = y;
        this.descendedSinceFallReference = false;
    }

    // ── 잠수/용암/재생 누적기 접근 ──
    /** [ENCHANT-WIDE] 호흡으로 반 칸(공기 1점)만 줄어든 이월분(0/1). 영속하지 않는 1틱짜리 나머지다. */
    private int respirationAirRemainder;

    int respirationAirRemainder() {
        return respirationAirRemainder;
    }

    void setRespirationAirRemainder(int value) {
        this.respirationAirRemainder = value;
    }

    int submergedTicks() {
        return submergedTicks;
    }

    void setSubmergedTicks(int value) {
        this.submergedTicks = value;
    }

    int drownAccum() {
        return drownAccum;
    }

    void setDrownAccum(int value) {
        this.drownAccum = value;
    }

    int lavaAccum() {
        return lavaAccum;
    }

    void setLavaAccum(int value) {
        this.lavaAccum = value;
    }

    int powderSnowTicks() {
        return powderSnowTicks;
    }

    void setPowderSnowTicks(int value) {
        powderSnowTicks = value;
    }

    int freezeDamageAccum() {
        return freezeDamageAccum;
    }

    void setFreezeDamageAccum(int value) {
        freezeDamageAccum = value;
    }

    // ── 화상 타이머(틱 스레드) ──
    int fireTicks() {
        return fireTicks;
    }

    /**
     * 화상 타이머 설정(0 미만은 0). onFire 상태(fireTicks>0)가 토글되면 poseDirty 를 세워
     * 정지 중인 플레이어라도 playerMoves 로 불 켜짐/꺼짐이 즉시 전파되게 한다(§11.5 브로드캐스트).
     */
    void setFireTicks(int value) {
        int next = Math.max(0, value);
        if (this.fireTicks == next) return;
        boolean before = this.fireTicks > 0;
        boolean after = next > 0;
        this.fireTicks = next;
        this.stateRevision++;
        if (before != after) {
            this.poseDirty = true;
        }
    }

    int fireAccum() {
        return fireAccum;
    }

    void setFireAccum(int value) {
        int next = Math.max(0, value);
        if (this.fireAccum == next) return;
        this.fireAccum = next;
        this.stateRevision++;
    }

    /** 접속 복원은 저장된 화상 피해 위상을 되감지 않고 정확히 이어 쓴다. */
    void restoreFireState(int fireTicks, int fireAccum) {
        this.fireTicks = Math.max(0, fireTicks);
        this.fireAccum = Math.max(0, fireAccum);
        this.poseDirty = true;
    }

    /** QA 무적을 켠다. 서버 기동 프로퍼티에서만 오고 런타임 메시지로는 바뀌지 않는다. */
    void setQaGodmode(boolean enabled) {
        this.qaGodmode = enabled;
    }

    boolean qaGodmode() {
        return qaGodmode;
    }

    // ── 체력 변경(틱 스레드) ──
    boolean damage(int amount, String cause) {
        return damage(amount, cause, null);
    }

    boolean damage(int amount, String cause, String killer) {
        // [QA-GODMODE] 유일한 플래그 검사 지점. 피해가 "적용되지 않았다"(false)로 보이므로
        // 호출부는 평소의 무피격(무적 프레임·이미 사망) 경로와 똑같이 동작한다.
        if (qaGodmode) return false;
        if (dead || amount <= 0) {
            return false;
        }
        // [BRIMSTONE] 화염 저항 게이트. 토템 성분(fireResistanceTicksRemaining)과 물약
        // 상태이상(StatusEffect.FIRE_RESISTANCE)이 **같은 판정 한 곳**을 연다 — 둘이 서로
        // 다른 자리에서 피해를 걷어내면 겹쳤을 때 판정이 두 벌로 갈린다.
        if ((fireResistanceTicksRemaining > 0
                        || statusEffects.has(StatusEffect.FIRE_RESISTANCE))
                && (cause.equals("on_fire") || cause.equals("in_fire") || cause.equals("lava"))) {
            return false;
        }
        int applied = amount;
        if (hurtProtectionTicks > 0) {
            if (amount <= previousHurtDamage) {
                return false;
            }
            applied = amount - previousHurtDamage;
            previousHurtDamage = amount;
            // 차액 피해는 보호 기간을 연장하지 않는다.
        } else {
            previousHurtDamage = amount;
            hurtProtectionTicks = CombatRules.HURT_COOLDOWN_TICKS;
        }
        // MC 방어도/강인도 공식은 원 피해에 적용한다. 현재 프로토콜 HP가 정수이므로 실제 감소량의 양의
        // 소수는 기존 낙하 피해와 같은 방식으로 올림하되, 공식 자체는 CombatRules의 double 순수 함수로 보존한다.
        // [LIGHTNING] 낙뢰는 방어도로 줄어든다. 바닐라 {@code lightning_bolt} 피해 타입은
        // 태그 {@code minecraft:bypasses_armor} 에 들어 있지 않아 {@code Entity#thunderHit} 의
        // 5.0F 가 일반 피해와 같이 방어도·보호를 모두 통과한다(몹 쪽 hurtByLightning 도 같다).
        boolean armorReducible = cause.equals("falling_stalactite") || cause.equals("mob") || cause.equals("arrow")
                || cause.equals("explosion") || cause.equals("lava")
                || cause.equals("in_fire") || cause.equals("cactus")
                || cause.equals("lightning");
        double reduced = armorReducible
                ? CombatRules.damageAfterArmor(applied, inventory.armorPoints(), inventory.armorToughness())
                : applied;
        // [GOLD-FOOD] 저항(Resistance)은 바닐라 순서대로 방어도 감산 <b>뒤</b> · 보호 인챈트
        // <b>앞</b>에서 곱한다. 방어도가 통하지 않는 피해(낙하·기아·마법)에도 걸리므로
        // armorReducible 게이트 밖이다.
        if (statusEffects.has(StatusEffect.RESISTANCE)) {
            reduced *= StatusEffects.resistanceMultiplier(
                    statusEffects.amplifier(StatusEffect.RESISTANCE));
        }
        // [SURV-X] 보호(Protection)는 바닐라대로 방어도 감산 뒤에 EPF 합으로 한 번 더 곱한다.
        // [ENCHANT-WIDE] 26.3 LivingEntity.getDamageAfterMagicAbsorb 는 방어도 게이트와 무관하게 기아
        // (#bypasses_effects)만 빼고 모든 피해에 EnchantmentHelper.getDamageProtection 을 적용한다:
        // 보호 1·lv, 화염 2·lv(#is_fire), 폭발 2·lv, 발사체 2·lv(#is_projectile), 가벼운 착지 3·lv(#is_fall).
        int epf = EnchantmentRules.damageProtectionEpf(cause,
                inventory.equippedWideEnchantments(ArmorSlot.HELMET),
                inventory.equippedWideEnchantments(ArmorSlot.CHESTPLATE),
                inventory.equippedWideEnchantments(ArmorSlot.LEGGINGS),
                inventory.equippedWideEnchantments(ArmorSlot.BOOTS));
        if (epf > 0) {
            reduced = EnchantmentRules.damageAfterProtectionMilli(
                    (int) Math.round(reduced * EnchantmentRules.MILLI), epf)
                    / (double) EnchantmentRules.MILLI;
        }
        int healthDamage = (int) Math.ceil(reduced);
        int absorbed = Math.min(absorptionPoints, healthDamage);
        absorptionPoints -= absorbed;
        healthDamage -= absorbed;
        health = Math.max(0, health - healthDamage);
        // [TRIAL-GAP] InfestedMobEffect.onMobHurt 는 받아들여진 피해마다 한 번 굴린다.
        if (statusEffects.has(StatusEffect.INFESTED)) pendingInfestedHurts++;
        if (armorReducible) brokenSoundItems.addAll(inventory.damageArmor(applied));
        healthDirty = true;
        stateRevision++;
        justDamaged = true;
        cancelBandage();
        healthCause = cause;
        damageCause = cause;
        hurtKiller = killer;
        boolean usedTotem = health <= 0
                && (inventory.consumeOne(PlayerInventory.Hand.MAIN, PlayerInventory.TOTEM_OF_UNDYING)
                        || inventory.consumeOne(
                                PlayerInventory.Hand.OFFHAND, PlayerInventory.TOTEM_OF_UNDYING));
        if (usedTotem) {
            // 1.21.4 DeathProtection.TOTEM_OF_UNDYING: clear effects, then apply
            // regeneration II (900), absorption II (100) and fire resistance I (800 MC ticks).
            health = 1;
            clearStatusEffects();
            regenerationMcTicksRemaining = TOTEM_REGEN_MC_TICKS;
            regenerationMcTickAccum = 0;
            absorptionTicksRemaining = TOTEM_ABSORPTION_TICKS;
            absorptionPoints = TOTEM_ABSORPTION_POINTS;
            fireResistanceTicksRemaining = TOTEM_FIRE_RESISTANCE_TICKS;
            totemUseSounds++;
        } else if (health <= 0) {
            fleshBandage.restore(0);
            dead = true;
            cancelPendingCraftingOpen();
            justDied = true;
            // [PHANTOM] 바닐라 ServerPlayer#die 도 TIME_SINCE_REST 를 0 으로 되돌린다 —
            // 죽어서 밤을 넘긴 플레이어에게 팬텀이 계속 붙지 않게 하는 원문 규약이다.
            resetTimeSinceRest();
            regenHealMilli = 0;
            resetCombatUseState();
            // 사망 시 불을 끈다(환경 틱은 사망자를 건너뛰므로 시체에 fireTicks 가 남아 남 화면에 불이 붙는 것 방지).
            setFireTicks(0);
            fireAccum = 0;
            // [TRIAL-GAP] 목록을 비우기 전에 onMobRemoved(KILLED) 효과를 기록한다.
            recordDeathTriggeredEffects();
            clearStatusEffects();
        }
        return true;
    }

    /**
     * 독 피해 전용 경로. 바닐라대로 방어구와 피격 무적을 모두 무시하고, 체력을 1 미만으로는 내리지 않습니다.
     * 흡수 하트는 일반 피해와 같은 순서로 먼저 소모합니다.
     *
     * @return 실제로 체력이나 흡수가 줄었으면 true
     */
    boolean damagePoison(int amount) {
        if (dead || amount <= 0) return false;
        int remaining = amount;
        int absorbed = Math.min(absorptionPoints, remaining);
        absorptionPoints -= absorbed;
        remaining -= absorbed;
        int healthDamage = Math.min(remaining, Math.max(0, health - 1));
        if (absorbed == 0 && healthDamage == 0) return false;
        health -= healthDamage;
        if (statusEffects.has(StatusEffect.INFESTED)) pendingInfestedHurts++;
        healthDirty = true;
        stateRevision++;
        justDamaged = true;
        healthCause = "poison";
        cancelBandage();
        damageCause = "poison";
        hurtKiller = null;
        return true;
    }

    /** 방패 사용을 시작한 손을 고정해 선택 슬롯 변경이 사용 대상을 바꾸지 못하게 합니다. */
    void setShieldInput(boolean pressed, PlayerInventory.HandRef hand, long nowNanos) {
        if (hand == null) throw new IllegalArgumentException("shield hand is required");
        if (pressed && (!shieldInput || !sameHandRef(hand, shieldHand))) shieldRaisedAtNanos = nowNanos;
        shieldInput = pressed;
        shieldHand = pressed ? hand : null;
    }

    private static boolean sameHandRef(
            PlayerInventory.HandRef left, PlayerInventory.HandRef right) {
        return left != null && right != null && left.hand() == right.hand()
                && left.mainSlot() == right.mainSlot();
    }

    /** F 교환과 컨테이너 이동이 진행 중인 손 사용을 즉시 취소할 때 호출합니다. */
    void cancelHandUse() {
        cancelBandage();
        shieldInput = false;
        shieldRaisedAtNanos = 0;
        shieldHand = null;
    }

    /** 선택 칸 변경은 주손으로 든 방패만 내린다. 보조손 방패는 바닐라처럼 계속 막는다. */
    void cancelMainHandUse() {
        if (bandageHand != null && bandageHand.hand() == PlayerInventory.Hand.MAIN) cancelBandage();
        if (shieldHand != null && shieldHand.hand() == PlayerInventory.Hand.MAIN) cancelHandUse();
    }

    boolean shieldInput() {
        return shieldInput;
    }

    boolean shieldActive(long nowNanos) {
        return shieldInput
                && shieldHand != null
                && PlayerInventory.isShield(inventory.stack(shieldHand).itemType())
                && nowNanos >= shieldRaisedAtNanos + SHIELD_RAISE_NANOS
                && nowNanos >= shieldDisabledUntilNanos;
    }

    /**
     * 방향이 있는 전투 피해의 서버 권위 진입점. 정면 방패면 피해와 넉백을 모두 무효화하고 방패를 1 마모한다.
     * 도끼 공격은 막히지 않고 방패를 5초간 무력화한다. 환경 피해는 기존 damage 경로를 사용한다.
     */
    DirectionalDamageResult damageFromResult(int amount, String cause, String killer,
            double sourceX, double sourceZ, boolean axeAttack, long nowNanos) {
        if (dead || amount <= 0) return DirectionalDamageResult.REJECTED;
        if (axeAttack && shieldActive(nowNanos) && isInShieldFront(sourceX, sourceZ)) {
            shieldDisabledUntilNanos = nowNanos + SHIELD_DISABLE_NANOS;
            return damage(amount, cause, killer)
                    ? DirectionalDamageResult.DAMAGED
                    : DirectionalDamageResult.REJECTED;
        }
        if (shieldActive(nowNanos) && isInShieldFront(sourceX, sourceZ)) {
            PlayerInventory.StackSnapshot before = inventory.stack(shieldHand);
            short shieldType = before.itemType();
            inventory.degrade(shieldHand);
            shieldBlockedItems.add(shieldType);
            if (before.durability() == 1 && inventory.stack(shieldHand).isEmpty()) {
                brokenSoundItems.add(shieldType);
            }
            return DirectionalDamageResult.BLOCKED;
        }
        return damage(amount, cause, killer)
                ? DirectionalDamageResult.DAMAGED
                : DirectionalDamageResult.REJECTED;
    }

    boolean damageFrom(int amount, String cause, String killer,
            double sourceX, double sourceZ, boolean axeAttack, long nowNanos) {
        return damageFromResult(amount, cause, killer, sourceX, sourceZ, axeAttack, nowNanos)
                .damaged();
    }

    /**
     * 피해 없이 서버 권위 넉백만 발생한 전투 사실을 기존 playerHurt 방송 경계에 실어 보냅니다.
     * Ravager 방패 반동처럼 체력·피격 무적은 바꾸지 않지만 클라이언트가 권위 속도를 받아야 하는 경우입니다.
     */
    void markKnockbackOnly(String cause, String killer) {
        if (dead) return;
        healthDirty = true;
        justDamaged = true;
        healthCause = cause;
        damageCause = cause;
        hurtKiller = killer;
    }

    void recordBrokenItem(short itemType) {
        brokenSoundItems.add(itemType);
    }

    List<Short> drainBrokenSoundItems() {
        List<Short> result = List.copyOf(brokenSoundItems);
        brokenSoundItems.clear();
        return result;
    }

    void recordShelfMushroomBounce(int x, int y, int z) {
        shelfMushroomBounceSounds.add(new BlockPos(x, y, z));
    }

    List<BlockPos> drainShelfMushroomBounceSounds() {
        List<BlockPos> result = List.copyOf(shelfMushroomBounceSounds);
        shelfMushroomBounceSounds.clear();
        return result;
    }

    /**
     * [VANILLA-SOUNDS] 낙하 피해 착지의 블록 낙하음 한 건({@code LivingEntity.playBlockFallSound}).
     * 좌표는 착지한 발 위치이고 blockType 은 소리를 정한 발 아래 블록이다.
     */
    record BlockFallSound(double x, double y, double z, int blockType) { }

    void recordBlockFallSound(double x, double y, double z, int blockType) {
        blockFallSounds.add(new BlockFallSound(x, y, z, blockType));
    }

    List<BlockFallSound> drainBlockFallSounds() {
        List<BlockFallSound> result = List.copyOf(blockFallSounds);
        blockFallSounds.clear();
        return result;
    }

    /** 이번 틱 막기 소리로 내보낼 방패 종류들(막은 순서대로). 드레인하면 비워진다. */
    List<Short> drainShieldBlockedSounds() {
        List<Short> result = List.copyOf(shieldBlockedItems);
        shieldBlockedItems.clear();
        return result;
    }

    int shieldBlockedCount() {
        return shieldBlockedItems.size();
    }

    private boolean isInShieldFront(double sourceX, double sourceZ) {
        double dx = sourceX - x;
        double dz = sourceZ - z;
        double length = Math.hypot(dx, dz);
        if (length <= 1e-9) return true;
        double forwardX = -Math.sin(yaw);
        double forwardZ = -Math.cos(yaw);
        // 기본 horizontal_blocking_angle 90°: dot >= 0 인 전방 반구.
        return (dx * forwardX + dz * forwardZ) / length >= 0.0;
    }

    boolean hurtProtected() {
        return hurtProtectionTicks > 0;
    }

    /** 환경 틱 시작에서 1회 호출. 초기 피격 5틱 후 다시 피해를 허용한다. */
    void advanceHurtTick() {
        advanceStatusEffects();
        if (windChargeCooldownTicks > 0) windChargeCooldownTicks--;
        if (hurtProtectionTicks > 0 && --hurtProtectionTicks == 0) {
            previousHurtDamage = 0;
        }
    }

    boolean windChargeReady() { return windChargeCooldownTicks == 0; }

    void startWindChargeCooldown() { windChargeCooldownTicks = 5; }

    /**
     * 선택한 황금사과를 소비하고 재생 II 5초 + 흡수 I 2분(추가 HP 4)을 부여한다.
     * 바닐라 alwaysEdible 이라 만복에서도 사용할 수 있고, 허기 4 + saturation 9.6 도 함께 회복한다.
     */
    boolean consumeGoldenApple(PlayerInventory.HandRef hand) {
        if (dead || !com.gameexpert.engine.inventory.InventoryRules.consumeGoldenApple(
                inventory, hand)) {
            return false;
        }
        eat(com.gameexpert.engine.inventory.InventoryRules.foodNutrition(
                        com.gameexpert.engine.inventory.PlayerInventory.GOLDEN_APPLE),
                com.gameexpert.engine.inventory.InventoryRules.foodSaturationMilli(
                        com.gameexpert.engine.inventory.PlayerInventory.GOLDEN_APPLE));
        regenerationMcTicksRemaining = GOLDEN_APPLE_REGEN_MC_TICKS;
        regenerationMcTickAccum = 0;
        absorptionTicksRemaining = GOLDEN_APPLE_ABSORPTION_TICKS;
        absorptionPoints = 4;
        return true;
    }

    /**
     * [GOLD-FOOD] 마법이 부여된 황금 사과를 하나 먹습니다. 바닐라 alwaysEdible 이라 만복에서도
     * 먹히며, 네 효과를 <b>한 번에</b> 부여합니다([B] minecraft.wiki «Enchanted Golden Apple»):
     * 흡수 IV 2:00 · 재생 II 0:20 · 화염 저항 I 5:00 · 저항 I 5:00.
     *
     * <p>흡수·재생은 이미 황금사과가 쓰는 <b>물리 축</b>(absorptionPoints·regeneration…)에
     * 그대로 얹고, 화염 저항·저항은 상태이상 목록에 얹는다 — 화염 저항 게이트가 이미 두 축을
     * OR 로 읽고 있어 토템/물약과 판정이 갈리지 않는다.
     */
    boolean consumeEnchantedGoldenApple(PlayerInventory.HandRef hand) {
        if (dead || !com.gameexpert.engine.inventory.InventoryRules.consumeOne(
                inventory, hand, PlayerInventory.ENCHANTED_GOLDEN_APPLE)) {
            return false;
        }
        eat(com.gameexpert.engine.inventory.InventoryRules.foodNutrition(
                        PlayerInventory.ENCHANTED_GOLDEN_APPLE),
                com.gameexpert.engine.inventory.InventoryRules.foodSaturationMilli(
                        PlayerInventory.ENCHANTED_GOLDEN_APPLE));
        regenerationMcTicksRemaining = ENCHANTED_GOLDEN_APPLE_REGEN_MC_TICKS;
        regenerationMcTickAccum = 0;
        absorptionTicksRemaining = ENCHANTED_GOLDEN_APPLE_ABSORPTION_TICKS;
        absorptionPoints = ENCHANTED_GOLDEN_APPLE_ABSORPTION_POINTS;
        statusEffects.apply(StatusEffect.FIRE_RESISTANCE, 0,
                ENCHANTED_GOLDEN_APPLE_LONG_EFFECT_TICKS);
        statusEffects.apply(StatusEffect.RESISTANCE, 0,
                ENCHANTED_GOLDEN_APPLE_LONG_EFFECT_TICKS);
        return true;
    }

    /**
     * [GOLD-FOOD] 후렴과 한 개분의 허기·포화를 채웁니다(alwaysEdible 이라 만복에서도 부른다).
     * 순간이동 목적지 굴림은 월드를 읽어야 하므로 {@code WorldTickLoop} 이 소유한다 —
     * 여기서는 <b>영양만</b> 처리해 두 관심사를 섞지 않는다.
     */
    void eatChorusFruit() {
        eat(com.gameexpert.engine.inventory.InventoryRules.foodNutrition(
                        PlayerInventory.CHORUS_FRUIT),
                com.gameexpert.engine.inventory.InventoryRules.foodSaturationMilli(
                        PlayerInventory.CHORUS_FRUIT));
    }

    /**
     * [GOLD-FOOD] 꿀이 든 병을 하나 마십니다. 바닐라대로 <b>독만</b> 해제하고(우유와 달리 다른
     * 효과는 남긴다) 빈 유리병을 돌려주며, 음료지만 허기를 채우는 유일한 아이템이다.
     */
    boolean consumeHoneyBottle(PlayerInventory.HandRef hand) {
        if (dead || !com.gameexpert.engine.inventory.InventoryRules.consumeHoneyBottle(
                inventory, hand)) {
            return false;
        }
        eat(com.gameexpert.engine.inventory.InventoryRules.foodNutrition(
                        PlayerInventory.HONEY_BOTTLE),
                com.gameexpert.engine.inventory.InventoryRules.foodSaturationMilli(
                        PlayerInventory.HONEY_BOTTLE));
        statusEffects.remove(StatusEffect.POISON);
        return true;
    }

    /**
     * 우유 양동이를 마셔 현재 상태이상을 모두 지우고 같은 칸에 빈 양동이를 돌려줍니다.
     * 우유는 음식이 아니므로 체력은 회복하지 않습니다.
     */
    boolean consumeMilk(PlayerInventory.HandRef hand) {
        if (dead || !com.gameexpert.engine.inventory.InventoryRules.replaceBucket(
                inventory, hand, com.gameexpert.engine.inventory.PlayerInventory.MILK_BUCKET,
                com.gameexpert.engine.inventory.PlayerInventory.BUCKET)) {
            return false;
        }
        clearStatusEffects();
        return true;
    }

    /**
     * [RAID-REWARD] 전리품 악기 재사용 판정. 효과가 0 이라 밸런스가 아니라 스팸 방지만 한다.
     * 쿨다운은 재접속으로 초기화돼도 무해하므로 영속하지 않는다.
     */
    boolean tryUseTrophyInstrument(long tickNo) {
        if (dead || tickNo < trophyInstrumentReadyTick) return false;
        trophyInstrumentReadyTick = tickNo + PlayerInventory.TROPHY_INSTRUMENT_COOLDOWN_TICKS;
        return true;
    }

    /**
     * [TRIAL-GAP] 불길한 병 하나를 마신다. 바닐라 {@code OminousBottleAmplifier.onConsume}:
     * {@code MobEffectInstance(BAD_OMEN, 120000, value, false, false, true)}. 병 아이템에는
     * use_remainder 가 없어 유리병을 돌려주지 않고 한 개만 줄어든다(스택 64).
     */
    boolean consumeOminousBottle(PlayerInventory.HandRef hand) {
        if (dead) return false;
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        if (held.itemType() != PlayerInventory.OMINOUS_BOTTLE) return false;
        int amplifier = held.itemComponents().ominousBottleAmplifier();
        if (!inventory.consumeOne(hand, PlayerInventory.OMINOUS_BOTTLE)) return false;
        statusEffects.apply(StatusEffect.BAD_OMEN, amplifier, BAD_OMEN_DURATION_TICKS);
        return true;
    }

    /**
     * [POTION] 선택 슬롯의 마시는 물약 하나를 비우고 그 효과를 부여합니다. 바닐라와 같이 빈
     * 유리병이 같은 칸에 남고, 효과가 없는 어색한 물약도 마실 수는 있습니다.
     *
     * @return 실제로 마셨으면 true(인벤 변경)
     */
    boolean drinkPotion(PlayerInventory.HandRef hand) {
        if (dead) return false;
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        short type = held.itemType();
        // [UTILITY] 전용 ID · 범용 물약(CONTENTS_POTION) 모두 (형태, 물약 키)로 풀고 바닐라
        // Potions.<clinit> 의 효과 전부를 건다(거북 도사는 둘, 치유는 즉시 치유).
        com.gameexpert.engine.effect.PotionCatalog.Contents contents =
                com.gameexpert.engine.effect.PotionCatalog.resolve(
                        type, held.itemComponents().potionContents());
        if (contents == null
                || contents.form() != com.gameexpert.engine.effect.PotionCatalog.Form.POTION) {
            return false;
        }
        java.util.List<com.gameexpert.engine.mob.ProjectileEffect> effects =
                com.gameexpert.engine.effect.PotionCatalog.drinkEffects(contents.key());
        if (!inventory.replaceSingle(hand, type, PlayerInventory.GLASS_BOTTLE)) return false;
        for (com.gameexpert.engine.mob.ProjectileEffect effect : effects) {
            if (effect.effect() == com.gameexpert.engine.effect.StatusEffect.INSTANT_HEALTH) {
                // HealOrHarmMobEffect.applyInstantenousEffect: 마신 물약은 근접도 1.0.
                heal(com.gameexpert.engine.effect.PotionCatalog.instantHealAmount(
                        effect.amplifier(), 1.0), "regen");
            } else if (effect.effect().instantaneous()) {
                damageMagic(com.gameexpert.engine.effect.PotionCatalog.instantDamageAmount(
                        effect.amplifier(), 1.0));
            } else {
                statusEffects.apply(effect.effect(), effect.amplifier(), effect.durationTicks());
            }
        }
        return true;
    }

    /** 현재 구현된 모든 상태이상을 제거합니다. 새 상태이상도 이 한 곳에 추가합니다. */
    void clearStatusEffects() {
        clearGoldenAppleEffects();
        statusEffects.clear();
    }

    /** 상태이상 목록(독·감속·나약). 즉발 효과는 여기 남지 않습니다. */
    public StatusEffects statusEffects() {
        return statusEffects;
    }

    /** 접속 시 DB에 남은 MC 틱·주기 누적까지 그대로 복원합니다. */
    public void restoreStatusEffects(List<StatusEffects.PersistentEffect> snapshot) {
        statusEffects.restorePersistence(snapshot);
    }

    /** 재생 주기 위상·흡수 잔량·별도 화염 저항 시계를 접속 시 그대로 복원합니다. */
    private final FleshBandage fleshBandage = new FleshBandage();
    private PlayerInventory.HandRef bandageHand;
    private boolean bandageFinishRequested;

    void beginBandage(PlayerInventory.HandRef hand) {
        if (!dead && inventory.stack(hand).itemType() == Blocks.FLESH_BANDAGE
                && fleshBandage.begin("held", 0)) {
            bandageHand = hand;
            bandageFinishRequested = false;
        }
    }

    void cancelBandage() {
        fleshBandage.cancelUse();
        bandageHand = null;
        bandageFinishRequested = false;
    }

    /** Client completion may precede twenty authority ticks; retain it for this exact use. */
    boolean requestBandageFinish(PlayerInventory.HandRef hand) {
        if (!sameHandRef(hand, bandageHand)) { cancelBandage(); return false; }
        bandageFinishRequested = true;
        return finishPendingBandage();
    }

    boolean finishPendingBandage() {
        if (!bandageFinishRequested) return false;
        if (dead || bandageHand == null
                || bandageHand.hand() == PlayerInventory.Hand.MAIN
                        && bandageHand.mainSlot() != inventory.selectedSlot()
                || inventory.stack(bandageHand).itemType() != Blocks.FLESH_BANDAGE) {
            cancelBandage();
            return false;
        }
        if (!fleshBandage.ready() || inventory.settlementLeased()) return false;
        return finishBandage();
    }

    boolean finishBandage() {
        if (dead || bandageHand == null || !fleshBandage.ready()) { cancelBandage(); return false; }
        boolean consumed = inventory.consumeOne(bandageHand, (short) Blocks.FLESH_BANDAGE);
        if (consumed) { fleshBandage.consumed(); stateRevision++; }
        cancelBandage();
        return consumed;
    }

    public void restoreEffectClocks(StatusEffects.PersistentPlayerEffectClocks snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("플레이어 상태이상 시계는 null일 수 없습니다");
        }
        regenerationMcTicksRemaining = snapshot.regenerationMcTicksRemaining();
        regenerationMcTickAccum = snapshot.regenerationMcTickAccum();
        absorptionTicksRemaining = snapshot.absorptionTicksRemaining();
        absorptionPoints = snapshot.absorptionPoints();
        fireResistanceTicksRemaining = snapshot.fireResistanceTicksRemaining();
        cancelBandage();
        fleshBandage.restore(snapshot.bandageHealingTicks());
    }

    /** persistence executor에 넘길 불변 상태이상 시계 스냅샷. */
    public StatusEffects.PersistentPlayerEffectClocks effectClocksSnapshot() {
        return new StatusEffects.PersistentPlayerEffectClocks(
                regenerationMcTicksRemaining, regenerationMcTickAccum,
                absorptionTicksRemaining, absorptionPoints, fireResistanceTicksRemaining, fleshBandage.healingTicks());
    }

    /**
     * 지속 상태이상을 부여합니다.
     *
     * @param durationTicks 10 TPS 서버 틱 기준 지속시간
     * @return 목록이 실제로 바뀌었으면 true
     */
    boolean applyStatusEffect(StatusEffect effect, int amplifier, int durationTicks) {
        if (dead) return false;
        boolean changed = statusEffects.apply(effect, amplifier, durationTicks);
        if (changed) stateRevision++;
        return changed;
    }

    /** 홀수 MC 틱 지속시간을 보존하는 상태이상 부여 경로. */
    boolean applyStatusEffectMcTicks(StatusEffect effect, int amplifier, int durationMcTicks) {
        if (dead) return false;
        boolean changed = statusEffects.applyMcTicks(effect, amplifier, durationMcTicks);
        if (changed) stateRevision++;
        return changed;
    }

    /** [BEACON] ambient 상태이상 부여(신호기). 갱신 규칙은 {@link #applyStatusEffectMcTicks} 와 같다. */
    boolean applyAmbientStatusEffectMcTicks(StatusEffect effect, int amplifier, int durationMcTicks) {
        if (dead) return false;
        boolean changed = statusEffects.applyAmbientMcTicks(effect, amplifier, durationMcTicks);
        if (changed) stateRevision++;
        return changed;
    }

    /** 한 효과만 제거하고 영속 revision 을 함께 갱신합니다. */
    boolean removeStatusEffect(StatusEffect effect) {
        boolean changed = statusEffects.remove(effect);
        if (changed) stateRevision++;
        return changed;
    }

    /** [TRIAL-GAP] 벌레 먹음을 지닌 채 받아들여진 피해 횟수. MobSystem 이 소비한다. */
    private int pendingInfestedHurts;
    /** [TRIAL-GAP] 사망 순간 지니고 있던 onMobRemoved 효과(돌풍 충전 · 거미줄 · 점액). */
    private final java.util.EnumSet<StatusEffect> deathTriggeredEffects =
            java.util.EnumSet.noneOf(StatusEffect.class);

    int consumeInfestedHurts() {
        int hurts = pendingInfestedHurts;
        pendingInfestedHurts = 0;
        return hurts;
    }

    java.util.Set<StatusEffect> consumeDeathTriggeredEffects() {
        if (deathTriggeredEffects.isEmpty()) return java.util.Set.of();
        java.util.Set<StatusEffect> drained = java.util.EnumSet.copyOf(deathTriggeredEffects);
        deathTriggeredEffects.clear();
        return drained;
    }

    private void recordDeathTriggeredEffects() {
        for (StatusEffect effect : new StatusEffect[] {StatusEffect.WIND_CHARGED,
                StatusEffect.WEAVING, StatusEffect.OOZING}) {
            if (statusEffects.has(effect)) deathTriggeredEffects.add(effect);
        }
    }

    /** 고통 물약 등 즉발 마법 피해. 방어구를 무시하고 피격 무적 규칙은 일반 피해와 같습니다. */
    boolean damageMagic(int amount) {
        return damage(amount, "magic", null);
    }

    /** 환경 틱에서 1회 진행. 10 TPS 한 틱마다 MC 2틱을 누적해 25틱 회복 주기를 반올림하지 않는다. */
    void advanceStatusEffects() {
        if (dead) { fleshBandage.restore(0); return; }
        boolean persistentEffectTicked = !statusEffects.isEmpty()
                || fleshBandage.healingTicks() > 0
                || regenerationMcTicksRemaining > 0
                || absorptionTicksRemaining > 0
                || fireResistanceTicksRemaining > 0;
        // [TRIAL-GAP] 황금 사과·토템의 재생 II 시계가 켜져 있으면 목록의 재생(물약, 앰프 0)은
        // 바닐라 hiddenEffect 처럼 지속만 흐르고 회복하지 않는다(MobEffectInstance.tickDownDuration).
        boolean clockRegenerationActive = regenerationMcTicksRemaining > 0;
        fleshBandage.advanceUse("held", bandageHand != null
                && inventory.stack(bandageHand).itemType() == Blocks.FLESH_BANDAGE, true, 0);
        if (fleshBandage.advanceHealing(true)) heal(1, "regen");
        if (regenerationMcTicksRemaining > 0) {
            int elapsed = Math.min(2, regenerationMcTicksRemaining);
            regenerationMcTicksRemaining -= elapsed;
            regenerationMcTickAccum += elapsed;
            while (regenerationMcTickAccum >= REGEN_II_INTERVAL_MC_TICKS) {
                regenerationMcTickAccum -= REGEN_II_INTERVAL_MC_TICKS;
                heal(1, "golden_apple_regeneration");
            }
        }
        if (absorptionTicksRemaining > 0 && --absorptionTicksRemaining == 0) {
            absorptionPoints = 0;
        }
        if (fireResistanceTicksRemaining > 0) fireResistanceTicksRemaining--;
        int poison = statusEffects.tick();
        if (poison > 0) damagePoison(poison);
        // [TRIAL-GAP] RegenerationMobEffect.applyEffectTick: 체력이 최대 미만이면 1 회복.
        if (!clockRegenerationActive) {
            for (int heal = statusEffects.regenerationHeals(); heal > 0; heal--) {
                heal(1, "regen");
            }
        }
        // 허기 효과의 MC 틱당 exhaustion 0.005를 기존 milli 단위로 누적한다.
        int hungerMcTicks = statusEffects.hungerMcTicksElapsed();
        if (hungerMcTicks > 0) {
            addExhaustion(hungerMcTicks * 5 * (statusEffects.hungerAmplifier() + 1));
        }
        // [COOKING] 포만감은 흘러간 MC 틱마다 바닐라 eat(level + 1, 1.0F) 를 그대로 적용한다
        // (허기 +레벨+1, 포화 +(레벨+1)×2.0 = milli 로 (레벨+1)×2000).
        int saturationMcTicks = statusEffects.saturationMcTicksElapsed();
        if (saturationMcTicks > 0) {
            int perTick = statusEffects.saturationAmplifier() + 1;
            for (int step = 0; step < saturationMcTicks; step++) {
                eat(perTick, perTick * 2 * HungerRules.MILLI);
            }
        }
        // 남은 시간과 독의 다음 피해 주기도 영속 상태다. 효과가 조용히 흘러가기만 한 틱도
        // revision 을 올려야 직전 DB 저장 이후의 시간이 다음 주기/퇴장 저장에 반영된다.
        if (persistentEffectTicked) stateRevision++;
    }

    int regenerationTicksRemaining() {
        return (regenerationMcTicksRemaining + 1) / 2;
    }

    int absorptionTicksRemaining() {
        return absorptionTicksRemaining;
    }

    int absorptionPoints() {
        return absorptionPoints;
    }

    int fireResistanceTicksRemaining() {
        return fireResistanceTicksRemaining;
    }

    void setHurtKnockback(double kbX, double kbY, double kbZ,
                          double kbBonusX, double kbBonusZ) {
        this.hurtKbX = kbX;
        this.hurtKbY = kbY;
        this.hurtKbZ = kbZ;
        this.hurtKbBonusX = kbBonusX;
        this.hurtKbBonusZ = kbBonusZ;
    }

    double hurtKbX() { return hurtKbX; }
    double hurtKbY() { return hurtKbY; }
    double hurtKbZ() { return hurtKbZ; }
    double hurtKbBonusX() { return hurtKbBonusX; }
    double hurtKbBonusZ() { return hurtKbBonusZ; }

    void heal(int amount) {
        heal(amount, "regen");
    }

    /** 지정 사유(regen/food 등)로 회복. 만복(20)이면 아무 일 없음(호출부에서 소비 여부 판정). */
    void heal(int amount, String cause) {
        if (dead || amount <= 0 || health >= 20) {
            return;
        }
        health = Math.min(20, health + amount);
        healthDirty = true;
        stateRevision++;
        healthCause = cause;
    }

    // ── 허기(SURV-H) ──

    /** 현재 허기(0~20). welcome.self·healthUpdate 로 본인에게만 전달한다. */
    public int food() {
        return food;
    }

    /** 은닉 saturation(1/1000 단위). HUD에는 표시하지 않지만 회복 속도를 지배한다. */
    public int saturationMilli() {
        return saturationMilli;
    }

    int exhaustionMilli() {
        return exhaustionMilli;
    }

    int foodTickTimer() {
        return foodTickTimer;
    }

    /** 허기·saturation 이 바뀌어 healthUpdate 재전송이 필요한가. */
    // ── [SURV-X] 경험치 ──
    /** 누적 경험치(사망하면 0으로 되돌아간다). */
    public int xpTotal() {
        return xpTotal;
    }

    /** 파생 레벨. 저장하지 않고 항상 XpRules 로 계산한다. */
    public int xpLevel() {
        return XpRules.levelFromTotal(xpTotal);
    }

    /** 누적 경험치를 더한다(음수 방지). 값이 바뀌면 xpUpdate 방송 대상이 된다. */
    void addXp(int amount) {
        if (amount == 0) return;
        setXpTotal(Math.max(0, xpTotal + amount));
    }

    void setXpTotal(int value) {
        int clamped = Math.max(0, value);
        if (clamped == xpTotal) return;
        xpTotal = clamped;
        xpDirty = true;
        stateRevision++;
    }

    /** 저장된 누적 경험치로 복원한다(입장 경로 전용, dirty 를 세우지 않는다). */
    void restoreXpTotal(int persistedXpTotal) {
        this.xpTotal = Math.max(0, persistedXpTotal);
    }

    boolean xpDirty() {
        return xpDirty;
    }

    void consumeXpDirty() {
        xpDirty = false;
    }

    /** 인챈트 제안 추첨 시드. */
    int enchantSeed() {
        return enchantSeed;
    }

    void reshuffleEnchantSeed(int seed) {
        this.enchantSeed = seed;
    }

    boolean foodDirty() {
        return foodDirty;
    }

    void consumeFoodDirty() {
        foodDirty = false;
    }

    /** 영속 상태 복원(접속 시 1회). 필드가 없던 세이브는 호출부가 만복 기본값을 넘긴다. */
    void restoreHunger(int persistedFood, int persistedSaturationMilli) {
        this.food = Math.max(0, Math.min(HungerRules.MAX_FOOD, persistedFood));
        this.saturationMilli = Math.max(0, Math.min(this.food * HungerRules.MILLI, persistedSaturationMilli));
    }

    /** exhaustion 누적(milli). 4.0을 넘으면 다음 허기 틱이 saturation → 허기 순으로 덜어낸다. */
    void addExhaustion(int milli) {
        if (dead || milli <= 0) return;
        exhaustionMilli += milli;
    }

    /** 이동 거리 비례 exhaustion. TPS와 무관하므로 이동 메시지 단위로 그때그때 누적한다. */
    void addMovementExhaustion(double distanceBlocks) {
        if (dead) return;
        int numerator = movementExhaustionRemainder + HungerRules.movementExhaustionHundredthsMilli(
                HungerRules.MOVEMENT_EXHAUSTION_PER_BLOCK_MILLI, distanceBlocks);
        movementExhaustionRemainder = numerator % 100;
        addExhaustion(numerator / 100);
    }

    /**
     * 계약(CONTRACT §3)의 "음식·우유는 주손에서 32 game ticks 사용한 뒤 소비"를 서버가 강제한다.
     * 20 TPS 기준 32틱 = 1.6초이므로 10 TPS 서버 틱으로는 16틱이고, 지연 여유로 1틱을 뺀다.
     * 클라가 사용 애니메이션을 건너뛰고 consume 을 연달아 보내도 이 간격 아래로는 회복하지 못한다.
     */
    public boolean canConsumeAt(long tickNo) {
        return tickNo - lastConsumeTickNo >= CONSUME_INTERVAL_TICKS - CONSUME_TOLERANCE_TICKS;
    }

    /** 승인한 소비의 틱 번호를 기록한다(다음 소비의 기준). */
    public void markConsumed(long tickNo) {
        lastConsumeTickNo = tickNo;
    }

    /** 바닐라 {@code Player.canEat}: 만복이면 일반 음식을 먹을 수 없다(황금사과는 예외). */
    public boolean canEat() {
        return !dead && food < HungerRules.MAX_FOOD;
    }

    /** 바닐라 {@code FoodData.eat}: 허기는 20에서, saturation 은 허기 값에서 각각 잘린다. */
    void eat(int nutrition, int saturationGainMilli) {
        if (dead) return;
        setFood(Math.min(food + nutrition, HungerRules.MAX_FOOD));
        setSaturationMilli(Math.min(saturationMilli + saturationGainMilli, food * HungerRules.MILLI));
    }

    /** 난이도를 모르는 호출부(테스트)용 편의 오버로드. normal 하한(1)을 쓴다. */
    void tickHunger(boolean starvationDisabled) {
        tickHunger(starvationDisabled, HungerRules.STARVE_MIN_HEALTH_NORMAL);
    }

    /**
     * 바닐라 {@code FoodData.tick} 1회. 서버 틱(10 TPS)마다 환경 틱에서 호출한다.
     *
     * <p>① exhaustion 4.0 초과분을 saturation(없으면 허기)으로 덜어낸다 — MC 틱당 1회 판정이라
     * 서버 1틱에 {@link HungerRules#EXHAUSTION_STEPS_PER_TICK}회 돌린다.
     * ② 만복+saturation → 빠른 회복 / 허기 18↑ → 느린 회복 / 허기 0 → 굶주림, 세 분기는 배타이며
     * 바닐라와 같이 tickTimer 를 공유한다.
     *
     * <p>정적판 {@code StandalonePlayerVitals.tickHunger} 는 이 중 {@code starvationDisabled} 만
     * 받지 않는다. 그건 드리프트가 아니라 서버 전용 QA 무적 스위치({@code app.qa-invulnerable})라서,
     * 정적판에는 켤 수단 자체가 없다(welcome 의 {@code qaInvulnerable} 이 상수 false). 나머지
     * 분기·상수·소비 순서는 양판이 같아야 한다.
     *
     * @param starvationDisabled QA 무적. 굶주림 <b>피해</b>만 없애고 허기 소모·회복은 그대로 돈다.
     * @param starvationMinHealth 난이도별 하한(easy 10 / normal 1 / hard 0).
     */
    void tickHunger(boolean starvationDisabled, int starvationMinHealth) {
        if (dead) return;
        for (int step = 0; step < HungerRules.EXHAUSTION_STEPS_PER_TICK; step++) {
            if (exhaustionMilli <= HungerRules.EXHAUSTION_THRESHOLD_MILLI) break;
            exhaustionMilli -= HungerRules.EXHAUSTION_THRESHOLD_MILLI;
            if (saturationMilli > 0) {
                setSaturationMilli(Math.max(saturationMilli - HungerRules.MILLI, 0));
            } else {
                setFood(Math.max(food - 1, 0));
            }
        }
        boolean hurt = health < 20;
        if (hurt && saturationMilli > 0 && food >= HungerRules.SATURATED_REGEN_FOOD_THRESHOLD) {
            if (++foodTickTimer >= HungerRules.SATURATED_REGEN_INTERVAL_TICKS) {
                foodTickTimer = 0;
                // 소모량과 회복량이 같은 saturation 값에서 나오므로 소모를 먼저 읽어 둔다.
                int used = Math.min(saturationMilli, HungerRules.SATURATED_REGEN_MAX_SATURATION_MILLI);
                healRegenMilli(HungerRules.saturatedRegenHealMilli(saturationMilli), "food");
                addExhaustion(used);
            }
        } else if (hurt && food >= HungerRules.REGEN_FOOD_THRESHOLD) {
            if (++foodTickTimer >= HungerRules.REGEN_INTERVAL_TICKS) {
                foodTickTimer = 0;
                heal(1, "regen");
                addExhaustion(HungerRules.REGEN_EXHAUSTION_MILLI);
            }
        } else if (food <= 0) {
            if (++foodTickTimer >= HungerRules.STARVE_INTERVAL_TICKS) {
                foodTickTimer = 0;
                // 난이도별 하한(easy 10 / normal 1 / hard 0)은 Difficulty.starvationMinHealth()가 공급한다.
                if (!starvationDisabled && HungerRules.starvationDamageAllowed(health, starvationMinHealth)) {
                    damage(HungerRules.STARVE_DAMAGE, "starve");
                }
            }
        } else {
            foodTickTimer = 0;
        }
    }

    private void setFood(int value) {
        if (value == food) return;
        food = value;
        foodDirty = true;
        stateRevision++;
    }

    private void setSaturationMilli(int value) {
        if (value == saturationMilli) return;
        saturationMilli = value;
        foodDirty = true;
        stateRevision++;
    }

    /** 빠른 회복의 1 HP 미만 회복량을 milli 로 이월해 정수 HP에 반영한다. */
    private void healRegenMilli(int milli, String cause) {
        if (dead || milli <= 0 || health >= 20) {
            regenHealMilli = 0;
            return;
        }
        regenHealMilli += milli;
        while (regenHealMilli >= HungerRules.MILLI && health < 20) {
            regenHealMilli -= HungerRules.MILLI;
            heal(1, cause);
        }
        if (health >= 20) regenHealMilli = 0;
    }

    // ── 침대 리스폰 지점(S2a) ──
    void setBedSpawn(int x, int y, int z) {
        this.hasBedSpawn = true;
        this.bedSpawnX = x;
        this.bedSpawnY = y;
        this.bedSpawnZ = z;
        this.stateRevision++;
    }

    void clearBedSpawn() {
        if (!hasBedSpawn) return;
        hasBedSpawn = false;
        stateRevision++;
    }

    public boolean hasBedSpawn() {
        return hasBedSpawn;
    }

    public int bedSpawnX() {
        return bedSpawnX;
    }

    public int bedSpawnY() {
        return bedSpawnY;
    }

    public int bedSpawnZ() {
        return bedSpawnZ;
    }

    // ── 밤 수면 상태(S2a) ──
    boolean sleeping() {
        return sleeping;
    }

    void setSleeping(boolean value) {
        this.sleeping = value;
    }

    /**
     * [FURNITURE-26.3] 수면 진입. 어느 침대에서 자는지를 <b>리스폰 지점과 따로</b> 기록한다.
     * 건초 침대는 리스폰을 설정하지 않으므로(바닐라 [B]) 수면 유효성·점유·기상 시 자멸이
     * {@code bedSpawn*} 을 볼 수 없기 때문이다. 좌표는 항상 <b>머리 칸</b>이다.
     */
    void beginSleep(int headX, int y, int headZ) {
        this.sleeping = true;
        this.sleepBedX = headX;
        this.sleepBedY = y;
        this.sleepBedZ = headZ;
        // [PHANTOM] 바닐라 ServerPlayer#startSleeping 은 super.startSleeping 보다 **먼저**
        // TIME_SINCE_REST 를 0 으로 되돌린다. 밤을 넘겼는지·아침 전환에 성공했는지는 보지 않으므로
        // 리셋 자리는 handleSleepMorning 이 아니라 여기다(mc-phantom-insomnia.md §3).
        this.timeSinceRestMcTicks = 0;
    }

    /**
     * [PHANTOM] 불면 카운터를 한 서버 틱만큼 진행한다. 바닐라는 자고 있지 않은 매 game tick 마다
     * +1 이고 우리 틱은 10 TPS 라 한 틱이 game tick 2 개에 해당한다 → +2.
     * 자는 동안에는 올리지 않는 것까지 원문과 같다.
     */
    void advanceTimeSinceRest() {
        if (sleeping) return;
        timeSinceRestMcTicks += MC_TICKS_PER_SERVER_TICK;
    }

    /** [PHANTOM] 사망 리셋(바닐라 {@code ServerPlayer#die}). 침대 진입 리셋과 같은 자리다. */
    void resetTimeSinceRest() {
        this.timeSinceRestMcTicks = 0;
    }

    /** [PHANTOM] 재접속 복원. 컬럼이 없던 세이브는 0 이 들어온다. */
    public void restoreTimeSinceRest(long mcTicks) {
        this.timeSinceRestMcTicks = Math.max(0, mcTicks);
    }

    /** [PHANTOM] 현재 불면 시간(MC 틱). 스폰 굴림과 영속 저장이 같은 값을 읽는다. */
    public long timeSinceRestMcTicks() {
        return timeSinceRestMcTicks;
    }

    public int sleepBedX() {
        return sleepBedX;
    }

    public int sleepBedY() {
        return sleepBedY;
    }

    public int sleepBedZ() {
        return sleepBedZ;
    }

    void openChest(int x, int y, int z) { openChest(x, y, z, null); }
    void openChest(int x, int y, int z, BlockPos partner) {
        openChest = new BlockPos(x, y, z);
        openChestPartner = partner;
    }
    BlockPos openChest() { return openChest; }
    BlockPos openChestPartner() { return openChestPartner; }
    void closeChest() {
        openChest = null;
        openChestPartner = null;
    }
    void openFurnace(int x, int y, int z) { openFurnace = new BlockPos(x, y, z); }
    BlockPos openFurnace() { return openFurnace; }
    void closeFurnace() { openFurnace = null; }
    void openMobCargo(long mobId) { openMobCargo = mobId; }
    long openMobCargo() { return openMobCargo; }
    void closeMobCargo() { openMobCargo = 0L; }
    record GeneratedEntityCargoSession(long sessionId, long entityId, long sourceRevision) {
        GeneratedEntityCargoSession {
            if (sessionId <= 0L || entityId <= 0L || sourceRevision < 0L
                    || sessionId == Long.MAX_VALUE || entityId == Long.MAX_VALUE
                    || sourceRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid generated cargo session");
            }
        }
    }
    long openGeneratedEntityCargo(long entityId, long sourceRevision) {
        long sessionId = nextGeneratedSessionId(generatedCargoSessionSequence);
        generatedCargoSessionSequence = sessionId;
        openGeneratedEntityCargo = new GeneratedEntityCargoSession(
                sessionId, entityId, sourceRevision);
        return sessionId;
    }
    GeneratedEntityCargoSession openGeneratedEntityCargo() { return openGeneratedEntityCargo; }
    boolean ownsGeneratedEntityCargoSession(long entityId, long sessionId) {
        return openGeneratedEntityCargo != null
                && openGeneratedEntityCargo.entityId() == entityId
                && openGeneratedEntityCargo.sessionId() == sessionId;
    }
    void advanceGeneratedEntityCargoSession(long entityId, long sessionId,
            long expectedRevision, long nextRevision) {
        if (!ownsGeneratedEntityCargoSession(entityId, sessionId)
                || openGeneratedEntityCargo.sourceRevision() != expectedRevision
                || expectedRevision == Long.MAX_VALUE
                || nextRevision != Math.addExact(expectedRevision, 1L)) {
            throw new IllegalStateException("generated cargo session revision is stale");
        }
        openGeneratedEntityCargo = new GeneratedEntityCargoSession(
                sessionId, entityId, nextRevision);
    }
    boolean closeGeneratedEntityCargo(long entityId, long sessionId) {
        if (!ownsGeneratedEntityCargoSession(entityId, sessionId)) return false;
        openGeneratedEntityCargo = null;
        return true;
    }
    void closeGeneratedEntityCargo() { openGeneratedEntityCargo = null; }

    record GeneratedEntityEquipmentSettlement(long settlementId, long entityId,
            long expectedEntityRevision, long expectedPlayerRevision, PlayerAction.Hand hand) {
        GeneratedEntityEquipmentSettlement {
            if (settlementId <= 0L || entityId <= 0L || expectedEntityRevision < 0L
                    || expectedPlayerRevision < 0L || settlementId == Long.MAX_VALUE
                    || entityId == Long.MAX_VALUE || expectedEntityRevision == Long.MAX_VALUE
                    || expectedPlayerRevision == Long.MAX_VALUE || hand == null) {
                throw new IllegalArgumentException("invalid generated equipment settlement");
            }
        }
    }
    GeneratedEntityEquipmentSettlement beginGeneratedEntityEquipmentSettlement(long entityId,
            long expectedEntityRevision, long expectedPlayerRevision, PlayerAction.Hand hand) {
        if (generatedEquipmentSettlement != null) {
            throw new IllegalStateException("generated equipment settlement is already in flight");
        }
        long settlementId = nextGeneratedSessionId(generatedEquipmentSettlementSequence);
        generatedEquipmentSettlementSequence = settlementId;
        generatedEquipmentSettlement = new GeneratedEntityEquipmentSettlement(settlementId,
                entityId, expectedEntityRevision, expectedPlayerRevision, hand);
        return generatedEquipmentSettlement;
    }
    GeneratedEntityEquipmentSettlement generatedEntityEquipmentSettlement() {
        return generatedEquipmentSettlement;
    }
    boolean ownsGeneratedEntityEquipmentSettlement(long settlementId, long entityId,
            long expectedEntityRevision, long expectedPlayerRevision) {
        GeneratedEntityEquipmentSettlement current = generatedEquipmentSettlement;
        return current != null && current.settlementId() == settlementId
                && current.entityId() == entityId
                && current.expectedEntityRevision() == expectedEntityRevision
                && current.expectedPlayerRevision() == expectedPlayerRevision;
    }
    boolean completeGeneratedEntityEquipmentSettlement(long settlementId, long entityId,
            long expectedEntityRevision, long expectedPlayerRevision) {
        if (!ownsGeneratedEntityEquipmentSettlement(settlementId, entityId,
                expectedEntityRevision, expectedPlayerRevision)) return false;
        generatedEquipmentSettlement = null;
        return true;
    }
    void cancelGeneratedEntityEquipmentSettlement() {
        generatedEquipmentSettlement = null;
    }
    private static long nextGeneratedSessionId(long current) {
        long next = current + 1L;
        return next <= 0L || next == Long.MAX_VALUE ? 1L : next;
    }
    void openEnchanting(int x, int y, int z) { openEnchanting = new BlockPos(x, y, z); }
    BlockPos openEnchanting() { return openEnchanting; }
    void closeEnchanting() { openEnchanting = null; }
    void openCrafting(PlayerAction.CraftStation station, int x, int y, int z) {
        openCrafting(station, x, y, z, null);
    }
    void openCrafting(PlayerAction.CraftStation station, int x, int y, int z, Long requestId) {
        long nextSessionId = craftingSessionSequence + 1L;
        if (nextSessionId == 0L) nextSessionId = 1L;
        craftingSessionSequence = nextSessionId;
        openCraftingSessionId = nextSessionId;
        openCraftingRequestId = requestId;
        openCraftingStation = station;
        openCraftingTable = station == PlayerAction.CraftStation.INVENTORY
                ? null : new BlockPos(x, y, z);
        anvilRename = null;
        loomSelection = null;
    }
    PlayerAction.CraftStation openCraftingStation() { return openCraftingStation; }
    BlockPos openCraftingTable() { return openCraftingTable; }
    long openCraftingSessionId() { return openCraftingSessionId; }
    Long openCraftingRequestId() { return openCraftingRequestId; }
    void beginCraftingOpen(PlayerAction.OpenCrafting open) { pendingCraftingOpen = open; }
    boolean pendingCraftingOpenMatches(PlayerAction.OpenCrafting open) {
        return pendingCraftingOpen == open;
    }
    void cancelPendingCraftingOpen() { pendingCraftingOpen = null; }
    void cancelPendingCraftingOpen(Long requestId) {
        if (requestId != null && pendingCraftingOpen != null
                && requestId.equals(pendingCraftingOpen.requestId())) pendingCraftingOpen = null;
    }
    String anvilRename() { return anvilRename; }
    void anvilRename(String value) { anvilRename = value; }
    com.gameexpert.engine.inventory.LoomRules.Pattern loomSelection() { return loomSelection; }
    void loomSelection(com.gameexpert.engine.inventory.LoomRules.Pattern value) {
        loomSelection = value;
    }
    void closeCrafting() {
        openCraftingStation = null;
        openCraftingTable = null;
        openCraftingSessionId = 0L;
        openCraftingRequestId = null;
        anvilRename = null;
        loomSelection = null;
    }

    void openLectern(int x, int y, int z) { openLectern(x, y, z, null); }
    void openLectern(int x, int y, int z, Long requestId) {
        openLectern = new BlockPos(x, y, z);
        openLecternRequestId = requestId;
    }
    BlockPos openLectern() { return openLectern; }
    Long openLecternRequestId() { return openLecternRequestId; }
    void closeLectern() { openLectern = null; openLecternRequestId = null; }
    void openBook(PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot stack) {
        openBook(hand, stack, null);
    }
    void openBook(PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot stack, Long requestId) {
        if (hand == null || stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("book session requires exact hand and stack");
        }
        openBookHand = hand;
        openBookStack = stack;
        openBookRequestId = requestId;
    }
    PlayerInventory.HandRef openBookHand() { return openBookHand; }
    PlayerInventory.StackSnapshot openBookStack() { return openBookStack; }
    Long openBookRequestId() { return openBookRequestId; }
    boolean bookSessionMatchesCurrentStack() {
        return openBookHand != null && openBookStack != null
                && (openBookHand.hand() == PlayerInventory.Hand.OFFHAND
                        || inventory.selectedSlot() == openBookHand.mainSlot())
                && inventory.stack(openBookHand).equals(openBookStack);
    }
    void updateOpenBookStack(PlayerInventory.StackSnapshot stack) { openBookStack = stack; }
    void closeBook() { openBookHand = null; openBookStack = null; openBookRequestId = null; }

    void deferRespawn() {
        respawnRequested = true;
    }

    boolean respawnRequested() {
        return respawnRequested;
    }

    void respawn(double sx, double sy, double sz) {
        // [ARROW-GROUND] 리스폰은 새 몸이라 박힌 화살이 없다.
        stuckArrowCount = 0;
        removeArrowMcTicks = 0;
        consumeHealthFlags();
        respawnRequested = false;
        health = 20;
        dead = false;
        healthDirty = true;
        stateRevision++;
        justRespawned = true;
        healthCause = "respawn";
        submergedTicks = 0;
        drownAccum = 0;
        lavaAccum = 0;
        // 가루눈 누적도 함께 되돌린다. 남겨 두면 스폰 지점이 가루눈에 닿을 때
        // 70틱 유예 없이 첫 환경 틱에서 곧바로 동상 피해가 들어간다(정적판
        // StandalonePlayerVitals.respawn 은 처음부터 둘 다 0으로 되돌린다).
        powderSnowTicks = 0;
        freezeDamageAccum = 0;
        hurtProtectionTicks = 0;
        previousHurtDamage = 0;
        food = HungerRules.INITIAL_FOOD;
        saturationMilli = HungerRules.INITIAL_SATURATION_MILLI;
        exhaustionMilli = 0;
        movementExhaustionRemainder = 0;
        foodTickTimer = 0;
        regenHealMilli = 0;
        foodDirty = true;
        // [SURV-X] 사망 드랍은 틱 루프가 처리하고, 여기서는 누적 경험치를 확실히 0으로 되돌린다.
        setXpTotal(0);
        xpDirty = true;
        resetCombatUseState();
        clearStatusEffects();
        setFireTicks(0); // 리스폰 시 불 끔(§11.5)
        fireAccum = 0;
        forcePose(sx, sy, sz, yaw, pitch);
    }

    private void resetCombatUseState() {
        cancelHandUse();
        shieldDisabledUntilNanos = 0;
    }

    private void clearGoldenAppleEffects() {
        cancelBandage();
        fleshBandage.restore(0);
        regenerationMcTicksRemaining = 0;
        regenerationMcTickAccum = 0;
        absorptionTicksRemaining = 0;
        absorptionPoints = 0;
        fireResistanceTicksRemaining = 0;
    }

    // ── 브로드캐스트 플래그(틱 루프 ⑤에서 소비) ──
    boolean poseDirty() {
        return poseDirty || selectedItemId() != broadcastSelectedItemId
                || !selectedEnchantments().equals(broadcastSelectedEnchantments)
                || !armorSignature().equals(broadcastArmorSignature);
    }

    void consumePoseDirty() {
        poseDirty = false;
        broadcastSelectedItemId = selectedItemId();
        broadcastSelectedEnchantments = selectedEnchantments();
        broadcastArmorSignature = armorSignature();
    }

    /**
     * [ARMOR-TRIM] 착용 갑옷 네 칸의 종류·성분(트림·[EQUIPMENT] 염색)·인챈트(광택)와 [SHIELD-PATTERN] 두 손
     * 스택의 성분(무늬 층) 문자열. 바뀌면 pose 를 다시 싣는다.
     */
    private String armorSignature() {
        StringBuilder signature = new StringBuilder(32);
        for (com.gameexpert.engine.inventory.ArmorSlot slot
                : com.gameexpert.engine.inventory.ArmorSlot.values()) {
            PlayerInventory.StackSnapshot stack = inventory.equippedStack(slot);
            signature.append(stack.itemType()).append(':');
            if (stack.itemComponentData() != null) signature.append(stack.itemComponentData());
            signature.append(':').append(inventory.equippedWideEnchantments(slot).wireValue());
            signature.append('|');
        }
        for (PlayerInventory.Hand hand : PlayerInventory.Hand.values()) {
            PlayerInventory.StackSnapshot stack = inventory.stack(hand);
            signature.append(stack.itemType()).append(':');
            if (stack.itemComponentData() != null) signature.append(stack.itemComponentData());
            signature.append(';');
        }
        return signature.toString();
    }

    int drainTotemUseSounds() {
        int count = totemUseSounds;
        totemUseSounds = 0;
        return count;
    }

    boolean healthDirty() {
        return healthDirty;
    }

    boolean justDamaged() {
        return justDamaged;
    }

    String healthCause() {
        return healthCause;
    }

    String damageCause() {
        return damageCause;
    }

    String hurtKiller() {
        return hurtKiller;
    }

    boolean justDied() {
        return justDied;
    }

    boolean justRespawned() {
        return justRespawned;
    }

    // ── 주기 저장 dirty 가드 ──
    /** 마지막 저장 이후 영속 대상(pose·health·bed·inventory)이 하나라도 바뀌었는가. */
    boolean persistDirty() {
        return stateRevision != savedStateRevision || inventory.revision() != savedInvRevision;
    }

    /** 한 플레이어당 비동기 저장 하나만 허용한다. 실패 승인이 오면 다음 주기에 다시 제출됩니다. */
    boolean beginPersistence() {
        if (persistenceInFlight || !persistDirty()) return false;
        persistenceInFlight = true;
        return true;
    }

    long stateRevision() {
        return stateRevision;
    }

    /** DB 커밋 뒤 월드 소유 틱에서만 호출합니다. 제출 뒤 변경된 revision은 dirty로 남습니다. */
    void acknowledgePersistence(long persistedStateRevision, long persistedInvRevision) {
        savedStateRevision = Math.max(savedStateRevision, persistedStateRevision);
        savedInvRevision = Math.max(savedInvRevision, persistedInvRevision);
        persistenceInFlight = false;
    }

    /** 실패한 DB 작업은 어떤 revision도 승인하지 않고 재제출만 허용합니다. */
    void rejectPersistence() {
        persistenceInFlight = false;
    }

    /** 피격 사건의 송신 인계만 확인한다. 사망 정산이 쓰는 사유·가해자는 보존한다. */
    void consumeHurtEvent() {
        justDamaged = false;
        hurtKbX = 0.0;
        hurtKbY = 0.0;
        hurtKbZ = 0.0;
        hurtKbBonusX = 0.0;
        hurtKbBonusZ = 0.0;
    }

    void consumeHealthFlags() {
        healthDirty = false;
        consumeHurtEvent();
        justDied = false;
        justRespawned = false;
        healthCause = null;
        damageCause = null;
        hurtKiller = null;
    }
}
