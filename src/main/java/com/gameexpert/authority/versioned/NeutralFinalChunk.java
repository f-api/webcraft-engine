package com.gameexpert.authority.versioned;

import java.util.*;

/** App-owned immutable projection. Only a selected producer verifies provenance; this DTO never
 * substitutes for its retained carrier bytes or imports its codec/type namespace. */
public final class NeutralFinalChunk {
    private ProducerBinding producerBinding;
    private Long verifiedGenerationSeed;

    /** Only a verified generation-response boundary may grant this extra capability. */
    void bindGenerationSeed(long seed) {
        producer();
        if (verifiedGenerationSeed != null) throw new IllegalStateException("generation seed already bound");
        verifiedGenerationSeed = seed;
    }

    public void requireGenerationSeed(long expectedSeed) {
        producer();
        if (verifiedGenerationSeed == null) {
            throw new IllegalArgumentException("canonical generation world seed proof is absent");
        }
        if (verifiedGenerationSeed.longValue() != expectedSeed) {
            throw new IllegalArgumentException("canonical product world seed mismatch");
        }
    }
    void bind(ProducerBinding binding) {
        if (producerBinding != null) throw new IllegalStateException("producer already bound");
        producerBinding = Objects.requireNonNull(binding);
    }
    private ProducerBinding producer() {
        if (producerBinding == null) throw new IllegalStateException("unverified neutral projection has no producer authority");
        return producerBinding;
    }
    public byte[] encodedCarrier() { return producer().encodedCarrier(); }
    public com.gameexpert.world.WorldGenerationProfile generationProfile(){return producer().generationProfile();}
    public NeutralFinalChunk verifyCarrier(byte[] encoded) { return producer().verify(chunkX, chunkZ, encoded); }
    public NeutralFinalChunk withSidecars(Sidecars selected) { return producer().select(this, selected); }
    /** The verified sidecars of {@link #withSidecars}, answered once per carrier and selection. */
    public Sidecars projectedSidecars(Sidecars selected) { return producer().projectSidecars(this, selected); }
    /** Producer identity of this carrier without sidecars, as lowercase SHA-256 hex. */
    public String sourceFingerprintSha256() {
        // 내용과 생산기 묶음은 한 번 정해지면 바뀌지 않으므로 지문도 한 번만 묻는다. 청크 하나가 승인·확인·정산마다
        // 같은 지문을 생산기에 다시 인코딩해 물으면서 영속 스레드 CPU 의 약 15%를 썼다.
        String cached = sourceFingerprint;
        if (cached == null) {
            cached = producer().sourceFingerprint(this);
            sourceFingerprint = cached;
        }
        return cached;
    }
    private volatile String sourceFingerprint;
    public StateOverride defaultState(int blockId) { return producer().defaultState(blockId); }
    public boolean matchesCanonicalMob(StructureEntity row) { return producer().matchesCanonicalMob(this, row); }
    public byte[] resolveLoot(byte[] context, long seed, String table, long rawSeed, int x, int y, int z, int slots, Long initialLo, Long initialHi) { return producer().loot(this, true, context, seed, table, rawSeed, x, y, z, slots, initialLo, initialHi); }
    public LateLootOutcome prepareLateLoot(byte[] context,long seed,String table,long rawSeed,
            int x,int y,int z,int slots,Long initialLo,Long initialHi,
            CanonicalStructureSnapshot snapshot,List<LateLootOutcome.Claim> claims) {
        return producer().lateLoot(this,context,seed,table,rawSeed,x,y,z,slots,initialLo,initialHi,snapshot,claims);
    }
    public void verifyLoot(byte[] context, long seed, String table, long rawSeed, int x, int y, int z, int slots, Long initialLo, Long initialHi) { producer().loot(this, false, context, seed, table, rawSeed, x, y, z, slots, initialLo, initialHi); }
    public byte[] renderVerifiedMapPreview(long worldSeed, TargetMap target, boolean seaLevel) { return producer().renderPreview(this, worldSeed, target, seaLevel); }
    private final int chunkX;
    private final int chunkZ;
    private final PackedBlockIds blockIds;
    private final Map<Integer, StateOverride> stateOverrides;
    private final int[] worldSurfaceWg;
    private final int[] oceanFloorWg;
    private final int[] motionBlocking;
    private final Sidecars sidecars;
    public NeutralFinalChunk(int chunkX, int chunkZ, short[] blockIds, Map<Integer, StateOverride> stateOverrides, int[] worldSurfaceWg, int[] oceanFloorWg, int[] motionBlocking, Sidecars sidecars) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blockIds = PackedBlockIds.pack(blockIds);
        this.stateOverrides = Map.copyOf(stateOverrides);
        this.worldSurfaceWg = worldSurfaceWg.clone();
        this.oceanFloorWg = oceanFloorWg.clone();
        this.motionBlocking = motionBlocking.clone();
        this.sidecars = Objects.requireNonNull(sidecars);
    }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public short[] blockIds() { return blockIds.unpack(); }
    public int blockIdCount() { return blockIds.length; }
    public short blockIdAt(int index) { return blockIds.get(index); }
    public Map<Integer, StateOverride> stateOverrides() { return stateOverrides; }
    /** {@link #stateOverrides()} without boxing the cell index; built once per carrier. */
    public StateOverride stateOverrideAt(int blockIndex) {
        int[] keys = overrideKeys;
        if (keys == null) {
            int capacity = 2;
            while (capacity < stateOverrides.size() * 2) capacity <<= 1;
            keys = new int[capacity];
            StateOverride[] values = new StateOverride[capacity];
            int mask = capacity - 1;
            for (Map.Entry<Integer, StateOverride> entry : stateOverrides.entrySet()) {
                int slot = entry.getKey() * 0x9E3779B9 & mask;
                while (keys[slot] != 0) slot = (slot + 1) & mask;
                keys[slot] = entry.getKey() + 1;
                values[slot] = entry.getValue();
            }
            overrideValues = values;
            overrideKeys = keys;
        }
        int mask = keys.length - 1;
        int slot = blockIndex * 0x9E3779B9 & mask;
        int encoded;
        while ((encoded = keys[slot]) != 0) {
            if (encoded == blockIndex + 1) return overrideValues[slot];
            slot = (slot + 1) & mask;
        }
        return null;
    }
    private volatile int[] overrideKeys;
    private StateOverride[] overrideValues;
    public int[] worldSurfaceWg() { return worldSurfaceWg.clone(); }
    public int[] oceanFloorWg() { return oceanFloorWg.clone(); }
    public int[] motionBlocking() { return motionBlocking.clone(); }
    public Sidecars sidecars() { return sidecars; }
    @Override public boolean equals(Object other) {
        if (this==other) return true;
        if (!(other instanceof NeutralFinalChunk value)) return false;
        return chunkX==value.chunkX && chunkZ==value.chunkZ && blockIds.equals(value.blockIds) && Objects.equals(stateOverrides,value.stateOverrides) && Arrays.equals(worldSurfaceWg,value.worldSurfaceWg) && Arrays.equals(oceanFloorWg,value.oceanFloorWg) && Arrays.equals(motionBlocking,value.motionBlocking) && Objects.equals(sidecars,value.sidecars);
    }
    @Override public int hashCode() { return Objects.hash(chunkX,chunkZ,blockIds.hashCode(),stateOverrides,Arrays.hashCode(worldSurfaceWg),Arrays.hashCode(oceanFloorWg),Arrays.hashCode(motionBlocking),sidecars); }
    public enum ContainerLootSourceSection { LOOT, ENTS }
    public enum TickPriority {
        EXTREMELY_HIGH(-3), VERY_HIGH(-2), HIGH(-1), NORMAL(0), LOW(1), VERY_LOW(2), EXTREMELY_LOW(3);
        private final int value;
        TickPriority(int value) { this.value=value; }
        public int value() { return value; }
        public static TickPriority fromValue(int value) { for(var p:values())if(p.value==value)return p;throw new IllegalArgumentException("priority outside -3..3"); }
    }
    public static final class StateOverride {
        private final int packed;
        private final int blockId;
        private final int stateCode;
        private final String exactState;
        private final boolean leavesTag;
        public StateOverride(int packed, int blockId, int stateCode, String exactState) {
            this(packed, blockId, stateCode, exactState, false);
        }
        private final String fluidTypeKey;
        public StateOverride(int packed, int blockId, int stateCode, String exactState, boolean leavesTag) {
            this(packed,blockId,stateCode,exactState,leavesTag,"minecraft:empty");
        }
        public StateOverride(int packed, int blockId, int stateCode, String exactState, boolean leavesTag, String fluidTypeKey) {
            // Resident chunks hold tens of thousands of these over a small vocabulary of state strings.
            this.fluidTypeKey = Objects.requireNonNull(fluidTypeKey).intern();
            this.leavesTag = leavesTag;
            this.packed = packed;
            this.blockId = blockId;
            this.stateCode = stateCode;
            this.exactState = Objects.requireNonNull(exactState).intern();
        }
        public int packed() { return packed; }
        public int blockId() { return blockId; }
        public int stateCode() { return stateCode; }
        public String exactState() { return exactState; }
        public boolean isLeavesTag() { return leavesTag; }
        public String fluidTypeKey() { return fluidTypeKey; }
        public String blockKey() { int bracket=exactState.indexOf('['); return bracket<0?exactState:exactState.substring(0,bracket); }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof StateOverride value)) return false;
            return packed==value.packed && blockId==value.blockId && stateCode==value.stateCode && leavesTag==value.leavesTag && Objects.equals(fluidTypeKey,value.fluidTypeKey) && Objects.equals(exactState,value.exactState);
        }
        @Override public int hashCode() { return Objects.hash(packed,blockId,stateCode,exactState,leavesTag,fluidTypeKey); }
    }
    public static final class BlockTick {
        private final int packed;
        private final int blockId;
        private final String key;
        private final int delay;
        private final TickPriority priority;
        private final long subTickOrder;
        public BlockTick(int packed, int blockId, String key, int delay, TickPriority priority, long subTickOrder) {
            position(packed); unsigned16(blockId); NeutralFinalChunk.key(key); nonnegative(delay);
            this.packed = packed;
            this.blockId = blockId;
            this.key = Objects.requireNonNull(key).intern();
            this.delay = delay;
            this.priority = Objects.requireNonNull(priority);
            this.subTickOrder = subTickOrder;
        }
        public int packed() { return packed; }
        public int blockId() { return blockId; }
        public String key() { return key; }
        public int delay() { return delay; }
        public TickPriority priority() { return priority; }
        public long subTickOrder() { return subTickOrder; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof BlockTick value)) return false;
            return packed==value.packed && blockId==value.blockId && Objects.equals(key,value.key) && delay==value.delay && Objects.equals(priority,value.priority) && subTickOrder==value.subTickOrder;
        }
        @Override public int hashCode() { return Objects.hash(packed,blockId,key,delay,priority,subTickOrder); }
    }
    public static final class FluidTick {
        private final int packed;
        private final String key;
        private final int delay;
        private final TickPriority priority;
        private final long subTickOrder;
        public FluidTick(int packed, String key, int delay, TickPriority priority, long subTickOrder) {
            position(packed); NeutralFinalChunk.key(key); nonnegative(delay);
            this.packed = packed;
            this.key = Objects.requireNonNull(key);
            this.delay = delay;
            this.priority = Objects.requireNonNull(priority);
            this.subTickOrder = subTickOrder;
        }
        public int packed() { return packed; }
        public String key() { return key; }
        public int delay() { return delay; }
        public TickPriority priority() { return priority; }
        public long subTickOrder() { return subTickOrder; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof FluidTick value)) return false;
            return packed==value.packed && Objects.equals(key,value.key) && delay==value.delay && Objects.equals(priority,value.priority) && subTickOrder==value.subTickOrder;
        }
        @Override public int hashCode() { return Objects.hash(packed,key,delay,priority,subTickOrder); }
    }
    public static final class Loot {
        private final int packed;
        private final String facing;
        private final String table;
        private final long seed;
        public Loot(int packed, String facing, String table, long seed) {
            position(packed); if (!Set.of("north", "south", "east", "west", "up", "down").contains(facing)) throw new IllegalArgumentException("invalid container facing"); key(table);
            this.packed = packed;
            this.facing = Objects.requireNonNull(facing);
            this.table = Objects.requireNonNull(table);
            this.seed = seed;
        }
        public int packed() { return packed; }
        public String facing() { return facing; }
        public String table() { return table; }
        public long seed() { return seed; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof Loot value)) return false;
            return packed==value.packed && Objects.equals(facing,value.facing) && Objects.equals(table,value.table) && seed==value.seed;
        }
        @Override public int hashCode() { return Objects.hash(packed,facing,table,seed); }
    }
    public static final class Spawner {
        private final int packed;
        private final String entityType;
        public Spawner(int packed, String entityType) {
            position(packed); key(entityType);
            this.packed = packed;
            this.entityType = Objects.requireNonNull(entityType);
        }
        public int packed() { return packed; }
        public String entityType() { return entityType; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof Spawner value)) return false;
            return packed==value.packed && Objects.equals(entityType,value.entityType);
        }
        @Override public int hashCode() { return Objects.hash(packed,entityType); }
    }
    public static final class Owner {
        private final int packed;
        private final long owner;
        public Owner(int packed, long owner) {
            position(packed);
            this.packed = packed;
            this.owner = owner;
        }
        public int packed() { return packed; }
        public long owner() { return owner; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof Owner value)) return false;
            return packed==value.packed && owner==value.owner;
        }
        @Override public int hashCode() { return Objects.hash(packed,owner); }
    }
    public static final class Archaeology {
        private final int packed;
        private final String table;
        private final long seed;
        public Archaeology(int packed, String table, long seed) {
            position(packed); key(table);
            this.packed = packed;
            this.table = Objects.requireNonNull(table);
            this.seed = seed;
        }
        public int packed() { return packed; }
        public String table() { return table; }
        public long seed() { return seed; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof Archaeology value)) return false;
            return packed==value.packed && Objects.equals(table,value.table) && seed==value.seed;
        }
        @Override public int hashCode() { return Objects.hash(packed,table,seed); }
    }
    public static final class BeeNest {
        private final int packed;
        private final List<Integer> ticksInHive;
        public BeeNest(int packed, List<Integer> ticksInHive) {
            position(packed); if (ticksInHive.size()>65535) throw new IllegalArgumentException("too many bees"); for (int ticks:ticksInHive) if (ticks<0 || ticks>598) throw new IllegalArgumentException("bee ticks outside 0..598");
            this.packed = packed;
            this.ticksInHive = List.copyOf(ticksInHive);
        }
        public int packed() { return packed; }
        public List<Integer> ticksInHive() { return ticksInHive; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof BeeNest value)) return false;
            return packed==value.packed && Objects.equals(ticksInHive,value.ticksInHive);
        }
        @Override public int hashCode() { return Objects.hash(packed,ticksInHive); }
    }
    public static final class BlockEntity {
        private final int packed;
        private final String blockIdentity;
        private final String entityType;
        private final byte[] canonicalNbt;
        public BlockEntity(int packed, String blockIdentity, String entityType, byte[] canonicalNbt) {
            position(packed); key(blockIdentity); key(entityType); payload(canonicalNbt); if (canonicalNbt.length<3 || canonicalNbt[0]!=10) throw new IllegalArgumentException("block entity payload must be compound NBT");
            this.packed = packed;
            this.blockIdentity = Objects.requireNonNull(blockIdentity);
            this.entityType = Objects.requireNonNull(entityType);
            this.canonicalNbt = canonicalNbt.clone();
        }
        public int packed() { return packed; }
        public String blockIdentity() { return blockIdentity; }
        public String entityType() { return entityType; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof BlockEntity value)) return false;
            return packed==value.packed && Objects.equals(blockIdentity,value.blockIdentity) && Objects.equals(entityType,value.entityType) && Arrays.equals(canonicalNbt,value.canonicalNbt);
        }
        @Override public int hashCode() { return Objects.hash(packed,blockIdentity,entityType,Arrays.hashCode(canonicalNbt)); }
    }
    public static final class StructureEntity {
        private final String entityKey;
        private final String spawnReason;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final double velocityX;
        private final double velocityY;
        private final double velocityZ;
        private final String lootTable;
        private final long lootSeed;
        private final byte[] canonicalPayload;
        public StructureEntity(String entityKey, String spawnReason, double x, double y, double z, float yaw, float pitch, double velocityX, double velocityY, double velocityZ, String lootTable, long lootSeed, byte[] canonicalPayload) {
            key(entityKey); key(spawnReason); for(double value:new double[]{x,y,z,yaw,pitch,velocityX,velocityY,velocityZ}) if(!Double.isFinite(value)) throw new IllegalArgumentException("nonfinite entity value"); if(!lootTable.isEmpty()) key(lootTable); payload(canonicalPayload);
            this.entityKey = Objects.requireNonNull(entityKey);
            this.spawnReason = Objects.requireNonNull(spawnReason);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.lootTable = Objects.requireNonNull(lootTable);
            this.lootSeed = lootSeed;
            this.canonicalPayload = canonicalPayload.clone();
        }
        public String entityKey() { return entityKey; }
        public String spawnReason() { return spawnReason; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public double velocityX() { return velocityX; }
        public double velocityY() { return velocityY; }
        public double velocityZ() { return velocityZ; }
        public String lootTable() { return lootTable; }
        public long lootSeed() { return lootSeed; }
        public byte[] canonicalPayload() { return canonicalPayload.clone(); }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof StructureEntity value)) return false;
            return Objects.equals(entityKey,value.entityKey) && Objects.equals(spawnReason,value.spawnReason) && Double.doubleToLongBits(x)==Double.doubleToLongBits(value.x) && Double.doubleToLongBits(y)==Double.doubleToLongBits(value.y) && Double.doubleToLongBits(z)==Double.doubleToLongBits(value.z) && Float.floatToIntBits(yaw)==Float.floatToIntBits(value.yaw) && Float.floatToIntBits(pitch)==Float.floatToIntBits(value.pitch) && Double.doubleToLongBits(velocityX)==Double.doubleToLongBits(value.velocityX) && Double.doubleToLongBits(velocityY)==Double.doubleToLongBits(value.velocityY) && Double.doubleToLongBits(velocityZ)==Double.doubleToLongBits(value.velocityZ) && Objects.equals(lootTable,value.lootTable) && lootSeed==value.lootSeed && Arrays.equals(canonicalPayload,value.canonicalPayload);
        }
        @Override public int hashCode() { return Objects.hash(entityKey,spawnReason,x,y,z,yaw,pitch,velocityX,velocityY,velocityZ,lootTable,lootSeed,Arrays.hashCode(canonicalPayload)); }
    }
    public static final class Sidecars {
        public static final Sidecars EMPTY = new Sidecars(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        public Sidecars(List<BlockTick> blockTicks, List<FluidTick> fluidTicks, List<Loot> loot, List<Spawner> spawners, List<Owner> owners, List<Archaeology> archaeology, List<BeeNest> bees, List<BlockEntity> blockEntities, List<StructureEntity> entities) {
            this(blockTicks, fluidTicks, loot, spawners, owners, archaeology, bees, blockEntities, entities, List.of());
        }
        private final List<BlockTick> blockTicks;
        private final List<FluidTick> fluidTicks;
        private final List<Loot> loot;
        private final List<Spawner> spawners;
        private final List<Owner> owners;
        private final List<Archaeology> archaeology;
        private final List<BeeNest> bees;
        private final List<BlockEntity> blockEntities;
        private final List<StructureEntity> entities;
        private final List<ContainerLootDeclaration> containerLootDeclarations;
        public Sidecars(List<BlockTick> blockTicks, List<FluidTick> fluidTicks, List<Loot> loot, List<Spawner> spawners, List<Owner> owners, List<Archaeology> archaeology, List<BeeNest> bees, List<BlockEntity> blockEntities, List<StructureEntity> entities, List<ContainerLootDeclaration> containerLootDeclarations) {
            this.blockTicks = List.copyOf(blockTicks);
            this.fluidTicks = List.copyOf(fluidTicks);
            this.loot = List.copyOf(loot);
            this.spawners = List.copyOf(spawners);
            this.owners = List.copyOf(owners);
            this.archaeology = List.copyOf(archaeology);
            this.bees = List.copyOf(bees);
            this.blockEntities = List.copyOf(blockEntities);
            this.entities = List.copyOf(entities);
            this.containerLootDeclarations = List.copyOf(containerLootDeclarations);
        }
        public List<BlockTick> blockTicks() { return blockTicks; }
        public List<FluidTick> fluidTicks() { return fluidTicks; }
        public List<Loot> loot() { return loot; }
        public List<Spawner> spawners() { return spawners; }
        public List<Owner> owners() { return owners; }
        public List<Archaeology> archaeology() { return archaeology; }
        public List<BeeNest> bees() { return bees; }
        public List<BlockEntity> blockEntities() { return blockEntities; }
        public List<StructureEntity> entities() { return entities; }
        public List<ContainerLootDeclaration> containerLootDeclarations() { return containerLootDeclarations; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof Sidecars value)) return false;
            return Objects.equals(blockTicks,value.blockTicks) && Objects.equals(fluidTicks,value.fluidTicks) && Objects.equals(loot,value.loot) && Objects.equals(spawners,value.spawners) && Objects.equals(owners,value.owners) && Objects.equals(archaeology,value.archaeology) && Objects.equals(bees,value.bees) && Objects.equals(blockEntities,value.blockEntities) && Objects.equals(entities,value.entities) && Objects.equals(containerLootDeclarations,value.containerLootDeclarations);
        }
        @Override public int hashCode() { return Objects.hash(blockTicks,fluidTicks,loot,spawners,owners,archaeology,bees,blockEntities,entities,containerLootDeclarations); }
    }
    public static final class ContainerLootDeclaration {
        private final int ordinal;
        private final ContainerLootSourceSection sourceSection;
        private final int sourceSectionOrdinal;
        private final int containerSize;
        private final ProductionContext productionContext;
        private final String producerSourceSha256;
        private final String sourceDeclarationSha256;
        public ContainerLootDeclaration(int ordinal, ContainerLootSourceSection sourceSection, int sourceSectionOrdinal, int containerSize, ProductionContext productionContext, String producerSourceSha256, String sourceDeclarationSha256) {
            nonnegative(ordinal); nonnegative(sourceSectionOrdinal); if(containerSize<1 || containerSize>256) throw new IllegalArgumentException("container size outside 1..256"); sha(producerSourceSha256); sha(sourceDeclarationSha256);
            this.ordinal = ordinal;
            this.sourceSection = Objects.requireNonNull(sourceSection);
            this.sourceSectionOrdinal = sourceSectionOrdinal;
            this.containerSize = containerSize;
            this.productionContext = Objects.requireNonNull(productionContext);
            this.producerSourceSha256 = Objects.requireNonNull(producerSourceSha256);
            this.sourceDeclarationSha256 = Objects.requireNonNull(sourceDeclarationSha256);
        }
        public int ordinal() { return ordinal; }
        public ContainerLootSourceSection sourceSection() { return sourceSection; }
        public int sourceSectionOrdinal() { return sourceSectionOrdinal; }
        public int containerSize() { return containerSize; }
        public ProductionContext productionContext() { return productionContext; }
        public String producerSourceSha256() { return producerSourceSha256; }
        public String sourceDeclarationSha256() { return sourceDeclarationSha256; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof ContainerLootDeclaration value)) return false;
            return ordinal==value.ordinal && Objects.equals(sourceSection,value.sourceSection) && sourceSectionOrdinal==value.sourceSectionOrdinal && containerSize==value.containerSize && Objects.equals(productionContext,value.productionContext) && Objects.equals(producerSourceSha256,value.producerSourceSha256) && Objects.equals(sourceDeclarationSha256,value.sourceDeclarationSha256);
        }
        @Override public int hashCode() { return Objects.hash(ordinal,sourceSection,sourceSectionOrdinal,containerSize,productionContext,producerSourceSha256,sourceDeclarationSha256); }
    }
    public static final class ProductionContext {
        private final String biomeKey;
        private final String worldIdentity;
        private final String sourceIdentity;
        private final String tableIdentity;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final String catalogReceipt;
        private final List<TargetMap> targets;
        private final List<LegacyMap> legacyMaps;
        private final boolean located;
        public ProductionContext(String biomeKey, String worldIdentity, String sourceIdentity, String tableIdentity, int originX, int originY, int originZ, String catalogReceipt, List<TargetMap> targets, List<LegacyMap> legacyMaps, boolean located) {
            this.biomeKey = Objects.requireNonNull(biomeKey);
            this.worldIdentity = Objects.requireNonNull(worldIdentity);
            this.sourceIdentity = Objects.requireNonNull(sourceIdentity);
            this.tableIdentity = Objects.requireNonNull(tableIdentity);
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.catalogReceipt = Objects.requireNonNull(catalogReceipt);
            this.targets = List.copyOf(targets);
            this.legacyMaps = List.copyOf(legacyMaps);
            this.located = located;
        }
        public String biomeKey() { return biomeKey; }
        public String worldIdentity() { return worldIdentity; }
        public String sourceIdentity() { return sourceIdentity; }
        public String tableIdentity() { return tableIdentity; }
        public int originX() { return originX; }
        public int originY() { return originY; }
        public int originZ() { return originZ; }
        public String catalogReceipt() { return catalogReceipt; }
        public List<TargetMap> targets() { return targets; }
        public List<LegacyMap> legacyMaps() { return legacyMaps; }
        public boolean located() { return located; }
        public Map<String, TargetMap> maps() {
            Map<String,TargetMap> result=new LinkedHashMap<>();
            for(TargetMap target:targets) if(result.put(target.key(),target)!=null)throw new IllegalArgumentException("duplicate map target");
            return Collections.unmodifiableMap(result);
        }
        public void requireMatches(long seed, String table, int x, int y, int z) {
            if(!worldIdentity.equals(Long.toString(seed)) || !tableIdentity.equals(table) || originX!=x || originY!=y || originZ!=z) throw new IllegalArgumentException("production context request mismatch");
        }

        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof ProductionContext value)) return false;
            return Objects.equals(biomeKey,value.biomeKey) && Objects.equals(worldIdentity,value.worldIdentity) && Objects.equals(sourceIdentity,value.sourceIdentity) && Objects.equals(tableIdentity,value.tableIdentity) && originX==value.originX && originY==value.originY && originZ==value.originZ && Objects.equals(catalogReceipt,value.catalogReceipt) && Objects.equals(targets,value.targets) && Objects.equals(legacyMaps,value.legacyMaps) && located==value.located;
        }
        @Override public int hashCode() { return Objects.hash(biomeKey,worldIdentity,sourceIdentity,tableIdentity,originX,originY,originZ,catalogReceipt,targets,legacyMaps,located); }
    }
    public static final class MapBinding {
        private final String destination;
        private final String destinationTag;
        private final String structureSet;
        private final List<String> acceptedMembers;
        private final String worldIdentity;
        private final String sourceIdentity;
        private final String tableIdentity;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int scale;
        private final int searchRadius;
        private final boolean skipExistingChunks;
        private final String locatorSourceReceipt;
        private final String referenceSnapshotReceipt;
        public MapBinding(String destination, String destinationTag, String structureSet, List<String> acceptedMembers, String worldIdentity, String sourceIdentity, String tableIdentity, int originX, int originY, int originZ, int scale, int searchRadius, boolean skipExistingChunks, String locatorSourceReceipt, String referenceSnapshotReceipt) {
            this.destination = Objects.requireNonNull(destination);
            this.destinationTag = Objects.requireNonNull(destinationTag);
            this.structureSet = Objects.requireNonNull(structureSet);
            this.acceptedMembers = List.copyOf(acceptedMembers);
            this.worldIdentity = Objects.requireNonNull(worldIdentity);
            this.sourceIdentity = Objects.requireNonNull(sourceIdentity);
            this.tableIdentity = Objects.requireNonNull(tableIdentity);
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.scale = scale;
            this.searchRadius = searchRadius;
            this.skipExistingChunks = skipExistingChunks;
            this.locatorSourceReceipt = Objects.requireNonNull(locatorSourceReceipt);
            this.referenceSnapshotReceipt = Objects.requireNonNull(referenceSnapshotReceipt);
        }
        public String destination() { return destination; }
        public String destinationTag() { return destinationTag; }
        public String structureSet() { return structureSet; }
        public List<String> acceptedMembers() { return acceptedMembers; }
        public String worldIdentity() { return worldIdentity; }
        public String sourceIdentity() { return sourceIdentity; }
        public String tableIdentity() { return tableIdentity; }
        public int originX() { return originX; }
        public int originY() { return originY; }
        public int originZ() { return originZ; }
        public int scale() { return scale; }
        public int searchRadius() { return searchRadius; }
        public boolean skipExistingChunks() { return skipExistingChunks; }
        public String locatorSourceReceipt() { return locatorSourceReceipt; }
        public String referenceSnapshotReceipt() { return referenceSnapshotReceipt; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof MapBinding value)) return false;
            return Objects.equals(destination,value.destination) && Objects.equals(destinationTag,value.destinationTag) && Objects.equals(structureSet,value.structureSet) && Objects.equals(acceptedMembers,value.acceptedMembers) && Objects.equals(worldIdentity,value.worldIdentity) && Objects.equals(sourceIdentity,value.sourceIdentity) && Objects.equals(tableIdentity,value.tableIdentity) && originX==value.originX && originY==value.originY && originZ==value.originZ && scale==value.scale && searchRadius==value.searchRadius && skipExistingChunks==value.skipExistingChunks && Objects.equals(locatorSourceReceipt,value.locatorSourceReceipt) && Objects.equals(referenceSnapshotReceipt,value.referenceSnapshotReceipt);
        }
        @Override public int hashCode() { return Objects.hash(destination,destinationTag,structureSet,acceptedMembers,worldIdentity,sourceIdentity,tableIdentity,originX,originY,originZ,scale,searchRadius,skipExistingChunks,locatorSourceReceipt,referenceSnapshotReceipt); }
    }
    public static final class FoundTarget {
        private final int targetX;
        private final int targetZ;
        private final int savedCenterX;
        private final int savedCenterZ;
        private final String previewSha256;
        public FoundTarget(int targetX, int targetZ, int savedCenterX, int savedCenterZ, String previewSha256) {
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.savedCenterX = savedCenterX;
            this.savedCenterZ = savedCenterZ;
            this.previewSha256 = Objects.requireNonNull(previewSha256);
        }
        public int targetX() { return targetX; }
        public int targetZ() { return targetZ; }
        public int savedCenterX() { return savedCenterX; }
        public int savedCenterZ() { return savedCenterZ; }
        public String previewSha256() { return previewSha256; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof FoundTarget value)) return false;
            return targetX==value.targetX && targetZ==value.targetZ && savedCenterX==value.savedCenterX && savedCenterZ==value.savedCenterZ && Objects.equals(previewSha256,value.previewSha256);
        }
        @Override public int hashCode() { return Objects.hash(targetX,targetZ,savedCenterX,savedCenterZ,previewSha256); }
    }
    public static final class TargetMap {
        private final String key;
        private final MapBinding binding;
        private final String targetReceipt;
        private final FoundTarget found;
        public TargetMap(String key, MapBinding binding, String targetReceipt, FoundTarget found) {
            this.key = Objects.requireNonNull(key);
            this.binding = Objects.requireNonNull(binding);
            this.targetReceipt = Objects.requireNonNull(targetReceipt);
            this.found = found;
        }
        public String key() { return key; }
        public MapBinding binding() { return binding; }
        public String targetReceipt() { return targetReceipt; }
        public FoundTarget found() { return found; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof TargetMap value)) return false;
            return Objects.equals(key,value.key) && Objects.equals(binding,value.binding) && Objects.equals(targetReceipt,value.targetReceipt) && Objects.equals(found,value.found);
        }
        @Override public int hashCode() { return Objects.hash(key,binding,targetReceipt,found); }
    }
    public static final class LegacyMap {
        private final String key;
        private final String destination;
        private final String worldIdentity;
        private final String sourceIdentity;
        private final String tableIdentity;
        private final int mapId;
        private final int centerX;
        private final int centerZ;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int scale;
        private final String resolverCatalogReceipt;
        public LegacyMap(String key, String destination, String worldIdentity, String sourceIdentity, String tableIdentity, int mapId, int centerX, int centerZ, int originX, int originY, int originZ, int scale, String resolverCatalogReceipt) {
            this.key = Objects.requireNonNull(key);
            this.destination = Objects.requireNonNull(destination);
            this.worldIdentity = Objects.requireNonNull(worldIdentity);
            this.sourceIdentity = Objects.requireNonNull(sourceIdentity);
            this.tableIdentity = Objects.requireNonNull(tableIdentity);
            this.mapId = mapId;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.scale = scale;
            this.resolverCatalogReceipt = Objects.requireNonNull(resolverCatalogReceipt);
        }
        public String key() { return key; }
        public String destination() { return destination; }
        public String worldIdentity() { return worldIdentity; }
        public String sourceIdentity() { return sourceIdentity; }
        public String tableIdentity() { return tableIdentity; }
        public int mapId() { return mapId; }
        public int centerX() { return centerX; }
        public int centerZ() { return centerZ; }
        public int originX() { return originX; }
        public int originY() { return originY; }
        public int originZ() { return originZ; }
        public int scale() { return scale; }
        public String resolverCatalogReceipt() { return resolverCatalogReceipt; }
        @Override public boolean equals(Object other) {
            if (this==other) return true;
            if (!(other instanceof LegacyMap value)) return false;
            return Objects.equals(key,value.key) && Objects.equals(destination,value.destination) && Objects.equals(worldIdentity,value.worldIdentity) && Objects.equals(sourceIdentity,value.sourceIdentity) && Objects.equals(tableIdentity,value.tableIdentity) && mapId==value.mapId && centerX==value.centerX && centerZ==value.centerZ && originX==value.originX && originY==value.originY && originZ==value.originZ && scale==value.scale && Objects.equals(resolverCatalogReceipt,value.resolverCatalogReceipt);
        }
        @Override public int hashCode() { return Objects.hash(key,destination,worldIdentity,sourceIdentity,tableIdentity,mapId,centerX,centerZ,originX,originY,originZ,scale,resolverCatalogReceipt); }
    }
    private static void position(int packed) { if(packed<0 || packed>=98304) throw new IllegalArgumentException("packed position outside chunk"); }
    private static void unsigned16(int value) { if(value<0 || value>65535) throw new IllegalArgumentException("block ID outside unsigned16"); }
    private static void nonnegative(int value) { if(value<0) throw new IllegalArgumentException("negative sidecar value"); }
    private static void payload(byte[] value) { if(Objects.requireNonNull(value).length>1048576) throw new IllegalArgumentException("canonical payload too large"); }
    private static void sha(String value) { if(!Objects.requireNonNull(value).matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid SHA256"); }
    private static void key(String value) {
        Objects.requireNonNull(value);
        int colon=value.indexOf(':');
        if(colon<=0 || colon==value.length()-1 || value.indexOf(':',colon+1)>=0) throw new IllegalArgumentException("invalid canonical key");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(i==colon) continue;
            boolean alnum=c>='a'&&c<='z'||c>='0'&&c<='9';
            if(c>127 || (!alnum && c!='_' && c!='-' && c!='.' && (i<colon || c!='/'))) throw new IllegalArgumentException("invalid canonical key");
        }
    }

    /**
     * Palette-packed copy of the carrier's block IDs. A resident chunk already keeps a plain short[] for
     * the runtime; this retained projection is read rarely, so it holds a few bits per cell instead of
     * another 196KB array.
     */
    private static final class PackedBlockIds {
        private final int length;
        private final short[] palette;
        private final int bits;
        private final long[] words;
        private final int hash;

        private PackedBlockIds(int length, short[] palette, int bits, long[] words, int hash) {
            this.length = length;
            this.palette = palette;
            this.bits = bits;
            this.words = words;
            this.hash = hash;
        }

        /** Per-thread block ID -> palette slot + 1; pack() resets the entries it used before returning. */
        private static final ThreadLocal<int[]> SLOT_OF = ThreadLocal.withInitial(() -> new int[65536]);

        static PackedBlockIds pack(short[] ids) {
            int[] slotOf = SLOT_OF.get();
            try {
                return pack(ids, slotOf);
            } finally {
                Arrays.fill(slotOf, 0);
            }
        }

        private static PackedBlockIds pack(short[] ids, int[] slotOf) {
            short[] palette = new short[16];
            int size = 0;
            for (short id : ids) {
                int key = Short.toUnsignedInt(id);
                if (slotOf[key] != 0) continue;
                if (size == palette.length) palette = Arrays.copyOf(palette, size * 2);
                palette[size++] = id;
                slotOf[key] = size;
            }
            palette = Arrays.copyOf(palette, size);
            int bits = size <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(size - 1);
            long[] words = new long[bits == 0 ? 0 : (int) (((long) ids.length * bits + 63) >>> 6)];
            if (bits != 0) {
                long mask = (1L << bits) - 1;
                for (int index = 0; index < ids.length; index++) {
                    long value = (slotOf[Short.toUnsignedInt(ids[index])] - 1) & mask;
                    long bit = (long) index * bits;
                    int word = (int) (bit >>> 6);
                    int offset = (int) (bit & 63);
                    words[word] |= value << offset;
                    if (offset + bits > 64) words[word + 1] |= value >>> (64 - offset);
                }
            }
            return new PackedBlockIds(ids.length, palette, bits, words, Arrays.hashCode(ids));
        }

        short get(int index) {
            if (index < 0 || index >= length) throw new ArrayIndexOutOfBoundsException(index);
            if (bits == 0) return palette[0];
            long bit = (long) index * bits;
            int word = (int) (bit >>> 6);
            int offset = (int) (bit & 63);
            long value = words[word] >>> offset;
            if (offset + bits > 64) value |= words[word + 1] << (64 - offset);
            return palette[(int) (value & ((1L << bits) - 1))];
        }

        short[] unpack() {
            short[] ids = new short[length];
            for (int index = 0; index < length; index++) ids[index] = get(index);
            return ids;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PackedBlockIds value)) return false;
            return length == value.length && hash == value.hash && Arrays.equals(unpack(), value.unpack());
        }

        @Override public int hashCode() { return hash; }
    }
}
