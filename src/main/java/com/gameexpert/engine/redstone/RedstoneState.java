package com.gameexpert.engine.redstone;

import com.gameexpert.terrain.Blocks;

/**
 * [REDSTONE] 레드스톤 블록의 상태 바이트 어휘와 방향 규약 — {@code client/src/world/redstoneState.ts} 의
 * 문자 그대로 사본. {@code RedstoneStateParityTest} 가 두 원문의 상수 값을 대조한다. 바닐라 근거는 핀
 * 26.3 블록 클래스(RedStoneWireBlock · RepeaterBlock · ComparatorBlock · ObserverBlock · PistonBaseBlock …)다.
 *
 * <p>방향은 두 가지 코드를 쓴다.
 * <ul>
 *   <li>DIR: 바닐라 Direction 3D 값(DOWN 0 · UP 1 · NORTH 2 · SOUTH 3 · WEST 4 · EAST 5). 엔진 순회 순서가
 *       바닐라 {@code Direction.values()} 와 같아야 해서 엔진 내부는 전부 이 코드다.</li>
 *   <li>F6: 상태 바이트의 6방향 facing(N 0 · E 1 · S 2 · W 3 · UP 4 · DOWN 5). 발사기의 기존 규약이며
 *       수평 네 값이 저장소 FACING_MASK(N·E·S·W)와 같아 렌더가 수평 블록과 한 코드를 쓴다.</li>
 * </ul>
 */
public final class RedstoneState {
    private RedstoneState() {
    }

    public static final int DIR_DOWN = 0;
    public static final int DIR_UP = 1;
    public static final int DIR_NORTH = 2;
    public static final int DIR_SOUTH = 3;
    public static final int DIR_WEST = 4;
    public static final int DIR_EAST = 5;
    public static final int[] DIR_DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DIR_DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DIR_DZ = {0, 0, -1, 1, 0, 0};
    public static final int[] DIR_OPPOSITE = {1, 0, 3, 2, 5, 4};
    /** {@code Direction.values()} */
    public static final int[] DIRECTIONS = {0, 1, 2, 3, 4, 5};
    /** {@code Direction.Plane.HORIZONTAL} */
    public static final int[] HORIZONTALS = {DIR_NORTH, DIR_EAST, DIR_SOUTH, DIR_WEST};
    /** {@code Direction.Plane.VERTICAL} */
    public static final int[] VERTICALS = {DIR_UP, DIR_DOWN};
    /** {@code NeighborUpdater.UPDATE_ORDER} */
    public static final int[] UPDATE_ORDER = {DIR_WEST, DIR_EAST, DIR_DOWN, DIR_UP, DIR_NORTH, DIR_SOUTH};
    /** {@code BlockBehaviour.UPDATE_SHAPE_ORDER} */
    public static final int[] UPDATE_SHAPE_ORDER = {DIR_WEST, DIR_EAST, DIR_NORTH, DIR_SOUTH, DIR_DOWN, DIR_UP};

    /** 수평 facing(N0 E1 S2 W3) → DIR. */
    private static final int[] H_TO_DIR = {DIR_NORTH, DIR_EAST, DIR_SOUTH, DIR_WEST};
    /** F6(N0 E1 S2 W3 U4 D5) → DIR. */
    private static final int[] F6_TO_DIR = {DIR_NORTH, DIR_EAST, DIR_SOUTH, DIR_WEST, DIR_UP, DIR_DOWN};
    /** DIR → F6. */
    private static final int[] DIR_TO_F6 = {5, 4, 0, 2, 3, 1};

    public static int hToDir(int h) {
        return H_TO_DIR[h & 3];
    }

    public static int dirToH(int dir) {
        return dir == DIR_NORTH ? 0 : dir == DIR_EAST ? 1 : dir == DIR_SOUTH ? 2 : dir == DIR_WEST ? 3 : 0;
    }

    public static int f6ToDir(int f6) {
        return F6_TO_DIR[f6 <= 5 ? f6 : 0];
    }

    public static int dirToF6(int dir) {
        return DIR_TO_F6[dir];
    }

    /** {@code Direction.getClockWise()} (수평만). */
    public static int dirClockWise(int dir) {
        return dir == DIR_NORTH ? DIR_EAST : dir == DIR_EAST ? DIR_SOUTH : dir == DIR_SOUTH ? DIR_WEST : DIR_NORTH;
    }

    public static int dirCounterClockWise(int dir) {
        return dir == DIR_NORTH ? DIR_WEST : dir == DIR_WEST ? DIR_SOUTH : dir == DIR_SOUTH ? DIR_EAST : DIR_NORTH;
    }

