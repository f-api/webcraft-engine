package com.gameexpert.engine;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.placed.dto.PlacedEntitySnapshot;
import com.gameexpert.placed.service.PlacedEntityPersistenceService;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.web.socket.WebSocketSession;

/**
 * [CONTAINER-MENUS] Player-placed armor stands and minecarts (26.3-snapshot-7 javap
 * {@code ArmorStand}, {@code ArmorStandItem}, {@code AbstractMinecart}, {@code OldMinecartBehavior},
 * {@code Minecart}, {@code MinecartChest}, {@code MinecartHopper}, {@code MinecartFurnace},
 * {@code MinecartTNT}, {@code MinecartItem}, {@code MinecartDispenseItemBehavior}). The
 * {@code minecart_improvements} datapack is experimental, so carts run the old behaviour.
 *
 * <p>The authority owns the physics: it runs two unscaled 20 TPS entity steps per 10 TPS tick,
 * exactly like {@link PrimedTntSystem}. Rails are the repository's plain rail (10 {@code RailShape}
 * states); powered, detector and activator rails exist only as items, so those branches of
 * {@code moveAlongTrack} never run. A rider's position is client-authoritative like every player's:
 * the rider's client pins itself to the broadcast cart and the authority only reads its move intent.
 *
 * <p>Owner-thread only. Client actions arrive through the ordered player action queue
 * ({@link PlayerAction.PlacedEntityCommand}); persistence follows {@link BoatSystem}: the live set
 * is the stored set, written asynchronously on change and every 50 ticks while carts move.
 */
final class PlacedEntitySystem {

    /** Protocol kinds with the entity dimensions of {@code EntityTypes}. */
    enum Kind {
        ARMOR_STAND("armor_stand", PlayerInventory.ARMOR_STAND, 0.5, 1.975, 0),
        MINECART("minecart", PlayerInventory.MINECART, 0.98, 0.7, 0),
        CHEST_MINECART("chest_minecart", PlayerInventory.CHEST_MINECART, 0.98, 0.7, 27),
        HOPPER_MINECART("hopper_minecart", PlayerInventory.HOPPER_MINECART, 0.98, 0.7, 5),
        FURNACE_MINECART("furnace_minecart", PlayerInventory.FURNACE_MINECART, 0.98, 0.7, 0),
        TNT_MINECART("tnt_minecart", PlayerInventory.TNT_MINECART, 0.98, 0.7, 0);

        final String protocol;
        final short item;
        final double width;
        final double height;
        final int cargoSlots;

        Kind(String protocol, short item, double width, double height, int cargoSlots) {
            this.protocol = protocol;
            this.item = item;
            this.width = width;
            this.height = height;
            this.cargoSlots = cargoSlots;
        }

        boolean minecart() {
            return this != ARMOR_STAND;
        }

        static Kind forItem(short item) {
            for (Kind kind : values()) if (kind.item == item) return kind;
            return null;
        }

        static Kind forProtocol(String name) {
            for (Kind kind : values()) if (kind.protocol.equals(name)) return kind;
            return null;
        }
    }

    // ── vanilla constants ────────────────────────────────────────────────
    static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    /** {@code AbstractMinecart.getDefaultGravity}: 0.04, 0.005 in water. */
    static final double CART_GRAVITY = 0.04;
    static final double CART_WATER_GRAVITY = 0.005;
    /** {@code OldMinecartBehavior.getMaxSpeed}: 0.4, 0.2 in water (furnace: half). */
    static final double CART_MAX_SPEED = 0.4;
    static final double CART_WATER_MAX_SPEED = 0.2;
    static final double CART_SLOPE_ACCELERATION = 0.0078125;
    static final double CART_AIR_DRAG = 0.95;
    static final float CART_DESTROY_DAMAGE = 40.0F;
    static final int FURNACE_FUEL_PER_ITEM = 3600;
    static final int FURNACE_MAX_FUEL = 32000;
    static final int TNT_FUSE = 80;
    /** {@code LivingEntity} gravity and air drag an armor stand falls with. */
    static final double STAND_GRAVITY = 0.08;
    static final double STAND_VERTICAL_DRAG = 0.98;
    static final double STAND_AIR_FRICTION = 0.91;
    static final double STAND_GROUND_FRICTION = 0.6 * 0.91;
    /** {@code ArmorStand.WOBBLE_TIME}. */
    static final long ARMOR_STAND_WOBBLE_MC_TICKS = 5L;
    /** Equipment slot order of {@code EquipmentSlot}: main hand, off hand, feet, legs, chest, head. */
    static final int MAINHAND = 0;
    static final int OFFHAND = 1;
    static final int FEET = 2;
    static final int LEGS = 3;
    static final int CHEST = 4;
    static final int HEAD = 5;
    static final int FLAG_SMALL = 1;
    static final int FLAG_SHOW_ARMS = 4;
    static final int CART_FLAG_FLIPPED = 1;
    static final int CART_FLAG_HOPPER_DISABLED = 2;
    private static final double POS_EPS = 1.0e-3;
    private static final double COLLISION_EPSILON = 1.0e-7;
    /** {@code AbstractMinecart.exits} by {@code RailShape} ordinal (the repository's rail state). */
    private static final int[][][] EXITS = {
            {{0, 0, -1}, {0, 0, 1}},    // NORTH_SOUTH
            {{-1, 0, 0}, {1, 0, 0}},    // EAST_WEST
            {{-1, -1, 0}, {1, 0, 0}},   // ASCENDING_EAST
            {{-1, 0, 0}, {1, -1, 0}},   // ASCENDING_WEST
            {{0, 0, -1}, {0, -1, 1}},   // ASCENDING_NORTH
            {{0, -1, -1}, {0, 0, 1}},   // ASCENDING_SOUTH
            {{0, 0, 1}, {1, 0, 0}},     // SOUTH_EAST
            {{0, 0, 1}, {-1, 0, 0}},    // SOUTH_WEST
            {{0, 0, -1}, {-1, 0, 0}},   // NORTH_WEST
            {{0, 0, -1}, {1, 0, 0}},    // NORTH_EAST
    };

    /** One live armor stand or minecart. */
    static final class Placed {
        final long id;
        final Kind kind;
        double x;
        double y;
        double z;
        double xo;
        double yo;
        double zo;
        double vx;
        double vy;
        double vz;
        float yaw;
        float pitch;
        boolean flipped;
        boolean onGround;
        boolean horizontalCollision;
        boolean removed;
        // VehicleEntity
        int hurtTime;
        int hurtDir = 1;
        float damage;
        String rider;
        long mobPassengerId;
        double inputX;
        double inputZ;
        // ArmorStand
        final PlayerInventory.StackSnapshot[] equipment = new PlayerInventory.StackSnapshot[6];
        int flags;
        long lastHitMcTick = Long.MIN_VALUE / 4;
        float health = 20.0F;
        int fireTicks;
        // AbstractMinecartContainer / MinecartHopper
        final ChestInventory cargo;
        boolean hopperEnabled = true;
        // MinecartFurnace
        int fuel;
        double pushX;
        double pushZ;
        // MinecartTNT
        int fuse = -1;
        // bookkeeping
        WsMessages.PlacedEntityDto sent;
        double storedX = Double.NaN;
        double storedY = Double.NaN;
        double storedZ = Double.NaN;

        Placed(long id, Kind kind, double x, double y, double z, float yaw) {
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xo = x;
            this.yo = y;
            this.zo = z;
            this.yaw = yaw;
            java.util.Arrays.fill(equipment, PlayerInventory.StackSnapshot.EMPTY);
            this.cargo = kind.cargoSlots > 0 ? new ChestInventory(kind.cargoSlots) : null;
        }

        double halfWidth() {
            return kind.width / 2.0;
        }

        boolean hasFuel() {
            return fuel > 0;
        }

        boolean primed() {
            return fuse > -1;
        }
    }

    private final WorldRuntime rt;
    private final Random random;
    private final Map<Long, Placed> entities = new LinkedHashMap<>();
    private final List<Placed> tickSnapshot = new ArrayList<>();
    private final Set<String> synced = new HashSet<>();
    /** Open cargo menu per player nickname. */
    private final Map<String, Long> openCargo = new HashMap<>();
    private long nextId = 1;

    private PlacedEntityPersistenceService persistence;
    private final ConcurrentLinkedQueue<long[]> persistenceCompletions = new ConcurrentLinkedQueue<>();
    private long persistenceRevision;
    private boolean persistenceDirty;
    private boolean persistenceInFlight;

    PlacedEntitySystem(WorldRuntime rt) {
        this(rt, new Random());
    }

    PlacedEntitySystem(WorldRuntime rt, Random random) {
        this.rt = rt;
        this.random = random;
    }

    // ── persistence ──────────────────────────────────────────────────────

    void installPersistence(PlacedEntityPersistenceService service) {
        persistence = service;
        if (service == null) return;
        Set<Long> passengerIds = new HashSet<>();
        for (PlacedEntitySnapshot snapshot : service.loadWorld(rt.worldId())) {
            if (snapshot.mobPassengerId() != 0 && !passengerIds.add(snapshot.mobPassengerId())) throw new IllegalStateException("duplicate minecart passenger");
            Placed restored = restore(snapshot);
            if (restored == null) continue;
            entities.put(restored.id, restored);
            nextId = Math.max(nextId, restored.id + 1);
        }
    }

    private static Placed restore(PlacedEntitySnapshot snapshot) {
        Kind kind = Kind.forProtocol(snapshot.kind());
        if (kind == null) return null;
        Placed placed = new Placed(snapshot.entityId(), kind, snapshot.x(), snapshot.y(),
                snapshot.z(), snapshot.yaw());
        placed.pitch = snapshot.pitch();
        placed.mobPassengerId = snapshot.mobPassengerId();
        placed.health = snapshot.health();
        placed.fireTicks = snapshot.fireTicks();
        placed.vx = snapshot.velocityX();
        placed.vy = snapshot.velocityY();
        placed.vz = snapshot.velocityZ();
        placed.fuel = snapshot.fuel();
        placed.fuse = snapshot.fuse();
        placed.pushX = snapshot.pushX();
        placed.pushZ = snapshot.pushZ();
        if (kind == Kind.ARMOR_STAND) {
            placed.flags = snapshot.flags();
        } else {
            placed.flipped = (snapshot.flags() & CART_FLAG_FLIPPED) != 0;
            placed.hopperEnabled = (snapshot.flags() & CART_FLAG_HOPPER_DISABLED) == 0;
        }
        for (ChestItem item : snapshot.items()) {
            PlayerInventory.StackSnapshot stack = new PlayerInventory.StackSnapshot(
                    item.getItemType(), item.getItemCount(),
                    item.getDurability() == null ? 0 : item.getDurability(),
                    item.enchantmentsOrZero(), item.mapIdOrZero(), item.shulkerIdOrZero(),
                    item.getBucketMobData(), item.getItemComponentData());
            if (kind == Kind.ARMOR_STAND) {
                if (item.getSlot() >= 0 && item.getSlot() < 6) placed.equipment[item.getSlot()] = stack;
            } else if (placed.cargo != null && item.getSlot() >= 0
                    && item.getSlot() < placed.cargo.slots()) {
                placed.cargo.restoreSlot(item.getSlot(), stack.itemType(), stack.count(),
                        PlayerInventory.isDurable(stack.itemType()) ? stack.durability() : null,
                        stack.enchantments(), stack.mapId(), stack.shulkerId(),
                        stack.bucketMobData(), stack.itemComponentData());
            }
        }
        placed.storedX = placed.x;
        placed.storedY = placed.y;
        placed.storedZ = placed.z;
        return placed;
    }

