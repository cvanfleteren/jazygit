package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The commits of HEAD and of the highlighted branch that are not on their base: which ones to read, and listing
 * only those or the whole logs.
 */
public final class AheadUpdate {

    private AheadUpdate() {}

    public static Next update(Model model, AheadMsg msg) {
        return switch (msg) {
            case AheadMsg.Toggled() ->
                searchFollowed(model.mapRepoView(repoView -> repoView.withOnlyAhead(!repoView.onlyAhead())));
        };
    }

    /**
     * Runs the search again when it should leave out other commits than it does: after the logs were switched, or
     * once the base of HEAD is known, or another one.
     */
    private static Next searchFollowed(Model model) {
        Option<BranchRef> notOn = searchNotOn(model);
        return model.repoView()
                .commitSearch()
                .filter(search -> !search.notOn().equals(notOn))
                .map(search -> CommitSearch.loading(search.text(), notOn))
                .map(search -> Next.of(
                        model.mapRepoView(repoView -> repoView.withCommitSearch(Option.of(search))), search.load()))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The branch whose commits a search of the log of HEAD leaves out: its base, while only the commits not on it are
     * listed.
     */
    public static Option<BranchRef> searchNotOn(Model model) {
        return Option.when(model.repoView().onlyAhead(), () -> head(model).map(Ahead::base))
                .flatMap(base -> base);
    }

    /**
     * The commits of HEAD that are not on its base, once they are read for the log shown and it has one. Those read
     * for another HEAD, while its log is read anew or they are, are not: they would mark the wrong commits, and list
     * commits that are not on HEAD to act on.
     */
    public static Option<Ahead> head(Model model) {
        return model.repo()
                .ahead()
                .toOption()
                .flatMap(ahead -> ahead)
                .filter(ahead -> readFor(ahead, model.repo().commits()));
    }

    /**
     * The commits of {@code branch} that are not on its base, once they are read for the log of it shown and it has
     * one.
     */
    public static Option<Ahead> branch(Model model, BranchRef branch) {
        Loadable<List<Commit>> log = model.repoView()
                .branchLog()
                .map(Preview::shown)
                .filter(shown -> shown.key().equals(branch))
                .map(Preview::value)
                .getOrElse(Loadable.loading());
        return model.repoView()
                .branchAhead()
                .filter(preview -> preview.key().equals(branch))
                .flatMap(preview -> preview.value().toOption())
                .flatMap(ahead -> ahead)
                .filter(ahead -> readFor(ahead, log));
    }

    /**
     * Whether {@code ahead} was read from the tip of {@code log}.
     */
    private static boolean readFor(Ahead ahead, Loadable<List<Commit>> log) {
        return log.toOption().flatMap(List::headOption).exists(tip -> tip.sha().equals(ahead.tip()));
    }

    /**
     * The load of the commits of HEAD that are not on its base, as the branches read last tell what that is.
     */
    public static Cmd.LoadAhead ofHead(Model model) {
        return new Cmd.LoadAhead(Option.none(), model.repo().currentBranch().map(Branch::name), main(model));
    }

    /**
     * The load of the commits of {@code branch} that are not on its base.
     */
    public static Cmd.LoadAhead of(Model model, BranchRef branch) {
        return new Cmd.LoadAhead(Option.of(branch), Option.of(name(branch)), main(model));
    }

    /**
     * Keeps what {@code load} read of the log of {@code branch} just read, so that the log is shown with it at once.
     */
    static Next branchLogLoaded(
            Model model, BranchRef branch, Cmd.LoadAhead load, Either<Failure, Option<Ahead>> ahead) {
        Model reading = model.mapRepoView(repoView -> repoView.withBranchAhead(Option.of(repoView.branchAhead()
                .filter(preview -> preview.key().equals(branch))
                .getOrElse(() -> new Preview<>(branch, Loadable.loading())))));
        return read(reading, load, ahead);
    }

    /**
     * Keeps what {@code load} read with a log, or that it failed, as {@link #loaded} and {@link #failed} do. It was
     * asked for with the log, so the branches read since may tell another base: then it is read again for that one.
     */
    static Next read(Model model, Cmd.LoadAhead load, Either<Failure, Option<Ahead>> ahead) {
        Next kept = ahead.fold(failure -> Next.of(failed(model, load, failure)), read -> loaded(model, load, read));
        Cmd.LoadAhead current = load.branch().fold(() -> ofHead(kept.model()), branch -> of(kept.model(), branch));
        return current.equals(load) ? kept : new Next(kept.model(), kept.cmds().append(current));
    }

    /**
     * Keeps what {@code load} read, unless it was for a branch no longer shown. For HEAD, the search follows its
     * base.
     */
    static Next loaded(Model model, Cmd.LoadAhead load, Option<Ahead> ahead) {
        return load.branch()
                .fold(
                        () -> searchFollowed(model.mapJobs(jobs -> jobs.withoutFailedRead(load))
                                .mapRepo(repo -> repo.withAhead(repo.ahead().reload(ahead)))),
                        branch -> Next.of(model.mapRepoView(repoView -> repoView.withBranchAhead(
                                Preview.updated(repoView.branchAhead(), branch, preview -> preview.reload(ahead))))));
    }

    /**
     * Keeps that {@code load} failed, unless it was for a branch no longer shown.
     */
    static Model failed(Model model, Cmd.LoadAhead load, Failure failure) {
        return load.branch()
                .fold(
                        () -> model.mapJobs(jobs -> jobs.withFailedRead(load))
                                .mapRepo(repo -> repo.withAhead(repo.ahead().failed(failure))),
                        branch -> model.mapRepoView(repoView -> repoView.withBranchAhead(
                                Preview.updated(repoView.branchAhead(), branch, preview -> preview.failed(failure)))));
    }

    /**
     * The loads to run again once the branches were read: those whose base is another one now, or whose base moved.
     * Not those whose log is read again anyway, as that reads them too: of HEAD, when it is not where its log was
     * read; of the highlighted branch, when it moved.
     *
     * @param before the model before the branches were read
     */
    static List<Cmd> branchesLoaded(Model before, Model next) {
        Option<Cmd> head = Option.when(
                logOfHead(next) && (!ofHead(before).equals(ofHead(next)) || baseMoved(before, next, head(next))),
                () -> ofHead(next));
        Option<Cmd> branch = next.repoView()
                .branchAhead()
                .map(Preview::key)
                .filter(shown -> LoadUpdate.tipOf(before, shown).equals(LoadUpdate.tipOf(next, shown)))
                .filter(shown -> !of(before, shown).equals(of(next, shown))
                        || baseMoved(before, next, AheadUpdate.branch(next, shown)))
                .map(shown -> of(next, shown));
        return head.toList().appendAll(branch);
    }

    /**
     * Whether the log read last is that of HEAD as the status read last has it, so that it is not read again.
     */
    private static boolean logOfHead(Model model) {
        return model.repo()
                .commits()
                .toOption()
                .exists(log -> log.headOption()
                        .map(Commit::sha)
                        .equals(model.repo().status().toOption().flatMap(status -> status.headCommit())));
    }

    /**
     * The loads to run again as they failed.
     */
    static List<Cmd.Load> retries(Model model) {
        return List.<Cmd.Load>empty()
                .appendAll(Option.when(
                        model.repo().ahead() instanceof Loadable.Failed
                                || model.jobs().headAheadFailed(),
                        () -> ofHead(model)))
                .appendAll(model.repoView()
                        .branchAhead()
                        .filter(preview -> preview.value() instanceof Loadable.Failed)
                        .map(preview -> of(model, preview.key())));
    }

    private static boolean baseMoved(Model before, Model next, Option<Ahead> ahead) {
        return ahead.exists(read -> !LoadUpdate.tipOf(before, read.base()).equals(LoadUpdate.tipOf(next, read.base())));
    }

    private static Option<BranchName> main(Model model) {
        return model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(Branch::isDefault))
                .map(Branch::name);
    }

    private static BranchName name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(var remote) -> remote.branch();
        };
    }
}
