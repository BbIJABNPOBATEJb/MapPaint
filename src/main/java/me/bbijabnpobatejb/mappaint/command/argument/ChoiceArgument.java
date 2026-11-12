package me.bbijabnpobatejb.mappaint.command.argument;

import dev.rollczi.litecommands.argument.Argument;
import dev.rollczi.litecommands.argument.parser.ParseResult;
import dev.rollczi.litecommands.argument.resolver.ArgumentResolver;
import dev.rollczi.litecommands.invocation.Invocation;
import dev.rollczi.litecommands.suggestion.SuggestionContext;
import dev.rollczi.litecommands.suggestion.SuggestionResult;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.util.Chat;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChoiceArgument<E extends Enum<E>> extends ArgumentResolver<CommandSender, E> {

    E[] values;
    String what;

    public ChoiceArgument(Class<E> type, String what) {
        this.values = type.getEnumConstants();
        this.what = what;
    }

    @Override
    protected ParseResult<E> parse(Invocation<CommandSender> invocation, Argument<E> argument, String input) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(input)) return ParseResult.success(value);
        }
        return ParseResult.failure(Chat.generateMessage(Chat.red(
                Lang.get("command.unknown-choice", "input", input, "what", Lang.get("command.choice." + what)))));
    }

    @Override
    public SuggestionResult suggest(Invocation<CommandSender> invocation, Argument<E> argument, SuggestionContext context) {
        List<String> names = new ArrayList<>(values.length);
        for (E value : values) names.add(value.name().toLowerCase(Locale.ROOT));
        return SuggestionResult.of(names);
    }
}
