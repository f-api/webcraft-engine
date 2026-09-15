package com.gameexpert.qa;

import com.gameexpert.common.NotFoundException;
import com.gameexpert.qa.persistence.FinalSceneH12gTerminal;
import com.gameexpert.qa.persistence.FinalSceneH12gTerminalKey;
import com.gameexpert.qa.persistence.FinalSceneH12gTerminalKeyRepository;
import com.gameexpert.qa.persistence.FinalSceneH12gTerminalRepository;
import com.gameexpert.api.persistence.WorldStore;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Durable authenticated terminal storage used by the Spring H12g final-scene binding. */
@Service
public class FinalSceneH12gTerminalService {
    public static final String ENVELOPE_SCHEMA = "game-expert.qa-final-scene-h12g-terminal-envelope/v1";
    public static final String DURABLE_SCHEMA = "game-expert.qa-final-scene-h12g-module/v1";
    private static final char ENVELOPE_SEPARATOR = '~';
    private static final Pattern MAC_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_DURABLE_BYTES = 262_144;
    private static final int MAX_CANDIDATES = 4_096;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final String[] DURABLE_KEYS = {
        "schema", "version", "sourceIdentity", "state", "equivalence", "operationId",
        "idempotencyKey", "operation", "candidates", "attemptId", "leaseNonce",
        "leaseExpiresAtMs", "leaseSourceInstance"
    };
    private static final String[] DURABLE_CANDIDATE_KEYS = {
        "roleOrdinal", "candidateOrdinal"
    };
    private static final Base64.Encoder PAYLOAD_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder PAYLOAD_DECODER = Base64.getUrlDecoder();
    private static final long FIRST_KEY_EPOCH = 1L;
    private static final int KEY_INSTALL_ATTEMPTS = 8;
    private static final ObjectMapper DURABLE_JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final FinalSceneH12gTerminalRepository terminals;
    private final FinalSceneH12gTerminalKeyRepository keys;
    private final WorldStore worlds;
    private final Consumer<byte[]> entropy;
    private final Function<Supplier<FinalSceneH12gTerminalKey>, FinalSceneH12gTerminalKey>
            keyTransaction;

    @Autowired
    public FinalSceneH12gTerminalService(FinalSceneH12gTerminalRepository terminals,
            FinalSceneH12gTerminalKeyRepository keys,
            WorldStore worlds,
            PlatformTransactionManager transactionManager) {
        this(terminals, keys, worlds, new SecureRandom()::nextBytes,
                requiresNewKeyTransaction(transactionManager));
    }

    FinalSceneH12gTerminalService(FinalSceneH12gTerminalRepository terminals,
            FinalSceneH12gTerminalKeyRepository keys, WorldStore worlds,
            Consumer<byte[]> entropy) {
        this(terminals, keys, worlds, entropy, Supplier::get);
    }

    private FinalSceneH12gTerminalService(FinalSceneH12gTerminalRepository terminals,
            FinalSceneH12gTerminalKeyRepository keys, WorldStore worlds,
            Consumer<byte[]> entropy,
            Function<Supplier<FinalSceneH12gTerminalKey>, FinalSceneH12gTerminalKey>
                    keyTransaction) {
        this.terminals = Objects.requireNonNull(terminals, "H12g terminal repository");
        this.keys = Objects.requireNonNull(keys, "H12g terminal key repository");
        this.worlds = Objects.requireNonNull(worlds, "world repository");
        this.entropy = Objects.requireNonNull(entropy, "H12g terminal entropy");
        this.keyTransaction = Objects.requireNonNull(keyTransaction, "H12g key transaction");
    }

    /** One ordinal from the authenticated durable state, with no opaque product capability. */
    public record CurrentCandidate(long roleOrdinal, long candidateOrdinal) {
        public CurrentCandidate {
            requireOrdinal(roleOrdinal, "role ordinal");
            requireOrdinal(candidateOrdinal, "candidate ordinal");
        }
    }

