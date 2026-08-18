package org.lowern1ght.burg.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Mob;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.lowern1ght.burg.Ouat;
import org.lowern1ght.burg.client.NpcLook;
import org.lowern1ght.burg.entity.TownNpc;

import java.util.List;

/**
 * A villager-shaped mesh on a HumanoidModel base: vanilla's look with vanilla's arm
 * poses and armour layer, which VillagerModel does not have.
 *
 * <p>Typed on plain {@link Mob}, not on {@code Mob & TownNpc}. It has to be: the cast this rig
 * now carries includes {@code minecraft:villager} itself, and a vanilla class cannot be made
 * to implement our interface without a mixin. The three things the rig actually wanted off
 * {@link TownNpc} — folded arms, the reading pose, the build swing — are asked of
 * {@link org.lowern1ght.burg.client.NpcLook} instead, which answers for a
 * {@code TownNpc} by delegation and for a villager by derivation. Everything else here was
 * only ever using {@code Mob}.
 */
public class NpcModel<T extends Mob> extends HumanoidModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "npc"), "main_layer");
    private final List<ModelPart> parts;

    /**
     * Whether to fold the arms this frame.
     *
     * <p>Held as a flag instead of a mesh part. The villager has a single fused cube for its
     * folded arms and hides the real ones behind it; a person has to actually fold the arms he
     * has. That means it is a POSE, and a pose has to be applied after {@code super.setupAnim}
     * or the walk cycle overwrites it — which is why this is set in {@code prepareMobModel} and
     * used a step later.
     */
    private boolean crossingArms;
    /** Set from the entity each frame; see NpcPose. */
    private org.lowern1ght.burg.entity.NpcPose pose =
        org.lowern1ght.burg.entity.NpcPose.STANDING;

    public NpcModel(ModelPart root) {
        super(root);
        this.parts = root.getAllParts().filter((part) -> !part.isEmpty()).collect(ImmutableList.toImmutableList());
    }

    /**
     * A person, on the player's own mesh and the player's own UV.
     *
     * <p>This used to be a villager: head 8x10x8 instead of 8x8x8, a nose, a torso 6 deep
     * instead of 4, and a 20-tall {@code jacket} robe hanging past the legs. Those four cubes
     * were the entire villager look — the skeleton underneath was always {@code HumanoidModel},
     * which IS the player's. So becoming human was replacing a mesh, not replacing a rig, and
     * the arm poses, the armour layer and every animation carried over untouched.
     *
     * <p><b>Player UV, deliberately, and not HumanoidModel's.</b> {@code createMesh} still uses
     * the pre-1.8 single-arm layout — it mirrors the right arm at (40,16) for the left instead
     * of reading (32,48) — so a skin drawn for a modern player would come out with a mirrored
     * left arm and leg. Every part is therefore authored here against the real layout: base at
     * (0,0)/(16,16)/(40,16)/(32,48)/(0,16)/(16,48), second layer at
     * (32,0)/(16,32)/(40,32)/(48,48)/(0,32)/(0,48). Any player skin in the world now fits.
     *
     * <p>The second layer is what clothing is painted on. {@code NpcClothesLayer} re-renders
     * this same mesh with the garment texture, so a garment leaves the base regions transparent
     * and fills only the outer ones — which is exactly how the old robe cube worked, except the
     * regions are now the ones every skin editor already knows.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        PartDefinition root = meshDefinition.getRoot();
        // 0.25 for the body and limbs, 0.5 for the head, matching a vanilla player exactly.
        CubeDeformation outer = new CubeDeformation(0.25F);
        CubeDeformation hatLayer = new CubeDeformation(0.5F);

        root.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
        // A sibling of head, not a child of it — HumanoidModel reads `hat` off the root and
        // copies the head's rotation onto it every frame in setupAnim.
        root.addOrReplaceChild("hat", CubeListBuilder.create()
            .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, hatLayer), PartPose.ZERO);

        root.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
            .texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer), PartPose.ZERO);

        // Pivots at the shoulder, not the model centre: (0,0,0) buried the arm inside the body
        // mesh and made every rotation animation invisible.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
            .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
            .texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
            .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
            .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
            .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
            .texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
            .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
            .texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
            PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    @Override
    public void prepareMobModel(T npc, float limbSwing, float limbSwingAmount, float partialTick) {
        HumanoidModel.ArmPose leftArmPose = getArmPose(npc, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose rightArmPose = getArmPose(npc, InteractionHand.OFF_HAND);
        if (leftArmPose.isTwoHanded())
            rightArmPose = npc.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (npc.getMainArm() == HumanoidArm.RIGHT) {
            this.rightArmPose = leftArmPose;
            this.leftArmPose = rightArmPose;
        } else {
            this.rightArmPose = rightArmPose;
            this.leftArmPose = leftArmPose;
        }
        setCrossedArms(NpcLook.isCrossingArms(npc));
        super.prepareMobModel(npc, limbSwing, limbSwingAmount, partialTick);
    }

    @Override
    public void setupAnim(T npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.head.zRot = 0.0F;
        // Read here rather than in a setter, because this is the one method that is handed the
        // entity. A field set from somewhere else would be a frame behind at best.
        this.pose = org.lowern1ght.burg.client.NpcLook.poseOf(npc);

        // The build swing needs somewhere on the entity to keep its per-frame cursor, so it
        // stays a TownNpc-only animation. A villager does not lay blocks and never enters it.
        if (npc instanceof TownNpc builder) {
            // Detect a new block placement: generation counter changed since last frame.
            int gen = builder.getBuildGeneration();
            if (gen != builder.getClientLastBuildGeneration()) {
                builder.setClientLastBuildGeneration(gen);
                builder.setClientBuildPlacedAtAge(ageInTicks);
            }

            float elapsed = ageInTicks - builder.getClientBuildPlacedAtAge();
            float duration = 7.0f; // ticks for the swing animation to complete
            if (elapsed >= 0 && elapsed <= duration) {
                // t = 1.0 at placement, 0.0 at end of animation.
                float t = 1.0f - (elapsed / duration);
                // Ease-out quadratic: fast start, decelerates to rest.
                float swing = t * (2.0f - t);
                // Lateral arc: peaks mid-animation to give the arm a natural curved path.
                float arc = Mth.sin((float) Math.PI * (1.0f - t));
                this.rightArm.xRot -= swing * 1.2F;
                this.rightArm.zRot -= arc * 0.18F;
            }
        }

        // Both of these are poses that override the walk cycle, so they run last. Reading wins
        // over folded arms: someone holding a plan open is plainly not standing idle.
        if (this.crossingArms) animateCrossedArms();
        // One switch, after super.setupAnim, because a pose OVERRIDES the walk cycle rather
        // than blending with it -- the same reason the reading pose has to run last.
        switch (this.pose) {
            case SITTING -> animateSitting();
            case DOZING -> animateDozing();
            case TALKING -> animateTalking(ageInTicks);
            default -> { }
        }
        animateReadingPose(npc);

        /*
         * The head cloth is on `hat`, so `hat` has to follow the head — and `copyFrom` is the
         * only thing that does it in all three axes, which is exactly why vanilla ends
         * HumanoidModel.setupAnim with this same call.
         *
         * It has to be LAST. Vanilla's copy happens inside super, and everything above this line
         * moves the head afterwards: `head.zRot = 0` and, when a builder is reading a plan,
         * `head.xRot = 0.38`. What used to stand here was `hat.xRot = head.xRot` in two places —
         * one axis, hand-synced, so a tilted head left its zRot behind on the hat. That was
         * invisible while the cube was empty and would have been a head cloth sliding off a
         * woman's head the moment she looked up.
         */
        this.hat.copyFrom(this.head);
    }

    private void animateReadingPose(T npc) {
        if (NpcLook.isReading(npc)) {
            if (!npc.isLeftHanded()) {
                this.rightArm.xRot = -1.65F;
                this.rightArm.yRot = -0.36F;
                this.rightArm.zRot = 1.5F;
                this.rightArm.y += 1F;
                this.rightArm.x -= 0.75F;
                this.leftArm.xRot = -1.2F;
                this.leftArm.yRot = 0.1F;
                this.leftArm.zRot = 0.1F;
            } else {
                this.leftArm.xRot = -1.65F;
                this.leftArm.yRot = 0.36F;
                this.leftArm.zRot = -1.5F;
                this.leftArm.y += 1F;
                this.leftArm.x += 0.75F;
                this.rightArm.xRot = -1.2F;
                this.rightArm.yRot = -0.1F;
                this.rightArm.zRot = -0.1F;
            }
            this.head.xRot = 0.38F;
        }
    }

    private HumanoidModel.ArmPose getArmPose(T npc, InteractionHand hand) {
        ItemStack itemstack = npc.getItemInHand(hand);
        if (itemstack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (npc.getUsedItemHand() == hand && npc.getUseItemRemainingTicks() > 0) {
            UseAnim useanim = itemstack.getUseAnimation();
            if (useanim == UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
            if (useanim == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (useanim == UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
            if (useanim == UseAnim.CROSSBOW && hand == npc.getUsedItemHand()) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            if (useanim == UseAnim.SPYGLASS) return HumanoidModel.ArmPose.SPYGLASS;
            if (useanim == UseAnim.TOOT_HORN) return HumanoidModel.ArmPose.TOOT_HORN;
            if (useanim == UseAnim.BRUSH) return HumanoidModel.ArmPose.BRUSH;
        } else if (!npc.swinging && itemstack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(itemstack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    public void setCrossedArms(boolean crossedArms) {
        this.crossingArms = crossedArms;
    }

    /** Both arms in across the chest, one forearm resting over the other. */
    /**
     * DISABLED, and the reason is worth keeping.
     *
     * <p>These numbers came from {@code VillagerModel}, where folded arms are ONE PRE-MODELLED
     * CUBE already built in the crossed position and {@code xRot = -0.75} merely tilts it. Our rig
     * is the player skeleton: two separate arms hanging at the shoulder. Rotating each of those
     * forward by 0.8 radians does not fold them across the chest — it raises them straight out in
     * front, which in game read as a zombie with its arms up. A pose borrowed from a model whose
     * geometry it depended on.
     *
     * <p>It only ever fired for a vanilla {@code Villager} with no profession — {@code
     * NpcLook.isCrossingArms} — since our own {@code Npc.isCrossingArms} is always false. Vanilla
     * villagers are being phased out anyway, so nothing of ours loses a pose, and arms that hang
     * are correct where arms that stick out are a visible fault.
     *
     * <p>To bring it back properly the arms have to roll INWARD across the chest rather than swing
     * forward, and the exact rotations cannot be settled from the source — only by looking at them
     * in game. Restoring the merged cube the way vanilla does it is the other option.
     */

    /**
     * Sitting on the ground.
     *
     * <p>Every number below is <b>vanilla's own</b>, read out of {@code HumanoidModel.setupAnim}'s
     * riding branch by decompiling it. Borrowed deliberately and safely: riding is a humanoid pose
     * on this exact skeleton. The pose this mod borrowed before came from {@code VillagerModel},
     * where folded arms are ONE pre-modelled cube, and the same numbers on two separate hanging
     * arms raised them straight out in front. Same-skeleton is the whole difference.
     *
     * <p>The renderer also has to drop the whole body, or the legs fold into the ground rather
     * than in front of it — vanilla gets that from the vehicle's passenger offset and we have no
     * vehicle, so {@code NpcRenderer} translates instead.
     */
    private void animateSitting() {
        this.rightArm.xRot += -0.62831855F;
        this.leftArm.xRot += -0.62831855F;
        this.rightLeg.xRot = -1.4137167F;
        this.rightLeg.yRot = 0.31415927F;
        this.rightLeg.zRot = 0.07853982F;
        this.leftLeg.xRot = -1.4137167F;
        this.leftLeg.yRot = -0.31415927F;
        this.leftLeg.zRot = -0.07853982F;
    }

    /** Asleep where they sat: the sitting pose with the head fallen forward. */
    private void animateDozing() {
        animateSitting();
        this.head.xRot = 0.55F;
        this.head.yRot = 0.10F;
        this.rightArm.xRot = -0.15F;
        this.leftArm.xRot = -0.15F;
    }

    /**
     * Talking to somebody.
     *
     * <p>The one pose here that is invented rather than borrowed — vanilla has no humanoid gesture
     * to copy — so these numbers are the ones to distrust until they have been seen in game. One
     * hand up and turned slightly in, which is the least that reads as speech rather than as a
     * twitch.
     */
    private void animateTalking(float ageInTicks) {
        float wave = net.minecraft.util.Mth.sin(ageInTicks * 0.18F) * 0.25F;
        this.rightArm.xRot = -0.85F + wave;
        this.rightArm.zRot = -0.35F;
        this.leftArm.xRot = -0.15F;
        this.head.yRot += 0.20F;
    }

    private void animateCrossedArms() {
        // Intentionally empty. See the note above before filling it in.
    }

}
