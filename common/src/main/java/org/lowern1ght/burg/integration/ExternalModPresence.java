package org.lowern1ght.burg.integration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live cache of "which mods are loaded right now", the soft-dependency check the rest of
 * Burg uses to decide whether to enable a feature that references another mod's items.
 *
 * <p>The cache is rebuilt on every datapack sync (player join or {@code /reload}). It is
 * NOT valid for client-side display logic that needs to track a user's install between
 * reloads — there is no need to. {@link #isLoaded(String)} is the only call site that
 * should matter for production code.
 *
 * <p>Initialization is the caller's job. {@link #register()} subscribes to
 * the {@code OnDatapackSyncEvent} on the {@link NeoForge#EVENT_BUS} and runs {@link #refresh()}
 * once at startup. The codebase already has
 * {@code OuatForge.onServerStarting(...)} as its reload entry point — {@link #register()}
 * wires the same lifecycle into the integration package without forcing that entry point
 * to know about it.
 */
public final class ExternalModPresence {

    public static final String FARMERS_DELIGHT = "farmersdelight";

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalModPresence.class);
    private static final Set<String> loadedNow = ConcurrentHashMap.newKeySet();
    private static final Set<String> warnedItems = ConcurrentHashMap.newKeySet();

    private ExternalModPresence() {}

    /** @return {@code true} when {@code modId} is a loaded mod on this server. */
    public static boolean isLoaded(String modId) {
        return loadedNow.contains(modId);
    }

    /** @return an immutable snapshot of currently-loaded mod ids. */
    public static Set<String> loadedMods() {
        return Set.copyOf(loadedNow);
    }

    /** Called at world load and on every datapack sync. Rebuilds the cached set. */
    public static void refresh() {
        loadedNow.clear();
        for (var modInfo : ModList.get().getMods()) {
            loadedNow.add(modInfo.getModId());
        }
    }

    /**
     * Log at most once per {@code (modId, ResourceLocation)} pair. Hot reloads fire
     * the parser many times for the same missing item; this dedupe keeps the log quiet.
     */
    public static void warnMissingItemOnce(String modId, ResourceLocation rl) {
        if (warnedItems.add(modId + ':' + rl)) {
            LOGGER.warn("Item {} not available (mod {} not loaded or item id missing); skipping", rl, modId);
        }
    }

    /** Test-only accessor for the warn dedupe set. */
    public static int warnedCount() {
        return warnedItems.size();
    }

    /** Wires {@link #refresh()} to the datapack reload lifecycle. Idempotent. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(event -> refresh());
        refresh();
    }
}
