package com.gameexpert.authority.versioned.worker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class WorkerSnapshotUploadTest {
    @AfterEach void cleanup() { WorkerSnapshotUpload.close(); }

    @Test void emptySnapshotUsesExactHistoricalDigestAndCanBeClosed() throws Exception {
        long token = begin(41, 0);
        DataInputStream page = call(17, out -> { out.writeLong(token); out.writeInt(0); out.writeInt(512); });
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        DataOutputStream fields = new DataOutputStream(legacy);
        fields.write("MC263-CANONICAL-STRUCTURE-REFERENCE-SNAPSHOT-V1\0".getBytes(StandardCharsets.US_ASCII));
        fields.writeLong(41); fields.writeInt(0);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacy.toByteArray())), page.readUTF());
        assertEquals(0, page.readInt()); assertEquals(0, page.readInt()); assertEquals(0, page.available());
        assertEquals(0, call(20, out -> out.writeLong(token)).available());
        assertNotEquals(token, begin(41, 0));
    }

    @Test void incorrectTokenInvalidatesUploadAndAllowsFreshBegin() throws Exception {
        long token = begin(41, 0);
        assertThrows(IllegalArgumentException.class, () -> call(20, out -> out.writeLong(token + 1)));
        assertNotEquals(token, begin(41, 0));
    }

    @Test void incompleteSnapshotCannotPublishAndFailureReleasesState() throws Exception {
        long token = begin(41, 1);
        assertThrows(IllegalArgumentException.class, () -> call(17, out -> {
            out.writeLong(token); out.writeInt(0); out.writeInt(512);
        }));
        assertNotEquals(token, begin(41, 0));
    }

    @Test void trailingBeginBytesRejectAndReleaseState() {
        assertThrows(IllegalArgumentException.class, () -> call(15, out -> {
            out.writeLong(41); out.writeInt(0); out.writeByte(1);
        }));
        assertDoesNotThrow(() -> begin(41, 0));
    }

    @Test void duplicateClaimAcrossBatchesIsRejected() throws Exception {
        long token = begin(41, 0);
        Writer batch = out -> {
            out.writeLong(token); out.writeInt(1); out.writeUTF("minecraft:buried_treasure");
            out.writeInt(1); out.writeInt(2); out.writeUTF("a".repeat(64));
        };
        call(19, batch);
        assertThrows(IllegalArgumentException.class, () -> call(19, batch));
        assertDoesNotThrow(() -> begin(41, 0));
    }

    private static long begin(long world, int rows) throws Exception {
        return call(15, out -> { out.writeLong(world); out.writeInt(rows); }).readLong();
    }
    private static DataInputStream call(int operation, Writer writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        writer.write(new DataOutputStream(bytes));
        byte[] response = WorkerSnapshotUpload.execute(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), operation);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(response));
        assertEquals(0x57504731, input.readInt()); assertEquals(1, input.readUnsignedByte());
        return input;
    }
    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws Exception; }
}
