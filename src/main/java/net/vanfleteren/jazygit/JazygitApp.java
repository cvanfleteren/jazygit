package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.TuiConfig;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.JGitInfoProvider;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Program;
import net.vanfleteren.jazygit.feature.commit.AmendDialog;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.feature.help.HelpPopup;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.feature.stage.StageAllDialog;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchDialog;
import net.vanfleteren.jazygit.ui.ContentPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.feature.branch.ForcePushDialog;
import net.vanfleteren.jazygit.feature.branch.NewBranchDialog;
import net.vanfleteren.jazygit.ui.StashPanel;
import net.vanfleteren.jazygit.ui.StatusPanel;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    private final FilesPanel filesPanel = new FilesPanel(msg -> this.program.dispatch(msg),
            () -> ContentPanel.diffFocusId().ifPresent(id -> runner().focusManager().setFocus(id)));
    private final BranchesPanel branchesPanel = new BranchesPanel(msg -> this.program.dispatch(msg));
    private final NewBranchDialog newBranchDialog = new NewBranchDialog(msg -> this.program.dispatch(msg));
    private final DeleteBranchDialog deleteBranchDialog = new DeleteBranchDialog(msg -> this.program.dispatch(msg));
    private final ForcePushDialog forcePushDialog = new ForcePushDialog(msg -> this.program.dispatch(msg));
    private final AmendDialog amendDialog = new AmendDialog(msg -> this.program.dispatch(msg));
    private final CommitDialog rewordDialog = CommitDialog.reword(msg -> this.program.dispatch(msg));
    private final StageAllDialog stageAllDialog = new StageAllDialog(msg -> this.program.dispatch(msg));
    private final CommitDialog commitDialog = new CommitDialog(msg -> this.program.dispatch(msg));
    private final CommitsPanel commitsPanel = new CommitsPanel(msg -> this.program.dispatch(msg));
    private final HelpDialog helpDialog = new HelpDialog(msg -> this.program.dispatch(msg));
    // The panel the help popup was opened from, which gets the focus back.
    private String helpOrigin = FilesPanel.ID;
    private ExecutorService io;
    private Program program;

    public JazygitApp(GitInfoProvider provider) {
        this.provider = provider;
    }

    @Override
    protected void onStart() {
        ToolkitRunner runner = runner();
        runner.focusManager().setFocus(FilesPanel.ID);
        ContentPanel.onLeaveDiff(() -> {
            boolean inDiff = ContentPanel.isDiffId(runner.focusManager().focusedId());
            if (inDiff) {
                runner.focusManager().setFocus(FilesPanel.ID);
            }
            return inDiff;
        });
        io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("git-io").daemon().factory());
        program = Program.start(provider, io, runner::runOnRenderThread);
        runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> program.dispatch(new Msg.Tick())),
                REFRESH_INTERVAL);
    }


    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder().mouseCapture(true).fpsOverlay(true).build();
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
        filesPanel.selectionChange(model).ifPresent(program::dispatch);
        Element layout = row(
                column(
                        StatusPanel.render(model),
                        filesPanel.render(model, focusedId).percent(30),
                        branches.percent(30),
                        commitsPanel.render(model, focusedId).percent(30),
                        // The remaining ~10%, after the fixed-height status panel.
                        StashPanel.render().fill())
                        .percent(30),
                ContentPanel.render(program.model(), focusedId, commitsPanel.selectedIndex())
                        .fill());
        // The open popup takes the focus, and gives it back to the pane it was opened from.
        model.popup(HelpPopup.class).map(HelpPopup::topic).ifPresent(topic -> helpOrigin = switch (topic) {
            case FILES -> FilesPanel.ID;
            case BRANCHES -> BranchesPanel.ID;
            case COMMITS -> CommitsPanel.ID;
        });
        List<Popup> popups = List.of(
                new Popup(newBranchDialog.render(model), List.of(NewBranchDialog.ID), BranchesPanel.ID),
                new Popup(deleteBranchDialog.render(model), List.of(DeleteBranchDialog.ID), BranchesPanel.ID),
                new Popup(forcePushDialog.render(model), List.of(ForcePushDialog.ID), BranchesPanel.ID),
                new Popup(amendDialog.render(model), List.of(AmendDialog.ID), FilesPanel.ID),
                new Popup(stageAllDialog.render(model), List.of(StageAllDialog.ID), FilesPanel.ID),
                new Popup(commitDialog.render(model), commitDialog.ids(), FilesPanel.ID),
                new Popup(rewordDialog.render(model), rewordDialog.ids(), CommitsPanel.ID),
                new Popup(helpDialog.render(model), List.of(HelpDialog.ID), helpOrigin));
        Optional<Popup> open = popups.stream().filter(p -> p.element().isPresent()).findFirst();
        if (open.isPresent()) {
            if (focusedId == null || !open.get().ids().contains(focusedId)) {
                runner().focusManager().setFocus(open.get().ids().getFirst());
            }
        } else {
            popups.stream()
                    .filter(p -> focusedId != null && p.ids().contains(focusedId))
                    .findFirst()
                    .ifPresent(p -> runner().focusManager().setFocus(p.returnTo()));
        }
        Optional<Element> dialog = open.flatMap(Popup::element);
        return dialog.<Element>map(d -> stack(layout, d)).orElse(layout);
    }

    private record Popup(Optional<Element> element, List<String> ids, String returnTo) {
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
