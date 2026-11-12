package me.bbijabnpobatejb.mappaint.ui.menu;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.item.ItemService;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.ui.inventory.InventoryGuiImpl;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.ItemUtil;
import me.bbijabnpobatejb.mappaint.util.MathUtil;
import me.bbijabnpobatejb.mappaint.util.Sounds;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import me.bbijabnpobatejb.mappaint.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminGui extends InventoryGuiImpl {

    private static final int PER_PAGE = 36;
    private static final int FIRST_SLOT = 9;
    private static final int GRID = 5;
    private static final int STACK = 64;

    final MapPaint plugin;
    int page;
    boolean canvases;
    private @Nullable String confirmDelete;

    public AdminGui(MapPaint plugin, int page) {
        super(54, Component.text(Lang.get("menu.admin.title")));
        this.plugin = plugin;
        this.page = page;
        init();
        render();
    }

    @Override
    public void init() {
        val items = plugin.getItemService();
        if (canvases) {
            initCanvases();
            return;
        }
        addCloseButton();

        addGive(2, items.createBrush(), 1);
        addGive(3, items.createCanvas(1, 1, 1), STACK);
        addButton(4, ItemUtil.builder(Material.ITEM_FRAME)
                .displayName(StringUtil.mm(Lang.get("menu.admin.canvases.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.admin.canvases.lore", "max", GRID + "×" + GRID)))
                .addLore(ActionLoreBuilder.create(Lang.get("action.open")).build())
                .build(), (gui, player, click) -> {
            canvases = true;
            refresh();
            return true;
        });

        List<Painting> paintings = new ArrayList<>(plugin.getStorage().all());
        paintings.sort(Comparator.comparingLong((Painting p) -> p.modified).reversed());
        int pages = Math.max(1, (paintings.size() + PER_PAGE - 1) / PER_PAGE);
        page = MathUtil.clamp(page, 0, pages - 1);
        val date = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= paintings.size()) break;
            Painting painting = paintings.get(index);
            boolean confirming = painting.id.equals(confirmDelete);

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
                item.lore(StringUtil.mmLore(Lang.list("menu.admin.painting",
                                "author", StringUtil.escape(painting.ownerName),
                                "size", painting.blocksW + "×" + painting.blocksH,
                                "res", painting.res,
                                "state", ItemService.state(painting),
                                "date", date.format(new Date(painting.modified)),
                                "placed", painting.placements.size(),
                                "id", painting.id)))
                        .addLore(ActionLoreBuilder.create(Lang.get("menu.admin.take"))
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
                    .displayName(Chat.white(Lang.get("menu.admin.empty")))
                    .build());
        }
        if (page > 0) addPageButton(45, Lang.get("menu.page.previous"), -1);
        addInfo(49, ItemUtil.builder(Material.BOOK)
                .displayName(StringUtil.mm(Lang.get("menu.admin.count", "count", paintings.size())))
                .lore(StringUtil.mmLore(Lang.get("menu.page.of", "page", page + 1, "pages", pages)))
                .build());
        if (page < pages - 1) addPageButton(53, Lang.get("menu.page.next"), 1);
    }

    private void initCanvases() {
        addReturnButton((gui, player, click) -> {
            canvases = false;
            refresh();
            return true;
        });
        int max = plugin.getConfigHandler().getMaxSize();
        for (int height = 1; height <= GRID; height++) {
            for (int width = 1; width <= GRID; width++) {
                int slot = getItemPos(1 + width, height);
                if (width > max || height > max) {
                    addInfo(slot, ItemUtil.builder(Material.GRAY_STAINED_GLASS_PANE)
                            .displayName(Chat.gray(Lang.get("item.canvas.name", "size", width + "×" + height)))
                            .lore(StringUtil.mmLore(Lang.list("menu.admin.too-big", "max", max)))
                            .build());
                    continue;
                }
                addGive(slot, plugin.getItemService().createCanvas(width, height, 1), STACK);
            }
        }
    }

    private void addGive(int slot, ItemStack item, int stack) {
        addButton(slot, ItemUtil.builder(item)
                .addLore(ActionLoreBuilder.create(Lang.get("action.take"))
                        .shiftLeftClick(stack > 1 ? Lang.get("menu.admin.take-stack", "amount", stack) : null)
                        .build())
                .build(), (gui, player, click) -> {
            ItemStack given = item.clone();
            if (click.isShiftClick() && stack > 1) given.setAmount(stack);
            plugin.getItemService().give(player, given);
            Sounds.playSound(player, Sounds.POP);
            return false;
        });
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
        if (canvases) return StringUtil.mmLore(Lang.list("menu.admin.help-canvases", "amount", STACK));
        return StringUtil.mmLore(Lang.list("menu.admin.help", "max", GRID + "×" + GRID));
    }
}
