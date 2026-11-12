package me.bbijabnpobatejb.mappaint.compat;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import lombok.experimental.UtilityClass;
import me.bbijabnpobatejb.mappaint.MapPaint;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

@UtilityClass
public class Compat {

    private final String MODERN = "me.bbijabnpobatejb.mappaint.modern.";
    private final Method MAP_COLOR = method(BlockData.class, "getMapColor");
    private final Method HIDE_TOOLTIP = method(ItemMeta.class, "setHideTooltip", boolean.class);

    public boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public boolean isOlderThan(ServerVersion version) {
        return PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(version);
    }

    public Prompts prompts(MapPaint plugin) {
        Prompts dialogs = hasClass("io.papermc.paper.dialog.Dialog") ? modern("DialogPrompts", plugin) : null;
        return dialogs != null ? dialogs : new ChatPrompts(plugin);
    }

    public ItemStyle items(MapPaint plugin) {
        ItemStyle modern = hasClass("io.papermc.paper.datacomponent.item.Consumable") ? modern("ModernItems", plugin) : null;
        return modern != null ? modern : new LegacyItems();
    }

    public @Nullable Icons icons(MapPaint plugin) {
        return isOlderThan(ServerVersion.V_1_21_9) ? null : modern("SpriteIcons", plugin);
    }

    public void listeners(MapPaint plugin) {
        if (hasClass("io.papermc.paper.event.player.PlayerPickEntityEvent")) modern("PickListener", plugin);
    }

    public void optionalEvent(MapPaint plugin, String name, Consumer<Event> handler) {
        try {
            Class<? extends Event> type = Class.forName(name).asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(type, new Listener() {
            }, EventPriority.HIGH, (listener, event) -> handler.accept(event), plugin, true);
        } catch (ClassNotFoundException | LinkageError ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T modern(String name, MapPaint plugin) {
        try {
            return (T) Class.forName(MODERN + name).getConstructor(MapPaint.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().warning("Can't use " + name + " on this server version: " + e);
            return null;
        }
    }

    public @Nullable Color mapColor(Block block) {
        if (MAP_COLOR == null) return null;
        try {
            return (Color) MAP_COLOR.invoke(block.getBlockData());
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public void hideTooltip(ItemMeta meta) {
        if (HIDE_TOOLTIP == null) return;
        try {
            HIDE_TOOLTIP.invoke(meta, true);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private @Nullable Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public HangingBreakByEntityEvent hangingBreak(Hanging hanging, Player player) {
        try {
            for (Constructor<?> constructor : HangingBreakByEntityEvent.class.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 3 && types[1] == Entity.class && types[2] == HangingBreakEvent.RemoveCause.class) {
                    return (HangingBreakByEntityEvent) constructor.newInstance(hanging, player, HangingBreakEvent.RemoveCause.ENTITY);
                }
            }
            Class<?> sourceType = Class.forName("org.bukkit.damage.DamageSource");
            Class<?> damageType = Class.forName("org.bukkit.damage.DamageType");
            Class<?> builderType = Class.forName("org.bukkit.damage.DamageSource$Builder");
            Object builder = sourceType.getMethod("builder", damageType).invoke(null, damageType.getField("PLAYER_ATTACK").get(null));
            builderType.getMethod("withCausingEntity", Entity.class).invoke(builder, player);
            builderType.getMethod("withDirectEntity", Entity.class).invoke(builder, player);
            Object source = builderType.getMethod("build").invoke(builder);
            return HangingBreakByEntityEvent.class
                    .getConstructor(Hanging.class, Entity.class, sourceType, HangingBreakEvent.RemoveCause.class)
                    .newInstance(hanging, player, source, HangingBreakEvent.RemoveCause.ENTITY);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't create the hanging break event", e);
        }
    }
}
