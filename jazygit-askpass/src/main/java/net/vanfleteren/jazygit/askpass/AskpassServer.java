package net.vanfleteren.jazygit.askpass;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
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
import net.vanfleteren.jazygit.git.branches.GitAskPass;
import net.vanfleteren.jazygit.process.SshAsking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets ssh ask the user through jazygit (ADR 150). It listens on a Unix domain socket, in a directory of its own that
 * only the user can enter; ssh runs jazygit as its {@code SSH_ASKPASS}, which passes what ssh asks here. It starts
 * listening only for the first operation that lets ssh ask, and stops when the app does, so a session that never talks
 * to a remote leaves nothing behind (ADR 153). Each operation that talks to a remote gets a token of its own, which
 * ties what ssh asks to it, and which is retired once it ends.
 */
public final class AskpassServer implements SshAsking, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AskpassServer.class);
    // Short: the path of a socket may only be about 100 bytes, and the temporary directory of macOS is long already.
    private static final String PREFIX = "jg";
    private static final String SOCKET = "s";
    private static final int TOKEN_BYTES = 32;
    // What SSH_ASKPASS_PROMPT says when ssh only shows something while it waits, as for touching a security key.
    private static final String SHOWN_ONLY = "none";

    // Where the directory of the socket is made, and what writes the helper ssh runs into it.
    private final Path temporary;
    private final Function<Path, Try<Path>> helper;
    // Whether an ssh command, as git runs it in its words, asks through jazygit; asked once per command.
    private final BiPredicate<Path, List<String>> asks;
    // The environment variables jazygit was started with.
    private final Function<String, Option<String>> environment;
    private final ConcurrentMap<List<String>, Boolean> asking = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Operation> operations = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final SecureRandom random = new SecureRandom();
    // Who asks the user; nobody until the app is there.
    private volatile Asking user = Asking.NOBODY;
    // What listens, once the first operation that lets ssh ask started it, or why it could not; guarded by this.
    private Option<Try<Listening>> listening = Option.none();
    // Whether the server stopped; guarded by this.
    private boolean closed;

    private AskpassServer(
            Path temporary,
            Function<Path, Try<Path>> helper,
            BiPredicate<Path, List<String>> asks,
            Function<String, Option<String>> environment) {
        this.temporary = temporary;
        this.helper = helper;
        this.asks = asks;
        this.environment = environment;
    }

    /**
     * A server that listens, once ssh may ask, in a new directory in {@code temporary}, with jazygit itself as the
     * helper ssh runs: the native binary, or a launcher written into the directory that runs this {@code java} on
     * this class path.
     */
    public static AskpassServer in(Path temporary) {
        return in(temporary, AskpassLauncher::of, OpenSsh::asks, name -> Option.of(System.getenv(name)));
    }

    /**
     * A server that listens, once ssh may ask, in a new directory in {@code temporary}, with the helper {@code helper}
     * gives for the directory, letting the ssh commands ask that {@code asks} says do, with jazygit started with the
     * variables {@code environment} gives.
     */
    static AskpassServer in(
            Path temporary,
            Function<Path, Try<Path>> helper,
            BiPredicate<Path, List<String>> asks,
            Function<String, Option<String>> environment) {
        return new AskpassServer(temporary, helper, asks, environment);
    }

    /**
     * What listens: started the first time it is asked for, and then the same until the server stops; a failure is
     * kept, so that it is tried once. Once the server stopped, nothing listens again.
     */
    private synchronized Try<Listening> listening() {
        if (closed) {
            return Try.failure(new IllegalStateException("The askpass server stopped"));
        }
        if (listening.isEmpty()) {
            listening = Option.of(Listening.start(temporary, helper)
                    .peek(started -> Thread.ofVirtual().name("askpass").start(() -> accept(started.server())))
                    .onFailure(failed ->
                            LOG.debug("ssh cannot ask through jazygit: the socket could not be opened", failed)));
        }
        return listening.get();
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

    /**
     * A server listening on {@code socket}; one that cannot bind it is closed again.
     */
    private static Try<ServerSocketChannel> listen(Path socket) {
        return Try.of(() -> ServerSocketChannel.open(StandardProtocolFamily.UNIX))
                .flatMap(server -> Try.of(() -> server.bind(UnixDomainSocketAddress.of(socket)))
                        .onFailure(failed -> Try.run(server::close)));
    }

    /**
     * Has {@code asking} ask the user from now on.
     */
    public void answeredBy(Asking asking) {
        this.user = asking;
    }

    @Override
    public <T> Asked<T> asking(Path directory, Function<Map<String, String>, T> talk) {
        Option<String> configured = BatchSsh.configured(directory, environment.apply("GIT_SSH_COMMAND"));
        // As git picks it: GIT_SSH_COMMAND or core.sshCommand, as words a shell splits; else GIT_SSH, one program;
        // else ssh.
        List<String> ssh = configured
                .map(OpenSsh::words)
                .orElse(() -> environment
                        .apply("GIT_SSH")
                        .filter(program -> !program.isBlank())
                        .map(List::of))
                .getOrElse(List.of("ssh"));
        Option<Listening> listens = Option.when(
                        asking.computeIfAbsent(ssh, command -> asks.test(directory, command)), this::listening)
                .flatMap(Try::toOption);
        if (listens.isEmpty()) {
            return Asked.of(talk.apply(BatchSsh.environment(configured)));
        }
        String token = HexFormat.of().formatHex(bytes());
        Operation operation = new Operation();
        operations.put(token, operation);
        try {
            return new Asked<>(talk.apply(environment(token, directory, listens.get())), operation.cancelled());
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
     * The variables that have ssh ask through jazygit, for the operation of {@code token} in {@code directory}, over
     * what {@code listens}. Git
     * asks for what it asks itself, as a user name over https, through {@code GIT_ASKPASS}, else {@code core.askPass},
     * else {@code SSH_ASKPASS}, which is now jazygit's helper: {@code GIT_ASKPASS} is set to what the user had in that
     * order, and to nothing when they had none, which keeps git from asking.
     */
    private Map<String, String> environment(String token, Path directory, Listening listens) {
        return HashMap.of(
                "SSH_ASKPASS",
                listens.helper().toString(),
                "SSH_ASKPASS_REQUIRE",
                "force",
                AskpassProtocol.VARIABLE,
                new AskpassProtocol.Address(token, listens.socket()).variable(),
                "GIT_ASKPASS",
                environment
                        .apply("GIT_ASKPASS")
                        .orElse(() -> GitAskPass.configured(directory))
                        .orElse(() -> environment.apply("SSH_ASKPASS"))
                        .getOrElse(""));
    }

    /**
     * Takes the connections of the helpers to {@code server}, each on a thread of its own, until it is closed.
     */
    private void accept(ServerSocketChannel server) {
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
        Try.withResources(() -> connection)
                .of(open -> AskpassProtocol.request(Channels.newInputStream(open))
                        .flatMap(request -> answer(
                                Option.of(operations.get(request.token())), request, Channels.newOutputStream(open))))
                .flatMap(answered -> answered)
                .onFailure(failed -> LOG.debug("A helper went away", failed));
    }

    /**
     * Answers {@code request} of {@code operation}: one ssh only shows while it waits at once with nothing; a secret,
     * as the user does, but for an operation the user cancelled something of already; anything else, or for no
     * operation that runs, not at all.
     */
    private Try<Void> answer(Option<Operation> operation, AskpassProtocol.Request request, OutputStream out) {
        if (operation.isEmpty()) {
            return AskpassProtocol.refuse(out);
        }
        if (request.kind().equals(SHOWN_ONLY)) {
            return AskpassProtocol.answer(out, new byte[0]);
        }
        return !request.kind().isEmpty() || operation.get().cancelled().isDefined()
                ? AskpassProtocol.refuse(out)
                : asked(operation.get(), request.prompt(), out);
    }

    /**
     * Asks the user {@code prompt}, for {@code operation}, and answers with what they typed, wiped once it is written;
     * refuses once they cancelled, and the rest of the operation with it.
     */
    private Try<Void> asked(Operation operation, String prompt, OutputStream out) {
        long request = requests.incrementAndGet();
        if (!operation.asking(request)) {
            return AskpassProtocol.refuse(out);
        }
        Option<SecretBuffer> typed = user.ask(request, prompt).join();
        operation.answered(request, typed.isEmpty() ? Option.of(new Cancelled(prompt)) : Option.none());
        return typed.fold(() -> AskpassProtocol.refuse(out), buffer -> {
            byte[] answer = buffer.utf8();
            buffer.clear();
            return AskpassProtocol.answer(out, answer).andFinally(() -> Arrays.fill(answer, (byte) 0));
        });
    }

    /**
     * Stops listening, if it did: what is asked is withdrawn, and the socket and its directory are removed. Nothing
     * listens after.
     */
    @Override
    public void close() {
        Option<Listening> stopped;
        synchronized (this) {
            closed = true;
            stopped = listening.flatMap(Try::toOption);
        }
        stopped.forEach(started -> Try.run(started.server()::close));
        // The app may be gone already, and its render thread with it.
        List.ofAll(operations.values())
                .forEach(operation -> operation.end().forEach(request -> Try.run(() -> user.withdraw(request))));
        stopped.forEach(started -> delete(started.directory()));
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
     * The socket's path, once it listens, for the tests.
     */
    synchronized Option<Path> socket() {
        return listening.flatMap(Try::toOption).map(Listening::socket);
    }

    /**
     * What listens: the socket, in a directory of its own, and the helper ssh runs, written into it if it is a
     * launcher.
     */
    private record Listening(Path directory, Path socket, Path helper, ServerSocketChannel server) {

        /**
         * Listens in a new directory in {@code temporary}, with the helper {@code helper} gives for it; what was made
         * is removed again when something fails.
         */
        static Try<Listening> start(Path temporary, Function<Path, Try<Path>> helper) {
            return Try.of(() -> Files.createTempDirectory(temporary, PREFIX, ownerOnly()))
                    .flatMap(directory -> listen(directory.resolve(SOCKET))
                            .flatMap(server -> helper.apply(directory)
                                    .map(launcher ->
                                            new Listening(directory, directory.resolve(SOCKET), launcher, server))
                                    .onFailure(failed -> Try.run(server::close)))
                            .onFailure(failed -> delete(directory)));
        }
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
