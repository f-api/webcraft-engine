package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.Pos;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.Result;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.State;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.TraceEvent;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.TraceSink;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
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

/** Pinned 26.3-snapshot-7 complex-tree and tree-selector execution leaf. */
public final class Mc263ComplexTreeFeature {
    public static final int TRACE_MAGIC = 0x43545833; // CTX3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-complex-tree-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String FORKING_CLASS_SHA256 = "ae28358ecb72ab750ab8c9c7baea3fb9051dffa11134e1c084d1692aba4d7699";
    public static final String CHERRY_CLASS_SHA256 = "fa1c9ebc6c9691f316673ae3a0b70fd2665e3821ca129c2cdc9a24f28a037a57";
    public static final Map<String,String> SOURCE_CLASS_SHA256 = Map.ofEntries(
            Map.entry("fancy_trunk","9b16f46a1302376abe915f6ba58a75c2e960cd9d43ee479eb022e3c8fdc0224f"),
            Map.entry("dark_oak_trunk","72de2d7bd08277fc126ebdc85507e4494497360b36de78ecd2cd7e2b2162e041"),
            Map.entry("giant_trunk","85b6f4ab46fdce61950d3500439b4cd46c955bd1fca8a49ee44ed6cdf220b638"),
            Map.entry("mega_jungle_trunk","34b5ae22788339c92a87ee2e1f1ea0bb7f91dc51acb9b4f001185bed7c5e0a25"),
            Map.entry("acacia_foliage","b7320aca463d24817506b2b679395abcc6e43c729e4495a3c8d59169ef91c489"),
            Map.entry("fancy_foliage","9085a7963a9228369cca15a6c5d275171c0ed7250193366c605991bfaaf478ea"),
            Map.entry("dark_oak_foliage","29064b6b5ed3b72a42691b0bb502d82a43150580c047901b14201f2c80054bfb"),
            Map.entry("spruce_foliage","e4194137f706efd723d5dceb02cfeaa346fd5922666cc90f0ac75c5efb1f2ac4"),
            Map.entry("pine_foliage","1b7089c9f075288561b6ade3765999bb99877cb58b37b9673150279a10d4fe55"),
            Map.entry("mega_jungle_foliage","fea44c9354d9825b8557d58a9316fc6c67886771473cb562499cd360ca2a4344"),
            Map.entry("mega_pine_foliage","5cdd4c4619d9849d81133b07cf59d98f848ef07ae8523b1f3af12470f668745f"),
            Map.entry("cherry_foliage","0a37c8ba0e860a4bd1e7d6c7883f9c867fe41de9e401f9928db380bdb0a10f21"));

