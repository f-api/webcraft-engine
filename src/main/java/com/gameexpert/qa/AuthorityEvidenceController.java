package com.gameexpert.qa;

import com.gameexpert.common.InvalidRequestException;
import com.gameexpert.config.EngineProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** QA-server-only REST transport for the shared browser authority evidence hook. */
@RestController
public class AuthorityEvidenceController {
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final Pattern CREATION_RECEIPT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern CANONICAL_I64 = Pattern.compile("(?:0|-[1-9][0-9]*|[1-9][0-9]*)");
    private static final int MAX_DURABLE_DEPTH = 32;
    private static final int MAX_ENTS_CHUNKS = 17 * 17;
    private static final String[] DURABLE_KEYS = {
        "schema", "abi", "seed", "coordinates", "structures", "bent", "ents", "ticks",
        "loot", "reconnect", "eviction"
    };
    private static final String[] COORDINATE_KEYS = {
        "minChunkX", "minChunkZ", "maxChunkX", "maxChunkZ"
    };
    private static final String[] STRUCTURE_CHUNK_KEYS = {"chunkX", "chunkZ", "starts", "references"};
    private static final String[] STRUCTURE_START_KEYS = {
        "structureId", "valid", "startKey", "originChunkX", "originChunkZ", "references",
        "boundingBox", "pieceCount"
    };
    private static final String[] STRUCTURE_REFERENCE_KEYS = {"structureId", "origins"};
    private static final String[] DOMAIN_KEYS = {"schema", "entries"};
    private static final String[] BENT_KEYS = {
        "chunkX", "chunkZ", "packed", "x", "y", "z", "blockIdentity", "entityType",
        "canonicalNbtSha256"
    };
    private static final String[] ENTS_DOMAIN_KEYS = {"schema", "chunks"};
    private static final String[] ENTS_CHUNK_KEYS = {
        "chunkX", "chunkZ", "laneClaimed", "laneAcknowledged", "laneRejected",
        "activationOutcome", "entries"
    };
    private static final String[] ENTS_ENTRY_KEYS = {
        "encounterOrdinal", "disposition", "packed", "structures", "kind", "entityKey", "spawnReason",
        "x", "y", "z", "yaw", "pitch", "velocityX", "velocityY", "velocityZ",
        "lootTable", "lootSeed", "canonicalPayloadSha256", "canonicalRowSha256",
        "durableRowSha256"
    };
    private static final String[] TICKS_KEYS = {
        "chunkX", "chunkZ", "x", "y", "z", "lane", "key", "dueTick", "priority",
        "subTickOrder", "state", "disposition"
    };
    private static final String[] LOOT_KEYS = {
        "chunkX", "chunkZ", "packed", "x", "y", "z", "table", "rawSeed", "state",
        "resultSha256"
    };
    private static final String[] CANONICAL_CHUNK_KEYS = {
        "chunkX", "chunkZ", "finalCarrierSha256", "structureCarrierSha256", "laneClaimMask",
        "laneAckMask", "laneRejectedMask"
    };
    private static final ObjectMapper REQUEST_JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final EngineProperties properties;
    private final AuthorityEvidenceService evidence;
    private final AuthorityEvidenceCreationReceiptService creationReceiptService;
    private final AuthorityEvidenceCoordinator coordinator;

    public AuthorityEvidenceController(EngineProperties properties, AuthorityEvidenceService evidence,
            AuthorityEvidenceCreationReceiptService creationReceiptService,
            AuthorityEvidenceCoordinator coordinator) {
        this.properties = Objects.requireNonNull(properties, "engine properties");
        this.evidence = Objects.requireNonNull(evidence, "authority evidence service");
        this.creationReceiptService = Objects.requireNonNull(
                creationReceiptService, "authority evidence creation receipt service");
        this.coordinator = Objects.requireNonNull(coordinator, "authority evidence coordinator");
    }

