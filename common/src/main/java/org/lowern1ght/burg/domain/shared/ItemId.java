package org.lowern1ght.burg.domain.shared;

import java.util.Locale;
import java.util.Objects;

/**
 * Canonical identity of a Minecraft {@code Item} inside the Settlement bounded
 * context. Minecraft delivers items as {@code net.minecraft.world.item.Item}
 * with a {@code ResourceLocation} key, but the domain layer is
 * Minecraft-free (ADR-0008).
 *
 * @param value the canonical lowercase {@code "namespace:path"} form; never null
 *
 * <p>{@code ItemId} wraps the canonical string form the registry key already
 * uses; {@link #of(String)} is the strict factory at the Town facade edge and
 * {@link #parseOrEmpty} is the lenient converter used by the additive NBT
 * load path.
 *
 * <p>Instances are immutable. Two {@code ItemId} values are equal iff their
 * canonical string forms are equal. The canonical form is lowercase
 * {@code namespace:path}; {@link #of(String)} normalises the case of the
 * input, so a domain hash-map key matches regardless of which form the
 * caller typed.
 *
 * <p>No Minecraft imports. Conversion to and from {@code Item} happens at
 * the {@code Town} facade edge via
 * {@code ItemId.of(BuiltInRegistries.ITEM.getKey(item).toString())}.
 */
public record ItemId(String value) {

    /**
     * The empty / unset id. Used by the additive NBT load path when the
     * stored string is absent or malformed — a sentinel the same shape as
     * {@link CitizenId#EMPTY}.
     */
    public static final ItemId EMPTY = new ItemId("minecraft:air");

    public ItemId {
        Objects.requireNonNull(value, "ItemId.value");
        // We do NOT validate the "namespace:path" shape on purpose: the
        // additive NBT-load path on Town must be able to wrap whatever
        // string was persisted, including a stray key whose namespace
        // happens to contain a colon. The strict factory is #of(String).
    }

    /**
     * Strict factory. Normalises the input to lowercase
     * {@code namespace:path} and validates that it parses as a resource
     * location. Throws {@link IllegalArgumentException} for malformed
     * strings; the boundary caller (the Town facade) is responsible for
     * catching its own garbage when it has one.
     */
    public static ItemId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalised = raw.toLowerCase(Locale.ROOT);
        int colon = normalised.indexOf(':');
        if (colon <= 0 || colon == normalised.length() - 1) {
            throw new IllegalArgumentException(
                "ItemId must be of the form 'namespace:path' (got '" + raw + "')");
        }
        String namespace = normalised.substring(0, colon);
        String path = normalised.substring(colon + 1);
        if (!isValidNamespace(namespace) || !isValidPath(path)) {
            throw new IllegalArgumentException(
                "ItemId must be of the form 'namespace:path' (got '" + raw + "')");
        }
        return new ItemId(normalised);
    }

    /**
     * Lenient factory. Bad strings — null, empty, missing colon, illegal
     * characters — become the {@link #EMPTY} sentinel rather than throwing.
     * Used by the additive NBT load path where a missing or unparseable
     * key must read as "no stock", never as a hard failure.
     */
    public static ItemId parseOrEmpty(String raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        try {
            return of(raw);
        } catch (IllegalArgumentException e) {
            return EMPTY;
        }
    }

    /**
     * Returns the {@code namespace} segment of the canonical form, or
     * {@code ""} for {@link #EMPTY}.
     */
    public String namespace() {
        int colon = value.indexOf(':');
        return colon < 0 ? "" : value.substring(0, colon);
    }

    /**
     * Returns the {@code path} segment of the canonical form, or
     * {@code value} for {@link #EMPTY}.
     */
    public String path() {
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static boolean isValidNamespace(String ns) {
        if (ns.isEmpty()) return false;
        for (int i = 0; i < ns.length(); i++) {
            char c = ns.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String path) {
        if (path.isEmpty()) return false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '/'
                || c == '.')) {
                return false;
            }
        }
        return true;
    }
}