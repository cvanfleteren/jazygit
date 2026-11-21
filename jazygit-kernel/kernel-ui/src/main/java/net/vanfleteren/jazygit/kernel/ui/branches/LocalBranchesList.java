package net.vanfleteren.jazygit.kernel.ui.branches;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.load.BranchFiles;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.state.view.NoLog;
import net.vanfleteren.jazygit.kernel.state.view.Unlisted;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups.Group;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The local branches, with the name of the current branch in bold and a colour of its own. Space checks out the highlighted branch, P pushes it, R asks
 * for a new name for it, d asks where to delete it, n asks for the name of a new branch starting at it, w checks it
 * out in a new worktree.
 * A branch shows what the features show of it, its badges, such as its pull request on GitHub (ADR 167).
 * <p>
 * A group the features make of branches, as a stack of pull requests on GitHub, is one node: a header, and what is in
 * it under it, indented (ADR 72, ADR 167). The keys on the header are those of the group; the keys of a branch do
 * nothing there. What is in it but is no local branch shows the log of its remote-tracking branch, once that is
 * fetched. The groups can also be left out, and their branches listed as any other (ADR 87).
 */
class LocalBranchesList implements BranchesTabList {

    // How far a branch under the header of its group is indented.
    private static final String INDENT = "    ";

    private final LoadableList<List<BranchNode>> list;
    private final Keymap keymap = Keymap.standard();
    private final BranchBadges badges;
    private final BranchGroups groups;
    private final Clock clock;
    private final PaneId pane;
    // Whether the files pane was locked to the branches at the last render (ADR 149).
    private boolean locked;
    // The nodes the last render listed: built again on every render, and kept while they are the same, so the list
    // reselects only when they change.
    private Loadable<List<BranchNode>> nodes = Loadable.loading();

    /**
     * @param id         the id of the pane the branches are listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param clock      animates the spinners of the busy branches
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     * @param badges     what the features show of a branch
     * @param groups     the groups the features make of the branches
     */
    LocalBranchesList(
            String id,
            Consumer<Msg> dispatch,
            Clock clock,
            Theme.Highlights highlights,
            BranchBadges badges,
            BranchGroups groups) {
        this.clock = clock;
        this.badges = badges;
        this.groups = groups;
        this.pane = new PaneId(id);
        list = new LoadableList<>(Messages.get("panel.branches.title"), id, LocalBranchesList::reselect, highlights);
        // The highlight alone marks the selection; a symbol would indent the rows.
        list.highlightSymbol("");
        // Escape lets go of the files locked to the branches, before it does anything else (ADR 149).
        list.onKey(event -> PaneKeys.sending(
                locked && event.isCancel() ? Option.of(new SelectionMsg.FilesUnlocked()) : keys(event), dispatch));
    }

