package org.dawnoftime.onceuponatown;

public class OuatConfig {

    // Prevent vanilla villages from spawning. Existing villages will be untouched, new ones won't generate
    public static final FakeConfig<Boolean> DISABLE_VANILLA_VILLAGES = new FakeConfig<>(true);

    public record FakeConfig<T>(T configValue) {
        public T get() {
            return this.configValue;
        }
    }
}
