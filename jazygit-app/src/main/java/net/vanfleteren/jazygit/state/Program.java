package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The runtime around {@link Update}: holds the current {@link Model}, performs the commands
 * {@link Update} asks for on the {@code io} executor, and feeds their outcome back as messages on
 * the {@code ui} executor.
 *
 * <p>The model, and the command started last per {@link Cmd.Slot slot}, are only read and replaced on the
 * {@code ui} executor's thread, so they need no synchronisation. {@link #dispatch(Msg)} and {@link #model()} must
 * be called on that thread.
 */
public final class Program {

    private final Env env;
    private final Executor io;
    private final Executor background;
    private final Executor ui;
    private final Consumer<Throwable> bugs;
    // The command started last for each slot, to cancel when a newer one comes.
    private final Map<Cmd.Slot, Future<Void>> latest = new EnumMap<>(Cmd.Slot.class);
    private Model model;

    private Program(Env env, Executor io, Executor background, Executor ui, Consumer<Throwable> bugs) {
        this.env = env;
        this.io = io;
        this.background = background;
        this.ui = ui;
        this.bugs = bugs;
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
        return start(env, SettingsStore.Loaded.DEFAULT, Option.none(), Landing.START, io, background, ui, bug -> {});
    }

    /**
     * Creates the program and starts its initial loads.
     *
     * @param settings      the settings the session starts with
     * @param themeOverride the theme for this session instead of the saved one, if any
     * @param landing       where the repository is shown at first
     * @param bugs          told about every command that threw, which the user is only shown the message of
     */
    public static Program start(
            Env env,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            Landing landing,
            Executor io,
            Executor background,
            Executor ui,
            Consumer<Throwable> bugs) {
        Program program = new Program(env, io, background, ui, bugs);
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
        apply(Update.update(model, msg));
    }

    private void apply(Next next) {
        model = next.model();
        next.cmds().forEach(this::launch);
    }

    /**
     * Performs {@code cmd} on its executor. One that takes a slot cancels the one that took it before, if that
     * one has not started yet: it is skipped, and reports nothing.
     */
    private void launch(Cmd cmd) {
        FutureTask<Void> task = new FutureTask<>(
                () -> {
                    Msg result = perform(env, cmd, bugs);
                    Try.run(() -> ui.execute(() -> dispatch(result)));
                },
                null);
        cmd.slot().forEach(slot -> Option.of(latest.put(slot, task)).forEach(older -> older.cancel(false)));
        // Once the app stops, the executors take nothing anymore, and what is left is dropped.
        Try.run(() -> (cmd.background() ? background : io).execute(task));
    }
}
