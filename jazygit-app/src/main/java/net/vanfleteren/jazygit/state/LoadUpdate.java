package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.state.LoadMsg.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * What happens when something was read from the repository. The load is no longer running, whatever came of
 * it: {@link Msg.Done} took it out of what runs.
 */
public final class LoadUpdate {

    private LoadUpdate() {}

    public static Next update(Model model, LoadMsg msg) {
        Model done = model.mapJobs(
                jobs -> jobs.withoutFailedRead(msg.cmd()).withRead(msg.cmd(), msg instanceof LoadMsg.Failed));
        return switch (msg) {
            // What was read with other diff options than the ones now chosen is dropped, whatever came of it.
            case LoadMsg m when stale(done, m.cmd()) -> Next.of(done);
            case StatusLoaded loaded -> statusLoaded(model, loaded.kept(done));
            // The parents and the counts that could not be read stay as read before (ADR 46).
            case BranchesLoaded(var load, var branches, var bases) ->
                branchesLoaded(model, done.mapRepo(repo -> {
                    Repo read = repo.withBranches(repo.branches().reload(branches.local()))
                            .withRemoteBranches(repo.remoteBranches().reload(branches.remote()));
                    return bases.fold(
                            () -> read, of -> read.withParents(of.parents()).withBaseDistance(of.counts()));
                }));
            case MetadataChecked(var stamps, var index) -> IndexUpdate.checked(metadataChecked(done, stamps), index);
            // The files of a stash that is dropped close, as its name is now that of another stash.
            case StashesLoaded loaded ->
                Next.of(loaded.kept(done)
                        .mapRepoView(repoView -> repoView.withCommitFiles(repoView.commitFiles()
                                .filter(view -> !(view.of() instanceof CommitFilesView.Changes.OfStash stash)
                                        || loaded.stashes()
                                                .exists(listed -> listed.sha().equals(stash.sha()))))));
            case WorktreeChangesLoaded loaded ->
                Next.of(worktreeChangesRead(done).mapRepo(repo -> repo.withWorktreeChanges(loaded.changes())));
            // The log is only read again when HEAD moved, so the history the search runs on changed too, and what
            // is not on its base: that was read with it, so that the log is shown with it at once.
            case CommitsLoaded loaded ->
                commitsLoaded(AheadUpdate.read(loaded.kept(done), loaded.load(), loaded.ahead()));
            // What arrives after another item was highlighted, or after the diff options changed, is dropped.
            case BranchLogLoaded loaded ->
                loaded.cmd().target().wanted(done)
                        ? AheadUpdate.branchLogLoaded(loaded.kept(done), loaded.branch(), loaded.load(), loaded.ahead())
                        : Next.of(done);
            case AheadLoaded(var load, var ahead) -> AheadUpdate.aheadLoaded(done, load, ahead);
            // What the files looked like is kept, so that the diff is read again only once they change (ADR 58). Only
            // the highlighted diff is looked at, so the stamps of the diffs read before go.
            // The files of the working tree read whole to expand them are read again with it (ADR 77).
            case FileDiffLoaded loaded ->
                rereadWhole(loaded.kept(done).mapJobs(jobs -> {
                    Map<Load, MetadataStamp> others = jobs.readAt().filterKeys(load -> !(load instanceof LoadFileDiff));
                    return jobs.withReadAt(loaded.stamp().fold(() -> others, now -> others.put(msg.cmd(), now)));
                }));
            // Only the diff that is still shown, with the options now chosen, is read again.
            case FileDiffChecked(var load, var stamp) ->
                shownDiff(done).contains(load)
                        ? metadataChecked(done, List.of(Tuple.of((Load) load, stamp)))
                        : Next.of(done);
            // The cursor goes from the diff, which showed while the file was read, to the version asked for (ADR 102).
            case VersionRead read ->
                Next.of(versionShown(read.kept(done), read.cmd().area()));
            // What arrives after another search was run or another item was highlighted, or for a view closed since,
            // finds no target, and is dropped.
            case Read<?> read -> Next.of(read.kept(done));
            // Without the files to tell, the loads they guard run, as they would without them.
            case LoadMsg.Failed(CheckMetadata cmd, var failure) ->
                metadataChecked(done, CheckMetadata.GUARDED.map(load -> Tuple.of(load, Option.none())));
            case LoadMsg.Failed(Load cmd, Failure failure) ->
                Next.of(failed(
                        done.mapJobs(jobs ->
                                jobs.withReadAt(jobs.readAt().remove(cmd)).withFailedRead(cmd)),
                        cmd,
                        failure));
        };
    }

