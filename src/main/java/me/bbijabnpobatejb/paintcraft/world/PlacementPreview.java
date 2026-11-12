package me.bbijabnpobatejb.paintcraft.world;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.item.PaintItem;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.packet.FakeDisplay;
import me.bbijabnpobatejb.paintcraft.packet.PacketScene;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlacementPreview {

    private static final int FITS = 0x1FFF5E;
    private static final int BLOCKED = 0xFF1F1F;
    private static final int RECHECK_TICKS = 10;

    private static final class Shown {
        final PacketScene scene = new PacketScene();
        Object key;
        int checkedTick;
    }

    PaintCraft plugin;
    Map<UUID, Shown> shown = new HashMap<>();
    BlockData fits = Material.LIME_STAINED_GLASS.createBlockData();
    BlockData blocked = Material.RED_STAINED_GLASS.createBlockData();

    public void tick() {
        int tick = Bukkit.getCurrentTick();
        if ((tick & 1) != 0) return;
        if (!plugin.getConfigHandler().isPlacementPreview()) {
            if (!shown.isEmpty()) shutdown();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            PaintItem type = plugin.getItemService().typeOf(hand);
            if (type != PaintItem.CANVAS && type != PaintItem.PAINTING) {
                hide(player);
                continue;
            }
            update(player, hand, type, tick);
        }
        for (Iterator<Map.Entry<UUID, Shown>> it = shown.entrySet().iterator(); it.hasNext(); ) {
            if (Bukkit.getPlayer(it.next().getKey()) == null) it.remove();
        }
    }

    private void update(Player player, ItemStack hand, PaintItem type, int tick) {
        val items = plugin.getItemService();
        int blocksW = items.widthOf(hand), blocksH = items.heightOf(hand);
        if (type == PaintItem.PAINTING) {
            Painting painting = plugin.getStorage().get(items.paintingIdOf(hand));
            if (painting != null) {
                blocksW = painting.blocksW;
                blocksH = painting.blocksH;
            }
        }

        val trace = player.rayTraceBlocks(5.0, FluidCollisionMode.NEVER);
        Block block = trace == null ? null : trace.getHitBlock();
        BlockFace face = trace == null ? null : trace.getHitBlockFace();
        if (block == null || face == null || !player.hasPermission(PaintCraft.PERMISSION_USE)) {
            hide(player);
            return;
        }

        val placements = plugin.getPlacementManager();
        int rot = placements.rotationFor(player, face);
        Object key = List.of(block.getX(), block.getY(), block.getZ(), face, rot, blocksW, blocksH);
        Shown current = shown.get(player.getUniqueId());
        if (current != null && key.equals(current.key) && tick - current.checkedTick < RECHECK_TICKS) return;

        PlacementManager.Spot spot = placements.findSpot(player, block, face, blocksW, blocksH);
        boolean ok = spot != null;
        if (spot == null) spot = placements.defaultSpot(player, block, face, blocksW, blocksH);

        if (current == null) {
            current = new Shown();
            shown.put(player.getUniqueId(), current);
            current.scene.setViewers(List.of(player));
        }
        current.key = key;
        current.checkedTick = tick;

        Plane plane = Plane.of(player.getWorld(), spot.face(), spot.rot(), spot.ox(), spot.oy(), spot.oz());
        current.scene.beginFrame();
        FakeDisplay display = plane.box(current.scene.block("outline"), 0, 0, blocksW, blocksH, 0, 0.03, ok ? fits : blocked);
        display.glow(true, ok ? FITS : BLOCKED).light(FakeDisplay.FULL_BRIGHT).interpolation(2);
        current.scene.endFrame();
    }

    private void hide(Player player) {
        Shown current = shown.remove(player.getUniqueId());
        if (current != null) current.scene.destroy();
    }

    public void forget(Player player) {
        Shown current = shown.remove(player.getUniqueId());
        if (current != null) current.scene.forgetViewer(player.getUniqueId());
    }

    public void shutdown() {
        for (Shown current : shown.values()) current.scene.destroy();
        shown.clear();
    }
}
