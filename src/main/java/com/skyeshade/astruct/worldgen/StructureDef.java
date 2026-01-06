package com.skyeshade.astruct.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyeshade.astruct.ALog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

public record StructureDef(
        ResourceLocation id,
        ResourceKey<Level> dimension,
        ResourceLocation startPool,
        ResourceLocation fallbackPool,
        int softRadiusChunks,
        int planHorizonChunks,
        Budgets budgets,
        GenY genY,
        int spacing,
        List<ResourceLocation> biomes
) {
    public static final Codec<HolderSet<Biome>> BIOMES_CODEC =
            RegistryCodecs.homogeneousList(Registries.BIOME);
    public record Budgets(int maxSteps, int maxOpenConnectors) {
        public static final Codec<Budgets> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.INT.fieldOf("max_steps").forGetter(Budgets::maxSteps),
                Codec.INT.fieldOf("max_open_connectors").forGetter(Budgets::maxOpenConnectors)
        ).apply(b, Budgets::new));
    }



    public static final Codec<StructureDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(StructureDef::id),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(StructureDef::dimension),
            ResourceLocation.CODEC.fieldOf("start_pool").forGetter(StructureDef::startPool),
            ResourceLocation.CODEC.fieldOf("fallback_pool").forGetter(StructureDef::fallbackPool),
            Codec.INT.fieldOf("soft_radius_chunks").forGetter(StructureDef::softRadiusChunks),
            Codec.INT.fieldOf("plan_horizon_chunks").forGetter(StructureDef::planHorizonChunks),
            Budgets.CODEC.fieldOf("budgets").forGetter(StructureDef::budgets),
            GenY.CODEC.optionalFieldOf("gen_y", new GenY(GenY.MIN_PLUS, 0)).forGetter(StructureDef::genY),
            Codec.INT.fieldOf("spacing").forGetter(StructureDef::spacing),
            Codec.list(ResourceLocation.CODEC)
                    .optionalFieldOf("biomes", List.of())
                    .forGetter(StructureDef::biomes)



    ).apply(i, StructureDef::new));
    public record GenY(String mode, int value) {
        public static final Codec<GenY> CODEC =
                RecordCodecBuilder.create(b -> b.group(
                        Codec.STRING.fieldOf("mode").forGetter(GenY::mode),
                        Codec.INT.optionalFieldOf("value", 0).forGetter(GenY::value)
                ).apply(b, GenY::new));

        public static final String FIXED    = "fixed";
        public static final String MIN_PLUS = "min_plus";
        public static final String WORLD_Y  = "world_y";
        public static final String SURFACE  = "surface";

        /**
         * NOTE: SURFACE mode reads world state and must be called on the main thread.
         */
        public static int resolveGenY(ServerLevel level,
                                      StructureDef def,
                                      int x,
                                      int z) {

            GenY g = def.genY() != null
                    ? def.genY()
                    : new GenY(MIN_PLUS, 122);

            int min = level.getMinBuildHeight();

            return switch (g.mode()) {
                case FIXED    -> g.value();
                case WORLD_Y  -> Math.max(min, level.getSeaLevel());
                case MIN_PLUS -> Math.max(min + g.value(), min);
                case SURFACE  -> Math.max(getSurfaceBlockY(level, x, z) + g.value(), min);
                default -> {
                    ALog.warn("[Astruct] Unknown gen_y mode '{}'", g.mode());
                    yield Math.max(min + g.value(), min);
                }
            };
        }

        public static int getSurfaceBlockY(ServerLevel level, int x, int z) {
            int maxY = level.getMaxBuildHeight() - 1;
            int minY = level.getMinBuildHeight();

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);

            for (int y = maxY; y > minY; y--) {
                pos.setY(y);
                if (!level.getBlockState(pos).isAir()) {
                    return y + 1;
                }
            }
            return minY;
        }
    }




}
