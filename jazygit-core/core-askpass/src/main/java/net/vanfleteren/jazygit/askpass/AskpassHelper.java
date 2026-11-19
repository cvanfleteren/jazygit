package net.vanfleteren.jazygit.askpass;

import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
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
                .toTry()
                .flatMap(address -> Try.withResources(() -> SocketChannel.open(StandardProtocolFamily.UNIX))
                        .of(channel -> askOver(channel, address, kind, prompt, out))
                        .flatMap(asked -> asked))
                .getOrElse(1);
    }

    /**
     * Asks over {@code channel}, and prints the answer.
     */
    private static Try<Integer> askOver(
            SocketChannel channel, AskpassProtocol.Address address, String kind, String prompt, OutputStream out) {
        return Try.run(() -> channel.connect(UnixDomainSocketAddress.of(address.socket())))
                .flatMap(connected -> AskpassProtocol.send(
                        Channels.newOutputStream(channel), new AskpassProtocol.Request(address.token(), kind, prompt)))
                .flatMap(sent -> AskpassProtocol.received(Channels.newInputStream(channel)))
                .flatMap(answer -> print(answer, out));
    }

    /**
     * Prints {@code answer}, if any, and wipes it: 0 once it is printed, 1 for none.
     */
    private static Try<Integer> print(Option<byte[]> answer, OutputStream out) {
        return answer.fold(
                () -> Try.success(1),
                bytes -> Try.run(() -> {
                            out.write(bytes);
                            out.write('\n');
                            out.flush();
                        })
                        .andFinally(() -> Arrays.fill(bytes, (byte) 0))
                        .map(printed -> 0));
    }
}
