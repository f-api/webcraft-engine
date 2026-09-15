package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stable per-block exact-state codebook for the immutable 26.3 final-chunk carrier. */
public final class Mc263ExactStateCodec {
    private static final Map<Integer, List<String>> EXPLICIT_RELEASED_CODEBOOKS = Map.of(
            Blocks.COBBLE_WALL, List.of(
                    "minecraft:cobblestone_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=none]",
                    "minecraft:cobblestone_wall[east=low,north=none,south=low,up=true,waterlogged=false,west=none]",
                    "minecraft:cobblestone_wall[east=low,north=none,south=none,up=false,waterlogged=false,west=low]",
                    "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=none]",
                    "minecraft:cobblestone_wall[east=none,north=low,south=low,up=false,waterlogged=false,west=none]",
                    "minecraft:cobblestone_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=low]",
                    "minecraft:cobblestone_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=low]",
                    "minecraft:cobblestone_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]",
                    "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]",
                    "minecraft:cobblestone_wall[east=none,north=low,south=none,up=true,waterlogged=false,west=none]"),
            Blocks.STONE_BRICK_STAIRS, List.of(
                    "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=north,half=top,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=inner_left,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]",
                    "minecraft:stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=true]",
                    "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=true]"),
            Blocks.STONE_SLAB, List.of(
                    "minecraft:stone_slab[type=bottom,waterlogged=false]",
                    "minecraft:stone_slab[type=double,waterlogged=false]",
                    "minecraft:stone_slab[type=bottom,waterlogged=true]"),
            Blocks.MOSSY_STONE_BRICK_SLAB, List.of(
                    "minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=false]",
                    "minecraft:mossy_stone_brick_slab[type=double,waterlogged=false]",
                    "minecraft:mossy_stone_brick_slab[type=top,waterlogged=false]",
                    "minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=true]"),
            Blocks.MOSSY_STONE_BRICK_STAIRS, List.of(
                    "minecraft:mossy_stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=north,half=top,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=west,half=top,shape=straight,waterlogged=false]",
                    "minecraft:mossy_stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]",
                    "minecraft:mossy_stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]"),
            Blocks.MOSSY_STONE_BRICK_WALL, List.of(
                    "minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]",
                    "minecraft:mossy_stone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=none]",
                    "minecraft:mossy_stone_brick_wall[east=none,north=tall,south=none,up=true,waterlogged=false,west=none]",
                    "minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]",
                    "minecraft:mossy_stone_brick_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]",
                    "minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=true,west=none]",
                    "minecraft:mossy_stone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=true,west=none]",
                    "minecraft:mossy_stone_brick_wall[east=none,north=tall,south=none,up=true,waterlogged=true,west=none]"),
            Blocks.CHEST, List.of(
                    "minecraft:chest[facing=north,type=single,waterlogged=false]",
                    "minecraft:chest[facing=east,type=single,waterlogged=false]",
                    "minecraft:chest[facing=east,type=single,waterlogged=true]",
                    "minecraft:chest[facing=north,type=single,waterlogged=true]",
                    "minecraft:chest[facing=south,type=single,waterlogged=false]",
                    "minecraft:chest[facing=south,type=single,waterlogged=true]",
                    "minecraft:chest[facing=west,type=single,waterlogged=false]",
                    "minecraft:chest[facing=west,type=single,waterlogged=true]",
                    "minecraft:chest[facing=north,type=left,waterlogged=false]",
                    "minecraft:chest[facing=north,type=right,waterlogged=false]",
                    "minecraft:chest[facing=west,type=left,waterlogged=false]",
                    "minecraft:chest[facing=west,type=right,waterlogged=false]"),
            Blocks.WHITE_WALL_BANNER, List.of(
                    "minecraft:white_wall_banner[facing=north]",
                    "minecraft:white_wall_banner[facing=west]",
                    "minecraft:white_wall_banner[facing=east]",
                    "minecraft:white_wall_banner[facing=south]"));
    /** G1Q additions preserve every released code as a strict prefix, then append audit rows. */
    private static final Map<Integer, List<String>> G1Q_APPEND_ONLY_CODEBOOKS = Map.of(
            Blocks.MANGROVE_LEAVES, List.of(
                    "minecraft:mangrove_leaves[distance=7,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=1,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=1,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=2,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=2,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=3,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=3,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=4,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=4,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=5,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=5,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=6,persistent=false,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=6,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=7,persistent=false,waterlogged=true]",
                    "minecraft:mangrove_leaves[distance=1,persistent=true,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=2,persistent=true,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=3,persistent=true,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=4,persistent=true,waterlogged=false]",
                    "minecraft:mangrove_leaves[distance=7,persistent=true,waterlogged=false]"),
            Blocks.MUDDY_MANGROVE_ROOTS, List.of(
                    "minecraft:muddy_mangrove_roots[axis=y]",
                    "minecraft:muddy_mangrove_roots[axis=x]",
                    "minecraft:muddy_mangrove_roots[axis=z]"),
            Blocks.CHAIN, List.of(
                    "minecraft:chain[axis=y]",
                    "minecraft:iron_chain[axis=y,waterlogged=false]"),
            Blocks.TRIPWIRE_HOOK_BLOCK, List.of(
                    "minecraft:tripwire_hook[attached=false,facing=north,powered=false]",
                    "minecraft:tripwire_hook[attached=true,facing=east,powered=false]",
                    "minecraft:tripwire_hook[attached=true,facing=north,powered=false]",
                    "minecraft:tripwire_hook[attached=true,facing=south,powered=false]",
                    "minecraft:tripwire_hook[attached=true,facing=west,powered=false]",
                    "minecraft:tripwire_hook[attached=false,facing=east,powered=false]",
                    "minecraft:tripwire_hook[attached=false,facing=south,powered=false]",
                    "minecraft:tripwire_hook[attached=false,facing=west,powered=false]"),
            Blocks.TRIPWIRE, List.of(
                    "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=false,west=false]",
                    "minecraft:tripwire[attached=true,disarmed=false,east=false,north=true,powered=false,south=true,west=false]",
                    "minecraft:tripwire[attached=true,disarmed=false,east=true,north=false,powered=false,south=false,west=true]",
                    "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=false,west=true]",
                    "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=true,west=false]",
                    "minecraft:tripwire[attached=false,disarmed=false,east=false,north=true,powered=false,south=false,west=false]",
                    "minecraft:tripwire[attached=false,disarmed=false,east=false,north=true,powered=false,south=true,west=false]",
                    "minecraft:tripwire[attached=false,disarmed=false,east=true,north=false,powered=false,south=false,west=false]",
                    "minecraft:tripwire[attached=false,disarmed=false,east=true,north=false,powered=false,south=false,west=true]"),
            Blocks.DISPENSER, List.of(
                    "minecraft:dispenser[facing=north,triggered=false]",
                    "minecraft:dispenser[facing=east,triggered=false]",
                    "minecraft:dispenser[facing=south,triggered=false]",
                    "minecraft:dispenser[facing=west,triggered=false]",
                    "minecraft:dispenser[facing=up,triggered=false]"));
    private static final Map<Integer, List<Mc263FeatureBlockState>> STATES_BY_ID;
    private static final Map<String, Integer> CODE_BY_EXACT;
    private static final int MAX_STATE_COUNT;

