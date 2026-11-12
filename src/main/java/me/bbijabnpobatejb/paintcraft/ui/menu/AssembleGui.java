package me.bbijabnpobatejb.paintcraft.ui.menu;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.ItemUtil;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import me.bbijabnpobatejb.paintcraft.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AssembleGui extends InventoryGuiImpl {

    private static final int COLUMNS = 7;
    private static final int ROWS = 5;

    PaintCraft plugin;
    Player viewer;

    public AssembleGui(PaintCraft plugin, Player viewer) {
        super(54, Component.text(Lang.get("dialog.canvas.title")));
        this.plugin = plugin;
        this.viewer = viewer;
        init();
        render();
    }

    @Override
    public void init() {
        addCloseButton();
        int have = plugin.getRecipeService().unitCanvases(viewer);
        boolean free = viewer.getGameMode() == GameMode.CREATIVE;
        int max = plugin.getConfigHandler().getMaxSize();

        for (int height = 1; height <= Math.min(max, ROWS); height++) {
            for (int width = 1; width <= Math.min(max, COLUMNS); width++) {
                int w = width, h = height, need = width * height;
                if (need == 1) continue;
                String size = width + "×" + height;
                String canvases = Lang.plural(need, "unit.canvas");
                boolean enough = free || have >= need;

                addButton(getItemPos(width, height), ItemUtil.builder(enough ? Material.PAPER : Material.GRAY_DYE)
                        .displayName(enough ? Chat.white(Lang.get("item.canvas.name", "size", size))
                                : Chat.gray(Lang.get("item.canvas.name", "size", size)))
                        .lore(StringUtil.mmLore(enough ? Lang.get("dialog.canvas.assemble", "size", size, "canvases", canvases)
                                : Lang.get("dialog.canvas.need", "canvases", canvases, "have", have)))
                        .addLore(enough ? ActionLoreBuilder.create(Lang.get("action.take")).build() : List.of())
                        .amount(Math.min(64, need))
                        .build(), (gui, player, click) -> {
                    plugin.getRecipeService().assemble(player, w, h);
                    player.closeInventory();
                    return true;
                });
            }
        }
    }

    @Override
    public List<Component> getHelpText() {
        boolean free = viewer.getGameMode() == GameMode.CREATIVE;
        int have = plugin.getRecipeService().unitCanvases(viewer);
        List<String> lines = new ArrayList<>();
        if (!free) lines.add("<white>" + Lang.get("dialog.canvas.have", "canvases", Lang.plural(have, "unit.canvas")));
        lines.addAll(Lang.list("menu.assemble.help"));
        return StringUtil.mmLore(lines);
    }
}
