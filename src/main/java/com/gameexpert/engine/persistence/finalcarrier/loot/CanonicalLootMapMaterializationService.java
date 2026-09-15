package com.gameexpert.engine.persistence.finalcarrier.loot;

import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.NeutralFinalChunk.TargetMap;
import com.gameexpert.map.service.WorldMapPersistenceService;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomically turns terrain-sealed map placeholders into durable positive world-map identities. */
@Service
public class CanonicalLootMapMaterializationService {
    private static final byte[] MAGIC = "MCF263MAPMAT1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC_V2 = "MCF263MAPMAT2".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RECEIPT_DOMAIN =
            "MCF263/LOOT/MAP-MATERIALIZATION/v1".getBytes(StandardCharsets.US_ASCII);

    public record Materialization(CanonicalLootStoredResolution resolution,
            List<WorldMapData> maps, byte[] payload, String receipt) {
        public Materialization {
            Objects.requireNonNull(resolution, "materialized LOOT resolution");
            maps = List.copyOf(maps);
            payload = payload == null ? null : payload.clone();
            if (maps.isEmpty() != (payload == null && receipt == null)) {
                throw new IllegalArgumentException("map materialization identity is incomplete");
            }
            if (!maps.isEmpty() && !isSha256(receipt)) {
                throw new IllegalArgumentException("map materialization receipt is invalid");
            }
        }

        @Override public byte[] payload() {
            return payload == null ? null : payload.clone();
        }
    }

    private record Entry(int slotIndex, String componentKey, String destination,
            String targetReceipt, int mapId, int centerX, int centerZ, int scale,
            String previewSha256) {}

    private final WorldMapPersistenceService maps;
    private final CanonicalWorldgenStore canonical;

    @Autowired
    public CanonicalLootMapMaterializationService(WorldMapPersistenceService maps,
            CanonicalWorldgenStore canonicalWorldgenStore) {
        this.maps = Objects.requireNonNull(maps, "world map persistence");
        this.canonical = Objects.requireNonNull(canonicalWorldgenStore, "canonical worldgen store");
    }

    private NeutralFinalChunk sourceFor(WorldCanonicalLootAssignment assignment) {
        var verified = assignment.verifiedProducerSource();
        if (verified.isPresent()) return verified.orElseThrow();
        var snapshot = canonical.find(assignment.getWorldId(), assignment.getChunkX(),
                assignment.getChunkZ());
        if (snapshot == null || snapshot.commit().semanticFinalChunk() == null) {
            throw new IllegalStateException("map assignment has no bound canonical producer");
        }
        NeutralFinalChunk source = snapshot.commit().semanticFinalChunk();
        assignment.verifyProducer(source);
        return source;
    }

