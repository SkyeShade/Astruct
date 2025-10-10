
package com.skyeshade.astruct.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.util.Set;


public record PlacementStep(
        ResourceLocation templateId,
        BlockPos origin,
        Set<Long> requiredChunks,
        Rotation rotation,
        Mirror mirror
) {
    public PlacementStep(ResourceLocation id, BlockPos o, Set<Long> req) {
        this(id, o, req, Rotation.NONE, Mirror.NONE);
    }

    public StructurePlaceSettings toSettings() {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
    }
}
