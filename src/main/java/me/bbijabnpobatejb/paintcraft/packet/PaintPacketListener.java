package me.bbijabnpobatejb.paintcraft.packet;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PaintPacketListener implements PacketListener {

    SlotTracker slots;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();
        if (type == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            slots.onSlotPacket(event.getUser().getUUID(), new WrapperPlayClientHeldItemChange(event).getSlot());
        } else if (type == PacketType.Play.Client.PONG) {
            slots.onPong(event.getUser().getUUID(), new WrapperPlayClientPong(event).getId());
        }
    }
}
