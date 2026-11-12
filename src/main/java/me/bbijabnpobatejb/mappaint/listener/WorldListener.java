package me.bbijabnpobatejb.mappaint.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.MapPaint;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.server.MapInitializeEvent;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorldListener implements Listener {

    MapPaint plugin;

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
