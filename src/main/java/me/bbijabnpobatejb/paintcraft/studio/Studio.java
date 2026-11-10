package me.bbijabnpobatejb.paintcraft.studio;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.packet.PacketScene;
import me.bbijabnpobatejb.paintcraft.world.Placement;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class Studio {

    final Player player;
    final Placement placement;
    final PacketScene scene = new PacketScene();
    final List<Widget> widgets = new ArrayList<>();

    @Nullable Widget hovered;
    final Map<StudioService.Pane, double[]> panes = new EnumMap<>(StudioService.Pane.class);
    StudioService.@Nullable Pane dragging;
    double[] dragPosition;
    double grabA, grabB;
    boolean dragHeld;
    int dragTicks;
    @Nullable Placement lookTarget;
    int lookChecks;
    int age;
    int closingTicks = -1;
    int lastClickTick = -100;
    int lastHintTick = -100;
}
