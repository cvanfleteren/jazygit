package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.ui.ErrorText;

/**
 * Builds models for tests: with fixed sample data through {@link Update}, or by running the real
 * {@link Program} synchronously.
 */
public final class TestModels {

    public static final RepoStatus CLEAN =
            new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), List.of());
    public static final RepoStatus DIRTY = new RepoStatus(
            Head.onBranch(new Branch.Name("main"), new Sha("aaaa")),
            List.of(new FileEntry("a.txt", ChangeType.UNTRACKED)));
    public static final RepoStatus MOVED =
            new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("bbbb")), List.of());
    public static final List<Branch> BRANCHES = List.of(
            new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
            new Branch(new Branch.Name("feature"), false, new Sha("ffff")));
    public static final List<Commit> COMMITS =
            List.of(new Commit(new Sha("aaaa"), "Ada", "ada@example.com", Instant.EPOCH, "First", ""));
    public static final List<Commit> FEATURE_COMMITS =
            List.of(new Commit(new Sha("ffff"), "Ada", "ada@example.com", Instant.EPOCH, "Feature", ""));

    private TestModels() {}

    /**
     * A model with the sample status, branches and commits loaded and nothing running.
     */
    public static Model loaded() {
        Model model = Update.init("repo").model();
        model = Update.update(model, new LoadMsg.StatusLoaded(CLEAN)).model();
        model = Update.update(model, new LoadMsg.BranchesLoaded(BRANCHES)).model();
        model = Update.update(model, new LoadMsg.RemoteBranchesLoaded(io.vavr.collection.List.of()))
                .model();
        model = Update.update(model, new LoadMsg.StashesLoaded(io.vavr.collection.List.of()))
                .model();
        return Update.update(model, new LoadMsg.CommitsLoaded(COMMITS)).model();
    }

    /**
     * The model after all of the provider's data has been loaded.
     */
    public static Model loaded(GitInfoProvider provider) {
        return Program.start(new Env(provider, StubGitHub.empty()), Runnable::run, Runnable::run, Runnable::run)
                .model();
    }

    /**
     * The model after all of the provider's data, and the log of {@code branch}, has been loaded.
     */
    public static Model loaded(GitInfoProvider provider, String branch) {
        Program program =
                Program.start(new Env(provider, StubGitHub.empty()), Runnable::run, Runnable::run, Runnable::run);
        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name(branch))));
        return program.model();
    }

    /**
     * The model after all of the provider's data, and the diff of {@code files}, has been loaded.
     */
    public static Model withFilesSelected(GitInfoProvider provider, io.vavr.collection.List<FileEntry> files) {
        Program program =
                Program.start(new Env(provider, StubGitHub.empty()), Runnable::run, Runnable::run, Runnable::run);
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
     * {@code model} with {@code entries} in its command log.
     */
    public static Model withLog(Model model, LogEntry... entries) {
        return model.mapOutcome(outcome -> outcome.withCommandLog(List.of(entries)));
    }

    /**
     * {@code model} showing that checking out {@code branch} failed, git saying {@code explanation}.
     */
    public static Model withCheckoutError(Model model, String branch, String explanation) {
        return model.fail(new AppError.OperationFailed(Operation.CHECKOUT, Option.of(branch), refused(explanation)));
    }

    /**
     * Git refusing with {@code explanation}.
     */
    public static Failure refused(String explanation) {
        return new Failure.Git(new GitError.CommandFailed(explanation, 1));
    }

    /**
     * {@code cmd} having run {@code commands}.
     */
    public static OperationMsg succeeded(OperationCmd cmd, String... commands) {
        return new OperationMsg.Succeeded(cmd, StubGitProvider.executed(commands));
    }

    /**
     * {@code cmd} having been refused by git, which said {@code explanation}.
     */
    public static OperationMsg failed(OperationCmd cmd, String explanation) {
        return new OperationMsg.Failed(cmd, refused(explanation));
    }

    /**
     * The error {@code model} shows, as the user sees it.
     */
    public static Option<String> errorText(Model model) {
        return model.outcome().error().map(ErrorText::of);
    }

    /**
     * {@code model} with the log of {@code branch} still loading, while the log it replaced stays visible.
     */
    public static Model switchingBranch(Model model, String branch) {
        return model.mapRepoView(repoView -> repoView.withBranchLog(Option.of(Preview.loading(
                new BranchRef.Local(new Branch.Name(branch)), model.repoView().branchLog()))));
    }
}
