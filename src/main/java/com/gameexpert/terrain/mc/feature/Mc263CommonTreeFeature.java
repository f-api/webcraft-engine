package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Exact dormant common straight/blob/bush and fallen-tree kernel for 26.3-snapshot-7. */
public final class Mc263CommonTreeFeature {
    /** The pinned namespaced-key grammar, compiled once: {@code String.matches} recompiles
     * this pattern on every call, and the key validator runs once per read state. */
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final int TRACE_MAGIC = 0x43544633; // CTF3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-common-tree-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String TREE_CLASS_SHA256 = "bbba55eac5b4991411cf3a59388499f928e70497a750e437c3e09acdcae0c1ce";
    public static final String FALLEN_CLASS_SHA256 = "609c1adac92f00da7055c04cfddd644316beca561673758a404b9984871249a7";
    public static final String STRAIGHT_TRUNK_CLASS_SHA256 = "58cb1999f7d36993094e287e89a166f4c0c15001acf3e01f91e77d4b42ab4c09";
    public static final String BLOB_FOLIAGE_CLASS_SHA256 = "05e3c52e2dcb72cf26894bb57b11aa46cf9c93a59f900847872cd5ed2f796773";

    private static final Map<String, Spec> SPECS = specs();
    private static final Map<String, FallenSpec> FALLEN = fallenSpecs();
    private static final Map<String, Phase> PHASES = Map.ofEntries(
            Map.entry("preflight", new Phase(1, 2)), Map.entry("rng_int", new Phase(2, 3)),
            Map.entry("rng_float", new Phase(3, 3)), Map.entry("read", new Phase(4, 6)),
            Map.entry("predicate", new Phase(5, 6)), Map.entry("write", new Phase(6, 8)),
            Map.entry("height", new Phase(7, 6)), Map.entry("foliage", new Phase(8, 7)),
            Map.entry("decorator", new Phase(9, 6)), Map.entry("bee", new Phase(10, 6)),
            Map.entry("finish", new Phase(11, 5)), Map.entry("result", new Phase(12, 6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) { throw new AssertionError(); }
    };

    private Mc263CommonTreeFeature() { }

    public record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
    }
    public record State(String block, Map<String, String> properties, boolean air,
                        boolean replaceableByTrees, boolean log, boolean leaves,
                        boolean persistent, boolean waterSource, boolean solidRender) {
        public State { block = key(block); properties = Collections.unmodifiableMap(new TreeMap<>(properties)); }
        public static State of(String block) {
            return new State(block, Map.of(), false, false, false, false, false, false, true);
        }
        public State property(String name, String value) {
            Map<String,String> next = new TreeMap<>(properties); next.put(name, value);
            return new State(block, next, air, replaceableByTrees, log, leaves, persistent,
                    waterSource, solidRender);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream().map(e -> e.getKey()+"="+e.getValue())
                    .reduce("[",(a,b)->a.equals("[")?a+b:a+","+b)+"]";
        }
    }
    public interface WorldAccess {
        int minY(); int maxY();
        State state(Pos pos);
        default boolean supportsInternalPredicates() { return false; }
        default State internalPredicateState(Pos pos) {
            throw new UnsupportedOperationException("internal tree state predicates");
        }
        default boolean internalFluidIsWater(Pos pos) {
            throw new UnsupportedOperationException("internal tree fluid predicates");
        }
        boolean cannotReplaceBelowTreeTrunk(State state);
        boolean canSaplingSurvive(State sapling, Pos pos);
        boolean supportsFeature(String configuredKey);
        boolean supportsState(State state);
        boolean supportsBeeNestPayload();
        boolean supportsTreeFinalization();
        boolean supportsPostProcessing();
        boolean set(Pos pos, State state, int flags);
        boolean setAndUpdate(Pos pos, State state);
        void markAboveForPostProcessing(Pos pos);
        boolean sturdyUp(Pos below, Pos queriedFrom);
        int motionBlockingNoLeaves(Pos pos);
        void storeBee(Pos nest, int ticksInHive);
        void finishTree(Set<Pos> logs, Set<Pos> leaves, Set<Pos> roots,
                        Set<Pos> decorations);
    }
    public interface TraceSink { default boolean enabled(){return true;} void record(String p,long...v); }
    public record TraceEvent(String phase,long[] values){ public TraceEvent{values=values.clone();} @Override public long[] values(){return values.clone();} }
    public record Result(boolean placed,int reads,int writes,int retained,int bees,int postprocess) { }

