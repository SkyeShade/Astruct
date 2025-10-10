package com.skyeshade.astruct;

import com.skyeshade.astruct.commands.CommandsAstruct;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(Astruct.MODID)
public class Astruct {

    public static final String MODID = "astruct";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Astruct(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);


        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }
}
