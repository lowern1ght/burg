package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.WidgetCC;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.client.gui.GuiUtils.GUI_COLOR_GREY;
import static org.dawnoftime.onceuponatown.client.gui.GuiUtils.drawCenteredString;

public abstract class BaseCCScreen extends Screen {
    public static final ResourceLocation GUI_TEXTURE = Ouat.modResource("textures/gui/culture_creator.png");
    public static final int TEXTURE_TOTAL_WIDTH = 240;
    public static final int TEXTURE_TOTAL_HEIGHT = 217;
    private static final int TEXTURE_TAB_HEIGHT = 166;
    private static final int TEXTURE_SCROLL_ICON_WIDTH = 8;
    private static final int TEXTURE_SCROLL_ICON_HEIGHT = 27;
    private static final int TEXTURE_NAVIGATION_OFF_WIDTH = 59;
    private static final int TEXTURE_NAVIGATION_OFF_HEIGHT = 15;
    private static final int TEXTURE_NAVIGATION_ON_WIDTH = 66;
    private static final int TEXTURE_NAVIGATION_ON_HEIGHT = 21;
    private static final int TITLE_OFFSET_Y = 8;
    private static final int SCROLL_ZONE_X = 225;
    private static final int SCROLL_ZONE_Y = 20;
    public static final int WIDGET_ZONE_X = 8;
    private static final int WIDGET_ZONE_Y = 20;
    public static final int WIDGET_ZONE_WIDTH = 211;
    private static final int WIDGET_ZONE_HEIGHT = 139;
    public static final int WIDGET_HEIGHT = 20;
    private static final int WIDGET_HEIGHT_PADDED = WIDGET_HEIGHT + 1;
    private static final int WIDGET_PLUS_BUTTON_HEIGHT = 12;
    private static final int FOLDER_BUTTON_X = 224;
    private static final int FOLDER_BUTTON_Y = 6;
    private static final int FOLDER_BUTTON_SIDE_LENGTH = 10;
    private static final int NAVIGATION_ZONE_Y = 19;
    private static final int MAX_NAVIGATION_NUMBER = 8;

    int posX;
    private int posY;
    private int scrollOffset = 0;
    private int scrollMaxOffset;
    boolean scrolling = false;
    protected final LinkedHashMap<String, WidgetCC> widgets = new LinkedHashMap<>();
    private IconButton folderButton;
    protected List<NavigationTab> navigationTabList;

    public BaseCCScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        navigationTabList = this.createNavigationMap();
        posX = (width - TEXTURE_TOTAL_WIDTH) / 2;
        posY = (height - TEXTURE_TOTAL_HEIGHT) / 2;
        // Since this function is also called on resize, we have to reset the widgets.
        widgets.clear();
        // Creates the button to open the file explorer at the current level.
        Path folderPath = this.getDirectoryPath();
        folderButton = new IconButton(posX + FOLDER_BUTTON_X, posY + FOLDER_BUTTON_Y, FOLDER_BUTTON_SIDE_LENGTH, GUI_TEXTURE, 83, 186, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT,
                btn -> Util.getPlatform().openUri(folderPath.toUri()));
        this.addWidget(folderButton);
        // Finally, create the widgets specific to the screen.
        this.initWidgets();
        this.updateWidgetPositions();
        scrollMaxOffset = Math.max(widgets.size() * WIDGET_HEIGHT_PADDED - WIDGET_ZONE_HEIGHT, 0);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        guiGraphics.blit(GUI_TEXTURE, posX, posY, 0, 0, TEXTURE_TOTAL_WIDTH, TEXTURE_TAB_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        drawCenteredString(guiGraphics, font, title, posX, posY + TITLE_OFFSET_Y, TEXTURE_TOTAL_WIDTH, GUI_COLOR_GREY);
        guiGraphics.enableScissor(posX + WIDGET_ZONE_X, posY + WIDGET_ZONE_Y, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH, posY + WIDGET_ZONE_Y + WIDGET_ZONE_HEIGHT);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.disableScissor();
        this.folderButton.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderScrollBar(guiGraphics);
        this.renderNavigationButtons(guiGraphics);
    }

