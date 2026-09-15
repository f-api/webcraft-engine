package com.gameexpert.terrain.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CanonicalWorldgenChunkRepository
        extends JpaRepository<CanonicalWorldgenChunk, Long> {
    List<CanonicalWorldgenChunk> findAllByWorldIdOrderByChunkXAscChunkZAsc(long worldId);
    interface StructureRow {
        String getWorldIdentity(); int getChunkX(); int getChunkZ(); byte[] getStructureCarrier();
    }
    @Query("select c.worldIdentity as worldIdentity, c.chunkX as chunkX, c.chunkZ as chunkZ, c.structureCarrier as structureCarrier from CanonicalWorldgenChunk c where c.worldId = :worldId order by c.chunkX, c.chunkZ")
    List<StructureRow> findStructureRows(@Param("worldId") long worldId);

    Optional<CanonicalWorldgenChunk> findByWorldIdAndChunkXAndChunkZ(long worldId, int chunkX,
            int chunkZ);
    /**
     * 존재 여부만 묻는다. 엔티티를 읽으면 longblob 캐리어 3개를 전부 실어 오므로, 커밋 여부만
     * 필요한 생산 워커 경로는 이 조회를 쓴다.
     */
    boolean existsByWorldIdAndChunkXAndChunkZ(long worldId, int chunkX, int chunkZ);
    @Modifying
    @Query(value = """
            insert into canonical_worldgen_chunks
                (world_id, world_identity, chunk_x, chunk_z, abi_version, carrier_schema,
                 final_carrier, structure_carrier, mutable_piece_successor, commit_fingerprint,
                 lane_claim_mask, lane_ack_mask, lane_rejected_mask, row_revision)
            values
                (:worldId, :worldIdentity, :chunkX, :chunkZ, :abiVersion, :carrierSchema,
                 :finalCarrier, :structureCarrier, :mutablePieceSuccessor, :fingerprint, 0, 0, 0, 0)
            on duplicate key update id = id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("worldId") long worldId,
            @Param("worldIdentity") String worldIdentity, @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ, @Param("abiVersion") int abiVersion,
            @Param("carrierSchema") int carrierSchema, @Param("finalCarrier") byte[] finalCarrier,
            @Param("structureCarrier") byte[] structureCarrier,
            @Param("mutablePieceSuccessor") byte[] mutablePieceSuccessor,
            @Param("fingerprint") byte[] fingerprint);
    @Modifying
    @Query(value = """
            insert into canonical_worldgen_chunks
                (world_id, world_identity, chunk_x, chunk_z, abi_version, carrier_schema,
                 final_carrier, structure_carrier, mutable_piece_successor, commit_fingerprint,
                 group_fingerprint, group_min_chunk_x, group_min_chunk_z, group_max_chunk_x,
                 group_max_chunk_z, group_ordinal, group_size,
                 lane_claim_mask, lane_ack_mask, lane_rejected_mask, row_revision)
            values
                (:worldId, :worldIdentity, :chunkX, :chunkZ, :abiVersion, :carrierSchema,
                 :finalCarrier, :structureCarrier, :mutablePieceSuccessor, :fingerprint,
                 :groupFingerprint, :groupMinChunkX, :groupMinChunkZ, :groupMaxChunkX,
                 :groupMaxChunkZ, :groupOrdinal, :groupSize, 0, 0, 0, 0)
            on duplicate key update id = id
            """, nativeQuery = true)
    int insertGroupIfAbsent(@Param("worldId") long worldId,
            @Param("worldIdentity") String worldIdentity, @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ, @Param("abiVersion") int abiVersion,
            @Param("carrierSchema") int carrierSchema, @Param("finalCarrier") byte[] finalCarrier,
            @Param("structureCarrier") byte[] structureCarrier,
            @Param("mutablePieceSuccessor") byte[] mutablePieceSuccessor,
            @Param("fingerprint") byte[] fingerprint,
            @Param("groupFingerprint") byte[] groupFingerprint,
            @Param("groupMinChunkX") int groupMinChunkX,
            @Param("groupMinChunkZ") int groupMinChunkZ,
            @Param("groupMaxChunkX") int groupMaxChunkX,
            @Param("groupMaxChunkZ") int groupMaxChunkZ,
            @Param("groupOrdinal") int groupOrdinal, @Param("groupSize") int groupSize);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select chunk from CanonicalWorldgenChunk chunk where chunk.worldId = :worldId "
            + "and chunk.chunkX = :chunkX and chunk.chunkZ = :chunkZ")
    Optional<CanonicalWorldgenChunk> findForUpdateByWorldIdAndChunkXAndChunkZ(
            @Param("worldId") long worldId, @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ);
    void deleteByWorldId(long worldId);
}