    public static List<String> configuredKeys() { return SPECS.keySet().stream().sorted().toList(); }
    public static List<String> fallenKeys() { return FALLEN.keySet().stream().sorted().toList(); }

    /** Validates the complete configured-tree capability closure without live world access. */
    public static void preflightConfigured(String key, WorldAccess world) {
        preflight(requireSpec(key), world, null);
    }

    /** Validates configured-tree and checked-sapling capabilities without live world access. */
    public static void preflightChecked(String key, State sapling, WorldAccess world) {
        preflight(requireSpec(key), world, sapling);
    }

    /** Validates the complete fallen-tree capability closure without live world access. */
    public static void preflightFallen(String key, WorldAccess world) {
        FallenSpec spec = FALLEN.get(normalize(key));
        if (spec == null) throw new IllegalArgumentException(key);
        preflight(spec, world);
    }

    public static Result placeChecked(String key, State sapling, Mc263WorldgenRandomSource random,
            Pos origin, WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(key); preflightChecked(key, sapling, world);
        emit(trace,"preflight",id(spec.key),1);
        boolean survives = world.canSaplingSurvive(sapling, origin);
        emit(trace,"predicate",id(key),origin.x,origin.y,origin.z,survives?1:0,1);
        if (!survives) return new Result(false,0,0,0,0,0);
        return place(spec, random, origin, world, trace);
    }
    public static Result place(String key, Mc263WorldgenRandomSource random, Pos origin, WorldAccess world) {
        return place(key,random,origin,world,NO_TRACE);
    }
    public static Result place(String key, Mc263WorldgenRandomSource random, Pos origin, WorldAccess world,
            TraceSink trace) {
        Spec spec=requireSpec(key); preflightConfigured(key,world); emit(trace,"preflight",id(spec.key),0); return place(spec,random,origin,world,trace);
    }

