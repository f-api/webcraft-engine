package com.gameexpert.mob.dto;

import com.gameexpert.engine.mob.PoisonDartFrogRules;
import com.gameexpert.engine.mob.MobRuntime;
import lombok.Getter;
import lombok.EqualsAndHashCode;

/** 틱 런타임과 JPA 계층 사이에서 복사하는 몹 상태입니다. */
@Getter
@EqualsAndHashCode
public class MobPersistenceSnapshot {

    public static final int SCHEMA_VERSION = 3;

    private int schemaVersion = SCHEMA_VERSION;

    private final long mobId;
    private final String type;
    private String variant;
    private final double x;
    private final double y;
    private final double z;
    private float yaw;
    private float pitch;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private final double healthPoints;
    private final boolean pickedUpEquipment;
    private final short heldItem;
    private final int heldItemDurability;
    private final short helmetItem;
    private final int helmetDurability;
    private final short chestplateItem;
    private final int chestplateDurability;
    private final short leggingsItem;
    private final int leggingsDurability;
    private final short bootsItem;
    private final int bootsDurability;
    private final short carriedBlock;
    private int sulfurCubePickupCooldownTicks;
    private int sulfurCubeFuseTicks = -1;
    private int sulfurCubeMaxFuseTicks = -1;
    private boolean sulfurCubeFromBucket;
    /** [EC-MOBS] 셜커 부착면 · 아이템 액자 방향(바닐라 Direction 3D 값). 다른 종은 0. */
    private int attachFace;
    /** [EC-MOBS] 셜커 raw peek(0..100) · 아이템 액자 회전(0..7). 다른 종은 0. */
    private int attachState;
    /** [EC-MOBS] 아이템 액자에 넣은 아이템의 인챈트 워드 0. */
    private long heldItemEnchantments;
    /** [EC-MOBS] 아이템 액자에 넣은 아이템의 성분 문자열(확장 인챈트 WCIC4). 없으면 null. */
    private String heldItemComponents;
    private final String ownerNickname;
    private final String customName;
    private final int ageTicksRemaining;
    private final int breedingCooldownTicks;
    private final boolean babyForm;
    private final int airSupplyTicks;
    private final int underwaterConversionTicks;
    private final boolean drownedCarrierRolled;
    private final boolean drownedTridentCarrier;
    private boolean drownedShellCarrier;
    private int poisonDartFrogFeedCooldownMcTicks;
    private int poisonDartFrogDefensiveTicks;
    private int poisonDartFrogContactCooldownTicks;
    private long poisonDartFrogFeedSequence;
    private long poisonDartFrogPendingFeedSequence;
    private int poisonDartFrogPendingFeedX;
    private int poisonDartFrogPendingFeedY;
    private int poisonDartFrogPendingFeedZ;
    /** Natural Drowned jockey one-shot state; legacy rows default to settled/no-roll. */
    private boolean drownedJockeyDecisionArmed;
    private boolean drownedJockeyDecisionSettled = true;
    private boolean drownedJockeyDecisionWinner;
    /** Passenger-to-vehicle mob edge; zero means no mob vehicle. */
    private long vehicleMobId;
    private boolean skeletonHorseTrapActive;
    private int skeletonHorseTrapAgeMcTicks;
    private int copperGolemOxidationAge;
    private boolean copperGolemWaxed;
    private int copperGolemPose;
    private long copperGolemNextWeatheringMcTick = -1L;
    private boolean copperGolemStatuePending;
    private final boolean beeHasStung;
    private final int beeDeathAfterStingTicks;
    private final boolean beeHasNectar;
    private final double beeHomeX;
    private final double beeHomeY;
    private final double beeHomeZ;
    /** Positive while the Bee is hidden inside its persisted nest/hive. */
    private int beeHiveTicks;
    private final boolean persistenceRequired;
    private final int slimeSize;
    private final int loveTicksRemaining;
    private final long vexSummonerMobId;
    private final int vexLimitedLifeTicks;
    private final String angerTargetNickname;
    private final int angerTicks;
    private final int ravagerAttackTicks;
    private final int ravagerStunnedTicks;
    private final int ravagerRoarTicks;
    private final String armadilloShellState;
    private final int armadilloStateMcTicks;
    private final int armadilloDangerMcTicks;
    private final int armadilloScuteTicks;
    private final int dolphinMoistureTicks;
    private final int dolphinDryDamageMcTicks;
    private final boolean dolphinGotFish;
    private final int dolphinTreasureX;
    private final int dolphinTreasureY;
    private final int dolphinTreasureZ;
    private String illagerContext;
    private long illagerContextIdentity;
    private int illagerPolicyVersion;
    private long companionHandlerMobId;
    private long companionAppearanceLane;
    private int companionRebindCount;
    private String companionAbilityPhase = "IDLE";
    private int companionAbilityTicks;
    private int companionCooldownTicks;
    private String companionTargetNickname;
    private int companionMarkTicks;
    private double companionChargeDistance;
    private int companionOrphanTicks;
    private boolean ocelotTrusting;
    private boolean wolfSitting;
    private boolean companionSitting;
    /** Null only in rows written before Cat/Parrot companion state existed. */
    private Integer catCollarColor;
    private int wolfCollarColor = -1;
    private int wolfArmorDurability;
    private int tadpoleAgeMcTicks;
    private boolean turtleTravelingHome;
    private int turtleEggDigMcTicks;
    private int turtleEggCount;
    /** Pufferfish NBT PuffState (0=small, 1=mid, 2=full); transient counters are not persisted. */
    private int pufferPuffStage;
    private String zombieNautilusChargePhase = "IDLE";
    private int zombieNautilusChargeCooldownMcTicks;
    private String zombieNautilusChargeTargetNickname;
    private long zombieNautilusChargeTargetMobId;
    private double zombieNautilusChargeVx;
    private double zombieNautilusChargeVy;
    private double zombieNautilusChargeVz;
    private double zombieNautilusChargeDistance;
    private int zombieNautilusNaturalTargetCooldownMcTicks;
    private int illusionerInvisibilityMcTicks;
    private int illusionerMirrorCooldownMcTicks;
    private int illusionerBlindnessCooldownMcTicks;
    private int illusionerCastMcTicks;
    private String illusionerBlindTargetKey;
    private boolean companionDecisionSettled;
    private boolean companionDecisionWinner;
    /** Owning raid instance for released raiders and their companions; 0 outside a raid. */
    private long raidId;
    private int raidWave;
    /** 좀비 주민 치료 카운트다운(20 TPS 게임 틱). 0 이면 전환 중이 아니다. */
    private int zombieVillagerConversionMcTicks;
    /** 카운트다운을 시작시킨 플레이어 닉네임. 전환 중이 아니면 null 이다. */
    private String zombieVillagerConversionStarter;
    /**
     * 주민의 사회 기억(gossip)과 gossip 타이머를 담은 한 줄. VillagerSocialState 의 코덱이며
     * 저장할 것이 없으면 null 이다.
     */
    private String villagerSocial;
    /** Exact VillagerData carried by supported igloo entities and preserved through curing. */
    private String villagerBiomeType;
    private String villagerProfession;
    private int villagerLevel;
    private boolean finalCarrierBinding;
    // ── [FARM-ANIMAL] 동물 상호작용 3종의 영속 상태. 다른 종은 중립값(-1/0/false)으로 남는다. ──
    /** 양의 MC DyeColor 네트워크 ID(0..15). 양이 아니면 -1 이다. */
    private int sheepColor = -1;
    /** 전단된 양인가. 풀을 먹으면 다시 false 가 된다. */
    private boolean sheepSheared;
    /** 진행 중인 풀 먹기 애니메이션의 남은 MC 틱(0~40). */
    private int sheepEatMcTicks;
    /** 닭의 다음 산란까지 남은 MC 틱(6,000~11,999). 닭이 아니면 0 이다. */
    private int chickenEggMcTicks;
    /** 안장을 얹은 돼지인가. */
    private boolean pigSaddled;
    /** 진행 중인 당근 낚싯대 부스트의 경과·총 MC 틱. 부스트 중이 아니면 둘 다 0 이다. */
    private int pigBoostMcTicks;
    private int pigBoostTotalMcTicks;
    /**
     * 차지드 크리퍼인가. 바닐라 {@code Creeper} 의 {@code DATA_IS_POWERED} 는 NBT {@code powered}
     * 로 저장되는 영속 상태다 — 낙뢰로 켜진 크리퍼는 재시작 뒤에도 폭발력 6을 유지한다.
     * 크리퍼가 아니면 언제나 false 다.
     */
    private boolean creeperPowered;
    /** 라이터로 수동 점화된 크리퍼인가. 바닐라 NBT {@code ignited}. */
    private boolean creeperIgnited;
    /**
     * [TRIAL-GAP] 불길한 트라이얼 스포너의 장비 표(equipment/trial_chamber_*)를 입은 몹인가.
     * 바닐라 {@code slot_drop_chances 0.0} 이라 그 장비는 사망해도 떨어지지 않는다.
     */
    private boolean trialEquipmentNoDrop;
    /**
     * [GLOWING] 몹의 활성 상태이상(남은 MC 틱·독 주기 누적 포함). 바닐라 {@code LivingEntity} 가
     * {@code active_effects} 를 저장하는 것과 같다. 없으면 빈 목록이다.
     */
    private java.util.List<com.gameexpert.engine.effect.StatusEffects.PersistentEffect> statusEffects =
            java.util.List.of();
    /**
     * [MOB-EQUIP] 칸별 장비 성분(인챈트·성분 문자열)과 드랍 계층({@code MobEquipmentComponentsCodec},
     * {@code MEC1;…}). 성분이 없고 드랍 계층이 몹 단위 불리언에서 파생되면 null — 옛 행과 같다.
     */
    private String equipmentComponents;
    /** 갈색 무시룸이 다음 그릇에 담을 작은 꽃 블록 ID. 0이면 저장된 효과가 없다. */
    private int mooshroomStoredFlower;
    // ── [MOUNT] 말 계열 영속 상태. 말이 아니면 전부 중립값(false/0)으로 남는다. ──
    /** 길들인 말인가(바닐라 NBT {@code Tame}). */
    private boolean horseTamed;
    /** 길들이기 진척(0..100, 바닐라 NBT {@code Temper}). */
    private int horseTemper;
    /** 안장을 얹은 말인가(바닐라 {@code SaddleItem}). */
    private boolean horseSaddled;
    /** Exact ordinary-Horse BODY armor item ID; zero means empty. */
    private short horseArmorItem;
    /** Goat horn presence bit mask: bit0=left, bit1=right; null preserves old rows as both present. */
    private Integer goatHornMask;
    /** Durable Goat brain state; null phase is a pre-ram-state row and restores as IDLE/-1. */
    private String goatRamPhase = "IDLE";
    private int goatRamCooldownMcTicks = -1;
    private String goatRamTargetNickname;
    private double goatRamTargetX;
    private double goatRamTargetZ;
    private double goatRamRunUpX;
    private double goatRamRunUpZ;
    private double goatRamDirectionX;
    private double goatRamDirectionZ;
    private int goatRamPrepareMcTicks;
    private double goatRamDistance;
    private long goatRamSequence;
    private int goatRamPendingBlockId;
    private int goatRamPendingX;
    private int goatRamPendingY;
    private int goatRamPendingZ;
    private boolean goatRamPendingPreferLeft;
    /** Durable Sniffer brain state and retryable digging-loot request. */
    private String snifferDigPhase = "IDLE";
    private int snifferDigCooldownMcTicks;
    private int snifferDigPhaseMcTicks;
    private int snifferDigDropDelayMcTicks;
    private int snifferDigTargetX;
    private int snifferDigTargetY;
    private int snifferDigTargetZ;
    private long snifferDigSearchEpoch;
    private long snifferDigSequence;
    private long snifferPendingDropSequence;
    private short snifferPendingDropItem;
    /** Player identity holding this mob's lead; null means unleashed. */
    private String leashHolderNickname;
    /**
     * 개체 스탯(바닐라 {@code Attributes} 기본값). 셋 다 0 인 옛 행은 스탯을 갖지 않았던 행이라
     * 복원 시 생성 롤을 그대로 둔다(열대어 variant 와 같은 마이그레이션 계약).
     */
    private double horseMaxHealth;
    private double horseSpeed;
    private double horseJumpStrength;
    // ── [MOUNT] 상자 화물·라마 힘·카펫. 상자를 달 수 있는 종이 아니면 전부 중립값이다. ──
    /**
     * 상자를 단 개체인가(바닐라 {@code AbstractChestedHorse} NBT {@code ChestedHorse}).
     * 상자는 뗄 수 없으므로 한 번 참이 되면 사망까지 참이다. 이 값이 없던 옛 행은 false 다.
     */
    private boolean horseChested;
    /** HMI1 exact SADDLE/BODY/cargo stack payload; required for horse-family rows. */
    private String horseInventoryData;
    /** Exact shared logical horse-menu generation. */
    private long horseMenuPersistenceRevision;
    /** Exact equipment-local generation; never collapsed into the shared generation. */
    private long horseEquipmentPersistenceRevision;
    /** Exact cargo-local generation. */
    private long cargoPersistenceRevision;
    /**
     * 라마 힘 스탯(1~5, 바닐라 {@code Llama} NBT {@code Strength}). 화물 열 수가 곧 이 값이다.
     * 0 은 "이 트랙 이전에 저장된 행"이라 복원 경계가 결정론 롤을 그대로 둔다(말 스탯 0 과 같은
     * 마이그레이션 계약). 라마가 아니면 언제나 0 이다.
     */
    private int llamaStrength;
    /**
     * 라마 장식 카펫의 MC DyeColor 네트워크 ID(0~15) 또는 장식 없음(-1,
     * {@code LlamaRules.NO_CARPET}). 라마가 아니면 언제나 -1 이다(양 색 -1 과 같은 계약).
     */
    private int llamaCarpetColor = -1;
    // ── [MOUNT] 낙타 영속 상태. 낙타가 아니면 둘 다 false 로 남는다. ──
    /**
     * 안장을 얹은 낙타인가(바닐라 {@code Camel} 의 {@code SaddleItem}). 낙타는 길들이기가
     * 없으므로 안장 한 칸이 곧 조종 조건이며, 이 값이 없던 옛 행은 false 로 읽힌다.
     */
    private boolean camelSaddled;
    /**
     * 앉은 자세의 낙타인가(바닐라 NBT {@code IsSitting}). 대시 쿨다운·유휴 카운터는 만료형
     * 휘발 상태라 저장하지 않는다 — 복원 뒤 둘 다 0 에서 다시 시작한다.
     */
    private boolean camelSitting;
    // ── [HARNESS] 해피 가스트 영속 상태. 해피 가스트가 아니면 false/-1 로 남는다. ──
    /**
     * 하네스를 쓴 해피 가스트인가(바닐라 {@code HappyGhast} 의 장비 슬롯). 낙타의 안장과 같은
     * 자리이며, 이 값이 없던 옛 행은 false 로 읽혀 하네스가 없는 개체가 된다.
     */
    private boolean happyGhastHarnessed;
    /**
     * 쓰고 있는 하네스의 MC {@code DyeColor} 네트워크 ID(0..15). 하네스가 없으면 -1 이며,
     * 이 값이 없던 옛 행도 -1 로 읽힌다(양 색 {@code sheepColor} 와 같은 "없으면 -1" 어휘).
     */
    private int happyGhastHarnessColor = -1;
    // ── [NAUTILUS-MOUNT] 노틸러스 계열 영속 상태. 다른 종은 false/-1 로 남는다. ──
    /**
     * 안장을 얹은 노틸러스인가([B] §8-2 의 안장 슬롯). 낙타의 안장·해피 가스트의 하네스와
     * 같은 자리이며, 이 값이 없던 옛 행은 false 로 읽혀 안장이 없는 개체가 된다.
     */
    private boolean nautilusSaddled;
    /**
     * 입고 있는 노틸러스 갑옷의 <b>티어 서열</b>(0=구리 … 3=다이아몬드). 없으면 -1 이며 이 값이
     * 없던 옛 행도 -1 로 읽힌다(하네스 색과 같은 "없으면 -1" 어휘).
     *
     * <p><b>아이템 ID 가 아니라 서열을 저장한다</b> — 네더라이트 티어가 뒤에 붙어도 이미 저장된
     * 월드가 그대로 읽히게 하기 위해서다.
     */
    private int nautilusArmorTier = -1;
    /** 알레이가 좋아하는 플레이어에게 운반 중인 matching stack. 요청 handshake는 저장하지 않는다. */
    private int allayDeliveryCount;
    private int allayDeliveryDurability;
    private boolean turtleGravid;
    private int turtleHomeX;
    private int turtleHomeY;
    private int turtleHomeZ;

