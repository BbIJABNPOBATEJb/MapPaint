package me.bbijabnpobatejb.paintcraft.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
import me.bbijabnpobatejb.paintcraft.util.MathUtil;
import me.bbijabnpobatejb.paintcraft.util.Sounds;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfigHandler {

    final PaintCraft plugin;

    int maxSize = 5;
    int defaultResolution = 32;
    List<Integer> resolutions = List.of(16, 32, 64, 128);
    double reach = 6.0;

    int maxBrush = 32;
    boolean eraserTransparent = false;

    int maxPaintings = 200;
    int undoSteps = 30;
    boolean onlyPlacerBreaks = true;
    int autosaveSeconds = 20;

    boolean importCode = true;
    boolean imageUrl = true;
    int imageMaxBytes = 8 * 1024 * 1024;
    int imageResolution = 128;
    boolean imageAllowPrivate = false;
    int paperPerBlock = 1;

    boolean updateCheck = true;
    boolean panel = true;
    boolean placementPreview = true;
    boolean glowing = true;
    boolean sharedPainting = true;
    boolean sounds = true;
    int resolutionUndoMinutes = 30;

    boolean brushRecipe = true;
    List<Material> brushIngredients = List.of(Material.STICK, Material.FEATHER);
    boolean canvasRecipe = true;
    List<Material> canvasIngredients = List.of(Material.PAPER, Material.STICK);
    int canvasAmount = 1;

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        val config = plugin.getConfig();

        maxSize = MathUtil.clamp(config.getInt("canvas.max-size", 5), 1, 16);
        List<Integer> allowed = new ArrayList<>();
        for (int res : config.getIntegerList("canvas.resolutions")) {
            if ((res == 16 || res == 32 || res == 64 || res == 128) && !allowed.contains(res)) allowed.add(res);
        }
        if (allowed.isEmpty()) allowed.addAll(List.of(16, 32, 64, 128));
        allowed.sort(null);
        resolutions = List.copyOf(allowed);
        defaultResolution = nearestAllowed(config.getInt("canvas.default-resolution", 32));
        reach = MathUtil.clamp(config.getDouble("canvas.reach", 6.0), 2.0, 32.0);

        maxBrush = MathUtil.clamp(config.getInt("brush.max-size", 32), 1, 64);
        eraserTransparent = config.getBoolean("brush.eraser-transparent", false);

        maxPaintings = Math.max(0, config.getInt("limits.paintings-per-player", 200));
        undoSteps = MathUtil.clamp(config.getInt("limits.undo-steps", 30), 1, 500);
        onlyPlacerBreaks = config.getBoolean("protection.only-placer-breaks", true);
        autosaveSeconds = MathUtil.clamp(config.getInt("autosave-seconds", 20), 5, 600);

        importCode = config.getBoolean("import.code", true);
        imageUrl = config.getBoolean("import.image-url", true);
        imageMaxBytes = MathUtil.clamp(config.getInt("import.image-max-bytes", 8 * 1024 * 1024), 1024, 64 * 1024 * 1024);
        imageResolution = nearestAllowed(config.getInt("import.image-resolution", 128));
        imageAllowPrivate = config.getBoolean("import.image-allow-private-hosts", false);
        paperPerBlock = MathUtil.clamp(config.getInt("import.paper-per-block", 1), 0, 64);

        updateCheck = config.getBoolean("update-check", true);
        panel = config.getBoolean("features.panel", true);
        placementPreview = config.getBoolean("features.placement-preview", true);
        glowing = config.getBoolean("features.glowing", true);
        sharedPainting = config.getBoolean("features.shared-painting", true);
        sounds = config.getBoolean("sounds", true);
        Sounds.setEnabled(sounds);
        resolutionUndoMinutes = MathUtil.clamp(config.getInt("canvas.resolution-undo-minutes", 30), 0, 24 * 60);

        brushRecipe = config.getBoolean("recipes.brush.enabled", true);
        brushIngredients = materials("recipes.brush.ingredients", List.of(Material.STICK, Material.FEATHER));
        canvasRecipe = config.getBoolean("recipes.canvas.enabled", true);
        canvasIngredients = materials("recipes.canvas.ingredients", List.of(Material.PAPER, Material.STICK));
        canvasAmount = MathUtil.clamp(config.getInt("recipes.canvas.amount", 1), 1, 64);
    }

    public byte getEraserColor() {
        return eraserTransparent ? Palette.TRANSPARENT : Palette.WHITE;
    }

    int nearestAllowed(int wanted) {
        int best = resolutions.get(0);
        for (int res : resolutions) {
            if (Math.abs(res - wanted) < Math.abs(best - wanted)) best = res;
        }
        return best;
    }

    List<Material> materials(String path, List<Material> fallback) {
        List<Material> result = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList(path)) {
            Material material = Material.matchMaterial(name);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("config.yml: unknown item in recipe: " + name);
            } else {
                result.add(material);
            }
        }
        return result.isEmpty() || result.size() > 9 ? fallback : List.copyOf(result);
    }
}
