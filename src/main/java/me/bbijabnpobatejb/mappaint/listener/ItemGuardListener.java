package me.bbijabnpobatejb.mappaint.listener;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.MapPaint;
import org.bukkit.block.Container;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ItemGuardListener implements Listener {

    private static final Set<InventoryType> STATIONS = EnumSet.of(InventoryType.MERCHANT, InventoryType.CARTOGRAPHY,
            InventoryType.ANVIL, InventoryType.GRINDSTONE, InventoryType.SMITHING, InventoryType.STONECUTTER, InventoryType.LOOM);

    MapPaint plugin;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResult(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT || !STATIONS.contains(event.getInventory().getType())) return;
        if (holdsOurs(event.getInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVanillaPlace(HangingPlaceEvent event) {
        if (plugin.getItemService().typeOf(event.getItemStack()) == null) return;
        if (!plugin.getPlacementManager().isOurs(event.getEntity())) event.setCancelled(true);
    }

    public void onCrafter(Event event) {
        if (!(event instanceof BlockEvent crafter) || !(event instanceof Cancellable cancellable)) return;
        if (crafter.getBlock().getState() instanceof Container container && holdsOurs(container.getInventory())) {
            cancellable.setCancelled(true);
        }
    }

    private boolean holdsOurs(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (plugin.getItemService().typeOf(item) != null) return true;
        }
        return false;
    }
}
