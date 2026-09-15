package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement.DoubleVec;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement.EntityPayload;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement.EntityRequest;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement.RawDoubles;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement.RawFloats;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement.Vec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Pinned 26.3-snapshot-7 Pillager Outpost structure-entity authority. */
public final class Mc263PillagerOutpostEntityAuthority {
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

    private static final String ALLAY = "minecraft:allay";
    private static final String IRON_GOLEM = "minecraft:iron_golem";
    private static final String SPAWN_REASON = "minecraft:structure";
    private static final String RUNTIME_SPAWN_REASON = "STRUCTURE";

    private static final int ALLAY_LENGTH = 729;
    private static final int IRON_GOLEM_LENGTH = 903;

    private static final double GOLEM_MOTION_Y =
            Double.longBitsToDouble(0xbfb41205c28f5c29L);
    private static final float GOLEM_PITCH = Float.intBitsToFloat(0x418f6edb);
    private static final double GOLEM_FOLLOW_RANGE_AMOUNT =
            Double.longBitsToDouble(0x3f65e3da94937b00L);

    private static final double ALLAY_LOW_MOTION_X =
            Double.longBitsToDouble(0xbf83c82db620abf7L);
    private static final double ALLAY_LOW_MOTION_Y =
            Double.longBitsToDouble(0xbfb43425e2a270d8L);
    private static final double ALLAY_HIGH_MOTION_X =
            Double.longBitsToDouble(0x3f960a4d7113d98eL);
    private static final double ALLAY_HIGH_MOTION_Y =
            Double.longBitsToDouble(0xbfa63417c53eb8beL);
    private static final double ALLAY_HIGH_MOTION_Z =
            Double.longBitsToDouble(0x3f7c9bf547f66b8dL);
    private static final float ALLAY_LOW_PITCH = Float.intBitsToFloat(0xc2200000);
    private static final float ALLAY_HIGH_PITCH = Float.intBitsToFloat(0x42200000);
    private static final double ALLAY_LOW_FOLLOW_RANGE_AMOUNT =
            Double.longBitsToDouble(0xbfa0db07a0e60ee2L);
    private static final double ALLAY_HIGH_FOLLOW_RANGE_AMOUNT =
            Double.longBitsToDouble(0x3f7e8499c4ceabbaL);
    private static final double ALLAY_MOVEMENT_SPEED =
            Double.longBitsToDouble(0x3fd3333340000000L);

    private static final List<String> GOLEM_ATTRIBUTES = List.of(
            "minecraft:armor", "minecraft:armor_toughness",
            "minecraft:attack_knockback", "minecraft:follow_range",
            "minecraft:knockback_resistance", "minecraft:max_health",
            "minecraft:movement_speed");
    private static final List<String> ALLAY_ATTRIBUTES = List.of(
            "minecraft:follow_range", "minecraft:movement_speed");

