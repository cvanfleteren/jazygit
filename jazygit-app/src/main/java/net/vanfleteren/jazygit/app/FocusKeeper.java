package net.vanfleteren.jazygit.app;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.content.ContentPanel;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.view.BuildsView;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.FilesLock;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.CommandLogPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.content.ContentArea;
import net.vanfleteren.jazygit.ui.content.ContentSources;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Where the focus is, which TamboUI keeps, not the model: which pane, content area or popup has it, and where it goes
 * when a screen is shown, a click lands, or the content of a pane is opened or left. Called on the render thread.
 */
final class FocusKeeper {

    private final Supplier<ToolkitRunner> runner;
    private final Supplier<Model> model;
    private final ContentPanel contentPanel;
    private final Popups popups;
    private final Runnable requestRedraw;
    // The screen the last frame showed; when the model shows another one, its pane gets the focus back.
    private Screen shownScreen = Screen.REPOSITORY;
    // The focus the last frame was drawn with that was not empty, which a click on nothing that takes it clears.
    private Option<Focus> lastFocus = Option.none();
    // The files of a commit the last frame listed; when the model opens or closes them, the focus follows.
    private Option<CommitFilesView> shownFiles = Option.none();
    // The lock of the files pane at the last frame, by its serial: a new one moves the focus into the files (ADR 149).
    private Option<Integer> shownLock = Option.none();
    // The branch whose builds the last frame had open, and whether they are to take the focus once they show.
    private Option<BranchName> shownBuilds = Option.none();
    private boolean buildsToFocus = false;

    /**
     * @param runner        the runner of the app, once it is started
     * @param model         the model shown
     * @param requestRedraw asks for a frame
     */
    FocusKeeper(
            Supplier<ToolkitRunner> runner,
            Supplier<Model> model,
            ContentPanel contentPanel,
            Popups popups,
            Runnable requestRedraw) {
        this.runner = runner;
        this.model = model;
        this.contentPanel = contentPanel;
        this.popups = popups;
        this.requestRedraw = requestRedraw;
    }

    /**
     * What has the focus; none before the first frame.
     */
    Option<Focus> focus() {
        return Option.of(manager().focusedId()).map(id -> focusOf(id, popups::owns, contentPanel.sources()));
    }

    /**
     * Moves the focus to {@code focus}.
     */
    void moveTo(Focus focus) {
        manager().setFocus(focus.id());
    }

    /**
     * Moves the focus to where it was outside the popups on the screen {@code model} shows.
     */
    void moveToPaneOf(Model model) {
        moveTo(PopupSlot.focusedPane(model));
    }

    /**
     * Gives the focus back to what had it before a frame of {@code model} is drawn, and to the pane of the screen it
     * shows when that is another one; says where the focus is then.
     */
    Option<Focus> settle(Model model) {
        // A click on nothing that takes the focus cleared it; what had it keeps it.
        if (focus().isEmpty()) {
            lastFocus.forEach(this::moveTo);
        }
        if (model.screen() != shownScreen) {
            shownScreen = model.screen();
            moveToPaneOf(model);
        }
        followCommitFiles(model);
        followBuilds(model);
        leaveCollapsedColumn(model);
        // An area that no longer shows gives the focus to the one that took its place, not to the first pane.
        focus().flatMap(focus -> contentPanel.replacing(model, focus)).forEach(this::moveTo);
        Option<Focus> settled = focus();
        lastFocus = settled.orElse(lastFocus);
        return settled;
    }

