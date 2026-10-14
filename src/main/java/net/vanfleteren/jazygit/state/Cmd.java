package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;

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
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {

        @Override
        default Msg failed(Failure failure) {
            return new LoadMsg.Failed(this, failure);
        }
    }

    record LoadStatus() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().status().fold(this::failedWith, LoadMsg.StatusLoaded::new);
        }
    }

    record LoadBranches() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().branches().fold(this::failedWith, LoadMsg.BranchesLoaded::new);
        }
    }

    record LoadRemoteBranches() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().remoteBranches().fold(this::failedWith, LoadMsg.RemoteBranchesLoaded::new);
        }
    }

    record LoadStashes() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().stashes().fold(this::failedWith, LoadMsg.StashesLoaded::new);
        }
    }

    record LoadCommits() implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().commits().fold(this::failedWith, LoadMsg.CommitsLoaded::new);
        }
    }

    record LoadBranchLog(BranchRef branch) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().log(branch).fold(this::failedWith, commits -> new LoadMsg.BranchLogLoaded(branch, commits));
        }
    }

    record LoadCommitDetail(Sha sha) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().commitChanges(sha).fold(this::failedWith, changes -> new LoadMsg.CommitDetailLoaded(sha, changes));
        }
    }

    record LoadFileDiff(List<FileEntry> files) implements Load {

        @Override
        public Msg run(Env env) {
            return env.git().diff(files).fold(this::failedWith, diff -> new LoadMsg.FileDiffLoaded(files, diff));
        }
    }
}
