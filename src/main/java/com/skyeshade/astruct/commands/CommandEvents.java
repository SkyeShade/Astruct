package com.skyeshade.astruct.commands;

import com.skyeshade.astruct.Astruct;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Astruct.MODID)
public final class CommandEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        CommandsAstruct.attach(e.getDispatcher());
    }
}
