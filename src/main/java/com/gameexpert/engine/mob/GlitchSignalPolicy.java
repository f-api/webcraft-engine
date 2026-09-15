package com.gameexpert.engine.mob;

/**
 * WebCraft-specific glitch signal enderman policy (project contract, not a vanilla rule).
 *
 * <p>Both authorities decide the same facts from the same immutable structure identity:
 * <ul>
 *   <li>count: one signal enderman with 90% probability, two with 10%, rolled once per glitch
 *       center from {@code (worldSeed, siteKey)};</li>
 *   <li>placement candidates: integer offsets rejected outside the horizontal annulus
 *       [{@value #MIN_RADIUS}, {@value #MAX_RADIUS}] blocks around the glitch center, so no
 *       floating-point trigonometry can diverge between Java and TypeScript.</li>
 * </ul>
 *
 * <p>Ownership and persistence live with the caller: the mob authority owns the lane, evaluates
 * one accepted glitch site per tick, and records a single durable claim per center.
 */
public final class GlitchSignalPolicy {
    /** Inclusive minimum horizontal distance from the glitch center. */
    public static final int MIN_RADIUS = 64;
    /** Inclusive maximum horizontal distance from the glitch center. */
    public static final int MAX_RADIUS = 96;
    /** Deterministic candidate offsets evaluated per signal slot before the slot is abandoned. */
    public static final int CANDIDATES = 24;
    /** Durable claim lane name; distinct from every structure kind name. */
    public static final String CLAIM_KIND = "GLITCH_SIGNAL";
    /** Durable claim lane code for the standalone numeric claim key. */
    public static final long CLAIM_KIND_CODE = 0x1000_0000L;
    /** Bumping this invalidates nothing already claimed; it only labels new decisions. */
    public static final int POLICY_VERSION = 1;

    private static final int COUNT_SALT = 0x5e9d1a37;
    private static final int OFFSET_SALT = 0x2f1b3c5d;
    private static final int SPAN = MAX_RADIUS * 2 + 1;

    private GlitchSignalPolicy() {
    }

    /** 90% one, 10% two, decided once per glitch center. */
    public static int signalCount(int worldSeed, long siteKey) {
        int state = mix(worldSeed ^ (int) siteKey ^ COUNT_SALT);
        state = mix(state + 0x6d2b79f5);
        return Integer.remainderUnsigned(state, 1_000) < 900 ? 1 : 2;
    }

    /**
     * Deterministic horizontal offset for one candidate, or {@code null} when the sampled square
     * offset falls outside the required annulus.
     */
    public static int[] candidateOffset(long siteKey, int index, int candidate) {
        int seed = mix((int) siteKey ^ OFFSET_SALT) + (index + 1) * 0x9e3779b1;
        int first = mix(seed + candidate * 0x6d2b79f5);
        int second = mix(first + 0x9e3779b1);
        int dx = Integer.remainderUnsigned(first, SPAN) - MAX_RADIUS;
        int dz = Integer.remainderUnsigned(second, SPAN) - MAX_RADIUS;
        int squared = dx * dx + dz * dz;
        if (squared < MIN_RADIUS * MIN_RADIUS || squared > MAX_RADIUS * MAX_RADIUS) return null;
        return new int[] { dx, dz };
    }

    private static int mix(int value) {
        int mixed = value;
        mixed = (mixed ^ mixed >>> 16) * 0x7feb352d;
        mixed = (mixed ^ mixed >>> 15) * 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }
}