    static {
        Map<Integer, List<Mc263FeatureBlockState>> grouped = new HashMap<>();
        for (Mc263FeatureBlockState state : Mc263FeatureBlockState.exactCatalog()) {
            grouped.computeIfAbsent(state.blockId(), ignored -> new ArrayList<>()).add(state);
        }
        Map<String, Integer> codes = new HashMap<>();
        Map<Integer, List<Mc263FeatureBlockState>> frozen = new HashMap<>();
        int maximum = 0;
        for (Map.Entry<Integer, List<Mc263FeatureBlockState>> entry : grouped.entrySet()) {
            int id = entry.getKey();
            Mc263FeatureBlockState defaultState = Mc263FeatureBlockState.defaultForId(id);
            List<Mc263FeatureBlockState> ordered = releasedOrder(
                    id, entry.getValue(), defaultState);
            if (ordered.size() > 256) {
                throw new ExceptionInInitializerError("block " + id
                        + " has more than 256 released exact states: " + ordered.size());
            }
            for (int code = 0; code < ordered.size(); code++) {
                Integer previous = codes.put(ordered.get(code).exactState(), code);
                if (previous != null) {
                    throw new ExceptionInInitializerError("duplicate exact state in codebook: "
                            + ordered.get(code).exactState());
                }
            }
            maximum = Math.max(maximum, ordered.size());
            frozen.put(id, List.copyOf(ordered));
        }
        STATES_BY_ID = Map.copyOf(frozen);
        CODE_BY_EXACT = Map.copyOf(codes);
        MAX_STATE_COUNT = maximum;
    }

    private Mc263ExactStateCodec() {}

