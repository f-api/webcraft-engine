package com.gameexpert.world.repository;

import com.gameexpert.world.entity.WorldDimension;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldDimensionRepository extends JpaRepository<WorldDimension, Long> {
    @Query("select d from WorldDimension d join fetch d.root join fetch d.child where d.child.id = :id")
    Optional<WorldDimension> findByChildId(@Param("id") long id);
    @Query("select d from WorldDimension d join fetch d.child where d.root.id = :root and d.dimensionKey = :key")
    Optional<WorldDimension> findByRootIdAndDimensionKey(@Param("root") long root, @Param("key") String key);
    @Query("select d from WorldDimension d join fetch d.child where d.root.id = :root order by d.child.id")
    List<WorldDimension> findByRootId(@Param("root") long root);

    /** 접속 인원 집계에는 하위 월드의 ID만 필요합니다. 엔티티 전체를 로드하지 않습니다. */
    @Query("select d.child.id from WorldDimension d where d.root.id = :root order by d.child.id")
    List<Long> findChildIdsByRootId(@Param("root") long root);
}
