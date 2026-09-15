package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.CombatRules;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.PlayerInteractionRules;
import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.RafflesiaRules;
import com.gameexpert.terrain.Blocks;

/**
 * 몹 공통 상태 + 헬퍼. 이동은 이 객체의 x/y/z/yaw/state 변이로 반영하고,
 * 한 틱의 외부 영향은 {@link #tick} 이 이벤트 목록으로 낸다.
 */
public abstract class Mob {
    /** Runtime-owned observer for the single durable mob-vehicle edge. Absent in leaf tests. */
    interface VehicleBindingListener {
        void onVehicleBindingChanged(Mob passenger, long previousVehicleId, long vehicleId);
    }

    private long horseMenuPersistenceRevision;
    /** Pufferfish species-local low bits carry PuffState 0..2. */
    public static final int VISUAL_PUFFERFISH_PUFF_SHIFT = 0;
    public static final int VISUAL_PUFFERFISH_PUFF_MASK = 0x3;
    public static final int VISUAL_DROWNED_TRIDENT = 1;
    /** Skeleton-family-only transient target yaw; not part of the persistence projection. */
    public static final int VISUAL_SKELETON_AIMING = 1;
    /**
     * [MOUNT] 안장을 얹은 낙타. 낙타는 다른 시각 플래그를 쓰지 않으므로 하위 비트를 그대로
     * 재사용한다 — 이 묶음의 계약대로 비트가 겹쳐도 종이 다르면 섞이지 않는다.
     */
    public static final int VISUAL_CAMEL_SADDLED = 1;
    /** [MOUNT] 앉은 자세의 낙타(바닐라 {@code Camel#isCamelSitting}). */
    public static final int VISUAL_CAMEL_SITTING = 1 << 1;
    public static final int VISUAL_BEE_NECTAR = 1 << 1;
    public static final int VISUAL_BEE_STUNG = 1 << 2;
    public static final int VISUAL_BEE_ANGRY = 1 << 3;
    public static final int VISUAL_BAT_RESTING = 1 << 4;
    public static final int VISUAL_GLOW_SQUID_DARKENED = 1 << 5;
    public static final int VISUAL_ARMADILLO_ROLLING = 1 << 6;
    public static final int VISUAL_ARMADILLO_SCARED = 1 << 7;
    public static final int VISUAL_ARMADILLO_UNROLLING = 1 << 8;
    public static final int VISUAL_ARMADILLO_HIDDEN_IN_SHELL = 1 << 9;
    public static final int VISUAL_WOLF_TAMED = 1 << 10;
    /** Cat/Parrot-scoped visual bits; unrelated species retain their own encoding. */
    public static final int VISUAL_COMPANION_TAMED = 1;
    public static final int VISUAL_COMPANION_SITTING = 1 << 1;
    public static final int VISUAL_CAT_COLLAR_SHIFT = 2;
    public static final int VISUAL_CAT_COLLAR_MASK = 0xf << VISUAL_CAT_COLLAR_SHIFT;
    public static final int VISUAL_WOLF_SITTING = 1 << 11;
    /** Bits 12–15 carry the exact MC DyeColor network id (0..15). */
    public static final int VISUAL_WOLF_COLLAR_SHIFT = 12;
    public static final int VISUAL_WOLF_COLLAR_MASK = 0xF << VISUAL_WOLF_COLLAR_SHIFT;
    public static final int VISUAL_WOLF_BODY_ARMOR = 1 << 16;
    /** Bits 17–18 carry Crackiness.Level ordinal: NONE=0, LOW=1, MEDIUM=2, HIGH=3. */
    public static final int VISUAL_WOLF_ARMOR_CRACK_SHIFT = 17;
    public static final int VISUAL_WOLF_ARMOR_CRACK_MASK = 0x3 << VISUAL_WOLF_ARMOR_CRACK_SHIFT;
    /** 환술사 자기 투명화. 클라는 이 비트로 본체를 숨기고 클라 전용 거울상 4개를 그린다. */
    public static final int VISUAL_ILLUSIONER_INVISIBLE = 1 << 19;
    /** [FARM-ANIMAL] 전단된 양. 클라는 양털 껍데기를 그리지 않는다. */
    public static final int VISUAL_SHEEP_SHEARED = 1 << 20;
    /**
     * [CONTAINER-MENUS] A snow golem whose pumpkin was sheared off / a sheared bogged. Visual bits are
     * species-scoped, so they share {@link #VISUAL_SHEEP_SHEARED}'s number without mixing.
     */
    public static final int VISUAL_SNOW_GOLEM_PUMPKINLESS = 1 << 20;
    public static final int VISUAL_BOGGED_SHEARED = 1 << 20;
    /** Bits 21–24 carry the exact MC DyeColor network id (0..15) of the sheep wool. */
    public static final int VISUAL_SHEEP_COLOR_SHIFT = 21;
    public static final int VISUAL_SHEEP_COLOR_MASK = 0xF << VISUAL_SHEEP_COLOR_SHIFT;
    /** 풀 먹기 애니메이션 중. 바닐라 {@code EatBlockGoal} 의 40 MC 틱 구간이다. */
    public static final int VISUAL_SHEEP_EATING = 1 << 25;
    /** [FARM-ANIMAL] 안장을 얹은 돼지. */
    public static final int VISUAL_PIG_SADDLED = 1 << 26;
    /** [FARM-ANIMAL] 부스트 중인 돼지(당근 낚싯대). */
    public static final int VISUAL_PIG_BOOSTING = 1 << 27;
    /**
     * 낙뢰를 맞은 크리퍼(바닐라 {@code DATA_IS_POWERED}). 클라는 이 비트로 전기 오라를 그리고,
     * 서버는 폭발력을 2배로 쓴다.
     */
    public static final int VISUAL_CREEPER_POWERED = 1 << 28;
    /** [MOUNT] 안장을 얹은 말 계열. 돼지 안장과 별도 비트라 종별 디코딩이 섞이지 않는다. */
    public static final int VISUAL_HORSE_SADDLED = 1 << 29;
    /** [MOUNT] 길들인 말 계열(바닐라 {@code AbstractHorse#isTamed}). */
    public static final int VISUAL_HORSE_TAMED = 1 << 30;
    /** 좀비 주민 치료 카운트다운 진행 중. 클라는 이 비트로 바닐라 "몸 떨림"을 그린다. */
    public static final int VISUAL_ZOMBIE_VILLAGER_CONVERTING = 1 << 20;
    /**
     * [MOUNT] 상자를 단 말 계열(당나귀·노새·라마). visual 비트는 <b>종별 스코프</b>라 양 전용
     * 비트와 번호가 겹쳐도 섞이지 않는다({@code VISUAL_ZOMBIE_VILLAGER_CONVERTING} 이 이미
     * {@code VISUAL_SHEEP_SHEARED} 와 같은 비트를 쓰는 것과 같은 계약이다).
     */
    public static final int VISUAL_HORSE_CHESTED = 1 << 25;
    /**
     * [HARNESS] 하네스를 쓴 해피 가스트. 클라 {@code HappyGhastModel} 이 이 비트로
     * 바닐라 하네스 레이어를 보이고 숨긴다({@code MOB_VISUAL_HAPPY_GHAST_HARNESSED} 와 같은 29번).
     * visual 비트는 <b>종별 스코프</b>라 {@link #VISUAL_HORSE_SADDLED} 와 번호가 겹쳐도
     * 섞이지 않는다(위 좀비 주민/양 선례와 같은 계약).
     */
    public static final int VISUAL_HAPPY_GHAST_HARNESSED = 1 << 29;
    /**
     * [NAUTILUS-MOUNT] 안장을 얹은 노틸러스. visual 비트는 <b>종별 스코프</b>라
     * {@link #VISUAL_CAMEL_SADDLED} 와 번호가 겹쳐도 섞이지 않는다(좀비 주민/양 선례와 같은
     * 계약). 노틸러스는 다른 시각 플래그를 쓰지 않으므로 하위 비트를 그대로 재사용한다.
     */
    public static final int VISUAL_NAUTILUS_SADDLED = 1;
    /** [NAUTILUS-MOUNT] 노틸러스 갑옷을 입은 개체. 정확한 티어는 별도 item-id wire가 운반한다. */
    public static final int VISUAL_NAUTILUS_ARMORED = 1 << 1;
    /** [MOUNT] 카펫을 두른 라마. 색은 아래 4비트가 나른다. */
    public static final int VISUAL_LLAMA_CARPET = 1 << 20;
    /** Bits 21–24 carry the exact MC DyeColor network id (0..15) of the llama carpet. */
    public static final int VISUAL_LLAMA_CARPET_COLOR_SHIFT = 21;
    public static final int VISUAL_LLAMA_CARPET_COLOR_MASK = 0xF << VISUAL_LLAMA_CARPET_COLOR_SHIFT;
    /**
     * [HARNESS] Happy-ghast-only bits 0–3: MC DyeColor network id of the worn harness (meaningful
     * only with {@link #VISUAL_HAPPY_GHAST_HARNESSED}); client {@code MOB_VISUAL_HAPPY_GHAST_HARNESS_COLOR_*}.
     */
    public static final int VISUAL_HAPPY_GHAST_HARNESS_COLOR_SHIFT = 0;
    public static final int VISUAL_HAPPY_GHAST_HARNESS_COLOR_MASK = 0xF << VISUAL_HAPPY_GHAST_HARNESS_COLOR_SHIFT;
    /** [HARNESS] Happy-ghast-only bit 4: a seat is occupied (vanilla {@code Entity#isVehicle}). */
    public static final int VISUAL_HAPPY_GHAST_RIDDEN = 1 << 4;
    /** [MOB-LOOK] Warden-scoped: vanilla Pose.EMERGING / Pose.DIGGING. */
    public static final int VISUAL_WARDEN_EMERGING = 1;
    public static final int VISUAL_WARDEN_DIGGING = 1 << 1;
    /** [MOB-LOOK] Turtle-scoped: vanilla Turtle#hasEgg / Turtle#isLayingEgg. */
    public static final int VISUAL_TURTLE_HAS_EGG = 1;
    public static final int VISUAL_TURTLE_LAYING_EGG = 1 << 1;
    /** [MOB-LOOK] Allay-scoped: vanilla Allay.DATA_DANCING. */
    public static final int VISUAL_ALLAY_DANCING = 1;
    /**
     * Iron-golem-only bits 0–1: {@code IronGolem#getCrackiness} (NONE, LOW, MEDIUM, HIGH);
     * client {@code MOB_VISUAL_IRON_GOLEM_CRACK_*}.
     */
    public static final int VISUAL_IRON_GOLEM_CRACK_SHIFT = 0;
    public static final int VISUAL_IRON_GOLEM_CRACK_MASK = 0x3 << VISUAL_IRON_GOLEM_CRACK_SHIFT;
    /**
     * Villager / zombie-villager bits 0–9: VillagerData biome type (0–2), profession (3–6) and
     * level − 1 (7–9) in {@link com.gameexpert.engine.mob.villager.VillagerAppearance} wire order;
     * client {@code MOB_VISUAL_VILLAGER_*}.
     */
    public static final int VISUAL_VILLAGER_TYPE_SHIFT = 0;
    public static final int VISUAL_VILLAGER_TYPE_MASK = 0x7 << VISUAL_VILLAGER_TYPE_SHIFT;
    public static final int VISUAL_VILLAGER_PROFESSION_SHIFT = 3;
    public static final int VISUAL_VILLAGER_PROFESSION_MASK = 0xF << VISUAL_VILLAGER_PROFESSION_SHIFT;
    public static final int VISUAL_VILLAGER_LEVEL_SHIFT = 7;
    public static final int VISUAL_VILLAGER_LEVEL_MASK = 0x7 << VISUAL_VILLAGER_LEVEL_SHIFT;
    /**
     * Copper-golem-only bits ({@link CopperGolem#visualFlags}): 0–1 weathering stage, 2 waxed,
     * 3–4 statue pose; client {@code MOB_VISUAL_COPPER_GOLEM_*}.
     */
    public static final int VISUAL_COPPER_GOLEM_OXIDATION_SHIFT = 0;
    public static final int VISUAL_COPPER_GOLEM_OXIDATION_MASK = 0x3 << VISUAL_COPPER_GOLEM_OXIDATION_SHIFT;
    public static final int VISUAL_COPPER_GOLEM_WAXED = 1 << 2;
    public static final int VISUAL_COPPER_GOLEM_POSE_SHIFT = 3;
    public static final int VISUAL_COPPER_GOLEM_POSE_MASK = 0x3 << VISUAL_COPPER_GOLEM_POSE_SHIFT;
    /** 정확한 Java 기본값은 이번 제한 검색에서 미확인. WebCraft가 채택한 3초 기억값. */
    public static final int TARGET_UNSEEN_MEMORY_TICKS = 30;
    /** LOS 갱신은 2틱(0.2초)마다 수행해 최대 70몹의 광선 비용을 절반으로 제한한다. */
    public static final int TARGET_LOS_INTERVAL_TICKS = 2;
    /** 정확한 Java 지속시간은 이번 제한 검색에서 미확인. WebCraft가 채택한 화상 지속 8초. */
    public static final int SUN_FIRE_TICKS = 80;
    /** 정확한 Java 주기는 이번 제한 검색에서 미확인. WebCraft 매핑: 1초마다 1 damage point. */
    public static final int FIRE_DAMAGE_INTERVAL_TICKS = 10;
    /** 일반 생명체의 15초 공기와 이후 1초마다 2점인 바닐라 익사를 10TPS 실시간으로 환산한다. */
    static final int MAX_AIR_SUPPLY_TICKS = 150;
    static final int AXOLOTL_MAX_AIR_SUPPLY_TICKS = 3_000;
    static final int DROWN_DAMAGE_INTERVAL_TICKS = 10;
    static final int DROWN_DAMAGE_POINTS = 2;
    static final float FLOAT_GOAL_CHANCE = 0.8f;
    static final double FLOAT_GOAL_JUMP_IMPULSE = 0.04;
    static final int FLOAT_GOAL_DRAWS_PER_TICK = 2;
    /** 제품 규칙: 좀비·허스크는 머리가 연속으로 30초 잠기면 다음 수중 단계로 변환된다. */
    static final int WATER_CONVERSION_TICKS = 300;
    private static final int AIR_RECOVERY_PER_TICK = 8;
    public long id;
    public final MobType type;
    public double x, y, z;        // 발밑 중심(§11.1)
    public double yaw;            // 바라보는 방향(라디안, 연출)
    /** [MOB-LOOK] 머리 절대 방향(라디안, yaw 와 같은 규약). NaN 이면 몸 방향이다. 영속하지 않는다. */
    public double headYawAbs = Double.NaN;
    /** [MOB-LOOK] 머리 pitch(라디안, 바닐라 xRot: 양수 = 아래). 영속하지 않는다. */
    public double headPitch;
    public double vy;             // 수직 속도(블록/틱)
    /** 서버 권위 넓백 수평 속도(블록/10TPS 틱). */
    public double knockbackVx, knockbackVz;
    /** 직전 물리 틱에서 실제로 해소된 수평 운동(블록/10TPS 틱). */
    public double horizontalVx, horizontalVz;
    private boolean knockbackOverridesAi;
    public boolean onGround;
    public int hp;
    /** MC 는 소수 피해를 보존한다. hp 는 기존 정수 WS 스냅샷용 올림값이고 이 값이 전투 정본이다. */
    private double healthPoints;
    private long lastHurtTick = Long.MIN_VALUE;
    private double previousHurtDamage;
    /** 바닐라 LivingEntity.lastHurtByPlayerTime=100 MC틱을 10TPS 권위 틱으로 환산한 값. */
    private static final int PLAYER_KILL_CREDIT_TICKS = 50;
    /** 저장하지 않는 최근 플레이어 처치 크레딧. 재기동을 가로질러 드랍을 승격하지 않는다. */
    private String lastPlayerHurtNickname;
    private int playerKillCreditTicksRemaining;
    public MobState state = MobState.IDLE;
    public final double spawnY;   // 스폰 시 발 y(스냅샷/디버그용)
    public boolean removed;       // true 면 매니저가 제거(예: 크리퍼 폭발 후)
    /** 최근 플레이어에서 32블록 밖에 연속으로 머문 20TPS 논리 틱 수. */
    int farDespawnTicks;

    /** 자연 장비와 구분되는, 땅에서 주운 장비가 하나라도 있는지 여부. 디스폰 면제의 정본이다. */
    private boolean pickedUpEquipment;
    /** [TRIAL-GAP] 트라이얼 장비 표가 입힌 장비는 바닐라 slot_drop_chances 0.0 이다. */
    private boolean trialEquipmentNoDrop;
    private short heldItem;
    private int heldItemDurability;
    private short offhandItem;
    private short replacedEquipment;
    private int replacedEquipmentDurability;
    private final short[] equippedArmor = new short[ArmorSlot.values().length];
    private final int[] equippedArmorDurability = new int[ArmorSlot.values().length];
    /**
     * [MOB-EQUIP] 칸별 스택 성분({@link MobEquipmentRules}): 워드 0 인챈트와 성분 문자열
     * ({@code ItemComponentCodec} — 확장 인챈트·사용자 이름·가죽 색·장래의 갑옷 장식). 성분 문자열은
     * 해석하지 않고 그대로 옮긴다. 주손이 빈 해골 계열·약탈자는 고유 활/석궁의 성분을 이 칸에 둔다.
     */
    private long heldItemEnchantments;
    private String heldItemComponents;
    private long replacedEquipmentEnchantments;
    private String replacedEquipmentComponents;
    private boolean replacedEquipmentDrops;
    private final long[] equippedArmorEnchantments = new long[ArmorSlot.values().length];
    private final String[] equippedArmorComponents = new String[ArmorSlot.values().length];
    /**
     * [MOB-EQUIP] 칸별 드랍 확률 계층(바닐라 {@code DropChances}). 보장(주운 장비, 2.0) 비트와 드랍 없음
     * (트라이얼 장비, 0.0) 비트이며 나머지 칸은 자연 8.5%다. 비트는 {@link MobEquipmentRules#MAIN_HAND_BIT}
     * · {@link MobEquipmentRules#armorBit}. 옛 행은 몹 단위 불리언에서 파생한다.
     */
    private int guaranteedDropSlots;
    private int noDropSlots;
    /** 0이면 미탑승. 보트가 매 틱 좌석 좌표를 갱신한다. */
    private long mountedBoatId;
    private long placedVehicleId;
    private int placedBoardingCooldown;
    private java.util.function.LongPredicate placedVehicleDismount;
    private double vehicleX, vehicleY, vehicleZ, vehicleYaw;

    // 단순 지역 회피 상태. 경로 그래프 없이 몇 틱간 막혔는지만 기억한다.
    int blockedMoveTicks;
    int detourTicks;
    int detourSign;

    // 적대 AI 타겟 기억. 플레이어 객체는 매 틱 스냅샷이 바뀌므로 nickname만 보존한다.
    private String targetNickname;
    /** Social target prepared from the current active list; never persisted or sent to clients. */
    private Mob preparedSocialTarget;
    /** Transient standard-bearer aura, rebuilt from the active raid set every tick. */
    private double preparedRaidSpeedMultiplier = 1.0;
    // 길들임/이름은 런타임 소유 상태이며, 둘 중 하나라도 있으면 서버 재시작 뒤 복원한다.
    private String ownerNickname;
    private boolean ocelotTrusting;
    private boolean wolfSitting;
    private String customName;
    /** 양동이 방출처럼 바닐라가 자연 디스폰에서 제외하는 서버 권위 지속성. */
    private boolean persistenceRequired;
    /** Durable encounter identity for raid/patrol/site illagers and their rare companions. */
    private IllagerCompanionPolicy.Context illagerContext;
    private long illagerContextIdentity;
    private int illagerPolicyVersion;
    private boolean companionDecisionSettled;
    private boolean companionDecisionWinner;
    /** Owning raid instance for released raiders and their companions; 0 outside a raid. */
    private long raidId;
    private int raidWave;
    private int targetLosCooldown;
    private int unseenTargetTicks;
    private boolean targetVisible;
    private double lastSeenTargetX, lastSeenTargetY, lastSeenTargetZ;

    // 좀비/스켈레톤 햇빛 연소 상태(10TPS 실시간 단위).
    private int fireTicks;
    private int fireDamageTicks;

    // 머리 잠김 기준 서버 권위 공기/변환 상태. 틱·저장 모두 클라이언트 추론에 의존하지 않는다.
    private int airSupplyTicks = MAX_AIR_SUPPLY_TICKS;
    private int underwaterConversionTicks;
    /** 이번 authority tick의 순서 있는 두 FloatGoal 결과(bit0→bit1). -1이면 비활성이다. */
    private int preparedFloatGoalMask = -1;

    // 교배 수치는 종별 구현 트랙이 주입한다. 0은 성체/비 love-mode이며 별도 MobType은 만들지 않는다.
    private int ageTicksRemaining;
    private int loveTicksRemaining;
    private int breedingCooldownTicks;
    /** BABY_ZOMBIE 및 그 변환 결과의 영구적인 어린 외형. 동물 성장 타이머와 분리한다. */
    private boolean babyForm;
    /** 실제 공격·발사·쏘기 이벤트가 발생할 때만 증가하는 렌더 동기화 커서. */
    private int actionSequence;
    private String nativeEquipment = "none";
    private String actionKind = "none";
    private String actionPhase = "idle";
    private int actionTicksRemaining;
    private long vehicleMobId;
    private VehicleBindingListener vehicleBindingListener;

    // 배회 상태(무목표 시 저속 이동)
    protected int wanderTimer;
    protected double wdx, wdz;
    private int rafflesiaOdorX;
    private int rafflesiaOdorY;
    private int rafflesiaOdorZ;
    private boolean rafflesiaOdorPresent;
    private boolean rafflesiaOdorInitialized;
    private RafflesiaRules.OdorResponse rafflesiaOdorResponse = RafflesiaRules.OdorResponse.NONE;

    // 서버 권위 생활음 상태. 스폰 ID로 위상을 고정해 재현 가능하며 클라이언트 타이머를 두지 않는다.
    private boolean soundPositionReady;
    private double soundX, soundZ;
    private double soundStepDistance;
    /** 바닐라 {@code Mob.ambientSoundTime}. MC 틱마다 1 증가하고 발성 시 -interval 로 되감긴다. */
    private int ambientSoundTime;
    private int ambientRolls;
    /** MC 20TPS ↔ WebCraft 10TPS. 바닐라 ambientSoundTime 은 MC 틱마다 1 증가한다. */
    private static final int MC_TICKS_PER_SERVER_TICK = 2;
    /**
     * 바닐라 {@code Entity.applyMovementEmissionAndPlaySound}: {@code moveDist += horizontalDistance * 0.6F}
     * 이고 {@code moveDist > nextStep} 이면 발소리 후 {@code nextStep = (int) moveDist + 1} 이다.
     * 즉 0.6·이동거리가 정수를 넘을 때마다 한 걸음이고, 종·크기와 무관하게 1/0.6 블록 간격이다.
     */
    private static final double STEP_STRIDE = 1.0 / 0.6;

    // towardHoriz/wander 의 수평 변위 반환용 재사용 2-슬롯 버퍼(호출부가 즉시 소비, 몹당 1개).
    private final double[] moveBuf = new double[2];

    // 상태이상(FX A1). 몹은 감속·나약·독만 받고 저장하지 않는다(화염 틱과 같은 휘발 상태).
    private final StatusEffects statusEffects = new StatusEffects();

    protected Mob(long id, MobType type, double x, double y, double z) {
        this.id = id;
        this.type = type;
        this.x = x; this.y = y; this.z = z;
        this.spawnY = y;
        this.healthPoints = type.maxHp();
        this.hp = type.maxHp();
        this.babyForm = type == MobType.BABY_ZOMBIE;
        this.airSupplyTicks = maxAirSupplyTicks();
    }

