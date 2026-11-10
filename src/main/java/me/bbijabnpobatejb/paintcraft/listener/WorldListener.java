package me.bbijabnpobatejb.paintcraft.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import lombok.RequiredArgsConstructor;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.server.MapInitializeEvent;

@RequiredArgsConstructor
public class WorldListener implements Listener {

    private final PaintCraft plugin;

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame frame) plugin.getPlacementManager().register(frame);
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof ItemFrame) plugin.getPlacementManager().unregister(event.getEntity());
    }

    @EventHandler
    public void onMap(MapInitializeEvent event) {
        plugin.getMapBridge().onMapLoaded(event.getMap());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.getPlacementManager().isOurs(event.getEntity())) event.setCancelled(true);
    }
}
