package com.gameexpert.engine.dragon;

import java.util.ArrayList;
import java.util.List;

/**
 * [DRAGON] 바닐라 {@code EnderDragon} 의 24 노드 비행 그래프와 A* ({@code findPath}/{@code reconstructPath}),
 * 그 안에서 쓰는 {@code Node}·{@code BinaryHeap}·{@code Path} 의 이식(핀 26.3 javap). 정적판
 * {@code DragonPath.ts} 와 같은 연산 순서다 — 동률일 때 힙 순서가 경로를 가르므로 {@code upHeap}/
 * {@code downHeap} 비교식까지 그대로 옮긴다.
 */
public final class DragonPath {
    /** 바닐라 {@code nodeAdjacency} 상수(비트 i = 노드 i 와 이어짐). */
    static final int[] NODE_ADJACENCY = {
        6146, 8197, 8202, 16404, 32808, 32848, 65696, 131392, 131712, 263424, 526848, 525313,
        1581057, 3166214, 2138120, 6373424, 4358208, 12910976, 9044480, 9706496, 15216640, 13688832,
        11763712, 8257536,
    };

    /** {@code net.minecraft.world.level.pathfinder.Node} 중 드래곤이 쓰는 필드. */
    public static final class Node {
        public final int x;
        public final int y;
        public final int z;
        int heapIdx = -1;
        float g;
        float h;
        float f;
        Node cameFrom;
        boolean closed;

        public Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        float distanceTo(Node other) {
            float dx = other.x - x;
            float dy = other.y - y;
            float dz = other.z - z;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        float distanceToSqr(Node other) {
            float dx = other.x - x;
            float dy = other.y - y;
            float dz = other.z - z;
            return dx * dx + dy * dy + dz * dz;
        }

        boolean sameCell(Node other) {
            return other.x == x && other.y == y && other.z == z;
        }

        boolean inOpenSet() {
            return heapIdx >= 0;
        }
    }

    /** {@code net.minecraft.world.level.pathfinder.Path}(노드 목록 + 다음 칸 커서). */
    public static final class Path {
        final List<Node> nodes;
        int nextNodeIndex;

        Path(List<Node> nodes) {
            this.nodes = nodes;
        }

        public void advance() {
            nextNodeIndex++;
        }

        public boolean isDone() {
            return nextNodeIndex >= nodes.size();
        }

        public Node nextNode() {
            return nodes.get(nextNodeIndex);
        }

        public int nextNodeIndex() {
            return nextNodeIndex;
        }

        public List<Node> nodes() {
            return nodes;
        }

        /** 영속 복원용: 노드 좌표와 커서로 되살린다. */
        public static Path restore(List<Node> nodes, int nextNodeIndex) {
            Path path = new Path(new ArrayList<>(nodes));
            path.nextNodeIndex = nextNodeIndex;
            return path;
        }
    }

    /** {@code net.minecraft.world.level.pathfinder.BinaryHeap}. */
    static final class BinaryHeap {
        private Node[] heap = new Node[128];
        private int size;

        Node insert(Node node) {
            if (node.heapIdx >= 0) throw new IllegalStateException("OW KNOWS!");
            if (size == heap.length) {
                Node[] grown = new Node[size << 1];
                System.arraycopy(heap, 0, grown, 0, size);
                heap = grown;
            }
            heap[size] = node;
            node.heapIdx = size;
            upHeap(size++);
            return node;
        }

        void clear() {
            size = 0;
        }

        Node pop() {
            Node top = heap[0];
            heap[0] = heap[--size];
            heap[size] = null;
            if (size > 0) downHeap(0);
            top.heapIdx = -1;
            return top;
        }

        void changeCost(Node node, float cost) {
            float old = node.f;
            node.f = cost;
            if (cost < old) upHeap(node.heapIdx);
            else downHeap(node.heapIdx);
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void upHeap(int index) {
            Node node = heap[index];
            float cost = node.f;
            while (index > 0) {
                int parentIndex = index - 1 >> 1;
                Node parent = heap[parentIndex];
                if (!(cost < parent.f)) break;
                heap[index] = parent;
                parent.heapIdx = index;
                index = parentIndex;
            }
            heap[index] = node;
            node.heapIdx = index;
        }

        private void downHeap(int index) {
            Node node = heap[index];
            float cost = node.f;
            while (true) {
                int left = 1 + (index << 1);
                int right = left + 1;
                if (left >= size) break;
                Node leftNode = heap[left];
                float leftCost = leftNode.f;
                Node rightNode;
                float rightCost;
                if (right >= size) {
                    rightNode = null;
                    rightCost = Float.POSITIVE_INFINITY;
                } else {
                    rightNode = heap[right];
                    rightCost = rightNode.f;
                }
                if (leftCost < rightCost) {
                    if (!(leftCost < cost)) break;
                    heap[index] = leftNode;
                    leftNode.heapIdx = index;
                    index = left;
                } else {
                    if (!(rightCost < cost)) break;
                    heap[index] = rightNode;
                    rightNode.heapIdx = index;
                    index = right;
                }
            }
            heap[index] = node;
            node.heapIdx = index;
        }
    }

    private final Node[] nodes = new Node[24];
    private final BinaryHeap openSet = new BinaryHeap();

    /** 노드가 이미 세워졌는가(바닐라 {@code nodes[0] != null}). */
    public boolean built() {
        return nodes[0] != null;
    }

    public Node node(int index) {
        return nodes[index];
    }

