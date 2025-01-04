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
    private static final ResourceLocation BACKGROUND_TEXTURE = Ouat.modResource("textures/gui/culture_creator.png");
    private static final int TEXTURE_TOTAL_WIDTH = 281;
    private static final int TEXTURE_TOTAL_HEIGHT = 217;
    private static final int TEXTURE_TAB_HEIGHT = 166;
    private static final int TITLE_OFFSET_Y = 8;
    private static final int WIDGET_ZONE_X = 7;
    private static final int WIDGET_ZONE_Y = 20;
    protected static final int WIDGET_ZONE_WIDTH = 254;
    protected static final int WIDGET_ZONE_HEIGHT = 139;
    protected static final int WIDGET_HEIGHT = 20;

    private int posX;
    private int posY;
    private int scrollOffset = 0;
    private int scrollMaxOffset;
    protected final List<AbstractWidget[]> widgets = new ArrayList<>();

    public BaseCCScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        posX = (width - TEXTURE_TOTAL_WIDTH) / 2;
        posY = (height - TEXTURE_TOTAL_HEIGHT) / 2;
        this.initWidgets();
        this.updateWidgetPositions();
        scrollMaxOffset = Math.max(widgets.size() * WIDGET_HEIGHT - WIDGET_ZONE_HEIGHT, 0);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        posX = (width - TEXTURE_TOTAL_WIDTH) / 2;
        posY = (height - TEXTURE_TOTAL_HEIGHT) / 2;
        this.updateWidgetPositions();
        scrollMaxOffset = Math.max(widgets.size() * WIDGET_HEIGHT - WIDGET_ZONE_HEIGHT, 0);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.blit(BACKGROUND_TEXTURE, posX, posY, 0, 0, TEXTURE_TOTAL_WIDTH, TEXTURE_TAB_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        drawCenteredString(guiGraphics, font, title, posX, posY + TITLE_OFFSET_Y, TEXTURE_TOTAL_WIDTH, GUI_COLOR_GREY);
        guiGraphics.enableScissor(posX + WIDGET_ZONE_X, posY + WIDGET_ZONE_Y, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH, posY + WIDGET_ZONE_Y + WIDGET_ZONE_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scrollMaxOffset == 0) {
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
                guiGraphics.blit(BACKGROUND_TEXTURE, this.getX(), this.getY(), uOffset, vOffset, this.getWidth(), this.getHeight(), TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
            }
        }
    }
}
