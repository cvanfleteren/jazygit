package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.GitInfoProvider;
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

    private final GitInfoProvider provider;
    private final Executor io;
    private final Executor ui;
    private Model model;

    private Program(GitInfoProvider provider, Executor io, Executor ui) {
        this.provider = provider;
        this.io = io;
        this.ui = ui;
    }

    /**
     * Creates the program and starts its initial loads.
     *
     * @param io where git is called; a single thread keeps git commands from running concurrently
     * @param ui the thread that owns the model, i.e. the render thread
     */
    public static Program start(GitInfoProvider provider, Executor io, Executor ui) {
        Program program = new Program(provider, io, ui);
        program.apply(Update.init(provider.repositoryName()));
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
        for (Cmd cmd : next.cmds()) {
            io.execute(() -> {
                Msg result = perform(provider, cmd);
                ui.execute(() -> dispatch(result));
            });
        }
    }

    static Msg perform(GitInfoProvider provider, Cmd cmd) {
        try {
            return cmd.run(provider);
        } catch (RuntimeException e) {
            return cmd.failed(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
