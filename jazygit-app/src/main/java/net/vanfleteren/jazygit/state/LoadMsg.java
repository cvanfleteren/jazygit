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
     * The local and the remote-tracking branches, read at the same moment, with the parents of the branches and how
     * far each is from its base, read right after; none when the parents could not be read.
     */
    record BranchesLoaded(AllBranches branches, Option<Bases> bases) implements LoadMsg {

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
     * What the bases of the branches are, and how far each local branch is from its own.
     *
     * @param parents the branch each branch was made from, of those that have one recorded
     * @param counts  how far each local branch that has a base is from it, by its name (ADR 46)
     */
    record Bases(Map<BranchName, BranchName> parents, Map<BranchName, BaseDistance> counts) {}

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

    /**
     * The changes of the commit {@code sha}, read for the preview whose slot is {@code preview}; every preview of
     * it shows them.
     */
    record CommitDetailLoaded(Sha sha, DiffOptions options, DiffText changes, Cmd.Slot preview) implements LoadMsg {

        /**
         * The changes of the commit {@code sha}, read for the commits pane.
         */
        public CommitDetailLoaded(Sha sha, DiffOptions options, DiffText changes) {
            this(sha, options, changes, Cmd.Slot.COMMIT_DETAIL);
        }

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadCommitDetail(sha, options, preview);
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

    record CommitFilesLoaded(CommitFilesView.Changes of, List<CommitFile> files) implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadCommitFiles(of);
        }
    }

    record CommitFileDiffLoaded(CommitFilesView.Changes of, List<CommitFile> files, DiffOptions options, DiffText diff)
            implements LoadMsg {

        @Override
        public Cmd.Load cmd() {
            return new Cmd.LoadCommitFileDiff(of, files, options);
        }
    }

    record Failed(Cmd.Load cmd, Failure failure) implements LoadMsg {}

    /**
     * {@code cmd} did not run: a newer load for its {@link Cmd#slot() slot} was asked for before it started.
     */
    record Skipped(Cmd.Load cmd) implements LoadMsg {}
}
