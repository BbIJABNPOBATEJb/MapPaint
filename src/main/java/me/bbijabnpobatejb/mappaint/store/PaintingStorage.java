package me.bbijabnpobatejb.mappaint.store;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import me.bbijabnpobatejb.mappaint.model.Painting;
import me.bbijabnpobatejb.mappaint.palette.Palette;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

@FieldDefaults(level = AccessLevel.PRIVATE)
public final class PaintingStorage {

    private static final int MAGIC = 0x50435031;
    private static final String ID_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final long EVICT_AFTER_MS = 5 * 60_000L;

    final Plugin plugin;
    final Path dir;
    final Path dataFile;
    final Map<String, Painting> byId = new HashMap<>();
    final Map<Integer, Painting> byMapId = new HashMap<>();
    final ArrayDeque<Integer> freeMapIds = new ArrayDeque<>();
    final Set<String> removedPlacements = new HashSet<>();
    final SecureRandom random = new SecureRandom();
    final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MapPaint-writer");
        t.setDaemon(true);
        return t;
    });
    boolean dataDirty;
    public java.util.function.Consumer<Painting> beforeEvict = painting -> {
    };

    public PaintingStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("paintings");
        this.dataFile = plugin.getDataFolder().toPath().resolve("data.yml");
    }

    public void load() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Can't create the paintings folder", e);
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".pcp")).forEach(this::readHeader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Can't read the paintings folder", e);
        }
        if (Files.exists(dataFile)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile.toFile());
            for (int id : yaml.getIntegerList("free-map-ids")) {
                if (!byMapId.containsKey(id)) {
                    freeMapIds.add(id);
                }
            }
            removedPlacements.addAll(yaml.getStringList("removed-placements"));
        }
    }

    private void readHeader(Path file) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - 4);
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            Painting painting = readHeader(id, in);
            byId.put(id, painting);
            indexMaps(painting);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Broken painting file: " + name, e);
        }
    }

    private static Painting readHeader(String id, DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("not a painting file");
        }
        UUID owner = new UUID(in.readLong(), in.readLong());
        String ownerName = in.readUTF();
        String title = in.readUTF();
        int flags = in.readUnsignedByte();
        int blocksW = in.readUnsignedByte();
        int blocksH = in.readUnsignedByte();
        int res = in.readUnsignedShort();
        if (blocksW < 1 || blocksH < 1 || blocksW > 64 || blocksH > 64 || Integer.bitCount(res) != 1 || res > 128) {
            throw new IOException("bad size");
        }
        Painting painting = new Painting(id, owner, ownerName, blocksW, blocksH, res);
        painting.title = title;
        painting.signed = (flags & 1) != 0;
        painting.shared = (flags & 2) != 0;
        painting.touched = (flags & 4) != 0;
        painting.imported = (flags & 8) != 0;
        painting.created = in.readLong();
        painting.modified = in.readLong();
        for (int i = 0; i < painting.mapIds.length; i++) {
            painting.mapIds[i] = in.readInt();
        }
        return painting;
    }

    public byte[] pixels(Painting painting) {
        painting.lastAccess = System.currentTimeMillis();
        if (painting.pixels == null) {
            painting.pixels = readPixels(painting);
        }
        return painting.pixels;
    }

    private byte[] readPixels(Painting painting) {
        int length = painting.width() * painting.height();
        byte[] pixels = new byte[length];
        Path file = file(painting.id);
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            readHeader(painting.id, in);
            try (InputStream inflated = new InflaterInputStream(in)) {
                int read = 0;
                while (read < length) {
                    int n = inflated.read(pixels, read, length - read);
                    if (n < 0) {
                        throw new EOFException();
                    }
                    read += n;
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Can't read pixels of painting " + painting.id + ", the canvas will be blank", e);
            Arrays.fill(pixels, Palette.WHITE);
        }
        return pixels;
    }

    public Painting get(String id) {
        return id == null ? null : byId.get(id);
    }

    public Collection<Painting> all() {
        return byId.values();
    }

    public List<Painting> ownedBy(UUID owner) {
        List<Painting> list = new ArrayList<>();
        for (Painting painting : byId.values()) {
            if (painting.owner.equals(owner)) {
                list.add(painting);
            }
        }
        list.sort(Comparator.comparingLong((Painting p) -> p.modified).reversed());
        return list;
    }

    public int countOwnedBy(UUID owner) {
        int count = 0;
        for (Painting painting : byId.values()) {
            if (painting.owner.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public Painting create(UUID owner, String ownerName, int blocksW, int blocksH, int res) {
        Painting painting = new Painting(newId(), owner, ownerName, blocksW, blocksH, res);
        painting.pixels = new byte[painting.width() * painting.height()];
        Arrays.fill(painting.pixels, Palette.WHITE);
        painting.lastAccess = System.currentTimeMillis();
        painting.dirty = true;
        byId.put(painting.id, painting);
        return painting;
    }

    private String newId() {
        while (true) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
            }
            if (!byId.containsKey(sb.toString())) {
                return sb.toString();
            }
        }
    }

    public void delete(Painting painting) {
        byId.remove(painting.id);
        for (int mapId : painting.mapIds) {
            if (mapId >= 0 && byMapId.remove(mapId) != null) {
                freeMapIds.add(mapId);
                dataDirty = true;
            }
        }
        painting.dirty = false;
        Path file = file(painting.id);
        writer.execute(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Can't delete the file of painting " + painting.id, e);
            }
        });
    }

    public void indexMaps(Painting painting) {
        for (int mapId : painting.mapIds) {
            if (mapId >= 0) {
                byMapId.put(mapId, painting);
            }
        }
    }

    public Painting byMapId(int mapId) {
        return byMapId.get(mapId);
    }

    public int usedMaps() {
        return byMapId.size();
    }

    public int freeMaps() {
        return freeMapIds.size();
    }

    public MapView allocateMap() {
        while (!freeMapIds.isEmpty()) {
            int id = freeMapIds.poll();
            dataDirty = true;
            MapView view = Bukkit.getMap(id);
            if (view != null) {
                return view;
            }
        }
        return Bukkit.createMap(Bukkit.getWorlds().get(0));
    }

    public void markPlacementRemoved(UUID placement) {
        removedPlacements.add(placement.toString());
        dataDirty = true;
    }

    public boolean isPlacementRemoved(UUID placement) {
        return !removedPlacements.isEmpty() && removedPlacements.contains(placement.toString());
    }

    public void autosave() {
        long now = System.currentTimeMillis();
        for (Painting painting : byId.values()) {
            if (painting.dirty) {
                painting.dirty = false;
                byte[] header = header(painting);
                byte[] pixels = pixels(painting).clone();
                Path file = file(painting.id);
                writer.execute(() -> write(file, header, pixels));
            } else if (painting.pixels != null && painting.placements.isEmpty()
                    && now - painting.lastAccess > EVICT_AFTER_MS) {
                beforeEvict.accept(painting);
                painting.pixels = null;
                painting.undo = null;
                painting.resSource = null;
            }
        }
        if (dataDirty) {
            dataDirty = false;
            YamlConfiguration yaml = dataYaml();
            writer.execute(() -> saveData(yaml));
        }
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(20, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Saving paintings did not finish in 20 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Painting painting : byId.values()) {
            if (painting.dirty) {
                painting.dirty = false;
                write(file(painting.id), header(painting), pixels(painting));
            }
        }
        saveData(dataYaml());
    }

    private YamlConfiguration dataYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("free-map-ids", new ArrayList<>(freeMapIds));
        yaml.set("removed-placements", new ArrayList<>(removedPlacements));
        return yaml;
    }

    private void saveData(YamlConfiguration yaml) {
        try {
            yaml.save(dataFile.toFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Can't save data.yml", e);
        }
    }

    private static byte[] header(Painting painting) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeLong(painting.owner.getMostSignificantBits());
            out.writeLong(painting.owner.getLeastSignificantBits());
            out.writeUTF(painting.ownerName);
            out.writeUTF(painting.title);
            out.writeByte((painting.signed ? 1 : 0) | (painting.shared ? 2 : 0) | (painting.touched ? 4 : 0)
                    | (painting.imported ? 8 : 0));
            out.writeByte(painting.blocksW);
            out.writeByte(painting.blocksH);
            out.writeShort(painting.res);
            out.writeLong(painting.created);
            out.writeLong(painting.modified);
            for (int mapId : painting.mapIds) {
                out.writeInt(mapId);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    private void write(Path file, byte[] header, byte[] pixels) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (var out = Files.newOutputStream(tmp)) {
                out.write(header);
                try (DeflaterOutputStream deflated = new DeflaterOutputStream(out)) {
                    deflated.write(pixels);
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Can't save painting " + file.getFileName(), e);
        }
    }

    private Path file(String id) {
        return dir.resolve(id + ".pcp");
    }
}
