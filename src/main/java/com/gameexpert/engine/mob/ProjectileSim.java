package com.gameexpert.engine.mob;

import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot;
import com.gameexpert.terrain.Blocks;

/**
 * 서버 권위 투사체 시뮬레이션(순수 로직). 위치·속도(블록/틱),
 * 이전→새 위치 선분을 DDA 복셀 충돌(막히면 소멸)과 엔티티 AABB 명중 판정.
 */
public final class ProjectileSim {
    public static final double GRAVITY = 0.05;    // 화살 블록/틱²
    /**
     * [CONTAINER-MENUS] 이 저장소의 화살 척도: 바닐라 MC 틱당 화살 속력에 곱하는 값. 만충 활(바닐라
     * 3.0)이 {@code Skeleton.ARROW_SPEED} 1.5 이므로 0.5 다. 발사기·불길한 아이템 소환기의 화살도
     * 같은 척도를 쓴다.
     */
    public static final double ARROW_SIMULATION_SCALE = 0.5;
    public static final double SNOWBALL_GRAVITY = 0.03;
    public static final double SNOWBALL_AIR_INERTIA = 0.99;
    public static final double SNOWBALL_WATER_INERTIA = 0.8;
    public static final double SNOWBALL_POWER = 1.5;
    /**
     * [TRIDENT] 화살 계열(화살·석궁 볼트·삼지창)이 <b>물에 잠긴 동안</b> 매 틱 곱하는 관성.
     * 바닐라 {@code AbstractArrow.waterInertia} 0.6 이며 {@code ThrownTrident} 도 같은 부모를
     * 쓴다 — 그래서 한 종만 붙이지 않고 {@link #arrowFamilyPhysics} 전체에 함께 건다
     * (등급 [B], 코드 상수라 1.21.4 데이터 덤프에는 없다).
     *
     * <p><b>공기 관성은 여전히 없다</b>(바닐라 0.99 미도입 divergence). 이번 값은 물 안에서만
     * 곱해지므로 기존 지상 탄도 골든은 한 값도 움직이지 않는다.
     *
     * <p>10 TPS divergence: 눈덩이·달걀 0.8 과 투척 물약 0.8 의 선례 그대로 <b>권위 1틱에
     * 한 번</b> 곱한다. 바닐라 20 TPS 로 환산하면 0.6² = 0.36 이 실시간 등가지만, 그러면 같은
     * 투사체 정본 안에서 화살 계열만 다른 환산 규약을 쓰게 되어 선례와 어긋난다.
     */
    public static final double ARROW_WATER_INERTIA = 0.6;
    public static final double SNOWBALL_INACCURACY = 1.0;
    /** 투척 물약 공기·수중 저항(바닐라 ThrownPotion). 중력은 화살과 같은 GRAVITY 를 쓴다. */
    public static final double POTION_AIR_INERTIA = 0.99;
    public static final double POTION_WATER_INERTIA = 0.8;
    /** [CONTAINER-MENUS] {@code ThrownExperienceBottle.getDefaultGravity}. */
    public static final double EXPERIENCE_BOTTLE_GRAVITY = 0.07;
    /** [CONTAINER-MENUS] {@code ExperienceBottleItem.use}: {@code shootFromRotation(-20, 0.7, 1)}. */
    public static final double PLAYER_EXPERIENCE_BOTTLE_POWER = 0.7;
    /** 마녀 투척 물약 초기 속력(블록/틱). */
    public static final double POTION_POWER = 0.75;
    /** [POTION] 플레이어 투척 물약 초기 속력. 바닐라 {@code ThrownPotion} 발사 속도 0.5 다. */
    public static final double PLAYER_POTION_POWER = 0.5;
    /**
     * [POTION] 플레이어 투척 물약의 상향 발사각(도). 바닐라
     * {@code shootFromRotation(player, xRot, yRot, -20.0F, 0.5F, 1.0F)} 의 -20 이며,
     * MC 는 pitch 가 아래쪽이 양수라 WebCraft 의 위쪽 양수 규약에서는 +20 이다.
     */
    public static final double PLAYER_POTION_PITCH_OFFSET_DEGREES = 20.0;
    public static final double INACCURACY_NOISE_SCALE = 0.0172275;
    public static final int MAX_AGE = 60;
    /** 바닐라 FireworkRocketEntity 상승 가속: 수평 ×1.15, 매 틱 +0.04 블록/틱 수직. */
    public static final double FIREWORK_HORIZONTAL_ACCELERATION = 1.15;
    public static final double FIREWORK_UPWARD_ACCELERATION = 0.04;
    public static final int DEFAULT_DAMAGE = 4;   // 화살 피해(스켈레톤)

    private static final double PLAYER_HALF = 0.3; // 폭 0.6
    /** 플레이어 히트박스 높이. 찌가 걸린 플레이어 위 부착점(0.8×키)에도 쓴다. */
    public static final double PLAYER_HEIGHT = 1.8;

    public boolean gatewayPending;
    public double previousX, previousY, previousZ;
    public int gatewayX, gatewayY, gatewayZ;

    private boolean enterGateway(MobWorldView world, double ax, double ay, double az,
            double bx, double by, double bz, double limit) {
        if (kind != Kind.ENDER_PEARL || !world.endDimension()) return false;
        double best = limit;
        boolean found = false;
        for (int gx=(int)Math.floor(Math.min(ax,bx)); gx<=Math.floor(Math.max(ax,bx)); gx++)
            for (int gy=(int)Math.floor(Math.min(ay,by)); gy<=Math.floor(Math.max(ay,by)); gy++)
                for (int gz=(int)Math.floor(Math.min(az,bz)); gz<=Math.floor(Math.max(az,bz)); gz++) {
                    if (!world.endGatewayAvailable(gx,gy,gz)) continue;
                    double lo=0, hi=1;
                    double[] a={ax,ay,az}, d={bx-ax,by-ay,bz-az}; int[] c={gx,gy,gz};
                    for (int axis=0;axis<3;axis++) {
                        if (Math.abs(d[axis])<1e-12) { if(a[axis]<c[axis] || a[axis]>=c[axis]+1) hi=-1; }
                        else { double p=(c[axis]-a[axis])/d[axis], q=(c[axis]+1-a[axis])/d[axis];
                            lo=Math.max(lo,Math.min(p,q)); hi=Math.min(hi,Math.max(p,q)); }
                    }
                    if(lo<=hi && lo<=best) {best=lo;found=true;gatewayX=gx;gatewayY=gy;gatewayZ=gz;}
                }
        if (!found) return false;
        x=Math.max(gatewayX+1e-7,Math.min(gatewayX+1-1e-7,ax+(bx-ax)*best));
        y=Math.max(gatewayY+1e-7,Math.min(gatewayY+1-1e-7,ay+(by-ay)*best));
        z=Math.max(gatewayZ+1e-7,Math.min(gatewayZ+1-1e-7,az+(bz-az)*best));
        gatewayPending=true;
        return true;
    }

    public long id;
    public double x, y, z;
    public double vx, vy, vz;
    public int age;
    public boolean alive = true;
    /** alive가 false가 된 정확한 서버 종결 사실. */
    public String terminalReason;
    public Long targetMobId;
    /**
     * [BLOCK-SHAPES] 마지막 블록 판정이 맞힌 충돌 상자의 주인 칸과 진입면(바닐라 BlockHitResult 의
     * getBlockPos · getDirection, Direction 3D data 순서 0 아래 · 1 위 · 2 북 · 3 남 · 4 서 · 5 동; 면을
     * 정할 수 없으면 -1). {@code terminalReason == "block_hit"} 일 때만 뜻이 있고, 종 울림
     * (BellBlock#onProjectileHit)이 쓴다.
     */
    public int blockHitX, blockHitY, blockHitZ, blockHitFace = -1;
    /**
     * [ARROW-GROUND] 이번 권위 틱에 블록에 박혔는가와 그 명중점 높이(밀어내기 전). 박힌 화살은 종결하지 않지만
     * 바닐라 {@code AbstractArrow.onHitBlock} 도 {@code BlockState.onProjectileHit}(종 울림)를 부르므로
     * {@code MobSystem} 이 {@link #blockHitX}..{@link #blockHitFace} 와 함께 읽는다. 영속하지 않는다.
     */
    private boolean stuckThisTick;
    private double stuckHitY;

    public boolean stuckThisTick() { return stuckThisTick; }
    public double stuckHitY() { return stuckHitY; }
    /**
     * 찌가 수면에 안착했는가. 안착한 뒤로는 물리도 수명도 멈추고, 낚시 시스템이
     * 위치(입질 시 잠김)와 제거 시점을 소유한다.
     */
    public boolean landed;
    /**
     * 폭죽 로켓의 서버 틱 수명. 바닐라 20 TPS 수명을 서버 10 TPS 로 환산한 값이며,
     * 다른 종류는 0(=MAX_AGE 사용)이다.
     */
    public int lifetimeTicks;
    public final int damage;
    public final Kind kind;
    /** 명중·착탄 시 부여할 상태이상. 일반 화살/눈덩이는 null. */
    public final ProjectileEffect effect;
    private final String shooterNickname;
    private final long shooterMobId;
    private final MobType shooterMobType;
    /**
     * 후킹된 플레이어 닉네임. 찌 전용이며 그 밖의 투사체는 언제나 null 이다.
     * 후킹된 찌는 낚시 시스템이 위치를 소유하므로 {@link #landed} 와 같이 물리를 멈춘다.
     */
    public String hookedPlayer;
    /** 후킹된 몹 id(0 = 없음). 찌 전용이다. */
    public long hookedMobId;
    /** Separate placed-entity namespace; zero means no fishing target. */
    public long hookedPlacedEntityId;
    /**
     * [ZOMBIE-ANIMAL] 이 달걀이 <b>상한 달걀</b>인가. 던지는 경로·물리·프로토콜 종류는 바닐라
     * 달걀과 글자 그대로 같고({@link Kind#EGG}) 갈리는 것은 <b>부화 굴림 하나뿐</b>이다 —
     * 상한 달걀은 부화 확률이 0 이라 굴림 자체를 하지 않는다({@code MobSystem} 의 종결 처리와
     * {@code FarmAnimalRules.spoiledEggHatches} 참조). 달걀이 아닌 종류에서는 항상 false 다.
     */
    public boolean spoiledEgg;
    /** Exact chicken variant carried by an ordinary thrown egg; null for spoiled/non-egg shots. */
    public String eggVariant;
    /**
     * [TRIDENT] 이 투사체가 종결할 때 <b>되돌려줄 삼지창의 남은 내구</b>. 0 이면 회수물이 없다.
     *
     * <p>바닐라에서 <b>드라운드가 던진 삼지창은 주울 수 없고</b>("just like arrows shot by
     * skeletons") 낙하 회수는 플레이어가 던진 것에만 해당한다. 그래서 이 값은
     * {@link #fromPlayerTrident} 만 세우며 몹 투척({@link #fromShot})은 언제나 0 이다 —
     * 종결 처리는 "플레이어가 던졌는가"를 다시 묻지 않고 이 값 하나만 본다.
     */
    public int recoverableDurability;
    /** Remaining Minecraft ticks during which a player wind charge cannot be deflected. */
    public int noDeflectMcTicks;
    /**
     * [TRIAL-GAP] 이 개체가 싣는 아이템. 잔류형 물약 · 효과 구름(색을 정하는 물약) · 불길한
     * 아이템 소환기(내보낼 아이템) · 효과 화살에서만 0 이 아니다.
     */
    public short payloadItemType;
    /** [TRIAL-GAP] 불길한 아이템 소환기가 내보낼 개수. */
    public int payloadCount;
    /** [TRIAL-GAP] 효과 구름 반지름(바닐라 {@code DATA_RADIUS}). */
    public double cloudRadius;
    /** [DRAGON] 구름 대기({@code waitTime}, MC 틱). 잔류형 물약 10, 드래곤 숨결 20(바닐라 기본값). */
    public int cloudWaitTime = CLOUD_WAIT_TIME_MC_TICKS;
    /** [DRAGON] 구름 {@code radiusPerTick}. 잔류형 물약 −3/600, 화염구 숨결 (7−3)/600, 앉은 불길 0. */
    public double cloudRadiusPerTick = CLOUD_RADIUS_PER_TICK;
    /** [DRAGON] 구름 {@code radiusOnUse}. 잔류형 물약 −0.5, 드래곤 숨결 0. */
    public double cloudRadiusOnUse = CLOUD_RADIUS_ON_USE;
    /** [TRIAL-GAP] 불길한 아이템 소환기 {@code spawn_item_after_ticks}(MC 틱). */
    public int spawnItemAfterMcTicks;
    /**
     * [TRIAL-GAP] 효과 구름 {@code victims}: 대상 키("p:닉네임" · "m:몹 id") → 다시 적용할 수
     * 있는 tickCount. 바닐라 {@code addAdditionalSaveData} 도 저장하지 않는다.
     */
    public final java.util.Map<String, Integer> cloudVictims = new java.util.LinkedHashMap<>();
    /**
     * [ENCHANT-WIDE] 쏜/던진 무기의 인챈트. 밀어내기·화염(활), 관통(석궁), 찌르기(삼지창)가 명중 시
     * 여기서 읽힌다. 몹 발사체는 빈 집합이다.
     */
    public com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments =
            com.gameexpert.engine.enchant.WideEnchantments.EMPTY;
    /**
     * [ENCHANT-WIDE] 관통 화살이 이미 뚫은 몹. 바닐라 {@code AbstractArrow.piercingIgnoreEntityIds}
     * 처럼 영속하지 않는 비행 중 상태다.
     */
    public java.util.Set<Long> piercedMobIds;
    /**
     * [ENCHANT-WIDE] 회수될 삼지창의 워드 0 인챈트와 성분 문자열. 예전 회수물은 내구만 되돌려
     * 인챈트·이름이 사라졌다. 삼지창이 아니면 0/null 이다.
     */
    public long recoverEnchantments;
    public String recoverComponentData;
    /**
     * [UTILITY] 투척·잔류형 물약과 그 효과 구름이 싣는 물약 키(바닐라 {@code potion_contents}).
     * 있으면 착탄·구름이 {@link com.gameexpert.engine.effect.PotionCatalog} 의 효과 전부(거북 도사의
     * 두 효과, 즉시 치유 등)를 건다. 없으면(마녀 물약 · 옛 행) {@link #effect} 하나만 건다.
     */
    public String potionKey;