    public static int dirAxis(int dir) {
        return dir <= 1 ? 1 : dir <= 3 ? 2 : 0;
    }

    // ── 상태 비트 ───────────────────────────────────────────────────────────────────────────
    /** 버튼·레버("면 부착"): bit0 POWERED · bits1-2 수평 facing · bits3-4 FACE. */
    public static final int ATTACH_POWERED = 0x01;
    public static final int ATTACH_FACE_FLOOR = 0;
    public static final int ATTACH_FACE_WALL = 1;
    public static final int ATTACH_FACE_CEILING = 2;

    public static int attachFacing(int state) {
        return (state >> 1) & 3;
    }

    public static int attachFace(int state) {
        int f = (state >> 3) & 3;
        return f <= 2 ? f : 0;
    }

    public static int attachState(int face, int facing, boolean powered) {
        return (powered ? ATTACH_POWERED : 0) | (facing & 3) << 1 | (face & 3) << 3;
    }

    /** {@code FaceAttachedHorizontalDirectionalBlock.getConnectedDirection} (벽 반대 방향, DIR). */
    public static int attachConnectedDir(int state) {
        int face = attachFace(state);
        return face == ATTACH_FACE_CEILING ? DIR_DOWN : face == ATTACH_FACE_FLOOR ? DIR_UP : hToDir(attachFacing(state));
    }

    /** 감압판: bit0 POWERED. */
    public static final int PLATE_POWERED = 0x01;

    /** 레드스톤 가루: bits0-3 POWER · bit4 N · bit5 E · bit6 S · bit7 W 연결(SIDE 또는 UP). */
    public static final int WIRE_POWER_MASK = 0x0f;
    public static final int WIRE_NORTH = 0x10;
    public static final int WIRE_EAST = 0x20;
    public static final int WIRE_SOUTH = 0x40;
    public static final int WIRE_WEST = 0x80;
    public static final int WIRE_SIDES_MASK = 0xf0;

    public static int wirePower(int state) {
        return state & WIRE_POWER_MASK;
    }

    /** 수평 DIR → 연결 비트. */
    public static int wireSideBit(int dir) {
        return dir == DIR_NORTH ? WIRE_NORTH : dir == DIR_EAST ? WIRE_EAST : dir == DIR_SOUTH ? WIRE_SOUTH : dir == DIR_WEST ? WIRE_WEST : 0;
    }

    /** 바닐라 {@code RedStoneWireBlock.COLORS}(ARGB.colorFromFloat, float 산술 그대로). */
    public static final int[] WIRE_POWER_COLORS = {
        0x4c0000, 0x700000, 0x7a0000, 0x840000, 0x8e0000, 0x990000, 0xa30000, 0xad0000,
        0xb70000, 0xc10000, 0xcc0000, 0xd60000, 0xe00000, 0xea0600, 0xf41b00, 0xff3200,
    };

    /** 벽 레드스톤 횃불: bits0-1 수평 facing(벽 반대). */
    public static int wallTorchFacing(int state) {
        return state & 3;
    }

    /** 중계기: bits0-1 facing(입력 쪽) · bits2-3 DELAY-1 · bit4 LOCKED · bit5 POWERED. */
    public static final int REPEATER_LOCKED = 0x10;
    public static final int REPEATER_POWERED = 0x20;

    public static int repeaterDelay(int state) {
        return ((state >> 2) & 3) + 1;
    }

    public static int repeaterState(int facing, int delay, boolean locked, boolean powered) {
        return (facing & 3) | ((Math.max(1, Math.min(4, delay)) - 1) << 2)
                | (locked ? REPEATER_LOCKED : 0) | (powered ? REPEATER_POWERED : 0);
    }

    /** 비교기: bits0-1 facing · bit2 SUBTRACT · bit3 POWERED · bits4-7 OutputSignal(블록 엔티티 값). */
    public static final int COMPARATOR_SUBTRACT = 0x04;
    public static final int COMPARATOR_POWERED = 0x08;

    public static int comparatorOutput(int state) {
        return (state >> 4) & 15;
    }

    public static int withComparatorOutput(int state, int output) {
        return (state & 0x0f) | (Math.max(0, Math.min(15, output)) << 4);
    }

    /** 다이오드(중계기·비교기) 공통 facing/powered. */
    public static int diodeFacing(int state) {
        return state & 3;
    }

    public static boolean diodePowered(int id, int state) {
        return id == Blocks.REPEATER ? (state & REPEATER_POWERED) != 0 : (state & COMPARATOR_POWERED) != 0;
    }

