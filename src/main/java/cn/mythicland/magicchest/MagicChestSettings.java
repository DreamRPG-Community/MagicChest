package cn.mythicland.magicchest;

import cn.mythicland.lib.bootstrap.annotation.ConfigComponent;
import cn.mythicland.lib.config.ConfigValue;
import cn.mythicland.lib.config.ConfigView;
import cn.mythicland.lib.config.ConfigurableComponent;
import cn.mythicland.magicchest.api.RefreshPolicy;
import org.bukkit.Particle;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Loads and validates MagicChest's current configuration.
 */
@ConfigComponent
final class MagicChestSettings implements ConfigurableComponent {

    private volatile Snapshot snapshot;

    private static List<String> requiredOptions(
            List<String> configuredValues,
            String path,
            boolean interval
    ) {
        if (configuredValues == null || configuredValues.isEmpty()) {
            throw new IllegalStateException(path + " must be a non-empty list");
        }
        List<String> result = getStrings(configuredValues, path);
        for (String option : result.subList(0, result.size() - 1)) {
            if (interval) RefreshPolicy.parseInterval(option);
            else RefreshPolicy.parseDailyTime(option);
        }
        result.set(result.size() - 1, "custom");
        return List.copyOf(result);
    }

    @Nonnull
    private static List<String> getStrings(List<String> configuredValues, String path) {
        List<String> result = new ArrayList<>(configuredValues.size());
        Set<String> seen = new HashSet<>();
        for (String value : configuredValues) {
            String normalized = value.trim();
            if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(path + " contains duplicate option: " + normalized);
            }
            result.add(normalized);
        }
        if (!result.getLast().equalsIgnoreCase("custom")) {
            throw new IllegalStateException(path + " must end with custom");
        }
        return result;
    }

    @SuppressWarnings("SameParameterValue")
    private static List<String> requiredParticles(List<String> configuredValues, String path) {
        if (configuredValues == null || configuredValues.isEmpty()) {
            throw new IllegalStateException(path + " must be a non-empty list");
        }
        List<String> particles = new ArrayList<>(configuredValues.size());
        Set<String> seen = new HashSet<>();
        for (String value : configuredValues) {
            String particle = value.toUpperCase(Locale.ROOT);
            if (!seen.add(particle.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(path + " contains duplicate particle: " + particle);
            }
            particles.add(particle);
        }
        for (String particle : particles) {
            if (!particle.equals("NONE")) {
                try {
                    Particle.valueOf(particle);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Unknown particle option: " + particle, exception);
                }
            }
        }
        return List.copyOf(particles);
    }

    @Override
    public synchronized void reload(ConfigView configuration) {
        RawSettings raw = Objects.requireNonNull(configuration, "configuration")
                .bind(RawSettings.class);
        List<String> intervalOptions = requiredOptions(raw.intervalOptions(), "refresh.interval-options", true);
        List<String> dailyOptions = requiredOptions(raw.dailyOptions(), "refresh.daily-options", false);
        Duration customInterval = RefreshPolicy.parseInterval(raw.customInterval());
        LocalTime customDailyTime = RefreshPolicy.parseDailyTime(raw.customDailyTime());
        ZoneId zone;
        try {
            zone = ZoneId.of(raw.timeZone());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid refresh.time-zone", exception);
        }

        List<String> particleOptions = requiredParticles(raw.particleOptions(), "particle.options");
        snapshot = new Snapshot(
                intervalOptions,
                customInterval,
                dailyOptions,
                customDailyTime,
                zone,
                particleOptions,
                raw.particleRenderDistance(),
                raw.particleIntervalTicks(),
                raw.particleCount(),
                raw.hologramViewDistance(),
                raw.hologramLineSpacing(),
                raw.hologramHeight()
        );
    }

    Snapshot snapshot() {
        Snapshot value = snapshot;
        if (value == null) throw new IllegalStateException("MagicChest settings are not loaded");
        return value;
    }

    private record RawSettings(
            @ConfigValue(
                    path = "refresh.interval-options",
                    defaultValue = "1m,5m,10m,30m,60m,custom",
                    nonBlank = true
            )
            List<String> intervalOptions,
            @ConfigValue(
                    path = "refresh.custom-interval",
                    defaultValue = "2h",
                    nonBlank = true
            )
            String customInterval,
            @ConfigValue(
                    path = "refresh.daily-options",
                    defaultValue = "00:00,08:00,12:00,18:00,custom",
                    nonBlank = true
            )
            List<String> dailyOptions,
            @ConfigValue(
                    path = "refresh.custom-daily-time",
                    defaultValue = "20:00",
                    nonBlank = true
            )
            String customDailyTime,
            @ConfigValue(
                    path = "refresh.time-zone",
                    defaultValue = "Asia/Shanghai",
                    nonBlank = true
            )
            String timeZone,
            @ConfigValue(
                    path = "particle.options",
                    defaultValue = "none,VILLAGER_HAPPY",
                    nonBlank = true
            )
            List<String> particleOptions,
            @ConfigValue(
                    path = "particle.render-distance",
                    defaultValue = "32",
                    positive = true
            )
            int particleRenderDistance,
            @ConfigValue(
                    path = "particle.interval-ticks",
                    defaultValue = "10",
                    positive = true
            )
            int particleIntervalTicks,
            @ConfigValue(
                    path = "particle.count",
                    defaultValue = "12",
                    positive = true
            )
            int particleCount,
            @ConfigValue(
                    path = "hologram.view-distance",
                    defaultValue = "32.0",
                    positive = true
            )
            double hologramViewDistance,
            @ConfigValue(
                    path = "hologram.line-spacing",
                    defaultValue = "0.25",
                    positive = true
            )
            double hologramLineSpacing,
            @ConfigValue(
                    path = "hologram.height",
                    defaultValue = "2.0",
                    positive = true
            )
            double hologramHeight
    ) {
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
