package com.gameexpert.cluster;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * MySQL owns the lease and the write fence. Redis is only a transport.
 * Protected DML takes the ownership row lock in the SAME transaction as the data.
 * Takeover therefore waits for committed prior writes; an expired incarnation never
 * reuses its in-memory world, even when it can reach MySQL again.
 */
@Component
public final class WorldAuthority implements SmartInitializingSingleton, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WorldAuthority.class);
    private static final long LOCAL_NANOS = Duration.ofSeconds(12).toNanos();
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class Owner {
        long root;
        String node;
        long epoch;
        @com.fasterxml.jackson.annotation.JsonCreator
        public Owner(@com.fasterxml.jackson.annotation.JsonProperty("root") long root, @com.fasterxml.jackson.annotation.JsonProperty("node") String node, @com.fasterxml.jackson.annotation.JsonProperty("epoch") long epoch) {
            this.root = root;
            this.node = node;
            this.epoch = epoch;
        }
    }
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    private static class Held {
        Owner owner;
        long deadline;
        @com.fasterxml.jackson.annotation.JsonCreator
        private Held(@com.fasterxml.jackson.annotation.JsonProperty("owner") Owner owner, @com.fasterxml.jackson.annotation.JsonProperty("deadline") long deadline) {
            this.owner = owner;
            this.deadline = deadline;
        }
    }
    private final DataSource dataSource;
    private final Environment environment;
    private final ConcurrentMap<Long, Held> held = new ConcurrentHashMap<>();
    private final Set<Long> retired = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, Long> roots = new ConcurrentHashMap<>();
    private final ScheduledExecutorService renewals = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cluster-world-lease"); t.setDaemon(true); return t;
    });
    private volatile boolean enabled;
    private volatile boolean closed;
    private volatile String namespace;
    private static volatile WorldAuthority active;

    public WorldAuthority(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource; this.environment = environment;
    }
    public boolean enabled() { return enabled; }
    public String namespace() { return namespace; }
    public Set<Long> retiredRoots() { return Set.copyOf(retired); }

    @Override public void afterSingletonsInstantiated() {
        try (Connection c = dataSource.getConnection()) {
            if (!c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) return;
            if (!environment.getProperty("webcraft.game.cluster-enabled", Boolean.class, true)) return;
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                    "SELECT CONCAT(@@server_uuid, ':', DATABASE())")) {
                if (!r.next()) throw new SQLException("Missing database identity");
                namespace = "webcraft:game:v1:" + SqlWriteFences.digest(r.getString(1)).substring(0, 24);
            }
            SqlWriteFences.install(c);
            enabled = true;
            active = this;
            renewals.scheduleWithFixedDelay(this::renew, 2, 2, TimeUnit.SECONDS);
            log.info("Game authority enabled: node={} namespace={} (separate from student chat)",
                    ClusterIdentity.NODE, namespace);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot initialize shared-world write fencing", failure);
        }
    }

    public long rootFor(long world) {
        return roots.computeIfAbsent(world, key -> {
            try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement(
                    "SELECT root_world_id FROM world_dimensions WHERE child_world_id=?")) {
                q.setLong(1, key);
                try (ResultSet rows = q.executeQuery()) { return rows.next() ? rows.getLong(1) : key; }
            } catch (SQLException failure) { throw new IllegalStateException("Cannot resolve world root", failure); }
        });
    }

    public Owner resolve(long world) {
        if (!enabled) return new Owner(world, ClusterIdentity.NODE, 0);
        long root = rootFor(world);
        if (closed) throw new IllegalStateException("WORLD_AUTHORITY_CLOSED");
        Held known = held.get(root);
        if (known != null) {
            if (System.nanoTime() < known.deadline()) return known.owner();
            retire(root); throw new IllegalStateException("WORLD_AUTHORITY_EXPIRED");
        }
        long started = System.nanoTime();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement q = c.prepareStatement("INSERT INTO webcraft_world_authority"
                        + "(world_id,node_id,epoch,expires_at) VALUES (?, '', 0, '1970-01-01') ON DUPLICATE KEY UPDATE world_id=world_id")) {
                    q.setLong(1, root); q.executeUpdate();
                }
                Owner owner;
                long remainingMicros;
                try (PreparedStatement q = c.prepareStatement("SELECT node_id,epoch,"
                        + "TIMESTAMPDIFF(MICROSECOND,UTC_TIMESTAMP(6),expires_at)"
                        + " FROM webcraft_world_authority WHERE world_id=? FOR UPDATE")) {
                    q.setLong(1, root);
                    try (ResultSet rows = q.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Missing ownership row");
                        owner = new Owner(root, rows.getString(1), rows.getLong(2));
                        remainingMicros = rows.getLong(3);
                    }
                }
                if (remainingMicros <= 0) {
                    if (retired.contains(root) || ClusterIdentity.NODE.equals(owner.node())) {
                        retire(root); throw new SQLException("Expired incarnation cannot reacquire a live cache");
                    }
                    owner = new Owner(root, ClusterIdentity.NODE, Math.addExact(owner.epoch(), 1));
                    try (PreparedStatement q = c.prepareStatement("UPDATE webcraft_world_authority SET"
                            + " node_id=?,epoch=?,expires_at=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(6)) WHERE world_id=?")) {
                        q.setString(1, owner.node()); q.setLong(2, owner.epoch()); q.setLong(3, root); q.executeUpdate();
                    }
                    remainingMicros = 15_000_000;
                }
                c.commit();
                if (ClusterIdentity.NODE.equals(owner.node())) {
                    long safeNanos = Math.min(LOCAL_NANOS, remainingMicros * 1000 - Duration.ofSeconds(3).toNanos());
                    if (safeNanos <= 0 || System.nanoTime() - started >= safeNanos) {
                        retire(root); throw new SQLException("Ownership acquisition exceeded safe deadline");
                    }
                    held.putIfAbsent(root, new Held(owner, started + safeNanos));
                    log.info("World authority acquired: root={} node={} epoch={}", root, owner.node(), owner.epoch());
                }
                return owner;
            } catch (Exception failure) { c.rollback(); throw failure; }
        } catch (Exception failure) { throw new IllegalStateException("Cannot resolve world ownership", failure); }
    }

    public boolean owns(long world) {
        if (!enabled) return true;
        long root = rootFor(world);
        Held value = held.get(root);
        if (closed || value == null || retired.contains(root)) return false;
        if (System.nanoTime() >= value.deadline()) { retire(root); return false; }
        return true;
    }
    public boolean accepts(Owner expected) {
        Held value = held.get(expected.root());
        return owns(expected.root()) && value != null && value.owner().equals(expected);
    }
    public static long rootOf(long world) {
        WorldAuthority authority = active;
        return authority == null || !authority.enabled ? world : authority.rootFor(world);
    }
    public static void requireRuntime(long world) {
        WorldAuthority authority = active;
        if (authority == null || !authority.enabled) return;
        Owner owner = authority.resolve(world);
        if (!ClusterIdentity.NODE.equals(owner.node()) || !authority.owns(world)) {
            throw new IllegalStateException("WORLD_NOT_OWNED_HERE");
        }
    }
    public static boolean permitsRuntime(long world) {
        WorldAuthority authority = active;
        return authority == null || !authority.enabled || authority.owns(world);
    }
    private void renew() {
        for (Map.Entry<Long, Held> entry : held.entrySet()) {
            long root = entry.getKey(); Held prior = entry.getValue(); long started = System.nanoTime();
            if (retired.contains(root) || started >= prior.deadline()) { retire(root); continue; }
            try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement(
                    "UPDATE webcraft_world_authority SET expires_at=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(6))"
                            + " WHERE world_id=? AND node_id=? AND epoch=? AND expires_at>UTC_TIMESTAMP(6)")) {
                q.setQueryTimeout(3); q.setLong(1, root); q.setString(2, ClusterIdentity.NODE); q.setLong(3, prior.owner().epoch());
                if (q.executeUpdate() != 1 || System.nanoTime() >= prior.deadline()) { retire(root); continue; }
                held.replace(root, prior, new Held(prior.owner(), started + LOCAL_NANOS));
            } catch (Exception failure) {
                // An ambiguous renewal is terminal, not permission to revive a cached world.
                log.warn("World lease renewal failed; fencing root {}", root, failure); retire(root);
            }
        }
    }
    private void retire(long root) {
        if (retired.add(root)) log.error("World authority retired: root={} node={}; MySQL rejects late writes",
                root, ClusterIdentity.NODE);
        held.remove(root);
    }
    @Override @jakarta.annotation.PreDestroy public void close() {
        closed = true;
        renewals.shutdownNow();
        // Never release early while another persistence worker could still be draining.
        // The successor waits for expiration AND the database transaction row lock.
    }
}
