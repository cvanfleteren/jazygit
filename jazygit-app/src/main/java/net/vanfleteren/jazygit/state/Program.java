package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import io.vavr.control.Try;
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
import net.vanfleteren.jazygit.state.Update.Next;
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
    // The command started last for each slot, to cancel when a newer one comes.
    private final Map<Cmd.Slot, Future<Void>> latest = new EnumMap<>(Cmd.Slot.class);
    // What a command that names a guard holds while it runs, so another one that names it waits.
    private final Map<Cmd.Guard, Lock> guards = new EnumMap<>(Cmd.Guard.class);
    private Model model;

    private Program(
            Env env, Executor io, Executor background, Executor ui, Consumer<Throwable> bugs, Runnable modelChanged) {
        this.env = env;
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
     * @param env        what the commands run against
     * @param io         where git is called; a single thread keeps git commands from running concurrently
     * @param background where {@link Cmd#background() background} commands run, so they do not hold up the
     *                   others; several at once may
     * @param ui         the thread that owns the model, i.e. the render thread
     */
    public static Program start(Env env, Executor io, Executor background, Executor ui) {
        return start(
                env,
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                Landing.START,
                io,
                background,
                ui,
                bug -> {},
                () -> {});
    }

    /**
     * Creates the program and starts its initial loads.
     *
     * @param settings      the settings the session starts with
     * @param themeOverride the theme for this session instead of the saved one, if any
     * @param landing       where the repository is shown at first
     * @param bugs          told about every command that threw, which the user is only shown the message of
     * @param modelChanged  told on the {@code ui} executor's thread whenever a message leaves a model that draws
     *                      differently from the one before ({@link Model#drawn()}), starting with the first; the
     *                      screen has to be drawn again
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
            Runnable modelChanged) {
        Program program = new Program(env, io, background, ui, bugs, modelChanged);
        program.apply(Update.init(env.git().repositoryName(), settings, themeOverride, landing));
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
        // Only the type: a message can hold a whole repository's commits. The clock's ticks come too often to tell.
        if (LOG.isDebugEnabled() && !(msg instanceof Msg.Tick || msg instanceof Msg.Clock)) {
            LOG.debug(msg.getClass()
                    .getName()
                    .substring(msg.getClass().getPackageName().length() + 1));
        }
        apply(Update.update(model, msg));
    }

    private void apply(Next next) {
        Model before = model;
        model = next.model();
        if (before == null || !model.drawn().equals(before.drawn())) {
            modelChanged.run();
        }
        next.cmds().forEach(this::launch);
    }

    /**
     * Performs {@code cmd} on its executor, once it holds its guard, if it names one. One that takes a slot cancels
     * the one that took it before, if that one has not started yet: it is skipped, and reports nothing.
     */
    private void launch(Cmd cmd) {
        Option<Lock> guard = cmd.guard().map(guards::get);
        FutureTask<Void> task = new FutureTask<>(
                () -> {
                    Msg result = guarded(guard, () -> perform(env, cmd, bugs));
                    Try.run(() -> ui.execute(() -> dispatch(result)));
                },
                null);
        cmd.slot().forEach(slot -> Option.of(latest.put(slot, task)).forEach(older -> older.cancel(false)));
        // Once the app stops, the executors take nothing anymore, and what is left is dropped.
        Try.run(() -> (cmd.background() ? background : io).execute(task));
    }

    private static Msg guarded(Option<Lock> guard, Supplier<Msg> perform) {
        guard.forEach(Lock::lock);
        try {
            return perform.get();
        } finally {
            guard.forEach(Lock::unlock);
        }
    }
}