    // ── [ARROW-GROUND] 바닐라 AbstractArrow 의 박힘 · 줍기 상태 ──────────────────────────
    /** {@code AbstractArrow.tickDespawn}: 박힌 뒤 {@code life >= 1200}(MC 틱)이면 사라진다. */
    public static final int ARROW_GROUND_LIFE_MC_TICKS = 1200;
    /** {@code AbstractArrow.SHAKE_TIME}: 박힌 순간의 흔들림(MC 틱). 흔들리는 동안은 줍지 못한다. */
    public static final int ARROW_SHAKE_MC_TICKS = 7;
    /** {@code onHitBlock}: 박힌 위치를 속도 부호 방향으로 {@code 0.05F} 만큼 되돌린다(float 상수). */
    public static final double ARROW_GROUND_NUDGE = (double) 0.05f;
    /** {@code shouldFall}: 위치를 {@code inflate(0.06)} 한 상자에 충돌이 없으면 떨어진다. */
    public static final double ARROW_FALL_PROBE = 0.06;
    /**
     * 거절된 명중의 되튐: {@code ProjectileDeflection.REVERSE}({@code scale(-0.5)}) 뒤
     * {@code onHitEntity} 의 {@code scale(0.2)} — 합해서 속도 × −0.1.
     */
    public static final double ARROW_DEFLECT_SCALE = -0.1;
    /** {@code getDeltaMovement().lengthSqr() < 1.0E-7}(MC 틱 속도)이면 되튄 화살은 제자리에서 끝난다. */
    public static final double ARROW_DEFLECT_MIN_MC_SPEED_SQR = 1.0E-7;
    /** 박힌 화살의 영속 체크포인트 간격(MC 틱). 수명 계수만 바뀌는 틱마다 원장을 다시 쓰지 않는다. */
    public static final int ARROW_GROUND_CHECKPOINT_MC_TICKS = 200;

    /**
     * [ARROW-GROUND] 바닐라 {@code AbstractArrow.Pickup}. 순서가 곧 바닐라 ordinal 이자 영속 값이다
     * ({@code tryPickup} 의 tableswitch 0 → 거절, 1 → 인벤토리 추가, 2 → 창작 모드만).
     */
    public enum Pickup { DISALLOWED, ALLOWED, CREATIVE_ONLY }

    /** 블록에 박혀 있는가(바닐라 {@code IN_GROUND}). 박힌 동안 {@link #vx} 등은 박힌 순간의 방향이다. */
    public boolean inGround;
    /** 줍기 규칙. 몹·주인 없는 화살과 옛 행은 {@link Pickup#DISALLOWED} 다. */
    public Pickup pickup = Pickup.DISALLOWED;
    /** 남은 흔들림(MC 틱, 바닐라 {@code shakeTime}). */
    public int shakeMcTicks;
    /** 박힌 뒤 흐른 MC 틱(바닐라 {@code life}). */
    public int groundLifeMcTicks;
    /** 박힌 블록(바닐라 {@code lastState}) — 블록 ID 와 상태. 박히지 않았으면 0. */
    public int groundBlockId;
    public int groundBlockState;

    public enum Kind {
        ARROW("arrow"),
        TRIDENT("trident"),
        SNOWBALL("snowball"),
        SPLASH_POTION("splash_potion"),
        FISHING_BOBBER("fishing_bobber"),
        FIREWORK_ROCKET("firework_rocket"),
        EGG("egg"),
        /**
         * 라마 침({@code LlamaSpit}). 피해·발사 속도·부정확도는 {@link LlamaRules} 가 소유하고,
         * 중력·항력은 눈덩이·달걀과 같은 투척 물리 계약을 그대로 쓴다
         * ({@code LlamaRules} divergence 3).
         */
        LLAMA_SPIT("llama_spit"),
        BREEZE_WIND_CHARGE("breeze_wind_charge"),
        WIND_CHARGE("wind_charge"),
        /**
         * 던진 엔더의 눈({@code EyeOfEnder}). 충돌·명중·영속이 없는 비행체라 이 클래스의
         * 인스턴스로 만들지 않고 {@code EnderEyeSystem} 이 소유한다 — 투사체 프로토콜 이름과
         * 투사체 id 공간만 이 등록부를 공유한다.
         */
        EYE_OF_ENDER("eye_of_ender"),
        /**
         * [TRIAL-GAP] 잔류형 물약({@code ThrownLingeringPotion}). 물리·착탄 판정은 투척 물약과
         * 같고, 착탄에서 즉발 스플래시 대신 효과 구름({@link #AREA_EFFECT_CLOUD})을 남긴다.
         */
        LINGERING_POTION("lingering_potion"),
        /**
         * [TRIAL-GAP] 효과 구름({@code AreaEffectCloud}). 움직이지 않는 개체라 물리가 없고
         * {@link #age} 가 바닐라 {@code tickCount}(MC 틱)다. 투사체 원장에 싣는 이유는 id 공간 ·
         * 방송(projectileSpawn/Update/Remove) · 영속을 그대로 쓰기 위해서다.
         */
        AREA_EFFECT_CLOUD("area_effect_cloud"),
        /**
         * [TRIAL-GAP] 불길한 아이템 소환기({@code OminousItemSpawner}). 물리가 없고
         * {@link #age} 가 {@code tickCount}(MC 틱)다. {@code spawn_item_after_ticks} 에 실은
         * 아이템을 아래로 쏘거나 떨구고 사라진다.
         */
        OMINOUS_ITEM_SPAWNER("ominous_item_spawner"),
        /**
         * [TRIAL-GAP] 작은 화염구({@code SmallFireball}). 중력이 없고 매 MC 틱
         * {@code (v + normalize(v)·0.1) × 0.95}(물속 0.8) 로 가속·감쇠한다. 명중하면 불 5 초와
         * 5 피해, 블록에 맞으면 맞은 면 바깥 칸이 비었으면 불을 놓는다.
         */
        SMALL_FIREBALL("small_fireball"),
        /**
         * [EC-MOBS] 셜커 탄환({@code ShulkerBullet}). 중력이 없고(대상을 잃으면 0.04) 대상을 향해 축 방향으로
         * 조향한다({@link ShulkerBulletRules}). 명중하면 4 피해와 공중 부양 200 MC 틱, 블록에 맞으면 사라진다.
         * 바닐라처럼 저장된다: 조향 상태는 {@link #steeringData} 칸이다.
         */
        SHULKER_BULLET("shulker_bullet"),
        /**
         * [DRAGON] 드래곤 화염구({@code DragonFireball}, AbstractHurtingProjectile 가속 0.1 · 관성 0.95). 주인(드래곤)
         * 이 아닌 개체나 블록에 닿으면 그 자리에 드래곤 숨결 구름({@link #DRAGON_BREATH})을 남기고 사라진다. 피해는
         * 구름이 준다. 위치 · 속도 · 주인으로 저장된다.
         */
        DRAGON_FIREBALL("dragon_fireball"),
        /**
         * [DRAGON] 드래곤 숨결 구름({@code AreaEffectCloud} + DRAGON_BREATH 입자): 화염구 착탄(반지름 3 → 7, 600 틱,
         * 즉시 피해 II)과 앉아 뿜는 불길({@code DragonSittingFlamingPhase}, 반지름 5, 200 틱, 즉시 피해 I). 대기
         * 20 틱 · 재적용 20 틱 · 사용 반지름 0. 빈 병으로 떠 담는다. 저장되고, 대기 · 크기 변화는 효과 세기에서 되살린다.
         */
        DRAGON_BREATH("dragon_breath"),
        /**
         * [CONTAINER-MENUS] 던진 경험치 병({@code ThrownExperienceBottle}). 투척 물약과 같은
         * 관성(공기 0.99 · 수중 0.8)에 자기 중력 0.07 을 받고, 무엇에 맞든 그 자리에 경험치
         * 3 + U(5) + U(5) 를 뿌린다(level event 2002 · 1053 은 클라가 제거 사유로 파생한다).
         */
        EXPERIENCE_BOTTLE("experience_bottle"),
        FLESH_SPIT("flesh_spit"),
        ENDER_PEARL("ender_pearl");

        private final String protocolName;

        Kind(String protocolName) {
            this.protocolName = protocolName;
        }

        public String protocolName() {
            return protocolName;
        }

        /**
         * [POT-PROJECTILE] Vanilla {@code #minecraft:impact_projectiles} (26.3 data pack
         * {@code tags/entity_type/impact_projectiles.json}: #arrows, firework_rocket, snowball, fireball,
         * small_fireball, egg, trident, dragon_fireball, wither_skull, wind_charge, breeze_wind_charge),
         * which {@code Projectile.mayBreak} requires. Client twin: {@code standaloneImpactProjectile}.
         */
        public boolean impactProjectile() {
            return switch (this) {
                case ARROW, TRIDENT, SNOWBALL, FIREWORK_ROCKET, EGG, BREEZE_WIND_CHARGE, WIND_CHARGE,
                        SMALL_FIREBALL, DRAGON_FIREBALL -> true;
                default -> false;
            };
        }
    }

    public ProjectileSim(double x, double y, double z, double vx, double vy, double vz) {
        this(Kind.ARROW, x, y, z, vx, vy, vz, DEFAULT_DAMAGE, null, 0, null, null);
    }

    public ProjectileSim(double x, double y, double z, double vx, double vy, double vz, int damage) {
        this(Kind.ARROW, x, y, z, vx, vy, vz, damage, null, 0, null, null);
    }

    private ProjectileSim(Kind kind, double x, double y, double z,
                          double vx, double vy, double vz, int damage,
                          String shooterNickname, long shooterMobId, MobType shooterMobType,
                          ProjectileEffect effect) {
        this.kind = kind;
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.damage = damage;
        this.shooterNickname = shooterNickname;
        this.shooterMobId = shooterMobId;
        this.shooterMobType = shooterMobType;
        this.effect = effect;
    }

    /** 이 투사체를 쏜/던진 플레이어 닉네임. 몹이 쏜 것이면 null 이다. */
    public String shooterNickname() {
        return shooterNickname;
    }

    public ProjectilePersistenceSnapshot persistenceSnapshot() {
        return new ProjectilePersistenceSnapshot(id, kind.name(), shooterNickname, shooterMobId,
                shooterMobType == null ? null : shooterMobType.name(), x, y, z, vx, vy, vz,
                age, landed, lifetimeTicks, damage,
                effect == null ? null : effect.effect().name(),
                effect == null ? 0 : effect.amplifier(),
                effect == null ? 0 : effect.durationTicks(), hookedPlayer, hookedMobId,
                spoiledEgg, eggVariant, recoverableDurability, noDeflectMcTicks,
                payloadItemType, payloadCount, cloudRadius, spawnItemAfterMcTicks,
                weaponEnchantments.isEmpty() ? null : weaponEnchantments.toHex(),
                recoverEnchantments == 0L ? null : recoverEnchantments, recoverComponentData,
                inGround, pickup.ordinal(), shakeMcTicks, groundLifeMcTicks,
                groundBlockId, groundBlockState, potionKey, steeringData(), hookedPlacedEntityId);
    }

    /** Restores one row from the durable live-projectile ledger without replaying a launch event. */
    public static ProjectileSim restore(ProjectilePersistenceSnapshot s) {
        ProjectileEffect restoredEffect = s.effect() == null ? null
                : new ProjectileEffect(StatusEffect.valueOf(s.effect()),
                        s.effectAmplifier(), s.effectDurationTicks());
        MobType restoredShooterType = s.shooterMobType() == null ? null
                : MobType.valueOf(s.shooterMobType());
        ProjectileSim projectile = new ProjectileSim(Kind.valueOf(s.kind()),
                s.x(), s.y(), s.z(), s.velocityX(), s.velocityY(), s.velocityZ(), s.damage(),
                s.ownerNickname(), s.shooterMobId(), restoredShooterType, restoredEffect);
        projectile.id = s.projectileId();
        projectile.age = s.age();
        projectile.landed = s.landed();
        projectile.lifetimeTicks = s.lifetimeTicks();
        projectile.hookedPlayer = s.hookedPlayer();
        projectile.hookedMobId = s.hookedMobId();
        projectile.hookedPlacedEntityId = s.hookedPlacedEntityId();
        projectile.spoiledEgg = s.spoiledEgg();
        projectile.eggVariant = s.eggVariant();
        projectile.recoverableDurability = s.recoverableDurability();
        projectile.noDeflectMcTicks = s.noDeflectMcTicks();
        projectile.payloadItemType = s.payloadItemType();
        projectile.payloadCount = s.payloadCount();
        projectile.cloudRadius = s.cloudRadius();
        projectile.spawnItemAfterMcTicks = s.spawnItemAfterMcTicks();
        // [ENCHANT-WIDE] 인챈트가 없던 옛 행은 null 이라 빈 집합·0·null 로 그대로 복원된다.
        projectile.weaponEnchantments = s.weaponEnchantments() == null
                ? com.gameexpert.engine.enchant.WideEnchantments.EMPTY
                : com.gameexpert.engine.enchant.WideEnchantments.fromHex(s.weaponEnchantments());
        projectile.recoverEnchantments = s.recoverEnchantments() == null
                ? 0L : s.recoverEnchantments();
        projectile.recoverComponentData = s.recoverComponentData();
        projectile.inGround = s.inGround();
        projectile.pickup = Pickup.values()[s.pickup()];
        projectile.shakeMcTicks = s.shakeMcTicks();
        projectile.groundLifeMcTicks = s.groundLifeMcTicks();
        projectile.groundBlockId = s.groundBlockId();
        projectile.groundBlockState = s.groundBlockState();
        projectile.potionKey = s.potionKey();
        if (projectile.kind == Kind.SHULKER_BULLET) {
            projectile.restoreSteering(s.steeringData());
        } else if (projectile.kind == Kind.DRAGON_BREATH) {
            // [DRAGON] 숨결 구름의 대기 · 크기 변화 · 사용 반지름은 생성 규칙에서 정해진다(앉은 불길 = 즉시 피해 I).
            boolean flame = restoredEffect != null && restoredEffect.amplifier() == 0;
            projectile.cloudWaitTime = DRAGON_BREATH_WAIT_TIME_MC_TICKS;
            projectile.cloudRadiusPerTick = flame ? 0.0 : DRAGON_BREATH_RADIUS_PER_TICK;
            projectile.cloudRadiusOnUse = 0.0;
        }
        return projectile;
    }

