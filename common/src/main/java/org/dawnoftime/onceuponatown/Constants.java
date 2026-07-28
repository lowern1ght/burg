package org.dawnoftime.onceuponatown;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {

	public static final String MOD_ID = "onceuponatown";
	public static final String MOD_NAME = "Once Upon a Town";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

	public static final String STREETS_POOL = MOD_ID + ":streets";

	// Reads a BlockPos stored as a CompoundTag with X/Y/Z int fields.
	// Reads a BlockPos written in EITHER format.
	//
	// The port migrated the writes and not the reads, and the two silently disagreed:
	// `NbtUtils.writeBlockPos` emits an IntArrayTag [x,y,z] since 1.21, while this read
	// `tag.getInt("X"/"Y"/"Z")` from the legacy compound. `getInt` on an int-array tag
	// returns 0, so EVERY position in the mod deserialised to (0,0,0). Three symptoms, one
	// cause: every building on the town map drew at the same origin and they stacked; the
	// hub screen's `anchorPos` came out (0,0,0), so every trade packet was rejected by the
	// server's anchor check without a word; and builders lost their town on reload and
	// discarded themselves.
	//
	// Both layouts are accepted, because saves written before the migration are still on
	// disk and dropping them would lose a player's town.
	public static BlockPos readBlockPos(net.minecraft.nbt.Tag tag) {
		if (tag instanceof net.minecraft.nbt.IntArrayTag arr && arr.size() == 3) {
			return new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
		}
		if (tag instanceof CompoundTag compound) {
			return new BlockPos(compound.getInt("X"), compound.getInt("Y"), compound.getInt("Z"));
		}
		return BlockPos.ZERO;
	}

	// Reads a BlockPos from the given key. Takes the RAW tag: `getCompound(key)` returns an
	// empty compound when the value is an int array, which is how the mismatch above turned
	// into silence instead of an exception.
	public static BlockPos readBlockPos(CompoundTag tag, String key) {
		return readBlockPos(tag.get(key));
	}

	// Reads a BlockEntity's NBT into a fresh tag, threading the registry-access provider
	// required by the 1.21 BlockEntity.saveWithoutMetadata() signature. The block entity
	// is assumed to live in the given level.
	public static CompoundTag readBlockEntityNbt(BlockEntity be, Level level) {
		return be.saveWithoutMetadata(level.registryAccess());
	}

	// Re-exported write helper so call sites can stay symmetric with readBlockPos above.
	public static net.minecraft.nbt.Tag writeBlockPos(BlockPos pos) {
		return NbtUtils.writeBlockPos(pos);
	}
}
