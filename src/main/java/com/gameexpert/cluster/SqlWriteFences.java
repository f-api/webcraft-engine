package com.gameexpert.cluster;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Engine-owned additive SQL, not an entity replacement or a student concurrency answer. */
final class SqlWriteFences {
    private SqlWriteFences() { }
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    private static class ForeignKey {
        String table;
        String column;
        String parent;
        String parentColumn;
        @com.fasterxml.jackson.annotation.JsonCreator
        private ForeignKey(@com.fasterxml.jackson.annotation.JsonProperty("table") String table, @com.fasterxml.jackson.annotation.JsonProperty("column") String column, @com.fasterxml.jackson.annotation.JsonProperty("parent") String parent, @com.fasterxml.jackson.annotation.JsonProperty("parentColumn") String parentColumn) {
            this.table = table;
            this.column = column;
            this.parent = parent;
            this.parentColumn = parentColumn;
        }
    }
    static void install(Connection c) throws SQLException {
        String lock = "webcraft:fence-schema:" + digest(c.getCatalog()).substring(0, 24);
        try (PreparedStatement acquire = c.prepareStatement("SELECT GET_LOCK(?,30)")) {
            acquire.setString(1, lock);
            try (ResultSet r = acquire.executeQuery()) {
                if (!r.next() || r.getInt(1) != 1) throw new SQLException("Schema fence installer busy");
            }
        }
        try {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS webcraft_world_authority (world_id BIGINT NOT NULL PRIMARY KEY,"
                        + "node_id VARCHAR(36) NOT NULL,epoch BIGINT NOT NULL,expires_at DATETIME(6) NOT NULL) ENGINE=InnoDB");
            }
            prepareLeaseProtocol(c);
            Map<String, Set<String>> columns = new TreeMap<>();
            try (PreparedStatement q = c.prepareStatement("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS"
                    + " WHERE TABLE_SCHEMA=DATABASE()"); ResultSet r = q.executeQuery()) {
                while (r.next()) columns.computeIfAbsent(r.getString(1), k -> new HashSet<>()).add(r.getString(2));
            }
            for (String table : List.of("world", "worlds")) {
                if (!columns.containsKey(table)) continue;
                // Create the fence row in the world creation transaction, before any world data.
                // DML guards must remain read-only: an INSERT branch makes MySQL prelock the
                // authority table for writes, serializing otherwise independent save transactions.
                String trigger = "wc_owner_init_v1_" + digest(table).substring(0, 20);
                try (Statement statement = c.createStatement()) {
                    statement.execute("CREATE TRIGGER IF NOT EXISTS `" + trigger + "` AFTER INSERT ON `"
                            + identifier(table) + "` FOR EACH ROW INSERT INTO webcraft_world_authority"
                            + "(world_id,node_id,epoch,expires_at) VALUES(NEW.id,'',0,'1970-01-01')");
                    statement.execute("INSERT IGNORE INTO webcraft_world_authority(world_id,node_id,epoch,expires_at)"
                            + " SELECT id,'',0,'1970-01-01' FROM `" + identifier(table) + "`");
                }
            }
            Map<String, String> paths = new TreeMap<>();
            Set<String> student = Set.of("world", "worlds", "player", "players", "chat_message", "chat_messages",
                    "webcraft_world_authority", "webcraft_world_lease");
            for (Map.Entry<String, Set<String>> e : columns.entrySet()) {
                if (student.contains(e.getKey())) continue;
                if (e.getValue().contains("world_id")) paths.put(e.getKey(), "$row.world_id");
                else if (e.getValue().contains("root_world_id")) paths.put(e.getKey(), "$row.root_world_id");
            }
            List<ForeignKey> keys = new ArrayList<>();
            try (PreparedStatement q = c.prepareStatement("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,"
                    + "REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE"
                    + " WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL"); ResultSet r = q.executeQuery()) {
                while (r.next()) keys.add(new ForeignKey(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
            }
            // In particular inventory_items -> player_world_states -> world. Resolve from real FK names.
            for (int pass = 0; pass < 8; pass++) {
                boolean changed = false;
                for (ForeignKey key : keys) {
                    if (paths.containsKey(key.table()) || student.contains(key.table()) || !paths.containsKey(key.parent())) continue;
                    String alias = "fence_parent_" + pass;
                    String parent = paths.get(key.parent()).replace("$row.", alias + ".");
                    paths.put(key.table(), "(SELECT " + parent + " FROM `" + identifier(key.parent()) + "` " + alias
                            + " WHERE " + alias + ".`" + identifier(key.parentColumn()) + "`=$row.`" + identifier(key.column()) + "`)");
                    changed = true;
                }
                if (!changed) break;
            }
            if (paths.keySet().stream().noneMatch(t -> t.contains("inventory"))) {
                throw new SQLException("Inventory FK fencing path missing");
            }
            int installed = 0;
            for (Map.Entry<String, String> path : paths.entrySet()) {
                for (String event : List.of("INSERT", "UPDATE", "DELETE")) {
                    if (installTrigger(c, path.getKey(), path.getValue(), event)) installed++;
                }
            }
            // MySQL FK cascades do not invoke child triggers. Fence root deletion itself.
            for (String table : List.of("world", "worlds")) {
                if (columns.containsKey(table) && installTrigger(c, table, "$row.id", "DELETE")) installed++;
            }
            LoggerFactory.getLogger(SqlWriteFences.class).info(
                    "World write fence coverage: {} tables, {} new triggers; tables={}", paths.size(), installed, paths.keySet());
        } finally {
            try (PreparedStatement release = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                release.setString(1, lock); release.execute();
            }
        }
    }
    private static void prepareLeaseProtocol(Connection c) throws SQLException {
        boolean leaseExists;
        try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='webcraft_world_lease'");
                ResultSet rows = q.executeQuery()) {
            rows.next(); leaseExists = rows.getInt(1) != 0;
        }
        boolean legacy = !leaseExists;
        try (PreparedStatement q = c.prepareStatement("SELECT COUNT(*) FROM information_schema.TRIGGERS"
                + " WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME LIKE 'wc_fence_v1_%'"
                + " AND ACTION_STATEMENT LIKE '%fence_until<=UTC_TIMESTAMP(6)%'");
                ResultSet rows = q.executeQuery()) {
            rows.next(); legacy |= rows.getInt(1) != 0;
        }
        if (legacy) requireExpiredLeases(c, "webcraft_world_authority");
        if (legacy && leaseExists) requireExpiredLeases(c, "webcraft_world_lease");
        // Old processes cannot acquire/renew the now-stable fence, even if they were idle
        // during upgrade. The connection marker is supplied by this engine, not student config.
        for (String event : List.of("INSERT", "UPDATE", "DELETE")) {
            String name = "wc_authority_protocol_v2_" + event.toLowerCase(Locale.ROOT);
            String body = "BEGIN IF COALESCE(@webcraft_fence_protocol,0)<>2 THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEBCRAFT_FENCE_PROTOCOL_REQUIRED'; END IF; END";
            ensureTrigger(c, name, "webcraft_world_authority", event, body);
        }
        try (Statement statement = c.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS webcraft_world_lease (world_id BIGINT NOT NULL PRIMARY KEY,"
                    + "node_id VARCHAR(36) NOT NULL,epoch BIGINT NOT NULL,expires_at DATETIME(6) NOT NULL) ENGINE=InnoDB");
        }
        if (legacy) {
            c.setAutoCommit(false);
            try {
                // Drain admitted legacy writers and recheck races with the first quiescence check.
                try (Statement q = c.createStatement(); ResultSet rows = q.executeQuery(
                        "SELECT world_id, node_id, expires_at>UTC_TIMESTAMP(6) FROM webcraft_world_authority FOR UPDATE")) {
                    while (rows.next()) {
                        if (!rows.getString(2).isEmpty() && rows.getBoolean(3)) throw activeLegacyLease();
                    }
                }
                copyMissingLeases(c);
                c.commit();
            } catch (SQLException failure) {
                c.rollback(); throw failure;
            } finally { c.setAutoCommit(true); }
        } else {
            copyMissingLeases(c);
        }
    }

