package com.skyeshade.astruct.worldgen;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.skyeshade.astruct.Astruct;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
public final class StructureDefReload extends SimpleJsonResourceReloadListener {
    public static final String FOLDER = "worldgen/async_structure";

    public StructureDefReload() { super(new GsonBuilder().create(), FOLDER); }
    private static boolean basicValidate(ResourceLocation fileId, StructureDef def) {
        boolean ok = true;
        if (def.spacing() <= 0) { Astruct.LOGGER.error("[Astruct] {} spacing must be > 0", def.id()); ok = false; }
        if (def.budgets().maxSteps() < 1) {
            Astruct.LOGGER.error("[Astruct] {} budgets.max_steps must be >= 1", def.id()); ok = false;
        }
        if (def.planHorizonChunks() < 1) {
            Astruct.LOGGER.error("[Astruct] {} plan_horizon_chunks must be >= 1", def.id()); ok = false;
        }
        return ok;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager manager, ProfilerFiller profiler) {
        var fresh = new HashMap<ResourceLocation, StructureDef>();
        int errors = 0;

        for (var e : jsons.entrySet()) {
            var fileId = e.getKey();
            var def = StructureDef.CODEC.parse(JsonOps.INSTANCE, e.getValue())
                    .resultOrPartial(msg -> Astruct.LOGGER.error("[Astruct] Parse error {}: {}", fileId, msg))
                    .orElse(null);
            if (def == null) { errors++; continue; }

            if (!def.id().equals(fileId)) {
                Astruct.LOGGER.warn("[Astruct] def.id()={} != file id {}. Using def.id().", def.id(), fileId);
            }

            if (!basicValidate(fileId, def)) { errors++; continue; }

            if (fresh.put(def.id(), def) != null) {
                Astruct.LOGGER.warn("[Astruct] Duplicate def id {}; last one wins.", def.id());
            }

            fresh.put(def.id(), def);
        }

        AstructDefs.INSTANCE.replaceAll(fresh);
        Astruct.LOGGER.info("[Astruct] Loaded {} structure defs ({} errors).", fresh.size(), errors);
    }
}
