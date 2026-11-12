package me.bbijabnpobatejb.paintcraft;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import dev.rollczi.litecommands.LiteCommands;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.val;
import me.bbijabnpobatejb.paintcraft.command.BukkitCommandConfigurator;
import me.bbijabnpobatejb.paintcraft.command.PaintCommand;
import me.bbijabnpobatejb.paintcraft.compat.Compat;
import me.bbijabnpobatejb.paintcraft.compat.Icons;
import me.bbijabnpobatejb.paintcraft.compat.ItemStyle;
import me.bbijabnpobatejb.paintcraft.compat.Prompts;
import me.bbijabnpobatejb.paintcraft.config.ConfigHandler;
import me.bbijabnpobatejb.paintcraft.craft.CraftListener;
import me.bbijabnpobatejb.paintcraft.craft.RecipeService;
import me.bbijabnpobatejb.paintcraft.diag.ErrorLog;
import me.bbijabnpobatejb.paintcraft.diag.Profiler;
import me.bbijabnpobatejb.paintcraft.diag.ReportService;
import me.bbijabnpobatejb.paintcraft.exchange.ExchangeService;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.item.ItemService;
import me.bbijabnpobatejb.paintcraft.listener.ItemGuardListener;
import me.bbijabnpobatejb.paintcraft.listener.PaintListener;
import me.bbijabnpobatejb.paintcraft.listener.WorldListener;
import me.bbijabnpobatejb.paintcraft.map.MapBridge;
import me.bbijabnpobatejb.paintcraft.packet.PaintPacketListener;
import me.bbijabnpobatejb.paintcraft.packet.SlotTracker;
import me.bbijabnpobatejb.paintcraft.paint.PaintService;
import me.bbijabnpobatejb.paintcraft.store.PaintingStorage;
import me.bbijabnpobatejb.paintcraft.studio.StudioService;
import me.bbijabnpobatejb.paintcraft.ui.inventory.InventoryGuiImpl;
import me.bbijabnpobatejb.paintcraft.ui.listener.InventoryListener;
import me.bbijabnpobatejb.paintcraft.util.ChatInput;
import me.bbijabnpobatejb.paintcraft.util.UpdateChecker;
import me.bbijabnpobatejb.paintcraft.world.PlacementManager;
import me.bbijabnpobatejb.paintcraft.world.PlacementPreview;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.logging.Level;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class PaintCraft extends JavaPlugin {
    @Getter
    static PaintCraft instance;
    public static final String PLUGIN_ID = "paintcraft";
    public static final String PERMISSION_USE = PLUGIN_ID + ".use";
    public static final String PERMISSION_EXPORT = PLUGIN_ID + ".export";
    public static final String PERMISSION_IMPORT = PLUGIN_ID + ".import";
    public static final String PERMISSION_IMAGE = PLUGIN_ID + ".image";
    public static final String PERMISSION_GIVE = PLUGIN_ID + ".give";
    public static final String PERMISSION_ADMIN = PLUGIN_ID + ".admin";
    public static final int BSTATS_ID = 0;
    private static final long ERROR_INTERVAL_MILLIS = 5000;

    LiteCommands<CommandSender> liteCommands;

    ConfigHandler configHandler;
    PaintingStorage storage;
    MapBridge mapBridge;
    ItemService itemService;
    PlacementManager placementManager;
    PaintService paintService;
    ExchangeService exchangeService;
    RecipeService recipeService;
    SlotTracker slotTracker;
    StudioService studioService;
    PlacementPreview placementPreview;
    ChatInput chatInput;
    Prompts prompts;
    ItemStyle itemStyle;
    @Nullable Icons icons;
    PacketListenerCommon packetListener;
    BukkitTask autosaveTask;
    Profiler profiler;
    ErrorLog errorLog;
    ReportService reportService;
    UpdateChecker updateChecker;
    boolean unsupported;
    long lastTickError;

    @Override
    public void onLoad() {
        if (!Compat.hasClass("io.papermc.paper.event.player.PrePlayerAttackEntityEvent")) {
            unsupported = true;
            return;
        }
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        if (unsupported) {
            getLogger().severe("PaintCraft needs Paper 1.20 or newer (or a fork of it). Spigot and CraftBukkit are not supported.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        instance = this;
        errorLog = new ErrorLog();
        getLogger().addHandler(errorLog);
        PacketEvents.getAPI().init();

        configHandler = new ConfigHandler(this);
        configHandler.load();
        reloadLanguage();
        profiler = new Profiler();
        reportService = new ReportService(this);
        updateChecker = new UpdateChecker(this);

        storage = new PaintingStorage(this);
        storage.load();
        mapBridge = new MapBridge(this, storage);
        storage.beforeEvict = mapBridge::detach;
        chatInput = new ChatInput(this);
        prompts = Compat.prompts(this);
        itemStyle = Compat.items(this);
        icons = Compat.icons(this);
        itemService = new ItemService(this);
        placementManager = new PlacementManager(this, storage, mapBridge, itemService);
        paintService = new PaintService(this, storage, mapBridge, placementManager);
        exchangeService = new ExchangeService(this);
        recipeService = new RecipeService(this);
        recipeService.load();

        slotTracker = new SlotTracker(this);
        studioService = new StudioService(this);
        placementPreview = new PlacementPreview(this);

        registerListeners();
        registerCommand();
        packetListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new PaintPacketListener(slotTracker), PacketListenerPriority.NORMAL);

        placementManager.scanLoaded();

        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        scheduleAutosave();
        startMetrics();
        updateChecker.check();
        if (!storage.all().isEmpty()) getLogger().info("Paintings in storage: " + storage.all().size());
    }

    void startMetrics() {
        if (BSTATS_ID <= 0) return;
        val metrics = new Metrics(this, BSTATS_ID);
        metrics.addCustomChart(new SimplePie("language", Lang::code));
        metrics.addCustomChart(new SingleLineChart("paintings", () -> storage.all().size()));
    }

    @Override
    public void onDisable() {
        if (unsupported) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof InventoryGuiImpl) player.closeInventory();
        }
        if (reportService != null) reportService.shutdown();
        if (studioService != null) studioService.shutdown();
        if (placementPreview != null) placementPreview.shutdown();
        if (packetListener != null) PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        if (storage != null) storage.shutdown();
        if (recipeService != null) recipeService.unload();
        if (liteCommands != null) liteCommands.unregister();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
        if (errorLog != null) getLogger().removeHandler(errorLog);
    }

    public void reload() {
        configHandler.load();
        reloadLanguage();
        recipeService.load();
        scheduleAutosave();
        if (!configHandler.isPanel()) studioService.shutdown();
        Bukkit.getOnlinePlayers().forEach(studioService::refresh);
    }

    public void reloadLanguage() {
        Lang.init(this);

        String code = getConfig().getString("language", Lang.DEFAULT).toLowerCase(Locale.ROOT);
        if (!Lang.isAvailable(code)) {
            getLogger().warning("Unknown language '" + code + "' in config.yml, using " + Lang.DEFAULT
                    + ". Available: " + String.join(", ", Lang.available()));
            code = Lang.DEFAULT;
        }
        Lang.load(code);
    }

    public void setLanguage(String code) {
        Lang.load(code);
        getConfig().set("language", Lang.code());
        saveConfig();
        recipeService.load();
        Bukkit.getOnlinePlayers().forEach(studioService::refresh);
    }

    void tick() {
        long start = System.nanoTime(), painted = start, panel = start;
        try {
            paintService.tick();
            painted = System.nanoTime();
            studioService.tick();
            panel = System.nanoTime();
            placementPreview.tick();
        } catch (RuntimeException | LinkageError e) {
            long now = System.currentTimeMillis();
            if (now - lastTickError > ERROR_INTERVAL_MILLIS) {
                lastTickError = now;
                getLogger().log(Level.SEVERE, "Paint tick failed", e);
            }
        }
        long end = System.nanoTime();
        painted = Math.max(painted, start);
        panel = Math.max(panel, painted);
        profiler.record(painted - start, panel - painted, end - panel);
        reportService.tick(painted - start, panel - painted, end - panel);
    }

    void scheduleAutosave() {
        if (autosaveTask != null) autosaveTask.cancel();
        long period = configHandler.getAutosaveSeconds() * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, storage::autosave, period, period);
    }

    void registerCommand() {
        val builder = LiteBukkitFactory.builder(PLUGIN_ID, this);
        BukkitCommandConfigurator.apply(builder, this);
        liteCommands = builder
                .commands(new PaintCommand(this))
                .build();
    }

    void registerListeners() {
        registerListener(new PaintListener(this));
        registerListener(new WorldListener(this));
        registerListener(new InventoryListener());
        registerListener(new CraftListener(this));
        val guard = new ItemGuardListener(this);
        registerListener(guard);
        Compat.optionalEvent(this, "org.bukkit.event.block.CrafterCraftEvent", guard::onCrafter);
        registerListener(updateChecker);
        registerListener(chatInput);
        Compat.listeners(this);
    }

    void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
