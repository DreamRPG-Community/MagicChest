package cn.mythicland.magicchest;

import cn.mythicland.magicchest.api.RefreshMode;
import cn.mythicland.magicchest.api.RefreshPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers compact interval and daily refresh parsing.
 */
class RefreshPolicyTest {

    @Test
    void parsesSupportedIntervalUnits() {
        assertEquals(Duration.ofSeconds(1), RefreshPolicy.parseInterval("1s"));
        assertEquals(Duration.ofMinutes(5), RefreshPolicy.parseInterval("5m"));
        assertEquals(Duration.ofHours(1), RefreshPolicy.parseInterval("1h"));
    }

    @Test
    void rejectsMalformedIntervalAndDailyValues() {
        assertThrows(IllegalArgumentException.class, () -> RefreshPolicy.parseInterval("0m"));
        assertThrows(IllegalArgumentException.class, () -> RefreshPolicy.parseInterval("1d"));
        assertThrows(IllegalArgumentException.class, () -> RefreshPolicy.parseDailyTime("24:00"));
        assertThrows(IllegalArgumentException.class, () -> RefreshPolicy.parseDailyTime("9:00"));
    }

    @Test
    void dailyPolicyMovesToTheNextLocalOccurrence() {
        RefreshPolicy policy = RefreshPolicy.daily(LocalTime.of(13, 0));
        Instant now = Instant.parse("2026-08-08T04:30:00Z");

        assertEquals(
                Instant.parse("2026-08-08T05:00:00Z"),
                policy.nextRefresh(now, ZoneId.of("Asia/Shanghai"))
        );
        assertEquals(RefreshMode.DAILY, policy.mode());
    }

    @Test
    void formatsDurationsWithTheConfiguredCompactSyntax() {
        assertEquals("2h", RefreshPolicy.formatInterval(Duration.ofHours(2)));
        assertEquals("5m", RefreshPolicy.formatInterval(Duration.ofMinutes(5)));
        assertEquals("90s", RefreshPolicy.formatInterval(Duration.ofSeconds(90)));
    }

    @Test
    void alwaysAndNeverPoliciesHaveExplicitSchedulingSemantics() {
        Instant now = Instant.parse("2026-08-08T04:30:00Z");

        RefreshPolicy always = RefreshPolicy.always();
        assertEquals(RefreshMode.ALWAYS, always.mode());
        assertEquals(now, always.nextRefresh(now, ZoneId.of("Asia/Shanghai")));

        RefreshPolicy never = RefreshPolicy.never();
        assertEquals(RefreshMode.NEVER, never.mode());
        assertEquals(Instant.MAX, never.nextRefresh(now, ZoneId.of("Asia/Shanghai")));
        assertFalse(never.display().isBlank());
    }
}
