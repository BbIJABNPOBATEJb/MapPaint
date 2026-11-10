package me.bbijabnpobatejb.paintcraft.ui.menu;

import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.paint.Tool;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
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

public class PaletteGui extends InventoryGuiImpl {

    private static final int PER_PAGE = 36;
    private static final int FIRST_COLOR_SLOT = 9;

    private final PaintCraft plugin;
    private final Player viewer;

    public PaletteGui(PaintCraft plugin, Player viewer) {
        super(54, Component.text(Lang.get("menu.palette.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        init();
        render();
    }

    @Override
    public void init() {
        val paint = plugin.getPaintService();
        val session = paint.session(viewer);
        val items = plugin.getItemService();
        addCloseButton();

        List<Integer> bases = Palette.bases();
        int pages = (bases.size() + PER_PAGE - 1) / PER_PAGE;
        int page = Math.floorMod(session.getPalettePage(), pages);
        session.setPalettePage(page);

        byte current = session.getColor();
        boolean transparent = Palette.isTransparent(current);
        int currentBase = transparent ? Palette.baseOf(Palette.WHITE) : Palette.baseOf(current);

        addButton(1, ItemUtil.builder(Material.ARROW)
                .displayName(Chat.white(Lang.get("menu.palette.page", "page", page + 1, "pages", pages)))
                .lore(ActionLoreBuilder.create(Lang.get("menu.page.action")).build())
                .build(), (gui, player, click) -> {
            session.setPalettePage(page + 1);
            refresh();
            return true;
        });
        addButton(2, ItemUtil.builder(Material.NAME_TAG)
                .displayName(Chat.white(Lang.get("menu.palette.hex.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.palette.hex.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.palette.hex.action")).build())
                .build(), (gui, player, click) -> {
            Dialogs.hex(plugin, player);
            return true;
        });
        addColor(3, Palette.TRANSPARENT, transparent, StringUtil.mmLore(Lang.list("menu.palette.transparent")));
        for (int level = 0; level < 4; level++) {
            byte color = Palette.shade(currentBase, level);
            addColor(4 + level, color, color == current, StringUtil.mmLore(Lang.list("menu.palette.shade")));
        }

        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= bases.size()) break;
            int base = bases.get(index);
            addColor(FIRST_COLOR_SLOT + i, Palette.shade(base, 3), !transparent && base == currentBase, List.of());
        }

        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            Tool tool = tools[i];
            addButton(45 + i, ItemUtil.builder(tool.getIcon())
                    .displayName(Chat.white(tool.getDisplayName()))
                    .lore(StringUtil.mmLore(tool.getHint()))
                    .addLore(ActionLoreBuilder.create(Lang.get("action.select")).build())
                    .glowing(tool == session.getTool())
                    .build(), (gui, player, click) -> {
                paint.setTool(player, tool);
                refresh();
                return true;
            });
        }

        int size = session.getSize();
        addButton(52, ItemUtil.builder(Material.CLAY_BALL)
                .displayName(StringUtil.mm(Lang.get("menu.palette.size.name", "size", size)))
                .lore(StringUtil.mmLore(Lang.list("menu.palette.size.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("menu.palette.size.more"))
                        .rightClick(Lang.get("menu.palette.size.less"))
                        .custom(Lang.get("menu.palette.size.shift"), Lang.get("menu.palette.size.by-four"))
                        .build())
                .amount(Math.min(64, size))
                .build(), (gui, player, click) -> {
            int step = click.isShiftClick() ? 4 : 1;
            paint.setSize(player, size + (click.isRightClick() ? -step : step));
            refresh();
            return true;
        });

        addButton(53, ItemUtil.builder(Material.PAINTING)
                .displayName(Chat.white(Lang.get("menu.palette.canvas.name")))
                .lore(StringUtil.mmLore(Lang.list("menu.palette.canvas.lore")))
                .addLore(ActionLoreBuilder.create(Lang.get("action.open")).build())
                .build(), (gui, player, click) -> {
            Painting painting = paint.target(player);
            if (painting != null) new CanvasGui(plugin, player, painting).onOpen(player);
            else new GalleryGui(plugin, player, 0).onOpen(player);
            return true;
        });
    }

    private void addColor(int slot, byte color, boolean selected, List<Component> description) {
        addButton(slot, ItemUtil.builder(plugin.getItemService().createSwatch(color, selected, description))
                .addLore(ActionLoreBuilder.create(Lang.get("action.select")).build())
                .build(), (gui, player, click) -> {
            plugin.getPaintService().setColor(player, color);
            refresh();
            return true;
        });
    }

    @Override
    public List<Component> getHelpText() {
        return StringUtil.mmLore(Lang.list("menu.palette.help"));
    }
}