    /** Legacy V1 Spring GET is intentionally fail-closed; V2 producer reuse stays internal. */
    public ResponseEntity<Map<String, Object>> capture(
            @PathVariable long worldId,
            @RequestParam int minChunkX,
            @RequestParam int minChunkZ,
            @RequestParam int maxChunkX,
            @RequestParam int maxChunkZ) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/worlds/{worldId}/debug/authority-evidence/arm")
    public ResponseEntity<Void> arm(@PathVariable long worldId, HttpServletRequest request) {
        if (!isQaLoopback(request)) return ResponseEntity.notFound().build();
        if (worldId <= 0) throw invalidBody();
        JsonNode body = readBody(request);
        requireExactKeys(body, "nickname", "window", "creationReceipt");
        String nickname = requiredText(body, "nickname");
        if (!NICKNAME.matcher(nickname).matches()) throw invalidBody();
        AuthorityEvidenceJournal.Window window = readWindow(body);
        String creationReceipt = requiredText(body, "creationReceipt");
        if (!CREATION_RECEIPT.matcher(creationReceipt).matches()) throw invalidBody();

        creationReceiptService.consumeAndArm(worldId, nickname, window, creationReceipt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/worlds/{worldId}/debug/authority-evidence/capture")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<Map<String, Object>> capture(
            @PathVariable long worldId, HttpServletRequest request) {
        if (!isQaLoopback(request)) return ResponseEntity.notFound().build();
        if (worldId <= 0) throw invalidBody();
        JsonNode body = readBody(request);
        requireExactKeys(body, "nickname", "window");
        String nickname = requiredText(body, "nickname");
        if (!NICKNAME.matcher(nickname).matches()) throw invalidBody();
        AuthorityEvidenceJournal.Window window = readWindow(body);

        java.util.Optional<AuthorityEvidenceJournal.Snapshot> recovered =
                coordinator.recoverSealedCapture(worldId, nickname, window);
        if (recovered.isPresent()) {
            DurableCapture durableCapture = restoreDurableCapture(
                    recovered.get().capturePayload().durableJson(), window);
            return ResponseEntity.ok(schemaV2(durableCapture, recovered.get()));
        }

        AuthorityEvidenceCoordinator.CaptureLease lease = coordinator.acquireCaptureLease(
                worldId, nickname, window);
        try (lease) {
            DurableCapture durableCapture = detachDurableCapture(evidence.capture(worldId,
                    window.minChunkX(), window.minChunkZ(), window.maxChunkX(), window.maxChunkZ()),
                    window, lease.evictionRows(), lease.reconnectRows());
            AuthorityEvidenceJournal.Snapshot sealed = lease.seal(durableCapture.payload);
            return ResponseEntity.ok(schemaV2(durableCapture, sealed));
        }
    }

    private boolean isQaLoopback(HttpServletRequest request) {
        return properties.qaSeeding() && request != null
                && isNumericLoopback(request.getRemoteAddr());
    }

    private static boolean isNumericLoopback(String peer) {
        if (peer == null || peer.isEmpty()) return false;
        return peer.indexOf(':') < 0 ? isIpv4Loopback(peer) : isIpv6Loopback(peer);
    }

    private static boolean isIpv4Loopback(String peer) {
        String[] octets = peer.split("\\.", -1);
        if (octets.length != 4) return false;
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3
                    || (octet.length() > 1 && octet.charAt(0) == '0')) return false;
            int value = 0;
            for (int character = 0; character < octet.length(); character++) {
                char digit = octet.charAt(character);
                if (digit < '0' || digit > '9') return false;
                value = value * 10 + digit - '0';
                if (value > 255) return false;
            }
            if (index == 0 && value != 127) return false;
        }
        return true;
    }

    private static boolean isIpv6Loopback(String peer) {
        if (peer.indexOf('.') >= 0 || peer.indexOf('%') >= 0) return false;
        int compression = peer.indexOf("::");
        if (compression >= 0 && compression != peer.lastIndexOf("::")) return false;
        int[] groups = new int[8];
        try {
            if (compression >= 0) {
                int left = parseIpv6Groups(peer.substring(0, compression), groups, 0);
                int rightCount = parseIpv6GroupCount(peer.substring(compression + 2));
                int rightStart = 8 - rightCount;
                if (rightStart <= left) return false;
                parseIpv6Groups(peer.substring(compression + 2), groups, rightStart);
            } else if (parseIpv6Groups(peer, groups, 0) != 8) {
                return false;
            }
        } catch (IllegalArgumentException failure) {
            return false;
        }
        if (groups[7] != 1) return false;
        for (int index = 0; index < 7; index++) {
            if (groups[index] != 0) return false;
        }
        return true;
    }

    private static int parseIpv6GroupCount(String value) {
        if (value.isEmpty()) return 0;
        return value.split(":", -1).length;
    }

    private static int parseIpv6Groups(String value, int[] groups, int offset) {
        if (value.isEmpty()) return 0;
        String[] parts = value.split(":", -1);
        if (offset + parts.length > groups.length) throw new IllegalArgumentException();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 4
                    || (part.length() > 1 && part.charAt(0) == '0')) {
                throw new IllegalArgumentException();
            }
            for (int character = 0; character < part.length(); character++) {
                char digit = part.charAt(character);
                if (!((digit >= '0' && digit <= '9') || (digit >= 'a' && digit <= 'f')
                        || (digit >= 'A' && digit <= 'F'))) {
                    throw new IllegalArgumentException();
                }
            }
            int parsed = Integer.parseInt(part, 16);
            groups[offset + index] = parsed;
        }
        return parts.length;
    }

    private static JsonNode readBody(HttpServletRequest request) {
        try {
            JsonNode body = REQUEST_JSON.readTree(request.getInputStream());
            if (body == null || !body.isObject()) throw invalidBody();
            return body;
        } catch (InvalidRequestException failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalidBody();
        }
    }

    private static void requireExactKeys(JsonNode object, String... expectedKeys) {
        if (object == null || !object.isObject() || object.size() != expectedKeys.length) {
            throw invalidBody();
        }
        Set<String> expected = Set.of(expectedKeys);
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!expected.contains(field.getKey())) throw invalidBody();
        }
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString()) throw invalidBody();
        return value.asString();
    }

    private static AuthorityEvidenceJournal.Window readWindow(JsonNode body) {
        JsonNode window = body.get("window");
        requireExactKeys(window, "minChunkX", "minChunkZ", "maxChunkX", "maxChunkZ");
        try {
            return new AuthorityEvidenceJournal.Window(
                    requiredInt(window, "minChunkX"), requiredInt(window, "minChunkZ"),
                    requiredInt(window, "maxChunkX"), requiredInt(window, "maxChunkZ"));
        } catch (IllegalArgumentException failure) {
            throw invalidBody();
        }
    }

    private static int requiredInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalidBody();
        }
        return value.intValue();
    }

    private static InvalidRequestException invalidBody() {
        return new InvalidRequestException("INVALID_REQUEST_BODY");
    }

    private static Map<String, Object> schemaV2(DurableCapture durable,
            AuthorityEvidenceJournal.Snapshot snapshot) {
        AuthorityEvidenceJournal.ArmBinding binding = snapshot.binding();
        AuthorityEvidenceJournal.Window window = binding.window();
        long revision = snapshot.revision();
        requireExactRevision(snapshot, revision);
        if (snapshot.eviction() == null || snapshot.reconnect() == null
                || snapshot.eviction().revision() != snapshot.events().get(5).revision()
                || snapshot.reconnect().revision() != snapshot.events().get(8).revision()) {
            throw new IllegalStateException("authority evidence revision is missing or stale");
        }
        if (durable.payload == null
                || durable.payload.sourceRevision() != snapshot.reconnect().revision()
                || !durable.evictionRows.equals(snapshot.eviction().canonicalChunks())
                || !durable.reconnectRows.equals(snapshot.reconnect().canonicalChunks())
                || !durable.payload.sourceFingerprint().equals(
                        snapshot.eviction().sourceFingerprint()
                                + snapshot.reconnect().sourceFingerprint())) {
            throw new IllegalStateException("authority evidence capture source is stale");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schema", snapshot.schema());
        result.put("abi", snapshot.abi());
        result.put("revision", revision);
        result.put("seed", durable.seed);
        result.put("nickname", binding.nickname());
        result.put("coordinates", coordinates(window));
        result.put("lifecycle", lifecycle(snapshot.events(), revision));
        result.put("structures", durable.structures);
        result.put("bent", durable.bent);
        result.put("ents", durable.ents);
        result.put("ticks", durable.ticks);
        result.put("loot", durable.loot);
        result.put("reconnect", projection(snapshot.reconnect()));
        result.put("eviction", projection(snapshot.eviction()));
        return immutableMap(result);
    }

    private static void requireExactRevision(AuthorityEvidenceJournal.Snapshot snapshot,
            long revision) {
        List<AuthorityEvidenceJournal.LifecycleEvent> events = snapshot.events();
        if (revision <= 0 || events == null || events.size() != 10
                || events.get(events.size() - 1).revision() != revision) {
            throw new IllegalStateException("authority evidence revision is missing or stale");
        }
        long previous = 0;
        for (int index = 0; index < events.size(); index++) {
            long actual = events.get(index).revision();
            if (actual <= previous || actual != index + 2L) {
                throw new IllegalStateException("authority evidence revision is missing or replayed");
            }
            previous = actual;
        }
    }

    private static Map<String, Object> coordinates(AuthorityEvidenceJournal.Window window) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("minChunkX", window.minChunkX());
        result.put("minChunkZ", window.minChunkZ());
        result.put("maxChunkX", window.maxChunkX());
        result.put("maxChunkZ", window.maxChunkZ());
        return immutableMap(result);
    }

    private static Map<String, Object> lifecycle(List<AuthorityEvidenceJournal.LifecycleEvent> events,
            long revision) {
        ArrayList<Object> copied = new ArrayList<>(events.size());
        for (AuthorityEvidenceJournal.LifecycleEvent event : events) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("schema", event.schema());
            item.put("sequence", event.sequence());
            item.put("revision", event.revision());
            item.put("kind", event.kind());
            item.put("connectionGeneration", event.connectionGeneration());
            item.put("connectionIdentitySha256", event.connectionIdentitySha256());
            item.put("chunkX", event.chunkX());
            item.put("chunkZ", event.chunkZ());
            copied.add(immutableMap(item));
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schema", AuthorityEvidenceJournal.SCHEMA);
        result.put("revision", revision);
        result.put("events", immutableList(copied));
        return immutableMap(result);
    }

    private static Map<String, Object> projection(AuthorityEvidenceJournal.Projection projection) {
        ArrayList<Object> rows = new ArrayList<>(projection.canonicalChunks().size());
        for (AuthorityEvidenceJournal.CanonicalChunk row : projection.canonicalChunks()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("chunkX", row.chunkX());
            item.put("chunkZ", row.chunkZ());
            item.put("finalCarrierSha256", row.finalCarrierSha256());
            item.put("structureCarrierSha256", row.structureCarrierSha256());
            item.put("laneClaimMask", row.laneClaimMask());
            item.put("laneAckMask", row.laneAckMask());
            item.put("laneRejectedMask", row.laneRejectedMask());
            rows.add(immutableMap(item));
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schema", projection.schema());
        result.put("revision", projection.revision());
        result.put("sourceSequence", projection.sourceSequence());
        result.put("canonicalChunks", immutableList(rows));
        return immutableMap(result);
    }

    private static DurableCapture detachDurableCapture(Object value,
            AuthorityEvidenceJournal.Window expectedWindow) {
        return detachDurableCapture(value, expectedWindow, null, null);
    }

    private static DurableCapture detachDurableCapture(Object value,
            AuthorityEvidenceJournal.Window expectedWindow,
            List<AuthorityEvidenceJournal.CanonicalChunk> authoritativeEvictionRows,
            List<AuthorityEvidenceJournal.CanonicalChunk> authoritativeReconnectRows) {
        Map<String, Object> root = detachExactMap(value, "durable capture", DURABLE_KEYS, 0);
        requireDurableInt(root.get("schema"), "durable schema", AuthorityEvidenceService.SCHEMA);
        requireDurableString(root.get("abi"), "durable ABI", AuthorityEvidenceService.ABI);
        int seed = requireDurableInt(root.get("seed"), "durable seed");

        Map<String, Object> coordinates = exactDetachedMap(
                root.get("coordinates"), "durable coordinates", COORDINATE_KEYS);
        int minChunkX = requireDurableInt(coordinates.get("minChunkX"), "minimum chunk X");
        int minChunkZ = requireDurableInt(coordinates.get("minChunkZ"), "minimum chunk Z");
        int maxChunkX = requireDurableInt(coordinates.get("maxChunkX"), "maximum chunk X");
        int maxChunkZ = requireDurableInt(coordinates.get("maxChunkZ"), "maximum chunk Z");
        if (minChunkX != expectedWindow.minChunkX() || minChunkZ != expectedWindow.minChunkZ()
                || maxChunkX != expectedWindow.maxChunkX() || maxChunkZ != expectedWindow.maxChunkZ()) {
            throw malformedDurable("durable coordinates do not match the requested window");
        }

        List<?> structures = requireDurableList(root.get("structures"), "structures");
        validateStructures(structures);
        validateDomain(root.get("bent"), "bent", BENT_KEYS);
        validateEntsDomain(root.get("ents"), expectedWindow);
        validateDomain(root.get("ticks"), "ticks", TICKS_KEYS);
        validateDomain(root.get("loot"), "loot", LOOT_KEYS);
        validateCanonicalProjection(root.get("reconnect"), "reconnect", expectedWindow);
        validateCanonicalProjection(root.get("eviction"), "eviction", expectedWindow);
        List<AuthorityEvidenceJournal.CanonicalChunk> reconnectRows = canonicalRows(
                root.get("reconnect"), "reconnect");
        List<AuthorityEvidenceJournal.CanonicalChunk> evictionRows = canonicalRows(
                root.get("eviction"), "eviction");
        if (authoritativeEvictionRows != null || authoritativeReconnectRows != null) {
            if (authoritativeEvictionRows == null || authoritativeReconnectRows == null) {
                throw malformedDurable("capture projection source is incomplete");
            }
            boolean sourceMatches = evictionRows.equals(authoritativeEvictionRows)
                    && reconnectRows.equals(authoritativeReconnectRows);
            if (!sourceMatches && (!evictionRows.isEmpty() || !reconnectRows.isEmpty())) {
                throw malformedDurable("capture projection does not match the sealed source");
            }
            evictionRows = List.copyOf(authoritativeEvictionRows);
            reconnectRows = List.copyOf(authoritativeReconnectRows);
            root = bindCanonicalProjections(root, evictionRows, reconnectRows);
        }
        byte[] serialized;
        try {
            serialized = REQUEST_JSON.writeValueAsBytes(root);
        } catch (Exception failure) {
            throw malformedDurable("durable capture could not be persisted");
        }
        return new DurableCapture(seed, structures, root.get("bent"), root.get("ents"),
                root.get("ticks"), root.get("loot"), evictionRows, reconnectRows,
                new AuthorityEvidenceJournal.CapturePayload(serialized, evictionRows, reconnectRows));
    }

    private static Map<String, Object> bindCanonicalProjections(Map<String, Object> root,
            List<AuthorityEvidenceJournal.CanonicalChunk> evictionRows,
            List<AuthorityEvidenceJournal.CanonicalChunk> reconnectRows) {
        LinkedHashMap<String, Object> bound = new LinkedHashMap<>(root);
        bound.put("reconnect", canonicalProjection(reconnectRows));
        bound.put("eviction", canonicalProjection(evictionRows));
        return immutableMap(bound);
    }

    private static Map<String, Object> canonicalProjection(
            List<AuthorityEvidenceJournal.CanonicalChunk> rows) {
        ArrayList<Object> detachedRows = new ArrayList<>(rows.size());
        for (AuthorityEvidenceJournal.CanonicalChunk row : rows) {
            detachedRows.add(orderedMap(
                    "chunkX", row.chunkX(), "chunkZ", row.chunkZ(),
                    "finalCarrierSha256", row.finalCarrierSha256(),
                    "structureCarrierSha256", row.structureCarrierSha256(),
                    "laneClaimMask", row.laneClaimMask(),
                    "laneAckMask", row.laneAckMask(),
                    "laneRejectedMask", row.laneRejectedMask()));
        }
        return orderedMap("schema", AuthorityEvidenceService.SCHEMA,
                "canonicalChunks", immutableList(detachedRows));
    }

    @SuppressWarnings("unchecked")
    private static DurableCapture restoreDurableCapture(byte[] serialized,
            AuthorityEvidenceJournal.Window expectedWindow) {
        try {
            Map<String, Object> root = REQUEST_JSON.readValue(serialized, Map.class);
            return detachDurableCapture(root, expectedWindow);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw malformedDurable("persisted durable capture is malformed");
        }
    }

    private static List<AuthorityEvidenceJournal.CanonicalChunk> canonicalRows(Object value,
            String label) {
        Map<String, Object> projection = exactDetachedMap(
                value, label + " projection", new String[] {"schema", "canonicalChunks"});
        ArrayList<AuthorityEvidenceJournal.CanonicalChunk> rows = new ArrayList<>();
        for (Object rowValue : requireDurableList(
                projection.get("canonicalChunks"), label + " canonical chunks")) {
            Map<String, Object> row = exactDetachedMap(
                    rowValue, label + " canonical chunk", CANONICAL_CHUNK_KEYS);
            rows.add(new AuthorityEvidenceJournal.CanonicalChunk(
                    requireDurableInt(row.get("chunkX"), label + " chunk X"),
                    requireDurableInt(row.get("chunkZ"), label + " chunk Z"),
                    requireDigest(row.get("finalCarrierSha256"), label + " final carrier digest"),
                    requireDigest(row.get("structureCarrierSha256"),
                            label + " structure carrier digest"),
                    requireDurableInt(row.get("laneClaimMask"), label + " claim mask"),
                    requireDurableInt(row.get("laneAckMask"), label + " ack mask"),
                    requireDurableInt(row.get("laneRejectedMask"), label + " rejected mask")));
        }
        return List.copyOf(rows);
    }

    private static Map<String, Object> detachExactMap(Object value, String label,
            String[] expectedKeys, int depth) {
        if (!(value instanceof Map<?, ?> source)) throw malformedDurable(label + " is not an object");
        if (depth > MAX_DURABLE_DEPTH) throw malformedDurable(label + " is too deeply nested");
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (index >= expectedKeys.length || !(entry.getKey() instanceof String key)
                    || !expectedKeys[index].equals(key)) {
                throw malformedDurable(label + " has unexpected keys or order");
            }
            copy.put(key, detachValue(entry.getValue(), label + "." + key, depth + 1));
            index++;
        }
        if (index != expectedKeys.length) throw malformedDurable(label + " is missing keys");
        return immutableMap(copy);
    }

    private static Object detachValue(Object value, String label, int depth) {
        if (depth > MAX_DURABLE_DEPTH) throw malformedDurable(label + " is too deeply nested");
        if (value == null || value instanceof String || value instanceof Integer
                || value instanceof Long || value instanceof Double || value instanceof Float
                || value instanceof Short || value instanceof Byte || value instanceof Boolean) {
            if (value instanceof Double number && !Double.isFinite(number)) {
                throw malformedDurable(label + " is not finite");
            }
            if (value instanceof Float number && !Float.isFinite(number)) {
                throw malformedDurable(label + " is not finite");
            }
            return value;
        }
        if (value instanceof Map<?, ?> source) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw malformedDurable(label + " has a non-string key");
                }
                if (copy.containsKey(key)) throw malformedDurable(label + " has duplicate keys");
                copy.put(key, detachValue(entry.getValue(), label + "." + key, depth + 1));
            }
            return immutableMap(copy);
        }
        if (value instanceof List<?> source) {
            ArrayList<Object> copy = new ArrayList<>(source.size());
            for (Object entry : source) copy.add(detachValue(entry, label + "[]", depth + 1));
            return immutableList(copy);
        }
        throw malformedDurable(label + " contains an unsupported mutable value");
    }

    private static Map<String, Object> exactDetachedMap(Object value, String label,
            String[] expectedKeys) {
        if (!(value instanceof Map<?, ?> source)) throw malformedDurable(label + " is not an object");
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (index >= expectedKeys.length || !(entry.getKey() instanceof String key)
                    || !expectedKeys[index].equals(key)) {
                throw malformedDurable(label + " has unexpected keys or order");
            }
            copy.put(key, entry.getValue());
            index++;
        }
        if (index != expectedKeys.length) throw malformedDurable(label + " is missing keys");
        return immutableMap(copy);
    }

    private static void validateStructures(List<?> structures) {
        for (Object structure : structures) {
            Map<String, Object> chunk = exactDetachedMap(
                    structure, "structure chunk", STRUCTURE_CHUNK_KEYS);
            requireDurableInt(chunk.get("chunkX"), "structure chunk X");
            requireDurableInt(chunk.get("chunkZ"), "structure chunk Z");
            for (Object start : requireDurableList(chunk.get("starts"), "structure starts")) {
                Map<String, Object> row = exactDetachedMap(start, "structure start", STRUCTURE_START_KEYS);
                requireResourceKey(row.get("structureId"), "structure ID");
                boolean valid = requireDurableBoolean(row.get("valid"), "structure valid flag");
                if (!valid) {
                    for (String field : List.of("startKey", "originChunkX", "originChunkZ",
                            "references", "boundingBox")) {
                        if (row.get(field) != null) {
                            throw malformedDurable("invalid structure owns " + field);
                        }
                    }
                    requireDurableInt(row.get("pieceCount"), "invalid structure piece count", 0);
                    continue;
                }
                requireDurableString(row.get("startKey"), "structure start key");
                requireDurableInt(row.get("originChunkX"), "structure origin X");
                requireDurableInt(row.get("originChunkZ"), "structure origin Z");
                int references = requireDurableInt(
                        row.get("references"), "structure reference count");
                if (references < 0) throw malformedDurable("structure reference count is negative");
                List<?> box = requireDurableList(row.get("boundingBox"), "structure bounding box");
                if (box.size() != 6) throw malformedDurable("structure bounding box must have six values");
                int[] coordinates = new int[6];
                for (int index = 0; index < coordinates.length; index++) {
                    coordinates[index] = requireDurableInt(
                            box.get(index), "structure bounding box coordinate");
                }
                if (coordinates[0] > coordinates[3] || coordinates[1] > coordinates[4]
                        || coordinates[2] > coordinates[5]) {
                    throw malformedDurable("structure bounding box is inverted");
                }
                int pieceCount = requireDurableInt(row.get("pieceCount"), "structure piece count");
                if (pieceCount <= 0) throw malformedDurable("structure piece count is not positive");
            }
            for (Object reference : requireDurableList(chunk.get("references"), "structure references")) {
                Map<String, Object> row = exactDetachedMap(
                        reference, "structure reference", STRUCTURE_REFERENCE_KEYS);
                requireDurableString(row.get("structureId"), "reference structure ID");
                requireDurableStringList(row.get("origins"), "reference origins");
            }
        }
    }

    private static void validateDomain(Object value, String label, String[] rowKeys) {
        Map<String, Object> domain = exactDetachedMap(value, label + " domain", DOMAIN_KEYS);
        requireDurableInt(domain.get("schema"), label + " schema", AuthorityEvidenceService.SCHEMA);
        for (Object rowValue : requireDurableList(domain.get("entries"), label + " entries")) {
            Map<String, Object> row = exactDetachedMap(rowValue, label + " row", rowKeys);
            switch (label) {
                case "bent" -> {
                    requireDurableInt(row.get("chunkX"), label + " chunk X");
                    requireDurableInt(row.get("chunkZ"), label + " chunk Z");
                    requireDurableInt(row.get("packed"), label + " packed position");
                    for (String field : List.of("x", "y", "z")) {
                        requireDurableInt(row.get(field), label + " " + field);
                    }
                    requireDurableString(row.get("blockIdentity"), label + " block identity");
                    requireDurableString(row.get("entityType"), label + " entity type");
                    requireDigest(row.get("canonicalNbtSha256"), label + " NBT digest");
                }
                case "ticks" -> {
                    for (String field : List.of("chunkX", "chunkZ", "x", "y", "z", "priority")) {
                        requireDurableInt(row.get(field), label + " " + field);
                    }
                    String lane = requireDurableString(row.get("lane"), label + " lane");
                    if (!Set.of("BLOCK", "FLUID").contains(lane)) {
                        throw malformedDurable("ticks lane is invalid");
                    }
                    requireResourceKey(row.get("key"), label + " key");
                    requireCanonicalI64(row.get("dueTick"), label + " due tick");
                    int priority = requireDurableInt(row.get("priority"), label + " priority");
                    if (priority < -3 || priority > 3) {
                        throw malformedDurable("ticks priority is invalid");
                    }
                    requireCanonicalI64(row.get("subTickOrder"), label + " sub-tick order");
                    String state = requireDurableString(row.get("state"), label + " state");
                    Object dispositionValue = row.get("disposition");
                    if (state.equals("SCHEDULED")) {
                        if (dispositionValue != null) {
                            throw malformedDurable("scheduled tick owns a disposition");
                        }
                    } else if (state.equals("CONSUMED")) {
                        String disposition = requireDurableString(
                                dispositionValue, label + " disposition");
                        if (!Set.of("EXECUTE", "LIVE_TYPE_NO_OP").contains(disposition)) {
                            throw malformedDurable("consumed tick disposition is invalid");
                        }
                    } else {
                        throw malformedDurable("ticks state is invalid");
                    }
                }
                case "loot" -> {
                    for (String field : List.of("chunkX", "chunkZ", "packed", "x", "y", "z")) {
                        requireDurableInt(row.get(field), label + " " + field);
                    }
                    requireDurableString(row.get("table"), label + " table");
                    requireDurableString(row.get("rawSeed"), label + " raw seed");
                    requireDurableString(row.get("state"), label + " state");
                    requireNullableDurableString(row.get("resultSha256"), label + " result digest");
                }
                default -> throw malformedDurable("unknown durable domain " + label);
            }
        }
    }

    private static void validateEntsDomain(Object value,
            AuthorityEvidenceJournal.Window expectedWindow) {
        Map<String, Object> domain = exactDetachedMap(value, "ents domain", ENTS_DOMAIN_KEYS);
        requireDurableInt(domain.get("schema"), "ents schema",
                AuthorityEvidenceService.ENTITY_SCHEMA_V3);
        List<?> chunks = requireDurableList(domain.get("chunks"), "ents chunks");
        if (chunks.size() > MAX_ENTS_CHUNKS) {
            throw malformedDurable("ents chunk count exceeds the canonical window");
        }
        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;
        boolean first = true;
        for (Object chunkValue : chunks) {
            Map<String, Object> chunk = exactDetachedMap(
                    chunkValue, "ents chunk", ENTS_CHUNK_KEYS);
            int chunkX = requireDurableInt(chunk.get("chunkX"), "ents chunk X");
            int chunkZ = requireDurableInt(chunk.get("chunkZ"), "ents chunk Z");
            if (chunkX < expectedWindow.minChunkX() || chunkX > expectedWindow.maxChunkX()
                    || chunkZ < expectedWindow.minChunkZ() || chunkZ > expectedWindow.maxChunkZ()) {
                throw malformedDurable("ents chunk is outside the requested window");
            }
            if (!first && (chunkX < previousX || (chunkX == previousX && chunkZ <= previousZ))) {
                throw malformedDurable("ents chunks are out of order");
            }
            boolean claimed = requireDurableBoolean(chunk.get("laneClaimed"), "ents claimed flag");
            boolean acknowledged = requireDurableBoolean(
                    chunk.get("laneAcknowledged"), "ents acknowledged flag");
            boolean rejected = requireDurableBoolean(chunk.get("laneRejected"), "ents rejected flag");
            String outcome = requireDurableString(chunk.get("activationOutcome"), "ents outcome");
            List<?> entries = requireDurableList(chunk.get("entries"), "ents entries");
            boolean hasDurableRows = false;
            for (int ordinal = 0; ordinal < entries.size(); ordinal++) {
                Map<String, Object> row = exactDetachedMap(
                        entries.get(ordinal), "ents row", ENTS_ENTRY_KEYS);
                if (requireDurableInt(row.get("encounterOrdinal"), "ents encounter ordinal")
                        != ordinal) {
                    throw malformedDurable("ents encounter ordinals are not contiguous");
                }
                String disposition = requireDurableString(
                        row.get("disposition"), "ents disposition");
                if (!Set.of("LIVE", "OVERRIDDEN").contains(disposition)) {
                    throw malformedDurable("ents disposition is invalid");
                }
                requireDurableInt(row.get("packed"), "ents packed position");
                requireSortedUniqueResourceKeys(row.get("structures"), "ents structures");
                String kind = requireDurableString(row.get("kind"), "ents kind");
                if (!Set.of("CHEST_MINECART", "ENTITY").contains(kind)) {
                    throw malformedDurable("ents kind is invalid");
                }
                requireResourceKey(row.get("entityKey"), "ents entity key");
                requireResourceKey(row.get("spawnReason"), "ents spawn reason");
                for (String field : List.of("x", "y", "z", "yaw", "pitch",
                        "velocityX", "velocityY", "velocityZ")) {
                    requireDurableDouble(row.get(field), "ents " + field);
                }
                if (row.get("lootTable") != null) {
                    requireResourceKey(row.get("lootTable"), "ents loot table");
                }
                requireCanonicalI64(row.get("lootSeed"), "ents loot seed");
                requireDigest(row.get("canonicalPayloadSha256"), "ents payload digest");
                String canonicalRow = requireDigest(
                        row.get("canonicalRowSha256"), "ents canonical row digest");
                Object durableValue = row.get("durableRowSha256");
                if (durableValue != null) {
                    if (disposition.equals("OVERRIDDEN")) {
                        throw malformedDurable("overridden ents row owns a durable row digest");
                    }
                    String durableRow = requireDigest(durableValue, "ents durable row digest");
                    if (!durableRow.equals(canonicalRow)) {
                        throw malformedDurable("ents durable row does not match its canonical row");
                    }
                    hasDurableRows = true;
                }
            }
            validateEntsOutcome(outcome, entries.isEmpty(), claimed, acknowledged, rejected,
                    hasDurableRows, entries);
            previousX = chunkX;
            previousZ = chunkZ;
            first = false;
        }
    }

    private static void validateEntsOutcome(String outcome, boolean empty, boolean claimed,
            boolean acknowledged, boolean rejected, boolean hasDurableRows, List<?> entries) {
        boolean allActivatedRowsSettled = !empty;
        if (allActivatedRowsSettled) {
            for (Object entry : entries) {
                Map<?, ?> row = (Map<?, ?>) entry;
                boolean live = row.get("disposition").equals("LIVE");
                if (live != (row.get("durableRowSha256") != null)) {
                    allActivatedRowsSettled = false;
                    break;
                }
            }
        }
        boolean valid = switch (outcome) {
            case "EMPTY" -> empty && !claimed && !acknowledged && !rejected && !hasDurableRows;
            case "CLAIMED_PENDING" -> !empty && claimed && !acknowledged && !rejected
                    && !hasDurableRows;
            case "TERMINALLY_REJECTED" -> !empty && claimed && !acknowledged && rejected
                    && !hasDurableRows;
            case "DURABLY_ACTIVATED" -> !empty && claimed && acknowledged && !rejected
                    && allActivatedRowsSettled;
            default -> false;
        };
        if (!valid) throw malformedDurable("ents activation outcome is inconsistent");
    }

    private static void requireSortedUniqueResourceKeys(Object value, String label) {
        String previous = null;
        for (Object entry : requireDurableList(value, label)) {
            String key = requireResourceKey(entry, label + " value");
            if (previous != null && previous.compareTo(key) >= 0) {
                throw malformedDurable(label + " are not sorted and unique");
            }
            previous = key;
        }
    }

    private static void validateCanonicalProjection(Object value, String label,
            AuthorityEvidenceJournal.Window expectedWindow) {
        Map<String, Object> projection = exactDetachedMap(
                value, label + " projection", new String[] {"schema", "canonicalChunks"});
        requireDurableInt(projection.get("schema"), label + " schema", AuthorityEvidenceService.SCHEMA);
        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;
        boolean first = true;
        for (Object rowValue : requireDurableList(
                projection.get("canonicalChunks"), label + " canonical chunks")) {
            Map<String, Object> row = exactDetachedMap(
                    rowValue, label + " canonical chunk", CANONICAL_CHUNK_KEYS);
            int chunkX = requireDurableInt(row.get("chunkX"), label + " chunk X");
            int chunkZ = requireDurableInt(row.get("chunkZ"), label + " chunk Z");
            if (chunkX < expectedWindow.minChunkX() || chunkX > expectedWindow.maxChunkX()
                    || chunkZ < expectedWindow.minChunkZ() || chunkZ > expectedWindow.maxChunkZ()
                    || (!first && (chunkX < previousX || (chunkX == previousX && chunkZ <= previousZ)))) {
                throw malformedDurable(label + " canonical chunks are outside or out of order");
            }
            requireDigest(row.get("finalCarrierSha256"), label + " final carrier digest");
            requireDigest(row.get("structureCarrierSha256"), label + " structure carrier digest");
            requireDurableInt(row.get("laneClaimMask"), label + " claim mask");
            requireDurableInt(row.get("laneAckMask"), label + " ack mask");
            requireDurableInt(row.get("laneRejectedMask"), label + " rejected mask");
            previousX = chunkX;
            previousZ = chunkZ;
            first = false;
        }
    }

    private static List<?> requireDurableList(Object value, String label) {
        if (!(value instanceof List<?> list)) throw malformedDurable(label + " is not an array");
        return list;
    }

    private static void requireDurableStringList(Object value, String label) {
        for (Object entry : requireDurableList(value, label)) requireDurableString(entry, label + " value");
    }

    private static int requireDurableInt(Object value, String label) {
        if (!(value instanceof Integer integer)) throw malformedDurable(label + " is not an int32");
        return integer;
    }

    private static int requireDurableInt(Object value, String label, int expected) {
        int actual = requireDurableInt(value, label);
        if (actual != expected) throw malformedDurable(label + " is not " + expected);
        return actual;
    }

    private static double requireDurableDouble(Object value, String label) {
        if (!(value instanceof Double number) || !Double.isFinite(number)
                || Double.doubleToRawLongBits(number) == Long.MIN_VALUE) {
            throw malformedDurable(label + " is not a canonical finite number");
        }
        return number;
    }

    private static boolean requireDurableBoolean(Object value, String label) {
        if (!(value instanceof Boolean flag)) throw malformedDurable(label + " is not a boolean");
        return flag;
    }

    private static String requireDurableString(Object value, String label) {
        if (!(value instanceof String string)) throw malformedDurable(label + " is not a string");
        return string;
    }

    private static String requireDurableString(Object value, String label, String expected) {
        String actual = requireDurableString(value, label);
        if (!expected.equals(actual)) throw malformedDurable(label + " is not " + expected);
        return actual;
    }

    private static void requireNullableDurableString(Object value, String label) {
        if (value != null) requireDurableString(value, label);
    }

    private static String requireResourceKey(Object value, String label) {
        String key = requireDurableString(value, label);
        if (!RESOURCE_KEY.matcher(key).matches()) {
            throw malformedDurable(label + " is not a canonical resource key");
        }
        return key;
    }

    private static long requireCanonicalI64(Object value, String label) {
        String decimal = requireDurableString(value, label);
        if (!CANONICAL_I64.matcher(decimal).matches()) {
            throw malformedDurable(label + " is not canonical signed-i64 decimal");
        }
        try {
            long parsed = Long.parseLong(decimal);
            if (!Long.toString(parsed).equals(decimal)) {
                throw malformedDurable(label + " is not canonical signed-i64 decimal");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw malformedDurable(label + " is outside signed-i64");
        }
    }

    private static String requireDigest(Object value, String label) {
        if (!(value instanceof String digest) || !DIGEST.matcher(digest).matches()) {
            throw malformedDurable(label + " is not lower-case SHA-256 hex");
        }
        return digest;
    }

    private static IllegalStateException malformedDurable(String message) {
        return new IllegalStateException("malformed durable authority evidence: " + message);
    }

    private static final class DurableCapture {
        private final int seed;
        private final Object structures;
        private final Object bent;
        private final Object ents;
        private final Object ticks;
        private final Object loot;
        private final List<AuthorityEvidenceJournal.CanonicalChunk> evictionRows;
        private final List<AuthorityEvidenceJournal.CanonicalChunk> reconnectRows;
        private final AuthorityEvidenceJournal.CapturePayload payload;

        private DurableCapture(int seed, Object structures, Object bent, Object ents, Object ticks,
                Object loot, List<AuthorityEvidenceJournal.CanonicalChunk> evictionRows,
                List<AuthorityEvidenceJournal.CanonicalChunk> reconnectRows,
                AuthorityEvidenceJournal.CapturePayload payload) {
            this.seed = seed;
            this.structures = structures;
            this.bent = bent;
            this.ents = ents;
            this.ticks = ticks;
            this.loot = loot;
            this.evictionRows = List.copyOf(evictionRows);
            this.reconnectRows = List.copyOf(reconnectRows);
            this.payload = payload;
        }
    }

    private static Map<String, Object> orderedMap(Object... fields) {
        if ((fields.length & 1) != 0) {
            throw new IllegalArgumentException("ordered map fields must be paired");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            result.put((String) fields[index], fields[index + 1]);
        }
        return result;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<Object> immutableList(List<Object> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
