package com.gameexpert.authority.versioned;
import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.util.*;
/** Atomic, structure-only input to a selected producer. No final block arrays cross this snapshot. */
public final class CanonicalStructureSnapshot {
 public static final class Row {
  private final int x,z;private final byte[] carrier;
  public Row(int x,int z,byte[] carrier){this.x=x;this.z=z;if(carrier==null||carrier.length==0||carrier.length>16*1024*1024)throw new IllegalArgumentException("invalid structure row");this.carrier=carrier.clone();}
  public int chunkX(){return x;}public int chunkZ(){return z;}public byte[] carrier(){return carrier.clone();}
 }
 private final long worldId;private final WorldGenerationProfile profile;private final List<Row> rows;
 private final com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot references;
 public CanonicalStructureSnapshot(long worldId,WorldGenerationProfile profile,List<Row> rows){
  if(worldId<=0||rows==null||rows.size()>1_000_000)throw new IllegalArgumentException("invalid structure snapshot");this.worldId=worldId;this.profile=WorldGenerationProfiles.requireSupported(profile);
  var ordered=new ArrayList<>(rows);ordered.sort(Comparator.comparingInt(Row::chunkX).thenComparingInt(Row::chunkZ));long total=53;
  for(int i=0;i<ordered.size();i++){var row=ordered.get(i);total+=12L+row.carrier.length;if(total>64*1024*1024)throw new IllegalArgumentException("structure snapshot exceeds bounds");if(i>0&&row.x==ordered.get(i-1).x&&row.z==ordered.get(i-1).z)throw new IllegalArgumentException("duplicate structure snapshot row");}
  this.rows=List.copyOf(ordered);references=ProducerStoreWire.referencesRows(ProducerAuthorities.forProfile(this.profile),this.profile,worldId,this.rows);
 }
 public long worldId(){return worldId;}public WorldGenerationProfile profile(){return profile;}public List<Row> rows(){return rows;}
 public String receipt(){return references.receipt();}
 public com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot references(){return references;}
}
