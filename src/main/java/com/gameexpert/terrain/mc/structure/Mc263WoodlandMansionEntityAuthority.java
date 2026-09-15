package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionGrammar.Data;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionGrammar.Marker;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionGrammar.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionGrammar.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionGrammar.StructureMarker;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.CanonicalNbt;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.EntityPayload;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.Position;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.ServerLevelRandom;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.ServerRandomState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Pinned 26.3-snapshot-7 Woodland Mansion marker/entity authority leaf. */
public final class Mc263WoodlandMansionEntityAuthority {
    public static final String PROCEDURAL_AUTHORITY_SHA256 =
            "cd9757f9c593a31f558ed57f855ac638fb9f00652273083837e9e1c2ee3abd93";
    public static final String GRAMMAR_RESOURCE_SHA256 =
            "cdc45615029aad2bf39674e4761a44fbf2a1bc25f19eb0abd1e47a073439382e";
    public static final String SETTLEMENT_EVIDENCE_SHA256 =
            "7b8568894e1293efac8fd492da54c507de431ef2c3dad2a378aa1c657158600d";
    public static final String CANONICAL_PROJECTION_SHA256 =
            "b71a616a3be4cc168a05b2d5a393d31053aebe805444eb1d599cc5b56e541612";
    public static final String MANSION_PIECE_CLASS_SHA256 =
            "baa3eb44c97870e6e456c0c19dc63f67b5ed7ed26d9aed590d92c268bd507007";

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private static final String MAGE = "Mage";
    private static final String WARRIOR = "Warrior";
    private static final String ALLAY_GROUP = "Group of Allays";
    private static final String EVOKER = "minecraft:evoker";
    private static final String VINDICATOR = "minecraft:vindicator";
    private static final String ALLAY = "minecraft:allay";
    private static final String SPAWN_REASON = "minecraft:structure";
    private static final String RUNTIME_SPAWN_REASON = "STRUCTURE";
    private static final double FOLLOW_RANGE_DEVIATION =
            Double.longBitsToDouble(0x3fbd66cf41f212d8L);
    private static final int EVOKER_NBT_BYTES = 652;
    private static final int VINDICATOR_NBT_BYTES = 701;
    private static final int ALLAY_NBT_BYTES = 682;

    public static Receipt generate(MarkerRequest request, ServerLevelRandom callerRandom) {
        Objects.requireNonNull(callerRandom, "Mansion marker caller random");
        ResolvedMarker marker = preflight(request);

        synchronized (callerRandom) {
            ServerRandomState predecessor = callerRandom.state();
            List<Long> predecessorContinuation =
                    callerRandom.continuation().continuationNextLongI64();
            ServerLevelRandom candidate = callerRandom.copy();
            ArrayList<GeneratedEntity> entities = new ArrayList<>();

            if (MAGE.equals(marker.metadata)) {
                entities.add(generateEntity(marker, EVOKER, candidate, 0));
            } else if (WARRIOR.equals(marker.metadata)) {
                entities.add(generateEntity(marker, VINDICATOR, candidate, 0));
            } else {
                int count = candidate.nextInt(3) + 1;
                for (int index = 0; index < count; index++) {
                    entities.add(generateEntity(marker, ALLAY, candidate, index));
                }
            }

            validateEntities(marker, entities);
            if (!callerRandom.state().equals(predecessor)
                    || !callerRandom.continuation().continuationNextLongI64()
                            .equals(predecessorContinuation)) {
                throw new IllegalStateException("stale Mansion marker RNG predecessor");
            }
            ServerRandomState successor = candidate.state();
            List<Long> successorContinuation =
                    candidate.continuation().continuationNextLongI64();
            callerRandom.replaceWith(candidate);
            return new Receipt(request, entities, predecessor, predecessorContinuation,
                    successor, successorContinuation);
        }
    }

