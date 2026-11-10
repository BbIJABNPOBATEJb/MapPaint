package me.bbijabnpobatejb.paintcraft.ui.button;

import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

@FunctionalInterface
public interface ButtonConsumer {

    boolean apply(InventoryGuiImpl gui, Player player, ClickType clickType);
}