    /**
     * Builds the overwhelmingly-empty per-mob event result without allocating on quiet ticks.
     * The first event uses the JDK singleton list; only the uncommon multi-event tick allocates
     * a mutable list. Returned values remain stable across later ticks, unlike a reused mob-local
     * buffer.
     */
    protected static List<MobEvent> appendEvent(List<MobEvent> events, MobEvent event) {
        if (events.isEmpty()) return List.of(event);
        if (events instanceof ArrayList<MobEvent> mutable) {
            mutable.add(event);
            return mutable;
        }
        List<MobEvent> multiple = new ArrayList<>(4);
        multiple.addAll(events);
        multiple.add(event);
        return multiple;
    }

    private double growthScale() {
        if (!isBaby() || type == MobType.BABY_ZOMBIE) return 1.0;
        return switch (type) {
            case ARMADILLO -> 0.6;
            case DOLPHIN -> 0.65;
            default -> 0.5;
        };
    }
    public double width() { return type.width() * growthScale(); }
    public double height() { return type.height() * growthScale(); }

    /**
     * [EC-MOBS] 권위 명중·사거리 판정이 쓰는 월드 AABB {@code {minX, minY, minZ, maxX, maxY, maxZ}}.
     * 기본은 발밑 중심 정사각 상자이고, 벽에 걸린 아이템 액자(바닐라 상자 중심 위치 · 두께 1/16)와
     * 부착면 쪽으로 열리는 셜커만 재정의한다.
     */
    public double[] authorityAabb() {
        double half = width() * 0.5;
        return new double[] {x - half, y, z - half, x + half, y + height(), z + half};
    }

    /** [EC-MOBS] 와이어 {@code heldItemId}. 아이템 액자는 넣은 아이템을 여기로 보인다. */
    public short displayedHeldItem() { return heldItem(); }

    /**
     * [EC-MOBS] 스스로 움직이지도 밀리지도 않는 개체(셜커 {@code getDeltaMovement 0}·{@code push} 무시, 걸린
     * 아이템 액자, 엔드 수정, 드래곤전 런타임이 옮기는 드래곤). 겹침 분리와 넉백이 이 개체를 옮기지 않는다.
     */
    public boolean immovable() { return false; }

    /**
     * [DRAGON] 투사체·근접이 맞히는 부위 상자들({@code minX, minY, minZ, maxX, maxY, maxZ}). null 이면 몸 상자
     * 하나다. 엔더 드래곤만 여덟 부위({@code EnderDragonPart})를 돌려준다.
     */
    public double[][] partHitBoxes() { return null; }
    public int maxHp() {
        return type == MobType.WOLF && ownerNickname != null ? 40 : type.maxHp();
    }
    /** 클라이언트가 추론하지 않는 서버 권위 이동 매질. */
    /** 활성 가디언 레이저가 추적하는 권위의 표적 몸 중심 좌표. */
    public double[] guardianBeamTarget() { return null; }

    public String movementMedium() { return "land"; }
    public double armor() {
        double total = type.armor();
        for (short item : equippedArmor) total += PlayerInventory.armorPoints(item);
        if (this instanceof NautilusFamilyMob nautilus) {
            total += NautilusMountRules.armorPoints(nautilus.armorTier());
        }
        if (this instanceof AbstractHorseMob horse) total += HorseRules.armorPoints(horse.armorItem());
        return total;
    }
    public double toughness() {
        double total = type.toughness();
        for (short item : equippedArmor) total += PlayerInventory.armorToughness(item);
        if (this instanceof NautilusFamilyMob nautilus) {
            total += NautilusMountRules.armorToughness(nautilus.armorTier());
        }
        if (this instanceof AbstractHorseMob horse) {
            total += HorseRules.armorToughness(horse.armorItem());
        }
        return total;
    }
    /**
     * 바닐라 {@code KNOCKBACK_RESISTANCE} 속성값: 종류 기본값({@link MobKnockbackResistance}) + 몸통
     * 방어구 몫 + 네더라이트 방어구 조각 + [ZOMBIE-KB] 좀비 계열 스폰 보너스 + [SULFUR-KB] 유황 큐브
     * 아키타입 수정치, {@code [-2, 1]}. 근접·밀어내기·염소 들이받기가 모두 이 값을 읽는다.
     */
    public double knockbackResistance() {
        if (immovable()) return 1.0;
        double equipment = 0.0;
        if (this instanceof NautilusFamilyMob nautilus) {
            equipment = NautilusMountRules.armorKnockbackResistance(nautilus.armorTier());
        } else if (this instanceof AbstractHorseMob horse) {
            equipment = HorseRules.armorKnockbackResistance(horse.armorItem());
        }
        equipment += MobKnockbackResistance.armorPieces(equippedArmor);
        equipment += MobKnockbackResistance.zombieRandomSpawnBonus(type, id);
        if (this instanceof SulfurCube cube && cube.archetype() != null) {
            equipment += cube.archetype().knockbackResistance;
        }
        return MobKnockbackResistance.total(type, equipment);
    }
    public double exactHealth() { return healthPoints; }
    public boolean isOnFire() { return fireTicks > 0; }
    public void extinguish() { fireTicks = 0; }
    public boolean hasPickedUpEquipment() { return pickedUpEquipment; }
    public short heldItem() { return heldItem; }
    public short offhandItem() { return offhandItem; }
    public int heldItemDurability() { return heldItemDurability; }
    public short equippedItem(ArmorSlot slot) { return equippedArmor[slot.ordinal()]; }
    public int equippedItemDurability(ArmorSlot slot) {
        return equippedArmorDurability[slot.ordinal()];
    }
    /** [MOB-EQUIP] 주손(또는 고유 활/석궁)의 워드 0 인챈트. */
    public long heldItemEnchantments() { return heldItemEnchantments; }
    /** [MOB-EQUIP] 주손(또는 고유 활/석궁)의 성분 문자열, 없으면 null. */
    public String heldItemComponents() { return heldItemComponents; }
    public long equippedItemEnchantments(ArmorSlot slot) {
        return equippedArmorEnchantments[slot.ordinal()];
    }
    public String equippedItemComponents(ArmorSlot slot) {
        return equippedArmorComponents[slot.ordinal()];
    }
    /** [MOB-EQUIP] 전투가 읽는 주손 무기의 43종 인챈트(날카로움·밀치기·발화·힘·밀어내기·화염 등). */
    public com.gameexpert.engine.enchant.WideEnchantments heldWeaponEnchantments() {
        if (heldItemEnchantments == 0L && heldItemComponents == null) {
            return com.gameexpert.engine.enchant.WideEnchantments.EMPTY;
        }
        return PlayerInventory.wideEnchantmentsOf(heldItemEnchantments, heldItemComponents);
    }
    /** [MOB-EQUIP] 방어구 한 칸의 43종 인챈트. */
    public com.gameexpert.engine.enchant.WideEnchantments equippedWideEnchantments(ArmorSlot slot) {
        int i = slot.ordinal();
        if (equippedArmorEnchantments[i] == 0L && equippedArmorComponents[i] == null) {
            return com.gameexpert.engine.enchant.WideEnchantments.EMPTY;
        }
        return PlayerInventory.wideEnchantmentsOf(equippedArmorEnchantments[i],
                equippedArmorComponents[i]);
    }
    /**
     * [MOB-EQUIP] 바닐라 {@code Mob.doHurtTarget} 의 {@code EnchantmentHelper.modifyDamage}: 주손 무기의
     * 날카로움(대상 무관)·강타(#undead)·살충(#arthropod)·찌르기(#aquatic) 추가 피해. {@code target} 이 null
     * 이면 플레이어 대상이다(날카로움만). 플레이어 근접과 같은 {@code meleeEnchantmentBonusMilli} 를 쓴다.
     */
    public final double heldWeaponMeleeBonus(MobType target) {
        if (heldItem == PlayerInventory.EMPTY) return 0.0;
        com.gameexpert.engine.enchant.WideEnchantments weapon = heldWeaponEnchantments();
        if (weapon.isEmpty()) return 0.0;
        int milli = com.gameexpert.engine.enchant.EnchantmentRules.meleeEnchantmentBonusMilli(
                heldItem, weapon,
                target != null && UndeadNeutralityRules.isUndead(target),
                target != null && com.gameexpert.engine.enchant.EnchantmentRules.isArthropod(target),
                target != null && com.gameexpert.engine.enchant.EnchantmentRules.isAquatic(target));
        return milli / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
    }

    /** [MOB-EQUIP] 무기 피해(종 기본값과 들고 있는 무기 중 큰 값)에 인챈트 추가 피해를 더해 올림한다. */
    protected final int enchantedMeleeDamage(double baseDamage, MobType target) {
        return (int) Math.ceil(baseDamage + heldWeaponMeleeBonus(target));
    }

    /** [MOB-EQUIP] 주손 무기가 효과를 내는 인챈트 레벨(밀치기·발화·살충 등), 효과가 없는 무기면 0. */
    public final int heldWeaponEnchantmentLevel(int enchantId) {
        if (heldItem == PlayerInventory.EMPTY) return 0;
        return com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(enchantId, heldItem)
                ? heldWeaponEnchantments().level(enchantId) : 0;
    }

    /** [MOB-EQUIP] 보장 드랍(주운 장비) 칸 비트. */
    public int guaranteedDropSlots() { return guaranteedDropSlots; }
    /** [MOB-EQUIP] 드랍 없음(트라이얼 장비) 칸 비트. */
    public int noDropSlots() { return noDropSlots; }
    /** [MOB-EQUIP] 비어 있지 않은 장비 칸 비트. */
    public int occupiedEquipmentSlots() {
        int mask = heldItem == PlayerInventory.EMPTY ? 0 : MobEquipmentRules.MAIN_HAND_BIT;
        for (int i = 0; i < equippedArmor.length; i++) {
            if (equippedArmor[i] != PlayerInventory.EMPTY) mask |= MobEquipmentRules.armorBit(i);
        }
        return mask;
    }
    /** [MOB-EQUIP] 어느 칸이든 인챈트·성분을 지녔는가(영속 열을 쓸지 정한다). */
    public boolean hasEquipmentComponents() {
        if (heldItemEnchantments != 0L || heldItemComponents != null) return true;
        for (int i = 0; i < equippedArmor.length; i++) {
            if (equippedArmorEnchantments[i] != 0L || equippedArmorComponents[i] != null) return true;
        }
        return false;
    }
    /**
     * [MOB-EQUIP] 주손이 비어 있을 때 쥐고 있는 고유 원거리 무기(해골 계열 활, 약탈자 석궁). 이 저장소는
     * 그 무기를 빈 손(0)으로 나타내므로 성분만 주손 칸에 둔다. 없으면 0.
     */
    public short nativeRangedWeapon() {
        if (this instanceof Skeleton) return PlayerInventory.BOW;
        if (type == MobType.PILLAGER) return PlayerInventory.CROSSBOW;
        return PlayerInventory.EMPTY;
    }
    /** 바닐라 {@code getPreferredWeaponType()}: 해골 계열 {@code #skeleton_preferred_weapons}(활), 드라운드 삼지창. */
    short preferredWeapon() {
        if (this instanceof Skeleton) return PlayerInventory.BOW;
        if (type == MobType.DROWNED) return PlayerInventory.TRIDENT;
        return PlayerInventory.EMPTY;
    }
    /** 주손 칸의 실제 스택. 빈 손 + 고유 무기는 그 무기(내구 만재)로 본다. */
    MobEquipmentRules.Stack heldStack() {
        short item = heldItem;
        int durability = heldItemDurability;
        if (item == PlayerInventory.EMPTY) {
            item = nativeRangedWeapon();
            durability = item == PlayerInventory.EMPTY ? 0 : PlayerInventory.initialDurability(item);
        }
        return item == PlayerInventory.EMPTY ? MobEquipmentRules.Stack.EMPTY
                : new MobEquipmentRules.Stack(item, durability, heldItemEnchantments, heldItemComponents);
    }
    MobEquipmentRules.Stack armorStack(int ordinal) {
        return equippedArmor[ordinal] == PlayerInventory.EMPTY ? MobEquipmentRules.Stack.EMPTY
                : new MobEquipmentRules.Stack(equippedArmor[ordinal], equippedArmorDurability[ordinal],
                        equippedArmorEnchantments[ordinal], equippedArmorComponents[ordinal]);
    }
    /** 서버에서 실제로 들고 있는 블록 ID. 엔더맨 외에는 0이다. */
    public int carriedBlockId() { return 0; }
    /** 역할 고유 장비. 등록 아이템과 중복 렌더하지 않는 현재 wire 의미다. */
    public String nativeEquipment() { return nativeEquipment; }
    protected final void setNativeEquipment(String equipment) {
        nativeEquipment = equipment == null ? "none" : equipment;
    }

    /** Installs deterministic spawn equipment without marking it as picked-up loot. */
    protected final void installGeneratedHeldItem(short itemType) {
        installGeneratedHeldItem(itemType, 0L, null);
    }

    /** [MOB-EQUIP] 성분을 지닌 생성 주손 장비(트라이얼 장비 표 등). 자연 칸(8.5%)으로 둔다. */
    protected final void installGeneratedHeldItem(short itemType, long enchantments, String components) {
        int durability = itemType == 0 ? 0 : PlayerInventory.initialDurability(itemType);
        validateEquipmentDurability(itemType, durability);
        validateSlotComponents(itemType, enchantments, components);
        heldItem = itemType;
        heldItemDurability = durability;
        heldItemEnchantments = enchantments;
        heldItemComponents = components;
        guaranteedDropSlots &= ~MobEquipmentRules.MAIN_HAND_BIT;
        noDropSlots &= ~MobEquipmentRules.MAIN_HAND_BIT;
        pickedUpEquipment = guaranteedDropSlots != 0;
    }

    /**
     * [MOB-EQUIP] 빈 손으로 나타내는 고유 원거리 무기(해골 계열 활·약탈자 석궁)의 성분을 싣는다.
     * 트라이얼 원거리 장비 표의 힘 I·밀어내기 I 활이 이 자리로 들어간다.
     */
    protected final void installNativeWeaponComponents(long enchantments, String components) {
        short weapon = nativeRangedWeapon();
        if (weapon == PlayerInventory.EMPTY || heldItem != PlayerInventory.EMPTY) {
            throw new IllegalStateException("mob has no empty-hand native ranged weapon");
        }
        validateSlotComponents(weapon, enchantments, components);
        heldItemEnchantments = enchantments;
        heldItemComponents = components;
    }

    /**
     * Allay hand slot. Unlike combat equipment, its filter item may be any ordinary non-durable
     * item (durability 0) as well as a durable item with its exact remaining durability.
     */
    protected final boolean installAllayHeldItem(short itemType, int durability) {
        if (type != MobType.ALLAY || heldItem != PlayerInventory.EMPTY
                || itemType == PlayerInventory.EMPTY) return false;
        validateHeldItem(itemType, durability, true);
        heldItem = itemType;
        heldItemDurability = durability;
        heldItemEnchantments = 0L;
        heldItemComponents = null;
        pickedUpEquipment = false;
        return true;
    }

    /** [EC-MOBS] 아이템 액자에 한 개를 넣는다(어떤 아이템이든, 내구 아이템은 남은 내구 그대로). */
    protected final boolean installFramedItem(short itemType, int durability) {
        if (type != MobType.ITEM_FRAME || heldItem != PlayerInventory.EMPTY
                || itemType == PlayerInventory.EMPTY) return false;
        validateHeldItem(itemType, durability, true);
        heldItem = itemType;
        heldItemDurability = durability;
        pickedUpEquipment = false;
        return true;
    }

    /** [EC-MOBS] 아이템 액자의 아이템을 꺼낸다. */
    protected final EquipmentDrop removeFramedItem() {
        if (type != MobType.ITEM_FRAME || heldItem == PlayerInventory.EMPTY) return null;
        EquipmentDrop result = new EquipmentDrop(heldItem, heldItemDurability);
        heldItem = PlayerInventory.EMPTY;
        heldItemDurability = 0;
        return result;
    }

    /** Removes and returns the Allay's player-given filter item. */
    protected final EquipmentDrop removeAllayHeldItem() {
        if (type != MobType.ALLAY || heldItem == PlayerInventory.EMPTY) return null;
        EquipmentDrop result = new EquipmentDrop(heldItem, heldItemDurability,
                heldItemEnchantments, heldItemComponents);
        heldItem = PlayerInventory.EMPTY;
        heldItemDurability = 0;
        heldItemEnchantments = 0L;
        heldItemComponents = null;
        return result;
    }

    /**
     * [TRIAL-GAP] 불길한 트라이얼 스포너 설정의 장비(바닐라 {@code Mob.equip(EquipmentTable)}).
     * 바닐라 {@code EquipmentUser.resolveSlot}: 갑옷은 제 칸, 그 외는 비어 있는 주손. 해골·스트레이의
     * 활은 이 저장소에서 빈 손(0)이 곧 활이라 따로 쥐여 주지 않는다. {@code slot_drop_chances 0.0} 이라
     * 이 장비는 사망해도, 교체돼도 떨어지지 않는다(칸별 드랍 없음 비트). [MOB-EQUIP] 표가 붙인 인챈트
     * ({@code set_enchantments} — 화염·발사체·일반 보호 IV 를 함께)와 스택 성분 문자열을 그대로 싣는다.
     * 갑옷 장식({@code set_components minecraft:trim})은 {@code TrialLootTables} 의 TRIM 함수가 성분 문자열에
     * 담는 순간 이 경로가 손대지 않고 몹 장비로 옮긴다(장식 레인의 연결 지점).
     */
    public final void installTrialEquipment(
            java.util.List<com.gameexpert.engine.trial.TrialLootTables.Stack> stacks) {
        boolean mainHandUsed = false;
        int trialSlots = 0;
        for (com.gameexpert.engine.trial.TrialLootTables.Stack stack : stacks) {
            short item = stack.itemType();
            ArmorSlot slot = PlayerInventory.armorSlot(item);
            if (slot != null) {
                installGeneratedArmor(item, stack.enchantments(), stack.itemComponentData());
                trialSlots |= MobEquipmentRules.armorBit(slot.ordinal());
                continue;
            }
            if (mainHandUsed) continue;
            mainHandUsed = true;
            if (item == PlayerInventory.BOW && this instanceof Skeleton) {
                // 이 저장소에서 해골 계열의 빈 손(0)이 곧 활이다. 활의 성분만 그 자리에 싣는다.
                installNativeWeaponComponents(stack.enchantments(), stack.itemComponentData());
            } else {
                installGeneratedHeldItem(item, stack.enchantments(), stack.itemComponentData());
            }
            trialSlots |= MobEquipmentRules.MAIN_HAND_BIT;
        }
        trialEquipmentNoDrop = true;
        noDropSlots |= trialSlots;
    }

    /** [TRIAL-GAP] 트라이얼 장비를 입었는가(드랍 확률 0). */
    public boolean trialEquipmentNoDrop() { return trialEquipmentNoDrop; }

    void restoreTrialEquipmentNoDrop(boolean value) {
        trialEquipmentNoDrop = value;
        noDropSlots = value ? MobEquipmentRules.ALL_SLOTS : 0;
    }

    /** Installs deterministic spawn armor without marking it as picked-up loot. */
    protected final void installGeneratedArmor(short itemType) {
        installGeneratedArmor(itemType, 0L, null);
    }

    /** [MOB-EQUIP] 성분을 지닌 생성 방어구. 자연 칸(8.5%)으로 둔다. */
    protected final void installGeneratedArmor(short itemType, long enchantments, String components) {
        if (itemType == 0) return;
        ArmorSlot slot = PlayerInventory.armorSlot(itemType);
        int durability = PlayerInventory.initialDurability(itemType);
        validateEquipmentDurability(itemType, durability);
        validateSlotComponents(itemType, enchantments, components);
        int i = slot.ordinal();
        equippedArmor[i] = itemType;
        equippedArmorDurability[i] = durability;
        equippedArmorEnchantments[i] = enchantments;
        equippedArmorComponents[i] = components;
        guaranteedDropSlots &= ~MobEquipmentRules.armorBit(i);
        noDropSlots &= ~MobEquipmentRules.armorBit(i);
        pickedUpEquipment = guaranteedDropSlots != 0;
    }
    /** 종별 외형 비트. 매 틱 객체를 만들지 않고 정수 하나로 전송한다. */
    public int visualFlags() { return 0; }
    /**
     * Villager / zombie-villager appearance bits ({@code VISUAL_VILLAGER_*}). The world owns
     * VillagerData (structure facts, job ledger, trade level), so {@code MobSystem} refreshes this
     * before each broadcast; session-only, never persisted. Other species ignore it.
     */
    private int villagerAppearanceFlags;
    public void setVillagerAppearanceFlags(int flags) { villagerAppearanceFlags = flags; }
    protected int villagerAppearanceFlags() { return villagerAppearanceFlags; }

    public String actionKind() { return actionKind; }
    public String actionPhase() { return actionPhase; }
    public int actionTicksRemaining() { return actionTicksRemaining; }
    public int actionSequence() { return actionSequence; }
    public long vehicleMobId() { return vehicleMobId; }
    public boolean isMobPassenger() { return vehicleMobId != 0; }
    public boolean isRidingBoat() { return mountedBoatId != 0; }
    public long mountedBoatId() { return mountedBoatId; }
    public long placedVehicleId() { return placedVehicleId; }
    public void bindPlacedVehicleDismount(java.util.function.LongPredicate handler) { placedVehicleDismount = handler; }
    /** The placed authority owns its durable seat ledger; mob-to-mob seats remain local. */
    public boolean dismountAuthoritativeVehicle() {
        if (placedVehicleId != 0) return placedVehicleDismount != null && placedVehicleDismount.test(placedVehicleId);
        if (vehicleMobId == 0) return false;
        dismountMobVehicle();
        return true;
    }
    public boolean canBoardPlacedVehicle() { return placedBoardingCooldown == 0; }
    void advancePlacedVehicleCooldown() { if (placedBoardingCooldown > 0) placedBoardingCooldown--; }
    public boolean isRidingPlacedVehicle() { return placedVehicleId != 0; }
    public void mountPlacedVehicle(long id, double x, double y, double z, double yaw) {
        if (id <= 0 || mountedBoatId != 0 || vehicleMobId != 0) throw new IllegalStateException("invalid placed vehicle binding");
        placedVehicleId = id;
        vehicleX = x; vehicleY = y; vehicleZ = z; vehicleYaw = yaw;
        lockToVehicle();
    }
    public void dismountPlacedVehicle(double x, double y, double z) {
        placedVehicleId = 0;
        placedVehicleDismount = null;
        placedBoardingCooldown = 30;
        this.x = x; this.y = y; this.z = z;
        vy = 0; horizontalVx = 0; horizontalVz = 0; knockbackVx = 0; knockbackVz = 0;
    }
    public String ownerNickname() { return ownerNickname; }
    public boolean ocelotTrusting() { return ocelotTrusting; }
    public boolean wolfSitting() { return wolfSitting; }
    public boolean companionSitting() { return this instanceof CompanionMob mob && mob.sitting(); }
    public int catCollarColor() { return this instanceof CompanionMob mob ? mob.collarColor() : -1; }
    public boolean tameCompanion(String nickname) {
        return this instanceof CompanionMob mob && mob.tame(nickname);
    }
    public boolean toggleCompanionSitting(String nickname) {
        return this instanceof CompanionMob mob && mob.toggleSitting(nickname);
    }
    public boolean dyeCatCollar(String nickname, int color) {
        return this instanceof CompanionMob mob && mob.dyeCollar(nickname, color);
    }
    public void companionFoodFeedback(boolean tamed) {
        if (this instanceof CompanionMob) markVisualAction(tamed ? "celebrate" : "eat", 10);
    }
    void restoreCompanionState(boolean sitting, int color) {
        if (this instanceof CompanionMob mob) mob.restoreCompanion(sitting, color);
        else if (sitting || color != -1) {
            throw new IllegalStateException("non-companion mob has persisted Cat/Parrot state");
        }
    }
    public int wolfCollarColor() { return this instanceof Wolf wolf ? wolf.collarColor() : -1; }
    public int wolfArmorDurability() { return this instanceof Wolf wolf ? wolf.bodyArmorDurability() : 0; }
    public int tadpoleAgeMcTicks() { return this instanceof Tadpole tadpole ? tadpole.ageMcTicks() : 0; }
    public int turtleEggDigMcTicks() {
        return this instanceof Turtle turtle ? turtle.eggDigMcTicks() : 0;
    }
    public boolean turtleTravelingHome() {
        return this instanceof Turtle turtle && turtle.travelingHome();
    }
    public int turtleEggCount() {
        return this instanceof Turtle turtle ? turtle.eggCount() : 0;
    }
    // ── [FARM-ANIMAL] 종 고유 상태 위임. 다른 종은 중립값을 돌려주어 영속 whitelist 가 좁게 남는다. ──
    /**
     * The persisted sheared flag (column {@code sheepSheared}): a sheep's wool, and
     * [CONTAINER-MENUS] a snow golem's pumpkin or a bogged's mushrooms taken by shears.
     */
    public boolean sheepSheared() {
        if (this instanceof Sheep sheep) return sheep.sheared();
        if (this instanceof SnowGolem golem) return !golem.hasPumpkin();
        return this instanceof Bogged bogged && bogged.sheared();
    }
    /** 양의 MC DyeColor 네트워크 ID(0..15). 양이 아니면 -1. */
    public int sheepColor() { return this instanceof Sheep sheep ? sheep.color() : -1; }
    public int sheepEatMcTicks() { return this instanceof Sheep sheep ? sheep.eatMcTicks() : 0; }
    public int chickenEggMcTicks() {
        return this instanceof Chicken chicken ? chicken.eggMcTicks() : 0;
    }
    public boolean pigSaddled() { return this instanceof Pig pig && pig.saddled(); }
    /**
     * 차지드 크리퍼인가. 바닐라 {@code Creeper#isPowered}({@code DATA_IS_POWERED}) 이며 NBT
     * {@code powered} 로 저장되는 영속 상태다. 크리퍼가 아니면 false 다.
     */
    public boolean creeperPowered() { return this instanceof Creeper creeper && creeper.isPowered(); }
    /** 라이터로 수동 점화된 크리퍼인가. 바닐라 NBT {@code ignited}. */
    public boolean creeperIgnited() { return this instanceof Creeper creeper && creeper.isIgnited(); }
    public String pigRiderNickname() { return this instanceof Pig pig ? pig.riderNickname() : null; }
    public int pigBoostMcTicks() { return this instanceof Pig pig ? pig.boostMcTicks() : 0; }
    public int pigBoostTotalMcTicks() {
        return this instanceof Pig pig ? pig.boostTotalMcTicks() : 0;
    }

