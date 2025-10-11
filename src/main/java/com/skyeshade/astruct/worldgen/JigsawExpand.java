package com.skyeshade.astruct.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class JigsawExpand {
    private JigsawExpand() {}

    public static List<PoolElementStructurePiece> expandFrom(
            ServerLevel level,
            Holder<StructureTemplatePool> startPool,
            BlockPos startPos,
            int maxDepth,
            int maxDistanceFromCenter
    ) {

        var registries   = level.registryAccess();
        var generator    = level.getChunkSource().getGenerator();
        var biomeSource  = generator.getBiomeSource();
        var randomState  = level.getChunkSource().randomState();
        var templateMgr  = level.getStructureManager();
        long seed        = level.getSeed();
        ChunkPos chunkPos = new ChunkPos(startPos);
        LevelHeightAccessor height = level;
        Predicate<Holder<Biome>> validBiome = b -> true;

        var ctx = new Structure.GenerationContext(
                registries,
                generator,
                biomeSource,
                randomState,
                templateMgr,
                seed,
                chunkPos,
                height,
                validBiome
        );

        Optional<Structure.GenerationStub> stubOpt = JigsawPlacement.addPieces(
                ctx,
                startPool,
                Optional.empty(),
                Math.max(1, maxDepth),
                startPos,
                false,
                Optional.empty(),
                Math.max(0, maxDistanceFromCenter),
                PoolAliasLookup.EMPTY,
                DimensionPadding.ZERO,
                LiquidSettings.IGNORE_WATERLOGGING
        );

        var out = new ArrayList<PoolElementStructurePiece>();
        if (stubOpt.isEmpty()) return out;


        StructurePiecesBuilder builder = new StructurePiecesBuilder();

        stubOpt.get().generator().ifLeft(consumer -> consumer.accept(builder));


        var built = builder.build();
        for (StructurePiece p : built.pieces()) {
            if (p instanceof PoolElementStructurePiece pe) {
                out.add(pe);
            }
        }
        return out;
    }
}