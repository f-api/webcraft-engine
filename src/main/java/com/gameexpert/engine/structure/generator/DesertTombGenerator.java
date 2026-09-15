package com.gameexpert.engine.structure.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/** Site-hash procedural desert pyramid/tomb built strictly above an intact desert surface. */
public final class DesertTombGenerator implements StructureOverlayGenerator {
    private static final int EROSION_PURPOSE = 0x64;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        SizeClass size = SizeClass.roll(site.siteKey());
        int radius = size.baseRadius + Math.floorMod(site.shapeLane() >>> 2, size.radiusVariants);
        int inset = 1 + ((site.topologyLane() >>> 4) & 1);
        int tierHeight = 1 + ((site.topologyLane() >>> 6) & 1) + size.tierHeightBonus;
        int buried = ((site.adaptationLane() >>> 8) & 7) == 0 ? 2
                : ((site.adaptationLane() >>> 8) & 3) == 0 ? 1 : 0;
        int tiers = Math.min(Math.max(3, (radius / inset) - buried),
                (radius - buried * inset - 1) / inset + 1);
        int topY = Math.max(7, 1 + tiers * tierHeight + 2);
        int groundY = flatDesertGround(site, world, radius + size.elementReach);
        if (groundY < Blocks.MIN_Y || groundY + topY > Blocks.MAX_Y) return List.of();

        Planner p = new Planner(site, world);
        foundation(p,site,world,groundY,radius);
        int motif = (site.shapeLane() >>> 13) & 3;
        int chamberRadius = 2 + ((site.partLane0(0, 0x64) >>> 5) & 1) + size.chamberRadiusBonus;
        boolean eroded = ((site.agingLane() >>> 3) & 3) != 0;
        for (int tier = 0; tier < tiers; tier++) {
            int shellRadius = radius - (tier + buried) * inset;
            if (shellRadius < 1) break;
            for (int dy = 0; dy < tierHeight; dy++) {
                int rise = 1 + tier * tierHeight + dy;
                for (int z=-shellRadius;z<=shellRadius;z++) for (int x=-shellRadius;x<=shellRadius;x++) {
                    if (Math.abs(x)!=shellRadius && Math.abs(z)!=shellRadius) continue;
                    if (entrance(x,z,shellRadius,rise,tier,tierHeight,chamberRadius)) continue;
                    if (eroded && erosion(site,x,rise,z,tier,shellRadius)) continue;
                    p.add(x,groundY+rise,z,Blocks.SANDSTONE);
                }
            }
        }

        int capRadius=Math.max(1,radius-(tiers+buried)*inset+1);
        int capY=groundY+1+tiers*tierHeight;
        for(int z=-capRadius;z<=capRadius;z++) for(int x=-capRadius;x<=capRadius;x++)
            p.add(x,capY,z,Blocks.SANDSTONE_SLAB);
        if (((site.shapeLane() >>> 20) & 1) != 0) p.add(0,capY+1,0,Blocks.SANDSTONE);

        // Above-ground chamber: floor, altar/loot markers and lights; shell remains hollow.
        for(int z=-chamberRadius;z<=chamberRadius;z++) for(int x=-chamberRadius;x<=chamberRadius;x++)
            p.add(x,groundY+1,z,((Math.abs(x)+Math.abs(z))&1)==0?Blocks.SANDSTONE:Blocks.RED_SAND);
        int lootCount=1+((site.partLane1(0,0x65)>>>4)&1)+size.lootBonus;
        for(int i=0;i<lootCount;i++) {
            int lane=site.partLane0(i,0x66);
            int lx=((lane>>>5)&1)==0?-chamberRadius+1:chamberRadius-1;
            int lz=((lane>>>7)&1)==0?-1:1;
            p.add(lx,groundY+2,lz,Blocks.CHEST);
        }
        p.add(-chamberRadius+1,groundY+2,0,Blocks.TORCH);
        p.add(chamberRadius-1,groundY+2,0,Blocks.TORCH);

