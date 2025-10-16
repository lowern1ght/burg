package org.dawnoftime.onceuponatown.client.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.widgets.WidgetsWorkarounds;
import org.dawnoftime.onceuponatown.menu.ProjectsMenu;
import org.dawnoftime.onceuponatown.network.C2SNpcScreenPacket;
import org.dawnoftime.onceuponatown.network.C2SProjectsScreenPacket;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Pair;

import java.util.ArrayList;
import java.util.List;

public class ProjectsScreen extends NpcScreen<ProjectsMenu> {
    private static final int STORE_X = 12;
    private static final int STORE_Y = 28;
    private static final int QUEUE_X = 146;
    private static final int QUEUE_Y = 28   ;
    private StoreScrollWidget storeScrollWidget;
    private QueueScrollWidget queueScrollWidget;

    public ProjectsScreen(ProjectsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        storeScrollWidget = (new StoreScrollWidget(menu.getBuildings(), leftPos + STORE_X, topPos + STORE_Y,
            118, 122,
            Component.literal("Store")));

        queueScrollWidget = (new QueueScrollWidget(menu.getProjects(), leftPos + QUEUE_X, topPos + QUEUE_Y,
            118, 122,
            Component.literal("Queue")));

        storeScrollWidget.buttons.forEach(this::addWidget);
        queueScrollWidget.buttons.forEach(this::addWidget);

        /*
        addRenderableWidget(Button.builder(Component.literal("Build"), button -> {
            button.setMessage(Component.literal("Build"));
        }).bounds(leftPos + STORE_X, topPos + 153, 50, 16 ).build());

        addRenderableWidget(Button.builder(Component.literal("Remove"), button -> {
            button.setMessage(Component.literal("Remove"));
        }).bounds(leftPos + QUEUE_X, topPos + 153, 50, 16 ).build());

         */

        addRenderableWidget(storeScrollWidget);
        addRenderableWidget(queueScrollWidget);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.literal("Store"), STORE_X + 2, 18, 4210752, false);
        graphics.drawString(font, Component.literal("Queue"), QUEUE_X + 2, 18, 4210752, false);
    }

    @Override
    protected Tab getTab() {
        return Tab.PROJECTS;
    }

    private static class StoreScrollWidget extends AbstractScrollWidget {
        private final List<Button> buttons = new ArrayList<>();

        public StoreScrollWidget(List<Pair<String, ItemStack>> buildings, int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
            for (int i = 0; i < buildings.size(); i++) {
                int finalI = i;
                buttons.add(new BuildingButton(this, buildings.get(i).getA(), buildings.get(i).getB(), x + 2, y + 2 + (i * 20), 114, 20,
                    Ouat.translatable("building", buildings.get(i).getA()),
                    button -> {
                        Ouat.CLIENT.sendToServer(new C2SProjectsScreenPacket(buildings.get(finalI).getA()));
                        Ouat.CLIENT.sendToServer(new C2SNpcScreenPacket(Tab.PROJECTS.ordinal()));
                    }
                ));
            }
        }

        @Override
        protected int getInnerHeight() {
            return buttons.size() * 20 - 4;
        }

        @Override
        protected double scrollRate() {
            return 10;
        }

        @Override
        protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            buttons.forEach(button -> button.render(guiGraphics, mouseX , (int) (mouseY + scrollAmount()), partialTick));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", this.getMessage()));
        }

        private static class BuildingButton extends Button implements WidgetsWorkarounds {
            private final StoreScrollWidget parent;
            private final String buildingId;
            private final ItemStack buildingStack;

            protected BuildingButton(StoreScrollWidget parent, String buildingId, ItemStack buildingStack, int x, int y, int width, int height, Component message, OnPress onPress) {
                this(parent, buildingId, buildingStack, x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
            }

            protected BuildingButton(StoreScrollWidget parent, String buildingId, ItemStack buildingStack, int x, int y, int width, int height, Component message, OnPress onPress, CreateNarration createNarration) {
                super(x, y, width, height, message, onPress, createNarration);
                this.parent = parent;
                this.buildingId = buildingId;
                this.buildingStack = buildingStack;
            }

            @Override
            protected void renderScrollingString(@NotNull GuiGraphics guiGraphics, @NotNull Font font, int padding, int color) {
                int minX = this.getX() + 21;
                int maxX = this.getX() + this.getWidth() - 3;
                renderScrollingString(guiGraphics, font, this.getMessage(), minX, this.getY(), maxX, this.getY() + this.getHeight(), color);
            }

            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                guiGraphics.renderFakeItem(buildingStack, getX() + 4, getY() + 2);
            }

            @Override
            protected boolean clicked(double mouseX, double mouseY) {
                return this.active && this.visible &&
                    mouseX >= (double)this.getX() &&
                    mouseY >= (double)(this.getY() - parent.scrollAmount()) &&
                    mouseX < (double)(this.getX() + this.width) &&
                    mouseY < (double)(this.getY() + this.height - parent.scrollAmount());
            }
        }
    }

    private static class QueueScrollWidget extends AbstractScrollWidget {
        private final List<Button> buttons = new ArrayList<>();

        public QueueScrollWidget(List<Pair<String, ItemStack>> projects, int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
            for (int i = 0; i < projects.size(); i++) {
                int finalI = i;
                buttons.add(new ProjectButton(this, projects.get(i).getA(), projects.get(i).getB(), x + 2, y + 2 + (i * 20), 114, 20,
                    Component.literal(projects.get(i).getA()),
                    button -> button.setMessage(Component.literal("Not done yet"))

                ));
            }
        }

        @Override
        protected int getInnerHeight() {
            return buttons.size() * 20 - 4;
        }

        @Override
        protected double scrollRate() {
            return 10;
        }

        @Override
        protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            buttons.forEach(button -> button.render(guiGraphics, mouseX , (int) (mouseY + scrollAmount()), partialTick));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", this.getMessage()));
        }

        private static class ProjectButton extends Button implements WidgetsWorkarounds {
            private final QueueScrollWidget parent;
            private final String buildingId;
            private final ItemStack buildingStack;

            protected ProjectButton(QueueScrollWidget parent, String buildingId, ItemStack buildingStack, int x, int y, int width, int height, Component message, OnPress onPress) {
                this(parent, buildingId, buildingStack, x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
            }

            protected ProjectButton(QueueScrollWidget parent, String buildingId, ItemStack buildingStack, int x, int y, int width, int height, Component message, OnPress onPress, CreateNarration createNarration) {
                super(x, y, width, height, message, onPress, createNarration);
                this.parent = parent;
                this.buildingId = buildingId;
                this.buildingStack = buildingStack;
            }

            @Override
            protected void renderScrollingString(@NotNull GuiGraphics guiGraphics, @NotNull Font font, int padding, int color) {
                int minX = this.getX() + 21;
                int maxX = this.getX() + this.getWidth() - 3;
                renderScrollingString(guiGraphics, font, this.getMessage(), minX, this.getY(), maxX, this.getY() + this.getHeight(), color);
            }

            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                guiGraphics.renderFakeItem(buildingStack, getX() + 4, getY() + 2);
            }

            @Override
            protected boolean clicked(double mouseX, double mouseY) {
                return this.active && this.visible &&
                    mouseX >= (double)this.getX() &&
                    mouseY >= (double)(this.getY() - parent.scrollAmount()) &&
                    mouseX < (double)(this.getX() + this.width) &&
                    mouseY < (double)(this.getY() + this.height - parent.scrollAmount());
            }
        }
    }
}
