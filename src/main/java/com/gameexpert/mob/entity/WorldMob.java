package com.gameexpert.mob.entity;

import java.util.Objects;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.engine.mob.Armadillo;
import com.gameexpert.engine.mob.MobRuntime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서버를 다시 시작해도 복원할 현재 몹 하나의 최소 상태입니다. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(
        name = "world_mobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_mob_id",
                columnNames = { "world_id", "mob_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldMob {

    /** The last value that can be incremented without overflowing the durable identity sequence. */
    public static final long MAX_MOB_ID = Long.MAX_VALUE - 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mob_schema_version", nullable = false)
    private Integer mobSchemaVersion;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long mobId;

    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long cargoPersistenceRevision;

    /** Exact shared logical horse-menu generation; append-only beside the legacy cargo column. */
    @Column(name = "horse_menu_persistence_revision", nullable = false,
            columnDefinition = "bigint not null default 0")
    private long horseMenuPersistenceRevision;

    /** Exact equipment-local generation; it is never max-collapsed with the other revisions. */
    @Column(name = "horse_equipment_persistence_revision", nullable = false,
            columnDefinition = "bigint not null default 0")
    private long horseEquipmentPersistenceRevision;

    @Column(nullable = false)
    private String type;

    private String variant;

    @Column(nullable = false)
    private double posX;

    @Column(nullable = false)
    private double posY;

    @Column(nullable = false)
    private double posZ;

    @Column(nullable = false) private float yaw;
    @Column(nullable = false) private float pitch;
    @Column(nullable = false) private double velocityX;
    @Column(nullable = false) private double velocityY;
    @Column(nullable = false) private double velocityZ;

    @Column(nullable = false)
    private double healthPoints;

    @Column(nullable = false)
    private boolean pickedUpEquipment = false;

    @Column(nullable = false)
    private short heldItem = 0;

    @Column(nullable = false)
    private int heldItemDurability = 0;

    @Column(nullable = false)
    private short helmetItem = 0;

    @Column(nullable = false)
    private int helmetDurability = 0;

    @Column(nullable = false)
    private short chestplateItem = 0;

    @Column(nullable = false)
    private int chestplateDurability = 0;

    @Column(nullable = false)
    private short leggingsItem = 0;

    @Column(nullable = false)
    private int leggingsDurability = 0;

    @Column(nullable = false)
    private short bootsItem = 0;

    @Column(nullable = false)
    private int bootsDurability = 0;

    @Column(nullable = false)
    private short carriedBlock = 0;

    private String ownerNickname;

    private String customName;

    @Column(nullable = false)
    private int ageTicksRemaining;

    @Column(nullable = false)
    private int breedingCooldownTicks;

    @Column(nullable = false)
    private boolean babyForm = false;

    @Column(nullable = false)
    private int airSupplyTicks = 150;

    @Column(nullable = false)
    private int underwaterConversionTicks = 0;

    @Column(nullable = false)
    private boolean drownedCarrierRolled = false;

    @Column(nullable = false)
    private boolean drownedTridentCarrier = false;

    @Column(nullable = false)
    private boolean drownedShellCarrier = false;

    @Column(name = "poison_dart_frog_feed_cooldown_mc_ticks")
    private Integer poisonDartFrogFeedCooldownMcTicks;

    @Column(name = "poison_dart_frog_defensive_ticks")
    private Integer poisonDartFrogDefensiveTicks;

    @Column(name = "poison_dart_frog_contact_cooldown_ticks")
    private Integer poisonDartFrogContactCooldownTicks;

    @Column(name = "poison_dart_frog_feed_sequence")
    private Long poisonDartFrogFeedSequence;

    @Column(name = "poison_dart_frog_pending_feed_sequence")
    private Long poisonDartFrogPendingFeedSequence;
    @Column(name = "poison_dart_frog_pending_feed_x")
    private Integer poisonDartFrogPendingFeedX;
    @Column(name = "poison_dart_frog_pending_feed_y")
    private Integer poisonDartFrogPendingFeedY;
    @Column(name = "poison_dart_frog_pending_feed_z")
    private Integer poisonDartFrogPendingFeedZ;

    @Column(name = "drowned_jockey_decision_armed")
    private Boolean drownedJockeyDecisionArmed;

    @Column(name = "drowned_jockey_decision_settled")
    private Boolean drownedJockeyDecisionSettled;

    @Column(name = "drowned_jockey_decision_winner")
    private Boolean drownedJockeyDecisionWinner;

    @Column(name = "mooshroom_stored_flower")
    private Integer mooshroomStoredFlower;

    @Column(name = "vehicle_mob_id")
    private Long vehicleMobId;

    @Column(name = "skeleton_horse_trap_active", nullable = false)
    private boolean skeletonHorseTrapActive;

    @Column(name = "skeleton_horse_trap_age_mc_ticks", nullable = false)
    private int skeletonHorseTrapAgeMcTicks;

    @Column(name = "copper_golem_oxidation_age", nullable = false)
    private int copperGolemOxidationAge;

    @Column(name = "copper_golem_waxed", nullable = false)
    private boolean copperGolemWaxed;

    @Column(name = "copper_golem_pose", nullable = false)
    private int copperGolemPose;

    @Column(name = "copper_golem_next_weathering_mc_tick", nullable = false)
    private long copperGolemNextWeatheringMcTick = -1L;

    @Column(name = "copper_golem_statue_pending", nullable = false)
    private boolean copperGolemStatuePending;

    @Column(nullable = false)
    private boolean beeHasStung = false;

    @Column(nullable = false)
    private int beeDeathAfterStingTicks = 0;

    @Column(nullable = false)
    private boolean beeHasNectar = false;

    @Column(nullable = false)
    private double beeHomeX = 0;

    @Column(nullable = false)
    private double beeHomeY = 0;

    @Column(nullable = false)
    private double beeHomeZ = 0;

    @Column(name = "bee_hive_ticks")
    private Integer beeHiveTicks;

    @Column(name = "puffer_puff_stage")
    private Integer pufferPuffStage;

    @Column(nullable = false)
    private boolean persistenceRequired = false;

    @Column(nullable = false)
    private int slimeSize = 0;

    @Column(nullable = false)
    private int loveTicksRemaining = 0;

    @Column(nullable = false)
    private long vexSummonerMobId = 0;

    @Column(nullable = false)
    private int vexLimitedLifeTicks = 0;

    private String angerTargetNickname;

    @Column(nullable = false)
    private int angerTicks = 0;

    @Column(name = "ravager_attack_ticks")
    private Integer ravagerAttackTicks;

    @Column(name = "ravager_stunned_ticks")
    private Integer ravagerStunnedTicks;

    @Column(name = "ravager_roar_ticks")
    private Integer ravagerRoarTicks;

    @Column(name = "armadillo_shell_state")
    private String armadilloShellState;

    @Column(name = "armadillo_state_ticks")
    private Integer armadilloStateMcTicks;

    @Column(name = "armadillo_danger_ticks")
    private Integer armadilloDangerMcTicks;

    @Column(name = "armadillo_scute_ticks")
    private Integer armadilloScuteTicks;

    @Column(name = "dolphin_moisture_ticks")
    private Integer dolphinMoistureTicks;

    @Column(name = "dolphin_dry_damage_ticks")
    private Integer dolphinDryDamageMcTicks;

    @Column(name = "dolphin_got_fish")
    private Boolean dolphinGotFish;

    @Column(name = "dolphin_treasure_x")
    private Integer dolphinTreasureX;

    @Column(name = "dolphin_treasure_y")
    private Integer dolphinTreasureY;

    @Column(name = "dolphin_treasure_z")
    private Integer dolphinTreasureZ;

    @Column(name = "illager_context")
    private String illagerContext;

    @Column(name = "illager_context_identity")
    private Long illagerContextIdentity;

    @Column(name = "illager_policy_version")
    private Integer illagerPolicyVersion;

    @Column(name = "companion_handler_mob_id")
    private Long companionHandlerMobId;

    @Column(name = "companion_appearance_lane")
    private Long companionAppearanceLane;

    @Column(name = "companion_rebind_count")
    private Integer companionRebindCount;

    @Column(name = "companion_ability_phase")
    private String companionAbilityPhase;

    @Column(name = "companion_ability_ticks")
    private Integer companionAbilityTicks;

    @Column(name = "companion_cooldown_ticks")
    private Integer companionCooldownTicks;

    @Column(name = "companion_target_nickname")
    private String companionTargetNickname;

    @Column(name = "companion_mark_ticks")
    private Integer companionMarkTicks;

    @Column(name = "companion_charge_distance")
    private Double companionChargeDistance;

    @Column(name = "companion_orphan_ticks")
    private Integer companionOrphanTicks;

    @Column(name = "ocelot_trusting")
    private Boolean ocelotTrusting;

    @Column(name = "wolf_sitting")
    private Boolean wolfSitting;
    private Boolean companionSitting;
    private Integer catCollarColor;

    @Column(name = "wolf_collar_color")
    private Integer wolfCollarColor;

    @Column(name = "wolf_armor_durability")
    private Integer wolfArmorDurability;

    @Column(name = "tadpole_age_mc_ticks")
    private Integer tadpoleAgeMcTicks;

    @Column(name = "turtle_egg_dig_mc_ticks")
    private Integer turtleEggDigMcTicks;

    @Column(name = "turtle_traveling_home")
    private Boolean turtleTravelingHome;

    @Column(name = "turtle_egg_count")
    private Integer turtleEggCount;

    // ── [FARM-ANIMAL] 양 전단·염색, 닭 산란, 돼지 안장. 종별 화이트리스트는 toSnapshot 이 건다. ──
    @Column(name = "sheep_color")
    private Integer sheepColor;

    @Column(name = "sheep_sheared")
    private Boolean sheepSheared;

    @Column(name = "sheep_eat_mc_ticks")
    private Integer sheepEatMcTicks;

    @Column(name = "chicken_egg_mc_ticks")
    private Integer chickenEggMcTicks;

    @Column(name = "pig_saddled")
    private Boolean pigSaddled;

    /**
     * 차지드 크리퍼 표식(바닐라 {@code Creeper} NBT {@code powered}). 이 열이 없던 옛 행은
     * null 로 읽혀 false 가 된다 — 열대어 variant 와 같은 마이그레이션 계약이다.
     */
    @Column(name = "creeper_powered")
    private Boolean creeperPowered;

    /** 바닐라 Creeper NBT {@code ignited}; 이전 행의 null은 점화되지 않음이다. */
    @Column(name = "creeper_ignited")
    private Boolean creeperIgnited;

    /** [TRIAL-GAP] 트라이얼 장비의 드랍 확률 0 표식. 이전 행의 null은 표식 없음이다. */
    @Column(name = "trial_equipment_no_drop")
    private Boolean trialEquipmentNoDrop;

    /**
     * [GLOWING] 몹의 활성 상태이상 {@code 종류:앰프:남은MC틱:주기누적} 을 {@code ;} 로 이은 문자열.
     * 이전 행의 null(과 빈 문자열)은 효과 없음이고, 알 수 없거나 손상된 항목은 복원 때 버린다.
     */
    @Column(name = "status_effects", length = 1024)
    private String statusEffects;
    /** [MOB-EQUIP] 칸별 장비 성분({@code MEC1;…}). 이전 행의 null은 성분 없음·몹 단위 드랍 계층이다. */
    @Column(name = "equipment_components", length = 16_384)
    private String equipmentComponents;

    @Column(name = "pig_boost_mc_ticks")
    private Integer pigBoostMcTicks;

    @Column(name = "pig_boost_total_mc_ticks")
    private Integer pigBoostTotalMcTicks;

    /**
     * [MOUNT] 말 계열 상태. 이 열들이 없던 옛 행은 null 로 읽혀 미길들임·안장 없음·스탯 0 이
     * 되고, 스탯 0 은 복원 경계에서 "생성 롤 유지"로 해석된다(열대어 variant 와 같은 계약).
     */
    @Column(name = "horse_tamed")
    private Boolean horseTamed;

    @Column(name = "horse_temper")
    private Integer horseTemper;

    @Column(name = "horse_saddled")
    private Boolean horseSaddled;

    /** Exact ordinary-Horse BODY armor item ID; null/zero means an empty slot. */
    @Column(name = "horse_armor_item")
    private Integer horseArmorItem;

    @Column(name = "goat_horn_mask")
    private Integer goatHornMask;

    @Column(name = "goat_ram_phase")
    private String goatRamPhase;

    @Column(name = "goat_ram_cooldown_mc_ticks")
    private Integer goatRamCooldownMcTicks;

    @Column(name = "goat_ram_target_nickname")
    private String goatRamTargetNickname;

    @Column(name = "goat_ram_target_x") private Double goatRamTargetX;
    @Column(name = "goat_ram_target_z") private Double goatRamTargetZ;
    @Column(name = "goat_ram_run_up_x") private Double goatRamRunUpX;
    @Column(name = "goat_ram_run_up_z") private Double goatRamRunUpZ;
    @Column(name = "goat_ram_direction_x") private Double goatRamDirectionX;
    @Column(name = "goat_ram_direction_z") private Double goatRamDirectionZ;
    @Column(name = "goat_ram_prepare_mc_ticks") private Integer goatRamPrepareMcTicks;
    @Column(name = "goat_ram_distance") private Double goatRamDistance;
    @Column(name = "goat_ram_sequence") private Long goatRamSequence;
    @Column(name = "goat_ram_pending_block_id") private Integer goatRamPendingBlockId;
    @Column(name = "goat_ram_pending_x") private Integer goatRamPendingX;
    @Column(name = "goat_ram_pending_y") private Integer goatRamPendingY;
    @Column(name = "goat_ram_pending_z") private Integer goatRamPendingZ;
    @Column(name = "goat_ram_pending_prefer_left") private Boolean goatRamPendingPreferLeft;

    @Column(name = "sniffer_dig_phase") private String snifferDigPhase;
    @Column(name = "sniffer_dig_cooldown_mc_ticks") private Integer snifferDigCooldownMcTicks;
    @Column(name = "sniffer_dig_phase_mc_ticks") private Integer snifferDigPhaseMcTicks;
    @Column(name = "sniffer_dig_drop_delay_mc_ticks") private Integer snifferDigDropDelayMcTicks;
    @Column(name = "sniffer_dig_target_x") private Integer snifferDigTargetX;
    @Column(name = "sniffer_dig_target_y") private Integer snifferDigTargetY;
    @Column(name = "sniffer_dig_target_z") private Integer snifferDigTargetZ;
    @Column(name = "sniffer_dig_search_epoch") private Long snifferDigSearchEpoch;
    @Column(name = "sniffer_dig_sequence") private Long snifferDigSequence;
    @Column(name = "sniffer_pending_drop_sequence") private Long snifferPendingDropSequence;
    @Column(name = "sniffer_pending_drop_item") private Integer snifferPendingDropItem;

    @Column(name = "leash_holder_nickname")
    private String leashHolderNickname;

    @Column(name = "horse_max_health")
    private Double horseMaxHealth;

    @Column(name = "horse_speed")
    private Double horseSpeed;

    @Column(name = "horse_jump_strength")
    private Double horseJumpStrength;

    /**
     * [MOUNT] 상자 장착·화물·라마 힘·카펫. 이 열들이 없던 옛 행은 null 로 읽혀 상자 없음·빈
     * 화물·힘 0(= 생성 롤 유지)·장식 없음이 된다(말 계열 스탯과 같은 마이그레이션 계약).
     * 종 화이트리스트는 {@link #toSnapshot} 이 다시 건다 — 손상된 행이 소에게 화물을 붙일 수 없다.
     */
    @Column(name = "horse_chested")
    private Boolean horseChested;

    /**
     * 파괴적인 migration 없이 기존 schema를 읽기 위해서만 남겨 둔 구 화물 열이다. schema-3의
     * 유일한 권위는 HMI1이며 이 값은 쓰거나 외부에 노출하지 않는다.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "horse_cargo", length = 1_024, insertable = false, updatable = false)
    private String legacyHorseCargo;

    /** Strict HMI1 exact horse menu stacks, including item-component identity. */
    @Column(name = "horse_inventory_data", length = 16_384)
    private String horseInventoryData;

    @Column(name = "llama_strength")
    private Integer llamaStrength;

    @Column(name = "llama_carpet_color")
    private Integer llamaCarpetColor;

    /**
     * [MOUNT] 낙타 상태. 이 열들이 없던 옛 행은 null 로 읽혀 안장 없음·서 있음이 된다
     * (말 계열·열대어 variant 와 같은 마이그레이션 계약). 대시 쿨다운과 유휴 카운터는
     * 만료형 휘발 상태라 열을 갖지 않는다.
     */
    @Column(name = "camel_saddled")
    private Boolean camelSaddled;

    @Column(name = "camel_sitting")
    private Boolean camelSitting;

    /**
     * [HARNESS] 해피 가스트 하네스 상태. 이 열들이 없던 옛 행은 null 로 읽혀 하네스 없음이
     * 된다(낙타 열과 같은 마이그레이션 계약). 좌석 기수는 세션 수명을 넘지 못해 열이 없다.
     */
    @Column(name = "happy_ghast_harnessed")
    private Boolean happyGhastHarnessed;

    @Column(name = "happy_ghast_harness_color")
    private Integer happyGhastHarnessColor;

    /**
     * [NAUTILUS-MOUNT] 노틸러스 계열 안장·갑옷 상태. 이 열들이 없던 옛 행은 null 로 읽혀 안장 없음·
     * 갑옷 없음이 된다(낙타·하네스 열과 같은 마이그레이션 계약). 좌석 기수는 세션 수명을
     * 넘지 못해 열이 없다. 갑옷은 아이템 ID 가 아니라 <b>티어 서열</b>을 담는다 — 네더라이트가
     * 뒤에 붙어도 저장된 월드가 그대로 읽히게 하기 위해서다.
     */
    @Column(name = "nautilus_saddled")
    private Boolean nautilusSaddled;

    @Column(name = "nautilus_armor_tier")
    private Integer nautilusArmorTier;

    /** nullable columns keep pre-Allay rows compatible; null restores as an empty carried stack. */
    @Column(name = "allay_delivery_count")
    private Integer allayDeliveryCount;

    @Column(name = "allay_delivery_durability")
    private Integer allayDeliveryDurability;

    @Column(name = "turtle_gravid")
    private Boolean turtleGravid;

    @Column(name = "turtle_home_x")
    private Integer turtleHomeX;

    @Column(name = "turtle_home_y")
    private Integer turtleHomeY;

    @Column(name = "turtle_home_z")
    private Integer turtleHomeZ;

    @Column(name = "zombie_nautilus_charge_phase")
    private String zombieNautilusChargePhase;

    @Column(name = "zombie_nautilus_charge_cooldown_mc_ticks")
    private Integer zombieNautilusChargeCooldownMcTicks;

    @Column(name = "zombie_nautilus_charge_target_nickname")
    private String zombieNautilusChargeTargetNickname;

    @Column(name = "zombie_nautilus_charge_target_mob_id")
    private Long zombieNautilusChargeTargetMobId;

    @Column(name = "zombie_nautilus_charge_vx")
    private Double zombieNautilusChargeVx;

    @Column(name = "zombie_nautilus_charge_vy")
    private Double zombieNautilusChargeVy;

    @Column(name = "zombie_nautilus_charge_vz")
    private Double zombieNautilusChargeVz;

    @Column(name = "zombie_nautilus_charge_distance")
    private Double zombieNautilusChargeDistance;

    @Column(name = "zombie_nautilus_natural_target_cooldown_mc_ticks")
    private Integer zombieNautilusNaturalTargetCooldownMcTicks;

    @Column(name = "illusioner_invisibility_mc_ticks")
    private Integer illusionerInvisibilityMcTicks;

    @Column(name = "illusioner_mirror_cooldown_mc_ticks")
    private Integer illusionerMirrorCooldownMcTicks;

    @Column(name = "illusioner_blindness_cooldown_mc_ticks")
    private Integer illusionerBlindnessCooldownMcTicks;

    @Column(name = "illusioner_cast_mc_ticks")
    private Integer illusionerCastMcTicks;

    @Column(name = "illusioner_blind_target_key")
    private String illusionerBlindTargetKey;

    @Column(name = "companion_decision_settled")
    private Boolean companionDecisionSettled;

    @Column(name = "companion_decision_winner")
    private Boolean companionDecisionWinner;
    /** Owning raid instance for released raiders and their companions; null outside a raid. */
    @Column(name = "raid_id")
    private Long raidId;

    @Column(name = "raid_wave")
    private Integer raidWave;

    /** 좀비 주민 치료 카운트다운(20 TPS 게임 틱). null/0 이면 전환 중이 아니다(MOB.md §2). */
    @Column(name = "zombie_villager_conversion_mc_ticks")
    private Integer zombieVillagerConversionMcTicks;

    @Column(name = "zombie_villager_conversion_starter")
    private String zombieVillagerConversionStarter;

    /** 주민 사회 기억(gossip)과 gossip 타이머 한 줄. 주민이 아니거나 비어 있으면 null 이다. */
    @Column(name = "villager_social", length = 4_096)
    private String villagerSocial;

    private String villagerBiomeType;
    private String villagerProfession;
    private Integer villagerLevel;
    @Column(nullable = false) private boolean finalCarrierBinding;

    @Column(name = "sulfur_cube_pickup_cooldown_ticks")
    private Integer sulfurCubePickupCooldownTicks;

    @Column(name = "sulfur_cube_fuse_ticks")
    private Integer sulfurCubeFuseTicks;

    @Column(name = "sulfur_cube_max_fuse_ticks")
    private Integer sulfurCubeMaxFuseTicks;

    @Column(name = "sulfur_cube_from_bucket")
    private Boolean sulfurCubeFromBucket;

    /** [EC-MOBS] 셜커 부착면 · 아이템 액자 방향. 옛 행은 null(= 셜커 아래 · 액자 남쪽 기본은 복원이 정한다). */
    @Column(name = "attach_face")
    private Integer attachFace;

    /** [EC-MOBS] 셜커 raw peek · 아이템 액자 회전. */
    @Column(name = "attach_state")
    private Integer attachState;

    @Column(name = "held_item_enchantments")
    private Long heldItemEnchantments;

    @Column(name = "held_item_components", length = 4096)
    private String heldItemComponents;

    public WorldMob(Long worldId, MobPersistenceSnapshot snapshot) {
        this.worldId = worldId;
        apply(snapshot);
    }

    /** Existing JPA identity is retained while the authoritative runtime snapshot is replaced. */
    public void apply(MobPersistenceSnapshot snapshot) {
        apply(snapshot, false);
    }

    /**
     * A periodic checkpoint can contain several live menu mutations. Its caller must hold the
     * row's write lock; the last committed menu is the compare-and-set lineage, not the number
     * of gameplay generations captured between checkpoints. Direct settlements still use apply.
     */
    public void applyCheckpoint(MobPersistenceSnapshot expected, MobPersistenceSnapshot snapshot) {
        if (horseMenuType(type)) {
            if (expected == null || expected.getMobId() != mobId
                    || !Objects.equals(expected.getType(), type)
                    || !Objects.equals(expected.getHorseInventoryData(), horseInventoryData)
                    || expected.getHorseMenuPersistenceRevision() != horseMenuPersistenceRevision
                    || expected.getHorseEquipmentPersistenceRevision()
                            != horseEquipmentPersistenceRevision
                    || expected.getCargoPersistenceRevision() != cargoPersistenceRevision
                    || expected.isHorseChested() != Boolean.TRUE.equals(horseChested)) {
                throw new IllegalArgumentException("horse menu checkpoint baseline does not match storage");
            }
        }
        apply(snapshot, true);
    }

    private void apply(MobPersistenceSnapshot snapshot, boolean checkpoint) {
        if (snapshot == null) throw new IllegalArgumentException("mob snapshot required");
        requireValidMobId(snapshot.getMobId());
        MobRuntime.validateHorseInventoryPayload(snapshot);
        if (mobSchemaVersion != null && mobId == snapshot.getMobId()
                && horseMenuType(type) && horseMenuType(snapshot.getType())) {
            long incomingShared = snapshot.getHorseMenuPersistenceRevision();
            long incomingEquipment = snapshot.getHorseEquipmentPersistenceRevision();
            long incomingCargo = snapshot.getCargoPersistenceRevision();
            if (incomingShared < horseMenuPersistenceRevision
                    || incomingEquipment < horseEquipmentPersistenceRevision
                    || incomingCargo < cargoPersistenceRevision) {
                throw new IllegalArgumentException("stale mob cargo persistence revision tuple; "
                        + "horse inventory changed without advancing the authenticated generation");
            }
            if (horseMenuPersistenceRevision >= Long.MAX_VALUE - 1L) {
                throw new IllegalArgumentException("horse menu persistence revision is exhausted");
            }
            long nextShared = horseMenuPersistenceRevision + 1L;
            boolean payloadChanged = !Objects.equals(horseInventoryData,
                    snapshot.getHorseInventoryData());
            boolean tupleChanged = incomingShared != horseMenuPersistenceRevision
                    || incomingEquipment != horseEquipmentPersistenceRevision
                    || incomingCargo != cargoPersistenceRevision;
            boolean containerShapeChanged = Boolean.TRUE.equals(horseChested)
                    != snapshot.isHorseChested();
            if (!checkpoint && incomingShared > nextShared) {
                throw new IllegalArgumentException("horse menu persistence revision skipped a generation");
            }
            boolean localAdvanced = incomingEquipment > horseEquipmentPersistenceRevision
                    || incomingCargo > cargoPersistenceRevision;
            if ((payloadChanged || tupleChanged)
                    && (!checkpoint && incomingShared != horseMenuPersistenceRevision
                            && incomingShared != nextShared
                            || !localAdvanced && !(incomingShared == nextShared
                                    && containerShapeChanged))) {
                throw new IllegalArgumentException(
                        "horse menu payload and revision tuple are from mixed generations; "
                                + "horse inventory changed without advancing the authenticated generation");
            }
        }
        this.mobSchemaVersion = snapshot.getSchemaVersion();
        this.mobId = snapshot.getMobId();
        this.type = snapshot.getType();
        this.variant = snapshot.getVariant();
        this.posX = snapshot.getX();
        this.posY = snapshot.getY();
        this.posZ = snapshot.getZ();
        this.yaw = snapshot.getYaw();
        this.pitch = snapshot.getPitch();
        this.velocityX = snapshot.getVelocityX();
        this.velocityY = snapshot.getVelocityY();
        this.velocityZ = snapshot.getVelocityZ();
        this.healthPoints = snapshot.getHealthPoints();
        this.pickedUpEquipment = snapshot.isPickedUpEquipment();
        this.heldItem = snapshot.getHeldItem();
        this.heldItemDurability = snapshot.getHeldItemDurability();
        this.helmetItem = snapshot.getHelmetItem();
        this.helmetDurability = snapshot.getHelmetDurability();
        this.chestplateItem = snapshot.getChestplateItem();
        this.chestplateDurability = snapshot.getChestplateDurability();
        this.leggingsItem = snapshot.getLeggingsItem();
        this.leggingsDurability = snapshot.getLeggingsDurability();
        this.bootsItem = snapshot.getBootsItem();
        this.bootsDurability = snapshot.getBootsDurability();
        this.carriedBlock = snapshot.getCarriedBlock();
        this.sulfurCubePickupCooldownTicks = snapshot.getSulfurCubePickupCooldownTicks();
        this.sulfurCubeFuseTicks = snapshot.getSulfurCubeFuseTicks();
        this.sulfurCubeMaxFuseTicks = snapshot.getSulfurCubeMaxFuseTicks();
        this.sulfurCubeFromBucket = snapshot.isSulfurCubeFromBucket();
        this.attachFace = snapshot.getAttachFace();
        this.attachState = snapshot.getAttachState();
        this.heldItemEnchantments = snapshot.getHeldItemEnchantments();
        this.heldItemComponents = snapshot.getHeldItemComponents();
        this.ownerNickname = snapshot.getOwnerNickname();
        this.customName = snapshot.getCustomName();
        this.ageTicksRemaining = snapshot.getAgeTicksRemaining();
        this.breedingCooldownTicks = snapshot.getBreedingCooldownTicks();
        this.babyForm = snapshot.isBabyForm();
        this.airSupplyTicks = snapshot.getAirSupplyTicks();
        this.underwaterConversionTicks = snapshot.getUnderwaterConversionTicks();
        this.drownedCarrierRolled = snapshot.isDrownedCarrierRolled();
        this.drownedTridentCarrier = snapshot.isDrownedTridentCarrier();
        this.drownedShellCarrier = snapshot.isDrownedShellCarrier();
        this.poisonDartFrogFeedCooldownMcTicks =
                snapshot.getPoisonDartFrogFeedCooldownMcTicks();
        this.poisonDartFrogDefensiveTicks = snapshot.getPoisonDartFrogDefensiveTicks();
        this.poisonDartFrogContactCooldownTicks =
                snapshot.getPoisonDartFrogContactCooldownTicks();
        this.poisonDartFrogFeedSequence = snapshot.getPoisonDartFrogFeedSequence();
        this.poisonDartFrogPendingFeedSequence = snapshot.getPoisonDartFrogPendingFeedSequence();
        this.poisonDartFrogPendingFeedX = snapshot.getPoisonDartFrogPendingFeedX();
        this.poisonDartFrogPendingFeedY = snapshot.getPoisonDartFrogPendingFeedY();
        this.poisonDartFrogPendingFeedZ = snapshot.getPoisonDartFrogPendingFeedZ();
        this.drownedJockeyDecisionArmed = snapshot.isDrownedJockeyDecisionArmed();
        this.drownedJockeyDecisionSettled = snapshot.isDrownedJockeyDecisionSettled();
        this.drownedJockeyDecisionWinner = snapshot.isDrownedJockeyDecisionWinner();
        this.vehicleMobId = snapshot.getVehicleMobId() == 0L ? null : snapshot.getVehicleMobId();
        this.skeletonHorseTrapActive = snapshot.isSkeletonHorseTrapActive();
        this.skeletonHorseTrapAgeMcTicks = snapshot.getSkeletonHorseTrapAgeMcTicks();
        this.copperGolemOxidationAge = snapshot.getCopperGolemOxidationAge();
        this.copperGolemWaxed = snapshot.isCopperGolemWaxed();
        this.copperGolemPose = snapshot.getCopperGolemPose();
        this.copperGolemNextWeatheringMcTick = snapshot.getCopperGolemNextWeatheringMcTick();
        this.copperGolemStatuePending = snapshot.isCopperGolemStatuePending();
        this.beeHasStung = snapshot.isBeeHasStung();
        this.beeDeathAfterStingTicks = snapshot.getBeeDeathAfterStingTicks();
        this.beeHasNectar = snapshot.isBeeHasNectar();
        this.beeHomeX = snapshot.getBeeHomeX();
        this.beeHomeY = snapshot.getBeeHomeY();
        this.beeHomeZ = snapshot.getBeeHomeZ();
        this.beeHiveTicks = snapshot.getBeeHiveTicks();
        this.pufferPuffStage = snapshot.getPufferPuffStage();
        this.persistenceRequired = snapshot.isPersistenceRequired();
        this.slimeSize = snapshot.getSlimeSize();
        this.loveTicksRemaining = snapshot.getLoveTicksRemaining();
        this.vexSummonerMobId = snapshot.getVexSummonerMobId();
        this.vexLimitedLifeTicks = snapshot.getVexLimitedLifeTicks();
        this.angerTargetNickname = snapshot.getAngerTargetNickname();
        this.angerTicks = snapshot.getAngerTicks();
        this.ravagerAttackTicks = snapshot.getRavagerAttackTicks();
        this.ravagerStunnedTicks = snapshot.getRavagerStunnedTicks();
        this.ravagerRoarTicks = snapshot.getRavagerRoarTicks();
        this.armadilloShellState = snapshot.getArmadilloShellState();
        this.armadilloStateMcTicks = snapshot.getArmadilloStateMcTicks();
        this.armadilloDangerMcTicks = snapshot.getArmadilloDangerMcTicks();
        this.armadilloScuteTicks = snapshot.getArmadilloScuteTicks();
        this.dolphinMoistureTicks = snapshot.getDolphinMoistureTicks();
        this.dolphinDryDamageMcTicks = snapshot.getDolphinDryDamageMcTicks();
        this.dolphinGotFish = snapshot.isDolphinGotFish();
        this.dolphinTreasureX = snapshot.getDolphinTreasureX();
        this.dolphinTreasureY = snapshot.getDolphinTreasureY();
        this.dolphinTreasureZ = snapshot.getDolphinTreasureZ();
        this.illagerContext = snapshot.getIllagerContext();
        this.illagerContextIdentity = snapshot.getIllagerContextIdentity();
        this.illagerPolicyVersion = snapshot.getIllagerPolicyVersion();
        this.companionHandlerMobId = snapshot.getCompanionHandlerMobId();
        this.companionAppearanceLane = snapshot.getCompanionAppearanceLane();
        this.companionRebindCount = snapshot.getCompanionRebindCount();
        this.companionAbilityPhase = snapshot.getCompanionAbilityPhase();
        this.companionAbilityTicks = snapshot.getCompanionAbilityTicks();
        this.companionCooldownTicks = snapshot.getCompanionCooldownTicks();
        this.companionTargetNickname = snapshot.getCompanionTargetNickname();
        this.companionMarkTicks = snapshot.getCompanionMarkTicks();
        this.companionChargeDistance = snapshot.getCompanionChargeDistance();
        this.companionOrphanTicks = snapshot.getCompanionOrphanTicks();
        this.ocelotTrusting = snapshot.isOcelotTrusting();
        this.wolfSitting = snapshot.isWolfSitting();
        this.companionSitting = snapshot.isCompanionSitting();
        this.catCollarColor = snapshot.getCatCollarColor();
        this.wolfCollarColor = snapshot.getWolfCollarColor();
        this.wolfArmorDurability = snapshot.getWolfArmorDurability();
        this.tadpoleAgeMcTicks = snapshot.getTadpoleAgeMcTicks();
        this.turtleTravelingHome = snapshot.isTurtleTravelingHome();
        this.turtleEggDigMcTicks = snapshot.getTurtleEggDigMcTicks();
        this.turtleEggCount = snapshot.getTurtleEggCount();
        this.sheepColor = snapshot.getSheepColor();
        this.sheepSheared = snapshot.isSheepSheared();
        this.sheepEatMcTicks = snapshot.getSheepEatMcTicks();
        this.chickenEggMcTicks = snapshot.getChickenEggMcTicks();
        this.pigSaddled = snapshot.isPigSaddled();
        this.pigBoostMcTicks = snapshot.getPigBoostMcTicks();
        this.pigBoostTotalMcTicks = snapshot.getPigBoostTotalMcTicks();
        this.horseTamed = snapshot.isHorseTamed();
        this.horseTemper = snapshot.getHorseTemper();
        this.horseSaddled = snapshot.isHorseSaddled();
        this.horseArmorItem = Short.toUnsignedInt(snapshot.getHorseArmorItem());
        this.goatHornMask = snapshot.getGoatHornMask();
        this.goatRamPhase = snapshot.getGoatRamPhase();
        this.goatRamCooldownMcTicks = snapshot.getGoatRamCooldownMcTicks();
        this.goatRamTargetNickname = snapshot.getGoatRamTargetNickname();
        this.goatRamTargetX = snapshot.getGoatRamTargetX();
        this.goatRamTargetZ = snapshot.getGoatRamTargetZ();
        this.goatRamRunUpX = snapshot.getGoatRamRunUpX();
        this.goatRamRunUpZ = snapshot.getGoatRamRunUpZ();
        this.goatRamDirectionX = snapshot.getGoatRamDirectionX();
        this.goatRamDirectionZ = snapshot.getGoatRamDirectionZ();
        this.goatRamPrepareMcTicks = snapshot.getGoatRamPrepareMcTicks();
        this.goatRamDistance = snapshot.getGoatRamDistance();
        this.goatRamSequence = snapshot.getGoatRamSequence();
        this.goatRamPendingBlockId = snapshot.getGoatRamPendingBlockId();
        this.goatRamPendingX = snapshot.getGoatRamPendingX();
        this.goatRamPendingY = snapshot.getGoatRamPendingY();
        this.goatRamPendingZ = snapshot.getGoatRamPendingZ();
        this.goatRamPendingPreferLeft = snapshot.isGoatRamPendingPreferLeft();
        this.snifferDigPhase = snapshot.getSnifferDigPhase();
        this.snifferDigCooldownMcTicks = snapshot.getSnifferDigCooldownMcTicks();
        this.snifferDigPhaseMcTicks = snapshot.getSnifferDigPhaseMcTicks();
        this.snifferDigDropDelayMcTicks = snapshot.getSnifferDigDropDelayMcTicks();
        this.snifferDigTargetX = snapshot.getSnifferDigTargetX();
        this.snifferDigTargetY = snapshot.getSnifferDigTargetY();
        this.snifferDigTargetZ = snapshot.getSnifferDigTargetZ();
        this.snifferDigSearchEpoch = snapshot.getSnifferDigSearchEpoch();
        this.snifferDigSequence = snapshot.getSnifferDigSequence();
        this.snifferPendingDropSequence = snapshot.getSnifferPendingDropSequence();
        this.snifferPendingDropItem = Short.toUnsignedInt(snapshot.getSnifferPendingDropItem());
        this.leashHolderNickname = snapshot.getLeashHolderNickname();
        this.horseMaxHealth = snapshot.getHorseMaxHealth();
        this.horseSpeed = snapshot.getHorseSpeed();
        this.horseJumpStrength = snapshot.getHorseJumpStrength();
        this.horseChested = snapshot.isHorseChested();
        this.horseInventoryData = snapshot.getHorseInventoryData();
        this.horseMenuPersistenceRevision = snapshot.getHorseMenuPersistenceRevision();
        this.horseEquipmentPersistenceRevision = snapshot.getHorseEquipmentPersistenceRevision();
        this.cargoPersistenceRevision = snapshot.getCargoPersistenceRevision();
        this.llamaStrength = snapshot.getLlamaStrength();
        this.llamaCarpetColor = snapshot.getLlamaCarpetColor();
        this.camelSaddled = snapshot.isCamelSaddled();
        this.camelSitting = snapshot.isCamelSitting();
        this.happyGhastHarnessed = snapshot.isHappyGhastHarnessed();
        this.happyGhastHarnessColor = snapshot.getHappyGhastHarnessColor();
        this.nautilusSaddled = snapshot.isNautilusSaddled();
        this.nautilusArmorTier = snapshot.getNautilusArmorTier();
        this.allayDeliveryCount = snapshot.getAllayDeliveryCount();
        this.allayDeliveryDurability = snapshot.getAllayDeliveryDurability();
        this.turtleGravid = snapshot.isTurtleGravid();
        this.turtleHomeX = snapshot.getTurtleHomeX();
        this.turtleHomeY = snapshot.getTurtleHomeY();
        this.turtleHomeZ = snapshot.getTurtleHomeZ();
        this.creeperPowered = snapshot.isCreeperPowered();
        this.creeperIgnited = snapshot.isCreeperIgnited();
        this.trialEquipmentNoDrop = snapshot.isTrialEquipmentNoDrop();
        this.statusEffects = encodeStatusEffects(snapshot.getStatusEffects());
        this.equipmentComponents = snapshot.getEquipmentComponents();
        this.mooshroomStoredFlower = snapshot.getMooshroomStoredFlower();
        this.zombieNautilusChargePhase = snapshot.getZombieNautilusChargePhase();
        this.zombieNautilusChargeCooldownMcTicks =
                snapshot.getZombieNautilusChargeCooldownMcTicks();
        this.zombieNautilusChargeTargetNickname =
                snapshot.getZombieNautilusChargeTargetNickname();
        this.zombieNautilusChargeTargetMobId = snapshot.getZombieNautilusChargeTargetMobId();
        this.zombieNautilusChargeVx = snapshot.getZombieNautilusChargeVx();
        this.zombieNautilusChargeVy = snapshot.getZombieNautilusChargeVy();
        this.zombieNautilusChargeVz = snapshot.getZombieNautilusChargeVz();
        this.zombieNautilusChargeDistance = snapshot.getZombieNautilusChargeDistance();
        this.zombieNautilusNaturalTargetCooldownMcTicks =
                snapshot.getZombieNautilusNaturalTargetCooldownMcTicks();
        this.illusionerInvisibilityMcTicks = snapshot.getIllusionerInvisibilityMcTicks();
        this.illusionerMirrorCooldownMcTicks = snapshot.getIllusionerMirrorCooldownMcTicks();
        this.illusionerBlindnessCooldownMcTicks =
                snapshot.getIllusionerBlindnessCooldownMcTicks();
        this.illusionerCastMcTicks = snapshot.getIllusionerCastMcTicks();
        this.illusionerBlindTargetKey = snapshot.getIllusionerBlindTargetKey();
        this.companionDecisionSettled = snapshot.isCompanionDecisionSettled();
        this.companionDecisionWinner = snapshot.isCompanionDecisionWinner();
        this.raidId = snapshot.getRaidId() == 0L ? null : snapshot.getRaidId();
        this.raidWave = this.raidId == null ? null : snapshot.getRaidWave();
        this.zombieVillagerConversionMcTicks = snapshot.getZombieVillagerConversionMcTicks();
        this.zombieVillagerConversionStarter = snapshot.getZombieVillagerConversionStarter();
        this.villagerSocial = snapshot.getVillagerSocial();
        this.villagerBiomeType = snapshot.getVillagerBiomeType();
        this.villagerProfession = snapshot.getVillagerProfession();
        this.villagerLevel = snapshot.getVillagerLevel();
        this.finalCarrierBinding = snapshot.isFinalCarrierBinding();
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("mob name required");
        customName = name;
    }

    /** Applies one logical menu settlement without replacing later non-menu mob state. */
    public boolean applyHorseMenuSnapshotIfNext(MobPersistenceSnapshot snapshot, long revision) {
        MobRuntime.validateHorseInventoryPayload(snapshot);
        if (snapshot.getMobId() != mobId || !Objects.equals(snapshot.getType(), type)
                || !horseMenuType(type)
                || snapshot.getHorseMenuPersistenceRevision() != revision) {
            throw new IllegalArgumentException("horse menu target does not match row");
        }
        if (horseMenuPersistenceRevision >= Long.MAX_VALUE - 1L
                || revision != horseMenuPersistenceRevision + 1L) return false;
        if (snapshot.isHorseChested() != Boolean.TRUE.equals(horseChested)) {
            throw new IllegalArgumentException("horse menu settlement changed container shape");
        }
        var before = new com.gameexpert.engine.inventory.HorseMenuContainerAccess(
                new MobRuntime().restore(toSnapshot())).snapshot().stacks();
        var after = new com.gameexpert.engine.inventory.HorseMenuContainerAccess(
                new MobRuntime().restore(snapshot)).snapshot().stacks();
        if (before.length != after.length) {
            throw new IllegalArgumentException("horse menu settlement changed container shape");
        }
        int equipmentChanges = 0;
        int cargoChanges = 0;
        for (int slot = 0; slot < before.length; slot++) {
            if (!before[slot].equals(after[slot])) {
                if (slot < com.gameexpert.engine.inventory.HorseMenuContainerAccess.CARGO_START) {
                    equipmentChanges++;
                } else cargoChanges++;
            }
        }
        if (equipmentChanges + cargoChanges == 0
                || snapshot.getHorseEquipmentPersistenceRevision()
                        != Math.addExact(horseEquipmentPersistenceRevision, equipmentChanges)
                || snapshot.getCargoPersistenceRevision()
                        != Math.addExact(cargoPersistenceRevision, cargoChanges)) {
            throw new IllegalArgumentException("mixed horse menu persistence revision tuple");
        }
        horseInventoryData = snapshot.getHorseInventoryData();
        horseMenuPersistenceRevision = revision;
        horseEquipmentPersistenceRevision = snapshot.getHorseEquipmentPersistenceRevision();
        cargoPersistenceRevision = snapshot.getCargoPersistenceRevision();
        horseSaddled = snapshot.isHorseSaddled();
        horseArmorItem = Short.toUnsignedInt(snapshot.getHorseArmorItem());
        llamaCarpetColor = snapshot.getLlamaCarpetColor();
        camelSaddled = snapshot.isCamelSaddled();
        return true;
    }

    /** 몹 화물 조작이 낸 완전 스냅샷을 지연 쓰기 방지 세대와 함께 적용한다. */
    public boolean applyCargoSnapshotIfNewer(MobPersistenceSnapshot snapshot, long revision) {
        if (snapshot == null) throw new IllegalArgumentException("mob snapshot required");
        requireValidMobId(snapshot.getMobId());
        if (revision >= Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("mob cargo revision overflow");
        }
        if (revision <= 0L) {
            throw new IllegalArgumentException("mob cargo revision must be positive");
        }
        if (revision != snapshot.getCargoPersistenceRevision()) {
            throw new IllegalArgumentException("mob cargo revision does not match snapshot");
        }
        if (mobId != snapshot.getMobId()) {
            throw new IllegalArgumentException("mob cargo target does not match row");
        }
        if (!horseMenuType(type) || !horseMenuType(snapshot.getType())) {
            throw new IllegalArgumentException("mob cargo target must be a horse menu");
        }
        if (snapshot.getHorseMenuPersistenceRevision() != horseMenuPersistenceRevision
                || snapshot.getHorseEquipmentPersistenceRevision()
                        != horseEquipmentPersistenceRevision) {
            throw new IllegalArgumentException("mixed horse menu persistence revision tuple");
        }
        if (cargoPersistenceRevision >= Long.MAX_VALUE - 1L
                || revision != cargoPersistenceRevision + 1L) return false;
        MobRuntime.validateHorseInventoryPayload(snapshot);
        horseInventoryData = snapshot.getHorseInventoryData();
        horseMenuPersistenceRevision = snapshot.getHorseMenuPersistenceRevision();
        horseEquipmentPersistenceRevision = snapshot.getHorseEquipmentPersistenceRevision();
        cargoPersistenceRevision = revision;
        return true;
    }
    public long getCargoPersistenceRevision() { return cargoPersistenceRevision; }

    /** Rejects identities that cannot be allocated or safely advanced by the runtime sequence. */
    public static void requireValidMobId(long mobId) {
        if (mobId <= 0L || mobId > MAX_MOB_ID) {
            throw new IllegalArgumentException("mob id must be positive and less than Long.MAX_VALUE");
        }
    }

    /**
     * Species whitelist for persisted raid membership, mirroring
     * {@code MobRelationshipPolicy.isRaidMembershipType}. The entity only stores type names, so the
     * gate is repeated here to keep a corrupted row from restoring a raid id onto a cow.
     */
    private static boolean raidMembershipType(String type) {
        if (type == null) return false;
        return switch (type) {
            case "PILLAGER", "VINDICATOR", "EVOKER", "ILLUSIONER", "RAVAGER", "WITCH",
                    "STANDARD_BEARER", "WEB_TRAPPER", "BREACHER", "DEMOLISHER", "BUILDER",
                    "BRIARBACK", "GLOAMKITE" -> true;
            default -> false;
        };
    }

    /**
     * [MOUNT] 말 계열 화이트리스트. {@code HorseRules.isHorseFamily} 와 같은 집합이며, 엔티티는
     * 종 이름만 들고 있으므로 {@code raidMembershipType} 과 같은 이유로 여기서 한 번 더 건다.
     */
    private static boolean horseFamilyType(String type) {
        if (type == null) return false;
        return switch (type) {
            // 좀비 말도 같은 상태(길들이기·안장·개체 스탯)를 갖는다. 빼면 재시작마다
            // 안장을 잃는다(HorseRules.isHorseFamily 와 같은 집합이어야 한다).
            case "HORSE", "DONKEY", "MULE", "LLAMA", "ZOMBIE_HORSE" -> true;
            default -> false;
        };
    }

    private static boolean horseMenuType(String type) {
        return horseFamilyType(type) || "CAMEL".equals(type)
                || "SKELETON_HORSE".equals(type) || "TRADER_LLAMA".equals(type);
    }

    /** 상자를 달 수 있는 종. {@code ChestedHorseRules.chestable} 과 같은 집합이다. */
    private static boolean chestableType(String type) {
        if (type == null) return false;
        return switch (type) {
            case "DONKEY", "MULE", "LLAMA", "TRADER_LLAMA" -> true;
            default -> false;
        };
    }

    /** 라마 힘은 [1,5] 이거나 "옛 행"을 뜻하는 0 이다. 그 밖의 값은 손상이라 0 으로 읽는다. */
    private static int clampLlamaStrengthOrZero(Integer strength) {
        if (strength == null || strength < 1 || strength > 5) return 0;
        return strength;
    }

    /** 카펫 색은 [0,15] 이거나 장식 없음(-1)이다. 그 밖의 값은 손상이라 장식 없음으로 읽는다. */
    private static int carpetColorOrNone(Integer color) {
        if (color == null || color < 0 || color > 15) return -1;
        return color;
    }

    public MobPersistenceSnapshot toSnapshot() {
        requireValidMobId(mobId);
        if (mobSchemaVersion == null
                || mobSchemaVersion != MobPersistenceSnapshot.SCHEMA_VERSION) {
            throw new IllegalStateException("unsupported persisted mob schema version");
        }
        if (horseMenuType(type)
                && (horseInventoryData == null || horseInventoryData.isBlank())) {
            throw new IllegalStateException("horse inventory current-schema payload required");
        }
        if (!"DROWNED".equals(type) && drownedShellCarrier) {
            throw new IllegalStateException("non-Drowned mob has persisted shell carrier state");
        }
        boolean armadillo = "ARMADILLO".equals(type);
        boolean dolphin = "DOLPHIN".equals(type);
        boolean pufferfish = "PUFFERFISH".equals(type);
        boolean wolf = "WOLF".equals(type);
        boolean tadpole = "TADPOLE".equals(type);
        boolean sheep = "SHEEP".equals(type);
        boolean chicken = "CHICKEN".equals(type);
        boolean pig = "PIG".equals(type);
        // [MOUNT] 말 계열 전체가 길들이기·안장·개체 스탯을 갖는다(HorseRules.isHorseFamily 와 같은
        // 집합). 말 하나로 좁히면 당나귀·노새·라마가 재시작마다 길들이기를 잃는다.
        boolean horse = horseFamilyType(type);
        boolean traderLlama = "TRADER_LLAMA".equals(type);
        boolean chestable = chestableType(type);
        boolean llama = "LLAMA".equals(type) || "TRADER_LLAMA".equals(type);
        boolean chested = chestable && Boolean.TRUE.equals(horseChested);
        boolean camel = "CAMEL".equals(type);
        boolean allay = "ALLAY".equals(type);
        boolean turtle = "TURTLE".equals(type);
        boolean happyGhast = "HAPPY_GHAST".equals(type);
        boolean nautilus = "NAUTILUS".equals(type);
        boolean zombieNautilus = "ZOMBIE_NAUTILUS".equals(type);
        boolean illusioner = "ILLUSIONER".equals(type);
        boolean zombieVillager = "ZOMBIE_VILLAGER".equals(type);
        boolean mooshroom = "MOOSHROOM".equals(type);
        boolean raider = raidMembershipType(type);
        MobPersistenceSnapshot snapshot = new MobPersistenceSnapshot(
                mobId, type, variant, posX, posY, posZ,
                healthPoints, pickedUpEquipment,
                heldItem, heldItemDurability,
                helmetItem, helmetDurability,
                chestplateItem, chestplateDurability,
                leggingsItem, leggingsDurability,
                bootsItem, bootsDurability,
                carriedBlock,
                ownerNickname, customName,
                ageTicksRemaining, breedingCooldownTicks, babyForm,
                airSupplyTicks, underwaterConversionTicks,
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                persistenceRequired, slimeSize, loveTicksRemaining,
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTargetNickname, angerTicks,
                zeroIfNull(ravagerAttackTicks), zeroIfNull(ravagerStunnedTicks),
                zeroIfNull(ravagerRoarTicks),
                armadillo ? defaultArmadilloShellState() : null,
                armadillo ? zeroIfNull(armadilloStateMcTicks) : 0,
                armadillo ? zeroIfNull(armadilloDangerMcTicks) : 0,
                armadillo ? defaultArmadilloScuteTicks() : 0,
                dolphin ? defaultDolphinMoistureTicks() : 0,
                dolphin ? zeroIfNull(dolphinDryDamageMcTicks) : 0,
                dolphin && Boolean.TRUE.equals(dolphinGotFish),
                dolphin ? zeroIfNull(dolphinTreasureX) : 0,
                dolphin ? zeroIfNull(dolphinTreasureY) : 0,
                dolphin ? zeroIfNull(dolphinTreasureZ) : 0,
                illagerContext,
                longZeroIfNull(illagerContextIdentity),
                zeroIfNull(illagerPolicyVersion),
                longZeroIfNull(companionHandlerMobId),
                longZeroIfNull(companionAppearanceLane),
                zeroIfNull(companionRebindCount),
                companionAbilityPhase == null ? "IDLE" : companionAbilityPhase,
                zeroIfNull(companionAbilityTicks), zeroIfNull(companionCooldownTicks),
                companionTargetNickname, zeroIfNull(companionMarkTicks),
                companionChargeDistance == null ? 0.0 : companionChargeDistance,
                zeroIfNull(companionOrphanTicks), Boolean.TRUE.equals(ocelotTrusting),
                Boolean.TRUE.equals(wolfSitting),
                wolf ? wolfCollarColor == null ? 14 : wolfCollarColor : -1,
                wolf ? zeroIfNull(wolfArmorDurability) : 0,
                tadpole ? zeroIfNull(tadpoleAgeMcTicks) : 0,
                zombieNautilus && zombieNautilusChargePhase != null
                        ? zombieNautilusChargePhase : "IDLE",
                zombieNautilus ? zeroIfNull(zombieNautilusChargeCooldownMcTicks) : 0,
                zombieNautilus ? zombieNautilusChargeTargetNickname : null,
                zombieNautilus ? longZeroIfNull(zombieNautilusChargeTargetMobId) : 0,
                zombieNautilus && zombieNautilusChargeVx != null
                        ? zombieNautilusChargeVx : 0.0,
                zombieNautilus && zombieNautilusChargeVy != null
                        ? zombieNautilusChargeVy : 0.0,
                zombieNautilus && zombieNautilusChargeVz != null
                        ? zombieNautilusChargeVz : 0.0,
                zombieNautilus && zombieNautilusChargeDistance != null
                        ? zombieNautilusChargeDistance : 0.0,
                zombieNautilus
                        ? zeroIfNull(zombieNautilusNaturalTargetCooldownMcTicks) : 0,
                raider ? longZeroIfNull(raidId) : 0L,
                raider && raidId != null ? zeroIfNull(raidWave) : 0)
                .withCompanionState(Boolean.TRUE.equals(companionSitting),
                        catCollarColor == null ? "CAT".equals(type) ? 14 : -1 : catCollarColor)
                .withBeeHiveTicks("BEE".equals(type) ? zeroIfNull(beeHiveTicks) : 0)
                .withPufferPuffStage(pufferfish
                        ? Math.max(0, Math.min(2, zeroIfNull(pufferPuffStage))) : 0)
                .withIllusionerAndCompanionDecision(
                        illusioner ? zeroIfNull(illusionerInvisibilityMcTicks) : 0,
                        illusioner ? zeroIfNull(illusionerMirrorCooldownMcTicks) : 0,
                        illusioner ? zeroIfNull(illusionerBlindnessCooldownMcTicks) : 0,
                        illusioner ? zeroIfNull(illusionerCastMcTicks) : 0,
                        illusioner ? illusionerBlindTargetKey : null,
                        Boolean.TRUE.equals(companionDecisionSettled),
                        Boolean.TRUE.equals(companionDecisionSettled)
                                && Boolean.TRUE.equals(companionDecisionWinner))
                .withZombieVillagerConversion(
                        zombieVillager ? zeroIfNull(zombieVillagerConversionMcTicks) : 0,
                        zombieVillager && zeroIfNull(zombieVillagerConversionMcTicks) > 0
                                ? zombieVillagerConversionStarter : null)
                // 손상된 행이 소에게 주민 평판을 복원하지 못하도록 종 화이트리스트를 다시 건다.
                .withVillagerSocial("VILLAGER".equals(type) ? villagerSocial : null)
                .withFinalCarrierFacts(variant, yaw, pitch, velocityX, velocityY, velocityZ,
                        villagerDataType(type) ? villagerBiomeType : null,
                        villagerDataType(type) ? villagerProfession : null,
                        villagerDataType(type) ? zeroIfNull(villagerLevel) : 0)
                .withFinalCarrierBinding(finalCarrierBinding)
                // 손상된 행이 다른 종에게 양털·산란·안장 상태를 흘리지 않도록 종을 다시 건다.
                .withFarmAnimalState(
                        sheep ? sheepColor == null ? -1 : sheepColor : -1,
                        // [CONTAINER-MENUS] The same column carries the snow golem's missing
                        // pumpkin and the bogged's sheared head.
                        (sheep || "SNOW_GOLEM".equals(type) || "BOGGED".equals(type))
                                && Boolean.TRUE.equals(sheepSheared),
                        sheep ? zeroIfNull(sheepEatMcTicks) : 0,
                        chicken ? zeroIfNull(chickenEggMcTicks) : 0,
                        pig && Boolean.TRUE.equals(pigSaddled),
                        pig ? zeroIfNull(pigBoostMcTicks) : 0,
                        pig ? zeroIfNull(pigBoostTotalMcTicks) : 0)
                // 손상된 행이 다른 종을 차지드로 만들지 않도록 종을 다시 건다.
                .withCreeperPowered(
                        "CREEPER".equals(type) && Boolean.TRUE.equals(creeperPowered))
                .withCreeperIgnited(
                        "CREEPER".equals(type) && Boolean.TRUE.equals(creeperIgnited))
                .withTrialEquipmentNoDrop(Boolean.TRUE.equals(trialEquipmentNoDrop))
                .withStatusEffects(decodeStatusEffects(statusEffects))
                .withEquipmentComponents(equipmentComponents)
                .withMooshroomStoredFlower(mooshroom
                        ? validMooshroomFlowerOrNone(mooshroomStoredFlower) : 0)
                // 손상된 행이 다른 종에게 길들이기·안장·말 스탯을 흘리지 않도록 종을 다시 건다.
                .withHorseState(
                        (horse || traderLlama) && Boolean.TRUE.equals(horseTamed),
                        horse ? zeroIfNull(horseTemper) : 0,
                        horse && Boolean.TRUE.equals(horseSaddled),
                        horse ? zeroIfNull(horseMaxHealth) : 0.0,
                        horse ? zeroIfNull(horseSpeed) : 0.0,
                        horse ? zeroIfNull(horseJumpStrength) : 0.0)
                .withHorseArmorItem((short) ("HORSE".equals(type)
                        && validHorseArmorItem(horseArmorItem) ? horseArmorItem : 0))
                .withGoatHornMask("GOAT".equals(type)
                        ? goatHornMask == null ? null : Math.max(0, Math.min(3, goatHornMask))
                        : null)
                .withGoatRamState(
                        "GOAT".equals(type) && goatRamPhase != null ? goatRamPhase : "IDLE",
                        "GOAT".equals(type) && goatRamCooldownMcTicks != null
                                ? Math.max(-1, Math.min(6_000, goatRamCooldownMcTicks)) : -1,
                        "GOAT".equals(type) ? goatRamTargetNickname : null,
                        "GOAT".equals(type) ? zeroIfNull(goatRamTargetX) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamTargetZ) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamRunUpX) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamRunUpZ) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamDirectionX) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamDirectionZ) : 0,
                        "GOAT".equals(type) ? Math.max(0, Math.min(20,
                                zeroIfNull(goatRamPrepareMcTicks))) : 0,
                        "GOAT".equals(type) ? Math.max(0, zeroIfNull(goatRamDistance)) : 0,
                        "GOAT".equals(type) ? Math.max(0L, longZeroIfNull(goatRamSequence)) : 0,
                        "GOAT".equals(type) ? Math.max(0, zeroIfNull(goatRamPendingBlockId)) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamPendingX) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamPendingY) : 0,
                        "GOAT".equals(type) ? zeroIfNull(goatRamPendingZ) : 0,
                        "GOAT".equals(type) && Boolean.TRUE.equals(goatRamPendingPreferLeft))
                .withSnifferDigState(
                        "SNIFFER".equals(type) && snifferDigPhase != null
                                ? snifferDigPhase : "IDLE",
                        "SNIFFER".equals(type) ? Math.max(0, Math.min(9_600,
                                zeroIfNull(snifferDigCooldownMcTicks))) : 0,
                        "SNIFFER".equals(type) ? Math.max(0,
                                zeroIfNull(snifferDigPhaseMcTicks)) : 0,
                        "SNIFFER".equals(type) ? Math.max(0, Math.min(120,
                                zeroIfNull(snifferDigDropDelayMcTicks))) : 0,
                        "SNIFFER".equals(type) ? zeroIfNull(snifferDigTargetX) : 0,
                        "SNIFFER".equals(type) ? zeroIfNull(snifferDigTargetY) : 0,
                        "SNIFFER".equals(type) ? zeroIfNull(snifferDigTargetZ) : 0,
                        "SNIFFER".equals(type) ? Math.max(0L,
                                longZeroIfNull(snifferDigSearchEpoch)) : 0,
                        "SNIFFER".equals(type) ? Math.max(0L,
                                longZeroIfNull(snifferDigSequence)) : 0,
                        "SNIFFER".equals(type) ? Math.max(0L,
                                longZeroIfNull(snifferPendingDropSequence)) : 0,
                        (short) ("SNIFFER".equals(type)
                                ? Math.max(0, zeroIfNull(snifferPendingDropItem)) : 0))
                .withLeashHolderNickname(leashHolderNickname)
                // 손상된 행이 상자를 달 수 없는 종에게 상자·힘·카펫을 흘리지 않도록 종을 다시 건다.
                .withHorseEquipment(
                        chested,
                        llama ? clampLlamaStrengthOrZero(llamaStrength) : 0,
                        llama ? carpetColorOrNone(llamaCarpetColor) : -1)
                // 손상된 행이 다른 종에게 낙타 안장·자세를 흘리지 않도록 종을 다시 건다.
                .withCamelState(
                        camel && Boolean.TRUE.equals(camelSaddled),
                        camel && Boolean.TRUE.equals(camelSitting))
                .withHorseInventoryDataAndRevisions(
                        horseMenuType(type) ? horseInventoryData : null,
                        horseMenuType(type) ? horseMenuPersistenceRevision : 0,
                        horseMenuType(type) ? horseEquipmentPersistenceRevision : 0,
                        horseMenuType(type) ? cargoPersistenceRevision : 0)
                // 손상된 행이 다른 종에게 하네스를 흘리지 않도록 종을 다시 건다. 색이 범위
                // 밖이거나 하네스 없이 색만 남은 행은 하네스 자체를 버린다(없는 색을 지어내지
                // 않는 것이 스냅샷 생성자의 계약이다).
                .withHappyGhastState(
                        happyGhast && Boolean.TRUE.equals(happyGhastHarnessed)
                                && validHarnessColor(happyGhastHarnessColor),
                        happyGhast && Boolean.TRUE.equals(happyGhastHarnessed)
                                && validHarnessColor(happyGhastHarnessColor)
                                ? happyGhastHarnessColor : -1)
                // 손상된 행이 다른 종에게 안장·갑옷을 흘리지 않도록 종을 다시 건다. 티어가
                // 범위 밖인 행은 갑옷 자체를 버린다(없는 티어를 지어내지 않는다 — 하네스 색과
                // 같은 판단이다).
                .withNautilusMountState(
                        (nautilus || zombieNautilus) && Boolean.TRUE.equals(nautilusSaddled),
                        nautilus || zombieNautilus
                                ? validNautilusArmorTierOrNone(nautilusArmorTier) : -1)
                .withDrownedShellCarrier(
                        "DROWNED".equals(type) && drownedShellCarrier)
                .withPoisonDartFrogState(
                        "POISON_DART_FROG".equals(type)
                                ? zeroIfNull(poisonDartFrogFeedCooldownMcTicks) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? zeroIfNull(poisonDartFrogDefensiveTicks) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? zeroIfNull(poisonDartFrogContactCooldownTicks) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? longZeroIfNull(poisonDartFrogFeedSequence) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? longZeroIfNull(poisonDartFrogPendingFeedSequence) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? zeroIfNull(poisonDartFrogPendingFeedX) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? zeroIfNull(poisonDartFrogPendingFeedY) : 0,
                        "POISON_DART_FROG".equals(type)
                                ? zeroIfNull(poisonDartFrogPendingFeedZ) : 0)
                .withDrownedJockeyState(
                        "DROWNED".equals(type) && Boolean.TRUE.equals(drownedJockeyDecisionArmed),
                        !"DROWNED".equals(type)
                                || !Boolean.FALSE.equals(drownedJockeyDecisionSettled),
                        "DROWNED".equals(type) && Boolean.TRUE.equals(drownedJockeyDecisionWinner),
                        "DROWNED".equals(type)
                                && Boolean.TRUE.equals(drownedJockeyDecisionSettled)
                                && Boolean.TRUE.equals(drownedJockeyDecisionWinner)
                                && vehicleMobId != null ? vehicleMobId : 0L)
                .withSkeletonTrapRiderVehicle(
                        "SKELETON".equals(type) && vehicleMobId != null ? vehicleMobId : 0L)
                .withSkeletonHorseTrapState(
                        "SKELETON_HORSE".equals(type) && skeletonHorseTrapActive,
                        "SKELETON_HORSE".equals(type) ? skeletonHorseTrapAgeMcTicks : 0)
                .withCopperGolemState(
                        "COPPER_GOLEM".equals(type) ? copperGolemOxidationAge : 0,
                        "COPPER_GOLEM".equals(type) && copperGolemWaxed,
                        "COPPER_GOLEM".equals(type) ? copperGolemPose : 0,
                        "COPPER_GOLEM".equals(type) ? copperGolemNextWeatheringMcTick : -1L,
                        "COPPER_GOLEM".equals(type) && copperGolemStatuePending)
                .withAllayDelivery(
                        allay ? zeroIfNull(allayDeliveryCount) : 0,
                        allay ? zeroIfNull(allayDeliveryDurability) : 0)
                .withTurtleEggState(
                        turtle && Boolean.TRUE.equals(turtleGravid),
                        turtle ? turtleHomeX == null ? (int) Math.floor(posX) : turtleHomeX : 0,
                        turtle ? turtleHomeY == null ? (int) Math.floor(posY) : turtleHomeY : 0,
                        turtle ? turtleHomeZ == null ? (int) Math.floor(posZ) : turtleHomeZ : 0,
                        turtle && Boolean.TRUE.equals(turtleTravelingHome),
                        turtle ? Math.max(0, zeroIfNull(turtleEggDigMcTicks)) : 0,
                        turtle ? Math.max(0, zeroIfNull(turtleEggCount)) : 0)
                .withSulfurCubeState(
                        "SULFUR_CUBE".equals(type)
                                ? zeroIfNull(sulfurCubePickupCooldownTicks) : 0,
                        "SULFUR_CUBE".equals(type)
                                ? sulfurCubeFuseTicks == null ? -1 : sulfurCubeFuseTicks : -1,
                        "SULFUR_CUBE".equals(type)
                                ? sulfurCubeMaxFuseTicks == null ? -1 : sulfurCubeMaxFuseTicks : -1,
                        "SULFUR_CUBE".equals(type) && Boolean.TRUE.equals(sulfurCubeFromBucket))
                .withAttachedState(
                        "HANGING_MAW".equals(type) || "SHULKER".equals(type) || "ITEM_FRAME".equals(type)
                                ? attachFace == null ? ("ITEM_FRAME".equals(type) ? 3 : 0) : attachFace : 0,
                        "HANGING_MAW".equals(type) || "SHULKER".equals(type) || "ITEM_FRAME".equals(type) ? zeroIfNull(attachState) : 0,
                        "ITEM_FRAME".equals(type) && heldItemEnchantments != null ? heldItemEnchantments : 0L,
                        "ITEM_FRAME".equals(type) ? heldItemComponents : null);
        MobRuntime.validateHorseInventoryPayload(snapshot);
        return snapshot;
    }

    /**
     * [NAUTILUS-MOUNT] 저장된 갑옷 티어가 등록된 다섯 티어(0..4) 안이면 그대로, 아니면 -1(없음).
     * null 인 옛 행도 -1 이다.
     */
    private static int validNautilusArmorTierOrNone(Integer tier) {
        return tier != null && tier >= 0 && tier <= 4 ? tier : -1;
    }

    private static boolean validHorseArmorItem(Integer item) {
        return item != null && item != 0
                && com.gameexpert.engine.mob.HorseRules.isArmorItem((short) item.intValue());
    }

    private static int validMooshroomFlowerOrNone(Integer flower) {
        return flower != null && com.gameexpert.engine.SuspiciousStewRules.isStewFlower(flower)
                ? flower : 0;
    }

    private static boolean villagerDataType(String type) {
        return "VILLAGER".equals(type) || "ZOMBIE_VILLAGER".equals(type);
    }

    /** [HARNESS] 저장된 하네스 색이 MC DyeColor 범위(0..15) 안인가. null 은 거짓이다. */
    private static boolean validHarnessColor(Integer color) {
        return color != null && color >= 0 && color <= 15;
    }

    private String defaultArmadilloShellState() {
        return armadilloShellState == null ? "IDLE" : armadilloShellState;
    }

    private int defaultArmadilloScuteTicks() {
        if (armadilloScuteTicks != null) return armadilloScuteTicks;
        return Armadillo.initialScuteMcTicks(mobId);
    }

    private int defaultDolphinMoistureTicks() {
        return dolphinMoistureTicks == null ? 2_400 : dolphinMoistureTicks;
    }

    private static double zeroIfNull(Double value) {
        return value == null ? 0.0 : value;
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static long longZeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    /** [GLOWING] 활성 상태이상 직렬화. 빈 목록은 null(옛 행과 같은 모양)이다. */
    static String encodeStatusEffects(
            java.util.List<com.gameexpert.engine.effect.StatusEffects.PersistentEffect> effects) {
        if (effects == null || effects.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (com.gameexpert.engine.effect.StatusEffects.PersistentEffect effect : effects) {
            if (out.length() > 0) out.append(';');
            out.append(effect.effect().name()).append(':').append(effect.amplifier()).append(':')
                    .append(effect.remainingMcTicks()).append(':').append(effect.periodAccumMcTicks());
        }
        return out.toString();
    }

    /** [GLOWING] 활성 상태이상 역직렬화. 알 수 없는 종류·손상·중복 항목은 버린다(옛 저장분 호환). */
    static java.util.List<com.gameexpert.engine.effect.StatusEffects.PersistentEffect> decodeStatusEffects(
            String encoded) {
        if (encoded == null || encoded.isBlank()) return java.util.List.of();
        java.util.List<com.gameexpert.engine.effect.StatusEffects.PersistentEffect> out = new java.util.ArrayList<>();
        java.util.EnumSet<com.gameexpert.engine.effect.StatusEffect> seen =
                java.util.EnumSet.noneOf(com.gameexpert.engine.effect.StatusEffect.class);
        for (String entry : encoded.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length != 4) continue;
            try {
                com.gameexpert.engine.effect.StatusEffect effect =
                        com.gameexpert.engine.effect.StatusEffect.valueOf(parts[0]);
                if (!seen.add(effect)) continue;
                out.add(new com.gameexpert.engine.effect.StatusEffects.PersistentEffect(effect,
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            } catch (IllegalArgumentException invalid) {
                // 알 수 없는 종류·범위 밖 값은 그 항목만 버린다.
            }
        }
        out.sort(java.util.Comparator.comparingInt(effect -> effect.effect().ordinal()));
        return java.util.List.copyOf(out);
    }
}
