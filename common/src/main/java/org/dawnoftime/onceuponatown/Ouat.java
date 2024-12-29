package org.dawnoftime.onceuponatown;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dawnoftime.onceuponatown.client.ClientAbstractions;

import java.util.ServiceLoader;


public class Ouat {
    public static final String MOD_ID = "onceuponatown";
    public static final String MOD_NAME = "Once upon a Town";
    public static final Logger LOG = LogManager.getLogger(MOD_NAME);
    // Common and client events and calls.
    public static final CommonAbstractions COMMON = load(CommonAbstractions.class);
    public static final ClientAbstractions CLIENT = load(ClientAbstractions.class);

    public static ResourceLocation modResource(String name) {
        return new ResourceLocation(MOD_ID, name);
    }

    public static void info(String info) {
        LOG.info("\u001B[32m{}\u001B[0m", info);
    }

    public static void debug(String debug) {
        LOG.debug("\u001B[33m{}\u001B[0m", debug);
    }

    public static void error(String error) {
        LOG.error("\u001B[31m{}\u001B[0m", error);
    }

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
