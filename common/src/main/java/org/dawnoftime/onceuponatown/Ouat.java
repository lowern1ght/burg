package org.dawnoftime.onceuponatown;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dawnoftime.onceuponatown.client.ClientAbstractions;
import org.dawnoftime.onceuponatown.network.S2CPlayerChatMessagePacket;

import java.util.ServiceLoader;


public class Ouat {
    public static final String MOD_ID = "onceuponatown";
    public static final String MOD_NAME = "Once upon a Town";
    public static final String MOD_ABBREVIATION = "ouat";
    public static final Logger LOG = LogManager.getLogger(MOD_NAME);
    // Common and client events and calls.
    public static final CommonAbstractions COMMON = load(CommonAbstractions.class);
    public static final ClientAbstractions CLIENT = load(ClientAbstractions.class);

    public static ResourceLocation modResource(String name) {
        return new ResourceLocation(MOD_ID, name);
    }

    public static MutableComponent translatable(String prefix, String key, Object... args) {
        return Component.translatable(MOD_ID + "." + prefix + "." + key, args);
    }

    /**
     * Logs an informational message with green color in the console.
     *
     * @param info The informational message to be logged.
     */
    public static void info(String info) {
        LOG.info("\u001B[32m{}\u001B[0m", info);
    }

    /**
     * Logs a debug-level message with yellow color in the console.
     *
     * @param debug The debug message to be logged.
     */
    public static void debug(String debug) {
        LOG.debug("\u001B[33m{}\u001B[0m", debug);
    }

    /**
     * Logs an error message with red color in the console.
     *
     * @param error The error message to be logged.
     */
    public static void error(String error) {
        LOG.error("\u001B[31m{}\u001B[0m", error);
    }

    /**
     * Sends a chat message to a specific player.
     *
     * @param player The player who will receive the message.
     * @param translationKey The translation key for the message, defined in the language files.
     * @param args Optional arguments for message formatting, replacing placeholders in the translation key.
     */
    public static void clientChat(Player player, String translationKey, Object... args) {
        Component message = Ouat.translatable(translationKey, args);
        COMMON.sendToClient(player, S2CPlayerChatMessagePacket.create(message));
    }

    /**
     * Load a service for the current environment. Your implementation of the service must be defined
     * manually by including a text file in META-INF/services named with the fully qualified class name of the service.
     *
     * @param clazz Class of the common element that is implemented differently depending on the platform.
     * @param <T>   Class studied.
     * @return An instance of the given class.
     */
    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}
