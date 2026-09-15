package com.gameexpert.engine.persistence.finalcarrier.bees;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Current-only schema-4 binary projection of exact canonical bee-nest occupants. */
public final class CanonicalBeePayload {
    private static final int MAGIC = 0x42454534; // BEE4
    private static final int SCHEMA = 4;
    private static final int MAX_NESTS = 65_536;
    private static final int MAX_OCCUPANTS_PER_NEST = 3;

    private CanonicalBeePayload() {
    }

    public static byte[] encode(List<NeutralFinalChunk.BeeNest> nests) {
        if (nests.size() > MAX_NESTS) {
            throw new IllegalArgumentException("too many canonical bee nests");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(SCHEMA);
            out.writeInt(nests.size());
            for (NeutralFinalChunk.BeeNest nest : nests) {
                if (nest.ticksInHive().size() > MAX_OCCUPANTS_PER_NEST) {
                    throw new IllegalArgumentException("canonical bee nest exceeds capacity");
                }
                out.writeInt(nest.packed());
                out.writeInt(nest.ticksInHive().size());
                for (int ticks : nest.ticksInHive()) out.writeInt(ticks);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static List<NeutralFinalChunk.BeeNest> decode(byte[] payload) {
        if (payload == null) throw new IllegalArgumentException("canonical bee payload required");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != MAGIC || in.readInt() != SCHEMA) {
                throw new IllegalArgumentException("canonical bee payload is not schema 4");
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_NESTS) {
                throw new IllegalArgumentException("invalid canonical bee nest count");
            }
            ArrayList<NeutralFinalChunk.BeeNest> nests = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int packed = in.readInt();
                int occupants = in.readInt();
                if (occupants < 0 || occupants > MAX_OCCUPANTS_PER_NEST) {
                    throw new IllegalArgumentException("invalid canonical bee occupant count");
                }
                ArrayList<Integer> ticks = new ArrayList<>(occupants);
                for (int j = 0; j < occupants; j++) ticks.add(in.readInt());
                nests.add(new NeutralFinalChunk.BeeNest(packed, ticks));
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException("trailing canonical bee payload bytes");
            }
            return List.copyOf(nests);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("truncated canonical bee payload", truncated);
        }
    }
}
