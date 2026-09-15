package com.gameexpert.authority.versioned;

import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Registered producer bootstrap. Host codecs are never a fallback for a missing or invalid bundle. */
public final class ProducerAuthorities {
    private static final Map<WorldGenerationProfile,IsolatedProducerSession> SESSIONS=new HashMap<>();
    private static final Map<WorldGenerationProfile,IsolatedProducerSession> GENERATION_SESSIONS=new HashMap<>();
    private static final Map<WorldGenerationProfile,Map<Integer,NeutralFinalChunk.StateOverride>> DEFAULTS=new HashMap<>();
    private static final Map<WorldGenerationProfile,Map<Integer,NeutralFinalChunk.StateOverride>> DECODED=new HashMap<>();
    private static final Map<WorldGenerationProfile,Map<String,NeutralFinalChunk.StateOverride>> EXACT=new HashMap<>();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { closeAll(); } catch(RuntimeException failure) { System.err.println("Producer cleanup failed: "+failure.getMessage()); }
        },"generation-producer-cleanup"));
    }
    private ProducerAuthorities() { }

    /** Registry owns the returned session; consumers must not close it individually. */
    public static synchronized IsolatedProducerSession forProfile(WorldGenerationProfile value) {
        return session(SESSIONS, value);
    }

    /** Long terrain production cannot hold the codec lane needed by simulation and snapshots. */
    public static synchronized IsolatedProducerSession forGeneration(WorldGenerationProfile value) {
        return session(GENERATION_SESSIONS, value);
    }

    private static IsolatedProducerSession session(
            Map<WorldGenerationProfile,IsolatedProducerSession> sessions, WorldGenerationProfile value) {
        WorldGenerationProfile profile=WorldGenerationProfiles.requireSupported(value);
        IsolatedProducerSession existing=sessions.get(profile);
        if(existing!=null) return existing;
        Path root=ProducerBundle.directory();
        try(var manifest=ProducerAuthorities.class.getResourceAsStream("/generation-producers/current-worker.properties")) {
            var jars=ProducerRuntimeManifest.read(manifest,root,profile);
            byte[] sourceGraph;
            try (var graph = ProducerAuthorities.class.getResourceAsStream("/generation-producers/current-source-graph.json")) {
                if (graph == null) throw new IOException("current producer source graph missing");
                sourceGraph = graph.readNBytes(1024 * 1024 + 1);
            }
            IsolatedProducerSession created=new IsolatedProducerSession(jars,profile,jars.get(1).sha256(),sourceGraph);
            sessions.put(profile,created);
            return created;
        } catch(IOException failure) { throw new IllegalStateException("verified producer bundle unavailable; world data retained",failure); }
    }
    public static NeutralFinalChunk verify(WorldGenerationProfile profile,int chunkX,int chunkZ,byte[] carrier) {
        return NeutralFinalChunkWire.verify(forProfile(profile),WorldGenerationProfiles.requireSupported(profile),chunkX,chunkZ,carrier);
    }
    /** Default exact-state semantics are obtained from the same private codec even before a carrier exists. */
    public static synchronized NeutralFinalChunk.StateOverride defaultState(WorldGenerationProfile value,int blockId) {
        WorldGenerationProfile profile=WorldGenerationProfiles.requireSupported(value);
        if(blockId<0 || blockId>65535) throw new IllegalArgumentException("invalid default block ID");
        IsolatedProducerSession producer=forProfile(profile);
        return DEFAULTS.computeIfAbsent(profile,ignored->new HashMap<>()).computeIfAbsent(blockId,id->{
            try {
                var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
                out.writeInt(NeutralFinalChunkWire.MAGIC);out.writeByte(NeutralFinalChunkWire.VERSION);
                out.writeUTF(profile.getBaselineId());out.writeUTF(profile.getInputFingerprintSha256());out.writeUTF(profile.getGeneratorSourceSha256());
                out.writeInt(profile.getWorldVersion());out.writeInt(profile.getDataPackMajor());out.writeInt(profile.getResourcePackMajor());out.writeInt(profile.getProtocolVersion());
                out.writeByte(5);out.writeInt(1);out.writeInt(id);
                try(var in=new DataInputStream(new ByteArrayInputStream(producer.exchange(bytes.toByteArray())))) {
                    if(in.readInt()!=NeutralFinalChunkWire.MAGIC || in.readUnsignedByte()!=NeutralFinalChunkWire.VERSION || in.readInt()!=1 || in.readInt()!=id) throw new IOException("default state response identity mismatch");
                    String exact=in.readUTF();int leaves=in.readUnsignedByte();String fluid=in.readUTF();
                    if(exact.isEmpty() || leaves>1 || in.available()!=0) throw new IOException("invalid default state response");
                    return new NeutralFinalChunk.StateOverride(0,id,0,exact,leaves==1,fluid);
                }
            } catch(IOException invalid) { throw new IllegalStateException("isolated default-state response invalid",invalid); }
        });
    }
    public static synchronized NeutralFinalChunk.StateOverride decodeState(WorldGenerationProfile value,int blockId,int stateCode) {
        if(blockId<0 || blockId>65535 || stateCode<0 || stateCode>255) throw new IllegalArgumentException("invalid exact-state code");
        WorldGenerationProfile profile=WorldGenerationProfiles.requireSupported(value);
        return DECODED.computeIfAbsent(profile,ignored->new HashMap<>()).computeIfAbsent((blockId<<8)|stateCode,ignored->stateQuery(profile,11,blockId,stateCode,null));
    }
    public static synchronized NeutralFinalChunk.StateOverride exactState(WorldGenerationProfile value,String exact) {
        if(exact==null || exact.isEmpty() || exact.length()>4096) throw new IllegalArgumentException("invalid exact-state name");
        WorldGenerationProfile profile=WorldGenerationProfiles.requireSupported(value);
        return EXACT.computeIfAbsent(profile,ignored->new HashMap<>()).computeIfAbsent(exact,ignored->stateQuery(profile,12,-1,-1,exact));
    }
    private static NeutralFinalChunk.StateOverride stateQuery(WorldGenerationProfile value,int operation,int blockId,int code,String exactRequest) {
        WorldGenerationProfile profile=WorldGenerationProfiles.requireSupported(value);
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeInt(NeutralFinalChunkWire.MAGIC);out.writeByte(NeutralFinalChunkWire.VERSION);
            out.writeUTF(profile.getBaselineId());out.writeUTF(profile.getInputFingerprintSha256());out.writeUTF(profile.getGeneratorSourceSha256());
            out.writeInt(profile.getWorldVersion());out.writeInt(profile.getDataPackMajor());out.writeInt(profile.getResourcePackMajor());out.writeInt(profile.getProtocolVersion());
            out.writeByte(operation);
            if(operation==11) {out.writeInt(blockId);out.writeInt(code);} else out.writeUTF(exactRequest);
            try(var in=new DataInputStream(new ByteArrayInputStream(forProfile(profile).exchange(bytes.toByteArray())))) {
                if(in.readInt()!=NeutralFinalChunkWire.MAGIC || in.readUnsignedByte()!=NeutralFinalChunkWire.VERSION) throw new IOException("state response version mismatch");
                int id=in.readInt(),state=in.readInt();String exact=in.readUTF();int leaves=in.readUnsignedByte();String fluid=in.readUTF();
                if(id<0 || id>65535 || state<0 || state>255 || exact.isEmpty() || leaves>1 || in.available()!=0 ||
                        operation==11 && (id!=blockId || state!=code)) throw new IOException("state response identity mismatch");
                return new NeutralFinalChunk.StateOverride(0,id,state,exact,leaves==1,fluid);
            }
        } catch(IOException invalid) {throw new IllegalStateException("isolated exact-state response invalid",invalid);}
    }
    public static synchronized void closeAll() {
        RuntimeException failure=null;
        for(var sessions:java.util.List.of(SESSIONS,GENERATION_SESSIONS)) for(IsolatedProducerSession session:sessions.values()) {
            try { session.close(); } catch(IOException cleanup) {
                if(failure==null) failure=new IllegalStateException("producer cleanup failed",cleanup);else failure.addSuppressed(cleanup);
            }
        }
        SESSIONS.clear();GENERATION_SESSIONS.clear();DEFAULTS.clear();DECODED.clear();EXACT.clear();
        if(failure!=null) throw failure;
    }
}
