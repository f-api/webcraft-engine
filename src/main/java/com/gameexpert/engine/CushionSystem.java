package com.gameexpert.engine;

import com.gameexpert.cushion.dto.CushionSnapshot;
import com.gameexpert.cushion.service.CushionPersistenceService;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages.CushionDismount;
import com.gameexpert.ws.dto.WsMessages.CushionDto;
import com.gameexpert.ws.dto.WsMessages.CushionMount;
import com.gameexpert.ws.dto.WsMessages.CushionRemove;
import com.gameexpert.ws.dto.WsMessages.CushionSpawn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Minecraft 26.3 cushion decoration entities. Mutable state belongs exclusively to the world tick;
 * websocket threads enqueue semantic actions. The colored 1282..1297 identities are items, never
 * resident blocks.
 */
final class CushionSystem {
    static final double WIDTH = 1.0;
    static final double HEIGHT = 0.25;

    private final WorldRuntime rt;
    private final List<Cushion> cushions = new ArrayList<>();
    private final Map<String, Long> riddenByPlayer = new HashMap<>();

    /** [ENCHANT-WIDE] 이 플레이어가 방석에 앉아 있는가(돌진 인챈트의 탈것 요건). */
    boolean isRiding(String nickname) {
        return riddenByPlayer.containsKey(nickname);
    }
    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private volatile List<CushionDto> welcomeSnapshot = List.of();
    private CushionPersistenceService persistence;
    private long nextId = 1;
    private final ConcurrentLinkedQueue<PersistenceCompletion> persistenceCompletions =
            new ConcurrentLinkedQueue<>();
    private long persistenceRevision;
    private boolean persistenceDirty;
    private boolean persistenceInFlight;

    CushionSystem(WorldRuntime rt) {
        this.rt = rt;
    }

    void installPersistence(CushionPersistenceService service) {
        persistence = service;
        if (service == null) return;
        for (CushionSnapshot snapshot : service.loadWorld(rt.worldId())) {
            if (!Blocks.isCushion(Short.toUnsignedInt(snapshot.getItemType()))) continue;
            cushions.add(new Cushion(snapshot.getCushionId(), snapshot.getItemType(), snapshot.getX(),
                    snapshot.getY(), snapshot.getZ(), snapshot.getYaw(),
                    snapshot.getItemComponentData()));
            nextId = Math.max(nextId, snapshot.getCushionId() + 1);
        }
        publishSnapshot();
    }

    List<CushionDto> welcomeSnapshot() {
        return welcomeSnapshot;
    }

    void enqueuePlace(String nickname, double x, double y, double z, PlayerInventory.Hand hand) {
        if (hand == null) throw new IllegalArgumentException("hand is required");
        commands.add(new Place(nickname, x, y, z, hand, null));
    }

    /** Places from the hand captured when the ordered action applied, not the tick-time selection. */
    void enqueuePlace(String nickname, double x, double y, double z,
            PlayerInventory.HandRef capturedHand) {
        if (capturedHand == null) throw new IllegalArgumentException("hand is required");
        commands.add(new Place(nickname, x, y, z, capturedHand.hand(), capturedHand));
    }

    void enqueueSit(String nickname, long cushionId) {
        commands.add(new Sit(nickname, cushionId));
    }

    void enqueueLeave(String nickname) {
        commands.add(new Leave(nickname));
    }

    void enqueueBreak(String nickname, long cushionId) {
        commands.add(new Break(nickname, cushionId));
    }

    void playerLeft(String nickname) {
        leave(nickname);
        publishSnapshot();
    }

    void playerDied(String nickname) {
        leave(nickname);
        publishSnapshot();
    }

    void tick(long tickNo) {
        drainPersistenceCompletions();
        Command command;
        while ((command = commands.poll()) != null) {
            if (command instanceof Place place) place(place);
            else if (command instanceof Sit sit) sit(sit);
            else if (command instanceof Leave leave) leave(leave.getNickname());
            else if (command instanceof Break breaking) breakCushion(breaking);
        }
        syncRiders();
        // BlockAttachedEntity rechecks its attachment every 100 game ticks. This authority ticks at
        // 10 Hz, hence every 50 ticks is the same five-second lifecycle edge.
        if (tickNo % 50 == 0) removeUnsupported();
        submitPersistenceIfNeeded();
        publishSnapshot();
    }

