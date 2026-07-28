package org.dawnoftime.onceuponatown.entity.citizen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.client.CitizenMembershipClientState;
import org.dawnoftime.onceuponatown.entity.CitizenNames;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.registry.AttachmentRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;

import java.util.List;

/**
 * The one seam between "a villager" and "a villager of ours".
 *
 * <p>Every call site goes through here rather than reading the attachment directly, because
 * the answer comes from two different places depending on side: the attachment is the truth
 * on the server, and the client has only what was published to it. Hiding that split in one
 * class is what lets the renderer, the commands and the town code all just ask
 * {@link #isCitizen}.
 *
 * <p>How many base skins exist. Vanilla varies a villager by biome TYPE — seven skins under
 * one profession overlay — and the mod's first cut had exactly one, so every citizen was the
 * same person in different clothes.
 */
public final class Citizens {

    public static final int SKIN_VARIANTS = 6;

    /** Distinct dye-shifts within one profession's clothing, so two farmers are not twins. */
    public static final int TINT_VARIANTS = 4;

    private Citizens() {
    }

    // --- membership ---

    /**
     * Is this villager a member of a town?
     *
     * <p>Server: the attachment, which is the truth. Client: whatever was published, which is
     * why {@code enlist} and {@code dismiss} both broadcast.
     */
    public static boolean isCitizen(Villager villager) {
        if (villager == null) return false;
        if (villager.level().isClientSide) {
            return CitizenMembershipClientState.isMember(villager.getUUID());
        }
        return data(villager).isMember();
    }

    /** The town this villager belongs to, or {@code null}. Server-side only. */
    public static BlockPos anchorOf(Villager villager) {
        if (villager == null || villager.level().isClientSide) return null;
        return data(villager).getTownAnchor();
    }

    /**
     * Take this villager into a town.
     *
     * <p>Persistence is set here rather than in a constructor because there is no constructor
     * of ours any more: an ordinary villager despawns when no player is near, and a resident
     * the town holds a UUID for despawning is a silent leak.
     */
    public static void enlist(Villager villager, BlockPos anchor) {
        CitizenData d = data(villager);
        d.setTownAnchor(anchor);
        villager.setPersistenceRequired();
        // Before anything else reads the sex, and only possible once the anchor is set: the
        // balance is counted over the town this person is joining.
        balanceSex(villager, anchor);
        // Vanilla strips a profession from any villager with no claimed job site AND no
        // experience — `LoseJobOnSiteLoss`. A citizen enlisted before the town has built its
        // workstation would fall back to NONE within a tick or two and turn up unclothed;
        // measured as "they all came out grey". One point of experience is vanilla's own
        // exemption: someone who has done business keeps his trade.
        if (villager.getVillagerData().getProfession() != VillagerProfession.NONE
                && villager.getVillagerXp() == 0) {
            villager.setVillagerXp(1);
        }
        publish(villager, true);
    }

    /**
     * Release this villager back to being nobody's.
     *
     * <p>Deliberately not {@code discard()}. The old {@code Citizen} subclass killed itself
     * when its town was gone, which was defensible for an entity we had spawned; deleting a
     * plain villager because OUR bookkeeping went stale is not. It stops being ours and
     * carries on living, which is also what a person would do.
     */
    public static void dismiss(Villager villager) {
        data(villager).setTownAnchor(null);
        publish(villager, false);
    }

    /**
     * Drop membership if the town it names no longer exists.
     *
     * <p>Called when a player starts tracking the villager rather than on every tick: that is
     * the moment it begins to matter visually, it is rare, and it costs nothing for the
     * hundreds of ordinary villagers a world holds. Returns whether it is still a citizen.
     */
    public static boolean validate(ServerLevel level, Villager villager) {
        CitizenData d = data(villager);
        BlockPos anchor = d.getTownAnchor();
        if (anchor == null) return false;
        if (LevelTowns.get(level).getTownAt(anchor).isEmpty()) {
            dismiss(villager);
            return false;
        }
        return true;
    }

    /** Every citizen in the box. Replaces {@code getEntitiesOfClass(Citizen.class, ...)}. */
    public static List<Villager> in(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(Villager.class, box, Citizens::isCitizen);
    }