    private static final Map<String, Spec> TREES = trees();
    private static final Spec PLAIN_CHERRY = new Spec(
            "minecraft:cherry", "minecraft:cherry_log", "minecraft:cherry_leaves",
            7, 1, 0, Trunk.CHERRY, Foliage.CHERRY, 1, 0, 2, true,
            0, 0, false, false, false, 0, 0);
    private static final Map<String, Wrapper> WRAPPERS = wrappers();
    private static final Map<String, Selector> SELECTORS = selectors();
    private static final Map<String, String> SELECTED_SAPLINGS = selectedSaplings();
    private static final Map<Integer, Placement> PLACEMENTS = placements();
    private static final Map<String, Phase> PHASES = Map.ofEntries(
            Map.entry("preflight",new Phase(1,3)),Map.entry("rng_int",new Phase(2,3)),
            Map.entry("rng_float",new Phase(3,2)),Map.entry("rng_bool",new Phase(13,2)),Map.entry("height",new Phase(4,6)),
            Map.entry("heightmap",new Phase(5,6)),Map.entry("predicate",new Phase(6,6)),
            Map.entry("selector",new Phase(7,4)),Map.entry("read",new Phase(8,6)),
            Map.entry("write",new Phase(9,8)),Map.entry("foliage",new Phase(10,6)),
            Map.entry("finish",new Phase(11,6)),Map.entry("result",new Phase(12,6)),
            Map.entry("decorator",new Phase(14,6)),Map.entry("bee",new Phase(15,6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) { throw new AssertionError(); }
    };

    private Mc263ComplexTreeFeature() { }

    /** Extra placed-feature queries deliberately kept outside the frozen shared carrier. */
    public interface World extends Mc263CommonTreeFeature.WorldAccess {
        int oceanFloor(Pos column);
        int worldSurface(Pos column);
        boolean biomeAllows(String placedKey, Pos pos);
        boolean supportsBeneathTreePodzolTag();
        boolean beneathTreePodzolReplaceable(State state);
    }

    /** Adapter for T2, poplar, mushroom and mangrove leaves. Preflight must not query or use RNG. */
    public interface LeafExecutor {
        boolean supports(String configuredKey);
        void preflight(String configuredKey, World world);

        /** Existing Xoroshiro callback surface retained for source compatibility. */
        default Result place(String configuredKey, WorldgenRandom random, Pos origin, World world,
                TraceSink trace) {
            throw new UnsupportedOperationException(
                    "Xoroshiro leaf execution not implemented: " + configuredKey);
        }

        /** Caller-RNG surface. The Xoroshiro fallback passes the same live object without reseeding. */
        default Result place(String configuredKey, Mc263WorldgenRandomSource random, Pos origin,
                World world, TraceSink trace) {
            if (random instanceof WorldgenRandom xoroshiro) {
                return place(configuredKey, xoroshiro, origin, world, trace);
            }
            throw new UnsupportedOperationException(
                    "caller-RNG leaf execution not implemented: " + configuredKey);
        }
    }

    public static List<String> treeKeys() { return TREES.keySet().stream().sorted().toList(); }
    public static List<String> selectorKeys() { return SELECTORS.keySet().stream().sorted().toList(); }
    public static Set<Integer> placedIndices() { return Set.copyOf(PLACEMENTS.keySet()); }
    public static String placedKey(int index) { return requirePlacement(index).key; }
    public static String configuredKey(int index) { return requirePlacement(index).selector; }
    /** Validates the complete placed-index closure without RNG or live world access. */
    public static void preflightIndex(int index, World world, LeafExecutor leaves) {
        preflightPlaced(requirePlacement(index), world, leaves);
    }
    /** Validates one configured selector/wrapper/tree closure without RNG or live world access. */
    public static void preflightConfigured(String key, World world, LeafExecutor leaves) {
        String normalized = normalize(key);
        preflightConfigured(normalized, world, leaves, new LinkedHashSet<>());
    }
    public static String placedJsonSha256(int index) { return switch(index) {
        case 1 -> "d7ec2e2849b27b93e0341418333de5f13533ed967f390a34ccb65bcbb3d74a23";
        case 3 -> "72d72abdc6284eaa2abc7f3be6594ecc130be996ffefa7e8269456694f610f88";
        case 4 -> "fd3dc970e5a76a6d8f26166e37c5cf2b3be7921f65183aeeb4421ef6dcf4864e";
        case 5 -> "893a076d61f867eeee570bfd828d869ab355b5eac52f1a88bcec7e14ce96fea4";
        case 8 -> "485dde5886259ee6775d60cddd2c9b379cb171942a7b1dbc63c622084b92590a";
        case 10 -> "3ad37e2f7467d160dc3e23314a8e2fa5a604489b6a57d5a2bd9629b6e9cc158c";
        case 12 -> "8911f08fa5f8a9e54e9352e30f02e4dceedd1db55d8f912238b094718c392c89";
        case 16 -> "cd6863c7c8aed0948c2b9680d29abaaca6e2464015d249d03e9f5fd924974333";
        case 20 -> "33ca8e591d163b6d4228efddcf2b8d83e2e89d55023a2babc6da29f6a9029331";
        case 22 -> "6c1fba6304ebdf124bcb44d065a24bc27028e096e00b1b7f2d438b9cca68b07f";
        case 26 -> "5b9a9f2e9f1983ae2056a66d5c1c93b98601fc7603883bb2015e30ac13a5a8ba";
        case 27 -> "f0e39ad05012fd508b8432259b966c20b0540b6c30aa4977bc28ac0638d19fed";
        case 28 -> "a3bddb672c8db97503005f0549cfc45120dab01df5ed1f25cfcfffa74adaaf22";
        case 40 -> "494e07b75025c472da38badcc854e2adc15a4a5c66d8aca037dc23e964046d4e";
        case 42 -> "f356ff2db3733eaa22f07c44d37e86dc7c538216b5bed11fd558f71a3c2ce1c1";
        case 44 -> "659adda72509858aede91cab105fda3da8818458faac0a630bc3286374326256";
        case 45 -> "c7383b1358db2f08803982de92a9da8004412e19240058eaac25f4762099b45e";
        case 46 -> "8033710791353274c08a9fff62f1facc21fc36803fab2a9acd7e36b476db8d5f";
        case 47 -> "57a4695f838e765a8576b95b797da5a4c6b6595a2c320b2542c1e9675f2ca473";
        case 48 -> "c7e93ad98a0b56c3d7bcf33ae561e31add19ac0577fb5a066041d059740974e0";
        case 49 -> "80b46fca52100a07cfead3477dac3e0629d714b66acf6c486e5fdd4ffa36b7ca";
        case 53 -> "7d10ad7376d76630a5b70997d130b3167f566eb8e90fd4b33bf2abfc4c155c06";
        case 55 -> "ddc5c24dcc8d7a48a9e077885e1aa0b0c2f0964988224af6267255740c38e60f";
        case 59 -> "da7c834929f50dd831bfa99706eacd29b5189f53e7f13cad770295bbc11200e4";
        default -> throw new IllegalArgumentException("unsupported index " + index);
    }; }

    public static Result placeConfigured(String key, Mc263WorldgenRandomSource random, Pos origin, World world,
            LeafExecutor leaves, TraceSink trace) {
        String normalized = normalize(key);
        preflightConfigured(normalized, world, leaves, new LinkedHashSet<>());
        emit(trace, "preflight", id(normalized), 0, 1);
        return placeConfiguredLive(normalized, random, origin, world, leaves, trace);
    }

    public static Result placeIndex(int index, Mc263WorldgenRandomSource random, Pos chunkOrigin, World world,
            LeafExecutor leaves, TraceSink trace) {
        Placement placement = requirePlacement(index);
        preflightPlaced(placement, world, leaves);
        emit(trace, "preflight", index, id(placement.selector), 2);
        int count;
        if (placement.rarity > 1) {
            if (nextFloat(random, 11, trace) >= 1.0f / placement.rarity)
                return result(false, new Counts(), trace, placement.key);
            count = 1;
        } else {
            count = placement.baseCount;
            if (placement.extraDenominator > 0
                    && nextInt(random, placement.extraDenominator, 10, trace) == 0) count++;
        }
        Counts total = new Counts(); boolean placed = false;
        for (int i = 0; i < count; i++) {
            int x = chunkOrigin.x() + nextInt(random, 16, 12, trace);
            int z = chunkOrigin.z() + nextInt(random, 16, 13, trace);
            Pos column = new Pos(x, chunkOrigin.y(), z);
            int ocean = world.oceanFloor(column); total.reads++;
            int surface = world.worldSurface(column); total.reads++;
            emit(trace, "heightmap", x, z, ocean, surface, placement.maxWaterDepth, index);
            if (surface - ocean > placement.maxWaterDepth) continue;
            int placedY = world.oceanFloor(column); total.reads++;
            emit(trace, "heightmap", x, z, placedY, placedY, -1, index);
            Pos at = new Pos(x, placedY, z);
            if (placement.survivalBeforeBiome && !survives(placement, at, world, trace)) continue;
            boolean biome = world.biomeAllows(placement.key, at); total.reads++;
            emit(trace, "predicate", id(placement.key), x, ocean, z, biome ? 1 : 0, 20);
            if (!biome) continue;
            if (!placement.survivalBeforeBiome && placement.sapling != null
                    && !survives(placement, at, world, trace)) continue;
            Result r = placeConfiguredLive(placement.selector, random, at, world, leaves, trace);
            total.add(r); placed |= r.placed();
        }
        return result(placed, total, trace, placement.key);
    }

    private static boolean survives(Placement p, Pos at, World w, TraceSink t) {
        State sapling = sapling(p.sapling);
        boolean ok = w.canSaplingSurvive(sapling, at);
        emit(t, "predicate", id(p.sapling), at.x(), at.y(), at.z(), ok ? 1 : 0, 21);
        return ok;
    }
    private static Placement requirePlacement(int index){Placement p=PLACEMENTS.get(index);if(p==null)throw new IllegalArgumentException("unsupported index "+index);return p;}
    private static Spec treeSpec(String key) {
        return PLAIN_CHERRY.key.equals(key) ? PLAIN_CHERRY : TREES.get(key);
    }

    private static Result placeConfiguredLive(String key, Mc263WorldgenRandomSource random, Pos origin,
            World world, LeafExecutor leaves, TraceSink trace) {
        Wrapper wrapper=WRAPPERS.get(key);
        if(wrapper!=null){Pos at=origin;if(wrapper.snow){int steps=0;while(steps<8){State state=read(world,at,trace,new Counts());if(!state.block().equals("minecraft:powder_snow"))break;at=at.offset(0,1,0);steps++;if(at.y()<world.minY()||at.y()>world.maxY())return new Result(false,steps,0,0,0,0);}State below=read(world,at.offset(0,-1,0),trace,new Counts());if(!below.block().equals("minecraft:snow_block")&&!below.block().equals("minecraft:powder_snow"))return new Result(false,steps+1,0,0,0,0);}if(wrapper.sapling!=null){State sapling=sapling(wrapper.sapling);boolean ok=world.canSaplingSurvive(sapling,at);emit(trace,"predicate",id(wrapper.key),at.x(),at.y(),at.z(),ok?1:0,22);if(!ok)return new Result(false,0,0,0,0,0);}Spec owned=treeSpec(wrapper.target);return owned!=null?placeTree(owned,random,at,world,leaves,trace):leaves.place(wrapper.target,random,at,world,trace);}
        Spec spec = treeSpec(key);
        if (spec != null) return placeTree(spec, random, origin, world, leaves, trace);
        Selector selector = SELECTORS.get(key);
        if (selector != null) {
            String selected;
            if (selector.weighted) {
                int value = nextInt(random, selector.totalWeight, 30, trace);
                selected = selector.fallback;
                for (Choice c : selector.choices) { if (value < c.weight) { selected = c.key; break; } value -= c.weight; }
            } else {
                selected = selector.fallback;
                for (Choice c : selector.choices) {
                    float value = nextFloat(random, 31, trace);
                    if (value < c.chance) { selected = c.key; break; }
                }
            }
            emit(trace, "selector", id(key), id(selected), selector.weighted ? 1 : 0,
                    selector.choices.size());
            String selectedSapling = SELECTED_SAPLINGS.get(selected);
            if (selectedSapling != null) {
                boolean ok = world.canSaplingSurvive(sapling(selectedSapling), origin);
                emit(trace, "predicate", id(selected), origin.x(), origin.y(), origin.z(), ok ? 1 : 0, 22);
                if (!ok) return new Result(false, 0, 0, 0, 0, 0);
            }
            return placeConfiguredLive(selected, random, origin, world, leaves, trace);
        }
        return leaves.place(key, random, origin, world, trace);
    }

    private static Result placeTree(Spec s, Mc263WorldgenRandomSource r, Pos o, World w, LeafExecutor leaf,
            TraceSink t) {
        Counts c = new Counts();
        int h = s.base + nextInt(r, s.randA + 1, 100, t) + nextInt(r, s.randB + 1, 101, t);
        FoliageParams fp = switch (s.foliage) {
            case SPRUCE -> new FoliageParams(Math.max(4,h-uniform(r,1,2,102,t)),
                    uniform(r,2,3,103,t));
            case PINE -> { int fh=uniform(r,3,4,104,t); yield new FoliageParams(fh,
                    1+nextInt(r,Math.max(h-fh+1,1),105,t)); }
            case MEGA_PINE -> new FoliageParams(uniform(r,s.crownMin,s.crownMax,106,t),0);
            case ACACIA -> new FoliageParams(0,2);
            case FANCY,DARK -> new FoliageParams(4,s.foliage==Foliage.FANCY?2:0);
            case MEGA_JUNGLE -> new FoliageParams(2,2);
            case CHERRY -> new FoliageParams(5,4);
        };
        emit(t, "height", id(s.key), h, s.trunk.ordinal(), s.foliage.ordinal(), o.y(), 0);
        if (o.y() < w.minY() + 1 || o.y() + h + 1 > w.maxY() + 1)
            return result(false, c, t, s.key);
        int clipped = maxFreeHeight(s, h, o, w, t, c);
        if (clipped < h && (s.unusedA == 0 || clipped < s.unusedA))
            return result(false, c, t, s.key);
        Set<Pos> logs = new HashSet<>(), foliage = new HashSet<>(), deco = new HashSet<>();
        List<Attachment> attachments = trunks(s, clipped, o, r, w, t, c, logs);
        for (Attachment a : attachments) foliage(s, a, clipped, fp, r, w, t, c, foliage);
        if (logs.isEmpty() && foliage.isEmpty()) return result(false, c, t, s.key);
        decorate(s, logs, foliage, r, w, leaf, t, c, deco);
        // Preserve the source HashSet walk; Set.copyOf applies a per-JVM encounter-order salt.
        w.finishTree(Collections.unmodifiableSet(new LinkedHashSet<>(logs)),
                Collections.unmodifiableSet(new LinkedHashSet<>(foliage)), Set.of(),
                Collections.unmodifiableSet(new LinkedHashSet<>(deco)));
        emit(t, "finish", logs.size(), foliage.size(), deco.size(), c.reads, c.writes, 0);
        return result(true, c, t, s.key);
    }

    private static int maxFreeHeight(Spec s, int h, Pos o, World w, TraceSink t, Counts c) {
        for (int y = 0; y <= h + 1; y++) {
            int radius = y < s.limit ? s.lower : s.upper;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                Pos pos=o.offset(x,y,z);State state=read(w,pos,t,c);
                boolean free=replaceable(state)||(state=read(w,pos,t,c)).log();
                if (!free || (!s.ignoreVines && read(w,pos,t,c).block().equals("minecraft:vine")))
                    return y - 2;
            }
        }
        return h;
    }

