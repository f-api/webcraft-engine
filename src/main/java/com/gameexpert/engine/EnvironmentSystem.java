package com.gameexpert.engine;

import com.gameexpert.engine.sulfur.PotentSulfurRules;

import java.util.concurrent.ThreadLocalRandom;

import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.engine.crop.SweetBerryBushRules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.blocks.P4Rules;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.mob.NautilusMountRules;

import static com.gameexpert.engine.Fluids.*;

/**
 * [제공코드] 서버 권위 환경 데미지·자연 재생·사망 판정(월드당 1개, 틱 스레드 전용).
 *
 * 전투/환경 수치는 MC-REFERENCE §7을 따른다. 이동 스트림으로 재구성한 낙하(최고점−착지점−3),
 * 머리 잠김 익사(약 15초 후 초당 2점), 용암 접촉(4/0.5s)+15초 화상,
 * 불·모닥불 접촉(8초 점화·접촉 1점)+연소(초당 1점)를 적용한다.
 * 체력이 바뀐 플레이어는 {@link PlayerTickState}의 브로드캐스트 플래그로 표시되어 틱 루프 ⑤에서
 * healthUpdate/playerHurt/playerDeath로 나갑니다.
 */
public final class EnvironmentSystem {

    // MC-REFERENCE §7 상수(단위: 블록/틱. 1틱=0.1초, 10틱=1초).
    // WebCraft 조정(2026-09-12 사용자 지시): 바닐라 안전 낙하 3블록 대신 5블록. 5블록을 넘는 낙하부터 피해.
    private static final double FALL_SAFE = 5.0;
    private static final double HALF_WIDTH = 0.3;
    private static final double BODY_HEIGHT = 1.8;
    private static final int DROWN_GRACE_TICKS = 150; // 약 15초
    private static final int DROWN_INTERVAL_TICKS = 10; // 1초마다 2 = 바닐라 2 DPS
    private static final int DROWN_DAMAGE = 2;
    private static final int LAVA_INTERVAL_TICKS = 5; // 0.5초당 4
    private static final int LAVA_DAMAGE = 4;
    private static final int FIRE_MAX_TICKS = 150; // 용암 접촉 시 화상 15초
    private static final int FIRE_CONTACT_MAX_TICKS = 80; // 불/모닥불 접촉 시 화상 8초
    private static final int BURN_INTERVAL_TICKS = 10; // 화염 1초당 1
    private static final int BURN_DAMAGE = 1;
    private static final int CACTUS = 19;
    /**
     * 접촉 판정용 AABB 수축 폭. 바닐라 {@code Entity.checkInsideBlocks} 는 엔티티 AABB 를
     * {@code deflate(1.0E-7)} 한 뒤 그 상자가 걸치는 <b>셀</b>마다
     * {@code Block.entityInside} 를 부른다 — 충돌 셰이프가 아니라 셀 전체가 기준이다.
     */
    private static final double INSIDE_BLOCK_EPSILON = 1.0E-7;
    private static final int CACTUS_DAMAGE = 1; // 0.5하트; 5틱 피격 보호가 0.5초 주기를 만든다.
    private static final int CAMPFIRE_DAMAGE = 1; // MC 일반 모닥불: 10 game tick(0.5초)당 1 point.
    private static final int FIRE_CONTACT_DAMAGE = 1;
    private static final int POWDER_SNOW_FREEZE_START_TICKS = 70;
    private static final int POWDER_SNOW_DAMAGE_INTERVAL_TICKS = 20;
    private static final int POWDER_SNOW_DAMAGE = 1;
    private final TerrainAccessor accessor;
    private final BlockStateLookup states;
    private FireLookup fires;
    private final OverlayChangeListener overlayChanges;
    private final boolean qaInvulnerable;
    /** 난이도별 굶주림 하한(easy 10 / normal 1 / hard 0). Difficulty.starvationMinHealth() 공급. */
    private final int starvationMinHealth;
    /** 같은 이동 pose의 기존 낙하 추적기가 확정한 착지 관측. 호출자가 즉시 한 번 소비한다. */
    private LandingObservation latestLandingObservation;

    static final class LandingObservation {
        private final int fallDamage;
        private final int healthBefore;
        private final int healthAfter;

        private LandingObservation(int fallDamage, int healthBefore, int healthAfter) {
            this.fallDamage = fallDamage;
            this.healthBefore = healthBefore;
            this.healthAfter = healthAfter;
        }

        int fallDamage() {
            return fallDamage;
        }

        int healthBefore() {
            return healthBefore;
        }

        int healthAfter() {
            return healthAfter;
        }
    }

    LandingObservation consumeLandingObservation() {
        LandingObservation observation = latestLandingObservation;
        latestLandingObservation = null;
        return observation;
    }

    /**
     * 환경 층이 만든 블록 변형 하나를 월드에 싣는 sink. 상태 바이트까지 함께 넘기는 것은
     * [TURTLE] 알 밟기 때문이다 — 알은 개수·균열이 상태에 실려 있어 블록 종류만 넘기면
     * 남은 알 수가 사라진다. 밭 밟기처럼 상태가 없는 변형은 0 을 넘긴다.
     */
    @FunctionalInterface
    public interface OverlayChangeListener {
        void changed(int x, int y, int z, int blockType, int blockState);
    }

    public EnvironmentSystem(TerrainAccessor accessor) {
        this(accessor, (x, y, z, blockId) -> 0, (x, y, z) -> false, (x, y, z, blockType, blockState) -> { }, false);
    }

    public EnvironmentSystem(TerrainAccessor accessor, BlockStateLookup states) {
        this(accessor, states, (x, y, z) -> false, (x, y, z, blockType, blockState) -> { }, false);
    }

    public EnvironmentSystem(TerrainAccessor accessor, BlockStateLookup states, FireLookup fires) {
        this(accessor, states, fires, (x, y, z, blockType, blockState) -> { }, false);
    }

    public EnvironmentSystem(TerrainAccessor accessor, BlockStateLookup states, FireLookup fires,
            OverlayChangeListener overlayChanges) {
        this(accessor, states, fires, overlayChanges, false);
    }

    public EnvironmentSystem(TerrainAccessor accessor, BlockStateLookup states, FireLookup fires,
            OverlayChangeListener overlayChanges, boolean qaInvulnerable) {
        this(accessor, states, fires, overlayChanges, qaInvulnerable,
                HungerRules.STARVE_MIN_HEALTH_NORMAL);
    }

