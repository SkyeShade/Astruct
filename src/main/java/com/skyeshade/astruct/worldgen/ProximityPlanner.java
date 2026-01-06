package com.skyeshade.astruct.worldgen;

import com.skyeshade.astruct.ALog;
import com.skyeshade.astruct.Astruct;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = Astruct.MODID)
public final class ProximityPlanner {

    private static final Map<ResourceLocation, Integer> COOLDOWN_TICKS = new HashMap<>();
    private static final int SCAN_INTERVAL_TICKS = 60;
    private static final int DEF_COOLDOWN_TICKS  = 60;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        var server = e.getServer();
        if ((server.getTickCount() % SCAN_INTERVAL_TICKS) != 0) return;


        for (StructureDef def : AstructDefs.INSTANCE.all()) {

            int cd = COOLDOWN_TICKS.getOrDefault(def.id(), 0);
            if (cd > 0) {
                COOLDOWN_TICKS.put(def.id(), cd - SCAN_INTERVAL_TICKS);
                continue;
            }

            boolean sawTargetLevel = false;
            boolean scheduledAny   = false;

            for (var level : server.getAllLevels()) {
                if (level.dimension() != def.dimension()) continue;
                sawTargetLevel = true;

                var sl      = (ServerLevel) level;
                var wd      = AstructWorldData.get(sl);
                var players = sl.players();
                if (players.isEmpty()) continue;

                final int spacing = def.spacing();




                final int grace = Math.max(4, def.planHorizonChunks()) * 16
                        + Math.max(4, def.softRadiusChunks())  * 16
                        + 128;

                for (var p : players) {
                    var here = p.blockPosition();

                    int y = StructureDef.GenY.resolveGenY(level, def, here.getX(), here.getZ());
                    wd.ensureCentersAround(sl, def, here, grace, y);

                    int minCx = CenterLocator.cellOf(here.getX() - grace, spacing);
                    int maxCx = CenterLocator.cellOf(here.getX() + grace, spacing);
                    int minCz = CenterLocator.cellOf(here.getZ() - grace, spacing);
                    int maxCz = CenterLocator.cellOf(here.getZ() + grace, spacing);


                    for (int cx = minCx; cx <= maxCx; cx++) {
                        for (int cz = minCz; cz <= maxCz; cz++) {
                            if (wd.isPlannedCell(def.id(), cx, cz) || wd.isPlanningCell(def.id(), cx, cz) || wd.isInvalidBiomeCell(def.id(), cx, cz)) continue;

                            var center = CenterLocator.centerForCell(
                                    sl.dimension(), sl.getSeed(), spacing, cx, cz, y, def.id());

                            if (!center.closerThan(here, grace)) continue;
                            if (!def.biomes().isEmpty()) {

                                Holder<Biome> biomeAt = level.getBiome(center);

                                // Resolve biome ID
                                ResourceLocation biomeId = level.registryAccess()
                                        .registryOrThrow(Registries.BIOME)
                                        .getKey(biomeAt.value());

                                if (biomeId == null || !def.biomes().contains(biomeId)) {
                                    wd.setInvalidBiomeCell(def.id(), cx, cz, true);
                                    continue;
                                }
                            }




                            wd.setPlanningCell(def.id(), cx, cz, true);
                            StructureManager.planStructure(sl, def.id(), cx, cz);

                            ALog.debug("[Proximity] scheduled plan def={} cell[{},{}] center={} player={}",
                                    def.id(), cx, cz, center, p.getGameProfile().getName());

                            scheduledAny = true;
                        }
                    }
                }
            }


            if (scheduledAny) {
                COOLDOWN_TICKS.put(def.id(), DEF_COOLDOWN_TICKS);
            } else if (!sawTargetLevel) {
                COOLDOWN_TICKS.put(def.id(), DEF_COOLDOWN_TICKS * 4);
                ALog.debug("[Proximity] target dimension {} not loaded for def {}; skipping",
                        def.dimension().location(), def.id());
            } else {
                COOLDOWN_TICKS.put(def.id(), DEF_COOLDOWN_TICKS);
            }
        }
    }

    private static int resolveGenY(ServerLevel level, StructureDef def) {
        var g = def.genY();
        int min = level.getMinBuildHeight();
        return switch (g.mode()) {
            case "fixed"    -> g.value();
            case "world_y"  -> Math.max(min, level.getSeaLevel());
            case "min_plus" -> Math.max(min + g.value(), min);
            default         -> Math.max(min + 122, min);
        };
    }
}
