package org.dawnoftime.onceuponatown.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class Profession {
    public static Profession BUILDER = new Profession("builder");
    public static Profession FARMER = new Profession("farmer");

    private String id;

    public Profession(String id) {
        this.id = id;
    }

    public Profession createFromDataPack(String cultureId, ResourceLocation rl, ResourceManager manager) {
        return null;
    }

    public String getId() {
        return id;
    }
}
