package me.bbijabnpobatejb.paintcraft.util.action;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.util.Chat;
import me.bbijabnpobatejb.paintcraft.util.StringUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionLoreBuilder {

    public static final String KEY_DROP = "key.drop";
    public static final String KEY_SWAP_HANDS = "key.swapOffhand";
    public static final String KEY_SNEAK = "key.sneak";
    public static final String KEY_JUMP = "key.jump";
    public static final String KEY_SPRINT = "key.sprint";

    private final List<Component> lines = new ArrayList<>();

    public static ActionLoreBuilder create() {
        return new ActionLoreBuilder();
    }

    public static ActionLoreBuilder create(@Nullable Object leftText) {
        return new ActionLoreBuilder().leftClick(leftText);
    }

    public ActionLoreBuilder leftClick(@Nullable Object text) {
        return addAction(Component.text(Lang.get("click.left")), text);
    }

    public ActionLoreBuilder shiftLeftClick(@Nullable Object text) {
        return addAction(Component.text("Shift + " + Lang.get("click.left")), text);
    }

    public ActionLoreBuilder rightClick(@Nullable Object text) {
        return addAction(Component.text(Lang.get("click.right")), text);
    }

    public ActionLoreBuilder shiftRightClick(@Nullable Object text) {
        return addAction(Component.text("Shift + " + Lang.get("click.right")), text);
    }

    public ActionLoreBuilder drop(@Nullable Object text) {
        return key(KEY_DROP, text);
    }

    public ActionLoreBuilder swapHands(@Nullable Object text) {
        return key(KEY_SWAP_HANDS, text);
    }

    public ActionLoreBuilder shiftSwapHands(@Nullable Object text) {
        return addAction(Component.text("Shift + ").append(Component.keybind(KEY_SWAP_HANDS)), text);
    }

    public ActionLoreBuilder key(String keybind, @Nullable Object text) {
        return addAction(Component.keybind(keybind), text);
    }

    public ActionLoreBuilder custom(String actionName, @Nullable Object text) {
        return addAction(Component.text(actionName), text);
    }

    private ActionLoreBuilder addAction(Component actionName, @Nullable Object text) {
        if (text == null) return this;

        Component description = text instanceof Component component ? component : StringUtil.mm(text.toString());
        boolean empty = PlainTextComponentSerializer.plainText().serialize(description).isEmpty();

        Component line = Component.empty().color(NamedTextColor.WHITE)
                .append(actionName.colorIfAbsent(TextColor.color(Chat.MAIN)))
                .append(empty ? Component.empty() : Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(description)
                .decoration(TextDecoration.ITALIC, false);
        lines.add(line);
        return this;
    }

    public List<Component> build() {
        List<Component> out = new ArrayList<>(lines.size() + 1);
        if (!lines.isEmpty()) out.add(Component.empty());
        out.addAll(lines);
        return out;
    }

    public List<Component> buildOnlyLines() {
        return new ArrayList<>(lines);
    }
}
