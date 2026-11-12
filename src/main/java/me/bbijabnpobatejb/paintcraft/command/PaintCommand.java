package me.bbijabnpobatejb.paintcraft.command;

import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Sender;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.join.Join;
import dev.rollczi.litecommands.annotations.optional.OptionalArg;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.command.argument.CanvasSize;
import me.bbijabnpobatejb.paintcraft.command.argument.LanguageArgument;
import me.bbijabnpobatejb.paintcraft.diag.ReportService;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.item.PaintItem;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.paint.Tool;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
import me.bbijabnpobatejb.paintcraft.ui.menu.AdminGui;
import me.bbijabnpobatejb.paintcraft.ui.menu.GalleryGui;
import me.bbijabnpobatejb.paintcraft.ui.menu.PaletteGui;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.MathUtil;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Command(name = "paint", aliases = {"paintcraft", "картина", "картины"})
@Permission(PaintCraft.PERMISSION_USE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaintCommand {

    PaintCraft plugin;

    @Execute
    void open(@Sender Player sender) {
        new PaletteGui(plugin, sender).onOpen(sender);
    }

    @Execute(name = "gallery")
    void gallery(@Sender Player sender) {
        new GalleryGui(plugin, sender, 0).onOpen(sender);
    }

    @Execute(name = "help")
    void help(@Sender CommandSender sender) {
        sender.sendMessage(StringUtil.mm(Lang.get("command.help.title")));
        for (String line : Lang.list("command.help.lines")) {
            sender.sendMessage(StringUtil.mm("<gray>" + Chat.CUBE + " " + line));
        }
    }

    @Execute(name = "color")
    void color(@Sender Player sender, @Arg("#RRGGBB") String hex) {
        int rgb = Palette.parseHex(hex);
        if (rgb < 0) {
            Chat.sendChat(sender, Chat.red(Lang.get("common.color-format")));
            return;
        }
        plugin.getPaintService().setColor(sender, Palette.nearest(rgb));
    }

    @Execute(name = "size")
    void size(@Sender Player sender, @Arg("size") int size) {
        plugin.getPaintService().setSize(sender, size);
    }

    @Execute(name = "tool")
    void tool(@Sender Player sender, @Arg("tool") Tool tool) {
        plugin.getPaintService().setTool(sender, tool);
    }

    @Execute(name = "panel")
    void panel(@Sender Player sender) {
        plugin.getPaintService().resetLayout(sender);
        Chat.sendChat(sender, Chat.green(Lang.get("command.panel-reset")));
    }

    @Execute(name = "undo")
    void undo(@Sender Player sender) {
        plugin.getPaintService().undo(sender, false);
    }

    @Execute(name = "redo")
    void redo(@Sender Player sender) {
        plugin.getPaintService().undo(sender, true);
    }

    @Execute(name = "clear")
    void clear(@Sender Player sender) {
        Painting painting = resolve(sender);
        if (painting == null) return;
        val paint = plugin.getPaintService();
        if (!paint.canEdit(sender, painting)) {
            paint.sendCannotEdit(sender, painting);
            return;
        }
        paint.clear(painting);
        Chat.sendActionBar(sender, StringUtil.mm(Lang.get("common.canvas-cleared")));
    }

    @Execute(name = "sign")
    void sign(@Sender Player sender) {
        Painting painting = resolve(sender);
        if (painting != null) plugin.getPrompts().sign(sender, painting);
    }

    @Execute(name = "sign")
    void sign(@Sender Player sender, @Join("title") String title) {
        Painting painting = resolve(sender);
        if (painting != null) plugin.getExchangeService().sign(sender, painting, title);
    }

    @Execute(name = "export")
    void export(@Sender Player sender) {
        Painting painting = resolve(sender);
        if (painting != null) plugin.getExchangeService().export(sender, painting);
    }

    @Execute(name = "import")
    void importCode(@Sender Player sender, @OptionalArg("code") String code) {
        if (code == null) plugin.getPrompts().importCode(sender, null);
        else plugin.getExchangeService().importText(sender, code);
    }

    @Execute(name = "image")
    @Permission(PaintCraft.PERMISSION_IMAGE)
    void image(@Sender Player sender, @OptionalArg("url") String url) {
        plugin.getPrompts().image(sender, url);
    }

    @Execute(name = "admin")
    @Permission(PaintCraft.PERMISSION_ADMIN)
    void admin(@Sender Player sender) {
        new AdminGui(plugin, 0).onOpen(sender);
    }

    @Execute(name = "give")
    @Permission(PaintCraft.PERMISSION_GIVE)
    void give(@Sender CommandSender sender, @Arg("item") PaintItem item, @OptionalArg("WxH") CanvasSize size,
              @OptionalArg("amount") Integer amount, @OptionalArg("player") Player target) {
        Player receiver = target != null ? target : sender instanceof Player player ? player : null;
        if (receiver == null) {
            Chat.sendChat(sender, Chat.red(Lang.get("command.specify-player")));
            return;
        }
        val items = plugin.getItemService();
        ItemStack stack;
        switch (item) {
            case BRUSH -> stack = items.createBrush();
            case CANVAS -> {
                CanvasSize canvas = size == null ? CanvasSize.SINGLE : size;
                stack = items.createCanvas(canvas.width(), canvas.height(), amount == null ? 1 : MathUtil.clamp(amount, 1, 64));
            }
            default -> {
                Chat.sendChat(sender, Chat.red(Lang.get("command.give-painting")));
                return;
            }
        }
        items.give(receiver, stack);
        Chat.sendChat(sender, StringUtil.mm(Lang.get("command.given", "player", receiver.getName())));
    }

    @Execute(name = "language", aliases = {"lang"})
    @Permission(PaintCraft.PERMISSION_ADMIN)
    void language(@Sender CommandSender sender) {
        List<String> languages = new ArrayList<>();
        for (String code : Lang.available()) {
            languages.add(code + " (" + Lang.name(code) + ")");
        }
        Chat.sendChat(sender, StringUtil.mm(Lang.get("language.current",
                "language", Lang.name(Lang.code()) + " (" + Lang.code() + ")")));
        Chat.sendChat(sender, StringUtil.mm(Lang.get("language.available", "languages", String.join(", ", languages))));
    }

    @Execute(name = "language", aliases = {"lang"})
    @Permission(PaintCraft.PERMISSION_ADMIN)
    void language(@Sender CommandSender sender, @Arg("language") LanguageArgument.Code code) {
        plugin.setLanguage(code.value());
        Chat.sendChat(sender, StringUtil.mm(Lang.get("language.changed", "language", Lang.name(code.value()))));
    }

    @Execute(name = "report")
    @Permission(PaintCraft.PERMISSION_ADMIN)
    void report(@Sender CommandSender sender, @OptionalArg("seconds") Integer seconds) {
        plugin.getReportService().start(sender, seconds == null ? ReportService.DEFAULT_SECONDS : seconds);
    }

    @Execute(name = "reload")
    @Permission(PaintCraft.PERMISSION_ADMIN)
    void reload(@Sender CommandSender sender) {
        plugin.reload();
        Chat.sendChat(sender, Chat.green(Lang.get("command.reloaded")));
    }

    private @Nullable Painting resolve(Player player) {
        val items = plugin.getItemService();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean looking = plugin.getPlacementManager().traceVisible(player, plugin.getConfigHandler().getReach()) != null;

        Painting painting = null;
        if (!looking && items.typeOf(hand) == PaintItem.PAINTING) painting = plugin.getStorage().get(items.paintingIdOf(hand));
        if (painting == null) painting = plugin.getPaintService().target(player);
        if (painting == null) Chat.sendChat(player, Chat.red(Lang.get("command.look-at-painting")));
        return painting;
    }
}