    /** 찌가 엔티티에 걸려 있는가. 걸린 찌는 물리 대신 낚시 시스템이 위치를 소유한다. */
    public boolean hooked() {
        return hookedPlayer != null || hookedMobId != 0L || hookedPlacedEntityId != 0L;
    }

    /** 몹 발사 이벤트로부터 종류가 보존된 투사체를 생성한다. */
    public static ProjectileSim fromShot(MobEvent.ShootArrow s) {
        return new ProjectileSim(s.kind(), s.x(), s.y(), s.z(),
                s.vx(), s.vy(), s.vz(), s.damage(), null, 0, null, s.effect());
    }

    public static ProjectileSim fromShot(MobEvent.ShootArrow s, Mob shooter) {
        ProjectileSim shot = new ProjectileSim(s.kind(), s.x(), s.y(), s.z(),
                s.vx(), s.vy(), s.vz(), s.damage(), null, shooter.id, shooter.type, s.effect());
        // [POTION-COLOR] 마녀 투척 물약은 그 물약 아이템을 실어 projectileSpawn.itemType 으로 보낸다.
        if (s.kind() == Kind.SPLASH_POTION) shot.payloadItemType = MobEffectRules.witchSplashItem(s.effect());
        return shot;
    }

    // ── [EC-MOBS] 셜커 탄환 ──
    private ShulkerBulletRules.State bullet;
    private String bulletTargetNickname;
    private long bulletTargetMobId;
    private ShulkerBulletRules.BulletRandom bulletRandom;
    /** {@code Projectile.leftOwner}: 주인 상자(1 부풀림)를 벗어나기 전에는 주인을 맞히지 않는다. */
    private boolean bulletLeftOwner;
    /** 바닐라 {@code tickCount}(MC 틱). 엔티티 명중 여유 {@code computeMargin} 이 쓴다. */
    private int bulletMcTicks;
    /** 명중 효과: 공중 부양 200 MC 틱(10 TPS 100 틱). */
    public static final ProjectileEffect SHULKER_BULLET_EFFECT = new ProjectileEffect(
            com.gameexpert.engine.effect.StatusEffect.LEVITATION, 0,
            ShulkerBulletRules.LEVITATION_MC_TICKS / 2);

    /**
     * [EC-MOBS] 바닐라 {@code new ShulkerBullet(level, shulker, target, axis)}: 셜커 상자 중심에서 출발해
     * {@code currentMoveDirection = UP} 뒤 {@code selectNextMoveDirection(axis, target)}.
     */
    public static ProjectileSim shulkerBullet(MobEvent.ShootShulkerBullet shot, Mob owner,
            ShulkerBulletRules.Target target, ShulkerBulletRules.SteerWorld steer,
            ShulkerBulletRules.BulletRandom random) {
        ProjectileSim sim = new ProjectileSim(Kind.SHULKER_BULLET, shot.x(), shot.y(), shot.z(), 0, 0, 0,
                ShulkerBulletRules.DAMAGE, null, owner.id, owner.type, SHULKER_BULLET_EFFECT);
        sim.bullet = new ShulkerBulletRules.State(shot.x(), shot.y(), shot.z());
        sim.bulletTargetNickname = shot.targetNickname();
        sim.bulletTargetMobId = shot.targetMobId();
        sim.bulletRandom = random;
        ShulkerBulletRules.selectNextMoveDirection(sim.bullet, shot.axis(), target, steer, random);
        return sim;
    }

    /**
     * [EC-MOBS] 셜커 탄환 조향 상태의 영속 문자열(바닐라 {@code ShulkerBullet.addAdditionalSaveData} 의 Target · Dir ·
     * Steps · TXD/TYD/TZD 에 이 저장소의 탄환 난수 상태 · leftOwner · tickCount 를 더한다). 위치 · 속도는 투사체 칸이다.
     * {@code SB1:대상몹:방향:걸음:TXD:TYD:TZD:난수:떠남:MC틱:대상닉네임} — 실수는 IEEE 비트 16진, 닉네임은 마지막(없으면 빈
     * 칸). 셜커 탄환이 아니면 null.
     */
    public String steeringData() {
        if (kind != Kind.SHULKER_BULLET || bullet == null) return null;
        return String.join(":", "SB1", Long.toString(bulletTargetMobId), Integer.toString(bullet.moveDirection),
                Integer.toString(bullet.flightSteps),
                Long.toHexString(Double.doubleToRawLongBits(bullet.targetDeltaX)),
                Long.toHexString(Double.doubleToRawLongBits(bullet.targetDeltaY)),
                Long.toHexString(Double.doubleToRawLongBits(bullet.targetDeltaZ)),
                Long.toHexString(bulletRandom.state()), bulletLeftOwner ? "1" : "0",
                Integer.toString(bulletMcTicks), bulletTargetNickname == null ? "" : bulletTargetNickname);
    }

    private void restoreSteering(String data) {
        String[] parts = data == null ? new String[0] : data.split(":", 11);
        if (parts.length != 11 || !"SB1".equals(parts[0])) {
            throw new IllegalArgumentException("invalid shulker bullet steering data");
        }
        bullet = new ShulkerBulletRules.State(x, y, z);
        bullet.vx = vx;
        bullet.vy = vy;
        bullet.vz = vz;
        bulletTargetMobId = Long.parseLong(parts[1]);
        bullet.moveDirection = Integer.parseInt(parts[2]);
        bullet.flightSteps = Integer.parseInt(parts[3]);
        bullet.targetDeltaX = Double.longBitsToDouble(Long.parseUnsignedLong(parts[4], 16));
        bullet.targetDeltaY = Double.longBitsToDouble(Long.parseUnsignedLong(parts[5], 16));
        bullet.targetDeltaZ = Double.longBitsToDouble(Long.parseUnsignedLong(parts[6], 16));
        bulletRandom = ShulkerBulletRules.BulletRandom.ofState(Long.parseLong(parts[7], 16));
        bulletLeftOwner = "1".equals(parts[8]);
        bulletMcTicks = Integer.parseInt(parts[9]);
        bulletTargetNickname = parts[10].isEmpty() ? null : parts[10];
        if (bulletTargetMobId < 0 || bullet.moveDirection < -1 || bullet.moveDirection > 5 || bulletMcTicks < 0
                || !Double.isFinite(bullet.targetDeltaX) || !Double.isFinite(bullet.targetDeltaY)
                || !Double.isFinite(bullet.targetDeltaZ)) {
            throw new IllegalArgumentException("invalid shulker bullet steering data");
        }
    }

    public String bulletTargetNickname() { return bulletTargetNickname; }
    public long bulletTargetMobId() { return bulletTargetMobId; }

    /** 조향이 보는 월드(빈 칸 · 딛을 수 있는 윗면). */
    public static ShulkerBulletRules.SteerWorld steerWorld(MobWorldView world) {
        return new ShulkerBulletRules.SteerWorld() {
            @Override
            public boolean empty(int bx, int by, int bz) {
                return (world.getBlock(bx, by, bz) & 0xffff) == 0;
            }

            @Override
            public boolean canStandOn(int bx, int by, int bz) {
                int block = world.getBlock(bx, by, bz);
                return block >= 0 && ShulkerSupport.sturdyFace(block & 0xffff,
                        world.blockState(bx, by, bz, block & 0xffff), ItemFrameRules.UP);
            }
        };
    }

    private ShulkerBulletRules.Target bulletTarget(MobWorldView world, java.util.List<Mob> mobs) {
        if (bulletTargetNickname != null) {
            for (PlayerSnapshot p : world.players()) {
                if (p.alive() && bulletTargetNickname.equals(p.nickname())) {
                    return new ShulkerBulletRules.Target(p.x(), p.y(), p.z(), PLAYER_HEIGHT);
                }
            }
            return null;
        }
        if (bulletTargetMobId != 0L) {
            for (Mob mob : mobs) {
                if (mob.id == bulletTargetMobId && !mob.isDead() && !mob.removed) {
                    return new ShulkerBulletRules.Target(mob.x, mob.y, mob.z, mob.height());
                }
            }
        }
        return null;
    }

    /**
     * [EC-MOBS] 셜커 탄환 한 권위 틱 = MC 틱 두 번의 {@code ShulkerBullet.tick}: 조향(대상이 없으면 중력) →
     * {@code getHitResultOnMoveVector}(블록을 먼저 자르고, 그 선분에서 {@code computeMargin} 만큼 부풀린 엔티티
     * 상자 중 가장 가까운 것) → 이동 → 명중 처리 → 방향 재선택.
     */
    private MobEvent tickShulkerBullet(MobWorldView world, java.util.List<Mob> mobs) {
        ShulkerBulletRules.SteerWorld steer = steerWorld(world);
        for (int mcTick = 0; mcTick < 2 && alive; mcTick++) {
            bulletMcTicks++;
            ShulkerBulletRules.Target target = bulletTarget(world, mobs);
            ShulkerBulletRules.steerVelocity(bullet, target != null);
            double px = bullet.x, py = bullet.y, pz = bullet.z;
            double nx = px + bullet.vx, ny = py + bullet.vy, nz = pz + bullet.vz;
            double tVoxel = voxelHitT(world, px, py, pz, nx, ny, nz);
            double limit = Double.isNaN(tVoxel) ? 1.0 : tVoxel;
            double margin = ShulkerBulletRules.hitMargin(bulletMcTicks);
            if (!bulletLeftOwner) bulletLeftOwner = leftOwner(mobs, px, py, pz);
            PlayerSnapshot hitPlayer = null;
            Mob hitMob = null;
            double best = Double.POSITIVE_INFINITY;
            for (PlayerSnapshot p : world.players()) {
                if (!p.alive()) continue;
                double t = segAabbT(px, py, pz, nx, ny, nz, p.x() - PLAYER_HALF - margin, p.y() - margin,
                        p.z() - PLAYER_HALF - margin, p.x() + PLAYER_HALF + margin, p.y() + PLAYER_HEIGHT + margin,
                        p.z() + PLAYER_HALF + margin);
                if (t >= 0 && t <= limit && t < best) {
                    best = t;
                    hitPlayer = p;
                }
            }
            for (Mob mob : mobs) {
                if (mob.isDead() || mob.removed) continue;
                if (mob.id == shooterMobId && !bulletLeftOwner) continue;
                double[] box = mob.authorityAabb();
                double t = segAabbT(px, py, pz, nx, ny, nz, box[0] - margin, box[1] - margin, box[2] - margin,
                        box[3] + margin, box[4] + margin, box[5] + margin);
                if (t >= 0 && t <= limit && t < best) {
                    best = t;
                    hitMob = mob;
                    hitPlayer = null;
                }
            }
            MobWorldView.PlacedProjectileHit placed = world.placedProjectileHit(px, py, pz, nx, ny, nz, margin);
            boolean hitPlaced = placed != null && placed.t() >= 0 && placed.t() <= limit && placed.t() < best;
            bullet.x = nx;
            bullet.y = ny;
            bullet.z = nz;
            x = nx;
            y = ny;
            z = nz;
            vx = bullet.vx;
            vy = bullet.vy;
            vz = bullet.vz;
            if (hitPlaced) {
                alive = false;
                terminalReason = "placed_entity_hit";
                return new MobEvent.AttackPlacedEntity(placed.entityId(), kind, damage, false,
                        vx * vx + vy * vy + vz * vz, px, pz);
            }
            if (hitPlayer != null) {
                alive = false;
                terminalReason = "player_hit";
                return new MobEvent.AttackPlayer(hitPlayer.nickname(), damage, px, pz, null, shooterMobId,
                        shooterMobType, effect);
            }
            if (hitMob != null) {
                alive = false;
                terminalReason = "mob_hit";
                targetMobId = hitMob.id;
                return new MobEvent.AttackMob(hitMob.id, damage, null, shooterMobId, shooterMobType, effect);
            }
            if (!Double.isNaN(tVoxel)) {
                x = px + (nx - px) * tVoxel;
                y = py + (ny - py) * tVoxel;
                z = pz + (nz - pz) * tVoxel;
                alive = false;
                terminalReason = "block_hit";
                return null;
            }
            if (y < Blocks.MIN_Y - 64) {
                alive = false;
                terminalReason = "expired";
                return null;
            }
            ShulkerBulletRules.afterMove(bullet, target, steer, bulletRandom);
        }
        return null;
    }

