package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant, pure-Java carrier and evaluator for the pinned 26.3-snapshot-7 Beardifier.
 *
 * <p>The carrier retains only facts consumed by Beardifier: the observed structure-reference and
 * start order, terrain adjustment, piece bounding boxes, pool projection and ground delta, and
 * ordered junctions. It deliberately contains no template blocks or placement data.</p>
 */
public final class Mc263Beardifier {
    private static final int KERNEL_RADIUS = 12;
    private static final int KERNEL_SIZE = 24;
    private static final int KERNEL_AREA = KERNEL_SIZE * KERNEL_SIZE;
    private static final float[] BEARD_KERNEL = createKernel();
    private static final byte[] CARRIER_RECEIPT_MAGIC = "BDF263C1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EVALUATOR_RECEIPT_MAGIC =
            "BDF263E1".getBytes(StandardCharsets.US_ASCII);

    private final int chunkX;
    private final int chunkZ;
    private final List<Rigid> rigids;
    private final List<Junction> junctions;
    private final BoundingBox affectedBox;

    private Mc263Beardifier(
            int chunkX,
            int chunkZ,
            List<Rigid> rigids,
            List<Junction> junctions,
            BoundingBox affectedBox) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.rigids = List.copyOf(rigids);
        this.junctions = List.copyOf(junctions);
        this.affectedBox = affectedBox;
    }

    /** Resolves the carrier in its recorded reference/start traversal order. */
    public static Mc263Beardifier fromCarrier(ChunkCarrier carrier) {
        Objects.requireNonNull(carrier, "carrier");
        int chunkMinX = Math.multiplyExact(carrier.chunkX(), 16);
        int chunkMinZ = Math.multiplyExact(carrier.chunkZ(), 16);
        int closeMinX = Math.subtractExact(chunkMinX, KERNEL_RADIUS);
        int closeMinZ = Math.subtractExact(chunkMinZ, KERNEL_RADIUS);
        int closeMaxX = Math.addExact(chunkMinX, 15 + KERNEL_RADIUS);
        int closeMaxZ = Math.addExact(chunkMinZ, 15 + KERNEL_RADIUS);

        java.util.ArrayList<Rigid> rigids = new java.util.ArrayList<>();
        java.util.ArrayList<Junction> junctions = new java.util.ArrayList<>();
        BoundingBox affected = null;
        Set<String> observedGroups = new HashSet<>();
        Set<String> observedStarts = new HashSet<>();

        for (ReferenceGroup group : carrier.referenceGroups()) {
            if (!observedGroups.add(group.structureId())) {
                throw new IllegalArgumentException(
                        "duplicate structure reference group: " + group.structureId());
            }
            for (StructureStart start : group.orderedStarts()) {
                if (!observedStarts.add(start.startKey())) {
                    throw new IllegalArgumentException(
                            "duplicate structure start reference: " + start.startKey());
                }
                if (!start.resolvedValid() || start.terrainAdjustment() == TerrainAdjustment.NONE) {
                    continue;
                }
                for (Piece piece : start.pieces()) {
                    BoundingBox box = piece.boundingBox();
                    if (!box.intersectsXZ(closeMinX, closeMinZ, closeMaxX, closeMaxZ)) {
                        continue;
                    }
                    if (!piece.poolElement() || piece.projection() == Projection.RIGID) {
                        rigids.add(new Rigid(
                                box, start.terrainAdjustment(), piece.groundLevelDelta()));
                        affected = include(affected, box);
                    }
                    if (!piece.poolElement()) {
                        continue;
                    }
                    for (Junction junction : piece.junctions()) {
                        if (junction.sourceX() <= closeMinX
                                || junction.sourceZ() <= closeMinZ
                                || junction.sourceX() >= closeMaxX
                                || junction.sourceZ() >= closeMaxZ) {
                            continue;
                        }
                        junctions.add(junction);
                        affected = include(affected, BoundingBox.point(
                                junction.sourceX(), junction.sourceGroundY(), junction.sourceZ()));
                    }
                }
            }
        }
        BoundingBox inflated = affected == null ? null : affected.inflatedBy(24);
        return new Mc263Beardifier(carrier.chunkX(), carrier.chunkZ(), rigids, junctions, inflated);
    }

    /**
     * Computes the exact float contribution, accumulating every rigid before every junction.
     */
    public float compute(int blockX, int blockY, int blockZ) {
        if (affectedBox == null || !affectedBox.contains(blockX, blockY, blockZ)) {
            return 0.0f;
        }
        float noiseValue = 0.0f;
        for (Rigid rigid : rigids) {
            BoundingBox box = rigid.box();
            int dx = Math.max(0, Math.max(box.minX() - blockX, blockX - box.maxX()));
            int dz = Math.max(0, Math.max(box.minZ() - blockZ, blockZ - box.maxZ()));
            int groundY = Math.addExact(box.minY(), rigid.groundLevelDelta());
            int dyToGround = blockY - groundY;
            int dy = switch (rigid.terrainAdjustment()) {
                case NONE -> 0;
                case BURY, BEARD_THIN -> dyToGround;
                case BEARD_BOX -> Math.max(
                        0, Math.max(groundY - blockY, blockY - box.maxY()));
                case ENCAPSULATE -> Math.max(
                        0, Math.max(box.minY() - blockY, blockY - box.maxY()));
            };
            noiseValue += switch (rigid.terrainAdjustment()) {
                case NONE -> 0.0f;
                case BURY -> getBuryContribution(dx, (float) dy / 2.0f, dz);
                case BEARD_THIN, BEARD_BOX ->
                        getBeardContribution(dx, dy, dz, dyToGround) * 0.8f;
                case ENCAPSULATE -> getBuryContribution(
                        (float) dx / 2.0f, (float) dy / 2.0f, (float) dz / 2.0f) * 0.8f;
            };
        }
        for (Junction junction : junctions) {
            int dx = blockX - junction.sourceX();
            int dy = blockY - junction.sourceGroundY();
            int dz = blockZ - junction.sourceZ();
            noiseValue += getBeardContribution(dx, dy, dz, dy) * 0.4f;
        }
        return noiseValue;
    }

    public boolean isEmpty() {
        return affectedBox == null;
    }

    public int rigidCount() {
        return rigids.size();
    }

    public int junctionCount() {
        return junctions.size();
    }

    /** SHA-256 of the accepted, ordered evaluator input after official filtering. */
    public String receiptSha256() {
        return sha256(writeReceipt(output -> {
            output.write(EVALUATOR_RECEIPT_MAGIC);
            output.writeInt(chunkX);
            output.writeInt(chunkZ);
            output.writeBoolean(affectedBox != null);
            if (affectedBox != null) {
                affectedBox.write(output);
            }
            output.writeInt(rigids.size());
            for (Rigid rigid : rigids) {
                rigid.box().write(output);
                output.writeByte(rigid.terrainAdjustment().code());
                output.writeInt(rigid.groundLevelDelta());
            }
            output.writeInt(junctions.size());
            for (Junction junction : junctions) {
                junction.write(output);
            }
        }));
    }

    private static BoundingBox include(BoundingBox existing, BoundingBox addition) {
        return existing == null ? addition : existing.encapsulating(addition);
    }

    private static float getBuryContribution(float dx, float dy, float dz) {
        float distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq >= 36.0f) {
            return 0.0f;
        }
        return 1.0f - (float) Math.sqrt(distanceSq) / 6.0f;
    }

    private static float getBeardContribution(int dx, int dy, int dz, int yToGround) {
        int xi = dx + KERNEL_RADIUS;
        int yi = dy + KERNEL_RADIUS;
        int zi = dz + KERNEL_RADIUS;
        if (!inKernelRange(xi) || !inKernelRange(yi) || !inKernelRange(zi)) {
            return 0.0f;
        }
        float dyWithOffset = (float) yToGround + 0.5f;
        float distanceSqr = (float) dx * (float) dx
                + dyWithOffset * dyWithOffset
                + (float) dz * (float) dz;
        float value = -dyWithOffset * (float) fastInvSqrt((double) (distanceSqr / 2.0f))
                / 2.0f;
        return value * BEARD_KERNEL[zi * KERNEL_AREA + xi * KERNEL_SIZE + yi];
    }

    private static boolean inKernelRange(int index) {
        return index >= 0 && index < KERNEL_SIZE;
    }

    private static float[] createKernel() {
        float[] kernel = new float[KERNEL_SIZE * KERNEL_SIZE * KERNEL_SIZE];
        for (int zi = 0; zi < KERNEL_SIZE; zi++) {
            for (int xi = 0; xi < KERNEL_SIZE; xi++) {
                for (int yi = 0; yi < KERNEL_SIZE; yi++) {
                    int dx = xi - KERNEL_RADIUS;
                    double dy = (double) (yi - KERNEL_RADIUS) + 0.5;
                    int dz = zi - KERNEL_RADIUS;
                    double distanceSqr = (double) dx * (double) dx
                            + dy * dy
                            + (double) dz * (double) dz;
                    kernel[zi * KERNEL_AREA + xi * KERNEL_SIZE + yi] =
                            (float) Math.pow(Math.E, -distanceSqr / 16.0);
                }
            }
        }
        return kernel;
    }

    private static double fastInvSqrt(double value) {
        double half = 0.5 * value;
        long bits = Double.doubleToRawLongBits(value);
        bits = 6_910_469_410_427_058_090L - (bits >> 1);
        value = Double.longBitsToDouble(bits);
        value *= 1.5 - half * value * value;
        return value;
    }

    private static byte[] writeReceipt(ReceiptWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory Beardifier receipt failed", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @FunctionalInterface
    private interface ReceiptWriter {
        void write(DataOutputStream output) throws IOException;
    }

    public enum TerrainAdjustment {
        NONE(0),
        BURY(1),
        BEARD_THIN(2),
        BEARD_BOX(3),
        ENCAPSULATE(4);

        private final int code;

        TerrainAdjustment(int code) {
            this.code = code;
        }

        int code() {
            return code;
        }
    }

    public enum Projection {
        RIGID(0),
        TERRAIN_MATCHING(1),
        NOT_APPLICABLE(2);

        private final int code;

        Projection(int code) {
            this.code = code;
        }

        int code() {
            return code;
        }
    }

    public record BoundingBox(
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted Beardifier bounding box");
            }
        }

        static BoundingBox point(int x, int y, int z) {
            return new BoundingBox(x, y, z, x, y, z);
        }

        boolean intersectsXZ(int otherMinX, int otherMinZ, int otherMaxX, int otherMaxZ) {
            return maxX >= otherMinX
                    && minX <= otherMaxX
                    && maxZ >= otherMinZ
                    && minZ <= otherMaxZ;
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }

        BoundingBox encapsulating(BoundingBox other) {
            return new BoundingBox(
                    Math.min(minX, other.minX),
                    Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ));
        }

        BoundingBox inflatedBy(int amount) {
            return new BoundingBox(
                    Math.subtractExact(minX, amount),
                    Math.subtractExact(minY, amount),
                    Math.subtractExact(minZ, amount),
                    Math.addExact(maxX, amount),
                    Math.addExact(maxY, amount),
                    Math.addExact(maxZ, amount));
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(minX);
            output.writeInt(minY);
            output.writeInt(minZ);
            output.writeInt(maxX);
            output.writeInt(maxY);
            output.writeInt(maxZ);
        }
    }

    public record Junction(
            int sourceX,
            int sourceGroundY,
            int sourceZ,
            int deltaY,
            Projection destinationProjection) {
        public Junction {
            Objects.requireNonNull(destinationProjection, "destinationProjection");
            if (destinationProjection == Projection.NOT_APPLICABLE) {
                throw new IllegalArgumentException("junction destination projection is absent");
            }
        }

        void write(DataOutputStream output) throws IOException {
            output.writeInt(sourceX);
            output.writeInt(sourceGroundY);
            output.writeInt(sourceZ);
            output.writeInt(deltaY);
            output.writeByte(destinationProjection.code());
        }
    }

    public record Piece(
            String pieceType,
            BoundingBox boundingBox,
            boolean poolElement,
            Projection projection,
            int groundLevelDelta,
            List<Junction> junctions) {
        public Piece {
            requireText(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(projection, "projection");
            junctions = List.copyOf(junctions);
            if (poolElement && projection == Projection.NOT_APPLICABLE) {
                throw new IllegalArgumentException("pool piece projection is absent");
            }
            if (!poolElement && (projection != Projection.NOT_APPLICABLE
                    || groundLevelDelta != 0 || !junctions.isEmpty())) {
                throw new IllegalArgumentException("non-pool piece carries pool-only facts");
            }
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, pieceType);
            boundingBox.write(output);
            output.writeBoolean(poolElement);
            output.writeByte(projection.code());
            output.writeInt(groundLevelDelta);
            output.writeInt(junctions.size());
            for (Junction junction : junctions) {
                junction.write(output);
            }
        }
    }

    public record StructureStart(
            String startKey,
            String structureId,
            int originChunkX,
            int originChunkZ,
            boolean resolvedValid,
            TerrainAdjustment terrainAdjustment,
            BoundingBox adjustedBoundingBox,
            List<Piece> pieces) {
        public StructureStart {
            requireText(startKey, "startKey");
            requireText(structureId, "structureId");
            Objects.requireNonNull(terrainAdjustment, "terrainAdjustment");
            pieces = List.copyOf(pieces);
            String expectedKey = structureId + "@" + originChunkX + "," + originChunkZ;
            if (!startKey.equals(expectedKey)) {
                throw new IllegalArgumentException("structure start key/origin mismatch");
            }
            if (resolvedValid && (adjustedBoundingBox == null || pieces.isEmpty())) {
                throw new IllegalArgumentException("valid structure start lacks body");
            }
            if (!resolvedValid && (adjustedBoundingBox != null || !pieces.isEmpty())) {
                throw new IllegalArgumentException("unresolved structure start carries body");
            }
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, startKey);
            writeString(output, structureId);
            output.writeInt(originChunkX);
            output.writeInt(originChunkZ);
            output.writeBoolean(resolvedValid);
            output.writeByte(terrainAdjustment.code());
            output.writeBoolean(adjustedBoundingBox != null);
            if (adjustedBoundingBox != null) {
                adjustedBoundingBox.write(output);
            }
            output.writeInt(pieces.size());
            for (Piece piece : pieces) {
                piece.write(output);
            }
        }
    }

    public record ReferenceGroup(String structureId, List<StructureStart> orderedStarts) {
        public ReferenceGroup {
            requireText(structureId, "structureId");
            orderedStarts = List.copyOf(orderedStarts);
            if (orderedStarts.isEmpty()) {
                throw new IllegalArgumentException("empty structure reference group");
            }
            for (StructureStart start : orderedStarts) {
                if (!structureId.equals(start.structureId())) {
                    throw new IllegalArgumentException("reference group/start structure mismatch");
                }
            }
        }

        void write(DataOutputStream output) throws IOException {
            writeString(output, structureId);
            output.writeInt(orderedStarts.size());
            for (StructureStart start : orderedStarts) {
                start.write(output);
            }
        }
    }

    public record ChunkCarrier(int chunkX, int chunkZ, List<ReferenceGroup> referenceGroups) {
        public ChunkCarrier {
            referenceGroups = List.copyOf(referenceGroups);
        }

        /** SHA-256 of all immutable carrier facts in observed traversal order. */
        public String receiptSha256() {
            return sha256(writeReceipt(output -> {
                output.write(CARRIER_RECEIPT_MAGIC);
                output.writeInt(chunkX);
                output.writeInt(chunkZ);
                output.writeInt(referenceGroups.size());
                for (ReferenceGroup group : referenceGroups) {
                    group.write(output);
                }
            }));
        }
    }

    private record Rigid(
            BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {}

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is blank");
        }
    }
}