    /**
     * Moves the focus from the branches into the builds of a branch once they show, after {@code B} opened them, and
     * back to the branches once they are closed while they have it (ADR 131).
     */
    private void followBuilds(Model model) {
        Option<BranchName> builds = model.repoView().branches().builds().map(BuildsView::branch);
        if (!builds.equals(shownBuilds)) {
            if (builds.isEmpty() && focus().contains(AreaId.BUILDS)) {
                moveTo(BranchesPanel.PANE);
            }
            buildsToFocus = builds.isDefined();
            shownBuilds = builds;
        }
        // The area takes the focus only once a frame drew it: until then, the next frame tries again.
        if (buildsToFocus && contentPanel.areaOfPane(model, BranchesPanel.PANE).contains(AreaId.BUILDS)) {
            if (focus().contains(BranchesPanel.PANE)) {
                moveTo(AreaId.BUILDS);
            }
            buildsToFocus = focus().contains(BranchesPanel.PANE);
            if (buildsToFocus) {
                requestRedraw.run();
            }
        }
    }

    /**
     * Moves the focus from a pane of the left column into the content shown for it once the column is collapsed, which
     * does not draw the pane (ADR 104).
     */
    private void leaveCollapsedColumn(Model model) {
        if (model.preferences().leftCollapsed(model.screen())) {
            focus().flatMap(focus -> focus instanceof PaneId pane
                                    && Scopes.leftPanes(model.screen()).contains(pane)
                            ? contentPanel.areaOfPane(model, pane)
                            : Option.none())
                    .forEach(this::moveTo);
        }
    }

    /**
     * Moves the focus into the files pane each time Enter, or {@code F} on a branch, locks it to a list (ADR 149), but
     * not as the files follow the highlight (ADR 141), and back to the pane the files of a commit were listed for once
     * they are gone while the files pane, or the diff it shows, has it (ADR 44).
     */
    private void followCommitFiles(Model model) {
        Option<Integer> lock = model.repoView().files().locked().map(FilesLock::serial);
        if (!lock.equals(shownLock)) {
            shownLock = lock;
            lock.forEach(serial -> moveTo(Scopes.FILES.pane()));
        }
        Option<CommitFilesView> files = model.repoView().files().commitFiles();
        if (files.map(FocusKeeper::opened).equals(shownFiles.map(FocusKeeper::opened))) {
            return;
        }
        Option<CommitFilesView> closed = shownFiles;
        shownFiles = files;
        if (files.isEmpty()) {
            closed.filter(view -> focus().exists(focus -> focus.equals(view.listedIn())
                            || contentPanel.paneOfArea(model, focus).contains(view.listedIn())))
                    .forEach(view -> moveTo(view.pane()));
        }
    }

    /**
     * Whose files {@code view} lists, and whether they follow the highlight: {@code F} on the commit whose files follow
     * it still moves the focus into them.
     */
    private static Tuple2<Changes, Boolean> opened(CommitFilesView view) {
        return Tuple.of(view.of(), view.followed());
    }

    /**
     * The left pane in use, whose highlighted row shows: the one with the focus, {@code focus}, also while the content
     * it selects has the focus, or a popup that has it.
     */
    Option<PaneId> activePane(Model model, Option<Focus> focus) {
        return onScreen(focus).orElse(model.shownPane()).flatMap(onScreen -> switch (onScreen) {
            case PaneId pane -> Option.of(pane);
            case AreaId area -> contentPanel.paneOfArea(model, area);
        });
    }

    /**
     * Tells the model where the focus is, {@code focus}, when it is outside the popups and the command log and the
     * model does not know yet: a closing popup gives it back there, as Escape in the log does.
     */
    Option<Msg> report(Model model, Option<Focus> focus) {
        // The framework knows no focus before the first render.
        return onScreen(focus)
                .filter(onScreen -> !model.shownPane().contains(onScreen))
                .map(ViewMsg.Focused::new);
    }

    /**
     * Moves the focus to the content shown for the pane {@code pane}, so it can be scrolled by key.
     */
    void openContent(PaneId pane) {
        contentPanel.areaOfPane(model.get(), pane).forEach(this::moveTo);
    }

