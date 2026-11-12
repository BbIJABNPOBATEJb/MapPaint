package me.bbijabnpobatejb.paintcraft.ui.listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InventoryListener implements Listener {

    Map<InventoryGuiImpl, Integer> activeGuis = new HashMap<>();

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof InventoryGuiImpl gui)) return;

        boolean own = event.getClickedInventory() != null && event.getClickedInventory() != event.getInventory();
        if (own && gui.allowsOwnInventory() && !event.isShiftClick()
                && event.getClick() != org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) gui.onClick(player, event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof InventoryGuiImpl) event.setCancelled(true);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof InventoryGuiImpl gui) activeGuis.merge(gui, 1, Integer::sum);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof InventoryGuiImpl gui)) return;
        activeGuis.computeIfPresent(gui, (key, count) -> count <= 1 ? null : count - 1);
        if (event.getPlayer() instanceof Player player) gui.onClose(player);
    }

    @EventHandler
    public void onTick(ServerTickEndEvent event) {
        if (event.getTickNumber() % 20 != 0 || activeGuis.isEmpty()) return;
        for (InventoryGuiImpl gui : new ArrayList<>(activeGuis.keySet())) gui.onSecond();
    }
}