    private static List<Attachment> trunks(Spec s, int h, Pos o, Mc263WorldgenRandomSource r, World w,
            TraceSink t, Counts c, Set<Pos> logs) {
        below(s, o, r, w, t, c, logs, s.trunk == Trunk.DARK || s.trunk == Trunk.GIANT || s.trunk == Trunk.MEGA_JUNGLE);
        return switch (s.trunk) {
            case FORKING -> forking(s, h, o, r, w, t, c, logs);
            case FANCY -> fancy(s, h, o, r, w, t, c, logs);
            case DARK -> dark(s, h, o, r, w, t, c, logs);
            case GIANT, MEGA_JUNGLE -> giant(s, h, o, r, w, t, c, logs);
            case CHERRY -> cherry(s, h, o, r, w, t, c, logs);
            case STRAIGHT -> { for (int y=0;y<h;y++) log(s,o.offset(0,y,0),"y",w,t,c,logs); yield List.of(new Attachment(o.offset(0,h,0),0,false)); }
        };
    }

    private static void below(Spec s, Pos o, Mc263WorldgenRandomSource r, World w, TraceSink t, Counts c,
            Set<Pos> logs, boolean giant) {
        int width = giant ? 2 : 1;
        for (int x=0;x<width;x++) for(int z=0;z<width;z++) {
            Pos p=o.offset(x,-1,z); State state=read(w,p,t,c);
            if (!w.cannotReplaceBelowTreeTrunk(state)) write(w,p,State.of("minecraft:dirt"),t,c,logs);
        }
    }

    private static List<Attachment> forking(Spec s,int h,Pos o,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> logs){
        Dir lean=Dir.H[nextInt(r,4,110,t)];int leanHeight=h-nextInt(r,4,111,t)-1,steps=3-nextInt(r,3,112,t),x=o.x(),z=o.z(),ey=Integer.MIN_VALUE;
        for(int y=0;y<h;y++){if(y>=leanHeight&&steps>0){x+=lean.dx;z+=lean.dz;steps--;}if(log(s,new Pos(x,o.y()+y,z),"y",w,t,c,logs))ey=o.y()+y+1;}
        List<Attachment>a=new ArrayList<>();if(ey!=Integer.MIN_VALUE)a.add(new Attachment(new Pos(x,ey,z),1,false));
        x=o.x();z=o.z();Dir branch=Dir.H[nextInt(r,4,113,t)];if(branch!=lean){int pos=leanHeight-nextInt(r,2,114,t)-1,n=1+nextInt(r,3,115,t);ey=Integer.MIN_VALUE;for(int y=pos;y<h&&n>0;y++,n--){if(y<1)continue;x+=branch.dx;z+=branch.dz;if(log(s,new Pos(x,o.y()+y,z),"y",w,t,c,logs))ey=o.y()+y+1;}if(ey!=Integer.MIN_VALUE)a.add(new Attachment(new Pos(x,ey,z),0,false));}return a;
    }

    private static List<Attachment> fancy(Spec s,int treeHeight,Pos o,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> logs){
        int height=treeHeight+2,trunkHeight=(int)Math.floor(height*.618),top=o.y()+trunkHeight;
        List<FancyAttachment> points=new ArrayList<>();points.add(new FancyAttachment(o.offset(0,height-5,0),top));
        for(int y=height-5;y>=0;y--){float shape=treeShape(height,y);if(shape<0)continue;double radius=shape*(nextFloat(r,120,t)+.328);double angle=nextFloat(r,121,t)*2*Math.PI;Pos start=o.offset((int)Math.floor(radius*Math.sin(angle)+.5),y-1,(int)Math.floor(radius*Math.cos(angle)+.5));if(!limbFree(start,start.offset(0,5,0),w,t,c))continue;int dx=o.x()-start.x(),dz=o.z()-start.z();int branchTop=(int)(start.y()-Math.sqrt(dx*dx+dz*dz)*.381);branchTop=Math.min(branchTop,top);Pos base=new Pos(o.x(),branchTop,o.z());if(limbFree(base,start,w,t,c))points.add(new FancyAttachment(start,branchTop));}
        limb(s,o,o.offset(0,trunkHeight,0),w,t,c,logs);List<Attachment> out=new ArrayList<>();for(FancyAttachment p:points)if(p.branchBase-o.y()>=height*.2){if(p.branchBase!=p.pos.y())limb(s,new Pos(o.x(),p.branchBase,o.z()),p.pos,w,t,c,logs);out.add(new Attachment(p.pos,0,false));}return out;
    }

    private static float treeShape(int h,int y){if(y<h*.3f)return-1;float r=h/2f,a=r-y;if(Math.abs(a)>=r)return 0;return(float)(Math.sqrt(r*r-a*a)*.5);}
    private static boolean limbFree(Pos a,Pos b,World w,TraceSink t,Counts c){int n=Math.max(Math.abs(b.x()-a.x()),Math.max(Math.abs(b.y()-a.y()),Math.abs(b.z()-a.z())));for(int i=0;i<=n;i++){Pos p=new Pos(a.x()+Math.round((b.x()-a.x())*(float)i/n),a.y()+Math.round((b.y()-a.y())*(float)i/n),a.z()+Math.round((b.z()-a.z())*(float)i/n));State state=read(w,p,t,c);if(!replaceable(state)&&!read(w,p,t,c).log())return false;}return true;}
    private static void limb(Spec s,Pos a,Pos b,World w,TraceSink t,Counts c,Set<Pos> logs){int n=Math.max(Math.abs(b.x()-a.x()),Math.max(Math.abs(b.y()-a.y()),Math.abs(b.z()-a.z())));for(int i=0;i<=n;i++){Pos p=new Pos(a.x()+Math.round((b.x()-a.x())*(float)i/n),a.y()+Math.round((b.y()-a.y())*(float)i/n),a.z()+Math.round((b.z()-a.z())*(float)i/n));String axis=Math.abs(p.x()-a.x())>=Math.abs(p.z()-a.z())?"x":"z";if(p.x()==a.x()&&p.z()==a.z())axis="y";log(s,p,axis,w,t,c,logs);}}

