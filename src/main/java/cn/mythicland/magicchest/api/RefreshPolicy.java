package cn.mythicland.magicchest.api;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable validated refresh policy.
 *
 * @param mode           scheduling mode
 * @param interval       interval duration, only used by interval mode
 * @param dailyTime      local daily time, only used by daily mode
 */
public record RefreshPolicy(
        RefreshMode mode,
        Duration interval,
        LocalTime dailyTime
) {

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("(\\d+)([smh])");
    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Validates the mode-specific fields.
     */
    public RefreshPolicy {
        mode = Objects.requireNonNull(mode, "mode");
        if (mode == RefreshMode.INTERVAL) {
            interval = Objects.requireNonNull(interval, "interval");
            if (interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("interval must be positive");
            }
            dailyTime = null;
        } else if (mode == RefreshMode.DAILY) {
            dailyTime = Objects.requireNonNull(dailyTime, "dailyTime");
            interval = null;
        } else {
            interval = null;
            dailyTime = null;
        }
    }

    /**
     * Creates an interval policy.
     *
     * @param interval positive duration
     * @return policy
     */
    public static RefreshPolicy interval(Duration interval) {
        return new RefreshPolicy(RefreshMode.INTERVAL, interval, null);
    }

    /**
     * Creates a daily policy.
     *
     * @param dailyTime local time
     * @return policy
     */
    public static RefreshPolicy daily(LocalTime dailyTime) {
        return new RefreshPolicy(RefreshMode.DAILY, null, dailyTime);
    }

    /**
     * Creates an always-refreshing policy.
     *
     * @return always-refreshing policy
     */
    public static RefreshPolicy always() {
        return new RefreshPolicy(RefreshMode.ALWAYS, null, null);
    }

    /**
     * Creates a never-refreshing policy.
     *
     * @return never-refreshing policy
     */
    public static RefreshPolicy never() {
        return new RefreshPolicy(RefreshMode.NEVER, null, null);
    }

    /**
     * Parses the supported compact interval syntax.
     *
     * @param value interval such as {@code 1s}, {@code 5m}, or {@code 1h}
     * @return duration
     * @throws IllegalArgumentException for malformed or overflowing values
     */
    public static Duration parseInterval(String value) {
        String text = Objects.requireNonNull(value, "value").trim();
        Matcher matcher = INTERVAL_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Interval must use 1s, 1m, or 1h format: " + value);
        }
        try {
            long amount = Long.parseLong(matcher.group(1));
            if (amount <= 0L) throw new IllegalArgumentException("Interval amount must be positive");
            long multiplier = switch (matcher.group(2)) {
                case "s" -> 1L;
                case "m" -> 60L;
                case "h" -> 3600L;
                default -> throw new IllegalArgumentException("Unsupported interval unit");
            };
            return Duration.ofSeconds(Math.multiplyExact(amount, multiplier));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Interval is too large: " + value, exception);
        }
    }

    /**
     * Parses the strict daily {@code HH:mm} format.
     *
     * @param value daily time
     * @return parsed time
     */
    public static LocalTime parseDailyTime(String value) {
        String text = Objects.requireNonNull(value, "value").trim();
        try {
            return LocalTime.parse(text, DAILY_FORMAT);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Daily time must use HH:mm format: " + value, exception);
        }
    }

    /**
     * Calculates the next occurrence strictly after the supplied instant.
     *
     * @param now  current instant
     * @param zone daily policy timezone
     * @return next refresh instant
     */
    public Instant nextRefresh(Instant now, ZoneId zone) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
        if (mode == RefreshMode.INTERVAL) return now.plus(interval);
        if (mode == RefreshMode.ALWAYS) return now;
        if (mode == RefreshMode.NEVER) return Instant.MAX;
        ZonedDateTime localNow = now.atZone(zone);
        ZonedDateTime candidate = localNow.with(dailyTime);
        if (!candidate.toInstant().isAfter(now)) candidate = candidate.plusDays(1L);
        return candidate.toInstant();
    }

    /**
     * Returns a compact display value for a menu.
     *
     * @return interval text or daily time
     */
    public String display() {
        if (mode == RefreshMode.DAILY) return dailyTime.format(DAILY_FORMAT);
        if (mode == RefreshMode.ALWAYS) return "始终刷新";
        if (mode == RefreshMode.NEVER) return "永不刷新";
        return formatInterval(interval);
    }

    /**
     * Formats a duration using the configuration syntax instead of Java's {@code PT...} syntax.
     *
     * @param value positive duration
     * @return compact interval such as {@code 2h}, {@code 5m}, or {@code 10s}
     */
    public static String formatInterval(Duration value) {
        Duration duration = Objects.requireNonNull(value, "value");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        long seconds = duration.toSeconds();
        if (seconds % 3600L == 0L) return seconds / 3600L + "h";
        if (seconds % 60L == 0L) return seconds / 60L + "m";
        return seconds + "s";
    }
}
