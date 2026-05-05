package com.vaultcache;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity test — no Spring context.
 * All business logic is covered by pure Mockito tests in service/aspect packages.
 */
class VaultCacheApplicationTests {

    @Test
    void sanityCheck_javaVersion() {
        assertThat(System.getProperty("java.version")).isNotNull();
    }
}
