package com.gameexpert.engine.redstone;

import static com.gameexpert.engine.redstone.RedstoneHost.ENTITY_FILTER_EVERYTHING;
import static com.gameexpert.engine.redstone.RedstoneHost.ENTITY_FILTER_LIVING;
import static com.gameexpert.engine.redstone.RedstoneHost.PUSH_BLOCK;
import static com.gameexpert.engine.redstone.RedstoneHost.PUSH_DESTROY;
import static com.gameexpert.engine.redstone.RedstoneHost.PUSH_NORMAL;
import static com.gameexpert.engine.redstone.RedstoneHost.PUSH_ONLY;
import static com.gameexpert.engine.redstone.RedstoneHost.REDSTONE_UNAVAILABLE;
import static com.gameexpert.engine.redstone.RedstoneState.ATTACH_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.BELL_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.BULB_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.COMPARATOR_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.COMPARATOR_SUBTRACT;
import static com.gameexpert.engine.redstone.RedstoneState.CRAFTER_ID;
import static com.gameexpert.engine.redstone.RedstoneState.DAYLIGHT_INVERTED;
import static com.gameexpert.engine.redstone.RedstoneState.DIRECTIONS;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_DOWN;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_DX;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_DY;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_DZ;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_EAST;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_NORTH;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_OPPOSITE;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_SOUTH;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_UP;
import static com.gameexpert.engine.redstone.RedstoneState.DIR_WEST;
import static com.gameexpert.engine.redstone.RedstoneState.DOOR_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.DROPPER_ID;
import static com.gameexpert.engine.redstone.RedstoneState.GATE_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.HOOK_ATTACHED;
import static com.gameexpert.engine.redstone.RedstoneState.HOOK_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.HORIZONTALS;
import static com.gameexpert.engine.redstone.RedstoneState.NOTE_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.OBSERVER_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.PISTON_EXTENDED;
import static com.gameexpert.engine.redstone.RedstoneState.PISTON_HEAD_STICKY;
import static com.gameexpert.engine.redstone.RedstoneState.PLATE_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.RAIL_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.REPEATER_LOCKED;
import static com.gameexpert.engine.redstone.RedstoneState.REPEATER_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.TRAPDOOR_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.TRIPWIRE_ATTACHED;
import static com.gameexpert.engine.redstone.RedstoneState.TRIPWIRE_DISARMED;
import static com.gameexpert.engine.redstone.RedstoneState.TRIPWIRE_POWERED;
import static com.gameexpert.engine.redstone.RedstoneState.UPDATE_ORDER;
import static com.gameexpert.engine.redstone.RedstoneState.UPDATE_SHAPE_ORDER;
import static com.gameexpert.engine.redstone.RedstoneState.VERTICALS;
import static com.gameexpert.engine.redstone.RedstoneState.WIRE_POWER_MASK;
import static com.gameexpert.engine.redstone.RedstoneState.WIRE_SIDES_MASK;
import static com.gameexpert.engine.redstone.RedstoneState.attachConnectedDir;
import static com.gameexpert.engine.redstone.RedstoneState.buttonPressTicks;
import static com.gameexpert.engine.redstone.RedstoneState.comparatorOutput;
import static com.gameexpert.engine.redstone.RedstoneState.dirAxis;
import static com.gameexpert.engine.redstone.RedstoneState.dirClockWise;
import static com.gameexpert.engine.redstone.RedstoneState.dirCounterClockWise;
import static com.gameexpert.engine.redstone.RedstoneState.dirToF6;
import static com.gameexpert.engine.redstone.RedstoneState.dirToH;
import static com.gameexpert.engine.redstone.RedstoneState.f6Facing;
import static com.gameexpert.engine.redstone.RedstoneState.f6ToDir;
import static com.gameexpert.engine.redstone.RedstoneState.hToDir;
import static com.gameexpert.engine.redstone.RedstoneState.isButton;
import static com.gameexpert.engine.redstone.RedstoneState.isPressurePlate;
import static com.gameexpert.engine.redstone.RedstoneState.isRedstoneRail;
import static com.gameexpert.engine.redstone.RedstoneState.noteValue;
import static com.gameexpert.engine.redstone.RedstoneState.plateDetectsEverything;
import static com.gameexpert.engine.redstone.RedstoneState.railIsSlope;
import static com.gameexpert.engine.redstone.RedstoneState.repeaterDelay;
import static com.gameexpert.engine.redstone.RedstoneState.tripwireSideBit;
import static com.gameexpert.engine.redstone.RedstoneState.wireSideBit;
import static com.gameexpert.engine.redstone.RedstoneState.withComparatorOutput;
import static com.gameexpert.terrain.Blocks.ACTIVATOR_RAIL;
import static com.gameexpert.terrain.Blocks.AIR;
import static com.gameexpert.terrain.Blocks.BELL;
import static com.gameexpert.terrain.Blocks.COMPARATOR;
import static com.gameexpert.terrain.Blocks.CRYING_OBSIDIAN;
import static com.gameexpert.terrain.Blocks.DAYLIGHT_DETECTOR;
import static com.gameexpert.terrain.Blocks.DETECTOR_RAIL;
import static com.gameexpert.terrain.Blocks.DISPENSER;
import static com.gameexpert.terrain.Blocks.HONEY_BLOCK;
import static com.gameexpert.terrain.Blocks.HOPPER;
import static com.gameexpert.terrain.Blocks.JUKEBOX;
import static com.gameexpert.terrain.Blocks.LEVER;
import static com.gameexpert.terrain.Blocks.MAX_Y;
import static com.gameexpert.terrain.Blocks.MIN_Y;
import static com.gameexpert.terrain.Blocks.MOVING_PISTON;
import static com.gameexpert.terrain.Blocks.NOTE_BLOCK;
import static com.gameexpert.terrain.Blocks.OBSERVER;
import static com.gameexpert.terrain.Blocks.OBSIDIAN;
import static com.gameexpert.terrain.Blocks.PISTON;
import static com.gameexpert.terrain.Blocks.PISTON_HEAD;
import static com.gameexpert.terrain.Blocks.POWERED_RAIL;
import static com.gameexpert.terrain.Blocks.RAIL;
import static com.gameexpert.terrain.Blocks.REDSTONE_BLOCK;
import static com.gameexpert.terrain.Blocks.REDSTONE_LAMP;
import static com.gameexpert.terrain.Blocks.REDSTONE_LAMP_LIT;
import static com.gameexpert.terrain.Blocks.REDSTONE_TORCH;
import static com.gameexpert.terrain.Blocks.REDSTONE_TORCH_OFF;
import static com.gameexpert.terrain.Blocks.REDSTONE_WALL_TORCH;
import static com.gameexpert.terrain.Blocks.REDSTONE_WALL_TORCH_OFF;
import static com.gameexpert.terrain.Blocks.REDSTONE_WIRE;
import static com.gameexpert.terrain.Blocks.REPEATER;
import static com.gameexpert.terrain.Blocks.SLIME_BLOCK;
import static com.gameexpert.terrain.Blocks.STICKY_PISTON;
import static com.gameexpert.terrain.Blocks.STONE_BUTTON;
import static com.gameexpert.terrain.Blocks.TARGET;
import static com.gameexpert.terrain.Blocks.TNT;
import static com.gameexpert.terrain.Blocks.TRAPPED_CHEST;
import static com.gameexpert.terrain.Blocks.TRIPWIRE;
import static com.gameexpert.terrain.Blocks.TRIPWIRE_HOOK_BLOCK;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gameexpert.terrain.Blocks;

/**
 * [REDSTONE] 바닐라 26.3 레드스톤 신호 엔진 — Spring 권위 사본.
 *
 * <p>{@code client/src/backend/standalone/redstone/RedstoneEngine.ts} 의 줄 단위 사본이다. 핀 26.3 클래스
 * (RedStoneWireBlock + DefaultRedstoneWireEvaluator, RedstoneTorchBlock, DiodeBlock, RepeaterBlock,
 * ComparatorBlock, ObserverBlock, LeverBlock, ButtonBlock, BasePressurePlateBlock, TargetBlock,
 * DaylightDetectorBlock, RedstoneLampBlock, NoteBlock, TripWire(Hook)Block, piston/PistonBaseBlock ·
 * PistonStructureResolver · PistonMovingBlockEntity · PistonHeadBlock, SignalGetter, Level.setBlock ·
 * LevelChunk.setBlockState, CollectingNeighborUpdater, LevelTicks)의 순서와 값을 옮긴 TS 엔진과 같은
 * 순서로 읽고 쓰며, {@code redstone-engine-vectors-v1.json} 벡터가 두 엔진의 결과를 틱 단위로 대조한다.
 *
 * <p>FeatureFlags.DEFAULT_FLAGS 는 VANILLA 뿐이라 {@code redstone_experiments} 가 꺼져 있다. 그래서 기본
 * 가루 평가기(DefaultRedstoneWireEvaluator)와 orientation 없는 이웃 갱신만 옮긴다.
 *
 * <p>월드 쓰기는 오버레이에 쌓이고 읽기는 오버레이를 먼저 본다(바닐라 setBlock 직후 읽기와 같다).
 * 권위는 {@link #drainWrites()} 로 순변경을 받아 자기 쓰기 깔때기로 한 번에 반영한다.
 *
 * <p>TS 의 문자열 좌표 키는 여기서 long 키다. 순서가 결과에 닿는 맵(움직이는 블록·햇빛 감지기·외부 변경·
 * 엔티티 칸)은 TS Map 삽입 순과 같은 {@link LinkedHashMap} 이고, 예약 틱은 TS 처럼 정렬한 뒤에만 돈다.
 */
public final class RedstoneEngine {

    // ── 바닐라 상수 ───────────────────────────────────────────────────────────────────────
    /** {@code Block.UPDATE_*} 플래그. */
    public static final int UPDATE_NEIGHBORS = 1;
    public static final int UPDATE_CLIENTS = 2;
    public static final int UPDATE_INVISIBLE = 4;
    public static final int UPDATE_KNOWN_SHAPE = 16;
    public static final int UPDATE_SUPPRESS_DROPS = 32;
    public static final int UPDATE_MOVE_BY_PISTON = 64;
    public static final int UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE = 128;
    public static final int UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS = 256;
    public static final int UPDATE_SKIP_ON_PLACE = 512;
    public static final int UPDATE_ALL = 3;
    /** {@code TickPriority} 값. */
    public static final int PRIORITY_EXTREMELY_HIGH = -3;
    public static final int PRIORITY_VERY_HIGH = -2;
    public static final int PRIORITY_HIGH = -1;
    public static final int PRIORITY_NORMAL = 0;
    /** {@code MinecraftServer.getMaxChainedNeighborUpdates()} 기본값. */
    public static final int MAX_CHAINED_NEIGHBOR_UPDATES = 1_000_000;
    /** {@code LevelTicks.tick(…, 65536, …)}. */
    public static final int MAX_TICKS_PER_GAME_TICK = 65_536;
    /** {@code PistonStructureResolver.MAX_PUSH_DEPTH}. */
    public static final int MAX_PUSH_DEPTH = 12;
    /** {@code RedstoneTorchBlock.RECENT_TOGGLE_TIMER / MAX_RECENT_TOGGLES / RESTART_DELAY}. */
    private static final int TORCH_RECENT_TOGGLE_TIMER = 60;
    private static final int TORCH_MAX_RECENT_TOGGLES = 8;
    private static final int TORCH_RESTART_DELAY = 160;

    /** 틱 식별에 쓰는 "같은 바닐라 블록" 대표 ID(점등 쌍둥이를 하나로 접는다). */
    public static int redstoneVanillaBlock(int id) {
        switch (id) {
            case REDSTONE_TORCH_OFF: return REDSTONE_TORCH;
            case REDSTONE_WALL_TORCH_OFF: return REDSTONE_WALL_TORCH;
            case REDSTONE_LAMP_LIT: return REDSTONE_LAMP;
            default: return Blocks.isCopperBulbLit(id) ? Blocks.copperBulbUnlit(id) : id;
        }
    }

    /** 좌표 → long 키(x·z 26비트, y 12비트). TS {@code key(x, y, z)} 문자열과 1:1 이다. */
    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private static int keyX(long k) {
        return (int) (k >> 38);
    }

    private static int keyZ(long k) {
        return (int) ((k << 26) >> 38);
    }

    private static int keyY(long k) {
        return (int) ((k << 52) >> 52);
    }

    /** 예약 틱 키(TS {@code key + "#" + block}). */
    private static final class TickKey {
        final long pos;
        final int block;

        TickKey(int x, int y, int z, int block) {
            this.pos = key(x, y, z);
            this.block = block;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TickKey other && other.pos == pos && other.block == block;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(pos) * 31 + block;
        }
    }

    private static final class BlockEvent {
        final int x;
        final int y;
        final int z;
        final int block;
        final int b0;
        final int b1;

