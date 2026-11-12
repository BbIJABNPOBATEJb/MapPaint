package me.bbijabnpobatejb.paintcraft.util;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateChecker implements Listener {

    public static final String PAGE = "https://github.com/BbIJABNPOBATEJb/PaintCraft";
    private static final String LATEST = "https://api.github.com/repos/BbIJABNPOBATEJb/PaintCraft/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");

    final PaintCraft plugin;
    @Getter
    volatile @Nullable String latest;

    public void check() {
        if (!plugin.getConfigHandler().isUpdateCheck()) return;
        String current = plugin.getDescription().getVersion();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                val request = HttpRequest.newBuilder(URI.create(LATEST))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "PaintCraft")
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();
                val response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return;
                val matcher = TAG.matcher(response.body());
                if (!matcher.find() || !isNewer(matcher.group(1), current)) return;
                latest = matcher.group(1);
                plugin.getLogger().info("PaintCraft " + latest + " is available (running " + current + "): " + PAGE + "/releases");
            } catch (Exception ignored) {
            }
        });
    }

    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[.\\-+]"), b = current.split("[.\\-+]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = number(a, i), y = number(b, i);
            if (x != y) return x > y;
        }
        return false;
    }

    private static int number(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String version = latest;
        Player player = event.getPlayer();
        if (version == null || !plugin.getConfigHandler().isUpdateCheck() || !player.hasPermission(PaintCraft.PERMISSION_ADMIN)) return;
        Chat.sendChat(player, StringUtil.mm(Lang.get("update.available",
                "version", version, "current", plugin.getDescription().getVersion(), "url", PAGE + "/releases")));
    }
}
