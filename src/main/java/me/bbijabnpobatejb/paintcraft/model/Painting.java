package me.bbijabnpobatejb.paintcraft.model;

import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.map.TileRenderer;
import me.bbijabnpobatejb.paintcraft.paint.UndoStack;
import me.bbijabnpobatejb.paintcraft.world.Placement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Painting {

    public final String id;
    public UUID owner;
    public String ownerName;
    public String title = "";
    public boolean signed;
    public boolean shared;
    public boolean touched;
    public boolean imported;
    public int blocksW;
    public int blocksH;
    public int res;
    public long created;
    public long modified;
    public int[] mapIds;

    public byte[] pixels;

    public boolean dirty;
    public long lastAccess;
    public TileRenderer[] tiles;
    public final List<Placement> placements = new ArrayList<>(1);
    public UndoStack undo;
    public int changes;
    public byte[] resSource;
    public int resSourceRes;
    public int resStamp;
    public long resTime;

    public Painting(String id, UUID owner, String ownerName, int blocksW, int blocksH, int res) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.blocksW = blocksW;
        this.blocksH = blocksH;
        this.res = res;
        this.mapIds = new int[blocksW * blocksH];
        java.util.Arrays.fill(this.mapIds, -1);
        this.created = System.currentTimeMillis();
        this.modified = this.created;
    }

    public int width() {
        return blocksW * res;
    }

    public int height() {
        return blocksH * res;
    }

    public int tileCount() {
        return blocksW * blocksH;
    }

    public String getDisplayTitle() {
        return title.isEmpty() ? Lang.get("painting.untitled") : title;
    }

    public void markChanged() {
        changes++;
        dirty = true;
        touched = true;
        modified = System.currentTimeMillis();
    }
}
