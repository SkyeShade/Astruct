
package com.skyeshade.astruct.worldgen;

import com.skyeshade.astruct.ALog;
import com.skyeshade.astruct.Astruct;

import com.skyeshade.astruct.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Mod.EventBusSubscriber(modid = Astruct.MODID)
public final class PlacerEvents {

    private static final int MAX_PLACEMENTS_PER_TICK = Config.MAX_PLACEMENTS_PER_TICK;


    private record Pending(ResourceKey<Level> dim, UUID id) {}

    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();


    private static final Set<String> ENQUEUED =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static String key(ResourceKey<Level> dim, UUID id) {
        return dim.location() + ":" + id;
    }


    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (level.getServer().getPlayerList().getPlayerCount() == 0) return;

        ChunkPos cp = e.getChunk().getPos();
        PlacerData data = PlacerData.get(level);
        var ids = data.stepsForChunk(cp.toLong());
        if (ids.isEmpty()) return;

        ALog.debug("[Placer] chunkLoad {} -> steps touching: {}", cp, ids.size());
        for (UUID id : ids) {
            if (data.isRealized(id)) continue;
            data.get(id).ifPresent(step -> {
                boolean ready = allChunksLoaded(level, step.requiredChunks());
                ALog.debug("[Placer] step={} ready={} origin={} reqChunks={}",
                        id, ready, step.origin(), step.requiredChunks().size());
                if (ready) {
                    String k = key(level.dimension(), id);
                    if (ENQUEUED.add(k)) PENDING.addLast(new Pending(level.dimension(), id));
                }
            });
        }
    }



    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        final PlacerData data = PlacerData.get(level);
        var ids = data.stepsForChunk(e.getChunk().getPos().toLong());
        if (ids.isEmpty()) return;


        for (UUID id : ids) {
            ENQUEUED.remove(key(level.dimension(), id));
        }

        if (!PENDING.isEmpty()) {
            PENDING.removeIf(p -> p.dim.equals(level.dimension()) && ids.contains(p.id));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if(e.phase == TickEvent.Phase.START) {
            return;
        }
        final int SWEEP_LIMIT = 64;
        for (ServerLevel sl : e.getServer().getAllLevels()) {

            if (sl.getServer().getPlayerList().getPlayerCount() == 0) continue;

            PlacerData pd = PlacerData.get(sl);
            int swept = 0;
            for (var entry : pd.entries()) {
                if (swept >= SWEEP_LIMIT) break;

                var id = entry.getKey();
                var step = entry.getValue();
                if (pd.isRealized(id)) continue;


                String k = key(sl.dimension(), id);
                if (ENQUEUED.contains(k)) continue;


                if (anyChunkLoaded(sl, step.requiredChunks())) {
                    if (ENQUEUED.add(k)) {
                        PENDING.addLast(new Pending(sl.dimension(), id));
                        swept++;
                    }
                }
            }
        }


        int budget = MAX_PLACEMENTS_PER_TICK;
        if (budget > 0 && !PENDING.isEmpty()) {
            ALog.debug("[Astruct/Placer] draining queue size={} (budget={})", PENDING.size(), budget);
        }
        while (budget-- > 0 && !PENDING.isEmpty()) {
            Pending p = PENDING.pollFirst();
            ENQUEUED.remove(key(p.dim, p.id));
            try {
                processOne(e.getServer(), p);
            } catch (Throwable t) {
                ALog.warn("[Astruct/Placer] placement task failed for id={} dim={}", p.id, p.dim.location(), t);
            }
        }
    }


    private static void processOne(MinecraftServer server, Pending p) {
        ServerLevel level = server.getLevel(p.dim());
        if (level == null) return;

        PlacerData data = PlacerData.get(level);
        var optStep = data.get(p.id());
        if (optStep.isEmpty()) return;
        if (data.isRealized(p.id())) return;

        var step = optStep.get();


        if (!anyChunkLoaded(level, step.requiredChunks())) {

            return;
        }

        var tm  = level.getStructureManager();
        var tpl = tm.getOrCreate(step.templateId());

        var settings = new StructurePlaceSettings()
                .setRotation(step.rotation())
                .setMirror(step.mirror())
                .addProcessor(JigsawReplacementProcessor.INSTANCE)
                .setIgnoreEntities(true);

        boolean ok = tpl.placeInWorld(
                level, step.origin(), step.origin(), settings,
                RandomSource.create(step.origin().asLong()), 2);

        if (ok) {
            data.markRealized(p.id());
        } else {

            ALog.warn("[Astruct/Placer] FAILED place step={} (dim={}); will wait for future readiness",
                    p.id(), p.dim().location());
        }
    }

    private static boolean anyChunkLoaded(ServerLevel level, Collection<Long> required) {
        var src = level.getChunkSource();
        for (long packed : required)
            if (src.hasChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed))) return true;
        return false;
    }


    private static boolean allChunksLoaded(ServerLevel level, Collection<Long> required) {
        var src = level.getChunkSource();
        for (long packed : required)
            if (!src.hasChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed))) return false;
        return true;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        PENDING.removeIf(p -> p.dim.equals(level.dimension()));
        ENQUEUED.removeIf(k -> k.startsWith(level.dimension().location().toString() + ":"));
    }


    public static void requestPlacementNow(ServerLevel level, UUID id, PlacementStep step) {
        if (!allChunksLoaded(level, step.requiredChunks())) return;
        if (PlacerData.get(level).isRealized(id)) return;
        String k = key(level.dimension(), id);
        if (ENQUEUED.add(k)) PENDING.addLast(new Pending(level.dimension(), id));
    }
}