    private void renderScrollBar(@NotNull GuiGraphics guiGraphics){
        if (this.scrollbarActivated()){
            int offset = this.getScrollButtonY();
            guiGraphics.blit(GUI_TEXTURE, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y + offset, 67, 166, TEXTURE_SCROLL_ICON_WIDTH, TEXTURE_SCROLL_ICON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        } else {
            guiGraphics.blit(GUI_TEXTURE, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y, 75, 166, TEXTURE_SCROLL_ICON_WIDTH, TEXTURE_SCROLL_ICON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
        }
    }

    private void renderNavigationButtons(@NotNull GuiGraphics guiGraphics){
        int navigationNumber = navigationTabList.size();
        int start = Math.max(0, navigationNumber - MAX_NAVIGATION_NUMBER);
        for (int i = start; i < navigationNumber; i++) {
            int offsetY = (i - start) * TEXTURE_NAVIGATION_OFF_HEIGHT;
            if (i == navigationNumber - 1) {
                // Last ON button
                int x = posX - TEXTURE_NAVIGATION_ON_WIDTH + 3;
                int y = posY + NAVIGATION_ZONE_Y + offsetY;
                guiGraphics.blit(GUI_TEXTURE, x, y, 0, 196, TEXTURE_NAVIGATION_ON_WIDTH, TEXTURE_NAVIGATION_ON_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
                drawCenteredString(guiGraphics, font, navigationTabList.get(i).displayName(), x, y + 7, TEXTURE_NAVIGATION_ON_WIDTH, GUI_COLOR_GREY);
            } else {
                if (i == start) {
                    // First OFF button
                    guiGraphics.blit(GUI_TEXTURE, posX - TEXTURE_NAVIGATION_OFF_WIDTH, posY + NAVIGATION_ZONE_Y + offsetY, 5, 166, TEXTURE_NAVIGATION_OFF_WIDTH, TEXTURE_NAVIGATION_OFF_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
                } else {
                    // All the other OFF buttons
                    guiGraphics.blit(GUI_TEXTURE, posX - TEXTURE_NAVIGATION_OFF_WIDTH, posY + NAVIGATION_ZONE_Y + offsetY, 5, 181, TEXTURE_NAVIGATION_OFF_WIDTH, TEXTURE_NAVIGATION_OFF_HEIGHT, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT);
                }
                drawCenteredString(guiGraphics, font, navigationTabList.get(i).displayName(), posX - TEXTURE_NAVIGATION_OFF_WIDTH, posY + NAVIGATION_ZONE_Y + offsetY + 6, TEXTURE_NAVIGATION_OFF_WIDTH, GUI_COLOR_GREY);
            }
        }
    }

    private boolean scrollbarActivated() {
        return scrollMaxOffset > 0;
    }

    private void updateWidgetPositions() {
        String[] widgetIds = widgets.keySet().toArray(new String[0]);
        for (int i = 0; i < widgetIds.length; i++) {
            int widgetY = i * WIDGET_HEIGHT_PADDED - scrollOffset;
            if (widgetY + WIDGET_HEIGHT_PADDED > 0 && widgetY < WIDGET_ZONE_HEIGHT) {
                for (AbstractWidget widget : widgets.get(widgetIds[i]).getWidgets()) {
                    widget.visible = true;
                    widget.setY(posY + WIDGET_ZONE_Y + widgetY);
                }
            } else {
                for (AbstractWidget widget : widgets.get(widgetIds[i]).getWidgets()) {
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
            if (mouseInArea(mouseX, mouseY, posX + SCROLL_ZONE_X, posY + SCROLL_ZONE_Y, TEXTURE_SCROLL_ICON_WIDTH, WIDGET_ZONE_HEIGHT)) {
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
        int navigationButtons = Math.min(navigationTabList.size() - 1, MAX_NAVIGATION_NUMBER - 1); // The last button can not be clicked.
        if (navigationButtons > 0) {
            if (mouseInArea(mouseX, mouseY, posX - TEXTURE_NAVIGATION_OFF_WIDTH, posY + NAVIGATION_ZONE_Y, TEXTURE_NAVIGATION_OFF_WIDTH, TEXTURE_NAVIGATION_OFF_HEIGHT * navigationButtons)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                int targetedButton = (int) ((mouseY - posY - NAVIGATION_ZONE_Y) / TEXTURE_NAVIGATION_OFF_HEIGHT);
                targetedButton += Math.max(0, navigationTabList.size() - MAX_NAVIGATION_NUMBER);
                Ouat.CLIENT.sendToServer(navigationTabList.get(targetedButton).packetSupplier().get());
            }
        } else if (scrolling) {
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

    private Path getDirectoryPath() throws InvalidPathException {
        Path path = Ouat.COMMON.getConfigFolder().toPath().resolve(MOD_ID);
        for (NavigationTab navigation : navigationTabList){
            String folder = navigation.folderName();
            if (folder != null) {
                path = path.resolve(folder);
            }
        }
        return path;
    }

    /**
     * Function used to initialize the value of the navigation map. This map defines the name of the lateral button, and
     * a supplier that returns a packet that can be used to switch screen.
     * @return A LinkedHashMap that associate a button name to a packet. The values are ordered following the button order.
     */
    public abstract List<NavigationTab> createNavigationMap();

    /**
     * Function that defines all the widgets in the screen, used to create or modify the culture parameters.
     */
    public abstract void initWidgets();

    protected WidgetCC addWidget(String id, WidgetCC widget) {
        widgets.put(id, widget);
        for (AbstractWidget w : widget.getWidgets()) {
            this.addRenderableWidget(w);
        }
        return widget;
    }

    public static boolean mouseInArea(double mouseX, double mouseY, int xStart, int yStart, int width, int height) {
        return mouseX >= (double) xStart && mouseX < (double) (xStart + width) && mouseY >= (double) yStart && mouseY < (double) (yStart + height);
    }

    public record NavigationTab(@Nullable String folderName, Component displayName, Supplier<OuatPacket> packetSupplier) {}

    /* TODO Faire ça pour l'export des schematics.
     * Ajouter un bouton le culture creator pour l'export, qui prépare la zone...
     * Pour chaque colonne de bas en haut : si le bloc == AIR et (max de la colonne ou bloc du dessus == VOID), alors remplace par VOID.
     * Ajouter 2 boutton toggle : AIR -> glass et VOID -> red tainted glass
     * */
}
