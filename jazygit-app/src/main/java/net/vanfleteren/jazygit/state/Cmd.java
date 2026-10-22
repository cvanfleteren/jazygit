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
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.GitMetadata;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
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
     * The slot this command takes, if it is only wanted until a newer one for that slot is asked for: the newer
     * one cancels it when it has not started yet, and it then reports nothing. Only a command whose result the
     * model drops once a newer one is asked for may take one, as nothing else may wait for its answer.
     */
    default Option<Slot> slot() {
        return Option.none();
    }

    /**
     * What a command can be the latest of.
     */
    enum Slot {
        /**
         * The preview of the highlighted pull request.
         */
        PULL_REQUEST_PREVIEW
    }

    /**
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {

        @Override
        default Msg failed(Failure failure) {
            return new LoadMsg.Failed(this, failure);
        }
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

    record LoadStatus() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().status().fold(this::failedWith, LoadMsg.StatusLoaded::new);
        }
    }

    /**
     * Reads the local and the remote-tracking branches, at once.
     */
    record LoadBranches() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().allBranches().fold(this::failedWith, LoadMsg.BranchesLoaded::new);
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
        public Msg run(Env env) {
            Option<GitMetadata> metadata = env.git().metadata();
            return new LoadMsg.MetadataChecked(List.of(
                    Tuple.of((Load) new LoadBranches(), metadata.flatMap(GitMetadata::branches)),
                    Tuple.of(new LoadStashes(), metadata.flatMap(GitMetadata::stashes)),
                    Tuple.of(new LoadWorktrees(), metadata.flatMap(GitMetadata::worktrees))));
        }
    }

    record LoadStashes() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().stashes().fold(this::failedWith, LoadMsg.StashesLoaded::new);
        }
    }

    record LoadWorktrees() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().worktrees().fold(this::failedWith, LoadMsg.WorktreesLoaded::new);
        }
    }

    /**
     * Reads whether each of {@code worktrees} has changes, one after the other. One that cannot be read is left
     * out. It runs in the background, as it takes a git status per worktree, and nobody waits for it.
     */
    record LoadWorktreeChanges(List<String> worktrees) implements Load {

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
                                    .hasChanges(Path.of(location))
                                    .toOption()
                                    .map(changes -> Tuple.of(location, changes)))
                            .toMap(entry -> entry));
        }
    }

    record LoadCommits() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().commits().fold(this::failedWith, LoadMsg.CommitsLoaded::new);
        }
    }

    /**
     * Reads the commits of the log of HEAD that {@code query} finds, leaving out those on {@code notOn}, if given.
     */
    record SearchCommits(CommitQuery query, Option<BranchRef> notOn) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .search(query, notOn)
                    .fold(this::failedWith, commits -> new LoadMsg.CommitSearchLoaded(query, notOn, commits));
        }
    }

    /**
     * Reads the commits of {@code branch}, or of HEAD for none, that are not on its base: the branch {@code named}
     * was made from, if that was recorded, else {@code main}. Of each, the remote-tracking one on
     * {@link RemoteBranch#DEFAULT_REMOTE} when there is one, else the local one. The branch {@code named} is no base
     * of itself.
     *
     * @param named the name of the branch, local or remote; none for a detached HEAD
     * @param main  the main branch, once the branches are read and one is
     */
    record LoadAhead(Option<BranchRef> branch, Option<Branch.Name> named, Option<Branch.Name> main) implements Load {

        @Override
        public Msg run(Env env) {
            return named.fold(
                            () -> Either.<ProcessError, Option<Branch.Name>>right(Option.none()),
                            env.git()::parentBranch)
                    .<GitError>mapLeft(error -> error)
                    .flatMap(parent -> env.git().ahead(branch, bases(parent)))
                    .fold(this::failedWith, ahead -> new LoadMsg.AheadLoaded(this, ahead));
        }

        /**
         * The branches to compare with, the first that exists counting.
         */
        List<BranchRef> bases(Option<Branch.Name> parent) {
            return parent.toList()
                    .appendAll(main)
                    .filter(base -> !named.contains(base))
                    .distinct()
                    .flatMap(base -> List.<BranchRef>of(
                            new BranchRef.Remote(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, base)),
                            new BranchRef.Local(base)));
        }
    }

    record LoadBranchLog(BranchRef branch) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .log(branch)
                    .fold(this::failedWith, commits -> new LoadMsg.BranchLogLoaded(branch, commits));
        }
    }

    /**
     * Reads the commits only on {@code branch} and the ones only on {@code other}.
     */
    record LoadDivergence(Branch.Name branch, BranchRef other) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .divergence(new BranchRef.Local(branch), other)
                    .fold(this::failedWith, divergence -> new LoadMsg.DivergenceLoaded(branch, other, divergence));
        }
    }

    record LoadCommitDetail(Sha sha, DiffOptions options) implements DiffLoad {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .commitChanges(sha, options)
                    .fold(this::failedWith, changes -> new LoadMsg.CommitDetailLoaded(sha, options, changes));
        }
    }

    record LoadStashDetail(Sha sha, DiffOptions options) implements DiffLoad {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .stashChanges(sha, options)
                    .fold(this::failedWith, changes -> new LoadMsg.StashDetailLoaded(sha, options, changes));
        }
    }

    record LoadFileDiff(List<FileEntry> files, DiffOptions options) implements DiffLoad {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .diff(files, options)
                    .fold(this::failedWith, diff -> new LoadMsg.FileDiffLoaded(files, options, diff));
        }
    }
}
