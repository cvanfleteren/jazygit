package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.AllBranches;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.git.model.MetadataStamp;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.settings.Settings;

/**
 * IO that {@link Update} asks for. Commands are immutable data that know how to run themselves and
 * how to report failure; {@link Program} performs them in the background and feeds the outcome
 * back as a {@link Msg}.
 */
public interface Cmd {

    /**
     * Performs the IO against {@code env} and returns the {@link Msg} that reports its outcome. A {@link RuntimeException}
     * thrown from here is a bug; {@link Program} still turns it into {@link #failed(Failure)}.
     */
    Msg run(Env env);

    /**
     * The {@link Msg} that reports this command failing for {@code failure}.
     */
    Msg failed(Failure failure);

    /**
     * The {@link Msg} that reports this command failing with {@code error}, before any command ran.
     */
    default Msg failedWith(GitError error) {
        return failed(new Failure.Git(error));
    }

    /**
     * The {@link Msg} that reports this command failing with {@code failure}.
     */
    default Msg failedWith(GitFailure<?> failure) {
        return failed(Failure.of(failure));
    }

    /**
     * The {@link Msg} that reports this command failing with {@code failure} of gh.
     */
    default Msg failedWith(GhFailure failure) {
        return failed(Failure.of(failure));
    }

    /**
     * The {@link Msg} that reports this command failing with {@code error} of gh, before any command ran.
     */
    default Msg failedWith(GhError error) {
        return failed(new Failure.GitHub(error));
    }

    /**
     * Whether this command runs in the background, next to the other commands instead of after them: it is
     * slow, like talking to a remote, and the user is not waiting for it.
     */
    default boolean background() {
        return false;
    }

    /**
     * What this command shows as while it runs, once it ran a while (ADR 33); none for {@link Label#WORKING}.
     */
    default Option<Label> label() {
        return Option.none();
    }

    /**
     * The slot this command takes, if it is only wanted until a newer one for that slot is asked for: the newer
     * one cancels it when it has not started yet, and it then reports only {@link #skipped()}. Only a command whose
     * result the model drops once a newer one is asked for may take one, as nothing else may wait for its answer.
     */
    default Option<Slot> slot() {
        return Option.none();
    }

    /**
     * What this command reports when a newer one for its {@link #slot() slot} cancelled it before it started, if
     * anything.
     */
    default Option<Msg> skipped() {
        return Option.none();
    }

    /**
     * What this command must not share with another command at the same time, if anything: while a command that
     * holds it runs, one that needs it waits on its own thread.
     */
    default Option<Guard> guard() {
        return Option.none();
    }

    /**
     * What a command can be the latest of.
     */
    enum Slot {
        /**
         * The preview of the highlighted pull request.
         */
        PULL_REQUEST_PREVIEW,
        /**
         * The log of the highlighted branch.
         */
        BRANCH_LOG,
        /**
         * The changes of the commit highlighted in the commits pane.
         */
        COMMIT_DETAIL,
        /**
         * The changes of the commit highlighted in the divergence that is open.
         */
        DIVERGENCE_DETAIL,
        /**
         * The changes of the commit highlighted in the commits of a branch that are open.
         */
        BRANCH_COMMITS_DETAIL,
        /**
         * The changes of the stash highlighted.
         */
        STASH_DETAIL,
        /**
         * The diff of the node highlighted in the tree of the files of a commit or a stash.
         */
        COMMIT_FILE_DIFF,
        /**
         * The diff of the files highlighted.
         */
        FILE_DIFF
    }

    /**
     * What a command can keep others from while it runs.
     */
    enum Guard {
        /**
         * The remote-tracking refs and {@code FETCH_HEAD}, which every fetch writes: by fetching, pulling, pushing,
         * deleting a branch on its remote, or checking out a pull request through gh. Two at once can leave
         * {@code FETCH_HEAD} with the lines of both, or fail on the lock of a ref.
         */
        REMOTE_REFS
    }

