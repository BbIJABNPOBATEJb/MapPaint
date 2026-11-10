package me.bbijabnpobatejb.paintcraft.craft;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.item.ItemService;
import me.bbijabnpobatejb.paintcraft.item.PaintItem;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecipeService {

    PaintCraft plugin;
    List<NamespacedKey> visible = new ArrayList<>();
    List<NamespacedKey> placeholders = new ArrayList<>();

    public void load() {
        unload();
        val config = plugin.getConfigHandler();
        val items = plugin.getItemService();

        if (config.isBrushRecipe()) {
            val recipe = new ShapelessRecipe(key("brush"), items.createBrush());
            config.getBrushIngredients().forEach(recipe::addIngredient);
            add(recipe.getKey(), recipe, visible);
        }
        if (config.isCanvasRecipe()) {
            val recipe = new ShapelessRecipe(key("canvas"), items.createCanvas(1, 1, config.getCanvasAmount()));
            config.getCanvasIngredients().forEach(recipe::addIngredient);
            add(recipe.getKey(), recipe, visible);
        }

        ItemStack stub = items.createCanvas(1, 1, 1);
        for (int columns = 1; columns <= 3; columns++) {
            for (int rows = 1; rows <= 3; rows++) {
                if (columns * rows == 1) continue;
                val recipe = new ShapedRecipe(key("grid_" + columns + "x" + rows), stub);
                String[] shape = new String[rows];
                Arrays.fill(shape, "C".repeat(columns));
                recipe.shape(shape);
                recipe.setIngredient('C', ItemService.BASE);
                add(recipe.getKey(), recipe, placeholders);
            }
        }
        val single = new ShapelessRecipe(key("single"), stub);
        single.addIngredient(ItemService.BASE);
        add(single.getKey(), single, placeholders);
        val pair = new ShapelessRecipe(key("pair"), stub);
        pair.addIngredient(ItemService.BASE);
        pair.addIngredient(ItemService.BASE);
        add(pair.getKey(), pair, placeholders);

        Bukkit.getOnlinePlayers().forEach(this::discover);
    }

    public void unload() {
        visible.forEach(Bukkit::removeRecipe);
        placeholders.forEach(Bukkit::removeRecipe);
        visible.clear();
        placeholders.clear();
    }

    void add(NamespacedKey key, Recipe recipe, List<NamespacedKey> list) {
        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
        list.add(key);
    }

    NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    public int unitCanvases(Player player) {
        ItemService items = plugin.getItemService();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (items.typeOf(hand) != PaintItem.CANVAS || items.widthOf(hand) != 1 || items.heightOf(hand) != 1) return 0;
        return hand.getAmount();
    }

    public void assemble(Player player, int width, int height) {
        int max = plugin.getConfigHandler().getMaxSize();
        int need = width * height;
        if (width < 1 || height < 1 || width > max || height > max || need < 2) return;

        int have = unitCanvases(player);
        if (have == 0) {
            Chat.sendActionBar(player, Chat.red(Lang.get("assemble.hold")));
            return;
        }
        boolean free = player.getGameMode() == GameMode.CREATIVE;
        if (!free && have < need) {
            Chat.sendActionBar(player, Chat.red(Lang.get("assemble.not-enough", "size", width + "×" + height,
                    "canvases", Lang.plural(need, "unit.canvas"), "have", have)));
            Sounds.playSound(player, Sounds.ERROR);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!free) hand.setAmount(have - need);
        plugin.getItemService().give(player, plugin.getItemService().createCanvas(width, height, 1));
        Chat.sendActionBar(player, Chat.green(Lang.get("assemble.done", "size", width + "×" + height)));
        Sounds.playSound(player, Sounds.POP);
    }

    public void discover(Player player) {
        player.discoverRecipes(visible);
    }

    public boolean isPlaceholder(NamespacedKey key) {
        return placeholders.contains(key);
    }

    public @Nullable ItemStack match(ItemStack[] matrix) {
        val items = plugin.getItemService();
        int side = matrix.length == 4 ? 2 : 3;
        int count = 0, canvases = 0;
        int minX = side, minY = side, maxX = -1, maxY = -1;
        int width = -1, height = -1;
        boolean sameSize = true;
        ItemStack paintingItem = null, canvasItem = null;

        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            PaintItem type = items.typeOf(item);
            if (type == null) continue;
            if (type == PaintItem.BRUSH) return null;

            count++;
            int x = i % side, y = i / side;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            if (type == PaintItem.CANVAS) {
                canvases++;
                canvasItem = item;
                if (width < 0) {
                    width = items.widthOf(item);
                    height = items.heightOf(item);
                } else if (width != items.widthOf(item) || height != items.heightOf(item)) {
                    sameSize = false;
                }
            } else {
                if (paintingItem != null) return null;
                paintingItem = item;
            }
        }

        if (paintingItem == null) {
            if (!sameSize) return null;
            if (count == 1) {
                int units = width * height;
                return units > 1 && units <= 64 ? items.createCanvas(1, 1, units) : null;
            }
            int columns = maxX - minX + 1, rows = maxY - minY + 1;
            if (columns * rows != count) return null;
            int max = plugin.getConfigHandler().getMaxSize();
            return width * columns <= max && height * rows <= max ? items.createCanvas(width * columns, height * rows, 1) : null;
        }

        if (count == 1) return items.createCanvas(items.widthOf(paintingItem), items.heightOf(paintingItem), 1);

        Painting painting = plugin.getStorage().get(items.paintingIdOf(paintingItem));
        if (count == 2 && canvases == 1 && painting != null && painting.signed
                && items.widthOf(canvasItem) == painting.blocksW && items.heightOf(canvasItem) == painting.blocksH) {
            return items.createPainting(painting, 2);
        }
        return null;
    }
}
