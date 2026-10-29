package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.control.Option;
import java.util.Objects;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.ui.ContentPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;

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
    private Option<String> lastFocus = Option.none();
    // The files of a commit the last frame listed; when the model opens or closes them, the focus follows.
    private Option<CommitFilesView> shownFiles = Option.none();

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
     * The element with the focus; none before the first frame.
     */
    String focusedId() {
        return focus().focusedId();
    }

    /**
     * Moves the focus to the element {@code id}.
     */
    void moveTo(String id) {
        focus().setFocus(id);
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
    String settle(Model model) {
        // A click on nothing that takes the focus cleared it; what had it keeps it.
        if (focusedId() == null) {
            lastFocus.forEach(this::moveTo);
        }
        if (model.screen() != shownScreen) {
            shownScreen = model.screen();
            moveToPaneOf(model);
        }
        followCommitFiles(model);
        String focusedId = focusedId();
        lastFocus = Option.of(focusedId).orElse(lastFocus);
        return focusedId;
    }

    /**
     * Moves the focus into the pane that lists the files of a commit or a stash once they are opened, and back to the
     * pane they were opened from once they are closed while that pane, or the diff it shows, has it (ADR 44).
     */
    private void followCommitFiles(Model model) {
        Option<CommitFilesView> files = model.repoView().commitFiles();
        if (files.map(CommitFilesView::of).equals(shownFiles.map(CommitFilesView::of))) {
            return;
        }
        Option<CommitFilesView> closed = shownFiles;
        shownFiles = files;
        files.forEach(opened -> moveTo(opened.listedIn()));
        if (files.isEmpty()) {
            closed.filter(view -> {
                        String focusedId = focusedId();
                        return view.listedIn().equals(focusedId)
                                || contentPanel.paneOfArea(model, focusedId).contains(view.listedIn());
                    })
                    .forEach(view -> moveTo(view.pane()));
        }
    }

    /**
     * The left pane in use, whose highlighted row shows: the one with the focus, also while the content it selects
     * has the focus, or a popup that has it.
     */
    String activePane(Model model, String focusedId) {
        return Option.of(focusedId)
                .filter(id -> !popups.owns(id))
                .orElse(model.shownPane())
                .map(id -> contentPanel.paneOfArea(model, id).getOrElse(id))
                .getOrNull();
    }

    /**
     * Tells the model where the focus is, {@code focusedId}, when it is outside the popups and the model does not
     * know yet: a closing popup gives it back there.
     */
    Option<Msg> report(Model model, String focusedId) {
        // The framework knows no focus before the first render.
        return Option.of(focusedId)
                .filter(id -> !popups.owns(id))
                .filter(id -> !model.shownPane().contains(id))
                .map(id -> new Msg.Focused(id, ContentPanel.isArea(id)));
    }

    /**
     * Moves the focus to the content shown for the pane {@code paneId}, so it can be scrolled by key.
     */
    void openContent(String paneId) {
        contentPanel.areaOfPane(model.get(), paneId).forEach(this::moveTo);
    }

    /**
     * Gives the focus back to the pane whose content has it, and says whether there was one.
     */
    boolean leaveScrollArea() {
        Option<String> pane = contentPanel.paneOfArea(model.get(), focusedId());
        pane.forEach(this::moveTo);
        return pane.isDefined();
    }

    /**
     * Gives the focus to the pane or area a left click lands on, which then handles the click as usual. TamboUI only
     * does so for a click that no element handles, but the layout around the panes hands the click to the pane, which
     * does. An open popup keeps the focus.
     */
    void clicked(MouseEvent event) {
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton() && !popups.owns(focusedId())) {
            focus().focusAt(event.x(), event.y());
        }
    }

    /**
     * Asks for another frame once the frame being rendered is drawn, if the focus is no longer {@code drawnWith} by
     * then: a popup that opens or closes moves it, and TamboUI focuses the first pane after a frame drawn with no
     * focus or with one that is gone. The model does not know the focus, so does not ask for the frame.
     */
    void redrawIfMoves(String drawnWith) {
        runner.get().tuiRunner().runLater(() -> {
            if (!Objects.equals(focusedId(), drawnWith)) {
                requestRedraw.run();
            }
        });
    }

    private FocusManager focus() {
        return runner.get().focusManager();
    }
}