    /**
     * Re-authenticates one persisted Mansion ENTS row against the exact ordered canonical NBT
     * projection. The only variable NBT facts not carried as sidecar columns are the authenticated
     * follow-range draw and handedness draw; recover those two typed tags, rebuild the complete
     * payload, and require byte identity.
     */
    public static boolean matchesCanonicalPayload(String entityKey, double[] position,
            float[] rotation, double[] motion, byte[] canonicalPayload) {
        Objects.requireNonNull(entityKey, "Mansion persisted entity key");
        Objects.requireNonNull(position, "Mansion persisted position");
        Objects.requireNonNull(rotation, "Mansion persisted rotation");
        Objects.requireNonNull(motion, "Mansion persisted motion");
        Objects.requireNonNull(canonicalPayload, "Mansion persisted canonical NBT");
        int expectedLength = switch (entityKey) {
            case EVOKER -> EVOKER_NBT_BYTES;
            case VINDICATOR -> VINDICATOR_NBT_BYTES;
            case ALLAY -> ALLAY_NBT_BYTES;
            default -> -1;
        };
        if (canonicalPayload.length != expectedLength
                || position.length != 3 || rotation.length != 2 || motion.length != 3) {
            return false;
        }
        int amountOffset = uniqueNamedTagValueOffset(canonicalPayload, TAG_DOUBLE, "amount");
        int handedOffset = uniqueNamedTagValueOffset(canonicalPayload, TAG_BYTE, "LeftHanded");
        if (amountOffset < 0 || handedOffset < 0) return false;
        double followRangeAmount = ByteBuffer.wrap(canonicalPayload, amountOffset, Double.BYTES)
                .getDouble();
        int leftHanded = canonicalPayload[handedOffset] & 0xff;
        if (!Double.isFinite(followRangeAmount) || leftHanded > 1) return false;
        byte[] rebuilt = encodeCanonicalNbt(entityKey, followRangeAmount, leftHanded == 1,
                position, rotation, motion);
        return MessageDigest.isEqual(rebuilt, canonicalPayload);
    }

    private static int uniqueNamedTagValueOffset(byte[] payload, int tagType, String name) {
        byte[] encodedName = name.getBytes(StandardCharsets.UTF_8);
        int found = -1;
        for (int index = 0; index + 3 + encodedName.length <= payload.length; index++) {
            if ((payload[index] & 0xff) != tagType
                    || (payload[index + 1] & 0xff) != (encodedName.length >>> 8)
                    || (payload[index + 2] & 0xff) != (encodedName.length & 0xff)) continue;
            boolean same = true;
            for (int offset = 0; offset < encodedName.length; offset++) {
                if (payload[index + 3 + offset] != encodedName[offset]) {
                    same = false;
                    break;
                }
            }
            if (!same) continue;
            int valueOffset = index + 3 + encodedName.length;
            if (found >= 0) return -1;
            found = valueOffset;
        }
        return found;
    }