    /**
     * Moves the focus from the pane that has it into the content shown for that pane, as Right does in any pane that
     * leaves the key unhandled; says whether there was such content.
     */
    boolean openFocusedContent() {
        Option<AreaId> area = focus().flatMap(focus ->
                focus instanceof PaneId pane ? contentPanel.areaOfPane(model.get(), pane) : Option.none());
        area.forEach(this::moveTo);
        return area.isDefined();
    }

    /**
     * Whether the content shown for a pane has the focus, which {@link #leaveScrollArea} gives back to that pane.
     */
    boolean inPaneContent() {
        return paneOfFocusedArea().isDefined();
    }

    /**
     * Whether the content shown for the pane {@code pane} has the focus, or had it before a popup took it.
     */
    boolean inContentOf(Model model, PaneId pane) {
        return onScreen(focus())
                .orElse(model.shownPane())
                .flatMap(onScreen -> contentPanel.paneOfArea(model, onScreen))
                .contains(pane);
    }

    /**
     * Gives the focus back to the pane whose content has it, and says whether there was one.
     */
    boolean leaveScrollArea() {
        Option<PaneId> pane = paneOfFocusedArea();
        pane.forEach(this::moveTo);
        return pane.isDefined();
    }

    /**
     * The pane whose content has the focus, if an area of it has.
     */
    private Option<PaneId> paneOfFocusedArea() {
        return focus().flatMap(focus -> contentPanel.paneOfArea(model.get(), focus));
    }

    /**
     * Gives the focus to the pane or area a left click lands on, which then handles the click as usual. TamboUI only
     * does so for a click that no element handles, but the layout around the panes hands the click to the pane, which
     * does. An open popup keeps the focus.
     */
    void clicked(MouseEvent event) {
        if (event.kind() == MouseEventKind.PRESS
                && event.isLeftButton()
                && !focus().exists(focus -> popups.owns(focus.id()))) {
            manager().focusAt(event.x(), event.y());
        }
    }

    /**
     * Asks for another frame once the frame being rendered is drawn, if the focus is no longer {@code drawnWith} by
     * then: a popup that opens or closes moves it, and TamboUI focuses the first pane after a frame drawn with no
     * focus or with one that is gone. The model does not know the focus, so does not ask for the frame.
     */
    void redrawIfMoves(Option<Focus> drawnWith) {
        runner.get().tuiRunner().runLater(() -> {
            if (!focus().equals(drawnWith)) {
                requestRedraw.run();
            }
        });
    }

    /**
     * What the element {@code id}, which TamboUI says has the focus, is: an element of a popup, which
     * {@code ownedByPopup} tells, or the command log (ADR 129), aside from the panes and the content panel; an area of
     * the content panel, which one of {@code sources} declares; or else a pane. This is the one place a TamboUI id is
     * read as a focus.
     */
    static Focus focusOf(String id, Predicate<String> ownedByPopup, ContentSources sources) {
        if (ownedByPopup.test(id) || CommandLogPanel.ID.equals(id)) {
            return new Focus.Aside(id);
        }
        return sources.focusable(id).<Focus>map(ContentArea::id).getOrElse(() -> new PaneId(id));
    }

    /**
     * {@code focus} if it is on the screen, outside the popups and the command log. The model keeps where the focus
     * was before an element aside took it, which the content panel and the highlighted row of a pane go by, and which
     * the focus goes back to.
     */
    private static Option<Focus.OnScreen> onScreen(Option<Focus> focus) {
        return focus.flatMap(on -> on instanceof Focus.OnScreen onScreen ? Option.of(onScreen) : Option.none());
    }

    /**
     * Gives the focus to the command log (ADR 129).
     */
    void moveToCommandLog() {
        moveTo(CommandLogPanel.FOCUS);
    }

    /**
     * Gives the focus back from the command log to where it was before, if the log has it.
     */
    void leaveCommandLog(Model model) {
        if (focus().contains(CommandLogPanel.FOCUS)) {
            moveToPaneOf(model);
        }
    }

    private FocusManager manager() {
        return runner.get().focusManager();
    }
}
