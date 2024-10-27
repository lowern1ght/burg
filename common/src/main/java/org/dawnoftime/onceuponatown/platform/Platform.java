package org.dawnoftime.onceuponatown.platform;

import java.util.ServiceLoader;

public class Platform {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * Load a service for the current environment. Your implementation of the service must be defined
     * manually by including a text file in META-INF/services named with the fully qualified class name of the service.
     * @param clazz Class of the common element that is implemented differently depending on the platform.
     * @return An instance of the given class.
     * @param <T> Class studied.
     */
    public static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
    }
}