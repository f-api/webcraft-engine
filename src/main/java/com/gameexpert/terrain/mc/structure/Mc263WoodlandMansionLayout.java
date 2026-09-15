package com.gameexpert.terrain.mc.structure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Dormant procedural Woodland Mansion layout grammar for Minecraft Java 26.3-snapshot-7.
 *
 * <p>This is a source-bound translation of the layout-owning classes authenticated by MAN-E0.
 * It selects only identifiers from {@link Mc263WoodlandMansionGrammar}; it contains no template
 * payload, persisted piece catalog, finite probe lookup, or legacy project generator call.</p>
 */
final class Mc263WoodlandMansionLayout {
    static final String MAN_E0_CLASS_CLOSURE_SHA256 =
            "f1c6884fae503081e2c6ad996db46854d84d77f01814a845abcdb00ed0b46268";
    static final String MAN_E0_SOURCE_SET_SHA256 =
            "06d31d82ad8291f75e036075853ac625378a3c846c5224f68ebf961b99e5dc4b";

    private static final int ROOM_1X1 = 0x10000;
    private static final int ROOM_1X2 = 0x20000;
    private static final int ROOM_2X2 = 0x40000;
    private static final int ROOM_ORIGIN_FLAG = 0x100000;
    private static final int ROOM_DOOR_FLAG = 0x200000;
    private static final int ROOM_STAIRS_FLAG = 0x400000;
    private static final int ROOM_CORRIDOR_FLAG = 0x800000;
    private static final int ROOM_TYPE_MASK = 0xF0000;
    private static final int ROOM_ID_MASK = 0xFFFF;

    private Mc263WoodlandMansionLayout() {
    }

    static Result generate(Mc263WoodlandMansionGrammar grammar, long worldSeed,
            int chunkX, int chunkZ, Mc263WoodlandMansionGrammar.Pos origin) {
        Objects.requireNonNull(grammar, "Mansion grammar");
        Objects.requireNonNull(origin, "Mansion origin");
        TraceLegacy48 random = TraceLegacy48.largeFeature(worldSeed, chunkX, chunkZ);
        Mc263WoodlandMansionGrammar.Rotation rotation = rotation(random.nextInt(4));
        return generatePrepared(grammar, worldSeed, chunkX, chunkZ, origin, rotation, random);
    }

    static Result generate(Mc263WoodlandMansionGrammar grammar, long worldSeed,
            int chunkX, int chunkZ, Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation expectedRotation) {
        Objects.requireNonNull(grammar, "Mansion grammar");
        Objects.requireNonNull(origin, "Mansion origin");
        Objects.requireNonNull(expectedRotation, "Mansion rotation");
        TraceLegacy48 random = TraceLegacy48.largeFeature(worldSeed, chunkX, chunkZ);
        Mc263WoodlandMansionGrammar.Rotation rotation = rotation(random.nextInt(4));
        if (rotation != expectedRotation) {
            throw new IllegalArgumentException("Mansion rotation does not match Legacy48 draw");
        }
        return generatePrepared(grammar, worldSeed, chunkX, chunkZ, origin, rotation, random);
    }

    private static Result generatePrepared(Mc263WoodlandMansionGrammar grammar, long worldSeed,
            int chunkX, int chunkZ, Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation rotation, TraceLegacy48 random) {
        MansionGrid grid = new MansionGrid(random);
        MansionPiecePlacer placer = new MansionPiecePlacer(grammar, random);
        ArrayList<LayoutPiece> pieces = new ArrayList<>();
        placer.createMansion(origin, rotation, pieces, grid);
        List<LayoutPiece> frozenPieces = List.copyOf(pieces);
        long state48 = random.state();
        int count = random.count();
        List<Long> continuation = random.continuation(8);
        String drawReceipt = random.drawReceiptSha256();
        return new Result(worldSeed, chunkX, chunkZ, origin, rotation, frozenPieces,
                state48, count, continuation, drawReceipt,
                structuralSha256(frozenPieces, state48, count, continuation, drawReceipt));
    }

    static final class Result {
        private final long worldSeed;
        private final int chunkX;
        private final int chunkZ;
        private final Mc263WoodlandMansionGrammar.Pos origin;
        private final Mc263WoodlandMansionGrammar.Rotation rotation;
        private final List<LayoutPiece> pieces;
        private final long layoutState48;
        private final int layoutWorldgenCount;
        private final List<Long> layoutContinuation;
        private final String layoutDrawReceiptSha256;
        private final String structuralSha256;

        private Result(long worldSeed, int chunkX, int chunkZ,
                Mc263WoodlandMansionGrammar.Pos origin,
                Mc263WoodlandMansionGrammar.Rotation rotation, List<LayoutPiece> pieces,
                long layoutState48, int layoutWorldgenCount, List<Long> layoutContinuation,
                String layoutDrawReceiptSha256, String structuralSha256) {
            this.worldSeed = worldSeed;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.origin = origin;
            this.rotation = rotation;
            this.pieces = List.copyOf(pieces);
            this.layoutState48 = layoutState48;
            this.layoutWorldgenCount = layoutWorldgenCount;
            this.layoutContinuation = List.copyOf(layoutContinuation);
            this.layoutDrawReceiptSha256 = layoutDrawReceiptSha256;
            this.structuralSha256 = structuralSha256;
        }

        long worldSeed() { return worldSeed; }
        int chunkX() { return chunkX; }
        int chunkZ() { return chunkZ; }
        Mc263WoodlandMansionGrammar.Pos origin() { return origin; }
        Mc263WoodlandMansionGrammar.Rotation rotation() { return rotation; }
        List<LayoutPiece> pieces() { return pieces; }
        long layoutState48() { return layoutState48; }
        int layoutWorldgenCount() { return layoutWorldgenCount; }
        List<Long> layoutContinuation() { return layoutContinuation; }
        String layoutDrawReceiptSha256() { return layoutDrawReceiptSha256; }
        String structuralSha256() { return structuralSha256; }
    }

    static final class LayoutPiece {
        private final int ordinal;
        private final String templateId;
        private final Mc263WoodlandMansionGrammar.Pos position;
        private final Mc263WoodlandMansionGrammar.Rotation rotation;
        private final Mc263WoodlandMansionGrammar.Mirror mirror;
        private final Mc263WoodlandMansionGrammar.Box boundingBox;
        private final List<Mc263WoodlandMansionGrammar.Marker> markers;

        private LayoutPiece(int ordinal, String templateId,
                Mc263WoodlandMansionGrammar.Pos position,
                Mc263WoodlandMansionGrammar.Rotation rotation,
                Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Box boundingBox,
                List<Mc263WoodlandMansionGrammar.Marker> markers) {
            this.ordinal = ordinal;
            this.templateId = templateId;
            this.position = position;
            this.rotation = rotation;
            this.mirror = mirror;
            this.boundingBox = boundingBox;
            this.markers = List.copyOf(markers);
        }

        int ordinal() { return ordinal; }
        String templateId() { return templateId; }
        Mc263WoodlandMansionGrammar.Pos position() { return position; }
        Mc263WoodlandMansionGrammar.Rotation rotation() { return rotation; }
        Mc263WoodlandMansionGrammar.Mirror mirror() { return mirror; }
        Mc263WoodlandMansionGrammar.Box boundingBox() { return boundingBox; }
        List<Mc263WoodlandMansionGrammar.Marker> markers() { return markers; }
    }

    private static final class MansionGrid {
        private static final int CLEAR = 0;
        private static final int CORRIDOR = 1;
        private static final int ROOM = 2;
        private static final int START_ROOM = 3;
        private static final int BLOCKED = 5;

        private final TraceLegacy48 random;
        private final SimpleGrid baseGrid;
        private final SimpleGrid thirdFloorGrid;
        private final SimpleGrid[] floorRooms;
        private final int entranceX;
        private final int entranceY;

