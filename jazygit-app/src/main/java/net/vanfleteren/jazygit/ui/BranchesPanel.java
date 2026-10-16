package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.widgets.Pane;

/**
 * Left-side panel listing the local branches, with the current branch marked. Space checks out
 * the highlighted branch, P pushes it, R asks for a new name for it, d asks where to delete it, n asks for the
 * name of a new branch starting at it.
 * A branch with a pull request shows its number, its CI checks and its review; one without shows the CI checks
 * of the branch it follows on GitHub.
 * <p>
 * {@code [} and {@code ]} go through the lists of the pane: the local branches, the remote-tracking ones and the
 * worktrees, in the same pane, so the focus moves through it as one. Space checks out the highlighted remote branch, n starts a new branch at it. A remote branch
 * that a local branch follows is dimmed. A remote branch on GitHub shows its pull request or its CI checks, like a
 * local branch. Clicking the name of a list in the title shows that list.
 * <p>
 * The third list is the worktrees of the repository, the one shown marked. Space switches to the highlighted one,
 * d removes it. On the local branches, w checks the highlighted branch out in a new worktree.
 * <p>
 * While a divergence is open, it is listed in place of the local branches: the commits only on the branch it is
 * compared with, then the ones only on the local branch, each under the name of their branch. Enter moves the
 * focus to the highlighted commit, Escape goes back to the branches.
 */
public class BranchesPanel {

    public static final String ID = Scopes.BRANCHES.id();

    private static final String TITLE_SEPARATOR = " │ ";

    private final LoadableList<List<Branch>> list;
    private final LoadableList<List<RemoteRef>> remoteList;
    private final LoadableList<List<Worktree>> worktreeList;
    private final LoadableList<Divergence> divergenceList;
    private final Keymap keymap = Keymap.standard();
    private final Consumer<BranchesTab> tabClicked;
    // The list the pane shows, as of the last render; the keys of the others do nothing.
    private BranchesTab tab = BranchesTab.LOCAL;
    // Whether a local branch follows a remote branch, as of the last render.
    private Predicate<RemoteBranch> tracked = remote -> false;
    // The branches an operation runs on, shown with a spinner; the rows are built from it on every render.
    private Map<BranchRef, Operation> busy = HashMap.empty();
    // How a branch is doing on GitHub, as of the last render; the rows are built from it on every render.
    private Function<Branch, List<LoadableList.Seg>> gitHub = branch -> List.empty();
    private Function<RemoteBranch, List<LoadableList.Seg>> remoteGitHub = remote -> List.empty();
    // The divergence listed in place of the local branches, as of the last render.
    private Option<DivergenceView> divergence = Option.none();
    // The highlighted row of the divergence at the last render, which tells which way the highlight moved.
    private int divergenceSelected;
    // Which divergence was listed at the last render, by the load that reads it.
    private Option<Cmd.LoadDivergence> divergenceShown = Option.none();

    /**
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param tabClicked called when the name of a list in the title is clicked, with that list
     */
    public BranchesPanel(Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked) {
        this(dispatch, tabClicked, Clock.systemUTC());
    }

