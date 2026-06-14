package org.dawnoftime.onceuponatown.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.dawnoftime.onceuponatown.client.gui.screens.NbtPreviewScreen;

public class NbtPreviewClientState {

    public static void open(CompoundTag data) {
        Minecraft.getInstance().setScreen(new NbtPreviewScreen());
    }
}
