package com.skyeshade.astruct.worldgen;


import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.Map;


public final class AstructWorldData extends SavedData {
    private static final String NAME = "astruct_world";

    private final Object2ObjectOpenHashMap<ResourceLocation, LongOpenHashSet> invalidBiomeCells =
            new Object2ObjectOpenHashMap<>();

    private final Object2ObjectOpenHashMap<ResourceLocation, Long2ObjectOpenHashMap<BlockPos>> centersByStructure = new Object2ObjectOpenHashMap<>();


    private final Object2ObjectOpenHashMap<ResourceLocation, LongOpenHashSet> plannedCells = new Object2ObjectOpenHashMap<>();


    private final Object2ObjectOpenHashMap<ResourceLocation, LongOpenHashSet> planningCells = new Object2ObjectOpenHashMap<>();

    private static long cellKey(int cx, int cz) { return (((long) cx) << 32) | (cz & 0xffffffffL); }

    public static AstructWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(AstructWorldData::new, (tag, prov) -> AstructWorldData.load(tag)),
                NAME
        );
    }

    public AstructWorldData() {}

    public boolean isInvalidBiomeCell(ResourceLocation structureId, int cx, int cz) {
        var set = invalidBiomeCells.get(structureId);
        return set != null && set.contains(cellKey(cx, cz));
    }

    public void setInvalidBiomeCell(ResourceLocation structureId, int cx, int cz, boolean invalid) {
        var set = invalidBiomeCells.computeIfAbsent(structureId, __ -> new LongOpenHashSet());
        long k = cellKey(cx, cz);
        if (invalid) set.add(k); else set.remove(k);
        setDirty();
    }


    public boolean isPlannedCell(ResourceLocation structureId, int cx, int cz) {
        var set = plannedCells.get(structureId);
        return set != null && set.contains(cellKey(cx, cz));
    }

    public void setPlannedCell(ResourceLocation structureId, int cx, int cz, boolean planned) {
        var set = plannedCells.computeIfAbsent(structureId, __ -> new LongOpenHashSet());
        long k = cellKey(cx, cz);
        if (planned) set.add(k); else set.remove(k);
        setDirty();
    }

    public boolean isPlanningCell(ResourceLocation structureId, int cx, int cz) {
        var set = planningCells.get(structureId);
        return set != null && set.contains(cellKey(cx, cz));
    }

    public void setPlanningCell(ResourceLocation structureId, int cx, int cz, boolean v) {
        var set = planningCells.computeIfAbsent(structureId, __ -> new LongOpenHashSet());
        long k = cellKey(cx, cz);
        if (v) set.add(k); else set.remove(k);
    }


    public BlockPos ensureCenterForCell(ServerLevel level, ResourceLocation structureId,
                                        int spacing, int cx, int cz, int y) {
        var map = centersByStructure.computeIfAbsent(structureId, __ -> new Long2ObjectOpenHashMap<>());
        long key = cellKey(cx, cz);
        BlockPos existing = map.get(key);
        if (existing != null) return existing;

        BlockPos created = CenterLocator.centerForCell(level.dimension(), level.getSeed(),
                spacing, cx, cz, y, structureId);
        map.put(key, created);
        setDirty();
        return created;
    }

    public BlockPos ensureCenterForCell(ServerLevel level, StructureDef def, int cx, int cz, int y) {
        return ensureCenterForCell(level, def.id(), def.spacing(), cx, cz, y);
    }

    public void ensureCentersAround(ServerLevel level, StructureDef def, BlockPos around, int radius, int y) {
        int spacing = def.spacing();
        int minCx = CenterLocator.cellOf(around.getX() - radius, spacing);
        int maxCx = CenterLocator.cellOf(around.getX() + radius, spacing);
        int minCz = CenterLocator.cellOf(around.getZ() - radius, spacing);
        int maxCz = CenterLocator.cellOf(around.getZ() + radius, spacing);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ensureCenterForCell(level, def.id(), spacing, cx, cz, y);
            }
        }
    }


    public static AstructWorldData load(CompoundTag tag) {
        AstructWorldData d = new AstructWorldData();

        if (tag.contains("invalidBiomeCellsByStructure", Tag.TAG_COMPOUND)) {
            CompoundTag root = tag.getCompound("invalidBiomeCellsByStructure");
            for (String structureKey : root.getAllKeys()) {
                ResourceLocation structureId = ResourceLocation.parse(structureKey);
                var set = new LongOpenHashSet(root.getLongArray(structureKey));
                d.invalidBiomeCells.put(structureId, set);
            }
        }

        if (tag.contains("centersByStructure", Tag.TAG_COMPOUND)) {
            CompoundTag root = tag.getCompound("centersByStructure");
            for (String structureKey : root.getAllKeys()) {
                ResourceLocation structureId = ResourceLocation.parse(structureKey);
                CompoundTag pack = root.getCompound(structureKey);
                Long2ObjectOpenHashMap<BlockPos> map = new Long2ObjectOpenHashMap<>();
                for (Tag t : pack.getList("list", Tag.TAG_COMPOUND)) {
                    CompoundTag e = (CompoundTag) t;
                    long cell = e.getLong("cell");
                    BlockPos pos = new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z"));
                    map.put(cell, pos);
                }
                d.centersByStructure.put(structureId, map);
            }
        } else {

            if (tag.contains("centers", Tag.TAG_LIST)) {
                ResourceLocation legacy = ResourceLocation.parse("astruct:_legacy");
                Long2ObjectOpenHashMap<BlockPos> map = new Long2ObjectOpenHashMap<>();
                for (Tag t : tag.getList("centers", Tag.TAG_COMPOUND)) {
                    CompoundTag e = (CompoundTag) t;
                    map.put(e.getLong("cell"), new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")));
                }
                d.centersByStructure.put(legacy, map);
            }
        }


        if (tag.contains("plannedCellsByStructure", Tag.TAG_COMPOUND)) {
            CompoundTag root = tag.getCompound("plannedCellsByStructure");
            for (String structureKey : root.getAllKeys()) {
                ResourceLocation structureId = ResourceLocation.parse(structureKey);
                var set = new LongOpenHashSet(root.getLongArray(structureKey));
                d.plannedCells.put(structureId, set);
            }
        } else if (tag.contains("plannedCells", Tag.TAG_LONG_ARRAY)) {
            ResourceLocation legacy = ResourceLocation.parse("astruct:_legacy");
            d.plannedCells.put(legacy, new LongOpenHashSet(tag.getLongArray("plannedCells")));
        }

        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag invalidBiomeRoot = new CompoundTag();
        invalidBiomeCells.forEach((structureId, set) ->
                invalidBiomeRoot.put(structureId.toString(), new LongArrayTag(set.toLongArray())));
        tag.put("invalidBiomeCellsByStructure", invalidBiomeRoot); // ✅

        CompoundTag centersRoot = new CompoundTag();
        centersByStructure.forEach((structureId, map) -> {
            CompoundTag pack = new CompoundTag();
            ListTag list = new ListTag();
            map.forEach((cell, pos) -> {
                CompoundTag e = new CompoundTag();
                e.putLong("cell", cell);
                e.putInt("x", pos.getX());
                e.putInt("y", pos.getY());
                e.putInt("z", pos.getZ());
                list.add(e);
            });
            pack.put("list", list);
            centersRoot.put(structureId.toString(), pack);
        });
        tag.put("centersByStructure", centersRoot);


        CompoundTag plannedRoot = new CompoundTag();
        plannedCells.forEach((structureId, set) -> plannedRoot.put(structureId.toString(),
                new LongArrayTag(set.toLongArray())));
        tag.put("plannedCellsByStructure", plannedRoot);

        return tag;
    }

    public Map<ResourceLocation, Long2ObjectOpenHashMap<BlockPos>> centersView() {
        return Collections.unmodifiableMap(centersByStructure);
    }
}
