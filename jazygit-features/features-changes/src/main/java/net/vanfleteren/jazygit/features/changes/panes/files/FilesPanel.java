package net.vanfleteren.jazygit.features.changes.panes.files;

import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.FileTree;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FileFilter;
import net.vanfleteren.jazygit.kernel.state.view.FileFilterMsg;
import net.vanfleteren.jazygit.kernel.state.view.FileFilterUpdate;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.FilesLock;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.FilesLeftPane;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.BorderBar;
import net.vanfleteren.jazygit.kernel.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.kernel.ui.widgets.PasteTarget;

/**
 * Left-side panel showing the files of the working tree ({@link WorkingTreeFilesList}) or, while they are open, those
 * a commit or a stash changed ({@link CommitFilesList}, ADR 44), as a directory tree. Each list keeps its own trees, so
 * the files of the working tree are as they were when those of a commit are closed. {@code /} filters the tree shown
 * by the paths of its files (ADR 105): the line above it takes the keys that edit the filter, and Escape clears it
 * before it does anything else. The pane knows no feature: what the features show in it fills the slots of
 * {@link FilesPane} (ADR 172).
 */
public final class FilesPanel implements FilesLeftPane {

    public static final String ID = Scopes.FILES.id();

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = Scopes.FILES.pane();

    private final WorkingTreeFilesList working;
    private final CommitFilesList commits;
    private final FilterLine filterLine = new FilterLine();
    // What a paste does: while the filter line is open, it goes in it.
    private final Consumer<PasteEvent> paste;
    // The trees' key handlers also see keys typed in other panes, so they must know whether the pane has focus.
    private boolean focused;
    // Whether the pane was locked to a list at the last render, which Escape in the working tree lets go of (ADR 149).
    private boolean locked;
    // The filter of the tree shown, as last rendered (ADR 105).
    private Option<FileFilter> filter = Option.none();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     * @param openDiff called when Enter or Right is pressed on a file or directory, to move focus to its diff
     */
    public FilesPanel(Consumer<Msg> dispatch, Runnable openDiff) {
        this(dispatch, openDiff, Theme.Highlights.DEFAULT, Fills.NONE);
    }

    /**
     * @param highlights how the highlighted node is shown, also when the pane is not in use
     * @param fills      what the features give for the slots of {@link FilesPane}
     */
    public FilesPanel(Consumer<Msg> dispatch, Runnable openDiff, Theme.Highlights highlights, Fills fills) {
        paste = event -> filter.filter(FileFilter::typing)
                .forEach(typing -> dispatch.accept(new FileFilterMsg.Edited(filterLine.paste(typing.text(), event))));
        TreeKeys keys =
                new TreeKeys(() -> focused, event -> filtered(event, dispatch), openDiff, dispatch, Keymap.standard());
        working = new WorkingTreeFilesList(highlights, fills, keys, () -> locked);
        commits = new CommitFilesList(highlights, keys);
    }

    /**
     * What {@code event} does to the filter of the tree shown (ADR 105). While its line is open, the keys that edit
     * it do, Enter keeps it and Escape puts back the one from before; the keys the tree moves with go to the tree,
     * and the others do nothing. Once it is closed, Escape clears the filter, before it does anything else.
     */
    private Option<Runnable> filtered(KeyEvent event, Consumer<Msg> dispatch) {
        return filter.filter(FileFilter::typing)
                .map(typing -> moves(event)
                        ? Option.<Runnable>none()
                        : Option.<Runnable>of(() -> typed(typing.text(), event).forEach(dispatch)))
                .getOrElse(() -> filter.isDefined() && event.isCancel()
                        ? Option.of(() -> dispatch.accept(new FileFilterMsg.Cleared()))
                        : Option.none());
    }

    /**
     * What {@code event} sends while the line holds {@code text}.
     */
    private Option<Msg> typed(String text, KeyEvent event) {
        return event.isCancel()
                ? Option.of(new FileFilterMsg.Cancelled())
                : event.isConfirm()
                        ? Option.of(new FileFilterMsg.Submitted())
                        : filterLine.edit(text, event).map(FileFilterMsg.Edited::new);
    }

    /**
     * Whether {@code event} is one of the keys the tree moves its highlight with, which it keeps while the filter is
     * typed.
     */
    private static boolean moves(KeyEvent event) {
        return event.matches(Actions.MOVE_UP)
                || event.matches(Actions.MOVE_DOWN)
                || event.matches(Actions.PAGE_UP)
                || event.matches(Actions.PAGE_DOWN)
                || event.code() == KeyCode.HOME
                || event.code() == KeyCode.END;
    }

