package org.dawnoftime.onceuponatown;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;

public class Ouat {
    public static final String MOD_ID = "onceuponatown";

    public static void init() {
        BlockRegistry.register();
        BlockEntityRegistry.register();
        EntityRegistry.register();
    }

    public static ResourceLocation modResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
