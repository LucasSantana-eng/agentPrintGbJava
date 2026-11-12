package com.gabryelle.agent.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigManagerTest {
    @Test
    void normalizesOriginsAndDropsTrailingSlash() {
        assertEquals("https://example.com:8443",
                ConfigManager.normalizeOrigin("HTTPS://Example.com:8443/"));
    }

    @Test
    void rejectsPathsAndNonHttpSchemes() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.normalizeOrigin("https://example.com/app"));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigManager.normalizeOrigin("file://example.com"));
    }
}
