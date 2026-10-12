package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchDialog;
import net.vanfleteren.jazygit.feature.branch.DeleteBranchPopup;
import net.vanfleteren.jazygit.feature.branch.ForcePushDialog;
import net.vanfleteren.jazygit.feature.branch.ForcePushPopup;
import net.vanfleteren.jazygit.feature.branch.NewBranchDialog;
import net.vanfleteren.jazygit.feature.branch.RebaseDialog;
import net.vanfleteren.jazygit.feature.branch.RebasePopup;
import net.vanfleteren.jazygit.feature.branch.StashCheckoutDialog;
import net.vanfleteren.jazygit.feature.branch.StashCheckoutPopup;
import net.vanfleteren.jazygit.feature.commit.AmendDialog;
import net.vanfleteren.jazygit.feature.commit.AmendPopup;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.feature.commit.SquashDialog;
import net.vanfleteren.jazygit.feature.commit.SquashPopup;
import net.vanfleteren.jazygit.feature.commit.StageAllPopup;
import net.vanfleteren.jazygit.feature.discard.DiscardDialog;
import net.vanfleteren.jazygit.feature.discard.DiscardPopup;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.feature.help.HelpPopup;
import net.vanfleteren.jazygit.feature.stage.StageAllDialog;
import net.vanfleteren.jazygit.feature.stash.StashDialog;
import net.vanfleteren.jazygit.feature.stash.StashDropDialog;
import net.vanfleteren.jazygit.feature.stash.StashDropPopup;
import net.vanfleteren.jazygit.feature.theme.ThemeDialog;
import net.vanfleteren.jazygit.feature.theme.ThemePopup;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.JGitInfoProvider;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Program;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.CommandLogPanel;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.ContentPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.StashPanel;
import net.vanfleteren.jazygit.ui.StatusPanel;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
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

    // Rows of the command log panel, borders included.
    private static final int COMMAND_LOG_HEIGHT = CommandLogPanel.VISIBLE_LINES + 2;
    private static final int FILES_PERCENT = 25;
    private static final int BRANCHES_PERCENT = 20;
    private static final int COMMITS_PERCENT = 30;
    // A border around a single blank line.
    private static final int MIN_STASH_HEIGHT = 3;
    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500);

    private final GitInfoProvider provider;
    private final FilesPanel filesPanel = new FilesPanel(msg -> this.program.dispatch(msg),
            () -> openContent(FilesPanel.ID));
    private final BranchesPanel branchesPanel = new BranchesPanel(msg -> this.program.dispatch(msg));
    private final NewBranchDialog newBranchDialog = new NewBranchDialog(msg -> this.program.dispatch(msg));
    private final ChoiceDialog<DiscardPopup> discardDialog = DiscardDialog.create(msg -> this.program.dispatch(msg));
    private final ChoiceDialog<ThemePopup> themeDialog = ThemeDialog.create(msg -> this.program.dispatch(msg));
    private final ChoiceDialog<RebasePopup> rebaseDialog = RebaseDialog.create(msg -> this.program.dispatch(msg));
    private final ChoiceDialog<DeleteBranchPopup> deleteBranchDialog = DeleteBranchDialog.create(msg -> this.program.dispatch(msg));
    private final ConfirmDialog<ForcePushPopup> forcePushDialog = ForcePushDialog.create(msg -> this.program.dispatch(msg));
    private final ConfirmDialog<StashCheckoutPopup> stashCheckoutDialog = StashCheckoutDialog.create(msg -> this.program.dispatch(msg));
    private final ConfirmDialog<AmendPopup> amendDialog = AmendDialog.create(msg -> this.program.dispatch(msg));
    private final ConfirmDialog<SquashPopup> squashDialog = SquashDialog.create(msg -> this.program.dispatch(msg));
    private final CommitDialog rewordDialog = CommitDialog.reword(msg -> this.program.dispatch(msg));
    private final ConfirmDialog<StashDropPopup> stashDropDialog = StashDropDialog.create(msg -> this.program.dispatch(msg));
    private final StashDialog stashDialog = new StashDialog(msg -> this.program.dispatch(msg));
    private final ConfirmDialog<StageAllPopup> stageAllDialog = StageAllDialog.create(msg -> this.program.dispatch(msg));
    private final CommitDialog commitDialog = new CommitDialog(msg -> this.program.dispatch(msg));
    private final CommitsPanel commitsPanel = new CommitsPanel(msg -> this.program.dispatch(msg),
            () -> openContent(CommitsPanel.ID));
    private final StashPanel stashPanel = new StashPanel(msg -> this.program.dispatch(msg));
    private final HelpDialog helpDialog = new HelpDialog(msg -> this.program.dispatch(msg));
    // The panel the help popup was opened from, which gets the focus back.
    private String helpOrigin = FilesPanel.ID;
    // Where the focus was when no popup had it, which the theme popup, opened from anywhere, gives it back to.
    private String lastFocus = FilesPanel.ID;
    private ExecutorService io;
    private Program program;

    public JazygitApp(GitInfoProvider provider) {
        this.provider = provider;
    }

    /**
     * Moves the focus to the content shown for the pane {@code paneId}, so it can be scrolled by key.
     */
    private void openContent(String paneId) {
        ContentPanel.areaOfPane(paneId).ifPresent(id -> runner().focusManager().setFocus(id));
    }

    @Override
    protected void onStart() {
        ToolkitRunner runner = runner();
        runner.styleEngine(Theme.createEngine());
        runner.eventRouter().addGlobalHandler(event -> {
            if (event instanceof KeyEvent key) {
                for (Keymap.Global global : Keymap.standard().globals()) {
                    if (global.matches().test(key)) {
                        program.dispatch(global.msg().get());
                        return EventResult.HANDLED;
                    }
                }
            }
            return EventResult.UNHANDLED;
        });
        runner.focusManager().setFocus(FilesPanel.ID);
        ContentPanel.onLeaveScrollArea(() -> {
            Optional<String> pane = ContentPanel.paneOfArea(runner.focusManager().focusedId());
            pane.ifPresent(runner.focusManager()::setFocus);
            return pane.isPresent();
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
        Theme.activate(model.theme());
        String focusedId = runner().focusManager().focusedId();
        Panel branches = branchesPanel.render(model, focusedId);
        // The highlighted branch lives in the list widget, which knows it once rendered; the
        // content panel then shows the log of that branch.
        branchesPanel.selectionChange(model).ifPresent(program::dispatch);
        commitsPanel.selectionChange(model).ifPresent(program::dispatch);
        filesPanel.selectionChange(model).ifPresent(program::dispatch);
        Panel stash = stashPanel.render(model, focusedId);
        Optional<Integer> stashHeight = stashHeight(model);
        Element layout = row(
                column(
                        StatusPanel.render(model),
                        filesPanel.render(model, focusedId).percent(FILES_PERCENT),
                        branches.percent(BRANCHES_PERCENT),
                        // With the stash sized to its content, the commits take what is left, which is
                        // their share when the stash is as tall as it may get.
                        stashHeight.isPresent()
                                ? commitsPanel.render(model, focusedId).fill()
                                : commitsPanel.render(model, focusedId).percent(COMMITS_PERCENT),
                        stashHeight.map(stash::length).orElseGet(stash::fill))
                        .percent(30),
                column(
                        ContentPanel.render(program.model(), focusedId, commitsPanel.selectedIndex())
                                .fill(),
                        CommandLogPanel.render(model).length(COMMAND_LOG_HEIGHT))
                        .fill());
        // The open popup takes the focus, and gives it back to the pane it was opened from.
        model.popup(HelpPopup.class).map(HelpPopup::scopeId).ifPresent(scopeId -> helpOrigin = scopeId);
        List<Popup> popups = List.of(
                new Popup(newBranchDialog.render(model), List.of(NewBranchDialog.ID), lastFocus),
                new Popup(discardDialog.render(model), List.of(DiscardDialog.ID), FilesPanel.ID),
                new Popup(themeDialog.render(model), List.of(ThemeDialog.ID), lastFocus),
                new Popup(rebaseDialog.render(model), List.of(RebaseDialog.ID), BranchesPanel.ID),
                new Popup(deleteBranchDialog.render(model), List.of(DeleteBranchDialog.ID), BranchesPanel.ID),
                new Popup(forcePushDialog.render(model), List.of(ForcePushDialog.ID), BranchesPanel.ID),
                new Popup(stashCheckoutDialog.render(model), List.of(StashCheckoutDialog.ID), BranchesPanel.ID),
                new Popup(amendDialog.render(model), List.of(AmendDialog.ID), FilesPanel.ID),
                new Popup(stashDropDialog.render(model), List.of(StashDropDialog.ID), StashPanel.ID),
                new Popup(stashDialog.render(model), List.of(StashDialog.ID), FilesPanel.ID),
                new Popup(stageAllDialog.render(model), List.of(StageAllDialog.ID), FilesPanel.ID),
                new Popup(commitDialog.render(model), commitDialog.ids(), FilesPanel.ID),
                new Popup(squashDialog.render(model), List.of(SquashDialog.ID), CommitsPanel.ID),
                new Popup(rewordDialog.render(model), rewordDialog.ids(), CommitsPanel.ID),
                new Popup(helpDialog.render(model), List.of(HelpDialog.ID), helpOrigin));
        if (focusedId != null && popups.forAll(p -> !p.ids().contains(focusedId))) {
            lastFocus = focusedId;
        }
        Optional<Popup> open = popups.find(p -> p.element().isPresent()).toJavaOptional();
        if (open.isPresent()) {
            if (focusedId == null || !open.get().ids().contains(focusedId)) {
                runner().focusManager().setFocus(open.get().ids().head());
            }
        } else {
            popups.find(p -> focusedId != null && p.ids().contains(focusedId))
                    .forEach(p -> runner().focusManager().setFocus(p.returnTo()));
        }
        Optional<Element> dialog = open.flatMap(Popup::element);
        return dialog.<Element>map(d -> stack(layout, d)).orElse(layout);
    }

    /**
     * The height of the stash pane: just its rows, but at most the share of the left column that is
     * left once the other panes have theirs, and at least an empty pane (a blank line in a border).
     * Empty when the terminal size is not known.
     */
    private Optional<Integer> stashHeight(Model model) {
        int rows = model.stashes() instanceof Loadable.Loaded<List<Stash>>(List<Stash> stashes)
                ? Math.max(1, stashes.size())
                : 1;
        try {
            int height = runner().tuiRunner().backend().size().height();
            int share = height - StatusPanel.HEIGHT
                    - height * (FILES_PERCENT + BRANCHES_PERCENT + COMMITS_PERCENT) / 100;
            return Optional.of(Math.max(MIN_STASH_HEIGHT, Math.min(rows + 2, share)));
        } catch (IOException e) {
            return Optional.empty();
        }
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
