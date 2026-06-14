package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class TownCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("ouat")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("town")
                    .then(Commands.literal("addstock")
                        .then(Commands.argument("item", ItemArgument.item(context))
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1, 6400))
                                .executes(TownCommand::addStock))))
                    .then(Commands.literal("status")
                        .executes(TownCommand::status))
                    .then(Commands.literal("stock")
                        .executes(TownCommand::stock))
                    .then(Commands.literal("setup")
                        .executes(TownCommand::setup))
                    .then(Commands.literal("assignbuilder")
                        .executes(TownCommand::assignBuilder))
                    .then(Commands.literal("addconnection")
                        .executes(TownCommand::addConnection))
                    .then(Commands.literal("inittown")
                        .executes(TownCommand::initTown)))
                .then(Commands.literal("preview")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(TownCommand::previewBuilding)))
        );
    }

    private static int addStock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Town town = LevelTowns.get(level).getNearestTown(pos, 128).orElse(null);
        if (town == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No town within 128 blocks"));
            return 0;
        }
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        int qty = IntegerArgumentType.getInteger(ctx, "quantity");
        town.addStock(item, qty);
        LevelTowns.get(level).markDirty();
        ctx.getSource().sendSuccess(
            () -> Component.literal("[OUAT] Added " + qty + "x " + BuiltInRegistries.ITEM.getKey(item)), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Town town = LevelTowns.get(level).getNearestTown(pos, 128).orElse(null);
        if (town == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No town within 128 blocks"));
            return 0;
        }
        TownInventory inv = town.getTownInventory();
        StringBuilder sb = new StringBuilder("[OUAT STATUS]\n");
        sb.append("Buildings: ").append(town.getBuildings().size()).append("\n");
        sb.append("Free connections: ").append(town.getAvailableConnectionPoints().size()).append("\n");
        sb.append("Builders: ").append(town.getBuilderNpcIds()).append(" (target: ").append(town.getTargetBuilderCount()).append(")\n");
        town.getBuildings().forEach(b ->
            sb.append("  [").append(b.defId).append("] @").append(b.worldPos).append("\n"));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int stock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Town town = LevelTowns.get(level).getNearestTown(pos, 128).orElse(null);
        if (town == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No town within 128 blocks"));
            return 0;
        }
        TownInventory inv = town.getTownInventory();

        // Collect all items that actually have stock: from buildings' actual storage + reserve
        Set<Item> allItems = new LinkedHashSet<>();
        for (PlacedBuilding b : town.getBuildings()) {
            allItems.addAll(b.getStockedItems());
        }
        allItems.addAll(town.getReserveStock().keySet());

        if (allItems.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[OUAT STOCK] No stock in this town"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("[OUAT STOCK] " + town.getBuildings().size() + " buildings\n");
        for (Item item : allItems) {
            int qty = inv.getStock(item);
            if (qty <= 0) continue;
            int max = inv.getMaxStock(item);
            sb.append("  ").append(BuiltInRegistries.ITEM.getKey(item).getPath())
                .append(": ").append(qty);
            if (max > 0) sb.append("/").append(max);
            sb.append("\n");
        }
        String result = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    // Links the nearest NPC within 16 blocks to the nearest town within 128 blocks
    private static int assignBuilder(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Town town = LevelTowns.get(level).getNearestTown(pos, 128).orElse(null);
        if (town == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No town within 128 blocks"));
            return 0;
        }
        AABB box = new AABB(pos).inflate(16);
        Npc npc = level.getEntitiesOfClass(Npc.class, box).stream().findFirst().orElse(null);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No NPC within 16 blocks"));
            return 0;
        }
        town.addBuilderNpcId(npc.getUUID());
        LevelTowns.get(level).markDirty();
        ctx.getSource().sendSuccess(
            () -> Component.literal("[OUAT] Builder assigned (slot " + town.getBuilderSlot(npc.getUUID()) + "): " + npc.getUUID()), false);
        return 1;
    }

    // One-shot dev shortcut: setup + stock + spawn NPC + assign builder in one command
    private static int initTown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos anchorPos = BlockPos.containing(ctx.getSource().getPosition());

        // 1. Place town anchor and create Town
        level.setBlock(anchorPos, BlockRegistry.TOWN_ANCHOR.defaultBlockState(), 3);
        TownAnchorBlockEntity be = (TownAnchorBlockEntity) level.getBlockEntity(anchorPos);
        if (be == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] BlockEntity did not spawn"));
            return 0;
        }
        Town town = new Town();
        LevelTowns.get(level).registerTown(anchorPos, town);

        // 2. Seed starting stock
        Item oakLog = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "oak_log"));
        town.addStock(oakLog, 64);

        // 3. Spawn NPC 2 blocks north of the anchor
        BlockPos npcPos = anchorPos.north(2);
        Npc npc = EntityRegistry.NPC.create(level);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] NPC entity creation failed"));
            return 0;
        }
        npc.moveTo(npcPos.getX() + 0.5, npcPos.getY(), npcPos.getZ() + 0.5, 0f, 0f);
        level.addFreshEntityWithPassengers(npc);

        // 4. Assign builder
        town.addBuilderNpcId(npc.getUUID());
        LevelTowns.get(level).markDirty();

        ctx.getSource().sendSuccess(() -> Component.literal(
            "[OUAT] Town initialized at " + anchorPos + " -- now run /ouat town addconnection to start building"
        ), false);
        return 1;
    }

    // Debug: injects a free connection point at the player's position so the NPC can start building
    // Use in flat worlds where ChunkGeneratorMixin never fires and no jigsaw connections exist
    private static int addConnection(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        Town town = LevelTowns.get(level).getNearestTown(pos, 128).orElse(null);
        if (town == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] No town within 128 blocks"));
            return 0;
        }
        Direction facing = ctx.getSource().getPlayerOrException().getDirection();
        // Scan actual terrain surface instead of using player feet (which are 1 block above ground).
        int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        BlockPos seedPos = new BlockPos(pos.getX(), terrainY, pos.getZ());
        // Empty targetPool = generic slot: filter accepts it for any building regardless of entry_pool
        ConnectionPoint cp = ConnectionPoint.of(seedPos, facing, "");
        town.addFreeConnection(cp);
        ctx.getSource().sendSuccess(
            () -> net.minecraft.network.chat.Component.literal(
                "[OUAT-DEBUG] Seed connection at Y=" + seedPos.getY() + " (terrain scan, player was at Y=" + pos.getY() + ") facing=" + facing.getName()), false);
        LevelTowns.get(level).markDirty();
        ctx.getSource().sendSuccess(
            () -> Component.literal("[OUAT] Connection point added at " + seedPos + " facing " + facing.getName()), false);
        return 1;
    }

    // Opens a floating isometric preview widget of a building's NBT structure.
    // Usage: /ouat preview <building_id>  (e.g. /ouat preview wild_stone)
    // The id matches the filename without extension, looked up in structures/plains/jobs/.
    private static int previewBuilding(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "id");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        // All structure subdirectories to search, in priority order
        String[] subDirs = {"jobs", "houses", "gardens", "streets", "starters"};
        ResourceLocation fileId = null;
        Optional<Resource> resOpt = Optional.empty();
        for (String sub : subDirs) {
            ResourceLocation candidate = new ResourceLocation("onceuponatown",
                "structures/plains/" + sub + "/" + id + ".nbt");
            resOpt = server.getResourceManager().getResource(candidate);
            if (resOpt.isPresent()) {
                fileId = candidate;
                break;
            }
        }

        if (resOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                "[OUAT] Structure not found: " + id + " (searched plains/jobs, houses, gardens, streets, starters)"));
            return 0;
        }

        try (InputStream is = resOpt.get().open()) {
            CompoundTag rawNbt = NbtIo.readCompressed(is);
            CompoundTag packetData = buildPreviewPacket(id, rawNbt);
            NetworkHelper.sendNbtPreviewPacket.accept(player, packetData);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] Failed to read structure: " + e.getMessage()));
            return 0;
        }
        return 1;
    }

    // Converts the raw NBT CompoundTag (palette + blocks arrays) into the compact
    // preview packet format the client renderer expects.
    private static CompoundTag buildPreviewPacket(String title, CompoundTag rawNbt) {
        CompoundTag out = new CompoundTag();
        out.putString("title", title);

        ListTag rawSize = rawNbt.getList("size", Tag.TAG_INT);
        out.putInt("sizeX", rawSize.getInt(0));
        out.putInt("sizeY", rawSize.getInt(1));
        out.putInt("sizeZ", rawSize.getInt(2));

        // Palette: keep only the block name ("n" key) to reduce packet size
        ListTag rawPalette = rawNbt.getList("palette", Tag.TAG_COMPOUND);
        ListTag palette = new ListTag();
        for (int i = 0; i < rawPalette.size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("n", rawPalette.getCompound(i).getString("Name"));
            palette.add(entry);
        }
        out.put("palette", palette);

        // Blocks: each entry is an IntArray [x, y, z, paletteIndex]
        ListTag rawBlocks = rawNbt.getList("blocks", Tag.TAG_COMPOUND);
        ListTag blocks = new ListTag();
        for (int i = 0; i < rawBlocks.size(); i++) {
            CompoundTag block = rawBlocks.getCompound(i);
            ListTag rawPos = block.getList("pos", Tag.TAG_INT);
            blocks.add(new IntArrayTag(new int[]{
                rawPos.getInt(0), rawPos.getInt(1), rawPos.getInt(2),
                block.getInt("state")
            }));
        }
        out.put("blocks", blocks);

        return out;
    }

    // Fallback for dev: manual Town init at player position
    // Use only if ChunkGeneratorMixin does not fire correctly
    private static int setup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos anchorPos = BlockPos.containing(ctx.getSource().getPosition());
        level.setBlock(anchorPos, BlockRegistry.TOWN_ANCHOR.defaultBlockState(), 3);
        TownAnchorBlockEntity be = (TownAnchorBlockEntity) level.getBlockEntity(anchorPos);
        if (be == null) {
            ctx.getSource().sendFailure(Component.literal("[OUAT] BlockEntity did not spawn"));
            return 0;
        }
        Town town = new Town();
        LevelTowns.get(level).registerTown(anchorPos, town);
        ctx.getSource().sendSuccess(() -> Component.literal("[OUAT] Town initialized at " + anchorPos), false);
        return 1;
    }
}
