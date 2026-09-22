package com.gameexpert.authority.versioned;

import com.gameexpert.world.WorldGenerationProfile;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.gameexpert.authority.versioned.NeutralFinalChunk.*;

/** Private capability retaining the exact verified carrier and selected producer. */
final class ProducerBinding {
    private final IsolatedProducerSession producer;
    private final WorldGenerationProfile profile;
    /** Deflated carrier bytes; producer calls are rare, so a resident chunk keeps them compact. */
    private final byte[] packedCarrier;
    private final int carrierLength;
    private final Map<Integer, StateOverride> defaults = new ConcurrentHashMap<>();
    ProducerBinding(IsolatedProducerSession producer, WorldGenerationProfile profile, byte[] carrier) {
        this.producer=Objects.requireNonNull(producer); this.profile=Objects.requireNonNull(profile);
        this.carrierLength=carrier.length;
        this.packedCarrier=deflate(carrier);
    }
    byte[] encodedCarrier() { return carrier(); }
    private byte[] carrier() {
        var inflater=new java.util.zip.Inflater();
        try {
            inflater.setInput(packedCarrier);
            byte[] out=new byte[carrierLength];
            int n=0;
            while(n<carrierLength){
                int read=inflater.inflate(out,n,carrierLength-n);
                if(read==0&&(inflater.finished()||inflater.needsInput()))break;
                n+=read;
            }
            if(n!=carrierLength||!inflater.finished())throw new IllegalStateException("retained carrier is corrupt");
            return out;
        } catch(java.util.zip.DataFormatException corrupt){throw new IllegalStateException("retained carrier is corrupt",corrupt);}
        finally{inflater.end();}
    }
    private static byte[] deflate(byte[] raw) {
        var deflater=new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw);deflater.finish();
            var out=new ByteArrayOutputStream(Math.max(64,raw.length/8));byte[] buffer=new byte[8192];
            while(!deflater.finished()){int n=deflater.deflate(buffer);out.write(buffer,0,n);}
            return out.toByteArray();
        } finally{deflater.end();}
    }
    WorldGenerationProfile generationProfile(){return profile;}
    NeutralFinalChunk verify(int chunkX, int chunkZ, byte[] encoded) { return NeutralFinalChunkWire.verify(producer, profile, chunkX, chunkZ, encoded); }
    NeutralFinalChunk select(NeutralFinalChunk source, Sidecars selected) {
        Objects.requireNonNull(selected);
        if(source.sidecars().equals(selected)) return source;
        final byte[] carrier=carrier();
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);header(out,4);
            out.writeInt(source.chunkX());out.writeInt(source.chunkZ());out.writeInt(carrier.length);out.write(carrier);
            Sidecars original=source.sidecars();
            ordinals(out,original.blockTicks(),selected.blockTicks());ordinals(out,original.fluidTicks(),selected.fluidTicks());
            ordinals(out,original.loot(),selected.loot());ordinals(out,original.spawners(),selected.spawners());
            ordinals(out,original.owners(),selected.owners());ordinals(out,original.archaeology(),selected.archaeology());
            ordinals(out,original.bees(),selected.bees());ordinals(out,original.blockEntities(),selected.blockEntities());
            ordinals(out,original.entities(),selected.entities());
            try(var in=response(bytes.toByteArray())) {
                byte[] encoded=readBytes(in),projection=readBytes(in);
                if(in.available()!=0)throw new IOException("trailing subset response");
                NeutralFinalChunk result=NeutralFinalChunkWire.decodeResponse(projection,source.chunkX(),source.chunkZ());
                result.bind(new ProducerBinding(producer,profile,encoded));
                return result;
            }
        } catch(IOException malformed) { throw new IllegalArgumentException("invalid producer subset response",malformed); }
    }
    boolean matchesCanonicalMob(NeutralFinalChunk source, StructureEntity row) {
        final byte[] carrier=carrier();
        Objects.requireNonNull(row);
        int ordinal=source.sidecars().entities().indexOf(row);
        if(ordinal<0) throw new IllegalArgumentException("entity is not part of verified carrier");
        try {
            var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes); header(out,7);
            out.writeInt(source.chunkX()); out.writeInt(source.chunkZ()); out.writeInt(carrier.length); out.write(carrier); out.writeInt(ordinal);
            try(var in=response(bytes.toByteArray())) {
                int value=in.readUnsignedByte(); if(value>1 || in.available()!=0) throw new IOException("invalid canonical mob response");
                return value==1;
            }
        } catch(IOException malformed) { throw new IllegalArgumentException("invalid canonical mob response",malformed); }
    }
    byte[] renderPreview(NeutralFinalChunk source, long seed, TargetMap target, boolean seaLevel) {
        final byte[] carrier=carrier();
        Objects.requireNonNull(target);
        int declaration=-1, targetOrdinal=-1;
        for(int i=0;i<source.sidecars().containerLootDeclarations().size();i++) {
            int ordinal=source.sidecars().containerLootDeclarations().get(i).productionContext().targets().indexOf(target);
            if(ordinal>=0) { declaration=i;targetOrdinal=ordinal;break; }
        }
        if(declaration<0 || target.found()==null) throw new IllegalArgumentException("map target is not a verified Found declaration");
        try {
            var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);header(out,10);
            out.writeInt(source.chunkX());out.writeInt(source.chunkZ());out.writeInt(carrier.length);out.write(carrier);
            out.writeInt(declaration);out.writeInt(targetOrdinal);out.writeLong(seed);out.writeBoolean(seaLevel);
            try(var in=response(bytes.toByteArray())) {
                byte[] colors=readBytes(in); if(colors.length!=16384 || in.available()!=0)throw new IOException("invalid map preview response");
                return colors;
            }
        } catch(IOException malformed) { throw new IllegalArgumentException("invalid verified preview response",malformed); }
    }
    byte[] loot(NeutralFinalChunk source, boolean resolve, byte[] context, long seed, String table, long rawSeed, int x, int y, int z, int slots, Long initialLo, Long initialHi) {
        final byte[] carrier=carrier();
        Objects.requireNonNull(context);if(context.length==0||context.length>16384)throw new IllegalArgumentException("invalid loot context size");
        if((initialLo==null)!=(initialHi==null))throw new IllegalArgumentException("incomplete named loot sequence");
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);header(out,resolve?8:9);
            out.writeInt(source.chunkX());out.writeInt(source.chunkZ());out.writeInt(carrier.length);out.write(carrier);
            out.writeInt(context.length);out.write(context);out.writeLong(seed);out.writeUTF(table);out.writeLong(rawSeed);
            out.writeInt(x);out.writeInt(y);out.writeInt(z);out.writeInt(slots);out.writeBoolean(initialLo!=null);
            if(initialLo!=null){out.writeLong(initialLo);out.writeLong(initialHi);}
            try(var in=response(bytes.toByteArray())) {
                byte[] result;
                if(resolve)result=readBytes(in);else { if(in.readUnsignedByte()!=1)throw new IOException("loot declaration rejected");result=new byte[0]; }
                if(in.available()!=0)throw new IOException("trailing loot response");return result;
            }
        } catch(IOException malformed){throw new IllegalArgumentException("invalid producer loot response",malformed);}
    }
    LateLootOutcome lateLoot(NeutralFinalChunk source,byte[] context,long seed,String table,long rawSeed,
            int x,int y,int z,int slots,Long initialLo,Long initialHi,
            CanonicalStructureSnapshot snapshot,List<LateLootOutcome.Claim> claims) {
        final byte[] carrier=carrier();
        Objects.requireNonNull(context);Objects.requireNonNull(snapshot);Objects.requireNonNull(claims);
        if(!profile.equals(snapshot.profile()))throw new IllegalArgumentException("late loot snapshot profile differs");
        if(context.length==0||context.length>16384||(initialLo==null)!=(initialHi==null))throw new IllegalArgumentException("invalid late loot request");
        try {
            var legacyBytes=new ByteArrayOutputStream();var legacy=new DataOutputStream(legacyBytes);
            legacy.writeInt(source.chunkX());legacy.writeInt(source.chunkZ());legacy.writeInt(carrier.length);legacy.write(carrier);
            legacy.writeInt(context.length);legacy.write(context);legacy.writeLong(seed);legacy.writeUTF(table);legacy.writeLong(rawSeed);
            legacy.writeInt(x);legacy.writeInt(y);legacy.writeInt(z);legacy.writeInt(slots);legacy.writeBoolean(initialLo!=null);
            if(initialLo!=null){legacy.writeLong(initialLo);legacy.writeLong(initialHi);}legacy.flush();
            synchronized (producer) {
                try (ProducerSnapshotUpload upload = new ProducerSnapshotUpload(
                        producer, profile, snapshot.worldId(), snapshot.rows())) {
                    upload.requireReceipt(snapshot.receipt());
                    upload.claims(claims);
                    return LateLootOutcome.fromWorker(upload.execute(legacyBytes.toByteArray()), context);
                }
            }
        }catch(IOException malformed){throw new IllegalArgumentException("invalid late loot request",malformed);}
    }
    StateOverride defaultState(int blockId) {
        if(blockId<0 || blockId>65535) throw new IllegalArgumentException("invalid default block ID");
        return defaults.computeIfAbsent(blockId,id->{
            try {
                var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);header(out,5);
                out.writeInt(1);out.writeInt(id);
                try(var in=response(bytes.toByteArray())) {
                    if(in.readInt()!=1 || in.readInt()!=id)throw new IOException("default state identity mismatch");
                    String exact=in.readUTF();int leaves=in.readUnsignedByte();String fluid=in.readUTF();
                    if(exact.isEmpty()||leaves>1||in.available()!=0)throw new IOException("invalid default state response");
                    return new StateOverride(0,id,0,exact,leaves==1,fluid);
                }
            } catch(IOException malformed){throw new IllegalArgumentException("invalid producer default response",malformed);}
        });
    }
    private static <T> void ordinals(DataOutputStream out,List<T> original,List<T> selected) throws IOException {
        out.writeInt(selected.size());int after=0;
        for(T row:selected){
            while(after<original.size()&&!Objects.equals(original.get(after),row))after++;
            if(after==original.size())throw new IllegalArgumentException("selected sidecars must be an ordered original subset");
            out.writeInt(after++);
        }
    }
    private void header(DataOutputStream out,int operation) throws IOException {
        out.writeInt(NeutralFinalChunkWire.MAGIC);out.writeByte(NeutralFinalChunkWire.VERSION);
        out.writeUTF(profile.getBaselineId());out.writeUTF(profile.getInputFingerprintSha256());out.writeUTF(profile.getGeneratorSourceSha256());
        out.writeInt(profile.getWorldVersion());out.writeInt(profile.getDataPackMajor());out.writeInt(profile.getResourcePackMajor());out.writeInt(profile.getProtocolVersion());out.writeByte(operation);
    }
    private DataInputStream response(byte[] request) throws IOException {
        if(request.length>NeutralFinalChunkWire.MAX_BYTES)throw new IllegalArgumentException("producer request exceeds bounds");
        byte[] response=producer.exchange(request);
        if(response.length>NeutralFinalChunkWire.MAX_BYTES)throw new IOException("producer response exceeds bounds");
        var in=new DataInputStream(new ByteArrayInputStream(response));
        if(in.readInt()!=NeutralFinalChunkWire.MAGIC||in.readUnsignedByte()!=NeutralFinalChunkWire.VERSION)throw new IOException("producer response version mismatch");
        return in;
    }
    private static byte[] readBytes(DataInputStream in) throws IOException {
        int count=in.readInt();if(count<=0||count>in.available())throw new IOException("invalid producer byte count");return in.readNBytes(count);
    }
}
