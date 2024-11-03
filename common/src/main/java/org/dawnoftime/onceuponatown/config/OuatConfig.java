package org.dawnoftime.onceuponatown.config;

public class OuatConfig {

    // Prevent vanilla villages from spawning. Existing villages will be untouched, new ones won't generate
    private static final FakeConfig<Boolean> DISABLE_VANILLA_VILLAGES = new FakeConfig<>(true);

    private record FakeConfig<T>(T configValue) {
        public T get() {
            return this.configValue;
        }
    }
}
