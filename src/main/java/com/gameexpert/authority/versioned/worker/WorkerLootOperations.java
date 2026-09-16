package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAssignmentPlan;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Arrays;

/** All loot receipt validation and random execution stay inside the selected producer. */
final class WorkerLootOperations {
    @FunctionalInterface
    interface ResolutionRunner {
        Mc263ContainerLootResolver.Resolution resolve(long worldSeed,String table,long rawSeed,
                int x,int y,int z,int slots,Mc263ContainerLootResolver.XoroshiroState initial,
                Mc263ContainerLootResolver.LocatedProductionContext original) throws Exception;
    }
    static byte[] execute(DataInputStream in, boolean resolve) throws Exception {
        return execute(in,resolve,null);
    }
    static byte[] execute(DataInputStream in, boolean resolve, ResolutionRunner runner) throws Exception {
        int x=in.readInt(),z=in.readInt(),length=in.readInt();
        if(length<=0||length>in.available())throw new IllegalArgumentException("invalid loot carrier frame");
        var carrier=Mc263FinalChunkCodec.decode(in.readNBytes(length));
        if(carrier.chunkX()!=x||carrier.chunkZ()!=z)throw new IllegalArgumentException("loot carrier coordinates mismatch");
        length=in.readInt();if(length<=0||length>16384||length>in.available())throw new IllegalArgumentException("invalid loot context frame");
        byte[] context=in.readNBytes(length);long worldSeed=in.readLong();String table=in.readUTF();long seed=in.readLong();
        int originX=in.readInt(),originY=in.readInt(),originZ=in.readInt(),slots=in.readInt();
        boolean hasInitial=in.readBoolean();long lo=hasInitial?in.readLong():0,hi=hasInitial?in.readLong():0;
        if(in.available()!=0)throw new IllegalArgumentException("trailing loot request");
        Method encode=CanonicalLootAssignmentPlan.class.getDeclaredMethod("encodeLocatedProductionContextPayload",Mc263FinalChunkSidecars.ContainerLootDeclaration.class);
        encode.setAccessible(true);
        Mc263FinalChunkSidecars.ContainerLootDeclaration matched=null;
        for(var declaration:carrier.sidecars().containerLootDeclarations()) {
            if(Arrays.equals(context,(byte[])encode.invoke(null,declaration))) {
                if(matched!=null)throw new IllegalArgumentException("ambiguous loot declaration");matched=declaration;
            }
        }
        if(matched==null)throw new IllegalArgumentException("context not bound to verified carrier");
        matched.productionContext().requireMatches(worldSeed,table,originX,originY,originZ);
        if(slots!=matched.containerSize())throw new IllegalArgumentException("loot slot shape mismatch");
        long actualSeed;
        if(matched.sourceSection()==Mc263FinalChunkSidecars.ContainerLootSourceSection.LOOT) {
            var row=carrier.sidecars().loot().get(matched.sourceSectionOrdinal());
            actualSeed=row.seed();if(!row.table().equals(table))throw new IllegalArgumentException("loot table mismatch");
        } else {
            var row=carrier.sidecars().entities().get(matched.sourceSectionOrdinal());
            actualSeed=row.lootSeed();if(!row.lootTable().equals(table))throw new IllegalArgumentException("entity loot table mismatch");
        }
        if(seed!=actualSeed || hasInitial!=(seed==0))throw new IllegalArgumentException("loot random source mismatch");
        var initial=hasInitial?new Mc263ContainerLootResolver.XoroshiroState(lo,hi):null;
        if(hasInitial) {
            Method named=CanonicalLootAssignmentPlan.class.getDeclaredMethod("namedInitialState",long.class,String.class);
            named.setAccessible(true);long[] expected=(long[])named.invoke(null,worldSeed,table);
            if(lo!=expected[0]||hi!=expected[1])throw new IllegalArgumentException("named loot sequence mismatch");
        }
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x57504731);out.writeByte(1);
        if(resolve) {
            if(!(matched.productionContext() instanceof Mc263ContainerLootResolver.LocatedProductionContext located))throw new IllegalArgumentException("production loot needs located context");
            var resolved=runner==null
                    ? Mc263ContainerLootResolver.resolveLocated(worldSeed,table,seed,originX,originY,originZ,slots,initial,located)
                    : runner.resolve(worldSeed,table,seed,originX,originY,originZ,slots,initial,located);
            CanonicalLootStoredResolution stored = WorkerLootResultEncoding.encode(resolved);
            byte[] result=stored.encode();
            out.writeInt(result.length);out.write(result);
        } else out.writeBoolean(true);
        return bytes.toByteArray();
    }
}
