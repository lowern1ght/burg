package org.dawnoftime.onceuponatown.command;

import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CultureInfoCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cultureinfo")
                .then(Commands.argument("cultureid", StringArgumentType.string())
                .executes(context -> cultureInfo(context.getSource(), StringArgumentType.getString(context, "cultureid"))));
    }

    private static int cultureInfo(CommandSourceStack source, String cultureId) {
        Culture culture = CultureManager.getCultureById(cultureId);
        if (culture != null) {
            var erasComponent = Component.literal("Eras :");
            List<Culture.Era> eras = culture.getEras();
            for (int i = 0; i < eras.size(); ++i) {
                Culture.Era era = eras.get(i);
                erasComponent.append(Component.literal("\n" + era.order() + " - Required xp : " + era.requiredXp() + ", Max buildings weight : " + era.buildingsWeight()));
            }
            var cultureInfo = Component.literal("Culture ").append(Component.literal(culture.getId() + "\n").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("Starterpack min size : " + culture.getStarterPackMinSize() + "\n"))
                    .append(Component.literal("Starterpack max size : " + culture.getStarterPackMaxSize() + "\n"))
                    .append(erasComponent);

            source.sendSuccess(() -> cultureInfo, false);
        } else {
            source.sendSuccess(() -> Component.literal("Culture not found"), false);
        }
        return 1;
    }
}
