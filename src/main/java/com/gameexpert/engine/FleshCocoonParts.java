package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.List;

/** Matching resident segments only; a broken upper remnant cannot mint another reward. */
public final class FleshCocoonParts {
    private FleshCocoonParts() { }
    @lombok.Getter @lombok.experimental.Accessors(fluent = true) @lombok.RequiredArgsConstructor
    public static final class Parts {
        private final List<BlockPos> cells;
        private final boolean reward;
    }
    public static Parts resolve(int x, int y, int z, int state,
            Fluids.BlockLookup blocks, Fluids.BlockLookup states) {
        int segment = state >>> 2, base = y - segment, stage = state & 3;
        List<BlockPos> cells = new ArrayList<>();
        if (segment > 2 || stage > 2) return new Parts(List.of(), false);
        boolean reward = false;
        for (int i = 0; i < 3; i++) {
            if (blocks.get(x, base + i, z) == Blocks.FLESH_LARGE_COCOON
                    && states.get(x, base + i, z) == (stage | i << 2)) {
                cells.add(new BlockPos(x, base + i, z));
                if (i == 0) reward = true;
            }
        }
        return new Parts(List.copyOf(cells), reward);
    }
    public static List<BlockPos> guardianWitnesses(int x,int y,int z) {
        List<BlockPos> result=new ArrayList<>();
        for(int dy=0;dy<2;dy++)for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)
            result.add(new BlockPos(x+dx,y+dy,z+dz));
        result.add(new BlockPos(x,y+2,z));
        return List.copyOf(result);
    }
    public static boolean guardianClearance(int x,int y,int z,Fluids.BlockLookup blocks,Fluids.BlockLookup states) {
        if(resolve(x,y,z,2,blocks,states).cells().size()!=3)return false;
        for(var p:guardianWitnesses(x,y,z)) {
            if(p.x()==x&&p.z()==z) {
                if(blocks.get(p.x(),p.y(),p.z())!=Blocks.FLESH_LARGE_COCOON
                        ||states.get(p.x(),p.y(),p.z())!=(2|(p.y()-y)<<2))return false;
            } else if(blocks.get(p.x(),p.y(),p.z())!=Blocks.AIR)return false;
        }
        return true;
    }
}
