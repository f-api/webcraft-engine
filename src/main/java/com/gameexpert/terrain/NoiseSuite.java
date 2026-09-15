package com.gameexpert.terrain;

/**
 * WebCraft 노이즈 프리미티브 — 공유 계약(docs/CONTRACT.md) §5~§7 정본.
 * TS 측 client/src/world/Noise.ts 와 수식/괄호/연산 순서가 문자 그대로 동일해야 한다.
 *
 * 언어 매핑(§5):
 *   Math.imul(a,b)      → a * b      (int, 자연 오버플로)
 *   h >>> n             → h >>> n    (int)
 *   x | 0               → (int 타입이면 자동)
 *   Math.floor(a / b)   → Math.floorDiv(a, b)  (정수 나눗셈)
 * 모든 해시 중간값은 int(32비트). double 로 나가는 지점은 오직 val01/val11.
 */
public final class NoiseSuite {
    private NoiseSuite() {}

    // murmur3 finalizer
    public static int mix32(int h) {
        h ^= h >>> 16;
        h = h * 0x85EBCA6B;
        h ^= h >>> 13;
        h = h * 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    // 좌표 해시 (§6)
    public static int hash2(int s, int x, int z) {
        return mix32(s ^ x * 0x9E3779B1 ^ z * 0x85EBCA77);
    }

    public static int hash3(int s, int x, int y, int z) {
        return mix32(s ^ x * 0x9E3779B1 ^ y * 0xC2B2AE3D ^ z * 0x85EBCA77);
    }

    // 해시 → 실수 (§6). 상수는 반드시 double 표기.
    public static double val01(int h) {
        return ((h >>> 8) & 0xFFFFFF) / 16777216.0; // [0, 1)
    }

    public static double val11(int h) {
        return val01(h) * 2.0 - 1.0; // [-1, 1)
    }

    // 파생 시드 (§6)
    public static int S(int seed, int k) {
        return mix32(seed ^ k * 0x9E3779B9);
    }

    // splitmix32 난수 발생기 (§6)
    public static final class Rng {
        private int state;
        public Rng(int seed) {
            this.state = seed;
        }
        public int next() {
            this.state = this.state + 0x9E3779B9;
            return mix32(this.state);
        }
        public int rndInt(int n) {
            return (next() >>> 1) % n;
        }
    }

    // quintic fade (§7):  t³(t(6t−15)+10)
    public static double fade(double t) {
        return t * t * t * (t * (6 * t - 15) + 10);
    }

    // smoothstep ≡ fade (§8)
    public static double smoothstep(double t) {
        return fade(t);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double clampD(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    // 2D value noise. px/pz 는 이미 (좌표 * freq) 로 스케일된 double.
    public static double noise2(int s, double px, double pz) {
        int xi = (int) Math.floor(px);
        int zi = (int) Math.floor(pz);
        double fx = px - xi;
        double fz = pz - zi;
        double u = fade(fx);
        double v = fade(fz);
        double c00 = val11(hash2(s, xi, zi));
        double c10 = val11(hash2(s, xi + 1, zi));
        double c01 = val11(hash2(s, xi, zi + 1));
        double c11 = val11(hash2(s, xi + 1, zi + 1));
        double nx0 = lerp(c00, c10, u);
        double nx1 = lerp(c01, c11, u);
        return lerp(nx0, nx1, v);
    }

    // 3D value noise. px/py/pz 는 이미 (좌표 * freq) 로 스케일된 double.
    public static double noise3(int s, double px, double py, double pz) {
        int xi = (int) Math.floor(px);
        int yi = (int) Math.floor(py);
        int zi = (int) Math.floor(pz);
        double fx = px - xi;
        double fy = py - yi;
        double fz = pz - zi;
        double u = fade(fx);
        double v = fade(fy);
        double w = fade(fz);
        double c000 = val11(hash3(s, xi, yi, zi));
        double c100 = val11(hash3(s, xi + 1, yi, zi));
        double c010 = val11(hash3(s, xi, yi + 1, zi));
        double c110 = val11(hash3(s, xi + 1, yi + 1, zi));
        double c001 = val11(hash3(s, xi, yi, zi + 1));
        double c101 = val11(hash3(s, xi + 1, yi, zi + 1));
        double c011 = val11(hash3(s, xi, yi + 1, zi + 1));
        double c111 = val11(hash3(s, xi + 1, yi + 1, zi + 1));
        double x00 = lerp(c000, c100, u);
        double x10 = lerp(c010, c110, u);
        double x01 = lerp(c001, c101, u);
        double x11 = lerp(c011, c111, u);
        double y0 = lerp(x00, x10, v);
        double y1 = lerp(x01, x11, v);
        return lerp(y0, y1, w);
    }

    // fBm (§7). 통일된 시그니처: 좌표는 원본(raw)으로 넘기고 freq 파라미터로 곱함.
    public static double fbm2(int s, double x, double z, int octaves, double freq) {
        double amp = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        double f = freq;
        for (int i = 0; i < octaves; i++) {
            int si = mix32(s + i * 0x9E3779B9);
            sum += noise2(si, x * f, z * f) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }

    public static double fbm3(int s, double x, double y, double z, int octaves, double freq) {
        double amp = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        double f = freq;
        for (int i = 0; i < octaves; i++) {
            int si = mix32(s + i * 0x9E3779B9);
            sum += noise3(si, x * f, y * f, z * f) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return sum / norm;
    }

    // FNV-1a 32bit (§9).
    public static int fnv1a(byte[] bytes) {
        int h = 0x811C9DC5;
        for (int i = 0; i < bytes.length; i++) {
            h ^= (bytes[i] & 0xFF);
            h = h * 0x01000193;
        }
        return h;
    }

    public static String fnvHex(byte[] bytes) {
        return String.format("0x%08x", fnv1a(bytes));
    }

    /** Canonical u16 terrain FNV: block-index order, big-endian high byte then low byte. */
    public static int fnv1a(short[] blocks) {
        int hash = 0x811C9DC5;
        for (short block : blocks) {
            int value = Short.toUnsignedInt(block);
            hash = (hash ^ (value >>> 8)) * 0x01000193;
            hash = (hash ^ (value & 0xFF)) * 0x01000193;
        }
        return hash;
    }

    public static String fnvHex(short[] blocks) {
        return String.format("0x%08x", fnv1a(blocks));
    }
}
