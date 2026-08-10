package cn.mythicland.magicchest;

import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.config.ConfigSupport;
import cn.mythicland.magicchest.api.RefreshPolicy;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Loads and validates MagicChest's current configuration.
 */
@InjectComponent
final class MagicChestSettings {

    private final JavaPlugin plugin;
    private volatile Snapshot snapshot;

    MagicChestSettings(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        reload();
    }

    synchronized void reload() {
        FileConfiguration configuration = ConfigSupport.loadDefault(plugin);
        List<String> intervalOptions = requiredOptions(configuration, "refresh.interval-options", true);
        List<String> dailyOptions = requiredOptions(configuration, "refresh.daily-options", false);
        String customIntervalText = requiredString(configuration, "refresh.custom-interval");
        String customDailyText = requiredString(configuration, "refresh.custom-daily-time");
        Duration customInterval = RefreshPolicy.parseInterval(customIntervalText);
        LocalTime customDailyTime = RefreshPolicy.parseDailyTime(customDailyText);
        ZoneId zone;
        try {
            zone = ZoneId.of(requiredString(configuration, "refresh.time-zone"));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid refresh.time-zone", exception);
        }

        List<String> particleOptions = requiredParticles(configuration, "particle.options");
        int renderDistance = requiredPositiveInt(configuration, "particle.render-distance");
        int particleIntervalTicks = requiredPositiveInt(configuration, "particle.interval-ticks");
        int particleCount = requiredPositiveInt(configuration, "particle.count");
        double hologramViewDistance = requiredPositiveDouble(configuration, "hologram.view-distance");
        double lineSpacing = requiredPositiveDouble(configuration, "hologram.line-spacing");
        double height = requiredPositiveDouble(configuration, "hologram.height");
        snapshot = new Snapshot(
                intervalOptions,
                customInterval,
                dailyOptions,
                customDailyTime,
                zone,
                particleOptions,
                renderDistance,
                particleIntervalTicks,
                particleCount,
                hologramViewDistance,
                lineSpacing,
                height
        );
    }

    Snapshot snapshot() {
        Snapshot value = snapshot;
        if (value == null) throw new IllegalStateException("MagicChest settings are not loaded");
        return value;
    }

    private static List<String> requiredOptions(
            FileConfiguration configuration,
            String path,
            boolean interval
    ) {
        List<String> result = new ArrayList<>(requiredStringList(configuration, path, "option", String::trim));
        if (!result.getLast().equalsIgnoreCase("custom")) {
            throw new IllegalStateException(path + " must end with custom");
        }
        for (String option : result.subList(0, result.size() - 1)) {
            if (interval) RefreshPolicy.parseInterval(option);
            else RefreshPolicy.parseDailyTime(option);
        }
        result.set(result.size() - 1, "custom");
        return List.copyOf(result);
    }

    @SuppressWarnings("SameParameterValue")
    private static List<String> requiredParticles(FileConfiguration configuration, String path) {
        List<String> particles = requiredStringList(
                configuration,
                path,
                "particle",
                text -> text.toUpperCase(Locale.ROOT)
        );
        for (String particle : particles) {
            if (!particle.equals("NONE")) {
                try {
                    Particle.valueOf(particle);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Unknown particle option: " + particle, exception);
                }
            }
        }
        return particles;
    }

    private static List<String> requiredStringList(
            FileConfiguration configuration,
            String path,
            String duplicateLabel,
            UnaryOperator<String> normalizer
    ) {
        Object raw = configuration.get(path);
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalStateException(path + " must be a non-empty list");
        }
        List<String> result = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalStateException(path + " contains a non-string option");
            }
            String normalized = normalizer.apply(text.trim());
            if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(path + " contains duplicate " + duplicateLabel + ": " + normalized);
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    private static String requiredString(FileConfiguration configuration, String path) {
        Object raw = configuration.get(path);
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalStateException(path + " must be a non-empty string");
        }
        return value.trim();
    }

    private static int requiredPositiveInt(FileConfiguration configuration, String path) {
        Object raw = configuration.get(path);
        if (!(raw instanceof Number number)) throw new IllegalStateException(path + " must be a number");
        int value = number.intValue();
        if (value <= 0 || number.doubleValue() != value) {
            throw new IllegalStateException(path + " must be a positive integer");
        }
        return value;
    }

    private static double requiredPositiveDouble(FileConfiguration configuration, String path) {
        Object raw = configuration.get(path);
        if (!(raw instanceof Number number)) throw new IllegalStateException(path + " must be a number");
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalStateException(path + " must be finite and positive");
        }
        return value;
    }

    /**
     * Immutable settings snapshot used by the main-thread domain service.
     */
    record Snapshot(
            List<String> intervalOptions,
            Duration customInterval,
            List<String> dailyOptions,
            LocalTime customDailyTime,
            ZoneId timeZone,
            List<String> particleOptions,
            int particleRenderDistance,
            int particleIntervalTicks,
            int particleCount,
            double hologramViewDistance,
            double hologramLineSpacing,
            double hologramHeight
    ) {

        Snapshot {
            intervalOptions = List.copyOf(intervalOptions);
            dailyOptions = List.copyOf(dailyOptions);
            particleOptions = List.copyOf(particleOptions);
            Objects.requireNonNull(customInterval, "customInterval");
            Objects.requireNonNull(customDailyTime, "customDailyTime");
            Objects.requireNonNull(timeZone, "timeZone");
        }

        String defaultIntervalOption() {
            return intervalOptions.getFirst();
        }

        String defaultDailyOption() {
            return dailyOptions.getFirst();
        }

        String defaultParticle() {
            return particleOptions.getFirst();
        }
    }
}
