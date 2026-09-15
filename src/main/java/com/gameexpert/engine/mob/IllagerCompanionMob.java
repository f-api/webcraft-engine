package com.gameexpert.engine.mob;

/** Durable handler binding shared by WebCraft's two illager companion species. */
abstract class IllagerCompanionMob extends Mob {
    static final int ORPHAN_CLEANUP_TICKS = 40;

    enum AbilityPhase {
        IDLE,
        PRIMARY_TELEGRAPH,
        SECONDARY_TELEGRAPH,
        ACTIVE,
        RECOVERY
    }

    record CompanionEncounterState(
            String abilityPhase,
            int abilityTicks,
            int cooldownTicks,
            String targetNickname,
            int markTicks,
            double chargeDistance,
            int orphanTicks) {}

    private long handlerMobId;
    private long appearanceLane;
    private int rebindCount;
    private transient Mob preparedHandler;
    protected AbilityPhase companionAbilityPhase = AbilityPhase.IDLE;
    protected int companionAbilityTicks;
    protected int companionCooldownTicks;
    protected String companionTargetNickname;
    protected int companionMarkTicks;
    protected double companionChargeDistance;
    private int companionOrphanTicks;

    IllagerCompanionMob(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }

    final void bindHandler(long handlerMobId, IllagerCompanionPolicy.Context context,
            long contextIdentity, long appearanceLane, int policyVersion, int rebindCount) {
        if (handlerMobId <= 0 || context == null || policyVersion <= 0
                || rebindCount < 0
                || rebindCount > IllagerCompanionPolicy.MAX_REBINDS_PER_COMPANION) {
            throw new IllegalArgumentException("invalid illager companion binding");
        }
        this.handlerMobId = handlerMobId;
        this.appearanceLane = appearanceLane;
        this.rebindCount = rebindCount;
        assignIllagerContext(context, contextIdentity, policyVersion);
        setPersistenceRequired(true);
    }

    final long handlerMobId() { return handlerMobId; }
    final IllagerCompanionPolicy.Context companionContext() { return illagerContext(); }
    final long contextIdentity() { return illagerContextIdentity(); }
    final long appearanceLane() { return appearanceLane; }
    final int policyVersion() { return illagerPolicyVersion(); }
    final int rebindCount() { return rebindCount; }
    final boolean mayRebind() {
        return rebindCount < IllagerCompanionPolicy.MAX_REBINDS_PER_COMPANION;
    }

    final void prepareHandler(Mob handler) {
        preparedHandler = handler != null && !handler.isDead() && !handler.removed
                ? handler : null;
    }

    final Mob preparedHandler() { return preparedHandler; }

    final CompanionEncounterState encounterState() {
        return new CompanionEncounterState(
                companionAbilityPhase.name(), companionAbilityTicks,
                companionCooldownTicks, companionTargetNickname,
                companionMarkTicks, companionChargeDistance, companionOrphanTicks);
    }

    final void restoreEncounterState(CompanionEncounterState state) {
        if (state == null) throw new IllegalArgumentException("missing companion encounter state");
        AbilityPhase phase;
        try {
            phase = AbilityPhase.valueOf(state.abilityPhase());
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid companion ability phase", invalid);
        }
        validateEncounterState(type, phase, state.abilityTicks(), state.cooldownTicks(),
                state.targetNickname(), state.markTicks(), state.chargeDistance(),
                state.orphanTicks());
        companionAbilityPhase = phase;
        companionAbilityTicks = state.abilityTicks();
        companionCooldownTicks = state.cooldownTicks();
        companionTargetNickname = state.targetNickname();
        companionMarkTicks = state.markTicks();
        companionChargeDistance = state.chargeDistance();
        companionOrphanTicks = state.orphanTicks();
        syncPresentationState();
    }

    final int orphanTicks() { return companionOrphanTicks; }

    final void clearOrphanTicks() { companionOrphanTicks = 0; }

    /** Returns true on the cleanup tick; 40 is never persisted as a live state. */
    final boolean advanceOrphanTick() {
        if (companionOrphanTicks >= ORPHAN_CLEANUP_TICKS - 1) return true;
        companionOrphanTicks++;
        return false;
    }

    protected final void beginCompanionPhase(AbilityPhase phase, int ticks,
            String targetNickname) {
        if (phase == AbilityPhase.IDLE || phase == AbilityPhase.ACTIVE
                || ticks <= 0
                || phase != AbilityPhase.RECOVERY
                && (targetNickname == null || targetNickname.isBlank())) {
            throw new IllegalArgumentException("invalid companion timed phase");
        }
        companionAbilityPhase = phase;
        companionAbilityTicks = ticks;
        companionTargetNickname = phase == AbilityPhase.RECOVERY ? null : targetNickname;
        if (phase == AbilityPhase.RECOVERY) {
            companionMarkTicks = 0;
            companionChargeDistance = 0.0;
        }
        syncPresentationState();
    }

