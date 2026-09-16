package com.gameexpert.engine.persistence.finalcarrier.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldLootReferencePageRepository extends JpaRepository<WorldLootReferencePage, Long> {
    interface PayloadRow {
        byte[] getPayload();
    }
    @Query("select p.payload as payload from WorldLootReferencePage p where p.worldId = :worldId and p.baselineId = :baseline and p.snapshotIdentity = :identity and p.payloadKind = :kind and p.pageNumber = :number")
    Optional<PayloadRow> findPayloadRow(@Param("worldId") long worldId, @Param("baseline") String baseline,
            @Param("identity") String identity, @Param("kind") String kind, @Param("number") int number);
    default Optional<byte[]> payload(long worldId, String baseline, String identity,
            String kind, int number) {
        return findPayloadRow(worldId, baseline, identity, kind, number).map(PayloadRow::getPayload);
    }
    @Modifying
    @Query(value = "insert into world_loot_reference_pages (world_id, baseline_id, snapshot_identity, payload_kind, page_number, payload) values (:worldId, :baseline, :identity, :kind, :number, :payload)", nativeQuery = true)
    int insertPage(@Param("worldId") long worldId, @Param("baseline") String baseline,
            @Param("identity") String identity, @Param("kind") String kind,
            @Param("number") int number, @Param("payload") byte[] payload);
    @Modifying
    @Query("delete from WorldLootReferencePage p where p.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
