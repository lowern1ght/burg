package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.client.screen.ScreenUtils.GUI_COLOR_GREY;
import static org.dawnoftime.onceuponatown.client.screen.ScreenUtils.drawCenteredString;

public abstract class BaseCCScreen extends Screen {
    private static final ResourceLocation GUI_TEXTURE = Ouat.modResource("textures/gui/culture_creator.png");
    private static final int TEXTURE_TOTAL_WIDTH = 240;
    private static final int TEXTURE_TOTAL_HEIGHT = 217;
    private static final int TEXTURE_TAB_HEIGHT = 166;
    private static final int TEXTURE_SCROLL_ICON_ON_U = 67;
    private static final int TEXTURE_SCROLL_ICON_ON_V = 166;
    private static final int TEXTURE_SCROLL_ICON_OFF_U = 75;
    private static final int TEXTURE_SCROLL_ICON_OFF_V = 166;
    private static final int TEXTURE_SCROLL_ICON_WIDTH = 8;
    private static final int TEXTURE_SCROLL_ICON_HEIGHT = 27;
    private static final int TEXTURE_NAVIGATION_OFF_TOP_U = 5;
    private static final int TEXTURE_NAVIGATION_OFF_TOP_V = 166;
    private static final int TEXTURE_NAVIGATION_OFF_U = 5;
    private static final int TEXTURE_NAVIGATION_OFF_V = 181;
    private static final int TEXTURE_NAVIGATION_OFF_WIDTH = 59;
    private static final int TEXTURE_NAVIGATION_OFF_HEIGHT = 15;
    private static final int TEXTURE_NAVIGATION_ON_U = 0;
    private static final int TEXTURE_NAVIGATION_ON_V = 196;
    private static final int TEXTURE_NAVIGATION_ON_WIDTH = 66;
    private static final int TEXTURE_NAVIGATION_ON_HEIGHT = 21;
    private static final int TITLE_OFFSET_Y = 8;
    private static final int SCROLL_ZONE_X = 225;
    private static final int SCROLL_ZONE_Y = 20;
    private static final int WIDGET_ZONE_X = 7;
    private static final int WIDGET_ZONE_Y = 20;
    private static final int WIDGET_ZONE_WIDTH = 213;
    private static final int WIDGET_ZONE_HEIGHT = 139;
    private static final int WIDGET_HEIGHT = 20;

    private int posX;
    private int posY;
    private int scrollOffset = 0;
    private int scrollMaxOffset;
    boolean scrolling = false;
    protected final List<AbstractWidget[]> widgets = new ArrayList<>();

