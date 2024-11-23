package org.dawnoftime.onceuponatown.building.type;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;

import java.util.HashMap;

public class SliceBuildType extends BuildType {
    private final HashMap<String, BuildVariant> slab_variants = new HashMap<>();
    private final HashMap<String, BuildVariant> stairs_variants = new HashMap<>();
    private int width;
    private int patternLength;

    public SliceBuildType(String buildTypeName) {
        super(buildTypeName, 0);
    }

    public int getPatternLength() {
        return this.patternLength;
    }

    public int getWidth() {
        return this.width;
    }

    @Override
    public void addVariant(BuildVariant variant, String shape, String cultureId) {
        if(this.width == 0){
            this.width = variant.getSize().getX();
            this.patternLength = variant.getSize().getZ();
        }else{
            if(this.width != variant.getSize().getX() || this.patternLength != variant.getSize().getZ()){
                Ouat.error("Culture [%s]: Failed to register a build_variant. Every build_variant associated with '%s' must have the same width and length.".formatted(cultureId, this.getName()));
                return;
            }
        }
        switch (shape) {
            case "flat" -> super.addVariant(variant, shape, cultureId);
            case "slab" -> this.slab_variants.put(variant.getName(), variant);
            case "stairs" ->  this.stairs_variants.put(variant.getName(), variant);
        }
    }

    @Override
    public boolean isNotValid(String cultureId) {
        if(this.getVariants().isEmpty()){
            throw new CorruptedCultureException("Culture [%s]: Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'flat'.".formatted(cultureId, this.getName()));
        }
        if(this.slab_variants.isEmpty()){
            throw new CorruptedCultureException("Culture [%s]: Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'slab'.".formatted(cultureId, this.getName()));
        }
        if(this.stairs_variants.isEmpty()) {
            throw new CorruptedCultureException("Culture [%s]: Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'stairs'.".formatted(cultureId, this.getName()));
        }
        return false;
    }
}
