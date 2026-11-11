package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.github.*;
import net.vanfleteren.jazygit.state.load.*;

/**
 * The local branches, with the name of the current branch in bold and a colour of its own. Space checks out the highlighted branch, P pushes it, R asks
 * for a new name for it, d asks where to delete it, n asks for the name of a new branch starting at it, w checks it
 * out in a new worktree.
 * A branch with a pull request shows its number, its CI checks and its review; one without shows the CI checks
 * of the branch it follows on GitHub.
 * <p>
 * A stack of pull requests on GitHub with a local branch is one node: a header, and its pull requests under it in
 * GitHub's order, indented (ADR 72). Enter on the header opens or closes it; the keys of a branch do nothing there.
 * A pull request of it whose branch is not checked out shows the log of its remote-tracking branch, once that is
 * fetched. S lists the branches of the stacks as any other branch, and shows the stacks again (ADR 87).
 */
class LocalBranchesList implements BranchesTabList {

    // How far a branch under the header of its stack is indented.
    private static final String INDENT = "    ";

    private final LoadableList<List<BranchNode>> list;
    private final Keymap keymap = Keymap.standard();
    private final Clock clock;
    // The nodes the last render listed: built again on every render, and kept while they are the same, so the list
    // reselects only when they change.
    private Loadable<List<BranchNode>> nodes = Loadable.loading();

    /**
     * @param id         the id of the pane the branches are listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param clock      animates the spinners of the busy branches
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     */
    LocalBranchesList(String id, Consumer<Msg> dispatch, Clock clock, Theme.Highlights highlights) {
        this.clock = clock;
        list = new LoadableList<>(Messages.get("panel.branches.title"), id, LocalBranchesList::reselect, highlights);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        list.onKey(event -> PaneKeys.sending(keys(event), dispatch));
    }

    /**
     * A branch on two lines (ADR 45): its name and its spinner while it is busy; below them its age, how far it is
     * from its upstream and from its base (ADR 46), how it is doing on GitHub and whether it is checked out in
     * another worktree.
     *
     * @param distance how far the branch is from its base, if that is known
     * @param spinner  the label and the spinner frame of a busy branch, as one text
     */
    static LoadableList.Row row(
            Branch b,
            Duration since,
            Option<BaseDistance> distance,
            List<LoadableList.Seg> gitHub,
            Option<String> spinner) {
        return row(b, since, distance, Option.none(), gitHub, spinner);
    }

    /**
     * A branch on two lines, as {@link #row(Branch, Duration, Option, List, Option)}, with the parent it shows after
     * how far it is from its base, {@code ⇣1 on feat/a} (ADR 72).
     *
     * @param parent the parent the branch shows, if it shows one ({@link #shownParent})
     */
    static LoadableList.Row row(
            Branch b,
            Duration since,
            Option<BaseDistance> distance,
            Option<BranchName> parent,
            List<LoadableList.Seg> gitHub,
            Option<String> spinner) {
        return new LoadableList.Row(List.of(
                                b.current()
                                        ? LoadableList.Seg.styled(b.name().value(), Theme.Classes.CURRENT)
                                        : LoadableList.Seg.of(b.name().value()))
                        .appendAll(
                                BranchSegs.group(BranchSegs.spinnerSeg(spinner).toList())))
                .withDetail(List.of(LoadableList.Seg.of("  " + TimeText.age(since)))
                        .appendAll(BranchSegs.group(BranchSegs.sync(b)))
                        .appendAll(BranchSegs.group(
                                BranchSegs.baseDistance(b, distance).appendAll(BranchSegs.parent(parent))))
                        .appendAll(BranchSegs.group(gitHub))
                        .appendAll(BranchSegs.group(BranchSegs.worktree(b))));
    }

    /**
     * The parent {@code branch} shows on its line 2 (ADR 72): none for one built on a root, which is what one expects,
     * nor for one in a stack on GitHub, whose place in it says it, unless it does not have all the commits of that
     * parent, which GitHub does not merge (ADR 73).
     */
    static Option<BranchName> shownParent(Model model, Branch branch) {
        return model.repo()
                .parents()
                .get(branch.name())
                .filter(parent -> !GitHubBases.root(
                        parent,
                        AheadUpdate.main(model),
                        model.gitHub().protectedBranches().getOrElse(HashSet.empty())))
                .filter(parent -> model.gitHub()
                                .pullRequest(branch)
                                .flatMap(PullRequest::stack)
                                .isEmpty()
                        || model.repo().behindParent(branch.name()).isDefined());
    }

