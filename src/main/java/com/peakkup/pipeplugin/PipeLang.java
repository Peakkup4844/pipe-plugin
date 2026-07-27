package com.peakkup.pipeplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads and holds console/log messages from lang.yml so they can be localized
 * without touching code. Placeholders written as {name} are substituted at runtime.
 *
 * On first run the bundled lang.yml is copied into the data folder; the bundled
 * copy is also kept as defaults so keys added in later versions still resolve
 * even if the admin's file is older.
 */
public final class PipeLang {

    private final FileConfiguration lang;

    public PipeLang(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        FileConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        // Fall back to the jar's bundled lang.yml for any key missing from the admin's copy
        InputStream bundled = plugin.getResource("lang.yml");
        if (bundled != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        this.lang = loaded;
    }

    /** Message for {@code key}; returns the key itself only if even the bundled file lacks it. */
    public String msg(String key) {
        return lookup(key);
    }

    /**
     * Message for {@code key} with {@code {placeholder}} tokens replaced.
     * Pass alternating name/value pairs, e.g. {@code msg("plugin-enabled", "platform", "Folia")}.
     */
    public String msg(String key, String... placeholders) {
        String s = lookup(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return s;
    }

    /**
     * Text for {@code key}, falling back to the jar's bundled lang.yml, then to the key itself.
     *
     * <p>Deliberately not {@code getString(key, key)}: passing an explicit default makes Bukkit
     * read only the admin's own file and skip {@link FileConfiguration#setDefaults} entirely,
     * so every key added in a later version printed as a raw key like
     * {@code config.allowed-containers} on any server whose lang.yml predates it.
     * The single-argument getter is the one that consults the defaults.
     */
    private String lookup(String key) {
        String text = lang.getString(key);
        return text != null ? text : key;
    }
}
