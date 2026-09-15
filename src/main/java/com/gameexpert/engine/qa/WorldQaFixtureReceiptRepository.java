package com.gameexpert.engine.qa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldQaFixtureReceiptRepository
        extends JpaRepository<WorldQaFixtureReceipt, Long> {
    Optional<WorldQaFixtureReceipt> findByWorldIdAndFixtureId(Long worldId, String fixtureId);
    void deleteAllByWorldId(Long worldId);
}
