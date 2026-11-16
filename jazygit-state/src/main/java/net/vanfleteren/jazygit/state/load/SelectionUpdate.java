package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.FileContent;
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
import net.vanfleteren.jazygit.state.preferences.Preferences;
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
            case UnfetchedSelected(PrNumber pullRequest) -> unfetchedSelected(model, pullRequest);
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
                model.repoView().preview().branchLog(),
                log -> model.mapRepoView(repoView -> repoView.mapPreview(preview -> preview.withBranchLog(log))
                        .mapBranches(branches -> branches.withUnfetched(Option.none()))),
                log -> new LoadBranchLog(log, AheadUpdate.of(model, log)));
    }

    /**
     * No log is shown, nor read again as branches move.
     */
    private static Next unfetchedSelected(Model model, PrNumber pullRequest) {
        return Next.of(
                model.mapRepoView(repoView -> repoView.mapPreview(preview -> preview.withBranchLog(Option.none()))
                        .mapBranches(branches -> branches.withUnfetched(Option.of(pullRequest)))));
    }

    /**
     * Loads the detail of commit {@code sha} of the commits pane.
     */
    private static Next commitSelected(Model model, Sha sha) {
        return selected(
                model,
                sha,
                model.repoView().preview().commitDetail(),
                detail ->
                        model.mapRepoView(repoView -> repoView.mapPreview(preview -> preview.withCommitDetail(detail))),
                detail(model, Cmd.Slot.COMMIT_DETAIL));
    }

    /**
     * Loads the detail of commit {@code sha} of the divergence shown, if one is.
     */
    private static Next divergenceCommitSelected(Model model, Sha sha) {
        return model.repoView()
                .branches()
                .divergence()
                .map(view -> selected(
                        model,
                        sha,
                        view.detail(),
                        detail -> model.mapRepoView(repoView -> repoView.mapBranches(
                                branches -> branches.withDivergence(Option.of(view.withDetail(detail))))),
                        detail(model, Cmd.Slot.DIVERGENCE_DETAIL)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Loads the detail of commit {@code sha} of the branch commits shown, if they are.
     */
    private static Next branchCommitSelected(Model model, Sha sha) {
        return model.repoView()
                .branches()
                .branchCommits()
                .map(view -> selected(
                        model,
                        sha,
                        view.detail(),
                        detail -> model.mapRepoView(repoView -> repoView.mapBranches(
                                branches -> branches.withBranchCommits(Option.of(view.withDetail(detail))))),
                        detail(model, Cmd.Slot.BRANCH_COMMITS_DETAIL)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * A node with changes shows its diff, not the node without them highlighted before (ADR 108).
     */
    private static Next filesSelected(Model model, List<FileEntry> files) {
        Model changed = model.mapRepoView(repoView -> repoView.mapFiles(view -> view.withUnchanged(Option.none())));
        return selected(
                changed,
                files,
                changed.repoView().preview().fileDiff(),
                diff -> changed.mapRepoView(repoView -> repoView.mapPreview(preview -> preview.withFileDiff(diff))),
                entries ->
                        new LoadFileDiff(entries, model.get(Preferences.SLICE).diffOptions()));
    }

    /**
     * Reads the content of the file without changes at {@code path}: in the commit whose files are listed, while they
     * are (ADR 128), else on disk (ADR 108).
     */
    private static Next unchangedFileSelected(Model model, String path) {
        return model.repoView()
                .files()
                .commitFiles()
                .map(view -> selected(
                        model,
                        path,
                        file(view.unchanged()),
                        content -> model.mapRepoView(repoView -> repoView.mapFiles(part -> part.withCommitFiles(
                                Option.of(view.withUnchanged(content.map(UnchangedNode.File::new)))))),
                        read -> new ReadCommitFileContent(view.of(), read)))
                .getOrElse(() -> workingFileSelected(model, path));
    }

    private static Option<Preview<String, FileContent>> file(Option<UnchangedNode> node) {
        return node.flatMap(
                shown -> shown instanceof UnchangedNode.File(var content) ? Option.of(content) : Option.none());
    }

    private static Next workingFileSelected(Model model, String path) {
        return selected(
                model,
                path,
                file(model.repoView().files().unchanged()),
                content -> model.mapRepoView(repoView ->
                        repoView.mapFiles(part -> part.withUnchanged(content.map(UnchangedNode.File::new)))),
                ReadFileContent::new);
    }

    /**
     * A directory without changes needs nothing read: it says how many files it holds, in the commit whose files are
     * listed, while they are.
     */
    private static Next unchangedDirectorySelected(Model model, String path, int files) {
        UnchangedNode node = new UnchangedNode.Directory(path, files);
        return Next.of(model.mapRepoView(repoView -> repoView.files()
                .commitFiles()
                .map(view ->
                        repoView.mapFiles(part -> part.withCommitFiles(Option.of(view.withUnchanged(Option.of(node))))))
                .getOrElse(() -> repoView.mapFiles(part -> part.withUnchanged(Option.of(node))))
                .withoutVersions()));
    }

    /**
     * Loads the diff of {@code files} of the commit files shown, if they are. A node with changes shows its diff, not
     * the node without them highlighted before (ADR 128).
     */
    private static Next commitFilesSelected(Model model, List<CommitFile> files) {
        Model changed = model.mapRepoView(repoView -> repoView.mapFiles(
                part -> part.withCommitFiles(part.commitFiles().map(open -> open.withUnchanged(Option.none())))));
        return changed.repoView()
                .files()
                .commitFiles()
                .map(view -> selected(
                        changed,
                        files,
                        view.diff(),
                        diff -> changed.mapRepoView(repoView ->
                                repoView.mapFiles(part -> part.withCommitFiles(Option.of(view.withDiff(diff))))),
                        entries -> LoadCommitFileDiff.of(
                                view, entries, model.get(Preferences.SLICE).diffOptions())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Loads the detail of stash {@code sha}.
     */
    private static Next stashSelected(Model model, Sha sha) {
        return selected(
                model,
                sha,
                model.repoView().preview().stashDetail(),
                detail ->
                        model.mapRepoView(repoView -> repoView.mapPreview(preview -> preview.withStashDetail(detail))),
                stash -> new LoadStashDetail(stash, model.get(Preferences.SLICE).diffOptions()));
    }

    private static Function<Sha, Load> detail(Model model, Cmd.Slot slot) {
        return sha -> new LoadCommitDetail(sha, model.get(Preferences.SLICE).diffOptions(), slot);
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
                        with.apply(Option.of(Preview.loading(key, shown))).mapRepoView(view -> view.withoutVersions()),
                        load.apply(key));
    }
}
