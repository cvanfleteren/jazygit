package net.vanfleteren.jazygit.app.askpass;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.IOException;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.branches.BatchSsh;
import net.vanfleteren.jazygit.process.SshAsking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets ssh ask the user through jazygit (ADR 150). It listens on a Unix domain socket, in a directory of its own that
 * only the user can enter, from the start of the app until it stops; ssh runs jazygit as its {@code SSH_ASKPASS}, which
 * passes what ssh asks here. Each operation that talks to a remote gets a token of its own, which ties what ssh asks to
 * it, and which is retired once it ends.
 */
public final class AskpassServer implements SshAsking, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AskpassServer.class);
    // Short: the path of a socket may only be about 100 bytes, and the temporary directory of macOS is long already.
    private static final String PREFIX = "jg";
    private static final String SOCKET = "s";
    private static final int TOKEN_BYTES = 32;
    // What SSH_ASKPASS_PROMPT says when ssh only shows something while it waits, as for touching a security key.
    private static final String SHOWN_ONLY = "none";

    private final Path directory;
    private final Path socket;
    private final Path helper;
    private final ServerSocketChannel server;
    // Whether an ssh command, as git runs it, asks through jazygit; asked once per command.
    private final BiPredicate<Path, String> asks;
    private final ConcurrentMap<String, Boolean> asking = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Operation> operations = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final SecureRandom random = new SecureRandom();
    // Who asks the user; nobody until the app is there.
    private volatile Asking user = Asking.NOBODY;

    private AskpassServer(
            Path directory, Path socket, Path helper, ServerSocketChannel server, BiPredicate<Path, String> asks) {
        this.directory = directory;
        this.socket = socket;
        this.helper = helper;
        this.server = server;
        this.asks = asks;
    }

    /**
     * Starts listening, in a new directory in {@code temporary}, with jazygit itself as the helper ssh runs: the native
     * binary, or a launcher written into the directory that runs this {@code java} on this class path.
     */
    public static Try<AskpassServer> start(Path temporary) {
        return start(temporary, AskpassLauncher::of, OpenSsh::asks);
    }

    /**
     * Starts listening, in a new directory in {@code temporary}, with the helper {@code helper} gives for the
     * directory, letting the ssh commands ask that {@code asks} says do.
     */
    static Try<AskpassServer> start(Path temporary, Function<Path, Try<Path>> helper, BiPredicate<Path, String> asks) {
        return Try.of(() -> Files.createTempDirectory(temporary, PREFIX, ownerOnly()))
                .flatMap(directory -> Try.of(() -> listen(directory.resolve(SOCKET)))
                        .flatMap(server -> helper.apply(directory)
                                .map(launcher ->
                                        new AskpassServer(directory, directory.resolve(SOCKET), launcher, server, asks))
                                .onFailure(failed -> Try.run(server::close)))
                        .onFailure(failed -> delete(directory)))
                .peek(started -> Thread.ofVirtual().name("askpass").start(started::accept));
    }

    /**
     * The attributes of a directory only its owner can enter, where the file system has them.
     */
    private static FileAttribute<?>[] ownerOnly() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[] {
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
                }
                : new FileAttribute<?>[0];
    }

    private static ServerSocketChannel listen(Path socket) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            server.bind(UnixDomainSocketAddress.of(socket));
            return server;
        } catch (IOException e) {
            server.close();
            throw e;
        }
    }

    /**
     * Has {@code asking} ask the user from now on.
     */
    public void answeredBy(Asking asking) {
        this.user = asking;
    }

    @Override
    public <T> Asked<T> asking(Path directory, Function<Map<String, String>, T> talk) {
        Option<String> configured = BatchSsh.configured(directory);
        String ssh = configured.getOrElse("ssh");
        if (!asking.computeIfAbsent(ssh, command -> asks.test(directory, command))) {
            return Asked.of(talk.apply(BatchSsh.environment(configured)));
        }
        String token = HexFormat.of().formatHex(bytes());
        Operation operation = new Operation();
        operations.put(token, operation);
        try {
            return new Asked<>(talk.apply(environment(token)), operation.cancelled());
        } finally {
            operations.remove(token);
            operation.end().forEach(request -> user.withdraw(request));
        }
    }

    private byte[] bytes() {
        byte[] token = new byte[TOKEN_BYTES];
        random.nextBytes(token);
        return token;
    }

    /**
     * The variables that have ssh ask through jazygit, for the operation of {@code token}. Git would ask through the
     * same helper for what it asks itself, as a user name over https, when nothing else is set for that: an empty
     * {@code GIT_ASKPASS} keeps it from that, unless the user set one.
     */
    Map<String, String> environment(String token) {
        return HashMap.of(
                "SSH_ASKPASS",
                helper.toString(),
                "SSH_ASKPASS_REQUIRE",
                "force",
                AskpassProtocol.VARIABLE,
                new AskpassProtocol.Address(token, socket).variable(),
                "GIT_ASKPASS",
                Option.of(System.getenv("GIT_ASKPASS")).getOrElse(""));
    }

    /**
     * Takes the connections of the helpers, each on a thread of its own, until the server is closed.
     */
    private void accept() {
        while (server.isOpen()) {
            Try.of(server::accept)
                    .onSuccess(connection ->
                            Thread.ofVirtual().name("askpass-request").start(() -> handle(connection)))
                    .onFailure(failed -> {
                        if (server.isOpen()) {
                            LOG.debug("A helper could not connect", failed);
                        }
                    });
        }
    }

    /**
     * Answers what a helper asks on {@code connection}, as the user does.
     */
    private void handle(SocketChannel connection) {
        try (connection) {
            AskpassProtocol.Request request = AskpassProtocol.request(Channels.newInputStream(connection));
            answer(Option.of(operations.get(request.token())), request, Channels.newOutputStream(connection));
        } catch (IOException e) {
            LOG.debug("A helper went away", e);
        }
    }

    /**
     * Answers {@code request} of {@code operation}: one ssh only shows while it waits at once with nothing; a secret,
     * as the user does, but for an operation the user cancelled something of already; anything else, or for no
     * operation that runs, not at all.
     */
    private void answer(Option<Operation> operation, AskpassProtocol.Request request, OutputStream out)
            throws IOException {
        if (operation.isEmpty()) {
            AskpassProtocol.refuse(out);
        } else if (request.kind().equals(SHOWN_ONLY)) {
            AskpassProtocol.answer(out, new byte[0]);
        } else if (!request.kind().isEmpty() || operation.get().cancelled().isDefined()) {
            AskpassProtocol.refuse(out);
        } else {
            asked(operation.get(), request.prompt(), out);
        }
    }

    /**
     * Asks the user {@code prompt}, for {@code operation}, and answers with what they typed, wiped once it is written;
     * refuses once they cancelled, and the rest of the operation with it.
     */
    private void asked(Operation operation, String prompt, OutputStream out) throws IOException {
        long request = requests.incrementAndGet();
        if (!operation.asking(request)) {
            AskpassProtocol.refuse(out);
            return;
        }
        Option<SecretBuffer> typed = user.ask(request, prompt).join();
        operation.answered(request, typed.isEmpty() ? Option.of(new Cancelled(prompt)) : Option.none());
        if (typed.isEmpty()) {
            AskpassProtocol.refuse(out);
            return;
        }
        byte[] answer = typed.get().utf8();
        typed.get().clear();
        try {
            AskpassProtocol.answer(out, answer);
        } finally {
            Arrays.fill(answer, (byte) 0);
        }
    }

    /**
     * Stops listening: what is asked is withdrawn, and the socket and its directory are removed.
     */
    @Override
    public void close() {
        Try.run(server::close);
        // The app may be gone already, and its render thread with it.
        List.ofAll(operations.values())
                .forEach(operation -> operation.end().forEach(request -> Try.run(() -> user.withdraw(request))));
        delete(directory);
    }

    /**
     * Removes {@code directory} and what is in it; what cannot be removed stays.
     */
    private static void delete(Path directory) {
        Try.withResources(() -> Files.list(directory))
                .of(files -> List.ofAll(files.toList()))
                .getOrElse(List.empty())
                .forEach(file -> Try.run(() -> Files.deleteIfExists(file)));
        Try.run(() -> Files.deleteIfExists(directory));
    }

    /**
     * The socket's path, for the tests.
     */
    Path socket() {
        return socket;
    }

    /**
     * An operation that talks to a remote, while it runs: what it asks the user now, and what the user cancelled.
     */
    private static final class Operation {

        private Option<Long> asking = Option.none();
        private Option<Cancelled> cancelled = Option.none();
        private boolean ended;

        /**
         * Starts asking {@code request}; false once the operation ended.
         */
        synchronized boolean asking(long request) {
            if (!ended) {
                asking = Option.of(request);
            }
            return !ended;
        }

        /**
         * {@code request} was answered: {@code cancelled}, if the user cancelled it before the operation ended.
         */
        synchronized void answered(long request, Option<Cancelled> cancelled) {
            if (asking.contains(request)) {
                asking = Option.none();
                if (!ended) {
                    this.cancelled = this.cancelled.orElse(cancelled);
                }
            }
        }

        synchronized Option<Cancelled> cancelled() {
            return cancelled;
        }

        /**
         * Ends the operation: what it still asks, if anything, is to be withdrawn.
         */
        synchronized Option<Long> end() {
            ended = true;
            Option<Long> withdrawn = asking;
            asking = Option.none();
            return withdrawn;
        }
    }
}
