package me.bbijabnpobatejb.paintcraft.modern;

import io.papermc.paper.event.player.PlayerPickEntityEvent;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.world.Placement;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PickListener implements Listener {

    PaintCraft plugin;

    public PickListener(PaintCraft plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPick(PlayerPickEntityEvent event) {
        val placements = plugin.getPlacementManager();
        if (!placements.isOurs(event.getEntity())) return;
        event.setCancelled(true);

        Placement placement = placements.of(event.getEntity());
        Player player = event.getPlayer();
        if (placement == null || player.getGameMode() != GameMode.CREATIVE) return;
        plugin.getItemService().give(player, plugin.getItemService().createPainting(placement.painting, 1));
    }
}
