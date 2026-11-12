package me.bbijabnpobatejb.mappaint.command;

import dev.rollczi.litecommands.LiteCommandsBuilder;
import dev.rollczi.litecommands.bukkit.LiteBukkitMessages;
import dev.rollczi.litecommands.bukkit.LiteBukkitSettings;
import dev.rollczi.litecommands.message.LiteMessages;
import dev.rollczi.litecommands.schematic.SchematicFormat;
import lombok.experimental.UtilityClass;
import lombok.val;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.command.argument.CanvasSize;
import me.bbijabnpobatejb.mappaint.command.argument.ChoiceArgument;
import me.bbijabnpobatejb.mappaint.command.argument.LanguageArgument;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.item.PaintItem;
import me.bbijabnpobatejb.mappaint.paint.Tool;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.logging.Level;

@UtilityClass
public class BukkitCommandConfigurator {

    public void apply(LiteCommandsBuilder<CommandSender, LiteBukkitSettings, ?> builder, MapPaint plugin) {
        builder
                .settings(settings -> settings.fallbackPrefix(MapPaint.PLUGIN_ID).nativePermissions(true))
                .message(LiteMessages.INVALID_USAGE, input -> Chat.red(Lang.get("command.invalid-usage")))
                .message(LiteMessages.MISSING_PERMISSIONS,
                        input -> Chat.generateMessage(Chat.red(Lang.get("common.no-permission"))))
                .message(LiteBukkitMessages.PLAYER_ONLY,
                        input -> Chat.generateMessage(Chat.red(Lang.get("command.player-only"))))
                .result(Component.class, (invocation, result, chain) -> invocation.sender().sendMessage(result))
                .argument(Tool.class, new ChoiceArgument<>(Tool.class, "tool"))
                .argument(PaintItem.class, new ChoiceArgument<>(PaintItem.class, "item"))
                .argument(CanvasSize.class, new CanvasSize.Resolver(plugin))
                .argument(LanguageArgument.Code.class, new LanguageArgument())
                .invalidUsage((invocation, result, chain) -> {
                    val sender = invocation.sender();
                    val schematic = result.getSchematic();

                    if (schematic.isOnlyFirst()) {
                        sender.sendMessage(getUsageErrorSingleMessage(schematic.first()));
                        return;
                    }

                    sender.sendMessage(StringUtil.mm(Lang.get("command.usage-header")));
                    for (val scheme : schematic.all()) {
                        sender.sendMessage(getUsageErrorListItem(scheme));
                    }
                })
                .exceptionUnexpected((invocation, exception, chain) -> {
                    plugin.getLogger().log(Level.WARNING, "Command failed: " + invocation.name() + " "
                            + String.join(" ", invocation.arguments().asList()), exception);
                    invocation.sender().sendMessage(Chat.generateMessage(Chat.red(Lang.get("common.error"))));
                })
                .schematicGenerator(SchematicFormat.angleBrackets());
    }

    public @NotNull Component getUsageErrorListItem(String placeholder) {
        return StringUtil.mm("<gray>▪ <white>" + placeholder);
    }

    public @NotNull Component getUsageErrorSingleMessage(String placeholder) {
        return StringUtil.mm(Lang.get("command.usage", "usage", placeholder));
    }
}