    /**
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {

        @Override
        default Msg failed(Failure failure) {
            return new LoadMsg.Failed(this, failure);
        }

        /**
         * A skipped load is no longer running either.
         */
        @Override
        default Option<Msg> skipped() {
            return Option.of(new LoadMsg.Skipped(this));
        }
    }

    /**
     * A load that keeps what it reads in a {@link Target}: what it read goes there, so does that it failed, and a
     * tick reads it again while it shows as failed there. The loads that are not targeted keep what they read in a
     * way of their own, which {@link LoadUpdate} spells out for each.
     *
     * @param <T> what the load reads
     */
    sealed interface Targeted<T> extends Load {

        Target<T> target();
    }

    /**
     * A load whose result depends on the diff options it was started with.
     */
    sealed interface DiffLoad extends Load {

        DiffOptions options();
    }

    /**
     * Keeps {@code next}, which the user changed from {@code previous}, for the next session.
     */
    record SaveSettings(Settings previous, Settings next) implements Cmd {

        @Override
        public Msg run(Env env) {
            return env.settings()
                    .save(previous, next)
                    .fold(problem -> failed(new Failure.Settings(problem)), saved -> new SettingsMsg.Saved());
        }

        @Override
        public Msg failed(Failure failure) {
            return new SettingsMsg.NotSaved(failure);
        }
    }

    record LoadStatus() implements Targeted<RepoStatus> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.status"));
        }

        @Override
        public Target<RepoStatus> target() {
            return Target.inRepo(Repo::status, Repo::withStatus);
        }

        @Override
        public Msg run(Env env) {
            return env.git().status().read().fold(this::failedWith, LoadMsg.StatusLoaded::new);
        }
    }

    /**
     * Reads the local and the remote-tracking branches, at once, and with them the parents of the branches and how
     * far each local branch is from its base (ADR 46): those go by the branches and the parents just read, so they
     * come back with them, in one message (ADR 58). When the parents cannot be read, the branches come back without
     * them, and the parents and counts read before stay.
     */
    record LoadBranches() implements Load {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.branches"));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .allBranches()
                    .fold(this::failedWith, branches -> new LoadMsg.BranchesLoaded(branches, bases(env, branches)));
        }

        /**
         * The parents of {@code branches} and how far each is from its base, or none when the parents cannot be read.
         */
        private static Option<LoadMsg.Bases> bases(Env env, AllBranches branches) {
            return env.git()
                    .parents()
                    .toOption()
                    .map(parents -> new LoadMsg.Bases(
                            parents, env.git().baseDistance(branches.tips(), parents, branches.main())));
        }
    }

    /**
     * Looks at the files that the branches, the stashes and the worktrees are read from, so that a tick only reads
     * again those that changed: that takes a git process each, this none.
     */
    record CheckMetadata() implements Load {

        /**
         * The loads that a stamp of {@link GitMetadata} tells to run again.
         */
        static final List<Load> GUARDED = List.of(new LoadBranches(), new LoadStashes(), new LoadWorktrees());

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.metadata"));
        }

        @Override
        public Msg run(Env env) {
            Option<GitMetadata> metadata = env.git().status().metadata();
            return new LoadMsg.MetadataChecked(List.of(
                    Tuple.of((Load) new LoadBranches(), metadata.flatMap(GitMetadata::branches)),
                    Tuple.of(new LoadStashes(), metadata.flatMap(GitMetadata::stashes)),
                    Tuple.of(new LoadWorktrees(), metadata.flatMap(GitMetadata::worktrees))));
        }
    }

    record LoadStashes() implements Targeted<List<Stash>> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.stashes"));
        }

        @Override
        public Target<List<Stash>> target() {
            return Target.inRepo(Repo::stashes, Repo::withStashes);
        }

        @Override
        public Msg run(Env env) {
            return env.git().stashes().list().fold(this::failedWith, LoadMsg.StashesLoaded::new);
        }
    }

    record LoadWorktrees() implements Targeted<List<Worktree>> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.worktrees"));
        }

        @Override
        public Target<List<Worktree>> target() {
            return Target.inRepo(Repo::worktrees, Repo::withWorktrees);
        }

        @Override
        public Msg run(Env env) {
            return env.git().worktrees().list().fold(this::failedWith, LoadMsg.WorktreesLoaded::new);
        }
    }

    /**
     * Reads whether each of {@code worktrees} has changes, one after the other. One that cannot be read is left
     * out. It runs in the background, as it takes a git status per worktree, and nobody waits for it.
     */
    record LoadWorktreeChanges(List<String> worktrees) implements Load {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.worktreeChanges"));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return new LoadMsg.WorktreeChangesLoaded(
                    worktrees,
                    worktrees
                            .flatMap(location -> env.git()
                                    .worktrees()
                                    .hasChanges(Path.of(location))
                                    .toOption()
                                    .map(changes -> Tuple.of(location, changes)))
                            .toMap(entry -> entry));
        }
    }

    /**
     * Reads the log of HEAD from the commit {@code ahead} reads from, where the status read last says HEAD is (ADR 49),
     * and then what of it is not on its base, as {@code ahead} does: the log is shown with it. Without a commit, the
     * log is empty and nothing is read.
     */
    record LoadCommits(LoadAhead ahead) implements Targeted<List<Commit>> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.commits"));
        }

        @Override
        public Target<List<Commit>> target() {
            return Target.inRepo(Repo::commits, Repo::withCommits);
        }

        @Override
        public Msg run(Env env) {
            return ahead.tip()
                    .fold(() -> Either.<ProcessError, List<Commit>>right(List.empty()), env.git()::commits)
                    .fold(this::failedWith, commits -> new LoadMsg.CommitsLoaded(commits, ahead, ahead.read(env)));
        }
    }

    /**
     * Reads the commits of the log of HEAD that {@code query} finds, leaving out those on {@code notOn}, if given.
     */
    record SearchCommits(CommitQuery query, Option<BranchRef> notOn) implements Targeted<List<Commit>> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.search"));
        }

        /**
         * The results of the search, while it is the one shown.
         */
        @Override
        public Target<List<Commit>> target() {
            return Target.inView(Lens.where(
                            RepoView::commitSearch,
                            RepoView::withCommitSearch,
                            search -> search.load().equals(this))
                    .andThen(Lens.of(CommitSearch::results, CommitSearch::withResults)));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .search(query, notOn)
                    .fold(this::failedWith, commits -> new LoadMsg.CommitSearchLoaded(query, notOn, commits));
        }
    }

    /**
     * Reads the commits from {@code tip}, where {@code branch} is, or HEAD for none, that are not on {@code base}. Both
     * are where the status and the branches read last have them (ADR 49), so two loads are equal only when they read
     * the same.
     *
     * @param tip  the commit the branch or HEAD is at; none for a HEAD without commits, or a branch not read
     * @param base the base of ADR 20, {@link BranchRef#bases}, with the commit it is at: the first of them the branches
     *             read last have; none when there is none to compare with
     */
    record LoadAhead(Option<BranchRef> branch, Option<Sha> tip, Option<Base> base) implements Load {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.ahead"));
        }

        @Override
        public Msg run(Env env) {
            return read(env).fold(this::failed, ahead -> new LoadMsg.AheadLoaded(this, ahead));
        }

        /**
         * The commits not on the base, none when there is no base to compare with, or nothing to compare; or why they
         * could not be read.
         */
        Either<Failure, Option<Ahead>> read(Env env) {
            return tip.flatMap(ours -> base.map(theirs -> env.git()
                            .ahead(ours, theirs.ref(), theirs.tip())
                            .<Failure>mapLeft(Failure.Git::new)
                            .map(Option::of)))
                    .getOrElse(() -> Either.right(Option.none()));
        }

        /**
         * A branch compared with, and the commit it is at.
         */
        public record Base(BranchRef ref, Sha tip) {}
    }

    /**
     * Reads the log of {@code branch}, and then what of it is not on its base, as {@code ahead} does: the log is shown
     * with it.
     */
    record LoadBranchLog(BranchRef branch, LoadAhead ahead) implements Targeted<List<Commit>> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.branchLog", branch.name()));
        }

        @Override
        public Option<Slot> slot() {
            return Option.of(Slot.BRANCH_LOG);
        }

        /**
         * The log of the branch, while it is the one highlighted.
         */
        @Override
        public Target<List<Commit>> target() {
            return Target.inView(Preview.lens(RepoView::branchLog, RepoView::withBranchLog, branch));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .log(branch)
                    .fold(
                            this::failedWith,
                            commits -> new LoadMsg.BranchLogLoaded(branch, commits, ahead, ahead.read(env)));
        }
    }

    /**
     * Reads the commits only on {@code branch} and the ones only on {@code other}.
     */
    record LoadDivergence(BranchName branch, BranchRef other) implements Targeted<Divergence> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.divergence", branch.value(), other.name()));
        }

        /**
         * The commits of the divergence, while it is the one open.
         */
        @Override
        public Target<Divergence> target() {
            return Target.inView(Lens.where(
                            RepoView::divergence,
                            RepoView::withDivergence,
                            view -> view.load().equals(this))
                    .andThen(Lens.of(DivergenceView::commits, DivergenceView::withCommits)));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .divergence(new BranchRef.Local(branch), other)
                    .fold(this::failedWith, divergence -> new LoadMsg.DivergenceLoaded(branch, other, divergence));
        }
    }

    /**
     * Reads the changes of the commit {@code sha}, for the preview whose slot is {@code preview}: the commit detail of
     * the commits pane, of a divergence or of the commits of a branch. Each takes its own slot, so that highlighting
     * a commit in one does not skip the read another one waits for.
     */
    record LoadCommitDetail(Sha sha, DiffOptions options, Slot preview) implements DiffLoad, Targeted<DiffText> {

        /**
         * Reads the changes of the commit {@code sha} highlighted in the commits pane.
         */
        public LoadCommitDetail(Sha sha, DiffOptions options) {
            this(sha, options, Slot.COMMIT_DETAIL);
        }

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.commitDetail", sha.shortSha()));
        }

        @Override
        public Option<Slot> slot() {
            return Option.of(preview);
        }

        /**
         * The detail of the commit in the preview of {@code preview}, while it is the one highlighted there. Every
         * other preview of it shows what was read too.
         */
        @Override
        public Target<DiffText> target() {
            Lens<RepoView, Loadable<DiffText>> commits =
                    Preview.lens(RepoView::commitDetail, RepoView::withCommitDetail, sha);
            Lens<RepoView, Loadable<DiffText>> divergence = Lens.some(RepoView::divergence, RepoView::withDivergence)
                    .andThen(Preview.lens(DivergenceView::detail, DivergenceView::withDetail, sha));
            Lens<RepoView, Loadable<DiffText>> branchCommits = Lens.some(
                            RepoView::branchCommits, RepoView::withBranchCommits)
                    .andThen(Preview.lens(BranchCommitsView::detail, BranchCommitsView::withDetail, sha));
            return Target.inView(
                    switch (preview) {
                        case DIVERGENCE_DETAIL -> divergence.alongWith(commits).alongWith(branchCommits);
                        case BRANCH_COMMITS_DETAIL ->
                            branchCommits.alongWith(commits).alongWith(divergence);
                        default -> commits.alongWith(divergence).alongWith(branchCommits);
                    });
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .commitChanges(sha, options)
                    .fold(this::failedWith, changes -> new LoadMsg.CommitDetailLoaded(sha, options, changes, preview));
        }
    }

    record LoadStashDetail(Sha sha, DiffOptions options) implements DiffLoad, Targeted<DiffText> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.stashDetail"));
        }

        @Override
        public Option<Slot> slot() {
            return Option.of(Slot.STASH_DETAIL);
        }

        /**
         * The detail of the stash, while it is the one highlighted.
         */
        @Override
        public Target<DiffText> target() {
            return Target.inView(Preview.lens(RepoView::stashDetail, RepoView::withStashDetail, sha));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .stashes()
                    .changes(sha, options)
                    .fold(this::failedWith, changes -> new LoadMsg.StashDetailLoaded(sha, options, changes));
        }
    }

    /**
     * Reads the files a commit, a stash, a pull request or a branch changed, for the tree of them the files pane lists. Those
     * of a pull request are read in the background, as its commits may have to be fetched first (ADR 55).
     */
    record LoadCommitFiles(CommitFilesView.Changes of) implements Targeted<List<CommitFile>> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.commitFiles", of.sha().shortSha()));
        }

        @Override
        public boolean background() {
            return of instanceof CommitFilesView.Changes.OfPullRequest;
        }

        /**
         * The files of the view of {@code of}, while it is the one open.
         */
        @Override
        public Target<List<CommitFile>> target() {
            return Target.inView(
                    CommitFilesView.open(of).andThen(Lens.of(CommitFilesView::files, CommitFilesView::withFiles)));
        }

        @Override
        public Msg run(Env env) {
            return (switch (of) {
                        case CommitFilesView.Changes.OfCommit(Sha sha) ->
                            env.git().commitFiles(sha);
                        case CommitFilesView.Changes.OfStash(Sha sha) ->
                            env.git().stashes().files(sha);
                        case CommitFilesView.Changes.OfBranch(var branch, var base, Sha sha) ->
                            env.git().rangeFiles(base.tip(), sha);
                        case CommitFilesView.Changes.OfPullRequest(PullRequestCommits commits) ->
                            env.git().pullRequestFiles(commits);
                    })
                    .fold(this::failedWith, files -> new LoadMsg.CommitFilesLoaded(of, files));
        }
    }

    /**
     * Reads what a commit, a stash or a pull request changed in the files of the node highlighted in the tree of its
     * files.
     */
    record LoadCommitFileDiff(CommitFilesView.Changes of, List<CommitFile> files, DiffOptions options)
            implements DiffLoad, Targeted<DiffText> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.diff"));
        }

        @Override
        public Option<Slot> slot() {
            return Option.of(Slot.COMMIT_FILE_DIFF);
        }

        /**
         * The diff in the view of {@code of}, while it is the one open, and {@code files} the node highlighted in it.
         */
        @Override
        public Target<DiffText> target() {
            return Target.inView(CommitFilesView.open(of)
                    .andThen(Preview.lens(CommitFilesView::diff, CommitFilesView::withDiff, files)));
        }

        @Override
        public Msg run(Env env) {
            return (switch (of) {
                        case CommitFilesView.Changes.OfCommit(Sha sha) ->
                            env.git().commitFileChanges(sha, files, options);
                        case CommitFilesView.Changes.OfStash(Sha sha) ->
                            env.git().stashes().fileChanges(sha, files, options);
                        case CommitFilesView.Changes.OfBranch(var branch, var base, Sha sha) ->
                            env.git().rangeFileChanges(base.tip(), sha, files, options);
                        case CommitFilesView.Changes.OfPullRequest(PullRequestCommits commits) ->
                            env.git().pullRequestFileChanges(commits, files, options);
                    })
                    .fold(this::failedWith, diff -> new LoadMsg.CommitFileDiffLoaded(of, files, options, diff));
        }
    }

    record LoadFileDiff(List<FileEntry> files, DiffOptions options) implements DiffLoad, Targeted<Diffs> {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.diff"));
        }

        @Override
        public Option<Slot> slot() {
            return Option.of(Slot.FILE_DIFF);
        }

        /**
         * The diff of the files, while they are the ones highlighted.
         */
        @Override
        public Target<Diffs> target() {
            return Target.inView(Preview.lens(RepoView::fileDiff, RepoView::withFileDiff, files));
        }

        /**
         * Reads the diff, after a stamp of what it is read from: a change while it is read then shows in the next
         * stamp, and the diff is read again (ADR 58).
         */
        @Override
        public Msg run(Env env) {
            Option<MetadataStamp> stamp = env.git().status().diffStamp(files);
            return env.git()
                    .diff(files, options)
                    .fold(this::failedWith, diff -> new LoadMsg.FileDiffLoaded(files, options, diff, stamp));
        }
    }

    /**
     * Looks at the files the diff {@code load} is read from, so that it is read again only once they changed: that
     * takes a git process or more, this none (ADR 58).
     */
    record CheckFileDiff(LoadFileDiff load) implements Load {

        @Override
        public Msg run(Env env) {
            return new LoadMsg.FileDiffChecked(load, env.git().status().diffStamp(load.files()));
        }
    }
}
