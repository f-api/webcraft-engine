package com.gameexpert.world.dimension.flesh;

import java.util.ArrayList;
import java.util.List;
import com.gameexpert.world.dimension.DimensionChunkProvider.InitialMob;

/** Four deterministic interior sentry sites; population persistence owns one-time installation. */
public final class FleshMawPlacements {
    private FleshMawPlacements() { }
    public static List<InitialMob> inChunk(int seed, int cx, int cz) {
        var layout=FleshColonyLayout.forCoordinate(seed,cx*16,cz*16);
        var result=new ArrayList<InitialMob>();
        int[] dx={-1,1,0,0},dz={0,0,-1,1},face={5,4,3,2};
        for(int ray=0;ray<4;ray++) {
            InitialMob found=null;
            int y=layout.mainChamberCeilingY()-2;
            for(int distance=4;distance<=layout.heartWidth() && found==null;distance++) {
                int x=layout.centerX()+dx[ray]*distance,z=layout.centerZ()+dz[ray]*distance;
                if(layout.sample(x,y,z).kind()==FleshColonyLayout.TissueKind.AIR
                    && layout.sample(x+dx[ray],y,z+dz[ray]).kind()==FleshColonyLayout.TissueKind.MYOCARDIUM)
                    found=new InitialMob("hanging_maw",x+.5,y,z+.5,face[ray],0);
            }
            if(found==null) {
                int x=layout.centerX()+dx[ray]*4+dz[ray]*3,z=layout.centerZ()+dz[ray]*4+dx[ray]*3;
                for(y=67;y<layout.heartTopY() && found==null;y++)
                    if(layout.sample(x,y,z).kind()==FleshColonyLayout.TissueKind.AIR
                        && layout.sample(x,y+1,z).kind()==FleshColonyLayout.TissueKind.MYOCARDIUM)
                        found=new InitialMob("hanging_maw",x+.5,y,z+.5,0,0);
            }
            if(found!=null && Math.floorDiv((int)Math.floor(found.x()),16)==cx
                && Math.floorDiv((int)Math.floor(found.z()),16)==cz) result.add(found);
        }
        return List.copyOf(result);
    }
}