    public MobPersistenceSnapshot(
            long mobId,
            String type,
            String variant,
            double x,
            double y,
            double z,
            double healthPoints,
            boolean pickedUpEquipment,
            int heldItem,
            int heldItemDurability,
            int helmetItem,
            int helmetDurability,
            int chestplateItem,
            int chestplateDurability,
            int leggingsItem,
            int leggingsDurability,
            int bootsItem,
            int bootsDurability,
            int carriedBlock,
            String ownerNickname,
            String customName,
            int ageTicksRemaining,
            int breedingCooldownTicks,
            boolean babyForm,
            int airSupplyTicks,
            int underwaterConversionTicks,
            boolean drownedCarrierRolled,
            boolean drownedTridentCarrier,
            boolean beeHasStung,
            int beeDeathAfterStingTicks,
            boolean beeHasNectar,
            double beeHomeX,
            double beeHomeY,
            double beeHomeZ,
            boolean persistenceRequired,
            int slimeSize,
            int loveTicksRemaining,
            long vexSummonerMobId,
            int vexLimitedLifeTicks,
            String angerTargetNickname,
            int angerTicks,
            int ravagerAttackTicks,
            int ravagerStunnedTicks,
            int ravagerRoarTicks) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                null, 0, 0, 0, 0, 0, false, 0, 0, 0);
    }

    public MobPersistenceSnapshot(
            long mobId,
            String type,
            String variant,
            double x,
            double y,
            double z,
            double healthPoints,
            boolean pickedUpEquipment,
            int heldItem,
            int heldItemDurability,
            int helmetItem,
            int helmetDurability,
            int chestplateItem,
            int chestplateDurability,
            int leggingsItem,
            int leggingsDurability,
            int bootsItem,
            int bootsDurability,
            int carriedBlock,
            String ownerNickname,
            String customName,
            int ageTicksRemaining,
            int breedingCooldownTicks,
            boolean babyForm,
            int airSupplyTicks,
            int underwaterConversionTicks,
            boolean drownedCarrierRolled,
            boolean drownedTridentCarrier,
            boolean beeHasStung,
            int beeDeathAfterStingTicks,
            boolean beeHasNectar,
            double beeHomeX,
            double beeHomeY,
            double beeHomeZ,
            boolean persistenceRequired,
            int slimeSize,
            int loveTicksRemaining,
            long vexSummonerMobId,
            int vexLimitedLifeTicks,
            String angerTargetNickname,
            int angerTicks,
            int ravagerAttackTicks,
            int ravagerStunnedTicks,
            int ravagerRoarTicks,
            String armadilloShellState,
            int armadilloStateMcTicks,
            int armadilloDangerMcTicks,
            int armadilloScuteTicks,
            int dolphinMoistureTicks,
            int dolphinDryDamageMcTicks,
            boolean dolphinGotFish,
            int dolphinTreasureX,
            int dolphinTreasureY,
            int dolphinTreasureZ) {
        // Keep the DTO able to carry a rejected database row. Runtime, entity and publication
        // boundaries validate the identity before it can become live or durable state.
        this.mobId = mobId;
        this.type = type;
        this.variant = variant;
        this.x = x;
        this.y = y;
        this.z = z;
        this.healthPoints = healthPoints;
        this.pickedUpEquipment = pickedUpEquipment;
        this.heldItem = checkedId(heldItem);
        this.heldItemDurability = heldItemDurability;
        this.helmetItem = checkedId(helmetItem);
        this.helmetDurability = helmetDurability;
        this.chestplateItem = checkedId(chestplateItem);
        this.chestplateDurability = chestplateDurability;
        this.leggingsItem = checkedId(leggingsItem);
        this.leggingsDurability = leggingsDurability;
        this.bootsItem = checkedId(bootsItem);
        this.bootsDurability = bootsDurability;
        this.carriedBlock = checkedId(carriedBlock);
        this.ownerNickname = ownerNickname;
        this.customName = customName;
        this.ageTicksRemaining = ageTicksRemaining;
        this.breedingCooldownTicks = breedingCooldownTicks;
        this.babyForm = babyForm;
        this.airSupplyTicks = airSupplyTicks;
        this.underwaterConversionTicks = underwaterConversionTicks;
        this.drownedCarrierRolled = drownedCarrierRolled;
        this.drownedTridentCarrier = drownedTridentCarrier;
        this.beeHasStung = beeHasStung;
        this.beeDeathAfterStingTicks = beeDeathAfterStingTicks;
        this.beeHasNectar = beeHasNectar;
        this.beeHomeX = beeHomeX;
        this.beeHomeY = beeHomeY;
        this.beeHomeZ = beeHomeZ;
        this.persistenceRequired = persistenceRequired;
        this.slimeSize = slimeSize;
        this.loveTicksRemaining = loveTicksRemaining;
        this.vexSummonerMobId = vexSummonerMobId;
        this.vexLimitedLifeTicks = vexLimitedLifeTicks;
        this.angerTargetNickname = angerTargetNickname;
        this.angerTicks = angerTicks;
        this.ravagerAttackTicks = ravagerAttackTicks;
        this.ravagerStunnedTicks = ravagerStunnedTicks;
        this.ravagerRoarTicks = ravagerRoarTicks;
        this.armadilloShellState = armadilloShellState;
        this.armadilloStateMcTicks = armadilloStateMcTicks;
        this.armadilloDangerMcTicks = armadilloDangerMcTicks;
        this.armadilloScuteTicks = armadilloScuteTicks;
        this.dolphinMoistureTicks = dolphinMoistureTicks;
        this.dolphinDryDamageMcTicks = dolphinDryDamageMcTicks;
        this.dolphinGotFish = dolphinGotFish;
        this.dolphinTreasureX = dolphinTreasureX;
        this.dolphinTreasureY = dolphinTreasureY;
        this.dolphinTreasureZ = dolphinTreasureZ;
    }

    public MobPersistenceSnapshot(
            long mobId, String type, String variant,
            double x, double y, double z, double healthPoints,
            boolean pickedUpEquipment,
            int heldItem, int heldItemDurability,
            int helmetItem, int helmetDurability,
            int chestplateItem, int chestplateDurability,
            int leggingsItem, int leggingsDurability,
            int bootsItem, int bootsDurability,
            int carriedBlock, String ownerNickname, String customName,
            int ageTicksRemaining, int breedingCooldownTicks, boolean babyForm,
            int airSupplyTicks, int underwaterConversionTicks,
            boolean drownedCarrierRolled, boolean drownedTridentCarrier,
            boolean beeHasStung, int beeDeathAfterStingTicks, boolean beeHasNectar,
            double beeHomeX, double beeHomeY, double beeHomeZ,
            boolean persistenceRequired, int slimeSize, int loveTicksRemaining,
            long vexSummonerMobId, int vexLimitedLifeTicks,
            String angerTargetNickname, int angerTicks,
            int ravagerAttackTicks, int ravagerStunnedTicks, int ravagerRoarTicks,
            String armadilloShellState, int armadilloStateMcTicks,
            int armadilloDangerMcTicks, int armadilloScuteTicks,
            int dolphinMoistureTicks, int dolphinDryDamageMcTicks,
            boolean dolphinGotFish, int dolphinTreasureX, int dolphinTreasureY,
            int dolphinTreasureZ,
            String illagerContext, long illagerContextIdentity, int illagerPolicyVersion,
            long companionHandlerMobId, long companionAppearanceLane,
            int companionRebindCount) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateMcTicks, armadilloDangerMcTicks,
                armadilloScuteTicks, dolphinMoistureTicks, dolphinDryDamageMcTicks,
                dolphinGotFish, dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ);
        this.illagerContext = illagerContext;
        this.illagerContextIdentity = illagerContextIdentity;
        this.illagerPolicyVersion = illagerPolicyVersion;
        this.companionHandlerMobId = companionHandlerMobId;
        this.companionAppearanceLane = companionAppearanceLane;
        this.companionRebindCount = companionRebindCount;
    }

    public MobPersistenceSnapshot(
            long mobId, String type, String variant,
            double x, double y, double z, double healthPoints,
            boolean pickedUpEquipment,
            int heldItem, int heldItemDurability,
            int helmetItem, int helmetDurability,
            int chestplateItem, int chestplateDurability,
            int leggingsItem, int leggingsDurability,
            int bootsItem, int bootsDurability,
            int carriedBlock, String ownerNickname, String customName,
            int ageTicksRemaining, int breedingCooldownTicks, boolean babyForm,
            int airSupplyTicks, int underwaterConversionTicks,
            boolean drownedCarrierRolled, boolean drownedTridentCarrier,
            boolean beeHasStung, int beeDeathAfterStingTicks, boolean beeHasNectar,
            double beeHomeX, double beeHomeY, double beeHomeZ,
            boolean persistenceRequired, int slimeSize, int loveTicksRemaining,
            long vexSummonerMobId, int vexLimitedLifeTicks,
            String angerTargetNickname, int angerTicks,
            int ravagerAttackTicks, int ravagerStunnedTicks, int ravagerRoarTicks,
            String armadilloShellState, int armadilloStateMcTicks,
            int armadilloDangerMcTicks, int armadilloScuteTicks,
            int dolphinMoistureTicks, int dolphinDryDamageMcTicks,
            boolean dolphinGotFish, int dolphinTreasureX, int dolphinTreasureY,
            int dolphinTreasureZ,
            String illagerContext, long illagerContextIdentity, int illagerPolicyVersion,
            long companionHandlerMobId, long companionAppearanceLane,
            int companionRebindCount, String companionAbilityPhase,
            int companionAbilityTicks, int companionCooldownTicks,
            String companionTargetNickname, int companionMarkTicks,
            double companionChargeDistance, int companionOrphanTicks) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateMcTicks, armadilloDangerMcTicks,
                armadilloScuteTicks, dolphinMoistureTicks, dolphinDryDamageMcTicks,
                dolphinGotFish, dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ,
                illagerContext, illagerContextIdentity, illagerPolicyVersion,
                companionHandlerMobId, companionAppearanceLane, companionRebindCount);
        this.companionAbilityPhase = companionAbilityPhase;
        this.companionAbilityTicks = companionAbilityTicks;
        this.companionCooldownTicks = companionCooldownTicks;
        this.companionTargetNickname = companionTargetNickname;
        this.companionMarkTicks = companionMarkTicks;
        this.companionChargeDistance = companionChargeDistance;
        this.companionOrphanTicks = companionOrphanTicks;
    }

    public MobPersistenceSnapshot(
            long mobId, String type, String variant,
            double x, double y, double z, double healthPoints,
            boolean pickedUpEquipment,
            int heldItem, int heldItemDurability,
            int helmetItem, int helmetDurability,
            int chestplateItem, int chestplateDurability,
            int leggingsItem, int leggingsDurability,
            int bootsItem, int bootsDurability,
            int carriedBlock, String ownerNickname, String customName,
            int ageTicksRemaining, int breedingCooldownTicks, boolean babyForm,
            int airSupplyTicks, int underwaterConversionTicks,
            boolean drownedCarrierRolled, boolean drownedTridentCarrier,
            boolean beeHasStung, int beeDeathAfterStingTicks, boolean beeHasNectar,
            double beeHomeX, double beeHomeY, double beeHomeZ,
            boolean persistenceRequired, int slimeSize, int loveTicksRemaining,
            long vexSummonerMobId, int vexLimitedLifeTicks,
            String angerTargetNickname, int angerTicks,
            int ravagerAttackTicks, int ravagerStunnedTicks, int ravagerRoarTicks,
            String armadilloShellState, int armadilloStateMcTicks,
            int armadilloDangerMcTicks, int armadilloScuteTicks,
            int dolphinMoistureTicks, int dolphinDryDamageMcTicks,
            boolean dolphinGotFish, int dolphinTreasureX, int dolphinTreasureY,
            int dolphinTreasureZ,
            String illagerContext, long illagerContextIdentity, int illagerPolicyVersion,
            long companionHandlerMobId, long companionAppearanceLane,
            int companionRebindCount, String companionAbilityPhase,
            int companionAbilityTicks, int companionCooldownTicks,
            String companionTargetNickname, int companionMarkTicks,
            double companionChargeDistance, int companionOrphanTicks,
            boolean ocelotTrusting) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateMcTicks, armadilloDangerMcTicks,
                armadilloScuteTicks, dolphinMoistureTicks, dolphinDryDamageMcTicks,
                dolphinGotFish, dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ,
                illagerContext, illagerContextIdentity, illagerPolicyVersion,
                companionHandlerMobId, companionAppearanceLane, companionRebindCount,
                companionAbilityPhase, companionAbilityTicks, companionCooldownTicks,
                companionTargetNickname, companionMarkTicks, companionChargeDistance,
                companionOrphanTicks);
        this.ocelotTrusting = ocelotTrusting;
    }

    public MobPersistenceSnapshot(
            long mobId, String type, String variant,
            double x, double y, double z, double healthPoints,
            boolean pickedUpEquipment,
            int heldItem, int heldItemDurability,
            int helmetItem, int helmetDurability,
            int chestplateItem, int chestplateDurability,
            int leggingsItem, int leggingsDurability,
            int bootsItem, int bootsDurability,
            int carriedBlock, String ownerNickname, String customName,
            int ageTicksRemaining, int breedingCooldownTicks, boolean babyForm,
            int airSupplyTicks, int underwaterConversionTicks,
            boolean drownedCarrierRolled, boolean drownedTridentCarrier,
            boolean beeHasStung, int beeDeathAfterStingTicks, boolean beeHasNectar,
            double beeHomeX, double beeHomeY, double beeHomeZ,
            boolean persistenceRequired, int slimeSize, int loveTicksRemaining,
            long vexSummonerMobId, int vexLimitedLifeTicks,
            String angerTargetNickname, int angerTicks,
            int ravagerAttackTicks, int ravagerStunnedTicks, int ravagerRoarTicks,
            String armadilloShellState, int armadilloStateMcTicks,
            int armadilloDangerMcTicks, int armadilloScuteTicks,
            int dolphinMoistureTicks, int dolphinDryDamageMcTicks,
            boolean dolphinGotFish, int dolphinTreasureX, int dolphinTreasureY,
            int dolphinTreasureZ,
            String illagerContext, long illagerContextIdentity, int illagerPolicyVersion,
            long companionHandlerMobId, long companionAppearanceLane,
            int companionRebindCount, String companionAbilityPhase,
            int companionAbilityTicks, int companionCooldownTicks,
            String companionTargetNickname, int companionMarkTicks,
            double companionChargeDistance, int companionOrphanTicks,
            boolean ocelotTrusting, boolean wolfSitting) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateMcTicks, armadilloDangerMcTicks,
                armadilloScuteTicks, dolphinMoistureTicks, dolphinDryDamageMcTicks,
                dolphinGotFish, dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ,
                illagerContext, illagerContextIdentity, illagerPolicyVersion,
                companionHandlerMobId, companionAppearanceLane, companionRebindCount,
                companionAbilityPhase, companionAbilityTicks, companionCooldownTicks,
                companionTargetNickname, companionMarkTicks, companionChargeDistance,
                companionOrphanTicks, ocelotTrusting);
        this.wolfSitting = wolfSitting;
    }

    public MobPersistenceSnapshot(
            long mobId, String type, String variant,
            double x, double y, double z, double healthPoints,
            boolean pickedUpEquipment,
            int heldItem, int heldItemDurability,
            int helmetItem, int helmetDurability,
            int chestplateItem, int chestplateDurability,
            int leggingsItem, int leggingsDurability,
            int bootsItem, int bootsDurability,
            int carriedBlock, String ownerNickname, String customName,
            int ageTicksRemaining, int breedingCooldownTicks, boolean babyForm,
            int airSupplyTicks, int underwaterConversionTicks,
            boolean drownedCarrierRolled, boolean drownedTridentCarrier,
            boolean beeHasStung, int beeDeathAfterStingTicks, boolean beeHasNectar,
            double beeHomeX, double beeHomeY, double beeHomeZ,
            boolean persistenceRequired, int slimeSize, int loveTicksRemaining,
            long vexSummonerMobId, int vexLimitedLifeTicks,
            String angerTargetNickname, int angerTicks,
            int ravagerAttackTicks, int ravagerStunnedTicks, int ravagerRoarTicks,
            String armadilloShellState, int armadilloStateMcTicks,
            int armadilloDangerMcTicks, int armadilloScuteTicks,
            int dolphinMoistureTicks, int dolphinDryDamageMcTicks,
            boolean dolphinGotFish, int dolphinTreasureX, int dolphinTreasureY,
            int dolphinTreasureZ,
            String illagerContext, long illagerContextIdentity, int illagerPolicyVersion,
            long companionHandlerMobId, long companionAppearanceLane,
            int companionRebindCount, String companionAbilityPhase,
            int companionAbilityTicks, int companionCooldownTicks,
            String companionTargetNickname, int companionMarkTicks,
            double companionChargeDistance, int companionOrphanTicks,
            boolean ocelotTrusting, boolean wolfSitting,
            int wolfCollarColor, int wolfArmorDurability, int tadpoleAgeMcTicks,
            String zombieNautilusChargePhase,
            int zombieNautilusChargeCooldownMcTicks,
            String zombieNautilusChargeTargetNickname,
            long zombieNautilusChargeTargetMobId,
            double zombieNautilusChargeVx, double zombieNautilusChargeVy,
            double zombieNautilusChargeVz, double zombieNautilusChargeDistance,
            int zombieNautilusNaturalTargetCooldownMcTicks) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateMcTicks, armadilloDangerMcTicks,
                armadilloScuteTicks, dolphinMoistureTicks, dolphinDryDamageMcTicks,
                dolphinGotFish, dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ,
                illagerContext, illagerContextIdentity, illagerPolicyVersion,
                companionHandlerMobId, companionAppearanceLane, companionRebindCount,
                companionAbilityPhase, companionAbilityTicks, companionCooldownTicks,
                companionTargetNickname, companionMarkTicks, companionChargeDistance,
                companionOrphanTicks, ocelotTrusting, wolfSitting);
        this.wolfCollarColor = wolfCollarColor;
        this.wolfArmorDurability = wolfArmorDurability;
        this.tadpoleAgeMcTicks = tadpoleAgeMcTicks;
        this.zombieNautilusChargePhase = zombieNautilusChargePhase;
        this.zombieNautilusChargeCooldownMcTicks = zombieNautilusChargeCooldownMcTicks;
        this.zombieNautilusChargeTargetNickname = zombieNautilusChargeTargetNickname;
        this.zombieNautilusChargeTargetMobId = zombieNautilusChargeTargetMobId;
        this.zombieNautilusChargeVx = zombieNautilusChargeVx;
        this.zombieNautilusChargeVy = zombieNautilusChargeVy;
        this.zombieNautilusChargeVz = zombieNautilusChargeVz;
        this.zombieNautilusChargeDistance = zombieNautilusChargeDistance;
        this.zombieNautilusNaturalTargetCooldownMcTicks =
                zombieNautilusNaturalTargetCooldownMcTicks;
    }

    /**
     * 환술사 주문 타이머와 개체별 동행 동물 판정을 덧붙입니다. 매개변수 70개짜리 생성자 사슬을 더
     * 늘리지 않는 append-only 경로이며, 새 스냅샷 1건에 대해 정확히 한 번만 호출합니다.
     */
    public MobPersistenceSnapshot withIllusionerAndCompanionDecision(
            int illusionerInvisibilityMcTicks,
            int illusionerMirrorCooldownMcTicks,
            int illusionerBlindnessCooldownMcTicks,
            int illusionerCastMcTicks,
            String illusionerBlindTargetKey,
            boolean companionDecisionSettled,
            boolean companionDecisionWinner) {
        this.illusionerInvisibilityMcTicks = illusionerInvisibilityMcTicks;
        this.illusionerMirrorCooldownMcTicks = illusionerMirrorCooldownMcTicks;
        this.illusionerBlindnessCooldownMcTicks = illusionerBlindnessCooldownMcTicks;
        this.illusionerCastMcTicks = illusionerCastMcTicks;
        this.illusionerBlindTargetKey = illusionerBlindTargetKey;
        this.companionDecisionSettled = companionDecisionSettled;
        this.companionDecisionWinner = companionDecisionWinner;
        return this;
    }

    /**
     * 좀비 주민 치료 카운트다운을 덧붙입니다. 생성자 사슬을 늘리지 않는 append-only 경로이며
     * 새 스냅샷 1건에 대해 정확히 한 번만 호출합니다(MOB.md §2).
     */
    public MobPersistenceSnapshot withZombieVillagerConversion(
            int zombieVillagerConversionMcTicks,
            String zombieVillagerConversionStarter) {
        this.zombieVillagerConversionMcTicks = zombieVillagerConversionMcTicks;
        this.zombieVillagerConversionStarter = zombieVillagerConversionStarter;
        return this;
    }

    /**
     * 주민 사회 기억을 덧붙입니다. 생성자 사슬을 늘리지 않는 append-only 경로이며 새 스냅샷 1건에
     * 대해 정확히 한 번만 호출합니다(MOB.md §2).
     */
    public MobPersistenceSnapshot withVillagerSocial(String villagerSocial) {
        this.villagerSocial = villagerSocial;
        return this;
    }

    public MobPersistenceSnapshot withCompanionState(boolean sitting, int collarColor) {
        boolean companion = "CAT".equals(type) || "PARROT".equals(type);
        if (sitting && (!companion || ownerNickname == null)
                || ("CAT".equals(type) ? collarColor < 0 || collarColor > 15 : collarColor != -1)) {
            throw new IllegalStateException("invalid persisted Cat/Parrot companion state");
        }
        this.companionSitting = sitting;
        // 고양이가 아닌 몹은 목걸이 색이 없다. -1을 그대로 저장하면 companion 상태 이전에 만든 행(null)과
        // 복원 뒤 다시 찍은 스냅샷이 같은 몹인데도 달라지므로 null로 정규화한다.
        this.catCollarColor = collarColor == -1 && !"CAT".equals(type) ? null : collarColor;
        return this;
    }

    public MobPersistenceSnapshot withFinalCarrierFacts(String exactVariant, float yaw, float pitch,
            double velocityX, double velocityY, double velocityZ,
            String villagerBiomeType, String villagerProfession, int villagerLevel) {
        this.variant = exactVariant;
        this.yaw = yaw;
        this.pitch = pitch;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.villagerBiomeType = villagerBiomeType;
        this.villagerProfession = villagerProfession;
        this.villagerLevel = villagerLevel;
        this.finalCarrierBinding = true;
        return this;
    }

    public MobPersistenceSnapshot withFinalCarrierBinding(boolean finalCarrierBinding) {
        this.finalCarrierBinding = finalCarrierBinding;
        return this;
    }

    /**
     * 양 색·전단, 닭 산란 커서, 돼지 안장·부스트를 덧붙입니다. 생성자 사슬을 늘리지 않는
     * append-only 경로이며 새 스냅샷 1건에 대해 정확히 한 번만 호출합니다(MOB.md §4).
     * 기수 닉네임은 세션 수명을 넘지 못하므로 저장하지 않는다 — 재시작 뒤 좌석은 항상 빈다.
     */
    public MobPersistenceSnapshot withFarmAnimalState(
            int sheepColor, boolean sheepSheared, int sheepEatMcTicks,
            int chickenEggMcTicks,
            boolean pigSaddled, int pigBoostMcTicks, int pigBoostTotalMcTicks) {
        this.sheepColor = sheepColor;
        this.sheepSheared = sheepSheared;
        this.sheepEatMcTicks = sheepEatMcTicks;
        this.chickenEggMcTicks = chickenEggMcTicks;
        this.pigSaddled = pigSaddled;
        this.pigBoostMcTicks = pigBoostMcTicks;
        this.pigBoostTotalMcTicks = pigBoostTotalMcTicks;
        return this;
    }

    /**
     * 차지드 크리퍼 표식을 덧붙입니다. 생성자 사슬을 늘리지 않는 append-only 경로이며 새 스냅샷
     * 1건에 대해 정확히 한 번만 호출합니다(MOB.md §2). 열대어 variant 와 같은 계약으로, 값이
     * 없던 옛 행은 false 로 읽혀 낙뢰를 맞은 적 없는 크리퍼가 된다.
     */
    public MobPersistenceSnapshot withCreeperPowered(boolean creeperPowered) {
        this.creeperPowered = creeperPowered;
        return this;
    }

    public MobPersistenceSnapshot withCreeperIgnited(boolean creeperIgnited) {
        this.creeperIgnited = creeperIgnited;
        return this;
    }

    public MobPersistenceSnapshot withTrialEquipmentNoDrop(boolean trialEquipmentNoDrop) {
        this.trialEquipmentNoDrop = trialEquipmentNoDrop;
        return this;
    }

    /** [GLOWING] 활성 상태이상. null 은 빈 목록으로 읽는다. */
    public MobPersistenceSnapshot withStatusEffects(
            java.util.List<com.gameexpert.engine.effect.StatusEffects.PersistentEffect> statusEffects) {
        this.statusEffects = statusEffects == null ? java.util.List.of() : java.util.List.copyOf(statusEffects);
        return this;
    }

    /** [MOB-EQUIP] 칸별 장비 성분 열. 형식 검증은 복원({@code Mob.restoreEquipmentComponents})이 한다. */
    public MobPersistenceSnapshot withEquipmentComponents(String equipmentComponents) {
        this.equipmentComponents = equipmentComponents;
        return this;
    }

    public MobPersistenceSnapshot withMooshroomStoredFlower(int flowerBlockId) {
        if (flowerBlockId != com.gameexpert.engine.mob.MooshroomRules.NO_STORED_FLOWER
                && !com.gameexpert.engine.SuspiciousStewRules.isStewFlower(flowerBlockId)) {
            throw new IllegalArgumentException("invalid Mooshroom stored flower: " + flowerBlockId);
        }
        mooshroomStoredFlower = flowerBlockId;
        return this;
    }

    /**
     * 말 계열의 길들이기·안장·개체 스탯을 덧붙입니다. 생성자 사슬을 늘리지 않는 append-only
     * 경로이며 새 스냅샷 1건에 대해 정확히 한 번만 호출합니다. 기수 닉네임은 세션 수명을 넘지
     * 못하므로 저장하지 않는다 — 재시작 뒤 좌석은 항상 빈다(돼지와 같은 계약).
     */
    public MobPersistenceSnapshot withHorseState(boolean horseTamed, int horseTemper,
            boolean horseSaddled, double horseMaxHealth, double horseSpeed,
            double horseJumpStrength) {
        this.horseTamed = horseTamed;
        this.horseTemper = horseTemper;
        this.horseSaddled = horseSaddled;
        this.horseMaxHealth = horseMaxHealth;
        this.horseSpeed = horseSpeed;
        this.horseJumpStrength = horseJumpStrength;
        return this;
    }

    public MobPersistenceSnapshot withHorseArmorItem(short armorItem) {
        if (armorItem != 0 && !com.gameexpert.engine.mob.HorseRules.isArmorItem(armorItem)) {
            throw new IllegalArgumentException("invalid horse armor item: "
                    + Short.toUnsignedInt(armorItem));
        }
        this.horseArmorItem = armorItem;
        return this;
    }

    public MobPersistenceSnapshot withGoatHornMask(Integer hornMask) {
        if (hornMask != null && (hornMask < 0 || hornMask > 3)) {
            throw new IllegalArgumentException("invalid Goat horn mask: " + hornMask);
        }
        this.goatHornMask = hornMask;
        return this;
    }

    public MobPersistenceSnapshot withGoatRamState(String phase, int cooldownMcTicks,
            String targetNickname, double targetX, double targetZ,
            double runUpX, double runUpZ, double directionX, double directionZ,
            int prepareMcTicks, double distance, long sequence,
            int pendingBlockId, int pendingX, int pendingY, int pendingZ,
            boolean pendingPreferLeft) {
        if (phase == null || (!"IDLE".equals(phase) && !"PREPARING".equals(phase)
                && !"CHARGING".equals(phase) && !"IMPACT_PENDING".equals(phase))
                || cooldownMcTicks < -1 || cooldownMcTicks > 6_000
                || prepareMcTicks < 0 || prepareMcTicks > 20 || distance < 0 || sequence < 0
                || "IMPACT_PENDING".equals(phase) && pendingBlockId <= 0) {
            throw new IllegalArgumentException("invalid Goat ram snapshot");
        }
        goatRamPhase = phase;
        goatRamCooldownMcTicks = cooldownMcTicks;
        goatRamTargetNickname = targetNickname;
        goatRamTargetX = targetX;
        goatRamTargetZ = targetZ;
        goatRamRunUpX = runUpX;
        goatRamRunUpZ = runUpZ;
        goatRamDirectionX = directionX;
        goatRamDirectionZ = directionZ;
        goatRamPrepareMcTicks = prepareMcTicks;
        goatRamDistance = distance;
        goatRamSequence = sequence;
        goatRamPendingBlockId = pendingBlockId;
        goatRamPendingX = pendingX;
        goatRamPendingY = pendingY;
        goatRamPendingZ = pendingZ;
        goatRamPendingPreferLeft = pendingPreferLeft;
        return this;
    }

    public MobPersistenceSnapshot withSnifferDigState(String phase, int cooldownMcTicks,
            int phaseMcTicks, int dropDelayMcTicks, int targetX, int targetY, int targetZ,
            long searchEpoch, long sequence, long pendingSequence, short pendingItem) {
        boolean validPhase = "IDLE".equals(phase) || "SCENTING".equals(phase)
                || "SNIFFING".equals(phase) || "SEARCHING".equals(phase)
                || "DIGGING".equals(phase) || "RISING".equals(phase);
        int maximumPhaseTicks = phase == null ? -1 : switch (phase) {
            case "IDLE" -> 0;
            case "SCENTING", "SNIFFING" -> 80;
            case "SEARCHING" -> 600;
            case "DIGGING" -> 180;
            case "RISING" -> 40;
            default -> -1;
        };
        if (!validPhase || cooldownMcTicks < 0 || cooldownMcTicks > 9_600
                || phaseMcTicks < 0 || phaseMcTicks > maximumPhaseTicks
                || dropDelayMcTicks < 0 || dropDelayMcTicks > 120
                || searchEpoch < 0 || sequence < 0 || pendingSequence < 0
                || (pendingSequence == 0) != (pendingItem == 0)
                || pendingItem != 0
                    && pendingItem != com.gameexpert.engine.inventory.PlayerInventory.TORCHFLOWER_SEEDS
                    && pendingItem != com.gameexpert.engine.inventory.PlayerInventory.PITCHER_POD) {
            throw new IllegalArgumentException("invalid Sniffer dig snapshot");
        }
        snifferDigPhase = phase;
        snifferDigCooldownMcTicks = cooldownMcTicks;
        snifferDigPhaseMcTicks = phaseMcTicks;
        snifferDigDropDelayMcTicks = dropDelayMcTicks;
        snifferDigTargetX = targetX;
        snifferDigTargetY = targetY;
        snifferDigTargetZ = targetZ;
        snifferDigSearchEpoch = searchEpoch;
        snifferDigSequence = sequence;
        snifferPendingDropSequence = pendingSequence;
        snifferPendingDropItem = pendingItem;
        return this;
    }

    public MobPersistenceSnapshot withLeashHolderNickname(String nickname) {
        this.leashHolderNickname = nickname == null || nickname.isBlank() ? null : nickname;
        return this;
    }

    /**
     * 상자 장착·라마 힘·카펫을 덧붙입니다. 생성자 사슬을 늘리지 않는 append-only 경로이며
     * 새 스냅샷 1건에 대해 정확히 한 번만 호출합니다({@link #withHorseState} 와 같은 계약).
     * 라마 힘은 HMI1 화물 열 수를 정하므로 복원 경계가 메뉴보다 먼저 되돌린다.
     *
     * @param llamaCarpetColor 0~15 또는 장식 없음 -1({@code LlamaRules.NO_CARPET})
     */
    public MobPersistenceSnapshot withHorseEquipment(boolean horseChested,
            int llamaStrength, int llamaCarpetColor) {
        if (llamaCarpetColor < -1 || llamaCarpetColor > 15 || llamaStrength < 0
                || llamaStrength > 5) {
            throw new IllegalArgumentException("invalid horse equipment snapshot");
        }
        this.horseChested = horseChested;
        this.llamaStrength = llamaStrength;
        this.llamaCarpetColor = llamaCarpetColor;
        return this;
    }

    public MobPersistenceSnapshot withHorseInventoryData(String encoded) {
        String previous = horseInventoryData;
        horseInventoryData = encoded;
        try {
            MobRuntime.validateHorseInventoryPayloadShape(this);
        } catch (RuntimeException invalid) {
            horseInventoryData = previous;
            throw invalid;
        }
        return this;
    }

    public MobPersistenceSnapshot withCargoPersistenceRevision(long revision) {
        validateCargoPersistenceRevision(revision);
        cargoPersistenceRevision = revision;
        return this;
    }

    /**
     * HMI1 payload and its durable generation are one persistence value. Both fields are staged
     * before the full horse-menu/scalar gate and are restored together if that gate rejects them.
     */
    public MobPersistenceSnapshot withHorseInventoryDataAndRevision(
            String encoded, long revision) {
        return withHorseInventoryDataAndRevisions(encoded, revision, revision, revision);
    }

    /**
     * HMI1 payload and the exact shared/equipment/cargo tuple are one append-only persistence
     * value. The old two-argument writer remains a compatibility alias for rows whose three
     * generations were historically collapsed to one value.
     */
    public MobPersistenceSnapshot withHorseInventoryDataAndRevisions(String encoded,
            long sharedRevision, long equipmentRevision, long cargoRevision) {
        validateHorsePersistenceRevision(sharedRevision, "horse menu");
        validateHorsePersistenceRevision(equipmentRevision, "horse equipment");
        validateHorsePersistenceRevision(cargoRevision, "horse cargo");
        String previousPayload = horseInventoryData;
        long previousShared = horseMenuPersistenceRevision;
        long previousEquipment = horseEquipmentPersistenceRevision;
        long previousCargo = cargoPersistenceRevision;
        horseInventoryData = encoded;
        horseMenuPersistenceRevision = sharedRevision;
        horseEquipmentPersistenceRevision = equipmentRevision;
        cargoPersistenceRevision = cargoRevision;
        try {
            MobRuntime.validateHorseInventoryPayload(this);
        } catch (RuntimeException invalid) {
            horseInventoryData = previousPayload;
            horseMenuPersistenceRevision = previousShared;
            horseEquipmentPersistenceRevision = previousEquipment;
            cargoPersistenceRevision = previousCargo;
            throw invalid;
        }
        return this;
    }

    /**
     * 낙타의 안장·자세를 덧붙입니다. 생성자 사슬을 늘리지 않는 append-only 경로이며 새 스냅샷
     * 1건에 대해 정확히 한 번만 호출합니다(말 계열과 같은 계약). 좌석 기수 닉네임은 세션 수명을
     * 넘지 못하므로 저장하지 않는다 — 재시작 뒤 두 좌석 모두 항상 빈다.
     */
    public MobPersistenceSnapshot withCamelState(boolean camelSaddled, boolean camelSitting) {
        boolean previousSaddled = this.camelSaddled;
        boolean previousSitting = this.camelSitting;
        this.camelSaddled = camelSaddled;
        this.camelSitting = camelSitting;
        if (horseInventoryData != null) {
            try {
                MobRuntime.validateHorseInventoryPayload(this);
            } catch (RuntimeException invalid) {
                this.camelSaddled = previousSaddled;
                this.camelSitting = previousSitting;
                throw invalid;
            }
        }
        return this;
    }

    /**
     * [HARNESS] 해피 가스트의 하네스 장착 상태와 색을 덧붙입니다. 생성자 사슬을 늘리지 않는
     * append-only 경로이며 새 스냅샷 1건에 대해 정확히 한 번만 호출합니다(낙타와 같은 계약).
     * 좌석 기수 닉네임은 세션 수명을 넘지 못하므로 저장하지 않는다 — 재시작 뒤 네 좌석 모두
     * 항상 빈다.
     *
     * <p>하네스가 없는데 색이 붙거나, 하네스가 있는데 색이 범위 밖인 조합은 저장 자체를
     * 거절한다. 손상된 조합을 그대로 실으면 복원 쪽이 "없는 색"을 지어내야 한다.
     */
    public MobPersistenceSnapshot withHappyGhastState(boolean harnessed, int harnessColor) {
        boolean validColor = harnessColor >= 0 && harnessColor <= 15;
        if (harnessed ? !validColor : harnessColor != -1) {
            throw new IllegalArgumentException(
                    "invalid HappyGhast harness snapshot: harnessed=" + harnessed
                            + " color=" + harnessColor);
        }
        this.happyGhastHarnessed = harnessed;
        this.happyGhastHarnessColor = harnessColor;
        return this;
    }

    /**
     * [NAUTILUS-MOUNT] 안장·갑옷 상태를 덧붙인다. 손상된 조합(범위 밖 티어)은 저장 시점에
     * 거절한다 — 하네스가 색 없이 참일 수 없는 것과 같은 계약이다.
     */
    public MobPersistenceSnapshot withNautilusMountState(boolean saddled, int armorTier) {
        if (armorTier < -1 || armorTier > 4) {
            throw new IllegalArgumentException(
                    "invalid Nautilus mount snapshot: armorTier=" + armorTier);
        }
        this.nautilusSaddled = saddled;
        this.nautilusArmorTier = armorTier;
        return this;
    }

    public MobPersistenceSnapshot withDrownedJockeyState(
            boolean armed, boolean settled, boolean winner, long vehicleMobId) {
        if ((!armed && (!settled || winner)) || (!settled && winner)
                || vehicleMobId < 0 || (vehicleMobId != 0 && (!settled || !winner))) {
            throw new IllegalArgumentException("invalid Drowned jockey persistence state");
        }
        this.drownedJockeyDecisionArmed = armed;
        this.drownedJockeyDecisionSettled = settled;
        this.drownedJockeyDecisionWinner = winner;
        this.vehicleMobId = vehicleMobId;
        return this;
    }

    public MobPersistenceSnapshot withSkeletonHorseTrapState(boolean active, int ageMcTicks) {
        if ((!"SKELETON_HORSE".equals(type) && (active || ageMcTicks != 0))
                || ageMcTicks < 0
                || ageMcTicks >= com.gameexpert.engine.mob.SkeletonHorse.TRAP_DESPAWN_TICKS
                || (!active && ageMcTicks != 0)) {
            throw new IllegalArgumentException("invalid Skeleton Horse trap persistence state");
        }
        this.skeletonHorseTrapActive = active;
        this.skeletonHorseTrapAgeMcTicks = ageMcTicks;
        return this;
    }

    public MobPersistenceSnapshot withSkeletonTrapRiderVehicle(long vehicleMobId) {
        if (vehicleMobId < 0 || (vehicleMobId != 0 && !"SKELETON".equals(type))) {
            throw new IllegalArgumentException("invalid Skeleton trap rider vehicle");
        }
        if (vehicleMobId != 0) this.vehicleMobId = vehicleMobId;
        return this;
    }

    public MobPersistenceSnapshot withCopperGolemState(int oxidationAge, boolean waxed, int pose,
            long nextWeatheringMcTick, boolean statuePending) {
        boolean neutral = oxidationAge == 0 && !waxed && pose == 0
                && nextWeatheringMcTick == -1L && !statuePending;
        if ((!"COPPER_GOLEM".equals(type) && !neutral)
                || oxidationAge < 0 || oxidationAge > 3 || pose < 0 || pose > 3
                || nextWeatheringMcTick < -2L
                || waxed != (nextWeatheringMcTick == -2L)
                || statuePending && (waxed || oxidationAge != 3)) {
            throw new IllegalArgumentException("invalid Copper Golem persistence state");
        }
        this.copperGolemOxidationAge = oxidationAge;
        this.copperGolemWaxed = waxed;
        this.copperGolemPose = pose;
        this.copperGolemNextWeatheringMcTick = nextWeatheringMcTick;
        this.copperGolemStatuePending = statuePending;
        return this;
    }

    /** Persists the Drowned offhand shell independently from its main-hand equipment. */
    public MobPersistenceSnapshot withDrownedShellCarrier(boolean shellCarrier) {
        if (shellCarrier && (!"DROWNED".equals(type) || !drownedCarrierRolled)) {
            throw new IllegalArgumentException("invalid Drowned shell carrier state");
        }
        this.drownedShellCarrier = shellCarrier;
        return this;
    }

    public MobPersistenceSnapshot withPoisonDartFrogState(
            int feedCooldownMcTicks, int defensiveTicks, int contactCooldownTicks,
            long feedSequence, long pendingFeedSequence,
            int pendingFeedX, int pendingFeedY, int pendingFeedZ) {
        boolean neutral = feedCooldownMcTicks == 0 && defensiveTicks == 0
                && contactCooldownTicks == 0 && feedSequence == 0 && pendingFeedSequence == 0
                && pendingFeedX == 0 && pendingFeedY == 0 && pendingFeedZ == 0;
        if (!"POISON_DART_FROG".equals(type) && !neutral) {
            throw new IllegalArgumentException("non-poison-dart-frog has persisted species state");
        }
        if (feedCooldownMcTicks < 0
                || feedCooldownMcTicks > PoisonDartFrogRules.FEED_COOLDOWN_MC_TICKS
                || defensiveTicks < 0
                || defensiveTicks > PoisonDartFrogRules.DEFENSIVE_WINDOW_TICKS
                || contactCooldownTicks < 0
                || contactCooldownTicks > PoisonDartFrogRules.CONTACT_COOLDOWN_TICKS
                || feedSequence < 0 || pendingFeedSequence < 0
                || (pendingFeedSequence != 0 && pendingFeedSequence != feedSequence)
                || (pendingFeedSequence != 0 && feedCooldownMcTicks != 0)
                || (pendingFeedSequence == 0
                        && (pendingFeedX != 0 || pendingFeedY != 0 || pendingFeedZ != 0))) {
            throw new IllegalArgumentException("invalid poison dart frog persistence state");
        }
        this.poisonDartFrogFeedCooldownMcTicks = feedCooldownMcTicks;
        this.poisonDartFrogDefensiveTicks = defensiveTicks;
        this.poisonDartFrogContactCooldownTicks = contactCooldownTicks;
        this.poisonDartFrogFeedSequence = feedSequence;
        this.poisonDartFrogPendingFeedSequence = pendingFeedSequence;
        this.poisonDartFrogPendingFeedX = pendingFeedX;
        this.poisonDartFrogPendingFeedY = pendingFeedY;
        this.poisonDartFrogPendingFeedZ = pendingFeedZ;
        return this;
    }

    public MobPersistenceSnapshot withBeeHiveTicks(int hiveTicks) {
        if (hiveTicks < 0 || hiveTicks > 0 && beeHasNectar) {
            throw new IllegalArgumentException("invalid Bee hive stay snapshot");
        }
        this.beeHiveTicks = hiveTicks;
        return this;
    }

    public MobPersistenceSnapshot withPufferPuffStage(int stage) {
        if (stage < 0 || stage > 2) {
            throw new IllegalArgumentException("invalid Pufferfish PuffState: " + stage);
        }
        this.pufferPuffStage = stage;
        return this;
    }

    /**
     * 알레이가 드랍 엔티티에서 확정 획득한 운반 스택을 덧붙입니다. pickup/return outstanding은
     * 외부 엔티티를 재검증하는 일회성 handshake라 저장하지 않고 재시작 뒤 false에서 다시 발행한다.
     */
    public MobPersistenceSnapshot withAllayDelivery(int count, int durability) {
        if (count < 0 || durability < 0 || count == 0 && durability != 0) {
            throw new IllegalArgumentException("invalid Allay delivery snapshot");
        }
        this.allayDeliveryCount = count;
        this.allayDeliveryDurability = durability;
        return this;
    }

    public MobPersistenceSnapshot withTurtleEggState(boolean gravid, int homeX, int homeY,
            int homeZ, boolean travelingHome, int digMcTicks, int eggCount) {
        if (digMcTicks < 0 || gravid != (eggCount >= 1 && eggCount <= 4)
                || !gravid && (travelingHome || digMcTicks != 0)
                || travelingHome && digMcTicks != 0) {
            throw new IllegalArgumentException("invalid Turtle egg digging snapshot");
        }
        this.turtleGravid = gravid;
        this.turtleHomeX = homeX;
        this.turtleHomeY = homeY;
        this.turtleHomeZ = homeZ;
        this.turtleTravelingHome = travelingHome;
        this.turtleEggDigMcTicks = digMcTicks;
        this.turtleEggCount = eggCount;
        return this;
    }

    /**
     * Widest form. Raid membership is the last pair so that the released raider, its wave and its
     * exact health all commit in the one row the mob aggregate already writes.
     */
    public MobPersistenceSnapshot(
            long mobId, String type, String variant,
            double x, double y, double z, double healthPoints,
            boolean pickedUpEquipment,
            int heldItem, int heldItemDurability,
            int helmetItem, int helmetDurability,
            int chestplateItem, int chestplateDurability,
            int leggingsItem, int leggingsDurability,
            int bootsItem, int bootsDurability,
            int carriedBlock, String ownerNickname, String customName,
            int ageTicksRemaining, int breedingCooldownTicks, boolean babyForm,
            int airSupplyTicks, int underwaterConversionTicks,
            boolean drownedCarrierRolled, boolean drownedTridentCarrier,
            boolean beeHasStung, int beeDeathAfterStingTicks, boolean beeHasNectar,
            double beeHomeX, double beeHomeY, double beeHomeZ,
            boolean persistenceRequired, int slimeSize, int loveTicksRemaining,
            long vexSummonerMobId, int vexLimitedLifeTicks,
            String angerTargetNickname, int angerTicks,
            int ravagerAttackTicks, int ravagerStunnedTicks, int ravagerRoarTicks,
            String armadilloShellState, int armadilloStateMcTicks,
            int armadilloDangerMcTicks, int armadilloScuteTicks,
            int dolphinMoistureTicks, int dolphinDryDamageMcTicks,
            boolean dolphinGotFish, int dolphinTreasureX, int dolphinTreasureY,
            int dolphinTreasureZ,
            String illagerContext, long illagerContextIdentity, int illagerPolicyVersion,
            long companionHandlerMobId, long companionAppearanceLane,
            int companionRebindCount, String companionAbilityPhase,
            int companionAbilityTicks, int companionCooldownTicks,
            String companionTargetNickname, int companionMarkTicks,
            double companionChargeDistance, int companionOrphanTicks,
            boolean ocelotTrusting, boolean wolfSitting,
            int wolfCollarColor, int wolfArmorDurability, int tadpoleAgeMcTicks,
            String zombieNautilusChargePhase,
            int zombieNautilusChargeCooldownMcTicks,
            String zombieNautilusChargeTargetNickname,
            long zombieNautilusChargeTargetMobId,
            double zombieNautilusChargeVx, double zombieNautilusChargeVy,
            double zombieNautilusChargeVz, double zombieNautilusChargeDistance,
            int zombieNautilusNaturalTargetCooldownMcTicks,
            long raidId, int raidWave) {
        this(mobId, type, variant, x, y, z, healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability, helmetItem, helmetDurability,
                chestplateItem, chestplateDurability, leggingsItem, leggingsDurability,
                bootsItem, bootsDurability, carriedBlock, ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateMcTicks, armadilloDangerMcTicks,
                armadilloScuteTicks, dolphinMoistureTicks, dolphinDryDamageMcTicks,
                dolphinGotFish, dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ,
                illagerContext, illagerContextIdentity, illagerPolicyVersion,
                companionHandlerMobId, companionAppearanceLane, companionRebindCount,
                companionAbilityPhase, companionAbilityTicks, companionCooldownTicks,
                companionTargetNickname, companionMarkTicks, companionChargeDistance,
                companionOrphanTicks, ocelotTrusting, wolfSitting,
                wolfCollarColor, wolfArmorDurability, tadpoleAgeMcTicks,
                zombieNautilusChargePhase, zombieNautilusChargeCooldownMcTicks,
                zombieNautilusChargeTargetNickname, zombieNautilusChargeTargetMobId,
                zombieNautilusChargeVx, zombieNautilusChargeVy, zombieNautilusChargeVz,
                zombieNautilusChargeDistance, zombieNautilusNaturalTargetCooldownMcTicks);
        this.raidId = raidId;
        this.raidWave = raidWave;
    }

    private static void validateCargoPersistenceRevision(long revision) {
        validateHorsePersistenceRevision(revision, "mob cargo");
    }

    private static void validateHorsePersistenceRevision(long revision, String name) {
        if (revision < 0 || revision >= Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException(
                    name + " persistence revision must be non-negative and publishable");
        }
    }

    private static short checkedId(int id) {
        if (id < 0 || id > Short.MAX_VALUE) {
            throw new IllegalArgumentException("persisted protocol ID is out of range: " + id);
        }
        return (short) id;
    }

    /** [EC-MOBS] 셜커(부착면·peek) · 아이템 액자(방향·회전·넣은 아이템의 인챈트/성분) 상태. */
    public MobPersistenceSnapshot withAttachedState(int face, int state, long enchantments, String components) {
        if (face < 0 || face > 5 || state < 0 || state > 100) {
            throw new IllegalArgumentException("invalid attached entity snapshot");
        }
        this.attachFace = face;
        this.attachState = state;
        this.heldItemEnchantments = enchantments;
        this.heldItemComponents = components;
        return this;
    }

    public MobPersistenceSnapshot withSulfurCubeState(int pickupCooldownTicks,
            int fuseTicks, int maxFuseTicks, boolean fromBucket) {
        this.sulfurCubePickupCooldownTicks = pickupCooldownTicks;
        this.sulfurCubeFuseTicks = fuseTicks;
        this.sulfurCubeMaxFuseTicks = maxFuseTicks;
        this.sulfurCubeFromBucket = fromBucket;
        return this;
    }
}
