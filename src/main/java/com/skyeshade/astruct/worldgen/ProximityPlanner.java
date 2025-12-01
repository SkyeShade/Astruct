package com.skyeshade.astruct.worldgen;

import com.skyeshade.astruct.ALog;
import com.skyeshade.astruct.Astruct;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Astruct.MODID)
public final class ProximityPlanner {

    private static final Map<ResourceLocation, Integer> COOLDOWN_TICKS = new HashMap<>();
    private static final int SCAN_INTERVAL_TICKS = 60;
    private static final int DEF_COOLDOWN_TICKS  = 60;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if(e.phase == TickEvent.Phase.START) {
            return;
        }
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

                var wd      = AstructWorldData.get(level);
                var players = level.players();
                if (players.isEmpty()) continue;

                final int spacing = def.spacing();
                final int y       = StructureDef.GenY.resolveGenY(level, def);


                final int grace = Math.max(4, def.planHorizonChunks()) * 16
                        + Math.max(4, def.softRadiusChunks())  * 16
                        + 128;

                for (var p : players) {
                    var here = p.blockPosition();


                    wd.ensureCentersAround(level, def, here, grace, y);

                    int minCx = CenterLocator.cellOf(here.getX() - grace, spacing);
                    int maxCx = CenterLocator.cellOf(here.getX() + grace, spacing);
                    int minCz = CenterLocator.cellOf(here.getZ() - grace, spacing);
                    int maxCz = CenterLocator.cellOf(here.getZ() + grace, spacing);


                    for (int cx = minCx; cx <= maxCx; cx++) {
                        for (int cz = minCz; cz <= maxCz; cz++) {
                            if (wd.isPlannedCell(def.id(), cx, cz) || wd.isPlanningCell(def.id(), cx, cz)) continue;

                            var center = CenterLocator.centerForCell(
                                    level.dimension(), level.getSeed(), spacing, cx, cz, y, def.id());

                            if (!center.closerThan(here, grace)) continue;


                            wd.setPlanningCell(def.id(), cx, cz, true);
                            StructureManager.planStructure(level, def.id(), cx, cz);

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

}
