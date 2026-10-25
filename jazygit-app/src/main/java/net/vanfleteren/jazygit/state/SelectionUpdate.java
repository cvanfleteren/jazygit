package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.Cmd.LoadStashDetail;
import net.vanfleteren.jazygit.state.SelectionMsg.BranchCommitSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.BranchSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.CommitSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.DivergenceCommitSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.FilesSelected;
import net.vanfleteren.jazygit.state.SelectionMsg.StashSelected;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The panes' highlights, and the data loaded for them.
 */
public final class SelectionUpdate {

    private SelectionUpdate() {}

    public static Next update(Model model, SelectionMsg msg) {
        return switch (msg) {
            case BranchSelected(var branch) ->
                selected(
                        model,
                        branch,
                        model.repoView().branchLog(),
                        log -> model.mapRepoView(repoView -> repoView.withBranchLog(log)),
                        log -> new LoadBranchLog(log, AheadUpdate.of(model, log)));
            case CommitSelected(var sha) ->
                selected(
                        model,
                        sha,
                        model.repoView().commitDetail(),
                        detail -> model.mapRepoView(repoView -> repoView.withCommitDetail(detail)),
                        detail(model, Cmd.Slot.COMMIT_DETAIL));
            case DivergenceCommitSelected(var sha) ->
                model.repoView()
                        .divergence()
                        .map(view -> selected(
                                model,
                                sha,
                                view.detail(),
                                detail -> model.mapRepoView(
                                        repoView -> repoView.withDivergence(Option.of(view.withDetail(detail)))),
                                detail(model, Cmd.Slot.DIVERGENCE_DETAIL)))
                        .getOrElse(() -> Next.of(model));
            case BranchCommitSelected(var sha) ->
                model.repoView()
                        .branchCommits()
                        .map(view -> selected(
                                model,
                                sha,
                                view.detail(),
                                detail -> model.mapRepoView(
                                        repoView -> repoView.withBranchCommits(Option.of(view.withDetail(detail)))),
                                detail(model, Cmd.Slot.BRANCH_COMMITS_DETAIL)))
                        .getOrElse(() -> Next.of(model));
            case FilesSelected(var files) ->
                selected(
                        model,
                        files,
                        model.repoView().fileDiff(),
                        diff -> model.mapRepoView(repoView -> repoView.withFileDiff(diff)),
                        entries -> new LoadFileDiff(entries, model.preferences().diffOptions()));
            case StashSelected(var sha) ->
                selected(
                        model,
                        sha,
                        model.repoView().stashDetail(),
                        detail -> model.mapRepoView(repoView -> repoView.withStashDetail(detail)),
                        stash -> new LoadStashDetail(stash, model.preferences().diffOptions()));
        };
    }

    private static Function<Sha, Cmd.Load> detail(Model model, Cmd.Slot slot) {
        return sha -> new LoadCommitDetail(sha, model.preferences().diffOptions(), slot);
    }

    /**
     * Loads the preview of {@code key}, unless it is the one already shown.
     *
     * @param shown the preview that is shown
     * @param with  the model with another preview shown
     * @param load  the load of the preview of a key
     */
    private static <K, V> Next selected(
            Model model,
            K key,
            Option<Preview<K, V>> shown,
            Function<Option<Preview<K, V>>, Model> with,
            Function<K, Cmd.Load> load) {
        return shown.exists(preview -> preview.key().equals(key))
                ? Next.of(model)
                : Next.of(with.apply(Option.of(Preview.loading(key, shown))), load.apply(key));
    }
}
