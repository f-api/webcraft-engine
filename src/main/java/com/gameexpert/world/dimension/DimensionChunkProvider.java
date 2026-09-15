package com.gameexpert.world.dimension;

import java.util.List;

/** 부작용 없는 결정론적 사용자 차원 공급자. 월드 저장/몹 생성/정본 receipt를 만들지 않는다. */
@FunctionalInterface
public interface DimensionChunkProvider {
    DimensionChunk generate(int seed, int chunkX, int chunkZ);

    /**
     * [DIMENSION-EXT] 콘텐츠가 정하는 도착 공간 재구성. 권위는 이동 준비 중 안전 검사 직전에 각 칸을
     * 현재 블록과 비교해 다르면 기존 블록을 드랍과 함께 부수고 이 블록(상태 0)으로 바꾼다(바닐라
     * {@code EndPlatformFeature.createEndPlatform(level, pos, true)} 의 일반화). 기본은 없음.
     */
    default List<Cell> arrivalCells(int seed) {
        return List.of();
    }

    /**
     * [DIMENSION-EXT] 콘텐츠 소유 초기 컨테이너. 권위는 해당 칸이 생성된 그 블록 그대로이고 플레이어
     * 편집·기존 컨테이너 행이 없을 때만 처음 열기(파괴 드랍 포함)에 한 번 채우고 일반 상자 행으로
     * 영속한다. 정본 LOOT 경로와 섞지 않는다. 기본은 없음.
     */
    default List<Container> initialContainers(int seed, int chunkX, int chunkZ) {
        return List.of();
    }

    /** [DIMENSION-EXT] 월드 좌표 한 칸과 목표 블록. */
    record Cell(int x, int y, int z, int blockType) {
    }

    /** [DIMENSION-EXT] 월드 좌표 컨테이너 한 개와 칸별 내용. */
    record Container(int x, int y, int z, int blockType, List<Slot> slots) {
        public Container {
            slots = List.copyOf(slots);
        }
    }

    /**
     * [DIMENSION-EXT] 컨테이너 칸 하나. [END-CITY] {@code durability} 0 은 새 아이템의 최대 내구, 인챈트 워드 0 과
     * 성분 문자열(확장 인챈트 WCIC4, 없으면 null)은 전리품 표가 굴린 값을 그대로 싣는다.
     */
    record Slot(int slot, int itemType, int count, int durability, long enchantments, String componentData) {
        public Slot(int slot, int itemType, int count) {
            this(slot, itemType, count, 0, 0L, null);
        }
    }

    /**
     * [END-CITY] 콘텐츠 소유 초기 개체(엔드 도시 셜커 · 겉날개 액자). 권위는 청크를 처음 채울 때(자연 스폰
     * 첫 판정과 같은 한 번) 이 목록을 개체로 만들고, 그 판정은 청크별로 영속하므로 다시 만들지 않는다.
     */
    default List<InitialMob> initialMobs(int seed, int chunkX, int chunkZ) {
        return List.of();
    }

    /**
     * [END-CITY] 초기 개체 하나: 몹 종류 이름(바닐라 id 경로), 위치, 방향(바닐라 Direction 3D 값, 셜커는 부착면,
     * 액자는 바라보는 방향), 들고 있는 아이템(액자 속 아이템, 없으면 0).
     */
    record InitialMob(String type, double x, double y, double z, int direction, int heldItem) {
    }
}
