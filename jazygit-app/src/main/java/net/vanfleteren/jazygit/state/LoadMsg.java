package net.vanfleteren.jazygit.state;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
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

    /**
     * The local and the remote-tracking branches, read at the same moment.
     */
    record BranchesLoaded(AllBranches branches) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadBranches();
        }
    }

    /**
     * What each of the loads that {@link Cmd.CheckMetadata} guards is read from looks like now; none when that
     * cannot be told, and the load is then to run anyway.
     */
    record MetadataChecked(List<Tuple2<Cmd.Load, Option<MetadataStamp>>> stamps) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.CheckMetadata();
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

    /**
     * Whether those of {@code worktrees} that could be read have changes, by their location.
     */
    record WorktreeChangesLoaded(List<String> worktrees, Map<String, Boolean> changes) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadWorktreeChanges(worktrees);
        }
    }

    /**
     * The log of HEAD, with what {@code load} read of it after.
     */
    record CommitsLoaded(List<Commit> commits, Cmd.LoadAhead load, Either<Failure, Option<Ahead>> ahead)
            implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadCommits(load);
        }
    }

    record CommitSearchLoaded(CommitQuery query, Option<BranchRef> notOn, List<Commit> commits) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.SearchCommits(query, notOn);
        }
    }

    /**
     * The commits {@code load} read: none when there was no base to compare with.
     */
    record AheadLoaded(Cmd.LoadAhead load, Option<Ahead> ahead) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return load;
        }
    }

    /**
     * The log of {@code branch}, with what {@code load} read of it after.
     */
    record BranchLogLoaded(
            BranchRef branch, List<Commit> commits, Cmd.LoadAhead load, Either<Failure, Option<Ahead>> ahead)
            implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadBranchLog(branch, load);
        }
    }

    record DivergenceLoaded(BranchName branch, BranchRef other, Divergence divergence) implements LoadMsg {

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

    record StashDetailLoaded(Sha sha, DiffOptions options, DiffText changes) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadStashDetail(sha, options);
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
