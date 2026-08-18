package org.lowern1ght.burg.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;

public abstract class DraggableWidget implements Renderable, GuiEventListener {

    public static final int TITLE_BAR_H = 12;
    private static final int CLOSE_BTN_W = 10;

    protected int x, y, width, height;

    // Subclasses can disable the close button or position clamping
    protected boolean showCloseButton = true;
    protected boolean clampPosition   = true;

    private boolean dragging = false;
    private int dragStartMouseX, dragStartMouseY;
    private int dragStartX, dragStartY;
    private boolean closed = false;
    private boolean focused = false;

    private final int freeZoneMaxX;
    private final int screenHeight;

    public DraggableWidget(int x, int y, int width, int height, int freeZoneMaxX, int screenHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.freeZoneMaxX = freeZoneMaxX;
        this.screenHeight = screenHeight;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderTitleBar(g, mouseX, mouseY);
        renderContent(g, x, y + TITLE_BAR_H, width, height - TITLE_BAR_H, mouseX, mouseY, delta);
    }

    private void renderTitleBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + TITLE_BAR_H, getTitleBarColor());
        g.drawString(Minecraft.getInstance().font, getTitle(), x + 3, y + 2, getTitleTextColor(), false);
        renderTitleBarExtras(g, mouseX, mouseY);
        if (showCloseButton) {
            int btnX = closeBtnX();
            boolean hoverClose = mouseX >= btnX && mouseX < btnX + CLOSE_BTN_W
                && mouseY >= y && mouseY < y + TITLE_BAR_H;
            g.fill(btnX, y + 1, btnX + CLOSE_BTN_W, y + TITLE_BAR_H - 1,
                hoverClose ? 0xFFCC3333 : 0xFF882222);
            g.drawString(Minecraft.getInstance().font, "X", btnX + 2, y + 2, 0xFFFFFFFF, false);
        }
    }

    protected void renderTitleBarExtras(GuiGraphics g, int mouseX, int mouseY) {}

    protected int getTitleBarColor()  { return 0xF5333333; }
    protected int getTitleTextColor() { return 0xFFFFFFFF; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && showCloseButton && isOverCloseButton(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (button == 0 && onTitleBarClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && isOverTitleBar(mouseX, mouseY)) {
            dragging = true;
            dragStartMouseX = (int) mouseX;
            dragStartMouseY = (int) mouseY;
            dragStartX = x;
            dragStartY = y;
            return true;
        }
        return contentMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mX, double mY, int button, double dX, double dY) {
        if (dragging) {
            x = clampX(dragStartX + (int)(mX - dragStartMouseX));
            y = clampY(dragStartY + (int)(mY - dragStartMouseY));
            onMoved();
            return true;
        }
        return contentMouseDragged(mX, mY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return contentMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return contentMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    protected void onClose() { closed = true; }
    protected void onMoved() {}

    public boolean isClosed() { return closed; }
    public boolean isDragging() { return dragging; }
    public int getX() { return x; }
    public int getY() { return y; }
    protected void setHeight(int h) { this.height = h; }

    @Override
    public boolean isFocused() { return focused; }

    @Override
    public void setFocused(boolean focused) { this.focused = focused; }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isOverTitleBar(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TITLE_BAR_H;
    }

    private boolean isOverCloseButton(double mouseX, double mouseY) {
        int btnX = closeBtnX();
        return mouseX >= btnX && mouseX < btnX + CLOSE_BTN_W
            && mouseY >= y && mouseY < y + TITLE_BAR_H;
    }

    protected int closeBtnX() { return x + width - CLOSE_BTN_W - 1; }

    protected boolean onTitleBarClick(double mouseX, double mouseY) { return false; }

    private int clampX(int nx) { return clampPosition ? Math.max(0, Math.min(freeZoneMaxX - width, nx)) : nx; }
    private int clampY(int ny) { return clampPosition ? Math.max(0, Math.min(screenHeight - height, ny)) : ny; }

    protected abstract void renderContent(GuiGraphics g, int cx, int cy, int cw, int ch,
                                           int mouseX, int mouseY, float delta);
    protected abstract String getTitle();

    protected boolean contentMouseClicked(double mouseX, double mouseY, int button) { return false; }
    protected boolean contentMouseDragged(double mX, double mY, int button, double dX, double dY) { return false; }
    protected boolean contentMouseReleased(double mouseX, double mouseY, int button) { return false; }
    protected boolean contentMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }
}