    private static List<Mc263FeatureBlockState> releasedOrder(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<Mc263FeatureBlockState> ruinedPortal = catalogStates.stream()
                .filter(state -> Mc263FeatureBlockState.isRuinedPortalClosureExactState(
                        state.exactState()))
                .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                .toList();
        if (ruinedPortal.isEmpty()) {
            return releasedOrderBeforeRuinedPortalClosure(blockId, catalogStates, defaultState);
        }
        List<Mc263FeatureBlockState> releasedBeforeRuinedPortal = catalogStates.stream()
                .filter(state -> !Mc263FeatureBlockState.isRuinedPortalClosureExactState(
                        state.exactState()))
                .toList();
        if (releasedBeforeRuinedPortal.isEmpty()
                || Mc263FeatureBlockState.isRuinedPortalClosureExactState(
                        defaultState.exactState())) {
            throw new ExceptionInInitializerError(
                    "ruined-portal closure changed an existing/default codebook for block "
                            + blockId);
        }
        List<Mc263FeatureBlockState> withRuinedPortal = new ArrayList<>(
                releasedOrderBeforeRuinedPortalClosure(blockId, releasedBeforeRuinedPortal,
                        defaultState));
        withRuinedPortal.addAll(ruinedPortal);
        return withRuinedPortal;
    }

    private static List<Mc263FeatureBlockState> releasedOrderBeforeRuinedPortalClosure(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<Mc263FeatureBlockState> postClosure = catalogStates.stream()
                .filter(state -> Mc263FeatureBlockState.isPostStateCloseExactState(
                        state.exactState()))
                .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                .toList();
        if (postClosure.isEmpty()) {
            return releasedOrderBeforePostStateClose(blockId, catalogStates, defaultState);
        }
        List<Mc263FeatureBlockState> released = catalogStates.stream()
                .filter(state -> !Mc263FeatureBlockState.isPostStateCloseExactState(
                        state.exactState()))
                .toList();
        if (released.isEmpty()
                || Mc263FeatureBlockState.isPostStateCloseExactState(defaultState.exactState())) {
            throw new ExceptionInInitializerError(
                    "POST STATE-CLOSE changed an existing/default codebook for block " + blockId);
        }
        List<Mc263FeatureBlockState> ordered = new ArrayList<>(
                releasedOrderBeforePostStateClose(blockId, released, defaultState));
        ordered.addAll(postClosure);
        return ordered;
    }

    private static List<Mc263FeatureBlockState> releasedOrderBeforePostStateClose(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<Mc263FeatureBlockState> saplingStage = catalogStates.stream()
                .filter(state -> Mc263FeatureBlockState.isSaplingStageExactState(
                        state.exactState()))
                .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                .toList();
        if (saplingStage.isEmpty()) {
            return releasedOrderBeforeSaplingStage(blockId, catalogStates, defaultState);
        }
        List<Mc263FeatureBlockState> released = catalogStates.stream()
                .filter(state -> !Mc263FeatureBlockState.isSaplingStageExactState(
                        state.exactState()))
                .toList();
        if (released.isEmpty()) {
            if (!Mc263FeatureBlockState.isSaplingStageExactState(defaultState.exactState())) {
                throw new ExceptionInInitializerError(
                        "sapling stage changed an unowned default for block " + blockId);
            }
            List<Mc263FeatureBlockState> ordered = new ArrayList<>();
            ordered.add(defaultState);
            saplingStage.stream()
                    .filter(state -> !state.exactState().equals(defaultState.exactState()))
                    .forEach(ordered::add);
            return ordered;
        }
        if (Mc263FeatureBlockState.isSaplingStageExactState(defaultState.exactState())) {
            throw new ExceptionInInitializerError(
                    "sapling stage replaced a released default for block " + blockId);
        }
        List<Mc263FeatureBlockState> ordered = new ArrayList<>(
                releasedOrderBeforeSaplingStage(blockId, released, defaultState));
        ordered.addAll(saplingStage);
        return ordered;
    }

