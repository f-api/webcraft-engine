package com.gameexpert.engine.mob;

import java.util.Objects;
import java.util.Optional;

/**
 * Stateless cross-authority policy for the rare Briarback and Gloamkite handlers.
 *
 * <p>The policy never creates an entity and owns no lifecycle state. A caller evaluates it once
 * for each eligible handler creation. The caller is responsible for passing a stable context
 * identity, retaining a winning decision, and enforcing the one-rebind lifecycle rule.
 */
public final class IllagerCompanionPolicy {
    public static final int POLICY_VERSION = 1;
    public static final int ROLL_DENOMINATOR = 128;
    public static final int ROLL_RESIDUE_MASK = ROLL_DENOMINATOR - 1;
    public static final int MAX_REBINDS_PER_COMPANION = 1;
    /** The threat cost of the basic raider replaced by a winning raid companion. */
    public static final int BASIC_RAIDER_THREAT = 5;

    public static final int BRIARBACK_HP = 28;
    public static final int BRIARBACK_CHARGE_TELEGRAPH_TICKS = 8;
    public static final int BRIARBACK_MAX_CHARGE_BLOCKS = 8;
    public static final int BRIARBACK_DAMAGE = 6;
    public static final double BRIARBACK_SPEED = 0.68;
    public static final int BRIARBACK_RECOVERY_TICKS = 20;
    public static final int BRIARBACK_COOLDOWN_TICKS = 80;
    public static final double BRIARBACK_KNOCKBACK = 1.8;

    public static final int GLOAMKITE_HP = 16;
    public static final int GLOAMKITE_MARK_TELEGRAPH_TICKS = 6;
    public static final int GLOAMKITE_MARK_DURATION_TICKS = 80;
    public static final int GLOAMKITE_DIVE_TELEGRAPH_TICKS = 6;
    public static final int GLOAMKITE_DAMAGE = 3;
    public static final double GLOAMKITE_DIVE_SPEED = 0.72;
    public static final int GLOAMKITE_COOLDOWN_TICKS = 80;
    public static final int GLOAMKITE_RECOVERY_TICKS = 10;

    /* SplitMix64 constants. All arithmetic in the decision path is modulo 2^64. */
    private static final long MIX_INITIAL = 0x6A09E667F3BCC909L;
    private static final long MIX_GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_MULTIPLIER_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_MULTIPLIER_B = 0x94D049BB133111EBL;
    private static final long TAG_ROLL = 0x243F6A8885A308D3L;
    private static final long TAG_APPEARANCE = 0x13198A2E03707344L;
    private static final long TAG_WORLD_SEED = 0xA4093822299F31D0L;
    private static final long TAG_CONTEXT = 0x082EFA98EC4E6C89L;
    private static final long TAG_CONTEXT_ID = 0x452821E638D01377L;
    private static final long TAG_HANDLER_ID = 0xBE5466CF34E90C6CL;
    private static final long TAG_POLICY_VERSION = 0xC0AC29B7C97C50DDL;

    /** The authority context is part of the roll domain even when identities happen to match. */
    public enum Context {
        RAID(0x72616964L),
        PATROL(0x70617472L),
        ACCEPTED_SITE(0x73697465L);

        private final long tag;

        Context(long tag) {
            this.tag = tag;
        }

        private long tag() {
            return tag;
        }
    }

    /** Handler roles that can be materialized by the current raid/patrol/site authorities. */
    public enum HandlerRole {
        PILLAGER(0),
        VINDICATOR(1),
        EVOKER(2),
        WITCH(3),
        VEX(4),
        RAVAGER(5),
        ILLUSIONER(6),
        STANDARD_BEARER(7),
        WEB_TRAPPER(8),
        BREACHER(9),
        DEMOLISHER(10),
        BUILDER(11);

        private final int code;

        HandlerRole(int code) {
            this.code = code;
        }

        private int code() {
            return code;
        }
    }

    public enum Species {
        BRIARBACK,
        GLOAMKITE
    }

