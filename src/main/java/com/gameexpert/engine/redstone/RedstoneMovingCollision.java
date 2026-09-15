package com.gameexpert.engine.redstone;

import com.gameexpert.engine.BuildingBlockRules;
import static com.gameexpert.engine.redstone.RedstoneState.*;
import static com.gameexpert.terrain.Blocks.*;

/** Moving shapes are clipped into every occupied cell, including the air behind their owner cell. */
public final class RedstoneMovingCollision {
    private RedstoneMovingCollision() {}
    public static void visit(Iterable<RedstoneMovingBlock> moving, int x, int y, int z,
            BuildingBlockRules.CollisionBoxVisitor visitor) {
        for (RedstoneMovingBlock m : moving) {
            if (Math.abs(m.x-x)>2 || Math.abs(m.y-y)>2 || Math.abs(m.z-z)>2) continue;
            if (!m.extending && m.source && isPistonBase(m.movedBlock))
                boxes(m.movedBlock, m.movedState | PISTON_EXTENDED, m.x, m.y, m.z, x,y,z,visitor);
            int id = m.source ? PISTON_HEAD : m.movedBlock;
            boolean shortHead = m.extending != (1.0-m.progress < .25);
            int state = m.source ? dirToF6(m.direction) | (shortHead ? 16 : 0) : m.movedState;
            double offset = m.extending ? m.progress-1 : 1-m.progress;
            boxes(id,state,m.x+DIR_DX[m.direction]*offset,m.y+DIR_DY[m.direction]*offset,
                    m.z+DIR_DZ[m.direction]*offset,x,y,z,visitor);
        }
    }
    private static void boxes(int id,int state,double ox,double oy,double oz,int x,int y,int z,
            BuildingBlockRules.CollisionBoxVisitor visitor) {
        BuildingBlockRules.forCollisionBoxes(id,state,(int)Math.floor(ox),(int)Math.floor(oz),
            (a,b,c,d,e,f) -> {
                double x0=Math.max(0,ox+a-x), y0=Math.max(0,oy+b-y), z0=Math.max(0,oz+c-z);
                double x1=Math.min(1,ox+d-x), y1=Math.min(1,oy+e-y), z1=Math.min(1,oz+f-z);
                if(x1>x0 && y1>y0 && z1>z0) visitor.visit(x0,y0,z0,x1,y1,z1);
            });
    }
}
