package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.List;

/**
 * [EC-MOBS] 아이템 액자(바닐라 {@code ItemFrame}, stableId 105). 수치·규칙은 {@link ItemFrameRules} 가 소유한다.
 *
 * <p>권위 몹의 {@code x,y,z} 는 바닐라 그대로 명중 상자 <b>중심</b>이고 칸 좌표·방향이 정본이다. 스스로 움직이지
 * 않고({@link #immovable()}), 체력이 없다 — 피해는 {@link #hurtFrame} 가 "넣은 아이템 떨구기" 또는 "액자째 부서짐"
 * 두 갈래로 바꾼다({@code ItemFrame.hurtServer}). 매 100 MC 틱마다 {@code survives()} 를 보고 지지를 잃으면 부서진다
 * ({@code BlockAttachedEntity.tick}). 넣은 아이템은 몹 손 칸({@link #heldItem()})에 싣고, 인챈트 워드 0 과 성분
 * 문자열(확장 인챈트 WCIC4)은 이 개체가 따로 든다.
 */
public final class ItemFrame extends Mob {
    private int blockX;
    private int blockY;
    private int blockZ;
    private int direction = ItemFrameRules.SOUTH;
    private int rotation;
    private long itemEnchantments;
    private String itemComponents;
    /** {@code BlockAttachedEntity.checkInterval}(MC 틱). */
    private int checkMcTicks;
    /** 이번 판정에서 지지를 잃었다(런타임이 부서짐·드랍으로 소비한다). */
    private boolean supportLost;

    public ItemFrame(long id, double x, double y, double z) {
        super(id, MobType.ITEM_FRAME, x, y, z);
        blockX = (int) Math.floor(x);
        blockY = (int) Math.floor(y);
        blockZ = (int) Math.floor(z);
        recenter();
    }

    /** 칸 좌표와 방향으로 건다(설치 · 엔드 배 표지 · 영속 복원). */
    public void hang(int x, int y, int z, int facing) {
        if (!ItemFrameRules.validDirection(facing)) throw new IllegalArgumentException("invalid frame direction");
        blockX = x;
        blockY = y;
        blockZ = z;
        direction = facing;
        recenter();
    }

    private void recenter() {
        this.x = ItemFrameRules.centerX(blockX, direction);
        this.y = ItemFrameRules.centerY(blockY, direction);
        this.z = ItemFrameRules.centerZ(blockZ, direction);
    }

    public int blockX() { return blockX; }
    public int blockY() { return blockY; }
    public int blockZ() { return blockZ; }
    public int direction() { return direction; }
    public int rotation() { return rotation; }
    public long itemEnchantments() { return itemEnchantments; }
    public String itemComponents() { return itemComponents; }
    public boolean hasItem() { return heldItem() != PlayerInventory.EMPTY; }

    /** 영속 복원: 칸 좌표는 상자 중심에서 되돌린다. */
    void restoreFrameState(int facing, int savedRotation, long enchantments, String components) {
        if (!ItemFrameRules.validDirection(facing) || savedRotation < 0
                || savedRotation >= ItemFrameRules.NUM_ROTATIONS) {
            throw new IllegalStateException("invalid persisted item frame state");
        }
        direction = facing;
        blockX = ItemFrameRules.blockX(x, facing);
        blockY = ItemFrameRules.blockY(y, facing);
        blockZ = ItemFrameRules.blockZ(z, facing);
        rotation = savedRotation;
        itemEnchantments = enchantments;
        itemComponents = components;
        recenter();
    }

    /** {@code ItemFrame.interact} 의 INSERT: 한 개를 넣고 회전을 0 으로 둔다. */
    public boolean insert(short itemType, int durability, long enchantments, String components) {
        if (!installFramedItem(itemType, durability)) return false;
        itemEnchantments = enchantments;
        itemComponents = components;
        rotation = 0;
        return true;
    }

    /** {@code ItemFrame.interact} 의 ROTATE: {@code setRotation(getRotation() + 1)}. */
    public void rotate() {
        rotation = ItemFrameRules.nextRotation(rotation);
    }

    /** 넣은 아이템을 꺼낸다(떨구기·부서짐). 없으면 null. */
    public FramedItem takeItem() {
        long enchantments = itemEnchantments;
        String components = itemComponents;
        EquipmentDrop drop = removeFramedItem();
        if (drop == null) return null;
        itemEnchantments = 0L;
        itemComponents = null;
        rotation = 0;
        return new FramedItem(drop.itemType(), drop.durability(), enchantments, components);
    }

    /** 꺼낸 아이템 한 개(액자 속 스택은 늘 1개다). */
    public record FramedItem(short itemType, int durability, long enchantments, String components) {
    }

    /** {@code ItemFrame.hurtServer}: 폭발이 아니고 아이템이 있으면 아이템만, 아니면 액자째. */
    public ItemFrameRules.Hurt hurtFrame(boolean explosion) {
        return ItemFrameRules.hurt(explosion, hasItem());
    }

    public boolean supportLost() { return supportLost; }

    @Override public boolean immovable() { return true; }
    @Override public boolean fireImmune() { return true; }
    @Override public boolean hasStepSound() { return false; }
    @Override public boolean hasAmbientSound() { return false; }

    @Override
    public double[] authorityAabb() {
        return ItemFrameRules.boundingBox(blockX, blockY, blockZ, direction);
    }

    @Override
    public int visualFlags() {
        return ItemFrameRules.visualFlags(direction, rotation);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        // BlockAttachedEntity.tick: checkInterval++ >= 100 이면 0 으로 되감고 survives() 를 본다. 한 권위 틱 = 2 MC 틱.
        for (int step = 0; step < 2 && !supportLost; step++) {
            if (checkMcTicks++ >= ItemFrameRules.CHECK_INTERVAL_MC_TICKS) {
                checkMcTicks = 0;
                if (!survives(world)) supportLost = true;
            }
        }
        return List.of();
    }

    /** {@code ItemFrame.survives()}. 다른 액자 목록은 런타임이 {@link #prepareFrames} 로 준다. */
    public boolean survives(MobWorldView world) {
        return ItemFrameRules.survives(new ItemFrameRules.SupportWorld() {
            @Override
            public boolean blockCollision(double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ) {
                return MobPhysics.blockCollision(world, minX, minY, minZ, maxX, maxY, maxZ);
            }

            @Override
            public boolean solid(int sx, int sy, int sz) {
                int block = world.getBlock(sx, sy, sz);
                return block >= 0 && ItemFrameSupport.solid(block & 0xffff);
            }

            @Override
            public boolean diode(int sx, int sy, int sz) {
                int block = world.getBlock(sx, sy, sz);
                return block >= 0 && ItemFrameSupport.diode(block & 0xffff);
            }
        }, id, blockX, blockY, blockZ, direction, frames);
    }

    private Iterable<ItemFrameRules.Hanging> frames = List.of();

    /** 같은 월드에 걸린 다른 액자들(런타임이 판정 전에 준다). */
    void prepareFrames(Iterable<ItemFrameRules.Hanging> hanging) {
        frames = hanging == null ? List.of() : hanging;
    }

    /** 설치 판정용(설치 전 탐침 액자). */
    public void prepareFramesForPlacement(Iterable<ItemFrameRules.Hanging> hanging) {
        prepareFrames(hanging);
    }

    public ItemFrameRules.Hanging hanging() {
        return new ItemFrameRules.Hanging(id, blockX, blockY, blockZ, direction);
    }
}
