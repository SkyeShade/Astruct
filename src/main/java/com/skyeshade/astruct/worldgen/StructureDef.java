package com.skyeshade.astruct.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record StructureDef(
        ResourceLocation id,
        ResourceKey<Level> dimension,
        ResourceLocation startPool,
        String connectorTarget,
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
        public static final Codec<GenY> CODEC = RecordCodecBuilder.create(b -> b.group(
                Codec.STRING.fieldOf("mode").forGetter(GenY::mode),
                Codec.INT.optionalFieldOf("value", 0).forGetter(GenY::value)
        ).apply(b, GenY::new));
    }

    public static final Codec<StructureDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(StructureDef::id),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(StructureDef::dimension),
            ResourceLocation.CODEC.fieldOf("start_pool").forGetter(StructureDef::startPool),
            Codec.STRING.fieldOf("connector_target").forGetter(StructureDef::connectorTarget),
            ResourceLocation.CODEC.fieldOf("fallback_pool").forGetter(StructureDef::fallbackPool),
            Codec.INT.fieldOf("soft_radius_chunks").forGetter(StructureDef::softRadiusChunks),
            Codec.INT.fieldOf("plan_horizon_chunks").forGetter(StructureDef::planHorizonChunks),
            Budgets.CODEC.fieldOf("budgets").forGetter(StructureDef::budgets),
            GenY.CODEC.optionalFieldOf("gen_y", new GenY("min_plus", 122)).forGetter(StructureDef::genY),
            Codec.INT.fieldOf("spacing").forGetter(StructureDef::spacing)
    ).apply(i, StructureDef::new));
}
