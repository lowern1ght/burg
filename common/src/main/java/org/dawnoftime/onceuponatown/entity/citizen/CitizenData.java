package org.dawnoftime.onceuponatown.entity.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.dawnoftime.onceuponatown.Constants;

/**
 * What the mod knows about a villager that belongs to a town, attached to the vanilla entity.
 *
 * <p><b>Why attached and not a subclass.</b> A citizen IS a villager — not something that
 * replaces one. Substituting our own entity type meant every villager already standing in a
 * generated village was the wrong class, the author's own NBTs spawn seven raw
 * {@code minecraft:villager}, and a child bred by two of ours came out vanilla unless we
 * intercepted breeding. Membership is a fact ABOUT a villager, so it is stored on the
 * villager, and every villager in the world stays eligible to become one.
 *
 * <p><b>Sentinels mean "derive", and that is load-bearing.</b> Name, face and tint are pure
 * functions of the entity's UUID ({@link org.dawnoftime.onceuponatown.entity.CitizenNames}),
 * so the common case stores nothing at all and cannot drift. It also means the CLIENT can
 * work them out for itself from a UUID it already has — which matters because NeoForge
 * 21.1.77 has no {@code sync()} on {@code AttachmentType.Builder}: attachments are
 * server-side only. The one thing the client genuinely cannot derive is membership, and that
 * is the one thing {@link Citizens} publishes over the wire.
 *
 * <p>The overrides exist for the cast that is coming: a chief, a named guard, a diplomat is
 * given its face and its name deliberately rather than getting whatever its id hashes to.
 */
public class CitizenData implements INBTSerializable<CompoundTag> {

    /** "Not chosen — derive it from the UUID." Not 0, or face 0 could never be pinned. */
    public static final int DERIVE = -1;

    /**
     * Anchor of the town this villager belongs to. {@code null} means it is nobody's —
     * an ordinary villager. This field alone decides membership; there is no separate flag
     * that could disagree with it.
     */
    private BlockPos townAnchor = null;

    private int face = DERIVE;
    private int tint = DERIVE;

    /**
     * {@code 0} man, {@code 1} woman, {@link #DERIVE} for the UUID's own coin flip.
     *
     * <p>Stored only when the flip was overridden, which happens when it would have deepened an
     * existing shortage of one sex in the town. Left alone the town is 50/50 and this field is
     * never written — see {@code Citizens.balanceSex}.
     */
    private int sex = DERIVE;

    /** Empty means derive. Stored under the name tag, never as the name tag. */
    private String givenName = "";

    public boolean isMember() {
        return townAnchor != null;
    }

    public BlockPos getTownAnchor() {
        return townAnchor;
    }

    public void setTownAnchor(BlockPos pos) {
        this.townAnchor = pos;
    }

    public int getFace() {
        return face;
    }

    public void setFace(int face) {
        this.face = face;
    }

    public int getTint() {
        return tint;
    }

    public void setTint(int tint) {
        this.tint = tint;
    }

    public int getSex() {
        return sex;
    }

    public void setSex(int sex) {
        this.sex = sex;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String name) {
        this.givenName = name == null ? "" : name;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        // Written through Constants, not NbtUtils, and read back the same way. Mixing the two
        // is the bug that cost this project a whole session: NbtUtils writes an IntArrayTag
        // since 1.21 and getInt("X") on one returns 0 without complaining, so every position
        // in the mod silently became (0,0,0).
        if (townAnchor != null) tag.put("TownAnchor", Constants.writeBlockPos(townAnchor));
        if (face != DERIVE) tag.putInt("Face", face);
        if (tint != DERIVE) tag.putInt("Tint", tint);
        if (sex != DERIVE) tag.putInt("Sex", sex);
        if (!givenName.isEmpty()) tag.putString("GivenName", givenName);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        townAnchor = tag.contains("TownAnchor") ? Constants.readBlockPos(tag, "TownAnchor") : null;
        face = tag.contains("Face") ? tag.getInt("Face") : DERIVE;
        tint = tag.contains("Tint") ? tag.getInt("Tint") : DERIVE;
        sex = tag.contains("Sex") ? tag.getInt("Sex") : DERIVE;
        givenName = tag.getString("GivenName");
    }
}