        BlockEvent(int x, int y, int z, int block, int b0, int b1) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.b0 = b0;
            this.b1 = b1;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BlockEvent e && e.x == x && e.y == y && e.z == z && e.block == block
                    && e.b0 == b0 && e.b1 == b1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, block, b0, b1);
        }
    }

    /** TS {@code NeighborUpdates} 네 종(0 simple · 1 full · 2 multi · 3 shape). */
    private static final class NeighborUpdate {
        final int kind;
        int x;
        int y;
        int z;
        int block;
        int id;
        int state;
        boolean moved;
        int skip;
        int idx;
        int dir;
        int nx;
        int ny;
        int nz;
        int nid;
        int nstate;
        int flags;
        int limit;

        NeighborUpdate(int kind) {
            this.kind = kind;
        }
    }

    private static final class ExternalChange {
        final int x;
        final int y;
        final int z;
        final int oldId;
        final int oldState;
        boolean placedByPlayer;

        ExternalChange(int x, int y, int z, int oldId, int oldState, boolean placedByPlayer) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldId = oldId;
            this.oldState = oldState;
            this.placedByPlayer = placedByPlayer;
        }
    }

    private static final class TorchToggle {
        final int x;
        final int y;
        final int z;
        final long when;

        TorchToggle(int x, int y, int z, long when) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.when = when;
        }
    }

    /** {@link #takeStats()} 결과. */
    public static final class Stats {
        public int neighborUpdates;
        public int shapeUpdates;
        public int blockWrites;
    }

    private static final int EMPTY = -2;

    private final RedstoneHost host;
    /** 세션 쓰기(블록<<8|state). */
    private final Map<Long, Integer> overlay = new HashMap<>();
    private final List<Long> overlayOrder = new ArrayList<>();
    /** 권위에 넘겼지만 아직 반영 확인을 받지 못한 쓰기. */
    private final Map<Long, Integer> inFlight = new HashMap<>();
    private final Map<Long, Integer> original = new HashMap<>();
    // 틱
    private final Map<TickKey, RedstoneScheduledTick> ticks = new HashMap<>();
    private final List<RedstoneScheduledTick> running = new ArrayList<>();
    private final Set<TickKey> runningSet = new HashSet<>();
    private long subTickCounter = 0;
    private long gameTime = 0;
    private boolean ticksDirty = false;
    // 블록 이벤트
    private final ArrayDeque<BlockEvent> blockEvents = new ArrayDeque<>();
    private final Set<BlockEvent> blockEventKeys = new HashSet<>();
    // 블록 엔티티
    private final Map<Long, RedstoneMovingBlock> moving = new LinkedHashMap<>();
    private final Map<Long, int[]> daylight = new LinkedHashMap<>();
    // 이웃 갱신기
    private final List<NeighborUpdate> stack = new ArrayList<>();
    private final List<NeighborUpdate> addedThisLayer = new ArrayList<>();
    private int updateCount = 0;
    // 가루 평가 상태
    private boolean shouldSignal = true;
    // 횃불 과열
    private final ArrayDeque<TorchToggle> torchToggles = new ArrayDeque<>();
    // 외부 변경
    private final Map<Long, ExternalChange> external = new LinkedHashMap<>();
    // 엔티티 칸
    private final Map<Long, int[]> entityCells = new LinkedHashMap<>();
    private Stats stats = new Stats();
    /** ServerLevel.handlingTick: 블록 틱·블록 이벤트를 도는 동안 참(엔티티·블록 엔티티 틱은 거짓). */
    private boolean handlingTick = false;

    public RedstoneEngine(RedstoneHost host) {
        this.host = host;
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 월드 읽기·쓰기
    // ════════════════════════════════════════════════════════════════════════════════════

    private int packed(int x, int y, int z) {
        if (y < MIN_Y || y > MAX_Y) return 0;
        long k = key(x, y, z);
        Integer o = overlay.get(k);
        if (o != null) return o;
        Integer f = inFlight.get(k);
        if (f != null) return f;
        int id = host.block(x, y, z);
        if (id == REDSTONE_UNAVAILABLE) return EMPTY;
        return id << 8 | (id == AIR ? 0 : host.state(x, y, z, id) & 0xff);
    }

    /** 셀 블록 ID(비상주는 공기로 읽는다). */
    public int blockAt(int x, int y, int z) {
        int p = packed(x, y, z);
        return p == EMPTY ? AIR : p >> 8;
    }

    public int stateAt(int x, int y, int z) {
        int p = packed(x, y, z);
        return p == EMPTY ? 0 : p & 0xff;
    }

    private boolean resident(int x, int y, int z) {
        return y >= MIN_Y && y <= MAX_Y && packed(x, y, z) != EMPTY;
    }

    private void writeRaw(int x, int y, int z, int id, int state) {
        long k = key(x, y, z);
        if (!original.containsKey(k)) {
            int before = packed(x, y, z);
            original.put(k, before);
            overlayOrder.add(k);
        }
        overlay.put(k, id << 8 | (id == AIR ? 0 : state & 0xff));
        stats.blockWrites++;
    }

    /**
     * 세션 순변경을 권위에 넘긴다. 권위는 반영을 마친 뒤 {@link #commitApplied()} 를 불러야 한다 —
     * 그 사이 엔진 읽기는 넘긴 값을 계속 본다.
     */
    public List<RedstoneCellWrite> drainWrites() {
        List<RedstoneCellWrite> out = new ArrayList<>();
        for (long k : overlayOrder) {
            int value = overlay.get(k);
            int before = original.get(k);
            if (value == before) continue;
            out.add(new RedstoneCellWrite(keyX(k), keyY(k), keyZ(k), value >> 8, value & 0xff));
            inFlight.put(k, value);
        }
        overlay.clear();
        overlayOrder.clear();
        original.clear();
        return out;
    }

    /** 넘긴 쓰기가 권위 월드에 반영됐다. */
    public void commitApplied() {
        inFlight.clear();
    }

    /** 반영 실패로 넘긴 쓰기를 버린다(권위 월드가 정본이다). */
    public void commitRejected() {
        inFlight.clear();
    }

    public boolean hasPendingWrites() {
        return !overlayOrder.isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // Level.setBlock / removeBlock / destroyBlock
    // ════════════════════════════════════════════════════════════════════════════════════

    public boolean setBlock(int x, int y, int z, int id, int state, int flags) {
        return setBlock(x, y, z, id, state, flags, 512);
    }

    public boolean setBlock(int x, int y, int z, int id, int state, int flags, int updateLimit) {
        if (y < MIN_Y || y > MAX_Y || !resident(x, y, z)) return false;
        int oldId = blockAt(x, y, z);
        int oldState = stateAt(x, y, z);
        int newState = id == AIR ? 0 : state & 0xff;
        if (oldId == id && oldState == newState) return false;
        writeRaw(x, y, z, id, newState);
        chunkSideEffects(x, y, z, oldId, oldState, id, newState, flags);
        if (blockAt(x, y, z) != id || stateAt(x, y, z) != newState) return true;
        levelSideEffects(x, y, z, oldId, oldState, id, newState, flags, updateLimit);
        return true;
    }

    /** {@code LevelChunk.setBlockState} 의 블록 엔티티·제거·설치 부작용. */
    private void chunkSideEffects(int x, int y, int z, int oldId, int oldState, int id, int state, int flags) {
        boolean blockChanged = redstoneVanillaBlock(oldId) != redstoneVanillaBlock(id);
        boolean movedByPiston = (flags & UPDATE_MOVE_BY_PISTON) != 0;
        if (blockChanged) {
            long k = key(x, y, z);
            if (oldId == MOVING_PISTON) {
                RedstoneMovingBlock entity = moving.get(k);
                if (entity != null && (flags & UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS) == 0) finalTick(entity);
                moving.remove(k);
            }
            if (oldId == DAYLIGHT_DETECTOR) daylight.remove(k);
        }
        if ((blockChanged || isRail(id)) && ((flags & UPDATE_NEIGHBORS) != 0 || movedByPiston)) {
            affectNeighborsAfterRemoval(oldId, oldState, x, y, z, movedByPiston);
        }
        if (blockAt(x, y, z) != id) return;
        if ((flags & UPDATE_SKIP_ON_PLACE) == 0) onPlace(id, state, x, y, z, oldId, oldState, movedByPiston);
        if (id == DAYLIGHT_DETECTOR) daylight.put(key(x, y, z), new int[] {x, y, z});
    }

    /** {@code newBlock instanceof BaseRailBlock}. */
    private boolean isRail(int id) {
        return id == RAIL || isRedstoneRail(id);
    }

    /** {@code Level.setBlock} 의 이웃 갱신·형상 갱신. */
    private void levelSideEffects(int x, int y, int z, int oldId, int oldState, int id, int state, int flags,
            int updateLimit) {
        if ((flags & UPDATE_NEIGHBORS) != 0) {
            updateNeighborsAt(x, y, z, redstoneVanillaBlock(oldId));
            if (host.hasAnalogOutput(id, state)) updateNeighbourForOutputSignal(x, y, z, id);
        }
        if ((flags & UPDATE_KNOWN_SHAPE) == 0 && updateLimit > 0) {
            int f = flags & ~(UPDATE_NEIGHBORS | UPDATE_SUPPRESS_DROPS);
            updateIndirectNeighbourShapes(oldId, oldState, x, y, z, f, updateLimit - 1);
            updateNeighbourShapes(id, state, x, y, z, f, updateLimit - 1);
            updateIndirectNeighbourShapes(id, state, x, y, z, f, updateLimit - 1);
        }
    }

    public boolean removeBlock(int x, int y, int z, boolean movedByPiston) {
        int id = blockAt(x, y, z);
        int replacement = host.fluidAfterRemoval(x, y, z, id, stateAt(x, y, z));
        return setBlock(x, y, z, replacement, 0, UPDATE_ALL | (movedByPiston ? UPDATE_MOVE_BY_PISTON : 0));
    }

    public boolean destroyBlock(int x, int y, int z, boolean dropResources) {
        return destroyBlock(x, y, z, dropResources, 512);
    }

    public boolean destroyBlock(int x, int y, int z, boolean dropResources, int updateLimit) {
        int id = blockAt(x, y, z);
        if (id == AIR) return false;
        int state = stateAt(x, y, z);
        if (dropResources) host.dropResources(x, y, z, id, state);
        int replacement = host.fluidAfterRemoval(x, y, z, id, state);
        return setBlock(x, y, z, replacement, 0, UPDATE_ALL, updateLimit);
    }

    private boolean setBlockAndUpdate(int x, int y, int z, int id, int state) {
        return setBlock(x, y, z, id, state, UPDATE_ALL);
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 이웃 갱신(CollectingNeighborUpdater)
    // ════════════════════════════════════════════════════════════════════════════════════

    public void updateNeighborsAt(int x, int y, int z, int block) {
        NeighborUpdate u = new NeighborUpdate(2);
        u.x = x;
        u.y = y;
        u.z = z;
        u.block = block;
        u.skip = -1;
        u.idx = 0;
        addAndRun(u);
    }

    public void updateNeighborsAtExceptFromFacing(int x, int y, int z, int block, int skip) {
        NeighborUpdate u = new NeighborUpdate(2);
        u.x = x;
        u.y = y;
        u.z = z;
        u.block = block;
        u.skip = skip;
        u.idx = 0;
        if (UPDATE_ORDER[0] == skip) u.idx++;
        addAndRun(u);
    }

    public void neighborChanged(int x, int y, int z, int block) {
        NeighborUpdate u = new NeighborUpdate(0);
        u.x = x;
        u.y = y;
        u.z = z;
        u.block = block;
        addAndRun(u);
    }

    public void neighborChangedFull(int id, int state, int x, int y, int z, int block, boolean moved) {
        NeighborUpdate u = new NeighborUpdate(1);
        u.x = x;
        u.y = y;
        u.z = z;
        u.id = id;
        u.state = state;
        u.block = block;
        u.moved = moved;
        addAndRun(u);
    }

    private void neighborShapeChanged(int dir, int x, int y, int z, int nx, int ny, int nz, int nid, int nstate,
            int flags, int limit) {
        NeighborUpdate u = new NeighborUpdate(3);
        u.dir = dir;
        u.x = x;
        u.y = y;
        u.z = z;
        u.nx = nx;
        u.ny = ny;
        u.nz = nz;
        u.nid = nid;
        u.nstate = nstate;
        u.flags = flags;
        u.limit = limit;
        addAndRun(u);
    }

    private void addAndRun(NeighborUpdate update) {
        boolean runningAlready = updateCount > 0;
        boolean tooMany = updateCount >= MAX_CHAINED_NEIGHBOR_UPDATES;
        updateCount++;
        if (!tooMany) {
            if (runningAlready) addedThisLayer.add(update);
            else stack.add(update);
        }
        if (!runningAlready) runUpdates();
    }

    private void runUpdates() {
        try {
            while (!stack.isEmpty() || !addedThisLayer.isEmpty()) {
                for (int i = addedThisLayer.size() - 1; i >= 0; i--) stack.add(addedThisLayer.get(i));
                addedThisLayer.clear();
                NeighborUpdate next = stack.get(stack.size() - 1);
                while (addedThisLayer.isEmpty()) {
                    if (!runNext(next)) {
                        // 맨 위(가장 최근 push)를 꺼낸다. 바닐라 ArrayDeque.push/peek/pop 과 같은 LIFO 이며,
                        // runNext 가 새 갱신을 더하지 않은 동안 맨 위는 여전히 next 다.
                        stack.remove(stack.size() - 1);
                        break;
                    }
                }
            }
        } finally {
            stack.clear();
            addedThisLayer.clear();
            updateCount = 0;
        }
    }

    /** {@code NeighborUpdates.runNext}. 계속할 갱신이 남으면 true. */
    private boolean runNext(NeighborUpdate u) {
        switch (u.kind) {
            case 0: {
                stats.neighborUpdates++;
                executeUpdate(blockAt(u.x, u.y, u.z), stateAt(u.x, u.y, u.z), u.x, u.y, u.z, u.block, false);
                return false;
            }
            case 1: {
                stats.neighborUpdates++;
                executeUpdate(u.id, u.state, u.x, u.y, u.z, u.block, u.moved);
                return false;
            }
            case 2: {
                int dir = UPDATE_ORDER[u.idx++];
                int nx = u.x + DIR_DX[dir];
                int ny = u.y + DIR_DY[dir];
                int nz = u.z + DIR_DZ[dir];
                stats.neighborUpdates++;
                executeUpdate(blockAt(nx, ny, nz), stateAt(nx, ny, nz), nx, ny, nz, u.block, false);
                if (u.idx < UPDATE_ORDER.length && UPDATE_ORDER[u.idx] == u.skip) u.idx++;
                return u.idx < UPDATE_ORDER.length;
            }
            default: {
                stats.shapeUpdates++;
                executeShapeUpdate(u.dir, u.x, u.y, u.z, u.nx, u.ny, u.nz, u.nid, u.nstate, u.flags, u.limit);
                return false;
            }
        }
    }

    private void executeShapeUpdate(int dir, int x, int y, int z, int nx, int ny, int nz, int nid, int nstate,
            int flags, int limit) {
        int id = blockAt(x, y, z);
        if ((flags & UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE) != 0 && id == REDSTONE_WIRE) return;
        int state = stateAt(x, y, z);
        int next = updateShape(id, state, x, y, z, dir, nx, ny, nz, nid, nstate);
        updateOrDestroy(id, state, next >> 8, next & 0xff, x, y, z, flags, limit);
    }

    /** {@code Block.updateOrDestroy}. */
    private void updateOrDestroy(int oldId, int oldState, int id, int state, int x, int y, int z, int flags,
            int limit) {
        if (id == oldId && state == oldState) return;
        if (id == AIR) destroyBlock(x, y, z, (flags & UPDATE_SUPPRESS_DROPS) == 0, limit);
        else setBlock(x, y, z, id, state, flags & ~UPDATE_SUPPRESS_DROPS, limit);
    }

    /** {@code BlockStateBase.updateNeighbourShapes}. */
    private void updateNeighbourShapes(int id, int state, int x, int y, int z, int flags, int limit) {
        for (int dir : UPDATE_SHAPE_ORDER) {
            neighborShapeChanged(DIR_OPPOSITE[dir], x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir],
                    x, y, z, id, state, flags, limit);
        }
    }

    /** {@code Level.updateNeighbourForOutputSignal}. */
    public void updateNeighbourForOutputSignal(int x, int y, int z, int block) {
        for (int dir : HORIZONTALS) {
            int rx = x + DIR_DX[dir];
            int rz = z + DIR_DZ[dir];
            if (!resident(rx, y, rz)) continue;
            int id = blockAt(rx, y, rz);
            if (id == COMPARATOR) {
                neighborChangedFull(id, stateAt(rx, y, rz), rx, y, rz, block, false);
            } else if (isConductorAt(rx, y, rz)) {
                rx += DIR_DX[dir];
                rz += DIR_DZ[dir];
                id = blockAt(rx, y, rz);
                if (id == COMPARATOR) neighborChangedFull(id, stateAt(rx, y, rz), rx, y, rz, block, false);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 예약 틱(LevelTicks)
    // ════════════════════════════════════════════════════════════════════════════════════

    public void scheduleTick(int x, int y, int z, int block, int delay) {
        scheduleTick(x, y, z, block, delay, PRIORITY_NORMAL);
    }

    public void scheduleTick(int x, int y, int z, int block, int delay, int priority) {
        int vb = redstoneVanillaBlock(block);
        long sub = subTickCounter++;
        TickKey k = new TickKey(x, y, z, vb);
        if (ticks.containsKey(k)) return;
        ticks.put(k, new RedstoneScheduledTick(x, y, z, vb, gameTime + delay, priority, sub));
        ticksDirty = true;
    }

    public boolean hasScheduledTick(int x, int y, int z, int block) {
        return ticks.containsKey(new TickKey(x, y, z, redstoneVanillaBlock(block)));
    }

    public boolean willTickThisTick(int x, int y, int z, int block) {
        return runningSet.contains(new TickKey(x, y, z, redstoneVanillaBlock(block)));
    }

    private static final Comparator<RedstoneScheduledTick> TICK_ORDER = (a, b) -> {
        int c = Long.compare(a.triggerTick, b.triggerTick);
        if (c != 0) return c;
        c = Integer.compare(a.priority, b.priority);
        if (c != 0) return c;
        return Long.compare(a.subTickOrder, b.subTickOrder);
    };

    private void collectTicks(long now) {
        List<RedstoneScheduledTick> due = new ArrayList<>();
        for (RedstoneScheduledTick tick : ticks.values()) {
            if (tick.triggerTick <= now && resident(tick.x, tick.y, tick.z)) due.add(tick);
        }
        if (due.isEmpty()) return;
        due.sort(TICK_ORDER);
        int count = Math.min(due.size(), MAX_TICKS_PER_GAME_TICK);
        for (int i = 0; i < count; i++) {
            RedstoneScheduledTick tick = due.get(i);
            TickKey k = new TickKey(tick.x, tick.y, tick.z, tick.block);
            ticks.remove(k);
            running.add(tick);
            runningSet.add(k);
        }
        ticksDirty = true;
    }

    private void runCollectedTicks() {
        for (int i = 0; i < running.size(); i++) {
            RedstoneScheduledTick tick = running.get(i);
            runningSet.remove(new TickKey(tick.x, tick.y, tick.z, tick.block));
            int id = blockAt(tick.x, tick.y, tick.z);
            if (redstoneVanillaBlock(id) == tick.block) {
                tick(id, stateAt(tick.x, tick.y, tick.z), tick.x, tick.y, tick.z);
            }
        }
        running.clear();
        runningSet.clear();
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 블록 이벤트(피스톤·소리 블록)
    // ════════════════════════════════════════════════════════════════════════════════════

    private void blockEvent(int x, int y, int z, int block, int b0, int b1) {
        BlockEvent event = new BlockEvent(x, y, z, block, b0, b1);
        if (blockEventKeys.contains(event)) return;
        blockEventKeys.add(event);
        blockEvents.add(event);
    }

    private void runBlockEvents() {
        List<BlockEvent> reschedule = new ArrayList<>();
        while (!blockEvents.isEmpty()) {
            BlockEvent event = blockEvents.removeFirst();
            blockEventKeys.remove(event);
            if (!resident(event.x, event.y, event.z)) {
                reschedule.add(event);
                continue;
            }
            int id = blockAt(event.x, event.y, event.z);
            if (redstoneVanillaBlock(id) == event.block) {
                triggerEvent(id, stateAt(event.x, event.y, event.z), event.x, event.y, event.z, event.b0, event.b1);
            }
        }
        for (BlockEvent event : reschedule) blockEvent(event.x, event.y, event.z, event.block, event.b0, event.b1);
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 권위 진입점
    // ════════════════════════════════════════════════════════════════════════════════════

    public long currentGameTime() {
        return gameTime;
    }

    public void noteExternalChange(int x, int y, int z, int oldId, int oldState) {
        noteExternalChange(x, y, z, oldId, oldState, false);
    }

    /** 비엔진 쓰기(플레이어 편집·유체·폭발 …)의 이전 값을 기록한다. 같은 칸은 첫 이전 값을 유지한다. */
    public void noteExternalChange(int x, int y, int z, int oldId, int oldState, boolean placedByPlayer) {
        long k = key(x, y, z);
        ExternalChange existing = external.get(k);
        if (existing != null) {
            if (placedByPlayer) existing.placedByPlayer = true;
            return;
        }
        external.put(k, new ExternalChange(x, y, z, oldId, oldId == AIR ? 0 : oldState & 0xff, placedByPlayer));
    }

    /** 대기 중인 외부 변경을 바닐라 setBlock(UPDATE_ALL) 부작용으로 처리한다. */
    public void processExternalChanges(long gameTime) {
        if (external.isEmpty()) return;
        this.gameTime = gameTime;
        List<ExternalChange> changes = new ArrayList<>(external.values());
        external.clear();
        for (ExternalChange change : changes) {
            int x = change.x;
            int y = change.y;
            int z = change.z;
            int oldId = change.oldId;
            int oldState = change.oldState;
            if (!resident(x, y, z)) continue;
            int id = blockAt(x, y, z);
            int state = stateAt(x, y, z);
            if (id == oldId && state == oldState) continue;
            if (!externalChangeRelevant(x, y, z, oldId, id, state)) continue;
            chunkSideEffects(x, y, z, oldId, oldState, id, state, UPDATE_ALL);
            if (blockAt(x, y, z) == id && stateAt(x, y, z) == state) {
                levelSideEffects(x, y, z, oldId, oldState, id, state, UPDATE_ALL, 512);
            }
            if (change.placedByPlayer && blockAt(x, y, z) == id) setPlacedBy(id, stateAt(x, y, z), x, y, z);
        }
    }

    /**
     * 외부 변경이 레드스톤 행동을 건드릴 수 있는가. 바닐라는 모든 setBlock 이 이웃 갱신을 보내지만
     * 레드스톤이 아닌 블록의 neighborChanged/updateShape 는 이 엔진의 몫이 아니다(권위의 기존 지지·연결
     * 규칙이 맡는다). 그래서 변경 칸·옛 블록·새 블록·이웃 여섯 칸·비교기 두 칸 너머 중 하나라도 이
     * 엔진이 행동을 소유하는 블록일 때만 부작용을 돌린다 — 결과는 전부 돌린 것과 같다.
     */
    private boolean externalChangeRelevant(int x, int y, int z, int oldId, int id, int state) {
        if (isEngineBlock(oldId) || isEngineBlock(id)) return true;
        for (int dir : DIRECTIONS) {
            int nx = x + DIR_DX[dir];
            int ny = y + DIR_DY[dir];
            int nz = z + DIR_DZ[dir];
            if (isEngineBlock(blockAt(nx, ny, nz))) return true;
        }
        if (host.hasAnalogOutput(id, state) || host.hasAnalogOutput(oldId, 0)) {
            for (int dir : HORIZONTALS) {
                if (blockAt(x + 2 * DIR_DX[dir], y, z + 2 * DIR_DZ[dir]) == COMPARATOR) return true;
            }
        }
        return false;
    }

    /** 이 엔진이 neighborChanged/updateShape/onPlace/제거 행동을 소유하는 블록. */
    public boolean isEngineBlock(int id) {
        switch (id) {
            case REDSTONE_WIRE: case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH:
            case REDSTONE_WALL_TORCH_OFF: case REDSTONE_BLOCK: case LEVER: case REPEATER: case COMPARATOR:
            case OBSERVER: case PISTON: case STICKY_PISTON: case PISTON_HEAD: case MOVING_PISTON:
            case REDSTONE_LAMP: case REDSTONE_LAMP_LIT: case TARGET: case DAYLIGHT_DETECTOR: case NOTE_BLOCK:
            case TRIPWIRE: case TRIPWIRE_HOOK_BLOCK: case DISPENSER: case DROPPER_ID: case CRAFTER_ID: case TNT: case BELL:
            case HOPPER: case TRAPPED_CHEST: case JUKEBOX: case RAIL: case POWERED_RAIL: case ACTIVATOR_RAIL: case DETECTOR_RAIL:
                return true;
            default:
                return isButton(id) || isPressurePlate(id) || Blocks.isDoor(id) || Blocks.isTrapdoor(id)
                        || Blocks.isFenceGate(id) || Blocks.isCopperBulb(id) || Blocks.isShelf(id);
        }
    }

    /** 컨테이너 내용이 바뀌었다({@code BlockEntity.setChanged} → {@code updateNeighbourForOutputSignal}). */
    public void containerChanged(int x, int y, int z) {
        int id = blockAt(x, y, z);
        if (host.hasAnalogOutput(id, stateAt(x, y, z))) updateNeighbourForOutputSignal(x, y, z, id);
    }

    /** 덫 상자 열람자 수가 바뀌었다({@code TrappedChestBlockEntity.signalOpenCount}). */
    public void chestViewersChanged(int x, int y, int z) {
        int id = blockAt(x, y, z);
        if (id != TRAPPED_CHEST) return;
        updateNeighborsAt(x, y, z, id);
        updateNeighborsAt(x, y - 1, z, id);
    }

    /** 엔티티 AABB 가 겹친 칸(감압판·철사 덫 entityInside 후보). 다음 게임 틱에 처리한다. */
    public void noteEntityCell(int x, int y, int z) {
        int id = blockAt(x, y, z);
        if (isPressurePlate(id) || id == TRIPWIRE || isButton(id) || id == DETECTOR_RAIL) {
            entityCells.put(key(x, y, z), new int[] {x, y, z});
        }
    }

    /**
     * 한 게임 틱(바닐라 ServerLevel.tick 의 blockTicks → blockEvents → entities → blockEntities).
     * 권위는 틱마다 두 번(G-1, G) 부른다.
     */
    public void tickGame(long gameTime, boolean lastSubTick) {
        this.gameTime = gameTime;
        handlingTick = true;
        try {
            collectTicks(gameTime);
            runCollectedTicks();
            runBlockEvents();
        } finally {
            handlingTick = false;
        }
        if (lastSubTick) runEntityInside();
        tickBlockEntities();
    }

    private void runEntityInside() {
        if (entityCells.isEmpty()) return;
        List<int[]> cells = new ArrayList<>(entityCells.values());
        entityCells.clear();
        for (int[] cell : cells) {
            int x = cell[0];
            int y = cell[1];
            int z = cell[2];
            int id = blockAt(x, y, z);
            int state = stateAt(x, y, z);
            if (isPressurePlate(id)) {
                if ((state & PLATE_POWERED) == 0) plateCheckPressed(id, x, y, z, state, 0);
            } else if (id == TRIPWIRE) {
                if ((state & TRIPWIRE_POWERED) == 0 && !hasScheduledTick(x, y, z, TRIPWIRE)) {
                    tripwireCheckPressed(x, y, z);
                }
            } else if (isButton(id)) {
                if ((state & ATTACH_POWERED) == 0 && id != STONE_BUTTON) buttonCheckPressed(id, state, x, y, z);
            } else if (id == DETECTOR_RAIL) {
                if ((state & RAIL_POWERED) == 0) detectorCheckPressed(state, x, y, z);
            }
        }
    }

    private void tickBlockEntities() {
        if (!moving.isEmpty()) {
            for (RedstoneMovingBlock entity : new ArrayList<>(moving.values())) {
                if (moving.get(key(entity.x, entity.y, entity.z)) != entity) continue;
                tickMovingBlock(entity);
            }
        }
        if (!daylight.isEmpty() && gameTime % 20 == 0 && host.hasSkyLight()) {
            for (Map.Entry<Long, int[]> entry : new ArrayList<>(daylight.entrySet())) {
                int x = entry.getValue()[0];
                int y = entry.getValue()[1];
                int z = entry.getValue()[2];
                int id = blockAt(x, y, z);
                if (id != DAYLIGHT_DETECTOR) {
                    if (resident(x, y, z)) daylight.remove(entry.getKey());
                    continue;
                }
                updateDaylightSignal(stateAt(x, y, z), x, y, z);
            }
        }
    }

    public boolean use(int x, int y, int z, long gameTime) {
        return use(x, y, z, gameTime, true);
    }

    /** 우클릭({@code useWithoutItem}). 엔진이 소비했으면 true. */
    public boolean use(int x, int y, int z, long gameTime, boolean mayBuild) {
        this.gameTime = gameTime;
        int id = blockAt(x, y, z);
        int state = stateAt(x, y, z);
        if (id == LEVER) {
            leverPull(state, x, y, z);
            return true;
        }
        if (isButton(id)) {
            if ((state & ATTACH_POWERED) != 0) return true;
            buttonPress(id, state, x, y, z);
            return true;
        }
        if (id == REPEATER) {
            if (!mayBuild) return false;
            int delay = repeaterDelay(state);
            setBlockAndUpdate(x, y, z, id, (state & ~0x0c) | ((delay % 4) << 2));
            return true;
        }
        if (id == COMPARATOR) {
            if (!mayBuild) return false;
            int next = state ^ COMPARATOR_SUBTRACT;
            host.sound("comparator_click", x, y, z, (next & COMPARATOR_SUBTRACT) != 0 ? 0.55 : 0.5);
            setBlock(x, y, z, id, next, UPDATE_CLIENTS);
            if (blockAt(x, y, z) == COMPARATOR) comparatorRefreshOutput(stateAt(x, y, z), x, y, z);
            return true;
        }
        if (id == DAYLIGHT_DETECTOR) {
            if (!mayBuild) return false;
            int next = state ^ DAYLIGHT_INVERTED;
            setBlock(x, y, z, id, next, UPDATE_CLIENTS);
            updateDaylightSignal(next, x, y, z);
            return true;
        }
        if (id == REDSTONE_WIRE) {
            if (!mayBuild) return false;
            return wireUse(state, x, y, z);
        }
        if (id == NOTE_BLOCK) {
            int note = (noteValue(state) + 1) % 25;
            int next = (state & ~0x1f) | note;
            setBlockAndUpdate(x, y, z, id, next);
            playNote(next, x, y, z);
            return true;
        }
        return false;
    }

    /** 좌클릭 시작({@code attack}). 소리 블록만 연주한다. */
    public void attack(int x, int y, int z, long gameTime) {
        this.gameTime = gameTime;
        if (blockAt(x, y, z) == NOTE_BLOCK) playNote(stateAt(x, y, z), x, y, z);
    }

    /** 플레이어가 부수기 직전({@code playerWillDestroy}). 가위로 끊은 철사 덫은 DISARMED 로 먼저 바뀐다. */
    public void playerWillDestroy(int x, int y, int z, boolean holdingShears, long gameTime) {
        this.gameTime = gameTime;
        int id = blockAt(x, y, z);
        if (id == TRIPWIRE && holdingShears) {
            setBlock(x, y, z, id, stateAt(x, y, z) | TRIPWIRE_DISARMED, UPDATE_INVISIBLE | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /** 투사체가 과녁에 맞았다({@code TargetBlock.onProjectileHit}). */
    public int targetHit(int x, int y, int z, int face, double hitX, double hitY, double hitZ, boolean arrow,
            long gameTime) {
        this.gameTime = gameTime;
        if (blockAt(x, y, z) != TARGET) return 0;
        double dx = Math.abs(frac(hitX) - 0.5);
        double dy = Math.abs(frac(hitY) - 0.5);
        double dz = Math.abs(frac(hitZ) - 0.5);
        int axis = dirAxis(face);
        double distance = axis == 1 ? Math.max(dx, dz) : axis == 2 ? Math.max(dx, dy) : Math.max(dy, dz);
        int strength = (int) Math.max(1, Math.ceil(15 * Math.min(1, Math.max(0, (0.5 - distance) / 0.5))));
        if (!hasScheduledTick(x, y, z, TARGET)) {
            setBlockAndUpdate(x, y, z, TARGET, strength);
            scheduleTick(x, y, z, TARGET, arrow ? 20 : 8);
        }
        return strength;
    }

    /**
     * 권위가 상태를 소유하는 신호원(주크박스 재생 시작·끝)이 바뀌었다. 바닐라 JukeboxBlockEntity 는
     * {@code level.updateNeighborsAt(pos, block)} 을 부른다.
     */
    public void sourceChanged(int x, int y, int z) {
        updateNeighborsAt(x, y, z, redstoneVanillaBlock(blockAt(x, y, z)));
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 신호 조회(SignalGetter)
    // ════════════════════════════════════════════════════════════════════════════════════

    public boolean isConductorAt(int x, int y, int z) {
        int id = blockAt(x, y, z);
        return id != AIR && host.isConductor(id, stateAt(x, y, z));
    }

    public int getDirectSignal(int x, int y, int z, int dir) {
        int id = blockAt(x, y, z);
        return id == AIR ? 0 : blockDirectSignal(id, stateAt(x, y, z), x, y, z, dir);
    }

    public int getDirectSignalTo(int x, int y, int z) {
        int result = 0;
        result = Math.max(result, getDirectSignal(x, y - 1, z, DIR_DOWN));
        if (result >= 15) return result;
        result = Math.max(result, getDirectSignal(x, y + 1, z, DIR_UP));
        if (result >= 15) return result;
        result = Math.max(result, getDirectSignal(x, y, z - 1, DIR_NORTH));
        if (result >= 15) return result;
        result = Math.max(result, getDirectSignal(x, y, z + 1, DIR_SOUTH));
        if (result >= 15) return result;
        result = Math.max(result, getDirectSignal(x - 1, y, z, DIR_WEST));
        if (result >= 15) return result;
        result = Math.max(result, getDirectSignal(x + 1, y, z, DIR_EAST));
        return result;
    }

    public int getControlInputSignal(int x, int y, int z, int dir, boolean onlyDiodes) {
        int id = blockAt(x, y, z);
        if (onlyDiodes) return id == REPEATER || id == COMPARATOR ? getDirectSignal(x, y, z, dir) : 0;
        if (id == REDSTONE_BLOCK) return 15;
        if (id == REDSTONE_WIRE) return stateAt(x, y, z) & WIRE_POWER_MASK;
        return isSignalSource(id, stateAt(x, y, z)) ? getDirectSignal(x, y, z, dir) : 0;
    }

    public boolean hasSignal(int x, int y, int z, int dir) {
        return getSignal(x, y, z, dir) > 0;
    }

    public int getSignal(int x, int y, int z, int dir) {
        int id = blockAt(x, y, z);
        if (id == AIR) return 0;
        int state = stateAt(x, y, z);
        int signal = blockSignal(id, state, x, y, z, dir);
        return host.isConductor(id, state) ? Math.max(signal, getDirectSignalTo(x, y, z)) : signal;
    }

    public boolean hasNeighborSignal(int x, int y, int z) {
        if (getSignal(x, y - 1, z, DIR_DOWN) > 0) return true;
        if (getSignal(x, y + 1, z, DIR_UP) > 0) return true;
        if (getSignal(x, y, z - 1, DIR_NORTH) > 0) return true;
        if (getSignal(x, y, z + 1, DIR_SOUTH) > 0) return true;
        if (getSignal(x - 1, y, z, DIR_WEST) > 0) return true;
        return getSignal(x + 1, y, z, DIR_EAST) > 0;
    }

    public int getBestNeighborSignal(int x, int y, int z) {
        int best = 0;
        for (int dir : DIRECTIONS) {
            int signal = getSignal(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], dir);
            if (signal >= 15) return 15;
            if (signal > best) best = signal;
        }
        return best;
    }

    public boolean isSignalSource(int id, int state) {
        switch (id) {
            case REDSTONE_WIRE: return shouldSignal;
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
            case REDSTONE_BLOCK: case LEVER: case REPEATER: case COMPARATOR: case OBSERVER: case TARGET:
            case DAYLIGHT_DETECTOR: case TRIPWIRE_HOOK_BLOCK: case TRAPPED_CHEST: case JUKEBOX: case DETECTOR_RAIL:
                return true;
            default:
                return isButton(id) || isPressurePlate(id);
        }
    }

    private int ownSignal(int id, int state, int x, int y, int z) {
        switch (id) {
            case REDSTONE_WIRE: return state & WIRE_POWER_MASK;
            case REDSTONE_TORCH: case REDSTONE_WALL_TORCH: return 15;
            case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH_OFF: return 0;
            case REDSTONE_BLOCK: return 15;
            case LEVER: return (state & ATTACH_POWERED) != 0 ? 15 : 0;
            case REPEATER: return (state & REPEATER_POWERED) != 0 ? 15 : 0;
            case COMPARATOR: return (state & COMPARATOR_POWERED) != 0 ? comparatorOutput(state) : 0;
            case OBSERVER: return (state & OBSERVER_POWERED) != 0 ? 15 : 0;
            case TARGET: return state & 15;
            case DAYLIGHT_DETECTOR: return state & 15;
            case TRIPWIRE_HOOK_BLOCK: return (state & HOOK_POWERED) != 0 ? 15 : 0;
            case TRAPPED_CHEST: return Math.max(0, Math.min(15, host.chestViewers(x, y, z)));
            case JUKEBOX: return host.jukeboxPlaying(x, y, z) ? 15 : 0;
            case DETECTOR_RAIL: return (state & RAIL_POWERED) != 0 ? 15 : 0;
            default:
                if (isButton(id)) return (state & ATTACH_POWERED) != 0 ? 15 : 0;
                if (isPressurePlate(id)) return (state & PLATE_POWERED) != 0 ? 15 : 0;
                return 0;
        }
    }

    /** {@code BlockBehaviour.getSignal}(방향은 받는 쪽 → 이 블록). */
    private int blockSignal(int id, int state, int x, int y, int z, int dir) {
        switch (id) {
            case REDSTONE_WIRE: {
                if (!shouldSignal || dir == DIR_DOWN) return 0;
                int power = state & WIRE_POWER_MASK;
                if (power == 0) return 0;
                if (dir != DIR_UP) {
                    int shape = wireConnectionState(state, x, y, z);
                    if ((shape & wireSideBit(DIR_OPPOSITE[dir])) == 0) return 0;
                }
                return power;
            }
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF:
                return dir != DIR_UP ? ownSignal(id, state, x, y, z) : 0;
            case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
                return hToDir(state & 3) != dir ? ownSignal(id, state, x, y, z) : 0;
            case REPEATER: case COMPARATOR:
                return hToDir(state & 3) == dir ? ownSignal(id, state, x, y, z) : 0;
            case OBSERVER:
                return f6ToDir(f6Facing(state)) == dir ? ownSignal(id, state, x, y, z) : 0;
            default:
                return ownSignal(id, state, x, y, z);
        }
    }

    /** {@code BlockBehaviour.getDirectSignal}. */
    private int blockDirectSignal(int id, int state, int x, int y, int z, int dir) {
        switch (id) {
            case REDSTONE_WIRE:
                return shouldSignal ? blockSignal(id, state, x, y, z, dir) : 0;
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
                return dir == DIR_DOWN ? blockSignal(id, state, x, y, z, dir) : 0;
            case REPEATER: case COMPARATOR: case OBSERVER:
                return blockSignal(id, state, x, y, z, dir);
            case LEVER:
                return (state & ATTACH_POWERED) != 0 && attachConnectedDir(state) == dir ? 15 : 0;
            case TRIPWIRE_HOOK_BLOCK:
                return (state & HOOK_POWERED) != 0 && hToDir(state & 3) == dir ? 15 : 0;
            case TRAPPED_CHEST:
                return dir == DIR_UP ? blockSignal(id, state, x, y, z, dir) : 0;
            case DETECTOR_RAIL:
                return (state & RAIL_POWERED) != 0 && dir == DIR_UP ? 15 : 0;
            default:
                if (isButton(id)) return (state & ATTACH_POWERED) != 0 && attachConnectedDir(state) == dir ? 15 : 0;
                if (isPressurePlate(id)) return dir == DIR_UP ? ownSignal(id, state, x, y, z) : 0;
                return 0;
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 블록 행동 디스패치
    // ════════════════════════════════════════════════════════════════════════════════════

    private void onPlace(int id, int state, int x, int y, int z, int oldId, int oldState, boolean moved) {
        boolean same = redstoneVanillaBlock(oldId) == redstoneVanillaBlock(id);
        switch (id) {
            case REDSTONE_LAMP: case REDSTONE_LAMP_LIT:
                if (!same) {
                    int placed = placementBlock(id, x, y, z);
                    if (placed != id) setBlock(x, y, z, placed, 0, UPDATE_CLIENTS);
                }
                return;
            case REDSTONE_WIRE:
                if (!same) {
                    wireUpdatePowerStrength(x, y, z, state, true);
                    for (int dir : VERTICALS) updateNeighborsAt(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], REDSTONE_WIRE);
                    wireUpdateNeighborsOfNeighboringWires(x, y, z);
                }
                return;
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
                torchNotifyNeighbors(x, y, z, redstoneVanillaBlock(id));
                return;
            case REPEATER: case COMPARATOR:
                diodeUpdateNeighborsInFront(id, state, x, y, z);
                return;
            case OBSERVER:
                if (!same && (state & OBSERVER_POWERED) != 0 && !hasScheduledTick(x, y, z, OBSERVER)) {
                    int next = state & ~OBSERVER_POWERED;
                    setBlock(x, y, z, id, next, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
                    observerUpdateNeighborsInFront(next, x, y, z);
                }
                return;
            case PISTON: case STICKY_PISTON:
                if (!same && !moving.containsKey(key(x, y, z))) pistonCheckIfExtend(id, state, x, y, z);
                return;
            case TARGET:
                if (!same && (state & 15) > 0 && !hasScheduledTick(x, y, z, TARGET)) {
                    setBlock(x, y, z, id, 0, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
                }
                return;
            case TRIPWIRE:
                if (!same) tripwireUpdateSource(x, y, z, state);
                return;
            case TNT:
                if (!same && hasNeighborSignal(x, y, z) && host.primeTnt(x, y, z)) removeBlock(x, y, z, false);
                return;
            default:
                if (isRail(id)) {
                    if (!same) railOnPlace(id, state, x, y, z, moved);
                    return;
                }
                if (Blocks.isCopperBulb(id) && !same) copperBulbCheckAndFlip(id, state, x, y, z);
        }
    }

    private void affectNeighborsAfterRemoval(int id, int state, int x, int y, int z, boolean moved) {
        switch (id) {
            case REDSTONE_WIRE:
                if (!moved) {
                    for (int dir : DIRECTIONS) updateNeighborsAt(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], REDSTONE_WIRE);
                    wireUpdatePowerStrength(x, y, z, state, false);
                    wireUpdateNeighborsOfNeighboringWires(x, y, z);
                }
                return;
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
                if (!moved) torchNotifyNeighbors(x, y, z, redstoneVanillaBlock(id));
                return;
            case REPEATER: case COMPARATOR:
                if (!moved) diodeUpdateNeighborsInFront(id, state, x, y, z);
                return;
            case OBSERVER:
                if ((state & OBSERVER_POWERED) != 0 && hasScheduledTick(x, y, z, OBSERVER)) {
                    observerUpdateNeighborsInFront(state & ~OBSERVER_POWERED, x, y, z);
                }
                return;
            case LEVER:
                if (!moved && (state & ATTACH_POWERED) != 0) attachUpdateNeighbours(id, state, x, y, z);
                return;
            case TRIPWIRE:
                if (!moved) tripwireUpdateSource(x, y, z, state | TRIPWIRE_POWERED);
                return;
            case TRIPWIRE_HOOK_BLOCK:
                if (!moved) hookOnRemoved(state, x, y, z);
                return;
            case PISTON_HEAD: {
                int back = DIR_OPPOSITE[f6ToDir(f6Facing(state))];
                int bx = x + DIR_DX[back];
                int by = y + DIR_DY[back];
                int bz = z + DIR_DZ[back];
                if (headFitsBase(state, blockAt(bx, by, bz), stateAt(bx, by, bz))) destroyBlock(bx, by, bz, true);
                return;
            }
            default:
                if (isRail(id)) {
                    if (!moved) {
                        if (railIsSlope(state)) updateNeighborsAt(x, y + 1, z, id);
                        if (isRedstoneRail(id)) {
                            updateNeighborsAt(x, y, z, id);
                            updateNeighborsAt(x, y - 1, z, id);
                        }
                    }
                    return;
                }
                if (isButton(id)) {
                    if (!moved && (state & ATTACH_POWERED) != 0) attachUpdateNeighbours(id, state, x, y, z);
                    return;
                }
                if (isPressurePlate(id)) {
                    if (!moved && (state & PLATE_POWERED) != 0) {
                        updateNeighborsAt(x, y, z, id);
                        updateNeighborsAt(x, y - 1, z, id);
                    }
                    return;
                }
                if (host.hasAnalogOutput(id, state)) updateNeighbourForOutputSignal(x, y, z, id);
        }
    }

    private void executeUpdate(int id, int state, int x, int y, int z, int block, boolean moved) {
        switch (id) {
            case AIR: return;
            case REDSTONE_WIRE:
                if (wireCanSurvive(x, y, z)) wireUpdatePowerStrength(x, y, z, state, false);
                else {
                    host.dropResources(x, y, z, id, state);
                    removeBlock(x, y, z, false);
                }
                return;
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF: {
                boolean lit = id == REDSTONE_TORCH || id == REDSTONE_WALL_TORCH;
                if (lit == torchHasNeighborSignal(id, state, x, y, z) && !willTickThisTick(x, y, z, id)) {
                    scheduleTick(x, y, z, id, 2);
                }
                return;
            }
            case REPEATER: case COMPARATOR:
                if (redstoneVanillaBlock(blockAt(x, y, z)) != id) return;
                if (diodeCanSurvive(x, y, z)) {
                    if (id == COMPARATOR) comparatorCheckTickOnNeighbor(state, x, y, z);
                    else repeaterCheckTickOnNeighbor(state, x, y, z);
                } else {
                    host.dropResources(x, y, z, id, state);
                    removeBlock(x, y, z, false);
                    for (int dir : DIRECTIONS) updateNeighborsAt(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], id);
                }
                return;
            case PISTON: case STICKY_PISTON:
                pistonCheckIfExtend(id, state, x, y, z);
                return;
            case PISTON_HEAD:
                if (headCanSurvive(state, x, y, z)) {
                    int back = DIR_OPPOSITE[f6ToDir(f6Facing(state))];
                    neighborChanged(x + DIR_DX[back], y + DIR_DY[back], z + DIR_DZ[back], block);
                }
                return;
            case REDSTONE_LAMP: case REDSTONE_LAMP_LIT: {
                boolean lit = id == REDSTONE_LAMP_LIT;
                if (lit != hasNeighborSignal(x, y, z)) {
                    if (lit) scheduleTick(x, y, z, id, 4);
                    else setBlock(x, y, z, REDSTONE_LAMP_LIT, state, UPDATE_CLIENTS);
                }
                return;
            }
            case NOTE_BLOCK: {
                boolean signal = hasNeighborSignal(x, y, z);
                if (signal != ((state & NOTE_POWERED) != 0)) {
                    if (signal) playNote(state, x, y, z);
                    setBlockAndUpdate(x, y, z, id, signal ? state | NOTE_POWERED : state & ~NOTE_POWERED);
                }
                return;
            }
            case DISPENSER: case DROPPER_ID: case CRAFTER_ID: case HOPPER:
                // 발사기·공급기·제작기(TRIGGERED 가장자리와 4틱 발사)와 호퍼 잠금은 권위의 컨테이너 규칙이
                // 소유한다. 엔진은 바닐라 neighborChanged 를 그 규칙의 신호 이웃 큐로 넘기고, 규칙은 이 엔진의
                // hasNeighborSignal 을 읽는다.
                host.consumerNeighborChanged(x, y, z, id);
                return;
            case TNT:
                if (hasNeighborSignal(x, y, z) && host.primeTnt(x, y, z)) removeBlock(x, y, z, false);
                return;
            case BELL: {
                boolean signal = hasNeighborSignal(x, y, z);
                if (signal != ((state & BELL_POWERED) != 0)) {
                    if (signal) host.ringBell(x, y, z);
                    setBlockAndUpdate(x, y, z, id, signal ? state | BELL_POWERED : state & ~BELL_POWERED);
                }
                return;
            }
            default:
                if (isRail(id)) railNeighborChanged(id, state, x, y, z, block, moved);
                else if (Blocks.isDoor(id)) doorNeighborChanged(id, state, x, y, z, block);
                else if (Blocks.isTrapdoor(id)) trapdoorNeighborChanged(id, state, x, y, z);
                else if (Blocks.isFenceGate(id)) gateNeighborChanged(id, state, x, y, z);
                else if (Blocks.isCopperBulb(id)) copperBulbCheckAndFlip(id, state, x, y, z);
                else if (Blocks.isShelf(id)) host.consumerNeighborChanged(x, y, z, id);
        }
    }

    /** {@code BlockBehaviour.updateShape}. 결과는 (id<<8|state). */
    private int updateShape(int id, int state, int x, int y, int z, int dir, int nx, int ny, int nz, int nid,
            int nstate) {
        int same = id << 8 | state;
        switch (id) {
            case AIR: return same;
            case REDSTONE_WIRE:
                return wireUpdateShape(state, x, y, z, dir, nx, ny, nz, nid, nstate);
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF:
                return dir == DIR_DOWN && !floorTorchCanSurvive(x, y, z) ? 0 : same;
            case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
                return DIR_OPPOSITE[dir] == hToDir(state & 3) && !wallTorchCanSurvive(state, x, y, z) ? 0 : same;
            case REPEATER:
                if (dir == DIR_DOWN && !host.isRigidTop(nid, nstate)) return 0;
                if (dirAxis(dir) != dirAxis(hToDir(state & 3))) {
                    boolean locked = repeaterIsLocked(state, x, y, z);
                    return id << 8 | (locked ? state | REPEATER_LOCKED : state & ~REPEATER_LOCKED);
                }
                return same;
            case COMPARATOR:
                return dir == DIR_DOWN && !host.isRigidTop(nid, nstate) ? 0 : same;
            case OBSERVER:
                if (f6ToDir(f6Facing(state)) == dir && (state & OBSERVER_POWERED) == 0) {
                    if (!hasScheduledTick(x, y, z, OBSERVER)) scheduleTick(x, y, z, OBSERVER, 2);
                }
                return same;
            case LEVER:
                return DIR_OPPOSITE[attachConnectedDir(state)] == dir && !attachCanSurvive(state, x, y, z) ? 0 : same;
            case TRIPWIRE:
                if (dir != DIR_UP && dir != DIR_DOWN) {
                    int bit = tripwireSideBit(dir);
                    return id << 8 | (tripwireShouldConnectTo(nid, nstate, dir) ? state | bit : state & ~bit);
                }
                return same;
            case TRIPWIRE_HOOK_BLOCK:
                return DIR_OPPOSITE[dir] == hToDir(state & 3) && !hookCanSurvive(state, x, y, z) ? 0 : same;
            case PISTON_HEAD:
                return DIR_OPPOSITE[dir] == f6ToDir(f6Facing(state)) && !headCanSurvive(state, x, y, z) ? 0 : same;
            default:
                if (isButton(id)) {
                    return DIR_OPPOSITE[attachConnectedDir(state)] == dir && !attachCanSurvive(state, x, y, z) ? 0 : same;
                }
                if (isPressurePlate(id)) return dir == DIR_DOWN && !plateCanSurvive(x, y, z) ? 0 : same;
                if (Blocks.isDoor(id)) return doorUpdateShape(id, state, dir, nid, nstate);
                return same;
        }
    }

    private void tick(int id, int state, int x, int y, int z) {
        switch (id) {
            case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH: case REDSTONE_WALL_TORCH_OFF:
                torchTick(id, state, x, y, z);
                return;
            case REPEATER:
                diodeTick(id, state, x, y, z);
                return;
            case COMPARATOR:
                comparatorRefreshOutput(state, x, y, z);
                return;
            case OBSERVER:
                if ((state & OBSERVER_POWERED) != 0) setBlock(x, y, z, id, state & ~OBSERVER_POWERED, UPDATE_CLIENTS);
                else {
                    setBlock(x, y, z, id, state | OBSERVER_POWERED, UPDATE_CLIENTS);
                    scheduleTick(x, y, z, OBSERVER, 2);
                }
                observerUpdateNeighborsInFront(state, x, y, z);
                return;
            case TARGET:
                if ((state & 15) != 0) setBlockAndUpdate(x, y, z, id, 0);
                return;
            case REDSTONE_LAMP: case REDSTONE_LAMP_LIT:
                if (id == REDSTONE_LAMP_LIT && !hasNeighborSignal(x, y, z)) setBlock(x, y, z, REDSTONE_LAMP, state, UPDATE_CLIENTS);
                return;
            case TRIPWIRE:
                if ((stateAt(x, y, z) & TRIPWIRE_POWERED) != 0) tripwireCheckPressed(x, y, z);
                return;
            case TRIPWIRE_HOOK_BLOCK:
                hookCalculateState(x, y, z, state, false, true, -1, -1);
                return;
            default:
                if (isButton(id)) {
                    if ((state & ATTACH_POWERED) != 0) buttonCheckPressed(id, state, x, y, z);
                    return;
                }
                if (isPressurePlate(id)) {
                    if ((state & PLATE_POWERED) != 0) plateCheckPressed(id, x, y, z, state, 15);
                }
                if (id == DETECTOR_RAIL && (state & RAIL_POWERED) != 0) detectorCheckPressed(state, x, y, z);
        }
    }

    private boolean triggerEvent(int id, int state, int x, int y, int z, int b0, int b1) {
        if (id == PISTON || id == STICKY_PISTON) return pistonTriggerEvent(id, state, x, y, z, b0, b1);
        if (id == NOTE_BLOCK) {
            host.playNote(x, y, z, noteValue(state));
            return true;
        }
        return false;
    }

    private void setPlacedBy(int id, int state, int x, int y, int z) {
        switch (id) {
            case REPEATER:
                if (repeaterShouldTurnOn(state, x, y, z)) scheduleTick(x, y, z, id, 1);
                return;
            case COMPARATOR:
                if (comparatorShouldTurnOn(state, x, y, z)) scheduleTick(x, y, z, id, 1);
                return;
            case PISTON: case STICKY_PISTON:
                pistonCheckIfExtend(id, state, x, y, z);
                return;
            case TRIPWIRE_HOOK_BLOCK:
                hookCalculateState(x, y, z, state, false, false, -1, -1);
                return;
            default:
                return;
        }
    }

    private void updateIndirectNeighbourShapes(int id, int state, int x, int y, int z, int flags, int limit) {
        if (id != REDSTONE_WIRE) return;
        for (int dir : HORIZONTALS) {
            if ((state & wireSideBit(dir)) == 0) continue;
            int sx = x + DIR_DX[dir];
            int sz = z + DIR_DZ[dir];
            if (blockAt(sx, y, sz) == REDSTONE_WIRE) continue;
            int opposite = DIR_OPPOSITE[dir];
            if (blockAt(sx, y - 1, sz) == REDSTONE_WIRE) {
                int tx = sx + DIR_DX[opposite];
                int tz = sz + DIR_DZ[opposite];
                neighborShapeChanged(opposite, sx, y - 1, sz, tx, y - 1, tz, blockAt(tx, y - 1, tz),
                        stateAt(tx, y - 1, tz), flags, limit);
            }
            if (blockAt(sx, y + 1, sz) == REDSTONE_WIRE) {
                int tx = sx + DIR_DX[opposite];
                int tz = sz + DIR_DZ[opposite];
                neighborShapeChanged(opposite, sx, y + 1, sz, tx, y + 1, tz, blockAt(tx, y + 1, tz),
                        stateAt(tx, y + 1, tz), flags, limit);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 레드스톤 가루(RedStoneWireBlock + DefaultRedstoneWireEvaluator)
    // ════════════════════════════════════════════════════════════════════════════════════

    private boolean wireCanSurviveOn(int id, int state) {
        return host.isFaceSturdy(id, state, DIR_UP) || id == HOPPER;
    }

    private boolean wireCanSurvive(int x, int y, int z) {
        return wireCanSurviveOn(blockAt(x, y - 1, z), stateAt(x, y - 1, z));
    }

    /** {@code shouldConnectTo(state, direction)}; direction −1 = null. */
    private boolean wireShouldConnectTo(int id, int state, int dir) {
        if (id == REDSTONE_WIRE) return true;
        if (id == REPEATER) {
            int facing = hToDir(state & 3);
            return facing == dir || DIR_OPPOSITE[facing] == dir;
        }
        if (id == OBSERVER) return dir == f6ToDir(f6Facing(state));
        return isSignalSource(id, state) && dir != -1;
    }

    /** {@code getConnectingSide}: 0 NONE · 1 SIDE · 2 UP. */
    private int wireConnectingSide(int x, int y, int z, int dir, boolean canConnectUp) {
        int rx = x + DIR_DX[dir];
        int rz = z + DIR_DZ[dir];
        int rid = blockAt(rx, y, rz);
        int rstate = stateAt(rx, y, rz);
        if (canConnectUp) {
            boolean placeableAbove = Blocks.isTrapdoor(rid) || wireCanSurviveOn(rid, rstate);
            if (placeableAbove && wireShouldConnectTo(blockAt(rx, y + 1, rz), stateAt(rx, y + 1, rz), -1)) {
                if (host.isFaceSturdy(rid, rstate, DIR_OPPOSITE[dir])) return 2;
                return 1;
            }
        }
        return !wireShouldConnectTo(rid, rstate, dir)
                && (host.isConductor(rid, rstate)
                        || !wireShouldConnectTo(blockAt(rx, y - 1, rz), stateAt(rx, y - 1, rz), -1))
                ? 0 : 1;
    }

    private boolean wireCanConnectUp(int x, int y, int z) {
        return !isConductorAt(x, y + 1, z);
    }

    /** {@code getMissingConnections} → 연결 비트(N·E·S·W). {@code from} 에 이미 연결된 방향은 유지한다. */
    private int wireMissingConnections(int from, int x, int y, int z) {
        boolean canConnectUp = wireCanConnectUp(x, y, z);
        int sides = from & WIRE_SIDES_MASK;
        for (int dir : HORIZONTALS) {
            int bit = wireSideBit(dir);
            if ((sides & bit) == 0 && wireConnectingSide(x, y, z, dir, canConnectUp) != 0) sides |= bit;
        }
        return sides;
    }

    /** {@code getConnectionState(level, state, pos)} → 새 연결 비트(파워 비트 제외). */
    private int wireConnectionSides(int state, int x, int y, int z) {
        boolean wasDot = (state & WIRE_SIDES_MASK) == 0;
        int sides = wireMissingConnections(0, x, y, z);
        if (wasDot && sides == 0) return 0;
        boolean north = (sides & 0x10) != 0;
        boolean east = (sides & 0x20) != 0;
        boolean south = (sides & 0x40) != 0;
        boolean west = (sides & 0x80) != 0;
        boolean northSouthEmpty = !north && !south;
        boolean eastWestEmpty = !east && !west;
        int out = sides;
        if (!west && northSouthEmpty) out |= 0x80;
        if (!east && northSouthEmpty) out |= 0x20;
        if (!north && eastWestEmpty) out |= 0x10;
        if (!south && eastWestEmpty) out |= 0x40;
        return out;
    }

    private int wireConnectionState(int state, int x, int y, int z) {
        return wireConnectionSides(state, x, y, z) | (state & WIRE_POWER_MASK);
    }

    private int wireUpdateShape(int state, int x, int y, int z, int dir, int nx, int ny, int nz, int nid,
            int nstate) {
        if (dir == DIR_DOWN) return wireCanSurviveOn(nid, nstate) ? REDSTONE_WIRE << 8 | state : 0;
        if (dir == DIR_UP) return REDSTONE_WIRE << 8 | wireConnectionState(state, x, y, z);
        int side = wireConnectingSide(x, y, z, dir, wireCanConnectUp(x, y, z));
        int bit = wireSideBit(dir);
        boolean stored = (state & bit) != 0;
        boolean isCross = (state & WIRE_SIDES_MASK) == WIRE_SIDES_MASK;
        if ((side != 0) == stored && !isCross) {
            return REDSTONE_WIRE << 8 | (side != 0 ? state | bit : state & ~bit);
        }
        int cross = WIRE_SIDES_MASK | (state & WIRE_POWER_MASK);
        int seeded = side != 0 ? cross | bit : cross & ~bit;
        return REDSTONE_WIRE << 8 | wireConnectionState(seeded, x, y, z);
    }

    private boolean wireUse(int state, int x, int y, int z) {
        int sides = state & WIRE_SIDES_MASK;
        boolean isCross = sides == WIRE_SIDES_MASK;
        boolean isDot = sides == 0;
        if (!isCross && !isDot) return false;
        int next = (isCross ? 0 : WIRE_SIDES_MASK) | (state & WIRE_POWER_MASK);
        next = wireConnectionState(next, x, y, z);
        if (next == state) return false;
        setBlockAndUpdate(x, y, z, REDSTONE_WIRE, next);
        for (int dir : HORIZONTALS) {
            int bit = wireSideBit(dir);
            int rx = x + DIR_DX[dir];
            int rz = z + DIR_DZ[dir];
            if (((state & bit) != 0) != ((next & bit) != 0) && isConductorAt(rx, y, rz)) {
                updateNeighborsAtExceptFromFacing(rx, y, rz, REDSTONE_WIRE, DIR_OPPOSITE[dir]);
            }
        }
        return true;
    }

    private int wireGetBlockSignal(int x, int y, int z) {
        shouldSignal = false;
        int signal = getBestNeighborSignal(x, y, z);
        shouldSignal = true;
        return signal;
    }

    private int wireIncomingSignal(int x, int y, int z) {
        int wire = 0;
        for (int dir : HORIZONTALS) {
            int nx = x + DIR_DX[dir];
            int nz = z + DIR_DZ[dir];
            int nid = blockAt(nx, y, nz);
            if (nid == REDSTONE_WIRE) wire = Math.max(wire, stateAt(nx, y, nz) & WIRE_POWER_MASK);
            boolean neighbourConductor = nid != AIR && host.isConductor(nid, stateAt(nx, y, nz));
            if (neighbourConductor && !isConductorAt(x, y + 1, z)) {
                if (blockAt(nx, y + 1, nz) == REDSTONE_WIRE) wire = Math.max(wire, stateAt(nx, y + 1, nz) & WIRE_POWER_MASK);
            } else if (!neighbourConductor) {
                if (blockAt(nx, y - 1, nz) == REDSTONE_WIRE) wire = Math.max(wire, stateAt(nx, y - 1, nz) & WIRE_POWER_MASK);
            }
        }
        return Math.max(0, wire - 1);
    }

    private void wireUpdatePowerStrength(int x, int y, int z, int state, boolean skipShapeUpdates) {
        int blockSignal = wireGetBlockSignal(x, y, z);
        int target = blockSignal == 15 ? blockSignal : Math.max(blockSignal, wireIncomingSignal(x, y, z));
        if ((state & WIRE_POWER_MASK) == target) return;
        if (blockAt(x, y, z) == REDSTONE_WIRE && stateAt(x, y, z) == state) {
            setBlock(x, y, z, REDSTONE_WIRE, (state & ~WIRE_POWER_MASK) | target, UPDATE_CLIENTS);
        }
        // Sets.newHashSet() 순회 순서는 BlockPos.hashCode 순서다. 같은 결과를 두 권위가 내도록 그 순서를
        // 그대로 재현한다.
        List<int[]> positions = new ArrayList<>();
        positions.add(new int[] {x, y, z});
        for (int dir : DIRECTIONS) positions.add(new int[] {x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir]});
        for (int[] p : hashSetOrder(positions)) updateNeighborsAt(p[0], p[1], p[2], REDSTONE_WIRE);
    }

    private void wireCheckCornerChangeAt(int x, int y, int z) {
        if (blockAt(x, y, z) != REDSTONE_WIRE) return;
        updateNeighborsAt(x, y, z, REDSTONE_WIRE);
        for (int dir : DIRECTIONS) updateNeighborsAt(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], REDSTONE_WIRE);
    }

    private void wireUpdateNeighborsOfNeighboringWires(int x, int y, int z) {
        for (int dir : HORIZONTALS) wireCheckCornerChangeAt(x + DIR_DX[dir], y, z + DIR_DZ[dir]);
        for (int dir : HORIZONTALS) {
            int tx = x + DIR_DX[dir];
            int tz = z + DIR_DZ[dir];
            if (isConductorAt(tx, y, tz)) wireCheckCornerChangeAt(tx, y + 1, tz);
            else wireCheckCornerChangeAt(tx, y - 1, tz);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 레드스톤 횃불
    // ════════════════════════════════════════════════════════════════════════════════════

    private void torchNotifyNeighbors(int x, int y, int z, int block) {
        for (int dir : DIRECTIONS) updateNeighborsAt(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], block);
    }

    private boolean torchHasNeighborSignal(int id, int state, int x, int y, int z) {
        if (id == REDSTONE_WALL_TORCH || id == REDSTONE_WALL_TORCH_OFF) {
            int opposite = DIR_OPPOSITE[hToDir(state & 3)];
            return hasSignal(x + DIR_DX[opposite], y + DIR_DY[opposite], z + DIR_DZ[opposite], opposite);
        }
        return hasSignal(x, y - 1, z, DIR_DOWN);
    }

    private void torchTick(int id, int state, int x, int y, int z) {
        boolean signal = torchHasNeighborSignal(id, state, x, y, z);
        while (!torchToggles.isEmpty() && gameTime - torchToggles.peekFirst().when > TORCH_RECENT_TOGGLE_TIMER) {
            torchToggles.removeFirst();
        }
        boolean wall = id == REDSTONE_WALL_TORCH || id == REDSTONE_WALL_TORCH_OFF;
        boolean lit = id == REDSTONE_TORCH || id == REDSTONE_WALL_TORCH;
        if (lit) {
            if (signal) {
                setBlockAndUpdate(x, y, z, wall ? REDSTONE_WALL_TORCH_OFF : REDSTONE_TORCH_OFF, state);
                if (torchToggledTooFrequently(x, y, z, true)) {
                    host.sound("redstone_torch_burnout", x, y, z, 1);
                    scheduleTick(x, y, z, blockAt(x, y, z), TORCH_RESTART_DELAY);
                }
            }
        } else if (!signal && !torchToggledTooFrequently(x, y, z, false)) {
            setBlockAndUpdate(x, y, z, wall ? REDSTONE_WALL_TORCH : REDSTONE_TORCH, state);
        }
    }

    private boolean torchToggledTooFrequently(int x, int y, int z, boolean add) {
        if (add) torchToggles.addLast(new TorchToggle(x, y, z, gameTime));
        int count = 0;
        for (TorchToggle toggle : torchToggles) {
            if (toggle.x == x && toggle.y == y && toggle.z == z && ++count >= TORCH_MAX_RECENT_TOGGLES) return true;
        }
        return false;
    }

    private boolean floorTorchCanSurvive(int x, int y, int z) {
        return host.canSupportCenter(blockAt(x, y - 1, z), stateAt(x, y - 1, z), DIR_UP);
    }

    private boolean wallTorchCanSurvive(int state, int x, int y, int z) {
        int facing = hToDir(state & 3);
        int back = DIR_OPPOSITE[facing];
        int sx = x + DIR_DX[back];
        int sz = z + DIR_DZ[back];
        return host.isFaceSturdy(blockAt(sx, y, sz), stateAt(sx, y, sz), facing);
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 다이오드(중계기·비교기)
    // ════════════════════════════════════════════════════════════════════════════════════

    private boolean diodeCanSurvive(int x, int y, int z) {
        return host.isRigidTop(blockAt(x, y - 1, z), stateAt(x, y - 1, z));
    }

    private int diodeInputSignal(int state, int x, int y, int z) {
        int dir = hToDir(state & 3);
        int tx = x + DIR_DX[dir];
        int tz = z + DIR_DZ[dir];
        int input = getSignal(tx, y, tz, dir);
        if (input >= 15) return input;
        return Math.max(input, blockAt(tx, y, tz) == REDSTONE_WIRE ? stateAt(tx, y, tz) & WIRE_POWER_MASK : 0);
    }

    private int diodeAlternateSignal(int state, int x, int y, int z, boolean diodesOnly) {
        int dir = hToDir(state & 3);
        int cw = dirClockWise(dir);
        int ccw = dirCounterClockWise(dir);
        return Math.max(
                getControlInputSignal(x + DIR_DX[cw], y, z + DIR_DZ[cw], cw, diodesOnly),
                getControlInputSignal(x + DIR_DX[ccw], y, z + DIR_DZ[ccw], ccw, diodesOnly));
    }

    private boolean diodeShouldPrioritize(int state, int x, int y, int z) {
        int dir = DIR_OPPOSITE[hToDir(state & 3)];
        int ox = x + DIR_DX[dir];
        int oz = z + DIR_DZ[dir];
        int oid = blockAt(ox, y, oz);
        return (oid == REPEATER || oid == COMPARATOR) && hToDir(stateAt(ox, y, oz) & 3) != dir;
    }

    private void diodeUpdateNeighborsInFront(int id, int state, int x, int y, int z) {
        int dir = hToDir(state & 3);
        int opposite = DIR_OPPOSITE[dir];
        int ox = x + DIR_DX[opposite];
        int oz = z + DIR_DZ[opposite];
        neighborChanged(ox, y, oz, id);
        updateNeighborsAtExceptFromFacing(ox, y, oz, id, dir);
    }

    private boolean repeaterIsLocked(int state, int x, int y, int z) {
        return diodeAlternateSignal(state, x, y, z, true) > 0;
    }

    private boolean repeaterShouldTurnOn(int state, int x, int y, int z) {
        return diodeInputSignal(state, x, y, z) > 0;
    }

    private void diodeTick(int id, int state, int x, int y, int z) {
        if (repeaterIsLocked(state, x, y, z)) return;
        boolean on = (state & REPEATER_POWERED) != 0;
        boolean shouldTurnOn = repeaterShouldTurnOn(state, x, y, z);
        if (on && !shouldTurnOn) setBlock(x, y, z, id, state & ~REPEATER_POWERED, UPDATE_CLIENTS);
        else if (!on) {
            setBlock(x, y, z, id, state | REPEATER_POWERED, UPDATE_CLIENTS);
            if (!shouldTurnOn) scheduleTick(x, y, z, id, repeaterDelay(state) * 2, PRIORITY_VERY_HIGH);
        }
    }

    private void repeaterCheckTickOnNeighbor(int state, int x, int y, int z) {
        if (repeaterIsLocked(state, x, y, z)) return;
        boolean on = (state & REPEATER_POWERED) != 0;
        boolean shouldTurnOn = repeaterShouldTurnOn(state, x, y, z);
        if (on != shouldTurnOn && !willTickThisTick(x, y, z, REPEATER)) {
            int priority = PRIORITY_HIGH;
            if (diodeShouldPrioritize(state, x, y, z)) priority = PRIORITY_EXTREMELY_HIGH;
            else if (on) priority = PRIORITY_VERY_HIGH;
            scheduleTick(x, y, z, REPEATER, repeaterDelay(state) * 2, priority);
        }
    }

    private int comparatorInputSignal(int state, int x, int y, int z) {
        int result = diodeInputSignal(state, x, y, z);
        int dir = hToDir(state & 3);
        int tx = x + DIR_DX[dir];
        int tz = z + DIR_DZ[dir];
        int tid = blockAt(tx, y, tz);
        int tstate = stateAt(tx, y, tz);
        int opposite = DIR_OPPOSITE[dir];
        if (host.hasAnalogOutput(tid, tstate)) {
            result = host.analogOutput(tx, y, tz, opposite);
        } else if (result < 15 && tid != AIR && host.isConductor(tid, tstate)) {
            tx += DIR_DX[dir];
            tz += DIR_DZ[dir];
            tid = blockAt(tx, y, tz);
            tstate = stateAt(tx, y, tz);
            if (host.hasAnalogOutput(tid, tstate)) result = host.analogOutput(tx, y, tz, opposite);
        }
        return result;
    }

    private int comparatorCalculateOutput(int state, int x, int y, int z) {
        int input = comparatorInputSignal(state, x, y, z);
        if (input == 0) return 0;
        int alternate = diodeAlternateSignal(state, x, y, z, false);
        if (alternate > input) return 0;
        return (state & COMPARATOR_SUBTRACT) != 0 ? input - alternate : input;
    }

    private boolean comparatorShouldTurnOn(int state, int x, int y, int z) {
        int input = comparatorInputSignal(state, x, y, z);
        if (input == 0) return false;
        int side = diodeAlternateSignal(state, x, y, z, false);
        return input > side || (input == side && (state & COMPARATOR_SUBTRACT) == 0);
    }

    private void comparatorCheckTickOnNeighbor(int state, int x, int y, int z) {
        if (willTickThisTick(x, y, z, COMPARATOR)) return;
        int output = comparatorCalculateOutput(state, x, y, z);
        int old = comparatorOutput(state);
        if (output != old || ((state & COMPARATOR_POWERED) != 0) != comparatorShouldTurnOn(state, x, y, z)) {
            int priority = diodeShouldPrioritize(state, x, y, z) ? PRIORITY_HIGH : PRIORITY_NORMAL;
            scheduleTick(x, y, z, COMPARATOR, 2, priority);
        }
    }

    private void comparatorRefreshOutput(int state, int x, int y, int z) {
        int output = comparatorCalculateOutput(state, x, y, z);
        int old = comparatorOutput(state);
        // 블록 엔티티 OutputSignal 쓰기(바닐라는 setChanged 없이 필드만 바꾼다 → 알림 없는 상태 쓰기).
        int current = withComparatorOutput(state, output);
        if (current != state) {
            writeRaw(x, y, z, COMPARATOR, current);
        }
        if (old != output || (state & COMPARATOR_SUBTRACT) == 0) {
            boolean sourceOn = comparatorShouldTurnOn(state, x, y, z);
            boolean isOn = (state & COMPARATOR_POWERED) != 0;
            if (isOn && !sourceOn) {
                current &= ~COMPARATOR_POWERED;
                setBlock(x, y, z, COMPARATOR, current, UPDATE_CLIENTS);
            } else if (!isOn && sourceOn) {
                current |= COMPARATOR_POWERED;
                setBlock(x, y, z, COMPARATOR, current, UPDATE_CLIENTS);
            }
            diodeUpdateNeighborsInFront(COMPARATOR, state, x, y, z);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 관찰자
    // ════════════════════════════════════════════════════════════════════════════════════

    private void observerUpdateNeighborsInFront(int state, int x, int y, int z) {
        int dir = f6ToDir(f6Facing(state));
        int opposite = DIR_OPPOSITE[dir];
        int ox = x + DIR_DX[opposite];
        int oy = y + DIR_DY[opposite];
        int oz = z + DIR_DZ[opposite];
        neighborChanged(ox, oy, oz, OBSERVER);
        updateNeighborsAtExceptFromFacing(ox, oy, oz, OBSERVER, dir);
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 레버·버튼·감압판(면 부착)
    // ════════════════════════════════════════════════════════════════════════════════════

    private boolean attachCanSurvive(int state, int x, int y, int z) {
        int toward = DIR_OPPOSITE[attachConnectedDir(state)];
        int sx = x + DIR_DX[toward];
        int sy = y + DIR_DY[toward];
        int sz = z + DIR_DZ[toward];
        return host.isFaceSturdy(blockAt(sx, sy, sz), stateAt(sx, sy, sz), DIR_OPPOSITE[toward]);
    }

    private void attachUpdateNeighbours(int id, int state, int x, int y, int z) {
        int front = DIR_OPPOSITE[attachConnectedDir(state)];
        updateNeighborsAt(x, y, z, id);
        updateNeighborsAt(x + DIR_DX[front], y + DIR_DY[front], z + DIR_DZ[front], id);
    }

    private void leverPull(int state, int x, int y, int z) {
        int next = state ^ ATTACH_POWERED;
        setBlockAndUpdate(x, y, z, LEVER, next);
        attachUpdateNeighbours(LEVER, next, x, y, z);
        host.sound("lever_click", x, y, z, (next & ATTACH_POWERED) != 0 ? 0.6 : 0.5);
    }

    private void buttonPress(int id, int state, int x, int y, int z) {
        setBlockAndUpdate(x, y, z, id, state | ATTACH_POWERED);
        attachUpdateNeighbours(id, state, x, y, z);
        scheduleTick(x, y, z, id, buttonPressTicks(id));
        host.sound(id == STONE_BUTTON ? "stone_button_click_on" : "wooden_button_click_on", x, y, z, 1);
    }

    private void buttonCheckPressed(int id, int state, int x, int y, int z) {
        // 화살 감지는 나무 버튼만(BlockSetType.canButtonBeActivatedByArrows).
        boolean arrows = id != STONE_BUTTON;
        double[] box = attachShapeBox(state);
        boolean pressed = arrows && host.hasArrow(x + box[0], y + box[1], z + box[2], x + box[3], y + box[4], z + box[5]);
        boolean was = (state & ATTACH_POWERED) != 0;
        if (pressed != was) {
            setBlockAndUpdate(x, y, z, id, pressed ? state | ATTACH_POWERED : state & ~ATTACH_POWERED);
            attachUpdateNeighbours(id, state, x, y, z);
            String sound = id == STONE_BUTTON ? "stone_button_click_" : "wooden_button_click_";
            host.sound(sound + (pressed ? "on" : "off"), x, y, z, 1);
        }
        if (pressed) scheduleTick(x, y, z, id, buttonPressTicks(id));
    }

    private boolean plateCanSurvive(int x, int y, int z) {
        int id = blockAt(x, y - 1, z);
        int state = stateAt(x, y - 1, z);
        return host.isRigidTop(id, state) || host.canSupportCenter(id, state, DIR_UP);
    }

    private void plateCheckPressed(int id, int x, int y, int z, int state, int oldSignal) {
        // TOUCH_AABB = Block.column(14, 0, 4) = (1/16, 0, 1/16) .. (15/16, 4/16, 15/16).
        int count = host.countEntities(x + 1.0 / 16, y, z + 1.0 / 16, x + 15.0 / 16, y + 0.25, z + 15.0 / 16,
                plateDetectsEverything(id) ? ENTITY_FILTER_EVERYTHING : ENTITY_FILTER_LIVING);
        int signal = count > 0 ? 15 : 0;
        boolean wasPressed = oldSignal > 0;
        boolean isPressed = signal > 0;
        if (oldSignal != signal) {
            setBlock(x, y, z, id, isPressed ? state | PLATE_POWERED : state & ~PLATE_POWERED, UPDATE_CLIENTS);
            updateNeighborsAt(x, y, z, id);
            updateNeighborsAt(x, y - 1, z, id);
        }
        boolean wood = plateDetectsEverything(id);
        if (!isPressed && wasPressed) host.sound(wood ? "wooden_pressure_plate_click_off" : "stone_pressure_plate_click_off", x, y, z, 1);
        else if (isPressed && !wasPressed) host.sound(wood ? "wooden_pressure_plate_click_on" : "stone_pressure_plate_click_on", x, y, z, 1);
        if (isPressed) scheduleTick(x, y, z, id, 20);
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 과녁·햇빛 감지기·램프·소리 블록·호퍼·구리 전구·문·다락문·울타리 문·선반
    // ════════════════════════════════════════════════════════════════════════════════════

    private void updateDaylightSignal(int state, int x, int y, int z) {
        int target = host.effectiveSkyBrightness(x, y, z);
        float sunAngle = (float) host.sunAngleDegrees() * (float) (Math.PI / 180);
        if ((state & DAYLIGHT_INVERTED) != 0) target = 15 - target;
        else if (target > 0) {
            float offset = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            sunAngle = sunAngle + (offset - sunAngle) * 0.2F;
            target = Math.round((float) target * mthCos(sunAngle));
        }
        target = Math.max(0, Math.min(15, target));
        if ((state & 15) != target) setBlockAndUpdate(x, y, z, DAYLIGHT_DETECTOR, (state & ~15) | target);
    }

    private void playNote(int state, int x, int y, int z) {
        // 악기가 머리 계열이 아니면 위가 공기일 때만 울린다(worksAboveNoteBlock 은 머리뿐이다).
        if (blockAt(x, y + 1, z) == AIR) blockEvent(x, y, z, NOTE_BLOCK, 0, 0);
    }

    private void copperBulbCheckAndFlip(int id, int state, int x, int y, int z) {
        boolean signal = hasNeighborSignal(x, y, z);
        boolean powered = (state & BULB_POWERED) != 0;
        if (signal == powered) return;
        int next = id;
        if (!powered) {
            next = Blocks.toggledCopperBulb(id);
            host.sound(Blocks.isCopperBulbLit(next) ? "copper_bulb_turn_on" : "copper_bulb_turn_off", x, y, z, 1);
        }
        setBlockAndUpdate(x, y, z, next, signal ? state | BULB_POWERED : state & ~BULB_POWERED);
    }

    private void doorNeighborChanged(int id, int state, int x, int y, int z, int block) {
        boolean upper = (state & 0x08) != 0;
        boolean signal = hasNeighborSignal(x, y, z) || hasNeighborSignal(x, upper ? y - 1 : y + 1, z);
        if (redstoneVanillaBlock(block) == id) return;
        boolean powered = (state & DOOR_POWERED) != 0;
        if (signal == powered) return;
        boolean open = (state & 0x04) != 0;
        if (signal != open) host.sound(signal ? "door_open" : "door_close", x, y, z, 1);
        int next = (state & ~(DOOR_POWERED | 0x04)) | (signal ? DOOR_POWERED | 0x04 : 0);
        setBlock(x, y, z, id, next, UPDATE_CLIENTS);
    }

    /** {@code DoorBlock.updateShape}: 짝 반쪽의 facing·open·hinge·powered 를 따른다. */
    private int doorUpdateShape(int id, int state, int dir, int nid, int nstate) {
        boolean upper = (state & 0x08) != 0;
        if ((dir == DIR_UP && !upper) || (dir == DIR_DOWN && upper)) {
            if (nid == id && ((nstate & 0x08) != 0) != upper) {
                return id << 8 | (nstate & ~0x08) | (state & 0x08);
            }
        }
        return id << 8 | state;
    }

    private void trapdoorNeighborChanged(int id, int state, int x, int y, int z) {
        boolean signal = hasNeighborSignal(x, y, z);
        if (signal == ((state & TRAPDOOR_POWERED) != 0)) return;
        int next = state;
        if (((state & 0x08) != 0) != signal) {
            next = signal ? next | 0x08 : next & ~0x08;
            host.sound(signal ? "trapdoor_open" : "trapdoor_close", x, y, z, 1);
        }
        setBlock(x, y, z, id, signal ? next | TRAPDOOR_POWERED : next & ~TRAPDOOR_POWERED, UPDATE_CLIENTS);
    }

    private void gateNeighborChanged(int id, int state, int x, int y, int z) {
        boolean power = hasNeighborSignal(x, y, z);
        if (((state & GATE_POWERED) != 0) == power) return;
        setBlock(x, y, z, id, (state & ~(GATE_POWERED | 0x04)) | (power ? GATE_POWERED | 0x04 : 0), UPDATE_CLIENTS);
        if (((state & 0x04) != 0) != power) host.sound(power ? "fence_gate_open" : "fence_gate_close", x, y, z, 1);
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 동력·활성화·감지 레일(BaseRailBlock · PoweredRailBlock · DetectorRailBlock)
    // ════════════════════════════════════════════════════════════════════════════════════

    /** {@code canSupportRigidBlock(below)} 와 경사면 쪽 받침({@code shouldBeRemoved}). */
    private boolean railShouldBeRemoved(int state, int x, int y, int z) {
        if (!host.isRigidTop(blockAt(x, y - 1, z), stateAt(x, y - 1, z))) return true;
        int shape = state & (blockAt(x, y, z) == RAIL ? 15 : 7);
        int side = shape == 2 ? DIR_EAST : shape == 3 ? DIR_WEST : shape == 4 ? DIR_NORTH : shape == 5 ? DIR_SOUTH : -1;
        if (side < 0) return false;
        int sx = x + DIR_DX[side];
        int sz = z + DIR_DZ[side];
        return !host.isRigidTop(blockAt(sx, y, sz), stateAt(sx, y, sz));
    }

    /** {@code BaseRailBlock.onPlace} → {@code updateState(state, level, pos, movedByPiston)}(모양은 권위 레일 규칙). */
    private void railOnPlace(int id, int state, int x, int y, int z, boolean moved) {
        int shape = state & (id == RAIL ? 15 : 7);
        int next = new RedstoneRailShape(this, x, y, z).place(hasNeighborSignal(x, y, z), true, shape);
        if (isRedstoneRail(id)) neighborChangedFull(id, next, x, y, z, id, moved);
        if (id == DETECTOR_RAIL) detectorCheckPressed(stateAt(x, y, z), x, y, z);
    }

    private void railNeighborChanged(int id, int state, int x, int y, int z, int block, boolean moved) {
        if (blockAt(x, y, z) != id) return;
        if (railShouldBeRemoved(state, x, y, z)) {
            host.dropResources(x, y, z, id, state);
            removeBlock(x, y, z, moved);
            return;
        }
        if (id == RAIL) {
            RedstoneRailShape rail = new RedstoneRailShape(this, x, y, z);
            if (isSignalSource(block, 0) && rail.countPotentialConnections() == 3) {
                rail.place(hasNeighborSignal(x, y, z), false, state & 15);
            }
            return;
        }
        if (id == DETECTOR_RAIL) return;
        boolean powered = (state & RAIL_POWERED) != 0;
        boolean shouldPower = hasNeighborSignal(x, y, z)
                || poweredRailSignal(id, x, y, z, state, true, 0)
                || poweredRailSignal(id, x, y, z, state, false, 0);
        if (shouldPower != powered) {
            setBlockAndUpdate(x, y, z, id, shouldPower ? state | RAIL_POWERED : state & ~RAIL_POWERED);
            updateNeighborsAt(x, y - 1, z, id);
            if (railIsSlope(state)) updateNeighborsAt(x, y + 1, z, id);
        }
    }

    /** {@code PoweredRailBlock.findPoweredRailSignal}. */
    private boolean poweredRailSignal(int id, int x, int y, int z, int state, boolean forward, int depth) {
        if (depth >= 8) return false;
        boolean checkBelow = true;
        int shape = state & 7;
        switch (shape) {
            case 0: z += forward ? 1 : -1; break;
            case 1: x += forward ? -1 : 1; break;
            case 2:
                if (forward) x--;
                else {
                    x++;
                    y++;
                    checkBelow = false;
                }
                shape = 1;
                break;
            case 3:
                if (forward) {
                    x--;
                    y++;
                    checkBelow = false;
                } else x++;
                shape = 1;
                break;
            case 4:
                if (forward) z++;
                else {
                    z--;
                    y++;
                    checkBelow = false;
                }
                shape = 0;
                break;
            case 5:
                if (forward) {
                    z++;
                    y++;
                    checkBelow = false;
                } else z--;
                shape = 0;
                break;
            default: return false;
        }
        return sameRailWithPower(id, x, y, z, forward, depth, shape)
                || checkBelow && sameRailWithPower(id, x, y - 1, z, forward, depth, shape);
    }

    private boolean sameRailWithPower(int id, int x, int y, int z, boolean forward, int depth, int dir) {
        if (blockAt(x, y, z) != id) return false;
        int state = stateAt(x, y, z);
        int mine = state & 7;
        if (dir == 1 && (mine == 0 || mine == 4 || mine == 5)) return false;
        if (dir == 0 && (mine == 1 || mine == 2 || mine == 3)) return false;
        if ((state & RAIL_POWERED) == 0) return false;
        return hasNeighborSignal(x, y, z) || poweredRailSignal(id, x, y, z, state, forward, depth + 1);
    }

    /** {@code DetectorRailBlock.checkPressed}(수레 검색 상자 0.2 인셋). */
    private void detectorCheckPressed(int state, int x, int y, int z) {
        if (railShouldBeRemoved(state, x, y, z)) return;
        boolean was = (state & RAIL_POWERED) != 0;
        boolean pressed = host.countMinecarts(x + 0.2, y, z + 0.2, x + 0.8, y + 0.8, z + 0.8) > 0;
        if (pressed != was) {
            int next = pressed ? state | RAIL_POWERED : state & ~RAIL_POWERED;
            setBlockAndUpdate(x, y, z, DETECTOR_RAIL, next);
            for (int[] c : railConnections(next, x, y, z)) {
                int cid = blockAt(c[0], c[1], c[2]);
                neighborChangedFull(cid, stateAt(c[0], c[1], c[2]), c[0], c[1], c[2], redstoneVanillaBlock(cid), false);
            }
            updateNeighborsAt(x, y, z, DETECTOR_RAIL);
            updateNeighborsAt(x, y - 1, z, DETECTOR_RAIL);
        }
        if (pressed) scheduleTick(x, y, z, DETECTOR_RAIL, 20);
        updateNeighbourForOutputSignal(x, y, z, DETECTOR_RAIL);
    }

    private static final int[] RAIL_CONNECTION_DY = {0, 1, -1};

    /** {@code RailState.getConnections} 의 실제 레일 위치(같은 높이 → 위 → 아래 순). */
    private List<int[]> railConnections(int state, int x, int y, int z) {
        List<int[]> ends = new ArrayList<>();
        switch (state & 7) {
            case 0: ends.add(new int[] {x, y, z - 1}); ends.add(new int[] {x, y, z + 1}); break;
            case 1: ends.add(new int[] {x - 1, y, z}); ends.add(new int[] {x + 1, y, z}); break;
            case 2: ends.add(new int[] {x - 1, y, z}); ends.add(new int[] {x + 1, y + 1, z}); break;
            case 3: ends.add(new int[] {x - 1, y + 1, z}); ends.add(new int[] {x + 1, y, z}); break;
            case 4: ends.add(new int[] {x, y + 1, z - 1}); ends.add(new int[] {x, y, z + 1}); break;
            case 5: ends.add(new int[] {x, y, z - 1}); ends.add(new int[] {x, y + 1, z + 1}); break;
            default: break;
        }
        List<int[]> out = new ArrayList<>();
        for (int[] end : ends) {
            for (int dy : RAIL_CONNECTION_DY) {
                int rid = blockAt(end[0], end[1] + dy, end[2]);
                if (rid == RAIL || isRedstoneRail(rid)) {
                    out.add(new int[] {end[0], end[1] + dy, end[2]});
                    break;
                }
            }
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 철사 덫·갈고리
    // ════════════════════════════════════════════════════════════════════════════════════

    private boolean tripwireShouldConnectTo(int id, int state, int dir) {
        if (id == TRIPWIRE_HOOK_BLOCK) return hToDir(state & 3) == DIR_OPPOSITE[dir];
        return id == TRIPWIRE;
    }

    private static final int[] TRIPWIRE_SOURCE_DIRS = {DIR_SOUTH, DIR_WEST};

    private void tripwireUpdateSource(int x, int y, int z, int state) {
        for (int dir : TRIPWIRE_SOURCE_DIRS) {
            for (int i = 1; i < 42; i++) {
                int tx = x + DIR_DX[dir] * i;
                int tz = z + DIR_DZ[dir] * i;
                int id = blockAt(tx, y, tz);
                if (id == TRIPWIRE_HOOK_BLOCK) {
                    int hookState = stateAt(tx, y, tz);
                    if (hToDir(hookState & 3) == DIR_OPPOSITE[dir]) {
                        hookCalculateState(tx, y, tz, hookState, false, true, i, state);
                    }
                    break;
                }
                if (id != TRIPWIRE) break;
            }
        }
    }

    private void tripwireCheckPressed(int x, int y, int z) {
        int state = stateAt(x, y, z);
        boolean attached = (state & TRIPWIRE_ATTACHED) != 0;
        // SHAPE_ATTACHED = column(16, 1, 2.5) · SHAPE_NOT_ATTACHED = column(16, 0, 8).
        double minY = attached ? 1.0 / 16 : 0;
        double maxY = attached ? 2.5 / 16 : 0.5;
        boolean shouldBePressed = host.countEntities(x, y + minY, z, x + 1, y + maxY, z + 1, ENTITY_FILTER_EVERYTHING) > 0;
        boolean wasPressed = (state & TRIPWIRE_POWERED) != 0;
        if (shouldBePressed != wasPressed) {
            int next = shouldBePressed ? state | TRIPWIRE_POWERED : state & ~TRIPWIRE_POWERED;
            setBlockAndUpdate(x, y, z, TRIPWIRE, next);
            tripwireUpdateSource(x, y, z, next);
        }
        if (shouldBePressed) scheduleTick(x, y, z, TRIPWIRE, 10);
        else if (wasPressed) scheduleTick(x, y, z, TRIPWIRE, 0);
    }

    private boolean hookCanSurvive(int state, int x, int y, int z) {
        int facing = hToDir(state & 3);
        int back = DIR_OPPOSITE[facing];
        int sx = x + DIR_DX[back];
        int sz = z + DIR_DZ[back];
        return host.isFaceSturdy(blockAt(sx, y, sz), stateAt(sx, y, sz), facing);
    }

    /** {@code TripWireHookBlock.calculateState}. wireSourceState −1 = null. */
    private void hookCalculateState(int x, int y, int z, int state, boolean isBeingDestroyed, boolean canUpdate,
            int wireSource, int wireSourceState) {
        int dir = hToDir(state & 3);
        boolean wasAttached = (state & HOOK_ATTACHED) != 0;
        boolean wasPowered = (state & HOOK_POWERED) != 0;
        boolean attached = !isBeingDestroyed;
        boolean powered = false;
        int receiverPos = 0;
        int[] wireStates = new int[42];
        Arrays.fill(wireStates, -1);
        for (int i = 1; i < 42; i++) {
            int tx = x + DIR_DX[dir] * i;
            int tz = z + DIR_DZ[dir] * i;
            int wid = blockAt(tx, y, tz);
            int wstate = stateAt(tx, y, tz);
            if (wid == TRIPWIRE_HOOK_BLOCK) {
                if (hToDir(wstate & 3) == DIR_OPPOSITE[dir]) receiverPos = i;
                break;
            }
            if (wid != TRIPWIRE && i != wireSource) {
                wireStates[i] = -1;
                attached = false;
            } else {
                if (i == wireSource && wireSourceState >= 0) wstate = wireSourceState;
                boolean armed = (wstate & TRIPWIRE_DISARMED) == 0;
                boolean wirePowered = (wstate & TRIPWIRE_POWERED) != 0;
                powered = powered || (armed && wirePowered);
                wireStates[i] = wstate;
                if (i == wireSource) {
                    scheduleTick(x, y, z, TRIPWIRE_HOOK_BLOCK, 10);
                    attached = attached && armed;
                }
            }
        }
        attached = attached && receiverPos > 1;
        powered = powered && attached;
        int base = (attached ? HOOK_ATTACHED : 0) | (powered ? HOOK_POWERED : 0);
        if (receiverPos > 0) {
            int tx = x + DIR_DX[dir] * receiverPos;
            int tz = z + DIR_DZ[dir] * receiverPos;
            int opposite = DIR_OPPOSITE[dir];
            setBlockAndUpdate(tx, y, tz, TRIPWIRE_HOOK_BLOCK, base | dirToH(opposite));
            hookNotifyNeighbors(tx, y, tz, opposite);
            if (blockAt(x, y, z) != TRIPWIRE_HOOK_BLOCK) {
                // 바닐라는 newState(기본 FACING=NORTH)로 onRemoved 를 부른다.
                hookOnRemoved(base, x, y, z);
                return;
            }
            hookEmitState(tx, y, tz, attached, powered, wasAttached, wasPowered);
        }
        hookEmitState(x, y, z, attached, powered, wasAttached, wasPowered);
        if (!isBeingDestroyed) {
            setBlockAndUpdate(x, y, z, TRIPWIRE_HOOK_BLOCK, base | dirToH(dir));
            if (canUpdate) hookNotifyNeighbors(x, y, z, dir);
        }
        if (wasAttached != attached) {
            for (int i = 1; i < receiverPos; i++) {
                int tx = x + DIR_DX[dir] * i;
                int tz = z + DIR_DZ[dir] * i;
                int data = wireStates[i];
                if (data < 0) continue;
                int cur = blockAt(tx, y, tz);
                if (cur == TRIPWIRE) {
                    setBlockAndUpdate(tx, y, tz, TRIPWIRE, attached ? data | TRIPWIRE_ATTACHED : data & ~TRIPWIRE_ATTACHED);
                } else if (cur == TRIPWIRE_HOOK_BLOCK) {
                    // trySetValue(ATTACHED) 는 갈고리 상태에도 같은 속성이 있어 갈고리를 그 갈고리 값으로 쓴다.
                    setBlockAndUpdate(tx, y, tz, TRIPWIRE, attached ? data | TRIPWIRE_ATTACHED : data & ~TRIPWIRE_ATTACHED);
                }
            }
        }
    }

    private void hookEmitState(int x, int y, int z, boolean attached, boolean powered, boolean wasAttached,
            boolean wasPowered) {
        if (powered && !wasPowered) host.sound("tripwire_click_on", x, y, z, 0.6);
        else if (!powered && wasPowered) host.sound("tripwire_click_off", x, y, z, 0.5);
        else if (attached && !wasAttached) host.sound("tripwire_attach", x, y, z, 0.7);
        else if (!attached && wasAttached) host.sound("tripwire_detach", x, y, z, 1.2);
    }

    private void hookNotifyNeighbors(int x, int y, int z, int dir) {
        int front = DIR_OPPOSITE[dir];
        updateNeighborsAt(x, y, z, TRIPWIRE_HOOK_BLOCK);
        updateNeighborsAt(x + DIR_DX[front], y, z + DIR_DZ[front], TRIPWIRE_HOOK_BLOCK);
    }

    private void hookOnRemoved(int state, int x, int y, int z) {
        boolean attached = (state & HOOK_ATTACHED) != 0;
        boolean powered = (state & HOOK_POWERED) != 0;
        if (attached || powered) hookCalculateState(x, y, z, state, true, false, -1, -1);
        if (powered) hookNotifyNeighbors(x, y, z, hToDir(state & 3));
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 피스톤
    // ════════════════════════════════════════════════════════════════════════════════════

    private boolean pistonNeighborSignal(int x, int y, int z, int pushDir) {
        for (int dir : DIRECTIONS) {
            if (dir != pushDir && hasSignal(x + DIR_DX[dir], y + DIR_DY[dir], z + DIR_DZ[dir], dir)) return true;
        }
        if (hasSignal(x, y, z, DIR_DOWN)) return true;
        for (int dir : DIRECTIONS) {
            if (dir != DIR_DOWN && hasSignal(x + DIR_DX[dir], y + 1 + DIR_DY[dir], z + DIR_DZ[dir], dir)) return true;
        }
        return false;
    }

    private void pistonCheckIfExtend(int id, int state, int x, int y, int z) {
        int dir = f6ToDir(f6Facing(state));
        boolean extend = pistonNeighborSignal(x, y, z, dir);
        boolean extended = (state & PISTON_EXTENDED) != 0;
        if (extend && !extended) {
            if (new PistonStructureResolver(this, x, y, z, dir, true).resolve()) blockEvent(x, y, z, id, 0, dir);
        } else if (!extend && extended) {
            int px = x + 2 * DIR_DX[dir];
            int py = y + 2 * DIR_DY[dir];
            int pz = z + 2 * DIR_DZ[dir];
            int event = 1;
            if (blockAt(px, py, pz) == MOVING_PISTON && f6ToDir(f6Facing(stateAt(px, py, pz))) == dir) {
                RedstoneMovingBlock entity = moving.get(key(px, py, pz));
                if (entity != null && entity.extending
                        && (entity.progressO < 0.5 || gameTime == entity.lastTicked || handlingTick)) {
                    event = 2;
                }
            }
            blockEvent(x, y, z, id, event, dir);
        }
    }

    /** {@code PistonBaseBlock.isPushable}. */
    public boolean isPushable(int id, int state, int x, int y, int z, int dir, boolean allowDestroyable,
            int connectionDir) {
        if (y < MIN_Y || y > MAX_Y || !resident(x, y, z)) return false;
        if (id == AIR) return true;
        if (id == OBSIDIAN || id == CRYING_OBSIDIAN) return false;
        if (dir == DIR_DOWN && y == MIN_Y) return false;
        if (dir == DIR_UP && y == MAX_Y) return false;
        if (id != PISTON && id != STICKY_PISTON) {
            if (host.isUnbreakable(id)) return false;
            switch (pushReactionOf(id, state)) {
                case PUSH_BLOCK: return false;
                case PUSH_DESTROY: return allowDestroyable;
                case PUSH_ONLY: return dir == connectionDir;
                default: break;
            }
        } else if ((state & PISTON_EXTENDED) != 0) {
            return false;
        }
        return !host.hasBlockEntity(id);
    }

    public int pushReactionOf(int id, int state) {
        switch (id) {
            case PISTON_HEAD: case MOVING_PISTON: return PUSH_BLOCK;
            // pistonProperties().pushReaction(IMMOVEABLE); isPushable 이 피스톤을 따로 허용한다.
            case PISTON: case STICKY_PISTON: return PUSH_BLOCK;
            case REDSTONE_WIRE: case REDSTONE_TORCH: case REDSTONE_TORCH_OFF: case REDSTONE_WALL_TORCH:
            case REDSTONE_WALL_TORCH_OFF: case LEVER: case REPEATER: case COMPARATOR: case TRIPWIRE:
            case TRIPWIRE_HOOK_BLOCK:
                return PUSH_DESTROY;
            default:
                if (isButton(id) || isPressurePlate(id)) return PUSH_DESTROY;
                return host.pushReaction(id, state);
        }
    }

    private boolean pistonTriggerEvent(int id, int state, int x, int y, int z, int b0, int b1) {
        int dir = f6ToDir(f6Facing(state));
        boolean sticky = id == STICKY_PISTON;
        int extendedState = state | PISTON_EXTENDED;
        boolean extend = pistonNeighborSignal(x, y, z, dir);
        if (extend && (b0 == 1 || b0 == 2)) {
            setBlock(x, y, z, id, extendedState, UPDATE_CLIENTS);
            return false;
        }
        if (!extend && b0 == 0) return false;
        if (b0 == 0) {
            if (!pistonMoveBlocks(id, x, y, z, dir, true)) return false;
            setBlock(x, y, z, id, extendedState, UPDATE_ALL | UPDATE_MOVE_BY_PISTON);
            host.sound("piston_extend", x, y, z, 0.6);
        } else if (b0 == 1 || b0 == 2) {
            int hx = x + DIR_DX[dir];
            int hy = y + DIR_DY[dir];
            int hz = z + DIR_DZ[dir];
            RedstoneMovingBlock headEntity = moving.get(key(hx, hy, hz));
            if (headEntity != null) finalTick(headEntity);
            int movingState = dirToF6(dir) | (sticky ? PISTON_HEAD_STICKY : 0);
            setBlock(x, y, z, MOVING_PISTON, movingState, UPDATE_INVISIBLE | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            // newMovingBlockEntity(pos, movingPistonState, defaultBlockState().setValue(FACING, from3DDataValue(b1 & 7)), …)
            addMovingBlock(x, y, z, id, dirToF6((b1 & 7) <= 5 ? b1 & 7 : 0), dir, false, true);
            updateNeighborsAt(x, y, z, MOVING_PISTON);
            updateNeighbourShapes(MOVING_PISTON, movingState, x, y, z, UPDATE_CLIENTS, 512);
            if (sticky) {
                int tx = x + 2 * DIR_DX[dir];
                int ty = y + 2 * DIR_DY[dir];
                int tz = z + 2 * DIR_DZ[dir];
                int tid = blockAt(tx, ty, tz);
                int tstate = stateAt(tx, ty, tz);
                boolean pistonPiece = false;
                if (tid == MOVING_PISTON) {
                    RedstoneMovingBlock entity = moving.get(key(tx, ty, tz));
                    if (entity != null && entity.direction == dir && entity.extending) {
                        finalTick(entity);
                        pistonPiece = true;
                    }
                }
                if (!pistonPiece) {
                    if (b0 != 1 || tid == AIR
                            || !isPushable(tid, tstate, tx, ty, tz, DIR_OPPOSITE[dir], false, dir)
                            || pushReactionOf(tid, tstate) != PUSH_NORMAL && tid != PISTON && tid != STICKY_PISTON) {
                        removeBlock(hx, hy, hz, false);
                    } else {
                        pistonMoveBlocks(id, x, y, z, dir, false);
                    }
                }
            } else {
                removeBlock(hx, hy, hz, false);
            }
            host.sound("piston_contract", x, y, z, 0.6);
        }
        return true;
    }

    private boolean pistonMoveBlocks(int id, int x, int y, int z, int dir, boolean extending) {
        int ax = x + DIR_DX[dir];
        int ay = y + DIR_DY[dir];
        int az = z + DIR_DZ[dir];
        if (!extending && blockAt(ax, ay, az) == PISTON_HEAD) {
            setBlock(ax, ay, az, AIR, 0, UPDATE_INVISIBLE | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
        PistonStructureResolver resolver = new PistonStructureResolver(this, x, y, z, dir, extending);
        if (!resolver.resolve()) return false;
        Map<Long, int[]> deleteAfterMove = new LinkedHashMap<>();
        List<int[]> toPush = resolver.toPush;
        List<int[]> toPushShapes = new ArrayList<>();
        for (int[] p : toPush) {
            int bid = blockAt(p[0], p[1], p[2]);
            int bst = stateAt(p[0], p[1], p[2]);
            toPushShapes.add(new int[] {bid, bst});
            deleteAfterMove.put(key(p[0], p[1], p[2]), new int[] {p[0], p[1], p[2], bid, bst});
        }
        List<int[]> toDestroy = resolver.toDestroy;
        List<int[]> toUpdate = new ArrayList<>();
        int pushDir = extending ? dir : DIR_OPPOSITE[dir];
        List<RedstonePistonMove.MovedBlock> animated = new ArrayList<>();
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            int[] p = toDestroy.get(i);
            int bid = blockAt(p[0], p[1], p[2]);
            int bst = stateAt(p[0], p[1], p[2]);
            host.dropResources(p[0], p[1], p[2], bid, bst);
            setBlock(p[0], p[1], p[2], AIR, 0, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE);
            toUpdate.add(new int[] {bid, bst});
        }
        for (int i = toPush.size() - 1; i >= 0; i--) {
            int[] o = toPush.get(i);
            int bid = blockAt(o[0], o[1], o[2]);
            int bst = stateAt(o[0], o[1], o[2]);
            int px = o[0] + DIR_DX[pushDir];
            int py = o[1] + DIR_DY[pushDir];
            int pz = o[2] + DIR_DZ[pushDir];
            deleteAfterMove.remove(key(px, py, pz));
            int movingState = dirToF6(dir);
            setBlock(px, py, pz, MOVING_PISTON, movingState, UPDATE_INVISIBLE | UPDATE_MOVE_BY_PISTON | 256);
            addMovingBlock(px, py, pz, toPushShapes.get(i)[0], toPushShapes.get(i)[1], dir, extending, false);
            animated.add(new RedstonePistonMove.MovedBlock(o[0], o[1], o[2], toPushShapes.get(i)[0], toPushShapes.get(i)[1]));
            toUpdate.add(new int[] {bid, bst});
        }
        if (extending) {
            boolean sticky = id == STICKY_PISTON;
            int headState = dirToF6(dir) | (sticky ? PISTON_HEAD_STICKY : 0);
            int movingState = dirToF6(dir) | (sticky ? PISTON_HEAD_STICKY : 0);
            deleteAfterMove.remove(key(ax, ay, az));
            setBlock(ax, ay, az, MOVING_PISTON, movingState, UPDATE_INVISIBLE | UPDATE_MOVE_BY_PISTON | 256);
            addMovingBlock(ax, ay, az, PISTON_HEAD, headState, dir, true, true);
        }
        // HashMap<BlockPos, BlockState> 순회 순서(BlockPos.hashCode 버킷 순 · 같은 버킷은 삽입 순).
        List<int[]> deleteOrder = hashSetOrder(new ArrayList<>(deleteAfterMove.values()));
        for (int[] p : deleteOrder) {
            setBlock(p[0], p[1], p[2], AIR, 0, UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_MOVE_BY_PISTON);
        }
        for (int[] p : deleteOrder) {
            updateIndirectNeighbourShapes(p[3], p[4], p[0], p[1], p[2], UPDATE_CLIENTS, 512);
            updateNeighbourShapes(AIR, 0, p[0], p[1], p[2], UPDATE_CLIENTS, 512);
        }
        int updateIndex = 0;
        for (int i = toDestroy.size() - 1; i >= 0; i--) {
            int[] u = toUpdate.get(updateIndex++);
            int[] p = toDestroy.get(i);
            affectNeighborsAfterRemoval(u[0], u[1], p[0], p[1], p[2], false);
            updateIndirectNeighbourShapes(u[0], u[1], p[0], p[1], p[2], UPDATE_CLIENTS, 512);
            updateNeighborsAt(p[0], p[1], p[2], redstoneVanillaBlock(u[0]));
        }
        for (int i = toPush.size() - 1; i >= 0; i--) {
            int[] p = toPush.get(i);
            updateNeighborsAt(p[0], p[1], p[2], redstoneVanillaBlock(toUpdate.get(updateIndex++)[0]));
        }
        if (extending) updateNeighborsAt(ax, ay, az, PISTON_HEAD);
        Collections.reverse(animated);
        host.pistonMove(new RedstonePistonMove(x, y, z, dirToF6(dir), extending, id == STICKY_PISTON, pushDir,
                animated, gameTime));
        return true;
    }

    private boolean headFitsBase(int headState, int baseId, int baseState) {
        int base = (headState & PISTON_HEAD_STICKY) != 0 ? STICKY_PISTON : PISTON;
        return baseId == base && (baseState & PISTON_EXTENDED) != 0 && f6Facing(baseState) == f6Facing(headState);
    }

    private boolean headCanSurvive(int state, int x, int y, int z) {
        int back = DIR_OPPOSITE[f6ToDir(f6Facing(state))];
        int bx = x + DIR_DX[back];
        int by = y + DIR_DY[back];
        int bz = z + DIR_DZ[back];
        int bid = blockAt(bx, by, bz);
        int bst = stateAt(bx, by, bz);
        return headFitsBase(state, bid, bst) || bid == MOVING_PISTON && f6Facing(bst) == f6Facing(state);
    }

    private void addMovingBlock(int x, int y, int z, int movedBlock, int movedState, int dir, boolean extending,
            boolean source) {
        moving.put(key(x, y, z), new RedstoneMovingBlock(x, y, z, movedBlock, movedState, dir, extending, source,
                0, 0, gameTime));
        ticksDirty = true;
    }

    /** {@code PistonMovingBlockEntity.finalTick}. */
    private void finalTick(RedstoneMovingBlock entity) {
        if (entity.progressO >= 1) return;
        entity.progress = 1;
        entity.progressO = 1;
        long k = key(entity.x, entity.y, entity.z);
        if (moving.get(k) == entity) moving.remove(k);
        ticksDirty = true;
        int x = entity.x;
        int y = entity.y;
        int z = entity.z;
        if (blockAt(x, y, z) != MOVING_PISTON) return;
        int next;
        if (entity.source) next = 0;
        else next = updateFromNeighbourShapes(entity.movedBlock, entity.movedState, x, y, z);
        setBlockAndUpdate(x, y, z, next >> 8, next & 0xff);
        neighborChanged(x, y, z, redstoneVanillaBlock(next >> 8));
    }

    /** {@code PistonMovingBlockEntity.tick}. */
    private void tickMovingBlock(RedstoneMovingBlock entity) {
        entity.lastTicked = gameTime;
        entity.progressO = entity.progress;
        int x = entity.x;
        int y = entity.y;
        int z = entity.z;
        if (entity.progressO >= 1) {
            moving.remove(key(x, y, z));
            ticksDirty = true;
            if (blockAt(x, y, z) != MOVING_PISTON) return;
            int next = updateFromNeighbourShapes(entity.movedBlock, entity.movedState, x, y, z);
            if ((next >> 8) == AIR) {
                setBlock(x, y, z, entity.movedBlock, entity.movedState, UPDATE_INVISIBLE | UPDATE_KNOWN_SHAPE | UPDATE_MOVE_BY_PISTON | 256);
                updateOrDestroy(entity.movedBlock, entity.movedState, AIR, 0, x, y, z, UPDATE_ALL, 512);
            } else {
                setBlock(x, y, z, next >> 8, next & 0xff, UPDATE_ALL | UPDATE_MOVE_BY_PISTON);
                neighborChanged(x, y, z, redstoneVanillaBlock(next >> 8));
            }
            return;
        }
        double newProgress = entity.progress + 0.5;
        moveCollidedEntities(entity, newProgress);
        entity.progress = Math.min(1, newProgress);
        ticksDirty = true;
    }

    private void moveCollidedEntities(RedstoneMovingBlock entity, double newProgress) {
        int movement = entity.extending ? entity.direction : DIR_OPPOSITE[entity.direction];
        double delta = newProgress - entity.progress;
        double current = entity.extending ? entity.progress - 1 : 1 - entity.progress;
        // 셀 전체 AABB(블록 충돌 형상 근사)를 진행도만큼 옮기고 이동 방향으로 delta 만큼 넓힌다.
        double ox = entity.x + current * DIR_DX[entity.direction];
        double oy = entity.y + current * DIR_DY[entity.direction];
        double oz = entity.z + current * DIR_DZ[entity.direction];
        if (entity.source && entity.movedBlock == PISTON_HEAD && !entity.extending) return;
        double ex = DIR_DX[movement] * delta;
        double ey = DIR_DY[movement] * delta;
        double ez = DIR_DZ[movement] * delta;
        host.pushEntities(
                Math.min(ox, ox + ex), Math.min(oy, oy + ey), Math.min(oz, oz + ez),
                Math.max(ox + 1, ox + 1 + ex), Math.max(oy + 1, oy + 1 + ey), Math.max(oz + 1, oz + 1 + ez),
                movement, delta + 0.01, entity.movedBlock == SLIME_BLOCK);
    }

    /** {@code Block.updateFromNeighbourShapes}. */
    private int updateFromNeighbourShapes(int id, int state, int x, int y, int z) {
        int cur = id << 8 | state;
        for (int dir : UPDATE_SHAPE_ORDER) {
            int nx = x + DIR_DX[dir];
            int ny = y + DIR_DY[dir];
            int nz = z + DIR_DZ[dir];
            cur = updateShape(cur >> 8, cur & 0xff, x, y, z, dir, nx, ny, nz, blockAt(nx, ny, nz), stateAt(nx, ny, nz));
        }
        return cur;
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 설치 상태(getStateForPlacement 의 이웃 의존 부분)
    // ════════════════════════════════════════════════════════════════════════════════════

    /** 권위 설치 경로가 쓰기 직전에 부른다. 방향 비트는 클라 값을 유지하고 이웃 의존 비트만 다시 정한다. */
    public int placementState(int id, int state, int x, int y, int z, long gameTime) {
        this.gameTime = gameTime;
        switch (id) {
            case REDSTONE_WIRE:
                return wireConnectionState(WIRE_SIDES_MASK, x, y, z) & ~WIRE_POWER_MASK;
            case REPEATER: {
                int base = state & 0x0f;
                return repeaterIsLocked(base, x, y, z) ? base | REPEATER_LOCKED : base;
            }
            case COMPARATOR:
                return state & (0x03 | COMPARATOR_SUBTRACT);
            case OBSERVER: case PISTON: case STICKY_PISTON:
                return f6Facing(state);
            case REDSTONE_LAMP: case REDSTONE_LAMP_LIT:
                return 0;
            case TARGET: return 0;
            case DAYLIGHT_DETECTOR: return 0;
            case NOTE_BLOCK: return 0;
            case LEVER: return state & ~ATTACH_POWERED;
            case TRIPWIRE_HOOK_BLOCK: return state & 3;
            case TRIPWIRE: {
                int out = 0;
                for (int dir : HORIZONTALS) {
                    int nx = x + DIR_DX[dir];
                    int nz = z + DIR_DZ[dir];
                    if (tripwireShouldConnectTo(blockAt(nx, y, nz), stateAt(nx, y, nz), dir)) out |= tripwireSideBit(dir);
                }
                return out;
            }
            default:
                if (isButton(id)) return state & ~ATTACH_POWERED;
                if (isPressurePlate(id)) return 0;
                if (Blocks.isDoor(id)) {
                    boolean upper = (state & 0x08) != 0;
                    boolean powered = hasNeighborSignal(x, y, z) || hasNeighborSignal(x, upper ? y - 1 : y + 1, z);
                    return (state & ~(DOOR_POWERED | 0x04)) | (powered ? DOOR_POWERED | 0x04 : 0);
                }
                if (Blocks.isTrapdoor(id)) {
                    return hasNeighborSignal(x, y, z) ? state | 0x08 | TRAPDOOR_POWERED : state & ~(0x08 | TRAPDOOR_POWERED);
                }
                if (Blocks.isFenceGate(id)) {
                    return hasNeighborSignal(x, y, z) ? state | 0x04 | GATE_POWERED : state & ~(0x04 | GATE_POWERED);
                }
                return state;
        }
    }

    /** 램프의 설치 ID(LIT 은 ID 쌍). */
    public int placementBlock(int id, int x, int y, int z) {
        if (id == REDSTONE_LAMP || id == REDSTONE_LAMP_LIT) return hasNeighborSignal(x, y, z) ? REDSTONE_LAMP_LIT : REDSTONE_LAMP;
        return id;
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 영속
    // ════════════════════════════════════════════════════════════════════════════════════

    /** 영속할 상태가 마지막 스냅샷 이후 바뀌었는가. */
    public boolean takeDirty() {
        boolean dirty = ticksDirty;
        ticksDirty = false;
        return dirty;
    }

    public void forMovingCollisionBoxes(int x, int y, int z,
            com.gameexpert.engine.BuildingBlockRules.CollisionBoxVisitor visitor) {
        RedstoneMovingCollision.visit(moving.values(), x, y, z, visitor);
    }

    public RedstoneEngineSnapshot snapshot() {
        List<RedstoneScheduledTick> sorted = new ArrayList<>(ticks.values());
        sorted.sort(TICK_ORDER);
        List<RedstoneMovingBlock> movingBlocks = new ArrayList<>();
        for (RedstoneMovingBlock entity : moving.values()) movingBlocks.add(entity.copy(-1));
        List<int[]> daylightDetectors = new ArrayList<>();
        for (int[] pos : daylight.values()) daylightDetectors.add(pos.clone());
        return new RedstoneEngineSnapshot(sorted, movingBlocks, daylightDetectors, subTickCounter);
    }

    public void restore(RedstoneEngineSnapshot snapshot) {
        ticks.clear();
        moving.clear();
        daylight.clear();
        for (RedstoneScheduledTick tick : snapshot.ticks) {
            ticks.put(new TickKey(tick.x, tick.y, tick.z, tick.block), tick);
        }
        for (RedstoneMovingBlock entity : snapshot.movingBlocks) {
            moving.put(key(entity.x, entity.y, entity.z), entity.copy(-1));
        }
        for (int[] pos : snapshot.daylightDetectors) daylight.put(key(pos[0], pos[1], pos[2]), new int[] {pos[0], pos[1], pos[2]});
        subTickCounter = Math.max(subTickCounter, snapshot.subTickCounter);
        ticksDirty = false;
    }

    /** 청크 활성화 때 햇빛 감지기와 기존 저장본의 해제 예약 틱을 복원한다. */
    public void trackLoaded(int x, int y, int z, int id, int state, long gameTime) {
        if (id == DAYLIGHT_DETECTOR) {
            if (!daylight.containsKey(key(x, y, z))) {
                daylight.put(key(x, y, z), new int[] {x, y, z});
                ticksDirty = true;
            }
            return;
        }
        boolean powered = isButton(id) ? (state & ATTACH_POWERED) != 0
                : isPressurePlate(id) ? (state & PLATE_POWERED) != 0
                : id == OBSERVER ? (state & OBSERVER_POWERED) != 0
                : id == TARGET && (state & 15) != 0;
        if (!powered || hasScheduledTick(x, y, z, id)) return;
        long previous = this.gameTime;
        this.gameTime = gameTime;
        scheduleTick(x, y, z, id, 1);
        this.gameTime = previous;
    }

    /** 그 칸의 움직이는 블록 엔티티, 없으면 null. */
    public RedstoneMovingBlock movingBlockAt(int x, int y, int z) {
        return moving.get(key(x, y, z));
    }

    public int scheduledTickCount() {
        return ticks.size();
    }

    public Stats takeStats() {
        Stats out = stats;
        stats = new Stats();
        return out;
    }

    // ── PistonStructureResolver 가 쓰는 조회 ──
    void beginTickHandling() {
        handlingTick = true;
    }

    void endTickHandling() {
        handlingTick = false;
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // 정적 도우미
    // ════════════════════════════════════════════════════════════════════════════════════

    /** 버튼 형상의 AABB(블록 좌표 0..1). */
    private static double[] attachShapeBox(int state) {
        int face = (state >> 3) & 3;
        int facing = (state >> 1) & 3;
        double p = (state & 1) != 0 ? 1.0 / 16 : 2.0 / 16;
        if (face == 0) return new double[] {5.0 / 16, 0, 6.0 / 16, 11.0 / 16, p, 10.0 / 16};
        if (face == 2) return new double[] {5.0 / 16, 1 - p, 6.0 / 16, 11.0 / 16, 1, 10.0 / 16};
        switch (facing) {
            case 0: return new double[] {5.0 / 16, 6.0 / 16, 1 - p, 11.0 / 16, 10.0 / 16, 1};
            case 2: return new double[] {5.0 / 16, 6.0 / 16, 0, 11.0 / 16, 10.0 / 16, p};
            case 3: return new double[] {1 - p, 6.0 / 16, 5.0 / 16, 1, 10.0 / 16, 11.0 / 16};
            default: return new double[] {0, 6.0 / 16, 5.0 / 16, p, 10.0 / 16, 11.0 / 16};
        }
    }

    /**
     * {@code Sets.newHashSet()}(HashMap) 순회 순서. BlockPos.hashCode = (y + z * 31) * 31 + x
     * (Vec3i.hashCode), HashMap 은 h ^ (h >>> 16) 의 하위 비트 버킷 순이고 같은 버킷은 삽입 순이다.
     * 원소 7개는 기본 용량 16(임계 12)에 들어간다. 각 원소의 앞 세 값이 좌표다.
     */
    public static List<int[]> hashSetOrder(List<int[]> positions) {
        List<List<int[]>> buckets = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) buckets.add(new ArrayList<>());
        Set<Long> seen = new HashSet<>();
        for (int[] pos : positions) {
            long k = key(pos[0], pos[1], pos[2]);
            if (!seen.add(k)) continue;
            int h = (pos[1] + pos[2] * 31) * 31 + pos[0];
            int spread = (h ^ (h >>> 16)) & 15;
            buckets.get(spread).add(pos);
        }
        List<int[]> out = new ArrayList<>(positions.size());
        for (List<int[]> bucket : buckets) out.addAll(bucket);
        return out;
    }

    private static final class SinTable {
        static final float[] SIN = new float[65536];

        static {
            // V8 의 Math.sin 은 fdlibm 이식이라 StrictMath 가 TS 사본과 같은 표를 낸다.
            for (int i = 0; i < 65536; i++) SIN[i] = (float) StrictMath.sin(i / 10430.378350470453);
        }
    }

    /** {@code Mth.cos(double)}: 65536 칸 사인 표({@code SIN[i] = (float) Math.sin(i / 10430.378350470453)}). */
    public static float mthCos(double value) {
        return SinTable.SIN[(int) ((long) (value * 10430.378350470453 + 16384.0) & 65535L)];
    }

    // ════════════════════════════════════════════════════════════════════════════════════
    // PistonStructureResolver
    // ════════════════════════════════════════════════════════════════════════════════════

    static final class PistonStructureResolver {
        final List<int[]> toPush = new ArrayList<>();
        final List<int[]> toDestroy = new ArrayList<>();
        private final RedstoneEngine engine;
        private final int px;
        private final int py;
        private final int pz;
        private final int pistonDirection;
        private final boolean extending;
        private final int startX;
        private final int startY;
        private final int startZ;
        private final int pushDirection;

        PistonStructureResolver(RedstoneEngine engine, int px, int py, int pz, int pistonDirection, boolean extending) {
            this.engine = engine;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.pistonDirection = pistonDirection;
            this.extending = extending;
            if (extending) {
                this.pushDirection = pistonDirection;
                this.startX = px + DIR_DX[pistonDirection];
                this.startY = py + DIR_DY[pistonDirection];
                this.startZ = pz + DIR_DZ[pistonDirection];
            } else {
                this.pushDirection = DIR_OPPOSITE[pistonDirection];
                this.startX = px + 2 * DIR_DX[pistonDirection];
                this.startY = py + 2 * DIR_DY[pistonDirection];
                this.startZ = pz + 2 * DIR_DZ[pistonDirection];
            }
        }

        private int id(int x, int y, int z) {
            return engine.blockAt(x, y, z);
        }

        private int st(int x, int y, int z) {
            return engine.stateAt(x, y, z);
        }

        private boolean isPiston(int x, int y, int z) {
            return x == px && y == py && z == pz;
        }

        private int indexOf(int x, int y, int z) {
            for (int i = 0; i < toPush.size(); i++) {
                int[] p = toPush.get(i);
                if (p[0] == x && p[1] == y && p[2] == z) return i;
            }
            return -1;
        }

        boolean resolve() {
            toPush.clear();
            toDestroy.clear();
            int sx = startX;
            int sy = startY;
            int sz = startZ;
            int nid = id(sx, sy, sz);
            int nst = st(sx, sy, sz);
            if (!engine.isPushable(nid, nst, sx, sy, sz, pushDirection, false, pistonDirection)) {
                if (extending && engine.pushReactionOf(nid, nst) == PUSH_DESTROY) {
                    toDestroy.add(new int[] {sx, sy, sz});
                    return true;
                }
                return false;
            }
            if (!addBlockLine(sx, sy, sz, pushDirection)) return false;
            for (int i = 0; i < toPush.size(); i++) {
                int[] p = toPush.get(i);
                if (isSticky(id(p[0], p[1], p[2])) && !addBranchingBlocks(p[0], p[1], p[2])) return false;
            }
            return true;
        }

        private boolean addBlockLine(int x, int y, int z, int direction) {
            int nid = id(x, y, z);
            int nst = st(x, y, z);
            if (nid == AIR) return true;
            if (!engine.isPushable(nid, nst, x, y, z, pushDirection, false, direction)) return true;
            if (isPiston(x, y, z)) return true;
            if (indexOf(x, y, z) >= 0) return true;
            int blockCount = 1;
            if (blockCount + toPush.size() > MAX_PUSH_DEPTH) return false;
            int back = DIR_OPPOSITE[pushDirection];
            while (isSticky(nid)) {
                int bx = x + DIR_DX[back] * blockCount;
                int by = y + DIR_DY[back] * blockCount;
                int bz = z + DIR_DZ[back] * blockCount;
                int previous = nid;
                nid = id(bx, by, bz);
                nst = st(bx, by, bz);
                if (nid == AIR || !canStickToEachOther(previous, nid)
                        || !engine.isPushable(nid, nst, bx, by, bz, pushDirection, false, back)
                        || isPiston(bx, by, bz)) break;
                if (++blockCount + toPush.size() > MAX_PUSH_DEPTH) return false;
            }
            int blocksAdded = 0;
            for (int i = blockCount - 1; i >= 0; i--) {
                toPush.add(new int[] {x + DIR_DX[back] * i, y + DIR_DY[back] * i, z + DIR_DZ[back] * i});
                blocksAdded++;
            }
            int i = 1;
            for (;;) {
                int fx = x + DIR_DX[pushDirection] * i;
                int fy = y + DIR_DY[pushDirection] * i;
                int fz = z + DIR_DZ[pushDirection] * i;
                int collision = indexOf(fx, fy, fz);
                if (collision > -1) {
                    reorderListAtCollision(blocksAdded, collision);
                    for (int j = 0; j <= collision + blocksAdded; j++) {
                        int[] c = toPush.get(j);
                        if (isSticky(id(c[0], c[1], c[2])) && !addBranchingBlocks(c[0], c[1], c[2])) return false;
                    }
                    return true;
                }
                nid = id(fx, fy, fz);
                nst = st(fx, fy, fz);
                if (nid == AIR) return true;
                if (!engine.isPushable(nid, nst, fx, fy, fz, pushDirection, true, pushDirection)
                        || isPiston(fx, fy, fz)) return false;
                if (engine.pushReactionOf(nid, nst) == PUSH_DESTROY) {
                    toDestroy.add(new int[] {fx, fy, fz});
                    return true;
                }
                if (toPush.size() >= MAX_PUSH_DEPTH) return false;
                toPush.add(new int[] {fx, fy, fz});
                blocksAdded++;
                i++;
            }
        }

        private void reorderListAtCollision(int blocksAdded, int collisionPos) {
            List<int[]> head = new ArrayList<>(toPush.subList(0, collisionPos));
            List<int[]> last = new ArrayList<>(toPush.subList(toPush.size() - blocksAdded, toPush.size()));
            List<int[]> mid = new ArrayList<>(toPush.subList(collisionPos, toPush.size() - blocksAdded));
            toPush.clear();
            toPush.addAll(head);
            toPush.addAll(last);
            toPush.addAll(mid);
        }

        private boolean addBranchingBlocks(int x, int y, int z) {
            int from = id(x, y, z);
            for (int dir : DIRECTIONS) {
                if (dirAxis(dir) == dirAxis(pushDirection)) continue;
                int nx = x + DIR_DX[dir];
                int ny = y + DIR_DY[dir];
                int nz = z + DIR_DZ[dir];
                if (canStickToEachOther(id(nx, ny, nz), from) && !addBlockLine(nx, ny, nz, dir)) return false;
            }
            return true;
        }
    }

    private static boolean isSticky(int id) {
        return id == SLIME_BLOCK || id == HONEY_BLOCK;
    }

    private static boolean canStickToEachOther(int a, int b) {
        if (a == HONEY_BLOCK && b == SLIME_BLOCK) return false;
        if (a == SLIME_BLOCK && b == HONEY_BLOCK) return false;
        return isSticky(a) || isSticky(b);
    }
}
