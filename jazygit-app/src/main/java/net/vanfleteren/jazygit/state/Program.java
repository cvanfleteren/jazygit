package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Update.Launch;
import net.vanfleteren.jazygit.state.Update.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The runtime around {@link Update}: holds the current {@link Model}, performs the commands
 * {@link Update} asks for on the {@code io} executor, and feeds their outcome back as messages on
 * the {@code ui} executor.
 *
 * <p>The model, and the command started last per {@link Cmd.Slot slot}, are only read and replaced on the
 * {@code ui} executor's thread, so they need no synchronisation. {@link #dispatch(Msg)} and {@link #model()} must
 * be called on that thread. The lock of each {@link Cmd.Guard guard} is taken on the thread that performs the
 * command.
 */
public final class Program {

    // The messages handled, for the debug log.
    private static final Logger LOG = LoggerFactory.getLogger(Program.class);

    private final Env env;
    private final Executor io;
    private final Executor background;
    private final Executor ui;
    private final Consumer<Throwable> bugs;
    private final Runnable modelChanged;
    private final Runnable quit;
    private final Clock clock;
    // The command started last for each slot, to cancel when a newer one comes.
    private final Map<Cmd.Slot, Tuple2<Launch, Future<Void>>> latest = new EnumMap<>(Cmd.Slot.class);
    // What a command that names a guard holds while it runs, so another one that names it waits.
    private final Map<Cmd.Guard, Lock> guards = new EnumMap<>(Cmd.Guard.class);
    // Set from the ui thread, read by the commands waiting for a guard.
    private volatile boolean stopped;
    private Model model;

    private Program(
            Env env,
            Executor io,
            Executor background,
            Executor ui,
            Consumer<Throwable> bugs,
            Runnable modelChanged,
            Runnable quit,
            Clock clock) {
        this.env = env;
        this.quit = quit;
        this.clock = clock;
        this.io = io;
        this.background = background;
        this.ui = ui;
        this.bugs = bugs;
        this.modelChanged = modelChanged;
        for (Cmd.Guard guard : Cmd.Guard.values()) {
            guards.put(guard, new ReentrantLock());
        }
    }

    /**
     * Creates the program and starts its initial loads.
     *
     * @param env           what the commands run against
     * @param settings      the settings the session starts with
     * @param themeOverride the theme for this session instead of the saved one, if any
     * @param landing       where the repository is shown at first
     * @param io            where git is called; a single thread keeps git commands from running concurrently
     * @param background    where {@link Cmd#background() background} commands run, so they do not hold up the
     *                      others; several at once may
     * @param ui            the thread that owns the model, i.e. the render thread
     * @param bugs          told about every command that threw, which the user is only shown the message of
     * @param modelChanged  told on the {@code ui} executor's thread whenever a message leaves a model that draws
     *                      differently from the one before ({@link Model#drawn()}), starting with the first; the
     *                      screen has to be drawn again
     * @param quit          run on the {@code ui} executor's thread once a message leaves a {@link Step} that asks
     *                      the app to stop
     * @param clock         the time the model is told with every message
     */
    public static Program start(
            Env env,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            Landing landing,
            Executor io,
            Executor background,
            Executor ui,
            Consumer<Throwable> bugs,
            Runnable modelChanged,
            Runnable quit,
            Clock clock) {
        Program program = new Program(env, io, background, ui, bugs, modelChanged, quit, clock);
        program.apply(Update.begin(env.git().repositoryName(), settings, themeOverride, landing, clock.instant()));
        return program;
    }

    static Msg perform(Env env, Cmd cmd) {
        return perform(env, cmd, bug -> {});
    }

    static Msg perform(Env env, Cmd cmd, Consumer<Throwable> bugs) {
        try {
            return cmd.run(env);
        } catch (Throwable e) {
            // A bug rather than a git failure, which commands report as a Msg. Still keep the app going, and
            // always answer: a command that never does would keep its load running and its branch busy.
            bugs.accept(e);
            return cmd.failed(new Failure.Bug(e));
        }
    }

    public Model model() {
        return model;
    }

    public void dispatch(Msg msg) {
        Instant now = clock.instant();
        // The clock's ticks come too often to tell.
        if (LOG.isDebugEnabled() && !(msg instanceof Msg.Tick || msg instanceof Msg.Clock)) {
            LOG.debug(described(msg, now));
        }
        apply(Update.step(model, msg, now));
    }

    /**
     * {@code msg} for the debug log, handled at {@code now}: only its type, as a message can hold a whole repository's
     * commits. A command that is done tells which one it was, how long it ran and what it reported.
     */
    String described(Msg msg, Instant now) {
        return msg instanceof Msg.Done(long id, Option<Msg> result)
                ? "Done "
                        + model.jobs()
                                .running()
                                .get(id)
                                .map(running -> type(running.cmd()) + " "
                                        + Duration.between(running.started(), now)
                                                .toMillis() + "ms")
                                .getOrElse("?")
                        + result.map(reported -> " → " + type(reported)).getOrElse("")
                : type(msg);
    }

    private static String type(Object value) {
        return value.getClass()
                .getName()
                .substring(value.getClass().getPackageName().length() + 1)
                .replace('$', '.');
    }

    private void apply(Step next) {
        Model before = model;
        model = next.model();
        if (before == null || !model.drawn().equals(before.drawn())) {
            modelChanged.run();
        }
        next.launches().forEach(this::launch);
        if (next.quit()) {
            quit.run();
        }
    }

    /**
     * Performs the command of {@code launch} on its executor, once it holds its guard, if it names one, and reports
     * it {@link Msg.Done done} with what it reported. One that takes a slot cancels the one that took it before, if
     * that one has not started yet: it is skipped, and reports only {@link Cmd#skipped()}, if anything.
     */
    private void launch(Launch launch) {
        Cmd cmd = launch.cmd();
        Option<Lock> guard = cmd.guard().map(guards::get);
        FutureTask<Void> task = new FutureTask<>(
                () -> guarded(guard, () -> perform(env, cmd, bugs)).forEach(result -> done(launch, Option.of(result))),
                null);
        cmd.slot()
                .flatMap(slot -> Option.of(latest.put(slot, Tuple.of(launch, task))))
                .filter(older -> older._2().cancel(false))
                .map(Tuple2::_1)
                .forEach(older -> done(older, older.cmd().skipped()));
        // Once the app stops, the executors take nothing anymore, and what is left is dropped.
        Try.run(() -> (cmd.background() ? background : io).execute(task));
    }

    /**
     * Reports, on the {@code ui} executor's thread, that the command of {@code launch} is done, with {@code result}.
     */
    private void done(Launch launch, Option<Msg> result) {
        Try.run(() -> ui.execute(() -> dispatch(new Msg.Done(launch.id(), result))));
    }

    /**
     * Stops the commands that wait for a guard: once they hold it, they are skipped, and report nothing. The
     * executors drop what they have queued themselves, but a command that waits for its guard has left their queue.
     */
    public void stop() {
        stopped = true;
    }

    /**
     * What {@code perform} reports, holding {@code guard}, if any; nothing when the program stopped while it waited
     * for it.
     */
    private Option<Msg> guarded(Option<Lock> guard, Supplier<Msg> perform) {
        guard.forEach(Lock::lock);
        try {
            return guard.isDefined() && stopped ? Option.none() : Option.of(perform.get());
        } finally {
            guard.forEach(Lock::unlock);
        }
    }
}
