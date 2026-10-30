package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutCmd;
import net.vanfleteren.jazygit.feature.commit.MessageEdited;
import net.vanfleteren.jazygit.feature.commit.MessagePopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.ui.ErrorText;

/**
 * Builds models for tests: with fixed sample data through {@link Update}, or by running the real
 * {@link Program} synchronously.
 */
public final class TestModels {

    public static final RepoStatus CLEAN =
            new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), List.of());
    public static final RepoStatus DIRTY = new RepoStatus(
            Head.onBranch(new BranchName("main"), new Sha("aaaa")),
            List.of(Fixtures.fileEntry("a.txt", ChangeType.UNTRACKED)));
    public static final RepoStatus MOVED =
            new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("bbbb")), List.of());
    public static final List<Branch> BRANCHES = List.of(
            Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
            Fixtures.branch(new BranchName("feature"), false, new Sha("ffff")));
    public static final List<Commit> COMMITS =
            List.of(Fixtures.commit(new Sha("aaaa"), "Ada", "ada@example.com", Instant.EPOCH, "First", ""));
    public static final List<Commit> FEATURE_COMMITS =
            List.of(Fixtures.commit(new Sha("ffff"), "Ada", "ada@example.com", Instant.EPOCH, "Feature", ""));

    // The time of the programs started without one: it stands still.
    private static final Clock STILL = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    private TestModels() {}

    /**
     * The program over {@code env}, started with the default settings at a time that stands still, telling no one
     * about bugs, changes to the model or the app stopping.
     */
    public static Program program(Env env, Executor io, Executor background, Executor ui) {
        return program(env, io, background, ui, () -> {});
    }

    /**
     * As above, telling {@code modelChanged} whenever the model draws differently.
     */
    public static Program program(Env env, Executor io, Executor background, Executor ui, Runnable modelChanged) {
        return Program.start(
                env,
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                Landing.START,
                io,
                background,
                ui,
                bug -> {},
                modelChanged,
                () -> {},
                STILL);
    }

    /**
     * The id of the last read of the details of a pull request given out in {@code model}: the one a dialog that just
     * started a read waits for (ADR 34).
     */
    public static long lastRead(Model model) {
        return model.gitHub().nextRead() - 1;
    }

    /**
     * A model with the sample status, branches and commits loaded and nothing running.
     */
    public static Model loaded() {
        Model read = branchesRead();
        // The log was asked for when the status came, before the branches were known.
        Cmd commits = runningCmds(read).find(Cmd.LoadCommits.class::isInstance).getOrElse(TestModels::loadCommits);
        return done(read, commits, commitsLoaded(COMMITS)).model();
    }

    /**
     * The model after all of the provider's data has been loaded.
     */
    public static Model loaded(GitInfoProvider provider) {
        return program(new Env(provider, StubGitHub.empty()), Runnable::run, Runnable::run, Runnable::run)
                .model();
    }

    /**
     * The model after all of the provider's data, and the log of {@code branch}, has been loaded.
     */
    public static Model loaded(GitInfoProvider provider, String branch) {
        Program program = program(new Env(provider, StubGitHub.empty()), Runnable::run, Runnable::run, Runnable::run);
        program.dispatch(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName(branch))));
        return program.model();
    }

    /**
     * The sample model before its log is read, as {@link Program} has it.
     */
    private static Model branchesRead() {
        Model model = started(Update.init("repo")).model();
        model = done(model, new Cmd.LoadStatus(), new LoadMsg.StatusLoaded(CLEAN))
                .model();
        model = done(model, new Cmd.LoadBranches(), new LoadMsg.BranchesLoaded(new AllBranches(BRANCHES, List.empty())))
                .model();
        Cmd.LoadBaseDistance distance = LoadUpdate.baseDistance(model);
        model = done(model, distance, new LoadMsg.BaseDistanceLoaded(distance, io.vavr.collection.HashMap.empty()))
                .model();
        model = done(model, new Cmd.LoadParents(), new LoadMsg.ParentsLoaded(io.vavr.collection.HashMap.empty()))
                .model();
        model = done(model, new Cmd.LoadStashes(), new LoadMsg.StashesLoaded(io.vavr.collection.List.of()))
                .model();
        return done(model, new Cmd.LoadWorktrees(), new LoadMsg.WorktreesLoaded(io.vavr.collection.List.of()))
                .model();
    }

    /**
     * The load of the log of HEAD of the sample model.
     */
    public static Cmd.LoadCommits loadCommits() {
        return new Cmd.LoadCommits(AheadUpdate.ofHead(branchesRead()));
    }

    /**
     * The log of HEAD of the sample model read as {@code commits}, which has no base to compare with.
     */
    public static LoadMsg.CommitsLoaded commitsLoaded(List<Commit> commits) {
        return new LoadMsg.CommitsLoaded(commits, loadCommits().ahead(), Either.right(Option.none()));
    }

    /**
     * The load of the log of {@code branch} of the sample model.
     */
    public static Cmd.LoadBranchLog loadBranchLog(BranchRef branch) {
        return new Cmd.LoadBranchLog(branch, AheadUpdate.of(branchesRead(), branch));
    }

    /**
     * The load of the log of {@code branch} as {@code model} has it.
     */
    public static Cmd.LoadBranchLog loadBranchLog(Model model, BranchRef branch) {
        return new Cmd.LoadBranchLog(branch, AheadUpdate.of(model, branch));
    }

    /**
     * The log of {@code branch} of the sample model read as {@code commits}, which has no base to compare with.
     */
    public static LoadMsg.BranchLogLoaded branchLogLoaded(BranchRef branch, List<Commit> commits) {
        return new LoadMsg.BranchLogLoaded(
                branch, commits, loadBranchLog(branch).ahead(), Either.right(Option.none()));
    }

    /**
     * {@code model} after the branches were read again, finding the local branches {@code local} and the
     * remote-tracking branches it had, for a test about the local ones; without the read of how far they are from their base.
     */
    public static Update.Next localBranchesRead(Model model, List<Branch> local) {
        return withoutBaseDistance(Update.update(
                model,
                new LoadMsg.BranchesLoaded(
                        new AllBranches(local, model.repo().remoteBranches().getOrElse(List.empty())))));
    }

    /**
     * {@code model} after the branches were read again, finding the local branches it had and the remote-tracking
     * branches {@code remote}, for a test about the remote ones; without the read of how far they are from their base.
     */
    public static Update.Next remoteBranchesRead(Model model, List<RemoteRef> remote) {
        return withoutBaseDistance(Update.update(
                model,
                new LoadMsg.BranchesLoaded(
                        new AllBranches(model.repo().branches().getOrElse(List.empty()), remote))));
    }

    /**
     * {@code next} without the read of how far the branches are from their base, which every read of the branches
     * asks for (ADR 46): the tests of what else a read of the branches asks for leave it out.
     */
    private static Update.Next withoutBaseDistance(Update.Next next) {
        return new Update.Next(next.model(), next.cmds().filter(cmd -> !(cmd instanceof Cmd.LoadBaseDistance)));
    }

    /**
     * {@code model} listing what the search {@code text} found: {@code found}.
     */
    public static Model searched(Model model, String text, List<Commit> found) {
        model = Update.update(model, new CommitSearchMsg.Opened()).model();
        model = Update.update(model, new CommitSearchMsg.Edited(text)).model();
        model = Update.update(model, new CommitSearchMsg.Submitted()).model();
        return Update.update(model, new LoadMsg.CommitSearchLoaded(CommitQuery.parse(text), Option.none(), found))
                .model();
    }

    /**
     * The model after all of the provider's data, and the diff of {@code files}, has been loaded.
     */
    public static Model withFilesSelected(GitInfoProvider provider, io.vavr.collection.List<FileEntry> files) {
        Program program = program(new Env(provider, StubGitHub.empty()), Runnable::run, Runnable::run, Runnable::run);
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
        return model.fail(new AppError.OperationFailed(CheckoutCmd.CHECKOUT, Option.of(branch), refused(explanation)));
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
                new BranchRef.Local(new BranchName(branch)), model.repoView().branchLog()))));
    }

    /**
     * {@code model} after the user typed {@code text} in the open {@link TextPopup}.
     */
    public static Model typed(Model model, String text) {
        return Update.update(model, new Msg.TextEdited(text)).model();
    }

    /**
     * {@code model} after the user typed {@code message} in the open {@link MessagePopup}.
     */
    public static Model typed(Model model, CommitMessage message) {
        return Update.update(model, new MessageEdited(message)).model();
    }

    /**
     * {@code model} with {@code cmds} running, asked for at its time: what {@link Update#step} makes of the commands
     * a message asks for.
     */
    public static Model running(Model model, Cmd... cmds) {
        return model.mapJobs(jobs -> jobs.withStarted(List.of(cmds), model.now()));
    }

    /**
     * {@code model} with {@code operation} running on {@code branch}, which is busy until it is done.
     */
    public static Model busy(Model model, BranchRef branch, Operation operation) {
        return running(model, new Busy(branch, operation));
    }

    /**
     * What {@code msg} makes of {@code model} as {@link Program} handles it: the commands it asks for run.
     */
    public static Update.Next stepped(Model model, Msg msg) {
        return started(Update.update(model, msg));
    }

    /**
     * {@code next} as {@link Program} applies it: the commands it asks for run.
     */
    public static Update.Next started(Update.Next next) {
        return new Update.Next(
                next.model()
                        .mapJobs(jobs ->
                                jobs.withStarted(next.cmds(), next.model().now())),
                next.cmds(),
                next.quit());
    }

    /**
     * The commands that run in {@code model}.
     */
    public static io.vavr.collection.Set<Cmd> runningCmds(Model model) {
        return model.jobs().running().values().map(Jobs.Running::cmd).toSet();
    }

    /**
     * {@code model} with the command {@code cmd} that runs done, as {@link Program} reports it, with what it
     * reported; the commands that asks for run.
     */
    public static Update.Next done(Model model, Cmd cmd, Msg result) {
        long id = model.jobs()
                .running()
                .find(running -> running._2().cmd().equals(cmd))
                .map(running -> running._1())
                .getOrElseThrow(() -> new AssertionError(cmd + " does not run"));
        return stepped(model, new Msg.Done(id, Option.of(result)));
    }

    /**
     * An operation that keeps {@code branch} busy while it runs, like a push.
     */
    public record Busy(BranchRef branch, Operation operation) implements OperationCmd {

        @Override
        public Option<BranchRef> busyBranch() {
            return Option.of(branch);
        }

        @Override
        public Msg run(Env env) {
            return succeeded(List.empty());
        }
    }
}
