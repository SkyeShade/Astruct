package com.skyeshade.astruct;


import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;


@EventBusSubscriber(modid = Astruct.MODID)
public final class Config {


    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGS_V =
            BUILDER.comment("Enable verbose debug logs for Astruct (spammy).")
                    .define("debug_logs", false);

    public static final ModConfigSpec.IntValue MAX_PLACEMENTS_PER_TICK_V =
            BUILDER.comment("Max structure placement steps processed per server tick.")
                    .defineInRange("max_placements_per_tick", 10, 1, 10_000);

    public static final ModConfigSpec SPEC = BUILDER.build();


    public static volatile boolean DEBUG_LOGS = false;
    public static volatile int MAX_PLACEMENTS_PER_TICK = 10;

    private Config() {}


    public static void bake() {
        try {
            DEBUG_LOGS = DEBUG_LOGS_V.get();
            MAX_PLACEMENTS_PER_TICK = MAX_PLACEMENTS_PER_TICK_V.get();
        } catch (IllegalStateException ignored) {
            // Config not loaded yet
        }
    }


    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() == SPEC) bake();
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading e) {
        if (e.getConfig().getSpec() == SPEC) bake();
    }


    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
