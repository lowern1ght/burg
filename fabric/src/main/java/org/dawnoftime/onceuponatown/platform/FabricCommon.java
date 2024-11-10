package org.dawnoftime.onceuponatown.platform;

import net.fabricmc.loader.api.FabricLoader;
import org.dawnoftime.onceuponatown.Common;

public class FabricCommon implements Common {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
