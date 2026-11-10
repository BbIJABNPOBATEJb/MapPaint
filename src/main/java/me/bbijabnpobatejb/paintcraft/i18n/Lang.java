package me.bbijabnpobatejb.paintcraft.i18n;

import lombok.experimental.UtilityClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@UtilityClass
public class Lang {

    public final String DEFAULT = "en";
    public final List<String> BUNDLED = List.of("en", "ru");

    private Plugin plugin;
    private File folder;
    private YamlConfiguration active = new YamlConfiguration();
    private String code = DEFAULT;
    private final Map<String, String> names = new LinkedHashMap<>();

    public void init(Plugin plugin) {
        Lang.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "lang");

        for (String language : BUNDLED) {
            String resource = "lang/" + language + ".yml";
            if (!new File(folder, language + ".yml").exists() && plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            }
        }

        names.clear();
        for (String language : available()) {
            names.put(language, config(language).getString("language.name", language));
        }
    }

    public List<String> available() {
        List<String> result = new ArrayList<>(BUNDLED);
        File[] files = folder == null ? null : folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return result;

        List<String> extra = new ArrayList<>();
        for (File file : files) {
            String language = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!result.contains(language)) extra.add(language);
        }
        extra.sort(String::compareTo);
        result.addAll(extra);
        return result;
    }

    public boolean isAvailable(String language) {
        return language != null && available().contains(language.toLowerCase(Locale.ROOT));
    }

    public void load(String language) {
        String normalized = language.toLowerCase(Locale.ROOT);
        active = config(normalized);
        code = normalized;
    }

    private YamlConfiguration config(String language) {
        YamlConfiguration english = bundled(DEFAULT);
        YamlConfiguration base = language.equals(DEFAULT) ? english : bundled(language);
        if (base != english) base.setDefaults(english);

        File file = new File(folder, language + ".yml");
        if (!file.exists()) return base;

        YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.setDefaults(base);
        return disk;
    }

    private YamlConfiguration bundled(String language) {
        InputStream stream = plugin.getResource("lang/" + language + ".yml");
        if (stream == null) return new YamlConfiguration();

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Can't read bundled language " + language + ": " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    public String code() {
        return code;
    }

    public String name(String language) {
        return names.getOrDefault(language, language);
    }

    public String get(String key, Object... placeholders) {
        return fill(active.getString(key, key), placeholders);
    }

    public List<String> list(String key, Object... placeholders) {
        List<String> lines = new ArrayList<>();
        for (String line : active.getStringList(key)) {
            lines.add(fill(line, placeholders));
        }
        return lines;
    }

    public String plural(int count, String key, Object... placeholders) {
        String form;
        if (active.contains(key + ".few")) {
            int mod100 = Math.abs(count) % 100;
            int mod10 = mod100 % 10;
            if (mod100 >= 11 && mod100 <= 14) form = "many";
            else if (mod10 == 1) form = "one";
            else if (mod10 >= 2 && mod10 <= 4) form = "few";
            else form = "many";
        } else {
            form = count == 1 ? "one" : "many";
        }
        return fill(active.getString(key + "." + form, key), placeholders).replace("{count}", String.valueOf(count));
    }

    private String fill(String template, Object... placeholders) {
        String result = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}