    static GeneratedEntity buildCanonicalEntity(String marker, Position markerPosition,
            int encounterOrdinal, double followRangeAmount, Float equipmentDraw,
            float handednessDraw) {
        Objects.requireNonNull(marker, "Mansion entity marker");
        Objects.requireNonNull(markerPosition, "Mansion entity marker position");
        if (encounterOrdinal < 0) {
            throw new IllegalArgumentException("negative Mansion entity encounter ordinal");
        }
        if (!Double.isFinite(followRangeAmount)
                || !Float.isFinite(handednessDraw)
                || handednessDraw < 0.0F || handednessDraw >= 1.0F) {
            throw new IllegalArgumentException("invalid Mansion entity finalize draw");
        }
        String entityKey;
        int expectedLength;
        if (MAGE.equals(marker)) {
            require(equipmentDraw == null, "Evoker unexpectedly consumed equipment draw");
            entityKey = EVOKER;
            expectedLength = EVOKER_NBT_BYTES;
        } else if (WARRIOR.equals(marker)) {
            require(equipmentDraw != null && Float.isFinite(equipmentDraw)
                            && equipmentDraw >= 0.0F && equipmentDraw < 1.0F,
                    "Vindicator equipment draw is unavailable");
            entityKey = VINDICATOR;
            expectedLength = VINDICATOR_NBT_BYTES;
        } else if (ALLAY_GROUP.equals(marker)) {
            require(equipmentDraw == null, "Allay unexpectedly consumed equipment draw");
            entityKey = ALLAY;
            expectedLength = ALLAY_NBT_BYTES;
        } else {
            throw new IllegalArgumentException("unsupported Mansion entity marker: " + marker);
        }

        double[] position = new double[] {
                markerPosition.x() + 0.5D, markerPosition.y(), markerPosition.z() + 0.5D
        };
        float[] rotation = new float[] {0.0F, 0.0F};
        double[] motion = new double[] {0.0D, 0.0D, 0.0D};
        byte[] canonical = encodeCanonicalNbt(entityKey, followRangeAmount,
                handednessDraw < 0.05F, position, rotation, motion);
        require(canonical.length == expectedLength,
                "Mansion canonical entity NBT length drift: " + canonical.length);
        EntityPayload payload = new EntityPayload(marker, markerPosition, entityKey,
                SPAWN_REASON, RUNTIME_SPAWN_REASON, new CanonicalNbt(canonical), true);
        return new GeneratedEntity(encounterOrdinal, position, rotation, motion, payload,
                followRangeAmount, equipmentDraw, handednessDraw);
    }

    private static GeneratedEntity generateEntity(ResolvedMarker marker, String entityKey,
            ServerLevelRandom random, int encounterOrdinal) {
        double followRangeAmount = random.triangle(0.0D, FOLLOW_RANGE_DEVIATION);
        Float equipmentDraw = VINDICATOR.equals(entityKey) ? random.nextFloat() : null;
        float handednessDraw = random.nextFloat();
        return buildCanonicalEntity(marker.metadata, marker.position, encounterOrdinal,
                followRangeAmount, equipmentDraw, handednessDraw);
    }