    /**
     * Reads again, with the options now chosen, each file of the working tree read whole to expand it that the diff
     * now read of the working tree has, and that is not being read (ADR 77).
     */
    private static Next rereadWhole(Model model) {
        DiffOptions options = model.preferences().diffOptions();
        Set<String> shown = model.repoView()
                .fileDiff()
                .flatMap(preview -> preview.value().toOption())
                .toList()
                .flatMap(diffs -> diffs.staged()
                        .parsed()
                        .files()
                        .appendAll(diffs.unstaged().parsed().files()))
                .map(file -> file.file().path())
                .toSet();
        List<ReadWholeFile> reads = model.repoView()
                .expanded()
                .values()
                .filter(expansion -> expansion.content() instanceof ScrolledContent.Diff)
                .flatMap(expansion -> expansion.files().values())
                .filter(file -> shown.contains(file.read().path()))
                .map(file -> file.read().withOptions(options))
                .filter(read -> !model.jobs().runs(read))
                .toList();
        // A version of a file of the working tree is read again too, as it was read, whitespace not ignored (ADR 102).
        List<ReadVersion> versions = model.repoView()
                .versions()
                .values()
                .filter(version -> version.content() instanceof ScrolledContent.Diff)
                .map(Version::read)
                .filter(read -> shown.contains(read.path()) && !model.jobs().runs(read))
                .toList();
        return new Next(model, List.<Cmd>narrow(reads).appendAll(versions));
    }

    /**
     * {@code model} with the cursor of the area {@code area} moved onto the version it shows, if it shows one and its
     * cursor is in the diff of it (ADR 102).
     */
    static Model versionShown(Model model, String area) {
        return model.repoView()
                .versions()
                .get(area)
                .filter(version -> version.showing().isDefined())
                .flatMap(version -> model.scrolled()
                        .get(area)
                        .filter(at -> version.holdsFor(at.content()))
                        .map(at -> new Scrolled(
                                at.content(),
                                at.row(),
                                at.column(),
                                at.cursor().map(on -> version.moved(on, version.shown())),
                                Option.none())))
                .map(at -> model.withScrolled(model.scrolled().put(area, at)))
                .getOrElse(model);
    }

    /**
     * Runs the search again on the log just read, unless keeping what is not on its base already did.
     */
    private static Next commitsLoaded(Next read) {
        return new Next(
                read.model(),
                read.cmds()
                        .appendAll(read.model()
                                .repoView()
                                .commitSearch()
                                .map(CommitSearch::load)
                                .filter(search -> !read.cmds().contains(search))));
    }

    /**
     * Whether {@code load} read with other diff options than the ones now chosen, so that what came of it is outdated.
     */
    private static boolean stale(Model model, Load load) {
        return load instanceof DiffLoad diff
                && !diff.options().equals(model.preferences().diffOptions());
    }

    /**
     * Starts the loads of {@code stamps} whose files changed since they were last started, or that cannot tell, and
     * keeps what their files look like now. One that still runs is left alone, with what it was started with: it may
     * have read before the change, so the next tick looks again.
     */
    private static Next metadataChecked(Model model, List<Tuple2<Load, Option<MetadataStamp>>> listed) {
        // The branches are read as GitHub now says they are built on: when that changed, they are read again, and
        // what the files looked like for the load told something else is of no use anymore.
        List<Tuple2<Load, Option<MetadataStamp>>> stamps =
                listed.map(stamp -> stamp.map1(load -> LoadBranches.current(model, load)));
        Load branches = LoadBranches.current(model, LoadBranches.LISTED);
        Model current = model.mapJobs(jobs -> jobs.withReadAt(
                jobs.readAt().filterKeys(load -> !(load instanceof LoadBranches) || load.equals(branches))));
        return started(current, stamps);
    }

