package com.gameexpert.qa;

import com.gameexpert.qa.persistence.FinalSceneH12gConsumerReceipt;
import com.gameexpert.qa.persistence.FinalSceneH12gConsumerReceiptRepository;
import com.gameexpert.qa.persistence.FinalSceneH12gTerminal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Durable Spring consumers for the authenticated H12g product and publication boundaries. */
@Service
public class FinalSceneH12gConsumerService {
    public static final String MODULE_SCHEMA = FinalSceneH12gTerminalService.DURABLE_SCHEMA;
    public static final String PRODUCT_SCHEMA =
            "game-expert.qa-final-scene-h12g-product/v1";
    public static final String PUBLICATION_SCHEMA =
            "game-expert.qa-final-scene-h12g-publication/v1";
    public static final String PRODUCT_RESULT = "SUCCESS";
    public static final String PUBLICATION_RESULT = "ACKNOWLEDGED";
    public static final int MAX_CANDIDATES = 4_096;
    public static final int MAX_DURABLE_BYTES = 262_144;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final String[] EQUIVALENCE_KEYS = {
        "request", "sourceIdentity", "activationIdentity", "candidates"
    };
    private static final String[] EQUIVALENCE_CANDIDATE_KEYS = {
        "roleOrdinal", "candidateOrdinal", "present", "fresh", "kind", "target", "origin",
        "receiptLineage", "authenticatedIdentity"
    };
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final FinalSceneH12gTerminalService terminals;
    private final FinalSceneH12gConsumerReceiptRepository receipts;

    @Autowired
    public FinalSceneH12gConsumerService(FinalSceneH12gTerminalService terminals,
            FinalSceneH12gConsumerReceiptRepository receipts) {
        this.terminals = Objects.requireNonNull(terminals, "H12g terminal service");
        this.receipts = Objects.requireNonNull(receipts, "H12g consumer receipt repository");
    }

    /** Candidate facts accepted by the durable product boundary; opaque receipts never persist. */
    public record CandidateFact(long roleOrdinal, long candidateOrdinal, boolean present,
            boolean fresh, String kind, String target, String origin) {
        public CandidateFact {
            requireOrdinal(roleOrdinal, "role ordinal");
            requireOrdinal(candidateOrdinal, "candidate ordinal");
            requireCanonical(kind, 256, "candidate kind");
            requireCanonical(target, 256, "candidate target");
            requireCanonical(origin, 256, "candidate origin");
        }
    }

    /** Strict product command projection. requestFingerprint is the canonical request JSON. */
    public record ProductCommand(String schema, String operationId, String idempotencyKey,
            String authorization, String requestFingerprint, List<CandidateFact> candidates) {
        public ProductCommand {
            if (!PRODUCT_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("H12g product schema is invalid");
            }
            requireCanonical(operationId, 512, "product operation ID");
            requireSource(idempotencyKey, "product idempotency key");
            requireBounded(authorization, FinalSceneH12gTerminal.MAX_ENVELOPE_BYTES,
                    "product authorization");
            requireCanonical(requestFingerprint, MAX_DURABLE_BYTES,
                    "product request fingerprint");
            candidates = immutableCandidates(candidates);
        }
    }

    /** Strict publication command projection. */
    public record PublicationCommand(String schema, String operationId, String idempotencyKey,
            String authorization, List<FinalSceneH12gTerminalService.CurrentCandidate> candidates) {
        public PublicationCommand {
            if (!PUBLICATION_SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("H12g publication schema is invalid");
            }
            requireCanonical(operationId, 512, "publication operation ID");
            requireSource(idempotencyKey, "publication idempotency key");
            requireBounded(authorization, FinalSceneH12gTerminal.MAX_ENVELOPE_BYTES,
                    "publication authorization");
            candidates = immutableOrdinals(candidates);
        }
    }

    /** Commit-safe response copied from the durable row, never an entity reference. */
    public record ConsumerReceipt(String schema, String operationId, String idempotencyKey,
            String authorization, String result) {
        public ConsumerReceipt {
            if ((!PRODUCT_SCHEMA.equals(schema) && !PUBLICATION_SCHEMA.equals(schema))
                    || !canonical(operationId, 512) || !isSource(idempotencyKey)
                    || !bounded(authorization, FinalSceneH12gTerminal.MAX_ENVELOPE_BYTES)
                    || !canonical(result, 32) || !validResult(schema, result)) {
                throw new IllegalArgumentException("H12g consumer receipt response is invalid");
            }
        }
    }

