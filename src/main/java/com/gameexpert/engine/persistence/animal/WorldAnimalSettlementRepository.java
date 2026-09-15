package com.gameexpert.engine.persistence.animal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldAnimalSettlementRepository extends JpaRepository<WorldAnimalSettlement, Long> {
    Optional<WorldAnimalSettlement> findByWorldIdAndSettlementKey(Long worldId, String settlementKey);
    List<WorldAnimalSettlement> findAllByWorldIdAndCompletedFalseOrderByIdAsc(Long worldId);
    List<WorldAnimalSettlement> findAllByWorldIdAndKindOrderByIdAsc(Long worldId, String kind);
    @org.springframework.data.jpa.repository.Query("select max(r.token) from WorldAnimalSettlement r where r.worldId=:worldId and r.kind=:kind")
    Optional<Long> maximumToken(@org.springframework.data.repository.query.Param("worldId") long worldId,
            @org.springframework.data.repository.query.Param("kind") String kind);
    @org.springframework.data.jpa.repository.Query("select max(r.token) from WorldAnimalSettlement r where r.worldId=:worldId and r.kind=:kind and r.x between :minX and :maxX and r.z between :minZ and :maxZ")
    Optional<Long> maximumTokenInColumnBox(@org.springframework.data.repository.query.Param("worldId") long worldId,
            @org.springframework.data.repository.query.Param("kind") String kind,
            @org.springframework.data.repository.query.Param("minX") int minX,
            @org.springframework.data.repository.query.Param("maxX") int maxX,
            @org.springframework.data.repository.query.Param("minZ") int minZ,
            @org.springframework.data.repository.query.Param("maxZ") int maxZ);
    void deleteAllByWorldId(Long worldId);
}
