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
    private static final int RENEWAL_ATTEMPTS = 3;
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
    /**
     * Lease renewal borrows from its own small pool. When a burst of game writes held every shared
     * connection, renewals queued behind them, missed the 12 s local deadline, and the node dropped
     * every world it owned.
     */
    private volatile DataSource renewalSource;
    private volatile AutoCloseable renewalPool;
    /** Held for the process lifetime when fences are unavailable, so a second server cannot start. */
    private volatile Connection singleServerLock;
    private volatile String singleServerLockName;
    private final Environment environment;
    private final ConcurrentMap<Long, Held> held = new ConcurrentHashMap<>();
    private final Set<Long> retired = ConcurrentHashMap.newKeySet();
    /** The ownership each retired root last held here, to tell whether anyone owned it since. */
    private final ConcurrentMap<Long, Owner> lastHeld = new ConcurrentHashMap<>();
    /** Retired roots whose in-memory runtime this node has already discarded. */
    private final Set<Long> recoverable = ConcurrentHashMap.newKeySet();
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
            try {
                SqlWriteFences.install(c);
            } catch (SQLException failure) {
                if (!fenceTriggersNeedPrivilege(failure)) throw failure;
                holdSingleServer(namespace);
                log.warn("Write fences need MySQL triggers, which this account may not create while binary"
                        + " logging is on (RDS default). Running as a single server; set the parameter"
                        + " log_bin_trust_function_creators=1 to run several servers on this database.");
                return;
            }
            enabled = true;
            active = this;
            openRenewalPool();
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
        try (Connection c = dataSource.getConnection()) {
            // Ordinary routing does not queue an exclusive fence lock behind game saves.
            try (PreparedStatement q = c.prepareStatement("SELECT node_id,epoch,"
                    + "TIMESTAMPDIFF(MICROSECOND,UTC_TIMESTAMP(6),expires_at)"
                    + " FROM webcraft_world_lease WHERE world_id=?")) {
                q.setLong(1, root);
                try (ResultSet rows = q.executeQuery()) {
                    if (rows.next() && rows.getLong(3) > 0
                            && !ClusterIdentity.NODE.equals(rows.getString(1))) {
                        return new Owner(root, rows.getString(1), rows.getLong(2));
                    }
                }
            }
            // A retired root still routes to a live owner elsewhere; only taking it back is refused.
            if (retired.contains(root) && !recoverable.contains(root)) throw new RetiredRefusal();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement q = c.prepareStatement("INSERT INTO webcraft_world_authority"
                        + "(world_id,node_id,epoch,expires_at) VALUES (?, '', 0, '1970-01-01') ON DUPLICATE KEY UPDATE world_id=world_id")) {
                    q.setLong(1, root); q.executeUpdate();
                }
                // Takeover waits for every admitted writer BEFORE locking/rechecking the lease.
                // Renewal touches only the lease, so a waiting takeover cannot stop heartbeats.
                Owner fenced;
                try (PreparedStatement q = c.prepareStatement(
                        "SELECT node_id,epoch FROM webcraft_world_authority WHERE world_id=? FOR UPDATE")) {
                    q.setLong(1, root);
                    try (ResultSet rows = q.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Missing ownership fence");
                        fenced = new Owner(root, rows.getString(1), rows.getLong(2));
                    }
                }
                try (PreparedStatement q = c.prepareStatement("INSERT IGNORE INTO webcraft_world_lease"
                        + "(world_id,node_id,epoch,expires_at) SELECT world_id,node_id,epoch,expires_at"
                        + " FROM webcraft_world_authority WHERE world_id=?")) {
                    q.setLong(1, root);
                    q.executeUpdate();
                }
                Owner owner;
                long remainingMicros;
                long leaseObservedAt = System.nanoTime();
                try (PreparedStatement q = c.prepareStatement("SELECT node_id,epoch,"
                        + "TIMESTAMPDIFF(MICROSECOND,UTC_TIMESTAMP(6),expires_at)"
                        + " FROM webcraft_world_lease WHERE world_id=? FOR UPDATE")) {
                    q.setLong(1, root);
                    try (ResultSet rows = q.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Missing ownership lease");
                        owner = new Owner(root, rows.getString(1), rows.getLong(2));
                        remainingMicros = rows.getLong(3);
                    }
                }
                if (!fenced.equals(owner)) throw new SQLException("World lease and fence disagree");
                boolean recovering = false;
                if (remainingMicros <= 0) {
                    if (retired.contains(root) || ClusterIdentity.NODE.equals(owner.node())) {
                        // Taking a retired world back is safe only if nobody owned it since: the lease
                        // still names this node's last incarnation, so writes this node buffered are the
                        // newest ones and no other owner's changes can be overwritten by them.
                        recovering = recoverable.contains(root) && owner.equals(lastHeld.get(root));
                        if (!recovering) {
                            retire(root); throw new SQLException("Expired incarnation cannot reacquire a live cache");
                        }
                    }
                    owner = new Owner(root, ClusterIdentity.NODE, Math.addExact(owner.epoch(), 1));
                    try (PreparedStatement q = c.prepareStatement("UPDATE webcraft_world_authority SET"
                            + " node_id=?,epoch=? WHERE world_id=?")) {
                        q.setString(1, owner.node()); q.setLong(2, owner.epoch()); q.setLong(3, root); q.executeUpdate();
                    }
                    try (PreparedStatement q = c.prepareStatement("UPDATE webcraft_world_lease SET"
                            + " node_id=?,epoch=?,expires_at=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(6)) WHERE world_id=?")) {
                        q.setString(1, owner.node()); q.setLong(2, owner.epoch()); q.setLong(3, root);
                        leaseObservedAt = System.nanoTime();
                        q.executeUpdate();
                    }
                    remainingMicros = 15_000_000;
                }
                c.commit();
                if (recovering) {
                    recoverable.remove(root); lastHeld.remove(root); retired.remove(root);
                    log.warn("World authority recovered: root={} node={} epoch={}", root, owner.node(), owner.epoch());
                }
                if (ClusterIdentity.NODE.equals(owner.node())) {
                    long safeNanos = Math.min(LOCAL_NANOS, remainingMicros * 1000 - Duration.ofSeconds(3).toNanos());
                    if (closed || retired.contains(root) || safeNanos <= 0
                            || System.nanoTime() - leaseObservedAt >= safeNanos) {
                        retire(root); throw new SQLException("Ownership acquisition exceeded safe deadline");
                    }
                    held.putIfAbsent(root, new Held(owner, leaseObservedAt + safeNanos));
                    log.info("World authority acquired: root={} node={} epoch={}", root, owner.node(), owner.epoch());
                }
                return owner;
            } catch (Exception failure) { c.rollback(); throw failure; }
        } catch (RetiredRefusal refused) {
            throw refused;
        } catch (Exception failure) { throw new IllegalStateException("Cannot resolve world ownership", failure); }
    }

    /** Refusing to take a retired root back is a routing answer, not a database failure. */
    private static final class RetiredRefusal extends IllegalStateException {
        RetiredRefusal() { super("WORLD_AUTHORITY_RETIRED"); }
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
            renew(entry.getKey(), entry.getValue());
        }
    }

    private void renew(long root, Held prior) {
        for (int attempt = 1; attempt <= RENEWAL_ATTEMPTS; attempt++) {
            long started = System.nanoTime();
            if (!mayRenew(root, prior, started)) { retire(root); return; }
            SQLException rolledBack = null;
            try (Connection c = renewalConnection(); PreparedStatement q = c.prepareStatement(
                    "UPDATE webcraft_world_lease SET expires_at=TIMESTAMPADD(SECOND,15,UTC_TIMESTAMP(6))"
                            + " WHERE world_id=? AND node_id=? AND epoch=? AND expires_at>UTC_TIMESTAMP(6)")) {
                q.setQueryTimeout(3);
                q.setLong(1, root);
                q.setString(2, ClusterIdentity.NODE);
                q.setLong(3, prior.owner().epoch());
                int updated;
                try {
                    updated = q.executeUpdate();
                } catch (SQLException failure) {
                    // MySQL 1213 confirms rollback. Timeouts and connection errors do not.
                    if (failure.getErrorCode() == 1213 && "40001".equals(failure.getSQLState())) {
                        rolledBack = failure;
                    }
                    throw failure;
                }
                if (updated != 1 || !mayRenew(root, prior, System.nanoTime())) {
                    retire(root);
                    return;
                }
                held.replace(root, prior, new Held(prior.owner(), started + LOCAL_NANOS));
                return;
            } catch (Exception failure) {
                // A retry never extends the previously confirmed local lease.
                if (failure == rolledBack && failure.getSuppressed().length == 0
                        && attempt < RENEWAL_ATTEMPTS
                        && mayRenew(root, prior, System.nanoTime())) {
                    continue;
                }
                log.warn("World lease renewal failed; fencing root {}", root, failure);
                retire(root);
                return;
            }
        }
    }

    private boolean mayRenew(long root, Held prior, long now) {
        return !closed && !retired.contains(root) && held.get(root) == prior
                && now < prior.deadline();
    }
    private void retire(long root) {
        if (retired.add(root)) log.error("World authority retired: root={} node={}; runtime stopped, takeover fences prior writers",
                root, ClusterIdentity.NODE);
        Held prior = held.remove(root);
        if (prior != null) lastHeld.put(root, prior.owner());
    }
    /** The node discarded the retired root's runtime; it may take the world back if nobody else did. */
    public void runtimeDiscarded(long root) {
        if (retired.contains(root) && lastHeld.containsKey(root)) recoverable.add(root);
    }
    /** MySQL 1419: CREATE TRIGGER needs SUPER (or the trust parameter) while binary logging is on. */
    private static boolean fenceTriggersNeedPrivilege(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1419) return true;
        }
        return false;
    }

    /**
     * Without fences two servers would simulate the same worlds and overwrite each other. A named
     * lock on a connection kept for the process lifetime lets exactly one server run; another server
     * on the same database refuses to start. A keepalive stops the idle timeout from dropping it.
     */
    private void holdSingleServer(String name) {
        String lock = "webcraft:single:" + name.substring(name.length() - 24);
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement q = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
                q.setString(1, lock);
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next() || r.getInt(1) != 1) {
                        connection.close();
                        throw new IllegalStateException("Another server is already running on this database."
                                + " Several servers need the MySQL parameter log_bin_trust_function_creators=1.");
                    }
                }
            }
            singleServerLock = connection;
            singleServerLockName = lock;
            renewals.scheduleWithFixedDelay(this::keepSingleServer, 60, 60, TimeUnit.SECONDS);
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot take the single-server database lock", failure);
        }
    }

    private void keepSingleServer() {
        Connection connection = singleServerLock;
        if (connection == null || closed) return;
        try (PreparedStatement q = connection.prepareStatement("SELECT IS_USED_LOCK(?) = CONNECTION_ID()")) {
            q.setString(1, singleServerLockName);
            try (ResultSet r = q.executeQuery()) {
                if (r.next() && r.getInt(1) == 1) return;
            }
        } catch (SQLException failure) {
            log.warn("Single-server database lock connection failed; taking it again", failure);
        }
        try { connection.close(); } catch (SQLException ignored) { }
        singleServerLock = null;
        try {
            holdSingleServerAgain();
        } catch (RuntimeException failure) {
            log.error("Single-server database lock lost and could not be taken again", failure);
        }
    }

    private void holdSingleServerAgain() {
        try {
            Connection connection = dataSource.getConnection();
            try (PreparedStatement q = connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
                q.setString(1, singleServerLockName);
                try (ResultSet r = q.executeQuery()) {
                    if (r.next() && r.getInt(1) == 1) { singleServerLock = connection; return; }
                }
            }
            connection.close();
            throw new IllegalStateException("Another server took the single-server database lock");
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot take the single-server database lock again", failure);
        }
    }

    private Connection renewalConnection() throws SQLException {
        DataSource source = renewalSource;
        return (source == null ? dataSource : source).getConnection();
    }

    /**
     * Copies the shared Hikari pool's connection settings into a two-connection pool used only by
     * renewals (they run one at a time on a single thread). Connections carry the same fence tag as
     * every other engine connection. Without a Hikari pool, renewals keep using the shared source.
     */
    private void openRenewalPool() {
        renewalSource = dataSource;
        try {
            if (!dataSource.isWrapperFor(com.zaxxer.hikari.HikariDataSource.class)) return;
            com.zaxxer.hikari.HikariDataSource shared = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
            if (shared.getJdbcUrl() == null) return;
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setPoolName("webcraft-lease");
            config.setJdbcUrl(shared.getJdbcUrl());
            config.setUsername(shared.getUsername());
            config.setPassword(shared.getPassword());
            if (shared.getDriverClassName() != null) config.setDriverClassName(shared.getDriverClassName());
            config.setDataSourceProperties(shared.getDataSourceProperties());
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(1);
            // Answer inside the 12 s local lease window instead of waiting out a shared-pool queue.
            config.setConnectionTimeout(8_000);
            config.setValidationTimeout(2_000);
            config.setInitializationFailTimeout(-1);
            com.zaxxer.hikari.HikariDataSource pool = new com.zaxxer.hikari.HikariDataSource(config);
            renewalPool = pool;
            renewalSource = new FencedDataSourcePostProcessor.TaggedDataSource(pool);
        } catch (SQLException | RuntimeException failure) {
            log.warn("Dedicated lease connections unavailable; renewing through the shared pool", failure);
            renewalSource = dataSource;
        }
    }

    @Override @jakarta.annotation.PreDestroy public void close() {
        closed = true;
        renewals.shutdownNow();
        Connection single = singleServerLock;
        singleServerLock = null;
        if (single != null) {
            try { single.close(); } catch (SQLException failure) { log.debug("Single-server lock close failed", failure); }
        }
        AutoCloseable pool = renewalPool;
        renewalPool = null;
        if (pool != null) {
            try { pool.close(); }
            catch (Exception failure) { log.debug("Lease connection pool close failed", failure); }
        }
        // Never release early while another persistence worker could still be draining.
        // The successor waits for expiration AND the database transaction row lock.
    }
}