    private static Result place(Spec spec, Mc263WorldgenRandomSource random, Pos origin, WorldAccess world,
            TraceSink trace) {
        Counts c=new Counts();
        int height=spec.baseHeight+nextInt(random,spec.randA+1,100,trace)
                +nextInt(random,spec.randB+1,101,trace);
        int foliageHeight=spec.foliageHeight, radius=spec.radius;
        emit(trace,"height",id(spec.key),height,foliageHeight,radius,origin.y,0);
        if(origin.y<world.minY()+1||origin.y+height+1>world.maxY()+1) return result(false,c,trace,spec.key);
        int clipped=maxFree(spec,height,origin,world,trace,c);
        if(clipped<height) return result(false,c,trace,spec.key);
        Set<Pos> logs=new HashSet<>(), leaves=new HashSet<>(), decorations=new HashSet<>();
        Pos below=origin.offset(0,-1,0); State belowState=read(world,below,trace,c);
        if(!world.cannotReplaceBelowTreeTrunk(belowState)) write(world,below,State.of("minecraft:dirt"),19,trace,c,logs);
        for(int y=0;y<clipped;y++) {
            Pos pos=origin.offset(0,y,0); State live=predicateState(world,pos,trace,c);
            if(valid(live)) write(world,pos,axis(spec.log,"y"),19,trace,c,logs);
        }
        Pos crown=origin.offset(0,clipped,0);
        int offset=spec.foliageOffset;
        for(int yo=offset;yo>=offset-foliageHeight;yo--) {
            int rowRadius=spec.bush ? radius-1-yo : Math.max(radius-1-yo/2,0);
            for(int dx=-rowRadius;dx<=rowRadius;dx++) for(int dz=-rowRadius;dz<=rowRadius;dz++) {
                int ax=Math.abs(dx),az=Math.abs(dz);
                boolean skip=ax==rowRadius&&az==rowRadius
                        &&(nextInt(random,2,200,trace)==0||(!spec.bush&&yo==0));
                emit(trace,"foliage",crown.x+dx,crown.y+yo,crown.z+dz,rowRadius,yo,skip?1:0,spec.bush?1:0);
                if(skip)continue;
                Pos p=crown.offset(dx,yo,dz);
                if(world.supportsInternalPredicates()){
                    if(predicateState(world,p,trace,c).persistent)continue;
                    if(!valid(predicateState(world,p,trace,c)))continue;
                    write(world,p,leaves(spec.leaf,predicateWater(world,p,c)),19,trace,c,leaves);
                }else{
                    State live=read(world,p,trace,c);
                    if(live.persistent||!valid(live))continue;
                    write(world,p,leaves(spec.leaf,live.waterSource),19,trace,c,leaves);
                }
            }
        }
        if(logs.isEmpty()&&leaves.isEmpty())return result(false,c,trace,spec.key);
        List<Pos> orderedLogs=decoratorOrder(logs,world), orderedLeaves=decoratorOrder(leaves,world);
        for(Decorator decorator:spec.decorators) decorate(decorator,orderedLogs,orderedLeaves,
                random,world,trace,c,decorations);
        // Set.copyOf deliberately randomizes encounter order between JVM starts. Tree
        // finalization consumes that order, so retain the already-authenticated HashSet walk.
        world.finishTree(Collections.unmodifiableSet(new LinkedHashSet<>(logs)),
                Collections.unmodifiableSet(new LinkedHashSet<>(leaves)), Set.of(),
                Collections.unmodifiableSet(new LinkedHashSet<>(decorations)));
        emit(trace,"finish",logs.size(),leaves.size(),decorations.size(),c.bees,c.writes);
        return result(true,c,trace,spec.key);
    }

    public static Result placeFallen(String key, Mc263WorldgenRandomSource random, Pos origin,
            WorldAccess world, TraceSink trace) {
        FallenSpec spec=FALLEN.get(normalize(key)); if(spec==null)throw new IllegalArgumentException(key);
        preflightFallen(key,world); emit(trace,"preflight",id(spec.key),0); Counts c=new Counts(); Set<Pos> stump=new HashSet<>();
        State log=axis(spec.log,"y"); writeUpdate(world,origin,log,trace,c,stump);
        if(spec.stumpVines) trunkVines(ordered(stump),random,world,trace,c,new HashSet<>());
        Direction direction=Direction.H[nextInt(random,4,300,trace)];
        int length=spec.min+nextInt(random,spec.max-spec.min+1,301,trace)-2;
        int distance=2+nextInt(random,2,302,trace);
        Pos start=origin.offset(direction.dx*distance,1,direction.dz*distance);
        for(int i=0;i<6;i++){if(valid(read(world,start,trace,c))&&world.sturdyUp(start.offset(0,-1,0),start))break;start=start.offset(0,-1,0);}
        Pos cursor=start;int gap=0;boolean can=true;
        for(int i=0;i<length;i++){State live=read(world,cursor,trace,c);if(!valid(live)){can=false;break;}if(!world.sturdyUp(cursor.offset(0,-1,0),cursor)){if(++gap>2){can=false;break;}}else gap=0;cursor=cursor.offset(direction.dx,0,direction.dz);}
        Set<Pos> fallen=new HashSet<>();
        if(can){cursor=start;for(int i=0;i<length;i++){writeUpdate(world,cursor,axis(spec.log,direction.axis),trace,c,fallen);cursor=cursor.offset(direction.dx,0,direction.dz);}attachedMushrooms(ordered(fallen),random,world,trace,c,new HashSet<>());}
        return result(true,c,trace,spec.key);
    }

