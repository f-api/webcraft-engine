package com.gameexpert.engine.redstone;

import static com.gameexpert.terrain.Blocks.*;
import static com.gameexpert.engine.redstone.RedstoneState.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** [REDSTONE] Pinned collision and selection AABBs, in model pixels. */
public final class RedstoneShapes {
    private RedstoneShapes() {}
    private static final double[][] FULL = {{0,0,0,16,16,16}}, EMPTY = {};
    private static final Map<Integer,double[][]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean nonCollision(int id) {
        return isRedstoneTorch(id) || isButton(id) || isPressurePlate(id) || isRedstoneRail(id)
            || id == REDSTONE_WIRE || id == LEVER || id == TRIPWIRE || id == TRIPWIRE_HOOK_BLOCK || id == MOVING_PISTON;
    }
    public static boolean has(int id) {
        return nonCollision(id) || isPistonBase(id) || id == PISTON_HEAD || id == REPEATER || id == COMPARATOR || id == DAYLIGHT_DETECTOR;
    }
    public static double[][] collision(int id, int state) {
        int key = id * 256 + (state & 255);
        return CACHE.computeIfAbsent(key, ignored -> collisionUncached(id, state));
    }
    private static double[][] collisionUncached(int id, int state) {
        if (nonCollision(id)) return EMPTY;
        if (id == REPEATER || id == COMPARATOR) return new double[][] {{0,0,0,16,2,16}};
        if (id == DAYLIGHT_DETECTOR) return new double[][] {{0,0,0,16,6,16}};
        if (isPistonBase(id)) return (state & 8) == 0 ? FULL : new double[][] {rotate(new double[]{0,0,4,16,16,16}, state & 7)};
        if (id == PISTON_HEAD) return new double[][] {rotate(new double[]{0,0,0,16,16,4}, state & 7), rotate(new double[]{6,6,4,10,10,(state & 16) != 0 ? 16 : 20}, state & 7)};
        return FULL;
    }
    public static double[][] outline(int id, int state) {
        if (id == MOVING_PISTON) return EMPTY;
        if (isRedstoneRail(id)) return new double[][]{{0,0,0,16,(state & 7) >= 2 && (state & 7) <= 5 ? 8 : 2,16}};
        if (isPressurePlate(id)) return new double[][]{{1,0,1,15,(state & 1) != 0 ? .5 : 1,15}};
        if (id == REDSTONE_WIRE) {
            var boxes = new ArrayList<double[]>(); boxes.add(new double[]{3,0,3,13,1,13});
            for(int f=0;f<4;f++) if((state & 1 << f)!=0) boxes.add(rotate(new double[]{3,0,0,13,1,8},f));
            return boxes.toArray(double[][]::new);
        }
        if (id == TRIPWIRE) return new double[][]{{0,(state & 2)!=0 ? 1:0,0,16,(state & 2)!=0 ? 2.5:8,16}};
        if (id == TRIPWIRE_HOOK_BLOCK) return new double[][]{rotate(new double[]{5,0,10,11,10,16},state & 3)};
        if (isWallRedstoneTorch(id)) return new double[][]{rotate(new double[]{5.5,3,11,10.5,13,16},state & 3)};
        if (isRedstoneTorch(id)) return new double[][]{{6,0,6,10,10,10}};
        if (id == LEVER || isButton(id)) {
            boolean lever = id == LEVER; double depth=lever ? 6:(state & 1)!=0 ? 1:2, low=lever ? 4:6, high=lever ? 12:10;
            int face=attachFace(state);
            double[] box=face==ATTACH_FACE_WALL ? new double[]{5,low,16-depth,11,high,16}:face==ATTACH_FACE_FLOOR ? new double[]{5,0,low,11,depth,high}:new double[]{5,16-depth,low,11,16,high};
            return new double[][]{rotate(box,attachFacing(state))};
        }
        return collision(id,state);
    }
    public static double top(int id,int state) { double top=0; for(double[] box:collision(id,state)) top=Math.max(top,box[4]/16);return top; }
    public static double bottom(int id,int state) { double bottom=1; for(double[] box:collision(id,state)) bottom=Math.min(bottom,box[1]/16);return bottom; }
    public static boolean full(int id,int state) { return isPistonBase(id) && (state & 8)==0; }
    private static double[] rotate(double[] box,int facing) {
        double ax=Double.POSITIVE_INFINITY, ay=ax, az=ax,bx=Double.NEGATIVE_INFINITY,by=bx,bz=bx;
        for(double x:new double[]{box[0],box[3]}) for(double y:new double[]{box[1],box[4]}) for(double z:new double[]{box[2],box[5]}) {
            double px=x,py=y,pz=z;
            if(facing==1){px=16-z;pz=x;} else if(facing==2){px=16-x;pz=16-z;} else if(facing==3){px=z;pz=16-x;} else if(facing==4){py=16-z;pz=y;} else if(facing==5){py=z;pz=16-y;}
            ax=Math.min(ax,px);ay=Math.min(ay,py);az=Math.min(az,pz);bx=Math.max(bx,px);by=Math.max(by,py);bz=Math.max(bz,pz);
        }
        return new double[]{ax,ay,az,bx,by,bz};
    }
}
