package com.gameexpert.cluster;

/** A process incarnation, never a host name or a reusable server number. */
public final class ClusterIdentity {
    public static final String NODE = java.util.UUID.randomUUID().toString();
    public static final String ROLE = "webcraft.cluster.role";
    public static final String AUTHORITY = "authority";
    private ClusterIdentity() { }
    public static boolean isAuthority(org.springframework.web.socket.WebSocketSession session) {
        return AUTHORITY.equals(session.getAttributes().get(ROLE));
    }
}
