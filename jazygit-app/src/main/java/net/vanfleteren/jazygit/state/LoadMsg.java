package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.*;

/**
 * The outcome of reading from the repository.
 */
public sealed interface LoadMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return LoadUpdate.update(model, this);
    }

    /**
     * The load this is the outcome of.
     */
    Cmd.Load cmd();

    record StatusLoaded(RepoStatus status) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadStatus();
        }
    }

    record BranchesLoaded(List<Branch> branches) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadBranches();
        }
    }

    record RemoteBranchesLoaded(List<RemoteRef> branches) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadRemoteBranches();
        }
    }

    record StashesLoaded(List<Stash> stashes) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadStashes();
        }
    }

    record WorktreesLoaded(List<Worktree> worktrees) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadWorktrees();
        }
    }

    record CommitsLoaded(List<Commit> commits) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadCommits();
        }
    }

    record CommitSearchLoaded(CommitQuery query, List<Commit> commits) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.SearchCommits(query);
        }
    }

    record BranchLogLoaded(BranchRef branch, List<Commit> commits) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadBranchLog(branch);
        }
    }

    record DivergenceLoaded(Branch.Name branch, BranchRef other, Divergence divergence) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadDivergence(branch, other);
        }
    }

    record CommitDetailLoaded(Sha sha, DiffOptions options, DiffText changes) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadCommitDetail(sha, options);
        }
    }

    record FileDiffLoaded(List<FileEntry> files, DiffOptions options, Diffs diff) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadFileDiff(files, options);
        }
    }

    record Failed(Cmd.Load cmd, Failure failure) implements LoadMsg {}
}
