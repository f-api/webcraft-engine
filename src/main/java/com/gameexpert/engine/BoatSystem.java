package com.gameexpert.engine;

import com.gameexpert.boat.dto.BoatSnapshot;
import com.gameexpert.boat.service.BoatPersistenceService;
import com.gameexpert.engine.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages.BoatDespawn;
import com.gameexpert.ws.dto.WsMessages.BoatDto;
import com.gameexpert.ws.dto.WsMessages.BoatMount;
import com.gameexpert.ws.dto.WsMessages.BoatPosDto;
import com.gameexpert.ws.dto.WsMessages.BoatSpawn;
import com.gameexpert.ws.dto.WsMessages.BoatUpdate;
import com.gameexpert.ws.dto.WsMessages.InventoryUpdate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 클라 권위 보트 시스템(월드당 1개, 틱 스레드 전용). 서버는 <b>자체 물리를 굴리지 않고</b> 좌석·중계·수명만
 * 담당한다(운전자 클라가 부유·전후진·조향·충돌을 계산해 {@code boatPos} 로 올린다 — 플레이어 이동과 동일 신뢰 모델).
 *
 * <p>틱 순서 ③.7(드랍 아이템 뒤)에서 {@link #tick} 이 호출된다: 명령 큐 드레인(설치·탑승·하차·위치반영·파괴) →
 * 변경된 보트 위치 배칭 브로드캐스트 → welcome 스냅샷 게시. 빈 보트는 아무도 {@code boatPos} 를 보내지 않으므로
 * 마지막 위치에 정지한다. 설치된 보트는 {@link BoatPersistenceService} 로 월드에 새겨지므로, 월드가 비어
 * {@link WorldRuntime} 가 폐기됐다가 다시 만들어져도 같은 id·같은 좌표로 되살아난다(플레이어 좌석은 세션 상태라
 * 저장하지 않고, 몹 승객은 근접 자동 탑승으로 다시 태워진다).
 *
 * <p>스레딩 불변식: WS 스레드는 {@code enqueue*} 로 명령을 넣기만 하고(스레드 안전 큐), 상태 변경은 전부 틱 스레드.
 */
final class BoatSystem {

    // ID 는 PlayerInventory 가 단일 출처다(2026-07-20 아이템 256+ 분리).
    static final short BOAT_ITEM = PlayerInventory.BOAT;
    private static final double POS_EPS = 1e-3;
    static final int CAPACITY = 2;
    private static final double BOAT_WIDTH = 1.375;
    private static final double BOAT_HEIGHT = 0.5625;
    /** 운전자 pose 와 보트 좌표 사이에 허용하는 최대 간격(블록). */
    static final double DRIVER_POS_RANGE = 16.0;
    static final double MOB_BOARD_RANGE = 0.9;
    private static final double MOB_BOARD_VERTICAL_RANGE = 1.25;
    private static final double MOB_SEAT_Y = 0.38;

    private final WorldRuntime rt;

    private final List<Boat> boats = new ArrayList<>();
    private final Map<String, Long> riderOf = new HashMap<>(); // 닉네임 → 탑승 중인 보트 id

    /** [ENCHANT-WIDE] 이 플레이어가 보트에 타고 있는가(돌진 인챈트의 탈것 요건). */
    boolean isRiding(String nickname) {
        return riderOf.containsKey(nickname);
    }
    private final ConcurrentLinkedQueue<Cmd> commands = new ConcurrentLinkedQueue<>();
    private long nextId = 1;

    private BoatPersistenceService persistence;
    private final ConcurrentLinkedQueue<PersistenceCompletion> persistenceCompletions =
            new ConcurrentLinkedQueue<>();
    private long persistenceRevision;
    private boolean persistenceDirty;
    private boolean persistenceInFlight;

    // WS 스레드가 welcome 구성 시 읽는 현재 스냅샷(불변).
    private volatile List<BoatDto> welcomeSnapshot = List.of();
    private boolean snapshotDirty;

    BoatSystem(WorldRuntime rt) {
        this.rt = rt;
    }

    /**
     * 저장된 보트를 런타임 생성 시점에 그대로 되살린다. id 발급기는 불러온 최대 id 위에서 다시 시작해
     * 재입장 뒤 설치한 보트가 기존 보트의 id 를 덮어쓰지 않게 한다.
     */
    void installPersistence(BoatPersistenceService service) {
        persistence = service;
        if (service == null) return;
        for (BoatSnapshot snapshot : service.loadWorld(rt.worldId())) {
            Boat boat = new Boat(snapshot.getBoatId(), snapshot.getX(), snapshot.getY(),
                    snapshot.getZ(), snapshot.getYaw());
            boat.markPersisted();
            boats.add(boat);
            nextId = Math.max(nextId, snapshot.getBoatId() + 1);
        }
        snapshotDirty = true;
        publishSnapshot();
    }

    List<BoatDto> welcomeSnapshot() {
        return welcomeSnapshot;
    }

    // ── WS 스레드 진입점(enqueue only) ──────────────────────────────────
    void enqueuePlace(String nickname, double x, double y, double z, double yaw) {
        enqueuePlace(nickname, x, y, z, yaw, PlayerInventory.Hand.MAIN);
    }

    void enqueuePlace(String nickname, double x, double y, double z, double yaw,
            PlayerInventory.Hand hand) {
        if (hand == null) throw new IllegalArgumentException("hand is required");
        commands.add(new Place(nickname, x, y, z, yaw, hand, null));
    }

    /**
     * 틱 스레드의 액션 적용이 도착 순서대로 잡은 손으로 설치한다. 보트 틱까지 사이에 스크롤이
     * 먼저 적용돼도 지금 선택 칸이 아니라 이 손의 칸에서 보트를 꺼낸다.
     */
    void enqueuePlace(String nickname, double x, double y, double z, double yaw,
            PlayerInventory.HandRef capturedHand) {
        if (capturedHand == null) throw new IllegalArgumentException("hand is required");
        commands.add(new Place(nickname, x, y, z, yaw, capturedHand.hand(), capturedHand));
    }

    void enqueueBoard(String nickname, long boatId) {
        commands.add(new Board(nickname, boatId));
    }

    void enqueueLeave(String nickname) {
        commands.add(new Leave(nickname));
    }

    void enqueuePos(String nickname, long boatId, double x, double y, double z, double yaw) {
        commands.add(new Pos(nickname, boatId, x, y, z, yaw));
    }

    void enqueueBreak(String nickname, long boatId) {
        commands.add(new Break(nickname, boatId));
    }

    /** 틱 소유자의 세션 종료 처리: 해당 좌석만 비우고 남은 플레이어에게 운전을 넘긴다. */
    void playerLeft(String nickname) {
        leave(nickname);
        publishSnapshot();
    }

    /** 사망 방송보다 먼저 운전 권한과 welcome 스냅샷을 정리한다. */
    void playerDied(String nickname) {
        leave(nickname);
        publishSnapshot();
    }

    // ── 틱 처리 ────────────────────────────────────────────────────────
    void tick(long tickNo) {
        drainPersistenceCompletions();
        Cmd cmd;
        while ((cmd = commands.poll()) != null) {
            apply(cmd);
        }
        syncAndBoardMobs();
        broadcastUpdates();
        // 주행 중에는 매 틱 좌표가 바뀌므로 틱마다 쓰지 않는다. 5초(50틱)마다 운전자가 없어 정지한
        // 보트만 골라 저장된 좌표와 다를 때 한 번 새긴다.
        if (tickNo % 50 == 0) persistSettledPositions();
        submitPersistenceIfNeeded();
        publishSnapshot();
    }

    /** MobSystem AI보다 먼저 호출되어 탑승 좌표/면제 플래그가 디스폰 판정보다 앞서 확정된다. */
    void prepareMobTick() {
        syncAndBoardMobs();
    }

    private void apply(Cmd cmd) {
        if (cmd instanceof Place p) {
            place(p);
        } else if (cmd instanceof Board b) {
            board(b);
        } else if (cmd instanceof Leave l) {
            leave(l.nickname());
        } else if (cmd instanceof Pos p) {
            pos(p);
        } else if (cmd instanceof Break br) {
            breakBoat(br);
        }
    }

    // 설치: 선택 슬롯의 보트 아이템 1개 소모 → 물/지면 위 스폰 → 배치자 자동 탑승(운전자).
    private void place(Place p) {
        if (!finite(p.x(), p.y(), p.z(), p.yaw())) {
            return;
        }
        PlayerTickState player = rt.players().get(p.nickname());
        if (player == null || player.isDead()) {
            return;
        }
        if (riderOf.containsKey(p.nickname())) {
            return; // 이미 다른 보트 탑승 중
        }
        if (!withinPlayerReach(player, p.x(), p.y(), p.z())) {
            return;
        }
        int bx = floor(p.x());
        int by = floor(p.y());
        int bz = floor(p.z());
        int occupied = WorldTickLoop.residentBlockType(rt.accessor(), bx, by, bz);
        int below = WorldTickLoop.residentBlockType(rt.accessor(), bx, by - 1, bz);
        int above = WorldTickLoop.residentBlockType(rt.accessor(), bx, by + 1, bz);
        if (!validPlacementCell(occupied, below, above)) {
            return;
        }
        PlayerInventory.HandRef hand = p.capturedHand() == null
                ? player.inventory().capture(p.hand())
                : player.inventory().capture(p.hand(), p.capturedHand().mainSlot());
        if (!player.inventory().consumeOne(hand, BOAT_ITEM)) {
            return; // 요청 손에 보트 아이템이 없음
        }
        Boat boat = new Boat(nextId++, p.x(), p.y(), p.z(), p.yaw());
        boat.driver = p.nickname();
        boats.add(boat);
        riderOf.put(p.nickname(), boat.id);
        snapshotDirty = true;
        broadcast(new BoatSpawn(boat.id, boat.x, boat.y, boat.z, boat.yaw));
        broadcastMount(boat, p.nickname());
        sendTo(player, WorldTickLoop.inventoryMessage(player));
        persist();
    }

    /**
     * [CONTAINER-MENUS] {@code BoatDispenseItemBehavior}: an empty (driverless) boat at the given
     * position and yaw, on the same spawn broadcast and persistence path as a placed one.
     */
    long placeDispensed(double x, double y, double z, double yaw) {
        if (!finite(x, y, z, yaw)) return 0L;
        Boat boat = new Boat(nextId++, x, y, z, yaw);
        boats.add(boat);
        snapshotDirty = true;
        broadcast(new BoatSpawn(boat.id, boat.x, boat.y, boat.z, boat.yaw));
        persist();
        return boat.id;
    }

    // 탑승: 몹을 포함한 두 좌석 중 빈자리를 배정하고 기존 운전자를 유지한다.
    private void board(Board b) {
        Boat boat = find(b.boatId());
        if (boat == null || riderOf.containsKey(b.nickname())
                || boat.occupiedSeats() >= CAPACITY) {
            return;
        }
        PlayerTickState player = rt.players().get(b.nickname());
        if (player == null || player.isDead()
                || !withinPlayerReach(player, boat.x, boat.y, boat.z)) {
            return;
        }
        if (boat.driver == null) boat.driver = b.nickname();
        else boat.playerPassengerNames.add(b.nickname());
        riderOf.put(b.nickname(), boat.id);
        boat.passengersDirty = true;
        snapshotDirty = true;
        broadcastMount(boat, b.nickname());
    }

    private void broadcastMount(Boat boat, String nickname) {
        broadcast(new BoatMount(boat.id, nickname, boat.driver,
                List.copyOf(boat.playerPassengerNames), List.copyOf(boat.mobPassengers)));
    }

    /** [SPEAR-MOB] 창 돌진의 하마({@code Entity.stopRiding}): 타고 있었으면 내리고 참. */
    boolean forceLeave(String nickname) {
        if (!riderOf.containsKey(nickname)) return false;
        leave(nickname);
        return true;
    }

    // 하차(자발적 leaveBoat 또는 세션 종료): 해당 좌석만 비우고 마지막 플레이어가 떠나면 정지한다.
    private void leave(String nickname) {
        Long boatId = riderOf.remove(nickname);
        if (boatId == null) {
            return;
        }
        Boat boat = find(boatId);
        if (boat != null) {
            if (nickname.equals(boat.driver)) {
                boat.driver = boat.playerPassengerNames.isEmpty()
                        ? null : boat.playerPassengerNames.removeFirst();
            } else {
                boat.playerPassengerNames.remove(nickname);
            }
            boat.passengersDirty = true;
            snapshotDirty = true;
            // 마지막 플레이어가 떠난 좌표만 정착 위치로 새긴다.
            if (boat.driver == null && !boat.persistedMatches()) persist();
            broadcast(new com.gameexpert.ws.dto.WsMessages.BoatDismount(boatId, nickname,
                    boat.driver, List.copyOf(boat.playerPassengerNames), List.copyOf(boat.mobPassengers)));
        }
    }

    // 위치 반영: 운전자가 보낸 값만 신뢰(유효성만 가볍게 검증). 중계는 broadcastUpdates 에서 배칭.
    private void pos(Pos p) {
        Long driving = riderOf.get(p.nickname());
        if (driving == null || driving != p.boatId()) {
            return; // 그 보트의 운전자가 아니면 무시
        }
        PlayerTickState player = rt.players().get(p.nickname());
        if (player == null || player.isDead()) {
            return;
        }
        if (!finite(p.x(), p.y(), p.z(), p.yaw())) {
            return;
        }
        // 유한성만으로는 1e300 같은 좌표가 그대로 전원에게 방송된다. 월드 경계와 운전자 pose 근처
        // 라는 두 상한을 함께 요구한다. 운전자 클라는 보트에 플레이어 pose 를 붙여 올리고 그 pose 는
        // MovementLimits 로 이미 속도 제한을 받으므로, 이 검사가 곧 보트의 속도 상한이 된다.
        if (!MovementLimits.withinWorldBounds(p.x(), p.y(), p.z())
                || !withinDriverRange(player, p.x(), p.y(), p.z())) {
            return;
        }
        Boat boat = find(p.boatId());
        if (boat == null || !p.nickname().equals(boat.driver)) {
            return;
        }
        if (Double.compare(boat.x, p.x()) != 0
                || Double.compare(boat.y, p.y()) != 0
                || Double.compare(boat.z, p.z()) != 0
                || Double.compare(boat.yaw, p.yaw()) != 0) {
            snapshotDirty = true;
        }
        boat.x = p.x();
        boat.y = p.y();
        boat.z = p.z();
        boat.yaw = p.yaw();
    }

    // 파괴: 보트 제거 + 보트 아이템 1개 드랍(기존 드랍 파이프라인 재사용) + 소멸 방송.
    private void breakBoat(Break br) {
        Boat boat = find(br.boatId());
        if (boat == null) {
            return;
        }
        PlayerTickState player = rt.players().get(br.nickname());
        if (player == null || player.isDead()
                || !withinPlayerReach(player, boat.x, boat.y, boat.z)) {
            return;
        }
        boats.remove(boat);
        snapshotDirty = true;
        if (boat.driver != null) {
            riderOf.remove(boat.driver);
        }
        for (String passenger : boat.playerPassengerNames) riderOf.remove(passenger);
        dismountAllMobs(boat);
        rt.itemSystem().spawnDrop(BOAT_ITEM, 1, boat.x, boat.y + 0.3, boat.z);
        broadcast(new BoatDespawn(List.of(boat.id)));
        persist();
    }

    /**
     * 보트가 운전자의 서버 권위 pose 근처인가. 운전 중 클라는 플레이어 pose 를 보트 좌표로
     * 맞춰 올리므로(BoatManager) 정상 주행에서 둘의 간격은 1블록 안쪽이다. 16블록은 pose 가
     * 몇 틱 뒤처져 도착해도(보트 최고 8블록/초 = 0.8블록/틱) 남는 여유다.
     */
    static boolean withinDriverRange(PlayerTickState driver, double x, double y, double z) {
        double dx = x - driver.x();
        double dy = y - driver.y();
        double dz = z - driver.z();
        return dx * dx + dy * dy + dz * dz <= DRIVER_POS_RANGE * DRIVER_POS_RANGE;
    }

    static boolean withinPlayerReach(PlayerTickState player, double x, double y, double z) {
        return PlayerInteractionRules.canInteractWithEntity(
                player.x(), player.y(), player.z(), player.crouching(),
                x, y, z, BOAT_WIDTH, BOAT_HEIGHT);
    }

    static boolean validPlacementCell(int occupied, int below, int above) {
        if (occupied == WorldTickLoop.UNAVAILABLE_BLOCK
                || below == WorldTickLoop.UNAVAILABLE_BLOCK
                || above == WorldTickLoop.UNAVAILABLE_BLOCK
                || Fluids.isSolid(above)) {
            return false;
        }
        return Fluids.isWater(occupied)
                || occupied == Blocks.LILY_PAD && Fluids.isWater(below)
                || !Fluids.isSolid(occupied) && Fluids.isSolid(below);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * 보트 측면 충돌 자동 탑승. 플레이어가 이미 탄 보트에는 새 몹을 자동으로 넣지 않고,
     * 빈 보트는 총 정원 2까지 받는다. 기존 몹 승객이 하나뿐이면 플레이어가 남은 좌석에 탈 수 있다.
     */
    private void syncAndBoardMobs() {
        List<Mob> activeCandidates = rt.mobSystem().mobsForBoats();
        for (Boat boat : boats) {
            if (boat.mobPassengers.removeIf(id -> {
                Mob mob = rt.mobSystem().mobForBoat(id);
                if (mob != null && !mob.isDead() && !mob.removed) return false;
                if (mob != null) {
                    mob.dismountBoat(mob.x, mob.y, mob.z);
                    rt.mobSystem().reindexBoatMob(mob);
                }
                return true;
            })) {
                boat.passengersDirty = true;
                snapshotDirty = true;
            }
            syncPassengers(boat);
            if (boat.driver != null || boat.mobPassengers.size() >= CAPACITY) continue;
            for (Mob mob : activeCandidates) {
                if (mob.isDead() || mob.removed || mob.isRidingBoat() || mob.isRidingPlacedVehicle()) continue;
                double dx = mob.x - boat.x;
                double dz = mob.z - boat.z;
                if (dx * dx + dz * dz > MOB_BOARD_RANGE * MOB_BOARD_RANGE
                        || Math.abs(mob.y - boat.y) > MOB_BOARD_VERTICAL_RANGE) continue;
                boat.mobPassengers.add(mob.id);
                boat.passengersDirty = true;
                snapshotDirty = true;
                syncPassenger(boat, mob, boat.mobPassengers.size() - 1);
                if (boat.mobPassengers.size() >= CAPACITY) break;
            }
        }
    }

    private void syncPassengers(Boat boat) {
        for (int i = 0; i < boat.mobPassengers.size(); i++) {
            Mob mob = rt.mobSystem().mobForBoat(boat.mobPassengers.get(i));
            if (mob != null) syncPassenger(boat, mob, i);
        }
    }

    private void syncPassenger(Boat boat, Mob mob, int index) {
        int occupied = boat.occupiedSeats();
        double forward = occupied <= 1 ? 0.0 : (index == 0 ? -0.32 : 0.32);
        double seatX = boat.x + Math.sin(boat.yaw) * forward;
        double seatZ = boat.z + Math.cos(boat.yaw) * forward;
        if (mob.mountedBoatId() == boat.id) mob.syncBoatSeat(seatX, boat.y + MOB_SEAT_Y, seatZ, boat.yaw);
        else mob.mountBoat(boat.id, seatX, boat.y + MOB_SEAT_Y, seatZ, boat.yaw);
        rt.mobSystem().reindexBoatMob(mob);
    }

    private void dismountAllMobs(Boat boat) {
        double sideX = Math.cos(boat.yaw) * 0.9;
        double sideZ = -Math.sin(boat.yaw) * 0.9;
        for (int i = 0; i < boat.mobPassengers.size(); i++) {
            Mob mob = rt.mobSystem().mobForBoat(boat.mobPassengers.get(i));
            if (mob != null) {
                mob.dismountBoat(boat.x + sideX, boat.y + 0.1, boat.z + sideZ);
                rt.mobSystem().reindexBoatMob(mob);
            }
        }
        boat.mobPassengers.clear();
        boat.passengersDirty = true;
        snapshotDirty = true;
    }

    private void broadcastUpdates() {
        List<BoatPosDto> moved = null;
        for (Boat b : boats) {
            if (Math.abs(b.x - b.lastX) > POS_EPS
                    || Math.abs(b.y - b.lastY) > POS_EPS
                    || Math.abs(b.z - b.lastZ) > POS_EPS
                    || Math.abs(b.yaw - b.lastYaw) > POS_EPS
                    || b.passengersDirty) {
                if (moved == null) {
                    moved = new ArrayList<>();
                }
                moved.add(new BoatPosDto(b.id, b.x, b.y, b.z, b.yaw, b.driver,
                        List.copyOf(b.playerPassengerNames), List.copyOf(b.mobPassengers)));
                b.lastX = b.x;
                b.lastY = b.y;
                b.lastZ = b.z;
                b.lastYaw = b.yaw;
                b.passengersDirty = false;
            }
        }
        if (moved != null) {
            broadcast(new BoatUpdate(moved));
        }
    }

    /** 정지한(운전자 없는) 보트 중 저장 좌표와 어긋난 것이 하나라도 있으면 한 번만 새긴다. */
    private void persistSettledPositions() {
        for (Boat boat : boats) {
            if (boat.driver == null && !boat.persistedMatches()) {
                persist();
                return;
            }
        }
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
        List<BoatSnapshot> snapshot = snapshotForPersistence();
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
            if (completion.success() && completion.revision() == persistenceRevision) {
                persistenceDirty = false;
            }
        }
    }

    /** 런타임 폐기 직전 마지막 좌표. 실패하면 재시도할 틱이 없으므로 남은 dirty 여부와 무관하게 쓴다. */
    void flushForDisposal() {
        if (persistence == null) return;
        List<BoatSnapshot> snapshot = snapshotForPersistence();
        if (rt.ctx().persistenceExecutor() == null) {
            persistence.replaceWorld(rt.worldId(), snapshot);
        } else {
            rt.ctx().persistenceExecutor().submitFuture(
                    () -> persistence.replaceWorld(rt.worldId(), snapshot));
        }
    }

    /**
     * 쓰기에 넘길 불변 스냅샷. 넘긴 좌표를 그 자리에서 "저장됨"으로 표시해, 실패해도 dirty 플래그가
     * 남아 다음 틱이 재제출한다(정착 스윕이 같은 좌표를 매번 다시 잡지 않게 한다).
     */
    private List<BoatSnapshot> snapshotForPersistence() {
        List<BoatSnapshot> snapshot = new ArrayList<>(boats.size());
        for (Boat boat : boats) {
            snapshot.add(new BoatSnapshot(boat.id, boat.x, boat.y, boat.z, boat.yaw));
            boat.markPersisted();
        }
        return List.copyOf(snapshot);
    }

    private record PersistenceCompletion(long revision, boolean success) {
    }

    private void publishSnapshot() {
        if (!snapshotDirty) return;
        List<BoatDto> snap = new ArrayList<>(boats.size());
        for (Boat b : boats) {
            snap.add(new BoatDto(b.id, b.x, b.y, b.z, b.yaw, b.driver,
                    List.copyOf(b.playerPassengerNames), List.copyOf(b.mobPassengers)));
        }
        welcomeSnapshot = List.copyOf(snap);
        snapshotDirty = false;
    }

    private Boat find(long boatId) {
        for (Boat b : boats) {
            if (b.id == boatId) {
                return b;
            }
        }
        return null;
    }

    private static boolean finite(double... values) {
        for (double v : values) {
            if (!Double.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    private void sendTo(PlayerTickState player, InventoryUpdate message) {
        var session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
        }
    }

    private void broadcast(Object message) {
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
    }

    // ── 인메모리 보트 엔티티 ────────────────────────────────────────────
    private static final class Boat {
        final long id;
        double x;
        double y;
        double z;
        double yaw;
        String driver; // 현재 운전자 닉네임(없으면 null → 정지)
        final List<String> playerPassengerNames = new ArrayList<>();
        final List<Long> mobPassengers = new ArrayList<>();
        boolean passengersDirty;
        double lastX;
        double lastY;
        double lastZ;
        double lastYaw;
        // 마지막으로 영속 계층에 넘긴 좌표. NaN 은 "아직 넘긴 적 없음"이라 항상 불일치가 된다.
        double persistedX = Double.NaN;
        double persistedY = Double.NaN;
        double persistedZ = Double.NaN;
        double persistedYaw = Double.NaN;

        int occupiedSeats() {
            return (driver == null ? 0 : 1) + playerPassengerNames.size() + mobPassengers.size();
        }

        void markPersisted() {
            persistedX = x;
            persistedY = y;
            persistedZ = z;
            persistedYaw = yaw;
        }

        boolean persistedMatches() {
            return Double.compare(persistedX, x) == 0
                    && Double.compare(persistedY, y) == 0
                    && Double.compare(persistedZ, z) == 0
                    && Double.compare(persistedYaw, yaw) == 0;
        }

        Boat(long id, double x, double y, double z, double yaw) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastYaw = yaw;
        }
    }

    // ── WS 스레드가 넣고 틱 스레드가 소비하는 명령(MPSC) ─────────────────
    private sealed interface Cmd permits Place, Board, Leave, Pos, Break {
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Place implements Cmd {
        private final String nickname;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
        private final PlayerInventory.Hand hand;
        /** 액션 적용 시 잡은 손. null 이면 틱에서 현재 선택 칸을 쓴다(직접 큐 경로). */
        private final PlayerInventory.HandRef capturedHand;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Board implements Cmd {
        private final String nickname;
        private final long boatId;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Leave implements Cmd {
        private final String nickname;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Pos implements Cmd {
        private final String nickname;
        private final long boatId;
        private final double x;
        private final double y;
        private final double z;
        private final double yaw;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Break implements Cmd {
        private final String nickname;
        private final long boatId;
    }
}
