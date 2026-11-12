package me.bbijabnpobatejb.paintcraft.compat;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.item.PaintItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LegacyItems implements ItemStyle {

    private static final int REPEAT_TICKS = 5;

    Map<UUID, Integer> lastUse = new HashMap<>();

    @Override
    public Material material(PaintItem type) {
        return switch (type) {
            case BRUSH -> Material.BRUSH;
            case CANVAS -> Material.PAPER;
            case PAINTING -> Material.PAINTING;
        };
    }

    @Override
    public ItemStack create(PaintItem type) {
        return new ItemStack(material(type));
    }

    @Override
    public void brush(ItemStack item) {
    }

    @Override
    public void maxStack(ItemStack item, int size) {
    }

    @Override
    public void used(Player player, boolean frame) {
        lastUse.put(player.getUniqueId(), Bukkit.getCurrentTick());
    }

    @Override
    public boolean isHolding(Player player) {
        Integer tick = lastUse.get(player.getUniqueId());
        return player.isHandRaised() || tick != null && Bukkit.getCurrentTick() - tick <= REPEAT_TICKS;
    }

    @Override
    public void forget(Player player) {
        lastUse.remove(player.getUniqueId());
    }
}
