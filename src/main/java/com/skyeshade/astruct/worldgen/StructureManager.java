package com.skyeshade.astruct.worldgen;


import com.mojang.logging.LogUtils;
import com.skyeshade.astruct.ALog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class StructureManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Back-compat entry: plan in the 0,0 grid cell (if you read this, i dont recommend using it). */
    public static void planStructure(ServerLevel level, ResourceLocation defId) {
        StructureDef def = AstructDefs.INSTANCE.get(defId);
        if (def == null) {
            ALog.warn("Astruct: missing structure def {}", defId);
            return;
        }


        new Planner(level, def, 0, 0).startAsync();

        ALog.debug("Astruct: planning {} in {} (cell[0,0], start={}, target={})",
                defId, level.dimension().location(), def.startPool(), def.connectorTarget());
    }

    /** Plan for a specific grid cell. */
    public static void planStructure(ServerLevel level, ResourceLocation defId, int cx, int cz) {
        StructureDef def = AstructDefs.INSTANCE.get(defId);
        if (def == null) {
            ALog.warn("Astruct: missing structure def {}", defId);
            return;
        }

        new Planner(level, def, cx, cz).startAsync();

        ALog.debug("Astruct: planning {} in {} for cell[{},{}] (start={}, target={})",
                defId, level.dimension().location(), cx, cz, def.startPool(), def.connectorTarget());
    }
}
