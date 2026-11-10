package me.bbijabnpobatejb.paintcraft.ui.menu;

import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.item.ItemService;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.ui.Dialogs;
import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.ItemUtil;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import me.bbijabnpobatejb.paintcraft.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public class CanvasGui extends InventoryGuiImpl {

    private final PaintCraft plugin;
    private final Player viewer;
    private final Painting painting;

    public CanvasGui(PaintCraft plugin, Player viewer, Painting painting) {
        super(27, Component.text(Lang.get("menu.canvas.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.painting = painting;
        init();
        render();
    }

    @Override
    public void init() {
        val paint = plugin.getPaintService();
        boolean owner = painting.owner.equals(viewer.getUniqueId()) || viewer.hasPermission(PaintCraft.PERMISSION_ADMIN);
        boolean ownerCanChange = owner && !painting.signed;

        addReturnButton((gui, player, click) -> {
            new PaletteGui(plugin, player).onOpen(player);
            return true;
        });

        addInfo(4, ItemUtil.builder(Material.PAINTING)
                .displayName(StringUtil.mm("<#FF7800>" + StringUtil.escape(painting.getDisplayTitle())))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.info",
                        "author", StringUtil.escape(painting.ownerName),
                        "size", painting.blocksW + "×" + painting.blocksH,
                        "pixels", painting.width() + "×" + painting.height(),
                        "state", ItemService.state(painting),
                        "id", painting.id)))
                .glowing(painting.signed)
                .build());

        addButton(10, ItemUtil.builder(Material.SPYGLASS)
                .displayName(StringUtil.mm(Lang.get("menu.canvas.resolution.name", "res", painting.res)))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.resolution.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.canvas.resolution.finer"))
                        .rightClick(Lang.get("menu.canvas.resolution.coarser"))
                        .build())
                .build(), (gui, player, click) -> {
            if (!checkEditable(player)) return false;
            List<Integer> options = plugin.getConfigHandler().getResolutions();
            int index = options.indexOf(painting.res);
            paint.setResolution(painting, options.get(Math.floorMod(index + (click.isRightClick() ? -1 : 1), options.size())));
            refresh();
            return true;
        });

        addButton(11, ItemUtil.builder(Material.WHITE_WOOL)
                .displayName(Chat.white(Lang.get("menu.canvas.clear.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.clear.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.canvas.clear.action")).build())
                .build(), (gui, player, click) -> {
            if (!checkEditable(player)) return false;
            paint.clear(painting);
            Chat.sendActionBar(player, StringUtil.mm(Lang.get("common.canvas-cleared")));
            return true;
        });

        addButton(12, ItemUtil.builder(painting.shared ? Material.LIME_DYE : Material.GRAY_DYE)
                .displayName(StringUtil.mm(Lang.get("menu.canvas.shared.name", "state", Chat.getToggleString(painting.shared))))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.shared.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("action.toggle")).build())
                .glowing(painting.shared)
                .build(), (gui, player, click) -> {
            if (!ownerCanChange) {
                paint.sendCannotEdit(player, painting);
                return false;
            }
            painting.shared = !painting.shared;
            painting.dirty = true;
            refresh();
            return true;
        });

        addButton(13, ItemUtil.builder(Material.WRITABLE_BOOK)
                .displayName(Chat.white(Lang.get("menu.canvas.sign.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.sign.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.canvas.sign.action")).build())
                .build(), (gui, player, click) -> {
            if (!ownerCanChange) {
                paint.sendCannotEdit(player, painting);
                return false;
            }
            Dialogs.sign(plugin, player, painting);
            return true;
        });

        addButton(14, ItemUtil.builder(Material.PAPER)
                .displayName(Chat.white(Lang.get("menu.canvas.export.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.export.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.canvas.export.action")).build())
                .build(), (gui, player, click) -> {
            plugin.getExchangeService().export(player, painting);
            return true;
        });

        addButton(15, ItemUtil.builder(Material.HOPPER)
                .displayName(Chat.white(Lang.get("menu.canvas.import.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.import.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.canvas.import.action")).build())
                .build(), (gui, player, click) -> {
            Dialogs.importCode(plugin, player, null);
            return true;
        });

        addButton(16, ItemUtil.builder(Material.BOOKSHELF)
                .displayName(Chat.white(Lang.get("menu.gallery.title")))
                .lore(StringUtil.mmLore(Lang.list("menu.canvas.gallery.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("action.open")).build())
                .build(), (gui, player, click) -> {
            new GalleryGui(plugin, player, 0).onOpen(player);
            return true;
        });
    }

    private boolean checkEditable(Player player) {
        if (plugin.getPaintService().canEdit(player, painting)) return true;
        plugin.getPaintService().sendCannotEdit(player, painting);
        return false;
    }
}
