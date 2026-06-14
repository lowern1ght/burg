package org.dawnoftime.onceuponatown.client.gui.widgets;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class NbtIsoRenderer {

    private static final int S = 16;
    private static final int H = S / 2;  // 8
    private static final int Q = S / 4;  // 4

    private final List<int[]> sortedBlocks;
    private final List<String> palette;

    public NbtIsoRenderer(CompoundTag data) {
        this.palette = parsePalette(data);
        this.sortedBlocks = parseAndSortBlocks(data);
    }

    public int getBlockCount() {
        return sortedBlocks.size();
    }

    private static List<String> parsePalette(CompoundTag data) {
        ListTag raw = data.getList("palette", Tag.TAG_COMPOUND);
        List<String> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            result.add(raw.getCompound(i).getString("n"));
        }
        return result;
    }

    private List<int[]> parseAndSortBlocks(CompoundTag data) {
        ListTag raw = data.getList("blocks", Tag.TAG_INT_ARRAY);
        List<int[]> list = new ArrayList<>();
        for (Tag t : raw) {
            int[] arr = ((IntArrayTag) t).getAsIntArray();
            if (arr.length < 4) continue;
            int state = arr[3];
            if (state < 0 || state >= palette.size()) continue;
            String name = palette.get(state);
            if ("minecraft:air".equals(name) || "minecraft:jigsaw".equals(name)) continue;
            list.add(arr);
        }
        list.sort((a, b) -> {
            int da = a[0] + a[2], db = b[0] + b[2];
            return da != db ? Integer.compare(da, db) : Integer.compare(a[1], b[1]);
        });
        return list;
    }

    // Renders into GuiGraphics' own buffer (RenderType.gui), so ordering vs background is correct.
    public void render(GuiGraphics g, int contentX, int contentY, int contentW, int contentH) {
        if (sortedBlocks.isEmpty()) return;

        int originX = contentX + contentW / 2;
        int originY = contentY + contentH / 2;

        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());

        for (int[] block : sortedBlocks) {
            int bx = block[0], by = block[1], bz = block[2];
            int[] colors = colorForBlock(palette.get(block[3]));
            if (colors == null) continue;

            int px = originX + (bx - bz) * H;
            int py = originY + (bx + bz) * Q - by * H;

            // Top face (rhombus)
            quad(vc, m,
                px,      py - Q,
                px + H,  py,
                px,      py + Q,
                px - H,  py,
                colors[0]);

            // Left face
            quad(vc, m,
                px - H,  py,
                px,      py + Q,
                px,      py + Q + H,
                px - H,  py + H,
                colors[1]);

            // Right face
            quad(vc, m,
                px,      py + Q,
                px + H,  py,
                px + H,  py + H,
                px,      py + Q + H,
                colors[2]);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                              float x1, float y1, float x2, float y2,
                              float x3, float y3, float x4, float y4,
                              int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int gv = (argb >> 8)  & 0xFF;
        int b  =  argb        & 0xFF;
        vc.vertex(m, x1, y1, 0).color(r, gv, b, a).endVertex();
        vc.vertex(m, x2, y2, 0).color(r, gv, b, a).endVertex();
        vc.vertex(m, x3, y3, 0).color(r, gv, b, a).endVertex();
        vc.vertex(m, x4, y4, 0).color(r, gv, b, a).endVertex();
    }

    private static int[] colorForBlock(String name) {
        return switch (name) {
            case "minecraft:grass_block"      -> new int[]{0xFF5BA828, 0xFF4A7E1C, 0xFF366012};
            case "minecraft:grass",
                 "minecraft:tall_grass"       -> new int[]{0xFF4E8C1B, 0xFF3D6E14, 0xFF2E530F};
            case "minecraft:dirt"             -> new int[]{0xFF8B5E3C, 0xFF6E4A2E, 0xFF523722};
            case "minecraft:coarse_dirt"      -> new int[]{0xFF7A5030, 0xFF5E3C24, 0xFF472D1A};
            case "minecraft:rooted_dirt"      -> new int[]{0xFF7A5030, 0xFF5E3C24, 0xFF472D1A};
            case "minecraft:podzol"           -> new int[]{0xFF5C3A1E, 0xFF482D18, 0xFF362212};
            case "minecraft:stone"            -> new int[]{0xFF808080, 0xFF606060, 0xFF484848};
            case "minecraft:stone_slab",
                 "minecraft:cobblestone_slab" -> new int[]{0xFF909090, 0xFF686868, 0xFF505050};
            case "minecraft:cobblestone"      -> new int[]{0xFF707070, 0xFF585858, 0xFF404040};
            case "minecraft:mossy_cobblestone"-> new int[]{0xFF607060, 0xFF4A5A4A, 0xFF384438};
            case "minecraft:gravel"           -> new int[]{0xFF8A8078, 0xFF6B6360, 0xFF504B48};
            case "minecraft:sand"             -> new int[]{0xFFD2B06A, 0xFFA88A52, 0xFF7E693D};
            case "minecraft:sandstone"        -> new int[]{0xFFD4C06A, 0xFFA89852, 0xFF7E723D};
            case "minecraft:oak_log",
                 "minecraft:oak_wood"         -> new int[]{0xFF8B6914, 0xFF6E530F, 0xFF523D0A};
            case "minecraft:oak_planks"       -> new int[]{0xFFCBA135, 0xFFA07D28, 0xFF7A5F1E};
            case "minecraft:oak_leaves"       -> new int[]{0xFF2E6B1A, 0xFF225212, 0xFF193D0D};
            case "minecraft:oak_slab"         -> new int[]{0xFFCBA135, 0xFFA07D28, 0xFF7A5F1E};
            case "minecraft:spruce_log",
                 "minecraft:spruce_wood"      -> new int[]{0xFF5C4010, 0xFF47310C, 0xFF362508};
            case "minecraft:spruce_planks"    -> new int[]{0xFF8B6630, 0xFF6E5025, 0xFF523C1C};
            case "minecraft:spruce_leaves"    -> new int[]{0xFF1E4D0D, 0xFF173B0A, 0xFF112D07};
            case "minecraft:birch_log"        -> new int[]{0xFFD4D4C0, 0xFFA8A898, 0xFF7E7E72};
            case "minecraft:birch_planks"     -> new int[]{0xFFD4C890, 0xFFA89E70, 0xFF7E7654};
            case "minecraft:birch_leaves"     -> new int[]{0xFF5A8030, 0xFF476428, 0xFF364C1E};
            case "minecraft:dark_oak_log"     -> new int[]{0xFF3A2A10, 0xFF2D200C, 0xFF221808};
            case "minecraft:dark_oak_planks"  -> new int[]{0xFF4A3010, 0xFF38240C, 0xFF2A1C08};
            case "minecraft:jungle_log"       -> new int[]{0xFF6B5418, 0xFF554212, 0xFF3F310D};
            case "minecraft:acacia_log"       -> new int[]{0xFF8C6040, 0xFF6E4C30, 0xFF523924};
            case "minecraft:stone_bricks",
                 "minecraft:stone_brick_slab" -> new int[]{0xFF888888, 0xFF686868, 0xFF505050};
            case "minecraft:bricks"           -> new int[]{0xFFA05040, 0xFF7E3C30, 0xFF5E2C24};
            case "minecraft:planks"           -> new int[]{0xFFCBA135, 0xFFA07D28, 0xFF7A5F1E};
            case "minecraft:water"            -> new int[]{0xFF2466BB, 0xFF1C4E93, 0xFF153A6E};
            case "minecraft:lava"             -> new int[]{0xFFDD6010, 0xFFAA480C, 0xFF803508};
            case "minecraft:snow",
                 "minecraft:snow_block"       -> new int[]{0xFFEEEEEE, 0xFFCCCCCC, 0xFFAAAAAA};
            case "minecraft:ice"              -> new int[]{0xFF7AACCC, 0xFF5E88A4, 0xFF46667C};
            case "minecraft:netherrack"       -> new int[]{0xFF7A2020, 0xFF5E1818, 0xFF481212};
            case "minecraft:soul_sand"        -> new int[]{0xFF4A3828, 0xFF3A2C20, 0xFF2C2018};
            case "minecraft:glowstone"        -> new int[]{0xFFEEB830, 0xFFBB9024, 0xFF8C6C1A};
            case "minecraft:pumpkin"          -> new int[]{0xFFE0781C, 0xFFB05E16, 0xFF844710};
            case "minecraft:hay_block"        -> new int[]{0xFFCCA820, 0xFFA08418, 0xFF786312};
            case "minecraft:tnt"              -> new int[]{0xFFCC3333, 0xFFA02828, 0xFF781E1E};
            case "minecraft:crafting_table"   -> new int[]{0xFF8B6914, 0xFF6E530F, 0xFF523D0A};
            case "minecraft:furnace",
                 "minecraft:blast_furnace"    -> new int[]{0xFF707070, 0xFF585858, 0xFF404040};
            case "minecraft:chest",
                 "minecraft:trapped_chest"    -> new int[]{0xFFAA8833, 0xFF886A28, 0xFF64501E};
            case "minecraft:bookshelf"        -> new int[]{0xFF8B6914, 0xFF6E530F, 0xFF523D0A};
            case "minecraft:white_wool"       -> new int[]{0xFFDDDDDD, 0xFFBBBBBB, 0xFF999999};
            case "minecraft:air",
                 "minecraft:cave_air",
                 "minecraft:void_air",
                 "minecraft:jigsaw"           -> null;
            default                           -> new int[]{0xFFAA44AA, 0xFF882288, 0xFF661A66};
        };
    }
}