    /**
     * When what the pane lists in {@code model} changes as time goes by (ADR 124): the files listed before stop being
     * listed in place of those still read, which the pane then says it reads (ADR 141).
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .filter(view -> view.files() instanceof Loadable.Loading<List<CommitFile>>)
                .flatMap(CommitFilesView::before)
                .map(kept -> (ShownTime) (now, before) ->
                        Option.of(kept.until()).filter(until -> until.isAfter(now) && before.isAfter(until)))
                .toList();
    }

    @Override
    public PaneId pane() {
        return PANE;
    }

    @Override
    public Option<Msg> followChange(Model model, Option<PaneId> inUse, Function<PaneId, Option<Followed>> followed) {
        return FollowedFiles.change(model, inUse, followed.apply(Scopes.BRANCHES.pane()));
    }

    /**
     * The message to send when the highlighted node is not the one whose diff {@code model} holds: of the files of
     * the commit while they are open, else of the working tree.
     */
    @Override
    public Option<Msg> selectionChange(Model model) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(view -> commits.selectionChange(model, view))
                .getOrElse(() -> working.selectionChange(model));
    }

    /**
     * Moves the highlight of the tree shown in {@code model} to its file {@code step} of the highlighted node, as
     * {@link FileTreeList#step} does, and says whether there was one (ADR 117). The pane does not get the focus.
     */
    @Override
    public boolean step(Model model, FileStepMsg.Step step) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(view -> commits.step(view, step))
                .getOrElse(() -> working.step(model.repoView(), step));
    }

    /**
     * The pane while {@code pane} has the focus, and is in use.
     */
    public Panel render(Model model, PaneId pane) {
        return render(model, Option.of(pane), Option.of(pane));
    }

    /**
     * @param focus what has the focus, if anything
     * @param inUse the left pane in use, whose highlighted node shows, as it is again when it
     *              is back in use
     */
    @Override
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        focused = focus.contains(PANE);
        filter = FileFilterUpdate.shown(model);
        locked = model.repoView().files().locked().isDefined();
        ListFrame frame = new ListFrame(
                focused,
                inUse.contains(PANE),
                FileFilter.shown(filter),
                filteredTitle(),
                lockedTitle(model),
                this::withLine);
        return model.repoView()
                .files()
                .commitFiles()
                .map(open -> commits.render(model, open, frame))
                .orElse(() -> model.repoView().files().unlisted().map(none -> commits.unlisted(none.why(), frame)))
                .getOrElse(() -> working.render(model, frame));
    }

    /**
     * What the title of the pane ends in while it is locked to a list: which list (ADR 149).
     */
    private static String lockedTitle(Model model) {
        return model.repoView()
                .files()
                .locked()
                .map(lock -> Messages.get("changes.panes.files.lockedTo", Messages.get(listTitle(lock))))
                .getOrElse("");
    }

    /**
     * The message key of the title of the list {@code lock} is to.
     */
    private static String listTitle(FilesLock lock) {
        if (lock.pane().equals(Scopes.COMMITS.pane())) {
            return "panel.commits.title";
        }
        if (lock.pane().equals(Scopes.STASH.pane())) {
            return "panel.stash.title";
        }
        return switch (lock.list()) {
            case LIST, OTHER_TAB -> "panel.branches.title";
            case DIVERGENCE -> "panel.divergence.title";
            case BRANCH_COMMITS -> "panel.branchCommits.title";
        };
    }

    /**
     * What the title says after what it says already while the files are filtered, once the filter is typed: the
     * line above the tree shows it meanwhile.
     */
    private String filteredTitle() {
        return filter.filter(typed -> !typed.typing())
                .flatMap(FileFilter::shown)
                .map(text -> Messages.get("changes.panes.files.filtered", text))
                .getOrElse("");
    }

    /**
     * {@code tree}, its scrollbar on the right border of the pane, under the line the filter is typed in while it is
     * open, which keeps the width inside the border.
     */
    private Element withLine(Element tree) {
        Element bordered = BorderBar.of(tree);
        return filter.filter(FileFilter::typing)
                .<Element>map(
                        typing -> new PasteTarget(Toolkit.column(filterLine.field(typing.text()), bordered), ID, paste))
                .getOrElse(bordered);
    }

    /**
     * The tree widget of the files of the working tree, which holds their selection and expansion state.
     */
    TreeElement<FileTree<FileEntry>> tree() {
        return working.statusTree();
    }

    /**
     * The tree widget of all files of the working tree, which holds their selection and expansion state (ADR 108).
     */
    TreeElement<FileTree<FileEntry>> allTree() {
        return working.allTree();
    }

    /**
     * The tree widget of the files of a commit, which holds their selection and expansion state.
     */
    TreeElement<FileTree<CommitFile>> commitTree() {
        return commits.commitTree();
    }

    /**
     * The tree widget of all files of a commit, which holds their selection and expansion state (ADR 128).
     */
    TreeElement<FileTree<CommitFile>> commitAllTree() {
        return commits.commitAllTree();
    }
}
