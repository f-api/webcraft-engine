package com.gameexpert.authority.versioned.worker;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import java.io.*;
import java.util.*;

/** Profile-local read/subset operations. The host can select existing facts, never invent them. */
public final class LegacyCarrierOperations {
    private LegacyCarrierOperations() { }

    public static byte[] subset(DataInputStream in) throws IOException {
        int x=in.readInt(), z=in.readInt(), length=in.readInt();
        if(length<=0||length>64*1024*1024||length>in.available()) throw new IllegalArgumentException("invalid subset carrier length");
        var chunk=Mc263FinalChunkCodec.decode(in.readNBytes(length));
        if(chunk.chunkX()!=x||chunk.chunkZ()!=z)throw new IllegalArgumentException("subset target mismatch");
        var s=chunk.sidecars();
        int[] bt=ordinals(in,s.blockTicks().size()), ft=ordinals(in,s.fluidTicks().size()),
                lo=ordinals(in,s.loot().size()), sp=ordinals(in,s.spawners().size()),
                ow=ordinals(in,s.owners().size()), ar=ordinals(in,s.archaeology().size()),
                be=ordinals(in,s.bees().size()), bl=ordinals(in,s.blockEntities().size()),
                en=ordinals(in,s.entities().size());
        if(in.available()!=0)throw new IllegalArgumentException("trailing subset data");
        var declarations=new ArrayList<Mc263FinalChunkSidecars.ContainerLootDeclaration>();
        int declaration=0, selectedLoot=0;
        for(int i=0;i<s.loot().size();i++){
            var original=s.containerLootDeclarations().get(declaration);
            var checked=Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                    original,declaration,i,x,z,s.loot().get(i));
            if(checked!=original)throw new IllegalArgumentException("source loot declaration mismatch");
            if(Arrays.binarySearch(lo,i)>=0)declarations.add(
                    Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                            original,declarations.size(),selectedLoot++,x,z,s.loot().get(i)));
            declaration++;
        }
        int selectedEntity=0;
        for(int i=0;i<s.entities().size();i++){
            var row=s.entities().get(i);boolean retained=Arrays.binarySearch(en,i)>=0;
            if(!row.lootTable().isEmpty()){
                var original=s.containerLootDeclarations().get(declaration);
                var checked=Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                        original,declaration,i,x,z,row);
                if(checked!=original)throw new IllegalArgumentException("source entity declaration mismatch");
                if(retained)declarations.add(Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                        original,declarations.size(),selectedEntity,x,z,row));
                declaration++;
            }
            if(retained)selectedEntity++;
        }
        if(declaration!=s.containerLootDeclarations().size())throw new IllegalArgumentException("unmatched source declaration");
        var filtered=new Mc263FinalChunkSidecars(select(s.blockTicks(),bt),select(s.fluidTicks(),ft),
                select(s.loot(),lo),select(s.spawners(),sp),select(s.owners(),ow),
                select(s.archaeology(),ar),select(s.bees(),be),select(s.blockEntities(),bl),
                select(s.entities(),en),declarations);
        byte[] raw=Mc263FinalChunkCodec.encode(new Mc263FinalChunkCodec.FinalChunk(x,z,
                chunk.blockIds(),chunk.stateOverrides(),chunk.worldSurfaceWg(),chunk.oceanFloorWg(),
                chunk.motionBlocking(),filtered));
        byte[] projection=LegacyProducerWorker.project(Mc263FinalChunkCodec.decode(raw));
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0x57504731);out.writeByte(1);out.writeInt(raw.length);out.write(raw);
        out.writeInt(projection.length);out.write(projection);out.flush();return bytes.toByteArray();
    }

    public static byte[] defaults(DataInputStream in) throws IOException {
        int count=in.readInt();
        if(count<0||count>32768||count*4L!=in.available())throw new IllegalArgumentException("default state request outside bounds");
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(0x57504731);out.writeByte(1);out.writeInt(count);int previous=-1;
        for(int i=0;i<count;i++){
            int id=in.readInt();if(id<=previous||id>32767)throw new IllegalArgumentException("unordered default block IDs");
            previous=id;var state=com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState.defaultForId(id);out.writeInt(id);out.writeUTF(state.exactState());out.writeBoolean(state.isLeavesTag());out.writeUTF(state.fluidTypeKey());
        }
        out.flush();return bytes.toByteArray();
    }

    private static int[] ordinals(DataInputStream in,int sourceCount) throws IOException {
        int count=in.readInt();if(count<0||count>sourceCount||count*4L>in.available())throw new IllegalArgumentException("subset ordinal count outside bounds");
        int[] result=new int[count];int previous=-1;
        for(int i=0;i<count;i++){int value=in.readInt();if(value<=previous||value>=sourceCount)throw new IllegalArgumentException("invalid subset ordinal");result[i]=value;previous=value;}
        return result;
    }

    private static <T> List<T> select(List<T> source,int[] indices){
        var result=new ArrayList<T>(indices.length);for(int index:indices)result.add(source.get(index));return List.copyOf(result);
    }
}
