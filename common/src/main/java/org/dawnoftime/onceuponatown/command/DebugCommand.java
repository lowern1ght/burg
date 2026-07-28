package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.FoodListDataHandler;
import org.dawnoftime.onceuponatown.datapack.TradePriceDataHandler;
import net.minecraft.world.entity.npc.Villager;
import org.dawnoftime.onceuponatown.entity.citizen.Citizens;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownIntegrity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * State dumps for diagnosis. <b>To the log first, chat second.</b>
 *
 * <p>Written because every real fault this mod has shown was SILENT, not loud. Positions
 * deserialising to (0,0,0) drew every building on top of every other and made the server
 * refuse every trade without a word. A citizen quietly losing its profession to vanilla's
 * `LoseJobOnSiteLoss` looked like "the clothes don't work". None of it raised an exception,
 * so the log was clean and the screen just looked wrong. A dump is how you tell "it did
 * nothing" apart from "it did something you did not expect".
 *
 * <p>The log is the primary target on purpose. Chat truncates, scrolls and cannot be pasted
 * accurately; `logs/latest.log` can be read in full, afterwards, by someone who was not at
 * the keyboard. Chat gets a one-line verdict so you know the command ran.
 *
 * <pre>
 *   /ouat debug            everything below, in order
 *   /ouat debug town       anchor, population, stock, buildings and their REAL origins
 *   /ouat debug citizens   every resident: name, trade, xp, face, job site, home
 *   /ouat debug data       how many entries each datapack handler actually loaded
 *   /ouat debug pos        round-trips a BlockPos through NBT — the regression guard
 * </pre>
 */