    private static List<Mc263FeatureBlockState> releasedOrderBeforeSaplingStage(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<Mc263FeatureBlockState> stateClose = catalogStates.stream()
                .filter(state -> Mc263FeatureBlockState.isStateCloseExactState(state.exactState()))
                .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                .toList();
        if (!stateClose.isEmpty()) {
            List<Mc263FeatureBlockState> previous = catalogStates.stream()
                    .filter(state -> !Mc263FeatureBlockState.isStateCloseExactState(
                            state.exactState()))
                    .toList();
            if (previous.isEmpty()) {
                List<Mc263FeatureBlockState> ordered = new ArrayList<>();
                ordered.add(defaultState);
                stateClose.stream().filter(state -> state != defaultState)
                        .forEach(ordered::add);
                return ordered;
            }
            if (Mc263FeatureBlockState.isStateCloseExactState(defaultState.exactState())) {
                throw new ExceptionInInitializerError(
                        "STATE-CLOSE changed an existing default for block " + blockId);
            }
            List<Mc263FeatureBlockState> ordered = new ArrayList<>(
                    releasedOrderBeforeStateClose(blockId, previous, defaultState));
            ordered.addAll(stateClose);
            return ordered;
        }
        return releasedOrderBeforeStateClose(blockId, catalogStates, defaultState);
    }

    private static List<Mc263FeatureBlockState> releasedOrderBeforeStateClose(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<Mc263FeatureBlockState> additions = catalogStates.stream()
                .filter(state -> Mc263FeatureBlockState.isVillageG3J15ExactState(state.exactState()))
                .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                .toList();
        if (additions.isEmpty()) {
            return releasedOrderBeforeVillageG3J15(blockId, catalogStates, defaultState);
        }
        List<Mc263FeatureBlockState> previous = catalogStates.stream()
                .filter(state -> !Mc263FeatureBlockState.isVillageG3J15ExactState(state.exactState()))
                .toList();
        if (previous.isEmpty()) {
            if (!Mc263FeatureBlockState.isVillageG3J15ExactState(defaultState.exactState())) {
                throw new ExceptionInInitializerError(
                        "Village G3J15 changed an unowned default for block " + blockId);
            }
            List<Mc263FeatureBlockState> ordered = new ArrayList<>();
            ordered.add(defaultState);
            additions.stream()
                    .filter(state -> !state.exactState().equals(defaultState.exactState()))
                    .forEach(ordered::add);
            return ordered;
        }
        if (Mc263FeatureBlockState.isVillageG3J15ExactState(defaultState.exactState())) {
            throw new ExceptionInInitializerError(
                    "Village G3J15 replaced a released default for block " + blockId);
        }
        List<Mc263FeatureBlockState> ordered = new ArrayList<>(
                releasedOrderBeforeVillageG3J15(blockId, previous, defaultState));
        ordered.addAll(additions);
        return ordered;
    }

    private static List<Mc263FeatureBlockState> releasedOrderBeforeVillageG3J15(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<Mc263FeatureBlockState> additions = catalogStates.stream()
                .filter(state -> Mc263FeatureBlockState.isMansionP2PExactState(state.exactState()))
                .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                .toList();
        if (additions.isEmpty()) {
            return releasedOrderBeforeMansionP2P(blockId, catalogStates, defaultState);
        }
        List<Mc263FeatureBlockState> previous = catalogStates.stream()
                .filter(state -> !Mc263FeatureBlockState.isMansionP2PExactState(state.exactState()))
                .toList();
        if (previous.isEmpty()) {
            if (!Mc263FeatureBlockState.isMansionP2PExactState(defaultState.exactState())) {
                throw new ExceptionInInitializerError(
                        "Mansion P2P changed an unowned default for block " + blockId);
            }
            List<Mc263FeatureBlockState> ordered = new ArrayList<>();
            ordered.add(defaultState);
            additions.stream()
                    .filter(state -> !state.exactState().equals(defaultState.exactState()))
                    .forEach(ordered::add);
            return ordered;
        }
        if (Mc263FeatureBlockState.isMansionP2PExactState(defaultState.exactState())) {
            throw new ExceptionInInitializerError(
                    "Mansion P2P replaced a released default for block " + blockId);
        }
        List<Mc263FeatureBlockState> ordered = new ArrayList<>(
                releasedOrderBeforeMansionP2P(blockId, previous, defaultState));
        ordered.addAll(additions);
        return ordered;
    }

    private static List<Mc263FeatureBlockState> releasedOrderBeforeMansionP2P(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState) {
        List<String> pinned = G1Q_APPEND_ONLY_CODEBOOKS.get(blockId);
        if (pinned == null) pinned = EXPLICIT_RELEASED_CODEBOOKS.get(blockId);
        if (pinned == null) {
            List<Mc263FeatureBlockState> ordered = new ArrayList<>();
            ordered.add(defaultState);
            catalogStates.stream()
                    .filter(state -> !state.exactState().equals(defaultState.exactState()))
                    .sorted(java.util.Comparator.comparing(Mc263FeatureBlockState::exactState))
                    .forEach(ordered::add);
            return ordered;
        }
        return explicitReleasedOrder(blockId, catalogStates, defaultState, pinned);
    }

