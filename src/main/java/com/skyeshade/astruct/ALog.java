package com.skyeshade.astruct;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class ALog {
    private static final Logger LOG = LogUtils.getLogger();

    public static volatile boolean DEBUG_ENABLED = false;

    public static void debug(String fmt, Object... args) {
        if (DEBUG_ENABLED && LOG.isDebugEnabled()) LOG.debug(fmt, args);
    }
    public static void info(String fmt, Object... args) {
        LOG.info(fmt, args);
    }
    public static void warn(String fmt, Object... args) {
        LOG.warn(fmt, args);
    }
    public static void error(String fmt, Object... args) {
        LOG.error(fmt, args);
    }
}
