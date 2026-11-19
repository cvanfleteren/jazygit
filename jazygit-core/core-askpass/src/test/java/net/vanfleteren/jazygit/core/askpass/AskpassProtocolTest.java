package net.vanfleteren.jazygit.core.askpass;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * What the helper and jazygit say to each other (ADR 150), and how it fails as a value when the other end goes away.
 */
class AskpassProtocolTest {

    @Test
    void aRequestComesAcrossAsItWasSent() {
        AskpassProtocol.Request request = new AskpassProtocol.Request("token", "", "Enter passphrase for key 'k': ");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertTrue(AskpassProtocol.send(out, request).isSuccess());
        assertEquals(request, AskpassProtocol.request(in(out)).get());
    }

    @Test
    void anAnswerComesAcrossAsItWasSent() {
        byte[] answer = "pässword".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertTrue(AskpassProtocol.answer(out, answer).isSuccess());
        assertArrayEquals(answer, AskpassProtocol.received(in(out)).get().get());
    }

    @Test
    void aRefusalIsNoAnswer() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertTrue(AskpassProtocol.refuse(out).isSuccess());
        assertEquals(Option.none(), AskpassProtocol.received(in(out)).get());
    }

    @Test
    void anOtherEndThatWentAwayFails() {
        assertTrue(
                AskpassProtocol.request(new ByteArrayInputStream(new byte[0])).isFailure());
        assertTrue(
                AskpassProtocol.received(new ByteArrayInputStream(new byte[0])).isFailure());
        byte[] cut = Arrays.copyOf(answered(10), 7);
        assertTrue(AskpassProtocol.received(new ByteArrayInputStream(cut)).isFailure());
        assertTrue(AskpassProtocol.refuse(new Closed()).isFailure());
        assertTrue(AskpassProtocol.answer(new Closed(), new byte[1]).isFailure());
    }

    @Test
    void anAnswerTooLongToBeOneFails() {
        Try<Option<byte[]>> received =
                AskpassProtocol.received(new ByteArrayInputStream(answered(AskpassProtocol.MAX_ANSWER + 1)));

        assertTrue(received.isFailure());
        assertTrue(received.getCause() instanceof IOException);
    }

    @Test
    void theAddressHasTheTokenAndTheSocket() {
        AskpassProtocol.Address address = new AskpassProtocol.Address("abc", Path.of("/tmp/jg1/s"));

        assertEquals(Option.of(address), AskpassProtocol.Address.parse(address.variable()));
        assertEquals(Option.none(), AskpassProtocol.Address.parse("abc"));
        assertEquals(Option.none(), AskpassProtocol.Address.parse(" /tmp/jg1/s"));
        assertEquals(Option.none(), AskpassProtocol.Address.parse("abc "));
    }

    private static ByteArrayInputStream in(ByteArrayOutputStream out) {
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * What says an answer of {@code length} bytes is coming, with that many bytes after it.
     */
    private static byte[] answered(int length) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Try.run(() -> {
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(0);
            data.writeInt(length);
            data.write(new byte[Math.max(0, Math.min(length, 16))]);
        });
        return bytes.toByteArray();
    }

    /**
     * A stream that is closed: every write fails.
     */
    private static final class Closed extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            throw new IOException("closed");
        }
    }
}
