package me.bbijabnpobatejb.mappaint.diag;

import com.github.retrooper.packetevents.PacketEvents;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.palette.Palette;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.MathUtil;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import me.bbijabnpobatejb.mappaint.world.Placement;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportService {

    public static final int DEFAULT_SECONDS = 5;
    private static final int MAX_SECONDS = 30;
    private static final int KEEP = 20;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx");

    @FieldDefaults(level = AccessLevel.PRIVATE)
    private static final class Recording {
        CommandSender sender;
        @Nullable UUID player;
        int seconds, ticksLeft;
        long mapPackets, mapBytes, displayPackets, strokePoints;
        final List<String> lines = new ArrayList<>();
        final List<String> events = new ArrayList<>();
    }

    final MapPaint plugin;
    @Nullable Recording recording;

    public void start(CommandSender sender, int seconds) {
        if (recording != null) {
            Chat.sendChat(sender, Chat.red(Lang.get("report.busy")));
            return;
        }
        val started = new Recording();
        started.sender = sender;
        started.player = sender instanceof Player player ? player.getUniqueId() : null;
        started.seconds = MathUtil.clamp(seconds, 0, MAX_SECONDS);
        started.ticksLeft = started.seconds * 20;
        snapshot(started);
        recording = started;

        if (started.ticksLeft == 0) {
            finish();
            return;
        }
        Chat.sendChat(sender, StringUtil.mm(Lang.get("report.started", "seconds", started.seconds)));
    }

    public void tick(long... nanos) {
        val current = recording;
        if (current == null) return;

        val line = new StringBuilder(String.format(Locale.ROOT, "%8d", Bukkit.getCurrentTick()));
        for (int phase = 0; phase < Profiler.PHASES.length; phase++) {
            line.append(String.format(Locale.ROOT, " %s=%.3f", Profiler.PHASES[phase], nanos[phase] / 1_000_000.0));
        }
        line.append(" mapPk=").append(Counters.mapPackets - current.mapPackets)
                .append(" mapBytes=").append(Counters.mapBytes - current.mapBytes)
                .append(" dispPk=").append(Counters.displayPackets - current.displayPackets)
                .append(" points=").append(Counters.strokePoints - current.strokePoints);
        if (!current.events.isEmpty()) line.append("  ").append(String.join("; ", current.events));
        current.lines.add(line.toString());
        current.events.clear();
        snapshot(current);

        if (--current.ticksLeft <= 0) finish();
    }

    public void event(Player player, String text) {
        val current = recording;
        if (current != null && player.getUniqueId().equals(current.player)) current.events.add(text);
    }

    public void shutdown() {
        recording = null;
    }

    private void snapshot(Recording target) {
        target.mapPackets = Counters.mapPackets;
        target.mapBytes = Counters.mapBytes;
        target.displayPackets = Counters.displayPackets;
        target.strokePoints = Counters.strokePoints;
    }

    private void finish() {
        val done = recording;
        recording = null;
        if (done == null) return;

        String text;
        try {
            text = build(done);
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Can't build the report", e);
            Chat.sendChat(done.sender, Chat.red(Lang.get("report.failed", "reason", String.valueOf(e))));
            return;
        }
        File folder = new File(plugin.getDataFolder(), "reports");
        File file = new File(folder, "report-" + ZonedDateTime.now().format(FILE_TIME) + ".txt");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String failure = null;
            try {
                Files.createDirectories(folder.toPath());
                Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
                prune(folder);
            } catch (IOException e) {
                failure = String.valueOf(e.getMessage());
            }
            String reason = failure;
            Bukkit.getScheduler().runTask(plugin, () -> announce(done.sender, file, reason));
        });
    }

    private void announce(CommandSender sender, File file, @Nullable String failure) {
        if (failure != null) {
            Chat.sendChat(sender, Chat.red(Lang.get("report.failed", "reason", failure)));
            return;
        }
        String shown = "plugins/" + plugin.getDataFolder().getName() + "/reports/" + file.getName();
        val click = ClickEvent.copyToClipboard(file.getAbsolutePath());
        val hover = HoverEvent.showText(StringUtil.mm(Lang.get("report.copy")));
        Chat.sendChat(sender, StringUtil.mm(Lang.get("report.saved", "path", shown)).clickEvent(click).hoverEvent(hover));
        Chat.sendChat(sender, StringUtil.mm(Lang.get("report.hint")).clickEvent(click).hoverEvent(hover));
    }

    private void prune(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.startsWith("report-") && name.endsWith(".txt"));
        if (files == null || files.length <= KEEP) return;
        Arrays.sort(files);
        for (int i = 0; i < files.length - KEEP; i++) files[i].delete();
    }

    private String build(Recording done) {
        val out = new StringBuilder();
        Player caller = done.player == null ? null : Bukkit.getPlayer(done.player);

        out.append("MapPaint report\n");
        line(out, "Generated", ZonedDateTime.now().format(TEXT_TIME));
        line(out, "Requested by", done.sender.getName());
        out.append("No IP addresses, chat messages or tokens are written to this file.\n");

        section(out, "Plugin");
        line(out, "Version", plugin.getDescription().getVersion());
        line(out, "Jar", jarName());
        line(out, "Language", Lang.code());
        line(out, "bStats", MapPaint.BSTATS_ID > 0 ? "id " + MapPaint.BSTATS_ID : "off");
        line(out, "Update check", plugin.getConfigHandler().isUpdateCheck()
                ? "on, latest seen: " + (plugin.getUpdateChecker().getLatest() == null ? "-" : plugin.getUpdateChecker().getLatest())
                : "off");

        section(out, "Server");
        line(out, "Software", Bukkit.getName() + " " + Bukkit.getVersion());
        line(out, "API", Bukkit.getBukkitVersion());
        line(out, "Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        line(out, "OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        Runtime runtime = Runtime.getRuntime();
        line(out, "CPU threads", runtime.availableProcessors());
        line(out, "Memory", (runtime.totalMemory() - runtime.freeMemory()) / 1048576 + " MB used of " + runtime.maxMemory() / 1048576 + " MB");
        line(out, "TPS (1m, 5m, 15m)", tps());
        line(out, "MSPT", String.format(Locale.ROOT, "%.2f", Bukkit.getAverageTickTime()));
        line(out, "Players online", Bukkit.getOnlinePlayers().size());
        for (World world : Bukkit.getWorlds()) {
            line(out, "World " + world.getName(), world.getEnvironment() + ", chunks " + world.getLoadedChunks().length
                    + ", entities " + world.getEntityCount());
        }

        section(out, "PacketEvents");
        line(out, "Version", PacketEvents.getAPI().getVersion());
        line(out, "Server version", PacketEvents.getAPI().getServerManager().getVersion());

        section(out, "Compatibility");
        line(out, "Prompts", plugin.getPrompts().getClass().getSimpleName());
        line(out, "Items", plugin.getItemStyle().getClass().getSimpleName());
        line(out, "Panel icons", plugin.getIcons() == null ? "item displays" : plugin.getIcons().getClass().getSimpleName());

        section(out, "Players");
        for (Player player : Bukkit.getOnlinePlayers()) {
            line(out, player.getName(), "client " + PacketEvents.getAPI().getPlayerManager().getClientVersion(player)
                    + ", " + player.getGameMode() + ", world " + player.getWorld().getName());
        }

        section(out, "Plugins");
        for (Plugin other : Bukkit.getPluginManager().getPlugins()) {
            line(out, other.getName(), other.getDescription().getVersion() + (other.isEnabled() ? "" : " (disabled)"));
        }

        section(out, "config.yml");
        out.append(config()).append('\n');

        section(out, "State");
        state(out);

        section(out, "Painting under the crosshair");
        target(out, caller);

        section(out, "Tick timings, ms (last " + plugin.getProfiler().getSize() + " ticks)");
        timings(out);

        section(out, "Counters since start");
        line(out, "Map packets", Counters.mapPackets);
        line(out, "Map bytes", Counters.mapBytes);
        line(out, "Display packets", Counters.displayPackets);
        line(out, "Stroke points", Counters.strokePoints);

        section(out, "Recording, " + done.seconds + " s (" + done.lines.size() + " ticks)");
        if (done.lines.isEmpty()) out.append("-\n");
        for (String line : done.lines) out.append(line).append('\n');

        val errors = plugin.getErrorLog().entries();
        section(out, "Recent warnings and errors (" + errors.size() + ")");
        if (errors.isEmpty()) out.append("-\n");
        for (String error : errors) out.append(error).append('\n');
        return out.toString();
    }

    private void state(StringBuilder out) {
        val storage = plugin.getStorage();
        int loaded = 0, signed = 0, imported = 0, dirty = 0, shared = 0;
        for (Painting painting : storage.all()) {
            if (painting.pixels != null) loaded++;
            if (painting.signed) signed++;
            if (painting.imported) imported++;
            if (painting.dirty) dirty++;
            if (painting.shared) shared++;
        }
        line(out, "Paintings", storage.all().size() + " (in memory " + loaded + ", signed " + signed + ", imported " + imported
                + ", shared " + shared + ", unsaved " + dirty + ")");
        line(out, "Map ids", storage.usedMaps() + " used, " + storage.freeMaps() + " spare");

        Map<String, int[]> worlds = new TreeMap<>();
        for (Placement placement : plugin.getPlacementManager().all()) {
            int[] counts = worlds.computeIfAbsent(placement.world.getName(), name -> new int[2]);
            counts[0]++;
            counts[1] += placement.frames.length;
        }
        if (worlds.isEmpty()) line(out, "Placements", 0);
        for (val entry : worlds.entrySet()) {
            line(out, "Placements in " + entry.getKey(), entry.getValue()[0] + " (" + entry.getValue()[1] + " frames)");
        }

        val sessions = plugin.getPaintService().sessions();
        line(out, "Sessions", sessions.size());
        for (val entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            val session = entry.getValue();
            line(out, "  " + (player == null ? entry.getKey().toString() : player.getName()),
                    "tool " + session.getTool() + ", size " + session.getSize() + (session.isSquareBrush() ? " square" : " round")
                            + ", color " + color(session.getColor()) + ", panel " + (player != null && plugin.getStudioService().isOpen(player) ? "open" : "closed")
                            + ", stroke " + (session.getStroke() != null ? "active" : "none"));
        }
    }

    private void target(StringBuilder out, @Nullable Player caller) {
        if (caller == null) {
            out.append("-\n");
            return;
        }
        val hit = plugin.getPlacementManager().trace(caller, plugin.getConfigHandler().getReach() * 4);
        if (hit == null) {
            out.append("none\n");
            return;
        }
        Placement placement = hit.placement();
        Painting painting = placement.painting;
        line(out, "Id", painting.id);
        line(out, "Owner", painting.ownerName);
        line(out, "Size", painting.blocksW + "x" + painting.blocksH + " blocks, " + painting.res + " px/block, "
                + painting.width() + "x" + painting.height() + " px");
        line(out, "Flags", "signed=" + painting.signed + " shared=" + painting.shared + " touched=" + painting.touched
                + " imported=" + painting.imported + " unsaved=" + painting.dirty + " pixels=" + (painting.pixels != null));
        line(out, "Undo", painting.undo == null ? "empty" : painting.undo.getSteps() + " steps, " + painting.undo.getRedoSteps()
                + " redo, " + painting.undo.getPixels() + " px");
        line(out, "Map ids", Arrays.toString(painting.mapIds));
        line(out, "Placed", painting.placements.size() + " time(s)");
        line(out, "This one", placement.world.getName() + " " + placement.ox + " " + placement.oy + " " + placement.oz
                + ", face " + placement.face + ", rotation " + placement.rot);
        int valid = 0;
        for (val frame : placement.frames) {
            if (frame != null && frame.isValid()) valid++;
        }
        line(out, "Frames", valid + " of " + placement.frames.length + " valid, glowing " + plugin.getPlacementManager().isGlowing(placement));
        line(out, "Crosshair", "x=" + hit.x() + " y=" + hit.y() + " inside=" + hit.inside());
        line(out, "Can edit", plugin.getPaintService().canEdit(caller, painting));
    }

    private void timings(StringBuilder out) {
        val profiler = plugin.getProfiler();
        out.append(String.format(Locale.ROOT, "%-10s %8s %8s %8s%n", "phase", "avg", "p95", "max"));
        for (int phase = 0; phase <= Profiler.PHASES.length; phase++) {
            String name = phase < Profiler.PHASES.length ? Profiler.PHASES[phase] : "total";
            out.append(String.format(Locale.ROOT, "%-10s %8.3f %8.3f %8.3f%n", name,
                    profiler.average(phase), profiler.percentile(phase, 0.95), profiler.max(phase)));
        }
    }

    private String config() {
        try {
            return Files.readString(new File(plugin.getDataFolder(), "config.yml").toPath(), StandardCharsets.UTF_8).stripTrailing();
        } catch (IOException e) {
            return "can't read: " + e.getMessage();
        }
    }

    private String jarName() {
        try {
            return new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
        } catch (Exception e) {
            return "?";
        }
    }

    private String tps() {
        val parts = new ArrayList<String>();
        for (double value : Bukkit.getTPS()) parts.add(String.format(Locale.ROOT, "%.2f", Math.min(20.0, value)));
        return String.join(", ", parts);
    }

    private String color(byte color) {
        return Palette.isTransparent(color) ? "transparent" : Palette.hex(color);
    }

    private void section(StringBuilder out, String title) {
        out.append("\n== ").append(title).append(" ==\n");
    }

    private void line(StringBuilder out, String key, Object value) {
        out.append(key).append(": ").append(value).append('\n');
    }
}
