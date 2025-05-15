package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.mouseInArea;

public class DropdownOverlayScreen extends Screen {
    private static final ResourceLocation OVERLAY_TEXTURE = Ouat.modResource("textures/gui/drop_down.png");
    private static final int TEXTURE_TOTAL_WIDTH = 155;
    private static final int TEXTURE_TOTAL_HEIGHT = 78;
    private static final int TEXTURE_WIDTH = 149;
    private static final int TEXTURE_SCROLL_ICON_WIDTH = 6;
    private static final int TEXTURE_SCROLL_ICON_HEIGHT = 15;
    private static final int SCROLL_ZONE_X = 139;
    private static final int SCROLL_ZONE_Y = 4;
    public static final int WIDGET_ZONE_X = 4;
    private static final int WIDGET_ZONE_Y = 4;
    public static final int WIDGET_ZONE_WIDTH = 132;
    private static final int WIDGET_ZONE_HEIGHT = 70;
    public static final int WIDGET_HEIGHT = 16;
    private static final int WIDGET_HEIGHT_PADDED = WIDGET_HEIGHT + 1;

    private final Screen parentScreen;
    private final DropdownButton parentButton;
    int posX;
    private int posY;
    private int scrollOffset = 0;
    private int scrollMaxOffset;
    boolean scrolling = false;
    private final HashMap<String, String> options;
    private final LinkedHashMap<String, Button> widgets = new LinkedHashMap<>();

    public DropdownOverlayScreen(Screen parent, DropdownButton button, LinkedHashMap<String, String> options) {
        super(Component.empty());
        this.parentScreen = parent;
        this.parentButton = button;
        this.options = options;
    }

    @Override
    protected void init() {
        // Since this function is also called on resize, we have to reset the widgets.
        this.posX = parentButton.getX();
        this.posY = parentButton.getY() + parentButton.getHeight() - 1;
        widgets.clear();
        // Finally, create the widgets specific to the screen.
        this.initWidgets();
        this.updateWidgetPositions();
        scrollMaxOffset = Math.max(widgets.size() * WIDGET_HEIGHT_PADDED - WIDGET_ZONE_HEIGHT, 0);
    }

    private void initWidgets() {
        for (String id : options.keySet()) {
            Button button = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT, Component.literal(options.get(id)), btn -> {
                parentButton.setCurrentChoiceKey(id);
                Minecraft.getInstance().setScreen(parentScreen);
            });
            widgets.put(id, button);
            this.addRenderableWidget(button);
        }
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        parentScreen.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        parentScreen.render(guiGraphics, 0, 0, partialTicks);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        guiGraphics.blit(OVERLAY_TEXTURE, posX, posY, 0, 0, TEXTURE_WIDTH, TEXTURE_TOTAL_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        guiGraphics.enableScissor(posX + WIDGET_ZONE_X, posY + WIDGET_ZONE_Y, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH, posY + WIDGET_ZONE_Y + WIDGET_ZONE_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.disableScissor();
        this.renderScrollBar(guiGraphics);
        guiGraphics.pose().popPose();
    }

    private void renderScrollBar(@NotNull GuiGraphics guiGraphics){
        if (this.scrollbarActivated()){
            int offset = this.getScrollButtonY();
            guiGraphics.blit(OVERLAY_TEXTURE, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y + offset, 149, 0, TEXTURE_SCROLL_ICON_WIDTH, TEXTURE_SCROLL_ICON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        } else {
            guiGraphics.blit(OVERLAY_TEXTURE, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y, 149, 15, TEXTURE_SCROLL_ICON_WIDTH, TEXTURE_SCROLL_ICON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        }
    }

    private void updateWidgetPositions() {
        String[] widgetIds = widgets.keySet().toArray(new String[0]);
        for (int i = 0; i < widgetIds.length; i++) {
            int widgetY = i * WIDGET_HEIGHT_PADDED - scrollOffset;
            AbstractWidget widget = widgets.get(widgetIds[i]);
            if (widgetY + WIDGET_HEIGHT_PADDED > 0 && widgetY < WIDGET_ZONE_HEIGHT) {
                widget.visible = true;
                widget.setY(posY + WIDGET_ZONE_Y + widgetY);
            } else {
                widget.visible = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.scrollbarActivated()) {
            return false;
        }
        if (delta < 0 && scrollOffset < scrollMaxOffset) {
            scrollOffset = Math.min(scrollOffset + 4, scrollMaxOffset);
        } else if (delta > 0 && scrollOffset > 0) {
            scrollOffset = Math.max(scrollOffset - 4, 0);
        }
        this.updateWidgetPositions();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.scrollbarActivated()) {
            // Detect if the user clicked somewhere in the scroll bar (but not the button).
            if (mouseInArea(mouseX, mouseY, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y, TEXTURE_SCROLL_ICON_WIDTH, WIDGET_ZONE_HEIGHT)) {
                scrolling = true;
                this.setScrollOffsetFromMouseY(mouseY);
                this.updateWidgetPositions();
                return true;
            }
        }
        if (!mouseInArea(mouseX, mouseY, posX, posY, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT)) {
            // Detect if the user clicked outside the dropdown screen.
            Minecraft.getInstance().setScreen(parentScreen);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling) {
            this.setScrollOffsetFromMouseY(mouseY);
            this.updateWidgetPositions();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrolling) {
            scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean scrollbarActivated() {
        return scrollMaxOffset > 0;
    }

    private int getScrollButtonY() {
        return (int) (((double) scrollOffset / (double) scrollMaxOffset) * (WIDGET_ZONE_HEIGHT - TEXTURE_SCROLL_ICON_HEIGHT));
    }

    private void setScrollOffsetFromMouseY(double mouseY){
        double newScrollButtonY = Math.min(Math.max(0, mouseY - posY  - SCROLL_ZONE_Y - TEXTURE_SCROLL_ICON_HEIGHT / 2.0D), WIDGET_ZONE_HEIGHT - TEXTURE_SCROLL_ICON_HEIGHT);
        scrollOffset = (int) (scrollMaxOffset * newScrollButtonY / (double) (WIDGET_ZONE_HEIGHT - TEXTURE_SCROLL_ICON_HEIGHT));
    }
}
