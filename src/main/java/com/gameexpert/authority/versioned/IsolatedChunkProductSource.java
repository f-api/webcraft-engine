package com.gameexpert.authority.versioned;
import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.ChunkProductSource;
import com.gameexpert.terrain.CanonicalStoreChunkProductSource;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.*;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
/** One world-bound production/publication lane, with immutable committed replay outside the lock. */
public final class IsolatedChunkProductSource implements ChunkProductSource {
 private final CanonicalWorldgenStore store;private final long worldId,gameTime;private final int seed;
 private final WorldGenerationProfile profile;private final CanonicalStoreChunkProductSource replay;
 private final ReentrantLock production=new ReentrantLock(true);
 public IsolatedChunkProductSource(CanonicalWorldgenStore store,long worldId,int seed,WorldGenerationProfile profile,long gameTime){
  this.store=Objects.requireNonNull(store);if(worldId<=0||gameTime<0)throw new IllegalArgumentException("invalid producer world/time");
  this.worldId=worldId;this.seed=seed;this.gameTime=gameTime;this.profile=WorldGenerationProfiles.requireSupported(profile);replay=new CanonicalStoreChunkProductSource(store,worldId,this.profile);
 }
 @Override public WorldGenerationProfile generationProfile(){return profile;}
 @Override public ChunkGenerator.GeneratedChunk generate(int requestedSeed,int x,int z){
  requireSeed(requestedSeed);var existing=replay.replayIfCommitted(x,z);if(existing!=null)return existing;
  production.lock();try{return generateLocked(x,z);}finally{production.unlock();}
 }
 public boolean prefetch(int requestedSeed,int x,int z){
  requireSeed(requestedSeed);if(store.isCommitted(worldId,x,z))return true;
  if(production.hasQueuedThreads()||!production.tryLock())return false;
  try{generateLocked(x,z);return true;}finally{production.unlock();}
 }
 private ChunkGenerator.GeneratedChunk generateLocked(int x,int z){
  for(int attempt=0;attempt<32;attempt++){
   var existing=replay.replayIfCommitted(x,z);if(existing!=null)return existing;
   CanonicalStructureSnapshot snapshot=store.structureSnapshot(worldId,profile);
   CanonicalWorldgenStore.ChunkCommit proposal=produce(snapshot,x,z);
   if(store.commitFromSnapshot(proposal,snapshot.receipt())){
    var committed=replay.replayIfCommitted(x,z);if(committed==null)throw new IllegalStateException("canonical publication disappeared");return committed;
   }
  }
  throw new IllegalStateException("canonical reference snapshot changed repeatedly during production");
 }
 private CanonicalWorldgenStore.ChunkCommit produce(CanonicalStructureSnapshot snapshot,int x,int z){
  if(snapshot.worldId()!=worldId||snapshot.profile()!=profile)throw new IllegalStateException("producer received a foreign world snapshot");
  try{
   var bytes=new ByteArrayOutputStream();var out=ProducerStoreWire.header(bytes,profile,13);
   out.writeLong(worldId);out.writeInt(seed);out.writeLong(gameTime);out.writeInt(x);out.writeInt(z);out.writeUTF(snapshot.receipt());out.writeInt(snapshot.rows().size());
   for(var row:snapshot.rows()){out.writeInt(row.chunkX());out.writeInt(row.chunkZ());ProducerStoreWire.write(out,row.carrier());}out.flush();
   var session=ProducerAuthorities.forGeneration(profile);
   try(var in=ProducerStoreWire.response(session.exchange(bytes.toByteArray()))){
    if(in.readLong()!=worldId||in.readInt()!=seed||in.readInt()!=x||in.readInt()!=z||!in.readUTF().equals(snapshot.receipt()))throw new IOException("generation proposal binding mismatch");
    byte[] fin=ProducerStoreWire.read(in,false),structure=ProducerStoreWire.read(in,false),successor=ProducerStoreWire.read(in,true),fingerprint=ProducerStoreWire.read(in,false);
    if(in.available()!=0)throw new IOException("trailing generation proposal");
    var proposal=new CanonicalWorldgenStore.ChunkCommit(worldId,profile.getBaselineId(),x,z,fin,structure,successor,fingerprint);
    if(!java.util.Arrays.equals(fin,proposal.finalCarrier()))throw new IOException("verified generation carrier changed at commit boundary");
    proposal.semanticFinalChunk().bindGenerationSeed(seed);
    return proposal;
   }
  }catch(IOException malformed){throw new IllegalStateException("invalid generation transport",malformed);}
 }
 private void requireSeed(int requested){if(requested!=seed)throw new IllegalArgumentException("producer world seed mismatch");}
}
