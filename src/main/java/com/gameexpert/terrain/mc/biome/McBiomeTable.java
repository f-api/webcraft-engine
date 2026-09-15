package com.gameexpert.terrain.mc.biome;

import com.gameexpert.terrain.mc.McTerrainDataPin;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Exact nearest-neighbour search over the official 26.3-snapshot-7 Overworld climate R-tree.
 *
 * <p>The embedded tree preserves Mojang's built child order, six-child branching and strict
 * {@code distance < bestDistance} tie handling. The seventh R-tree coordinate is the parameter
 * point offset; sampled target points use the vanilla zero offset.</p>
 */
public final class McBiomeTable {
    private static final int PAYLOAD_MAGIC = 0x4D433236;
    private static final Tree TREE = load();

    private Leaf cachedLeaf;

    public int select(McClimate climate) {
        long[] target = {
            climate.value(0), climate.value(1), climate.value(2),
            climate.value(3), climate.value(4), climate.value(5), 0L
        };
        long bestDistance = cachedLeaf == null ? Long.MAX_VALUE : cachedLeaf.distance(target);
        cachedLeaf = TREE.root.search(target, cachedLeaf, bestDistance);
        return cachedLeaf.biomeId;
    }

    public void clearCache() {
        cachedLeaf = null;
    }

    static int nodeCount() {
        return TREE.nodeCount;
    }

    static int leafCount() {
        return TREE.leafCount;
    }

    static String payloadSha256() {
        return TREE.payloadSha256;
    }

    private static Tree load() {
        byte[] compressed = Base64.getDecoder().decode(McBiomeTableData.GZIP_BASE64);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] payload = gzip.readAllBytes();
            String digest = sha256(payload);
            if (!digest.equals(McTerrainDataPin.CLIMATE_TREE_SHA256)) {
                throw new IOException("26.3 climate tree digest mismatch: " + digest);
            }
            Counter counter = new Counter();
            try (DataInputStream input =
                    new DataInputStream(new ByteArrayInputStream(payload))) {
                if (input.readInt() != PAYLOAD_MAGIC) {
                    throw new IOException("26.3 climate tree magic mismatch");
                }
                if (input.readInt() != McTerrainDataPin.WORLD_VERSION) {
                    throw new IOException("26.3 climate tree world version mismatch");
                }
                Node root = readNode(input, counter);
                if (input.available() != 0) {
                    throw new IOException("trailing bytes in 26.3 climate tree");
                }
                if (counter.nodes != McTerrainDataPin.CLIMATE_TREE_NODE_COUNT
                        || counter.leaves != McTerrainDataPin.CLIMATE_POINT_COUNT) {
                    throw new IOException("26.3 climate tree count mismatch: nodes="
                            + counter.nodes + ", leaves=" + counter.leaves);
                }
                return new Tree(root, counter.nodes, counter.leaves, digest);
            }
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Node readNode(DataInputStream input, Counter counter) throws IOException {
        counter.nodes++;
        int[] ranges = new int[McClimate.AXES * 2 + 2];
        for (int i = 0; i < ranges.length; i++) ranges[i] = input.readInt();
        int type = input.readUnsignedByte();
        if (type == 0) {
            counter.leaves++;
            return new Leaf(ranges, input.readUnsignedShort());
        }
        if (type != 1) throw new IOException("unknown 26.3 climate tree node type " + type);
        int childCount = input.readUnsignedByte();
        if (childCount < 2 || childCount > 6) {
            throw new IOException("invalid 26.3 climate tree child count " + childCount);
        }
        Node[] children = new Node[childCount];
        for (int i = 0; i < children.length; i++) children[i] = readNode(input, counter);
        return new Branch(ranges, children);
    }

    private static String sha256(byte[] value) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
    }

    private abstract static class Node {
        private final int[] ranges;

        private Node(int[] ranges) {
            this.ranges = ranges;
        }

        final long distance(long[] target) {
            long sum = 0L;
            for (int axis = 0; axis < target.length; axis++) {
                long value = target[axis];
                int low = ranges[axis * 2];
                int high = ranges[axis * 2 + 1];
                long delta = value < low ? low - value : value > high ? value - high : 0L;
                sum += delta * delta;
            }
            return sum;
        }

        abstract Leaf search(long[] target, Leaf alternate, long bestDistance);
    }

    private static final class Leaf extends Node {
        private final int biomeId;

        private Leaf(int[] ranges, int biomeId) {
            super(ranges);
            this.biomeId = biomeId;
        }

        @Override
        Leaf search(long[] target, Leaf alternate, long bestDistance) {
            return this;
        }
    }

    private static final class Branch extends Node {
        private final Node[] children;

        private Branch(int[] ranges, Node[] children) {
            super(ranges);
            this.children = children;
        }

        @Override
        Leaf search(long[] target, Leaf alternate, long bestDistance) {
            Leaf selected = alternate;
            long best = bestDistance;
            for (Node child : children) {
                long childDistance = child.distance(target);
                if (childDistance < best) {
                    Leaf candidate = child.search(target, selected, best);
                    long candidateDistance = child == candidate
                            ? childDistance : candidate.distance(target);
                    if (candidateDistance < best) {
                        best = candidateDistance;
                        selected = candidate;
                    }
                }
            }
            return selected;
        }
    }

    private static final class Counter {
        private int nodes;
        private int leaves;
    }

    private static final class Tree {
        private final Node root;
        private final int nodeCount;
        private final int leafCount;
        private final String payloadSha256;

        private Tree(Node root, int nodeCount, int leafCount, String payloadSha256) {
            this.root = root;
            this.nodeCount = nodeCount;
            this.leafCount = leafCount;
            this.payloadSha256 = payloadSha256;
        }
    }
}
