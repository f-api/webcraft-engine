package com.gameexpert.engine.qa;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.FrogPoisonConversionRules;
import com.gameexpert.terrain.Blocks;

/**
 * 전체 콘텐츠 QA 월드에 적용할 순수하고 결정적인 {@code content-v1} 배치 계획.
 *
 * <p>이 클래스는 런타임이나 저장소를 호출하지 않는다. 호출자는 {@link #cells()}를 기존의
 * 영속 블록 변경 경로로, {@link #mobRequests()}를 실제 QA 몹 스폰 경로로, {@link #supplies()}를
 * 플레이어 인벤토리 추가 경로로 각각 적용해야 한다. 따라서 픽스처도 실제 플레이 상태와 같은
 * 권위·영속 계약을 밟으며, 이 계획 안에는 가짜 아이템 컴포넌트나 런타임 전용 상태가 없다.</p>
 */
public final class ContentQaFixturePlan {
    public static final String HIVE_OCCUPANT_ID = "bee-harvest-inside";

    public static final String FIXTURE_ID = "content-v1";
    public static final int WIDTH = 32;
    public static final int DEPTH = 32;
    /** Highest fixture roof is y+5; y+6 stays clear for movement without overflowing the live journal. */
    public static final int CLEAR_HEIGHT = 6;
    /** 권위 시계는 MC 시간의 절반 단위이므로 6300 == MC 12600(야간 시작). */
    public static final int WORLD_TIME = 6_300;
    public static final int XP_AMOUNT = 30;
    public static final boolean EQUIP_ELYTRA = true;
    public static final int MIN_ANCHOR_XZ = -(1 << 25);
    public static final int MAX_ANCHOR_XZ = (1 << 25) - WIDTH;

    private static final int MAX_COLONY_LIGHT = 7;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final Bounds bounds;
    private final List<Cell> cells;
    private final List<Cell> stations;
    private final List<MobRequest> mobRequests;
    private final List<Supply> supplies;
    private final List<ColonyProbe> colonies;
    private final long checksum;
    private final Cell receiptCell;
    private final CooldownWitness cooldownWitness;

    private ContentQaFixturePlan(Bounds bounds, List<Cell> cells, List<Cell> stations,
            List<MobRequest> mobRequests, List<Supply> supplies, List<ColonyProbe> colonies,
            Cell receiptCell) {
        this.bounds = bounds;
        this.cells = List.copyOf(cells);
        this.stations = List.copyOf(stations);
        this.mobRequests = List.copyOf(mobRequests);
        this.supplies = List.copyOf(supplies);
        this.colonies = List.copyOf(colonies);
        this.receiptCell = Objects.requireNonNull(receiptCell, "receiptCell");
        this.cooldownWitness = new CooldownWitness("eligible-dark", "eligible-dark",
                "eligible-dark-cooldown-witness",
                FrogPoisonConversionRules.COLONY_COOLDOWN_MC_TICKS, 1, 0);
        this.checksum = checksum(bounds, this.cells, this.stations, this.mobRequests,
                this.supplies, this.colonies, this.receiptCell, this.cooldownWitness);
    }

