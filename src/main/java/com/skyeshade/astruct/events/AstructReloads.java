package com.skyeshade.astruct.events;


import com.skyeshade.astruct.Astruct;
import com.skyeshade.astruct.worldgen.StructureDefReload;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = Astruct.MODID)
public final class AstructReloads {
    @SubscribeEvent
    public static void onAddReload(AddReloadListenerEvent e) {
        e.addListener(new StructureDefReload());
    }
}
