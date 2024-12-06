package org.dawnoftime.onceuponatown.building.type;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import static org.dawnoftime.onceuponatown.town.generation.ProtoTown.RANDOM_SOURCE;

public class SliceBuildType extends BuildType {
    private final HashMap<String, BuildVariant> slabVariants = new HashMap<>();
    private final HashMap<String, BuildVariant> stairsVariants = new HashMap<>();
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
        try {
            if (this.width == 0) {
                this.width = variant.getSize().getX();
                this.patternLength = variant.getSize().getZ();
            } else {
                if (this.width != variant.getSize().getX() || this.patternLength != variant.getSize().getZ()) {
                    throw new CorruptedCultureException(cultureId, "Failed to register a build_variant. Every build_variant associated with '%s' must have the same width and length.".formatted(this.getName()));
                }
            }
            switch (SliceBuildShape.fromString(cultureId, variant.getName(), shape)) {
                case FLAT -> super.addVariant(variant, shape, cultureId);
                case SLAB -> this.slabVariants.put(variant.getName(), variant);
                case STAIRS -> this.stairsVariants.put(variant.getName(), variant);
            }
        }catch(CorruptedCultureException e){
            Ouat.error(e.getMessage());
        }
    }

    public BuildVariant getVariant(SliceBuildShape shape, String variantName){
        return switch(shape){
            case FLAT -> this.getVariants().get(variantName);
            case SLAB -> this.slabVariants.get(variantName);
            case STAIRS -> this.stairsVariants.get(variantName);
        };
    }

    public String getRandomVariantName(SliceBuildShape shape){
        Set<String> keys = switch(shape){
            case FLAT -> this.getVariants().keySet();
            case SLAB -> this.slabVariants.keySet();
            case STAIRS -> this.stairsVariants.keySet();
        };
        return new ArrayList<>(keys).get(RANDOM_SOURCE.nextInt(keys.size()));
    }

    @Override
    public boolean isNotValid(String cultureId) {
        if(this.getVariants().isEmpty()){
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'flat'.".formatted(this.getName()));
        }
        if(this.slabVariants.isEmpty()){
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'slab'.".formatted(this.getName()));
        }
        if(this.stairsVariants.isEmpty()) {
            throw new CorruptedCultureException(cultureId, "Failed to load a culture. You need to define at least one build_variant for the build_type '%s' with 'shape': 'stairs'.".formatted(this.getName()));
        }
        return false;
    }

    public enum SliceBuildShape{
        FLAT("flat", 0),
        SLAB("slab", 0.5F),
        STAIRS("stairs", 1.0F);

        private final String shapeName;
        private final float slope;

        SliceBuildShape(String shapeName, float slope){
            this.shapeName = shapeName;
            this.slope = slope;
        }

        public String getShapeName() {
            return this.shapeName;
        }

        public int getYSize(int patternLength, int totalSizeY){
            return getMaxYForSliceSchematic(0, patternLength, totalSizeY) + 1;
        }

        public int getMinYForSliceSchematic(int patternPos){
            return (int) Math.floor(patternPos * this.slope);
        }

        public int getMaxYForSliceSchematic(int patternPos, int patternLength, int totalSizeY){
            return totalSizeY - (int) Math.floor((patternLength - 1 - patternPos) * this.slope) - 1;
        }

        public static SliceBuildShape fromString(String cultureId, String variantName, String shapeName) {
            for (SliceBuildShape shape : values()) {
                if (shape.shapeName.equals(shapeName)) {
                    return shape;
                }
            }
            throw new CorruptedCultureException(cultureId, "Failed to register a build_variant '%s'. The accepted shape are ['flat', 'slab' or 'stairs'], and not '%s'.".formatted(variantName, shapeName));
        }
    }
}