    private static TargetMap targetFor(NeutralFinalChunk.ProductionContext context, String key) {
        return context.targets().stream().filter(target -> target.key().equals(key))
                .findFirst().orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Materialization materializeJoiningTransaction(
            WorldCanonicalLootAssignment assignment, CanonicalLootStoredResolution candidate) {
        Objects.requireNonNull(assignment, "canonical LOOT assignment");
        Objects.requireNonNull(candidate, "canonical LOOT candidate");
        var late = assignment.verifiedLateOutcome().orElse(null);
        if (late != null && !MessageDigest.isEqual(candidate.encode(), late.resolution())) {
            throw new IllegalStateException("late candidate differs from authenticated producer outcome");
        }
        List<CanonicalLootStoredResolution.PendingMapReference> pending =
                candidate.pendingMapReferences();
        if (pending.isEmpty()) {
            return new Materialization(candidate, List.of(), null, null);
        }
        NeutralFinalChunk source = late == null ? sourceFor(assignment) : null;
        var context = assignment.getEffectiveLocatedProductionContext();
        List<TargetMap> found = new ArrayList<>(pending.size());
        List<WorldMapPersistenceService.NewMapSpec> specs = new ArrayList<>(pending.size());
        for (CanonicalLootStoredResolution.PendingMapReference reference : pending) {
            TargetMap target = targetFor(context, reference.destination());
            if (target == null) {
                throw new IllegalStateException(
                        "pending map has no persisted authenticated target");
            }
            if (target.found() == null
                    || !reference.targetReceipt().equals(target.targetReceipt())) {
                throw new IllegalStateException(
                        "pending map differs from locked authenticated target receipt");
            }
            var exact = target.found();
            byte[] colors = late == null
                    ? source.renderVerifiedMapPreview(assignment.getWorldSeed(), target, true)
                    : late.preview(target.targetReceipt());
            found.add(target);
            specs.add(new WorldMapPersistenceService.NewMapSpec(exact.savedCenterX(),
                    exact.savedCenterZ(), target.binding().scale(), colors,
                    new WorldMapData.TargetMarker(target.binding().destination(), exact.targetX(), exact.targetZ())));
        }
        List<WorldMapData> created = maps.createNextBatchJoiningTransaction(
                assignment.getWorldId(), specs);
        requireFreshBatch(assignment.getWorldId(), specs, created);
        List<Integer> ids = created.stream().map(WorldMapData::getMapId).toList();
        CanonicalLootStoredResolution materialized = candidate.materializePendingMapIds(ids);
        List<Entry> entries = entries(pending, found, created);
        byte[] payload = encode(entries, true);
        String receipt = receipt(assignment.getDefinitionFingerprint(), candidate.encode(),
                materialized.encode(), payload);
        return new Materialization(materialized, created, payload, receipt);
    }

    /** Locks and reauthenticates every stored row before an already-resolved assignment replays. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Materialization replayJoiningTransaction(WorldCanonicalLootAssignment assignment,
            CanonicalLootStoredResolution stored, byte[] payload, String receipt) {
        Objects.requireNonNull(assignment, "canonical LOOT assignment");
        Objects.requireNonNull(stored, "stored canonical LOOT result");
        var late = assignment.verifiedLateOutcome().orElse(null);
        if (payload == null && receipt == null) {
            if (late != null && !MessageDigest.isEqual(stored.encode(), late.resolution())) {
                throw new IllegalStateException("late replay differs from authenticated no-map outcome");
            }
            if (!stored.pendingMapReferences().isEmpty()) {
                throw new IllegalStateException("resolved LOOT retained a pending map identity");
            }
            return new Materialization(stored, List.of(), null, null);
        }
        if (payload == null || !isSha256(receipt)) {
            throw new IllegalStateException("stored map materialization identity is incomplete");
        }
        List<Entry> entries = decode(payload);
        boolean seaLevel = Arrays.equals(Arrays.copyOf(payload, MAGIC.length), MAGIC_V2);
        NeutralFinalChunk source = late == null ? sourceFor(assignment) : null;
        var context = assignment.getEffectiveLocatedProductionContext();
        List<WorldMapData> adopted = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            TargetMap target = targetFor(context, entry.destination());
            if (target == null) {
                throw new IllegalStateException(
                        "stored map has no persisted authenticated target");
            }
            var found = target.found();
            if (found == null
                    || !entry.targetReceipt().equals(target.targetReceipt())
                    || found.savedCenterX() != entry.centerX()
                    || found.savedCenterZ() != entry.centerZ()
                    || target.binding().scale() != entry.scale()
                    || !entry.previewSha256().equals(found.previewSha256())) {
                throw new IllegalStateException("stored map target receipt no longer authenticates");
            }
            byte[] colors = late == null
                    ? source.renderVerifiedMapPreview(assignment.getWorldSeed(), target, seaLevel)
                    : late.preview(target.targetReceipt());
            WorldMapData row = maps.lockMapJoiningTransaction(
                    assignment.getWorldId(), entry.mapId());
            requireExactRow(row, assignment.getWorldId(), entry, colors);
            requireStoredMapId(stored, entry);
            adopted.add(row);
        }
        CanonicalLootStoredResolution pending = restorePending(stored, entries);
        if (late != null && !MessageDigest.isEqual(pending.encode(), late.resolution())) {
            throw new IllegalStateException("late replay differs from authenticated pending-map outcome");
        }
        String expected = receipt(assignment.getDefinitionFingerprint(), pending.encode(),
                stored.encode(), payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                receipt.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("stored map materialization receipt mismatch");
        }
        return new Materialization(stored, adopted, payload, receipt);
    }

    private static List<Entry> entries(
            List<CanonicalLootStoredResolution.PendingMapReference> pending,
            List<TargetMap> found, List<WorldMapData> maps) {
        List<Entry> entries = new ArrayList<>(pending.size());
        for (int index = 0; index < pending.size(); index++) {
            var reference = pending.get(index);
            TargetMap target = found.get(index);
            WorldMapData map = maps.get(index);
            entries.add(new Entry(reference.slotIndex(), reference.componentKey(),
                    reference.destination(), reference.targetReceipt(), map.getMapId(),
                    target.found().savedCenterX(), target.found().savedCenterZ(), target.binding().scale(),
                    target.found().previewSha256()));
        }
        return List.copyOf(entries);
    }

    private static void requireFreshBatch(long worldId,
            List<WorldMapPersistenceService.NewMapSpec> specs, List<WorldMapData> created) {
        if (created == null || created.size() != specs.size()) {
            throw new IllegalStateException("durable map allocator returned the wrong batch shape");
        }
        int priorId = -1;
        for (int index = 0; index < created.size(); index++) {
            WorldMapData row = created.get(index);
            WorldMapPersistenceService.NewMapSpec spec = specs.get(index);
            if (row == null || row.getWorldId() != worldId || row.getMapId() <= 0
                    || (priorId > 0 && row.getMapId() != priorId + 1)
                    || row.getCenterX() != spec.centerX() || row.getCenterZ() != spec.centerZ()
                    || row.getScale() != spec.scale() || row.isLocked() || row.getRevision() != 0
                    || !MessageDigest.isEqual(row.getColors(), spec.colors())
                    || !java.util.Objects.equals(row.getTargetMarker(), spec.targetMarker())) {
                throw new IllegalStateException(
                        "durable map allocator returned a foreign or noncanonical row");
            }
            priorId = row.getMapId();
        }
    }

    private static void requireExactRow(WorldMapData row, long worldId,
            Entry entry, byte[] colors) {
        if (row == null || row.getWorldId() != worldId || row.getMapId() != entry.mapId()
                || row.getCenterX() != entry.centerX() || row.getCenterZ() != entry.centerZ()
                || row.getScale() != entry.scale() || row.isLocked()
                || row.getRevision() == 0
                        && !MessageDigest.isEqual(row.getColors(), colors)) {
            throw new IllegalStateException("durable map row differs from materialization receipt");
        }
    }

    private static void requireStoredMapId(CanonicalLootStoredResolution stored, Entry entry) {
        if (entry.slotIndex() < 0 || entry.slotIndex() >= stored.slots().size()) {
            throw new IllegalStateException("materialized map slot is outside stored LOOT");
        }
        CanonicalLootStoredResolution.Slot slot = stored.slots().get(entry.slotIndex());
        CanonicalLootStoredResolution.Component component = slot == null
                ? null : slot.components().get(entry.componentKey());
        if (component == null || !"MAP_ID".equals(component.kind())
                || component.mapId() == null || component.mapId() != entry.mapId()) {
            throw new IllegalStateException("stored LOOT map ID differs from durable map row");
        }
    }

    private static CanonicalLootStoredResolution restorePending(
            CanonicalLootStoredResolution stored, List<Entry> entries) {
        List<CanonicalLootStoredResolution.PendingMapReference> references = entries.stream()
                .map(entry -> new CanonicalLootStoredResolution.PendingMapReference(
                        entry.slotIndex(), entry.componentKey(), entry.destination(),
                        entry.targetReceipt()))
                .toList();
        return stored.restorePendingMapIds(references);
    }

    private static byte[] encode(List<Entry> entries, boolean seaLevel) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(seaLevel ? MAGIC_V2 : MAGIC);
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeInt(entry.slotIndex());
                writeText(out, entry.componentKey()); writeText(out, entry.destination());
                writeText(out, entry.targetReceipt()); out.writeInt(entry.mapId());
                out.writeInt(entry.centerX()); out.writeInt(entry.centerZ());
                out.writeInt(entry.scale()); writeText(out, entry.previewSha256());
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static List<Entry> decode(byte[] payload) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            byte[] magic = in.readNBytes(MAGIC.length);
            boolean seaLevel = MessageDigest.isEqual(magic, MAGIC_V2);
            if (!seaLevel && !MessageDigest.isEqual(magic, MAGIC)) throw invalid();
            int size = in.readInt();
            if (size <= 0 || size > 27) throw invalid();
            List<Entry> entries = new ArrayList<>(size);
            int priorSlot = -1;
            int priorMapId = -1;
            java.util.Set<String> componentCells = new HashSet<>();
            for (int index = 0; index < size; index++) {
                Entry entry = new Entry(in.readInt(), readText(in), readText(in), readText(in),
                        in.readInt(), in.readInt(), in.readInt(), in.readInt(), readText(in));
                String componentCell = entry.slotIndex() + "\0" + entry.componentKey();
                if (entry.slotIndex() < priorSlot || entry.mapId() <= 0
                        || (priorMapId > 0 && entry.mapId() != priorMapId + 1)
                        || entry.scale() < 0 || entry.scale() > 4
                        || !componentCells.add(componentCell)
                        || !isSha256(entry.targetReceipt()) || !isSha256(entry.previewSha256())) {
                    throw invalid();
                }
                priorSlot = entry.slotIndex();
                priorMapId = entry.mapId();
                entries.add(entry);
            }
            if (in.read() != -1) throw invalid();
            if (!Arrays.equals(payload, encode(entries, seaLevel))) throw invalid();
            return List.copyOf(entries);
        } catch (IOException | RuntimeException malformed) {
            throw new IllegalStateException("malformed map materialization payload", malformed);
        }
    }

    private static String receipt(String definition, byte[] pending, byte[] materialized,
            byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(RECEIPT_DOMAIN);
            digest.update(definition.getBytes(StandardCharsets.US_ASCII));
            digest.update(pending); digest.update(materialized); digest.update(payload);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void writeText(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 65_535) throw invalid();
        out.writeShort(bytes.length); out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int size = in.readUnsignedShort();
        if (size == 0) throw invalid();
        byte[] bytes = in.readNBytes(size);
        if (bytes.length != size) throw invalid();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}")
                && !value.equals("0".repeat(64));
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid map materialization payload");
    }
}
