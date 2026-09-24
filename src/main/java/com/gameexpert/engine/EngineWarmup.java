package com.gameexpert.engine;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;

/**
 * Loads the engine's large static tables once the server is up, on its own thread.
 *
 * <p>Each of these classes builds a table in its static initializer. The first cell that needs one pays for it,
 * and when that cell is read inside a world tick the whole tick waits: a profile of ticks that overran their
 * budget showed a feature block-state table being built in the middle of one. Loading them before anyone joins
 * moves that work off the tick.</p>
 *
 * <p>{@code webcraft.warmWorldsOnStartup=true} 를 주면 이미 있는 월드의 스폰 주변도 미리 만들어
 * 둔다. 첫 진입 위치는 누가 들어오든 같으므로, 아무도 접속하지 않은 동안 만들어 두면 첫 사람이
 * 그 대기를 겪지 않는다. 월드가 여러 개면 모두 데우므로, 고정 월드 하나로 운영할 때 쓴다.</p>
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

    private final ObjectProvider<WorldStore> worlds;
    private final ObjectProvider<WorldEngineManager> engines;
    private final boolean warmWorlds;
    private final int radius;
    private final boolean keepLoaded;
    private final int pregenerateRadius;

    public EngineWarmup(ObjectProvider<WorldStore> worlds, ObjectProvider<WorldEngineManager> engines,
            Environment environment) {
        this.worlds = worlds;
        this.engines = engines;
        this.warmWorlds = environment.getProperty("webcraft.warmWorldsOnStartup", Boolean.class, true);
        this.radius = Math.max(0, environment.getProperty("webcraft.warmWorldRadius", Integer.class, 4));
        this.keepLoaded = environment.getProperty("webcraft.keepWorldsLoaded", Boolean.class, true);
        this.pregenerateRadius = Math.max(0, environment.getProperty("webcraft.pregenerateRadius", Integer.class, 0));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        if (keepLoaded) {
            // 워밍보다 먼저 건다. 데운 월드가 아무도 없다는 이유로 10초 뒤 내려가면 워밍이 헛일이 된다.
            WorldEngineManager manager = engines.getIfAvailable();
            if (manager != null) manager.keepWorldsLoaded(true);
        }
        Thread.ofVirtual().name("engine-table-warmup").start(() -> {
            loadTables();
            if (warmWorlds) warmExistingWorldSpawns();
        });
    }

    /**
     * 이미 있는 월드의 스폰 주변 생산을 미리 시작한다. 실패는 머리 시작을 잃는 것뿐이라 기록만 한다.
     */
    private void warmExistingWorldSpawns() {
        WorldStore store = worlds.getIfAvailable();
        WorldEngineManager manager = engines.getIfAvailable();
        if (store == null || manager == null) {
            return;
        }
        long started = System.nanoTime();
        int warmed = 0;
        try {
            for (WorldAccess world : store.findRootWorlds()) {
                try {
                    manager.warmSpawnArea(world.getId(), (int) world.getSeed(), world.getDifficulty(), radius);
                    warmed++;
                } catch (RuntimeException failure) {
                    log.debug("World spawn warm-up skipped world={}", world.getId(), failure);
                }
            }
        } catch (RuntimeException failure) {
            log.debug("World spawn warm-up skipped", failure);
            return;
        }
        log.info("월드 스폰 프리워밍 시작: {}개 ({}ms)", warmed, (System.nanoTime() - started) / 1_000_000L);
        if (pregenerateRadius > radius) pregenerateWorlds(store, manager);
    }

    /** 워밍 반경 바깥을 선생성 반경까지 미리 만든다. 사람이 있으면 멈췄다가 비면 이어 간다. */
    private void pregenerateWorlds(WorldStore store, WorldEngineManager manager) {
        for (WorldAccess world : store.findRootWorlds()) {
            long started = System.nanoTime();
            try {
                int produced = manager.pregenerateAround(world.getId(), (int) world.getSeed(),
                        radius + 1, pregenerateRadius);
                log.info("월드 선생성 완료: world={} 반경 {}→{} 청크 {}개 ({}s)", world.getId(), radius + 1,
                        pregenerateRadius, produced, (System.nanoTime() - started) / 1_000_000_000L);
            } catch (RuntimeException failure) {
                log.info("월드 선생성 중단: world={} ({})", world.getId(), failure.toString());
            }
        }
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
