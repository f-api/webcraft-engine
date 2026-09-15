package com.gameexpert.world.dimension;

import com.gameexpert.terrain.Blocks;

/** 일반 월드 exact-carrier 코드가 아닌 현재 엔진 block/state 평면이다. */
public final class DimensionChunk {
    private final short[] blocks;
    private final byte[] states;

    public DimensionChunk(short[] blocks, byte[] states) {
        if (blocks == null || states == null || blocks.length != Blocks.CHUNK_BLOCKS
                || states.length != Blocks.CHUNK_BLOCKS) {
            throw new IllegalArgumentException("complete dimension chunk planes required");
        }
        this.blocks = blocks.clone();
        this.states = states.clone();
        for (int index = 0; index < this.blocks.length; index++) {
            int block = Short.toUnsignedInt(this.blocks[index]);
            if (!Blocks.isWorldBlockId(block)) {
                throw new IllegalArgumentException("unregistered dimension block: " + block);
            }
            if (block == Blocks.AIR && this.states[index] != 0) {
                throw new IllegalArgumentException("AIR has no dimension block state");
            }
        }
    }

    public short[] blocks() { return blocks.clone(); }
    public byte[] states() { return states.clone(); }
    public int blockAt(int index) { return Short.toUnsignedInt(blocks[index]); }
    public int stateAt(int index) { return Byte.toUnsignedInt(states[index]); }
}
