package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class CultureCommand {
    static final SuggestionProvider<CommandSourceStack> SUGGEST_CULTURES = (commandContext, suggestionsBuilder) -> {
        List<String> suggestions = new ArrayList<>();
        ServerCultures.getLoadedCultures().forEach(culture -> suggestions.add(culture.getId()));
        suggestions.addAll(ServerCultures.getCorruptedCultures());
        return SharedSuggestionProvider.suggest(suggestions, suggestionsBuilder);
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("culture")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
            .then(Commands.literal("list")
                .executes(context -> listCultures(context.getSource()))
            )
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .suggests(SUGGEST_CULTURES)
                    .executes(context -> cultureInfo(context.getSource(), StringArgumentType.getString(context, "name")))
                )
            );
    }

    private static int listCultures(CommandSourceStack source) {
        List<Culture> loaded = ServerCultures.getLoadedCultures();
        int loadedSize = loaded.size();
        Set<String> corrupted = ServerCultures.getCorruptedCultures();
        int corruptedSize = corrupted.size();
        MutableComponent message = Component.empty();
        if (loadedSize != 0) {
            List<String> ids = new ArrayList<>();
            loaded.forEach(culture -> ids.add(culture.getId()));
            StringJoiner joiner = new StringJoiner(", ");
            ids.forEach(joiner::add);
            message.append(loadedSize + " culture" + (loadedSize > 1 ? "s" : "") + " loaded :").append(Component.literal(" " + joiner));
        }
        if (corruptedSize != 0) {
            StringJoiner joiner = new StringJoiner(", ");
            corrupted.forEach(joiner::add);
            message.append(CommonComponents.NEW_LINE)
                .append(Component.literal(corruptedSize + " culture" + (corruptedSize > 1 ? "s" : "") + " corrupted :").withStyle(ChatFormatting.RED))
                .append(Component.literal(" " + joiner));
        }
        if (loadedSize == 0 && corruptedSize == 0) {
            message.append("No cultures founded");
        }
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static int cultureInfo(CommandSourceStack source, String cultureId) {
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture != null) {
            var eras = culture.getEras();
            var specializations = culture.getSpecializations();
            var buildTypes = culture.getBuildTypes();
            var starterPack = culture.getStarterPack();
            // TITLE
            MutableComponent output = Component.literal("Culture ")
                .append(Component.literal(culture.getId()).withStyle(ChatFormatting.YELLOW))
                .append(" :")
                .append(CommonComponents.NEW_LINE)
                .append(CommonComponents.NEW_LINE)
                // ERAS
                .append(Component.literal("Eras").withStyle(ChatFormatting.GREEN))
                .append(" (")
                .append(Component.literal(String.valueOf(eras.size())).withStyle(ChatFormatting.GRAY))
                .append(") :")
                .append(CommonComponents.NEW_LINE);
            eras.forEach(era -> output
                .append("Required xp :")
                .append(Component.literal(" " + era.requiredExperience()).withStyle(ChatFormatting.GRAY))
                .append(", max buildings weight :")
                .append(Component.literal(" " + era.maxBuildingsWeight()).withStyle(ChatFormatting.GRAY))
                .append(CommonComponents.NEW_LINE));
            // SPECIALIZATIONS
            output
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal("Specializations").withStyle(ChatFormatting.GREEN))
                .append(" (")
                .append(Component.literal(String.valueOf(specializations.size())).withStyle(ChatFormatting.GRAY))
                .append(") :");
            specializations.forEach((spe ->
                output.append(Component.literal(" " + spe.getId()).withStyle(ChatFormatting.DARK_AQUA))));
            // BUILDING TYPES
            output
                .append(CommonComponents.NEW_LINE)
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal("Buildings").withStyle(ChatFormatting.GREEN))
                .append(" (")
                .append(Component.literal(String.valueOf(buildTypes.size())).withStyle(ChatFormatting.GRAY))
                .append(") :")
                .append(CommonComponents.NEW_LINE);
            buildTypes.forEach((type) -> output
                .append(Component.literal(" " + type.getId()).withStyle(ChatFormatting.DARK_AQUA))
                .append(" (")
                .append(Component.literal(type.getLevels().size() + " ").withStyle(ChatFormatting.GRAY))
                .append("level" + (type.getLevels().size() > 1 ? "s" : "") + ", ")
                .append(Component.literal(type.getBuildVariants().size() + " ").withStyle(ChatFormatting.GRAY))
                .append("variant" + (type.getBuildVariants().size() > 1 ? "s" : "") + ")")
                .append(CommonComponents.NEW_LINE));
            // STARTER PACK
            output
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal("Starter pack").withStyle(ChatFormatting.GREEN))
                .append(" (")
                .append(Component.literal(String.valueOf(starterPack.size())).withStyle(ChatFormatting.GRAY))
                .append(") :")
                .append(CommonComponents.NEW_LINE);
            starterPack.forEach((type, minMax) -> output
                .append(Component.literal(" " + type).withStyle(ChatFormatting.DARK_AQUA))
                .append(" (min :")
                .append(Component.literal(" " + minMax.getA()).withStyle(ChatFormatting.GRAY))
                .append(", max :")
                .append(Component.literal(" " + minMax.getA()).withStyle(ChatFormatting.GRAY))
                .append(")")
                .append(CommonComponents.NEW_LINE));

            source.sendSuccess(() -> output, true);
        } else if (ServerCultures.getCorruptedCultures().contains(cultureId)) {
            source.sendSuccess(() -> Component.literal("This culture is corrupted").withStyle(ChatFormatting.RED), true);
        } else {
            source.sendFailure(Component.literal("Culture not founded"));
        }
        return 1;
    }
}
