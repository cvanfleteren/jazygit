package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.vanfleteren.jazygit.feature.repos.RecentRepo;
import net.vanfleteren.jazygit.feature.start.StartCmd;
import net.vanfleteren.jazygit.feature.start.StartDialogs;
import net.vanfleteren.jazygit.feature.start.StartMsg;
import net.vanfleteren.jazygit.feature.start.StartUpdate;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.PopupView;
import net.vanfleteren.jazygit.ui.widgets.SelectedTextColoured;

/**
 * What jazygit shows when it starts outside a repository, before {@link JazygitApp}: a dialog asking what to open
 * instead (ADR 28). It ends with the repository chosen, or with none when the user quits.
 *
 * <p>Like the app, it holds a {@link Model}, which its messages update, and runs the {@link StartCmd}s they ask for
 * on a thread of their own. The model has no repository; only its popup and preferences are used.
 */
final class StartApp extends ToolkitApp {

    private final StyleEngine styleEngine;
    private final List<PopupView> popups = StartDialogs.popups(this::dispatch);
    // Where the commands run, so the screen keeps being drawn meanwhile.
    private final ExecutorService io = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("git-io").daemon().factory());
    // Only read and replaced on the render thread.
    private Model model;
    private Option<Path> chosen = Option.none();

    private StartApp(Model model, StyleEngine styleEngine) {
        this.model = model;
        this.styleEngine = styleEngine;
    }

    /**
     * Asks what to open instead of {@code directory}, which is in no repository, drawn as {@code settings} and
     * {@code themeOverride} say: the working tree of the repository chosen, or none when the user quits.
     *
     * @param recent the recent repositories that can be opened instead
     */
    static Option<Path> choose(
            Path directory,
            List<RecentRepo> recent,
            SettingsStore.Loaded settings,
            Option<ThemeName> themeOverride,
            StyleEngine styleEngine)
            throws Exception {
        StartApp app = new StartApp(
                StartUpdate.init(
                        directory.toString(), Files.isDirectory(directory), recent, settings.settings(), themeOverride),
                styleEngine);
        app.run();
        return app.chosen;
    }

    @Override
    protected void onStart() {
        runner().styleEngine(styleEngine);
        // Ctrl+C quits, wherever the focus is: nothing has been started that it would leave half-way.
        runner().eventRouter().addGlobalHandler(event -> {
            if (event instanceof KeyEvent key && key.isCtrlC()) {
                quit();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        });
    }

    @Override
    protected void onStop() {
        io.shutdown();
    }

    /**
     * Quitting, or a repository that opened, ends the app; any other message updates the model. Called on the
     * render thread.
     */
    private void dispatch(Msg msg) {
        switch (msg) {
            case Msg.Quit() -> quit();
            case StartMsg.Opened(String worktree) -> {
                chosen = Option.of(Path.of(worktree));
                quit();
            }
            default -> {
                Update.Next next = Update.update(model, msg);
                model = next.model();
                next.cmds().forEach(cmd -> {
                    if (cmd instanceof StartCmd start) {
                        launch(start);
                    } else {
                        // Only the app has what another command runs against; asking for one here is a bug.
                        JazygitApp.logBug(new IllegalStateException("Not a command of the start: " + cmd));
                    }
                });
            }
        }
    }

    /**
     * Runs {@code cmd} on {@link #io}, and dispatches its outcome on the render thread. One that throws is a bug,
     * which is shown, and written down as the app's are.
     */
    private void launch(StartCmd cmd) {
        io.execute(() -> {
            Msg outcome = Try.of(cmd::run)
                    .onFailure(JazygitApp::logBug)
                    .getOrElseGet(bug -> cmd.failed(new Failure.Bug(bug)));
            Option.of(runner()).forEach(running -> running.runOnRenderThread(() -> dispatch(outcome)));
        });
    }

    @Override
    protected Element render() {
        Theme.activate(styleEngine, model.preferences().theme());
        // The dialog shown takes the focus, also when it takes the place of another one.
        Option<Tuple2<PopupView, Element>> open = popups.flatMap(
                        view -> view.render(model).map(dialog -> Tuple.of(view, dialog)))
                .headOption();
        String focused = runner().focusManager().focusedId();
        open.map(Tuple2::_1)
                .filter(view -> !view.ids().contains(focused))
                .forEach(view -> runner().focusManager().setFocus(view.ids().head()));
        Element screen = open.<Element>map(Tuple2::_2).getOrElse(() -> text(""));
        return new SelectedTextColoured(screen, Theme.selectedText(styleEngine));
    }
}
