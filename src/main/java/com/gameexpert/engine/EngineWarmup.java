package com.gameexpert.engine;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads the engine's large static tables once the server is up, on its own thread.
 *
 * <p>Each of these classes builds a table in its static initializer. The first cell that needs one pays for it,
 * and when that cell is read inside a world tick the whole tick waits: a profile of ticks that overran their
 * budget showed a feature block-state table being built in the middle of one. Loading them before anyone joins
 * moves that work off the tick.</p>
 */
@Component
public class EngineWarmup {
    private static final Logger log = LoggerFactory.getLogger(EngineWarmup.class);

    /** Table holders seen initializing inside world ticks, plus the block rule tables that feed them. */
    static final List<String> TABLES = List.of(
            "com.gameexpert.terrain.Blocks",
            "com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState",
            "com.gameexpert.terrain.mc.feature.Mc263PostprocessResolver",
            "com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec",
            "com.gameexpert.terrain.CanonicalPostprocessActivationContext",
            "com.gameexpert.terrain.mc.biome.McBiomeTable",
            "com.gameexpert.engine.BuildingBlockRules",
            "com.gameexpert.engine.Fluids",
            "com.gameexpert.engine.BlockModelShapes",
            "com.gameexpert.engine.DecorativeCollisionShapes",
            "com.gameexpert.engine.redstone.RedstoneShapes",
            "com.gameexpert.block.snapshot.ChunkSnapshotCodec");

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        Thread.ofVirtual().name("engine-table-warmup").start(EngineWarmup::loadTables);
    }

    /** Initializes every table; a failure here is only a lost head start, so it is logged and skipped. */
    static void loadTables() {
        long started = System.nanoTime();
        for (String name : TABLES) {
            try {
                Class.forName(name, true, EngineWarmup.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError | RuntimeException failure) {
                log.debug("Engine table warm-up skipped {}", name, failure);
            }
        }
        // Block rule tables are built on first use; ask for one cell of each here.
        BuildingBlockRules.collisionIgnoresState(com.gameexpert.terrain.Blocks.STONE);
        Fluids.isSolid(com.gameexpert.terrain.Blocks.STONE);
        log.info("Engine tables warmed in {} ms", (System.nanoTime() - started) / 1_000_000);
    }
}
