package org.dawnoftime.onceuponatown.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import org.dawnoftime.onceuponatown.client.gui.screens.TownScrollScreen;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.*;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

public class ClientUtils {
    public static void openTownMapItemScreen(CompoundTag packetTag) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new TownScrollScreen(packetTag)));
    }

    public static void openBuildingCCScreen(S2COpenBuildingCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new BuildingCCScreen(packet)));
    }

    public static void openBuildingsCCScreen(S2COpenBuildingsCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new BuildingsCCScreen(packet)));
    }

    public static void openCultureCCScreen(S2COpenCultureCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new CultureCCScreen(packet)));
    }

    public static void openCulturesCCScreen(S2COpenCulturesCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new CulturesCCScreen(packet)));
    }

    public static void openLevelsCCScreen(S2COpenLevelsCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new LevelsCCScreen(packet)));
    }

    public static void openLevelCCScreen(S2COpenLevelCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new LevelCCScreen(packet)));
    }

    public static void openVariantsCCScreen(S2COpenVariantsCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new VariantsCCScreen(packet)));
    }

    public static void openVariantLevelsCCScreen(S2COpenVariantLevelsCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new VariantLevelsCCScreen(packet)));
    }

    public static void openVariantLevelCCScreen(S2COpenVariantLevelCCScreenPacket packet) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new VariantLevelCCScreen(packet)));
    }
}
