package com.gameexpert.engine.redstone;

import static com.gameexpert.terrain.Blocks.*;

import com.gameexpert.engine.BlockFamilies;

/** [REDSTONE] 권위 입출력 포트와 순수 신호 엔진 사이의 어댑터. 정적판 StandaloneRedstone과 같은 물성·음향·하늘 계산. */
public final class RuntimeRedstoneHost implements RedstoneHost {
    public interface Port {
      /** `(state << 16) | blockType`, 비상주는 음수. */
      int packedAt(int x, int y, int z);
      int analogOutputAt(int x, int y, int z, int direction);
      int fluidAfterRemoval(int x, int y, int z, int blockType, int state);
      void dropBlock(int x, int y, int z, int blockType, int state);
      boolean primeTnt(int x, int y, int z);
      void ringBell(int x, int y, int z);
      void worldSound(String kind, int x, int y, int z, int blockType, double pitch);
      void pistonMove(RedstonePistonMove move);
      void pushEntities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int moveDir, double amount, boolean bounce);
      void consumerNeighborChanged(int x, int y, int z, int blockType);
      boolean jukeboxPlaying(int x, int y, int z);
      int countEntities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        boolean livingOnly);
      boolean hasArrow(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
      int countMinecarts(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
      int rawSkyLight(int x, int y, int z);
      /** 바닐라 dayTime(0..23999). */
      long dayTime();
      double rainLevel();
      double thunderLevel();
      boolean hasSkyLight();
      int chestViewers(int x, int y, int z);
    }

    private final Port port;
    private final RedstoneEngine engine;

    public RuntimeRedstoneHost(Port port) {
        this.port = port;
        this.engine = new RedstoneEngine(this);
    }

    public RedstoneEngine engine() { return engine; }

    @Override public int block(int x, int y, int z) {
        int packed = port.packedAt(x, y, z);
        return packed < 0 ? REDSTONE_UNAVAILABLE : packed & 0xffff;
    }
    @Override public int state(int x, int y, int z, int blockType) {
        int packed = port.packedAt(x, y, z);
        return packed < 0 ? 0 : (packed >>> 16) & 0xff;
    }
    @Override public boolean isConductor(int id, int state) { return RedstoneRules.isConductor(id, state); }
    @Override public boolean isFaceSturdy(int id, int state, int face) { return RedstoneRules.isFaceSturdy(id, state, face); }
    @Override public boolean isRigidTop(int id, int state) { return RedstoneRules.isRigidTop(id, state); }
    @Override public boolean canSupportCenter(int id, int state, int face) { return RedstoneRules.canSupportCenter(id, state, face); }
    @Override public int pushReaction(int id, int state) { return RedstoneRules.pushReaction(id, state); }
    @Override public boolean hasBlockEntity(int id) { return RedstoneRules.hasBlockEntity(id); }
    @Override public boolean isUnbreakable(int id) { return RedstoneRules.isUnbreakable(id); }
    @Override public boolean hasAnalogOutput(int id, int state) { return RedstoneRules.hasAnalogOutput(id); }
    @Override public int analogOutput(int x, int y, int z, int direction) { return port.analogOutputAt(x, y, z, direction); }
    @Override public int fluidAfterRemoval(int x, int y, int z, int id, int state) { return port.fluidAfterRemoval(x, y, z, id, state); }
    @Override public void dropResources(int x, int y, int z, int id, int state) { port.dropBlock(x, y, z, id, state); }
    @Override public boolean primeTnt(int x, int y, int z) { return port.primeTnt(x, y, z); }
    @Override public void ringBell(int x, int y, int z) { port.ringBell(x, y, z); }
    @Override public void playNote(int x, int y, int z, int note) {
        port.worldSound("note_block_" + noteBlockInstrument(engine.blockAt(x, y - 1, z)),
                x, y, z, NOTE_BLOCK, (float) Math.pow(2, (note - 12) / 12.0));
    }
    @Override public void sound(String kind, int x, int y, int z, double pitch) {
        port.worldSound(kind, x, y, z, engine.blockAt(x, y, z), pitch);
    }
    @Override public void pistonMove(RedstonePistonMove move) { port.pistonMove(move); }
    @Override public void pushEntities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            int moveDir, double amount, boolean bounce) {
        port.pushEntities(minX, minY, minZ, maxX, maxY, maxZ, moveDir, amount, bounce);
    }
    @Override public void consumerNeighborChanged(int x, int y, int z, int id) { port.consumerNeighborChanged(x, y, z, id); }
    @Override public boolean jukeboxPlaying(int x, int y, int z) { return port.jukeboxPlaying(x, y, z); }
    @Override public int countEntities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int filter) {
        return port.countEntities(minX, minY, minZ, maxX, maxY, maxZ, filter != ENTITY_FILTER_EVERYTHING);
    }
    @Override public boolean hasArrow(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return port.hasArrow(minX, minY, minZ, maxX, maxY, maxZ);
    }
    @Override public int countMinecarts(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return port.countMinecarts(minX, minY, minZ, maxX, maxY, maxZ);
    }
    @Override public int effectiveSkyBrightness(int x, int y, int z) {
        return Math.max(0, port.rawSkyLight(x, y, z) - VanillaSkyTimeline.vanillaSkyDarken(
                port.dayTime(), port.rainLevel(), port.thunderLevel()));
    }
    @Override public double sunAngleDegrees() { return VanillaSkyTimeline.vanillaSunAngleDegrees(port.dayTime()); }
    @Override public boolean hasSkyLight() { return port.hasSkyLight(); }
    @Override public int chestViewers(int x, int y, int z) { return port.chestViewers(x, y, z); }

    public static String noteBlockInstrument(int below) {
      if (below == GOLD_BLOCK) return "bell";
      if (below == CLAY) return "flute";
      if (below == PACKED_ICE) return "chime";
      if (below >= WHITE_WOOL && below <= WHITE_WOOL + 15) return "guitar";
      if (below == BONE_BLOCK) return "xylophone";
      if (below == IRON_BLOCK) return "iron_xylophone";
      if (below == SOUL_SAND) return "cow_bell";
      if (below == PUMPKIN || below == CARVED_PUMPKIN || below == JACK_O_LANTERN) return "didgeridoo";
      if (below == EMERALD_BLOCK) return "bit";
      if (below == HAY_BLOCK) return "banjo";
      if (below == GLOWSTONE) return "pling";
      if (isGlassBlock(below) || below == SEA_LANTERN || below == BEACON) return "hat";
      if (below == SAND || below == RED_SAND || below == GRAVEL || isConcretePowder(below)) return "snare";
      if (isPlankBlock(below) || isWoodStairs(below) || isWoodSlab(below) || BlockFamilies.isWoodLog(below)
        || isStrippedLog(below) || isFence(below) || below == BOOKSHELF || below == CRAFTING_TABLE
        || below == NOTE_BLOCK || below == JUKEBOX || below == CHEST || below == TRAPPED_CHEST
        || below == BARREL) return "bass";
      if (RedstoneRules.isConductor(below, 0) && below != AIR) return "basedrum";
      return "harp";
    }

}
