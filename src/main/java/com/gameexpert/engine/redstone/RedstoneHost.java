package com.gameexpert.engine.redstone;

/**
 * [REDSTONE] 레드스톤 엔진이 권위에 요구하는 포트. {@code client/src/backend/standalone/redstone/RedstoneHost.ts}
 * 와 같은 모양이다. 엔진은 이 포트로만 월드를 읽고, 자기 쓰기는 오버레이에 쌓아 두었다가 세션 끝에
 * {@link RedstoneEngine#drainWrites()} 로 한 번에 넘긴다(Spring 은 런타임 쓰기 깔때기, 정적판은 한 번의 chunks.apply).
 */
public interface RedstoneHost {
    /** 셀을 읽을 수 없음(비상주 청크). 엔진은 공기처럼 읽고 절대 쓰지 않는다. */
    int REDSTONE_UNAVAILABLE = -1;

    /** 바닐라 PushReaction. */
    int PUSH_NORMAL = 0;
    int PUSH_DESTROY = 1;
    int PUSH_BLOCK = 2;
    int PUSH_IGNORE = 3;
    int PUSH_ONLY = 4;

    /** 감압판·철사 덫이 세는 엔티티 종류. */
    int ENTITY_FILTER_EVERYTHING = 0;
    int ENTITY_FILTER_LIVING = 1;

    /** 셀의 블록 ID. 비상주면 {@link #REDSTONE_UNAVAILABLE}. */
    int block(int x, int y, int z);

    /** 셀의 상태 바이트(0..255). */
    int state(int x, int y, int z, int blockType);

    // ── 물성(양 권위 RedstoneRules 가 같은 값을 낸다) ──
    boolean isConductor(int blockType, int state);

    /** {@code isFaceSturdy(face, FULL)}. face 는 DIR. */
    boolean isFaceSturdy(int blockType, int state, int face);

    /** {@code isFaceSturdy(UP, RIGID)}(다이오드·레일 받침). */
    boolean isRigidTop(int blockType, int state);

    /** {@code canSupportCenter(face)}(바닥 횃불·감압판 받침). */
    boolean canSupportCenter(int blockType, int state, int face);

    int pushReaction(int blockType, int state);

    /** 피스톤이 옮기지 못하는 블록 엔티티 보유 블록. */
    boolean hasBlockEntity(int blockType);

    /** {@code getDestroySpeed == -1}(파괴 불가). */
    boolean isUnbreakable(int blockType);

    /** 비교기가 읽는 아날로그 출력이 있는 블록인가. */
    boolean hasAnalogOutput(int blockType, int state);

    int analogOutput(int x, int y, int z, int direction);

    /** 교체 가능한 비고체(물·풀 등)가 떠밀릴 때 남는 블록(공기 또는 물). */
    int fluidAfterRemoval(int x, int y, int z, int blockType, int state);

    // ── 효과 ──
    /** 블록이 부서져 자원을 떨군다(피스톤 파괴·지지 상실). */
    void dropResources(int x, int y, int z, int blockType, int state);

    /** TNT 점화. 성공하면 엔진이 그 칸을 지운다. */
    boolean primeTnt(int x, int y, int z);

    void ringBell(int x, int y, int z);

    /** 소리 블록 연주. 악기는 위·아래 블록에서 권위가 정한다. */
    void playNote(int x, int y, int z, int note);

    /** 월드 음향 이벤트. */
    void sound(String kind, int x, int y, int z, double pitch);

    /** 피스톤 이동 애니메이션 이벤트. */
    void pistonMove(RedstonePistonMove move);

    /** 피스톤 이동 칸을 지나는 엔티티를 민다(바닐라 moveCollidedEntities). */
    void pushEntities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            int moveDir, double amount, boolean bounce);

    /**
     * 권위 규칙이 소유하는 소비 블록(발사기·공급기·제작기·호퍼·선반)이 바닐라 neighborChanged 를 받았다.
     * 권위는 자기 신호 이웃 큐에 넣고, 그 규칙은 엔진 {@code hasNeighborSignal} 로 신호를 읽는다.
     */
    void consumerNeighborChanged(int x, int y, int z, int blockType);

    /** 주크박스가 음반을 재생 중인가({@code JukeboxBlock.ownSignal}). */
    boolean jukeboxPlaying(int x, int y, int z);

    // ── 엔티티 ──
    int countEntities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int filter);

    boolean hasArrow(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    /** 상자와 겹치는 광차 수(감지 레일). */
    int countMinecarts(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    // ── 환경 ──
    /** {@code getEffectiveSkyBrightness}(원시 하늘빛 − skyDarken). */
    int effectiveSkyBrightness(int x, int y, int z);

    /** {@code EnvironmentAttributes.SUN_ANGLE}(도). */
    double sunAngleDegrees();

    boolean hasSkyLight();

    /** 덫 상자를 연 플레이어 수. */
    int chestViewers(int x, int y, int z);
}
