package com.gameexpert.engine;

import com.gameexpert.engine.hopper.HopperContainer;
import com.gameexpert.engine.hopper.HopperCooldown;
import com.gameexpert.engine.hopper.HopperRules;
import com.gameexpert.engine.hopper.HopperStack;
import com.gameexpert.engine.hopper.HopperTransfer;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [HOPPER] Spring owner of {@code HopperBlockEntity}. Hopper contents live in the coordinate
 * {@link ChestStorage} as a five-slot row (the same durable {@code world_chests} lane as chests),
 * the per-hopper {@code cooldownTime}/{@code tickedGameTime} live here.
 *
 * <p>The owner loop runs at 10 Hz and every authority tick covers two game ticks, so each hopper
 * runs {@code pushItemsTick} twice per authority tick with the two absolute game times (the same
 * sub-stepping the brewing lane uses). Hoppers tick in ascending (x, y, z) order.
 *
 * <p>Neighbour containers are resolved exactly where vanilla reads them. A generated LOOT chest,
 * barrel or pot is first materialised through the canonical first-open writer, and a custom
 * dimension content chest through {@code openChest}; while a canonical request is in flight, or a
 * player's container settlement targets the same cell, the neighbour is present-but-closed.
 */
final class HopperSystem {

    /** Amortised chunk scans for generated hoppers per authority tick. */
    static final int CHUNK_SCANS_PER_TICK = 4;
    static final int MC_TICKS_PER_AUTHORITY_TICK = 2;

    private final WorldRuntime rt;
    private final Map<Long, LinkedHashSet<BlockPos>> hoppersByChunk = new HashMap<>();
    private final Set<Long> activeChunks = new HashSet<>();
    private final LinkedHashSet<Long> pendingScans = new LinkedHashSet<>();
    private final Map<BlockPos, HopperCooldown> cooldowns = new HashMap<>();
    private final Set<BlockPos> touchedCooldowns = new HashSet<>();
    private final Set<BlockPos> pendingLootOpens = new HashSet<>();
    private final LinkedHashSet<BlockPos> changedChests = new LinkedHashSet<>();
    private final LinkedHashSet<BlockPos> changedShelves = new LinkedHashSet<>();
    private final short[] scanTypes = new short[Blocks.CHUNK_BLOCKS];
    private final byte[] scanStates = new byte[Blocks.CHUNK_BLOCKS];

    HopperSystem(WorldRuntime rt) {
        this.rt = rt;
    }

    // ── index ────────────────────────────────────────────────────────────

    /** Single overlay funnel ({@code setIndexedOverlay}): a hopper appears or is replaced. */
    void record(int x, int y, int z, int blockType) {
        long key = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        BlockPos pos = new BlockPos(x, y, z);
        if (blockType == Blocks.HOPPER) {
            hoppersByChunk.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(pos);
            return;
        }
        LinkedHashSet<BlockPos> indexed = hoppersByChunk.get(key);
        if (indexed != null && indexed.remove(pos) && indexed.isEmpty()) {
            hoppersByChunk.remove(key);
        }
        // Setting a different block discards the block entity; a later hopper starts fresh.
        cooldowns.remove(pos);
        touchedCooldowns.remove(pos);
        rt.chestStorage().clearHopperCooldown(x, y, z);
    }

