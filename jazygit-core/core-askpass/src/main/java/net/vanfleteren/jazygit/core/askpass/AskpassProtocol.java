package net.vanfleteren.jazygit.core.askpass;

import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * What the helper and the running jazygit say to each other over the socket (ADR 150). The helper sends a
 * {@link Request}; jazygit answers with what the user typed, or refuses. Each step is a {@link Try}, failed when the
 * other end went away.
 */
final class AskpassProtocol {

    /**
     * The variable that runs jazygit as the helper, set to the {@link Address} of the jazygit that asks.
     */
    static final String VARIABLE = "JAZYGIT_ASKPASS";

    // The most an answer may be: far more than any passphrase.
    static final int MAX_ANSWER = 64 * 1024;
    private static final byte ANSWERED = 0;
    private static final byte REFUSED = 1;

    private AskpassProtocol() {}

    /**
     * Sends {@code request}.
     */
    static Try<Void> send(OutputStream out, Request request) {
        return Try.run(() -> {
            DataOutputStream data = new DataOutputStream(out);
            data.writeUTF(request.token());
            data.writeUTF(request.kind());
            data.writeUTF(request.prompt());
            data.flush();
        });
    }

    /**
     * Reads a request.
     */
    static Try<Request> request(InputStream in) {
        DataInputStream data = new DataInputStream(in);
        return Try.of(() -> new Request(data.readUTF(), data.readUTF(), data.readUTF()));
    }

    /**
     * Answers with {@code answer}.
     */
    static Try<Void> answer(OutputStream out, byte[] answer) {
        return Try.run(() -> {
            DataOutputStream data = new DataOutputStream(out);
            data.writeByte(ANSWERED);
            data.writeInt(answer.length);
            data.write(answer);
            data.flush();
        });
    }

    /**
     * Refuses to answer.
     */
    static Try<Void> refuse(OutputStream out) {
        return Try.run(() -> {
            DataOutputStream data = new DataOutputStream(out);
            data.writeByte(REFUSED);
            data.flush();
        });
    }

    /**
     * The answer, which the caller wipes once it is written; none when it was refused. An answer longer than
     * {@link #MAX_ANSWER} fails.
     */
    static Try<Option<byte[]>> received(InputStream in) {
        DataInputStream data = new DataInputStream(in);
        return Try.of(data::readByte)
                .flatMap(status -> status != ANSWERED
                        ? Try.success(Option.none())
                        : Try.of(data::readInt).flatMap(length -> read(data, length)));
    }

    /**
     * The {@code length} bytes of an answer.
     */
    private static Try<Option<byte[]>> read(DataInputStream data, int length) {
        if (length < 0 || length > MAX_ANSWER) {
            return Try.failure(new IOException("An answer of " + length + " bytes"));
        }
        byte[] answer = new byte[length];
        return Try.run(() -> data.readFully(answer)).map(read -> Option.of(answer));
    }

    /**
     * What ssh asked, sent to the jazygit of {@code token}.
     *
     * @param token  the token of the operation that runs ssh
     * @param kind   what ssh says it wants, as {@code SSH_ASKPASS_PROMPT} has it: empty for a secret, {@code confirm}
     *               for a yes, {@code none} for nothing
     * @param prompt what ssh asked, as it asked it
     */
    record Request(String token, String kind, String prompt) {}

    /**
     * Where the helper finds the jazygit that asks, and the token of the operation, as {@link #VARIABLE} has them:
     * the token, a space, and the path of the socket.
     */
    record Address(String token, Path socket) {

        /**
         * The address {@code variable} has; none for one that has no token or no socket.
         */
        static Option<Address> parse(String variable) {
            int space = variable.indexOf(' ');
            return Option.when(
                    space > 0 && space < variable.length() - 1,
                    () -> new Address(variable.substring(0, space), Path.of(variable.substring(space + 1))));
        }

        /**
         * The value of {@link #VARIABLE}.
         */
        String variable() {
            return token + " " + socket;
        }
    }
}