    /** {@code Projectile.checkLeftOwner}: 주인 상자를 1 부풀린 상자와 탄환 상자가 겹치지 않으면 떠났다. */
    private boolean leftOwner(java.util.List<Mob> mobs, double px, double py, double pz) {
        double h = ShulkerBulletRules.SIZE * 0.5;
        for (Mob mob : mobs) {
            if (mob.id != shooterMobId || mob.isDead() || mob.removed) continue;
            double[] box = mob.authorityAabb();
            return !(px - h < box[3] + 1.0 && px + h > box[0] - 1.0 && py < box[4] + 1.0
                    && py + ShulkerBulletRules.SIZE > box[1] - 1.0 && pz - h < box[5] + 1.0 && pz + h > box[2] - 1.0);
        }
        return true;
    }

    public static ProjectileSim fromPlayer(String nickname, double x, double y, double z,
                                            double vx, double vy, double vz, int damage) {
        ProjectileSim arrow = new ProjectileSim(Kind.ARROW, x, y, z, vx, vy, vz,
                damage, nickname, 0, null, null);
        // [ARROW-GROUND] AbstractArrow.setOwner(Player): DISALLOWED 였던 줍기가 ALLOWED 가 된다.
        // 무한·다중 발사 사본(INTANGIBLE_PROJECTILE)은 발사 경로가 CREATIVE_ONLY 로 낮춘다.
        arrow.pickup = Pickup.ALLOWED;
        return arrow;
    }

    public static ProjectileSim fromPlayerWindCharge(String nickname, double x, double y, double z,
                                                      double vx, double vy, double vz) {
        ProjectileSim charge = new ProjectileSim(Kind.WIND_CHARGE, x, y, z, vx, vy, vz,
                1, nickname, 0, null, null);
        charge.noDeflectMcTicks = 5;
        return charge;
    }

    /**
     * [TRIDENT] 플레이어가 던진 삼지창. 궤적·명중 판정은 드라운드 투척과 <b>같은</b>
     * {@link Kind#TRIDENT} 한 구현을 그대로 쓰고, 갈리는 것은 두 가지뿐이다 — 던진 주인이
     * 있어 자기 자신을 맞히지 않는 것과, 종결 지점에서 {@code recoverableDurability} 만큼
     * 남은 삼지창이 회수물로 떨어지는 것이다.
     *
     * @param recoverableDurability 투척 내구 소모를 이미 반영한 남은 내구(1 이상).
     */
    public static ProjectileSim fromPlayerTrident(String nickname, double x, double y, double z,
                                                  double vx, double vy, double vz,
                                                  int damage, int recoverableDurability) {
        if (recoverableDurability <= 0) {
            throw new IllegalArgumentException("회수될 삼지창 내구는 1 이상이어야 합니다.");
        }
        ProjectileSim trident = new ProjectileSim(Kind.TRIDENT, x, y, z, vx, vy, vz,
                damage, nickname, 0, null, null);
        trident.recoverableDurability = recoverableDurability;
        return trident;
    }

    /** 바닐라 눈덩이: 현재 프로젝트에 Blaze가 없어 모든 대상 피해는 0이며 충돌 즉시 소멸합니다. */
    public static ProjectileSim fromEnderPearl(String nickname, double x, double y, double z,
                                             double vx, double vy, double vz) {
        return new ProjectileSim(Kind.ENDER_PEARL, x, y, z, vx, vy, vz,
                0, nickname, 0, null, null);
    }

    public static ProjectileSim fromSnowball(String nickname, double x, double y, double z,
                                             double vx, double vy, double vz) {
        return new ProjectileSim(Kind.SNOWBALL, x, y, z, vx, vy, vz,
                0, nickname, 0, null, null);
    }

    /**
     * 바닐라 달걀({@code ThrownEgg}): {@code ThrowableProjectile} 기본 중력·저항이라 물리는
     * 눈덩이와 한 글자도 다르지 않고, {@code onHitEntity} 의 피해도 0.0F 다. 부화 굴림은
     * 물리가 아니라 종결 사실을 받는 {@link MobRuntime#hatchEggChicks} 가 소유한다.
     */
    public static ProjectileSim fromEgg(String nickname, double x, double y, double z,
                                        double vx, double vy, double vz, String variant) {
        if (!"temperate".equals(variant) && !"warm".equals(variant) && !"cold".equals(variant)) {
            throw new IllegalArgumentException("thrown egg chicken variant is required");
        }
        ProjectileSim egg = new ProjectileSim(Kind.EGG, x, y, z, vx, vy, vz,
                0, nickname, 0, null, null);
        egg.eggVariant = variant;
        return egg;
    }

    /**
     * [ZOMBIE-ANIMAL] 상한 달걀. {@link #fromEgg} 와 <b>같은 투사체</b>이며 표식 하나만 다르다 —
     * 물리·피해·프로토콜 종류가 전부 같고 부화만 하지 않는다.
     */
    public static ProjectileSim fromSpoiledEgg(String nickname, double x, double y, double z,
                                               double vx, double vy, double vz) {
        ProjectileSim egg = new ProjectileSim(Kind.EGG, x, y, z, vx, vy, vz,
                0, nickname, 0, null, null);
        egg.spoiledEgg = true;
        return egg;
    }

    /**
     * 바닐라 {@code ThrowableProjectile} 물리(중력 0.03 · 공기 0.99 · 수중 0.8)를 쓰는가.
     * 눈덩이와 달걀이 같은 부모라 두 종류가 정확히 같은 궤적을 그린다. 라마 침도 여기에 실린다
     * ({@code LlamaRules} divergence 3: 중력·항력은 투사체 정본이 소유한다).
     */
    private static boolean throwableItemPhysics(Kind kind) {
        if (kind == Kind.ENDER_PEARL) return true;
        return kind == Kind.SNOWBALL || kind == Kind.EGG || kind == Kind.LLAMA_SPIT;
    }

    /**
     * [TRIDENT] 바닐라 {@code AbstractArrow} 물리(중력 0.05 · 수중 관성 0.6)를 쓰는가.
     * 화살과 석궁 볼트는 같은 {@link Kind#ARROW} 한 종류이고 삼지창({@code ThrownTrident})도
     * 같은 부모라, 세 가지가 정확히 같은 감쇠를 받는다 — 한 종만 갈라놓으면 그쪽이 divergence 다.
     */
    private static boolean arrowFamilyPhysics(Kind kind) {
        return kind == Kind.ARROW || kind == Kind.TRIDENT;
    }

    public boolean isWindCharge() {
        return kind == Kind.BREEZE_WIND_CHARGE || kind == Kind.WIND_CHARGE;
    }

    /** Explosion strength used by the official trigger-only gust burst. */
    public double windBurstPower() {
        return kind == Kind.BREEZE_WIND_CHARGE ? 3.0 : kind == Kind.WIND_CHARGE ? 1.2 : 0.0;
    }

    /** Player-thrown charges use the official SimpleExplosionDamageCalculator 1.22 multiplier. */
    public double windKnockbackMultiplier() {
        return kind == Kind.WIND_CHARGE ? 1.22 : 1.0;
    }

    /** Collision bursts plus AbstractWindCharge's max-build-height + 30 failsafe burst. */
    public boolean shouldWindBurstOnTerminal() {
        return isWindCharge() && ("block_hit".equals(terminalReason)
                || "player_hit".equals(terminalReason)
                || "mob_hit".equals(terminalReason)
                || "placed_entity_hit".equals(terminalReason)
                || "expired".equals(terminalReason) && y > Blocks.MAX_Y + 30.0);
    }

    /**
     * 낚시찌: 포물선으로 날아가 수면에 안착한다. 비행 중 엔티티에 닿으면 거기에 걸리고
     * (바닐라 onHitEntity), 물이 아닌 블록에 부딪히거나 MAX_FLIGHT_TICKS 안에 착수하지도
     * 걸리지도 못하면 캐스팅이 취소된다.
     */
    public static ProjectileSim fromFishingCast(String nickname, double x, double y, double z,
                                                double vx, double vy, double vz) {
        return new ProjectileSim(Kind.FISHING_BOBBER, x, y, z, vx, vy, vz,
                0, nickname, 0, null, null);
    }

    /**
     * 폭죽 로켓: 별(firework star)이 없어 폭발 피해는 0이며, 수명이 다하면 그 자리에서 팝한다.
     * 엔티티·플레이어를 맞히지 않고 블록에 막히면 즉시 터진다(바닐라 동일).
     */
    public static ProjectileSim fromFireworkRocket(String nickname, double x, double y, double z,
                                                   double vx, double vy, double vz,
                                                   int lifetimeTicks) {
        ProjectileSim rocket = new ProjectileSim(Kind.FIREWORK_ROCKET, x, y, z, vx, vy, vz,
                0, nickname, 0, null, null);
        rocket.lifetimeTicks = lifetimeTicks;
        return rocket;
    }

    /** 투척 물약: 충돌 대상과 무관하게 착탄점에서 광역 효과를 낸다. */
    public static ProjectileSim fromPotion(long shooterMobId, double x, double y, double z,
                                           double vx, double vy, double vz,
                                           ProjectileEffect effect) {
        return new ProjectileSim(Kind.SPLASH_POTION, x, y, z, vx, vy, vz, 0, null,
                shooterMobId, null, effect);
    }

    /**
     * [POTION] 플레이어가 던진 투척 물약. 마녀 물약과 물리·착탄 규칙이 한 글자도 다르지 않고
     * 던진 본인만 직격 판정에서 제외된다(바닐라 {@code ThrowableProjectile} 과 같다).
     */
    public static ProjectileSim fromPlayerPotion(String nickname, double x, double y, double z,
                                                 double vx, double vy, double vz,
                                                 ProjectileEffect effect) {
        return new ProjectileSim(Kind.SPLASH_POTION, x, y, z, vx, vy, vz, 0, nickname,
                0, null, effect);
    }

    /**
     * [POTION-COLOR] 던진 투척 물약 아이템을 실은 플레이어 물약. projectileSpawn.itemType 으로 나가
     * 클라이언트가 병을 그 물약 색(PotionContents.getColor)으로 칠한다.
     */
    public static ProjectileSim fromPlayerPotion(String nickname, double x, double y, double z,
                                                 double vx, double vy, double vz,
                                                 ProjectileEffect effect, short itemType) {
        ProjectileSim potion = fromPlayerPotion(nickname, x, y, z, vx, vy, vz, effect);
        if (itemType > 0) potion.payloadItemType = itemType;
        return potion;
    }

    /**
     * [CONTAINER-MENUS] 던진(또는 발사기가 쏜, {@code nickname == null}) 경험치 병.
     */
    public static ProjectileSim experienceBottle(String nickname, double x, double y, double z,
                                                 double vx, double vy, double vz) {
        ProjectileSim bottle = new ProjectileSim(Kind.EXPERIENCE_BOTTLE, x, y, z, vx, vy, vz, 0,
                nickname, 0, null, null);
        bottle.payloadItemType = com.gameexpert.engine.inventory.PlayerInventory.EXPERIENCE_BOTTLE;
        return bottle;
    }

    // ── [TRIAL-GAP] 잔류형 물약 · 효과 구름 · 불길한 아이템 소환기 · 작은 화염구 · 효과 화살 ──

    /** 효과 구름 기본 반지름({@code ThrownLingeringPotion.onHitAsPotion} 의 setRadius 3.0). */
    public static final double CLOUD_RADIUS = 3.0;
    /** {@code setRadiusOnUse(-0.5)}. */
    public static final double CLOUD_RADIUS_ON_USE = -0.5;
    /** {@code setDuration(600)}(MC 틱). */
    public static final int CLOUD_DURATION_MC_TICKS = 600;
    /** {@code setWaitTime(10)}(MC 틱). */
    public static final int CLOUD_WAIT_TIME_MC_TICKS = 10;
    /** {@code setRadiusPerTick(-radius / duration)} — float 나눗셈이다. */
    public static final double CLOUD_RADIUS_PER_TICK = -3.0f / 600.0f;
    /** {@code AreaEffectCloud.DEFAULT_REAPPLICATION_DELAY}. */
    public static final int CLOUD_REAPPLICATION_DELAY_MC_TICKS = 20;
    /** {@code AreaEffectCloud.TIME_BETWEEN_APPLICATIONS}. */
    public static final int CLOUD_TIME_BETWEEN_APPLICATIONS = 5;
    /** {@code AreaEffectCloud.MINIMAL_RADIUS}. */
    public static final double CLOUD_MINIMAL_RADIUS = 0.5;
    /** {@code AreaEffectCloud.HEIGHT}. */
    public static final double CLOUD_HEIGHT = 0.5;
    /** {@code OminousItemSpawner.TICKS_BEFORE_ABOUT_TO_SPAWN_SOUND}. */
    public static final int OMINOUS_ABOUT_TO_SPAWN_SOUND_MC_TICKS = 36;
    /** {@code AbstractHurtingProjectile.INITAL_ACCELERATION_POWER}. */
    public static final double FIREBALL_ACCELERATION = 0.1;
    /** {@code SmallFireball.getInertia}/{@code getLiquidInertia}. */
    public static final double FIREBALL_INERTIA = 0.95;
    public static final double FIREBALL_LIQUID_INERTIA = 0.8;
    /** {@code SmallFireball.onHitEntity}: igniteForSeconds(5) · hurt 5.0. */
    public static final int FIREBALL_DAMAGE = 5;
    public static final int FIREBALL_FIRE_SECONDS = 5;

    /** [TRIAL-GAP] 플레이어가 던진 잔류형 물약. 물리·착탄은 투척 물약과 같다. */
    public static ProjectileSim fromPlayerLingeringPotion(String nickname, double x, double y,
            double z, double vx, double vy, double vz, ProjectileEffect effect, short itemType) {
        ProjectileSim potion = new ProjectileSim(Kind.LINGERING_POTION, x, y, z, vx, vy, vz, 0,
                nickname, 0, null, effect);
        potion.payloadItemType = itemType;
        return potion;
    }

