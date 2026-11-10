package me.bbijabnpobatejb.paintcraft.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PacketScene {

    private final Map<Object, FakeDisplay> displays = new LinkedHashMap<>();
    private final Map<UUID, Player> viewers = new HashMap<>();
    private final Map<UUID, Set<Integer>> personalGlow = new HashMap<>();
    private final Map<UUID, Integer> personalGlowColor = new HashMap<>();

    public void beginFrame() {
        for (FakeDisplay display : displays.values()) display.used = false;
    }

    public FakeDisplay block(Object key) {
        return get(key, FakeDisplay.Kind.BLOCK);
    }

    public FakeDisplay text(Object key) {
        return get(key, FakeDisplay.Kind.TEXT);
    }

    public FakeDisplay item(Object key) {
        return get(key, FakeDisplay.Kind.ITEM);
    }

    public FakeDisplay interaction(Object key) {
        return get(key, FakeDisplay.Kind.INTERACTION);
    }

    public @Nullable FakeDisplay find(Object key) {
        return displays.get(key);
    }

    public Map<Object, FakeDisplay> displays() {
        return displays;
    }

    public Collection<Player> viewers() {
        return viewers.values();
    }

    public boolean isEmpty() {
        return displays.isEmpty();
    }

    private FakeDisplay get(Object key, FakeDisplay.Kind kind) {
        FakeDisplay display = displays.get(key);
        if (display == null || display.getKind() != kind) {
            display = new FakeDisplay(kind);
            FakeDisplay previous = displays.put(key, display);
            if (previous != null) orphans.add(previous);
        }
        display.used = true;
        return display;
    }

    private final List<FakeDisplay> orphans = new ArrayList<>();

    public void endFrame() {
        List<PacketWrapper<?>> packets = new ArrayList<>();
        List<Integer> removed = new ArrayList<>();

        for (FakeDisplay orphan : orphans) if (orphan.isSpawned()) removed.add(orphan.getEntityId());
        orphans.clear();

        Iterator<FakeDisplay> iterator = displays.values().iterator();
        while (iterator.hasNext()) {
            FakeDisplay display = iterator.next();
            if (!display.used) {
                if (display.isSpawned()) removed.add(display.getEntityId());
                iterator.remove();
                continue;
            }
            if (display.isSpawned()) display.appendChanges(packets);
            else display.appendSpawn(packets);
            display.markSent();
        }

        if (!removed.isEmpty()) {
            packets.add(new WrapperPlayServerDestroyEntities(removed.stream().mapToInt(Integer::intValue).toArray()));
            for (Set<Integer> glow : personalGlow.values()) removed.forEach(glow::remove);
        }

        if (packets.isEmpty()) return;
        for (Player viewer : viewers.values()) send(viewer, packets);
    }

    public void setViewers(Collection<Player> wanted) {
        Set<UUID> wantedIds = new HashSet<>();
        for (Player player : wanted) {
            wantedIds.add(player.getUniqueId());
            if (viewers.containsKey(player.getUniqueId())) continue;
            viewers.put(player.getUniqueId(), player);
            showAll(player);
        }

        Iterator<Map.Entry<UUID, Player>> iterator = viewers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Player> entry = iterator.next();
            if (wantedIds.contains(entry.getKey())) continue;
            hideAll(entry.getValue());
            personalGlow.remove(entry.getKey());
            personalGlowColor.remove(entry.getKey());
            iterator.remove();
        }
    }

    public void forgetViewer(UUID playerId) {
        viewers.remove(playerId);
        personalGlow.remove(playerId);
        personalGlowColor.remove(playerId);
    }

    public void setPersonalGlow(Player player, Collection<Object> keys, int color) {
        if (!viewers.containsKey(player.getUniqueId())) return;

        Set<Integer> wanted = new HashSet<>();
        Map<Integer, FakeDisplay> byId = new HashMap<>();
        for (Object key : keys) {
            FakeDisplay display = displays.get(key);
            if (display == null || !display.isSpawned() || display.getKind() == FakeDisplay.Kind.INTERACTION) continue;
            wanted.add(display.getEntityId());
            byId.put(display.getEntityId(), display);
        }

        Set<Integer> current = personalGlow.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        Integer previousColor = personalGlowColor.put(player.getUniqueId(), color);
        boolean recolor = previousColor == null || previousColor != color;
        if (current.equals(wanted) && !recolor) return;

        List<PacketWrapper<?>> packets = new ArrayList<>();
        for (FakeDisplay display : displays.values()) {
            int id = display.getEntityId();
            if (current.contains(id) && !wanted.contains(id)) packets.add(display.glowPacket(false, -1));
        }
        for (int id : wanted) {
            if (recolor || !current.contains(id)) packets.add(byId.get(id).glowPacket(true, color));
        }

        current.clear();
        current.addAll(wanted);
        if (current.isEmpty()) {
            personalGlow.remove(player.getUniqueId());
            personalGlowColor.remove(player.getUniqueId());
        }
        if (!packets.isEmpty()) send(player, packets);
    }

    public void destroy() {
        int[] ids = displays.values().stream().filter(FakeDisplay::isSpawned).mapToInt(FakeDisplay::getEntityId).toArray();
        if (ids.length > 0) {
            List<PacketWrapper<?>> packets = List.of(new WrapperPlayServerDestroyEntities(ids));
            for (Player viewer : viewers.values()) send(viewer, packets);
        }
        displays.clear();
        viewers.clear();
        personalGlow.clear();
    }

    private void showAll(Player player) {
        List<PacketWrapper<?>> packets = new ArrayList<>();
        for (FakeDisplay display : displays.values()) {
            if (display.isSpawned()) display.appendSpawn(packets);
        }
        if (!packets.isEmpty()) send(player, packets);
    }

    private void hideAll(Player player) {
        if (!player.isOnline()) return;
        int[] ids = displays.values().stream().filter(FakeDisplay::isSpawned).mapToInt(FakeDisplay::getEntityId).toArray();
        if (ids.length > 0) send(player, List.of(new WrapperPlayServerDestroyEntities(ids)));
    }

    private static void send(Player player, List<? extends PacketWrapper<?>> packets) {
        if (!player.isOnline()) return;
        var manager = PacketEvents.getAPI().getPlayerManager();
        if (packets.size() == 1) {
            manager.sendPacket(player, packets.get(0));
            return;
        }
        manager.writePacket(player, new WrapperPlayServerBundle());
        for (PacketWrapper<?> packet : packets) manager.writePacket(player, packet);
        manager.sendPacket(player, new WrapperPlayServerBundle());
    }
}
