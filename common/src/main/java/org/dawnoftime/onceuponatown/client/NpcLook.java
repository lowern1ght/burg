package org.dawnoftime.onceuponatown.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.entity.TownNpc;
import org.dawnoftime.onceuponatown.entity.citizen.Citizens;

import java.util.Map;

/**
 * How an entity on the shared rig should look, for entities that cannot tell us themselves.
 *
 * <p>{@code NpcModel} used to be typed on {@code Mob & TownNpc} and read the answers straight
 * off the entity. That stopped working the moment the cast included {@code minecraft:villager}:
 * a vanilla class cannot implement our interface without a mixin. So the questions moved here,
 * and this class answers them two ways — by delegation for anything of ours that implements
 * {@link TownNpc}, and by derivation for a villager, whose profession already says everything
 * the rig needs to know.
 */
public final class NpcLook {

    /**
     * Clothing per profession, drawn over the skin.
     *
     * <p>Vanilla draws its profession overlay on its own model and its own UV; ours is a
     * separate texture on the shared rig, so the whole cast stays one rig deep. A profession
     * with no entry here goes unclothed rather than borrowing someone else's trade.
     */
    private static final Map<String, String> CLOTHES = Map.of(
        "farmer", "farmer_clothes",
        "mason", "mason_clothes",
        "weaponsmith", "smith_clothes",
        "toolsmith", "smith_clothes",
        "armorer", "smith_clothes",
        "fletcher", "forester_clothes"
    );

    /**
     * Dye shifts multiplied into the clothing texture, one per tint variant.
     *
     * <p>A multiply rather than four more drawn files: 7 garments x 4 tints would be 28
     * textures to keep in step, and the whole point of the recolour pipeline was that the
     * hand-drawn mask is the only thing anyone has to draw.
     *
     * <p><b>An eleventh-century range, and that means mostly no dye at all.</b> A peasant wore
     * the colour the sheep grew — cream, grey, moorit brown — because dyeing cost money and
     * labour a household in a sunken-floor house did not have. Of the dyes that WERE within
     * reach, madder is the one that turns up on ordinary cloth; weld gives a yellow-ochre.
     *
     * <p>Green is deliberately absent, and that was a mistake I had made: green needed
     * double-dyeing, woad over weld, so it was an expensive colour and the wrong one to put on
     * a farmhand. Blue is missing for the same reason at this rung — woad is affordable but not
     * free, so it belongs to a village that has something to trade.
     *
     * <p>All of them stay close to white on purpose. These multiply a drawn texture, so a
     * strong shift stops reading as cloth and starts reading as a team colour.
     */
    private static final int[] TINTS = {
        0xFFFFFFFF,   // as drawn — undyed cream fleece
        0xFFD8C9A8,   // undyed, the yellower fleece
        0xFFB0A498,   // undyed grey-brown, a moorit or grey sheep
        0xFFC08A63,   // madder — the one dye an ordinary household could afford
    };

    private NpcLook() {
    }

    /** Reading the town plan: both hands occupied, head tilted down. Builders only. */
    public static boolean isReading(Mob mob) {
        return mob instanceof TownNpc npc && npc.isReading();
    }

    /**
     * Arms folded across the chest.
     *
     * <p>For a villager this is vanilla's own tell for unemployed, and true for the same
     * reason — it is the pose vanilla itself uses, so a jobless citizen reads the way a player
     * already expects a jobless villager to read.
     */
    public static boolean isCrossingArms(Mob mob) {
        if (mob instanceof TownNpc npc) return npc.isCrossingArms();
        if (mob instanceof Villager villager) {
            return villager.getVillagerData().getProfession() == VillagerProfession.NONE;
        }
        return false;
    }

    /** The clothing overlay, or {@code null} for bare. */
    public static ResourceLocation clothes(Mob mob) {
        if (mob instanceof TownNpc npc) return npc.clothesTexture();
        if (mob instanceof Villager villager) {
            String key = CLOTHES.get(villager.getVillagerData().getProfession().name());
            if (key == null) return null;
            return ResourceLocation.fromNamespaceAndPath(
                Constants.MOD_ID, "textures/entity/npc/" + key + ".png");
        }
        return null;
    }

    /**
     * ARGB multiplied into the clothing, so two farmers are not twins.
     *
     * <p>Opaque white for anything that is not a citizen — and opaque is the operative word.
     * This argument used to be handed {@code getOverlayCoords(...)}, a leftover from the
     * 1.20.1 signature where the tail of the parameter list was three float colour channels
     * rather than one packed int. The overlay constant is {@code 0x000A0000}, whose alpha byte
     * is zero, so every garment in the mod was being drawn fully transparent — which is the
     * real reason the citizens turned up looking like nothing but recoloured skin.
     */
    public static int clothesTint(Mob mob) {
        if (mob instanceof Villager villager && Citizens.isCitizen(villager)) {
            return TINTS[Math.floorMod(Citizens.tintOf(villager), TINTS.length)];
        }
        return 0xFFFFFFFF;
    }
}