    /** [TRIAL-GAP] 주인 없는(불길한 아이템 소환기가 쏜) 잔류형 물약. */
    public static ProjectileSim dispensedLingeringPotion(double x, double y, double z,
            double vx, double vy, double vz, ProjectileEffect effect, short itemType) {
        return fromPlayerLingeringPotion(null, x, y, z, vx, vy, vz, effect, itemType);
    }

    /**
     * [TRIAL-GAP] 잔류형 물약이 남기는 효과 구름. {@code effect} 는 이미 잔류형 배율 0.25 를 곱한
     * 값이고 {@code potionItem} 은 색을 정한다.
     */
    public static ProjectileSim areaEffectCloud(String ownerNickname, double x, double y,
            double z, ProjectileEffect effect, short potionItem) {
        ProjectileSim cloud = new ProjectileSim(Kind.AREA_EFFECT_CLOUD, x, y, z, 0, 0, 0, 0,
                ownerNickname, 0, null, effect);
        cloud.payloadItemType = potionItem;
        cloud.cloudRadius = CLOUD_RADIUS;
        cloud.lifetimeTicks = CLOUD_DURATION_MC_TICKS;
        return cloud;
    }

    /** [DRAGON] {@code DragonFireball.onHit} 의 구름: 반지름 3, 600 틱, (7 − 3)/600 씩 커지고 즉시 피해 II. */
    public static final double DRAGON_BREATH_RADIUS = 3.0;
    public static final int DRAGON_BREATH_DURATION_MC_TICKS = 600;
    public static final double DRAGON_BREATH_RADIUS_PER_TICK = (7.0f - 3.0f) / 600;
    /** [DRAGON] {@code DragonSittingFlamingPhase} 의 불길: 반지름 5, 200 틱, 즉시 피해 I. */
    public static final double DRAGON_FLAME_RADIUS = 5.0;
    public static final int DRAGON_FLAME_DURATION_MC_TICKS = 200;
    /** [DRAGON] {@code AreaEffectCloud} 기본 waitTime. */
    public static final int DRAGON_BREATH_WAIT_TIME_MC_TICKS = 20;

    /**
     * [DRAGON] 드래곤 화염구. {@code dir} 은 단위 벡터(MC 틱 속도 1)이고 권위 틱 변위는 두 배다. 주인 드래곤은
     * 맞히지 않는다.
     */
    public static ProjectileSim dragonFireball(long dragonMobId, double x, double y, double z,
            double dirX, double dirY, double dirZ) {
        return new ProjectileSim(Kind.DRAGON_FIREBALL, x, y, z, dirX * 2.0, dirY * 2.0, dirZ * 2.0, 0,
                null, dragonMobId, null, null);
    }

    /** [DRAGON] 드래곤 숨결 구름(화염구 착탄 {@code flame == false} · 앉은 불길 {@code flame == true}). */
    public static ProjectileSim dragonBreath(long dragonMobId, double x, double y, double z, boolean flame) {
        ProjectileSim cloud = new ProjectileSim(Kind.DRAGON_BREATH, x, y, z, 0, 0, 0, 0, null, dragonMobId, null,
                new ProjectileEffect(com.gameexpert.engine.effect.StatusEffect.INSTANT_DAMAGE, flame ? 0 : 1, 1));
        cloud.cloudRadius = flame ? DRAGON_FLAME_RADIUS : DRAGON_BREATH_RADIUS;
        cloud.lifetimeTicks = flame ? DRAGON_FLAME_DURATION_MC_TICKS : DRAGON_BREATH_DURATION_MC_TICKS;
        cloud.cloudWaitTime = DRAGON_BREATH_WAIT_TIME_MC_TICKS;
        cloud.cloudRadiusPerTick = flame ? 0.0 : DRAGON_BREATH_RADIUS_PER_TICK;
        cloud.cloudRadiusOnUse = 0.0;
        return cloud;
    }

    /** [DRAGON] 효과 구름 종류(잔류형 물약 구름 · 드래곤 숨결). */
    public boolean isCloud() {
        return kind == Kind.AREA_EFFECT_CLOUD || kind == Kind.DRAGON_BREATH;
    }

    /** [TRIAL-GAP] 불길한 아이템 소환기({@code OminousItemSpawner.create}). */
    public static ProjectileSim ominousItemSpawner(double x, double y, double z, short itemType,
            int count, int spawnItemAfterMcTicks) {
        if (itemType <= 0 || count <= 0 || spawnItemAfterMcTicks <= 0) {
            throw new IllegalArgumentException("ominous item spawner payload is required");
        }
        ProjectileSim spawner = new ProjectileSim(Kind.OMINOUS_ITEM_SPAWNER, x, y, z, 0, 0, 0, 0,
                null, 0, null, null);
        spawner.payloadItemType = itemType;
        spawner.payloadCount = count;
        spawner.spawnItemAfterMcTicks = spawnItemAfterMcTicks;
        return spawner;
    }

    /** [TRIAL-GAP] 주인 없는 작은 화염구(화염구 아이템 발사). */
    public static ProjectileSim dispensedSmallFireball(double x, double y, double z,
            double vx, double vy, double vz) {
        return new ProjectileSim(Kind.SMALL_FIREBALL, x, y, z, vx, vy, vz, FIREBALL_DAMAGE,
                null, 0, null, null);
    }

