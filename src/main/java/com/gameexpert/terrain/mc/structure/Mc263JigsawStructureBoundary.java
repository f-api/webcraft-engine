package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureCatalog.HeightMode;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureCatalog.StructureSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure fail-closed boundary between pinned jigsaw data and future procedural piece emitters.
 * It selects and rotates procedural descriptors, but deliberately places no blocks.
 */
public final class Mc263JigsawStructureBoundary {
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final Set<String> CAMP_TREE_POOLS = Set.of("acacia", "bamboo", "birch",
            "birch_bees", "cherry", "cherry_bees", "fancy_oak", "fancy_oak_bees",
            "jungle", "mega_jungle", "mega_pine", "oak", "orange_poplar", "pale_oak",
            "pine", "red_poplar", "spruce", "spruce_on_snow", "super_birch_bees",
            "thick_spruce", "yellow_poplar");
    private static final Set<String> OUTPOST_POOLS = Set.of("base_plates", "towers",
            "feature_plates", "features");
    private static final Set<String> TRAIL_RUINS_POOLS = Set.of("tower", "roads", "decor",
            "tower/additions", "tower/tower_top", "buildings", "buildings/grouped");
    private static final Set<String> ANCIENT_CITY_POOLS = Set.of("city_center", "structures",
            "sculk", "walls", "walls/no_corners", "city/entrance", "city_center/walls");
    private static final Set<String> TRIAL_CHAMBERS_POOLS = Set.of(
            "atrium", "chamber/addon", "chamber/assembly", "chamber/end",
            "chamber/entrance_cap", "chamber/eruption", "chamber/pedestal",
            "chamber/slanted", "chambers/end", "chests/contents/supply", "chests/supply",
            "corridor", "corridor/slices", "corridors/addon/lower",
            "corridors/addon/middle", "corridors/addon/middle_upper", "decor", "decor/bed",
            "decor/chamber", "decor/disposal", "dispensers/chamber", "entrance", "hallway",
            "hallway/fallback", "reward/all", "reward/contents/default",
            "reward/ominous_vault", "spawner/all", "spawner/breeze",
            "spawner/contents/breeze", "spawner/melee", "spawner/melee/husk",
            "spawner/melee/spider", "spawner/melee/zombie", "spawner/ranged",
            "spawner/ranged/poison_skeleton", "spawner/ranged/skeleton",
            "spawner/ranged/stray", "spawner/slow_ranged",
            "spawner/slow_ranged/poison_skeleton", "spawner/slow_ranged/skeleton",
            "spawner/slow_ranged/stray", "spawner/small_melee",
            "spawner/small_melee/baby_zombie", "spawner/small_melee/cave_spider",
            "spawner/small_melee/silverfish", "spawner/small_melee/slime");
    private static final Set<String> VILLAGE_COMMON_POOLS = Set.of("animals",
            "butcher_animals", "cats", "iron_golem", "sheep", "well_bottoms");
    private static final Set<String> VILLAGE_BASE_POOLS = Set.of("decor", "houses", "streets",
            "terminators", "town_centers", "villagers", "zombie/decor", "zombie/houses",
            "zombie/streets", "zombie/terminators", "zombie/villagers");
    private static final Set<String> PROCESSORS = Set.of(
            "", "minecraft:ancient_city_generic_degradation",
            "minecraft:ancient_city_start_degradation",
            "minecraft:ancient_city_walls_degradation", "minecraft:farm_desert",
            "minecraft:farm_plains", "minecraft:farm_savanna", "minecraft:farm_snowy",
            "minecraft:farm_taiga", "minecraft:mossify_10_percent",
            "minecraft:mossify_20_percent", "minecraft:mossify_70_percent",
            "minecraft:outpost_rot", "minecraft:street_plains",
            "minecraft:street_savanna", "minecraft:street_snowy_or_taiga",
            "minecraft:trail_ruins_houses_archaeology",
            "minecraft:trail_ruins_roads_archaeology",
            "minecraft:trail_ruins_tower_top_archaeology",
            "minecraft:trial_chambers_copper_bulb_degradation",
            "minecraft:zombie_desert", "minecraft:zombie_plains",
            "minecraft:zombie_savanna", "minecraft:zombie_snowy", "minecraft:zombie_taiga");

