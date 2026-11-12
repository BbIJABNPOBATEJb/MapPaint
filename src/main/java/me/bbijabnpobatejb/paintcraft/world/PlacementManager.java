package me.bbijabnpobatejb.paintcraft.world;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.item.ItemService;
import me.bbijabnpobatejb.paintcraft.map.MapBridge;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.store.PaintingStorage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PlacementManager {

    Plugin plugin;
    PaintingStorage store;
    MapBridge bridge;
    ItemService items;
    Map<UUID, Placement> byId = new HashMap<>();

    NamespacedKey paintingKey;
    NamespacedKey placeKey;
    NamespacedKey tileKey;
    NamespacedKey rotKey;
    NamespacedKey placerKey;

    public PlacementManager(Plugin plugin, PaintingStorage store, MapBridge bridge, ItemService items) {
        this.plugin = plugin;
        this.store = store;
        this.bridge = bridge;
        this.items = items;
        paintingKey = new NamespacedKey(plugin, "painting");
        placeKey = new NamespacedKey(plugin, "place");
        tileKey = new NamespacedKey(plugin, "tile");
        rotKey = new NamespacedKey(plugin, "rot");
        placerKey = new NamespacedKey(plugin, "placer");
    }

    public Collection<Placement> all() {
        return byId.values();
    }

    public boolean isOurs(Entity entity) {
        return entity instanceof ItemFrame && entity.getPersistentDataContainer().has(paintingKey, PersistentDataType.STRING);
    }

    public Placement of(Entity entity) {
        if (!(entity instanceof ItemFrame)) {
            return null;
        }
        String place = entity.getPersistentDataContainer().get(placeKey, PersistentDataType.STRING);
        return place == null ? null : byId.get(UUID.fromString(place));
    }

    public void scanLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                register(frame);
            }
        }
    }

    public void register(ItemFrame frame) {
        PersistentDataContainer pdc = frame.getPersistentDataContainer();
        String paintingId = pdc.get(paintingKey, PersistentDataType.STRING);
        if (paintingId == null) {
            return;
        }
        try {
            UUID place = UUID.fromString(pdc.get(placeKey, PersistentDataType.STRING));
            int tile = pdc.getOrDefault(tileKey, PersistentDataType.INTEGER, 0);
            Painting painting = store.get(paintingId);
            if (painting == null || tile >= painting.tileCount() || store.isPlacementRemoved(place)) {
                Bukkit.getScheduler().runTask(plugin, frame::remove);
                return;
            }
            Placement placement = byId.get(place);
            if (placement == null) {
                int rot = pdc.getOrDefault(rotKey, PersistentDataType.INTEGER, 0);
                String placer = pdc.get(placerKey, PersistentDataType.STRING);
                BlockFace face = frame.getFacing();
                int[] a = Placement.axes(face, rot);
                int tx = tile % painting.blocksW, ty = tile / painting.blocksW;
                Location at = frame.getLocation();
                placement = new Placement(place, painting, frame.getWorld(), face, rot,
                        placer == null ? painting.owner : UUID.fromString(placer),
                        at.getBlockX() - tx * a[0] - ty * a[3],
                        at.getBlockY() - tx * a[1] - ty * a[4],
                        at.getBlockZ() - tx * a[2] - ty * a[5]);
                byId.put(place, placement);
                painting.placements.add(placement);
            }
            placement.frames[tile] = frame;
            bridge.ensure(painting);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Can't parse a painting frame at " + frame.getLocation(), e);
        }
    }

    public void unregister(Entity entity) {
        Placement placement = of(entity);
        if (placement == null) {
            return;
        }
        for (int i = 0; i < placement.frames.length; i++) {
            if (placement.frames[i] != null && placement.frames[i].getUniqueId().equals(entity.getUniqueId())) {
                placement.frames[i] = null;
            }
        }
        if (placement.isEmpty()) {
            byId.remove(placement.id);
            placement.painting.placements.remove(placement);
        }
    }

    public Placement.Hit trace(Player player, double max) {
        Location eye = player.getEyeLocation();
        Vector origin = eye.toVector(), direction = eye.getDirection();
        World world = player.getWorld();
        Placement.Hit best = null;
        for (Placement placement : byId.values()) {
            if (placement.world != world) {
                continue;
            }
            double reach = max + placement.painting.blocksW + placement.painting.blocksH;
            double dx = placement.ox - origin.getX(), dy = placement.oy - origin.getY(), dz = placement.oz - origin.getZ();
            if (dx * dx + dy * dy + dz * dz > reach * reach) {
                continue;
            }
            Placement.Hit hit = placement.trace(origin, direction, max);
            if (hit == null) {
                continue;
            }
            if (best == null) {
                best = hit;
            } else if (hit.inside()) {
                if (!best.inside() || hit.distance() < best.distance()) {
                    best = hit;
                }
            } else if (!best.inside() && hit.outside() < best.outside()) {
                best = hit;
            }
        }
        return best;
    }

    public Placement.Hit traceVisible(Player player, double max) {
        Placement.Hit hit = trace(player, max);
        if (hit == null || !hit.inside()) {
            return null;
        }
        Location eye = player.getEyeLocation();
        double clear = hit.distance() - 0.05;
        if (clear > 0 && player.getWorld().rayTraceBlocks(eye, eye.getDirection(), clear, FluidCollisionMode.NEVER, true) != null) {
            return null;
        }
        return hit;
    }

    public record Spot(BlockFace face, int rot, int ox, int oy, int oz) {
    }

    public Spot findSpot(Player player, Block clicked, BlockFace face, int bw, int bh) {
        if (!face.isCartesian()) {
            return null;
        }
        int rot = rotationFor(player, face);
        int[] a = Placement.axes(face, rot);
        Block cell = clicked.getRelative(face);
        World world = cell.getWorld();
        for (int ty = bh - 1; ty >= 0; ty--) {
            for (int step = 0; step < bw; step++) {
                int tx = (bw - 1) / 2 + ((step & 1) == 1 ? (step + 1) / 2 : -(step / 2));
                if (tx < 0 || tx >= bw) {
                    continue;
                }
                int ox = cell.getX() - tx * a[0] - ty * a[3];
                int oy = cell.getY() - tx * a[1] - ty * a[4];
                int oz = cell.getZ() - tx * a[2] - ty * a[5];
                if (fits(world, face, a, ox, oy, oz, bw, bh)) {
                    return new Spot(face, rot, ox, oy, oz);
                }
            }
        }
        return null;
    }

    public int rotationFor(Player player, BlockFace face) {
        return face.getModY() != 0 ? yawToRot(player.getLocation().getYaw()) : 0;
    }

    public Spot defaultSpot(Player player, Block clicked, BlockFace face, int bw, int bh) {
        int rot = rotationFor(player, face);
        int[] a = Placement.axes(face, rot);
        Block cell = clicked.getRelative(face);
        int tx = (bw - 1) / 2, ty = bh - 1;
        return new Spot(face, rot,
                cell.getX() - tx * a[0] - ty * a[3],
                cell.getY() - tx * a[1] - ty * a[4],
                cell.getZ() - tx * a[2] - ty * a[5]);
    }

    private boolean fits(World world, BlockFace face, int[] a, int ox, int oy, int oz, int bw, int bh) {
        for (int ty = 0; ty < bh; ty++) {
            for (int tx = 0; tx < bw; tx++) {
                int x = ox + tx * a[0] + ty * a[3], y = oy + tx * a[1] + ty * a[4], z = oz + tx * a[2] + ty * a[5];
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    return false;
                }
                Block block = world.getBlockAt(x, y, z);
                if (!block.isPassable()) {
                    return false;
                }
                BoundingBox box = new BoundingBox(x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98);
                if (!world.getNearbyEntities(box, e -> e instanceof Hanging hanging && hanging.getFacing() == face).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public Placement place(Painting painting, World world, Spot spot, UUID placer, boolean glow) {
        bridge.ensure(painting);
        Placement placement = new Placement(UUID.randomUUID(), painting, world, spot.face(), spot.rot(), placer,
                spot.ox(), spot.oy(), spot.oz());
        byId.put(placement.id, placement);
        painting.placements.add(placement);
        try {
            for (int tile = 0; tile < placement.frames.length; tile++) {
                placement.frames[tile] = spawnFrame(placement, tile, glow);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Can't place a painting", e);
            remove(placement);
            return null;
        }
        return placement;
    }

    private ItemFrame spawnFrame(Placement placement, int tile, boolean glow) {
        Painting painting = placement.painting;
        int tx = tile % painting.blocksW, ty = tile / painting.blocksW;
        Location at = new Location(placement.world,
                placement.cellX(tx, ty) + 0.5, placement.cellY(tx, ty) + 0.5, placement.cellZ(tx, ty) + 0.5);
        Class<? extends ItemFrame> type = glow ? GlowItemFrame.class : ItemFrame.class;
        return placement.world.spawn(at, type, false, frame -> {
            frame.setFacingDirection(placement.face, true);
            frame.setVisible(false);
            frame.setFixed(true);
            frame.setInvulnerable(true);
            frame.setPersistent(true);
            frame.setSilent(true);
            frame.setItemDropChance(0f);
            frame.setItem(items.createMap(painting.tiles[tile].view), false);
            frame.setRotation(frameRotation(placement.face, placement.rot));
            PersistentDataContainer pdc = frame.getPersistentDataContainer();
            pdc.set(paintingKey, PersistentDataType.STRING, painting.id);
            pdc.set(placeKey, PersistentDataType.STRING, placement.id.toString());
            pdc.set(tileKey, PersistentDataType.INTEGER, tile);
            pdc.set(rotKey, PersistentDataType.INTEGER, placement.rot);
            pdc.set(placerKey, PersistentDataType.STRING, placement.placer.toString());
        });
    }

    static Rotation frameRotation(BlockFace face, int rot) {
        if (face == BlockFace.UP) {
            return Rotation.values()[rot];
        }
        if (face == BlockFace.DOWN) {
            return Rotation.values()[(4 - rot) % 4];
        }
        return Rotation.NONE;
    }

    private static int yawToRot(float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        return new int[]{2, 3, 0, 1}[quarter];
    }

    public void remove(Placement placement) {
        boolean complete = true;
        byId.remove(placement.id);
        placement.painting.placements.remove(placement);
        for (int i = 0; i < placement.frames.length; i++) {
            ItemFrame frame = placement.frames[i];
            if (frame != null && frame.isValid()) {
                frame.remove();
            } else {
                complete = false;
            }
            placement.frames[i] = null;
        }
        if (!complete) {
            store.markPlacementRemoved(placement.id);
        }
    }

    public void removeAll(Painting painting) {
        for (Placement placement : painting.placements.toArray(Placement[]::new)) {
            remove(placement);
        }
    }

    public boolean isGlowing(Placement placement) {
        for (ItemFrame frame : placement.frames) {
            if (frame != null) {
                return frame instanceof GlowItemFrame;
            }
        }
        return false;
    }

    public boolean setGlowing(Placement placement, boolean glow) {
        if (!placement.isComplete()) {
            return false;
        }
        for (int i = 0; i < placement.frames.length; i++) {
            ItemFrame old = placement.frames[i];
            placement.frames[i] = spawnFrame(placement, i, glow);
            old.remove();
        }
        return true;
    }
}
