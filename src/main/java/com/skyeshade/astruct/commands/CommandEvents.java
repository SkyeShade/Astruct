package com.skyeshade.astruct.commands;

import com.skyeshade.astruct.Astruct;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Astruct.MODID)
public final class CommandEvents {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        CommandsAstruct.attach(e.getDispatcher());
    }
}
