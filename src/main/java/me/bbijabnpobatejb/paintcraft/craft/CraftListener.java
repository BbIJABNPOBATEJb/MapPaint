package me.bbijabnpobatejb.paintcraft.craft;

import lombok.RequiredArgsConstructor;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

@RequiredArgsConstructor
public class CraftListener implements Listener {

    private final PaintCraft plugin;

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        int ours = 0, foreign = 0;
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;
            if (plugin.getItemService().typeOf(item) != null) ours++;
            else foreign++;
        }

        if (ours == 0) {
            if (foreign > 0 && event.getRecipe() instanceof Keyed keyed && plugin.getRecipeService().isPlaceholder(keyed.getKey())) {
                inventory.setResult(null);
            }
            return;
        }
        inventory.setResult(foreign > 0 ? null : plugin.getRecipeService().match(matrix));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getRecipeService().discover(event.getPlayer());
    }
}