    private Mc263JigsawStructureBoundary() {}

    public static ValidatedGrammar validate(Grammar grammar) {
        if (grammar == null) throw new IllegalArgumentException("jigsaw grammar is required");
        StructureSpec structure = Mc263JigsawStructureCatalog.require(grammar.structureKey());
        if (grammar.pools().isEmpty()) throw new IllegalArgumentException("pool grammar is empty");

        Map<String, PoolSpec> byKey = new HashMap<>();
        for (PoolSpec pool : grammar.pools()) {
            requireResourceKey(pool.key(), "pool key");
            if (!isPoolKeyForStructure(structure.key(), pool.key())) {
                throw new IllegalArgumentException("pool is outside pinned structure grammar: "
                        + pool.key());
            }
            if (byKey.put(pool.key(), pool) != null) {
                throw new IllegalArgumentException("duplicate pool: " + pool.key());
            }
        }
        if (!byKey.containsKey(structure.startPool())) {
            throw new IllegalArgumentException("pinned start pool is absent: "
                    + structure.startPool());
        }

        for (PoolSpec pool : grammar.pools()) validatePool(pool, byKey, structure);
        List<PoolSpec> ordered = new ArrayList<>(grammar.pools());
        ordered.sort(Comparator.comparing(PoolSpec::key));
        return new ValidatedGrammar(structure, List.copyOf(ordered), Map.copyOf(byKey));
    }

    private static void validatePool(PoolSpec pool, Map<String, PoolSpec> pools,
            StructureSpec structure) {
        requireResourceKey(pool.fallback(), "fallback pool");
        if (!pool.fallback().equals(EMPTY_POOL) && !pools.containsKey(pool.fallback())) {
            throw new IllegalArgumentException("missing fallback pool: " + pool.fallback());
        }
        if (pool.elements().isEmpty() && !pool.key().equals(EMPTY_POOL)) {
            throw new IllegalArgumentException("non-empty pool key has no elements: " + pool.key());
        }
        long totalWeight = 0;
        for (ElementSpec element : pool.elements()) {
            totalWeight += element.weight();
            if (totalWeight > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("pool weight exceeds codec bound: " + pool.key());
            }
            validateElement(element, pools, structure);
        }
    }

    private static void validateElement(ElementSpec element, Map<String, PoolSpec> pools,
            StructureSpec structure) {
        if (element.weight() <= 0) throw new IllegalArgumentException("element weight");
        if (element.type() == null || element.projection() == null) {
            throw new IllegalArgumentException("element type and projection are required");
        }
        if (!PROCESSORS.contains(element.processor())) {
            throw new IllegalArgumentException("unsupported pinned processor: "
                    + element.processor());
        }
        if (element.type() == ElementType.EMPTY) {
            if (!element.key().isEmpty() || element.bounds() != null
                    || !element.connectors().isEmpty() || !element.components().isEmpty()) {
                throw new IllegalArgumentException("empty pool element carries procedural data");
            }
            return;
        }
        requireResourceKey(element.key(), "element key");
        if (element.bounds() == null) throw new IllegalArgumentException("element bounds");
        validateBounds(element.bounds(), structure);
        if (element.type() == ElementType.FEATURE && (!element.connectors().isEmpty()
                || !element.components().isEmpty())) {
            throw new IllegalArgumentException("feature element cannot carry jigsaw connectors");
        }
        if (element.type() == ElementType.LIST) {
            if (element.components().isEmpty()) {
                throw new IllegalArgumentException("list pool element requires components");
            }
            for (String component : element.components()) {
                requireResourceKey(component, "list component");
            }
        } else if (!element.components().isEmpty()) {
            throw new IllegalArgumentException("only list pool elements have components");
        }
        Set<String> connectorIdentities = new HashSet<>();
        for (ConnectorSpec connector : element.connectors()) {
            validateConnector(connector, pools, element.bounds());
            String identity = connector.x() + ":" + connector.y() + ":" + connector.z()
                    + ":" + connector.front() + ":" + connector.name();
            if (!connectorIdentities.add(identity)) {
                throw new IllegalArgumentException("duplicate connector: " + identity);
            }
        }
    }

