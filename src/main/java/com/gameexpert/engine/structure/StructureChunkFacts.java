package com.gameexpert.engine.structure;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.terrain.Mc263BaseHeightSampler;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrierOrigin;

/** Ordered structure facts projected directly from the canonical product carrier. */
public final class StructureChunkFacts {
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    public record Fact(String structureId, int originChunkX, int originChunkZ, Box boundingBox) {}

    public record Facts(List<Fact> starts, List<Fact> references, List<String> unsupported) {
        public Facts {
            starts = List.copyOf(starts);
            references = List.copyOf(references);
            unsupported = List.copyOf(unsupported);
        }
    }

    private StructureChunkFacts() {}

    public static Facts forChunk(int seed, int chunkX, int chunkZ) {
        Mc263StructureCarrier carrier = Mc263StructureCarrierOrigin.assemble(seed, chunkX, chunkZ,
                Mc263BaseHeightSampler.overworld(seed)).carrier();
        ArrayList<Fact> starts = new ArrayList<>();
        for (ChunkStarts chunk : carrier.startChunks()) {
            if (chunk.chunkX() != chunkX || chunk.chunkZ() != chunkZ) continue;
            for (StartEntry entry : chunk.orderedStarts()) {
                if (entry.body() instanceof ValidStart valid) {
                    starts.add(fact(entry.structureId(), valid));
                }
            }
            break;
        }

        ArrayList<Fact> references = new ArrayList<>();
        ChunkReferences chunkReferences = carrier.referenceChunk(chunkX, chunkZ).orElseThrow(
                () -> new IllegalStateException("canonical carrier lacks target references"));
        for (Mc263StructureCarrier.ReferenceSet set : chunkReferences.orderedSets()) {
            for (ValidStart start : carrier.resolveStarts(chunkReferences, set.structureId())) {
                references.add(fact(set.structureId(), start));
            }
        }
        return new Facts(starts, references, List.of());
    }

    private static Fact fact(String structureId, ValidStart start) {
        Mc263StructureCarrier.BoundingBox box = start.adjustedBoundingBox();
        return new Fact(structureId, start.originChunkX(), start.originChunkZ(),
                new Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
    }
}
