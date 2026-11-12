package me.bbijabnpobatejb.mappaint.compat;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.palette.Palette;
import me.bbijabnpobatejb.mappaint.ui.menu.AssembleGui;
import me.bbijabnpobatejb.mappaint.ui.menu.ImageGui;
import me.bbijabnpobatejb.mappaint.ui.menu.PaletteGui;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatPrompts implements Prompts {

    private static final int PART_LIMIT = 200;

    MapPaint plugin;

    @Override
    public int partLimit() {
        return PART_LIMIT;
    }

    @Override
    public void export(Player player, Painting painting, List<String> parts, int index) {
        int length = parts.stream().mapToInt(String::length).sum();
        Chat.sendChat(player, StringUtil.mm(Lang.get("export.chat.header",
                "title", StringUtil.escape(painting.getDisplayTitle()), "length", length)));

        Component line = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            String label = parts.size() == 1 ? Lang.get("dialog.export.copy") : Lang.get("export.chat.part", "part", i + 1);
            line = line.append(Chat.main("[" + label + "]")
                            .clickEvent(ClickEvent.copyToClipboard(parts.get(i)))
                            .hoverEvent(HoverEvent.showText(Chat.gray(Lang.get("dialog.export.copy-tooltip")))))
                    .append(Component.space());
        }
        player.sendMessage(line);
        if (parts.size() > 1) Chat.sendChat(player, Chat.gray(Lang.get("export.chat.hint")));
    }

    @Override
    public void importCode(Player player, @Nullable String hint) {
        if (!player.hasPermission(MapPaint.PERMISSION_IMPORT) || !plugin.getConfigHandler().isImportCode()) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        plugin.getChatInput().ask(player, hint != null ? hint : Lang.get("input.import"),
                code -> plugin.getExchangeService().importText(player, code));
    }

    @Override
    public void sign(Player player, Painting painting) {
        plugin.getChatInput().ask(player, Lang.get("input.sign"),
                title -> plugin.getExchangeService().sign(player, painting, title));
    }

    @Override
    public void hex(Player player) {
        plugin.getChatInput().ask(player, Lang.get("input.hex"), value -> {
            int rgb = Palette.parseHex(value);
            if (rgb < 0) Chat.sendChat(player, Chat.red(Lang.get("common.color-format")));
            else plugin.getPaintService().setColor(player, Palette.nearest(rgb));
            new PaletteGui(plugin, player).onOpen(player);
        });
    }

    @Override
    public void canvas(Player player) {
        if (plugin.getRecipeService().unitCanvases(player) > 0) new AssembleGui(plugin, player).onOpen(player);
    }

    @Override
    public void image(Player player, @Nullable String url) {
        if (!player.hasPermission(MapPaint.PERMISSION_IMAGE) || !plugin.getConfigHandler().isImageUrl()) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        new ImageGui(plugin, player, url).onOpen(player);
    }
}
