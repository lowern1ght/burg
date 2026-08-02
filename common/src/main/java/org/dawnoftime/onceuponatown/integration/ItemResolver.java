package org.dawnoftime.onceuponatown.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The single entry point for resolving a {@link ResourceLocation} into an {@link ItemStack}.
 *
 * <p>Wraps {@link BuiltInRegistries#ITEM} lookups so that every Burg system that reads
 * item ids from JSON has the same behaviour when the target item is missing:
 * <ul>
 *   <li>Log at most once per {@code (modId, ResourceLocation)} via
 *       {@link ExternalModPresence#warnMissingItemOnce} — no spam on hot reloads.</li>
 *   <li>Return {@link ItemStack#EMPTY} so the caller can {@code continue} without
 *       silently inserting an AIR-placeholder entry.</li>
 * </ul>
 * Before this class, every call site did its own
 * {@code BuiltInRegistries.ITEM.get(...)} (or {@code .getOptional(...).orElse(null)}),
 * which meant missing items either silently became AIR or were silently skipped — both
 * of which made life harder for anyone debugging "why are my farmersdelight recipes
 * empty after install".
 */
public final class ItemResolver {

    private ItemResolver() {}

    /**
     * Resolves a {@link ResourceLocation} to an {@link ItemStack}.
     *
     * @return a non-empty stack of the registered item, or {@link ItemStack#EMPTY} when
     *         the item is not registered (mod absent, mod present but item id wrong,
     *         datapack typo). The empty case is logged at most once per
     *         {@code (modId, ResourceLocation)} via
     *         {@link ExternalModPresence#warnMissingItemOnce}.
     */
    public static ItemStack resolve(ResourceLocation rl) {
        var opt = BuiltInRegistries.ITEM.getOptional(rl);
        if (opt.isEmpty()) {
            ExternalModPresence.warnMissingItemOnce(rl.getNamespace(), rl);
            return ItemStack.EMPTY;
        }
        return new ItemStack(opt.get());
    }

    /**
     * @return {@code true} when the item is registered. Use this when the caller only
     *         needs a presence check and does not need the {@link ItemStack}.
     */
    public static boolean isPresent(ResourceLocation rl) {
        return BuiltInRegistries.ITEM.getOptional(rl).isPresent();
    }

    /**
     * String convenience overload for {@link #isPresent(ResourceLocation)} and the
     * common case where the id is already a string (a JSON field, a config value).
     * Parses the id, then forwards to the typed overload — same warning behaviour.
     */
    public static boolean isPresent(String itemId) {
        return isPresent(ResourceLocation.parse(itemId));
    }

    /**
     * String convenience overload for {@link #resolve(ResourceLocation)} that returns
     * whether the resolved stack is non-empty. Useful for tests and for callers that
     * hold only the id and want a boolean answer without keeping the {@link ItemStack}.
     */
    public static boolean resolveExists(String itemId) {
        return !resolve(ResourceLocation.parse(itemId)).isEmpty();
    }
}
