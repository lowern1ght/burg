package org.lowern1ght.burg.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lowern1ght.burg.common.ui.Color;
import org.lowern1ght.burg.common.ui.DrawContext;
import org.lowern1ght.burg.common.ui.TextStyle;

/**
 * The Minecraft adapter for {@link DrawContext}. Every {@code drawRect} /
 * {@code drawText} / clip-stack call is forwarded to a {@link GuiGraphics};
 * the engine itself never sees Minecraft types — the adapter is the
 * only place this translation lives (ADR-0022 §"Three rules").
 *
 * <p>The constructor takes the GUI-space origin offset (top-left of the
 * parent screen inside the {@link GuiGraphics} coordinate space) so
 * widgets drawn at {@code (0, 0)} land at the parent's top-left. Mouse
 * coordinates come from the {@link Screen} render-loop parameters already
 * in GUI-space; the adapter subtracts the origin so the engine sees
 * consistent {@code (0, 0)}-rooted coordinates.
 *
 * <p>Clip-stack management: {@link GuiGraphics#enableScissor} uses
 * absolute screen-pixel coordinates; the adapter applies the origin
 * offset when pushing a clip and removes it when popping.
 */
public final class McDrawContext extends DrawContext {

    private final GuiGraphics guiGraphics;
    private final Font font;
    private final int originX;
    private final int originY;

    /**
     * Constructs the context. {@code width} / {@code height} are the
     * parent's size in GUI-space; {@code originX} / {@code originY} are
     * the top-left of the parent inside {@code guiGraphics}' coordinate
     * space (typically {@code leftPos} / {@code topPos} for subclasses of
     * {@code AbstractContainerScreen}, or {@code (0, 0)} for a top-level
     * {@link Screen}).
     */
    public McDrawContext(
        GuiGraphics guiGraphics,
        Font font,
        int originX,
        int originY,
        int width,
        int height,
        int mouseX,
        int mouseY
    ) {
        super(width, height, mouseX - originX, mouseY - originY);
        this.guiGraphics = guiGraphics;
        this.font = font;
        this.originX = originX;
        this.originY = originY;
    }

    @Override
    public void drawRect(int x, int y, int w, int h, Color color) {
        if (w <= 0 || h <= 0) return;
        if (color.alpha() <= 0) return;
        // guiGraphics.fill takes an ARGB int directly; the engine's
        // Color.argb is in the same byte order.
        guiGraphics.fill(originX + x, originY + y, originX + x + w, originY + y + h, color.argb());
    }

    @Override
    public void drawText(String text, int x, int y, TextStyle style) {
        if (text == null || text.isEmpty()) return;
        // Resolve lang keys of the form "namespace.path" via the active
        // Language; the placeholder label carries the lang key so the
        // engine never sees a translated string. Plain text draws verbatim.
        Component component = isLangKey(text) ? Component.translatable(text) : Component.literal(text);
        int color = style.text().argb();
        guiGraphics.drawString(font, component, originX + x, originY + y, color, false);
    }

    @Override
    public void pushClip(org.lowern1ght.burg.common.ui.Rect rect) {
        super.pushClip(rect);
        // Apply the intersected clip in screen-space. enableScissor takes
        // absolute coordinates — translate the engine's GUI-space rect.
        var c = clip();
        if (!c.isEmpty()) {
            guiGraphics.enableScissor(originX + c.x(), originY + c.y(), originX + c.x() + c.w(), originY + c.y() + c.h());
        } else {
            // Empty clip — push a 0x0 scissor so subsequent draws short-circuit.
            guiGraphics.enableScissor(originX, originY, originX, originY);
        }
    }

    @Override
    public void popClip() {
        super.popClip();
        guiGraphics.disableScissor();
    }

    private static boolean isLangKey(String text) {
        // The engine's lang keys look like "namespace.path" — at least one
        // dot, no spaces, no leading capital. The text formatter in
        // TownHubScreen uses these as translation keys; anything else is
        // treated as a literal.
        if (text == null || text.isEmpty()) return false;
        int dot = text.indexOf('.');
        return dot > 0 && dot < text.length() - 1;
    }
}