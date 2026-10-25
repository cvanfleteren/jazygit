package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.state.BranchCommitsView;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * How the diffs are read. Changing it reads the diffs that are shown again, which keep showing as they were
 * until they are read; what was read before the change is dropped when it arrives.
 */
public final class DiffOptionsUpdate {

    private DiffOptionsUpdate() {}

    public static Next update(Model model, DiffOptionsMsg msg) {
        return switch (msg) {
            // The key works everywhere, but not in a dialog, whose diff does not change.
            case DiffOptionsMsg.WhitespaceToggled() when model.popup().isDefined() -> Next.of(model);
            case DiffOptionsMsg.WhitespaceToggled() ->
                changed(
                        model,
                        model.preferences()
                                .diffOptions()
                                .withIgnoreWhitespace(
                                        !model.preferences().diffOptions().ignoreWhitespace()));
        };
    }

    private static Next changed(Model model, DiffOptions options) {
        Option<DivergenceView> divergence =
                model.repoView().divergence().map(view -> view.withDetail(reloading(view.detail())));
        Option<BranchCommitsView> branchCommits =
                model.repoView().branchCommits().map(view -> view.withDetail(reloading(view.detail())));
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
                .appendAll(
                        model.repoView().stashDetail().map(detail -> new Cmd.LoadStashDetail(detail.key(), options)));
        return new Next(
                model.mapPreferences(preferences -> preferences.withDiffOptions(options))
                        .mapRepoView(repoView -> repoView.withFileDiff(reloading(repoView.fileDiff()))
                                .withCommitDetail(reloading(repoView.commitDetail()))
                                .withStashDetail(reloading(repoView.stashDetail()))
                                .withDivergence(divergence)
                                .withBranchCommits(branchCommits)),
                loads);
    }

    /**
     * {@code preview} loading again, showing what it shows until it is read.
     */
    private static <K, V> Option<Preview<K, V>> reloading(Option<Preview<K, V>> preview) {
        return preview.map(shown -> Preview.loading(shown.key(), Option.of(shown)));
    }
}