    /** 전단 가능한 양인가. 상태를 바꾸지 않는다. */
    public boolean readyForShearing() {
        return this instanceof Sheep sheep && sheep.readyForShearing();
    }

    /** 전단 확정. 호출자가 {@link #readyForShearing()} 로 먼저 가려낸다. */
    public void shearSheep() {
        if (this instanceof Sheep sheep) sheep.shear();
    }

    public boolean dyeSheep(int dyeColorId) {
        return this instanceof Sheep sheep && sheep.dye(dyeColorId);
    }

    public boolean saddlePig() { return this instanceof Pig pig && pig.saddle(); }

    public boolean mountPig(String nickname) {
        return this instanceof Pig pig && pig.mount(nickname);
    }

    public boolean dismountPig(String nickname) {
        return this instanceof Pig pig && pig.dismount(nickname);
    }

    public boolean boostPig(String nickname, MobRandom rng) {
        return this instanceof Pig pig && pig.boost(nickname, rng);
    }

    /** 기수 좌표 업링크 반영. 실제 기수일 때만 참이다(보트 운전자 업링크와 같은 계약). */
    public boolean applyPigRiderPosition(String nickname,
            double x, double y, double z, double yaw) {
        return this instanceof Pig pig && pig.applyRiderPosition(nickname, x, y, z, yaw);
    }

    /** 기수 세션이 사라졌을 때(사망·퇴장) 좌석만 비운다. */
    public void clearPigRider() {
        if (this instanceof Pig pig) pig.clearRider();
    }

    // ── [MOUNT] 종 비의존 좌석 계약. 돼지·말이 같은 통로를 쓴다({@link MobMountRules}). ──
    /** 이 개체가 좌석을 갖는가. */
    public boolean mountable() { return MobMountRules.rideable(type); }

    /** 좌석 seatIndex 의 기수 닉네임. 비어 있으면 null. */
    public String seatRider(int seatIndex) {
        if (!MobMountRules.validSeat(type, seatIndex)) return null;
        if (this instanceof Pig pig) return pig.riderNickname();
        if (this instanceof AbstractHorseMob horse) return horse.riderNickname();
        if (this instanceof Camel camel) return camel.seatRiderNickname(seatIndex);
        if (this instanceof HappyGhast ghast) return ghast.seatRiderNickname(seatIndex);
        // [NAUTILUS-MOUNT] 수중 탈것도 같은 좌석 원장 통로를 쓴다.
        if (this instanceof NautilusFamilyMob nautilus) return nautilus.seatRiderNickname(seatIndex);
        return null;
    }

    /** 좌석 착석. 종별 탑승 조건(안장·나이·중복 착석)은 각 종 구현이 본다. */
    public boolean mountSeat(String nickname, int seatIndex) {
        if (!MobMountRules.validSeat(type, seatIndex)) return false;
        if (this instanceof Pig pig) return pig.mount(nickname);
        if (this instanceof AbstractHorseMob horse) return horse.mount(nickname);
        if (this instanceof Camel camel) return camel.mount(nickname, seatIndex);
        if (this instanceof HappyGhast ghast) return ghast.mount(nickname, seatIndex);
        if (this instanceof NautilusFamilyMob nautilus) return nautilus.mount(nickname, seatIndex);
        return false;
    }

    /** 좌석 이탈. 실제로 그 좌석에 앉아 있던 기수일 때만 참이다. */
    public boolean dismountSeat(String nickname, int seatIndex) {
        if (!MobMountRules.validSeat(type, seatIndex)) return false;
        if (this instanceof Pig pig) return pig.dismount(nickname);
        if (this instanceof AbstractHorseMob horse) return horse.dismount(nickname);
        if (this instanceof Camel camel) return camel.dismount(nickname, seatIndex);
        if (this instanceof HappyGhast ghast) return ghast.dismount(nickname, seatIndex);
        if (this instanceof NautilusFamilyMob nautilus) return nautilus.dismount(nickname, seatIndex);
        return false;
    }

    /** 좌석만 비운다(기수 사망·퇴장). */
    public void clearSeat(int seatIndex) {
        if (!MobMountRules.validSeat(type, seatIndex)) return;
        if (this instanceof Pig pig) pig.clearRider();
        if (this instanceof AbstractHorseMob horse) horse.clearRider();
        if (this instanceof Camel camel) camel.clearCamelSeat(seatIndex);
        if (this instanceof HappyGhast ghast) ghast.clearGhastSeat(seatIndex);
        if (this instanceof NautilusFamilyMob nautilus) nautilus.clearNautilusSeat(seatIndex);
    }

    /**
     * 조종석 기수의 좌표 업링크 반영. 좌석 일치·경계 검증은 호출자가 먼저 하고, 여기서는
     * <b>조종 권한 표</b>({@link MobMountRules#controllingSeat(MobType, int)})를 다시 본다 —
     * 라마처럼 좌석은 있어도 조종할 수 없는 종은 좌표 정본을 넘겨받지 못한다.
     */
    public boolean applySeatRiderPosition(String nickname, int seatIndex,
            double x, double y, double z, double yaw) {
        if (!MobMountRules.controllingSeat(type, seatIndex)) return false;
        if (this instanceof Pig pig) return pig.applyRiderPosition(nickname, x, y, z, yaw);
        if (this instanceof Camel camel) return camel.applyRiderPosition(nickname, x, y, z, yaw);
        if (this instanceof HappyGhast ghast) {
            return ghast.applyRiderPosition(nickname, x, y, z, yaw);
        }
        if (this instanceof NautilusFamilyMob nautilus) {
            return nautilus.applyRiderPosition(nickname, x, y, z, yaw);
        }
        if (this instanceof AbstractHorseMob horse) {
            return horse.applyRiderPosition(nickname, x, y, z, yaw);
        }
        return false;
    }

    // ── [MOUNT] 말 계열 고유 상태 위임. 다른 종은 중립값이라 영속 whitelist 가 좁게 남는다. ──
    // ── [MOUNT] 낙타 고유 상태 위임. 다른 종은 중립값이라 영속 whitelist 가 좁게 남는다. ──
    public boolean camelSaddled() { return this instanceof Camel camel && camel.saddled(); }
    public boolean camelSitting() { return this instanceof Camel camel && camel.sitting(); }
    public int camelDashCooldownMcTicks() {
        return this instanceof Camel camel ? camel.dashCooldownMcTicks() : 0;
    }
    public boolean saddleCamel() { return this instanceof Camel camel && camel.saddle(); }
    /** Full-stack camel saddle ingress; the legacy type-only overload remains a compatibility shim. */
    public boolean saddleCamel(PlayerInventory.StackSnapshot saddle) {
        return this instanceof Camel camel && camel.saddle(saddle);
    }
    public boolean tryCamelDash(String nickname, int charge) {
        return this instanceof Camel camel && camel.tryDash(nickname, charge);
    }
    /**
     * 저장된 낙타 상태를 복원한다. 손상된 행이 다른 종에게 안장·자세를 흘리면 거부한다
     * (말 계열·농장 동물 복원과 같은 계약).
     */
    public void restoreCamelState(boolean saddled, boolean sitting) {
        if (this instanceof Camel camel) {
            camel.restoreState(saddled, sitting);
        } else if (saddled || sitting) {
            throw new IllegalArgumentException("non-Camel row carries camel saddle/pose state");
        }
    }

    // ── [HARNESS] 해피 가스트 고유 상태 위임. 다른 종은 중립값이라 영속 whitelist 가 좁게 남는다. ──
    /** 하네스를 쓰고 있는 해피 가스트인가. 다른 종은 언제나 거짓이다. */
    public boolean happyGhastHarnessed() {
        return this instanceof HappyGhast ghast && ghast.harnessed();
    }

    /** 하네스 색(MC DyeColor 0..15). 하네스가 없거나 다른 종이면 -1(양의 색과 같은 어휘). */
    public int happyGhastHarnessColor() {
        return this instanceof HappyGhast ghast ? ghast.harnessColor() : -1;
    }

    /** 하네스 장착. 성체 해피 가스트이고 아직 쓰고 있지 않을 때만 참이다. */
    public boolean harnessHappyGhast(int color) {
        return this instanceof HappyGhast ghast && ghast.harness(color);
    }

    /**
     * 저장된 해피 가스트 하네스 상태를 복원한다. 손상된 행이 다른 종에게 하네스를 흘리면
     * 거부한다(낙타·말 계열·농장 동물 복원과 같은 계약).
     */
    public void restoreHappyGhastState(boolean harnessed, int harnessColor) {
        if (this instanceof HappyGhast ghast) {
            ghast.restoreState(harnessed, harnessColor);
        } else if (harnessed || harnessColor != -1) {
            throw new IllegalArgumentException("non-HappyGhast row carries harness state");
        }
    }

    // ── [NAUTILUS-MOUNT] 노틸러스 고유 상태 위임. 다른 종은 중립값이라 영속 whitelist 가 좁게 남는다. ──
    /** 안장을 얹은 노틸러스인가. 다른 종은 언제나 거짓이다. */
    public boolean nautilusSaddled() {
        return this instanceof NautilusFamilyMob nautilus && nautilus.saddled();
    }

    /**
     * 노틸러스 갑옷 티어(0=구리 … 3=다이아몬드). 입고 있지 않거나 다른 종이면
     * {@link NautilusMountRules#NO_ARMOR}(-1) — 하네스 색과 같은 어휘다.
     */
    public int nautilusArmorTier() {
        return this instanceof NautilusFamilyMob nautilus
                ? nautilus.armorTier() : NautilusMountRules.NO_ARMOR;
    }

    /** Exact body armor item for spawn/update presentation; zero means no armor. */
    public int nautilusArmorItemId() {
        return NautilusMountRules.armorItemForTier(nautilusArmorTier());
    }

    /** 안장 장착. 길들인 성체 노틸러스이고 아직 얹지 않았을 때만 참이다. */
    public boolean saddleNautilus() {
        return this instanceof NautilusFamilyMob nautilus && nautilus.saddle();
    }

    /** 갑옷 장착. 길들인 성체 노틸러스이고 아직 입지 않았을 때만 참이다. */
    public boolean equipNautilusArmor(int tier) {
        return this instanceof NautilusFamilyMob nautilus && nautilus.equipArmor(tier);
    }