    /**
     * Immutable view of the exact authenticated terminal image used by a consumer.  The
     * envelope is returned only after the current MAC key, binding and durable JSON all verify.
     */
    public record AuthenticatedCurrent(String sourceIdentity, String operationId,
            String idempotencyKey, String state, String operation,
            List<CurrentCandidate> candidates, String authorization,
            String requestFingerprint, String equivalence, long version) {
        public AuthenticatedCurrent {
            if (!isSourceIdentity(sourceIdentity) || !canonical(operationId, 512)
                    || !isSourceIdentity(idempotencyKey) || !canonical(state, 32)
                    || !canonical(operation, 32) || candidates == null
                    || !bounded(authorization, FinalSceneH12gTerminal.MAX_ENVELOPE_BYTES)
                    || equivalence == null || version <= 0 || version == Long.MAX_VALUE) {
                throw new IllegalArgumentException("H12g authenticated current is invalid");
            }
            candidates = List.copyOf(candidates);
        }

        /** Alias matching the terminal vocabulary used by callers. */
        public String envelope() {
            return authorization;
        }
    }

    /**
     * Reads and verifies the current durable image under the world -> key -> terminal lock
     * order. The supplied authorization must be the exact currently stored envelope.
     */
    @Transactional
    public AuthenticatedCurrent authenticatedCurrent(long worldId, String sourceIdentity,
            String terminalKey, String authorization) {
        return authenticatedCurrentInternal(worldId, sourceIdentity, terminalKey, authorization);
    }

    /** Variant that binds both the exact authorization and the expected idempotency key. */
    @Transactional
    public AuthenticatedCurrent authenticatedCurrent(long worldId, String sourceIdentity,
            String terminalKey, String idempotencyKey, String authorization) {
        AuthenticatedCurrent current = authenticatedCurrent(worldId, sourceIdentity, terminalKey,
                authorization);
        if (!Objects.equals(idempotencyKey, current.idempotencyKey())) {
            throw new IllegalStateException("H12g idempotency key is not the exact current key");
        }
        return current;
    }

    /** Descriptive alias for callers that name the operation as an authenticated read. */
    @Transactional
    public AuthenticatedCurrent readAuthenticatedCurrent(long worldId, String sourceIdentity,
            String terminalKey, String authorization) {
        return authenticatedCurrent(worldId, sourceIdentity, terminalKey, authorization);
    }

    /** Alias preserving the explicit idempotency binding for consumer callers. */
    @Transactional
    public AuthenticatedCurrent readAuthenticatedCurrent(long worldId, String sourceIdentity,
            String terminalKey, String idempotencyKey, String authorization) {
        return authenticatedCurrent(worldId, sourceIdentity, terminalKey, idempotencyKey,
                authorization);
    }

    private AuthenticatedCurrent authenticatedCurrentInternal(long worldId, String sourceIdentity,
            String terminalKey, String authorization) {
        requireActiveTransaction("H12g authenticated current read");
        FinalSceneH12gTerminal.requireBinding(worldId, sourceIdentity, terminalKey);
        lockWorld(worldId);
        FinalSceneH12gTerminalKey macKey = lockedMacKey();
        FinalSceneH12gTerminal row = terminals.findBindingForUpdate(
                FinalSceneH12gTerminal.bindingDigest(worldId, sourceIdentity, terminalKey))
                .orElseThrow(() -> new IllegalStateException(
                        "H12g authenticated current terminal row is missing"));
        if (!sameBinding(row, worldId, sourceIdentity, terminalKey)) {
            throw new IllegalStateException("H12g terminal binding digest collision");
        }
        requireCurrentKey(row, macKey);
        String envelope = row.authenticatedEnvelope();
        OpenedEnvelope opened = openWithKey(macKey, worldId, sourceIdentity, terminalKey, envelope);
        if (opened == null) {
            throw new IllegalStateException("H12g terminal row envelope is unauthenticated");
        }
        if (authorization == null || !authorization.equals(envelope)) {
            throw new IllegalStateException(
                    "H12g authorization envelope is not the exact current envelope");
        }
        DurableState durable = decodeDurableState(opened.payload());
        if (!sourceIdentity.equals(durable.sourceIdentity())
                || !terminalKey.equals(durable.operationId())) {
            throw new IllegalStateException("H12g durable state binding is not current");
        }
        return new AuthenticatedCurrent(durable.sourceIdentity(), durable.operationId(),
                durable.idempotencyKey(), durable.state(), durable.operation(),
                durable.candidates(), envelope, durable.requestFingerprint(),
                durable.equivalence(), durable.version());
    }

