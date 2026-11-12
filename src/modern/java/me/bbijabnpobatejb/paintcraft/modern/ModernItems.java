package me.bbijabnpobatejb.paintcraft.modern;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.compat.ItemStyle;
import me.bbijabnpobatejb.paintcraft.item.PaintItem;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("UnstableApiUsage")
public class ModernItems implements ItemStyle {

    private static final Material BASE = Material.RECOVERY_COMPASS;

    public ModernItems(PaintCraft plugin) {
        brush(create(PaintItem.BRUSH));
    }

    @Override
    public Material material(PaintItem type) {
        return BASE;
    }

    @Override
    public ItemStack create(PaintItem type) {
        ItemStack item = new ItemStack(BASE);
        String model = switch (type) {
            case BRUSH -> "brush";
            case CANVAS -> "map";
            case PAINTING -> "painting";
        };
        item.editMeta(meta -> meta.setItemModel(NamespacedKey.minecraft(model)));
        return item;
    }

    @Override
    public void brush(ItemStack item) {
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(100_000f)
                .animation(ItemUseAnimation.BRUSH)
                .sound(Key.key("minecraft", "intentionally_empty"))
                .hasConsumeParticles(false));
        try {
            UseEffectsPatch.apply(item);
        } catch (LinkageError ignored) {
        }
        try {
            item.setData(DataComponentTypes.TOOL, Tool.tool()
                    .defaultMiningSpeed(0.0001f)
                    .damagePerBlock(0)
                    .canDestroyBlocksInCreative(false));
        } catch (LinkageError e) {
            item.setData(DataComponentTypes.TOOL, Tool.tool()
                    .defaultMiningSpeed(0.0001f)
                    .damagePerBlock(0));
        }
    }

    @Override
    public void maxStack(ItemStack item, int size) {
        item.editMeta(meta -> meta.setMaxStackSize(size));
    }

    @Override
    public void used(Player player, boolean frame) {
        if (frame) player.startUsingItem(EquipmentSlot.HAND);
    }

    @Override
    public boolean isHolding(Player player) {
        return player.isHandRaised();
    }

    @Override
    public void forget(Player player) {
    }
}
