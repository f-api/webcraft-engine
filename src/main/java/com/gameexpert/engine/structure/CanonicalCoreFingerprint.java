package com.gameexpert.engine.structure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Canonical core identity: anchor translation, D4 minimum, roles, graph, no cosmetics. */
public final class CanonicalCoreFingerprint {
    public enum Role { FOUNDATION, WALL, FLOOR, ROOF, TRIM, SUPPORT, OPENING, COSMETIC }

    public static final class RoleVoxel {
        private final int x;
        private final int y;
        private final int z;
        private final Role role;

        public RoleVoxel(int x, int y, int z, Role role) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.role = role;
        }
    }

    public static final class PartEdge {
        private final int from;
        private final int to;
        private final int role;

        public PartEdge(int from, int to, int role) {
            this.from = from;
            this.to = to;
            this.role = role;
        }
    }

    private CanonicalCoreFingerprint() {}

    public static String fingerprint(int anchorX, int anchorY, int anchorZ,
            List<RoleVoxel> voxels, List<PartEdge> graph) {
        String graphText = canonicalGraph(graph);
        String best = null;
        for (int transform = 0; transform < 8; transform++) {
            List<String> transformed = new ArrayList<>();
            for (RoleVoxel voxel : voxels) {
                if (voxel.role == Role.COSMETIC) continue;
                int x = voxel.x - anchorX;
                int z = voxel.z - anchorZ;
                int tx = transformX(x, z, transform);
                int tz = transformZ(x, z, transform);
                transformed.add(tx + "," + (voxel.y - anchorY) + "," + tz + "," + voxel.role.ordinal());
            }
            transformed.sort(String::compareTo);
            String representation = graphText + "|" + String.join(";", transformed);
            if (best == null || representation.compareTo(best) < 0) best = representation;
        }
        return sha256(best == null ? graphText : best);
    }

    private static String canonicalGraph(List<PartEdge> graph) {
        List<PartEdge> sorted = new ArrayList<>(graph);
        sorted.sort(Comparator.comparingInt((PartEdge edge) -> edge.from)
                .thenComparingInt(edge -> edge.to).thenComparingInt(edge -> edge.role));
        List<String> values = new ArrayList<>(sorted.size());
        for (PartEdge edge : sorted) values.add(edge.from + "," + edge.to + "," + edge.role);
        return String.join(";", values);
    }

    private static int transformX(int x, int z, int transform) {
        int reflected = transform >= 4 ? -x : x;
        return switch (transform & 3) {
            case 0 -> reflected;
            case 1 -> -z;
            case 2 -> -reflected;
            default -> z;
        };
    }

    private static int transformZ(int x, int z, int transform) {
        int reflected = transform >= 4 ? -x : x;
        return switch (transform & 3) {
            case 0 -> z;
            case 1 -> reflected;
            case 2 -> -z;
            default -> -reflected;
        };
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte part : digest) out.append(String.format("%02x", part & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