    private static DurableState decodeDurableState(String raw) {
        if (raw == null || raw.isEmpty()
                || FinalSceneH12gTerminal.strictUtf8Length(raw) > MAX_DURABLE_BYTES) {
            throw new IllegalStateException("H12g durable state is outside its bounded capacity");
        }
        try {
            JsonNode root = DURABLE_JSON.readTree(raw);
            requireExactKeys(root, DURABLE_KEYS);
            if (!raw.equals(DURABLE_JSON.writeValueAsString(root))) {
                throw new IllegalArgumentException("H12g durable state is not canonical JSON");
            }

            String schema = requiredText(root, "schema");
            long version = requiredPositiveLong(root, "version");
            String sourceIdentity = requiredSource(root, "sourceIdentity");
            String state = requiredText(root, "state");
            String equivalence = requiredCanonical(root, "equivalence", MAX_DURABLE_BYTES);
            String operationId = requiredCanonical(root, "operationId", 512);
            String idempotencyKey = requiredSource(root, "idempotencyKey");
            String operation = requiredText(root, "operation");
            String attemptId = requiredSource(root, "attemptId");
            String leaseNonce = nullableSource(root, "leaseNonce");
            Long leaseExpiresAtMs = nullablePositiveLong(root, "leaseExpiresAtMs");
            String leaseSourceInstance = nullableSource(root, "leaseSourceInstance");

            if (!DURABLE_SCHEMA.equals(schema) || !legalState(state)
                    || !legalOperation(state, operation)
                    || !leaseShape(state, leaseNonce, leaseExpiresAtMs, leaseSourceInstance)) {
                throw new IllegalArgumentException("H12g durable state transition is invalid");
            }
            JsonNode candidateNodes = root.get("candidates");
            if (candidateNodes == null || !candidateNodes.isArray()
                    || candidateNodes.size() > MAX_CANDIDATES) {
                throw new IllegalArgumentException("H12g durable candidates are invalid");
            }
            List<CurrentCandidate> candidates = new ArrayList<>(candidateNodes.size());
            for (JsonNode candidate : candidateNodes) {
                requireExactKeys(candidate, DURABLE_CANDIDATE_KEYS);
                candidates.add(new CurrentCandidate(
                        requiredOrdinal(candidate, "roleOrdinal"),
                        requiredOrdinal(candidate, "candidateOrdinal")));
            }
            String requestFingerprint = extractRequestFingerprint(equivalence);
            return new DurableState(version, sourceIdentity, state, equivalence, operationId,
                    idempotencyKey, operation, List.copyOf(candidates), attemptId,
                    leaseNonce, leaseExpiresAtMs, leaseSourceInstance, requestFingerprint);
        } catch (RuntimeException invalidState) {
            if (invalidState instanceof IllegalStateException) throw invalidState;
            throw new IllegalStateException("H12g durable state failed strict decoding", invalidState);
        }
    }

