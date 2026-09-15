package com.gameexpert.engine.mob;

/**
 * 중력 보정 조준: 고정 속력 s 로 원점→표적을 맞히는 초기 속도(블록/틱)를 구한다.
 * 발사체 중력 g 는 ProjectileSim 과 동일(0.05 블록/틱²). 저각(직사) 해를 택한다.
 *
 * 포물선: Δy = d·tanθ − (g·d²)/(2 s²)·(1+tan²θ).
 * A = g·d²/(2 s²) 로 두면  A·T² − d·T + (Δy + A) = 0  (T=tanθ).
 */
public final class Ballistics {
    private Ballistics() {}

    /**
     * @return {vx, vy, vz}(블록/틱) 또는 사거리 밖이면 null.
     */
    public static double[] solve(double sx, double sy, double sz,
                                 double tx, double ty, double tz,
                                 double speed, double gravity) {
        double dxh = tx - sx, dzh = tz - sz;
        double d = Math.sqrt(dxh * dxh + dzh * dzh);
        double dy = ty - sy;

        if (d < 1e-6) {
            // 표적이 바로 위/아래: 수직 발사.
            double vy = dy >= 0 ? speed : -speed;
            return new double[]{0, vy, 0};
        }

        double s2 = speed * speed;
        double a = gravity * d * d / (2.0 * s2);
        double disc = d * d - 4.0 * a * (dy + a);
        if (disc < 0) return null;                 // 사거리 밖

        double t = (d - Math.sqrt(disc)) / (2.0 * a);   // 저각(직사) 해
        double theta = Math.atan(t);
        double vHoriz = speed * Math.cos(theta);
        double vy = speed * Math.sin(theta);
        double ux = dxh / d, uz = dzh / d;
        return new double[]{vHoriz * ux, vy, vHoriz * uz};
    }
}
