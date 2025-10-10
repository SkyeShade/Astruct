
package com.skyeshade.astruct.worldgen;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
public final class PlacerData extends SavedData {
    private static final String NAME = "astruct_placer";


    private final Map<UUID, PlacementStep> steps = new LinkedHashMap<>();


    private final Long2ObjectMap<List<UUID>> byChunk = new Long2ObjectOpenHashMap<>();


    private final ObjectOpenHashSet<UUID> realized = new ObjectOpenHashSet<>();

    public static PlacerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(PlacerData::new, PlacerData::load),
                NAME
        );
    }

    private final ObjectOpenHashSet<String> fingerprints = new ObjectOpenHashSet<>();

    private static String fp(PlacementStep s) {

        return s.templateId() + "@" + s.origin().getX() + "," + s.origin().getY() + "," + s.origin().getZ()
                + "#" + s.rotation().name() + "/" + s.mirror().name();
    }

    public PlacerData() {}


    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {

        ListTag list = new ListTag();
        for (var e : steps.entrySet()) {
            UUID id = e.getKey();
            PlacementStep step = e.getValue();

            CompoundTag n = new CompoundTag();
            n.putUUID("id", id);
            n.putString("template", step.templateId().toString());
            n.putInt("ox", step.origin().getX());
            n.putInt("oy", step.origin().getY());
            n.putInt("oz", step.origin().getZ());

            ListTag chunks = new ListTag();
            for (long l : step.requiredChunks()) chunks.add(LongTag.valueOf(l));
            n.put("chunks", chunks);

            n.putString("rotation", step.rotation().name());
            n.putString("mirror", step.mirror().name());

            list.add(n);
        }
        tag.put("steps", list);


        ListTag done = new ListTag();
        for (UUID id : realized) {
            CompoundTag n = new CompoundTag();
            n.putUUID("id", id);
            done.add(n);
        }
        tag.put("realized", done);

        return tag;
    }

    public static PlacerData load(CompoundTag tag, HolderLookup.Provider provider) {
        PlacerData d = new PlacerData();


        ListTag list = tag.getList("steps", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;

            UUID id = e.getUUID("id");
            ResourceLocation tpl = ResourceLocation.parse(e.getString("template"));
            BlockPos origin = new BlockPos(e.getInt("ox"), e.getInt("oy"), e.getInt("oz"));

            Set<Long> req = new HashSet<>();
            ListTag chunks = e.getList("chunks", Tag.TAG_LONG);
            for (Tag ct : chunks) req.add(((LongTag) ct).getAsLong());

            Rotation rot = Rotation.NONE;
            Mirror mir = Mirror.NONE;
            if (e.contains("rotation", Tag.TAG_STRING)) {
                try { rot = Rotation.valueOf(e.getString("rotation")); } catch (IllegalArgumentException ignored) {}
            }
            if (e.contains("mirror", Tag.TAG_STRING)) {
                try { mir = Mirror.valueOf(e.getString("mirror")); } catch (IllegalArgumentException ignored) {}
            }

            PlacementStep step = new PlacementStep(tpl, origin, req, rot, mir);
            d.steps.put(id, step);
            d.fingerprints.add(fp(step)); // rebuild fp cache
        }

        // realized
        ListTag done = tag.getList("realized", Tag.TAG_COMPOUND);
        for (Tag t : done) d.realized.add(((CompoundTag) t).getUUID("id"));

        d.rebuildIndex();
        return d;
    }


    public List<UUID> stepsForChunk(long chunkLong) {
        return byChunk.getOrDefault(chunkLong, List.of());
    }

    public Optional<PlacementStep> get(UUID id) { return Optional.ofNullable(steps.get(id)); }

    public boolean isRealized(UUID id) { return realized.contains(id); }

    /** Call after a successful placeInWorld; removes from 'steps' and records realized. */
    public void markRealized(UUID id) {

        PlacementStep s = steps.remove(id);
        if (s != null) {
            fingerprints.remove(fp(s));
            for (long ch : s.requiredChunks()) {
                var list = byChunk.get(ch);
                if (list != null) list.remove(id);
            }
        }
        realized.add(id);
        setDirty();
    }

    /** Add a new step (if unique). */
    public void add(UUID id, PlacementStep step) {
        if (realized.contains(id)) return; // already placed previously
        String sig = fp(step);
        if (fingerprints.contains(sig)) return; // already queued

        steps.put(id, step);
        fingerprints.add(sig);
        for (long ch : step.requiredChunks())
            byChunk.computeIfAbsent(ch, k -> new ArrayList<>()).add(id);
        setDirty();
    }

    /** Remove a pending step without marking realized (e.g., template missing). */
    public void remove(UUID id) {
        PlacementStep s = steps.remove(id);
        if (s != null) {
            fingerprints.remove(fp(s));
            for (long ch : s.requiredChunks()) {
                var list = byChunk.get(ch);
                if (list != null) list.remove(id);
            }
            setDirty();
        }
    }

    public void rebuildIndex() {
        byChunk.clear();
        for (var e : steps.entrySet())
            for (long ch : e.getValue().requiredChunks())
                byChunk.computeIfAbsent(ch, k -> new ArrayList<>()).add(e.getKey());
    }

    public Collection<Map.Entry<UUID, PlacementStep>> entries() {
        return List.copyOf(steps.entrySet());
    }
}
