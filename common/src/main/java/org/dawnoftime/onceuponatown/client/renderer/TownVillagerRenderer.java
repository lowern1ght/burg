package org.dawnoftime.onceuponatown.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.model.layer.NpcClothesLayer;
import org.dawnoftime.onceuponatown.entity.citizen.Citizens;

/**
 * Renders every {@code minecraft:villager} — ours on the mod's rig, everyone else's on
 * vanilla's.
 *
 * <p><b>Why this replaces vanilla's renderer rather than adding one.</b> Citizens ARE
 * villagers, so there is no separate entity type left to hang a renderer on. Registering for
 * {@code EntityType.VILLAGER} is supported ({@code EntityRenderersEvent.RegisterRenderers}
 * will happily take an override) and needs no mixin.
 *
 * <p><b>Why it still falls back.</b> The blast radius is every villager in the world,
 * including the ones in villages the player found and the seven raw villagers that ship inside
 * the author's own NBTs. If all of them came out on our rig then belonging to a town would
 * stop being visible, which is the one thing the rig is there to show. So a non-citizen is
 * handed to a real {@code VillagerRenderer} held here for the purpose: strangers look exactly
 * as vanilla draws them, biome skin and profession overlay and all, because that IS vanilla
 * drawing them.
 *
 * <p>{@code NpcModel} is what ours gain by it — a villager silhouette that also has arm poses
 * and an armour layer, neither of which vanilla's {@code VillagerModel} has. That is what lets
 * a citizen hold the tool of its trade today and a conscript wear iron later.
 */
public class TownVillagerRenderer extends HumanoidMobRenderer<Villager, NpcModel<Villager>> {

    // One per base skin, resolved once. Vanilla varies a villager the same way, by its TYPE;
    // the only difference is that ours are recolours of one drawn skin, not seven drawings.
    private static final ResourceLocation[] MEN = skins("citizen_skin_");

    /**
     * The same six complexions, on a woman.
     *
     * <p>Half of every town has always been female — {@link Citizens#isFemale} is the coin flip
     * that already decides whether a citizen is called {@code -wyn} or {@code -mund} — and until
     * this array existed none of it was visible: {@code getTextureLocation} indexed one set of
     * six and a Hedda came out looking exactly like a Sigmund.
     *
     * <p>Same COUNT as the male set and the same index, deliberately: {@link Citizens#faceOf}
     * is one roll off the UUID and it means the same complexion whichever set it lands in, so
     * face 3 is the same person's colouring either way. Only the covering and the cut differ —
     * a head cloth on the {@code hat} cube and an ankle-length gown on the legs' outer layer,
     * both of which were empty in every skin the mod shipped. No second mesh: a slim-arm rig
     * would double the graphics work and the difference does not need it.
     */
    private static final ResourceLocation[] WOMEN = skins("citizen_skin_f");

    private static ResourceLocation[] skins(String prefix) {
        ResourceLocation[] out = new ResourceLocation[Citizens.SKIN_VARIANTS];
        for (int i = 0; i < out.length; i++) {
            out[i] = ResourceLocation.fromNamespaceAndPath(
                Constants.MOD_ID, "textures/entity/npc/" + prefix + i + ".png");
        }
        return out;
    }

    /** A genuine vanilla renderer, kept for the villagers that are nobody's. */
    private final VillagerRenderer vanilla;

    public TownVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel<>(context.bakeLayer(NpcModel.LAYER_LOCATION)), 0.5F);
        this.vanilla = new VillagerRenderer(context);
        this.addLayer(new NpcClothesLayer<>(this));
        // Registered even though a citizen wears none yet: it is the same layer the builder
        // already had, it costs nothing while the slots are empty, and it is the whole reason
        // a garrison will be readable by equipment instead of by seven bespoke textures.
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    @Override
    public void render(Villager villager, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        if (!Citizens.isCitizen(villager)) {
            vanilla.render(villager, entityYaw, partialTick, pose, buffer, packedLight);
            return;
        }
        super.render(villager, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /**
     * Which of the twelve skins this citizen wears.
     *
     * <p>Two independent rolls off the UUID, and they have to stay independent: the sex comes
     * from {@link Citizens#isFemale}, which is {@code CitizenNames.isFeminine} — the FIRST draw
     * of the name generator's sequence, which is why the sex and the name can never disagree —
     * and the complexion from {@link Citizens#faceOf}, which is a salted hash of the same id.
     *
     * <p>Only reached for a citizen: {@link #render} hands anything that is not one to a real
     * {@code VillagerRenderer}, which resolves its own texture.
     */
    @Override
    public ResourceLocation getTextureLocation(Villager villager) {
        ResourceLocation[] set = Citizens.isFemale(villager) ? WOMEN : MEN;
        return set[Citizens.faceOf(villager)];
    }

    /**
     * The same 0.9375 vanilla's own {@code VillagerRenderer} applies, and the builder with it.
     *
     * <p>Without it a citizen stands noticeably taller than the stranger next to him and than
     * the builder who put his house up, purely because {@code HumanoidMobRenderer} does not
     * shrink and {@code VillagerRenderer} does. Belonging to a town should be legible from the
     * clothes, not from being a different size to everyone else.
     */
    @Override
    protected void scale(Villager villager, PoseStack pose, float partialTick) {
        float f = 0.9375F;
        if (villager.isBaby()) {
            f *= 0.5F;
            this.shadowRadius = 0.25F;
        } else {
            this.shadowRadius = 0.5F;
        }
        pose.scale(f, f, f);
    }

    /**
     * Show a citizen's name when the player looks at them.
     *
     * <p>Not always on: a floating label over every resident turns a town back into the
     * spreadsheet this work is trying to get away from. Looking at someone is when their name
     * matters, and it is also how vanilla treats a name-tagged mob, so it needs no explaining.
     *
     * <p>{@code crosshairPickEntity} is bounded by the player's own reach, so there is no
     * distance test to get wrong.
     */
    @Override
    protected boolean shouldShowName(Villager villager) {
        if (!Citizens.isCitizen(villager)) return super.shouldShowName(villager);
        if (villager.isCustomNameVisible()) return true;
        return villager == this.entityRenderDispatcher.crosshairPickEntity;
    }

    /**
     * The label over a citizen's head.
     *
     * <p>A name tag wins, and the given name survives underneath it. That is the whole reason
     * the mod stopped calling {@code setCustomName} to store identity: a custom name IS the
     * name tag, so one anvil used to overwrite who somebody was, permanently and invisibly.
     * Now the tag is a label a player put on, and taking it off reveals Hedda Ashcroft again.
     */
    @Override
    protected void renderNameTag(Villager villager, Component displayName, PoseStack pose,
                                 MultiBufferSource buffer, int packedLight, float partialTick) {
        Component label = villager.hasCustomName() || !Citizens.isCitizen(villager)
            ? displayName
            : Component.literal(Citizens.nameOf(villager));
        super.renderNameTag(villager, label, pose, buffer, packedLight, partialTick);
    }
}
