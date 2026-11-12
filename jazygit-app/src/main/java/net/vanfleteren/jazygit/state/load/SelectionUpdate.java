package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.load.SelectionMsg.BranchCommitSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.BranchSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.CommitFilesSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.CommitSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.DivergenceCommitSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.FilesSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.StashSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.UnchangedDirectorySelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.UnchangedFileSelected;
import net.vanfleteren.jazygit.state.load.SelectionMsg.UnfetchedSelected;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;

/**
 * The panes' highlights, and the data loaded for them.
 */
public final class SelectionUpdate {

    private SelectionUpdate() {}

    public static Next update(Model model, SelectionMsg msg) {
        return switch (msg) {
            case BranchSelected(var branch) -> branchSelected(model, branch);
            case UnfetchedSelected(int pullRequest) -> unfetchedSelected(model, pullRequest);
            case CommitSelected(var sha) -> commitSelected(model, sha);
            case DivergenceCommitSelected(var sha) -> divergenceCommitSelected(model, sha);
            case BranchCommitSelected(var sha) -> branchCommitSelected(model, sha);
            case FilesSelected(var files) -> filesSelected(model, files);
            case UnchangedFileSelected(String path) -> unchangedFileSelected(model, path);
            case UnchangedDirectorySelected(String path, int files) -> unchangedDirectorySelected(model, path, files);
            case CommitFilesSelected(var files) -> commitFilesSelected(model, files);
            case StashSelected(var sha) -> stashSelected(model, sha);
        };
    }

    /**
     * Loads the log of {@code branch}.
     */
    private static Next branchSelected(Model model, BranchRef branch) {
        return selected(
                model,
                branch,
                model.repoView().branchLog(),
                log -> model.mapRepoView(repoView -> repoView.withBranchLog(log).withUnfetched(Option.none())),
                log -> new LoadBranchLog(log, AheadUpdate.of(model, log)));
    }

    /**
     * No log is shown, nor read again as branches move.
     */
    private static Next unfetchedSelected(Model model, int pullRequest) {
        return Next.of(model.mapRepoView(
                repoView -> repoView.withBranchLog(Option.none()).withUnfetched(Option.of(pullRequest))));
    }

    /**
     * Loads the detail of commit {@code sha} of the commits pane.
     */
    private static Next commitSelected(Model model, Sha sha) {
        return selected(
                model,
                sha,
                model.repoView().commitDetail(),
                detail -> model.mapRepoView(repoView -> repoView.withCommitDetail(detail)),
                detail(model, Cmd.Slot.COMMIT_DETAIL));
    }

    /**
     * Loads the detail of commit {@code sha} of the divergence shown, if one is.
     */
    private static Next divergenceCommitSelected(Model model, Sha sha) {
        return model.repoView()
                .divergence()
                .map(view -> selected(
                        model,
                        sha,
                        view.detail(),
                        detail -> model.mapRepoView(
                                repoView -> repoView.withDivergence(Option.of(view.withDetail(detail)))),
                        detail(model, Cmd.Slot.DIVERGENCE_DETAIL)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Loads the detail of commit {@code sha} of the branch commits shown, if they are.
     */
    private static Next branchCommitSelected(Model model, Sha sha) {
        return model.repoView()
                .branchCommits()
                .map(view -> selected(
                        model,
                        sha,
                        view.detail(),
                        detail -> model.mapRepoView(
                                repoView -> repoView.withBranchCommits(Option.of(view.withDetail(detail)))),
                        detail(model, Cmd.Slot.BRANCH_COMMITS_DETAIL)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * A node with changes shows its diff, not the node without them highlighted before (ADR 108).
     */
    private static Next filesSelected(Model model, List<FileEntry> files) {
        Model changed = model.mapRepoView(repoView -> repoView.withUnchanged(Option.none()));
        return selected(
                changed,
                files,
                changed.repoView().fileDiff(),
                diff -> changed.mapRepoView(repoView -> repoView.withFileDiff(diff)),
                entries -> new LoadFileDiff(entries, model.preferences().diffOptions()));
    }

    /**
     * Reads the content of the file without changes at {@code path}.
     */
    private static Next unchangedFileSelected(Model model, String path) {
        return selected(
                model,
                path,
                model.repoView()
                        .unchanged()
                        .flatMap(node ->
                                node instanceof UnchangedNode.File(var content) ? Option.of(content) : Option.none()),
                content -> model.mapRepoView(repoView -> repoView.withUnchanged(content.map(UnchangedNode.File::new))),
                ReadFileContent::new);
    }

    /**
     * A directory without changes needs nothing read: it says how many files it holds.
     */
    private static Next unchangedDirectorySelected(Model model, String path, int files) {
        return Next.of(model.mapRepoView(
                repoView -> repoView.withUnchanged(Option.of(new UnchangedNode.Directory(path, files)))
                        .withVersions(HashMap.empty())));
    }

    /**
     * Loads the diff of {@code files} of the commit files shown, if they are.
     */
    private static Next commitFilesSelected(Model model, List<CommitFile> files) {
        return model.repoView()
                .commitFiles()
                .map(view -> selected(
                        model,
                        files,
                        view.diff(),
                        diff -> model.mapRepoView(repoView -> repoView.withCommitFiles(Option.of(view.withDiff(diff)))),
                        entries -> LoadCommitFileDiff.of(
                                view, entries, model.preferences().diffOptions())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Loads the detail of stash {@code sha}.
     */
    private static Next stashSelected(Model model, Sha sha) {
        return selected(
                model,
                sha,
                model.repoView().stashDetail(),
                detail -> model.mapRepoView(repoView -> repoView.withStashDetail(detail)),
                stash -> new LoadStashDetail(stash, model.preferences().diffOptions()));
    }

    private static Function<Sha, Load> detail(Model model, Cmd.Slot slot) {
        return sha -> new LoadCommitDetail(sha, model.preferences().diffOptions(), slot);
    }

    /**
     * Loads the preview of {@code key}, unless it is the one already shown. Another item highlighted drops the versions
     * of the files shown in place of their diffs (ADR 102): coming back to one shows its diff.
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
            Function<K, Load> load) {
        return shown.exists(preview -> preview.key().equals(key))
                ? Next.of(model)
                : Next.of(
                        with.apply(Option.of(Preview.loading(key, shown)))
                                .mapRepoView(view -> view.withVersions(HashMap.empty())),
                        load.apply(key));
    }
}