    private static void validateBounds(Bounds bounds, StructureSpec structure) {
        if (bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY()
                || bounds.minZ() > bounds.maxZ()) {
            throw new IllegalArgumentException("inverted element bounds");
        }
        long xSpan = (long) bounds.maxX() - bounds.minX() + 1L;
        long ySpan = (long) bounds.maxY() - bounds.minY() + 1L;
        long zSpan = (long) bounds.maxZ() - bounds.minZ() + 1L;
        if (xSpan > (long) structure.maxHorizontalDistance() * 2L + 1L
                || ySpan > (long) structure.maxVerticalDistance() * 2L + 1L
                || zSpan > (long) structure.maxHorizontalDistance() * 2L + 1L) {
            throw new IllegalArgumentException("element exceeds pinned structure bound");
        }
        int terrainEdge = structure.terrainAdaptation() == null ? 0 : 12;
        if (structure.maxHorizontalDistance() + terrainEdge > 128) {
            throw new IllegalArgumentException("terrain-adapted structure exceeds 128 blocks");
        }
    }

    private static void validateConnector(ConnectorSpec connector, Map<String, PoolSpec> pools,
            Bounds bounds) {
        if (connector.front() == null || connector.top() == null || connector.joint() == null) {
            throw new IllegalArgumentException("connector orientation is required");
        }
        if (connector.front().axis() == connector.top().axis()) {
            throw new IllegalArgumentException("connector front and top axes must differ");
        }
        if (!bounds.contains(connector.x(), connector.y(), connector.z())) {
            throw new IllegalArgumentException("connector lies outside element bounds");
        }
        requireResourceKey(connector.name(), "connector name");
        requireResourceKey(connector.target(), "connector target");
        requireResourceKey(connector.pool(), "connector pool");
        if (!connector.pool().equals(EMPTY_POOL) && !pools.containsKey(connector.pool())) {
            throw new IllegalArgumentException("connector references missing pool: "
                    + connector.pool());
        }
    }

    public static StartPlan plan(long worldSeed, int chunkX, int chunkZ, Grammar grammar) {
        return plan(worldSeed, chunkX, chunkZ, validate(grammar));
    }

    public static StartPlan plan(long worldSeed, int chunkX, int chunkZ,
            ValidatedGrammar grammar) {
        if (grammar == null) throw new IllegalArgumentException("validated grammar is required");
        StructureSpec structure = grammar.structure();
        LegacyRand seedRandom = new LegacyRand(worldSeed);
        long xScale = seedRandom.nextLong();
        long zScale = seedRandom.nextLong();
        LegacyRand random = new LegacyRand((long) chunkX * xScale
                ^ (long) chunkZ * zScale ^ worldSeed);

        int startHeight = structure.minStartHeight();
        if (structure.heightMode() == HeightMode.UNIFORM) {
            startHeight += random.nextInt(structure.maxStartHeight()
                    - structure.minStartHeight() + 1);
        }
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        PoolSpec startPool = grammar.byKey().get(structure.startPool());
        ElementSpec selected = weighted(startPool.elements(), random);
        if (selected.type() == ElementType.EMPTY) {
            return StartPlan.empty(structure.key(), chunkX, chunkZ, startHeight, rotation);
        }
        if (!structure.startJigsawName().isEmpty()
                && selected.connectors().stream().noneMatch(connector ->
                        connector.name().equals(structure.startJigsawName()))) {
            return StartPlan.empty(structure.key(), chunkX, chunkZ, startHeight, rotation);
        }
        int blockX = Math.multiplyExact(chunkX, 16);
        int blockZ = Math.multiplyExact(chunkZ, 16);
        List<RotatedConnector> connectors = new ArrayList<>();
        for (ConnectorSpec connector : selected.connectors()) {
            Vec3 position = rotate(connector.x(), connector.y(), connector.z(), rotation);
            connectors.add(new RotatedConnector(position.x(), position.y(), position.z(),
                    rotate(connector.front(), rotation), rotate(connector.top(), rotation),
                    connector.joint(), connector.name(), connector.target(), connector.pool(),
                    connector.placementPriority(), connector.selectionPriority()));
        }
        return new StartPlan(structure.key(), chunkX, chunkZ, blockX, startHeight, blockZ,
                rotation, selected.key(), List.copyOf(connectors), true);
    }