    /** 관찰자: bits0-2 F6 facing · bit3 POWERED. */
    public static final int OBSERVER_POWERED = 0x08;
    /** 피스톤: bits0-2 F6 facing · bit3 EXTENDED. */
    public static final int PISTON_EXTENDED = 0x08;
    /** 피스톤 머리: bits0-2 F6 facing · bit3 STICKY · bit4 SHORT. 움직이는 피스톤: bits0-2 · bit3 STICKY. */
    public static final int PISTON_HEAD_STICKY = 0x08;
    public static final int PISTON_HEAD_SHORT = 0x10;
    /** 발사기·공급기: bits0-2 F6 facing · bit3 TRIGGERED. */
    public static final int DISPENSER_TRIGGERED = 0x08;
    /**
     * [CONTAINER] 공급기·제작기 월드 블록 ID 는 컨테이너 레인이 소유한다(조율자 배정 2336·2337). 그 레인의
     * Blocks 상수가 착지하면 이 두 줄은 그 상수의 별칭으로 바뀐다.
     */
    public static final int DROPPER_ID = 2336;
    public static final int CRAFTER_ID = 2337;
    /** 제작기: bits0-3 ORIENTATION(FrontAndTop 0..11) · bit4 TRIGGERED · bit5 CRAFTING. */
    public static final int CRAFTER_TRIGGERED = 0x10;
    public static final int CRAFTER_CRAFTING = 0x20;

    public static int f6Facing(int state) {
        int f = state & 7;
        return f <= 5 ? f : 0;
    }

    /** 과녁: bits0-3 POWER. 햇빛 감지기: bits0-3 POWER · bit4 INVERTED. */
    public static final int DAYLIGHT_INVERTED = 0x10;
    /** 소리 블록: bits0-4 NOTE(0..24) · bit5 POWERED. */
    public static final int NOTE_POWERED = 0x20;

    public static int noteValue(int state) {
        int n = state & 0x1f;
        return n <= 24 ? n : 0;
    }

    /** 철사 덫 갈고리: bits0-1 facing · bit2 ATTACHED · bit3 POWERED. */
    public static final int HOOK_ATTACHED = 0x04;
    public static final int HOOK_POWERED = 0x08;
    /** 철사 덫: bit0 POWERED · bit1 ATTACHED · bit2 DISARMED · bit3 N · bit4 E · bit5 S · bit6 W. */
    public static final int TRIPWIRE_POWERED = 0x01;
    public static final int TRIPWIRE_ATTACHED = 0x02;
    public static final int TRIPWIRE_DISARMED = 0x04;
    public static final int TRIPWIRE_NORTH = 0x08;
    public static final int TRIPWIRE_EAST = 0x10;
    public static final int TRIPWIRE_SOUTH = 0x20;
    public static final int TRIPWIRE_WEST = 0x40;

    public static int tripwireSideBit(int dir) {
        return dir == DIR_NORTH ? TRIPWIRE_NORTH : dir == DIR_EAST ? TRIPWIRE_EAST
                : dir == DIR_SOUTH ? TRIPWIRE_SOUTH : dir == DIR_WEST ? TRIPWIRE_WEST : 0;
    }

    /** 동력·활성화·감지 레일: bits0-2 직선 모양(RAIL_* 0..5) · bit3 POWERED. */
    public static final int RAIL_POWERED = 0x08;

    /** 경사 레일 모양(ASCENDING_* 2..5). */
    public static boolean railIsSlope(int state) {
        int s = state & 7;
        return s >= 2 && s <= 5;
    }

    /** 문·다락문·울타리 문·종·구리 전구의 POWERED 비트(기존 형상 어휘의 빈 비트). */
    public static final int DOOR_POWERED = 0x20;
    public static final int TRAPDOOR_POWERED = 0x10;
    public static final int GATE_POWERED = 0x10;
    public static final int BELL_POWERED = 0x10;
    public static final int BULB_POWERED = 0x01;

    // ── 블록 군 ───────────────────────────────────────────────────────────────────────────
    public static boolean isButton(int id) {
        return id == Blocks.POPLAR_BUTTON || id == Blocks.STONE_BUTTON || id == Blocks.OAK_BUTTON;
    }

    public static boolean isPressurePlate(int id) {
        return id == Blocks.POPLAR_PRESSURE_PLATE || id == Blocks.STONE_PRESSURE_PLATE || id == Blocks.OAK_PRESSURE_PLATE;
    }

    /** 바닐라 BlockSetType 의 버튼 유지 틱: 돌 20 · 나무(참나무·포플러) 30. */
    public static int buttonPressTicks(int id) {
        return id == Blocks.STONE_BUTTON ? 20 : 30;
    }