    /**
     * A branch on two lines (ADR 45): its name and its spinner while it is busy; below them its age, how far it is
     * from its upstream and from its base (ADR 46), its badges (ADR 167) and whether it is checked out in another
     * worktree.
     *
     * @param distance how far the branch is from its base, if that is known
     * @param spinner  the label and the spinner frame of a busy branch, as one text
     */
    static LoadableList.Row row(
            Branch b,
            Duration since,
            Option<BaseDistance> distance,
            List<LoadableList.Seg> badges,
            Option<String> spinner) {
        return row(b, since, distance, Option.none(), badges, spinner);
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
            List<LoadableList.Seg> badges,
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
                        .appendAll(BranchSegs.group(badges))
                        .appendAll(BranchSegs.group(BranchSegs.worktree(b))));
    }

    /**
     * The parent {@code branch} shows on its line 2 (ADR 72): none for one built on a root, which is what one expects,
     * nor for one in a group of {@code groups}, as a stack on GitHub, whose place in it says it, unless it does not
     * have all the commits of that parent, which GitHub does not merge (ADR 73).
     */
    static Option<BranchName> shownParent(Model model, Branch branch, List<Group> groups) {
        BranchBases bases = BranchBases.in(model);
        return model.repo()
                .parents()
                .get(branch.name())
                .filter(parent -> !bases.root(parent, AheadUpdate.main(model)))
                .filter(parent -> !groups.exists(group -> group.members().contains(branch.name()))
                        || model.repo().behindParent(branch.name()).isDefined());
    }

    /**
     * The selection follows its node when the list is reordered, also a branch that was renamed: when it is gone and
     * one branch came in its place. After a checkout it goes to the new current branch, which is listed first, or to
     * the header of its stack when that is closed.
     */
    private static int reselect(List<BranchNode> previous, List<BranchNode> next, int selected) {
        Option<BranchName> before = current(previous);
        boolean detached = detached(next);
        Option<String> target = before.equals(current(next)) && detached == detached(previous)
                ? Option.of(selected)
                        .filter(i -> i < previous.size())
                        .map(previous::get)
                        .map(node -> node instanceof BranchNode.Leaf leaf
                                ? leafKey(renamed(
                                        branches(previous),
                                        branches(next),
                                        leaf.branch().name()))
                                : node.key())
                // Once HEAD is detached, it is what is checked out, listed first.
                : detached
                        ? next.find(BranchNode.Detached.class::isInstance).map(BranchNode::key)
                        : current(next)
                                .map(name -> next.find(node -> holds(node, name))
                                        .map(BranchNode::key)
                                        .getOrElse(() -> leafKey(name)));
        return target.map(key -> next.indexWhere(node -> node.key().equals(key)))
                .filter(i -> i >= 0)
                .getOrElse(selected);
    }

    private static boolean detached(List<BranchNode> nodes) {
        return nodes.exists(BranchNode.Detached.class::isInstance);
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
            case BranchNode.Detached detached -> false;
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
            case BranchNode.Detached detached -> List.<Branch>empty();
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
     * The header of a group on one line (ADR 72, ADR 167): whether it is open, its title, then what its feature shows
     * after it, each apart. While its branches are busy, the {@code spinner} shows after its title, as on a busy
     * branch, also while the group is closed (ADR 74).
     */
    static LoadableList.Row header(BranchNode.Header header, Option<String> spinner) {
        return new LoadableList.Row(List.of(LoadableList.Seg.styled(header.open() ? "▾ " : "▸ ", Theme.Classes.DIM))
                .appendAll(header.group().title())
                .appendAll(BranchSegs.group(BranchSegs.spinnerSeg(spinner).toList()))
                .appendAll(header.group().after().flatMap(seg -> BranchSegs.group(List.of(seg)))));
    }

    /**
     * What belongs to a group but is no local branch here, on one dimmed line under the header of its group.
     */
    static LoadableList.Row other(BranchNode.Other other) {
        return LoadableList.Row.of(
                LoadableList.Seg.styled(INDENT + other.entry().text(), Theme.Classes.DIM));
    }

    /**
     * A detached HEAD on one line, as the checked out branch is shown: no branch is checked out.
     */
    static LoadableList.Row detachedRow(BranchNode.Detached detached) {
        return LoadableList.Row.of(LoadableList.Seg.styled(
                Messages.get("branches.detached", detached.at().shortSha()), Theme.Classes.CURRENT));
    }

    /**
     * {@code row} indented under the header of its stack, both its lines.
     */
    static LoadableList.Row indented(LoadableList.Row row) {
        return new LoadableList.Row(
                row.segs().prepend(LoadableList.Seg.of(INDENT)),
                row.detail().map(detail -> detail.prepend(LoadableList.Seg.of(INDENT))));
    }

    /**
     * The times the list shows (ADR 124): the age of each branch, and the text of its badges that changes with the
     * time.
     */
    static List<ShownTime> shownTimes(Model model, BranchBadges badges) {
        return model.repo()
                .branches()
                .toOption()
                .toList()
                .flatMap(read -> read.map(
                        b -> BranchSegs.shownTime(b.lastActivity(), badges.of(model, new BranchRef.Local(b.name())))));
    }

    @Override
    public Panel render(Model model, boolean focused, boolean active, Line title) {
        locked = PaneKeys.lockedTo(model, pane);
        Function<BranchRef, Option<String>> spinnerOf = BranchSegs.spinners(model, clock.instant());
        List<Group> grouping = model.repo()
                .branches()
                .toOption()
                .map(branches -> groups.of(model, branches))
                .getOrElse(List.empty());
        Loadable<List<BranchNode>> read = model.repo()
                .branches()
                .map(branches -> BranchNode.of(
                        branches,
                        grouping,
                        model.repoView().branches().grouped(),
                        model.repoView().branches().closedGroups()))
                .map(branchNodes -> detachedHead(model)
                        .map(at -> branchNodes.prepend(new BranchNode.Detached(at)))
                        .getOrElse(branchNodes));
        nodes = read.equals(nodes) ? nodes : read;
        Function<Branch, LoadableList.Row> rowOf = b -> row(
                b,
                Duration.between(b.lastActivity(), model.now()),
                model.repo().baseDistance().get(b.name()),
                shownParent(model, b, grouping),
                badges.of(model, new BranchRef.Local(b.name())).segs(),
                spinnerOf.apply(new BranchRef.Local(b.name())));
        return list.render(
                nodes,
                listed -> listed.map(node -> switch (node) {
                    case BranchNode.Leaf leaf ->
                        leaf.inGroup() ? indented(rowOf.apply(leaf.branch())) : rowOf.apply(leaf.branch());
                    case BranchNode.Header header ->
                        header(
                                header,
                                header.branches()
                                        .iterator()
                                        .flatMap(b -> spinnerOf.apply(new BranchRef.Local(b.name())))
                                        .headOption());
                    case BranchNode.Other other -> other(other);
                    case BranchNode.Detached detached -> detachedRow(detached);
                }),
                focused,
                active,
                title);
    }

    /**
     * The commit HEAD is detached at, if it is. Not while a rebase goes on, which detaches HEAD meanwhile and which the
     * status line names, as it leaves the branch it rebases out.
     */
    static Option<Sha> detachedHead(Model model) {
        return model.repo()
                .status()
                .toOption()
                .filter(status -> !status.inProgress().exists(InProgress.Rebasing.class::isInstance))
                .map(RepoStatus::head)
                .flatMap(head -> head instanceof Head.Detached(Sha at) ? Option.of(at) : Option.none());
    }

    /**
     * The branch whose log shows: the highlighted one, the last local branch of the group whose header is highlighted,
     * whose log holds the whole of a stack, or the remote-tracking branch of what belongs to a group but is not checked
     * out, if it was fetched.
     */
    @Override
    public Option<BranchRef> selectedRef() {
        return selected().flatMap(node -> switch (node) {
            case BranchNode.Leaf leaf ->
                Option.<BranchRef>of(new BranchRef.Local(leaf.branch().name()));
            case BranchNode.Header header -> header.top().map(BranchRef.Local::new);
            case BranchNode.Other other -> other.entry().log().map(BranchRef.Remote::new);
            case BranchNode.Detached detached -> Option.<BranchRef>none();
        });
    }

    /**
     * What the files pane lists for the highlighted row (ADR 141): a branch its files against its base, the main
     * branch what its tip changed, what is checked out here, a branch or a detached HEAD, the files of the working
     * tree; a group and what belongs to it but is not checked out here have none.
     */
    @Override
    public Option<Followed> followed(Model model) {
        return selected().flatMap(node -> switch (node) {
            case BranchNode.Leaf leaf -> BranchFiles.of(model, leaf.branch().name());
            case BranchNode.Header header -> Option.<Followed>of(new Followed.Nothing(new Unlisted.Group()));
            case BranchNode.Other other -> Option.<Followed>of(new Followed.Nothing(new Unlisted.NotHere()));
            case BranchNode.Detached detached -> Option.<Followed>of(new Followed.WorkingTree());
        });
    }

    /**
     * Why the highlighted row has no log: what belongs to a group but is neither checked out nor fetched, or a
     * detached HEAD, whose log the commits pane shows.
     */
    @Override
    public Option<NoLog> noLog() {
        return selected().flatMap(node -> switch (node) {
            case BranchNode.Other other
            when other.entry().log().isEmpty() ->
                Option.<NoLog>of(new NoLog.Unfetched(other.entry().unknown()));
            case BranchNode.Detached detached -> Option.<NoLog>of(new NoLog.Detached(detached.at()));
            default -> Option.<NoLog>none();
        });
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
     * What {@code event} does on the highlighted node: on a header, what the keys of its group do, else what those of
     * the pane do that need no branch; on anything else, what the keys of a branch do.
     */
    private Option<Msg> keys(KeyEvent event) {
        return selected()
                .filter(BranchNode.Header.class::isInstance)
                .map(node -> ((BranchNode.Header) node).group().keys())
                .fold(
                        () -> keymap.handle(Scopes.BRANCHES, event, selectedBranch()),
                        keys -> keys.handle(keymap, event)
                                .orElse(() -> keymap.handle(Scopes.BRANCHES, event, Option.none())));
    }
}
