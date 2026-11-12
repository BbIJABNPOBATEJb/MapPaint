package me.bbijabnpobatejb.mappaint.paint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import org.bukkit.Material;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum Tool {
    BRUSH(Material.BRUSH),
    ERASER(Material.SPONGE),
    FILL(Material.BUCKET),
    SPRAY(Material.DRAGON_BREATH),
    LINE(Material.STICK),
    RECT(Material.ITEM_FRAME),
    ELLIPSE(Material.SNOWBALL);

    Material icon;

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
