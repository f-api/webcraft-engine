package com.gameexpert.engine.persistence.finalcarrier.loot;

/**
 * Transaction-joining boundary that writes the fixed LOOT slots into the authoritative chest or
 * dispenser aggregate. Implementations must lock the aggregate and compare the complete mutation.
 */
@FunctionalInterface
public interface CanonicalLootAggregatePersistence {
    enum Outcome {
        COMMITTED,
        ALREADY_COMMITTED,
        REJECTED
    }

    Outcome installResolvedJoiningTransaction(CanonicalLootAggregateMutation mutation);
}
