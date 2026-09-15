package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import com.gameexpert.authority.versioned.NeutralFinalChunk.StructureEntity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Durable aggregate for one schema-4 {@code ENTS} final-carrier installation.
 *
 * <p>The Spring transaction owner installs the complete {@link #plannedEntities()} list and commits
 * the complete {@link #expectedReceipts()} list in one atomic boundary. Encounter order is
 * identity-bearing: each authoritative entity ID is a
 * deterministic function of the installation identity and encounter ordinal. Exact row facts are
 * fingerprinted separately, so reusing that identity for reordered or changed facts fails closed.
 * A terminal snapshot can be restored only when it proves the same complete installation.</p>
 */
public final class StructureEntityAggregate {

    private static final byte[] SOURCE_DOMAIN =
            "MCF263LC/ENTS/SOURCE/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ROW_DOMAIN =
            "MCF263LC/ENTS/ROW/v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ENTITY_ID_DOMAIN =
            "MCF263LC/ENTS/AUTHORITY-ID/v1".getBytes(StandardCharsets.US_ASCII);

    public enum InstallOutcome {
        INSTALLED,
        ALREADY_INSTALLED
    }

    private final Installation installation;
    private volatile TerminalState terminalState;

    private StructureEntityAggregate(Installation installation) {
        this.installation = Objects.requireNonNull(installation, "installation");
    }

    /** Creates a prepared aggregate without installing any runtime entity. */
    public static StructureEntityAggregate prepare(Installation installation) {
        return new StructureEntityAggregate(installation);
    }

    /**
     * Restores an installed aggregate after a restart. Partial or conflicting durable state is
     * rejected rather than interpreted as prepared or repaired implicitly.
     */
    public static StructureEntityAggregate restore(Installation installation,
            TerminalState terminalState) {
        StructureEntityAggregate aggregate = prepare(installation);
        aggregate.validateTerminalState(
                Objects.requireNonNull(terminalState, "terminal state"));
        aggregate.terminalState = terminalState.copy();
        return aggregate;
    }

    /**
     * Commits the complete atomic installation receipt. Identical unknown-commit retries are
     * idempotent; partial, reordered, or conflicting receipts fail closed.
     */
    public synchronized InstallOutcome commit(List<InstallReceipt> candidateReceipts) {
        List<InstallReceipt> candidate = immutableReceipts(candidateReceipts);
        validateCompleteReceipts(candidate);
        TerminalState candidateState = new TerminalState(installation.installationIdentity(),
                installation.sourceFingerprint(), candidate);
        if (terminalState == null) {
            terminalState = candidateState;
            return InstallOutcome.INSTALLED;
        }
        if (!terminalState.samePayload(candidateState)) {
            throw new IllegalStateException("ENTS terminal installation conflict");
        }
        return InstallOutcome.ALREADY_INSTALLED;
    }

    public String installationIdentity() {
        return installation.installationIdentity();
    }

    public String sourceFingerprint() {
        return installation.sourceFingerprint();
    }

    public List<PlannedEntity> plannedEntities() {
        return installation.plannedEntities();
    }

    public List<InstallReceipt> expectedReceipts() {
        return installation.expectedReceipts();
    }

    public boolean installed() {
        return terminalState != null;
    }

    /** Returns a defensive restart snapshot only after the atomic installation is terminal. */
    public Optional<TerminalState> terminalState() {
        TerminalState value = terminalState;
        return value == null ? Optional.empty() : Optional.of(value.copy());
    }

    private void validateTerminalState(TerminalState state) {
        if (!installation.installationIdentity().equals(state.installationIdentity())
                || !installation.sourceFingerprint().equals(state.sourceFingerprint())) {
            throw new IllegalStateException("ENTS terminal source conflict");
        }
        validateCompleteReceipts(state.receipts());
    }

    private void validateCompleteReceipts(List<InstallReceipt> candidate) {
        List<InstallReceipt> expected = installation.expectedReceipts();
        if (candidate.size() != expected.size()) {
            throw new IllegalStateException("partial ENTS installation is forbidden");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).equals(candidate.get(index))) {
                throw new IllegalStateException(
                        "ENTS installation receipt conflict at encounter ordinal " + index);
            }
        }
    }

    /** Complete immutable schema-4 source facts for one carrier installation. */
    public static final class Installation {
        private final String installationIdentity;
        private final String sourceFingerprint;
        private final List<PlannedEntity> plannedEntities;
        private final List<InstallReceipt> expectedReceipts;

        public Installation(String installationIdentity, List<StructureEntity> orderedRows) {
            this(installationIdentity, orderedRows, null);
        }

        /** Builds the same exact plan with IDs reserved by the durable per-world allocator. */
        public Installation(String installationIdentity, List<StructureEntity> orderedRows,
                List<Long> authoritativeEntityIds) {
            this.installationIdentity = requireIdentity(
                    installationIdentity, "ENTS installation identity");
            Objects.requireNonNull(orderedRows, "ordered ENTS rows");
            if (orderedRows.isEmpty()) {
                throw new IllegalArgumentException("ordered ENTS rows must not be empty");
            }
            ArrayList<PlannedEntity> planned = new ArrayList<>(orderedRows.size());
            if (authoritativeEntityIds != null
                    && authoritativeEntityIds.size() != orderedRows.size()) {
                throw new IllegalArgumentException("ENTS authoritative ID count mismatch");
            }
            Set<Long> entityIds = new HashSet<>();
            for (int ordinal = 0; ordinal < orderedRows.size(); ordinal++) {
                StructureEntity row = Objects.requireNonNull(
                        orderedRows.get(ordinal), "ENTS row at ordinal " + ordinal);
                long authoritativeId = authoritativeEntityIds == null
                        ? authoritativeEntityId(this.installationIdentity, ordinal)
                        : Objects.requireNonNull(authoritativeEntityIds.get(ordinal),
                                "ENTS authoritative ID at ordinal " + ordinal);
                if (authoritativeId <= 0L) {
                    throw new IllegalArgumentException("ENTS authoritative ID must be positive");
                }
                PlannedEntity entity = new PlannedEntity(ordinal, authoritativeId, row);
                if (!entityIds.add(entity.authoritativeEntityId())) {
                    throw new IllegalStateException(
                            "deterministic ENTS authoritative entity ID collision");
                }
                planned.add(entity);
            }
            this.plannedEntities = List.copyOf(planned);
            this.expectedReceipts = planned.stream()
                    .map(PlannedEntity::expectedReceipt).toList();
            this.sourceFingerprint = StructureEntityAggregate.sourceFingerprint(
                    this.installationIdentity, this.plannedEntities);
        }

        public String installationIdentity() {
            return installationIdentity;
        }

        public String sourceFingerprint() {
            return sourceFingerprint;
        }

        public List<PlannedEntity> plannedEntities() {
            return plannedEntities;
        }

        public List<InstallReceipt> expectedReceipts() {
            return expectedReceipts;
        }
    }

    /** Exact immutable spawn request derived from one encounter-ordered schema-4 row. */
    public static final class PlannedEntity {
        private final int encounterOrdinal;
        private final long authoritativeEntityId;
        private final String entityKey;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final double velocityX;
        private final double velocityY;
        private final double velocityZ;
        private final String spawnReason;
        private final String lootTable;
        private final long lootSeed;
        private final byte[] canonicalPayload;
        private final String rowFingerprint;

        private PlannedEntity(int encounterOrdinal, long authoritativeEntityId,
                StructureEntity row) {
            this.encounterOrdinal = encounterOrdinal;
            this.authoritativeEntityId = authoritativeEntityId;
            this.entityKey = row.entityKey();
            this.x = row.x();
            this.y = row.y();
            this.z = row.z();
            this.yaw = row.yaw();
            this.pitch = row.pitch();
            this.velocityX = row.velocityX();
            this.velocityY = row.velocityY();
            this.velocityZ = row.velocityZ();
            this.spawnReason = row.spawnReason();
            this.lootTable = row.lootTable();
            this.lootSeed = row.lootSeed();
            this.canonicalPayload = row.canonicalPayload();
            this.rowFingerprint = StructureEntityAggregate.rowFingerprint(this);
        }

        public int encounterOrdinal() { return encounterOrdinal; }
        public long authoritativeEntityId() { return authoritativeEntityId; }
        public String entityKey() { return entityKey; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public double velocityX() { return velocityX; }
        public double velocityY() { return velocityY; }
        public double velocityZ() { return velocityZ; }
        public String spawnReason() { return spawnReason; }
        public String lootTable() { return lootTable; }
        public long lootSeed() { return lootSeed; }
        public byte[] canonicalPayload() { return canonicalPayload.clone(); }
        public String rowFingerprint() { return rowFingerprint; }

        public InstallReceipt expectedReceipt() {
            return new InstallReceipt(encounterOrdinal, authoritativeEntityId, rowFingerprint);
        }
    }

    /** Minimal durable proof that the exact planned entity was installed. */
    public static final class InstallReceipt {
        private final int encounterOrdinal;
        private final long authoritativeEntityId;
        private final String rowFingerprint;

        public InstallReceipt(int encounterOrdinal, long authoritativeEntityId,
                String rowFingerprint) {
            if (encounterOrdinal < 0) {
                throw new IllegalArgumentException("negative ENTS encounter ordinal");
            }
            if (authoritativeEntityId <= 0) {
                throw new IllegalArgumentException(
                        "ENTS authoritative entity ID must be positive");
            }
            this.encounterOrdinal = encounterOrdinal;
            this.authoritativeEntityId = authoritativeEntityId;
            this.rowFingerprint = requireFingerprint(rowFingerprint, "ENTS row fingerprint");
        }

        public int encounterOrdinal() { return encounterOrdinal; }
        public long authoritativeEntityId() { return authoritativeEntityId; }
        public String rowFingerprint() { return rowFingerprint; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InstallReceipt value)) return false;
            return encounterOrdinal == value.encounterOrdinal
                    && authoritativeEntityId == value.authoritativeEntityId
                    && rowFingerprint.equals(value.rowFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(encounterOrdinal, authoritativeEntityId, rowFingerprint);
        }
    }

    /** Immutable terminal persistence value used to prove an exactly-once install after restart. */
    public static final class TerminalState {
        private final String installationIdentity;
        private final String sourceFingerprint;
        private final List<InstallReceipt> receipts;

        public TerminalState(String installationIdentity, String sourceFingerprint,
                List<InstallReceipt> receipts) {
            this.installationIdentity = requireIdentity(
                    installationIdentity, "ENTS terminal installation identity");
            this.sourceFingerprint = requireFingerprint(
                    sourceFingerprint, "ENTS terminal source fingerprint");
            this.receipts = immutableReceipts(receipts);
        }

        public String installationIdentity() { return installationIdentity; }
        public String sourceFingerprint() { return sourceFingerprint; }
        public List<InstallReceipt> receipts() { return receipts; }

        private TerminalState copy() {
            return new TerminalState(installationIdentity, sourceFingerprint, receipts);
        }

        private boolean samePayload(TerminalState other) {
            return installationIdentity.equals(other.installationIdentity)
                    && sourceFingerprint.equals(other.sourceFingerprint)
                    && receipts.equals(other.receipts);
        }
    }

    private static List<InstallReceipt> immutableReceipts(List<InstallReceipt> values) {
        Objects.requireNonNull(values, "ENTS install receipts");
        ArrayList<InstallReceipt> copy = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            InstallReceipt value = Objects.requireNonNull(
                    values.get(index), "ENTS install receipt at ordinal " + index);
            copy.add(new InstallReceipt(value.encounterOrdinal(),
                    value.authoritativeEntityId(), value.rowFingerprint()));
        }
        return List.copyOf(copy);
    }

    private static long authoritativeEntityId(String installationIdentity, int ordinal) {
        MessageDigest digest = sha256();
        digest.update(ENTITY_ID_DOMAIN);
        updateString(digest, installationIdentity);
        updateInt(digest, ordinal);
        long value = ByteBuffer.wrap(digest.digest()).getLong() & Long.MAX_VALUE;
        return value == 0L ? 1L : value;
    }

    private static String sourceFingerprint(String identity, List<PlannedEntity> entities) {
        MessageDigest digest = sha256();
        digest.update(SOURCE_DOMAIN);
        updateString(digest, identity);
        updateInt(digest, entities.size());
        for (PlannedEntity entity : entities) {
            updateInt(digest, entity.encounterOrdinal());
            updateString(digest, entity.rowFingerprint());
        }
        return hex(digest.digest());
    }

    private static String rowFingerprint(PlannedEntity entity) {
        MessageDigest digest = sha256();
        digest.update(ROW_DOMAIN);
        updateInt(digest, entity.encounterOrdinal());
        updateString(digest, entity.entityKey());
        updateLong(digest, Double.doubleToRawLongBits(entity.x()));
        updateLong(digest, Double.doubleToRawLongBits(entity.y()));
        updateLong(digest, Double.doubleToRawLongBits(entity.z()));
        updateInt(digest, Float.floatToRawIntBits(entity.yaw()));
        updateInt(digest, Float.floatToRawIntBits(entity.pitch()));
        updateLong(digest, Double.doubleToRawLongBits(entity.velocityX()));
        updateLong(digest, Double.doubleToRawLongBits(entity.velocityY()));
        updateLong(digest, Double.doubleToRawLongBits(entity.velocityZ()));
        updateString(digest, entity.spawnReason());
        updateString(digest, entity.lootTable());
        updateLong(digest, entity.lootSeed());
        updateBytes(digest, entity.canonicalPayload());
        return hex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateBytes(MessageDigest digest, byte[] value) {
        updateInt(digest, value.length);
        digest.update(value);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte element : value) {
            result.append(Character.forDigit((element >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(element & 0x0f, 16));
        }
        return result.toString();
    }

    private static String requireIdentity(String value, String description) {
        Objects.requireNonNull(value, description);
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    description + " must be nonempty and contain no NUL");
        }
        return value;
    }

    private static String requireFingerprint(String value, String description) {
        Objects.requireNonNull(value, description);
        if (value.length() != 64) {
            throw new IllegalArgumentException(description + " must be lowercase SHA-256 hex");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'a' && character <= 'f')) {
                throw new IllegalArgumentException(
                        description + " must be lowercase SHA-256 hex");
            }
        }
        return value;
    }
}