    // --- identity, derived from the UUID unless deliberately overridden ---

    /**
     * The citizen's own name.
     *
     * <p>Not {@code getCustomName}. A custom name is the NAME TAG, a thing the player owns
     * and can overwrite, and using it for identity meant one anvil turned a citizen into
     * someone else permanently. The given name lives underneath the tag, so a tagged citizen
     * shows the tag and is still Hedda Ashcroft.
     */
    public static String nameOf(Villager villager) {
        if (!villager.level().isClientSide) {
            String stored = data(villager).getGivenName();
            if (!stored.isEmpty()) return stored;
        }
        return CitizenNames.of(villager.getUUID());
    }

    public static final int MALE = 0;
    public static final int FEMALE = 1;

    /**
     * Is this person a woman? Half of them are.
     *
     * <p>Derived from the UUID by default, which the client can do for itself, and only stored
     * when the town's balance had to be corrected. See {@link CitizenNames#isFeminine} — the sex
     * is the same coin flip that already decides whether the name ends {@code -wyn} or
     * {@code -mund}, so a woman has always had a woman's name.
     */
    public static boolean isFemale(Villager villager) {
        if (!villager.level().isClientSide) {
            int stored = data(villager).getSex();
            if (stored != CitizenData.DERIVE) return stored == FEMALE;
        }
        return CitizenNames.isFeminine(villager.getUUID());
    }

    /**
     * Nudge a newcomer's sex if the coin flip would deepen a shortage.
     *
     * <p>A fair flip per person is not the same as a balanced town. Six people is a small
     * sample: five of one sex comes up often enough to notice, and a town that cannot pair off
     * is a town that cannot have children. So the flip stands unless the town is ALREADY skewed
     * by more than one and the newcomer would make it worse — which keeps the gap inside two,
     * writes nothing in the ordinary case, and leaves the sex of any individual still a matter
     * of chance rather than a rota.
     */
    private static void balanceSex(Villager newcomer, BlockPos anchor) {
        if (!(newcomer.level() instanceof ServerLevel level)) return;
        // The newcomer is not in the world yet at enlist time, so it cannot count itself.
        List<Villager> town = in(level, new AABB(anchor).inflate(64.0));
        int females = 0;
        for (Villager other : town) if (isFemale(other)) females++;
        int males = town.size() - females;

        boolean wouldBeFemale = CitizenNames.isFeminine(newcomer.getUUID());
        if (wouldBeFemale && females > males + 1) {
            data(newcomer).setSex(MALE);
        } else if (!wouldBeFemale && males > females + 1) {
            data(newcomer).setSex(FEMALE);
        }
    }

    public static int faceOf(Villager villager) {
        return variant(villager, CitizenData::getFace, FACE_SALT, SKIN_VARIANTS);
    }

    public static int tintOf(Villager villager) {
        return variant(villager, CitizenData::getTint, TINT_SALT, TINT_VARIANTS);
    }

    // Distinct salts so face and tint are independent rolls off one UUID rather than two
    // views of the same hash — see CitizenNames.variant for why that matters.
    private static final int FACE_SALT = 0;
    private static final int TINT_SALT = 1;

    // Server: an override if one was set, else the UUID's own answer. Client: always the
    // UUID's answer, because an override is not published and a chief is server-authored.
    private static int variant(Villager villager,
                               java.util.function.ToIntFunction<CitizenData> field,
                               int salt, int variants) {
        if (!villager.level().isClientSide) {
            int chosen = field.applyAsInt(data(villager));
            if (chosen != CitizenData.DERIVE) return Math.floorMod(chosen, variants);
        }
        return CitizenNames.variant(villager.getUUID(), salt, variants);
    }

    // --- plumbing ---

    /**
     * The attachment, created on first touch. Server-side only: reading it on the client
     * would hand back a fresh empty record and quietly report every citizen as nobody's.
     */
    private static CitizenData data(Villager villager) {
        return villager.getData(AttachmentRegistry.CITIZEN);
    }

    private static void publish(Villager villager, boolean member) {
        NetworkHelper.broadcastVillagerIdentity.accept(villager, member);
    }
}
