package me.bbijabnpobatejb.mappaint.ui.button;

import org.bukkit.inventory.ItemStack;

public record ItemButton(int slotId, ButtonConsumer click, ItemStack itemStack) {
}
