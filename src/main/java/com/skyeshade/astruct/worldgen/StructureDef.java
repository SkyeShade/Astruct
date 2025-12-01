package com.skyeshade.astruct.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;
import java.util.List;

public record StructureDef(
        ResourceLocation id,
        ResourceKey<Level> dimension,
        HolderSet<Biome> biomes,
        ResourceLocation startPool,
        ResourceLocation fallbackPool,
        int softRadiusChunks,
        int planHorizonChunks,
        Budgets budgets,
        GenY genY,
        int spacing
) {
    public record Budgets(int maxSteps, int maxOpenConnectors) {
        public static final Codec<Budgets> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.INT.fieldOf("max_steps").forGetter(Budgets::maxSteps),
                Codec.INT.fieldOf("max_open_connectors").forGetter(Budgets::maxOpenConnectors)
        ).apply(b, Budgets::new));
    }

    public record GenY(String mode, int value) {
        public static final String FIXED = "fixed";
        public static final String MIN_PLUS = "min_plus";
        public static final String WORLD_Y = "world_y";
        public static final String SURFACE = "surface";

        public static final List<MapColor> SURFACE_MATERIALS = Arrays.asList(MapColor.WATER, MapColor.ICE);

        public static int getSurfaceBlockY(ServerLevel serverLevel, int x, int z) {
            int height = serverLevel.getHeight();
            int minY = serverLevel.getMinBuildHeight();
            BlockPos pos = new BlockPos(x, height, z);
            for (int y = height; y > minY; y--) {
                BlockState blockState = serverLevel.getBlockState(pos);
                MapColor mapColor = blockState.getMapColor(serverLevel, pos);
                if (blockState.getLightBlock(serverLevel, pos) >= 15 || SURFACE_MATERIALS.contains(mapColor)) {
                    return pos.above().getY();
                }
                pos = pos.below();
            }

            return height - 1;
        }

        public static int resolveGenY(ServerLevel level, StructureDef def) {
            var g = def.genY();
            int min = level.getMinBuildHeight();
            return switch (g.mode()) {
                case StructureDef.GenY.FIXED    -> g.value();
                case StructureDef.GenY.WORLD_Y  -> Math.max(min, level.getSeaLevel());
                case StructureDef.GenY.MIN_PLUS -> Math.max(min + g.value(), min);
                default         -> Math.max(min + 122, min);
            };
        }

        public static final Codec<GenY> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.STRING.fieldOf("mode").forGetter(GenY::mode),
                Codec.INT.optionalFieldOf("value", 0).forGetter(GenY::value)
        ).apply(b, GenY::new));
    }

    public static final Codec<StructureDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(StructureDef::id),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(StructureDef::dimension),
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(StructureDef::biomes),
            ResourceLocation.CODEC.fieldOf("start_pool").forGetter(StructureDef::startPool),
            ResourceLocation.CODEC.fieldOf("fallback_pool").forGetter(StructureDef::fallbackPool),
            Codec.INT.fieldOf("soft_radius_chunks").forGetter(StructureDef::softRadiusChunks),
            Codec.INT.fieldOf("plan_horizon_chunks").forGetter(StructureDef::planHorizonChunks),
            Budgets.CODEC.fieldOf("budgets").forGetter(StructureDef::budgets),
            GenY.CODEC.optionalFieldOf("gen_y", new GenY("min_plus", 122)).forGetter(StructureDef::genY),
            Codec.INT.fieldOf("spacing").forGetter(StructureDef::spacing)
    ).apply(i, StructureDef::new));
}