    /**
     * The selection follows its node when the list is reordered, also a branch that was renamed: when it is gone and
     * one branch came in its place. After a checkout it goes to the new current branch, which is listed first, or to
     * the header of its stack when that is closed.
     */
    private static int reselect(List<BranchNode> previous, List<BranchNode> next, int selected) {
        Option<BranchName> before = current(previous);
        Option<String> target = before.equals(current(next))
                ? Option.of(selected)
                        .filter(i -> i < previous.size())
                        .map(previous::get)
                        .map(node -> node instanceof BranchNode.Leaf leaf
                                ? leafKey(renamed(
                                        branches(previous),
                                        branches(next),
                                        leaf.branch().name()))
                                : node.key())
                : current(next)
                        .map(name -> next.find(node -> holds(node, name))
                                .map(BranchNode::key)
                                .getOrElse(() -> leafKey(name)));
        return target.map(key -> next.indexWhere(node -> node.key().equals(key)))
                .filter(i -> i >= 0)
                .getOrElse(selected);
    }

    private static String leafKey(BranchName name) {
        return "branch:" + name.value();
    }

    /**
     * Whether {@code node} shows the branch {@code name}: as itself, or under the header of a closed stack.
     */
    private static boolean holds(BranchNode node, BranchName name) {
        return switch (node) {
            case BranchNode.Leaf leaf -> leaf.branch().name().equals(name);
            case BranchNode.Header header ->
                !header.open()
                        && header.branches().exists(branch -> branch.name().equals(name));
            case BranchNode.Other other -> false;
        };
    }

    /**
     * The branches of {@code nodes}, also those of the stacks shown closed.
     */
    private static List<Branch> branches(List<BranchNode> nodes) {
        return nodes.flatMap(node -> switch (node) {
            case BranchNode.Leaf leaf -> List.of(leaf.branch());
            case BranchNode.Header header -> header.open() ? List.<Branch>empty() : header.branches();
            case BranchNode.Other other -> List.<Branch>empty();
        });
    }

    /**
     * The name {@code name} has in {@code next}: its own while it is there, else the only one that is new there.
     */
    private static BranchName renamed(List<Branch> previous, List<Branch> next, BranchName name) {
        List<BranchName> before = previous.map(Branch::name);
        List<BranchName> after = next.map(Branch::name);
        List<BranchName> added = after.removeAll(before);
        return after.contains(name) || added.size() != 1 ? name : added.head();
    }

    private static Option<BranchName> current(List<BranchNode> nodes) {
        return branches(nodes).find(Branch::current).map(Branch::name);
    }

    /**
     * The header of a stack on one line: whether it is open, its number and its base, then apart how many pull
     * requests it has (ADR 72), and whether it {@code diverged}: GitHub does not merge it until it is rebased (ADR 73).
     * While its branches are rebased or pushed, the {@code spinner} shows after its name, as on a busy branch, also
     * while the stack is closed (ADR 74).
     */
    static LoadableList.Row header(BranchNode.Header header, boolean diverged, Option<String> spinner) {
        return new LoadableList.Row(List.of(
                        LoadableList.Seg.styled(header.open() ? "▾ " : "▸ ", Theme.Classes.DIM),
                        LoadableList.Seg.styled(
                                Messages.get(
                                        "branches.stack.header",
                                        header.stack().number(),
                                        header.stack().base().getOrElse("?")),
                                Theme.Classes.PULL_REQUEST))
                .appendAll(BranchSegs.group(BranchSegs.spinnerSeg(spinner).toList()))
                .appendAll(BranchSegs.group(List.of(LoadableList.Seg.styled(
                        " " + Messages.get("branches.stack.size", header.stack().size()), Theme.Classes.DIM))))
                .appendAll(
                        diverged
                                ? BranchSegs.group(List.of(LoadableList.Seg.styled(
                                        " " + Messages.get("branches.stack.diverged"), Theme.Classes.ERROR)))
                                : List.empty()));
    }

    /**
     * Whether a branch of the stack of {@code header} does not have all the commits of the one below it in the stack
     * (ADR 73). One that is only behind the base of the stack is not: GitHub merges that.
     */
    static boolean diverged(Model model, BranchNode.Header header) {
        List<BranchName> members = header.branches().map(Branch::name);
        return header.branches()
                .exists(b -> model.repo().parents().get(b.name()).exists(members::contains)
                        && model.repo().behindParent(b.name()).isDefined());
    }

    /**
     * A pull request of a stack whose branch is not checked out, or that is merged or closed, on one dimmed line
     * under the header of its stack.
     */
    static LoadableList.Row other(BranchNode.Other other) {
        String key = switch (other.entry().state()) {
            case OPEN -> "branches.stack.notCheckedOut";
            case MERGED -> "branches.stack.merged";
            case CLOSED -> "branches.stack.closed";
        };
        return LoadableList.Row.of(
                LoadableList.Seg.styled(INDENT + Messages.get(key, other.entry().number()), Theme.Classes.DIM));
    }