    /** Structure-ready activation (the furnace/brewing lanes activate at the same boundary). */
    void activateChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        activeChunks.add(key);
        pendingScans.add(key);
    }

    void deactivateChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        activeChunks.remove(key);
        pendingScans.remove(key);
        hoppersByChunk.remove(key);
    }

    HopperCooldown cooldownAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        touchedCooldowns.add(pos);
        return cooldowns.computeIfAbsent(pos, ignored -> {
            Integer saved = rt.chestStorage().hopperCooldownAt(x, y, z);
            // Vanilla restores TransferCooldown only; tickedGameTime begins at zero.
            return new HopperCooldown(saved == null ? HopperRules.NO_COOLDOWN_TIME : saved, 0);
        });
    }

    /** Test/diagnostic view of the hoppers that tick next. */
    List<BlockPos> tickingHoppers() {
        List<BlockPos> out = new ArrayList<>();
        for (long key : activeChunks) {
            LinkedHashSet<BlockPos> indexed = hoppersByChunk.get(key);
            if (indexed != null) out.addAll(indexed);
        }
        out.sort(HopperSystem::compare);
        return out;
    }

    private void scanPendingChunks() {
        int budget = CHUNK_SCANS_PER_TICK;
        var iterator = pendingScans.iterator();
        while (budget > 0 && iterator.hasNext()) {
            long key = iterator.next();
            iterator.remove();
            budget--;
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            TerrainAccessor.SnapshotSource source = rt.accessor().snapshotSource(chunkX, chunkZ);
            if (source == null) continue;
            source.copyCellsTo(scanTypes, scanStates);
            LinkedHashSet<BlockPos> found = hoppersByChunk.computeIfAbsent(
                    key, ignored -> new LinkedHashSet<>());
            int baseX = chunkX * Blocks.CHUNK_X;
            int baseZ = chunkZ * Blocks.CHUNK_Z;
            for (int lx = 0; lx < Blocks.CHUNK_X; lx++) {
                for (int lz = 0; lz < Blocks.CHUNK_Z; lz++) {
                    for (int y = Blocks.MIN_Y; y <= Blocks.MAX_Y; y++) {
                        if (Short.toUnsignedInt(scanTypes[Blocks.blockIndex(lx, y, lz)])
                                == Blocks.HOPPER) {
                            found.add(new BlockPos(baseX + lx, y, baseZ + lz));
                        }
                    }
                }
            }
            if (found.isEmpty()) hoppersByChunk.remove(key);
        }
    }

    // ── tick ─────────────────────────────────────────────────────────────

    /** One authority tick: two vanilla block-entity ticks for every active hopper. */
    void tick() {
        scanPendingChunks();
        List<BlockPos> hoppers = tickingHoppers();
        long endGameTime = rt.clock().gameTimeMcTicks();
        for (int step = MC_TICKS_PER_AUTHORITY_TICK - 1; step >= 0; step--) {
            long gameTime = endGameTime - step;
            for (BlockPos pos : hoppers) {
                tickHopper(pos, gameTime);
            }
        }
        for (BlockPos pos : touchedCooldowns) {
            HopperCooldown cooldown = cooldowns.get(pos);
            if (cooldown != null) rt.chestStorage().saveHopperCooldown(
                    pos.x(), pos.y(), pos.z(), cooldown.cooldownTime());
        }
        touchedCooldowns.clear();
        publishChanges();
    }

    private void tickHopper(BlockPos pos, long gameTime) {
        int block = WorldTickLoop.residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        if (block == WorldTickLoop.UNAVAILABLE_BLOCK) return;
        if (block != Blocks.HOPPER) {
            record(pos.x(), pos.y(), pos.z(), block);
            return;
        }
        // A player's menu settlement owns this inventory until it lands; do not race it.
        if (rt.containerSettlementTargets(pos.x(), pos.y(), pos.z())) return;
        int state = refreshEnabled(pos, rt.blockStates().get(pos.x(), pos.y(), pos.z(), Blocks.HOPPER));
        HopperAdapter hopper = hopperAt(pos);
        World world = new World(pos, state);
        ChestInventory.PersistenceSnapshot before = hopper.inventory.persistenceSnapshot();
        try {
            for (ItemEntity item : rt.itemSystem().hopperSuckCandidates(
                    pos.x(), pos.y(), pos.z(), true)) {
                HopperTransfer.entityInside(hopper, state, world, new ItemAdapter(item));
            }
            HopperTransfer.pushItemsTick(hopper, state, gameTime, world);
        } catch (MutationRejected rejected) {
            // The only fallible neighbour (a brewing stand's synchronous write) refused before it
            // changed. Undo the hopper side of the same move; the cooldown stays where the failed
            // attempt left it, exactly as a tick in which nothing moved.
            restore(hopper.inventory, before);
            rt.chestStorage().markDirty(pos.x(), pos.y(), pos.z());
        }
    }

    private static void restore(ChestInventory inventory, ChestInventory.PersistenceSnapshot before) {
        ChestInventory.Snapshot contents = before.snapshot();
        for (int slot = 0; slot < inventory.slots(); slot++) {
            int current = inventory.count(slot);
            if (current > 0) inventory.take(slot, current);
            short type = contents.itemTypes()[slot];
            int count = contents.counts()[slot];
            if (type == PlayerInventory.EMPTY || count <= 0) continue;
            inventory.putInSlot(slot, type, count, contents.durabilities()[slot],
                    contents.enchantments()[slot], contents.mapIds()[slot],
                    contents.shulkerIds()[slot], contents.bucketMobData()[slot],
                    contents.itemComponentData()[slot]);
        }
    }

    /**
     * {@code HopperBlock.checkPoweredState}: {@code enabled = !hasNeighborSignal(pos)}. Vanilla
     * runs it from {@code onPlace}/{@code neighborChanged}; this repository's only signal sources
     * (the poplar button and pressure plate) are re-read before each tick, which observes the
     * same edge no later than vanilla's next block-entity tick.
     */
    int refreshEnabled(BlockPos pos, int state) {
        boolean enabled = !rt.tickLoop().hopperHasNeighborSignal(pos.x(), pos.y(), pos.z());
        int normalized = HopperRules.normalizeState(state);
        int next = HopperRules.withEnabled(normalized, enabled);
        if (next != state) {
            rt.setBlockState(pos.x(), pos.y(), pos.z(), Blocks.HOPPER, next);
            rt.tickBlockChanges().put(pos, (short) Blocks.HOPPER);
        }
        return next;
    }

    private void publishChanges() {
        for (BlockPos pos : changedChests) rt.tickLoop().publishHopperChestChange(pos);
        for (BlockPos pos : changedShelves) rt.tickLoop().publishHopperShelfChange(pos);
        changedChests.clear();
        changedShelves.clear();
    }

    // ── neighbour resolution ─────────────────────────────────────────────

    private final class World implements HopperTransfer.World {
        private final BlockPos pos;
        private final int state;

        World(BlockPos pos, int state) {
            this.pos = pos;
            this.state = state;
        }

        @Override
        public HopperContainer attachedContainer() {
            int facing = HopperRules.facing(state);
            return containerAt(pos.x() + HopperRules.stepX(facing),
                    pos.y() + HopperRules.stepY(facing), pos.z() + HopperRules.stepZ(facing));
        }

        @Override
        public HopperContainer sourceContainer() {
            return containerAt(pos.x(), pos.y() + 1, pos.z());
        }

        @Override
        public boolean aboveBlocksSuction() {
            int above = WorldTickLoop.residentBlockType(rt.accessor(), pos.x(), pos.y() + 1, pos.z());
            if (above == WorldTickLoop.UNAVAILABLE_BLOCK) return true;
            int aboveState = rt.blockStates().get(pos.x(), pos.y() + 1, pos.z(), above);
            return BuildingBlockRules.isFullCollisionShape(above, aboveState)
                    && !HopperRules.doesNotBlockHoppers(above);
        }

        @Override
        public List<ItemAdapter> itemsInSuckArea() {
            List<ItemEntity> entities = rt.itemSystem().hopperSuckCandidates(
                    pos.x(), pos.y(), pos.z(), false);
            List<ItemAdapter> items = new ArrayList<>(entities.size());
            for (ItemEntity entity : entities) items.add(new ItemAdapter(entity));
            return items;
        }
    }

    /**
     * {@code HopperBlockEntity.getContainerAt}: the block container first ({@code
     * WorldlyContainerHolder}, then a {@code Container} block entity, a chest combined with its
     * partner while ignoring the blocked check). Entity containers are not modelled.
     */
    HopperContainer containerAt(int x, int y, int z) {
        int block = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
        if (block == WorldTickLoop.UNAVAILABLE_BLOCK) return HopperContainer.UNAVAILABLE;
        BlockPos pos = new BlockPos(x, y, z);
        if (block == Blocks.COMPOSTER) {
            int level = ComposterRules.level(rt.blockStates().get(x, y, z, Blocks.COMPOSTER));
            if (level == ComposterRules.READY_LEVEL) return new ComposterOutput(pos);
            return level < ComposterRules.MAX_FILL_LEVEL
                    ? new ComposterInput(pos, level) : new ComposterEmpty();
        }
        if (block == Blocks.HOPPER) {
            if (rt.containerSettlementTargets(x, y, z)) return HopperContainer.UNAVAILABLE;
            return hopperAt(pos);
        }
        if (FurnaceRules.isFurnace(block)) {
            if (rt.containerSettlementTargets(x, y, z)) return HopperContainer.UNAVAILABLE;
            FurnaceInventory existing = rt.furnaceStorage().peekAt(x, y, z);
            // A stale other-variant row is spilled by the furnace lane before anyone reads it.
            if (existing != null && existing.variant() != FurnaceVariant.of(block)) {
                return HopperContainer.UNAVAILABLE;
            }
            return new FurnaceAdapter(pos, block, existing);
        }
        if (block == Blocks.BREWING_STAND) {
            if (rt.containerSettlementTargets(x, y, z)) return HopperContainer.UNAVAILABLE;
            return new BrewingAdapter(pos);
        }
        if (block == Blocks.JUKEBOX) return new JukeboxAdapter(pos);
        if (Blocks.isShelf(block)) {
            if (rt.containerSettlementTargets(x, y, z)) return HopperContainer.UNAVAILABLE;
            ChestInventory shelf = rt.chestStorage().openAt(x, y, z,
                    com.gameexpert.engine.shelf.ShelfRules.SLOTS);
            return new InventoryAdapter(HopperRules.ContainerKind.GENERIC,
                    List.of(pos), List.of(shelf), null, true);
        }
        // [CONTAINER-MENUS] CrafterBlockEntity is a plain nine-slot Container whose canPlaceItem
        // refuses disabled slots and keeps the grid balanced (smallerStackExist).
        if (block == Blocks.CRAFTER) {
            if (rt.containerSettlementTargets(x, y, z)) return HopperContainer.UNAVAILABLE;
            ChestInventory crafter = rt.chestStorage().openAt(x, y, z,
                    com.gameexpert.engine.dispenser.CrafterRules.SLOTS);
            if (crafter.slots() != com.gameexpert.engine.dispenser.CrafterRules.SLOTS) {
                return HopperContainer.UNAVAILABLE;
            }
            return new InventoryAdapter(HopperRules.ContainerKind.GENERIC, List.of(pos),
                    List.of(crafter), null, false, rt.crafterDisabledSlots(x, y, z));
        }
        boolean chestShaped = block != Blocks.ENDER_CHEST && Blocks.isChestShaped(block);
        // DispenserBlockEntity (dispenser, dropper) is a plain nine-slot Container: every face
        // reaches every slot, exactly like a barrel.
        if (chestShaped || block == Blocks.BARREL || Blocks.isShulkerBox(block)
                || Blocks.isDecoratedPot(block) || block == Blocks.DISPENSER
                || block == Blocks.DROPPER) {
            List<BlockPos> halves = chestShaped
                    ? rt.tickLoop().hopperChestHalves(x, y, z, block) : List.of(pos);
            List<ChestInventory> inventories = new ArrayList<>(halves.size());
            for (BlockPos half : halves) {
                if (rt.containerSettlementTargets(half.x(), half.y(), half.z())) {
                    return HopperContainer.UNAVAILABLE;
                }
                ChestInventory inventory = rt.openContainer(half.x(), half.y(), half.z());
                if (inventory == null) {
                    requestLootMaterialization(pos, block, halves);
                    return HopperContainer.UNAVAILABLE;
                }
                inventories.add(inventory);
            }
            HopperRules.ContainerKind kind = Blocks.isShulkerBox(block)
                    ? HopperRules.ContainerKind.SHULKER_BOX : HopperRules.ContainerKind.GENERIC;
            return new InventoryAdapter(kind, halves, inventories, null, false);
        }
        // [CONTAINER-MENUS] HopperBlockEntity.getEntityContainer: a placed chest or hopper
        // minecart whose box intersects the cell when no block container is there.
        PlacedEntitySystem.Placed cart = rt.placedEntities().containerEntityAt(x, y, z);
        if (cart != null) {
            return new EntityCargoAdapter(HopperRules.ContainerKind.GENERIC, cart.cargo,
                    () -> rt.placedEntities().cargoChanged(cart));
        }
        return null;
    }

    /**
     * [CONTAINER-MENUS] {@code MinecartHopper#suckInItems}: {@code HopperBlockEntity.suckInItems}
     * with the cart as the hopper — the container above {@code (x, y + 0.5 + 1, z)} first, else the
     * item entities in the cart box inflated by 0.25 horizontally (a cart is not grid aligned, so
     * nothing above blocks the suction).
     */
    boolean minecartSuck(ChestInventory cargo, double x, double y, double z, double width,
            double height, Runnable changed) {
        EntityCargoAdapter hopper = new EntityCargoAdapter(HopperRules.ContainerKind.HOPPER, cargo,
                changed);
        double half = width / 2.0;
        HopperTransfer.World world = new HopperTransfer.World() {
            @Override
            public HopperContainer attachedContainer() {
                return null;
            }

            @Override
            public HopperContainer sourceContainer() {
                HopperContainer above = containerAt((int) Math.floor(x),
                        (int) Math.floor(y + 0.5 + 1.0), (int) Math.floor(z));
                return above == HopperContainer.UNAVAILABLE ? null : above;
            }

            @Override
            public boolean aboveBlocksSuction() {
                return false;
            }

            @Override
            public List<ItemAdapter> itemsInSuckArea() {
                List<ItemEntity> entities = rt.itemSystem().itemsIntersecting(
                        x - half - 0.25, y, z - half - 0.25, x + half + 0.25, y + height,
                        z + half + 0.25);
                List<ItemAdapter> items = new ArrayList<>(entities.size());
                for (ItemEntity entity : entities) items.add(new ItemAdapter(entity));
                return items;
            }
        };
        boolean moved;
        try {
            moved = HopperTransfer.suckInItems(hopper, world);
        } catch (MutationRejected rejected) {
            moved = false;
        }
        publishChanges();
        return moved;
    }

    /** [CONTAINER-MENUS] A placed minecart's cargo: chest storage, the cart's own change hook. */
    private final class EntityCargoAdapter extends InventoryAdapter {
        private final Runnable changed;

        EntityCargoAdapter(HopperRules.ContainerKind kind, ChestInventory cargo, Runnable changed) {
            super(kind, List.of(new BlockPos(0, 0, 0)), List.of(cargo), null, false);
            this.changed = changed;
        }

        @Override
        void touched(int slot) {
            changed.run();
        }
    }

    /**
     * {@code RandomizableContainerBlockEntity.unpackLootTable}: vanilla unpacks the table on the
     * hopper's first read. Here that is the durable canonical first-open writer, which completes
     * on a later owner turn; one request per cell is outstanding at a time.
     */
    void requestLootMaterialization(BlockPos clicked, int block, List<BlockPos> halves) {
        if (!pendingLootOpens.add(clicked)) return;
        long token = rt.requestCanonicalChestOpen(null, null, block, true, clicked, halves,
                completion -> pendingLootOpens.remove(clicked));
        if (token == 0L) pendingLootOpens.remove(clicked);
    }

    /**
     * [CONTAINER-MENUS] {@code DropperBlock#dispenseFrom}: when {@code getContainerAt(front)} is a
     * container, {@code HopperBlockEntity.addItem(dropper, container, stack.copyWithCount(1),
     * facing.getOpposite())}. Returns null when there is no container in front (the dropper then
     * dispenses the item as an entity), {@link HopperStack#EMPTY} when the one item moved and the
     * unchanged one-item stack when it did not. A container that is present but closed (loot in
     * flight, a player settlement on it) takes nothing.
     */
    HopperStack dropperInsert(BlockPos dropper, ChestInventory dropperInventory, HopperStack one,
            int dispenserFacing) {
        int fx = dropper.x() + com.gameexpert.engine.dispenser.DispenserRules.stepX(dispenserFacing);
        int fy = dropper.y() + com.gameexpert.engine.dispenser.DispenserRules.stepY(dispenserFacing);
        int fz = dropper.z() + com.gameexpert.engine.dispenser.DispenserRules.stepZ(dispenserFacing);
        HopperContainer destination = containerAt(fx, fy, fz);
        if (destination == null) return null;
        if (destination == HopperContainer.UNAVAILABLE) return one;
        HopperContainer source = new InventoryAdapter(HopperRules.ContainerKind.GENERIC,
                List.of(dropper), List.of(dropperInventory), null, false);
        int face = direction3d(com.gameexpert.engine.dispenser.DispenserRules.opposite(dispenserFacing));
        HopperStack remainder;
        try {
            remainder = HopperTransfer.addItem(source, destination, one, face);
        } catch (MutationRejected rejected) {
            return one;
        }
        publishChanges();
        return remainder;
    }

    /**
     * The container half of {@code CrafterBlock#dispenseItem}: into the container in front, one
     * item at a time (stopping at the first refusal) when that container is a crafter or the
     * stack exceeds {@code Container.getMaxStackSize(stack)}, otherwise the whole stack until an
     * {@code addItem} moves nothing. Returns {@code null} when no container is in front, else
     * what is left over.
     */
    HopperStack crafterInsert(BlockPos crafter, ChestInventory crafterInventory, HopperStack stack,
            int dispenserFacing) {
        int fx = crafter.x() + com.gameexpert.engine.dispenser.DispenserRules.stepX(dispenserFacing);
        int fy = crafter.y() + com.gameexpert.engine.dispenser.DispenserRules.stepY(dispenserFacing);
        int fz = crafter.z() + com.gameexpert.engine.dispenser.DispenserRules.stepZ(dispenserFacing);
        HopperContainer destination = containerAt(fx, fy, fz);
        if (destination == null) return null;
        if (destination == HopperContainer.UNAVAILABLE) return stack;
        HopperContainer source = new InventoryAdapter(HopperRules.ContainerKind.GENERIC,
                List.of(crafter), List.of(crafterInventory), null, false,
                rt.crafterDisabledSlots(crafter.x(), crafter.y(), crafter.z()));
        int face = direction3d(com.gameexpert.engine.dispenser.DispenserRules.opposite(dispenserFacing));
        boolean oneByOne = WorldTickLoop.residentBlockType(rt.accessor(), fx, fy, fz) == Blocks.CRAFTER
                || stack.count() > Math.min(destination.maxStackSize(), stack.maxStackSize());
        HopperStack remaining = stack;
        try {
            while (!remaining.isEmpty()) {
                if (oneByOne) {
                    HopperStack result = HopperTransfer.addItem(
                            source, destination, remaining.withCount(1), face);
                    if (!result.isEmpty()) break;
                    remaining = remaining.withCount(remaining.count() - 1);
                } else {
                    int before = remaining.count();
                    remaining = HopperTransfer.addItem(source, destination, remaining, face);
                    if (remaining.count() == before) break;
                }
            }
        } catch (MutationRejected rejected) {
            // The destination refused mid-way; what was already moved stays moved.
        }
        publishChanges();
        return remaining;
    }

    /**
     * [CONTAINER-MENUS] Whether the container in front of a dropper/crafter exists but is not yet
     * readable (a LOOT first open or a player settlement in flight). Vanilla unpacks the loot
     * synchronously; here the caller waits a tick. Requests the materialisation.
     */
    boolean frontContainerPending(BlockPos source, int dispenserFacing) {
        int fx = source.x() + com.gameexpert.engine.dispenser.DispenserRules.stepX(dispenserFacing);
        int fy = source.y() + com.gameexpert.engine.dispenser.DispenserRules.stepY(dispenserFacing);
        int fz = source.z() + com.gameexpert.engine.dispenser.DispenserRules.stepZ(dispenserFacing);
        if (fy < Blocks.MIN_Y || fy > Blocks.MAX_Y) return false;
        int block = WorldTickLoop.residentBlockType(rt.accessor(), fx, fy, fz);
        if (block == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
        return containerAt(fx, fy, fz) == HopperContainer.UNAVAILABLE;
    }

    /** Dispenser facing code (0 N, 1 E, 2 S, 3 W, 4 up, 5 down) to {@code Direction#get3DDataValue}. */
    static int direction3d(int dispenserFacing) {
        return switch (dispenserFacing) {
            case com.gameexpert.engine.dispenser.DispenserRules.NORTH -> HopperRules.NORTH;
            case com.gameexpert.engine.dispenser.DispenserRules.SOUTH -> HopperRules.SOUTH;
            case com.gameexpert.engine.dispenser.DispenserRules.WEST -> HopperRules.WEST;
            case com.gameexpert.engine.dispenser.DispenserRules.EAST -> HopperRules.EAST;
            case com.gameexpert.engine.dispenser.DispenserRules.UP -> HopperRules.UP;
            default -> HopperRules.DOWN;
        };
    }

    boolean lootMaterializationPending(int x, int y, int z) {
        return pendingLootOpens.contains(new BlockPos(x, y, z));
    }

    private HopperAdapter hopperAt(BlockPos pos) {
        ChestInventory inventory = rt.chestStorage().openAt(
                pos.x(), pos.y(), pos.z(), HopperRules.CONTAINER_SIZE);
        return new HopperAdapter(pos, inventory, cooldownAt(pos.x(), pos.y(), pos.z()));
    }

    // ── adapters ─────────────────────────────────────────────────────────

    static HopperStack stackAt(ChestInventory inventory, int slot) {
        short type = inventory.itemType(slot);
        int count = inventory.count(slot);
        if (type == PlayerInventory.EMPTY || count <= 0) return HopperStack.EMPTY;
        return new HopperStack(type, count, inventory.durability(slot),
                inventory.enchantments(slot), inventory.mapId(slot), inventory.shulkerId(slot),
                inventory.bucketMobData(slot), inventory.itemComponentData(slot));
    }

    private static int put(ChestInventory inventory, int slot, HopperStack stack, int amount) {
        return inventory.putInSlot(slot, stack.itemType(), amount, stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData());
    }

    /** Coordinate {@link ChestInventory} halves: chests, barrels, shulkers, pots, shelves, hopper. */
    private class InventoryAdapter implements HopperContainer {
        final HopperRules.ContainerKind kind;
        final List<BlockPos> positions;
        final List<ChestInventory> inventories;
        final HopperCooldown cooldown;
        final boolean shelf;
        final int size;
        /** Disabled-slot mask of a crafter, or -1 for every other container. */
        final int crafterDisabled;

        InventoryAdapter(HopperRules.ContainerKind kind, List<BlockPos> positions,
                List<ChestInventory> inventories, HopperCooldown cooldown, boolean shelf) {
            this(kind, positions, inventories, cooldown, shelf, -1);
        }

        InventoryAdapter(HopperRules.ContainerKind kind, List<BlockPos> positions,
                List<ChestInventory> inventories, HopperCooldown cooldown, boolean shelf,
                int crafterDisabled) {
            this.crafterDisabled = crafterDisabled;
            this.kind = kind;
            this.positions = positions;
            this.inventories = inventories;
            this.cooldown = cooldown;
            this.shelf = shelf;
            int total = 0;
            for (ChestInventory inventory : inventories) total += inventory.slots();
            this.size = total;
        }

        private int half(int slot) {
            return slot < inventories.getFirst().slots() ? 0 : 1;
        }

        private int local(int slot) {
            return half(slot) == 0 ? slot : slot - inventories.getFirst().slots();
        }

        @Override public HopperRules.ContainerKind kind() { return kind; }
        @Override public int size() { return size; }

        @Override
        public HopperStack get(int slot) {
            return stackAt(inventories.get(half(slot)), local(slot));
        }

        /**
         * Chest-schema storage persists every identity column, so {@code Container.canPlaceItem}
         * stays the vanilla {@code true}; {@code ListBackedContainer} (the shelf) additionally
         * requires an empty slot or a count below the stack limit.
         */
        @Override
        public boolean canPlaceItem(int slot, HopperStack stack) {
            ChestInventory inventory = inventories.get(half(slot));
            int local = local(slot);
            boolean representable = inventory.roomForSlot(local, stack.itemType(),
                    stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData()) > 0
                    || inventory.itemType(local) != PlayerInventory.EMPTY;
            if (!representable) return false;
            if (crafterDisabled >= 0) return crafterCanPlaceItem(slot);
            if (!shelf) return true;
            HopperStack current = get(slot);
            return current.isEmpty() || current.count() < Math.min(maxStackSize(),
                    stack.maxStackSize());
        }

        @Override
        public void setItem(int slot, HopperStack stack) {
            if (put(inventories.get(half(slot)), local(slot), stack, stack.count())
                    != stack.count()) {
                throw new IllegalStateException("hopper setItem rejected");
            }
            touched(slot);
        }

        @Override
        public void grow(int slot, int amount) {
            HopperStack current = get(slot);
            if (put(inventories.get(half(slot)), local(slot), current, amount) != amount) {
                throw new IllegalStateException("hopper grow rejected");
            }
            touched(slot);
        }

        @Override
        public void removeOne(int slot) {
            if (inventories.get(half(slot)).take(local(slot), 1) != 1) {
                throw new IllegalStateException("hopper remove rejected");
            }
            touched(slot);
        }

        /** {@link com.gameexpert.engine.dispenser.CrafterRules#canPlaceItem} over this grid. */
        private boolean crafterCanPlaceItem(int slot) {
            int[] counts = new int[size];
            int[] maxStacks = new int[size];
            boolean[] sameAs = new boolean[size];
            HopperStack current = get(slot);
            for (int other = 0; other < size; other++) {
                HopperStack stack = get(other);
                counts[other] = stack.isEmpty() ? 0 : stack.count();
                maxStacks[other] = stack.isEmpty() ? 1 : stack.maxStackSize();
                sameAs[other] = !stack.isEmpty() && !current.isEmpty()
                        && HopperStack.isSameItemSameComponents(stack, current);
            }
            return com.gameexpert.engine.dispenser.CrafterRules.canPlaceItem(
                    crafterDisabled, slot, counts, maxStacks, sameAs);
        }

        void touched(int slot) {
            BlockPos pos = positions.get(half(slot));
            rt.chestStorage().markDirty(pos.x(), pos.y(), pos.z());
            if (shelf) changedShelves.add(pos);
            else changedChests.add(positions.getFirst());
        }

        @Override public void setChanged() { }

        @Override public HopperCooldown hopper() { return cooldown; }
    }

    private final class HopperAdapter extends InventoryAdapter {
        final ChestInventory inventory;

        HopperAdapter(BlockPos pos, ChestInventory inventory, HopperCooldown cooldown) {
            super(HopperRules.ContainerKind.HOPPER, List.of(pos), List.of(inventory),
                    cooldown, false);
            this.inventory = inventory;
        }
    }

    /** {@code AbstractFurnaceBlockEntity} over the repository's type+count furnace storage. */
    private final class FurnaceAdapter implements HopperContainer {
        final BlockPos pos;
        final FurnaceVariant variant;
        /** Read without creating a row; the first write opens the coordinate furnace. */
        FurnaceInventory furnace;

        FurnaceAdapter(BlockPos pos, int block, FurnaceInventory existing) {
            this.pos = pos;
            this.variant = FurnaceVariant.of(block);
            this.furnace = existing;
        }

        private FurnaceInventory writable() {
            if (furnace == null) {
                furnace = rt.furnaceStorage().openAt(pos.x(), pos.y(), pos.z(), variant);
            }
            return furnace;
        }

        @Override public HopperRules.ContainerKind kind() { return HopperRules.ContainerKind.FURNACE; }
        @Override public int size() { return FurnaceInventory.SLOTS; }

        @Override
        public HopperStack get(int slot) {
            if (furnace == null) return HopperStack.EMPTY;
            return HopperStack.of(furnace.itemType(slot), furnace.count(slot));
        }

        /**
         * Vanilla {@code canPlaceItem} narrowed to the repository furnace rows: they persist only
         * type and count, the input slot holds a smeltable of this variant, the fuel slot a
         * burnable (an empty bucket has no fuel row).
         */
        @Override
        public boolean canPlaceItem(int slot, HopperStack stack) {
            if (!HopperRules.furnaceCanPlaceItem(slot, stack, get(FurnaceInventory.FUEL_SLOT))
                    || !FurnaceInventory.isTypeCountCarrier(new PlayerInventory.StackSnapshot(
                            stack.itemType(), stack.count(), stack.durability(), stack.enchantments(),
                            stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                            stack.itemComponentData()))) return false;
            HopperStack current = get(slot);
            if (!current.isEmpty()) return true;
            FurnaceInventory probe = furnace != null ? furnace : new FurnaceInventory(variant);
            return probe.roomFor(slot, stack.itemType()) > 0;
        }

        @Override
        public void setItem(int slot, HopperStack stack) {
            if (writable().add(slot, stack.itemType(), stack.count()) != stack.count()) {
                throw new IllegalStateException("furnace setItem rejected");
            }
            touched();
        }

        @Override
        public void grow(int slot, int amount) {
            if (writable().add(slot, furnace.itemType(slot), amount) != amount) {
                throw new IllegalStateException("furnace grow rejected");
            }
            touched();
        }

        @Override
        public void removeOne(int slot) {
            if (writable().take(slot, 1) != 1) {
                throw new IllegalStateException("furnace take rejected");
            }
            touched();
        }

        private void touched() {
            FurnaceStorage storage = rt.furnaceStorage();
            storage.markDirty(pos.x(), pos.y(), pos.z());
            storage.activate(pos.x(), pos.y(), pos.z());
            rt.tickLoop().publishHopperFurnaceChange(pos, furnace);
        }

        @Override public void setChanged() { }
    }

    /**
     * {@code BrewingStandBlockEntity} in vanilla slot numbering. Every write is one synchronous
     * revision-checked brewing row write (the brewing tick lane's {@code persistTick}); a stale
     * row aborts the move before either side changes.
     */
    private final class BrewingAdapter implements HopperContainer {
        final BlockPos pos;
        /** Read without creating a row; the first write opens the coordinate stand. */
        BrewingInventory live;

        BrewingAdapter(BlockPos pos) {
            this.pos = pos;
            this.live = rt.brewingStorage().peekAt(pos.x(), pos.y(), pos.z());
        }

        @Override public HopperRules.ContainerKind kind() {
            return HopperRules.ContainerKind.BREWING_STAND;
        }
        @Override public int size() { return BrewingInventory.SLOTS; }

        @Override
        public HopperStack get(int slot) {
            if (live == null) return HopperStack.EMPTY;
            int stored = HopperRules.brewingStorageSlot(slot);
            // [BREWING-26.3] 범용 물약의 potionContents 성분도 칸의 정체성이라 함께 싣는다.
            if (live.count(stored) == 0) return HopperStack.EMPTY;
            return new HopperStack(live.itemType(stored), live.count(stored), live.durability(stored), 0L, 0, 0, null,
                    live.itemComponentData(stored));
        }

        @Override
        public boolean canPlaceItem(int slot, HopperStack stack) {
            // 바닐라 양조대는 물약 내용물 같은 컴포넌트를 거부하지 않는다. 내구·인챈트·지도 등
            // 이 저장소의 다른 스택 정체성 축은 양조대 칸에 담기지 않으므로 비어 있어야 한다.
            return stack.durability() == PlayerInventory.initialDurability(stack.itemType())
                    && stack.enchantments() == 0L && stack.mapId() == 0
                    && stack.shulkerId() == 0 && stack.bucketMobData() == null
                    && HopperRules.brewingCanPlaceItem(slot, stack, get(slot));
        }

        @Override
        public void setItem(int slot, HopperStack stack) {
            commit(planned -> planned.put(HopperRules.brewingStorageSlot(slot), stack.itemType(),
                    stack.count(), stack.durability(), 0L, 0, 0, null, stack.itemComponentData()) == stack.count());
        }

        @Override
        public void grow(int slot, int amount) {
            int stored = HopperRules.brewingStorageSlot(slot);
            String components = live.itemComponentData(stored);
            commit(planned -> planned.put(stored, live.itemType(stored), amount, live.durability(stored), 0L, 0, 0,
                    null, components) == amount);
        }

        @Override
        public void removeOne(int slot) {
            int stored = HopperRules.brewingStorageSlot(slot);
            commit(planned -> planned.take(stored, 1) == 1);
        }

        private void commit(java.util.function.Predicate<BrewingInventory> mutation) {
            if (live == null) live = rt.brewingStorage().openAt(pos.x(), pos.y(), pos.z());
            BrewingInventory planned = BrewingInventory.fromSnapshot(live.snapshot());
            if (!mutation.test(planned)) throw new IllegalStateException("brewing move rejected");
            short[] types = planned.itemTypesSnapshot();
            int[] counts = planned.countsSnapshot();
            String[] components = planned.componentsSnapshot();
            // BrewingStandBlockEntity.serverTick zeroes brewTime once the stand stops being
            // brewable (a bottle or the ingredient left); the durable row cannot hold the
            // half-state in between, so the cancel lands with the move.
            int brewTicks = planned.brewTicks();
            short ingredient = planned.brewingIngredient();
            if (brewTicks > 0 && (ingredient != types[BrewingInventory.INGREDIENT_SLOT]
                    || !BrewingInventory.canBrew(ingredient, types, components))) {
                brewTicks = 0;
                ingredient = PlayerInventory.EMPTY;
            }
            BrewingInventory committed = new BrewingInventory();
            committed.restore(types, counts, components, planned.fuel(), brewTicks, ingredient);
            committed.restorePersistenceRevision(live.persistenceRevision() + 1);
            var target = new InventoryMutationTarget.Brewing(
                    new InventoryMutationTarget.Position(pos.x(), pos.y(), pos.z()), types, counts,
                    components, committed.fuel(), committed.brewTicks(),
                    committed.brewingIngredient(), committed.persistenceRevision());
            boolean accepted = rt.brewingPersistence() == null
                    || rt.brewingPersistence().persistTick(rt.worldId(),
                            live.persistenceRevision(), target)
                            != com.gameexpert.brewing.service.BrewingPersistenceService.Outcome.STALE;
            if (!accepted || rt.brewingStorage().peekAt(pos.x(), pos.y(), pos.z()) != live) {
                throw new MutationRejected();
            }
            rt.brewingStorage().load(pos.x(), pos.y(), pos.z(), committed);
            live = committed;
            if (committed.needsTick()) rt.brewingStorage().activate(pos.x(), pos.y(), pos.z());
            rt.tickLoop().publishHopperBrewingChange(pos, committed);
        }

        @Override public void setChanged() { }
    }

    /**
     * [JUKEBOX] {@code JukeboxBlockEntity} as a {@code ContainerSingleItem}: max stack 1,
     * {@code canPlaceItem} = a {@code JUKEBOX_PLAYABLE} item (any components) into the empty slot
     * (setItem plays it), {@code canTakeItem(target, …)} = the receiving container has an empty slot.
     * The only receiver modelled here is the hopper directly below (the one that sucks from above).
     * The disc stack lives in the jukebox block entity ({@code WorldTickLoop.jukeboxDisc}).
     */
    private final class JukeboxAdapter implements HopperContainer {
        final BlockPos pos;

        JukeboxAdapter(BlockPos pos) {
            this.pos = pos;
        }

        private PlayerInventory.StackSnapshot disc() {
            return rt.tickLoop().jukeboxDisc(pos.x(), pos.y(), pos.z());
        }

        @Override public HopperRules.ContainerKind kind() { return HopperRules.ContainerKind.GENERIC; }
        @Override public int size() { return 1; }
        @Override public int maxStackSize() { return 1; }
        @Override public HopperStack get(int slot) {
            PlayerInventory.StackSnapshot disc = disc();
            return disc.isEmpty() ? HopperStack.EMPTY : new HopperStack(disc.itemType(), 1,
                    disc.durability(), disc.enchantments(), disc.mapId(), disc.shulkerId(),
                    disc.bucketMobData(), disc.itemComponentData());
        }

        @Override
        public boolean canPlaceItem(int slot, HopperStack stack) {
            return disc().isEmpty() && !stack.isEmpty()
                    && com.gameexpert.engine.jukebox.JukeboxRules.isMusicDisc(stack.itemType());
        }

        @Override
        public boolean canTakeItem(int slot, HopperStack stack) {
            HopperContainer below = containerAt(pos.x(), pos.y() - 1, pos.z());
            if (below.kind() != HopperRules.ContainerKind.HOPPER) return false;
            for (int index = 0; index < below.size(); index++) {
                if (below.get(index).isEmpty()) return true;
            }
            return false;
        }

        @Override
        public void setItem(int slot, HopperStack stack) {
            PlayerInventory.StackSnapshot disc = new PlayerInventory.StackSnapshot(stack.itemType(), 1,
                    stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData());
            if (!rt.tickLoop().insertJukeboxDisc(pos.x(), pos.y(), pos.z(), disc)) {
                throw new MutationRejected();
            }
        }

        @Override public void grow(int slot, int amount) {
            throw new IllegalStateException("jukebox holds a single disc");
        }

        @Override
        public void removeOne(int slot) {
            if (rt.tickLoop().takeJukeboxDisc(pos.x(), pos.y(), pos.z()).isEmpty()) {
                throw new MutationRejected();
            }
        }

        @Override public void setChanged() { }
    }

    /** {@code ComposterBlock$InputContainer}: one compostable item, top face only. */
    private final class ComposterInput implements HopperContainer {
        final BlockPos pos;
        final int level;
        boolean changed;

        ComposterInput(BlockPos pos, int level) {
            this.pos = pos;
            this.level = level;
        }

        @Override public HopperRules.ContainerKind kind() {
            return HopperRules.ContainerKind.COMPOSTER_INPUT;
        }
        @Override public int size() { return 1; }
        @Override public int maxStackSize() { return 1; }
        @Override public HopperStack get(int slot) { return HopperStack.EMPTY; }
        @Override public boolean canPlaceItem(int slot, HopperStack stack) { return true; }
        @Override public boolean composterChanged() { return changed; }

        /**
         * {@code SimpleContainer.setItem} fires {@code setChanged}, which composts the item
         * ({@code addLayer}), plays {@code levelEvent 1500} and removes it again.
         */
        @Override
        public void setItem(int slot, HopperStack stack) {
            if (stack.isEmpty() || !ComposterRules.isCompostable(stack.itemType())) return;
            changed = true;
            int state = ComposterRules.level(level);
            int next = ComposterRules.levelAfterInsert(state, stack.itemType(),
                    rt.tickLoop().hopperComposterRoll());
            if (next != state) {
                rt.setBlockState(pos.x(), pos.y(), pos.z(), Blocks.COMPOSTER, next);
                rt.tickBlockChanges().put(pos, (short) Blocks.COMPOSTER);
            }
            rt.tickLoop().publishHopperComposterFill(pos, next);
        }

        @Override public void grow(int slot, int amount) {
            throw new IllegalStateException("composter input never merges");
        }
        @Override public void removeOne(int slot) {
            throw new IllegalStateException("composter input is not a source");
        }
        @Override public void setChanged() { }
    }

    /** {@code ComposterBlock$OutputContainer}: one bone meal, bottom face only. */
    private final class ComposterOutput implements HopperContainer {
        final BlockPos pos;
        boolean changed;
        boolean taken;

        ComposterOutput(BlockPos pos) {
            this.pos = pos;
        }

        @Override public HopperRules.ContainerKind kind() {
            return HopperRules.ContainerKind.COMPOSTER_OUTPUT;
        }
        @Override public int size() { return 1; }
        @Override public int maxStackSize() { return 1; }
        @Override public HopperStack get(int slot) {
            return taken ? HopperStack.EMPTY : HopperStack.of(PlayerInventory.BONE_MEAL, 1);
        }
        @Override public boolean canPlaceItem(int slot, HopperStack stack) { return true; }
        @Override public boolean composterChanged() { return changed; }
        @Override public void setItem(int slot, HopperStack stack) {
            throw new IllegalStateException("composter output refuses every face");
        }
        @Override public void grow(int slot, int amount) {
            throw new IllegalStateException("composter output refuses every face");
        }

        /** {@code SimpleContainer.removeItem} fires {@code setChanged}: {@code ComposterBlock.empty}. */
        @Override
        public void removeOne(int slot) {
            taken = true;
            empty();
        }

        /** The failed take's {@code removeItem} already emptied the composter. */
        @Override
        public void failedRemoveAttempt(int slot) {
            empty();
        }

        private void empty() {
            changed = true;
            if (rt.blockStates().get(pos.x(), pos.y(), pos.z(), Blocks.COMPOSTER) != 0) {
                rt.setBlockState(pos.x(), pos.y(), pos.z(), Blocks.COMPOSTER, 0);
                rt.tickBlockChanges().put(pos, (short) Blocks.COMPOSTER);
            }
        }

        @Override public void setChanged() { }
    }

    /** {@code ComposterBlock$EmptyContainer} (level 7). */
    private static final class ComposterEmpty implements HopperContainer {
        @Override public HopperRules.ContainerKind kind() {
            return HopperRules.ContainerKind.COMPOSTER_EMPTY;
        }
        @Override public int size() { return 0; }
        @Override public HopperStack get(int slot) { return HopperStack.EMPTY; }
        @Override public boolean canPlaceItem(int slot, HopperStack stack) { return false; }
        @Override public void setItem(int slot, HopperStack stack) {
            throw new IllegalStateException("empty composter container");
        }
        @Override public void grow(int slot, int amount) {
            throw new IllegalStateException("empty composter container");
        }
        @Override public void removeOne(int slot) {
            throw new IllegalStateException("empty composter container");
        }
        @Override public void setChanged() { }
    }

    private final class ItemAdapter implements HopperTransfer.Item {
        final ItemEntity entity;

        ItemAdapter(ItemEntity entity) {
            this.entity = entity;
        }

        @Override
        public HopperStack stack() {
            if (entity.count <= 0) return HopperStack.EMPTY;
            return new HopperStack(entity.itemType, entity.count, entity.durability,
                    entity.enchantments, entity.mapId, entity.shulkerId,
                    entity.bucketMobData, entity.itemComponentData);
        }

        @Override
        public void setStack(HopperStack remaining) {
            rt.itemSystem().applyHopperAbsorption(entity, remaining.isEmpty() ? 0 : remaining.count());
        }
    }

    private static final class MutationRejected extends RuntimeException {
        MutationRejected() {
            super("hopper neighbour write was stale", null, false, false);
        }
    }

    private static int compare(BlockPos a, BlockPos b) {
        int order = Integer.compare(a.x(), b.x());
        if (order == 0) order = Integer.compare(a.y(), b.y());
        return order == 0 ? Integer.compare(a.z(), b.z()) : order;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
    }
}
