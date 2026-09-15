package com.gameexpert.terrain.mc.feature;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Canonical-completion layer for evidence boundaries.
 *
 * <p>{@link Mc263FeatureBlockState#exactState()} is the project's internal exact-state
 * representation: it omits a property whenever the block's registered exact state carries only that
 * property's default value ({@code minecraft:deepslate} rather than
 * {@code minecraft:deepslate[axis=y]}). Vanilla's canonical rendering — {@code BlockState#getValues}
 * over the block's whole {@code StateDefinition} — always prints every property of the state, so the
 * two forms disagree for exactly the blocks listed in {@link #DEFAULT_PROPERTY_COMPLETION}.
 *
 * <p>This class converts our internal form into the vanilla-complete form at the boundary where a
 * pinned official receipt is compared, and nowhere else: the internal representation and every
 * pinned catalog string stay unchanged.
 *
 * <p><b>Authentication.</b> Every row below is the {@code defaultBlockState()} rendering of the
 * pinned inner server jar {@code 26.3-snapshot-7} (inner SHA-1
 * {@code 2f1ef79f3cad10138ad18da45b265fe656624026}, outer SHA-1
 * {@code 06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61}), read through
 * {@code BuiltInRegistries.BLOCK} after {@code Bootstrap.bootStrap()} and serialized with the same
 * ascending-property-name rule the {@code input-state-cells-v1} / {@code input-state-digests-v1}
 * oracles use. The rows are the complete set: comparing that registry dump against the whole frozen
 * {@link Mc263FeatureBlockState} exact-state catalog yields the released-prefix rows plus the
 * append-only default states below and no state that carries a property vanilla does not define. The
 * defining vanilla block classes are {@code RotatedPillarBlock} / {@code InfestedRotatedPillarBlock}
 * ({@code axis}, default {@code y}), {@code LiquidBlock} ({@code level}, default {@code 0}),
 * {@code RedStoneOreBlock} ({@code lit}, default {@code false}) and {@code SaplingBlock}
 * ({@code stage}, default {@code 0}) and {@code CarvedPumpkinBlock} ({@code facing}, default
 * {@code north}).
 *
 * <p>{@code Mc263VanillaCanonicalStateTest} binds the table to the authenticated receipts: to the
 * ISC-V1 per-cell palettes (payload sha {@code 751fbf75…}) and to the ISD-V1 recorded vanilla
 * sample-cell rows (payload sha {@code aca00756…}).
 */
public final class Mc263VanillaCanonicalState {
    /** Bare internal exact state → vanilla canonical rendering with its default properties. */
    private static final Map<String, String> DEFAULT_PROPERTY_COMPLETION = Map.ofEntries(
            Map.entry("minecraft:deepslate", "minecraft:deepslate[axis=y]"),
            Map.entry("minecraft:infested_deepslate", "minecraft:infested_deepslate[axis=y]"),
            Map.entry("minecraft:water", "minecraft:water[level=0]"),
            Map.entry("minecraft:lava", "minecraft:lava[level=0]"),
            Map.entry("minecraft:redstone_ore", "minecraft:redstone_ore[lit=false]"),
            Map.entry("minecraft:deepslate_redstone_ore",
                    "minecraft:deepslate_redstone_ore[lit=false]"),
            Map.entry("minecraft:oak_sapling", "minecraft:oak_sapling[stage=0]"),
            Map.entry("minecraft:birch_sapling", "minecraft:birch_sapling[stage=0]"),
            Map.entry("minecraft:spruce_sapling", "minecraft:spruce_sapling[stage=0]"),
            Map.entry("minecraft:jungle_sapling", "minecraft:jungle_sapling[stage=0]"),
            Map.entry("minecraft:acacia_sapling", "minecraft:acacia_sapling[stage=0]"),
            Map.entry("minecraft:dark_oak_sapling", "minecraft:dark_oak_sapling[stage=0]"),
            Map.entry("minecraft:cherry_sapling", "minecraft:cherry_sapling[stage=0]"),
            Map.entry("minecraft:pale_oak_sapling", "minecraft:pale_oak_sapling[stage=0]"),
            Map.entry("minecraft:poplar_sapling", "minecraft:poplar_sapling[stage=0]"),
            Map.entry("minecraft:jack_o_lantern", "minecraft:jack_o_lantern[facing=north]"));

    private Mc263VanillaCanonicalState() {
    }

    /** The authenticated completion table, ordered by bare state, for receipts and diagnostics. */
    public static SortedMap<String, String> defaultPropertyCompletion() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(DEFAULT_PROPERTY_COMPLETION));
    }

    /**
     * Inverse of the completion table: the bare internal state one vanilla-complete spelling
     * folds back onto, or {@code null} when the spelling is not a table completion. The pinned
     * production closure enumerates vanilla-complete spellings, so this keeps a block such as
     * {@code minecraft:deepslate[axis=y]} one internal state instead of a second exact code.
     */
    public static String internalBareForVanillaCompletion(String exactState) {
        if (exactState == null) return null;
        for (Map.Entry<String, String> entry : DEFAULT_PROPERTY_COMPLETION.entrySet()) {
            if (entry.getValue().equals(exactState)) return entry.getKey();
        }
        return null;
    }

    /**
     * Renders one internal exact state in vanilla's canonical form: every property of the block's
     * state definition, ascending property-name order, {@code namespace:path[k=v,...]}.
     *
     * <p>A state that already prints every property is returned unchanged, so the helper is
     * idempotent and safe to apply to an already-canonical string.
     */
    public static String renderVanillaCanonical(String exactState) {
        if (exactState == null) throw new IllegalArgumentException("exact state is required");
        int bracket = exactState.indexOf('[');
        if (bracket < 0) {
            String completed = DEFAULT_PROPERTY_COMPLETION.get(exactState);
            return completed == null ? exactState : completed;
        }
        String blockKey = exactState.substring(0, bracket);
        String completed = DEFAULT_PROPERTY_COMPLETION.get(blockKey);
        if (completed == null) return exactState;
        // The block is in the table, but this state already carries a property list. Vanilla
        // defines exactly the properties the completed form prints, so the state is only canonical
        // when it names the same property set; anything else means the table has gone stale
        // against the internal catalog and must be regenerated from the pinned registry.
        if (propertyNames(exactState).equals(propertyNames(completed))) return exactState;
        throw new IllegalStateException("canonical completion table does not cover exact state "
                + exactState + "; the pinned vanilla form defines " + propertyNames(completed));
    }

    private static Set<String> propertyNames(String state) {
        int bracket = state.indexOf('[');
        if (bracket < 0 || !state.endsWith("]")) return Set.of();
        Set<String> names = new TreeSet<>();
        for (String assignment : state.substring(bracket + 1, state.length() - 1).split(",")) {
            int equals = assignment.indexOf('=');
            if (equals < 0) throw new IllegalArgumentException("malformed exact state " + state);
            names.add(assignment.substring(0, equals));
        }
        return names;
    }
}