    public EnvironmentSystem(TerrainAccessor accessor, BlockStateLookup states, FireLookup fires,
            OverlayChangeListener overlayChanges, boolean qaInvulnerable, int starvationMinHealth) {
        this.accessor = accessor;
        this.states = states;
        this.fires = fires;
        this.overlayChanges = overlayChanges;
        this.qaInvulnerable = qaInvulnerable;
        this.starvationMinHealth = starvationMinHealth;
    }

    void setFireLookup(FireLookup fires) {
        this.fires = fires;
    }

    /** 낙하 데미지: max(0,f−5)(바닐라는 f−3, WebCraft 조정). 정수 HP에는 양의 소수 피해를 올림해 적용한다. */
    public static int fallDamage(double fallDistance) {
        return fallDistance > FALL_SAFE ? (int) Math.ceil(fallDistance - FALL_SAFE) : 0;
    }

    static String fallHurtCause(double fallDistance) {
        return fallDistance > 7.0 ? "fall_big" : "fall_small";
    }

    /**
     * 월드 틱을 모르는 옛 호출 경로. [SULFUR] 유황 간헐천은 <b>시각의 순수 함수</b>라 틱이
     * 없으면 판정할 수 없으므로 이 경로는 분출 피해를 적용하지 않는다(테스트·도구 전용).
     * 실제 서버 루프는 아래 {@link #tick(PlayerTickState, long)} 을 쓴다.
     */
    public void tick(PlayerTickState p) {
        tick(p, NO_WORLD_TICK);
    }

    /** 유황 간헐천 판정을 건너뛰는 sentinel. 음수라 어떤 실제 월드 틱과도 겹치지 않는다. */
    private static final long NO_WORLD_TICK = -1L;

    public void tick(PlayerTickState p, long worldTick) {
        tick(p, worldTick, true);
    }

    /** 개인 시계는 지형 준비와 무관하게 진행하고, 접촉 판정만 상주 지형을 기다립니다. */
    void tick(PlayerTickState p, long worldTick, boolean terrainReady) {
        if (p.isDead()) {
            return;
        }
        p.advanceHurtTick();
        if (!terrainReady) {
            p.resetFallTracking(p.y());
            p.tickHunger(qaInvulnerable, starvationMinHealth);
            return;
        }

        observeFallPose(p);
        p.finishGlideFallSample(p.y(), p.gliding() && p.airborne());
        if (p.isDead()) return;
        double px = p.x();
        double py = p.y();
        double pz = p.z();
        // [TURTLE] 등껍질 투구는 익사 게이트 **바로 앞**이다 — 정적판 StandalonePlayerVitals 도
        // applyTurtleHelmet 다음에 applyDrowning 을 돌린다. 이 순서라야 물에서 나온 그 틱에
        // 다시 걸린 수중 호흡이 같은 틱의 익사 판정에 곧바로 반영된다.
        applyTurtleHelmet(p, px, py, pz);
        applyDrown(p, px, py, pz);
        if (p.isDead()) return;
        applyLavaAndFire(p, px, py, pz);
        if (p.isDead()) return;
        applyFireContact(p, px, py, pz);
        if (p.isDead()) return;
        applyCactus(p, px, py, pz);
        if (p.isDead()) return;
        applyCampfire(p, px, py, pz);
        if (p.isDead()) return;
        applyMagmaStep(p, px, py, pz);
        if (p.isDead()) return;
        // [SULFUR] 분출 중인 유황 간헐천 기둥. 피해가 없는 밀어올림뿐이라 사인 경합은 없지만,
        // 분기 위치를 옮기면 같은 틱의 다른 환경 피해와 healthDirty 경합 순서가 달라진다.
        applySulfurGeyser(p, px, py, pz, worldTick);
        applyPotentSulfurNoxiousGas(p, px, py, pz, worldTick);
        if (p.isDead()) return;
        applyQaAwarePowderSnowFreezing(p, aabbTouchesBlock(px, py, pz, Blocks.POWDER_SNOW));
        if (p.isDead()) return;
        // 자연 회복과 굶주림은 모두 허기가 게이팅한다(SURV-H). 기존 150틱/1200틱 divergence 는 폐기했다.
        // QA 무적은 이 시스템의 피해만 없애므로 허기 소모·회복은 그대로 돌고 굶주림 피해만 빠진다.
        p.tickHunger(qaInvulnerable, starvationMinHealth);
    }

    /**
     * 이동 한 구간의 exhaustion 을 누적한다. 걷기·스프린트·수영·점프를 구분하지 않고 실제 수평
     * 이동거리에 하나의 낮은 WebCraft 비율을 적용하며, 점프에는 별도 비용을 더하지 않는다.
     *
     * @param prevX 이 구간 이전의 서버 권위 x
     * @param prevZ 이 구간 이전의 서버 권위 z
     */
    void accrueMovementExhaustion(PlayerTickState p, double prevX, double prevZ) {
        if (p.isDead()) return;
        double distance = Math.hypot(p.x() - prevX, p.z() - prevZ);
        if (!(distance > 0.0)) return;
        p.addMovementExhaustion(distance);
    }

    /**
     * 이동 메시지 한 건의 접지 전이를 즉시 관찰한다. WebSocket은 순서를 보존하지만 한 서버 틱에
     * 착지와 다음 점프가 함께 도착할 수 있으므로, 최종 pose만 보면 짧은 착지가 사라진다.
     */
    void observeFallPose(PlayerTickState p) {
        latestLandingObservation = null;
        double px = p.x();
        double py = p.y();
        double pz = p.z();
        boolean resetsFall = aabbTouchesWater(px, py, pz)
                || aabbTouchesBlock(px, py, pz, LADDER)
                || aabbTouchesBlock(px, py, pz, Blocks.VINE)
                || aabbTouchesBlock(px, py, pz, Blocks.COBWEB);
        double supportY = footprintSupportY(px, py, pz);
        boolean groundedNow = supportY != Double.NEGATIVE_INFINITY;
        // The ±0.1 contact window may observe a pose a few hundredths above the surface. Persisting
        // that sampled pose turns an exact safe 3-block descent into 3.01 and causes one false HP.
        applyFall(p, groundedNow ? supportY : py, groundedNow, resetsFall);
    }

