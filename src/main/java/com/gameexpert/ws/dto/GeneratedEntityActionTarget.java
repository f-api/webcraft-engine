package com.gameexpert.ws.dto;

import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts.Kind;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Pure, unregistered input boundary for staged generated-entity actions. */
public final class GeneratedEntityActionTarget {
    private static final long MAX_JSON_SAFE_INTEGER = (1L << 53) - 1L;
    private static final Set<String> TARGET_KEYS = Set.of("schema", "kind", "entityId");
    private static final Set<String> INTERACT_KEYS = Set.of("type", "target", "hand");
    private static final Set<String> CORRELATED_INTERACT_KEYS = Set.of("type", "target", "hand", "requestId");
    private static final Set<String> ATTACK_KEYS = Set.of("type", "target", "sprinting");

    private final Kind kind;
    private final long entityId;

    private GeneratedEntityActionTarget(Kind kind, long entityId) {
        this.kind = Objects.requireNonNull(kind, "generated entity kind");
        this.entityId = entityId;
    }

    public Kind kind() { return kind; }
    public long entityId() { return entityId; }
    public int schema() { return 1; }

    public static GeneratedEntityActionTarget parse(JsonNode value) {
        requireExactObject(value, TARGET_KEYS, "generated entity target");
        JsonNode schema = value.get("schema");
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt()
                || schema.intValue() != 1) {
            throw new IllegalArgumentException("generated entity target schema must be 1");
        }
        JsonNode kindNode = value.get("kind");
        if (kindNode == null || !kindNode.isString()) {
            throw new IllegalArgumentException("generated entity target kind must be a string");
        }
        Kind kind;
        try {
            kind = Kind.valueOf(kindNode.asString());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown generated entity target kind", unknown);
        }
        JsonNode id = value.get("entityId");
        if (id == null || !id.isIntegralNumber() || !id.canConvertToLong()) {
            throw new IllegalArgumentException("generated entity ID must be an integer");
        }
        long entityId = id.longValue();
        if (entityId <= 0L || entityId > MAX_JSON_SAFE_INTEGER) {
            throw new IllegalArgumentException("generated entity ID is outside the JSON-safe domain");
        }
        return new GeneratedEntityActionTarget(kind, entityId);
    }

    public static Interact parseInteract(JsonNode value) {
        boolean correlated = value != null && value.has("requestId");
        requireExactObject(value, correlated ? CORRELATED_INTERACT_KEYS : INTERACT_KEYS,
                "generated entity interact action");
        if (correlated) {
            JsonNode requestId = value.get("requestId");
            if (!requestId.isIntegralNumber() || !requestId.canConvertToLong()
                    || requestId.longValue() <= 0 || requestId.longValue() > MAX_JSON_SAFE_INTEGER) {
                throw new IllegalArgumentException("requestId must be a positive safe integer");
            }
        }
        requireType(value, "entityInteract");
        JsonNode hand = value.get("hand");
        if (hand == null || !hand.isString()) {
            throw new IllegalArgumentException("generated entity interaction hand must be a string");
        }
        Hand parsedHand = switch (hand.asString()) {
            case "main" -> Hand.MAIN;
            case "offhand" -> Hand.OFFHAND;
            default -> throw new IllegalArgumentException(
                    "generated entity interaction hand must be main or offhand");
        };
        return new Interact(parse(value.get("target")), parsedHand);
    }

    public static Attack parseAttack(JsonNode value) {
        requireExactObject(value, ATTACK_KEYS, "generated entity attack action");
        requireType(value, "entityAttack");
        JsonNode sprinting = value.get("sprinting");
        if (sprinting == null || !sprinting.isBoolean()) {
            throw new IllegalArgumentException("generated entity attack sprinting must be boolean");
        }
        return new Attack(parse(value.get("target")), sprinting.booleanValue());
    }

    private static void requireType(JsonNode value, String expected) {
        JsonNode type = value.get("type");
        if (type == null || !type.isString() || !expected.equals(type.asString())) {
            throw new IllegalArgumentException("generated entity action type mismatch");
        }
    }

    private static void requireExactObject(JsonNode value, Set<String> expected, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        LinkedHashSet<String> actual = new LinkedHashSet<>();
        value.propertyNames().forEach(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(label + " has unsupported fields");
        }
    }

    public enum Hand { MAIN, OFFHAND }

    public static final class Interact {
        private final GeneratedEntityActionTarget target;
        private final Hand hand;

        private Interact(GeneratedEntityActionTarget target, Hand hand) {
            this.target = target;
            this.hand = hand;
        }

        public GeneratedEntityActionTarget target() { return target; }
        public Hand hand() { return hand; }
    }

    public static final class Attack {
        private final GeneratedEntityActionTarget target;
        private final boolean sprinting;

        private Attack(GeneratedEntityActionTarget target, boolean sprinting) {
            this.target = target;
            this.sprinting = sprinting;
        }

        public GeneratedEntityActionTarget target() { return target; }
        public boolean sprinting() { return sprinting; }
    }
}
