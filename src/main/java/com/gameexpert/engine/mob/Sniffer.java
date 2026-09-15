package com.gameexpert.engine.mob;

/**
 * 스니퍼(바닐라 실존 종, stableId 93). 수치 근거는 {@link MobType#SNIFFER} 의 인라인
 * 인용이다(MC Java 1.21.4 {@code EntityType.SNIFFER sized(1.9F, 1.75F)} ·
 * {@code createAttributes()} MAX_HEALTH 14 · MOVEMENT_SPEED 0.1).
 *
 * <p><b>고대 씨앗 발굴 계약</b>(바닐라 {@code Sniffer.State}): 냄새 맡기(SNIFFING) →
 * 탐색(SEARCHING) → 파기(DIGGING) → 일어나기(RISING). 파기는 160~180 MC 틱이며
 * 시작 120 MC 틱 뒤 씨앗을 요청하고,
 * 두 번의 발굴 사이에는 {@value #SEARCH_COOLDOWN_TICKS} MC 틱의 휴지가 있다. 나오는 씨앗은
 * 토치플라워 씨앗과 피처 포드 둘뿐이며 <b>확률은 50:50</b> 이다.
 *
 * <p>이 웨이브는 <b>코어 등록</b>이라 발굴 상태기계와 신규 식물 2종(토치플라워·피처 포드)의
 * 블록 등록은 다음 단계다 — 블록 ID 대역 1454~1459 를 예약해 둔다.
 */
public final class Sniffer extends AnimalMob {

    /** 파기 시작부터 씨앗 요청까지의 MC 틱. */
    public static final int DIG_DROP_DELAY_TICKS = SnifferDigRules.DIG_DROP_DELAY_MC_TICKS;
    /** 발굴 사이 휴지 MC 틱. */
    public static final int SEARCH_COOLDOWN_TICKS = SnifferDigRules.COOLDOWN_MC_TICKS;
    /** 씨앗 추첨의 배타 상한. 0=토치플라워 씨앗 · 1=피처 포드. */
    public static final int SEED_ROLL = 2;

    private SnifferDigRules.Phase digPhase = SnifferDigRules.Phase.IDLE;
    private int digCooldownMcTicks;
    private int digPhaseMcTicks;
    private int digDropDelayMcTicks;
    private int digTargetX;
    private int digTargetY;
    private int digTargetZ;
    private long digSearchEpoch;
    private long digSequence;
    private long pendingDropSequence;
    private short pendingDropItem;

    public Sniffer(long id, double x, double y, double z) {
        super(MobType.SNIFFER, id, x, y, z);
    }

    SnifferDigRules.Phase digPhase() { return digPhase; }
    int digCooldownMcTicks() { return digCooldownMcTicks; }
    int digPhaseMcTicks() { return digPhaseMcTicks; }
    int digDropDelayMcTicks() { return digDropDelayMcTicks; }
    int digTargetX() { return digTargetX; }
    int digTargetY() { return digTargetY; }
    int digTargetZ() { return digTargetZ; }
    long digSearchEpoch() { return digSearchEpoch; }
    long digSequence() { return digSequence; }
    long pendingDropSequence() { return pendingDropSequence; }
    short pendingDropItem() { return pendingDropItem; }

    boolean confirmDrop(long sequence) {
        if (pendingDropSequence == 0 || pendingDropSequence != sequence) return false;
        pendingDropSequence = 0;
        pendingDropItem = 0;
        setPersistenceRequired(true);
        return true;
    }

