package me.bbijabnpobatejb.paintcraft.map;

import me.bbijabnpobatejb.paintcraft.model.Painting;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class TileRenderer extends MapRenderer {

    private final MapBridge bridge;
    public final Painting painting;
    public final int tileX;
    public final int tileY;
    public final MapView view;
    MapCanvas canvas;
    boolean full = true;

    TileRenderer(MapBridge bridge, Painting painting, int tileX, int tileY, MapView view) {
        super(false);
        this.bridge = bridge;
        this.painting = painting;
        this.tileX = tileX;
        this.tileY = tileY;
        this.view = view;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (this.canvas != canvas) {
            this.canvas = canvas;
            full = true;
        }
        if (full) {
            full = false;
            bridge.paintFull(this);
        }
    }
}
