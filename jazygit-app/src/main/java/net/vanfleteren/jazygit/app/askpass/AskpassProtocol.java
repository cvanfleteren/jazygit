package net.vanfleteren.jazygit.app.askpass;

import io.vavr.control.Option;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * What the helper and the running jazygit say to each other over the socket (ADR 150). The helper sends a
 * {@link Request}; jazygit answers with what the user typed, or refuses.
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
    static void send(OutputStream out, Request request) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeUTF(request.token());
        data.writeUTF(request.kind());
        data.writeUTF(request.prompt());
        data.flush();
    }

    /**
     * Reads a request.
     */
    static Request request(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        return new Request(data.readUTF(), data.readUTF(), data.readUTF());
    }

    /**
     * Answers with {@code answer}.
     */
    static void answer(OutputStream out, byte[] answer) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeByte(ANSWERED);
        data.writeInt(answer.length);
        data.write(answer);
        data.flush();
    }

    /**
     * Refuses to answer.
     */
    static void refuse(OutputStream out) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeByte(REFUSED);
        data.flush();
    }

    /**
     * The answer, which the caller wipes once it is written; none when it was refused.
     */
    static Option<byte[]> received(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        if (data.readByte() != ANSWERED) {
            return Option.none();
        }
        int length = data.readInt();
        if (length < 0 || length > MAX_ANSWER) {
            throw new IOException("An answer of " + length + " bytes");
        }
        byte[] answer = new byte[length];
        data.readFully(answer);
        return Option.of(answer);
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