    /**
     * {@code anchorX, floorY, anchorZ}를 32×32 바닥의 북서 모서리로 삼아 계획을 만든다.
     * 좌표는 TerrainAccessor의 26비트 X/Z 키와 월드 Y 범위를 넘지 않도록 선제 검증한다.
     */
    public static ContentQaFixturePlan create(int anchorX, int floorY, int anchorZ) {
        validateAnchor(anchorX, floorY, anchorZ);
        Bounds bounds = new Bounds(anchorX, floorY, anchorZ, WIDTH, CLEAR_HEIGHT, DEPTH);
        Map<Point, Integer> finalBlocks = new HashMap<>(WIDTH * DEPTH * (CLEAR_HEIGHT + 1));
        Map<Point, Integer> authoredStates = new HashMap<>(4);

        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                put(finalBlocks, anchorX + x, floorY, anchorZ + z, Blocks.STONE);
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    put(finalBlocks, anchorX + x, floorY + dy, anchorZ + z, Blocks.AIR);
                }
            }
        }

        List<ColonyProbe> colonies = new ArrayList<>(4);
        List<MobRequest> mobs = new ArrayList<>(4);
        addDarkRoom(finalBlocks, anchorX + 1, floorY, anchorZ + 2);
        colonies.add(addColony(finalBlocks, mobs, "eligible-dark", anchorX + 3, floorY + 1,
                anchorZ + 4, true, true, true, "night+dark+rafflesia+firefly"));
        mobs.add(new MobRequest("eligible-dark-cooldown-witness", MobType.FROG,
                anchorX + 4.5, floorY + 1, anchorZ + 5.5));

        addDarkRoom(finalBlocks, anchorX + 9, floorY, anchorZ + 2);
        colonies.add(addColony(finalBlocks, mobs, "control-no-rafflesia", anchorX + 11,
                floorY + 1, anchorZ + 4, true, false, false, "missing-rafflesia"));

        addDarkRoom(finalBlocks, anchorX + 17, floorY, anchorZ + 2);
        colonies.add(addColony(finalBlocks, mobs, "control-no-firefly", anchorX + 19,
                floorY + 1, anchorZ + 4, false, true, false, "missing-firefly-bush"));

        addDarkRoom(finalBlocks, anchorX + 25, floorY, anchorZ + 2);
        ColonyProbe bright = addColony(finalBlocks, mobs, "control-bright", anchorX + 27,
                floorY + 1, anchorZ + 4, true, true, false, "light-above-seven");
        put(finalBlocks, anchorX + 27, floorY + 1, anchorZ + 5, Blocks.GLOWSTONE);
        colonies.add(bright);

        int[] stationIds = {
                Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE,
                Blocks.BREWING_STAND, Blocks.CARTOGRAPHY_TABLE, Blocks.CAULDRON,
                Blocks.GRINDSTONE, Blocks.LECTERN, Blocks.LOOM, Blocks.SMITHING_TABLE,
                Blocks.STONECUTTER, Blocks.ANVIL, Blocks.CHEST, Blocks.BARREL
        };
        List<Cell> stations = new ArrayList<>(stationIds.length);
        for (int i = 0; i < stationIds.length; i++) {
            Point point = new Point(anchorX + 1 + i, floorY + 1, anchorZ + 12);
            put(finalBlocks, point.x(), point.y(), point.z(), stationIds[i]);
            stations.add(new Cell(point, stationIds[i]));
        }

        // H12 reward props: removing the ordinary stone support exercises the real neighbor/drop path.
        put(finalBlocks, anchorX + 1, floorY + 1, anchorZ + 16, Blocks.STONE);
        put(finalBlocks, anchorX + 1, floorY + 2, anchorZ + 16, Blocks.TATTERED_BANNER);
        put(finalBlocks, anchorX + 3, floorY + 1, anchorZ + 16, Blocks.STONE);
        put(finalBlocks, anchorX + 3, floorY + 2, anchorZ + 16, Blocks.CHERRY_BONSAI);

        // H12 flight wall and fishing water are plain world cells consumed by normal movement/actions.
        for (int y = floorY + 1; y <= floorY + 5; y++) {
            for (int z = anchorZ + 15; z <= anchorZ + 19; z++) {
                put(finalBlocks, anchorX + 27, y, z, Blocks.STONE);
            }
        }
        // The three-deep pond needs an equally tall visible retaining perimeter. Without it,
        // normal fluid ticks escape across the flat arena floor and flood unrelated dry probes.
        for (int y = floorY + 1; y <= floorY + 3; y++) {
            for (int z = anchorZ + 19; z <= anchorZ + 26; z++) {
                put(finalBlocks, anchorX + 15, y, z, Blocks.GLASS);
                put(finalBlocks, anchorX + 22, y, z, Blocks.GLASS);
            }
            for (int x = anchorX + 16; x <= anchorX + 21; x++) {
                put(finalBlocks, x, y, anchorZ + 19, Blocks.GLASS);
                put(finalBlocks, x, y, anchorZ + 26, Blocks.GLASS);
            }
        }
        for (int x = anchorX + 16; x <= anchorX + 21; x++) {
            for (int z = anchorZ + 20; z <= anchorZ + 25; z++) {
                put(finalBlocks, x, floorY + 1, z, Blocks.WATER_SOURCE);
                put(finalBlocks, x, floorY + 2, z, Blocks.WATER_SOURCE);
                put(finalBlocks, x, floorY + 3, z, Blocks.WATER_SOURCE);
            }
        }

        // Reciprocal pairs plus two stair corners. Runtime neighbor processing derives final states.
        int shapeZ = anchorZ + 28;
        put(finalBlocks, anchorX + 1, floorY + 1, shapeZ, Blocks.WOOD_FENCE);
        put(finalBlocks, anchorX + 2, floorY + 1, shapeZ, Blocks.WOOD_FENCE);
        put(finalBlocks, anchorX + 4, floorY + 1, shapeZ, Blocks.COBBLE_WALL);
        put(finalBlocks, anchorX + 5, floorY + 1, shapeZ, Blocks.COBBLE_WALL);
        put(finalBlocks, anchorX + 7, floorY + 1, shapeZ, Blocks.GLASS_PANE);
        put(finalBlocks, anchorX + 8, floorY + 1, shapeZ, Blocks.GLASS_PANE);
        // Four straight authored facings form a 2x2 turn. The live neighbor pass, rather than the
        // fixture, derives the four non-straight shape ordinals from these perpendicular facings.
        putAuthored(finalBlocks, authoredStates, anchorX + 10, floorY + 1, shapeZ,
                Blocks.WOOD_STAIRS, 0); // north
        putAuthored(finalBlocks, authoredStates, anchorX + 11, floorY + 1, shapeZ,
                Blocks.WOOD_STAIRS, 1); // east
        putAuthored(finalBlocks, authoredStates, anchorX + 10, floorY + 1, shapeZ + 1,
                Blocks.WOOD_STAIRS, 1); // east
        putAuthored(finalBlocks, authoredStates, anchorX + 11, floorY + 1, shapeZ + 1,
                Blocks.WOOD_STAIRS, 0); // north

        // Natural raid trigger anchor. The real raid schedule owns all role spawning and bossbar state.
        mobs.add(new MobRequest("final-scene-village-anchor", MobType.VILLAGER,
                anchorX + 15.5, floorY + 1, anchorZ + 16.5));
        // The visible north cap holds the resident under normal hive rules during preparation.
        // The clear west side remains available for ordinary harvest release and flight.
        putAuthored(finalBlocks, authoredStates, anchorX + 25, floorY + 1, anchorZ + 23,
                Blocks.BEEHIVE, 20);
        put(finalBlocks, anchorX + 25, floorY + 1, anchorZ + 22, Blocks.GLASS);
        mobs.add(new MobRequest(HIVE_OCCUPANT_ID, MobType.BEE,
                anchorX + 25.5, floorY + 1.5, anchorZ + 23.5));
        mobs.add(new MobRequest("bee-harvest-outside-control", MobType.BEE,
                anchorX + 25.5, floorY + 1, anchorZ + 25.5));

        // 부술 수 없는 바닥 모서리의 최종 셀이 재접속·재요청 멱등 receipt 역할을 한다.
        Cell receipt = new Cell(new Point(anchorX + WIDTH - 1, floorY,
                anchorZ + DEPTH - 1), Blocks.BEDROCK);
        put(finalBlocks, receipt.point().x(), receipt.point().y(), receipt.point().z(),
                receipt.blockId());

        List<Cell> cells = finalBlocks.entrySet().stream()
                .map(entry -> new Cell(entry.getKey(), entry.getValue(),
                        authoredStates.getOrDefault(entry.getKey(), 0)))
                .sorted(Comparator.comparingInt((Cell cell) -> cell.point().y())
                        .thenComparingInt(cell -> cell.point().z())
                        .thenComparingInt(cell -> cell.point().x()))
                .toList();
        return new ContentQaFixturePlan(bounds, cells, stations, mobs, rawSupplies(), colonies,
                receipt);
    }

    private static void validateAnchor(int x, int y, int z) {
        if (x < MIN_ANCHOR_XZ || x > MAX_ANCHOR_XZ
                || z < MIN_ANCHOR_XZ || z > MAX_ANCHOR_XZ) {
            throw new IllegalArgumentException("QA fixture X/Z anchor is outside the packed world range");
        }
        // receipt는 y>=0에서 자연적으로 절대 생기지 않는 BEDROCK 셀이다.
        if (y < 0 || y > Blocks.MAX_Y - CLEAR_HEIGHT) {
            throw new IllegalArgumentException("QA fixture floor leaves the world build height");
        }
    }

    /** 여섯 칸 정사각형을 틴티드 글라스 벽/천장으로 감싸 주간에도 내부 광량을 차단한다. */
    private static void addDarkRoom(Map<Point, Integer> blocks, int minX, int floorY, int minZ) {
        int maxX = minX + 5;
        int maxZ = minZ + 5;
        for (int y = floorY + 1; y <= floorY + 4; y++) {
            for (int x = minX; x <= maxX; x++) {
                put(blocks, x, y, minZ, Blocks.STONE);
                put(blocks, x, y, maxZ, Blocks.STONE);
            }
            for (int z = minZ + 1; z < maxZ; z++) {
                put(blocks, minX, y, z, Blocks.STONE);
                put(blocks, maxX, y, z, Blocks.STONE);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                put(blocks, x, floorY + 5, z, Blocks.STONE);
            }
        }
    }

    private static ColonyProbe addColony(Map<Point, Integer> blocks, List<MobRequest> mobs,
            String id, int bushX, int y, int z, boolean firefly, boolean rafflesia,
            boolean expectedEligible, String falseControl) {
        Cell bush = null;
        if (firefly) {
            put(blocks, bushX, y - 1, z, Blocks.GRASS);
            bush = new Cell(new Point(bushX, y, z), Blocks.FIREFLY_BUSH);
            put(blocks, bush.point().x(), bush.point().y(), bush.point().z(), bush.blockId());
        }
        Cell flower = null;
        if (rafflesia) {
            put(blocks, bushX + 2, y - 1, z, Blocks.MANGROVE_ROOTS);
            flower = new Cell(new Point(bushX + 2, y, z), Blocks.RAFFLESIA);
            put(blocks, flower.point().x(), flower.point().y(), flower.point().z(), flower.blockId());
        }
        mobs.add(new MobRequest(id, MobType.FROG, bushX + 0.5, y, z + 0.5));
        return new ColonyProbe(id, bush, flower, MAX_COLONY_LIGHT, expectedEligible, falseControl);
    }

    private static List<Supply> rawSupplies() {
        return List.of(
                new Supply(PlayerInventory.ELYTRA, 1),
                new Supply(PlayerInventory.FIREWORK_ROCKET_1, 16),
                new Supply(PlayerInventory.FISHING_ROD, 1),
                new Supply(PlayerInventory.DIAMOND_PICKAXE, 1),
                new Supply((short) Blocks.LOG, 64),
                new Supply(PlayerInventory.STICK, 64),
                new Supply(PlayerInventory.COAL, 64),
                new Supply(PlayerInventory.RAW_IRON, 64),
                new Supply(PlayerInventory.IRON_INGOT, 64),
                new Supply(PlayerInventory.COPPER_INGOT, 64),
                new Supply(PlayerInventory.DIAMOND, 32),
                new Supply(PlayerInventory.LEATHER, 32),
                new Supply(PlayerInventory.LEATHER_HELMET, 1),
                new Supply(PlayerInventory.PAPER, 64),
                new Supply(PlayerInventory.MAP, 8),
                new Supply(PlayerInventory.BOOK, 16),
                new Supply(PlayerInventory.WRITABLE_BOOK, 1, ItemComponentCodec.encode(
                        PlayerInventory.WRITABLE_BOOK,
                        ItemComponentData.EMPTY.withBook(new ItemComponentData.BookData(
                                null, null, List.of(""))))),
                new Supply(PlayerInventory.FEATHER, 16),
                new Supply(PlayerInventory.INK_SAC, 16),
                new Supply(PlayerInventory.WHITE_DYE, 16),
                new Supply(PlayerInventory.RED_DYE, 16),
                new Supply((short) Blocks.WHITE_BANNER, 1),
                new Supply(PlayerInventory.HONEYCOMB, 32),
                new Supply(PlayerInventory.WATER_BUCKET, 1),
                new Supply(PlayerInventory.GLASS_BOTTLE, 16),
                new Supply(PlayerInventory.LAPIS_LAZULI, 64),
                new Supply(PlayerInventory.NAME_TAG, 8),
                new Supply(PlayerInventory.DIAMOND_HOE, 1),
                new Supply(PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 4),
                new Supply(PlayerInventory.NETHERITE_INGOT, 16));
    }

    private static void put(Map<Point, Integer> blocks, int x, int y, int z, int blockId) {
        blocks.put(new Point(x, y, z), blockId);
    }

    private static void putAuthored(Map<Point, Integer> blocks, Map<Point, Integer> states,
            int x, int y, int z, int blockId, int state) {
        Point point = new Point(x, y, z);
        blocks.put(point, blockId);
        states.put(point, state);
    }

    private static long checksum(Bounds bounds, List<Cell> cells, List<Cell> stations,
            List<MobRequest> mobs, List<Supply> supplies, List<ColonyProbe> colonies,
            Cell receipt, CooldownWitness cooldown) {
        long hash = FNV_OFFSET;
        hash = fnvString(hash, FIXTURE_ID);
        hash = fnvInt(hash, bounds.anchorX());
        hash = fnvInt(hash, bounds.floorY());
        hash = fnvInt(hash, bounds.anchorZ());
        hash = fnvInt(hash, bounds.width());
        hash = fnvInt(hash, bounds.clearHeight());
        hash = fnvInt(hash, bounds.depth());
        hash = fnvInt(hash, WORLD_TIME);
        hash = fnvInt(hash, XP_AMOUNT);
        hash = fnvInt(hash, EQUIP_ELYTRA ? 1 : 0);
        hash = fnvInt(hash, cells.size());
        for (Cell cell : cells) {
            hash = fnvCell(hash, cell);
        }
        hash = fnvInt(hash, stations.size());
        for (Cell station : stations) hash = fnvCell(hash, station);
        hash = fnvCell(hash, receipt);
        hash = fnvInt(hash, mobs.size());
        for (MobRequest mob : mobs) {
            hash = fnvString(hash, mob.id());
            hash = fnvInt(hash, mob.type().stableId());
            hash = fnvLong(hash, Double.doubleToLongBits(mob.x()));
            hash = fnvLong(hash, Double.doubleToLongBits(mob.y()));
            hash = fnvLong(hash, Double.doubleToLongBits(mob.z()));
        }
        hash = fnvInt(hash, supplies.size());
        for (Supply supply : supplies) {
            hash = fnvInt(hash, Short.toUnsignedInt(supply.itemType()));
            hash = fnvInt(hash, supply.count());
            hash = fnvInt(hash, supply.itemComponentData() == null ? 0 : 1);
            if (supply.itemComponentData() != null) {
                hash = fnvString(hash, supply.itemComponentData());
            }
        }
        hash = fnvInt(hash, colonies.size());
        for (ColonyProbe colony : colonies) {
            hash = fnvString(hash, colony.id());
            hash = fnvNullableCell(hash, colony.fireflyBush());
            hash = fnvNullableCell(hash, colony.rafflesia());
            hash = fnvInt(hash, colony.requiredMaximumLight());
            hash = fnvInt(hash, colony.expectedEligible() ? 1 : 0);
            hash = fnvString(hash, colony.condition());
        }
        hash = fnvString(hash, cooldown.colonyId());
        hash = fnvString(hash, cooldown.firstFrogId());
        hash = fnvString(hash, cooldown.blockedFrogId());
        hash = fnvLong(hash, cooldown.cooldownMcTicks());
        hash = fnvInt(hash, cooldown.expectedInitialConversions());
        hash = fnvInt(hash, cooldown.expectedConversionsDuringCooldown());
        return hash;
    }

    private static long fnvString(long hash, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        hash = fnvInt(hash, bytes.length);
        for (byte element : bytes) hash = fnv(hash, element);
        return hash;
    }

    private static long fnvNullableCell(long hash, Cell cell) {
        hash = fnvInt(hash, cell == null ? 0 : 1);
        return cell == null ? hash : fnvCell(hash, cell);
    }

    private static long fnvCell(long hash, Cell cell) {
        hash = fnvInt(hash, cell.point().x());
        hash = fnvInt(hash, cell.point().y());
        hash = fnvInt(hash, cell.point().z());
        hash = fnvInt(hash, cell.blockId());
        return fnvInt(hash, cell.state());
    }

    private static long fnvInt(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) hash = fnv(hash, (byte) (value >>> shift));
        return hash;
    }

    private static long fnvLong(long hash, long value) {
        for (int shift = 0; shift < 64; shift += 8) hash = fnv(hash, (byte) (value >>> shift));
        return hash;
    }

    private static long fnv(long hash, byte value) {
        return (hash ^ (value & 0xffL)) * FNV_PRIME;
    }

    public String id() { return FIXTURE_ID; }
    public Bounds bounds() { return bounds; }
    public int worldTime() { return WORLD_TIME; }
    public List<Cell> cells() { return cells; }
    public List<Cell> stations() { return stations; }
    public List<MobRequest> mobRequests() { return mobRequests; }
    public List<Supply> supplies() { return supplies; }
    public List<ColonyProbe> colonies() { return colonies; }
    public long checksum() { return checksum; }
    public int xpAmount() { return XP_AMOUNT; }
    public boolean equipElytra() { return EQUIP_ELYTRA; }
    public Cell receiptCell() { return receiptCell; }
    public CooldownWitness cooldownWitness() { return cooldownWitness; }

    public record Point(int x, int y, int z) {}

    public record Cell(Point point, int blockId, int state) {
        public Cell(Point point, int blockId) {
            this(point, blockId, 0);
        }

        public Cell {
            Objects.requireNonNull(point, "point");
            if (blockId < Blocks.AIR || blockId > Blocks.PROTOCOL_ID_HIGH_WATER) {
                throw new IllegalArgumentException("unknown block protocol id: " + blockId);
            }
            if (state < 0 || state > 0xff
                    || BuildingBlockRules.normalizeState(blockId, state) != state) {
                throw new IllegalArgumentException("invalid authored block state: " + state);
            }
        }
    }

    public record Bounds(int anchorX, int floorY, int anchorZ, int width, int clearHeight,
            int depth) {}

    public record MobRequest(String id, MobType type, double x, double y, double z) {
        public MobRequest {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
        }
    }

    /** Exact fixture stack request; component-bearing items carry their validated wire payload. */
    public record Supply(short itemType, int count, String itemComponentData) {
        public Supply(short itemType, int count) {
            this(itemType, count, null);
        }

        public Supply {
            if (itemType <= 0 || count <= 0 || count > PlayerInventory.stackMax(itemType)) {
                throw new IllegalArgumentException("invalid supply stack");
            }
            ItemComponentCodec.decode(itemType, itemComponentData);
        }
    }

    /** null인 식물 칸은 의도적으로 빠진 독립 음성 대조군이다. */
    public record ColonyProbe(String id, Cell fireflyBush, Cell rafflesia,
            int requiredMaximumLight, boolean expectedEligible, String condition) {
        public ColonyProbe {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(condition, "condition");
        }
    }

    /** 첫 개구리 1마리 변환 뒤 같은 군락의 두 번째 개구리가 cooldown 동안 0회여야 하는 증인. */
    public record CooldownWitness(String colonyId, String firstFrogId, String blockedFrogId,
            long cooldownMcTicks, int expectedInitialConversions,
            int expectedConversionsDuringCooldown) {}
}
