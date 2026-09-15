package com.gameexpert.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 균일 공간 해시(포인트 → 정수 인덱스). 근접 질의의 <b>후보만</b> 근방 셀로 좁히는 도구이며,
 * 최종 거리/규칙 판정은 호출부가 원래 로직 그대로 수행한다. 따라서 산출 결과는 완전 불변이고,
 * 그리드는 오직 후보 축소(선형 스캔 → 근방 셀 스캔)만 담당한다.
 *
 * <p>버킷은 셀당 리스트 할당 없이 단일 연결 리스트({@code head} 맵 + {@code next[]} 체인)로 유지한다.
 * 좌표가 바뀌는 매 사용마다 {@link #reset(int)} 후 다시 채워 스테일 엔트리를 0 으로 보장한다.
 *
 * <p><b>주의</b>: 셀 키는 21/20/21 비트로 패킹하므로 아주 먼 좌표는 키가 충돌할 수 있으나,
 * 충돌은 "여분 후보"만 만들 뿐 호출부의 정확한 거리 재검증에 걸러지므로 결과에는 영향이 없다(성능만).
 */
final class SpatialGrid {

    /**
     * 근방 후보가 전체의 상당부분이면 후보 정렬이 선형 순회보다 비싸진다.
     * 이 경우는 원본 인덱스 순회로 돌아가 고밀도에서 O(n² log n)을 O(n²)로 제한한다.
     */
    static boolean preferLinearTraversal(int candidateCount, int totalCount) {
        return candidateCount >= 64 && (long) candidateCount * 8L >= totalCount;
    }

    /** 셀에 담긴 각 원소 인덱스를 순서 보장 없이 전달받는 소비자. */
    interface IntSink {
        void accept(int idx);
    }

    private final double inv;              // 1 / cellSize
    private int[] next = new int[0];       // 같은 셀의 다음 원소 인덱스(-1=종단)
    private final Map<Long, Integer> head = new HashMap<>();

    SpatialGrid(double cellSize) {
        this.inv = 1.0 / cellSize;
    }

    /** capacity 개 원소를 담을 수 있게 준비하고 모든 버킷을 비운다(재사용 시 스테일 제거). */
    void reset(int capacity) {
        if (next.length < capacity) {
            next = new int[capacity];
        }
        head.clear();
    }

    /** 인덱스 idx(0..capacity-1)의 원소를 좌표 (x,y,z) 가 속한 셀 버킷에 넣는다. */
    void add(int idx, double x, double y, double z) {
        long k = key(cell(x), cell(y), cell(z));
        Integer h = head.get(k);
        next[idx] = (h == null) ? -1 : h;
        head.put(k, idx);
    }

    /**
     * (x,y,z) 를 중심으로 반경 radius 를 덮는 모든 셀의 원소 인덱스를 sink 로 전달한다(순서 무보장).
     * radius 와 cellSize 의 대소에 무관하게 [p−r, p+r] 을 덮는 셀을 모두 훑으므로 근방 원소를 빠뜨리지 않는다.
     */
    void forEachNear(double x, double y, double z, double radius, IntSink sink) {
        int x0 = cell(x - radius), x1 = cell(x + radius);
        int y0 = cell(y - radius), y1 = cell(y + radius);
        int z0 = cell(z - radius), z1 = cell(z + radius);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    Integer h = head.get(key(cx, cy, cz));
                    for (int i = (h == null ? -1 : h); i >= 0; i = next[i]) {
                        sink.accept(i);
                    }
                }
            }
        }
    }

    private int cell(double v) {
        return (int) Math.floor(v * inv);
    }

    private static long key(int cx, int cy, int cz) {
        return ((long) (cx & 0x1FFFFF) << 41) | ((long) (cy & 0xFFFFF) << 21) | (long) (cz & 0x1FFFFF);
    }
}
