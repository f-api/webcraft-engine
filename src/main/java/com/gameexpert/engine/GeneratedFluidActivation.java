package com.gameexpert.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.gameexpert.terrain.Blocks;

/** Seeds exposed generated fluid after structure/FTIK settlement, never on a mere terrain read. */
public final class GeneratedFluidActivation {
    public enum Edge { WEST, EAST, NORTH, SOUTH }
    @FunctionalInterface public interface Visitor {
        void accept(int x, int y, int z, int blockType);
    }
    public interface World {
        /** Negative means unavailable, not air. */
        int lookup(int x, int y, int z);
        void visit(int chunkX, int chunkZ, Edge edge, Visitor visitor);
        void activate(int x, int y, int z, int blockType);
    }
    private static final int[][] FACES = {{0,-1,0},{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,1,0}};
    private final World world;
    private final Map<Long, Set<Integer>> ready = new HashMap<>();

    public GeneratedFluidActivation(World world) { this.world = world; }

    public void onChunkReady(int cx, int cz, Collection<Integer> exactFluidIndexes) {
        if (ready.putIfAbsent(key(cx,cz), Set.copyOf(exactFluidIndexes)) != null) return;
        scan(cx,cz,null);
        scan(cx-1,cz,Edge.EAST);
        scan(cx+1,cz,Edge.WEST);
        scan(cx,cz-1,Edge.SOUTH);
        scan(cx,cz+1,Edge.NORTH);
    }

    public void onChunkEvicted(int cx, int cz) { ready.remove(key(cx,cz)); }
    public void clear() { ready.clear(); }

    public static boolean faceNeedsTick(int source, int neighbor, boolean flowFace) {
        if (neighbor < 0) return false;
        return flowFace && Fluids.isReplaceable(neighbor)
                || Fluids.isWater(source) && Fluids.isLava(neighbor)
                || Fluids.isLava(source) && Fluids.isWater(neighbor);
    }

    private void scan(int cx, int cz, Edge edge) {
        Set<Integer> exact = ready.get(key(cx,cz));
        if (exact == null) return;
        world.visit(cx,cz,edge,(x,y,z,block)-> {
            if (!Fluids.isFluid(block) || exact.contains(Blocks.blockIndex(x-cx*16,y,z-cz*16))) return;
            for (int[] face : FACES) {
                int nx=x+face[0], ny=y+face[1], nz=z+face[2];
                if (ny<Blocks.MIN_Y || ny>Blocks.MAX_Y
                        || !ready.containsKey(key(Math.floorDiv(nx,16),Math.floorDiv(nz,16)))) continue;
                if (faceNeedsTick(block,world.lookup(nx,ny,nz),face[1]!=1)) {
                    world.activate(x,y,z,block);
                    return;
                }
            }
        });
    }

    private static long key(int x,int z) { return ((long)x<<32) ^ (z&0xffff_ffffL); }
}
