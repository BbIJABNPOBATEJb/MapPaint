package me.bbijabnpobatejb.paintcraft.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@FieldDefaults(level = AccessLevel.PRIVATE)
public final class SlotTracker {

    private static final int PING_MARK = 0x50430000;
    private static final int PONG = -1;

    private static final class State {
        final ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<>();
        int clientSlot = -1;
        boolean pingScheduled;
    }

    final Plugin plugin;
    final Map<UUID, State> states = new ConcurrentHashMap<>();
    int pingCounter;

    public SlotTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    void onSlotPacket(UUID player, int slot) {
        states.computeIfAbsent(player, id -> new State()).queue.add(slot);
    }

    void onPong(UUID player, int id) {
        if ((id & 0xFFFF0000) != PING_MARK) return;
        State state = states.get(player);
        if (state != null) state.queue.add(PONG);
    }

    public int clientSlotBefore(Player player, int newSlot, int serverSlot) {
        State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
        boolean found = false;
        Integer entry;
        while ((entry = state.queue.poll()) != null) {
            if (entry == PONG) {
                state.clientSlot = serverSlot;
            } else if (entry == newSlot) {
                found = true;
                break;
            }
        }
        int before = found && state.clientSlot >= 0 ? state.clientSlot : serverSlot;
        state.clientSlot = newSlot;
        return before;
    }

    public void confirmCorrection(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
        if (state.pingScheduled) return;
        state.pingScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            state.pingScheduled = false;
            if (!player.isOnline()) return;
            int id = PING_MARK | (pingCounter++ & 0xFFFF);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(id));
        });
    }

    public void forget(UUID player) {
        states.remove(player);
    }
}