    private PlacedEntitySnapshot snapshot(Placed placed) {
        List<ChestItem> items = new ArrayList<>();
        if (placed.kind == Kind.ARMOR_STAND) {
            for (int slot = 0; slot < 6; slot++) addItem(items, slot, placed.equipment[slot]);
        } else if (placed.cargo != null) {
            for (int slot = 0; slot < placed.cargo.slots(); slot++) {
                short type = placed.cargo.itemType(slot);
                if (type == PlayerInventory.EMPTY || placed.cargo.count(slot) <= 0) continue;
                addItem(items, slot, new PlayerInventory.StackSnapshot(type,
                        placed.cargo.count(slot), placed.cargo.durability(slot),
                        placed.cargo.enchantments(slot), placed.cargo.mapId(slot),
                        placed.cargo.shulkerId(slot), placed.cargo.bucketMobData(slot),
                        placed.cargo.itemComponentData(slot)));
            }
        }
        int flags = placed.kind == Kind.ARMOR_STAND ? placed.flags
                : (placed.flipped ? CART_FLAG_FLIPPED : 0)
                        | (placed.hopperEnabled ? 0 : CART_FLAG_HOPPER_DISABLED);
        return new PlacedEntitySnapshot(placed.id, placed.kind.protocol, placed.x, placed.y,
                placed.z, placed.yaw, placed.pitch, placed.vx, placed.vy, placed.vz, flags,
                placed.fuel, placed.fuse, placed.pushX, placed.pushZ, items, placed.health, placed.fireTicks, placed.mobPassengerId);
    }

    private static void addItem(List<ChestItem> items, int slot, PlayerInventory.StackSnapshot stack) {
        if (stack == null || stack.isEmpty()) return;
        items.add(new ChestItem(slot, stack.itemType(), stack.count(),
                PlayerInventory.isDurable(stack.itemType()) ? stack.durability() : null,
                stack.enchantments() == 0L ? null : stack.enchantments(),
                stack.mapId() == 0 ? null : stack.mapId(),
                stack.shulkerId() == 0 ? null : stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData()));
    }

    private final Map<Long, MobPersistenceSnapshot> passengerPersistence = new HashMap<>();

    private void persist() {
        persistenceRevision++;
        persistenceDirty = true;
        submitPersistenceIfNeeded();
    }

    private void submitPersistenceIfNeeded() {
        if (!persistenceDirty || persistenceInFlight || persistence == null
                || rt.ctx().persistenceExecutor() == null) return;
        long revision = persistenceRevision;
        List<PlacedEntitySnapshot> snapshot = snapshotForPersistence();
        List<MobPersistenceSnapshot> passengers = List.copyOf(passengerPersistence.values());
        boolean accepted = rt.ctx().persistenceExecutor().trySubmit(() -> {
            try {
                persistence.replaceWorldWithPassengers(rt.worldId(), snapshot, passengers);
                persistenceCompletions.add(new long[] {revision, 1});
            } catch (RuntimeException | Error exception) {
                persistenceCompletions.add(new long[] {revision, 0});
                throw exception;
            }
        });
        if (accepted) persistenceInFlight = true;
    }

    private void drainPersistenceCompletions() {
        long[] completion;
        while ((completion = persistenceCompletions.poll()) != null) {
            persistenceInFlight = false;
            if (completion[1] == 1 && completion[0] == persistenceRevision) { persistenceDirty = false; passengerPersistence.clear(); }
        }
    }

    private List<PlacedEntitySnapshot> snapshotForPersistence() {
        List<PlacedEntitySnapshot> out = new ArrayList<>(entities.size());
        for (Placed placed : entities.values()) {
            out.add(snapshot(placed));
            placed.storedX = placed.x;
            placed.storedY = placed.y;
            placed.storedZ = placed.z;
        }
        return List.copyOf(out);
    }

    /** Last write before the runtime is discarded (the {@link BoatSystem} contract). */
    void flushForDisposal() {
        if (persistence == null) return;
        List<PlacedEntitySnapshot> snapshot = snapshotForPersistence();
        List<MobPersistenceSnapshot> passengers = List.copyOf(passengerPersistence.values());
        if (rt.ctx().persistenceExecutor() == null) {
            persistence.replaceWorldWithPassengers(rt.worldId(), snapshot, passengers);
        } else {
            rt.ctx().persistenceExecutor().submitFuture(
                    () -> persistence.replaceWorldWithPassengers(rt.worldId(), snapshot, passengers));
        }
    }

    // ── queries ──────────────────────────────────────────────────────────

    int count() {
        return entities.size();
    }

    Placed find(long id) {
        Placed placed = entities.get(id);
        return placed == null || placed.removed ? null : placed;
    }

    List<Placed> snapshotForTest() {
        return List.copyOf(entities.values());
    }

    /** Test seam: spawns an entity directly (no item, reach or overlap checks). */
    Placed spawnForTest(Kind kind, double x, double y, double z, float yaw) {
        return spawn(kind, x, y, z, yaw);
    }

    boolean isRiding(String nickname) {
        for (Placed placed : entities.values()) {
            if (nickname.equals(placed.rider)) return true;
        }
        return false;
    }

    List<WsMessages.PlacedEntityDto> welcomeSnapshot() {
        List<WsMessages.PlacedEntityDto> out = new ArrayList<>(entities.size());
        for (Placed placed : entities.values()) out.add(dto(placed));
        return out;
    }

    /**
     * {@code HopperBlockEntity.getEntityContainer}: the container minecart whose box intersects the
     * one-block box centred on the cell (the first by entity ID), or null.
     */
    Placed containerEntityAt(int x, int y, int z) {
        double minX = x, minY = y, minZ = z;
        double maxX = x + 1.0, maxY = y + 1.0, maxZ = z + 1.0;
        for (Placed placed : entities.values()) {
            if (placed.removed || placed.cargo == null) continue;
            if (intersects(placed, minX, minY, minZ, maxX, maxY, maxZ)) return placed;
        }
        return null;
    }

    static boolean intersects(Placed placed, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double half = placed.halfWidth();
        return placed.x - half < maxX && placed.x + half > minX
                && placed.y < maxY && placed.y + placed.kind.height > minY
                && placed.z - half < maxZ && placed.z + half > minZ;
    }

    // ── spawning ─────────────────────────────────────────────────────────

    private Placed spawn(Kind kind, double x, double y, double z, float yaw) {
        Placed placed = new Placed(nextId++, kind, x, y, z, yaw);
        entities.put(placed.id, placed);
        placed.sent = dto(placed);
        broadcast(new WsMessages.PlacedEntitySpawn(List.of(placed.sent)));
        persist();
        return placed;
    }