    private void applyFall(PlayerTickState p, double py, boolean groundedNow, boolean resetsFall) {
        // 물/사다리는 그 시점까지의 거리만 즉시 지운다. 이후 다시 공중에 나오면 그 위치부터 새 낙하다.
        // [TRIAL-GAP] 느린 낙하: 바닐라 LivingEntity.aiStep 이 SLOW_FALLING 을 지닌 동안 매 틱
        // resetFallDistance() 를 부른다 — 같은 "매 틱 지우기"다.
        if (resetsFall || p.statusEffects().has(
                com.gameexpert.engine.effect.StatusEffect.SLOW_FALLING)) {
            p.resetFallTrackingFromMedium(py);
            return;
        }
        if (!groundedNow) {
            // 겉날개 활공은 바닐라 checkSlowFallDistance 로 완만한 구간의 낙하 거리를 잘라 낸다.
            // 급강하는 그대로 누적되므로 다이빙 뒤 착지는 여전히 정상 낙하 피해를 만든다.
            if (p.gliding()) {
                p.observeGlideAirborne(py);
            } else {
                p.finishGlideFallSample(py, false);
                p.observeAirborne(py);
            }
            return;
        }
        // 지면에 안착. 매 착지마다 peak를 현재 발 높이로 원자적으로 초기화한다.
        p.finishGlideFallSample(py, false);
        if (p.airborne()) {
            int healthBefore = p.health();
            // [POTION-GAP] 도약은 바닐라 calculateFallDamage 처럼 **입력 거리**에서 레벨+1
            // 블록을 뺀다(새 피해 공식이 아니다). fallHurtCause 도 보정 뒤 거리를 보므로
            // 가벼운 낙하로 내려가면 소리도 가벼운 쪽이 된다 — 바닐라와 같다.
            double fallDistance = Math.max(0.0,
                    p.fallPeakY() - py - p.statusEffects().jumpBoostFallReduction());
            int landingX = (int) Math.floor(p.x());
            int landingY = (int) Math.floor(py - 0.02);
            int landingZ = (int) Math.floor(p.z());
            int landingBlock = blockAt(landingX, landingY, landingZ);
            int landingState = states.state(
                    landingX, landingY, landingZ, landingBlock);
            if (landingBlock == Blocks.SHELF_MUSHROOM
                    && com.gameexpert.engine.blocks.P29Rules.playsBounceSound(false, false)) {
                p.recordShelfMushroomBounce(landingX, landingY, landingZ);
            }
            // [UTILITY] SlimeBlock.fallOn: 웅크리지 않았으면 causeFallDamage(distance, 0.0F) — 피해 0.
            int dmg = landingBlock == Blocks.SLIME_BLOCK && !p.crouching()
                    ? 0 : landingFallDamage(fallDistance, landingBlock, landingState);
            if (dmg > 0) {
                // [VANILLA-SOUNDS] LivingEntity.causeFallDamage 는 계산된 피해가 0 보다 크면 hurt 앞에서
                // playBlockFallSound 를 부른다: 발 아래 (floor x, floor(y − 0.2), floor z) 칸이 공기가
                // 아니면 그 SoundType 의 fall 사건을 엔티티 위치에서 낸다. 정적판 StandalonePlayerVitals 와 짝이다.
                int soundY = (int) Math.floor(py - 0.2);
                int soundBlock = soundY == landingY ? landingBlock : blockAt(landingX, soundY, landingZ);
                if (soundBlock != Blocks.AIR) {
                    p.recordBlockFallSound(p.x(), py, p.z(), soundBlock);
                }
                damage(p, dmg, fallHurtCause(fallDistance));
            }
            latestLandingObservation = new LandingObservation(
                    dmg, healthBefore, p.health());
            // Java farmland fallOn: 플레이어는 fallDistance-0.5 확률로 경작지를 흙으로 만든다.
            if (landingBlock == Blocks.FARMLAND && fallDistance > 0.5
                    && ThreadLocalRandom.current().nextDouble() < fallDistance - 0.5) {
                accessor.setOverlay(landingX, landingY, landingZ, Blocks.DIRT);
                overlayChanges.changed(landingX, landingY, landingZ, Blocks.DIRT, 0);
            } else if (landingBlock == Blocks.TURTLE_EGG) {
                // [TURTLE] [A] TurtleEggBlock.fallOn — 좀비가 아닌 개체가 위에 떨어지면
                // 1/3 로 알이 하나 부서진다. 밭 밟기와 달리 **낙하 거리를 보지 않는다**.
                // 플레이어는 canDestroyEgg 가 언제나 참이라 mobGriefing 을 보지 않는다.
                //
                // 밭 밟기와 **같은 자리**의 배타 갈래인 것은 난수 규율이다 — 한 착지가 난수를
                // 두 번 뽑지 않고, 밭도 알도 아닌 착지의 난수 소비량은 예전 그대로다.
                // client StandalonePlayerVitals 의 같은 갈래와 짝이다.
                if (TurtleEggRules.canDestroy(true, false, false)
                        && ThreadLocalRandom.current()
                                .nextInt(TurtleEggRules.FALL_BOUND) == 0) {
                    breakOneTurtleEgg(landingX, landingY, landingZ, landingState);
                }
            }
        }
        applyTurtleEggStep(p, py);
        p.resetFallTracking(py);
    }

    /**
     * [TURTLE] [A] {@code TurtleEggBlock.stepOn} — 웅크리지 않은 채 알 위를 <b>밟고 지나가면</b>
     * 1/100 로 알이 하나 부서진다. 낙하(1/3)와는 다른 갈래이고, 이 저장소에서는 접지한 틱마다
     * "딛는 칸이 바뀌었는가"로 그 순간을 잡는다(client 와 같은 옮김).
     *
     * <p>난수 소비: 딛고 선 칸이 <b>거북 알일 때만</b> 굴린다. 게이트가 난수보다 앞에 있으므로
     * 그 밖의 모든 보행은 예전과 난수 소비량이 같다.
     */
    private void applyTurtleEggStep(PlayerTickState p, double py) {
        int x = (int) Math.floor(p.x());
        int y = (int) Math.floor(py - 0.02);
        int z = (int) Math.floor(p.z());
        boolean moved = p.observeStepCell(x, y, z);
        // [A] isSteppingCarefully() — 웅크리면 밟아도 부수지 않는다.
        if (!moved || p.crouching()) return;
        int block = blockAt(x, y, z);
        if (block != Blocks.TURTLE_EGG) return;
        if (!TurtleEggRules.canDestroy(true, false, false)) return;
        if (ThreadLocalRandom.current().nextInt(TurtleEggRules.STEP_BOUND) != 0) return;
        breakOneTurtleEgg(x, y, z, states.state(x, y, z, block));
    }

    /**
     * [TURTLE] 알 하나를 부순다. 마지막 하나였으면 칸이 비고, 아니면 개수만 하나 줄어든 채
     * 균열 단계를 그대로 유지한다([A] {@code decreaseEggs}). 상태 갱신 경로는 밭 밟기와 같은
     * overlay 다.
     */
    private void breakOneTurtleEgg(int x, int y, int z, int state) {
        int decreased = TurtleEggRules.decreasedState(state);
        if (decreased < 0) {
            accessor.setOverlay(x, y, z, Blocks.AIR);
            overlayChanges.changed(x, y, z, Blocks.AIR, 0);
            return;
        }
        overlayChanges.changed(x, y, z, Blocks.TURTLE_EGG, decreased);
    }