    private static ResolvedMarker preflight(MarkerRequest request) {
        Objects.requireNonNull(request, "Mansion marker request");
        require(PROCEDURAL_AUTHORITY_SHA256.equals(
                        Mc263WoodlandMansionSettlement.P2R_P2T_AUTHORITY_SHA256),
                "Mansion entity procedural authority drift");
        require(Mc263WoodlandMansionSettlement.SERVER_LEVEL_RANDOM_AUTHORITY
                        .implementationSha256().equals(
                                Mc263WoodlandMansionSettlement
                                        .SERVER_LEVEL_RANDOM_IMPLEMENTATION_SHA256)
                        && Mc263WoodlandMansionSettlement.SERVER_LEVEL_RANDOM_AUTHORITY
                                .sourceSha256().equals(
                                        Mc263WoodlandMansionSettlement
                                                .SERVER_LEVEL_RANDOM_SOURCE_SHA256),
                "Mansion entity ServerLevel RNG authority drift");
        require(EVOKER_NBT_BYTES > 0 && VINDICATOR_NBT_BYTES > 0 && ALLAY_NBT_BYTES > 0
                        && FOLLOW_RANGE_DEVIATION ==
                                Double.longBitsToDouble(0x3fbd66cf41f212d8L),
                "Mansion entity canonical capability closure unavailable");

        Mc263WoodlandMansionGrammar grammar = Mc263WoodlandMansionGrammar.loadAccepted();
        require(GRAMMAR_RESOURCE_SHA256.equals(Mc263WoodlandMansionGrammar.FILE_SHA256),
                "Mansion entity grammar resource identity drift");
        require(grammar.templates().size()
                        == Mc263WoodlandMansionSettlement.AUTHORITY_TEMPLATE_COUNT,
                "Mansion entity grammar authority cardinality drift");
        Mc263WoodlandMansionGrammar.SettlementEvidence evidence =
                grammar.requireSettlementEvidence();
        require(SETTLEMENT_EVIDENCE_SHA256.equals(evidence.evidenceSha256())
                        && CANONICAL_PROJECTION_SHA256.equals(evidence.entityCanonicalization()
                                .canonicalProjectionSha256())
                        && evidence.entityCanonicalization().repeatedOfficialRuns() == 2
                        && evidence.entityCanonicalization().entityCountPerRun() == 14
                        && evidence.entityCanonicalization().allRuntimeUuidsDiffer(),
                "Mansion canonical ENTS evidence authority drift");
        LinkedHashSet<String> evidenceBranches = new LinkedHashSet<>();
        LinkedHashSet<String> evidenceEntities = new LinkedHashSet<>();
        for (Mc263WoodlandMansionGrammar.SettlementProbe probe : evidence.probes()) {
            for (Mc263WoodlandMansionGrammar.SettlementClip clip : probe.clips()) {
                for (Mc263WoodlandMansionGrammar.EntityEvidence entity
                        : clip.sidecars().entities()) {
                    evidenceBranches.add(entity.marker());
                    evidenceEntities.add(entity.entityKey());
                }
            }
        }
        require(evidenceBranches.equals(new LinkedHashSet<>(
                            List.of(MAGE, WARRIOR, ALLAY_GROUP)))
                        && evidenceEntities.equals(new LinkedHashSet<>(
                                List.of(EVOKER, VINDICATOR, ALLAY))),
                "Mansion entity branch capability closure unavailable");
        Mc263WoodlandMansionGrammar.Template template =
                grammar.templates().get(request.templateId);
        require(template != null, "unknown Mansion entity template: " + request.templateId);
        ArrayList<Marker> markers = new ArrayList<>();
        for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
            if (command instanceof Data data
                    && data.semantic() instanceof StructureMarker structureMarker) {
                markers.add(new Marker(markers.size(), structureMarker.metadata(), data.position()));
            }
        }
        require(request.markerOrdinal >= 0 && request.markerOrdinal < markers.size(),
                "Mansion marker ordinal outside authenticated template");
        Marker authenticated = markers.get(request.markerOrdinal);
        require(authenticated.metadata().equals(request.metadata),
                "Mansion marker metadata does not match authenticated grammar");
        require(MAGE.equals(request.metadata) || WARRIOR.equals(request.metadata)
                        || ALLAY_GROUP.equals(request.metadata),
                "Mansion marker is not an entity marker: " + request.metadata);

