package me.bbijabnpobatejb.paintcraft.palette;

import lombok.experimental.UtilityClass;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.map.MapPalette;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings({"deprecation", "removal"})
@UtilityClass
public class Palette {

    public static final byte TRANSPARENT = 0;

    private static final int[] SHADES_DARK_TO_LIGHT = {3, 0, 1, 2};

    private static final int[] RGB = new int[256];
    private static final boolean[] VALID = new boolean[256];
    private static final int BASE_COUNT;
    private static final List<Integer> BASES_SORTED;

    private static final float[][] LAB = new float[256][];
    private static final byte[] NEAREST = new byte[1 << 18];
    private static final boolean[] NEAREST_KNOWN = new boolean[1 << 18];

    public static final byte WHITE;
    public static final byte BLACK;

    static {
        int lastValid = 3;
        for (int i = 4; i < 256; i++) {
            try {
                Color c = MapPalette.getColor((byte) i);
                if (c.getAlpha() < 255) {
                    continue;
                }
                RGB[i] = c.getRGB() & 0xFFFFFF;
                LAB[i] = oklab(RGB[i]);
                VALID[i] = true;
                lastValid = i;
            } catch (RuntimeException ignored) {
            }
        }
        BASE_COUNT = (lastValid + 1) / 4;

        List<Integer> bases = new ArrayList<>();
        for (int base = 1; base < BASE_COUNT; base++) {
            if (VALID[base * 4 + 2]) {
                bases.add(base);
            }
        }
        bases.sort(Comparator.comparingDouble(Palette::sortKey));
        BASES_SORTED = List.copyOf(bases);

        WHITE = nearestSlow(0xFFFFFF);
        int black = -1;
        for (int i = 4; i < 256; i++) {
            int c = RGB[i];
            boolean grey = (c >> 16) == ((c >> 8) & 255) && (c >> 16) == (c & 255);
            if (VALID[i] && grey && (black < 0 || c < RGB[black])) {
                black = i;
            }
        }
        BLACK = black < 0 ? nearestSlow(0x000000) : (byte) black;
    }

    private static double sortKey(int base) {
        int rgb = RGB[base * 4 + 2];
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        if (hsb[1] < 0.13f) {
            return -1.0 - hsb[2];
        }
        return Math.floor(hsb[0] * 12) * 10 + (1 - hsb[2]) * 5 + (1 - hsb[1]);
    }

    public static boolean isValid(byte color) {
        return VALID[color & 0xFF];
    }

    public static boolean isTransparent(byte color) {
        return (color & 0xFF) < 4;
    }

    public static int rgb(byte color) {
        return isTransparent(color) ? 0xFFFFFF : RGB[color & 0xFF];
    }

    public static String hex(byte color) {
        return String.format("#%06X", rgb(color));
    }

    public static Component chip(byte color) {
        if (isTransparent(color)) {
            return Component.text(Lang.get("color.transparent"), TextColor.color(0x1FFFE3));
        }
        return Component.text("⬛ " + hex(color), TextColor.color(rgb(color)));
    }

    public static float hueOf(byte color, float fallback) {
        if (isTransparent(color)) return fallback;
        int rgb = rgb(color);
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        return hsb[1] < 0.12f ? fallback : hsb[0];
    }

    public static byte fromHsb(float hue, float saturation, float brightness) {
        return nearest(Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
    }

    public static List<Integer> bases() {
        return BASES_SORTED;
    }

    public static int baseOf(byte color) {
        return (color & 0xFF) / 4;
    }

    public static byte shade(int base, int level) {
        return (byte) (base * 4 + SHADES_DARK_TO_LIGHT[level]);
    }

    public static int levelOf(byte color) {
        int raw = (color & 0xFF) % 4;
        for (int level = 0; level < 4; level++) {
            if (SHADES_DARK_TO_LIGHT[level] == raw) {
                return level;
            }
        }
        return 3;
    }

    public static byte nearest(int rgb) {
        int key = ((rgb >> 18) & 63) << 12 | ((rgb >> 10) & 63) << 6 | ((rgb >> 2) & 63);
        if (!NEAREST_KNOWN[key]) {
            NEAREST[key] = nearestSlow(rgb);
            NEAREST_KNOWN[key] = true;
        }
        return NEAREST[key];
    }

    private static final float HUE_WEIGHT = 4f;

    public static byte nearestSlow(int rgb) {
        float[] lab = oklab(rgb);
        int best = 4;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 4; i < 256; i++) {
            if (!VALID[i]) {
                continue;
            }
            float dl = lab[0] - LAB[i][0], da = lab[1] - LAB[i][1], db = lab[2] - LAB[i][2];
            float distance = dl * dl + HUE_WEIGHT * (da * da + db * db);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return (byte) best;
    }

    private static float[] oklab(int rgb) {
        double r = linear((rgb >> 16) & 255), g = linear((rgb >> 8) & 255), b = linear(rgb & 255);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new float[]{
                (float) (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s),
                (float) (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s),
                (float) (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)};
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static int parseHex(String text) {
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
