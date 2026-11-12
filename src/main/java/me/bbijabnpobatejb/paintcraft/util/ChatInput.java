package me.bbijabnpobatejb.paintcraft.util;

import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatInput implements Listener {

    Plugin plugin;
    Map<UUID, Consumer<String>> waiting = new ConcurrentHashMap<>();

    public void ask(Player player, String prompt, Consumer<String> answer) {
        waiting.put(player.getUniqueId(), answer);
        player.closeInventory();
        Chat.sendChat(player, StringUtil.mm(Lang.get("input.prompt", "prompt", prompt, "cancel", Lang.get("input.cancel-word"))));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> answer = waiting.remove(player.getUniqueId());
        if (answer == null) return;
        event.setCancelled(true);

        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (text.equalsIgnoreCase(Lang.get("input.cancel-word")) || text.equalsIgnoreCase("cancel")) {
                Chat.sendChat(player, Chat.gray(Lang.get("input.cancelled")));
                return;
            }
            answer.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waiting.remove(event.getPlayer().getUniqueId());
    }
}
