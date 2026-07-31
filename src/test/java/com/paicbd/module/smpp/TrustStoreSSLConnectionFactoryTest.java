package com.paicbd.module.smpp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustStoreSSLConnectionFactoryTest {

    @TempDir
    static Path tempDir;

    private static String truststorePath;
    private static final String TRUSTSTORE_PASSWORD = "changeit";

    @BeforeAll
    static void generateTestTruststore() throws Exception {
        truststorePath = tempDir.resolve("test-truststore.p12").toString();
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", "test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-storetype", "PKCS12",
                "-keystore", truststorePath,
                "-storepass", TRUSTSTORE_PASSWORD,
                "-validity", "1",
                "-dname", "CN=test"
        );
        pb.redirectErrorStream(true);
        int exitCode = pb.start().waitFor();
        assertEquals(0, exitCode, "keytool failed to generate test truststore");
    }

    @Test
    @DisplayName("Constructor with blank truststore path throws IllegalArgumentException with descriptive message")
    void constructorWithBlankTruststorePathThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TrustStoreSSLConnectionFactory("", TRUSTSTORE_PASSWORD));
        assertEquals("Truststore file path must not be empty when TLS is enabled", ex.getMessage());
    }

    @Test
    @DisplayName("Constructor with null truststore path throws IllegalArgumentException with descriptive message")
    void constructorWithNullTruststorePathThrowsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new TrustStoreSSLConnectionFactory(null, TRUSTSTORE_PASSWORD));
        assertEquals("Truststore file path must not be empty when TLS is enabled", ex.getMessage());
    }

    @Test
    @DisplayName("Constructor with nonexistent truststore file throws RuntimeException naming the missing path")
    void constructorWithNonexistentTruststoreFileThrowsRuntimeException() {
        String missingPath = tempDir.resolve("nonexistent.p12").toString();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> new TrustStoreSSLConnectionFactory(missingPath, TRUSTSTORE_PASSWORD));
        assertTrue(ex.getMessage().contains("Failed to initialize TLS truststore from '"));
        assertTrue(ex.getMessage().contains(missingPath));
    }

    @Test
    @DisplayName("Constructor with wrong truststore password throws RuntimeException naming the truststore path")
    void constructorWithWrongTruststorePasswordThrowsRuntimeException() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> new TrustStoreSSLConnectionFactory(truststorePath, "wrongpassword"));
        assertTrue(ex.getMessage().contains("Failed to initialize TLS truststore from '"));
        assertTrue(ex.getMessage().contains(truststorePath));
    }

    @Test
    @DisplayName("Constructor with valid truststore path and password initializes SSL context successfully")
    void constructorWithValidTruststoreInitializesSslContextSuccessfully() {
        TrustStoreSSLConnectionFactory factory = new TrustStoreSSLConnectionFactory(truststorePath, TRUSTSTORE_PASSWORD);
        assertNotNull(factory);
    }
}
