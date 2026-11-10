package me.bbijabnpobatejb.paintcraft.paint;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import org.bukkit.Material;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum Tool {
    BRUSH(Material.BRUSH),
    ERASER(Material.SPONGE),
    FILL(Material.BUCKET),
    SPRAY(Material.DRAGON_BREATH),
    LINE(Material.STICK),
    RECT(Material.ITEM_FRAME),
    ELLIPSE(Material.SNOWBALL);

    private final Material icon;

    public String getDisplayName() {
        return Lang.get("tool." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    public String getHint() {
        return Lang.get("tool." + name().toLowerCase(Locale.ROOT) + ".hint");
    }

    public boolean isShape() {
        return this == LINE || this == RECT || this == ELLIPSE;
    }
}
