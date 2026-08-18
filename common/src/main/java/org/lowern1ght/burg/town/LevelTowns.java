package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.lowern1ght.burg.datapack.QuestDataHandler;
import org.lowern1ght.burg.integration.xaero.XaeroIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every town in one dimension, keyed by its anchor position.
 *
 * <p><b>One unreadable town used to delete every town in the world.</b> Verified from the
 * bytecode of {@code DimensionDataStorage}: {@code readSavedData} wraps the whole load in {@code
 * catch (Exception)}, logs "Error loading saved data" and returns {@code null}; {@code
 * computeIfAbsent} then sees null and hands back a <em>brand new empty</em> instance, which the
 * next autosave writes over {@code ouat_towns.dat}. So a single bad building entry, a renamed
 * item id, a quest def that no longer parses — anything at all throwing inside {@link #fromNbt}
 * — silently and permanently erased the player's every settlement, with one line in the log to
 * show for it.
 *
 * <p>Two guards against that, and they are separate on purpose. Per-town {@code try/catch} means
 * one bad town costs one town instead of all of them. Keeping the raw tag of whatever failed and
 * writing it back out unchanged means the bad town is not destroyed either: it stops running, but
 * its data is still on disk for a later version to read. Dropping it would be the same data loss
 * arriving one save later.
 */
public class LevelTowns extends SavedData {
    private static final String DATA_NAME = "ouat_towns";
    private static final Logger LOGGER = LoggerFactory.getLogger(LevelTowns.class);
    private final Map<Long, Town> towns = new HashMap<>();
    /**
     * Town entries that failed to parse, held verbatim so {@link #save} can put them back.
     *
     * <p>Not a cache and not a repair queue — a refusal to throw the player's data away because
     * this version of the code cannot read it.
     */
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static LevelTowns get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(LevelTowns::new, LevelTowns::fromNbt),
            DATA_NAME
        );
    }

    public void registerTown(BlockPos anchorPos, Town town) {
        towns.put(anchorPos.asLong(), town);
        setDirty();
        // Soft-dep waypoint integration. No-op when Xaero's Minimap is not loaded;
        // on the server JVM with Xaero loaded the call logs a one-shot warning and
        // returns (Xaero is client-side only). See XaeroIntegration class javadoc.
        XaeroIntegration.onTownRegistered(town);
    }

    public Optional<Town> getTownAt(BlockPos anchorPos) {
        return Optional.ofNullable(towns.get(anchorPos.asLong()));
    }

    public Collection<Town> getAllTowns() { return towns.values(); }

    public Set<Map.Entry<Long, Town>> getAllTownEntries() { return towns.entrySet(); }

    public Optional<Town> getNearestTown(BlockPos pos, int maxRadius) {
        double maxRadiusSq = (double) maxRadius * maxRadius;
        return towns.entrySet().stream()
            .filter(e -> BlockPos.of(e.getKey()).distSqr(pos) <= maxRadiusSq)
            .min(Comparator.comparingDouble(e -> BlockPos.of(e.getKey()).distSqr(pos)))
            .map(Map.Entry::getValue);
    }

    // Call after any Town mutation so SavedData schedules a write on next autosave
    public void markDirty() { setDirty(); }

    /** How many towns in this level's save could not be parsed and are being held as raw data. */
    public int getUnreadableTownCount() { return unreadable.size(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        towns.forEach((posLong, town) -> {
            CompoundTag entry = town.toNbt();
            entry.putLong("AnchorPos", posLong);
            list.add(entry);
        });
        // Copied through untouched. These are the entries this version could not read; writing the
        // file without them would finish the job the load failure started.
        for (CompoundTag raw : unreadable) list.add(raw.copy());
        tag.put("Towns", list);
        return tag;
    }

    public static LevelTowns fromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        LevelTowns lt = new LevelTowns();
        ListTag saved = tag.getList("Towns", Tag.TAG_COMPOUND);
        for (Tag t : saved) {
            CompoundTag entry = (CompoundTag) t;
            long posLong = entry.getLong("AnchorPos");
            try {
                lt.towns.put(posLong, Town.fromNbt(entry));
            } catch (Exception e) {
                // Loud, named and counted. The failure itself is contained here; what must not be
                // contained is the player's knowledge of it, because the symptom in game is simply
                // that a village they built is not there any more.
                lt.unreadable.add(entry.copy());
                LOGGER.error("[OUAT-INTEGRITY] Town at {} failed to load and has been held aside as"
                    + " raw data -- it will not run, but it is preserved in ouat_towns.dat and"
                    + " written back unchanged. {} of {} town(s) affected.",
                    BlockPos.of(posLong), lt.unreadable.size(), saved.size(), e);
            }
        }
        if (!lt.unreadable.isEmpty()) {
            LOGGER.error("[OUAT-INTEGRITY] {} town(s) loaded, {} unreadable. Run"
                + " `/ouat debug verify` in each dimension.", lt.towns.size(), lt.unreadable.size());
        }
        Set<String> validQuestIds = QuestDataHandler.getRegistryKeySet();
        boolean anyChanged = false;
        for (Town town : lt.towns.values()) {
            if (town.cleanupOrphanedQuestData(validQuestIds)) anyChanged = true;
        }
        if (anyChanged) lt.setDirty();
        return lt;
    }
}
