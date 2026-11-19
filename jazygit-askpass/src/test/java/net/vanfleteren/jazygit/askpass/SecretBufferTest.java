package net.vanfleteren.jazygit.askpass;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * What the user types for ssh, kept as characters that are wiped, never as a {@code String} (ADR 150).
 */
class SecretBufferTest {

    @Test
    void whatIsTypedComesOutInUtf8() {
        SecretBuffer typed = typed("pässwörd😀");

        assertArrayEquals("pässwörd😀".getBytes(StandardCharsets.UTF_8), typed.utf8());
        assertEquals(9, typed.typed());
    }

    @Test
    void erasingTakesTheLastCharacterAwayBothHalvesOfOneOutsideTheBasicPlane() {
        SecretBuffer typed = typed("ab😀");

        typed.erase();
        assertArrayEquals("ab".getBytes(StandardCharsets.UTF_8), typed.utf8());
        typed.erase();
        typed.erase();
        typed.erase();
        assertEquals(0, typed.typed());
    }

    @Test
    void aLongSecretGrowsTheBuffer() {
        String secret = "x".repeat(100);

        assertArrayEquals(secret.getBytes(StandardCharsets.UTF_8), typed(secret).utf8());
    }

    @Test
    void clearingWipesWhatWasTyped() {
        SecretBuffer typed = typed("secret");

        typed.clear();

        assertEquals(0, typed.typed());
        assertArrayEquals(new byte[0], typed.utf8());
    }

    @Test
    void itNeverSaysWhatWasTyped() {
        assertEquals("****", typed("secret").toString());
    }

    private static SecretBuffer typed(String text) {
        SecretBuffer buffer = new SecretBuffer();
        text.codePoints().forEach(buffer::type);
        return buffer;
    }
}
