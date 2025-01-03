package org.dawnoftime.onceuponatown.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;

public class CultureInfoCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cultureinfo")
                .then(Commands.argument("cultureid", StringArgumentType.string())
                        .executes(context -> cultureInfo(context.getSource(), StringArgumentType.getString(context, "cultureid"))));
    }

    private static int cultureInfo(CommandSourceStack source, String cultureId) {
        Culture culture = ServerCultures.getCultureOrNull(cultureId);
        if (culture != null) {
            // Eras
            var erasComponent = Component.literal("Eras").withStyle(ChatFormatting.BLUE).append(" (");
            var eras = culture.getEras();
            erasComponent.append(eras.size() + ") :\n");
            eras.forEach((era
                    -> erasComponent.append(Component.literal(era.era() + " - Exp needed : " + era.requiredExperience() + ", max clutter : " + era.maxBuildingsWeight() + "\n"))));

            // Specializations
            var specializationsComponent = Component.literal("Specializations").withStyle(ChatFormatting.BLUE).append(" (");
            var specializations = culture.getSpecializations();
            specializationsComponent.append(specializations.size() + ") : ");
            specializations.forEach((s -> specializationsComponent.append(s + " ")));
            specializationsComponent.append("\n");

            // Starter pack
            var starterPackComponent = Component.literal("Starter pack").withStyle(ChatFormatting.BLUE).append(" (");
            var starterPack = culture.getStarterPack();
            starterPackComponent.append(starterPack.size() + " building" + (starterPack.size() > 1 ? "s" : "") + ") :\n");
            starterPack.forEach((buildTypeId, amountInPack)
                    -> starterPackComponent.append(Component.literal(buildTypeId).withStyle(ChatFormatting.AQUA))
                    .append(" (min : " + amountInPack.getA() + ", max : " + amountInPack.getB() + ")\n"));

            // Build types
            var buildTypesComponent = Component.literal("Buildings (");
            var buildTypes = culture.getBuildTypes();
            buildTypesComponent.append(buildTypes.size() + ") :\n");
            //buildTypes.sort(Comparator.comparing(BuildType::getId));

            buildTypes.forEach((buildType) -> {
                int nbOfVariants = buildType.getBuildVariants().size();
                int nbOfLevels = buildType.getLevels().size();
                buildTypesComponent.append(buildType.getId()).withStyle(ChatFormatting.AQUA)
                        .append(" : " + nbOfVariants + " variant" + (nbOfVariants > 1 ? "s" : "") + ", " + nbOfLevels + " level" + (nbOfLevels > 1 ? "s" : "") + "\n");
            });

            var cultureInfo = Component.literal("Culture '").append(Component.literal(culture.getId()).withStyle(ChatFormatting.YELLOW)).append("'\n")
                    .append(erasComponent).append(specializationsComponent).append(starterPackComponent).append(buildTypesComponent);

            source.sendSuccess(() -> cultureInfo, false);
        } else {
            source.sendFailure(Component.literal("Culture not founded"));
        }
        return 1;
    }
}
