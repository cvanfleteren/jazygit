package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * IO that {@link Update} asks for. Commands are immutable data that know how to run themselves and
 * how to report failure; {@link Program} performs them in the background and feeds the outcome
 * back as a {@link Msg}.
 */
public interface Cmd {

    /**
     * Performs the IO and returns the {@link Msg} that reports its outcome. A {@link RuntimeException}
     * thrown from here is a bug; {@link Program} still turns it into {@link #failed(String)}.
     */
    Msg run(GitInfoProvider git);

    /**
     * The {@link Msg} that reports this command failing with {@code message}.
     */
    Msg failed(String message);

    /**
     * The {@link Msg} that reports this command failing with {@code error}.
     */
    default Msg failedWith(GitError error) {
        return failed(GitErrorText.of(error));
    }

    /**
     * The {@link Msg} that reports this command failing with {@code failure}, after logging the commands
     * that were run when this command has a log title.
     */
    default Msg failedWith(GitFailure<?> failure) {
        Msg failed = failedWith(failure.error());
        return logTitleKey().<Msg>map(key -> new LoggedMsg(key, failure.commands(), failed)).getOrElse(failed);
    }

    /**
     * The message key of the command log title for this command's git commands, if it runs any that
     * change the repository.
     */
    default Option<String> logTitleKey() {
        return Option.none();
    }

    /**
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {

        /**
         * What this load reads, so one that is still running is not started again: loads with the same
         * key read the same thing. That is the load itself, unless it carries data that only helps reading.
         */
        default Load key() {
            return this;
        }

        @Override
        default Msg failed(String message) {
            return new LoadMsg.Failed(this, message);
        }
    }

    record LoadStatus() implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.status().fold(this::failedWith, LoadMsg.StatusLoaded::new);
        }
    }

    /**
     * Reads the branches.
     *
     * @param known the branches as shown now, which spare the provider recounting how far unchanged
     *              branches are from their upstream. They do not change what is loaded, so they are not
     *              part of the {@link #key()}.
     */
    record LoadBranches(List<Branch> known) implements Load {

        public LoadBranches() {
            this(List.empty());
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.branches(known).fold(this::failedWith, LoadMsg.BranchesLoaded::new);
        }

        @Override
        public Load key() {
            return new LoadBranches();
        }
    }

    record LoadStashes() implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.stashes().fold(this::failedWith, LoadMsg.StashesLoaded::new);
        }
    }

    record LoadCommits() implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.commits().fold(this::failedWith, LoadMsg.CommitsLoaded::new);
        }
    }

    record LoadBranchLog(String branch) implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.log(branch).fold(this::failedWith, commits -> new LoadMsg.BranchLogLoaded(branch, commits));
        }
    }

    record LoadCommitDetail(Sha sha) implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.commitChanges(sha).fold(this::failedWith, changes -> new LoadMsg.CommitDetailLoaded(sha, changes));
        }
    }

    record LoadFileDiff(List<FileEntry> files) implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return git.diff(files).fold(this::failedWith, diff -> new LoadMsg.FileDiffLoaded(files, diff));
        }
    }
}
