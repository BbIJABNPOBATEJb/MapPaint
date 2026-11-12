package me.bbijabnpobatejb.mappaint.ui.inventory;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.mappaint.compat.Compat;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.ui.button.ButtonConsumer;
import me.bbijabnpobatejb.mappaint.ui.button.ItemButton;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.ItemUtil;
import me.bbijabnpobatejb.mappaint.util.Sounds;
import me.bbijabnpobatejb.mappaint.util.StringUtil;
import me.bbijabnpobatejb.mappaint.util.action.ActionLoreBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class InventoryGuiImpl implements InventoryHolder {

    public static final int CLOSE_SLOT = 0;
    public static final int HELP_SLOT = 8;

    private static ItemStack background;

    final int size;
    final Inventory inventory;
    final Map<Integer, ItemButton> buttons = new LinkedHashMap<>();

    protected InventoryGuiImpl(int size, Component title) {
        this.size = size;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    protected InventoryGuiImpl(int size, String miniMessageTitle) {
        this(size, StringUtil.mm(miniMessageTitle));
    }

    public abstract void init();

    public void render() {
        inventory.clear();
        renderBackground();
        if (shouldRenderHelp()) renderHelp(getHelpText());
        renderButtons();
    }

    public void renderButtons() {
        for (val button : buttons.values()) inventory.setItem(button.slotId(), button.itemStack());
    }

    public void renderBackground() {
        if (background == null) {
            background = ItemUtil.builder(Material.GRAY_STAINED_GLASS_PANE)
                    .displayName(Component.space())
                    .editMeta(Compat::hideTooltip)
                    .build();
        }
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, background);
    }

    public void clear() {
        buttons.clear();
        inventory.clear();
    }

    public void refresh() {
        clear();
        init();
        render();
    }

    public void onOpen(Player player) {
        player.openInventory(inventory);
    }

    public void onClose(Player player) {
    }

    public boolean allowsOwnInventory() {
        return false;
    }

    public void onSecond() {
    }

    public void onClick(Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() != inventory) return;
        val button = buttons.get(event.getSlot());
        if (button == null) return;
        if (button.click().apply(this, player, event.getClick())) playClickSound(player);
    }

    public void playClickSound(Player player) {
        Sounds.playSound(player, Sounds.HAT, .6f, 1f);
    }

    public void addButton(int slotId, ItemStack itemStack, ButtonConsumer click) {
        buttons.put(slotId, new ItemButton(slotId, click, itemStack));
    }

    public void addButton(int x, int y, ItemStack itemStack, ButtonConsumer click) {
        addButton(getItemPos(x, y), itemStack, click);
    }

    public void addInfo(int slotId, ItemStack itemStack) {
        addButton(slotId, itemStack, (gui, player, click) -> false);
    }

    public void addCloseButton() {
        addButton(CLOSE_SLOT, ItemUtil.builder(Material.BARRIER)
                .displayName(Chat.red(Lang.get("menu.close.name")))
                .lore(ActionLoreBuilder.create(Lang.get("menu.close.action")).build())
                .build(), (gui, player, click) -> {
            player.closeInventory();
            return true;
        });
    }

    public void addReturnButton(ButtonConsumer click) {
        addButton(CLOSE_SLOT, ItemUtil.builder(Material.ARROW)
                .displayName(Chat.white(Lang.get("menu.back.name")))
                .lore(ActionLoreBuilder.create(Lang.get("menu.back.action")).build())
                .build(), click);
    }

    public boolean shouldRenderHelp() {
        return !getHelpText().isEmpty();
    }

    public List<Component> getHelpText() {
        return List.of();
    }

    public void renderHelp(List<Component> text) {
        inventory.setItem(HELP_SLOT, ItemUtil.builder(Material.BOOK)
                .displayName(Chat.yellow(Lang.get("menu.hint")))
                .lore(text)
                .build());
    }

    public static int getItemPos(int x, int y) {
        return x + y * 9;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