    void restoreDigState(String phaseName, int cooldownMcTicks, int phaseMcTicks,
            int dropDelayMcTicks,
            int targetX, int targetY, int targetZ, long searchEpoch, long sequence,
            long pendingSequence, short pendingItem) {
        SnifferDigRules.Phase restored;
        try {
            restored = phaseName == null ? SnifferDigRules.Phase.IDLE
                    : SnifferDigRules.Phase.valueOf(phaseName);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("invalid Sniffer dig phase " + phaseName, invalid);
        }
        int maximumPhaseTicks = switch (restored) {
            case IDLE -> 0;
            case SCENTING -> SnifferDigRules.SCENTING_MAX_MC_TICKS;
            case SNIFFING -> SnifferDigRules.SNIFFING_MAX_MC_TICKS;
            case SEARCHING -> SnifferDigRules.SEARCH_MAX_MC_TICKS;
            case DIGGING -> SnifferDigRules.DIGGING_MAX_MC_TICKS;
            case RISING -> SnifferDigRules.RISING_MC_TICKS;
        };
        if (cooldownMcTicks < 0 || cooldownMcTicks > SnifferDigRules.COOLDOWN_MC_TICKS
                || phaseMcTicks < 0 || phaseMcTicks > maximumPhaseTicks || dropDelayMcTicks < 0
                || dropDelayMcTicks > SnifferDigRules.DIG_DROP_DELAY_MC_TICKS
                || sequence < 0 || searchEpoch < 0 || pendingSequence < 0
                || (pendingSequence == 0) != (pendingItem == 0)
                || (pendingItem != 0
                    && pendingItem != com.gameexpert.engine.inventory.PlayerInventory.TORCHFLOWER_SEEDS
                    && pendingItem != com.gameexpert.engine.inventory.PlayerInventory.PITCHER_POD)) {
            throw new IllegalArgumentException("invalid persisted Sniffer dig state");
        }
        digPhase = restored;
        digCooldownMcTicks = cooldownMcTicks;
        digPhaseMcTicks = phaseMcTicks;
        digDropDelayMcTicks = dropDelayMcTicks;
        digTargetX = targetX;
        digTargetY = targetY;
        digTargetZ = targetZ;
        digSearchEpoch = searchEpoch;
        digSequence = sequence;
        pendingDropSequence = pendingSequence;
        pendingDropItem = pendingItem;
    }

