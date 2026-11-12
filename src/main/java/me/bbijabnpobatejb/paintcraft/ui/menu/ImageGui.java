package me.bbijabnpobatejb.paintcraft.ui.menu;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.exchange.ImageOptions;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.ItemUtil;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import me.bbijabnpobatejb.paintcraft.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ImageGui extends InventoryGuiImpl {

    final PaintCraft plugin;
    final Player viewer;
    String url;
    ImageOptions.Fit fit;
    int zoom, x, y, brightness, contrast, saturation, width, height;
    boolean dither, shade, clear;

    public ImageGui(PaintCraft plugin, Player viewer, @Nullable String url) {
        super(27, Component.text(Lang.get("dialog.image.title")));
        this.plugin = plugin;
        this.viewer = viewer;

        ImageOptions saved = plugin.getPaintService().session(viewer).getImageOptions();
        ImageOptions last = saved != null ? saved : ImageOptions.defaults("", 1, 1);
        this.url = url != null ? url : last.url();
        fit = last.fit();
        zoom = Math.round(last.zoom() * 100);
        x = Math.round(last.offsetX() * 100);
        y = Math.round(last.offsetY() * 100);
        brightness = Math.round(last.brightness() * 100);
        contrast = Math.round(last.contrast() * 100);
        saturation = Math.round(last.saturation() * 100);
        width = last.blocksW();
        height = last.blocksH();
        dither = last.dither();
        shade = last.blockShade();
        clear = last.clearFill();
        init();
        render();
    }

    @Override
    public void init() {
        addCloseButton();
        int max = plugin.getConfigHandler().getMaxSize();

        addButton(2, ItemUtil.builder(Material.NAME_TAG)
                .displayName(Chat.white(Lang.get("dialog.image.url")))
                .lore(StringUtil.mmLore(url.isBlank() ? "<dark_gray>—" : "<white>" + StringUtil.escape(shorten(url))))
                .addLore(ActionLoreBuilder.create(Lang.get("action.change")).build())
                .build(), (gui, player, click) -> {
            plugin.getChatInput().ask(player, Lang.get("input.url"), text -> {
                url = text;
                reopen(player);
            });
            return true;
        });
        addButton(3, ItemUtil.builder(Material.ITEM_FRAME)
                .displayName(Chat.white(Lang.get("dialog.image.fit.label") + ": " + fit.getDisplayName()))
                .addLore(ActionLoreBuilder.create(Lang.get("action.change")).build())
                .build(), (gui, player, click) -> {
            ImageOptions.Fit[] values = ImageOptions.Fit.values();
            fit = values[(fit.ordinal() + 1) % values.length];
            refresh();
            return true;
        });
        number(4, Material.SPYGLASS, "dialog.image.zoom", zoom, 25, 100, 400, v -> zoom = v);
        number(5, Material.ARROW, "dialog.image.x", x, 10, -100, 100, v -> x = v);
        number(6, Material.HOPPER, "dialog.image.y", y, 10, -100, 100, v -> y = v);

        number(11, Material.GLOWSTONE_DUST, "dialog.image.brightness", brightness, 10, -100, 100, v -> brightness = v);
        number(12, Material.QUARTZ, "dialog.image.contrast", contrast, 10, -100, 100, v -> contrast = v);
        number(13, Material.RED_DYE, "dialog.image.saturation", saturation, 10, -100, 100, v -> saturation = v);
        toggle(14, "dialog.image.dither", dither, () -> dither = !dither);
        toggle(15, "dialog.image.shade", shade, () -> shade = !shade);
        toggle(16, "dialog.image.clear", clear, () -> clear = !clear);

        number(21, Material.PAPER, "dialog.image.width", Math.max(1, Math.min(max, width)), 1, 1, max, v -> width = v);
        number(22, Material.PAPER, "dialog.image.height", Math.max(1, Math.min(max, height)), 1, 1, max, v -> height = v);
        addButton(26, ItemUtil.builder(Material.LIME_DYE)
                .displayName(Chat.green(Lang.get("dialog.image.button")))
                .addLore(ActionLoreBuilder.create(Lang.get("action.select")).build())
                .build(), (gui, player, click) -> {
            if (url.isBlank()) {
                Chat.sendChat(player, Chat.red(Lang.get("dialog.image.no-url")));
                return false;
            }
            player.closeInventory();
            plugin.getExchangeService().importImage(player, new ImageOptions(url.trim(), width, height, fit,
                    zoom / 100f, x / 100f, y / 100f, dither, clear, brightness / 100f, contrast / 100f, saturation / 100f, shade));
            return true;
        });
    }

    private void number(int slot, Material icon, String key, int value, int step, int min, int max, IntConsumer set) {
        addButton(slot, ItemUtil.builder(icon)
                .displayName(StringUtil.mm("<white>" + Lang.get(key) + ": <#FF7800>" + value))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.palette.size.more"))
                        .rightClick(Lang.get("menu.palette.size.less"))
                        .build())
                .build(), (gui, player, click) -> {
            set.accept(Math.max(min, Math.min(max, value + (click.isRightClick() ? -step : step))));
            refresh();
            return true;
        });
    }

    private void toggle(int slot, String key, boolean value, Runnable flip) {
        addButton(slot, ItemUtil.builder(value ? Material.LIME_DYE : Material.GRAY_DYE)
                .displayName(StringUtil.mm("<white>" + Lang.get(key) + ": " + Chat.getToggleString(value)))
                .addLore(ActionLoreBuilder.create(Lang.get("action.toggle")).build())
                .build(), (gui, player, click) -> {
            flip.run();
            refresh();
            return true;
        });
    }

    private void reopen(Player player) {
        clear();
        init();
        render();
        onOpen(player);
    }

    private String shorten(String text) {
        return text.length() <= 48 ? text : text.substring(0, 45) + "...";
    }

    @Override
    public boolean shouldRenderHelp() {
        return false;
    }
}