    private static List<Attachment> dark(Spec s,int h,Pos o,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> logs){Dir d=Dir.H[nextInt(r,4,130,t)];int lean=h-nextInt(r,4,131,t),steps=2-nextInt(r,3,132,t),x=o.x(),z=o.z(),ey=o.y()+h-1;for(int y=0;y<h;y++){if(y>=lean&&steps>0){x+=d.dx;z+=d.dz;steps--;}for(int ox=0;ox<2;ox++)for(int oz=0;oz<2;oz++)log(s,new Pos(x+ox,o.y()+y,z+oz),"y",w,t,c,logs);}List<Attachment>a=new ArrayList<>();a.add(new Attachment(new Pos(x,ey,z),0,true));for(int ox=-1;ox<=2;ox++)for(int oz=-1;oz<=2;oz++){if((ox>=0&&ox<=1&&oz>=0&&oz<=1)||nextInt(r,3,133,t)>0)continue;int n=nextInt(r,3,134,t)+2;for(int y=0;y<n;y++)log(s,new Pos(o.x()+ox,ey-y-1,o.z()+oz),"y",w,t,c,logs);a.add(new Attachment(new Pos(o.x()+ox,ey,o.z()+oz),0,false));}return a;}

    private static List<Attachment> giant(Spec s,int h,Pos o,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> logs){for(int y=0;y<h;y++){logIfFree(s,o.offset(0,y,0),"y",w,t,c,logs);if(y<h-1){logIfFree(s,o.offset(1,y,0),"y",w,t,c,logs);logIfFree(s,o.offset(1,y,1),"y",w,t,c,logs);logIfFree(s,o.offset(0,y,1),"y",w,t,c,logs);}}List<Attachment>a=new ArrayList<>();a.add(new Attachment(o.offset(0,h,0),0,true));if(s.trunk==Trunk.MEGA_JUNGLE)for(int bh=h-2-nextInt(r,4,140,t);bh>h/2;bh-=2+nextInt(r,4,142,t)){float angle=nextFloat(r,141,t)*(float)Math.PI*2;int bx=0,bz=0;for(int b=0;b<5;b++){bx=(int)(1.5f+Math.cos(angle)*b);bz=(int)(1.5f+Math.sin(angle)*b);log(s,o.offset(bx,bh-3+b/2,bz),Math.abs(bx)>=Math.abs(bz)?"x":"z",w,t,c,logs);}a.add(new Attachment(o.offset(bx,bh,bz),-2,false));}return a;}

    private static List<Attachment> cherry(Spec s,int h,Pos o,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> logs){int first=Math.max(0,h-1+uniform(r,-4,-3,150,t)),second=Math.max(0,h-1+uniform(r,-4,-4,151,t));if(second>=first)second++;int count=1+nextInt(r,3,152,t);boolean middle=count==3,both=count>=2;int trunk=middle?h:(both?Math.max(first,second)+1:first+1);for(int y=0;y<trunk;y++)log(s,o.offset(0,y,0),"y",w,t,c,logs);List<Attachment>a=new ArrayList<>();if(middle)a.add(new Attachment(o.offset(0,trunk,0),0,false));Dir d=Dir.H[nextInt(r,4,153,t)];a.add(cherryBranch(s,h,o,d,first,first<trunk-1,r,w,t,c,logs));if(both)a.add(cherryBranch(s,h,o,d.opposite(),second,second<trunk-1,r,w,t,c,logs));return a;}
    private static Attachment cherryBranch(Spec s,int h,Pos o,Dir d,int start,boolean continues,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> logs){Pos p=o.offset(0,start,0);int endY=h-1+uniform(r,-1,0,154,t),distance=uniform(r,2,4,155,t)+(continues||endY<start?1:0);Pos end=o.offset(d.dx*distance,endY,d.dz*distance);int horizontal=continues||endY<start?2:1;for(int i=0;i<horizontal;i++){p=p.offset(d.dx,0,d.dz);log(s,p,d.axis,w,t,c,logs);}Dir vertical=end.y()>p.y()?Dir.UP:Dir.DOWN;while(manhattan(p,end)!=0){float chance=Math.abs(end.y()-p.y())/(float)manhattan(p,end);if(nextFloat(r,156,t)<chance)p=p.offset(0,vertical.dy,0);else p=p.offset(d.dx,0,d.dz);log(s,p,p.x()==end.x()&&p.z()==end.z()?"y":d.axis,w,t,c,logs);}return new Attachment(end.offset(0,1,0),0,false);}

    private static void foliage(Spec s,Attachment a,int h,FoliageParams fp,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){
        switch(s.foliage){
            case ACACIA->{row(s,a,a.pos,-1,2+a.radiusOffset,r,w,t,c,out);row(s,a,a.pos,0,1,r,w,t,c,out);row(s,a,a.pos,0,1+a.radiusOffset,r,w,t,c,out);}
            case FANCY->{for(int y=4;y>=0;y--)row(s,a,a.pos,y,y==4||y==0?2:3,r,w,t,c,out);}
            case DARK->{row(s,a,a.pos,-1,a.doubleTrunk?2:2,r,w,t,c,out);row(s,a,a.pos,0,a.doubleTrunk?3:1,r,w,t,c,out);if(a.doubleTrunk){row(s,a,a.pos,1,2,r,w,t,c,out);if(nextBoolean(r,170,t))row(s,a,a.pos,2,0,r,w,t,c,out);}}
            case SPRUCE->{int radius=fp.radius,offset=uniform(r,0,2,172,t),fh=fp.height,cur=nextInt(r,2,174,t),max=1,min=0;for(int y=offset;y>=-fh;y--){row(s,a,a.pos,y,cur,r,w,t,c,out);if(cur>=max){cur=min;min=1;max=Math.min(max+1,radius+a.radiusOffset);}else cur++;}}
            case PINE->{int fh=fp.height,radius=fp.radius,cur=0;for(int y=1;y>=1-fh;y--){row(s,a,a.pos,y,cur,r,w,t,c,out);if(cur>=1&&y==2-fh)cur--;else if(cur<radius+a.radiusOffset)cur++;}}
            case MEGA_JUNGLE->{int leafH=(a.doubleTrunk?2:1+nextInt(r,2,177,t))+a.heightOffset;for(int y=0;y>=-leafH;y--)row(s,a,a.pos,y,3+a.radiusOffset-y,r,w,t,c,out);}
            case MEGA_PINE->{int fh=fp.height,prev=0;for(int y=-fh;y<=0;y++){int smooth=(int)Math.floor((-y)/(float)fh*3.5f),radius=(-y>0&&smooth==prev&&((a.pos.y()+y)&1)==0)?smooth+1:smooth;row(s,a,a.pos,y,radius,r,w,t,c,out);prev=smooth;}}
            case CHERRY->{int radius=3,fh=5;row(s,a,a.pos,fh-3,radius-2,r,w,t,c,out);row(s,a,a.pos,fh-4,radius-1,r,w,t,c,out);for(int y=0;y>=0;y--)row(s,a,a.pos,y,radius,r,w,t,c,out);hangingRow(s,a,a.pos,-1,radius,r,w,t,c,out);hangingRow(s,a,a.pos,-2,radius-1,r,w,t,c,out);}
        }
    }

    private static void row(Spec s,Attachment a,Pos center,int y,int radius,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){for(int x=-radius;x<=radius+(a.doubleTrunk?1:0);x++)for(int z=-radius;z<=radius+(a.doubleTrunk?1:0);z++){int ax=Math.min(Math.abs(x),Math.abs(x-1)),az=Math.min(Math.abs(z),Math.abs(z-1));boolean skip=s.foliage.skip(ax,y,az,radius,a.doubleTrunk,r,t);emit(t,"foliage",center.x()+x,center.y()+y,center.z()+z,radius,y,skip?1:0);if(!skip)leaf(s,center.offset(x,y,z),w,t,c,out);}}
    private static void hangingRow(Spec s,Attachment a,Pos center,int y,int radius,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){row(s,a,center,y,radius,r,w,t,c,out);Pos log=center.offset(0,-1,0);for(Dir along:Dir.H){Dir edge=along.clockwise();int edgeDistance=(edge==Dir.E||edge==Dir.S)?radius+(a.doubleTrunk?1:0):radius;Pos p=center.offset(edge.dx*edgeDistance,y-1,edge.dz*edgeDistance).offset(-along.dx*radius,0,-along.dz*radius);for(int i=-radius;i<radius+(a.doubleTrunk?1:0);i++){if(out.contains(p.offset(0,1,0))&&manhattan(log,p)<7&&nextFloat(r,181,t)<=.16666667f){int before=c.writes;leaf(s,p,w,t,c,out);if(c.writes>before){Pos down=p.offset(0,-1,0);if(manhattan(log,down)<7&&nextFloat(r,182,t)<=.33333334f)leaf(s,down,w,t,c,out);}}p=p.offset(along.dx,0,along.dz);}}}