    /**
     * [TRIAL-GAP] 주인 없는 화살/효과 화살(발사형 아이템 발사). 바닐라 {@code AbstractArrow}
     * 명중 피해 {@code ceil(속력 × 기본 피해 2.0)}(속력은 MC 틱당)을 싣는다.
     * [CONTAINER-MENUS] 속도는 이 저장소의 화살 모형({@link #ARROW_SIMULATION_SCALE}: 바닐라 MC 틱
     * 속력 × 0.5, 활·발사기와 같은 척도)이므로 MC 틱 속력은 그 척도로 되돌려 구한다.
     */
    public static ProjectileSim dispensedArrow(double x, double y, double z,
            double vx, double vy, double vz, ProjectileEffect effect, short itemType) {
        double mcSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz) / ARROW_SIMULATION_SCALE;
        int damage = Math.max(0, (int) Math.ceil(mcSpeed * 2.0));
        ProjectileSim arrow = new ProjectileSim(Kind.ARROW, x, y, z, vx, vy, vz, damage,
                null, 0, null, effect);
        arrow.payloadItemType = itemType;
        return arrow;
    }

    /**
     * [CONTAINER-MENUS] An ownerless tipped arrow from a dispenser: the dispensed plain arrow's
     * velocity scale and fixed damage plus the potion payload.
     */
    public static ProjectileSim dispensedTippedArrow(double x, double y, double z,
            double vx, double vy, double vz, int damage, ProjectileEffect effect, short itemType) {
        ProjectileSim arrow = new ProjectileSim(Kind.ARROW, x, y, z, vx, vy, vz, damage,
                null, 0, null, effect);
        arrow.payloadItemType = itemType;
        return arrow;
    }

    /** [TRIAL-GAP] 플레이어가 쏜 효과 화살. 물리는 화살과 같고 명중 효과와 아이템만 싣는다. */
    public static ProjectileSim fromPlayerTippedArrow(String nickname, double x, double y,
            double z, double vx, double vy, double vz, int damage, ProjectileEffect effect,
            short itemType) {
        ProjectileSim arrow = new ProjectileSim(Kind.ARROW, x, y, z, vx, vy, vz, damage,
                nickname, 0, null, effect);
        arrow.payloadItemType = itemType;
        // [ARROW-GROUND] 플레이어가 쏜 효과 화살도 줍기 ALLOWED 이며 줍는 스택이 곧 이 효과 화살이다.
        arrow.pickup = nickname == null ? Pickup.DISALLOWED : Pickup.ALLOWED;
        return arrow;
    }

    /** 효과 구름·불길한 아이템 소환기처럼 물리 없이 자기 tickCount 만 도는 정지 개체인가. */
    public boolean stationaryEntity() {
        return isCloud() || kind == Kind.OMINOUS_ITEM_SPAWNER;
    }

    /** 효과 구름이 대기 중인가({@code tickCount < waitTime}). */
    public boolean cloudWaiting() {
        return age < cloudWaitTime;
    }

    /** 잔류형 물약 착탄 이벤트. 효과가 없으면 null. */
    private MobEvent lingering(double cloudX, double cloudY, double cloudZ) {
        return effect == null ? null : new MobEvent.LingeringPotion(cloudX, cloudY, cloudZ,
                effect, shooterNickname, payloadItemType, potionKey);
    }

    /**
     * [TRIAL-GAP] 효과 구름 한 권위 틱(MC 2 틱) — 바닐라 {@code AreaEffectCloud.serverTick} 를
     * MC 틱마다 그대로 돈다. 적용 대상은 모아서 한 이벤트로 돌려주고 호출자가 효과를 건다.
     */
    private MobEvent tickCloud(MobWorldView world, java.util.List<Mob> mobs) {
        java.util.List<String> players = null;
        java.util.List<Long> mobIds = null;
        for (int mcTick = 0; mcTick < 2 && alive; mcTick++) {
            age++;
            if (age - cloudWaitTime >= lifetimeTicks) {
                alive = false;
                terminalReason = "expired";
                break;
            }
            if (age < cloudWaitTime) continue;
            float radius = (float) cloudRadius;
            radius += (float) cloudRadiusPerTick;
            if (radius < CLOUD_MINIMAL_RADIUS) {
                alive = false;
                terminalReason = "expired";
                break;
            }
            cloudRadius = radius;
            if (age % CLOUD_TIME_BETWEEN_APPLICATIONS != 0) continue;
            final int tickCount = age;
            cloudVictims.values().removeIf(until -> tickCount >= until);
            if (effect == null) {
                cloudVictims.clear();
                continue;
            }
            for (PlayerSnapshot p : world.players()) {
                if (!p.alive() || !cloudTouches(p.x() - PLAYER_HALF, p.y(), p.z() - PLAYER_HALF,
                        p.x() + PLAYER_HALF, p.y() + PLAYER_HEIGHT, p.z() + PLAYER_HALF)) continue;
                String key = "p:" + p.nickname();
                if (cloudVictims.containsKey(key)) continue;
                if (!cloudInRadius(p.x(), p.z(), radius)) continue;
                cloudVictims.put(key, tickCount + CLOUD_REAPPLICATION_DELAY_MC_TICKS);
                if (players == null) players = new java.util.ArrayList<>(2);
                players.add(p.nickname());
                radius += (float) cloudRadiusOnUse;
                if (radius < CLOUD_MINIMAL_RADIUS) {
                    alive = false;
                    terminalReason = "expired";
                    break;
                }
                cloudRadius = radius;
            }
            if (!alive) break;
            for (Mob mob : mobs) {
                if (mob.isDead() || mob.removed) continue;
                double half = mob.width() * 0.5;
                if (!cloudTouches(mob.x - half, mob.y, mob.z - half,
                        mob.x + half, mob.y + mob.height(), mob.z + half)) continue;
                String key = "m:" + mob.id;
                if (cloudVictims.containsKey(key)) continue;
                if (!cloudInRadius(mob.x, mob.z, radius)) continue;
                cloudVictims.put(key, tickCount + CLOUD_REAPPLICATION_DELAY_MC_TICKS);
                if (mobIds == null) mobIds = new java.util.ArrayList<>(2);
                mobIds.add(mob.id);
                radius += (float) cloudRadiusOnUse;
                if (radius < CLOUD_MINIMAL_RADIUS) {
                    alive = false;
                    terminalReason = "expired";
                    break;
                }
                cloudRadius = radius;
            }
        }
        if (players == null && mobIds == null) return null;
        return new MobEvent.CloudApplications(
                players == null ? java.util.List.of() : java.util.List.copyOf(players),
                mobIds == null ? java.util.List.of() : java.util.List.copyOf(mobIds),
                effect, shooterNickname, potionKey);
    }

    /** 구름 경계 상자 {@code (x±r, y..y+0.5, z±r)} 와 대상 상자가 겹치는가({@code AABB.intersects}). */
    private boolean cloudTouches(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double r = cloudRadius;
        return minX < x + r && maxX > x - r && minY < y + CLOUD_HEIGHT && maxY > y
                && minZ < z + r && maxZ > z - r;
    }

    /** 바닐라 수평 거리 판정 {@code dx² + dz² <= radius²}(radius 는 float). */
    private boolean cloudInRadius(double ex, double ez, float radius) {
        double dx = ex - x;
        double dz = ez - z;
        return dx * dx + dz * dz <= (double) (radius * radius);
    }

    /**
     * [TRIAL-GAP] 불길한 아이템 소환기 한 권위 틱(MC 2 틱) — 바닐라 {@code tickServer}: tickCount 가
     * {@code spawn_item_after_ticks − 36} 이면 예고음, 그 이상이면 아이템을 내보내고 사라진다.
     */
    private MobEvent tickOminousItemSpawner() {
        MobEvent event = null;
        for (int mcTick = 0; mcTick < 2 && alive; mcTick++) {
            age++;
            if (age == spawnItemAfterMcTicks - OMINOUS_ABOUT_TO_SPAWN_SOUND_MC_TICKS) {
                event = new MobEvent.OminousItemSpawnerSound(x, y, z);
            }
            if (age >= spawnItemAfterMcTicks) {
                alive = false;
                terminalReason = "expired";
                return new MobEvent.OminousItemSpawnerRelease(x, y, z, payloadItemType,
                        payloadCount);
            }
        }
        return event;
    }

    /**
     * 한 틱 진행. 플레이어 명중 시 AttackPlayer 이벤트 반환(그리고 소멸),
     * 블록/수명으로 소멸 시 alive=false 로만 표시하고 null 반환.
     */
    public MobEvent tick(MobWorldView world) {
        return tick(world, java.util.List.of());
    }

    /** 같은 선분에서 블록·플레이어·몹 중 가장 먼저 닿는 대상을 확정합니다. */
    public MobEvent tick(MobWorldView world, java.util.List<Mob> mobs) {
        if(kind!=Kind.ENDER_PEARL)return tickSingle(world,mobs);
        MobEvent event=null;
        for(int step=0;step<2 && alive && !gatewayPending;step++) {
            event=tickSingle(world,mobs);
            if(event!=null)break;
        }
        return event;
    }

    private MobEvent tickSingle(MobWorldView world, java.util.List<Mob> mobs) {
        if (!alive || gatewayPending) return null;
        stuckThisTick = false;
        // 안착·후킹된 찌는 낚시 시스템이 수명을 소유한다. 나이도 먹지 않고 물리도 멈춘다.
        if (landed || hooked()) return null;
        // [TRIAL-GAP] 효과 구름 · 불길한 아이템 소환기는 물리 없이 자기 tickCount 만 돈다.
        if (isCloud()) return tickCloud(world, mobs);
        if (kind == Kind.OMINOUS_ITEM_SPAWNER) return tickOminousItemSpawner();
        if (kind == Kind.SHULKER_BULLET) {
            age++;
            return tickShulkerBullet(world, mobs);
        }
        if (sticksInGround()) {
            // [ARROW-GROUND] AbstractArrow.tick 첫머리: 제 칸의 충돌 상자가 위치를 품으면 박힌다.
            if (!inGround && insideOwnCellCollision(world)) {
                vx = 0; vy = 0; vz = 0;
                inGround = true;
                groundBlockId = 0;
                groundBlockState = 0;
            }
            if (inGround) return tickArrowInGround(world);
        }
        if (isWindCharge() && y > Blocks.MAX_Y + 30.0) {
            alive = false;
            terminalReason = "expired";
            return null;
        }
        age++;
        if (isWindCharge() && noDeflectMcTicks > 0) {
            noDeflectMcTicks = Math.max(0, noDeflectMcTicks - 2);
        }
        // 찌의 비행 상한은 낚시 규칙이 소유한다(그 안에 착수하지 못하면 캐스팅 취소).
        int maxAge = kind == Kind.FISHING_BOBBER
                ? com.gameexpert.engine.FishingRules.MAX_FLIGHT_TICKS
                : kind == Kind.FIREWORK_ROCKET ? lifetimeTicks
                : kind == Kind.ENDER_PEARL || isWindCharge() || kind == Kind.SMALL_FIREBALL || kind == Kind.DRAGON_FIREBALL
                        ? Integer.MAX_VALUE : MAX_AGE;
        if ((kind == Kind.DRAGON_FIREBALL || kind == Kind.ENDER_PEARL)
                && y < (world.endDimension() ? 0 : Blocks.MIN_Y) - 64.0) {
            alive = false;
            terminalReason = "expired";
            return null;
        }
        if (age > maxAge) { alive = false; terminalReason = "expired"; return null; }

        double px = x, py = y, pz = z;
        previousX=x;previousY=y;previousZ=z;
        if(kind==Kind.ENDER_PEARL) {
            double drag=Fluids.isWater(world.getBlock((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)))
                    ? (double)0.8F : (double)0.99F;
            vx*=drag;vy=(vy-0.03)*drag;vz*=drag;
        }
        if (kind == Kind.FIREWORK_ROCKET) {
            // 로켓은 중력을 받지 않고 스스로 가속한다. 서버 1틱 = 바닐라 2틱이라 두 번 적용한다.
            for (int mcTick = 0; mcTick < 2; mcTick++) {
                vx *= FIREWORK_HORIZONTAL_ACCELERATION;
                vz *= FIREWORK_HORIZONTAL_ACCELERATION;
                vy += FIREWORK_UPWARD_ACCELERATION;
            }
        } else if (kind == Kind.FISHING_BOBBER) {
            // 찌는 바닐라 FishingHook 의 자기 중력을 쓴다(화살보다 가볍게 떨어진다).
            vy -= com.gameexpert.engine.FishingRules.BOBBER_GRAVITY;
        } else if (kind == Kind.SMALL_FIREBALL || kind == Kind.DRAGON_FIREBALL) {
            // [TRIAL-GAP][DRAGON] AbstractHurtingProjectile.applyInertia 를 MC 틱 둘로 돈다. v 는 권위
            // 틱당 변위(= MC 틱 속도 × 2)이고 이번 이동 선분은 두 MC 틱 변위의 합이다.
            double inertia = Fluids.isWater(world.getBlock(floor(x), floor(y), floor(z)) & 0xFFFF)
                    ? FIREBALL_LIQUID_INERTIA : FIREBALL_INERTIA;
            double mx = vx / 2.0, my = vy / 2.0, mz = vz / 2.0;
            double sumX = 0, sumY = 0, sumZ = 0;
            for (int mcTick = 0; mcTick < 2; mcTick++) {
                double length = Math.sqrt(mx * mx + my * my + mz * mz);
                if (length > 1e-12) {
                    mx += mx / length * FIREBALL_ACCELERATION;
                    my += my / length * FIREBALL_ACCELERATION;
                    mz += mz / length * FIREBALL_ACCELERATION;
                }
                mx *= inertia; my *= inertia; mz *= inertia;
                sumX += mx; sumY += my; sumZ += mz;
            }
            vx = sumX; vy = sumY; vz = sumZ;
        } else if (kind == Kind.EXPERIENCE_BOTTLE) {
            vy -= EXPERIENCE_BOTTLE_GRAVITY;
        } else if (!throwableItemPhysics(kind) && !isWindCharge() && kind != Kind.FLESH_SPIT) {
            vy -= GRAVITY;
        }
        double nx = x + vx, ny = y + vy, nz = z + vz;

        double tVoxel = voxelHitT(world, px, py, pz, nx, ny, nz);   // NaN=없음
        if (!Double.isNaN(tVoxel)) {
            ProjectileCollisionQuery hitQuery = PROJECTILE_COLLISION_QUERY.get();
            blockHitX = hitQuery.hitCellX;
            blockHitY = hitQuery.hitCellY;
            blockHitZ = hitQuery.hitCellZ;
            blockHitFace = hitQuery.hitFace;
        }

        if (kind == Kind.FISHING_BOBBER) {
            return tickBobber(world, mobs, px, py, pz, nx, ny, nz, tVoxel);
        }
        if (kind == Kind.FIREWORK_ROCKET) {
            // 로켓은 엔티티를 통과하고 블록에 닿는 순간 그 자리에서 터진다(피해 없음).
            if (!Double.isNaN(tVoxel)) {
                x = px + (nx - px) * tVoxel;
                y = py + (ny - py) * tVoxel;
                z = pz + (nz - pz) * tVoxel;
                alive = false;
                terminalReason = "block_hit";
                return null;
            }
            x = nx; y = ny; z = nz;
            return null;
        }

        PlayerSnapshot hitP = null;
        double tPlayer = Double.POSITIVE_INFINITY;
        for (PlayerSnapshot p : world.players()) {
            if (!p.alive() || p.nickname().equals(shooterNickname)) continue;
            double t = segAabbT(px, py, pz, nx, ny, nz, p);
            if (t >= 0 && t < tPlayer) { tPlayer = t; hitP = p; }
        }

        Mob hitMob = null;
        double tMob = Double.POSITIVE_INFINITY;
        for (Mob mob : mobs) {
            if (mob.id == shooterMobId || mob.isDead() || mob.removed) continue;
            // [ENCHANT-WIDE] 관통 화살은 한 번 뚫은 몹을 다시 맞히지 않는다.
            if (piercedMobIds != null && piercedMobIds.contains(mob.id)) continue;
            if (!isWindCharge() && shooterMobType != null
                    && !MobRelationshipPolicy.mayProjectileDamage(shooterMobType, mob)) {
                continue;
            }
            double t = mobHitT(px, py, pz, nx, ny, nz, mob);
            if (t >= 0 && t < tMob) { tMob = t; hitMob = mob; }
        }

        MobWorldView.PlacedProjectileHit placed = world.placedProjectileHit(px, py, pz, nx, ny, nz);
        double tPlaced = placed == null ? Double.POSITIVE_INFINITY : placed.t();
        if (enterGateway(world, px, py, pz, nx, ny, nz,
                Math.min(Math.min(tPlayer,tMob),Math.min(tPlaced,Double.isNaN(tVoxel)?1:tVoxel)))) return null;
        if (placed != null && tPlaced < tPlayer && tPlaced < tMob
                && (Double.isNaN(tVoxel) || tPlaced <= tVoxel)) {
            x = px + (nx - px) * tPlaced;
            y = py + (ny - py) * tPlaced;
            z = pz + (nz - pz) * tPlaced;
            alive = false;
            terminalReason = "placed_entity_hit";
            if (kind == Kind.DRAGON_FIREBALL) return new MobEvent.DragonFireballHit(x, y, z);
            if (kind == Kind.EXPERIENCE_BOTTLE) return experienceBottleHit(-vx, -vy, -vz);
            if (kind == Kind.SPLASH_POTION) return splash();
            if (kind == Kind.LINGERING_POTION) return lingering(x, y, z);
            boolean burning = kind == Kind.SMALL_FIREBALL || kind == Kind.ARROW
                    && weaponEnchantments.level(com.gameexpert.engine.enchant.EnchantmentRules.FLAME) > 0;
            double scale = kind == Kind.ARROW ? ARROW_SIMULATION_SCALE : 1.0;
            return new MobEvent.AttackPlacedEntity(placed.entityId(), kind, damage, burning,
                    (vx * vx + vy * vy + vz * vz) / (scale * scale), px, pz);
        }
        if (hitP != null && tPlayer <= tMob && (Double.isNaN(tVoxel) || tPlayer <= tVoxel)) {
            x = px + (nx - px) * tPlayer;
            y = py + (ny - py) * tPlayer;
            z = pz + (nz - pz) * tPlayer;
            alive = false;
            terminalReason = "player_hit";
            if (kind == Kind.DRAGON_FIREBALL) return new MobEvent.DragonFireballHit(x, y, z);
            if (kind == Kind.EXPERIENCE_BOTTLE) return experienceBottleHit(-vx, -vy, -vz);
            if (kind == Kind.SPLASH_POTION) return splash();
            if (kind == Kind.LINGERING_POTION) return lingering(hitP.x(), hitP.y(), hitP.z());
            // 충돌점이 아니라 직전 화살 위치를 넘겨 어느 방향에서 날아왔는지 보존한다.
            return new MobEvent.AttackPlayer(
                    hitP.nickname(), damage, px, pz, shooterNickname, shooterMobId,
                    shooterMobType, effect);
        }
        if (hitMob != null && (Double.isNaN(tVoxel) || tMob <= tVoxel)) {
            x = px + (nx - px) * tMob;
            y = py + (ny - py) * tMob;
            z = pz + (nz - pz) * tMob;
            int piercing = kind == Kind.ARROW
                    ? weaponEnchantments.level(
                            com.gameexpert.engine.enchant.EnchantmentRules.PIERCING) : 0;
            if (piercing > 0) {
                // [ENCHANT-WIDE] 26.3 AbstractArrow.onHitEntity: 뚫은 수가 level+1 에 이르면 다음 대상에서
                // 피해 없이 사라지고, 아니면 이 대상을 기록하고 계속 난다(명중 지점에서 다음 틱을 잇는다).
                if (piercedMobIds == null) piercedMobIds = new java.util.HashSet<>();
                if (piercedMobIds.size()
                        >= com.gameexpert.engine.enchant.EnchantmentRules.piercingMaxTargets(piercing)) {
                    alive = false;
                    terminalReason = "mob_hit";
                    targetMobId = hitMob.id;
                    return null;
                }
                piercedMobIds.add(hitMob.id);
                return new MobEvent.AttackMob(
                        hitMob.id, damage, shooterNickname, shooterMobId, shooterMobType, effect)
                        .withProjectileWeapon(weaponEnchantments, vx, vz);
            }
            alive = false;
            terminalReason = "mob_hit";
            targetMobId = hitMob.id;
            if (kind == Kind.DRAGON_FIREBALL) return new MobEvent.DragonFireballHit(x, y, z);
            if (kind == Kind.EXPERIENCE_BOTTLE) return experienceBottleHit(-vx, -vy, -vz);
            if (kind == Kind.SPLASH_POTION) return splash();
            if (kind == Kind.LINGERING_POTION) return lingering(hitMob.x, hitMob.y, hitMob.z);
            return new MobEvent.AttackMob(
                    hitMob.id, damage, shooterNickname, shooterMobId, shooterMobType, effect)
                    .withProjectileWeapon(weaponEnchantments, vx, vz);
        }
        if (!Double.isNaN(tVoxel)) {
            x = px + (nx - px) * tVoxel;
            y = py + (ny - py) * tVoxel;
            z = pz + (nz - pz) * tVoxel;
            if (sticksInGround()) {
                stickInGround(world);
                return null;
            }
            if (isWindCharge()) offsetWindBlockBurst(nx - px, ny - py, nz - pz);
            alive = false;
            terminalReason = "block_hit";
            if (kind == Kind.LINGERING_POTION) return lingering(x, y, z);
            if (kind == Kind.DRAGON_FIREBALL) return new MobEvent.DragonFireballHit(x, y, z);
            if (kind == Kind.EXPERIENCE_BOTTLE) {
                // BlockHitResult.getDirection().getUnitVec3(): the struck face's outward normal.
                return experienceBottleHit(faceNormal(blockHitFace, 0), faceNormal(blockHitFace, 1),
                        faceNormal(blockHitFace, 2));
            }
            if (kind == Kind.SMALL_FIREBALL) {
                // SmallFireball.onHitBlock: 맞은 면 바깥 칸(선분이 들어오기 직전 칸)이 비었으면 불.
                double len = Math.sqrt((nx - px) * (nx - px) + (ny - py) * (ny - py)
                        + (nz - pz) * (nz - pz));
                double back = len > 1e-12 ? 1e-4 / len : 0.0;
                return new MobEvent.SmallFireballBlockHit(
                        floor(x - (nx - px) * back), floor(y - (ny - py) * back),
                        floor(z - (nz - pz) * back));
            }
            return kind == Kind.SPLASH_POTION ? splash() : null;
        }
        x = nx; y = ny; z = nz;
        if (throwableItemPhysics(kind) && kind!=Kind.ENDER_PEARL) {
            double inertia = Fluids.isWater(world.getBlock(floor(x), floor(y), floor(z)) & 0xFFFF)
                    ? SNOWBALL_WATER_INERTIA : SNOWBALL_AIR_INERTIA;
            vx *= inertia;
            vy = vy * inertia - SNOWBALL_GRAVITY;
            vz *= inertia;
        } else if (kind == Kind.SPLASH_POTION || kind == Kind.LINGERING_POTION
                || kind == Kind.EXPERIENCE_BOTTLE) {
            double inertia = Fluids.isWater(world.getBlock(floor(x), floor(y), floor(z)) & 0xFFFF)
                    ? POTION_WATER_INERTIA : POTION_AIR_INERTIA;
            vx *= inertia;
            vy *= inertia;
            vz *= inertia;
        } else if (arrowFamilyPhysics(kind)
                && Fluids.isWater(world.getBlock(floor(x), floor(y), floor(z)) & 0xFFFF)) {
            // 물 밖에서는 곱셈 자체가 없다(공기 관성 0.99 미도입) — 지상 탄도 골든 불변.
            vx *= ARROW_WATER_INERTIA;
            vy *= ARROW_WATER_INERTIA;
            vz *= ARROW_WATER_INERTIA;
        }
        return null;
    }

    // ── [ARROW-GROUND] AbstractArrow 박힘 · 떨어짐 · 되튐 · 줍기 ───────────────────────────────

    /**
     * 블록에 박히는 투사체인가. 바닐라에서는 {@code AbstractArrow} 전부(화살·효과 화살·분광 화살·삼지창)가
     * 박히지만, 이 저장소의 플레이어 삼지창은 종결 지점의 회수물로 돌아오는 기존 계약을 지키므로
     * 화살 종류만 박힌다. 분광 화살이 새 종류로 들어오면 이 한 곳에 더한다(줍기 스택은
     * {@link #pickupItemType()} 가 싣는 아이템에서 이미 파생한다).
     */
    public boolean sticksInGround() {
        return kind == Kind.ARROW;
    }

    /** 흔들림이 끝나 줍기 판정을 받을 수 있는 박힌 화살인가({@code playerTouch} 첫 조건). */
    public boolean pickupReady() {
        return alive && sticksInGround() && inGround && shakeMcTicks <= 0;
    }

    /**
     * 줍는 스택({@code getPickupItem} = 발사에 쓴 스택 한 개의 사본). 효과 화살은 싣는 아이템이 곧
     * 효과 화살 ID 라 물약 성분이 그대로 돌아오고, 아무것도 싣지 않은 화살은 보통 화살이다.
     */
    public short pickupItemType() {
        return payloadItemType > 0 ? payloadItemType
                : com.gameexpert.engine.inventory.PlayerInventory.ARROW;
    }

    /** 바닐라 {@code EntityType.ARROW} 크기 0.5 × 0.5. 줍기 상자 교차에 쓴다. */
    public static final double ARROW_BOX_HALF_WIDTH = 0.25;
    public static final double ARROW_BOX_HEIGHT = 0.5;

    /**
     * {@code Player.aiStep} 의 접촉 상자 {@code getBoundingBox().inflate(1.0, 0.5, 1.0)} 가 이 화살의
     * 상자와 겹치는가({@code AABB.intersects}: 엄격 부등호).
     */
    public boolean touchesPlayerPickupBox(double playerX, double playerY, double playerZ) {
        double minX = playerX - PLAYER_HALF - 1.0, maxX = playerX + PLAYER_HALF + 1.0;
        double minY = playerY - 0.5, maxY = playerY + PLAYER_HEIGHT + 0.5;
        double minZ = playerZ - PLAYER_HALF - 1.0, maxZ = playerZ + PLAYER_HALF + 1.0;
        return x - ARROW_BOX_HALF_WIDTH < maxX && x + ARROW_BOX_HALF_WIDTH > minX
                && y < maxY && y + ARROW_BOX_HEIGHT > minY
                && z - ARROW_BOX_HALF_WIDTH < maxZ && z + ARROW_BOX_HALF_WIDTH > minZ;
    }

    /**
     * {@code onHitBlock}: 위치를 속도 부호 방향으로 0.05 되돌리고 박는다. 흔들림 7, 관통 0, 뚫은 몹 목록
     * 비움. 박힌 동안 속도 칸은 박힌 순간의 방향으로 남겨 두어 입장 스냅샷이 방향을 싣게 한다(물리는 멈춘다).
     */
    private void stickInGround(MobWorldView world) {
        stuckThisTick = true;
        stuckHitY = y;
        ProjectileCollisionQuery query = PROJECTILE_COLLISION_QUERY.get();
        int cellX = query.hitCellX, cellY = query.hitCellY, cellZ = query.hitCellZ;
        short block = world.getBlock(cellX, cellY, cellZ);
        int id = block < 0 ? 0 : block & 0xffff;
        groundBlockId = id;
        groundBlockState = id == 0 ? 0 : Math.max(0, world.blockState(cellX, cellY, cellZ, id));
        x -= Math.signum(vx) * ARROW_GROUND_NUDGE;
        y -= Math.signum(vy) * ARROW_GROUND_NUDGE;
        z -= Math.signum(vz) * ARROW_GROUND_NUDGE;
        inGround = true;
        shakeMcTicks = ARROW_SHAKE_MC_TICKS;
        groundLifeMcTicks = 0;
        piercedMobIds = null;
        if (weaponEnchantments.level(com.gameexpert.engine.enchant.EnchantmentRules.PIERCING) > 0) {
            weaponEnchantments = weaponEnchantments.with(
                    com.gameexpert.engine.enchant.EnchantmentRules.PIERCING, 0);
        }
    }

    /**
     * 박힌 화살의 한 권위 틱(MC 2 틱). MC 틱마다 흔들림을 줄이고, 제 칸의 블록이 박힌 블록과 다르고
     * 둘레 0.06 안에 충돌이 없으면 떨어지기 시작하며({@code startFalling}), 아니면 수명을 센다.
     */
    private MobEvent tickArrowInGround(MobWorldView world) {
        for (int mcTick = 0; mcTick < 2; mcTick++) {
            if (shakeMcTicks > 0) shakeMcTicks--;
            int cx = floor(x), cy = floor(y), cz = floor(z);
            short block = world.getBlock(cx, cy, cz);
            int id = block < 0 ? -1 : block & 0xffff;
            int state = id <= 0 ? 0 : world.blockState(cx, cy, cz, id);
            boolean sameState = id == groundBlockId && state == groundBlockState;
            if (!sameState && shouldFall(world)) {
                startFalling();
                return null;
            }
            groundLifeMcTicks++;
            if (groundLifeMcTicks >= ARROW_GROUND_LIFE_MC_TICKS) {
                alive = false;
                terminalReason = "expired";
                return null;
            }
        }
        return null;
    }

    /** 박힌 수명이 영속 체크포인트 경계를 막 지났는가(원장 revision 을 올릴 틱). */
    public boolean groundCheckpointDue() {
        return inGround && groundLifeMcTicks > 0
                && groundLifeMcTicks % ARROW_GROUND_CHECKPOINT_MC_TICKS < 2;
    }

    /** {@code startFalling}: 속도 0 × 난수(= 0), 수명 0. 흔들림·박힌 블록은 박힘과 함께 끝난다. */
    private void startFalling() {
        inGround = false;
        vx = 0; vy = 0; vz = 0;
        groundLifeMcTicks = 0;
        shakeMcTicks = 0;
        groundBlockId = 0;
        groundBlockState = 0;
    }

    /** {@code shouldFall}: {@code level.noCollision(new AABB(pos, pos).inflate(0.06))}. */
    private boolean shouldFall(MobWorldView world) {
        return !collisionOverlaps(world, x - ARROW_FALL_PROBE, y - ARROW_FALL_PROBE,
                z - ARROW_FALL_PROBE, x + ARROW_FALL_PROBE, y + ARROW_FALL_PROBE,
                z + ARROW_FALL_PROBE);
    }

    /** 제 칸 충돌 상자가 현재 위치를 품는가({@code tick} 첫머리의 {@code AABB.contains}). */
    private boolean insideOwnCellCollision(MobWorldView world) {
        int cx = floor(x), cy = floor(y), cz = floor(z);
        short block = world.getBlock(cx, cy, cz);
        if (block < 0) return false;
        boolean[] inside = {false};
        world.forBlockCollisionBoxes(cx, cy, cz,
                (x0, y0, z0, x1, y1, z1) -> {
                    // AABB.contains: min 포함, max 제외.
                    if (x >= cx + x0 && x < cx + x1 && y >= cy + y0 && y < cy + y1
                            && z >= cz + z0 && z < cz + z1) inside[0] = true;
                });
        return inside[0];
    }

    /** 상자와 엄격히 겹치는 블록 충돌 상자가 하나라도 있는가. 사용할 수 없는 칸은 막힘으로 본다. */
    private static boolean collisionOverlaps(MobWorldView world, double minX, double minY,
            double minZ, double maxX, double maxY, double maxZ) {
        boolean[] hit = {false};
        for (int bx = floor(minX); bx <= floor(maxX) && !hit[0]; bx++) {
            for (int by = floor(minY) - 1; by <= floor(maxY) && !hit[0]; by++) {
                for (int bz = floor(minZ); bz <= floor(maxZ) && !hit[0]; bz++) {
                    short block = world.getBlock(bx, by, bz);
                    if (block < 0) {
                        if (by >= floor(minY)) hit[0] = true;
                        continue;
                    }
                    final int cellX = bx, cellY = by, cellZ = bz;
                    world.forBlockCollisionBoxes(bx, by, bz, (x0, y0, z0, x1, y1, z1) -> {
                                if (cellX + x1 > minX && cellX + x0 < maxX
                                        && cellY + y1 > minY && cellY + y0 < maxY
                                        && cellZ + z1 > minZ && cellZ + z0 < maxZ) hit[0] = true;
                            });
                }
            }
        }
        return hit[0];
    }

    /**
     * 거절된 명중({@code hurtOrSimulate == false}): {@code ProjectileDeflection.REVERSE} 로 속도 × −0.5,
     * 이어 × 0.2 — 화살은 명중 지점에서 되튀어 계속 난다. MC 틱 속도² 가 {@code 1.0E-7} 미만이면
     * 그 자리에서 끝나며, 그때 줍기가 ALLOWED 면 호출자가 줍는 스택을 떨궈야 한다(반환 true).
     */
    /** Enderman rejects arrow side effects on failed hurt: keep velocity and continue through. */
    public void continueAfterEndermanHit(long mobId) {
        alive = true;
        terminalReason = null;
        targetMobId = null;
        if (piercedMobIds == null) piercedMobIds = new java.util.HashSet<>();
        piercedMobIds.add(mobId);
    }

    public boolean deflectRefusedHit() {
        vx *= ARROW_DEFLECT_SCALE;
        vy *= ARROW_DEFLECT_SCALE;
        vz *= ARROW_DEFLECT_SCALE;
        alive = true;
        terminalReason = null;
        targetMobId = null;
        double mcSpeedSqr = (vx * vx + vy * vy + vz * vz) / 4.0;
        if (mcSpeedSqr < ARROW_DEFLECT_MIN_MC_SPEED_SQR) {
            alive = false;
            terminalReason = "expired";
            return pickup == Pickup.ALLOWED;
        }
        return false;
    }

    /** AbstractWindCharge#onHitBlock offsets the burst 0.25 blocks along the hit face normal. */
    private void offsetWindBlockBurst(double dx, double dy, double dz) {
        double epsilon = 1e-9;
        if (dx != 0.0 && Math.abs(x - Math.rint(x)) <= epsilon) {
            x -= Math.signum(dx) * 0.25;
        } else if (dy != 0.0 && Math.abs(y - Math.rint(y)) <= epsilon) {
            y -= Math.signum(dy) * 0.25;
        } else if (dz != 0.0 && Math.abs(z - Math.rint(z)) <= epsilon) {
            z -= Math.signum(dz) * 0.25;
        }
    }

    /**
     * 찌 전용 한 틱. 바닐라 {@code FishingHook.tick} 처럼 이번 이동 선분에서 엔티티를 먼저
     * 보고(맞으면 후킹), 그다음 수면 착수와 블록 충돌을 본다. 물이 아닌 블록을 먼저 만나면
     * 캐스팅이 취소된다(block_hit).
     */
    private MobEvent tickBobber(MobWorldView world, java.util.List<Mob> mobs,
                                double px, double py, double pz,
                                double nx, double ny, double nz, double tVoxel) {
        // 바닐라 onHitEntity: 던진 본인은 제외하고 가장 먼저 닿는 엔티티에 걸린다.
        PlayerSnapshot hitPlayer = null;
        double tEntity = Double.POSITIVE_INFINITY;
        for (PlayerSnapshot p : world.players()) {
            if (!p.alive() || p.nickname().equals(shooterNickname)) continue;
            double t = segAabbT(px, py, pz, nx, ny, nz, p);
            if (t >= 0 && t < tEntity) { tEntity = t; hitPlayer = p; }
        }
        Mob hitMob = null;
        for (Mob mob : mobs) {
            if (mob.isDead() || mob.removed) continue;
            double t = segAabbT(px, py, pz, nx, ny, nz,
                    mob.x - mob.width() * 0.5, mob.y, mob.z - mob.width() * 0.5,
                    mob.x + mob.width() * 0.5, mob.y + mob.height(), mob.z + mob.width() * 0.5);
            if (t >= 0 && t < tEntity) { tEntity = t; hitMob = mob; hitPlayer = null; }
        }
        MobWorldView.PlacedProjectileHit placed = world.placedProjectileHit(px, py, pz, nx, ny, nz);
        boolean hitPlaced = placed != null && placed.t() >= 0 && placed.t() < tEntity;
        if (hitPlaced) tEntity = placed.t();
        if ((hitPlayer != null || hitMob != null || hitPlaced)
                && (Double.isNaN(tVoxel) || tEntity <= tVoxel)) {
            x = px + (nx - px) * tEntity;
            y = py + (ny - py) * tEntity;
            z = pz + (nz - pz) * tEntity;
            vx = 0; vy = 0; vz = 0;
            if (hitPlaced) hookedPlacedEntityId = placed.entityId();
            else if (hitMob != null) hookedMobId = hitMob.id;
            else hookedPlayer = hitPlayer.nickname();
            return null;
        }
        // 블록에 막히면 그 지점까지만 훑는다. 얕은 물이라도 수면이 먼저면 착수가 이긴다.
        double end = Double.isNaN(tVoxel) ? 1.0 : tVoxel;
        double ex = px + (nx - px) * end;
        double ey = py + (ny - py) * end;
        double ez = pz + (nz - pz) * end;
        if (landOnWater(world, py, ex, ey, ez)) {
            return null;
        }
        if (!Double.isNaN(tVoxel)) {
            x = ex; y = ey; z = ez;
            alive = false;
            terminalReason = "block_hit";
            return null;
        }
        x = nx; y = ny; z = nz;
        double inertia = com.gameexpert.engine.FishingRules.BOBBER_AIR_INERTIA;
        vx *= inertia;
        vy *= inertia;
        vz *= inertia;
        return null;
    }

    /**
     * 선분이 지나간 도착 기둥에서 가장 높은 물 칸을 찾아 그 윗면에 찌를 세운다.
     * 한 틱에 물기둥을 통째로 지나쳐도 놓치지 않도록 훑은 높이 구간 전체를 본다.
     */
    private boolean landOnWater(MobWorldView world, double startY,
                                double ex, double ey, double ez) {
        int cx = floor(ex);
        int cz = floor(ez);
        int fromY = floor(Math.max(startY, ey));
        int toY = floor(Math.min(startY, ey));
        for (int cy = fromY; cy >= toY; cy--) {
            int water = world.getBlock(cx, cy, cz) & 0xFFFF;
            if (!Fluids.isWater(water)) continue;
            x = ex;
            z = ez;
            // 찌는 보이는 수면에 뜬다. 셀 윗면(+1.0)은 원천수 기준으로 2px 공중이다.
            y = com.gameexpert.engine.FishingRules.waterSurfaceY(
                    cy, water, world.getBlock(cx, cy + 1, cz) & 0xFFFF);
            vx = 0; vy = 0; vz = 0;
            landed = true;
            return true;
        }
        return false;
    }

    /** 착탄점에서의 광역 효과 이벤트. 효과가 없는 물약은 이벤트를 내지 않는다. */
    /**
     * [CONTAINER-MENUS] {@code ThrownExperienceBottle.onHit}: {@code awardWithDirection(hit
     * location, direction, 3 + nextInt(5) + nextInt(5))}; the amount is drawn by the handler.
     */
    private MobEvent experienceBottleHit(double dx, double dy, double dz) {
        return new MobEvent.ExperienceBottleHit(x, y, z, dx, dy, dz);
    }

    /** Direction 3D data order: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east (-1 = none). */
    private static double faceNormal(int face, int axis) {
        return switch (face) {
            case 0 -> axis == 1 ? -1.0 : 0.0;
            case 1 -> axis == 1 ? 1.0 : 0.0;
            case 2 -> axis == 2 ? -1.0 : 0.0;
            case 3 -> axis == 2 ? 1.0 : 0.0;
            case 4 -> axis == 0 ? -1.0 : 0.0;
            case 5 -> axis == 0 ? 1.0 : 0.0;
            default -> 0.0;
        };
    }

    private MobEvent splash() {
        return effect == null ? null
                : new MobEvent.SplashPotion(x, y, z, effect, shooterNickname, potionKey);
    }

    // ── DDA 후보 셀 안의 실제 충돌 상자: 첫 접촉 t∈[0,1], 없으면 NaN ──
    private static double voxelHitT(MobWorldView world, double ax, double ay, double az,
                                    double bx, double by, double bz) {
        int vx = floor(ax), vy = floor(ay), vz = floor(az);
        ProjectileCollisionQuery query = PROJECTILE_COLLISION_QUERY.get();
        query.ax = ax; query.ay = ay; query.az = az;
        query.bx = bx; query.by = by; query.bz = bz;
        query.hit = Double.POSITIVE_INFINITY;
        visitProjectileNeighborCells(world, vx, vy, vz, query);

        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int stepX = sign(dx), stepY = sign(dy), stepZ = sign(dz);

        double tMaxX = boundaryT(ax, dx, stepX);
        double tMaxY = boundaryT(ay, dy, stepY);
        double tMaxZ = boundaryT(az, dz, stepZ);
        double tDeltaX = dx != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = dy != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = dz != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        while (true) {
            double t;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { vx += stepX; t = tMaxX; tMaxX += tDeltaX; }
            else if (tMaxY <= tMaxZ)              { vy += stepY; t = tMaxY; tMaxY += tDeltaY; }
            else                                  { vz += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; }
            if (t > 1.0 || t > query.hit) {
                return Double.isFinite(query.hit) ? query.hit : Double.NaN;
            }
            visitProjectileNeighborCells(world, vx, vy, vz, query);
        }
    }

    private static final ThreadLocal<ProjectileCollisionQuery> PROJECTILE_COLLISION_QUERY =
            ThreadLocal.withInitial(ProjectileCollisionQuery::new);

    /** 같은 스레드의 동기 DDA 순회가 재사용하는 콜백이다. */
    private static final class ProjectileCollisionQuery implements BuildingBlockRules.CollisionBoxVisitor {
        double ax, ay, az, bx, by, bz, hit;
        int cellX, cellY, cellZ, voxelX, voxelY, voxelZ;
        /** [BLOCK-SHAPES] 지금까지 가장 이른 명중의 주인 칸과 진입면, 마지막 blockSegAabbT 의 진입면. */
        int hitCellX, hitCellY, hitCellZ, hitFace = -1, entryFace = -1;

        @Override
        public void visit(double x0, double y0, double z0, double x1, double y1, double z1) {
            x0 += cellX; x1 += cellX;
            y0 += cellY; y1 += cellY;
            z0 += cellZ; z1 += cellZ;
            // 이웃 상자는 현재 DDA 셀 안으로 돌출한 부분이 있어야 한다. 접한 면만으로 막지 않는다.
            if (x1 <= voxelX || x0 >= voxelX + 1
                    || y1 <= voxelY || y0 >= voxelY + 1
                    || z1 <= voxelZ || z0 >= voxelZ + 1) return;
            double t = blockSegAabbT(ax, ay, az, bx, by, bz,
                    x0, y0, z0, x1, y1, z1, this);
            if (t >= 0 && t < hit) {
                hit = t;
                hitCellX = cellX; hitCellY = cellY; hitCellZ = cellZ;
                hitFace = entryFace;
            }
        }
    }

    private static void visitProjectileNeighborCells(MobWorldView world, int vx, int vy, int vz,
            ProjectileCollisionQuery query) {
        query.voxelX = vx; query.voxelY = vy; query.voxelZ = vz;
        // 선반버섯의 수평 돌출과 울타리의 1.5블록 높이는 선분이 지난 셀 밖에 주인이 있다.
        for (int x = vx - 1; x <= vx + 1; x++) {
            for (int y = vy - 1; y <= vy; y++) {
                for (int z = vz - 1; z <= vz + 1; z++) {
                    short block = world.getBlock(x, y, z);
                    if (block < 0) {
                        // 사용할 수 없는 경계 셀은 DDA가 실제로 들어간 경우에만 벽이다(정적판과 동일).
                        // 이웃 탐색으로 넓히지 않지만, 가짜 AIR처럼 통과시키지도 않는다.
                        if (x == vx && y == vy && z == vz) {
                            double t = segAabbT(query.ax, query.ay, query.az,
                                    query.bx, query.by, query.bz, x, y, z, x + 1, y + 1, z + 1);
                            if (t >= 0 && t < query.hit) {
                                query.hit = t;
                                query.hitCellX = x; query.hitCellY = y; query.hitCellZ = z;
                                query.hitFace = -1;
                            }
                        }
                        continue;
                    }
                    query.cellX = x; query.cellY = y; query.cellZ = z;
                    world.forBlockCollisionBoxes(x, y, z, query);
                }
            }
        }
    }

    /** 시작점 origin, 방향성분 d(전체 세그먼트 기준)에서 다음 복셀 경계까지의 t. */
    private static double boundaryT(double origin, double d, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double cell = Math.floor(origin);
        double next = step > 0 ? cell + 1.0 : cell;
        return (next - origin) / d;   // d 부호와 (next-origin) 부호가 일치 → 양수
    }

    /**
     * 블록 내부로 이어지는 진입만 명중이다. 면 이탈·평행·모서리 한 점 접촉은 제외한다.
     * [BLOCK-SHAPES] 진입 t 를 정한 축의 면을 {@code query.entryFace} 에 남긴다(시작점이 이미 상자 안이면 -1).
     */
    private static double blockSegAabbT(double ax, double ay, double az,
                                        double bx, double by, double bz,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ,
                                        ProjectileCollisionQuery query) {
        double entry = 0, exit = Double.POSITIVE_INFINITY;
        query.entryFace = -1;
        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? ax : axis == 1 ? ay : az;
            double delta = (axis == 0 ? bx : axis == 1 ? by : bz) - origin;
            double lo = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double hi = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (delta == 0) {
                if (origin <= lo || origin >= hi) return -1;
                continue;
            }
            double ta = (lo - origin) / delta, tb = (hi - origin) / delta;
            double near = Math.min(ta, tb);
            if (near >= entry) query.entryFace = entryFace(axis, delta);
            entry = Math.max(entry, near);
            exit = Math.min(exit, Math.max(ta, tb));
            if (entry >= exit) return -1;
        }
        // 끝점에서 진입면에 도달하는 명중도 유지하므로 exit를 1로 자르지 않는다.
        return entry <= 1 ? entry : -1;
    }

    /** 이 축을 delta 방향으로 들어가며 만나는 면(Direction 3D data). +x 로 가면 서쪽 면이다. */
    static int entryFace(int axis, double delta) {
        return switch (axis) {
            case 0 -> delta > 0 ? 4 : 5;
            case 1 -> delta > 0 ? 0 : 1;
            default -> delta > 0 ? 2 : 3;
        };
    }

    // ── 선분 vs 플레이어 AABB(슬랩): 진입 t∈[0,1] 또는 -1 ──
    private static double segAabbT(double ax, double ay, double az,
                                   double bx, double by, double bz, PlayerSnapshot p) {
        double minX = p.x() - PLAYER_HALF, maxX = p.x() + PLAYER_HALF;
        double minY = p.y(),               maxY = p.y() + PLAYER_HEIGHT;
        double minZ = p.z() - PLAYER_HALF, maxZ = p.z() + PLAYER_HALF;

        return segAabbT(ax, ay, az, bx, by, bz, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static double segAabbT(double ax, double ay, double az,
                                   double bx, double by, double bz,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double t0 = 0.0, t1 = 1.0;

        double[] r;
        r = slab(ax, dx, minX, maxX, t0, t1); if (r == null) return -1; t0 = r[0]; t1 = r[1];
        r = slab(ay, dy, minY, maxY, t0, t1); if (r == null) return -1; t0 = r[0]; t1 = r[1];
        r = slab(az, dz, minZ, maxZ, t0, t1); if (r == null) return -1; t0 = r[0]; t1 = r[1];
        return t0;
    }

    /**
     * [DRAGON] 선분이 몹에 처음 닿는 비율. 드래곤처럼 부위 개체({@code EnderDragonPart})로 맞는 몹은
     * {@link Mob#partHitBoxes} 의 가장 이른 상자를, 나머지는 몸 상자를 쓴다. 닿지 않으면 음수.
     */
    private static double mobHitT(double ax, double ay, double az, double bx, double by, double bz, Mob mob) {
        double[][] parts = mob.partHitBoxes();
        if (parts == null) {
            return segAabbT(ax, ay, az, bx, by, bz,
                    mob.x - mob.width() * 0.5, mob.y, mob.z - mob.width() * 0.5,
                    mob.x + mob.width() * 0.5, mob.y + mob.height(), mob.z + mob.width() * 0.5);
        }
        double best = -1;
        for (double[] box : parts) {
            double t = segAabbT(ax, ay, az, bx, by, bz, box[0], box[1], box[2], box[3], box[4], box[5]);
            if (t >= 0 && (best < 0 || t < best)) best = t;
        }
        return best;
    }

    private static double[] slab(double o, double d, double lo, double hi, double t0, double t1) {
        if (Math.abs(d) < 1e-12) {
            if (o < lo || o > hi) return null;      // 축에 평행하며 슬랩 밖
            return new double[]{t0, t1};
        }
        double ta = (lo - o) / d, tb = (hi - o) / d;
        if (ta > tb) { double tmp = ta; ta = tb; tb = tmp; }
        double n0 = Math.max(t0, ta), n1 = Math.min(t1, tb);
        if (n0 > n1) return null;
        return new double[]{n0, n1};
    }

    private static int floor(double v) { return (int) Math.floor(v); }
    private static int sign(double v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); }
}
