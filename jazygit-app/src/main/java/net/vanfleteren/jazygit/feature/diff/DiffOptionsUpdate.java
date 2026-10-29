package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.BranchCommitsView;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.CommitFilesView;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * How the diffs are read, and how the changes of the commits and the stashes are drawn. Changing either reads the
 * diffs that are shown again, which keep showing as they were until they are read; what was read before the change is
 * dropped when it arrives. The style decides how a merge is read (ADR 44), so it changes how the diffs are read too.
 */
public final class DiffOptionsUpdate {

    private DiffOptionsUpdate() {}

    public static Next update(Model model, DiffOptionsMsg msg) {
        return switch (msg) {
            // The keys work everywhere, but not in a dialog, whose diff does not change.
            case DiffOptionsMsg m when model.popup().isDefined() -> Next.of(model);
            case DiffOptionsMsg.WhitespaceToggled() ->
                changed(model.mapPreferences(
                        preferences -> preferences.withIgnoreWhitespace(!preferences.ignoreWhitespace())));
            case DiffOptionsMsg.StyleToggled() -> styleToggled(model);
        };
    }

    /**
     * Draws the changes in the other style from now on, keeping it for the next session.
     */
    private static Next styleToggled(Model model) {
        Settings previous = model.preferences().settings();
        Settings chosen = previous.withDiffStyle(previous.diffStyle().toggled());
        Next changed = changed(model.mapPreferences(preferences -> preferences.withSettings(chosen)));
        return new Next(changed.model(), changed.cmds().append(new Cmd.SaveSettings(previous, chosen)));
    }

    /**
     * Reads the diffs shown in {@code model} again, with the options it has now.
     */
    private static Next changed(Model model) {
        DiffOptions options = model.preferences().diffOptions();
        Option<DivergenceView> divergence =
                model.repoView().divergence().map(view -> view.withDetail(reloading(view.detail())));
        Option<BranchCommitsView> branchCommits =
                model.repoView().branchCommits().map(view -> view.withDetail(reloading(view.detail())));
        Option<CommitFilesView> commitFiles =
                model.repoView().commitFiles().map(view -> view.withDiff(reloading(view.diff())));
        List<Cmd> loads = List.<Cmd>empty()
                .appendAll(model.repoView().fileDiff().map(diff -> new Cmd.LoadFileDiff(diff.key(), options)))
                .appendAll(model.repoView()
                        .commitDetail()
                        .map(detail -> new Cmd.LoadCommitDetail(detail.key(), options, Cmd.Slot.COMMIT_DETAIL)))
                .appendAll(model.repoView()
                        .divergence()
                        .flatMap(DivergenceView::detail)
                        .map(detail -> new Cmd.LoadCommitDetail(detail.key(), options, Cmd.Slot.DIVERGENCE_DETAIL)))
                .appendAll(model.repoView()
                        .branchCommits()
                        .flatMap(BranchCommitsView::detail)
                        .map(detail -> new Cmd.LoadCommitDetail(detail.key(), options, Cmd.Slot.BRANCH_COMMITS_DETAIL)))
                .appendAll(model.repoView().stashDetail().map(detail -> new Cmd.LoadStashDetail(detail.key(), options)))
                .appendAll(model.repoView()
                        .commitFiles()
                        .flatMap(view -> view.diff().map(diff -> view.diffLoad(diff.key(), options))));
        return new Next(
                model.mapRepoView(repoView -> repoView.withFileDiff(reloading(repoView.fileDiff()))
                        .withCommitDetail(reloading(repoView.commitDetail()))
                        .withStashDetail(reloading(repoView.stashDetail()))
                        .withDivergence(divergence)
                        .withBranchCommits(branchCommits)
                        .withCommitFiles(commitFiles)),
                loads);
    }

    /**
     * {@code preview} loading again, showing what it shows until it is read.
     */
    private static <K, V> Option<Preview<K, V>> reloading(Option<Preview<K, V>> preview) {
        return preview.map(shown -> Preview.loading(shown.key(), Option.of(shown)));
    }
}
