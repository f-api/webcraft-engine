package com.gameexpert.audit;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.ProducerAuthorities;
import com.gameexpert.engine.mob.SpawnerRules;
import com.gameexpert.engine.persistence.finalcarrier.spawner.SpawnerAggregate;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GenericSpawnerCarrierTest {
    @Test void existingStoredCarrierInstallsSkeletonWithoutRewritingBytes() throws Exception {
        byte[] raw;
        try (InputStream input = getClass().getResourceAsStream("/fixtures/spawner-generic-skeleton.bin")) {
            raw = input.readAllBytes();
        }
        NeutralFinalChunk carrier = ProducerAuthorities.verify(WorldGenerationProfiles.CURRENT, 9, -4, raw);
        SpawnerAggregate aggregate = SpawnerAggregate.prepare("stored-carrier-regression", carrier);
        assertThat(aggregate.plannedSpawners()).anySatisfy(spawner -> {
            assertThat(spawner.x()).isEqualTo(145);
            assertThat(spawner.y()).isEqualTo(-21);
            assertThat(spawner.z()).isEqualTo(-53);
            assertThat(spawner.entityKey()).isEqualTo("minecraft:skeleton");
            assertThat(spawner.carrierBlockId()).isEqualTo(60);
            assertThat(spawner.authoritativeState()).isEqualTo(3);
        });
    }
    @Test void genericCarrierDoesNotAuthorizeUnknownEntityOrUnrelatedBlocks() {
        assertThat(SpawnerRules.matchesCarrier("minecraft:skeleton", 60)).isTrue();
        assertThat(SpawnerRules.matchesCarrier("minecraft:skeleton", SpawnerRules.carrierForEntityKey("minecraft:skeleton"))).isTrue();
        assertThat(SpawnerRules.matchesCarrier("minecraft:skeleton", 1)).isFalse();
        assertThat(SpawnerRules.matchesCarrier("minecraft:unknown", 60)).isFalse();
    }
}
