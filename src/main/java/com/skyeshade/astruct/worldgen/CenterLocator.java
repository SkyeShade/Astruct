package com.skyeshade.astruct.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;


public final class CenterLocator {
    private CenterLocator() {}

    private static long mix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    private static double toUnitDouble(long bits) {
        return (double) (bits >>> 11) * 0x1.0p-53;
    }

    public static int centerX(int cx, int spacing) {
        return cx * spacing + spacing / 2;
    }

    public static int centerZ(int cz, int spacing) {
        return cz * spacing + spacing / 2;
    }

    public static BlockPos centerForCell(ResourceKey<Level> dim, long worldSeed,
                                         int spacing, int cx, int cz, int y,
                                         ResourceLocation structureSalt) {

        long cellKey = (((long) cx) << 32) ^ (cz & 0xffffffffL);


        long base = worldSeed
                ^ dim.location().hashCode()
                ^ structureSalt.hashCode()
                ^ cellKey;


        long hx = mix64(base ^ 0xA5A5A5A5A5A5A5A5L);
        long hz = mix64(base ^ 0x5A5A5A5A5A5A5A5AL);

        int margin = Math.max(16, spacing / 8);
        int range  = Math.max(1, spacing - 2 * margin);

        int ox = (int) Math.floor(toUnitDouble(hx) * range);
        int oz = (int) Math.floor(toUnitDouble(hz) * range);

        int x = cx * spacing + margin + ox;
        int z = cz * spacing + margin + oz;
        return new BlockPos(x, y, z);
    }

    public static int cellOf(int coord, int spacing) {
        return Math.floorDiv(coord, spacing);
    }
}