    public BaseCCScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        posX = (width - TEXTURE_TOTAL_WIDTH) / 2;
        posY = (height - TEXTURE_TOTAL_HEIGHT) / 2;
        // Since this function is also called on resize, we have to reset the widgets.
        widgets.clear();
        this.initWidgets();
        this.updateWidgetPositions();
        scrollMaxOffset = Math.max(widgets.size() * WIDGET_HEIGHT - WIDGET_ZONE_HEIGHT, 0);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.blit(GUI_TEXTURE, posX, posY, 0, 0, TEXTURE_TOTAL_WIDTH, TEXTURE_TAB_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        drawCenteredString(guiGraphics, font, title, posX, posY + TITLE_OFFSET_Y, TEXTURE_TOTAL_WIDTH, GUI_COLOR_GREY);
        guiGraphics.enableScissor(posX + WIDGET_ZONE_X, posY + WIDGET_ZONE_Y, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH, posY + WIDGET_ZONE_Y + WIDGET_ZONE_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.disableScissor();
        this.renderScrollBar(guiGraphics);
        this.renderNavigationButtons(guiGraphics);
    }

    private void renderScrollBar(@NotNull GuiGraphics guiGraphics){
        if (this.scrollbarActivated()){
            int offset = this.getScrollButtonY();
            guiGraphics.blit(GUI_TEXTURE, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y + offset, TEXTURE_SCROLL_ICON_ON_U, TEXTURE_SCROLL_ICON_ON_V, TEXTURE_SCROLL_ICON_WIDTH, TEXTURE_SCROLL_ICON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        } else {
            guiGraphics.blit(GUI_TEXTURE, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y, TEXTURE_SCROLL_ICON_OFF_U, TEXTURE_SCROLL_ICON_OFF_V, TEXTURE_SCROLL_ICON_WIDTH, TEXTURE_SCROLL_ICON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        }
    }

    private void renderNavigationButtons(@NotNull GuiGraphics guiGraphics){

    }

    private boolean scrollbarActivated() {
        return scrollMaxOffset > 0;
    }

    private void updateWidgetPositions() {
        for (int i = 0; i < widgets.size(); i++) {
            AbstractWidget[] rowWidgets = widgets.get(i);
            int widgetY = i * WIDGET_HEIGHT - scrollOffset;
            if (widgetY + WIDGET_HEIGHT > 0 && widgetY < WIDGET_ZONE_HEIGHT) {
                for (AbstractWidget widget : rowWidgets) {
                    widget.visible = true;
                    widget.setY(posY + WIDGET_ZONE_Y + widgetY);
                }
            } else {
                for (AbstractWidget widget : rowWidgets) {
                    widget.visible = false;
                }
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
            if (this.inInArea(mouseX, mouseY, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y, TEXTURE_SCROLL_ICON_WIDTH, WIDGET_ZONE_HEIGHT)) {
                scrolling = true;
                this.setScrollOffsetFromMouseY(mouseY);
                this.updateWidgetPositions();
                return true;
            }
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
        if (scrolling){
            scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int getScrollButtonY() {
        return (int) (((double) scrollOffset / (double) scrollMaxOffset) * (WIDGET_ZONE_HEIGHT - TEXTURE_SCROLL_ICON_HEIGHT));
    }

    private void setScrollOffsetFromMouseY(double mouseY){
        double newScrollButtonY = Math.min(Math.max(0, mouseY - posY  - SCROLL_ZONE_Y - TEXTURE_SCROLL_ICON_HEIGHT / 2.0D), WIDGET_ZONE_HEIGHT - TEXTURE_SCROLL_ICON_HEIGHT);
        scrollOffset = (int) (scrollMaxOffset * newScrollButtonY / (double) (WIDGET_ZONE_HEIGHT - TEXTURE_SCROLL_ICON_HEIGHT));
    }

    private boolean inInArea(double mouseX, double mouseY, int xStart, int yStart, int width, int height) {
        return mouseX >= (double) xStart && mouseX < (double) (xStart + width) && mouseY >= (double) yStart && mouseY < (double) (yStart + height);
    }

    public abstract void initWidgets();

    /**
     * Adds a row to the screen that contains only a button.
     *
     * @param buttonTextComponent Component that will be displayed on the button.
     * @param onPress             OnPress effect of the button.
     */
    protected void createButton(Component buttonTextComponent, Button.OnPress onPress) {
        Button button = Button.builder(buttonTextComponent, onPress).bounds(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT).build();
        widgets.add(new AbstractWidget[]{button});
        this.addRenderableWidget(button);
    }

    /**
     * Adds a row to the screen that an editBox, with a confirm button next to it.
     *
     * @param editBoxHintComponent Component that will be displayed in the editBox to help the user knowing what to write.
     * @param onPressConfirm       OnPress effect of the confirm button.
     */
    protected void createEditBoxAndConfirm(Component editBoxHintComponent, Button.OnPress onPressConfirm) {
        EditBox editBox = new EditBox(this.font, posX + WIDGET_ZONE_X + 1, 0, WIDGET_ZONE_WIDTH - WIDGET_HEIGHT - 2, WIDGET_HEIGHT - 2, Component.empty()) {
            // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be out of its size...
            @Override
            public void setY(int y) {
                super.setY(y + 1);
            }
        };
        editBox.setHint(editBoxHintComponent);
        IconButton button = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, 83, 166, onPressConfirm);
        widgets.add(new AbstractWidget[]{editBox, button});
        this.addRenderableWidget(editBox);
        this.addRenderableWidget(button);
    }

    private static class IconButton extends Button {
        private final int uOffset;
        private final int vOffset;

        private IconButton(int x, int y, int size, int uOffset, int vOffset, Button.OnPress onPress) {
            super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
            this.uOffset = uOffset;
            this.vOffset = vOffset;
        }

        @Override
        public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            if (this.visible) {
                guiGraphics.blit(GUI_TEXTURE, this.getX(), this.getY(), uOffset, vOffset, this.getWidth(), this.getHeight(), TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
            }
        }
    }

    /* TODO Faire ça pour l'export des schematics.
     * Ajouter un bouton le culture creator pour l'export, qui prépare la zone...
     * Pour chaque colonne de bas en haut : si le bloc == AIR et (max de la colonne ou bloc du dessus == VOID), alors remplace par VOID.
     * Ajouter 2 boutton toggle : AIR -> glass et VOID -> red tainted glass
     * */
}
