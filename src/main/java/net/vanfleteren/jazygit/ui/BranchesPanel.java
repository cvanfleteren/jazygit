package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Left-side panel listing the local branches, with the current branch marked. Space checks out
 * the highlighted branch, P pushes it, d asks where to delete it, n asks for the name of a new branch starting at it.
 * A branch with a pull request shows its number, its CI checks and its review.
 */
public class BranchesPanel {

    public static final String ID = Scopes.BRANCHES.id();

    private final LoadableList<List<Branch>> list;
    private final Keymap keymap = Keymap.standard();
    // The branches an operation runs on, shown with a spinner; the rows are built from it on every render.
    private Map<String, Operation> busy = HashMap.empty();
    // The pull request of a branch, as of the last render; the rows are built from it on every render.
    private Function<Branch, Option<PullRequest>> pullRequests = branch -> Option.none();

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
    private static LoadableList.Row row(Branch b, Duration since, Option<PullRequest> pullRequest,
                                        Option<String> spinner) {
        return new LoadableList.Row(
                List.of(LoadableList.Seg.of("%4s ".formatted(TimeText.age(since)) + (b.current() ? "* " : "  ") + b.name()))
                        .appendAll(sync(b))
                        .appendAll(pullRequest(pullRequest))
                        .appendAll(spinner.map(text -> LoadableList.Seg.styled(" " + text, Theme.Classes.SPINNER))));
    }

    /**
     * The spinner of a branch that {@code operation} runs on, preceded by its label if it has one.
     */
    static String spinner(Operation operation, Instant now) {
        return busyLabel(operation).map(label -> label + " ").getOrElse("") + TimeText.spinnerFrame(now);
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

    private static LoadableList.Seg symbol(PullRequestSymbols.Symbol symbol) {
        return LoadableList.Seg.styled(" " + symbol.text(), symbol.cssClass());
    }

    /**
     * The selection follows its branch when the list is reordered. After a checkout it goes to the new
     * current branch, which is listed first.
     */
    private static int reselect(List<Branch> previous, List<Branch> next, int selected) {
        Option<String> before = current(previous);
        Option<String> target = before.equals(current(next))
                ? Option.of(selected).filter(i -> i < previous.size()).map(i -> previous.get(i).name())
                : current(next);
        return target
                .map(name -> next.indexWhere(b -> b.name().equals(name)))
                .filter(i -> i >= 0)
                .getOrElse(selected);
    }

    private static Option<String> current(List<Branch> branches) {
        return branches.find(Branch::current).map(Branch::name);
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public BranchesPanel(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemUTC());
    }

    /**
     * @param clock tells how long ago the branches were last committed to
     */
    public BranchesPanel(Consumer<Msg> dispatch, Clock clock) {
        list = new LoadableList<>(Messages.get("panel.branches.title"), ID,
                branches -> branches
                        .map(b -> row(b, Duration.between(b.tipTime(), clock.instant()),
                                pullRequests.apply(b),
                                busy.get(b.name()).map(operation -> spinner(operation, clock.instant())))),
                BranchesPanel::reselect);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKey(event -> PaneKeys.sending(keymap.handle(Scopes.BRANCHES, event, selectedBranch()), dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        busy = model.busy();
        pullRequests = model::pullRequest;
        return list.render(model.branches(), ID.equals(focusedId));
    }

    /**
     * The index of the currently highlighted branch, never negative.
     */
    public int selectedIndex() {
        return list.selectedIndex();
    }

    /**
     * The name of the highlighted branch, once the branches are shown.
     */
    public Option<String> selectedBranch() {
        return list.selected(branches -> branches).map(Branch::name);
    }

    /**
     * The message to send when the highlighted branch is not the one whose log {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(selectedBranch(), model.branchLog(), SelectionMsg.BranchSelected::new);
    }
}