    private static Next started(Model model, List<Tuple2<Load, Option<MetadataStamp>>> stamps) {
        List<Tuple2<Load, Option<MetadataStamp>>> changed = stamps.filter(
                        stamp -> !model.jobs().runs(stamp._1()))
                .filter(stamp -> stamp._2().isEmpty()
                        || !model.jobs().readAt().get(stamp._1()).equals(stamp._2()));
        List<Load> loads = changed.map(Tuple2::_1);
        return new Next(
                model.mapJobs(jobs -> jobs.withReadAt(changed.foldLeft(
                        jobs.readAt(),
                        (readAt, stamp) ->
                                stamp._2().fold(() -> readAt.remove(stamp._1()), now -> readAt.put(stamp._1(), now))))),
                List.narrow(loads));
    }

    private static Next statusLoaded(Model before, Model next) {
        boolean headMoved = !before.repo()
                .status()
                .toOption()
                .exists(previous -> next.repo()
                        .status()
                        .toOption()
                        .exists(status -> previous.headCommit().equals(status.headCommit())));
        // Commit loads are not deduplicated: one queued after HEAD moved must still run, even if
        // an earlier one is in progress.
        List<Cmd> cmds = headMoved ? List.of(new LoadCommits(AheadUpdate.ofHead(next))) : List.empty();
        return reloadDiff(next, cmds, headMoved);
    }

    /**
     * Reads the highlighted diff again once its files changed, as they can change on disk without their status
     * changing: it looks at them first, without git (ADR 58), unless HEAD moved, which changes what the staged changes
     * are compared with. This waits for the status: right after (un)staging, the entries the diff was read for are
     * outdated, and reading them again would show their changes as gone for as long as the new status takes to
     * arrive.
     */
    private static Next reloadDiff(Model model, List<Cmd> cmds, boolean headMoved) {
        return shownDiff(model)
                .filter(load -> !model.jobs().runs(load) && !model.jobs().runs(new CheckFileDiff(load)))
                .map(load -> new Next(model, cmds.append(headMoved ? load : new CheckFileDiff(load))))
                .getOrElse(() -> new Next(model, cmds));
    }

    /**
     * The read of the highlighted diff, with the options now chosen, while the status still lists its files.
     */
    private static Option<LoadFileDiff> shownDiff(Model model) {
        return model.repoView()
                .fileDiff()
                .filter(d -> model.repo()
                        .status()
                        .toOption()
                        .exists(status -> status.files().containsAll(d.key())))
                .map(d -> new LoadFileDiff(d.key(), model.preferences().diffOptions()));
    }

    /**
     * Reads what shows a branch again when the branch moved: the log of the highlighted one, and the divergence
     * that is open, once however many of its branches moved. The commits of a branch that is gone are closed. The
     * commits ahead whose base moved, or is another one by the parents read with the branches, are read again.
     */
    private static Next branchesLoaded(Model before, Model next) {
        Next reloaded = reloadDivergence(
                before,
                reloadMovedLog(
                        closeGoneBranchCommits(next),
                        branch -> !tipOf(before, branch).equals(tipOf(next, branch))));
        return new Next(reloaded.model(), reloaded.cmds().appendAll(AheadUpdate.basesRead(before, next)));
    }

    /**
     * Reads the divergence that is open again when one of its branches moved, as it is outdated. When one of
     * them is gone, or the branch does not follow the upstream it is compared with anymore, it is closed: as
     * an error when the comparison cannot be made anymore, quietly when the branch itself is gone.
     */
    private static Next reloadDivergence(Model before, Next next) {
        Model model = next.model();
        return model.repoView()
                .divergence()
                .map(view -> switch (validity(model, view)) {
                    case Validity.Keep() ->
                        view.refs().exists(ref -> !tipOf(before, ref).equals(tipOf(model, ref)))
                                ? new Next(model, next.cmds().append(view.load()))
                                : next;
                    case Validity.Close() -> new Next(withoutDivergence(model), next.cmds());
                    case Validity.CloseWith(AppError error) ->
                        new Next(withoutDivergence(model).fail(error), next.cmds());
                })
                .getOrElse(next);
    }

    /**
     * {@code model} with the commits of the branch that are open closed, quietly, if the branch is gone.
     */
    private static Model closeGoneBranchCommits(Model model) {
        return model.repoView()
                .branchCommits()
                .filter(view -> tipOf(model, view.branch()).contains(Option.none()))
                .map(gone -> model.mapRepoView(repoView -> repoView.withBranchCommits(Option.none())))
                .getOrElse(model);
    }

    private static Model withoutDivergence(Model model) {
        return model.mapRepoView(repoView -> repoView.withDivergence(Option.none()));
    }

