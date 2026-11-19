package net.vanfleteren.jazygit.core.askpass;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * What the user typed for ssh, such as a passphrase, as characters that are wiped once they are no longer needed
 * (ADR 150): never a {@code String}, which cannot be wiped, and never in the model. Only touched on one thread at a
 * time: the render thread while it is typed, then the thread that hands it to ssh.
 */
public final class SecretBuffer {

    private static final int START = 32;

    private char[] chars = new char[START];
    private int length;

    /**
     * Adds the character {@code codePoint} at the end. An array that is too small is replaced by a larger one, and
     * wiped.
     */
    public void type(int codePoint) {
        int needed = Character.charCount(codePoint);
        if (length + needed > chars.length) {
            char[] larger = Arrays.copyOf(chars, Math.max(chars.length * 2, length + needed));
            Arrays.fill(chars, '\0');
            chars = larger;
        }
        length += Character.toChars(codePoint, chars, length);
    }

    /**
     * Takes the last character away, both halves of one outside the basic plane.
     */
    public void erase() {
        if (length > 0) {
            int last = Character.isLowSurrogate(chars[length - 1])
                            && length > 1
                            && Character.isHighSurrogate(chars[length - 2])
                    ? 2
                    : 1;
            Arrays.fill(chars, length - last, length, '\0');
            length -= last;
        }
    }

    /**
     * How many characters were typed, for the dots that show them.
     */
    public int typed() {
        return Character.codePointCount(chars, 0, length);
    }

    /**
     * Wipes what was typed.
     */
    public void clear() {
        Arrays.fill(chars, '\0');
        length = 0;
    }

    /**
     * What was typed, encoded in UTF-8, as ssh reads it; the caller wipes the bytes once they are written. The buffers
     * of the encoding are wiped.
     */
    public byte[] utf8() {
        CharsetEncoder encoder = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer encoded = ByteBuffer.allocate((int) Math.ceil(length * (double) encoder.maxBytesPerChar()));
        try {
            encoder.encode(CharBuffer.wrap(chars, 0, length), encoded, true);
            encoder.flush(encoded);
            return Arrays.copyOf(encoded.array(), encoded.position());
        } finally {
            Arrays.fill(encoded.array(), (byte) 0);
        }
    }

    /**
     * Never what was typed.
     */
    @Override
    public String toString() {
        return "****";
    }
}
