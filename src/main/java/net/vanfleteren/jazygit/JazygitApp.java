package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.GitInfoProvider;
import net.vanfleteren.jazygit.model.JGitInfoProvider;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Program;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.ContentPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.StatusPanel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for jazygit, a lazygit-style terminal UI for Git built with TamboUI.
 *
 * <p>Rendering never touches git: it only draws the current {@link Model}. Git is read on a
 * background thread by the {@link Program}, which publishes new models on the render thread.
 */
public class JazygitApp extends ToolkitApp {

    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500);

    private final GitInfoProvider provider;
    private final FilesPanel filesPanel = new FilesPanel(msg -> this.program.dispatch(msg));
    // Key handlers run on the render thread, where the program may be used.
    private final BranchesPanel branchesPanel = new BranchesPanel(msg -> this.program.dispatch(msg));
    private final CommitsPanel commitsPanel = new CommitsPanel();
    private ExecutorService io;
    private Program program;

    public JazygitApp(GitInfoProvider provider) {
        this.provider = provider;
    }

    @Override
    protected void onStart() {
        ToolkitRunner runner = runner();
        runner.focusManager().setFocus(FilesPanel.ID);
        io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("git-io").daemon().factory());
        program = Program.start(provider, io, runner::runOnRenderThread);
        runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> program.dispatch(new Msg.Tick())),
                REFRESH_INTERVAL);
    }

    @Override
    protected void onStop() {
        if (io == null) {
            return;
        }
        io.shutdownNow();
        try {
            // Let a running git call finish before main() closes the repository.
            io.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected Element render() {
        Model model = program.model();
        String focusedId = runner().focusManager().focusedId();
        Panel branches = branchesPanel.render(model, focusedId);
        // The highlighted branch lives in the list widget, which knows it once rendered; the
        // content panel then shows the log of that branch.
        branchesPanel.selectionChange(model).ifPresent(program::dispatch);
        return row(
                column(
                        StatusPanel.render(model),
                        filesPanel.render(model, focusedId),
                        branches,
                        commitsPanel.render(model, focusedId))
                        .percent(30),
                ContentPanel.render(program.model(), focusedId, commitsPanel.selectedIndex())
                        .fill());
    }

    /**
     * @param args optional path inside the repository to open; defaults to the current directory
     */
    public static void main(String[] args) throws Exception {
        Path start = (args.length > 0 ? Path.of(args[0]) : Path.of("")).toAbsolutePath();
        JGitInfoProvider provider;
        try {
            provider = new JGitInfoProvider(start);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        try (provider) {
            new JazygitApp(provider).run();
        }
    }
}
