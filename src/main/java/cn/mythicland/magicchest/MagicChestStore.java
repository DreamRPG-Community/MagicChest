package cn.mythicland.magicchest;

import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.item.ItemStackArrayCodec;
import cn.mythicland.lib.storage.AtomicYamlStore;
import cn.mythicland.magicchest.api.MagicChestKey;
import cn.mythicland.magicchest.api.MagicChestSize;
import cn.mythicland.magicchest.api.RefreshMode;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns MagicChest's atomic YAML storage and fixed 54-slot item encoding.
 */
@InjectComponent
final class MagicChestStore implements AutoCloseable {

    private static final int VERSION = 1;

    private final LibApi lib;
    private final Logger logger;
    private final Path file;
    private final Map<MagicChestKey, MagicChestRecord> records = new LinkedHashMap<>();
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    MagicChestStore(JavaPlugin plugin, LibApi lib, Logger logger) {
        Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.logger = Objects.requireNonNull(logger, "logger");
        Path dataDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        this.file = dataDirectory.resolve("chests.yml").normalize();
        load();
    }

    private static String requiredString(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("MagicChest data requires a non-empty string: " + path);
        }
        return text.trim();
    }

    private static boolean requiredBoolean(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Boolean result))
            throw new IllegalStateException("MagicChest data requires boolean: " + path);
        return result;
    }

    private static boolean optionalBoolean(ConfigurationSection section) {
        Object value = section.get("refresh.enabled");
        if (value == null) return false;
        if (!(value instanceof Boolean result)) {
            throw new IllegalStateException("MagicChest data requires boolean: refresh.enabled");
        }
        return result;
    }

    private static long requiredLong(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Number number))
            throw new IllegalStateException("MagicChest data requires number: " + path);
        long result = number.longValue();
        if (result < 0L || number.doubleValue() != result) {
            throw new IllegalStateException("MagicChest data requires non-negative integer: " + path);
        }
        return result;
    }

    private static <T extends Enum<T>> T enumValue(
            ConfigurationSection section,
            String path,
            Class<T> type
    ) {
        String value = requiredString(section, path);
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown MagicChest value at " + path + ": " + value, exception);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    Collection<MagicChestRecord> all() {
        return List.copyOf(records.values());
    }

    MagicChestRecord find(MagicChestKey key) {
        return records.get(key);
    }

    void put(MagicChestRecord record) {
        Objects.requireNonNull(record, "record");
        records.put(record.key(), record);
    }

    void remove(MagicChestKey key) {
        records.remove(key);
    }

    void save(Collection<MagicChestRecord> currentRecords) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("MagicChest storage snapshots require the main thread");
        Map<String, Object> snapshot = serialize(currentRecords);
        synchronized (this) {
            lastWrite = lastWrite.handle((ignored, failure) -> {
                        if (failure != null) logger.log(Level.SEVERE, "Previous MagicChest storage write failed", failure);
                        return null;
                    }).thenCompose(ignored -> lib.runAsync(() -> writeSnapshot(snapshot)))
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            logger.log(Level.SEVERE, "Failed to save MagicChest data", unwrap(failure));
                        }
                    });
        }
    }

    @Override
    public void close() {
        CompletableFuture<Void> pending;
        synchronized (this) {
            pending = lastWrite;
        }
        try {
            pending.join();
        } catch (CompletionException exception) {
            logger.log(Level.SEVERE, "MagicChest data did not finish saving", unwrap(exception));
        }
    }

    private void load() {
        try {
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "file.parent"));
            validateFilePath();
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) writeInitialFile();
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(file.toFile());
            Object version = configuration.get("version");
            if (!(version instanceof Number number) || number.intValue() != VERSION) {
                throw new IllegalStateException("MagicChest chests.yml requires version " + VERSION);
            }
            ConfigurationSection section = configuration.getConfigurationSection("chests");
            if (section == null) {
                if (configuration.getKeys(false).size() == 1 && configuration.isConfigurationSection("version")) {
                    section = configuration.createSection("chests");
                } else {
                    throw new IllegalStateException("MagicChest chests.yml requires a chests section");
                }
            }
            Map<MagicChestKey, MagicChestRecord> loaded = new LinkedHashMap<>();
            for (String identifier : section.getKeys(false)) {
                ConfigurationSection chest = section.getConfigurationSection(identifier);
                if (chest == null) throw new IllegalStateException("Chest entry is not an object: " + identifier);
                MagicChestKey key = MagicChestKey.parse(identifier);
                MagicChestRecord record = readRecord(key, chest);
                if (loaded.putIfAbsent(key, record) != null) {
                    throw new IllegalStateException("Duplicate MagicChest key: " + identifier);
                }
            }
            records.clear();
            loaded.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(MagicChestKey::encoded)))
                    .forEach(entry -> records.put(entry.getKey(), entry.getValue()));
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load MagicChest data: " + file, exception);
        }
    }

    private MagicChestRecord readRecord(MagicChestKey key, ConfigurationSection chest) {
        MagicChestSize size = enumValue(chest, "size", MagicChestSize.class);
        RefreshMode mode = enumValue(chest, "refresh.mode", RefreshMode.class);
        String particle = requiredString(chest, "particle").toUpperCase(java.util.Locale.ROOT);
        if (!particle.equals("NONE")) {
            try {
                Particle.valueOf(particle);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unknown MagicChest particle: " + particle, exception);
            }
        }
        return new MagicChestRecord(
                key,
                optionalBoolean(chest),
                size,
                mode,
                requiredString(chest, "refresh.interval-option"),
                requiredString(chest, "refresh.daily-option"),
                particle,
                requiredBoolean(chest, "hologram"),
                requiredBoolean(chest, "editing"),
                requiredLong(chest, "refresh.next-epoch-second"),
                decode(chest, "template"),
                decode(chest, "draft"),
                decode(chest, "live")
        );
    }

    private Map<String, Object> serialize(Collection<MagicChestRecord> currentRecords) {
        Objects.requireNonNull(currentRecords, "currentRecords");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        Map<String, Object> chests = new LinkedHashMap<>();
        currentRecords.stream()
                .sorted(Comparator.comparing(record -> record.key().encoded()))
                .forEach(record -> chests.put(record.key().encoded(), serializeRecord(record)));
        root.put("chests", chests);
        return root;
    }

    private Map<String, Object> serializeRecord(MagicChestRecord record) {
        Map<String, Object> chest = new LinkedHashMap<>();
        chest.put("size", record.size().name());
        Map<String, Object> refresh = new LinkedHashMap<>();
        refresh.put("enabled", record.refreshEnabled());
        refresh.put("mode", record.refreshMode().name());
        refresh.put("interval-option", record.intervalOption());
        refresh.put("daily-option", record.dailyOption());
        refresh.put("next-epoch-second", record.nextRefreshEpochSecond());
        chest.put("refresh", refresh);
        chest.put("particle", record.particle());
        chest.put("hologram", record.hologramEnabled());
        chest.put("editing", record.editing());
        chest.put("template", ItemStackArrayCodec.serialize(record.templateCopy()));
        chest.put("draft", ItemStackArrayCodec.serialize(record.draftCopy()));
        chest.put("live", ItemStackArrayCodec.serialize(record.liveCopy()));
        return chest;
    }

    private ItemStack[] decode(ConfigurationSection section, String path) {
        return ItemStackArrayCodec.deserialize(requiredString(section, path), MagicChestRecord.STORAGE_SIZE);
    }

    private void writeInitialFile() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("chests", new LinkedHashMap<>());
        AtomicYamlStore.write(file, root);
    }

    private void writeSnapshot(Map<String, Object> snapshot) {
        try {
            AtomicYamlStore.write(file, snapshot);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void validateFilePath() {
        if (Files.isSymbolicLink(file))
            throw new IllegalStateException("MagicChest data file is a symbolic link: " + file);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("MagicChest data path is not a regular file: " + file);
        }
    }
}