    private boolean digForbidden(MobWorldView world) {
        int block = world.getBlock((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.floor(z)) & 0xffff;
        return isBaby() || fleeTimer > 0 || isRidingBoat() || isMobPassenger() || !onGround
                || com.gameexpert.engine.Fluids.isWaterMedium(block);
    }

    private boolean targetStillDiggable(MobWorldView world) {
        return SnifferDigRules.diggable(world.getBlock(digTargetX, digTargetY, digTargetZ) & 0xffff)
                && !world.isSolid(world.getBlock(digTargetX, digTargetY + 1, digTargetZ));
    }

    private boolean findTarget(MobWorldView world) {
        long epoch = ++digSearchEpoch;
        int originX = (int) Math.floor(x), originY = (int) Math.floor(y), originZ = (int) Math.floor(z);
        for (int attempt = 0; attempt < SnifferDigRules.SEARCH_ATTEMPTS; attempt++) {
            int radius = SnifferDigRules.SEARCH_BASE_RADIUS + 2 * attempt;
            int candidateX = originX + SnifferDigRules.offset(id, epoch, attempt, 1, radius);
            int candidateY = originY + SnifferDigRules.offset(id, epoch, attempt, 2,
                    SnifferDigRules.SEARCH_VERTICAL_RADIUS) - 1;
            int candidateZ = originZ + SnifferDigRules.offset(id, epoch, attempt, 3, radius);
            if (!SnifferDigRules.diggable(world.getBlock(candidateX, candidateY, candidateZ) & 0xffff)
                    || world.isSolid(world.getBlock(candidateX, candidateY + 1, candidateZ))) continue;
            digTargetX = candidateX;
            digTargetY = candidateY;
            digTargetZ = candidateZ;
            digPhase = SnifferDigRules.Phase.SEARCHING;
            digPhaseMcTicks = SnifferDigRules.SEARCH_MAX_MC_TICKS;
            setPersistenceRequired(true);
            return true;
        }
        return false;
    }

    private void abortDig() {
        digPhase = SnifferDigRules.Phase.IDLE;
        digPhaseMcTicks = 0;
        digDropDelayMcTicks = 0;
        setPersistenceRequired(true);
    }

    @Override
    public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return java.util.List.of();
        if (digCooldownMcTicks > 0) digCooldownMcTicks = SnifferDigRules.advance(digCooldownMcTicks);
        if (digForbidden(world)) {
            if (digPhase != SnifferDigRules.Phase.IDLE) abortDig();
            return super.tick(world, rng);
        }
        if (digPhase == SnifferDigRules.Phase.IDLE) {
            if (digCooldownMcTicks > 0) return super.tick(world, rng);
            digPhase = SnifferDigRules.Phase.SCENTING;
            digPhaseMcTicks = SnifferDigRules.sampleInclusive(rng,
                    SnifferDigRules.SCENTING_MIN_MC_TICKS,
                    SnifferDigRules.SCENTING_MAX_MC_TICKS);
            setPersistenceRequired(true);
        }
        if (digPhase == SnifferDigRules.Phase.SCENTING
                || digPhase == SnifferDigRules.Phase.SNIFFING) {
            state = MobState.IDLE;
            digPhaseMcTicks = SnifferDigRules.advance(digPhaseMcTicks);
            if (digPhaseMcTicks > 0) return java.util.List.of();
            if (digPhase == SnifferDigRules.Phase.SCENTING) {
                digPhase = SnifferDigRules.Phase.SNIFFING;
                digPhaseMcTicks = SnifferDigRules.sampleInclusive(rng,
                        SnifferDigRules.SNIFFING_MIN_MC_TICKS,
                        SnifferDigRules.SNIFFING_MAX_MC_TICKS);
            } else if (!findTarget(world)) {
                abortDig();
            }
            return java.util.List.of();
        }
        if (digPhase == SnifferDigRules.Phase.SEARCHING) {
            if (!targetStillDiggable(world)) {
                abortDig();
                return super.tick(world, rng);
            }
            double targetX = digTargetX + 0.5, targetZ = digTargetZ + 0.5;
            double dx = targetX - x, dz = targetZ - z;
            double distance = Math.hypot(dx, dz);
            if (distance > SnifferDigRules.TARGET_REACHED_DISTANCE) {
                digPhaseMcTicks = SnifferDigRules.advance(digPhaseMcTicks);
                if (digPhaseMcTicks == 0) {
                    abortDig();
                    return super.tick(world, rng);
                }
                double speed = type.baseSpeed() * SnifferDigRules.SEARCH_SPEED_MODIFIER * 2;
                double scale = Math.min(speed, distance) / distance;
                yaw = Math.atan2(dz, dx);
                state = MobState.WANDER;
                MobPhysics.tickMove(this, world, dx * scale, 0, dz * scale, MoveMode.WALK);
                return java.util.List.of();
            }
            digPhase = SnifferDigRules.Phase.DIGGING;
            digPhaseMcTicks = SnifferDigRules.sampleInclusive(rng,
                    SnifferDigRules.DIGGING_MIN_MC_TICKS,
                    SnifferDigRules.DIGGING_MAX_MC_TICKS);
            digDropDelayMcTicks = SnifferDigRules.DIG_DROP_DELAY_MC_TICKS;
        }
        state = MobState.IDLE;
        if (digPhase == SnifferDigRules.Phase.DIGGING && !targetStillDiggable(world)) {
            abortDig();
            return java.util.List.of();
        }
        digPhaseMcTicks = SnifferDigRules.advance(digPhaseMcTicks);
        if (digPhase == SnifferDigRules.Phase.DIGGING && digDropDelayMcTicks > 0) {
            digDropDelayMcTicks = SnifferDigRules.advance(digDropDelayMcTicks);
            if (digDropDelayMcTicks == 0 && pendingDropSequence == 0) {
                digSequence++;
                pendingDropSequence = digSequence;
                pendingDropItem = rng.nextInt(SEED_ROLL) == 0
                        ? com.gameexpert.engine.inventory.PlayerInventory.TORCHFLOWER_SEEDS
                        : com.gameexpert.engine.inventory.PlayerInventory.PITCHER_POD;
                setPersistenceRequired(true);
            }
        }
        if (digPhaseMcTicks > 0) return java.util.List.of();
        if (digPhase == SnifferDigRules.Phase.DIGGING) {
            digCooldownMcTicks = SnifferDigRules.COOLDOWN_MC_TICKS;
            digPhase = SnifferDigRules.Phase.RISING;
            digPhaseMcTicks = SnifferDigRules.RISING_MC_TICKS;
            setPersistenceRequired(true);
        } else if (digPhase == SnifferDigRules.Phase.RISING) {
            digPhase = SnifferDigRules.Phase.IDLE;
        }
        return java.util.List.of();
    }
}
