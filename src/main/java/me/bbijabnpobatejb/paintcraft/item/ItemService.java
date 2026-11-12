package me.bbijabnpobatejb.paintcraft.item;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.compat.ItemStyle;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
import me.bbijabnpobatejb.paintcraft.util.ItemUtil;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import me.bbijabnpobatejb.paintcraft.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemService {

    NamespacedKey kindKey;
    NamespacedKey widthKey;
    NamespacedKey heightKey;
    NamespacedKey idKey;
    ItemStyle style;

    public ItemService(PaintCraft plugin) {
        style = plugin.getItemStyle();
        kindKey = new NamespacedKey(plugin, "kind");
        widthKey = new NamespacedKey(plugin, "w");
        heightKey = new NamespacedKey(plugin, "h");
        idKey = new NamespacedKey(plugin, "id");
    }

    public ItemStack createBrush() {
        ItemStack item = ItemUtil.builder(style.create(PaintItem.BRUSH))
                .displayName(Lang.get("item.brush.name"))
                .lore(ActionLoreBuilder.create()
                        .rightClick(Lang.get("item.brush.paint"))
                        .leftClick(Lang.get("item.brush.pick"))
                        .shiftRightClick(Lang.get("item.brush.palette"))
                        .custom(Lang.get("item.brush.shift-key"), Lang.get("item.brush.shift"))
                        .custom(Lang.get("item.brush.wheel-key"), Lang.get("item.brush.wheel"))
                        .swapHands(Lang.get("item.brush.undo"))
                        .shiftSwapHands(Lang.get("item.brush.redo"))
                        .buildOnlyLines())
                .string(kindKey, PaintItem.BRUSH.name())
                .build();
        style.maxStack(item, 1);
        style.brush(item);
        return item;
    }

    public ItemStack createCanvas(int width, int height, int amount) {
        val builder = ItemUtil.builder(style.create(PaintItem.CANVAS))
                .displayName("<white>" + Lang.get("item.canvas.name", "size", width + "×" + height))
                .lore(StringUtil.mmLore(Lang.list("item.canvas.lore")));
        if (width == 1 && height == 1) {
            builder.addLore(ActionLoreBuilder.create()
                    .shiftRightClick(Lang.get("item.canvas.assemble"))
                    .buildOnlyLines());
        }
        ItemStack item = builder
                .amount(amount)
                .string(kindKey, PaintItem.CANVAS.name())
                .integer(widthKey, width)
                .integer(heightKey, height)
                .build();
        style.maxStack(item, 64);
        return item;
    }

    public ItemStack createPainting(Painting painting, int amount) {
        val builder = ItemUtil.builder(style.create(PaintItem.PAINTING))
                .string(kindKey, PaintItem.PAINTING.name())
                .string(idKey, painting.id)
                .integer(widthKey, painting.blocksW)
                .integer(heightKey, painting.blocksH);
        String size = painting.blocksW + "×" + painting.blocksH;
        List<String> lore = new ArrayList<>();
        lore.add(Lang.get("item.painting.author", "name", StringUtil.escape(painting.ownerName)));
        lore.add(Lang.get("item.painting.size", "size", size));
        if (painting.imported) lore.add(Lang.get("item.painting.imported"));
        lore.add("");
        if (painting.signed) {
            lore.addAll(Lang.list("item.painting.signed", "size", size));
            builder.displayName("<#FF7800>" + StringUtil.escape(painting.getDisplayTitle()))
                    .glowing(true);
        } else {
            lore.addAll(Lang.list("item.painting.sketch", "id", painting.id));
            builder.displayName("<white>" + Lang.get("item.painting.sketch-name"));
        }
        ItemStack item = builder.lore(StringUtil.mmLore(lore)).amount(amount).build();
        style.maxStack(item, painting.signed ? 64 : 1);
        return item;
    }

    public static String state(Painting painting) {
        return Lang.get(painting.signed ? "painting.signed" : "painting.sketch")
                + (painting.imported ? Lang.get("painting.imported") : "");
    }

    public ItemStack createMap(MapView view) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createSwatch(byte color, boolean selected, List<Component> lore) {
        if (Palette.isTransparent(color)) {
            return ItemUtil.builder(Material.GLASS_BOTTLE)
                    .displayName("<#1FFFE3>" + Lang.get("color.transparent"))
                    .lore(lore)
                    .glowing(selected)
                    .build();
        }
        return ItemUtil.builder(Material.LEATHER_CHESTPLATE)
                .leatherColor(Palette.rgb(color))
                .displayName(Palette.chip(color))
                .lore(lore)
                .glowing(selected)
                .build();
    }

    public void give(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    public @Nullable PaintItem typeOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String kind = ItemUtil.getString(item, kindKey);
        if (kind == null) return null;
        try {
            return PaintItem.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isBrush(@Nullable ItemStack item) {
        return typeOf(item) == PaintItem.BRUSH;
    }

    public int widthOf(ItemStack item) {
        return ItemUtil.getInt(item, widthKey, 1);
    }

    public int heightOf(ItemStack item) {
        return ItemUtil.getInt(item, heightKey, 1);
    }

    public @Nullable String paintingIdOf(ItemStack item) {
        return ItemUtil.getString(item, idKey);
    }
}
