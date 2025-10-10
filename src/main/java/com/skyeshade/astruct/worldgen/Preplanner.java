package com.skyeshade.astruct.worldgen;

import com.mojang.logging.LogUtils;
import com.skyeshade.astruct.ALog;
import com.skyeshade.astruct.Astruct;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
@EventBusSubscriber(modid = Astruct.MODID)
public final class Preplanner {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        var server = e.getServer();


        for (StructureDef def : AstructDefs.INSTANCE.all()) {
            ServerLevel level = server.getLevel(def.dimension());
            if (level == null) {
                ALog.debug("[Astruct/Preplanner] skip {}; dimension {} not loaded", def.id(), def.dimension().location());
                continue;
            }

            int min = level.getMinBuildHeight();
            int y = switch (def.genY().mode()) {
                case "fixed"    -> def.genY().value();
                case "world_y"  -> Math.max(min, level.getSeaLevel());
                case "min_plus" -> Math.max(min + def.genY().value(), min);
                default         -> Math.max(min + 122, min);
            };

            BlockPos spawn = level.getSharedSpawnPos();
            int radius = graceRadiusBlocks(def);

            AstructWorldData.get(level).ensureCentersAround(level, def, spawn, radius, y);

            ALog.debug("[Astruct/Preplanner] Seeded centers for {} (dim={}, spacing={}, y={})",
                    def.id(), def.dimension().location(), def.spacing(), y);
        }


    }

    static int graceRadiusBlocks(StructureDef def) {
        int h = Math.max(4, def.planHorizonChunks()) * 16;
        int r = Math.max(4, def.softRadiusChunks()) * 16;
        return h + r + 128;
    }
}
