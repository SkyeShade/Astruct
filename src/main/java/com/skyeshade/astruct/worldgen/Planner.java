package com.skyeshade.astruct.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Planner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    private final StructureDef def;
    private final Holder<StructureTemplatePool> startPool; // resolved on main thread
    private final String target;                            // connector target string
    private final int cx, cz;

    public Planner(ServerLevel level, StructureDef def, int cx, int cz) {
        this.level = level;
        this.def   = def;
        this.cx    = cx;
        this.cz    = cz;

        // Resolve start pool from registry (MAIN thread)
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        this.startPool = pools.getHolder(ResourceKey.create(Registries.TEMPLATE_POOL, def.startPool()))
                .orElseThrow(() -> new IllegalStateException("Missing template pool: " + def.startPool()));
        this.target = def.connectorTarget();
    }

    private static @Nullable ResourceLocation templateIdFromElement(
            net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement el
    ) {
        var enc = net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement
                .CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, el)
                .result();
        if (enc.isPresent() && enc.get() instanceof net.minecraft.nbt.CompoundTag ct) {
            if (ct.contains("element_type", net.minecraft.nbt.Tag.TAG_STRING)) {
                // Only handle single_pool_element; skip features/empty/etc.
                String type = ct.getString("element_type");
                if (!"minecraft:single_pool_element".equals(type)) return null;
            }
            if (ct.contains("location", net.minecraft.nbt.Tag.TAG_STRING)) {
                String loc = ct.getString("location");
                if (!loc.isEmpty()) return ResourceLocation.parse(loc);
            }
        }
        return null;
    }

    /** Start the async expansion + main-thread enqueue. */
    public void startAsync() {
        AstructWorldData.get(level).setPlanningCell(def.id(), cx, cz, true);

        LOGGER.info("[Astruct/Planner] started async plan for {} cell[{},{}] (spacing={})",
                def.id(), cx, cz, def.spacing());

        CompletableFuture
                .supplyAsync(this::doExpandOffThread)                          // EXPAND off-thread
                .thenAcceptAsync(this::finishOnMainThread, level.getServer())  // ENQUEUE on main
                .exceptionally(ex -> {
                    AstructWorldData.get(level).setPlanningCell(def.id(), cx, cz, false);
                    LOGGER.error("[Astruct/Planner] planning failed for def={} cell[{},{}]", def.id(), cx, cz, ex);
                    return null;
                });
    }


    /** Gen-Y resolver matching your JSON modes. */
    private int resolveGenY() {
        var g = def.genY();
        String mode = g == null || g.mode() == null ? "min_plus" : g.mode();
        int min = level.getMinBuildHeight();

        return switch (mode) {
            case "fixed"   -> g.value();
            case "world_y" -> Math.max(min, level.getSeaLevel());
            case "min_plus" -> Math.max(min + g.value(), min);
            default        -> Math.max(min + g.value(), min);
        };
    }


    private record ExpandResult(List<PoolElementStructurePiece> pieces, BlockPos center) {}

    /** Off-thread: compute center + run jigsaw expansion. No SavedData writes here. */
    private ExpandResult doExpandOffThread() {
        final long t0 = System.nanoTime();

        int y       = resolveGenY();
        int spacing = def.spacing();


        BlockPos center = CenterLocator.centerForCell(
                level.dimension(), level.getSeed(), spacing, cx, cz, y, def.id());

        int depth   = Mth.clamp(def.budgets().maxSteps(), 1, 32);
        int maxDist = def.softRadiusChunks() > 0 ? def.softRadiusChunks() * 16 : 128;
        maxDist     = Mth.clamp(maxDist, 64, 256);

        LOGGER.info("[Astruct/Planner] expand: def={} cell[{},{}] center={} depth={} maxDist={} target={} startPool={}",
                def.id(), cx, cz, center, depth, maxDist, def.connectorTarget(), def.startPool());

        List<PoolElementStructurePiece> pieces;
        try {
            pieces = JigsawExpand.expandFrom(level, startPool, def.connectorTarget(), center, depth, maxDist);
        } catch (Throwable t) {
            LOGGER.error("[Astruct/Planner] expandFrom threw for def={} cell[{},{}]: {}", def.id(), cx, cz, t.toString(), t);
            throw t; // handled by exceptionally(...)
        }

        long ms = (System.nanoTime() - t0) / 1_000_000L;
        LOGGER.info("[Astruct/Planner] expand: def={} cell[{},{}] pieces={} in {} ms",
                def.id(), cx, cz, pieces.size(), ms);

        return new ExpandResult(pieces, center);
    }






    /** Main-thread: persist center, enqueue placement steps, wake any touching loaded chunks. */
    private void finishOnMainThread(ExpandResult res) {
        int spacing = def.spacing();

        LOGGER.info("[Astruct/Planner] finish: def={} cell[{},{}] pieces={} center={}",
                def.id(), cx, cz, res.pieces().size(), res.center());


        AstructWorldData.get(level).ensureCenterForCell(level, def.id(), spacing, cx, cz, res.center().getY());

        PlacerData pd = PlacerData.get(level);
        int enq = 0;

        for (var p : res.pieces()) {
            var tplId = templateIdFromElement(p.getElement());
            if (tplId == null) {
                LOGGER.warn("[Astruct/Planner] piece had no templateId; skipping.");
                continue;
            }

            var bb   = p.getBoundingBox();
            var cmin = new ChunkPos(bb.minX() >> 4, bb.minZ() >> 4);
            var cmax = new ChunkPos(bb.maxX() >> 4, bb.maxZ() >> 4);

            Set<Long> req = new java.util.HashSet<>();
            for (int x = cmin.x; x <= cmax.x; x++)
                for (int z = cmin.z; z <= cmax.z; z++)
                    req.add(ChunkPos.asLong(x, z));

            var id   = UUID.randomUUID();
            var step = new PlacementStep(tplId, p.getPosition(), req, p.getRotation(), Mirror.NONE);
            pd.add(id, step);
            enq++;

            // Wake immediately if any required chunk is already loaded
            boolean touchesLoaded = step.requiredChunks().stream()
                    .anyMatch(l -> level.hasChunk(ChunkPos.getX(l), ChunkPos.getZ(l)));
            if (touchesLoaded) {
                LOGGER.info("[Astruct/Planner] wake placement now (touches loaded) tpl={} origin={}", tplId, p.getPosition());
                PlacerEvents.requestPlacementNow(level, id, step);
            }
        }

        LOGGER.info("[Astruct/Planner] finish: def={} cell[{},{}] enqueued={} (touchesLoaded may have fired)",
                def.id(), cx, cz, enq);

        AstructWorldData wd = AstructWorldData.get(level);
        wd.setPlanningCell(def.id(), cx, cz, false);
        wd.setPlannedCell(def.id(), cx, cz, enq > 0);

        var pdata = StructurePlanData.get(level);
        int horizon  = Math.max(4, def.planHorizonChunks());
        int maxSteps = Math.max(64, def.budgets().maxSteps());
        pdata.put(def.id(), new StructurePlanData.PlanSummary(horizon, Math.min(maxSteps, res.pieces().size())));
    }


}