    private static String extractRequestFingerprint(String equivalence) {
        try {
            JsonNode root = DURABLE_JSON.readTree(equivalence);
            if (root == null || !root.isObject() || !exactKeys(root,
                    "request", "sourceIdentity", "activationIdentity", "candidates")) {
                return null;
            }
            JsonNode request = root.get("request");
            return request != null && request.isString()
                    && canonical(request.asString(), MAX_DURABLE_BYTES)
                    ? request.asString() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean legalState(String state) {
        return "CLAIMED".equals(state) || "RETRYABLE".equals(state)
                || "EXECUTED".equals(state) || "PUBLISHING".equals(state)
                || "PUBLISHED".equals(state) || "ABANDONED".equals(state);
    }

    private static boolean legalOperation(String state, String operation) {
        return switch (state) {
            case "CLAIMED", "PUBLISHING" -> "PENDING".equals(operation);
            case "RETRYABLE" -> "RETRY".equals(operation);
            case "EXECUTED" -> "SUCCESS".equals(operation)
                    || "RETRY".equals(operation) || "REJECTED".equals(operation)
                    || "CLASSIFIED_FAILURE".equals(operation);
            case "PUBLISHED" -> "SUCCESS".equals(operation);
            case "ABANDONED" -> "CLASSIFIED_FAILURE".equals(operation);
            default -> false;
        };
    }

    private static boolean leaseShape(String state, String leaseNonce, Long leaseExpiresAtMs,
            String leaseSourceInstance) {
        boolean leased = "CLAIMED".equals(state) || "PUBLISHING".equals(state);
        return leased == (leaseNonce != null && leaseExpiresAtMs != null
                && leaseSourceInstance != null);
    }

    private static void requireExactKeys(JsonNode object, String... expectedKeys) {
        if (!exactKeys(object, expectedKeys)) {
            throw new IllegalArgumentException("H12g durable JSON members are invalid");
        }
    }

    private static boolean exactKeys(JsonNode object, String... expectedKeys) {
        if (object == null || !object.isObject() || object.size() != expectedKeys.length) {
            return false;
        }
        int index = 0;
        Set<String> expected = Set.of(expectedKeys);
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!expected.contains(field.getKey()) || !expectedKeys[index++].equals(field.getKey())) {
                return false;
            }
        }
        return index == expectedKeys.length;
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("H12g durable text field is invalid: " + field);
        }
        return value.asString();
    }

    private static String requiredSource(JsonNode object, String field) {
        String value = requiredText(object, field);
        if (!isSourceIdentity(value)) {
            throw new IllegalArgumentException("H12g durable source field is invalid: " + field);
        }
        return value;
    }

    private static String nullableSource(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("H12g durable nullable field is absent: " + field);
        }
        if (value.isNull()) return null;
        if (!value.isString() || !isSourceIdentity(value.asString())) {
            throw new IllegalArgumentException("H12g durable nullable source is invalid: " + field);
        }
        return value.asString();
    }

    private static String requiredCanonical(JsonNode object, String field, int maximumBytes) {
        String value = requiredText(object, field);
        if (!canonical(value, maximumBytes)) {
            throw new IllegalArgumentException("H12g durable canonical field is invalid: " + field);
        }
        return value;
    }

    private static long requiredOrdinal(JsonNode object, String field) {
        long value = requiredLong(object, field);
        if (value < 0 || value > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("H12g durable ordinal is invalid: " + field);
        }
        return value;
    }

    private static long requiredPositiveLong(JsonNode object, String field) {
        long value = requiredLong(object, field);
        if (value <= 0 || value == Long.MAX_VALUE || value > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("H12g durable positive integer is invalid: " + field);
        }
        return value;
    }

    private static Long nullablePositiveLong(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null) {
            throw new IllegalArgumentException("H12g durable nullable integer is absent: " + field);
        }
        if (value.isNull()) return null;
        long decoded = requiredPositiveLong(object, field);
        return decoded;
    }

    private static long requiredLong(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("H12g durable integer is invalid: " + field);
        }
        return value.longValue();
    }

    private static boolean isSourceIdentity(String value) {
        return value != null && MAC_HEX.matcher(value).matches()
                && FinalSceneH12gTerminal.strictUtf8Length(value) == 64;
    }

    private static boolean canonical(String value, int maximumBytes) {
        return bounded(value, maximumBytes)
                && value.equals(Normalizer.normalize(value, Normalizer.Form.NFC))
                && value.equals(value.strip())
                && value.chars().noneMatch(character -> character <= 0x1f || character == 0x7f);
    }

    private static boolean bounded(String value, int maximumBytes) {
        return value != null && !value.isEmpty()
                && FinalSceneH12gTerminal.strictUtf8Length(value) <= maximumBytes;
    }

    private static void requireOrdinal(long value, String label) {
        if (value < 0 || value > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("H12g " + label + " is invalid");
        }
    }

    private static void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(operation + " requires an active transaction");
        }
    }

    @Transactional(readOnly = true)
    public String read(long worldId, String sourceIdentity, String terminalKey) {
        FinalSceneH12gTerminal.requireBinding(worldId, sourceIdentity, terminalKey);
        if (worlds.findById(worldId).isEmpty()) {
            return null;
        }
        FinalSceneH12gTerminal row = terminals.findByBindingDigest(
                FinalSceneH12gTerminal.bindingDigest(worldId, sourceIdentity, terminalKey))
                .orElse(null);
        if (row == null) return null;
        if (!sameBinding(row, worldId, sourceIdentity, terminalKey)) {
            throw new IllegalStateException("H12g terminal binding digest collision");
        }
        FinalSceneH12gTerminalKey macKey = keys.findById(
                FinalSceneH12gTerminalKey.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "H12g terminal rows exist without MAC key authority"));
        requireCurrentKey(row, macKey);
        String envelope = row.authenticatedEnvelope();
        if (openWithKey(macKey, worldId, sourceIdentity, terminalKey, envelope) == null) {
            throw new IllegalStateException("H12g terminal row envelope is unauthenticated");
        }
        return envelope;
    }

    /** Exact-string null/create/update/delete compare-and-exchange under the durable key lock. */
    @Transactional
    public boolean compareExchange(long worldId, String sourceIdentity, String terminalKey,
            String expected, String replacement) {
        FinalSceneH12gTerminal.requireBinding(worldId, sourceIdentity, terminalKey);
        lockWorld(worldId);
        if (expected != null) {
            FinalSceneH12gTerminal.requireEnvelope(expected);
        }
        if (expected == null && replacement == null) {
            FinalSceneH12gTerminal current = terminals.findByBindingDigest(
                    FinalSceneH12gTerminal.bindingDigest(worldId, sourceIdentity, terminalKey))
                    .orElse(null);
            if (current == null) {
                return true;
            }
            FinalSceneH12gTerminalKey existingKey = keys.findById(
                    FinalSceneH12gTerminalKey.SINGLETON_ID)
                    .orElseThrow(() -> new IllegalStateException(
                            "H12g terminal rows exist without MAC key authority"));
            requireCurrentKey(current, existingKey);
            return false;
        }
        FinalSceneH12gTerminalKey macKey = lockedMacKey();
        if (replacement != null) {
            FinalSceneH12gTerminal.requireEnvelope(replacement);
            if (openWithKey(macKey, worldId, sourceIdentity, terminalKey, replacement) == null) {
                throw new IllegalArgumentException("H12g replacement envelope is unauthenticated");
            }
        }

        FinalSceneH12gTerminal current = terminals.findBindingForUpdate(
                FinalSceneH12gTerminal.bindingDigest(worldId, sourceIdentity, terminalKey))
                .orElse(null);
        if (current != null && !sameBinding(current, worldId, sourceIdentity, terminalKey)) {
            throw new IllegalStateException("H12g terminal binding digest collision");
        }
        if (current != null) {
            requireCurrentKey(current, macKey);
        }
        if (expected != null
                && openWithKey(macKey, worldId, sourceIdentity, terminalKey, expected) == null) {
            return false;
        }
        String currentValue = current == null ? null : current.authenticatedEnvelope();
        if (!Objects.equals(currentValue, expected)) {
            return false;
        }
        if (current == null) {
            if (replacement != null) {
                terminals.saveAndFlush(new FinalSceneH12gTerminal(
                        worldId, sourceIdentity, terminalKey, macKey.keyEpoch(),
                        macKey.keyIdentity(), replacement));
            }
            return true;
        }
        if (replacement == null) {
            terminals.delete(current);
            terminals.flush();
        } else if (!replacement.equals(currentValue)) {
            current.replaceEnvelope(replacement);
            terminals.saveAndFlush(current);
        }
        return true;
    }

    @Transactional
    public String seal(long worldId, String sourceIdentity, String terminalKey,
            String canonicalPayload) {
        FinalSceneH12gTerminal.requireBinding(worldId, sourceIdentity, terminalKey);
        lockWorld(worldId);
        byte[] payload = strictPayload(canonicalPayload);
        FinalSceneH12gTerminalKey macKey = lockedMacKey();
        String encodedPayload = PAYLOAD_ENCODER.encodeToString(payload);
        String mac = hex(macKey.sign(authenticatedMaterial(
                worldId, sourceIdentity, terminalKey, macKey.keyEpoch(),
                macKey.keyIdentity(), payload)));
        String envelope = ENVELOPE_SCHEMA + ENVELOPE_SEPARATOR + macKey.keyEpoch()
                + ENVELOPE_SEPARATOR + macKey.keyIdentity()
                + ENVELOPE_SEPARATOR + encodedPayload
                + ENVELOPE_SEPARATOR + mac;
        FinalSceneH12gTerminal.requireEnvelope(envelope);
        return envelope;
    }

    @Transactional(readOnly = true)
    public String open(long worldId, String sourceIdentity, String terminalKey,
            String authenticatedEnvelope) {
        FinalSceneH12gTerminal.requireBinding(worldId, sourceIdentity, terminalKey);
        if (!isBoundedEnvelope(authenticatedEnvelope)) {
            return null;
        }
        if (worlds.findById(worldId).isEmpty()) {
            return null;
        }
        FinalSceneH12gTerminalKey macKey = keys.findById(
                FinalSceneH12gTerminalKey.SINGLETON_ID).orElse(null);
        OpenedEnvelope opened = macKey == null ? null : openWithKey(
                macKey, worldId, sourceIdentity, terminalKey, authenticatedEnvelope);
        return opened == null ? null : opened.payload();
    }

    @Transactional
    public int purgeWorld(long worldId) {
        FinalSceneH12gTerminal.requireWorldId(worldId);
        lockWorld(worldId);
        lockedMacKey();
        return terminals.deleteAllByWorldId(worldId);
    }

    private FinalSceneH12gTerminalKey lockedMacKey() {
        if (keys.findById(FinalSceneH12gTerminalKey.SINGLETON_ID).isEmpty()) {
            installFirstKeyWithRetries();
        }
        return keys.findByIdForUpdate(FinalSceneH12gTerminalKey.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "H12g terminal MAC key authority vanished"));
    }

    private void installFirstKeyWithRetries() {
        TransientDataAccessException lastTransient = null;
        for (int attempt = 0; attempt < KEY_INSTALL_ATTEMPTS; attempt++) {
            try {
                keyTransaction.apply(this::installOrLoadKey);
                return;
            } catch (TransientDataAccessException transientFailure) {
                lastTransient = transientFailure;
            }
        }
        throw new IllegalStateException("H12g terminal MAC key installation did not converge",
                lastTransient);
    }

    private void lockWorld(long worldId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("H12g terminal mutation requires an active transaction");
        }
        worlds.findByIdForShare(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
    }

    private FinalSceneH12gTerminalKey installOrLoadKey() {
        FinalSceneH12gTerminalKey existing = keys.findByIdForUpdate(
                FinalSceneH12gTerminalKey.SINGLETON_ID).orElse(null);
        if (existing != null) {
            return existing;
        }
        if (terminals.count() != 0) {
            throw new IllegalStateException("H12g terminal rows exist without MAC key authority");
        }
        byte[] material = new byte[FinalSceneH12gTerminal.MAC_KEY_BYTES];
        entropy.accept(material);
        if (material.length != FinalSceneH12gTerminal.MAC_KEY_BYTES) {
            throw new IllegalStateException("H12g terminal entropy returned invalid key material");
        }
        String identity = FinalSceneH12gTerminalKey.identity(FIRST_KEY_EPOCH, material);
        keys.installFirstKey(FinalSceneH12gTerminalKey.SINGLETON_ID, FIRST_KEY_EPOCH,
                identity, material);
        FinalSceneH12gTerminalKey installed = keys.findByIdForUpdate(
                FinalSceneH12gTerminalKey.SINGLETON_ID).orElse(null);
        if (installed == null && terminals.count() != 0) {
            throw new IllegalStateException("H12g terminal rows exist without MAC key authority");
        }
        if (installed == null) {
            throw new IllegalStateException("H12g terminal MAC key installation vanished");
        }
        return installed;
    }

    private static OpenedEnvelope openWithKey(FinalSceneH12gTerminalKey macKey, long worldId,
            String sourceIdentity, String terminalKey, String envelope) {
        try {
            int first = envelope.indexOf(ENVELOPE_SEPARATOR);
            int second = first < 0 ? -1 : envelope.indexOf(ENVELOPE_SEPARATOR, first + 1);
            int third = second < 0 ? -1 : envelope.indexOf(ENVELOPE_SEPARATOR, second + 1);
            int fourth = third < 0 ? -1 : envelope.indexOf(ENVELOPE_SEPARATOR, third + 1);
            if (first != ENVELOPE_SCHEMA.length() || second <= first + 1
                    || third <= second + 1 || fourth <= third + 1
                    || envelope.indexOf(ENVELOPE_SEPARATOR, fourth + 1) >= 0
                    || !envelope.regionMatches(0, ENVELOPE_SCHEMA, 0, ENVELOPE_SCHEMA.length())) {
                return null;
            }
            String epochText = envelope.substring(first + 1, second);
            long epoch = Long.parseLong(epochText);
            String keyIdentity = envelope.substring(second + 1, third);
            String encodedPayload = envelope.substring(third + 1, fourth);
            String suppliedMac = envelope.substring(fourth + 1);
            if (!Long.toString(epoch).equals(epochText) || epoch != macKey.keyEpoch()
                    || !keyIdentity.equals(macKey.keyIdentity())
                    || !MAC_HEX.matcher(keyIdentity).matches()
                    || !MAC_HEX.matcher(suppliedMac).matches()) {
                return null;
            }
            byte[] payload = PAYLOAD_DECODER.decode(encodedPayload);
            if (payload.length == 0 || payload.length > FinalSceneH12gTerminal.MAX_PAYLOAD_BYTES
                    || !PAYLOAD_ENCODER.encodeToString(payload).equals(encodedPayload)) {
                return null;
            }
            byte[] expectedMac = macKey.sign(authenticatedMaterial(
                    worldId, sourceIdentity, terminalKey, epoch, keyIdentity, payload));
            byte[] actualMac = fromHex(suppliedMac);
            if (!MessageDigest.isEqual(expectedMac, actualMac)) {
                return null;
            }
            String decoded = new String(payload, StandardCharsets.UTF_8);
            return MessageDigest.isEqual(payload, decoded.getBytes(StandardCharsets.UTF_8))
                    ? new OpenedEnvelope(epoch, keyIdentity, decoded) : null;
        } catch (IllegalArgumentException malformedEnvelope) {
            return null;
        }
    }

    private static byte[] strictPayload(String payload) {
        if (payload == null || payload.isEmpty()
                || FinalSceneH12gTerminal.strictUtf8Length(payload)
                        > FinalSceneH12gTerminal.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("H12g canonical payload is invalid");
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isBoundedEnvelope(String envelope) {
        if (envelope == null || envelope.isEmpty()
                || FinalSceneH12gTerminal.strictUtf8Length(envelope)
                        > FinalSceneH12gTerminal.MAX_ENVELOPE_BYTES) return false;
        int first = envelope.indexOf(ENVELOPE_SEPARATOR);
        int second = first < 0 ? -1 : envelope.indexOf(ENVELOPE_SEPARATOR, first + 1);
        int third = second < 0 ? -1 : envelope.indexOf(ENVELOPE_SEPARATOR, second + 1);
        int fourth = third < 0 ? -1 : envelope.indexOf(ENVELOPE_SEPARATOR, third + 1);
        return first == ENVELOPE_SCHEMA.length() && second > first + 1
                && third > second + 1 && fourth > third + 1
                && envelope.indexOf(ENVELOPE_SEPARATOR, fourth + 1) < 0
                && envelope.regionMatches(0, ENVELOPE_SCHEMA, 0, ENVELOPE_SCHEMA.length());
    }

    private static byte[] authenticatedMaterial(long worldId, String sourceIdentity,
            String terminalKey, long keyEpoch, String keyIdentity, byte[] payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            write(data, ENVELOPE_SCHEMA.getBytes(StandardCharsets.US_ASCII));
            data.writeLong(worldId);
            write(data, sourceIdentity.getBytes(StandardCharsets.US_ASCII));
            write(data, terminalKey.getBytes(StandardCharsets.UTF_8));
            data.writeLong(keyEpoch);
            write(data, keyIdentity.getBytes(StandardCharsets.US_ASCII));
            write(data, payload);
            data.flush();
            return bytes.toByteArray();
        } catch (IOException impossibleMemoryFailure) {
            throw new IllegalStateException("H12g terminal MAC material failed", impossibleMemoryFailure);
        }
    }

    private static void write(DataOutputStream data, byte[] value) throws IOException {
        data.writeInt(value.length);
        data.write(value);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte current : value) {
            result.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(current & 0x0f, 16));
        }
        return result.toString();
    }

    private static byte[] fromHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static boolean sameBinding(FinalSceneH12gTerminal row, long worldId,
            String sourceIdentity, String terminalKey) {
        return row.worldId() == worldId && row.sourceIdentity().equals(sourceIdentity)
                && row.terminalKey().equals(terminalKey);
    }

    private static void requireCurrentKey(FinalSceneH12gTerminal row,
            FinalSceneH12gTerminalKey key) {
        if (row.keyEpoch() != key.keyEpoch() || !row.keyIdentity().equals(key.keyIdentity())) {
            throw new IllegalStateException("H12g terminal row belongs to another MAC key epoch");
        }
    }

    private static Function<Supplier<FinalSceneH12gTerminalKey>, FinalSceneH12gTerminalKey>
            requiresNewKeyTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transaction manager"));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return supplier -> transaction.execute(ignored -> supplier.get());
    }

    private record DurableState(long version, String sourceIdentity, String state,
            String equivalence, String operationId, String idempotencyKey, String operation,
            List<CurrentCandidate> candidates, String attemptId, String leaseNonce,
            Long leaseExpiresAtMs, String leaseSourceInstance, String requestFingerprint) {
    }

    private record OpenedEnvelope(long keyEpoch, String keyIdentity, String payload) { }
}
