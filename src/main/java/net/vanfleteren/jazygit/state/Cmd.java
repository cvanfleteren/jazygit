package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.FileEntry;

import java.util.List;

/**
 * IO that {@link Update} asks for. Commands are immutable data that know how to run themselves and
 * how to report failure; {@link Program} performs them in the background and feeds the outcome
 * back as a {@link Msg}.
 */
public sealed interface Cmd {

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
     * Reads part of the repository state into the {@link Model}.
     */
    sealed interface Load extends Cmd {

        @Override
        default Msg failed(String message) {
            return new LoadMsg.Failed(this, message);
        }
    }

    sealed interface BranchCmd extends Cmd {

        /**
         * Creates the branch {@code name} at {@code base} and switches to it.
         */
        record CreateBranch(String name, String base) implements BranchCmd {

            @Override
            public Msg run(GitInfoProvider git) {
                git.createBranch(name, base);
                return new NewBranchMsg.Created(name);
            }

            @Override
            public Msg failed(String message) {
                return new NewBranchMsg.Failed(name, message);
            }
        }

        record DeleteBranch(String branch, DeleteScope scope) implements BranchCmd {
            /**
             * Where a branch is deleted.
             */
            public enum DeleteScope {
                LOCAL, REMOTE, BOTH
            }

            @Override
            public Msg run(GitInfoProvider git) {
                git.deleteBranch(branch, scope != DeleteScope.REMOTE, scope != DeleteScope.LOCAL);
                return new DeleteBranchMsg.Deleted(branch);
            }

            @Override
            public Msg failed(String message) {
                return new DeleteBranchMsg.Failed(branch, message);
            }
        }

        record Checkout(String branch) implements BranchCmd {

            @Override
            public Msg run(GitInfoProvider git) {
                git.checkout(branch);
                return new CheckoutMsg.Done(branch);
            }

            @Override
            public Msg failed(String message) {
                return new CheckoutMsg.Failed(branch, message);
            }
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

    record LoadFileDiff(List<FileEntry> files) implements Load {

        public LoadFileDiff {
            files = List.copyOf(files);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new LoadMsg.FileDiffLoaded(files, git.diff(files));
        }
    }

    record Stage(List<String> paths) implements Cmd {

        public Stage {
            paths = List.copyOf(paths);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.stage(paths);
            return new StageMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new StageMsg.Failed(message);
        }
    }

    /**
     * Takes paths out of the index: the {@code added} ones (not in HEAD) become untracked again, the
     * {@code others} are reset to HEAD.
     */
    record Unstage(List<String> added, List<String> others) implements Cmd {

        public Unstage {
            added = List.copyOf(added);
            others = List.copyOf(others);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.unstageNew(added);
            git.unstage(others);
            return new StageMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new StageMsg.Failed(message);
        }
    }

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<String> paths) implements Cmd {

        public StageForCommit {
            paths = List.copyOf(paths);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.stage(paths);
            return new CommitMsg.StagedForCommit();
        }

        @Override
        public Msg failed(String message) {
            return new CommitMsg.StageForCommitFailed(message);
        }
    }

    record Commit(String summary, String description) implements Cmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.commit(summary, description);
            return new CommitMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new CommitMsg.Failed(message);
        }
    }

    /**
     * Stages the paths, if any, then amends the last commit with the index, keeping its message.
     */
    record Amend(List<String> stage) implements Cmd {

        public Amend {
            stage = List.copyOf(stage);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.stage(stage);
            git.amendLastCommit();
            return new AmendMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new AmendMsg.Failed(message);
        }
    }

    /**
     * Replaces the message of the last commit.
     */
    record Reword(String summary, String description) implements Cmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.reword(summary, description);
            return new RewordMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new RewordMsg.Failed(message);
        }
    }
}
