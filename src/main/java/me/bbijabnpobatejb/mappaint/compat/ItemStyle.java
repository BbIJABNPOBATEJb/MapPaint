package me.bbijabnpobatejb.mappaint.compat;

import me.bbijabnpobatejb.mappaint.item.PaintItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ItemStyle {

    Material material(PaintItem type);

    ItemStack create(PaintItem type);

    void brush(ItemStack item);

    void maxStack(ItemStack item, int size);

    void used(Player player, boolean frame);

    boolean isHolding(Player player);

    void forget(Player player);
}
