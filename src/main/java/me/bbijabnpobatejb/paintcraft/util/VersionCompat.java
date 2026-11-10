package me.bbijabnpobatejb.paintcraft.util;

import lombok.experimental.UtilityClass;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

import java.lang.reflect.Constructor;

@UtilityClass
public class VersionCompat {

    private final Constructor<HangingBreakByEntityEvent> WITH_DAMAGE_SOURCE = findConstructor(
            Hanging.class, Entity.class, DamageSource.class, HangingBreakEvent.RemoveCause.class);
    private final Constructor<HangingBreakByEntityEvent> WITHOUT_DAMAGE_SOURCE = findConstructor(
            Hanging.class, Entity.class, HangingBreakEvent.RemoveCause.class);

    public HangingBreakByEntityEvent createHangingBreak(Hanging hanging, Player player) {
        try {
            if (WITH_DAMAGE_SOURCE != null) {
                DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK)
                        .withCausingEntity(player).withDirectEntity(player).build();
                return WITH_DAMAGE_SOURCE.newInstance(hanging, player, source, HangingBreakEvent.RemoveCause.ENTITY);
            }
            if (WITHOUT_DAMAGE_SOURCE != null) {
                return WITHOUT_DAMAGE_SOURCE.newInstance(hanging, player, HangingBreakEvent.RemoveCause.ENTITY);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't create the hanging break event", e);
        }
        throw new IllegalStateException("No suitable HangingBreakByEntityEvent constructor on this server version");
    }

    private Constructor<HangingBreakByEntityEvent> findConstructor(Class<?>... parameters) {
        try {
            return HangingBreakByEntityEvent.class.getConstructor(parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
