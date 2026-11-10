package me.bbijabnpobatejb.paintcraft.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class StringUtil {

    private final DecimalFormat DECIMAL = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public Component mm(String message) {
        return miniMessage(message);
    }

    public Component miniMessage(String message) {
        return MiniMessage.miniMessage().deserialize(message).compact();
    }

    public List<Component> mmLore(String... lines) {
        return mmLore(List.of(lines));
    }

    public List<Component> mmLore(List<String> lines) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(line.isEmpty() ? Component.empty() : noItalic(mm("<gray>" + line)));
        }
        return lore;
    }

    public String escape(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }

    public Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public String number(double value) {
        return DECIMAL.format(value);
    }
}
