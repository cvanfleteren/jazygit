package net.vanfleteren.jazygit.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Left-side panel listing the local branches, with the current branch marked. Space checks out
 * the highlighted branch, P pushes it, R asks for a new name for it, d asks where to delete it, n asks for the
 * name of a new branch starting at it.
 * A branch with a pull request shows its number, its CI checks and its review; one without shows the CI checks
 * of the branch it follows on GitHub.
 * <p>
 * {@code [} and {@code ]} switch to the remote-tracking branches and back, in the same pane, so the focus moves
 * through it as one. Space checks out the highlighted remote branch, n starts a new branch at it. A remote branch
 * that a local branch follows is dimmed. A remote branch on GitHub shows its pull request or its CI checks, like a
 * local branch. Clicking the name of a list in the title shows that list.
 */
public class BranchesPanel {

    public static final String ID = Scopes.BRANCHES.id();

    private static final String TITLE_SEPARATOR = " │ ";

    private final LoadableList<List<Branch>> list;
    private final LoadableList<List<RemoteRef>> remoteList;
    private final Keymap keymap = Keymap.standard();
    private final Consumer<BranchesTab> tabClicked;
    // The list the pane shows, as of the last render; the keys of the other one do nothing.
    private BranchesTab tab = BranchesTab.LOCAL;
    // Whether a local branch follows a remote branch, as of the last render.
    private Predicate<RemoteBranch> tracked = remote -> false;
    // The branches an operation runs on, shown with a spinner; the rows are built from it on every render.
    private Map<BranchRef, Operation> busy = HashMap.empty();
    // How a branch is doing on GitHub, as of the last render; the rows are built from it on every render.
    private Function<Branch, List<LoadableList.Seg>> gitHub = branch -> List.empty();
    private Function<RemoteBranch, List<LoadableList.Seg>> remoteGitHub = remote -> List.empty();

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
    private static LoadableList.Row row(Branch b, Duration since, List<LoadableList.Seg> gitHub,
                                        Option<String> spinner) {
        return new LoadableList.Row(
                List.of(LoadableList.Seg.of("%4s ".formatted(TimeText.age(since)) + (b.current() ? "* " : "  ") + b.name().value()))
                        .appendAll(worktree(b))
                        .appendAll(sync(b))
                        .appendAll(gitHub)
                        .appendAll(spinner.map(text -> LoadableList.Seg.styled(" " + text, Theme.Classes.SPINNER))));
    }

    private static LoadableList.Row remoteRow(RemoteRef ref, Duration since, boolean tracked,
                                              List<LoadableList.Seg> gitHub, Option<String> spinner) {
        String text = "%4s   ".formatted(TimeText.age(since)) + ref.branch().shortName();
        return new LoadableList.Row(
                List.of(tracked ? LoadableList.Seg.styled(text, Theme.Classes.DIM) : LoadableList.Seg.of(text))
                        .appendAll(gitHub)
                        .appendAll(spinner.map(frame -> LoadableList.Seg.styled(" " + frame, Theme.Classes.SPINNER))));
    }

    /**
     * The title of the pane: the names of both lists, the one shown in bold and underlined, the other dimmed.
     * The title takes the colour of the border, so they differ in text style only; underlining shows the
     * difference in themes with colours that bold and dim barely change.
     */
    static Line title(BranchesTab tab) {
        Span local = Span.raw(Messages.get("panel.branches.title"));
        Span remote = Span.raw(Messages.get("panel.remoteBranches.title"));
        return tab == BranchesTab.LOCAL
                ? Line.from(local.bold().underlined(), Span.raw(TITLE_SEPARATOR), remote.dim())
                : Line.from(local.dim(), Span.raw(TITLE_SEPARATOR), remote.bold().underlined());
    }

    /**
     * The list whose name in the title is {@code column} cells from the start of the title, if any; the
     * separator between them is none.
     */
    static Option<BranchesTab> tabAt(int column) {
        int local = Span.raw(Messages.get("panel.branches.title")).width();
        int remoteStart = local + Span.raw(TITLE_SEPARATOR).width();
        int remoteEnd = remoteStart + Span.raw(Messages.get("panel.remoteBranches.title")).width();
        if (column >= 0 && column < local) {
            return Option.of(BranchesTab.LOCAL);
        }
        return Option.when(column >= remoteStart && column < remoteEnd, BranchesTab.REMOTE);
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
        return Option.when(branch.checkedOutElsewhere(),
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
                        Option.of(branch.ahead()).filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↑" + n, Theme.Classes.AHEAD)),
                        Option.of(branch.behind()).filter(n -> n > 0)
                                .map(n -> LoadableList.Seg.styled(" ↓" + n, Theme.Classes.BEHIND)),
                        branch.goneUpstream()
                                .map(upstream -> LoadableList.Seg.styled(" " + Messages.get("branches.gone"),
                                        Theme.Classes.GONE)))
                .flatMap(o -> o);
    }

