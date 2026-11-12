package me.bbijabnpobatejb.mappaint.studio;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.packet.FakeDisplay;
import me.bbijabnpobatejb.mappaint.packet.PacketScene;
import me.bbijabnpobatejb.mappaint.paint.PlayerSession;
import me.bbijabnpobatejb.mappaint.paint.Tool;
import me.bbijabnpobatejb.mappaint.palette.Palette;
import me.bbijabnpobatejb.mappaint.ui.menu.GalleryGui;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.MathUtil;
import me.bbijabnpobatejb.mappaint.util.Sounds;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import me.bbijabnpobatejb.mappaint.world.Placement;
import me.bbijabnpobatejb.mappaint.world.Plane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StudioService {

    private static final int SLIDE_TICKS = 6;
    private static final int OPEN_DELAY = 3;
    private static final double MAX_DISTANCE = 16;
    private static final int SWITCH_CHECKS = 4;

    private static final int BORDER = 0xFF2B2430;
    private static final int PANEL = 0xFFA999AC;
    private static final int INSET = 0xFF5F5264;
    private static final int SELECTED = 0xFF8FC8D0;
    private static final int HOVER = 0xFFFFFFFF;
    private static final int DOT = 0xFFE4DCE4;

    private static final int[] SIZE_PRESETS = {1, 2, 3, 4, 6, 8, 12, 16};

    private static final double RING_OUTER = 0.46;
    private static final double RING_WIDTH = 0.10;
    private static final int RING_SEGMENTS = 30;
    private static final int SQUARE_CELLS = 7;
    private static final double SQUARE_SIDE = (RING_OUTER - RING_WIDTH) * Math.sqrt(2) - 0.03;

    private static final int ACTIONS = 11;
    private static final double ACTION_CELL = 0.22;
    private static final double EDGE = 0.02;
    private static final double GAP = 0.05;
    private static final double HANDLE = 0.13;
    private static final double DRAG_LIMIT = 3.0;
    private static final int TALL = 4;

    @RequiredArgsConstructor
    public enum Pane {
        TOOLS(0.84, 1.58),
        COLORS(2 * RING_OUTER + 0.10, 1.55),
        ACTIONS(StudioService.ACTIONS * ACTION_CELL + 0.12, 0.50);

        final double w, h;

        String title() {
            return Lang.get("panel.window." + name().toLowerCase(Locale.ROOT));
        }
    }

    final MapPaint plugin;
    final Map<UUID, Studio> studios = new HashMap<>();
    final Map<UUID, Placement> selected = new HashMap<>();
    final Map<UUID, Placement> dismissed = new HashMap<>();
    final BlockData wood = Material.DARK_OAK_PLANKS.createBlockData();
    final BlockData darkWood = Material.SPRUCE_PLANKS.createBlockData();
    boolean broken;

    public boolean isOpen(Player player) {
        Studio studio = studios.get(player.getUniqueId());
        return studio != null && studio.getClosingTicks() < 0;
    }

    public void select(Player player, Placement placement) {
        if (!plugin.getConfigHandler().isPanel()) return;
        dismissed.remove(player.getUniqueId());
        Studio current = studios.get(player.getUniqueId());
        if (current != null) {
            if (current.getPlacement() == placement && current.getClosingTicks() < 0) return;
            current.getScene().destroy();
        }
        selected.put(player.getUniqueId(), placement);
        open(player, placement);
    }

    private void open(Player player, Placement placement) {
        val studio = new Studio(player, placement);
        studios.put(player.getUniqueId(), studio);
        studio.getScene().setViewers(List.of(player));
        render(studio, true, 0);
    }

    public void close(Player player) {
        Placement placement = selected.remove(player.getUniqueId());
        if (placement != null) dismissed.put(player.getUniqueId(), placement);
        collapse(player);
    }

    private void collapse(Player player) {
        Studio studio = studios.get(player.getUniqueId());
        if (studio == null || studio.getClosingTicks() >= 0) return;
        if (studio.getDragging() != null) drop(studio);
        studio.setHovered(null);
        render(studio, true, SLIDE_TICKS);
        studio.setClosingTicks(SLIDE_TICKS + 1);
    }

    public void forget(Player player) {
        selected.remove(player.getUniqueId());
        dismissed.remove(player.getUniqueId());
        Studio studio = studios.remove(player.getUniqueId());
        if (studio != null) studio.getScene().forgetViewer(player.getUniqueId());
    }

    public void shutdown() {
        for (Studio studio : studios.values()) studio.getScene().destroy();
        studios.clear();
        selected.clear();
        dismissed.clear();
    }

    public void refresh(Player player) {
        Studio studio = studios.get(player.getUniqueId());
        if (studio != null && studio.getClosingTicks() < 0 && studio.getAge() >= OPEN_DELAY) render(studio, false, 3);
    }

    public void refresh(Painting painting) {
        for (Studio studio : studios.values()) {
            if (studio.getPlacement().painting == painting) refresh(studio.getPlayer());
        }
    }

    public void tick() {
        if (!plugin.getConfigHandler().isPanel()) return;
        selectByLook();
        reopen();
        if (studios.isEmpty()) return;
        int tick = Bukkit.getCurrentTick();
        for (Iterator<Studio> it = studios.values().iterator(); it.hasNext(); ) {
            Studio studio = it.next();
            Player player = studio.getPlayer();
            if (studio.getClosingTicks() >= 0) {
                studio.setClosingTicks(studio.getClosingTicks() - 1);
                if (studio.getClosingTicks() <= 0) {
                    studio.getScene().destroy();
                    it.remove();
                }
                continue;
            }
            if (!isValid(studio)) {
                if (!player.isOnline() || player.getWorld() != studio.getPlacement().world) {
                    studio.getScene().forgetViewer(player.getUniqueId());
                    it.remove();
                } else {
                    collapse(player);
                }
                continue;
            }

            studio.setAge(studio.getAge() + 1);
            if (studio.getAge() < OPEN_DELAY) continue;
            if (studio.getAge() == OPEN_DELAY) {
                render(studio, false, SLIDE_TICKS);
                continue;
            }
            if (studio.getDragging() != null) {
                drag(studio);
                continue;
            }

            Widget hovered = find(studio);
            if (hovered != null && hovered.click() == null) hovered = null;
            if (hovered != studio.getHovered()) {
                if (hovered == null && studio.getHovered() != null && studio.getHovered().name() != null
                        && tick - studio.getLastClickTick() > 40) {
                    Chat.sendActionBar(player, Component.empty());
                }
                studio.setHovered(hovered);
                render(studio, false, 2);
                studio.setLastHintTick(-100);
            }
            if (hovered != null && hovered.name() != null && tick - studio.getLastHintTick() >= 30) {
                studio.setLastHintTick(tick);
                Chat.sendActionBar(player, StringUtil.mm(hovered.name()));
            }
        }
    }

    private void selectByLook() {
        if ((Bukkit.getCurrentTick() & 1) != 0) return;
        double reach = plugin.getConfigHandler().getReach();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getItemService().isBrush(player.getInventory().getItemInMainHand())) continue;

            Studio studio = studios.get(player.getUniqueId());
            boolean open = studio != null && studio.getClosingTicks() < 0;
            if (open && (studio.getDragging() != null || find(studio) != null)) {
                studio.setLookTarget(null);
                continue;
            }

            val hit = plugin.getPlacementManager().traceVisible(player, reach);
            Placement placement = hit == null ? null : hit.placement();
            if (placement == null || (open && studio.getPlacement() == placement)) {
                if (open) studio.setLookTarget(null);
                continue;
            }
            if (dismissed.get(player.getUniqueId()) == placement) continue;
            if (!plugin.getPaintService().canEdit(player, placement.painting)) continue;
            if (open) {
                if (studio.getLookTarget() != placement) {
                    studio.setLookTarget(placement);
                    studio.setLookChecks(0);
                }
                studio.setLookChecks(studio.getLookChecks() + 1);
                if (studio.getLookChecks() < SWITCH_CHECKS) continue;
            }
            select(player, placement);
        }
    }

    private void reopen() {
        if (selected.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Placement>> it = selected.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Placement> entry = it.next();
            if (studios.containsKey(entry.getKey())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            Placement placement = entry.getValue();
            if (player == null || !isReachable(player, placement)) {
                it.remove();
                continue;
            }
            if (!plugin.getItemService().isBrush(player.getInventory().getItemInMainHand())) continue;
            open(player, placement);
        }
    }

    private boolean isValid(Studio studio) {
        Player player = studio.getPlayer();
        if (!player.isOnline() || !isReachable(player, studio.getPlacement())) return false;
        return plugin.getItemService().isBrush(player.getInventory().getItemInMainHand());
    }

    private boolean isReachable(Player player, Placement placement) {
        if (player.getWorld() != placement.world) return false;
        if (!placement.painting.placements.contains(placement)) return false;
        if (!plugin.getPaintService().canEdit(player, placement.painting)) return false;
        return placement.center().distanceSquared(player.getLocation()) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    private Placement.@Nullable Hit look(Studio studio, double distance) {
        Location eye = studio.getPlayer().getEyeLocation();
        return studio.getPlacement().trace(eye.toVector(), eye.getDirection(), distance);
    }

    private @Nullable Widget find(Studio studio) {
        val hit = look(studio, plugin.getConfigHandler().getReach() + 2);
        if (hit == null) return null;
        List<Widget> widgets = studio.getWidgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).contains(hit.a(), hit.b())) return widgets.get(i);
        }
        return null;
    }

    public boolean click(Player player) {
        Studio studio = studios.get(player.getUniqueId());
        if (studio == null || studio.getClosingTicks() >= 0 || studio.getAge() <= OPEN_DELAY) return false;
        if (studio.getDragging() != null) {
            if (!studio.isDragHeld() && studio.getDragTicks() >= 4) drop(studio);
            return true;
        }
        Widget widget = find(studio);
        if (widget == null) return false;
        if (widget.click() == null) return true;

        int tick = Bukkit.getCurrentTick();
        if (tick - studio.getLastClickTick() < 3) return true;
        studio.setLastClickTick(tick);
        Sounds.playSound(player, Sounds.HAT, .6f, 1f);
        widget.click().accept(player);
        return true;
    }

    public boolean isDragging(Player player) {
        Studio studio = studios.get(player.getUniqueId());
        return studio != null && studio.getDragging() != null;
    }

    private void grab(Player player, Pane pane) {
        Studio studio = studios.get(player.getUniqueId());
        if (studio == null) return;
        if (player.isSneaking()) {
            plugin.getPaintService().setPanePosition(player, pane.ordinal(), Float.NaN, Float.NaN);
            render(studio, false, 4);
            Chat.sendActionBar(player, Chat.gray(Lang.get("panel.window-reset", "window", pane.title())));
            return;
        }
        val hit = look(studio, MAX_DISTANCE);
        double[] position = studio.getPanes().get(pane);
        if (hit == null || position == null) return;
        studio.setDragging(pane);
        studio.setDragPosition(position.clone());
        studio.setGrabA(hit.a() - position[0]);
        studio.setGrabB(hit.b() - position[1]);
        studio.setDragHeld(false);
        studio.setDragTicks(0);
        studio.setHovered(null);
        render(studio, false, 2);
        Chat.sendActionBar(player, StringUtil.mm(Lang.get("panel.window-grabbed", "window", pane.title())));
    }

    private void drag(Studio studio) {
        Player player = studio.getPlayer();
        Pane pane = studio.getDragging();
        studio.setDragTicks(studio.getDragTicks() + 1);
        if (plugin.getItemStyle().isHolding(player)) {
            studio.setDragHeld(true);
        } else if (studio.isDragHeld()) {
            drop(studio);
            return;
        }

        val hit = look(studio, MAX_DISTANCE);
        if (hit == null) return;
        Painting painting = studio.getPlacement().painting;
        double[] old = studio.getDragPosition();
        double a = MathUtil.clamp(hit.a() - studio.getGrabA(), -pane.w - DRAG_LIMIT, painting.blocksW + DRAG_LIMIT);
        double b = MathUtil.clamp(hit.b() - studio.getGrabB(), -pane.h - DRAG_LIMIT, painting.blocksH + DRAG_LIMIT);
        double[] moved = resolve(pane, a, b, obstacles(studio, pane), old);
        if (Math.abs(moved[0] - old[0]) < 1.0e-3 && Math.abs(moved[1] - old[1]) < 1.0e-3) return;
        studio.setDragPosition(moved);
        render(studio, false, 2);
    }

    private void drop(Studio studio) {
        Pane pane = studio.getDragging();
        if (pane == null) return;
        Painting painting = studio.getPlacement().painting;
        double[] position = studio.getDragPosition();
        studio.setDragging(null);
        plugin.getPaintService().setPanePosition(studio.getPlayer(), pane.ordinal(),
                (float) normalize(position[0] + pane.w / 2, painting.blocksW),
                (float) normalize(position[1] + pane.h / 2, painting.blocksH));
        Sounds.playSound(studio.getPlayer(), Sounds.HAT, .6f, .8f);
        if (studio.getClosingTicks() < 0) render(studio, false, 2);
    }

    private static double normalize(double value, double size) {
        if (value < 0) return value;
        return value > size ? 1 + value - size : value / size;
    }

    private static double denormalize(double value, double size) {
        if (value < 0) return value;
        return value > 1 ? size + value - 1 : value * size;
    }

    private static double[] defaultPosition(Pane pane, double width, double height) {
        double middle = height >= TALL ? height - 1.5 : height / 2;
        return switch (pane) {
            case TOOLS -> new double[]{-0.24 - pane.w, middle - pane.h / 2};
            case COLORS -> new double[]{width + 0.24, middle - pane.h / 2};
            case ACTIONS -> {
                double a = width / 2 - pane.w / 2;
                if (height >= TALL) yield new double[]{a, height + 0.30};
                double sideTop = middle - Math.max(Pane.TOOLS.h, Pane.COLORS.h) / 2;
                yield new double[]{a, Math.min(-0.14, sideTop - 0.07) - pane.h};
            }
        };
    }

    private void layout(Studio studio) {
        Painting painting = studio.getPlacement().painting;
        double width = painting.blocksW, height = painting.blocksH;
        float[] custom = plugin.getPaintService().session(studio.getPlayer()).getLayout();
        Map<Pane, double[]> panes = studio.getPanes();
        panes.clear();
        for (Pane pane : Pane.values()) {
            if (pane != studio.getDragging() && Float.isNaN(custom[pane.ordinal() * 2])) {
                panes.put(pane, defaultPosition(pane, width, height));
            }
        }
        for (Pane pane : Pane.values()) {
            if (pane == studio.getDragging() || panes.containsKey(pane)) continue;
            double a = denormalize(custom[pane.ordinal() * 2], width) - pane.w / 2;
            double b = denormalize(custom[pane.ordinal() * 2 + 1], height) - pane.h / 2;
            panes.put(pane, resolve(pane, a, b, obstacles(studio, pane), defaultPosition(pane, width, height)));
        }
        if (studio.getDragging() != null) panes.put(studio.getDragging(), studio.getDragPosition());
    }

    private List<double[]> obstacles(Studio studio, Pane self) {
        Painting painting = studio.getPlacement().painting;
        double margin = EDGE + GAP;
        List<double[]> result = new ArrayList<>(3);
        result.add(new double[]{-0.19 - margin, -0.10 - margin, painting.blocksW + 0.19 + margin, painting.blocksH + 0.22 + margin});
        for (Map.Entry<Pane, double[]> entry : studio.getPanes().entrySet()) {
            Pane pane = entry.getKey();
            if (pane == self) continue;
            double[] p = entry.getValue();
            result.add(new double[]{p[0] - 2 * EDGE - GAP, p[1] - 2 * EDGE - GAP, p[0] + pane.w + 2 * EDGE + GAP, p[1] + pane.h + 2 * EDGE + GAP});
        }
        return result;
    }

    private static double[] resolve(Pane pane, double a, double b, List<double[]> obstacles, double[] fallback) {
        for (int pass = 0; pass < 6; pass++) {
            boolean clear = true;
            for (double[] o : obstacles) {
                double left = o[0] - (a + pane.w), right = o[2] - a, up = o[1] - (b + pane.h), down = o[3] - b;
                if (left >= 0 || right <= 0 || up >= 0 || down <= 0) continue;
                clear = false;
                double da = -left < right ? left : right, db = -up < down ? up : down;
                if (Math.abs(da) <= Math.abs(db)) a += da;
                else b += db;
            }
            if (clear) return new double[]{a, b};
        }
        return fallback;
    }

    private void render(Studio studio, boolean collapsed, int ticks) {
        if (broken) return;
        try {
            layout(studio);
            val draw = new Draw(studio, collapsed, ticks);
            studio.getScene().beginFrame();
            studio.getWidgets().clear();
            draw.easel();
            draw.toolPanel();
            draw.colorPanel();
            draw.actionBar();
            draw.hover();
            studio.getScene().endFrame();
        } catch (RuntimeException | LinkageError e) {
            broken = true;
            plugin.getLogger().log(Level.SEVERE, "Canvas panel disabled: can't build its packets", e);
        }
    }

    private final class Draw {

        static final double L_BORDER = 0.020, L_PANEL = 0.024, L_INSET = 0.028, L_HOVER = 0.031, L_SELECTED = 0.033,
                L_CONTENT = 0.036, L_ICON = 0.044;

        final Studio studio;
        final Player player;
        final PacketScene scene;
        final Plane plane;
        final Painting painting;
        final PlayerSession session;
        final boolean collapsed;
        final int ticks;
        final double width, height;
        @Nullable Vector fold;

        Draw(Studio studio, boolean collapsed, int ticks) {
            this.studio = studio;
            this.player = studio.getPlayer();
            this.scene = studio.getScene();
            this.plane = studio.getPlacement().plane;
            this.painting = studio.getPlacement().painting;
            this.session = plugin.getPaintService().session(player);
            this.collapsed = collapsed;
            this.ticks = ticks;
            this.width = painting.blocksW;
            this.height = painting.blocksH;
        }

        void easel() {
            fold = null;
            box("easel-left", -0.13, -0.10, -0.01, height + 0.22, 0, 0.12, wood);
            box("easel-right", width + 0.01, -0.10, width + 0.13, height + 0.22, 0, 0.12, wood);
            box("easel-top", -0.01, -0.07, width + 0.01, -0.01, 0, 0.07, darkWood);
            box("easel-shelf", -0.19, height + 0.01, width + 0.19, height + 0.10, 0, 0.26, wood);
            box("easel-lip", -0.19, height - 0.03, width + 0.19, height + 0.01, 0.22, 0.26, darkWood);
        }

        void toolPanel() {
            Pane pane = Pane.TOOLS;
            double[] origin = begin(pane, Lang.get("panel.title.tools"));
            double w = pane.w, a0 = origin[0], a1 = a0 + w;
            double b = origin[1] + 0.06 + 0.17;

            rect("tools-name-bg", a0 + 0.06, b, a1 - 0.06, b + 0.16, L_INSET, INSET);
            String toolName = session.getTool().getDisplayName();
            double nameHeight = fitHeight(toolName, 0.15, w - 0.18);
            label("tools-name", (a0 + a1) / 2, b + 0.08 + nameHeight * 0.44, nameHeight, Chat.white(toolName));
            b += 0.21;

            Tool[] tools = Tool.values();
            double cell = 0.18;
            for (int i = 0; i < tools.length; i++) {
                Tool tool = tools[i];
                double ca = a0 + 0.06 + (i % 4) * cell, cb = b + (i / 4) * cell;
                if (tool == session.getTool()) rect("tool-selected", ca, cb, ca + cell, cb + cell, L_SELECTED, SELECTED);
                icon("tool-" + tool.name(), ca + cell / 2, cb + cell / 2, cell * 0.8, tool.getIcon());
                button(ca, cb, ca + cell, cb + cell, "<#FF7800>" + tool.getDisplayName() + " <dark_gray>» <gray>" + tool.getHint(),
                        p -> plugin.getPaintService().setTool(p, tool));
            }
            b += 2 * cell + 0.06;

            boolean square = session.isSquareBrush();
            rect("sizes-bg", a0 + 0.06, b, a1 - 0.06, b + 2 * cell + 0.04, L_INSET, INSET);
            b += 0.02;
            for (int i = 0; i < SIZE_PRESETS.length; i++) {
                int size = SIZE_PRESETS[i];
                double ca = a0 + 0.06 + (i % 4) * cell, cb = b + (i / 4) * cell;
                if (size == session.getSize()) rect("size-selected", ca + 0.01, cb + 0.01, ca + cell - 0.01, cb + cell - 0.01, L_SELECTED, SELECTED);
                dot("size-" + size, ca + cell / 2, cb + cell / 2, 0.03 + size * 0.0085, !square);
                button(ca, cb, ca + cell, cb + cell, Lang.get("panel.size", "size", size),
                        p -> plugin.getPaintService().setSize(p, size));
            }
            b += 2 * cell + 0.07;

            double half = (w - 0.12) / 2;
            for (int i = 0; i < 2; i++) {
                boolean squareButton = i == 1;
                double ca = a0 + 0.06 + i * half;
                if (square == squareButton) rect("shape-selected", ca, b, ca + half, b + 0.20, L_SELECTED, SELECTED);
                else rect("shape-bg-" + i, ca + 0.01, b + 0.01, ca + half - 0.01, b + 0.19, L_INSET, INSET);
                dot("shape-" + i, ca + half / 2, b + 0.10, 0.12, !squareButton);
                button(ca, b, ca + half, b + 0.20,
                        Lang.get(squareButton ? "panel.shape.square" : "panel.shape.round"),
                        p -> plugin.getPaintService().setSquareBrush(p, squareButton));
            }
        }

        void colorPanel() {
            Pane pane = Pane.COLORS;
            double[] origin = begin(pane, Lang.get("panel.title.colors"));
            double a0 = origin[0], a1 = a0 + pane.w;
            double b = origin[1] + 0.06 + 0.17;

            byte current = session.getColor();
            boolean transparent = Palette.isTransparent(current);
            float hue = session.getHue();
            double cx = (a0 + a1) / 2, cy = b + RING_OUTER + 0.02;

            double middle = RING_OUTER - RING_WIDTH / 2;
            double segment = 2 * Math.PI * RING_OUTER / RING_SEGMENTS * 1.03;
            int selectedSegment = Math.floorMod(Math.round(hue * RING_SEGMENTS), RING_SEGMENTS);
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double angle = 2 * Math.PI * i / RING_SEGMENTS;
                double sa = cx + middle * Math.sin(angle), sb = cy - middle * Math.cos(angle);
                float segmentHue = (float) i / RING_SEGMENTS;
                if (i == selectedSegment) {
                    finish(plane.rectRotated(scene.text("hue-selected"), sa, sb, segment + 0.03, RING_WIDTH + 0.04, angle, L_SELECTED, HOVER));
                }
                finish(plane.rectRotated(scene.text("hue-" + i), sa, sb, segment, RING_WIDTH, angle, L_CONTENT + (i % 2) * 0.002,
                        0xFF000000 | java.awt.Color.HSBtoRGB(segmentHue, 1f, 1f)));
                double r = RING_WIDTH / 2;
                swatch(sa - r, sb - r, sa + r, sb + r, Lang.get("panel.hue"),
                        p -> plugin.getPaintService().setHue(p, segmentHue));
            }

            double side = SQUARE_SIDE, cell = side / SQUARE_CELLS;
            double qa = cx - side / 2, qb = cy - side / 2;
            rect("square-bg", qa - 0.012, qb - 0.012, qa + side + 0.012, qb + side + 0.012, L_INSET, INSET);
            byte[] cells = new byte[SQUARE_CELLS * SQUARE_CELLS];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = Palette.fromHsb(hue, (float) (i % SQUARE_CELLS) / (SQUARE_CELLS - 1), 1f - (float) (i / SQUARE_CELLS) / SQUARE_CELLS);
            }
            int marked = -1;
            if (!transparent) {
                int picked = session.getPickedCell();
                if (picked >= 0 && picked < cells.length && cells[picked] == current) {
                    marked = picked;
                } else {
                    for (int i = 0; i < cells.length && marked < 0; i++) if (cells[i] == current) marked = i;
                }
            }
            for (int i = 0; i < cells.length; i++) {
                byte color = cells[i];
                int index = i;
                double ca = qa + (i % SQUARE_CELLS) * cell, cb = qb + (i / SQUARE_CELLS) * cell;
                if (i == marked) {
                    rect("cell-selected", ca - 0.012, cb - 0.012, ca + cell + 0.012, cb + cell + 0.012, L_CONTENT + 0.003, HOVER);
                }
                rect("cell-" + i, ca, cb, ca + cell, cb + cell, i == marked ? L_CONTENT + 0.006 : L_CONTENT, argb(color));
                swatch(ca, cb, ca + cell, cb + cell, chip(color), p -> plugin.getPaintService().pickCell(p, color, index));
            }
            b = cy + RING_OUTER + 0.08;

            double big = 0.24;
            rect("current-frame", a0 + 0.05, b, a0 + 0.05 + big, b + big, L_SELECTED, SELECTED);
            if (transparent) icon("current-none", a0 + 0.05 + big / 2, b + big / 2, big * 0.7, Material.BARRIER);
            else rect("current", a0 + 0.075, b + 0.025, a0 + 0.025 + big, b + big - 0.025, L_CONTENT, argb(current));
            button(a0 + 0.05, b, a0 + 0.05 + big, b + big, Lang.get("panel.current-color", "color", name(current)),
                    p -> plugin.getPrompts().hex(p));

            byte previous = session.getPreviousColor();
            double pa = a0 + 0.05 + big + 0.03;
            rect("previous-frame", pa, b + 0.09, pa + 0.15, b + big, L_INSET, INSET);
            if (Palette.isTransparent(previous)) icon("previous-none", pa + 0.075, b + 0.165, 0.10, Material.BARRIER);
            else rect("previous", pa + 0.02, b + 0.11, pa + 0.13, b + big - 0.02, L_CONTENT, argb(previous));
            button(pa, b + 0.09, pa + 0.15, b + big, Lang.get("panel.previous-color", "color", name(previous)),
                    p -> plugin.getPaintService().setColor(p, previous));

            double ta = pa + 0.18;
            if (transparent) rect("none-selected", ta, b + 0.09, ta + 0.15, b + big, L_SELECTED, SELECTED);
            icon("color-none", ta + 0.075, b + 0.165, 0.12, Material.BARRIER);
            button(ta, b + 0.09, ta + 0.15, b + big, Lang.get("panel.transparent"),
                    p -> plugin.getPaintService().setColor(p, Palette.TRANSPARENT));

            int currentBase = transparent ? Palette.baseOf(Palette.WHITE) : Palette.baseOf(current);
            double shadesFrom = ta + 0.18, shade = (a1 - 0.05 - shadesFrom) / 4;
            for (int level = 0; level < 4; level++) {
                byte color = Palette.shade(currentBase, level);
                double sa = shadesFrom + level * shade;
                if (color == current) rect("shade-selected", sa, b + 0.07, sa + shade, b + big, L_SELECTED, SELECTED);
                rect("shade-" + level, sa + 0.01, b + 0.08, sa + shade - 0.01, b + big - 0.01, L_CONTENT, argb(color));
                swatch(sa + 0.01, b + 0.08, sa + shade - 0.01, b + big - 0.01, Lang.get("panel.shade", "color", chip(color)),
                        p -> plugin.getPaintService().setColorKeepHue(p, color));
            }
        }

        void actionBar() {
            val paint = plugin.getPaintService();
            boolean owner = painting.owner.equals(player.getUniqueId()) || player.hasPermission(MapPaint.PERMISSION_ADMIN);
            record Action(Material icon, String name, Consumer<Player> run) {
            }
            List<Action> actions = List.of(
                    new Action(Material.ARROW, Lang.get("panel.action.undo"), p -> paint.undo(p, false)),
                    new Action(Material.SPECTRAL_ARROW, Lang.get("panel.action.redo"), p -> paint.undo(p, true)),
                    new Action(Material.WHITE_WOOL, Lang.get("panel.action.clear"), p -> {
                        paint.clear(painting);
                        Chat.sendActionBar(p, StringUtil.mm(Lang.get("common.canvas-cleared")));
                    }),
                    new Action(Material.SPYGLASS, Lang.get("panel.action.resolution", "res", painting.res),
                            p -> {
                                List<Integer> options = plugin.getConfigHandler().getResolutions();
                                paint.setResolution(painting, options.get((options.indexOf(painting.res) + 1) % options.size()));
                            }),
                    new Action(painting.shared ? Material.LIME_DYE : Material.GRAY_DYE,
                            Lang.get("panel.action.shared", "state", Chat.getToggleString(painting.shared)), p -> {
                        if (!owner) {
                            paint.sendCannotEdit(p, painting);
                            return;
                        }
                        if (!plugin.getConfigHandler().isSharedPainting()) {
                            Chat.sendActionBar(p, Chat.red(Lang.get("common.feature-off")));
                            return;
                        }
                        painting.shared = !painting.shared;
                        painting.dirty = true;
                        refresh(painting);
                    }),
                    new Action(Material.WRITABLE_BOOK, Lang.get("panel.action.sign"), p -> {
                        if (owner) plugin.getPrompts().sign(p, painting);
                        else paint.sendCannotEdit(p, painting);
                    }),
                    new Action(Material.PAPER, Lang.get("panel.action.export"),
                            p -> plugin.getExchangeService().export(p, painting)),
                    new Action(Material.HOPPER, Lang.get("panel.action.import"),
                            p -> plugin.getPrompts().importCode(p, null)),
                    new Action(Material.PAINTING, Lang.get("panel.action.image"),
                            p -> plugin.getPrompts().image(p, null)),
                    new Action(Material.BOOKSHELF, Lang.get("panel.action.gallery"), p -> new GalleryGui(plugin, p, 0).onOpen(p)),
                    new Action(Material.BARRIER, Lang.get("panel.action.hide"),
                            StudioService.this::close));

            Pane pane = Pane.ACTIONS;
            String size = Lang.get("panel.pixels", "size", painting.width() + "×" + painting.height());
            double[] origin = begin(pane, painting.getDisplayTitle() + " · " + size);
            double a0 = origin[0], b = origin[1] + 0.22;
            for (int i = 0; i < actions.size(); i++) {
                Action action = actions.get(i);
                double ca = a0 + 0.06 + i * ACTION_CELL;
                icon("action-" + i, ca + ACTION_CELL / 2, b + ACTION_CELL / 2, ACTION_CELL * 0.82, action.icon());
                button(ca, b, ca + ACTION_CELL, b + ACTION_CELL, action.name(), action.run());
            }
        }

        void hover() {
            Widget hovered = studio.getHovered();
            if (hovered == null) return;
            if (hovered.swatch()) {
                double t = 0.014, out = L_ICON + 0.004;
                double a0 = hovered.a0(), b0 = hovered.b0(), a1 = hovered.a1(), b1 = hovered.b1();
                rect("hover", a0 - t, b0 - t, a1 + t, b0, out, HOVER);
                rect("hover-bottom", a0 - t, b1, a1 + t, b1 + t, out, HOVER);
                rect("hover-left", a0 - t, b0, a0, b1, out, HOVER);
                rect("hover-right", a1, b0, a1 + t, b1, out, HOVER);
                return;
            }
            rect("hover", hovered.a0() - 0.008, hovered.b0() - 0.008, hovered.a1() + 0.008, hovered.b1() + 0.008, L_HOVER, HOVER);
        }

        double[] begin(Pane pane, String title) {
            double[] origin = studio.getPanes().get(pane);
            double a0 = origin[0], b0 = origin[1], a1 = a0 + pane.w, b1 = b0 + pane.h;
            double fa = MathUtil.clamp((a0 + a1) / 2, 0, width), fb = MathUtil.clamp((b0 + b1) / 2, 0, height);
            fold = plane.point(fa, fb, 0.02);

            String key = pane.name().toLowerCase(Locale.ROOT);
            rect(key + "-border", a0 - EDGE, b0 - EDGE, a1 + EDGE, b1 + EDGE, L_BORDER, BORDER);
            rect(key + "-bg", a0, b0, a1, b1, L_PANEL, PANEL);
            studio.getWidgets().add(new Widget(a0 - EDGE, b0 - EDGE, a1 + EDGE, b1 + EDGE, null, null));

            double titleHeight = fitHeight(title, 0.14, pane.w - 2 * HANDLE - 0.14);
            label(key + "-title", (a0 + a1) / 2, b0 + 0.115 + titleHeight * 0.52, titleHeight, Component.text(title, TextColor.color(0x3A3040)));

            boolean dragging = studio.getDragging() == pane;
            double ha = a1 - HANDLE - 0.03, hb = b0 + 0.03, hc = ha + HANDLE / 2, hm = hb + HANDLE / 2;
            rect(key + "-handle", ha, hb, ha + HANDLE, hb + HANDLE, L_INSET, dragging ? SELECTED : INSET);
            rect(key + "-handle-h", ha + 0.02, hm - 0.012, ha + HANDLE - 0.02, hm + 0.012, L_CONTENT, DOT);
            rect(key + "-handle-v", hc - 0.012, hb + 0.02, hc + 0.012, hb + HANDLE - 0.02, L_CONTENT + 0.002, DOT);
            button(ha, hb, ha + HANDLE, hb + HANDLE, Lang.get("panel.move"), p -> grab(p, pane));
            return origin;
        }

        void button(double a0, double b0, double a1, double b1, String name, Consumer<Player> click) {
            studio.getWidgets().add(new Widget(a0, b0, a1, b1, name, click));
        }

        void swatch(double a0, double b0, double a1, double b1, String name, Consumer<Player> click) {
            studio.getWidgets().add(new Widget(a0, b0, a1, b1, name, click, true));
        }

        void rect(String key, double a0, double b0, double a1, double b1, double out, int argb) {
            finish(plane.rect(scene.text(key), a0, b0, a1, b1, out, argb));
        }

        void label(String key, double a, double b, double size, Component text) {
            finish(plane.label(scene.text(key), a, b, size, L_ICON, text));
        }

        void icon(String key, double a, double b, double size, Material material) {
            val icons = plugin.getIcons();
            if (icons != null) finish(plane.sprite(scene.text(key), a, b, size, L_ICON, icons.sprite(material)));
            else finish(plane.icon(scene.item(key), a, b, size, L_ICON, new ItemStack(material)));
        }

        void dot(String key, double a, double b, double size, boolean round) {
            double r = size / 2;
            if (!round || size < 0.05) {
                rect(key, a - r, b - r, a + r, b + r, L_CONTENT, DOT);
                return;
            }
            rect(key, a - r * 0.72, b - r * 0.72, a + r * 0.72, b + r * 0.72, L_CONTENT, DOT);
            rect(key + "-wide", a - r, b - r * 0.42, a + r, b + r * 0.42, L_CONTENT + 0.002, DOT);
            rect(key + "-tall", a - r * 0.42, b - r, a + r * 0.42, b + r, L_CONTENT + 0.004, DOT);
        }

        double fitHeight(String text, double maxHeight, double maxWidth) {
            double widthAtFullHeight = text.length() * 6 * 0.025 * (maxHeight / 0.25);
            return widthAtFullHeight <= maxWidth ? maxHeight : maxHeight * maxWidth / widthAtFullHeight;
        }

        void box(String key, double a0, double b0, double a1, double b1, double out0, double out1, BlockData block) {
            finish(plane.box(scene.block(key), a0, b0, a1, b1, out0, out1, block));
        }

        void finish(FakeDisplay display) {
            display.interpolation(ticks);
            if (!collapsed) return;
            if (fold != null) {
                Vector position = display.position();
                display.translation().mul(0.04f).add(
                        (float) ((fold.getX() - position.getX()) * 0.96),
                        (float) ((fold.getY() - position.getY()) * 0.96),
                        (float) ((fold.getZ() - position.getZ()) * 0.96));
            }
            display.scale().mul(0.04f);
        }

        int argb(byte color) {
            return 0xFF000000 | Palette.rgb(color);
        }

        String name(byte color) {
            return Palette.isTransparent(color) ? Lang.get("color.transparent-short") : Palette.hex(color);
        }

        String chip(byte color) {
            return "<" + Palette.hex(color) + ">⬛ " + Palette.hex(color);
        }
    }
}
