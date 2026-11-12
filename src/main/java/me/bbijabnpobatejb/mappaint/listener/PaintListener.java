package me.bbijabnpobatejb.mappaint.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.mappaint.MapPaint;
import me.bbijabnpobatejb.mappaint.compat.Compat;
import me.bbijabnpobatejb.mappaint.i18n.Lang;
import me.bbijabnpobatejb.mappaint.item.PaintItem;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.paint.PlayerSession;
import me.bbijabnpobatejb.mappaint.palette.Palette;
import me.bbijabnpobatejb.mappaint.ui.menu.PaletteGui;
import me.bbijabnpobatejb.mappaint.util.Chat;
import me.bbijabnpobatejb.mappaint.util.Sounds;
import me.bbijabnpobatejb.mappaint.world.Placement;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaintListener implements Listener {

    private static final double NEAR_MISS = 0.6;
    private static final double SEAM_REACH = 6.0;

    final MapPaint plugin;
    boolean breaking;

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() == Action.PHYSICAL) return;
        PaintItem type = plugin.getItemService().typeOf(event.getItem());
        Player player = event.getPlayer();
        if (type == null) {
            onSeamClick(event, player);
            return;
        }
        if (!player.hasPermission(MapPaint.PERMISSION_USE)) return;

        if (type == PaintItem.BRUSH) {
            onBrushClick(event, player);
            return;
        }
        if (type == PaintItem.CANVAS && player.isSneaking() && plugin.getRecipeService().unitCanvases(player) > 0
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            Block block = event.getClickedBlock();
            if (block == null || !block.getType().isInteractable()) {
                event.setCancelled(true);
                plugin.getPrompts().canvas(player);
                return;
            }
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useItemInHand() == Event.Result.DENY) return;

        Block clicked = event.getClickedBlock();
        if (clicked.getType().isInteractable() && !player.isSneaking() && event.useInteractedBlock() != Event.Result.DENY) return;
        event.setCancelled(true);
        place(player, event.getItem(), clicked, event);
    }

    private void onSeamClick(PlayerInteractEvent event, Player player) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        val hit = plugin.getPlacementManager().traceVisible(player, SEAM_REACH);
        if (hit == null) return;

        Placement placement = hit.placement();
        if (action == Action.LEFT_CLICK_BLOCK) {
            int tile = (int) hit.b() * placement.painting.blocksW + (int) hit.a();
            ItemFrame frame = placement.frames[tile];
            if (frame == null || !frame.isValid()) return;
            event.setCancelled(true);
            onPunch(player, frame);
        } else if (useInk(player, placement, player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private void onBrushClick(PlayerInteractEvent event, Player player) {
        Action action = event.getAction();
        val session = plugin.getPaintService().session(player);
        double reach = plugin.getConfigHandler().getReach();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (right) plugin.getItemStyle().used(player, false);

        if (plugin.getStudioService().click(player)) {
            if (right && plugin.getStudioService().isDragging(player)) event.setUseInteractedBlock(Event.Result.DENY);
            else event.setCancelled(true);
            return;
        }

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            pickColor(player, action == Action.LEFT_CLICK_BLOCK ? event.getClickedBlock() : null);
            return;
        }

        val hit = plugin.getPlacementManager().trace(player, reach);
        val visible = hit != null && hit.inside() ? plugin.getPlacementManager().traceVisible(player, reach) : null;
        if (visible != null) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (!plugin.getPaintService().canEdit(player, visible.placement().painting)) event.setCancelled(true);
            if (firstClickThisTick(session)) plugin.getPaintService().press(player, visible);
            return;
        }

        if (!player.isSneaking() && action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType().isInteractable()
                && event.useInteractedBlock() != Event.Result.DENY) {
            return;
        }
        event.setCancelled(true);
        if (hit != null && hit.outside() < NEAR_MISS) return;
        openPalette(player, session);
    }

    private void onBrushClickFrame(Player player) {
        if (!player.hasPermission(MapPaint.PERMISSION_USE)) return;
        val style = plugin.getItemStyle();
        if (plugin.getStudioService().click(player)) {
            if (plugin.getStudioService().isDragging(player)) style.used(player, true);
            return;
        }
        val session = plugin.getPaintService().session(player);
        val visible = plugin.getPlacementManager().traceVisible(player, plugin.getConfigHandler().getReach());
        if (visible == null) {
            if (player.isSneaking()) openPalette(player, session);
            return;
        }
        if (session.getStroke() != null) style.used(player, true);
        if (!firstClickThisTick(session)) return;

        plugin.getPaintService().press(player, visible);
        if (session.getStroke() != null) style.used(player, true);
    }

    private void openPalette(Player player, PlayerSession session) {
        if (firstClickThisTick(session)) new PaletteGui(plugin, player).onOpen(player);
    }

    private boolean firstClickThisTick(PlayerSession session) {
        int tick = Bukkit.getCurrentTick();
        if (session.getLastClickTick() == tick) return false;
        session.setLastClickTick(tick);
        return true;
    }

    private void pickColor(Player player, @Nullable Block block) {
        val session = plugin.getPaintService().session(player);
        if (!firstClickThisTick(session)) return;

        val hit = plugin.getPlacementManager().traceVisible(player, plugin.getConfigHandler().getReach());
        if (hit != null) {
            Painting painting = hit.placement().painting;
            byte color = plugin.getStorage().pixels(painting)[hit.y() * painting.width() + hit.x()];
            plugin.getPaintService().setColor(player, color);
            Sounds.playSound(player, Sound.ITEM_BOTTLE_FILL, .5f, 1.4f);
            return;
        }
        if (block == null) return;

        Color color = Compat.mapColor(block);
        if (color == null || color.asRGB() == 0) return;
        plugin.getPaintService().setColor(player, Palette.nearest(color.asRGB()));
        Sounds.playSound(player, Sound.ITEM_BOTTLE_FILL, .5f, 1.4f);
    }

    private void place(Player player, ItemStack item, Block clicked, PlayerInteractEvent event) {
        val items = plugin.getItemService();
        val config = plugin.getConfigHandler();
        val placements = plugin.getPlacementManager();

        Painting painting = null;
        int blocksW, blocksH;
        if (items.typeOf(item) == PaintItem.PAINTING) {
            painting = plugin.getStorage().get(items.paintingIdOf(item));
            if (painting == null) {
                Chat.sendChat(player, Chat.red(Lang.get("place.deleted")));
                player.getInventory().setItemInMainHand(items.createCanvas(items.widthOf(item), items.heightOf(item), item.getAmount()));
                return;
            }
            blocksW = painting.blocksW;
            blocksH = painting.blocksH;
        } else {
            blocksW = items.widthOf(item);
            blocksH = items.heightOf(item);
            if (blocksW > config.getMaxSize() || blocksH > config.getMaxSize()) {
                Chat.sendActionBar(player, Chat.red(Lang.get("place.too-big", "max", config.getMaxSize() + "×" + config.getMaxSize())));
                return;
            }
        }

        val spot = placements.findSpot(player, clicked, event.getBlockFace(), blocksW, blocksH);
        if (spot == null) {
            Chat.sendActionBar(player, Chat.red(Lang.get("place.no-room", "size", blocksW + "×" + blocksH)));
            return;
        }
        boolean created = painting == null;
        if (created) {
            if (plugin.getExchangeService().isOverLimit(player)) return;
            painting = plugin.getStorage().create(player.getUniqueId(), player.getName(), blocksW, blocksH,
                    config.getDefaultResolution());
        }

        Placement placement = placements.place(painting, player.getWorld(), spot, player.getUniqueId(), false);
        if (placement != null) {
            val check = new HangingPlaceEvent(placement.frames[0], player, clicked, event.getBlockFace(), EquipmentSlot.HAND, item);
            if (!check.callEvent()) {
                placements.remove(placement);
                placement = null;
            }
        }
        if (placement == null) {
            if (created) plugin.getExchangeService().delete(painting);
            Chat.sendActionBar(player, Chat.red(Lang.get("place.denied")));
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        plugin.getPaintService().session(player).setLastPainting(painting);
        player.swingMainHand();
        player.getWorld().playSound(placement.center(), Sound.ENTITY_PAINTING_PLACE, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!plugin.getPlacementManager().isOurs(event.getAttacked())) return;
        event.setCancelled(true);
        onPunch(event.getPlayer(), (ItemFrame) event.getAttacked());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(HangingBreakEvent event) {
        if (breaking || !plugin.getPlacementManager().isOurs(event.getEntity())) return;
        event.setCancelled(true);
        if (!(event instanceof HangingBreakByEntityEvent byEntity) || !(byEntity.getRemover() instanceof Player player)) return;

        ItemFrame frame = (ItemFrame) event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (frame.isValid() && player.isOnline()) onPunch(player, frame);
        });
    }

    private void onPunch(Player player, ItemFrame frame) {
        val session = plugin.getPaintService().session(player);
        int tick = Bukkit.getCurrentTick();
        if (tick - session.getLastPunchTick() <= 1) return;
        session.setLastPunchTick(tick);

        if (plugin.getItemService().isBrush(player.getInventory().getItemInMainHand())) {
            if (!plugin.getStudioService().click(player)) pickColor(player, null);
            return;
        }
        Placement placement = plugin.getPlacementManager().of(frame);
        if (placement != null) takeDown(player, placement, frame);
    }

    private boolean canManage(Player player, Placement placement) {
        return !plugin.getConfigHandler().isOnlyPlacerBreaks() || placement.placer.equals(player.getUniqueId())
                || player.hasPermission(MapPaint.PERMISSION_ADMIN);
    }

    private void takeDown(Player player, Placement placement, ItemFrame frame) {
        if (!canManage(player, placement)) {
            Chat.sendActionBar(player, Chat.red(Lang.get("place.break-owner")));
            return;
        }
        breaking = true;
        try {
            if (!Compat.hangingBreak(frame, player).callEvent()) {
                Chat.sendActionBar(player, Chat.red(Lang.get("place.denied")));
                return;
            }
        } finally {
            breaking = false;
        }

        Painting painting = placement.painting;
        plugin.getPaintService().finishStrokes(painting);
        Location center = placement.center();
        plugin.getPlacementManager().remove(placement);
        center.getWorld().playSound(center, Sound.ENTITY_PAINTING_BREAK, 1f, 1f);

        val items = plugin.getItemService();
        if (painting.touched || painting.signed || !painting.placements.isEmpty()) {
            center.getWorld().dropItem(center, items.createPainting(painting, 1));
            return;
        }
        plugin.getExchangeService().delete(painting);
        if (player.getGameMode() != GameMode.CREATIVE) {
            center.getWorld().dropItem(center, items.createCanvas(painting.blocksW, painting.blocksH, 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        val placements = plugin.getPlacementManager();
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!placements.isOurs(event.getRightClicked())) {
            if (plugin.getItemService().isBrush(hand) && event.getRightClicked().getType().name().equals("ARMADILLO")) {
                event.setCancelled(true);
            }
            return;
        }

        if (plugin.getItemService().isBrush(hand)) {
            event.setCancelled(true);
            onBrushClickFrame(player);
            return;
        }
        Placement placement = placements.of(event.getRightClicked());
        if (placement != null && useInk(player, placement, hand)) event.setCancelled(true);
    }

    private boolean useInk(Player player, Placement placement, ItemStack hand) {
        val placements = plugin.getPlacementManager();
        boolean glow = hand.getType() == Material.GLOW_INK_SAC;
        if (!glow && hand.getType() != Material.INK_SAC) return false;
        if (!plugin.getConfigHandler().isGlowing() || placements.isGlowing(placement) == glow) return true;
        if (!canManage(player, placement)) {
            Chat.sendActionBar(player, Chat.red(Lang.get("place.change-owner")));
            return true;
        }
        if (!placements.setGlowing(placement, glow)) return true;

        if (player.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        player.swingMainHand();
        player.getWorld().playSound(placement.center(), glow ? Sound.ITEM_GLOW_INK_SAC_USE : Sound.ITEM_INK_SAC_USE, 1f, 1f);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        val items = plugin.getItemService();
        if (!items.isBrush(event.getOffHandItem()) && !items.isBrush(event.getMainHandItem())) return;
        event.setCancelled(true);
        plugin.getPaintService().undo(event.getPlayer(), event.getPlayer().isSneaking());
    }

    @EventHandler(ignoreCancelled = true)
    public void onScroll(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int before = plugin.getSlotTracker().clientSlotBefore(player, event.getNewSlot(), event.getPreviousSlot());
        if (!player.isSneaking()) return;
        if (!plugin.getItemService().isBrush(player.getInventory().getItem(event.getPreviousSlot()))) return;

        int diff = Math.floorMod(event.getNewSlot() - before, 9);
        int steps = diff <= 4 ? diff : diff - 9;
        if (steps == 0) return;
        event.setCancelled(true);
        plugin.getSlotTracker().confirmCorrection(player);
        val paint = plugin.getPaintService();
        paint.setSize(player, paint.session(player).getSize() - steps);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getItemService().isBrush(event.getItemDrop().getItemStack())) return;
        Player player = event.getPlayer();
        val session = plugin.getPaintService().session(player);
        if (Bukkit.getCurrentTick() - session.getLastInventoryClickTick() <= 1) return;

        event.setCancelled(true);
        plugin.getPaintService().cycleTool(player, player.isSneaking() ? 1 : -1);
        Sounds.playSound(player, Sounds.HAT, .5f, player.isSneaking() ? 1.3f : 1f);
        player.updateInventory();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        plugin.getPaintService().session(player).setLastInventoryClickTick(Bukkit.getCurrentTick());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getStudioService().forget(event.getPlayer());
        plugin.getPlacementPreview().forget(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.getItemService().isBrush(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStudioService().forget(event.getPlayer());
        plugin.getPlacementPreview().forget(event.getPlayer());
        plugin.getSlotTracker().forget(event.getPlayer().getUniqueId());
        plugin.getPaintService().forget(event.getPlayer());
        plugin.getItemStyle().forget(event.getPlayer());
    }
}
