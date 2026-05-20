package org.dawnoftime.onceuponatown.client.gui.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public class ClientBuildingProductionTooltip implements ClientTooltipComponent {
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE  = 16;
    private static final int GAP        = 3;

    private final BuildingProductionTooltip data;

    public ClientBuildingProductionTooltip(BuildingProductionTooltip data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return data.rows().size() * ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int max = 0;
        for (BuildingProductionTooltip.Row row : data.rows()) {
            int w = ICON_SIZE + GAP + font.width(row.text());
            if (w > max) max = w;
        }
        return max;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        for (int i = 0; i < data.rows().size(); i++) {
            graphics.renderItem(data.rows().get(i).stack(), x, y + i * ROW_HEIGHT + 1);
        }
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix4f, MultiBufferSource.BufferSource bufferSource) {
        for (int i = 0; i < data.rows().size(); i++) {
            BuildingProductionTooltip.Row row = data.rows().get(i);
            font.drawInBatch(
                row.text().getVisualOrderText(),
                x + ICON_SIZE + GAP,
                y + i * ROW_HEIGHT + 5,
                0xFFAAAAAA,
                true,
                matrix4f,
                bufferSource,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
            );
        }
    }
}
