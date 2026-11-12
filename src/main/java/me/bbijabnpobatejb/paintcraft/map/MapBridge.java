package me.bbijabnpobatejb.paintcraft.map;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.diag.Counters;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.store.PaintingStorage;
import me.bbijabnpobatejb.paintcraft.world.Placement;
import org.bukkit.Bukkit;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("deprecation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class MapBridge {

    public static final int MAP = 128;

    Plugin plugin;
    PaintingStorage store;
    Set<Player> viewers = new HashSet<>();

    public void ensure(Painting painting) {
        if (painting.tiles == null) {
            painting.tiles = new TileRenderer[painting.tileCount()];
        }
        boolean allocated = false;
        for (int i = 0; i < painting.tiles.length; i++) {
            if (painting.tiles[i] != null) {
                continue;
            }
            MapView view = painting.mapIds[i] >= 0 ? Bukkit.getMap(painting.mapIds[i]) : null;
            if (view == null) {
                view = store.allocateMap();
                painting.mapIds[i] = view.getId();
                allocated = true;
            }
            attach(painting, i, view);
        }
        if (allocated) {
            store.indexMaps(painting);
            painting.dirty = true;
        }
    }

    public void onMapLoaded(MapView view) {
        Painting painting = store.byMapId(view.getId());
        if (painting == null) {
            return;
        }
        for (int i = 0; i < painting.mapIds.length; i++) {
            if (painting.mapIds[i] == view.getId()) {
                if (painting.tiles == null) {
                    painting.tiles = new TileRenderer[painting.tileCount()];
                }
                if (painting.tiles[i] == null || painting.tiles[i].view != view) {
                    attach(painting, i, view);
                }
                return;
            }
        }
    }

    private void attach(Painting painting, int index, MapView view) {
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        for (MapRenderer renderer : List.copyOf(view.getRenderers())) {
            view.removeRenderer(renderer);
        }
        TileRenderer tile = new TileRenderer(this, painting, index % painting.blocksW, index / painting.blocksW, view);
        view.addRenderer(tile);
        painting.tiles[index] = tile;
    }

    public void detach(Painting painting) {
        if (painting.tiles == null) {
            return;
        }
        for (TileRenderer tile : painting.tiles) {
            if (tile != null) {
                tile.view.removeRenderer(tile);
            }
        }
        painting.tiles = null;
    }

    void paintFull(TileRenderer tile) {
        Painting painting = tile.painting;
        byte[] pixels = store.pixels(painting);
        int res = painting.res, scale = MAP / res, width = painting.width();
        int baseX = tile.tileX * res, baseY = tile.tileY * res;
        MapCanvas canvas = tile.canvas;
        for (int my = 0; my < MAP; my++) {
            int row = (baseY + my / scale) * width + baseX;
            for (int mx = 0; mx < MAP; mx++) {
                canvas.setPixel(mx, my, pixels[row + mx / scale]);
            }
        }
    }

    public void updateAll(Painting painting) {
        update(painting, 0, 0, painting.width() - 1, painting.height() - 1);
    }

    public void update(Painting painting, int x0, int y0, int x1, int y1) {
        if (painting.tiles == null || x1 < x0 || y1 < y0) {
            return;
        }
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(painting.width() - 1, x1);
        y1 = Math.min(painting.height() - 1, y1);
        byte[] pixels = store.pixels(painting);
        int res = painting.res, scale = MAP / res, width = painting.width();

        for (int ty = y0 / res; ty <= y1 / res; ty++) {
            for (int tx = x0 / res; tx <= x1 / res; tx++) {
                int index = ty * painting.blocksW + tx;
                TileRenderer tile = painting.tiles[index];
                if (tile == null) {
                    continue;
                }
                int px0 = Math.max(x0, tx * res), px1 = Math.min(x1, tx * res + res - 1);
                int py0 = Math.max(y0, ty * res), py1 = Math.min(y1, ty * res + res - 1);
                int mx0 = (px0 - tx * res) * scale, my0 = (py0 - ty * res) * scale;
                int mw = (px1 - px0 + 1) * scale, mh = (py1 - py0 + 1) * scale;

                byte[] patch = new byte[mw * mh];
                MapCanvas canvas = tile.full ? null : tile.canvas;
                for (int j = 0; j < mh; j++) {
                    int row = (py0 + j / scale) * width + px0;
                    for (int i = 0; i < mw; i++) {
                        byte color = pixels[row + i / scale];
                        patch[j * mw + i] = color;
                        if (canvas != null) {
                            canvas.setPixel(mx0 + i, my0 + j, color);
                        }
                    }
                }
                sendNow(painting, index, tile, mx0, my0, mw, mh, patch);
            }
        }
    }

    private void sendNow(Painting painting, int index, TileRenderer tile, int x, int y, int w, int h, byte[] patch) {
        if (painting.placements.isEmpty()) {
            return;
        }
        viewers.clear();
        for (Placement placement : painting.placements) {
            ItemFrame frame = placement.frames[index];
            if (frame != null && frame.isValid()) {
                viewers.addAll(frame.getTrackedPlayers());
            }
        }
        if (viewers.isEmpty()) {
            return;
        }
        val players = PacketEvents.getAPI().getPlayerManager();
        for (Player viewer : viewers) {
            players.sendPacket(viewer, new WrapperPlayServerMapData(tile.view.getId(), (byte) 0, false, true, null, w, h, x, y, patch));
            Counters.map(patch.length);
        }
        viewers.clear();
    }
}