    private static void decorate(Spec s,Set<Pos> logs,Set<Pos> leaves,Mc263WorldgenRandomSource r,World w,LeafExecutor delegate,TraceSink t,Counts c,Set<Pos> out){List<Pos> ol=ordered(logs),of=ordered(leaves);if(s.megaVines){for(Pos p:ol)for(Dir d:Dir.VINE)if(nextInt(r,3,190,t)>0){Pos q=p.offset(d.dx,0,d.dz);if(read(w,q,t,c).air())write(w,q,vine(d.opposite().key),t,c,out);}for(Pos p:of)for(Dir d:Dir.VINE)if(nextFloat(r,191,t)<.25f){Pos q=p.offset(d.dx,0,d.dz);for(int i=0;i<5&&read(w,q,t,c).air();i++,q=q.offset(0,-1,0))write(w,q,vine(d.opposite().key),t,c,out);}}
        float bee=beeProbability(s.key);if(bee>=0){emit(t,"decorator",1,ol.size(),of.size(),Float.floatToRawIntBits(bee),0,0);beehive(ol,of,bee,r,w,t,c,out);}
        if(hasLitter(s.key)){emit(t,"decorator",2,ol.size(),of.size(),0,96,4);groundLitter(ol,96,4,3,r,w,t,c,out);emit(t,"decorator",2,ol.size(),of.size(),0,150,2);groundLitter(ol,150,2,4,r,w,t,c,out);}
        if(s.alterGround)for(Pos p:ol)if(p.y()==ol.getFirst().y())alterGround(p,r,w,t,c,out);
        if(s.pale){List<Pos> shuffled=new ArrayList<>(ol);shuffle(shuffled,r,t);Pos origin=shuffled.stream().min(Comparator.comparingInt(Pos::y)).orElseThrow();if(nextFloat(r,200,t)<.15f){Result nested=delegate.place("minecraft:pale_moss_patch",r,origin.offset(0,1,0),w,t);c.add(nested);}for(Pos p:ol)if(nextFloat(r,201,t)<.4f&&read(w,p.offset(0,-1,0),t,c).air())moss(p.offset(0,-1,0),r,w,t,c,out);for(Pos p:of)if(nextFloat(r,202,t)<.8f&&read(w,p.offset(0,-1,0),t,c).air())moss(p.offset(0,-1,0),r,w,t,c,out);}
        if(s.creaking&&nextFloat(r,210,t)<1f){List<Pos> shuffled=new ArrayList<>(ol);shuffle(shuffled,r,t);for(Pos p:shuffled){boolean all=true;for(Dir d:Dir.ALL)if(!read(w,p.offset(d.dx,d.dy,d.dz),t,c).log()){all=false;break;}if(all){write(w,p,State.of("minecraft:creaking_heart").property("axis","y").property("creaking_heart_state","dormant").property("natural","true"),t,c,out);break;}}}
    }
    private static float beeProbability(String key){if(key.endsWith("bees_0002_leaf_litter"))return .002f;if(key.endsWith("bees_002"))return .02f;if(key.endsWith("bees_005"))return .05f;if(key.endsWith("bees"))return 1f;return -1f;}
    private static boolean hasLitter(String key){return key.endsWith("leaf_litter");}
    private static void beehive(List<Pos> logs,List<Pos> leaves,float chance,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){if(logs.isEmpty()||nextFloat(r,250,t)>=chance)return;int y=!leaves.isEmpty()?Math.max(leaves.getFirst().y()-1,logs.getFirst().y()+1):Math.min(logs.getFirst().y()+1+nextInt(r,3,251,t),logs.getLast().y());List<Pos> choices=new ArrayList<>();for(Pos p:logs)if(p.y()==y)for(Dir d:List.of(Dir.E,Dir.S,Dir.W))choices.add(p.offset(d.dx,0,d.dz));shuffle(choices,r,t);for(Pos p:choices)if(read(w,p,t,c).air()&&read(w,p.offset(0,0,1),t,c).air()){write(w,p,State.of("minecraft:bee_nest").property("facing","south").property("honey_level","0"),t,c,out);int n=2+nextInt(r,2,252,t);for(int i=0;i<n;i++){int ticks=nextInt(r,599,253,t);w.storeBee(p,ticks);c.bees++;emit(t,"bee",p.x(),p.y(),p.z(),ticks,i,n);}break;}}
    private static void groundLitter(List<Pos> logs,int tries,int radius,int max,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){if(logs.isEmpty())return;int minY=logs.getFirst().y(),minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;for(Pos p:logs)if(p.y()==minY){minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());}for(int i=0;i<tries;i++){Pos p=new Pos(minX-radius+nextInt(r,maxX-minX+2*radius+1,260,t),minY-2+nextInt(r,5,261,t),minZ-radius+nextInt(r,maxZ-minZ+2*radius+1,262,t)),above=p.offset(0,1,0);State a=read(w,above,t,c),base=read(w,p,t,c);if((a.air()||a.block().equals("minecraft:vine"))&&base.solidRender()&&w.motionBlockingNoLeaves(p)<=above.y()){int pick=nextInt(r,max*4,263,t),amount=1+pick/4;Dir face=Dir.H[pick%4];write(w,above,State.of("minecraft:leaf_litter").property("facing",face.key).property("segment_amount",Integer.toString(amount)),t,c,out);}}}
    private static void alterGround(Pos p,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){for(Pos q:List.of(p.offset(-1,0,-1),p.offset(2,0,-1),p.offset(-1,0,2),p.offset(2,0,2))){circle(q,w,t,c,out);}for(int i=0;i<5;i++){int n=nextInt(r,64,220,t),x=n%8,z=n/8;if(x==0||x==7||z==0||z==7)circle(p.offset(-3+x,0,-3+z),w,t,c,out);}}
    private static void circle(Pos p,World w,TraceSink t,Counts c,Set<Pos> out){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){if(Math.abs(x)==2&&Math.abs(z)==2)continue;Pos q=p.offset(x,0,z);for(int dy=2;dy>=-3;dy--){Pos at=q.offset(0,dy,0);State live=read(w,at,t,c);if(w.beneathTreePodzolReplaceable(live)){write(w,at,State.of("minecraft:podzol").property("snowy","false"),t,c,out);break;}if(!live.air()&&dy<0)break;}}}
    private static void moss(Pos p,Mc263WorldgenRandomSource r,World w,TraceSink t,Counts c,Set<Pos> out){while(read(w,p.offset(0,-1,0),t,c).air()&&nextFloat(r,230,t)>=.5f){write(w,p,State.of("minecraft:pale_hanging_moss").property("tip","false"),t,c,out);p=p.offset(0,-1,0);}write(w,p,State.of("minecraft:pale_hanging_moss").property("tip","true"),t,c,out);}

    private static void preflightPlaced(Placement p,World w,LeafExecutor leaves){if(!w.supportsFeature(p.key))throw new UnsupportedOperationException(p.key);preflightConfigured(p.selector,w,leaves,new LinkedHashSet<>());if(p.sapling!=null&& !w.supportsState(sapling(p.sapling)))throw new UnsupportedOperationException(sapling(p.sapling).canonical());}
    private static void preflightConfigured(String key,World w,LeafExecutor leaves,Set<String> seen){if(!seen.add(key))return;Wrapper wrapper=WRAPPERS.get(key);if(wrapper!=null){preflightWrapper(wrapper,w,leaves,seen);return;}Spec s=treeSpec(key);if(s!=null){preflightTree(s,w,leaves);return;}Selector selector=SELECTORS.get(key);if(selector!=null){if(!w.supportsFeature(key))throw new UnsupportedOperationException(key);for(Choice c:selector.choices)preflightSelected(c.key,w,leaves,seen);preflightSelected(selector.fallback,w,leaves,seen);return;}if(!leaves.supports(key))throw new UnsupportedOperationException(key);leaves.preflight(key,w);}
    private static void preflightSelected(String key,World w,LeafExecutor leaves,Set<String> seen){String s=SELECTED_SAPLINGS.get(key);if(s!=null&&!w.supportsState(sapling(s)))throw new UnsupportedOperationException(sapling(s).canonical());preflightConfigured(key,w,leaves,seen);}
    private static void preflightWrapper(Wrapper wrapper,World w,LeafExecutor leaves,Set<String> seen){if(!w.supportsFeature(wrapper.key))throw new UnsupportedOperationException(wrapper.key);if(wrapper.sapling!=null&&!w.supportsState(sapling(wrapper.sapling)))throw new UnsupportedOperationException(sapling(wrapper.sapling).canonical());if(wrapper.snow){for(String block:List.of("minecraft:powder_snow","minecraft:snow_block"))if(!w.supportsState(State.of(block)))throw new UnsupportedOperationException(block);}preflightConfigured(wrapper.target,w,leaves,seen);}
    private static void preflightTree(Spec s,World w,LeafExecutor leaves){if(!w.supportsFeature(s.key))throw new UnsupportedOperationException(s.key);if(!w.supportsTreeFinalization())throw new UnsupportedOperationException("tree finalization");List<State> states=new ArrayList<>(List.of(axis(s.log,"x"),axis(s.log,"y"),axis(s.log,"z"),State.of("minecraft:dirt")));for(int distance=1;distance<=7;distance++){states.add(leaves(s.leaf,distance,false));states.add(leaves(s.leaf,distance,true));}if(s.megaVines)for(Dir d:Dir.H)states.add(vine(d.key));if(beeProbability(s.key)>=0){if(!w.supportsBeeNestPayload())throw new UnsupportedOperationException("beehive payload");states.add(State.of("minecraft:bee_nest").property("facing","south").property("honey_level","0"));}if(hasLitter(s.key))for(int amount=1;amount<=4;amount++)for(Dir d:Dir.H)states.add(State.of("minecraft:leaf_litter").property("facing",d.key).property("segment_amount",Integer.toString(amount)));if(s.alterGround){if(!w.supportsBeneathTreePodzolTag())throw new UnsupportedOperationException("minecraft:beneath_tree_podzol_replaceable");states.add(State.of("minecraft:podzol").property("snowy","false"));}if(s.pale){states.add(State.of("minecraft:pale_hanging_moss").property("tip","false"));states.add(State.of("minecraft:pale_hanging_moss").property("tip","true"));if(!leaves.supports("minecraft:pale_moss_patch"))throw new UnsupportedOperationException("minecraft:pale_moss_patch");leaves.preflight("minecraft:pale_moss_patch",w);}if(s.creaking)states.add(State.of("minecraft:creaking_heart").property("axis","y").property("creaking_heart_state","dormant").property("natural","true"));for(State state:states)if(!w.supportsState(state))throw new UnsupportedOperationException(state.canonical());}

    private static boolean log(Spec s,Pos p,String axis,World w,TraceSink t,Counts c,Set<Pos> out){State live=read(w,p,t,c);if(!replaceable(live))return false;write(w,p,axis(s.log,axis),t,c,out);return true;}
    private static boolean logIfFree(Spec s,Pos p,String axis,World w,TraceSink t,Counts c,Set<Pos> out){State live=read(w,p,t,c);if(!replaceable(live)&&!read(w,p,t,c).log())return false;return log(s,p,axis,w,t,c,out);}
    private static void leaf(Spec s,Pos p,World w,TraceSink t,Counts c,Set<Pos> out){State live=read(w,p,t,c);if(live.persistent()||!replaceable(live))return;write(w,p,leaves(s.leaf,7,live.waterSource()),t,c,out);}
    private static State read(World w,Pos p,TraceSink t,Counts c){State state=w.state(p);c.reads++;emit(t,"read",p.x(),p.y(),p.z(),id(state.canonical()),state.air()?1:0,state.replaceableByTrees()?1:0);return state;}
    private static void write(World w,Pos p,State state,TraceSink t,Counts c,Set<Pos> out){boolean kept=w.set(p,state,19);c.writes++;if(kept)c.retained++;out.add(p);emit(t,"write",p.x(),p.y(),p.z(),id(state.canonical()),19,kept?1:0,state.properties().hashCode(),id(state.block()));}
    private static boolean replaceable(State s){return s.air()||s.replaceableByTrees();}
    private static State axis(String block,String axis){return new State(block,Map.of("axis",axis),
            false,false,true,false,false,false,true);}
    private static State sapling(String block){return State.of(block).property("stage","0");}
    private static State leaves(String block,int distance,boolean water){return new State(block,Map.of("distance",Integer.toString(distance),"persistent","false","waterlogged",Boolean.toString(water)),false,true,false,true,false,water,false);}
    private static State vine(String face){return State.of("minecraft:vine").property("east",Boolean.toString(face.equals("east"))).property("north",Boolean.toString(face.equals("north"))).property("south",Boolean.toString(face.equals("south"))).property("up","false").property("west",Boolean.toString(face.equals("west")));}
    private static List<Pos> ordered(Set<Pos> set){List<Pos> out=new ArrayList<>(set);out.sort(Comparator.comparingInt(Pos::y).thenComparingInt(Pos::x).thenComparingInt(Pos::z));return out;}
    private static void shuffle(List<?> list,Mc263WorldgenRandomSource r,TraceSink t){for(int i=list.size();i>1;i--){int j=nextInt(r,i,240,t);java.util.Collections.swap(list,i-1,j);}}
    private static int uniform(Mc263WorldgenRandomSource r,int min,int max,int site,TraceSink t){return min+nextInt(r,max-min+1,site,t);}
    private static int nextInt(Mc263WorldgenRandomSource r,int bound,int site,TraceSink t){int value=r.nextInt(bound);emit(t,"rng_int",bound,value,site);return value;}
    private static float nextFloat(Mc263WorldgenRandomSource r,int site,TraceSink t){float value=r.nextFloat();emit(t,"rng_float",Float.floatToRawIntBits(value),site);return value;}
    private static boolean nextBoolean(Mc263WorldgenRandomSource r,int site,TraceSink t){boolean value=r.nextBoolean();emit(t,"rng_bool",value?1:0,site);return value;}
    private static int manhattan(Pos a,Pos b){return Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z());}
    private static Result result(boolean placed,Counts c,TraceSink t,String key){emit(t,"result",id(key),placed?1:0,c.reads,c.writes,c.retained,c.bees);return new Result(placed,c.reads,c.writes,c.retained,c.bees,0);}
    private static void emit(TraceSink t,String phase,long...values){if(t.enabled())t.record(phase,values);}
    private static String normalize(String key){return key.startsWith("minecraft:")?key:"minecraft:"+key;}
    private static long id(String s){long h=0xcbf29ce484222325L;for(byte b:s.getBytes(StandardCharsets.UTF_8))h=(h^(b&255))*0x100000001b3L;return h;}

    public static byte[] encodeTrace(List<TraceEvent> events){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(TRACE_MAGIC);out.writeShort(TRACE_VERSION);byte[] schema=TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);out.writeShort(schema.length);out.write(schema);out.writeInt(events.size());for(TraceEvent e:events){Phase phase=PHASES.get(e.phase());if(phase==null||e.values().length!=phase.arity)throw new IllegalArgumentException(e.phase());out.writeByte(phase.id);out.writeByte(phase.arity);for(long v:e.values())out.writeLong(v);}return bytes.toByteArray();}catch(IOException e){throw new AssertionError(e);}}

    private static Map<String,Spec> trees(){Map<String,Spec>m=new LinkedHashMap<>();
        add(m,new Spec("minecraft:acacia","minecraft:acacia_log","minecraft:acacia_leaves",5,2,2,Trunk.FORKING,Foliage.ACACIA,1,0,2,true,0,0,false,false,false,0,0));
        for(String k:List.of("fancy_oak","fancy_oak_bees_002","fancy_oak_bees_005","fancy_oak_bees","fancy_oak_bees_0002_leaf_litter","fancy_oak_leaf_litter"))add(m,new Spec("minecraft:"+k,"minecraft:oak_log","minecraft:oak_leaves",3,11,0,Trunk.FANCY,Foliage.FANCY,0,0,0,true,4,0,false,false,false,0,0));
        add(m,new Spec("minecraft:dark_oak_leaf_litter","minecraft:dark_oak_log","minecraft:dark_oak_leaves",6,2,1,Trunk.DARK,Foliage.DARK,1,1,2,true,0,0,false,false,false,0,0));
        add(m,new Spec("minecraft:pale_oak","minecraft:pale_oak_log","minecraft:pale_oak_leaves",6,2,1,Trunk.DARK,Foliage.DARK,1,1,2,true,0,0,false,true,false,0,0));
        add(m,new Spec("minecraft:pale_oak_creaking","minecraft:pale_oak_log","minecraft:pale_oak_leaves",6,2,1,Trunk.DARK,Foliage.DARK,1,1,2,true,0,0,false,true,true,0,0));
        add(m,new Spec("minecraft:spruce","minecraft:spruce_log","minecraft:spruce_leaves",5,2,1,Trunk.STRAIGHT,Foliage.SPRUCE,2,0,2,true,0,0,false,false,false,0,0));
        add(m,new Spec("minecraft:pine","minecraft:spruce_log","minecraft:spruce_leaves",6,4,0,Trunk.STRAIGHT,Foliage.PINE,2,0,2,true,0,0,false,false,false,0,0));
        add(m,new Spec("minecraft:mega_jungle_tree","minecraft:jungle_log","minecraft:jungle_leaves",10,2,19,Trunk.MEGA_JUNGLE,Foliage.MEGA_JUNGLE,1,1,2,true,0,0,true,false,false,0,0));
        add(m,new Spec("minecraft:mega_spruce","minecraft:spruce_log","minecraft:spruce_leaves",13,2,14,Trunk.GIANT,Foliage.MEGA_PINE,1,1,2,true,0,0,false,false,false,13,17).altered());
        add(m,new Spec("minecraft:mega_pine","minecraft:spruce_log","minecraft:spruce_leaves",13,2,14,Trunk.GIANT,Foliage.MEGA_PINE,1,1,2,true,0,0,false,false,false,3,7).altered());
        add(m,new Spec("minecraft:cherry_bees_005","minecraft:cherry_log","minecraft:cherry_leaves",7,1,0,Trunk.CHERRY,Foliage.CHERRY,1,0,2,true,0,0,false,false,false,0,0));return Map.copyOf(m);}
    private static void add(Map<String,Spec>m,Spec s){m.put(s.key,s);}
    private static Choice c(String k,float p){return new Choice(normalize(k),p,0);}private static Choice w(String k,int n){return new Choice(normalize(k),0,n);}
    private static Map<String,Wrapper> wrappers(){
        Map<String,Wrapper>m=new LinkedHashMap<>();
        wrap(m,"acacia_checked","acacia","minecraft:acacia_sapling",false);
        wrap(m,"birch_checked","birch","minecraft:birch_sapling",false);
        wrap(m,"oak_checked","oak","minecraft:oak_sapling",false);
        wrap(m,"fancy_oak_checked","fancy_oak","minecraft:oak_sapling",false);
        wrap(m,"spruce_checked","spruce","minecraft:spruce_sapling",false);
        wrap(m,"mega_spruce_checked","mega_spruce","minecraft:spruce_sapling",false);
        wrap(m,"pale_oak_checked","pale_oak","minecraft:pale_oak_sapling",false);
        wrap(m,"pine_checked","pine","minecraft:spruce_sapling",false);
        wrap(m,"mega_pine_checked","mega_pine","minecraft:spruce_sapling",false);
        wrap(m,"mega_jungle_tree_checked","mega_jungle_tree","minecraft:jungle_sapling",false);
        wrap(m,"cherry_checked","cherry","minecraft:cherry_sapling",false);
        wrap(m,"jungle_tree","jungle_tree","minecraft:jungle_sapling",false);
        wrap(m,"fallen_oak_tree","fallen_oak_tree","minecraft:oak_sapling",false);
        wrap(m,"fallen_birch_tree","fallen_birch_tree","minecraft:birch_sapling",false);
        wrap(m,"fallen_super_birch_tree","fallen_super_birch_tree","minecraft:birch_sapling",false);
        wrap(m,"fallen_jungle_tree","fallen_jungle_tree","minecraft:jungle_sapling",false);
        wrap(m,"fallen_spruce_tree","fallen_spruce_tree","minecraft:spruce_sapling",false);
        wrap(m,"pine_on_snow","pine",null,true);
        wrap(m,"spruce_on_snow","spruce",null,true);
        wrap(m,"pale_oak_creaking_checked","pale_oak_creaking","minecraft:pale_oak_sapling",false);
        return Map.copyOf(m);
    }
    private static void wrap(Map<String,Wrapper>m,String key,String target,String sapling,boolean snow){
        m.put(normalize(key),new Wrapper(normalize(key),normalize(target),sapling,snow));
    }
    // 26.3 TreePlacements: every tree a selector picks is a placed feature carrying
    // would_survive(<sapling>); without it an origin on a lower tree's leaves grows a tree whose
    // trunk base turns those leaves into dirt, stacking tree over tree. These placed features share
    // their configured feature's name, so the check applies only when a selector picks the key;
    // callers placing the configured feature directly (abandoned camp, fixtures) keep its body.
    private static Map<String,String> selectedSaplings(){Map<String,String>m=new LinkedHashMap<>();
        for(String k:List.of("jungle_bush","oak_leaf_litter","fancy_oak_leaf_litter","oak_bees_002","fancy_oak_bees_002",
                "fancy_oak_bees","fancy_oak_bees_0002_leaf_litter",
                "oak_bees_0002_leaf_litter"))m.put(normalize(k),"minecraft:oak_sapling");
        m.put(normalize("dark_oak_leaf_litter"),"minecraft:dark_oak_sapling");
        for(String k:List.of("birch_leaf_litter","birch_bees_002","super_birch_bees","super_birch_bees_0002","birch_bees_0002",
                "birch_bees_0002_leaf_litter"))m.put(normalize(k),"minecraft:birch_sapling");
        for(String k:List.of("red_poplar_leaf_litter","orange_poplar_leaf_litter","yellow_poplar_leaf_litter",
                "fallen_poplar_tree"))m.put(normalize(k),"minecraft:poplar_sapling");
        return Map.copyOf(m);}
    private static Map<String,Selector> selectors(){Map<String,Selector>m=new LinkedHashMap<>();
        random(m,"trees_savanna",List.of(c("acacia_checked",.8f),c("fallen_oak_tree",.0125f)),"oak_checked");
        random(m,"bamboo_vegetation",List.of(c("fancy_oak_checked",.05f),c("jungle_bush",.15f),c("mega_jungle_tree_checked",.7f)),"bamboo_jungle_grass");
        random(m,"trees_sparse_jungle",List.of(c("fancy_oak_checked",.1f),c("jungle_bush",.5f),c("fallen_jungle_tree",.0125f)),"jungle_tree");
        weighted(m,"trees_dappled_forest",List.of(w("red_poplar_leaf_litter",200),w("orange_poplar_leaf_litter",240),w("yellow_poplar_leaf_litter",90),w("spruce_checked",27)),"fallen_poplar_tree",677);
        random(m,"trees_badlands",List.of(c("fallen_oak_tree",.0125f)),"oak_leaf_litter");
        random(m,"trees_jungle",List.of(c("fancy_oak_checked",.1f),c("jungle_bush",.5f),c("mega_jungle_tree_checked",.33333334f),c("fallen_jungle_tree",.0125f)),"jungle_tree");
        random(m,"pale_garden_vegetation",List.of(c("pale_oak_creaking_checked",.1f),c("pale_oak_checked",.9f)),"pale_oak_checked");
        random(m,"trees_dark_forest",List.of(c("huge_brown_mushroom",.025f),c("huge_red_mushroom",.05f),c("dark_oak_leaf_litter",.6666667f),c("fallen_birch_tree",.0025f),c("birch_leaf_litter",.2f),c("fallen_oak_tree",.0125f),c("fancy_oak_leaf_litter",.1f)),"oak_leaf_litter");
        random(m,"trees_flower_forest",List.of(c("fallen_birch_tree",.0025f),c("birch_bees_002",.2f),c("fancy_oak_bees_002",.1f)),"oak_bees_002");
        random(m,"birch_tall",List.of(c("fallen_super_birch_tree",.00625f),c("super_birch_bees_0002",.5f),c("fallen_birch_tree",.0125f)),"birch_bees_0002");
        random(m,"trees_birch",List.of(c("fallen_birch_tree",.0125f)),"birch_bees_0002");
        random(m,"birch_oak_leaf_litter",List.of(c("fallen_birch_tree",.0025f),c("birch_bees_0002_leaf_litter",.2f),c("fancy_oak_bees_0002_leaf_litter",.1f),c("fallen_oak_tree",.0125f)),"oak_bees_0002_leaf_litter");
        random(m,"meadow_trees",List.of(c("fancy_oak_bees",.5f)),"super_birch_bees");
        random(m,"trees_windswept_hills",List.of(c("fallen_spruce_tree",.008325f),c("spruce_checked",.666f),c("fancy_oak_checked",.1f),c("fallen_oak_tree",.0125f)),"oak_checked");
        random(m,"trees_old_growth_pine_taiga",List.of(c("mega_spruce_checked",.025641026f),c("mega_pine_checked",.30769232f),c("pine_checked",.33333334f),c("fallen_spruce_tree",.0125f)),"spruce_checked");
        random(m,"trees_old_growth_spruce_taiga",List.of(c("mega_spruce_checked",.33333334f),c("pine_checked",.33333334f),c("fallen_spruce_tree",.0125f)),"spruce_checked");
        random(m,"trees_taiga",List.of(c("pine_checked",.33333334f),c("fallen_spruce_tree",.0125f)),"spruce_checked");
        random(m,"trees_grove",List.of(c("pine_on_snow",.33333334f)),"spruce_on_snow");
        random(m,"trees_snowy",List.of(c("fallen_spruce_tree",.0125f)),"spruce_checked");
        random(m,"trees_water",List.of(c("fancy_oak_checked",.1f)),"oak_checked");
        random(m,"trees_plains",List.of(c("fancy_oak_bees_005",.33333334f),c("fallen_oak_tree",.0125f)),"oak_bees_005");
        return Map.copyOf(m);}
    private static void random(Map<String,Selector>m,String k,List<Choice>c,String f){m.put(normalize(k),new Selector(normalize(k),c,normalize(f),false,0));}private static void weighted(Map<String,Selector>m,String k,List<Choice>c,String f,int n){m.put(normalize(k),new Selector(normalize(k),c,normalize(f),true,n));}
    private static Map<Integer,Placement> placements(){Map<Integer,Placement>m=new TreeMap<>();put(m,1,"trees_windswept_savanna","trees_savanna",2,10,0,null,false);put(m,3,"bamboo_vegetation","bamboo_vegetation",30,10,0,null,false);put(m,4,"trees_sparse_jungle","trees_sparse_jungle",2,10,0,null,false);put(m,5,"trees_dappled_forest","trees_dappled_forest",6,0,0,null,false);put(m,8,"trees_badlands","trees_badlands",5,10,0,"minecraft:oak_sapling",false);put(m,10,"trees_jungle","trees_jungle",50,10,0,null,false);put(m,12,"trees_savanna","trees_savanna",1,10,0,null,false);put(m,16,"pale_garden_vegetation","pale_garden_vegetation",16,0,0,null,false);put(m,20,"dark_forest_vegetation","trees_dark_forest",16,0,0,null,false);put(m,22,"trees_flower_forest","trees_flower_forest",6,10,0,null,false);put(m,26,"birch_tall","birch_tall",10,10,0,null,false);put(m,27,"trees_birch","trees_birch",10,10,0,"minecraft:birch_sapling",false);put(m,28,"trees_birch_and_oak_leaf_litter","birch_oak_leaf_litter",10,10,0,null,false);put(m,40,"trees_meadow","meadow_trees",0,0,100,null,false);put(m,42,"trees_windswept_forest","trees_windswept_hills",3,10,0,null,false);put(m,44,"trees_old_growth_pine_taiga","trees_old_growth_pine_taiga",10,10,0,null,false);put(m,45,"trees_old_growth_spruce_taiga","trees_old_growth_spruce_taiga",10,10,0,null,false);put(m,46,"trees_taiga","trees_taiga",10,10,0,null,false);put(m,47,"trees_grove","trees_grove",10,10,0,null,false);put(m,48,"trees_windswept_hills","trees_windswept_hills",0,10,0,null,false);put(m,49,"trees_snowy","trees_snowy",0,10,0,"minecraft:spruce_sapling",false);put(m,53,"trees_water","trees_water",0,10,0,null,false);put(m,55,"trees_plains","trees_plains",0,20,0,"minecraft:oak_sapling",true);put(m,59,"trees_cherry","cherry_bees_005",10,10,0,"minecraft:cherry_sapling",false);return Map.copyOf(m);}
    private static void put(Map<Integer,Placement>m,int i,String key,String s,int count,int denom,int rarity,String sapling,boolean before){m.put(i,new Placement(i,normalize(key),normalize(s),count,denom,rarity,0,sapling,before));}

    private record Spec(String key,String log,String leaf,int base,int randA,int randB,Trunk trunk,Foliage foliage,int limit,int lower,int upper,boolean ignoreVines,int unusedA,int unusedB,boolean megaVines,boolean pale,boolean creaking,int crownMin,int crownMax,boolean alterGround){Spec(String key,String log,String leaf,int base,int randA,int randB,Trunk trunk,Foliage foliage,int limit,int lower,int upper,boolean ignoreVines,int a,int b,boolean vines,boolean pale,boolean heart,int cmin,int cmax){this(key,log,leaf,base,randA,randB,trunk,foliage,limit,lower,upper,ignoreVines,a,b,vines,pale,heart,cmin,cmax,false);}Spec altered(){return new Spec(key,log,leaf,base,randA,randB,trunk,foliage,limit,lower,upper,ignoreVines,unusedA,unusedB,megaVines,pale,creaking,crownMin,crownMax,true);}}
    private record Selector(String key,List<Choice> choices,String fallback,boolean weighted,int totalWeight){}
    private record Wrapper(String key,String target,String sapling,boolean snow){}
    private record Choice(String key,float chance,int weight){}
    private record Placement(int index,String key,String selector,int baseCount,int extraDenominator,int rarity,int maxWaterDepth,String sapling,boolean survivalBeforeBiome){}
    private record Attachment(Pos pos,int radiusOffset,boolean doubleTrunk,int heightOffset){Attachment(Pos p,int r,boolean d){this(p,r,d,0);}}
    private record FancyAttachment(Pos pos,int branchBase){}
    private record FoliageParams(int height,int radius){}
    private record Phase(int id,int arity){}
    private enum Trunk{FORKING,FANCY,DARK,STRAIGHT,GIANT,MEGA_JUNGLE,CHERRY}
    private enum Foliage{ACACIA,FANCY,DARK,SPRUCE,PINE,MEGA_JUNGLE,MEGA_PINE,CHERRY;
        boolean skip(int x,int y,int z,int radius,boolean d,Mc263WorldgenRandomSource r,TraceSink t){return switch(this){case ACACIA->y==0?(x>1||z>1)&&x!=0&&z!=0:x==radius&&z==radius&&radius>0;case FANCY->((x+.5f)*(x+.5f)+(z+.5f)*(z+.5f))>radius*radius;case DARK->y==-1&&!d?x==radius&&z==radius:y==1&&x+z>radius*2-2;case SPRUCE,PINE->x==radius&&z==radius&&radius>0;case MEGA_JUNGLE,MEGA_PINE->x+z>=7||x*x+z*z>radius*radius;case CHERRY->{if(y==-1&&(x==radius||z==radius)&&nextFloat(r,180,t)<.25f)yield true;boolean corner=x==radius&&z==radius;if(radius>2)yield corner||(x+z>radius*2-2&&nextFloat(r,183,t)<.5f);yield corner&&nextFloat(r,184,t)<.5f;}};}}
    private enum Dir{N(0,0,-1,"north","z"),E(1,0,0,"east","x"),S(0,0,1,"south","z"),W(-1,0,0,"west","x"),UP(0,1,0,"up","y"),DOWN(0,-1,0,"down","y");static final Dir[]H={N,E,S,W};static final Dir[]VINE={W,E,N,S};static final Dir[]ALL=values();final int dx,dy,dz;final String key,axis;Dir(int x,int y,int z,String k,String a){dx=x;dy=y;dz=z;key=k;axis=a;}Dir opposite(){return switch(this){case N->S;case E->W;case S->N;case W->E;case UP->DOWN;case DOWN->UP;};}Dir clockwise(){return switch(this){case N->E;case E->S;case S->W;case W->N;default->throw new IllegalStateException();};}}
    private static final class Counts{int reads,writes,retained,bees;void add(Result r){reads+=r.reads();writes+=r.writes();retained+=r.retained();bees+=r.bees();}}
}
