package com.gameexpert.engine.persistence.finalcarrier.spawner;

import com.gameexpert.engine.mob.SpawnerRules;
import com.gameexpert.terrain.Blocks;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, completely preflighted schema-4 SPWN installation definition. */
public final class SpawnerAggregate {
    private static final byte[] FINGERPRINT_DOMAIN =
            "MCF263LC/SPWN/AGGREGATE/v1".getBytes(StandardCharsets.US_ASCII);

    private final String installationIdentity;
    private final String sourceFingerprint;
    private final List<PlannedSpawner> plannedSpawners;

    private SpawnerAggregate(String installationIdentity, String sourceFingerprint,
            List<PlannedSpawner> plannedSpawners) {
        this.installationIdentity = installationIdentity;
        this.sourceFingerprint = sourceFingerprint;
        this.plannedSpawners = plannedSpawners;
    }

    /** Validates every row and its exact carrier block before returning any installable state. */
    public static SpawnerAggregate prepare(String installationIdentity,
            NeutralFinalChunk typedCarrier) {
        if (installationIdentity == null || installationIdentity.isBlank()) {
            throw new IllegalArgumentException("SPWN installation identity required");
        }
        Objects.requireNonNull(typedCarrier, "typed SPWN carrier");
        List<NeutralFinalChunk.Spawner> rows = typedCarrier.sidecars().spawners();
        short[] blockIds = typedCarrier.blockIds();
        Set<Integer> positions = new HashSet<>();
        ArrayList<PlannedSpawner> planned = new ArrayList<>(rows.size());
        for (NeutralFinalChunk.Spawner row : rows) {
            if (!positions.add(row.packed())) {
                throw new IllegalArgumentException("duplicate SPWN carrier position");
            }
            int state = SpawnerRules.stateForEntityKey(row.entityType());
            int expectedBlockId = SpawnerRules.carrierForEntityKey(row.entityType());
            int carrierBlockId = Short.toUnsignedInt(blockIds[row.packed()]);
            if (state == 0 || expectedBlockId == 0 || carrierBlockId != expectedBlockId) {
                throw new IllegalArgumentException(
                        "SPWN entity key does not match exact carrier block evidence");
            }
            int localX = row.packed() % Blocks.CHUNK_X;
            int yz = row.packed() / Blocks.CHUNK_X;
            int localZ = yz % Blocks.CHUNK_Z;
            int y = Blocks.MIN_Y + yz / Blocks.CHUNK_Z;
            planned.add(new PlannedSpawner(row.packed(),
                    typedCarrier.chunkX() * Blocks.CHUNK_X + localX, y,
                    typedCarrier.chunkZ() * Blocks.CHUNK_Z + localZ,
                    row.entityType(), carrierBlockId, state));
        }
        List<PlannedSpawner> immutable = List.copyOf(planned);
        return new SpawnerAggregate(installationIdentity,
                fingerprint(installationIdentity, typedCarrier.chunkX(), typedCarrier.chunkZ(),
                        immutable), immutable);
    }

    public String installationIdentity() {
        return installationIdentity;
    }

    public String sourceFingerprint() {
        return sourceFingerprint;
    }

    public List<PlannedSpawner> plannedSpawners() {
        return plannedSpawners;
    }

    public boolean sameDefinition(SpawnerAggregate other) {
        return other != null && installationIdentity.equals(other.installationIdentity)
                && sourceFingerprint.equals(other.sourceFingerprint)
                && plannedSpawners.equals(other.plannedSpawners);
    }

    private static String fingerprint(String identity, int chunkX, int chunkZ,
            List<PlannedSpawner> planned) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_DOMAIN);
            update(digest, identity);
            update(digest, chunkX);
            update(digest, chunkZ);
            update(digest, planned.size());
            for (PlannedSpawner value : planned) {
                update(digest, value.packed());
                update(digest, value.x());
                update(digest, value.y());
                update(digest, value.z());
                update(digest, value.entityKey());
                update(digest, value.carrierBlockId());
                update(digest, value.authoritativeState());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    /** Exact runtime state for one preflighted carrier cell. */
    public record PlannedSpawner(int packed, int x, int y, int z, String entityKey,
            int carrierBlockId, int authoritativeState) {
        public PlannedSpawner {
            Objects.requireNonNull(entityKey, "SPWN entity key");
        }
    }
}
