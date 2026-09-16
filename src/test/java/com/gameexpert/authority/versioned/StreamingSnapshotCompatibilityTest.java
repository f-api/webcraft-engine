package com.gameexpert.authority.versioned;

import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamingSnapshotCompatibilityTest {
    private static final long WORLD = 739L;
    private static final WorldGenerationProfile PROFILE = WorldGenerationProfiles.CURRENT;

    @Test
    void streamedReferencesPreserveLegacyReceiptAndEveryReference() throws Exception {
        byte[] carrier = realCarrier();
        List<CanonicalStructureSnapshot.Row> rows = List.of(
                new CanonicalStructureSnapshot.Row(-2, -2, carrier),
                new CanonicalStructureSnapshot.Row(-1, -2, carrier));
        IsolatedProducerSession producer = ProducerAuthorities.forProfile(PROFILE);
        StructureReferenceSnapshot streamed = ProducerStoreWire.referencesRows(producer, PROFILE, WORLD, rows);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = ProducerStoreWire.header(bytes, PROFILE, 6);
        out.writeLong(WORLD);
        out.writeInt(rows.size());
        for (CanonicalStructureSnapshot.Row row : rows) {
            out.writeInt(row.chunkX());
            out.writeInt(row.chunkZ());
            ProducerStoreWire.write(out, row.carrier());
        }
        out.flush();
        try (DataInputStream response = ProducerStoreWire.response(producer.exchange(bytes.toByteArray()))) {
            assertThat(streamed.receipt()).isEqualTo(response.readUTF());
            int count = response.readInt();
            assertThat(count).isPositive();
            for (int index = 0; index < count; index++) {
                String key = response.readUTF();
                int x = response.readInt();
                int z = response.readInt();
                assertThat(streamed.references(key, x, z)).isEqualTo(response.readInt());
            }
            assertThat(response.available()).isZero();
        }
        assertThat(streamed.references("missing", 0, 0)).isZero();
    }

    @Test
    void realProducerStreamsMoreThan64MiBWithoutEagerlyLoadingWorldCarriers() throws Exception {
        byte[] carrier = realCarrier();
        int count = (64 * 1024 * 1024) / carrier.length + 2;
        AtomicInteger loads = new AtomicInteger();
        List<CanonicalStructureSnapshot.Row> rows = IntStream.range(0, count)
                .mapToObj(index -> CanonicalStructureSnapshot.Row.lazy(index, -2, carrier.length, () -> {
                    loads.incrementAndGet();
                    return carrier;
                })).toList();
        assertThat(loads).hasValue(0);
        assertThat((long) count * carrier.length).isGreaterThan(64L * 1024 * 1024);
        StructureReferenceSnapshot result = ProducerStoreWire.referencesRows(
                ProducerAuthorities.forProfile(PROFILE), PROFILE, WORLD, rows);
        assertThat(loads).hasValue(count);
        assertThat(result.receipt()).isEqualTo(independentReceipt(carrier, count));
        assertThat(loads).hasValue(count);
    }

    @Test
    void failedLazyRowAndExplicitCloseReleaseTheRealWorkerUpload() throws Exception {
        byte[] carrier = realCarrier();
        IsolatedProducerSession producer = ProducerAuthorities.forProfile(PROFILE);
        List<CanonicalStructureSnapshot.Row> broken = List.of(
                CanonicalStructureSnapshot.Row.lazy(0, 0, carrier.length, () -> {
                    throw new IllegalStateException("fixture loader failure");
                }));
        assertThatThrownBy(() -> ProducerStoreWire.referencesRows(producer, PROFILE, WORLD, broken))
                .isInstanceOf(IllegalStateException.class).hasMessage("fixture loader failure");
        synchronized (producer) {
            ProducerSnapshotUpload upload = new ProducerSnapshotUpload(producer, PROFILE, WORLD, List.of());
            assertThat(upload.references().receipt()).hasSize(64);
            upload.close();
            upload.close();
            assertThatThrownBy(upload::references).isInstanceOf(IllegalStateException.class)
                    .hasMessage("snapshot upload is closed");
            try (ProducerSnapshotUpload next = new ProducerSnapshotUpload(producer, PROFILE, WORLD, List.of())) {
                assertThat(next.references().receipt()).hasSize(64);
            }
        }
    }

    private static String independentReceipt(byte[] carrier, int count) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("MC263-CANONICAL-STRUCTURE-REFERENCE-SNAPSHOT-V1\0"
                .getBytes(StandardCharsets.US_ASCII));
        try (DataOutputStream fields = new DataOutputStream(
                new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            fields.writeLong(WORLD);
            fields.writeInt(count);
            for (int index = 0; index < count; index++) {
                fields.writeInt(index);
                fields.writeInt(-2);
                fields.writeInt(carrier.length);
                fields.write(carrier);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] realCarrier() throws IOException {
        CanonicalStructureSnapshot empty = new CanonicalStructureSnapshot(WORLD, PROFILE, List.of());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = ProducerStoreWire.header(bytes, PROFILE, 13);
        output.writeLong(WORLD);
        output.writeInt(1464029505);
        output.writeLong(0L);
        output.writeInt(-2);
        output.writeInt(-2);
        output.writeUTF(empty.receipt());
        output.writeInt(0);
        output.flush();
        try (DataInputStream input = ProducerStoreWire.response(
                ProducerAuthorities.forGeneration(PROFILE).exchange(bytes.toByteArray()))) {
            assertThat(input.readLong()).isEqualTo(WORLD);
            input.readInt();
            input.readInt();
            input.readInt();
            assertThat(input.readUTF()).isEqualTo(empty.receipt());
            ProducerStoreWire.read(input, false);
            byte[] carrier = ProducerStoreWire.read(input, false);
            ProducerStoreWire.read(input, true);
            ProducerStoreWire.read(input, false);
            assertThat(input.available()).isZero();
            return carrier;
        }
    }
}
