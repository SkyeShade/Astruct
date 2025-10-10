package com.skyeshade.astruct.events;


import com.skyeshade.astruct.Astruct;
import com.skyeshade.astruct.worldgen.StructureDefReload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;


@EventBusSubscriber(modid = Astruct.MODID)
public final class AstructReloads {
    @SubscribeEvent
    public static void onAddReload(AddReloadListenerEvent e) {
        e.addListener(new StructureDefReload());
    }
}