        private MansionGrid(TraceLegacy48 random) {
            this.random = random;
            entranceX = 7;
            entranceY = 4;
            baseGrid = new SimpleGrid(11, 11, BLOCKED);
            baseGrid.set(entranceX, entranceY, entranceX + 1, entranceY + 1, START_ROOM);
            baseGrid.set(entranceX - 1, entranceY, entranceX - 1, entranceY + 1, ROOM);
            baseGrid.set(entranceX + 2, entranceY - 2, entranceX + 3, entranceY + 3, BLOCKED);
            baseGrid.set(entranceX + 1, entranceY - 2, entranceX + 1, entranceY - 1, CORRIDOR);
            baseGrid.set(entranceX + 1, entranceY + 2, entranceX + 1, entranceY + 3, CORRIDOR);
            baseGrid.set(entranceX - 1, entranceY - 1, CORRIDOR);
            baseGrid.set(entranceX - 1, entranceY + 2, CORRIDOR);
            baseGrid.set(0, 0, 11, 1, BLOCKED);
            baseGrid.set(0, 9, 11, 11, BLOCKED);
            recursiveCorridor(baseGrid, entranceX, entranceY - 2, Direction.WEST, 6);
            recursiveCorridor(baseGrid, entranceX, entranceY + 3, Direction.WEST, 6);
            recursiveCorridor(baseGrid, entranceX - 2, entranceY - 1, Direction.WEST, 3);
            recursiveCorridor(baseGrid, entranceX - 2, entranceY + 2, Direction.WEST, 3);
            while (cleanEdges(baseGrid)) {
                // Source-bound fixed point.
            }
            floorRooms = new SimpleGrid[] {
                    new SimpleGrid(11, 11, BLOCKED),
                    new SimpleGrid(11, 11, BLOCKED),
                    new SimpleGrid(11, 11, BLOCKED)
            };
            identifyRooms(baseGrid, floorRooms[0]);
            identifyRooms(baseGrid, floorRooms[1]);
            floorRooms[0].set(entranceX + 1, entranceY, entranceX + 1, entranceY + 1,
                    ROOM_CORRIDOR_FLAG);
            floorRooms[1].set(entranceX + 1, entranceY, entranceX + 1, entranceY + 1,
                    ROOM_CORRIDOR_FLAG);
            thirdFloorGrid = new SimpleGrid(baseGrid.width, baseGrid.height, BLOCKED);
            setupThirdFloor();
            identifyRooms(thirdFloorGrid, floorRooms[2]);
        }

        private static boolean isHouse(SimpleGrid grid, int x, int y) {
            int value = grid.get(x, y);
            return value == CORRIDOR || value == ROOM || value == START_ROOM || value == 4;
        }

        private boolean isRoomId(SimpleGrid grid, int x, int y, int floor, int roomId) {
            return (floorRooms[floor].get(x, y) & ROOM_ID_MASK) == roomId;
        }

        private Direction get1x2RoomDirection(SimpleGrid grid, int x, int y,
                int floorNum, int roomId) {
            for (Direction direction : Direction.HORIZONTAL) {
                if (isRoomId(grid, x + direction.stepX, y + direction.stepZ,
                        floorNum, roomId)) {
                    return direction;
                }
            }
            return null;
        }

        private void recursiveCorridor(SimpleGrid grid, int x, int y,
                Direction heading, int depth) {
            if (depth <= 0) return;
            grid.set(x, y, CORRIDOR);
            grid.setIf(x + heading.stepX, y + heading.stepZ, CLEAR, CORRIDOR);
            for (int attempts = 0; attempts < 8; attempts++) {
                Direction nextDir = Direction.from2DDataValue(random.nextInt(4));
                if (nextDir == heading.opposite()
                        || nextDir == Direction.EAST && random.nextBoolean()) {
                    continue;
                }
                int nx = x + heading.stepX;
                int ny = y + heading.stepZ;
                if (grid.get(nx + nextDir.stepX, ny + nextDir.stepZ) != CLEAR
                        || grid.get(nx + nextDir.stepX * 2, ny + nextDir.stepZ * 2) != CLEAR) {
                    continue;
                }
                recursiveCorridor(grid, x + heading.stepX + nextDir.stepX,
                        y + heading.stepZ + nextDir.stepZ, nextDir, depth - 1);
                break;
            }
            Direction cw = heading.clockwise();
            Direction ccw = heading.counterClockwise();
            grid.setIf(x + cw.stepX, y + cw.stepZ, CLEAR, ROOM);
            grid.setIf(x + ccw.stepX, y + ccw.stepZ, CLEAR, ROOM);
            grid.setIf(x + heading.stepX + cw.stepX, y + heading.stepZ + cw.stepZ, CLEAR, ROOM);
            grid.setIf(x + heading.stepX + ccw.stepX, y + heading.stepZ + ccw.stepZ, CLEAR, ROOM);
            grid.setIf(x + heading.stepX * 2, y + heading.stepZ * 2, CLEAR, ROOM);
            grid.setIf(x + cw.stepX * 2, y + cw.stepZ * 2, CLEAR, ROOM);
            grid.setIf(x + ccw.stepX * 2, y + ccw.stepZ * 2, CLEAR, ROOM);
        }

