package com.gameexpert.authority.versioned;

import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.world.WorldGenerationProfile;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Callers hold the producer monitor for the entire upload/use/close lifecycle. */
final class ProducerSnapshotUpload implements AutoCloseable {
    private static final int BATCH_BYTES = 1024 * 1024;
    private static final int PAGE_SIZE = 256;
    private final IsolatedProducerSession producer;
    private final WorldGenerationProfile profile;
    private final long token;
    private boolean closed;

    ProducerSnapshotUpload(IsolatedProducerSession producer, WorldGenerationProfile profile,
            long worldId, List<CanonicalStructureSnapshot.Row> rows) throws IOException {
        if (!Thread.holdsLock(producer)) throw new IllegalStateException("snapshot upload requires producer lock");
        this.producer = producer;
        this.profile = profile;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = ProducerStoreWire.header(bytes, profile, 15);
        out.writeLong(worldId);
        out.writeInt(rows.size());
        out.flush();
        try (DataInputStream response = response(bytes)) {
            token = response.readLong();
            requireEnd(response);
        }
        try {
            for (int start = 0; start < rows.size();) {
                int end = start;
                long size = 0;
                do {
                    size += 12L + rows.get(end).byteLength();
                    end++;
                } while (end < rows.size() && size + 12L + rows.get(end).byteLength() <= BATCH_BYTES);
                append(rows.subList(start, end));
                start = end;
            }
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (IOException | RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void append(List<CanonicalStructureSnapshot.Row> rows) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, 16);
        out.writeInt(rows.size());
        for (CanonicalStructureSnapshot.Row row : rows) {
            out.writeInt(row.chunkX());
            out.writeInt(row.chunkZ());
            ProducerStoreWire.write(out, row.carrier());
        }
        out.flush();
        try (DataInputStream response = response(bytes)) { requireEnd(response); }
    }

    void claims(List<LateLootOutcome.Claim> claims) throws IOException {
        for (int start = 0; start < claims.size(); start += PAGE_SIZE) {
            List<LateLootOutcome.Claim> page = claims.subList(start, Math.min(start + PAGE_SIZE, claims.size()));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = header(bytes, 19);
            out.writeInt(page.size());
            for (LateLootOutcome.Claim claim : page) {
                out.writeUTF(claim.structureId());
                out.writeInt(claim.originChunkX());
                out.writeInt(claim.originChunkZ());
                out.writeUTF(claim.structureRowSha256());
            }
            out.flush();
            try (DataInputStream response = response(bytes)) { requireEnd(response); }
        }
    }

    void requireReceipt(String expected) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, 17);
        out.writeInt(0);
        out.writeInt(1);
        out.flush();
        try (DataInputStream in = response(bytes)) {
            String actual = in.readUTF();
            int total = in.readInt();
            int count = in.readInt();
            if (!actual.equals(expected) || total < 0 || count != Math.min(total, 1)) {
                throw new IOException("immutable uploaded snapshot receipt changed");
            }
            if (count == 1) {
                in.readUTF();
                in.readInt();
                in.readInt();
                if (in.readInt() < 0) throw new IOException("negative structure reference");
            }
            requireEnd(in);
        }
    }

    Mc263LocatedMapAuthority.StructureReferenceSnapshot references() throws IOException {
        Map<String, Integer> values = new HashMap<>();
        String receipt = null;
        int offset = 0;
        int total = -1;
        do {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = header(bytes, 17);
            out.writeInt(offset);
            out.writeInt(PAGE_SIZE);
            out.flush();
            try (DataInputStream in = response(bytes)) {
                String pageReceipt = in.readUTF();
                int pageTotal = in.readInt();
                int count = in.readInt();
                if (!pageReceipt.matches("[0-9a-f]{64}") || pageTotal < 0 || count < 0
                        || count > PAGE_SIZE || (long) offset + count > pageTotal
                        || (count == 0 && offset < pageTotal)
                        || (receipt != null && (!receipt.equals(pageReceipt) || total != pageTotal))) {
                    throw new IOException("invalid reference page binding");
                }
                receipt = pageReceipt;
                total = pageTotal;
                for (int index = 0; index < count; index++) {
                    String key = in.readUTF();
                    int x = in.readInt();
                    int z = in.readInt();
                    int value = in.readInt();
                    if (value < 0 || values.put(key + "\0" + x + "\0" + z, value) != null) {
                        throw new IOException("duplicate or negative structure reference");
                    }
                }
                requireEnd(in);
                offset += count;
            }
        } while (offset < total);
        String frozenReceipt = receipt;
        Map<String, Integer> frozenValues = Map.copyOf(values);
        return new Mc263LocatedMapAuthority.StructureReferenceSnapshot() {
            @Override public String receipt() { return frozenReceipt; }
            @Override public int references(String key, int x, int z) {
                return frozenValues.getOrDefault(key + "\0" + x + "\0" + z, 0);
            }
        };
    }

    byte[] execute(byte[] legacyRequest) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, 18);
        ProducerStoreWire.write(out, legacyRequest);
        out.flush();
        return producer.exchange(bytes.toByteArray());
    }

    private DataOutputStream header(ByteArrayOutputStream bytes, int operation) throws IOException {
        if (closed) throw new IllegalStateException("snapshot upload is closed");
        DataOutputStream out = ProducerStoreWire.header(bytes, profile, operation);
        out.writeLong(token);
        return out;
    }

    private DataInputStream response(ByteArrayOutputStream bytes) throws IOException {
        return ProducerStoreWire.response(producer.exchange(bytes.toByteArray()));
    }

    private static void requireEnd(DataInputStream input) throws IOException {
        if (input.available() != 0) throw new IOException("trailing snapshot upload response");
    }

    @Override public void close() throws IOException {
        if (closed) return;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, 20);
        out.flush();
        closed = true;
        try (DataInputStream response = response(bytes)) { requireEnd(response); }
    }
}