    /** Consumes one CLAIMED/PENDING product image and durably replays its exact result. */
    @Transactional
    public ConsumerReceipt consumeProduct(long worldId, String sourceIdentity,
            ProductCommand command) {
        Objects.requireNonNull(command, "H12g product command");
        FinalSceneH12gTerminalService.AuthenticatedCurrent current = current(
                worldId, sourceIdentity, command.operationId(), command.authorization());
        requireExactBinding(current, sourceIdentity, command.operationId(),
                command.idempotencyKey());
        Equivalence facts = decodeEquivalence(current.equivalence(),
                sourceIdentity, current.requestFingerprint());
        if (!command.requestFingerprint().equals(facts.requestFingerprint())
                || !sameFacts(command.candidates(), facts.candidates())) {
            throw conflict("H12g product request facts do not match the durable binding");
        }
        return consume(worldId, sourceIdentity, FinalSceneH12gConsumerReceipt.PRODUCT_PHASE,
                command.operationId(), command.idempotencyKey(), command.authorization(),
                command.requestFingerprint(), current, PRODUCT_SCHEMA, PRODUCT_RESULT);
    }

    /** Consumes one PUBLISHING/PENDING publication image and durably replays its exact result. */
    @Transactional
    public ConsumerReceipt consumePublication(long worldId, String sourceIdentity,
            PublicationCommand command) {
        Objects.requireNonNull(command, "H12g publication command");
        FinalSceneH12gTerminalService.AuthenticatedCurrent current = current(
                worldId, sourceIdentity, command.operationId(), command.authorization());
        requireExactBinding(current, sourceIdentity, command.operationId(),
                command.idempotencyKey());
        Equivalence facts = decodeEquivalence(current.equivalence(),
                sourceIdentity, current.requestFingerprint());
        if (!sameOrdinals(command.candidates(), facts.candidates())) {
            throw conflict("H12g publication evidence does not match the durable binding");
        }
        return consume(worldId, sourceIdentity, FinalSceneH12gConsumerReceipt.PUBLICATION_PHASE,
                command.operationId(), command.idempotencyKey(), command.authorization(),
                facts.requestFingerprint(), current, PUBLICATION_SCHEMA, PUBLICATION_RESULT);
    }

    /** Convenience overload for callers that already have the canonical command components. */
    @Transactional
    public ConsumerReceipt consumeProduct(long worldId, String sourceIdentity,
            String operationId, String idempotencyKey, String authorization,
            String requestFingerprint, List<CandidateFact> candidates) {
        return consumeProduct(worldId, sourceIdentity, new ProductCommand(PRODUCT_SCHEMA,
                operationId, idempotencyKey, authorization, requestFingerprint, candidates));
    }

    /** Convenience overload for callers that already have the canonical evidence projection. */
    @Transactional
    public ConsumerReceipt consumePublication(long worldId, String sourceIdentity,
            String operationId, String idempotencyKey, String authorization,
            List<FinalSceneH12gTerminalService.CurrentCandidate> candidates) {
        return consumePublication(worldId, sourceIdentity, new PublicationCommand(
                PUBLICATION_SCHEMA, operationId, idempotencyKey, authorization, candidates));
    }

    private FinalSceneH12gTerminalService.AuthenticatedCurrent current(long worldId,
            String sourceIdentity, String operationId, String authorization) {
        FinalSceneH12gTerminal.requireBinding(worldId, sourceIdentity, operationId);
        return terminals.authenticatedCurrent(worldId, sourceIdentity, operationId, authorization);
    }

    private static void requireExactBinding(
            FinalSceneH12gTerminalService.AuthenticatedCurrent current, String sourceIdentity,
            String operationId, String idempotencyKey) {
        if (!sourceIdentity.equals(current.sourceIdentity())
                || !operationId.equals(current.operationId())
                || !idempotencyKey.equals(current.idempotencyKey())) {
            throw conflict("H12g durable binding is not exact");
        }
    }

    private ConsumerReceipt consume(long worldId, String sourceIdentity, String phase,
            String operationId, String idempotencyKey, String authorization,
            String requestFingerprint, FinalSceneH12gTerminalService.AuthenticatedCurrent current,
            String schema, String result) {
        FinalSceneH12gConsumerReceipt stored = receipts
                .findByWorldIdAndSourceIdentityAndPhaseAndIdempotencyKeyForUpdate(
                        worldId, sourceIdentity, phase, idempotencyKey)
                .orElse(null);
        if (stored != null) {
            if (!worldIdEquals(stored.worldId(), worldId)
                    || !sourceIdentity.equals(stored.sourceIdentity())
                    || !phase.equals(stored.phase())
                    || !stored.matches(operationId, idempotencyKey, authorization,
                            requestFingerprint)) {
                throw conflict("H12g consumer receipt identity conflict");
            }
            return immutableReceipt(schema, stored);
        }
        if ("PRODUCT".equals(phase)
                && (!"CLAIMED".equals(current.state()) || !"PENDING".equals(current.operation()))) {
            throw conflict("H12g product durable state is not CLAIMED/PENDING");
        }
        if ("PUBLICATION".equals(phase)
                && (!"PUBLISHING".equals(current.state())
                        || !"PENDING".equals(current.operation()))) {
            throw conflict("H12g publication durable state is not PUBLISHING/PENDING");
        }
        FinalSceneH12gConsumerReceipt receipt = new FinalSceneH12gConsumerReceipt(
                worldId, sourceIdentity, phase, idempotencyKey, operationId, authorization,
                requestFingerprint, result);
        receipts.saveAndFlush(receipt);
        return new ConsumerReceipt(schema, operationId, idempotencyKey, authorization, result);
    }

