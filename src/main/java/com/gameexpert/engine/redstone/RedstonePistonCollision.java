package com.gameexpert.engine.redstone;

import com.gameexpert.engine.BuildingBlockRules.CollisionBoxVisitor;

/** [REDSTONE] Axis clip against ordinary voxel collision boxes, without replacing entity physics. */
public final class RedstonePistonCollision {
    private RedstonePistonCollision() {}
    @FunctionalInterface public interface Cells {
        void boxes(int x, int y, int z, CollisionBoxVisitor visitor);
    }
    public static double clip(double[] box, int axis, double delta, Cells cells) {
        double[] min = {box[0],box[1],box[2]}, max = {box[3],box[4],box[5]};
        double[] low = min.clone(), high = max.clone();
        low[axis] += Math.min(0,delta); high[axis] += Math.max(0,delta);
        double[] clipped = {delta};
        for(int x=(int)Math.floor(low[0])-1;x<=(int)Math.floor(high[0])+1;x++)
            for(int y=(int)Math.floor(low[1])-1;y<=(int)Math.floor(high[1])+1;y++)
                for(int z=(int)Math.floor(low[2])-1;z<=(int)Math.floor(high[2])+1;z++) {
                    final int cx=x,cy=y,cz=z;
                    cells.boxes(x,y,z,(ax,ay,az,bx,by,bz)->{
                        double[] lo={cx+ax,cy+ay,cz+az},hi={cx+bx,cy+by,cz+bz};
                        for(int cross=0;cross<3;cross++) if(cross!=axis && (max[cross]<=lo[cross]+1e-7 || min[cross]>=hi[cross]-1e-7)) return;
                        if(delta>0 && max[axis]<=lo[axis]+1e-7) clipped[0]=Math.min(clipped[0],Math.max(0,lo[axis]-max[axis]));
                        if(delta<0 && min[axis]>=hi[axis]-1e-7) clipped[0]=Math.max(clipped[0],Math.min(0,hi[axis]-min[axis]));
                    });
                }
        return clipped[0];
    }
}
