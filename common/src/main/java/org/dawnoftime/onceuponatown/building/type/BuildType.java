package org.dawnoftime.onceuponatown.building.type;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;

import java.util.HashMap;

public abstract class BuildType {
    private final String name;
    private final int townClutter;
    private final HashMap<String, BuildVariant> variants = new HashMap<>();

    protected BuildType(String name, int townClutter) {
        this.name = name;
        this.townClutter = townClutter;
    }

    public String getName() {
        return this.name;
    }

    public void addVariant(BuildVariant variant, String shape, String cultureId) {
        this.variants.put(variant.getName(), variant);
    }

    public HashMap<String, BuildVariant> getVariants() {
        return this.variants;
    }

    public boolean isNotValid(String cultureId) {
        if (this.variants.isEmpty()) {
            Ouat.error("Culture [%s]: Canceled the registration of the build_type '%s' as it doesn't have any build_variant.".formatted(cultureId, this.getName()));
            return true;
        }
        return false;
    }
}
