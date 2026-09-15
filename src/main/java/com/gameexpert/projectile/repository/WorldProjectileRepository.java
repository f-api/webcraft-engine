package com.gameexpert.projectile.repository;

import com.gameexpert.projectile.entity.WorldProjectile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldProjectileRepository extends JpaRepository<WorldProjectile, Long> {
    List<WorldProjectile> findAllByWorldId(Long worldId);
    void deleteAllByWorldId(Long worldId);
}
