package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;

import java.time.Instant;

/**
 * Builds models for tests: with fixed sample data through {@link Update}, or by running the real
 * {@link Program} synchronously.
 */
public final class TestModels {

    public static final RepoStatus CLEAN = new RepoStatus(Head.onBranch("main", new Sha("aaaa")), List.of());
    public static final RepoStatus DIRTY = new RepoStatus(Head.onBranch("main", new Sha("aaaa")), List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
    public static final RepoStatus MOVED = new RepoStatus(Head.onBranch("main", new Sha("bbbb")), List.of());
    public static final List<Branch> BRANCHES = List.of(new Branch("main", true, new Sha("aaaa")),
            new Branch("feature", false, new Sha("ffff")));
    public static final List<Commit> COMMITS = List.of(
            new Commit(new Sha("aaaa"), "Ada", "ada@example.com", Instant.EPOCH, "First", ""));
    public static final List<Commit> FEATURE_COMMITS = List.of(
            new Commit(new Sha("ffff"), "Ada", "ada@example.com", Instant.EPOCH, "Feature", ""));

    private TestModels() {
    }

    /**
     * A model with the sample status, branches and commits loaded and nothing running.
     */
    public static Model loaded() {
        Model model = Update.init("repo").model();
        model = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        model = Update.update(model, new LoadMsg.BranchesLoaded(BRANCHES)).model();
        model = Update.update(model, new LoadMsg.StashesLoaded(io.vavr.collection.List.of())).model();
        return Update.update(model, new LoadMsg.CommitsLoaded(COMMITS)).model();
    }

    /**
     * The model after all of the provider's data has been loaded.
     */
    public static Model loaded(GitInfoProvider provider) {
        return Program.start(provider, Runnable::run, Runnable::run, Runnable::run).model();
    }

    /**
     * The model after all of the provider's data, and the log of {@code branch}, has been loaded.
     */
    public static Model loaded(GitInfoProvider provider, String branch) {
        Program program = Program.start(provider, Runnable::run, Runnable::run, Runnable::run);
        program.dispatch(new SelectionMsg.BranchSelected(branch));
        return program.model();
    }

    /**
     * The model after all of the provider's data, and the diff of {@code files}, has been loaded.
     */
    public static Model withFilesSelected(GitInfoProvider provider, io.vavr.collection.List<FileEntry> files) {
        Program program = Program.start(provider, Runnable::run, Runnable::run, Runnable::run);
        program.dispatch(new SelectionMsg.FilesSelected(files));
        return program.model();
    }

    /**
     * The model before anything has been loaded.
     */
    public static Model loading(String repositoryName) {
        return Model.initial(repositoryName);
    }

    /**
     * {@code model} after an operation failed with {@code error}.
     */
    public static Model withLog(Model model, LogEntry... entries) {
        return model.withCommandLog(List.of(entries));
    }

    public static Model withError(Model model, String error) {
        return model.withError(Option.of(error));
    }

    /**
     * {@code model} with the log of {@code branch} still loading, while the log it replaced stays visible.
     */
    public static Model switchingBranch(Model model, String branch) {
        return model.withBranchLog(Option.of(BranchLog.loading(branch, model.branchLog())));
    }
}