    private static ConsumerReceipt immutableReceipt(String schema,
            FinalSceneH12gConsumerReceipt stored) {
        return new ConsumerReceipt(schema, stored.operationId(), stored.idempotencyKey(),
                stored.authorization(), stored.result());
    }

    private static Equivalence decodeEquivalence(String raw,
            String expectedSource, String expectedRequestFingerprint) {
        if (raw == null || raw.isEmpty() || FinalSceneH12gTerminal.strictUtf8Length(raw)
                > MAX_DURABLE_BYTES) {
            throw conflict("H12g durable equivalence is outside its bounded capacity");
        }
        try {
            JsonNode root = JSON.readTree(raw);
            requireExactKeys(root, EQUIVALENCE_KEYS);
            if (!raw.equals(JSON.writeValueAsString(root))) {
                throw new IllegalArgumentException("H12g durable equivalence is not canonical");
            }
            String request = canonicalText(root, "request", MAX_DURABLE_BYTES);
            String source = sourceText(root, "sourceIdentity");
            canonicalText(root, "activationIdentity", 256);
            if (!expectedSource.equals(source)) {
                throw new IllegalArgumentException("H12g durable equivalence source mismatch");
            }
            if (expectedRequestFingerprint != null
                    && !expectedRequestFingerprint.equals(request)) {
                throw new IllegalArgumentException("H12g durable request fingerprint mismatch");
            }
            JsonNode candidateNodes = root.get("candidates");
            if (candidateNodes == null || !candidateNodes.isArray()
                    || candidateNodes.size() > MAX_CANDIDATES) {
                throw new IllegalArgumentException("H12g durable equivalence candidates invalid");
            }
            List<EquivalenceCandidate> candidates = new ArrayList<>(candidateNodes.size());
            for (JsonNode candidate : candidateNodes) {
                requireExactKeys(candidate, EQUIVALENCE_CANDIDATE_KEYS);
                candidates.add(new EquivalenceCandidate(
                        ordinal(candidate, "roleOrdinal"), ordinal(candidate, "candidateOrdinal"),
                        booleanValue(candidate, "present"), booleanValue(candidate, "fresh"),
                        canonicalText(candidate, "kind", 256),
                        canonicalText(candidate, "target", 256),
                        canonicalText(candidate, "origin", 256),
                        optionalCanonicalText(candidate, "receiptLineage", 256),
                        optionalCanonicalText(candidate, "authenticatedIdentity", 256)));
            }
            return new Equivalence(request, List.copyOf(candidates));
        } catch (RuntimeException invalid) {
            throw conflict("H12g durable equivalence failed strict decoding");
        }
    }

    private static boolean sameFacts(List<CandidateFact> supplied,
            List<EquivalenceCandidate> durable) {
        if (supplied.size() != durable.size()) return false;
        for (int index = 0; index < supplied.size(); index++) {
            CandidateFact left = supplied.get(index);
            EquivalenceCandidate right = durable.get(index);
            if (left.roleOrdinal() != right.roleOrdinal()
                    || left.candidateOrdinal() != right.candidateOrdinal()
                    || left.present() != right.present() || left.fresh() != right.fresh()
                    || !left.kind().equals(right.kind()) || !left.target().equals(right.target())
                    || !left.origin().equals(right.origin())) return false;
        }
        return true;
    }

    private static boolean sameOrdinals(
            List<FinalSceneH12gTerminalService.CurrentCandidate> supplied,
            List<EquivalenceCandidate> durable) {
        if (supplied.size() != durable.size()) return false;
        for (int index = 0; index < supplied.size(); index++) {
            FinalSceneH12gTerminalService.CurrentCandidate left = supplied.get(index);
            EquivalenceCandidate right = durable.get(index);
            if (left.roleOrdinal() != right.roleOrdinal()
                    || left.candidateOrdinal() != right.candidateOrdinal()) return false;
        }
        return true;
    }

