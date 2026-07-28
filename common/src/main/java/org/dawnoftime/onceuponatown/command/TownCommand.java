package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.dawnoftime.onceuponatown.entity.citizen.Citizens;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.terrain.TerrainCarver;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.EraTransitionDataHandler;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TownCommand {

    // Spawns one citizen at the caller's feet, gives it a vanilla profession, and ties it
    // to the nearest town so it self-validates on reload. Everything after that — claiming a
    // workstation, keeping a schedule, harvesting, sleeping — is vanilla's, and that is the
    // whole point of the test: if it stands still, the profession has no workstation nearby.
    private static int citizen(CommandContext<CommandSourceStack> ctx, String professionPath) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());

        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION
            .getOptional(ResourceLocation.withDefaultNamespace(professionPath))
            .orElse(null);
        if (profession == null) {
            src.sendFailure(Component.literal(
                "[OUAT] No such villager profession: " + professionPath));
            return 0;
        }

        // A plain vanilla villager, not an entity of ours. Membership is attached to it
        // afterwards, which is the whole architecture: a citizen IS a villager, so every
        // villager already standing in a generated village is eligible to become one and
        // nothing has to be substituted.
        Villager citizen = EntityType.VILLAGER.create(level);
        if (citizen == null) {
            src.sendFailure(Component.literal("[OUAT] Could not create the villager entity"));
            return 0;
        }
        citizen.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
            src.getRotation().y, 0.0f);
        citizen.setVillagerData(citizen.getVillagerData().setProfession(profession));
        // The anchor is the map KEY in LevelTowns, not a field on Town — so the nearest
        // town is found over the entries and the citizen is given the key, which is what
        // `getTownAt` will later look it up by.
        BlockPos anchor = LevelTowns.get(level).getAllTownEntries().stream()
            .map(e -> BlockPos.of(e.getKey()))
            .min(java.util.Comparator.comparingDouble(a -> a.distSqr(pos)))
            .orElse(null);
        // Enlisted AFTER the profession is set: `enlist` grants the one experience point that
        // stops vanilla demoting a jobless villager, and it can only tell whether that is
        // needed once it can see the trade.
        if (anchor != null) Citizens.enlist(citizen, anchor);
        level.addFreshEntity(citizen);

        src.sendSuccess(() -> Component.literal(
            "[OUAT] " + (anchor == null ? "Villager" : Citizens.nameOf(citizen))
            + " spawned as " + professionPath
            + (anchor == null
                ? " (no town nearby — spawned as an ordinary villager, not a citizen)"
                : " for the town at " + anchor)), true);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                 CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("ouat")
                .then(Commands.literal("town")
                    .then(Commands.literal("status")
                        .executes(TownCommand::status))
                    .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(TownCommand::spawn))
                    // Test hook for the citizen scaffold: put one live resident in front of
                    // you with a trade, and watch whether vanilla's own schedule takes it
                    // from there. `/ouat town citizen farmer`
                    .then(Commands.literal("citizen")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> citizen(ctx, "none"))
                        .then(Commands.argument("profession", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                BuiltInRegistries.VILLAGER_PROFESSION.keySet().stream()
                                    .map(ResourceLocation::getPath), b))
                            .executes(ctx -> citizen(ctx,
                                StringArgumentType.getString(ctx, "profession"))))))
                // `/ouat debug ...` sits beside `town`, not under it: it reports on the whole
                // level, not on one settlement.
                .then(DebugCommand.node())
        );
    }

    /**
     * Put a whole starting settlement on the ground, the way world generation does.
     *
     * <p><b>This used to place the anchor and nothing else</b>, and that is why a commanded town
     * came out as a lone campfire with no buildings and no way to grow one. {@code
     * Town.initFromEraDef} is documented as running "after all starter buildings are
     * registered" — worldgen places the settlement piece first and registers it
     * ({@code ChunkGeneratorMixin}), and only then builds the Town around it. Called with
     * nothing registered it had no starter to read, so the town got no era and no orientation,
     * its connection-point list stayed empty, and the builder had nowhere it was allowed to
     * build. The campfire was not broken; it was the only thing there.
     *
     * <p>So this now follows the generator's own order: place the structure, read its jigsaw
     * connections out of the NBT, register it as the starter, THEN init the era, then register
     * the town.
     *
     * <p><b>And it prepares the site, which it also used to skip.</b> World generation never
     * drops a starter on raw ground: {@code plains_town.json} carries {@code
     * project_start_to_heightmap} and {@code terrain_adaptation: beard_thin}, so the piece is put
     * at the surface and the terrain is then bearded up to meet it and carved away above it.
     * Placing at the player's feet with nothing else did the opposite of both. Measured on all
     * three starters, local layer y=0 is the <b>ground itself</b> — 60-66 grass blocks, coarse
     * dirt, dirt path — so anchoring it at the player's feet, which is the air block above the
     * grass, floated the whole settlement one course up with the original turf still underneath,
     * every doorway a step high and the campfire standing in mid-air. And because {@code place}
     * skips air rather than clearing it, whatever grew inside the footprint — grass, flowers, a
     * tree — stayed there, poking through the floor and the walls. That is the "broken main
     * building" and the "campfire is gone", and it had nothing to do with the NBTs.
     */
    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos standing = BlockPos.containing(src.getPosition());

        // The three settlements are genuinely different buildings, not one with variants, so a
        // debug town picks one at random rather than always showing the same starter.
        String[] starters = {"settlement", "settlement_2", "settlement_3"};
        String defId = starters[level.getRandom().nextInt(starters.length)];
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) {
            src.sendFailure(Component.literal(
                "[OUAT] No building definition '" + defId + "' — is the datapack loaded?"));
            return 0;
        }
        StructureTemplate template = level.getStructureManager().get(def.nbt).orElse(null);
        if (template == null) {
            src.sendFailure(Component.literal("[OUAT] NBT not found: " + def.nbt));
            return 0;
        }

        // Site survey over the real footprint, not just the block underfoot: the median ground
        // level is where layer 0 belongs, and the spread between lowest and highest is how rough
        // the ground is. The median rather than the minimum so one pothole or one boulder does
        // not sink or lift the whole settlement.
        Vec3i size = template.getSize();
        List<Integer> groundLevels = new ArrayList<>(size.getX() * size.getZ());
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                int g = BuildSchematic.groundY(level, standing.getX() + dx, standing.getZ() + dz);
                if (g != BuildSchematic.NO_GROUND) groundLevels.add(g);
            }
        }
        if (groundLevels.isEmpty()) {
            src.sendFailure(Component.literal(
                "[OUAT] No ground under the footprint here — stand somewhere solid."));
            return 0;
        }
        Collections.sort(groundLevels);
        int groundLevel = groundLevels.get(groundLevels.size() / 2);
        int relief = groundLevels.get(groundLevels.size() - 1) - groundLevels.get(0);
        BlockPos pos = new BlockPos(standing.getX(), groundLevel, standing.getZ());

        // Carve above and fill below, in the order NewBuildAction uses — the mod's own site prep,
        // and the stand-in for the beardifier the command does not get.
        TerrainCarver.prePlace(level, pos, template, Rotation.NONE);
        TerrainCarver.postPlace(level, pos, template, Rotation.NONE);

        if (!BuildSchematic.place(level, pos, def.nbt, Rotation.NONE)) {
            src.sendFailure(Component.literal("[OUAT] Could not place " + def.nbt));
            return 0;
        }

        Town town = new Town();
        town.setName("Debug Town");

        // Registered BEFORE initFromEraDef, which is the whole point: it reads the starter to
        // decide the town's era and which way it faces.
        List<ConnectionPoint> connections =
            BuildSchematic.readJigsawPointsFromNbt(level, pos, def.nbt, Rotation.NONE);
        BoundingBox bb = BuildSchematic.computeBoundingBox(level, pos, def.nbt, Rotation.NONE)
            .orElse(new BoundingBox(pos));
        town.registerBuilding(pos, defId, connections, bb, Rotation.NONE);

        // The starting stock the generator grants and this command did not. `settlement.json`
        // declares 10 oak logs and 10 cobblestone; without them the town owns nothing, and a
        // builder with no materials never starts a build — the town would just stand there
        // looking finished. Gated on the era def the same way `ChunkGeneratorMixin` gates it, so
        // only a genuine starter hands out a founding grant.
        int granted = 0;
        if (EraTransitionDataHandler.getEraDefForStarter(defId) != null) {
            for (ItemCost cost : def.initialStock) {
                town.addStock(cost.item(), cost.amount());
                granted += cost.amount();
            }
        }

        town.initFromEraDef();

        // The anchor is NOT placed here — every starter NBT already contains exactly one, and
        // `ChunkGeneratorMixin` says so outright: "anchor block must always be present in the
        // settlement NBT". It finds that one rather than adding its own.
        //
        // Placing a second gave the town two campfires: mine at the structure's origin, which
        // was the one registered and therefore the one that opened the panel, and the author's
        // own a few blocks away with no town behind it — a campfire that looked identical and
        // did nothing when clicked.
        //
        // Searched in the world rather than read from the NBT so that rotation is handled for
        // free: the anchor sits at a different local offset in each of the three starters.
        BlockPos anchorPos = null;
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(bb.minX(), bb.minY(), bb.minZ()),
                new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()))) {
            if (level.getBlockState(p).is(BlockRegistry.TOWN_ANCHOR)) {
                anchorPos = p.immutable();
                break;
            }
        }
        if (anchorPos == null) {
            src.sendFailure(Component.literal(
                "[OUAT] '" + defId + "' placed but carries no town anchor — the town cannot be"
                + " registered. That NBT is broken, not the command."));
            return 0;
        }

        LevelTowns.get(level).registerTown(anchorPos, town);
        LevelTowns.get(level).markDirty();

        final int free = connections.size();
        final BlockPos at = anchorPos;
        final int stock = granted;
        final int spread = relief;
        src.sendSuccess(() -> Component.literal(
            "[OUAT] Town spawned with starter '" + defId + "', anchor at " + at + ", "
            + free + " free connection point(s), " + stock + " items of starting stock"
            + " — right-click the campfire!"
            // Reported, not refused. The carver handles a slope; past three or four blocks it
            // does so by building a visible dirt plinth, and you should hear that from the
            // command rather than discover it on the contact sheet.
            + (spread > 3 ? "\n[OUAT] Site relief is " + spread + " blocks — the terrain under it"
                + " had to be levelled, so expect a plinth. Flatter ground reads better." : "")),
            true);
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

        int houses = 0, jobs = 0, gardens = 0, streets = 0;
        for (ConnectionPoint cp : town.getAvailableConnectionPoints()) {
            String pool = cp.targetName();
            if (pool.contains("houses"))        houses++;
            else if (pool.contains("jobs"))     jobs++;
            else if (pool.contains("gardens"))  gardens++;
            else if (pool.contains("streets"))  streets++;
        }

        String slot = " free slot";
        StringBuilder sb = new StringBuilder("[Village status]\n");
        sb.append("Houses  : ").append(houses).append(houses == 1 ? slot : slot + "s").append("\n");
        sb.append("Jobs    : ").append(jobs).append(jobs == 1 ? slot : slot + "s").append("\n");
        sb.append("Gardens : ").append(gardens).append(gardens == 1 ? slot : slot + "s").append("\n");
        sb.append("Streets : ").append(streets).append(streets == 1 ? slot : slot + "s");
        if (houses == 0 && jobs == 0 && gardens == 0 && streets == 0) {
            sb.append("\nNo connection points available -- the village cannot expand.");
        }

        String result = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
