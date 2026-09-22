package com.gameexpert.authority.versioned;

import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.*;
import java.util.*;

/** Host durable transport; all carrier interpretation belongs to the profile's worker. */
public final class ProducerStoreWire {
    private ProducerStoreWire() { }
    public static final class ValidatedCommit {
        private final byte[] finalCarrier, structureCarrier, successor, fingerprint;
        private final NeutralFinalChunk semantic;
        private final List<Integer> referenceCounts;
        private ValidatedCommit(byte[] f,byte[] s,byte[] m,byte[] h,NeutralFinalChunk semantic,List<Integer> counts){
            finalCarrier=f.clone();structureCarrier=s.clone();successor=m==null?null:m.clone();fingerprint=h.clone();this.semantic=semantic;referenceCounts=List.copyOf(counts);
        }
        public byte[] finalCarrier(){return finalCarrier.clone();}
        public byte[] structureCarrier(){return structureCarrier.clone();}
        public byte[] successor(){return successor==null?null:successor.clone();}
        public byte[] fingerprint(){return fingerprint.clone();}
        public NeutralFinalChunk semantic(){return semantic;}
        public List<Integer> referenceCounts(){return referenceCounts;}
    }
    public static ValidatedCommit validate(IsolatedProducerSession session,WorldGenerationProfile profile,long world,int x,int z,
            byte[] fin,byte[] structure,byte[] successor,byte[] fingerprint,int[] overrides){
        Objects.requireNonNull(overrides);if(overrides.length>98304)throw new IllegalArgumentException("too many player overrides");
        try{
            var bytes=new SizedBytes(512+16L+fin.length+structure.length+(successor==null?0:successor.length)
                    +fingerprint.length+4+4L*overrides.length);var out=header(bytes,profile,3);out.writeLong(world);out.writeInt(x);out.writeInt(z);
            write(out,fin);write(out,structure);write(out,successor);write(out,fingerprint);out.writeInt(overrides.length);for(int cell:overrides)out.writeInt(cell);out.flush();
            try(var in=response(session.exchange(bytes.exact()))){
                byte[] f=read(in,false),s=read(in,false),m=read(in,true),h=read(in,false);int count=in.readInt();
                if(count<0||count*4L>in.available())throw new IOException("invalid reference count list");
                List<Integer> counts=new ArrayList<>();for(int i=0;i<count;i++){int value=in.readInt();if(value<0)throw new IOException("negative structure reference");counts.add(value);}
                if(in.available()!=0)throw new IOException("trailing commit response");
                return new ValidatedCommit(f,s,m,h,NeutralFinalChunkWire.verify(session,profile,x,z,f),counts);
            }
        }catch(IOException malformed){throw new IllegalArgumentException("invalid producer commit transport",malformed);}
    }
    public static com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot references(
            IsolatedProducerSession session,WorldGenerationProfile profile,long world,
            List<com.gameexpert.terrain.persistence.CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots){
        List<CanonicalStructureSnapshot.Row> rows=new ArrayList<>();
        for(var row:snapshots){var c=row.commit();if(c.worldId()!=world||!profile.getBaselineId().equals(c.worldIdentity()))throw new IllegalArgumentException("mixed structure snapshot profile/world");rows.add(new CanonicalStructureSnapshot.Row(c.chunkX(),c.chunkZ(),c.structureCarrier()));}
        return referencesRows(session,profile,world,rows);
    }
    public static com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot referencesRows(
            IsolatedProducerSession session,WorldGenerationProfile profile,long world,
            List<CanonicalStructureSnapshot.Row> snapshots){
        List<CanonicalStructureSnapshot.Row> ordered = snapshots.stream()
                .sorted(Comparator.comparingInt(CanonicalStructureSnapshot.Row::chunkX)
                        .thenComparingInt(CanonicalStructureSnapshot.Row::chunkZ)).toList();
        synchronized (session) {
            try (ProducerSnapshotUpload upload = new ProducerSnapshotUpload(session, profile, world, ordered)) {
                return upload.references();
            } catch (IOException malformed) {
                throw new IllegalArgumentException("invalid structure snapshot transport", malformed);
            }
        }
    }
    static DataOutputStream header(ByteArrayOutputStream bytes,WorldGenerationProfile profile,int operation)throws IOException{
        WorldGenerationProfiles.requireSupported(profile);var out=new DataOutputStream(bytes);out.writeInt(0x57504731);out.writeByte(1);
        out.writeUTF(profile.getBaselineId());out.writeUTF(profile.getInputFingerprintSha256());out.writeUTF(profile.getGeneratorSourceSha256());
        out.writeInt(profile.getWorldVersion());out.writeInt(profile.getDataPackMajor());out.writeInt(profile.getResourcePackMajor());out.writeInt(profile.getProtocolVersion());out.writeByte(operation);return out;
    }
    static DataInputStream response(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length>64*1024*1024)throw new IOException("producer response outside bounds");var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=0x57504731||in.readUnsignedByte()!=1)throw new IOException("producer response version mismatch");return in;
    }
    static void write(DataOutputStream out,byte[] value)throws IOException{if(value!=null&&value.length>64*1024*1024)throw new IOException("carrier exceeds transport bounds");out.writeInt(value==null?-1:value.length);if(value!=null)out.write(value);}
    static byte[] read(DataInputStream in,boolean nullable)throws IOException{int size=in.readInt();if(nullable&&size==-1)return null;if(size<=0||size>64*1024*1024||size>in.available())throw new IOException("invalid carrier response size");return in.readNBytes(size);}
}
