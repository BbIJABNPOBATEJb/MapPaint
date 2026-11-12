package me.bbijabnpobatejb.paintcraft.ui.menu;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import me.bbijabnpobatejb.paintcraft.item.ItemService;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.ItemUtil;
import me.bbijabnpobatejb.paintcraft.util.MathUtil;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import me.bbijabnpobatejb.paintcraft.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class GalleryGui extends InventoryGuiImpl {

    private static final int PER_PAGE = 36;
    private static final int FIRST_SLOT = 9;

    final PaintCraft plugin;
    final Player viewer;
    int page;
    private @Nullable String confirmDelete;

    public GalleryGui(PaintCraft plugin, Player viewer, int page) {
        super(54, Component.text(Lang.get("menu.gallery.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        this.page = page;
        init();
        render();
    }

    @Override
    public void init() {
        addReturnButton((gui, player, click) -> {
            new PaletteGui(plugin, player).onOpen(player);
            return true;
        });

        List<Painting> paintings = plugin.getStorage().ownedBy(viewer.getUniqueId());
        int pages = Math.max(1, (paintings.size() + PER_PAGE - 1) / PER_PAGE);
        page = MathUtil.clamp(page, 0, pages - 1);
        val date = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= paintings.size()) break;
            Painting painting = paintings.get(index);
            boolean confirming = painting.id.equals(confirmDelete);
            String size = painting.blocksW + "×" + painting.blocksH;

            val item = ItemUtil.builder(confirming ? Material.BARRIER : painting.signed ? Material.PAINTING : Material.MAP)
                    .displayName(StringUtil.mm("<#FF7800>" + StringUtil.escape(painting.getDisplayTitle())))
                    .glowing(painting.signed);
            if (confirming) {
                item.lore(StringUtil.mmLore(Lang.list("menu.delete.confirm")))
                        .addLore(ActionLoreBuilder.create()
                                .shiftRightClick(Lang.get("menu.delete.yes"))
                                .leftClick(Lang.get("menu.delete.no"))
                                .build());
            } else {
                item.lore(StringUtil.mmLore(Lang.list("menu.gallery.painting",
                                "size", size,
                                "res", painting.res,
                                "state", ItemService.state(painting),
                                "date", date.format(new Date(painting.modified)),
                                "placed", painting.placements.size())))
                        .addLore(ActionLoreBuilder.create(Lang.get("menu.gallery.take", "size", size))
                                .rightClick(Lang.get("menu.admin.export"))
                                .shiftRightClick(Lang.get("menu.delete.action"))
                                .build());
            }

            addButton(FIRST_SLOT + i, item.build(), (gui, player, click) -> {
                if (click.isShiftClick() && click.isRightClick()) {
                    if (confirming) {
                        plugin.getExchangeService().delete(painting);
                        confirmDelete = null;
                        Chat.sendActionBar(player, Chat.gray(Lang.get("menu.delete.done")));
                    } else {
                        confirmDelete = painting.id;
                    }
                    refresh();
                    return true;
                }
                if (click.isRightClick()) {
                    plugin.getExchangeService().export(player, painting);
                    return true;
                }
                if (confirming) {
                    confirmDelete = null;
                    refresh();
                    return true;
                }
                plugin.getExchangeService().takeCopy(player, painting);
                return false;
            });
        }

        if (paintings.isEmpty()) {
            addInfo(getItemPos(4, 2), ItemUtil.builder(Material.MAP)
                    .displayName(Chat.white(Lang.get("menu.gallery.empty.name")))
                    .lore(StringUtil.mmLore(Lang.list("menu.gallery.empty.lore")))
                    .build());
        }
        if (page > 0) addPageButton(45, Lang.get("menu.page.previous"), -1);
        if (page < pages - 1) addPageButton(53, Lang.get("menu.page.next"), 1);
    }

    private void addPageButton(int slot, String name, int shift) {
        addButton(slot, ItemUtil.builder(Material.ARROW)
                .displayName(Chat.white(name))
                .lore(ActionLoreBuilder.create(Lang.get("menu.page.action")).build())
                .build(), (gui, player, click) -> {
            page += shift;
            refresh();
            return true;
        });
    }

    @Override
    public List<Component> getHelpText() {
        return StringUtil.mmLore(Lang.list("menu.gallery.help"));
    }
}
