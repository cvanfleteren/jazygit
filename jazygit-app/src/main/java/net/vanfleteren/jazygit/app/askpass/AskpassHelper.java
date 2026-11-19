package net.vanfleteren.jazygit.app.askpass;

import io.vavr.control.Option;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

/**
 * jazygit run by ssh as its {@code SSH_ASKPASS} (ADR 150): it passes what ssh asks to the jazygit that runs ssh,
 * prints the answer for ssh, and exits; with 1 when the answer was refused. It starts no TUI. The native binary runs it
 * when {@code JAZYGIT_ASKPASS} is set; with {@code java -jar}, the launcher jazygit writes runs this class.
 */
public final class AskpassHelper {

    private AskpassHelper() {}

    /**
     * @param args what ssh asks, as its only argument
     */
    public static void main(String[] args) {
        System.exit(run(
                Option.of(System.getenv(AskpassProtocol.VARIABLE)),
                Option.of(System.getenv("SSH_ASKPASS_PROMPT")).getOrElse(""),
                args.length > 0 ? args[0] : "",
                new FileOutputStream(FileDescriptor.out)));
    }

    /**
     * Whether jazygit is to run as the helper: {@code JAZYGIT_ASKPASS} is set.
     */
    public static boolean asked() {
        return System.getenv(AskpassProtocol.VARIABLE) != null;
    }

    /**
     * Asks the jazygit at {@code variable} what {@code prompt} asks, and prints the answer to {@code out}, with a line
     * break after it, as ssh reads it.
     *
     * @param kind as {@code SSH_ASKPASS_PROMPT} says
     * @return the code to exit with: 0 when answered, 1 otherwise
     */
    static int run(Option<String> variable, String kind, String prompt, OutputStream out) {
        return variable.flatMap(AskpassProtocol.Address::parse)
                .map(address -> {
                    try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                        channel.connect(UnixDomainSocketAddress.of(address.socket()));
                        AskpassProtocol.send(
                                Channels.newOutputStream(channel),
                                new AskpassProtocol.Request(address.token(), kind, prompt));
                        return print(AskpassProtocol.received(Channels.newInputStream(channel)), out);
                    } catch (IOException e) {
                        return 1;
                    }
                })
                .getOrElse(1);
    }

    /**
     * Prints {@code answer}, if any, and wipes it.
     */
    private static int print(Option<byte[]> answer, OutputStream out) throws IOException {
        if (answer.isEmpty()) {
            return 1;
        }
        byte[] bytes = answer.get();
        try {
            out.write(bytes);
            out.write('\n');
            out.flush();
            return 0;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