    /**
     * {@code row} indented under the header of its stack, both its lines.
     */
    static LoadableList.Row indented(LoadableList.Row row) {
        return new LoadableList.Row(
                row.segs().prepend(LoadableList.Seg.of(INDENT)),
                row.detail().map(detail -> detail.prepend(LoadableList.Seg.of(INDENT))));
    }

    @Override
    public Panel render(Model model, boolean focused, boolean active, Line title) {
        Function<BranchRef, Option<String>> spinnerOf = BranchSegs.spinners(model, clock.instant());
        Loadable<List<BranchNode>> read = model.repo()
                .branches()
                .map(branches -> BranchNode.of(
                        branches,
                        model.gitHub()::pullRequest,
                        !model.repoView().unstacked(),
                        model.repoView().closedStacks(),
                        head -> fetched(model, head)));
        nodes = read.equals(nodes) ? nodes : read;
        Function<Branch, LoadableList.Row> rowOf = b -> row(
                b,
                Duration.between(b.lastActivity(), model.now()),
                model.repo().baseDistance().get(b.name()),
                shownParent(model, b),
                BranchSegs.gitHub(
                        model.gitHub().pullRequest(b),
                        model.gitHub().merge(model.gitHub().pullRequest(b)),
                        model.gitHub().checks(b),
                        model.gitHub().waiting(b),
                        BranchSegs.eta(model, model.gitHub().eta(b)),
                        model.now()),
                spinnerOf.apply(new BranchRef.Local(b.name())));
        return list.render(
                nodes,
                listed -> listed.map(node -> switch (node) {
                    case BranchNode.Leaf leaf ->
                        leaf.inStack() ? indented(rowOf.apply(leaf.branch())) : rowOf.apply(leaf.branch());
                    case BranchNode.Header header ->
                        header(
                                header,
                                diverged(model, header),
                                header.branches()
                                        .iterator()
                                        .flatMap(b -> spinnerOf.apply(new BranchRef.Local(b.name())))
                                        .headOption());
                    case BranchNode.Other other -> other(other);
                }),
                focused,
                active,
                title);
    }

    /**
     * The remote-tracking branch on GitHub of {@code head}, the branch a pull request merges from, if it was fetched.
     */
    static Option<RemoteBranch> fetched(Model model, String head) {
        return model.repo()
                .remoteBranches()
                .toOption()
                .getOrElse(List.empty())
                .iterator()
                .map(RemoteRef::branch)
                .find(remote ->
                        remote.branch().value().equals(head) && model.gitHub().mayBeOnGitHub(remote));
    }

    /**
     * The branch whose log shows: the highlighted one, the highest local branch of the stack whose header is
     * highlighted, whose log holds the whole stack, or the remote-tracking branch of the highlighted pull request that
     * is not checked out, if it was fetched.
     */
    @Override
    public Option<BranchRef> selectedRef() {
        return selected().flatMap(node -> switch (node) {
            case BranchNode.Leaf leaf ->
                Option.<BranchRef>of(new BranchRef.Local(leaf.branch().name()));
            case BranchNode.Header header -> header.top().map(BranchRef.Local::new);
            case BranchNode.Other other -> other.fetched().map(BranchRef.Remote::new);
        });
    }

    /**
     * The highlighted pull request of a stack, whose branch is neither checked out nor fetched, so that it has no log.
     */
    @Override
    public Option<Integer> unfetched() {
        return selected()
                .filter(BranchNode.Other.class::isInstance)
                .map(node -> (BranchNode.Other) node)
                .filter(other -> other.fetched().isEmpty())
                .map(other -> other.entry().number());
    }

    /**
     * The name of the highlighted branch, once the branches are shown; none while a header or a pull request whose
     * branch is not checked out is highlighted.
     */
    Option<BranchName> selectedBranch() {
        return selected()
                .flatMap(node -> node instanceof BranchNode.Leaf leaf
                        ? Option.of(leaf.branch().name())
                        : Option.none());
    }

    private Option<BranchNode> selected() {
        return list.selected(listed -> listed);
    }

    /**
     * What {@code event} does on the highlighted node: on a header, what the keys of a stack do, else what those of
     * the pane do that need no branch; on anything else, what the keys of a branch do.
     */
    private Option<Msg> keys(KeyEvent event) {
        return selected()
                .filter(BranchNode.Header.class::isInstance)
                .map(node -> ((BranchNode.Header) node).stack().number())
                .fold(
                        () -> keymap.handle(Scopes.BRANCHES, event, selectedBranch()),
                        stack -> keymap.handle(Scopes.STACKS, event, Option.of(stack))
                                .orElse(() -> keymap.handle(Scopes.BRANCHES, event, Option.none())));
    }
}
