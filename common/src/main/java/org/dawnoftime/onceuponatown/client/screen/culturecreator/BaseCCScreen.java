package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.dawnoftime.onceuponatown.Ouat;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.client.screen.ScreenUtils.GUI_COLOR_GREY;
import static org.dawnoftime.onceuponatown.client.screen.ScreenUtils.drawCenteredString;

public abstract class BaseCCScreen extends Screen {
    private static final ResourceLocation BACKGROUND_TEXTURE = Ouat.createOuatResource("textures/gui/culture_creator.png");
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
    protected final List<AbstractWidget> widgets = new ArrayList<>();

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
        if(scrollMaxOffset == 0){
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
            AbstractWidget widget = widgets.get(i);
            int widgetY = i * WIDGET_HEIGHT - scrollOffset;
            if(widgetY + WIDGET_HEIGHT > 0 && widgetY < WIDGET_ZONE_HEIGHT){
                widget.visible = true;
                widget.setY(posY + WIDGET_ZONE_Y + widgetY);
            }else{
                widget.visible = false;
            }
        }
    }

    protected void createButton(Component component,  Button.OnPress onPress){
        Button button = Button.builder(component, onPress).bounds(posX + WIDGET_ZONE_X, posY + WIDGET_ZONE_Y, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT).build();
        widgets.add(button);
        this.addRenderableWidget(button);
    }

    public abstract void initWidgets();
}