    /** Complete immutable combat constants; zero-valued fields are inapplicable to that species. */
    public record CompanionStats(
            Species species,
            int maxHp,
            int chargeTelegraphTicks,
            int maxChargeBlocks,
            int damage,
            double speed,
            int recoveryTicks,
            int cooldownTicks,
            double knockback,
            int markTelegraphTicks,
            int markDurationTicks,
            int diveTelegraphTicks,
            double diveSpeed) {
        public CompanionStats {
            Objects.requireNonNull(species, "species");
        }
    }

    public static final CompanionStats BRIARBACK_STATS = new CompanionStats(
            Species.BRIARBACK,
            BRIARBACK_HP,
            BRIARBACK_CHARGE_TELEGRAPH_TICKS,
            BRIARBACK_MAX_CHARGE_BLOCKS,
            BRIARBACK_DAMAGE,
            BRIARBACK_SPEED,
            BRIARBACK_RECOVERY_TICKS,
            BRIARBACK_COOLDOWN_TICKS,
            BRIARBACK_KNOCKBACK,
            0,
            0,
            0,
            0.0);

    public static final CompanionStats GLOAMKITE_STATS = new CompanionStats(
            Species.GLOAMKITE,
            GLOAMKITE_HP,
            0,
            0,
            GLOAMKITE_DAMAGE,
            0.0,
            0,
            GLOAMKITE_COOLDOWN_TICKS,
            0.0,
            GLOAMKITE_MARK_TELEGRAPH_TICKS,
            GLOAMKITE_MARK_DURATION_TICKS,
            GLOAMKITE_DIVE_TELEGRAPH_TICKS,
            GLOAMKITE_DIVE_SPEED);

    /** The seven-bit residue is the complete decision witness for the 1/128 roll. */
    public record Roll(int residue, boolean winner, long mixedValue) {
        public Roll {
            if (residue < 0 || residue > ROLL_RESIDUE_MASK) {
                throw new IllegalArgumentException("residue=" + residue);
            }
            if (winner != (residue == 0)) {
                throw new IllegalArgumentException("winner must equal residue == 0");
            }
        }

        /** Raw unsigned 64-bit output, useful when comparing Java and TypeScript diagnostics. */
        public String mixedValueUnsigned() {
            return Long.toUnsignedString(mixedValue);
        }
    }

    /** Materialized policy facts for one eligible handler creation. */
    public record Decision(
            Context context,
            HandlerRole handlerRole,
            Species species,
            Roll roll,
            long appearanceLane,
            int raidThreatSubstitution) {
        public Decision {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(handlerRole, "handlerRole");
            Objects.requireNonNull(species, "species");
            Objects.requireNonNull(roll, "roll");
            if (raidThreatSubstitution < 0) {
                throw new IllegalArgumentException("raidThreatSubstitution");
            }
        }

        public boolean winner() {
            return roll.winner();
        }
    }

    /**
     * Read-only facts used by the later lifecycle owner to choose a rebind. The candidate list
     * supplied to {@link #nearestEligibleRebind} must already exclude dead or already-bound
     * handlers; this policy intentionally does not own runtime or persistence state.
     *
     * <p>{@code distanceSquared} is a caller-provided non-negative fixed/integer distance metric.
     * The comparator performs no floating-point work and breaks equal-distance ties by unsigned
     * handler id, then by role code.
     */
    public record RebindCandidate(
            long handlerMobId,
            Context context,
            long contextIdentity,
            HandlerRole role,
            long distanceSquared) {
        public RebindCandidate {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(role, "role");
            if (distanceSquared < 0) {
                throw new IllegalArgumentException("distanceSquared");
            }
        }

    }

    private IllagerCompanionPolicy() {}

    /** Return the immutable combat constants for one species. */
    public static CompanionStats stats(Species species) {
        Objects.requireNonNull(species, "species");
        return species == Species.BRIARBACK ? BRIARBACK_STATS : GLOAMKITE_STATS;
    }

