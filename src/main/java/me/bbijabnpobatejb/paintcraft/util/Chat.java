package me.bbijabnpobatejb.paintcraft.util;

import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class Chat {

    public final int MAIN = 0xFF7800;
    public final int GRAY = 0xAAAAAA;
    public final int DARK_GRAY = 0x555555;
    public final int GREEN = 0x1FFF5E;
    public final int RED = 0xFF1F1F;
    public final int WHITE = 0xFFFFFF;
    public final int BLUE = 0x1F98FF;
    public final int AQUA = 0x1FFFE3;
    public final int YELLOW = 0xFFE31F;

    public final String MAIN_MM = "<#FF7800>";
    public final String GRADIENT = "<gradient:#FF7800:#FF3C00>";

    public final String POINT = "●";
    public final String CUBE = "▪";
    public final String ARROW = "»";

    public void sendChat(@Nullable CommandSender sender, String miniMessage) {
        if (sender == null) return;
        sendChat(sender, StringUtil.mm(miniMessage));
    }

    public void sendChat(@Nullable CommandSender sender, Component component) {
        if (sender == null) return;
        sender.sendMessage(generateMessage(component));
    }

    public void sendActionBar(@Nullable CommandSender sender, Component component) {
        if (sender == null) return;
        if (sender instanceof Player player) {
            player.sendActionBar(component);
        } else {
            sender.sendMessage(component);
        }
    }

    public Component main(String s) {
        return colorText(s, MAIN);
    }

    public Component gray(String s) {
        return colorText(s, GRAY);
    }

    public Component darkGray(String s) {
        return colorText(s, DARK_GRAY);
    }

    public Component green(String s) {
        return colorText(s, GREEN);
    }

    public Component red(String s) {
        return colorText(s, RED);
    }

    public Component white(String s) {
        return colorText(s, WHITE);
    }

    public Component blue(String s) {
        return colorText(s, BLUE);
    }

    public Component aqua(String s) {
        return colorText(s, AQUA);
    }

    public Component yellow(String s) {
        return colorText(s, YELLOW);
    }

    public Component colorText(String s, int color) {
        return Component.text(s).color(TextColor.color(color));
    }

    public Component getToggleMessage(boolean enabled) {
        return enabled ? green(Lang.get("common.on")) : red(Lang.get("common.off"));
    }

    public String getToggleString(boolean enabled) {
        return enabled ? "<#1FFF5E>" + Lang.get("common.on") : "<#FF1F1F>" + Lang.get("common.off");
    }

    public @NotNull Component generateMessage(Component component) {
        return getPrefix().append(component);
    }

    public @NotNull Component getPrefix() {
        return StringUtil.mm("<#FF7800>" + Lang.get("prefix") + " <dark_gray>» <reset>");
    }
}
