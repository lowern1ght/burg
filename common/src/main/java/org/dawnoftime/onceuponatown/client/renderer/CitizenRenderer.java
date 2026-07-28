package org.dawnoftime.onceuponatown.client.renderer;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.model.layer.NpcClothesLayer;
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

    // One per base skin, resolved once. Vanilla varies a villager the same way, by its TYPE;
    // the only difference is that ours are recolours of one drawn skin, not seven drawings.
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
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(Citizen citizen) {
        return SKINS[citizen.getSkinVariant()];
    }
}