    private static void requireExactKeys(JsonNode object, String... expectedKeys) {
        if (object == null || !object.isObject() || object.size() != expectedKeys.length) {
            throw new IllegalArgumentException("H12g JSON members are invalid");
        }
        int index = 0;
        Set<String> expected = Set.of(expectedKeys);
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!expected.contains(field.getKey()) || !expectedKeys[index++].equals(field.getKey())) {
                throw new IllegalArgumentException("H12g JSON members are not canonical");
            }
        }
    }

    private static String canonicalText(JsonNode object, String field, int maximumBytes) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString()
                || !canonical(value.asString(), maximumBytes)) {
            throw new IllegalArgumentException("H12g canonical field is invalid: " + field);
        }
        return value.asString();
    }

    private static String optionalCanonicalText(JsonNode object, String field, int maximumBytes) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString()
                || !bounded(value.asString(), maximumBytes)
                || !value.asString().equals(Normalizer.normalize(value.asString(),
                        Normalizer.Form.NFC))
                || value.asString().chars()
                        .anyMatch(character -> character <= 0x1f || character == 0x7f)) {
            throw new IllegalArgumentException("H12g optional canonical field is invalid: " + field);
        }
        return value.asString();
    }

    private static String sourceText(JsonNode object, String field) {
        String value = canonicalText(object, field, 64);
        requireSource(value, field);
        return value;
    }

    private static long ordinal(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("H12g ordinal is invalid: " + field);
        }
        long ordinal = value.longValue();
        requireOrdinal(ordinal, field);
        return ordinal;
    }

    private static boolean booleanValue(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("H12g boolean is invalid: " + field);
        }
        return value.asBoolean();
    }

    private static List<CandidateFact> immutableCandidates(List<CandidateFact> candidates) {
        if (candidates == null || candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("H12g candidate list is invalid");
        }
        List<CandidateFact> copy = new ArrayList<>(candidates.size());
        for (CandidateFact candidate : candidates) {
            copy.add(Objects.requireNonNull(candidate, "H12g candidate fact"));
        }
        return List.copyOf(copy);
    }

    private static List<FinalSceneH12gTerminalService.CurrentCandidate> immutableOrdinals(
            List<FinalSceneH12gTerminalService.CurrentCandidate> candidates) {
        if (candidates == null || candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("H12g publication candidates are invalid");
        }
        List<FinalSceneH12gTerminalService.CurrentCandidate> copy = new ArrayList<>(
                candidates.size());
        for (FinalSceneH12gTerminalService.CurrentCandidate candidate : candidates) {
            copy.add(Objects.requireNonNull(candidate, "H12g publication candidate"));
        }
        return List.copyOf(copy);
    }

    private static void requireSource(String value, String label) {
        if (!isSource(value)) throw new IllegalArgumentException("H12g " + label + " is invalid");
    }

    private static boolean isSource(String value) {
        return value != null && SHA256.matcher(value).matches()
                && FinalSceneH12gTerminal.strictUtf8Length(value) == 64;
    }

    private static void requireCanonical(String value, int maximumBytes, String label) {
        if (!canonical(value, maximumBytes)) {
            throw new IllegalArgumentException("H12g " + label + " is invalid");
        }
    }

    private static boolean canonical(String value, int maximumBytes) {
        return bounded(value, maximumBytes)
                && value.equals(Normalizer.normalize(value, Normalizer.Form.NFC))
                && value.equals(value.strip())
                && value.chars().noneMatch(character -> character <= 0x1f || character == 0x7f);
    }

    private static void requireBounded(String value, int maximumBytes, String label) {
        if (!bounded(value, maximumBytes)) {
            throw new IllegalArgumentException("H12g " + label + " is invalid");
        }
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

    private static boolean worldIdEquals(Long value, long expected) {
        return value != null && value == expected;
    }

    private static boolean validResult(String schema, String result) {
        if (PRODUCT_SCHEMA.equals(schema)) {
            return "SUCCESS".equals(result) || "RETRY".equals(result)
                    || "REJECTED".equals(result) || "CLASSIFIED_FAILURE".equals(result);
        }
        return "ACKNOWLEDGED".equals(result) || "RETRY".equals(result);
    }

    private static IllegalStateException conflict(String message) {
        return new IllegalStateException(message);
    }

    private record EquivalenceCandidate(long roleOrdinal, long candidateOrdinal, boolean present,
            boolean fresh, String kind, String target, String origin, String receiptLineage,
            String authenticatedIdentity) {
    }

    private record Equivalence(String requestFingerprint, List<EquivalenceCandidate> candidates) {
    }
}