    private static void requireExpiredLeases(Connection c, String table) throws SQLException {
        try (Statement q = c.createStatement(); ResultSet rows = q.executeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE node_id<>'' AND expires_at>UTC_TIMESTAMP(6)")) {
            rows.next(); if (rows.getLong(1) != 0) throw activeLegacyLease();
        }
    }

    private static SQLException activeLegacyLease() {
        return new SQLException("Active legacy world leases prevent fence migration; stop all application nodes and retry");
    }

    private static void copyMissingLeases(Connection c) throws SQLException {
        try (Statement statement = c.createStatement()) {
            statement.executeUpdate("INSERT IGNORE INTO webcraft_world_lease(world_id,node_id,epoch,expires_at)"
                    + " SELECT world_id,node_id,epoch,expires_at FROM webcraft_world_authority");
        }
    }

    private static boolean installTrigger(Connection c, String table, String expression, String event) throws SQLException {
        String name = "wc_fence_v1_" + digest(table + ":" + event).substring(0, 20);
        String body = triggerBody(expression, event, false);
        String existing = triggerBody(c, name);
        if (existing == null) {
            ensureTrigger(c, name, table, event, body);
            dropTemporaryGuard(c, table, event, body);
            return true;
        }
        if (body.trim().equals(existing.trim())) {
            dropTemporaryGuard(c, table, event, body);
            return false;
        }
        if (!triggerBody(expression, event, true).trim().equals(existing.trim())) {
            throw new SQLException("Different existing world write fence: " + name + "; do not replace an unknown fence");
        }
        // Never leave a writable table uncovered while DDL autocommits each migration step.
        String temporary = temporaryGuardName(table, event);
        ensureTrigger(c, temporary, table, event, body);
        try (Statement statement = c.createStatement()) { statement.execute("DROP TRIGGER `" + name + "`"); }
        ensureTrigger(c, name, table, event, body);
        dropTemporaryGuard(c, table, event, body);
        return true;
    }

