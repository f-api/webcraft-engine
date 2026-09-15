package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorCarrierBridge;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftOrderedAggregate;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant atomic successor boundary for exact mineshaft aggregate evidence.
 *
 * <p>All evidence, replacements and sidecars are validated against immutable inputs first. Only
 * then are a successor STR263C1 carrier and final chunk value constructed. This class is not wired
 * to the canonical producer; a complete mineshaft start generator remains the activation gate.
 */
public final class Mc263MineshaftSettlement {
    private static final int CHUNK_EDGE = 16;

    private Mc263MineshaftSettlement() { }

    public record Settlement(Mc263StructureCarrier structureCarrier,
            Mc263FinalChunkCodec.FinalChunk finalChunk) { }

    /**
     * FEATURES-only semantic settlement.  This validates and advances STR from the exact piece
     * execution and returns sidecar facts for one StructureBatch; it never constructs a final
     * carrier, so LDEC remains exclusively the final-assembly responsibility.
     */
    public record ProvisionalSettlement(Mc263StructureCarrier structureCarrier,
            List<Mc263FinalChunkSidecars.Spawner> spawners) { }

    public static ProvisionalSettlement prepareFeaturesSettlement(Mc263StructureCarrier carrier,
            ChunkReferences references,
            List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(evidence, "evidence");
        Map<String, ValidStart> resolved = new HashMap<>();
        ArrayList<ValidStart> order = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            if (isMineshaft(start.startKey())) {
                if (resolved.put(start.startKey(), start) != null) {
                    throw new IllegalArgumentException("duplicate resolved mineshaft start");
                }
                order.add(start);
            }
        }
        if (evidence.size() != order.size()) {
            throw new IllegalArgumentException("mineshaft start evidence set mismatch");
        }
        Map<PieceAddress, byte[]> replacements = new HashMap<>();
        ArrayList<Mc263FinalChunkSidecars.Spawner> spawners = new ArrayList<>();
        Set<String> starts = new HashSet<>();
        Set<PieceAddress> pieces = new HashSet<>();
        Set<Integer> spawnerCells = new HashSet<>();
        Set<EntityCell> entityCells = new HashSet<>();
        int startOrder = 0;
        for (var startEvidence : List.copyOf(evidence)) {
            ValidStart start = resolved.get(startEvidence.startKey());
            if (start == null || !starts.add(startEvidence.startKey())
                    || start != order.get(startOrder++)
                    || start.originChunkX() != startEvidence.originChunkX()
                    || start.originChunkZ() != startEvidence.originChunkZ()
                    || start.references() != startEvidence.references()) {
                throw new IllegalArgumentException("mineshaft start evidence/carrier mismatch");
            }
            int previousPiece = -1;
            for (var pieceEvidence : startEvidence.orderedPieces()) {
                int index = pieceEvidence.pieceIndex();
                if (index <= previousPiece || index >= start.orderedPieces().size()
                        || !pieces.add(new PieceAddress(start.startKey(), index))) {
                    throw new IllegalArgumentException("duplicate mineshaft piece evidence");
                }
                previousPiece = index;
                Piece piece = start.orderedPieces().get(index);
                requireKind(piece, pieceEvidence.kind());
                requireExecutionKind(pieceEvidence);
                if (!(pieceEvidence.execution()
                        instanceof Mc263MineshaftOrderedAggregate.CorridorEvidence corridor)) continue;
                var facts = validateCorridorReplacement(piece,
                        new PieceAddress(start.startKey(), index), corridor, replacements);
                for (var effect : corridor.execution().spawnerEffects()) {
                    if (!Mc263MineshaftCorridorPieceExecutor.CAVE_SPIDER.equals(effect.entityType())
                            || !facts.spiderCorridor()
                            || !corridor.execution().finalHasPlacedSpider()) {
                        throw new IllegalArgumentException("noncanonical corridor spawner effect");
                    }
                    requireInside(facts.boundingBox(), effect.anchor());
                    int packed = packed(references, effect.anchor().x(), effect.anchor().y(),
                            effect.anchor().z());
                    if (!spawnerCells.add(packed)) {
                        throw new IllegalArgumentException("duplicate corridor spawner effect");
                    }
                    spawners.add(new Mc263FinalChunkSidecars.Spawner(packed, effect.entityType()));
                }
                for (var effect : corridor.execution().chestMinecartEffects()) {
                    var entity = Mc263FinalChunkAssembler.chestMinecart(effect);
                    packed(references, effect.anchor().x(), effect.anchor().y(), effect.anchor().z());
                    requireInside(facts.boundingBox(), effect.anchor());
                    if (!entityCells.add(EntityCell.of(entity))) {
                        throw new IllegalArgumentException("duplicate corridor chest-minecart effect");
                    }
                }
            }
        }
        return new ProvisionalSettlement(rebuild(carrier, replacements).strictlyDecoded(),
                List.copyOf(spawners));
    }

    public static Settlement settle(Mc263StructureCarrier carrier, ChunkReferences references,
            List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence,
            Mc263FinalChunkCodec.FinalChunk chunk) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(chunk, "chunk");
        if (references.chunkX() != chunk.chunkX() || references.chunkZ() != chunk.chunkZ()) {
            throw new IllegalArgumentException("settlement chunk/reference mismatch");
        }
        Mc263FinalChunkCodec.validate(chunk);

        Map<String, ValidStart> resolved = new HashMap<>();
        ArrayList<ValidStart> resolvedOrder = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            if (isMineshaft(start.startKey())) {
                if (resolved.put(start.startKey(), start) != null) {
                    throw new IllegalArgumentException("duplicate resolved mineshaft start");
                }
                resolvedOrder.add(start);
            }
        }
        if (evidence.size() != resolvedOrder.size()) {
            throw new IllegalArgumentException("mineshaft start evidence set mismatch");
        }

        Map<PieceAddress, byte[]> replacements = new HashMap<>();
        ArrayList<Mc263FinalChunkSidecars.Spawner> addedSpawners = new ArrayList<>();
        ArrayList<Mc263FinalChunkSidecars.ChestMinecart> addedEntities = new ArrayList<>();
        Set<String> seenStarts = new HashSet<>();
        Set<PieceAddress> seenPieces = new HashSet<>();
        Set<Integer> incomingSpawners = new HashSet<>();
        Set<EntityCell> incomingEntities = new HashSet<>();

        int startOrder = 0;
        for (var startEvidence : List.copyOf(evidence)) {
            ValidStart start = resolved.get(startEvidence.startKey());
            if (start == null || !seenStarts.add(startEvidence.startKey())
                    || start != resolvedOrder.get(startOrder++)
                    || start.originChunkX() != startEvidence.originChunkX()
                    || start.originChunkZ() != startEvidence.originChunkZ()
                    || start.references() != startEvidence.references()) {
                throw new IllegalArgumentException("mineshaft start evidence/carrier mismatch");
            }
            int previousPiece = -1;
            for (var pieceEvidence : startEvidence.orderedPieces()) {
                int index = pieceEvidence.pieceIndex();
                if (index <= previousPiece || index >= start.orderedPieces().size()) {
                    throw new IllegalArgumentException("mineshaft evidence piece index outside start");
                }
                previousPiece = index;
                PieceAddress address = new PieceAddress(start.startKey(), index);
                if (!seenPieces.add(address)) {
                    throw new IllegalArgumentException("duplicate mineshaft piece evidence");
                }
                Piece piece = start.orderedPieces().get(index);
                requireKind(piece, pieceEvidence.kind());
                requireExecutionKind(pieceEvidence);
                if (!(pieceEvidence.execution()
                        instanceof Mc263MineshaftOrderedAggregate.CorridorEvidence corridor)) {
                    continue;
                }
                var facts = validateCorridorReplacement(piece, address, corridor, replacements);
                for (var effect : corridor.execution().spawnerEffects()) {
                    if (!Mc263MineshaftCorridorPieceExecutor.CAVE_SPIDER.equals(
                            effect.entityType())) {
                        throw new IllegalArgumentException("noncanonical corridor spawner effect");
                    }
                    int packed = packed(chunk, effect.anchor().x(), effect.anchor().y(),
                            effect.anchor().z());
                    requireInside(facts.boundingBox(), effect.anchor());
                    if (!facts.spiderCorridor()
                            || !corridor.execution().finalHasPlacedSpider()) {
                        throw new IllegalArgumentException("spawner effect from non-spider corridor");
                    }
                    if (!incomingSpawners.add(packed)) {
                        throw new IllegalArgumentException("duplicate corridor spawner effect");
                    }
                    addedSpawners.add(new Mc263FinalChunkSidecars.Spawner(
                            packed, effect.entityType()));
                }
                for (var effect : corridor.execution().chestMinecartEffects()) {
                    Mc263FinalChunkSidecars.ChestMinecart entity =
                            Mc263FinalChunkAssembler.chestMinecart(effect);
                    packed(chunk, effect.anchor().x(), effect.anchor().y(), effect.anchor().z());
                    requireInside(facts.boundingBox(), effect.anchor());
                    EntityCell cell = EntityCell.of(entity);
                    if (!incomingEntities.add(cell)) {
                        throw new IllegalArgumentException("duplicate corridor chest-minecart effect");
                    }
                    addedEntities.add(entity);
                }
            }
        }

        requireAuthenticatedEntityRows(chunk.sidecars(), addedEntities);

        Mc263FinalChunkSidecars sidecars = mergedSidecars(
                chunk.sidecars(), addedSpawners, addedEntities);
        Mc263FinalChunkCodec.FinalChunk successorChunk = new Mc263FinalChunkCodec.FinalChunk(
                chunk.chunkX(), chunk.chunkZ(), chunk.blockIds(), chunk.stateOverrides(),
                chunk.worldSurfaceWg(), chunk.oceanFloorWg(), chunk.motionBlocking(), sidecars);
        // Global duplicate, position and block-capability validation occurs before either result.
        // The encoded bytes were never used, so the same checks run without materialising them.
        Mc263FinalChunkCodec.validate(successorChunk);

        Mc263StructureCarrier successorCarrier = rebuild(carrier, replacements);
        // Constructor and a full canonical receipt decode validate every retained/replaced fact.
        successorCarrier = successorCarrier.strictlyDecoded();
        return new Settlement(successorCarrier, successorChunk);
    }

    private static Mc263MineshaftCorridorPieceExecutor.PieceFacts validateCorridorReplacement(
            Piece piece, PieceAddress address,
            Mc263MineshaftOrderedAggregate.CorridorEvidence evidence,
            Map<PieceAddress, byte[]> replacements) {
        byte[] current = piece.persistedPayload().binaryNbtCompound();
        var facts = Mc263MineshaftCorridorCarrierBridge.decodeFacts(piece);
        boolean target = evidence.execution().finalHasPlacedSpider();
        if (!evidence.replacementRequired()) {
            if (evidence.replacementNbtBytes() != 0 || evidence.replacementNbtSha256() != null
                    || facts.hasPlacedSpider() != target) {
                throw new IllegalArgumentException("corridor nonreplacement payload mismatch");
            }
            return facts;
        }
        byte[] successor = Mc263MineshaftCorridorCarrierBridge.officialNbt(facts, target);
        if (successor.length != evidence.replacementNbtBytes()
                || !sha256(successor).equals(evidence.replacementNbtSha256())) {
            // Idempotent replay starts from the already-settled successor.
            if (current.length != evidence.replacementNbtBytes()
                    || !sha256(current).equals(evidence.replacementNbtSha256())
                    || facts.hasPlacedSpider() != target) {
                throw new IllegalArgumentException("corridor replacement payload mismatch");
            }
            successor = current;
        }
        replacements.put(address, successor);
        return facts;
    }

    private static Mc263FinalChunkSidecars mergedSidecars(Mc263FinalChunkSidecars source,
            List<Mc263FinalChunkSidecars.Spawner> spawners,
            List<Mc263FinalChunkSidecars.ChestMinecart> entities) {
        ArrayList<Mc263FinalChunkSidecars.Spawner> mergedSpawners =
                new ArrayList<>(source.spawners());
        Map<Integer, String> existingSpawners = new HashMap<>();
        for (var value : source.spawners()) existingSpawners.put(value.packed(), value.entityType());
        for (var value : spawners) {
            String existing = existingSpawners.putIfAbsent(value.packed(), value.entityType());
            if (existing == null) mergedSpawners.add(value);
            else if (!existing.equals(value.entityType())) {
                throw new IllegalArgumentException("corridor spawner conflicts with existing sidecar");
            }
        }

        ArrayList<Mc263FinalChunkSidecars.StructureEntity> mergedEntities =
                new ArrayList<>(source.entities());
        Map<EntityCell, Mc263FinalChunkSidecars.ChestMinecart> existingEntities = new HashMap<>();
        for (var value : source.chestMinecarts()) existingEntities.put(EntityCell.of(value), value);
        for (var value : entities) {
            var existing = existingEntities.putIfAbsent(EntityCell.of(value), value);
            if (existing == null) mergedEntities.add(
                    Mc263FinalChunkSidecars.StructureEntity.fromChestMinecart(value));
            else if (!existing.equals(value)) {
                throw new IllegalArgumentException(
                        "corridor chest minecart conflicts with existing sidecar");
            }
        }
        return new Mc263FinalChunkSidecars(source.blockTicks(), source.fluidTicks(), source.loot(),
                mergedSpawners, source.owners(), source.archaeology(), source.bees(),
                source.blockEntities(), mergedEntities, source.containerLootDeclarations());
    }

    private static void requireAuthenticatedEntityRows(Mc263FinalChunkSidecars source,
            List<Mc263FinalChunkSidecars.ChestMinecart> additions) {
        for (var value : additions) {
            Mc263FinalChunkSidecars.StructureEntity row =
                    Mc263FinalChunkSidecars.StructureEntity.fromChestMinecart(value);
            if (!source.entities().contains(row)) {
                throw new IllegalStateException(
                        "mineshaft LDEC upstream handoff missing: Mc263FinalChunkAssembler "
                                + "must provide the authenticated ENTS row and declaration "
                                + "before settlement");
            }
        }
    }

    private static Mc263StructureCarrier rebuild(Mc263StructureCarrier carrier,
            Map<PieceAddress, byte[]> replacements) {
        // AGENTS 10l: a start whose pieces this settlement did not replace is retained as the
        // very object the carrier already holds. Rebuilding it into an equal copy produced the
        // same bytes but discarded the record's strict-decode proof, so every chunk-start record
        // in the window was re-encoded, re-decoded and re-encoded again on every settled chunk.
        // withStarts already returns the record itself when its starts are element-identical, so
        // an untouched chunk stays proven and strictlyDecoded() short-circuits on it.
        ArrayList<ChunkStarts> chunks = new ArrayList<>();
        for (ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<StartEntry> starts = new ArrayList<>();
            for (StartEntry entry : chunk.orderedStarts()) {
                if (!(entry.body() instanceof ValidStart valid)) {
                    starts.add(entry); continue;
                }
                ArrayList<Piece> pieces = new ArrayList<>();
                boolean replaced = false;
                for (int index = 0; index < valid.orderedPieces().size(); index++) {
                    Piece piece = valid.orderedPieces().get(index);
                    byte[] payload = replacements.get(new PieceAddress(valid.startKey(), index));
                    if (payload == null) {
                        pieces.add(piece);
                        continue;
                    }
                    replaced = true;
                    pieces.add(new Piece(piece.pieceType(),
                            piece.boundingBox(), piece.poolElement(), piece.projection(),
                            piece.groundLevelDelta(), piece.junctions(), new PiecePayload(payload)));
                }
                if (!replaced) {
                    starts.add(entry); continue;
                }
                starts.add(new StartEntry(entry.structureId(), new ValidStart(valid.startKey(),
                        valid.originChunkX(), valid.originChunkZ(), valid.references(),
                        valid.adjustedBoundingBox(), pieces)));
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }

    private static int packed(Mc263FinalChunkCodec.FinalChunk chunk, int x, int y, int z) {
        if (Math.floorDiv(x, CHUNK_EDGE) != chunk.chunkX()
                || Math.floorDiv(z, CHUNK_EDGE) != chunk.chunkZ()
                || y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalArgumentException("corridor effect outside settlement chunk");
        }
        return Blocks.blockIndex(Math.floorMod(x, CHUNK_EDGE), y,
                Math.floorMod(z, CHUNK_EDGE));
    }

    private static int packed(ChunkReferences references, int x, int y, int z) {
        if (Math.floorDiv(x, CHUNK_EDGE) != references.chunkX()
                || Math.floorDiv(z, CHUNK_EDGE) != references.chunkZ()
                || y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalArgumentException("corridor effect outside settlement chunk");
        }
        return Blocks.blockIndex(Math.floorMod(x, CHUNK_EDGE), y, Math.floorMod(z, CHUNK_EDGE));
    }

    private static void requireInside(Mc263MineshaftCorridorPieceExecutor.BoundingBox box,
            Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        if (position.x() < box.minX() || position.x() > box.maxX()
                || position.y() < box.minY() || position.y() > box.maxY()
                || position.z() < box.minZ() || position.z() > box.maxZ()) {
            throw new IllegalArgumentException("corridor effect outside source piece");
        }
    }

    private static void requireKind(Piece piece,
            Mc263MineshaftOrderedAggregate.PieceKind kind) {
        String expected = switch (kind) {
            case ROOM -> "minecraft:msroom";
            case CORRIDOR -> Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE;
            case CROSSING -> "minecraft:mscrossing";
            case STAIRS -> "minecraft:msstairs";
        };
        if (!expected.equals(piece.pieceType())) {
            throw new IllegalArgumentException("mineshaft piece evidence kind mismatch");
        }
    }

    private static void requireExecutionKind(
            Mc263MineshaftOrderedAggregate.PieceEvidence evidence) {
        boolean matches = switch (evidence.kind()) {
            case ROOM -> evidence.execution()
                    instanceof Mc263MineshaftOrderedAggregate.RoomEvidence;
            case CORRIDOR -> evidence.execution()
                    instanceof Mc263MineshaftOrderedAggregate.CorridorEvidence;
            case CROSSING -> evidence.execution()
                    instanceof Mc263MineshaftOrderedAggregate.CrossingEvidence;
            case STAIRS -> evidence.execution()
                    instanceof Mc263MineshaftOrderedAggregate.StairsEvidence;
        };
        if (!matches) throw new IllegalArgumentException("mineshaft execution evidence kind mismatch");
    }

    private static boolean isMineshaft(String startKey) {
        return startKey.startsWith(Mc263MineshaftOrderedAggregate.NORMAL_STRUCTURE + "@")
                || startKey.startsWith(Mc263MineshaftOrderedAggregate.MESA_STRUCTURE + "@");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record PieceAddress(String startKey, int pieceIndex) { }
    private record EntityCell(long x, long y, long z) {
        static EntityCell of(Mc263FinalChunkSidecars.ChestMinecart value) {
            return new EntityCell(Double.doubleToRawLongBits(value.x()),
                    Double.doubleToRawLongBits(value.y()), Double.doubleToRawLongBits(value.z()));
        }
    }
}