    /** 감압판 감지 대상: 돌은 생물(MOBS), 나무는 모든 엔티티(EVERYTHING). */
    public static boolean plateDetectsEverything(int id) {
        return id != Blocks.STONE_PRESSURE_PLATE;
    }

    public static boolean isRedstoneTorch(int id) {
        return id == Blocks.REDSTONE_TORCH || id == Blocks.REDSTONE_TORCH_OFF
                || id == Blocks.REDSTONE_WALL_TORCH || id == Blocks.REDSTONE_WALL_TORCH_OFF;
    }

    public static boolean isWallRedstoneTorch(int id) {
        return id == Blocks.REDSTONE_WALL_TORCH || id == Blocks.REDSTONE_WALL_TORCH_OFF;
    }

    public static boolean isLitRedstoneTorch(int id) {
        return id == Blocks.REDSTONE_TORCH || id == Blocks.REDSTONE_WALL_TORCH;
    }

    public static boolean isDiode(int id) {
        return id == Blocks.REPEATER || id == Blocks.COMPARATOR;
    }

    public static boolean isPistonBase(int id) {
        return id == Blocks.PISTON || id == Blocks.STICKY_PISTON;
    }

    public static boolean isRedstoneLamp(int id) {
        return id == Blocks.REDSTONE_LAMP || id == Blocks.REDSTONE_LAMP_LIT;
    }

    public static boolean isPoweredRailFamily(int id) {
        return id == Blocks.POWERED_RAIL || id == Blocks.ACTIVATOR_RAIL;
    }

    public static boolean isRedstoneRail(int id) {
        return id == Blocks.POWERED_RAIL || id == Blocks.ACTIVATOR_RAIL || id == Blocks.DETECTOR_RAIL;
    }

    /** 이 저장소의 레드스톤 부품 월드 블록(엔진이 행동을 소유하는 ID). */
    public static boolean isRedstoneComponent(int id) {
        return id == Blocks.REDSTONE_WIRE || isRedstoneTorch(id) || id == Blocks.REDSTONE_BLOCK || id == Blocks.LEVER
                || isButton(id) || isPressurePlate(id) || id == Blocks.REPEATER || id == Blocks.COMPARATOR
                || id == Blocks.OBSERVER || isPistonBase(id) || id == Blocks.PISTON_HEAD || id == Blocks.MOVING_PISTON
                || isRedstoneLamp(id) || id == Blocks.TARGET || id == Blocks.DAYLIGHT_DETECTOR || id == Blocks.NOTE_BLOCK
                || id == Blocks.TRIPWIRE || id == Blocks.TRIPWIRE_HOOK_BLOCK || id == Blocks.DISPENSER || id == DROPPER_ID
                || id == CRAFTER_ID
                || isRedstoneRail(id);
    }
    /** 이 블록의 상태 어휘. 레드스톤 밖이면 -1이며 기존 형상 정규화를 사용한다. */
    public static int normalizeBlockState(int id, int state) {
        state &= 0xff;
        if (id == Blocks.REDSTONE_WIRE || id == Blocks.COMPARATOR || id == Blocks.TRIPWIRE) return state;
        if (id == Blocks.REPEATER) return state & 0x3f;
        if (id == Blocks.LEVER || isButton(id)) return (state & 7) | attachFace(state) << 3;
        if (isPressurePlate(id)) return state & PLATE_POWERED;
        if (isWallRedstoneTorch(id)) return state & 3;
        if (isRedstoneTorch(id) || id == Blocks.REDSTONE_BLOCK || isRedstoneLamp(id)) return 0;
        if (id == Blocks.OBSERVER || isPistonBase(id) || id == Blocks.MOVING_PISTON)
            return f6Facing(state) | state & 8;
        if (id == Blocks.PISTON_HEAD) return f6Facing(state) | state & 0x18;
        if (id == Blocks.BELL) return state & 0x1f;
        if (id == Blocks.TARGET || id == Blocks.TRIPWIRE_HOOK_BLOCK) return state & 15;
        if (id == Blocks.DAYLIGHT_DETECTOR) return state & 0x1f;
        if (id == Blocks.NOTE_BLOCK) return noteValue(state) | state & NOTE_POWERED;
        if (isRedstoneRail(id)) return Math.min(state & 7, 5) | state & RAIL_POWERED;
        if (Blocks.isDoor(id)) return state & 0x3f;
        if (Blocks.isTrapdoor(id) || Blocks.isFenceGate(id)) return state & 0x1f;
        return -1;
    }

}
