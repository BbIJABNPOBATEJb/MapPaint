package me.bbijabnpobatejb.mappaint.exchange;

import me.bbijabnpobatejb.mappaint.i18n.Lang;
import org.bukkit.block.BlockFace;

import java.util.Locale;

public record ImageOptions(String url, int blocksW, int blocksH, Fit fit, float zoom, float offsetX, float offsetY,
                           boolean dither, boolean clearFill, float brightness, float contrast, float saturation,
                           boolean blockShade) {

    public enum Fit {
        COVER,
        CONTAIN,
        STRETCH;

        public String getDisplayName() {
            return Lang.get("dialog.image.fit." + name().toLowerCase(Locale.ROOT));
        }
    }

    public static float faceShade(BlockFace face) {
        return switch (face) {
            case UP -> 1f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
    }

    public static ImageOptions defaults(String url, int blocksW, int blocksH) {
        return new ImageOptions(url, blocksW, blocksH, Fit.COVER, 1f, 0f, 0f, true, false, 0f, 0f, 0f, false);
    }
}
