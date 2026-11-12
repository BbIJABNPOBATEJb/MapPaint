package me.bbijabnpobatejb.paintcraft.modern;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.paintcraft.PaintCraft;
import me.bbijabnpobatejb.paintcraft.compat.Prompts;
import me.bbijabnpobatejb.paintcraft.exchange.ImageOptions;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.model.Painting;
import me.bbijabnpobatejb.paintcraft.palette.Palette;
import me.bbijabnpobatejb.paintcraft.ui.menu.PaletteGui;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor
@SuppressWarnings("UnstableApiUsage")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DialogPrompts implements Prompts {

    private static final int PART_LIMIT = 12_000;
    private static final int CANVAS_GRID = 8;

    PaintCraft plugin;

    @Override
    public int partLimit() {
        return PART_LIMIT;
    }

    @Override
    public void export(Player player, Painting painting, List<String> parts, int index) {
        String code = parts.get(index);
        boolean multi = parts.size() > 1;
        String text = multi
                ? Lang.get("dialog.export.text-parts", "title", painting.getDisplayTitle(),
                "part", index + 1, "parts", parts.size(), "length", code.length())
                : Lang.get("dialog.export.text", "title", painting.getDisplayTitle(), "length", code.length());

        List<DialogInput> inputs = new ArrayList<>();
        if (code.length() <= 4000) {
            inputs.add(DialogInput.text("code", Component.text(Lang.get("dialog.code")))
                    .width(320)
                    .initial(code)
                    .maxLength(code.length())
                    .multiline(TextDialogInput.MultilineOptions.create(null, 90))
                    .build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.builder(Component.text(Lang.get("dialog.export.copy")))
                .tooltip(Component.text(Lang.get("dialog.export.copy-tooltip")))
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.copyToClipboard(code)))
                .build());
        if (multi && index + 1 < parts.size()) {
            buttons.add(button(Lang.get("dialog.export.next"), p -> export(p, painting, parts, index + 1)));
        } else if (multi) {
            buttons.add(button(Lang.get("dialog.export.first"), p -> export(p, painting, parts, 0)));
        }

        show(player, base(Lang.get("dialog.export.title"), text, inputs, DialogBase.DialogAfterAction.NONE), buttons, 2);
    }

    @Override
    public void importCode(Player player, @Nullable String hint) {
        if (!player.hasPermission(PaintCraft.PERMISSION_IMPORT) || !plugin.getConfigHandler().isImportCode()) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        DialogInput input = DialogInput.text("code", Component.text(Lang.get("dialog.code")))
                .width(320)
                .maxLength(PART_LIMIT + 64)
                .multiline(TextDialogInput.MultilineOptions.create(null, 90))
                .build();
        String text = hint != null ? hint : Lang.get("dialog.import.text");
        show(player, base(Lang.get("dialog.import.title"), text, List.of(input), DialogBase.DialogAfterAction.CLOSE),
                List.of(button(Lang.get("dialog.import.button"), (p, view) -> {
                    String code = view.getText("code");
                    if (code != null && !code.isBlank()) plugin.getExchangeService().importText(p, code);
                })), 1);
    }

    @Override
    public void sign(Player player, Painting painting) {
        DialogInput input = DialogInput.text("title", Component.text(Lang.get("dialog.sign.input")))
                .width(260)
                .initial(painting.title)
                .maxLength(32)
                .build();
        show(player, base(Lang.get("dialog.sign.title"), Lang.get("dialog.sign.text"), List.of(input),
                        DialogBase.DialogAfterAction.CLOSE),
                List.of(button(Lang.get("dialog.sign.button"), (p, view) -> {
                    String title = view.getText("title");
                    plugin.getExchangeService().sign(p, painting, title == null ? "" : title);
                })), 1);
    }

    @Override
    public void hex(Player player) {
        DialogInput input = DialogInput.text("hex", Component.text(Lang.get("dialog.hex.input")))
                .width(160)
                .initial(Palette.hex(plugin.getPaintService().session(player).getColor()))
                .maxLength(7)
                .build();
        show(player, base(Lang.get("dialog.hex.title"), Lang.get("dialog.hex.text"), List.of(input),
                        DialogBase.DialogAfterAction.CLOSE),
                List.of(button(Lang.get("dialog.hex.button"), (p, view) -> {
                    String value = view.getText("hex");
                    int rgb = value == null ? -1 : Palette.parseHex(value);
                    if (rgb < 0) Chat.sendChat(p, Chat.red(Lang.get("common.color-format")));
                    else plugin.getPaintService().setColor(p, Palette.nearest(rgb));
                    new PaletteGui(plugin, p).onOpen(p);
                })), 1);
    }

    @Override
    public void canvas(Player player) {
        int have = plugin.getRecipeService().unitCanvases(player);
        if (have == 0) return;
        boolean free = player.getGameMode() == GameMode.CREATIVE;
        int max = Math.min(plugin.getConfigHandler().getMaxSize(), CANVAS_GRID);

        List<ActionButton> buttons = new ArrayList<>();
        for (int height = 1; height <= max; height++) {
            for (int width = 1; width <= max; width++) {
                int w = width, h = height, need = width * height;
                String size = width + "×" + height;
                String canvases = Lang.plural(need, "unit.canvas");
                if (need == 1) {
                    buttons.add(cell(size, Chat.DARK_GRAY, Lang.get("dialog.canvas.same"), p -> canvas(p)));
                } else if (free || have >= need) {
                    buttons.add(cell(size, Chat.WHITE, Lang.get("dialog.canvas.assemble", "size", size, "canvases", canvases),
                            p -> plugin.getRecipeService().assemble(p, w, h)));
                } else {
                    buttons.add(cell(size, Chat.DARK_GRAY, Lang.get("dialog.canvas.need", "canvases", canvases, "have", have),
                            p -> plugin.getRecipeService().assemble(p, w, h)));
                }
            }
        }
        String text = (free ? Lang.get("dialog.canvas.creative")
                : Lang.get("dialog.canvas.have", "canvases", Lang.plural(have, "unit.canvas")))
                + " " + Lang.get("dialog.canvas.text");
        show(player, base(Lang.get("dialog.canvas.title"), text, List.of(), DialogBase.DialogAfterAction.CLOSE), buttons, max);
    }

    private ActionButton cell(String label, int color, String tooltip, Consumer<Player> action) {
        return ActionButton.builder(Chat.colorText(label, color))
                .tooltip(Component.text(tooltip))
                .width(36)
                .action(click((player, view) -> action.accept(player)))
                .build();
    }

    @Override
    public void image(Player player, @Nullable String url) {
        if (!player.hasPermission(PaintCraft.PERMISSION_IMAGE) || !plugin.getConfigHandler().isImageUrl()) {
            Chat.sendChat(player, Chat.red(Lang.get("common.no-permission")));
            return;
        }
        ImageOptions saved = plugin.getPaintService().session(player).getImageOptions();
        ImageOptions last = saved != null ? saved : ImageOptions.defaults("", 1, 1);
        int max = plugin.getConfigHandler().getMaxSize();

        List<SingleOptionDialogInput.OptionEntry> fits = new ArrayList<>();
        for (ImageOptions.Fit fit : ImageOptions.Fit.values()) {
            fits.add(SingleOptionDialogInput.OptionEntry.create(fit.name(), Component.text(fit.getDisplayName()), fit == last.fit()));
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.text("url", Component.text(Lang.get("dialog.image.url")))
                .width(320)
                .initial(url != null ? url : last.url())
                .maxLength(1024)
                .build());
        inputs.add(DialogInput.singleOption("fit", Component.text(Lang.get("dialog.image.fit.label")), fits).width(320).build());
        inputs.add(slider("zoom", Lang.get("dialog.image.zoom"), 100, 400, 5, last.zoom() * 100));
        inputs.add(slider("x", Lang.get("dialog.image.x"), -100, 100, 5, last.offsetX() * 100));
        inputs.add(slider("y", Lang.get("dialog.image.y"), -100, 100, 5, last.offsetY() * 100));
        inputs.add(slider("brightness", Lang.get("dialog.image.brightness"), -100, 100, 5, last.brightness() * 100));
        inputs.add(slider("contrast", Lang.get("dialog.image.contrast"), -100, 100, 5, last.contrast() * 100));
        inputs.add(slider("saturation", Lang.get("dialog.image.saturation"), -100, 100, 5, last.saturation() * 100));
        inputs.add(DialogInput.bool("dither", Component.text(Lang.get("dialog.image.dither")))
                .initial(last.dither()).build());
        inputs.add(DialogInput.bool("shade", Component.text(Lang.get("dialog.image.shade")))
                .initial(last.blockShade()).build());
        inputs.add(DialogInput.bool("clear", Component.text(Lang.get("dialog.image.clear")))
                .initial(last.clearFill()).build());
        inputs.add(slider("w", Lang.get("dialog.image.width"), 1, max, 1, Math.clamp(last.blocksW(), 1, max)));
        inputs.add(slider("h", Lang.get("dialog.image.height"), 1, max, 1, Math.clamp(last.blocksH(), 1, max)));

        show(player, base(Lang.get("dialog.image.title"), Lang.get("dialog.image.text"), inputs,
                        DialogBase.DialogAfterAction.CLOSE),
                List.of(button(Lang.get("dialog.image.button"), (p, view) -> {
                    String link = view.getText("url");
                    if (link == null || link.isBlank()) {
                        Chat.sendChat(p, Chat.red(Lang.get("dialog.image.no-url")));
                        return;
                    }
                    ImageOptions.Fit fit;
                    try {
                        fit = ImageOptions.Fit.valueOf(String.valueOf(view.getText("fit")));
                    } catch (IllegalArgumentException e) {
                        fit = ImageOptions.Fit.COVER;
                    }
                    plugin.getExchangeService().importImage(p, new ImageOptions(link.trim(),
                            Math.round(number(view, "w", 1)), Math.round(number(view, "h", 1)), fit,
                            number(view, "zoom", 100) / 100f, number(view, "x", 0) / 100f, number(view, "y", 0) / 100f,
                            !Boolean.FALSE.equals(view.getBoolean("dither")), Boolean.TRUE.equals(view.getBoolean("clear")),
                            number(view, "brightness", 0) / 100f, number(view, "contrast", 0) / 100f,
                            number(view, "saturation", 0) / 100f, Boolean.TRUE.equals(view.getBoolean("shade"))));
                })), 1);
    }

    private DialogInput slider(String key, String label, float from, float to, float step, float initial) {
        return DialogInput.numberRange(key, Component.text(label), from, to)
                .step(step)
                .initial(Math.clamp(Math.round(initial / step) * step, from, to))
                .width(320)
                .build();
    }

    private float number(DialogResponseView view, String key, float fallback) {
        Float value = view.getFloat(key);
        return value == null ? fallback : value;
    }

    private interface Reply {
        void accept(Player player, DialogResponseView view);
    }

    private DialogBase base(String title, String text, List<DialogInput> inputs, DialogBase.DialogAfterAction after) {
        return DialogBase.builder(Component.text(title))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(after)
                .body(List.of(DialogBody.plainMessage(Component.text(text), 320)))
                .inputs(inputs)
                .build();
    }

    private ActionButton button(String label, Consumer<Player> action) {
        return button(label, (player, view) -> action.accept(player));
    }

    private ActionButton button(String label, Reply reply) {
        return ActionButton.builder(Component.text(label))
                .width(150)
                .action(click(reply))
                .build();
    }

    private DialogAction click(Reply reply) {
        return DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player)) return;
            if (Bukkit.isPrimaryThread()) reply.accept(player, view);
            else Bukkit.getScheduler().runTask(plugin, () -> reply.accept(player, view));
        }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(15)).build());
    }

    private void show(Player player, DialogBase base, List<ActionButton> buttons, int columns) {
        ActionButton close = base.afterAction() == DialogBase.DialogAfterAction.NONE
                ? button(Lang.get("menu.close.name"), Player::closeDialog)
                : ActionButton.builder(Component.text(Lang.get("menu.close.name"))).width(150).build();
        player.showDialog(Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).exitAction(close).columns(columns).build())));
    }
}
