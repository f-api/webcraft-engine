package com.gameexpert.terrain.mc.surface;

import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.terrain.mc.Mc263NoiseRegistry;
import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.df.McDensityFunction;
import com.gameexpert.terrain.mc.df.McDensityFunctionLoader;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Vanilla surface_rule JSON을 파싱하고 ordered first-match 규칙을 평가한다. */
public final class McSurfaceRuleEngine {
    public static final int NO_WATER = Integer.MIN_VALUE;

    private static final int HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE = 8;
    private static final int SURFACE_CELL_BITS = 4;
    private static final int SURFACE_CELL_MASK = 15;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private Rule root;
    private final McRandom.PositionalFactory random;
    private final McDensityFunction preliminarySurfaceFunction;
    private final McNormalNoise surfaceSecondaryNoise;
    private final McBadlandsSurface badlands;
    private final int minY;
    private final int height;
    private final int cellHeight;
    private final Map<String, McNormalNoise> noises = new HashMap<>();
    private final Map<String, McRandom.PositionalFactory> gradientRandoms = new HashMap<>();
    private static final long PRELIMINARY_CACHE_MISS = Long.MIN_VALUE;
    private final LongOpenHashMap preliminarySurfaceCache = new LongOpenHashMap(256);

    private McSurfaceRuleEngine(Path dataRoot, long seed, Rule root,
            McDensityFunction preliminarySurfaceFunction, McNormalNoise surfaceSecondaryNoise,
            int minY, int height, int cellHeight) throws IOException {
        this.random = new McRandom(seed).forkPositional();
        this.root = root;
        this.preliminarySurfaceFunction = preliminarySurfaceFunction;
        this.surfaceSecondaryNoise = surfaceSecondaryNoise;
        this.badlands = new McBadlandsSurface(random, noise("minecraft:clay_bands_offset"));
        this.minY = minY;
        this.height = height;
        this.cellHeight = cellHeight;
    }

    public static McSurfaceRuleEngine load(Path overworldJson, long seed) throws IOException {
        JsonNode node = materialRuleTree();
        int minY = -64;
        int height = 384;
        int cellHeight = 8;
        McRandom.PositionalFactory random = new McRandom(seed).forkPositional();
        McNormalNoise secondary = Mc263NoiseRegistry.create(random,
                "minecraft:surface_secondary");
        McDensityFunction preliminary = new McDensityFunctionLoader(Path.of("."), seed)
                .preliminarySurfaceLevel();
        McSurfaceRuleEngine engine = new McSurfaceRuleEngine(Path.of("."), seed, null, preliminary, secondary,
                minY, height, cellHeight);
        engine.root = engine.parseRule(node);
        return engine;
    }

    public static McSurfaceRuleEngine load26_3(long seed) throws IOException {
        return load(Path.of("."), seed);
    }

