package org.dawnoftime.onceuponatown;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ServiceLoader;


public class Ouat {
    // Core
    public static final String MOD_ID = "onceuponatown";
    public static final String MOD_NAME = "Once upon a Town";
    public static ResourceLocation createOuatResource(String name) {
        return new ResourceLocation(MOD_ID, name);
    }

    // Logs
    public static final Logger LOG = LogManager.getLogger(MOD_NAME);
    public static void info(String info) {
        LOG.info(info);
    }

    public static void debug(String debug) {
        LOG.debug(debug);
    }

    public static void error(String error) {
        LOG.error(error);
    }

    // Common and client events and calls.
    public static final Common COMMON = load(Common.class);

    public static final Client CLIENT = load(Client.class);

    /**
     * Load a service for the current environment. Your implementation of the service must be defined
     * manually by including a text file in META-INF/services named with the fully qualified class name of the service.
     * @param clazz Class of the common element that is implemented differently depending on the platform.
     * @return An instance of the given class.
     * @param <T> Class studied.
     */
    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}
