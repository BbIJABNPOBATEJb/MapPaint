package me.bbijabnpobatejb.paintcraft.studio;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public record Widget(double a0, double b0, double a1, double b1, @Nullable String name, @Nullable Consumer<Player> click,
                     boolean swatch) {

    public Widget(double a0, double b0, double a1, double b1, @Nullable String name, @Nullable Consumer<Player> click) {
        this(a0, b0, a1, b1, name, click, false);
    }

    public boolean contains(double a, double b) {
        return a >= a0 && a < a1 && b >= b0 && b < b1;
    }
}
