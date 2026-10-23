package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;

/**
 * The local branches, with the name of the current branch in bold and a colour of its own. Space checks out the highlighted branch, P pushes it, R asks
 * for a new name for it, d asks where to delete it, n asks for the name of a new branch starting at it, w checks it
 * out in a new worktree.
 * A branch with a pull request shows its number, its CI checks and its review; one without shows the CI checks
 * of the branch it follows on GitHub.
 */
class LocalBranchesList implements BranchesTabList {

    private final LoadableList<List<Branch>> list;
    private final Keymap keymap = Keymap.standard();
    private final Clock clock;

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
        list.onKey(event -> PaneKeys.sending(keymap.handle(Scopes.BRANCHES, event, selectedBranch()), dispatch));
    }

    /**
     * @param spinner the label and the spinner frame of a busy branch, as one text
     */
    static LoadableList.Row row(Branch b, Duration since, List<LoadableList.Seg> gitHub, Option<String> spinner) {
        return new LoadableList.Row(List.of(
                        LoadableList.Seg.of("%4s ".formatted(TimeText.age(since))),
                        b.current()
                                ? LoadableList.Seg.styled(b.name().value(), Theme.Classes.CURRENT)
                                : LoadableList.Seg.of(b.name().value()))
                .appendAll(BranchSegs.worktree(b))
                .appendAll(BranchSegs.sync(b))
                .appendAll(gitHub)
                .appendAll(BranchSegs.spinnerSeg(spinner)));
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

    @Override
    public Panel render(Model model, boolean focused, boolean active, Line title) {
        Function<BranchRef, Option<String>> spinnerOf = BranchSegs.spinners(model, clock.instant());
        return list.render(
                model.repo().branches(),
                branches -> branches.map(b -> row(
                        b,
                        Duration.between(b.tipTime(), model.now()),
                        BranchSegs.gitHub(
                                model.gitHub().pullRequest(b),
                                model.gitHub().merge(model.gitHub().pullRequest(b)),
                                model.gitHub().checks(b)),
                        spinnerOf.apply(new BranchRef.Local(b.name())))),
                focused,
                active,
                title);
    }

    @Override
    public Option<BranchRef> selectedRef() {
        return selectedBranch().map(BranchRef.Local::new);
    }

    /**
     * The name of the highlighted branch, once the branches are shown.
     */
    Option<Branch.Name> selectedBranch() {
        return list.selected(branches -> branches).map(Branch::name);
    }
}
