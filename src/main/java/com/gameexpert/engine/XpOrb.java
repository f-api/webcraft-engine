package com.gameexpert.engine;

/**
 * [SURV-X] 서버 권위 경험치 구슬 엔티티. 채굴·제련 회수·몹 처치·번식·사망 드랍이 이 엔티티로 스폰되고,
 * 중력·지면 안착·자석 흡인·병합·근접 획득·수명을 {@link XpOrbSystem} 이 매 틱 처리한다.
 *
 * <p>좌표/속도 단위는 블록/틱이며 위치는 구슬 중심의 점 근사다. age 는 스폰 후 경과 틱 수로,
 * 근접 획득 유예와 수명 판정에 쓴다. 필드 구성은 {@link ItemEntity} 를 그대로 따른다.
 */
final class XpOrb {
    long id;
    /** 이 구슬이 지급할 경험치. 병합하면 합산된다. */
    int amount;
    double x, y, z;
    double vx, vy, vz;
    int age;

    // 마지막으로 브로드캐스트한 위치(xpOrbUpdates 변경분 판정용).
    double lastX, lastY, lastZ;

    XpOrb(int amount, double x, double y, double z, double vx, double vy, double vz) {
        this.amount = amount;
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
        this.lastX = x; this.lastY = y; this.lastZ = z;
    }
}
