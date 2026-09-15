package com.gameexpert.authority.versioned.worker;
import com.gameexpert.terrain.CanonicalPostprocessActivationContext;
import com.gameexpert.terrain.Mc263BaseHeightSampler;
import com.gameexpert.terrain.Mc263FeaturesRegionBridge;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrierOrigin;
import java.io.*;
/** Production generation transfers only the complete immutable structure-reference snapshot. */
public final class LegacyStructureGenerationRequest {
 private LegacyStructureGenerationRequest() { }
 private static GenerationContext cached;
 private static final class GenerationContext implements AutoCloseable {
  final long worldId; final int seed;
  final Mc263StructureCarrierOrigin.WorldContext world;
  final Mc263StructureCarrierOrigin.RegionMemo structures=Mc263StructureCarrierOrigin.RegionMemo.bounded(9);
  final Mc263FeaturesRegionBridge.RegionMemo features=Mc263FeaturesRegionBridge.RegionMemo.bounded(25);
  final Mc263FeaturesRegionBridge.InputBuilderContext builders=Mc263FeaturesRegionBridge.newInputBuilderContext(1);
  GenerationContext(long worldId,int seed){this.worldId=worldId;this.seed=seed;world=Mc263StructureCarrierOrigin.prepare(seed,Mc263BaseHeightSampler.overworld(seed));}
  public void close(){builders.close();}
 }
 // The owning producer session serializes requests; only immutable world inputs are reused.
 private static GenerationContext context(long worldId,int seed){
  if(cached==null||cached.worldId!=worldId||cached.seed!=seed){close();cached=new GenerationContext(worldId,seed);}
  return cached;
 }
 static void close(){if(cached!=null){cached.close();cached=null;}}

 public static byte[] execute(DataInputStream in)throws Exception{
  long worldId=in.readLong();int seed=in.readInt();long time=in.readLong();int x=in.readInt(),z=in.readInt();String expected=in.readUTF();
  if(worldId<=0||time<0||!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid generation snapshot binding");
  var packed=new ByteArrayOutputStream();var snapshotOut=new DataOutputStream(packed);snapshotOut.writeLong(worldId);in.transferTo(snapshotOut);snapshotOut.flush();
  var snapshot=LegacyStoreOperations.readReferences(new DataInputStream(new ByteArrayInputStream(packed.toByteArray())));
  if(!expected.equals(snapshot.receipt()))throw new IllegalArgumentException("structure snapshot receipt mismatch");
  var declarationWitness=WorkerClaimSnapshot.generationWitness(snapshot);
  var maps=Mc263LocatedMapAuthority.pinnedBiomePreview(requested->{if(requested!=seed)throw new IllegalArgumentException("foreign snapshot seed");return declarationWitness;});
  var contexts=Mc263ProductionContextCatalog.liveProvider(maps);
  var context=context(worldId,seed);
  var assembly=Mc263StructureCarrierOrigin.assemble(context.world,x,z,context.structures);
  {
   var pending=Mc263FeaturesRegionBridge.startPostCarversInput(context.builders,seed,x,z,assembly.carrier(),context.features);
   var product=Mc263FeaturesRegionBridge.generateCanonicalProductFromInput(seed,x,z,assembly.carrier(),contexts,new CanonicalPostprocessActivationContext(time),pending.finish());
   product.verifiedFinalChunk(seed,x,z); // Preserve the original product provenance check before projection.
   var result=new ByteArrayOutputStream();var out=new DataOutputStream(result);out.writeInt(0x57504731);out.writeByte(1);out.writeLong(worldId);out.writeInt(seed);out.writeInt(x);out.writeInt(z);out.writeUTF(expected);
   byte[] successor=product.structureCarrier().receiptBytes();for(byte[] raw:new byte[][]{product.finalCarrier(),successor,successor,product.commitFingerprint()}){out.writeInt(raw.length);out.write(raw);}out.flush();return result.toByteArray();
  }
 }
}