    /**
     * Whether {@code view} can still be shown, and if not, whether there is an error to tell.
     */
    private static Validity validity(Model model, DivergenceView view) {
        Option<Option<Branch>> branch =
                model.repo().branches().toOption().map(loaded -> model.repo().branch(view.branch()));
        if (branch.contains(Option.none())) {
            return new Validity.Close();
        }
        Option<Branch> known = branch.flatMap(b -> b);
        return switch (view.against()) {
            case UPSTREAM ->
                known.filter(b -> !b.upstream().map(Upstream::branch).contains(view.other()))
                        .<Validity>map(b -> b.upstream().isDefined()
                                ? new Validity.Close()
                                : new Validity.CloseWith(AppError.noUpstream(b)))
                        .getOrElse(new Validity.Keep());
            case BASE ->
                tipOf(model, view.other())
                        .filter(Option::isEmpty)
                        .<Validity>map(gone -> new Validity.CloseWith(new AppError.NoBaseBranch(view.branch())))
                        .getOrElse(new Validity.Keep());
        };
    }

    /**
     * The tip of {@code branch}, once the branches it is among are loaded: none when it is not one of them.
     */
    static Option<Option<Sha>> tipOf(Model model, BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) ->
                model.repo()
                        .branches()
                        .toOption()
                        .map(loaded -> model.repo().branch(name).map(Branch::tip));
            case BranchRef.Remote(RemoteBranch remote) ->
                model.repo()
                        .remoteBranches()
                        .toOption()
                        .map(loaded -> model.repo().remoteBranch(remote).map(RemoteRef::tip));
        };
    }

    /**
     * Reads the log of the highlighted branch again when it moved (a commit, reset, fetch...), as it is outdated.
     *
     * @param moved whether the highlighted branch is no longer where it was
     */
    private static Next reloadMovedLog(Model next, Predicate<BranchRef> moved) {
        return next.repoView()
                .branchLog()
                .map(Preview::key)
                .filter(moved)
                .map(branch -> Next.of(next, new LoadBranchLog(branch, AheadUpdate.of(next, branch))))
                .getOrElse(() -> Next.of(next));
    }

    /**
     * {@code model} with reading which worktrees have changes finished now, so that a tick waits before reading it
     * again.
     */
    private static Model worktreeChangesRead(Model model) {
        return model.mapJobs(jobs -> jobs.withWorktreeChangesRead(Option.of(model.now())));
    }

    /**
     * {@code open} with {@code f} applied if it holds a view that {@code matches}: what a read gives is only for the
     * view that asked for it, and is dropped when another one is open now.
     */
    static <V> Option<V> mapWhere(Option<V> open, Predicate<V> matches, UnaryOperator<V> f) {
        return open.map(view -> matches.test(view) ? f.apply(view) : view);
    }

    /**
     * {@code model} with {@code cmd} having failed: in its {@link Target}, or as each load that is not targeted keeps
     * what it reads.
     */
    private static Model failed(Model model, Load cmd, Failure failure) {
        return switch (cmd) {
            case Targeted<?> targeted -> targeted.target().failed(model, failure);
            // The parents and the counts read before stay, as they would while reading again (ADR 46).
            case LoadBranches _ ->
                model.mapRepo(repo -> repo.withBranches(repo.branches().failed(failure))
                        .withRemoteBranches(repo.remoteBranches().failed(failure)));
            // Which worktrees have changes is a detail: when the whole read fails, what was read before stays, and
            // a later tick tries again. A single worktree that cannot be read is left out of a read that succeeds.
            case LoadWorktreeChanges _ -> worktreeChangesRead(model);
            case LoadAhead load -> AheadUpdate.failed(model, load, failure);
            // What the files look like is kept by Jobs: when looking at them fails, the next status looks again.
            case CheckMetadata() -> model;
            case CheckFileDiff ignored -> model;
        };
    }

    /**
     * Whether the divergence that is open can still be shown.
     */
    private sealed interface Validity {

        /**
         * It can, and stays open.
         */
        record Keep() implements Validity {}

        /**
         * It cannot, and closes quietly: there is nothing to tell.
         */
        record Close() implements Validity {}

        /**
         * It cannot, and closes telling {@code error}.
         */
        record CloseWith(AppError error) implements Validity {}
    }
}