    private static JsonNode materialRuleTree() throws IOException {
        byte[] compressed = Base64.getDecoder().decode(Mc263MaterialRuleData.GZIP_BASE64);
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            raw = gzip.readAllBytes();
        }
        try {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw));
            if (!Mc263MaterialRuleData.RAW_SHA256.equals(actual)) {
                throw new IOException("26.3 material-rule payload SHA-256 mismatch: " + actual);
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        return MAPPER.readTree(raw);
    }

    /** 일치한 result_state Name을 반환하고, 어느 규칙도 일치하지 않으면 null을 반환한다. */
    public String evaluate(McSurfaceContext context) {
        return root.evaluate(context);
    }

    /** SurfaceSystem의 column별 정수 runDepth(Java narrowing conversion)를 계산한다. */
    public int surfaceDepth(int x, int z) throws IOException {
        double value = noise("minecraft:surface").getValue(x, 0.0, z) * 2.75 + 3.0
                + random.at(x, 0, z).nextDouble() * 0.25;
        return (int) value;
    }

    /** Vanilla SurfaceSystem's independently seeded secondary-depth noise input. */
    public double secondarySurfaceNoise(int x, int z) {
        return surfaceSecondaryNoise.getValue(x, 0.0, z);
    }

    /** 26.3 find_top_surface를 quart-aligned X/Z에서 평가한다. */
    public int preliminarySurfaceLevel(int x, int z) {
        return scanPreliminarySurface(
                Math.floorDiv(x, 4) * 4,
                Math.floorDiv(z, 4) * 4);
    }

    /**
     * SurfaceRules.Context#getMinSurfaceLevel: bilinear over the four 16-block surface-cell
     * corners of {@code computePreliminarySurfaceLevel}, then {@code + surfaceDepth - 8}. This is
     * NOT the quart-aligned {@link #preliminarySurfaceLevel} the aquifer consumes.
     */
    public int minSurfaceLevel(int blockX, int blockZ, int surfaceDepth) {
        int cellX = blockX >> SURFACE_CELL_BITS;
        int cellZ = blockZ >> SURFACE_CELL_BITS;
        float corner00 = scanPreliminarySurface(cellX << SURFACE_CELL_BITS,
                cellZ << SURFACE_CELL_BITS);
        float corner10 = scanPreliminarySurface((cellX + 1) << SURFACE_CELL_BITS,
                cellZ << SURFACE_CELL_BITS);
        float corner01 = scanPreliminarySurface(cellX << SURFACE_CELL_BITS,
                (cellZ + 1) << SURFACE_CELL_BITS);
        float corner11 = scanPreliminarySurface((cellX + 1) << SURFACE_CELL_BITS,
                (cellZ + 1) << SURFACE_CELL_BITS);
        float deltaX = (blockX & SURFACE_CELL_MASK) / 16.0F;
        float deltaZ = (blockZ & SURFACE_CELL_MASK) / 16.0F;
        float interpolated = lerp(deltaZ,
                lerp(deltaX, corner00, corner10),
                lerp(deltaX, corner01, corner11));
        return floor(interpolated) + surfaceDepth
                - HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE;
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static int floor(float value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /** The engine is thread-local; retain only the current chunk's pure lookup results. */
    public void resetChunkCache() {
        preliminarySurfaceCache.clear();
    }

    private int scanPreliminarySurface(int x, int z) {
        int quartX = Math.floorDiv(x, 4) * 4;
        int quartZ = Math.floorDiv(z, 4) * 4;
        long key = ((long) quartX & 0xffffffffL) | ((long) quartZ << 32);
        long cached = preliminarySurfaceCache.get(key, PRELIMINARY_CACHE_MISS);
        if (cached != PRELIMINARY_CACHE_MISS) return (int) cached;
        int value = (int) preliminarySurfaceFunction.compute(
                new McDensityFunction.Context(quartX, 0, quartZ));
        preliminarySurfaceCache.put(key, value);
        return value;
    }

    private Rule parseRule(JsonNode node) throws IOException {
        String type = type(node);
        return switch (type) {
            case "block" -> {
                JsonNode state = required(node, "result_state", type);
                if (!state.isString()) throw malformed("26.3 result_state must be a string", state);
                yield new BlockRule(state.stringValue());
            }
            case "sequence" -> {
                JsonNode sequence = required(node, "sequence", type);
                if (!sequence.isArray()) throw malformed("sequence must be an array", node);
                List<Rule> rules = new ArrayList<>(sequence.size());
                for (JsonNode child : sequence) rules.add(parseRule(child));
                yield new SequenceRule(List.copyOf(rules));
            }
            case "condition" -> new ConditionalRule(
                    parseCondition(required(node, "if_true", type)),
                    parseRule(required(node, "then_run", type)));
            case "bandlands" -> context -> badlands.stateAt(
                    context.blockX(), context.blockY(), context.blockZ());
            default -> throw malformed("unsupported surface rule type: " + type, node);
        };
    }

    private Condition parseCondition(JsonNode node) throws IOException {
        String type = type(node);
        return switch (type) {
            case "above_preliminary_surface" ->
                    c -> c.blockY() >= minSurfaceLevel(c.blockX(), c.blockZ(), c.surfaceDepth());
            case "biome" -> biomeCondition(required(node, "biome_is", type));
            case "noise_threshold" -> noiseThreshold(node);
            case "stone_depth" -> stoneDepth(node);
            case "water" -> water(node);
            case "y_above" -> yAbove(node);
            case "steep" -> this::steep;
            case "not" -> {
                Condition inverted = parseCondition(required(node, "invert", type));
                yield c -> !inverted.test(c);
            }
            case "hole" -> c -> c.surfaceDepth() <= 0;
            case "vertical_gradient" -> verticalGradient(node);
            case "temperature" -> c -> c.adjustedTemperature() < 0.15F;
            default -> throw malformed("unsupported surface condition type: " + type, node);
        };
    }

    private Condition biomeCondition(JsonNode value) throws IOException {
        Set<String> biomes = new HashSet<>();
        if (value.isArray()) {
            for (JsonNode item : value) biomes.add(normalize(item.asString()));
        } else if (value.isString()) {
            biomes.add(normalize(value.asString()));
        } else {
            throw malformed("biome_is must be a string or array", value);
        }
        Set<String> immutable = Set.copyOf(biomes);
        return c -> immutable.contains(normalize(c.biome()));
    }

    private Condition noiseThreshold(JsonNode node) throws IOException {
        String id = text(node, "noise");
        double min = number(node, "min_threshold");
        double max = number(node, "max_threshold");
        boolean is3d = bool(node, "is_3d", false);
        McNormalNoise sampler = noise(id);
        return c -> {
            double value = sampler.getValue(c.blockX(), is3d ? c.blockY() : 0.0, c.blockZ());
            return value >= min && value <= max;
        };
    }

    private Condition stoneDepth(JsonNode node) throws IOException {
        int offset = integer(node, "offset");
        boolean addSurface = bool(node, "add_surface_depth", false);
        int secondaryRange = integer(node, "secondary_depth_range");
        boolean ceiling = "ceiling".equals(text(node, "surface_type"));
        return c -> {
            int stone = ceiling ? c.stoneDepthBelow() : c.stoneDepthAbove();
            int surface = addSurface ? c.surfaceDepth() : 0;
            int secondary = secondaryRange == 0 ? 0
                    : (int) ((c.secondarySurfaceNoise() + 1.0) * 0.5 * secondaryRange);
            return stone <= 1 + offset + surface + secondary;
        };
    }

    private Condition water(JsonNode node) throws IOException {
        int offset = integer(node, "offset");
        int multiplier = integer(node, "surface_depth_multiplier");
        boolean addStone = bool(node, "add_stone_depth", false);
        return c -> c.waterHeight() == NO_WATER
                || c.blockY() + (addStone ? c.stoneDepthAbove() : 0)
                >= c.waterHeight() + offset + c.surfaceDepth() * multiplier;
    }

    private Condition yAbove(JsonNode node) throws IOException {
        Anchor anchor = anchor(required(node, "anchor", "y_above"));
        int multiplier = integer(node, "surface_depth_multiplier");
        boolean addStone = bool(node, "add_stone_depth", false);
        return c -> c.blockY() + (addStone ? c.stoneDepthAbove() : 0)
                >= anchor.y(c) + c.surfaceDepth() * multiplier;
    }

    private Condition verticalGradient(JsonNode node) throws IOException {
        String name = text(node, "random_name");
        Anchor trueAt = anchor(required(node, "true_at_and_below", "vertical_gradient"));
        Anchor falseAt = anchor(required(node, "false_at_and_above", "vertical_gradient"));
        McRandom.PositionalFactory positional = gradientRandoms.computeIfAbsent(normalize(name),
                n -> random.fromHashOf(n).forkPositional());
        return c -> {
            int low = trueAt.y(c);
            int high = falseAt.y(c);
            if (c.blockY() <= low) return true;
            if (c.blockY() >= high) return false;
            double chance = 1.0D - (c.blockY() - low) / (double) (high - low);
            return positional.at(c.blockX(), c.blockY(), c.blockZ()).nextFloat() < chance;
        };
    }

    private boolean steep(McSurfaceContext c) {
        int lx = c.blockX() & 15;
        int lz = c.blockZ() & 15;
        int zSub = Math.max(0, lz - 1);
        int zAdd = Math.min(15, lz + 1);
        if (c.topBlockHeightExclusive(lx, zAdd)
                >= c.topBlockHeightExclusive(lx, zSub) + 4) return true;
        int xSub = Math.max(0, lx - 1);
        int xAdd = Math.min(15, lx + 1);
        return c.topBlockHeightExclusive(xSub, lz)
                >= c.topBlockHeightExclusive(xAdd, lz) + 4;
    }

    private McNormalNoise noise(String identifier) throws IOException {
        String id = normalize(identifier);
        McNormalNoise found = noises.get(id);
        if (found != null) return found;
        try {
            found = Mc263NoiseRegistry.create(random, id);
        } catch (IllegalArgumentException unsupported) {
            throw new IOException("지원하지 않는 26.3 named noise: " + id, unsupported);
        }
        noises.put(id, found);
        return found;
    }

    private static Anchor anchor(JsonNode node) throws IOException {
        if (node.has("absolute")) {
            int value = integer(node, "absolute");
            return c -> value;
        }
        if (node.has("above_bottom")) {
            int value = integer(node, "above_bottom");
            return c -> c.minY() + value;
        }
        if (node.has("below_top")) {
            int value = integer(node, "below_top");
            return c -> c.minY() + c.height() - 1 - value;
        }
        throw malformed("unknown vertical anchor", node);
    }

    private static String type(JsonNode node) throws IOException {
        String value = text(node, "type");
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static String normalize(String id) {
        return id.startsWith("minecraft:") ? id : "minecraft:" + id;
    }

    private static JsonNode required(JsonNode node, String field, String owner) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw malformed(owner + " missing " + field, node);
        return value;
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = required(node, field, "node");
        if (!value.isString()) throw malformed(field + " must be text", node);
        return value.asString();
    }

    private static double number(JsonNode node, String field) throws IOException {
        JsonNode value = required(node, field, "node");
        if (!value.isNumber()) throw malformed(field + " must be numeric", node);
        return value.doubleValue();
    }

    private static int integer(JsonNode node, String field) throws IOException {
        JsonNode value = required(node, field, "node");
        if (!value.isIntegralNumber()) throw malformed(field + " must be integral", node);
        return value.intValue();
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) throws IOException {
        JsonNode value = node.get(field);
        if (value == null) return fallback;
        if (!value.isBoolean()) throw malformed(field + " must be boolean", node);
        return value.booleanValue();
    }

    private static IOException malformed(String message, JsonNode node) {
        return new IOException(message + ": " + node);
    }

    private interface Rule { String evaluate(McSurfaceContext context); }
    private interface Condition { boolean test(McSurfaceContext context); }
    private interface Anchor { int y(McSurfaceContext context); }

    private static final class BlockRule implements Rule {
        private final String block;
        private BlockRule(String block) { this.block = block; }
        @Override public String evaluate(McSurfaceContext context) { return block; }
    }

    private static final class SequenceRule implements Rule {
        private final List<Rule> rules;
        private SequenceRule(List<Rule> rules) { this.rules = rules; }
        @Override public String evaluate(McSurfaceContext context) {
            for (Rule rule : rules) {
                String block = rule.evaluate(context);
                if (block != null) return block;
            }
            return null;
        }
    }

    private static final class ConditionalRule implements Rule {
        private final Condition condition;
        private final Rule thenRun;
        private ConditionalRule(Condition condition, Rule thenRun) {
            this.condition = condition;
            this.thenRun = thenRun;
        }
        @Override public String evaluate(McSurfaceContext context) {
            return condition.test(context) ? thenRun.evaluate(context) : null;
        }
    }
}
