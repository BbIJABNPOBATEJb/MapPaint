package me.bbijabnpobatejb.mappaint.modern;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.compat.Icons;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SpriteIcons implements Icons {

    private static final Set<Material> BLOCKS = EnumSet.of(Material.SPONGE, Material.WHITE_WOOL, Material.BOOKSHELF);

    Map<Material, Component> sprites = new EnumMap<>(Material.class);

    public SpriteIcons(MapPaint plugin) {
        sprite(Material.BRUSH);
    }

    @Override
    public Component sprite(Material material) {
        return sprites.computeIfAbsent(material, m -> {
            String name = m.getKey().getKey().toLowerCase(Locale.ROOT);
            boolean block = BLOCKS.contains(m);
            return Component.object(ObjectContents.sprite(Key.key("minecraft", block ? "blocks" : "items"),
                    Key.key("minecraft", (block ? "block/" : "item/") + name)));
        });
    }
}
