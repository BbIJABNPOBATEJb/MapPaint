package me.bbijabnpobatejb.mappaint.util;

import lombok.Setter;
import lombok.experimental.UtilityClass;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

@UtilityClass
public class Sounds {

    public final Sound BELL = Sound.BLOCK_NOTE_BLOCK_BELL;
    public final Sound HAT = Sound.BLOCK_NOTE_BLOCK_HAT;
    public final Sound ERROR = Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO;
    public final Sound POP = Sound.ENTITY_ITEM_PICKUP;

    @Setter
    private boolean enabled = true;

    public void playSound(CommandSender sender, Sound sound) {
        playSound(sender, sound, 1f, 1f);
    }

    public void playSound(CommandSender sender, Sound sound, float volume, float pitch) {
        if (enabled && sender instanceof Player player) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    public void playWorld(World world, Vector position, Sound sound, float volume, float pitch) {
        world.playSound(position.toLocation(world), sound, volume, pitch);
    }
}