    /**
     * The pull request of a branch, with a leading space: its number (dimmed for a draft), how its CI checks
     * are doing and where its review stands, each as a coloured symbol; nothing for a branch without one.
     */
    static List<LoadableList.Seg> pullRequest(Option<PullRequest> pullRequest) {
        return pullRequest.toList().flatMap(pr -> List.of(
                        Option.of(LoadableList.Seg.styled(" #" + pr.number(),
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
                : checks.flatMap(PullRequestSymbols::checks).map(BranchesPanel::symbol).toList();
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
                ? Option.of(selected).filter(i -> i < previous.size()).map(i -> previous.get(i).name())
                        .map(name -> renamed(previous, next, name))
                : current(next);
        return target
                .map(name -> next.indexWhere(b -> b.name().equals(name)))
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
    public BranchesPanel(Consumer<Msg> dispatch, Consumer<BranchesTab> tabClicked, Clock clock,
                         Theme.Highlights highlights) {
        this.tabClicked = tabClicked;
        list = new LoadableList<>(Messages.get("panel.branches.title"), ID,
                branches -> branches
                        .map(b -> row(b, Duration.between(b.tipTime(), clock.instant()),
                                gitHub.apply(b),
                                busy.get(new BranchRef.Local(b.name())).map(operation -> spinner(operation, clock.instant())))),
                BranchesPanel::reselect, highlights);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKey(event -> Option.when(tab == BranchesTab.LOCAL, event)
                .flatMap(key -> PaneKeys.sending(keymap.handle(Scopes.BRANCHES, key, selectedBranch()), dispatch)));
        remoteList = new LoadableList<>(Messages.get("panel.remoteBranches.title"), ID,
                refs -> refs.map(ref -> remoteRow(ref, Duration.between(ref.tipTime(), clock.instant()),
                        tracked.test(ref.branch()), remoteGitHub.apply(ref.branch()),
                        busy.get(new BranchRef.Remote(ref.branch())).map(operation -> spinner(operation, clock.instant())))),
                LoadableList.Reselect.following(RemoteRef::branch), highlights);
        remoteList.highlightSymbol("");
        remoteList.whenEmpty(Messages.get("remoteBranches.empty"));
        remoteList.onKey(event -> Option.when(tab == BranchesTab.REMOTE, event)
                .flatMap(key -> PaneKeys.sending(keymap.handle(Scopes.REMOTE_BRANCHES, key, selectedRemoteBranch()),
                        dispatch)));
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
        Panel panel = switch (tab) {
            case LOCAL -> list.render(model.branches(), focused, active, title(tab));
            case REMOTE -> remoteList.render(model.remoteBranches(), focused, active, title(tab));
        };
        return panel.onMouseEvent(event -> titleClick(panel.renderedArea(), event));
    }

    /**
     * Shows the list whose name in the title of the pane, drawn in {@code area}, is clicked. The title starts
     * after the corner of the border.
     */
    private EventResult titleClick(Rect area, MouseEvent event) {
        return Option.when(event.kind() == MouseEventKind.PRESS && event.isLeftButton()
                                && area != null && event.y() == area.top(),
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
        return tab == BranchesTab.LOCAL ? list.selectedIndex() : remoteList.selectedIndex();
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
     * The message to send when the highlighted branch of the list shown is not the one whose log {@code model}
     * holds.
     */
    public Option<Msg> selectionChange(Model model) {
        Option<BranchRef> selected = switch (model.branchesTab()) {
            case LOCAL -> selectedBranch().map(BranchRef.Local::new);
            case REMOTE -> selectedRemoteBranch().map(BranchRef.Remote::new);
        };
        return PaneKeys.selectionChange(selected, model.branchLog(), SelectionMsg.BranchSelected::new);
    }
}
