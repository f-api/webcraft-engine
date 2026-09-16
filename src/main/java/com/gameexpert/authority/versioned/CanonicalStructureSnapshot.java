package com.gameexpert.authority.versioned;

import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Immutable membership; carriers can be loaded individually from immutable persisted rows. */
public final class CanonicalStructureSnapshot {
    public static final class Row {
        private static final int MAX_ROW_BYTES = 16 * 1024 * 1024;
        private final int x;
        private final int z;
        private final int byteLength;
        private final Supplier<byte[]> loader;

        public Row(int x, int z, byte[] carrier) {
            requireCarrier(carrier);
            byte[] frozen = carrier.clone();
            this.x = x;
            this.z = z;
            this.byteLength = frozen.length;
            this.loader = () -> frozen;
        }

        private Row(int x, int z, int byteLength, Supplier<byte[]> loader) {
            if (byteLength <= 0 || byteLength > MAX_ROW_BYTES) {
                throw new IllegalArgumentException("invalid structure row");
            }
            this.x = x;
            this.z = z;
            this.byteLength = byteLength;
            this.loader = Objects.requireNonNull(loader);
        }

        public static Row lazy(int x, int z, int byteLength, Supplier<byte[]> loader) {
            return new Row(x, z, byteLength, loader);
        }

        public int chunkX() { return x; }
        public int chunkZ() { return z; }
        public int byteLength() { return byteLength; }

        public byte[] carrier() {
            byte[] loaded = loader.get();
            requireCarrier(loaded);
            if (loaded.length != byteLength) {
                throw new IllegalStateException("immutable structure row length changed");
            }
            return loaded.clone();
        }

        private static void requireCarrier(byte[] carrier) {
            if (carrier == null || carrier.length == 0 || carrier.length > MAX_ROW_BYTES) {
                throw new IllegalArgumentException("invalid structure row");
            }
        }
    }

    private final long worldId;
    private final WorldGenerationProfile profile;
    private final List<Row> rows;
    private final Mc263LocatedMapAuthority.StructureReferenceSnapshot references;

    public CanonicalStructureSnapshot(long worldId, WorldGenerationProfile profile, List<Row> rows) {
        if (worldId <= 0 || rows == null) throw new IllegalArgumentException("invalid structure snapshot");
        this.worldId = worldId;
        this.profile = WorldGenerationProfiles.requireSupported(profile);
        ArrayList<Row> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparingInt(Row::chunkX).thenComparingInt(Row::chunkZ));
        for (int index = 1; index < ordered.size(); index++) {
            Row previous = ordered.get(index - 1);
            Row row = ordered.get(index);
            if (row.x == previous.x && row.z == previous.z) {
                throw new IllegalArgumentException("duplicate structure snapshot row");
            }
        }
        this.rows = List.copyOf(ordered);
        this.references = ProducerStoreWire.referencesRows(
                ProducerAuthorities.forProfile(this.profile), this.profile, worldId, this.rows);
    }

    public long worldId() { return worldId; }
    public WorldGenerationProfile profile() { return profile; }
    public List<Row> rows() { return rows; }
    public String receipt() { return references.receipt(); }
    public Mc263LocatedMapAuthority.StructureReferenceSnapshot references() { return references; }
}
