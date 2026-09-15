package com.gameexpert.authority.versioned;

import static com.gameexpert.authority.versioned.NeutralFinalChunk.*;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.*;
import java.util.*;

/** Host-side bounded transport parser. It never loads a producer codec or revalidates with a foreign pin. */
public final class NeutralFinalChunkWire {
    public static final int MAGIC=0x57504731, VERSION=1, MAX_BYTES=64*1024*1024;
    private static final int BLOCKS=16*384*16, COLUMNS=16*16;
    private NeutralFinalChunkWire() { }

    /** The full stored profile and target coordinate are checked by the private producer worker. */
    public static NeutralFinalChunk verify(IsolatedProducerSession producer, WorldGenerationProfile profile,
            int chunkX, int chunkZ, byte[] carrier) {
        Objects.requireNonNull(producer); WorldGenerationProfiles.requireSupported(profile);
        NeutralFinalChunk result = decodeResponse(producer.exchange(verificationRequest(profile,chunkX,chunkZ,carrier)),chunkX,chunkZ);
        result.bind(new ProducerBinding(producer, profile, carrier));
        return result;
    }
    public static NeutralFinalChunk.StateOverride defaultState(IsolatedProducerSession producer, WorldGenerationProfile profile, int blockId) {
        WorldGenerationProfiles.requireSupported(profile);
        return new ProducerBinding(producer, profile, new byte[0]).defaultState(blockId);
    }
    static byte[] verificationRequest(WorldGenerationProfile p,int x,int z,byte[] carrier) {
        Objects.requireNonNull(carrier); if(carrier.length==0||carrier.length>MAX_BYTES-1024) throw new IllegalArgumentException("carrier frame outside bounds");
        try {
            var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeByte(VERSION);
            out.writeUTF(p.getBaselineId()); out.writeUTF(p.getInputFingerprintSha256()); out.writeUTF(p.getGeneratorSourceSha256());
            out.writeInt(p.getWorldVersion()); out.writeInt(p.getDataPackMajor()); out.writeInt(p.getResourcePackMajor()); out.writeInt(p.getProtocolVersion());
            out.writeByte(1); out.writeInt(x); out.writeInt(z); out.writeInt(carrier.length); out.write(carrier); out.flush();
            byte[] result=bytes.toByteArray();if(result.length>MAX_BYTES)throw new IllegalArgumentException("producer request exceeds bounds");return result;
        } catch(IOException impossible) { throw new IllegalStateException(impossible); }
    }
    static NeutralFinalChunk decodeResponse(byte[] response,int expectedX,int expectedZ) {
        Objects.requireNonNull(response); if(response.length==0||response.length>MAX_BYTES)throw new IllegalArgumentException("projection frame outside bounds");
        try(var in=new DataInputStream(new ByteArrayInputStream(response))) {
            if(in.readInt()!=MAGIC||in.readUnsignedByte()!=VERSION)throw new IOException("projection wire mismatch");
            int x=in.readInt(),z=in.readInt();if(x!=expectedX||z!=expectedZ)throw new IOException("projection coordinate mismatch");
            int n=count(in,2);if(n!=BLOCKS)throw new IOException("projection block count mismatch");
            short[] blocks=new short[n];for(int i=0;i<n;i++)blocks[i]=in.readShort();
            n=count(in,10);if(n>BLOCKS)throw new IOException("too many state overrides");
            Map<Integer,StateOverride> states=new LinkedHashMap<>();int previous=-1;
            for(int i=0;i<n;i++){int packed=position(in);int code=in.readInt();String exact=in.readUTF();
                if(packed<=previous||code<=0||code>255||exact.isEmpty())throw new IOException("invalid sparse state ordering or code");
                states.put(packed,new StateOverride(packed,Short.toUnsignedInt(blocks[packed]),code,exact,bool(in),in.readUTF()));previous=packed;}
            int[] surface=heightmap(in),ocean=heightmap(in),motion=heightmap(in);
            Sidecars sidecars=readSidecars(in);
            if(in.available()!=0)throw new IOException("trailing projection bytes");
            return new NeutralFinalChunk(x,z,blocks,states,surface,ocean,motion,sidecars);
        } catch(IOException|RuntimeException malformed) { throw new IllegalArgumentException("invalid neutral chunk projection",malformed); }
    }
    /** Counts are bounded by remaining bytes before allocation; no untrusted capacity allocation. */
    private static int count(DataInputStream in,int minimumBytes) throws IOException {
        int n=in.readInt();if(n<0||n>in.available()/minimumBytes)throw new IOException("collection count exceeds frame");return n;
    }
    private static int position(DataInputStream in) throws IOException {int p=in.readInt();if(p<0||p>=BLOCKS)throw new IOException("sidecar position outside chunk");return p;}
    private static int nonnegative(DataInputStream in) throws IOException {int n=in.readInt();if(n<0)throw new IOException("negative count");return n;}
    private static TickPriority priority(DataInputStream in) throws IOException {int p=in.readInt();if(p < -3||p>3)throw new IOException("invalid tick priority");return TickPriority.fromValue(p);}
    private static int unsigned16(DataInputStream in) throws IOException {int p=in.readInt();if(p<0||p>65535)throw new IOException("invalid block ID");return p;}
    private static boolean bool(DataInputStream in) throws IOException {int b=in.readUnsignedByte();if(b>1)throw new IOException("invalid boolean");return b==1;}
    private static int[] heightmap(DataInputStream in) throws IOException {int n=count(in,4);if(n!=COLUMNS)throw new IOException("heightmap size mismatch");int[] a=new int[n];for(int i=0;i<n;i++){a[i]=in.readInt();if(a[i]<-64||a[i]>320)throw new IOException("height outside chunk");}return a;}
    private static byte[] bytes(DataInputStream in) throws IOException {int n=count(in,1);return in.readNBytes(n);}
    private static List<String> strings(DataInputStream in) throws IOException {int n=count(in,2);var a=new ArrayList<String>();for(int i=0;i<n;i++)a.add(in.readUTF());return a;}
    private static Sidecars readSidecars(DataInputStream in) throws IOException {
        var blockTicks=new ArrayList<BlockTick>();for(int n=count(in,26);n>0;n--)blockTicks.add(new BlockTick(position(in),unsigned16(in),in.readUTF(),nonnegative(in),priority(in),in.readLong()));
        var fluidTicks=new ArrayList<FluidTick>();for(int n=count(in,22);n>0;n--)fluidTicks.add(new FluidTick(position(in),in.readUTF(),nonnegative(in),priority(in),in.readLong()));
        var loot=new ArrayList<Loot>();for(int n=count(in,16);n>0;n--)loot.add(new Loot(position(in),in.readUTF(),in.readUTF(),in.readLong()));
        var spawners=new ArrayList<Spawner>();for(int n=count(in,6);n>0;n--)spawners.add(new Spawner(position(in),in.readUTF()));
        var owners=new ArrayList<Owner>();for(int n=count(in,12);n>0;n--)owners.add(new Owner(position(in),in.readLong()));
        var archaeology=new ArrayList<Archaeology>();for(int n=count(in,14);n>0;n--)archaeology.add(new Archaeology(position(in),in.readUTF(),in.readLong()));
        var bees=new ArrayList<BeeNest>();for(int n=count(in,8);n>0;n--){int packed=position(in);var ticks=new ArrayList<Integer>();for(int k=count(in,4);k>0;k--){int tick=nonnegative(in);if(tick>598)throw new IOException("invalid bee delay");ticks.add(tick);}bees.add(new BeeNest(packed,ticks));}
        var blockEntities=new ArrayList<BlockEntity>();for(int n=count(in,12);n>0;n--)blockEntities.add(new BlockEntity(position(in),in.readUTF(),in.readUTF(),bytes(in)));
        var entities=new ArrayList<StructureEntity>();for(int n=count(in,70);n>0;n--)entities.add(new StructureEntity(in.readUTF(),in.readUTF(),in.readDouble(),in.readDouble(),in.readDouble(),in.readFloat(),in.readFloat(),in.readDouble(),in.readDouble(),in.readDouble(),in.readUTF(),in.readLong(),bytes(in)));
        var declarations=new ArrayList<ContainerLootDeclaration>();for(int n=count(in,23);n>0;n--){int ordinal=nonnegative(in);int source=in.readUnsignedByte();if(source>1)throw new IOException("unknown loot section");int sectionOrdinal=nonnegative(in),size=nonnegative(in);var context=context(in);declarations.add(new ContainerLootDeclaration(ordinal,source==0?ContainerLootSourceSection.LOOT:ContainerLootSourceSection.ENTS,sectionOrdinal,size,context,in.readUTF(),in.readUTF()));}
        return new Sidecars(blockTicks,fluidTicks,loot,spawners,owners,archaeology,bees,blockEntities,entities,declarations);
    }
    static ProductionContext context(DataInputStream in) throws IOException {
        String biome=in.readUTF(),world=in.readUTF(),source=in.readUTF(),table=in.readUTF();int x=in.readInt(),y=in.readInt(),z=in.readInt();String receipt=in.readUTF();
        int variant=in.readUnsignedByte();if(variant>1)throw new IOException("unknown production context variant");
        var targets=new ArrayList<TargetMap>();var legacy=new ArrayList<LegacyMap>();
        for(int n=count(in,2);n>0;n--){String key=in.readUTF();
            if(variant==1){var binding=new MapBinding(in.readUTF(),in.readUTF(),in.readUTF(),strings(in),in.readUTF(),in.readUTF(),in.readUTF(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),bool(in),in.readUTF(),in.readUTF());
                String targetReceipt=in.readUTF();FoundTarget found=bool(in)?new FoundTarget(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readUTF()):null;
                targets.add(new TargetMap(key,binding,targetReceipt,found));
            }else legacy.add(new LegacyMap(key,in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readUTF()));
        }
        return new ProductionContext(biome,world,source,table,x,y,z,receipt,targets,legacy,variant==1);
    }
}
