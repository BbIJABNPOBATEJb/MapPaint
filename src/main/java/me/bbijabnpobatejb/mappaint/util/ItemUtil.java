package me.bbijabnpobatejb.mappaint.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ItemUtil {

    private final Enchantment GLINT = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));

    public ItemBuilder builder(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public ItemBuilder builder(ItemStack itemStack) {
        return new ItemBuilder(itemStack.clone());
    }

    public @Nullable String getString(@Nullable ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public int getInt(@Nullable ItemStack item, NamespacedKey key, int fallback) {
        if (item == null || !item.hasItemMeta()) return fallback;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, fallback);
    }

    public record ItemBuilder(ItemStack itemStack) {

        public ItemBuilder displayName(Component name) {
            return editMeta(meta -> meta.displayName(StringUtil.noItalic(name)));
        }

        public ItemBuilder displayName(String miniMessage) {
            return displayName(StringUtil.mm(miniMessage));
        }

        public ItemBuilder lore(List<? extends Component> lore) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (Component line : lore) lines.add(StringUtil.noItalic(line));
            return editMeta(meta -> meta.lore(lines));
        }

        public ItemBuilder addLore(List<? extends Component> lore) {
            return editMeta(meta -> {
                List<Component> lines = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                for (Component line : lore) lines.add(StringUtil.noItalic(line));
                meta.lore(lines);
            });
        }

        public ItemBuilder addLore(String... miniMessageLines) {
            return addLore(StringUtil.mmLore(miniMessageLines));
        }

        public ItemBuilder glowing(boolean glowing) {
            if (!glowing) return this;
            return editMeta(meta -> meta.addEnchant(GLINT, 1, true));
        }

        public ItemBuilder amount(int amount) {
            itemStack.setAmount(MathUtil.clamp(amount, 1, 99));
            return this;
        }

        public ItemBuilder string(NamespacedKey key, String value) {
            return editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value));
        }

        public ItemBuilder integer(NamespacedKey key, int value) {
            return editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value));
        }

        public ItemBuilder leatherColor(int rgb) {
            return editMeta(meta -> {
                if (meta instanceof LeatherArmorMeta leather) leather.setColor(Color.fromRGB(rgb));
            });
        }

        public ItemBuilder editMeta(java.util.function.Consumer<ItemMeta> edit) {
            ItemMeta meta = itemStack.getItemMeta();
            edit.accept(meta);
            itemStack.setItemMeta(meta);
            return this;
        }

        public ItemStack build() {
            editMeta(meta -> meta.addItemFlags(ItemFlag.values()));
            return itemStack;
        }
    }
}
