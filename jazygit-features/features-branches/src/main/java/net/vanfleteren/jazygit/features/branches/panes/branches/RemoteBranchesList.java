package net.vanfleteren.jazygit.features.branches.panes.branches;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.view.ShownTabs;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The remote-tracking branches. Space checks out the highlighted remote branch, n starts a new branch at it. A remote
 * branch that a local branch follows is dimmed. A remote branch shows its badges, like a local branch (ADR 167).
 */
class RemoteBranchesList implements BranchesTabList {

    private final LoadableList<List<RemoteRef>> list;
    private final Keymap keymap = Keymap.standard();
    private final BranchBadges badges;
    private final Clock clock;

    /**
     * @param id         the id of the pane the remote branches are listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param clock      animates the spinners of the busy branches
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     * @param badges     what the features show of a branch
     */
    RemoteBranchesList(
            String id,
            Consumer<Msg> dispatch,
            Clock clock,
            Theme.Highlights highlights,
            BranchBadges badges,
            Supplier<Keymap.Hosted<ShownTabs>> tabs) {
        this.clock = clock;
        this.badges = badges;
        list = new LoadableList<>(
                Messages.get("panel.remoteBranches.title"),
                id,
                LoadableList.Reselect.following(RemoteRef::branch),
                highlights);
        list.highlightSymbol("");
        list.whenEmpty(Messages.get("branches.panes.branches.remoteBranches.empty"));
        list.onKey(event -> PaneKeys.sending(
                keymap.handle(Scopes.REMOTE_BRANCHES, event, selectedRemoteBranch(), tabs.get()), dispatch));
    }

    /**
     * A remote branch on two lines (ADR 45): its name, dimmed when a local branch follows it, and its spinner while
     * it is busy; below them how long ago its tip was committed and its badges (ADR 167).
     *
     * @param tracked whether a local branch follows it
     * @param spinner the label and the spinner frame of a busy branch, as one text
     */
    static LoadableList.Row row(
            RemoteRef ref, Duration since, boolean tracked, List<LoadableList.Seg> badges, Option<String> spinner) {
        String name = ref.branch().shortName();
        String age = "  " + TimeText.age(since);
        return new LoadableList.Row(
                        List.of(tracked ? LoadableList.Seg.styled(name, Theme.Classes.DIM) : LoadableList.Seg.of(name))
                                .appendAll(BranchSegs.group(
                                        BranchSegs.spinnerSeg(spinner).toList())))
                .withDetail(List.of(LoadableList.Seg.of(age)).appendAll(BranchSegs.group(badges)));
    }

    /**
     * The times the list shows (ADR 124): the age of each remote branch, and the text of its badges that changes with
     * the time.
     */
    static List<ShownTime> shownTimes(Model model, BranchBadges badges) {
        return model.repo()
                .remoteBranches()
                .toOption()
                .toList()
                .flatMap(read -> read.map(ref ->
                        BranchSegs.shownTime(ref.tipTime(), badges.of(model, new BranchRef.Remote(ref.branch())))));
    }

    @Override
    public Panel render(Model model, boolean focused, boolean active, Line title) {
        Function<BranchRef, Option<String>> spinnerOf = BranchSegs.spinners(model, clock.instant());
        return list.render(
                model.repo().remoteBranches(),
                refs -> refs.map(ref -> row(
                        ref,
                        Duration.between(ref.tipTime(), model.now()),
                        model.repo().trackingBranch(ref.branch()).isDefined(),
                        badges.of(model, new BranchRef.Remote(ref.branch())).segs(),
                        spinnerOf.apply(new BranchRef.Remote(ref.branch())))),
                focused,
                active,
                title);
    }

    @Override
    public Option<BranchRef> selectedRef() {
        return selectedRemoteBranch().map(BranchRef.Remote::new);
    }

    /**
     * The highlighted remote branch, once the remote branches are shown.
     */
    Option<RemoteBranch> selectedRemoteBranch() {
        return list.selected(refs -> refs).map(RemoteRef::branch);
    }
}