    static int landingFallDamage(double fallDistance, int landingBlock, int landingState) {
        if (landingBlock == Blocks.POINTED_DRIPSTONE
                && (landingState & com.gameexpert.engine.blocks.P6Rules.DRIPSTONE_UP) != 0
                && (landingState & com.gameexpert.engine.blocks.P6Rules.DRIPSTONE_THICKNESS_MASK)
                        == com.gameexpert.engine.blocks.P6Rules.DRIPSTONE_TIP) {
            return fallDamage(fallDistance + 2.0) * 2;
        }
        double multiplier = landingBlock == Blocks.SHELF_MUSHROOM
                ? com.gameexpert.engine.blocks.P29Rules.FALL_DISTANCE_REDUCTION
                : landingBlock == Blocks.HONEY_BLOCK
                ? AnimalDependencyBlockRules.honeyBlockFallDamageMultiplier()
                : P4Rules.fallDamageMultiplier(landingBlock);
        return (int) Math.ceil(fallDamage(fallDistance) * multiplier);
    }

    /** 플레이어의 0.6블록 발판 면적 중 어느 셀이라도 지지되면 착지로 본다. */
    private double footprintSupportY(double px, double py, double pz) {
        final double edgeEpsilon = 1e-3;
        final double shelfOutset = 3.5 / 16.0;
        int x0 = (int) Math.floor(px - HALF_WIDTH - shelfOutset + edgeEpsilon);
        int x1 = (int) Math.floor(px + HALF_WIDTH + shelfOutset - edgeEpsilon);
        int z0 = (int) Math.floor(pz - HALF_WIDTH - shelfOutset + edgeEpsilon);
        int z1 = (int) Math.floor(pz + HALF_WIDTH + shelfOutset - edgeEpsilon);
        int by = (int) Math.floor(py - 0.06);
        double supportY = Double.NEGATIVE_INFINITY;
        for (int below = 0; below <= 1; below++) {
            int cellY = by - below;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    int block = blockAt(x, cellY, z);
                    int state = states.state(x, cellY, z, block);
                    double collisionTop = BuildingBlockRules.supportTop(block, state, x, z,
                            px - HALF_WIDTH - x, px + HALF_WIDTH - x,
                            pz - HALF_WIDTH - z, pz + HALF_WIDTH - z);
                    if (collisionTop <= 0.0) continue;
                    double top = cellY + collisionTop;
                    if (top >= py - 0.1 && top <= py + edgeEpsilon && top > supportY) {
                        supportY = top;
                    }
                }
            }
        }
        return supportY;
    }

    /**
     * [TURTLE] [A] {@code TurtleHelmetItem.turtleHelmetTick}: 거북 등껍질을 쓴 채 <b>눈이 물에
     * 잠기지 않았을 때</b> 수중 호흡을 10 초짜리(권위 틱 100)로 다시 걸어 준다. 물속에서는 다시
     * 걸지 않으므로 이미 걸린 효과가 그대로 흐르고, 물에서 나오는 순간부터 10 초가 다시 찬다.
     *
     * <p>새 효과 축을 만들지 않는다 — 이미 있는 {@link StatusEffect#WATER_BREATHING} 을 그대로
     * 쓰므로 익사 게이트({@link #applyDrown})가 이미 이 효과를 보고 있다. 눈 높이 판정도 익사와
     * <b>같은 식</b>이라 물 표면에서 두 판정이 한 틱 어긋나지 않는다.
     */
    private void applyTurtleHelmet(PlayerTickState p, double px, double py, double pz) {
        boolean wearing = p.inventory().equippedType(ArmorSlot.HELMET)
                == PlayerInventory.TURTLE_SHELL;
        boolean eyeInWater = isWaterMediumAt(
                px, py + PlayerInteractionRules.eyeHeight(p.crouching()), pz);
        if (!TurtleEggRules.helmetRefreshesWaterBreathing(wearing, eyeInWater)) return;
        p.applyStatusEffect(StatusEffect.WATER_BREATHING, 0,
                TurtleEggRules.HELMET_WATER_BREATHING_TICKS);
    }

    private void applyDrown(PlayerTickState p, double px, double py, double pz) {
        boolean headInWater = isWaterMediumAt(
                px, py + PlayerInteractionRules.eyeHeight(p.crouching()), pz);
        if (!headInWater) {
            p.setSubmergedTicks(0);
            p.setDrownAccum(0);
            return;
        }
        if (p.statusEffects().has(StatusEffect.BREATH_OF_THE_NAUTILUS)) {
            // submergedTicks stores two MC air points per authority tick. Breath restores four
            // air points per MC tick, hence four counter units across this two-MC-tick step.
            p.setSubmergedTicks(Math.max(0, p.submergedTicks()
                    - NautilusMountRules.BREATH_AIR_REFILL_PER_MC_TICK));
            p.setDrownAccum(0);
            return;
        }
        // [POTION-GAP] 수중 호흡은 화염 저항이 화염 피해를 막는 것과 **같은 꼴의 게이트**다 —
        // 새 판정 축을 만들지 않고 이미 있는 익사 누적을 멈춰 세운다. 바닐라도 산소를 아예
        // 줄이지 않으므로(숨이 차오르지 않는다) 누적기를 0 으로 되돌린다.
        // [CONDUIT] 콘딧 파워도 **같은 게이트**를 연다 — [A] MobEffectUtil.hasWaterBreathing
        // 은 water_breathing 또는 conduit_power 중 하나만 있으면 참인 불리언 게이트라
        // 새 판정 축이 아니라 조건이 하나 느는 것뿐이다(토템이 화염 저항 게이트에 합류한
        // 것과 같은 꼴). 두 효과가 겹쳐도 판정이 두 벌로 갈리지 않는다.
        if (p.statusEffects().has(StatusEffect.WATER_BREATHING)
                || p.statusEffects().has(StatusEffect.CONDUIT_POWER)) {
            p.setDrownAccum(0);
            return;
        }
        // 공기 소모를 막는 효과는 호흡 인챈트 굴림과 산소 누적보다 먼저 확인합니다.
        // [ENCHANT-WIDE] 호흡: 26.3 LivingEntity.decreaseAirSupply 는 MC 틱마다
        // nextDouble() >= 1/(level+1) 이면 공기를 줄이지 않는다. 권위 1틱은 MC 2틱이고 이 계수기 한 칸이
        // 공기 2점이므로, 두 번 굴려 실제로 줄어든 공기 점수의 반을 칸으로 쌓는다(홀수 점은 이월).
        int airUnits = respirationAirUnits(p);
        p.setSubmergedTicks(p.submergedTicks() + airUnits);
        if (p.submergedTicks() <= DROWN_GRACE_TICKS || airUnits == 0) {
            return;
        }
        p.setDrownAccum(p.drownAccum() + 1);
        if (p.drownAccum() >= DROWN_INTERVAL_TICKS) {
            p.setDrownAccum(0);
            damage(p, DROWN_DAMAGE, "drown");
        }
    }

    /** [ENCHANT-WIDE] 호흡 굴림(테스트가 고정한다). */
    private java.util.function.DoubleSupplier respirationRandom =
            () -> ThreadLocalRandom.current().nextDouble();

    void setRespirationRandomForTest(java.util.function.DoubleSupplier random) {
        this.respirationRandom = random;
    }

    /** 이번 권위 틱에 줄어드는 공기 칸(1칸 = 공기 2점). 호흡이 없으면 언제나 1 이다. */
    private int respirationAirUnits(PlayerTickState p) {
        int level = p.inventory().equippedWideEnchantments(ArmorSlot.HELMET)
                .level(EnchantmentRules.RESPIRATION);
        if (level <= 0) return 1;
        int points = p.respirationAirRemainder();
        for (int mcTick = 0; mcTick < 2; mcTick++) {
            if (!EnchantmentRules.respirationKeepsAir(level, respirationRandom.getAsDouble())) {
                points++;
            }
        }
        p.setRespirationAirRemainder(points % 2);
        return points / 2;
    }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code isInWater} 근사: 발 칸이나 몸통 칸이 물 매질이면 물에 닿았다(돌진
     * 인챈트의 {@code is_in_water: false} 요건).
     */
    boolean touchingWater(PlayerTickState p) {
        return isWaterMediumAt(p.x(), p.y(), p.z()) || isWaterMediumAt(p.x(), p.y() + 1.0, p.z());
    }

    private boolean isWaterMediumAt(double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        int block = blockAt(bx, by, bz);
        int state = states.state(bx, by, bz, block);
        // [WATERLOG] carrier 가 심은 waterlogged 칸은 바닐라에서 레벨 8 수원이라 익사·호흡 판정이
        // 물속과 같다. 엔진이 아직 상태를 쓰지 않은 칸에서만 carrier 코드를 읽는다.
        return Fluids.isWaterMedium(block, state)
                || WaterloggedStates.isWaterloggedAt(accessor, block, state, bx, by, bz);
    }

    /**
     * 용암 접촉 피해 + 화상(MC-REFERENCE §7).
     * <ul>
     *   <li>용암 접촉 중: fireTicks 를 최대치(15초)로 재설정하고 0.5초당 4 피해(화상 중복 없음).</li>
     *   <li>용암 미접촉이지만 물에 닿으면: 즉시 소화(fireTicks=0, MC 동일).</li>
     *   <li>용암 미접촉·fireTicks&gt;0: 매 틱 −1 감소, 1초당 1 화상 피해.</li>
     * </ul>
     */
    private void applyLavaAndFire(PlayerTickState p, double px, double py, double pz) {
        if (aabbTouchesLava(px, py, pz)) {
            // 노출 중 계속 최대치로 갱신. [ENCHANT-WIDE] 화염으로부터 보호가 burning_time 으로 줄인다.
            p.setFireTicks(Math.max(p.fireTicks(), burningTicks(p, FIRE_MAX_TICKS)));
            p.setFireAccum(0); // 용암 피해 우선 — 화상 중복 없음
            p.setLavaAccum(p.lavaAccum() + 1);
            if (p.lavaAccum() >= LAVA_INTERVAL_TICKS) {
                p.setLavaAccum(0);
                damage(p, LAVA_DAMAGE, "lava");
            }
            return;
        }
        p.setLavaAccum(0);
        // 물 접촉 시 즉시 소화.
        if (p.fireTicks() > 0 && aabbTouchesWater(px, py, pz)) {
            p.setFireTicks(0);
            p.setFireAccum(0);
            return;
        }
        if (p.fireTicks() <= 0) {
            p.setFireAccum(0);
            return;
        }
        p.setFireTicks(p.fireTicks() - 1);
        p.setFireAccum(p.fireAccum() + 1);
        if (p.fireAccum() >= BURN_INTERVAL_TICKS) {
            p.setFireAccum(0);
            damage(p, BURN_DAMAGE, "on_fire");
        }
    }

    /** QA invulnerability removes only this system's environmental health changes. */
    private void damage(PlayerTickState p, int amount, String cause) {
        if (!qaInvulnerable) p.damage(amount, cause);
    }

    /** 가루눈 안의 플레이어는 7초 뒤 2초마다 1 point 동상 피해를 받으며 가죽 방어구가 이를 막는다. */
    static void applyPowderSnowFreezing(PlayerTickState p, boolean touchingPowderSnow) {
        applyPowderSnowFreezing(p, touchingPowderSnow, false);
    }

    private void applyQaAwarePowderSnowFreezing(PlayerTickState p, boolean touchingPowderSnow) {
        applyPowderSnowFreezing(p, touchingPowderSnow, qaInvulnerable);
    }

    private static void applyPowderSnowFreezing(PlayerTickState p, boolean touchingPowderSnow,
            boolean qaInvulnerable) {
        if (!touchingPowderSnow || wearsLeather(p)) {
            p.setPowderSnowTicks(0);
            p.setFreezeDamageAccum(0);
            return;
        }
        p.setPowderSnowTicks(p.powderSnowTicks() + 1);
        if (p.powderSnowTicks() <= POWDER_SNOW_FREEZE_START_TICKS) return;
        p.setFreezeDamageAccum(p.freezeDamageAccum() + 1);
        if (p.freezeDamageAccum() >= POWDER_SNOW_DAMAGE_INTERVAL_TICKS) {
            p.setFreezeDamageAccum(0);
            if (!qaInvulnerable) p.damage(POWDER_SNOW_DAMAGE, "freeze");
        }
    }

    private static boolean wearsLeather(PlayerTickState p) {
        return p.inventory().equippedType(ArmorSlot.HELMET) == PlayerInventory.LEATHER_HELMET
                || p.inventory().equippedType(ArmorSlot.CHESTPLATE) == PlayerInventory.LEATHER_CHESTPLATE
                || p.inventory().equippedType(ArmorSlot.LEGGINGS) == PlayerInventory.LEATHER_LEGGINGS
                || p.inventory().equippedType(ArmorSlot.BOOTS) == PlayerInventory.LEATHER_BOOTS;
    }

    private void applyMagmaStep(PlayerTickState p, double px, double py, double pz) {
        if (p.crouching()) return;
        // MagmaBlock.stepOn hurts without ignition. Match the collision solver's surface gap,
        // not body/side overlap or the wider fall-observation contact window.
        double epsilon = 1e-3;
        int y = (int) Math.floor(py + epsilon) - 1;
        if (Math.abs(py - (y + 1.0)) > epsilon + 1e-9) return;
        for (int x = (int) Math.floor(px - HALF_WIDTH + epsilon);
                x <= (int) Math.floor(px + HALF_WIDTH - epsilon); x++) {
            for (int z = (int) Math.floor(pz - HALF_WIDTH + epsilon);
                    z <= (int) Math.floor(pz + HALF_WIDTH - epsilon); z++) {
                if (blockAt(x, y, z) == Blocks.MAGMA) {
                    // [ENCHANT-WIDE] 차가운 걸음: damage_immunity #burn_from_stepping(hot_floor).
                    if (frostWalkerImmune(p)) return;
                    damage(p, 1, "in_fire");
                    return;
                }
            }
        }
    }

    /**
     * 선인장 <b>셀</b>(1×1×1)과 겹칠 때 서버 권위 피해.
     *
     * <p>divergence 수정: 이전 판은 1/16 인셋 충돌체와의 겹침을 요구했는데, 이동이 플레이어를
     * 그 인셋 모서리 바로 앞(인셋 − 반폭 − 0.001)에서 멈추므로 옆면에 붙어 선 플레이어는
     * 영원히 판정 밖이었다. 바닐라 {@code CactusBlock.entityInside} 는
     * {@code checkInsideBlocks} 가 고른 셀에서 무조건 피해를 주므로 셀 전체로 판정한다.
     */
    private void applyCactus(PlayerTickState p, double px, double py, double pz) {
        if (aabbTouchesCactus(px, py, pz)) {
            // 매 틱 시도하되 PlayerTickState 의 기존 5틱 피격 보호가 MC의 0.5초 간격을 보장한다.
            damage(p, CACTUS_DAMAGE, "cactus");
        }
    }

    /**
     * [CROP-BERRY] 달콤한 열매 덤불 접촉 피해. [A] {@code SweetBerryBushBlock.entityInside} 는
     * 히트박스가 겹치는 것만으로는 아프지 않고 <b>그 구간에 실제로 움직였을 때만</b>
     * ({@code |Δx| >= 0.003 || |Δz| >= 0.003}) 1 피해를 준다 — 덤불에 가만히 서 있는 것은
     * 안전하다. age 0(어린 싹)도 면제다.
     *
     * <p>그래서 이 판정은 환경 틱(위치 하나만 아는 자리)이 아니라 <b>이동 한 구간</b>을 아는
     * 자리에 있다 — {@link #accrueMovementExhaustion} 과 같은 호출점을 쓴다. 피해 크기·간격은
     * 선인장과 같은 1(0.5하트) · 같은 5틱 피격 보호다.
     *
     * <p>divergence: 바닐라는 여우가 피해·감속 둘 다 면제지만 이 웨이브는 몹 파일 편집이
     * 막혀 있어 반영하지 않았다(플레이어 경로만 닫는다).
     */
    void applySweetBerryBushContact(PlayerTickState p, double prevX, double prevZ) {
        if (p.isDead()) return;
        if (!SweetBerryBushRules.movedEnoughToBeHurt(p.x() - prevX, p.z() - prevZ)) return;
        if (!aabbTouchesHurtingSweetBerryBush(p.x(), p.y(), p.z())) return;
        damage(p, SweetBerryBushRules.CONTACT_DAMAGE, "sweet_berry_bush");
    }

    /** 덤불은 풀 큐브 히트박스가 아니라 셀 전체를 차지하는 통과 가능 블록이라 셀 겹침만 본다. */
    private boolean aabbTouchesHurtingSweetBerryBush(double px, double py, double pz) {
        int minX = (int) Math.floor(px - HALF_WIDTH);
        int maxX = (int) Math.floor(px + HALF_WIDTH);
        int minY = (int) Math.floor(py);
        int maxY = (int) Math.floor(py + BODY_HEIGHT);
        int minZ = (int) Math.floor(pz - HALF_WIDTH);
        int maxZ = (int) Math.floor(pz + HALF_WIDTH);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (blockAt(x, y, z) != Blocks.SWEET_BERRY_BUSH) continue;
                    int age = SweetBerryBushRules.age(
                            states.state(x, y, z, Blocks.SWEET_BERRY_BUSH));
                    if (SweetBerryBushRules.damagesOnContact(age)) return true;
                }
            }
        }
        return false;
    }

    private boolean aabbTouchesCactus(double px, double py, double pz) {
        double playerMinX = px - HALF_WIDTH;
        double playerMaxX = px + HALF_WIDTH;
        double playerMinY = py;
        double playerMaxY = py + BODY_HEIGHT;
        double playerMinZ = pz - HALF_WIDTH;
        double playerMaxZ = pz + HALF_WIDTH;
        int x0 = (int) Math.floor(playerMinX);
        int x1 = (int) Math.floor(playerMaxX);
        int y0 = (int) Math.floor(playerMinY);
        int y1 = (int) Math.floor(playerMaxY);
        int z0 = (int) Math.floor(playerMinZ);
        int z1 = (int) Math.floor(playerMaxZ);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (blockAt(x, y, z) != CACTUS) continue;
                    if (playerMaxX - INSIDE_BLOCK_EPSILON > x
                            && playerMinX + INSIDE_BLOCK_EPSILON < x + 1.0
                            && playerMaxY - INSIDE_BLOCK_EPSILON > y
                            && playerMinY + INSIDE_BLOCK_EPSILON < y + 1.0
                            && playerMaxZ - INSIDE_BLOCK_EPSILON > z
                            && playerMinZ + INSIDE_BLOCK_EPSILON < z + 1.0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 불이 붙은 모닥불의 7/16 높이 충돌체를 밟으면 0.5초당 1 point 피해.
     *
     * <p>divergence 수정: 이전 판은 접촉과 동시에 {@code setFireTicks(FIRE_CONTACT_MAX_TICKS)} 로
     * 플레이어를 점화해, 모닥불에서 벗어난 뒤에도 화상 피해가 이어졌다. 바닐라
     * {@code CampfireBlock.entityInside} 는 {@code campfire} 피해원으로 {@code hurt} 만 부르고
     * 화상 틱을 세우지 않는다 — 접촉 피해만 남긴다.
     */
    private void applyCampfire(PlayerTickState p, double px, double py, double pz) {
        if (!aabbTouchesLitCampfire(px, py, pz)) return;
        // [ENCHANT-WIDE] 차가운 걸음: damage_immunity #burn_from_stepping(campfire).
        if (frostWalkerImmune(p)) return;
        damage(p, CAMPFIRE_DAMAGE, "in_fire");
    }

    /** [ENCHANT-WIDE] 착용 장화의 차가운 걸음이 모닥불·마그마(#burn_from_stepping) 접촉 피해를 막는가. */
    private static boolean frostWalkerImmune(PlayerTickState p) {
        return EnchantmentRules.frostWalkerImmuneToStepping(
                p.inventory().equippedWideEnchantments(ArmorSlot.BOOTS));
    }

    /**
     * [ENCHANT-WIDE] 바닐라 LivingEntity.igniteForTicks: {@code ceil(ticks × burning_time)}. 화염으로부터
     * 보호가 부위마다 {@code -0.15·level} 을 더한다(0 미만은 0).
     */
    static int burningTicks(PlayerTickState p, int ticks) {
        var inventory = p.inventory();
        return EnchantmentRules.burningTicksAfterFireProtection(ticks,
                inventory.equippedWideEnchantments(ArmorSlot.HELMET),
                inventory.equippedWideEnchantments(ArmorSlot.CHESTPLATE),
                inventory.equippedWideEnchantments(ArmorSlot.LEGGINGS),
                inventory.equippedWideEnchantments(ArmorSlot.BOOTS));
    }

    /**
     * [SULFUR] 분출 중인 유황 간헐천 기둥 접촉. <b>피해는 없다</b> — 바닐라 26.2 의 분출은
     * 엔티티를 위로 밀 뿐 어떤 피해도 주지 않는다([B] 발췌 핀 §3a). 이전 웨이브가 재사용하던
     * 모닥불 피해({@code in_fire})는 근거가 없어 걷어냈다.
     *
     * <p>밀어올림은 새 물리를 만들지 않고 <b>피해 없는 권위 넉백</b> 경로를 그대로 쓴다 —
     * {@link PlayerTickState#setHurtKnockback}(kbY 만) + {@link PlayerTickState#markKnockbackOnly}
     * 는 Ravager 방패 반동이 이미 쓰는 계약이다. 바닐라처럼 분출기 내내 매 틱 다시 실린다.
     *
     * <p><b>낙하 피해는 면제하지 않는다</b>(실사양). 그래서 여기서 낙하 추적을 건드리지
     * 않는 것 자체가 규약의 구현이다 — 발사된 플레이어는 떨어지며 평소대로 피해를 받는다.
     */
    private void applySulfurGeyser(PlayerTickState p, double px, double py, double pz,
            long worldTick) {
        if (worldTick < 0) return;
        if (!aabbTouchesEruptingGeyser(px, py, pz, worldTick)) return;
        int waterBlocks = touchingPotentWaterBlocks(px, py, pz, worldTick);
        p.setHurtKnockback(0.0,
                PotentSulfurRules.launchImpulse(waterBlocks),
                0.0, 0.0, 0.0);
        p.markKnockbackOnly("geyser", null);
    }

    /** Refreshes ambient nausea while the player intersects a valid shallow noxious-gas pool. */
    private void applyPotentSulfurNoxiousGas(PlayerTickState p, double px, double py, double pz,
            long worldTick) {
        if (worldTick < 0 || Math.floorMod(
                worldTick * StatusEffects.MC_TICKS_PER_SERVER_TICK,
                PotentSulfurRules.EFFECT_FREQUENCY_TICKS) != 0) return;
        if (!touchesPotentSulfurNoxiousWater(px, py, pz)) return;
        p.applyStatusEffectMcTicks(StatusEffect.NAUSEA, 0,
                PotentSulfurRules.NAUSEA_DURATION_TICKS);
    }

    private boolean touchesPotentSulfurNoxiousWater(double px, double py, double pz) {
        if (!aabbTouchesWater(px, py, pz)) return false;
        int playerMinY = (int) Math.floor(py + 0.01);
        int playerMaxY = (int) Math.floor(py + BODY_HEIGHT - 0.01);
        int centerX = (int) Math.floor(px);
        int centerZ = (int) Math.floor(pz);
        int radius = (int) Math.ceil(PotentSulfurRules.EFFECT_RANGE);
        double rangeSquared = PotentSulfurRules.EFFECT_RANGE * PotentSulfurRules.EFFECT_RANGE;
        for (int sx = centerX - radius; sx <= centerX + radius; sx++) {
            for (int sz = centerZ - radius; sz <= centerZ + radius; sz++) {
                double dx = sx + 0.5 - px;
                double dz = sz + 0.5 - pz;
                if (dx * dx + dz * dz > rangeSquared) continue;
                for (int sulfurY = playerMinY - PotentSulfurRules.MAX_WATER_BLOCKS_ABOVE;
                        sulfurY < playerMaxY; sulfurY++) {
                    if (blockAt(sx, sulfurY, sz) != Blocks.POTENT_SULFUR) continue;
                    int water = geyserWaterColumn(sx, sulfurY, sz);
                    if (water <= 0
                            || blockAt(sx, sulfurY + water + 1, sz) != Blocks.AIR) continue;
                    int waterMinY = sulfurY + 1;
                    int waterMaxY = sulfurY + water;
                    if (playerMaxY >= waterMinY && playerMinY <= waterMaxY) return true;
                }
            }
        }
        return false;
    }

    private boolean aabbTouchesEruptingGeyser(double px, double py, double pz, long worldTick) {
        int x0 = (int) Math.floor(px - HALF_WIDTH);
        int x1 = (int) Math.floor(px + HALF_WIDTH);
        int z0 = (int) Math.floor(pz - HALF_WIDTH);
        int z1 = (int) Math.floor(pz + HALF_WIDTH);
        int y0 = (int) Math.floor(py);
        int y1 = (int) Math.floor(py + BODY_HEIGHT);
        int maxPlume = PotentSulfurRules.maxPlumeBlocks(
                PotentSulfurRules.MAX_WATER_BLOCKS_ABOVE);
        // 몸통 칸마다 기둥을 다시 훑으면 같은 (x,z) 열의 같은 ventY 를 몸통 높이만큼 되읽는다
        // (12칸 × 20오프셋 = 240 조회, 다른 환경 판정의 20배). 열 단위로 접어 ventY 를
        // y1-1 … y0-maxPlume 한 번씩만 본다 — 판정은 그대로다: 어떤 몸통 칸 y 가 기둥 안이라는
        // 조건 1 ≤ y-ventY ≤ plume 은 "기둥 꼭대기(ventY+plume)가 발밑 y0 이상"과 같다
        // (ventY ≤ y1-1 은 루프 범위가 이미 보장한다).
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int ventY = y1 - 1; ventY >= y0 - maxPlume; ventY--) {
                    if (blockAt(x, ventY, z) != Blocks.POTENT_SULFUR) continue;
                    int column = geyserWaterColumn(x, ventY, z);
                    int plume = PotentSulfurRules.maxPlumeBlocks(column);
                    if (plume <= 0 || ventY + plume < y0) continue;
                    int below = blockAt(x, ventY - 1, z);
                    if (below == Blocks.LAVA_SOURCE
                            || below == Blocks.MAGMA && PotentSulfurRules.periodicErupting(
                                    worldTick, column, x, ventY, z)) return true;
                }
            }
        }
        return false;
    }

    /**
     * 분출구 바로 위에 쌓인 <b>물 원천</b>의 칸수(0 … {@code MAX_WATER_COLUMN}+1). 상한을 한
     * 칸 넘겨 세는 것은 "5칸 이상이면 간헐천이 아니다"를 판정하기 위해서다([B] 물기둥 1~4칸).
     */
    private int geyserWaterColumn(int x, int ventY, int z) {
        int column = 0;
        while (column < PotentSulfurRules.MAX_WATER_BLOCKS_ABOVE
                && blockAt(x, ventY + 1 + column, z) == Blocks.WATER_SOURCE) column++;
        return column;
    }

    private int touchingPotentWaterBlocks(double px, double py, double pz, long worldTick) {
        int x = (int) Math.floor(px);
        int z = (int) Math.floor(pz);
        int y1 = (int) Math.floor(py + BODY_HEIGHT);
        int maxPlume = PotentSulfurRules.maxPlumeBlocks(
                PotentSulfurRules.MAX_WATER_BLOCKS_ABOVE);
        for (int sulfurY = y1 - 1; sulfurY >= (int) Math.floor(py) - maxPlume; sulfurY--) {
            if (blockAt(x, sulfurY, z) != Blocks.POTENT_SULFUR) continue;
            int water = geyserWaterColumn(x, sulfurY, z);
            int below = blockAt(x, sulfurY - 1, z);
            if (below == Blocks.LAVA_SOURCE || below == Blocks.MAGMA
                    && PotentSulfurRules.periodicErupting(worldTick, water, x, sulfurY, z)) {
                return water;
            }
        }
        return 0;
    }

    /** 자연 화재 좌표 접촉: 모닥불과 같은 피격 보호 주기로 0.5초당 1 point, 접촉 중 점화. */
    private void applyFireContact(PlayerTickState p, double px, double py, double pz) {
        if (!aabbTouchesFire(px, py, pz)) return;
        p.setFireTicks(Math.max(p.fireTicks(), burningTicks(p, FIRE_CONTACT_MAX_TICKS)));
        p.setFireAccum(0); // 접촉 피해 우선 — 같은 틱 화상 중복 없음.
        damage(p, FIRE_CONTACT_DAMAGE, "in_fire");
    }

    private boolean aabbTouchesFire(double px, double py, double pz) {
        int x0 = (int) Math.floor(px - HALF_WIDTH);
        int x1 = (int) Math.floor(px + HALF_WIDTH);
        int z0 = (int) Math.floor(pz - HALF_WIDTH);
        int z1 = (int) Math.floor(pz + HALF_WIDTH);
        int y0 = (int) Math.floor(py + 0.01);
        int y1 = (int) Math.floor(py + BODY_HEIGHT - 0.01);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (fires.burning(x, y, z)) return true;
                }
            }
        }
        int supportY = y0 - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (fires.burning(x, supportY, z)) return true;
            }
        }
        return false;
    }

    private boolean aabbTouchesLitCampfire(double px, double py, double pz) {
        double minX = px - HALF_WIDTH, maxX = px + HALF_WIDTH;
        double minY = py, maxY = py + BODY_HEIGHT;
        double minZ = pz - HALF_WIDTH, maxZ = pz + HALF_WIDTH;
        for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) {
            for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) {
                for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
                    if (blockAt(x, y, z) != com.gameexpert.terrain.Blocks.CAMPFIRE
                            || (states.state(x, y, z, com.gameexpert.terrain.Blocks.CAMPFIRE)
                                    & BuildingBlockRules.CAMPFIRE_LIT) == 0) continue;
                    if (maxX > x && minX < x + 1.0 && maxY > y && minY < y + 7.0 / 16.0
                            && maxZ > z && minZ < z + 1.0) return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface BlockStateLookup {
        int state(int x, int y, int z, int blockId);
    }

    @FunctionalInterface
    public interface FireLookup {
        boolean burning(int x, int y, int z);
    }

    private boolean aabbTouchesLava(double px, double py, double pz) {
        int x0 = (int) Math.floor(px - HALF_WIDTH);
        int x1 = (int) Math.floor(px + HALF_WIDTH);
        int z0 = (int) Math.floor(pz - HALF_WIDTH);
        int z1 = (int) Math.floor(pz + HALF_WIDTH);
        int y0 = (int) Math.floor(py + 0.01);
        int y1 = (int) Math.floor(py + BODY_HEIGHT - 0.01);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (isLava(blockAt(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean aabbTouchesWater(double px, double py, double pz) {
        int x0 = (int) Math.floor(px - HALF_WIDTH);
        int x1 = (int) Math.floor(px + HALF_WIDTH);
        int z0 = (int) Math.floor(pz - HALF_WIDTH);
        int z1 = (int) Math.floor(pz + HALF_WIDTH);
        int y0 = (int) Math.floor(py + 0.01);
        int y1 = (int) Math.floor(py + BODY_HEIGHT - 0.01);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (isWater(blockAt(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean aabbTouchesBlock(double px, double py, double pz, int wanted) {
        int x0 = (int) Math.floor(px - HALF_WIDTH);
        int x1 = (int) Math.floor(px + HALF_WIDTH);
        int z0 = (int) Math.floor(pz - HALF_WIDTH);
        int z1 = (int) Math.floor(pz + HALF_WIDTH);
        int y0 = (int) Math.floor(py + 0.01);
        int y1 = (int) Math.floor(py + BODY_HEIGHT - 0.01);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (blockAt(x, y, z) == wanted) return true;
                }
            }
        }
        return false;
    }

    private int blockAt(double x, double y, double z) {
        return blockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private int blockAt(int x, int y, int z) {
        return WorldTickLoop.residentBlockType(accessor, x, y, z);
    }
}
