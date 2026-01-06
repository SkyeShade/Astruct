package com.skyeshade.astruct.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.skyeshade.astruct.worldgen.AstructDefs;
import com.skyeshade.astruct.worldgen.AstructWorldData;
import com.skyeshade.astruct.worldgen.CenterLocator;
import com.skyeshade.astruct.worldgen.StructureDef;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.CompletableFuture;


public final class CommandsAstruct {
    private CommandsAstruct() {}

    private static final int DEFAULT_RADIUS = 100000;


    public static void attach(CommandDispatcher<CommandSourceStack> d) {

        CommandNode<CommandSourceStack> locate = d.getRoot().getChild("locate");
        if (locate == null) return;

        var astructUnderLocate = Commands.literal("astruct")
                .requires(s -> s.hasPermission(2))

                .executes(ctx -> doLocateVanillaStyle(ctx.getSource(), null, DEFAULT_RADIUS))

                .then(Commands.argument("radius", IntegerArgumentType.integer(256, 100000))
                        .executes(ctx -> doLocateVanillaStyle(
                                ctx.getSource(),
                                null,
                                IntegerArgumentType.getInteger(ctx, "radius"))))

                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .suggests((c, b) -> suggestStructureIds(b))
                        .executes(ctx -> doLocateVanillaStyle(
                                ctx.getSource(),
                                ResourceLocationArgument.getId(ctx, "id"),
                                DEFAULT_RADIUS))

                        .then(Commands.argument("radius", IntegerArgumentType.integer(256, 100000))
                                .executes(ctx -> doLocateVanillaStyle(
                                        ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                .build();

        locate.addChild(astructUnderLocate);
    }

    private static int doLocateVanillaStyle(CommandSourceStack src,
                                            ResourceLocation structureIdOrNull,
                                            int radius) {
        ServerLevel here = (ServerLevel) src.getLevel();
        if (here == null) {
            src.sendFailure(Component.literal("[Astruct] No level available."));
            return 0;
        }


        java.util.List<StructureDef> candidates = new java.util.ArrayList<>();
        if (structureIdOrNull != null) {
            StructureDef def = AstructDefs.INSTANCE.get(structureIdOrNull);
            if (def == null) {
                src.sendFailure(Component.literal("[Astruct] Unknown structure id: " + structureIdOrNull));
                return 0;
            }
            if (!here.dimension().equals(def.dimension())) {
                src.sendFailure(Component.literal(
                        "The requested structure generates in " + def.dimension().location()
                                + ". Try: /execute in " + def.dimension().location()
                                + " run locate astruct " + def.id()));
                return 0;
            }
            candidates.add(def);
        } else {
            for (StructureDef def : AstructDefs.INSTANCE.all()) {
                if (here.dimension().equals(def.dimension())) candidates.add(def);
            }
            if (candidates.isEmpty()) {
                src.sendFailure(Component.literal("[Astruct] No Astruct structures generate in this dimension."));
                return 0;
            }
        }

        BlockPos playerPos = BlockPos.containing(src.getPosition());
        StructureDef bestDef = null;
        BlockPos bestPos = null;
        double bestD2 = Double.MAX_VALUE;

        for (StructureDef def : candidates) {

            AstructWorldData data = AstructWorldData.get(here);
            data.ensureCentersAround(here, def, playerPos, radius, 0);

            var centersMap = data.centersView().get(def.id());
            if (centersMap == null || centersMap.isEmpty()) continue;

            for (BlockPos c : centersMap.values()) {
                if (!c.closerThan(playerPos, radius)) continue;

                int cx = CenterLocator.cellOf(c.getX(), def.spacing());
                int cz = CenterLocator.cellOf(c.getZ(), def.spacing());


                if (data.isInvalidBiomeCell(def.id(), cx, cz)) continue;

                double dx = (c.getX() + 0.5) - src.getPosition().x;
                double dz = (c.getZ() + 0.5) - src.getPosition().z;
                double d2 = dx * dx + dz * dz;

                if (d2 < bestD2) {
                    bestD2 = d2;
                    bestPos = c;
                    bestDef = def;
                }
            }
            int finalY = StructureDef.GenY.resolveGenY(here, bestDef, bestPos.getX(), bestPos.getZ());
            bestPos = new BlockPos(bestPos.getX(), finalY, bestPos.getZ());

        }

        if (bestPos == null) {
            src.sendSuccess(() -> Component.literal("[Astruct] No centers found within " + radius + " blocks."), false);
            return 1;
        }

        int dist = (int) Math.round(Math.sqrt(bestD2));
        StructureDef finalBestDef = bestDef;
        BlockPos finalBestPos = bestPos;

        src.sendSuccess(
                () -> Component.literal("The nearest " + finalBestDef.id() + " is at ")
                        .append(clickableCoords(finalBestPos))
                        .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(dist)).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" blocks)").withStyle(ChatFormatting.GRAY)),
                false
        );
        return 1;
    }



    private static MutableComponent clickableCoords(BlockPos pos) {
        String tpCmd = "/tp @s %d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
        MutableComponent coords = Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ())
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCmd))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to prefill: ").append(
                                        Component.literal(tpCmd).withStyle(ChatFormatting.YELLOW)))))
                ;
        return Component.literal("[")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(coords)
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }


    private static CompletableFuture<Suggestions> suggestStructureIds(SuggestionsBuilder b) {
        for (ResourceLocation id : AstructDefs.INSTANCE.ids()) {
            b.suggest(id.toString());
        }
        return b.buildFuture();
    }
}