    /** Removes and returns the exact Nautilus armor item id, or zero when illegal. */
    public int removeNautilusArmor() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.removeArmor() : 0;
    }

    /**
     * 저장된 노틸러스 안장·갑옷 상태를 복원한다. 손상된 행이 다른 종에게 안장/갑옷을 흘리면
     * 거부한다(하네스·낙타·말 계열 복원과 <b>같은 계약</b>). {@link #nautilusSaddled} ·
     * {@link #nautilusArmorTier} 와 <b>복원 게이트 쌍</b>을 이룬다 — 한쪽만 있으면 안장을 얹은
     * 개체가 재시작 뒤 맨몸으로 돌아온다.
     */
    public void restoreNautilusMountState(boolean saddled, int armorTier) {
        if (this instanceof NautilusFamilyMob nautilus) {
            nautilus.restoreMountState(saddled, armorTier);
        } else if (saddled || armorTier != NautilusMountRules.NO_ARMOR) {
            throw new IllegalArgumentException("non-Nautilus row carries nautilus mount state");
        }
    }

    public boolean horseTamed() {
        return this instanceof AbstractHorseMob h ? h.tamed()
                : this instanceof Camel || this instanceof SkeletonHorse
                        || this instanceof TraderLlama;
    }
    public int horseTemper() { return this instanceof AbstractHorseMob h ? h.temper() : 0; }
    public boolean horseSaddled() { return this instanceof AbstractHorseMob h && h.saddled(); }
    public double horseMaxHealthStat() {
        return this instanceof AbstractHorseMob h ? h.stats().maxHealth() : 0.0;
    }
    public double horseSpeedStat() {
        return this instanceof AbstractHorseMob h ? h.stats().speed() : 0.0;
    }
    public double horseJumpStrengthStat() {
        return this instanceof AbstractHorseMob h ? h.stats().jumpStrength() : 0.0;
    }
    public String horseRiderNickname() {
        return this instanceof AbstractHorseMob h ? h.riderNickname() : null;
    }
    /** 이번 틱 길들이기 판정에 실패해 낙마해야 하는가. */
    public boolean horseBuckPending() {
        return this instanceof AbstractHorseMob h && h.buckPending();
    }
    public void consumeHorseBuck() {
        if (this instanceof AbstractHorseMob h) h.consumeBuck();
    }
    public boolean feedHorse(short itemType) {
        return this instanceof AbstractHorseMob h && h.feed(itemType);
    }
    public boolean saddleHorse() { return this instanceof AbstractHorseMob h && h.saddle(); }
    /** Full-stack horse saddle ingress; the legacy no-argument overload remains a compatibility shim. */
    public boolean saddleHorse(PlayerInventory.StackSnapshot saddle) {
        return this instanceof AbstractHorseMob h && h.saddle(saddle);
    }
    /** Exact horse BODY armor item id, or zero when empty/not an ordinary Horse. */
    public short horseArmorItem() {
        return this instanceof AbstractHorseMob h ? h.armorItem() : 0;
    }
    /** Narrow authority API for central interaction dispatch. */
    public boolean equipHorseArmor(short itemType) {
        return this instanceof AbstractHorseMob h && h.equipArmor(itemType);
    }
    /** Full-stack armor ingress; all item components travel through the horse-menu mutation path. */
    public boolean equipHorseArmor(PlayerInventory.StackSnapshot armor) {
        return this instanceof AbstractHorseMob h && h.equipArmor(armor);
    }
    /** Narrow authority API for shears/inventory removal dispatch. */
    public short removeHorseArmor() {
        return this instanceof AbstractHorseMob h ? h.removeArmor() : 0;
    }
    /** Full-stack armor removal for callers that must return the exact installed item identity. */
    public PlayerInventory.StackSnapshot removeHorseArmorStack() {
        return this instanceof AbstractHorseMob h
                ? h.removeArmorStack() : PlayerInventory.StackSnapshot.EMPTY;
    }
    public int goatHornMask() { return this instanceof Goat goat ? goat.hornMask() : 0; }
    public GoatRamRules.Phase goatRamPhase() {
        return this instanceof Goat goat ? goat.ramPhase() : GoatRamRules.Phase.IDLE;
    }
    public int goatRamCooldownMcTicks() {
        return this instanceof Goat goat ? goat.ramCooldownMcTicks() : -1;
    }
    public String goatRamTargetNickname() {
        return this instanceof Goat goat ? goat.ramTargetNickname() : null;
    }
    public double goatRamTargetX() { return this instanceof Goat goat ? goat.ramTargetX() : 0; }
    public double goatRamTargetZ() { return this instanceof Goat goat ? goat.ramTargetZ() : 0; }
    public double goatRamRunUpX() { return this instanceof Goat goat ? goat.ramRunUpX() : 0; }
    public double goatRamRunUpZ() { return this instanceof Goat goat ? goat.ramRunUpZ() : 0; }
    public double goatRamDirectionX() {
        return this instanceof Goat goat ? goat.ramDirectionX() : 0;
    }
    public double goatRamDirectionZ() {
        return this instanceof Goat goat ? goat.ramDirectionZ() : 0;
    }
    public int goatRamPrepareMcTicks() {
        return this instanceof Goat goat ? goat.ramPrepareMcTicks() : 0;
    }
    public double goatRamDistance() { return this instanceof Goat goat ? goat.ramDistance() : 0; }
    public long goatRamSequence() { return this instanceof Goat goat ? goat.ramSequence() : 0; }
    boolean finishGoatEntityRam(MobRandom rng) {
        return this instanceof Goat goat && goat.finishEntityRam(rng);
    }
    Goat.RamImpact pendingGoatRamImpact() {
        return this instanceof Goat goat ? goat.pendingRamImpact() : null;
    }
    boolean consumeGoatRamImpact(long sequence) {
        return this instanceof Goat goat && goat.consumeRamImpact(sequence);
    }
    public SnifferDigRules.Phase snifferDigPhase() {
        return this instanceof Sniffer sniffer ? sniffer.digPhase() : SnifferDigRules.Phase.IDLE;
    }
    public int snifferDigCooldownMcTicks() {
        return this instanceof Sniffer sniffer ? sniffer.digCooldownMcTicks() : 0;
    }
    public int snifferDigPhaseMcTicks() {
        return this instanceof Sniffer sniffer ? sniffer.digPhaseMcTicks() : 0;
    }
    public int snifferDigDropDelayMcTicks() {
        return this instanceof Sniffer sniffer ? sniffer.digDropDelayMcTicks() : 0;
    }
    public int snifferDigTargetX() { return this instanceof Sniffer s ? s.digTargetX() : 0; }
    public int snifferDigTargetY() { return this instanceof Sniffer s ? s.digTargetY() : 0; }
    public int snifferDigTargetZ() { return this instanceof Sniffer s ? s.digTargetZ() : 0; }
    public long snifferDigSearchEpoch() {
        return this instanceof Sniffer s ? s.digSearchEpoch() : 0;
    }
    public long snifferDigSequence() { return this instanceof Sniffer s ? s.digSequence() : 0; }
    public long snifferPendingDropSequence() {
        return this instanceof Sniffer s ? s.pendingDropSequence() : 0;
    }
    public short snifferPendingDropItem() {
        return this instanceof Sniffer s ? s.pendingDropItem() : 0;
    }
    boolean confirmSnifferDigDrop(long sequence) {
        return this instanceof Sniffer s && s.confirmDrop(sequence);
    }
    void restoreSnifferDigState(String phase, int cooldownMcTicks, int phaseMcTicks,
            int dropDelayMcTicks, int targetX, int targetY, int targetZ,
            long searchEpoch, long sequence, long pendingSequence, short pendingItem) {
        if (this instanceof Sniffer sniffer) {
            sniffer.restoreDigState(phase, cooldownMcTicks, phaseMcTicks, dropDelayMcTicks,
                    targetX, targetY, targetZ, searchEpoch, sequence,
                    pendingSequence, pendingItem);
        } else if (phase != null && !"IDLE".equals(phase)) {
            throw new IllegalArgumentException("non-Sniffer row carries dig state");
        }
    }
    short dropGoatHornAfterCommittedRam(boolean preferLeft) {
        return this instanceof Goat goat ? goat.dropHornAfterCommittedRam(preferLeft) : 0;
    }
    void restoreGoatHornMask(Integer hornMask) {
        if (this instanceof Goat goat) {
            goat.restoreHornMask(hornMask == null ? 3 : hornMask);
        } else if (hornMask != null && hornMask != 0) {
            throw new IllegalArgumentException("non-Goat row carries horn state");
        }
    }
    void restoreGoatRamState(String phase, int cooldownMcTicks, String targetNickname,
            double targetX, double targetZ, double runUpX, double runUpZ,
            double directionX, double directionZ, int prepareMcTicks, double distance,
            long sequence, int pendingBlockId, int pendingX, int pendingY, int pendingZ,
            boolean preferLeft) {
        if (this instanceof Goat goat) {
            goat.restoreRamState(phase, cooldownMcTicks, targetNickname,
                    targetX, targetZ, runUpX, runUpZ, directionX, directionZ,
                    prepareMcTicks, distance, sequence, pendingBlockId,
                    pendingX, pendingY, pendingZ, preferLeft);
        } else if (phase != null && !"IDLE".equals(phase)) {
            throw new IllegalArgumentException("non-Goat row carries ram state");
        }
    }
    /**
     * 테스트 전용: {@code RunAroundLikeCrazyGoal} 판정 루프를 돌리지 않고 길들인다. 안장은
     * 길들인 성체만 받으므로(바닐라와 같다) 안장 게이트 회귀가 이 문을 통해 개체를 세운다.
     * 판정 루프 자체의 회귀는 {@code MobMountSaddleGateTest} 의 낙마 테스트가 지킨다.
     */
    public void tameHorseForTest(String nickname) {
        if (this instanceof AbstractHorseMob h) h.tame(nickname);
    }
    public boolean horseAcceptsJump(String nickname, int charge) {
        return this instanceof AbstractHorseMob h && h.acceptsJump(nickname, charge);
    }

    // ── [MOUNT] 상자 화물·카펫·힘(당나귀·노새·라마). ──────────────────
    /** 이 개체가 상자를 달 수 있는 종인가. */
    public boolean horseChestable() { return ChestedHorseRules.chestable(type); }
    /** 상자를 달았는가. */
    public boolean horseChested() {
        if (this instanceof AbstractHorseMob h) return h.chested();
        return this instanceof TraderLlama trader && trader.chested();
    }
    /** 상자를 단다. 길들인 성체이고 아직 상자가 없을 때만 참이다. */
    public boolean attachHorseChest() {
        if (this instanceof AbstractHorseMob h) return h.attachChest();
        return this instanceof TraderLlama trader && trader.attachChest();
    }
    /** 화물 컨테이너. 상자를 달지 않았으면 null 이다. */
    public com.gameexpert.engine.ChestInventory horseCargo() {
        if (this instanceof AbstractHorseMob h) return h.cargo();
        return this instanceof TraderLlama trader ? trader.cargo() : null;
    }
    /** Exact vanilla horse-menu SADDLE/BODY stacks (slots 0 and 1). */
    public com.gameexpert.engine.ChestInventory horseEquipment() {
        if (this instanceof AbstractHorseMob h) return h.equipment();
        if (this instanceof Camel camel) return camel.equipment();
        if (this instanceof SkeletonHorse skeleton) return skeleton.equipment();
        if (this instanceof TraderLlama trader) return trader.equipment();
        return null;
    }
    public boolean isHorseMenuRider(String nickname) {
        if (nickname == null) return false;
        if (this instanceof AbstractHorseMob horse) return nickname.equals(horse.riderNickname());
        if (this instanceof Camel camel) {
            for (int seat = 0; seat < CamelRules.SEAT_COUNT; seat++) {
                if (nickname.equals(camel.seatRiderNickname(seat))) return true;
            }
        }
        return false;
    }
    public long horseMenuPersistenceRevision() { return horseMenuPersistenceRevision; }
    public void restoreHorseMenuPersistenceRevision(long revision) {
        if (revision < 0 || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("invalid horse menu persistence revision");
        }
        horseMenuPersistenceRevision = revision;
    }

    /**
     * Gameplay mutation preflight. {@code MAX_VALUE - 1} is the last persisted generation but
     * has no representable successor, so it is terminal for live changes.
     */
    public final void preflightHorseMenuPersistenceRevision() {
        if (horseMenuPersistenceRevision >= Long.MAX_VALUE - 1) {
            throw new IllegalStateException("horse menu persistence revision is exhausted");
        }
    }

    /**
     * Normal horse-equipment insertion. The inventory mutation owns the local revision and this
     * method advances the one shared menu revision exactly once after a successful move.
     */
    protected final boolean putHorseMenuEquipment(ChestInventory inventory, int slot,
            PlayerInventory.StackSnapshot stack) {
        if (inventory == null || stack == null || stack.isEmpty() || stack.count() != 1) return false;
        preflightHorseMenuPersistenceRevision();
        if (inventory.persistenceRevision() >= Long.MAX_VALUE - 1) {
            throw new IllegalStateException("horse equipment persistence revision is exhausted");
        }
        int moved = inventory.putInSlot(slot, stack.itemType(), stack.count(), stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData());
        if (moved != 1) return false;
        advanceHorseMenuPersistenceRevision();
        setPersistenceRequired(true);
        return true;
    }

    /**
     * Normal horse-equipment removal. The exact stack is captured before the ordinary take API
     * clears it, so no component can be lost at the gameplay boundary.
     */
    protected final PlayerInventory.StackSnapshot takeHorseMenuEquipment(ChestInventory inventory,
            int slot) {
        if (inventory == null || slot < 0 || slot >= inventory.slots()
                || inventory.itemType(slot) == PlayerInventory.EMPTY
                || inventory.count(slot) <= 0) return PlayerInventory.StackSnapshot.EMPTY;
        preflightHorseMenuPersistenceRevision();
        if (inventory.persistenceRevision() >= Long.MAX_VALUE - 1) {
            throw new IllegalStateException("horse equipment persistence revision is exhausted");
        }
        PlayerInventory.StackSnapshot removed = new PlayerInventory.StackSnapshot(
                inventory.itemType(slot), inventory.count(slot), inventory.durability(slot),
                inventory.enchantments(slot), inventory.mapId(slot), inventory.shulkerId(slot),
                inventory.bucketMobData(slot), inventory.itemComponentData(slot));
        if (inventory.take(slot, removed.count()) != removed.count()) {
            return PlayerInventory.StackSnapshot.EMPTY;
        }
        advanceHorseMenuPersistenceRevision();
        setPersistenceRequired(true);
        return removed;
    }

    public void advanceHorseMenuPersistenceRevision() {
        preflightHorseMenuPersistenceRevision();
        horseMenuPersistenceRevision = Math.incrementExact(horseMenuPersistenceRevision);
    }
    /** 사망 드랍용. 화물을 비우고 내용물을 돌려준다. */
    public java.util.List<com.gameexpert.engine.ChestInventory.StoredStack> drainHorseCargo() {
        return this instanceof AbstractHorseMob h ? h.drainCargo() : java.util.List.of();
    }
    /** 라마 화물 열 수를 정하는 힘 스탯(1~5). 다른 종은 0 이다. */
    public int llamaStrength() {
        if (this instanceof AbstractHorseMob h) return h.strength();
        return this instanceof TraderLlama trader ? trader.strength() : 0;
    }
    /** 라마 장식 카펫 색(0~15) 또는 {@code LlamaRules.NO_CARPET}. */
    public int llamaCarpetColor() {
        if (this instanceof AbstractHorseMob h) return h.carpetColor();
        return this instanceof TraderLlama trader
                ? LlamaRules.carpetColorIndex(trader.equipment().itemType(1))
                : LlamaRules.NO_CARPET;
    }
    /** 카펫 장식. 라마이고 아직 장식이 없을 때만 참이다. */
    public boolean decorateLlama(short itemType) {
        if (this instanceof AbstractHorseMob h) return h.decorate(itemType);
        return this instanceof TraderLlama trader && trader.decorate(itemType);
    }
    /** Full-stack llama carpet ingress; the type-only overload remains a compatibility shim. */
    public boolean decorateLlama(PlayerInventory.StackSnapshot carpet) {
        if (this instanceof AbstractHorseMob h) return h.decorate(carpet);
        return this instanceof TraderLlama trader && trader.decorate(carpet);
    }

    void restoreHorseState(boolean tamed, int temper, boolean saddled,
                           double maxHealth, double speed, double jumpStrength) {
        restoreHorseState(tamed, temper, saddled, maxHealth, speed, jumpStrength,
                false, LlamaRules.NO_CARPET, 0);
    }

    /**
     * 말 계열 영속 복구. 라마 힘은 화물 칸 수를 정하므로 <b>상자보다 먼저</b> 되돌린다.
     */
    void restoreHorseState(boolean tamed, int temper, boolean saddled,
                           double maxHealth, double speed, double jumpStrength,
                           boolean chested, int carpetColor, int strength) {
        if (this instanceof AbstractHorseMob horse) {
            if (horse instanceof Llama llama && strength != 0) llama.restoreStrength(strength);
            // 말 상태가 없던 옛 행은 스탯이 0 이므로 생성 시 굴린 값을 그대로 둔다.
            if (maxHealth == 0.0 && speed == 0.0 && jumpStrength == 0.0) {
                horse.restoreHorseState(tamed, temper, saddled, horse.stats().maxHealth(),
                        horse.stats().speed(), horse.stats().jumpStrength(), chested, carpetColor);
            } else {
                horse.restoreHorseState(tamed, temper, saddled, maxHealth, speed, jumpStrength,
                        chested, carpetColor);
            }
        } else if (this instanceof TraderLlama trader) {
            // 트레이더 라마는 길들이기·기질·안장·스탯이 없다. 런타임 스냅샷은 horseStateType 밖이라
            // tamed=false 로 쓰고, 옛 행은 horseTamed() 규약대로 tamed=true 를 가질 수 있으므로
            // 둘 다 받되 나머지 말 상태는 비어 있어야 한다.
            if (temper != 0 || saddled || maxHealth != 0.0 || speed != 0.0
                    || jumpStrength != 0.0) {
                throw new IllegalArgumentException("invalid trader llama horse state");
            }
            trader.restoreHorseState(chested, carpetColor, strength);
        } else if (tamed || temper != 0 || saddled || chested
                || carpetColor != LlamaRules.NO_CARPET || strength != 0
                || maxHealth != 0.0 || speed != 0.0 || jumpStrength != 0.0) {
            throw new IllegalArgumentException("non-horse-family row carries horse state");
        }
    }

    /**
     * 저장된 화물 한 줄을 되돌린다. {@link #restoreHorseState} 가 먼저 상자를 달아 컨테이너를
     * 만들어 둔 뒤에만 호출한다(라마 힘 → 상자 → 화물 순서). 상자를 달 수 없는 행이 화물을
     * 들고 오면 손상으로 보고 거부한다(카펫·힘과 같은 계약).
     */
    void restoreHorseCargo(String encodedCargo) {
        if (this instanceof AbstractHorseMob horse) {
            horse.restoreCargo(encodedCargo);
        } else if (this instanceof TraderLlama trader) {
            trader.restoreCargo(encodedCargo);
        } else if (encodedCargo != null && !encodedCargo.isEmpty()) {
            throw new IllegalArgumentException("non-horse-family row carries cargo");
        }
    }

    void restoreHorseArmor(short armorItem) {
        if (this instanceof AbstractHorseMob horse) {
            horse.restoreArmor(armorItem);
        } else if (armorItem != 0) {
            throw new IllegalArgumentException("non-horse-family row carries horse armor");
        }
    }

    /**
     * 저장된 농장 동물 상태를 복원한다. 종이 아닌 값이 섞이면 손상으로 보고 거부한다
     * (Wolf/Tadpole 복원과 같은 계약).
     */
    /**
     * 저장된 차지드 표식을 복원한다. 다른 종에 켜진 값이 섞이면 손상으로 보고 거부한다
     * (농장 동물 복원과 같은 계약).
     */
    void restoreCreeperPowered(boolean powered) {
        if (this instanceof Creeper creeper) {
            creeper.setPowered(powered);
        } else if (powered) {
            throw new IllegalArgumentException("non-Creeper row carries the powered flag");
        }
    }

    void restoreCreeperIgnited(boolean ignited) {
        if (this instanceof Creeper creeper) {
            if (ignited) creeper.ignite();
        } else if (ignited) {
            throw new IllegalArgumentException("non-Creeper row carries the ignited flag");
        }
    }

    void restoreFarmAnimalState(int sheepColor, boolean sheepSheared, int sheepEatMcTicks,
                                int chickenEggMcTicks, boolean pigSaddled,
                                int pigBoostMcTicks, int pigBoostTotalMcTicks) {
        if (this instanceof Sheep sheep) {
            sheep.restoreSheepState(sheepColor, sheepSheared, sheepEatMcTicks);
        } else if (sheepColor != -1 || sheepEatMcTicks != 0) {
            throw new IllegalArgumentException("non-Sheep row carries Sheep wool state");
        } else if (this instanceof SnowGolem golem) {
            golem.restorePumpkin(!sheepSheared);
        } else if (this instanceof Bogged bogged) {
            bogged.restoreSheared(sheepSheared);
        } else if (sheepSheared) {
            throw new IllegalArgumentException("unshearable row carries the sheared flag");
        }
        if (this instanceof Chicken chicken) {
            chicken.restoreChickenState(chickenEggMcTicks);
        } else if (chickenEggMcTicks != 0) {
            throw new IllegalArgumentException("non-Chicken row carries an egg cursor");
        }
        if (this instanceof Pig pig) {
            pig.restorePigState(pigSaddled, null, pigBoostMcTicks, pigBoostTotalMcTicks);
        } else if (pigSaddled || pigBoostMcTicks != 0 || pigBoostTotalMcTicks != 0) {
            throw new IllegalArgumentException("non-Pig row carries saddle/boost state");
        }
    }
    public int zombieVillagerConversionMcTicks() {
        return this instanceof ZombieVillager zombieVillager
                ? zombieVillager.conversionMcTicks() : 0;
    }
    public String zombieVillagerConversionStarter() {
        return this instanceof ZombieVillager zombieVillager
                ? zombieVillager.conversionStarter() : null;
    }
    public String zombieNautilusChargePhase() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargePhase().name() : "IDLE";
    }
    public int zombieNautilusChargeCooldownMcTicks() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeCooldownMcTicks() : 0;
    }
    public String zombieNautilusChargeTargetNickname() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeTargetNickname() : null;
    }
    public long zombieNautilusChargeTargetMobId() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeTargetMobId() : 0L;
    }
    public double zombieNautilusChargeVx() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeVx() : 0.0;
    }
    public double zombieNautilusChargeVy() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeVy() : 0.0;
    }
    public double zombieNautilusChargeVz() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeVz() : 0.0;
    }
    public double zombieNautilusChargeDistance() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.chargeDistance() : 0.0;
    }
    public int zombieNautilusNaturalTargetCooldownMcTicks() {
        return this instanceof NautilusFamilyMob nautilus ? nautilus.naturalTargetCooldownMcTicks() : 0;
    }
    public int illusionerInvisibilityMcTicks() {
        return this instanceof Illusioner illusioner ? illusioner.invisibilityMcTicks() : 0;
    }
    public int illusionerMirrorCooldownMcTicks() {
        return this instanceof Illusioner illusioner ? illusioner.mirrorCooldownMcTicks() : 0;
    }
    public int illusionerBlindnessCooldownMcTicks() {
        return this instanceof Illusioner illusioner ? illusioner.blindnessCooldownMcTicks() : 0;
    }
    public int illusionerCastMcTicks() {
        return this instanceof Illusioner illusioner ? illusioner.castMcTicks() : 0;
    }
    public String illusionerBlindTargetKey() {
        return this instanceof Illusioner illusioner ? illusioner.blindTargetKey() : null;
    }
    public String customName() { return customName; }
    public void setOwnerNickname(String ownerNickname) { this.ownerNickname = ownerNickname; }

    /** Applies the accepted 1/3 bone-taming roll; ownership itself is durable generic mob state. */
    public boolean tameWolf(String nickname) {
        if (type != MobType.WOLF || ownerNickname != null || nickname == null || nickname.isBlank()) {
            return false;
        }
        ownerNickname = nickname;
        persistenceRequired = true;
        healthPoints = 40.0;
        hp = 40;
        return true;
    }

    /** Applies the accepted 1/3 raw-fish trust roll; Ocelots are never owners or sit-tamed. */
    public boolean trustOcelot() {
        if (type != MobType.OCELOT || ocelotTrusting) return false;
        ocelotTrusting = true;
        persistenceRequired = true;
        return true;
    }

    void restoreOcelotTrusting(boolean trusting) {
        if (type != MobType.OCELOT && trusting) {
            throw new IllegalStateException("non-Ocelot mob has persisted trusting state");
        }
        ocelotTrusting = trusting;
    }

    public boolean toggleWolfSitting(String nickname) {
        if (type != MobType.WOLF || ownerNickname == null
                || !ownerNickname.equals(nickname)) return false;
        wolfSitting = !wolfSitting;
        persistenceRequired = true;
        return true;
    }

    public boolean dyeWolfCollar(String nickname, int dyeColorId) {
        return this instanceof Wolf wolf && wolf.dyeCollar(nickname, dyeColorId);
    }

    public boolean equipWolfBodyArmor(String nickname) {
        return this instanceof Wolf wolf && wolf.equipBodyArmor(nickname);
    }

    public boolean repairWolfBodyArmor(String nickname) {
        return this instanceof Wolf wolf && wolf.repairBodyArmor(nickname);
    }

    /** Returns the remaining armor durability, or zero when the interaction is rejected. */
    public int removeWolfBodyArmor(String nickname) {
        return this instanceof Wolf wolf ? wolf.removeBodyArmor(nickname) : 0;
    }

    public boolean feedTadpoleGrowth() {
        return this instanceof Tadpole tadpole && tadpole.feedGrowth();
    }

    /** 좀비 주민이며 약함을 들고 있고 아직 전환 중이 아닌가. 상태를 바꾸지 않는다. */
    public boolean zombieVillagerCurable() {
        return this instanceof ZombieVillager zombieVillager && !zombieVillager.converting()
                && statusEffects().has(StatusEffect.WEAKNESS);
    }

    /** 좀비 주민 치료 카운트다운 시작. 약함 보유 + 미전환일 때만 성립한다. */
    public boolean beginZombieVillagerConversion(String nickname, MobRandom rng) {
        return this instanceof ZombieVillager zombieVillager
                && zombieVillager.beginConversion(nickname, rng);
    }

    public boolean tameZombieNautilus(String nickname) {
        return this instanceof NautilusFamilyMob nautilus && nautilus.tame(nickname);
    }

    public boolean feedZombieNautilus(String nickname, double amount) {
        return this instanceof NautilusFamilyMob nautilus && nautilus.feed(nickname, amount);
    }

    void restoreWolfSpeciesState(int collarColor, int armorDurability) {
        if (this instanceof Wolf wolf) wolf.restoreWolfState(collarColor, armorDurability);
        else if (collarColor != -1 || armorDurability != 0) {
            throw new IllegalStateException("non-Wolf mob has persisted Wolf equipment state");
        }
    }

    void restoreTadpoleAge(int ageMcTicks) {
        if (this instanceof Tadpole tadpole) tadpole.restoreAge(ageMcTicks);
        else if (ageMcTicks != 0) {
            throw new IllegalStateException("non-Tadpole mob has persisted Tadpole age");
        }
    }

    void restoreZombieVillagerConversion(int conversionMcTicks, String conversionStarter) {
        if (this instanceof ZombieVillager zombieVillager) {
            zombieVillager.restoreConversion(conversionMcTicks, conversionStarter);
        } else if (conversionMcTicks != 0 || conversionStarter != null) {
            throw new IllegalStateException(
                    "non-Zombie-Villager mob has persisted cure conversion state");
        }
    }

    public boolean heal(double amount) {
        if (isDead() || amount <= 0.0 || healthPoints >= maxHp()) return false;
        healthPoints = Math.min(maxHp(), healthPoints + amount);
        hp = (int) Math.ceil(healthPoints - 1e-9);
        return true;
    }

    /**
     * [NAUTILUS-BEHAVIOR] 만피인가. {@link #heal} 이 거짓을 돌려준 이유가 "회복할 것이 없다"
     * 하나인지 호출부가 가려낼 때 쓴다 — [B] «Nautilus» 의 "healed (or bred if at full
     * health)" 갈림이 정확히 이 술어다.
     */
    public boolean isAtFullHealth() {
        return !isDead() && healthPoints >= maxHp();
    }

    void restoreWolfSitting(boolean sitting) {
        if (type != MobType.WOLF && sitting) {
            throw new IllegalStateException("non-Wolf mob has persisted sitting state");
        }
        if (sitting && ownerNickname == null) {
            throw new IllegalStateException("wild Wolf has persisted sitting state");
        }
        wolfSitting = sitting;
    }
    public void setCustomName(String customName) { this.customName = customName; }
    public boolean shouldPersist() {
        return persistenceRequired || ownerNickname != null || customName != null || placedVehicleId != 0;
    }
    public boolean persistenceRequired() { return persistenceRequired; }
    public void setPersistenceRequired(boolean value) { persistenceRequired = value; }
    final void assignIllagerContext(IllagerCompanionPolicy.Context context,
            long contextIdentity, int policyVersion) {
        if (context == null || policyVersion <= 0) {
            throw new IllegalArgumentException("invalid illager encounter context");
        }
        illagerContext = context;
        illagerContextIdentity = contextIdentity;
        illagerPolicyVersion = policyVersion;
    }
    /**
     * Brands one released raider with the raid instance that owns it. The pair survives restart so
     * the ledger can rebuild its roster without re-deriving membership from positions.
     */
    public final void assignRaid(long raidId, int raidWave) {
        if (raidId == 0L) return;
        this.raidId = raidId;
        this.raidWave = Math.max(0, raidWave);
    }

    /** Removes only the raid ownership brand; other persistence reasons remain intact. */
    public final void clearRaid() {
        raidId = 0L;
        raidWave = 0;
        raidBuffLevel = 0;
    }

    /**
     * [RAID-OMEN] {@code Raider#applyRaidBuffs} 가 합류 때 준 무기 마법 부여 레벨(약탈자 빠른 장전 · 변명자 날카로움,
     * 0 = 없음). 정본은 레이드 원장 명단이며 몹 행은 영속하지 않는 사본을 든다.
     */
    private int raidBuffLevel;

    public final void setRaidBuffLevel(int level) {
        raidBuffLevel = Math.max(0, level);
    }

    public final int raidBuffLevel() { return raidBuffLevel; }

    public final long raidId() { return raidId; }
    public final int raidWave() { return raidWave; }
    final IllagerCompanionPolicy.Context illagerContext() { return illagerContext; }
    final long illagerContextIdentity() { return illagerContextIdentity; }
    final int illagerPolicyVersion() { return illagerPolicyVersion; }

    /**
     * 개체별 1/128 동행 동물 판정을 이 개체에 한 번만 확정합니다. 두 번째 평가는 재굴림 없이
     * 확정된 결과를 그대로 쓰므로 reconnect·언로드·재기동 뒤에도 같은 개체가 같은 결과를 유지합니다.
     */
    final void settleCompanionDecision(boolean winner) {
        if (companionDecisionSettled) {
            throw new IllegalStateException("companion decision already settled");
        }
        companionDecisionSettled = true;
        companionDecisionWinner = winner;
    }

    final void restoreCompanionDecision(boolean settled, boolean winner) {
        if (!settled && winner) {
            throw new IllegalArgumentException("unsettled companion decision cannot be a winner");
        }
        companionDecisionSettled = settled;
        companionDecisionWinner = winner;
    }

    public boolean companionDecisionSettled() { return companionDecisionSettled; }
    public boolean companionDecisionWinner() { return companionDecisionWinner; }
    public boolean isBaby() { return babyForm || ageTicksRemaining > 0; }
    public boolean babyForm() { return babyForm; }
    public int ageTicksRemaining() { return ageTicksRemaining; }
    public boolean isInLoveMode() {
        return loveTicksRemaining > 0 && (type != MobType.CAT || ownerNickname() != null);
    }
    public int loveTicksRemaining() { return loveTicksRemaining; }
    public int breedingCooldownTicks() { return breedingCooldownTicks; }
    /** 슬라임 이외의 몹은 0을 반환한다. */
    public int slimeSize() { return 0; }

    protected final void setInitialHealth(double health) {
        healthPoints = health;
        hp = (int) Math.ceil(health - 1e-9);
    }
    int airSupplyTicks() { return airSupplyTicks; }
    int underwaterConversionTicks() { return underwaterConversionTicks; }

    /** 종별 구현이 검증한 성장 시간을 주입하는 위임점. 수치는 공통 골격에 두지 않는다. */
    public void setAgeTicksRemaining(int ticks) {
        ageTicksRemaining = Math.max(0, ticks);
        if (ageTicksRemaining > 0) loveTicksRemaining = 0;
    }

    /** 영속 스냅샷의 성장·재번식 대기 시간과 love mode를 복원한다. */
    public void restoreBreedingState(int ageTicks, int cooldownTicks) {
        restoreBreedingState(ageTicks, cooldownTicks, 0);
    }

    public void restoreBreedingState(int ageTicks, int cooldownTicks, int loveTicks) {
        ageTicksRemaining = Math.max(0, ageTicks);
        breedingCooldownTicks = Math.max(0, cooldownTicks);
        loveTicksRemaining = Math.max(0, loveTicks);
        if (ageTicksRemaining > 0) loveTicksRemaining = 0;
    }

    public void restoreBabyForm(boolean value) {
        babyForm = value || type == MobType.BABY_ZOMBIE;
    }

    /**
     * [PIGLIN-BABY] 성장 타이머를 다 쓴 새끼 형태를 성체로 확정한다. {@code babyForm} 은 영속
     * 필드라 "생성 굴림을 이미 돌렸다"는 사실을 재접속·언로드 뒤에도 들고 있고, 이 전이가
     * 성장 경계를 <b>정확히 한 번</b> 지나게 한다. {@code BABY_ZOMBIE} 처럼 종 자체가 새끼인
     * 몹은 성체가 될 수 없으므로 건드리지 않는다.
     */
    protected final boolean growOutOfBabyForm() {
        if (!babyForm || type == MobType.BABY_ZOMBIE || ageTicksRemaining > 0) return false;
        babyForm = false;
        return true;
    }

    /** 저장 상태는 다음 피해/변환 틱 직전의 유효 범위로 제한해 손상된 행이 즉시 연쇄 이벤트를 만들지 않게 한다. */
    public void restoreWaterLifecycleState(int airTicks, int conversionTicks) {
        airSupplyTicks = Math.max(-DROWN_DAMAGE_INTERVAL_TICKS + 1,
                Math.min(maxAirSupplyTicks(), airTicks));
        underwaterConversionTicks = Math.max(0,
                Math.min(WATER_CONVERSION_TICKS - 1, conversionTicks));
    }

    /** 수생몹의 바닐라 Air NBT 정본. 피해 시 0으로 되돌아가며 수중에서는 최대치로 회복한다. */
    protected final boolean tickDryAirSupply() {
        airSupplyTicks--;
        if (airSupplyTicks > -DROWN_DAMAGE_INTERVAL_TICKS) return false;
        airSupplyTicks = 0;
        return true;
    }

    protected final void restoreAquaticAirSupply() {
        airSupplyTicks = maxAirSupplyTicks();
    }

    private int maxAirSupplyTicks() {
        return type == MobType.AXOLOTL ? AXOLOTL_MAX_AIR_SUPPLY_TICKS
                : MAX_AIR_SUPPLY_TICKS;
    }

    /** 종별 먹이 검증 뒤 호출한다. 아기와 비대상 종은 love-mode에 들어가지 않는다. */
    public boolean enterLoveMode(int ticks) {
        if (!type.breedingEnabled() || isBaby() || breedingCooldownTicks > 0
                || loveTicksRemaining > 0 || ticks <= 0) return false;
        if ((type == MobType.HORSE || type == MobType.DONKEY || type == MobType.LLAMA
                || type == MobType.WOLF || type == MobType.NAUTILUS || type == MobType.CAT)
                && ownerNickname == null) return false;
        loveTicksRemaining = ticks;
        return true;
    }

    void clearLoveMode() { loveTicksRemaining = 0; }

    void beginBreedingCooldown(int ticks) {
        breedingCooldownTicks = Math.max(0, ticks);
        loveTicksRemaining = 0;
    }

    /** 새끼 먹이 1회: Java처럼 남은 성장 시간의 10%를 초 단위 내림으로 단축한다. */
    public boolean accelerateBabyGrowth(int ticksPerSecond) {
        if (!isBaby() || ticksPerSecond <= 0) return false;
        int seconds = (int) Math.floor((ageTicksRemaining / (double) ticksPerSecond) * 0.1);
        ageTicksRemaining = Math.max(0, ageTicksRemaining - seconds * ticksPerSecond);
        return true;
    }

    /**
     * 바닐라 {@code ageUp(seconds)}: 남은 성장 시간을 초 단위로 앞당긴다.
     * 양이 풀을 먹었을 때의 새끼 성장 가속(60초)이 현재 유일한 호출처다.
     */
    public void ageUpSeconds(int seconds, int ticksPerSecond) {
        if (seconds <= 0 || ticksPerSecond <= 0) return;
        ageTicksRemaining = Math.max(0, ageTicksRemaining - seconds * ticksPerSecond);
    }

    /** love-mode였던 개체만 공간 인덱스를 갱신하도록 호출자에게 알려 cold mob hash 조회를 피한다. */
    boolean tickBreedingState() {
        boolean wasInLoveMode = loveTicksRemaining > 0;
        if (ageTicksRemaining > 0) ageTicksRemaining--;
        if (loveTicksRemaining > 0) loveTicksRemaining--;
        if (breedingCooldownTicks > 0) breedingCooldownTicks--;
        return wasInLoveMode;
    }

    /** 좀비·스켈레톤만 무기/방어구를 줍는다. 성분 없는 스택의 호환 진입점이다. */
    public boolean tryPickupEquipment(short itemType, int durability) {
        return tryPickupEquipment(itemType, durability, 0L, null, null);
    }

    /**
     * [MOB-EQUIP] 바닐라 {@code Mob.equipItemIfPossible}: 제 칸을 {@code canReplaceCurrentItem} 으로 바꿀 수
     * 있으면 그 칸, 방어구가 제 칸을 못 바꾸면 빈 주손으로 들어간다. 들어간 칸은 보장 드랍(2.0)이 되고,
     * 밀려난 장비는 그 칸의 확률로 {@code max(nextFloat - 0.1, 0) < chance} 일 때만 되돌린다
     * ({@link #takeReplacedEquipment}). 성분(인챈트·이름 등)은 그대로 옮긴다.
     *
     * @param rng 교체 드랍 굴림. null 이면 굴리지 않고 밀려난 장비를 드랍 확률 0 이 아닐 때 되돌린다.
     */
    public boolean tryPickupEquipment(short itemType, int durability, long enchantments,
            String components, MobRandom rng) {
        if (this instanceof SulfurCube) {
            return enchantments == 0L && components == null && tryPickupEquipment(itemType, durability);
        }
        int bit = pickupSlotBit(itemType, durability, enchantments, components);
        if (bit == 0) return false;
        validateEquipmentDurability(itemType, durability);
        validateSlotComponents(itemType, enchantments, components);
        float chance = slotDropChance(bit);
        short oldItem;
        int oldDurability;
        long oldEnchantments;
        String oldComponents;
        if (bit == MobEquipmentRules.MAIN_HAND_BIT) {
            oldItem = heldItem;
            oldDurability = heldItemDurability;
            oldEnchantments = heldItem == PlayerInventory.EMPTY ? 0L : heldItemEnchantments;
            oldComponents = heldItem == PlayerInventory.EMPTY ? null : heldItemComponents;
            heldItem = itemType;
            heldItemDurability = durability;
            heldItemEnchantments = enchantments;
            heldItemComponents = components;
        } else {
            int i = Integer.numberOfTrailingZeros(bit) - 1;
            oldItem = equippedArmor[i];
            oldDurability = equippedArmorDurability[i];
            oldEnchantments = equippedArmorEnchantments[i];
            oldComponents = equippedArmorComponents[i];
            equippedArmor[i] = itemType;
            equippedArmorDurability[i] = durability;
            equippedArmorEnchantments[i] = enchantments;
            equippedArmorComponents[i] = components;
        }
        replacedEquipment = oldItem;
        replacedEquipmentDurability = oldItem == PlayerInventory.EMPTY ? 0 : oldDurability;
        replacedEquipmentEnchantments = oldItem == PlayerInventory.EMPTY ? 0L : oldEnchantments;
        replacedEquipmentComponents = oldItem == PlayerInventory.EMPTY ? null : oldComponents;
        replacedEquipmentDrops = oldItem != PlayerInventory.EMPTY && (rng == null
                ? chance > 0.0F
                : MobEquipmentRules.replacedItemDrops(rng.nextFloat(), chance));
        guaranteedDropSlots |= bit;
        noDropSlots &= ~bit;
        pickedUpEquipment = true;
        return true;
    }

    public boolean canPickupEquipment(short itemType) {
        return canPickupEquipment(itemType, PlayerInventory.initialDurability(itemType), 0L, null);
    }

    /** [MOB-EQUIP] 이 스택(성분 포함)을 지금 주워 입을 칸이 있는가. */
    public boolean canPickupEquipment(short itemType, int durability, long enchantments,
            String components) {
        if (this instanceof SulfurCube) {
            return enchantments == 0L && components == null && canPickupEquipment(itemType);
        }
        return pickupSlotBit(itemType, durability, enchantments, components) != 0;
    }

    /**
     * 주워 넣을 칸 비트(0 = 줍지 않음). 종 게이트({@link #canEverPickupEquipment})와 품목 게이트(방어구·
     * 검·도구)는 이 저장소 규칙 그대로이고, 칸 판정은 바닐라 {@code canReplaceCurrentItem} 이다.
     */
    private int pickupSlotBit(short itemType, int durability, long enchantments, String components) {
        if (!canEverPickupEquipment() || itemType == PlayerInventory.EMPTY) return 0;
        MobEquipmentRules.Stack candidate =
                new MobEquipmentRules.Stack(itemType, durability, enchantments, components);
        if (PlayerInventory.isArmor(itemType)) {
            ArmorSlot slot = PlayerInventory.armorSlot(itemType);
            if (MobEquipmentRules.canReplaceCurrentItem(candidate, armorStack(slot.ordinal()), true,
                    PlayerInventory.EMPTY)) {
                return MobEquipmentRules.armorBit(slot.ordinal());
            }
            // equipItemIfPossible: 방어구 칸을 못 바꾸면 slot = MAINHAND, 교체 가능 = 주손이 비었는가.
            return heldStack().isEmpty() ? MobEquipmentRules.MAIN_HAND_BIT : 0;
        }
        if (!InventoryRules.isSword(itemType) && !InventoryRules.isTool(itemType)) return 0;
        return MobEquipmentRules.canReplaceCurrentItem(candidate, heldStack(), false,
                preferredWeapon()) ? MobEquipmentRules.MAIN_HAND_BIT : 0;
    }

    /** 칸 하나의 바닐라 드랍 확률: 트라이얼 0.0, 주운 장비 2.0, 그 밖 0.085. */
    private float slotDropChance(int bit) {
        if ((noDropSlots & bit) != 0) return 0.0F;
        if ((guaranteedDropSlots & bit) != 0) return MobEquipmentRules.GUARANTEED_DROP_CHANCE;
        return MobEquipmentRules.NATURAL_DROP_CHANCE;
    }

    /** Stable species gate used to keep passive and unrelated mobs out of dropped-equipment scans. */
    public boolean canEverPickupEquipment() {
        return type == MobType.ZOMBIE || type == MobType.BABY_ZOMBIE
                || type == MobType.ZOMBIE_PIGMAN || type == MobType.ZOMBIE_VILLAGER
                || type == MobType.HUSK || type == MobType.DROWNED
                || type == MobType.SKELETON || type == MobType.STRAY
                // [PARCHED-FAMILY] 파치드는 스켈레톤 변종이라 계열 전체와 같이 장비를 줍는다.
                // 원문이 "창만은 어떤 경우에도 줍지 않는다"고 못 박은 것 자체가 나머지 장비는
                // 줍는다는 뜻이고, 이 저장소에는 창 아이템이 없어 예외 분기가 필요 없다.
                || type == MobType.BOGGED || type == MobType.PARCHED;
    }

    /**
     * 장비 교체 직후 이전 장비를 드랍 엔티티로 되돌리기 위한 1회 소비 값. [MOB-EQUIP] 그 칸의 드랍 확률
     * 굴림에서 떨어지지 않은 장비(트라이얼 장비 포함)는 null 로 사라진다.
     */
    public EquipmentDrop takeReplacedEquipment() {
        if (replacedEquipment == 0) return null;
        EquipmentDrop old = replacedEquipmentDrops
                ? new EquipmentDrop(replacedEquipment, replacedEquipmentDurability,
                        replacedEquipmentEnchantments, replacedEquipmentComponents)
                : null;
        replacedEquipment = 0;
        replacedEquipmentDurability = 0;
        replacedEquipmentEnchantments = 0L;
        replacedEquipmentComponents = null;
        replacedEquipmentDrops = false;
        return old;
    }

    /**
     * 비어 있지 않은 방어구 칸 수. [SURV-X] 처치 XP 의 방어구 보너스가 칸마다 한 번씩 굴리므로
     * (바닐라 {@code Mob#getExperienceReward} 의 {@code HUMANOID_ARMOR} 루프) 그 횟수가 된다.
     * 주손은 방어구 칸이 아니라 세지 않는다.
     */
    public int equippedArmorCount() {
        int n = 0;
        for (short item : equippedArmor) if (item != 0) n++;
        return n;
    }

    /**
     * 사망 시 주운 장비(보장 칸)는 MC 규칙대로 100% 반환한다. [MOB-EQUIP] 칸별 판정이라 같은 몹의 자연·
     * 트라이얼 칸은 여기 오지 않고, 소실의 저주가 걸린 장비는 떨어지지 않는다. 성분을 그대로 싣는다.
     */
    public EquipmentDrop[] pickedEquipmentDrops() {
        if (guaranteedDropSlots == 0) return new EquipmentDrop[0];
        EquipmentDrop[] out = new EquipmentDrop[1 + equippedArmor.length];
        int n = 0;
        if (droppableSlot(MobEquipmentRules.MAIN_HAND_BIT, true)) {
            out[n++] = new EquipmentDrop(heldItem, heldItemDurability,
                    heldItemEnchantments, heldItemComponents);
        }
        for (int slot = 0; slot < equippedArmor.length; slot++) {
            if (droppableSlot(MobEquipmentRules.armorBit(slot), true)) {
                out[n++] = new EquipmentDrop(equippedArmor[slot], equippedArmorDurability[slot],
                        equippedArmorEnchantments[slot], equippedArmorComponents[slot]);
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /** 칸이 비어 있지 않고, 요청한 계층(보장/자연)이며, 드랍 없음·소실의 저주가 아닌가. */
    private boolean droppableSlot(int bit, boolean guaranteed) {
        MobEquipmentRules.Stack stack = bit == MobEquipmentRules.MAIN_HAND_BIT
                ? (heldItem == PlayerInventory.EMPTY ? MobEquipmentRules.Stack.EMPTY : heldStack())
                : armorStack(Integer.numberOfTrailingZeros(bit) - 1);
        if (stack.isEmpty() || (noDropSlots & bit) != 0) return false;
        if (((guaranteedDropSlots & bit) != 0) != guaranteed) return false;
        return !MobEquipmentRules.preventsEquipmentDrop(stack);
    }

    /**
     * 생성 장비의 사망 드랍. 바닐라 {@code LivingEntity.dropCustomDeathLoot} 대로 주손·방어구
     * 네 칸을 각각 8.5%(+약탈 레벨당 1%)로 굴리고, 살아남은 칸은 손상된 내구로 떨어진다.
     * 땅에서 주운(보장) 칸은 100% 반환 규칙이 따로 있고, 트라이얼 칸(0.0)과 소실의 저주는 굴리지 않는다.
     */
    public EquipmentDrop[] naturalEquipmentDrops(MobRandom rng, int lootingLevel) {
        EquipmentDrop[] rolled = new EquipmentDrop[1 + equippedArmor.length];
        int found = 0;
        for (int slot = -1; slot < equippedArmor.length; slot++) {
            int bit = slot < 0 ? MobEquipmentRules.MAIN_HAND_BIT : MobEquipmentRules.armorBit(slot);
            if (!droppableSlot(bit, false)) continue;
            if (!EquipmentDropRules.drops(rng.nextFloat(), lootingLevel)) continue;
            short item = slot < 0 ? heldItem : equippedArmor[slot];
            int maximum = PlayerInventory.initialDurability(item);
            rolled[found++] = new EquipmentDrop(item,
                    maximum <= 0 ? 0 : EquipmentDropRules.droppedDurability(maximum, rng),
                    slot < 0 ? heldItemEnchantments : equippedArmorEnchantments[slot],
                    slot < 0 ? heldItemComponents : equippedArmorComponents[slot]);
        }
        return java.util.Arrays.copyOf(rolled, found);
    }

    /** 저장된 전투·장비 aggregate를 한 번에 복원한다. 죽은 행이나 잘못된 장비 내구도는 허용하지 않는다. */
    void restorePersistentCombatState(double exactHealth, boolean pickedUp,
            short restoredHeldItem, int restoredHeldDurability,
            short helmetItem, int helmetDurability,
            short chestplateItem, int chestplateDurability,
            short leggingsItem, int leggingsDurability,
            short bootsItem, int bootsDurability) {
        if (!Double.isFinite(exactHealth) || exactHealth <= 1e-9 || exactHealth > maxHp()) {
            throw new IllegalStateException("invalid persisted health " + exactHealth
                    + " for " + type.name());
        }
        short[] items = {helmetItem, chestplateItem, leggingsItem, bootsItem};
        int[] durabilities = {helmetDurability, chestplateDurability,
                leggingsDurability, bootsDurability};
        validateHeldItem(restoredHeldItem, restoredHeldDurability,
                type == MobType.ALLAY || type == MobType.COPPER_GOLEM || type == MobType.ITEM_FRAME);
        for (int i = 0; i < items.length; i++) {
            validateEquipmentDurability(items[i], durabilities[i]);
            if (items[i] != 0 && PlayerInventory.armorSlot(items[i]) != ArmorSlot.values()[i]) {
                throw new IllegalStateException("persisted armor item is in the wrong slot");
            }
        }
        healthPoints = exactHealth;
        hp = (int) Math.ceil(exactHealth - 1e-9);
        state = MobState.IDLE;
        pickedUpEquipment = pickedUp;
        spawnWeaponPending = false;
        heldItem = restoredHeldItem;
        heldItemDurability = restoredHeldDurability;
        System.arraycopy(items, 0, equippedArmor, 0, items.length);
        System.arraycopy(durabilities, 0, equippedArmorDurability, 0, durabilities.length);
        // [MOB-EQUIP] 옛 행(성분 열 없음)의 파생: 성분 없음, 주운 몹이면 모든 칸 보장.
        heldItemEnchantments = 0L;
        heldItemComponents = null;
        java.util.Arrays.fill(equippedArmorEnchantments, 0L);
        java.util.Arrays.fill(equippedArmorComponents, null);
        guaranteedDropSlots = pickedUp ? MobEquipmentRules.ALL_SLOTS : 0;
    }

    /**
     * [MOB-EQUIP] 영속 열 {@code equipment_components}({@link MobEquipmentComponentsCodec}) 복원.
     * {@link #restorePersistentCombatState} 와 {@link #restoreTrialEquipmentNoDrop} 뒤에 부른다. null 은
     * 옛 행이라 그 둘이 파생한 값을 그대로 둔다.
     */
    void restoreEquipmentComponents(String encoded) {
        if (encoded == null) return;
        MobEquipmentComponentsCodec.Decoded decoded = MobEquipmentComponentsCodec.decode(encoded);
        short heldCarrier = heldItem == PlayerInventory.EMPTY ? nativeRangedWeapon() : heldItem;
        if (heldCarrier == PlayerInventory.EMPTY
                && (decoded.enchantments(0) != 0L || decoded.components(0) != null)) {
            throw new IllegalStateException("persisted components on an empty main hand");
        }
        validateSlotComponents(heldCarrier, decoded.enchantments(0), decoded.components(0));
        for (int i = 0; i < equippedArmor.length; i++) {
            validateSlotComponents(equippedArmor[i], decoded.enchantments(i + 1),
                    decoded.components(i + 1));
        }
        heldItemEnchantments = decoded.enchantments(0);
        heldItemComponents = decoded.components(0);
        for (int i = 0; i < equippedArmor.length; i++) {
            equippedArmorEnchantments[i] = decoded.enchantments(i + 1);
            equippedArmorComponents[i] = decoded.components(i + 1);
        }
        guaranteedDropSlots = decoded.guaranteedDropSlots();
        noDropSlots = decoded.noDropSlots();
        pickedUpEquipment = guaranteedDropSlots != 0;
        trialEquipmentNoDrop = noDropSlots != 0;
    }

    /**
     * [MOB-EQUIP] 영속 열 값. 성분이 없고 칸별 드랍 계층이 몹 단위 불리언(주운 몹·트라이얼)에서 그대로
     * 파생되면 null 이라 옛 행과 새 행이 바이트 단위로 같다.
     */
    public String encodeEquipmentComponents() {
        int occupied = occupiedEquipmentSlots();
        int legacyGuaranteed = pickedUpEquipment ? occupied : 0;
        int legacyNoDrop = trialEquipmentNoDrop ? occupied : 0;
        if (!hasEquipmentComponents() && (guaranteedDropSlots & occupied) == legacyGuaranteed
                && (noDropSlots & occupied) == legacyNoDrop) {
            return null;
        }
        long[] words = new long[1 + equippedArmor.length];
        String[] components = new String[1 + equippedArmor.length];
        words[0] = heldItemEnchantments;
        components[0] = heldItemComponents;
        for (int i = 0; i < equippedArmor.length; i++) {
            words[i + 1] = equippedArmorEnchantments[i];
            components[i + 1] = equippedArmorComponents[i];
        }
        return MobEquipmentComponentsCodec.encode(guaranteedDropSlots & MobEquipmentRules.ALL_SLOTS,
                noDropSlots & MobEquipmentRules.ALL_SLOTS, words, components);
    }

    /** [MOB-EQUIP] 칸 성분 검증({@link MobEquipmentRules#validComponents}). */
    private static void validateSlotComponents(short itemType, long enchantments, String components) {
        if (!MobEquipmentRules.validComponents(itemType, enchantments, components)) {
            throw new IllegalStateException("invalid equipment components for item "
                    + Short.toUnsignedInt(itemType));
        }
    }

    private static void validateEquipmentDurability(short itemType, int durability) {
        if (itemType == 0) {
            if (durability != 0) throw new IllegalStateException("empty equipment has durability");
            return;
        }
        if (!PlayerInventory.isDurable(itemType)
                || durability <= 0 || durability > PlayerInventory.initialDurability(itemType)) {
            throw new IllegalStateException("invalid equipment durability " + durability
                    + " for item " + Short.toUnsignedInt(itemType));
        }
    }

    private static void validateHeldItem(short itemType, int durability,
            boolean allowOrdinaryItem) {
        if (itemType == PlayerInventory.EMPTY) {
            if (durability != 0) throw new IllegalStateException("empty held item has durability");
            return;
        }
        if (!PlayerInventory.isDurable(itemType)) {
            if (!allowOrdinaryItem || durability != 0) {
                throw new IllegalStateException("invalid ordinary held item durability");
            }
            return;
        }
        validateEquipmentDurability(itemType, durability);
    }

    /** 드랍 한 칸의 아이템과 남은 내구도. 사망/교체 시에만 만들어지는 저빈도 값 객체다. */
    public static final class EquipmentDrop {
        private final short itemType;
        private final int durability;
        /** [MOB-EQUIP] 워드 0 인챈트와 성분 문자열(플레이어 스택과 같은 표현). */
        private final long enchantments;
        private final String itemComponentData;

        EquipmentDrop(short itemType, int durability) {
            this(itemType, durability, 0L, null);
        }

        EquipmentDrop(short itemType, int durability, long enchantments, String itemComponentData) {
            this.itemType = itemType;
            this.durability = durability;
            this.enchantments = enchantments;
            this.itemComponentData = itemComponentData;
        }

        public short itemType() { return itemType; }
        public int durability() { return durability; }
        public long enchantments() { return enchantments; }
        public String itemComponentData() { return itemComponentData; }
    }

    public void mountBoat(long boatId, double x, double y, double z, double yaw) {
        mountedBoatId = boatId;
        syncBoatSeat(x, y, z, yaw);
    }

    public void syncBoatSeat(double x, double y, double z, double yaw) {
        if (mountedBoatId == 0) return;
        vehicleX = x; vehicleY = y; vehicleZ = z; vehicleYaw = yaw;
        lockToVehicle();
    }

    /** Binds this mob as a passenger of another authoritative mob. */
    void mountMobVehicle(long vehicleId, double x, double y, double z, double yaw) {
        if (vehicleId <= 0 || vehicleId == id || mountedBoatId != 0 || placedVehicleId != 0) {
            throw new IllegalArgumentException("invalid mob vehicle binding");
        }
        long previousVehicleId = vehicleMobId;
        vehicleMobId = vehicleId;
        notifyVehicleBindingChanged(previousVehicleId);
        syncMobVehicle(x, y, z, yaw);
    }

    /** Restores only the durable edge; the runtime resolves and synchronizes it after all rows load. */
    void restoreMobVehicle(long vehicleId) {
        if (vehicleId < 0 || vehicleId == id || mountedBoatId != 0 || placedVehicleId != 0) {
            throw new IllegalArgumentException("invalid persisted mob vehicle binding");
        }
        long previousVehicleId = vehicleMobId;
        vehicleMobId = vehicleId;
        notifyVehicleBindingChanged(previousVehicleId);
    }

    void syncMobVehicle(double x, double y, double z, double yaw) {
        if (vehicleMobId == 0) return;
        vehicleX = x;
        vehicleY = y;
        vehicleZ = z;
        vehicleYaw = yaw;
        lockToVehicle();
    }

    /**
     * [SPEAR-KINETIC] 창 돌진의 하마({@code Entity.stopRiding}): 몹 탈것에서 내린다. 런타임 승객 색인은 바인딩
     * 리스너가 갱신한다.
     */
    // ── [SPEAR-MOB] 창 돌진 goal(SpearUseGoal · 피글린 Spear* 두뇌). 바닐라 goal·recentKineticEnemies 처럼 저장하지 않는다 ──
    /** 진행 중인 창 돌진 goal. 쓰지 않으면 null. */
    SpearUseAi.State spearUse;
    /** 창을 쓰는 동안의 시선(눈 → 표적 눈, 단위 벡터). */
    double spearLookX;
    double spearLookY;
    double spearLookZ;
    /** 지난 권위 틱의 위치(창 쓰는 동안의 알려진 이동). */
    double spearLastX;
    double spearLastY;
    double spearLastZ;
    boolean spearHasLast;
    /** {@code LivingEntity.recentKineticEnemies}: 대상 열쇠 → 마지막으로 찌른 쓰기 MC 틱. */
    final java.util.Map<String, Long> spearStabbedAt = new java.util.HashMap<>();

    /** [SPEAR-MOB] 창 돌진 goal 상태(없으면 null). */
    public SpearUseAi.State spearUseState() { return spearUse; }

    /** [SPEAR-MOB] 창을 쓰는 중이면 시선 {x, y, z}. */
    public double[] spearLook() { return new double[] {spearLookX, spearLookY, spearLookZ}; }

    /**
     * [SPEAR-MOB] 이번 권위 틱의 MC 틱당 이동(지난 권위 틱 위치 기준, 처음이면 0)을 돌려주고 기준점을 옮긴다.
     */
    public double[] consumeSpearMovement() {
        double[] move = spearHasLast
                ? new double[] {(x - spearLastX) / 2.0, (y - spearLastY) / 2.0, (z - spearLastZ) / 2.0}
                : new double[] {0.0, 0.0, 0.0};
        spearLastX = x;
        spearLastY = y;
        spearLastZ = z;
        spearHasLast = true;
        return move;
    }

    /** [SPEAR-MOB] 창 쓰기가 끝났다: 이동 기준점과 찌른 기록을 버린다(다음 쓰기는 새로 센다). */
    public void resetSpearTracking() {
        spearHasLast = false;
        spearStabbedAt.clear();
    }

    /** [SPEAR-MOB] 찌른 기록(접촉 쿨다운 표). */
    public java.util.Map<String, Long> spearStabbedAt() { return spearStabbedAt; }

    /**
     * [SPEAR-MOB] 창 돌진 goal 을 쓰는 종인가: 바닐라 {@code Zombie.addBehaviourGoals} 의 {@code SpearUseGoal}(좀비 ·
     * 새끼 좀비 · 허스크 · 좀비 주민 · 좀비화 피글린, 드라운드는 재정의로 빠진다)과 피글린 FIGHT 의 Spear* 행동.
     */
    public boolean spearUseEligible() { return false; }

    /** [SPEAR-MOB] {@code getAttributeBaseValue(ATTACK_DAMAGE)}(돌진 피해의 기본 공격력). 기본 2(LivingEntity 기본값). */
    public double attackDamageAttributeBase() { return 2.0; }

    /** [SPEAR-MOB] 창 돌진 goal 이 돌 조건: 자격 있는 종이 {@code KINETIC_WEAPON} 을 주손에 쥐었다. */
    public boolean spearUseActive() {
        return spearUseEligible() && com.gameexpert.engine.SpearRules.kinetic(heldItem()) != null;
    }

    /** [SPEAR-MOB] 표적 눈을 바라본다({@code LookControl}): 수평 yaw 와 창 시선 벡터. */
    protected final void aimSpear(double targetX, double targetZ, double targetEyeY) {
        faceToward(targetX, targetZ);
        double lx = targetX - x;
        double ly = targetEyeY - (y + eyeHeight());
        double lz = targetZ - z;
        double length = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (length > 1.0E-4) {
            spearLookX = lx / length;
            spearLookY = ly / length;
            spearLookZ = lz / length;
        }
    }

    /**
     * [SPEAR-MOB] 피글린 두뇌의 쓰기 상태를 몹 쪽 창 쓰기({@link #spearUseState})로 옮긴다. 두 권위의 돌진 판정
     * ({@code MobSystem.resolveMobSpearKinetics})은 이 한 자리만 읽는다.
     */
    protected final void syncSpearUse(boolean wasUsing) {
        SpearUseAi.BrainState brain = spearBrainForSync();
        boolean using = brain != null && brain.using();
        if (using) {
            if (spearUse == null) spearUse = new SpearUseAi.State();
            spearUse.useStartMcTick = brain.useStartMcTick();
        } else {
            if (wasUsing) resetSpearTracking();
            spearUse = null;
        }
    }

    /** [SPEAR-MOB] 두뇌형(피글린)만 재정의한다. */
    SpearUseAi.BrainState spearBrainForSync() { return null; }

    /** [SPEAR-MOB] goal 멈춤({@code SpearUseGoal.stop}): 상태를 버리고 창을 내린다. */
    public void stopSpearUse() {
        spearUse = null;
    }

    /**
     * [SPEAR-MOB] 창 돌진 goal 한 틱({@code SpearUseGoal.tick}). 표적 눈을 바라보고, 결정대로 표적 · 물러날 자리로 가는
     * 수평 변위를 돌려준다. goal 이 끝나면(done) 상태를 버려 다음 틱에 접근부터 다시 한다.
     */
    protected double[] tickSpearUse(MobWorldView world, MobRandom rng, double targetX, double targetY,
            double targetZ, double targetEyeY, java.util.function.Supplier<double[]> chase) {
        com.gameexpert.engine.SpearRules.Kinetic kinetic = com.gameexpert.engine.SpearRules.kinetic(heldItem());
        if (spearUse == null) spearUse = new SpearUseAi.State();
        aimSpear(targetX, targetZ, targetEyeY);
        boolean wasUsing = spearUse.using();
        SpearUseAi.Decision decision = SpearUseAi.tick(spearUse, kinetic, world.worldTick() * 2L, x, y, z,
                targetX, targetY, targetZ, isMobPassenger(),
                SpearUseAi.chargeSpeedModifier(isMobPassenger() ? vehicleType : null),
                (min, max, yRange, fx, fy, fz) -> SpearUseAi.posAway(rng, world, x, y, z, min, max, yRange,
                        fx, fy, fz));
        if (wasUsing && !spearUse.using()) resetSpearTracking();
        if (spearUse.done()) spearUse = null;
        state = MobState.CHASE;
        if (!decision.move()) return new double[] {0.0, 0.0};
        double[] move;
        if (decision.toX() == targetX && decision.toZ() == targetZ) {
            double[] chaseMove = chase.get();
            move = new double[] {chaseMove[0], chaseMove[1]};
        } else {
            double[] toward = towardHoriz(decision.toX(), decision.toZ(), type.baseSpeed());
            move = new double[] {toward[0], toward[1]};
        }
        move[0] *= decision.speedModifier();
        move[1] *= decision.speedModifier();
        return move;
    }

    /**
     * [SPEAR-MOB] 창 돌진 한 번이 플레이어에게 주는 피해({@code LivingEntity.stabAttack}): 인챈트 보너스({@code modifyDamage})
     * → 나약 → 난이도 배율(창 피해 유형은 {@code when_caused_by_living_non_player} 라 난이도를 받는다).
     */
    public int kineticPlayerDamage(MobWorldView world, float amount) {
        return contactDamage(world, enchantedMeleeDamage(amount, null));
    }

    /** [SPEAR-MOB] 창 돌진 한 번이 몹에게 주는 피해: 기본 + 대상별 인챈트 보너스(난이도 배율 없음). */
    public double kineticMobDamage(float amount, MobType target) {
        return amount + heldWeaponMeleeBonus(target);
    }

    /**
     * [SPEAR-MOB] 좀비 계열의 {@code populateDefaultEquipmentSlots} 가 아직 돌지 않았다(생성자가 세우고 첫 틱이 푼다).
     * 저장 행 복원 · 종 변환은 장비를 그대로 옮기므로 끈다. 저장하지 않는다.
     */
    private boolean spawnWeaponPending;

    /** [SPEAR-MOB] 좀비 계열 생성자: 주손 굴림을 첫 틱으로 미룬다(월드 난이도가 그때 보인다). */
    protected final void markSpawnWeaponPending() { spawnWeaponPending = true; }

    /** [SPEAR-MOB] 첫 틱 주손 굴림이 남았는가(시험용). */
    public final boolean spawnWeaponPending() { return spawnWeaponPending; }

    /**
     * [SPEAR-MOB] 미뤄 둔 {@code Zombie.populateDefaultEquipmentSlots}: 빈손이면 {@code SpearRules.zombieSpawnWeapon}(몹 id
     * 해시 · 월드 난이도)이 준 철 검 · 철 창 · 철 삽을 쥔다. 한 번만 돈다.
     */
    protected final void resolvePendingSpawnWeapon(MobWorldView world) {
        if (!spawnWeaponPending) return;
        spawnWeaponPending = false;
        if (heldItem != PlayerInventory.EMPTY) return;
        short weapon = com.gameexpert.engine.SpearRules.zombieSpawnWeapon(id, world.difficulty());
        if (weapon != PlayerInventory.EMPTY) installGeneratedHeldItem(weapon);
    }

    /** [SPEAR-MOB] 뿌리 탈것 종({@code getRootVehicle}); 탈것이 없으면 null. 런타임이 탑승 연결 때 채운다. */
    MobType vehicleType;

    public void dismountMobVehicle() {
        if (vehicleMobId == 0) return;
        clearMobVehicle();
    }

    void clearMobVehicle() {
        long previousVehicleId = vehicleMobId;
        vehicleMobId = 0;
        notifyVehicleBindingChanged(previousVehicleId);
        vy = 0;
        horizontalVx = 0;
        horizontalVz = 0;
        knockbackVx = 0;
        knockbackVz = 0;
    }

    /** Installs one shared runtime observer; replacing/removing it never changes entity semantics. */
    void installVehicleBindingListener(VehicleBindingListener listener) {
        vehicleBindingListener = listener;
    }

    private void notifyVehicleBindingChanged(long previousVehicleId) {
        if (previousVehicleId == vehicleMobId || vehicleBindingListener == null) return;
        vehicleBindingListener.onVehicleBindingChanged(this, previousVehicleId, vehicleMobId);
    }

    public void dismountBoat(double x, double y, double z) {
        mountedBoatId = 0;
        this.x = x; this.y = y; this.z = z;
        vy = 0; knockbackVx = 0; knockbackVz = 0;
    }

    /** AI의 공격/환경 처리는 유지하되 이동 결과만 보트 좌석으로 되돌린다. */
    void lockToVehicle() {
        if (mountedBoatId == 0 && vehicleMobId == 0 && placedVehicleId == 0) return;
        x = vehicleX; y = vehicleY; z = vehicleZ;
        // 활 표적 조준은 좌석 이동과 독립적이다. 다른 유휴/탑승 yaw 규약은 유지한다.
        if (!(this instanceof Skeleton && (visualFlags() & VISUAL_SKELETON_AIMING) != 0)) {
            yaw = vehicleYaw;
        }
        vy = 0; horizontalVx = 0; horizontalVz = 0;
        knockbackVx = 0; knockbackVz = 0;
    }
    /** 스폰 때 결정되어 수명 동안 유지되는 서버 권위 외형/행동 변종. 없으면 null. */
    public String variant() { return null; }
    /** 눈/발사 높이(발 기준). 바닐라가 별도 지정한 동물은 종별 값을 사용한다. */
    public double eyeHeight() {
        return switch (type) {
            case COW -> 1.30 * growthScale();
            case PIG -> 0.86875 * growthScale();
            case SHEEP -> 1.235 * growthScale();
            case CHICKEN -> 0.644 * growthScale();
            case RABBIT -> 0.425 * growthScale();
            case ARMADILLO -> 0.26 * growthScale();
            case DOLPHIN -> 0.3 * growthScale();
            case CAMEL -> CamelRules.EYE_HEIGHT * growthScale();
            case DONKEY -> 1.425 * growthScale();
            case HORSE, MULE -> 1.52 * growthScale();
            case LLAMA -> 1.7765 * growthScale();
            case FOX -> isBaby() ? 0.2975 : 0.4;
            case OCELOT -> 0.595 * growthScale();
            case FROG -> 0.425 * growthScale();
            case GOAT -> 1.105 * growthScale();
            case MOOSHROOM -> 1.3 * growthScale();
            case PANDA -> 1.0625 * growthScale();
            case PARROT -> 0.54 * growthScale();
            case POLAR_BEAR -> 1.19 * growthScale();
            case PUFFERFISH -> 0.455 * growthScale();
            case TURTLE -> 0.34 * growthScale();
            case WOLF -> 0.68 * growthScale();
            case TADPOLE -> 0.195 * growthScale();
            case COPPER_GOLEM -> 0.8125;
            case NAUTILUS, ZOMBIE_NAUTILUS -> 0.2751 * growthScale();
            case BROWN_BEAR -> 1.14;
            case GRIZZLY_BEAR -> 1.65;
            // 목이 꺾여 머리가 처진 자세라 갈색곰(1.14)보다 눈이 낮다.
            case ZOMBIE_BEAR -> ZombieBearRules.EYE_HEIGHT;
            // 살아 있는 말과 같은 골격이라 눈높이도 같다(1.52).
            case ZOMBIE_HORSE -> ZombieHorseRules.EYE_HEIGHT * growthScale();
            // 늑대(0.68)보다 낮다 — 좀비곰과 같은 이유로 머리가 처진 자세다.
            case ZOMBIE_WOLF -> ZombieWolfRules.EYE_HEIGHT;
            // [ZOMBIE-ANIMAL] 좀비화 6종은 살아 있는 원본과 같은 골격이라 눈높이도 같다.
            // 창작 3종·낙타 husk 는 각자 *Rules 가 소유한다(낙타 husk 는 바닐라 낙타와 같다).
            case ZOMBIE_COW -> ZombieCowRules.EYE_HEIGHT;
            case ZOMBIE_PIG -> ZombiePigRules.EYE_HEIGHT;
            case ZOMBIE_SHEEP -> ZombieSheepRules.EYE_HEIGHT;
            case ZOMBIE_GOAT -> ZombieGoatRules.EYE_HEIGHT;
            case ZOMBIE_FOX -> ZombieFoxRules.EYE_HEIGHT;
            case ZOMBIE_CHICKEN -> ZombieChickenRules.EYE_HEIGHT;
            case CARRION_STAG -> CarrionStagRules.EYE_HEIGHT;
            case CARRION_BOAR -> CarrionBoarRules.EYE_HEIGHT;
            case CARRION_CROW -> CarrionCrowRules.EYE_HEIGHT;
            case CAMEL_HUSK -> CamelHuskRules.EYE_HEIGHT;
            case VILLAGER -> 1.62 * growthScale();
            case COD -> 0.195;
            case SALMON, TROPICAL_FISH -> 0.26;
            case SQUID, GLOW_SQUID -> 0.4;
            case SPIDER -> 0.65;
            case CAVE_SPIDER -> 0.45;
            case BABY_ZOMBIE -> 0.93;
            case ZOMBIE, HUSK, DROWNED, ZOMBIE_VILLAGER -> isBaby() ? 0.93 : 1.74;
            case SKELETON, STRAY, BOGGED -> 1.74;
            // [GUARDIAN] Pinned EntityTypes explicitly sets eyeHeight to half the body height.
            case GUARDIAN, ELDER_GUARDIAN -> height() * 0.5;
            // ── 신종 12종(86~97) ──
            // 바닐라가 EntityType 빌더에서 eyeHeight 를 명시한 종은 그 값을 그대로 쓴다.
            // MC Java 1.21.4 EntityType.CAT `.sized(0.6F, 0.7F).eyeHeight(0.35F)`.
            case CAT -> 0.35 * growthScale();
            // EntityType.WANDERING_TRADER `.sized(0.6F, 1.95F).eyeHeight(1.62F)` — 주민과 같다.
            case WANDERING_TRADER -> 1.62;
            // 라마와 같은 골격이라 눈높이도 같다(1.7765).
            case TRADER_LLAMA -> 1.7765 * growthScale();
            // 말과 같은 골격이라 눈높이도 같다(1.52).
            case SKELETON_HORSE -> 1.52 * growthScale();
            // 허스크 계열 인간형이라 좀비 계열과 같은 1.74 다(ParchedRules 소유).
            case PARCHED -> ParchedRules.EYE_HEIGHT;
            // 아래 여섯은 바닐라 eyeHeight 원문을 확보하지 못했다. 지어내지 않고
            // GUARDIAN 과 같은 자리에서 EntityDimensions 기본값 height×0.85 를 쓴다.
            // 원문을 확보하면 이 줄만 종별로 갈라내면 된다.
            case PHANTOM, SNOW_GOLEM, SNIFFER, BREEZE, HAPPY_GHAST, WARDEN -> height() * 0.85;
            // 슬라임과 같은 상자·기본 눈높이다(SulfurCubeRules divergence 1).
            case SULFUR_CUBE -> SulfurCubeRules.EYE_HEIGHT;
            // [SULFUR] 황린 잠복자. 바닐라 대응이 없어 원문이 없으므로 지어내지 않고
            // EntityDimensions 기본값 height × 0.85 를 쓴다(BrimstoneLurkerRules 소유).
            case BRIMSTONE_LURKER -> BrimstoneLurkerRules.EYE_HEIGHT;
            // [CREAKING] 크리킹. [B] 위키에 눈높이 원문이 없어 같은 자리에서 기본값
            // height × 0.85 를 쓴다(CreakingRules 소유).
            case CREAKING -> CreakingRules.EYE_HEIGHT;
            default -> type == MobType.CREEPER ? height() * 0.85 : height() - 0.3;
        };
    }

    /** AI/물리 틱 뒤 실제 이동량과 결정론적 cadence에서 ambient/step 의미 이벤트를 만든다. */
    List<MobEvent> tickSoundEvents(boolean teleported) {
        List<MobEvent> events = null;
        // 바닐라 Mob.baseTick (1.21.4):
        //   if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
        //       this.resetAmbientSoundTime(); this.playAmbientSound(); }
        //   private void resetAmbientSoundTime() { this.ambientSoundTime = -this.getAmbientSoundInterval(); }
        // 고정 주기가 아니라 마지막 발성 이후 시간이 갈수록 확률이 오르는 램프다.
        // ambientSoundTime 은 MC 틱 단위이므로 서버 틱(100ms = MC 2틱)마다 두 번 굴린다.
        // 한 번 발성하면 -interval 로 되감겨 같은 틱의 두 번째 굴림은 절대 통과하지 않는다.
        if (hasAmbientSound() && !isDead()) {
            for (int mcTick = 0; mcTick < MC_TICKS_PER_SERVER_TICK; mcTick++) {
                if (nextAmbientRoll() < ambientSoundTime++) {
                    ambientSoundTime = -ambientSoundInterval();
                    // [EC-MOBS] Shulker.playAmbientSound: 닫혀 있으면 되감기만 하고 소리는 없다.
                    if (!ambientSoundAudible()) continue;
                    if (events == null) events = new java.util.ArrayList<>(2);
                    events.add(new MobEvent.Sound("ambient"));
                }
            }
        }

        if (!soundPositionReady || teleported) {
            soundPositionReady = true;
            soundX = x;
            soundZ = z;
            soundStepDistance = 0;
            return events == null ? List.of() : events;
        }
        double distance = Math.hypot(x - soundX, z - soundZ);
        soundX = x;
        soundZ = z;
        if (!hasStepSound() || isRidingBoat() || isRidingPlacedVehicle() || "swim".equals(movementMedium())
                || !onGround || distance > 1.5) {
            soundStepDistance = 0;
            return events == null ? List.of() : events;
        }
        soundStepDistance += distance;
        if (soundStepDistance >= STEP_STRIDE) {
            soundStepDistance %= STEP_STRIDE;
            if (events == null) events = new java.util.ArrayList<>(2);
            events.add(new MobEvent.Sound("step"));
        }
        return events == null ? List.of() : events;
    }

    /** [EC-MOBS] 되감긴 ambient 굴림이 실제 소리를 내는가. 셜커만 닫힌 동안 거짓이다. */
    protected boolean ambientSoundAudible() { return true; }

    public boolean hasAmbientSound() {
        return switch (type) {
            case CREEPER, BEE, IRON_GOLEM, COD, SALMON, TROPICAL_FISH -> false;
            default -> true;
        };
    }

    public boolean hasStepSound() {
        return switch (type) {
            case ZOMBIE_PIGMAN, PILLAGER, ENDERMAN, CREEPER, BEE,
                    BAT, SQUID, GLOW_SQUID, COD, SALMON, TROPICAL_FISH -> false;
            default -> true;
        };
    }

    /**
     * 바닐라 {@code getAmbientSoundInterval()} (MC 틱). 1.21.4 기준 실제 값:
     * {@code Mob}=80, {@code Animal}/{@code AbstractGolem}/{@code WaterAnimal}/
     * {@code AgeableWaterCreature}=120, {@code Turtle}=200, {@code AbstractHorse}=400,
     * {@code Ocelot}=900. 주민(AbstractVillager)은 Animal 이 아니라 AgeableMob 이므로 기본 80이다.
     * 바닐라에 대응물이 없는 WebCraft 고유 종은 대응 상위 분류값을 계약으로 따른다.
     */
    public int ambientSoundInterval() {
        return switch (type) {
            case OCELOT -> 900;
            // AbstractHorse. 좀비 말도 바닐라에서 같은 상위형이라 같은 주기다.
            case HORSE, DONKEY, MULE, LLAMA, ZOMBIE_HORSE -> 400;
            case TURTLE -> 200;                        // Turtle
            // Animal
            case COW, PIG, SHEEP, CHICKEN, RABBIT, BEE, FOX, FROG, GOAT, MOOSHROOM,
                    PANDA, PARROT, POLAR_BEAR, WOLF, AXOLOTL, ARMADILLO,
                    BROWN_BEAR -> 120;
            // WaterAnimal / AgeableWaterCreature
            case SQUID, GLOW_SQUID, COD, SALMON, TROPICAL_FISH, PUFFERFISH, DOLPHIN,
                    TADPOLE, NAUTILUS -> 120;
            case IRON_GOLEM, COPPER_GOLEM -> 120;      // AbstractGolem
            default -> 80;                             // Mob
        };
    }

    /**
     * 바닐라 {@code entity.random.nextInt(1000)} 대응. 몹별로 독립적인 결정론 스트림이라
     * 월드 공유 RNG 소비 순서를 바꾸지 않는다. Spring/standalone 이 같은 수열을 낸다.
     */
    private int nextAmbientRoll() {
        return ambientRoll((int) id, type.stableId(), ambientRolls++);
    }

    /** Spring/standalone 이 같은 수열을 내도록 32비트 정수 연산만 쓴다. */
    static int ambientRoll(int mobId, int stableId, int rollIndex) {
        int h = mobId * 0x9E3779B1 ^ stableId * 0x85EBCA77 ^ rollIndex * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        h *= 0x3B4C7A2F;
        h ^= h >>> 16;
        return (int) (Integer.toUnsignedLong(h) % 1000L);
    }

    /** 바닐라 {@code Mob.playHurtSound} 는 hurt 사운드 전에 resetAmbientSoundTime() 을 부른다. */
    private void resetAmbientSoundTime() {
        ambientSoundTime = -ambientSoundInterval();
    }

    /**
     * [MOB-EQUIP] 바닐라 {@code LivingEntity.getDamageAfterMagicAbsorb} 의 인챈트 단계: 방어구 네 칸의
     * {@code damage_protection} EPF 합(상한 20)으로 {@code 1 - epf/25} 를 곱한다. 플레이어
     * ({@code PlayerTickState.damage})와 같은 밀리 정수 경로다. 인챈트 방어구가 없으면 그대로 돌려준다.
     */
    protected final double afterArmorEnchantmentProtection(double amount, String cause) {
        if (amount <= 0.0) return amount;
        boolean any = false;
        for (int i = 0; i < equippedArmor.length && !any; i++) {
            any = equippedArmorEnchantments[i] != 0L || equippedArmorComponents[i] != null;
        }
        if (!any) return amount;
        int epf = com.gameexpert.engine.enchant.EnchantmentRules.damageProtectionEpf(cause,
                equippedWideEnchantments(ArmorSlot.HELMET),
                equippedWideEnchantments(ArmorSlot.CHESTPLATE),
                equippedWideEnchantments(ArmorSlot.LEGGINGS),
                equippedWideEnchantments(ArmorSlot.BOOTS));
        if (epf <= 0) return amount;
        return com.gameexpert.engine.enchant.EnchantmentRules.damageAfterProtectionMilli(
                (int) Math.round(amount * com.gameexpert.engine.enchant.EnchantmentRules.MILLI), epf)
                / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
    }

    /** 피해 적용. HP 0 도달 시 사망 상태로 전이(P6 전투에서 사용). */
    public void damage(double amount) {
        applyDamageAfterCooldown(adjustIncomingDamage(amount), "mob");
    }

    /**
     * 틱 기준 MC 피격 무적. 비교는 방어도 적용 전 원본 피해로 하고,
     * 보호 중 더 큰 피해는 원본 차액에만 방어도를 적용한다. true면 실제 피해 이벤트다.
     */
    public boolean damage(double amount, long tickNo) {
        return damage(amount, tickNo, "mob", 0.0);
    }

    /**
     * [MACE] 무기의 방어 효율 가산치(파괴 인챈트, 음수)를 실은 근접 피해. 바닐라
     * {@code CombatRules.getDamageAfterAbsorb} 가 무기의 {@code armor_effectiveness} 로 방어 효율을
     * 고친다. 0 이면 {@link #damage(double, long)} 와 같다.
     */
    public boolean damage(double amount, long tickNo, double armorEffectivenessDelta) {
        return damage(amount, tickNo, "mob", armorEffectivenessDelta);
    }

    /**
     * [MOB-EQUIP] 피해 원인을 아는 피격. {@code cause} 는 플레이어 피해와 같은 원인 문자열
     * ({@code "mob"}·{@code "arrow"}·{@code "explosion"}·{@code "in_fire"}·{@code "lightning"} …)이며
     * 방어구 인챈트 보호(보호·화염·폭발·발사체·가벼운 착지)가 플레이어와 같은
     * {@link com.gameexpert.engine.enchant.EnchantmentRules#damageProtectionEpf} 경로로 줄인다.
     */
    public boolean damage(double amount, long tickNo, String cause) {
        return damage(amount, tickNo, cause, 0.0);
    }

    /** 피해 원인과 무기의 방어 효율 가산치를 함께 싣는 피격 본체. */
    public boolean damage(double amount, long tickNo, String cause,
            double armorEffectivenessDelta) {
        if (isDead() || amount <= 0.0) return false;
        boolean protectedNow = hurtProtectedAt(tickNo);
        double appliedRaw = amount;
        if (protectedNow) {
            if (amount <= previousHurtDamage + 1e-9) return false;
            appliedRaw = amount - previousHurtDamage;
            previousHurtDamage = amount;
            // lastHurtTick 미갱신: 보호 기간 연장 금지.
        } else {
            lastHurtTick = tickNo;
            previousHurtDamage = amount;
        }
        applyDamageAfterCooldown(adjustIncomingDamage(appliedRaw), cause,
                armorEffectivenessDelta);
        if (!isDead()) onDamageSurvived(tickNo);
        return true;
    }

    /** 실제 피해가 적용된 뒤 생존한 종별 반응 훅. 피해 수식은 이 훅에서 바꾸지 않는다. */
    protected void onDamageSurvived(long tickNo) {
        // 기본 몹은 반응 없음.
    }

    /** 종별 피격 자세가 실제 방어도 계산 전에 바꾸는 원본 피해. 기본 몹은 그대로 통과시킨다. */
    protected double adjustIncomingDamage(double rawAmount) {
        return rawAmount;
    }

    /** 이 틱의 일반 넓백을 무시해야 하는 MC 피격 보호 창 내부인가. */
    public boolean hurtProtectedAt(long tickNo) {
        return lastHurtTick != Long.MIN_VALUE
                && tickNo >= lastHurtTick
                && tickNo - lastHurtTick < CombatRules.HURT_COOLDOWN_TICKS;
    }

    /**
     * [TRIAL-GAP] 벌레 먹음을 지닌 채 실제 피해를 받은 횟수(바닐라 {@code onMobHurt} 호출 수).
     * MobSystem 이 10% 굴림과 좀벌레 소환을 소유하고 여기서는 사실만 센다.
     */
    private int pendingInfestedHurts;

    /** [TRIAL-GAP] 위 횟수를 소비한다. */
    public int consumeInfestedHurts() {
        int hurts = pendingInfestedHurts;
        pendingInfestedHurts = 0;
        return hurts;
    }

    private void recordInfestedHurt() {
        if (statusEffects.has(com.gameexpert.engine.effect.StatusEffect.INFESTED)) {
            pendingInfestedHurts++;
        }
    }

    private void applyDamageAfterCooldown(double rawAmount) {
        applyDamageAfterCooldown(rawAmount, "mob", 0.0);
    }

    private void applyDamageAfterCooldown(double rawAmount, String cause) {
        applyDamageAfterCooldown(rawAmount, cause, 0.0);
    }

    private void applyDamageAfterCooldown(double rawAmount, String cause,
            double armorEffectivenessDelta) {
        if (rawAmount > 0.0) {
            recordInfestedHurt();
            double applied = afterArmorEnchantmentProtection(
                    CombatRules.damageAfterArmor(rawAmount, armor(), toughness(),
                            armorEffectivenessDelta), cause);
            healthPoints = Math.max(0.0, healthPoints - applied);
            // 기존 WS health 필드는 int 이므로 표시값만 올림한다. 실제 판정/누적은 healthPoints 로 수행한다.
            hp = healthPoints <= 1e-9 ? 0 : (int) Math.ceil(healthPoints - 1e-9);
            // 피격음이 나는 순간 ambient 램프를 되감는다(바닐라 Mob.playHurtSound).
            resetAmbientSoundTime();
        }
        if (healthPoints <= 1e-9) state = MobState.DEAD;
    }

    // ── 상태이상(FX A1) ──────────────────────────────────────────
    /**
     * 이 몹의 상태이상 목록. [GLOWING] 목록의 종류(남은 시간 제외)는 mobSpawn/mobUpdate 의
     * {@code effects} 로 복제된다 — 발광 윤곽선을 그리려면 클라가 알아야 하기 때문이다.
     */
    public StatusEffects statusEffects() {
        return statusEffects;
    }

    /** [GLOWING] 바닐라 틱수를 그대로 받는 지속 상태이상 부여(홀수 MC 틱도 손실 없이). */
    public boolean applyStatusEffectMcTicks(StatusEffect effect, int amplifier, int mcTicks) {
        if (isDead()) return false;
        return statusEffects.applyMcTicks(effect, amplifier, mcTicks);
    }

    /** 지속 상태이상 부여. durationTicks 는 10 TPS 서버 틱. */
    public boolean applyStatusEffect(StatusEffect effect, int amplifier, int durationTicks) {
        if (isDead()) return false;
        return statusEffects.apply(effect, amplifier, durationTicks);
    }

    /**
     * 상태이상 1틱 진행. 독은 방어구를 무시하고 HP 1 미만으로는 내리지 않습니다.
     *
     * @return 이번 틱에 독 피해가 들어갔으면 true
     */
    final boolean tickStatusEffects() {
        int poison = statusEffects.tick();
        // [TRIAL-GAP] RegenerationMobEffect.applyEffectTick: 체력이 최대 미만이면 차례마다 1 회복.
        for (int heal = statusEffects.regenerationHeals(); heal > 0 && !isDead(); heal--) {
            heal(1.0);
        }
        if (poison <= 0 || isDead()) return false;
        double allowed = Math.min(poison, Math.max(0.0, healthPoints - 1.0));
        if (allowed <= 0.0) return false;
        // [MOB-EQUIP] 독(바닐라 magic)은 방어도를 건너뛰지만 인챈트 보호는 거친다.
        damageBypassesArmor(afterArmorEnchantmentProtection(allowed, "magic"));
        return true;
    }

    /**
     * 물약 계열 마법 피해. 방어구를 무시하고 {@link #magicResistance()} 만 적용합니다.
     *
     * @return 실제로 체력이 줄었으면 true
     */
    public boolean damageMagic(double amount) {
        if (isDead() || amount <= 0.0) return false;
        double applied = afterArmorEnchantmentProtection(
                amount * (1.0 - magicResistance()), "magic");
        if (applied <= 0.0) return false;
        damageBypassesArmor(applied);
        return true;
    }

    /** 마법(물약) 피해 저항률 0~1. 마녀만 재정의합니다. */
    protected double magicResistance() {
        return 0.0;
    }

    /** 근접 명중이 함께 부여하는 상태이상. 동굴거미만 재정의합니다(난이도별 지속). */
    protected ProjectileEffect meleeEffect(MobWorldView world) {
        return null;
    }

    /** 감속을 반영한 이동 배율. AI 는 towardHoriz/wander 를 통해 자동으로 적용받습니다. */
    protected final double effectSpeedMultiplier() {
        return statusEffects.speedMultiplier();
    }

    /** 상태 효과와 현재 레이드 보정을 모두 반영한 이번 틱 이동 속도. */
    protected final double preparedMovementSpeed(double speed) {
        return speed * effectSpeedMultiplier() * preparedRaidSpeedMultiplier;
    }

    /**
     * 나약·힘을 반영한 근접 피해(0 하한). [POTION-GAP] 힘은 같은 자리의 음수 보정이라
     * 여기서 자동으로 증가 방향이 된다 — 투척 물약은 몹에게도 걸리므로 몹도 강화된다.
     */
    protected final int weakenedDamage(int baseDamage) {
        double penalty = statusEffects.meleeDamagePenalty();
        if (penalty == 0.0) return baseDamage;
        return (int) Math.max(0.0, Math.ceil(baseDamage - penalty));
    }

    void prepareRaidSpeedMultiplier(double multiplier) {
        preparedRaidSpeedMultiplier = Math.max(1.0, multiplier);
    }

    double preparedRaidCooldownMultiplier() {
        return preparedRaidSpeedMultiplier > 1.0 ? 0.90 : 1.0;
    }

    /**
     * 플레이어에게 실제로 들어가는 접촉 피해. 나약(가해 몹 상태이상)을 먼저 빼고 월드 난이도 배율을
     * 적용한다. 바닐라도 난이도 배율이 방어구 계산 앞에 오므로 이벤트 단계에서 곱한다.
     */
    protected final int contactDamage(MobWorldView world, int baseDamage) {
        return world.difficulty().scaleContactDamage(weakenedDamage(baseDamage));
    }

    /** 화염처럼 방어구 수치가 줄이지 않는 환경 피해를 직접 적용한다. */
    protected void damageBypassesArmor(double amount) {
        if (amount <= 0.0 || isDead()) return;
        recordInfestedHurt();
        healthPoints = Math.max(0.0, healthPoints - amount);
        hp = healthPoints <= 1e-9 ? 0 : (int) Math.ceil(healthPoints - 1e-9);
        if (healthPoints <= 1e-9) state = MobState.DEAD;
    }

    enum WaterLifecycleResult {
        NONE(null),
        DROWN_DAMAGE(null),
        CONVERT_TO_ZOMBIE(MobType.ZOMBIE),
        CONVERT_TO_DROWNED(MobType.DROWNED);

        private final MobType conversionType;

        WaterLifecycleResult(MobType conversionType) {
            this.conversionType = conversionType;
        }

        MobType conversionType() {
            return conversionType;
        }
    }

    /**
     * 머리 블록만 샘플링해 일반 익사 또는 좀비 계열 변환을 한 번 진행한다.
     * 호출당 객체를 만들지 않으며 런타임이 반환값을 피해/교체 이벤트로 변환한다.
     */
    WaterLifecycleResult tickWaterLifecycle(MobWorldView world) {
        int headX = (int) Math.floor(x);
        int headY = (int) Math.floor(y + eyeHeight());
        int headZ = (int) Math.floor(z);
        short headBlock = world.getBlock(headX, headY, headZ);
        // Active-union adoption can briefly precede the resident terrain mirror. Unknown is not AIR:
        // freezing one tick preserves the last authoritative breath/conversion cursor until the cell exists.
        if (headBlock < 0) return WaterLifecycleResult.NONE;
        int headId = headBlock & 0xffff;
        boolean headInWater = Fluids.isWaterMedium(headId,
                world.blockState(headX, headY, headZ, headId));

        if (type == MobType.ZOMBIE || type == MobType.BABY_ZOMBIE || type == MobType.HUSK) {
            airSupplyTicks = MAX_AIR_SUPPLY_TICKS;
            if (!headInWater) {
                underwaterConversionTicks = 0;
                return WaterLifecycleResult.NONE;
            }
            underwaterConversionTicks++;
            if (underwaterConversionTicks < WATER_CONVERSION_TICKS) {
                return WaterLifecycleResult.NONE;
            }
            underwaterConversionTicks = 0;
            return type == MobType.HUSK
                    ? WaterLifecycleResult.CONVERT_TO_ZOMBIE
                    : WaterLifecycleResult.CONVERT_TO_DROWNED;
        }

        underwaterConversionTicks = 0;
        if (!canDrown()) {
            if (!aquaticType()) airSupplyTicks = MAX_AIR_SUPPLY_TICKS;
            return WaterLifecycleResult.NONE;
        }
        if (!headInWater) {
            airSupplyTicks = Math.min(MAX_AIR_SUPPLY_TICKS,
                    airSupplyTicks + AIR_RECOVERY_PER_TICK);
            return WaterLifecycleResult.NONE;
        }
        airSupplyTicks--;
        if (airSupplyTicks > -DROWN_DAMAGE_INTERVAL_TICKS) {
            return WaterLifecycleResult.NONE;
        }
        airSupplyTicks = 0;
        damageBypassesArmor(afterArmorEnchantmentProtection(DROWN_DAMAGE_POINTS, "drown"));
        return WaterLifecycleResult.DROWN_DAMAGE;
    }

    private boolean aquaticType() {
        return switch (type.category()) {
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> true;
            default -> false;
        };
    }

    private boolean canDrown() {
        return switch (type) {
            // [PARCHED-FAMILY] 파치드는 스켈레톤 변종이라 계열과 같이 익사하지 않는다
            // (허스크 사본이던 옛 판은 익사했다 — 계열 정정의 일부다).
            case ZOMBIE_PIGMAN, ZOMBIFIED_PIGLIN, ZOMBIE_VILLAGER, DROWNED, SKELETON, IRON_GOLEM, ENDERMAN,
                    STRAY, BOGGED, PARCHED, SQUID, GLOW_SQUID, COD, SALMON, TROPICAL_FISH,
                    PUFFERFISH, TADPOLE, NAUTILUS, ZOMBIE_NAUTILUS, AXOLOTL,
                    POISON_DART_FROG -> false;
            default -> true;
        };
    }

    /** 변환 뒤에도 이름·소유자·장비와 현재 자세는 보존하되 바닐라처럼 체력은 새 종류 최대치로 시작한다. */
    void inheritConversionState(Mob source) {
        // Conversion retains the remaining hit-immunity window and its higher-damage baseline.
        lastHurtTick = source.lastHurtTick;
        previousHurtDamage = source.previousHurtDamage;
        yaw = source.yaw;
        onGround = source.onGround;
        ownerNickname = source.ownerNickname;
        customName = source.customName;
        persistenceRequired = source.persistenceRequired;
        pickedUpEquipment = source.pickedUpEquipment;
        // [TRIAL-GAP] 바닐라 convertTo 는 장비와 칸별 드랍 확률을 그대로 옮긴다.
        trialEquipmentNoDrop = source.trialEquipmentNoDrop;
        // [GLOWING] 바닐라 ConversionType.SINGLE 은 활성 효과를 새 몹에 그대로 복사한다.
        statusEffects().restorePersistence(source.statusEffects().persistenceSnapshot());
        spawnWeaponPending = false;
        heldItem = source.heldItem;
        heldItemDurability = source.heldItemDurability;
        System.arraycopy(source.equippedArmor, 0, equippedArmor, 0, equippedArmor.length);
        System.arraycopy(source.equippedArmorDurability, 0,
                equippedArmorDurability, 0, equippedArmorDurability.length);
        // [MOB-EQUIP] 바닐라 convertTo 는 스택(성분 포함)과 칸별 드랍 확률을 그대로 옮긴다. 고유 활의
        // 성분은 새 종류가 같은 고유 무기를 쥘 때만 남는다.
        boolean heldCarrierKept = source.heldItem != PlayerInventory.EMPTY
                || source.nativeRangedWeapon() == nativeRangedWeapon();
        heldItemEnchantments = heldCarrierKept ? source.heldItemEnchantments : 0L;
        heldItemComponents = heldCarrierKept ? source.heldItemComponents : null;
        System.arraycopy(source.equippedArmorEnchantments, 0,
                equippedArmorEnchantments, 0, equippedArmorEnchantments.length);
        System.arraycopy(source.equippedArmorComponents, 0,
                equippedArmorComponents, 0, equippedArmorComponents.length);
        guaranteedDropSlots = source.guaranteedDropSlots;
        noDropSlots = source.noDropSlots;
        babyForm = source.isBaby();
    }

    /** MobRuntime만 실제 외부 행동 이벤트를 본 뒤 호출한다. */
    void markVisualAction(String kind, int ticks) {
        actionKind = kind;
        actionPhase = "active";
        actionTicksRemaining = Math.max(1, ticks);
        actionSequence++;
    }

    void beginSemanticAction(String kind, int anticipationTicks) {
        actionKind = kind;
        actionPhase = "anticipation";
        actionTicksRemaining = Math.max(1, anticipationTicks);
        actionSequence++;
    }

    void cancelSemanticAction(int recoveryTicks) {
        if ("idle".equals(actionPhase) || "recovery".equals(actionPhase)) return;
        actionPhase = "recovery";
        actionTicksRemaining = Math.max(1, recoveryTicks);
        actionSequence++;
    }

    /** Companion authorities derive presentation from their own durable phase checkpoint. */
    void synchronizeVisualAction(String kind, String phase, int ticks) {
        int remaining = Math.max(0, ticks);
        if (!actionKind.equals(kind) || !actionPhase.equals(phase)) actionSequence++;
        actionKind = kind;
        actionPhase = phase;
        actionTicksRemaining = remaining;
    }

    /** MobSystem calls this only after a direct social attack has been accepted. */
    public void commitAcceptedMeleeAttack() {
        markVisualAction("melee", 1);
    }

    void tickVisualAction() {
        // The ghoul authority advances its attack windup only while reach and LOS remain valid.
        if ((type == MobType.GHOUL || type == MobType.BABY_GHOUL)
                && "melee".equals(actionKind) && "anticipation".equals(actionPhase)) return;
        if (actionTicksRemaining <= 0) return;
        actionTicksRemaining--;
        if (actionTicksRemaining != 0) return;
        if (!isPhasedSemanticAction(actionKind)) {
            actionKind = "none";
            actionPhase = "idle";
            return;
        }
        if ("anticipation".equals(actionPhase)) {
            actionPhase = "active";
            actionTicksRemaining = semanticActionDuration(actionKind, false);
            actionSequence++;
        } else if ("active".equals(actionPhase)) {
            actionPhase = "recovery";
            actionTicksRemaining = semanticActionDuration(actionKind, true);
            actionSequence++;
        } else {
            actionKind = "none";
            actionPhase = "idle";
            actionSequence++;
        }
    }

    private static boolean isPhasedSemanticAction(String kind) {
        return switch (kind) {
            case "cast_fangs", "summon_vex", "charge", "breach", "command",
                    "trap", "demolish", "build", "illusion" -> true;
            default -> false;
        };
    }

    private static int semanticActionDuration(String kind, boolean recovery) {
        if (recovery) return "charge".equals(kind) || "breach".equals(kind) ? 12 : 20;
        return switch (kind) {
            case "cast_fangs", "summon_vex", "charge", "breach", "trap" -> 8;
            case "demolish" -> 20;
            case "build" -> 12;
            default -> 1;
        };
    }

    /** 기본 넓백 후 sprint/KB +1을 별도 2차 호출로 합성한다. 인수 단위는 블록/10TPS 틱. */
    public void applyKnockback(double baseX, double baseZ, boolean grounded,
                               double bonusX, double bonusZ) {
        applyHorizontalImpulse(baseX, baseZ, 0.5);
        if (grounded) {
            double vertical = CombatRules.KNOCKBACK_VERTICAL_BPS / 10.0;
            vy = Math.min(vertical, vy / 2.0 + vertical);
        }
        if (Math.abs(bonusX) > 1e-12 || Math.abs(bonusZ) > 1e-12) {
            knockbackVx = knockbackVx / 2.0 + bonusX;
            knockbackVz = knockbackVz / 2.0 + bonusZ;
        }
        knockbackOverridesAi = true;
    }

    /**
     * 낚싯줄에 걸린 채 회수돼 캐스터 쪽으로 끌려간다(바닐라 {@code FishingHook.pullEntity}).
     * 바닐라가 deltaMovement 에 그대로 <b>더하는</b> 임펄스라 기존 속도를 반감하지 않는다.
     * 인수 단위는 블록/10TPS 틱이다.
     */
    public void applyFishingPull(double x, double y, double z) {
        applyHorizontalImpulse(x, z, 1.0);
        vy += y;
    }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code Entity.push(x, y, z)}: deltaMovement 에 그대로 더하는 임펄스
     * (밀어내기 화살). 인수 단위는 블록/10TPS 틱이며 낚싯줄 당김과 같은 합성 규약이다.
     */
    public void applyPush(double x, double y, double z) {
        applyFishingPull(x, y, z);
    }

    /**
     * [SULFUR-KB] 바닐라 {@code setDeltaMovement(old + d)} — 기존 속도를 반감하지 않고 더한다(유황 큐브
     * 넉백). 같은 틱에 앞선 넉백이 있으면 그 결과 위에 더한다. 인수 단위는 블록/10TPS 틱이다.
     */
    public void applyAdditiveKnockback(double dvx, double dvy, double dvz) {
        applyHorizontalImpulse(dvx, dvz, 1.0);
        vy += dvy;
    }

    /** 폭발 영향도를 10 TPS 변위 속도로 변환한 3축 충격량을 그대로 적용한다. */
    public void applyExplosionKnockback(double x, double y, double z) {
        applyHorizontalImpulse(x, z, 0.5);
        vy = vy / 2.0 + y;
    }

    /** 같은 물리 틱의 앞선 충격까지 포함한 현재 속도에 합성한다. */
    private void applyHorizontalImpulse(double x, double z, double retainedVelocity) {
        double currentX = knockbackOverridesAi ? knockbackVx : horizontalVx;
        double currentZ = knockbackOverridesAi ? knockbackVz : horizontalVz;
        knockbackVx = currentX * retainedVelocity + x;
        knockbackVz = currentZ * retainedVelocity + z;
        knockbackOverridesAi = true;
    }

    boolean consumeKnockbackOverride() {
        boolean value = knockbackOverridesAi;
        knockbackOverridesAi = false;
        return value;
    }

    public boolean isDead() { return healthPoints <= 1e-9; }

    /** 피해량 계산 없이 즉시 사망(자폭처럼 공격 피해가 아닌 제거 사유). */
    protected void kill() {
        healthPoints = 0.0;
        hp = 0;
        state = MobState.DEAD;
    }

    /**
     * [GUARDIAN] 근접 가해자에게 그 자리에서 되돌리는 가시 반사 피해. 기본 몹은 0(반사 없음)이며,
     * 가디언만 가시가 세워진 동안 2를 낸다(바닐라 {@code Guardian.hurtServer} 의 thorns 분기).
     */
    public int thornsDamage() { return 0; }

    /** 비치명적 피격 반응 훅. 기본 몹은 반응하지 않고 종별 AI가 필요한 동작만 재정의한다. */
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        // 적대 몹의 추적 AI는 tick에서 결정한다.
    }

    /** 번식 AI가 찾은 짝을 종별 이동 구현에 전달한다. 비번식 몹은 무시한다. */
    void seekBreedingPartner(Mob partner) {
        // 기본 몹은 번식 이동 없음.
    }

    /** 같은 무리 물고기의 저비용 리더 추적 훅. 다른 종은 무시한다. */
    void seekSchoolLeader(Mob leader) {
        // 기본 몹은 무리 이동 없음.
    }

    protected final void forceTarget(String nickname) {
        targetNickname = nickname;
        targetLosCooldown = 0;
        unseenTargetTicks = 0;
    }

    /** Runtime-only social target handoff; the reference is rebuilt every active tick. */
    void prepareSocialTarget(Mob target) {
        preparedSocialTarget = target;
    }

    Mob preparedSocialTarget() {
        return preparedSocialTarget;
    }

    void clearPreparedSocialTarget() {
        preparedSocialTarget = null;
    }

    /** 한 틱 처리. 이동은 상태 변이, 외부 영향은 이벤트로 반환. */
    public abstract List<MobEvent> tick(MobWorldView world, MobRandom rng);

    /**
     * Java FloatGoal의 두 game-tick을 10TPS authority tick 하나로 준비한다. 종별 AI가
     * 매번 이 판정을 복제하지 않도록 runtime이 공통으로 호출하고, 실제 수직 이동은
     * {@link MobPhysics#tickMove}가 소비한다.
     */
    void prepareFloatGoal(MobWorldView world, MobRandom rng) {
        preparedFloatGoalMask = -1;
        if (isDead() || isRidingBoat() || isRidingPlacedVehicle() || !supportsFloatGoal()) return;
        boolean liquidReady = this instanceof SulfurCube cube
                ? cube.submergedPastFluidJumpThreshold(world)
                : bodyTouchesWater(world) || bodyTouchesLava(world);
        if (!liquidReady) return;

        preparedFloatGoalMask = 0;
        for (int gameTick = 0; gameTick < FLOAT_GOAL_DRAWS_PER_TICK; gameTick++) {
            if (rng.nextFloat() < FLOAT_GOAL_CHANCE) preparedFloatGoalMask |= 1 << gameTick;
        }
    }

    /** Rebuilds transient odor memory from persisted blocks on a stable per-entity cadence. */
    void prepareRafflesiaOdor(MobWorldView world) {
        rafflesiaOdorResponse = RafflesiaRules.odorResponse(type);
        if (rafflesiaOdorResponse == RafflesiaRules.OdorResponse.NONE) {
            rafflesiaOdorPresent = false;
            return;
        }
        boolean rescan = !rafflesiaOdorInitialized || Math.floorMod(world.worldTick() + id,
                RafflesiaRules.ODOR_RESCAN_TICKS) == 0;
        if (!rescan && (!rafflesiaOdorPresent
                || world.getBlock(rafflesiaOdorX, rafflesiaOdorY, rafflesiaOdorZ)
                        == Blocks.RAFFLESIA)) return;
        long nearest = world.nearestRafflesia((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.floor(z), RafflesiaRules.ODOR_HORIZONTAL_RADIUS,
                RafflesiaRules.ODOR_VERTICAL_RADIUS);
        rafflesiaOdorInitialized = true;
        rafflesiaOdorPresent = nearest != RafflesiaRules.NO_POSITION;
        if (rafflesiaOdorPresent) {
            rafflesiaOdorX = RafflesiaRules.unpackX(nearest);
            rafflesiaOdorY = RafflesiaRules.unpackY(nearest);
            rafflesiaOdorZ = RafflesiaRules.unpackZ(nearest);
            if (world.getBlock(rafflesiaOdorX, rafflesiaOdorY, rafflesiaOdorZ)
                    != Blocks.RAFFLESIA) rafflesiaOdorPresent = false;
        }
    }

    private boolean supportsFloatGoal() {
        // Zombie's vanilla goal set does not float; sinking permits continuous eye immersion.
        if (type == MobType.ZOMBIE || type == MobType.BABY_ZOMBIE || type == MobType.HUSK
                || type == MobType.DROWNED || type == MobType.BEE || type == MobType.BAT
                || "fly".equals(movementMedium())) return false;
        return switch (type.category()) {
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> false;
            default -> true;
        };
    }

    /** -1은 일반 중력, 0 이상은 두 20TPS 논리 틱의 순서 있는 성공 비트다. */
    int consumeFloatGoalMask() {
        int mask = preparedFloatGoalMask;
        preparedFloatGoalMask = -1;
        return mask;
    }

    /** AI가 조기 반환해 물리를 호출하지 않은 틱의 준비 상태를 다음 틱으로 넘기지 않는다. */
    void clearPreparedFloatGoal() {
        preparedFloatGoalMask = -1;
    }

    // ── 공통 헬퍼 ───────────────────────────────────────────────
    /** maxDist 이내 가장 가까운 생존 플레이어(3D). 없으면 null. */
    protected PlayerSnapshot nearestAlive(MobWorldView world, double maxDist) {
        PlayerSnapshot best = null;
        double bd = maxDist * maxDist;
        for (PlayerSnapshot p : world.players()) {
            if (!p.alive()) continue;
            double d = sq(p.x() - x) + sq(p.y() - y) + sq(p.z() - z);
            if (d <= bd) { bd = d; best = p; }
        }
        return best;
    }

    /** Resolves one already-authoritative target identity without acquiring a replacement. */
    protected PlayerSnapshot lockedTarget(MobWorldView world, String nickname,
            double maxDist, boolean requireLineOfSight) {
        if (nickname == null) return null;
        double maxDistanceSquared = maxDist * maxDist;
        for (PlayerSnapshot player : world.players()) {
            if (!nickname.equals(player.nickname()) || !player.alive()
                    || !mayTargetPlayer(player)) continue;
            double distanceSquared = sq(player.x() - x) + sq(player.y() - y)
                    + sq(player.z() - z);
            if (distanceSquared > maxDistanceSquared
                    || requireLineOfSight && !canSee(world, player)) return null;
            return player;
        }
        return null;
    }

    /**
     * 최초 획득에는 LOS를 요구하고, 획득 뒤 LOS를 잃으면 3초간 같은 타겟을 유지한다.
     * 사거리 비교를 먼저 하므로 광선은 범위 안 후보에만 발사한다.
     */
    protected PlayerSnapshot trackedTarget(MobWorldView world, double maxDist, boolean mayAcquire) {
        PlayerSnapshot current = targetByNickname(world, maxDist);
        if (current != null) {
            if (targetLosCooldown <= 0) {
                boolean visible = canSee(world, current);
                targetLosCooldown = TARGET_LOS_INTERVAL_TICKS - 1;
                targetVisible = visible;
                if (visible) {
                    unseenTargetTicks = 0;
                    rememberTarget(current);
                } else {
                    unseenTargetTicks += TARGET_LOS_INTERVAL_TICKS;
                }
            } else {
                targetLosCooldown--;
            }
            if (unseenTargetTicks < TARGET_UNSEEN_MEMORY_TICKS) {
                return targetVisible ? current : new PlayerSnapshot(current.nickname(),
                        lastSeenTargetX, lastSeenTargetY, lastSeenTargetZ, true);
            }
            clearTarget();
        } else if (targetNickname != null) {
            clearTarget();
        }

        if (!mayAcquire) return null;
        // 벽 뒤 플레이어만 있는 경우에도 매 틱 모든 몹이 광선을 쏘지 않도록 획득 평가를 간격 제한한다.
        if (targetNickname == null && targetLosCooldown > 0) {
            targetLosCooldown--;
            return null;
        }
        PlayerSnapshot best = null;
        double bestSq = maxDist * maxDist;
        for (PlayerSnapshot p : world.players()) {
            if (!p.alive() || !mayTargetPlayer(p) || !canTargetPlayer(world, p)) continue;
            double d = sq(p.x() - x) + sq(p.y() - y) + sq(p.z() - z);
            if (d <= bestSq && canSee(world, p)) {
                bestSq = d;
                best = p;
            }
        }
        if (best != null) {
            targetNickname = best.nickname();
            targetLosCooldown = TARGET_LOS_INTERVAL_TICKS - 1;
            unseenTargetTicks = 0;
            targetVisible = true;
            rememberTarget(best);
        } else {
            targetLosCooldown = TARGET_LOS_INTERVAL_TICKS - 1;
        }
        return best;
    }

    /** Species-specific player eligibility without duplicating target memory and LOS rules. */
    protected boolean canTargetPlayer(PlayerSnapshot player) { return true; }

    /** Live world-dependent eligibility; default keeps other species' existing selection unchanged. */
    protected boolean canTargetPlayer(MobWorldView world, PlayerSnapshot player) { return true; }

    /**
     * [ROTTEN-LEATHER] 타게팅 자격의 **유일한 관문**. 종별 자격({@link #canTargetPlayer})에
     * 언데드 무적대 판정을 겹쳐 세운다. 획득·유지·재확인 세 경로가 모두 이쪽만 부르므로
     * 종별 구현이 무적대 판정을 각자 복제할 필요가 없다.
     */
    final boolean mayTargetPlayer(PlayerSnapshot player) {
        return canTargetPlayer(player)
                && !UndeadNeutralityRules.undeadNeutralTo(type, undeadGrudgeTarget, player);
    }

    /**
     * [ROTTEN-LEATHER] 이 개체가 기억하는 선공자. 개체별이라 옆의 같은 종에는 옮지 않는다
     * (좀비 피그맨처럼 무리 연쇄 계약이 있는 종만 그 경로가 무리 전체에 원한을 심는다).
     * 저장하지 않으므로 재접속하면 사라진다 — 계약과 근거는 {@link UndeadNeutralityRules}.
     */
    private String undeadGrudgeTarget;

    /** 플레이어에게 맞은 사실을 기록하고 종별 피격 반응으로 넘긴다. 선공 기억의 단일 진입점이다. */
    public final void hurtByPlayer(String attackerNickname, double attackerX, double attackerZ) {
        rememberUndeadGrudge(attackerNickname);
        onHurt(attackerNickname, attackerX, attackerZ);
    }

    /** 실제로 적용된 플레이어/길들인 늑대 피해만 100 MC틱 처치 크레딧을 연다. */
    public final void rememberPlayerKillCredit(String nickname) {
        if (nickname == null) return;
        lastPlayerHurtNickname = nickname;
        playerKillCreditTicksRemaining = PLAYER_KILL_CREDIT_TICKS;
    }

    /** 만료되지 않은 처치 크레딧의 플레이어 이름. 없거나 정확히 100 MC틱이면 null. */
    public final String recentPlayerKillCredit() {
        return playerKillCreditTicksRemaining > 0 ? lastPlayerHurtNickname : null;
    }

    /** Vanilla decrements lastHurtByPlayerTime only while this entity actually ticks. */
    public final void finishActiveTickPlayerKillCredit() {
        if (playerKillCreditTicksRemaining <= 0) return;
        if (--playerKillCreditTicksRemaining == 0) lastPlayerHurtNickname = null;
    }

    /** 무리 연쇄처럼 직접 맞지 않은 개체에도 원한을 심는 경로가 쓰는 훅. */
    public final void rememberUndeadGrudge(String attackerNickname) {
        if (attackerNickname != null) undeadGrudgeTarget = attackerNickname;
    }

    /** 테스트·진단용 읽기. 원한이 없으면 {@code null}. */
    public final String undeadGrudgeTarget() { return undeadGrudgeTarget; }

    /** 공격/발사 순간의 현재 LOS. 기억 중인 벽 너머 타겟을 실제로 때리지는 못하게 한다. */
    protected boolean canSee(MobWorldView world, PlayerSnapshot p) {
        return world.hasLineOfSight(x, y + eyeHeight(), z, p.x(),
                p.y() + PlayerInteractionRules.eyeHeight(p.crouching()), p.z());
    }

    /** 기억 스냅샷이 아니라 현재 실제 플레이어 위치까지 공격 LOS가 열렸는지 확인한다. */
    protected boolean canSeeTargetNow(MobWorldView world, PlayerSnapshot target) {
        for (PlayerSnapshot p : world.players()) {
            if (p.alive() && p.nickname().equals(target.nickname())) return canSee(world, p);
        }
        return false;
    }

    private void rememberTarget(PlayerSnapshot p) {
        lastSeenTargetX = p.x();
        lastSeenTargetY = p.y();
        lastSeenTargetZ = p.z();
    }

    private PlayerSnapshot targetByNickname(MobWorldView world, double maxDist) {
        if (targetNickname == null) return null;
        double maxSq = maxDist * maxDist;
        for (PlayerSnapshot p : world.players()) {
            if (!p.alive() || !targetNickname.equals(p.nickname()) || !mayTargetPlayer(p)
                    || !canTargetPlayer(world, p)) continue;
            double d = sq(p.x() - x) + sq(p.y() - y) + sq(p.z() - z);
            return d <= maxSq ? p : null;
        }
        return null;
    }

    private void clearTarget() {
        targetNickname = null;
        targetLosCooldown = 0;
        unseenTargetTicks = 0;
        targetVisible = false;
    }

    /** 종별 목표가 바닐라 조건으로 현재 추적을 끊을 때 쓰는 공통 상태 경계. */
    protected final void clearTrackedTarget() { clearTarget(); }

    /** 종별 목표가 불필요한 RNG를 소비하지 않고 현재 추적 여부를 확인한다. */
    protected final boolean hasTrackedTarget() { return targetNickname != null; }

    /** Current player-target priority marker; social targeting must not override it. */
    public final boolean hasPlayerTarget() { return targetNickname != null; }

    /** [MOB-LOOK] The tracked player target's nickname (null when none) for the replicated head look. */
    public final String trackedTargetNickname() { return targetNickname; }

    /**
     * 외부 발화원(낙뢰)이 붙이는 화상. 바닐라 {@code Entity#thunderHit} 은 이미 타고 있지 않을 때만
     * {@code igniteForSeconds(8)} 하므로, 더 오래 남은 화상을 줄이지 않는 max 로 옮긴다.
     */
    public final void igniteForTicks(int ticks) {
        if (ticks <= 0) return;
        fireTicks = Math.max(fireTicks, burningTicksAfterFireProtection(ticks));
    }

    /**
     * [MOB-EQUIP] 바닐라 {@code LivingEntity.igniteForTicks}: {@code ceil(ticks × burning_time)}, 화염으로부터
     * 보호가 부위마다 {@code -0.15·level}. 플레이어({@code EnvironmentSystem.burningTicks})와 같은 식이다.
     */
    private int burningTicksAfterFireProtection(int ticks) {
        boolean any = false;
        for (int i = 0; i < equippedArmor.length && !any; i++) {
            any = equippedArmorEnchantments[i] != 0L || equippedArmorComponents[i] != null;
        }
        if (!any) return ticks;
        return com.gameexpert.engine.enchant.EnchantmentRules.burningTicksAfterFireProtection(ticks,
                equippedWideEnchantments(ArmorSlot.HELMET),
                equippedWideEnchantments(ArmorSlot.CHESTPLATE),
                equippedWideEnchantments(ArmorSlot.LEGGINGS),
                equippedWideEnchantments(ArmorSlot.BOOTS));
    }

    /** 남은 화상 틱(10 TPS). [ENCHANT-WIDE] 화염 화살의 거절된 명중 복원용. */
    public final int remainingFireTicks() { return fireTicks; }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code AbstractArrow.onHitEntity} 는 피해가 거절되면
     * {@code setRemainingFireTicks(k)} 로 명중 전 화상 값을 되돌린다.
     */
    public final void setRemainingFireTicks(int ticks) {
        fireTicks = Math.max(0, ticks);
    }

    /**
     * 화염 접촉 면역인가. 바닐라 {@code Entity#fireImmune()} 과 같은 자리이며 기본은 false 다.
     * 유황 큐브({@link SulfurCubeRules#FIRE_IMMUNE})만 true 로 갈린다 — 유황 동굴 자체가 불과
     * 용암이 흔한 지형이라 면역이 없으면 스폰 직후 전멸하기 때문이다. 정적판 사본은
     * {@code StandaloneMobRules.ts} 의 {@code standaloneMobFireImmune} 이다.
     */
    public boolean fireImmune() { return false; }

    /** 모든 몹의 용암 화상과 언데드의 햇빛 화상을 한 번만 진행한다. */
    final void tickFireEnvironment(MobWorldView world, MobRandom rng) {
        if (fireImmune()) {
            fireTicks = 0;
            fireDamageTicks = 0;
            return;
        }
        boolean burnsInSun = type == MobType.ZOMBIE || type == MobType.BABY_ZOMBIE
                || type == MobType.ZOMBIE_VILLAGER || type == MobType.SKELETON
                || type == MobType.STRAY || type == MobType.BOGGED
                || type == MobType.DROWNED;
        int bx = (int) Math.floor(x);
        int feetY = (int) Math.floor(y + 0.01);
        int headY = (int) Math.floor(y + eyeHeight());
        int bz = (int) Math.floor(z);
        // 물은 햇빛 발화 후보나 이미 타는 몹에만 영향을 준다. 평범한 가축은 몸 AABB 전체를
        // 매 틱 읽을 이유가 없고, 용암 여부를 위한 발밑 한 칸만 아래에서 확인하면 된다.
        if ((burnsInSun || fireTicks > 0) && bodyTouchesWater(world)) {
            fireTicks = 0;
            fireDamageTicks = 0;
            return;
        }

        if (burnsInSun && world.sunlightLevel(bx, headY, bz) > 11) {
            if (equippedItem(ArmorSlot.HELMET) == Blocks.AIR) {
                fireTicks = burningTicksAfterFireProtection(SUN_FIRE_TICKS);
            } else {
                // Two vanilla game ticks per authority tick. The broken helmet still protects
                // this pass; the next pass can ignite the now-uncovered mob.
                int slot = ArmorSlot.HELMET.ordinal();
                int unbreaking = equippedWideEnchantments(ArmorSlot.HELMET).level(
                        com.gameexpert.engine.enchant.EnchantmentRules.UNBREAKING);
                for (int virtualTick = 0; virtualTick < 2 && equippedArmorDurability[slot] > 0; virtualTick++) {
                    if (rng.nextInt(2) == 0) continue;
                    if (unbreaking > 0 && com.gameexpert.engine.enchant.EnchantmentRules.unbreakingSkipsDurability(
                            unbreaking, true, rng.nextInt(1000), rng.nextInt(unbreaking + 1))) continue;
                    if (--equippedArmorDurability[slot] == 0) {
                        equippedArmor[slot] = 0;
                        equippedArmorEnchantments[slot] = 0L;
                        equippedArmorComponents[slot] = null;
                        guaranteedDropSlots &= ~MobEquipmentRules.armorBit(slot);
                        noDropSlots &= ~MobEquipmentRules.armorBit(slot);
                        pickedUpEquipment = guaranteedDropSlots != 0;
                        trialEquipmentNoDrop = noDropSlots != 0;
                    }
                }
            }
        }
        if (Fluids.isLava(world.getBlock(bx, feetY, bz) & 0xffff)) {
            fireTicks = Math.max(fireTicks, burningTicksAfterFireProtection(SUN_FIRE_TICKS));
        }
        if (fireTicks <= 0) {
            fireDamageTicks = 0;
            return;
        }

        fireTicks--;
        fireDamageTicks++;
        if (fireDamageTicks >= FIRE_DAMAGE_INTERVAL_TICKS) {
            fireDamageTicks = 0;
            // [MOB-EQUIP] 화상(바닐라 on_fire, #is_fire)은 방어도를 건너뛰지만 보호·화염으로부터 보호가 줄인다.
            damageBypassesArmor(afterArmorEnchantmentProtection(1.0, "on_fire"));
        }
    }

    int fireTicksForTest() { return fireTicks; }

    private static boolean isLava(short block) {
        return Fluids.isLava(block & 0xffff);
    }

    protected static final int BODY_WATER_UNKNOWN = -1;
    protected static final int BODY_WATER_DRY = 0;
    protected static final int BODY_WATER_WET = 1;

    /** 물이 하나라도 확인되면 WET, 그렇지 않고 비상주 셀이 있으면 UNKNOWN이다. */
    protected final int bodyWaterPresence(MobWorldView world) {
        boolean unknown = false;
        double half = width() / 2.0;
        int x0 = (int) Math.floor(x - half + 1e-6);
        int x1 = (int) Math.floor(x + half - 1e-6);
        int y0 = (int) Math.floor(y + 1e-6);
        int y1 = (int) Math.floor(y + height() - 1e-6);
        int z0 = (int) Math.floor(z - half + 1e-6);
        int z1 = (int) Math.floor(z + half - 1e-6);
        for (int by = y0; by <= y1; by++) {
            for (int px = x0; px <= x1; px++) {
                for (int pz = z0; pz <= z1; pz++) {
                    short block = world.getBlock(px, by, pz);
                    if (block >= 0 && Fluids.isWaterMedium(block & 0xffff)) {
                        return BODY_WATER_WET;
                    }
                    if (block < 0) unknown = true;
                }
            }
        }
        return unknown ? BODY_WATER_UNKNOWN : BODY_WATER_DRY;
    }

    /** 몸 AABB의 어느 셀이라도 물이면 햇빛 화상을 즉시 끈다(경계에 걸친 몹 포함). */
    protected final boolean bodyTouchesWater(MobWorldView world) {
        return bodyWaterPresence(world) == BODY_WATER_WET;
    }

    /** FloatGoal의 lava 경로를 같은 AABB 범위에서 판정한다. */
    protected final boolean bodyTouchesLava(MobWorldView world) {
        double half = width() / 2.0;
        int x0 = (int) Math.floor(x - half + 1e-6);
        int x1 = (int) Math.floor(x + half - 1e-6);
        int y0 = (int) Math.floor(y + 1e-6);
        int y1 = (int) Math.floor(y + height() - 1e-6);
        int z0 = (int) Math.floor(z - half + 1e-6);
        int z1 = (int) Math.floor(z + half - 1e-6);
        for (int by = y0; by <= y1; by++)
            for (int px = x0; px <= x1; px++)
                for (int pz = z0; pz <= z1; pz++)
                    if (isLava(world.getBlock(px, by, pz))) return true;
        return false;
    }

    protected double dist3d(PlayerSnapshot p) {
        return Math.sqrt(sq(p.x() - x) + sq(p.y() - y) + sq(p.z() - z));
    }

    protected double horizDist(PlayerSnapshot p) {
        return Math.sqrt(sq(p.x() - x) + sq(p.z() - z));
    }

    /** (tx,tz) 를 향한 수평 변위(길이 speed). 거리가 0 이면 {0,0}. */
    protected double[] towardHoriz(double tx, double tz, double speed) {
        double ax = tx - x, az = tz - z;
        double d = Math.sqrt(ax * ax + az * az);
        if (d < 1e-9) {
            moveBuf[0] = 0;
            moveBuf[1] = 0;
            return moveBuf;
        }
        double effective = preparedMovementSpeed(speed);
        moveBuf[0] = ax / d * effective;
        moveBuf[1] = az / d * effective;
        return moveBuf;
    }

    protected void faceToward(double tx, double tz) {
        yaw = Math.atan2(tz - z, tx - x);
    }

    /** 랜덤 목표를 향한 저속 배회 변위. 타이머가 끝나면 새 방향/대기를 뽑는다. */
    // Vanilla 26.3 RandomStrollGoal checks 100 no-action game ticks, including persistent mobs.
    private int randomWanderNoActionMcTicks;

    final void prepareRandomWanderActivity(MobWorldView world) {
        for (PlayerSnapshot player : world.players()) {
            if (player.alive() && sq(player.x() - x) + sq(player.y() - y)
                    + sq(player.z() - z) < 32.0 * 32.0) {
                randomWanderNoActionMcTicks = 0;
                return;
            }
        }
        randomWanderNoActionMcTicks = Math.min(100, randomWanderNoActionMcTicks + 2);
    }

    protected final boolean mayStartRandomWander() { return randomWanderNoActionMcTicks < 100; }

    protected double[] wander(MobRandom rng, double baseSpeed) {
        if (rafflesiaOdorPresent) {
            double targetX = rafflesiaOdorX + 0.5;
            double targetZ = rafflesiaOdorZ + 0.5;
            double dx = targetX - x;
            double dz = targetZ - z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            boolean attracts = rafflesiaOdorResponse == RafflesiaRules.OdorResponse.ATTRACT;
            if (distance > 1e-9 && (!attracts || distance > 1.25)) {
                double direction = rafflesiaOdorResponse == RafflesiaRules.OdorResponse.ATTRACT
                        ? 1.0 : -1.0;
                double speed = preparedMovementSpeed(baseSpeed * 0.65);
                moveBuf[0] = dx / distance * speed * direction;
                moveBuf[1] = dz / distance * speed * direction;
                yaw = Math.atan2(moveBuf[1], moveBuf[0]);
                return moveBuf;
            }
        }
        if (wanderTimer <= 0) {
            if (!mayStartRandomWander()) {
                moveBuf[0] = 0.0;
                moveBuf[1] = 0.0;
                return moveBuf;
            }
            if (rng.nextDouble() < 0.3) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double speed = baseSpeed * 0.5;
                wdx = Math.cos(ang) * speed;
                wdz = Math.sin(ang) * speed;
                yaw = ang;
                wanderTimer = 20 + rng.nextInt(40);
            } else {
                wdx = 0; wdz = 0;
                wanderTimer = 10 + rng.nextInt(20);
            }
        }
        wanderTimer--;
        double multiplier = effectSpeedMultiplier() * preparedRaidSpeedMultiplier;
        moveBuf[0] = wdx * multiplier;
        moveBuf[1] = wdz * multiplier;
        return moveBuf;
    }

    protected static double sq(double v) { return v * v; }
}
