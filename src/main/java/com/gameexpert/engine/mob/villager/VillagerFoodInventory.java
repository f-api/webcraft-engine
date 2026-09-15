package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.inventory.PlayerInventory;

/** Eight 64-item slots, encoded in wheat/bread/carrot/potato/beetroot order. */
public final class VillagerFoodInventory {
    public static final short[] ITEMS = {PlayerInventory.WHEAT, PlayerInventory.BREAD,
            PlayerInventory.CARROT, PlayerInventory.POTATO, PlayerInventory.BEETROOT};
    private static final int[] POINTS = {0, 4, 1, 1, 1};
    private VillagerFoodInventory() {}
    public static int index(int item) {
        for (int i=0; i<ITEMS.length; i++) if (ITEMS[i] == item) return i;
        return -1;
    }
    public static int[] parse(String value) {
        if (value == null || !value.matches("\\d+,\\d+,\\d+,\\d+,\\d+"))
            throw new IllegalArgumentException("invalid villager food inventory");
        String[] parts=value.split(",");
        int[] counts=new int[5]; int slots=0;
        for (int i=0;i<5;i++) {
            counts[i]=Integer.parseInt(parts[i]);
            if (counts[i]<0 || counts[i]>512) throw new IllegalArgumentException("villager inventory overflow");
            slots+=(counts[i]+63)/64;
        }
        if (slots>8) throw new IllegalArgumentException("villager inventory overflow");
        return counts;
    }
    public static String encode(int[] counts) {
        return counts[0]+","+counts[1]+","+counts[2]+","+counts[3]+","+counts[4];
    }
    public static int points(int[] counts) {
        int total=0; for (int i=1;i<5;i++) total+=counts[i]*POINTS[i]; return total;
    }
    public static int add(int[] counts, int index, int requested) {
        if (index<0 || index>=5 || requested<=0) return 0;
        int otherSlots=0;
        for (int i=0;i<5;i++) if(i!=index) otherSlots+=(counts[i]+63)/64;
        int accepted=Math.min(requested,(8-otherSlots)*64-counts[index]);
        counts[index]+=accepted;
        return accepted;
    }
    public static void consumeBreedingFood(int[] counts) {
        int remaining=12;
        for(int i=1;i<5 && remaining>0;i++) {
            int n=Math.min(counts[i],(remaining+POINTS[i]-1)/POINTS[i]);
            counts[i]-=n; remaining-=n*POINTS[i];
        }
    }
}
