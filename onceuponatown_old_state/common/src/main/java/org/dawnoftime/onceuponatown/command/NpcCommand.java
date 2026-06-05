package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.Profession;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;

import java.util.ArrayList;
import java.util.List;

public class NpcCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(Component.translatable("commands.summon.invalidPosition"));
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROFESSIONS = (context, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        String cultureId = StringArgumentType.getString(context, "culture");
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture != null) {
            culture.getProfessions().forEach(profession -> suggestions.add(profession.getId()));
        }
        suggestions.add(Profession.UNEMPLOYED.getId());
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("npc")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
            .then(Commands.literal("spawn")
                .then(Commands.argument("culture", StringArgumentType.string())
                    .suggests(CultureCommand.SUGGEST_CULTURES)
                    .then(Commands.argument("profession", StringArgumentType.string())
                        .suggests(SUGGEST_PROFESSIONS)
                        .executes(context -> spawnNpc(context.getSource(), StringArgumentType.getString(context, "culture"), StringArgumentType.getString(context, "profession"), 1, context.getSource().getPosition()))
                        .then(Commands.argument("position", Vec3Argument.vec3())
                            .executes(context -> spawnNpc(context.getSource(), StringArgumentType.getString(context, "culture"), StringArgumentType.getString(context, "profession"), 1, Vec3Argument.getVec3(context, "position")))
                        )
                    )
                )
            );
    }

    private static int spawnNpc(CommandSourceStack source, String cultureId, String professionId, int professionLevel, Vec3 position) throws CommandSyntaxException {
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture == null) {
            source.sendFailure(Component.literal("Culture not founded"));
            return 0;
        }
        Profession profession;
        if (professionId.equals(Profession.UNEMPLOYED.getId())) {
            profession = Profession.UNEMPLOYED;
        } else {
            profession = culture.getProfessionOrNull(professionId);
        }
        if (profession == null) {
            source.sendFailure(Component.literal("Profession not founded"));
            return 0;
        }
        if ((professionLevel < 1)) {
            source.sendFailure(Component.literal("Profession level must be greater than 0"));
            return 0;
        }
        BlockPos blockpos = BlockPos.containing(position);
        if (!Level.isInSpawnableBounds(blockpos)) {
            throw INVALID_POSITION.create();
        }
        Level level = source.getLevel();
        Npc npc = EntityRegistry.REGISTRY.NPC.get().create(level);
        if (npc == null) {
            source.sendFailure(Component.literal("Failed to summon the npc"));
            return 0;
        }
        npc.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        npc.finalizeSpawn(source.getLevel(), source.getLevel().getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.COMMAND, null, null);
        if (!level.addFreshEntity(npc)) {
            source.sendFailure(Component.literal("Failed to summon the npc"));
            return 0;
        }
        npc.setCulture(culture.getId());
        npc.setProfession(profession);
        source.sendSuccess(() -> Component.literal("Successfully summoned the npc"), true);
        return 1;
    }

    private static int setProfession(CommandSourceStack source, String professionId) {
        return 1;
    }

    private static int setProfessionLevel(CommandSourceStack source, String professionId) {
        return 1;
    }
}