public final class DebugCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugCommand.class);

    /** How far around the caller to look for towns and citizens. */
    private static final int RANGE = 192;

    private DebugCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("debug")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> all(ctx))
            .then(Commands.literal("town").executes(ctx -> run(ctx, "town")))
            .then(Commands.literal("citizens").executes(ctx -> run(ctx, "citizens")))
            .then(Commands.literal("data").executes(ctx -> run(ctx, "data")))
            .then(Commands.literal("pos").executes(ctx -> run(ctx, "pos")))
            .then(Commands.literal("verify").executes(DebugCommand::verify))
            // --- controls, not reports. A debug menu that can only look is half a tool: most
            // of what needs testing is a LATER rung, and reaching it legitimately costs an
            // hour of feeding a town. These skip the economy, and only the economy.
            .then(Commands.literal("upgrade")
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 9))
                    .executes(ctx -> upgrade(ctx, IntegerArgumentType.getInteger(ctx, "level")))))
            .then(Commands.literal("population")
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 40))
                    .executes(ctx -> population(ctx,
                        IntegerArgumentType.getInteger(ctx, "count")))))
            .then(Commands.literal("enlist")
                .requires(source -> source.hasPermission(2))
                .executes(DebugCommand::enlistStrays))
            .then(Commands.literal("clear")
                .executes(DebugCommand::clearCitizens));
    }

    /**
     * Does the saved state still agree with the world? One line per disagreement, or "clean".
     *
     * <p>Separate from the {@code town} dump on purpose. A dump prints what is there and leaves
     * the reading to you; this one has an opinion, so silence from it means something. Every
     * fault it looks for is one that has actually happened in this repo: an anchor gone and its
     * town orphaned, a level NBT that will not load so its upgrade is a no-op, a building at a
     * level higher than the count of level files, a town entry that would not parse at all.
     */
    private static int verify(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<String> findings = TownIntegrity.audit(level);
        int towns = LevelTowns.get(level).getAllTowns().size();

        LOGGER.info("[OUAT-VERIFY] dimension={} towns={} findings={}",
            level.dimension().location(), towns, findings.size());
        for (String f : findings) LOGGER.warn("[OUAT-VERIFY]   {}", f);

        StringBuilder sb = new StringBuilder("[OUAT] verify: ")
            .append(towns).append(" town(s) in ").append(level.dimension().location());
        if (findings.isEmpty()) {
            sb.append(" — clean.");
        } else {
            sb.append(", ").append(findings.size()).append(" finding(s):");
            for (String f : findings) sb.append("\n  • ").append(f);
        }
        String out = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(out), false);
        return findings.isEmpty() ? 1 : 0;
    }

    private static int all(CommandContext<CommandSourceStack> ctx) {
        run(ctx, "pos");
        run(ctx, "data");
        run(ctx, "town");
        run(ctx, "citizens");
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String section) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        List<String> out = new ArrayList<>();

        switch (section) {
            case "pos" -> positions(out);
            case "data" -> data(out);
            case "town" -> towns(out, level, at);
            case "citizens" -> citizens(out, level, at);
            default -> out.add("unknown section " + section);
        }

        LOGGER.info("[OUAT debug/{}] ----------------------------------------", section);
        for (String line : out) {
            LOGGER.info("[OUAT debug/{}] {}", section, line);
        }
        src.sendSuccess(() -> Component.literal(
            "[OUAT] debug/" + section + ": " + out.size() + " lines written to logs/latest.log"),
            false);
        return 1;
    }

    /**
     * Set every nearby building to a level, so a rung can be looked at without earning it.
     *
     * <p>Writes the level and nothing else: the stock, the residents and the production
     * multipliers all resolve FROM the level at read time, so this is not a shortcut that
     * leaves an inconsistent town behind. What it does skip is the NBT swap — the structure on
     * the ground stays whatever was built, so this shows the level's numbers, not its geometry.
     * Rebuilding the structure is `UpgradeAction`'s job and deliberately not duplicated here.
     */
    private static int upgrade(CommandContext<CommandSourceStack> ctx, int level) {
        CommandSourceStack src = ctx.getSource();
        BlockPos at = BlockPos.containing(src.getPosition());
        int touched = 0;
        for (var e : LevelTowns.get(src.getLevel()).getAllTownEntries()) {
            if (BlockPos.of(e.getKey()).distSqr(at) > (double) RANGE * RANGE) continue;
            for (PlacedBuilding b : e.getValue().getBuildings()) {
                b.setUpgradeLevel(level);
                touched++;
            }
        }
        LevelTowns.get(src.getLevel()).markDirty();
        final int n = touched;
        src.sendSuccess(() -> Component.literal(
            "[OUAT] set " + n + " building(s) to level " + level
            + " (numbers only — the structure on the ground is unchanged)"), true);
        return 1;
    }

    /**
     * Bring the citizen count up to `count`, or report why it cannot.
     *
     * <p>Only ever ADDS. Deleting people to hit a number is what `clear` is for, and keeping
     * the two apart means a mistyped population cannot quietly kill a town you were testing.
     */
    private static int population(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        BlockPos anchor = LevelTowns.get(level).getAllTownEntries().stream()
            .map(e -> BlockPos.of(e.getKey()))
            .min(java.util.Comparator.comparingDouble(a -> a.distSqr(at))).orElse(null);

        int have = Citizens.in(level, new AABB(at).inflate(RANGE)).size();
        int spawned = 0;
        for (int i = have; i < count; i++) {
            Villager c = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (c == null) break;
            // Spread them so they do not stack in one column and shove each other.
            double a = i * 2.399963;                    // golden angle: no clumps, no grid
            double r = 2.0 + 0.6 * i;
            c.moveTo(at.getX() + 0.5 + Math.cos(a) * r, at.getY(),
                     at.getZ() + 0.5 + Math.sin(a) * r, (float) Math.toDegrees(a), 0f);
            if (anchor != null) Citizens.enlist(c, anchor);
            level.addFreshEntity(c);
            spawned++;
        }
        final int n = spawned;
        final int was = have;
        src.sendSuccess(() -> Component.literal(
            "[OUAT] citizens " + was + " -> " + (was + n)
            + (n == 0 ? " (already at or above " + count + ")" : "")), true);
        return 1;
    }

    /**
     * Take every unaffiliated villager nearby into the nearest town.
     *
     * <p>A retrofit, and it exists because enlisting at spawn cannot reach backwards. Villagers
     * that shipped inside a building placed before that code existed — and any that wandered in
     * from a vanilla village — are already in the world as strangers, counted by nothing and
     * drawn on vanilla's rig. This is how an existing save catches up without being rebuilt.
     */
    private static int enlistStrays(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        BlockPos anchor = LevelTowns.get(level).getAllTownEntries().stream()
            .map(e -> BlockPos.of(e.getKey()))
            .min(java.util.Comparator.comparingDouble(a -> a.distSqr(at))).orElse(null);
        if (anchor == null) {
            src.sendFailure(Component.literal("[OUAT] No town in this level to enlist them into"));
            return 0;
        }

        int taken = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(RANGE))) {
            if (Citizens.isCitizen(v)) continue;
            Citizens.enlist(v, anchor);
            taken++;
        }
        final int n = taken;
        src.sendSuccess(() -> Component.literal(
            "[OUAT] enlisted " + n + " villager(s) into the town at " + anchor
            + (n == 0 ? " (none were unaffiliated)" : "")), true);
        return 1;
    }

    /** Remove every nearby citizen. Separate from `population` on purpose. */
    private static int clearCitizens(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        BlockPos at = BlockPos.containing(src.getPosition());
        // Only ours. Sweeping every Villager in range would delete the village the town was
        // built next to, which is not what "clear the citizens I spawned" means.
        List<Villager> found = Citizens.in(src.getLevel(), new AABB(at).inflate(RANGE));
        found.forEach(Villager::discard);
        src.sendSuccess(() -> Component.literal(
            "[OUAT] removed " + found.size() + " citizen(s)"), true);
        return 1;
    }

    /**
     * Round-trips a position through NBT in both layouts.
     *
     * <p>This is the test that would have caught the session's worst bug in one command. The
     * writes had migrated to `NbtUtils`, which emits an int array since 1.21, while the reader
     * still pulled `getInt("X")` off a compound and therefore got 0 — silently, because
     * `getCompound` on an int array yields an empty compound rather than an error.
     */
    private static void positions(List<String> out) {
        BlockPos probe = new BlockPos(-1429, 71, -810);   // the anchor from the test world

        CompoundTag modern = new CompoundTag();
        modern.put("P", Constants.writeBlockPos(probe));
        BlockPos backModern = Constants.readBlockPos(modern, "P");

        CompoundTag legacy = new CompoundTag();
        CompoundTag inner = new CompoundTag();
        inner.putInt("X", probe.getX());
        inner.putInt("Y", probe.getY());
        inner.putInt("Z", probe.getZ());
        legacy.put("P", inner);
        BlockPos backLegacy = Constants.readBlockPos(legacy, "P");

        out.add("probe            " + probe);
        out.add("written tag type " + modern.get("P").getType().getName()
            + "  (int array since 1.21)");
        out.add("modern round-trip " + backModern
            + (probe.equals(backModern) ? "   OK" : "   BROKEN — every position reads wrong"));
        out.add("legacy round-trip " + backLegacy
            + (probe.equals(backLegacy) ? "   OK (old saves still readable)" : "   BROKEN"));
    }

    /** Did the datapack actually load? An empty price table is a dead Buy button. */
    private static void data(List<String> out) {
        // No count is exposed, so ask the table itself: an empty price map answers every
        // item with 0, and `C2SBuyPacket` skips anything priced 0. That is a dead Buy button.
        CompoundTag prices = TradePriceDataHandler.buildPricesTag();
        out.add("trade_prices.json  " + prices.getAllKeys().size() + " priced items"
            + (prices.getAllKeys().isEmpty()
                ? "   <-- nothing is tradable, every purchase is a silent no-op" : ""));
        out.add("buildings          " + BuildingDataHandler.getAll().size() + " definitions");
        out.add("food               " + FoodListDataHandler.residentEntriesInOrder().size()
            + " resident foods, feeding at " + FoodListDataHandler.getFeedingSchedule());
    }

    private static void towns(List<String> out, ServerLevel level, BlockPos at) {
        var entries = LevelTowns.get(level).getAllTownEntries();
        out.add(entries.size() + " town(s) in this level");
        for (var e : entries) {
            BlockPos anchor = BlockPos.of(e.getKey());
            if (anchor.distSqr(at) > (double) RANGE * RANGE) continue;
            Town town = e.getValue();
            out.add("town @ " + anchor
                + "  residents " + town.getActiveResidents() + "/" + town.getTotalResidents()
                + " fed  builders " + town.getBuilderNpcIds().size());
            for (PlacedBuilding b : town.getBuildings()) {
                // The origin is printed because it is what the map draws from. All of them
                // reading the same value is the stacked-buildings bug, visible at a glance.
                out.add("   " + b.getDefId() + " lvl" + b.getUpgradeLevel()
                    + " @ " + b.worldPos
                    + "  stock " + stockOf(b));
            }
        }
    }

    private static String stockOf(PlacedBuilding b) {
        StringBuilder sb = new StringBuilder();
        for (Item item : b.getStockedItems()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(item.getDescriptionId()).append(" x").append(b.getStock(item));
        }
        return sb.length() == 0 ? "(empty)" : sb.toString();
    }

    /**
     * Every citizen and builder nearby, with the fields that have actually gone wrong.
     *
     * <p>`profession` and `xp` are together on purpose: vanilla strips a trade from any
     * villager with no claimed job site and zero experience, so a citizen reading
     * `none xp=0` is not a broken texture, it is a villager that was demoted. `anchor`
     * is here because a builder that loses it discards itself on the next load.
     */
    private static void citizens(List<String> out, ServerLevel level, BlockPos at) {
        AABB box = new AABB(at).inflate(RANGE);

        List<Villager> residents = Citizens.in(level, box);
        // Reported beside the citizens on purpose: an ordinary villager wandering the town is
        // the thing that dilutes the population and competes for workstations, and until now
        // it was invisible to every command. Seven of them ship inside the author's own NBTs.
        int strangers = level.getEntitiesOfClass(Villager.class, box).size() - residents.size();
        long women = residents.stream().filter(Citizens::isFemale).count();
        // Reported as a ratio because that is the thing that goes wrong: a fair coin per person
        // is not a balanced town, and a town that cannot pair off cannot have children.
        out.add(residents.size() + " citizen(s) — " + women + "f / " + (residents.size() - women)
            + "m — and " + strangers + " unaffiliated villager(s) within " + RANGE + " blocks");
        for (Villager c : residents) {
            VillagerProfession prof = c.getVillagerData().getProfession();
            out.add(String.format(
                "   %-22s %s %-12s xp=%-3d face=%d tint=%d type=%s anchor=%s job=%s home=%s",
                Citizens.nameOf(c), Citizens.isFemale(c) ? "f" : "m",
                prof.name(), c.getVillagerXp(), Citizens.faceOf(c), Citizens.tintOf(c),
                c.getVillagerData().getType(),
                Citizens.anchorOf(c) == null ? "NONE" : Citizens.anchorOf(c),
                memory(c, net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE),
                memory(c, net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)));
        }

        List<Npc> builders = level.getEntitiesOfClass(Npc.class, box);
        out.add(builders.size() + " builder(s) within " + RANGE + " blocks");
        for (Npc n : builders) {
            out.add("   builder " + n.getUUID().toString().substring(0, 8)
                + " anchor=" + (n.getTownAnchorPos() == null ? "NONE — will discard on load"
                                                            : n.getTownAnchorPos())
                + " reading=" + n.isReading());
        }
    }

    private static String memory(Villager c,
            net.minecraft.world.entity.ai.memory.MemoryModuleType<net.minecraft.core.GlobalPos> type) {
        return c.getBrain().getMemory(type).map(g -> g.pos().toShortString()).orElse("-");
    }
}