    /**
     * {@code MinecartItem.useOn}: only onto a rail; the cart spawns at the rail's bottom centre,
     * 0.0625 up (plus 0.5 on a slope). No overlap check and no sound (old behaviour).
     */
    private boolean placeMinecart(PlayerTickState player, PlayerInventory.HandRef hand, Kind kind,
            int x, int y, int z) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                x, y, z)) return false;
        if (!isRail(block(x, y, z))) return false;
        double offset = railSlope(railShape(x, y, z)) ? 0.5 : 0.0;
        if (!player.inventory().consumeOne(hand, kind.item)) return false;
        spawn(kind, x + 0.5, y + 0.0625 + offset, z + 0.5, 0.0F);
        sendInventory(player);
        return true;
    }

    /**
     * {@code ArmorStandItem.useOn}: never onto a bottom face; the stand spawns at the bottom centre
     * of the clicked cell when it is replaceable, else the adjacent cell; its box must be free of
     * blocks and entities; yaw snaps to 45 degrees; {@code entity.armor_stand.place} (0.75, 0.8).
     */
    private boolean placeArmorStand(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, int face) {
        if (face == 0 || face < 0 || face > 5) return false;
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                x, y, z)) return false;
        int clicked = block(x, y, z);
        if (clicked == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
        int px = x, py = y, pz = z;
        if (!Fluids.isReplaceable(clicked) && !Fluids.isFluid(clicked)) {
            px += faceX(face);
            py += faceY(face);
            pz += faceZ(face);
        }
        double sx = px + 0.5, sy = py, sz = pz + 0.5;
        if (!boxFree(sx, sy, sz, Kind.ARMOR_STAND.width, Kind.ARMOR_STAND.height)) return false;
        if (!player.inventory().consumeOne(hand, PlayerInventory.ARMOR_STAND)) return false;
        float yaw = armorStandPlacementYaw(player.yaw());
        spawn(Kind.ARMOR_STAND, sx, sy, sz, yaw);
        worldSound("armor_stand_place", sx, sy, sz, PlayerInventory.ARMOR_STAND);
        sendInventory(player);
        return true;
    }

    /**
     * {@code floor((wrapDegrees(rotation - 180) + 22.5) / 45) * 45} where the rotation is the vanilla
     * {@code yRot} of the repository yaw (radians, 0 = north): {@code yRot = 180 - degrees(yaw)}.
     */
    static float armorStandPlacementYaw(double repositoryYaw) {
        float rotation = (float) (180.0 - Math.toDegrees(repositoryYaw));
        float wrapped = wrapDegrees(rotation - 180.0F);
        return (float) Math.floor((wrapped + 22.5F) / 45.0F) * 45.0F;
    }

    private boolean boxFree(double x, double y, double z, double width, double height) {
        double half = width / 2.0;
        int minX = floor(x - half + COLLISION_EPSILON), maxX = floor(x + half - COLLISION_EPSILON);
        int minY = floor(y + COLLISION_EPSILON), maxY = floor(y + height - COLLISION_EPSILON);
        int minZ = floor(z - half + COLLISION_EPSILON), maxZ = floor(z + half - COLLISION_EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    int b = block(bx, by, bz);
                    if (b == WorldTickLoop.UNAVAILABLE_BLOCK || Fluids.isSolid(b)) return false;
                }
            }
        }
        double x0 = x - half, x1 = x + half, z0 = z - half, z1 = z + half, y1 = y + height;
        for (PlayerTickState other : rt.players().values()) {
            if (other.isDead()) continue;
            if (other.x() - 0.3 < x1 && other.x() + 0.3 > x0 && other.y() < y1
                    && other.y() + 1.8 > y && other.z() - 0.3 < z1 && other.z() + 0.3 > z0) {
                return false;
            }
        }
        for (Placed placed : entities.values()) {
            if (!placed.removed && intersects(placed, x0, y, z0, x1, y1, z1)) return false;
        }
        return rt.mobSystem().noMobIntersecting(x0, y, z0, x1, y1, z1);
    }

    /**
     * {@code DispenseItemBehavior} for {@code Items.ARMOR_STAND}: a stand at the bottom centre of
     * the cell in front, facing {@code direction.toYRot()}, with no overlap check.
     */
    boolean dispenseArmorStand(int x, int y, int z, int facing3d) {
        int b = block(x, y, z);
        if (b == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
        spawn(Kind.ARMOR_STAND, x + 0.5, y, z + 0.5, directionYRot(facing3d));
        return true;
    }

    /**
     * {@code MinecartDispenseItemBehavior}: at {@code center + step * 1.125} ({@code floor(y) +
     * stepY}); onto the rail in front (+0.6 on a slope, else +0.1), or onto a rail below an air
     * cell in front (-0.4 on a slope unless facing down, else -0.9); otherwise false so the caller
     * runs the default drop.
     */
    boolean dispenseMinecart(short item, int dispenserX, int dispenserY, int dispenserZ,
            int stepX, int stepY, int stepZ) {
        Kind kind = Kind.forItem(item);
        if (kind == null || !kind.minecart()) return false;
        double x = dispenserX + 0.5 + stepX * 1.125;
        double y = dispenserY + stepY;
        double z = dispenserZ + 0.5 + stepZ * 1.125;
        int fx = dispenserX + stepX, fy = dispenserY + stepY, fz = dispenserZ + stepZ;
        int front = block(fx, fy, fz);
        if (front == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
        double offset;
        if (isRail(front)) {
            offset = railSlope(railShape(fx, fy, fz)) ? 0.6 : 0.1;
        } else {
            if (front != Blocks.AIR) return false;
            int below = block(fx, fy - 1, fz);
            if (below != Blocks.RAIL) return false;
            boolean slope = railSlope(railShape(fx, fy - 1, fz));
            offset = stepY != -1 && slope ? -0.4 : -0.9;
        }
        spawn(kind, x, y + offset, z, 0.0F);
        return true;
    }

    /**
     * {@code EquipmentDispenseItemBehavior.dispenseEquipment} onto an armor stand in the cell in
     * front: the first stand that can take the stack in its slot (hands only with arms shown).
     */
    boolean dispenseEquipment(int x, int y, int z, PlayerInventory.StackSnapshot stack) {
        int slot = equipmentSlotFor(stack.itemType());
        if (slot < FEET) return false;
        for (Placed placed : entities.values()) {
            if (placed.removed || placed.kind != Kind.ARMOR_STAND) continue;
            if (!intersects(placed, x, y, z, x + 1.0, y + 1.0, z + 1.0)) continue;
            if (!placed.equipment[slot].isEmpty()) continue;
            placed.equipment[slot] = new PlayerInventory.StackSnapshot(stack.itemType(), 1,
                    stack.durability(), stack.enchantments(), 0, 0, null,
                    stack.itemComponentData());
            worldSound("armor_equip", placed.x, placed.y, placed.z, stack.itemType());
            persist();
            return true;
        }
        return false;
    }

    // ── player actions ───────────────────────────────────────────────────

    void apply(PlayerAction.PlacedEntityCommand command) {
        PlayerTickState player = rt.players().get(command.nickname());
        if (player == null || player.isDead()) return;
        switch (command.op()) {
            case PLACE -> place(player, command);
            case INTERACT -> interact(player, command);
            case ATTACK -> attack(player, command);
            case DISMOUNT -> dismount(player.nickname(), true);
            case RIDE_INPUT -> rideInput(player, command);
            case CLOSE_CARGO -> {
                Long open = openCargo.get(player.nickname());
                if (open != null && open == command.id()) closeCargo(player, true);
                else sendTo(player, new WsMessages.EntityCargoClosed(command.id()));
            }
        }
    }

    private void place(PlayerTickState player, PlayerAction.PlacedEntityCommand command) {
        PlayerInventory.HandRef hand = player.inventory().capture(
                command.offhand() ? PlayerInventory.Hand.OFFHAND : PlayerInventory.Hand.MAIN);
        short held = player.inventory().stack(hand).itemType();
        Kind kind = Kind.forItem(held);
        if (kind == null) return;
        if (kind == Kind.ARMOR_STAND) {
            placeArmorStand(player, hand, command.x(), command.y(), command.z(), command.face());
        } else {
            placeMinecart(player, hand, kind, command.x(), command.y(), command.z());
        }
    }

    private boolean withinReach(PlayerTickState player, Placed placed) {
        return PlayerInteractionRules.canInteractWithEntity(player.x(), player.y(), player.z(),
                player.crouching(), placed.x, placed.y, placed.z, placed.kind.width,
                placed.kind.height);
    }

    private void interact(PlayerTickState player, PlayerAction.PlacedEntityCommand command) {
        Placed placed = find(command.id());
        if (placed == null || !withinReach(player, placed)) return;
        PlayerInventory.HandRef hand = player.inventory().capture(
                command.offhand() ? PlayerInventory.Hand.OFFHAND : PlayerInventory.Hand.MAIN);
        switch (placed.kind) {
            case ARMOR_STAND -> interactArmorStand(player, hand, placed, command.hitY());
            case MINECART -> {
                // Minecart#interact: not while sneaking, only into an empty cart.
                if (player.crouching() || (placed.rider != null || placed.mobPassengerId != 0) || isRiding(player.nickname())
                        || rt.boatSystem().isRiding(player.nickname())
                        || rt.mobSystem().isSeated(player.nickname())) return;
                rt.tickLoop().closeMenusForPlacedEntity(player);
                placed.rider = player.nickname();
                placed.inputX = 0.0;
                placed.inputZ = 0.0;
            }
            case CHEST_MINECART, HOPPER_MINECART -> openCargo(player, placed);
            case FURNACE_MINECART -> {
                // MinecartFurnace#addFuel: coal or charcoal while fuel + 3600 <= 32000, and only
                // then a push away from the player; the stack loses one.
                short held = player.inventory().stack(hand).itemType();
                if (held != PlayerInventory.COAL && held != PlayerInventory.CHARCOAL) return;
                if (placed.fuel + FURNACE_FUEL_PER_ITEM > FURNACE_MAX_FUEL) return;
                if (!player.inventory().consumeOne(hand, held)) return;
                placed.fuel += FURNACE_FUEL_PER_ITEM;
                if (placed.fuel > 0) {
                    placed.pushX = placed.x - player.x();
                    placed.pushZ = placed.z - player.z();
                }
                sendInventory(player);
                persist();
            }
            case TNT_MINECART -> { }
        }
    }

    /** {@code ArmorStand#interact(Player, InteractionHand, Vec3)} with the hit height. */
    private void interactArmorStand(PlayerTickState player, PlayerInventory.HandRef hand,
            Placed stand, double hitY) {
        PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
        if (held.itemType() == PlayerInventory.NAME_TAG) return;
        int itemSlot = held.isEmpty() ? MAINHAND : equipmentSlotFor(held.itemType());
        if (held.isEmpty()) {
            int clicked = clickedSlot(stand, hitY);
            int target = isDisabled(stand, clicked) ? itemSlot : clicked;
            if (!stand.equipment[target].isEmpty()) swapItem(player, hand, stand, target, held);
            return;
        }
        if (isDisabled(stand, itemSlot)) return;
        if (itemSlot <= OFFHAND && (stand.flags & FLAG_SHOW_ARMS) == 0) return;
        swapItem(player, hand, stand, itemSlot, held);
    }

    /** {@code ArmorStand#getClickedSlot}. */
    static int clickedSlot(Placed stand, double hitY) {
        boolean small = (stand.flags & FLAG_SMALL) != 0;
        double y = small ? hitY * 2.0 : hitY;
        if (y >= 0.1 && y < 0.1 + (small ? 0.8 : 0.45) && !stand.equipment[FEET].isEmpty()) return FEET;
        if (y >= 0.9 + (small ? 0.3 : 0.0) && y < 0.9 + (small ? 1.0 : 0.7)
                && !stand.equipment[CHEST].isEmpty()) return CHEST;
        if (y >= 0.4 && y < 0.4 + (small ? 1.0 : 0.8) && !stand.equipment[LEGS].isEmpty()) return LEGS;
        if (y >= 1.6 && !stand.equipment[HEAD].isEmpty()) return HEAD;
        if (stand.equipment[MAINHAND].isEmpty() && !stand.equipment[OFFHAND].isEmpty()) return OFFHAND;
        return MAINHAND;
    }

    /** {@code ArmorStand#isDisabled}: no placed stand disables slots; hands need shown arms. */
    private static boolean isDisabled(Placed stand, int slot) {
        return slot <= OFFHAND && (stand.flags & FLAG_SHOW_ARMS) == 0;
    }

    /** {@code ArmorStand#swapItem} (survival: no infinite materials). */
    private void swapItem(PlayerTickState player, PlayerInventory.HandRef hand, Placed stand,
            int slot, PlayerInventory.StackSnapshot held) {
        PlayerInventory.StackSnapshot current = stand.equipment[slot];
        if (held.isEmpty() || held.count() <= 1) {
            if (!player.inventory().setStack(hand, current)) return;
            stand.equipment[slot] = held;
        } else {
            if (!current.isEmpty()) return;
            PlayerInventory.StackSnapshot one = new PlayerInventory.StackSnapshot(held.itemType(), 1,
                    held.durability(), held.enchantments(), held.mapId(), held.shulkerId(),
                    held.bucketMobData(), held.itemComponentData());
            PlayerInventory.StackSnapshot rest = new PlayerInventory.StackSnapshot(held.itemType(),
                    held.count() - 1, held.durability(), held.enchantments(), held.mapId(),
                    held.shulkerId(), held.bucketMobData(), held.itemComponentData());
            if (!player.inventory().setStack(hand, rest)) return;
            stand.equipment[slot] = one;
        }
        // LivingEntity#onEquipItem: the equippable's equip sound (armor slots only here).
        if (slot >= FEET && !stand.equipment[slot].isEmpty()) {
            worldSound("armor_equip", stand.x, stand.y, stand.z, stand.equipment[slot].itemType());
        }
        sendInventory(player);
        persist();
    }

    /** {@code getEquipmentSlotForItem}: the equippable slot, else the main hand. */
    static int equipmentSlotFor(short itemType) {
        ArmorSlot armor = PlayerInventory.armorSlot(itemType);
        if (armor == null) return MAINHAND;
        return switch (armor) {
            case HELMET -> HEAD;
            case CHESTPLATE -> CHEST;
            case LEGGINGS -> LEGS;
            case BOOTS -> FEET;
        };
    }

    private void attack(PlayerTickState player, PlayerAction.PlacedEntityCommand command) {
        Placed placed = find(command.id());
        if (placed == null || !withinReach(player, placed)) return;
        double damage = rt.mobSystem().combat().chargedDamageAgainstEntity(player, rt.tickNo());
        if (placed.kind == Kind.ARMOR_STAND) {
            hurtArmorStandByPlayer(placed, player);
        } else {
            hurtVehicle(placed, (float) damage, false, player.x(), player.z());
        }
    }

    /**
     * {@code ArmorStand#hurtServer} for a player attack ({@code #is_player_attack} can break):
     * a second hit within {@code WOBBLE_TIME} (5 game ticks) breaks the stand and drops the
     * armor stand item and its equipment; otherwise entity event 32 (hit sound and wobble).
     */
    com.gameexpert.engine.mob.MobWorldView.PlacedProjectileHit projectileHit(
            double ax, double ay, double az, double bx, double by, double bz) {
        return projectileHit(ax, ay, az, bx, by, bz, 0.0);
    }

    com.gameexpert.engine.mob.MobWorldView.PlacedProjectileHit projectileHit(
            double ax, double ay, double az, double bx, double by, double bz, double inflation) {
        Placed hit = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Placed entity : entities.values()) {
            if (entity.removed) continue;
            double half = entity.halfWidth();
            double t = com.gameexpert.engine.mob.ProjectileSim.segAabbT(ax, ay, az, bx, by, bz,
                    entity.x - half - inflation, entity.y - inflation, entity.z - half - inflation,
                    entity.x + half + inflation, entity.y + entity.kind.height + inflation, entity.z + half + inflation);
            if (t >= 0 && t < nearest) { hit = entity; nearest = t; }
        }
        return hit == null ? null : new com.gameexpert.engine.mob.MobWorldView.PlacedProjectileHit(hit.id, nearest);
    }

    /** FishingHook follows ordinary placed entities without causing damage. */
    boolean followFishingHook(com.gameexpert.engine.mob.ProjectileSim bobber) {
        Placed entity = entities.get(bobber.hookedPlacedEntityId);
        if (entity == null || entity.removed) return false;
        bobber.x = entity.x;
        bobber.y = FishingRules.hookedBobberY(entity.y, entity.kind.height);
        bobber.z = entity.z;
        return true;
    }

    /** Placed velocities are blocks per MC tick; FishingHook.pullEntity adds (owner - hook) *0.1. */
    void pullFishingHook(PlayerTickState player, com.gameexpert.engine.mob.ProjectileSim bobber) {
        Placed entity = entities.get(bobber.hookedPlacedEntityId);
        if (entity == null || entity.removed) return;
        entity.vx += (player.x() - bobber.x) * 0.1;
        entity.vy += (player.y() - bobber.y) * 0.1;
        entity.vz += (player.z() - bobber.z) * 0.1;
    }

    void hurtByProjectile(com.gameexpert.engine.mob.MobEvent.AttackPlacedEntity hit) {
        Placed entity = entities.get(hit.entityId());
        if (entity == null || entity.removed) return;
        if (entity.kind == Kind.ARMOR_STAND) {
            // DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS: arrows, tridents, fireballs and wind charges.
            switch (hit.kind()) {
                case ARROW, TRIDENT, SMALL_FIREBALL, WIND_CHARGE, BREEZE_WIND_CHARGE -> breakArmorStand(entity, true);
                default -> { }
            }
        } else if (entity.kind == Kind.TNT_MINECART && hit.kind() == com.gameexpert.engine.mob.ProjectileSim.Kind.ARROW
                && hit.burning()) {
            // MinecartTNT#hurtServer: a burning arrow explodes immediately with the arrow's speed.
            explodeTnt(entity, hit.speedSquared());
        } else {
            hurtVehicle(entity, hit.damage(), hit.burning(), hit.sourceX(), hit.sourceZ());
        }
    }

    private void hurtArmorStandByPlayer(Placed stand, PlayerTickState player) {
        long now = rt.clock().gameTimeMcTicks();
        if (now - stand.lastHitMcTick > ARMOR_STAND_WOBBLE_MC_TICKS) {
            stand.lastHitMcTick = now;
            broadcast(new WsMessages.PlacedEntityHit(stand.id));
            return;
        }
        breakArmorStand(stand, true);
    }

    /** {@code brokenByPlayer} (drops the stand item) then {@code brokenByAnything}. */
    private void breakArmorStand(Placed stand, boolean dropStand) {
        int bx = floor(stand.x), by = floor(stand.y), bz = floor(stand.z);
        if (dropStand) {
            rt.itemSystem().spawnDrop(PlayerInventory.ARMOR_STAND, 1, bx + 0.5, by + 0.5, bz + 0.5);
        }
        worldSound("armor_stand_break", stand.x, stand.y, stand.z, PlayerInventory.ARMOR_STAND);
        for (int slot = 0; slot < 6; slot++) {
            PlayerInventory.StackSnapshot stack = stand.equipment[slot];
            stand.equipment[slot] = PlayerInventory.StackSnapshot.EMPTY;
            if (stack.isEmpty()) continue;
            // Block.popResource(level, blockPosition().above(), stack)
            rt.itemSystem().spawnDrop(stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                    stack.itemComponentData(), bx + 0.5, by + 1.5, bz + 0.5);
        }
        remove(stand);
    }

    /**
     * {@code VehicleEntity#hurtServer}: flip the hurt direction, hurt time 10, damage + 10 x amount;
     * over 40 destroys ({@code TNT_MINECART} instead primes when the source ignites TNT or the cart
     * moves fast, see {@link #destroyVehicle}).
     */
    private void hurtVehicle(Placed cart, float amount, boolean ignitesTnt, double fromX, double fromZ) {
        if (cart.removed) return;
        cart.hurtDir = -cart.hurtDir;
        cart.hurtTime = 10;
        cart.damage += amount * 10.0F;
        boolean sourceDestroys = cart.kind == Kind.TNT_MINECART && ignitesTnt;
        if (cart.damage > CART_DESTROY_DAMAGE || sourceDestroys) destroyVehicle(cart, ignitesTnt);
    }

    private void destroyVehicle(Placed cart, boolean ignitesTnt) {
        if (cart.kind == Kind.TNT_MINECART) {
            // MinecartTNT#destroy: prime (fuse 0..38) when the source ignites TNT or the cart is
            // fast, otherwise the item drop.
            double speedSqr = cart.vx * cart.vx + cart.vz * cart.vz;
            if (ignitesTnt || speedSqr >= 0.009999999776482582) {
                if (cart.fuse < 0) {
                    primeTnt(cart);
                    cart.fuse = random.nextInt(20) + random.nextInt(20);
                }
                return;
            }
        }
        ejectRider(cart);
        rt.itemSystem().spawnDrop(cart.kind.item, 1, cart.x, cart.y, cart.z);
        if (cart.cargo != null) dropCargo(cart);
        remove(cart);
    }

    private void dropCargo(Placed cart) {
        for (ChestInventory.StoredStack stack : cart.cargo.drainAll()) {
            rt.itemSystem().spawnDrop(stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                    stack.itemComponentData(), cart.x, cart.y, cart.z);
        }
    }

    /** {@code MinecartTNT#primeFuse}: fuse 80, entity event 10, {@code entity.tnt.primed}. */
    private void primeTnt(Placed cart) {
        cart.fuse = TNT_FUSE;
        worldSound("tnt_prime", cart.x, cart.y, cart.z, (short) Blocks.TNT);
    }

    /** {@code MinecartTNT#explode}: power 4 + U * 1.5 * min(speed, 5). */
    private void explodeTnt(Placed cart, double speedSquared) {
        if (cart.removed) return;
        double power = (float) (4.0 + random.nextDouble() * 1.5 * Math.min(Math.sqrt(speedSquared), 5.0));
        double x = cart.x, y = cart.y, z = cart.z;
        ejectRider(cart);
        remove(cart);
        rt.mobSystem().explodeOrQueue(x, y, z, power, true);
    }

    private void rideInput(PlayerTickState player, PlayerAction.PlacedEntityCommand command) {
        for (Placed placed : entities.values()) {
            if (!player.nickname().equals(placed.rider)) continue;
            double ix = command.inputX(), iz = command.inputZ();
            double length = Math.sqrt(ix * ix + iz * iz);
            if (!Double.isFinite(length) || length > 1.0001) return;
            placed.inputX = ix;
            placed.inputZ = iz;
            return;
        }
    }

    /** The rider leaves: {@code getDismountLocationForPassenger} goes to the rider's client. */
    void dismount(String nickname, boolean notify) {
        for (Placed placed : entities.values()) {
            if (!nickname.equals(placed.rider)) continue;
            placed.rider = null;
            placed.inputX = 0.0;
            placed.inputZ = 0.0;
            if (notify) {
                double[] at = dismountLocation(placed);
                PlayerTickState player = rt.players().get(nickname);
                if (player != null) {
                    sendTo(player, new WsMessages.PlacedEntityDismounted(placed.id, at[0], at[1], at[2]));
                }
            }
            return;
        }
    }

    private void ejectRider(Placed cart) {
        ejectMobPassenger(cart);
        if (cart.rider != null) dismount(cart.rider, true);
    }

    void playerLeft(String nickname) {
        dismount(nickname, false);
        openCargo.remove(nickname);
        synced.remove(nickname);
    }

    void playerDied(String nickname) {
        dismount(nickname, false);
        PlayerTickState player = rt.players().get(nickname);
        if (player != null) closeCargo(player, false);
    }

    /**
     * {@code AbstractMinecart#getDismountLocationForPassenger} for a standing player: the eight
     * cells around the cart in {@code DismountHelper.offsetsForDirection(motionDirection)} order at
     * the cart level, one up and one down, whose floor is solid and whose two cells are free;
     * else the top of the cart box.
     */
    double[] dismountLocation(Placed cart) {
        int motion = motionDirection(cart);
        int[][] offsets = dismountOffsets(motion);
        int bx = floor(cart.x), by = floor(cart.y), bz = floor(cart.z);
        for (int dy : new int[] {0, 1, -1}) {
            for (int[] offset : offsets) {
                int cx = bx + offset[0], cy = by + dy, cz = bz + offset[1];
                int floorBlock = block(cx, cy - 1, cz);
                int feet = block(cx, cy, cz);
                int head = block(cx, cy + 1, cz);
                if (floorBlock == WorldTickLoop.UNAVAILABLE_BLOCK || feet == WorldTickLoop.UNAVAILABLE_BLOCK
                        || head == WorldTickLoop.UNAVAILABLE_BLOCK) continue;
                if (Fluids.isSolid(floorBlock) && !Fluids.isSolid(feet) && !Fluids.isSolid(head)) {
                    return new double[] {cx + 0.5, cy, cz + 0.5};
                }
            }
        }
        return new double[] {cart.x, cart.y + cart.kind.height, cart.z};
    }

    /**
     * {@code OldMinecartBehavior.getMotionDirection}: {@code getDirection().getClockWise()}, the
     * opposite when flipped. Horizontal direction indices: 0 south, 1 west, 2 north, 3 east.
     */
    static int motionDirection(Placed cart) {
        int facing = Math.floorMod((int) Math.floor(cart.yaw / 90.0 + 0.5), 4);
        int clockwise = (facing + 1) & 3;
        return cart.flipped ? (clockwise + 2) & 3 : clockwise;
    }

    /** {@code DismountHelper.offsetsForDirection}. */
    static int[][] dismountOffsets(int direction) {
        int[] d = horizontalStep(direction);
        int[] cw = horizontalStep((direction + 1) & 3);
        int[] ccw = {-cw[0], -cw[1]};
        int[] back = {-d[0], -d[1]};
        return new int[][] {
                {cw[0], cw[1]}, {ccw[0], ccw[1]},
                {back[0] + cw[0], back[1] + cw[1]}, {back[0] + ccw[0], back[1] + ccw[1]},
                {d[0] + cw[0], d[1] + cw[1]}, {d[0] + ccw[0], d[1] + ccw[1]},
                {back[0], back[1]}, {d[0], d[1]},
        };
    }

    private static int[] horizontalStep(int direction) {
        return switch (direction & 3) {
            case 0 -> new int[] {0, 1};    // south
            case 1 -> new int[] {-1, 0};   // west
            case 2 -> new int[] {0, -1};   // north
            default -> new int[] {1, 0};   // east
        };
    }

    // ── cargo menus ──────────────────────────────────────────────────────

    private void openCargo(PlayerTickState player, Placed cart) {
        rt.tickLoop().closeMenusForPlacedEntity(player);
        openCargo.put(player.nickname(), cart.id);
        sendTo(player, new WsMessages.EntityCargoOpen(cart.id, cart.kind.protocol, cargoSlots(cart)));
    }

    Placed openCargoOf(PlayerTickState player, long entityId) {
        Long open = openCargo.get(player.nickname());
        if (open == null || open != entityId) return null;
        Placed cart = find(entityId);
        if (cart == null || cart.cargo == null || !cargoStillValid(player, cart)) {
            closeCargo(player, true);
            return null;
        }
        return cart;
    }

    boolean hasOpenCargo(PlayerTickState player) {
        return openCargo.containsKey(player.nickname());
    }

    /** {@code ContainerEntity#isChestVehicleStillValid}: alive and within the 4-block entity reach. */
    private static boolean cargoStillValid(PlayerTickState player, Placed cart) {
        return !cart.removed && PlayerInteractionRules.canInteractWithEntity(player.x(), player.y(),
                player.z(), player.crouching(), cart.x, cart.y, cart.z, cart.kind.width,
                cart.kind.height);
    }

    void closeCargo(PlayerTickState player, boolean notify) {
        Long open = openCargo.remove(player.nickname());
        if (open == null) return;
        rt.tickLoop().returnContainerCursor(player);
        if (notify) sendTo(player, new WsMessages.EntityCargoClosed(open));
    }

    /** After a click changed the cargo: persist and resend it to every viewer. */
    void cargoChanged(Placed cart) {
        persist();
        broadcastCargo(cart);
    }

    void resendCargo(PlayerTickState player, Placed cart) {
        sendTo(player, cargoUpdate(cart));
    }

    private void broadcastCargo(Placed cart) {
        WsMessages.EntityCargoUpdate update = cargoUpdate(cart);
        for (Map.Entry<String, Long> entry : openCargo.entrySet()) {
            if (entry.getValue() != cart.id) continue;
            PlayerTickState viewer = rt.players().get(entry.getKey());
            if (viewer != null) sendTo(viewer, update);
        }
    }

    private static WsMessages.EntityCargoUpdate cargoUpdate(Placed cart) {
        return new WsMessages.EntityCargoUpdate(cart.id, cart.kind.protocol, cargoSlots(cart));
    }

    private static List<WsMessages.InventorySlot> cargoSlots(Placed cart) {
        return WorldTickLoop.containerSlots(com.gameexpert.engine.inventory.ContainerAccess.of(cart.cargo));
    }

    private void closeInvalidCargo() {
        if (openCargo.isEmpty()) return;
        for (String nickname : List.copyOf(openCargo.keySet())) {
            PlayerTickState player = rt.players().get(nickname);
            if (player == null) {
                openCargo.remove(nickname);
                continue;
            }
            Placed cart = find(openCargo.get(nickname));
            if (cart == null || !cargoStillValid(player, cart)) closeCargo(player, true);
        }
    }

    // ── explosions ───────────────────────────────────────────────────────

    /**
     * {@code ServerExplosion} entity damage: a stand is broken by anything (equipment only, no
     * stand item); a cart takes the explosion damage ({@code VehicleEntity#hurtServer}; an
     * explosion ignites a TNT minecart).
     */
    void onExplosion(double x, double y, double z, double power,
            java.util.function.ToDoubleFunction<double[]> exposure) {
        if (entities.isEmpty()) return;
        double range = 2.0 * power;
        for (Placed placed : List.copyOf(entities.values())) {
            if (placed.removed) continue;
            double dx = placed.x - x, dy = placed.y - y, dz = placed.z - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance >= range) continue;
            double half = placed.halfWidth();
            double seen = exposure.applyAsDouble(new double[] {placed.x - half, placed.y,
                    placed.z - half, placed.x + half, placed.y + placed.kind.height,
                    placed.z + half});
            int damage = ExplosionRules.damageAt(distance, power, seen);
            if (damage <= 0) continue;
            if (placed.kind == Kind.ARMOR_STAND) {
                breakArmorStand(placed, false);
            } else {
                hurtVehicle(placed, damage, true, x, z);
            }
        }
    }

    // ── tick ─────────────────────────────────────────────────────────────

    void tick(long tickNo) {
        drainPersistenceCompletions();
        syncJoiningPlayers();
        dropMissingRiders();
        for (Placed cart : entities.values()) syncMobPassenger(cart);
        if (!entities.isEmpty()) {
            List<WsMessages.PlacedEntityDto> updates = null;
            tickSnapshot.addAll(entities.values());
            try {
                for (Placed placed : tickSnapshot) {
                    if (placed.removed || !isTickingChunk(placed)) continue;
                    for (int step = 0; step < MC_TICKS_PER_AUTHORITY_TICK && !placed.removed; step++) {
                        if (!hasResidentMovementArea(placed)) break;
                        stepEntity(placed);
                    }
                }
                for (Placed placed : tickSnapshot) {
                    if (placed.removed) continue;
                    WsMessages.PlacedEntityDto dto = dto(placed);
                    if (!sameBroadcast(placed.sent, dto)) {
                        placed.sent = dto;
                        if (updates == null) updates = new ArrayList<>();
                        updates.add(dto);
                    }
                }
            } finally {
                tickSnapshot.clear();
            }
            if (updates != null) broadcast(new WsMessages.PlacedEntityUpdate(List.copyOf(updates)));
            if (tickNo % 50 == 0) persistMovedEntities();
        }
        closeInvalidCargo();
        submitPersistenceIfNeeded();
    }

    private void syncJoiningPlayers() {
        for (PlayerTickState player : rt.players().values()) {
            if (synced.add(player.nickname()) && !entities.isEmpty()) {
                sendTo(player, new WsMessages.PlacedEntitySpawn(welcomeSnapshot()));
            }
        }
        if (synced.size() > rt.players().size()) synced.retainAll(rt.players().keySet());
    }

    private void dropMissingRiders() {
        for (Placed placed : entities.values()) {
            if (placed.rider == null) continue;
            PlayerTickState rider = rt.players().get(placed.rider);
            if (rider == null || rider.isDead()) {
                placed.rider = null;
                placed.inputX = 0.0;
                placed.inputZ = 0.0;
            }
        }
    }

    private void persistMovedEntities() {
        for (Placed placed : entities.values()) {
            if (Double.compare(placed.x, placed.storedX) != 0 || Double.compare(placed.y, placed.storedY) != 0
                    || Double.compare(placed.z, placed.storedZ) != 0) {
                persist();
                return;
            }
        }
    }

    private void stepEntity(Placed placed) {
        tickEnvironment(placed);
        if (placed.removed) return;
        placed.xo = placed.x;
        placed.yo = placed.y;
        placed.zo = placed.z;
        if (placed.kind == Kind.ARMOR_STAND) {
            stepArmorStand(placed);
            return;
        }
        float yawO = placed.yaw;
        // VehicleEntity hurt shake decay (AbstractMinecart#tick).
        if (placed.hurtTime > 0) placed.hurtTime--;
        if (placed.damage > 0.0F) placed.damage -= 1.0F;
        if (placed.y < Blocks.MIN_Y - 64) {
            ejectRider(placed);
            remove(placed);
            return;
        }
        boolean inWater = inWater(placed);
        // OldMinecartBehavior#tick
        placed.vy -= inWater ? CART_WATER_GRAVITY : CART_GRAVITY;
        int bx = floor(placed.x), by = floor(placed.y), bz = floor(placed.z);
        if (isRail(block(bx, by - 1, bz))) by--;
        if (isRail(block(bx, by, bz))) {
            moveAlongTrack(placed, bx, by, bz, inWater);
            if (block(bx, by, bz) == Blocks.ACTIVATOR_RAIL) {
                activateRail(placed, (rt.blockState(bx, by, bz, Blocks.ACTIVATOR_RAIL) & 8) != 0);
            }
        } else {
            comeOffTrack(placed, inWater);
        }
        placed.pitch = 0.0F;
        double dx = placed.xo - placed.x, dz = placed.zo - placed.z;
        if (dx * dx + dz * dz > 0.001) {
            placed.yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI);
            if (placed.flipped) placed.yaw += 180.0F;
        }
        double turn = wrapDegrees(placed.yaw - yawO);
        if (turn < -170.0 || turn >= 170.0) {
            placed.yaw += 180.0F;
            placed.flipped = !placed.flipped;
        }
        placed.yaw = placed.yaw % 360.0F;
        pushAndPickupEntities(placed);
        syncMobPassenger(placed);
        pushedByPlayers(placed);
        switch (placed.kind) {
            case FURNACE_MINECART -> {
                if (placed.fuel > 0) placed.fuel--;
                if (placed.fuel <= 0) {
                    placed.pushX = 0.0;
                    placed.pushZ = 0.0;
                }
            }
            case TNT_MINECART -> tickTnt(placed);
            case HOPPER_MINECART -> {
                if (placed.hopperEnabled) {
                    rt.hopperSystem().minecartSuck(placed.cargo, placed.x, placed.y, placed.z,
                            placed.kind.width, placed.kind.height, () -> cargoChanged(placed));
                }
            }
            default -> { }
        }
    }

    private void activateRail(Placed cart, boolean powered) {
        if (cart.kind == Kind.HOPPER_MINECART) cart.hopperEnabled = !powered;
        if (!powered) return;
        if (cart.kind == Kind.TNT_MINECART && !cart.primed()) primeTnt(cart);
        if (cart.kind == Kind.MINECART) {
            ejectRider(cart);
            if (cart.hurtTime == 0) {
                cart.hurtDir = -cart.hurtDir;
                cart.hurtTime = 10;
                cart.damage = 50.0F;
            }
        }
    }

    /** {@code MinecartTNT#tick}: fuse countdown, explosion at 0 or on a fast wall hit. */
    private void tickTnt(Placed cart) {
        if (cart.fuse > 0) cart.fuse--;
        if (cart.fuse == 0) {
            explodeTnt(cart, cart.vx * cart.vx + cart.vz * cart.vz);
            return;
        }
        if (cart.horizontalCollision) {
            double speedSqr = cart.vx * cart.vx + cart.vz * cart.vz;
            if (speedSqr >= 0.009999999776482582) explodeTnt(cart, speedSqr);
        }
    }

    /** {@code LivingEntity#travel} for a stand: fall with gravity, no self-propulsion. */
    private void stepArmorStand(Placed stand) {
        double friction = stand.onGround ? STAND_GROUND_FRICTION : STAND_AIR_FRICTION;
        move(stand, stand.vx, stand.vy, stand.vz);
        if (stand.onGround && stand.vy < 0.0) stand.vy = 0.0;
        stand.vy = (stand.vy - STAND_GRAVITY) * STAND_VERTICAL_DRAG;
        stand.vx *= friction;
        stand.vz *= friction;
        if (stand.y < Blocks.MIN_Y - 64) remove(stand);
    }

    /** {@code OldMinecartBehavior#moveAlongTrack} (plain rails only). */
    void moveAlongTrack(Placed cart, int bx, int by, int bz, boolean inWater) {
        double[] before = railPosition(cart.x, cart.y, cart.z);
        double y = by;
        double slope = CART_SLOPE_ACCELERATION * (inWater ? 0.2 : 1.0);
        int shape = railShape(bx, by, bz);
        boolean poweredRail = block(bx, by, bz) == Blocks.POWERED_RAIL;
        boolean powered = poweredRail && (rt.blockState(bx, by, bz, Blocks.POWERED_RAIL) & 8) != 0;
        boolean brake = poweredRail && !powered;
        switch (shape) {
            case 2 -> { cart.vx -= slope; y++; }
            case 3 -> { cart.vx += slope; y++; }
            case 4 -> { cart.vz += slope; y++; }
            case 5 -> { cart.vz -= slope; y++; }
            default -> { }
        }
        int[] a = EXITS[shape][0];
        int[] b = EXITS[shape][1];
        double ex = b[0] - a[0];
        double ez = b[2] - a[2];
        double length = Math.sqrt(ex * ex + ez * ez);
        if (cart.vx * ex + cart.vz * ez < 0.0) {
            ex = -ex;
            ez = -ez;
        }
        double speed = Math.min(2.0, Math.sqrt(cart.vx * cart.vx + cart.vz * cart.vz));
        cart.vx = speed * ex / length;
        cart.vz = speed * ez / length;
        if (cart.rider != null && cart.kind == Kind.MINECART) {
            double ix = cart.inputX, iz = cart.inputZ;
            double inputSqr = ix * ix + iz * iz;
            if (inputSqr > 0.0) {
                double inputLength = Math.sqrt(inputSqr);
                double nx = ix / inputLength, nz = iz / inputLength;
                if (cart.vx * cart.vx + cart.vz * cart.vz < 0.01) {
                    cart.vx += nx * 0.001;
                    cart.vz += nz * 0.001;
                    brake = false;
                }
            }
        }
        if (brake) {
            if (Math.sqrt(cart.vx * cart.vx + cart.vz * cart.vz) < 0.03) {
                cart.vx = 0; cart.vy = 0; cart.vz = 0;
            } else {
                cart.vx *= 0.5; cart.vy = 0; cart.vz *= 0.5;
            }
        }
        double x0 = bx + 0.5 + a[0] * 0.5, z0 = bz + 0.5 + a[2] * 0.5;
        double x1 = bx + 0.5 + b[0] * 0.5, z1 = bz + 0.5 + b[2] * 0.5;
        ex = x1 - x0;
        ez = z1 - z0;
        double t;
        if (ex == 0.0) {
            t = cart.z - bz;
        } else if (ez == 0.0) {
            t = cart.x - bx;
        } else {
            t = ((cart.x - x0) * ex + (cart.z - z0) * ez) * 2.0;
        }
        cart.x = x0 + ex * t;
        cart.y = y;
        cart.z = z0 + ez * t;
        double multiplier = cart.rider != null || cart.mobPassengerId != 0 ? 0.75 : 1.0;
        double max = maxSpeed(cart, inWater);
        move(cart, clamp(multiplier * cart.vx, -max, max), 0.0, clamp(multiplier * cart.vz, -max, max));
        if (a[1] != 0 && floor(cart.x) - bx == a[0] && floor(cart.z) - bz == a[2]) {
            cart.y += a[1];
        } else if (b[1] != 0 && floor(cart.x) - bx == b[0] && floor(cart.z) - bz == b[2]) {
            cart.y += b[1];
        }
        applyNaturalSlowdown(cart, inWater);
        double[] after = railPosition(cart.x, cart.y, cart.z);
        if (after != null && before != null) {
            double dy = (before[1] - after[1]) * 0.05;
            double horizontal = Math.sqrt(cart.vx * cart.vx + cart.vz * cart.vz);
            if (horizontal > 0.0) {
                cart.vx *= (horizontal + dy) / horizontal;
                cart.vz *= (horizontal + dy) / horizontal;
            }
            cart.y = after[1];
        }
        int nx = floor(cart.x), nz = floor(cart.z);
        if (nx != bx || nz != bz) {
            double horizontal = Math.sqrt(cart.vx * cart.vx + cart.vz * cart.vz);
            cart.vx = horizontal * (nx - bx);
            cart.vz = horizontal * (nz - bz);
        }
        if (powered) {
            double horizontal = Math.sqrt(cart.vx * cart.vx + cart.vz * cart.vz);
            if (horizontal > 0.01) {
                cart.vx += cart.vx / horizontal * 0.06;
                cart.vz += cart.vz / horizontal * 0.06;
            } else if (shape == 1) {
                if (solid(bx - 1, by, bz)) cart.vx = 0.02;
                else if (solid(bx + 1, by, bz)) cart.vx = -0.02;
            } else if (shape == 0) {
                if (solid(bx, by, bz - 1)) cart.vz = 0.02;
                else if (solid(bx, by, bz + 1)) cart.vz = -0.02;
            }
        }
    }

    /** {@code AbstractMinecart#comeOffTrack}. */
    private void comeOffTrack(Placed cart, boolean inWater) {
        double max = maxSpeed(cart, inWater);
        cart.vx = clamp(cart.vx, -max, max);
        cart.vz = clamp(cart.vz, -max, max);
        if (cart.onGround) {
            cart.vx *= 0.5;
            cart.vy *= 0.5;
            cart.vz *= 0.5;
        }
        move(cart, cart.vx, cart.vy, cart.vz);
        if (!cart.onGround) {
            cart.vx *= CART_AIR_DRAG;
            cart.vy *= CART_AIR_DRAG;
            cart.vz *= CART_AIR_DRAG;
        }
    }

    private static double maxSpeed(Placed cart, boolean inWater) {
        double base = inWater ? CART_WATER_MAX_SPEED : CART_MAX_SPEED;
        if (cart.kind == Kind.FURNACE_MINECART) return base * (inWater ? 0.75 : 0.5);
        return base;
    }

    /**
     * {@code applyNaturalSlowdown}: plain carts 0.997 ridden / 0.96, containers 0.98 + (15 -
     * comparator signal) x 0.001, the furnace its push; x 0.95 in water; the vertical part is 0.
     */
    private void applyNaturalSlowdown(Placed cart, boolean inWater) {
        double factor;
        if (cart.kind == Kind.FURNACE_MINECART) {
            if (cart.pushX * cart.pushX + cart.pushZ * cart.pushZ > 1.0E-7) {
                calculateNewPushAlong(cart);
                cart.vx = cart.vx * 0.8 + cart.pushX;
                cart.vz = cart.vz * 0.8 + cart.pushZ;
                if (inWater) {
                    cart.vx *= 0.1;
                    cart.vz *= 0.1;
                }
            } else {
                cart.vx *= 0.98;
                cart.vz *= 0.98;
            }
            factor = cart.rider != null || cart.mobPassengerId != 0 ? 0.997 : 0.96;
        } else if (cart.cargo != null) {
            factor = (float) (0.98F + (15 - comparatorSignal(cart.cargo)) * 0.001F);
        } else {
            factor = cart.rider != null || cart.mobPassengerId != 0 ? 0.997 : 0.96;
        }
        if (inWater) factor *= cart.cargo != null ? 0.95F : 0.949999988079071;
        cart.vx *= factor;
        cart.vy = 0.0;
        cart.vz *= factor;
    }

    /** {@code MinecartFurnace#calculateNewPushAlong}. */
    private static void calculateNewPushAlong(Placed cart) {
        double pushSqr = cart.pushX * cart.pushX + cart.pushZ * cart.pushZ;
        double motionSqr = cart.vx * cart.vx + cart.vz * cart.vz;
        if (pushSqr > 1.0E-4 && motionSqr > 0.001) {
            double dot = cart.pushX * cart.vx + cart.pushZ * cart.vz;
            double px = cart.vx * dot / motionSqr, pz = cart.vz * dot / motionSqr;
            double projected = Math.sqrt(px * px + pz * pz);
            double pushLength = Math.sqrt(pushSqr);
            if (projected > 1.0E-5) {
                cart.pushX = px / projected * pushLength;
                cart.pushZ = pz / projected * pushLength;
            } else {
                cart.pushX = 0.0;
                cart.pushZ = 0.0;
            }
        }
    }

    /** {@code AbstractContainerMenu.getRedstoneSignalFromContainer}. */
    static int comparatorSignal(ChestInventory cargo) {
        float fill = 0.0F;
        int occupied = 0;
        for (int slot = 0; slot < cargo.slots(); slot++) {
            short type = cargo.itemType(slot);
            int count = cargo.count(slot);
            if (type == PlayerInventory.EMPTY || count <= 0) continue;
            fill += (float) count / Math.min(64, PlayerInventory.stackMax(type));
            occupied++;
        }
        if (occupied == 0) return 0;
        return (int) Math.floor(fill / cargo.slots() * 14.0F) + 1;
    }

    /** {@code OldMinecartBehavior#getPos}: the point on the rail under the given position. */
    double[] railPosition(double x, double y, double z) {
        int i = floor(x), j = floor(y), k = floor(z);
        if (isRail(block(i, j - 1, k))) j--;
        if (!isRail(block(i, j, k))) return null;
        int shape = railShape(i, j, k);
        int[] a = EXITS[shape][0];
        int[] b = EXITS[shape][1];
        double x0 = i + 0.5 + a[0] * 0.5, y0 = j + 0.0625 + a[1] * 0.5, z0 = k + 0.5 + a[2] * 0.5;
        double x1 = i + 0.5 + b[0] * 0.5, y1 = j + 0.0625 + b[1] * 0.5, z1 = k + 0.5 + b[2] * 0.5;
        double dx = x1 - x0, dy = (y1 - y0) * 2.0, dz = z1 - z0;
        double t;
        if (dx == 0.0) {
            t = z - k;
        } else if (dz == 0.0) {
            t = x - i;
        } else {
            t = ((x - x0) * dx + (z - z0) * dz) * 2.0;
        }
        double rx = x0 + dx * t, ry = y0 + dy * t, rz = z0 + dz * t;
        if (dy < 0.0) {
            ry += 1.0;
        } else if (dy > 0.0) {
            ry += 0.5;
        }
        return new double[] {rx, ry, rz};
    }

    /**
     * {@code OldMinecartBehavior#pushAndPickupEntities} between carts: every other cart in the box
     * inflated by 0.2 horizontally is pushed ({@code AbstractMinecart#push(Entity)}). Moving empty
     * rideable carts also board a colliding eligible mob and persist the binding with its row.
     */
    private void pushAndPickupEntities(Placed cart) {
        double half = cart.halfWidth();
        double minX = cart.x - half - 0.20000000298023224, maxX = cart.x + half + 0.20000000298023224;
        double minZ = cart.z - half - 0.20000000298023224, maxZ = cart.z + half + 0.20000000298023224;
        double minY = cart.y, maxY = cart.y + cart.kind.height;
        if (cart.kind == Kind.MINECART && cart.rider == null && cart.mobPassengerId == 0
                && cart.vx * cart.vx + cart.vz * cart.vz >= 0.01 && !onPoweredActivator(cart)) {
            for (Mob mob : rt.mobSystem().mobsForPlacedVehicles()) {
                if (mob.isDead() || mob.removed || mob.type == MobType.IRON_GOLEM
                        || mob.isRidingBoat() || mob.isRidingPlacedVehicle() || mob.isMobPassenger() || !mob.canBoardPlacedVehicle()) continue;
                double hw = mob.width() / 2;
                if (mob.x - hw >= maxX || mob.x + hw <= minX || mob.y >= maxY
                        || mob.y + mob.height() <= minY || mob.z - hw >= maxZ || mob.z + hw <= minZ) continue;
                cart.mobPassengerId = mob.id;
                syncMobPassenger(cart);
                persist();
                break;
            }
        }
        for (Placed other : entities.values()) {
            if (other == cart || other.removed || !other.kind.minecart()) continue;
            if (!intersects(other, minX, minY, minZ, maxX, maxY, maxZ)) continue;
            pushCart(other, cart);
        }
    }

    private boolean onPoweredActivator(Placed cart) {
        int x = floor(cart.x), y = floor(cart.y), z = floor(cart.z);
        if (isRail(block(x, y - 1, z))) y--;
        return block(x, y, z) == Blocks.ACTIVATOR_RAIL && (rt.blockState(x, y, z, Blocks.ACTIVATOR_RAIL) & 8) != 0;
    }

    private void syncMobPassenger(Placed cart) {
        if (cart.mobPassengerId == 0) return;
        Mob mob = rt.mobSystem().mobForPlacedVehicle(cart.mobPassengerId);
        if (mob == null) {
            if (rt.started()) { cart.mobPassengerId = 0; persist(); }
            return;
        }
        if (mob.isDead() || mob.removed || mob.isRidingBoat() || mob.isMobPassenger()
                || mob.isRidingPlacedVehicle() && mob.placedVehicleId() != cart.id) {
            cart.mobPassengerId = 0; persist(); return;
        }
        double seatY = mob.type == MobType.VILLAGER || mob.type == MobType.WANDERING_TRADER ? 0 : 0.1875;
        if (mob.placedVehicleId() != cart.id) mob.bindPlacedVehicleDismount(id -> dismountMobPassenger(id, mob.id));
        mob.mountPlacedVehicle(cart.id, cart.x, cart.y + seatY, cart.z, Math.toRadians(cart.yaw));
        MobPersistenceSnapshot row = rt.mobSystem().placedVehiclePassengerSnapshot(mob);
        if (row != null) passengerPersistence.put(mob.id, row);
    }

    private double[] placedDismount(Placed cart, Mob mob) {
        double angle = Math.toRadians(cart.yaw);
        for (int dy = 0; dy <= 2; dy++) for (int side : new int[] {1, -1}) {
            double x = cart.x + Math.sin(angle) * side, y = Math.floor(cart.y) + dy,
                    z = cart.z - Math.cos(angle) * side;
            double hw = mob.width() / 2;
            if (!Fluids.isSolid(block(floor(x), floor(y - 0.01), floor(z)))) continue;
            boolean free = true;
            for (int bx = floor(x - hw + 1e-7); bx <= floor(x + hw - 1e-7); bx++)
                for (int by = floor(y + 1e-7); by <= floor(y + mob.height() - 1e-7); by++)
                    for (int bz = floor(z - hw + 1e-7); bz <= floor(z + hw - 1e-7); bz++) {
                        int occupied = block(bx, by, bz);
                        if (occupied < 0 || Fluids.isSolid(occupied)) free = false;
                    }
            if (free) return new double[] {x, y, z};
        }
        return new double[] {cart.x, cart.y + 0.7, cart.z};
    }

    boolean dismountMobPassenger(long cartId, long mobId) {
        Placed cart = entities.get(cartId);
        if (cart == null || cart.removed || cart.mobPassengerId != mobId) return false;
        ejectMobPassenger(cart);
        return true;
    }

    private void ejectMobPassenger(Placed cart) {
        if (cart.mobPassengerId == 0) return;
        Mob mob = rt.mobSystem().mobForPlacedVehicle(cart.mobPassengerId);
        cart.mobPassengerId = 0;
        if (mob != null && mob.placedVehicleId() == cart.id) {
            double angle = Math.toRadians(cart.yaw);
            double[] exit = placedDismount(cart, mob);
            mob.dismountPlacedVehicle(exit[0], exit[1], exit[2]);
            MobPersistenceSnapshot row = rt.mobSystem().placedVehiclePassengerSnapshot(mob);
            if (row != null) passengerPersistence.put(mob.id, row);
        }
        persist();
    }

    /** Walking players push the carts they touch ({@code LivingEntity#pushEntities}). */
    private void pushedByPlayers(Placed cart) {
        double half = cart.halfWidth();
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead() || player.nickname().equals(cart.rider)) continue;
            if (player.x() - 0.3 >= cart.x + half || player.x() + 0.3 <= cart.x - half
                    || player.y() >= cart.y + cart.kind.height || player.y() + 1.8 <= cart.y
                    || player.z() - 0.3 >= cart.z + half || player.z() + 0.3 <= cart.z - half) {
                continue;
            }
            double dx = player.x() - cart.x, dz = player.z() - cart.z;
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr < 9.999999747378752E-5) continue;
            double[] push = pushVector(dx, dz, distanceSqr);
            cart.vx -= push[0];
            cart.vz -= push[1];
        }
    }

    /** {@code AbstractMinecart#push(Entity)} with {@code self} the pushed cart. */
    private static void pushCart(Placed self, Placed by) {
        if (by.rider != null && by.rider.equals(self.rider)) return;
        double dx = by.x - self.x, dz = by.z - self.z;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr < 9.999999747378752E-5) return;
        double[] push = pushVector(dx, dz, distanceSqr);
        pushOtherMinecart(self, by, push[0], push[1]);
    }

    private static double[] pushVector(double dx, double dz, double distanceSqr) {
        double distance = Math.sqrt(distanceSqr);
        dx /= distance;
        dz /= distance;
        double scale = 1.0 / distance;
        if (scale > 1.0) scale = 1.0;
        dx *= scale;
        dz *= scale;
        dx *= 0.10000000149011612;
        dz *= 0.10000000149011612;
        dx *= 0.5;
        dz *= 0.5;
        return new double[] {dx, dz};
    }

    /** {@code AbstractMinecart#pushOtherMinecart} (old behaviour). */
    private static void pushOtherMinecart(Placed self, Placed other, double dx, double dz) {
        double ox = other.x - self.x, oz = other.z - self.z;
        double oLength = Math.sqrt(ox * ox + oz * oz);
        if (oLength < 1.0E-4) return;
        double fx = Math.cos(self.yaw * 0.017453292F), fz = Math.sin(self.yaw * 0.017453292F);
        double fLength = Math.sqrt(fx * fx + fz * fz);
        double dot = Math.abs((ox / oLength) * (fx / fLength) + (oz / oLength) * (fz / fLength));
        if (dot < 0.800000011920929) return;
        boolean selfFurnace = self.kind == Kind.FURNACE_MINECART;
        boolean otherFurnace = other.kind == Kind.FURNACE_MINECART;
        if (otherFurnace && !selfFurnace) {
            self.vx *= 0.2;
            self.vz *= 0.2;
            self.vx += other.vx - dx;
            self.vz += other.vz - dz;
            other.vx *= 0.95;
            other.vz *= 0.95;
        } else if (!otherFurnace && selfFurnace) {
            other.vx *= 0.2;
            other.vz *= 0.2;
            other.vx += self.vx + dx;
            other.vz += self.vz + dz;
            self.vx *= 0.95;
            self.vz *= 0.95;
        } else {
            double mx = (other.vx + self.vx) / 2.0, mz = (other.vz + self.vz) / 2.0;
            self.vx *= 0.2;
            self.vz *= 0.2;
            self.vx += mx - dx;
            self.vz += mz - dz;
            other.vx *= 0.2;
            other.vz *= 0.2;
            other.vx += mx + dx;
            other.vz += mz + dz;
        }
    }

    // ── collision ────────────────────────────────────────────────────────

    /** {@code Entity#move(SELF)} against full-cube solid blocks (the {@link PrimedTntSystem} model). */
    private void move(Placed placed, double dx, double dy, double dz) {
        double clippedY = clipY(placed, dy);
        placed.y += clippedY;
        double clippedX = clipX(placed, dx);
        placed.x += clippedX;
        double clippedZ = clipZ(placed, dz);
        placed.z += clippedZ;
        boolean verticalCollision = Math.abs(dy - clippedY) > COLLISION_EPSILON;
        placed.onGround = dy < 0.0 && verticalCollision || dy == 0.0 && supported(placed);
        placed.horizontalCollision = Math.abs(dx - clippedX) > COLLISION_EPSILON
                || Math.abs(dz - clippedZ) > COLLISION_EPSILON;
        if (verticalCollision) placed.vy = 0.0;
        if (Math.abs(dx - clippedX) > COLLISION_EPSILON) placed.vx = 0.0;
        if (Math.abs(dz - clippedZ) > COLLISION_EPSILON) placed.vz = 0.0;
    }

    private boolean supported(Placed placed) {
        double half = placed.halfWidth();
        int y = floor(placed.y - COLLISION_EPSILON);
        if (placed.y - Math.floor(placed.y) > COLLISION_EPSILON) return false;
        for (int x = floor(placed.x - half + COLLISION_EPSILON); x <= floor(placed.x + half - COLLISION_EPSILON); x++) {
            for (int z = floor(placed.z - half + COLLISION_EPSILON); z <= floor(placed.z + half - COLLISION_EPSILON); z++) {
                if (solid(x, y, z)) return true;
            }
        }
        return false;
    }

    private double clipY(Placed e, double dy) {
        if (dy == 0.0) return 0.0;
        double half = e.halfWidth();
        int minX = floor(e.x - half + COLLISION_EPSILON), maxX = floor(e.x + half - COLLISION_EPSILON);
        int minZ = floor(e.z - half + COLLISION_EPSILON), maxZ = floor(e.z + half - COLLISION_EPSILON);
        if (dy > 0.0) {
            double top = e.y + e.kind.height;
            int end = floor(top + dy - COLLISION_EPSILON);
            for (int y = floor(top - COLLISION_EPSILON) + 1; y <= end; y++) {
                for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                    if (solid(x, y, z)) return Math.min(dy, y - top);
                }
            }
        } else {
            int end = floor(e.y + dy + COLLISION_EPSILON);
            for (int y = floor(e.y + COLLISION_EPSILON) - 1; y >= end; y--) {
                for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                    if (solid(x, y, z)) return Math.max(dy, y + 1.0 - e.y);
                }
            }
        }
        return dy;
    }

    private double clipX(Placed e, double dx) {
        if (dx == 0.0) return 0.0;
        double half = e.halfWidth();
        int minY = floor(e.y + COLLISION_EPSILON), maxY = floor(e.y + e.kind.height - COLLISION_EPSILON);
        int minZ = floor(e.z - half + COLLISION_EPSILON), maxZ = floor(e.z + half - COLLISION_EPSILON);
        if (dx > 0.0) {
            double edge = e.x + half;
            int end = floor(edge + dx - COLLISION_EPSILON);
            for (int x = floor(edge - COLLISION_EPSILON) + 1; x <= end; x++) {
                for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.min(dx, x - edge);
                }
            }
        } else {
            double edge = e.x - half;
            int end = floor(edge + dx + COLLISION_EPSILON);
            for (int x = floor(edge + COLLISION_EPSILON) - 1; x >= end; x--) {
                for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.max(dx, x + 1.0 - edge);
                }
            }
        }
        return dx;
    }

    private double clipZ(Placed e, double dz) {
        if (dz == 0.0) return 0.0;
        double half = e.halfWidth();
        int minY = floor(e.y + COLLISION_EPSILON), maxY = floor(e.y + e.kind.height - COLLISION_EPSILON);
        int minX = floor(e.x - half + COLLISION_EPSILON), maxX = floor(e.x + half - COLLISION_EPSILON);
        if (dz > 0.0) {
            double edge = e.z + half;
            int end = floor(edge + dz - COLLISION_EPSILON);
            for (int z = floor(edge - COLLISION_EPSILON) + 1; z <= end; z++) {
                for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.min(dz, z - edge);
                }
            }
        } else {
            double edge = e.z - half;
            int end = floor(edge + dz + COLLISION_EPSILON);
            for (int z = floor(edge + COLLISION_EPSILON) - 1; z >= end; z--) {
                for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.max(dz, z + 1.0 - edge);
                }
            }
        }
        return dz;
    }

    /** Entity fire/lava contact and ArmorStand's damage tags, once per unscaled game tick. */
    private void tickEnvironment(Placed entity) {
        boolean water = false, lava = false, fire = false;
        double half = entity.halfWidth();
        for (int x = floor(entity.x - half + COLLISION_EPSILON); x <= floor(entity.x + half - COLLISION_EPSILON); x++) {
            for (int y = floor(entity.y + COLLISION_EPSILON); y <= floor(entity.y + entity.kind.height - COLLISION_EPSILON); y++) {
                for (int z = floor(entity.z - half + COLLISION_EPSILON); z <= floor(entity.z + half - COLLISION_EPSILON); z++) {
                    int id = block(x, y, z);
                    water |= Fluids.isWater(id);
                    lava |= Fluids.isLava(id);
                    fire |= id == Blocks.FIRE || id == Blocks.CAMPFIRE
                            && (rt.blockState(x, y, z, id) & BuildingBlockRules.CAMPFIRE_LIT) != 0;
                }
            }
        }
        if (water) {
            if (entity.fireTicks != 0) { entity.fireTicks = 0; persist(); }
            return;
        }
        if (entity.fireTicks > 0) {
            if (entity.fireTicks % 20 == 0) {
                if (entity.kind == Kind.ARMOR_STAND) hurtBurningStand(entity, 4.0F);
                else hurtVehicle(entity, 1.0F, true, entity.x, entity.z);
            }
            entity.fireTicks--;
            if (entity.removed) return;
        }
        if (lava) {
            entity.fireTicks = Math.max(entity.fireTicks, 300);
            // ArmorStand ignores lava's direct damage; its on_fire damage above still applies.
            if (entity.kind.minecart()) hurtVehicle(entity, 4.0F, true, entity.x, entity.z);
            persist();
        } else if (fire) {
            if (entity.kind == Kind.ARMOR_STAND) {
                if (entity.fireTicks > 0) hurtBurningStand(entity, 0.15F);
                else entity.fireTicks = 100;
            } else {
                entity.fireTicks = Math.max(entity.fireTicks, 160);
                hurtVehicle(entity, 1.0F, true, entity.x, entity.z);
            }
            persist();
        }
    }

    private void hurtBurningStand(Placed stand, float amount) {
        stand.health -= amount;
        if (stand.health <= 0.5F) breakArmorStand(stand, false);
        else persist();
    }

    private boolean inWater(Placed placed) {
        double half = placed.halfWidth();
        for (int x = floor(placed.x - half); x <= floor(placed.x + half); x++) {
            for (int y = floor(placed.y); y <= floor(placed.y + placed.kind.height); y++) {
                for (int z = floor(placed.z - half); z <= floor(placed.z + half); z++) {
                    int b = block(x, y, z);
                    if (b != WorldTickLoop.UNAVAILABLE_BLOCK && Fluids.isWater(b)) return true;
                }
            }
        }
        return false;
    }

    private boolean solid(int x, int y, int z) {
        int b = block(x, y, z);
        return b == WorldTickLoop.UNAVAILABLE_BLOCK || Fluids.isSolid(b);
    }

    private int block(int x, int y, int z) {
        return WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
    }

    static boolean isRail(int id) {
        return id == Blocks.RAIL || id == Blocks.POWERED_RAIL || id == Blocks.DETECTOR_RAIL || id == Blocks.ACTIVATOR_RAIL;
    }

    int countMinecarts(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int count = 0;
        for (Placed cart : entities.values()) {
            if (cart.kind.minecart() && !cart.removed && intersects(cart, minX, minY, minZ, maxX, maxY, maxZ)) count++;
        }
        return count;
    }

    int minecartComparatorSignal(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (Placed cart : entities.values()) {
            if (!cart.removed && cart.cargo != null && intersects(cart, minX, minY, minZ, maxX, maxY, maxZ)) {
                return comparatorSignal(cart.cargo);
            }
        }
        return 0;
    }

    int railShape(int x, int y, int z) {
        int id = block(x, y, z);
        return Math.min(rt.blockState(x, y, z, id) & (id == Blocks.RAIL ? 0x0f : 7), id == Blocks.RAIL ? 9 : 5);
    }

    static boolean railSlope(int shape) {
        return shape >= 2 && shape <= 5;
    }

    private boolean isTickingChunk(Placed placed) {
        int chunkX = Math.floorDiv(floor(placed.x), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(floor(placed.z), Blocks.CHUNK_Z);
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return rt.activeSimulationChunksForMobTick().contains(key);
    }

    private boolean hasResidentMovementArea(Placed placed) {
        double half = placed.halfWidth() + 1.0;
        int minChunkX = Math.floorDiv(floor(placed.x - half), Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(floor(placed.x + half), Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(floor(placed.z - half), Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(floor(placed.z + half), Blocks.CHUNK_Z);
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (!rt.accessor().isChunkResident(cx, cz)) return false;
            }
        }
        return true;
    }

    // ── broadcast ────────────────────────────────────────────────────────

    private void remove(Placed placed) {
        if (placed.removed) return;
        ejectMobPassenger(placed);
        placed.removed = true;
        entities.remove(placed.id);
        for (String nickname : List.copyOf(openCargo.keySet())) {
            if (openCargo.get(nickname) != placed.id) continue;
            PlayerTickState viewer = rt.players().get(nickname);
            if (viewer != null) closeCargo(viewer, true);
            else openCargo.remove(nickname);
        }
        broadcast(new WsMessages.PlacedEntityRemove(List.of(placed.id)));
        persist();
    }

    WsMessages.PlacedEntityDto dto(Placed placed) {
        List<Integer> equipment;
        if (placed.kind == Kind.ARMOR_STAND) {
            equipment = new ArrayList<>(6);
            for (PlayerInventory.StackSnapshot stack : placed.equipment) {
                equipment.add(Short.toUnsignedInt(stack.itemType()));
            }
            equipment = List.copyOf(equipment);
        } else {
            equipment = List.of();
        }
        boolean lit = placed.kind == Kind.FURNACE_MINECART ? placed.hasFuel()
                : placed.kind == Kind.HOPPER_MINECART && placed.hopperEnabled;
        return new WsMessages.PlacedEntityDto(placed.id, placed.kind.protocol, placed.x,
                placed.y, placed.z, placed.yaw, placed.pitch, placed.rider, placed.hurtTime,
                placed.hurtDir, placed.damage, equipment, placed.flags, lit, placed.fuse)
                .withMobPassenger(placed.mobPassengerId)
                .withEquipmentDetails(placed.kind == Kind.ARMOR_STAND
                        ? java.util.Arrays.stream(placed.equipment).map(stack -> {
                            var components = com.gameexpert.engine.inventory.ItemComponentCodec.decode(
                                    stack.itemType(), stack.itemComponentData());
                            var trim = components.trim();
                            return new WsMessages.PlacedEquipmentAppearance(
                                    !components.enchantments(stack.enchantments()).isEmpty(),
                                    components.leatherColor(), trim == null ? null : trim.pattern() + ":" + trim.material());
                        }).toList() : List.of());
    }

    private static boolean sameBroadcast(WsMessages.PlacedEntityDto a, WsMessages.PlacedEntityDto b) {
        if (a == null) return false;
        return Math.abs(a.getX() - b.getX()) <= POS_EPS && Math.abs(a.getY() - b.getY()) <= POS_EPS
                && Math.abs(a.getZ() - b.getZ()) <= POS_EPS
                && Math.abs(a.getYaw() - b.getYaw()) <= 0.01F && a.getPitch() == b.getPitch()
                && java.util.Objects.equals(a.getRider(), b.getRider())
                && a.getMobPassengerId() == b.getMobPassengerId()
                && a.getHurtTime() == b.getHurtTime() && a.getHurtDir() == b.getHurtDir()
                && a.getDamage() == b.getDamage() && a.getEquipment().equals(b.getEquipment())
                && a.getEquipmentDetails().equals(b.getEquipmentDetails())
                && a.getFlags() == b.getFlags() && a.isLit() == b.isLit()
                && a.getFuse() == b.getFuse();
    }

    private void worldSound(String kind, double x, double y, double z, short blockType) {
        WsMessages.WorldSound message = new WsMessages.WorldSound(rt.nextEventId(), kind, x, y, z,
                blockType);
        double range = SoundRules.worldSoundRange(kind);
        for (PlayerTickState player : rt.players().values()) {
            if (SoundRules.audible(x, y, z, player.x(), player.y(), player.z(), range)) {
                sendTo(player, message);
            }
        }
    }

    private void sendInventory(PlayerTickState player) {
        sendTo(player, WorldTickLoop.inventoryMessage(player));
    }

    private void sendTo(PlayerTickState player, Object message) {
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
        }
    }

    private void broadcast(Object message) {
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
    }

    // ── math ─────────────────────────────────────────────────────────────

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code Mth.wrapDegrees(float)}. */
    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    /** {@code Direction.toYRot()} for a 3D data value (0 down, 1 up, 2 north, 3 south, 4 west, 5 east). */
    static float directionYRot(int facing3d) {
        int data2d = switch (facing3d) {
            case 3 -> 0;   // south
            case 4 -> 1;   // west
            case 2 -> 2;   // north
            case 5 -> 3;   // east
            default -> -1; // up, down
        };
        return (data2d & 3) * 90.0F;
    }

    private static int faceX(int face) {
        return face == 4 ? -1 : face == 5 ? 1 : 0;
    }

    private static int faceY(int face) {
        return face == 0 ? -1 : face == 1 ? 1 : 0;
    }

    private static int faceZ(int face) {
        return face == 2 ? -1 : face == 3 ? 1 : 0;
    }
}
