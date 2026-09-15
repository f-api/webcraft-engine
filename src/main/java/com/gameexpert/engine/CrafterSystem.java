package com.gameexpert.engine;

import com.gameexpert.engine.dispenser.CrafterRules;
import com.gameexpert.engine.dispenser.DispenserRules;
import com.gameexpert.engine.hopper.HopperStack;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.persistence.animal.AnimalBlockTickPersistenceService;
import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * [CONTAINER-MENUS] Owner of the pinned 26.3-snapshot-7 {@code CrafterBlock} redstone behaviour
 * (javap): the {@code TRIGGERED} neighbour edge ({@code hasNeighborSignal(pos)} only, no quasi
 * connectivity), the four-game-tick scheduled {@code dispenseFrom}, the craft itself through
 * {@link PlayerInventory#crafterCraft} (the repository recipe catalogue a player's crafting table
 * uses) and the six-game-tick {@code CRAFTING} countdown of {@code CrafterBlockEntity.serverTick}.
 * The standalone twin lives in {@code StandaloneWorldRuntime} over
 * {@code StandaloneDispenserRules.ts}.
 *
 * <p>The signal model is the repository's {@code Level.hasNeighborSignal}: a powered poplar
 * button or pressure plate next to the cell, the one the shelf, hopper and dispenser read.
 * {@code CrafterBlock#getStateForPlacement} reads the signal and {@code setPlacedBy} schedules a
 * craft when it is on; here a placed crafter gets the same outcome from its own neighbour
 * evaluation at the end of the placing tick (it is placed with {@code TRIGGERED} off, so a
 * present signal is a rising edge).
 *
 * <p>Scheduled crafts persist like vanilla's chunk-saved block ticks and the countdown like
 * {@code crafting_ticks_remaining}: both are absolute-game-time rows of the durable block-tick table
 * ({@link AnimalBlockTickPersistenceService.Kind#CRAFTER_TICK} / {@code CRAFTER_CRAFTING}).
 * Sounds: levelEvent 1050 when no recipe matches; 1049 and the white-smoke burst 2010 only when
 * {@code dispenseItem} spawns items into the world.
 */
final class CrafterSystem {

    /** {@code LevelEvent} ids of the crafter (26.3-snapshot-7 {@code LevelEvent}). */
    static final int EVENT_CRAFTER_CRAFT = 1049;
    static final int EVENT_CRAFTER_FAIL = 1050;
    static final int EVENT_SHOOT_WHITE_SMOKE = 2010;

    private final WorldRuntime rt;
    /** Due game time of each scheduled craft, in scheduling order. */
    private final LinkedHashMap<BlockPos, Long> scheduled = new LinkedHashMap<>();
    /** Game time at which each crafter's {@code CRAFTING} bit clears. */
    private final LinkedHashMap<BlockPos, Long> crafting = new LinkedHashMap<>();
    private final Random random = new Random();
    private DispenseBehaviours behaviours;

    CrafterSystem(WorldRuntime rt) {
        this.rt = rt;
    }

    private DispenseBehaviours behaviours() {
        if (behaviours == null) behaviours = new DispenseBehaviours(rt, random);
        return behaviours;
    }

    /** Test/diagnostic view of the pending crafts. */
    Map<BlockPos, Long> scheduledCrafts() {
        return java.util.Collections.unmodifiableMap(scheduled);
    }

    /** The block entity at {@code pos} was removed: drop its schedule and countdown. */
    void forget(BlockPos pos) {
        if (scheduled.remove(pos) != null) forgetRow(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_TICK);
        if (crafting.remove(pos) != null) {
            forgetRow(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_CRAFTING);
        }
    }

    /** Restores the durable schedule and countdown rows (world activation). */
    void hydrate(List<AnimalBlockTickPersistenceService.ScheduledTick> persisted) {
        scheduled.clear();
        crafting.clear();
        for (AnimalBlockTickPersistenceService.ScheduledTick tick : persisted) {
            BlockPos pos = new BlockPos(tick.x(), tick.y(), tick.z());
            if (tick.kind() == AnimalBlockTickPersistenceService.Kind.CRAFTER_TICK) {
                scheduled.put(pos, tick.dueTick());
            } else if (tick.kind() == AnimalBlockTickPersistenceService.Kind.CRAFTER_CRAFTING) {
                crafting.put(pos, tick.dueTick());
            }
        }
    }

    private void persist(BlockPos pos, AnimalBlockTickPersistenceService.Kind kind, long due) {
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (store != null) store.upsert(pos.x(), pos.y(), pos.z(), kind, due);
    }

    private void forgetRow(BlockPos pos, AnimalBlockTickPersistenceService.Kind kind) {
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (store != null) store.deleteAfterCommittedHatch(pos.x(), pos.y(), pos.z(), kind);
    }

    private void schedule(BlockPos pos, long due) {
        if (scheduled.putIfAbsent(pos, due) != null) return;
        persist(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_TICK, due);
    }

    private void startCrafting(BlockPos pos, long clearAt) {
        crafting.remove(pos);
        crafting.put(pos, clearAt);
        persist(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_CRAFTING, clearAt);
    }

    private void stopCrafting(BlockPos pos) {
        if (crafting.remove(pos) != null) {
            forgetRow(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_CRAFTING);
        }
    }

    /** {@code CrafterBlock#neighborChanged} at a notified cell (or the placement read of itself). */
    void evaluate(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        int block = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
        if (block != Blocks.CRAFTER) return;
        BlockPos pos = new BlockPos(x, y, z);
        int state = rt.blockStates().get(x, y, z, block);
        boolean powered = rt.tickLoop().hopperHasNeighborSignal(x, y, z);
        boolean triggered = CrafterRules.triggered(state);
        if (powered && !triggered) {
            schedule(pos, rt.clock().gameTimeMcTicks() + CrafterRules.TRIGGER_DURATION);
            setState(pos, CrafterRules.withTriggered(state, true));
        } else if (!powered && triggered) {
            // neighborChanged clears TRIGGERED and CRAFTING together.
            stopCrafting(pos);
            setState(pos, CrafterRules.withCrafting(CrafterRules.withTriggered(state, false), false));
        } else if (CrafterRules.crafting(state) && !crafting.containsKey(pos)) {
            // A CRAFTING bit with no live countdown (an owner restart) clears now.
            setState(pos, CrafterRules.withCrafting(state, false));
        }
    }

    private void setState(BlockPos pos, int next) {
        rt.setBlockState(pos.x(), pos.y(), pos.z(), Blocks.CRAFTER, next);
        rt.tickBlockChanges().put(pos, (short) Blocks.CRAFTER);
        rt.tickLoop().publishHopperChestChange(pos);
    }

    /**
     * Runs every craft whose scheduled game time has come, then the {@code serverTick}
     * countdown of every crafting crafter.
     */
    void tick() {
        long gameTime = rt.clock().gameTimeMcTicks();
        if (!scheduled.isEmpty()) {
            List<BlockPos> due = new ArrayList<>();
            Iterator<Map.Entry<BlockPos, Long>> iterator = scheduled.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Long> entry = iterator.next();
                if (entry.getValue() > gameTime) continue;
                due.add(entry.getKey());
                iterator.remove();
            }
            for (BlockPos pos : due) {
                forgetRow(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_TICK);
                int block = WorldTickLoop.residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
                // LevelTicks only runs a scheduled tick while the cell still holds the crafter.
                if (block != Blocks.CRAFTER) continue;
                dispenseFrom(pos, gameTime);
            }
        }
        if (crafting.isEmpty()) return;
        Iterator<Map.Entry<BlockPos, Long>> iterator = crafting.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (entry.getValue() > gameTime) continue;
            iterator.remove();
            BlockPos pos = entry.getKey();
            forgetRow(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_CRAFTING);
            if (WorldTickLoop.residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z())
                    != Blocks.CRAFTER) continue;
            int state = rt.blockStates().get(pos.x(), pos.y(), pos.z(), Blocks.CRAFTER);
            if (CrafterRules.crafting(state)) setState(pos, CrafterRules.withCrafting(state, false));
        }
    }

    /** {@code CrafterBlock#dispenseFrom}. */
    void dispenseFrom(BlockPos pos, long gameTime) {
        int x = pos.x(), y = pos.y(), z = pos.z();
        // A player's container settlement owns this inventory until it lands; retry next tick.
        if (rt.containerSettlementTargets(x, y, z)) {
            retry(pos, gameTime);
            return;
        }
        ChestInventory inventory = rt.openContainer(x, y, z);
        if (inventory == null || inventory.slots() != CrafterRules.SLOTS) return;
        int state = rt.blockStates().get(x, y, z, Blocks.CRAFTER);
        int front = CrafterRules.front(state);
        PlayerInventory.StackSnapshot[] grid = new PlayerInventory.StackSnapshot[CrafterRules.SLOTS];
        for (int slot = 0; slot < grid.length; slot++) grid[slot] = snapshot(inventory, slot);
        PlayerInventory.CrafterCraft craft = PlayerInventory.crafterCraft(grid);
        if (craft == null) {
            rt.mobSystem().levelEvent(EVENT_CRAFTER_FAIL, x, y, z, 0);
            return;
        }
        // dispenseItem's HopperBlockEntity.getContainerAt unpacks a LOOT container in front
        // synchronously; its durable first open lands on a later owner turn here, so the crafter
        // waits for it instead of spitting the result out.
        if (rt.hopperSystem().frontContainerPending(pos, front)) {
            retry(pos, gameTime);
            return;
        }
        // setCraftingTicksRemaining(6) + CRAFTING=true; serverTick then counts down within this
        // same game tick (block entities tick after scheduled ticks), so the bit clears after
        // five further game ticks.
        startCrafting(pos, gameTime + CrafterRules.CRAFTING_TICKS - 1);
        setState(pos, CrafterRules.withCrafting(state, true));
        dispenseItem(pos, inventory, stack(craft.result()), front);
        for (PlayerInventory.StackSnapshot remainder : craft.remainders()) {
            dispenseItem(pos, inventory, stack(remainder), front);
        }
        // getItems().forEach(stack -> { if (!stack.isEmpty()) stack.shrink(1); })
        for (int slot = 0; slot < CrafterRules.SLOTS; slot++) {
            if (inventory.itemType(slot) != PlayerInventory.EMPTY && inventory.count(slot) > 0) {
                inventory.take(slot, 1);
            }
        }
        rt.chestStorage().markDirty(x, y, z);
        rt.tickLoop().publishHopperChestChange(pos);
    }

    /**
     * {@code CrafterBlock#dispenseItem}: the container in front first
     * ({@link HopperSystem#crafterInsert}), then {@code DefaultDispenseItemBehavior.spawnItem}
     * at {@code Vec3.atCenterOf(pos).relative(front, 0.7)} with accuracy 6 for what is left.
     * levelEvents 1049/2010 and the {@code CRAFTER_RECIPE_CRAFTED} trigger have no lane here.
     */
    private void dispenseItem(BlockPos pos, ChestInventory inventory, HopperStack stack, int front) {
        if (stack.isEmpty()) return;
        HopperStack remaining = rt.hopperSystem().crafterInsert(pos, inventory, stack, front);
        if (remaining == null) remaining = stack;
        if (remaining.isEmpty()) return;
        behaviours().spawnItem(pos, remaining, front, DispenserRules.DEFAULT_ACCURACY);
        rt.mobSystem().levelEvent(EVENT_CRAFTER_CRAFT, pos.x(), pos.y(), pos.z(), 0);
        rt.mobSystem().levelEvent(EVENT_SHOOT_WHITE_SMOKE, pos.x(), pos.y(), pos.z(),
                HopperSystem.direction3d(front));
    }

    private void retry(BlockPos pos, long gameTime) {
        scheduled.putIfAbsent(pos, gameTime + 1);
        persist(pos, AnimalBlockTickPersistenceService.Kind.CRAFTER_TICK, scheduled.get(pos));
    }

    private static PlayerInventory.StackSnapshot snapshot(ChestInventory inventory, int slot) {
        short type = inventory.itemType(slot);
        int count = inventory.count(slot);
        if (type == PlayerInventory.EMPTY || count <= 0) return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(type, count, inventory.durability(slot),
                inventory.enchantments(slot), inventory.mapId(slot), inventory.shulkerId(slot),
                inventory.bucketMobData(slot), inventory.itemComponentData(slot));
    }

    private static HopperStack stack(PlayerInventory.StackSnapshot snapshot) {
        if (snapshot.isEmpty()) return HopperStack.EMPTY;
        return new HopperStack(snapshot.itemType(), snapshot.count(), snapshot.durability(),
                snapshot.enchantments(), snapshot.mapId(), snapshot.shulkerId(),
                snapshot.bucketMobData(), snapshot.itemComponentData());
    }
}