    private static int maxFree(Spec spec,int height,Pos origin,WorldAccess world,TraceSink trace,Counts c){
        for(int y=0;y<=height+1;y++){int r=y<spec.sizeLimit?spec.lowerSize:spec.upperSize;
            for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){State s=predicateState(world,origin.offset(x,y,z),trace,c);if(!(valid(s)||s.log)||(!spec.ignoreVines&&s.block.equals("minecraft:vine")))return y-2;}}
        return height;
    }

    private static void decorate(Decorator d,List<Pos> logs,List<Pos> leaves,Mc263WorldgenRandomSource r,
            WorldAccess w,TraceSink t,Counts c,Set<Pos> out){
        emit(t,"decorator",d.type.ordinal(),logs.size(),leaves.size(),Float.floatToRawIntBits(d.probability),d.a,d.b);
        switch(d.type){case TRUNK_VINE->trunkVines(logs,r,w,t,c,out);case LEAVE_VINE->leaveVines(leaves,d.probability,r,w,t,c,out);case COCOA->cocoa(logs,d.probability,r,w,t,c,out);case BEEHIVE->beehive(logs,leaves,d.probability,r,w,t,c,out);case GROUND->ground(logs,d.a,d.b,d.c,r,w,t,c,out);}
    }
    private static void trunkVines(List<Pos> logs,Mc263WorldgenRandomSource r,WorldAccess w,TraceSink t,Counts c,Set<Pos> out){for(Pos p:logs)for(Direction d:Direction.VINE_ORDER){if(nextInt(r,3,400,t)>0){Pos q=p.offset(d.dx,0,d.dz);if(predicateState(w,q,t,c).air)write(w,q,vine(d.oppositeFace),19,t,c,out);}}}
    private static void leaveVines(List<Pos> leaves,float chance,Mc263WorldgenRandomSource r,WorldAccess w,TraceSink t,Counts c,Set<Pos> out){for(Pos p:leaves)for(Direction d:Direction.VINE_ORDER){if(nextFloat(r,chance,410,t)){Pos q=p.offset(d.dx,0,d.dz);if(predicateState(w,q,t,c).air){if(w.supportsInternalPredicates()){write(w,q,vine(d.oppositeFace),19,t,c,out);q=q.offset(0,-1,0);for(int i=0;i<4;i++){if(!predicateState(w,q,t,c).air)break;write(w,q,vine(d.oppositeFace),19,t,c,out);q=q.offset(0,-1,0);}}else{for(int i=0;i<5;i++){if(!predicateState(w,q,t,c).air)break;write(w,q,vine(d.oppositeFace),19,t,c,out);q=q.offset(0,-1,0);}}}}}}
    private static void cocoa(List<Pos> logs,float chance,Mc263WorldgenRandomSource r,WorldAccess w,TraceSink t,Counts c,Set<Pos> out){if(!nextFloat(r,chance,420,t)||logs.isEmpty())return;int base=logs.getFirst().y;for(Pos p:logs)if(p.y-base<=2)for(Direction d:Direction.H)if(nextFloat(r,.25f,421,t)){Pos q=p.offset(-d.dx,0,-d.dz);if(predicateState(w,q,t,c).air)write(w,q,State.of("minecraft:cocoa").property("age",Integer.toString(nextInt(r,3,422,t))).property("facing",d.key),19,t,c,out);}}
    private static void beehive(List<Pos> logs,List<Pos> leaves,float chance,Mc263WorldgenRandomSource r,WorldAccess w,TraceSink t,Counts c,Set<Pos> out){if(logs.isEmpty()||!nextFloat(r,chance,430,t))return;int y=!leaves.isEmpty()?Math.max(leaves.getFirst().y-1,logs.getFirst().y+1):Math.min(logs.getFirst().y+1+nextInt(r,3,431,t),logs.getLast().y);List<Pos> choices=new ArrayList<>();for(Pos p:logs)if(p.y==y)for(Direction d:List.of(Direction.E,Direction.S,Direction.W))choices.add(p.offset(d.dx,0,d.dz));shuffle(choices,r,t);for(Pos p:choices)if(predicateState(w,p,t,c).air&&predicateState(w,p.offset(0,0,1),t,c).air){write(w,p,State.of("minecraft:bee_nest").property("facing","south").property("honey_level","0"),19,t,c,out);int n=2+nextInt(r,2,432,t);for(int i=0;i<n;i++){int ticks=nextInt(r,599,433,t);w.storeBee(p,ticks);c.bees++;emit(t,"bee",p.x,p.y,p.z,ticks,i,n);}break;}}
    private static void ground(List<Pos> logs,int tries,int radius,int maxAmount,Mc263WorldgenRandomSource r,WorldAccess w,TraceSink t,Counts c,Set<Pos> out){if(logs.isEmpty())return;int minY=logs.getFirst().y,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;for(Pos p:logs)if(p.y==minY){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);}for(int i=0;i<tries;i++){int x=minX-radius+nextInt(r,maxX-minX+2*radius+1,440,t),y=minY-2+nextInt(r,5,441,t),z=minZ-radius+nextInt(r,maxZ-minZ+2*radius+1,442,t);Pos p=new Pos(x,y,z),above=p.offset(0,1,0);State a=read(w,above,t,c),base=read(w,p,t,c);if((a.air||a.block.equals("minecraft:vine"))&&base.solidRender&&w.motionBlockingNoLeaves(p)<=above.y){int pick=nextInt(r,maxAmount*4,443,t);int amount=1+pick/4;Direction face=Direction.H[pick%4];write(w,above,State.of("minecraft:leaf_litter").property("facing",face.key).property("segment_amount",Integer.toString(amount)),19,t,c,out);}}}
    private static void attachedMushrooms(List<Pos> logs,Mc263WorldgenRandomSource r,WorldAccess w,TraceSink t,Counts c,Set<Pos> out){List<Pos> shuffled=new ArrayList<>(logs);shuffle(shuffled,r,t);for(Pos p:shuffled){nextInt(r,1,450,t);Pos q=p.offset(0,1,0);if(nextFloat(r,.1f,451,t)&&read(w,q,t,c).air){int pick=nextInt(r,3,452,t);write(w,q,State.of(pick<2?"minecraft:red_mushroom":"minecraft:brown_mushroom"),19,t,c,out);}}}

    private static void preflight(Spec s,WorldAccess w,State sapling){if(!w.supportsFeature(s.key))throw new UnsupportedOperationException(s.key);if(!w.supportsTreeFinalization())throw new UnsupportedOperationException("tree finalization");List<State> required=new ArrayList<>(List.of(axis(s.log,"x"),axis(s.log,"y"),axis(s.log,"z"),State.of("minecraft:dirt")));for(int distance=1;distance<=7;distance++){required.add(leaves(s.leaf,distance,false));required.add(leaves(s.leaf,distance,true));}if(sapling!=null)required.add(sapling);for(Decorator d:s.decorators){if(d.type==Type.BEEHIVE){required.add(State.of("minecraft:bee_nest").property("facing","south").property("honey_level","0"));if(!w.supportsBeeNestPayload())throw new UnsupportedOperationException("beehive payload");}if(d.type==Type.TRUNK_VINE||d.type==Type.LEAVE_VINE)for(Direction face:Direction.H)required.add(vine(face.key));if(d.type==Type.COCOA)for(int age=0;age<3;age++)for(Direction face:Direction.H)required.add(State.of("minecraft:cocoa").property("age",Integer.toString(age)).property("facing",face.key));if(d.type==Type.GROUND)for(int amount=1;amount<=d.c;amount++)for(Direction face:Direction.H)required.add(State.of("minecraft:leaf_litter").property("facing",face.key).property("segment_amount",Integer.toString(amount)));}for(State state:required)if(!w.supportsState(state))throw new UnsupportedOperationException(state.canonical());}
    private static void preflight(FallenSpec s,WorldAccess w){if(!w.supportsFeature(s.key))throw new UnsupportedOperationException(s.key);if(!w.supportsPostProcessing())throw new UnsupportedOperationException("postprocessing");List<State> required=new ArrayList<>(List.of(axis(s.log,"x"),axis(s.log,"y"),axis(s.log,"z"),State.of("minecraft:red_mushroom"),State.of("minecraft:brown_mushroom")));if(s.stumpVines)for(Direction d:Direction.H)required.add(vine(d.key));for(State state:required)if(!w.supportsState(state))throw new UnsupportedOperationException(state.canonical());}
    private static Spec requireSpec(String key){Spec s=SPECS.get(normalize(key));if(s==null)throw new IllegalArgumentException(key);return s;}
    private static Map<String,Spec> specs(){Map<String,Spec> m=new LinkedHashMap<>();List<Decorator> litter=List.of(Decorator.ground(96,4,3),Decorator.ground(150,2,4));add(m,new Spec("minecraft:oak","minecraft:oak_log","minecraft:oak_leaves",4,2,0,2,0,3,false,1,0,1,true,List.of()));add(m,new Spec("minecraft:birch","minecraft:birch_log","minecraft:birch_leaves",5,2,0,2,0,3,false,1,0,1,true,List.of()));add(m,new Spec("minecraft:jungle_tree_no_vine","minecraft:jungle_log","minecraft:jungle_leaves",4,8,0,2,0,3,false,1,0,1,true,List.of()));add(m,new Spec("minecraft:jungle_tree","minecraft:jungle_log","minecraft:jungle_leaves",4,8,0,2,0,3,false,1,0,1,true,List.of(Decorator.cocoa(.2f),Decorator.trunk(),Decorator.leaf(.25f))));add(m,new Spec("minecraft:jungle_bush","minecraft:jungle_log","minecraft:oak_leaves",1,0,0,2,1,2,true,0,0,0,false,List.of()));add(m,new Spec("minecraft:swamp_oak","minecraft:oak_log","minecraft:oak_leaves",5,3,0,3,0,3,false,1,0,1,false,List.of(Decorator.leaf(.25f))));add(m,new Spec("minecraft:oak_bees_002","minecraft:oak_log","minecraft:oak_leaves",4,2,0,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.02f))));add(m,new Spec("minecraft:oak_bees_005","minecraft:oak_log","minecraft:oak_leaves",4,2,0,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.05f))));add(m,new Spec("minecraft:oak_bees_0002_leaf_litter","minecraft:oak_log","minecraft:oak_leaves",4,2,0,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.002f),litter.get(0),litter.get(1))));add(m,new Spec("minecraft:birch_bees_0002","minecraft:birch_log","minecraft:birch_leaves",5,2,0,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.002f))));add(m,new Spec("minecraft:birch_bees_002","minecraft:birch_log","minecraft:birch_leaves",5,2,0,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.02f))));add(m,new Spec("minecraft:birch_bees_0002_leaf_litter","minecraft:birch_log","minecraft:birch_leaves",5,2,0,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.002f),litter.get(0),litter.get(1))));add(m,new Spec("minecraft:super_birch_bees_0002","minecraft:birch_log","minecraft:birch_leaves",5,2,6,2,0,3,false,1,0,1,true,List.of(Decorator.bee(.002f))));add(m,new Spec("minecraft:super_birch_bees","minecraft:birch_log","minecraft:birch_leaves",5,2,6,2,0,3,false,1,0,1,true,List.of(Decorator.bee(1f))));add(m,new Spec("minecraft:oak_leaf_litter","minecraft:oak_log","minecraft:oak_leaves",4,2,0,2,0,3,false,1,0,1,true,litter));add(m,new Spec("minecraft:birch_leaf_litter","minecraft:birch_log","minecraft:birch_leaves",5,2,0,2,0,3,false,1,0,1,true,litter));return Map.copyOf(m);}
    private static Map<String,FallenSpec> fallenSpecs(){Map<String,FallenSpec> m=new LinkedHashMap<>();m.put("minecraft:fallen_oak_tree",new FallenSpec("minecraft:fallen_oak_tree","minecraft:oak_log",4,7,true));m.put("minecraft:fallen_birch_tree",new FallenSpec("minecraft:fallen_birch_tree","minecraft:birch_log",5,8,false));m.put("minecraft:fallen_super_birch_tree",new FallenSpec("minecraft:fallen_super_birch_tree","minecraft:birch_log",5,15,false));m.put("minecraft:fallen_jungle_tree",new FallenSpec("minecraft:fallen_jungle_tree","minecraft:jungle_log",4,11,true));m.put("minecraft:fallen_spruce_tree",new FallenSpec("minecraft:fallen_spruce_tree","minecraft:spruce_log",6,10,false));return Map.copyOf(m);}
    private static void add(Map<String,Spec> m,Spec s){m.put(s.key,s);}
    private static State read(WorldAccess w,Pos p,TraceSink t,Counts c){State s=w.state(p);c.reads++;emit(t,"read",p.x,p.y,p.z,id(s.canonical()),s.air?1:0,s.replaceableByTrees?1:0);return s;}
    private static State predicateState(WorldAccess w,Pos p,TraceSink t,Counts c){
        if(!w.supportsInternalPredicates())return read(w,p,t,c);
        State s=w.internalPredicateState(p);c.reads++;return s;
    }
    private static boolean predicateWater(WorldAccess w,Pos p,Counts c){
        if(!w.supportsInternalPredicates())throw new IllegalStateException("internal tree predicates disabled");
        c.reads++;return w.internalFluidIsWater(p);
    }
    private static void write(WorldAccess w,Pos p,State s,int f,TraceSink t,Counts c,Set<Pos> set){boolean kept=w.set(p,s,f);c.writes++;if(kept)c.retained++;set.add(p);emit(t,"write",p.x,p.y,p.z,id(s.canonical()),f,kept?1:0,s.properties.hashCode(),id(s.block));}
    private static void writeUpdate(WorldAccess w,Pos p,State s,TraceSink t,Counts c,Set<Pos> set){boolean kept=w.setAndUpdate(p,s);c.writes++;if(kept)c.retained++;set.add(p);w.markAboveForPostProcessing(p);c.post++;emit(t,"write",p.x,p.y,p.z,id(s.canonical()),3,kept?1:0,s.properties.hashCode(),id(s.block));}
    private static boolean valid(State s){return s.air||s.replaceableByTrees;}
    private static State axis(String block,String axis){return State.of(block).property("axis",axis);}
    private static State leaves(String block,boolean water){return leaves(block,7,water);}
    private static State leaves(String block,int distance,boolean water){return new State(block,Map.of("distance",Integer.toString(distance),"persistent","false","waterlogged",Boolean.toString(water)),false,true,false,true,false,water,false);}
    private static State vine(String face){return State.of("minecraft:vine").property("east",Boolean.toString(face.equals("east"))).property("north",Boolean.toString(face.equals("north"))).property("south",Boolean.toString(face.equals("south"))).property("up","false").property("west",Boolean.toString(face.equals("west")));}
    private static List<Pos> ordered(Set<Pos> set){List<Pos> r=new ArrayList<>(set);r.sort(Comparator.comparingInt(Pos::y).thenComparingInt(Pos::x).thenComparingInt(Pos::z));return r;}
    private static List<Pos> decoratorOrder(Set<Pos> set,WorldAccess world){
        if(!world.supportsInternalPredicates())return ordered(set);
        List<Pos> r=new ArrayList<>(set);r.sort(Comparator.comparingInt(Pos::y));return r;
    }
    private static void shuffle(List<?> v,Mc263WorldgenRandomSource r,TraceSink t){for(int i=v.size();i>1;i--){int j=nextInt(r,i,460,t);java.util.Collections.swap(v,i-1,j);}}
    private static int nextInt(Mc263WorldgenRandomSource r,int b,int site,TraceSink t){int v=r.nextInt(b);emit(t,"rng_int",b,v,site);return v;}
    private static boolean nextFloat(Mc263WorldgenRandomSource r,float p,int site,TraceSink t){float v=r.nextFloat();emit(t,"rng_float",Float.floatToRawIntBits(v),Float.floatToRawIntBits(p),site);return v<p;}
    private static Result result(boolean placed,Counts c,TraceSink t,String key){emit(t,"result",id(key),placed?1:0,c.reads,c.writes,c.retained,c.bees);return new Result(placed,c.reads,c.writes,c.retained,c.bees,c.post);}
    public static byte[] encodeTrace(List<TraceEvent> events){try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeInt(TRACE_MAGIC);o.writeShort(TRACE_VERSION);byte[] s=TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);o.writeShort(s.length);o.write(s);o.writeInt(events.size());for(TraceEvent e:events){Phase p=PHASES.get(e.phase);if(p==null||e.values.length!=p.arity)throw new IllegalArgumentException(e.phase);o.writeByte(p.id);o.writeByte(p.arity);for(long v:e.values)o.writeLong(v);}return b.toByteArray();}catch(IOException x){throw new AssertionError(x);}}
    private static String normalize(String k){return k.startsWith("minecraft:")?k:"minecraft:"+k;}
    private static String key(String k){if(k==null||!RESOURCE_KEY.matcher(k).matches())throw new IllegalArgumentException(k);return k;}
    private static long id(String s){long h=0xcbf29ce484222325L;for(byte b:s.getBytes(StandardCharsets.UTF_8))h=(h^(b&255))*0x100000001b3L;return h;}
    private static void emit(TraceSink t,String p,long...v){if(t.enabled())t.record(p,v);}
    private enum Type{TRUNK_VINE,LEAVE_VINE,COCOA,BEEHIVE,GROUND}
    private record Decorator(Type type,float probability,int a,int b,int c){static Decorator trunk(){return new Decorator(Type.TRUNK_VINE,0,0,0,0);}static Decorator leaf(float p){return new Decorator(Type.LEAVE_VINE,p,0,0,0);}static Decorator cocoa(float p){return new Decorator(Type.COCOA,p,0,0,0);}static Decorator bee(float p){return new Decorator(Type.BEEHIVE,p,0,0,0);}static Decorator ground(int t,int r,int max){return new Decorator(Type.GROUND,0,t,r,max);}}
    private record Spec(String key,String log,String leaf,int baseHeight,int randA,int randB,int radius,int foliageOffset,int foliageHeight,boolean bush,int sizeLimit,int lowerSize,int upperSize,boolean ignoreVines,List<Decorator> decorators){}
    private record FallenSpec(String key,String log,int min,int max,boolean stumpVines){}
    private record Phase(int id,int arity){}
    private static final class Counts{int reads,writes,retained,bees,post;}
    private enum Direction{N(0,-1,"north","south","z"),E(1,0,"east","west","x"),S(0,1,"south","north","z"),W(-1,0,"west","east","x");static final Direction[] H=values();static final Direction[] VINE_ORDER={W,E,N,S};final int dx,dz;final String key,oppositeFace,axis;Direction(int x,int z,String k,String o,String a){dx=x;dz=z;key=k;oppositeFace=o;axis=a;}}
}
