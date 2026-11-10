package me.bbijabnpobatejb.paintcraft.paint;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.map.MapBridge;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
import me.bbijabnpobatejb.paintcraft.store.PaintingStorage;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import me.bbijabnpobatejb.paintcraft.world.Placement;
import me.bbijabnpobatejb.paintcraft.world.PlacementManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaintService {

    static final long RES_SOURCE_MILLIS = 30 * 60_000L;

    static final int GRACE_TICKS = 3;

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class Stroke {
        Player player;
        Placement placement;
        Painting painting;
        byte[] pixels;
        Tool tool;
        byte color;
        int size;
        boolean square;
        int startX, startY, lastX, lastY;
        int age;
        int axis;
        final UndoStack.Step step = new UndoStack.Step();
    }

    PaintCraft plugin;
    PaintingStorage storage;
    MapBridge bridge;
    PlacementManager placements;
    Map<UUID, PlayerSession> sessions = new HashMap<>();
    List<Stroke> strokes = new ArrayList<>();
    NamespacedKey colorKey, sizeKey, toolKey, squareKey, layoutKey;

    public PaintService(PaintCraft plugin, PaintingStorage storage, MapBridge bridge, PlacementManager placements) {
        this.plugin = plugin;
        this.storage = storage;
        this.bridge = bridge;
        this.placements = placements;
        colorKey = new NamespacedKey(plugin, "color");
        sizeKey = new NamespacedKey(plugin, "size");
        toolKey = new NamespacedKey(plugin, "tool");
        squareKey = new NamespacedKey(plugin, "square");
        layoutKey = new NamespacedKey(plugin, "layout");
    }

    public PlayerSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> {
            val session = new PlayerSession();
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            Byte color = pdc.get(colorKey, PersistentDataType.BYTE);
            if (color != null && (Palette.isValid(color) || Palette.isTransparent(color))) session.setColor(color);
            session.setHue(Palette.hueOf(session.getColor(), 0f));
            session.setSquareBrush(pdc.getOrDefault(squareKey, PersistentDataType.BOOLEAN, false));
            session.setSize(clampSize(pdc.getOrDefault(sizeKey, PersistentDataType.INTEGER, 1)));
            String[] layout = pdc.getOrDefault(layoutKey, PersistentDataType.STRING, "").split(";");
            for (int i = 0; i < session.getLayout().length && i < layout.length; i++) {
                try {
                    session.getLayout()[i] = Float.parseFloat(layout[i]);
                } catch (NumberFormatException ignored) {
                }
            }
            try {
                session.setTool(Tool.valueOf(pdc.getOrDefault(toolKey, PersistentDataType.STRING, "BRUSH")));
            } catch (IllegalArgumentException ignored) {
            }
            return session;
        });
    }

    void remember(Player player) {
        val session = session(player);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(colorKey, PersistentDataType.BYTE, session.getColor());
        pdc.set(sizeKey, PersistentDataType.INTEGER, session.getSize());
        pdc.set(toolKey, PersistentDataType.STRING, session.getTool().name());
        pdc.set(squareKey, PersistentDataType.BOOLEAN, session.isSquareBrush());
        val layout = new StringBuilder();
        for (float value : session.getLayout()) layout.append(value).append(';');
        pdc.set(layoutKey, PersistentDataType.STRING, layout.toString());
    }

    public void setPanePosition(Player player, int pane, float u, float v) {
        val session = session(player);
        session.getLayout()[pane * 2] = u;
        session.getLayout()[pane * 2 + 1] = v;
        remember(player);
    }

    public void forget(Player player) {
        val session = sessions.remove(player.getUniqueId());
        if (session == null || session.getStroke() == null) return;
        finish(session.getStroke());
        strokes.remove(session.getStroke());
    }

    public int clampSize(int size) {
        return Math.clamp(size, 1, plugin.getConfigHandler().getMaxBrush());
    }

    public void setColor(Player player, byte color) {
        val session = session(player);
        if (color != session.getColor()) session.setPreviousColor(session.getColor());
        session.setColor(color);
        session.setPickedCell(-1);
        session.setHue(Palette.hueOf(color, session.getHue()));
        changed(player);
    }

    public void resetLayout(Player player) {
        Arrays.fill(session(player).getLayout(), Float.NaN);
        remember(player);
        plugin.getStudioService().refresh(player);
    }

    public void pickCell(Player player, byte color, int cell) {
        session(player).setPickedCell(cell);
        setColorKeepHue(player, color);
    }

    public void setColorKeepHue(Player player, byte color) {
        val session = session(player);
        if (color != session.getColor()) session.setPreviousColor(session.getColor());
        session.setColor(color);
        changed(player);
    }

    public void setHue(Player player, float hue) {
        session(player).setPickedCell(-1);
        session(player).setHue(hue);
        plugin.getStudioService().refresh(player);
    }

    public void setSquareBrush(Player player, boolean square) {
        session(player).setSquareBrush(square);
        changed(player);
    }

    public void setSize(Player player, int size) {
        session(player).setSize(clampSize(size));
        changed(player);
    }

    public void setTool(Player player, Tool tool) {
        session(player).setTool(tool);
        changed(player);
    }

    public void cycleTool(Player player, int step) {
        Tool[] tools = Tool.values();
        setTool(player, tools[Math.floorMod(session(player).getTool().ordinal() + step, tools.length)]);
    }

    void changed(Player player) {
        remember(player);
        showHud(player);
        plugin.getStudioService().refresh(player);
    }

    public void showHud(Player player) {
        val session = session(player);
        Chat.sendActionBar(player, Component.empty()
                .append(Chat.white(session.getTool().getDisplayName()))
                .append(StringUtil.mm(" <dark_gray>· " + Lang.get("hud.size", "size", session.getSize()) + " <dark_gray>· "))
                .append(Palette.chip(session.getColor())));
    }

    public boolean canEdit(Player player, Painting painting) {
        if (painting.signed || !player.hasPermission(PaintCraft.PERMISSION_USE)) return false;
        return painting.owner.equals(player.getUniqueId()) || painting.shared || player.hasPermission(PaintCraft.PERMISSION_ADMIN);
    }

    public void sendCannotEdit(CommandSender sender, Painting painting) {
        Chat.sendActionBar(sender, painting.signed
                ? Chat.red(Lang.get("paint.signed"))
                : Chat.red(Lang.get("paint.not-yours", "player", painting.ownerName)));
    }

    public @Nullable Painting target(Player player) {
        val hit = placements.traceVisible(player, plugin.getConfigHandler().getReach());
        if (hit != null) return hit.placement().painting;

        val session = session(player);
        Painting last = session.getLastPainting();
        if (last != null && storage.get(last.id) != last) {
            session.setLastPainting(null);
            return null;
        }
        return last;
    }

    public void press(Player player, Placement.Hit hit) {
        val session = session(player);
        Painting painting = hit.placement().painting;
        session.setLastPainting(painting);
        if (!canEdit(player, painting)) {
            sendCannotEdit(player, painting);
            return;
        }
        plugin.getStudioService().select(player, hit.placement());
        if (session.getStroke() != null) return;

        Tool tool = session.getTool();
        byte color = tool == Tool.ERASER ? plugin.getConfigHandler().getEraserColor() : session.getColor();
        byte[] pixels = storage.pixels(painting);
        int x = hit.x(), y = hit.y();

        if (tool == Tool.FILL) {
            val step = new UndoStack.Step();
            val draw = new Draw(pixels, painting.width(), painting.height(), step);
            draw.fill(x, y, color);
            commit(painting, step, draw);
            return;
        }

        val stroke = new Stroke();
        stroke.player = player;
        stroke.placement = hit.placement();
        stroke.painting = painting;
        stroke.pixels = pixels;
        stroke.tool = tool;
        stroke.color = color;
        stroke.size = session.getSize();
        stroke.square = session.isSquareBrush();
        stroke.startX = stroke.lastX = x;
        stroke.startY = stroke.lastY = y;
        session.setStroke(stroke);
        strokes.add(stroke);
        apply(stroke, x, y);
    }

    public void tick() {
        if (strokes.isEmpty()) return;

        double reach = plugin.getConfigHandler().getReach() * 4;
        for (Iterator<Stroke> it = strokes.iterator(); it.hasNext(); ) {
            Stroke stroke = it.next();
            Player player = stroke.player;
            boolean alive = player.isOnline()
                    && stroke.painting.pixels == stroke.pixels
                    && stroke.painting.placements.contains(stroke.placement)
                    && plugin.getItemService().isBrush(player.getInventory().getItemInMainHand());
            boolean holding = alive && player.isHandRaised();
            if (!alive || (!holding && stroke.age >= GRACE_TICKS)) {
                finish(stroke);
                it.remove();
                continue;
            }
            stroke.age++;
            if (!holding) continue;

            Location eye = player.getEyeLocation();
            val hit = stroke.placement.trace(eye.toVector(), eye.getDirection(), reach);
            if (hit != null) apply(stroke, hit.x(), hit.y());
        }
    }

    void apply(Stroke stroke, int x, int y) {
        Painting painting = stroke.painting;
        val draw = new Draw(stroke.pixels, painting.width(), painting.height(), stroke.step);
        draw.square = stroke.square;
        boolean straight = stroke.player.isSneaking();
        if (stroke.tool.isShape()) {
            if (straight) {
                int dx = x - stroke.startX, dy = y - stroke.startY;
                int ax = Math.abs(dx), ay = Math.abs(dy);
                if (stroke.tool == Tool.LINE && ax > 2 * ay) {
                    y = stroke.startY;
                } else if (stroke.tool == Tool.LINE && ay > 2 * ax) {
                    x = stroke.startX;
                } else {
                    int side = Math.max(ax, ay);
                    x = stroke.startX + (dx < 0 ? -side : side);
                    y = stroke.startY + (dy < 0 ? -side : side);
                }
            }
            if (stroke.age > 0 && x == stroke.lastX && y == stroke.lastY) return;

            val step = stroke.step;
            int oldMinX = step.minX, oldMinY = step.minY, oldMaxX = step.maxX, oldMaxY = step.maxY;
            step.revertAndClear(stroke.pixels);
            switch (stroke.tool) {
                case LINE -> draw.line(stroke.startX, stroke.startY, x, y, stroke.size, stroke.color);
                case RECT -> draw.rect(stroke.startX, stroke.startY, x, y, stroke.size, stroke.color);
                default -> draw.ellipse(stroke.startX, stroke.startY, x, y, stroke.size, stroke.color);
            }
            bridge.update(painting, Math.min(oldMinX, draw.minX), Math.min(oldMinY, draw.minY),
                    Math.max(oldMaxX, draw.maxX), Math.max(oldMaxY, draw.maxY));
        } else {
            if (straight) {
                if (stroke.axis == 0 && Math.abs(x - stroke.startX) + Math.abs(y - stroke.startY) >= 3) {
                    stroke.axis = Math.abs(x - stroke.startX) >= Math.abs(y - stroke.startY) ? 1 : 2;
                }
                if (stroke.axis == 1) y = stroke.startY;
                else if (stroke.axis == 2) x = stroke.startX;
                else {
                    x = stroke.startX;
                    y = stroke.startY;
                }
            }
            if (stroke.tool == Tool.SPRAY) draw.spray(x, y, stroke.size, stroke.color);
            else draw.line(stroke.lastX, stroke.lastY, x, y, stroke.size, stroke.color);
            if (draw.changed()) bridge.update(painting, draw.minX, draw.minY, draw.maxX, draw.maxY);
        }
        stroke.lastX = x;
        stroke.lastY = y;
    }

    void finish(Stroke stroke) {
        val session = sessions.get(stroke.player.getUniqueId());
        if (session != null && session.getStroke() == stroke) session.setStroke(null);
        if (stroke.painting.pixels != stroke.pixels || stroke.step.isEmpty()) return;

        undoOf(stroke.painting).push(stroke.step);
        stroke.painting.markChanged();
    }

    public void finishStrokes(Painting painting) {
        for (Iterator<Stroke> it = strokes.iterator(); it.hasNext(); ) {
            Stroke stroke = it.next();
            if (stroke.painting != painting) continue;
            finish(stroke);
            it.remove();
        }
    }

    void commit(Painting painting, UndoStack.Step step, Draw draw) {
        if (!draw.changed()) return;
        bridge.update(painting, draw.minX, draw.minY, draw.maxX, draw.maxY);
        undoOf(painting).push(step);
        painting.markChanged();
    }

    UndoStack undoOf(Painting painting) {
        if (painting.undo == null) painting.undo = new UndoStack(plugin.getConfigHandler().getUndoSteps(), 4_000_000);
        return painting.undo;
    }

    public boolean undo(Painting painting, boolean redo) {
        finishStrokes(painting);
        byte[] pixels = storage.pixels(painting);
        val stack = undoOf(painting);
        val step = redo ? stack.redo(pixels) : stack.undo(pixels);
        if (step == null) return false;
        if (step.flipsImported) painting.imported = !painting.imported;

        if (step.isResolution()) {
            int res = painting.res;
            painting.pixels = step.fullPixels;
            painting.res = step.fullRes;
            step.fullPixels = pixels;
            step.fullRes = res;
            painting.markChanged();
            bridge.updateAll(painting);
            plugin.getStudioService().refresh(painting);
            return true;
        }
        bridge.update(painting, step.minX, step.minY, step.maxX, step.maxY);
        painting.markChanged();
        return true;
    }

    public void undo(Player player, boolean redo) {
        Painting painting = target(player);
        if (painting == null) {
            Chat.sendActionBar(player, Chat.gray(Lang.get("paint.undo.look")));
            return;
        }
        if (!canEdit(player, painting)) {
            sendCannotEdit(player, painting);
            return;
        }
        if (undo(painting, redo)) Chat.sendActionBar(player, Chat.gray(Lang.get(redo ? "paint.undo.redone" : "paint.undo.undone")));
        else Chat.sendActionBar(player, Chat.darkGray(Lang.get(redo ? "paint.undo.nothing-redo" : "paint.undo.nothing-undo")));
    }

    public UndoStack.@Nullable Step replace(Painting painting, byte[] replacement) {
        finishStrokes(painting);
        byte[] pixels = storage.pixels(painting);
        int width = painting.width();
        val step = new UndoStack.Step();
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] == replacement[i]) continue;
            step.remember(i, pixels[i], i % width, i / width);
            pixels[i] = replacement[i];
        }
        if (step.isEmpty()) return null;

        bridge.update(painting, step.minX, step.minY, step.maxX, step.maxY);
        undoOf(painting).push(step);
        painting.markChanged();
        return step;
    }

    public UndoStack.@Nullable Step lastStep(Painting painting) {
        return painting.undo == null ? null : painting.undo.peek();
    }

    public void clear(Painting painting) {
        byte[] blank = new byte[painting.width() * painting.height()];
        Arrays.fill(blank, Palette.WHITE);
        val step = replace(painting, blank);
        if (step != null && painting.imported) {
            step.flipsImported = true;
            painting.imported = false;
        }
    }

    public UndoStack.@Nullable Step replaceImported(Painting painting, byte[] replacement) {
        val step = replace(painting, replacement);
        if (step != null && !painting.imported) {
            step.flipsImported = true;
            painting.imported = true;
        }
        return step;
    }

    public void setResolution(Painting painting, int res) {
        if (res == painting.res) return;
        finishStrokes(painting);
        byte[] current = storage.pixels(painting);
        val stack = undoOf(painting);

        boolean untouched = painting.resSource != null && painting.resStamp == painting.changes
                && System.currentTimeMillis() - painting.resTime < RES_SOURCE_MILLIS;
        val last = stack.peek();
        if (untouched && last != null && last.isResolution()) {
            if (last.fullRes == res) stack.dropLast();
            stack.dropRedo();
        } else {
            if (!untouched) {
                painting.resSource = current;
                painting.resSourceRes = painting.res;
            }
            stack.push(UndoStack.Step.resolution(painting.res, current));
        }

        painting.pixels = resample(painting.resSource, painting.blocksW * painting.resSourceRes,
                painting.blocksH * painting.resSourceRes, painting.blocksW * res, painting.blocksH * res);
        painting.res = res;
        painting.markChanged();
        painting.resStamp = painting.changes;
        painting.resTime = System.currentTimeMillis();
        bridge.updateAll(painting);
        plugin.getStudioService().refresh(painting);
    }

    public static byte[] resample(byte[] source, int sw, int sh, int dw, int dh) {
        if (sw == dw && sh == dh) return source.clone();
        byte[] result = new byte[dw * dh];
        for (int y = 0; y < dh; y++) {
            int sy = Math.min(sh - 1, (int) ((y + 0.5) * sh / dh));
            for (int x = 0; x < dw; x++) {
                int sx = Math.min(sw - 1, (int) ((x + 0.5) * sw / dw));
                result[y * dw + x] = source[sy * sw + sx];
            }
        }
        return result;
    }
}