    private static String triggerBody(String expression, String event, boolean legacy) {
        String guards = event.equals("UPDATE")
                ? guard(expression, "OLD", legacy) + guard(expression, "NEW", legacy)
                : guard(expression, event.equals("DELETE") ? "OLD" : "NEW", legacy);
        return "BEGIN DECLARE fence_world BIGINT; DECLARE fence_owner VARCHAR(36); "
                + "DECLARE fence_until DATETIME(6); " + guards + "END";
    }

    private static String triggerBody(Connection c, String name) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT ACTION_STATEMENT FROM information_schema.TRIGGERS"
                + " WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?")) {
            q.setString(1, name);
            try (ResultSet rows = q.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static void ensureTrigger(Connection c, String name, String table, String event, String body) throws SQLException {
        String existing = triggerBody(c, name);
        if (existing != null) {
            if (!body.trim().equals(existing.trim())) throw new SQLException("Different existing world write fence: " + name);
            return;
        }
        try (Statement statement = c.createStatement()) {
            statement.execute("CREATE TRIGGER `" + name + "` BEFORE " + event + " ON `" + identifier(table)
                    + "` FOR EACH ROW " + body);
        }
    }

    private static String temporaryGuardName(String table, String event) {
        return "wc_fence_upgrade_v2_" + digest(table + ":" + event).substring(0, 20);
    }

    private static void dropTemporaryGuard(Connection c, String table, String event, String body) throws SQLException {
        String name = temporaryGuardName(table, event);
        String existing = triggerBody(c, name);
        if (existing == null) return;
        if (!body.trim().equals(existing.trim())) throw new SQLException("Different temporary world write fence: " + name);
        try (Statement statement = c.createStatement()) { statement.execute("DROP TRIGGER `" + name + "`"); }
    }

    static String guard(String expression, String row) { return guard(expression, row, false); }

    private static String guard(String expression, String row, boolean legacy) {
        // Stable incarnation fencing is independent of heartbeat expiry. Takeover takes X,
        // waits for admitted writes to commit, then changes the incarnation before loading state.
        return (legacy ? "" : "IF COALESCE(@webcraft_fence_protocol,0)<>2 THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEBCRAFT_FENCE_PROTOCOL_REQUIRED'; END IF; ")
                + "SET fence_world=" + expression.replace("$row", row) + "; "
                + "SET fence_world=COALESCE((SELECT root_world_id FROM world_dimensions WHERE child_world_id=fence_world),fence_world); "
                + "IF fence_world IS NOT NULL THEN "
                + "SET fence_owner=NULL; "
                + (legacy
                    ? "SELECT node_id,expires_at INTO fence_owner,fence_until FROM webcraft_world_authority WHERE world_id=fence_world FOR SHARE; "
                    : "SELECT node_id INTO fence_owner FROM webcraft_world_authority WHERE world_id=fence_world FOR SHARE; ")
                + "IF fence_owner IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEBCRAFT_MISSING_WORLD_AUTHORITY'; END IF; "
                + (legacy
                    ? "IF fence_owner<>'' AND (BINARY fence_owner<>BINARY COALESCE(@webcraft_node,'') OR fence_until<=UTC_TIMESTAMP(6)) "
                    : "IF fence_owner<>'' AND BINARY fence_owner<>BINARY COALESCE(@webcraft_node,'') ")
                + "THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEBCRAFT_STALE_WORLD_AUTHORITY'; END IF; END IF; ";
    }
    private static String identifier(String value) {
        if (!value.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Unsupported SQL identifier");
        return value;
    }
    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
