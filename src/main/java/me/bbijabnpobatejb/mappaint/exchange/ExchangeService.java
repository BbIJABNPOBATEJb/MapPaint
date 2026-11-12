package me.bbijabnpobatejb.mappaint.exchange;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.item.PaintItem;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.paint.PaintService;
import me.bbijabnpobatejb.mappaint.store.Codec;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.Sounds;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.awt.image.BufferedImage;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExchangeService {

    MapPaint plugin;

    public void export(Player player, Painting painting) {
        if (!player.hasPermission(MapPaint.PERMISSION_EXPORT)) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        String code = Codec.encode(painting.blocksW, painting.blocksH, painting.res, plugin.getStorage().pixels(painting));
        plugin.getPrompts().export(player, painting, Codec.split(code, plugin.getPrompts().partLimit()), 0);
    }

    public void importText(Player player, String text) {
        if (!player.hasPermission(MapPaint.PERMISSION_IMPORT) || !plugin.getConfigHandler().isImportCode()) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        val session = plugin.getPaintService().session(player);
        val part = Codec.parsePart(text);
        if (part != null) {
            String[] parts = session.getImportParts();
            if (parts == null || parts.length != part.total()) parts = new String[part.total()];
            parts[part.number() - 1] = part.data();
            session.setImportParts(parts);

            int missing = -1, have = 0;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null) have++;
                else if (missing < 0) missing = i;
            }
            if (missing >= 0) {
                plugin.getPrompts().importCode(player, Lang.get("import.parts",
                        "have", have, "total", part.total(), "next", missing + 1));
                return;
            }
            text = Codec.PREFIX + String.join("", parts);
            session.setImportParts(null);
        }

        Codec.Image image;
        try {
            image = Codec.decode(text, plugin.getConfigHandler().getMaxSize());
        } catch (Codec.FormatException e) {
            int max = plugin.getConfigHandler().getMaxSize();
            Chat.sendChat(player, Chat.red(e.getMessage().equals("size")
                    ? Lang.get("import.too-big", "max", max + "×" + max)
                    : Lang.get("import.bad-code")));
            Sounds.playSound(player, Sounds.ERROR);
            return;
        }
        apply(player, image.blocksW(), image.blocksH(), image.res(), false,
                (w, h) -> PaintService.resample(image.pixels(), image.width(), image.height(), w, h));
    }

    public void importImage(Player player, ImageOptions options) {
        val config = plugin.getConfigHandler();
        if (!player.hasPermission(MapPaint.PERMISSION_IMAGE) || !config.isImageUrl()) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        val session = plugin.getPaintService().session(player);
        if (session.isImageLoading()) {
            Chat.sendActionBar(player, Chat.gray(Lang.get("import.loading")));
            return;
        }
        session.setImageOptions(options);

        int res = config.getImageResolution();
        int width, height;
        val hit = plugin.getPlacementManager().traceVisible(player, config.getReach());
        ItemStack hand = player.getInventory().getItemInMainHand();
        val items = plugin.getItemService();
        if (hit != null) {
            width = hit.placement().painting.width();
            height = hit.placement().painting.height();
        } else if (items.typeOf(hand) == PaintItem.CANVAS) {
            width = items.widthOf(hand) * res;
            height = items.heightOf(hand) * res;
        } else {
            width = options.blocksW() * res;
            height = options.blocksH() * res;
        }

        float shade = options.blockShade() && hit != null ? ImageOptions.faceShade(hit.placement().face) : 1f;

        String url = options.url().trim();
        BufferedImage cached = url.equals(session.getImageCacheUrl()) ? session.getImageCache() : null;
        if (cached == null) Chat.sendActionBar(player, Chat.gray(Lang.get("import.loading")));
        int maxBytes = config.getImageMaxBytes();
        boolean allowPrivate = config.isImageAllowPrivate();
        session.setImageLoading(true);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            BufferedImage image;
            byte[] pixels;
            try {
                image = cached != null ? cached : ImageImporter.fetch(url, maxBytes, allowPrivate);
                pixels = ImageImporter.convert(image, width, height, options, shade);
            } catch (Exception | OutOfMemoryError e) {
                String reason = String.valueOf(e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.setImageLoading(false);
                    Chat.sendChat(player, Chat.red(Lang.get("import.image-failed", "reason", reason)));
                    Sounds.playSound(player, Sounds.ERROR);
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.setImageLoading(false);
                if (!player.isOnline()) return;
                session.setImageCache(image);
                session.setImageCacheUrl(url);
                apply(player, options.blocksW(), options.blocksH(), res, true,
                        (w, h) -> PaintService.resample(pixels, width, height, w, h));
            });
        });
    }

    private interface Source {
        byte[] pixels(int width, int height);
    }

    private void apply(Player player, int blocksW, int blocksH, int res, boolean image, Source source) {
        val paint = plugin.getPaintService();
        val items = plugin.getItemService();
        val session = paint.session(player);

        val hit = plugin.getPlacementManager().traceVisible(player, plugin.getConfigHandler().getReach());
        if (hit != null) {
            Painting painting = hit.placement().painting;
            if (!paint.canEdit(player, painting)) {
                paint.sendCannotEdit(player, painting);
                return;
            }
            boolean retry = image && session.getImagePainting() == painting && session.getImageStep() != null
                    && paint.lastStep(painting) == session.getImageStep();
            if (!retry && !payPaper(player, painting.blocksW * painting.blocksH)) return;
            if (retry) paint.undo(painting, false);

            val step = paint.replaceImported(painting, source.pixels(painting.width(), painting.height()));
            if (painting.undo != null) painting.undo.dropRedo();
            session.setImageStep(image ? step : null);
            session.setImagePainting(image ? painting : null);
            session.setLastPainting(painting);
            Chat.sendChat(player, StringUtil.mm(image
                    ? Lang.get("import.image-done")
                    : Lang.get("import.code-done")));
            Sounds.playSound(player, Sounds.POP);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean canvas = items.typeOf(hand) == PaintItem.CANVAS;
        if (canvas) {
            blocksW = items.widthOf(hand);
            blocksH = items.heightOf(hand);
        } else if (!isFree(player)) {
            Chat.sendChat(player, Chat.red(Lang.get("import.no-target", "size", blocksW + "×" + blocksH)));
            Sounds.playSound(player, Sounds.ERROR);
            return;
        }
        if (isOverLimit(player)) return;
        if (!payPaper(player, blocksW * blocksH)) return;

        Painting painting = plugin.getStorage().create(player.getUniqueId(), player.getName(), blocksW, blocksH, res);
        painting.pixels = source.pixels(painting.width(), painting.height());
        painting.imported = true;
        painting.markChanged();
        if (canvas && player.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        items.give(player, items.createPainting(painting, 1));
        Chat.sendChat(player, Chat.green(Lang.get("import.created", "size", blocksW + "×" + blocksH)));
        Sounds.playSound(player, Sounds.POP);
    }

    private boolean payPaper(Player player, int blocks) {
        int cost = blocks * plugin.getConfigHandler().getPaperPerBlock();
        if (cost <= 0 || isFree(player)) return true;
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.PAPER), cost)) {
            Chat.sendChat(player, Chat.red(Lang.get("import.paper", "sheets", Lang.plural(cost, "unit.sheet"),
                    "price", plugin.getConfigHandler().getPaperPerBlock())));
            Sounds.playSound(player, Sounds.ERROR);
            return false;
        }
        player.getInventory().removeItem(new ItemStack(Material.PAPER, cost));
        return true;
    }

    public boolean isFree(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.hasPermission(MapPaint.PERMISSION_ADMIN);
    }

    public boolean isOverLimit(Player player) {
        int limit = plugin.getConfigHandler().getMaxPaintings();
        if (limit <= 0 || player.hasPermission(MapPaint.PERMISSION_ADMIN)) return false;
        if (plugin.getStorage().countOwnedBy(player.getUniqueId()) < limit) return false;

        Chat.sendChat(player, Chat.red(Lang.get("import.limit", "paintings", Lang.plural(limit, "unit.painting"))));
        Sounds.playSound(player, Sounds.ERROR);
        return true;
    }

    public boolean takeCopy(Player player, Painting painting) {
        if (!isFree(player) && !takeCanvas(player, painting.blocksW, painting.blocksH)) {
            Chat.sendActionBar(player, Chat.red(Lang.get("gallery.need-canvas", "size", painting.blocksW + "×" + painting.blocksH)));
            Sounds.playSound(player, Sounds.ERROR);
            return false;
        }
        plugin.getItemService().give(player, plugin.getItemService().createPainting(painting, 1));
        Sounds.playSound(player, Sounds.POP);
        return true;
    }

    private boolean takeCanvas(Player player, int blocksW, int blocksH) {
        val items = plugin.getItemService();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (items.typeOf(item) != PaintItem.CANVAS) continue;
            if (items.widthOf(item) != blocksW || items.heightOf(item) != blocksH) continue;
            item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    public void delete(Painting painting) {
        plugin.getPaintService().finishStrokes(painting);
        plugin.getPlacementManager().removeAll(painting);
        plugin.getMapBridge().detach(painting);
        plugin.getStorage().delete(painting);
    }

    public void sign(Player player, Painting painting, String title) {
        boolean owner = painting.owner.equals(player.getUniqueId()) || player.hasPermission(MapPaint.PERMISSION_ADMIN);
        if (painting.signed || !owner) {
            plugin.getPaintService().sendCannotEdit(player, painting);
            return;
        }
        title = title.strip();
        if (title.length() > 32) title = title.substring(0, 32);

        plugin.getPaintService().finishStrokes(painting);
        painting.title = title;
        painting.signed = true;
        painting.shared = false;
        painting.undo = null;
        painting.resSource = null;
        painting.markChanged();
        plugin.getStudioService().refresh(painting);

        val items = plugin.getItemService();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (items.typeOf(contents[i]) != PaintItem.PAINTING || !painting.id.equals(items.paintingIdOf(contents[i]))) continue;
            player.getInventory().setItem(i, items.createPainting(painting, contents[i].getAmount()));
        }
        Chat.sendChat(player, StringUtil.mm(Lang.get("sign.done", "title", StringUtil.escape(painting.getDisplayTitle()))));
        Sounds.playSound(player, Sounds.BELL);
    }
}
