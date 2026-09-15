package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Locked access to mutable generated non-mob state and retained tombstones. */
public interface WorldGeneratedStructureEntityStateRepository
        extends JpaRepository<WorldGeneratedStructureEntityState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from WorldGeneratedStructureEntityState row where row.worldId = :worldId "
            + "and row.authoritativeEntityId = :entityId")
    Optional<WorldGeneratedStructureEntityState> findRawLockedByWorldIdAndAuthoritativeEntityId(
            @Param("worldId") long worldId, @Param("entityId") long authoritativeEntityId);

    default Optional<WorldGeneratedStructureEntityState>
            findLockedByWorldIdAndAuthoritativeEntityId(long worldId, long authoritativeEntityId) {
        return findRawLockedByWorldIdAndAuthoritativeEntityId(worldId, authoritativeEntityId)
                .map(WorldGeneratedStructureEntityState::requireValidDurableState);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from WorldGeneratedStructureEntityState row "
            + "where row.laneInstallationIdentity = :identity order by row.encounterOrdinal")
    List<WorldGeneratedStructureEntityState>
            findRawLockedByLaneInstallationIdentityOrderByEncounterOrdinal(
                    @Param("identity") String laneInstallationIdentity);

    default List<WorldGeneratedStructureEntityState>
            findLockedByLaneInstallationIdentityOrderByEncounterOrdinal(
                    String laneInstallationIdentity) {
        return findRawLockedByLaneInstallationIdentityOrderByEncounterOrdinal(
                laneInstallationIdentity).stream()
                .map(WorldGeneratedStructureEntityState::requireValidDurableState).toList();
    }

    @Query("select row from WorldGeneratedStructureEntityState row where row.worldId = :worldId "
            + "order by row.authoritativeEntityId")
    List<WorldGeneratedStructureEntityState> findRawByWorldIdOrderByAuthoritativeEntityId(
            @Param("worldId") long worldId);

    default List<WorldGeneratedStructureEntityState>
            findAllByWorldIdOrderByAuthoritativeEntityId(long worldId) {
        return findRawByWorldIdOrderByAuthoritativeEntityId(worldId).stream()
                .map(WorldGeneratedStructureEntityState::requireValidDurableState).toList();
    }

    @Query("select max(row.authoritativeEntityId) from WorldGeneratedStructureEntityState row "
            + "where row.worldId = :worldId")
    Long findMaximumAuthoritativeEntityIdByWorldId(@Param("worldId") long worldId);

    @Modifying
    @Query(value = "delete from world_generated_structure_entity_cargo where state_id in "
            + "(select id from world_generated_structure_entity_states where world_id = :worldId)",
            nativeQuery = true)
    int deleteCargoByWorldId(@Param("worldId") long worldId);

    @Modifying
    @Query("delete from WorldGeneratedStructureEntityState row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
