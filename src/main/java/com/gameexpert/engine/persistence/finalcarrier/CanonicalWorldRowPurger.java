package com.gameexpert.engine.persistence.finalcarrier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.gameexpert.engine.persistence.finalcarrier.archaeology.WorldArchaeologyBrushableRepository;
import com.gameexpert.engine.persistence.finalcarrier.bees.WorldCanonicalBeeInstallationRepository;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignmentRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityIdSequenceRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityStateRepository;

import com.gameexpert.engine.persistence.finalcarrier.reference.WorldStructureReferenceClaimRepository;

import com.gameexpert.engine.persistence.finalcarrier.reference.WorldLootReferenceSnapshotRepository;

import lombok.RequiredArgsConstructor;

/**
 * Deletes every canonical worldgen / final-carrier row a world owns.
 *
 * <p>World deletion used to clear only the gameplay aggregate, so {@code canonical_worldgen_chunks}
 * and the final-carrier tables kept their rows after {@code DELETE /worlds/{id}} returned 204. That
 * left the canonical tables growing without bound and, because the rows are keyed by world id
 * rather than by world identity, a re-created world could inherit another world's canonical
 * commits. The purge therefore runs inside the deleting transaction and covers the complete set of
 * world-scoped canonical tables rather than the two that were first observed leaking.</p>
 */
@Service
@RequiredArgsConstructor
public class CanonicalWorldRowPurger {

    /** Every world-scoped canonical table, so a new member cannot silently be left behind. */
    public static final List<String> CANONICAL_TABLES = List.of(
            "canonical_worldgen_chunks",
            "final_carrier_lane_mutations",
            "final_carrier_scheduled_ticks",
            "final_carrier_consumed_ticks",
            "world_archaeology_brushables",
            "world_canonical_bee_installations",
            "world_canonical_loot_assignments",
            "world_generated_structure_entity_states",
            "world_generated_structure_entity_cargo",
            "world_structure_entities",
            "world_structure_entity_id_sequences",
            "world_structure_reference_claims",
            "world_loot_reference_snapshots",
            "world_loot_reference_pages");

    private final WorldLootReferenceSnapshotRepository lootReferenceSnapshots;
    private final com.gameexpert.engine.persistence.finalcarrier.reference.WorldLootReferencePageRepository lootReferencePages;
    private final CanonicalWorldgenChunkPurgeRepository canonicalChunks;
    private final FinalCarrierLaneMutationRepository laneMutations;
    private final FinalCarrierScheduledTickRepository scheduledTicks;
    private final FinalCarrierConsumedTickRepository consumedTicks;
    private final WorldArchaeologyBrushableRepository archaeologyBrushables;
    private final WorldCanonicalBeeInstallationRepository beeInstallations;
    private final WorldCanonicalLootAssignmentRepository lootAssignments;
    private final WorldGeneratedStructureEntityStateRepository generatedEntityStates;
    private final WorldStructureEntityRepository structureEntities;
    private final WorldStructureEntityIdSequenceRepository structureEntityIdSequences;

    private final WorldStructureReferenceClaimRepository structureReferenceClaims;

    /**
     * Removes the world's canonical rows.
     *
     * <p>Joins the caller's transaction so the canonical rows and the world row disappear
     * together; a rollback leaves both in place.</p>
     *
     * @return deleted row count per canonical table, in {@link #CANONICAL_TABLES} order
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Map<String, Integer> purgeWorld(long worldId) {
        // canonical_worldgen_chunks is the parent publication; the lane/tick rows reference the
        // same (world, chunk) coordinates, so they are cleared in the same unit of work.
        int laneCount = laneMutations.deleteByWorldId(worldId);
        int scheduledCount = scheduledTicks.deleteByWorldId(worldId);
        int consumedCount = consumedTicks.deleteByWorldId(worldId);
        int archaeologyCount = archaeologyBrushables.deleteByWorldId(worldId);
        int beeCount = beeInstallations.deleteByWorldId(worldId);
        int lootCount = lootAssignments.deleteByWorldId(worldId);
        int generatedCargo = generatedEntityStates.deleteCargoByWorldId(worldId);
        int generatedStates = generatedEntityStates.deleteByWorldId(worldId);
        int structureCount = structureEntities.deleteByWorldId(worldId);
        int sequenceCount = structureEntityIdSequences.deleteByWorldId(worldId);
        int referenceCount = structureReferenceClaims.deleteByWorldId(worldId);
        int lootPageCount = lootReferencePages.deleteByWorldId(worldId);
        int lootSnapshotCount = lootReferenceSnapshots.deleteByWorldId(worldId);
        int chunkCount = canonicalChunks.deleteByWorldId(worldId);

        // Report order is a stable public contract independent of dependency-safe delete order.
        Map<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("canonical_worldgen_chunks", chunkCount);
        deleted.put("final_carrier_lane_mutations", laneCount);
        deleted.put("final_carrier_scheduled_ticks", scheduledCount);
        deleted.put("final_carrier_consumed_ticks", consumedCount);
        deleted.put("world_archaeology_brushables", archaeologyCount);
        deleted.put("world_canonical_bee_installations", beeCount);
        deleted.put("world_canonical_loot_assignments", lootCount);
        deleted.put("world_generated_structure_entity_states", generatedStates);
        deleted.put("world_generated_structure_entity_cargo", generatedCargo);
        deleted.put("world_structure_entities", structureCount);
        deleted.put("world_structure_entity_id_sequences", sequenceCount);
        deleted.put("world_structure_reference_claims", referenceCount);
        deleted.put("world_loot_reference_snapshots", lootSnapshotCount);
        deleted.put("world_loot_reference_pages", lootPageCount);
        return deleted;
    }
}
