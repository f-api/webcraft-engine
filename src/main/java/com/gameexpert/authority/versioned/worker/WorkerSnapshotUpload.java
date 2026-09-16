package com.gameexpert.authority.versioned.worker;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded transport frames share one private, token-bound snapshot assembly. */
final class WorkerSnapshotUpload {
    private static long sequence;
    private static WorkerSnapshotUpload active;
    private final long token;
    private final LegacyStoreOperations.SnapshotBuilder builder;
    private final Map<String,String> claims = new LinkedHashMap<>();
    private List<Map.Entry<String,int[]>> references;

    private WorkerSnapshotUpload(long world, int count) throws Exception {
        token = ++sequence;
        builder = new LegacyStoreOperations.SnapshotBuilder(world, count);
    }

    static synchronized void close() { active = null; }

    static synchronized byte[] execute(DataInputStream input, int operation) throws Exception {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0x57504731);
            output.writeByte(1);
            if (operation == 15) {
                if (active != null) throw new IllegalStateException("structure upload already active");
                active = new WorkerSnapshotUpload(input.readLong(), input.readInt());
                output.writeLong(active.token);
            } else {
                long token = input.readLong();
                if (active == null || active.token != token) throw new IllegalArgumentException("invalid structure upload token");
                switch (operation) {
                    case 16 -> active.append(input);
                    case 17 -> active.page(input, output);
                    case 18 -> {
                        byte[] legacy = frame(input);
                        requireEnd(input);
                        return WorkerLateLootOperations.execute(legacy, active.builder.finish(), active.claims);
                    }
                    case 19 -> active.claims(input);
                    case 20 -> active = null;
                    default -> throw new IllegalArgumentException("unsupported structure upload operation");
                }
            }
            requireEnd(input);
            output.flush();
            return bytes.toByteArray();
        } catch (Exception failure) {
            active = null;
            throw failure;
        }
    }

    private void append(DataInputStream input) throws Exception {
        int count = input.readInt();
        if (count < 0 || count * 12L > input.available()) throw new IllegalArgumentException("invalid structure row batch");
        for (int index = 0; index < count; index++) {
            int x = input.readInt();
            int z = input.readInt();
            builder.append(x, z, frame(input));
        }
    }

    private void claims(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count * 12L > input.available()) throw new IllegalArgumentException("invalid claim batch");
        for (int index = 0; index < count; index++) {
            String structure = input.readUTF();
            int x = input.readInt();
            int z = input.readInt();
            String sha = input.readUTF();
            if (!structure.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid claim fields");
            if (claims.put(structure + "\0" + x + "\0" + z, sha) != null) throw new IllegalArgumentException("duplicate claim key");
        }
    }

    private void page(DataInputStream input, DataOutputStream output) throws IOException {
        LegacyStoreOperations.ReferenceSnapshot snapshot = builder.finish();
        if (references == null) references = new ArrayList<>(snapshot.values().entrySet());
        int offset = input.readInt();
        int limit = input.readInt();
        if (offset < 0 || offset > references.size() || limit < 1 || limit > 512) throw new IllegalArgumentException("invalid reference page");
        int count = Math.min(limit, references.size() - offset);
        output.writeUTF(snapshot.receipt());
        output.writeInt(references.size());
        output.writeInt(count);
        for (int index = offset; index < offset + count; index++) {
            Map.Entry<String,int[]> entry = references.get(index);
            output.writeUTF(entry.getKey().substring(0, entry.getKey().indexOf('\0')));
            for (int value : entry.getValue()) output.writeInt(value);
        }
    }

    private static byte[] frame(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > 64 * 1024 * 1024 || size > input.available()) throw new IllegalArgumentException("invalid upload frame");
        return input.readNBytes(size);
    }

    private static void requireEnd(DataInputStream input) throws IOException {
        if (input.available() != 0) throw new IllegalArgumentException("trailing upload bytes");
    }
}
