package com.gameexpert.engine.mob.origin;

import com.gameexpert.engine.CopperAgeRules;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.terrain.Blocks;

/** Pure origin and block-transition plans for the Copper Golem. */
public final class CopperGolemOriginRules {

    public static final int FULLY_OXIDIZED_AGE = Blocks.COPPER_OXIDATION_STAGES - 1;

    private CopperGolemOriginRules() {
    }

    public interface BlockView {
        int blockAt(int x, int y, int z);
    }

    /**
     * Plans the two-block construction completed by placing a carved pumpkin or jack o'lantern
     * directly on an unwaxed, unoxidized copper block. The caller remains responsible for applying
     * both removals and the authoritative persistent spawn atomically.
     */
    public static ConstructionPlan constructionPlan(
            BlockView world, int headX, int headY, int headZ, int placedHead) {
        if (world == null || !isConstructionHead(placedHead) || headY <= Blocks.MIN_Y) return null;
        if (world.blockAt(headX, headY, headZ) != placedHead
                || world.blockAt(headX, headY - 1, headZ) != Blocks.COPPER_BLOCK) return null;
        return new ConstructionPlan(headX, headY, headZ);
    }

    public static boolean isConstructionHead(int blockId) {
        return blockId == Blocks.CARVED_PUMPKIN || blockId == Blocks.JACK_O_LANTERN;
    }

    /** A live, unwaxed golem becomes a statue only on reaching the final oxidation stage. */
    public static StatuePlan statuePlan(
            int oxidationAge, boolean waxed, int x, int y, int z, int poseState) {
        if (waxed || oxidationAge != FULLY_OXIDIZED_AGE) return null;
        return new StatuePlan(x, y, z, Blocks.OXIDIZED_COPPER_GOLEM_STATUE, poseState & 0xff);
    }

    /**
     * Scraping the last oxidation layer from an unwaxed base-stage statue revives the golem.
     * Other statue ages use {@link CopperAgeRules#scrapePlan(int, int)} and remain blocks.
     */
    public static RevivalPlan revivalPlan(int statueBlock, int x, int y, int z) {
        if (statueBlock != Blocks.COPPER_GOLEM_STATUE) return null;
        return new RevivalPlan(x, y, z);
    }

    public static final class ConstructionPlan {
        private final int x;
        private final int headY;
        private final int z;

        private ConstructionPlan(int x, int headY, int z) {
            this.x = x;
            this.headY = headY;
            this.z = z;
        }

        public MobType mobType() { return MobType.COPPER_GOLEM; }
        public int headX() { return x; }
        public int headY() { return headY; }
        public int headZ() { return z; }
        public int bodyX() { return x; }
        public int bodyY() { return headY - 1; }
        public int bodyZ() { return z; }
        public double spawnX() { return x + 0.5; }
        public double spawnY() { return headY - 1 + 0.05; }
        public double spawnZ() { return z + 0.5; }
    }

    public static final class StatuePlan {
        private final int x;
        private final int y;
        private final int z;
        private final int blockId;
        private final int state;

        private StatuePlan(int x, int y, int z, int blockId, int state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.state = state;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int blockId() { return blockId; }
        public int state() { return state; }
    }

    public static final class RevivalPlan {
        private final int x;
        private final int y;
        private final int z;

        private RevivalPlan(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public MobType mobType() { return MobType.COPPER_GOLEM; }
        public int statueX() { return x; }
        public int statueY() { return y; }
        public int statueZ() { return z; }
        public double spawnX() { return x + 0.5; }
        public double spawnY() { return y + 0.05; }
        public double spawnZ() { return z + 0.5; }
    }
}
