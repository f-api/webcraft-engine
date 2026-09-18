package com.gameexpert.engine.dragon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

final class DragonTerrainReadinessTest {
    @Test
    void restoredLandingWaitsForTerrainBeforeCachingItsTarget() {
        DragonFight.Host host = mock(DragonFight.Host.class);
        when(host.seed()).thenReturn(1464029505);
        when(host.dragonPresent(1L)).thenReturn(true);
        when(host.dragonPose(1L)).thenReturn(new double[] {30, 100, 30, 200});
        when(host.heightNoLeaves(0, 0)).thenReturn(320);
        DragonFightState state = new DragonFightState();
        state.dragonMobId = 1L;
        state.dragonPhase = DragonPhase.LANDING.id();
        DragonFight fight = new DragonFight(host, state);
        fight.bindDragon(1L);

        fight.tickMc();

        verify(host, never()).heightNoLeaves(0, 0);
        assertThat(fight.brain().y).isEqualTo(100);
        assertThat(fight.brain().health()).isEqualTo(200);
        when(host.arenaLoaded()).thenReturn(true);
        when(host.heightNoLeaves(0, 0)).thenReturn(65);
        for (int tick = 0; tick < 100; tick++) fight.tickMc();
        verify(host, atLeastOnce()).heightNoLeaves(0, 0);
        assertThat(fight.brain().y).isLessThan(100);
    }
}