        private boolean cleanEdges(SimpleGrid grid) {
            boolean touched = false;
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    if (grid.get(x, y) != CLEAR) continue;
                    int directNeighbors = 0;
                    directNeighbors += isHouse(grid, x + 1, y) ? 1 : 0;
                    directNeighbors += isHouse(grid, x - 1, y) ? 1 : 0;
                    directNeighbors += isHouse(grid, x, y + 1) ? 1 : 0;
                    directNeighbors += isHouse(grid, x, y - 1) ? 1 : 0;
                    if (directNeighbors >= 3) {
                        grid.set(x, y, ROOM);
                        touched = true;
                        continue;
                    }
                    if (directNeighbors != 2) continue;
                    int diagonalNeighbors = 0;
                    diagonalNeighbors += isHouse(grid, x + 1, y + 1) ? 1 : 0;
                    diagonalNeighbors += isHouse(grid, x - 1, y + 1) ? 1 : 0;
                    diagonalNeighbors += isHouse(grid, x + 1, y - 1) ? 1 : 0;
                    diagonalNeighbors += isHouse(grid, x - 1, y - 1) ? 1 : 0;
                    if (diagonalNeighbors <= 1) {
                        grid.set(x, y, ROOM);
                        touched = true;
                    }
                }
            }
            return touched;
        }

        private void setupThirdFloor() {
            ArrayList<GridPos> potentialRooms = new ArrayList<>();
            SimpleGrid floor = floorRooms[1];
            for (int y = 0; y < thirdFloorGrid.height; y++) {
                for (int x = 0; x < thirdFloorGrid.width; x++) {
                    int roomData = floor.get(x, y);
                    int roomType = roomData & ROOM_TYPE_MASK;
                    if (roomType == ROOM_1X2 && (roomData & ROOM_DOOR_FLAG) == ROOM_DOOR_FLAG) {
                        potentialRooms.add(new GridPos(x, y));
                    }
                }
            }
            if (potentialRooms.isEmpty()) {
                thirdFloorGrid.set(0, 0, thirdFloorGrid.width, thirdFloorGrid.height, BLOCKED);
                return;
            }
            GridPos roomPos = potentialRooms.get(random.nextInt(potentialRooms.size()));
            int roomData = floor.get(roomPos.x, roomPos.y);
            floor.set(roomPos.x, roomPos.y, roomData | ROOM_STAIRS_FLAG);
            Direction roomDir = get1x2RoomDirection(baseGrid, roomPos.x, roomPos.y,
                    1, roomData & ROOM_ID_MASK);
            if (roomDir == null) throw new IllegalStateException("Mansion 1x2 room lost its peer");
            int roomEndX = roomPos.x + roomDir.stepX;
            int roomEndY = roomPos.y + roomDir.stepZ;
            for (int y = 0; y < thirdFloorGrid.height; y++) {
                for (int x = 0; x < thirdFloorGrid.width; x++) {
                    if (!isHouse(baseGrid, x, y)) {
                        thirdFloorGrid.set(x, y, BLOCKED);
                    } else if (x == roomPos.x && y == roomPos.y) {
                        thirdFloorGrid.set(x, y, START_ROOM);
                    } else if (x == roomEndX && y == roomEndY) {
                        thirdFloorGrid.set(x, y, START_ROOM);
                        floorRooms[2].set(x, y, ROOM_CORRIDOR_FLAG);
                    }
                }
            }
            ArrayList<Direction> potentialCorridors = new ArrayList<>();
            for (Direction direction : Direction.HORIZONTAL) {
                if (thirdFloorGrid.get(roomEndX + direction.stepX,
                        roomEndY + direction.stepZ) == CLEAR) {
                    potentialCorridors.add(direction);
                }
            }
            if (potentialCorridors.isEmpty()) {
                thirdFloorGrid.set(0, 0, thirdFloorGrid.width, thirdFloorGrid.height, BLOCKED);
                floor.set(roomPos.x, roomPos.y, roomData);
                return;
            }
            Direction corridorDir = potentialCorridors.get(random.nextInt(potentialCorridors.size()));
            recursiveCorridor(thirdFloorGrid, roomEndX + corridorDir.stepX,
                    roomEndY + corridorDir.stepZ, corridorDir, 4);
            while (cleanEdges(thirdFloorGrid)) {
                // Source-bound fixed point.
            }
        }

        private void identifyRooms(SimpleGrid fromGrid, SimpleGrid roomGrid) {
            ArrayList<GridPos> roomPositions = new ArrayList<>();
            for (int y = 0; y < fromGrid.height; y++) {
                for (int x = 0; x < fromGrid.width; x++) {
                    if (fromGrid.get(x, y) == ROOM) roomPositions.add(new GridPos(x, y));
                }
            }
            shuffle(roomPositions, random);
            int roomId = 10;
            for (GridPos pos : roomPositions) {
                int x = pos.x;
                int y = pos.y;
                if (roomGrid.get(x, y) != CLEAR) continue;
                int x0 = x;
                int x1 = x;
                int y0 = y;
                int y1 = y;
                int type = ROOM_1X1;
                if (roomGrid.get(x + 1, y) == CLEAR && roomGrid.get(x, y + 1) == CLEAR
                        && roomGrid.get(x + 1, y + 1) == CLEAR
                        && fromGrid.get(x + 1, y) == ROOM && fromGrid.get(x, y + 1) == ROOM
                        && fromGrid.get(x + 1, y + 1) == ROOM) {
                    x1++;
                    y1++;
                    type = ROOM_2X2;
                } else if (roomGrid.get(x - 1, y) == CLEAR
                        && roomGrid.get(x, y + 1) == CLEAR
                        && roomGrid.get(x - 1, y + 1) == CLEAR
                        && fromGrid.get(x - 1, y) == ROOM && fromGrid.get(x, y + 1) == ROOM
                        && fromGrid.get(x - 1, y + 1) == ROOM) {
                    x0--;
                    y1++;
                    type = ROOM_2X2;
                } else if (roomGrid.get(x - 1, y) == CLEAR
                        && roomGrid.get(x, y - 1) == CLEAR
                        && roomGrid.get(x - 1, y - 1) == CLEAR
                        && fromGrid.get(x - 1, y) == ROOM && fromGrid.get(x, y - 1) == ROOM
                        && fromGrid.get(x - 1, y - 1) == ROOM) {
                    x0--;
                    y0--;
                    type = ROOM_2X2;
                } else if (roomGrid.get(x + 1, y) == CLEAR && fromGrid.get(x + 1, y) == ROOM) {
                    x1++;
                    type = ROOM_1X2;
                } else if (roomGrid.get(x, y + 1) == CLEAR && fromGrid.get(x, y + 1) == ROOM) {
                    y1++;
                    type = ROOM_1X2;
                } else if (roomGrid.get(x - 1, y) == CLEAR && fromGrid.get(x - 1, y) == ROOM) {
                    x0--;
                    type = ROOM_1X2;
                } else if (roomGrid.get(x, y - 1) == CLEAR && fromGrid.get(x, y - 1) == ROOM) {
                    y0--;
                    type = ROOM_1X2;
                }
                int doorX = random.nextBoolean() ? x0 : x1;
                int doorY = random.nextBoolean() ? y0 : y1;
                int doorFlag = ROOM_DOOR_FLAG;
                if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                    doorX = doorX == x0 ? x1 : x0;
                    doorY = doorY == y0 ? y1 : y0;
                    if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                        doorY = doorY == y0 ? y1 : y0;
                        if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                            doorX = doorX == x0 ? x1 : x0;
                            doorY = doorY == y0 ? y1 : y0;
                            if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                                doorFlag = 0;
                                doorX = x0;
                                doorY = y0;
                            }
                        }
                    }
                }
                for (int ry = y0; ry <= y1; ry++) {
                    for (int rx = x0; rx <= x1; rx++) {
                        roomGrid.set(rx, ry, type | roomId
                                | (rx == doorX && ry == doorY ? ROOM_ORIGIN_FLAG | doorFlag : 0));
                    }
                }
                roomId++;
            }
        }
    }

    private static final class MansionPiecePlacer {
        private final Mc263WoodlandMansionGrammar grammar;
        private final TraceLegacy48 random;
        private int startX;
        private int startY;

        private MansionPiecePlacer(Mc263WoodlandMansionGrammar grammar, TraceLegacy48 random) {
            this.grammar = grammar;
            this.random = random;
        }

        private void createMansion(Mc263WoodlandMansionGrammar.Pos origin,
                Mc263WoodlandMansionGrammar.Rotation rotation, List<LayoutPiece> pieces,
                MansionGrid mansion) {
            PlacementData data = new PlacementData(origin, rotation, "wall_flat");
            entrance(pieces, data);
            PlacementData secondData = new PlacementData(above(data.position, 8),
                    data.rotation, "wall_window");
            SimpleGrid baseGrid = mansion.baseGrid;
            SimpleGrid thirdGrid = mansion.thirdFloorGrid;
            startX = mansion.entranceX + 1;
            startY = mansion.entranceY + 1;
            int endX = mansion.entranceX + 1;
            int endY = mansion.entranceY;
            traverseOuterWalls(pieces, data, baseGrid, Direction.SOUTH,
                    startX, startY, endX, endY);
            traverseOuterWalls(pieces, secondData, baseGrid, Direction.SOUTH,
                    startX, startY, endX, endY);
            PlacementData thirdData = new PlacementData(above(data.position, 19),
                    data.rotation, "wall_window");
            boolean done = false;
            for (int y = 0; y < thirdGrid.height && !done; y++) {
                for (int x = thirdGrid.width - 1; x >= 0 && !done; x--) {
                    if (!MansionGrid.isHouse(thirdGrid, x, y)) continue;
                    thirdData.position = relative(thirdData.position, rotate(rotation, Direction.SOUTH),
                            checkedLinear(8, y - startY, 8));
                    thirdData.position = relative(thirdData.position, rotate(rotation, Direction.EAST),
                            checkedMultiply(x - startX, 8));
                    traverseWallPiece(pieces, thirdData);
                    traverseOuterWalls(pieces, thirdData, thirdGrid, Direction.SOUTH,
                            x, y, x, y);
                    done = true;
                }
            }
            createRoof(pieces, above(origin, 16), rotation, baseGrid, thirdGrid);
            createRoof(pieces, above(origin, 27), rotation, thirdGrid, null);

            FloorRoomCollection[] roomCollections = {
                    new FirstFloorRoomCollection(),
                    new SecondFloorRoomCollection(),
                    new ThirdFloorRoomCollection()
            };
            for (int floorNum = 0; floorNum < 3; floorNum++) {
                Mc263WoodlandMansionGrammar.Pos floorOrigin = above(origin,
                        Math.addExact(Math.multiplyExact(8, floorNum), floorNum == 2 ? 3 : 0));
                SimpleGrid rooms = mansion.floorRooms[floorNum];
                SimpleGrid grid = floorNum == 2 ? thirdGrid : baseGrid;
                String southPiece = floorNum == 0 ? "carpet_south_1" : "carpet_south_2";
                String westPiece = floorNum == 0 ? "carpet_west_1" : "carpet_west_2";
                for (int y = 0; y < grid.height; y++) {
                    for (int x = 0; x < grid.width; x++) {
                        if (grid.get(x, y) != 1) continue;
                        Mc263WoodlandMansionGrammar.Pos pos = relative(floorOrigin,
                                rotate(rotation, Direction.SOUTH), checkedLinear(8, y - startY, 8));
                        pos = relative(pos, rotate(rotation, Direction.EAST),
                                checkedMultiply(x - startX, 8));
                        addPiece(pieces, "corridor_floor", pos, rotation,
                                Mc263WoodlandMansionGrammar.Mirror.NONE);
                        if (grid.get(x, y - 1) == 1
                                || (rooms.get(x, y - 1) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG) {
                            addPiece(pieces, "carpet_north",
                                    above(relative(pos, rotate(rotation, Direction.EAST), 1), 1),
                                    rotation, Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (grid.get(x + 1, y) == 1
                                || (rooms.get(x + 1, y) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG) {
                            Mc263WoodlandMansionGrammar.Pos carpet = relative(pos,
                                    rotate(rotation, Direction.SOUTH), 1);
                            carpet = above(relative(carpet, rotate(rotation, Direction.EAST), 5), 1);
                            addPiece(pieces, "carpet_east", carpet, rotation,
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (grid.get(x, y + 1) == 1
                                || (rooms.get(x, y + 1) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG) {
                            Mc263WoodlandMansionGrammar.Pos carpet = relative(pos,
                                    rotate(rotation, Direction.SOUTH), 5);
                            carpet = relative(carpet, rotate(rotation, Direction.WEST), 1);
                            addPiece(pieces, southPiece, carpet, rotation,
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (grid.get(x - 1, y) == 1
                                || (rooms.get(x - 1, y) & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG) {
                            Mc263WoodlandMansionGrammar.Pos carpet = relative(pos,
                                    rotate(rotation, Direction.WEST), 1);
                            carpet = relative(carpet, rotate(rotation, Direction.NORTH), 1);
                            addPiece(pieces, westPiece, carpet, rotation,
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                    }
                }

                String wallPiece = floorNum == 0 ? "indoors_wall_1" : "indoors_wall_2";
                String doorPiece = floorNum == 0 ? "indoors_door_1" : "indoors_door_2";
                ArrayList<Direction> doorDirs = new ArrayList<>();
                for (int y = 0; y < grid.height; y++) {
                    for (int x = 0; x < grid.width; x++) {
                        boolean thirdFloorStartRoom = floorNum == 2 && grid.get(x, y) == 3;
                        if (grid.get(x, y) != 2 && !thirdFloorStartRoom) continue;
                        int roomData = rooms.get(x, y);
                        int roomType = roomData & ROOM_TYPE_MASK;
                        int roomId = roomData & ROOM_ID_MASK;
                        thirdFloorStartRoom = thirdFloorStartRoom
                                && (roomData & ROOM_CORRIDOR_FLAG) == ROOM_CORRIDOR_FLAG;
                        doorDirs.clear();
                        if ((roomData & ROOM_DOOR_FLAG) == ROOM_DOOR_FLAG) {
                            for (Direction direction : Direction.HORIZONTAL) {
                                if (grid.get(x + direction.stepX, y + direction.stepZ) == 1) {
                                    doorDirs.add(direction);
                                }
                            }
                        }
                        Direction doorDir = null;
                        if (!doorDirs.isEmpty()) {
                            doorDir = doorDirs.get(random.nextInt(doorDirs.size()));
                        } else if ((roomData & ROOM_ORIGIN_FLAG) == ROOM_ORIGIN_FLAG) {
                            doorDir = Direction.UP;
                        }
                        Mc263WoodlandMansionGrammar.Pos roomPos = relative(floorOrigin,
                                rotate(rotation, Direction.SOUTH), checkedLinear(8, y - startY, 8));
                        roomPos = relative(roomPos, rotate(rotation, Direction.EAST),
                                checkedLinear(-1, x - startX, 8));
                        if (MansionGrid.isHouse(grid, x - 1, y)
                                && !mansion.isRoomId(grid, x - 1, y, floorNum, roomId)) {
                            addPiece(pieces, doorDir == Direction.WEST ? doorPiece : wallPiece,
                                    roomPos, rotation, Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (grid.get(x + 1, y) == 1 && !thirdFloorStartRoom) {
                            addPiece(pieces, doorDir == Direction.EAST ? doorPiece : wallPiece,
                                    relative(roomPos, rotate(rotation, Direction.EAST), 8),
                                    rotation, Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (MansionGrid.isHouse(grid, x, y + 1)
                                && !mansion.isRoomId(grid, x, y + 1, floorNum, roomId)) {
                            Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                                    rotate(rotation, Direction.SOUTH), 7);
                            pos = relative(pos, rotate(rotation, Direction.EAST), 7);
                            addPiece(pieces, doorDir == Direction.SOUTH ? doorPiece : wallPiece,
                                    pos, rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (grid.get(x, y - 1) == 1 && !thirdFloorStartRoom) {
                            Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                                    rotate(rotation, Direction.NORTH), 1);
                            pos = relative(pos, rotate(rotation, Direction.EAST), 7);
                            addPiece(pieces, doorDir == Direction.NORTH ? doorPiece : wallPiece,
                                    pos, rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (roomType == ROOM_1X1) {
                            addRoom1x1(pieces, roomPos, rotation, doorDir, roomCollections[floorNum]);
                        } else if (roomType == ROOM_1X2 && doorDir != null) {
                            Direction roomDir = mansion.get1x2RoomDirection(grid, x, y,
                                    floorNum, roomId);
                            boolean stairs = (roomData & ROOM_STAIRS_FLAG) == ROOM_STAIRS_FLAG;
                            addRoom1x2(pieces, roomPos, rotation, roomDir, doorDir,
                                    roomCollections[floorNum], stairs);
                        } else if (roomType == ROOM_2X2 && doorDir != null && doorDir != Direction.UP) {
                            Direction roomDir = doorDir.clockwise();
                            if (!mansion.isRoomId(grid, x + roomDir.stepX, y + roomDir.stepZ,
                                    floorNum, roomId)) {
                                roomDir = roomDir.opposite();
                            }
                            addRoom2x2(pieces, roomPos, rotation, roomDir, doorDir,
                                    roomCollections[floorNum]);
                        } else if (roomType == ROOM_2X2 && doorDir == Direction.UP) {
                            addRoom2x2Secret(pieces, roomPos, rotation, roomCollections[floorNum]);
                        }
                    }
                }
            }
        }

        private void traverseOuterWalls(List<LayoutPiece> pieces, PlacementData data,
                SimpleGrid grid, Direction gridDirection, int startX, int startY,
                int endX, int endY) {
            int gridX = startX;
            int gridY = startY;
            Direction startDirection = gridDirection;
            do {
                if (!MansionGrid.isHouse(grid, gridX + gridDirection.stepX,
                        gridY + gridDirection.stepZ)) {
                    traverseTurn(pieces, data);
                    gridDirection = gridDirection.clockwise();
                    if (gridX == endX && gridY == endY && startDirection == gridDirection) continue;
                    traverseWallPiece(pieces, data);
                } else if (MansionGrid.isHouse(grid, gridX + gridDirection.stepX,
                                gridY + gridDirection.stepZ)
                        && MansionGrid.isHouse(grid,
                                gridX + gridDirection.stepX + gridDirection.counterClockwise().stepX,
                                gridY + gridDirection.stepZ + gridDirection.counterClockwise().stepZ)) {
                    traverseInnerTurn(data);
                    gridX += gridDirection.stepX;
                    gridY += gridDirection.stepZ;
                    gridDirection = gridDirection.counterClockwise();
                } else {
                    gridX += gridDirection.stepX;
                    gridY += gridDirection.stepZ;
                    if (gridX == endX && gridY == endY && startDirection == gridDirection) continue;
                    traverseWallPiece(pieces, data);
                }
            } while (gridX != endX || gridY != endY || startDirection != gridDirection);
        }

        private void createRoof(List<LayoutPiece> pieces, Mc263WoodlandMansionGrammar.Pos roofOrigin,
                Mc263WoodlandMansionGrammar.Rotation rotation, SimpleGrid grid,
                SimpleGrid aboveGrid) {
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    Mc263WoodlandMansionGrammar.Pos position = gridPosition(roofOrigin, rotation, x, y);
                    boolean isAbove = aboveGrid != null && MansionGrid.isHouse(aboveGrid, x, y);
                    if (!MansionGrid.isHouse(grid, x, y) || isAbove) continue;
                    addPiece(pieces, "roof", above(position, 3), rotation,
                            Mc263WoodlandMansionGrammar.Mirror.NONE);
                    if (!MansionGrid.isHouse(grid, x + 1, y)) {
                        addPiece(pieces, "roof_front",
                                relative(position, rotate(rotation, Direction.EAST), 6), rotation,
                                Mc263WoodlandMansionGrammar.Mirror.NONE);
                    }
                    if (!MansionGrid.isHouse(grid, x - 1, y)) {
                        Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                rotate(rotation, Direction.SOUTH), 7);
                        addPiece(pieces, "roof_front", p,
                                rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                                Mc263WoodlandMansionGrammar.Mirror.NONE);
                    }
                    if (!MansionGrid.isHouse(grid, x, y - 1)) {
                        addPiece(pieces, "roof_front",
                                relative(position, rotate(rotation, Direction.WEST), 1),
                                rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                                Mc263WoodlandMansionGrammar.Mirror.NONE);
                    }
                    if (!MansionGrid.isHouse(grid, x, y + 1)) {
                        Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                rotate(rotation, Direction.EAST), 6);
                        p = relative(p, rotate(rotation, Direction.SOUTH), 6);
                        addPiece(pieces, "roof_front", p,
                                rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                Mc263WoodlandMansionGrammar.Mirror.NONE);
                    }
                }
            }
            if (aboveGrid != null) {
                for (int y = 0; y < grid.height; y++) {
                    for (int x = 0; x < grid.width; x++) {
                        Mc263WoodlandMansionGrammar.Pos position = gridPosition(roofOrigin, rotation, x, y);
                        boolean isAbove = MansionGrid.isHouse(aboveGrid, x, y);
                        if (!MansionGrid.isHouse(grid, x, y) || !isAbove) continue;
                        if (!MansionGrid.isHouse(grid, x + 1, y)) {
                            addPiece(pieces, "small_wall",
                                    relative(position, rotate(rotation, Direction.EAST), 7), rotation,
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (!MansionGrid.isHouse(grid, x - 1, y)) {
                            Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                    rotate(rotation, Direction.WEST), 1);
                            p = relative(p, rotate(rotation, Direction.SOUTH), 6);
                            addPiece(pieces, "small_wall", p,
                                    rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (!MansionGrid.isHouse(grid, x, y - 1)) {
                            Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                    rotate(rotation, Direction.NORTH), 1);
                            addPiece(pieces, "small_wall", p,
                                    rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (!MansionGrid.isHouse(grid, x, y + 1)) {
                            Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                    rotate(rotation, Direction.EAST), 6);
                            p = relative(p, rotate(rotation, Direction.SOUTH), 7);
                            addPiece(pieces, "small_wall", p,
                                    rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (!MansionGrid.isHouse(grid, x + 1, y)) {
                            if (!MansionGrid.isHouse(grid, x, y - 1)) {
                                Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                        rotate(rotation, Direction.EAST), 7);
                                p = relative(p, rotate(rotation, Direction.NORTH), 2);
                                addPiece(pieces, "small_wall_corner", p, rotation,
                                        Mc263WoodlandMansionGrammar.Mirror.NONE);
                            }
                            if (!MansionGrid.isHouse(grid, x, y + 1)) {
                                Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                        rotate(rotation, Direction.EAST), 8);
                                p = relative(p, rotate(rotation, Direction.SOUTH), 7);
                                addPiece(pieces, "small_wall_corner", p,
                                        rotate(rotation,
                                                Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                        Mc263WoodlandMansionGrammar.Mirror.NONE);
                            }
                        }
                        if (!MansionGrid.isHouse(grid, x - 1, y)) {
                            if (!MansionGrid.isHouse(grid, x, y - 1)) {
                                Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                        rotate(rotation, Direction.WEST), 2);
                                p = relative(p, rotate(rotation, Direction.NORTH), 1);
                                addPiece(pieces, "small_wall_corner", p,
                                        rotate(rotation,
                                                Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                                        Mc263WoodlandMansionGrammar.Mirror.NONE);
                            }
                            if (!MansionGrid.isHouse(grid, x, y + 1)) {
                                Mc263WoodlandMansionGrammar.Pos p = relative(position,
                                        rotate(rotation, Direction.WEST), 1);
                                p = relative(p, rotate(rotation, Direction.SOUTH), 8);
                                addPiece(pieces, "small_wall_corner", p,
                                        rotate(rotation,
                                                Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                                        Mc263WoodlandMansionGrammar.Mirror.NONE);
                            }
                        }
                    }
                }
            }
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    Mc263WoodlandMansionGrammar.Pos position = gridPosition(roofOrigin, rotation, x, y);
                    boolean isAbove = aboveGrid != null && MansionGrid.isHouse(aboveGrid, x, y);
                    if (!MansionGrid.isHouse(grid, x, y) || isAbove) continue;
                    if (!MansionGrid.isHouse(grid, x + 1, y)) {
                        Mc263WoodlandMansionGrammar.Pos p2 = relative(position,
                                rotate(rotation, Direction.EAST), 6);
                        if (!MansionGrid.isHouse(grid, x, y + 1)) {
                            addPiece(pieces, "roof_corner",
                                    relative(p2, rotate(rotation, Direction.SOUTH), 6), rotation,
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        } else if (MansionGrid.isHouse(grid, x + 1, y + 1)) {
                            addPiece(pieces, "roof_inner_corner",
                                    relative(p2, rotate(rotation, Direction.SOUTH), 5), rotation,
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (!MansionGrid.isHouse(grid, x, y - 1)) {
                            addPiece(pieces, "roof_corner", p2,
                                    rotate(rotation,
                                            Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        } else if (MansionGrid.isHouse(grid, x + 1, y - 1)) {
                            Mc263WoodlandMansionGrammar.Pos p3 = relative(position,
                                    rotate(rotation, Direction.EAST), 9);
                            p3 = relative(p3, rotate(rotation, Direction.NORTH), 2);
                            addPiece(pieces, "roof_inner_corner", p3,
                                    rotate(rotation,
                                            Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                    }
                    if (!MansionGrid.isHouse(grid, x - 1, y)) {
                        Mc263WoodlandMansionGrammar.Pos p2 = position;
                        if (!MansionGrid.isHouse(grid, x, y + 1)) {
                            addPiece(pieces, "roof_corner",
                                    relative(p2, rotate(rotation, Direction.SOUTH), 6),
                                    rotate(rotation,
                                            Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        } else if (MansionGrid.isHouse(grid, x - 1, y + 1)) {
                            Mc263WoodlandMansionGrammar.Pos p3 = relative(p2,
                                    rotate(rotation, Direction.SOUTH), 8);
                            p3 = relative(p3, rotate(rotation, Direction.WEST), 3);
                            addPiece(pieces, "roof_inner_corner", p3,
                                    rotate(rotation,
                                            Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                        if (!MansionGrid.isHouse(grid, x, y - 1)) {
                            addPiece(pieces, "roof_corner", p2,
                                    rotate(rotation,
                                            Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        } else if (MansionGrid.isHouse(grid, x - 1, y - 1)) {
                            addPiece(pieces, "roof_inner_corner",
                                    relative(p2, rotate(rotation, Direction.SOUTH), 1),
                                    rotate(rotation,
                                            Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                                    Mc263WoodlandMansionGrammar.Mirror.NONE);
                        }
                    }
                }
            }
        }

        private Mc263WoodlandMansionGrammar.Pos gridPosition(
                Mc263WoodlandMansionGrammar.Pos origin,
                Mc263WoodlandMansionGrammar.Rotation rotation, int x, int y) {
            Mc263WoodlandMansionGrammar.Pos position = relative(origin,
                    rotate(rotation, Direction.SOUTH), checkedLinear(8, y - startY, 8));
            return relative(position, rotate(rotation, Direction.EAST),
                    checkedMultiply(x - startX, 8));
        }

        private void entrance(List<LayoutPiece> pieces, PlacementData data) {
            Direction west = rotate(data.rotation, Direction.WEST);
            addPiece(pieces, "entrance", relative(data.position, west, 9), data.rotation,
                    Mc263WoodlandMansionGrammar.Mirror.NONE);
            data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), 16);
        }

        private void traverseWallPiece(List<LayoutPiece> pieces, PlacementData data) {
            addPiece(pieces, data.wallType,
                    relative(data.position, rotate(data.rotation, Direction.EAST), 7), data.rotation,
                    Mc263WoodlandMansionGrammar.Mirror.NONE);
            data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), 8);
        }

        private void traverseTurn(List<LayoutPiece> pieces, PlacementData data) {
            data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), -1);
            addPiece(pieces, "wall_corner", data.position, data.rotation,
                    Mc263WoodlandMansionGrammar.Mirror.NONE);
            data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), -7);
            data.position = relative(data.position, rotate(data.rotation, Direction.WEST), -6);
            data.rotation = rotate(data.rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90);
        }

        private void traverseInnerTurn(PlacementData data) {
            data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), 6);
            data.position = relative(data.position, rotate(data.rotation, Direction.EAST), 8);
            data.rotation = rotate(data.rotation,
                    Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90);
        }

        private void addRoom1x1(List<LayoutPiece> pieces,
                Mc263WoodlandMansionGrammar.Pos roomPos,
                Mc263WoodlandMansionGrammar.Rotation rotation, Direction doorDir,
                FloorRoomCollection rooms) {
            Mc263WoodlandMansionGrammar.Rotation pieceRot = Mc263WoodlandMansionGrammar.Rotation.NONE;
            String roomType = rooms.get1x1(random);
            if (doorDir != Direction.EAST) {
                if (doorDir == Direction.NORTH) {
                    pieceRot = rotate(pieceRot,
                            Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90);
                } else if (doorDir == Direction.WEST) {
                    pieceRot = rotate(pieceRot, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180);
                } else if (doorDir == Direction.SOUTH) {
                    pieceRot = rotate(pieceRot, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90);
                } else {
                    roomType = rooms.get1x1Secret(random);
                }
            }
            Mc263WoodlandMansionGrammar.Pos orientation = zeroPositionWithTransform(
                    new Mc263WoodlandMansionGrammar.Pos(1, 0, 0),
                    Mc263WoodlandMansionGrammar.Mirror.NONE, pieceRot, 7, 7);
            pieceRot = rotate(pieceRot, rotation);
            orientation = rotate(orientation, rotation);
            addPiece(pieces, roomType, offset(roomPos, orientation), pieceRot,
                    Mc263WoodlandMansionGrammar.Mirror.NONE);
        }

        private void addRoom1x2(List<LayoutPiece> pieces,
                Mc263WoodlandMansionGrammar.Pos roomPos,
                Mc263WoodlandMansionGrammar.Rotation rotation, Direction roomDir,
                Direction doorDir, FloorRoomCollection rooms, boolean stairs) {
            if (roomDir == null) throw new IllegalStateException("Mansion 1x2 room direction absent");
            if (doorDir == Direction.EAST && roomDir == Direction.SOUTH) {
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs),
                        relative(roomPos, rotate(rotation, Direction.EAST), 1), rotation,
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.EAST && roomDir == Direction.NORTH) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.EAST), 1);
                pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs), pos, rotation,
                        Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT);
            } else if (doorDir == Direction.WEST && roomDir == Direction.NORTH) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.EAST), 7);
                pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs), pos,
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.WEST && roomDir == Direction.SOUTH) {
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs),
                        relative(roomPos, rotate(rotation, Direction.EAST), 7), rotation,
                        Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK);
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.EAST) {
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs),
                        relative(roomPos, rotate(rotation, Direction.EAST), 1),
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT);
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.WEST) {
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs),
                        relative(roomPos, rotate(rotation, Direction.EAST), 7),
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.NORTH && roomDir == Direction.WEST) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.EAST), 7);
                pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs), pos,
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK);
            } else if (doorDir == Direction.NORTH && roomDir == Direction.EAST) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.EAST), 1);
                pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
                addPiece(pieces, rooms.get1x2SideEntrance(random, stairs), pos,
                        rotate(rotation,
                                Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.NORTH) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.EAST), 1);
                pos = relative(pos, rotate(rotation, Direction.NORTH), 8);
                addPiece(pieces, rooms.get1x2FrontEntrance(random, stairs), pos, rotation,
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.NORTH && roomDir == Direction.SOUTH) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.EAST), 7);
                pos = relative(pos, rotate(rotation, Direction.SOUTH), 14);
                addPiece(pieces, rooms.get1x2FrontEntrance(random, stairs), pos,
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.WEST && roomDir == Direction.EAST) {
                addPiece(pieces, rooms.get1x2FrontEntrance(random, stairs),
                        relative(roomPos, rotate(rotation, Direction.EAST), 15),
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.EAST && roomDir == Direction.WEST) {
                Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                        rotate(rotation, Direction.WEST), 7);
                pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
                addPiece(pieces, rooms.get1x2FrontEntrance(random, stairs), pos,
                        rotate(rotation,
                                Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.UP && roomDir == Direction.EAST) {
                addPiece(pieces, rooms.get1x2Secret(random),
                        relative(roomPos, rotate(rotation, Direction.EAST), 15),
                        rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90),
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            } else if (doorDir == Direction.UP && roomDir == Direction.SOUTH) {
                addPiece(pieces, rooms.get1x2Secret(random),
                        relative(roomPos, rotate(rotation, Direction.EAST), 1), rotation,
                        Mc263WoodlandMansionGrammar.Mirror.NONE);
            }
        }

        private void addRoom2x2(List<LayoutPiece> pieces,
                Mc263WoodlandMansionGrammar.Pos roomPos,
                Mc263WoodlandMansionGrammar.Rotation rotation, Direction roomDir,
                Direction doorDir, FloorRoomCollection rooms) {
            int east = 0;
            int south = 0;
            Mc263WoodlandMansionGrammar.Rotation rot = rotation;
            Mc263WoodlandMansionGrammar.Mirror mirror = Mc263WoodlandMansionGrammar.Mirror.NONE;
            if (doorDir == Direction.EAST && roomDir == Direction.SOUTH) {
                east = -7;
            } else if (doorDir == Direction.EAST && roomDir == Direction.NORTH) {
                east = -7;
                south = 6;
                mirror = Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT;
            } else if (doorDir == Direction.NORTH && roomDir == Direction.EAST) {
                east = 1;
                south = 14;
                rot = rotate(rotation,
                        Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90);
            } else if (doorDir == Direction.NORTH && roomDir == Direction.WEST) {
                east = 7;
                south = 14;
                rot = rotate(rotation,
                        Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90);
                mirror = Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT;
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.WEST) {
                east = 7;
                south = -8;
                rot = rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90);
            } else if (doorDir == Direction.SOUTH && roomDir == Direction.EAST) {
                east = 1;
                south = -8;
                rot = rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90);
                mirror = Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT;
            } else if (doorDir == Direction.WEST && roomDir == Direction.NORTH) {
                east = 15;
                south = 6;
                rot = rotate(rotation, Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180);
            } else if (doorDir == Direction.WEST && roomDir == Direction.SOUTH) {
                east = 15;
                mirror = Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK;
            }
            Mc263WoodlandMansionGrammar.Pos pos = relative(roomPos,
                    rotate(rotation, Direction.EAST), east);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), south);
            addPiece(pieces, rooms.get2x2(random), pos, rot, mirror);
        }

        private void addRoom2x2Secret(List<LayoutPiece> pieces,
                Mc263WoodlandMansionGrammar.Pos roomPos,
                Mc263WoodlandMansionGrammar.Rotation rotation, FloorRoomCollection rooms) {
            addPiece(pieces, rooms.get2x2Secret(random),
                    relative(roomPos, rotate(rotation, Direction.EAST), 1), rotation,
                    Mc263WoodlandMansionGrammar.Mirror.NONE);
        }

        private void addPiece(List<LayoutPiece> pieces, String templateName,
                Mc263WoodlandMansionGrammar.Pos position,
                Mc263WoodlandMansionGrammar.Rotation rotation,
                Mc263WoodlandMansionGrammar.Mirror mirror) {
            String templateId = "minecraft:woodland_mansion/" + templateName;
            Mc263WoodlandMansionGrammar.Template template = grammar.requireTemplate(templateId);
            Mc263WoodlandMansionGrammar.Box box = boundingBox(template, position, rotation, mirror);
            ArrayList<Mc263WoodlandMansionGrammar.Marker> markers = new ArrayList<>();
            for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
                if (command instanceof Mc263WoodlandMansionGrammar.Data data
                        && data.semantic() instanceof Mc263WoodlandMansionGrammar.StructureMarker marker) {
                    Mc263WoodlandMansionGrammar.Pos transformed = transformLocal(
                            data.position(), mirror, rotation);
                    markers.add(new Mc263WoodlandMansionGrammar.Marker(markers.size(),
                            marker.metadata(), offset(position, transformed)));
                }
            }
            pieces.add(new LayoutPiece(pieces.size(), templateId, position, rotation, mirror,
                    box, markers));
        }
    }

    private abstract static class FloorRoomCollection {
        abstract String get1x1(TraceLegacy48 random);
        abstract String get1x1Secret(TraceLegacy48 random);
        abstract String get1x2SideEntrance(TraceLegacy48 random, boolean stairs);
        abstract String get1x2FrontEntrance(TraceLegacy48 random, boolean stairs);
        abstract String get1x2Secret(TraceLegacy48 random);
        abstract String get2x2(TraceLegacy48 random);
        abstract String get2x2Secret(TraceLegacy48 random);
    }

    private static class SecondFloorRoomCollection extends FloorRoomCollection {
        @Override String get1x1(TraceLegacy48 random) { return "1x1_b" + (random.nextInt(5) + 1); }
        @Override String get1x1Secret(TraceLegacy48 random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }
        @Override String get1x2SideEntrance(TraceLegacy48 random, boolean stairs) {
            return stairs ? "1x2_c_stairs" : "1x2_c" + (random.nextInt(4) + 1);
        }
        @Override String get1x2FrontEntrance(TraceLegacy48 random, boolean stairs) {
            return stairs ? "1x2_d_stairs" : "1x2_d" + (random.nextInt(5) + 1);
        }
        @Override String get1x2Secret(TraceLegacy48 random) {
            return "1x2_se" + (random.nextInt(1) + 1);
        }
        @Override String get2x2(TraceLegacy48 random) { return "2x2_b" + (random.nextInt(5) + 1); }
        @Override String get2x2Secret(TraceLegacy48 random) { return "2x2_s1"; }
    }

    private static final class ThirdFloorRoomCollection extends SecondFloorRoomCollection {
    }

    private static final class FirstFloorRoomCollection extends FloorRoomCollection {
        @Override String get1x1(TraceLegacy48 random) { return "1x1_a" + (random.nextInt(5) + 1); }
        @Override String get1x1Secret(TraceLegacy48 random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }
        @Override String get1x2SideEntrance(TraceLegacy48 random, boolean stairs) {
            return "1x2_a" + (random.nextInt(9) + 1);
        }
        @Override String get1x2FrontEntrance(TraceLegacy48 random, boolean stairs) {
            return "1x2_b" + (random.nextInt(5) + 1);
        }
        @Override String get1x2Secret(TraceLegacy48 random) {
            return "1x2_s" + (random.nextInt(2) + 1);
        }
        @Override String get2x2(TraceLegacy48 random) { return "2x2_a" + (random.nextInt(4) + 1); }
        @Override String get2x2Secret(TraceLegacy48 random) { return "2x2_s1"; }
    }

    private static final class SimpleGrid {
        private final int[][] grid;
        private final int width;
        private final int height;
        private final int valueIfOutside;

        private SimpleGrid(int width, int height, int valueIfOutside) {
            this.width = width;
            this.height = height;
            this.valueIfOutside = valueIfOutside;
            this.grid = new int[width][height];
        }

        private void set(int x, int y, int value) {
            if (x >= 0 && x < width && y >= 0 && y < height) grid[x][y] = value;
        }

        private void set(int x0, int y0, int x1, int y1, int value) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) set(x, y, value);
            }
        }

        private int get(int x, int y) {
            return x >= 0 && x < width && y >= 0 && y < height ? grid[x][y] : valueIfOutside;
        }

        private void setIf(int x, int y, int ifValue, int value) {
            if (get(x, y) == ifValue) set(x, y, value);
        }

        private boolean edgesTo(int x, int y, int ifValue) {
            return get(x - 1, y) == ifValue || get(x + 1, y) == ifValue
                    || get(x, y + 1) == ifValue || get(x, y - 1) == ifValue;
        }
    }

    private static final class PlacementData {
        private Mc263WoodlandMansionGrammar.Rotation rotation;
        private Mc263WoodlandMansionGrammar.Pos position;
        private final String wallType;

        private PlacementData(Mc263WoodlandMansionGrammar.Pos position,
                Mc263WoodlandMansionGrammar.Rotation rotation, String wallType) {
            this.position = position;
            this.rotation = rotation;
            this.wallType = wallType;
        }
    }

    private static final class GridPos {
        private final int x;
        private final int y;
        private GridPos(int x, int y) { this.x = x; this.y = y; }
    }

    private enum Direction {
        UP(0, 0), NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);

        private static final Direction[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};
        private static final Direction[] BY_2D_DATA = {SOUTH, WEST, NORTH, EAST};
        private final int stepX;
        private final int stepZ;

        Direction(int stepX, int stepZ) {
            this.stepX = stepX;
            this.stepZ = stepZ;
        }

        private static Direction from2DDataValue(int value) { return BY_2D_DATA[value & 3]; }

        private Direction opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
                case UP -> UP;
            };
        }

        private Direction clockwise() {
            return switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
                case UP -> UP;
            };
        }

        private Direction counterClockwise() {
            return switch (this) {
                case NORTH -> WEST;
                case WEST -> SOUTH;
                case SOUTH -> EAST;
                case EAST -> NORTH;
                case UP -> UP;
            };
        }
    }

    private static final class TraceLegacy48 {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private long state;
        private int count;
        private final ArrayList<Draw> draws = new ArrayList<>();

        private TraceLegacy48(long seed) {
            state = (seed ^ MULTIPLIER) & MASK;
        }

        private static TraceLegacy48 largeFeature(long worldSeed, int chunkX, int chunkZ) {
            TraceLegacy48 random = new TraceLegacy48(0L);
            random.setSeed(worldSeed);
            long first = random.nextLong();
            long second = random.nextLong();
            long mixed = (long) chunkX * first ^ (long) chunkZ * second ^ worldSeed;
            random.setSeed(mixed);
            return random;
        }

        private void setSeed(long seed) {
            state = (seed ^ MULTIPLIER) & MASK;
            draws.add(Draw.seed(draws.size(), seed, state));
        }

        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            int value = (int) (state >>> (48 - bits));
            count = Math.incrementExact(count);
            draws.add(Draw.bits(draws.size(), bits, value, state));
            return value;
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive Mansion Legacy48 bound");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }

        private boolean nextBoolean() { return next(1) != 0; }

        private long nextLong() { return ((long) next(32) << 32) + next(32); }

        private long state() { return state; }
        private int count() { return count; }

        private List<Long> continuation(int amount) {
            TraceLegacy48 copy = new TraceLegacy48(0L);
            copy.state = state;
            ArrayList<Long> values = new ArrayList<>(amount);
            for (int i = 0; i < amount; i++) values.add(copy.nextLongUntraced());
            return List.copyOf(values);
        }

        private long nextLongUntraced() {
            return ((long) nextUntraced(32) << 32) + nextUntraced(32);
        }

        private int nextUntraced(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            return (int) (state >>> (48 - bits));
        }

        private String drawReceiptSha256() {
            StringBuilder json = new StringBuilder(draws.size() * 96);
            json.append('[');
            for (int i = 0; i < draws.size(); i++) {
                if (i != 0) json.append(',');
                draws.get(i).appendCanonicalJson(json);
            }
            json.append(']');
            return sha256(json.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class Draw {
        private final int ordinal;
        private final String operation;
        private final String seedArgument;
        private final Integer bitsArgument;
        private final Integer result;
        private final long state48After;

        private Draw(int ordinal, String operation, String seedArgument, Integer bitsArgument,
                Integer result, long state48After) {
            this.ordinal = ordinal;
            this.operation = operation;
            this.seedArgument = seedArgument;
            this.bitsArgument = bitsArgument;
            this.result = result;
            this.state48After = state48After;
        }

        private static Draw seed(int ordinal, long seed, long state) {
            return new Draw(ordinal, "setSeed", Long.toString(seed), null, null, state);
        }

        private static Draw bits(int ordinal, int bits, int result, long state) {
            return new Draw(ordinal, "nextBits", null, bits, result, state);
        }

        private void appendCanonicalJson(StringBuilder out) {
            out.append("{\"argument\":");
            if (seedArgument != null) out.append('\"').append(seedArgument).append('\"');
            else out.append(bitsArgument);
            out.append(",\"operation\":\"").append(operation).append("\",\"ordinal\":")
                    .append(ordinal);
            if (result != null) out.append(",\"result\":").append(result);
            out.append(",\"state48After\":\"").append(state48After).append("\"}");
        }
    }

    private static Mc263WoodlandMansionGrammar.Rotation rotation(int index) {
        return switch (index) {
            case 0 -> Mc263WoodlandMansionGrammar.Rotation.NONE;
            case 1 -> Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90;
            case 2 -> Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180;
            case 3 -> Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("invalid Mansion rotation index");
        };
    }

    private static Mc263WoodlandMansionGrammar.Rotation rotate(
            Mc263WoodlandMansionGrammar.Rotation first,
            Mc263WoodlandMansionGrammar.Rotation second) {
        return rotation((quarterTurns(first) + quarterTurns(second)) & 3);
    }

    private static int quarterTurns(Mc263WoodlandMansionGrammar.Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    private static Direction rotate(Mc263WoodlandMansionGrammar.Rotation rotation,
            Direction direction) {
        if (direction == Direction.UP) return direction;
        Direction result = direction;
        for (int i = 0; i < quarterTurns(rotation); i++) result = result.clockwise();
        return result;
    }

    private static Mc263WoodlandMansionGrammar.Pos rotate(
            Mc263WoodlandMansionGrammar.Pos pos,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        int x = pos.x();
        int z = pos.z();
        return switch (rotation) {
            case NONE -> pos;
            case CLOCKWISE_90 -> new Mc263WoodlandMansionGrammar.Pos(Math.negateExact(z), pos.y(), x);
            case CLOCKWISE_180 -> new Mc263WoodlandMansionGrammar.Pos(
                    Math.negateExact(x), pos.y(), Math.negateExact(z));
            case COUNTERCLOCKWISE_90 -> new Mc263WoodlandMansionGrammar.Pos(z, pos.y(), Math.negateExact(x));
        };
    }

    private static Mc263WoodlandMansionGrammar.Pos transformLocal(
            Mc263WoodlandMansionGrammar.Pos pos, Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        int x = pos.x();
        int z = pos.z();
        if (mirror == Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT) z = Math.negateExact(z);
        else if (mirror == Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK) x = Math.negateExact(x);
        return rotate(new Mc263WoodlandMansionGrammar.Pos(x, pos.y(), z), rotation);
    }

    private static Mc263WoodlandMansionGrammar.Box boundingBox(
            Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Pos position,
            Mc263WoodlandMansionGrammar.Rotation rotation,
            Mc263WoodlandMansionGrammar.Mirror mirror) {
        int maxLocalX = Math.subtractExact(template.size().x(), 1);
        int maxLocalY = Math.subtractExact(template.size().y(), 1);
        int maxLocalZ = Math.subtractExact(template.size().z(), 1);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[] {0, maxLocalX}) {
            for (int z : new int[] {0, maxLocalZ}) {
                Mc263WoodlandMansionGrammar.Pos transformed = transformLocal(
                        new Mc263WoodlandMansionGrammar.Pos(x, 0, z), mirror, rotation);
                int worldX = Math.addExact(position.x(), transformed.x());
                int worldZ = Math.addExact(position.z(), transformed.z());
                minX = Math.min(minX, worldX);
                maxX = Math.max(maxX, worldX);
                minZ = Math.min(minZ, worldZ);
                maxZ = Math.max(maxZ, worldZ);
            }
        }
        return new Mc263WoodlandMansionGrammar.Box(minX, position.y(), minZ, maxX,
                Math.addExact(position.y(), maxLocalY), maxZ);
    }

    private static Mc263WoodlandMansionGrammar.Pos zeroPositionWithTransform(
            Mc263WoodlandMansionGrammar.Pos position, Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation, int sizeX, int sizeZ) {
        int maxX = Math.subtractExact(sizeX, 1);
        int maxZ = Math.subtractExact(sizeZ, 1);
        int mirrorX = mirror == Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK ? maxX : 0;
        int mirrorZ = mirror == Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT ? maxZ : 0;
        return switch (rotation) {
            case NONE -> offset(position,
                    new Mc263WoodlandMansionGrammar.Pos(mirrorX, 0, mirrorZ));
            case COUNTERCLOCKWISE_90 -> offset(position,
                    new Mc263WoodlandMansionGrammar.Pos(mirrorZ, 0,
                            Math.subtractExact(maxX, mirrorX)));
            case CLOCKWISE_90 -> offset(position,
                    new Mc263WoodlandMansionGrammar.Pos(Math.subtractExact(maxZ, mirrorZ),
                            0, mirrorX));
            case CLOCKWISE_180 -> offset(position,
                    new Mc263WoodlandMansionGrammar.Pos(Math.subtractExact(maxX, mirrorX),
                            0, Math.subtractExact(maxZ, mirrorZ)));
        };
    }

    private static Mc263WoodlandMansionGrammar.Pos relative(
            Mc263WoodlandMansionGrammar.Pos pos, Direction direction, int distance) {
        int dx = checkedMultiply(direction.stepX, distance);
        int dz = checkedMultiply(direction.stepZ, distance);
        return new Mc263WoodlandMansionGrammar.Pos(Math.addExact(pos.x(), dx), pos.y(),
                Math.addExact(pos.z(), dz));
    }

    private static Mc263WoodlandMansionGrammar.Pos above(
            Mc263WoodlandMansionGrammar.Pos pos, int distance) {
        return new Mc263WoodlandMansionGrammar.Pos(pos.x(), Math.addExact(pos.y(), distance), pos.z());
    }

    private static Mc263WoodlandMansionGrammar.Pos offset(
            Mc263WoodlandMansionGrammar.Pos first, Mc263WoodlandMansionGrammar.Pos second) {
        return new Mc263WoodlandMansionGrammar.Pos(Math.addExact(first.x(), second.x()),
                Math.addExact(first.y(), second.y()), Math.addExact(first.z(), second.z()));
    }

    private static int checkedMultiply(int a, int b) { return Math.multiplyExact(a, b); }

    private static int checkedLinear(int base, int value, int scale) {
        return Math.addExact(base, Math.multiplyExact(value, scale));
    }

    private static <T> void shuffle(List<T> values, TraceLegacy48 random) {
        for (int remaining = values.size(); remaining > 1; remaining--) {
            Collections.swap(values, remaining - 1, random.nextInt(remaining));
        }
    }

    private static String structuralSha256(List<LayoutPiece> pieces, long state48, int count,
            List<Long> continuation, String drawReceipt) {
        StringBuilder value = new StringBuilder(pieces.size() * 96);
        for (LayoutPiece piece : pieces) {
            value.append(piece.ordinal).append('|').append(piece.templateId).append('|')
                    .append(piece.position.x()).append(',').append(piece.position.y()).append(',')
                    .append(piece.position.z()).append('|').append(piece.rotation).append('|')
                    .append(piece.mirror).append('|').append(piece.boundingBox.minX()).append(',')
                    .append(piece.boundingBox.minY()).append(',').append(piece.boundingBox.minZ()).append(',')
                    .append(piece.boundingBox.maxX()).append(',').append(piece.boundingBox.maxY()).append(',')
                    .append(piece.boundingBox.maxZ());
            for (Mc263WoodlandMansionGrammar.Marker marker : piece.markers) {
                value.append('|').append(marker.ordinal()).append(':').append(marker.metadata()).append('@')
                        .append(marker.position().x()).append(',').append(marker.position().y()).append(',')
                        .append(marker.position().z());
            }
            value.append('\n');
        }
        value.append("rng|").append(state48).append('|').append(count).append('|')
                .append(drawReceipt).append('|').append(continuation).append('\n');
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
