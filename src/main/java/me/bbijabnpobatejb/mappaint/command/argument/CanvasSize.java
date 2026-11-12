package me.bbijabnpobatejb.mappaint.command.argument;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.util.Chat;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public record CanvasSize(int width, int height) {

    public static final CanvasSize SINGLE = new CanvasSize(1, 1);

    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Resolver extends ArgumentResolver<CommandSender, CanvasSize> {

        MapPaint plugin;

        @Override
        protected ParseResult<CanvasSize> parse(Invocation<CommandSender> invocation, Argument<CanvasSize> argument, String input) {
            int max = plugin.getConfigHandler().getMaxSize();
            String[] parts = input.toLowerCase(Locale.ROOT).replace('х', 'x').replace('×', 'x').split("x");
            try {
                if (parts.length == 2) {
                    int width = Integer.parseInt(parts[0]), height = Integer.parseInt(parts[1]);
                    if (width >= 1 && height >= 1 && width <= max && height <= max) {
                        return ParseResult.success(new CanvasSize(width, height));
                    }
                }
            } catch (NumberFormatException ignored) {
            }
            return ParseResult.failure(Chat.generateMessage(Chat.red(Lang.get("command.canvas-size", "max", max))));
        }

        @Override
        public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<CanvasSize> argument, SuggestionContext context) {
            int max = plugin.getConfigHandler().getMaxSize();
            return SuggestionResult.of("1x1", "2x1", "2x2", "3x2", max + "x" + max);
        }
    }
}