    /** Select the compatible species; Standard Bearer uses a stable existing identity lane. */
    public static Species speciesForRole(HandlerRole role, long selector) {
        if (role == null) return null;
        return switch (role) {
            case VINDICATOR, BREACHER, DEMOLISHER, BUILDER -> Species.BRIARBACK;
            case PILLAGER, EVOKER, ILLUSIONER, WEB_TRAPPER -> Species.GLOAMKITE;
            case STANDARD_BEARER -> (selector & 1L) == 0L
                    ? Species.BRIARBACK : Species.GLOAMKITE;
            case WITCH, VEX, RAVAGER -> null;
        };
    }

    public static boolean isEligibleRole(HandlerRole role) {
        return role != null && role != HandlerRole.WITCH
                && role != HandlerRole.VEX && role != HandlerRole.RAVAGER;
    }

    public static HandlerRole roleFor(MobType type) {
        if (type == null) return null;
        return switch (type) {
            case PILLAGER -> HandlerRole.PILLAGER;
            case VINDICATOR -> HandlerRole.VINDICATOR;
            case EVOKER -> HandlerRole.EVOKER;
            case WITCH -> HandlerRole.WITCH;
            case VEX -> HandlerRole.VEX;
            case RAVAGER -> HandlerRole.RAVAGER;
            case ILLUSIONER -> HandlerRole.ILLUSIONER;
            case STANDARD_BEARER -> HandlerRole.STANDARD_BEARER;
            case WEB_TRAPPER -> HandlerRole.WEB_TRAPPER;
            case BREACHER -> HandlerRole.BREACHER;
            case DEMOLISHER -> HandlerRole.DEMOLISHER;
            case BUILDER -> HandlerRole.BUILDER;
            default -> null;
        };
    }

    /** All three accepted authority contexts share role eligibility; context identity separates rolls. */
    public static boolean isEligible(Context context, HandlerRole role) {
        return context != null && isEligibleRole(role);
    }

    /**
     * Evaluate one eligible handler creation with the current policy version. Ineligible roles
     * return {@code null}; no companion can be selected without a handler role.
     */
    public static Decision evaluate(Context context, HandlerRole role,
                                    long worldSeed, long contextIdentity, long handlerMobId) {
        return evaluate(context, role, worldSeed, contextIdentity, handlerMobId, POLICY_VERSION);
    }

    /** Evaluate one eligible handler creation with an explicitly pinned policy version. */
    public static Decision evaluate(Context context, HandlerRole role,
                                    long worldSeed, long contextIdentity, long handlerMobId,
                                    int policyVersion) {
        if (!isEligible(context, role)) return null;
        Roll result = roll(worldSeed, context, contextIdentity, handlerMobId, policyVersion);
        long appearanceLane = stableAppearanceLane(
                worldSeed, context, contextIdentity, handlerMobId, policyVersion);
        return new Decision(
                context,
                role,
                speciesForRole(role, appearanceLane),
                result,
                appearanceLane,
                raidThreatSubstitution(context, result.winner()));
    }

    /**
     * Compute the independent 1/128 roll. This method has no shared RNG or mutable state, so every
     * handler creation may call it independently and multiple handlers may win.
     */
    public static Roll roll(long worldSeed, Context context, long contextIdentity,
                            long handlerMobId) {
        return roll(worldSeed, context, contextIdentity, handlerMobId, POLICY_VERSION);
    }

    public static Roll roll(long worldSeed, Context context, long contextIdentity,
                            long handlerMobId, int policyVersion) {
        Objects.requireNonNull(context, "context");
        long mixed = identityHash(worldSeed, context, contextIdentity, handlerMobId,
                policyVersion, TAG_ROLL);
        int residue = (int) (mixed & ROLL_RESIDUE_MASK);
        return new Roll(residue, residue == 0, mixed);
    }

    public static int rollResidue(long worldSeed, Context context, long contextIdentity,
                                  long handlerMobId) {
        return roll(worldSeed, context, contextIdentity, handlerMobId).residue();
    }

    public static boolean rollWins(long worldSeed, Context context, long contextIdentity,
                                   long handlerMobId) {
        return roll(worldSeed, context, contextIdentity, handlerMobId).winner();
    }