        Position local = transform(authenticated.position(), request.mirror, request.rotation);
        Position transformed = new Position(
                Math.addExact(request.templateOrigin.x(), local.x()),
                Math.addExact(request.templateOrigin.y(), local.y()),
                Math.addExact(request.templateOrigin.z(), local.z()));
        require(transformed.equals(request.transformedPosition),
                "Mansion marker transformed position drift");
        return new ResolvedMarker(request.metadata, transformed);
    }

    private static Position transform(Mc263WoodlandMansionGrammar.Pos position,
            Mirror mirror, Rotation rotation) {
        int x = position.x();
        int z = position.z();
        if (mirror == Mirror.LEFT_RIGHT) z = Math.negateExact(z);
        else if (mirror == Mirror.FRONT_BACK) x = Math.negateExact(x);
        return switch (rotation) {
            case NONE -> new Position(x, position.y(), z);
            case CLOCKWISE_90 -> new Position(Math.negateExact(z), position.y(), x);
            case CLOCKWISE_180 -> new Position(
                    Math.negateExact(x), position.y(), Math.negateExact(z));
            case COUNTERCLOCKWISE_90 -> new Position(z, position.y(), Math.negateExact(x));
        };
    }

    private static void validateEntities(ResolvedMarker marker,
            List<GeneratedEntity> entities) {
        int minimum = 1;
        int maximum = ALLAY_GROUP.equals(marker.metadata) ? 3 : 1;
        require(entities.size() >= minimum && entities.size() <= maximum,
                "Mansion marker entity cardinality drift");
        String entityKey = MAGE.equals(marker.metadata) ? EVOKER
                : WARRIOR.equals(marker.metadata) ? VINDICATOR : ALLAY;
        for (int index = 0; index < entities.size(); index++) {
            GeneratedEntity entity = entities.get(index);
            require(entity.encounterOrdinal == index,
                    "Mansion entity encounter order drift");
            EntityPayload payload = entity.payload;
            require(payload.marker().equals(marker.metadata)
                            && payload.markerPosition().equals(marker.position)
                            && payload.entityKey().equals(entityKey)
                            && SPAWN_REASON.equals(payload.spawnReason())
                            && RUNTIME_SPAWN_REASON.equals(payload.runtimeSpawnReason())
                            && payload.runtimeUuidExcluded(),
                    "Mansion ENTS semantic payload drift");
            byte[] rebuilt = encodeCanonicalNbt(entityKey, entity.followRangeAmount,
                    entity.handednessDraw < 0.05F, entity.position,
                    entity.rotation, entity.motion);
            require(MessageDigest.isEqual(rebuilt, payload.canonicalNbt().binary())
                            && sha256(rebuilt).equals(payload.canonicalNbt().sha256()),
                    "Mansion canonical ENTS NBT drift");
        }
    }

    private static byte[] encodeCanonicalNbt(String entityKey, double followRangeAmount,
            boolean leftHanded, double[] position, float[] rotation, double[] motion) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("");
            writeBrain(out);
            if (ALLAY.equals(entityKey)) writeListener(out);
            else writeRaiderPrefix(out);
            tagByte(out, "Invulnerable", 0);
            tagByte(out, "FallFlying", 0);
            tagInt(out, "PortalCooldown", 0);
            tagFloat(out, "AbsorptionAmount", 0.0F);
            if (ALLAY.equals(entityKey)) tagLong(out, "DuplicationCooldown", 0L);
            tagShort(out, "DeathTime", 0);
            tagByte(out, "PersistenceRequired", 1);
            if (EVOKER.equals(entityKey)) tagInt(out, "SpellTicks", 0);
            tagString(out, "id", entityKey);
            if (!ALLAY.equals(entityKey)) tagByte(out, "Patrolling", 0);
            tagDoubleList(out, "Motion", motion);
            tagFloat(out, "Health", ALLAY.equals(entityKey) ? 20.0F : 24.0F);
            if (VINDICATOR.equals(entityKey)) writeVindicatorEquipment(out);
            tagByte(out, "LeftHanded", leftHanded ? 1 : 0);
            tagDouble(out, "fall_distance", 0.0D);
            tagShort(out, "Air", 300);
            tagByte(out, "OnGround", 0);
            tagFloatList(out, "Rotation", rotation);
            tagInt(out, "current_impulse_context_reset_grace_time", 0);
            if (!ALLAY.equals(entityKey)) tagInt(out, "Wave", 0);
            tagDoubleList(out, "Pos", position);
            tagShort(out, "Fire", 0);
            tagByte(out, "CanPickUpLoot", 0);
            writeFollowRangeAttribute(out, followRangeAmount,
                    ALLAY.equals(entityKey) ? 16.0D : 12.0D);
            tagShort(out, "HurtTime", 0);
            if (ALLAY.equals(entityKey)) tagEmptyList(out, "Inventory");
            out.writeByte(TAG_END);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Mansion canonical entity NBT encoding failed",
                    exception);
        }
    }

    private static void writeBrain(DataOutputStream out) throws IOException {
        tagHeader(out, TAG_COMPOUND, "Brain");
        tagHeader(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
    }

    private static void writeListener(DataOutputStream out) throws IOException {
        tagHeader(out, TAG_COMPOUND, "listener");
        tagHeader(out, TAG_COMPOUND, "selector");
        tagLong(out, "tick", -1L);
        out.writeByte(TAG_END);
        tagInt(out, "event_delay", 0);
        out.writeByte(TAG_END);
    }

    private static void writeRaiderPrefix(DataOutputStream out) throws IOException {
        tagByte(out, "CanJoinRaid", 1);
        tagByte(out, "PatrolLeader", 0);
    }

    private static void writeVindicatorEquipment(DataOutputStream out) throws IOException {
        tagHeader(out, TAG_COMPOUND, "equipment");
        tagHeader(out, TAG_COMPOUND, "mainhand");
        tagInt(out, "count", 1);
        tagString(out, "id", "minecraft:iron_axe");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
    }

    private static void writeFollowRangeAttribute(DataOutputStream out, double amount,
            double base) throws IOException {
        tagHeader(out, TAG_LIST, "attributes");
        out.writeByte(TAG_COMPOUND);
        out.writeInt(1);
        tagString(out, "id", "minecraft:follow_range");
        tagHeader(out, TAG_LIST, "modifiers");
        out.writeByte(TAG_COMPOUND);
        out.writeInt(1);
        tagDouble(out, "amount", amount);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDouble(out, "base", base);
        out.writeByte(TAG_END);
    }

    private static void tagHeader(DataOutputStream out, int type, String name)
            throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static void tagByte(DataOutputStream out, String name, int value)
            throws IOException {
        tagHeader(out, TAG_BYTE, name);
        out.writeByte(value);
    }

    private static void tagShort(DataOutputStream out, String name, int value)
            throws IOException {
        tagHeader(out, TAG_SHORT, name);
        out.writeShort(value);
    }

    private static void tagInt(DataOutputStream out, String name, int value)
            throws IOException {
        tagHeader(out, TAG_INT, name);
        out.writeInt(value);
    }

    private static void tagLong(DataOutputStream out, String name, long value)
            throws IOException {
        tagHeader(out, TAG_LONG, name);
        out.writeLong(value);
    }

    private static void tagFloat(DataOutputStream out, String name, float value)
            throws IOException {
        tagHeader(out, TAG_FLOAT, name);
        out.writeFloat(value);
    }

    private static void tagDouble(DataOutputStream out, String name, double value)
            throws IOException {
        tagHeader(out, TAG_DOUBLE, name);
        out.writeDouble(value);
    }

    private static void tagString(DataOutputStream out, String name, String value)
            throws IOException {
        tagHeader(out, TAG_STRING, name);
        out.writeUTF(value);
    }

    private static void tagDoubleList(DataOutputStream out, String name, double[] values)
            throws IOException {
        tagHeader(out, TAG_LIST, name);
        out.writeByte(TAG_DOUBLE);
        out.writeInt(values.length);
        for (double value : values) out.writeDouble(value);
    }

    private static void tagFloatList(DataOutputStream out, String name, float[] values)
            throws IOException {
        tagHeader(out, TAG_LIST, name);
        out.writeByte(TAG_FLOAT);
        out.writeInt(values.length);
        for (float value : values) out.writeFloat(value);
    }

    private static void tagEmptyList(DataOutputStream out, String name) throws IOException {
        tagHeader(out, TAG_LIST, name);
        out.writeByte(TAG_END);
        out.writeInt(0);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static final class MarkerRequest {
        private final String templateId;
        private final int markerOrdinal;
        private final String metadata;
        private final Position templateOrigin;
        private final Mirror mirror;
        private final Rotation rotation;
        private final Position transformedPosition;

        public MarkerRequest(String templateId, int markerOrdinal, String metadata,
                Position templateOrigin, Mirror mirror, Rotation rotation,
                Position transformedPosition) {
            this.templateId = Objects.requireNonNull(templateId, "Mansion marker template");
            this.markerOrdinal = markerOrdinal;
            this.metadata = Objects.requireNonNull(metadata, "Mansion marker metadata");
            this.templateOrigin = Objects.requireNonNull(
                    templateOrigin, "Mansion marker template origin");
            this.mirror = Objects.requireNonNull(mirror, "Mansion marker mirror");
            this.rotation = Objects.requireNonNull(rotation, "Mansion marker rotation");
            this.transformedPosition = Objects.requireNonNull(
                    transformedPosition, "Mansion marker transformed position");
        }

        public String templateId() { return templateId; }
        public int markerOrdinal() { return markerOrdinal; }
        public String metadata() { return metadata; }
        public Position templateOrigin() { return templateOrigin; }
        public Mirror mirror() { return mirror; }
        public Rotation rotation() { return rotation; }
        public Position transformedPosition() { return transformedPosition; }
    }

    public static final class GeneratedEntity {
        private final int encounterOrdinal;
        private final double[] position;
        private final float[] rotation;
        private final double[] motion;
        private final EntityPayload payload;
        private final double followRangeAmount;
        private final Float equipmentDraw;
        private final float handednessDraw;

        private GeneratedEntity(int encounterOrdinal, double[] position, float[] rotation,
                double[] motion, EntityPayload payload, double followRangeAmount,
                Float equipmentDraw, float handednessDraw) {
            this.encounterOrdinal = encounterOrdinal;
            this.position = position.clone();
            this.rotation = rotation.clone();
            this.motion = motion.clone();
            this.payload = Objects.requireNonNull(payload);
            this.followRangeAmount = followRangeAmount;
            this.equipmentDraw = equipmentDraw;
            this.handednessDraw = handednessDraw;
        }

        public int encounterOrdinal() { return encounterOrdinal; }
        public double[] position() { return position.clone(); }
        public float[] rotation() { return rotation.clone(); }
        public double[] motion() { return motion.clone(); }
        public EntityPayload payload() { return payload; }
        public double followRangeAmount() { return followRangeAmount; }
        public Float equipmentDraw() { return equipmentDraw; }
        public float handednessDraw() { return handednessDraw; }
    }

    public static final class Receipt {
        private final MarkerRequest request;
        private final List<GeneratedEntity> entities;
        private final ServerRandomState predecessor;
        private final List<Long> predecessorContinuation;
        private final ServerRandomState successor;
        private final List<Long> successorContinuation;

        private Receipt(MarkerRequest request, List<GeneratedEntity> entities,
                ServerRandomState predecessor, List<Long> predecessorContinuation,
                ServerRandomState successor, List<Long> successorContinuation) {
            this.request = request;
            this.entities = List.copyOf(entities);
            this.predecessor = predecessor;
            this.predecessorContinuation = List.copyOf(predecessorContinuation);
            this.successor = successor;
            this.successorContinuation = List.copyOf(successorContinuation);
        }

        public MarkerRequest request() { return request; }
        public List<GeneratedEntity> entities() { return entities; }
        public ServerRandomState predecessor() { return predecessor; }
        public List<Long> predecessorContinuation() { return predecessorContinuation; }
        public ServerRandomState successor() { return successor; }
        public List<Long> successorContinuation() { return successorContinuation; }

        public RollbackReceipt rollback(ServerLevelRandom callerRandom) {
            Objects.requireNonNull(callerRandom, "Mansion rollback caller random");
            synchronized (callerRandom) {
                if (!callerRandom.state().equals(successor)
                        || !callerRandom.continuation().continuationNextLongI64()
                                .equals(successorContinuation)) {
                    throw new IllegalStateException("Mansion entity rollback successor conflict");
                }
                ServerLevelRandom restored = ServerLevelRandom.fromAuthenticatedState(
                        predecessor, predecessorContinuation);
                callerRandom.replaceWith(restored);
                return new RollbackReceipt(successor, predecessor);
            }
        }
    }

    public static final class RollbackReceipt {
        private final ServerRandomState predecessor;
        private final ServerRandomState successor;

        private RollbackReceipt(ServerRandomState predecessor, ServerRandomState successor) {
            this.predecessor = predecessor;
            this.successor = successor;
        }

        public ServerRandomState predecessor() { return predecessor; }
        public ServerRandomState successor() { return successor; }
    }

    private static final class ResolvedMarker {
        private final String metadata;
        private final Position position;

        private ResolvedMarker(String metadata, Position position) {
            this.metadata = metadata;
            this.position = position;
        }
    }

    private Mc263WoodlandMansionEntityAuthority() { }
}