    /**
     * @param clock tells how long ago the branches were last committed to
     */
    public BranchesPanel(Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked, Clock clock) {
        this(dispatch, tabClicked, clock, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     */
    public BranchesPanel(
            Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked, Clock clock, Theme.Highlights highlights) {
        this(dispatch, tabClicked, () -> {}, clock, highlights);
    }

    /**
     * @param openCommit called when Enter is pressed on a commit of the divergence, to move focus to its content
     */
    public BranchesPanel(
            Consumer<Msg> dispatch,
            Consumer<BranchesTab> tabClicked,
            Runnable openCommit,
            Clock clock,
            Theme.Highlights highlights) {
        this.tabClicked = tabClicked;
        list = new LoadableList<>(
                Messages.get("panel.branches.title"),
                ID,
                branches -> branches.map(b -> row(
                        b,
                        Duration.between(b.tipTime(), clock.instant()),
                        gitHub.apply(b),
                        busy.get(new BranchRef.Local(b.name())).map(operation -> spinner(operation, clock.instant())))),
                BranchesPanel::reselect,
                highlights);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKey(event -> Option.when(tab == BranchesTab.LOCAL && divergence.isEmpty(), event)
                .flatMap(key -> PaneKeys.sending(keymap.handle(Scopes.BRANCHES, key, selectedBranch()), dispatch)));
        remoteList = new LoadableList<>(
                Messages.get("panel.remoteBranches.title"),
                ID,
                refs -> refs.map(ref -> remoteRow(
                        ref,
                        Duration.between(ref.tipTime(), clock.instant()),
                        tracked.test(ref.branch()),
                        remoteGitHub.apply(ref.branch()),
                        busy.get(new BranchRef.Remote(ref.branch()))
                                .map(operation -> spinner(operation, clock.instant())))),
                LoadableList.Reselect.following(RemoteRef::branch),
                highlights);
        remoteList.highlightSymbol("");
        remoteList.whenEmpty(Messages.get("remoteBranches.empty"));
        remoteList.onKey(event -> Option.when(tab == BranchesTab.REMOTE, event)
                .flatMap(key -> PaneKeys.sending(
                        keymap.handle(Scopes.REMOTE_BRANCHES, key, selectedRemoteBranch()), dispatch)));
        worktreeList = new LoadableList<>(
                Messages.get("panel.worktrees.title"),
                ID,
                worktrees -> worktrees.map(BranchesPanel::worktreeRow),
                LoadableList.Reselect.following(Worktree::path),
                highlights);
        worktreeList.highlightSymbol("");
        worktreeList.whenEmpty(Messages.get("worktrees.empty"));
        worktreeList.onKey(event -> Option.when(tab == BranchesTab.WORKTREES, event)
                .flatMap(key -> PaneKeys.sending(keymap.handle(Scopes.WORKTREES, key, selectedWorktree()), dispatch)));
        divergenceList = new LoadableList<>(
                Messages.get("panel.divergence.title"),
                ID,
                commits -> divergence
                        .map(view -> entries(view, commits).map(BranchesPanel::divergenceRow))
                        .getOrElse(List.empty()),
                (previous, next, selected) -> divergence
                        .map(view -> LoadableList.Reselect.following(BranchesPanel::identity)
                                .apply(entries(view, previous), entries(view, next), selected))
                        .getOrElse(selected),
                highlights);
        divergenceList.highlightSymbol("");
        divergenceList.onKey(event -> Option.when(divergence.isDefined(), event)
                .flatMap(key -> key.code() == KeyCode.ENTER
                        ? selectedDivergenceCommit().map(commit -> openCommit)
                        : PaneKeys.sending(
                                keymap.handle(Scopes.DIVERGENCE, key, selectedDivergenceCommit()), dispatch)));
    }

    /**
     * The rows of a divergence: the commits only on the branch it is compared with, then the ones only on the
     * local branch, each under the name of their branch, with a line when there are none or more than are listed.
     */
    static List<DivergenceEntry> entries(DivergenceView view, Divergence divergence) {
        return side(view.other().name(), false, divergence.behind(), divergence.behindCount())
                .appendAll(side(view.branch().value(), true, divergence.ahead(), divergence.aheadCount()));
    }

    private static List<DivergenceEntry> side(String branch, boolean ahead, List<Commit> commits, int count) {
        List<CommitGraph.Node> nodes = CommitGraph.layout(commits);
        return List.<DivergenceEntry>of(new DivergenceEntry.Header(branch))
                .appendAll(nodes.zipWith(
                        nodes.map(CommitGraph.Node::closesBranch).prepend(false),
                        (node, joins) -> new DivergenceEntry.Shown(node, joins, ahead)))
                .appendAll(Option.<DivergenceEntry>when(commits.isEmpty(), DivergenceEntry.None::new))
                .appendAll(Option.<DivergenceEntry>when(
                        count > commits.size(), () -> new DivergenceEntry.More(count - commits.size())));
    }

    /**
     * What a row of a divergence is, which the highlight follows: a commit by its sha.
     */
    private static Object identity(DivergenceEntry entry) {
        return entry instanceof DivergenceEntry.Shown(var node, var joins, var ahead)
                ? node.commit().sha()
                : entry;
    }

    static LoadableList.Row divergenceRow(DivergenceEntry entry) {
        return switch (entry) {
            case DivergenceEntry.Header(String branch) ->
                LoadableList.Row.of(LoadableList.Seg.styled("── " + branch, Theme.Classes.BOLD));
            case DivergenceEntry.Shown(CommitGraph.Node node, boolean joins, boolean ahead) ->
                new LoadableList.Row(List.of(
                                ahead
                                        ? LoadableList.Seg.styled("↑ ", Theme.Classes.AHEAD)
                                        : LoadableList.Seg.styled("↓ ", Theme.Classes.BEHIND))
                        .appendAll(CommitsPanel.row(node, joins).segs()));
            case DivergenceEntry.None() ->
                LoadableList.Row.of(LoadableList.Seg.styled("  " + Messages.get("divergence.none"), Theme.Classes.DIM));
            case DivergenceEntry.More(int more) ->
                LoadableList.Row.of(
                        LoadableList.Seg.styled("  " + Messages.get("divergence.more", more), Theme.Classes.DIM));
        };
    }

    /**
     * What a busy branch shows on the left of its spinner: what is being done to it, for the operations that
     * talk to its remote.
     */
    static Option<String> busyLabel(Operation operation) {
        return switch (operation) {
            case PUSH -> Option.of(Messages.get("branches.busy.push"));
            case PULL, FAST_FORWARD -> Option.of(Messages.get("branches.busy.pull"));
            case CREATE_PR -> Option.of(Messages.get("branches.busy.pullRequest"));
            case MERGE_PR -> Option.of(Messages.get("branches.busy.mergePr"));
            case CLOSE_PR -> Option.of(Messages.get("branches.busy.closePr"));
            default -> Option.none();
        };
    }

    /**
     * @param spinner the label and the spinner frame of a busy branch, as one text
     */
    private static LoadableList.Row row(
            Branch b, Duration since, List<LoadableList.Seg> gitHub, Option<String> spinner) {
        return new LoadableList.Row(List.of(LoadableList.Seg.of("%4s ".formatted(TimeText.age(since))
                        + (b.current() ? "* " : "  ")
                        + b.name().value()))
                .appendAll(worktree(b))
                .appendAll(sync(b))
                .appendAll(gitHub)
                .appendAll(spinner.map(text -> LoadableList.Seg.styled(" " + text, Theme.Classes.SPINNER))));
    }

    private static LoadableList.Row remoteRow(
            RemoteRef ref, Duration since, boolean tracked, List<LoadableList.Seg> gitHub, Option<String> spinner) {
        String text = "%4s   ".formatted(TimeText.age(since)) + ref.branch().shortName();
        return new LoadableList.Row(
                List.of(tracked ? LoadableList.Seg.styled(text, Theme.Classes.DIM) : LoadableList.Seg.of(text))
                        .appendAll(gitHub)
                        .appendAll(spinner.map(frame -> LoadableList.Seg.styled(" " + frame, Theme.Classes.SPINNER))));
    }

    /**
     * The name of {@code tab} in the title.
     */
    private static Span tabName(BranchesTab tab) {
        return Span.raw(Messages.get(
                switch (tab) {
                    case LOCAL -> "panel.branches.title";
                    case REMOTE -> "panel.remoteBranches.title";
                    case WORKTREES -> "panel.worktrees.title";
                }));
    }

    /**
     * The title of the pane: the names of the lists, the one shown in bold and underlined, the others dimmed.
     * The title takes the colour of the border, so they differ in text style only; underlining shows the
     * difference in themes with colours that bold and dim barely change.
     */
    static Line title(BranchesTab tab) {
        List<Span> names = List.of(BranchesTab.values())
                .map(each -> each == tab
                        ? tabName(each).bold().underlined()
                        : tabName(each).dim());
        return Line.from(names.intersperse(Span.raw(TITLE_SEPARATOR)).toJavaList());
    }

    /**
     * The list whose name in the title is {@code column} cells from the start of the title, if any; the
     * separators between them are none.
     */
    static Option<BranchesTab> tabAt(int column) {
        int separator = Span.raw(TITLE_SEPARATOR).width();
        List<BranchesTab> tabs = List.of(BranchesTab.values());
        // Where the name of each tab starts: after the names and separators before it.
        List<Integer> starts =
                tabs.init().scanLeft(0, (start, tab) -> start + tabName(tab).width() + separator);
        return tabs.zip(starts)
                .find(tab ->
                        column >= tab._2 && column < tab._2 + tabName(tab._1).width())
                .map(tab -> tab._1);
    }

    /**
     * A worktree: marked when it is the one shown, the name of its directory, and what it has checked out; then
     * whether it is the main one, locked, or gone from disk.
     */
    static LoadableList.Row worktreeRow(Worktree worktree) {
        String checkedOut = worktree.branch()
                .map(Branch.Name::value)
                .getOrElse(() -> Messages.get(
                        "worktrees.detached", worktree.head().map(Sha::shortSha).getOrElse("")));
        return new LoadableList.Row(List.of(
                        Option.of(LoadableList.Seg.of((worktree.current() ? "* " : "  ") + worktree.name() + " ")),
                        Option.of(
                                worktree.branch().isDefined()
                                        ? LoadableList.Seg.of(checkedOut)
                                        : LoadableList.Seg.styled(checkedOut, Theme.Classes.DIM)),
                        Option.when(
                                worktree.main(),
                                () -> LoadableList.Seg.styled(" " + Messages.get("worktrees.main"), Theme.Classes.DIM)),
                        Option.when(
                                worktree.locked(),
                                () -> LoadableList.Seg.styled(
                                        " " + Messages.get("worktrees.locked"), Theme.Classes.DIM)),
                        Option.when(
                                worktree.prunable(),
                                () -> LoadableList.Seg.styled(
                                        " " + Messages.get("worktrees.missing"), Theme.Classes.GONE)))
                .flatMap(segment -> segment));
    }

    /**
     * The spinner of a branch that {@code operation} runs on, preceded by its label if it has one.
     */
    static String spinner(Operation operation, Instant now) {
        return busyLabel(operation).map(label -> label + " ").getOrElse("") + TimeText.spinnerFrame(now);
    }

    /**
     * That the branch is checked out in another worktree, dimmed and with a leading space; nothing for one that
     * is not.
     */
    static List<LoadableList.Seg> worktree(Branch branch) {
        return Option.when(
                        branch.checkedOutElsewhere(),
                        () -> LoadableList.Seg.styled(" " + Messages.get("branches.worktree"), Theme.Classes.DIM))
                .toList();
    }

    /**
     * The commits not yet pushed in green and not yet pulled in red, as an arrow and a count each,
     * with a leading space; "gone" when its upstream was deleted on the remote; nothing for a branch that is
     * in sync, or that has no upstream.
     */
    static List<LoadableList.Seg> sync(Branch branch) {
        return List.of(
                        Option.of(branch.ahead())
                                .filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↑" + n, Theme.Classes.AHEAD)),
                        Option.of(branch.behind())
                                .filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↓" + n, Theme.Classes.BEHIND)),
                        branch.goneUpstream()
                                .map(upstream -> LoadableList.Seg.styled(
                                        " " + Messages.get("branches.gone"), Theme.Classes.GONE)))
                .flatMap(o -> o);
    }

    /**
     * The pull request of a branch, with a leading space: its number (dimmed for a draft), how its CI checks
     * are doing and where its review stands, each as a coloured symbol; nothing for a branch without one.
     */
    static List<LoadableList.Seg> pullRequest(Option<PullRequest> pullRequest) {
        return pullRequest
                .toList()
                .flatMap(pr -> List.of(
                                Option.of(LoadableList.Seg.styled(
                                        " #" + pr.number(),
                                        pr.draft() ? Theme.Classes.DIM : Theme.Classes.PULL_REQUEST)),
                                PullRequestSymbols.checks(pr.checks()).map(BranchesPanel::symbol),
                                PullRequestSymbols.review(pr.review()).map(BranchesPanel::symbol))
                        .flatMap(o -> o));
    }

    /**
     * How a branch is doing on GitHub, with a leading space: its pull request, if it has one, else how the CI
     * checks on its tip are doing; nothing for a branch with neither.
     */
    static List<LoadableList.Seg> gitHub(Option<PullRequest> pullRequest, Option<Checks> checks) {
        return pullRequest.isDefined()
                ? pullRequest(pullRequest)
                : checks.flatMap(PullRequestSymbols::checks)
                        .map(BranchesPanel::symbol)
                        .toList();
    }

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }

    /**
     * The selection follows its branch when the list is reordered, also when it was renamed: when it is gone and
     * one branch came in its place. After a checkout it goes to the new current branch, which is listed first.
     */
    private static int reselect(List<Branch> previous, List<Branch> next, int selected) {
        Option<Branch.Name> before = current(previous);
        Option<Branch.Name> target = before.equals(current(next))
                ? Option.of(selected)
                        .filter(i -> i < previous.size())
                        .map(i -> previous.get(i).name())
                        .map(name -> renamed(previous, next, name))
                : current(next);
        return target.map(name -> next.indexWhere(b -> b.name().equals(name)))
                .filter(i -> i >= 0)
                .getOrElse(selected);
    }

    /**
     * The name {@code name} has in {@code next}: its own while it is there, else the only one that is new there.
     */
    private static Branch.Name renamed(List<Branch> previous, List<Branch> next, Branch.Name name) {
        List<Branch.Name> before = previous.map(Branch::name);
        List<Branch.Name> after = next.map(Branch::name);
        List<Branch.Name> added = after.removeAll(before);
        return after.contains(name) || added.size() != 1 ? name : added.head();
    }

    private static Option<Branch.Name> current(List<Branch> branches) {
        return branches.find(Branch::current).map(Branch::name);
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return render(model, focusedId, focusedId);
    }

    /**
     * @param activeId the id of the left pane in use, whose highlighted row shows
     */
    public Panel render(Model model, String focusedId, String activeId) {
        busy = model.busy();
        gitHub = branch -> gitHub(model.pullRequest(branch), model.checks(branch));
        remoteGitHub = remote -> gitHub(model.pullRequest(remote), model.checks(remote));
        tab = model.branchesTab();
        tracked = remote -> model.trackingBranch(remote).isDefined();
        boolean focused = ID.equals(focusedId);
        boolean active = ID.equals(activeId);
        divergence = Option.when(tab == BranchesTab.LOCAL, model::divergence).flatMap(open -> open);
        if (divergence.isDefined()) {
            DivergenceView view = divergence.get();
            if (!divergenceShown.contains(view.load())) {
                // A divergence just opened starts at its first commit.
                divergenceShown = Option.of(view.load());
                divergenceList.select(0);
                divergenceSelected = 0;
            }
            Panel panel = divergenceList.render(
                    view.commits(),
                    focused,
                    active,
                    Pane.title(Messages.get(
                            "panel.divergence.titleFor",
                            view.branch().value(),
                            view.other().name())));
            skipToCommit();
            return panel;
        }
        divergenceShown = Option.none();
        Panel panel = switch (tab) {
            case LOCAL -> list.render(model.branches(), focused, active, title(tab));
            case REMOTE -> remoteList.render(model.remoteBranches(), focused, active, title(tab));
            case WORKTREES -> worktreeList.render(model.worktrees(), focused, active, title(tab));
        };
        return panel.onMouseEvent(event -> titleClick(panel.renderedArea(), event));
    }

    /**
     * Moves the highlight off a row of the divergence that is not a commit, to the nearest commit the way it
     * moved, else the other way; when there are no commits, it stays.
     */
    private void skipToCommit() {
        List<Boolean> commits = divergence
                .flatMap(view -> view.commits()
                        .toOption()
                        .map(shown -> entries(view, shown).map(DivergenceEntry.Shown.class::isInstance)))
                .getOrElse(List.empty());
        int at = divergenceList.selectedIndex();
        int way = at >= divergenceSelected ? 1 : -1;
        Option<Integer> target = commits.lift().apply(at).contains(false)
                ? nearest(commits, at, way).orElse(() -> nearest(commits, at, -way))
                : Option.none();
        target.forEach(divergenceList::select);
        divergenceSelected = target.getOrElse(at);
    }

    private static Option<Integer> nearest(List<Boolean> commits, int from, int way) {
        return Stream.iterate(from + way, i -> i + way)
                .takeWhile(i -> i >= 0 && i < commits.size())
                .find(commits::get);
    }

    /**
     * Shows the list whose name in the title of the pane, drawn in {@code area}, is clicked. The title starts
     * after the corner of the border.
     */
    private EventResult titleClick(Rect area, MouseEvent event) {
        return Option.when(
                        event.kind() == MouseEventKind.PRESS
                                && event.isLeftButton()
                                && area != null
                                && event.y() == area.top(),
                        event.x() - area.left() - 1)
                .flatMap(BranchesPanel::tabAt)
                .map(clicked -> {
                    tabClicked.accept(clicked);
                    return EventResult.HANDLED;
                })
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * The index of the currently highlighted branch in the list shown, never negative.
     */
    public int selectedIndex() {
        if (divergence.isDefined()) {
            return divergenceList.selectedIndex();
        }
        return switch (tab) {
            case LOCAL -> list.selectedIndex();
            case REMOTE -> remoteList.selectedIndex();
            case WORKTREES -> worktreeList.selectedIndex();
        };
    }

    /**
     * The name of the highlighted branch, once the branches are shown.
     */
    public Option<Branch.Name> selectedBranch() {
        return list.selected(branches -> branches).map(Branch::name);
    }

    /**
     * The highlighted remote branch, once the remote branches are shown.
     */
    public Option<RemoteBranch> selectedRemoteBranch() {
        return remoteList.selected(refs -> refs).map(RemoteRef::branch);
    }

    /**
     * The highlighted commit of the divergence, once one is shown.
     */
    public Option<Commit> selectedDivergenceCommit() {
        return divergence.flatMap(view -> divergenceList
                .selected(shown -> entries(view, shown))
                .flatMap(entry -> entry instanceof DivergenceEntry.Shown(var node, var joins, var ahead)
                        ? Option.of(node.commit())
                        : Option.none()));
    }

    /**
     * The highlighted worktree, once the worktrees are shown.
     */
    public Option<Worktree> selectedWorktree() {
        return worktreeList.selected(worktrees -> worktrees);
    }

    /**
     * The message to send when the highlighted branch of the list shown is not the one whose log {@code model}
     * holds.
     */
    public Option<Msg> selectionChange(Model model) {
        if (model.branchesTab() == BranchesTab.LOCAL && model.divergence().isDefined()) {
            return PaneKeys.selectionChange(
                    selectedDivergenceCommit().map(Commit::sha),
                    model.divergence().flatMap(DivergenceView::detail),
                    SelectionMsg.DivergenceCommitSelected::new);
        }
        Option<BranchRef> selected = switch (model.branchesTab()) {
            case LOCAL -> selectedBranch().map(BranchRef.Local::new);
            case REMOTE -> selectedRemoteBranch().map(BranchRef.Remote::new);
            // The log of the branch the worktree has checked out; a detached one leaves the log as it is.
            case WORKTREES -> selectedWorktree().flatMap(Worktree::branch).map(BranchRef.Local::new);
        };
        return PaneKeys.selectionChange(selected, model.branchLog(), SelectionMsg.BranchSelected::new);
    }

    /**
     * A row of a divergence; only its commits can be highlighted.
     */
    sealed interface DivergenceEntry {

        /**
         * The name of the branch the commits below are only on.
         */
        record Header(String branch) implements DivergenceEntry {}

        /**
         * A commit only on one of the branches.
         *
         * @param joins whether the branch before this commit joins the main line at it
         * @param ahead whether it is on the local branch, rather than on the one it is compared with
         */
        record Shown(CommitGraph.Node node, boolean joins, boolean ahead) implements DivergenceEntry {}

        /**
         * That the branch above has no commits the other has not.
         */
        record None() implements DivergenceEntry {}

        /**
         * That the branch above has {@code more} commits than are listed.
         */
        record More(int more) implements DivergenceEntry {}
    }
}