    /** Stable, independent lane for a later appearance variant selector. */
    public static long stableAppearanceLane(long worldSeed, Context context,
                                            long contextIdentity, long handlerMobId) {
        return stableAppearanceLane(worldSeed, context, contextIdentity, handlerMobId,
                POLICY_VERSION);
    }

    public static long stableAppearanceLane(long worldSeed, Context context,
                                            long contextIdentity, long handlerMobId,
                                            int policyVersion) {
        Objects.requireNonNull(context, "context");
        return identityHash(worldSeed, context, contextIdentity, handlerMobId,
                policyVersion, TAG_APPEARANCE);
    }

    /** A winning raid companion replaces one basic-raider budget unit; it never loses its roll. */
    public static int raidThreatSubstitution(Context context, boolean winningRoll) {
        return context == Context.RAID && winningRoll ? BASIC_RAIDER_THREAT : 0;
    }

    /** Compare nearest-rebind facts without floating-point or collection-order dependence. */
    public static int compareRebindCandidates(RebindCandidate left, RebindCandidate right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        int distance = Long.compare(left.distanceSquared(), right.distanceSquared());
        if (distance != 0) return distance;
        int id = Long.compareUnsigned(left.handlerMobId(), right.handlerMobId());
        if (id != 0) return id;
        return Integer.compare(left.role().code(), right.role().code());
    }

    /**
     * Select exactly one nearest eligible candidate. Same-context and role eligibility are policy
     * facts; live/unbound filtering remains with the later runtime owner.
     */
    public static Optional<RebindCandidate> nearestEligibleRebind(
            long worldSeed, Context context, long contextIdentity, long deadHandlerMobId,
            Species companionSpecies,
            Iterable<RebindCandidate> candidates) {
        return nearestEligibleRebind(worldSeed, context, contextIdentity, deadHandlerMobId,
                companionSpecies, candidates, POLICY_VERSION);
    }

    public static Optional<RebindCandidate> nearestEligibleRebind(
            long worldSeed, Context context, long contextIdentity, long deadHandlerMobId,
            Species companionSpecies,
            Iterable<RebindCandidate> candidates,
            int policyVersion) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(companionSpecies, "companionSpecies");
        Objects.requireNonNull(candidates, "candidates");
        RebindCandidate nearest = null;
        for (RebindCandidate candidate : candidates) {
            if (candidate == null
                    || candidate.handlerMobId() == deadHandlerMobId
                    || candidate.context() != context
                    || candidate.contextIdentity() != contextIdentity
                    || !isEligibleRole(candidate.role())
                    || speciesForRole(candidate.role(), stableAppearanceLane(
                            worldSeed, context, contextIdentity, candidate.handlerMobId(),
                            policyVersion))
                            != companionSpecies) continue;
            if (nearest == null || compareRebindCandidates(candidate, nearest) < 0) {
                nearest = candidate;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static long identityHash(long worldSeed, Context context, long contextIdentity,
                                     long handlerMobId, int policyVersion, long laneTag) {
        long hash = mix64(MIX_INITIAL ^ laneTag);
        hash = mix64(hash ^ TAG_WORLD_SEED ^ worldSeed);
        hash = mix64(hash ^ TAG_CONTEXT ^ context.tag());
        hash = mix64(hash ^ TAG_CONTEXT_ID ^ contextIdentity);
        hash = mix64(hash ^ TAG_HANDLER_ID ^ handlerMobId);
        hash = mix64(hash ^ TAG_POLICY_VERSION ^ Integer.toUnsignedLong(policyVersion));
        return hash;
    }

    /**
     * Canonical unsigned SplitMix64 finalizer. Java long overflow is the required modulo-2^64
     * operation; the TypeScript mirror masks after every addition and multiplication.
     */
    private static long mix64(long value) {
        long z = value + MIX_GOLDEN_GAMMA;
        z = (z ^ (z >>> 30)) * MIX_MULTIPLIER_A;
        z = (z ^ (z >>> 27)) * MIX_MULTIPLIER_B;
        return z ^ (z >>> 31);
    }
}
