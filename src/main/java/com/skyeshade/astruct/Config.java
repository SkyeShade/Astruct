package com.skyeshade.astruct;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Astruct.MODID)
public final class Config {


    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGS_V =
            BUILDER.comment("Enable verbose debug logs for Astruct (spammy).")
                    .define("debug_logs", false);

    public static final ForgeConfigSpec.IntValue MAX_PLACEMENTS_PER_TICK_V =
            BUILDER.comment("Max structure placement steps processed per server tick.")
                    .defineInRange("max_placements_per_tick", 10, 1, 10_000);

    public static final ForgeConfigSpec SPEC = BUILDER.build();


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


}
