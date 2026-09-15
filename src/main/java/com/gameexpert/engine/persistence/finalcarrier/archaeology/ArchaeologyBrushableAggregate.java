package com.gameexpert.engine.persistence.finalcarrier.archaeology;

import com.gameexpert.terrain.Blocks;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable aggregate boundary for one {@code MCF263LC} {@code ARCH} installation.
 *
 * <p>The final-carrier transaction owner persists this value before acknowledging its lane, and
 * the runtime replays the same value after an unknown commit. Only a byte-for-byte identical
 * replay is accepted. Reusing either identity with a different payload fails closed.</p>
 */
public final class ArchaeologyBrushableAggregate {

    public enum InstallOutcome {
        INSTALLED,
        ALREADY_INSTALLED
    }

    public enum ConsumeOutcome {
        CONSUMED,
        ALREADY_CONSUMED
    }

    private final Installation installation;
    private volatile String resultIdentity;
    private volatile String currentExactBlockState;
    private volatile long targetRevision;
    private volatile boolean revoked;

    private ArchaeologyBrushableAggregate(Installation installation) {
        this.installation = Objects.requireNonNull(installation, "installation");
        this.currentExactBlockState = installation.target().exactBlockState();
    }

    public static ArchaeologyBrushableAggregate install(Installation installation) {
        return new ArchaeologyBrushableAggregate(installation);
    }

    /** Accepts an unknown-commit retry only when the complete installation payload is identical. */
    public InstallOutcome replayInstall(Installation candidate) {
        Objects.requireNonNull(candidate, "installation replay");
        if (!installation.samePayload(candidate)) {
            throw new IllegalStateException(
                    "archaeology installation identity or payload conflict");
        }
        return InstallOutcome.ALREADY_INSTALLED;
    }

    /**
     * Records the stable identity of the authoritative result. The result itself belongs to the
     * atomic consumption persistence boundary, not this value contract.
     */
    public synchronized ConsumeOutcome consume(String candidateResultIdentity) {
        return consume(candidateResultIdentity, currentExactBlockState, targetRevision);
    }

    public synchronized ConsumeOutcome consume(String candidateResultIdentity,
            String candidateExactBlockState, long candidateTargetRevision) {
        String candidate = requireIdentity(candidateResultIdentity, "result identity");
        String current = requireExactBlockState(candidateExactBlockState);
        if (revoked || !currentExactBlockState.equals(current)
                || targetRevision != candidateTargetRevision) {
            throw new IllegalStateException("archaeology target provenance conflict");
        }
        String expected = ArchaeologyLootResolver.pinned()
                .resolve(exactTableKey(), rawSeed()).resultIdentity();
        if (!expected.equals(candidate)) {
            throw new IllegalStateException("archaeology deterministic result conflict");
        }
        if (resultIdentity == null) {
            resultIdentity = candidate;
            return ConsumeOutcome.CONSUMED;
        }
        if (!resultIdentity.equals(candidate)) {
            throw new IllegalStateException("archaeology consumption payload conflict");
        }
        return ConsumeOutcome.ALREADY_CONSUMED;
    }

    public synchronized void advanceTarget(String expectedExactBlockState,
            long expectedRevision, String nextExactBlockState) {
        if (consumed() || revoked || targetRevision != expectedRevision
                || !currentExactBlockState.equals(requireExactBlockState(
                        expectedExactBlockState))) {
            throw new IllegalStateException("archaeology target provenance conflict");
        }
        currentExactBlockState = requireExactBlockState(nextExactBlockState);
        targetRevision++;
    }

    public synchronized void revoke(String expectedExactBlockState, long expectedRevision) {
        if (consumed() || revoked) return;
        if (targetRevision != expectedRevision
                || !currentExactBlockState.equals(requireExactBlockState(
                        expectedExactBlockState))) {
            throw new IllegalStateException("archaeology target provenance conflict");
        }
        revoked = true;
        targetRevision++;
    }

    static ArchaeologyBrushableAggregate restore(Installation installation,
            String currentExactBlockState, long targetRevision, boolean revoked,
            String resultIdentity) {
        if (targetRevision < 0) {
            throw new IllegalStateException("negative archaeology target revision");
        }
        ArchaeologyBrushableAggregate aggregate = install(installation);
        aggregate.currentExactBlockState = requireExactBlockState(currentExactBlockState);
        aggregate.targetRevision = targetRevision;
        if (revoked && resultIdentity != null) {
            throw new IllegalStateException("revoked archaeology result cannot be consumed");
        }
        if (resultIdentity != null) aggregate.consume(resultIdentity,
                aggregate.currentExactBlockState, aggregate.targetRevision);
        aggregate.revoked = revoked;
        return aggregate;
    }

    public String installationIdentity() {
        return installation.installationIdentity();
    }

    public String exactTableKey() {
        return installation.exactTableKey();
    }

    /** Returns the unmodified signed 64-bit value carried by the {@code ARCH} section. */
    public long rawSeed() {
        return installation.rawSeed();
    }

    public TargetBlock target() {
        return installation.target();
    }

