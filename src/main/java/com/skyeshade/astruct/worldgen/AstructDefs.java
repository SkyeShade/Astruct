package com.skyeshade.astruct.worldgen;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.skyeshade.astruct.Astruct;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Astruct.MODID)
public enum AstructDefs {
    INSTANCE;


    private volatile Map<ResourceLocation, StructureDef> byId = Map.of();


    public @Nullable StructureDef get(ResourceLocation id) {
        return byId.get(id);
    }


    public Collection<StructureDef> all() { return byId.values(); }
    public Set<ResourceLocation> ids()  { return byId.keySet(); }


    public void replaceAll(Map<ResourceLocation, StructureDef> fresh) {
        this.byId = Map.copyOf(fresh);
    }


    public boolean contains(ResourceLocation id) { return byId.containsKey(id); }
    public int size() { return byId.size(); }


    @SubscribeEvent
    public static void onAddReload(AddReloadListenerEvent e) {
        e.addListener(new StructureDefReload());
    }


    public static SuggestionsBuilder suggestIds(
            SuggestionsBuilder b) {
        for (ResourceLocation id : INSTANCE.ids()) {
            String s = id.toString();
            if (StringReader.isQuotedStringStart(b.getInput().isEmpty() ? '"' : b.getInput().charAt(0))) {

                b.suggest(s);
            } else if (s.indexOf(':') < 0) {
                b.suggest(s);
            } else {
                b.suggest(s);
            }
        }
        return b;
    }
}
