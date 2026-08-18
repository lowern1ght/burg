package org.lowern1ght.burg.building;

import org.lowern1ght.burg.building.schematic.SchematicBlock;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ConstructionUtils {

    // Sorts blocks bottom-up, layer by layer (Y ASC).
    // Within each Y layer, uses a boustrophedon (snake) pattern: X rows alternate Z direction.
    // This keeps the builder NPC moving forward continuously instead of jumping back to start each row.
    public static void sortBlocks(List<SchematicBlock> blockList) {
        blockList.sort(Comparator.comparingInt(b -> b.localPos().getY()));

        int i = 0;
        while (i < blockList.size()) {
            int y = blockList.get(i).localPos().getY();
            int layerStart = i;
            while (i < blockList.size() && blockList.get(i).localPos().getY() == y) i++;

            List<SchematicBlock> layer = blockList.subList(layerStart, i);
            layer.sort(Comparator.comparingInt((SchematicBlock b) -> b.localPos().getX())
                .thenComparingInt(b -> b.localPos().getZ()));

            // Reverse Z direction on every odd X-row so the NPC snakes through the layer.
            int j = 0;
            int xRowIndex = 0;
            while (j < layer.size()) {
                int x = layer.get(j).localPos().getX();
                int rowStart = j;
                while (j < layer.size() && layer.get(j).localPos().getX() == x) j++;
                if (xRowIndex % 2 == 1) Collections.reverse(layer.subList(rowStart, j));
                xRowIndex++;
            }
        }
    }
}