    /** Returns a defensive copy of the exact canonical lane receipt. */
    public byte[] canonicalReceiptBytes() {
        return installation.canonicalReceiptBytes();
    }

    public boolean consumed() {
        return resultIdentity != null;
    }

    public boolean revoked() {
        return revoked;
    }

    public String currentExactBlockState() {
        return currentExactBlockState;
    }

    public long targetRevision() {
        return targetRevision;
    }

    public Optional<String> resultIdentity() {
        return Optional.ofNullable(resultIdentity);
    }

    /** Immutable installation value; text and receipt bytes are retained without normalization. */
    public static final class Installation {
        private final String installationIdentity;
        private final String exactTableKey;
        private final long rawSeed;
        private final TargetBlock target;
        private final byte[] canonicalReceiptBytes;

        public Installation(String installationIdentity, String exactTableKey, long rawSeed,
                TargetBlock target, byte[] canonicalReceiptBytes) {
            this.installationIdentity = requireIdentity(
                    installationIdentity, "installation identity");
            this.exactTableKey = requireResourceKey(exactTableKey, "archaeology table key");
            this.rawSeed = rawSeed;
            this.target = Objects.requireNonNull(target, "target block");
            this.canonicalReceiptBytes = requireReceipt(canonicalReceiptBytes);
            ArchaeologyLootResolver.pinned().resolve(this.exactTableKey, this.rawSeed);
        }

        public String installationIdentity() {
            return installationIdentity;
        }

        public String exactTableKey() {
            return exactTableKey;
        }

        public long rawSeed() {
            return rawSeed;
        }

        public TargetBlock target() {
            return target;
        }

        public byte[] canonicalReceiptBytes() {
            return canonicalReceiptBytes.clone();
        }

        private boolean samePayload(Installation other) {
            return installationIdentity.equals(other.installationIdentity)
                    && exactTableKey.equals(other.exactTableKey)
                    && rawSeed == other.rawSeed
                    && target.equals(other.target)
                    && MessageDigest.isEqual(canonicalReceiptBytes, other.canonicalReceiptBytes);
        }
    }

    /** Exact world position and canonical block-state string observed at installation. */
    public static final class TargetBlock {
        private final int x;
        private final int y;
        private final int z;
        private final String exactBlockState;

        public TargetBlock(int x, int y, int z, String exactBlockState) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
                throw new IllegalArgumentException("archaeology target Y outside world: " + y);
            }
            this.x = x;
            this.y = y;
            this.z = z;
            this.exactBlockState = requireExactBlockState(exactBlockState);
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public String exactBlockState() {
            return exactBlockState;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TargetBlock value)) return false;
            return x == value.x && y == value.y && z == value.z
                    && exactBlockState.equals(value.exactBlockState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, exactBlockState);
        }
    }

    private static String requireIdentity(String value, String description) {
        Objects.requireNonNull(value, description);
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(description + " must be nonempty and contain no NUL");
        }
        return value;
    }

    private static String requireExactBlockState(String value) {
        Objects.requireNonNull(value, "exact block state");
        int properties = value.indexOf('[');
        String blockKey = properties < 0 ? value : value.substring(0, properties);
        requireResourceKey(blockKey, "target block key");
        if (!blockKey.equals("minecraft:suspicious_sand")
                && !blockKey.equals("minecraft:suspicious_gravel")) {
            throw new IllegalArgumentException(
                    "archaeology target is not brushable: " + blockKey);
        }
        if (properties >= 0) {
            if (properties == value.length() - 1 || !value.endsWith("]")
                    || value.indexOf('[', properties + 1) >= 0
                    || value.indexOf(']') != value.length() - 1) {
                throw new IllegalArgumentException("malformed exact target block state: " + value);
            }
            String[] assignments = value.substring(properties + 1, value.length() - 1)
                    .split(",", -1);
            for (String assignment : assignments) {
                int equals = assignment.indexOf('=');
                if (equals <= 0 || equals != assignment.lastIndexOf('=')
                        || equals == assignment.length() - 1
                        || !isStateToken(assignment.substring(0, equals), false)
                        || !isStateToken(assignment.substring(equals + 1), true)) {
                    throw new IllegalArgumentException(
                            "malformed exact target block state: " + value);
                }
            }
        }
        return value;
    }

    private static boolean isStateToken(String value, boolean allowDotAndDash) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && (!allowDotAndDash || character != '.' && character != '-')) {
                return false;
            }
        }
        return true;
    }

    private static String requireResourceKey(String value, String description) {
        Objects.requireNonNull(value, description);
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1) {
            throw new IllegalArgumentException("invalid exact " + description + ": " + value);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean separator = index == colon;
            boolean valid = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_' || character == '-' || character == '.'
                    || separator || index > colon && character == '/';
            if (!valid) {
                throw new IllegalArgumentException("invalid exact " + description + ": " + value);
            }
        }
        return value;
    }

    private static byte[] requireReceipt(byte[] value) {
        Objects.requireNonNull(value, "canonical receipt bytes");
        if (value.length == 0) {
            throw new IllegalArgumentException("canonical receipt bytes must not be empty");
        }
        return value.clone();
    }
}
