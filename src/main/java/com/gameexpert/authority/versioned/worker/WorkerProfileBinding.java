package com.gameexpert.authority.versioned.worker;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** External generation identity and internal producer input pin are deliberately separate. */
public final class WorkerProfileBinding {
 private static String[] text;
 private static int[] numbers;
 private WorkerProfileBinding() { }
 public static synchronized void configure(byte[] bytes)throws Exception{
  if(text!=null)throw new IllegalStateException("producer profile is already configured");
  if(bytes==null||bytes.length==0||bytes.length>1024*1024+2048)throw new IllegalArgumentException("invalid producer configuration bounds");
  try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
   if(in.readInt()!=0x57504331||in.readUnsignedByte()!=1)throw new IllegalArgumentException("unsupported producer configuration");
   String[] configuredText={in.readUTF(),in.readUTF(),in.readUTF()};int[] configuredNumbers={in.readInt(),in.readInt(),in.readInt(),in.readInt()};String expected=in.readUTF();
   int graphLength=in.readInt();if(graphLength<0||graphLength>1024*1024||graphLength>in.available())throw new IllegalArgumentException("invalid source graph bounds");
   byte[] graph=in.readNBytes(graphLength);
   if(in.available()!=0||configuredText[0].isBlank()||configuredText[0].length()>160||!configuredText[1].matches("[0-9a-f]{64}")||!configuredText[2].matches("[0-9a-f]{64}")||!expected.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("invalid producer configuration fields");
   Class<?> base=Class.forName("com.gameexpert.world.WorldBaseline");
   var location=base.getProtectionDomain().getCodeSource().getLocation();
   if(!"file".equals(location.getProtocol()))throw new IllegalStateException("producer origin is not a pinned file");
   Path archive=Path.of(location.toURI());if(!Files.isRegularFile(archive))throw new IllegalStateException("producer origin is not an archive");
   var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(archive)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
   if(!MessageDigest.isEqual(HexFormat.of().parseHex(expected),digest.digest()))throw new IllegalStateException("configured producer archive does not own the loaded base classes");
   if(graph.length==0){
    String[] fields={"ID","INPUT_FINGERPRINT_SHA256","GENERATOR_SOURCE_SHA256"};
    for(int i=0;i<fields.length;i++)if(!configuredText[i].equals(base.getField(fields[i]).get(null)))throw new IllegalArgumentException("legacy profile mismatch");
    if(!expected.equals("a7fa9068f6c4891aa04c478fcf983b117b1e8daf19c41b38d6a92c31db6117c0")||!Arrays.equals(configuredNumbers,new int[]{5009,115,95,1073742153}))throw new IllegalArgumentException("unreviewed legacy archive/profile");
   }else{
    var sourceDigest=MessageDigest.getInstance("SHA-256");sourceDigest.update("WEBCRAFT-GENERATION-SOURCE-GRAPH-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    if(!HexFormat.of().formatHex(sourceDigest.digest(graph)).equals(configuredText[2]))throw new IllegalArgumentException("source graph identity mismatch");
    var object=new tools.jackson.databind.ObjectMapper().readTree(graph);
    if(!expected.equals(object.get("producerArchiveSha256").asString()))throw new IllegalArgumentException("source graph producer archive mismatch");
   }
   numbers=configuredNumbers;text=configuredText;
  }
 }
 public static synchronized void requireRequest(DataInputStream in)throws IOException{
  if(text==null)throw new IllegalStateException("producer profile has not been configured");
  for(String expected:text)if(!expected.equals(in.readUTF()))throw new IllegalArgumentException("producer outer profile mismatch");
  for(int expected:numbers)if(expected!=in.readInt())throw new IllegalArgumentException("producer outer profile version mismatch");
 }
}