    protected final void beginCompanionActive() {
        companionAbilityPhase = AbilityPhase.ACTIVE;
        companionAbilityTicks = 0;
        syncPresentationState();
    }

    protected final void clearCompanionAbility() {
        companionAbilityPhase = AbilityPhase.IDLE;
        companionAbilityTicks = 0;
        companionTargetNickname = null;
        companionMarkTicks = 0;
        companionChargeDistance = 0.0;
        syncPresentationState();
    }

    private void syncPresentationState() {
        switch (companionAbilityPhase) {
            case IDLE -> synchronizeVisualAction("none", "idle", 0);
            case PRIMARY_TELEGRAPH -> synchronizeVisualAction(
                    type == MobType.GLOAMKITE ? "ranged_charge" : "charge",
                    "anticipation", companionAbilityTicks);
            case SECONDARY_TELEGRAPH -> synchronizeVisualAction(
                    "charge", "anticipation", companionAbilityTicks);
            case ACTIVE -> synchronizeVisualAction("charge", "active", 0);
            case RECOVERY -> synchronizeVisualAction(
                    "charge", "recovery", companionAbilityTicks);
        }
    }

    protected final void synchronizeCompanionPresentation() {
        syncPresentationState();
    }

    private static void validateEncounterState(MobType type, AbilityPhase phase,
            int abilityTicks, int cooldownTicks, String targetNickname, int markTicks,
            double chargeDistance, int orphanTicks) {
        if (abilityTicks < 0 || cooldownTicks < 0 || markTicks < 0
                || !Double.isFinite(chargeDistance) || chargeDistance < 0.0
                || orphanTicks < 0 || orphanTicks >= ORPHAN_CLEANUP_TICKS) {
            throw new IllegalArgumentException("invalid companion counters");
        }
        boolean targetPresent = targetNickname != null && !targetNickname.isBlank();
        if (phase == AbilityPhase.IDLE) {
            if (abilityTicks != 0 || targetPresent || markTicks != 0 || chargeDistance != 0.0) {
                throw new IllegalArgumentException("invalid idle companion state");
            }
            return;
        }
        if (phase == AbilityPhase.RECOVERY) {
            if (abilityTicks <= 0 || targetPresent || markTicks != 0 || chargeDistance != 0.0) {
                throw new IllegalArgumentException("invalid companion recovery state");
            }
            return;
        }
        if (!targetPresent || cooldownTicks != 0) {
            throw new IllegalArgumentException("active companion state requires one unlocked target");
        }
        if (type == MobType.BRIARBACK) {
            if (phase == AbilityPhase.SECONDARY_TELEGRAPH || markTicks != 0
                    || phase == AbilityPhase.PRIMARY_TELEGRAPH
                    && (abilityTicks < 1
                    || abilityTicks > IllagerCompanionPolicy.BRIARBACK_CHARGE_TELEGRAPH_TICKS
                    || chargeDistance != 0.0)
                    || phase == AbilityPhase.ACTIVE
                    && (abilityTicks != 0
                    || chargeDistance >= IllagerCompanionPolicy.BRIARBACK_MAX_CHARGE_BLOCKS)) {
                throw new IllegalArgumentException("invalid Briarback encounter state");
            }
            return;
        }
        if (type != MobType.GLOAMKITE || chargeDistance != 0.0) {
            throw new IllegalArgumentException("invalid companion species state");
        }
        boolean valid = switch (phase) {
            case PRIMARY_TELEGRAPH -> abilityTicks >= 1
                    && abilityTicks <= IllagerCompanionPolicy.GLOAMKITE_MARK_TELEGRAPH_TICKS
                    && markTicks == 0;
            case SECONDARY_TELEGRAPH -> abilityTicks >= 1
                    && abilityTicks <= IllagerCompanionPolicy.GLOAMKITE_DIVE_TELEGRAPH_TICKS
                    && markTicks >= 1
                    && markTicks <= IllagerCompanionPolicy.GLOAMKITE_MARK_DURATION_TICKS;
            case ACTIVE -> abilityTicks == 0 && markTicks >= 1
                    && markTicks <= IllagerCompanionPolicy.GLOAMKITE_MARK_DURATION_TICKS;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("invalid Gloamkite encounter state");
    }

    final void commitRebind(long nextHandlerMobId) {
        if (!mayRebind()) throw new IllegalStateException("companion rebind exhausted");
        handlerMobId = nextHandlerMobId;
        rebindCount++;
        companionOrphanTicks = 0;
    }
}