    public static EntityPayload spawnStructureEntity(EntityRequest request,
            Mc263WorldGenRegionRandom callerRandom) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callerRandom, "callerRandom");
        validateRequest(request);

        Mc263WorldGenRegionRandom.State predecessor = callerRandom.snapshot();
        Mc263WorldGenRegionRandom candidate = callerRandom.forkForTransaction();
        if (!candidate.snapshot().equals(predecessor)) {
            throw new IllegalStateException("forked WGR does not match caller predecessor");
        }

        float finalizeDraw = candidate.nextFloat();
        EntityPayload payload = buildCanonicalPayload(request, finalizeDraw);
        return commitVerified(
                request, callerRandom, predecessor, candidate, finalizeDraw, payload);
    }

    public static EntityPayload spawnStructureEntity(EntityRequest request,
            Mc263PillagerOutpostSettlement.PlacementRandom callerRandom) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callerRandom, "callerRandom");
        validateRequest(request);

        long predecessorLo = callerRandom.lo();
        long predecessorHi = callerRandom.hi();
        int predecessorCount = callerRandom.count();
        Mc263PillagerOutpostSettlement.PlacementRandom candidate = callerRandom.copy();
        float finalizeDraw = candidate.nextFloat();
        EntityPayload payload = buildCanonicalPayload(request, finalizeDraw);

        if (callerRandom.lo() != predecessorLo || callerRandom.hi() != predecessorHi
                || callerRandom.count() != predecessorCount) {
            throw new IllegalStateException("stale caller placement RNG predecessor");
        }
        Mc263PillagerOutpostSettlement.PlacementRandom expected =
                Mc263PillagerOutpostSettlement.PlacementRandom.fromState(
                        predecessorLo, predecessorHi, predecessorCount);
        float expectedDraw = expected.nextFloat();
        if (Float.floatToRawIntBits(expectedDraw) != Float.floatToRawIntBits(finalizeDraw)
                || candidate.lo() != expected.lo() || candidate.hi() != expected.hi()
                || candidate.count() != expected.count()) {
            throw new IllegalStateException(
                    "candidate placement RNG did not consume exactly one nextFloat");
        }
        validateCanonicalPayload(request, payload, finalizeDraw);
        if (callerRandom.lo() != predecessorLo || callerRandom.hi() != predecessorHi
                || callerRandom.count() != predecessorCount) {
            throw new IllegalStateException(
                    "caller placement RNG changed during payload validation");
        }
        callerRandom.replaceWith(candidate);
        return payload;
    }

    static EntityPayload buildCanonicalPayload(EntityRequest request, float finalizeDraw) {
        Objects.requireNonNull(request, "request");
        validateRequest(request);
        validateFinalizeDraw(finalizeDraw);
        EntityProgram program = programFor(request);
        EntityPayload payload = createCanonicalPayload(request, program, finalizeDraw);
        validateCanonicalPayload(request, payload, finalizeDraw);
        return payload;
    }

    static EntityPayload commitVerified(EntityRequest request,
            Mc263WorldGenRegionRandom callerRandom,
            Mc263WorldGenRegionRandom.State predecessor,
            Mc263WorldGenRegionRandom candidate, float finalizeDraw, EntityPayload payload) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callerRandom, "callerRandom");
        Objects.requireNonNull(predecessor, "predecessor");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(payload, "payload");
        validateRequest(request);
        validateFinalizeDraw(finalizeDraw);

        if (!callerRandom.snapshot().equals(predecessor)) {
            throw new IllegalStateException("stale caller WGR predecessor");
        }

        Mc263WorldGenRegionRandom expected = Mc263WorldGenRegionRandom.fromState(
                predecessor.lo(), predecessor.hi(), predecessor.drawCount(),
                predecessor.gaussianPresent(), predecessor.gaussianBits());
        float expectedDraw = expected.nextFloat();
        if (Float.floatToRawIntBits(expectedDraw) != Float.floatToRawIntBits(finalizeDraw)) {
            throw new IllegalStateException("finalize-spawn WGR draw drift");
        }
        if (!candidate.snapshot().equals(expected.snapshot())) {
            throw new IllegalStateException("candidate WGR did not consume exactly one nextFloat");
        }

        validateCanonicalPayload(request, payload, finalizeDraw);
        if (!callerRandom.snapshot().equals(predecessor)) {
            throw new IllegalStateException("caller WGR changed during payload validation");
        }
        if (!callerRandom.commitIfExactPredecessor(predecessor, candidate)) {
            throw new IllegalStateException("caller WGR predecessor changed before commit");
        }
        return payload;
    }

    static void validateCanonicalPayload(EntityRequest request, EntityPayload payload,
            float finalizeDraw) {
        Objects.requireNonNull(payload, "payload");
        validateRequest(request);
        validateFinalizeDraw(finalizeDraw);
        EntityProgram program = programFor(request);
        EntityPayload expected = createCanonicalPayload(request, program, finalizeDraw);

        if (payload.encounterOrdinal() != -1) {
            throw new IllegalStateException("entity authority payload must use encounter -1");
        }
        if (!request.entityKey().equals(payload.entityKey())) {
            throw new IllegalStateException("entity key drift");
        }
        if (!SPAWN_REASON.equals(payload.spawnReason())
                || !RUNTIME_SPAWN_REASON.equals(payload.runtimeSpawnReason())) {
            throw new IllegalStateException("structure spawn reason drift");
        }
        if (!payload.runtimeUuidExcluded()) {
            throw new IllegalStateException("runtime UUID must be excluded from canonical payload");
        }
        if (!program.attributeIds.equals(payload.canonicalAttributeIds())) {
            throw new IllegalStateException("canonical attribute order drift");
        }

        payload.position().validate();
        payload.rotation().validate();
        payload.motion().validate();
        if (!sameRawDoubles(payload.position(), expected.position())) {
            throw new IllegalStateException("entity position vector drift");
        }
        if (!sameRawFloats(payload.rotation(), expected.rotation())) {
            throw new IllegalStateException("entity rotation vector drift");
        }
        if (!sameRawDoubles(payload.motion(), expected.motion())) {
            throw new IllegalStateException("entity motion vector drift");
        }

        byte[] actual = payload.canonicalPayload();
        byte[] expectedBytes = expected.canonicalPayload();
        if (actual.length != program.binaryLength) {
            throw new IllegalStateException(
                    "canonical entity payload length drift: " + actual.length);
        }
        if (!MessageDigest.isEqual(actual, expectedBytes)) {
            throw new IllegalStateException("canonical entity NBT tag/order/type/value drift");
        }
        String actualSha256 = sha256(actual);
        if (!actualSha256.equals(payload.canonicalPayloadSha256())) {
            throw new IllegalStateException("canonical entity payload SHA-256 drift");
        }
        if (!actualSha256.equals(expected.canonicalPayloadSha256())) {
            throw new IllegalStateException("canonical entity payload expected SHA-256 drift");
        }
    }

    private static void validateRequest(EntityRequest request) {
        Objects.requireNonNull(request, "request");
        String entityKey = Objects.requireNonNull(request.entityKey(), "entityKey");
        if (!ALLAY.equals(entityKey) && !IRON_GOLEM.equals(entityKey)) {
            throw new IllegalArgumentException("unsupported Outpost entity: " + entityKey);
        }
        DoubleVec position = Objects.requireNonNull(
                request.templatePosition(), "templatePosition");
        if (!Double.isFinite(position.x()) || !Double.isFinite(position.y())
                || !Double.isFinite(position.z())) {
            throw new IllegalArgumentException("non-finite Outpost entity position");
        }
        Objects.requireNonNull(request.templateBlockPosition(), "templateBlockPosition");
        Objects.requireNonNull(request.rotation(), "rotation");
    }

    private static void validateFinalizeDraw(float finalizeDraw) {
        if (!Float.isFinite(finalizeDraw) || finalizeDraw < 0.0F || finalizeDraw >= 1.0F) {
            throw new IllegalArgumentException("invalid finalize-spawn nextFloat");
        }
    }

    private static EntityProgram programFor(EntityRequest request) {
        if (IRON_GOLEM.equals(request.entityKey())) {
            return EntityProgram.IRON_GOLEM;
        }
        DoubleVec position = request.templatePosition();
        Vec block = request.templateBlockPosition();
        double relativeY = position.y() - block.y();
        if (!Double.isFinite(relativeY) || relativeY < 0.0D || relativeY >= 1.0D) {
            throw new IllegalArgumentException("Allay position does not occupy its template block");
        }
        if (relativeY < 0.5D) {
            return EntityProgram.ALLAY_LOW;
        }
        return EntityProgram.ALLAY_HIGH;
    }

    private static EntityPayload createCanonicalPayload(EntityRequest request,
            EntityProgram program, float finalizeDraw) {
        DoubleVec position = request.templatePosition();
        double[] positionValues = new double[] {
                position.x(), position.y(), position.z()
        };
        double[] motion = new double[] {
                program.motionX, program.motionY, program.motionZ
        };
        float[] rotation = new float[] {
                yaw(program, request.rotation()), program.pitch
        };
        byte[] canonical = encodeCanonicalNbt(
                program, finalizeDraw, positionValues, rotation, motion);
        if (canonical.length != program.binaryLength) {
            throw new IllegalStateException(
                    "internal canonical entity payload length drift: " + canonical.length);
        }
        return new EntityPayload(-1, request.entityKey(), SPAWN_REASON, RUNTIME_SPAWN_REASON,
                rawDoubles(positionValues), rawFloats(rotation), rawDoubles(motion), canonical,
                program.attributeIds, true);
    }

    private static float yaw(EntityProgram program, Rotation rotation) {
        if (program == EntityProgram.IRON_GOLEM) {
            return switch (rotation) {
                case NONE -> 0.0F;
                case CLOCKWISE_90 -> 90.0F;
                case CLOCKWISE_180 -> 180.0F;
                case COUNTERCLOCKWISE_90 -> 270.0F;
            };
        }
        return switch (rotation) {
            case NONE -> -450.0F;
            case CLOCKWISE_90 -> -360.0F;
            case CLOCKWISE_180 -> -270.0F;
            case COUNTERCLOCKWISE_90 -> -180.0F;
        };
    }

    private static byte[] encodeCanonicalNbt(EntityProgram program, float finalizeDraw,
            double[] position, float[] rotation, double[] motion) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(program.binaryLength);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("");
            if (program == EntityProgram.IRON_GOLEM) {
                writeIronGolem(out, program, finalizeDraw, position, rotation, motion);
            } else {
                writeAllay(out, program, finalizeDraw, position, rotation, motion);
            }
            out.writeByte(TAG_END);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory canonical entity NBT encoding failed",
                    exception);
        }
    }

    private static void writeIronGolem(DataOutputStream out, EntityProgram program,
            float finalizeDraw, double[] position, float[] rotation, double[] motion)
            throws IOException {
        tagDoubleList(out, "Motion", motion);
        writeBrain(out);
        tagFloat(out, "Health", 100.0F);
        tagByte(out, "PlayerCreated", 0);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagByte(out, "LeftHanded", finalizeDraw < 0.05F ? 1 : 0);
        tagDouble(out, "fall_distance", 0.0D);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        tagInt(out, "PortalCooldown", 0);
        tagFloat(out, "AbsorptionAmount", 0.0F);
        tagFloatList(out, "Rotation", rotation);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        tagLong(out, "anger_end_time", -1L);
        tagDoubleList(out, "Pos", position);
        tagShort(out, "DeathTime", 0);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        writeGolemAttributes(out, program.followRangeAmount);
        tagByte(out, "PersistenceRequired", 0);
        tagString(out, "id", IRON_GOLEM);
        tagShort(out, "HurtTime", 0);
    }

    private static void writeAllay(DataOutputStream out, EntityProgram program,
            float finalizeDraw, double[] position, float[] rotation, double[] motion)
            throws IOException {
        writeBrain(out);
        tagHeader(out, TAG_COMPOUND, "listener");
        tagHeader(out, TAG_COMPOUND, "selector");
        tagLong(out, "tick", -1L);
        out.writeByte(TAG_END);
        tagInt(out, "event_delay", 0);
        out.writeByte(TAG_END);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "PortalCooldown", 0);
        tagFloat(out, "AbsorptionAmount", 0.0F);
        tagLong(out, "DuplicationCooldown", 0L);
        tagShort(out, "DeathTime", 0);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", ALLAY);
        tagDoubleList(out, "Motion", motion);
        tagFloat(out, "Health", 10.0F);
        tagByte(out, "LeftHanded", finalizeDraw < 0.05F ? 1 : 0);
        tagDouble(out, "fall_distance", 0.0D);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 0);
        tagFloatList(out, "Rotation", rotation);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        tagDoubleList(out, "Pos", position);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        writeAllayAttributes(out, program.followRangeAmount);
        tagShort(out, "HurtTime", 0);
        tagEmptyList(out, "Inventory");
    }

    private static void writeBrain(DataOutputStream out) throws IOException {
        tagHeader(out, TAG_COMPOUND, "Brain");
        tagHeader(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
    }

    private static void writeGolemAttributes(DataOutputStream out,
            double followRangeAmount) throws IOException {
        tagCompoundListHeader(out, "attributes", 7);
        writeSimpleAttribute(out, "minecraft:armor", 0.0D);
        writeSimpleAttribute(out, "minecraft:armor_toughness", 0.0D);
        writeSimpleAttribute(out, "minecraft:attack_knockback", 0.0D);
        writeFollowRangeAttribute(out, followRangeAmount);
        writeSimpleAttribute(out, "minecraft:knockback_resistance", 1.0D);
        writeSimpleAttribute(out, "minecraft:max_health", 100.0D);
        writeSimpleAttribute(out, "minecraft:movement_speed", 0.25D);
    }

    private static void writeAllayAttributes(DataOutputStream out,
            double followRangeAmount) throws IOException {
        tagCompoundListHeader(out, "attributes", 2);
        writeFollowRangeAttribute(out, followRangeAmount);
        writeSimpleAttribute(out, "minecraft:movement_speed", ALLAY_MOVEMENT_SPEED);
    }

    private static void writeSimpleAttribute(DataOutputStream out, String id, double base)
            throws IOException {
        tagString(out, "id", id);
        tagDouble(out, "base", base);
        out.writeByte(TAG_END);
    }

    private static void writeFollowRangeAttribute(DataOutputStream out,
            double amount) throws IOException {
        tagString(out, "id", "minecraft:follow_range");
        tagHeader(out, TAG_LIST, "modifiers");
        out.writeByte(TAG_COMPOUND);
        out.writeInt(1);
        tagDouble(out, "amount", amount);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDouble(out, "base", 16.0D);
        out.writeByte(TAG_END);
    }

    private static void tagCompoundListHeader(DataOutputStream out, String name, int size)
            throws IOException {
        tagHeader(out, TAG_LIST, name);
        out.writeByte(TAG_COMPOUND);
        out.writeInt(size);
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
        for (double value : values) {
            out.writeDouble(value);
        }
    }

    private static void tagFloatList(DataOutputStream out, String name, float[] values)
            throws IOException {
        tagHeader(out, TAG_LIST, name);
        out.writeByte(TAG_FLOAT);
        out.writeInt(values.length);
        for (float value : values) {
            out.writeFloat(value);
        }
    }

    private static void tagEmptyList(DataOutputStream out, String name) throws IOException {
        tagHeader(out, TAG_LIST, name);
        out.writeByte(TAG_END);
        out.writeInt(0);
    }

    private static RawDoubles rawDoubles(double[] values) {
        long[] rawBits = new long[values.length];
        List<String> rawBitsHex = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            rawBits[index] = Double.doubleToRawLongBits(values[index]);
            rawBitsHex.add(HexFormat.of().toHexDigits(rawBits[index]));
        }
        return new RawDoubles(values, rawBits, rawBitsHex);
    }

    private static RawFloats rawFloats(float[] values) {
        int[] rawBits = new int[values.length];
        List<String> rawBitsHex = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            rawBits[index] = Float.floatToRawIntBits(values[index]);
            rawBitsHex.add(HexFormat.of().toHexDigits(rawBits[index]));
        }
        return new RawFloats(values, rawBits, rawBitsHex);
    }

    private static boolean sameRawDoubles(RawDoubles left, RawDoubles right) {
        return Arrays.equals(left.values(), right.values())
                && Arrays.equals(left.rawBitsU64(), right.rawBitsU64())
                && left.rawBitsHex().equals(right.rawBitsHex());
    }

    private static boolean sameRawFloats(RawFloats left, RawFloats right) {
        return Arrays.equals(left.values(), right.values())
                && Arrays.equals(left.rawBitsU32(), right.rawBitsU32())
                && left.rawBitsHex().equals(right.rawBitsHex());
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private enum EntityProgram {
        IRON_GOLEM(
                IRON_GOLEM_LENGTH, GOLEM_PITCH, GOLEM_FOLLOW_RANGE_AMOUNT,
                GOLEM_ATTRIBUTES, 0.0D, GOLEM_MOTION_Y, 0.0D),
        ALLAY_LOW(
                ALLAY_LENGTH, ALLAY_LOW_PITCH, ALLAY_LOW_FOLLOW_RANGE_AMOUNT,
                ALLAY_ATTRIBUTES, ALLAY_LOW_MOTION_X, ALLAY_LOW_MOTION_Y, 0.0D),
        ALLAY_HIGH(
                ALLAY_LENGTH, ALLAY_HIGH_PITCH, ALLAY_HIGH_FOLLOW_RANGE_AMOUNT,
                ALLAY_ATTRIBUTES, ALLAY_HIGH_MOTION_X, ALLAY_HIGH_MOTION_Y,
                ALLAY_HIGH_MOTION_Z);

        final int binaryLength;
        final float pitch;
        final double followRangeAmount;
        final List<String> attributeIds;
        final double motionX;
        final double motionY;
        final double motionZ;

        EntityProgram(int binaryLength, float pitch, double followRangeAmount,
                List<String> attributeIds, double motionX, double motionY, double motionZ) {
            this.binaryLength = binaryLength;
            this.pitch = pitch;
            this.followRangeAmount = followRangeAmount;
            this.attributeIds = List.copyOf(attributeIds);
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
        }
    }

    private Mc263PillagerOutpostEntityAuthority() { }
}