    private void place(Place place) {
        if (!finite(place.getX(), place.getY(), place.getZ())) return;
        PlayerTickState player = rt.players().get(place.getNickname());
        if (player == null || player.isDead()
                || riddenByPlayer.containsKey(place.getNickname())
                || !withinReach(player, place.getX(), place.getY(), place.getZ())) return;
        PlayerInventory.HandRef hand = place.getCapturedHand() == null
                ? player.inventory().capture(place.getHand())
                : player.inventory().capture(place.getHand(), place.getCapturedHand().mainSlot());
        PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
        short item = held.itemType();
        if (!Blocks.isCushion(Short.toUnsignedInt(item))) return;

        double x = Math.floor(place.getX()) + 0.5;
        double z = Math.floor(place.getZ()) + 0.5;
        double y = place.getY();
        if (!validAnchor(x, y, z) || overlaps(x, y, z, -1)) return;
        if (!player.inventory().consumeOne(hand, item)) return;

        Cushion cushion = new Cushion(nextId++, item, x, y, z, snapYaw(player.yaw()),
                held.itemComponentData());
        cushions.add(cushion);
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new CushionSpawn(cushion.dto()));
        sendInventory(player);
        rt.queuePlayerInventoryBaseline(player);
        persist();
        rt.tickLoop().emitDecorationVibration(
                com.gameexpert.engine.sculk.SculkVibrationRules.Event.BLOCK_PLACE,
                x, y, z, player.nickname());
    }

    private void sit(Sit sit) {
        Cushion cushion = find(sit.getCushionId());
        PlayerTickState player = rt.players().get(sit.getNickname());
        if (cushion == null || cushion.rider != null || player == null || player.isDead()
                || player.crouching() || riddenByPlayer.containsKey(sit.getNickname())
                || !withinReach(player, cushion.x, cushion.y, cushion.z)) return;
        cushion.rider = sit.getNickname();
        riddenByPlayer.put(sit.getNickname(), cushion.id);
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new CushionMount(cushion.id, sit.getNickname()));
    }

    /** [SPEAR-MOB] 창 돌진의 하마({@code Entity.stopRiding}): 앉아 있었으면 내리고 참. */
    boolean forceLeave(String nickname) {
        if (!riddenByPlayer.containsKey(nickname)) return false;
        leave(nickname);
        return true;
    }

    private void leave(String nickname) {
        Long id = riddenByPlayer.remove(nickname);
        if (id == null) return;
        Cushion cushion = find(id);
        if (cushion != null) cushion.rider = null;
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new CushionDismount(id, nickname));
    }

    private void breakCushion(Break breaking) {
        Cushion cushion = find(breaking.getCushionId());
        PlayerTickState player = rt.players().get(breaking.getNickname());
        if (cushion == null || player == null || player.isDead()
                || !withinReach(player, cushion.x, cushion.y, cushion.z)) return;
        remove(cushion, true);
        rt.tickLoop().emitDecorationVibration(
                com.gameexpert.engine.sculk.SculkVibrationRules.Event.BLOCK_BREAK,
                cushion.x, cushion.y, cushion.z, player.nickname());
    }

    private void removeUnsupported() {
        for (Cushion cushion : List.copyOf(cushions)) {
            if (Boolean.FALSE.equals(survivesAt(rt, cushion.x, cushion.y, cushion.z))) remove(cushion, true);
        }
    }

    private void syncRiders() {
        for (Cushion cushion : cushions) {
            if (cushion.rider == null) continue;
            PlayerTickState player = rt.players().get(cushion.rider);
            if (player == null || player.isDead()) {
                leave(cushion.rider);
                continue;
            }
            player.forcePose(cushion.x, cushion.y + HEIGHT, cushion.z,
                    cushion.yaw, player.pitch());
        }
    }

    private void remove(Cushion cushion, boolean drop) {
        if (cushion.rider != null) leave(cushion.rider);
        cushions.remove(cushion);
        if (drop) rt.itemSystem().spawnDrop(cushion.itemType, 1,
                PlayerInventory.initialDurability(cushion.itemType), 0, 0, 0, null,
                cushion.itemComponentData, cushion.x, cushion.y + HEIGHT, cushion.z);
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new CushionRemove(cushion.id));
        persist();
    }

    /** Current Spring block point reads expose type only, so accept the exact top of a full block. */
    private boolean validAnchor(double x, double y, double z) {
        if (Math.abs(y - Math.rint(y)) > 1.0e-6) return false;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        int support = WorldTickLoop.residentBlockType(rt.accessor(), bx, by - 1, bz);
        int occupied = WorldTickLoop.residentBlockType(rt.accessor(), bx, by, bz);
        return support != WorldTickLoop.UNAVAILABLE_BLOCK && Fluids.isSolid(support)
                && occupied != WorldTickLoop.UNAVAILABLE_BLOCK && !Fluids.isSolid(occupied);
    }

    /** Unknown cells defer removal; only complete suffocating coverage destroys an anchored cushion. */
    static Boolean survivesAt(WorldRuntime rt, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int supportY = (int) Math.ceil(y) - 1;
        int support = WorldTickLoop.residentBlockType(rt.accessor(), bx, supportY, bz);
        if (support == WorldTickLoop.UNAVAILABLE_BLOCK) return null;
        int supportState = rt.blockStates().get(bx, supportY, bz, support);
        double top = BuildingBlockRules.collisionHeight(support, supportState);
        if (top <= 0 || Math.abs(supportY + top - y) > 1.0e-6) return false;
        boolean unknown = false;
        for (int cy = (int) Math.floor(y + 1.0e-7);
                cy <= (int) Math.floor(y + HEIGHT - 1.0e-7); cy++) {
            int block = WorldTickLoop.residentBlockType(rt.accessor(), bx, cy, bz);
            if (block == WorldTickLoop.UNAVAILABLE_BLOCK) { unknown = true; continue; }
            if (!CushionSuffocationRules.suffocates(block, rt.blockStates().get(bx, cy, bz, block))) return true;
        }
        return unknown ? null : false;
    }

    private boolean overlaps(double x, double y, double z, long exceptId) {
        for (Cushion cushion : cushions) {
            if (cushion.id == exceptId) continue;
            if (Math.abs(cushion.x - x) < WIDTH
                    && Math.abs(cushion.z - z) < WIDTH
                    && cushion.y < y + HEIGHT && y < cushion.y + HEIGHT) return true;
        }
        return false;
    }

    static float snapYaw(float yaw) {
        return (float) (Math.floor(yaw / 90.0 + 0.5) * 90.0);
    }

    static boolean withinReach(PlayerTickState player, double x, double y, double z) {
        return PlayerInteractionRules.canInteractWithEntity(player.x(), player.y(), player.z(),
                player.crouching(), x, y, z, WIDTH, HEIGHT);
    }

    private void persist() {
        publishSnapshot();
        persistenceRevision++;
        persistenceDirty = true;
        submitPersistenceIfNeeded();
    }

    private void submitPersistenceIfNeeded() {
        if (!persistenceDirty || persistenceInFlight || persistence == null
                || rt.ctx().persistenceExecutor() == null) return;
        long revision = persistenceRevision;
        List<CushionSnapshot> snapshot = cushions.stream().map(Cushion::snapshot).toList();
        boolean accepted = rt.ctx().persistenceExecutor().trySubmit(() -> {
            try {
                persistence.replaceWorld(rt.worldId(), snapshot);
                persistenceCompletions.add(new PersistenceCompletion(revision, true));
            } catch (RuntimeException | Error exception) {
                persistenceCompletions.add(new PersistenceCompletion(revision, false));
                throw exception;
            }
        });
        if (accepted) persistenceInFlight = true;
    }

    private void drainPersistenceCompletions() {
        PersistenceCompletion completion;
        while ((completion = persistenceCompletions.poll()) != null) {
            persistenceInFlight = false;
            if (completion.isSuccess() && completion.getRevision() == persistenceRevision) {
                persistenceDirty = false;
            }
        }
    }

    void flushForDisposal() {
        if (persistence == null) return;
        List<CushionSnapshot> snapshot = cushions.stream().map(Cushion::snapshot).toList();
        rt.submitDisposalPersistence(() -> persistence.replaceWorld(rt.worldId(), snapshot));
    }

    private void publishSnapshot() {
        welcomeSnapshot = cushions.stream().map(Cushion::dto).toList();
    }

    private Cushion find(long id) {
        for (Cushion cushion : cushions) if (cushion.id == id) return cushion;
        return null;
    }

    private void sendInventory(PlayerTickState player) {
        var session = rt.session(player.nickname());
        if (session != null) rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                WorldTickLoop.inventoryMessage(player));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static final class Cushion {
        final long id;
        final short itemType;
        final double x;
        final double y;
        final double z;
        final float yaw;
        final String itemComponentData;
        String rider;

        Cushion(long id, short itemType, double x, double y, double z, float yaw,
                String itemComponentData) {
            this.id = id;
            this.itemType = itemType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.itemComponentData = itemComponentData;
        }

        CushionSnapshot snapshot() {
            return new CushionSnapshot(id, itemType, x, y, z, yaw, itemComponentData);
        }

        CushionDto dto() {
            String customName = com.gameexpert.engine.inventory.ItemComponentCodec
                    .decode(itemType, itemComponentData).customName();
            return new CushionDto(id, itemType, x, y, z, yaw, rider, customName);
        }
    }

    private sealed interface Command permits Place, Sit, Leave, Break {}

    @Getter
    @AllArgsConstructor
    private static final class Place implements Command {
        private final String nickname;
        private final double x;
        private final double y;
        private final double z;
        private final PlayerInventory.Hand hand;
        /** Hand captured by the ordered action; null keeps the tick-time selection. */
        private final PlayerInventory.HandRef capturedHand;
    }

    @Getter
    @AllArgsConstructor
    private static final class Sit implements Command {
        private final String nickname;
        private final long cushionId;
    }

    @Getter
    @AllArgsConstructor
    private static final class Leave implements Command {
        private final String nickname;
    }

    @Getter
    @AllArgsConstructor
    private static final class Break implements Command {
        private final String nickname;
        private final long cushionId;
    }

    @Getter
    @AllArgsConstructor
    private static final class PersistenceCompletion {
        private final long revision;
        private final boolean success;
    }
}
