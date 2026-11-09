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

    /**
     * What a {@link Cmd.Targeted} load read, to keep in its {@link Target}.
     *
     * @param <T> what the load read
     */
    sealed interface Read<T> extends LoadMsg {

        @Override
        Cmd.Targeted<T> cmd();

        /**
         * What the load read.
         */
        T value();

        /**
         * {@code model} with what was read kept in the target of the load.
         */
        default Model kept(Model model) {
            return cmd().target().reload(model, value());
        }
    }

    record StatusLoaded(RepoStatus status) implements Read<RepoStatus> {

        @Override
        public RepoStatus value() {
            return status;
        }

        @Override
        public Cmd.LoadStatus cmd() {
            return new Cmd.LoadStatus();
        }
    }

    /**
     * The local and the remote-tracking branches, read at the same moment, with the parents of the branches and how
     * far each is from its base, read right after; none when the parents could not be read.
     */
    record BranchesLoaded(Cmd.LoadBranches cmd, AllBranches branches, Option<Bases> bases) implements LoadMsg {}

    /**
     * What each of the loads that {@link Cmd.CheckMetadata} guards is read from looks like now; none when that
     * cannot be told, and the load is then to run anyway. And what the index looks like, which is written anew once it
     * changed (ADR 96); none when that cannot be told yet.
     */
    record MetadataChecked(List<Tuple2<Cmd.Load, Option<MetadataStamp>>> stamps, Option<MetadataStamp> index)
            implements LoadMsg {

        /**
         * The stamps of the loads, without one of the index.
         */
        public MetadataChecked(List<Tuple2<Cmd.Load, Option<MetadataStamp>>> stamps) {
            this(stamps, Option.none());
        }

        @Override
        public Cmd.CheckMetadata cmd() {
            return new Cmd.CheckMetadata();
        }
    }

    record StashesLoaded(List<Stash> stashes) implements Read<List<Stash>> {

        @Override
        public List<Stash> value() {
            return stashes;
        }

        @Override
        public Cmd.LoadStashes cmd() {
            return new Cmd.LoadStashes();
        }
    }

    record WorktreesLoaded(List<Worktree> worktrees) implements Read<List<Worktree>> {

        @Override
        public List<Worktree> value() {
            return worktrees;
        }

        @Override
        public Cmd.LoadWorktrees cmd() {
            return new Cmd.LoadWorktrees();
        }
    }

    /**
     * Whether those of {@code worktrees} that could be read have changes, by their location.
     */
    record WorktreeChangesLoaded(List<String> worktrees, Map<String, Boolean> changes) implements LoadMsg {

        @Override
        public Cmd.LoadWorktreeChanges cmd() {
            return new Cmd.LoadWorktreeChanges(worktrees);
        }
    }

    /**
     * What the bases of the branches are, and how far each local branch is from its own.
     *
     * @param parents the parent of each branch that has one: what GitHub says it is built on, else the branch it was
     *                made from (ADR 72)
     * @param counts  how far each local branch that has a base is from it, by its name (ADR 46)
     */
    record Bases(Map<BranchName, BranchName> parents, Map<BranchName, BaseDistance> counts) {}

    /**
     * The log of HEAD, with what {@code load} read of it after.
     */
    record CommitsLoaded(List<Commit> commits, Cmd.LoadAhead load, Either<Failure, Option<Ahead>> ahead)
            implements Read<List<Commit>> {

        @Override
        public List<Commit> value() {
            return commits;
        }

        @Override
        public Cmd.LoadCommits cmd() {
            return new Cmd.LoadCommits(load);
        }
    }

    record CommitSearchLoaded(CommitQuery query, Option<BranchRef> notOn, List<Commit> commits)
            implements Read<List<Commit>> {

        @Override
        public List<Commit> value() {
            return commits;
        }

        @Override
        public Cmd.SearchCommits cmd() {
            return new Cmd.SearchCommits(query, notOn);
        }
    }

    /**
     * The commits {@code load} read: none when there was no base to compare with.
     */
    record AheadLoaded(Cmd.LoadAhead load, Option<Ahead> ahead) implements LoadMsg {

        @Override
        public Cmd.LoadAhead cmd() {
            return load;
        }
    }

    /**
     * The log of {@code branch}, with what {@code load} read of it after.
     */
    record BranchLogLoaded(
            BranchRef branch, List<Commit> commits, Cmd.LoadAhead load, Either<Failure, Option<Ahead>> ahead)
            implements Read<List<Commit>> {

        @Override
        public List<Commit> value() {
            return commits;
        }

        @Override
        public Cmd.LoadBranchLog cmd() {
            return new Cmd.LoadBranchLog(branch, load);
        }
    }

    record DivergenceLoaded(BranchName branch, BranchRef other, Divergence divergence) implements Read<Divergence> {

        @Override
        public Divergence value() {
            return divergence;
        }

        @Override
        public Cmd.LoadDivergence cmd() {
            return new Cmd.LoadDivergence(branch, other);
        }
    }

    /**
     * The changes of the commit {@code sha}, read for the preview whose slot is {@code preview}; every preview of
     * it shows them.
     */
    record CommitDetailLoaded(Sha sha, DiffOptions options, DiffText changes, Cmd.Slot preview)
            implements Read<DiffText> {

        /**
         * The changes of the commit {@code sha}, read for the commits pane.
         */
        public CommitDetailLoaded(Sha sha, DiffOptions options, DiffText changes) {
            this(sha, options, changes, Cmd.Slot.COMMIT_DETAIL);
        }

        @Override
        public DiffText value() {
            return changes;
        }

        @Override
        public Cmd.LoadCommitDetail cmd() {
            return new Cmd.LoadCommitDetail(sha, options, preview);
        }
    }

    record StashDetailLoaded(Sha sha, DiffOptions options, DiffText changes) implements Read<DiffText> {

        @Override
        public DiffText value() {
            return changes;
        }

        @Override
        public Cmd.LoadStashDetail cmd() {
            return new Cmd.LoadStashDetail(sha, options);
        }
    }

    /**
     * The diff of {@code files}, with what the files it was read from looked like before; none when that cannot be
     * told.
     */
    record FileDiffLoaded(List<FileEntry> files, DiffOptions options, Diffs diff, Option<MetadataStamp> stamp)
            implements Read<Diffs> {

        /**
         * The diff of {@code files}, without telling what its files looked like: it is read again with every status.
         */
        public FileDiffLoaded(List<FileEntry> files, DiffOptions options, Diffs diff) {
            this(files, options, diff, Option.none());
        }

        @Override
        public Diffs value() {
            return diff;
        }

        @Override
        public Cmd.LoadFileDiff cmd() {
            return new Cmd.LoadFileDiff(files, options);
        }
    }

    /**
     * What the files the diff {@code load} is read from look like now; none when that cannot be told, and it is then
     * to be read anyway.
     */
    record FileDiffChecked(Cmd.LoadFileDiff load, Option<MetadataStamp> stamp) implements LoadMsg {

        @Override
        public Cmd.CheckFileDiff cmd() {
            return new Cmd.CheckFileDiff(load);
        }
    }

    record CommitFilesLoaded(CommitFilesView.Changes of, List<CommitFile> files) implements Read<List<CommitFile>> {

        @Override
        public List<CommitFile> value() {
            return files;
        }

        @Override
        public Cmd.LoadCommitFiles cmd() {
            return new Cmd.LoadCommitFiles(of);
        }
    }

    record CommitFileDiffLoaded(CommitFilesView.Changes of, List<CommitFile> files, DiffOptions options, DiffText diff)
            implements Read<DiffText> {

        @Override
        public DiffText value() {
            return diff;
        }

        @Override
        public Cmd.LoadCommitFileDiff cmd() {
            return new Cmd.LoadCommitFileDiff(of, files, options);
        }
    }

    /**
     * The diff of a file read again with the whole file as context, to show more of it (ADR 77).
     */
    record WholeFileRead(Cmd.ReadWholeFile cmd, DiffText whole) implements Read<DiffText> {

        @Override
        public DiffText value() {
            return whole;
        }
    }

    record Failed(Cmd.Load cmd, Failure failure) implements LoadMsg {}

    /**
     * {@code cmd} did not run: a newer load for its {@link Cmd#slot() slot} was asked for before it started.
     */
    record Skipped(Cmd.Load cmd) implements LoadMsg {}
}
