package com.gameexpert.engine.dragon;

import com.gameexpert.terrain.Blocks;

/**
 * [DRAGON] 바닐라 {@code DragonEggBlock.teleport}(핀 26.3 javap): 1000 번까지 {@code pos + (nextInt(16) − nextInt(16),
 * nextInt(8) − nextInt(8), nextInt(16) − nextInt(16))} 을 뽑아 그 칸이 공기이고 아래 칸이 공기가 아니며 건축 높이
 * 안이면, level event 2015(data = (dx+16)&255 &lt;&lt; 16 | (dy+8)&255 &lt;&lt; 8 | (dz+16)&255)를 낸 뒤 알을 옮긴다.
 * 때리기({@code attack})·쓰기({@code useWithoutItem}) 모두 같다. 정적판 {@code DragonEggRules.ts} 와 같은 식이다.
 */
public final class DragonEggRules {
    private DragonEggRules() {
    }

    public interface Random {
        int nextInt(int bound);
    }

    public interface Lookup {
        /** 블록 ID(공기 0), 비상주면 음수. */
        int block(int x, int y, int z);
    }

    /** 옮길 자리 {@code {x, y, z, data}}, 1000 번 안에 못 찾으면 null. */
    public static int[] teleportTarget(Random random, Lookup lookup, int x, int y, int z) {
        for (int attempt = 0; attempt < 1000; attempt++) {
            int dx = random.nextInt(16) - random.nextInt(16);
            int dy = random.nextInt(8) - random.nextInt(8);
            int dz = random.nextInt(16) - random.nextInt(16);
            int tx = x + dx;
            int ty = y + dy;
            int tz = z + dz;
            if (ty < Blocks.MIN_Y || ty > Blocks.MAX_Y) continue;
            if (lookup.block(tx, ty, tz) != Blocks.AIR) continue;
            int below = lookup.block(tx, ty - 1, tz);
            if (below == Blocks.AIR || below < 0) continue;
            int data = (dx + 16 & 255) << 16 | (dy + 8 & 255) << 8 | dz + 16 & 255;
            return new int[] {tx, ty, tz, data};
        }
        return null;
    }
}
