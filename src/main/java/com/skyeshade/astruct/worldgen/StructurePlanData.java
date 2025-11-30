package com.skyeshade.astruct.worldgen;


import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;

public final class StructurePlanData extends SavedData {
    private static final String NAME = "astruct_structure_plans";


    private final Map<ResourceLocation, PlanSummary> plans = new Object2ObjectOpenHashMap<>();

    public static StructurePlanData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                StructurePlanData::load, StructurePlanData::new, NAME);
    }

    public StructurePlanData() {}

    public static StructurePlanData load(CompoundTag tag) {
        StructurePlanData d = new StructurePlanData();
        ListTag list = tag.getList("plans", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation id = ResourceLocation.parse(e.getString("id"));
            int horizon = e.getInt("horizon");
            int steps   = e.getInt("steps");
            d.plans.put(id, new PlanSummary(horizon, steps));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var entry : plans.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", entry.getKey().toString());
            e.putInt("horizon", entry.getValue().horizonChunks());
            e.putInt("steps", entry.getValue().plannedSteps());
            list.add(e);
        }
        tag.put("plans", list);
        return tag;
    }

    public PlanSummary get(ResourceLocation id) { return plans.get(id); }
    public void put(ResourceLocation id, PlanSummary sum) { plans.put(id, sum); setDirty(); }

    public record PlanSummary(int horizonChunks, int plannedSteps) {}
}