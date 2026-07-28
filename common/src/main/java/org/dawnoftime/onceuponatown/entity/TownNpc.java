package org.dawnoftime.onceuponatown.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * What {@code NpcModel} needs of an entity, so the one rig can serve more than one class.
 *
 * <p>The rig is worth sharing deliberately. {@code NpcModel} is built on
 * {@code HumanoidModel} but its mesh carries the VANILLA VILLAGER offsets — nose at
 * texOffs(24,0), robe at (0,38), crossed arms at (40,38) — so it looks like a villager
 * while inheriting arm poses and the armour layer, neither of which vanilla's own
 * {@code VillagerModel} has. A citizen rendered with it is villager-shaped and can still
 * hold a tool and wear a helmet; rendered with vanilla's model it could do neither.
 *
 * <p>Extracted from {@code Npc} rather than invented: these are exactly the members
 * {@code NpcModel.setupAnim} and {@code prepareMobModel} were already reading off the
 * builder. The client animation cursor is part of the contract because the model stores
 * its per-frame state on the entity instead of in the model, which is how vanilla does
 * it too.
 */
public interface TownNpc {

    /** Reading the town plan: both hands occupied, head tilted down. */
    boolean isReading();

    /** Arms folded across the chest — vanilla's unemployed-villager pose. */
    boolean isCrossingArms();

    /** Bumped on every block placed; the model diffs it to fire the swing. */
    int getBuildGeneration();

    /**
     * The clothing overlay drawn over the skin, or {@code null} for bare.
     *
     * <p>Per entity, not per class: one villager-shaped rig carries the whole cast, and
     * what tells a mason from a chief is this texture plus whatever they are holding.
     */
    ResourceLocation clothesTexture();

    // --- client-side animation cursor, never synced or saved ---

    int getClientLastBuildGeneration();

    void setClientLastBuildGeneration(int value);

    float getClientBuildPlacedAtAge();

    void setClientBuildPlacedAtAge(float value);
}
