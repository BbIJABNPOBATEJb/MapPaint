package me.bbijabnpobatejb.paintcraft.compat;

import me.bbijabnpobatejb.paintcraft.model.Painting;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Prompts {

    int partLimit();

    void export(Player player, Painting painting, List<String> parts, int index);

    void importCode(Player player, @Nullable String hint);

    void sign(Player player, Painting painting);

    void hex(Player player);

    void canvas(Player player);

    void image(Player player, @Nullable String url);
}
