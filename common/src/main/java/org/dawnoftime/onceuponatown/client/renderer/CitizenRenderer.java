package org.dawnoftime.onceuponatown.client.renderer;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.CitizenLook;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.model.layer.NpcClothesLayer;
import org.dawnoftime.onceuponatown.client.model.layer.NpcHairLayer;
import org.dawnoftime.onceuponatown.entity.Citizen;

/**
 * Renders a citizen on the mod's villager-shaped rig rather than on vanilla's.
 *
 * <p>Vanilla's {@code VillagerRenderer} would give the right silhouette and nothing else:
 * {@code VillagerModel} has no arm poses and no armour layer, so a villager cannot be shown
 * holding the tool of its trade and a conscript could never be shown in iron. {@code
 * NpcModel} carries the villager's own mesh offsets on a HumanoidModel base, so we keep the
 * silhouette and gain both.
 *
 * <p>Armour is registered here even though a citizen wears none yet. It is the same layer
 * the builder already had, it costs nothing while the slots are empty, and it is the whole
 * reason a garrison will be readable by equipment instead of by seven bespoke textures.
 */
public class CitizenRenderer extends HumanoidMobRenderer<Citizen, NpcModel<Citizen>> {

    // RETIRED, and kept only so the six files it names are not orphaned silently. Nothing reads
    // SKINS any more — see getTextureLocation. Removing this, and the twelve retired PNGs, is a
    // separate decision for whoever owns the asset list.
    private static final ResourceLocation[] SKINS = new ResourceLocation[Citizen.SKIN_VARIANTS];

    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = ResourceLocation.fromNamespaceAndPath(
                Constants.MOD_ID, "textures/entity/npc/citizen_skin_" + i + ".png");
        }
    }

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel<>(context.bakeLayer(NpcModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new NpcClothesLayer<>(this));
        // Hair, beard and headwear: PAINT on the `hat` cube the rig already carries, not
        // geometry. 31 of 31 reference skins use the head's second layer; the cubes this
        // replaces also cost a black screen. See NpcHairLayer.
        this.addLayer(new NpcHairLayer<>(this));
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    /**
     * The same drawn body pool every other citizen uses.
     *
     * <p>This used to index {@code citizen_skin_0..5} off the synced {@code DATA_SKIN}, which left
     * two appearance systems in the mod for one cast — the failure this repo keeps paying for, and
     * the reason {@code make_npc_textures --check} spent an afternoon measuring a retired villager
     * layout. {@link CitizenLook} is the single owner now.
     *
     * <p>{@code Citizen.getSkinVariant} is deliberately left alone: it is synced state that a save
     * already holds, and a chief given a chosen body still wants it. It simply is not what picks
     * the texture. Reconciling the two — feeding the synced value into
     * {@link CitizenLook.Look#body()} — is a small follow-up, not a rendering change.
     */
    @Override
    public ResourceLocation getTextureLocation(Citizen citizen) {
        return CitizenLook.body(citizen);
    }
}
