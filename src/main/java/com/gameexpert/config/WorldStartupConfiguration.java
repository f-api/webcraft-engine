package com.gameexpert.config;

import com.gameexpert.world.WorldBaselineReadiness;
import com.gameexpert.world.service.WorldOperations;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorldStartupConfiguration {
    @Bean
    public ApplicationRunner purgeObsoleteMinecraftWorlds(WorldOperations worlds,
            WorldBaselineReadiness readiness) {
        return arguments -> {
            worlds.purgeObsoleteWorlds();
            readiness.markReady();
        };
    }
}