    /**
     * {@code findClosestNode()} 첫 호출의 노드 생성: 바깥 12개 반지름 60(y 기준 +5), 가운데 8개 반지름 40
     * (+15), 안쪽 4개 반지름 20(+5). 높이는 {@code max(73, heightmap(MOTION_BLOCKING_NO_LEAVES) + 기준)}.
     */
    public void build(DragonWorld world) {
        for (int i = 0; i < 24; i++) {
            int yAdd = 5;
            int ring = i;
            int nx;
            int nz;
            if (i < 12) {
                nx = DragonMath.floor(60.0F * DragonMath.cos(2.0F * (-(float) Math.PI + 0.2617994F * ring)));
                nz = DragonMath.floor(60.0F * DragonMath.sin(2.0F * (-(float) Math.PI + 0.2617994F * ring)));
            } else if (i < 20) {
                ring -= 12;
                nx = DragonMath.floor(40.0F * DragonMath.cos(2.0F * (-(float) Math.PI + 0.3926991F * ring)));
                nz = DragonMath.floor(40.0F * DragonMath.sin(2.0F * (-(float) Math.PI + 0.3926991F * ring)));
                yAdd += 10;
            } else {
                ring -= 20;
                nx = DragonMath.floor(20.0F * DragonMath.cos(2.0F * (-(float) Math.PI + 0.7853982F * ring)));
                nz = DragonMath.floor(20.0F * DragonMath.sin(2.0F * (-(float) Math.PI + 0.7853982F * ring)));
            }
            int ny = Math.max(73, world.heightNoLeaves(nx, nz) + yAdd);
            nodes[i] = new Node(nx, ny, nz);
        }
    }

    /** 영속 복원: 저장된 노드 좌표를 그대로 쓴다(지형이 바뀌어도 바닐라처럼 첫 계산값을 유지한다). */
    public void restore(int[] coordinates) {
        if (coordinates == null || coordinates.length != 72) throw new IllegalArgumentException("24 nodes");
        for (int i = 0; i < 24; i++) {
            nodes[i] = new Node(coordinates[i * 3], coordinates[i * 3 + 1], coordinates[i * 3 + 2]);
        }
    }

    public int[] coordinates() {
        if (!built()) return null;
        int[] out = new int[72];
        for (int i = 0; i < 24; i++) {
            out[i * 3] = nodes[i].x;
            out[i * 3 + 1] = nodes[i].y;
            out[i * 3 + 2] = nodes[i].z;
        }
        return out;
    }

    /**
     * {@code findClosestNode(x, y, z)}: 수정이 하나도 없으면(또는 싸움이 없으면) 안쪽 12..23 만 본다.
     * {@code aliveCrystals} 가 -1 이면 싸움이 없다는 뜻이다(바닐라 {@code dragonFight == null}).
     */
    public int findClosestNode(double x, double y, double z, int aliveCrystals) {
        float best = 10000.0F;
        int bestIndex = 0;
        Node probe = new Node(DragonMath.floor(x), DragonMath.floor(y), DragonMath.floor(z));
        int start = aliveCrystals <= 0 ? 12 : 0;
        for (int i = start; i < 24; i++) {
            if (nodes[i] == null) continue;
            float distance = nodes[i].distanceToSqr(probe);
            if (distance < best) {
                best = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * {@code findPath(from, to, finalNode)} — 바닐라 A*. 인접은 {@code NODE_ADJACENCY}, 수정이 없으면 0..11 을
     * 건너뛴다. {@code finalNode} 가 있으면 도착 뒤 한 칸(플레이어 머리 위 등)을 덧붙인다.
     */
    public Path findPath(int fromIndex, int toIndex, Node finalNode, int aliveCrystals) {
        for (int i = 0; i < 24; i++) {
            Node node = nodes[i];
            node.closed = false;
            node.f = 0.0F;
            node.g = 0.0F;
            node.h = 0.0F;
            node.cameFrom = null;
            node.heapIdx = -1;
        }
        Node from = nodes[fromIndex];
        Node to = nodes[toIndex];
        from.g = 0.0F;
        from.h = from.distanceTo(to);
        from.f = from.h;
        openSet.clear();
        openSet.insert(from);
        Node closest = from;
        int minimum = aliveCrystals <= 0 ? 12 : 0;
        while (!openSet.isEmpty()) {
            Node current = openSet.pop();
            if (current.sameCell(to)) {
                if (finalNode != null) {
                    finalNode.cameFrom = to;
                    to = finalNode;
                }
                return reconstructPath(to);
            }
            if (current.distanceTo(to) < closest.distanceTo(to)) closest = current;
            current.closed = true;
            int currentIndex = 0;
            for (int i = 0; i < 24; i++) {
                if (nodes[i] == current) {
                    currentIndex = i;
                    break;
                }
            }
            for (int i = minimum; i < 24; i++) {
                if ((NODE_ADJACENCY[currentIndex] & 1 << i) <= 0) continue;
                Node neighbor = nodes[i];
                if (neighbor.closed) continue;
                float tentative = current.g + current.distanceTo(neighbor);
                if (neighbor.inOpenSet() && !(tentative < neighbor.g)) continue;
                neighbor.cameFrom = current;
                neighbor.g = tentative;
                neighbor.h = neighbor.distanceTo(to);
                if (neighbor.inOpenSet()) {
                    openSet.changeCost(neighbor, neighbor.g + neighbor.h);
                } else {
                    neighbor.f = neighbor.g + neighbor.h;
                    openSet.insert(neighbor);
                }
            }
        }
        if (closest == from) return null;
        if (finalNode != null) {
            finalNode.cameFrom = closest;
            closest = finalNode;
        }
        return reconstructPath(closest);
    }

    private static Path reconstructPath(Node end) {
        List<Node> list = new ArrayList<>();
        Node node = end;
        list.add(0, node);
        while (node.cameFrom != null) {
            node = node.cameFrom;
            list.add(0, node);
        }
        return new Path(list);
    }
}
