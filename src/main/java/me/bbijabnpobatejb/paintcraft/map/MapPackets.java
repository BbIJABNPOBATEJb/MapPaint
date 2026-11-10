package me.bbijabnpobatejb.paintcraft.map;

import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Optional;

public final class MapPackets {

    public static final boolean AVAILABLE;
    public static final String FAILURE;

    private static final MethodHandle NEW_MAP_ID;
    private static final MethodHandle NEW_PATCH;
    private static final MethodHandle NEW_PACKET;
    private static final MethodHandle GET_HANDLE;
    private static final MethodHandle GET_CONNECTION;
    private static final MethodHandle SEND;

    static {
        MethodHandle newMapId = null, newPatch = null, newPacket = null, getHandle = null, getConnection = null, send = null;
        String failure = null;
        try {
            Class<?> mapId = Class.forName("net.minecraft.world.level.saveddata.maps.MapId");
            Class<?> patch = Class.forName("net.minecraft.world.level.saveddata.maps.MapItemSavedData$MapPatch");
            Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundMapItemDataPacket");
            Class<?> anyPacket = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            newMapId = lookup.findConstructor(mapId, MethodType.methodType(void.class, int.class));
            newPatch = lookup.findConstructor(patch,
                    MethodType.methodType(void.class, int.class, int.class, int.class, int.class, byte[].class));
            newPacket = lookup.findConstructor(packet,
                    MethodType.methodType(void.class, mapId, byte.class, boolean.class, Optional.class, Optional.class));
            getHandle = lookup.findVirtual(craftPlayer, "getHandle", MethodType.methodType(serverPlayer));
            Field connection = serverPlayer.getField("connection");
            getConnection = lookup.unreflectGetter(connection);
            send = lookup.findVirtual(connection.getType(), "send", MethodType.methodType(void.class, anyPacket));
        } catch (Throwable t) {
            failure = t.toString();
        }
        NEW_MAP_ID = newMapId;
        NEW_PATCH = newPatch;
        NEW_PACKET = newPacket;
        GET_HANDLE = getHandle;
        GET_CONNECTION = getConnection;
        SEND = send;
        FAILURE = failure;
        AVAILABLE = failure == null;
    }

    private MapPackets() {
    }

    public static Object patch(int mapId, int x, int y, int width, int height, byte[] colors) throws Throwable {
        Object patch = NEW_PATCH.invoke(x, y, width, height, colors);
        return NEW_PACKET.invoke(NEW_MAP_ID.invoke(mapId), (byte) 0, true, Optional.empty(), Optional.of(patch));
    }

    public static void send(Player player, Object packet) throws Throwable {
        Object connection = GET_CONNECTION.invoke(GET_HANDLE.invoke(player));
        if (connection != null) {
            SEND.invoke(connection, packet);
        }
    }
}
