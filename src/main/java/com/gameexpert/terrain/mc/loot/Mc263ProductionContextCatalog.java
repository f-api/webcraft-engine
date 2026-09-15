package com.gameexpert.terrain.mc.loot;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton;
import com.gameexpert.terrain.mc.feature.Mc263FeaturesRegion;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedMap;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.ProductionContext;
import com.gameexpert.world.WorldBaseline;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict reader and provider for the future pinned 26.3 production-loot context catalog.
 *
 * <p>The catalog is deliberately unusable without a separately pinned {@link PinnedIdentity}.
 * A digest or source identity read from the resource itself is not authority. After the external
 * byte identity has matched, this reader also requires one canonical JSON representation, an
 * exact closed schema, the external source identity, the aggregate catalog seal, every context
 * row receipt, every full {@link ProductionContext} receipt, and every exact map-row receipt.</p>
 *
 * <p>No production resource is shipped by this class. The official-oracle publication lane owns
 * {@link #RESOURCE} and its external pin. Until both exist, loading or omitting this provider
 * fails closed.</p>
 */
public final class Mc263ProductionContextCatalog {
    public static final String RESOURCE = "/mc263/production-context-catalog-v3.json";
    public static final String SCHEMA = "mc263-production-context-catalog-v3";

    private static final int MAX_RESOURCE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_CONTEXTS = 1_048_576;
    private static final int MAX_MAPS = 64;
    private static final String ROW_RECEIPT_DOMAIN =
            "MC263-PRODUCTION-CONTEXT-CATALOG-ROW-V1\0";
    private static final String CATALOG_SEAL_DOMAIN =
            "MC263-PRODUCTION-CONTEXT-CATALOG-SEAL-V1\0";
    private static final String AUTHORITY_RECEIPT_DOMAIN =
            "MC263-PRODUCTION-CONTEXT-AUTHORITY-V1\0";
    private static final HexFormat HEX = HexFormat.of();
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final Set<String> ROOT_KEYS = Set.of(
            "catalogSeal", "contexts", "locatorSourceReceipt", "previewSourceReceipt",
            "schema", "sourceIdentity");
    private static final Set<String> CONTEXT_KEYS = Set.of(
            "biome", "contextReceipt", "maps", "originX", "originY", "originZ",
            "rowReceipt", "sourceIdentity", "tableIdentity", "worldIdentity");
    private static final Set<String> FOUND_MAP_KEYS = Set.of(
            "binding", "previewColorsBase64", "previewSha256", "savedCenterX",
            "savedCenterZ", "status", "targetReceipt", "targetX", "targetZ");
    private static final Set<String> NOT_FOUND_MAP_KEYS = Set.of(
            "binding", "status", "targetReceipt");
    private static final Set<String> BINDING_KEYS = Set.of(
            "acceptedMembers", "destination", "destinationTag", "locatorSourceReceipt",
            "referenceSnapshotReceipt",
            "originX", "originY", "originZ", "scale", "searchRadius",
            "skipExistingChunks", "sourceIdentity", "structureSet", "tableIdentity",
            "worldIdentity");
    private Mc263ProductionContextCatalog() { }

    /** External, non-resource authority over the exact catalog publication. */
    public record PinnedIdentity(int canonicalBytes, String canonicalSha256,
                                 String sourceIdentity, String previewSourceReceipt,
                                 String locatorSourceReceipt, String catalogSeal) {
        public PinnedIdentity {
            if (canonicalBytes <= 0 || canonicalBytes > MAX_RESOURCE_BYTES) {
                throw new IllegalArgumentException(
                        "production context catalog byte pin is outside bounds");
            }
            canonicalSha256 = requireSha256(canonicalSha256,
                    "production context catalog byte pin");
            sourceIdentity = requireSha256(sourceIdentity,
                    "production context catalog source identity pin");
            previewSourceReceipt = requireSha256(previewSourceReceipt,
                    "production context catalog preview source pin");
            locatorSourceReceipt = requireSha256(locatorSourceReceipt,
                    "production context catalog locator source pin");
            catalogSeal = requireSha256(catalogSeal,
                    "production context catalog seal pin");
        }
    }

    /**
     * Immutable catalog provider. Implementations are sealed here so production cannot substitute
     * a lambda that self-attests rows or invents a default map.
     */
    public sealed interface Provider
            permits ImmutableProvider, RuntimeProvider, CompositeProvider {
        /** Mints a fresh authority for exactly one upstream product target. */
        Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authorityFor(
                int worldSeed, int targetChunkX, int targetChunkZ);

        /**
         * 이 공급자가 해당 타깃 청크의 활성 소스 폐포를 실제로 가지고 있는지 여부.
         * 좌표 자체가 규범 밖이면 {@code authorityFor} 와 동일하게 예외로 실패한다.
         */
        boolean coversTarget(int worldSeed, int targetChunkX, int targetChunkZ);

        /** Exact authenticated row lookup, exposed for durable-caller validation and tests. */
        LootProductionContext requireContext(long worldSeed, String sourceIdentity,
                String tableIdentity, int originX, int originY, int originZ);

        String sourceIdentity();
        String catalogSeal();
        String canonicalSha256();
        int contextCount();
        List<LootProductionContext> contexts();
    }

    /** Loads the future production resource under a separately supplied pin. */
    public static Provider loadPinned(PinnedIdentity pin) {
        return loadResource(Mc263ProductionContextCatalog.class, RESOURCE, pin);
    }

    /**
     * Deterministic runtime provider for the playable-core delivery while the official exhaustive
     * context catalog remains deferred. It binds every emitted row to the current generator source
     * identity and exact live biome/origin; map-sensitive Camp rows still fail closed.
     */
    public static Provider playableProvider() {
        throw new IllegalStateException(
                "production context provider requires an authenticated preview renderer");
    }

    /** Arbitrary-seed production provider; the locator carries mandatory memoization. */
    public static Provider liveProvider(Mc263LocatedMapAuthority locatedMaps) {
        return new RuntimeProvider(Objects.requireNonNull(locatedMaps, "located-map authority"));
    }

    /**
     * 고정 발행 카탈로그를 우선하고, 그 카탈로그가 폐포를 갖지 않는 타깃에서만 임의 시드
     * 런타임 공급자로 넘긴다. 고정 카탈로그는 단일 월드 시드만 담고 있으므로, 이 합성이
     * 없으면 그 시드 밖의 모든 월드가 청크 생산 첫 요청에서 닫혀 입장 자체가 막힌다.
     */
    public static Provider pinnedOrLive(Provider pinned, Provider live) {
        return new CompositeProvider(
                Objects.requireNonNull(pinned, "pinned production context provider"),
                Objects.requireNonNull(live, "live production context provider"));
    }

    /** Loads one absolute classpath resource without any fallback path. */
    public static Provider loadResource(Class<?> anchor, String resource,
            PinnedIdentity pin) {
        Objects.requireNonNull(anchor, "production context catalog resource anchor");
        Objects.requireNonNull(pin, "production context catalog external pin");
        if (resource == null || !resource.startsWith("/") || resource.contains("..")) {
            throw new IllegalArgumentException(
                    "production context catalog resource path must be absolute and canonical");
        }
        try (InputStream input = anchor.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException(
                        "missing pinned production context catalog resource: " + resource);
            }
            byte[] bytes = input.readNBytes(MAX_RESOURCE_BYTES + 1);
            if (bytes.length > MAX_RESOURCE_BYTES || input.read() != -1) {
                throw new IllegalStateException(
                        "production context catalog resource exceeds byte bound");
            }
            return parse(bytes, pin);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "cannot read pinned production context catalog resource: " + resource,
                    failure);
        }
    }

    /** Parses exact canonical bytes only after their independent publication pin matches. */
    public static Provider parse(byte[] canonicalBytes, PinnedIdentity pin) {
        canonicalBytes = Objects.requireNonNull(canonicalBytes,
                "production context catalog canonical bytes").clone();
        Objects.requireNonNull(pin, "production context catalog external pin");
        if (canonicalBytes.length != pin.canonicalBytes()) {
            throw invalid("production context catalog byte count does not match external pin");
        }
        String actualSha = sha256(canonicalBytes);
        if (!secureEquals(actualSha, pin.canonicalSha256())) {
            throw invalid("production context catalog canonical bytes are stale");
        }
        if (canonicalBytes.length == 0 || canonicalBytes[canonicalBytes.length - 1] != '\n'
                || contains(canonicalBytes, (byte) '\r')
                || contains(canonicalBytes, (byte) 0)) {
            throw invalid("production context catalog byte envelope is noncanonical");
        }

        String document = decodeUtf8(canonicalBytes);
        String body = document.substring(0, document.length() - 1);
        if (body.indexOf('\n') >= 0 || body.isEmpty()) {
            throw invalid("production context catalog must be one canonical JSON line");
        }

        final JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException failure) {
            throw invalid("production context catalog JSON is malformed", failure);
        }
        requireObject(root, ROOT_KEYS, "production context catalog root");
        if (!SCHEMA.equals(text(root, "schema", "production context catalog root"))) {
            throw invalid("production context catalog schema drift");
        }
        String sourceIdentity = requireSha256(
                text(root, "sourceIdentity", "production context catalog root"),
                "production context catalog source identity");
        if (!secureEquals(sourceIdentity, pin.sourceIdentity())) {
            throw invalid("production context catalog source identity is stale");
        }
        String previewSourceReceipt = requireSha256(
                text(root, "previewSourceReceipt", "production context catalog root"),
                "production context catalog preview source receipt");
        String locatorSourceReceipt = requireSha256(
                text(root, "locatorSourceReceipt", "production context catalog root"),
                "production context catalog locator source receipt");
        if (!secureEquals(previewSourceReceipt, pin.previewSourceReceipt())
                || !secureEquals(previewSourceReceipt,
                        Mc263LocatedMapAuthority.OFFICIAL_PREVIEW_SOURCE_RECEIPT)
                || !secureEquals(locatorSourceReceipt, pin.locatorSourceReceipt())) {
            throw invalid("production context catalog preview/locator authority is stale");
        }
        String suppliedSeal = requireSha256(
                text(root, "catalogSeal", "production context catalog root"),
                "production context catalog seal");
        if (!secureEquals(suppliedSeal, pin.catalogSeal())) {
            throw invalid("production context catalog seal does not match external pin");
        }

        String canonical = canonicalJson(root) + "\n";
        if (!document.equals(canonical)
                || !Arrays.equals(canonicalBytes, canonical.getBytes(StandardCharsets.UTF_8))) {
            throw invalid("production context catalog JSON bytes are not canonical");
        }
        String expectedSeal = catalogSeal(root);
        if (!secureEquals(suppliedSeal, expectedSeal)) {
            throw invalid("stale production context catalog seal");
        }

        JsonNode rows = root.get("contexts");
        if (rows == null || !rows.isArray() || rows.isEmpty() || rows.size() > MAX_CONTEXTS) {
            throw invalid("production context catalog contexts are outside bounds");
        }
        LinkedHashMap<ContextKey, LocatedProductionContext> contexts = new LinkedHashMap<>();
        ContextKey previous = null;
        for (int index = 0; index < rows.size(); index++) {
            ParsedContext parsed = parseContext(rows.get(index), sourceIdentity,
                    locatorSourceReceipt, index);
            if (previous != null && previous.compareTo(parsed.key()) >= 0) {
                throw invalid("production context catalog rows are duplicate or unsorted at "
                        + index);
            }
            if (contexts.putIfAbsent(parsed.key(), parsed.context()) != null) {
                throw invalid("duplicate production context catalog entry at " + index);
            }
            previous = parsed.key();
        }
        return new ImmutableProvider(pin.canonicalSha256(), sourceIdentity, suppliedSeal,
                contexts);
    }

    private static ParsedContext parseContext(JsonNode row, String catalogSource,
            String catalogLocatorReceipt, int index) {
        String label = "production context catalog row[" + index + "]";
        requireObject(row, CONTEXT_KEYS, label);
        String biome = minecraftKey(text(row, "biome", label), label + " biome");
        String worldIdentity = canonicalWorldIdentity(text(row, "worldIdentity", label), label);
        String sourceIdentity = requireSha256(text(row, "sourceIdentity", label),
                label + " source identity");
        if (!secureEquals(sourceIdentity, catalogSource)) {
            throw invalid(label + " mixes another source identity");
        }
        String tableIdentity = minecraftKey(text(row, "tableIdentity", label),
                label + " table identity");
        int originX = integer(row, "originX", label);
        int originY = integer(row, "originY", label);
        int originZ = integer(row, "originZ", label);
        if (originY < Blocks.MIN_Y || originY > Blocks.MAX_Y) {
            throw invalid(label + " origin Y is outside the canonical build height");
        }
        String contextReceipt = requireSha256(text(row, "contextReceipt", label),
                label + " full context receipt");
        String rowReceipt = requireSha256(text(row, "rowReceipt", label),
                label + " row receipt");

        JsonNode mapRows = row.get("maps");
        if (mapRows == null || !mapRows.isArray() || mapRows.size() > MAX_MAPS) {
            throw invalid(label + " maps are outside bounds");
        }
        LinkedHashMap<String, Mc263LocatedMapAuthority.LocatedMapTarget> maps =
                new LinkedHashMap<>();
        String previousDestination = null;
        for (int mapIndex = 0; mapIndex < mapRows.size(); mapIndex++) {
            JsonNode mapRow = mapRows.get(mapIndex);
            String mapLabel = label + ".maps[" + mapIndex + "]";
            String status = text(mapRow, "status", mapLabel);
            if ("FOUND".equals(status)) {
                requireObject(mapRow, FOUND_MAP_KEYS, mapLabel);
            } else if ("NOT_FOUND".equals(status)) {
                requireObject(mapRow, NOT_FOUND_MAP_KEYS, mapLabel);
            } else {
                throw invalid(mapLabel + " has an unknown target status");
            }
            JsonNode bindingRow = mapRow.get("binding");
            requireObject(bindingRow, BINDING_KEYS, mapLabel + ".binding");
            String destination = destination(
                    text(bindingRow, "destination", mapLabel), mapLabel);
            if (previousDestination != null
                    && previousDestination.compareTo(destination) >= 0) {
                throw invalid(label + " maps are duplicate or unsorted");
            }
            String mapWorld = canonicalWorldIdentity(
                    text(bindingRow, "worldIdentity", mapLabel), mapLabel);
            String mapSource = requireSha256(text(bindingRow, "sourceIdentity", mapLabel),
                    mapLabel + " source identity");
            String mapTable = minecraftKey(text(bindingRow, "tableIdentity", mapLabel),
                    mapLabel + " table identity");
            int mapOriginX = integer(bindingRow, "originX", mapLabel);
            int mapOriginY = integer(bindingRow, "originY", mapLabel);
            int mapOriginZ = integer(bindingRow, "originZ", mapLabel);
            if (!worldIdentity.equals(mapWorld) || !sourceIdentity.equals(mapSource)
                    || !tableIdentity.equals(mapTable) || originX != mapOriginX
                    || originY != mapOriginY || originZ != mapOriginZ) {
                throw invalid(mapLabel + " is mixed with another world/source/table/origin");
            }
            String locatorReceipt = requireSha256(
                    text(bindingRow, "locatorSourceReceipt", mapLabel),
                    mapLabel + " locator source receipt");
            if (!secureEquals(locatorReceipt, catalogLocatorReceipt)) {
                throw invalid(mapLabel + " locator source receipt drift");
            }
            String referenceSnapshotReceipt = requireSha256(
                    text(bindingRow, "referenceSnapshotReceipt", mapLabel),
                    mapLabel + " reference snapshot receipt");
            JsonNode membersRow = bindingRow.get("acceptedMembers");
            if (membersRow == null || !membersRow.isArray() || membersRow.isEmpty()
                    || membersRow.size() > MAX_MAPS) {
                throw invalid(mapLabel + " accepted members outside bounds");
            }
            ArrayList<String> members = new ArrayList<>();
            for (int member = 0; member < membersRow.size(); member++) {
                if (!membersRow.get(member).isString()) {
                    throw invalid(mapLabel + " accepted member is not text");
                }
                members.add(minecraftKey(membersRow.get(member).asString(), mapLabel));
            }
            if (!bindingRow.get("skipExistingChunks").isBoolean()
                    || !bindingRow.get("skipExistingChunks").asBoolean()) {
                throw invalid(mapLabel + " skipExistingChunks drift");
            }
            var binding = new Mc263LocatedMapAuthority.Binding(destination,
                    text(bindingRow, "destinationTag", mapLabel),
                    minecraftKey(text(bindingRow, "structureSet", mapLabel), mapLabel),
                    members, mapWorld, mapSource, mapTable, mapOriginX, mapOriginY, mapOriginZ,
                    integer(bindingRow, "scale", mapLabel),
                    integer(bindingRow, "searchRadius", mapLabel), true, locatorReceipt,
                    referenceSnapshotReceipt);
            Mc263LocatedMapAuthority.LocatedMapTarget target;
            if ("FOUND".equals(status)) {
                String previewText = text(mapRow, "previewColorsBase64", mapLabel);
                byte[] preview;
                try {
                    preview = Base64.getDecoder().decode(previewText);
                } catch (IllegalArgumentException invalidBase64) {
                    throw invalid(mapLabel + " preview base64 drift", invalidBase64);
                }
                String previewSha = requireSha256(text(mapRow, "previewSha256", mapLabel),
                        mapLabel + " preview receipt");
                if (preview.length != Mc263LocatedMapAuthority.PREVIEW_COLOR_COUNT
                        || !Base64.getEncoder().encodeToString(preview).equals(previewText)
                        || !secureEquals(sha256(preview), previewSha)) {
                    throw invalid(mapLabel + " preview bytes drift");
                }
                target = new Mc263LocatedMapAuthority.Found(binding,
                        integer(mapRow, "targetX", mapLabel),
                        integer(mapRow, "targetZ", mapLabel),
                        integer(mapRow, "savedCenterX", mapLabel),
                        integer(mapRow, "savedCenterZ", mapLabel), previewSha,
                        requireSha256(text(mapRow, "targetReceipt", mapLabel),
                                mapLabel + " target receipt"));
            } else {
                target = new Mc263LocatedMapAuthority.NotFound(binding,
                        requireSha256(text(mapRow, "targetReceipt", mapLabel),
                                mapLabel + " target receipt"));
            }
            if (maps.putIfAbsent(destination, target) != null) {
                throw invalid("duplicate production context map destination: " + destination);
            }
            previousDestination = destination;
        }

        LocatedProductionContext context = LocatedProductionContext.authenticated(
                biome, maps, worldIdentity,
                sourceIdentity, tableIdentity, originX, originY, originZ, contextReceipt);
        String expectedRowReceipt = rowReceipt(context);
        if (!secureEquals(rowReceipt, expectedRowReceipt)) {
            throw invalid("stale production context catalog row receipt at " + index);
        }
        ContextKey key = new ContextKey(Long.parseLong(worldIdentity), sourceIdentity,
                tableIdentity, originX, originY, originZ);
        return new ParsedContext(key, context);
    }

    private static final class ImmutableProvider implements Provider {
        private final String canonicalSha256;
        private final String sourceIdentity;
        private final String catalogSeal;
        private final Map<ContextKey, LocatedProductionContext> contexts;
        private final Map<TargetKey, Map<ContextKey, LocatedProductionContext>> contextsBySource;
        private final List<LocatedProductionContext> orderedContexts;

        private ImmutableProvider(String canonicalSha256, String sourceIdentity,
                String catalogSeal,
                LinkedHashMap<ContextKey, LocatedProductionContext> contexts) {
            this.canonicalSha256 = canonicalSha256;
            this.sourceIdentity = sourceIdentity;
            this.catalogSeal = catalogSeal;
            this.contexts = Collections.unmodifiableMap(new LinkedHashMap<>(contexts));
            this.orderedContexts = List.copyOf(contexts.values());
            LinkedHashMap<TargetKey,
                    LinkedHashMap<ContextKey, LocatedProductionContext>> grouped =
                    new LinkedHashMap<>();
            for (Map.Entry<ContextKey, LocatedProductionContext> entry : contexts.entrySet()) {
                ContextKey key = entry.getKey();
                TargetKey source = new TargetKey(key.worldSeed(),
                        Math.floorDiv(key.originX(), Blocks.CHUNK_X),
                        Math.floorDiv(key.originZ(), Blocks.CHUNK_Z));
                grouped.computeIfAbsent(source, ignored -> new LinkedHashMap<>())
                        .put(key, entry.getValue());
            }
            LinkedHashMap<TargetKey, Map<ContextKey, LocatedProductionContext>> frozen =
                    new LinkedHashMap<>();
            grouped.forEach((key, value) -> frozen.put(key,
                    Collections.unmodifiableMap(new LinkedHashMap<>(value))));
            this.contextsBySource = Collections.unmodifiableMap(frozen);
        }

        @Override
        public boolean coversTarget(int worldSeed, int targetChunkX, int targetChunkZ) {
            requireTargetCoordinate(targetChunkX, Blocks.CHUNK_X, "X");
            requireTargetCoordinate(targetChunkZ, Blocks.CHUNK_Z, "Z");
            return !activeContexts(new TargetKey(worldSeed, targetChunkX, targetChunkZ)).isEmpty();
        }

        @Override
        public Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authorityFor(
                int worldSeed, int targetChunkX, int targetChunkZ) {
            requireTargetCoordinate(targetChunkX, Blocks.CHUNK_X, "X");
            requireTargetCoordinate(targetChunkZ, Blocks.CHUNK_Z, "Z");
            TargetKey target = new TargetKey(worldSeed, targetChunkX, targetChunkZ);
            Map<ContextKey, LocatedProductionContext> activeContexts = activeContexts(target);
            if (activeContexts.isEmpty()) {
                throw invalid("production context catalog has no authenticated active "
                        + "nine-source closure for "
                        + worldSeed + "/" + targetChunkX + "," + targetChunkZ);
            }
            byte[] receipt = authorityReceipt(catalogSeal, sourceIdentity, target);
            return Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority.authenticated(
                    receipt, (region, blockX, blockY, blockZ, tableIdentity) -> {
                        if (region.targetChunkX() != targetChunkX
                                || region.targetChunkZ() != targetChunkZ) {
                            throw invalid(
                                    "production context authority received a foreign target chunk");
                        }
                        requireActiveOutput(region, blockX, blockY, blockZ, target);
                        ContextKey key = new ContextKey(target.worldSeed(), sourceIdentity,
                                tableIdentity, blockX, blockY, blockZ);
                        LocatedProductionContext context = activeContexts.get(key);
                        if (context == null) {
                            throw invalid(
                                    "production context request is outside the authenticated "
                                            + "active nine-source closure");
                        }
                        context.requireMatches(target.worldSeed(), tableIdentity, blockX, blockY,
                                blockZ);
                        return context;
                    });
        }

        private Map<ContextKey, LocatedProductionContext> activeContexts(TargetKey target) {
            LinkedHashMap<ContextKey, LocatedProductionContext> active = new LinkedHashMap<>();
            for (int offsetX = -Mc263FeaturesRegion.INPUT_RADIUS;
                    offsetX <= Mc263FeaturesRegion.INPUT_RADIUS; offsetX++) {
                for (int offsetZ = -Mc263FeaturesRegion.INPUT_RADIUS;
                        offsetZ <= Mc263FeaturesRegion.INPUT_RADIUS; offsetZ++) {
                    TargetKey source = new TargetKey(target.worldSeed(),
                            Math.toIntExact((long) target.chunkX() + offsetX),
                            Math.toIntExact((long) target.chunkZ() + offsetZ));
                    Map<ContextKey, LocatedProductionContext> rows = contextsBySource.get(source);
                    if (rows != null) active.putAll(rows);
                }
            }
            return Collections.unmodifiableMap(active);
        }

        @Override
        public LocatedProductionContext requireContext(long worldSeed,
                String requestedSourceIdentity,
                String tableIdentity, int originX, int originY, int originZ) {
            requestedSourceIdentity = requireSha256(requestedSourceIdentity,
                    "production context lookup source identity");
            tableIdentity = minecraftKey(tableIdentity,
                    "production context lookup table identity");
            ContextKey key = new ContextKey(worldSeed, requestedSourceIdentity, tableIdentity,
                    originX, originY, originZ);
            LocatedProductionContext context = contexts.get(key);
            if (context == null) {
                throw invalid("missing exact production context for world/source/table/origin");
            }
            context.requireMatches(worldSeed, tableIdentity, originX, originY, originZ);
            return context;
        }

        @Override public String sourceIdentity() { return sourceIdentity; }
        @Override public String catalogSeal() { return catalogSeal; }
        @Override public String canonicalSha256() { return canonicalSha256; }
        @Override public int contextCount() { return contexts.size(); }
        @Override public List<LootProductionContext> contexts() {
            return List.copyOf(orderedContexts);
        }
    }

    private static final class RuntimeProvider implements Provider {
        private static final List<String> COMMON_DESTINATIONS = List.of("bamboo_jungle",
                "birch_forest", "cherry_grove", "dappled_forest", "flower_forest",
                "pale_garden", "swamp", "windswept_forest");
        private static final List<String> SECRET_DESTINATIONS = List.of("ancient_city",
                "desert_pyramid", "jungle_temple", "mineshaft", "ocean_ruin_warm",
                "trial_chambers", "woodland_mansion");
        private final Mc263LocatedMapAuthority locatedMaps;
        private final Map<ContextKey, LocatedProductionContext> contexts = new LinkedHashMap<>();
        private Integer boundWorldSeed;

        private RuntimeProvider(Mc263LocatedMapAuthority locatedMaps) {
            this.locatedMaps = locatedMaps;
        }

        @Override
        public synchronized boolean coversTarget(int worldSeed, int targetChunkX,
                int targetChunkZ) {
            requireTargetCoordinate(targetChunkX, Blocks.CHUNK_X, "X");
            requireTargetCoordinate(targetChunkZ, Blocks.CHUNK_Z, "Z");
            // 런타임 공급자는 임의 시드를 생성하지만 인스턴스당 시드 하나에만 결속된다.
            return boundWorldSeed == null || boundWorldSeed == worldSeed;
        }

        @Override
        public Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authorityFor(
                int worldSeed, int targetChunkX, int targetChunkZ) {
            requireTargetCoordinate(targetChunkX, Blocks.CHUNK_X, "X");
            requireTargetCoordinate(targetChunkZ, Blocks.CHUNK_Z, "Z");
            TargetKey target = new TargetKey(worldSeed, targetChunkX, targetChunkZ);
            Mc263LocatedMapAuthority.LocatedMapSession locatedMapSession;
            synchronized (this) {
                if (boundWorldSeed == null) {
                    boundWorldSeed = worldSeed;
                } else if (boundWorldSeed != worldSeed) {
                    throw invalid("runtime production context world seed changed");
                }
                locatedMapSession = locatedMaps.openSession(worldSeed);
            }
            return Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority.authenticated(
                    authorityReceipt(catalogSeal(), sourceIdentity(), target),
                    (region, blockX, blockY, blockZ, tableIdentity) -> {
                        if (region.targetChunkX() != targetChunkX
                                || region.targetChunkZ() != targetChunkZ) {
                            throw invalid("runtime production context received a foreign target");
                        }
                        requireActiveOutput(region, blockX, blockY, blockZ, target);
                        tableIdentity = minecraftKey(tableIdentity,
                                "runtime production context table identity");
                        ContextKey contextKey = new ContextKey(worldSeed, sourceIdentity(),
                                tableIdentity, blockX, blockY, blockZ);
                        synchronized (this) {
                            LocatedProductionContext existing = contexts.get(contextKey);
                            if (existing != null) return existing;
                        }
                        String biome = region.biomeKey(blockX, blockY, blockZ);
                        String worldIdentity = Long.toString(worldSeed);
                        List<String> destinations = switch (tableIdentity) {
                            case "minecraft:chests/abandoned_camp_common_chest" ->
                                    COMMON_DESTINATIONS;
                            case "minecraft:chests/abandoned_camp_secret_chest" ->
                                    SECRET_DESTINATIONS;
                            default -> List.of();
                        };
                        Map<String, Mc263LocatedMapAuthority.LocatedMapTarget> maps =
                                locatedMapSession.locateAll(sourceIdentity(), tableIdentity,
                                        blockX, blockY, blockZ, destinations);
                        String receipt = Mc263ContainerLootResolver.targetProductionContextReceipt(
                                biome, worldIdentity, sourceIdentity(), tableIdentity,
                                blockX, blockY, blockZ, maps);
                        LocatedProductionContext context = LocatedProductionContext.authenticated(
                                biome, maps, worldIdentity, sourceIdentity(), tableIdentity,
                                blockX, blockY, blockZ, receipt);
                        synchronized (this) {
                            LocatedProductionContext existing = contexts.putIfAbsent(
                                    contextKey, context);
                            return existing == null ? context : existing;
                        }
                    });
        }

        @Override
        public synchronized LocatedProductionContext requireContext(long worldSeed,
                String requestedSourceIdentity, String tableIdentity,
                int originX, int originY, int originZ) {
            ContextKey key = new ContextKey(worldSeed,
                    requireSha256(requestedSourceIdentity,
                            "runtime production context source identity"),
                    minecraftKey(tableIdentity, "runtime production context table identity"),
                    originX, originY, originZ);
            LocatedProductionContext context = contexts.get(key);
            if (context == null) {
                throw invalid("runtime production context has not been emitted for this origin");
            }
            return context;
        }

        @Override public String sourceIdentity() {
            return WorldBaseline.GENERATOR_SOURCE_SHA256;
        }
        @Override public String catalogSeal() {
            return sha256(("playable-core-production-context-v1\0" + sourceIdentity())
                    .getBytes(StandardCharsets.US_ASCII));
        }
        @Override public String canonicalSha256() { return sourceIdentity(); }
        @Override public synchronized int contextCount() { return contexts.size(); }
        @Override public synchronized List<LootProductionContext> contexts() {
            return contexts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .<LootProductionContext>map(Map.Entry::getValue).toList();
        }
    }

    private static final class CompositeProvider implements Provider {
        private final Provider pinned;
        private final Provider live;

        private CompositeProvider(Provider pinned, Provider live) {
            this.pinned = pinned;
            this.live = live;
        }

        private Provider providerFor(int worldSeed, int targetChunkX, int targetChunkZ) {
            return pinned.coversTarget(worldSeed, targetChunkX, targetChunkZ) ? pinned : live;
        }

        @Override
        public boolean coversTarget(int worldSeed, int targetChunkX, int targetChunkZ) {
            return pinned.coversTarget(worldSeed, targetChunkX, targetChunkZ)
                    || live.coversTarget(worldSeed, targetChunkX, targetChunkZ);
        }

        @Override
        public Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authorityFor(
                int worldSeed, int targetChunkX, int targetChunkZ) {
            return providerFor(worldSeed, targetChunkX, targetChunkZ)
                    .authorityFor(worldSeed, targetChunkX, targetChunkZ);
        }

        @Override
        public LootProductionContext requireContext(long worldSeed, String sourceIdentity,
                String tableIdentity, int originX, int originY, int originZ) {
            // 행은 두 공급자 중 실제로 그 타깃을 생산한 쪽에만 존재한다. 고정 카탈로그를
            // 먼저 조회하고, 거기에 없을 때만 런타임 공급자가 발행한 행을 찾는다.
            try {
                return pinned.requireContext(worldSeed, sourceIdentity, tableIdentity,
                        originX, originY, originZ);
            } catch (IllegalArgumentException missingFromPinnedCatalog) {
                return live.requireContext(worldSeed, sourceIdentity, tableIdentity,
                        originX, originY, originZ);
            }
        }

        @Override public String sourceIdentity() { return pinned.sourceIdentity(); }
        @Override public String catalogSeal() { return pinned.catalogSeal(); }
        @Override public String canonicalSha256() { return pinned.canonicalSha256(); }
        @Override public int contextCount() { return pinned.contextCount() + live.contextCount(); }

        @Override public List<LootProductionContext> contexts() {
            ArrayList<LootProductionContext> all = new ArrayList<>(pinned.contexts());
            all.addAll(live.contexts());
            return List.copyOf(all);
        }
    }

    /** Package-visible receipt helper for the oracle publisher and focused fixture builder. */
    static String rowReceipt(LootProductionContext context) {
        Objects.requireNonNull(context, "production context catalog row");
        context.requireAuthenticatedContext();
        return digest(out -> {
            out.write(ROW_RECEIPT_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            out.write(HEX.parseHex(requireSha256(context.catalogReceipt(),
                    "production context full receipt")));
        });
    }

    /** Package-visible helper for independently constructing canonical test publications. */
    static String catalogSealForUnsignedCanonicalJson(byte[] unsignedCanonicalJson) {
        byte[] owned = Objects.requireNonNull(unsignedCanonicalJson,
                "unsigned production context catalog bytes").clone();
        return digest(out -> {
            out.write(CATALOG_SEAL_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            out.writeInt(owned.length);
            out.write(owned);
        });
    }

    private static String catalogSeal(JsonNode root) {
        StringBuilder unsigned = new StringBuilder();
        unsigned.append("{\"contexts\":");
        canonical(root.get("contexts"), unsigned);
        unsigned.append(",\"locatorSourceReceipt\":");
        canonical(root.get("locatorSourceReceipt"), unsigned);
        unsigned.append(",\"previewSourceReceipt\":");
        canonical(root.get("previewSourceReceipt"), unsigned);
        unsigned.append(",\"schema\":");
        canonical(root.get("schema"), unsigned);
        unsigned.append(",\"sourceIdentity\":");
        canonical(root.get("sourceIdentity"), unsigned);
        unsigned.append('}');
        return catalogSealForUnsignedCanonicalJson(
                unsigned.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] authorityReceipt(String catalogSeal, String sourceIdentity,
            TargetKey target) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(AUTHORITY_RECEIPT_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            out.write(HEX.parseHex(catalogSeal));
            out.write(HEX.parseHex(sourceIdentity));
            out.writeLong(target.worldSeed());
            out.writeInt(target.chunkX());
            out.writeInt(target.chunkZ());
            out.flush();
            byte[] receipt = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            if (allZero(receipt)) {
                throw new IllegalStateException("production context authority receipt is zero");
            }
            return receipt;
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                    "production context authority receipt computation failed", impossible);
        }
    }

    private static String canonicalJson(JsonNode value) {
        StringBuilder result = new StringBuilder();
        canonical(value, result);
        return result.toString();
    }

    private static void canonical(JsonNode value, StringBuilder output) {
        if (value == null) throw invalid("production context catalog contains a missing value");
        if (value.isObject()) {
            output.append('{');
            // 정본 발행물은 binding 을 포함한 모든 객체를 키 사전순으로만 직렬화한다.
            ArrayList<String> names = new ArrayList<>();
            names.addAll(value.propertyNames());
            names.sort(Comparator.naturalOrder());
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) output.append(',');
                appendJsonString(output, names.get(index));
                output.append(':');
                canonical(value.get(names.get(index)), output);
            }
            output.append('}');
        } else if (value.isArray()) {
            output.append('[');
            for (int index = 0; index < value.size(); index++) {
                if (index > 0) output.append(',');
                canonical(value.get(index), output);
            }
            output.append(']');
        } else if (value.isString()) {
            appendJsonString(output, value.asString());
        } else if (value.isIntegralNumber()) {
            output.append(value.bigIntegerValue());
        } else if (value.isBoolean()) {
            // 카탈로그 binding 행의 skipExistingChunks 는 JSON 불리언으로 발행된다.
            output.append(value.booleanValue());
        } else {
            throw invalid("production context catalog contains a noncanonical JSON value");
        }
    }

    private static void appendJsonString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                throw invalid("production context catalog text must be canonical ASCII");
            }
            if (character == '"' || character == '\\') output.append('\\');
            output.append(character);
        }
        output.append('"');
    }

    private static void requireObject(JsonNode value, Set<String> keys, String label) {
        if (value == null || !value.isObject()) throw invalid(label + " is not an object");
        ArrayList<String> actual = new ArrayList<>();
        actual.addAll(value.propertyNames());
        if (actual.size() != keys.size() || !keys.containsAll(actual)) {
            throw invalid(label + " has extra, missing, or unknown entries");
        }
    }

    private static String text(JsonNode owner, String field, String label) {
        JsonNode value = owner.get(field);
        if (value == null || !value.isString()) {
            throw invalid(label + "." + field + " is not text");
        }
        return value.asString();
    }

    private static int integer(JsonNode owner, String field, String label) {
        JsonNode value = owner.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw invalid(label + "." + field + " is not a signed int32");
        }
        long number = value.longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(label + "." + field + " is outside signed int32");
        }
        return (int) number;
    }

    private static String canonicalWorldIdentity(String value, String label) {
        if (value == null || !value.matches("-?(0|[1-9][0-9]*)")) {
            throw invalid(label + " world identity is not canonical signed-64-bit text");
        }
        try {
            long parsed = Long.parseLong(value);
            if (!Long.toString(parsed).equals(value)) {
                throw invalid(label + " world identity is noncanonical");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw invalid(label + " world identity is outside signed-64-bit", failure);
        }
    }

    private static String minecraftKey(String value, String label) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw invalid("invalid canonical " + label + ": " + value);
        }
        return value;
    }

    private static String destination(String value, String label) {
        if (value == null || !value.matches("[a-z0-9_]+")) {
            throw invalid("invalid canonical map destination in " + label);
        }
        return value;
    }

    private static String requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")
                || value.equals("0".repeat(64))) {
            throw invalid(label + " must be a nonzero lowercase SHA-256");
        }
        return value;
    }

    private static void requireTargetCoordinate(int coordinate, int chunkWidth, String axis) {
        long minimumChunk = (long) coordinate - Mc263FeaturesRegion.INPUT_RADIUS;
        long maximumChunk = (long) coordinate + Mc263FeaturesRegion.INPUT_RADIUS;
        long minimumBlock = minimumChunk * chunkWidth;
        long maximumBlock = maximumChunk * chunkWidth + chunkWidth - 1L;
        if (minimumChunk < Integer.MIN_VALUE || maximumChunk > Integer.MAX_VALUE
                || minimumBlock < Integer.MIN_VALUE || maximumBlock > Integer.MAX_VALUE) {
            throw invalid("production context target chunk " + axis
                    + " cannot be represented as block coordinates: " + coordinate);
        }
    }

    private static void requireActiveOutput(Mc263FeaturesRegion region, int blockX, int blockY,
            int blockZ, TargetKey target) {
        if (!region.ensureCanWrite(blockX, blockY, blockZ)) {
            throw invalid("production context request is outside the active source write closure");
        }
        long sourceChunkX = Math.floorDiv((long) blockX, Blocks.CHUNK_X);
        long sourceChunkZ = Math.floorDiv((long) blockZ, Blocks.CHUNK_Z);
        long minimumX = (long) target.chunkX() - Mc263FeaturesRegion.INPUT_RADIUS;
        long maximumX = (long) target.chunkX() + Mc263FeaturesRegion.INPUT_RADIUS;
        long minimumZ = (long) target.chunkZ() - Mc263FeaturesRegion.INPUT_RADIUS;
        long maximumZ = (long) target.chunkZ() + Mc263FeaturesRegion.INPUT_RADIUS;
        if (sourceChunkX < minimumX || sourceChunkX > maximumX
                || sourceChunkZ < minimumZ || sourceChunkZ > maximumZ) {
            throw invalid("production context request is outside the authenticated active "
                    + "nine-source output closure");
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) {
            throw invalid("production context catalog is not strict UTF-8", failure);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String digest(ReceiptWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writer.write(output);
            output.flush();
            return sha256(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean secureEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean contains(byte[] bytes, byte value) {
        for (byte item : bytes) if (item == value) return true;
        return false;
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) if (value != 0) return false;
        return true;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    @FunctionalInterface
    private interface ReceiptWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private record ParsedContext(ContextKey key, LocatedProductionContext context) { }

    private record TargetKey(long worldSeed, int chunkX, int chunkZ) { }

    private record ContextKey(long worldSeed, String sourceIdentity, String tableIdentity,
                              int originX, int originY, int originZ)
            implements Comparable<ContextKey> {
        @Override
        public int compareTo(ContextKey other) {
            int comparison = Long.compare(worldSeed, other.worldSeed);
            if (comparison == 0) comparison = sourceIdentity.compareTo(other.sourceIdentity);
            if (comparison == 0) comparison = tableIdentity.compareTo(other.tableIdentity);
            if (comparison == 0) comparison = Integer.compare(originX, other.originX);
            if (comparison == 0) comparison = Integer.compare(originY, other.originY);
            if (comparison == 0) comparison = Integer.compare(originZ, other.originZ);
            return comparison;
        }
    }
}
