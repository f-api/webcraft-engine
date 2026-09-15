package com.gameexpert.world.dimension.voidend;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [END-CITY] 바닐라 엔드 도시(엔드 배 포함)의 배치·조각·블록·표지. 정적판
 * {@code client/src/world/dimensions/voidEnd/VoidEndCity.ts} 와 줄 단위 사본이다.
 *
 * <p>근거(핀 26.3 client jar, javap · data):
 * <ul>
 * <li>{@code worldgen/structure_set/end_cities.json}: random_spread spacing 20 · separation 11 · salt 10387313 ·
 * triangular. {@code RandomSpreadStructurePlacement.getPotentialStructureChunk}: 영역 = floorDiv(청크, 20),
 * {@code WorldgenRandom(LegacyRandomSource(0)).setLargeFeatureWithSalt(seed, rx, rz, salt)}, 오프셋 =
 * (nextInt(9) + nextInt(9)) / 2 를 x 다음 z.</li>
 * <li>{@code worldgen/structure/end_city.json}: biomes {@code #has_structure/end_city} = end_highlands ·
 * end_midlands, step surface_structures. {@code Structure.isValidBiome}: 시작점(청크 +7, y, +7)의 noise 바이옴.</li>
 * <li>{@code EndCityStructure.findGenerationPoint}: {@code Structure.GenerationContext.makeRandom} =
 * LegacyRandomSource {@code setLargeFeatureSeed(seed, cx, cz)}, {@code Rotation.getRandom}(nextInt(4)),
 * {@code getLowestYIn5by5BoxOffset7Blocks}: (x+7, z+7) 에서 회전별 (±5, ±5) 네 모서리의
 * {@code getFirstOccupiedHeight(WORLD_SURFACE_WG)} 최솟값이 60 미만이면 없음.</li>
 * <li>{@code EndCityPieces}(startHouseTower, recursiveChildren, 네 SectionGenerator, TOWER_BRIDGES ·
 * FAT_TOWER_BRIDGES) 와 {@code EndCityPiece}(overwrite 면 {@code BlockIgnoreProcessor.STRUCTURE_BLOCK},
 * 아니면 {@code STRUCTURE_AND_AIR}, 회전 기준점 0, 거울 없음), {@code StructureTemplate.transform /
 * getBoundingBox / calculateConnectedPosition}, {@code StructurePiece.findCollisionPiece}.</li>
 * <li>{@code ChunkGenerator.applyBiomeDecoration}: 청크 쓰기 구역 = (청크, minY + 1 .. maxY). 조각마다
 * {@code TemplateStructurePiece.postProcess} → {@code StructureTemplate.placeInWorld}(구역 안의 칸만,
 * RandomizableContainer 블록 엔티티마다 {@code LootTableSeed = random.nextLong()}) 뒤 데이터 표지를 (y, x, z)
 * 순서로 {@code handleDataMarker}: Chest → 아래 칸 상자에 {@code end_city_treasure} 와 {@code random.nextLong()},
 * Sentry → 셜커({@code setPos(x+.5, y, z+.5)}), Elytra → 겉날개를 든 아이템 액자(방향 = rotation.rotate(SOUTH)).
 * 난수는 {@code setFeatureSeed(decorationSeed, 구조물 순번, 4)} 의 Xoroshiro {@code WorldgenRandom} 이다.</li>
 * </ul>
 * 구조물 참조 반경은 바닐라 {@code ChunkGenerator.createReferences} 와 같은 8 청크다.
 */
public final class VoidEndCity {
    public static final int SPACING = 20;
    public static final int SEPARATION = 11;
    public static final int SALT = 10387313;
    /** 바닐라 {@code ChunkGenerator.createReferences} 반경(청크). */
    public static final int REFERENCE_RADIUS = 8;
    /**
     * surface_structures 단계 구조물 목록에서 end_city 의 순번({@code setFeatureSeed} 의 index). 서버의 구조물
     * 레지스트리는 데이터팩에서 식별자 순으로 읽히므로, 그 단계 구조물을 이름순으로 셌을 때 abandoned_camp_* 18종 ·
     * bastion_remnant · desert_pyramid 다음인 20 이다. 핀 26.3 전용 서버로 시드 4118 End 를 생성한 월드의 엔드 도시
     * 상자 {@code LootTableSeed} 두 개가 (index 20, step 4) 난수의 3·4 번째 nextLong 과 같음을 대조해 확정했다.
     */
    public static final int STRUCTURE_INDEX = 20;
    public static final int MIN_START_HEIGHT = 60;

    public static final int ROTATION_NONE = 0;
    public static final int ROTATION_CLOCKWISE_90 = 1;
    public static final int ROTATION_CLOCKWISE_180 = 2;
    public static final int ROTATION_COUNTERCLOCKWISE_90 = 3;

    /** 바닐라 {@code Direction} 3D 데이터 값. */
    public static final int DIRECTION_DOWN = 0;
    public static final int DIRECTION_UP = 1;
    public static final int DIRECTION_NORTH = 2;
    public static final int DIRECTION_SOUTH = 3;
    public static final int DIRECTION_WEST = 4;
    public static final int DIRECTION_EAST = 5;

    private VoidEndCity() {
    }

    // ── 조각 ──
    /** 조각 하나. {@code genDepth} 는 바닐라처럼 생성 중에 바뀐다. */
    public static final class Piece {
        public final String template;
        public int x;
        public int y;
        public int z;
        public final int rotation;
        public final boolean overwrite;
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;
        int genDepth;

        Piece(String template, int x, int y, int z, int rotation, boolean overwrite) {
            this.template = template;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
            this.overwrite = overwrite;
            EndCityTemplates.Template shape = EndCityTemplates.byName(template);
            int[] a = transform(0, 0, 0, rotation);
            int[] b = transform(shape.sizeX() - 1, shape.sizeY() - 1, shape.sizeZ() - 1, rotation);
            minX = Math.min(a[0], b[0]) + x;
            minY = Math.min(a[1], b[1]) + y;
            minZ = Math.min(a[2], b[2]) + z;
            maxX = Math.max(a[0], b[0]) + x;
            maxY = Math.max(a[1], b[1]) + y;
            maxZ = Math.max(a[2], b[2]) + z;
        }

        void move(int dx, int dy, int dz) {
            x += dx;
            y += dy;
            z += dz;
            minX += dx;
            maxX += dx;
            minY += dy;
            maxY += dy;
            minZ += dz;
            maxZ += dz;
        }

        boolean intersects(int bMinX, int bMinY, int bMinZ, int bMaxX, int bMaxY, int bMaxZ) {
            return maxX >= bMinX && minX <= bMaxX && maxZ >= bMinZ && minZ <= bMaxZ && maxY >= bMinY && minY <= bMaxY;
        }

        boolean intersects(Piece other) {
            return intersects(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
        }

        @Override
        public String toString() {
            return template + "@" + x + "," + y + "," + z + "/r" + rotation + (overwrite ? "" : "/keep")
                    + " [" + minX + "," + minY + "," + minZ + ".." + maxX + "," + maxY + "," + maxZ + "]";
        }
    }

    /** {@code StructureTemplate.transform}(거울 없음, 기준점 0). */
    public static int[] transform(int x, int y, int z, int rotation) {
        return switch (rotation) {
            case ROTATION_COUNTERCLOCKWISE_90 -> new int[] {z, y, -x};
            case ROTATION_CLOCKWISE_90 -> new int[] {-z, y, x};
            case ROTATION_CLOCKWISE_180 -> new int[] {-x, y, -z};
            default -> new int[] {x, y, z};
        };
    }

    /** {@code Rotation.getRotated}. */
    static int rotated(int rotation, int by) {
        return rotation + by & 3;
    }

    /** {@code Rotation.rotate(Direction)} (수평만 돈다). */
    public static int rotateDirection(int rotation, int direction) {
        if (direction == DIRECTION_DOWN || direction == DIRECTION_UP) return direction;
        // 시계 방향 순서 N, E, S, W.
        int[] clockwise = {DIRECTION_NORTH, DIRECTION_EAST, DIRECTION_SOUTH, DIRECTION_WEST};
        int index = 0;
        while (clockwise[index] != direction) index++;
        return clockwise[index + rotation & 3];
    }

    /** {@code EndCityPieces.addPiece}: 앞 조각의 회전으로 오프셋을 돌려 이어 붙인다. */
    private static Piece addPiece(Piece previous, int ox, int oy, int oz, String template, int rotation,
            boolean overwrite) {
        Piece piece = new Piece(template, previous.x, previous.y, previous.z, rotation, overwrite);
        int[] a = transform(ox, oy, oz, previous.rotation);
        piece.move(a[0], a[1], a[2]);
        return piece;
    }

    private static Piece addHelper(List<Piece> list, Piece piece) {
        list.add(piece);
        return piece;
    }

    private static final int[][] TOWER_BRIDGES = {
        {ROTATION_NONE, 1, -1, 0}, {ROTATION_CLOCKWISE_90, 6, -1, 1},
        {ROTATION_COUNTERCLOCKWISE_90, 0, -1, 5}, {ROTATION_CLOCKWISE_180, 5, -1, 6},
    };
    private static final int[][] FAT_TOWER_BRIDGES = {
        {ROTATION_NONE, 4, -1, 0}, {ROTATION_CLOCKWISE_90, 12, -1, 4},
        {ROTATION_COUNTERCLOCKWISE_90, 0, -1, 8}, {ROTATION_CLOCKWISE_180, 8, -1, 12},
    };

    private interface SectionGenerator {
        boolean generate(Builder builder, int genDepth, Piece parent, int[] offset, List<Piece> out);
    }

    /** 한 시작점의 조각 생성 상태(바닐라 정적 생성기의 shipCreated 를 시작점마다 새로 둔다 — init()). */
    private static final class Builder {
        final VoidEndGenerator.Random48 random;
        boolean shipCreated;

        Builder(VoidEndGenerator.Random48 random) {
            this.random = random;
        }

        final SectionGenerator houseTower = this::houseTower;
        final SectionGenerator tower = this::tower;
        final SectionGenerator towerBridge = this::towerBridge;
        final SectionGenerator fatTower = this::fatTower;

        boolean recursiveChildren(SectionGenerator generator, int genDepth, Piece parent, int[] offset,
                List<Piece> pieces) {
            if (genDepth > 8) return false;
            List<Piece> children = new ArrayList<>();
            if (generator.generate(this, genDepth, parent, offset, children)) {
                boolean collision = false;
                int id = random.nextInt();
                for (Piece child : children) {
                    child.genDepth = id;
                    Piece hit = null;
                    for (Piece existing : pieces) {
                        if (existing.intersects(child)) {
                            hit = existing;
                            break;
                        }
                    }
                    if (hit != null && hit.genDepth != parent.genDepth) {
                        collision = true;
                        break;
                    }
                }
                if (!collision) {
                    pieces.addAll(children);
                    return true;
                }
            }
            return false;
        }

        /** {@code EndCityPieces$1}(HOUSE_TOWER_GENERATOR). */
        private boolean houseTower(Builder self, int genDepth, Piece parent, int[] offset, List<Piece> out) {
            if (genDepth > 8) return false;
            int rotation = parent.rotation;
            Piece piece = addHelper(out, addPiece(parent, offset[0], offset[1], offset[2], "base_floor", rotation, true));
            int kind = random.nextInt(3);
            if (kind == 0) {
                addHelper(out, addPiece(piece, -1, 4, -1, "base_roof", rotation, true));
            } else if (kind == 1) {
                piece = addHelper(out, addPiece(piece, -1, 0, -1, "second_floor_2", rotation, false));
                piece = addHelper(out, addPiece(piece, -1, 8, -1, "second_roof", rotation, false));
                recursiveChildren(tower, genDepth + 1, piece, null, out);
            } else if (kind == 2) {
                piece = addHelper(out, addPiece(piece, -1, 0, -1, "second_floor_2", rotation, false));
                piece = addHelper(out, addPiece(piece, -1, 4, -1, "third_floor_2", rotation, false));
                piece = addHelper(out, addPiece(piece, -1, 8, -1, "third_roof", rotation, true));
                recursiveChildren(tower, genDepth + 1, piece, null, out);
            }
            return true;
        }

        /** {@code EndCityPieces$2}(TOWER_GENERATOR). */
        private boolean tower(Builder self, int genDepth, Piece parent, int[] offset, List<Piece> out) {
            int rotation = parent.rotation;
            int bx = 3 + random.nextInt(2);
            int bz = 3 + random.nextInt(2);
            Piece piece = addHelper(out, addPiece(parent, bx, -3, bz, "tower_base", rotation, true));
            piece = addHelper(out, addPiece(piece, 0, 7, 0, "tower_piece", rotation, true));
            Piece bridgePiece = random.nextInt(3) == 0 ? piece : null;
            int floors = 1 + random.nextInt(3);
            for (int i = 0; i < floors; i++) {
                piece = addHelper(out, addPiece(piece, 0, 4, 0, "tower_piece", rotation, true));
                if (i < floors - 1 && random.nextBoolean()) bridgePiece = piece;
            }
            if (bridgePiece != null) {
                for (int[] bridge : TOWER_BRIDGES) {
                    if (random.nextBoolean()) {
                        Piece end = addHelper(out, addPiece(bridgePiece, bridge[1], bridge[2], bridge[3], "bridge_end",
                                rotated(rotation, bridge[0]), true));
                        recursiveChildren(towerBridge, genDepth + 1, end, null, out);
                    }
                }
                addHelper(out, addPiece(piece, -1, 4, -1, "tower_top", rotation, true));
            } else {
                if (genDepth != 7) return recursiveChildren(fatTower, genDepth + 1, piece, null, out);
                addHelper(out, addPiece(piece, -1, 4, -1, "tower_top", rotation, true));
            }
            return true;
        }

        /** {@code EndCityPieces$3}(TOWER_BRIDGE_GENERATOR). */
        private boolean towerBridge(Builder self, int genDepth, Piece parent, int[] offset, List<Piece> out) {
            int rotation = parent.rotation;
            int length = random.nextInt(4) + 1;
            Piece piece = addHelper(out, addPiece(parent, 0, 0, -4, "bridge_piece", rotation, true));
            piece.genDepth = -1;
            int y = 0;
            for (int i = 0; i < length; i++) {
                if (random.nextBoolean()) {
                    piece = addHelper(out, addPiece(piece, 0, y, -4, "bridge_piece", rotation, true));
                    y = 0;
                } else {
                    if (random.nextBoolean()) {
                        piece = addHelper(out, addPiece(piece, 0, y, -4, "bridge_steep_stairs", rotation, true));
                    } else {
                        piece = addHelper(out, addPiece(piece, 0, y, -8, "bridge_gentle_stairs", rotation, true));
                    }
                    y = 4;
                }
            }
            if (shipCreated || random.nextInt(10 - genDepth) != 0) {
                if (!recursiveChildren(houseTower, genDepth + 1, piece, new int[] {-3, y + 1, -11}, out)) return false;
            } else {
                int sx = -8 + random.nextInt(8);
                int sz = -70 + random.nextInt(10);
                addHelper(out, addPiece(piece, sx, y, sz, "ship", rotation, true));
                shipCreated = true;
            }
            piece = addHelper(out, addPiece(piece, 4, y, 0, "bridge_end", rotated(rotation, ROTATION_CLOCKWISE_180), true));
            piece.genDepth = -1;
            return true;
        }

        /** {@code EndCityPieces$4}(FAT_TOWER_GENERATOR). */
        private boolean fatTower(Builder self, int genDepth, Piece parent, int[] offset, List<Piece> out) {
            int rotation = parent.rotation;
            Piece piece = addHelper(out, addPiece(parent, -3, 4, -3, "fat_tower_base", rotation, true));
            piece = addHelper(out, addPiece(piece, 0, 4, 0, "fat_tower_middle", rotation, true));
            for (int i = 0; i < 2 && random.nextInt(3) != 0; i++) {
                piece = addHelper(out, addPiece(piece, 0, 8, 0, "fat_tower_middle", rotation, true));
                for (int[] bridge : FAT_TOWER_BRIDGES) {
                    if (random.nextBoolean()) {
                        Piece end = addHelper(out, addPiece(piece, bridge[1], bridge[2], bridge[3], "bridge_end",
                                rotated(rotation, bridge[0]), true));
                        recursiveChildren(towerBridge, genDepth + 1, end, null, out);
                    }
                }
            }
            addHelper(out, addPiece(piece, -2, 8, -2, "fat_tower_top", rotation, true));
            return true;
        }
    }

    // ── 시작점 ──
    /** 한 엔드 도시. 조각 목록은 바닐라 추가 순서(= 블록 쓰기 순서)다. */
    public record Start(int chunkX, int chunkZ, int x, int y, int z, int rotation, List<Piece> pieces) {
    }

    /** {@code RandomSpreadStructurePlacement.getPotentialStructureChunk}. */
    public static int[] potentialStartChunk(int seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, SPACING);
        int regionZ = Math.floorDiv(chunkZ, SPACING);
        long salted = (long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + SALT;
        VoidEndGenerator.Random48 random = new VoidEndGenerator.Random48(salted);
        int spread = SPACING - SEPARATION;
        int offsetX = (random.nextInt(spread) + random.nextInt(spread)) / 2;
        int offsetZ = (random.nextInt(spread) + random.nextInt(spread)) / 2;
        return new int[] {regionX * SPACING + offsetX, regionZ * SPACING + offsetZ};
    }

    /** {@code WorldgenRandom.setLargeFeatureSeed} 을 LegacyRandomSource 에 건 난수. */
    static VoidEndGenerator.Random48 largeFeatureRandom(int seed, int chunkX, int chunkZ) {
        VoidEndGenerator.Random48 random = new VoidEndGenerator.Random48(seed);
        long a = random.nextLong();
        long b = random.nextLong();
        return new VoidEndGenerator.Random48((long) chunkX * a ^ (long) chunkZ * b ^ (long) seed);
    }

    private static final Start NONE = new Start(0, 0, 0, 0, 0, 0, List.of());
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, Start>> STARTS = new ConcurrentHashMap<>();

    /** 이 청크가 엔드 도시 시작 청크면 그 도시, 아니면 null(시드별 캐시). */
    public static Start startAt(int seed, int chunkX, int chunkZ) {
        ConcurrentHashMap<Long, Start> starts = STARTS.get(seed);
        if (starts == null) {
            if (STARTS.size() >= 8) STARTS.clear();
            starts = STARTS.computeIfAbsent(seed, ignored -> new ConcurrentHashMap<>());
        }
        long key = (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
        Start cached = starts.get(key);
        if (cached == null) {
            cached = computeStart(seed, chunkX, chunkZ);
            if (cached == null) cached = NONE;
            if (starts.size() > 4096) starts.clear();
            starts.put(key, cached);
        }
        return cached == NONE ? null : cached;
    }

    private static Start computeStart(int seed, int chunkX, int chunkZ) {
        int[] potential = potentialStartChunk(seed, chunkX, chunkZ);
        if (potential[0] != chunkX || potential[1] != chunkZ) return null;
        if (Math.abs((long) chunkX * 16) >= VoidEndGenerator.ISLAND_LIMIT - 512
                || Math.abs((long) chunkZ * 16) >= VoidEndGenerator.ISLAND_LIMIT - 512) {
            return null;
        }
        VoidEndGenerator.Random48 random = largeFeatureRandom(seed, chunkX, chunkZ);
        int rotation = random.nextInt(4);
        int ox = 5;
        int oz = 5;
        if (rotation == ROTATION_CLOCKWISE_90) {
            ox = -5;
        } else if (rotation == ROTATION_CLOCKWISE_180) {
            ox = -5;
            oz = -5;
        } else if (rotation == ROTATION_COUNTERCLOCKWISE_90) {
            oz = -5;
        }
        int x = chunkX * 16 + 7;
        int z = chunkZ * 16 + 7;
        VoidEndTerrain.Sampler sampler = VoidEndTerrain.forSeed(seed).sampler();
        int lowest = Math.min(Math.min(sampler.top(x, z), sampler.top(x, z + oz)),
                Math.min(sampler.top(x + ox, z), sampler.top(x + ox, z + oz)));
        if (lowest < MIN_START_HEIGHT) return null;
        // Structure.isValidBiome: 시작점 quart 의 noise 바이옴 = 시작 청크의 바이옴.
        int biome = VoidEndDecoration.chunkBiome(seed, chunkX, chunkZ);
        if (biome != VoidEndTerrain.BIOME_HIGHLANDS && biome != VoidEndTerrain.BIOME_MIDLANDS) return null;
        List<Piece> pieces = new ArrayList<>();
        Builder builder = new Builder(random);
        Piece piece = addHelper(pieces, new Piece("base_floor", x, lowest, z, rotation, true));
        piece = addHelper(pieces, addPiece(piece, -1, 0, -1, "second_floor_1", rotation, false));
        piece = addHelper(pieces, addPiece(piece, -1, 4, -1, "third_floor_1", rotation, false));
        piece = addHelper(pieces, addPiece(piece, -1, 8, -1, "third_roof", rotation, true));
        builder.recursiveChildren(builder.tower, 1, piece, null, pieces);
        return new Start(chunkX, chunkZ, x, lowest, z, rotation, List.copyOf(pieces));
    }

    /** 이 청크에 조각이 닿는 도시들(참조 반경 8 청크, 시작 청크 순). */
    public static List<Start> startsTouching(int seed, int chunkX, int chunkZ) {
        List<Start> found = new ArrayList<>(1);
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int regionX0 = Math.floorDiv(chunkX - REFERENCE_RADIUS, SPACING);
        int regionX1 = Math.floorDiv(chunkX + REFERENCE_RADIUS, SPACING);
        int regionZ0 = Math.floorDiv(chunkZ - REFERENCE_RADIUS, SPACING);
        int regionZ1 = Math.floorDiv(chunkZ + REFERENCE_RADIUS, SPACING);
        for (int regionZ = regionZ0; regionZ <= regionZ1; regionZ++) {
            for (int regionX = regionX0; regionX <= regionX1; regionX++) {
                int[] potential = potentialStartChunk(seed, regionX * SPACING, regionZ * SPACING);
                if (Math.abs(potential[0] - chunkX) > REFERENCE_RADIUS
                        || Math.abs(potential[1] - chunkZ) > REFERENCE_RADIUS) {
                    continue;
                }
                Start start = startAt(seed, potential[0], potential[1]);
                if (start == null) continue;
                for (Piece piece : start.pieces()) {
                    if (piece.intersects(minX, 1, minZ, minX + 15, 255, minZ + 15)) {
                        found.add(start);
                        break;
                    }
                }
            }
        }
        return found;
    }

    // ── 블록 쓰기 ──
    /** 조각 블록을 받는 곳. {@code block} 이 AIR 면 비우는 칸이다. */
    public interface Sink {
        void set(int x, int y, int z, int block, int state);
    }

    /** 이 청크 쓰기 구역(y 1..255) 안의 도시 칸을 쓴다(값은 {@link #chunkCells} 와 같다). */
    public static void writeChunk(int seed, int chunkX, int chunkZ, Sink sink) {
        ChunkCells cells = chunkCells(seed, chunkX, chunkZ);
        if (cells == null) return;
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        for (int y = Blocks.MIN_Y; y <= Blocks.MAX_Y; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int value = cells.at(lx, y, lz);
                    if (value >= 0) sink.set(minX + lx, y, minZ + lz, value >>> 8, value & 0xff);
                }
            }
        }
    }

    /**
     * 조각 하나를 청크 칸에 쓴다. 바닐라 {@code StructureTemplate.placeInWorld} 는 {@code knownShape} 가 아닌
     * 배치마다 놓은 칸의 {@code updateFromNeighbourShapes} 를 돌리므로, 받침을 잃은 벽 부착 블록(자홍색 벽
     * 현수막 {@code WallBannerBlock.canSurvive}: 뒤 칸 {@code isSolid}, 사다리 {@code LadderBlock.canSurvive}: 뒤
     * 칸의 그 면이 단단함)은 공기가 된다. 받침은 이 청크에서 앞서 쓴 도시 칸, 없으면 지형이다 — 이웃 청크의
     * 도시 조각은 그 청크의 장식이 따로 쓰므로(바닐라 구조물은 자기 청크 구역 안만 쓴다) 이 순간에는 없다.
     */
    private static void writePiece(Piece piece, int minX, int minZ, int[] cells, VoidEndTerrain.Sampler terrain,
            List<int[]> attached) {
        EndCityTemplates.Template template = EndCityTemplates.byName(piece.template);
        for (int ty = 0; ty < template.sizeY(); ty++) {
            for (int tz = 0; tz < template.sizeZ(); tz++) {
                for (int tx = 0; tx < template.sizeX(); tx++) {
                    int state = template.stateAt(tx, ty, tz);
                    if (state == EndCityTemplates.EMPTY || isStructureBlock(state)) continue;
                    if (!piece.overwrite && isAir(state)) continue;
                    int[] p = transform(tx, ty, tz, piece.rotation);
                    int x = p[0] + piece.x;
                    int y = p[1] + piece.y;
                    int z = p[2] + piece.z;
                    if (x < minX || x > minX + 15 || z < minZ || z > minZ + 15 || y < 1 || y > 255) continue;
                    int mapped = EndCityTemplates.STATE_MAP[state][piece.rotation];
                    cells[Blocks.blockIndex(x - minX, y, z - minZ)] = mapped >>> 8 == Blocks.AIR ? 0 : mapped;
                    int facing = attachedFacing(state, piece.rotation);
                    if (facing >= 0) attached.add(new int[] {x, y, z, facing, mapped >>> 8});
                }
            }
        }
        for (int[] cell : attached) {
            int x = cell[0];
            int y = cell[1];
            int z = cell[2];
            int index = Blocks.blockIndex(x - minX, y, z - minZ);
            if (cells[index] < 0 || cells[index] >>> 8 != cell[4]) continue;
            int[] behind = HORIZONTAL_STEP[cell[3] + 2 & 3];
            if (!supports(cell[4], cell[3], x + behind[0], y, z + behind[1], minX, minZ, cells, terrain)) {
                cells[index] = 0;
            }
        }
    }

    /** 북·동·남·서 한 칸 이동. */
    private static final int[][] HORIZONTAL_STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** 벽 부착 블록(벽 현수막·사다리)의 돌린 facing(북 0·동 1·남 2·서 3), 아니면 −1. */
    private static int attachedFacing(int state, int rotation) {
        String vanilla = EndCityTemplates.VANILLA_STATES.get(state);
        if (!vanilla.startsWith("minecraft:magenta_wall_banner") && !vanilla.startsWith("minecraft:ladder")) {
            return -1;
        }
        int facing = vanilla.contains("facing=east") ? 1 : vanilla.contains("facing=south") ? 2
                : vanilla.contains("facing=west") ? 3 : 0;
        return facing + rotation & 3;
    }

    private static boolean supports(int block, int facing, int x, int y, int z, int minX, int minZ, int[] cells,
            VoidEndTerrain.Sampler terrain) {
        int support;
        int supportState = 0;
        int value = x >= minX && x <= minX + 15 && z >= minZ && z <= minZ + 15
                ? cells[Blocks.blockIndex(x - minX, y, z - minZ)] : -1;
        if (value >= 0) {
            support = value >>> 8;
            supportState = value & 0xff;
        } else {
            support = terrain.isSolid(x, y, z) ? Blocks.END_STONE : Blocks.AIR;
        }
        if (block == Blocks.LADDER) return sturdyFace(support, supportState, facing);
        return isSolidSupport(support);
    }

    /** {@code BlockState.isSolid()}(legacy solid) 인 도시 팔레트·지형 블록. */
    private static boolean isSolidSupport(int block) {
        return block != Blocks.AIR && block != Blocks.LADDER && block != Blocks.END_ROD
                && block != Blocks.DRAGON_HEAD && block != Blocks.CHORUS_PLANT && block != Blocks.CHORUS_FLOWER;
    }

    /** {@code isFaceSturdy(…, face)}: 풀 큐브는 모든 면, 보라 계단은 등판(facing 쪽 면)만. */
    private static boolean sturdyFace(int block, int state, int face) {
        return switch (block) {
            case Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.END_STONE, Blocks.END_STONE_BRICKS,
                    Blocks.MAGENTA_STAINED_GLASS, Blocks.OBSIDIAN -> true;
            case Blocks.PURPUR_SLAB -> (state & 3) == 2;
            case Blocks.PURPUR_STAIRS -> (state & 3) == face;
            default -> false;
        };
    }

    private static final int STRUCTURE_BLOCK_STATE = EndCityTemplates.VANILLA_STATES.indexOf("minecraft:structure_block[mode=data]");
    private static final int AIR_STATE = EndCityTemplates.VANILLA_STATES.indexOf("minecraft:air");

    static boolean isStructureBlock(int state) {
        return state == STRUCTURE_BLOCK_STATE;
    }

    static boolean isAir(int state) {
        return state == AIR_STATE;
    }

    static boolean isLootChest(int state) {
        return EndCityTemplates.VANILLA_STATES.get(state).startsWith("minecraft:chest[");
    }

    // ── 표지와 상자 난수 ──
    /** 청크 장식이 만든 도시 내용물 하나. */
    public record Content(Kind kind, int x, int y, int z, long lootSeed, int direction) {
    }

    public enum Kind {
        /** {@code end_city_treasure} 상자(좌표는 상자 칸). */
        TREASURE_CHEST,
        /** 셜커(좌표는 표지 칸, 몸 위치 = x+.5, y, z+.5). */
        SENTRY,
        /** 겉날개를 든 아이템 액자(좌표는 표지 칸, direction = 바닐라 Direction 3D 값). */
        ELYTRA_FRAME,
        /** 강한 치유 물약 둘(칸 0·2)을 든 양조기(엔드 배 템플릿 블록 엔티티). */
        BREWING_STAND,
        /** 검은 위/아래 삼각형 무늬의 자홍색 벽 현수막(tower_top 템플릿 블록 엔티티). */
        BANNER
    }

    /**
     * 이 청크 장식({@code applyBiomeDecoration} 의 end_city 순번)이 만드는 내용물. 상자 LootTableSeed 는
     * {@code placeInWorld} 의 상자 블록 엔티티 nextLong 과 표지 처리의 nextLong 을 바닐라 순서대로 소비해 얻는다.
     */
    public static List<Content> chunkContents(int seed, int chunkX, int chunkZ) {
        List<Start> starts = startsTouching(seed, chunkX, chunkZ);
        if (starts.isEmpty()) return List.of();
        List<Content> contents = new ArrayList<>();
        long decoration = VoidEndDecoration.decorationSeed(seed, chunkX, chunkZ);
        VoidEndDecoration.WorldgenRandom random = VoidEndDecoration.featureRandom(decoration, STRUCTURE_INDEX,
                VoidEndDecoration.STEP_SURFACE_STRUCTURES);
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        ChunkCells cells = chunkCells(seed, chunkX, chunkZ);
        for (Start start : starts) {
            for (Piece piece : start.pieces()) {
                if (!piece.intersects(minX, 1, minZ, minX + 15, 255, minZ + 15)) continue;
                pieceContents(piece, minX, minZ, random, contents);
            }
        }
        // 받침을 잃어 공기가 된 벽 현수막은 내용물이 없다(바닐라는 블록 엔티티째 사라진다).
        contents.removeIf(content -> content.kind() == Kind.BANNER && (cells == null
                || cells.at(content.x() - minX, content.y(), content.z() - minZ) >>> 8 != Blocks.MAGENTA_WALL_BANNER));
        return List.copyOf(contents);
    }

    private static boolean inBox(int x, int y, int z, int minX, int minZ) {
        return x >= minX && x <= minX + 15 && z >= minZ && z <= minZ + 15 && y >= 1 && y <= 255;
    }

    private static void pieceContents(Piece piece, int minX, int minZ, VoidEndDecoration.WorldgenRandom random,
            List<Content> contents) {
        EndCityTemplates.Template template = EndCityTemplates.byName(piece.template);
        // placeInWorld: 블록 엔티티 묶음은 템플릿 (y, x, z) 순서다. RandomizableContainer(상자)만 난수를 쓴다.
        for (int ty = 0; ty < template.sizeY(); ty++) {
            for (int tx = 0; tx < template.sizeX(); tx++) {
                for (int tz = 0; tz < template.sizeZ(); tz++) {
                    int state = template.stateAt(tx, ty, tz);
                    if (state == EndCityTemplates.EMPTY) continue;
                    String vanilla = EndCityTemplates.VANILLA_STATES.get(state);
                    boolean chest = vanilla.startsWith("minecraft:chest[");
                    boolean brewing = vanilla.startsWith("minecraft:brewing_stand");
                    boolean banner = vanilla.startsWith("minecraft:magenta_wall_banner");
                    if (!chest && !brewing && !banner) continue;
                    int[] p = transform(tx, ty, tz, piece.rotation);
                    int x = p[0] + piece.x;
                    int y = p[1] + piece.y;
                    int z = p[2] + piece.z;
                    if (!inBox(x, y, z, minX, minZ)) continue;
                    if (chest) {
                        random.nextLong();
                    } else {
                        contents.add(new Content(brewing ? Kind.BREWING_STAND : Kind.BANNER, x, y, z, 0L, -1));
                    }
                }
            }
        }
        for (EndCityTemplates.Marker marker : template.markers()) {
            int[] p = transform(marker.x(), marker.y(), marker.z(), piece.rotation);
            int x = p[0] + piece.x;
            int y = p[1] + piece.y;
            int z = p[2] + piece.z;
            if (!inBox(x, y, z, minX, minZ)) continue;
            if (marker.metadata().startsWith("Chest")) {
                if (inBox(x, y - 1, z, minX, minZ)) {
                    contents.add(new Content(Kind.TREASURE_CHEST, x, y - 1, z, random.nextLong(), -1));
                }
            } else if (marker.metadata().startsWith("Sentry")) {
                contents.add(new Content(Kind.SENTRY, x, y, z, 0L, DIRECTION_DOWN));
            } else if (marker.metadata().startsWith("Elytra")) {
                contents.add(new Content(Kind.ELYTRA_FRAME, x, y, z, 0L,
                        rotateDirection(piece.rotation, DIRECTION_SOUTH)));
            }
        }
    }

    /** 이 칸에 도시 블록이 쓰였는가를 보는 청크 단위 뷰(장식 heightmap·후렴 성장 공간이 쓴다). */
    public static final class ChunkCells {
        private final int[] cells;

        ChunkCells(int[] cells) {
            this.cells = cells;
        }

        /** -1 = 도시가 쓰지 않은 칸, 아니면 (블록 &lt;&lt; 8 | state). */
        public int at(int lx, int y, int lz) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return -1;
            return cells[Blocks.blockIndex(lx, y, lz)];
        }
    }

    private static final ChunkCells NO_CELLS = new ChunkCells(new int[0]);
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, ChunkCells>> CELLS =
            new ConcurrentHashMap<>();

    /** 이 청크의 도시 칸(없으면 null, 시드별 캐시). 조각 순서대로 쓰고 조각마다 벽 부착 블록의 받침을 본다. */
    public static ChunkCells chunkCells(int seed, int chunkX, int chunkZ) {
        ConcurrentHashMap<Long, ChunkCells> bySeed = CELLS.get(seed);
        if (bySeed == null) {
            if (CELLS.size() >= 8) CELLS.clear();
            bySeed = CELLS.computeIfAbsent(seed, ignored -> new ConcurrentHashMap<>());
        }
        long key = (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
        ChunkCells cached = bySeed.get(key);
        if (cached == null) {
            cached = computeCells(seed, chunkX, chunkZ);
            if (bySeed.size() > 1024) bySeed.clear();
            bySeed.put(key, cached);
        }
        return cached == NO_CELLS ? null : cached;
    }

    private static ChunkCells computeCells(int seed, int chunkX, int chunkZ) {
        List<Start> starts = startsTouching(seed, chunkX, chunkZ);
        if (starts.isEmpty()) return NO_CELLS;
        int[] cells = new int[Blocks.CHUNK_BLOCKS];
        java.util.Arrays.fill(cells, -1);
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        VoidEndTerrain.Sampler terrain = VoidEndTerrain.forSeed(seed).sampler();
        List<int[]> attached = new ArrayList<>();
        for (Start start : starts) {
            for (Piece piece : start.pieces()) {
                if (!piece.intersects(minX, 1, minZ, minX + 15, 255, minZ + 15)) continue;
                writePiece(piece, minX, minZ, cells, terrain, attached);
            }
        }
        return new ChunkCells(cells);
    }
}
