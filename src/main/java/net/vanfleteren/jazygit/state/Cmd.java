package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.FileEntry;

import java.util.List;
import java.util.Optional;

/**
 * IO that {@link Update} asks for. Commands are immutable data that know how to run themselves and
 * how to report failure; {@link Program} performs them in the background and feeds the outcome
 * back as a {@link Msg}.
 */
public interface Cmd {

    /**
     * Performs the IO and returns the {@link Msg} that reports success. A {@link RuntimeException}
     * thrown from here is turned into {@link #failed(String)} by {@link Program}.
     */
    Msg run(GitInfoProvider git);

    /**
     * The {@link Msg} that reports this command failing with {@code message}.
     */
    Msg failed(String message);

    /**
     * The message key of the command log title for this command's git commands, if it runs any that
     * change the repository.
     */
    default Optional<String> logTitleKey() {
        return Optional.empty();
    }

    /**
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {

        @Override
        default Msg failed(String message) {
            return new LoadMsg.Failed(this, message);
        }
    }

    record LoadStatus() implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.StatusLoaded(git.status());
        }
    }

    record LoadBranches() implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.BranchesLoaded(git.branches());
        }
    }

    record LoadCommits() implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.CommitsLoaded(git.commits());
        }
    }

    record LoadBranchLog(String branch) implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.BranchLogLoaded(branch, git.log(branch));
        }
    }

    record LoadCommitDetail(String sha) implements Load {

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.CommitDetailLoaded(sha, git.commitChanges(sha));
        }
    }

    record LoadFileDiff(List<FileEntry> files) implements Load {

        public LoadFileDiff {
            files = List.copyOf(files);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.FileDiffLoaded(files, git.diff(files));
        }
    }
}