    static List<Mc263FeatureBlockState> explicitReleasedOrder(int blockId,
            List<Mc263FeatureBlockState> catalogStates, Mc263FeatureBlockState defaultState,
            List<String> pinned) {
        if (pinned.isEmpty() || !pinned.getFirst().equals(defaultState.exactState())) {
            throw new ExceptionInInitializerError("explicit exact-state codebook for block "
                    + blockId + " must pin the default state at code 0");
        }
        if (pinned.size() > 256) {
            throw new ExceptionInInitializerError("explicit exact-state codebook for block "
                    + blockId + " contains a code above 255");
        }

        Map<String, Mc263FeatureBlockState> remaining = new HashMap<>();
        for (Mc263FeatureBlockState state : catalogStates) {
            if (state.blockId() != blockId
                    || remaining.put(state.exactState(), state) != null) {
                throw new ExceptionInInitializerError("duplicate/mismatched exact state for block "
                        + blockId + ": " + state.exactState());
            }
        }
        Map<String, Integer> releasedCodes = new HashMap<>();
        List<Mc263FeatureBlockState> ordered = new ArrayList<>(pinned.size());
        for (int code = 0; code < pinned.size(); code++) {
            String exact = pinned.get(code);
            Integer previous = releasedCodes.putIfAbsent(exact, code);
            if (previous != null) {
                throw new ExceptionInInitializerError("duplicate pinned exact state for block "
                        + blockId + " at codes " + previous + " and " + code + ": " + exact);
            }
            Mc263FeatureBlockState state = remaining.remove(exact);
            if (state == null) {
                throw new ExceptionInInitializerError("missing pinned exact state for block "
                        + blockId + " at code " + code + ": " + exact);
            }
            ordered.add(state);
        }
        if (!remaining.isEmpty()) {
            throw new ExceptionInInitializerError("unreleased exact state for explicitly coded block "
                    + blockId + " requires an append-only code assignment: "
                    + remaining.keySet().stream().sorted().toList());
        }
        return ordered;
    }

    /**
     * Identity-keyed codebook, resolved once per class loader.
     *
     * <p>AGENTS rule 10l: {@link Mc263FeatureBlockState} interns exactly one instance per exact
     * state, so a state's code is a constant of that instance — yet encoding a final chunk asks
     * for it once per written block, and each ask hashed the full exact-state spelling. The
     * holder answers the identical question for every state the released codebook admits, using
     * the very predicate the per-call path used (the {@code CODE_BY_EXACT} entry whose
     * {@link #decode} round-trips back to the same interned state), so a state outside the
     * codebook still fails closed with the same message.</p>
     */
    private static final class CodeByStateHolder {
        private static final Map<Mc263FeatureBlockState, Integer> CODES = resolve();

        private static Map<Mc263FeatureBlockState, Integer> resolve() {
            java.util.IdentityHashMap<Mc263FeatureBlockState, Integer> codes =
                    new java.util.IdentityHashMap<>();
            for (List<Mc263FeatureBlockState> states : STATES_BY_ID.values()) {
                for (Mc263FeatureBlockState state : states) {
                    Integer code = CODE_BY_EXACT.get(state.exactState());
                    if (code != null && decode(state.blockId(), code) == state) {
                        codes.put(state, code);
                    }
                }
            }
            return codes;
        }

        private CodeByStateHolder() { }
    }

    public static int stateCode(Mc263FeatureBlockState state) {
        Integer code = CodeByStateHolder.CODES.get(state);
        if (code == null) {
            throw new IllegalArgumentException("state is outside released exact codebook: "
                    + state.exactState());
        }
        return code;
    }

    public static Mc263FeatureBlockState decode(int blockId, int stateCode) {
        List<Mc263FeatureBlockState> states = STATES_BY_ID.get(blockId);
        if (states == null || stateCode < 0 || stateCode >= states.size()) {
            throw new IllegalArgumentException("invalid exact state code " + stateCode
                    + " for block ID " + blockId);
        }
        return states.get(stateCode);
    }

    public static int stateCount(int blockId) {
        List<Mc263FeatureBlockState> states = STATES_BY_ID.get(blockId);
        if (states == null) throw new IllegalArgumentException("unknown block ID: " + blockId);
        return states.size();
    }

    public static int maximumStateCount() {
        return MAX_STATE_COUNT;
    }
}
