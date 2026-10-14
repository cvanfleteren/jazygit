package net.vanfleteren.jazygit.state;

import io.vavr.control.Try;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * The runtime around {@link Update}: holds the current {@link Model}, performs the commands
 * {@link Update} asks for on the {@code io} executor, and feeds their outcome back as messages on
 * the {@code ui} executor.
 *
 * <p>The model is only read and replaced on the {@code ui} executor's thread, so it needs no
 * synchronisation. {@link #dispatch(Msg)} and {@link #model()} must be called on that thread.
 */
public final class Program {

    private final Env env;
    private final Executor io;
    private final Executor background;
    private final Executor ui;
    private final Consumer<Throwable> bugs;
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
     *                   others
     * @param ui         the thread that owns the model, i.e. the render thread
     */
    public static Program start(Env env, Executor io, Executor background, Executor ui) {
        return start(env, io, background, ui, bug -> {
        });
    }

    /**
     * Creates the program and starts its initial loads.
     *
     * @param bugs told about every command that threw, which the user is only shown the message of
     */
    public static Program start(Env env, Executor io, Executor background, Executor ui, Consumer<Throwable> bugs) {
        Program program = new Program(env, io, background, ui, bugs);
        program.apply(Update.init(env.git().repositoryName()));
        return program;
    }

    public Model model() {
        return model;
    }

    public void dispatch(Msg msg) {
        apply(Update.update(model, msg));
    }

    private void apply(Next next) {
        model = next.model();
        // Once the app stops, the executors take nothing anymore, and what is left is dropped.
        next.cmds().forEach(cmd -> Try.run(() -> (cmd.background() ? background : io).execute(() -> {
            Msg result = perform(env, cmd, bugs);
            Try.run(() -> ui.execute(() -> dispatch(result)));
        })));
    }

    static Msg perform(Env env, Cmd cmd) {
        return perform(env, cmd, bug -> {
        });
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
}
