package org.lowern1ght.burg.common.ui;

import java.util.Objects;

/**
 * A text render style — fill colour + text colour + flags. The {@link #flags}
 * bit field is open for future fields (bold, italic, shadow, underline) and
 * stays engine-internal; concrete flag constants live as {@code public static
 * final int} on this class so every reader can find them.
 *
 * <p>No {@code net.minecraft} import. {@link TextStyle} is a POJO value
 * type the adapter translates into a Minecraft {@code Font} rendering call.
 *
 * <p>{@link #defaults()} is the engine's neutral style: opaque black on a
 * fully transparent background — text-only, no box. Widgets that want a
 * pill or a label background draw their own {@link Panel} first; the text
 * draws on top with the default style. {@code TextStyle.default()} is a
 * common short-form call site: Java permits {@code default} as an identifier
 * outside an interface body, so the method reads as the spec asks.
 */
public record TextStyle(Color fill, Color text, int flags) {

    public TextStyle {
        Objects.requireNonNull(fill, "fill");
        Objects.requireNonNull(text, "text");
    }

    /** Bold flag — the most likely first extension; reserved as bit 0. */
    public static final int BOLD = 1 << 0;

    /** Default style — transparent fill, black text, no flags. */
    public static final TextStyle DEFAULT = new TextStyle(Color.TRANSPARENT, Color.BLACK, 0);

    /** Default style — transparent fill, black text, no flags. */
    public static TextStyle defaults() {
        return DEFAULT;
    }

    /** True iff {@link #BOLD} is set. */
    public boolean bold() {
        return (flags & BOLD) != 0;
    }

    /** Returns a copy with the bold bit toggled to {@code bold}. */
    public TextStyle withBold(boolean bold) {
        int next = bold ? (flags | BOLD) : (flags & ~BOLD);
        return new TextStyle(fill, text, next);
    }
}