    public static StartPlan planAndApply(long worldSeed, int chunkX, int chunkZ,
            Grammar grammar, PlanSink sink) {
        if (sink == null) throw new IllegalArgumentException("plan sink is required");
        StartPlan plan = plan(worldSeed, chunkX, chunkZ, grammar);
        sink.apply(plan);
        return plan;
    }

    private static ElementSpec weighted(List<ElementSpec> elements, LegacyRand random) {
        int total = 0;
        for (ElementSpec element : elements) total = Math.addExact(total, element.weight());
        if (total <= 0) throw new IllegalArgumentException("pool has no weighted elements");
        int choice = random.nextInt(total);
        for (ElementSpec element : elements) {
            choice -= element.weight();
            if (choice < 0) return element;
        }
        throw new IllegalStateException("weighted selection escaped pool");
    }

    public static boolean canAttach(RotatedConnector source, RotatedConnector target) {
        if (source == null || target == null) return false;
        boolean oppositeFronts = source.front() == target.front().opposite();
        boolean topMatches = source.joint() == Joint.ROLLABLE || source.top() == target.top();
        boolean nameMatches = target.name().isEmpty() || source.target().equals(target.name());
        return oppositeFronts && topMatches && nameMatches;
    }

    public static Vec3 rotate(int x, int y, int z, Rotation rotation) {
        if (rotation == null) throw new IllegalArgumentException("rotation is required");
        return switch (rotation) {
            case NONE -> new Vec3(x, y, z);
            case CLOCKWISE_90 -> new Vec3(-z, y, x);
            case CLOCKWISE_180 -> new Vec3(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new Vec3(z, y, -x);
        };
    }

    public static Direction rotate(Direction direction, Rotation rotation) {
        if (direction.axis() == Axis.Y) return direction;
        Direction result = direction;
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        for (int index = 0; index < turns; index++) result = result.clockwise();
        return result;
    }

    private static void requireResourceKey(String key, String label) {
        if (key == null || !key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(label + " is not a namespaced key: " + key);
        }
    }

    private static boolean isPoolKeyForStructure(String structureKey, String poolKey) {
        if (poolKey.equals(EMPTY_POOL)) return true;
        if (structureKey.startsWith("minecraft:abandoned_camp_")) {
            String biome = structureKey.substring("minecraft:abandoned_camp_".length());
            return poolKey.equals("minecraft:abandoned_camp/tent/" + biome)
                    || poolKey.equals("minecraft:abandoned_camp/camp/" + biome)
                    || poolKey.startsWith("minecraft:abandoned_camp/trees/")
                    && CAMP_TREE_POOLS.contains(poolKey.substring(
                            "minecraft:abandoned_camp/trees/".length()));
        }
        if (structureKey.startsWith("minecraft:village_")) {
            String biome = structureKey.substring("minecraft:village_".length());
            if (poolKey.startsWith("minecraft:village/common/")) {
                return VILLAGE_COMMON_POOLS.contains(poolKey.substring(
                        "minecraft:village/common/".length()));
            }
            String prefix = "minecraft:village/" + biome + "/";
            if (!poolKey.startsWith(prefix)) return false;
            String suffix = poolKey.substring(prefix.length());
            if (VILLAGE_BASE_POOLS.contains(suffix)) {
                return !(biome.equals("plains") || biome.equals("snowy")
                        || biome.equals("taiga")) || !suffix.equals("zombie/terminators");
            }
            return (biome.equals("plains") || biome.equals("savanna")
                    || biome.equals("snowy")) && suffix.equals("trees")
                    || biome.equals("desert") && suffix.equals("camel");
        }
        return matchesPoolFamily(structureKey, poolKey, "minecraft:pillager_outpost",
                "minecraft:pillager_outpost/", OUTPOST_POOLS)
                || matchesPoolFamily(structureKey, poolKey, "minecraft:trail_ruins",
                        "minecraft:trail_ruins/", TRAIL_RUINS_POOLS)
                || matchesPoolFamily(structureKey, poolKey, "minecraft:trial_chambers",
                        "minecraft:trial_chambers/", TRIAL_CHAMBERS_POOLS)
                || matchesPoolFamily(structureKey, poolKey, "minecraft:ancient_city",
                        "minecraft:ancient_city/", ANCIENT_CITY_POOLS);
    }

    private static boolean matchesPoolFamily(String structureKey, String poolKey,
            String requiredStructure, String prefix, Set<String> suffixes) {
        return structureKey.equals(requiredStructure) && poolKey.startsWith(prefix)
                && suffixes.contains(poolKey.substring(prefix.length()));
    }

    public interface PlanSink { void apply(StartPlan plan); }

    public enum ElementType {
        LEGACY_SINGLE("minecraft:legacy_single_pool_element"),
        SINGLE("minecraft:single_pool_element"),
        LIST("minecraft:list_pool_element"),
        FEATURE("minecraft:feature_pool_element"),
        EMPTY("minecraft:empty_pool_element");

        private final String key;
        ElementType(String key) { this.key = key; }
        public String key() { return key; }
        public static ElementType fromKey(String key) {
            for (ElementType value : values()) if (value.key.equals(key)) return value;
            throw new IllegalArgumentException("unsupported pool element type: " + key);
        }
    }

    public enum Projection { RIGID, TERRAIN_MATCHING }
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum Joint { ALIGNED, ROLLABLE }
    public enum Axis { X, Y, Z }
    public enum Direction {
        DOWN(Axis.Y), UP(Axis.Y), NORTH(Axis.Z), SOUTH(Axis.Z), WEST(Axis.X), EAST(Axis.X);
        private final Axis axis;
        Direction(Axis axis) { this.axis = axis; }
        public Axis axis() { return axis; }
        public Direction opposite() {
            return switch (this) {
                case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH; case SOUTH -> NORTH;
                case WEST -> EAST; case EAST -> WEST;
            };
        }
        private Direction clockwise() {
            return switch (this) {
                case NORTH -> EAST; case EAST -> SOUTH; case SOUTH -> WEST; case WEST -> NORTH;
                default -> this;
            };
        }
    }

    public static final class Grammar {
        private final String structureKey;
        private final List<PoolSpec> pools;
        public Grammar(String structureKey, List<PoolSpec> pools) {
            this.structureKey = structureKey;
            this.pools = pools == null ? List.of() : List.copyOf(pools);
        }
        public String structureKey() { return structureKey; }
        public List<PoolSpec> pools() { return pools; }
    }

    public static final class PoolSpec {
        private final String key;
        private final String fallback;
        private final List<ElementSpec> elements;
        public PoolSpec(String key, String fallback, List<ElementSpec> elements) {
            this.key = key;
            this.fallback = fallback;
            this.elements = elements == null ? List.of() : List.copyOf(elements);
        }
        public String key() { return key; }
        public String fallback() { return fallback; }
        public List<ElementSpec> elements() { return elements; }
    }

    public static final class ElementSpec {
        private final ElementType type;
        private final String key;
        private final int weight;
        private final Projection projection;
        private final String processor;
        private final Bounds bounds;
        private final List<ConnectorSpec> connectors;
        private final List<String> components;
        public ElementSpec(ElementType type, String key, int weight, Projection projection,
                String processor, Bounds bounds, List<ConnectorSpec> connectors,
                List<String> components) {
            this.type = type;
            this.key = key == null ? "" : key;
            this.weight = weight;
            this.projection = projection;
            this.processor = processor == null ? "" : processor;
            this.bounds = bounds;
            this.connectors = connectors == null ? List.of() : List.copyOf(connectors);
            this.components = components == null ? List.of() : List.copyOf(components);
        }
        public static ElementSpec single(String key, int weight, Projection projection,
                String processor, Bounds bounds, List<ConnectorSpec> connectors) {
            return new ElementSpec(ElementType.LEGACY_SINGLE, key, weight, projection,
                    processor, bounds, connectors, List.of());
        }
        public static ElementSpec empty(int weight) {
            return new ElementSpec(ElementType.EMPTY, "", weight, Projection.RIGID,
                    "", null, List.of(), List.of());
        }
        public ElementType type() { return type; }
        public String key() { return key; }
        public int weight() { return weight; }
        public Projection projection() { return projection; }
        public String processor() { return processor; }
        public Bounds bounds() { return bounds; }
        public List<ConnectorSpec> connectors() { return connectors; }
        public List<String> components() { return components; }
    }

    public static final class ConnectorSpec {
        private final int x, y, z;
        private final Direction front, top;
        private final Joint joint;
        private final String name, target, pool;
        private final int placementPriority, selectionPriority;
        public ConnectorSpec(int x, int y, int z, Direction front, Direction top, Joint joint,
                String name, String target, String pool, int placementPriority,
                int selectionPriority) {
            this.x = x; this.y = y; this.z = z; this.front = front; this.top = top;
            this.joint = joint; this.name = name; this.target = target; this.pool = pool;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        public Direction front() { return front; } public Direction top() { return top; }
        public Joint joint() { return joint; } public String name() { return name; }
        public String target() { return target; } public String pool() { return pool; }
        public int placementPriority() { return placementPriority; }
        public int selectionPriority() { return selectionPriority; }
    }

    public static final class Bounds {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int minX() { return minX; } public int minY() { return minY; }
        public int minZ() { return minZ; } public int maxX() { return maxX; }
        public int maxY() { return maxY; } public int maxZ() { return maxZ; }
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    public static final class ValidatedGrammar {
        private final StructureSpec structure;
        private final List<PoolSpec> pools;
        private final Map<String, PoolSpec> byKey;
        private ValidatedGrammar(StructureSpec structure, List<PoolSpec> pools,
                Map<String, PoolSpec> byKey) {
            this.structure = structure; this.pools = pools; this.byKey = byKey;
        }
        public StructureSpec structure() { return structure; }
        public List<PoolSpec> pools() { return pools; }
        private Map<String, PoolSpec> byKey() { return byKey; }
    }

    public static final class StartPlan {
        private final String structureKey;
        private final int chunkX, chunkZ, blockX, blockY, blockZ;
        private final Rotation rotation;
        private final String elementKey;
        private final List<RotatedConnector> connectors;
        private final boolean present;
        private StartPlan(String structureKey, int chunkX, int chunkZ, int blockX, int blockY,
                int blockZ, Rotation rotation, String elementKey,
                List<RotatedConnector> connectors, boolean present) {
            this.structureKey = structureKey; this.chunkX = chunkX; this.chunkZ = chunkZ;
            this.blockX = blockX; this.blockY = blockY; this.blockZ = blockZ;
            this.rotation = rotation; this.elementKey = elementKey;
            this.connectors = connectors; this.present = present;
        }
        private static StartPlan empty(String key, int chunkX, int chunkZ, int y,
                Rotation rotation) {
            return new StartPlan(key, chunkX, chunkZ, Math.multiplyExact(chunkX, 16), y,
                    Math.multiplyExact(chunkZ, 16), rotation, "", List.of(), false);
        }
        public String structureKey() { return structureKey; }
        public int chunkX() { return chunkX; } public int chunkZ() { return chunkZ; }
        public int blockX() { return blockX; } public int blockY() { return blockY; }
        public int blockZ() { return blockZ; } public Rotation rotation() { return rotation; }
        public String elementKey() { return elementKey; }
        public List<RotatedConnector> connectors() { return connectors; }
        public boolean present() { return present; }
    }

    public static final class RotatedConnector {
        private final int x, y, z;
        private final Direction front, top;
        private final Joint joint;
        private final String name, target, pool;
        private final int placementPriority, selectionPriority;
        public RotatedConnector(int x, int y, int z, Direction front, Direction top, Joint joint,
                String name, String target, String pool, int placementPriority,
                int selectionPriority) {
            this.x = x; this.y = y; this.z = z; this.front = front; this.top = top;
            this.joint = joint; this.name = name; this.target = target; this.pool = pool;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        public Direction front() { return front; } public Direction top() { return top; }
        public Joint joint() { return joint; } public String name() { return name; }
        public String target() { return target; } public String pool() { return pool; }
        public int placementPriority() { return placementPriority; }
        public int selectionPriority() { return selectionPriority; }
    }

    public static final class Vec3 {
        private final int x, y, z;
        private Vec3(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
    }
}
