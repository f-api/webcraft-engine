package com.gameexpert.cluster;

/**
 * The resolved world owner cannot open a connection yet, usually while its lease outlives a crashed node.
 * The physical socket is already closed with 1012 so the client retries after the takeover.
 */
public final class WorldAuthorityUnavailableException extends IllegalStateException {
    private final long root;
    private final String owner;
    private final String reason;

    WorldAuthorityUnavailableException(long root, String owner, String reason, Throwable cause) {
        super("World authority unavailable: root=" + root + " owner=" + owner + " reason=" + reason, cause);
        this.root = root; this.owner = owner; this.reason = reason;
    }
    public long root() { return root; }
    public String owner() { return owner; }
    public String reason() { return reason; }
}
