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
 * The remote-tracking branches. Space checks out the highlighted remote branch, n starts a new branch at it. A remote
 * branch that a local branch follows is dimmed. A remote branch on GitHub shows its pull request or its CI checks,
 * like a local branch.
 */
class RemoteBranchesList implements BranchesTabList {

    private final LoadableList<List<RemoteRef>> list;
    private final Keymap keymap = Keymap.standard();
    private final Clock clock;

    /**
     * @param id         the id of the pane the remote branches are listed in
     * @param dispatch   receives the messages for the user's actions, on the render thread
     * @param clock      animates the spinners of the busy branches
     * @param highlights how the highlighted branch is shown, also when the pane is not in use
     */
    RemoteBranchesList(String id, Consumer<Msg> dispatch, Clock clock, Theme.Highlights highlights) {
        this.clock = clock;
        list = new LoadableList<>(
                Messages.get("panel.remoteBranches.title"),
                id,
                LoadableList.Reselect.following(RemoteRef::branch),
                highlights);
        list.highlightSymbol("");
        list.whenEmpty(Messages.get("remoteBranches.empty"));
        list.onKey(event ->
                PaneKeys.sending(keymap.handle(Scopes.REMOTE_BRANCHES, event, selectedRemoteBranch()), dispatch));
    }

    /**
     * A remote branch: how long ago its tip was committed and its name, dimmed when a local branch follows it; then
     * how it is doing on GitHub and its spinner while it is busy.
     *
     * @param tracked whether a local branch follows it
     * @param spinner the label and the spinner frame of a busy branch, as one text
     */
    static LoadableList.Row row(
            RemoteRef ref, Duration since, boolean tracked, List<LoadableList.Seg> gitHub, Option<String> spinner) {
        String text = "%4s ".formatted(TimeText.age(since)) + ref.branch().shortName();
        return new LoadableList.Row(
                List.of(tracked ? LoadableList.Seg.styled(text, Theme.Classes.DIM) : LoadableList.Seg.of(text))
                        .appendAll(gitHub)
                        .appendAll(BranchSegs.spinnerSeg(spinner)));
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
                        BranchSegs.gitHub(
                                model.gitHub().pullRequest(ref.branch()),
                                model.gitHub().merge(model.gitHub().pullRequest(ref.branch())),
                                model.gitHub().checks(ref.branch()),
                                BranchSegs.eta(model, model.gitHub().eta(ref.branch()))),
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
