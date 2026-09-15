package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.engine.effect.PotionRules;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.raid.ProceduralRaidSchedule;
import com.gameexpert.engine.raid.RaidLedger;
import com.gameexpert.engine.raid.RaidPlanGenerator;
import com.gameexpert.engine.raid.RaidRewardReceipt;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.trial.TrialVaultContract;
import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.mob.BrimstoneLurkerRules;
import com.gameexpert.engine.mob.BucketMobPayloadCodec;
import com.gameexpert.engine.mob.Bee;
import com.gameexpert.engine.mob.TemptationRules;
import com.gameexpert.engine.mob.NautilusMountRules;
import com.gameexpert.engine.mob.Drowned;
import com.gameexpert.engine.mob.Enderman;
import com.gameexpert.engine.mob.Armadillo;
import com.gameexpert.engine.mob.Allay;
import com.gameexpert.engine.mob.Creeper;
import com.gameexpert.engine.mob.Dolphin;
import com.gameexpert.engine.mob.EquipmentDropRules;
import com.gameexpert.engine.mob.FarmAnimalRules;
import com.gameexpert.engine.mob.CamelRules;
import com.gameexpert.engine.mob.CamelHuskRules;
import com.gameexpert.engine.mob.HappyGhastRules;
import com.gameexpert.engine.mob.CarrionBoarRules;
import com.gameexpert.engine.mob.CarrionCrowRules;
import com.gameexpert.engine.mob.CarrionStagRules;
import com.gameexpert.engine.mob.ParchedRules;
import com.gameexpert.engine.mob.ZombieBearRules;
import com.gameexpert.engine.mob.ZombieChickenRules;
import com.gameexpert.engine.mob.ZombieCowRules;
import com.gameexpert.engine.mob.ZombieFoxRules;
import com.gameexpert.engine.mob.ZombieGoatRules;
import com.gameexpert.engine.mob.ZombiePigRules;
import com.gameexpert.engine.mob.ZombieSheepRules;
import com.gameexpert.engine.mob.ZombieHorseRules;
import com.gameexpert.engine.mob.ZombieWolfRules;
import com.gameexpert.engine.mob.ChestedHorseRules;
import com.gameexpert.engine.mob.HorseRules;
import com.gameexpert.engine.mob.LlamaRules;
import com.gameexpert.engine.mob.MobMountRules;
import com.gameexpert.engine.mob.MooshroomRules;
import com.gameexpert.engine.mob.MobEffectRules;
import com.gameexpert.engine.mob.ProjectileEffect;
import com.gameexpert.engine.mob.Slime;
import com.gameexpert.engine.mob.SulfurCubeRules;
import com.gameexpert.engine.mob.SulfurCube;
import com.gameexpert.engine.mob.JavaMobRandom;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobEvent;
import com.gameexpert.engine.mob.MobRandom;
import com.gameexpert.engine.mob.GlitchSignalPolicy;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.terrain.mc.feature.Mc263BeeSidecarPlan;
import com.gameexpert.engine.mob.MobPhysics;
import com.gameexpert.engine.mob.MobRelationshipPolicy;
import com.gameexpert.engine.mob.MobState;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.EnderDragon;
import com.gameexpert.engine.mob.MobWorldView;
import com.gameexpert.engine.mob.MobSpawner;
import com.gameexpert.engine.mob.PlayerSnapshot;
import com.gameexpert.engine.mob.Piglin;
import com.gameexpert.engine.mob.PiglinBarterRules;
import com.gameexpert.engine.mob.ProjectileSim;
import com.gameexpert.engine.mob.Ravager;
import com.gameexpert.engine.mob.RottenLeatherDropRules;
import com.gameexpert.engine.mob.Silverfish;
import com.gameexpert.engine.mob.SpawnRequest;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.engine.mob.villager.VillagerJobClaimLedger;
import com.gameexpert.engine.mob.villager.VillagerPoiIndex;
import com.gameexpert.engine.mob.villager.VillagerJobSitePolicy;
import com.gameexpert.mob.dto.StructureOccupantClaimSnapshot;
import com.gameexpert.mob.dto.VillagerJobClaimSnapshot;
import com.gameexpert.mob.dto.VillagerBedClaimSnapshot;
import com.gameexpert.mob.dto.VillagerSocietySnapshot;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.engine.mob.GuardianSpawnRules;
import com.gameexpert.engine.mob.FrogPoisonConversionRules;
import com.gameexpert.engine.mob.origin.CatVillageOriginRules;
import com.gameexpert.engine.mob.origin.SkeletonHorseTrapOriginRules;
import com.gameexpert.frog.persistence.dto.FrogConversionIntent;
import com.gameexpert.frog.persistence.service.FrogColonyPersistenceService;
import com.gameexpert.engine.mob.OceanMonumentOccupants;
import com.gameexpert.engine.structure.AcceptedStructureSite;
import com.gameexpert.engine.structure.StructureAabb;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.engine.sculk.SculkCatalystRules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages;
import com.gameexpert.ws.dto.WsMessages.MobSpawnDto;
import com.gameexpert.ws.dto.WsMessages.MobUpdateDto;
import com.gameexpert.ws.dto.WsMessages.ProjectilePos;
import com.gameexpert.ws.dto.WsMessages.ProjectileRemoval;
import com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot;

/**
 * 몹/투사체/전투를 서버 월드 런타임에 붙이는 통합 시스템(월드당 1개, 틱 스레드 전용).
 *
 * 틱 루프 ③(유체)과 ④(환경 데미지) 사이에서 {@link #tick} 이 호출됩니다:
 * 플레이어 스냅샷 갱신 → {@link MobRuntime#tick} → 이벤트 소비(플레이어 피해·폭발·디스폰 사유)
 * → 몹/화살 변경분 브로드캐스트 → welcome 스냅샷 게시. 전투(attack)는 ①에서 {@link #combat()} 로 처리합니다.
 */
final class MobSystem {

    private static final Logger log = LoggerFactory.getLogger(MobSystem.class);
    static final int PORTAL_MAX_EXTENT = 22;

    private static final double POS_EPS = 1e-3;
    private static final double[] EMPTY_CAT_POSITIONS = new double[0];

    private final WorldRuntime rt;
    private final MobRuntime runtime;
    private final MobWorldViewAdapter world;
    private final MobRandom rng;
    private final CombatSystem combat;
    private final Fluids.BlockLookup fluidBlocks;
    private final double[] fluidFlow = new double[3]; // 틱 스레드에서 몹마다 재사용
    /**
     * 살아 있는 전역 고양이 좌표의 grow-only 틱 scratch. 논리 길이는 매 틱 따로 전달하므로
     * 고양이 수가 줄어도 이전 high-water tail 이 크리퍼/팬텀 판정에 다시 나타나지 않는다.
     */
    private double[] catPositionScratch = EMPTY_CAT_POSITIONS;

    // 브로드캐스트 변경분 추적(id → 마지막으로 내보낸 원시 상태). 조용한 틱에는 DTO를 만들지 않는다.
    private final Map<Long, MobBroadcastState> lastMobState = new LinkedHashMap<>();
    // welcome은 변경된 몹의 DTO만 교체하고, 게시 시 현재 활성 순서로 참조만 다시 묶는다.
    private final Map<Long, MobSpawnDto> welcomeMobState = new HashMap<>();
    private final Set<Long> knownArrows = new HashSet<>();
    private final Map<Long, ProjectileRemoval> terminatedArrows = new LinkedHashMap<>();
    /**
     * [ARROW-GROUND] 이번 틱 코어 패스에서 종결된 투사체. 명중이 거절되면(무적·방패·순간이동)
     * 화살은 바닐라 {@code ProjectileDeflection.REVERSE} 로 되살아나야 하므로 같은 틱의 전투 정산까지 붙잡는다.
     */
    private final Map<Long, ProjectileSim> terminatedThisTick = new HashMap<>();
    private final Map<Long, ProjectilePos> lastArrowPositions = new LinkedHashMap<>();
    /** A recoverable trident termination awaiting its atomic projectile+ground commit. */
    private boolean projectileRecoveryPersistencePending;
    private long nextEventId = 1;
    /** 전투 단계에서 이미 드랍 처리된 사망 id. 다음 몹 틱의 공통 제거 정리에서 중복을 막는다. */
    private final Set<Long> mobDropsHandled = new HashSet<>();
    /**
     * [DEEP-DARK] 스컬크 촉매가 흡수한 사망. 몹 lane 은 블록을 직접 쓰지 않으므로
     * {@code {x, y, z, charge}} 만 쌓아 두고, 같은 틱의 스컬크 단계가
     * {@link #drainSculkBlooms()} 로 비워 개화를 커밋한다.
     */
    private final java.util.ArrayDeque<int[]> sculkBlooms = new java.util.ArrayDeque<>();
    /** 개화 시드의 사망 순번. 같은 좌표에서 두 번 죽어도 같은 개화가 나오지 않게 한다. */
    private long sculkDeathOrdinal;
    /** 활 당김 시작 틱. 해제 시 서버 틱 차이로 charge를 확정한다. */
    private final Map<PlayerUseKey, Long> bowChargeStarted = new java.util.HashMap<>();
    /** 석궁 장전 시작 틱. 활과 달리 뗄 때 장전이 완료되고 다음 누름이 발사다. */
    private final Map<PlayerUseKey, Long> crossbowChargeStarted = new java.util.HashMap<>();
    /** 장전이 끝나 볼트를 물고 있는 손/슬롯. 바닐라 {@code Charged} 컴포넌트의 자리다. */
    private final java.util.Set<PlayerUseKey> crossbowLoaded = new java.util.HashSet<>();
    /** [TRIAL-GAP] 장전된 석궁이 문 화살 종류(바닐라 CHARGED_PROJECTILES). 없으면 일반 화살이다. */
    private final java.util.Map<PlayerUseKey, Short> crossbowLoadedAmmo = new java.util.HashMap<>();
    /** 진행 중인 낚시 캐스팅. 닉네임당 최대 하나이며 서버 틱 스레드에서만 만진다. */
    private final Map<String, FishingCast> fishingCasts = new java.util.HashMap<>();
    /**
     * [MOUNT] 탈것에 앉은 기수 원장(닉네임 → 좌석). 보트 {@code driverOf} 와 같은 자리이며,
     * 좌표 업링크·하차·세션 종료가 이 표 하나만 보고 판정한다. 돼지 전용이던 표를 종 비의존으로
     * 올린 것이라 돼지 동작은 그대로다(한 사람이 동시에 두 탈것에 앉지 못하는 규칙 포함).
     * 기수는 비영속이라 이 표도 인메모리다.
     */
    private final Map<String, Seat> seatOf = new java.util.HashMap<>();

    /**
     * [ENCHANT-WIDE] 이 상자와 겹치는 살아 있는 몹이 있는가(바닐라 {@code isUnobstructed} 의 엔티티 판정).
     * 차가운 걸음이 몹이 서 있는 물 칸을 얼리지 않게 한다.
     */
    Iterable<Mob> redstoneMobs() { return runtime.mobs(); }
    Iterable<ProjectileSim> redstoneProjectiles() { return runtime.arrows(); }
    int redstoneRawSkyLight(int x, int y, int z) { return world.rawSkyLight(x, y, z); }

    boolean anyMobIntersects(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        for (Mob mob : runtime.mobs()) {
            if (mob.isDead() || mob.removed) continue;
            double half = mob.width() / 2.0;
            if (mob.x + half > minX && mob.x - half < maxX
                    && mob.z + half > minZ && mob.z - half < maxZ
                    && mob.y + mob.height() > minY && mob.y < maxY) {
                return true;
            }
        }
        return false;
    }

    /** [ENCHANT-WIDE] 이 플레이어가 탈것 몹에 앉아 있는가(돌진 인챈트의 탈것 요건). */
    boolean isSeated(String nickname) {
        return seatOf.containsKey(nickname);
    }
    /** 기수별 마지막 HUD 사실. 체력이 실제로 바뀔 때만 개인 메시지를 보낸다. */
    private final Map<String, MountedHudFacts> mountedHudByRider = new java.util.HashMap<>();

    /** 긴 사용 상태를 시작한 플레이어와 정확한 손/주손 슬롯을 함께 고정하는 값 키입니다. */
    private static final class PlayerUseKey {
        private final String nickname;
        private final PlayerInventory.Hand hand;
        private final int mainSlot;

        private PlayerUseKey(String nickname, PlayerInventory.HandRef ref) {
            if (nickname == null || ref == null) {
                throw new IllegalArgumentException("nickname and hand reference are required");
            }
            this.nickname = nickname;
            this.hand = ref.hand();
            this.mainSlot = ref.mainSlot();
        }

        private boolean belongsTo(String nickname) {
            return this.nickname.equals(nickname);
        }

        private boolean mainHandOf(String nickname) {
            return hand == PlayerInventory.Hand.MAIN && belongsTo(nickname);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PlayerUseKey key)) return false;
            return mainSlot == key.mainSlot && hand == key.hand && nickname.equals(key.nickname);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(nickname, hand, mainSlot);
        }
    }

    /**
     * [MOUNT] 좌석 원장 한 칸. 다인승을 처음부터 담도록 좌석 번호를 함께 갖는다
     * ({@link com.gameexpert.engine.mob.MobMountRules}).
     */
    private record Seat(long mobId, int seatIndex, MobType type) {}

    /** 캐스팅 한 건의 권위 상태. 착수 전에는 seed/대기 목표가 확정되지 않는다. */
    private static final class FishingCast {
        private final long bobberId;
        private final PlayerInventory.HandRef hand;
        private boolean landed;
        private double surfaceY;
        private int seed;
        /** 입질까지 채워야 할 대기량(바닐라 틱). */
        private int waitTarget = -1;
        /** 지금까지 채운 대기량(바닐라 틱). 비·하늘에 따라 틱마다 증가폭이 달라진다. */
        private int waitProgress;
        /**
         * 대기가 끝난 뒤 채워야 할 접근량(바닐라 {@code timeUntilHooked}).
         * −1 이면 아직 접근 단계에 들어가지 않았다.
         */
        private int approachTarget = -1;
        /** 지금까지 채운 접근량(바닐라 틱). 대기와 같은 {@code i} 로 소모한다. */
        private int approachProgress;
        /** 접근 물결의 현재 각도(도). 바닐라 {@code fishAngle} 이다. */
        private double fishAngle;
        private long biteEndTick = -1;
        private boolean biting;

        private FishingCast(long bobberId, PlayerInventory.HandRef hand) {
            this.bobberId = bobberId;
            this.hand = hand;
        }
    }
    /** 눈덩이 방향 오차용 Box–Muller 두 번째 표본. 월드 틱 스레드에서만 접근합니다. */
    private boolean gaussianSpareReady;
    private double gaussianSpare;
    // 이번 틱 사라진 몹의 사유(far/daylight/exploded/death). 소비 후 비웁니다.
    private final Map<Long, String> pendingDespawnReasons = new java.util.HashMap<>();
    private final ArrayDeque<PendingExplosion> pendingExplosions = new ArrayDeque<>();
    /**
     * 날씨 권위가 {@code beginTick} 에서 확정한 낙뢰. 효과는 몹 틱 시작 지점에서 한 번에 적용해
     * 변신·사망이 같은 틱의 스폰/디스폰 브로드캐스트 diff 를 그대로 타게 한다.
     */
    private final ArrayDeque<PendingLightning> pendingLightning = new ArrayDeque<>();

    private static final class PendingLightning {
        private final LightningStrike strike;
        private final boolean natural;
        private PendingLightning(LightningStrike strike, boolean natural) {
            this.strike = strike;
            this.natural = natural;
        }
    }
    private boolean pendingExplosionRetryRequested;

    private static final class PendingExplosion {
        private final double x;
        private final double y;
        private final double z;
        private final double power;
        /** [DRAGON] {@code ExplosionInteraction.NONE}(부활 연출 종료의 수정 폭발)이면 거짓. */
        private final boolean destroyBlocks;

        private PendingExplosion(double x, double y, double z, double power) {
            this(x, y, z, power, true);
        }

        private PendingExplosion(double x, double y, double z, double power, boolean destroyBlocks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.power = power;
            this.destroyBlocks = destroyBlocks;
        }
    }

    private static final class PlayerExplosionImpact {
        private final PlayerTickState player;
        private final int damage;
        private final double impact;
        private final double directionX;
        private final double directionY;
        private final double directionZ;
        private final boolean hurtProtected;

        private PlayerExplosionImpact(PlayerTickState player, int damage, double impact,
                                      double directionX, double directionY, double directionZ,
                                      boolean hurtProtected) {
            this.player = player;
            this.damage = damage;
            this.impact = impact;
            this.directionX = directionX;
            this.directionY = directionY;
            this.directionZ = directionZ;
            this.hurtProtected = hurtProtected;
        }
    }

    private static final class MobExplosionImpact {
        private final Mob mob;
        private final int damage;
        private final double impact;
        private final double directionX;
        private final double directionY;
        private final double directionZ;
        private final boolean hurtProtected;

        private MobExplosionImpact(Mob mob, int damage, double impact,
                                   double directionX, double directionY, double directionZ,
                                   boolean hurtProtected) {
            this.mob = mob;
            this.damage = damage;
            this.impact = impact;
            this.directionX = directionX;
            this.directionY = directionY;
            this.directionZ = directionZ;
            this.hurtProtected = hurtProtected;
        }
    }

    // 매 틱 재사용하는 브로드캐스트 작업 버퍼(틱 스레드 전용, 사용 직전 clear 로 상태 누수 방지).
    private final Set<Long> mobCurrentIdsBuf = new HashSet<>();
    /** IDs actually sent while active in the prior owner tick; bounded to the current/previous active union. */
    private final Set<Long> lastBroadcastActiveMobIds = new HashSet<>();
    private final Map<String, List<Long>> despawnByReasonBuf = new LinkedHashMap<>();
    private final Set<Long> arrowCurrentIdsBuf = new HashSet<>();
    private final Set<Long> lastBroadcastActiveArrowIds = new HashSet<>();
    private final Set<Long> removedMobIdsBuf = new HashSet<>();
    private final ArrayDeque<BeeHiveEntryRequest> pendingBeeHiveEntries = new ArrayDeque<>();
    private final ArrayDeque<FroglightSettlementRequest> pendingFroglightSettlements =
            new ArrayDeque<>();
    private final Map<String, CopperStatueSettlementWork> pendingCopperStatueSettlements =
            new LinkedHashMap<>();
    private final Set<String> animalSettlementInFlight = new HashSet<>();
    private final Map<Long, MobDeathPlan> pendingMobDeathPlans = new LinkedHashMap<>();
    private final Map<Long, MobDeathSettlementWork> pendingMobDeathSettlements =
            new LinkedHashMap<>();
    private final Map<String, FrogConversionIntent> pendingFrogConversionCommits =
            new LinkedHashMap<>();
    private FrogColonyPersistenceService frogColonyPersistence;

    // welcome 용 현재 몹 스냅샷(WS 스레드가 읽음).
    private volatile List<MobSpawnDto> welcomeSnapshot = List.of();

    record BeeHiveEntryRequest(long mobId, int x, int y, int z) { }
    record FroglightSettlementRequest(long frogMobId, long sulfurCubeMobId, short itemType,
            double x, double y, double z) { }
    private record CopperStatueSettlementWork(long mobId, MobEvent.CopperGolemStatue statue,
            MobPersistenceSnapshot snapshot) { }
    private record MobDeathSettlementWork(long mobId,
            List<com.gameexpert.ground.dto.GroundItemSnapshot> items,
            int xp, int sculkCharge, int deathX, int deathY, int deathZ,
            double dropX, double dropY, double dropZ) { }
    private record MobDeathPlan(long mobId, List<PlayerInventory.DroppedStack> stacks,
            int xp, int sculkCharge, int deathX, int deathY, int deathZ,
            double dropX, double dropY, double dropZ) {
        private MobDeathPlan {
            stacks = List.copyOf(stacks);
        }
    }
    private record FrogApplyOutcome(
            FrogConversionIntent intent, boolean denied, boolean applied) { }

    // 청크 생성 시 동물 배치(§39 vanilla 바이옴별 연속 확률). 판정 완료 청크 키와 대기 큐.
    // onChunkActivated 는 world owner 스레드에서 오므로 두 컬렉션 모두 동시성 안전이어야 한다.
    private final Set<Long> populatedChunkKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // populatedChunkKeys의 부분집합이므로 월드에서 판정한 청크 수를 넘지 않으며, DB 성공 승인 전까지 유지한다.
    private final Set<Long> unpersistedPopulationChunkKeys =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingPopulationChunkKeys =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Detached initial herds retained unchanged until their aggregate transaction is acknowledged. */
    private final Map<Long, List<MobPersistenceSnapshot>> publicationPopulationRows =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Queue<long[]> pendingPopulationChunks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int POPULATION_CHUNKS_PER_TICK = 4;
    private static final int STRUCTURE_OCCUPANT_POLICY_VERSION = 1;
    /** Vertical search half-window around the glitch center for a signal standing cell. */
    private static final int GLITCH_SIGNAL_VERTICAL_REACH = 32;
    private final Set<String> knownStructureOccupantClaims =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Full receipts for the exact BEES lane; identity includes Y through siteKind. */
    private final Map<String, StructureOccupantClaimSnapshot> knownFinalCarrierBeeClaims =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, StructureOccupantClaimSnapshot> unpersistedStructureOccupantClaims =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, AcceptedStructureSite> pendingStructureOccupantSites =
            new LinkedHashMap<>();
    /** Villager job site lane: one durable row per villager, one occupant per station cell. */
    private final VillagerJobClaimLedger villagerJobClaims = new VillagerJobClaimLedger();
    /** POI 탐색 → 점유 → 직업 획득을 매 틱 잇는 배선. 규칙은 villager 패키지가 소유한다. */
    private final com.gameexpert.engine.mob.villager.VillagerJobAssignment villagerJobAssignment =
            new com.gameexpert.engine.mob.villager.VillagerJobAssignment(villagerJobClaims);
    /**
     * [VILLAGER-BELL] 주민 현재 활동의 정본(일정 + 종·습격의 HIDE/PRE_RAID/RAID). 바닐라와 같이
     * 영속하지 않는다. 몹 이동·직업·사회·gossip lane 이 모두 이 원장의 결정을 읽는다.
     */
    private final com.gameexpert.engine.mob.villager.VillagerActivityLedger villagerActivities =
            new com.gameexpert.engine.mob.villager.VillagerActivityLedger();
    /** [VILLAGER-BELL] 종 칸의 흔들림·청취 목록·공명 상태(휘발). */
    private final com.gameexpert.engine.mob.villager.VillagerBellRules bells =
            new com.gameexpert.engine.mob.villager.VillagerBellRules();
    /** Glitch signal enderman lane: same delivery, own exactly-once claim per glitch center. */
    private final Map<String, AcceptedStructureSite> pendingGlitchSignalSites =
            new LinkedHashMap<>();
    private final Map<Long, Set<String>> structureSiteKeysBySource = new HashMap<>();
    private final Map<String, Set<Long>> structureSiteSources = new HashMap<>();
    /** Victory receipts confirmed by the ledger but not yet acknowledged by a persistence flush. */
    private final Map<Long, RaidRewardReceipt> unpersistedRaidReceipts =
            new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Durable victory receipts that still owe their payout, {@code raidId → recipient nickname}.
     *
     * <p>An entry only exists once the receipt is known to be in the database — either it was read
     * back as {@code PENDING} at world load, or a flush acknowledged it. That ordering matters:
     * claiming a receipt that was never written would consume nothing and the prize would be lost.
     * The payout lane ({@link WorldRuntime#claimRaidVictoryPrizes}) removes an entry only when the
     * durable claim reports {@code GRANTED}/{@code ALREADY_GRANTED}, so a hero who is offline (or a
     * flush that crashed) simply keeps the entry for the next attempt.</p>
     */
    private final Map<Long, String> unclaimedRaidPrizes =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Last boss bar actually sent; null means no bar is showing. Tick thread only. */
    private WsMessages.RaidBossbar lastBossbar;
    /**
     * [TRIAL] 시련 설비 인덱스. 스포너 블록 인덱스와 같은 스캔 패스를 공유하므로 시련 배선이
     * 두 번째 전역 탐색을 만들지 않는다.
     */
    private final SpawnerIndex spawnerIndex;
    private final MobPersistenceService persistence;
    MobSystem(WorldRuntime rt, MobPersistenceService persistence) {
        this.rt = rt;
        this.persistence = persistence;
        int seed = rt.seed();
        this.world = new MobWorldViewAdapter(rt.accessor(), rt.clock(), rt.weatherSystem(),
                seed, rt.blockStates(), rt::worldSpawn, rt.difficulty());
        this.world.setCollisionLookup((x,y,z,visitor) -> rt.tickLoop().forRedstoneCollisionBoxes(x,y,z,visitor));
        this.world.setMobMutationJournal(rt::mobMutationJournal);
        this.world.setVillagerActivityLedger(villagerActivities);
        this.world.setCandleStates(rt::blockState);
        this.world.setPlacedProjectileLookup((ax, ay, az, bx, by, bz, inflation) ->
                rt.placedEntities().projectileHit(ax, ay, az, bx, by, bz, inflation));
        this.fluidBlocks = (x, y, z) -> world.getBlock(x, y, z) & 0xFFFF;
        // 스포너 인덱스는 실제 접근으로 올라온 캐시만 읽어 몹 틱에서 cold 청크를 생성하지 않는다.
        SpawnerIndex spawnerIndex = this.spawnerIndex = new SpawnerIndex(new SpawnerIndex.ChunkSource() {
            @Override
            public Object snapshot(int chunkX, int chunkZ) {
                return rt.accessor().snapshotSource(chunkX, chunkZ);
            }

            @Override
            public int blockTypeAt(Object snapshot, int blockIndex) {
                return ((TerrainAccessor.SnapshotSource) snapshot).blockTypeAt(blockIndex);
            }

            @Override
            public boolean copyCellsTo(Object snapshot, short[] types, byte[] states) {
                ((TerrainAccessor.SnapshotSource) snapshot).copyCellsTo(types, states);
                return true;
            }
        }, rt.accessor()::isChunkActivated);
        this.world.setHiveLookup(spawnerIndex::nearestBeeHive);
        this.world.setRafflesiaLookup(spawnerIndex::nearestRafflesia);
        this.world.setFireflyLookup((x, y, z, horizontal, vertical) ->
                spawnerIndex.nearestFireflyBush(x, y, z, horizontal, vertical,
                        (bx, by, bz) -> {
                            short block = world.getBlock(bx, by, bz);
                            return block >= 0 && com.gameexpert.engine.blocks.FireflyBushEcology
                                    .isConsumableHost(block & 0xffff,
                                            world.localBrightness(bx, by, bz));
                        }));
        this.world.setPoisonFrogColonyLookup((x, y, z, out) ->
                spawnerIndex.nearestPoisonFrogColony(x, y, z, out,
                        (rx, ry, rz, bx, by, bz) -> FrogPoisonConversionRules.validPair(
                                world.getBlock(rx, ry, rz) & 0xffff,
                                world.getBlock(bx, by, bz) & 0xffff,
                                world.localBrightness(bx, by, bz),
                                rx, ry, rz, bx, by, bz)));
        VillagerPoiIndex villagerPoiIndex = new VillagerPoiIndex(
                new VillagerPoiIndex.ChunkSource() {
                    @Override
                    public Object snapshot(int chunkX, int chunkZ) {
                        return rt.accessor().isChunkActivated(chunkX, chunkZ)
                                ? rt.accessor().snapshotSource(chunkX, chunkZ) : null;
                    }

                    @Override
                    public int blockTypeAt(Object snapshot, int blockIndex) {
                        return ((TerrainAccessor.SnapshotSource) snapshot).blockTypeAt(blockIndex);
                    }
                });
        this.runtime = new MobRuntime(spawnerIndex, seed, villagerPoiIndex);
        this.world.setEndDimension("void_end".equals(rt.dimensionKey()));
        this.world.setGatewayAvailability((x,y,z) -> WorldTickLoop.residentBlockType(rt.accessor(),x,y,z)==Blocks.END_GATEWAY
                && !rt.endGateways().coolingDown(new BlockPos(x,y,z),rt.clock().tickCount()));
        this.runtime.setNaturalSpawningEnabled(!rt.customDimension() || world.endDimension());
        this.world.setPoisonFrogColonyLookup((x, y, z, out) -> {
            long now = runtime.absoluteMcTick(world);
            return spawnerIndex.nearestPoisonFrogColony(x, y, z, out,
                    (rx, ry, rz, bx, by, bz) -> runtime.frogColonyReady(rx, ry, rz, now)
                            && FrogPoisonConversionRules.validPair(
                                    world.getBlock(rx, ry, rz) & 0xffff,
                                    world.getBlock(bx, by, bz) & 0xffff,
                                    world.localBrightness(bx, by, bz),
                                    rx, ry, rz, bx, by, bz));
        });
        this.rng = new JavaMobRandom(seed);
        this.combat = new CombatSystem(runtime.mobs(),
                (CombatSystem.MobDeathSink) (mob, reason, lootingLevel) -> {
                    pendingDespawnReasons.put(mob.id, reason);
                    runtime.splitSlimeOnDeath(mob, rng);
                    // 플레이어 처치 전용 희귀 드랍 + 처치 무기의 약탈 레벨까지 포함
                    spawnMobDrops(mob, true, lootingLevel);
                    mobDropsHandled.add(mob.id);
                },
                this::broadcast,
                runtime::rememberOwnerAttackedMob,
                // [GUARDIAN] 가시 반사. 사망 원인이 그 몹으로 남도록 몹 피해 경로를 그대로 쓴다.
                (nickname, damage, source) -> damagePlayerFrom(nickname, damage, "mob",
                        typeName(source), source.x, source.z, false));
        this.combat.setItemFrameHurt((frame, nickname) -> hurtItemFrame(frame, false, nickname));
        this.combat.setMobSoundSink(this::mobSound);
        this.combat.setPlayerSoundSink((player, kind) ->
                worldSound(kind, player.x(), player.y(), player.z(), (short) 0));
        this.combat.setDragonCombat(new CombatSystem.DragonCombat() {
            @Override
            public void hurtCrystal(Mob crystal, String attackerNickname) {
                rt.dragonFight().hurtCrystal(crystal.id, false, attackerNickname);
            }

            @Override
            public com.gameexpert.engine.dragon.DragonBrain.HurtResult meleeDragon(PlayerTickState player,
                    EnderDragon dragon, double amount) {
                return rt.dragonFight().meleeDragon(player, dragon, amount);
            }

            @Override
            public com.gameexpert.engine.dragon.DragonBrain.HurtResult kineticDragon(PlayerTickState player,
                    EnderDragon dragon, double amount) {
                return rt.dragonFight().kineticDragon(player, dragon, amount);
            }
        });
        if (persistence != null) {
            for (MobPersistenceSnapshot snapshot : persistence.loadWorld(rt.worldId())) {
                runtime.restore(snapshot);
            }
            runtime.reconcileRestoredMobVehicles();
            runtime.reserveMobIdThrough(persistence.loadedMobIdHighWater(rt.worldId()));
            for (long[] chunk : persistence.loadPopulatedChunks(rt.worldId())) {
                populatedChunkKeys.add(populationKey((int) chunk[0], (int) chunk[1]));
            }
            for (StructureOccupantClaimSnapshot claim
                    : persistence.loadStructureOccupantClaims(rt.worldId())) {
                knownStructureOccupantClaims.add(claim.identityKey());
                if (isFinalCarrierBeeKind(claim.siteKind())) {
                    knownFinalCarrierBeeClaims.put(claim.identityKey(), claim);
                }
            }
            List<VillagerJobClaimSnapshot> restoredJobClaims =
                    persistence.loadVillagerJobClaims(rt.worldId());
            villagerJobClaims.restore(restoredJobClaims);
            // Mob rows and the job lane commit in separate transactions. If the mob removal wins
            // immediately before a crash, the stale claim must not reserve its POI for a vanished
            // villager or be inherited when that numeric id is later reused.
            for (VillagerJobClaimSnapshot claim : restoredJobClaims) {
                if (!isLiveVillager(claim.mobId())) villagerJobClaims.forgetMob(claim.mobId());
            }
            // Ledger rows first, then rebind the restored raiders: membership must exist before a
            // member can rejoin it, and rejoining carries the persisted health into the boss bar.
            runtime.raidLedger().restore(persistence.loadRaidLedger(rt.worldId()));
            runtime.restoreRaidMembership();
            // A receipt that is still PENDING after a restart is a victory whose prize was never
            // handed out. Re-queue it so the payout lane retries; the conditional transition keeps
            // the retry from paying twice when the crash happened after the grant.
            for (RaidRewardReceipt receipt : persistence.loadRaidReceipts(rt.worldId())) {
                rememberUnclaimedRaidPrize(receipt);
            }
            publishSnapshot();
        }
        villagerJobClaims.addListener(new VillagerJobClaimLedger.Listener() {
            @Override
            public void onProfessionAssigned(VillagerJobClaimSnapshot claim) {
                syncVillagerTradeProfession(claim.mobId());
            }

            @Override
            public void onJobSiteReleased(VillagerJobClaimSnapshot claim,
                    com.gameexpert.engine.mob.villager.VillagerJobSitePolicy.ReleaseReason reason) {
                syncVillagerTradeProfession(claim.mobId());
            }
        });
    }

    /** Villager job/profession lane. Public read+write surface for the villager tracks. */
    public VillagerJobClaimLedger villagerJobClaims() {
        return villagerJobClaims;
    }

    long[] traderWindowState() {
        return runtime.traderWindowState();
    }

    void restoreTraderWindow(long nextAttemptTick, int chancePercent) {
        runtime.restoreTraderWindow(nextAttemptTick, chancePercent);
    }

    /** True only for an actually restored, living Villager aggregate. */
    boolean isLiveMerchant(long mobId) {
        Mob mob = combat().findAlive(mobId);
        return mob != null && (mob.type == MobType.VILLAGER || mob.type == MobType.WANDERING_TRADER);
    }

    boolean isLiveVillager(long mobId) {
        Mob mob = runtime.mobById(mobId);
        return mob != null && mob.type == MobType.VILLAGER && !mob.removed && !mob.isDead();
    }

    /**
     * A station block stopped existing at that cell, so its occupant loses the job site. The
     * block authority calls this from mining and explosion removal.
     */
    public void releaseVillagerJobSiteAt(int x, int y, int z) {
        villagerJobClaims.releaseAtPosition(x, y, z);
    }

    /** 주민 직업 배정 lane. 후보 기억·점유 승격의 관측 지점이다. */
    public com.gameexpert.engine.mob.villager.VillagerJobAssignment villagerJobAssignment() {
        return villagerJobAssignment;
    }

    /** 점유 직업 → 현재 거래 직업 → 살아 있는 주민의 생성 직업 순으로 읽는 순수 조회다. */
    public com.gameexpert.engine.mob.villager.VillagerProfessionSource villagerProfessionSource() {
        return mobId -> {
            if (villagerJobClaims.claimOf(mobId) != null) {
                return com.gameexpert.engine.mob.villager.VillagerProfessionSource
                        .tradeProfession(villagerJobClaims.professionOf(mobId));
            }
            if (!isLiveVillager(mobId)) {
                return com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession.NONE;
            }
            var current = villagerTrades().peek(mobId);
            if (current != null) return current.profession();
            String intrinsic = runtime.intrinsicVillagerProfession(mobId);
            return intrinsic == null
                    ? com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession.NONE
                    : com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession
                            .valueOf(intrinsic);
        };
    }

    /** NONE 도 영속해 실직 뒤 생성 당시 직업이 되살아나지 않게 한다. 결속 사실은 불변이다. */
    private void syncVillagerTradeProfession(long mobId) {
        if (!isLiveVillager(mobId)) return;
        var profession = com.gameexpert.engine.mob.villager.VillagerProfessionSource
                .tradeProfession(villagerJobClaims.professionOf(mobId));
        villagerTrades().stateFor(mobId, profession,
                com.gameexpert.engine.mob.villager.VillagerTradeRules.offerDraws(mobId, 1));
    }

    /**
     * Publishes a villager's / zombie villager's current VillagerData in its visual bits: biome
     * type from the structure facts (plains otherwise), profession from the profession source (job
     * ledger, trade state, spawn facts), level from the trade ledger (spawn facts, else 1). Zombie
     * villagers keep the facts they were converted with. The standalone authority publishes the same.
     */
    private void refreshVillagerAppearance(Mob m) {
        boolean villager = m instanceof com.gameexpert.engine.mob.Villager;
        if (!villager && !(m instanceof com.gameexpert.engine.mob.ZombieVillager)) return;
        com.gameexpert.mob.dto.MobPersistenceSnapshot facts = runtime.villagerCarrierFacts(m.id);
        String type = facts == null ? null : facts.getVillagerBiomeType();
        String profession;
        int level = facts == null || facts.getVillagerLevel() <= 0 ? 1 : facts.getVillagerLevel();
        if (villager) {
            profession = villagerProfessionSource().professionOf(m.id).name();
            var trade = villagerTrades().peek(m.id);
            if (trade != null) level = trade.level();
        } else {
            profession = facts == null ? null : facts.getVillagerProfession();
        }
        m.setVillagerAppearanceFlags(
                com.gameexpert.engine.mob.villager.VillagerAppearance.visualFlags(type, profession, level));
    }

    private boolean canAcquireVillagerJobSite(long mobId) {
        var current = villagerTrades().peek(mobId);
        return villagerProfessionSource().professionOf(mobId)
                != com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession.NITWIT
                && (current == null || current.totalXp() == 0);
    }

    /**
     * 거래 트랙이 읽고 쓰는 평판 원장. 정본은 거래 상대 주민 자신의 gossip 원장이라
     * 좀비 주민 치료로 붙은 평판이 그대로 그 주민의 가격에 반영된다.
     */
    public com.gameexpert.engine.mob.villager.VillagerReputationSource
            villagerReputationSource() {
        return com.gameexpert.engine.mob.villager.VillagerReputationSource
                .ofGossips(this::villagerGossipsOf);
    }

    /** 살아 있는 주민의 gossip 원장. 주민이 아니거나 없으면 null 이다. */
    private com.gameexpert.engine.mob.villager.VillagerGossips villagerGossipsOf(long mobId) {
        Mob mob = combat.findAlive(mobId);
        return mob instanceof com.gameexpert.engine.mob.Villager villager
                ? villager.social().gossips() : null;
    }

    /**
     * 주민 직업 lane 한 틱: 후보 POI 탐색 → 접근 → 2블록 안에서 정확히 한 번 점유 → 직업 획득.
     * 활성 청크의 주민만 대상으로 하며, 결정된 걷기 목표는 같은 틱의 몹 이동이 읽는다.
     */
    private void tickVillagerJobs(List<Mob> activeMobs) {
        List<VillagerJobHandle> villagers = null;
        for (Mob mob : activeMobs) {
            if (mob.type != MobType.VILLAGER) continue;
            if (villagers == null) villagers = new ArrayList<>();
            villagers.add(new VillagerJobHandle(mob, canAcquireVillagerJobSite(mob.id),
                    villagerActivities));
        }
        if (villagers == null) {
            world.setVillagerJobWalkTargets(Map.of());
            return;
        }
        var outcome = villagerJobAssignment.tick(villagerJobWorld, villagers);
        world.setVillagerJobWalkTargets(outcome.walkTargets());
        // 바닐라 WorkAtPoi.start: 작업대를 쓴 주민은 그 자리에서 재입고 조건을 확인한다.
        if (!outcome.workedAtJobSite().isEmpty()) {
            villagerTrades().restockWorkingVillagers(outcome.workedAtJobSite(),
                    com.gameexpert.engine.mob.villager.VillagerSocietyRules.mcGameTime(
                            world.dayCount(), world.worldTime()),
                    com.gameexpert.engine.mob.villager.VillagerSocietyRules.mcDayTime(
                            world.worldTime()));
        }
    }

    /**
     * [VILLAGER-BELL] 주민 활동 원장 한 틱과 종의 흔들림·공명 한 틱. 원장은 발밑 습격
     * ({@link com.gameexpert.engine.raid.RaidLedger#raidAt})과 HOME(침대) POI 를 보고, 결정은
     * 같은 틱의 직업·사회·gossip·이동 lane 이 읽는다.
     */
    private void tickVillagerActivities(List<Mob> activeMobs, long tickNo) {
        long gameTime = com.gameexpert.engine.mob.villager.VillagerSocietyRules.mcGameTime(
                world.dayCount(), world.worldTime());
        tickBells(gameTime);
        List<VillagerActivityHandle> villagers = null;
        for (Mob mob : activeMobs) {
            if (mob.type != MobType.VILLAGER) continue;
            if (villagers == null) villagers = new ArrayList<>();
            villagers.add(new VillagerActivityHandle(mob,
                    runtime.villagerSociety().bedClaims().bedOf(mob.id)));
        }
        if (villagers == null) return;
        com.gameexpert.engine.raid.RaidLedger raids = runtime.raidLedger();
        villagerActivities.tick(
                new com.gameexpert.engine.mob.villager.VillagerActivityLedger.ActivityWorld() {
                    @Override
                    public long worldTime() {
                        return world.worldTime();
                    }

                    @Override
                    public long dayCount() {
                        return world.dayCount();
                    }

                    @Override
                    public com.gameexpert.engine.mob.villager.VillagerActivityLedger.RaidView
                            raidAt(double x, double y, double z) {
                        return villagerRaidView(raids.raidAt((int) Math.floor(x),
                                (int) Math.floor(y), (int) Math.floor(z), tickNo));
                    }

                    @Override
                    public List<int[]> homesWithin(int originX, int originY, int originZ,
                            int radius) {
                        VillagerPoiIndex index = runtime.villagerPoiIndex();
                        if (index != null) {
                            return index.homesWithin(originX, originY, originZ, radius);
                        }
                        return com.gameexpert.engine.mob.villager.VillagerSocietyRules
                                .homesWithin(originX, originY, originZ, radius,
                                        (x, y, z) -> Blocks.isBed(world.getBlock(x, y, z) & 0xFFFF));
                    }
                }, villagers);
    }

    /** 원장 raid 한 건을 주민 뇌가 보는 바닐라 판정 표면으로 옮긴다. */
    static com.gameexpert.engine.mob.villager.VillagerActivityLedger.RaidView villagerRaidView(
            com.gameexpert.engine.raid.RaidLedger.Instance raid) {
        if (raid == null) return null;
        return new com.gameexpert.engine.mob.villager.VillagerActivityLedger.RaidView(
                raid.started(), raid.betweenWaves(),
                raid.status() == com.gameexpert.engine.raid.RaidLedger.Status.VICTORY,
                raid.status() == com.gameexpert.engine.raid.RaidLedger.Status.LOSS,
                raid.status() == com.gameexpert.engine.raid.RaidLedger.Status.STOPPED);
    }

    /** 활동 lane 이 보는 주민 하나. 몹 상태를 읽기만 한다. */
    private record VillagerActivityHandle(Mob mob, int[] homeBed)
            implements com.gameexpert.engine.mob.villager.VillagerActivityLedger.ActivityVillager {
        @Override public long id() { return mob.id; }

        @Override public double x() { return mob.x; }

        @Override public double y() { return mob.y; }

        @Override public double z() { return mob.z; }

        @Override public boolean baby() { return mob.isBaby(); }

        @Override public boolean alive() { return !mob.isDead() && !mob.removed; }
    }

    /**
     * [VILLAGER-BELL] 종의 {@code BellBlockEntity#triggerEvent}: 들은 주민에게 HEARD_BELL_TIME. 호출자
     * ({@code WorldTickLoop#ringBell})가 리치·{@code isProperHit} 을 확인했고 {@code bell_use} 소리를 방송한다.
     *
     * @return 이번 치기로 HEARD_BELL_TIME 을 얻은 주민 id
     */
    public List<Long> ringBell(int x, int y, int z) {
        long gameTime = com.gameexpert.engine.mob.villager.VillagerSocietyRules.mcGameTime(
                world.dayCount(), world.worldTime());
        List<BellHearer> candidates = new ArrayList<>();
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            candidates.add(new BellHearer(mob));
        }
        var ring = bells.ring(x, y, z, gameTime, candidates, this::bellHearer);
        for (long id : ring.heardVillagerIds()) villagerActivities.hearBell(id, gameTime);
        return ring.heardVillagerIds();
    }

    /** [VILLAGER-BELL] 테스트·관측: 주민의 활동 원장. */
    public com.gameexpert.engine.mob.villager.VillagerActivityLedger villagerActivities() {
        return villagerActivities;
    }

    private void tickBells(long gameTime) {
        // 흔들림·목록 수명만 센다. 공명 소리와 습격자 발광은 WorldTickLoop 의 BellResonance 가 소유한다.
        bells.tick(gameTime, this::bellHearer);
    }

    private BellHearer bellHearer(long id) {
        Mob mob = combat.findAlive(id);
        return mob == null || mob.removed ? null : new BellHearer(mob);
    }

    /** 종 목록에 드는 살아 있는 몹 하나. */
    private record BellHearer(Mob mob)
            implements com.gameexpert.engine.mob.villager.VillagerBellRules.Hearer {
        @Override public long id() { return mob.id; }

        @Override public double x() { return mob.x; }

        @Override public double y() { return mob.y; }

        @Override public double z() { return mob.z; }

        @Override public double width() { return mob.width(); }

        @Override public double height() { return mob.height(); }

        @Override public boolean villager() { return mob.type == MobType.VILLAGER; }

        /** {@code #minecraft:raiders}: 우민 진영에서 소환수 Vex 를 뺀 종. */
        @Override public boolean raider() {
            return mob.type != MobType.VEX
                    && com.gameexpert.engine.mob.MobRelationshipPolicy.isRaiderFaction(mob.type);
        }
    }

    /**
     * 주민끼리의 gossip 전파 한 틱. 짝짓기 판정은 {@code VillagerGossipExchange} 가, 전파와
     * 쿨다운은 각 주민의 {@code VillagerSocialState} 가 소유한다. 감쇠는 주민 자신의 일정 틱
     * ({@code Villager#refreshSchedule} → {@code maybeDecayGossip})에서 이미 돈다.
     */
    private void tickVillagerGossip(List<Mob> activeMobs) {
        List<VillagerGossipHandle> villagers = null;
        for (Mob mob : activeMobs) {
            if (mob.type != MobType.VILLAGER) continue;
            if (mob.isDead() || mob.removed) continue;
            if (!(mob instanceof com.gameexpert.engine.mob.Villager villager)) continue;
            if (villagers == null) villagers = new ArrayList<>();
            villagers.add(new VillagerGossipHandle(villager, villagerActivities));
        }
        if (villagers == null || villagers.size() < 2) return;
        long gameTime = com.gameexpert.engine.mob.villager.VillagerSocietyRules.mcGameTime(
                world.dayCount(), world.worldTime());
        long dayTime = com.gameexpert.engine.mob.villager.VillagerSocietyRules.mcDayTime(
                world.worldTime());
        var exchanges = com.gameexpert.engine.mob.villager.VillagerGossipExchange.plan(
                gameTime, dayTime, villagers);
        if (exchanges.isEmpty()) return;
        Map<Long, com.gameexpert.engine.mob.Villager> byId = new HashMap<>(villagers.size() * 2);
        for (VillagerGossipHandle handle : villagers) byId.put(handle.id(), handle.villager());
        for (var exchange : exchanges) {
            com.gameexpert.engine.mob.Villager listener = byId.get(exchange.listenerId());
            com.gameexpert.engine.mob.Villager teller = byId.get(exchange.tellerId());
            if (listener == null || teller == null) continue;
            listener.social().gossipWith(teller.social(), gameTime,
                    com.gameexpert.engine.mob.villager.VillagerGossipRules.transferRandom(
                            gameTime, exchange.listenerId(), exchange.tellerId()));
        }
    }

    /** gossip 짝짓기가 보는 주민 한 명. 몹 상태를 읽기만 한다. */
    private record VillagerGossipHandle(com.gameexpert.engine.mob.Villager villager,
            com.gameexpert.engine.mob.villager.VillagerActivityLedger activities)
            implements com.gameexpert.engine.mob.villager.VillagerGossipExchange
                    .GossipParticipant {
        /** [VILLAGER-BELL] 현재 활동의 정본은 활동 원장이다. */
        @Override
        public com.gameexpert.engine.mob.villager.VillagerBrainRules.Activity activity(
                long dayTime) {
            return activities.activityOf(villager.id, dayTime, villager.isBaby());
        }

        @Override
        public long id() {
            return villager.id;
        }

        @Override
        public double x() {
            return villager.x;
        }

        @Override
        public double y() {
            return villager.y;
        }

        @Override
        public double z() {
            return villager.z;
        }

        @Override
        public boolean baby() {
            return villager.isBaby();
        }

        @Override
        public long lastGossipTime() {
            return villager.social().lastGossipTime();
        }
    }

    private final com.gameexpert.engine.mob.villager.VillagerJobAssignment.JobWorld
            villagerJobWorld =
            new com.gameexpert.engine.mob.villager.VillagerJobAssignment.JobWorld() {
                @Override
                public int blockAt(int x, int y, int z) {
                    return world.getBlock(x, y, z) & 0xFFFF;
                }

                @Override
                public VillagerPoiIndex.SearchResult nearestFreeStation(int originX, int originY,
                        int originZ, VillagerPoiIndex.FreeCell free) {
                    VillagerPoiIndex index = runtime.villagerPoiIndex();
                    return index == null
                            ? new VillagerPoiIndex.SearchResult(false, null)
                            : index.nearestFreeJobSite(originX, originY, originZ, free);
                }

                @Override
                public long worldTime() {
                    return world.worldTime();
                }

                @Override
                public long dayCount() {
                    return world.dayCount();
                }
            };

    /** 배정 lane 이 보는 주민 한 명. 몹 상태를 읽기만 한다. */
    private static final class VillagerJobHandle
            implements com.gameexpert.engine.mob.villager.VillagerJobAssignment.JobVillager {
        private final Mob mob;
        private final boolean canAcquireJobSite;
        private final com.gameexpert.engine.mob.villager.VillagerActivityLedger activities;

        private VillagerJobHandle(Mob mob, boolean canAcquireJobSite,
                com.gameexpert.engine.mob.villager.VillagerActivityLedger activities) {
            this.mob = mob;
            this.canAcquireJobSite = canAcquireJobSite;
            this.activities = activities;
        }

        /** [VILLAGER-BELL] 현재 활동의 정본은 활동 원장이다. */
        @Override
        public com.gameexpert.engine.mob.villager.VillagerBrainRules.Activity activity(
                long dayTime) {
            return activities.activityOf(mob.id, dayTime, mob.isBaby());
        }

        @Override
        public boolean canAcquireJobSite() {
            return canAcquireJobSite;
        }

        @Override
        public long id() {
            return mob.id;
        }

        @Override
        public double x() {
            return mob.x;
        }

        @Override
        public double y() {
            return mob.y;
        }

        @Override
        public double z() {
            return mob.z;
        }

        @Override
        public boolean baby() {
            return mob.isBaby();
        }

        @Override
        public boolean alive() {
            return !mob.isDead() && !mob.removed;
        }

        /** 영속 원천은 주민 자신의 사회 기억이다. 없으면 마을 울타리가 없다. */
        @Override
        public int[] homeVillageAnchor() {
            return mob instanceof com.gameexpert.engine.mob.Villager villager
                    ? villager.homeVillageAnchor() : null;
        }
    }

    List<MobPersistenceSnapshot> persistenceSnapshot() {
        return runtime.persistenceSnapshot();
    }

    /** Typed after-commit ENTS publication seam; WorldRuntime wiring must call this on its owner. */
    public void installCommittedStructureEntities(List<MobPersistenceSnapshot> snapshots) {
        runtime.installCommittedStructureEntities(snapshots);
    }

    public void installCommittedStructureEntities(
            com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                    .CommittedEntityActivation activation) {
        runtime.installCommittedStructureEntities(activation.snapshots());
    }

    public void setStructureEntityDeathListener(
            MobRuntime.StructureEntityDeathListener listener) {
        runtime.setStructureEntityDeathListener(listener);
    }

    PopulationPersistenceSnapshot populationPersistenceSnapshot() {
        Set<Long> allKeys = new HashSet<>(unpersistedPopulationChunkKeys);
        allKeys.addAll(publicationPopulationRows.keySet());
        List<Long> chunkKeys = allKeys.stream().sorted().toList();
        List<MobPersistenceSnapshot> rows = new ArrayList<>(runtime.persistenceSnapshot());
        for (long key : chunkKeys) rows.addAll(publicationPopulationRows.getOrDefault(key, List.of()));
        List<long[]> chunks = chunkKeys.stream()
                .map(key -> new long[] { (int) (key >> 32), (int) (long) key })
                .toList();
        Map<String, StructureOccupantClaimSnapshot> claims =
                Map.copyOf(unpersistedStructureOccupantClaims);
        return new PopulationPersistenceSnapshot(
                List.copyOf(rows), chunkKeys, chunks, claims,
                runtime.raidLedger().snapshot(), List.copyOf(unpersistedRaidReceipts.values()),
                runtime.trialSpawners().persistentSnapshots(),
                runtime.trialSpawners().pendingStateChanges(),
                villagerJobClaims.unpersistedUpserts(), villagerJobClaims.unpersistedRemovals(),
                villagerTrades().unpersistedUpserts(), villagerTrades().unpersistedRemovals(),
                runtime.villagerSociety().persistenceSnapshot());
    }

    void restoreVillagerSociety(
            List<VillagerSocietySnapshot> states,
            List<VillagerBedClaimSnapshot> bedClaims) {
        Set<Long> liveVillagers = new HashSet<>();
        for (Mob mob : runtime.mobs()) {
            if (mob.type == MobType.VILLAGER && !mob.removed && !mob.isDead()) {
                liveVillagers.add(mob.id);
            }
        }
        List<VillagerSocietySnapshot> filteredStates = states == null ? List.of()
                : states.stream().filter(state -> liveVillagers.contains(state.villagerId())).toList();
        List<VillagerBedClaimSnapshot> filteredClaims = bedClaims == null ? List.of()
                : bedClaims.stream().filter(claim -> liveVillagers.contains(
                        claim.ownerVillagerId())).toList();
        runtime.villagerSociety().restore(filteredStates, filteredClaims);
        boolean staleMate = false;
        for (VillagerSocietySnapshot state : filteredStates) {
            if (state.mateId() == 0L || liveVillagers.contains(state.mateId())) continue;
            // The society and mob tables commit independently. A vanished higher-id mate must be
            // cleared during hydrate, before the allocator can reuse that number for a stranger.
            runtime.villagerSociety().forget(state.mateId());
            staleMate = true;
        }
        if (filteredStates.size() != (states == null ? 0 : states.size())
                || filteredClaims.size() != (bedClaims == null ? 0 : bedClaims.size())
                || staleMate) {
            // Repair crash residue on the next successful aggregate flush.
            runtime.villagerSociety().markPersistenceDirty();
        }
    }

    void acknowledgePopulationPersistence(PopulationPersistenceSnapshot snapshot) {
        Map<Long, List<MobPersistenceSnapshot>> committedPopulation = new HashMap<>();
        for (long key : snapshot.chunkKeys()) {
            List<MobPersistenceSnapshot> rows = publicationPopulationRows.get(key);
            if (rows != null && snapshot.mobs().containsAll(rows)) committedPopulation.put(key, rows);
        }
        if (!committedPopulation.isEmpty()) rt.enqueuePersistenceCompletion(() -> {
            boolean installed = false;
            for (var entry : committedPopulation.entrySet()) {
                if (publicationPopulationRows.get(entry.getKey()) != entry.getValue()) continue;
                runtime.installCommittedStructureEntities(entry.getValue());
                populatedChunkKeys.add(entry.getKey());
                pendingPopulationChunkKeys.remove(entry.getKey());
                publicationPopulationRows.remove(entry.getKey(), entry.getValue());
                installed = true;
            }
            if (installed) {
                List<Mob> active = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
                broadcastMobs(Set.of(), active);
                publishSnapshot(active);
            }
        });
        unpersistedPopulationChunkKeys.removeAll(snapshot.chunkKeys());
        snapshot.claims().forEach((key, claim) ->
                unpersistedStructureOccupantClaims.remove(key, claim));
        for (RaidRewardReceipt receipt : snapshot.raidReceipts()) {
            unpersistedRaidReceipts.remove(receipt.raidId(), receipt);
            // The receipt is durable now, so and only now may the payout lane try to consume it.
            rememberUnclaimedRaidPrize(receipt);
        }
        // Terminal rows (and a victory receipt when applicable) are durable at this point. Keeping
        // them forever grows every later full-ledger replacement and retains obsolete mob ids.
        // Match the exact captured status so an older async acknowledgment cannot forget a newer
        // state that happens to reuse the same identity.
        for (RaidLedger.InstanceSnapshot persisted : snapshot.raids()) {
            if ("ONGOING".equals(persisted.status())) continue;
            RaidLedger.Instance current = runtime.raidLedger().instance(persisted.raidId());
            if (current != null && current.status().name().equals(persisted.status())) {
                runtime.raidLedger().forget(persisted.raidId());
            }
        }
        villagerJobClaims.acknowledgePersisted(
                snapshot.villagerJobUpserts(), snapshot.villagerJobRemovals());
        villagerTrades().acknowledgePersisted(
                snapshot.villagerTradeUpserts(), snapshot.villagerTradeRemovals());
        if (snapshot.villagerSociety() != null) {
            runtime.villagerSociety().acknowledgePersistence(
                    snapshot.villagerSociety().revision());
        }
    }

    /**
     * Queues one durable receipt for payout. A receipt that is already {@code GRANTED} owes
     * nothing, and one without a recipient (no hero survived the arming) has nobody to pay, so
     * neither ever enters the lane.
     */
    private void rememberUnclaimedRaidPrize(RaidRewardReceipt receipt) {
        if (receipt == null || receipt.granted()
                || !RaidLedger.VICTORY_REWARD_TOKEN.equals(receipt.rewardToken())) return;
        String recipient = receipt.recipientNickname();
        if (recipient == null || recipient.isBlank()) return;
        unclaimedRaidPrizes.putIfAbsent(receipt.raidId(), recipient);
    }

    /** Durable receipts still owing a payout. Read by the persistence thread's payout lane. */
    Map<Long, String> unclaimedRaidPrizes() {
        return unclaimedRaidPrizes;
    }

    /** Drops one raid from the payout lane once its durable claim has resolved. */
    void forgetUnclaimedRaidPrize(long raidId) {
        unclaimedRaidPrizes.remove(raidId);
    }

    boolean hasUnpersistedPopulationChunks() {
        return !unpersistedPopulationChunkKeys.isEmpty()
                || !unpersistedStructureOccupantClaims.isEmpty()
                || villagerJobClaims.hasUnpersistedRows()
                || villagerTrades().hasUnpersistedRows()
                || runtime.villagerSociety().hasUnpersistedPersistence()
                || runtime.trialSpawners().hasPendingPersistenceEdges();
    }

    /**
     * 거래 진행도 lane 의 소유자는 틱 루프다(세션·거리 검증이 거기 있다). 영속 배치만 몹 lane 과
     * 같은 스냅샷/승인 사이클을 공유해, 한 flush 가 몹·직업·거래를 같은 시점으로 저장한다.
     */
    private com.gameexpert.engine.mob.villager.VillagerTradeSessions villagerTrades() {
        WorldTickLoop loop = rt.tickLoop();
        // 픽스처는 틱 루프 없이 몹 lane 만 돌린다. 그때의 거래 lane 은 비어 있는 detached
        // 세션으로 대신해, 재입고·flush·사망 forget 경로가 조용한 no-op 이 되게 한다.
        if (loop == null) {
            if (detachedVillagerTrades == null) {
                detachedVillagerTrades = new com.gameexpert.engine.mob.villager.VillagerTradeSessions();
            }
            return detachedVillagerTrades;
        }
        return loop.villagerTrades();
    }

    private com.gameexpert.engine.mob.villager.VillagerTradeSessions detachedVillagerTrades;

    static final class PopulationPersistenceSnapshot {
        private final List<MobPersistenceSnapshot> mobs;
        private final List<Long> chunkKeys;
        private final List<long[]> chunks;
        private final Map<String, StructureOccupantClaimSnapshot> claims;
        private final List<RaidLedger.InstanceSnapshot> raids;
        private final List<RaidRewardReceipt> raidReceipts;
        private final List<com.gameexpert.engine.trial.TrialSpawnerRuntime.SiteSnapshot> trials;
        private final List<com.gameexpert.engine.trial.TrialSpawnerRuntime.StateChange>
                trialStateChanges;
        private final Map<Long, VillagerJobClaimSnapshot> villagerJobUpserts;
        private final Map<Long, Long> villagerJobRemovals;
        private final Map<Long, com.gameexpert.mob.dto.VillagerTradeSnapshot> villagerTradeUpserts;
        private final Map<Long, Long> villagerTradeRemovals;
        private final com.gameexpert.engine.mob.villager.VillagerSociety.PersistenceSnapshot
                villagerSociety;

        private PopulationPersistenceSnapshot(List<MobPersistenceSnapshot> mobs,
                                              List<Long> chunkKeys, List<long[]> chunks,
                                              Map<String, StructureOccupantClaimSnapshot> claims,
                                              List<RaidLedger.InstanceSnapshot> raids,
                                              List<RaidRewardReceipt> raidReceipts,
                                              List<com.gameexpert.engine.trial.TrialSpawnerRuntime.SiteSnapshot>
                                                      trials,
                                              List<com.gameexpert.engine.trial.TrialSpawnerRuntime.StateChange>
                                                      trialStateChanges,
                                              Map<Long, VillagerJobClaimSnapshot> villagerJobUpserts,
                                              Map<Long, Long> villagerJobRemovals,
                                              Map<Long, com.gameexpert.mob.dto.VillagerTradeSnapshot>
                                                      villagerTradeUpserts,
                                              Map<Long, Long> villagerTradeRemovals,
                                              com.gameexpert.engine.mob.villager.VillagerSociety
                                                      .PersistenceSnapshot villagerSociety) {
            this.mobs = mobs;
            this.chunkKeys = chunkKeys;
            this.chunks = chunks;
            this.claims = claims;
            this.raids = raids;
            this.raidReceipts = raidReceipts;
            this.trials = trials;
            this.trialStateChanges = trialStateChanges;
            this.villagerJobUpserts = villagerJobUpserts;
            this.villagerJobRemovals = villagerJobRemovals;
            this.villagerTradeUpserts = villagerTradeUpserts;
            this.villagerTradeRemovals = villagerTradeRemovals;
            this.villagerSociety = villagerSociety;
        }

        Map<Long, VillagerJobClaimSnapshot> villagerJobUpserts() {
            return villagerJobUpserts;
        }

        Map<Long, Long> villagerJobRemovals() {
            return villagerJobRemovals;
        }

        Map<Long, com.gameexpert.mob.dto.VillagerTradeSnapshot> villagerTradeUpserts() {
            return villagerTradeUpserts;
        }

        Map<Long, Long> villagerTradeRemovals() {
            return villagerTradeRemovals;
        }

        com.gameexpert.engine.mob.villager.VillagerSociety.PersistenceSnapshot
                villagerSociety() {
            return villagerSociety;
        }

        List<RaidLedger.InstanceSnapshot> raids() {
            return raids;
        }

        List<RaidRewardReceipt> raidReceipts() {
            return raidReceipts;
        }

        List<com.gameexpert.engine.trial.TrialSpawnerRuntime.SiteSnapshot> trials() {
            return trials;
        }

        List<com.gameexpert.engine.trial.TrialSpawnerRuntime.StateChange> trialStateChanges() {
            return trialStateChanges;
        }

        List<MobPersistenceSnapshot> mobs() {
            return mobs;
        }

        List<Long> chunkKeys() {
            return chunkKeys;
        }

        List<long[]> chunks() {
            return chunks;
        }

        Map<String, StructureOccupantClaimSnapshot> claims() {
            return claims;
        }
    }

    CombatSystem combat() {
        return combat;
    }

    /** 바닐라 MOTION_BLOCKING_NO_LEAVES 하이트맵. 몹 스폰·철 골렘 배치·낙뢰가 공유한다. */
    int motionBlockingNoLeavesHeight(int x, int z) {
        return world.motionBlockingNoLeavesHeight(x, z);
    }

    /**
     * 컬럼의 가장 높은 비-공기 블록 y. 바닐라 {@code Heightmap.WORLD_SURFACE} − 1 이며,
     * 피뢰침 유인이 "그 피뢰침이 컬럼 최상단인가"를 판정할 때 쓴다
     * ({@code ServerLevel#findLightningRod} 의 두 번째 술어).
     */
    int worldSurfaceHeight(int x, int z) {
        return world.worldSurfaceHeight(x, z);
    }

    void invalidateLightColumn(int x, int z) {
        world.invalidateLightColumn(x, z);
    }

    void invalidateLightChunk(int chunkX, int chunkZ) {
        world.invalidateLightChunk(chunkX, chunkZ);
    }

    /** Primitive, allocation-free counters for optional over-budget tick diagnostics. */
    long lightCandidateQueries() { return world.lightCandidateQueries(); }

    long lightMemoHits() { return world.lightMemoHits(); }

    long lightMemoMisses() { return world.lightMemoMisses(); }

    long lightBfsVisitedCells() { return world.lightBfsVisitedCells(); }

    int lightMaxBfsVisitCount() { return world.lightMaxBfsVisitCount(); }

    void invalidateSpawnerChunk(int chunkX, int chunkZ) {
        spawnerIndex.invalidateChunk(chunkX, chunkZ);
    }

    void recordIndexedBlockChange(int x, int y, int z, int blockType) {
        spawnerIndex.recordBlockChanged(x, y, z, blockType);
    }


    /** 월드의 모든 중복 억제 대상 사건이 공유하는 단일 증가 ID. 틱 스레드 전용. */
    long nextEventId() {
        return nextEventId++;
    }

    /**
     * Allay mobInteract authority. A non-empty selected slot gives exactly one representable filter
     * item; an empty hand takes the original filter back. The Allay transition is confirmed before
     * inventory removal, and a failed inventory return becomes one world drop instead of item loss.
     */
    boolean interactAllay(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (mob == null || mob.type != MobType.ALLAY) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot selected = inventory.stack(hand);
        if (!selected.isEmpty()) {
            // Allay persistence currently carries exact type+durability. Reject richer stacks rather
            // than silently stripping their map identity or enchantment components on take/return.
            if (selected.enchantments() != EnchantmentRules.EMPTY_ENCHANTMENTS
                    || selected.mapId() != 0) return true;
            if (!runtime.giveAllayItem(mob.id, player.nickname(),
                    selected.itemType(), selected.durability())) return true;
            if (!inventory.consumeOne(hand, selected.itemType())) {
                throw new IllegalStateException("confirmed Allay give lost its selected item");
            }
            sendInventory(player);
            return true;
        }

        Mob.EquipmentDrop returned = runtime.takeAllayItem(mob.id, player.nickname());
        if (returned == null) return true;
        int inserted = inventory.addItem(returned.itemType(), 1, returned.durability());
        if (inserted == 1) {
            sendInventory(player);
        } else {
            rt.itemSystem().spawnAllayReturnDrop(mob.id, returned.itemType(), 1,
                    returned.durability(), mob.x, mob.y, mob.z);
        }
        return true;
    }

    /**
     * mobInteract의 거리·생존 검증 뒤 호출하는 동물 먹이 처리 경계.
     * love mode/성장 판정은 runtime, 선택 슬롯 1개 소비와 inventoryUpdate는 이 서버 경계가 맡는다.
     */
    boolean feedAnimal(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        short itemType = inventory.stack(hand).itemType();
        // 주민은 동물 love-mode 가 아니라 willing 음식 재고 lane 을 쓴다(VillagerMakeLove).
        if (mob != null && mob.type == MobType.VILLAGER) {
            if (!runtime.offerVillagerFood(mob, itemType)) return false;
            if (!inventory.consumeOne(hand, itemType)) return false;
            WebSocketSession villagerSession = rt.session(player.nickname());
            if (villagerSession != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), villagerSession,
                        WorldTickLoop.inventoryMessage(player));
            }
            return true;
        }
        MobRuntime.FeedResult result = runtime.feedBreedingFood(mob, itemType);
        if (result != MobRuntime.FeedResult.LOVE_MODE
                && result != MobRuntime.FeedResult.BABY_GROWTH) return false;
        // 양동이 먹이(열대어 양동이)는 소비 대신 빈 용기를 같은 칸에 돌려준다.
        if (itemType == PlayerInventory.TROPICAL_FISH_BUCKET) {
            if (!InventoryRules.replaceBucket(inventory, hand,
                    PlayerInventory.TROPICAL_FISH_BUCKET, PlayerInventory.WATER_BUCKET)) {
                return false;
            }
        } else {
            if (!inventory.consumeOne(hand, itemType)) return false;
        }
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                    WorldTickLoop.inventoryMessage(player));
        }
        return true;
    }

    /** Name-tag/leash precedence has already run; all effects consume the captured hand once. */
    String interactCompanion(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        short item = player.inventory().stack(hand).itemType();
        boolean food = com.gameexpert.engine.mob.CompanionRules.isFood(mob.type, item);
        if (mob.ownerNickname() == null && food) {
            if (!player.inventory().consumeOne(hand, item)) return "rejected";
            boolean success = rng.nextInt(mob.type == MobType.CAT ? 3 : 10) == 0
                    && mob.tameCompanion(player.nickname());
            // Cat.mobInteract makes even an unsuccessful fish attempt persistent.
            if (mob.type == MobType.CAT) mob.setPersistenceRequired(true);
            mob.companionFoodFeedback(success);
            runtime.refreshPersistenceSnapshot(mob);
            sendInventory(player);
            return "consumed";
        }
        if (mob.type == MobType.PARROT && item == PlayerInventory.COOKIE) {
            if (!player.inventory().consumeOne(hand, item)) return "rejected";
            mob.applyStatusEffect(StatusEffect.POISON, 0, 450);
            mob.damage(Float.MAX_VALUE);
            pendingDespawnReasons.put(mob.id, "death");
            spawnMobDrops(mob, true);
            mobDropsHandled.add(mob.id);
            broadcast(new WsMessages.MobHurt(mob.id, true));
            sendInventory(player);
            return "consumed";
        }
        if (mob.type == MobType.CAT) {
            int color = Short.toUnsignedInt(item) - Short.toUnsignedInt(PlayerInventory.WHITE_DYE);
            if (color >= 0 && color <= 15 && player.nickname().equals(mob.ownerNickname())
                    && color != mob.catCollarColor()) {
                if (!player.inventory().consumeOne(hand, item)) return "rejected";
                mob.dyeCatCollar(player.nickname(), color);
                runtime.refreshPersistenceSnapshot(mob);
                sendInventory(player);
                return "consumed";
            }
            if (food && player.nickname().equals(mob.ownerNickname()) && !mob.isAtFullHealth()) {
                if (!player.inventory().consumeOne(hand, item)) return "rejected";
                mob.heal(2); // Raw cod and raw salmon both carry nutrition 2.
                mob.companionFoodFeedback(false);
                runtime.refreshPersistenceSnapshot(mob);
                sendInventory(player);
                return "consumed";
            }
            if (food && feedAnimal(player, hand, mob)) {
                mob.companionFoodFeedback(false);
                runtime.refreshPersistenceSnapshot(mob);
                return "consumed";
            }
        }
        if (!mob.toggleCompanionSitting(player.nickname())) return "pass";
        runtime.refreshPersistenceSnapshot(mob);
        return "consumed";
    }

    /** Wolf bone taming consumes one bone per attempt and succeeds on the source-locked 1/3 roll. */
    boolean tameWolf(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (mob.type != MobType.WOLF || mob.ownerNickname() != null) return false;
        PlayerInventory inventory = player.inventory();
        if (!inventory.consumeOne(hand, PlayerInventory.BONE)) return false;
        boolean tamed = rng.nextInt(3) == 0 && mob.tameWolf(player.nickname());
        if (tamed) runtime.refreshPersistenceSnapshot(mob);
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                    WorldTickLoop.inventoryMessage(player));
        }
        return tamed;
    }

    /** Untamed Ocelots consume raw cod/salmon per attempt and trust the feeding player on 1/3. */
    boolean trustOcelot(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (mob.type != MobType.OCELOT || mob.ocelotTrusting()) return false;
        PlayerInventory inventory = player.inventory();
        short item = inventory.stack(hand).itemType();
        if ((item != PlayerInventory.COD_RAW && item != PlayerInventory.SALMON_RAW)
                || !inventory.consumeOne(hand, item)) return false;
        boolean trusted = rng.nextInt(3) == 0 && mob.trustOcelot();
        if (trusted) runtime.refreshPersistenceSnapshot(mob);
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                    WorldTickLoop.inventoryMessage(player));
        }
        return trusted;
    }

    boolean toggleWolfSitting(PlayerTickState player, Mob mob) {
        if (!mob.toggleWolfSitting(player.nickname())) return false;
        runtime.refreshPersistenceSnapshot(mob);
        return true;
    }

    boolean dyeWolfCollar(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        short held = inventory.stack(hand).itemType();
        int item = Short.toUnsignedInt(held);
        int first = Short.toUnsignedInt(PlayerInventory.WHITE_DYE);
        int last = Short.toUnsignedInt(PlayerInventory.BLACK_DYE);
        if (item < first || item > last
                || !mob.dyeWolfCollar(player.nickname(), item - first)) return false;
        if (!inventory.consumeOne(hand, held)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    boolean equipWolfBodyArmor(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.WOLF_ARMOR
                || !mob.equipWolfBodyArmor(player.nickname())
                || !inventory.consumeOne(hand, PlayerInventory.WOLF_ARMOR)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    boolean repairWolfBodyArmor(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.ARMADILLO_SCUTE
                || !mob.repairWolfBodyArmor(player.nickname())
                || !inventory.consumeOne(hand, PlayerInventory.ARMADILLO_SCUTE)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    boolean removeWolfBodyArmor(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.SHEARS) return false;
        int durability = mob.removeWolfBodyArmor(player.nickname());
        if (durability <= 0) return false;
        inventory.degrade(hand);
        rt.itemSystem().spawnDeathDrop(PlayerInventory.WOLF_ARMOR, 1, durability,
                mob.x, mob.y + 0.5, mob.z);
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    // ── [FARM-ANIMAL] 양 전단·염색, 돼지 안장·탑승·부스트(MOB.md §4) ──

    /**
     * 가위 우클릭 전단. 바닐라와 같이 양털 1~3 개를 떨구고 가위가 1 닳으며 {@code sheared} 가 선다.
     * 드랍 품목은 바닐라와 같이 <b>양의 색</b>이 정한다 — {@code sheepColor()} 가 MC
     * {@code DyeColor} 네트워크 ID 이므로 {@link Blocks#WOOL_BY_DYE_COLOR} 를 그대로 색인한다.
     * (사망 드랍도 같은 표를 쓴다 — {@link #animalDrops} 의 {@code sheepColor} 인자.)
     */
    boolean shearSheep(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.SHEARS
                || !mob.readyForShearing()) {
            return false;
        }
        int wool = FarmAnimalRules.shearWoolCount(rng);
        int color = mob.sheepColor();
        if (color < 0 || color >= Blocks.WOOL_BY_DYE_COLOR.length) return false;
        short woolItem = (short) Blocks.WOOL_BY_DYE_COLOR[color];
        mob.shearSheep();
        inventory.degrade(hand);
        rt.itemSystem().spawnDeathDrop(woolItem, wool, 0,
                mob.x, mob.y + 0.5, mob.z);
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    boolean shearSulfurCube(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof SulfurCube cube)
                || player.inventory().stack(hand).itemType() != PlayerInventory.SHEARS) return false;
        short body = cube.shearBodyItem();
        if (body == 0) return false;
        player.inventory().degrade(hand);
        rt.itemSystem().spawnDeathDrop(body, 1, 0, mob.x, mob.y + mob.height() * .5, mob.z);
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * [CONTAINER-MENUS] Shears on a snow golem or a bogged, from a player or a dispenser.
     * {@code SnowGolem#shear}: {@code setPumpkin(false)} and the {@code shearing/snow_golem} loot
     * (one carved pumpkin) at eye height. {@code Bogged#shear}: the {@code shearing/bogged} loot (two
     * rolls of brown or red mushroom, equal weight) at the top of its box, then {@code DATA_SHEARED}.
     */
    boolean shearSnowGolemOrBogged(Mob mob) {
        if (mob instanceof com.gameexpert.engine.mob.SnowGolem golem && golem.readyForShears()) {
            golem.shear();
            rt.itemSystem().spawnDeathDrop((short) Blocks.CARVED_PUMPKIN, 1, 0,
                    mob.x, mob.y + mob.eyeHeight(), mob.z);
        } else if (mob instanceof com.gameexpert.engine.mob.Bogged bogged
                && bogged.readyForShears()) {
            for (int roll = 0; roll < 2; roll++) {
                short mushroom = (short) (rng.nextInt(2) == 0 ? Blocks.MUSHROOM_BROWN
                        : Blocks.MUSHROOM_RED);
                rt.itemSystem().spawnDeathDrop(mushroom, 1, 0, mob.x, mob.y + mob.height(), mob.z);
            }
            bogged.shear();
        } else {
            return false;
        }
        mob.setPersistenceRequired(true);
        runtime.refreshPersistenceSnapshot(mob);
        return true;
    }

    /** [CONTAINER-MENUS] A player's shears on a snow golem or a bogged (one point of wear). */
    boolean shearSnowGolemOrBogged(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (player.inventory().stack(hand).itemType() != PlayerInventory.SHEARS
                || !shearSnowGolemOrBogged(mob)) return false;
        player.inventory().degrade(hand);
        sendInventory(player);
        return true;
    }

    boolean igniteSulfurCube(Mob mob) {
        if (!(mob instanceof SulfurCube cube) || !cube.prime(false, rng)) return false;
        runtime.refreshPersistenceSnapshot(cube);
        return true;
    }

    /** 염료 우클릭. 전단되지 않은 양의 색이 실제로 바뀔 때만 염료 한 개를 소비한다. */
    boolean dyeSheep(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        short held = inventory.stack(hand).itemType();
        int item = Short.toUnsignedInt(held);
        int first = Short.toUnsignedInt(PlayerInventory.WHITE_DYE);
        int last = Short.toUnsignedInt(PlayerInventory.BLACK_DYE);
        if (item < first || item > last || !mob.dyeSheep(item - first)) return false;
        if (!inventory.consumeOne(hand, held)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /** 안장 장착. 새끼가 아니고 아직 안장이 없는 돼지만 받는다. */
    boolean saddlePig(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.SADDLE || !mob.saddlePig()) {
            return false;
        }
        if (!inventory.consumeOne(hand, PlayerInventory.SADDLE)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /** 탑승. 성공하면 이후 좌표 정본은 기수 클라이언트로 넘어간다(보트와 같은 계약). */
    boolean mountPig(PlayerTickState player, Mob mob) {
        return mountMob(player, mob, MobMountRules.CONTROLLING_SEAT_INDEX);
    }

    /**
     * [MOUNT] 종 비의존 탑승. 좌석 원장은 하나뿐이라 한 사람은 동시에 한 좌석에만 앉는다.
     * 돼지는 하위호환을 위해 기존 {@code pigMount} 를, 그 밖의 종은 일반 {@code mobMount} 를
     * 방송한다(append-only — 기존 클라의 돼지 경로는 한 글자도 바뀌지 않는다).
     */
    boolean mountMob(PlayerTickState player, Mob mob, int seatIndex) {
        // QA 관측: 탑승 거절 사유를 남긴다(실측: 길들이고 안장까지 얹은 자연 노틸러스가 이유 없이
        // 탑승을 거절해 보였다). 게임플레이 판정은 바꾸지 않는다.
        if (seatOf.containsKey(player.nickname())) {
            log.info("mount refused: {} already seated on {}", player.nickname(), seatOf.get(player.nickname()));
            return false; // 이미 다른 탈것에 앉아 있다
        }
        if (!MobMountRules.validSeat(mob.type, seatIndex)) return false;
        if (seatIndex == MobMountRules.CONTROLLING_SEAT_INDEX
                && runtime.hasMobPassenger(mob.id)) {
            log.info("mount refused: mob {} {} carries a mob passenger", mob.id, mob.type);
            return false;
        }
        if (!mob.mountSeat(player.nickname(), seatIndex)) {
            log.info("mount refused by seat: mob {} {} owner={} rider={} baby={} dead={}",
                    mob.id, mob.type, mob.ownerNickname(), mob.seatRider(seatIndex), mob.isBaby(), mob.isDead());
            return false;
        }
        seatOf.put(player.nickname(), new Seat(mob.id, seatIndex, mob.type));
        runtime.refreshPersistenceSnapshot(mob);
        if (mob.type == MobType.PIG) {
            broadcast(new WsMessages.PigMount(mob.id, player.nickname()));
        } else {
            broadcast(new WsMessages.MobMount(mob.id, player.nickname(), seatIndex,
                    mob.type.name().toLowerCase(java.util.Locale.ROOT),
                    mountedSpeedBlocksPerSecond(mob), mountedJumpStrength(mob),
                    MobMountRules.controllingSeat(mob.type, seatIndex, mountedSaddled(mob))));
        }
        sendMountedHud(player.nickname(), mob, seatIndex, true);
        return true;
    }

    /**
     * [MOUNT] 탑승 확정에 실어 보내는 이동 속도(블록/초). 낙타는 개체 스탯이 없고 종 상수라
     * {@link CamelRules} 가 정본이며, 달리기 보너스는 기수 클라가 같은 규칙 사본으로 얹는다.
     */
    private static double mountedSpeedBlocksPerSecond(Mob mob) {
        return switch (mob.type) {
            case CAMEL ->
                    CamelRules.riddenSpeedBlocksPerSecond(false, mob.camelDashCooldownMcTicks());
            // [HARNESS] 해피 가스트는 개체 스탯이 없고 종 상수라 규칙 파일이 정본이다.
            case HAPPY_GHAST -> HappyGhastRules.riddenSpeedBlocksPerSecond();
            // [NAUTILUS-MOUNT] 수중 탈것도 개체 스탯이 없고 종 상수라 규칙 파일이 정본이다.
            case NAUTILUS, ZOMBIE_NAUTILUS -> NautilusMountRules.riddenSpeedBlocksPerSecond();
            default -> HorseRules.riddenSpeedBlocksPerSecond(mob.horseSpeedStat());
        };
    }

    /**
     * [MOUNT] 이 개체가 안장을 얹고 있는가. 종마다 안장 상태가 다른 필드에 있어 조종 게이트가
     * 보는 단일 통로를 둔다(라마는 안장 슬롯 자체가 없어 언제나 거짓이다).
     */
    private static boolean mountedSaddled(Mob mob) {
        return switch (mob.type) {
            case PIG -> mob.pigSaddled();
            case CAMEL -> mob.camelSaddled();
            // [HARNESS] 해피 가스트에서 안장 자리에 있는 것이 하네스다. 조종 게이트
            // (MobMountRules.steerable)가 보는 값이 종마다 다른 필드에 있으므로 여기서 접는다.
            case HAPPY_GHAST -> mob.happyGhastHarnessed();
            // [NAUTILUS-MOUNT] 노틸러스의 조종 게이트는 안장이다(말·낙타·돼지와 같은 어휘).
            case NAUTILUS, ZOMBIE_NAUTILUS -> mob.nautilusSaddled();
            default -> mob.horseSaddled();
        };
    }

    /** [MOUNT] 같은 방송의 점프 강도. 낙타는 이 값으로 <b>대시</b>한다. */
    private static double mountedJumpStrength(Mob mob) {
        return switch (mob.type) {
            case CAMEL -> CamelRules.JUMP_STRENGTH;
            // [HARNESS] 비행 탈것이라 도약도 대시도 없다(HappyGhastRules.JUMP_STRENGTH = 0).
            case HAPPY_GHAST -> HappyGhastRules.JUMP_STRENGTH;
            // [NAUTILUS-MOUNT] 수중 탈것이라 도약도 대시도 없다(비행 탈것과 같은 이유로 0).
            case NAUTILUS, ZOMBIE_NAUTILUS -> NautilusMountRules.JUMP_STRENGTH;
            default -> mob.horseJumpStrengthStat();
        };
    }

    /**
     * [HARNESS] 하네스 장착. <b>성체</b> 해피 가스트이고 아직 쓰고 있지 않을 때만 받는다
     * ([B] «Happy Ghast» — 가스틀링에는 씌울 수 없다). 성공하면 손에 든 하네스 하나를 쓰고,
     * 그 색이 개체 상태가 되어 {@link Mob#VISUAL_HAPPY_GHAST_HARNESSED} 비트가 선다.
     */
    boolean harnessHappyGhast(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        short held = inventory.stack(hand).itemType();
        int color = PlayerInventory.harnessDyeColor(held);
        if (color < 0 || !mob.harnessHappyGhast(color)) return false;
        if (!inventory.consumeOne(hand, held)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * [NAUTILUS-MOUNT] 노틸러스 갑옷 장착. <b>길들인 성체</b>일 때만 받는다([B] §8-2).
     * 성공하면 손에 든 갑옷 하나를 쓰고 그 티어가 개체 상태가 되어
     * {@link Mob#VISUAL_NAUTILUS_ARMORED} 비트가 선다(하네스와 같은 자리·같은 형태).
     */
    boolean equipNautilusArmor(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        short held = inventory.stack(hand).itemType();
        int tier = NautilusMountRules.armorTier(Short.toUnsignedInt(held));
        if (tier == NautilusMountRules.NO_ARMOR || !mob.equipNautilusArmor(tier)) return false;
        if (!inventory.consumeOne(hand, held)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /** Shears remove the exact body-armor item while no player passenger is mounted. */
    boolean removeNautilusArmor(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.SHEARS) return false;
        int removedItem = mob.removeNautilusArmor();
        if (removedItem == 0) return false;
        inventory.degrade(hand);
        short returned = (short) removedItem;
        if (inventory.addItem(returned, 1, 0) != 1) {
            rt.itemSystem().spawnDeathDrop(returned, 1, 0, mob.x, mob.y + 0.5, mob.z);
        }
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /** Immutable ordinary-Horse armor plan carrying the complete held stack identity. */
    record HorseArmorEquipPlan(Mob mob, PlayerInventory.StackSnapshot heldStack) {
        /** Compatibility constructor for the type-only central dispatch handoff. */
        HorseArmorEquipPlan(Mob mob, short armorItem) {
            this(mob, defaultSingleItem(armorItem));
        }

        short armorItem() {
            return heldStack == null ? PlayerInventory.EMPTY : heldStack.itemType();
        }
    }

    /** Immutable removal result carrying the exact installed armor stack. */
    record HorseArmorRemovalPlan(Mob mob, PlayerInventory.StackSnapshot armorStack) {
        /** Compatibility constructor for the former type-only removal handoff. */
        HorseArmorRemovalPlan(Mob mob, short armorItem) {
            this(mob, defaultSingleItem(armorItem));
        }

        short armorItem() {
            return armorStack == null ? PlayerInventory.EMPTY : armorStack.itemType();
        }
    }

    @FunctionalInterface
    private interface HorseMenuMutation {
        boolean apply();
    }

    private record HeldConsumptionPlan(PlayerInventory inventory,
            PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot held,
            PlayerInventory.CompletePersistenceSnapshot source,
            PlayerInventory.CompletePersistenceSnapshot committed) { }

    private static PlayerInventory.StackSnapshot defaultSingleItem(short itemType) {
        if (itemType == PlayerInventory.EMPTY
                || !PlayerInventory.isRegisteredItemType(itemType)) {
            return PlayerInventory.StackSnapshot.EMPTY;
        }
        return new PlayerInventory.StackSnapshot(itemType, 1,
                PlayerInventory.isDurable(itemType)
                        ? PlayerInventory.initialDurability(itemType) : 0,
                EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
    }

    private static PlayerInventory.StackSnapshot singleItem(
            PlayerInventory.StackSnapshot stack) {
        if (stack == null || stack.isEmpty()) return PlayerInventory.StackSnapshot.EMPTY;
        if (stack.count() == 1) return stack;
        return new PlayerInventory.StackSnapshot(stack.itemType(), 1, stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData());
    }

    private static PlayerInventory.StackSnapshot afterOne(
            PlayerInventory.StackSnapshot stack) {
        if (stack.count() == 1) return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(stack.itemType(), stack.count() - 1,
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData());
    }

    private static PlayerInventory.StackSnapshot horseMenuStack(Mob mob, int slot) {
        ChestInventory equipment = mob == null ? null : mob.horseEquipment();
        if (equipment == null || slot < 0 || slot >= equipment.slots()
                || equipment.itemType(slot) == PlayerInventory.EMPTY || equipment.count(slot) <= 0) {
            return PlayerInventory.StackSnapshot.EMPTY;
        }
        return new PlayerInventory.StackSnapshot(equipment.itemType(slot), equipment.count(slot),
                equipment.durability(slot), equipment.enchantments(slot), equipment.mapId(slot),
                equipment.shulkerId(slot), equipment.bucketMobData(slot),
                equipment.itemComponentData(slot));
    }

    private static boolean liveHorseMenuTarget(Mob mob) {
        return mob != null && !mob.isDead() && !mob.removed && mob.horseEquipment() != null;
    }

    /**
     * The shared token and the menu-local token are both checked before touching the player.
     * The local check prevents an equipment write from failing after inventory consumption.
     */
    private static boolean preflightHorseMenuMutation(Mob mob) {
        if (!liveHorseMenuTarget(mob)) return false;
        mob.preflightHorseMenuPersistenceRevision();
        if (mob.horseEquipment().persistenceRevision() >= Long.MAX_VALUE - 1) {
            throw new IllegalStateException("horse equipment persistence revision is exhausted");
        }
        return true;
    }

    /**
     * Plans the exact one-item consumption on a leased detached inventory. The live inventory is
     * untouched until the mob mutation has succeeded, and the lease makes the later installation
     * atomic against any competing inventory writer.
     */
    private static HeldConsumptionPlan planExactHeldConsumption(PlayerInventory inventory,
            PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot held) {
        if (inventory == null || hand == null || held == null || held.isEmpty()
                || !inventory.stack(hand).equals(held)) return null;
        PlayerInventory.CompletePersistenceSnapshot source;
        try {
            source = inventory.acquireSettlementLease();
        } catch (IllegalStateException unavailable) {
            return null;
        }
        if (source == null) return null;
        try {
            PlayerInventory planned = source.detachedInventory();
            // Rebind the captured slot, not the copy's current selection: the player may have
            // scrolled the hotbar between capture and settlement.
            PlayerInventory.HandRef detachedHand = planned.rebindHand(hand);
            if (detachedHand == null || !planned.stack(detachedHand).equals(held)
                    || !planned.setStack(detachedHand, afterOne(held))) {
                inventory.releaseSettlementLease(source);
                return null;
            }
            PlayerInventory.CompletePersistenceSnapshot committed =
                    planned.completePersistenceSnapshot();
            return new HeldConsumptionPlan(inventory, hand, held, source, committed);
        } catch (IllegalStateException unavailable) {
            inventory.releaseSettlementLease(source);
            return null;
        } catch (RuntimeException failure) {
            inventory.releaseSettlementLease(source);
            throw failure;
        }
    }

    /** Applies a preplanned mob mutation, then installs the already-validated inventory snapshot. */
    private static boolean applyPlannedHeldConsumption(HeldConsumptionPlan plan,
            HorseMenuMutation mutation) {
        if (plan == null) return false;
        final boolean applied;
        try {
            applied = mutation.apply();
        } catch (RuntimeException failure) {
            plan.inventory().releaseSettlementLease(plan.source());
            throw failure;
        }
        if (!applied) {
            plan.inventory().releaseSettlementLease(plan.source());
            return false;
        }
        if (!plan.inventory().installCommittedSettlement(plan.source(),
                plan.committed())) {
            plan.inventory().releaseSettlementLease(plan.source());
            throw new IllegalStateException("failed to install planned held-stack consumption");
        }
        return true;
    }

    private static boolean canEquipHorseArmor(Mob mob, PlayerInventory.StackSnapshot armor) {
        return liveHorseMenuTarget(mob) && mob.type == MobType.HORSE && !mob.isBaby()
                && mob.horseTamed() && mob.horseArmorItem() == PlayerInventory.EMPTY
                && armor != null && !armor.isEmpty() && armor.count() == 1
                && HorseRules.isArmorItem(armor.itemType());
    }

    static HorseArmorEquipPlan planEquipHorseArmor(Mob mob, short armorItem) {
        if (!HorseRules.isArmorItem(armorItem)) return null;
        return planEquipHorseArmor(mob, defaultSingleItem(armorItem));
    }

    /** Plans from the exact held stack; no mob or inventory state is mutated. */
    static HorseArmorEquipPlan planEquipHorseArmor(Mob mob,
            PlayerInventory.StackSnapshot armor) {
        if (!canEquipHorseArmor(mob, singleItem(armor))) return null;
        if (!preflightHorseMenuMutation(mob)) return null;
        return new HorseArmorEquipPlan(mob, armor);
    }

    /**
     * Commits a previously validated type-only plan after central dispatch has reserved one exact
     * stack. Full-stack plans preserve every component through the horse-menu ingress.
     */
    boolean confirmEquipHorseArmor(HorseArmorEquipPlan plan) {
        if (plan == null || !canEquipHorseArmor(plan.mob(), singleItem(plan.heldStack()))) {
            return false;
        }
        if (!preflightHorseMenuMutation(plan.mob())
                || !plan.mob().equipHorseArmor(singleItem(plan.heldStack()))) return false;
        runtime.refreshPersistenceSnapshot(plan.mob());
        return true;
    }

    /** Full HMI handoff: validates the exact held stack before consuming or mutating the mob. */
    boolean confirmEquipHorseArmor(PlayerTickState player, PlayerInventory.HandRef hand,
            HorseArmorEquipPlan plan) {
        if (player == null || hand == null || plan == null || !canEquipHorseArmor(plan.mob(),
                singleItem(plan.heldStack()))) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        if (!held.equals(plan.heldStack()) || !preflightHorseMenuMutation(plan.mob())) return false;
        if (!applyPlannedHeldConsumption(planExactHeldConsumption(inventory, hand, held),
                () -> plan.mob().equipHorseArmor(singleItem(held)))) return false;
        runtime.refreshPersistenceSnapshot(plan.mob());
        sendInventory(player);
        return true;
    }

    /** Direct full-stack armor action used by the live HMI path. */
    boolean equipHorseArmor(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (player == null || hand == null || mob == null) return false;
        PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
        HorseArmorEquipPlan plan = planEquipHorseArmor(mob, held);
        return confirmEquipHorseArmor(player, hand, plan);
    }

    static HorseArmorRemovalPlan planRemoveHorseArmor(Mob mob) {
        PlayerInventory.StackSnapshot armor = horseMenuStack(mob, 1);
        if (mob == null || mob.type != MobType.HORSE || mob.isDead()
                || mob.horseRiderNickname() != null || armor.isEmpty()
                || !HorseRules.isArmorItem(armor.itemType())) return null;
        return new HorseArmorRemovalPlan(mob, armor);
    }

    /** Commits removal only if the exact planned full stack is still installed. */
    boolean confirmRemoveHorseArmor(HorseArmorRemovalPlan plan) {
        if (plan == null || !canRemoveHorseArmor(plan.mob())
                || !horseMenuStack(plan.mob(), 1).equals(plan.armorStack())
                || !preflightHorseMenuMutation(plan.mob())) return false;
        PlayerInventory.StackSnapshot removed = plan.mob().removeHorseArmorStack();
        if (!removed.equals(plan.armorStack())) return false;
        runtime.refreshPersistenceSnapshot(plan.mob());
        return true;
    }

    private static boolean canRemoveHorseArmor(Mob mob) {
        PlayerInventory.StackSnapshot armor = horseMenuStack(mob, 1);
        return mob != null && mob.type == MobType.HORSE && !mob.isDead() && !mob.removed
                && mob.horseRiderNickname() == null && !armor.isEmpty()
                && HorseRules.isArmorItem(armor.itemType());
    }

    record MooshroomBowlPlan(Mob mooshroom, short resultItem) { }

    static MooshroomBowlPlan planFillBowlFromMooshroom(Mob mob) {
        short result = MooshroomRules.plannedStew(mob);
        return result == PlayerInventory.EMPTY ? null : new MooshroomBowlPlan(mob, result);
    }

    boolean confirmFillBowlFromMooshroom(MooshroomBowlPlan plan) {
        if (plan == null || !MooshroomRules.confirmStew(plan.mooshroom(), plan.resultItem())) {
            return false;
        }
        runtime.refreshPersistenceSnapshot(plan.mooshroom());
        return true;
    }

    boolean feedMooshroomFlower(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot before = inventory.stack(hand);
        short flower = before.itemType();
        if (!com.gameexpert.engine.SuspiciousStewRules.isStewFlower(
                Short.toUnsignedInt(flower)) || !inventory.consumeOne(hand, flower)) return false;
        if (!MooshroomRules.feedFlower(mob, Short.toUnsignedInt(flower))) {
            inventory.setStack(inventory.capture(hand.hand()), before);
            return false;
        }
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        var feedback = new WsMessages.MooshroomFeed(mob.id, mob.x, mob.y + mob.height() * 0.5, mob.z);
        for (PlayerTickState observer : rt.players().values()) {
            if (!SoundRules.audible(mob.x, mob.y, mob.z, observer.x(), observer.y(), observer.z(), 32)) continue;
            WebSocketSession session = rt.session(observer.nickname());
            if (session != null) rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, feedback);
        }
        return true;
    }

    MobRuntime.MooshroomShearPlan planShearMooshroom(Mob mob) {
        return MobRuntime.planShearMooshroom(mob);
    }

    /** Commits conversion and deterministic five-mushroom settlement; inventory/shears stay central. */
    Mob confirmShearMooshroom(MobRuntime.MooshroomShearPlan plan) {
        Mob converted = runtime.confirmShearMooshroom(plan);
        if (converted == null) return null;
        for (int i = 0; i < plan.mushroomCount(); i++) {
            rt.itemSystem().spawnBlockDrop((short) plan.mushroomBlock(),
                    plan.source().x, plan.source().y + 0.5, plan.source().z);
        }
        pendingDespawnReasons.put(plan.source().id, "converted");
        return converted;
    }

    List<MobRuntime.GoatRamImpactRequest> pendingGoatRamImpacts() {
        return runtime.pendingGoatRamImpacts();
    }

    MobRuntime.GoatHornDropPlan planGoatHornDropAfterRam(
            MobRuntime.GoatRamImpactRequest impact) {
        return MobRuntime.planGoatHornDropAfterRam(impact);
    }

    /** Commits one exact horn and emits it without changing loot-table RNG. */
    boolean confirmGoatHornDrop(MobRuntime.GoatHornDropPlan plan) {
        short horn = runtime.confirmGoatHornDrop(plan);
        if (horn == 0) return false;
        rt.itemSystem().spawnMobDrop(horn, 1,
                plan.goat().x, plan.goat().y + 0.5, plan.goat().z);
        return true;
    }

    boolean confirmGoatRamImpactWithoutHorn(MobRuntime.GoatRamImpactRequest request) {
        return runtime.confirmGoatRamImpactWithoutHorn(request);
    }

    List<MobRuntime.SnifferDigDropRequest> pendingSnifferDigDrops() {
        return runtime.pendingSnifferDigDrops();
    }

    /** Emits one persisted digging-loot request only after exact central confirmation. */
    boolean confirmSnifferDigDrop(MobRuntime.SnifferDigDropRequest request) {
        if (!runtime.confirmSnifferDigDrop(request)) return false;
        rt.itemSystem().spawnMobDrop(request.itemType(), 1,
                request.x(), request.y(), request.z());
        return true;
    }

    MobPersistenceSnapshot snifferClearedSettlementSnapshot(
            MobRuntime.SnifferDigDropRequest request) {
        if (request == null || request.sniffer().snifferPendingDropSequence() != request.sequence()
                || request.sniffer().snifferPendingDropItem() != request.itemType()) return null;
        Mob mob = request.sniffer();
        MobPersistenceSnapshot snapshot = runtime.detachedPersistenceSnapshot(mob);
        return snapshot == null ? null : snapshot.withSnifferDigState(
                mob.snifferDigPhase().name(), mob.snifferDigCooldownMcTicks(),
                mob.snifferDigPhaseMcTicks(), mob.snifferDigDropDelayMcTicks(),
                mob.snifferDigTargetX(), mob.snifferDigTargetY(), mob.snifferDigTargetZ(),
                mob.snifferDigSearchEpoch(), mob.snifferDigSequence(), 0, (short) 0);
    }

    MobPersistenceSnapshot persistenceSnapshot(Mob mob) {
        if (mob == null) return null;
        return runtime.persistenceSnapshot(mob.id);
    }

    /** Returns a current row detached from the snapshot published to persistence readers. */
    MobPersistenceSnapshot detachedPersistenceSnapshot(Mob mob) {
        return runtime.detachedPersistenceSnapshot(mob);
    }

    boolean confirmSnifferDigDropToken(MobRuntime.SnifferDigDropRequest request) {
        return runtime.confirmSnifferDigDrop(request);
    }

    void emitCommittedMobSound(long mobId, String kind) {
        Mob source = findMob(mobId);
        if (source != null && !source.isDead() && !source.removed) mobSound(source, kind);
    }

    List<BeeHiveEntryRequest> drainBeeHiveEntries() {
        if (pendingBeeHiveEntries.isEmpty()) return List.of();
        List<BeeHiveEntryRequest> drained = new ArrayList<>(pendingBeeHiveEntries);
        pendingBeeHiveEntries.clear();
        return drained;
    }

    List<FroglightSettlementRequest> drainFroglightSettlements() {
        if (pendingFroglightSettlements.isEmpty()) return List.of();
        List<FroglightSettlementRequest> drained = new ArrayList<>(pendingFroglightSettlements);
        pendingFroglightSettlements.clear();
        return List.copyOf(drained);
    }

    boolean hasPendingFroglightSettlements() {
        return !pendingFroglightSettlements.isEmpty();
    }

    boolean confirmFroglightSettlement(long sulfurCubeMobId) {
        return runtime.confirmFroglightSettlement(sulfurCubeMobId);
    }

    boolean confirmBeeHiveEntry(BeeHiveEntryRequest request) {
        return request != null && runtime.confirmBeeHiveEntry(
                request.mobId(), request.x(), request.y(), request.z());
    }

    int commitBeeHiveEntry(BeeHiveEntryRequest request) {
        if (!confirmBeeHiveEntry(request)) return 0;
        // Bee.growCrop: one percent of successful nectar deliveries increases honey by two.
        return rng.nextInt(100) == 0 ? 2 : 1;
    }

    int releaseBeesFromHive(int x, int y, int z, String angerTargetNickname) {
        return runtime.releaseBeesFromHive(x, y, z, angerTargetNickname);
    }

    void installNaturalBeeNestOccupants(int x, int y, int z, int residentCount) {
        String key = "BEE_NEST:" + x + ':' + z;
        if (knownStructureOccupantClaims.contains(key)) return;
        List<Mob> added = runtime.addInitialBeeNestOccupants(x, y, z, residentCount);
        long siteKey = Integer.toUnsignedLong(mixStructurePosition(
                rt.seed() ^ x * 31 ^ y * 193 ^ z * 389));
        StructureOccupantClaimSnapshot claim = new StructureOccupantClaimSnapshot(
                "BEE_NEST", x, z, siteKey, STRUCTURE_OCCUPANT_POLICY_VERSION, added.size());
        knownStructureOccupantClaims.add(key);
        unpersistedStructureOccupantClaims.put(key, claim);
    }

    /**
     * Installs one validated final-carrier BEES plan atomically with its durable claim snapshot.
     * All existing receipts are checked before any mob identity is allocated, so a mismatching
     * replay fails closed without partially activating the plan.
     */
    void installFinalCarrierBeeNestOccupants(Mc263BeeSidecarPlan.Plan plan) {
        java.util.Objects.requireNonNull(plan, "plan");
        for (Mc263BeeSidecarPlan.NestSeed nest : plan.nests()) {
            String kind = finalCarrierBeeKind(nest.worldY());
            String key = kind + ':' + nest.worldX() + ':' + nest.worldZ();
            StructureOccupantClaimSnapshot existing = knownFinalCarrierBeeClaims.get(key);
            if (existing == null) continue;
            if (existing.siteKey() != nest.durableFingerprint()
                    || existing.policyVersion() != STRUCTURE_OCCUPANT_POLICY_VERSION
                    || existing.occupantCount() != nest.occupants().size()) {
                throw new IllegalStateException(
                        "final-carrier BEES durable claim mismatch at " + key);
            }
        }
        for (Mc263BeeSidecarPlan.NestSeed nest : plan.nests()) {
            String kind = finalCarrierBeeKind(nest.worldY());
            String key = kind + ':' + nest.worldX() + ':' + nest.worldZ();
            if (knownFinalCarrierBeeClaims.containsKey(key)) continue;
            List<Integer> ticks = nest.occupants().stream()
                    .map(Mc263BeeSidecarPlan.OccupantSeed::ticksInHive).toList();
            List<Mob> added = runtime.addExactBeeNestOccupants(
                    nest.worldX(), nest.worldY(), nest.worldZ(), ticks);
            StructureOccupantClaimSnapshot claim = new StructureOccupantClaimSnapshot(
                    kind, nest.worldX(), nest.worldZ(), nest.durableFingerprint(),
                    STRUCTURE_OCCUPANT_POLICY_VERSION, added.size());
            knownStructureOccupantClaims.add(key);
            knownFinalCarrierBeeClaims.put(key, claim);
            unpersistedStructureOccupantClaims.put(key, claim);
        }
    }

    private static String finalCarrierBeeKind(int y) {
        if (y < com.gameexpert.terrain.Blocks.MIN_Y
                || y > com.gameexpert.terrain.Blocks.MAX_Y) {
            throw new IllegalArgumentException("BEES claim Y outside world: " + y);
        }
        return "MC263_BEES_" + (y - com.gameexpert.terrain.Blocks.MIN_Y);
    }

    private static boolean isFinalCarrierBeeKind(String kind) {
        return kind.startsWith("MC263_BEES_");
    }

    MobRuntime.LeashPlan planAttachLeash(Mob mob, String holderNickname) {
        return runtime.planAttachLeash(mob, holderNickname);
    }

    boolean confirmAttachLeash(MobRuntime.LeashPlan plan) {
        return runtime.confirmAttachLeash(plan);
    }

    String detachLeash(long mobId, String expectedHolder) {
        return runtime.detachLeash(mobId, expectedHolder);
    }

    List<Long> detachLeashesHeldBy(String nickname) {
        return runtime.detachLeashesHeldBy(nickname);
    }

    /**
     * [NAUTILUS-MOUNT] 노틸러스 안장 장착. 갑옷과 <b>같은 게이트</b>(길들인 성체)를 지나고
     * 슬롯만 다르다. 낙타 {@link #saddleCamel} 과 같은 형태이며, 길들임을 묻는 자리만 갈린다.
     */
    boolean saddleNautilus(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.SADDLE || !mob.saddleNautilus()) {
            return false;
        }
        if (!inventory.consumeOne(hand, PlayerInventory.SADDLE)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * [NAUTILUS-MOUNT] 노틸러스 탑승. 1인승이라 좌석 0 하나뿐이고, 길들임·성체 게이트는
     * {@code Nautilus.mount} 안에 있어 여기서는 분기만 연다(해피 가스트와 같은 분업).
     */
    boolean mountNautilus(PlayerTickState player, Mob mob) {
        return mountMob(player, mob, MobMountRules.CONTROLLING_SEAT_INDEX);
    }

    /**
     * [NAUTILUS-MOUNT] <b>Breath of the Nautilus</b>. [B] §8-2: 길들인 노틸러스를 타면 산소
     * 막대가 멈춘다.
     *
     * <p>물약과 구별되는 {@link StatusEffect#BREATH_OF_THE_NAUTILUS} 을 60 MC 틱 부여한다.
     * 효과가 없으면 즉시, 이후에는 절대 게임 시간 40 MC 틱 경계에서만 갱신한다. 하차해도
     * 제거하지 않아 남은 시간이 자연히 흐른다.
     *
     * <p>좌석 원장이 이 시스템에 있으므로 부여도 여기서 한다 — 효과 시스템에 좌석을 알리면
     * 신뢰 경계가 넓어지고, 좌석 정리({@code sweepPigRiders})와 한 틱 어긋날 수 있다.
     */
    private void refreshNautilusBreath() {
        if (seatOf.isEmpty()) return;
        long gameTimeMcTicks = rt.gameTimeMcTicks();
        for (Map.Entry<String, Seat> entry : seatOf.entrySet()) {
            if (entry.getValue().type() != MobType.NAUTILUS
                    && entry.getValue().type() != MobType.ZOMBIE_NAUTILUS) continue;
            PlayerTickState rider = rt.players().get(entry.getKey());
            if (rider == null || rider.isDead()) continue;
            if (!rider.statusEffects().has(StatusEffect.BREATH_OF_THE_NAUTILUS)
                    || NautilusMountRules.breathRefreshDue(gameTimeMcTicks)) {
                rider.applyStatusEffectMcTicks(StatusEffect.BREATH_OF_THE_NAUTILUS,
                        NautilusMountRules.BREATH_AMPLIFIER,
                        NautilusMountRules.BREATH_MC_TICKS);
            }
        }
    }

    /**
     * [HARNESS] 해피 가스트 탑승. 좌석이 넷이라 조종석(얼굴 위 앞자리)부터 비어 있는 자리에
     * 앉힌다 — 낙타 {@link #mountCamel} 과 같은 순서 규칙이다. 하네스가 없으면
     * {@code HappyGhast.mount} 가 좌석마다 거절하므로 여기서 그대로 false 로 떨어진다.
     */
    boolean mountHappyGhast(PlayerTickState player, Mob mob) {
        for (int seatIndex = 0; seatIndex < HappyGhastRules.SEAT_COUNT; seatIndex++) {
            if (mob.seatRider(seatIndex) != null) continue;
            return mountMob(player, mob, seatIndex);
        }
        return false;
    }

    /**
     * [MOUNT] 낙타 안장 장착. 바닐라 낙타는 길들이기가 없어 성체이고 안장이 없기만 하면 받는다.
     */
    boolean saddleCamel(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (player == null || hand == null) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        if (mob == null || mob.type != MobType.CAMEL || held.isEmpty()
                || held.itemType() != PlayerInventory.SADDLE || mob.isBaby()
                || mob.camelSaddled() || !liveHorseMenuTarget(mob)
                || !preflightHorseMenuMutation(mob)) {
            return false;
        }
        if (!applyPlannedHeldConsumption(planExactHeldConsumption(inventory, hand, held),
                () -> mob.saddleCamel(singleItem(held)))) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * [MOUNT] 낙타 탑승. 좌석이 둘이라 앞(조종석)부터 비어 있는 자리에 앉힌다 — 바닐라도 첫
     * 승객이 조종석이고 두 번째 승객이 뒷좌석이다({@code Camel#getPassengerAttachmentPoint}).
     */
    boolean mountCamel(PlayerTickState player, Mob mob) {
        for (int seatIndex = 0; seatIndex < CamelRules.SEAT_COUNT; seatIndex++) {
            if (mob.seatRider(seatIndex) != null) continue;
            return mountMob(player, mob, seatIndex);
        }
        return false;
    }

    /**
     * [FARM-ANIMAL] 기수 좌표 업링크. 보트 {@code BoatSystem.pos} 와 같은 신뢰 모델이라
     * 서버는 물리를 굴리지 않고 <b>기수 일치·유한성·월드 경계·기수 pose 근접</b>만 본다.
     *
     * <p>유한성만으로는 1e300 같은 좌표가 그대로 전원에게 방송된다. 기수 pose 는
     * {@link MovementLimits} 로 이미 속도 상한을 받으므로 그 pose 근처(16블록)라는 요구가 곧
     * 돼지의 속도 상한이 된다 — 부스트 최대 속도
     * 부스트 최대 약 5.1블록/초(권위 틱당
     * 0.51블록)라 16블록은 지연 보정 몫으로만 남는다.
     */
    boolean ridePigPos(PlayerTickState player, long mobId,
            double x, double y, double z, double yaw) {
        return rideMobPos(player, mobId, MobMountRules.CONTROLLING_SEAT_INDEX, x, y, z, yaw);
    }

    /**
     * [MOUNT] 종 비의존 기수 좌표 업링크. 검증 순서는 돼지 시절과 같다:
     * 좌석 일치 → 유한성 → 월드 경계 → 기수 pose 근접(leash). 조종석(seat 0)만 좌표를 올린다.
     */
    boolean rideMobPos(PlayerTickState player, long mobId, int seatIndex,
            double x, double y, double z, double yaw) {
        Seat seat = seatOf.get(player.nickname());
        // 조종 권한 표를 종까지 함께 본다 — 라마처럼 좌석은 있어도 조종할 수 없는 종은
        // 좌표 정본을 넘겨받지 못한다(바닐라 Llama#getControllingPassenger = null).
        if (seat == null || seat.mobId() != mobId || seat.seatIndex() != seatIndex
                || !MobMountRules.controllingSeat(seat.type(), seatIndex)
                || player.isDead()) return false;
        if (!MobMountRules.acceptableUplink(player.x(), player.y(), player.z(), x, y, z, yaw)) {
            return false;
        }
        if (!MovementLimits.withinWorldBounds(x, y, z)) return false;
        Mob mob = runtime.mobById(mobId);
        if (mob == null || mob.isDead() || mob.removed) return false;
        if (!mob.applySeatRiderPosition(player.nickname(), seatIndex, x, y, z, yaw)) return false;
        runtime.refreshSpatialIndex(mob);
        return true;
    }

    /** 자발적 하차·사망·세션 종료의 단일 통로. 좌석이 실제로 비워졌을 때만 방송한다. */
    boolean dismountPig(String nickname) {
        return dismountMob(nickname);
    }

    /**
     * [MOUNT] 자발적 하차·사망·세션 종료·낙마의 단일 통로. 좌석이 실제로 비워졌을 때만 방송한다.
     */
    boolean dismountMob(String nickname) {
        Seat seat = seatOf.remove(nickname);
        if (seat == null) return false;
        mountedHudByRider.remove(nickname);
        Mob mob = runtime.mobById(seat.mobId());
        if (mob != null) {
            mob.dismountSeat(nickname, seat.seatIndex());
            runtime.refreshPersistenceSnapshot(mob);
        }
        broadcastDismount(seat, nickname);
        return true;
    }

    private void broadcastDismount(Seat seat, String nickname) {
        if (seat.type() == MobType.PIG) {
            broadcast(new WsMessages.PigDismount(seat.mobId(), nickname));
        } else {
            broadcast(new WsMessages.MobDismount(seat.mobId(), nickname, seat.seatIndex()));
        }
    }

    /**
     * 타고 있던 돼지가 죽거나 사라졌으면 좌석을 정리하고 하차를 방송한다. 기수 클라는 이
     * 방송으로만 조종을 끝내므로, 이 sweep 이 없으면 카메라가 사라진 돼지에 붙은 채로 남는다.
     */
    private void sweepPigRiders() {
        if (seatOf.isEmpty()) return;
        List<String> gone = null;
        for (Map.Entry<String, Seat> entry : seatOf.entrySet()) {
            Seat seat = entry.getValue();
            Mob mob = runtime.mobById(seat.mobId());
            boolean seated = mob != null && !mob.isDead() && !mob.removed
                    && entry.getKey().equals(mob.seatRider(seat.seatIndex()));
            // [MOUNT] 미길들임 말이 길들이기 판정에 실패하면 그 자리에서 낙마시킨다
            // (바닐라 RunAroundLikeCrazyGoal: ejectPassengers + makeMad).
            if (seated && mob.horseBuckPending()) {
                mob.consumeHorseBuck();
                mob.clearSeat(seat.seatIndex());
                runtime.refreshPersistenceSnapshot(mob);
                seated = false;
            }
            if (seated) continue;
            if (gone == null) gone = new ArrayList<>();
            gone.add(entry.getKey());
        }
        if (gone == null) return;
        for (String nickname : gone) {
            Seat seat = seatOf.remove(nickname);
            if (seat == null) continue;
            mountedHudByRider.remove(nickname);
            Mob mob = runtime.mobById(seat.mobId());
            if (mob != null) mob.clearSeat(seat.seatIndex());
            broadcastDismount(seat, nickname);
        }
    }

    /** 당근 낚싯대 부스트. 성공하면 낚싯대가 7 닳는다(바닐라 {@code hurtAndBreak(7,…)}). */
    boolean boostPig(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.CARROT_ON_A_STICK) return false;
        if (!mob.boostPig(player.nickname(), rng)) return false;
        // 기수 클라가 같은 1+1.15·sin 배율을 적분할 수 있도록 서버가 굴린 커서를 함께 알린다.
        broadcast(new WsMessages.PigBoost(mob.id, mob.pigBoostMcTicks(),
                mob.pigBoostTotalMcTicks()));
        for (int wear = 0; wear < PlayerInventory.CARROT_ON_A_STICK_BOOST_DAMAGE; wear++) {
            PlayerInventory.HandRef currentHand = inventory.capture(hand.hand());
            if (inventory.stack(currentHand).itemType() != PlayerInventory.CARROT_ON_A_STICK) break;
            inventory.degrade(currentHand);
        }
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    // ── [MOUNT] 말 계열 상호작용. 좌석·업링크는 위의 종 비의존 통로를 그대로 쓴다. ──
    /**
     * 먹이. 바닐라 {@code AbstractHorse#handleEating} 표(밀·설탕·건초더미·사과·황금 당근·황금 사과)에
     * 있는 아이템만 소비되고 temper·회복·새끼 성장이 한 번에 적용된다.
     */
    boolean feedHorse(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        short held = inventory.stack(hand).itemType();
        if (!HorseRules.isFood(held) || !mob.feedHorse(held)) return false;
        if (!inventory.consumeOne(hand, held)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /** 안장 장착. 바닐라는 길들인 성체 말만 안장을 받는다. */
    boolean saddleHorse(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (player == null || hand == null) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        if (mob == null || !HorseRules.isHorseFamily(mob.type) || mob.type == MobType.LLAMA
                || held.isEmpty() || held.itemType() != PlayerInventory.SADDLE
                || mob.isBaby() || !mob.horseTamed() || mob.horseSaddled()
                || !liveHorseMenuTarget(mob) || !preflightHorseMenuMutation(mob)) {
            return false;
        }
        if (!applyPlannedHeldConsumption(planExactHeldConsumption(inventory, hand, held),
                () -> mob.saddleHorse(singleItem(held)))) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * 상자 장착. 바닐라 {@code AbstractChestedHorse#mobInteract} 는 길들인 성체이고 아직 상자가
     * 없을 때만 {@code Items.CHEST} 를 받는다. 화물 칸 수는 그 순간의 종·힘 스탯이 정하고
     * (당나귀·노새 15칸, 라마 힘×3칸), 상자는 뗄 수 없어 사망 드랍만이 회수 경로다.
     */
    boolean chestHorse(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (player == null || hand == null) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        boolean traderLlama = mob != null && mob.type == MobType.TRADER_LLAMA;
        if (mob == null || !ChestedHorseRules.isChestItem(held.itemType())
                || !liveHorseMenuTarget(mob) || !mob.horseChestable()
                || mob.horseChested() || mob.isBaby()
                || !traderLlama && !mob.horseTamed()
                || !preflightHorseMenuMutation(mob)) return false;
        if (!applyPlannedHeldConsumption(planExactHeldConsumption(inventory, hand, held),
                mob::attachHorseChest)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * [MOUNT] 화물 슬롯을 직접 만진 뒤 영속 스냅샷을 다시 뜬다. 화물 컨테이너는 개체가 소유한
     * 상태라 슬롯 변이가 곧 몹 행 변경이고, 이 호출이 그 사실을 저장 lane 에 알리는 유일한 통로다.
     */
    void refreshCargoPersistence(Mob mob) {
        mob.setPersistenceRequired(true);
        runtime.refreshPersistenceSnapshot(mob);
    }

    /**
     * 카펫 장식. 바닐라 {@code Llama} 는 body-armor 슬롯에 wool carpet 을 받으며 길들임 여부를
     * 묻지 않는다. 이미 장식이 있으면 교체하지 않는다(바닐라는 슬롯이 비어 있을 때만 넣는다).
     */
    boolean decorateLlama(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (player == null || hand == null) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        boolean llama = mob != null
                && (mob.type == MobType.LLAMA || mob.type == MobType.TRADER_LLAMA);
        if (!llama || !LlamaRules.isCarpet(held.itemType()) || !liveHorseMenuTarget(mob)
                || mob.llamaCarpetColor() != LlamaRules.NO_CARPET
                || !preflightHorseMenuMutation(mob)) return false;
        if (!applyPlannedHeldConsumption(planExactHeldConsumption(inventory, hand, held),
                () -> mob.decorateLlama(singleItem(held)))) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * 점프 차지 확정. 좌표 정본이 기수 클라라 권위는 물리를 굴리지 않고 <b>강도만</b> 검증한 뒤
     * 그 사실을 방송한다(바닐라 {@code AbstractHorse#onPlayerJump} → {@code executeRidersJump}).
     */
    boolean jumpMob(PlayerTickState player, long mobId, int charge) {
        Seat seat = seatOf.get(player.nickname());
        if (seat == null || seat.mobId() != mobId
                || !MobMountRules.controllingSeat(seat.type(), seat.seatIndex())) return false;
        Mob mob = runtime.mobById(mobId);
        if (mob == null || mob.isDead() || mob.removed) return false;
        // [MOUNT] 낙타의 점프 키는 도약이 아니라 전방 대시다. 같은 `mobJump` 를 받아 종으로
        // 갈리고, 확정 방송만 `mobDashed` 로 달라진다(바닐라 Camel#executeRidersJump).
        if (mob.type == MobType.CAMEL) {
            if (!mob.tryCamelDash(player.nickname(), charge)) return false;
            runtime.refreshPersistenceSnapshot(mob);
            broadcast(new WsMessages.MobDashed(mobId, player.nickname(),
                    CamelRules.dashHorizontalBlocksPerSecond(charge),
                    CamelRules.dashVerticalBlocksPerSecond(charge),
                    CamelRules.DASH_COOLDOWN_MC_TICKS));
            return true;
        }
        if (!mob.horseAcceptsJump(player.nickname(), charge)) return false;
        broadcast(new WsMessages.MobJumped(mobId, player.nickname(),
                HorseRules.jumpVelocityBlocksPerSecond(mob.horseJumpStrengthStat(), charge)));
        return true;
    }

    boolean feedTadpole(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.SLIME_BALL
                || !mob.feedTadpoleGrowth()
                || !inventory.consumeOne(hand, PlayerInventory.SLIME_BALL)) return false;
        mob.setPersistenceRequired(true);
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * 좀비 주민 치료 시작(MOB.md §2). 바닐라 {@code ZombieVillagerEntity.interactMob} 와 같이
     * 대상이 약함을 들고 있을 때만 성립하고, 성립하면 황금 사과 1개를 소비한다.
     */
    boolean cureZombieVillager(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.GOLDEN_APPLE
                || !mob.zombieVillagerCurable()
                || !inventory.consumeOne(hand, PlayerInventory.GOLDEN_APPLE)) return false;
        if (!mob.beginZombieVillagerConversion(player.nickname(), rng)) return false;
        mob.setPersistenceRequired(true);
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /**
     * [NAUTILUS-BEHAVIOR] 노틸러스 계열 두 종의 손 상호작용. 근거는 [B]
     * ({@code docs/research/mc-nautilus-1-21-11.md} §1·§3)이고 종이 갈리는 자리는 셋뿐이다.
     *
     * <ol>
     *   <li><b>길들이기(공통)</b> — "Each pufferfish or bucket of pufferfish has a 1⁄3
     *       chance of taming". 실패해도 먹이는 소비된다(늑대 뼈와 같은 형태).</li>
     *   <li><b>회복(공통 형태)</b> — 아무 생선/생선 양동이. 좀비 노틸러스는 이 저장소 현행대로
     *       길들인 개체만 받고, 노틸러스는 [B] 대로 길들임을 묻지 않는다.</li>
     *   <li><b>번식(노틸러스 전용)</b> — "healed (or bred if at full health)". 만피라
     *       회복이 실패한 순간이 곧 번식 조건이며, 좀비 노틸러스는 [B] 가 "cannot be bred"
     *       라고 못 박아 이 갈래로 들어가지 않는다.</li>
     * </ol>
     *
     * <p>새끼는 성체 계약 밖이지만 [B] 의 "each fish or bucket of fish fed to a baby
     * nautilus reduces its remaining growth time by 10%" 는 이 저장소의 공용 성장 가속
     * ({@link MobRuntime#feedBreedingFood} 의 {@code accelerateBabyGrowth(10)})과 같은 값이라
     * 노틸러스 새끼는 그 lane 으로 보낸다.
     */
    boolean interactNautilusFamily(
            PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (mob.type != MobType.NAUTILUS && mob.type != MobType.ZOMBIE_NAUTILUS) return false;
        PlayerInventory inventory = player.inventory();
        short item = inventory.stack(hand).itemType();
        if (!isNautilusFood(item)) return false;
        boolean bucketFood = isNautilusBucketFood(item);
        // 새끼의 성장 가속은 공용 번식 lane 의 판정({@code feedBreedingFood})을 그대로 쓰되
        // 먹이 소비만 이 종의 규칙(생선 양동이 넷 → 빈 양동이 반환)으로 한다. 좀비 노틸러스는
        // 번식표가 없어 그 판정이 NOT_FOOD 라 예전처럼 아무 일도 일어나지 않는다.
        if (mob.isBaby()) {
            if (runtime.feedBreedingFood(mob, item) != MobRuntime.FeedResult.BABY_GROWTH) {
                return false;
            }
            if (!consumeNautilusFood(inventory, hand, item, bucketFood)) return false;
            runtime.refreshPersistenceSnapshot(mob);
            sendInventory(player);
            return true;
        }
        boolean tamingFood = item == PlayerInventory.PUFFERFISH
                || item == PlayerInventory.PUFFERFISH_BUCKET;
        if (mob.ownerNickname() == null && tamingFood) {
            if (!consumeNautilusFood(inventory, hand, item, bucketFood)) return false;
            mob.setPersistenceRequired(true);
            boolean tamed = rng.nextInt(3) == 0 && mob.tameZombieNautilus(player.nickname());
            runtime.refreshPersistenceSnapshot(mob);
            sendInventory(player);
            return tamed;
        }
        if (mob.feedZombieNautilus(player.nickname(), NAUTILUS_FEED_HEAL_POINTS)) {
            if (!consumeNautilusFood(inventory, hand, item, bucketFood)) return false;
            runtime.refreshPersistenceSnapshot(mob);
            sendInventory(player);
            return true;
        }
        // 회복이 거부된 이유가 "만피" 하나뿐일 때만 번식으로 넘어간다. 좀비 노틸러스는
        // 번식표가 없어 breedingEnabled() 가 거짓이고, 미길들임 좀비 노틸러스는 회복 자체를
        // 받지 않으므로 두 경우 모두 여기서 조용히 끝난다(예전 동작 그대로).
        if (!mob.type.breedingEnabled() || !mob.isAtFullHealth()) return false;
        if (!runtime.enterLoveMode(mob, MobRuntime.LOVE_MODE_TICKS)) return false;
        if (!consumeNautilusFood(inventory, hand, item, bucketFood)) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    /** [NAUTILUS-BEHAVIOR] 먹이 하나가 회복시키는 체력. 좀비 노틸러스 시절 값 그대로다. */
    private static final double NAUTILUS_FEED_HEAL_POINTS = 2.0;

    private static boolean consumeNautilusFood(PlayerInventory inventory,
            PlayerInventory.HandRef hand,
            short item, boolean bucketFood) {
        if (bucketFood) {
            return InventoryRules.replaceBucket(
                    inventory, hand, item, PlayerInventory.WATER_BUCKET);
        }
        return inventory.consumeOne(hand, item);
    }

    /**
     * [NAUTILUS-TEMPT] 먹이 집합의 정본이 {@link TemptationRules#isFishOrFishBucket} 로 올라갔다.
     * 유혹 관문과 먹이 관문이 [B] 에서 <b>같은 문장</b>("any fish or bucket of fish")이라, 두
     * 곳이 각자 여덟 ID 를 열거하면 한쪽만 갱신됐을 때 "먹이는 되는데 유혹은 안 되는" 갈림이
     * 조용히 생긴다. 그래서 이 자리는 위임만 한다.
     */
    private static boolean isNautilusFood(short item) {
        return TemptationRules.isFishOrFishBucket(Short.toUnsignedInt(item));
    }

    private static boolean isNautilusBucketFood(short item) {
        return TemptationRules.isFishBucket(Short.toUnsignedInt(item));
    }

    /**
     * {@code PiglinAi.angerNearbyPiglins}: 보호 블록을 부수거나 여는 순간 플레이어 위치 16블록 안의
     * 한가한 피글린이 모두 그 플레이어에게 분노한다. 파괴는 시야를 요구하지 않고 컨테이너 개방만
     * {@code angerOnlyIfCanSee=true} 로 시야를 요구한다.
     */
    /**
     * [GLOWING] 종 공명({@link BellResonance})이 읽는 습격자 창구. 습격자는 바닐라
     * {@code EntityTypeTags.RAIDERS}(약탈자·변명자·소환사·환술사·파괴수·마녀)와 이 저장소의 레이드 전용
     * 일리저 계열이며, 습격자 계열이지만 소환물인 벡스는 태그 밖이라 뺀다.
     */
    BellResonance.Raiders bellRaiders() {
        return bellRaiders;
    }

    private final BellResonance.Raiders bellRaiders = new BellResonance.Raiders() {
        @Override
        public long[] capture(int x, int y, int z, int radius) {
            // getEntitiesOfClass(LivingEntity, new AABB(pos).inflate(radius)): 엔티티 상자와 겹치면 담긴다.
            double minX = x - radius, minY = y - radius, minZ = z - radius;
            double maxX = x + 1 + radius, maxY = y + 1 + radius, maxZ = z + 1 + radius;
            long[] ids = new long[8];
            int count = 0;
            for (Mob candidate : runtime.mobs()) {
                if (!isBellRaider(candidate)) continue;
                double half = candidate.width() * 0.5;
                if (candidate.x - half < maxX && candidate.x + half > minX
                        && candidate.y < maxY && candidate.y + candidate.height() > minY
                        && candidate.z - half < maxZ && candidate.z + half > minZ) {
                    if (count == ids.length) ids = java.util.Arrays.copyOf(ids, count * 2);
                    ids[count++] = candidate.id;
                }
            }
            return java.util.Arrays.copyOf(ids, count);
        }

        @Override
        public boolean inRange(long mobId, int x, int y, int z, int radius) {
            Mob mob = runtime.mobById(mobId);
            return mob != null && isBellRaider(mob)
                    && BellResonance.closerToCenterThan(x, y, z, mob.x, mob.y, mob.z, radius);
        }

        @Override
        public void glow(long mobId, int mcTicks) {
            Mob mob = runtime.mobById(mobId);
            if (mob != null && mob.applyStatusEffectMcTicks(StatusEffect.GLOWING, 0, mcTicks)) {
                // [GLOWING] 발광도 저장 행에 싣는다(바닐라 active_effects).
                runtime.refreshPersistenceSnapshot(mob);
            }
        }
    };

    private static boolean isBellRaider(Mob mob) {
        return !mob.isDead() && !mob.removed && mob.type != MobType.VEX
                && MobRelationshipPolicy.isRaiderFaction(mob.type);
    }

    void angerPiglinsNearGuardedEvent(String nickname, double px, double py, double pz,
            boolean requireLineOfSight) {
        if (nickname == null) return;
        double radiusSq = Piglin.GUARDED_ANGER_RADIUS * Piglin.GUARDED_ANGER_RADIUS;
        for (Mob candidate : runtime.mobs()) {
            if (!(candidate instanceof Piglin piglin) || !piglin.idleForGuardedAnger()) continue;
            double dx = candidate.x - px, dy = candidate.y - py, dz = candidate.z - pz;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            if (requireLineOfSight && !world.hasLineOfSight(
                    candidate.x, candidate.y + candidate.eyeHeight(), candidate.z,
                    px, py + PlayerInteractionRules.STANDING_EYE_HEIGHT, pz)) continue;
            piglin.angerAtGuardedEvent(nickname);
            runtime.refreshPersistenceSnapshot(piglin);
        }
    }

    /** Gold-ingot interaction is consumed and emitted atomically on the tick owner. */
    boolean barterPiglin(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof Piglin piglin)) return false;
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.GOLD_INGOT
                || !piglin.tryBeginBarter()
                || !inventory.consumeOne(hand, PlayerInventory.GOLD_INGOT)) return false;
        PiglinBarterRules.Drop drop = PiglinBarterRules.select(
                rng.nextInt(PiglinBarterRules.TOTAL_WEIGHT), rng.nextInt(9));
        rt.itemSystem().spawnMobDrop(drop.itemType(), drop.count(),
                mob.x, mob.y + 1.0, mob.z);
        runtime.refreshPersistenceSnapshot(mob);
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                    WorldTickLoop.inventoryMessage(player));
        }
        return true;
    }

    /** A completed brush stroke sheds one scute and costs the brush's vanilla 16 durability. */
    boolean brushArmadillo(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof Armadillo armadillo) || mob.isDead() || mob.removed
                || mob.isBaby() || armadillo.shellState() != Armadillo.ShellState.IDLE) {
            return false;
        }
        PlayerInventory inventory = player.inventory();
        if (inventory.stack(hand).itemType() != PlayerInventory.BRUSH) return false;
        for (int damage = 0; damage < 16; damage++) {
            PlayerInventory.HandRef currentHand = inventory.capture(hand.hand());
            if (inventory.stack(currentHand).itemType() != PlayerInventory.BRUSH) break;
            inventory.degrade(currentHand);
        }
        rt.itemSystem().spawnMobDrop(PlayerInventory.ARMADILLO_SCUTE, 1,
                mob.x, mob.y + 0.5, mob.z);
        sendInventory(player);
        return true;
    }

    /** Feeding any member of the dolphin-food tag commits its treasure-seeking state. */
    boolean feedDolphin(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof Dolphin dolphin) || mob.isDead() || mob.removed) return false;
        PlayerInventory inventory = player.inventory();
        short itemType = inventory.stack(hand).itemType();
        if (!isDolphinFood(itemType) || !inventory.consumeOne(hand, itemType)) return false;
        if (!dolphin.acceptFish()) return false;
        runtime.refreshPersistenceSnapshot(mob);
        sendInventory(player);
        return true;
    }

    private static boolean isDolphinFood(short itemType) {
        return itemType == PlayerInventory.COD_RAW || itemType == PlayerInventory.COD_COOKED
                || itemType == PlayerInventory.SALMON_RAW
                || itemType == PlayerInventory.SALMON_COOKED
                || itemType == PlayerInventory.TROPICAL_FISH
                || itemType == PlayerInventory.PUFFERFISH;
    }

    private void sendInventory(PlayerTickState player) {
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                    WorldTickLoop.inventoryMessage(player));
        }
    }

    /** 물 양동이로 대구/연어를 포획하고 같은 틱에 선택 슬롯과 제거 사유를 확정한다. */
    boolean captureFish(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (mob == null || mob.isDead() || mob.removed) return false;
        short filled = mob.type == MobType.COD ? PlayerInventory.COD_BUCKET
                : mob.type == MobType.SALMON ? PlayerInventory.SALMON_BUCKET
                : mob.type == MobType.TROPICAL_FISH ? PlayerInventory.TROPICAL_FISH_BUCKET
                : mob.type == MobType.PUFFERFISH ? PlayerInventory.PUFFERFISH_BUCKET
                : mob.type == MobType.TADPOLE ? PlayerInventory.TADPOLE_BUCKET
                : mob.type == MobType.AXOLOTL ? PlayerInventory.AXOLOTL_BUCKET : 0;
        if (filled == 0) return false;
        String payload = BucketMobPayloadCodec.requiresPayload(filled)
                ? BucketMobPayloadCodec.encode(mob.type, mob.variant(),
                        mob.tadpoleAgeMcTicks(), mob.customName())
                : null;
        if (!player.inventory().replaceSingle(
                hand, PlayerInventory.WATER_BUCKET, filled, payload)) return false;
        mob.removed = true;
        pendingDespawnReasons.put(mob.id, "bucketed");
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(
                    rt.worldId(), session, WorldTickLoop.inventoryMessage(player));
        }
        return true;
    }

    boolean captureSulfurCube(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof SulfurCube cube) || mob.isDead() || mob.removed
                || player.inventory().stack(hand).itemType() != PlayerInventory.BUCKET) return false;
        String payload = BucketMobPayloadCodec.encodeSulfur(cube.bodyItem(), mob.customName());
        if (!player.inventory().replaceSingle(hand, PlayerInventory.BUCKET,
                PlayerInventory.SULFUR_CUBE_BUCKET, payload)) return false;
        cube.setFromBucket(true);
        mob.removed = true;
        pendingDespawnReasons.put(mob.id, "bucketed");
        sendInventory(player);
        return true;
    }

    /**
     * [DEEP-DARK] 스컬크 비명체 4단계 경고의 <b>실제 워든 소환</b>. 판정 전부는
     * {@link com.gameexpert.engine.sculk.WardenSummon} 이 들고 있고(두 권위가 같은 순서를
     * 돌아야 하므로 몹 원장만 요구하는 정적 지점이다) 여기는 이 코어의 원장을 건네는 문이다.
     * {@code WorldTickLoop} 이 {@code SculkVibrationRules.WardenSummonSink} 자리에 끼운다.
     */
    Mob summonWarden(int x, int y, int z, String nickname) {
        return com.gameexpert.engine.sculk.WardenSummon.summon(runtime, x, y, z, nickname);
    }

    /**
     * [DEEP-DARK] 다 수화된 말린 가스트의 <b>실제 가스틀링 소생</b>. 워든 소환과 같은 분업이다 —
     * 판정 전부는 {@link com.gameexpert.engine.sculk.GhastlingRevival} 이 들고 있고 여기는 이
     * 코어의 몹 원장을 건네는 문이다. {@code WorldTickLoop} 이
     * {@code DriedGhastHydration.GhastlingRevivalSink} 자리에 끼운다.
     */
    Mob reviveGhastling(int x, int y, int z) {
        return com.gameexpert.engine.sculk.GhastlingRevival.revive(runtime, x, y, z);
    }

    /** 알 좌표를 고향으로 삼은 새끼를 일반 몹 원장·스폰·주기 저장 경로에 등록한다. */
    /**
     * [MOB-LOOK] GameEvent.JUKEBOX_PLAY ({@code playing}) / JUKEBOX_STOP_PLAY at a jukebox: every allay
     * whose JukeboxListener is in range starts / stops dancing ({@code Allay#setJukeboxPlaying}).
     */
    void jukeboxGameEvent(int x, int y, int z, boolean playing) {
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob instanceof Allay allay && !allay.isDead() && allay.hearsJukebox(x, y, z)) {
                allay.setJukeboxPlaying(x, y, z, playing);
            }
        }
    }

    void hatchTurtleEggs(int x, int y, int z, int count) {
        for (int index = 0; index < count; index++) {
            Mob turtle = runtime.addMob(MobType.TURTLE, x + 0.5, y, z + 0.5, true);
            runtime.setBabyAge(turtle, MobRuntime.BABY_GROWTH_TICKS);
            // addMob의 최초 성체 스냅샷을 성장 나이가 반영된 같은 ID의 행으로 교체한다.
            runtime.refreshPersistenceSnapshot(turtle);
        }
    }

    /** Player-built golems are persistent and enter the ordinary mob snapshot/broadcast lane. */
    Mob spawnConstructedGolem(MobType type, double x, double y, double z) {
        if (type != MobType.SNOW_GOLEM && type != MobType.IRON_GOLEM) {
            throw new IllegalArgumentException("unsupported constructed golem " + type);
        }
        return runtime.addMob(type, x, y, z, true);
    }

    FleshColonyProgress.PopulationCounts fleshPopulation(BlockPos core) {
        var population = new FleshNetherRules.Population(core);
        for (var mob : runtime.mobs()) if (!mob.removed && !mob.isDead()) {
            population.include(mob.type.name(), mob.x, mob.z);
        }
        return population.counts();
    }

    MobPersistenceSnapshot prepareFleshMob(String kind, double x, double y, double z) {
        MobType type = MobType.valueOf(kind.toUpperCase(java.util.Locale.ROOT));
        if (!FleshNetherRules.isFleshMob(type.name())) throw new IllegalArgumentException("unsupported flesh hatch");
        long id = runtime.reserveMobId();
        MobRuntime detached = new MobRuntime(players -> List.of(), rt.seed());
        detached.addMobWithReservedId(type, id, x, y, z, true);
        return detached.persistenceSnapshot(id);
    }

    long reserveMobId() { return runtime.reserveMobId(); }

    long currentMobIdFloor() { return runtime.currentMobIdFloor(); }

    boolean claimMobIdsFrom(long minimum, long through) {
        return runtime.claimMobIdsFrom(minimum, through);
    }

    void reserveMobIdThrough(long mobId) { runtime.reserveMobIdThrough(mobId); }

    Mob spawnConstructedGolemWithId(MobType type, long mobId,
            double x, double y, double z) {
        if (type != MobType.COPPER_GOLEM) {
            throw new IllegalArgumentException("reserved construction is Copper Golem only");
        }
        return runtime.addMobWithReservedId(type, mobId, x, y, z, true);
    }

    void rollbackConstructedGolem(long mobId) {
        runtime.rollbackExternalMob(mobId, MobType.COPPER_GOLEM);
    }

    boolean waxCopperGolem(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof com.gameexpert.engine.mob.CopperGolem golem)
                || golem.waxed()
                || player.inventory().stack(hand).itemType() != PlayerInventory.HONEYCOMB) {
            return false;
        }
        PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
        if (!player.inventory().consumeOne(hand, PlayerInventory.HONEYCOMB)) return false;
        if (!golem.wax()) {
            player.inventory().setStack(player.inventory().capture(hand.hand()), before);
            return false;
        }
        runtime.refreshPersistenceSnapshot(golem);
        sendInventory(player);
        return true;
    }

    boolean scrapeCopperGolem(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        if (!(mob instanceof com.gameexpert.engine.mob.CopperGolem golem)
                || !com.gameexpert.engine.enchant.EnchantmentRules.isAxeItem(
                        player.inventory().stack(hand).itemType())
                || !golem.scrape()) {
            return false;
        }
        player.inventory().degrade(hand);
        runtime.refreshPersistenceSnapshot(golem);
        sendInventory(player);
        return true;
    }

    /**
     * [CREAKING] 깨어난 크리킹 하트의 <b>실제 크리킹 소환</b>. 워든 소환·가스틀링 소생과 같은
     * 분업이다 — 판정 전부는 {@link com.gameexpert.engine.creaking.CreakingSummon} 이 들고
     * 있고(하트당 하나·결속 좌표·배치 규약) 여기는 이 코어의 몹 원장을 건네는 문이다.
     * {@code WorldTickLoop} 이 {@code CreakingHeartSystem.CreakingSink} 자리에 끼운다.
     */
    Mob summonCreaking(int x, int y, int z) {
        return com.gameexpert.engine.creaking.CreakingSummon.summon(runtime, x, y, z);
    }

    /**
     * [CREAKING] 잠들었거나 부서진 하트가 자기 크리킹을 거둔다. 하트 파괴가 곧 처치라는
     * 계약([B] «Creaking Heart»)의 실제 실행 지점이며, 판정은 같은 규칙 클래스가 갖는다.
     */
    boolean dismissCreaking(int x, int y, int z) {
        return com.gameexpert.engine.creaking.CreakingSummon.dismiss(runtime, x, y, z);
    }

    /** 검증된 물고기 양동이 방출. 양동이 출신은 자연 디스폰에서 제외하고 저장한다. */
    Mob releaseBucketFish(short bucketType, String bucketMobData,
            double x, double y, double z) {
        if (bucketType == PlayerInventory.SULFUR_CUBE_BUCKET) {
            BucketMobPayloadCodec.SulfurPayload payload =
                    BucketMobPayloadCodec.decodeSulfur(bucketMobData);
            SulfurCube cube = (SulfurCube) runtime.addMob(
                    MobType.SULFUR_CUBE, x, y, z, true);
            cube.restoreSulfurCubeState(payload.bodyItem(), 0, -1, -1, true);
            cube.setCustomName(payload.customName());
            runtime.refreshPersistenceSnapshot(cube);
            return cube;
        }
        MobType type = bucketType == PlayerInventory.COD_BUCKET ? MobType.COD
                : bucketType == PlayerInventory.SALMON_BUCKET ? MobType.SALMON
                : bucketType == PlayerInventory.TROPICAL_FISH_BUCKET ? MobType.TROPICAL_FISH
                : bucketType == PlayerInventory.PUFFERFISH_BUCKET ? MobType.PUFFERFISH
                : bucketType == PlayerInventory.TADPOLE_BUCKET ? MobType.TADPOLE
                : bucketType == PlayerInventory.AXOLOTL_BUCKET ? MobType.AXOLOTL : null;
        if (type == null) return null;
        if (BucketMobPayloadCodec.requiresPayload(bucketType)) {
            return runtime.addBucketMob(
                    BucketMobPayloadCodec.decode(bucketMobData, type), x, y, z);
        }
        if (bucketMobData != null) {
            throw new IllegalArgumentException("ordinary fish bucket cannot carry mob payload");
        }
        return runtime.addMob(type, x, y, z, true);
    }

    Mob releaseBucketFish(short bucketType, double x, double y, double z) {
        return releaseBucketFish(bucketType, null, x, y, z);
    }

    enum AnimalDependencySpawnKind { TADPOLE, SNIFFER }

    /**
     * Commits a verified hatch into the ordinary persistent resident/spawn-broadcast lane. The
     * scheduled block owner removes its source block only after this returns true.
     */
    boolean spawnAnimalDependency(AnimalDependencySpawnKind kind, int x, int y, int z) {
        if (kind == null || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
        MobType type = switch (kind) {
            case TADPOLE -> MobType.TADPOLE;
            case SNIFFER -> MobType.SNIFFER;
        };
        for (Mob mob : runtime.mobs()) {
            if (mob.type != type || mob.isDead() || mob.removed || !mob.persistenceRequired()) continue;
            if (Math.abs(mob.x - (x + 0.5)) < 1e-6 && Math.abs(mob.y - y) < 1e-6
                    && Math.abs(mob.z - (z + 0.5)) < 1e-6) return true;
        }
        double half = type.width() * 0.5;
        int minX = (int) Math.floor(x + 0.5 - half + 1e-6);
        int maxX = (int) Math.floor(x + 0.5 + half - 1e-6);
        int minZ = (int) Math.floor(z + 0.5 - half + 1e-6);
        int maxZ = (int) Math.floor(z + 0.5 + half - 1e-6);
        int maxY = (int) Math.ceil(y + type.height()) - 1;
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            for (int by = y; by <= maxY; by++) {
                if (bx == x && by == y && bz == z) continue;
                int block = WorldTickLoop.residentBlockType(rt.accessor(), bx, by, bz);
                if (block == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
                int state = rt.blockStates().get(bx, by, bz, block);
                if (BuildingBlockRules.blocksMotion(block, state)) return false;
            }
        }
        runtime.addMob(type, x + 0.5, y, z + 0.5, true);
        return true;
    }

    long reserveAnimalDependencyMobId() { return runtime.reserveMobId(); }

    Mob spawnAnimalDependencyWithId(AnimalDependencySpawnKind kind, long mobId,
            int x, int y, int z) {
        if (kind == null || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return null;
        MobType type = kind == AnimalDependencySpawnKind.TADPOLE ? MobType.TADPOLE : MobType.SNIFFER;
        // Reuse the exact admission gate without creating an ordinary-id mob.
        for (Mob mob : runtime.mobs()) if (mob.id == mobId && mob.type == type) return mob;
        if (!animalDependencySpawnFits(type, x, y, z)) return null;
        return runtime.addMobWithReservedId(type, mobId, x + 0.5, y, z + 0.5, true);
    }

    /** Owner-side admission captured before an asynchronous hatch settlement is submitted. */
    boolean animalDependencySpawnFits(AnimalDependencySpawnKind kind, int x, int y, int z) {
        if (kind == null || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
        MobType type = kind == AnimalDependencySpawnKind.TADPOLE ? MobType.TADPOLE : MobType.SNIFFER;
        return animalDependencySpawnFits(type, x, y, z);
    }

    /** Pure detached row construction; safe on the persistence writer and invisible to gameplay. */
    MobPersistenceSnapshot detachedAnimalDependencySnapshot(AnimalDependencySpawnKind kind,
            long mobId, int x, int y, int z) {
        if (kind == null) throw new IllegalArgumentException("animal dependency kind is required");
        MobType type = kind == AnimalDependencySpawnKind.TADPOLE ? MobType.TADPOLE : MobType.SNIFFER;
        return runtime.detachedAnimalDependencySnapshot(type, mobId, x + 0.5, y, z + 0.5);
    }

    /** Installs a database-committed hatch identity without re-running the earlier admission gate. */
    Mob installCommittedAnimalDependency(AnimalDependencySpawnKind kind, long mobId,
            int x, int y, int z) {
        if (kind == null) return null;
        MobType type = kind == AnimalDependencySpawnKind.TADPOLE ? MobType.TADPOLE : MobType.SNIFFER;
        Mob existing = runtime.mobById(mobId);
        if (existing != null) return existing.type == type ? existing : null;
        return runtime.addMobWithReservedId(type, mobId, x + 0.5, y, z + 0.5, true);
    }

    MobPersistenceSnapshot detachedCopperGolemSnapshot(long mobId,
            double x, double y, double z, String customName, short heldItem,
            int heldDurability, int pose) {
        return runtime.detachedCopperGolemSnapshot(
                mobId, x, y, z, customName, heldItem, heldDurability, pose);
    }

    Mob installCommittedCopperGolem(long mobId, double x, double y, double z,
            String customName, short heldItem, int heldDurability, int pose) {
        Mob mob = runtime.addMobWithReservedId(MobType.COPPER_GOLEM, mobId, x, y, z, true);
        if (mob instanceof com.gameexpert.engine.mob.CopperGolem golem && customName != null) {
            golem.restoreFromStatue(customName, heldItem, heldDurability, pose);
            runtime.refreshPersistenceSnapshot(golem);
        }
        return mob;
    }

    MobPersistenceSnapshot persistenceSnapshot(long mobId) {
        return runtime.persistenceSnapshot(mobId);
    }

    private boolean animalDependencySpawnFits(MobType type, int x, int y, int z) {
        double half = type.width() * 0.5;
        int minX = (int) Math.floor(x + 0.5 - half + 1e-6);
        int maxX = (int) Math.floor(x + 0.5 + half - 1e-6);
        int minZ = (int) Math.floor(z + 0.5 - half + 1e-6);
        int maxZ = (int) Math.floor(z + 0.5 + half - 1e-6);
        int maxY = (int) Math.ceil(y + type.height()) - 1;
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            for (int by = y; by <= maxY; by++) {
                if (bx == x && by == y && bz == z) continue;
                int block = WorldTickLoop.residentBlockType(rt.accessor(), bx, by, bz);
                if (block == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
                int state = rt.blockStates().get(bx, by, bz, block);
                if (BuildingBlockRules.blocksMotion(block, state)) return false;
            }
        }
        return true;
    }

    List<MobRuntime.TurtleEggPlacementRequest> drainTurtleEggPlacementRequests() {
        return runtime.drainTurtleEggPlacementRequests();
    }

    List<MobRuntime.FrogspawnPlacementRequest> drainFrogspawnPlacementRequests() {
        return runtime.drainFrogspawnPlacementRequests();
    }

    void installFrogColonyPersistence(FrogColonyPersistenceService persistence) {
        if (persistence == null) {
            throw new IllegalStateException("frog colony persistence is required");
        }
        frogColonyPersistence = persistence;
        // WorldRuntime enforces this binding before start. The read therefore happens before the
        // owner tick exists, while every later begin/apply/commit goes through the FIFO writer.
        runtime.installFrogColonyPersistence(rt.worldId(), persistence.hydrate(rt.worldId()));
    }

    void confirmFrogspawnPlacement(long sourceMobId, long partnerMobId, boolean placed) {
        runtime.confirmFrogspawnPlacement(sourceMobId, partnerMobId, placed);
    }

    void confirmTurtleEggPlacement(long turtleMobId, boolean placed) {
        runtime.confirmTurtleEggPlacement(turtleMobId, placed);
    }

    /** UI·잠금 해제로 사용을 취소합니다. 이미 장전된 탄약과 낚시찌는 보존합니다. */
    void cancelBowUse(PlayerTickState player) {
        bowChargeStarted.keySet().removeIf(key -> key.belongsTo(player.nickname()));
        crossbowChargeStarted.keySet().removeIf(key -> key.belongsTo(player.nickname()));
        combat.stopKineticUse(player);
    }

    /** pressed 전이 한 쌍과 시작 손을 받아 플레이어 화살을 기존 투사체 경로에 넣습니다. */
    boolean handleBowUse(PlayerTickState player, PlayerInventory.HandRef hand,
            boolean pressed, long tickNo) {
        PlayerInventory inventory = player.inventory();
        PlayerUseKey useKey = new PlayerUseKey(player.nickname(), hand);
        short held = inventory.stack(hand).itemType();
        // [SPEAR-KINETIC] 창은 KINETIC_WEAPON 이라 누르면 쓰기(돌진)를 시작하고 떼면 끝낸다(ItemStack.releaseUsing
        // 에 창 동작은 없다). 돌진 판정은 매 틱 tickSpearKinetic 이 돈다.
        if (!pressed && combat.stopKineticUse(player)) return false;
        if (pressed && SpearRules.kinetic(held) != null) {
            combat.startKineticUse(player, hand.hand(), tickNo);
            return false;
        }
        boolean hasCrossbowCharge = crossbowChargeStarted.keySet().stream()
                .anyMatch(key -> key.belongsTo(player.nickname()));
        if (held == PlayerInventory.CROSSBOW || hasCrossbowCharge) {
            return handleCrossbowUse(player, hand, useKey, pressed, tickNo);
        }
        if (pressed) {
            // [TRIDENT] 삼지창도 같은 충전 타이머 자리를 쓴다(바닐라의 주손 사용 슬롯도 하나다).
            if (held == PlayerInventory.BOW || held == PlayerInventory.TRIDENT) {
                bowChargeStarted.keySet().removeIf(key -> key.belongsTo(player.nickname()));
                bowChargeStarted.put(useKey, tickNo);
            }
            return false;
        }
        Long started = bowChargeStarted.remove(useKey);
        bowChargeStarted.keySet().removeIf(key -> key.belongsTo(player.nickname()));
        // [TRIDENT] 삼지창도 같은 우클릭 전이(누름→뗌)를 쓴다. 바닐라도 주손 사용 슬롯 하나를
        // 공유하므로 충전 타이머는 활과 같은 자리를 쓰고, 갈리는 것은 놓는 순간의 판정뿐이다.
        if (started != null && held == PlayerInventory.TRIDENT) {
            return throwTrident(player, hand, Math.max(0, tickNo - started));
        }
        if (started == null || held != PlayerInventory.BOW) return false;
        long heldTicks = Math.max(0, tickNo - started);
        double pull = Math.min(1.0, heldTicks / 10.0);
        double power = (pull * pull + pull * 2.0) / 3.0;
        long sourceInventoryRevision = inventory.revision();
        // [ENCHANT-WIDE] 무한: 바닐라 infinity.json 의 ammo_use 0 은 **보통 화살**에만 걸린다(효과 화살은
        // 그대로 먹는다). 쏘려면 탄약 하나는 있어야 한다.
        com.gameexpert.engine.enchant.WideEnchantments bowEnchantments =
                inventory.stack(hand).wideEnchantments();
        boolean infinity =
                com.gameexpert.engine.enchant.EnchantmentRules.infinitySavesArrow(bowEnchantments);
        if (power < 0.1) return false;
        // [TRIAL-GAP] 바닐라 Player.getProjectile 순서로 화살·효과 화살 하나를 먹는다.
        short ammo = inventory.peekArrowAmmo();
        if (ammo == PlayerInventory.EMPTY) return false;
        if (!(infinity && ammo == PlayerInventory.ARROW)) {
            ammo = inventory.consumeOneArrowAmmo();
            if (ammo == PlayerInventory.EMPTY) return false;
        }

        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double speed = com.gameexpert.engine.mob.Skeleton.ARROW_SPEED * power;
        double vx = -Math.sin(yaw) * horizontal * speed;
        double vy = Math.sin(pitch) * speed;
        double vz = -Math.cos(yaw) * horizontal * speed;
        // [SURV-X] 힘(Power)은 바닐라대로 기본 화살 피해에 고정량을 더한다(CombatRules 가 정본).
        PlayerInventory.HandRef currentHand = inventory.capture(hand.hand());
        int damage = CombatRules.arrowDamage(power, inventory.stack(currentHand).enchantments());
        // [ENCHANT-WIDE] 밀어내기·화염은 화살이 날라 명중 순간에 적용한다(damageMobFromProjectile).
        ProjectileSim arrow = shootPlayerAmmo(player, ammo, vx, vy, vz, damage, bowEnchantments);
        // [ARROW-GROUND] ProjectileWeaponItem.useAmmo: 무한으로 아낀 보통 화살은 INTANGIBLE_PROJECTILE
        // 사본이라 AbstractArrow 생성자가 줍기를 CREATIVE_ONLY 로 둔다(효과 화살은 먹으므로 ALLOWED).
        if (infinity && ammo == PlayerInventory.ARROW) {
            arrow.pickup = ProjectileSim.Pickup.CREATIVE_ONLY;
        }
        inventory.degrade(currentHand);
        announcePlayerProjectile(player, arrow, sourceInventoryRevision);
        return true;
    }

    /**
     * [TRIAL-GAP] 활·석궁이 먹은 탄약으로 화살을 쏜다. 효과 화살은 바닐라 {@code Arrow} 가
     * {@code potion_contents}(효과 화살 배율 0.125)를 명중에 싣는 것과 같이 효과를 싣는다.
     */
    private ProjectileSim shootPlayerAmmo(PlayerTickState player, short ammo,
            double vx, double vy, double vz, int damage,
            com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments) {
        double eyeY = player.y() + PlayerInteractionRules.eyeHeight(player.crouching());
        // [GLOWING] 분광 화살도 같은 길이다: 아이템을 싣고 명중에 발광 200 MC 틱을 건다.
        if (PlayerInventory.isTippedArrow(ammo) || ammo == PlayerInventory.SPECTRAL_ARROW) {
            ProjectileSim tipped = ProjectileSim.fromPlayerTippedArrow(
                    player.nickname(), player.x(), eyeY, player.z(), vx, vy, vz, damage,
                    PotionRules.ammoEffect(ammo), ammo);
            // [ENCHANT-WIDE] 효과 화살도 쏜 무기의 인챈트(밀어내기·화염·관통)를 싣는다.
            tipped.weaponEnchantments = weaponEnchantments;
            return runtime.addWorldProjectile(tipped);
        }
        return runtime.shootPlayerArrow(player.nickname(), player.x(), eyeY, player.z(),
                vx, vy, vz, damage, weaponEnchantments);
    }

    /**
     * [TRIDENT] 충전된 삼지창 투척. 활과 달리 <b>탄약이 없고 던진 삼지창 자체가 손을 떠난다</b> —
     * 투사체가 곧 그 아이템이라 회수될 때까지 인벤토리에 남지 않는다.
     *
     * <p>내구가 이번 투척으로 0 이 될 삼지창은 던지지 않는다(바닐라 {@code TridentItem} 도
     * 부러질 삼지창을 던지지 않는다). 부러진 채 날아가면 회수물이 없어 아이템이 사라지므로
     * 그 자체가 divergence 다.
     *
     * @param heldTicks 누름→뗌 사이의 권위 틱.
     */
    private boolean throwTrident(PlayerTickState player, PlayerInventory.HandRef hand,
            long heldTicks) {
        if (heldTicks < PlayerInventory.TRIDENT_THROW_AUTHORITY_TICKS) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot heldTrident = inventory.stack(hand);
        if (heldTrident.itemType() != PlayerInventory.TRIDENT
                || heldTrident.durability() - PlayerInventory.TRIDENT_THROW_DAMAGE <= 0) return false;

        long sourceInventoryRevision = inventory.revision();
        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double speed = com.gameexpert.engine.mob.Skeleton.ARROW_SPEED
                * PlayerInventory.TRIDENT_VELOCITY_RATIO;
        double vx = -Math.sin(yaw) * horizontal * speed;
        double vy = Math.sin(pitch) * speed;
        double vz = -Math.cos(yaw) * horizontal * speed;
        // 내구 소모는 인벤토리의 단일 관문을 지난 뒤 그 결과값을 투사체에 싣는다.
        for (int worn = 0; worn < PlayerInventory.TRIDENT_THROW_DAMAGE; worn++) {
            inventory.degrade(inventory.capture(hand.hand()));
        }
        PlayerInventory.HandRef currentHand = inventory.capture(hand.hand());
        PlayerInventory.StackSnapshot thrown = inventory.stack(currentHand);
        int recovered = thrown.durability();
        if (!inventory.consumeOne(currentHand, PlayerInventory.TRIDENT)) {
            throw new IllegalStateException("worn trident disappeared before throw commit");
        }
        // [ENCHANT-WIDE] 던진 삼지창은 인챈트(찌르기)와 성분을 그대로 싣고, 회수물로 되돌려준다.
        ProjectileSim trident = runtime.throwPlayerTrident(
                player.nickname(), player.x(),
                player.y() + PlayerInteractionRules.eyeHeight(player.crouching()), player.z(),
                vx, vy, vz, CombatRules.TRIDENT_PROJECTILE_DAMAGE, recovered,
                thrown.wideEnchantments(), thrown.itemComponentData());
        announcePlayerProjectile(player, trident, sourceInventoryRevision);
        return true;
    }

    /**
     * 석궁 우클릭. 바닐라 {@code CrossbowItem} 처럼 두 단계다.
     * 누름→뗌 사이가 장전 시간을 넘기면 화살 1을 먹고 장전 상태가 되고(피해·내구 소모 없음),
     * 장전된 상태에서 다시 누르면 즉시 볼트를 쏘며 내구 1이 닳는다.
     */
    private boolean handleCrossbowUse(PlayerTickState player, PlayerInventory.HandRef hand,
            PlayerUseKey useKey, boolean pressed, long tickNo) {
        PlayerInventory inventory = player.inventory();
        String nickname = player.nickname();
        if (inventory.stack(hand).itemType() != PlayerInventory.CROSSBOW) {
            crossbowChargeStarted.keySet().removeIf(key -> key.belongsTo(nickname));
            return false;
        }
        if (pressed) {
            bowChargeStarted.keySet().removeIf(key -> key.belongsTo(nickname));
            if (!crossbowLoaded.remove(useKey)) {
                crossbowChargeStarted.keySet().removeIf(key -> key.belongsTo(nickname));
                crossbowChargeStarted.put(useKey, tickNo);
                return false;
            }
            Short loaded = crossbowLoadedAmmo.remove(useKey);
            return shootCrossbowBolt(player, hand,
                    loaded == null ? PlayerInventory.ARROW : loaded);
        }
        Long started = crossbowChargeStarted.remove(useKey);
        crossbowChargeStarted.keySet().removeIf(key -> key.belongsTo(nickname));
        // [ENCHANT-WIDE] 빠른 장전: CrossbowItem.getChargeDuration = floor((1.25 - 0.25·level) × 20) MC 틱,
        // 권위 틱은 인챈트 없는 장전(CROSSBOW_CHARGE_AUTHORITY_TICKS)과 같은 올림 환산이다.
        int quickCharge = inventory.stack(hand).wideEnchantments()
                .level(com.gameexpert.engine.enchant.EnchantmentRules.QUICK_CHARGE);
        int chargeTicks = quickCharge > 0
                ? (com.gameexpert.engine.enchant.EnchantmentRules
                        .crossbowChargeMcTicks(quickCharge) + 1) / 2
                : PlayerInventory.CROSSBOW_CHARGE_AUTHORITY_TICKS;
        if (started == null
                || tickNo - started < chargeTicks) {
            return false;
        }
        short ammo = inventory.consumeOneArrowAmmo();
        if (ammo == PlayerInventory.EMPTY) return false;
        crossbowLoaded.add(useKey);
        crossbowLoadedAmmo.put(useKey, ammo);
        return true;
    }

    /**
     * 장전된 석궁 발사. 바닐라 석궁 볼트는 완충 활의 1.05배 속력으로 곧게 나간다.
     *
     * <p>[ENCHANT-WIDE] 다중 발사: 발사체 {@code 1 + 2·level} 개, 요 오프셋은
     * {@link com.gameexpert.engine.enchant.EnchantmentRules#multishotYawDegrees}(0, -10, +10) 를 시선 자신의
     * 위쪽 축으로 돌린다({@code CrossbowItem.getProjectileShotVector}). 탄약은 장전 때 하나만 썼고(다중 발사
     * 사본은 같은 탄약 — 효과 화살이면 효과 화살), 내구는 발사체마다 {@code getDurabilityUse} = 1 씩 닳는다
     * (석궁이 부서지면 거기서 멈춘다). 관통은 화살에 실린다.
     */
    private boolean shootCrossbowBolt(PlayerTickState player, PlayerInventory.HandRef hand,
            short ammo) {
        PlayerInventory inventory = player.inventory();
        long sourceInventoryRevision = inventory.revision();
        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double speed = com.gameexpert.engine.mob.Skeleton.ARROW_SPEED
                * PlayerInventory.CROSSBOW_VELOCITY_RATIO;
        double vx = -Math.sin(yaw) * horizontal * speed;
        double vy = Math.sin(pitch) * speed;
        double vz = -Math.cos(yaw) * horizontal * speed;
        PlayerInventory.StackSnapshot crossbow = inventory.stack(hand);
        com.gameexpert.engine.enchant.WideEnchantments crossbowEnchantments =
                crossbow.wideEnchantments();
        int damage = CombatRules.arrowDamage(
                PlayerInventory.CROSSBOW_VELOCITY_RATIO, crossbow.enchantments());
        int multishot = crossbowEnchantments.level(
                com.gameexpert.engine.enchant.EnchantmentRules.MULTISHOT);
        int projectiles = com.gameexpert.engine.enchant.EnchantmentRules
                .multishotProjectileCount(multishot);
        java.util.List<ProjectileSim> bolts = new java.util.ArrayList<>(projectiles);
        for (int index = 0; index < projectiles; index++) {
            double[] shot = rotateAboutViewUp(vx, vy, vz, Math.toRadians(
                    com.gameexpert.engine.enchant.EnchantmentRules.multishotYawDegrees(
                            multishot, index)));
            ProjectileSim bolt = shootPlayerAmmo(player, ammo, shot[0], shot[1], shot[2], damage,
                    crossbowEnchantments);
            // [ARROW-GROUND] ProjectileWeaponItem.draw: 두 번째부터의 다중 발사 사본은 useAmmo(..., true)
            // 가 INTANGIBLE_PROJECTILE 을 붙여 줍기가 CREATIVE_ONLY 다.
            if (index > 0) bolt.pickup = ProjectileSim.Pickup.CREATIVE_ONLY;
            bolts.add(bolt);
            boolean broke = false;
            for (int wear = 0; wear < PlayerInventory.CROSSBOW_SHOT_DAMAGE; wear++) {
                inventory.degrade(inventory.capture(hand.hand()));
                if (inventory.stack(inventory.capture(hand.hand())).itemType()
                        != PlayerInventory.CROSSBOW) {
                    broke = true;
                    break;
                }
            }
            if (broke) break;
        }
        // 한 번의 영속 트랜잭션이 모든 볼트와 닳은 석궁을 함께 넘긴다. 나머지 볼트는 발사 이벤트만 낸다.
        announcePlayerProjectile(player, bolts.get(0), sourceInventoryRevision);
        for (int index = 1; index < bolts.size(); index++) {
            ProjectileSim bolt = bolts.get(index);
            knownArrows.add(bolt.id);
            // 효과 화살 사본도 첫 볼트와 같은 itemType 을 싣는다(같은 생성 메시지 규약).
            broadcast(projectileSpawnMessage(bolt));
        }
        return true;
    }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code CrossbowItem.getProjectileShotVector}: 시선 벡터를 {@code view × up(0,1,0)}
     * 축으로 90° 돌린 "시선의 위쪽" 축을 만든 뒤, 그 축을 중심으로 {@code angle} 만큼 돌린다(로드리그 회전).
     */
    static double[] rotateAboutViewUp(double vx, double vy, double vz, double angle) {
        if (angle == 0.0) return new double[] {vx, vy, vz};
        double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (length < 1e-12) return new double[] {vx, vy, vz};
        double dx = vx / length, dy = vy / length, dz = vz / length;
        // right = view × (0, 1, 0)
        double rx = -dz, ry = 0.0, rz = dx;
        double rLength = Math.sqrt(rx * rx + rz * rz);
        if (rLength * rLength <= 1.0E-7) {
            rx = 1.0;
            rz = 0.0;
            rLength = 1.0;
        }
        rx /= rLength;
        rz /= rLength;
        double[] up = rotate(dx, dy, dz, rx, ry, rz, Math.PI / 2.0);
        double[] out = rotate(dx, dy, dz, up[0], up[1], up[2], angle);
        return new double[] {out[0] * length, out[1] * length, out[2] * length};
    }

    private static double[] rotate(double x, double y, double z,
            double ax, double ay, double az, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = ax * x + ay * y + az * z;
        double cx = ay * z - az * y;
        double cy = az * x - ax * z;
        double cz = ax * y - ay * x;
        return new double[] {
            x * cos + cx * sin + ax * dot * (1.0 - cos),
            y * cos + cy * sin + ay * dot * (1.0 - cos),
            z * cos + cz * sin + az * dot * (1.0 - cos),
        };
    }

    /**
     * 선택 칸 변경이 진행 중인 주손 활·삼지창·석궁 당김을 멈춘다(바닐라 handleSetCarriedItem 의
     * {@code getUsedItemHand() == MAIN_HAND} 일 때 {@code stopUsingItem}). 보조손 당김, 이미 장전된
     * 석궁(아이템 상태), 낚시찌는 사용 중 상태가 아니므로 그대로 둔다.
     */
    void stopMainHandUse(String nickname) {
        bowChargeStarted.keySet().removeIf(key -> key.mainHandOf(nickname));
        crossbowChargeStarted.keySet().removeIf(key -> key.mainHandOf(nickname));
    }

    /** 사망·퇴장·부활 경계에서 미완료 주손 사용이 다음 생명/연결로 새지 않게 한다. */
    void clearPlayerUseState(String nickname) {
        bowChargeStarted.keySet().removeIf(key -> key.belongsTo(nickname));
        crossbowChargeStarted.keySet().removeIf(key -> key.belongsTo(nickname));
        crossbowLoaded.removeIf(key -> key.belongsTo(nickname));
        crossbowLoadedAmmo.keySet().removeIf(key -> key.belongsTo(nickname));
        FishingCast cast = fishingCasts.remove(nickname);
        if (cast != null) retireBobber(cast);
    }

    // ── 낚시(FISHING) ──

    /**
     * 낚싯대 우클릭 한 번. 캐스팅 중이 아니면 찌를 던지고, 캐스팅 중이면 회수한다.
     * 돌려주는 값은 이번 회수로 소모할 낚싯대 내구다(바닐라 {@code FishingHook.retrieve} 의 i):
     * 전리품이면 {@code CATCH_DURABILITY_COST}, 엔티티를 걸었으면
     * {@code HOOKED_ENTITY_DURABILITY_COST}, 헛챔질·캐스팅이면 0 이다.
     */
    int handleFishingRodUse(PlayerTickState player, PlayerInventory.HandRef hand, long tickNo) {
        PlayerInventory inventory = player.inventory();
        if (!PlayerInventory.isFishingRod(inventory.stack(hand).itemType())) return 0;

        FishingCast cast = fishingCasts.get(player.nickname());
        if (cast == null) {
            castFishingLine(player, hand);
            return 0;
        }
        if (!sameHandRef(cast.hand, hand)) return 0;
        ProjectileSim hookedBobber = runtime.projectile(cast.bobberId);
        if (hookedBobber != null && hookedBobber.hooked()) {
            pullHookedEntity(player, hookedBobber);
            fishingCasts.remove(player.nickname());
            retireBobber(cast);
            return FishingRules.HOOKED_ENTITY_DURABILITY_COST;
        }
        boolean caught = cast.biting && tickNo <= cast.biteEndTick;
        if (caught) {
            ProjectileSim bobber = runtime.projectile(cast.bobberId);
            double bx = bobber == null ? player.x() : bobber.x;
            double by = bobber == null ? player.y() : bobber.y;
            double bz = bobber == null ? player.z() : bobber.z;
            // 바다의 행운은 범주 품질 보정에, open water 는 보물 등장 조건에 쓰인다.
            long rodEnchantments = inventory.stack(cast.hand).enchantments();
            FishingRules.LootDrop loot = FishingRules.rollLootDrop(cast.seed,
                    FishingRules.luckFromEnchantments(rodEnchantments),
                    bobberInOpenWater(bx, by, bz));
            rt.itemSystem().spawnFishingCatch(loot.itemType(), loot.count(),
                    loot.durability(), loot.enchantments(), bx, by, bz,
                    player.x(), player.y(), player.z());
            // 바닐라 FishingHook.retrieve 는 전리품과 함께 1~6 경험치를 플레이어 자리에 뿌린다.
            rt.xpOrbSystem().spawnOrbs(FishingRules.catchXp(cast.seed),
                    player.x(), player.y() + 0.5, player.z());
        }
        fishingCasts.remove(player.nickname());
        retireBobber(cast);
        return caught ? FishingRules.CATCH_DURABILITY_COST : 0;
    }

    /** 테스트가 자기 찌를 되짚을 때만 쓰는 조회. 권위 경로는 runtime.projectile 를 직접 쓴다. */
    ProjectileSim projectileForTest(long id) {
        return runtime.projectile(id);
    }

    /**
     * 걸린 엔티티를 캐스터 쪽으로 끌어당긴다(바닐라 {@code FishingHook.pullEntity}).
     * 몹은 서버가 직접 밀고, 플레이어는 이동이 클라 권위라 {@code fishingPull} 임펄스를 보낸다.
     */
    private void pullHookedEntity(PlayerTickState player, ProjectileSim bobber) {
        if (bobber.hookedPlacedEntityId != 0L) {
            rt.placedEntities().pullFishingHook(player, bobber);
            return;
        }
        if (bobber.hookedMobId != 0L) {
            Mob mob = runtime.mobById(bobber.hookedMobId);
            if (mob == null || mob.isDead() || mob.removed) return;
            mob.applyFishingPull(
                    FishingRules.entityPullPerAuthorityTick(player.x(), bobber.x),
                    FishingRules.entityPullPerAuthorityTick(player.y(), bobber.y),
                    FishingRules.entityPullPerAuthorityTick(player.z(), bobber.z));
            return;
        }
        if (bobber.hookedPlayer == null) return;
        PlayerTickState hooked = rt.players().get(bobber.hookedPlayer);
        if (hooked == null || hooked.isDead()) return;
        // 임펄스는 걸린 본인만 쓰므로 그 세션에만 보낸다(정적판 emitMessage 와 같은 대상).
        WebSocketSession session = rt.session(bobber.hookedPlayer);
        if (session == null) return;
        rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                new WsMessages.FishingPull(bobber.hookedPlayer,
                        FishingRules.entityPullBlocksPerSecond(player.x(), bobber.x),
                        FishingRules.entityPullBlocksPerSecond(player.y(), bobber.y),
                        FishingRules.entityPullBlocksPerSecond(player.z(), bobber.z)));
    }

    /** 시선 방향으로 찌를 던진다. 스노우볼과 같은 세기·눈높이를 쓰되 방향 오차는 주지 않는다. */
    private void castFishingLine(PlayerTickState player, PlayerInventory.HandRef hand) {
        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double scale = FishingRules.CAST_POWER / Math.sqrt(dx * dx + dy * dy + dz * dz);
        ProjectileSim bobber = runtime.castFishingBobber(
                player.nickname(), player.x(),
                player.y() + PlayerInteractionRules.eyeHeight(player.crouching()), player.z(),
                dx * scale, dy * scale, dz * scale);
        announcePlayerProjectile(player, bobber, -1);
        fishingCasts.put(player.nickname(), new FishingCast(bobber.id, hand));
    }

    /**
     * 찌를 브로드캐스트 제거까지 포함해 거둔다. 회수·취소·사망 경계가 모두 이 경로를 탄다.
     * 프로토콜에 새 사유를 늘리지 않도록 종결 사유는 기존 "expired" 를 쓴다.
     */
    private void retireBobber(FishingCast cast) {
        ProjectileSim bobber = runtime.projectile(cast.bobberId);
        if (bobber == null) return;
        bobber.alive = false;
        bobber.terminalReason = "expired";
        terminatedArrows.put(bobber.id, new ProjectileRemoval(bobber.id, "expired",
                bobber.x, bobber.y, bobber.z, null));
        runtime.removeProjectile(bobber.id);
    }

    /**
     * 캐스팅 진행 판정. 착수 시점에 시드를 확정해 입질/판정창 틱을 정하고, 입질 동안에는
     * 찌를 잠기게 한다. 위치 변화는 같은 틱의 projectileUpdate 로 나가므로 새 메시지가 없다.
     */
    private void tickFishing(long tickNo) {
        if (fishingCasts.isEmpty()) return;
        Iterator<Map.Entry<String, FishingCast>> it = fishingCasts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FishingCast> entry = it.next();
            PlayerTickState player = rt.players().get(entry.getKey());
            FishingCast cast = entry.getValue();
            ProjectileSim bobber = runtime.projectile(cast.bobberId);
            // 찌가 이미 소멸(물 아님·비행 시간 초과)했거나 플레이어가 사라지면 캐스팅도 끝난다.
            if (player == null || player.isDead() || bobber == null || !bobber.alive) {
                it.remove();
                if (bobber != null) retireBobber(cast);
                continue;
            }
            // 바닐라 shouldStopFishing: 낚싯대를 더 들고 있지 않으면 찌가 즉시 사라진다.
            boolean movedOffMainSlot = cast.hand.hand() == PlayerInventory.Hand.MAIN
                    && player.inventory().selectedSlot() != cast.hand.mainSlot();
            if (movedOffMainSlot || !PlayerInventory.isFishingRod(
                    player.inventory().stack(cast.hand).itemType())) {
                it.remove();
                retireBobber(cast);
                continue;
            }
            int lureLevel = FishingRules.lureFromEnchantments(
                    player.inventory().stack(cast.hand).enchantments());
            // 걸린 찌는 엔티티를 따라다닌다(바닐라 setPos(hookedIn.getY(0.8))). 대상이
            // 사라지면 찌도 캐스팅과 함께 거둔다.
            if (bobber.hooked()) {
                double beforeX = bobber.x;
                double beforeY = bobber.y;
                double beforeZ = bobber.z;
                try {
                    if (!followHookedEntity(bobber)
                            || FishingRules.tetherBroken(player.x(), player.y(), player.z(),
                                    bobber.x, bobber.y, bobber.z)) {
                        it.remove();
                        retireBobber(cast);
                    }
                } finally {
                    if (runtime.projectile(bobber.id) == bobber
                            && projectilePositionChanged(beforeX, beforeY, beforeZ, bobber)) {
                        runtime.markProjectileMutation(bobber);
                    }
                }
                continue;
            }
            if (!bobber.landed) continue;
            if (FishingRules.tetherBroken(player.x(), player.y(), player.z(),
                    bobber.x, bobber.y, bobber.z)) {
                it.remove();
                retireBobber(cast);
                continue;
            }
            if (!cast.landed) {
                cast.landed = true;
                cast.surfaceY = bobber.y;
                scheduleBite(player, cast, tickNo, bobber, lureLevel);
                worldSound("fishing_cast", bobber.x, bobber.y, bobber.z, (short) 0);
                continue;
            }
            if (!cast.biting) {
                int step = fishingWaitStep(cast, tickNo, bobber);
                if (cast.approachTarget < 0) {
                    cast.waitProgress += step;
                    if (cast.waitProgress < cast.waitTarget) continue;
                    // 바닐라: 대기가 끝나면 곧장 물지 않고 물고기가 다가오는 구간이 하나 더 있다.
                    cast.approachTarget = FishingRules.approachVanillaTicks(cast.seed);
                    cast.approachProgress = 0;
                    cast.fishAngle = FishingRules.approachStartAngleDegrees(cast.seed);
                    announceFishingApproach(cast, bobber);
                    continue;
                }
                cast.approachProgress += step;
                cast.fishAngle += FishingRules.approachAngleDriftDegrees(cast.seed, tickNo);
                if (cast.approachProgress < cast.approachTarget) {
                    announceFishingApproach(cast, bobber);
                    continue;
                }
                cast.biting = true;
                cast.biteEndTick = tickNo + FishingRules.biteWindowTicks(cast.seed);
                double beforeY = bobber.y;
                bobber.y = cast.surfaceY - FishingRules.BITE_SINK_DEPTH;
                if (Double.doubleToLongBits(beforeY) != Double.doubleToLongBits(bobber.y)) {
                    runtime.markProjectileMutation(bobber);
                }
                worldSound("fishing_bite", bobber.x, bobber.y, bobber.z, (short) 0);
                continue;
            }
            // 판정창을 놓쳤다. 찌를 띄우고 새 시드로 다음 입질을 예약해 계속 기다릴 수 있게 한다.
            if (cast.biting && tickNo > cast.biteEndTick) {
                cast.biting = false;
                double beforeY = bobber.y;
                bobber.y = cast.surfaceY;
                if (Double.doubleToLongBits(beforeY) != Double.doubleToLongBits(bobber.y)) {
                    runtime.markProjectileMutation(bobber);
                }
                scheduleBite(player, cast, tickNo, bobber, lureLevel);
            }
        }
    }

    /**
     * 착수 지점·틱으로 시드를 확정하고 채워야 할 대기량을 정한다. 미끼 인챈트는 바닐라와 같이
     * 대기량 자체를 레벨당 100 바닐라 틱 깎고, 낚싯대 종류 보정은 그 결과 위에 곱한다.
     * 둘 다 바닐라가 Lure 를 읽는 시점과 같이 <b>대기량을 뽑는 이 순간</b>의 주손 아이템으로 정한다.
     */
    private void scheduleBite(PlayerTickState player, FishingCast cast, long tickNo,
                              ProjectileSim bobber, int lureLevel) {
        cast.seed = FishingRules.castSeed(rt.seed(), tickNo,
                (int) Math.floor(bobber.x), (int) Math.floor(bobber.y), (int) Math.floor(bobber.z));
        short rodType = player.inventory().stack(cast.hand).itemType();
        cast.waitTarget = FishingRules.biteDelayVanillaTicks(cast.seed, lureLevel, rodType);
        cast.waitProgress = 0;
        cast.approachTarget = -1;
        cast.approachProgress = 0;
        cast.fishAngle = 0;
        cast.biteEndTick = -1;
    }

    private static boolean sameHandRef(
            PlayerInventory.HandRef left, PlayerInventory.HandRef right) {
        return left.hand() == right.hand() && left.mainSlot() == right.mainSlot();
    }

    /**
     * 접근 물결 한 표본을 방송한다. 바닐라는 물결 자리가 물일 때만 파티클을 내므로
     * 같은 조건을 여기서 본다(물이 아니면 이 틱에는 아무것도 보내지 않는다).
     */
    private void announceFishingApproach(FishingCast cast, ProjectileSim bobber) {
        int remaining = Math.max(cast.approachTarget - cast.approachProgress, 0);
        double wx = FishingRules.approachWakeX(bobber.x, cast.fishAngle, remaining);
        double wz = FishingRules.approachWakeZ(bobber.z, cast.fishAngle, remaining);
        double wy = FishingRules.approachWakeY(bobber.y);
        int below = fluidBlocks.get((int) Math.floor(wx), (int) Math.floor(wy - 1.0),
                (int) Math.floor(wz)) & 0xFFFF;
        if (!Fluids.isWater(below)) return;
        // 파티클 사실이므로 바닐라 sendParticles 와 같은 32블록 안에만 보낸다(SoundRules).
        WsMessages.FishingApproach message =
                new WsMessages.FishingApproach(bobber.id, wx, wy, wz, remaining);
        for (PlayerTickState listener : rt.players().values()) {
            if (!SoundRules.audible(wx, wy, wz, listener.x(), listener.y(), listener.z(),
                    SoundRules.PARTICLE_RANGE)) continue;
            WebSocketSession session = rt.session(listener.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
            }
        }
    }

    /**
     * 걸린 찌를 엔티티 위치로 옮긴다. 대상이 사라졌으면 false 를 돌려 캐스팅을 끝낸다.
     * 위치 갱신 자체는 같은 틱의 projectileUpdate 가 실어 나른다.
     */
    private boolean followHookedEntity(ProjectileSim bobber) {
        if (bobber.hookedPlacedEntityId != 0L) return rt.placedEntities().followFishingHook(bobber);
        if (bobber.hookedMobId != 0L) {
            Mob mob = runtime.mobById(bobber.hookedMobId);
            if (mob == null || mob.isDead() || mob.removed) return false;
            bobber.x = mob.x;
            bobber.y = FishingRules.hookedBobberY(mob.y, mob.height());
            bobber.z = mob.z;
            return true;
        }
        if (bobber.hookedPlayer == null) return false;
        PlayerTickState hooked = rt.players().get(bobber.hookedPlayer);
        if (hooked == null || hooked.isDead()) return false;
        bobber.x = hooked.x();
        bobber.y = FishingRules.hookedBobberY(hooked.y(), ProjectileSim.PLAYER_HEIGHT);
        bobber.z = hooked.z();
        return true;
    }

    /** 찌가 바닐라 open water(5×4×5) 위에 떠 있는가. 보물 전리품의 유일한 조건이다. */
    private boolean bobberInOpenWater(double bx, double by, double bz) {
        return FishingRules.isOpenWater(fluidBlocks, (int) Math.floor(bx),
                (int) Math.floor(by), (int) Math.floor(bz));
    }

    /**
     * 이번 틱에 채워지는 대기량. 바닐라와 같이 찌 <b>바로 위 칸</b>의 비·하늘 노출을 본다.
     * 맑고 열린 하늘이면 항상 {@code VANILLA_TICKS_PER_TICK} 이라 기존 타이밍과 같다.
     */
    private int fishingWaitStep(FishingCast cast, long tickNo, ProjectileSim bobber) {
        int bx = (int) Math.floor(bobber.x);
        int by = (int) Math.floor(bobber.y) + 1;
        int bz = (int) Math.floor(bobber.z);
        return FishingRules.waitStepTicks(cast.seed, tickNo,
                world.isRainingAt(bx, by, bz), world.openToSky(bx, by, bz));
    }

    /** 투사체 프로토콜을 공유하는 엔더의 눈 비행체용 id({@link EnderEyeSystem}). */
    void relocatePearl(ProjectileSim pearl) { runtime.markProjectileMutation(pearl); }

    long reserveProjectileId() {
        return runtime.reserveProjectileId();
    }

    /** 요청 손의 눈덩이 한 개를 소비하고 바닐라 발사력·오차로 서버 권위 투사체를 만듭니다. */
    private final java.util.Map<String, Long> pearlCooldowns = new java.util.HashMap<>();

    boolean handleEnderPearlThrow(PlayerTickState player, PlayerInventory.HandRef hand) {
        long now = rt.clock().tickCount();
        if (now < pearlCooldowns.getOrDefault(player.nickname(), 0L)) return false;
        PlayerInventory inventory = player.inventory();
        long sourceInventoryRevision = inventory.revision();
        if (!inventory.consumeOne(hand, PlayerInventory.ENDER_PEARL)) return false;

        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double noise = ProjectileSim.INACCURACY_NOISE_SCALE * ProjectileSim.SNOWBALL_INACCURACY;
        dx += (rng.nextDouble()-rng.nextDouble()) * noise;
        dy += (rng.nextDouble()-rng.nextDouble()) * noise;
        dz += (rng.nextDouble()-rng.nextDouble()) * noise;
        double scale = ProjectileSim.SNOWBALL_POWER;
        ProjectileSim snowball = runtime.throwPlayerEnderPearl(
                player.nickname(), player.x(), player.y() + (player.crouching() ? 1.27 : 1.62) - 0.1, player.z(),
                dx * scale, dy * scale, dz * scale);
        announcePlayerProjectile(player, snowball, sourceInventoryRevision);
        pearlCooldowns.put(player.nickname(), now + 10);
        return true;
    }

    boolean handleSnowballThrow(PlayerTickState player, PlayerInventory.HandRef hand) {
        PlayerInventory inventory = player.inventory();
        long sourceInventoryRevision = inventory.revision();
        if (!inventory.consumeOne(hand, PlayerInventory.SNOWBALL)) return false;

        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double noise = ProjectileSim.INACCURACY_NOISE_SCALE * ProjectileSim.SNOWBALL_INACCURACY;
        dx += nextGaussian() * noise;
        dy += nextGaussian() * noise;
        dz += nextGaussian() * noise;
        double scale = ProjectileSim.SNOWBALL_POWER / Math.sqrt(dx * dx + dy * dy + dz * dz);
        ProjectileSim snowball = runtime.throwPlayerSnowball(
                player.nickname(), player.x(), player.y() + 1.52, player.z(),
                dx * scale, dy * scale, dz * scale);
        announcePlayerProjectile(player, snowball, sourceInventoryRevision);
        return true;
    }

    /**
     * 선택 슬롯의 달걀 한 개를 소비하고 눈덩이와 같은 발사력·오차로 권위 투사체를 만듭니다.
     * 바닐라 {@code EggItem.use} 도 {@code shootFromRotation(..., 1.5F, 1.0F)} 라 눈덩이와
     * 같은 값이며, 그래서 발사 계산도 같은 상수를 공유합니다.
     */
    boolean handleEggThrow(PlayerTickState player, PlayerInventory.HandRef hand) {
        PlayerInventory inventory = player.inventory();
        // [ZOMBIE-ANIMAL] 상한 달걀은 같은 경로를 재사용한다 — 발사력·오차·물리가 전부 같고
        // 부화 굴림 하나만 갈린다.
        short thrown = inventory.stack(hand).itemType();
        boolean spoiled = thrown == PlayerInventory.SPOILED_EGG;
        String eggVariant = thrown == PlayerInventory.EGG ? "temperate"
                : thrown == PlayerInventory.BROWN_EGG ? "warm"
                : thrown == PlayerInventory.BLUE_EGG ? "cold" : null;
        if (eggVariant == null && !spoiled) return false;
        long sourceInventoryRevision = inventory.revision();
        if (!inventory.consumeOne(hand, thrown)) return false;

        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double noise = ProjectileSim.INACCURACY_NOISE_SCALE * ProjectileSim.SNOWBALL_INACCURACY;
        dx += nextGaussian() * noise;
        dy += nextGaussian() * noise;
        dz += nextGaussian() * noise;
        double scale = ProjectileSim.SNOWBALL_POWER / Math.sqrt(dx * dx + dy * dy + dz * dz);
        ProjectileSim egg = runtime.throwPlayerEgg(
                player.nickname(), player.x(), player.y() + 1.52, player.z(),
                dx * scale, dy * scale, dz * scale, eggVariant, spoiled);
        announcePlayerProjectile(player, egg, sourceInventoryRevision);
        return true;
    }

    /** 26.3 WindChargeItem: zero pitch offset, power 1.5/MC tick and a 0.5-second item cooldown. */
    boolean handleWindChargeThrow(PlayerTickState player, PlayerInventory.HandRef hand) {
        if (!player.windChargeReady()) return false;
        PlayerInventory inventory = player.inventory();
        long sourceInventoryRevision = inventory.revision();
        if (!inventory.consumeOne(hand, PlayerInventory.WIND_CHARGE)) return false;
        double yaw = player.yaw();
        double pitch = player.pitch();
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double deviation = ProjectileSim.INACCURACY_NOISE_SCALE;
        dx += nextTriangle(deviation);
        dy += nextTriangle(deviation);
        dz += nextTriangle(deviation);
        // Official 1.5 blocks/MC-tick, advanced once per 10 TPS authority step.
        double scale = 3.0 / Math.max(1e-9, Math.sqrt(dx * dx + dy * dy + dz * dz));
        ProjectileSim charge = runtime.throwPlayerWindCharge(
                player.nickname(), player.x(), player.y() + 1.62, player.z(),
                dx * scale, dy * scale, dz * scale);
        player.startWindChargeCooldown();
        announcePlayerProjectile(player, charge, sourceInventoryRevision);
        return true;
    }

    /**
     * [POTION] 선택 슬롯의 투척 물약 한 개를 소비하고 바닐라 {@code ThrownPotion} 발사력·오차로
     * 서버 권위 투사체를 만듭니다. 착탄 스플래시는 마녀 물약과 완전히 같은 경로
     * ({@link MobEvent.SplashPotion} → {@code handleSplashPotion})를 탑니다.
     */
    boolean handlePotionThrow(PlayerTickState player, PlayerInventory.HandRef hand) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot held = inventory.stack(hand);
        short type = held.itemType();
        // [CONTAINER-MENUS] The same throw message carries the experience bottle.
        if (type == PlayerInventory.EXPERIENCE_BOTTLE) return handleExperienceBottleThrow(player, hand);
        // [UTILITY] 전용 ID · 범용 물약(CONTENTS_*) 모두 (형태, 물약 키)로 푼다. 투척은 투척용·잔류형만.
        com.gameexpert.engine.effect.PotionCatalog.Contents contents =
                com.gameexpert.engine.effect.PotionCatalog.resolve(
                        type, held.itemComponents().potionContents());
        if (contents == null
                || contents.form() == com.gameexpert.engine.effect.PotionCatalog.Form.POTION) {
            return false;
        }
        // [TRIAL-GAP] 잔류형 물약도 같은 ThrowablePotionItem 투척이다(발사력·각도·오차 동일).
        boolean lingering =
                contents.form() == com.gameexpert.engine.effect.PotionCatalog.Form.LINGERING;
        // 대표 효과(첫 효과)는 옛 경로·방송용이다. 착탄은 potionKey 로 효과 전부를 건다. 효과 없는
        // 물약(물·평범한 물약 등)도 바닐라처럼 던질 수 있고 착탄해도 아무 효과가 없다.
        java.util.List<ProjectileEffect> effects = lingering
                ? com.gameexpert.engine.effect.PotionCatalog.lingeringEffects(contents.key())
                : com.gameexpert.engine.effect.PotionCatalog.splashEffects(contents.key());
        ProjectileEffect effect = effects.isEmpty() ? null : effects.get(0);
        long sourceInventoryRevision = inventory.revision();
        if (!inventory.consumeOne(hand, type)) return false;

        double yaw = player.yaw();
        // 바닐라는 물약을 시선보다 20도 위로 던진다(shootFromRotation 의 -20).
        double pitch = player.pitch()
                + Math.toRadians(ProjectileSim.PLAYER_POTION_PITCH_OFFSET_DEGREES);
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double noise = ProjectileSim.INACCURACY_NOISE_SCALE * ProjectileSim.SNOWBALL_INACCURACY;
        dx += nextGaussian() * noise;
        dy += nextGaussian() * noise;
        dz += nextGaussian() * noise;
        double scale = ProjectileSim.PLAYER_POTION_POWER
                / Math.sqrt(dx * dx + dy * dy + dz * dz);
        ProjectileSim thrown = lingering
                ? ProjectileSim.fromPlayerLingeringPotion(
                        player.nickname(), player.x(), player.y() + 1.52, player.z(),
                        dx * scale, dy * scale, dz * scale, effect, type)
                : ProjectileSim.fromPlayerPotion(
                        player.nickname(), player.x(), player.y() + 1.52, player.z(),
                        dx * scale, dy * scale, dz * scale, effect, type);
        thrown.potionKey = contents.key();
        ProjectileSim potion = runtime.addWorldProjectile(thrown);
        announcePlayerProjectile(player, potion, sourceInventoryRevision);
        return true;
    }

    /**
     * [CONTAINER-MENUS] {@code ExperienceBottleItem#use}: one bottle leaves the hand and a
     * {@code ThrownExperienceBottle} is shot from the eye (-0.1) 20 degrees above the view at
     * power 0.7 and inaccuracy 1 ({@code shootFromRotation(-20, 0.7, 1)}). The client derives the
     * {@code entity.experience_bottle.throw} sound from the projectile spawn.
     */
    boolean handleExperienceBottleThrow(PlayerTickState player, PlayerInventory.HandRef hand) {
        PlayerInventory inventory = player.inventory();
        long sourceInventoryRevision = inventory.revision();
        if (!inventory.consumeOne(hand, PlayerInventory.EXPERIENCE_BOTTLE)) return false;
        double yaw = player.yaw();
        double pitch = player.pitch()
                + Math.toRadians(ProjectileSim.PLAYER_POTION_PITCH_OFFSET_DEGREES);
        double horizontal = Math.cos(pitch);
        double dx = -Math.sin(yaw) * horizontal;
        double dy = Math.sin(pitch);
        double dz = -Math.cos(yaw) * horizontal;
        double noise = ProjectileSim.INACCURACY_NOISE_SCALE * ProjectileSim.SNOWBALL_INACCURACY;
        dx += nextGaussian() * noise;
        dy += nextGaussian() * noise;
        dz += nextGaussian() * noise;
        double scale = ProjectileSim.PLAYER_EXPERIENCE_BOTTLE_POWER
                / Math.sqrt(dx * dx + dy * dy + dz * dz);
        ProjectileSim bottle = runtime.addWorldProjectile(ProjectileSim.experienceBottle(
                player.nickname(), player.x(), player.y() + 1.52, player.z(),
                dx * scale, dy * scale, dz * scale));
        announcePlayerProjectile(player, bottle, sourceInventoryRevision);
        return true;
    }

    /**
     * [CONTAINER-MENUS] {@code ExperienceOrb.awardWithDirection(level, hit, direction, 3 + nextInt(5)
     * + nextInt(5))}: the orbs start half an orb box (0.25) along the direction from the hit point.
     */
    private void awardExperienceBottle(MobEvent.ExperienceBottleHit hit) {
        int amount = 3 + rng.nextInt(5) + rng.nextInt(5);
        double dx = hit.directionX(), dy = hit.directionY(), dz = hit.directionZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double offset = length > 1.0e-9 ? 0.25 / length : 0.0;
        rt.xpOrbSystem().spawnOrbs(amount, hit.x() + dx * offset, hit.y() + dy * offset,
                hit.z() + dz * offset);
    }

    /**
     * 선택 슬롯의 폭죽 로켓 한 개를 소비합니다. 활공 중이면 바닐라 부스트 창을 방송하고,
     * 그 밖에는 눈높이에서 스스로 상승하는 로켓 엔티티를 만듭니다. 별(firework star)이 없어
     * 두 경우 모두 폭발 피해는 0입니다(바닐라 동일).
     */
    boolean handleFireworkUse(PlayerTickState player, PlayerInventory.HandRef hand) {
        return handleFireworkUse(player, hand, null);
    }

    boolean handleFireworkUse(PlayerTickState player, PlayerInventory.HandRef hand,
            String finalSceneActionId) {
        PlayerInventory inventory = player.inventory();
        short type = inventory.stack(hand).itemType();
        int flightDuration = PlayerInventory.fireworkFlightDuration(type);
        long sourceInventoryRevision = inventory.revision();
        if (flightDuration == 0 || !inventory.consumeOne(hand, type)) return false;

        // 바닐라 FireworkRocketEntity: lifetime = 10 × flight_duration + rand(6) + rand(7) (20 TPS).
        int vanillaLifetime = 10 * flightDuration
                + rng.nextInt(6) + rng.nextInt(7);
        double eyeY = player.y() + 1.62;
        worldSound("firework_launch", player.x(), eyeY, player.z(), type);
        if (player.gliding()) {
            // 부착 로켓은 독립 궤적이 없다. 부스트 창만 방송하고 클라이언트가 자기 이동 예측에 적용한다.
            broadcast(new WsMessages.FireworkBoost(
                    player.nickname(), vanillaLifetime, finalSceneActionId));
            return true;
        }
        // 서버는 10 TPS 라 바닐라 수명의 절반 틱만큼 산다(올림).
        ProjectileSim rocket = runtime.launchPlayerFirework(
                player.nickname(), player.x(), eyeY, player.z(), 0.0, 0.0, 0.0,
                Math.max(1, (vanillaLifetime + 1) / 2));
        announcePlayerProjectile(player, rocket, sourceInventoryRevision);
        return true;
    }

    /**
     * 같은 서버 틱에 충돌해도 클라이언트가 생성→충돌 순서를 관찰하도록 플레이어 발사체는 즉시 알립니다.
     * knownArrows 등록으로 정규 활성 목록 브로드캐스트의 중복 spawn은 막습니다.
     */
    /**
     * [CONTAINER-MENUS] {@code ProjectileDispenseBehavior}: an ownerless projectile (the dispenser
     * has no owner, exactly like vanilla's {@code asProjectile(level, position, stack, direction)})
     * launched on the same chunk index, tick, persistence and broadcast path as a thrown one. The
     * motion is already the repository's per-kind simulation scale of the vanilla dispense power.
     * Returns null for an item without a repository projectile.
     */
    ProjectileSim launchDispensedProjectile(short itemType, double x, double y, double z,
            double vx, double vy, double vz) {
        ProjectileSim projectile;
        if (itemType == PlayerInventory.SNOWBALL) {
            projectile = runtime.throwPlayerSnowball(null, x, y, z, vx, vy, vz);
        } else if (itemType == PlayerInventory.EGG || itemType == PlayerInventory.BROWN_EGG
                || itemType == PlayerInventory.BLUE_EGG) {
            String variant = itemType == PlayerInventory.EGG ? "temperate"
                    : itemType == PlayerInventory.BROWN_EGG ? "warm" : "cold";
            projectile = runtime.throwPlayerEgg(null, x, y, z, vx, vy, vz, variant, false);
        } else if (itemType == PlayerInventory.WIND_CHARGE) {
            projectile = runtime.throwPlayerWindCharge(null, x, y, z, vx, vy, vz);
        } else if (itemType == PlayerInventory.ARROW) {
            // AbstractArrow base damage 2.0 scaled by the launch speed (1.1): ceil(2.2) = 3.
            projectile = runtime.shootPlayerArrow(null, x, y, z, vx, vy, vz, 3);
        } else if (PlayerInventory.isTippedArrow(itemType)
                || itemType == PlayerInventory.SPECTRAL_ARROW) {
            // TippedArrowItem / SpectralArrowItem: the arrow physics and damage of ARROW plus the
            // potion payload or the 200-tick glowing of SpectralArrow.
            projectile = ProjectileSim.dispensedTippedArrow(x, y, z, vx, vy, vz, 3,
                    PotionRules.ammoEffect(itemType), itemType);
            runtime.addWorldProjectile(projectile);
        } else if (PlayerInventory.isLingeringPotion(itemType)) {
            projectile = ProjectileSim.dispensedLingeringPotion(x, y, z, vx, vy, vz,
                    PotionRules.lingeringEffect(itemType), itemType);
            runtime.addWorldProjectile(projectile);
        } else if (itemType == PlayerInventory.EXPERIENCE_BOTTLE) {
            // [CONTAINER-MENUS] ExperienceBottleItem.asProjectile: an ownerless bottle.
            projectile = runtime.addWorldProjectile(
                    ProjectileSim.experienceBottle(null, x, y, z, vx, vy, vz));
        } else if (PotionRules.splashEffect(itemType) != null) {
            projectile = runtime.throwPlayerPotion(null, x, y, z, vx, vy, vz,
                    PotionRules.splashEffect(itemType));
        } else if (PlayerInventory.fireworkFlightDuration(itemType) > 0) {
            int vanillaLifetime = 10 * PlayerInventory.fireworkFlightDuration(itemType)
                    + rng.nextInt(6) + rng.nextInt(7);
            projectile = runtime.launchPlayerFirework(null, x, y, z, vx, vy, vz,
                    Math.max(1, (vanillaLifetime + 1) / 2));
        } else {
            return null;
        }
        knownArrows.add(projectile.id);
        broadcast(projectileSpawnMessage(projectile));
        return projectile;
    }

    /**
     * [CONTAINER-MENUS] {@code FireChargeItem} through {@code ProjectileDispenseBehavior}: an
     * ownerless {@code SmallFireball} along the normalised triangle direction at acceleration 0.1
     * (the ominous item spawner's same {@link ProjectileSim#dispensedSmallFireball} scale).
     */
    ProjectileSim launchDispensedFireball(double x, double y, double z,
            double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double scale = ProjectileSim.FIREBALL_ACCELERATION * 2.0 / Math.max(1e-12, length);
        ProjectileSim fireball = ProjectileSim.dispensedSmallFireball(x, y, z,
                dx * scale, dy * scale, dz * scale);
        runtime.addWorldProjectile(fireball);
        knownArrows.add(fireball.id);
        broadcast(projectileSpawnMessage(fireball));
        return fireball;
    }

    /**
     * [CONTAINER-MENUS] {@code Level.getEntitiesOfClass(LivingEntity.class, new AABB(pos))}: live
     * mobs whose box overlaps the unit cell, in id order.
     */
    /** [CONTAINER-MENUS] {@code Level.getEntities(null, box)} for mobs: none intersects the box. */
    boolean noMobIntersecting(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            double half = mob.width() / 2.0;
            if (mob.x + half > minX && mob.x - half < maxX && mob.y + mob.height() > minY
                    && mob.y < maxY && mob.z + half > minZ && mob.z - half < maxZ) {
                return false;
            }
        }
        return true;
    }

    List<Mob> mobsTouchingCell(int x, int y, int z) {
        List<Mob> touching = new ArrayList<>();
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            double half = mob.width() / 2.0;
            if (mob.x + half > x && mob.x - half < x + 1
                    && mob.y + mob.height() > y && mob.y < y + 1
                    && mob.z + half > z && mob.z - half < z + 1) {
                touching.add(mob);
            }
        }
        touching.sort(java.util.Comparator.comparingLong(mob -> mob.id));
        return touching;
    }

    /**
     * [CONTAINER-MENUS] {@code ShearsDispenseItemBehavior.tryShearEntity} for one mob:
     * {@code shearOffAllLeashConnections} (the lead drops) or a ready {@code Shearable}
     * (sheep, mooshroom, the sulfur cube). True when the mob was sheared or unleashed.
     */
    /** [CONTAINER-MENUS] The worldSound kind of a mob's {@code Shearable#shear}. */
    static String shearSoundKind(Mob mob) {
        if (mob.type == MobType.SNOW_GOLEM) return "snow_golem_shear";
        if (mob.type == MobType.BOGGED) return "bogged_shear";
        return "shears";
    }

    /** Whether a lead holds the mob (the dispenser cuts it before shearing). */
    boolean leashed(Mob mob) {
        return runtime.leashHolderNickname(mob.id) != null;
    }

    boolean dispenserShear(Mob mob) {
        if (runtime.leashHolderNickname(mob.id) != null
                && runtime.detachLeash(mob.id, null) != null) {
            rt.itemSystem().spawnDeathDrop(PlayerInventory.LEAD, 1, 0, mob.x, mob.y + 0.5, mob.z);
            return true;
        }
        if (mob.readyForShearing()) {
            int color = mob.sheepColor();
            if (color < 0 || color >= Blocks.WOOL_BY_DYE_COLOR.length) return false;
            int wool = FarmAnimalRules.shearWoolCount(rng);
            mob.shearSheep();
            rt.itemSystem().spawnDeathDrop((short) Blocks.WOOL_BY_DYE_COLOR[color], wool, 0,
                    mob.x, mob.y + 0.5, mob.z);
            runtime.refreshPersistenceSnapshot(mob);
            return true;
        }
        if (mob.type == MobType.MOOSHROOM) {
            // MushroomCow#shear: the adult converts to a cow and drops five mushrooms.
            return confirmShearMooshroom(planShearMooshroom(mob)) != null;
        }
        if (shearSnowGolemOrBogged(mob)) return true;
        if (mob instanceof SulfurCube cube) {
            short body = cube.shearBodyItem();
            if (body == 0) return false;
            rt.itemSystem().spawnDeathDrop(body, 1, 0, mob.x, mob.y + mob.height() * .5, mob.z);
            runtime.refreshPersistenceSnapshot(mob);
            return true;
        }
        return false;
    }

    /**
     * [CONTAINER-MENUS] {@code DispenseItemBehavior$2} for one mob: an alive tamed chested horse
     * (donkey, mule, llama) without a chest takes it ({@code AbstractChestedHorse} slot 499).
     */
    boolean dispenserChestHorse(Mob mob) {
        if (mob.isDead() || mob.removed || !mob.horseChestable() || mob.horseChested()
                || mob.isBaby() || !mob.horseTamed() || !liveHorseMenuTarget(mob)
                || !preflightHorseMenuMutation(mob) || !mob.attachHorseChest()) return false;
        mob.setPersistenceRequired(true);
        runtime.refreshPersistenceSnapshot(mob);
        return true;
    }

    /**
     * [CONTAINER-MENUS] The brush dispense ({@code DispenseItemBehavior$11} over the armadillos in
     * the target cell, {@code Armadillo#brushOffScute}): any adult armadillo, whatever its shell
     * state (javap: the only guard is {@code isBaby}).
     */
    boolean dispenserBrush(Mob mob) {
        if (!(mob instanceof Armadillo) || mob.isBaby()) return false;
        rt.itemSystem().spawnMobDrop(PlayerInventory.ARMADILLO_SCUTE, 1, mob.x, mob.y + 0.5, mob.z);
        return true;
    }

    /**
     * [CONTAINER-MENUS] {@code EquipmentDispenseItemBehavior.dispenseEquipment} onto a mob
     * ({@code LivingEntity#canEquipWithDispenser}): the stack's slot must be empty and
     * {@code canDispenserEquipIntoSlot} must allow it — a pig's saddle (adult), a tamed adult
     * horse family member's saddle or body armor, a tamed llama's carpet. Every other mob's
     * {@code canDispenserEquipIntoSlot} is {@code canPickUpLoot}, which no dispensable
     * item-slot of this repository's mob equipment offers. {@code setPersistenceRequired}.
     */
    boolean dispenserEquip(Mob mob, PlayerInventory.StackSnapshot one) {
        short item = one.itemType();
        boolean equipped = false;
        if (item == PlayerInventory.SADDLE) {
            if (mob.type == MobType.PIG) {
                equipped = !mob.isBaby() && mob.saddlePig();
            } else if (HorseRules.isHorseFamily(mob.type) && mob.type != MobType.LLAMA
                    && mob.type != MobType.TRADER_LLAMA && !mob.isBaby() && mob.horseTamed()
                    && !mob.horseSaddled() && liveHorseMenuTarget(mob)
                    && preflightHorseMenuMutation(mob)) {
                equipped = mob.saddleHorse(singleItem(one));
            }
        } else if (HorseRules.isArmorItem(item)) {
            HorseArmorEquipPlan plan = planEquipHorseArmor(mob, one);
            equipped = plan != null && confirmEquipHorseArmor(plan);
        } else if (LlamaRules.isCarpet(item)) {
            boolean llama = mob.type == MobType.LLAMA || mob.type == MobType.TRADER_LLAMA;
            equipped = llama && mob.horseTamed() && liveHorseMenuTarget(mob)
                    && mob.llamaCarpetColor() == LlamaRules.NO_CARPET
                    && preflightHorseMenuMutation(mob) && mob.decorateLlama(singleItem(one));
        }
        if (!equipped) return false;
        mob.setPersistenceRequired(true);
        runtime.refreshPersistenceSnapshot(mob);
        return true;
    }

    private void announcePlayerProjectile(PlayerTickState player, ProjectileSim projectile,
            long expectedPlayerRevision) {
        // Persistence precedes the observable launch event. Throwable/trident consumption and the
        // live projectile row therefore cross the crash boundary in one database transaction.
        rt.persistPlayerProjectileLaunch(player, expectedPlayerRevision);
        knownArrows.add(projectile.id);
        broadcast(projectileSpawnMessage(projectile));
    }

    /**
     * [TRIAL-GAP] 투사체 생성 메시지. 아이템을 싣는 종류(잔류형 물약 · 효과 구름 · 불길한 아이템
     * 소환기 · 효과 화살)만 itemType 을, 효과 구름만 radius 를 싣는다.
     */
    private static WsMessages.ProjectileSpawn projectileSpawnMessage(ProjectileSim projectile) {
        Integer itemType = projectile.payloadItemType > 0 ? (int) projectile.payloadItemType : null;
        Double radius = projectile.isCloud() ? projectile.cloudRadius : null;
        return new WsMessages.ProjectileSpawn(
                projectile.kind.protocolName(), projectile.id,
                projectile.x, projectile.y, projectile.z,
                projectile.vx, projectile.vy, projectile.vz,
                projectile.shooterNickname(), projectile.eggVariant, itemType, radius,
                projectile.inGround ? Boolean.TRUE : null);
    }

    /** Complete durable live set, including projectiles frozen in currently inactive chunks. */
    List<ProjectilePersistenceSnapshot> projectilePersistenceSnapshot() {
        // [EC-MOBS] 셜커 탄환은 조향 상태를 steeringData 칸에 싣고, [DRAGON] 드래곤 화염구 · 숨결 구름은 위치 · 속도 ·
        // 주인 · 구름 칸으로 저장된다(바닐라 개체 저장과 같다). 앉은 불길 단계가 쥔 구름 참조는 바닐라처럼 재기동을
        // 넘지 않는다 — 되살아난 구름은 제 수명대로 사라진다.
        return runtime.arrows().stream().filter(projectile -> projectile.alive)
                .map(ProjectileSim::persistenceSnapshot)
                .sorted(java.util.Comparator.comparingLong(
                        ProjectilePersistenceSnapshot::projectileId))
                .toList();
    }

    long projectileRevision() {
        return runtime.projectileRevision();
    }

    boolean projectileRecoveryPersistencePending() {
        return projectileRecoveryPersistencePending;
    }

    void acknowledgeProjectileRecoveryPersistence() {
        projectileRecoveryPersistencePending = false;
    }

    void restoreProjectiles(List<ProjectilePersistenceSnapshot> snapshots) {
        for (ProjectilePersistenceSnapshot snapshot : snapshots) {
            // Fishing hooks are connection-owned entities. A clean leave retires them; a process
            // crash may leave the last checkpoint row, but no disconnected cast session may be
            // resurrected without its owner and fishing state machine.
            if (ProjectileSim.Kind.valueOf(snapshot.kind()) == ProjectileSim.Kind.FISHING_BOBBER) {
                continue;
            }
            runtime.restoreProjectile(ProjectileSim.restore(snapshot));
        }
    }

    /** Silent initial state: welcome hydration must not replay projectile launch audio. */
    List<WsMessages.ProjectileSpawn> welcomeProjectileSnapshot() {
        return runtime.arrowsInChunks(rt.activeSimulationChunksForMobTick()).stream()
                .map(MobSystem::projectileSpawnMessage)
                .toList();
    }

    private double nextGaussian() {
        if (gaussianSpareReady) {
            gaussianSpareReady = false;
            return gaussianSpare;
        }
        double u1 = Math.max(Double.MIN_VALUE, rng.nextDouble());
        double u2 = rng.nextDouble();
        double radius = Math.sqrt(-2.0 * Math.log(u1));
        double angle = Math.PI * 2.0 * u2;
        gaussianSpare = radius * Math.sin(angle);
        gaussianSpareReady = true;
        return radius * Math.cos(angle);
    }

    /** Modern Projectile#getMovementToShoot uses RandomSource.triangle, not a Gaussian. */
    private double nextTriangle(double deviation) {
        return (rng.nextDouble() - rng.nextDouble()) * deviation;
    }

    /** ItemEntitySystem의 저빈도 장비 탐색 진입점. 제곱거리만 비교하고 실제 장비 규칙은 Mob이 맡는다. */
    long tryPickupEquipment(short itemType, int durability, double x, double y, double z, double range) {
        return tryPickupEquipment(itemType, durability, 0L, null, x, y, z, range);
    }

    /** [MOB-EQUIP] 성분(인챈트·성분 문자열)을 지닌 스택. 몹이 성분째 입고 교체 장비도 성분째 되돌린다. */
    long tryPickupEquipment(short itemType, int durability, long enchantments, String components,
            double x, double y, double z, double range) {
        Mob nearest = nearestPickupCandidate(
                runtime.equipmentMobsInChunks(rt.activeSimulationChunksForMobTick()),
                itemType, durability, enchantments, components, x, y, z, range);
        if (nearest == null || !nearest.tryPickupEquipment(
                itemType, durability, enchantments, components, rng)) return 0;
        Mob.EquipmentDrop replaced = nearest.takeReplacedEquipment();
        if (replaced != null) {
            rt.itemSystem().spawnDrop(replaced.itemType(), 1, replaced.durability(),
                    replaced.enchantments(), 0, 0, null, replaced.itemComponentData(), x, y, z);
        }
        runtime.refreshPersistenceSnapshot(nearest);
        return nearest.id;
    }

    /** Offers the current live item set to active Allays; each Allay core selects nearest + ID tie-break. */
    void offerAllayDroppedItems(List<ItemEntity> items) {
        if (items.isEmpty()) return;
        List<Mob> active = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        List<Allay> allays = null;
        for (Mob mob : active) {
            if (mob.type != MobType.ALLAY || mob.isDead() || mob.removed) continue;
            Allay allay = (Allay) mob;
            if (allay.heldItem() == PlayerInventory.EMPTY || allay.likedPlayerNickname() == null
                    || allay.deliveryCount() != 0) continue;
            if (allays == null) allays = new ArrayList<>();
            allays.add(allay);
        }
        if (allays == null) return;

        if ((long) allays.size() * items.size() < 64L) {
            for (Allay allay : allays) for (ItemEntity item : items) {
                if (!ItemEntitySystem.allayPickupEligible(item, allay.id)) continue;
                runtime.offerAllayDroppedItem(allay.id, item.id, item.itemType,
                        item.durability, item.count, item.x, item.y, item.z);
            }
            return;
        }

        Map<Long, List<ItemEntity>> byIdentity = new HashMap<>();
        for (ItemEntity item : items) {
            if (!ItemEntitySystem.allayPickupSearchEligible(item)) continue;
            byIdentity.computeIfAbsent(allayItemIdentity(item.itemType, item.durability),
                    ignored -> new ArrayList<>()).add(item);
        }
        for (Allay allay : allays) {
            List<ItemEntity> matching = byIdentity.get(
                    allayItemIdentity(allay.heldItem(), allay.heldItemDurability()));
            if (matching == null) continue;
            for (ItemEntity item : matching) {
                if (item.excludedAllayId == allay.id) continue;
                runtime.offerAllayDroppedItem(allay.id, item.id, item.itemType,
                        item.durability, item.count, item.x, item.y, item.z);
            }
        }
    }

    private static long allayItemIdentity(short itemType, int durability) {
        return ((long) itemType & 0xffffL) << 32 ^ (durability & 0xffff_ffffL);
    }

    static Mob nearestPickupCandidate(List<Mob> mobs, short itemType,
                                      double x, double y, double z, double range) {
        return nearestPickupCandidate(mobs, itemType, PlayerInventory.initialDurability(itemType),
                0L, null, x, y, z, range);
    }

    static Mob nearestPickupCandidate(List<Mob> mobs, short itemType, int durability,
                                      long enchantments, String components,
                                      double x, double y, double z, double range) {
        if (!pickupEquipmentItem(itemType)) return null;
        Mob nearest = null;
        double bestSq = range * range;
        for (Mob mob : mobs) {
            if (mob.isDead() || mob.removed || mob.isRidingBoat()) continue;
            if (!mob.canPickupEquipment(itemType, durability, enchantments, components)) continue;
            double dx = mob.x - x, dy = mob.y - y, dz = mob.z - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bestSq) {
                bestSq = d;
                nearest = mob;
            }
        }
        return nearest;
    }

    static boolean pickupEquipmentItem(short itemType) {
        return SulfurCubeRules.archetypeFor(Short.toUnsignedInt(itemType)) != null
                || PlayerInventory.isArmor(itemType)
                || InventoryRules.isSword(itemType)
                || InventoryRules.isTool(itemType);
    }

    /** WS 스레드가 welcome 구성 시 읽는 현재 몹 스냅샷(불변 리스트). */
    List<MobSpawnDto> welcomeSnapshot() {
        return welcomeSnapshot;
    }

    /** First-join bootstrap runs before the tick owner starts, so restored mobs can be filtered safely. */
    void refreshWelcomeSnapshotForChunks(Set<Long> chunkKeys) {
        List<Mob> active = runtime.mobsInChunks(chunkKeys);
        publishSnapshot(active);
        lastBroadcastActiveMobIds.clear();
        lastMobState.clear();
        for (Mob mob : active) {
            lastBroadcastActiveMobIds.add(mob.id);
            lastMobState.put(mob.id, MobBroadcastState.capture(mob));
        }
    }

    /**
     * Top-level steps of the mob phase. The runner demands a body for every constant, so a new
     * step cannot be added without wall-clock attribution (rule 10m).
     */
    enum MobPhaseStep {
        PRE_SETTLEMENT_PUMP,
        WORLD_TICK_BINDING,
        ACTIVATED_CHUNKS,
        POST_SETTLEMENT_PUMP
    }

    /** Diagnostics only: per-step wall clock plus the chunk work observed inside the mob phase. */
    static final class MobPhaseAttribution {
        static final long WARN_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

        private final long[] stepNanos = new long[MobPhaseStep.values().length];
        private long totalNanos;
        private long inactiveInspections;
        private long activations;

        long totalNanos() {
            return totalNanos;
        }

        long stepNanos(MobPhaseStep step) {
            return stepNanos[step.ordinal()];
        }

        long inactiveInspections() {
            return inactiveInspections;
        }

        long activations() {
            return activations;
        }

        /**
         * Inactive-chunk inspections are only observable from inside the activation guard, so the
         * guarded step reports what it saw. The terrain accessor exposes a sticky per-window flag
         * rather than a count, making this a lower bound (0 or 1 per guarded window).
         */
        void observeInactiveInspections(long observed) {
            if (observed > 0) inactiveInspections += observed;
        }

        boolean exceedsPhaseBudget() {
            return totalNanos > WARN_NANOS;
        }

        String warnLine() {
            StringBuilder steps = new StringBuilder();
            for (MobPhaseStep step : MobPhaseStep.values()) {
                if (steps.length() > 0) steps.append(',');
                steps.append(step.name()).append(':').append(millis(stepNanos(step)));
            }
            return "MOB_PHASE_ATTRIBUTION total=" + millis(totalNanos) + "ms steps=" + steps
                    + " inactiveInspections=" + inactiveInspections
                    + " activations=" + activations;
        }

        private static String millis(long nanos) {
            return String.format(Locale.ROOT, "%.1f", nanos / 1_000_000.0d);
        }
    }

    /** Chunk activations recorded by this thread; the per-tick counter avoids other worlds' ticks. */
    private static long sampleActivations() {
        TickSafetyTelemetry.Event event = TickSafetyTelemetry.Event.BLOCK_READ_ACTIVATION;
        return TickSafetyTelemetry.isTickThread()
                ? TickSafetyTelemetry.currentTickCount(event)
                : TickSafetyTelemetry.count(event);
    }

    /** Observed inside the activation guard only; see {@link MobPhaseAttribution#observeInactiveInspections}. */
    private static long sampleGuardedInactiveInspections() {
        return com.gameexpert.terrain.TerrainAccessor.inspectWithoutChunkActivation(() -> true)
                ? 0L : 1L;
    }

    /**
     * Runs every mob-phase step in declaration order and attributes its wall clock by name.
     * A step without a body fails closed instead of running unattributed.
     */
    static MobPhaseAttribution runMobPhase(MobPhaseAttribution attribution,
            Map<MobPhaseStep, Runnable> steps, LongSupplier nanoClock,
            LongSupplier activationCounter) {
        long activationsBefore = activationCounter.getAsLong();
        long phaseStart = nanoClock.getAsLong();
        try {
            for (MobPhaseStep step : MobPhaseStep.values()) {
                Runnable body = steps.get(step);
                if (body == null) {
                    throw new IllegalStateException("Mob phase step without attribution: " + step);
                }
                long start = nanoClock.getAsLong();
                try {
                    body.run();
                } finally {
                    attribution.stepNanos[step.ordinal()] += nanoClock.getAsLong() - start;
                }
            }
        } finally {
            attribution.totalNanos = nanoClock.getAsLong() - phaseStart;
            attribution.activations =
                    Math.max(0L, activationCounter.getAsLong() - activationsBefore);
        }
        return attribution;
    }

    void tick(long tickNo) {
        MobPhaseAttribution attribution = new MobPhaseAttribution();
        Map<MobPhaseStep, Runnable> steps = new EnumMap<>(MobPhaseStep.class);
        steps.put(MobPhaseStep.PRE_SETTLEMENT_PUMP, this::pumpAsyncAnimalSettlements);
        // [SULFUR] 몹 뷰의 절대 월드 틱을 환경 틱과 **같은 원천**으로 고정한다. 황린 잠복자의
        // 간헐천 연동 등장이 플레이어를 밀어올리는 분출과 같은 틱을 보아야 하기 때문이다.
        steps.put(MobPhaseStep.WORLD_TICK_BINDING, () -> world.setWorldTick(tickNo));
        // AI·스폰·투사체·유체 판정의 모든 블록 읽기는 활성 청크로 한정한다. 스포너 스캔만
        // 제한하면 다른 AI 조회가 cold 청크를 활성화해 대형 구조물 계획을 이 단계에서 동기 생성한다.
        steps.put(MobPhaseStep.ACTIVATED_CHUNKS, () ->
                com.gameexpert.terrain.TerrainAccessor.inspectValueWithoutChunkActivation(() -> {
                    try {
                        tickActivatedChunks(tickNo);
                    } finally {
                        attribution.observeInactiveInspections(sampleGuardedInactiveInspections());
                    }
                    return null;
                }));
        steps.put(MobPhaseStep.POST_SETTLEMENT_PUMP, this::pumpAsyncAnimalSettlements);
        try {
            runMobPhase(attribution, steps, System::nanoTime, MobSystem::sampleActivations);
        } finally {
            if (attribution.exceedsPhaseBudget()) log.warn("{}", attribution.warnLine());
        }
    }

    /** world owner 스레드의 청크 활성화 통지. 판정은 몹 틱 스레드에서 수행한다. */
    void onChunkActivated(int chunkX, int chunkZ) {
        runtime.invalidateVillagerPoiChunk(chunkX, chunkZ);
        // [END-CITY] 커스텀 차원도 청크 첫 채움을 한 번 판정한다: 동물 배치는 없지만 콘텐츠 소유 초기 개체
        // (엔드 도시 셜커 · 겉날개 액자, 흑요석 기둥의 엔드 수정)가 이 판정에서만 생긴다. 예전에는 여기서 곧장
        // 돌아가 Spring 엔드 차원에 수정 · 셜커 · 겉날개가 하나도 없었다(브라우저 QA 결함).
        if (!pendingExplosions.isEmpty()) pendingExplosionRetryRequested = true;
        long key = populationKey(chunkX, chunkZ);
        if (populatedChunkKeys.contains(key) || !pendingPopulationChunkKeys.add(key)) return;
        pendingPopulationChunks.add(new long[] { chunkX, chunkZ });
    }

    void onChunkEvicted(int chunkX, int chunkZ) {
        runtime.invalidateVillagerPoiChunk(chunkX, chunkZ);
        releaseStructureSiteSource(populationKey(chunkX, chunkZ));
    }

    /** Final, owner-thread structure facts; no presentation or public protocol state crosses this boundary. */
    void acceptStructureSites(int sourceChunkX, int sourceChunkZ,
            List<AcceptedStructureSite> sites) {
        Map<String, AcceptedStructureSite> delivered = new LinkedHashMap<>();
        for (AcceptedStructureSite site : sites) {
            String key = structureIdentity(site);
            AcceptedStructureSite duplicate = delivered.putIfAbsent(key, site);
            if (duplicate != null && !sameStructureFact(duplicate, site)) {
                throw new IllegalStateException("Conflicting accepted structure site " + key);
            }
            AcceptedStructureSite existing = pendingStructureOccupantSites.get(key);
            if (existing != null && !sameStructureFact(existing, site)) {
                throw new IllegalStateException("Conflicting accepted structure site " + key);
            }
        }

        long source = populationKey(sourceChunkX, sourceChunkZ);
        releaseStructureSiteSource(source);
        Set<String> sourceKeys = new HashSet<>();
        for (Map.Entry<String, AcceptedStructureSite> entry : delivered.entrySet()) {
            String key = entry.getKey();
            sourceKeys.add(key);
            // The glitch signal lane is a second, independently claimed decision on the same
            // accepted fact: it must be registered even when the resident lane is already claimed.
            if (entry.getValue().kind() == StructureSiteDescriptor.Kind.GLITCH_DUNGEON) {
                String signalKey = glitchSignalIdentity(entry.getValue());
                sourceKeys.add(signalKey);
                if (!knownStructureOccupantClaims.contains(signalKey)) {
                    pendingGlitchSignalSites.putIfAbsent(signalKey, entry.getValue());
                    structureSiteSources
                            .computeIfAbsent(signalKey, ignored -> new HashSet<>()).add(source);
                }
            }
            if (knownStructureOccupantClaims.contains(key)) continue;
            pendingStructureOccupantSites.putIfAbsent(key, entry.getValue());
            structureSiteSources.computeIfAbsent(key, ignored -> new HashSet<>()).add(source);
        }
        if (!sourceKeys.isEmpty()) structureSiteKeysBySource.put(source, sourceKeys);
    }

    private void releaseStructureSiteSource(long source) {
        Set<String> keys = structureSiteKeysBySource.remove(source);
        if (keys == null) return;
        for (String key : keys) {
            Set<Long> sources = structureSiteSources.get(key);
            if (sources == null) continue;
            sources.remove(source);
            if (!sources.isEmpty()) continue;
            structureSiteSources.remove(key);
            pendingStructureOccupantSites.remove(key);
            pendingGlitchSignalSites.remove(key);
        }
    }

    /** Evaluates at most one accepted site per authority tick. */
    private void drainStructureOccupants(long tickNo) {
        if (pendingStructureOccupantSites.isEmpty()) return;
        AcceptedStructureSite selected = null;
        String selectedKey = null;
        for (Map.Entry<String, AcceptedStructureSite> entry
                : pendingStructureOccupantSites.entrySet()) {
            AcceptedStructureSite site = entry.getValue();
            int anchorChunkX = Math.floorDiv(site.anchorX(), Blocks.CHUNK_X);
            int anchorChunkZ = Math.floorDiv(site.anchorZ(), Blocks.CHUNK_Z);
            if (!world.isChunkActive(anchorChunkX, anchorChunkZ)) continue;
            selected = site;
            selectedKey = entry.getKey();
            break;
        }
        if (selected == null || selectedKey == null) return;

        // Village cats use their own vanilla cadence rather than the ordinary natural-spawn cycle.
        // Keep the accepted site pending until that exact attempt boundary arrives.
        if (selected.kind() == StructureSiteDescriptor.Kind.VILLAGE
                && !CatVillageOriginRules.attemptDue(tickNo)) {
            return;
        }


        List<MobType> roster = new ArrayList<>(structureOccupantRoster(rt.seed(), selected));
        if (selected.kind() == StructureSiteDescriptor.Kind.VILLAGE) {
            int occupiedBeds = countVillageBeds(selected.bounds());
            int nearbyCats = countNearbyCats(
                    selected.anchorX(), selected.bounds().minY(), selected.anchorZ());
            CatVillageOriginRules.SpawnPlan cat = CatVillageOriginRules.plan(true, occupiedBeds,
                    nearbyCats, true, selected.anchorX(), selected.bounds().minY(),
                    selected.anchorZ());
            if (cat != null) roster.add(cat.mobType());
        }
        List<double[]> positions = new ArrayList<>(roster.size());
        List<MobType> placedTypes = new ArrayList<>(roster.size());
        List<Mob> existing = runtime.mobsInChunks(Set.of(populationKey(
                Math.floorDiv(selected.anchorX(), Blocks.CHUNK_X),
                Math.floorDiv(selected.anchorZ(), Blocks.CHUNK_Z))));
        List<Integer> placedRosterIndices = new ArrayList<>(roster.size());
        for (int index = 0; index < roster.size(); index++) {
            double[] position = findStructureOccupantPosition(
                    selected, roster.get(index), index, existing, positions, placedTypes);
            if (position == null) continue;
            if (position.length == 0) return; // a required resident cell became cold; defer
            positions.add(position);
            placedTypes.add(roster.get(index));
            placedRosterIndices.add(index);
        }

        List<Mob> placedHandlers = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            double[] position = positions.get(index);
            Mob placed = runtime.addMob(
                    placedTypes.get(index), position[0], position[1], position[2], true);
            placedHandlers.add(placed);
            // 이 주민이 놓인 칸이 곧 그 마을의 앵커다. 바닐라라면 이 구역에 점유된 마을 POI 가
            // 있어 PoiManager 마을 인덱스의 원천이 되는 자리이며, POI 기억이 하나도 없는 주민의
            // 울타리 원점이 된다({@code VillagerBrainRules#wanderedOutOfHomeVillage}).
            if (placed instanceof com.gameexpert.engine.mob.Villager villager) {
                villager.rememberHomeVillage((int) Math.floor(position[0]),
                        (int) Math.floor(position[1]), (int) Math.floor(position[2]));
            }
            // 바닐라: 자연 생성 주민의 10% 는 NITWIT 으로 태어나 영원히 취업하지 않는다.
            if (placed != null && placedTypes.get(index) == MobType.VILLAGER
                    && VillagerJobSitePolicy.naturalSpawnNitwit(
                            rt.seed(), selected.siteKey(), placedRosterIndices.get(index))) {
                villagerJobClaims.markNitwit(placed.id);
            }
        }
        int companionCount = 0;
        if (selected.kind() != StructureSiteDescriptor.Kind.WOODLAND_MANSION) {
            long contextIdentity = selected.siteKey();
            for (Mob handler : placedHandlers) {
                if (runtime.maybeAddIllagerCompanion(world, handler,
                        com.gameexpert.engine.mob.IllagerCompanionPolicy.Context.ACCEPTED_SITE,
                        contextIdentity) != null) {
                    companionCount++;
                }
            }
        }
        StructureOccupantClaimSnapshot claim = new StructureOccupantClaimSnapshot(
                selected.kind().name(), selected.cellX(), selected.cellZ(), selected.siteKey(),
                STRUCTURE_OCCUPANT_POLICY_VERSION, positions.size() + companionCount);
        knownStructureOccupantClaims.add(selectedKey);
        unpersistedStructureOccupantClaims.put(selectedKey, claim);
        pendingStructureOccupantSites.remove(selectedKey);
        structureSiteSources.remove(selectedKey);
    }

    private int countVillageBeds(StructureAabb bounds) {
        int count = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    short block = world.getBlock(x, y, z);
                    if (block >= 0 && Blocks.isBed(block & 0xffff)
                            && (rt.blockStates().get(x, y, z, block & 0xffff)
                                    & BuildingBlockRules.BED_HEAD) != 0) count++;
                }
            }
        }
        return count;
    }

    private int countNearbyCats(int x, int y, int z) {
        int count = 0;
        for (Mob mob : runtime.mobs()) {
            if (withinCatVillageCapBox(mob, x, y, z)) count++;
        }
        return count;
    }

    static boolean withinCatVillageCapBox(Mob mob, int x, int y, int z) {
        return mob != null && mob.type == MobType.CAT && !mob.isDead() && !mob.removed
                && Math.abs(mob.x - x) <= CatVillageOriginRules.LOCAL_HORIZONTAL_RADIUS
                && Math.abs(mob.y - y) <= CatVillageOriginRules.LOCAL_VERTICAL_RADIUS
                && Math.abs(mob.z - z) <= CatVillageOriginRules.LOCAL_HORIZONTAL_RADIUS;
    }

    /**
     * Glitch signal endermen. Evaluates at most one accepted glitch center per authority tick and
     * commits the whole decision — spawns plus the durable claim — atomically for that center, so
     * reconnect, chunk eviction and redelivery can never produce a second signal.
     */
    private void drainGlitchSignals() {
        if (pendingGlitchSignalSites.isEmpty()) return;
        AcceptedStructureSite selected = null;
        String selectedKey = null;
        for (Map.Entry<String, AcceptedStructureSite> entry : pendingGlitchSignalSites.entrySet()) {
            AcceptedStructureSite site = entry.getValue();
            if (!world.isChunkActive(Math.floorDiv(site.anchorX(), Blocks.CHUNK_X),
                    Math.floorDiv(site.anchorZ(), Blocks.CHUNK_Z))) continue;
            selected = site;
            selectedKey = entry.getKey();
            break;
        }
        if (selected == null || selectedKey == null) return;

        StructureAabb bounds = selected.bounds();
        int centerX = Math.floorDiv(bounds.minX() + bounds.maxX(), 2);
        int centerY = Math.floorDiv(bounds.minY() + bounds.maxY(), 2);
        int centerZ = Math.floorDiv(bounds.minZ() + bounds.maxZ(), 2);
        int count = GlitchSignalPolicy.signalCount(rt.seed(), selected.siteKey());
        List<double[]> positions = new ArrayList<>(count);
        boolean cold = false;
        for (int index = 0; index < count && !cold; index++) {
            double[] position = findGlitchSignalPosition(
                    selected.siteKey(), centerX, centerY, centerZ, index, positions);
            if (position == null) continue;
            if (position.length == 0) {
                cold = true;
                break;
            }
            positions.add(position);
        }
        if (cold) {
            // A required ring cell is not resident yet. Rotate this center to the back so other
            // pending glitch centers cannot starve, and retry once its ring chunks load.
            pendingGlitchSignalSites.remove(selectedKey);
            pendingGlitchSignalSites.put(selectedKey, selected);
            return;
        }

        for (double[] position : positions) {
            runtime.addMob(MobType.ENDERMAN, position[0], position[1], position[2], true);
        }
        StructureOccupantClaimSnapshot claim = new StructureOccupantClaimSnapshot(
                GlitchSignalPolicy.CLAIM_KIND, selected.cellX(), selected.cellZ(),
                selected.siteKey(), GlitchSignalPolicy.POLICY_VERSION, positions.size());
        knownStructureOccupantClaims.add(selectedKey);
        unpersistedStructureOccupantClaims.put(selectedKey, claim);
        pendingGlitchSignalSites.remove(selectedKey);
        structureSiteSources.remove(selectedKey);
    }

    /** null = no warm cell for this slot, empty = a candidate ring chunk is cold, else position. */
    private double[] findGlitchSignalPosition(long siteKey, int centerX, int centerY, int centerZ,
            int index, List<double[]> planned) {
        int topY = Math.min(centerY + GLITCH_SIGNAL_VERTICAL_REACH,
                Blocks.MAX_Y - (int) Math.ceil(MobType.ENDERMAN.height()));
        int bottomY = Math.max(centerY - GLITCH_SIGNAL_VERTICAL_REACH, Blocks.MIN_Y + 1);
        List<MobType> plannedTypes =
                java.util.Collections.nCopies(planned.size(), MobType.ENDERMAN);
        boolean sawCold = false;
        for (int candidate = 0; candidate < GlitchSignalPolicy.CANDIDATES; candidate++) {
            int[] offset = GlitchSignalPolicy.candidateOffset(siteKey, index, candidate);
            if (offset == null) continue;
            int x = centerX + offset[0];
            int z = centerZ + offset[1];
            int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
            int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
            // Cheap cold rejection first: a non-resident ring chunk must not cost a column scan.
            if (!world.isChunkActive(chunkX, chunkZ)) {
                sawCold = true;
                continue;
            }
            List<Mob> existing = runtime.mobsInChunks(Set.of(populationKey(chunkX, chunkZ)));
            for (int y = topY; y > bottomY; y--) {
                int validity = structureOccupantCellValidity(
                        x, y, z, MobType.ENDERMAN, existing, planned, plannedTypes);
                if (validity < 0) {
                    sawCold = true;
                    break;
                }
                if (validity > 0) return new double[] { x + 0.5, y, z + 0.5 };
            }
        }
        return sawCold ? new double[0] : null;
    }

    private static String glitchSignalIdentity(AcceptedStructureSite site) {
        return GlitchSignalPolicy.CLAIM_KIND + ':' + site.cellX() + ':' + site.cellZ();
    }

    private double[] findStructureOccupantPosition(AcceptedStructureSite site, MobType type,
            int ordinal, List<Mob> existing, List<double[]> planned,
            List<MobType> plannedTypes) {
        if (site.kind() == StructureSiteDescriptor.Kind.OCEAN_MONUMENT) {
            return findMonumentGuardianPosition(site, type, ordinal);
        }
        if (site.kind() == StructureSiteDescriptor.Kind.OCEAN_RUINS
                && type == MobType.DROWNED) {
            return findOceanRuinDrownedPosition(site, ordinal, existing, planned, plannedTypes);
        }
        StructureAabb bounds = site.bounds();
        double half = type.width() * 0.5;
        int anchorChunkX = Math.floorDiv(site.anchorX(), Blocks.CHUNK_X);
        int anchorChunkZ = Math.floorDiv(site.anchorZ(), Blocks.CHUNK_Z);
        int minX = Math.max((int) Math.ceil(bounds.minX() + half), anchorChunkX * Blocks.CHUNK_X);
        int maxX = Math.min((int) Math.floor(bounds.maxX() - half),
                anchorChunkX * Blocks.CHUNK_X + Blocks.CHUNK_X - 1);
        int minZ = Math.max((int) Math.ceil(bounds.minZ() + half), anchorChunkZ * Blocks.CHUNK_X);
        int maxZ = Math.min((int) Math.floor(bounds.maxZ() - half),
                anchorChunkZ * Blocks.CHUNK_X + Blocks.CHUNK_X - 1);
        if (minX > maxX || minZ > maxZ) return null;
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int candidates = Math.min(24, spanX * spanZ);
        int seed = mixStructurePosition((int) site.siteKey()
                ^ (site.kind().ordinal() + 1) * 0x632be5ab
                ^ site.cellX() * 0x85157af5 ^ site.cellZ() * 0x58f38ded)
                + (ordinal + 1) * 0x9e3779b1;
        boolean mansionInterior = site.kind() == StructureSiteDescriptor.Kind.WOODLAND_MANSION;
        boolean cold = false;
        for (int candidate = 0; candidate < candidates; candidate++) {
            int mixed = mixStructurePosition(seed + candidate * 0x6d2b79f5);
            int x = minX + Integer.remainderUnsigned(mixed, spanX);
            int z = minZ + Integer.remainderUnsigned(mixed >>> 11, spanZ);
            int startY = mansionInterior ? Math.max(bounds.minY() + 1, Blocks.MIN_Y)
                    : Math.min(bounds.maxY(), Blocks.MAX_Y - 1);
            int endY = mansionInterior ? Math.min(bounds.maxY(), Blocks.MAX_Y - 1)
                    : bounds.minY() + 1;
            int step = mansionInterior ? 1 : -1;
            for (int y = startY; mansionInterior ? y <= endY : y >= endY; y += step) {
                int validity = structureOccupantCellValidity(x, y, z, type, existing,
                        planned, plannedTypes, mansionInterior);
                if (validity < 0) {
                    cold = true;
                    break;
                }
                if (validity > 0) return new double[] { x + 0.5, y, z + 0.5 };
            }
        }
        return cold ? new double[0] : null;
    }

    /**
     * [MONUMENT] 신전 거주자는 물속에 산다. 일반 경로는 solid 지지와 물 없는 칸을 요구하므로
     * 쓸 수 없다.
     *
     * <p>엘더 3기는 바닐라와 같은 고정 좌표(날개 2 + 꼭대기 1)이고, 일반 가디언은 신전 경계
     * 상자 안에서 {@link GuardianSpawnRules#spawnable} 을 만족하는 물칸만 고른다 — 바닐라
     * {@code spawn_overrides.monster} 의 {@code bounding_box: "full"} 과 같은 범위다.
     */
    private double[] findMonumentGuardianPosition(AcceptedStructureSite site, MobType type,
            int ordinal) {
        StructureAabb bounds = site.bounds();
        if (type == MobType.ELDER_GUARDIAN) {
            List<int[]> elders = List.of(
                    new int[] {bounds.minX() + 12, bounds.minY() + 2, bounds.minZ() + 17},
                    new int[] {bounds.maxX() - 12, bounds.minY() + 5, bounds.maxZ() - 14},
                    new int[] {(bounds.minX() + bounds.maxX()) / 2, bounds.minY() + 14,
                            (bounds.minZ() + bounds.maxZ()) / 2});
            if (ordinal >= elders.size()) return null;
            int[] cell = elders.get(ordinal);
            short block = world.getBlock(cell[0], cell[1], cell[2]);
            if (block < 0) return new double[0];
            return new double[] { cell[0] + 0.5, cell[1], cell[2] + 0.5 };
        }
        int spanX = bounds.maxX() - bounds.minX() + 1;
        int spanZ = bounds.maxZ() - bounds.minZ() + 1;
        int spanY = bounds.maxY() - bounds.minY() + 1;
        int seed = mixStructurePosition((int) site.siteKey()
                ^ (site.kind().ordinal() + 1) * 0x632be5ab) + (ordinal + 1) * 0x9e3779b1;
        boolean cold = false;
        for (int candidate = 0; candidate < 64; candidate++) {
            int mixed = mixStructurePosition(seed + candidate * 0x6d2b79f5);
            int x = bounds.minX() + Integer.remainderUnsigned(mixed, spanX);
            int z = bounds.minZ() + Integer.remainderUnsigned(mixed >>> 11, spanZ);
            int y = bounds.minY() + Integer.remainderUnsigned(mixed >>> 21, spanY);
            short block = world.getBlock(x, y, z);
            if (block < 0) {
                cold = true;
                continue;
            }
            if (GuardianSpawnRules.spawnable(world, bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ(), x, y, z)) {
                return new double[] { x + 0.5, y, z + 0.5 };
            }
        }
        return cold ? new double[0] : null;
    }

    /**
     * [OCEAN-RUINS] Drowned residents are generated inside the submerged ruin volume. The generic
     * resident search deliberately rejects water, so use a bounded deterministic water-AABB scan
     * while retaining the same cold-chunk retry and overlap rules as every other occupant lane.
     */
    private double[] findOceanRuinDrownedPosition(AcceptedStructureSite site, int ordinal,
            List<Mob> existing, List<double[]> planned, List<MobType> plannedTypes) {
        StructureAabb bounds = site.bounds();
        double half = MobType.DROWNED.width() * 0.5;
        int anchorChunkX = Math.floorDiv(site.anchorX(), Blocks.CHUNK_X);
        int anchorChunkZ = Math.floorDiv(site.anchorZ(), Blocks.CHUNK_Z);
        int minX = Math.max((int) Math.ceil(bounds.minX() + half), anchorChunkX * Blocks.CHUNK_X);
        int maxX = Math.min((int) Math.floor(bounds.maxX() - half),
                anchorChunkX * Blocks.CHUNK_X + Blocks.CHUNK_X - 1);
        int minZ = Math.max((int) Math.ceil(bounds.minZ() + half), anchorChunkZ * Blocks.CHUNK_Z);
        int maxZ = Math.min((int) Math.floor(bounds.maxZ() - half),
                anchorChunkZ * Blocks.CHUNK_Z + Blocks.CHUNK_Z - 1);
        if (minX > maxX || minZ > maxZ) return null;
        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;
        int spanY = Math.max(1, bounds.maxY() - bounds.minY() + 1);
        int seed = mixStructurePosition((int) site.siteKey()
                ^ (site.kind().ordinal() + 1) * 0x632be5ab)
                + (ordinal + 1) * 0x9e3779b1;
        boolean cold = false;
        for (int candidate = 0; candidate < 64; candidate++) {
            int mixed = mixStructurePosition(seed + candidate * 0x6d2b79f5);
            int x = minX + Integer.remainderUnsigned(mixed, spanX);
            int z = minZ + Integer.remainderUnsigned(mixed >>> 11, spanZ);
            int y = bounds.minY() + Integer.remainderUnsigned(mixed >>> 21, spanY);
            int maxBodyY = (int) Math.ceil(y + MobType.DROWNED.height()) - 1;
            if (maxBodyY > Blocks.MAX_Y) continue;
            boolean water = true;
            for (int by = y; by <= maxBodyY; by++) {
                short cell = world.getBlock(x, by, z);
                if (cell < 0) {
                    cold = true;
                    water = false;
                    break;
                }
                if (!Fluids.isWaterMedium(cell & 0xffff)) {
                    water = false;
                    break;
                }
            }
            if (!water) continue;
            double centerX = x + 0.5;
            double centerZ = z + 0.5;
            boolean occupied = false;
            for (Mob mob : existing) {
                if (overlapsOccupant(centerX, y, centerZ, MobType.DROWNED,
                        mob.x, mob.y, mob.z, mob.type)) {
                    occupied = true;
                    break;
                }
            }
            if (occupied) continue;
            for (int index = 0; index < planned.size(); index++) {
                double[] other = planned.get(index);
                if (overlapsOccupant(centerX, y, centerZ, MobType.DROWNED,
                        other[0], other[1], other[2], plannedTypes.get(index))) {
                    occupied = true;
                    break;
                }
            }
            if (!occupied) return new double[] { centerX, y, centerZ };
        }
        return cold ? new double[0] : null;
    }

    /** -1 cold, 0 invalid, 1 valid. */
    private int structureOccupantCellValidity(int x, int y, int z, MobType type,
            List<Mob> existing, List<double[]> planned, List<MobType> plannedTypes) {
        return structureOccupantCellValidity(
                x, y, z, type, existing, planned, plannedTypes, false);
    }

    /** -1 cold, 0 invalid, 1 valid; mansions additionally require a nearby solid ceiling. */
    private int structureOccupantCellValidity(int x, int y, int z, MobType type,
            List<Mob> existing, List<double[]> planned, List<MobType> plannedTypes,
            boolean mansionInterior) {
        double half = type.width() * 0.5;
        int minX = (int) Math.floor(x + 0.5 - half);
        int maxX = (int) Math.floor(x + 0.5 + half - 1e-9);
        int minZ = (int) Math.floor(z + 0.5 - half);
        int maxZ = (int) Math.floor(z + 0.5 + half - 1e-9);
        int maxY = (int) Math.ceil(y + type.height()) - 1;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                short support = world.getBlock(bx, y - 1, bz);
                if (support < 0) return -1;
                if (!world.isSolid(support)) return 0;
                for (int by = y; by <= maxY; by++) {
                    short cell = world.getBlock(bx, by, bz);
                    if (cell < 0) return -1;
                    int block = cell & 0xffff;
                    if (BuildingBlockRules.blocksMotion(
                            block, world.blockState(bx, by, bz, block))
                            || Fluids.isWaterMedium(block) || Fluids.isLava(block)) return 0;
                }
                if (mansionInterior) {
                    boolean ceiling = false;
                    for (int by = maxY + 1;
                            by <= Math.min(Blocks.MAX_Y - 1,
                                    y + 5); by++) {
                        short cell = world.getBlock(bx, by, bz);
                        if (cell < 0) return -1;
                        int block = cell & 0xffff;
                        if (BuildingBlockRules.blocksMotion(
                                block, world.blockState(bx, by, bz, block))) {
                            ceiling = true;
                            break;
                        }
                    }
                    if (!ceiling) return 0;
                }
            }
        }
        double centerX = x + 0.5;
        double centerZ = z + 0.5;
        for (Mob mob : existing) {
            if (overlapsOccupant(centerX, y, centerZ, type, mob.x, mob.y, mob.z, mob.type)) {
                return 0;
            }
        }
        for (int index = 0; index < planned.size(); index++) {
            double[] other = planned.get(index);
            if (overlapsOccupant(centerX, y, centerZ, type,
                    other[0], other[1], other[2], plannedTypes.get(index))) return 0;
        }
        return 1;
    }

    private static boolean overlapsOccupant(double x, double y, double z, MobType type,
            double otherX, double otherY, double otherZ, MobType otherType) {
        return Math.abs(otherX - x) < (otherType.width() + type.width()) * 0.5
                && Math.abs(otherZ - z) < (otherType.width() + type.width()) * 0.5
                && y < otherY + otherType.height() && y + type.height() > otherY;
    }

    /**
     * 바닐라 {@code btree21wd} 사막 바이옴의 raw ID. canonical 마을 바이옴 관문이 통과시킨
     * 바이옴 중 낙타가 붙는 유일한 바이옴이다.
     */
    static final int DESERT_NOISE_BIOME = 2;

    /**
     * [MOUNT] 사막 마을당 낙타 정확히 1마리.
     *
     * <p>근거는 바닐라 1.21.4 다: {@code data/minecraft/worldgen/biome/desert.json} 의
     * {@code spawners.creature} 에 낙타가 없어 <b>자연 스폰이 존재하지 않고</b>, 낙타가 생기는
     * 유일한 경로는 사막 마을 구조물 생성이다({@code village_desert} 의
     * {@code minecraft:camel} 스폰 오버라이드). 또 위키가 못박듯 <b>버려진(좀비) 마을에는
     * 생기지 않는다</b> — 이 저장소에는 버려진 마을 종류가 없으므로 채택된 사막 마을은 전부
     * 비버려진 마을이고, 따라서 관문은 "앵커 바이옴이 사막인가" 하나로 닫힌다.
     *
     * <p>명단 <b>맨 끝에</b> 덧붙이고 난수 lane 을 한 번도 굴리지 않는다. 그래야 이미 채택된
     * 마을의 주민·골렘 구성이 한 마리도 바뀌지 않는다(철 골렘 도입 때와 같은 계약).
     * 바이옴을 모르는 경량 뷰({@code UNKNOWN_NOISE_BIOME})는 fail-closed 로 아무것도 더하지 않는다.
     */
    private static void addDesertVillageCamel(List<MobType> roster, AcceptedStructureSite site) {
        if (site.anchorBiome() == DESERT_NOISE_BIOME) roster.add(MobType.CAMEL);
    }

    static List<MobType> structureOccupantRoster(int worldSeed,
            AcceptedStructureSite site) {
        int state = mixStructurePosition(worldSeed ^ (int) site.siteKey()
                ^ (site.kind().ordinal() + 1) * 0x9e3779b1);
        int[] lane = { state };
        java.util.function.IntUnaryOperator next = bound -> {
            lane[0] = mixStructurePosition(lane[0] + 0x6d2b79f5);
            return Integer.remainderUnsigned(lane[0], bound);
        };
        if (site.kind().ordinal() == 10) {
            // 마을 사이트당 철 골렘 정확히 1마리(MC-REFERENCE §15). 이 명단은 마을 생성기가
            // 실제로 블록을 놓아 사이트가 채택된 뒤에만 평가되므로, 해시 관문(E lane)만 통과한
            // 바다 한복판 셀에는 골렘이 생기지 않는다. 주민 수 난수열은 예전 그대로 두어
            // 채택된 마을의 주민 구성은 바뀌지 않는다.
            if (next.applyAsInt(4) == 0) {
                List<MobType> golemOnly = new ArrayList<>(2);
                golemOnly.add(MobType.IRON_GOLEM);
                addDesertVillageCamel(golemOnly, site);
                return golemOnly;
            }
            int villagers = 2 + next.applyAsInt(4);
            List<MobType> result = new ArrayList<>(villagers + 2);
            for (int index = 0; index < villagers; index++) result.add(MobType.VILLAGER);
            result.add(MobType.IRON_GOLEM);
            addDesertVillageCamel(result, site);
            return result;
        }
        // [MONUMENT] 해저 신전: 엘더 3기(고정 좌표) + 바닐라 spawn_overrides 의 가디언 무리 2–4.
        if (site.kind() == StructureSiteDescriptor.Kind.OCEAN_MONUMENT) {
            return OceanMonumentOccupants.roster(
                    worldSeed, site.siteKey());
        }
        // [OCEAN-RUINS] Vanilla 1.21.4 places 1..4 persistent Drowned with every accepted
        // ocean-ruin site. Reuse the existing site-local deterministic lane so activation order,
        // unload, and redelivery cannot change the roster; standalone consumes the same draw.
        if (site.kind() == StructureSiteDescriptor.Kind.OCEAN_RUINS) {
            int drowned = 1 + next.applyAsInt(4);
            return java.util.Collections.nCopies(drowned, MobType.DROWNED);
        }
        if (site.kind().ordinal() == 11) {
            List<MobType> additions = List.of(MobType.VINDICATOR, MobType.EVOKER,
                    MobType.ILLUSIONER, MobType.RAVAGER, MobType.STANDARD_BEARER,
                    MobType.WEB_TRAPPER, MobType.BREACHER, MobType.DEMOLISHER, MobType.BUILDER);
            List<MobType> result = new ArrayList<>(List.of(MobType.PILLAGER, MobType.PILLAGER));
            result.add(additions.get(next.applyAsInt(additions.size())));
            if (next.applyAsInt(5) == 0) {
                result.add(additions.get(next.applyAsInt(additions.size())));
            }
            return result;
        }
        if (site.kind() == StructureSiteDescriptor.Kind.WOODLAND_MANSION) {
            int vindicators = 2 + next.applyAsInt(3);
            int evokers = 1 + next.applyAsInt(2);
            int allays = next.applyAsInt(4);
            List<MobType> result = new ArrayList<>(vindicators + evokers + allays);
            for (int index = 0; index < vindicators; index++) result.add(MobType.VINDICATOR);
            for (int index = 0; index < evokers; index++) result.add(MobType.EVOKER);
            for (int index = 0; index < allays; index++) result.add(MobType.ALLAY);
            return result;
        }
        int roll = next.applyAsInt(1_000);
        int kind = site.kind().ordinal();
        boolean pigSite = kind == 2 || kind == 3 || kind == 4 || kind == 5 || kind == 6
                || kind == 8 || kind == 9 || kind == 14 || kind == 17 || kind >= 19;
        if (pigSite && roll < 32) {
            return List.of(List.of(MobType.PIGMAN), List.of(MobType.ZOMBIE_PIGMAN),
                    List.of(MobType.ZOMBIFIED_PIGLIN), List.of(MobType.PIGLIN))
                    .get(next.applyAsInt(4));
        }
        if (roll >= 32 && roll < 44) {
            List<MobType> rare = List.of(MobType.VINDICATOR, MobType.EVOKER,
                    MobType.ILLUSIONER, MobType.STANDARD_BEARER, MobType.WEB_TRAPPER,
                    MobType.BREACHER, MobType.DEMOLISHER, MobType.BUILDER);
            return List.of(rare.get(next.applyAsInt(rare.size())));
        }
        return List.of();
    }

    private static int mixStructurePosition(int value) {
        int mixed = value;
        mixed = (mixed ^ mixed >>> 16) * 0x7feb352d;
        mixed = (mixed ^ mixed >>> 15) * 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }

    private static String structureIdentity(AcceptedStructureSite site) {
        return site.kind().name() + ':' + site.cellX() + ':' + site.cellZ();
    }

    private static boolean sameStructureFact(AcceptedStructureSite left,
            AcceptedStructureSite right) {
        StructureAabb a = left.bounds();
        StructureAabb b = right.bounds();
        return left.kind() == right.kind() && left.cellX() == right.cellX()
                && left.cellZ() == right.cellZ() && left.siteKey() == right.siteKey()
                && left.anchorX() == right.anchorX() && left.anchorZ() == right.anchorZ()
                && a.minX() == b.minX() && a.minY() == b.minY() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxY() == b.maxY() && a.maxZ() == b.maxZ();
    }

    private static long populationKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    /** Only already activated requested chunks participate; outer queued population stays queued. */
    void preparePopulationForPublication(Set<Long> chunkKeys) {
        // [END-CITY] 커스텀 차원의 첫 채움은 동물 행이 없고 초기 개체는 drainChunkPopulation 만 만든다.
        if (rt.customDimension()) return;
        for (long key : chunkKeys.stream().sorted().toList()) {
            int x = (int) (key >> 32), z = (int) key;
            if (populatedChunkKeys.contains(key) || publicationPopulationRows.containsKey(key)
                    || !pendingPopulationChunkKeys.contains(key)
                    || !rt.accessor().isChunkActivated(x, z) || !rt.accessor().isChunkResident(x, z)) continue;
            publicationPopulationRows.put(key, runtime.prepareChunkPopulationRows(world, x, z,
                    new JavaMobRandom(MobSpawner.decorationSeed(rt.seed(), x * 16, z * 16))));
        }
    }

    boolean populationPublicationPending(Set<Long> chunkKeys) {
        for (long key : chunkKeys) {
            if (publicationPopulationRows.containsKey(key) || unpersistedPopulationChunkKeys.contains(key)
                    || pendingPopulationChunkKeys.contains(key)) return true;
        }
        return false;
    }

    /**
     * 활성화된 청크의 생성 시 동물 배치를 틱 스레드에서 판정한다. 좌표 결정론 RNG로 바닐라의
     * 바이옴별 생성 확률을 굴리고, 실패해도 표식을 남겨 청크당 1회만 굴린다.
     */
    private void drainChunkPopulation() {
        for (int i = 0; i < POPULATION_CHUNKS_PER_TICK; i++) {
            long[] chunk = pendingPopulationChunks.poll();
            if (chunk == null) break;
            int chunkX = (int) chunk[0];
            int chunkZ = (int) chunk[1];
            long key = populationKey(chunkX, chunkZ);
            if (publicationPopulationRows.containsKey(key)) continue;
            if (populatedChunkKeys.contains(key)) {
                pendingPopulationChunkKeys.remove(key);
                continue;
            }
            // 활성화 통지 후 처리 전에 축출된 청크는 판정 완료로 표시하지 않는다. 예약을 해제해
            // 다음 실제 활성화 통지가 다시 큐잉하게 하며, 비활성 엔트리를 매 틱 회전시키지 않는다.
            if (!rt.accessor().isChunkActivated(chunkX, chunkZ)
                    || !rt.accessor().isChunkResident(chunkX, chunkZ)) {
                pendingPopulationChunkKeys.remove(key);
                continue;
            }
            pendingPopulationChunkKeys.remove(key);
            if (!populatedChunkKeys.add(key)) continue;
            MobRandom populationRng = new JavaMobRandom(MobSpawner.decorationSeed(
                    rt.seed(), chunkX * 16, chunkZ * 16));
            List<SpawnRequest> herd = rt.customDimension() ? List.of()
                    : runtime.planChunkPopulation(world, chunkX, chunkZ, populationRng);
            if (!herd.isEmpty()) {
                runtime.enqueueExternalSpawns(herd);
            }
            // [END-CITY] 콘텐츠 소유 초기 개체도 이 청크의 첫 채움에서 한 번만 만든다(판정 완료가 영속한다).
            for (var initial : rt.dimensionInitialMobs(chunkX, chunkZ)) spawnInitialDimensionMob(initial);
            unpersistedPopulationChunkKeys.add(key);
        }
    }

    /**
     * [END-CITY] 바닐라 {@code EndCityPiece.handleDataMarker}: Sentry 는 표지 칸 중심 발밑의 셜커(부착은 셜커 자신이
     * 판정한다), Elytra 는 그 칸에 걸린 겉날개 액자(방향 = 조각 회전의 SOUTH). 둘 다 영속 개체다.
     */
    private void spawnInitialDimensionMob(com.gameexpert.world.dimension.DimensionChunkProvider.InitialMob initial) {
        switch (initial.type()) {
            case "hanging_maw" -> {
                for (PlayerSnapshot player : playerSnapshots())
                    if (player.alive() && Math.sqrt(Math.pow(player.x()-initial.x(),2)
                        +Math.pow(player.y()-initial.y(),2)+Math.pow(player.z()-initial.z(),2))<24) return;
                var population=new FleshNetherRules.Population(new BlockPos((int)Math.floor(initial.x()),0,(int)Math.floor(initial.z())));
                for(Mob mob:runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()))
                    if(!mob.isDead()) population.include(mob.type.name(),mob.x,mob.z);
                if(population.counts().colonyTotal()>=12 || population.counts().residentGlobal()>=48) return;
                var maw=(com.gameexpert.engine.mob.HangingMaw)runtime.addMob(MobType.HANGING_MAW,initial.x(),initial.y(),initial.z(),true);
                maw.restoreAttachment(initial.direction(),0);
                runtime.refreshPersistenceSnapshot(maw);
            }
            case "shulker" -> {
                Mob shulker = runtime.addMob(MobType.SHULKER, initial.x(), initial.y(), initial.z(), true);
                runtime.refreshPersistenceSnapshot(shulker);
            }
            // [DRAGON] EndSpikeFeature.placeSpike 의 수정(받침 보임, 광선·무적 없음).
            case "end_crystal" -> {
                Mob crystal = runtime.addMobWithVariant(MobType.END_CRYSTAL, initial.x(), initial.y(), initial.z(), true,
                        com.gameexpert.engine.mob.EndCrystal.SHOW_BOTTOM);
                runtime.refreshPersistenceSnapshot(crystal);
            }
            case "item_frame" -> {
                int bx = (int) Math.floor(initial.x());
                int by = (int) Math.floor(initial.y());
                int bz = (int) Math.floor(initial.z());
                Mob placed = runtime.addMob(MobType.ITEM_FRAME, bx + 0.5, by + 0.5, bz + 0.5, true);
                com.gameexpert.engine.mob.ItemFrame frame = (com.gameexpert.engine.mob.ItemFrame) placed;
                frame.hang(bx, by, bz, initial.direction());
                short item = (short) initial.heldItem();
                if (item != PlayerInventory.EMPTY) {
                    frame.insert(item, PlayerInventory.isDurable(item) ? PlayerInventory.initialDurability(item) : 0,
                            0L, null);
                }
                runtime.refreshPersistenceSnapshot(frame);
            }
            default -> throw new IllegalArgumentException("unknown dimension initial mob " + initial.type());
        }
    }

    private void tickActivatedChunks(long tickNo) {
        // 보트 충돌 탑승/좌석 동기화는 AI·디스폰보다 먼저 확정해야 같은 틱의 면제가 살아난다.
        rt.boatSystem().prepareMobTick();
        sweepPigRiders();
        // [NAUTILUS-MOUNT] Breath of the Nautilus 는 좌석 정리 **바로 뒤**에 선다 — 이번 틱에
        // 내려온 기수에게 효과를 한 틱 더 주지 않기 위해서다.
        refreshNautilusBreath();
        advanceRaidOmens(runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()), tickNo);
        // [TRIAL] 시련 설비 좌표는 스포너 인덱스가 직전 틱에 확정한 목록이다. 두 번째 전역
        // 탐색을 만들지 않으려고 같은 상환 예산을 공유하므로 한 틱 늦을 수 있고, 활성화
        // 반경 14 는 인덱스가 보는 ±1 청크 안에 들어와 그 지연이 판정을 바꾸지 않는다.
        runtime.trialSpawners().observeFixtures(
                world, spawnerIndex.trialSpawnerBlocks(), spawnerIndex.vaultBlocks());
        world.setPlayers(playerSnapshots());
        // [CAT] 크리퍼·팬텀이 공유하는 전역 고양이 좌표는 같은 자리에서 한 틱에 한 번만 교체한다.
        int catPositionCoordinateCount = refreshCatPositions();
        world.setCatPositions(catPositionScratch, catPositionCoordinateCount);
        processPendingExplosions();
        // 낙뢰는 몹 AI 앞에서 확정한다. 변신·사망이 아래 beforeTick/activeAfterTick diff 에 잡혀
        // 같은 틱의 mobSpawn/mobDespawn 으로 나간다.
        applyLightningStrikes(tickNo);
        summonHurtSilverfish();
        // [TRIAL-GAP] 벌레 먹음 피격 굴림과 플레이어 사망 순간 효과(돌풍 충전 · 거미줄 · 점액).
        summonInfestedSilverfish();
        applyPlayerTriggeredEffects();
        // runtime은 틱 끝에 죽은 몹을 제거하므로, 비전투 사망(햇빛 등)의 장비 드랍을 위해 참조를 보존한다.
        drainChunkPopulation();
        drainStructureOccupants(tickNo);
        drainGlitchSignals();
        List<Mob> beforeTick = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        // 직업 배정은 몹 틱 앞에서 확정한다: 이번 틱의 걷기 목표가 같은 틱의 이동에 실려야
        // 바닐라처럼 "직업지로 걸어가 2블록 안에서 취업"이 한 흐름으로 이어진다.
        // [VILLAGER-BELL] 활동 원장이 먼저다: 직업·사회·gossip·이동이 같은 틱의 활동을 읽는다.
        tickVillagerActivities(beforeTick, tickNo);
        tickVillagerJobs(beforeTick);
        tickVillagerGossip(beforeTick);
        MobRuntime.TickResult result = runtime.tickWithDeferredPersistenceSnapshots(
                world, rng, rt.activeSimulationChunksForMobTick());
        for (ProjectileSim pearl : runtime.arrows()) {
            if (pearl.gatewayPending) rt.endGateways().travelPearl(pearl);
        }
        extinguishMobsInCauldrons(runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()));
        terminatedThisTick.clear();
        for (ProjectileSim arrow : result.terminatedProjectiles()) {
            if(arrow.kind==ProjectileSim.Kind.ENDER_PEARL && arrow.terminalReason!=null && arrow.terminalReason.endsWith("hit")) {
                PlayerTickState owner=rt.players().get(arrow.shooterNickname());
                if(owner!=null && !owner.isDead()) {
                    owner.forcePose(arrow.previousX,arrow.previousY,arrow.previousZ,owner.yaw(),owner.pitch());
                    owner.resetFallDistanceAfterSmash();
                    owner.damage(5,"ender_pearl");
                    var session=rt.session(owner.nickname());
                    if(session!=null) rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(),session,
                        new com.gameexpert.ws.dto.WsMessages.PlayerTeleportSelf(owner.x(),owner.y(),owner.z()));
                    PlayerRelocation.publish(rt, owner);
                }
            }
            terminatedThisTick.put(arrow.id, arrow);
            terminatedArrows.put(arrow.id, new ProjectileRemoval(arrow.id, arrow.terminalReason,
                    arrow.x, arrow.y, arrow.z, arrow.targetMobId));
            if (arrow.shouldWindBurstOnTerminal()) {
                applyWindChargeBurst(arrow);
            }
            // [BLOCK-SHAPES] BellBlock#onProjectileHit: 종의 충돌 상자를 맞힌 투사체는 그 면·높이로 종을 친다.
            if ("block_hit".equals(arrow.terminalReason) && arrow.blockHitFace >= 0) {
                rt.tickLoop().redstoneTargetHit(arrow);
                rt.tickLoop().ringBellFromProjectile(arrow.blockHitX, arrow.blockHitY, arrow.blockHitZ,
                        arrow.blockHitFace, arrow.y - arrow.blockHitY, arrow.shooterNickname());
                // [POT-PROJECTILE] DecoratedPotBlock#onProjectileHit: an impact projectile shatters the pot.
                rt.tickLoop().shatterDecoratedPotFromProjectile(arrow.kind,
                        arrow.blockHitX, arrow.blockHitY, arrow.blockHitZ);
                rt.tickLoop().breakChorusFlowerFromProjectile(arrow.kind,
                        arrow.blockHitX, arrow.blockHitY, arrow.blockHitZ);
            }
            // 부화는 이 자리에서만 확정한다. 아래 broadcastMobs 가 직전 활성 집합과 비교하므로
            // 여기서 더한 병아리는 종결된 알의 projectileRemove 와 같은 틱에 mobSpawn 으로 나간다.
            // [ZOMBIE-ANIMAL] 상한 달걀은 부화 확률이 0 이라 굴림 자체를 하지 않는다.
            if (arrow.kind == ProjectileSim.Kind.EGG
                    && (!arrow.spoiledEgg || FarmAnimalRules.spoiledEggHatches())
                    && FarmAnimalRules.eggHatchesOnTerminal(arrow.terminalReason)) {
                runtime.hatchEggChicks(arrow.x, arrow.y, arrow.z, arrow.eggVariant, rng);
            }
            // [TRIDENT] 플레이어가 던진 삼지창만 종결 지점에 회수물로 떨어진다. 종결 사유는
            // 가리지 않는다 — 명중이든 빗나감이든 바닐라의 삼지창은 그 자리에 남는다.
            // 드라운드 투척은 recoverableDurability 가 0 이라 여기서 걸러진다.
            if (arrow.kind == ProjectileSim.Kind.TRIDENT && arrow.recoverableDurability > 0) {
                rt.itemSystem().spawnTridentRecovery(arrow.recoverableDurability,
                        arrow.recoverEnchantments, arrow.recoverComponentData,
                        arrow.x, arrow.y, arrow.z);
                projectileRecoveryPersistencePending = true;
            }
        }
        // [ARROW-GROUND][BLOCK-SHAPES] 박힌 화살은 종결하지 않지만 AbstractArrow.onHitBlock 도 종을 친다.
        for (ProjectileSim arrow : runtime.arrowsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (arrow.stuckThisTick() && arrow.blockHitFace >= 0) {
                rt.tickLoop().redstoneTargetHit(arrow);
                rt.tickLoop().ringBellFromProjectile(arrow.blockHitX, arrow.blockHitY, arrow.blockHitZ,
                        arrow.blockHitFace, arrow.stuckHitY() - arrow.blockHitY, arrow.shooterNickname());
                // [POT-PROJECTILE] 박히는 화살도 DecoratedPotBlock#onProjectileHit 로 항아리를 깬다(정적판은
                // 같은 blockHits 에서 깬다). 깨진 칸의 화살은 박힌 블록이 사라진 화살 규칙으로 떨어진다.
                rt.tickLoop().shatterDecoratedPotFromProjectile(arrow.kind,
                        arrow.blockHitX, arrow.blockHitY, arrow.blockHitZ);
                rt.tickLoop().breakChorusFlowerFromProjectile(arrow.kind,
                        arrow.blockHitX, arrow.blockHitY, arrow.blockHitZ);
            }
        }
        Set<Long> removedIds = removedMobIdsBuf;
        removedIds.clear();
        for (Mob mob : beforeTick) {
            if (runtime.containsMob(mob.id)) {
                if (mob.isDead() && !mobDropsHandled.contains(mob.id)) {
                    spawnMobDrops(mob, false);
                    mobDropsHandled.add(mob.id);
                }
                continue;
            }
            // Death and conversion are both permanent removal boundaries for durable villager
            // state. Hibernation never reaches this branch because the mob remains in runtime.
            if (mob.type == MobType.VILLAGER) {
                villagerJobAssignment.forget(mob.id);
                villagerTrades().forget(mob.id);
                villagerActivities.forget(mob.id);
            }
            if (mob.isDead() && !mob.removed) {
                if (!mobDropsHandled.contains(mob.id)) spawnMobDrops(mob, false);
                mobDropsHandled.remove(mob.id);
                // [TRIAL-GAP] MobEffect.onMobRemoved(KILLED): 사망 확정(런타임 제거) 한 번만.
                applyDeathTriggeredEffects(mob.x, mob.y, mob.z, mob.height(), mob.width(),
                        deathTriggeredEffectsOf(mob));
            }
            removedIds.add(mob.id);
        }
        mobDropsHandled.removeIf(id -> !runtime.containsMob(id));
        List<Mob> fluidCandidates = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        for (Mob mob : fluidCandidates) {
            if (!mob.isRidingBoat() && !mob.isMobPassenger()
                    && applyFluidPush(mob, world, fluidBlocks, fluidFlow)) {
                runtime.refreshSpatialIndex(mob);
            }
        }
        // Fluid moves the vehicle after the AI pass. Re-attach mob passengers to that final
        // position; passengers never receive an independent fluid displacement.
        runtime.syncMobPassengersToVehicles();
        List<Mob> activeForCombat = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        consumeEvents(result, activeForCombat, removedIds);
        // [SPEAR-MOB] 창을 쓰는 몹의 KineticWeapon.damageEntities(AI 가 움직인 뒤, 같은 MOB 단계).
        resolveMobSpearKinetics(activeForCombat);
        syncMountedHud();
        // 유체가 청크 경계를 넘긴 경우 최종 활성 집합과 공간 인덱스를 같은 위치 기준으로 관찰한다.
        List<Mob> activeAfterTick = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        for (Mob mob : activeAfterTick) mob.finishActiveTickPlayerKillCredit();
        runtime.deferPersistenceSnapshots(activeAfterTick);
        syncRaidOutcome(tickNo);
        syncTrialSpawnerOutcome();
        boolean mobsChanged = broadcastMobs(removedIds, activeAfterTick);
        // 찌 위치(입질 잠김)와 회수 제거를 같은 틱의 projectileUpdate/Remove 에 실어 보낸다.
        tickFishing(tickNo);
        terminatedThisTick.clear();
        // [ARROW-GROUND] 박힌 화살 줍기. 같은 틱의 projectileRemove(reason pickup)로 나간다.
        pickUpGroundArrows();
        broadcastArrows();
        // welcome 스냅샷은 몹 집합/위치가 실제로 바뀐 틱에만 재구성한다(매틱 전량 재할당 제거).
        // broadcastMobs 가 변경(스폰/이동≥POS_EPS/디스폰)을 감지했을 때만 갱신하므로, 이때의
        // 스냅샷은 브로드캐스트 스트림과 동일한 현재 상태를 반영한다(입장 클라도 정확한 위치를 받음).
        if (mobsChanged) {
            publishSnapshot(activeAfterTick);
        }
    }

    /**
     * [MACE] 받아들여진 낙하 강타의 후속(바닐라 {@code MaceItem.hurtEnemy} → {@code doPostAttackEffects}
     * (돌풍) → {@code postHurtEnemy}). 소리·입자 사건, 대상 둘레 3.5 블록 넉백, 돌풍 폭발, 공격자 수직 속도
     * 0.01 블록/틱과 낙하 거리 초기화를 이 순서로 한다. 이동은 클라 권위라 플레이어 속도 변화는
     * {@link WsMessages.PlayerImpulse} 로 나른다(몹은 권위가 직접 민다).
     */
    void applyMaceSmash(PlayerTickState attacker, CombatSystem.MaceSmash smash) {
        if (attacker == null || smash == null) return;
        double fall = smash.fallDistance();
        Mob target = runtime.mobById(smash.targetId());
        double tx = target != null ? target.x : attacker.x();
        double ty = target != null ? target.y : attacker.y();
        double tz = target != null ? target.z : attacker.z();
        // hurtEnemy: 소리는 공격자 위치(volume 1 · pitch 1), 입자 사건은 대상 getOnPos(발밑 0.2).
        worldSound(MaceRules.smashSound(smash.targetOnGround(), fall),
                attacker.x(), attacker.y(), attacker.z(), (short) 0);
        levelEvent(MaceRules.SMASH_LEVEL_EVENT, (int) Math.floor(tx), (int) Math.floor(ty - 0.2),
                (int) Math.floor(tz), MaceRules.SMASH_LEVEL_EVENT_DATA);
        // hurtEnemy → knockback(level, attacker, target): 대상 발 위치 기준 3.5 블록 안의 살아 있는 개체.
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed || mob.id == smash.targetId()) continue;
            if (MaceRules.isTamableAnimal(mob.type)
                    && attacker.nickname().equals(mob.ownerNickname())) continue;
            double dx = mob.x - tx;
            double dy = mob.y - ty;
            double dz = mob.z - tz;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (!MaceRules.withinKnockbackRange(distanceSq)) continue;
            double distance = Math.sqrt(distanceSq);
            double power = MaceRules.knockbackPower(distance, fall, mob.knockbackResistance());
            if (!(power > 0.0) || distance <= 0.0) continue;
            // push(x·p, 0.7, z·p) 블록/MC틱 → 권위 틱(2 MC틱) 단위의 순수 가산.
            mob.applyFishingPull(dx / distance * power * 2.0,
                    MaceRules.SMASH_ATTACK_KNOCKBACK_POWER * 2.0, dz / distance * power * 2.0);
        }
        for (PlayerTickState player : rt.players().values()) {
            if (player == attacker || player.isDead()) continue;
            double dx = player.x() - tx;
            double dy = player.y() - ty;
            double dz = player.z() - tz;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (!MaceRules.withinKnockbackRange(distanceSq)) continue;
            double distance = Math.sqrt(distanceSq);
            PlayerInventory armor = player.inventory();
            double power = MaceRules.knockbackPower(distance, fall,
                    MaceRules.playerKnockbackResistance(armor.equippedType(ArmorSlot.HELMET),
                            armor.equippedType(ArmorSlot.CHESTPLATE),
                            armor.equippedType(ArmorSlot.LEGGINGS),
                            armor.equippedType(ArmorSlot.BOOTS)));
            if (!(power > 0.0) || distance <= 0.0) continue;
            sendImpulse(player, new WsMessages.PlayerImpulse(player.nickname(),
                    dx / distance * power * 20.0, MaceRules.SMASH_ATTACK_KNOCKBACK_POWER * 20.0,
                    dz / distance * power * 20.0, null));
        }
        // doPostAttackEffects → wind_burst(affected attacker): 공격자 발 위치의 세기 3.5 돌풍 폭발.
        double attackerLift = 0.0;
        int windBurst = smash.windBurstLevel();
        if (MaceRules.windBurstApplies(windBurst, fall)) {
            attackerLift = applyMaceWindBurst(attacker, windBurst);
        }
        // hurtEnemy 의 Vec3.with(Y, 0.01) 뒤 폭발 넉백이 더해진다. postHurtEnemy 가 낙하 거리를 지운다.
        attacker.resetFallDistanceAfterSmash();
        sendImpulse(attacker, new WsMessages.PlayerImpulse(attacker.nickname(), 0.0,
                attackerLift * 20.0, 0.0, MaceRules.ATTACKER_VERTICAL_VELOCITY_PER_MC_TICK * 20.0));
    }

    /**
     * [MACE] 돌풍 인챈트의 폭발({@code ExplodeEffect} → {@code ServerLevel.explode}, 원천 개체 없음이라 공격자도
     * 맞는다). {@code ServerExplosion.hurtEntities}: 거리 비율 {@code d = |발 − 중심| / (2·세기)} 가 1 이하인
     * 개체마다 {@code (x − cx, 눈 − cy, z − cz)} 방향으로 {@code (1 − d) · 노출 · 배율 · (1 − 폭발 넉백 저항)}
     * 블록/MC틱을 더한다. 피해·블록 파괴는 없다(데미지 타입 없음, block_interaction trigger).
     *
     * @return 공격자 자신이 받는 위쪽 임펄스(블록/MC틱)
     */
    private double applyMaceWindBurst(PlayerTickState attacker, int level) {
        double cx = attacker.x();
        double cy = attacker.y();
        double cz = attacker.z();
        double power = MaceRules.WIND_BURST_POWER;
        double multiplier = MaceRules.windBurstKnockbackMultiplier(level);
        worldSound("wind_burst", cx, cy, cz, (short) 0);
        double attackerLift = 0.0;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double fx = player.x() - cx;
            double fy = player.y() - cy;
            double fz = player.z() - cz;
            double ratio = Math.sqrt(fx * fx + fy * fy + fz * fz) / (2.0 * power);
            if (ratio > 1.0) continue;
            double ex = fx;
            double ey = player.y() + PlayerInteractionRules.STANDING_EYE_HEIGHT - cy;
            double ez = fz;
            double length = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (length == 0.0) continue;
            double exposure = ExplosionRules.exposure(this::residentBlock, cx, cy, cz,
                    player.x() - 0.3, player.y(), player.z() - 0.3,
                    player.x() + 0.3, player.y() + ProjectileSim.PLAYER_HEIGHT, player.z() + 0.3);
            PlayerInventory armor = player.inventory();
            double impulse = (1.0 - ratio) * exposure * multiplier
                    * EnchantmentRules.explosionKnockbackMultiplier(
                            armor.equippedEnchantments(ArmorSlot.HELMET),
                            armor.equippedEnchantments(ArmorSlot.CHESTPLATE),
                            armor.equippedEnchantments(ArmorSlot.LEGGINGS),
                            armor.equippedEnchantments(ArmorSlot.BOOTS));
            if (player == attacker) {
                attackerLift = ey / length * impulse;
                continue;
            }
            sendImpulse(player, new WsMessages.PlayerImpulse(player.nickname(),
                    ex / length * impulse * 20.0, ey / length * impulse * 20.0,
                    ez / length * impulse * 20.0, null));
        }
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            double fx = mob.x - cx;
            double fy = mob.y - cy;
            double fz = mob.z - cz;
            double ratio = Math.sqrt(fx * fx + fy * fy + fz * fz) / (2.0 * power);
            if (ratio > 1.0) continue;
            double ey = mob.y + mob.eyeHeight() - cy;
            double length = Math.sqrt(fx * fx + ey * ey + fz * fz);
            if (length == 0.0) continue;
            double half = mob.width() * 0.5;
            double exposure = ExplosionRules.exposure(this::residentBlock, cx, cy, cz,
                    mob.x - half, mob.y, mob.z - half,
                    mob.x + half, mob.y + mob.height(), mob.z + half);
            double impulse = (1.0 - ratio) * exposure * multiplier;
            if (!(impulse > 0.0)) continue;
            mob.applyFishingPull(fx / length * impulse * 2.0, ey / length * impulse * 2.0,
                    fz / length * impulse * 2.0);
        }
        return attackerLift;
    }

    /** 피해 여부와 무관하게 한 플레이어에게만 가는 권위 임펄스 메시지. */
    private void sendImpulse(PlayerTickState player, WsMessages.PlayerImpulse message) {
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
        }
    }

    /** Trigger-only wind explosion: no blast damage or drops, only exposure-scaled impulse. */
    private void applyWindChargeBurst(ProjectileSim charge) {
        applyWindBurst(charge.x, charge.y, charge.z, charge.windBurstPower(),
                charge.windKnockbackMultiplier());
    }

    /**
     * [TRIAL-GAP] 돌풍 폭발 한 번. 바람 탄환 착탄과 돌풍 충전 사망이 같은 계산을 쓴다
     * ({@code AbstractWindCharge.EXPLOSION_DAMAGE_CALCULATOR}).
     */
    private void applyWindBurst(double cx, double cy, double cz, double power,
            double multiplier) {
        double radius = power * 2.0;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double dx = player.x() - cx;
            double dy = player.y() + 0.9 - cy;
            double dz = player.z() - cz;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > radius || distance <= 1e-9) continue;
            double exposure = ExplosionRules.exposure(this::residentBlock,
                    cx, cy, cz,
                    player.x() - 0.3, player.y(), player.z() - 0.3,
                    player.x() + 0.3, player.y() + ProjectileSim.PLAYER_HEIGHT,
                    player.z() + 0.3);
            double impact = ExplosionRules.impactAt(distance, power, exposure) * multiplier;
            if (impact <= 0) continue;
            double velocity = impact * 20.0 / distance;
            // 이 폭발은 피해가 없어 justDamaged 방송을 기다리면 충격이 유실된다.
            sendImpulse(player, new WsMessages.PlayerImpulse(player.nickname(),
                    dx * velocity, dy * velocity, dz * velocity, null));
        }
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            double dx = mob.x - cx;
            double dy = mob.y + mob.height() * 0.5 - cy;
            double dz = mob.z - cz;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > radius || distance <= 1e-9) continue;
            double half = mob.width() * 0.5;
            double exposure = ExplosionRules.exposure(this::residentBlock,
                    cx, cy, cz,
                    mob.x - half, mob.y, mob.z - half,
                    mob.x + half, mob.y + mob.height(), mob.z + half);
            double impact = ExplosionRules.impactAt(distance, power, exposure) * multiplier;
            if (impact <= 0) continue;
            double velocity = impact * 2.0 / distance;
            mob.applyExplosionKnockback(dx * velocity, dy * velocity, dz * velocity);
        }
    }

    private void extinguishMobsInCauldrons(List<Mob> mobs) {
        for (Mob mob : mobs) {
            if (!mob.isOnFire() || mob.isDead()) continue;
            int x = (int) Math.floor(mob.x);
            int y = (int) Math.floor(mob.y);
            int z = (int) Math.floor(mob.z);
            if ((world.getBlock(x, y, z) & 0xffff) != Blocks.CAULDRON) {
                y--;
                if ((world.getBlock(x, y, z) & 0xffff) != Blocks.CAULDRON) continue;
            }
            int state = rt.blockStates().get(x, y, z, Blocks.CAULDRON);
            int kind = com.gameexpert.engine.inventory.CauldronRules.kind(state);
            if ((kind != com.gameexpert.engine.inventory.CauldronRules.WATER
                    && kind != com.gameexpert.engine.inventory.CauldronRules.POWDER_SNOW)
                    || com.gameexpert.engine.inventory.CauldronRules.level(state) == 0) continue;
            int nextState = com.gameexpert.engine.inventory.CauldronRules.lowerOneLevel(state);
            long settlementId = rt.itemSystem().reserveSettlementEntityId();
            var command = new com.gameexpert.block.dto.BlockStateSettlementCommand(
                    settlementId, rt.worldId(), x, y, z,
                    (short) Blocks.CAULDRON, (short) nextState);
            rt.playerBlockSettlements().settleBlockState(command);
            mob.extinguish();
            rt.setBlockState(x, y, z, Blocks.CAULDRON, nextState);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.CAULDRON);
            broadcast(new com.gameexpert.ws.dto.WsMessages.SoundEvent(
                    "fire_extinguish", (short) Blocks.CAULDRON));
        }
    }

    /**
     * Turns ledger edges into durable evidence and one boss-bar broadcast.
     *
     * <p>The victory receipt is only created here, from the ledger's own confirmed VICTORY, and is
     * handed to the same persistence boundary as the raid rows. Re-emitting it after a restart is
     * deliberate: the receipt insert is idempotent on {@code (worldId, raidId, rewardToken)}, so a
     * crash that lost the receipt but kept the victory still ends up with exactly one row.</p>
     */
    private void syncRaidOutcome(long tickNo) {
        // [RAID-OMEN] Raid.tick 의 VICTORY: 영웅마다 HERO_OF_THE_VILLAGE(48000 MC 틱, 증폭 raidOmenLevel − 1).
        for (RaidLedger.Instance victory : runtime.drainRaidVictories()) {
            for (String hero : victory.heroes()) {
                PlayerTickState player = rt.players().get(hero);
                if (player == null || player.isDead()) continue;
                player.applyStatusEffectMcTicks(StatusEffect.HERO_OF_THE_VILLAGE,
                        Math.max(0, victory.omenLevel() - 1), RaidLedger.HERO_OF_THE_VILLAGE_MC_TICKS);
            }
        }
        RaidLedger ledger = runtime.raidLedger();
        for (RaidLedger.Instance instance : ledger.instances()) {
            if (instance.status() != RaidLedger.Status.VICTORY) continue;
            unpersistedRaidReceipts.computeIfAbsent(instance.raidId(),
                    key -> RaidRewardReceipt.pending(instance, tickNo));
        }
        broadcastRaidBossbar();
    }

    /** Sends the bar only on real change; {@code active=false} is the single teardown signal. */
    /**
     * [TRIAL] 시련 원장이 낸 이번 틱의 가장자리를 실제 월드에 커밋한다.
     *
     * <p>상태 비트는 그대로 블록에 쓰고(스포너 여섯 상태 · 불길함, 금고 활성), 불길한 징조는
     * 시련의 징조로 바꾸며, EJECTING_REWARD 의 배출(감지 플레이어마다 하나, 바닐라 배출 표가 고른
     * 열쇠·소모품)은 durable 정산으로 갚는다 — 바닐라와 같이 스포너가 <b>배출</b>하므로 인벤토리가
     * 가득 차도 보상이 사라지지 않는다. 금고 자체의 전리품은 열쇠를 들고 온 플레이어가 우클릭했을 때
     * 영수증이 소유한다.</p>
     */
    void syncTrialSpawnerOutcome() {
        var trials = runtime.trialSpawners();
        // [TRIAL] 바닐라 transformBadOmenIntoTrialOmen: 불길한 징조를 지우고 18000 × (앰프 + 1)
        // MC 틱의 시련의 징조(앰프 0)를 준다. 스포너가 이미 불길해진 뒤라 순서는 결과를 바꾸지 않는다.
        for (var conversion : trials.drainOmenConversions()) {
            PlayerTickState player = rt.players().get(conversion.nickname());
            if (player == null || player.isDead()) continue;
            StatusEffects effects = player.statusEffects();
            if (!effects.has(StatusEffect.BAD_OMEN)) continue;
            int amplifier = effects.amplifier(StatusEffect.BAD_OMEN);
            player.removeStatusEffect(StatusEffect.BAD_OMEN);
            player.applyStatusEffect(StatusEffect.TRIAL_OMEN, 0,
                    com.gameexpert.engine.trial.TrialSpawnerContract
                            .trialOmenDurationTicks(amplifier));
        }
        List<com.gameexpert.engine.trial.TrialSpawnerRuntime.Victory> victories =
                trials.pendingVictories();
        if (!victories.isEmpty() && victories.getFirst().rewardEntityId() == 0) {
            var victory = victories.getFirst();
            long entityId = rt.itemSystem().reserveSettlementEntityId();
            trials.bindVictoryRewardEntity(victory.trialId(), victory.rewardIdentity(), entityId);
            victories = trials.pendingVictories();
        }
        if ((!trials.pendingStateChanges().isEmpty() || !trials.pendingVictories().isEmpty())
                && rt.trialPersistence() != null) {
            // State/victory edges bypass the periodic cadence. The persistence writer snapshots
            // mobs and Trial together; runtime fixture changes and rewards wait for its commit.
            rt.flushPersistentMobs();
        }
        if (!victories.isEmpty()) rt.settleTrialReward(victories.getFirst());
        // [TRIAL-GAP] 금고 배출(바닐라 VaultState.ejectResultItem). 스포너 배출과 같은 지면 CAS 정산을
        // 쓰고, durable 근거는 열쇠 정산이 만든 배출 outbox 행이다.
        List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultEjection> ejections =
                trials.pendingVaultEjections();
        if (!ejections.isEmpty()) {
            var ejection = ejections.getFirst();
            if (ejection.entityId() == 0) {
                trials.bindVaultEjectionEntity(ejection.token(),
                        rt.itemSystem().reserveSettlementEntityId());
                ejection = trials.pendingVaultEjections().getFirst();
            }
            rt.settleVaultEjection(ejection);
        }
        // [TRIAL-GAP] 이번 틱의 level event(3011~3020 입자·소리 폭발)와 서버 playSound.
        for (var event : trials.drainLevelEvents()) {
            levelEvent(event.event(), event.x(), event.y(), event.z(), event.data());
        }
        for (var sound : trials.drainSounds()) {
            if (Float.isNaN(sound.pitch())) {
                trialWorldSound(sound.kind(), sound.x(), sound.y(), sound.z(),
                        (short) sound.blockType());
            } else {
                trialWorldSound(sound.kind(), sound.x(), sound.y(), sound.z(),
                        (short) sound.blockType(), sound.pitch());
            }
        }
    }

    /** [TRIAL] 금고 활성 여부를 상호작용 lane 이 읽는 자리. */
    com.gameexpert.engine.trial.TrialSpawnerRuntime trialSpawners() {
        return runtime.trialSpawners();
    }

    private void broadcastRaidBossbar() {
        RaidLedger.Instance instance = runtime.raidLedger().ongoing();
        boolean visible = instance != null && instance.bossbarVisible();
        if (!visible) {
            if (lastBossbar == null) return;
            WsMessages.RaidBossbar teardown = new WsMessages.RaidBossbar(
                    lastBossbar.getRaidId(), false, 0.0, lastBossbar.getWave(),
                    lastBossbar.getWaveCount(), lastBossbar.getStatus());
            lastBossbar = null;
            broadcast(teardown);
            return;
        }
        WsMessages.RaidBossbar next = new WsMessages.RaidBossbar(
                raidIdHex(instance.raidId()), true, instance.bossbarProgress(),
                instance.currentWave(), instance.waveCount(), instance.status().name());
        if (lastBossbar != null && sameBossbar(lastBossbar, next)) return;
        lastBossbar = next;
        broadcast(next);
    }

    private static boolean sameBossbar(WsMessages.RaidBossbar left, WsMessages.RaidBossbar right) {
        return left.getRaidId().equals(right.getRaidId())
                && left.isActive() == right.isActive()
                && left.getWave() == right.getWave()
                && left.getWaveCount() == right.getWaveCount()
                && left.getStatus().equals(right.getStatus())
                && Math.abs(left.getProgress() - right.getProgress()) < 1e-9;
    }

    /** 64-bit identity as fixed 16-digit hex; JSON numbers would lose the low bits in a browser. */
    static String raidIdHex(long raidId) {
        return String.format("%016x", raidId);
    }

    /** Converts Bad Omen on village entry, then arms exactly one raid after the 30-second warning. */
    private void advanceRaidOmens(List<Mob> activeMobs, long tickNo) {
        double rangeSquared = ProceduralRaidSchedule.ACTIVATION_RANGE
                * ProceduralRaidSchedule.ACTIVATION_RANGE;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            Mob anchor = nearestRaidAnchor(
                    activeMobs, player.x(), player.y(), player.z(), rangeSquared);
            if (anchor == null) continue;
            StatusEffects effects = player.statusEffects();
            if (effects.has(StatusEffect.BAD_OMEN)) {
                // [TRIAL-GAP] 바닐라 BadOmenMobEffect.applyEffectTick: RAID_OMEN(600 MC 틱, 증폭 =
                // 불길한 징조의 증폭). 레벨 I~V 가 그대로 넘어간다.
                int amplifier = effects.amplifier(StatusEffect.BAD_OMEN);
                player.removeStatusEffect(StatusEffect.BAD_OMEN);
                player.applyStatusEffect(StatusEffect.RAID_OMEN, amplifier, 300);
                continue;
            }
            if (effects.remainingServerTicks(StatusEffect.RAID_OMEN) == 1) {
                // [RAID-OMEN] 바닐라 RaidOmenMobEffect → Raids.createOrExtendRaid: 그 자리에 활성 레이드가 있으면
                // 징조(amplifier + 1)를 흡수하고, 없으면 새 레이드를 그 레벨로 arm 한다.
                int amplifier = effects.amplifier(StatusEffect.RAID_OMEN);
                boolean absorbed = runtime.absorbRaidOmen(player.x(), player.y(), player.z(), amplifier);
                if (absorbed || runtime.armRaid(anchor.id, tickNo, player.nickname(), amplifier + 1)) {
                    player.removeStatusEffect(StatusEffect.RAID_OMEN);
                }
            }
        }
    }

    /** Nearest eligible village anchor; mob id breaks exact-distance ties deterministically. */
    static Mob nearestRaidAnchor(List<Mob> activeMobs, double x, double y, double z,
                                 double rangeSquared) {
        Mob nearest = null;
        double nearestDistanceSquared = rangeSquared;
        for (Mob mob : activeMobs) {
            if (mob.type != MobType.VILLAGER || mob.isBaby() || mob.isDead() || mob.removed) {
                continue;
            }
            double dx = x - mob.x;
            double dy = y - mob.y;
            double dz = z - mob.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > nearestDistanceSquared) continue;
            if (nearest == null || distanceSquared < nearestDistanceSquared || mob.id < nearest.id) {
                nearest = mob;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    /** 발/몸통 중심 중 먼저 닿는 유체만 계산하고 AI 이동 결과에 추가한다. 플레이어는 대상이 아니다. */
    static boolean applyFluidPush(Mob mob, com.gameexpert.engine.mob.MobWorldView world,
                                  Fluids.BlockLookup blocks, double[] flow) {
        int x = (int) Math.floor(mob.x);
        int z = (int) Math.floor(mob.z);
        int feetY = (int) Math.floor(mob.y + 0.01);
        int bodyY = (int) Math.floor(mob.y + mob.height() * 0.5);
        int fluidType = Fluids.typeOf(world.getBlock(x, feetY, z) & 0xFFFF);
        int sampleY = feetY;
        if (fluidType == Fluids.NONE && bodyY != feetY) {
            fluidType = Fluids.typeOf(world.getBlock(x, bodyY, z) & 0xFFFF);
            sampleY = bodyY;
        }
        if (fluidType == Fluids.NONE
                || !Fluids.flowVector(blocks, x, sampleY, z, flow)) {
            return false;
        }
        double push = Fluids.pushPerTick(fluidType);
        MobPhysics.addFluidPush(mob, world,
                flow[0] * push, flow[1] * push, flow[2] * push);
        return true;
    }

    // ── 이벤트 소비: 플레이어 피해 · 폭발 · 디스폰 사유 ──
    private void consumeEvents(MobRuntime.TickResult result, List<Mob> activeMobs,
                               Set<Long> removedIds) {
        for (MobRuntime.MobEmit emit : result.mobEvents()) {
            MobEvent event = emit.event();
            if (event instanceof MobEvent.AllayPickupItem pickup) {
                rt.itemSystem().confirmAllayPickup(runtime, emit.mobId(), pickup);
            } else if (event instanceof MobEvent.AllayReturnItem returned) {
                resolveAllayReturn(emit.mobId(), returned);
            } else if (event instanceof MobEvent.AttackMob attack && attack.direct()) {
                resolveDirectMobAttack(emit.mobId(), attack, activeMobs);
            } else if (event instanceof MobEvent.AttackPlayer attack) {
                // 근접 공격: 사망 메시지용으로 가해 몹 종류(zombie/spider 등)를 killer 로 전달.
                Mob attacker = findMob(emit.mobId());
                PlayerTickState target = rt.players().get(attack.nickname());
                if (attacker == null || attacker.isDead() || attacker.removed
                        || target == null || target.isDead()) continue;
                boolean ravagerAttack = attacker instanceof Ravager;
                boolean pufferfishContact = attacker.type == MobType.PUFFERFISH;
                boolean poisonFrogContact = attacker.type == MobType.POISON_DART_FROG;
                if (ravagerAttack) {
                    // Ravager sets AttackTick and plays its attack sound before hurt/shield logic.
                    attacker.commitAcceptedMeleeAttack();
                    mobSound(attacker, "attack");
                }
                MobAttackOutcome outcome = damagePlayerFromMob(
                        attack.nickname(), attack.damage(), emit.mobId());
                if (outcome.accepted()) {
                    if (!ravagerAttack && !pufferfishContact && !poisonFrogContact) {
                        attacker.commitAcceptedMeleeAttack();
                        mobSound(attacker, "attack");
                    }
                    if (outcome.damaged()) {
                        if (attacker instanceof com.gameexpert.engine.mob.PoisonDartFrog frog) {
                            frog.confirmDefensiveContact();
                            runtime.refreshPersistenceSnapshot(frog);
                            mobSound(frog, "attack");
                        }
                        runtime.rememberOwnerHurtByMob(attack.nickname(), emit.mobId());
                        if (attack.effect() != null) {
                            applyEffectToPlayer(rt.players().get(attack.nickname()), attack.effect(), 1.0);
                        }
                    }
                }
            } else if (event instanceof MobEvent.StingPlayer sting) {
                MobAttackOutcome outcome = damagePlayerFromMob(
                        sting.nickname(), sting.damage(), emit.mobId());
                if (outcome.accepted()) {
                    Mob source = findMob(emit.mobId());
                    if (source != null) mobSound(source, "sting");
                    if (outcome.damaged()) {
                        int poisonTicks = switch (rt.difficulty()) {
                            case EASY -> 0;
                            case NORMAL -> 100;
                            case HARD -> 180;
                        };
                        PlayerTickState target = rt.players().get(sting.nickname());
                        if (poisonTicks > 0 && target != null) {
                            target.applyStatusEffect(StatusEffect.POISON, 0, poisonTicks);
                        }
                    }
                }
            } else if (event instanceof MobEvent.GoatRamPlayer ram) {
                MobAttackOutcome outcome = damagePlayerFromMob(
                        ram.nickname(), ram.damage(), emit.mobId());
                if (outcome.damaged()) {
                    PlayerTickState target = rt.players().get(ram.nickname());
                    if (target != null) {
                        target.setHurtKnockback(
                                ram.directionX() * ram.knockback() * 10.0,
                                CombatRules.KNOCKBACK_VERTICAL_BPS,
                                ram.directionZ() * ram.knockback() * 10.0,
                                0.0, 0.0);
                    }
                }
            } else if (event instanceof MobEvent.BeeHiveEntry entry) {
                pendingBeeHiveEntries.addLast(new BeeHiveEntryRequest(
                        emit.mobId(), entry.x(), entry.y(), entry.z()));
            } else if (event instanceof MobEvent.Sound sound) {
                Mob source = findMob(emit.mobId());
                if (source != null) mobSound(source, sound.kind());
            } else if (event instanceof MobEvent.DropItem drop) {
                rt.itemSystem().spawnMobDrop(drop.itemType(), drop.count(),
                        drop.x(), drop.y(), drop.z());
            } else if (event instanceof MobEvent.FroglightDrop drop) {
                pendingFroglightSettlements.addLast(new FroglightSettlementRequest(
                        drop.frogMobId(), drop.sulfurCubeMobId(), drop.itemType(),
                        drop.x(), drop.y(), drop.z()));
            } else if (event instanceof MobEvent.LeashBroken broken) {
                PlayerTickState holder = rt.players().get(broken.holderNickname());
                if (holder != null && !holder.isDead()
                        && holder.inventory().addItem(PlayerInventory.LEAD, 1) == 1) {
                    sendInventory(holder);
                } else {
                    rt.itemSystem().spawnMobDrop(PlayerInventory.LEAD, 1,
                            broken.x(), broken.y(), broken.z());
                }
            } else if (event instanceof MobEvent.BreedingComplete birth) {
                rt.xpOrbSystem().spawnOrbs(
                        XpRules.xpForBreeding(Math.abs(rng.nextInt(1000))),
                        birth.x(), birth.y(), birth.z());
            } else if (event instanceof MobEvent.ShootShulkerBullet) {
                // [EC-MOBS] 탄환은 MobRuntime 이 이미 투사체 원장에 넣었다(방송은 새 투사체 경로가 한다).
            } else if (event instanceof MobEvent.Teleported teleport) {
                broadcastTeleport(emit.mobId(), teleport.fromX(), teleport.fromY(), teleport.fromZ(),
                        teleport.toX(), teleport.toY(), teleport.toZ());
            } else if (event instanceof MobEvent.Explode explode) {
                handleExplosion(explode);
            } else if (event instanceof MobEvent.Despawned despawned) {
                pendingDespawnReasons.put(emit.mobId(), despawned.reason());
            } else if (event instanceof MobEvent.Converted converted) {
                if (converted.replacementMobId() == emit.mobId()) {
                    Mob replacement = findMob(emit.mobId());
                    if (replacement != null && !replacement.isDead() && !replacement.removed) {
                        broadcast(new WsMessages.MobTransform(
                                nextEventId(), spawnDto(replacement)));
                    }
                } else {
                    pendingDespawnReasons.put(emit.mobId(), "converted");
                }
            } else if (event instanceof MobEvent.EnvironmentDamage damage) {
                broadcast(new WsMessages.MobHurt(emit.mobId(), damage.lethal()));
                if (damage.lethal()) pendingDespawnReasons.put(emit.mobId(), "death");
            } else if (event instanceof MobEvent.RavagerRoar roar) {
                resolveRavagerRoar(emit.mobId(), roar, activeMobs, removedIds);
            } else if (event instanceof MobEvent.AreaStatusEffect aura) {
                resolveAreaStatusEffect(aura);
            } else if (event instanceof MobEvent.ChangeBlock change) {
                Mob source = findMob(emit.mobId());
                if (WorldTickLoop.residentBlockType(rt.accessor(), change.x(), change.y(), change.z())
                        == change.expectedBlock()) {
                    rt.fluidSim().applyChange(change.x(), change.y(), change.z(),
                            change.replacementBlock());
                    if (source instanceof Enderman enderman) enderman.commitBlockChange();
                } else if (source instanceof Enderman enderman) {
                    enderman.rejectBlockChange();
                }
            } else if (event instanceof MobEvent.CopperGolemStatue statue) {
                resolveCopperGolemStatue(emit.mobId(), statue);
            } else if (event instanceof MobEvent.ChangeBlockState change) {
                int block = WorldTickLoop.residentBlockType(
                        rt.accessor(), change.x(), change.y(), change.z());
                int state = rt.blockStates().get(
                        change.x(), change.y(), change.z(), change.expectedBlock());
                if (block == change.expectedBlock() && state == change.expectedState()) {
                    rt.setBlockState(change.x(), change.y(), change.z(),
                            change.expectedBlock(), change.replacementState());
                    rt.tickBlockChanges().put(
                            new BlockPos(change.x(), change.y(), change.z()),
                            (short) change.expectedBlock());
                }
            }
        }
        for (MobRuntime.ProjectileHit hit : result.projectileHits()) {
            if (hit.event() instanceof MobEvent.AttackPlayer attack) {
                if (hit.kind() == ProjectileSim.Kind.SNOWBALL
                        || hit.kind() == ProjectileSim.Kind.EGG) continue;
                String killer = attack.shooterNickname() != null
                        ? attack.shooterNickname()
                        : attack.shooterMobType() == null
                                ? "skeleton"
                                : mobTypeName(attack.shooterMobType());
                if (hit.kind() == ProjectileSim.Kind.SMALL_FIREBALL) {
                    // SmallFireball.onHitEntity: igniteForSeconds(5) 뒤 fireball 피해 5(화염 ·
                    // 투사체 → 방어도가 깎고 화염 저항이 막는 in_fire 판정). 피해가 안 들면 불을
                    // 원래대로 되돌린다.
                    PlayerTickState target = rt.players().get(attack.nickname());
                    if (target != null) {
                        int previousFire = target.fireTicks();
                        target.setFireTicks(Math.max(previousFire,
                                ProjectileSim.FIREBALL_FIRE_SECONDS * 10));
                        int before = target.health();
                        damagePlayerFrom(attack.nickname(), attack.damage(), "in_fire",
                                "fireball", attack.sourceX(), attack.sourceZ(), false);
                        if (target.health() >= before) target.setFireTicks(previousFire);
                    }
                    continue;
                }
                PlayerTickState arrowTarget = rt.players().get(attack.nickname());
                // [MOB-EQUIP] 몹이 쏜 활의 화염·밀어내기(바닐라 AbstractArrow.onHitEntity): 불타는 화살은 피해
                // 판정 앞에서 5초 태우고 피해가 거절되면 되돌리며, 밀어내기는 받아들여진 명중에 화살 수평
                // 방향 level·0.6 push(위로 0.1)를 준다. 폭발 충격과 같은 playerHurt 넉백 필드 규약(×20)이다.
                com.gameexpert.engine.enchant.WideEnchantments bow = hit.weaponEnchantments();
                boolean arrowKind = hit.kind() == ProjectileSim.Kind.ARROW;
                int previousFire = arrowTarget == null ? 0 : arrowTarget.fireTicks();
                if (arrowTarget != null && arrowKind && bow.level(EnchantmentRules.FLAME) > 0) {
                    arrowTarget.setFireTicks(Math.max(previousFire, EnvironmentSystem.burningTicks(
                            arrowTarget, (EnchantmentRules.FLAME_TARGET_IGNITE_SECONDS
                                    * EnchantmentRules.MC_TICKS_PER_SECOND + 1) / 2)));
                }
                int healthBefore = arrowTarget == null ? 0 : arrowTarget.health();
                PlayerTickState.DirectionalDamageResult arrowResult = arrowTarget == null
                        ? PlayerTickState.DirectionalDamageResult.REJECTED
                        : damageFromAndSync(arrowTarget, attack.damage(), "arrow", killer,
                                attack.sourceX(), attack.sourceZ(), false);
                if (arrowKind && arrowTarget != null) {
                    if (arrowResult.damaged()) {
                        // [ARROW-GROUND] AbstractArrow.onHitEntity: 관통 없는 화살이 들어가면
                        // LivingEntity.setArrowCount(+1) — 플레이어 몸에 박힌 화살 수.
                        ProjectileSim source = terminatedThisTick.get(hit.projectileId());
                        if (source == null || source.weaponEnchantments.level(
                                com.gameexpert.engine.enchant.EnchantmentRules.PIERCING) <= 0) {
                            arrowTarget.addStuckArrow();
                        }
                    } else if (!arrowTarget.isDead()) {
                        // 방패·무적 틈: hurtOrSimulate == false 라 화살은 되튄다.
                        deflectRefusedArrow(hit.projectileId());
                    }
                }
                if (arrowTarget != null && arrowKind && !bow.isEmpty()) {
                    applyMobArrowPunch(arrowTarget, bow, healthBefore, previousFire, attack);
                }
                if (attack.effect() != null) {
                    applyEffectToPlayer(rt.players().get(attack.nickname()), attack.effect(), 1.0);
                }
            } else if (hit.event() instanceof MobEvent.AttackPlacedEntity attack) {
                rt.placedEntities().hurtByProjectile(attack);
            } else if (hit.event() instanceof MobEvent.AttackMob attack) {
                damageMobFromProjectile(hit.projectileId(), hit.kind(), attack, hit.x(), hit.y(), hit.z());
            } else if (hit.event() instanceof MobEvent.SplashPotion splash) {
                handleSplashPotion(splash);
            } else if (hit.event() instanceof MobEvent.ExperienceBottleHit bottle) {
                awardExperienceBottle(bottle);
            } else if (hit.event() instanceof MobEvent.LingeringPotion lingering) {
                // ThrownLingeringPotion.onHitAsPotion: 착탄 좌표에 효과 구름을 세운다.
                ProjectileSim cloud = ProjectileSim.areaEffectCloud(
                        lingering.shooterNickname(), lingering.x(), lingering.y(), lingering.z(),
                        lingering.effect(), lingering.potionItem());
                cloud.potionKey = lingering.potionKey();
                runtime.addWorldProjectile(cloud);
            } else if (hit.event() instanceof MobEvent.CloudApplications cloud) {
                applyCloudEffects(cloud);
            } else if (hit.event() instanceof MobEvent.OminousItemSpawnerSound sound) {
                // OminousItemSpawner.tickServer: blockPosition 에서 NEUTRAL 예고음.
                trialWorldSound("trial_spawner_about_to_spawn_item",
                        Math.floor(sound.x()) + 0.5, Math.floor(sound.y()) + 0.5,
                        Math.floor(sound.z()) + 0.5, (short) 0);
            } else if (hit.event() instanceof MobEvent.OminousItemSpawnerRelease release) {
                releaseOminousItem(release);
            } else if (hit.event() instanceof MobEvent.SmallFireballBlockHit fire) {
                igniteFireballCell(fire.x(), fire.y(), fire.z());
            } else if (hit.event() instanceof MobEvent.DragonFireballHit fireball) {
                dragonFireballHit(fireball);
            }
        }
    }

    /**
     * [TRIAL-GAP] 효과 구름 적용({@code AreaEffectCloud.serverTick}): 즉발 효과는 배율 0.5
     * ({@code applyInstantaneousEffect(..., 0.5)}), 지속 효과는 구름이 실은 그대로 건다.
     */
    private void applyCloudEffects(MobEvent.CloudApplications cloud) {
        if (cloud.potionKey() != null) {
            // [UTILITY] 물약 키가 있으면 잔류형 배율 0.25 를 곱한 효과 전부를 건다(즉발은 0.5 배).
            for (ProjectileEffect effect
                    : com.gameexpert.engine.effect.PotionCatalog.lingeringEffects(cloud.potionKey())) {
                double factor = effect.effect().instantaneous() ? 0.5 : 1.0;
                for (String nickname : cloud.players()) {
                    applyPotionEffectToPlayer(rt.players().get(nickname), effect, factor, false);
                }
                for (long mobId : cloud.mobIds()) {
                    Mob mob = findMob(mobId);
                    if (mob == null || mob.isDead() || mob.removed) continue;
                    applyPotionEffectToMob(mob, effect, factor, false, cloud.ownerNickname());
                }
            }
            return;
        }
        double factor = cloud.effect().effect().instantaneous() ? 0.5 : 1.0;
        for (String nickname : cloud.players()) {
            applyEffectToPlayer(rt.players().get(nickname), cloud.effect(), factor);
        }
        for (long mobId : cloud.mobIds()) {
            Mob mob = findMob(mobId);
            if (mob == null || mob.isDead() || mob.removed) continue;
            applyEffectToMob(mob, cloud.effect(), factor, cloud.ownerNickname());
        }
    }

    /**
     * [TRIAL-GAP] 불길한 아이템 소환기 {@code spawnItem}: 발사형 아이템이면 그 아이템의
     * {@code DispenseConfig} 로 아래(Direction.DOWN)로 쏘고({@code overrideDispenseEvent} 가 있으면
     * 그 레벨 이벤트를 먼저 낸다), 아니면 아이템 개체로 떨군다. 끝으로 레벨 이벤트 3021(data 1).
     */
    private void releaseOminousItem(MobEvent.OminousItemSpawnerRelease release) {
        short item = release.itemType();
        double x = release.x(), y = release.y(), z = release.z();
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        ProjectileSim projectile = null;
        if (item == PlayerInventory.ARROW || PlayerInventory.isTippedArrow(item)) {
            // ArrowItem: DispenseConfig 기본값 power 1.1 · uncertainty 6.0 → Projectile.shoot.
            // [CONTAINER-MENUS] 활·발사기와 같은 화살 척도(바닐라 × 0.5)로 쏜다.
            double[] v = dispenseShootVector(1.1, 6.0, ProjectileSim.ARROW_SIMULATION_SCALE);
            projectile = ProjectileSim.dispensedArrow(x, y, z, v[0], v[1], v[2],
                    PotionRules.tippedArrowEffect(item),
                    PlayerInventory.isTippedArrow(item) ? item : 0);
        } else if (PlayerInventory.isLingeringPotion(item)) {
            // ThrowablePotionItem: uncertainty × 0.5 = 3.0 · power × 1.25 = 1.375.
            // [CONTAINER-MENUS] 투척 물약 척도는 바닐라 그대로(플레이어 투척 0.5 = 바닐라 0.5)다.
            double[] v = dispenseShootVector(1.375, 3.0, 1.0);
            projectile = ProjectileSim.dispensedLingeringPotion(x, y, z, v[0], v[1], v[2],
                    PotionRules.lingeringEffect(item), item);
        } else if (item == PlayerInventory.FIRE_CHARGE) {
            // FireChargeItem.asProjectile: 방향 triangle(step, 0.11485) 을 정규화, 가속 0.1.
            // shoot 는 비어 있어 속도는 방향 × 0.1(MC 틱)이다. overrideDispenseEvent 1018.
            levelEvent(1018, bx, by, bz, 0);
            double[] d = dispenseTriangleDirection();
            double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            double scale = ProjectileSim.FIREBALL_ACCELERATION * 2.0 / Math.max(1e-12, length);
            projectile = ProjectileSim.dispensedSmallFireball(x, y, z,
                    d[0] * scale, d[1] * scale, d[2] * scale);
        } else if (item == PlayerInventory.WIND_CHARGE) {
            // WindChargeItem.asProjectile: setDeltaMovement(triangle 방향) 그대로(MC 틱). 1051.
            levelEvent(1051, bx, by, bz, 0);
            double[] d = dispenseTriangleDirection();
            projectile = ProjectileSim.fromPlayerWindCharge(null, x, y, z,
                    d[0] * 2.0, d[1] * 2.0, d[2] * 2.0);
            projectile.noDeflectMcTicks = 0;
        }
        if (projectile != null) {
            runtime.addWorldProjectile(projectile);
        } else {
            rt.itemSystem().spawnDrop(item, release.count(), x, y, z);
        }
        levelEvent(3021, bx, by, bz, 1);
    }

    /**
     * 바닐라 {@code Projectile.getMovementToShoot} 를 아래 방향(0, −1, 0)에 적용한다.
     * [CONTAINER-MENUS] 결과는 그 종류의 이 저장소 시뮬레이션 척도({@code simulationScale}: 화살
     * 0.5, 투척 물약 1.0 — 플레이어 투척·발사기와 같은 척도)를 곱한 속도다. 예전의 × 2(권위 틱당
     * 변위) 환산은 같은 화살을 활·발사기보다 네 배 빠르게 날렸다.
     */
    private double[] dispenseShootVector(double power, double uncertainty, double simulationScale) {
        double deviation = ProjectileSim.INACCURACY_NOISE_SCALE * uncertainty;
        double dx = nextTriangle(deviation);
        double dy = -1.0 + nextTriangle(deviation);
        double dz = nextTriangle(deviation);
        double speed = power * simulationScale;
        return new double[] {dx * speed, dy * speed, dz * speed};
    }

    /** {@code triangle(stepX, 0.11485) · triangle(stepY, …) · triangle(stepZ, …)}, Direction.DOWN. */
    private double[] dispenseTriangleDirection() {
        return new double[] {nextTriangle(0.11485), -1.0 + nextTriangle(0.11485),
                nextTriangle(0.11485)};
    }

    /**
     * [TRIAL-GAP] 작은 화염구 블록 명중({@code SmallFireball.onHitBlock}): 주인이 몹이 아니면
     * (mobGriefing 을 묻지 않고) 맞은 면 바깥 칸이 비었을 때 불을 놓는다.
     */
    private void igniteFireballCell(int x, int y, int z) {
        rt.tickLoop().igniteEmptyCell(x, y, z);
    }

    private void resolveCopperGolemStatue(long mobId, MobEvent.CopperGolemStatue statue) {
        Mob source = findMob(mobId);
        if (!(source instanceof com.gameexpert.engine.mob.CopperGolem)) return;
        if (WorldTickLoop.residentBlockType(rt.accessor(), statue.x(), statue.y(), statue.z())
                != Fluids.AIR) {
            runtime.rejectCopperGolemStatue(mobId);
            return;
        }
        MobPersistenceSnapshot snapshot = persistenceSnapshot(source);
        if (snapshot == null) return;
        String key = "copper-golem-statue:" + mobId;
        pendingCopperStatueSettlements.putIfAbsent(key,
                new CopperStatueSettlementWork(mobId, statue, snapshot));
    }

    private void pumpAsyncAnimalSettlements() {
        pumpFrogConversionPersistence();
        for (var entry : List.copyOf(pendingCopperStatueSettlements.entrySet())) {
            String key = entry.getKey();
            CopperStatueSettlementWork work = entry.getValue();
            if (animalSettlementInFlight.add(key)) {
                Runnable transaction = () -> {
                    com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                            .CopperStatueIntent intent = null;
                    boolean committed = false;
                    try {
                        intent = rt.animalSettlements().beginCopperGolemStatue(rt.worldId(),
                                work.mobId(), work.statue().x(), work.statue().y(),
                                work.statue().z(), work.statue().state());
                        committed = rt.animalSettlements().commitCopperGolemStatue(
                                rt.worldId(), intent, work.snapshot());
                    } catch (RuntimeException | Error failure) {
                        committed = false;
                    }
                    var durableIntent = intent;
                    boolean durable = committed;
                    rt.enqueuePersistenceCompletion(() -> {
                        animalSettlementInFlight.remove(key);
                        if (!durable || durableIntent == null) return;
                        rt.fluidSim().applyChange(work.statue().x(), work.statue().y(),
                                work.statue().z(), Blocks.OXIDIZED_COPPER_GOLEM_STATUE);
                        rt.setBlockState(work.statue().x(), work.statue().y(), work.statue().z(),
                                Blocks.OXIDIZED_COPPER_GOLEM_STATUE, work.statue().state());
                        rt.tickBlockChanges().put(new BlockPos(work.statue().x(),
                                work.statue().y(), work.statue().z()),
                                (short) Blocks.OXIDIZED_COPPER_GOLEM_STATUE);
                        runtime.confirmCopperGolemStatue(work.mobId());
                        pendingDespawnReasons.put(work.mobId(), "statue");
                        pendingCopperStatueSettlements.remove(key);
                        submitAnimalRetirement(durableIntent.key());
                    });
                };
                rt.submitAnimalSettlementPersistence(transaction,
                        () -> animalSettlementInFlight.remove(key));
            }
        }
        for (MobDeathSettlementWork work : List.copyOf(pendingMobDeathSettlements.values())) {
            submitMobDeathSettlement(work);
        }
        for (MobDeathPlan plan : List.copyOf(pendingMobDeathPlans.values())) {
            materializeMobDeathPlan(plan);
        }
    }

    void retryAnimalSettlementPersistenceForDisposal() {
        pumpAsyncAnimalSettlements();
    }

    boolean hasPendingAnimalSettlementPersistence() {
        return runtime.hasPendingFrogConversions()
                || !pendingFrogConversionCommits.isEmpty()
                || !pendingCopperStatueSettlements.isEmpty()
                || !pendingMobDeathPlans.isEmpty()
                || !pendingMobDeathSettlements.isEmpty()
                || !animalSettlementInFlight.isEmpty();
    }

    private void pumpFrogConversionPersistence() {
        if (frogColonyPersistence == null) return;
        for (MobRuntime.FrogConversionWork work : runtime.pendingFrogConversions()) {
            String key = frogConversionKey("apply", work.sourceMobId(), work.sequence());
            if (animalSettlementInFlight.contains(key)) continue;
            FrogConversionIntent recovered = work.recoveredIntent();
            if (recovered != null
                    && (recovered.getPhase() == FrogConversionIntent.Phase.APPLIED
                            || recovered.getPhase() == FrogConversionIntent.Phase.COMMITTED)) {
                completeAppliedFrogConversion(work, recovered);
                continue;
            }
            if (!animalSettlementInFlight.add(key)) continue;
            Runnable transaction = () -> {
                FrogApplyOutcome outcome;
                try {
                    FrogConversionIntent intent = recovered;
                    if (intent == null) {
                        var begun = frogColonyPersistence.begin(rt.worldId(),
                                work.sourceMobId(), work.sequence(),
                                work.colonyX(), work.colonyY(), work.colonyZ(),
                                work.hostX(), work.hostY(), work.hostZ(),
                                work.deterministicVariant(), work.currentMcTick());
                        if (begun.isEmpty()) {
                            outcome = new FrogApplyOutcome(null, true, false);
                            FrogApplyOutcome result = outcome;
                            rt.enqueuePersistenceCompletion(() ->
                                    completeFrogApply(key, work, result));
                            return;
                        }
                        intent = begun.get();
                    }
                    boolean applied = intent.getPhase() != FrogConversionIntent.Phase.PENDING
                            || work.replacementSnapshot() != null
                            && frogColonyPersistence.markApplied(rt.worldId(),
                                    work.sourceMobId(), work.sequence(),
                                    work.replacementSnapshot());
                    FrogConversionIntent appliedIntent = applied
                            ? withFrogPhase(intent, FrogConversionIntent.Phase.APPLIED) : intent;
                    outcome = new FrogApplyOutcome(appliedIntent, false, applied);
                } catch (RuntimeException | Error failure) {
                    outcome = new FrogApplyOutcome(null, false, false);
                }
                FrogApplyOutcome result = outcome;
                rt.enqueuePersistenceCompletion(() -> completeFrogApply(key, work, result));
            };
            rt.submitAnimalSettlementPersistence(transaction,
                    () -> animalSettlementInFlight.remove(key));
        }
        for (var entry : List.copyOf(pendingFrogConversionCommits.entrySet())) {
            String key = entry.getKey();
            FrogConversionIntent intent = entry.getValue();
            if (!animalSettlementInFlight.add(key)) continue;
            Runnable transaction = () -> {
                boolean committed;
                try {
                    committed = frogColonyPersistence.commit(rt.worldId(),
                            intent.getSourceMobId(), intent.getSequence());
                } catch (RuntimeException | Error failure) {
                    committed = false;
                }
                boolean durable = committed;
                rt.enqueuePersistenceCompletion(() -> {
                    animalSettlementInFlight.remove(key);
                    if (durable) pendingFrogConversionCommits.remove(key, intent);
                });
            };
            rt.submitAnimalSettlementPersistence(transaction,
                    () -> animalSettlementInFlight.remove(key));
        }
    }

    private void completeFrogApply(String key, MobRuntime.FrogConversionWork work,
            FrogApplyOutcome outcome) {
        animalSettlementInFlight.remove(key);
        if (outcome.denied()) {
            runtime.rejectFrogConversion(work);
            return;
        }
        if (!outcome.applied() || outcome.intent() == null) return;
        completeAppliedFrogConversion(work, outcome.intent());
    }

    private void completeAppliedFrogConversion(MobRuntime.FrogConversionWork work,
            FrogConversionIntent intent) {
        MobRuntime.FrogConversionInstallResult installed =
                runtime.installAppliedFrogConversion(work, intent);
        if (installed == MobRuntime.FrogConversionInstallResult.INSTALLED) {
            Mob replacement = findMob(work.sourceMobId());
            if (replacement != null && !replacement.isDead() && !replacement.removed) {
                MobSpawnDto transformed = spawnDto(replacement);
                welcomeMobState.put(replacement.id, transformed);
                lastMobState.put(replacement.id, MobBroadcastState.capture(replacement));
                broadcast(new WsMessages.MobTransform(nextEventId(), transformed));
                publishSnapshot();
            }
        }
        if (intent.getPhase() != FrogConversionIntent.Phase.COMMITTED) {
            pendingFrogConversionCommits.putIfAbsent(
                    frogConversionKey("commit", intent.getSourceMobId(), intent.getSequence()),
                    intent);
        }
    }

    private static FrogConversionIntent withFrogPhase(
            FrogConversionIntent source, FrogConversionIntent.Phase phase) {
        if (source.getPhase() == FrogConversionIntent.Phase.COMMITTED) return source;
        return new FrogConversionIntent(source.getSourceMobId(), source.getSequence(),
                source.getColonyX(), source.getColonyY(), source.getColonyZ(),
                source.getHostX(), source.getHostY(), source.getHostZ(),
                source.getDeterministicVariant(), phase);
    }

    private static String frogConversionKey(String phase, long mobId, long sequence) {
        return "frog-conversion:" + phase + ':' + mobId + ':' + sequence;
    }

    private void submitAnimalRetirement(String settlementKey) {
        rt.submitAnimalSettlementPersistence(() -> {
            try {
                rt.animalSettlements().retireCompleted(rt.worldId(), settlementKey);
            } catch (RuntimeException | Error ignored) {
                rt.enqueuePersistenceCompletion(() -> submitAnimalRetirement(settlementKey));
            }
        }, () -> rt.enqueuePersistenceCompletion(() -> submitAnimalRetirement(settlementKey)));
    }

    /** Resolves one core return request into inventory or one non-recollectable fallback drop. */
    private void resolveAllayReturn(long allayId, MobEvent.AllayReturnItem returned) {
        Mob allay = runtime.mobById(allayId);
        if (allay == null || allay.type != MobType.ALLAY) return;
        PlayerTickState player = rt.players().get(returned.nickname());
        int inserted = player == null || player.isDead() ? 0 : player.inventory().addItem(
                returned.itemType(), returned.count(), returned.durability());
        if (inserted > 0) {
            if (!runtime.confirmAllayReturn(allayId, inserted)) {
                throw new IllegalStateException("Allay return event lost its outstanding request");
            }
            sendInventory(player);
            rt.itemSystem().broadcastAllayDelivery(allay.x, allay.y, allay.z);
            return;
        }
        if (!runtime.confirmAllayReturn(allayId, returned.count())) return;
        rt.itemSystem().spawnAllayReturnDrop(allayId, returned.itemType(), returned.count(),
                returned.durability(), allay.x, allay.y, allay.z);
    }

    /** Resolves scalar social intents only after the complete active AI pass. */
    /**
     * [SPEAR-MOB] 창 돌진 goal 이 창을 쓰게 한 몹마다 이번 권위 틱의 {@code KineticWeapon.damageEntities}(속도 배율 0.2, 기본
     * 공격력은 그 종의 ATTACK_DAMAGE 속성 기본값). 대상은 살아 있는 플레이어와 몹(자기 · 같은 탈것 · 아이템 액자 · 엔드
     * 수정 · 드래곤 제외)이고, 한 대상이라도 찔렀으면 {@code broadcastEntityEvent(mob, 2)} 의 창 명중음을 몹 자리에서 낸다.
     */
    private void resolveMobSpearKinetics(List<Mob> active) {
        for (Mob attacker : List.copyOf(active)) {
            com.gameexpert.engine.mob.SpearUseAi.State use = attacker.spearUseState();
            if (use == null || !use.using() || attacker.isDead() || attacker.removed) continue;
            SpearRules.Kinetic kinetic = SpearRules.kinetic(attacker.heldItem());
            if (kinetic == null) continue;
            double[] look = attacker.spearLook();
            double[] move = attacker.consumeSpearMovement();
            long elapsed = Math.max(0L, runtime.worldTick() * 2L - use.useStartMcTick());
            MobKinetic.Attacker source = new MobKinetic.Attacker(attacker.x, attacker.y, attacker.z,
                    attacker.y + attacker.eyeHeight(), look[0], look[1], look[2], move[0], move[1], move[2]);
            List<MobKinetic.Target> targets = new ArrayList<>();
            for (PlayerTickState player : rt.players().values()) {
                if (player.isDead()) continue;
                double height = player.crouching() ? 1.5 : 1.8;
                targets.add(new MobKinetic.Target("p:" + player.nickname(), new double[][] {{
                        player.x() - 0.3, player.y(), player.z() - 0.3, player.x() + 0.3, player.y() + height,
                        player.z() + 0.3}}, 0.0, 0.0, 0.0));
            }
            for (Mob target : active) {
                if (target == attacker || target.isDead() || target.removed || target.type == MobType.ITEM_FRAME
                        || target.type == MobType.END_CRYSTAL || target.type == MobType.ENDER_DRAGON) continue;
                if (sameVehicle(attacker, target)) continue;
                double half = target.width() / 2.0;
                targets.add(new MobKinetic.Target("m:" + target.id, new double[][] {{target.x - half, target.y,
                        target.z - half, target.x + half, target.y + target.height(), target.z + half}},
                        target.horizontalVx, target.vy, target.horizontalVz));
            }
            List<MobKinetic.Stab> stabs = MobKinetic.evaluate(kinetic, elapsed, attacker.attackDamageAttributeBase(),
                    MobKinetic.MOB_SPEED_FACTOR, source, targets, world::hasLineOfSight, attacker.spearStabbedAt());
            boolean landed = false;
            for (MobKinetic.Stab stab : stabs) {
                if (stab.key().startsWith("p:")) {
                    PlayerTickState player = rt.players().get(stab.key().substring(2));
                    if (player != null && !player.isDead()) landed |= mobStabPlayer(attacker, player, stab);
                } else {
                    Mob target = findMob(Long.parseLong(stab.key().substring(2)));
                    if (target != null && !target.isDead() && !target.removed) landed |= mobStabMob(attacker, target, stab);
                }
                if (attacker.isDead()) break;
            }
            if (landed) {
                worldSound(SpearRules.soundKind(attacker.heldItem(), "hit"), attacker.x, attacker.y, attacker.z, (short) 0);
            }
        }
    }

    /** [SPEAR-MOB] {@code isPassengerOfSameVehicle}: 한쪽이 다른 쪽의 탈것이거나 같은 탈것에 탔다. */
    private static boolean sameVehicle(Mob a, Mob b) {
        return a.vehicleMobId() == b.id || b.vehicleMobId() == a.id
                || (a.vehicleMobId() != 0 && a.vehicleMobId() == b.vehicleMobId())
                || (a.placedVehicleId() != 0 && a.placedVehicleId() == b.placedVehicleId());
    }

    /**
     * [SPEAR-MOB] 몹의 {@code LivingEntity.stabAttack(slot, player, …)}: 피해(인챈트 · 나약 · 난이도) → 시선 방향 0.4 넉백(창 피해
     * 유형은 #no_knockback 이라 피격 넉백은 없다) → 하마(보트 · 쿠션 · 몹 좌석). 셋 다 없으면 명중이 아니다.
     */
    private boolean mobStabPlayer(Mob attacker, PlayerTickState player, MobKinetic.Stab stab) {
        boolean hurt = false;
        if (stab.dealsDamage()) {
            int damage = attacker.kineticPlayerDamage(world, stab.amount());
            PlayerTickState.DirectionalDamageResult result = damageFromAndSync(player, damage, "mob",
                    typeName(attacker), attacker.x, attacker.z, false);
            hurt = result.damaged();
            if (hurt) {
                applyThorns(player, attacker);
                int fireAspect = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.FIRE_ASPECT);
                if (fireAspect > 0) {
                    player.setFireTicks(Math.max(player.fireTicks(), EnvironmentSystem.burningTicks(player,
                            (EnchantmentRules.fireAspectIgniteMcTicks(fireAspect) + 1) / 2)));
                }
                runtime.rememberOwnerHurtByMob(player.nickname(), attacker.id);
            }
        }
        if (stab.knockback() && !player.isDead()) applyMobStabKnockback(player, attacker);
        boolean dismounted = false;
        if (stab.dismount()) {
            dismounted = dismountMob(player.nickname()) || rt.boatSystem().forceLeave(player.nickname())
                    || rt.cushionSystem().forceLeave(player.nickname());
        }
        return hurt || stab.knockback() || dismounted;
    }

    /** [SPEAR-MOB] 몹의 {@code stabAttack(slot, mob, …)}: 피해(대상별 인챈트) → 하마 → 시선 방향 넉백. */
    private boolean mobStabMob(Mob attacker, Mob target, MobKinetic.Stab stab) {
        boolean hurt = false;
        float amount = (float) attacker.kineticMobDamage(stab.amount(), target.type);
        if (stab.dealsDamage()) hurt = target.damage(amount, runtime.worldTick());
        boolean dismounted = false;
        if (stab.dismount()) dismounted = target.dismountAuthoritativeVehicle();
        if (stab.knockback() && !target.isDead()) {
            int cubeHits = CombatSystem.applyFacingStabKnockback(target, Math.cos(attacker.yaw), Math.sin(attacker.yaw),
                    CombatSystem.SulfurAttacker.of(attacker, target), amount,
                    attacker.heldWeaponEnchantmentLevel(EnchantmentRules.KNOCKBACK));
            for (int hit = 0; hit < cubeHits; hit++) mobSound(target, "hit");
        }
        if (!hurt && !stab.knockback() && !dismounted) return false;
        if (hurt) {
            String ownerCredit = mobKillPlayerCreditNickname(attacker);
            if (ownerCredit != null) target.rememberPlayerKillCredit(ownerCredit);
            if (!target.isDead()) target.onHurt(null, attacker.x, attacker.z);
            broadcast(new WsMessages.MobHurt(target.id, target.isDead()));
            if (target.isDead() && !mobDropsHandled.contains(target.id)) {
                pendingDespawnReasons.put(target.id, "death");
                runtime.splitSlimeOnDeath(target, rng);
                spawnMobDrops(target, ownerCredit != null);
                mobDropsHandled.add(target.id);
            }
        }
        return true;
    }

    private void resolveDirectMobAttack(long attackerId, MobEvent.AttackMob attack,
                                        List<Mob> activeMobs) {
        Mob attacker = findMob(attackerId);
        Mob target = findMob(attack.mobId());
        if (attacker == null || target == null
                || attacker.isDead() || attacker.removed
                || target.isDead() || target.removed
                || !isActiveMob(activeMobs, attacker) || !isActiveMob(activeMobs, target)
                || attacker.hasPlayerTarget()
                || !MobRelationshipPolicy.mayDirectlyTarget(attacker, target)
                || !MobRelationshipPolicy.inTargetRange(attacker, target)
                || !MobRelationshipPolicy.inAttackReach(attacker, target)
                || !MobRelationshipPolicy.hasLineOfSight(world, attacker, target)) return;
        boolean ravagerAttack = attacker instanceof Ravager;
        if (ravagerAttack) {
            attacker.commitAcceptedMeleeAttack();
            mobSound(attacker, "attack");
        }
        boolean groundedBefore = target.onGround;
        if (!target.damage(attack.damage(), runtime.worldTick())) return;
        String ownerCredit = mobKillPlayerCreditNickname(attacker);
        if (ownerCredit != null) {
            target.rememberPlayerKillCredit(ownerCredit);
        }
        // [SULFUR-KB] 몸통 블록을 삼킨 유황 큐브는 hurtServer 의 피격 넉백(dealDefaultKnockback: 0.4, 가해자 −
        // 대상, 피해)이 SulfurCube.knockback 이다 — 플레이어 근접과 같은 식을 몹 가해자의 눈·시선으로 돌린다.
        if (target instanceof com.gameexpert.engine.mob.SulfurCube cube && cube.hasBodyItem()
                && CombatSystem.applySulfurCubeKnockback(cube, CombatSystem.SulfurAttacker.of(attacker, target),
                        (float) attack.damage(), 0.4000000059604645,
                        attacker.x - target.x, attacker.z - target.z, false)) {
            mobSound(cube, "hit");
        }
        applyMobWeaponPostAttack(attacker, target, groundedBefore, attack.damage());

        if (!target.isDead()) {
            if (attack.effect() != null) applyEffectToMob(target, attack.effect(), 1.0);
            target.onHurt(null, attacker.x, attacker.z);
            // [EC-MOBS] 셜커의 HurtByTargetGoal 입력: 가해 몹(다른 셜커 제외)을 기억한다.
            if (target instanceof com.gameexpert.engine.mob.Shulker shulker) shulker.hurtByMob(attacker);
        }
        if (!ravagerAttack) {
            attacker.commitAcceptedMeleeAttack();
            mobSound(attacker, "attack");
        }
        broadcast(new WsMessages.MobHurt(target.id, target.isDead()));
        if (target.isDead()) {
            pendingDespawnReasons.put(target.id, "death");
            runtime.splitSlimeOnDeath(target, rng);
            spawnMobDrops(target, ownerCredit != null);
            mobDropsHandled.add(target.id);
        }
    }

    /**
     * [MOB-EQUIP] 몹 근접이 몹을 맞힌 뒤 무기 인챈트(바닐라 {@code Mob.doHurtTarget}): 밀치기
     * {@code knockback(0.5·level × (1 - 저항), 시선)}, 발화 {@code 4·level} 초 점화, 살충은 절지류에 구속 IV
     * (지속 {@code 1.5..1.5+0.5·(level-1)} 초). 플레이어 근접({@code CombatSystem}) 과 같은 규칙 함수를 쓴다.
     */
    private void applyMobWeaponPostAttack(Mob attacker, Mob target, boolean grounded, double damage) {
        int knockback = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.KNOCKBACK);
        if (knockback > 0 && !target.isDead() && target instanceof com.gameexpert.engine.mob.SulfurCube cube
                && cube.hasBodyItem()) {
            // [SULFUR-KB] Mob.doHurtTarget 의 causeExtraKnockback: knockback(0.5·level, sin(yRot), −cos(yRot),
            // 피해, extra) 도 큐브 식이다. 이 저장소 몹 yaw 의 시선은 (cos, sin) 이라 비율은 그 반대 방향이다.
            if (CombatSystem.applySulfurCubeKnockback(cube, CombatSystem.SulfurAttacker.of(attacker, target),
                    (float) damage, knockback * 0.5,
                    -Math.cos(attacker.yaw), -Math.sin(attacker.yaw), true)) {
                mobSound(cube, "hit");
            }
        } else if (knockback > 0 && !target.isDead() && target.knockbackResistance() < 1.0) {
            double strength = CombatRules.KNOCKBACK_BONUS_BPS / 10.0 * knockback
                    * Math.max(0.0, 1.0 - target.knockbackResistance());
            target.applyKnockback(Math.cos(attacker.yaw) * strength,
                    Math.sin(attacker.yaw) * strength, grounded, 0.0, 0.0);
        }
        int fireAspect = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.FIRE_ASPECT);
        if (fireAspect > 0 && !target.fireImmune()) {
            target.igniteForTicks((EnchantmentRules.fireAspectIgniteMcTicks(fireAspect) + 1) / 2);
        }
        int bane = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.BANE_OF_ARTHROPODS);
        if (bane > 0 && EnchantmentRules.isArthropod(target.type) && !target.isDead()) {
            target.statusEffects().applyMcTicks(com.gameexpert.engine.effect.StatusEffect.SLOWNESS,
                    EnchantmentRules.BANE_SLOWNESS_AMPLIFIER,
                    EnchantmentRules.baneSlownessMcTicks(bane, rng.nextFloat()));
        }
    }

    /** Vanilla credits a tame wolf's victim to a player for XP and killed-by-player loot. */
    static boolean mobKillGetsPlayerCredit(Mob attacker) {
        return mobKillPlayerCreditNickname(attacker) != null;
    }

    private static String mobKillPlayerCreditNickname(Mob attacker) {
        return attacker != null && attacker.type == MobType.WOLF
                ? attacker.ownerNickname() : null;
    }

    /**
     * [GUARDIAN] 반경 안의 살아 있는 플레이어 전원에게 같은 효과를 한 번 부여한다.
     * 바닐라 {@code MobEffectUtil.addEffectToPlayersAround} 와 같이 <b>거리 감쇠가 없고</b>
     * 구면 거리로만 판정한다(투척 물약의 감쇠와 다르다).
     */
    private void resolveAreaStatusEffect(MobEvent.AreaStatusEffect aura) {
        double radiusSq = aura.radius() * aura.radius();
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double dx = player.x() - aura.x();
            double dy = player.y() - aura.y();
            double dz = player.z() - aura.z();
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            applyEffectToPlayer(player, aura.effect(), 1.0);
        }
    }

    private void resolveRavagerRoar(long ravagerId, MobEvent.RavagerRoar roar,
                                    List<Mob> activeMobs, Set<Long> removedIds) {
        Mob source = findMob(ravagerId);
        if (!(source instanceof Ravager) || source.isDead()) return;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead() || !Ravager.withinRoarAabb(
                    source.x, source.y, source.z, source.width(), source.height(), roar.radius(),
                    player.x(), player.y(), player.z(), 0.6,
                    player.crouching() ? 1.5 : 1.8)) continue;
            damagePlayerFrom(player.nickname(), roar.damage(), "mob", "ravager",
                    roar.x(), roar.z(), false);
        }
        for (Mob target : activeMobs) {
            if (target == source || target.isDead() || target.removed
                    || !Ravager.withinRoarAabb(
                            source.x, source.y, source.z, source.width(), source.height(),
                            roar.radius(), target.x, target.y, target.z,
                            target.width(), target.height())) continue;
            double dx = target.x - roar.x();
            double dz = target.z - roar.z();
            boolean damaged = !MobRelationshipPolicy.isRaiderFaction(target.type)
                    && target.damage(roar.damage(), runtime.worldTick());
            if (!target.isDead()) {
                target.applyExplosionKnockback(
                        Ravager.strongKnockbackPerAuthorityTick(dx, dx, dz),
                        Ravager.strongKnockbackVerticalPerAuthorityTick(),
                        Ravager.strongKnockbackPerAuthorityTick(dz, dx, dz));
            }
            if (!damaged) continue;
            broadcast(new WsMessages.MobHurt(target.id, target.isDead()));
            if (target.isDead()) {
                pendingDespawnReasons.put(target.id, "death");
                runtime.splitSlimeOnDeath(target, rng);
                spawnMobDrops(target, false);
                runtime.removeResolvedDeadMob(target);
                removedIds.add(target.id);
            }
        }
    }

    private static boolean isActiveMob(List<Mob> activeMobs, Mob candidate) {
        if (activeMobs == null || candidate == null) return false;
        for (Mob mob : activeMobs) if (mob == candidate) return true;
        return false;
    }

    /**
     * 투척 물약 착탄: 반경 안의 플레이어·몹에게 거리 감쇠를 적용해 효과를 뿌린다.
     * 반경이 4블록으로 작아 전수 순회로도 O(활성 엔티티)이며 틱 예산 안이다.
     */
    private void handleSplashPotion(MobEvent.SplashPotion splash) {
        if (splash.potionKey() != null) {
            handleCatalogSplash(splash);
            return;
        }
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double factor = MobEffectRules.splashFactor(distanceTo(splash,
                    player.x(), player.y() + 0.9, player.z()));
            if (factor <= 0.0) continue;
            applyEffectToPlayer(player, splash.effect(), factor);
        }
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            double factor = MobEffectRules.splashFactor(distanceTo(splash,
                    mob.x, mob.y + mob.height() * 0.5, mob.z));
            if (factor <= 0.0) continue;
            applyEffectToMob(mob, splash.effect(), factor, splash.shooterNickname());
        }
    }

    /**
     * [UTILITY] 물약 키가 실린 투척 물약 착탄({@code ThrownSplashPotion.onHitAsPotion}): 근접도
     * {@code 1 - 거리/4} 를 효과마다 곱한다. 즉발 효과는 {@code HealOrHarmMobEffect} 양, 지속 효과는
     * {@code (int)(근접도 × 지속 + 0.5)}(20 MC 틱 이하는 걸지 않음)다.
     */
    private void handleCatalogSplash(MobEvent.SplashPotion splash) {
        java.util.List<ProjectileEffect> effects =
                com.gameexpert.engine.effect.PotionCatalog.splashEffects(splash.potionKey());
        if (effects.isEmpty()) return;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double factor = MobEffectRules.splashFactor(distanceTo(splash,
                    player.x(), player.y() + 0.9, player.z()));
            if (factor <= 0.0) continue;
            for (ProjectileEffect effect : effects) {
                applyPotionEffectToPlayer(player, effect, factor, true);
            }
        }
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed) continue;
            double factor = MobEffectRules.splashFactor(distanceTo(splash,
                    mob.x, mob.y + mob.height() * 0.5, mob.z));
            if (factor <= 0.0) continue;
            for (ProjectileEffect effect : effects) {
                if (mob.isDead()) break;
                applyPotionEffectToMob(mob, effect, factor, true, splash.shooterNickname());
            }
        }
    }

    /**
     * [UTILITY] 물약 효과 하나를 플레이어에게 건다. 즉발 효과는 바닐라 {@code HealOrHarmMobEffect}
     * 양({@code 4 << amp} 회복 · {@code 6 << amp} 피해에 근접도), 지속 효과는 투척이면 근접도로 줄이고
     * ({@code splash}), 구름이면 실린 지속 그대로 건다.
     */
    private void applyPotionEffectToPlayer(PlayerTickState player, ProjectileEffect effect,
            double factor, boolean splash) {
        if (player == null || player.isDead()) return;
        if (effect.effect() == com.gameexpert.engine.effect.StatusEffect.INSTANT_HEALTH) {
            int amount = com.gameexpert.engine.effect.PotionCatalog.instantHealAmount(
                    effect.amplifier(), factor);
            if (amount > 0) player.heal(amount, "regen");
            return;
        }
        if (effect.effect() == com.gameexpert.engine.effect.StatusEffect.INSTANT_DAMAGE) {
            int amount = com.gameexpert.engine.effect.PotionCatalog.instantDamageAmount(
                    effect.amplifier(), factor);
            if (amount > 0) player.damageMagic(amount);
            return;
        }
        int ticks = splash
                ? com.gameexpert.engine.effect.PotionCatalog.splashScaledServerTicks(
                        effect.durationTicks(), factor)
                : effect.durationTicks();
        if (ticks > 0) player.applyStatusEffect(effect.effect(), effect.amplifier(), ticks);
    }

    /** [UTILITY] 몹에게 물약 효과 하나를 건다(언데드는 즉시 치유·피해가 뒤집힌다). */
    private void applyPotionEffectToMob(Mob mob, ProjectileEffect effect, double factor,
            boolean splash, String shooterNickname) {
        boolean instantHealth =
                effect.effect() == com.gameexpert.engine.effect.StatusEffect.INSTANT_HEALTH;
        boolean instantDamage =
                effect.effect() == com.gameexpert.engine.effect.StatusEffect.INSTANT_DAMAGE;
        if (instantHealth || instantDamage) {
            // HealOrHarmMobEffect: isInvertedHealAndHarm(언데드)이면 치유는 피해, 피해는 치유다.
            boolean harm = instantDamage != com.gameexpert.engine.mob.MobTags.isUndead(mob.type);
            if (!harm) {
                mob.heal(com.gameexpert.engine.effect.PotionCatalog.instantHealAmount(
                        effect.amplifier(), factor));
                return;
            }
            double amount = com.gameexpert.engine.effect.PotionCatalog.instantDamageAmount(
                    effect.amplifier(), factor);
            if (amount <= 0) return;
            applyEffectToMob(mob, new ProjectileEffect(
                    com.gameexpert.engine.effect.StatusEffect.INSTANT_DAMAGE, effect.amplifier(), 0),
                    amount / StatusEffects.instantDamage(effect.amplifier()), shooterNickname);
            return;
        }
        int ticks = splash
                ? com.gameexpert.engine.effect.PotionCatalog.splashScaledServerTicks(
                        effect.durationTicks(), factor)
                : effect.durationTicks();
        if (ticks > 0) mob.applyStatusEffect(effect.effect(), effect.amplifier(), ticks);
    }

    private static double distanceTo(MobEvent.SplashPotion splash, double x, double y, double z) {
        double dx = x - splash.x();
        double dy = y - splash.y();
        double dz = z - splash.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 스플래시 감쇠 factor(1.0=직격)를 적용해 플레이어에게 효과 또는 즉발 피해를 준다. */
    private void applyEffectToPlayer(PlayerTickState player, ProjectileEffect effect, double factor) {
        if (player == null || player.isDead()) return;
        if (effect.effect().instantaneous()) {
            int amount = (int) Math.round(
                    StatusEffects.instantDamage(effect.amplifier()) * factor);
            if (amount > 0) player.damageMagic(amount);
            return;
        }
        ProjectileEffect scaled = effect.scaled(factor);
        if (scaled != null) {
            player.applyStatusEffect(scaled.effect(), scaled.amplifier(), scaled.durationTicks());
        }
    }

    private void applyEffectToMob(Mob mob, ProjectileEffect effect, double factor) {
        applyEffectToMob(mob, effect, factor, null);
    }

    private void applyEffectToMob(Mob mob, ProjectileEffect effect, double factor,
                                  String shooterNickname) {
        if (effect.effect().instantaneous()) {
            double amount = StatusEffects.instantDamage(effect.amplifier()) * factor;
            if (mob.damageMagic(amount)) {
                if (shooterNickname != null) {
                    mob.rememberPlayerKillCredit(shooterNickname);
                }
                broadcast(new WsMessages.MobHurt(mob.id, mob.isDead()));
                if (mob.isDead()) {
                    pendingDespawnReasons.put(mob.id, "death");
                    runtime.splitSlimeOnDeath(mob, rng);
                    spawnMobDrops(mob, shooterNickname != null,
                            lootingLevelOfPlayer(shooterNickname));
                    mobDropsHandled.add(mob.id);
                }
            }
            return;
        }
        ProjectileEffect scaled = effect.scaled(factor);
        if (scaled != null) {
            mob.applyStatusEffect(scaled.effect(), scaled.amplifier(), scaled.durationTicks());
        }
    }

    /** [SULFUR-KB] 투사체를 쏜 개체(DamageSource.getEntity): 살아 있는 플레이어 또는 몹. 없으면 null. */
    private CombatSystem.SulfurAttacker sulfurCubeShooter(MobEvent.AttackMob attack, Mob cube) {
        if (attack.shooterNickname() != null) {
            PlayerTickState player = rt.players().get(attack.shooterNickname());
            return player == null || player.isDead() ? null : CombatSystem.SulfurAttacker.of(player);
        }
        if (attack.shooterMobId() > 0) {
            Mob shooter = findMob(attack.shooterMobId());
            return shooter == null || shooter.isDead() || shooter.removed
                    ? null : CombatSystem.SulfurAttacker.of(shooter, cube);
        }
        return null;
    }

    /** 명중점을 모르는 호출(단위 테스트의 반사 호출): 대상 몸통 중심을 명중점으로 쓴다. */
    private void damageMobFromProjectile(long projectileId, ProjectileSim.Kind kind, MobEvent.AttackMob attack) {
        Mob mob = findMob(attack.mobId());
        if (mob == null) return;
        damageMobFromProjectile(projectileId, kind, attack, mob.x, mob.y + mob.height() * 0.5, mob.z);
    }

    private void damageMobFromProjectile(long projectileId, ProjectileSim.Kind kind,
                                         MobEvent.AttackMob attack, double hitX, double hitY, double hitZ) {
        Mob mob = findMob(attack.mobId());
        if (mob == null) return;
        // [DRAGON] 엔드 수정은 어떤 투사체 명중이든(피해 0 인 눈덩이·달걀 포함) EndCrystal.hurtServer 로 부서진다.
        if (mob.type == MobType.END_CRYSTAL) {
            rt.dragonFight().hurtCrystal(mob.id, false, attack.shooterNickname());
            return;
        }
        // [DRAGON] 드래곤은 맞은 부위(EnderDragonPart)로 두뇌의 hurt(part, …) 에 간다.
        if (mob instanceof EnderDragon dragon) {
            rt.dragonFight().hurtDragonByProjectile(dragon, kind, attack, hitX, hitY, hitZ);
            return;
        }
        if (attack.shooterMobType() != null
                && !MobRelationshipPolicy.mayProjectileDamage(attack.shooterMobType(), mob)) {
            return;
        }
        if (mob instanceof Enderman enderman && !mob.isRidingBoat()
                && !mob.isRidingPlacedVehicle() && !mob.isMobPassenger()) {
            // AbstractArrow ignites before hurt; Enderman's refused-hit side effects do not undo it.
            if (kind == ProjectileSim.Kind.ARROW && attack.weaponEnchantments().level(
                    com.gameexpert.engine.enchant.EnchantmentRules.FLAME) > 0) mob.igniteForTicks(50);
            MobEvent.Teleported teleport = enderman.evadeProjectileEvent(world, rng);
            if (teleport != null) {
                if (kind == ProjectileSim.Kind.ARROW) {
                    // Pre-release 1: failed hurt on Enderman never reverses or consumes the arrow.
                    broadcastTeleport(mob.id, teleport.fromX(), teleport.fromY(),
                            teleport.fromZ(), teleport.toX(), teleport.toY(), teleport.toZ());
                    continueEndermanArrow(projectileId, mob.id);
                    return;
                }
                markArrowEvaded(projectileId, mob.id,
                        teleport.fromX(), teleport.fromY(), teleport.fromZ(),
                        teleport.toX(), teleport.toY(), teleport.toZ());
                return;
            }
            // Unmounted Enderman remains projectile-immune even when all teleport attempts fail.
            if (kind == ProjectileSim.Kind.ARROW) continueEndermanArrow(projectileId, mob.id);
            return;
        }
        // [EC-MOBS] 아이템 액자는 체력이 없다: 투사체도 ItemFrame.hurtServer 의 두 갈래(넣은 아이템만 · 액자째)다.
        if (mob instanceof com.gameexpert.engine.mob.ItemFrame frame) {
            if (kind == ProjectileSim.Kind.SNOWBALL || kind == ProjectileSim.Kind.EGG) return;
            hurtItemFrame(frame, false, attack.shooterNickname());
            return;
        }
        // [EC-MOBS] 닫힌 셜커는 화살류(화살·삼지창)를 튕겨 낸다(Shulker.hurtServer 가 거짓).
        if (mob instanceof com.gameexpert.engine.mob.Shulker shulker && shulker.rejectsArrow()
                && (kind == ProjectileSim.Kind.ARROW || kind == ProjectileSim.Kind.TRIDENT)) {
            return;
        }
        // 바닐라 눈덩이는 Blaze에게만 3 피해를 줍니다. 현재 몹 목록에는 Blaze가 없으므로 충돌만 남깁니다.
        // 달걀은 바닐라에서도 hurt(..., 0.0F) 라 어느 대상에게도 피해가 없습니다.
        if (kind == ProjectileSim.Kind.SNOWBALL || kind == ProjectileSim.Kind.EGG) return;
        if (kind == ProjectileSim.Kind.SMALL_FIREBALL) {
            // [TRIAL-GAP] SmallFireball.onHitEntity: igniteForSeconds(5) 뒤 5 피해. 화염 면역은
            // hurtServer 가 실패해 불도 원래대로 되돌린다.
            if (mob.fireImmune()) return;
            mob.igniteForTicks(ProjectileSim.FIREBALL_FIRE_SECONDS * 10);
        }
        com.gameexpert.engine.enchant.WideEnchantments weapon = attack.weaponEnchantments();
        // [ENCHANT-WIDE] 화염: 26.3 AbstractArrow.onHitEntity 는 피해 판정 **앞에서** 불타는 화살이 대상을
        // 5초 태운다. 탑승 엔더맨도 일반 발화/피해 경로를 사용한다.
        // Enderman 외의 피해 거절은 명중 전 화상 값으로 되돌린다.
        int fireTicksBeforeHit = mob.remainingFireTicks();
        if (kind == ProjectileSim.Kind.ARROW
                && weapon.level(com.gameexpert.engine.enchant.EnchantmentRules.FLAME) > 0
                && !mob.fireImmune()) {
            mob.igniteForTicks((com.gameexpert.engine.enchant.EnchantmentRules
                    .FLAME_TARGET_IGNITE_SECONDS
                    * com.gameexpert.engine.enchant.EnchantmentRules.MC_TICKS_PER_SECOND + 1) / 2);
        }
        // [ENCHANT-WIDE] 찌르기: ThrownTrident.onHitEntity 의 modifyDamage(8) 가 #aquatic 에 2.5·level 을 더한다.
        double projectileDamage = attack.damage();
        if (kind == ProjectileSim.Kind.TRIDENT
                && com.gameexpert.engine.enchant.EnchantmentRules.isAquatic(mob.type)) {
            projectileDamage += com.gameexpert.engine.enchant.EnchantmentRules.typedDamageBonusMilli(
                    weapon.level(com.gameexpert.engine.enchant.EnchantmentRules.IMPALING))
                    / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
        }
        // [MOB-EQUIP] 발사체 보호(#is_projectile)·화염(화염구, #is_fire) EPF 가 몹 방어구 인챈트에도 걸린다.
        if (!mob.damage(projectileDamage, runtime.worldTick(),
                kind == ProjectileSim.Kind.SMALL_FIREBALL ? "in_fire" : "arrow")) {
            if (mob.type != MobType.ENDERMAN) mob.setRemainingFireTicks(fireTicksBeforeHit);
            if (kind == ProjectileSim.Kind.ARROW && !mob.isDead()) {
                if (mob.type == MobType.ENDERMAN) continueEndermanArrow(projectileId, mob.id);
                else deflectRefusedArrow(projectileId);
            }
            return;
        }
        // [SULFUR-KB] 몸통 블록을 삼킨 유황 큐브: hurtServer 의 dealDefaultKnockback 은 투사체면
        // calculateHorizontalHurtKnockbackDirection(= 투사체 속도 x, z)의 반대를 비율로 knockback(0.4, …) 을 부르고,
        // SulfurCube.knockback 은 DamageSource.getEntity()(쏜 개체)의 눈·시선으로 큐브 식을 돌린다. 쏜 개체가 없으면
        // 일반 넉백인데 이 저장소는 투사체 피격 넉백을 두지 않으므로 아무것도 하지 않는다.
        if (mob instanceof com.gameexpert.engine.mob.SulfurCube cube && cube.hasBodyItem()) {
            CombatSystem.SulfurAttacker shooter = sulfurCubeShooter(attack, cube);
            if (shooter != null && CombatSystem.applySulfurCubeKnockback(cube, shooter, (float) projectileDamage,
                    0.4000000059604645, -attack.hitVelocityX(), -attack.hitVelocityZ(), false)) {
                mobSound(cube, "hit");
            }
        }
        // [ENCHANT-WIDE] 밀어내기: AbstractArrow.doKnockback — 화살 수평 속도 방향으로
        // level × 0.6 × (1 - 넉백 저항) 블록/MC틱, 위로 0.1 을 push 한다(10 TPS 속도로는 ×2).
        int punch = kind == ProjectileSim.Kind.ARROW
                ? weapon.level(com.gameexpert.engine.enchant.EnchantmentRules.PUNCH) : 0;
        double horizontalSpeed = Math.hypot(attack.hitVelocityX(), attack.hitVelocityZ());
        if (punch > 0 && horizontalSpeed > 1e-12) {
            double push = com.gameexpert.engine.enchant.EnchantmentRules.punchHorizontalPush(
                    punch, mob.knockbackResistance()) * 2.0;
            if (push > 0.0) {
                mob.applyPush(attack.hitVelocityX() / horizontalSpeed * push,
                        com.gameexpert.engine.enchant.EnchantmentRules.PUNCH_VERTICAL_PUSH * 2.0,
                        attack.hitVelocityZ() / horizontalSpeed * push);
            }
        }
        if (attack.shooterNickname() != null) {
            mob.rememberPlayerKillCredit(attack.shooterNickname());
        }
        // [EC-MOBS] 셜커 탄환에 맞은 셜커는 이번 틱 AI 가 순간이동·복제를 판정한다(Shulker.hitByShulkerBullet).
        if (kind == ProjectileSim.Kind.SHULKER_BULLET
                && mob instanceof com.gameexpert.engine.mob.Shulker shulker) {
            shulker.markShulkerBulletHit();
        }
        // [EC-MOBS] 몹이 쏜 투사체에 맞은 셜커는 그 몹을 가해자로 기억한다(Shulker.hurtByMob, 셜커 탄환은 무시).
        if (!mob.isDead() && attack.shooterMobId() != 0L
                && mob instanceof com.gameexpert.engine.mob.Shulker shulker) {
            Mob shooter = findMob(attack.shooterMobId());
            if (shooter != null && !shooter.isDead() && !shooter.removed) shulker.hurtByMob(shooter);
        }
        if (attack.effect() != null && !mob.isDead()) applyEffectToMob(mob, attack.effect(), 1.0);
        broadcast(new WsMessages.MobHurt(mob.id, mob.isDead()));
        if (mob.isDead()) {
            pendingDespawnReasons.put(mob.id, "death");
            runtime.splitSlimeOnDeath(mob, rng);
            // 화살 처치의 약탈도 바닐라처럼 사수의 현재 주손 무기에서 읽는다(화살은 인챈트를 나르지 않는다).
            spawnMobDrops(mob, attack.shooterNickname() != null,
                    lootingLevelOfPlayer(attack.shooterNickname()));
            // [JUKEBOX] entities/creeper.json 둘째 pool: damage_source_properties{source_entity:
            // #skeletons} 이면 #creeper_drop_music_discs 12장 중 균등 한 장(expand, 가중치 1).
            if (mob.type == MobType.CREEPER && attack.shooterMobType() != null
                    && com.gameexpert.engine.mob.MobTags.isSkeleton(attack.shooterMobType())) {
                var discs = com.gameexpert.engine.jukebox.JukeboxRules.CREEPER_DROP_MUSIC_DISCS;
                rt.itemSystem().spawnMobDrop(discs.get(rng.nextInt(discs.size())), 1,
                        mob.x, mob.y + 0.5, mob.z);
            }
            mobDropsHandled.add(mob.id);
        } else if (attack.shooterNickname() != null
                && !windChargeSuppressesAnger(kind, mob.type)) {
            PlayerTickState shooter = rt.players().get(attack.shooterNickname());
            if (shooter != null) {
                runtime.rememberOwnerAttackedMob(shooter.nickname(), mob.id);
                mob.hurtByPlayer(shooter.nickname(), shooter.x(), shooter.z());
                if (mob.type == MobType.BEE) {
                    angerNearbyBees(mob, shooter.nickname(), shooter.x(), shooter.z());
                }
            }
        }
    }

    /**
     * [EC-MOBS] {@code ItemFrame.hurtServer}: 폭발이 아니고 넣은 아이템이 있으면 그 아이템만 액자 앞(방향 × 0.15)에
     * 떨구고({@code ITEM_FRAME_REMOVE_ITEM}), 아니면 액자째 부서진다({@code ITEM_FRAME_BREAK}, 액자 + 넣은 아이템).
     */
    void hurtItemFrame(com.gameexpert.engine.mob.ItemFrame frame, boolean explosion, String attackerNickname) {
        if (frame.isDead() || frame.removed) return;
        if (frame.hurtFrame(explosion) == com.gameexpert.engine.mob.ItemFrameRules.Hurt.POP_ITEM) {
            com.gameexpert.engine.mob.ItemFrame.FramedItem item = frame.takeItem();
            if (item == null) return;
            rt.itemSystem().spawnDeathDrop(item.itemType(), 1, item.durability(), item.enchantments(), 0, 0,
                    null, item.components(),
                    com.gameexpert.engine.mob.ItemFrameRules.dropX(frame.x, frame.direction()), frame.y,
                    com.gameexpert.engine.mob.ItemFrameRules.dropZ(frame.z, frame.direction()));
            mobSound(frame, "remove_item");
            runtime.refreshPersistenceSnapshot(frame);
            return;
        }
        // 부서지는 소리(ITEM_FRAME_BREAK)는 클라가 액자의 사망 퇴장에서 낸다(두 권위 공통 계약).
        frame.damage(1000.0, runtime.worldTick());
        rt.tickLoop().emitDecorationVibration(
                com.gameexpert.engine.sculk.SculkVibrationRules.Event.ENTITY_DIE,
                frame.x, frame.y, frame.z, attackerNickname);
        if (attackerNickname != null) frame.rememberPlayerKillCredit(attackerNickname);
        broadcast(new WsMessages.MobHurt(frame.id, true));
        pendingDespawnReasons.put(frame.id, "death");
        spawnMobDrops(frame, attackerNickname != null);
        mobDropsHandled.add(frame.id);
    }

    /**
     * [EC-MOBS] {@code HangingEntityItem.useOn}: 누른 칸의 면 바깥 칸에 {@code new ItemFrame(level, pos, face)} 를 세우고
     * {@code survives()} 일 때만 설치음({@code ITEM_FRAME_PLACE})과 함께 더하고 한 개를 소비한다.
     */
    boolean placeItemFrame(PlayerTickState player, PlayerInventory.HandRef hand, int clickedX, int clickedY,
            int clickedZ, int face) {
        if (!com.gameexpert.engine.mob.ItemFrameRules.validDirection(face)) return false;
        int x = clickedX + com.gameexpert.engine.mob.ItemFrameRules.stepX(face);
        int y = clickedY + com.gameexpert.engine.mob.ItemFrameRules.stepY(face);
        int z = clickedZ + com.gameexpert.engine.mob.ItemFrameRules.stepZ(face);
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
        com.gameexpert.engine.mob.ItemFrame probe = new com.gameexpert.engine.mob.ItemFrame(-1L, x, y, z);
        probe.hang(x, y, z, face);
        List<com.gameexpert.engine.mob.ItemFrameRules.Hanging> others = new ArrayList<>();
        for (Mob mob : runtime.mobs()) {
            if (mob instanceof com.gameexpert.engine.mob.ItemFrame frame && !frame.isDead() && !frame.removed) {
                others.add(frame.hanging());
            }
        }
        probe.prepareFramesForPlacement(others);
        if (!probe.survives(world)) return false;
        if (!player.inventory().consumeOne(hand, PlayerInventory.ITEM_FRAME)) return false;
        Mob placed = runtime.addMob(MobType.ITEM_FRAME, probe.x, probe.y, probe.z, true);
        ((com.gameexpert.engine.mob.ItemFrame) placed).hang(x, y, z, face);
        runtime.refreshPersistenceSnapshot(placed);
        // 설치음은 액자 mobSpawn 뒤에 나가야 클라가 소리의 주인을 안다(같은 틱의 스폰 방송 직후 비운다).
        deferredSpawnSounds.add(new DeferredMobSound(placed, "place"));
        return true;
    }

    /**
     * [EC-MOBS] 아이템 액자 우클릭({@code ItemFrame.interact}): 넣은 아이템이 있으면 회전({@code ITEM_FRAME_ROTATE_ITEM}),
     * 비었으면 손의 한 개를 넣는다({@code ITEM_FRAME_ADD_ITEM}). 빈 손으로 빈 액자를 누르면 아무 일도 없다.
     *
     * @return 인벤토리가 바뀌었으면 true
     */
    boolean interactItemFrame(PlayerTickState player, PlayerInventory.HandRef hand,
            com.gameexpert.engine.mob.ItemFrame frame) {
        PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
        boolean handHasItem = held.itemType() != PlayerInventory.EMPTY && held.count() > 0;
        switch (com.gameexpert.engine.mob.ItemFrameRules.interaction(false, frame.hasItem(), handHasItem)) {
            case ROTATE -> {
                frame.rotate();
                mobSound(frame, "rotate_item");
                runtime.refreshPersistenceSnapshot(frame);
                return false;
            }
            case INSERT -> {
                if (!frame.insert(held.itemType(), held.durability(), held.enchantments(),
                        held.itemComponentData())) return false;
                if (!player.inventory().consumeOne(hand, held.itemType())) {
                    frame.takeItem();
                    return false;
                }
                mobSound(frame, "add_item");
                runtime.refreshPersistenceSnapshot(frame);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Pinned 26.3 {@code #no_anger_from_wind_charge} entity-type tag. */
    private static boolean windChargeSuppressesAnger(ProjectileSim.Kind kind, MobType type) {
        if (kind != ProjectileSim.Kind.WIND_CHARGE
                && kind != ProjectileSim.Kind.BREEZE_WIND_CHARGE) return false;
        return type == MobType.BREEZE
                || type == MobType.SKELETON
                || type == MobType.BOGGED
                || type == MobType.STRAY
                || type == MobType.ZOMBIE
                || type == MobType.HUSK
                || type == MobType.SPIDER
                || type == MobType.CAVE_SPIDER
                || type == MobType.SLIME;
    }

    private void angerNearbyBees(Mob source, String nickname, double attackerX, double attackerZ) {
        double radiusSq = 16.0 * 16.0;
        for (Mob candidate : runtime.mobs()) {
            if (candidate.type != MobType.BEE || candidate.isDead() || candidate.removed) continue;
            double dx = candidate.x - source.x;
            double dy = candidate.y - source.y;
            double dz = candidate.z - source.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                candidate.onHurt(nickname, attackerX, attackerZ);
                runtime.refreshPersistenceSnapshot(candidate);
            }
        }
    }

    /**
     * 실버피시의 생존 피격 호출은 공통 damage 훅에서 기록하고, 여기서만 실제 몹을 추가한다.
     * runtime.tick 전에 넣어야 spawned 목록과 WS mobSpawn가 같은 틱에 함께 확정된다.
     */
    /** [TRIAL-GAP] 몹이 사망 순간 지닌 onMobRemoved 효과. */
    private static java.util.Set<StatusEffect> deathTriggeredEffectsOf(Mob mob) {
        java.util.EnumSet<StatusEffect> effects = java.util.EnumSet.noneOf(StatusEffect.class);
        for (StatusEffect effect : new StatusEffect[] {StatusEffect.WIND_CHARGED,
                StatusEffect.WEAVING, StatusEffect.OOZING}) {
            if (mob.statusEffects().has(effect)) effects.add(effect);
        }
        return effects;
    }

    /**
     * [TRIAL-GAP] 플레이어 쪽 효과 훅: 벌레 먹음 피격 굴림과 사망 순간 효과. 몹 쪽은 사망 확정
     * 루프와 {@link #summonInfestedSilverfish} 가 소유한다.
     */
    private void applyPlayerTriggeredEffects() {
        for (PlayerTickState player : rt.players().values()) {
            int hurts = player.consumeInfestedHurts();
            for (int hurt = 0; hurt < hurts; hurt++) {
                // [MACE-B2] 플레이어 시선(getLookAngle): 화살 발사와 같은 yaw/pitch 규약.
                double horizontal = Math.cos(player.pitch());
                rollInfestedSilverfish(player.x(), player.y() + ProjectileSim.PLAYER_HEIGHT / 2.0,
                        player.z(), -Math.sin(player.yaw()) * horizontal, Math.sin(player.pitch()),
                        -Math.cos(player.yaw()) * horizontal);
            }
            java.util.Set<StatusEffect> effects = player.consumeDeathTriggeredEffects();
            if (!effects.isEmpty()) {
                applyDeathTriggeredEffects(player.x(), player.y(), player.z(),
                        ProjectileSim.PLAYER_HEIGHT, 0.6, effects);
            }
        }
    }

    /** [TRIAL-GAP] 몹 쪽 벌레 먹음 피격 굴림. */
    private void summonInfestedSilverfish() {
        for (Mob mob : List.copyOf(runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()))) {
            int hurts = mob.consumeInfestedHurts();
            for (int hurt = 0; hurt < hurts; hurt++) {
                // [MACE-B2] 몹 시선: 몸통 yaw(atan2 규약), 머리 기울기 없음.
                rollInfestedSilverfish(mob.x, mob.y + mob.height() / 2.0, mob.z,
                        Math.cos(mob.yaw), 0.0, Math.sin(mob.yaw));
            }
        }
    }

    /**
     * [TRIAL-GAP] {@code InfestedMobEffect.onMobHurt}: nextFloat() ≤ 0.1 이면
     * {@code randomBetweenInclusive(1, 2)} 마리 좀벌레를 몸 중앙(발 + 키/2)에 세운다. [MACE-B2] 마리마다
     * 바닐라 {@code spawnSilverfish} 처럼 시선 방향 0.3 × (1, 1.5, 1) 을 ±90° 무작위로 돌린 초속도로 밀고
     * ({@link com.gameexpert.engine.effect.InfestedSilverfishRules}), {@code nextFloat() × 360} 방향을 보게
     * 하며, 좀벌레 피격음을 낸다.
     */
    private void rollInfestedSilverfish(double x, double y, double z,
            double lookX, double lookY, double lookZ) {
        if (rng.nextFloat() > 0.1f) return;
        int count = 1 + rng.nextInt(2);
        for (int index = 0; index < count; index++) {
            float angle = com.gameexpert.engine.effect.InfestedSilverfishRules.angle(rng.nextFloat());
            double[] velocity = com.gameexpert.engine.effect.InfestedSilverfishRules.velocity(
                    lookX, lookY, lookZ, angle);
            float yawDegrees = com.gameexpert.engine.effect.InfestedSilverfishRules.yawDegrees(
                    rng.nextFloat());
            Mob silverfish = runtime.addMob(MobType.SILVERFISH, x, y, z);
            if (silverfish == null) continue;
            // 바닐라 yRot(도)의 전방 (−sin r, cos r) 을 이 저장소 yaw(atan2 규약)로 옮긴다.
            silverfish.yaw = Math.toRadians(yawDegrees) + Math.PI / 2.0;
            // setDeltaMovement(블록/MC틱) → 권위 틱(2 MC틱) 단위. 새 개체라 기존 속도는 0 이다.
            silverfish.applyFishingPull(velocity[0] * 2.0, velocity[1] * 2.0, velocity[2] * 2.0);
            worldSound("silverfish_hurt", x, y, z, (short) 0);
        }
    }

    /**
     * [TRIAL-GAP] 사망 순간 효과(바닐라 {@code MobEffect.onMobRemoved}, RemovalReason.KILLED).
     * <ul>
     *   <li>돌풍 충전: 몸 중앙에서 반경 {@code 3 + nextFloat × 2} 의 돌풍 폭발(바람 탄환 계산기,
     *       넉백 배율 1.0).</li>
     *   <li>거미줄: 발 칸 둘레 {@code randomInCube(15, pos, 1)} 에서 교체 가능하고 아래 윗면이
     *       sturdy 한 칸을 {@code randomBetweenInclusive(2, 3)} 개까지 골라 거미줄 + 레벨 이벤트 3018.
     *       플레이어는 mobGriefing 을 묻지 않고, 이 저장소에는 mobGriefing 끄기가 없다.</li>
     *   <li>점액: 크기 2 슬라임 2 마리({@code numberOfSlimesToSpawn}: 최대 뭉침 24 − 몸 상자 +2 안의
     *       슬라임 수, 0..2 로 자른다)를 발 + 0.5 에 세운다.</li>
     * </ul>
     */
    private void applyDeathTriggeredEffects(double x, double y, double z, double height,
            double width, java.util.Set<StatusEffect> effects) {
        if (effects.contains(StatusEffect.WIND_CHARGED)) {
            double power = 3.0 + rng.nextFloat() * 2.0;
            double centerY = y + height / 2.0;
            applyWindBurst(x, centerY, z, power, 1.0);
            trialWorldSound("wind_charged_burst", x, centerY, z, (short) 0);
        }
        if (effects.contains(StatusEffect.WEAVING)) {
            spawnWeavingCobwebs((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
        if (effects.contains(StatusEffect.OOZING)) {
            int nearby = 0;
            double half = width / 2.0 + 2.0;
            for (Mob candidate : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
                if (candidate.type != MobType.SLIME || candidate.isDead() || candidate.removed) {
                    continue;
                }
                double ch = candidate.width() / 2.0;
                if (candidate.x + ch > x - half && candidate.x - ch < x + half
                        && candidate.z + ch > z - half && candidate.z - ch < z + half
                        && candidate.y + candidate.height() > y - 2.0
                        && candidate.y < y + height + 2.0) {
                    nearby++;
                }
            }
            int toSpawn = Math.max(0, Math.min(2, 24 - nearby));
            for (int index = 0; index < toSpawn; index++) {
                runtime.spawnOozingSlime(x, y + 0.5, z);
            }
        }
    }

    /** {@code WeavingMobEffect.spawnCobwebsRandomlyAround}. */
    private void spawnWeavingCobwebs(int cx, int cy, int cz) {
        int max = 2 + rng.nextInt(2);
        java.util.LinkedHashSet<BlockPos> chosen = new java.util.LinkedHashSet<>();
        for (int attempt = 0; attempt < 15; attempt++) {
            // BlockPos.randomInCube(random, 15, pos, 1): 각 축 Mth.nextInt(random, c − 1, c + 1).
            int x = cx - 1 + rng.nextInt(3);
            int y = cy - 1 + rng.nextInt(3);
            int z = cz - 1 + rng.nextInt(3);
            BlockPos pos = new BlockPos(x, y, z);
            if (chosen.contains(new BlockPos(x, y - 1, z))) continue;
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) continue;
            int block = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
            if (block < 0 || !(Fluids.isReplaceable(block) || Fluids.isFluid(block))) continue;
            if (!rt.tickLoop().sturdyTopAt(x, y - 1, z)) continue;
            chosen.add(pos);
            if (chosen.size() >= max) break;
        }
        for (BlockPos pos : chosen) {
            rt.fluidSim().applyChange(pos.x(), pos.y(), pos.z(), Blocks.COBWEB);
            rt.tickBlockChanges().put(pos, (short) Blocks.COBWEB);
            levelEvent(3018, pos.x(), pos.y(), pos.z(), 0);
        }
    }

    private void summonHurtSilverfish() {
        long tick = runtime.worldTick();
        List<Mob> existing = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        for (Mob mob : existing) {
            if (!(mob instanceof Silverfish silverfish) || mob.isDead() || mob.removed) continue;
            int requested = silverfish.consumePendingSummons();
            int localCount = localSilverfishCount(mob.x, mob.y, mob.z, 8.0);
            if (requested == 0 || localCount >= 12) continue;
            int summoned = 0;
            for (int attempt = 0; attempt < 24 && summoned < requested
                    && localCount < 12; attempt++) {
                int hash = mix32((int) (mob.id ^ (mob.id >>> 32) ^ tick ^ attempt * 0x9e3779b9));
                int x = (int) Math.floor(mob.x) + Math.floorMod(hash, 7) - 3;
                int z = (int) Math.floor(mob.z) + Math.floorMod(hash >>> 8, 7) - 3;
                int y = (int) Math.floor(mob.y);
                if (!validSilverfishSpawn(x, y, z)) continue;
                runtime.addMob(MobType.SILVERFISH, x + 0.5, y, z + 0.5);
                summoned++;
                localCount++;
            }
            silverfish.recordSummons(summoned);
        }
    }

    /** InfestedBlock.spawnAfterBreak: one triggered silverfish at the broken cell centre. */
    void spawnInfestedBlockSilverfish(int x, int y, int z) {
        runtime.addMob(MobType.SILVERFISH, x + 0.5, y, z + 0.5);
    }

    private int localSilverfishCount(double x, double y, double z, double radius) {
        double radiusSq = radius * radius;
        int count = 0;
        for (Mob candidate : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (candidate.type != MobType.SILVERFISH || candidate.isDead() || candidate.removed) continue;
            double dx = candidate.x - x, dy = candidate.y - y, dz = candidate.z - z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) count++;
        }
        return count;
    }

    private boolean validSilverfishSpawn(int x, int y, int z) {
        return world.isSolid(world.getBlock(x, y - 1, z))
                && world.getBlock(x, y, z) == Fluids.AIR;
    }

    private static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }

    private Mob findMob(long mobId) {
        return runtime.mobById(mobId);
    }

    /**
     * 낙뢰 효과 큐잉. 날씨 권위(WS 스레드가 아닌 틱 스레드의 {@code beginTick})에서만 호출된다.
     * 좌표는 이미 {@link LightningStrike.Hook} 보정을 거친 <b>최종</b> 타격점이다.
     */
    void enqueueLightningStrike(LightningStrike strike) {
        pendingLightning.addLast(new PendingLightning(strike, false));
    }

    void enqueueNaturalLightningStrike(LightningStrike strike) {
        pendingLightning.addLast(new PendingLightning(strike, true));
    }

    /**
     * 바닐라 {@code LightningBolt#tick} 의 효과 적용 — 발화(spawnFire) 후 AABB 안 엔티티의
     * {@code thunderHit}. 몹 AI 앞에서 한 번에 처리한다.
     */
    private void applyLightningStrikes(long tickNo) {
        while (!pendingLightning.isEmpty()) {
            PendingLightning pending = pendingLightning.removeFirst();
            LightningStrike strike = pending.strike;
            rt.tickLoop().igniteFromLightning(strike);
            applyLightningToPlayers(strike);
            applyLightningToMobs(strike, tickNo);
            trySpawnSkeletonHorseTrap(strike, pending.natural);
        }
    }

    private void trySpawnSkeletonHorseTrap(LightningStrike strike, boolean natural) {
        int x = (int) Math.floor(strike.x());
        int y = (int) Math.floor(strike.y());
        int z = (int) Math.floor(strike.z());
        String claimKey = "SKELETON_HORSE_TRAP:" + x + ':' + z;
        SkeletonHorseTrapOriginRules.TrapPlan plan = SkeletonHorseTrapOriginRules.plan(
                rt.seed(), x, y, z, true, natural, world.openToSky(x, y, z),
                knownStructureOccupantClaims.contains(claimKey));
        if (plan == null) return;
        Mob horse = runtime.addMob(plan.mobType(), plan.spawnX(), plan.spawnY(), plan.spawnZ(), true);
        if (horse == null) return;
        if (!(horse instanceof com.gameexpert.engine.mob.SkeletonHorse trapHorse)) {
            runtime.rollbackExternalMob(horse.id, plan.mobType());
            return;
        }
        trapHorse.armTrap();
        runtime.refreshPersistenceSnapshot(trapHorse);
        long siteKey = Integer.toUnsignedLong((int) plan.originKey());
        StructureOccupantClaimSnapshot claim = new StructureOccupantClaimSnapshot(
                "SKELETON_HORSE_TRAP", x, z, siteKey,
                STRUCTURE_OCCUPANT_POLICY_VERSION, 1);
        knownStructureOccupantClaims.add(claimKey);
        unpersistedStructureOccupantClaims.put(claimKey, claim);
    }

    private void applyLightningToPlayers(LightningStrike strike) {
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            if (!LightningStrikeRules.withinImpact(strike,
                    player.x(), player.y(), player.z(), 0.3, 1.8)) {
                continue;
            }
            // 바닐라 순서: 발화 → 피해. 사망 시 PlayerTickState 가 화상을 스스로 끈다.
            player.setFireTicks(Math.max(player.fireTicks(),
                    EnvironmentSystem.burningTicks(player, LightningStrikeRules.FIRE_TICKS)));
            damageFromAndSync(player, (int) LightningStrikeRules.DAMAGE, "lightning", null,
                    strike.x(), strike.z(), false);
        }
    }

    private void applyLightningToMobs(LightningStrike strike, long tickNo) {
        for (Mob mob : List.copyOf(
                runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()))) {
            if (mob.isDead() || mob.removed) continue;
            if (!LightningStrikeRules.withinImpact(strike,
                    mob.x, mob.y, mob.z, mob.width() / 2.0, mob.height())) {
                continue;
            }
            if (mob.type == MobType.MOOSHROOM) {
                if (MooshroomRules.toggleByLightning(mob)) {
                    runtime.refreshPersistenceSnapshot(mob);
                }
                continue;
            }
            switch (LightningStrikeRules.thunderHit(mob.type)) {
                case CONVERT_TO_WITCH -> convertByLightning(mob, MobType.WITCH);
                case CONVERT_TO_ZOMBIFIED_PIGLIN ->
                        convertByLightning(mob, MobType.ZOMBIFIED_PIGLIN);
                case CHARGE_CREEPER -> {
                    if (mob instanceof Creeper creeper) creeper.setPowered(true);
                    hurtByLightning(mob, tickNo);
                }
                case DAMAGE_AND_IGNITE -> hurtByLightning(mob, tickNo);
            }
        }
    }

    /**
     * {@code Entity#thunderHit}: 8초 발화 + 5 피해. 이 피해는 <b>방어도가 깎는다</b> —
     * 바닐라 {@code lightning_bolt} 피해 타입은 태그 {@code minecraft:bypasses_armor} 에
     * 들어 있지 않다. 플레이어 쪽({@code PlayerTickState.armorReducible})도 같은 결론이다.
     */
    private void hurtByLightning(Mob mob, long tickNo) {
        mob.igniteForTicks(LightningStrikeRules.FIRE_TICKS);
        if (!mob.damage(LightningStrikeRules.DAMAGE, tickNo, "lightning")) return;
        broadcast(new WsMessages.MobHurt(mob.id, mob.isDead()));
        if (!mob.isDead()) return;
        pendingDespawnReasons.put(mob.id, "death");
        runtime.splitSlimeOnDeath(mob, rng);
        spawnMobDrops(mob, false);
        // entities/turtle.json has a second, unconditional one-bowl pool guarded only by
        // DamageTypeTags.IS_LIGHTNING. Keep it after the ordinary seagrass pool, matching the
        // loot-table pool order, without teaching every other death path about damage sources.
        if (mob.type == MobType.TURTLE) {
            rt.itemSystem().spawnMobDrop(PlayerInventory.BOWL, 1,
                    mob.x, mob.y + 0.5, mob.z);
        }
        mobDropsHandled.add(mob.id);
    }

    /** 변신은 원본 id 를 "converted" 로 닫고 새 id 를 연다(치료·익사 변환과 같은 계약). */
    private void convertByLightning(Mob mob, MobType target) {
        runtime.convertByLightning(mob, target);
        pendingDespawnReasons.put(mob.id, "converted");
    }

    private void handleExplosion(MobEvent.Explode ex) {
        if (!explode(ex.x(), ex.y(), ex.z(), ex.radius())) {
            pendingExplosions.addLast(
                    new PendingExplosion(ex.x(), ex.y(), ex.z(), ex.radius()));
        }
    }

    /** Creeper and PrimedTnt share one explosion path; current fluid cells provide ray resistance dynamically. */
    boolean explode(double x, double y, double z, double power) {
        return explode(x, y, z, power, true);
    }

    /** [DRAGON] {@code destroyBlocks} 가 거짓이면 {@code ExplosionInteraction.NONE}: 피해·넉백만 있고 블록은 그대로다. */
    boolean explode(double x, double y, double z, double power, boolean destroyBlocks) {
        int extent = (int) Math.ceil(power * 2) + PORTAL_MAX_EXTENT;
        int minX = (int) Math.floor(x) - extent;
        int maxX = (int) Math.floor(x) + extent;
        int minZ = (int) Math.floor(z) - extent;
        int maxZ = (int) Math.floor(z) + extent;
        // A pending canonical container owns the whole blast admission window. Defer before
        // ExplosionRules consumes any RNG so retrying after the owner installs both halves keeps
        // the exact explosion sequence.
        if (rt.hasPendingCanonicalLootCoordinateInBox(minX, minZ, maxX, maxZ)) {
            pendingExplosionRetryRequested = true;
            return false;
        }
        if (!residentChunkBox(minX, minZ, maxX, maxZ)) return false;
        // 폭발력/저항 광선 파괴 → 유체 경로로 오버레이 반영 + dirty + 인접 유체 재활성화.
        // 파괴 블록은 MC decay 규칙 1/power 확률로 기존 블록 드랍 경로에 보낸다.
        List<BlockPos> destroyed = destroyBlocks ? ExplosionRules.destroyedBlocks(
                this::residentBlock, x, y, z, power, rng::nextDouble) : List.of();
        List<PlayerExplosionImpact> playerImpacts = new ArrayList<>();
        double blastRangeSquared = 4.0 * power * power;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= blastRangeSquared) continue;
            double distance = Math.sqrt(distanceSquared);
            double exposure = ExplosionRules.exposure(this::residentBlock,
                    x, y, z,
                    player.x() - 0.3, player.y(), player.z() - 0.3,
                    player.x() + 0.3, player.y() + 1.8, player.z() + 0.3);
            double impact = ExplosionRules.impactAt(distance, power, exposure);
            int damage = ExplosionRules.damageAt(distance, power, exposure);
            if (damage <= 0 || impact <= 0) continue;
            double inverseDistance = distance > 1e-9 ? 1.0 / distance : 0.0;
            playerImpacts.add(new PlayerExplosionImpact(
                    player, damage, impact,
                    dx * inverseDistance, dy * inverseDistance, dz * inverseDistance,
                    player.hurtProtected()));
        }
        List<MobExplosionImpact> mobImpacts = new ArrayList<>();
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead()) continue;
            // [DRAGON] 드래곤은 몸 중심이 아니라 부위마다 거리·노출을 본다(DragonFightSystem.hurtDragonByExplosion).
            if (mob instanceof EnderDragon) {
                mobImpacts.add(new MobExplosionImpact(mob, 0, 0.0, 0.0, 0.0, 0.0, false));
                continue;
            }
            double dx = mob.x - x;
            double dy = mob.y - y;
            double dz = mob.z - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= blastRangeSquared) continue;
            double distance = Math.sqrt(distanceSquared);
            double halfWidth = mob.width() / 2.0;
            double exposure = ExplosionRules.exposure(this::residentBlock,
                    x, y, z,
                    mob.x - halfWidth, mob.y, mob.z - halfWidth,
                    mob.x + halfWidth, mob.y + mob.height(), mob.z + halfWidth);
            double impact = ExplosionRules.impactAt(distance, power, exposure);
            int damage = ExplosionRules.damageAt(distance, power, exposure);
            if (damage <= 0 || impact <= 0) continue;
            double inverseDistance = distance > 1e-9 ? 1.0 / distance : 0.0;
            mobImpacts.add(new MobExplosionImpact(
                    mob, damage, impact,
                    dx * inverseDistance, dy * inverseDistance, dz * inverseDistance,
                    mob.hurtProtectedAt(runtime.worldTick())));
        }
        Set<BlockPos> pairedCells = new HashSet<>();
        for (BlockPos p : destroyed) {
            if (!pairedCells.add(p)) continue;
            int original = residentBlock(p.x(), p.y(), p.z());
            if (original == Fluids.AIR) continue;
            if (original == Blocks.TNT) {
                rt.tickLoop().primeTntFromExplosion(p.x(), p.y(), p.z(), rng);
                continue;
            }
            if (original == com.gameexpert.terrain.Blocks.DOOR_CLOSED) {
                int state = rt.blockStates().get(p.x(), p.y(), p.z(), original);
                int otherY = (state & BuildingBlockRules.DOOR_UPPER) != 0 ? p.y() - 1 : p.y() + 1;
                if (otherY >= com.gameexpert.terrain.Blocks.MIN_Y
                        && otherY <= com.gameexpert.terrain.Blocks.MAX_Y
                        && residentBlock(p.x(), otherY, p.z()) == original) {
                    BlockPos other = new BlockPos(p.x(), otherY, p.z());
                    pairedCells.add(other);
                    rt.fluidSim().applyChange(other.x(), other.y(), other.z(), Fluids.AIR);
                    rt.tickLoop().onExplosionBlockRemoved(
                            other.x(), other.y(), other.z(), original);
                }
            } else if (PitcherRules.isPitcher(original)) {
                // [PITCHER] The other half leaves with this one (DoublePlantBlock.updateShape); the loot below
                // is the lower half's (PitcherRules.lootBlock).
                int state = rt.blockStates().get(p.x(), p.y(), p.z(), original);
                int otherY = PitcherRules.isUpper(state) ? p.y() - 1 : p.y() + 1;
                if (otherY >= com.gameexpert.terrain.Blocks.MIN_Y
                        && otherY <= com.gameexpert.terrain.Blocks.MAX_Y
                        && residentBlock(p.x(), otherY, p.z()) == original) {
                    int otherState = rt.blockStates().get(p.x(), otherY, p.z(), original);
                    if (PitcherRules.isUpper(otherState) != PitcherRules.isUpper(state)) {
                        BlockPos other = new BlockPos(p.x(), otherY, p.z());
                        pairedCells.add(other);
                        rt.fluidSim().applyChange(other.x(), other.y(), other.z(), Fluids.AIR);
                        rt.tickLoop().onExplosionBlockRemoved(
                                other.x(), other.y(), other.z(), original);
                    }
                }
            } else if (com.gameexpert.terrain.Blocks.isBed(original)) {
                int state = rt.blockStates().get(p.x(), p.y(), p.z(), original);
                int otherX = BuildingBlockRules.bedOtherX(p.x(), state);
                int otherZ = BuildingBlockRules.bedOtherZ(p.z(), state);
                if (residentBlock(otherX, p.y(), otherZ) == original) {
                    int otherState = rt.blockStates().get(otherX, p.y(), otherZ, original);
                    if (BuildingBlockRules.matchingBedStates(state, otherState)) {
                        BlockPos other = new BlockPos(otherX, p.y(), otherZ);
                        pairedCells.add(other);
                        rt.fluidSim().applyChange(other.x(), other.y(), other.z(), Fluids.AIR);
                        rt.tickLoop().onExplosionBlockRemoved(
                                other.x(), other.y(), other.z(), original);
                    }
                }
            }
            int brokenState = rt.blockState(p.x(), p.y(), p.z(), original);
            String potComponents = Blocks.isDecoratedPot(original)
                    ? rt.tickLoop().decoratedPotComponentsAt(p.x(), p.y(), p.z()) : null;
            var potFaces = Blocks.isDecoratedPot(original)
                    ? rt.takeGeneratedTrialDecoratedPotAt(p.x(), p.y(), p.z())
                    : java.util.Optional.<WorldRuntime.GeneratedDecoratedPotRuntime>empty();
            rt.fluidSim().applyChange(p.x(), p.y(), p.z(), Fluids.AIR);
            rt.tickLoop().onExplosionBlockRemoved(p.x(), p.y(), p.z(), original);
            WorldTickLoop.dropRemovedBlockContents(
                    rt, original, p.x(), p.y(), p.z(), 0, false);
            if (VillagerJobSitePolicy.isJobSiteBlock(original)) {
                releaseVillagerJobSiteAt(p.x(), p.y(), p.z());
            }
            if (power == Math.floor(power) ? rng.nextInt((int) power) == 0 : rng.nextDouble() < 1.0 / power) {
                // [PITCHER] Only a lower half has loot; a mature crop drops the pitcher plant.
                int lootBlock = PitcherRules.lootBlock(original, brokenState);
                if (Blocks.isDecoratedPot(original)) {
                    rt.itemSystem().spawnDecoratedPotDrop((short) original, false, potComponents,
                            potFaces.isPresent() ? potFaces.get().faceItemTypes() : null,
                            p.x() + .5, p.y() + .5, p.z() + .5);
                } else if (lootBlock != Fluids.AIR) {
                    rt.itemSystem().spawnExplodedBlockDrop(
                            (short) lootBlock, brokenState, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
                }
            }
        }
        broadcast(new WsMessages.Explosion(x, y, z, power));
        // [CONTAINER-MENUS] Placed armor stands and minecarts take the same exposure-scaled damage.
        rt.placedEntities().onExplosion(x, y, z, power, box -> ExplosionRules.exposure(
                this::residentBlock, x, y, z, box[0], box[1], box[2], box[3], box[4], box[5]));
        for (PlayerExplosionImpact affected : playerImpacts) {
            boolean damaged = damageFromAndSync(
                    affected.player, affected.damage, "explosion", null, x, z, false).damaged();
            if (!damaged) continue;
            if (affected.hurtProtected) continue;
            // ServerExplosion reads the live armor attribute after damage (and armor breakage).
            PlayerInventory armor = affected.player.inventory();
            double velocity = affected.impact * 20.0
                    * EnchantmentRules.explosionKnockbackMultiplier(
                            armor.equippedEnchantments(ArmorSlot.HELMET),
                            armor.equippedEnchantments(ArmorSlot.CHESTPLATE),
                            armor.equippedEnchantments(ArmorSlot.LEGGINGS),
                            armor.equippedEnchantments(ArmorSlot.BOOTS));
            affected.player.setHurtKnockback(
                    affected.directionX * velocity,
                    affected.directionY * velocity,
                    affected.directionZ * velocity,
                    0.0, 0.0);
        }
        for (MobExplosionImpact affected : mobImpacts) {
            Mob mob = affected.mob;
            // [DRAGON] 폭발은 엔드 수정을 (다시 터뜨리지 않고) 부수고, 드래곤은 부위마다 폭발 피해를 받는다.
            if (mob.type == MobType.END_CRYSTAL) {
                rt.dragonFight().hurtCrystal(mob.id, true, null);
                continue;
            }
            if (mob instanceof EnderDragon dragon) {
                rt.dragonFight().hurtDragonByExplosion(dragon, x, y, z, power, this::residentBlock);
                continue;
            }
            if (!mob.damage(affected.damage, runtime.worldTick(), "explosion")) continue;
            if (!affected.hurtProtected && !mob.isDead()) {
                // [MOB-EQUIP] 몹 방어구의 폭발로부터 보호도 explosion_knockback_resistance 로 충격을 줄인다.
                double blast = EnchantmentRules.explosionKnockbackMultiplier(
                        mob.equippedItemEnchantments(ArmorSlot.HELMET),
                        mob.equippedItemEnchantments(ArmorSlot.CHESTPLATE),
                        mob.equippedItemEnchantments(ArmorSlot.LEGGINGS),
                        mob.equippedItemEnchantments(ArmorSlot.BOOTS));
                mob.applyExplosionKnockback(
                        affected.directionX * affected.impact * 2.0 * blast,
                        affected.directionY * affected.impact * 2.0 * blast,
                        affected.directionZ * affected.impact * 2.0 * blast);
            }
            broadcast(new WsMessages.MobHurt(mob.id, mob.isDead()));
            if (mob.isDead()) {
                pendingDespawnReasons.put(mob.id, "death");
                runtime.splitSlimeOnDeath(mob, rng);
                spawnMobDrops(mob, false);
                mobDropsHandled.add(mob.id);
            }
        }
        return true;
    }

    boolean hasResidentExplosionArea(double x, double z, int power) {
        int extent = (int) Math.ceil(power * 2) + PORTAL_MAX_EXTENT;
        int minX = (int) Math.floor(x) - extent;
        int maxX = (int) Math.floor(x) + extent;
        int minZ = (int) Math.floor(z) - extent;
        int maxZ = (int) Math.floor(z) + extent;
        return residentChunkBox(minX, minZ, maxX, maxZ);
    }

    private void processPendingExplosions() {
        if (!pendingExplosionRetryRequested) return;
        pendingExplosionRetryRequested = false;
        int pending = pendingExplosions.size();
        for (int index = 0; index < pending; index++) {
            PendingExplosion explosion = pendingExplosions.removeFirst();
            if (!explode(explosion.x, explosion.y, explosion.z, explosion.power, explosion.destroyBlocks)) {
                pendingExplosions.addLast(explosion);
            }
        }
    }

    private int residentBlock(int x, int y, int z) {
        return WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
    }

    private boolean residentChunkBox(int minX, int minZ, int maxX, int maxZ) {
        int minChunkX = Math.floorDiv(minX, Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(maxX, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(minZ, Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(maxZ, Blocks.CHUNK_Z);
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (!rt.accessor().isChunkResident(cx, cz)) return false;
            }
        }
        return true;
    }

    /** 플레이어 무기가 관여하지 않는 사인의 약탈 레벨. */
    private static final int NO_LOOTING_LEVEL = 0;

    /**
     * [PIGLIN-LOOTING] 이 플레이어가 지금 들고 있는 무기의 약탈 레벨.
     * 바닐라 {@code EnchantmentHelper.getMobLooting(killer)} 과 같이 주손만 본다.
     */
    private int lootingLevelOfPlayer(String nickname) {
        if (nickname == null) return NO_LOOTING_LEVEL;
        PlayerTickState player = rt.players().get(nickname);
        if (player == null) return NO_LOOTING_LEVEL;
        PlayerInventory inv = player.inventory();
        int slot = inv.selectedSlot();
        return CombatRules.lootingLevel(inv.itemType(slot), inv.enchantments(slot));
    }

    /**
     * 몹 사망 시 드랍 테이블에 따라 아이템 엔티티를 스폰한다.
     * 폭발·자연사·몹 간 전투처럼 플레이어 무기가 없는 사인은 약탈 레벨 0이다.
     */
    private void spawnMobDrops(Mob mob, boolean killedByPlayer) {
        spawnMobDrops(mob, killedByPlayer, NO_LOOTING_LEVEL);
    }

    /**
     * [PIGLIN-LOOTING] 처치 무기의 약탈 레벨을 실은 사망 정산.
     * 레벨 0이면 드랍 난수열이 예전과 비트 단위로 같다.
     */
    private void spawnMobDrops(Mob mob, boolean killedByPlayer, int lootingLevel) {
        String recentPlayer = mob.recentPlayerKillCredit();
        boolean playerCredit = killedByPlayer || recentPlayer != null;
        int effectiveLooting = killedByPlayer
                ? lootingLevel : lootingLevelOfPlayer(recentPlayer);
        double dx = mob.x, dy = mob.y + 0.5, dz = mob.z;
        int slimeSize = mob instanceof Slime slime ? slime.size() : 0;
        if (rt.hasAnimalSettlements()) {
            MobDeathSettlementWork existing = pendingMobDeathSettlements.get(mob.id);
            if (existing != null) {
                submitMobDeathSettlement(existing);
                return;
            }
            MobDeathPlan existingPlan = pendingMobDeathPlans.get(mob.id);
            if (existingPlan != null) {
                materializeMobDeathPlan(existingPlan);
                return;
            }
            List<PlayerInventory.DroppedStack> planned = new ArrayList<>();
            for (int[] drop : animalDrops(mob.type, rng, playerCredit, mob.isBaby(), mob.isOnFire(),
                    slimeSize, effectiveLooting, mob.sheepColor(), mob.sheepSheared())) {
                planned.add(exactDeathStack((short) drop[0], drop[1],
                        PlayerInventory.initialDurability((short) drop[0]), 0, 0, 0, null,
                        dropComponents(drop)));
            }
            if (mob instanceof Enderman enderman && enderman.carriedBlock() != 0) {
                short drop = InventoryRules.dropFor(enderman.carriedBlock());
                if (drop != 0) {
                    planned.add(exactDeathStack(drop, 1, PlayerInventory.initialDurability(drop),
                            0, 0, 0, null, null));
                }
            }
            for (MobRuntime.PermanentDeathStack stack : runtime.planPermanentDeathSettlement(mob)) {
                planned.add(exactDeathStack(stack.itemType(), stack.count(), stack.durability(),
                        stack.enchantments(), stack.mapId(), stack.shulkerId(),
                        stack.bucketMobData(), stack.itemComponentData()));
            }
            for (Mob.EquipmentDrop equipment : mob.pickedEquipmentDrops()) {
                planned.add(exactDeathStack(equipment.itemType(), 1, equipment.durability(),
                        equipment.enchantments(), 0, 0, null, equipment.itemComponentData()));
            }
            if (playerCredit) {
                for (Mob.EquipmentDrop equipment
                        : mob.naturalEquipmentDrops(rng, effectiveLooting)) {
                    planned.add(exactDeathStack(equipment.itemType(), 1, equipment.durability(),
                            equipment.enchantments(), 0, 0, null, equipment.itemComponentData()));
                }
            }
            if (mob instanceof Drowned drowned && drowned.shellCarrier()) {
                planned.add(exactDeathStack(PlayerInventory.NAUTILUS_SHELL, 1,
                        PlayerInventory.initialDurability(PlayerInventory.NAUTILUS_SHELL),
                        0, 0, 0, null, null));
            }
            boolean awardsXp = XpRules.mobKillAwardsXp(
                    mob.type, slimeSize, mob.isBaby(), playerCredit);
            int minXp = XpRules.mobKillXpMin(mob.type, slimeSize);
            int maxXp = XpRules.mobKillXpMax(mob.type, slimeSize);
            int xpRoll = awardsXp && maxXp > minXp ? rng.nextInt(maxXp - minXp + 1) : 0;
            int xp = XpRules.xpForMobKill(mob.type, slimeSize, mob.isBaby(), playerCredit,
                    xpRoll, mobKillArmorRolls(mob, slimeSize, playerCredit));
            int deathX = (int) Math.floor(dx);
            int deathY = (int) Math.floor(mob.y);
            int deathZ = (int) Math.floor(dz);
            int charge = SculkCatalystRules.charge(xp);
            boolean absorbed = charge > 0 && SculkCatalystRules.catalystNear(
                    this::sculkTerrainBlockAt, deathX, deathY, deathZ);
            MobDeathPlan plan = new MobDeathPlan(mob.id, planned,
                    absorbed ? 0 : xp, absorbed ? charge : 0, deathX, deathY, deathZ, dx, dy, dz);
            pendingMobDeathPlans.put(mob.id, plan);
            materializeMobDeathPlan(plan);
            return;
        }
        for (int[] d : animalDrops(mob.type, rng, playerCredit, mob.isBaby(), mob.isOnFire(),
                slimeSize, effectiveLooting, mob.sheepColor(), mob.sheepSheared())) {
            String components = dropComponents(d);
            if (components == null) {
                rt.itemSystem().spawnMobDrop((short) d[0], d[1], dx, dy, dz);
            } else {
                rt.itemSystem().spawnDeathDrop((short) d[0], d[1], 0, 0, 0, 0, null, components,
                        dx, dy, dz);
            }
        }
        if (mob instanceof Enderman enderman && enderman.carriedBlock() != 0) {
            rt.itemSystem().spawnBlockDrop(enderman.carriedBlock(), dx, dy, dz);
        }
        // Persisted mount/lead/cargo identity is one exactly-once settlement, outside loot RNG.
        List<MobRuntime.PermanentDeathStack> permanent =
                runtime.claimPermanentDeathSettlement(mob);
        for (MobRuntime.PermanentDeathStack stack : permanent) {
            rt.itemSystem().spawnDeathDrop(stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments(), stack.mapId(), stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData(), dx, dy, dz);
        }
        // 땅에서 주운 장비는 100% 반환한다.
        for (Mob.EquipmentDrop equipment : mob.pickedEquipmentDrops()) {
            rt.itemSystem().spawnDeathDrop(equipment.itemType(), 1, equipment.durability(),
                    equipment.enchantments(), 0, 0, null, equipment.itemComponentData(), dx, dy, dz);
        }
        // 생성 장비는 바닐라대로 플레이어에게 죽었을 때만 8.5%(+약탈 레벨당 1%)로 손상 드랍한다.
        if (playerCredit) {
            for (Mob.EquipmentDrop equipment
                    : mob.naturalEquipmentDrops(rng, effectiveLooting)) {
                rt.itemSystem().spawnDeathDrop(equipment.itemType(), 1, equipment.durability(),
                        equipment.enchantments(), 0, 0, null, equipment.itemComponentData(),
                        dx, dy, dz);
            }
        }
        // [TRIDENT] 보조손 앵무조개 껍데기는 **확률 굴림이 없다** — 바닐라는 들고 있으면
        // 무조건 떨어뜨린다([B] «Drowned»: "A drowned holding a nautilus shell always drops
        // it"). 그래서 8.5% 장비 규칙을 타지 않고 별도 줄로 나가며, 난수를 한 번도 쓰지
        // 않으므로 위의 드랍 난수열도 아래 XP 굴림도 프리픽스가 그대로다.
        // killedByPlayer 조건도 없다 — 바닐라 조건은 "들고 있으면" 하나뿐이다.
        if (mob instanceof Drowned drowned && drowned.shellCarrier()) {
            rt.itemSystem().spawnMobDrop(PlayerInventory.NAUTILUS_SHELL, 1, dx, dy, dz);
        }
        // [SURV-X] 경험치는 드랍과 같은 자리에서 나온다. 새끼·비플레이어 처치는 XpRules 가 0으로 만든다.
        // 기존 드랍표의 난수 소비 순서를 바꾸지 않도록 장비 굴림까지 모두 뽑은 뒤에 굴린다
        // (정적판 reserveStandaloneMobDeath 도 같은 순서다).
        boolean awardsXp = XpRules.mobKillAwardsXp(
                mob.type, slimeSize, mob.isBaby(), playerCredit);
        int minXp = XpRules.mobKillXpMin(mob.type, slimeSize);
        int maxXp = XpRules.mobKillXpMax(mob.type, slimeSize);
        int xpRoll = awardsXp && maxXp > minXp ? rng.nextInt(maxXp - minXp + 1) : 0;
        int xp = XpRules.xpForMobKill(mob.type, slimeSize, mob.isBaby(), playerCredit,
                xpRoll, mobKillArmorRolls(mob, slimeSize, playerCredit));
        // [DEEP-DARK] 반경 8 안에 스컬크 촉매가 있으면 그 경험치는 구슬이 되지 않고 확산
        // 예산이 된다 — 바닐라 SculkCatalystBlockEntity 가 charge 를 받고
        // entity.skipDropExperience() 를 부르는 자리와 같다. 난수를 쓰지 않으므로 위의
        // 드랍 난수열은 그대로다.
        int charge = SculkCatalystRules.charge(xp);
        int deathX = (int) Math.floor(dx);
        int deathY = (int) Math.floor(mob.y);
        int deathZ = (int) Math.floor(dz);
        if (charge > 0
                && SculkCatalystRules.catalystNear(this::sculkTerrainBlockAt, deathX, deathY, deathZ)) {
            sculkBlooms.add(new int[] {deathX, deathY, deathZ, charge});
            return;
        }
        if (xp > 0) rt.xpOrbSystem().spawnOrbs(xp, dx, dy, dz);
    }

    private static PlayerInventory.DroppedStack exactDeathStack(short itemType, int count,
            int durability, long enchantments, int mapId, int shulkerId,
            String bucketMobData, String itemComponentData) {
        return PlayerInventory.DroppedStack.exact(itemType, count, durability, enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData);
    }

    /** Validates the complete batch before advancing the shared item/XP identity high-water. */
    List<com.gameexpert.ground.dto.GroundItemSnapshot> reserveDeathSettlementItems(
            List<PlayerInventory.DroppedStack> stacks, double x, double y, double z) {
        if (stacks.isEmpty()) return List.of();
        WorldRuntime.GroundEntityIdAllocator ids = rt.groundEntityIdAllocator();
        long firstId = ids.nextId();
        long nextId = ids.preflightBatch(stacks.size());
        List<com.gameexpert.ground.dto.GroundItemSnapshot> items = new ArrayList<>(stacks.size());
        for (int index = 0; index < stacks.size(); index++) {
            items.add(rt.itemSystem().settlementDeathDropSnapshot(
                    Math.addExact(firstId, index), stacks.get(index), x, y, z));
        }
        ids.commitBatch(firstId, nextId);
        return List.copyOf(items);
    }

    private void materializeMobDeathPlan(MobDeathPlan plan) {
        if (pendingMobDeathSettlements.containsKey(plan.mobId())) return;
        List<com.gameexpert.ground.dto.GroundItemSnapshot> items;
        try {
            items = reserveDeathSettlementItems(
                    plan.stacks(), plan.dropX(), plan.dropY(), plan.dropZ());
        } catch (RuntimeException unavailableIdentityBatch) {
            return;
        }
        MobDeathSettlementWork work = new MobDeathSettlementWork(plan.mobId(), items,
                plan.xp(), plan.sculkCharge(), plan.deathX(), plan.deathY(), plan.deathZ(),
                plan.dropX(), plan.dropY(), plan.dropZ());
        pendingMobDeathSettlements.put(plan.mobId(), work);
        pendingMobDeathPlans.remove(plan.mobId());
        submitMobDeathSettlement(work);
    }

    private void submitMobDeathSettlement(MobDeathSettlementWork work) {
        String key = "mob-death:" + work.mobId();
        if (!animalSettlementInFlight.add(key)) return;
        Runnable transaction = () -> {
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .MobDeathIntent intent = null;
            boolean committed = false;
            try {
                intent = rt.animalSettlements().beginMobDeath(
                        rt.worldId(), work.mobId(), work.items());
                committed = rt.animalSettlements().commitMobDeath(rt.worldId(), intent);
            } catch (RuntimeException | Error failure) {
                committed = false;
            }
            var durableIntent = intent;
            boolean durable = committed;
            rt.enqueuePersistenceCompletion(() -> {
                animalSettlementInFlight.remove(key);
                if (!durable || durableIntent == null) return;
                work.items().forEach(rt.itemSystem()::commitSettlementDrop);
                if (work.sculkCharge() > 0) {
                    sculkBlooms.add(new int[] {work.deathX(), work.deathY(), work.deathZ(),
                            work.sculkCharge()});
                } else if (work.xp() > 0) {
                    rt.xpOrbSystem().spawnOrbs(
                            work.xp(), work.dropX(), work.dropY(), work.dropZ());
                }
                runtime.confirmPermanentDeathSettlement(work.mobId());
                pendingMobDeathSettlements.remove(work.mobId());
                submitAnimalRetirement(durableIntent.key());
            });
        };
        rt.submitAnimalSettlementPersistence(transaction,
                () -> animalSettlementInFlight.remove(key));
    }

    /**
     * [SURV-X] 방어구 보너스 굴림. 바닐라 {@code Mob#getExperienceReward} 는 {@code xpReward > 0}
     * 일 때만 비어 있지 않은 방어구 칸마다 {@code 1 + random.nextInt(3)} 을 뽑으므로, XP 가 0인
     * 처치에서는 난수를 <b>한 번도</b> 뽑지 않는다(그렇지 않으면 기존 난수열이 흔들린다).
     */
    private int[] mobKillArmorRolls(Mob mob, int slimeSize, boolean killedByPlayer) {
        if (!XpRules.mobKillAwardsXp(mob.type, slimeSize, mob.isBaby(), killedByPlayer)) {
            return XpRules.NO_ARMOR_ROLLS;
        }
        int pieces = mob.equippedArmorCount();
        if (pieces <= 0) return XpRules.NO_ARMOR_ROLLS;
        int[] rolls = new int[pieces];
        for (int i = 0; i < pieces; i++) rolls[i] = rng.nextInt(XpRules.XP_ARMOR_BONUS_SPAN);
        return rolls;
    }

    /**
     * [DEEP-DARK] 개화가 읽는 지형. 몹 lane 이 이미 쓰고 있는 상주 조회를 그대로 감싼다.
     */
    private int sculkTerrainBlockAt(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return WorldTickLoop.UNAVAILABLE_BLOCK;
        return WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
    }

    /**
     * 촉매가 흡수한 사망 목록을 비운다. 반환값은 {@code {x, y, z, charge, 사망순번}} 이고
     * 순번은 개화 시드에 들어가 같은 좌표의 반복 사망이 같은 개화를 내지 않게 한다.
     */
    List<long[]> drainSculkBlooms() {
        if (sculkBlooms.isEmpty()) return List.of();
        List<long[]> drained = new ArrayList<>(sculkBlooms.size());
        int[] pending;
        while ((pending = sculkBlooms.poll()) != null) {
            drained.add(new long[] {pending[0], pending[1], pending[2], pending[3],
                    ++sculkDeathOrdinal});
        }
        return drained;
    }

    List<Mob> mobsForBoats() {
        List<Mob> active = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        // This is a pre-start inspection path used by persistence/direct-engine fixtures, never a scheduled
        // world tick.  Live boats always receive the bounded union above.
        return !rt.started() && active.isEmpty() && rt.activeSimulationChunksForMobTick().isEmpty()
                ? runtime.mobs() : active;
    }

    /** 기존 보트 승객 해제·좌석 동기화용 전체 ID 조회. 신규 탑승 후보에는 사용하지 않는다. */
    List<Mob> mobsForPlacedVehicles() { return mobsForBoats(); }
    Mob mobForPlacedVehicle(long mobId) { return runtime.mobById(mobId); }
    com.gameexpert.mob.dto.MobPersistenceSnapshot placedVehiclePassengerSnapshot(Mob mob) {
        runtime.refreshSpatialIndex(mob);
        runtime.refreshPersistenceSnapshot(mob);
        return runtime.detachedPersistenceSnapshot(mob);
    }

    Mob mobForBoat(long mobId) {
        return runtime.mobById(mobId);
    }

    void reindexBoatMob(Mob mob) {
        runtime.refreshSpatialIndex(mob);
    }

    /**
     * QA 시딩 스폰({@code game.qa-seeding=true} 서버 전용). 자연 스폰과 같은 런타임 등록 경로를
     * 쓰므로 이후 AI·전투·드랍은 전부 평소 권위 규칙대로 돈다.
     */
    Mob qaSpawnMob(MobType type, double x, double y, double z) {
        return spawnMobForTest(type, x, y, z);
    }

    /** 예약 QA 월드만 호출하며, 경계 전이 자체는 다음 정상 몹 틱에 맡긴다. */
    boolean qaStageMobBoundary(long mobId, PlayerAction.MobAuditStage stage) {
        if (mobId <= 0 || mobId > 9_007_199_254_740_991L || stage == null) return false;
        if (stage == PlayerAction.MobAuditStage.CURE_ZOMBIE_VILLAGER) {
            return runtime.qaStageZombieVillagerCure(mobId);
        }
        Mob mob = runtime.mobById(mobId);
        if (mob == null || mob.isDead() || mob.type != stage.species()) return false;
        if (stage.ageBoundary()) {
            if (!mob.isBaby() || mob.ageTicksRemaining() <= 1 || mob.loveTicksRemaining() != 0) return false;
            mob.setAgeTicksRemaining(1);
        } else {
            if (mob.isBaby() || mob.breedingCooldownTicks() <= 1 || mob.loveTicksRemaining() != 0) return false;
            mob.restoreBreedingState(mob.ageTicksRemaining(), 1, mob.loveTicksRemaining());
        }
        return true;
    }

    /** Real non-persistence-required registration used by the reserved dense performance fixture. */
    long qaSpawnEphemeralFixtureMob(MobType type, double x, double y, double z) {
        return runtime.addEphemeralQaMob(type, x, y, z).id;
    }

    /** Exact fixture identities are checked directly; natural spawns never enter this count. */
    int qaLiveFixtureMobCount(long[] fixtureMobIds) {
        if (fixtureMobIds == null) return 0;
        int live = 0;
        for (long id : fixtureMobIds) {
            if (runtime.mobById(id) != null) live++;
        }
        return live;
    }

    /**
     * Optional QA read of the completed active union. The mob tick has already materialized and
     * cached this exact chunk query, so this is an allocation-free size read when the recorder asks.
     */
    int currentActiveMobCount() {
        return runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()).size();
    }

    int authoritativeMobCountForTest() {
        return runtime.mobs().size();
    }

    /** Installs the exact identity already committed by the atomic content-QA settlement. */
    Mob qaInstallPersistedMob(MobPersistenceSnapshot snapshot) {
        Mob mob = runtime.restore(snapshot);
        rt.activateSimulationChunkForTest(snapshot.getX(), snapshot.getZ());
        return mob;
    }

    void qaPublishCommittedFixtureSnapshot() {
        publishSnapshot();
    }

    static final int FINAL_SCENE_REQUIRED_ROLE_MASK =
            1 << RaidPlanGenerator.ROLE_PILLAGER
            | 1 << RaidPlanGenerator.ROLE_VINDICATOR
            | 1 << RaidPlanGenerator.ROLE_EVOKER
            | 1 << RaidPlanGenerator.ROLE_RAVAGER
            | 1 << RaidPlanGenerator.ROLE_STANDARD_BEARER;

    record FinalSceneRaidFacts(boolean active, int requiredRoleMask,
            boolean bossbarVisible) { }

    /** Arms the existing procedural schedule from exactly one persisted fixture anchor. */
    boolean qaArmFinalSceneRaid(com.gameexpert.engine.qa.ContentQaFixturePlan plan,
            long tickNo, String nickname, int releaseWindowTicks) {
        if (plan == null || rt.seed() != (int) com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_SEED
                || !com.gameexpert.engine.qa.ContentQaFixturePlan.FIXTURE_ID.equals(plan.id())) {
            return false;
        }
        com.gameexpert.engine.qa.ContentQaFixturePlan.MobRequest request = plan.mobRequests().stream()
                .filter(candidate -> "final-scene-village-anchor".equals(candidate.id())
                        && candidate.type() == MobType.VILLAGER)
                .findFirst().orElse(null);
        if (request == null) return false;
        Mob anchor = qaFinalSceneRaidAnchor(runtime.mobs(), request);
        if (anchor == null) return false;
        RaidLedger.Instance current = runtime.raidLedger().ongoing();
        if (current != null) return current.anchorMobId() == anchor.id
                && runtime.qaRaidConstraintMatches(anchor.id,
                        FINAL_SCENE_REQUIRED_ROLE_MASK, releaseWindowTicks);
        return runtime.armQaRaid(anchor.id, tickNo, nickname,
                FINAL_SCENE_REQUIRED_ROLE_MASK, releaseWindowTicks);
    }

    static Mob qaFinalSceneRaidAnchor(List<Mob> mobs,
            com.gameexpert.engine.qa.ContentQaFixturePlan.MobRequest request) {
        if (mobs == null || request == null || request.type() != MobType.VILLAGER) return null;
        Mob anchor = null;
        for (Mob candidate : mobs) {
            if (candidate.type != MobType.VILLAGER || candidate.isDead() || candidate.removed
                    || Double.compare(candidate.x, request.x()) != 0
                    || Double.compare(candidate.y, request.y()) != 0
                    || Double.compare(candidate.z, request.z()) != 0) continue;
            if (anchor != null) return null;
            anchor = candidate;
        }
        return anchor;
    }

    /** Reads only normal ledger membership and the normal published bossbar. */
    FinalSceneRaidFacts qaFinalSceneRaidFacts() {
        RaidLedger.Instance raid = runtime.raidLedger().ongoing();
        if (raid == null) return new FinalSceneRaidFacts(false, 0, false);
        java.util.EnumSet<MobType> roles = java.util.EnumSet.noneOf(MobType.class);
        for (long memberId : raid.liveMemberIds()) {
            Mob member = runtime.mobById(memberId);
            if (member != null && !member.isDead() && !member.removed) roles.add(member.type);
        }
        int required = 0;
        if (roles.contains(MobType.PILLAGER)) required |= 1 << RaidPlanGenerator.ROLE_PILLAGER;
        if (roles.contains(MobType.VINDICATOR)) required |= 1 << RaidPlanGenerator.ROLE_VINDICATOR;
        if (roles.contains(MobType.EVOKER)) required |= 1 << RaidPlanGenerator.ROLE_EVOKER;
        if (roles.contains(MobType.RAVAGER)) required |= 1 << RaidPlanGenerator.ROLE_RAVAGER;
        if (roles.contains(MobType.STANDARD_BEARER)) {
            required |= 1 << RaidPlanGenerator.ROLE_STANDARD_BEARER;
        }
        return new FinalSceneRaidFacts(raid.status() == RaidLedger.Status.ONGOING,
                required, lastBossbar != null && lastBossbar.isActive()
                        && raid.bossbarVisible());
    }

    /** [BLOCK-SHAPES] 테스트 전용: 오차 없는 플레이어 눈덩이 한 개를 권위 투사체 목록에 넣는다. */
    ProjectileSim throwSnowballForTest(String nickname, double x, double y, double z,
            double vx, double vy, double vz) {
        return runtime.throwPlayerSnowball(nickname, x, y, z, vx, vy, vz);
    }

    Mob spawnMobForTest(MobType type, double x, double y, double z) {
        Mob mob = runtime.addMob(type, x, y, z);
        rt.activateSimulationChunkForTest(x, z);
        return mob;
    }

    /** Package-visible regression probe: the owner must hand this system only its current chunk union. */
    int activeMobCountForTest() {
        return runtime.mobsInChunks(rt.activeSimulationChunksForMobTick()).size();
    }

    /**
     * 몹 드랍표(약탈 없음, 순수). 플레이어 처치 여부는 거미 눈, 좀비 희귀 드랍과 토끼발에 영향을 준다.
     * 각 원소는 {@code {itemType, count}}. rng 소비 순서는 spawnMobDrops 방출 순서와 동일하다.
     */
    /** 슬라임볼 0~2. 바닐라는 최소 크기(size 1)만 떨군다. */
    static final int SLIME_BALL_MAX_EXCLUSIVE = 3;
    /** 엔더 진주 0~1. */
    static final int ENDER_PEARL_MAX_EXCLUSIVE = 2;
    /** 마녀는 1~3회 굴려 회당 0~2개를 떨군다. */
    static final int WITCH_ROLL_MIN = 1;
    static final int WITCH_ROLL_MAX_EXCLUSIVE = 3;
    static final int WITCH_STACK_MAX_EXCLUSIVE = 3;
    /** Illusioner/WebCraft standard-bearer 화살 0~2. */
    static final int PILLAGER_ARROW_MAX_EXCLUSIVE = 3;
    /** [GUARDIAN] 프리즈머린 조각 uniform 0..2(바닐라 set_count min 0 max 2). */
    static final int GUARDIAN_SHARD_MAX_EXCLUSIVE = 3;
    /** [GUARDIAN] 두 번째 pool 의 생대구 가중치. */
    static final int GUARDIAN_COD_WEIGHT = 2;
    /** [GUARDIAN] 두 번째 pool 의 프리즈머린 수정 가중치. */
    static final int GUARDIAN_CRYSTAL_WEIGHT = 2;
    /** [GUARDIAN] 두 번째 pool 가중치 총합(생대구 2 + 수정 2 + empty 1). */
    static final int GUARDIAN_SECOND_POOL_WEIGHT = 5;
    /** [GUARDIAN] 세 번째 pool 의 기본 확률(random_chance_with_enchanted_bonus 0.025). */
    static final float GUARDIAN_FISH_CHANCE = 0.025F;

    /**
     * [NAUTILUS-DROP] 앵무조개 껍데기의 기본 드랍 확률. [B] «Nautilus» 드랍표의 5 % 이고
     * 정적판 {@code standaloneMobDrops} 의 같은 절이 같은 리터럴을 쓴다.
     */
    static final float NAUTILUS_SHELL_CHANCE = 0.05F;
    /** 약탈 I 이상에서 쓰는 JSON linear provider의 첫 확률. */
    static final float NAUTILUS_SHELL_ENCHANTED_BASE_CHANCE = 0.060000002F;
    /** [NAUTILUS-DROP] 약탈 I 초과 레벨당 확률 가산(+1 %p). 레벨 III 에서 8 % 가 된다. */
    static final float NAUTILUS_SHELL_LOOTING_STEP = 0.01F;

    /**
     * [ZOMBIE-NAUTILUS-FIX] 좀비 앵무조개 썩은 살점 개수 굴림의 배타 상한. {@code nextInt(4)}
     * 라 무약탈 <b>0~3</b> 이고, 0 이 네 갈래 중 하나라 "적어도 하나" 확률이 정확히 <b>75 %</b> 다.
     * 약탈 III 이 개수 0~3 을 더해 <b>0~6</b> 이 된다 — [B] «Zombie Nautilus» 드랍표의 숫자와
     * 같고, 근거는 {@code docs/research/mc-nautilus-1-21-11.md} §8-4 가 소유한다.
     *
     * <p>좀비 계열 인간형의 0~2({@code nextInt(3)})보다 한 칸 크다. 이 종만의 값이라 다른 종의
     * 굴림 형태에 닿지 않는다.
     */
    static final int ZOMBIE_NAUTILUS_ROTTEN_FLESH_ROLL = 4;

    /**
     * [GUARDIAN] {@code gameplay/fishing/fish} 하위 표를 한 번 굴린다. 표·순서·가중치는
     * 낚시 정본({@link FishingRules#fishTable()})을 그대로 재사용하므로 사본이 갈라지지 않는다.
     */
    private static int[] rollGuardianFish(MobRandom rng, boolean onFire) {
        FishingRules.LootEntry[] table = FishingRules.fishTable();
        int total = 0;
        for (FishingRules.LootEntry entry : table) total += entry.weight();
        int pick = rng.nextInt(total);
        for (FishingRules.LootEntry entry : table) {
            pick -= entry.weight();
            if (pick < 0) {
                short item = entry.itemType();
                if (onFire && item == PlayerInventory.COD_RAW) item = PlayerInventory.COD_COOKED;
                if (onFire && item == PlayerInventory.SALMON_RAW) item = PlayerInventory.SALMON_COOKED;
                return new int[]{Short.toUnsignedInt(item), entry.count()};
            }
        }
        FishingRules.LootEntry last = table[table.length - 1];
        short item = last.itemType();
        if (onFire && item == PlayerInventory.COD_RAW) item = PlayerInventory.COD_COOKED;
        if (onFire && item == PlayerInventory.SALMON_RAW) item = PlayerInventory.SALMON_COOKED;
        return new int[]{Short.toUnsignedInt(item), last.count()};
    }

    /** {@code random_chance_with_enchanted_bonus}의 float 선형 확률을 그대로 계산한다. */
    private static float lootTableChance(float unenchanted, float enchantedBase,
                                          float perLevelAboveFirst, int lootingLevel) {
        if (lootingLevel <= 0) return unenchanted;
        return enchantedBase + (lootingLevel - 1) * perLevelAboveFirst;
    }

    /** 마녀 첫 pool의 가중 엔트리 순서. 막대기 weight=2를 중복 슬롯으로 보존한다. */
    static final short[] WITCH_DROP_ITEMS = {
        PlayerInventory.GLOWSTONE_DUST, PlayerInventory.SUGAR,
        PlayerInventory.SPIDER_EYE, PlayerInventory.GLASS_BOTTLE,
        PlayerInventory.GUNPOWDER, PlayerInventory.STICK, PlayerInventory.STICK,
    };
    /**
     * [SHEEP-WOOL-COLOR] 색을 모르는 호출자(순수 드랍표 단위 테스트 등)가 쓰는 기본 양 색.
     * 바닐라 {@code getRandomSheepColor} 의 최빈값인 흰색이다.
     */
    static final int DEFAULT_SHEEP_COLOR = 0;

    /**
     * [SHEEP-WOOL-COLOR] MC {@code DyeColor} 네트워크 ID → 양털 블록 ID.
     * 범위 밖(양이 아닌 종의 {@code -1} 등)은 기본 흰 양털이 된다.
     */
    static int woolItemForSheepColor(int sheepColor) {
        return sheepColor >= 0 && sheepColor < Blocks.WOOL_BY_DYE_COLOR.length
                ? Blocks.WOOL_BY_DYE_COLOR[sheepColor]
                : Blocks.WOOL_BY_DYE_COLOR[DEFAULT_SHEEP_COLOR];
    }

    /**
     * [TRIAL-GAP] 드랍 배열의 선택 세 번째 칸. 불길한 병이면 그 증폭(바닐라
     * {@code set_ominous_bottle_amplifier})을 컴포넌트 문자열로 옮긴다. 그 밖은 null 이다.
     */
    static String dropComponents(int[] drop) {
        if (drop.length < 3 || drop[0] != PlayerInventory.OMINOUS_BOTTLE) return null;
        return PlayerInventory.ominousBottleComponents(drop[2]);
    }

    static List<int[]> animalDrops(MobType type, MobRandom rng) {
        return animalDrops(type, rng, true);
    }

    static List<int[]> animalDrops(MobType type, MobRandom rng, boolean killedByPlayer) {
        return animalDrops(type, rng, killedByPlayer, false, false);
    }

    static List<int[]> animalDrops(MobType type, MobRandom rng, boolean killedByPlayer,
                                   boolean baby, boolean onFire) {
        return animalDrops(type, rng, killedByPlayer, baby, onFire, 1);
    }

    /** {@code slimeSize}는 슬라임에게만 의미가 있고, 유황 큐브 크기는 {@code baby}로 갈린다. */
    static List<int[]> animalDrops(MobType type, MobRandom rng, boolean killedByPlayer,
                                   boolean baby, boolean onFire, int slimeSize) {
        return animalDrops(type, rng, killedByPlayer, baby, onFire, slimeSize, 0);
    }

    /**
     * [PIGLIN-LOOTING] 약탈 레벨을 실은 드랍표. 레벨 0이면 굴림 하나까지 예전과 같은 수열이라
     * 기존 골든 벡터가 그대로 유효하다({@link #lootingBonus} 가 레벨 0에서 난수를 소비하지 않는다).
     *
     * <p>바닐라 근거는 각 몹의 loot table 이다. 개수 가산은 {@code minecraft:looting_enchant}
     * ({@code count: 0..1}) 가 붙은 항목에만, 확률 가산은
     * {@code minecraft:random_chance_with_looting} 이 붙은 항목에만 넣었다. WebCraft 고유 몹
     * (pigman·briarback·gloamkite·raid 전문가)과 바닐라에 약탈 항이 없는 항목(철골렘·양털·
     * 물고기·뼛가루)은 레벨과 무관하게 그대로다.
     */
    static List<int[]> animalDrops(MobType type, MobRandom rng, boolean killedByPlayer,
                                   boolean baby, boolean onFire, int slimeSize,
                                   int lootingLevel) {
        return animalDrops(type, rng, killedByPlayer, baby, onFire, slimeSize, lootingLevel,
                DEFAULT_SHEEP_COLOR);
    }

    /**
     * [SHEEP-WOOL-COLOR] 양의 색을 실은 드랍표. {@code sheepColor} 는 MC {@code DyeColor}
     * 네트워크 ID(0..15)라 {@link Blocks#WOOL_BY_DYE_COLOR} 를 그대로 색인한다 — 전단
     * ({@link #shearSheep})과 완전히 같은 표이며, 바닐라도 사망 드랍은 색별 loot table
     * {@code entities/sheep/<color>} 이 정한다. 양이 아닌 종은 이 값을 읽지 않는다.
     */
    static List<int[]> animalDrops(MobType type, MobRandom rng, boolean killedByPlayer,
                                   boolean baby, boolean onFire, int slimeSize,
                                   int lootingLevel, int sheepColor) {
        return animalDrops(type, rng, killedByPlayer, baby, onFire, slimeSize, lootingLevel,
                sheepColor, false);
    }

    /**
     * [SHEEP-WOOL-COLOR] 전단 상태까지 실은 드랍표. 바닐라 {@code Sheep} 의 loot table 은
     * 양털 풀에 {@code entity_properties{sheared: false}} 조건이 붙어 있어 <b>전단된 양은
     * 사망해도 양털을 떨구지 않는다</b>(고기는 그대로). 양털 항은 난수를 소비하지 않으므로
     * 건너뛰어도 이후 스트림이 어긋나지 않는다. 양이 아닌 종은 이 값을 읽지 않는다.
     */
    static List<int[]> animalDrops(MobType type, MobRandom rng, boolean killedByPlayer,
                                   boolean baby, boolean onFire, int slimeSize,
                                   int lootingLevel, int sheepColor, boolean sheepSheared) {
        List<int[]> drops = new ArrayList<>();
        // LivingEntity.shouldDropLoot() is !isBaby; Monster overrides it to true. Keep that class
        // boundary instead of maintaining an incomplete species list as new ageable mobs land.
        if (baby && !type.hostile()) return drops;
        switch (type) {
            case COW, MOOSHROOM -> {
                // entities/{cow,mooshroom}.json pool order: leather, then beef.
                int leather = rng.nextInt(3) + lootingBonus(rng, lootingLevel); // 가죽 0~2
                if (leather > 0) drops.add(new int[]{PlayerInventory.LEATHER, leather});
                drops.add(new int[]{onFire ? PlayerInventory.BEEF_COOKED : PlayerInventory.BEEF_RAW,
                        1 + rng.nextInt(3) + lootingBonus(rng, lootingLevel)}); // 소고기 1~3
            }
            case GRIZZLY_BEAR -> {
                drops.add(new int[]{onFire ? PlayerInventory.BEEF_COOKED : PlayerInventory.BEEF_RAW,
                        3 + rng.nextInt(3) + lootingBonus(rng, lootingLevel)});
                drops.add(new int[]{PlayerInventory.LEATHER, 1 + rng.nextInt(3)});
            }
            case PIG, BOAR -> drops.add(new int[]{onFire ? PlayerInventory.PORK_COOKED : PlayerInventory.PORK_RAW,
                    1 + rng.nextInt(3) + lootingBonus(rng, lootingLevel)}); // 돼지고기 1~3
            case SHEEP -> {
                drops.add(new int[]{onFire ? PlayerInventory.MUTTON_COOKED : PlayerInventory.MUTTON_RAW,
                        1 + rng.nextInt(2) + lootingBonus(rng, lootingLevel)}); // 양고기 1~2
                // 양털 풀에는 바닐라에도 약탈 항이 없다.
                // [SHEEP-WOOL-COLOR] 색 양털 1. 바닐라는 색별 loot table 이 정하므로 전단과
                // 같은 표를 같은 방식으로 색인한다(정적판 standaloneMobDrops 와 같은 형태).
                // 이미 전단된 양은 바닐라 loot table 의 sheared:false 조건에 걸려 양털이 없다.
                if (!sheepSheared) drops.add(new int[]{woolItemForSheepColor(sheepColor), 1});
            }
            case CHICKEN -> {
                // entities/chicken pool 순서: 깃털, 고기.
                int feathers = rng.nextInt(3) + lootingBonus(rng, lootingLevel); // 깃털 0~2
                if (feathers > 0) drops.add(new int[]{PlayerInventory.FEATHER, feathers});
                drops.add(new int[]{onFire ? PlayerInventory.CHICKEN_COOKED : PlayerInventory.CHICKEN_RAW,
                        1 + lootingBonus(rng, lootingLevel)});
            }
            case RABBIT -> {
                // entities/rabbit pool 순서: 가죽, 고기, 플레이어 처치 조건 토끼발.
                int hide = rng.nextInt(2) + lootingBonus(rng, lootingLevel);
                if (hide > 0) drops.add(new int[]{PlayerInventory.RABBIT_HIDE, hide});
                drops.add(new int[]{
                        onFire ? PlayerInventory.RABBIT_COOKED : PlayerInventory.RABBIT_RAW,
                        1 + lootingBonus(rng, lootingLevel)});
                if (killedByPlayer && rng.nextFloat()
                        < lootTableChance(0.1F, 0.13F, 0.03F, lootingLevel)) {
                    drops.add(new int[]{PlayerInventory.RABBIT_FOOT, 1});
                }
            }
            case SQUID -> drops.add(new int[]{PlayerInventory.INK_SAC,
                    1 + rng.nextInt(3) + lootingBonus(rng, lootingLevel)});
            case GLOW_SQUID ->
                    drops.add(new int[]{PlayerInventory.GLOW_INK_SAC,
                            1 + rng.nextInt(3) + lootingBonus(rng, lootingLevel)});
            case COD -> {
                drops.add(new int[]{onFire ? PlayerInventory.COD_COOKED : PlayerInventory.COD_RAW, 1});
                if (rng.nextFloat() < 0.05F) drops.add(new int[]{PlayerInventory.BONE_MEAL, 1});
            }
            case SALMON -> {
                drops.add(new int[]{onFire ? PlayerInventory.SALMON_COOKED : PlayerInventory.SALMON_RAW, 1});
                if (rng.nextFloat() < 0.05F) drops.add(new int[]{PlayerInventory.BONE_MEAL, 1});
            }
            // 열대어·복어는 조리 변형이 없어 불에 죽어도 생물 그대로 떨어진다.
            case TROPICAL_FISH -> {
                drops.add(new int[]{PlayerInventory.TROPICAL_FISH, 1});
                if (rng.nextFloat() < 0.05F) drops.add(new int[]{PlayerInventory.BONE_MEAL, 1});
            }
            case PUFFERFISH -> {
                drops.add(new int[]{PlayerInventory.PUFFERFISH, 1});
                if (rng.nextFloat() < 0.05F) drops.add(new int[]{PlayerInventory.BONE_MEAL, 1});
            }
            case DOLPHIN -> {
                int cod = rng.nextInt(2) + lootingBonus(rng, lootingLevel);
                if (cod > 0) drops.add(new int[]{
                        onFire ? PlayerInventory.COD_COOKED : PlayerInventory.COD_RAW, cod});
            }
            // entities/{horse,donkey,mule,llama}.json: leather 0~2 + looting.
            case HORSE, DONKEY, MULE, LLAMA -> {
                int leather = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
                if (leather > 0) drops.add(new int[]{PlayerInventory.LEATHER, leather});
            }
            // entities/panda.json: bamboo exactly one, no random draw.
            case PANDA -> drops.add(new int[]{Blocks.BAMBOO, 1});
            // entities/parrot.json: feather 1~2 + looting.
            case PARROT -> drops.add(new int[]{PlayerInventory.FEATHER,
                    1 + rng.nextInt(2) + lootingBonus(rng, lootingLevel)});
            // entities/polar_bear.json: weighted cod 3 / salmon 1, then 0~2 + looting.
            case POLAR_BEAR -> {
                int item = rng.nextInt(4) < 3
                        ? (onFire ? PlayerInventory.COD_COOKED : PlayerInventory.COD_RAW)
                        : (onFire ? PlayerInventory.SALMON_COOKED : PlayerInventory.SALMON_RAW);
                int count = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
                if (count > 0) drops.add(new int[]{item, count});
            }
            // entities/turtle.json ordinary pool. The lightning-only bowl belongs to the death-source lane.
            case TURTLE -> {
                int seagrass = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
                if (seagrass > 0) drops.add(new int[]{Blocks.SEAGRASS, seagrass});
            }
            case SKELETON, STRAY, BOGGED -> {
                int bones = rng.nextInt(3) + lootingBonus(rng, lootingLevel); // 뼈 0~2
                if (bones > 0) drops.add(new int[]{PlayerInventory.BONE, bones});
                int arrows = rng.nextInt(3) + lootingBonus(rng, lootingLevel); // 화살 0~2
                if (arrows > 0) drops.add(new int[]{PlayerInventory.ARROW, arrows});
            }
            case ZOMBIE, BABY_ZOMBIE, HUSK, ZOMBIE_VILLAGER ->
                    addZombieFamilyDrops(drops, rng, killedByPlayer, onFire, lootingLevel);
            case BONE_PROCESSION -> {
                drops.add(new int[]{PlayerInventory.BONE, 3 + rng.nextInt(3)});
                drops.add(new int[]{Blocks.FLESH_BONE_PLATE, 1});
            }
            case HANGING_MAW -> {
                drops.add(new int[]{Blocks.FLESH_CLOT_SAC, 1});
                drops.add(new int[]{Blocks.FLESH_MEMBRANE, 1 + rng.nextInt(2)});
            }
            case FLESH_STALKER -> {
                drops.add(new int[]{Blocks.FLESH_FIBER, 1 + rng.nextInt(2)});
                if (rng.nextFloat() < .15F) drops.add(new int[]{Blocks.FLESH_HOOK_CLAW, 1});
            }
            case GHOUL, BABY_GHOUL -> {
                int fibers = (type == MobType.BABY_GHOUL ? 1 : 0) + rng.nextInt(2);
                if (fibers > 0) drops.add(new int[]{Blocks.FLESH_FIBER, fibers});
                if (rng.nextFloat() < .25F) drops.add(new int[]{Blocks.MYSTERY_FLESH, 1});
            }
            case DROWNED -> {
                int flesh = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                // 바닐라 drowned 구리 주괴: random_chance_with_looting 0.11 / 0.02.
                if (killedByPlayer && rng.nextFloat()
                        < lootTableChance(0.11F, 0.13F, 0.02F, lootingLevel)) {
                    drops.add(new int[]{PlayerInventory.COPPER_INGOT, 1});
                }
            }
            case ZOMBIE_PIGMAN, ZOMBIFIED_PIGLIN -> {
                int flesh = rng.nextInt(2) + lootingBonus(rng, lootingLevel); // MC 무약탈 0~1
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                int nuggets = rng.nextInt(2) + lootingBonus(rng, lootingLevel); // MC 무약탈 0~1
                if (nuggets > 0) drops.add(new int[]{PlayerInventory.GOLD_NUGGET, nuggets});
                // 바닐라 금 주괴: random_chance_with_looting 0.025 / 0.01.
                if (killedByPlayer && rng.nextFloat()
                        < lootTableChance(0.025F, 0.035F, 0.01F, lootingLevel)) {
                    drops.add(new int[]{PlayerInventory.GOLD_INGOT, 1});
                }
            }
            case SPIDER, CAVE_SPIDER -> {
                int string = rng.nextInt(3) + lootingBonus(rng, lootingLevel); // 실 0~2
                if (string > 0) drops.add(new int[]{PlayerInventory.STRING, string});
                if (killedByPlayer) {
                    int eyes = rng.nextInt(3) - 1 + lootingBonus(rng, lootingLevel);
                    if (eyes > 0) drops.add(new int[]{PlayerInventory.SPIDER_EYE, eyes});
                }
            }
            case CREEPER -> {
                int powder = rng.nextInt(3) + lootingBonus(rng, lootingLevel); // 화약 0~2
                if (powder > 0) drops.add(new int[]{PlayerInventory.GUNPOWDER, powder});
            }
            case IRON_GOLEM -> {
                // entities/iron_golem pool 순서: 양귀비, 철. 둘 다 약탈 함수가 없다.
                int flowers = rng.nextInt(3);
                if (flowers > 0) drops.add(new int[]{Blocks.FLOWER_RED, flowers});
                drops.add(new int[]{PlayerInventory.IRON_INGOT, 3 + rng.nextInt(3)}); // 철 주괴 3~5
            }
            case COPPER_GOLEM -> {
                // 출시된 26.2 표: uniform 1..3 + round(약탈 레벨 * nextFloat()).
                drops.add(new int[]{PlayerInventory.COPPER_INGOT,
                        1 + rng.nextInt(3) + lootingBonus(rng, lootingLevel)});
            }
            case SLIME -> {
                // 바닐라: 분열이 끝난 최소 크기만 슬라임볼을 떨군다.
                if (slimeSize == 1) {
                    int balls = rng.nextInt(SLIME_BALL_MAX_EXCLUSIVE)
                            + lootingBonus(rng, lootingLevel);
                    if (balls > 0) drops.add(new int[]{PlayerInventory.SLIME_BALL, balls});
                }
            }
            case ENDERMAN -> {
                int pearls = rng.nextInt(ENDER_PEARL_MAX_EXCLUSIVE)
                        + lootingBonus(rng, lootingLevel);
                if (pearls > 0) drops.add(new int[]{PlayerInventory.ENDER_PEARL, pearls});
            }
            case WITCH -> {
                int rolls = WITCH_ROLL_MIN + rng.nextInt(WITCH_ROLL_MAX_EXCLUSIVE);
                for (int roll = 0; roll < rolls; roll++) {
                    short item = WITCH_DROP_ITEMS[rng.nextInt(WITCH_DROP_ITEMS.length)];
                    int count = rng.nextInt(WITCH_STACK_MAX_EXCLUSIVE)
                            + lootingBonus(rng, lootingLevel);
                    if (count > 0) drops.add(new int[]{item, count});
                }
                drops.add(new int[]{PlayerInventory.REDSTONE_DUST,
                        4 + rng.nextInt(5) + lootingBonus(rng, lootingLevel)});
            }
            // entities/ravager.json: exactly one saddle, no conditions or random draw.
            case RAVAGER -> drops.add(new int[]{PlayerInventory.SADDLE, 1});
            case VINDICATOR -> {
                if (killedByPlayer) {
                    int emeralds = rng.nextInt(2) + lootingBonus(rng, lootingLevel);
                    if (emeralds > 0) drops.add(new int[]{PlayerInventory.EMERALD, emeralds});
                }
            }
            case EVOKER -> {
                // Java 1.21.4 entities/evoker: exactly one Totem of Undying has no
                // player-kill or Looting condition; the emerald pool remains the ordinary
                // 0..1 + Looting bonus roll.
                drops.add(new int[]{PlayerInventory.TOTEM_OF_UNDYING, 1});
                if (killedByPlayer) {
                    int emeralds = rng.nextInt(2) + lootingBonus(rng, lootingLevel);
                    if (emeralds > 0) drops.add(new int[]{PlayerInventory.EMERALD, emeralds});
                }
            }
            case ILLUSIONER -> {
                int arrows = rng.nextInt(PILLAGER_ARROW_MAX_EXCLUSIVE)
                        + lootingBonus(rng, lootingLevel);
                if (arrows > 0) drops.add(new int[]{PlayerInventory.ARROW, arrows});
            }
            case PIGMAN -> {
                int nuggets = rng.nextInt(3);
                if (nuggets > 0) drops.add(new int[]{PlayerInventory.GOLD_NUGGET, nuggets});
                int leather = rng.nextInt(2);
                if (leather > 0) drops.add(new int[]{PlayerInventory.LEATHER, leather});
            }
            case STANDARD_BEARER -> {
                int arrows = rng.nextInt(PILLAGER_ARROW_MAX_EXCLUSIVE);
                if (arrows > 0) drops.add(new int[]{PlayerInventory.ARROW, arrows});
                // [TRIAL-GAP] 바닐라 entities/pillager 대장 풀: set_ominous_bottle_amplifier
                // uniform(0, 4). 세 번째 칸이 그 증폭이다(드랍 소비자가 컴포넌트로 싣는다).
                // [RAID-OMEN] 이 풀에는 killed_by_player 조건이 없다: 플레이어가 죽이지 않은 대장도 떨군다.
                drops.add(new int[]{PlayerInventory.OMINOUS_BOTTLE, 1, rng.nextInt(5)});
            }
            case WEB_TRAPPER -> drops.add(new int[]{PlayerInventory.STRING, 1 + rng.nextInt(3)});
            // [EC-MOBS] entities/shulker: random_chance_with_enchanted_bonus(약탈 없으면 0.5, 있으면
            // 0.5625 + 0.0625·(레벨−1)) 로 셜커 껍데기 1. killed_by_player 조건이 없다.
            case SHULKER -> {
                float chance = lootingLevel > 0 ? 0.5625f + (lootingLevel - 1) * 0.0625f : 0.5f;
                if (rng.nextFloat() < chance) drops.add(new int[]{PlayerInventory.SHULKER_SHELL, 1});
            }
            // [EC-MOBS] ItemFrame.dropItem(…, true): 액자 자신 1(넣은 아이템은 MobRuntime 영구 정산이 싣는다).
            case ITEM_FRAME -> drops.add(new int[]{PlayerInventory.ITEM_FRAME, 1});
            case BREACHER -> {
                if (killedByPlayer && rng.nextFloat() < 0.1F) {
                    drops.add(new int[]{PlayerInventory.IRON_INGOT, 1});
                }
            }
            case DEMOLISHER -> drops.add(new int[]{Blocks.GRAVEL, 1 + rng.nextInt(4)});
            case BUILDER -> drops.add(new int[]{Blocks.PLANK, 2 + rng.nextInt(3)});
            case BRIARBACK -> {
                int pork = rng.nextInt(3);
                if (pork > 0) drops.add(new int[]{PlayerInventory.PORK_RAW, pork});
                int leather = rng.nextInt(2);
                if (leather > 0) drops.add(new int[]{PlayerInventory.LEATHER, leather});
            }
            // 좀비곰(WebCraft 창작몹): 썩은 살점 + 부패해도 남는 모피, 드러난 골격에서
            // 나오는 창작 희귀 드랍(뼈). pool 순서 = 난수 소비 순서이며 정적판과 동일하다.
            case ZOMBIE_BEAR -> {
                int flesh = rng.nextInt(ZombieBearRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                int leather = rng.nextInt(ZombieBearRules.LEATHER_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (leather > 0) drops.add(new int[]{PlayerInventory.LEATHER, leather});
                if (killedByPlayer && rng.nextInt(ZombieBearRules.BONE_RARE_ROLL) == 0) {
                    drops.add(new int[]{PlayerInventory.BONE, 1});
                }
            }
            // 좀비 말: 바닐라 entities/zombie_horse.json 은 썩은 살점 0~2 하나뿐이다. 썩은 가죽은
            // 종별 표가 아니라 아래 [ROTTEN-LEATHER] 공통 pool 이 붙인다(RottenLeatherDropRules).
            case ZOMBIE_HORSE -> {
                int flesh = rng.nextInt(ZombieHorseRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            // 좀비 늑대(WebCraft 창작몹): 썩은 살점 + 드러난 골격의 뼈. 썩은 가죽은 같은 이유로
            // 여기 있지 않고 공통 pool 이 붙인다.
            case ZOMBIE_WOLF -> {
                int flesh = rng.nextInt(ZombieWolfRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                if (killedByPlayer && rng.nextInt(ZombieWolfRules.BONE_RARE_ROLL) == 0) {
                    drops.add(new int[]{PlayerInventory.BONE, 1});
                }
            }
            // ── 좀비 동물 10종(stableId 76~85) ──
            // 좀비화 6종은 "썩은 살점 하나"뿐이다 — 살아 있는 원본의 고기·양털·가죽은 나오지
            // 않는다(부패한 개체에서 멀쩡한 식재료가 나오면 썩은 가죽 경제의 근거가 무너진다).
            // 썩은 가죽은 종별 표가 아니라 아래 [ROTTEN-LEATHER] 공통 pool 이 체급으로 붙인다.
            case ZOMBIE_COW -> {
                int flesh = rng.nextInt(ZombieCowRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            case ZOMBIE_PIG -> {
                int flesh = rng.nextInt(ZombiePigRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            // 전단 불가 계약의 사망 쪽 짝: 양털을 떨구지 않는다.
            case ZOMBIE_SHEEP -> {
                int flesh = rng.nextInt(ZombieSheepRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            case ZOMBIE_GOAT -> {
                int flesh = rng.nextInt(ZombieGoatRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            case ZOMBIE_FOX -> {
                int flesh = rng.nextInt(ZombieFoxRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            // 상한 달걀은 확률 굴림이라 약탈을 싣지 않는다(정수 굴림 형태 보존 —
            // 거미 눈과 같은 divergence).
            case ZOMBIE_CHICKEN -> {
                int flesh = rng.nextInt(ZombieChickenRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                if (rng.nextInt(ZombieChickenRules.SPOILED_EGG_ROLL) == 0) {
                    drops.add(new int[]{PlayerInventory.SPOILED_EGG, 1});
                }
            }
            case CARRION_STAG -> {
                int flesh = rng.nextInt(CarrionStagRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                if (killedByPlayer && rng.nextInt(CarrionStagRules.BONE_RARE_ROLL) == 0) {
                    drops.add(new int[]{PlayerInventory.BONE, 1});
                }
            }
            case CARRION_BOAR -> {
                int flesh = rng.nextInt(CarrionBoarRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                if (killedByPlayer && rng.nextInt(CarrionBoarRules.BONE_RARE_ROLL) == 0) {
                    drops.add(new int[]{PlayerInventory.BONE, 1});
                }
            }
            case CARRION_CROW -> {
                int flesh = rng.nextInt(CarrionCrowRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                int feathers = rng.nextInt(CarrionCrowRules.FEATHER_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (feathers > 0) drops.add(new int[]{PlayerInventory.FEATHER, feathers});
            }
            // 낙타 husk 는 바닐라 전리품 표 그대로 썩은 살점 2~3 이다(등급 A).
            case CAMEL_HUSK -> {
                int flesh = CamelHuskRules.ROTTEN_FLESH_MIN
                        + rng.nextInt(CamelHuskRules.ROTTEN_FLESH_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
            }
            case GLOAMKITE -> {
                int feathers = rng.nextInt(3);
                if (feathers > 0) drops.add(new int[]{PlayerInventory.FEATHER, feathers});
                int string = rng.nextInt(2);
                if (string > 0) drops.add(new int[]{PlayerInventory.STRING, string});
            }
            // [GUARDIAN] 바닐라 entities/guardian.json · entities/elder_guardian.json 그대로다.
            // pool 순서가 곧 난수 소비 순서라 정적판 standaloneMobDrops 와 문자 그대로 같다.
            case GUARDIAN, ELDER_GUARDIAN -> {
                // pool 1: 프리즈머린 조각 uniform 0..2 + enchanted_count_increase(약탈) 0..1.
                int shards = rng.nextInt(GUARDIAN_SHARD_MAX_EXCLUSIVE)
                        + lootingBonus(rng, lootingLevel);
                if (shards > 0) drops.add(new int[]{PlayerInventory.PRISMARINE_SHARD, shards});
                // 엘더는 대구 weight=3, 일반 가디언은 weight=2다. 선택된 실제 아이템에는
                // enchanted_count_increase가 붙으므로 두 엔트리 모두 약탈 개수를 적용한다.
                int codWeight = type == MobType.ELDER_GUARDIAN ? 3 : GUARDIAN_COD_WEIGHT;
                int pick = rng.nextInt(codWeight + GUARDIAN_CRYSTAL_WEIGHT + 1);
                if (pick < codWeight) {
                    drops.add(new int[]{
                        onFire ? PlayerInventory.COD_COOKED : PlayerInventory.COD_RAW,
                        1 + lootingBonus(rng, lootingLevel)});
                } else if (pick < codWeight + GUARDIAN_CRYSTAL_WEIGHT) {
                    drops.add(new int[]{PlayerInventory.PRISMARINE_CRYSTALS,
                            1 + lootingBonus(rng, lootingLevel)});
                }
                // [GUARDIAN] 엘더 전용 pool 3: killed_by_player → 젖은 스펀지 1.
                if (type == MobType.ELDER_GUARDIAN && killedByPlayer) {
                    drops.add(new int[]{PlayerInventory.WET_SPONGE, 1});
                }
                // 일반 pool 3 / 엘더 pool 4: credited rare fishing subtable.
                if (killedByPlayer && rng.nextFloat()
                        < lootTableChance(GUARDIAN_FISH_CHANCE, 0.035F, 0.01F, lootingLevel)) {
                    drops.add(rollGuardianFish(rng, onFire));
                }
                // [ARMOR-TRIM] 엘더 pool 5: empty weight 4 → Tide 형판 weight 1 순서의 가중 선택
                // (조건 없음 — 플레이어 처치가 아니어도 20%). nextInt(5) 소비는 예전과 같다.
                if (type == MobType.ELDER_GUARDIAN && rng.nextInt(5) >= 4) {
                    drops.add(new int[]{PlayerInventory.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 1});
                }
            }
            // [BRIMSTONE] 황린 잠복자. 굴림 순서는 유황 → 마그마 크림 → 갑각 파편으로 고정하고,
            // 이 순서 자체가 계약이다(정적판 사본이 같은 수열을 뽑아야 한다). 마그마 크림과
            // 갑각 파편은 약탈의 영향을 받지 않는 고정 확률이라 lootingChance 를 쓰지 않는다 —
            // 엘리트 처치 보상이 인챈트로 배가되면 화염 저항 사슬의 희소성이 무너진다.
            case BRIMSTONE_LURKER -> {
                int sulfur = BrimstoneLurkerRules.SULFUR_MIN
                        + rng.nextInt(BrimstoneLurkerRules.SULFUR_ROLL)
                        + lootingBonus(rng, lootingLevel);
                if (sulfur > 0) drops.add(new int[]{Blocks.SULFUR_BLOCK, sulfur});
                if (rng.nextFloat() < BrimstoneLurkerRules.MAGMA_CREAM_CHANCE) {
                    drops.add(new int[]{PlayerInventory.MAGMA_CREAM, 1});
                }
                if (rng.nextFloat() < BrimstoneLurkerRules.CARAPACE_SHARD_CHANCE) {
                    drops.add(new int[]{(short) Blocks.BRIMSTONE_CARAPACE_TROPHY, 1});
                }
            }
            // ── [WAVE-86-97] 신종 12종의 사망 드랍. 인용은 전부 1.21.4(해피 가스트만 1.21.6)
            //    데이터팩 `data/minecraft/loot_table/entities/<종>.json` 원문이고, 정적판
            //    `standaloneMobDrops` 의 같은 절과 굴림 순서·개수가 글자 그대로 같다.
            //
            //    드랍이 **없는** 넷은 여기 case 를 두지 않고 default 로 남긴다 — 바닐라도
            //    풀이 비어 있기 때문이다:
            //      · sniffer  : `entities/sniffer.json` 에 풀이 없다(이끼 뭉치를 떨구지 않는다.
            //                   바닐라의 sniffer 산출은 오직 **냄새 맡기**로 나오는 씨앗이다).
            //      · wandering_trader : `entities/wandering_trader.json` 은 pools 가 비어 있다
            //                   (거래 재고는 loot table 이 아니라 오퍼 표가 소유한다).
            //      · happy_ghast : 1.21.6 에서도 사망 드랍 풀이 없다(온순한 탈것).
            //      · sulfur_cube 의 **작은 개체**: 리서치 §5 "작은 개체는 없음" 이라
            //                   아래 case 가 영속 baby 형태가 아닌 성체만 떨군다.
            //
            // `entities/cat.json`: string · set_count uniform 0~2. 약탈 함수는 없다.
            case CAT -> {
                int string = rng.nextInt(3);
                if (string > 0) drops.add(new int[]{PlayerInventory.STRING, string});
            }
            // `entities/trader_llama.json` 은 라마와 같다: item leather · 0~2 · looting 0~1.
            case TRADER_LLAMA -> {
                int leather = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
                if (leather > 0) drops.add(new int[]{PlayerInventory.LEATHER, leather});
            }
            // `entities/skeleton_horse.json`: item bone · 0~2 · looting 0~1.
            case SKELETON_HORSE -> {
                int bones = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
                if (bones > 0) drops.add(new int[]{PlayerInventory.BONE, bones});
            }
            // `entities/phantom.json`: 풀 전체에 killed_by_player 조건이 붙어 있고
            // item phantom_membrane · 0~1 · looting 0~1 이다. 플레이어가 죽이지 않았으면
            // 바닐라도 굴림 자체가 없으므로 여기서도 난수를 쓰지 않는다.
            case PHANTOM -> {
                if (killedByPlayer) {
                    int membranes = rng.nextInt(2) + lootingBonus(rng, lootingLevel);
                    if (membranes > 0) {
                        drops.add(new int[]{PlayerInventory.PHANTOM_MEMBRANE, membranes});
                    }
                }
            }
            // `entities/snow_golem.json`: item snowball · set_count uniform 0~15.
            // 약탈 항이 없어(looting_enchant 가 붙어 있지 않다) 레벨과 무관하게 같은 표다.
            case SNOW_GOLEM -> {
                int snowballs = rng.nextInt(16);
                if (snowballs > 0) drops.add(new int[]{PlayerInventory.SNOWBALL, snowballs});
            }
            // `entities/breeze.json`: 풀 조건 killed_by_player, item breeze_rod · set_count uniform 1~2 ·
            // enchanted_count_increase(looting, uniform 1~2). 약탈 가산은 round(레벨 × U(1,2)) 이고
            // 레벨 0 이면 난수를 쓰지 않는다(EnchantedCountIncreaseFunction).
            case BREEZE -> {
                if (killedByPlayer) {
                    int rods = 1 + rng.nextInt(2);
                    if (lootingLevel > 0) {
                        rods += EnchantmentRules.lootingUniformBonusCount(lootingLevel, 1.0f, 2.0f,
                                rng.nextFloat());
                    }
                    drops.add(new int[]{PlayerInventory.BREEZE_ROD, rods});
                }
            }
            // `entities/warden.json`: item sculk_catalyst 하나뿐이고 조건도 함수도 없다(항상 1).
            // 난수를 소비하지 않는 유일한 신종 드랍이다.
            case WARDEN -> drops.add(new int[]{PlayerInventory.SCULK_CATALYST, 1});
            // 파치드(1.21.11). [PARCHED-FAMILY] 옛 판은 좀비 계열 표(썩은 살점 0~2)를 썼다.
            // 원문 드랍표는 스켈레톤 계열이다 — 뼈 0~2(66.67%) · 화살 0~2(66.67%). 굴림 순서도
            // 스켈레톤·스트레이·보그드와 같은 뼈→화살이라 그쪽 케이스와 글자 그대로 같은 형태다.
            // 원문의 세 번째 항목인 나약함 화살 0~1(플레이어/길들인 늑대 처치 한정)은 이 저장소에
            // 팁 화살 아이템이 없어 옮기지 못한다 — 스트레이의 감속 화살·보그드의 독 화살이 이미
            // 같은 이유로 빠져 있고, 그 선례를 그대로 따른다(ParchedRules divergence 1).
            case PARCHED -> {
                int bones = rng.nextInt(ParchedRules.BONE_ROLL) + lootingBonus(rng, lootingLevel);
                if (bones > 0) drops.add(new int[]{PlayerInventory.BONE, bones});
                int arrows = rng.nextInt(ParchedRules.ARROW_ROLL) + lootingBonus(rng, lootingLevel);
                if (arrows > 0) drops.add(new int[]{PlayerInventory.ARROW, arrows});
            }
            // Java 26.3-snapshot-7 entities/sulfur_cube has no loot pools.
            case SULFUR_CUBE -> { }
            // 노틸러스(1.21.11). [B] «Nautilus» 드랍표: 앵무조개 껍데기 0~1, 기본 5 %,
            // 약탈 레벨당 +1 %(레벨 III 에서 8 %), 그리고 풀 전체에 <b>"Only when killed by a
            // player or a tamed wolf"</b> 조건이 붙는다 — 이 저장소에서 그 조건의 이름이
            // {@code killedByPlayer}(= playerCredit, 길들인 늑대 처치 크레딧 포함)다.
            // 형태는 {@code random_chance_with_looting} chance 0.05 / multiplier 0.01 이라
            // 토끼발·드라운드 구리와 같은 자리에 같은 모양으로 선다.
            //
            // 좀비 노틸러스는 껍데기를 떨구지 않는다 — 아래 별도 case 의 썩은 살점만 떨군다.
            case NAUTILUS -> {
                if (killedByPlayer && rng.nextFloat() < lootTableChance(
                        NAUTILUS_SHELL_CHANCE, NAUTILUS_SHELL_ENCHANTED_BASE_CHANCE,
                        NAUTILUS_SHELL_LOOTING_STEP, lootingLevel)) {
                    drops.add(new int[]{PlayerInventory.NAUTILUS_SHELL, 1});
                }
            }
            // [ZOMBIE-NAUTILUS-FIX] 좀비 앵무조개(1.21.11). [B] «Zombie Nautilus» 드랍표:
            // 썩은 살점 <b>0~3</b>(약탈 III 에서 0~6)이고, 풀 전체에 노틸러스와 같은
            // <b>"Only when killed by players or tamed wolves"</b> 조건이 붙는다 — 이 저장소에서
            // 그 조건의 이름이 {@code killedByPlayer}(= playerCredit, 길들인 늑대 크레딧 포함)다.
            // 근거는 {@code docs/research/mc-nautilus-1-21-11.md} §8-4 가 소유한다.
            //
            // <b>공용 pool 과의 합성 순서</b>: 이 종별 표가 <b>먼저</b> 굴고, 그 뒤에 아래
            // [ROTTEN-LEATHER] 공용 pool(썩은 가죽)이 붙는다. 즉 한 마리가
            // {@code [썩은 살점, 썩은 가죽]} 순서로 내놓고, 난수도 그 순서로 소비된다.
            // 이 절이 서기 전에는 종별 표가 없어 공용 pool 만 탔으므로, 좀비 앵무조개의
            // 난수 소비가 이 웨이브에서 <b>플레이어 처치일 때만</b> 1 회(+약탈 시 1 회) 늘어난다.
            // 다른 종의 수열은 case 가 종별로 닫혀 있어 한 글자도 밀리지 않는다.
            //
            // 비플레이어 처치는 굴림 자체를 쓰지 않는다(닫힌 조건) — 그래야 노틸러스·팬텀과
            // 같은 형태가 되고, 골든 벡터의 스트림 위치 감시자가 그 사실을 잡는다.
            case ZOMBIE_NAUTILUS -> {
                if (killedByPlayer) {
                    int flesh = rng.nextInt(ZOMBIE_NAUTILUS_ROTTEN_FLESH_ROLL)
                            + lootingBonus(rng, lootingLevel);
                    if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
                }
            }
            // 박쥐·좀벌레·벌은 바닐라도 무드랍이라 default로 남긴다.
            default -> { /* 이 트랙에 드랍표가 없는 몹 */ }
        }
        // [ROTTEN-LEATHER] 좀비 동물의 남은 가죽. 종별 표 뒤에 한 번만 붙여 신종이 술어에
        // 추가돼도 기존 종의 난수 소비 수열 앞부분이 그대로 보존되게 한다. 개수는 체급 티어가
        // 정하되 굴림 횟수는 티어와 무관하게 항상 1 이다(기존 4종은 전부 LARGE 라 불변).
        if (RottenLeatherDropRules.rottenLeatherDrop(type)) {
            int rottenLeather = RottenLeatherDropRules.rollRottenLeather(type, rng)
                    + lootingBonus(rng, lootingLevel);
            if (rottenLeather > 0) {
                drops.add(new int[]{PlayerInventory.ROTTEN_LEATHER, rottenLeather});
            }
        }
        // WebCraft raid specialists have a deliberately rare, immediately useful prize. The
        // death settlement is already exactly-once, so this cannot duplicate across reconnects.
        if (killedByPlayer && isRaidRewardCarrier(type) && rng.nextInt(64) == 0) {
            drops.add(new int[]{PlayerInventory.GOLDEN_APPLE, 1});
        }
        return drops;
    }

    /**
     * [PIGLIN-LOOTING] 드랍 개수에 실리는 약탈 가산분. 레벨 0이면 난수를 소비하지 않으므로
     * 약탈 없는 처치는 이 기능이 붙기 전과 같은 수열을 낸다(바닐라도 {@code i == 0} 이면 즉시 반환).
     */
    private static int lootingBonus(MobRandom rng, int lootingLevel) {
        if (lootingLevel <= 0) return 0;
        return EnchantmentRules.lootingBonusCount(lootingLevel, rng.nextFloat());
    }

    private static boolean isRaidRewardCarrier(MobType type) {
        return type == MobType.EVOKER || type == MobType.ILLUSIONER
                || type == MobType.RAVAGER || type == MobType.STANDARD_BEARER
                || type == MobType.WEB_TRAPPER || type == MobType.BREACHER
                || type == MobType.DEMOLISHER || type == MobType.BUILDER;
    }

    private static void addZombieFamilyDrops(List<int[]> drops, MobRandom rng,
                                             boolean killedByPlayer, boolean onFire,
                                             int lootingLevel) {
        int flesh = rng.nextInt(3) + lootingBonus(rng, lootingLevel);
        if (flesh > 0) drops.add(new int[]{PlayerInventory.ROTTEN_FLESH, flesh});
        // 바닐라 좀비 희귀 드랍: random_chance_with_looting 0.025 / 0.01.
        if (!killedByPlayer || rng.nextFloat()
                >= lootTableChance(0.025F, 0.035F, 0.01F, lootingLevel)) return;
        short[] rare = {PlayerInventory.IRON_INGOT, PlayerInventory.CARROT, PlayerInventory.POTATO};
        short item = rare[rng.nextInt(rare.length)];
        if (onFire && item == PlayerInventory.POTATO) item = PlayerInventory.BAKED_POTATO;
        drops.add(new int[]{item, 1});
    }

    private void damagePlayerFrom(String nickname, int amount, String cause, String killer,
                                  double sourceX, double sourceZ, boolean axeAttack) {
        PlayerTickState p = rt.players().get(nickname);
        if (p != null) damageFromAndSync(p, amount, cause, killer, sourceX, sourceZ, axeAttack);
    }

    private enum MobAttackOutcome {
        REJECTED,
        BLOCKED,
        SHIELD_BLOCKED,
        DAMAGED;

        private boolean accepted() {
            return this != REJECTED;
        }

        private boolean damaged() {
            return this == DAMAGED;
        }
    }

    private MobAttackOutcome damagePlayerFromMob(String nickname, int amount, long mobId) {
        PlayerTickState p = rt.players().get(nickname);
        Mob attacker = findMob(mobId);
        if (p == null || attacker == null || attacker.isDead() || attacker.removed
                || p.isDead() || amount <= 0) {
            return MobAttackOutcome.REJECTED;
        }
        boolean protectedBefore = p.hurtProtected();
        double fleshArmorScale = FleshArmorRules.knockbackScale(
                p.inventory().equippedType(ArmorSlot.CHESTPLATE), attacker.type);
        boolean axeAttack = isAxe(attacker.heldItem());
        PlayerTickState.DirectionalDamageResult result = damageFromAndSync(
                p, amount, "mob", typeName(attacker), attacker.x, attacker.z, axeAttack);
        if (result == PlayerTickState.DirectionalDamageResult.BLOCKED) {
            if (attacker instanceof Ravager ravager && !ravager.onShieldBlocked(rng)) {
                applyRavagerStrongKnockback(attacker, p);
            }
            return MobAttackOutcome.SHIELD_BLOCKED;
        }
        // [ENCHANT-WIDE] 가시: hurtServer 가 성립한 근접 피격이면(무적 틈의 차액 피해 포함) post_attack 이 돈다.
        if (result.damaged()) applyThorns(p, attacker);
        // [MOB-EQUIP] 무기의 발화(fire_aspect post_attack): 받아들여진 근접 피격이면 4·level 초 점화하고
        // 대상의 화염으로부터 보호가 줄인다(바닐라 Mob.doHurtTarget → EnchantmentHelper.doPostAttackEffects).
        int fireAspect = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.FIRE_ASPECT);
        if (result.damaged() && fireAspect > 0) {
            p.setFireTicks(Math.max(p.fireTicks(), EnvironmentSystem.burningTicks(p,
                    (EnchantmentRules.fireAspectIgniteMcTicks(fireAspect) + 1) / 2)));
        }
        if (!result.damaged() || protectedBefore) return MobAttackOutcome.BLOCKED;

        applyMobAttackKnockback(p, attacker, attacker.type == MobType.RAVAGER ? 1.5 : 1.0, fleshArmorScale);
        return MobAttackOutcome.DAMAGED;
    }

    /**
     * [ENCHANT-WIDE] 가시(thorns.json post_attack, enchanted=victim, affected=attacker): 착용 방어구마다
     * {@code nextFloat() < 0.15·level} 이면 공격한 몹이 {@code randomBetween(1, 5)} 피해를 받고 그
     * 방어구가 내구 2 를 잃는다(한 점마다 내구성 판정). 가시로 죽은 몹은 플레이어 처치로 정산한다.
     */
    private void applyThorns(PlayerTickState p, Mob attacker) {
        boolean inventoryChanged = false;
        for (ArmorSlot slot : new ArmorSlot[] {ArmorSlot.BOOTS, ArmorSlot.LEGGINGS,
                ArmorSlot.CHESTPLATE, ArmorSlot.HELMET}) {
            int level = p.inventory().equippedWideEnchantments(slot)
                    .level(com.gameexpert.engine.enchant.EnchantmentRules.THORNS);
            if (level <= 0 || attacker.isDead() || attacker.removed) continue;
            if (!com.gameexpert.engine.enchant.EnchantmentRules.thornsTriggers(
                    level, thornsRandom.nextFloat())) continue;
            float reflected = com.gameexpert.engine.enchant.EnchantmentRules.thornsDamage(
                    thornsRandom.nextFloat());
            if (attacker.damage(reflected, runtime.worldTick())) {
                attacker.rememberPlayerKillCredit(p.nickname());
                broadcast(new WsMessages.MobHurt(attacker.id, attacker.isDead()));
                if (attacker.isDead() && !mobDropsHandled.contains(attacker.id)) {
                    pendingDespawnReasons.put(attacker.id, "death");
                    runtime.splitSlimeOnDeath(attacker, rng);
                    spawnMobDrops(attacker, true, 0);
                    mobDropsHandled.add(attacker.id);
                }
            }
            short broken = p.inventory().damageEquippedPiece(slot,
                    com.gameexpert.engine.enchant.EnchantmentRules.THORNS_SELF_DAMAGE);
            if (broken != PlayerInventory.EMPTY) p.recordBrokenItem(broken);
            inventoryChanged = true;
        }
        if (inventoryChanged) {
            WebSocketSession session = rt.session(p.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                        WorldTickLoop.inventoryMessage(p));
            }
        }
    }

    /** [ENCHANT-WIDE] 가시 판정 굴림(테스트가 고정한다). */
    private java.util.Random thornsRandom = new java.util.Random();

    void setThornsRandomForTest(java.util.Random random) {
        this.thornsRandom = random;
    }

    /**
     * [MOB-EQUIP] 몹 화살 명중의 뒤처리: 피해가 들지 않았으면 화염 점화를 되돌리고, 들었으면 밀어내기 push.
     * 화살 수평 방향은 발사 원점(직전 위치)에서 명중 플레이어로의 방향이다.
     */
    private void applyMobArrowPunch(PlayerTickState target,
            com.gameexpert.engine.enchant.WideEnchantments bow, int healthBefore,
            int previousFire, MobEvent.AttackPlayer attack) {
        boolean damaged = target.health() < healthBefore;
        if (!damaged) {
            target.setFireTicks(previousFire);
            return;
        }
        int punch = bow.level(EnchantmentRules.PUNCH);
        if (punch <= 0) return;
        double dx = target.x() - attack.sourceX();
        double dz = target.z() - attack.sourceZ();
        double length = Math.hypot(dx, dz);
        double push = EnchantmentRules.punchHorizontalPush(punch, 0.0);
        if (length <= 1e-12 || push <= 0.0) return;
        // AbstractArrow.doKnockback → Entity.push 는 속도에 순수 가산한다(넉백의 /2 합성이 아니다).
        // 이동은 클라 권위라 권위 임펄스(블록/초)로 나른다 — 정적판도 같은 playerImpulse 다.
        sendImpulse(target, new WsMessages.PlayerImpulse(target.nickname(),
                dx / length * push * 20.0, EnchantmentRules.PUNCH_VERTICAL_PUSH * 20.0,
                dz / length * push * 20.0, null));
    }

    private void applyMobAttackKnockback(PlayerTickState p, Mob attacker, double strength, double armorScale) {
        double dx = p.x() - attacker.x;
        double dz = p.z() - attacker.z;
        double d = Math.hypot(dx, dz);
        double kbX = 0.0;
        double kbZ = 0.0;
        if (d > 1e-9) {
            kbX = dx / d * CombatRules.KNOCKBACK_HORIZONTAL_BPS * strength;
            kbZ = dz / d * CombatRules.KNOCKBACK_HORIZONTAL_BPS * strength;
        }
        double kbY = groundedAtCurrentPose(rt.accessor(), p)
                ? CombatRules.KNOCKBACK_VERTICAL_BPS : 0.0;
        // [MOB-EQUIP] 무기의 밀치기: 바닐라 Mob.doHurtTarget 의 knockback(0.5·level, 시선 방향) 2차 호출.
        // 몹 yaw 는 atan2(dz, dx) 라 시선 수평 방향이 (cos, sin) 이다.
        int knockback = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.KNOCKBACK);
        double bonusX = 0.0;
        double bonusZ = 0.0;
        if (knockback > 0) {
            double bonus = CombatRules.KNOCKBACK_BONUS_BPS * knockback;
            bonusX = Math.cos(attacker.yaw) * bonus;
            bonusZ = Math.sin(attacker.yaw) * bonus;
        }
        p.setHurtKnockback(kbX * armorScale, kbY * armorScale, kbZ * armorScale,
                bonusX * armorScale, bonusZ * armorScale);
    }

    /**
     * [SPEAR-MOB] 몹 찌르기의 {@code causeExtraKnockback(target, 0.4, …)}: 대상 쪽이 아니라 쓰는 몹의 <b>시선 수평 방향</b>
     * (이 저장소 몹 yaw 의 (cos, sin))으로 민다. 밀치기 인챈트는 같은 방향의 2차 넉백이다.
     */
    private void applyMobStabKnockback(PlayerTickState p, Mob attacker) {
        double forwardX = Math.cos(attacker.yaw);
        double forwardZ = Math.sin(attacker.yaw);
        double kbY = groundedAtCurrentPose(rt.accessor(), p) ? CombatRules.KNOCKBACK_VERTICAL_BPS : 0.0;
        int knockback = attacker.heldWeaponEnchantmentLevel(EnchantmentRules.KNOCKBACK);
        double bonus = knockback > 0 ? CombatRules.KNOCKBACK_BONUS_BPS * knockback : 0.0;
        p.setHurtKnockback(forwardX * CombatRules.KNOCKBACK_HORIZONTAL_BPS, kbY,
                forwardZ * CombatRules.KNOCKBACK_HORIZONTAL_BPS, forwardX * bonus, forwardZ * bonus);
    }

    private static void applyRavagerStrongKnockback(Mob source, PlayerTickState target) {
        double dx = target.x() - source.x;
        double dz = target.z() - source.z;
        target.setHurtKnockback(
                Ravager.strongKnockbackBps(dx, dx, dz),
                Ravager.strongKnockbackVerticalBps(),
                Ravager.strongKnockbackBps(dz, dx, dz),
                0.0, 0.0);
        target.markKnockbackOnly("mob", typeName(source));
    }

    private static boolean isAxe(short itemType) {
        return itemType == PlayerInventory.AXE
                || itemType == PlayerInventory.STONE_AXE
                || itemType == PlayerInventory.IRON_AXE
                || itemType == PlayerInventory.GOLD_AXE
                || itemType == PlayerInventory.DIAMOND_AXE;
    }

    /** 방패 마모/파괴가 일어난 공격은 같은 틱에 개인 인벤토리 갱신까지 보낸다. */
    private PlayerTickState.DirectionalDamageResult damageFromAndSync(
                                      PlayerTickState player, int amount, String cause, String killer,
                                      double sourceX, double sourceZ, boolean axeAttack) {
        long before = player.inventory().revision();
        PlayerTickState.DirectionalDamageResult result = player.damageFromResult(
                amount, cause, killer, sourceX, sourceZ, axeAttack, System.nanoTime());
        if (player.inventory().revision() != before) {
            WebSocketSession session = rt.session(player.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                        WorldTickLoop.inventoryMessage(player));
            }
        }
        return result;
    }

    static boolean groundedAtCurrentPose(TerrainAccessor accessor, PlayerTickState player) {
        final double halfWidth = 0.3;
        final double eps = 1e-3;
        int x0 = (int) Math.floor(player.x() - halfWidth + eps);
        int x1 = (int) Math.floor(player.x() + halfWidth - eps);
        int z0 = (int) Math.floor(player.z() - halfWidth + eps);
        int z1 = (int) Math.floor(player.z() + halfWidth - eps);
        int by = (int) Math.floor(player.y() - 0.06);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                TerrainAccessor.ResidentBlock resident = accessor.residentBlock(x, by, z);
                if (!resident.isAvailable() || Fluids.isSolid(resident.blockType())) return true;
            }
        }
        return false;
    }

    // ── 몹 브로드캐스트: 스폰 · 변경분 · 디스폰. 몹 집합/위치가 바뀌었으면 true(welcome 스냅샷 갱신 신호). ──
    boolean broadcastMobs(Set<Long> removedIds, List<Mob> activeMobs) {
        boolean changed = false;
        Set<Long> currentIds = mobCurrentIdsBuf;
        currentIds.clear();
        for (Mob mob : activeMobs) if (visibleMob(mob)) currentIds.add(mob.id);
        Map<String, List<Long>> byReason = despawnByReasonBuf;
        byReason.clear();
        // Only the previous active set is visited.  Removing state for hibernated entities must never rescan the
        // complete history of parked mobs.
        for (long id : lastBroadcastActiveMobIds) {
            if (!currentIds.contains(id) && !removedIds.contains(id)) {
                lastMobState.remove(id);
                welcomeMobState.remove(id);
                byReason.computeIfAbsent("far", ignored -> new ArrayList<>()).add(id);
            }
        }

        List<MobSpawnDto> spawns = null;
        List<MobUpdateDto> updates = null;
        List<com.gameexpert.engine.mob.PlayerSnapshot> lookPlayers = world.players();
        for (Mob m : activeMobs) {
            refreshVillagerAppearance(m);
            if (!visibleMob(m)) continue;
            // [MOB-LOOK] LookControl: one authority tick of head turn toward the look target.
            if (!m.isDead()) {
                com.gameexpert.engine.mob.MobHeadLook.step(m, lookPlayers, m.trackedTargetNickname());
            }
            if (!lastBroadcastActiveMobIds.contains(m.id)) {
                if (spawns == null) spawns = new ArrayList<>();
                MobSpawnDto spawn = spawnDto(m);
                spawns.add(spawn);
                welcomeMobState.put(m.id, spawn);
                lastMobState.put(m.id, MobBroadcastState.capture(m));
                continue;
            }
            MobBroadcastState prev = lastMobState.get(m.id);
            if (prev == null || !prev.matches(m)) {
                if (updates == null) updates = new ArrayList<>();
                MobUpdateDto now = updateDto(m);
                updates.add(now);
                lastMobState.put(m.id, MobBroadcastState.capture(m));
                welcomeMobState.put(m.id, spawnDto(m));
            }
        }
        if (spawns != null) {
            broadcast(new WsMessages.MobSpawn(spawns));
            changed = true;
        }
        // [EC-MOBS] 새로 걸린 액자의 설치음(스폰 방송 뒤).
        for (DeferredMobSound deferred : deferredSpawnSounds) {
            if (!deferred.mob().isDead() && !deferred.mob().removed) mobSound(deferred.mob(), deferred.kind());
        }
        deferredSpawnSounds.clear();
        if (updates != null) {
            broadcast(new WsMessages.MobUpdate(updates));
            changed = true;
        }

        for (long id : removedIds) {
            String reason = pendingDespawnReasons.remove(id);
            boolean wasVisible = lastMobState.containsKey(id) || lastBroadcastActiveMobIds.contains(id);
            lastBroadcastActiveMobIds.remove(id);
            if (!wasVisible) continue;
            if (reason == null) reason = "death";
            byReason.computeIfAbsent(reason, k -> new ArrayList<>()).add(id);
            lastMobState.remove(id);
            welcomeMobState.remove(id);
        }
        for (Map.Entry<String, List<Long>> e : byReason.entrySet()) {
            broadcast(new WsMessages.MobDespawn(e.getValue(), e.getKey()));
            changed = true;
        }
        lastBroadcastActiveMobIds.clear();
        lastBroadcastActiveMobIds.addAll(currentIds);
        return changed;
    }

    // ── 화살 브로드캐스트: 스폰 · 위치 · 제거 ──
    private void broadcastArrows() {
        broadcastArrows(runtime.arrowsInChunks(rt.activeSimulationChunksForMobTick()));
    }

    void broadcastArrows(List<ProjectileSim> activeArrows) {
        Set<Long> currentIds = arrowCurrentIdsBuf;
        currentIds.clear();
        for (ProjectileSim arrow : activeArrows) currentIds.add(arrow.id);
        // Re-entering a player union requires a fresh projectileSpawn.  Restrict cleanup to the previous active
        // set so a long exploration history cannot turn this into a historical-entity scan.
        // A terminated projectile keeps its known membership until the removal receipt is published below.
        for (long id : lastBroadcastActiveArrowIds) {
            if (!currentIds.contains(id) && !terminatedArrows.containsKey(id)) {
                knownArrows.remove(id);
                lastArrowPositions.remove(id);
            }
        }

        List<ProjectilePos> positions = null;
        for (ProjectileSim a : activeArrows) {
            boolean newlyKnown = knownArrows.add(a.id);
            if (newlyKnown) {
                broadcast(projectileSpawnMessage(a));
            }
            ProjectilePos previous = lastArrowPositions.get(a.id);
            if (newlyKnown || projectilePositionChanged(previous, a)) {
                if (positions == null) positions = new ArrayList<>();
                // [TRIAL-GAP] 효과 구름은 반지름 변화도 위치 갱신에 싣는다.
                ProjectilePos position = a.isCloud()
                        ? new ProjectilePos(a.id, a.x, a.y, a.z, a.cloudRadius)
                        : a.inGround ? new ProjectilePos(a.id, a.x, a.y, a.z, null, Boolean.TRUE)
                        : new ProjectilePos(a.id, a.x, a.y, a.z);
                positions.add(position);
                lastArrowPositions.put(a.id, position);
            }
        }
        if (positions != null) broadcast(new WsMessages.ProjectileUpdate(positions));

        List<ProjectileRemoval> removed = null;
        Iterator<Map.Entry<Long, ProjectileRemoval>> it = terminatedArrows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ProjectileRemoval> entry = it.next();
            long id = entry.getKey();
            if (knownArrows.remove(id)) {
                if (removed == null) removed = new ArrayList<>();
                removed.add(entry.getValue());
                lastArrowPositions.remove(id);
            }
            lastBroadcastActiveArrowIds.remove(id);
            it.remove();
        }
        if (removed != null) broadcast(new WsMessages.ProjectileRemove(removed));
        lastBroadcastActiveArrowIds.clear();
        lastBroadcastActiveArrowIds.addAll(currentIds);
    }

    static boolean projectilePositionChanged(ProjectilePos previous, ProjectileSim current) {
        return previous == null || projectilePositionChanged(
                previous.getX(), previous.getY(), previous.getZ(), current)
                // [ARROW-GROUND] 제자리에서 박히거나(블록이 덮침) 떨어지기 시작한 전이도 싣는다.
                || Boolean.TRUE.equals(previous.getInGround()) != current.inGround
                // [TRIAL-GAP] 효과 구름은 제자리에서 반지름만 줄어든다.
                || current.isCloud()
                        && (previous.getRadius() == null || Double.doubleToLongBits(
                                previous.getRadius()) != Double.doubleToLongBits(current.cloudRadius));
    }

    private static boolean projectilePositionChanged(
            double previousX, double previousY, double previousZ, ProjectileSim current) {
        return Double.doubleToLongBits(previousX) != Double.doubleToLongBits(current.x)
                || Double.doubleToLongBits(previousY) != Double.doubleToLongBits(current.y)
                || Double.doubleToLongBits(previousZ) != Double.doubleToLongBits(current.z);
    }

    private void publishSnapshot() {
        List<Mob> active = runtime.mobsInChunks(rt.activeSimulationChunksForMobTick());
        // Loading persistence happens before the first player is known.  That one bootstrap welcome is not a
        // simulation pass; subsequent owner ticks and welcomes use only active buckets.
        if (!rt.started() && active.isEmpty() && rt.activeSimulationChunksForMobTick().isEmpty()) {
            active = runtime.mobs();
        }
        publishSnapshot(active);
    }

    /** 이미 계산된 활성 union 목록으로 welcome 스냅샷을 재구성한다(틱 경로의 mobsInChunks 재계산 제거). */
    void publishSnapshot(List<Mob> active) {
        List<MobSpawnDto> snap = new ArrayList<>();
        Set<Long> currentIds = mobCurrentIdsBuf;
        currentIds.clear();
        for (Mob m : active) {
            if (!visibleMob(m)) continue;
            currentIds.add(m.id);
            MobSpawnDto cached = welcomeMobState.get(m.id);
            if (cached == null) {
                cached = spawnDto(m);
                welcomeMobState.put(m.id, cached);
            }
            snap.add(cached);
        }
        welcomeMobState.keySet().retainAll(currentIds);
        welcomeSnapshot = List.copyOf(snap);
    }

    private static boolean visibleMob(Mob mob) {
        return !(mob instanceof Bee bee) || !bee.inHive();
    }

    /**
     * [CAT] 이번 틱 살아 있는 전역 고양이의 발좌표(x,y,z 3개씩). 크리퍼 회피와 팬텀 급강하
     * 중단이 공유하며, hibernate된 청크도 포함하는 정본 목록을 원래 순서로 한 번만 순회한다.
     * 반환값은 배열 capacity가 아니라 이번 틱 유효한 좌표 원소 수다.
     */
    private int refreshCatPositions() {
        int coordinateCount = 0;
        for (Mob m : runtime.mobs()) {
            if (m.type != MobType.CAT || m.removed || m.isDead()) continue;
            int required = coordinateCount + 3;
            if (required > catPositionScratch.length) {
                int grown = Math.max(required, Math.max(12, catPositionScratch.length * 2));
                catPositionScratch = java.util.Arrays.copyOf(catPositionScratch, grown);
            }
            catPositionScratch[coordinateCount++] = m.x;
            catPositionScratch[coordinateCount++] = m.y;
            catPositionScratch[coordinateCount++] = m.z;
        }
        return coordinateCount;
    }

    int refreshCatPositionsForTest() {
        return refreshCatPositions();
    }

    double[] catPositionScratchForTest() {
        return catPositionScratch;
    }

    private List<PlayerSnapshot> playerSnapshots() {
        List<PlayerSnapshot> out = new ArrayList<>();
        for (PlayerTickState p : rt.players().values()) {
            boolean pumpkin = p.inventory().equippedType(ArmorSlot.HELMET)
                    == com.gameexpert.terrain.Blocks.CARVED_PUMPKIN;
            boolean goldArmor = false;
            for (ArmorSlot slot : ArmorSlot.values()) {
                short item = p.inventory().equippedType(slot);
                if (item >= PlayerInventory.GOLD_ARMOR_MIN
                        && item <= PlayerInventory.GOLD_ARMOR_MAX) {
                    goldArmor = true;
                    break;
                }
            }
            out.add(new PlayerSnapshot(p.nickname(), p.x(), p.y(), p.z(), !p.isDead(),
                    p.yaw(), p.pitch(), p.crouching(), pumpkin, goldArmor,
                    // [ROTTEN-LEATHER] 언데드 무적대 조건도 금 방어구와 같은 자리에서 파생한다.
                    p.inventory().wearingRottenLeatherSet(),
                    p.health(), PlayerSnapshot.maskOf(p.statusEffects()),
                    // [PHANTOM] 불면 스폰 굴림이 읽는 유일한 축이다.
                    p.timeSinceRestMcTicks(),
                    // [NAUTILUS-TEMPT] 유혹 관문이 읽는 유일한 축 — 선택 슬롯의 아이템 타입
                    // 하나다. 인벤토리 전체를 싣지 않는 것이 금 방어구 사실과 같은 규약이고,
                    // 이 값은 WS 로 나가지 않는다(권위 내부 사실).
                    Short.toUnsignedInt(
                            p.inventory().itemType(p.inventory().selectedSlot())),
                    Short.toUnsignedInt(p.inventory().offhand().itemType())));
        }
        return out;
    }

    private void broadcast(Object message) {
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
    }

    private void syncMountedHud() {
        for (Map.Entry<String, Seat> entry : seatOf.entrySet()) {
            Mob mob = runtime.mobById(entry.getValue().mobId());
            if (mob != null && !mob.isDead() && !mob.removed) {
                sendMountedHud(entry.getKey(), mob, entry.getValue().seatIndex(), false);
            }
        }
    }

    private void sendMountedHud(String nickname, Mob mob, int seatIndex, boolean force) {
        double maxHealth = mob.horseMaxHealthStat() > 0.0
                ? mob.horseMaxHealthStat() : mob.maxHp();
        boolean showJumpMeter = MobMountRules.controllingSeat(mob.type, seatIndex)
                && mountedJumpStrength(mob) > 0.0;
        MountedHudFacts next = new MountedHudFacts(
                mob.id, mob.exactHealth(), maxHealth, showJumpMeter);
        if (!force && next.equals(mountedHudByRider.get(nickname))) return;
        WebSocketSession session = rt.session(nickname);
        if (session == null) return;
        if (rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                new WsMessages.MountedHud(next.mobId(), next.health(), next.maxHealth(),
                        next.showJumpMeter(), 0.0))) {
            mountedHudByRider.put(nickname, next);
        }
    }

    private record MountedHudFacts(long mobId, double health, double maxHealth,
            boolean showJumpMeter) {}

    public void notifyWardenVibration(int x, int y, int z, String sourceNickname) {
        for (Mob mob : runtime.mobs()) {
            if (mob instanceof com.gameexpert.engine.mob.Warden warden) {
                warden.hearVibration(x, y, z, sourceNickname);
            }
        }
    }

    public void rejectVillagerTradeVisual(long mobId) {
        Mob mob = runtime.mobById(mobId);
        if (mob instanceof com.gameexpert.engine.mob.Villager villager && !mob.isDead()) {
            villager.rejectTradeVisual();
        }
    }

    private static MobSpawnDto spawnDto(Mob m) {
        return new MobSpawnDto(m.id, typeName(m), m.x, m.y, m.z,
                m.isOnFire(), m.isBaby(), m.slimeSize(), m.variant(), movementMedium(m),
                itemId(m.heldItem()),
                itemId(m.offhandItem()),
                itemId(m.equippedItem(ArmorSlot.HELMET)),
                itemId(m.equippedItem(ArmorSlot.CHESTPLATE)),
                itemId(m.equippedItem(ArmorSlot.LEGGINGS)),
                itemId(m.equippedItem(ArmorSlot.BOOTS)),
                m.carriedBlockId(), m.nativeEquipment(), m.visualFlags(),
                m.nautilusArmorItemId(),
                Short.toUnsignedInt(m.horseArmorItem()),
                m.actionKind(), m.actionPhase(), m.actionTicksRemaining(),
                m.actionSequence(), m.vehicleMobId(), m.customName(),
                com.gameexpert.engine.effect.StatusEffects.protocolNames(m.statusEffects().activeMask()),
                equipmentGlint(m), wireHeadYaw(m), wireHeadPitch(m), m.guardianBeamTarget());
    }

    /** [MOB-GLINT] 비트: 주손 1 · 투구 4 · 흉갑 8 · 각반 16 · 부츠 32(부손은 추적하지 않는다). 정적판 mobEquipmentGlintMask. */
    static int equipmentGlint(Mob m) {
        int mask = m.heldWeaponEnchantments().isEmpty() ? 0 : 1;
        ArmorSlot[] armor = {ArmorSlot.HELMET, ArmorSlot.CHESTPLATE, ArmorSlot.LEGGINGS, ArmorSlot.BOOTS};
        for (int i = 0; i < armor.length; i++) {
            if (!m.equippedWideEnchantments(armor[i]).isEmpty()) mask |= 1 << (2 + i);
        }
        return mask;
    }

    /** [MOB-LOOK] Wire netHeadYaw / headPitch (radians, 1/1000 quantised). */
    private static double wireHeadYaw(Mob m) {
        return com.gameexpert.engine.mob.MobHeadLook.quantize(
                com.gameexpert.engine.mob.MobHeadLook.netHeadYaw(m.yaw, m.headYawAbs));
    }

    private static double wireHeadPitch(Mob m) {
        return com.gameexpert.engine.mob.MobHeadLook.quantize(m.headPitch);
    }

    /** [MOB-LOOK] Head turns smaller than this (radians) do not re-send the mob. */
    private static final double HEAD_EPS = 0.02;

    /**
     * 위치 밖의 최초 상태(이름표 등)가 바뀐 몹을 접속자 전원에게 mobTransform 으로 다시 보낸다.
     * welcome 캐시도 같이 갱신해 이후 입장자가 같은 상태를 받는다.
     */
    public void broadcastMobRefresh(Mob m) {
        if (m == null || m.isDead() || m.removed) return;
        MobSpawnDto refreshed = spawnDto(m);
        welcomeMobState.put(m.id, refreshed);
        lastMobState.put(m.id, MobBroadcastState.capture(m));
        broadcast(new WsMessages.MobTransform(nextEventId(), refreshed));
    }

    private static MobUpdateDto updateDto(Mob m) {
        return new MobUpdateDto(m.id, m.x, m.y, m.z, m.yaw, stateName(m),
                m.isOnFire(), m.isBaby(), movementMedium(m),
                itemId(m.heldItem()),
                itemId(m.offhandItem()),
                itemId(m.equippedItem(ArmorSlot.HELMET)),
                itemId(m.equippedItem(ArmorSlot.CHESTPLATE)),
                itemId(m.equippedItem(ArmorSlot.LEGGINGS)),
                itemId(m.equippedItem(ArmorSlot.BOOTS)),
                m.carriedBlockId(), m.nativeEquipment(), m.visualFlags(),
                m.nautilusArmorItemId(),
                Short.toUnsignedInt(m.horseArmorItem()),
                m.actionKind(), m.actionPhase(), m.actionTicksRemaining(),
                m.actionSequence(), m.vehicleMobId(),
                com.gameexpert.engine.effect.StatusEffects.protocolNames(m.statusEffects().activeMask()),
                equipmentGlint(m), wireHeadYaw(m), wireHeadPitch(m), m.guardianBeamTarget());
    }

    private static int itemId(short item) {
        return Short.toUnsignedInt(item);
    }

    /**
     * Last emitted wire facts without a protocol DTO. The quiet-tick comparison reads directly from the
     * live mob, so DTO allocation is reserved for mobs that will actually be sent. Strings are stable
     * authority values/literals and are captured only at the same boundaries as the former DTO cache.
     */
    private record MobBroadcastState(
            double x, double y, double z, double yaw, String state,
            boolean onFire, boolean baby, String movementMedium,
            int heldItemId, int offhandItemId,
            int helmetItemId, int chestItemId, int legsItemId, int feetItemId,
            int carriedBlockId, String nativeEquipment, int visualFlags,
            int nautilusArmorItemId, int horseArmorItemId,
            String actionKind, String actionPhase, int actionTicksRemaining,
            int actionSequence, long vehicleMobId, long effectMask, int equipmentGlint, double headYaw, double headPitch, double[] guardianBeamTarget) {

        private static MobBroadcastState capture(Mob m) {
            return new MobBroadcastState(m.x, m.y, m.z, m.yaw, stateName(m),
                    m.isOnFire(), m.isBaby(), MobSystem.movementMedium(m),
                    itemId(m.heldItem()), itemId(m.offhandItem()),
                    itemId(m.equippedItem(ArmorSlot.HELMET)),
                    itemId(m.equippedItem(ArmorSlot.CHESTPLATE)),
                    itemId(m.equippedItem(ArmorSlot.LEGGINGS)),
                    itemId(m.equippedItem(ArmorSlot.BOOTS)),
                    m.carriedBlockId(), m.nativeEquipment(), m.visualFlags(),
                    m.nautilusArmorItemId(), Short.toUnsignedInt(m.horseArmorItem()),
                    m.actionKind(), m.actionPhase(), m.actionTicksRemaining(),
                    m.actionSequence(), m.vehicleMobId(), m.statusEffects().activeMask(),
                    MobSystem.equipmentGlint(m), wireHeadYaw(m), wireHeadPitch(m), m.guardianBeamTarget());
        }

        private boolean matches(Mob m) {
            if (Math.abs(x - m.x) > POS_EPS
                    || Math.abs(y - m.y) > POS_EPS
                    || Math.abs(z - m.z) > POS_EPS
                    || Math.abs(yaw - m.yaw) > POS_EPS) {
                return false;
            }
            return state.equals(stateName(m))
                    && onFire == m.isOnFire()
                    && baby == m.isBaby()
                    && movementMedium.equals(MobSystem.movementMedium(m))
                    && heldItemId == itemId(m.heldItem())
                    && offhandItemId == itemId(m.offhandItem())
                    && helmetItemId == itemId(m.equippedItem(ArmorSlot.HELMET))
                    && chestItemId == itemId(m.equippedItem(ArmorSlot.CHESTPLATE))
                    && legsItemId == itemId(m.equippedItem(ArmorSlot.LEGGINGS))
                    && feetItemId == itemId(m.equippedItem(ArmorSlot.BOOTS))
                    && carriedBlockId == m.carriedBlockId()
                    && nativeEquipment.equals(m.nativeEquipment())
                    && visualFlags == m.visualFlags()
                    && nautilusArmorItemId == m.nautilusArmorItemId()
                    && horseArmorItemId == Short.toUnsignedInt(m.horseArmorItem())
                    && actionKind.equals(m.actionKind())
                    && actionPhase.equals(m.actionPhase())
                    && actionTicksRemaining == m.actionTicksRemaining()
                    && actionSequence == m.actionSequence()
                    && vehicleMobId == m.vehicleMobId()
                    && effectMask == m.statusEffects().activeMask()
                    && equipmentGlint == MobSystem.equipmentGlint(m)
                    // [MOB-LOOK] Head turns re-send the mob once they move past HEAD_EPS.
                    && Math.abs(headYaw - wireHeadYaw(m)) <= HEAD_EPS
                    && Math.abs(headPitch - wireHeadPitch(m)) <= HEAD_EPS
                    && java.util.Arrays.equals(guardianBeamTarget, m.guardianBeamTarget());
        }
    }

    private static String typeName(Mob m) {
        return mobTypeName(m.type);
    }

    private static String mobTypeName(MobType type) {
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String movementMedium(Mob mob) {
        return mob.movementMedium();
    }

    /**
     * [SPEAR-KINETIC] 창을 쓰는 플레이어 한 명의 이번 권위 틱 돌진({@code ItemStack.onUseTick →
     * KineticWeapon.damageEntities}). 명중이 있으면 {@code broadcastEntityEvent(user, 2)} 자리의 kineticHit 을
     * 공격자와 소리 반경 안 플레이어에게 보낸다. 인벤 갱신(내구)은 호출부(WorldTickLoop)가 보낸다.
     */
    CombatSystem.KineticTick tickSpearKinetic(PlayerTickState player, long tickNo) {
        CombatSystem.KineticTick result = combat.tickKineticUse(player, world, tickNo);
        if (result.stabs() > 0) {
            WsMessages.KineticHit message = new WsMessages.KineticHit(nextEventId(), player.nickname(),
                    result.itemType(), player.x(), player.y(), player.z());
            for (PlayerTickState listener : rt.players().values()) {
                if (listener != player && !SoundRules.audible(player.x(), player.y(), player.z(),
                        listener.x(), listener.y(), listener.z(), SoundRules.RANGE)) continue;
                WebSocketSession session = rt.session(listener.nickname());
                if (session != null) {
                    rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
                }
            }
        }
        return result;
    }

    /** 위치 기반 월드 사운드도 바닐라 전송 반경 밖에는 패킷을 보내지 않는다({@link SoundRules}). */
    private void worldSound(String kind, double x, double y, double z, short blockType) {
        WsMessages.WorldSound message =
                new WsMessages.WorldSound(nextEventId(), kind, x, y, z, blockType);
        double range = SoundRules.worldSoundRange(kind);
        for (PlayerTickState player : rt.players().values()) {
            if (!SoundRules.audible(x, y, z, player.x(), player.y(), player.z(), range)) continue;
            WebSocketSession session = rt.session(player.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
            }
        }
    }

    /**
     * [TRIAL-GAP] 바닐라 {@code ServerLevel.levelEvent(null, event, pos, data)}. 블록 좌표 그대로
     * 64블록 미만의 플레이어에게만 보낸다({@link SoundRules#LEVEL_EVENT_RANGE}).
     */
    /** [CONTAINER-MENUS] Test/diagnostic view: the last level events this world emitted. */
    private final java.util.ArrayDeque<WsMessages.LevelEvent> recentLevelEvents =
            new java.util.ArrayDeque<>();

    List<WsMessages.LevelEvent> drainRecentLevelEvents() {
        List<WsMessages.LevelEvent> drained = new ArrayList<>(recentLevelEvents);
        recentLevelEvents.clear();
        return drained;
    }

    void levelEvent(int event, int x, int y, int z, int data) {
        WsMessages.LevelEvent message = new WsMessages.LevelEvent(event, x, y, z, data);
        if (recentLevelEvents.size() >= 64) recentLevelEvents.removeFirst();
        recentLevelEvents.addLast(message);
        for (PlayerTickState player : rt.players().values()) {
            if (!SoundRules.audible(x, y, z, player.x(), player.y(), player.z(),
                    SoundRules.LEVEL_EVENT_RANGE)) continue;
            WebSocketSession session = rt.session(player.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
            }
        }
    }

    /** [TRIAL-GAP] 트라이얼 스포너·금고의 서버 playSound 를 worldSound kind 로 낸다. */
    void trialWorldSound(String kind, double x, double y, double z, short blockType) {
        worldSound(kind, x, y, z, blockType);
    }

    /**
     * [TRIAL-GAP] 서버가 pitch 를 정하는 kind({@code WorldSound.PITCHED_KINDS}, 금고 배출의
     * {@code 0.8 + 0.4 × ejectionProgress}). 전송 반경은 다른 worldSound 와 같다.
     */
    void trialWorldSound(String kind, double x, double y, double z, short blockType, float pitch) {
        WsMessages.WorldSound message =
                new WsMessages.WorldSound(nextEventId(), kind, x, y, z, blockType, pitch);
        double range = SoundRules.worldSoundRange(kind);
        for (PlayerTickState player : rt.players().values()) {
            if (!SoundRules.audible(x, y, z, player.x(), player.y(), player.z(), range)) continue;
            WebSocketSession session = rt.session(player.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
            }
        }
    }

    /** [EC-MOBS] 스폰 방송 뒤로 미룬 몹 소리(새로 건 액자의 설치음). */
    private record DeferredMobSound(Mob mob, String kind) {
    }

    private final List<DeferredMobSound> deferredSpawnSounds = new ArrayList<>();

    /** 몹 생활음·행동음은 전부 volume ≤ 1 이라 바닐라 전송 반경이 16블록이다({@link SoundRules}). */
    private void mobSound(Mob mob, String kind) {
        // eventId 는 수신자 수와 무관하게 한 번만 소비해 월드 사건 수열을 유지한다.
        WsMessages.MobSound message =
                new WsMessages.MobSound(nextEventId(), mob.id, kind, mob.x, mob.y, mob.z);
        for (PlayerTickState player : rt.players().values()) {
            if (!SoundRules.audible(mob.x, mob.y, mob.z,
                    player.x(), player.y(), player.z(), SoundRules.RANGE)) continue;
            WebSocketSession session = rt.session(player.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
            }
        }
    }

    private void broadcastTeleport(long mobId, double fromX, double fromY, double fromZ,
                                   double toX, double toY, double toZ) {
        broadcast(new WsMessages.MobTeleport(nextEventId(), mobId,
                new WsMessages.PositionDto(fromX, fromY, fromZ),
                new WsMessages.PositionDto(toX, toY, toZ)));
    }

    /**
     * [ARROW-GROUND] 거절된 명중의 화살 되튐(바닐라 {@code AbstractArrow.onHitEntity} 의 실패 가지).
     * 코어 패스가 이미 종결시킨 화살은 같은 id 로 되살려 계속 날리고, 관통 중이라 살아 있던 화살은 속도만
     * 뒤집는다. 되튄 속도가 거의 0 이면 그 자리에서 끝나며, 줍기가 ALLOWED 면 줍는 스택을 떨군다
     * ({@code spawnAtLocation(level, getPickupItem(), 0.1F)}).
     */
    private void continueEndermanArrow(long projectileId, long mobId) {
        ProjectileSim arrow = terminatedThisTick.remove(projectileId);
        boolean wasTerminated = arrow != null;
        if (arrow == null) arrow = runtime.projectile(projectileId);
        if (arrow == null || arrow.kind != ProjectileSim.Kind.ARROW) return;
        arrow.continueAfterEndermanHit(mobId);
        terminatedArrows.remove(arrow.id);
        if (wasTerminated) runtime.reviveProjectile(arrow);
        else runtime.markProjectileMutation(arrow);
    }

    private void deflectRefusedArrow(long projectileId) {
        ProjectileSim arrow = terminatedThisTick.remove(projectileId);
        boolean wasTerminated = arrow != null;
        if (arrow == null) arrow = runtime.projectile(projectileId);
        if (arrow == null || !arrow.sticksInGround()) return;
        boolean dropPickup = arrow.deflectRefusedHit();
        if (arrow.alive) {
            if (wasTerminated) {
                terminatedArrows.remove(arrow.id);
                runtime.reviveProjectile(arrow);
            } else {
                runtime.markProjectileMutation(arrow);
            }
            return;
        }
        terminatedArrows.put(arrow.id, new ProjectileRemoval(arrow.id, arrow.terminalReason,
                arrow.x, arrow.y, arrow.z, null));
        if (!wasTerminated) runtime.removeProjectile(arrow.id);
        if (dropPickup) {
            rt.itemSystem().spawnMobDrop(arrow.pickupItemType(), 1, arrow.x, arrow.y + 0.1, arrow.z);
        }
    }

    /**
     * [ARROW-GROUND] 바닐라 {@code Player.aiStep → touch → AbstractArrow.playerTouch}: 흔들림이 끝난 박힌
     * 화살이 플레이어 접촉 상자({@code inflate(1.0, 0.5, 1.0)})에 들어오면 줍기 규칙대로 거둔다. ALLOWED 는
     * 인벤토리가 스택 하나를 받아야만 줍고(가득 차면 그대로 남는다), CREATIVE_ONLY 는 무한 재료를 가진
     * 플레이어만인데 이 저장소에는 창작 모드가 없어 줍지 못하고 수명으로만 사라진다. DISALLOWED 는 거절.
     *
     * <p>인벤토리 증가와 원장 제거는 발사와 같은 동기 트랜잭션({@code persistPlayerProjectileLaunch})으로
     * 함께 커밋한 뒤에만 관찰 가능해진다. 정산 임대 중인 인벤토리는 이번 틱을 건너뛴다.
     */
    private void pickUpGroundArrows() {
        List<ProjectileSim> active = runtime.arrowsInChunks(rt.activeSimulationChunksForMobTick());
        Map<PlayerTickState, List<ProjectileSim>> picked = null;
        Map<PlayerTickState, Long> sourceRevisions = null;
        for (ProjectileSim arrow : active) {
            if (!arrow.pickupReady() || arrow.pickup != ProjectileSim.Pickup.ALLOWED) continue;
            PlayerTickState collector = null;
            double best = Double.POSITIVE_INFINITY;
            for (PlayerTickState player : rt.players().values()) {
                if (player.isDead() || player.inventory().settlementLeased()) continue;
                if (!arrow.touchesPlayerPickupBox(player.x(), player.y(), player.z())) continue;
                double dx = player.x() - arrow.x;
                double dy = player.y() - arrow.y;
                double dz = player.z() - arrow.z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= best) {
                    best = distance;
                    collector = player;
                }
            }
            if (collector == null) continue;
            if (sourceRevisions == null) sourceRevisions = new LinkedHashMap<>();
            sourceRevisions.putIfAbsent(collector, collector.inventory().revision());
            if (collector.inventory().addItem(arrow.pickupItemType(), 1) != 1) continue;
            runtime.removeProjectile(arrow.id);
            if (picked == null) picked = new LinkedHashMap<>();
            picked.computeIfAbsent(collector, ignored -> new ArrayList<>()).add(arrow);
        }
        if (picked == null) return;
        for (Map.Entry<PlayerTickState, List<ProjectileSim>> entry : picked.entrySet()) {
            PlayerTickState player = entry.getKey();
            rt.persistPlayerProjectileLaunch(player, sourceRevisions.get(player));
            sendInventory(player);
            for (ProjectileSim arrow : entry.getValue()) {
                terminatedArrows.put(arrow.id, new ProjectileRemoval(arrow.id, "pickup",
                        arrow.x, arrow.y, arrow.z, null, player.nickname()));
                broadcast(new WsMessages.ItemPickup(arrow.x, arrow.y, arrow.z));
            }
        }
    }

    private void markArrowEvaded(long arrowId, long mobId,
                                 double fromX, double fromY, double fromZ,
                                 double toX, double toY, double toZ) {
        ProjectileRemoval impact = terminatedArrows.get(arrowId);
        terminatedArrows.put(arrowId, evadedTerminal(arrowId, mobId, impact, fromX, fromY, fromZ));
        broadcastTeleport(mobId, fromX, fromY, fromZ, toX, toY, toZ);
    }

    static ProjectileRemoval evadedTerminal(long arrowId, long mobId, ProjectileRemoval impact,
                                             double fromX, double fromY, double fromZ) {
        double impactX = impact == null ? fromX : impact.getX();
        double impactY = impact == null ? fromY : impact.getY();
        double impactZ = impact == null ? fromZ : impact.getZ();
        return new ProjectileRemoval(arrowId, "evaded", impactX, impactY, impactZ, mobId);
    }

    static ProjectileRemoval terminalFor(long id, ProjectilePos lastPosition) {
        if (lastPosition == null) {
            throw new IllegalStateException("Missing terminal position for projectile " + id);
        }
        return new ProjectileRemoval(id, "expired", lastPosition.getX(), lastPosition.getY(),
                lastPosition.getZ(), null);
    }

    /**
     * 서버 상태를 클라 모델 기대 문자열로 매핑. 동물(passive)의 FLEE 는 "flee"(클라 AnimalBase 처리),
     * 적대 몹(스켈레톤)의 FLEE 는 "chase"(거리 유지 후퇴 연출), DEAD 는 "idle" 폴백.
     */
    private static String stateName(Mob m) {
        // 탑승 중 공격 애니메이션은 유지하되 chase/wander 보행 애니메이션은 멈춰 좌석에서 걷지 않는다.
        if (m.isRidingBoat() && m.state != MobState.ATTACK) return "idle";
        return switch (m.state) {
            case WANDER -> "wander";
            case CHASE -> "chase";
            case FLEE -> m.type.hostile() ? "chase" : "flee";
            case ATTACK -> "attack";
            case FUSE -> "fuse";
            case IDLE, DEAD -> "idle";
        };
    }

    // ── [DRAGON] 드래곤전 포트(DragonFightSystem 이 부른다) ─────────────────────────

    MobWorldView dragonWorldView() {
        return world;
    }

    /** 이 청크의 첫 채움 판정이 (영속으로) 끝났는가. */
    boolean chunkPopulated(int chunkX, int chunkZ) {
        return populatedChunkKeys.contains(populationKey(chunkX, chunkZ));
    }

    Mob mobById(long mobId) {
        return runtime.mobById(mobId);
    }

    void refreshMobSpatialIndex(Mob mob) {
        runtime.refreshSpatialIndex(mob);
    }

    void broadcastMobHurt(long mobId, boolean dead) {
        broadcast(new WsMessages.MobHurt(mobId, dead));
    }

    /** 원장의 살아 있는 이 종류 몹 전부(비활성 청크 포함, id 순). */
    List<Mob> mobsOfType(MobType type) {
        List<Mob> out = new ArrayList<>();
        for (Mob mob : runtime.mobs()) {
            if (mob.type == type && !mob.isDead() && !mob.removed) out.add(mob);
        }
        out.sort(java.util.Comparator.comparingLong(mob -> mob.id));
        return out;
    }

    /** 드래곤 몸통·날개가 치는 살아 있는 몹(LivingEntity): 드래곤·엔드 수정·아이템 액자는 빠진다. */
    List<Mob> livingMobsIn(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        List<Mob> out = new ArrayList<>();
        for (Mob mob : runtime.mobsInChunks(rt.activeSimulationChunksForMobTick())) {
            if (mob.isDead() || mob.removed || mob.type == MobType.ENDER_DRAGON || mob.type == MobType.END_CRYSTAL
                    || mob.type == MobType.ITEM_FRAME) continue;
            double half = mob.width() * 0.5;
            if (mob.x + half > minX && mob.x - half < maxX && mob.y + mob.height() > minY && mob.y < maxY
                    && mob.z + half > minZ && mob.z - half < maxZ) {
                out.add(mob);
            }
        }
        out.sort(java.util.Comparator.comparingLong(mob -> mob.id));
        return out;
    }

    /** 이 상자와 겹치는 몹이 있는가({@code EndCrystalItem.useOn} 의 {@code getEntities(null, box)}). */
    boolean anyEntityIn(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (Mob mob : runtime.mobs()) {
            if (mob.isDead() || mob.removed) continue;
            double[] box = mob.authorityAabb();
            if (box[0] < maxX && box[3] > minX && box[1] < maxY && box[4] > minY && box[2] < maxZ && box[5] > minZ) {
                return true;
            }
        }
        return false;
    }

    Mob spawnDragonFightMob(MobType type, double x, double y, double z, String variant, double yaw) {
        Mob mob = runtime.addMobWithVariant(type, x, y, z, true, variant);
        mob.yaw = yaw;
        runtime.refreshPersistenceSnapshot(mob);
        return mob;
    }

    /** 드래곤전이 없앤 개체는 사망 퇴장한다(드랍·XP 표가 비어 있다). */
    void markDragonFightDeath(Mob mob) {
        pendingDespawnReasons.put(mob.id, "death");
    }

    void pushMobByDragon(long mobId, double dx, double dy, double dz) {
        Mob mob = findMob(mobId);
        if (mob == null || mob.isDead() || mob.removed || mob.immovable()) return;
        // Entity.push 는 MC 틱 속도에 더한다. 몹 넉백 채널은 권위 틱 변위(×2)다.
        mob.applyExplosionKnockback(dx * 2.0, dy * 2.0, dz * 2.0);
    }

    void hurtByFallingSpeleothem(double x, double y, double z, int damage) {
        for (var player : rt.players().values()) {
            if (player.x() + .3 > x - .49 && player.x() - .3 < x + .49
                    && player.z() + .3 > z - .49 && player.z() - .3 < z + .49
                    && player.y() + 1.8 > y && player.y() < y + .98) {
                damagePlayerFrom(player.nickname(), damage, "falling_stalactite", null, x, z, false);
            }
        }
        for (var mob : runtime.mobs()) {
            if (mob.x + mob.width() / 2 > x - .49 && mob.x - mob.width() / 2 < x + .49
                    && mob.z + mob.width() / 2 > z - .49 && mob.z - mob.width() / 2 < z + .49
                    && mob.y + mob.height() > y && mob.y < y + .98) {
                hurtMobByDragon(mob.id, damage);
            }
        }
    }

    void hurtPlayerByDragon(String nickname, int amount, double sourceX, double sourceZ) {
        if (amount <= 0) return;
        damagePlayerFrom(nickname, amount, "mob", "ender_dragon", sourceX, sourceZ, false);
    }

    void hurtMobByDragon(long mobId, double amount) {
        Mob mob = findMob(mobId);
        if (mob == null || mob.isDead() || mob.removed) return;
        if (!mob.damage(amount, runtime.worldTick())) return;
        broadcast(new WsMessages.MobHurt(mob.id, mob.isDead()));
        if (mob.isDead()) {
            pendingDespawnReasons.put(mob.id, "death");
            runtime.splitSlimeOnDeath(mob, rng);
            spawnMobDrops(mob, false);
            mobDropsHandled.add(mob.id);
        }
    }

    long addDragonProjectile(ProjectileSim projectile) {
        runtime.addWorldProjectile(projectile);
        return projectile.id;
    }

    /** 구름·투사체를 다음 몹 lane 에서 사라지게 한다(사유 expired). */
    void expireProjectile(long projectileId) {
        for (ProjectileSim projectile : runtime.arrows()) {
            if (projectile.id == projectileId && projectile.alive) {
                projectile.alive = false;
                projectile.terminalReason = "expired";
            }
        }
    }

    /** 폭발을 지금 내거나(상주) 다음 틱으로 미룬다. */
    void explodeOrQueue(double x, double y, double z, double power, boolean destroyBlocks) {
        if (!explode(x, y, z, power, destroyBlocks)) {
            pendingExplosions.addLast(new PendingExplosion(x, y, z, power, destroyBlocks));
            pendingExplosionRetryRequested = true;
        }
    }

    /**
     * [DRAGON] {@code DragonFireball.onHit}: 화염구 상자를 (4, 2, 4) 부풀린 범위의 첫 살아 있는 개체가 거리² 16 안이면
     * 그 발밑으로 구름을 옮기고, 드래곤 숨결 구름(반지름 3 → 7, 600 틱, 즉시 피해 II)과 level event 2006(data 1).
     */
    private void dragonFireballHit(MobEvent.DragonFireballHit hit) {
        double cx = hit.x();
        double cy = hit.y();
        double cz = hit.z();
        double minX = hit.x() - 0.5 - 4.0;
        double maxX = hit.x() + 0.5 + 4.0;
        double minY = hit.y() - 2.0;
        double maxY = hit.y() + 1.0 + 2.0;
        double minZ = hit.z() - 0.5 - 4.0;
        double maxZ = hit.z() + 0.5 + 4.0;
        boolean moved = false;
        List<String> names = new ArrayList<>(rt.players().keySet());
        names.sort(String::compareTo);
        for (String name : names) {
            PlayerTickState player = rt.players().get(name);
            if (player == null || player.isDead()) continue;
            if (player.x() + 0.3 <= minX || player.x() - 0.3 >= maxX || player.y() + 1.8 <= minY
                    || player.y() >= maxY || player.z() + 0.3 <= minZ || player.z() - 0.3 >= maxZ) continue;
            double dx = player.x() - hit.x();
            double dy = player.y() - hit.y();
            double dz = player.z() - hit.z();
            if (dx * dx + dy * dy + dz * dz < 16.0) {
                cx = player.x();
                cy = player.y();
                cz = player.z();
                moved = true;
                break;
            }
        }
        if (!moved) {
            for (Mob mob : livingMobsIn(minX, minY, minZ, maxX, maxY, maxZ)) {
                double dx = mob.x - hit.x();
                double dy = mob.y - hit.y();
                double dz = mob.z - hit.z();
                if (dx * dx + dy * dy + dz * dz < 16.0) {
                    cx = mob.x;
                    cy = mob.y;
                    cz = mob.z;
                    break;
                }
            }
        }
        levelEvent(com.gameexpert.engine.dragon.DragonEvents.PARTICLES_DRAGON_FIREBALL_SPLASH,
                (int) Math.floor(hit.x()), (int) Math.floor(hit.y()), (int) Math.floor(hit.z()), 1);
        runtime.addWorldProjectile(ProjectileSim.dragonBreath(0L, cx, cy, cz, false));
    }

    /**
     * [DRAGON] {@code BottleItem.use}: 플레이어 상자를 2 부풀린 범위의 살아 있는 드래곤 숨결 구름 하나의 반지름을
     * 0.5 줄이고 그 구름을 뜬다(빈 병 → 드래곤의 숨결). 뜬 구름이 없으면 거짓.
     */
    boolean bottleDragonBreath(PlayerTickState player) {
        double minX = player.x() - 0.3 - 2.0;
        double maxX = player.x() + 0.3 + 2.0;
        double minY = player.y() - 2.0;
        double maxY = player.y() + 1.8 + 2.0;
        double minZ = player.z() - 0.3 - 2.0;
        double maxZ = player.z() + 0.3 + 2.0;
        for (ProjectileSim cloud : runtime.arrows()) {
            if (!cloud.alive || cloud.kind != ProjectileSim.Kind.DRAGON_BREATH) continue;
            double r = cloud.cloudRadius;
            if (cloud.x + r <= minX || cloud.x - r >= maxX || cloud.y + ProjectileSim.CLOUD_HEIGHT <= minY
                    || cloud.y >= maxY || cloud.z + r <= minZ || cloud.z - r >= maxZ) continue;
            cloud.cloudRadius = cloud.cloudRadius - 0.5;
            return true;
        }
        return false;
    }
}
