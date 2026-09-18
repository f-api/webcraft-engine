package com.gameexpert.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameexpert.world.dimension.DimensionChunkProductSource;
import com.gameexpert.world.dimension.DimensionProviders;
import com.gameexpert.world.dimension.DimensionRegistry;
import com.gameexpert.world.dimension.voidend.VoidEndDimension;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.gameexpert.block.persistence.BlockDiffBuffer;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.config.EngineProperties;
import com.gameexpert.terrain.ChunkProductSource;
import com.gameexpert.api.SessionRegistry;
import static org.mockito.Mockito.mock;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.springframework.web.socket.WebSocketSession;


final class DimensionActivationProgressTest {
    @Test
    void endChunkDoesNotWaitForOverworldTreeSources() throws Exception {
        try (Fixture fixture = fixture()) {
            assertThat(invoke(fixture.runtime, "activationTreeNeighborhoodReady",
                    new Class<?>[] {int.class, int.class}, 0, 0)).isEqualTo(true);
        }
    }

    @Test
    void completedEndActivationReleasesPlannerOwnership() throws Exception {
        try (Fixture fixture = fixture()) {
            Class<?> demandType = nested("ChunkActivationDemand");
            Constructor<?> constructor = demandType.getDeclaredConstructor(
                    long.class, int.class, int.class, List.class);
            constructor.setAccessible(true);
            Object demand = constructor.newInstance(0L, 0, 0, List.of());
            Map<Long, Object> plans = fixture.map("pendingChunkActivationPlans");
            plans.put(0L, 0L);
            invoke(fixture.runtime, "prepareChunkActivationPlan",
                    new Class<?>[] {demandType, long.class}, demand, 0L);
            assertThat(fixture.map("preparedChunkActivations")).containsKey(0L);
            assertThat(plans).isEmpty();
            fixture.map("preparedChunkActivations").clear();
            assertThat(invoke(fixture.runtime, "hasActivationBacklog", new Class<?>[0]))
                    .isEqualTo(false);
        }
    }

    private static Fixture fixture() throws Exception {
        Fixture fixture = new Fixture();
        DimensionProviders providers = new DimensionProviders(new DimensionRegistry());
        VoidEndDimension.install(providers);
        setField(fixture.runtime, "chunkProductSource", new DimensionChunkProductSource(
                providers, DimensionRegistry.VOID_END, 12345));
        return fixture;
    }
    static final class Fixture implements AutoCloseable {
        final WorldRuntime runtime;
        final WebSocketSession session;
        private volatile boolean open = true;
        Fixture() throws Exception {
            session = (WebSocketSession) Proxy.newProxyInstance(
                    WebSocketSession.class.getClassLoader(), new Class<?>[] {WebSocketSession.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getId" -> "entry-progress";
                        case "isOpen" -> open;
                        case "getAttributes" -> Map.of("ws.nickname", "entrant");
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
            WorldBlockDiffRepository repository = (WorldBlockDiffRepository) Proxy.newProxyInstance(
                    WorldBlockDiffRepository.class.getClassLoader(),
                    new Class<?>[] {WorldBlockDiffRepository.class},
                    (proxy, method, args) -> method.getName().startsWith("find") ? List.of() : null);
            EngineContext context = new EngineContext(null, mock(SessionRegistry.class), repository,
                    new BlockDiffBuffer(), null, null, null,
                    new EngineProperties(1, 5000, false, -1));
            ChunkProductSource noGeneration = (seed, chunkX, chunkZ) -> {
                throw new AssertionError("This entry-progress fixture must not generate terrain");
            };
            runtime = new WorldRuntime(772L, 12345, context, null, null, null, null, null,
                    Difficulty.DEFAULT, noGeneration);
            runtime.initializeWorldSpawn(16.5, 80, 160.5);
            setField(runtime, "started", true);
            invoke(runtime, "markOwnerThread", new Class<?>[0]);
        }

        @SuppressWarnings("unchecked")
        Map<Long, Object> map(String name) {
            return (Map<Long, Object>) field(runtime, name);
        }
        @Override public void close() { runtime.disposeNow(); }
    }
    static Class<?> nested(String name) {
        for (Class<?> type : WorldRuntime.class.getDeclaredClasses()) {
            if (type.getSimpleName().equals(name)) return type;
        }
        throw new AssertionError("Missing runtime nested type: " + name);
    }

    static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffff_ffffL);
    }
}
