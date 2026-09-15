package com.gameexpert.ground.service;

/** Result of an optimistic, exactly-once ground transaction. */
public enum GroundMutationOutcome { COMMITTED, IDEMPOTENT, STALE }
