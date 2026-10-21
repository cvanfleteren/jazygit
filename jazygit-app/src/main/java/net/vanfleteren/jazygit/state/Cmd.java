package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.FileEntry;
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
            return env.git().allBranches().fold(this::failedWith, LoadMsg.AllBranchesLoaded::new);
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
     * Reads the commits of the log of HEAD that {@code query} finds.
     */
    record SearchCommits(CommitQuery query) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .search(query)
                    .fold(this::failedWith, commits -> new LoadMsg.CommitSearchLoaded(query, commits));
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

    record LoadCommitDetail(Sha sha, DiffOptions options) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .commitChanges(sha, options)
                    .fold(this::failedWith, changes -> new LoadMsg.CommitDetailLoaded(sha, options, changes));
        }
    }

    record LoadFileDiff(List<FileEntry> files, DiffOptions options) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .diff(files, options)
                    .fold(this::failedWith, diff -> new LoadMsg.FileDiffLoaded(files, options, diff));
        }
    }
}
