package org.dawnoftime.onceuponatown.util;

import org.dawnoftime.onceuponatown.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OuatLog {
    public static final Logger LOG = LogManager.getLogger(Constants.MOD_ID);

    public static void info(String info) {
        LOG.info(info);
    }

    public static void debug(String debug) {
        LOG.debug(debug);
    }

    public static void error(String error) {
        LOG.error(error);
    }
}
