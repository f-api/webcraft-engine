package com.gameexpert.cluster;
import tools.jackson.databind.ObjectMapper;
public final class ClusterWireVerification {
    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        ClusterRuntime.Packet packet = new ClusterRuntime.Packet("input", "node", "wire", 1, 2, 3, 4, "payload", true, 1012);
        ClusterRuntime.Identity identity = new ClusterRuntime.Identity("name", 7, 13, "normal", 4);
        if (!packet.equals(mapper.readValue(mapper.writeValueAsString(packet), ClusterRuntime.Packet.class))) throw new AssertionError("packet round trip");
        if (!identity.equals(mapper.readValue(mapper.writeValueAsString(identity), ClusterRuntime.Identity.class))) throw new AssertionError("identity round trip");
        System.out.println("PASS: full engine packet and identity JSON round trips");
    }
}
