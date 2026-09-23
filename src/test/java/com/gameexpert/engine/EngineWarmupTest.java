package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;

/** Every warmed name must exist, or the warm-up quietly stops covering it. */
class EngineWarmupTest {
    @Test
    void everyTableNameResolvesAndLoads() throws Exception {
        for (String name : EngineWarmup.TABLES) {
            assertNotNull(Class.forName(name, false, EngineWarmupTest.class.getClassLoader()), name);
        }
        assertFalse(EngineWarmup.TABLES.isEmpty());
        EngineWarmup.loadTables();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @Test
    void startsSpawnProductionForExistingWorldsWhenAsked() throws Exception {
        WorldAccess world = mock(WorldAccess.class);
        when(world.getId()).thenReturn(7L);
        when(world.getSeed()).thenReturn(1234L);
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        WorldStore store = mock(WorldStore.class);
        when(store.findRootWorlds()).thenReturn(List.of(world));
        WorldEngineManager manager = mock(WorldEngineManager.class);

        new EngineWarmup(provider(store), provider(manager),
                new MockEnvironment().withProperty("webcraft.warmWorldsOnStartup", "true")).warmOnStartup();

        for (int wait = 0; wait < 200; wait++) {
            try {
                verify(manager).warmSpawnArea(7L, 1234, Difficulty.NORMAL, 4);
                return;
            } catch (AssertionError notYet) {
                Thread.sleep(25);
            }
        }
        verify(manager).warmSpawnArea(7L, 1234, Difficulty.NORMAL, 4);
    }

    @Test
    void keepsWorldsLoadedWhenAsked() {
        WorldEngineManager manager = mock(WorldEngineManager.class);
        new EngineWarmup(provider(mock(WorldStore.class)), provider(manager),
                new MockEnvironment().withProperty("webcraft.keepWorldsLoaded", "true")).warmOnStartup();
        verify(manager).keepWorldsLoaded(true);
    }

    @Test
    void leavesWorldsAloneByDefault() throws Exception {
        WorldStore store = mock(WorldStore.class);
        WorldEngineManager manager = mock(WorldEngineManager.class);

        new EngineWarmup(provider(store), provider(manager), new MockEnvironment()).warmOnStartup();
        Thread.sleep(300);

        verify(store, never()).findRootWorlds();
        verify(manager, never()).warmSpawnArea(anyLong(), anyInt(), any(), anyInt());
        verify(manager, never()).keepWorldsLoaded(org.mockito.ArgumentMatchers.anyBoolean());
    }
}
