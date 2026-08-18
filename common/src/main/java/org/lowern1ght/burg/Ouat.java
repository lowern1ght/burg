package org.lowern1ght.burg;

import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.registry.BlockEntityRegistry;
import org.lowern1ght.burg.registry.BlockRegistry;
import org.lowern1ght.burg.registry.EntityRegistry;

public class Ouat {
    public static final String MOD_ID = "burg";

    public static void init() {
        BlockRegistry.register();
        BlockEntityRegistry.register();
        EntityRegistry.register();
    }

    public static ResourceLocation modResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
