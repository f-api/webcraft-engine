package com.gameexpert.frog.persistence.dto;

import java.util.List;

/** Complete durable frog-colony state needed while activating one world. */
public final class FrogColonyHydration {
    private final List<FrogColonyCooldown> cooldowns;
    private final List<FrogConversionIntent> pendingConversions;

    public FrogColonyHydration(List<FrogColonyCooldown> cooldowns,
            List<FrogConversionIntent> pendingConversions) {
        this.cooldowns = List.copyOf(cooldowns);
        this.pendingConversions = List.copyOf(pendingConversions);
    }

    public List<FrogColonyCooldown> getCooldowns() { return cooldowns; }
    public List<FrogConversionIntent> getPendingConversions() { return pendingConversions; }
}
