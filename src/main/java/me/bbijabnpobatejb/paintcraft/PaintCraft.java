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
import me.bbijabnpobatejb.paintcraft.exchange.ExchangeService;
import me.bbijabnpobatejb.paintcraft.i18n.Lang;
import me.bbijabnpobatejb.paintcraft.item.ItemService;
import me.bbijabnpobatejb.paintcraft.listener.PaintListener;
import me.bbijabnpobatejb.paintcraft.listener.WorldListener;
import me.bbijabnpobatejb.paintcraft.map.MapBridge;
import me.bbijabnpobatejb.paintcraft.packet.PaintPacketListener;
import me.bbijabnpobatejb.paintcraft.packet.SlotTracker;
import me.bbijabnpobatejb.paintcraft.paint.PaintService;
import me.bbijabnpobatejb.paintcraft.store.PaintingStorage;
import me.bbijabnpobatejb.paintcraft.studio.StudioService;
import me.bbijabnpobatejb.paintcraft.ui.listener.InventoryListener;
import me.bbijabnpobatejb.paintcraft.util.ChatInput;
import me.bbijabnpobatejb.paintcraft.world.PlacementManager;
import me.bbijabnpobatejb.paintcraft.world.PlacementPreview;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;
        PacketEvents.getAPI().init();

        configHandler = new ConfigHandler(this);
        configHandler.load();
        reloadLanguage();

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
        if (!storage.all().isEmpty()) getLogger().info("Paintings in storage: " + storage.all().size());
    }

    @Override
    public void onDisable() {
        if (studioService != null) studioService.shutdown();
        if (placementPreview != null) placementPreview.shutdown();
        if (packetListener != null) PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        if (storage != null) storage.shutdown();
        if (recipeService != null) recipeService.unload();
        if (liteCommands != null) liteCommands.unregister();
        PacketEvents.getAPI().terminate();
    }

    public void reload() {
        configHandler.load();
        reloadLanguage();
        recipeService.load();
        scheduleAutosave();
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
        try {
            paintService.tick();
            studioService.tick();
            placementPreview.tick();
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "Paint tick failed", e);
        }
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
        registerListener(chatInput);
        Compat.listeners(this);
    }

    void registerListener(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
