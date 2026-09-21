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
            verify(fixture.connection, times(2)).prepareStatement(
                    "UPDATE webcraft_world_lease SET expires_at=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(6))"
                            + " WHERE world_id=? AND node_id=? AND epoch=? AND expires_at>UTC_TIMESTAMP(6)");
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
    void retiredResolveRoutesToALiveRemoteOwner() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.expire();
            fixture.renew();
            java.sql.ResultSet lease = mock(java.sql.ResultSet.class);
            when(fixture.statement.executeQuery()).thenReturn(lease);
            when(lease.next()).thenReturn(true);
            when(lease.getString(1)).thenReturn("other-node");
            when(lease.getLong(2)).thenReturn(8L);
            when(lease.getLong(3)).thenReturn(10_000_000L);
            // Another node took the world over; this node still sends its players there.
            assertEquals(new WorldAuthority.Owner(1, "other-node", 8), fixture.authority.resolve(1));
            assertFalse(fixture.authority.owns(1));
            verify(fixture.connection, never()).setAutoCommit(false);
        }
    }

    @Test
    void retiredResolveNeverReinstallsThisNode() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.expire();
            fixture.renew();
            java.sql.ResultSet lease = mock(java.sql.ResultSet.class);
            when(fixture.statement.executeQuery()).thenReturn(lease);
            when(lease.next()).thenReturn(true);
            when(lease.getString(1)).thenReturn(ClusterIdentity.NODE);
            when(lease.getLong(2)).thenReturn(7L);
            when(lease.getLong(3)).thenReturn(-1L);
            clearInvocations(fixture.connection);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> fixture.authority.resolve(1));
            assertEquals("WORLD_AUTHORITY_RETIRED", failure.getMessage());
            // Only the lock-free routing read ran: no fence, lock or ownership write.
            verify(fixture.connection).prepareStatement(contains("FROM webcraft_world_lease WHERE world_id=?"));
            verify(fixture.connection, never()).setAutoCommit(false);
            verify(fixture.connection, never()).prepareStatement(contains("FOR UPDATE"));
            verify(fixture.connection, never()).prepareStatement(startsWith("UPDATE"));
            assertFalse(fixture.authority.owns(1));
        }
    }

    @Test
    void renewalKeepsTheWorldWhenGameTrafficHoldsEverySharedConnection() throws Exception {
        try (Fixture fixture = new Fixture()) {
            // The shared pool is exhausted by game writes; only the lease pool can answer.
            when(fixture.source.getConnection()).thenThrow(new SQLException("HikariPool-1 - Connection is not available"));
            DataSource leases = mock(DataSource.class);
            Connection leaseConnection = mock(Connection.class);
            PreparedStatement renewal = mock(PreparedStatement.class);
            when(leases.getConnection()).thenReturn(leaseConnection);
            when(leaseConnection.prepareStatement(anyString())).thenReturn(renewal);
            when(renewal.executeUpdate()).thenReturn(1);
            Fixture.field("renewalSource").set(fixture.authority, leases);

            fixture.renew();

            assertTrue(fixture.authority.owns(1));
            assertTrue(fixture.authority.retiredRoots().isEmpty());
            verify(renewal).executeUpdate();
        }
    }

    @Test
    void renewalPoolCopiesTheSharedHikariSettingsAndStaysSmall() throws Exception {
        // The pool connects lazily; a stand-in driver lets it resolve the URL without a database.
        java.sql.Driver driver = mock(java.sql.Driver.class);
        when(driver.acceptsURL(anyString())).thenReturn(true);
        java.sql.DriverManager.registerDriver(driver);
        com.zaxxer.hikari.HikariDataSource shared = new com.zaxxer.hikari.HikariDataSource();
        shared.setJdbcUrl("jdbc:mysql://mysql:3306/webcraft");
        shared.setUsername("root");
        shared.setPassword("secret");
        WorldAuthority authority = new WorldAuthority(
                new FencedDataSourcePostProcessor.TaggedDataSource(shared), mock(Environment.class));
        Method open = WorldAuthority.class.getDeclaredMethod("openRenewalPool");
        open.setAccessible(true);
        open.invoke(authority);
        try {
            Object source = Fixture.field("renewalSource").get(authority);
            assertInstanceOf(FencedDataSourcePostProcessor.TaggedDataSource.class, source);
            com.zaxxer.hikari.HikariDataSource pool = (com.zaxxer.hikari.HikariDataSource) Fixture.field("renewalPool").get(authority);
            assertNotSame(shared, pool);
            assertEquals("webcraft-lease", pool.getPoolName());
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals("jdbc:mysql://mysql:3306/webcraft", pool.getJdbcUrl());
            assertEquals("root", pool.getUsername());
            assertEquals("secret", pool.getPassword());
        } finally {
            authority.close();
            shared.close();
            java.sql.DriverManager.deregisterDriver(driver);
        }
        assertNull(Fixture.field("renewalPool").get(authority));
    }

    @Test
    void renewalFallsBackToTheSharedSourceWithoutHikari() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Method open = WorldAuthority.class.getDeclaredMethod("openRenewalPool");
            open.setAccessible(true);
            open.invoke(fixture.authority);
            assertSame(fixture.source, Fixture.field("renewalSource").get(fixture.authority));
            assertNull(Fixture.field("renewalPool").get(fixture.authority));
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
