package com.gameexpert.cluster;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorldAuthorityRenewalTest {
    @Test
    void confirmedDeadlockRetriesWithoutLosingOwner() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.statement.executeUpdate()).thenThrow(deadlock()).thenReturn(1);
            fixture.renew();
            verify(fixture.statement, times(2)).executeUpdate();
            assertTrue(fixture.authority.owns(1));
            assertTrue(fixture.authority.accepts(fixture.owner));
            assertTrue(fixture.authority.retiredRoots().isEmpty());
            verify(fixture.statement, times(2)).setLong(3, 7L);
        }
    }

    @Test
    void repeatedDeadlocksStopAfterThreeAttempts() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.statement.executeUpdate()).thenThrow(deadlock());
            fixture.renew();
            verify(fixture.statement, times(3)).executeUpdate();
            assertFalse(fixture.authority.owns(1));
            assertEquals(java.util.Set.of(1L), fixture.authority.retiredRoots());
        }
    }

    @Test
    void unknownOutcomeAndTimeoutNeverRetry() throws Exception {
        for (SQLException failure : new SQLException[]{
                new SQLException("connection lost", "08S01", 0),
                new SQLException("lock wait timeout", "HY000", 1205),
                new SQLException("unconfirmed serialization failure", "40001", 0),
                new SQLException("wrong SQL state", "HY000", 1213)}) {
            try (Fixture fixture = new Fixture()) {
                when(fixture.statement.executeUpdate()).thenThrow(failure);
                fixture.renew();
                verify(fixture.statement).executeUpdate();
                assertFalse(fixture.authority.owns(1));
            }
        }
    }

    @Test
    void cleanupFailureMakesEvenDeadlockFailClosed() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.statement.executeUpdate()).thenThrow(deadlock());
            doThrow(new SQLException("close failed", "08S01")).when(fixture.connection).close();
            fixture.renew();
            verify(fixture.statement).executeUpdate();
            assertFalse(fixture.authority.owns(1));
        }
    }

    @Test
    void changedOwnerOrEpochCannotRenew() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.statement.executeUpdate()).thenReturn(0);
            fixture.renew();
            verify(fixture.statement).executeUpdate();
            assertFalse(fixture.authority.accepts(fixture.owner));
        }
    }

    @Test
    void deadlockAfterLocalDeadlineDoesNotRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.statement.executeUpdate()).thenAnswer(invocation -> {
                fixture.expire();
                throw deadlock();
            });
            fixture.renew();
            verify(fixture.statement).executeUpdate();
            assertFalse(fixture.authority.owns(1));
        }
    }

    @Test
    void successfulSqlAfterLocalDeadlineCannotReviveOwner() throws Exception {
        try (Fixture fixture = new Fixture()) {
            when(fixture.statement.executeUpdate()).thenAnswer(invocation -> {
                fixture.expire();
                return 1;
            });
            fixture.renew();
            assertFalse(fixture.authority.owns(1));
            assertEquals(java.util.Set.of(1L), fixture.authority.retiredRoots());
        }
    }

    @Test
    void retiredResolveDoesNotBorrowConnectionOrReinstallOwner() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.expire();
            fixture.renew();
            clearInvocations(fixture.source);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> fixture.authority.resolve(1));
            assertEquals("WORLD_AUTHORITY_RETIRED", failure.getMessage());
            verifyNoInteractions(fixture.source);
            assertFalse(fixture.authority.owns(1));
        }
    }

    private static SQLException deadlock() {
        return new SQLException("Deadlock found; transaction rolled back", "40001", 1213);
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final PreparedStatement statement = mock(PreparedStatement.class);
        final WorldAuthority authority = new WorldAuthority(source, mock(Environment.class));
        final WorldAuthority.Owner owner = new WorldAuthority.Owner(1, ClusterIdentity.NODE, 7);
        final Object held;

        @SuppressWarnings("unchecked")
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            field("enabled").setBoolean(authority, true);
            ((Map<Long, Long>) field("roots").get(authority)).put(1L, 1L);
            Class<?> heldType = Class.forName("com.gameexpert.cluster.WorldAuthority$Held");
            Constructor<?> constructor = heldType.getDeclaredConstructor(WorldAuthority.Owner.class, long.class);
            constructor.setAccessible(true);
            held = constructor.newInstance(owner, System.nanoTime() + Duration.ofSeconds(30).toNanos());
            ((Map<Long, Object>) field("held").get(authority)).put(1L, held);
        }

        void expire() throws Exception {
            Field deadline = held.getClass().getDeclaredField("deadline");
            deadline.setAccessible(true);
            deadline.setLong(held, System.nanoTime() - 1);
        }

        void renew() throws Exception {
            Method method = WorldAuthority.class.getDeclaredMethod("renew");
            method.setAccessible(true);
            method.invoke(authority);
        }

        private static Field field(String name) throws Exception {
            Field field = WorldAuthority.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        @Override public void close() { authority.close(); }
    }
}
