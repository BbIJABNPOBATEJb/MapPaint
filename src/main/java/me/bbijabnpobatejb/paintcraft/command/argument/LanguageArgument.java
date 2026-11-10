package me.bbijabnpobatejb.paintcraft.command.argument;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public class LanguageArgument extends ArgumentResolver<CommandSender, LanguageArgument.Code> {

    public record Code(String value) {
    }

    @Override
    protected ParseResult<Code> parse(Invocation<CommandSender> invocation, Argument<Code> context, String argument) {
        String code = argument.toLowerCase(Locale.ROOT);
        if (Lang.isAvailable(code)) return ParseResult.success(new Code(code));

        return ParseResult.failure(Chat.generateMessage(Chat.red(Lang.get("language.unknown",
                "input", argument,
                "languages", String.join(", ", Lang.available())))));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<Code> argument, SuggestionContext context) {
        return SuggestionResult.of(Lang.available());
    }
}
