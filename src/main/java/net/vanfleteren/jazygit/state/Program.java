package net.vanfleteren.jazygit.state;

import io.vavr.control.Try;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.concurrent.Executor;

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
    private Model model;

    private Program(Env env, Executor io, Executor background, Executor ui) {
        this.env = env;
        this.io = io;
        this.background = background;
        this.ui = ui;
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
        Program program = new Program(env, io, background, ui);
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
            Msg result = perform(env, cmd);
            Try.run(() -> ui.execute(() -> dispatch(result)));
        })));
    }

    static Msg perform(Env env, Cmd cmd) {
        try {
            return cmd.run(env);
        } catch (RuntimeException e) {
            // A bug rather than a git failure, which commands report as a Msg; still keep the app going.
            return cmd.failed(new Failure.Bug(e));
        }
    }
}