        staircase(p,groundY,radius,inset,tiers,tierHeight,buried,site);
        facade(p,groundY,radius-buried*inset,motif,size,site);
        if(buried>0) sandDrift(p,groundY,radius-buried*inset,site);
        perimeterElements(p, groundY, radius, size, site);
        // Connect inward-stepping shells without replacing loot or filling the chamber.
        for (int tier=0;tier<tiers;tier++) {
            int outer=radius-(tier+buried)*inset;
            int inner=Math.max(1,outer-inset);
            int rise=(tier+1)*tierHeight;
            for (int z=-outer+1;z<outer;z++) for (int x=-outer+1;x<outer;x++) {
                if (rise==2 && z<0 && Math.abs(x)<=1) continue;
                if (Math.max(Math.abs(x),Math.abs(z))>=inner)
                    p.add(x,groundY+rise,z,Blocks.SANDSTONE);
            }
        }
        return p.valid()?p.voxels():List.of();
    }

    private void foundation(Planner p,StructureSiteDescriptor site,SurfaceDecorator.BlockView world,
            int deckY,int radius){
        for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++){
            int[]r=rotate(x,z,site.direction());int surface=groundY(site.anchorX()+r[0],site.anchorZ()+r[1],world);
            for(int y=surface+1;y<=deckY+1;y++)p.add(x,y,z,Blocks.SANDSTONE);
        }
    }

    private boolean entrance(int x,int z,int radius,int rise,int tier,int tierHeight,int chamberRadius){
        return z==-radius && Math.abs(x)<=1 && (tier==0 || (rise<=tierHeight+2 && radius>=chamberRadius));
    }

    private boolean erosion(StructureSiteDescriptor site,int x,int rise,int z,int tier,int radius){
        if((Math.abs(x)==radius&&Math.abs(z)==radius)||tier==0&&rise==1)return false;
        int[] r=rotate(x,z,site.direction()); int wx=site.anchorX()+r[0],wz=site.anchorZ()+r[1];
        return (site.voxelLane(wx,rise,wz,EROSION_PURPOSE)&15)<(1+((site.agingLane()>>>12)&1));
    }

    private void staircase(Planner p,int y,int radius,int inset,int tiers,int tierHeight,int buried,
            StructureSiteDescriptor site){
        int steps=Math.min(tiers,3+((site.topologyLane()>>>11)&3));
        int width=1+((site.shapeLane()>>>23)&1);
        for(int step=0;step<steps;step++) for(int x=-width;x<=width;x++)
            p.set(x,y+1+step*tierHeight,-radius+buried*inset+step*inset,Blocks.SANDSTONE_STAIRS);
    }

    private void facade(Planner p,int y,int frontRadius,int motif,SizeClass size,
            StructureSiteDescriptor site){
        int base=y+3+((site.partLane1(1,0x67)>>>6)&1);
        // A solid five-wide facade tower keeps every colored motif attached to the shell.
        for(int rise=1;rise<=7+size.facadeHeightBonus;rise++)for(int x=-2;x<=2;x++)
            p.add(x,y+rise,-frontRadius,Blocks.SANDSTONE);
        if(motif==0){
            for(int row=0;row<3;row++)for(int x=-row;x<=row;x+=Math.max(1,row*2))p.set(x,base+2-row,-frontRadius,Blocks.RED_SAND);
        }else if(motif==1){
            for(int i=-2;i<=2;i++)p.set(i,base+Math.abs(i)%2,-frontRadius,Blocks.RED_SAND);
        }else if(motif==2){
            for(int i=-2;i<=2;i++){p.set(i,base,-frontRadius,Blocks.RED_SAND);p.set(0,base+i+2,-frontRadius,Blocks.RED_SAND);}
        }else{
            for(int z=-1;z<=1;z++)for(int x=-2;x<=2;x++)if(((x+z)&1)==0)p.set(x,base+z+1,-frontRadius,Blocks.RED_SAND);
        }
    }

    /**
     * Size classes add independent, site-hashed perimeter monuments, rather than replacing
     * any pyramid detail.  Their count and height make the site scale readable at a distance.
     */
    private void perimeterElements(Planner p, int y, int radius, SizeClass size,
            StructureSiteDescriptor site) {
        int offset = (site.topologyLane() >>> 17) & 7;
        for (int i = 0; i < size.elementCount; i++) {
            int lane = site.partLane1(i + 3, 0x68);
            int direction = (offset + (i * 8) / size.elementCount) & 7;
            int distance = radius + 2 + (lane & 1);
            int x = compassX(direction) * distance;
            int z = compassZ(direction) * distance;
            int height = size.elementHeight + ((lane >>> 4) & 1);
            for (int rise = 1; rise <= height; rise++) {
                p.add(x, y + rise, z, rise == height ? Blocks.SANDSTONE_SLAB : Blocks.SANDSTONE);
            }
            if (size == SizeClass.LARGE) {
                p.add(x - compassZ(direction), y + 1, z + compassX(direction), Blocks.RED_SAND);
                p.add(x + compassZ(direction), y + 1, z - compassX(direction), Blocks.RED_SAND);
            }
        }
    }

    private int compassX(int direction) {
        return switch (direction) { case 1, 2, 3 -> 1; case 5, 6, 7 -> -1; default -> 0; };
    }

    private int compassZ(int direction) {
        return switch (direction) { case 3, 4, 5 -> 1; case 7, 0, 1 -> -1; default -> 0; };
    }

    /** A raised asymmetric sand drift reads as burial without digging or writing below ground. */
    private void sandDrift(Planner p,int y,int radius,StructureSiteDescriptor site){
        int side=((site.adaptationLane()>>>17)&1)==0?-1:1;
        int depth=2+((site.adaptationLane()>>>19)&3);
        for(int z=-radius;z<=-radius+depth;z++)for(int x=side<0?-radius:-1;x<= (side<0?1:radius);x++){
            int edge=side<0?x+radius:radius-x;
            if(edge<=depth+1)p.add(x,y+1+(edge==0?1:0),z,Blocks.SAND);
        }
    }

    private int flatDesertGround(StructureSiteDescriptor site,SurfaceDecorator.BlockView world,int radius){
        int highest=Blocks.MIN_Y-1,lowest=Blocks.MAX_Y,desert=0,total=0;
        for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++){
            int[] r=rotate(x,z,site.direction());int wx=site.anchorX()+r[0],wz=site.anchorZ()+r[1];
            int ground=groundY(wx,wz,world);if(ground<Blocks.MIN_Y||!drySurface(wx,ground,wz,world))return Blocks.MIN_Y-1;
            int support=world.getBlock(wx,ground,wz);total++;
            if(support==Blocks.SAND||support==Blocks.SANDSTONE||support==Blocks.RED_SAND)desert++;else return Blocks.MIN_Y-1;
            highest=Math.max(highest,ground);lowest=Math.min(lowest,ground);
        }
        return total==0||desert!=total||highest!=lowest?Blocks.MIN_Y-1:highest;
    }

    /** The block-only planning view cannot name a biome; require an entirely dry desert-material pad. */
    private boolean drySurface(int x,int ground, int z,SurfaceDecorator.BlockView world){
        for(int y=ground+1;y<Blocks.MAX_Y;y++){
            int block=world.getBlock(x,y,z);
            if(Fluids.isFluid(block))return false;
        }
        return true;
    }

    private int groundY(int x,int z,SurfaceDecorator.BlockView world){
        for(int y=Blocks.MAX_Y;y>=Blocks.MIN_Y;y--){if(StructureTerrainRules.isStableGround(world.getBlock(x,y,z)))return y;}
        return Blocks.MIN_Y-1;
    }
    private static int[] rotate(int x,int z,int direction){return switch(direction){case 0->new int[]{x,z};case 1->new int[]{-z,x};case 2->new int[]{-x,-z};default->new int[]{z,-x};};}

    /** A site-key roll keeps the 40% / 40% / 20% spectrum stable across all passes. */
    private enum SizeClass {
        SMALL(5, 3, 0, 0, 0, 1, 0, 3),
        MEDIUM(8, 4, 1, 1, 1, 3, 1, 4),
        LARGE(12, 5, 1, 2, 2, 6, 3, 5);

        private final int baseRadius;
        private final int radiusVariants;
        private final int tierHeightBonus;
        private final int chamberRadiusBonus;
        private final int lootBonus;
        private final int elementCount;
        private final int facadeHeightBonus;
        private final int elementHeight;
        private final int elementReach;

        SizeClass(int baseRadius, int radiusVariants, int tierHeightBonus,
                int chamberRadiusBonus, int lootBonus, int elementCount,
                int facadeHeightBonus, int elementHeight) {
            this.baseRadius = baseRadius;
            this.radiusVariants = radiusVariants;
            this.tierHeightBonus = tierHeightBonus;
            this.chamberRadiusBonus = chamberRadiusBonus;
            this.lootBonus = lootBonus;
            this.elementCount = elementCount;
            this.facadeHeightBonus = facadeHeightBonus;
            this.elementHeight = elementHeight;
            this.elementReach = 4;
        }

        private static SizeClass roll(int siteKey) {
            int roll = Math.floorMod(siteKey, 10);
            return roll < 4 ? SMALL : roll < 8 ? MEDIUM : LARGE;
        }
    }

    private static final class Planner{
        private final StructureSiteDescriptor site;private final SurfaceDecorator.BlockView world;
        private final Map<BlockPos,RuinGenerator.Voxel> plan=new LinkedHashMap<>();private boolean valid=true;
        private Planner(StructureSiteDescriptor site,SurfaceDecorator.BlockView world){this.site=site;this.world=world;}
        private void add(int lx,int y,int lz,int block){int[]r=rotate(lx,lz,site.direction());BlockPos pos=new BlockPos(site.anchorX()+r[0],y,site.anchorZ()+r[1]);if(plan.containsKey(pos))return;if(!replaceable(world.getBlock(pos.x(),pos.y(),pos.z()))){valid=false;return;}plan.put(pos,RuinGenerator.Voxel.at(pos.x(),pos.y(),pos.z(),block));}
        private void set(int lx,int y,int lz,int block){int[]r=rotate(lx,lz,site.direction());BlockPos pos=new BlockPos(site.anchorX()+r[0],y,site.anchorZ()+r[1]);if(!replaceable(world.getBlock(pos.x(),pos.y(),pos.z()))){valid=false;return;}plan.put(pos,RuinGenerator.Voxel.at(pos.x(),pos.y(),pos.z(),block));}
        private boolean replaceable(int block){return StructureTerrainRules.isReplaceableByStructure(block);}
        private boolean valid(){return valid;}private List<RuinGenerator.Voxel> voxels(){return List.copyOf(plan.values());}
    }
}
