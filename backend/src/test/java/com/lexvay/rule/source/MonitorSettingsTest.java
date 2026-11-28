package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** How the monitor introduces itself (RG-2, D-566). */
class MonitorSettingsTest {

    @Test
    void theUserAgentIsTheProductVersionAndTheContact() {
        var settings = new MonitorSettings(" https://lexvay.example.test/monitor ", 30, "1.4.0");

        assertThat(settings.userAgent()).isEqualTo("LexvayMonitor/1.4.0 (+https://lexvay.example.test/monitor)");
        assertThat(settings.hasContactUrl()).isTrue();
        assertThat(settings.minHostInterval().toSeconds()).isEqualTo(30);
    }

    @Test
    void withoutAContactTheAgentStillNamesTheMonitorButTheSchedulerMustNotStart() {
        var settings = new MonitorSettings("", 30, "1.4.0");

        assertThat(settings.userAgent()).isEqualTo("LexvayMonitor/1.4.0");
        assertThat(settings.hasContactUrl()).isFalse();
        assertThat(new MonitorSettings(null, 30, "1.4.0").hasContactUrl()).isFalse();
        assertThatThrownBy(() -> new RuleSourceScheduler(null, settings)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEXVAY_MONITOR_CONTACT_URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ops@example.test", "ftp://example.test/about", "https://example.test/a b", "https://example.test/(about)",
            "https://example.test/\r\nX-Injected: 1", "https://exämple.test/about"})
    void aContactThatCannotBePutInAHeaderIsRefused(String contact) {
        assertThatThrownBy(() -> new MonitorSettings(contact, 30, "1.4.0")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEXVAY_MONITOR_CONTACT_URL");
    }

    @Test
    void aNegativeIntervalIsRefusedAndZeroTurnsTheThrottleOff() {
        assertThatThrownBy(() -> new MonitorSettings("", -1, "1.4.0")).isInstanceOf(IllegalStateException.class);
        assertThat(new MonitorSettings("", 0, "1.4.0").minHostInterval()).isZero();
    }
}
