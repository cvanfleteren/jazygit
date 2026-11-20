package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Predicate;
import net.vanfleteren.jazygit.core.git.model.BaseDistance;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.Ahead;
import net.vanfleteren.jazygit.core.repo.AllBranches;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.diff.BaseTip;
import net.vanfleteren.jazygit.kernel.state.view.Preview;

/**
 * The commits of HEAD and of the highlighted branch that are not on their base: which ones to read, and when.
 */
public final class AheadUpdate {

    private AheadUpdate() {}

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
     * How far the checked out branch is from its base, once that is read (ADR 46).
     */
    public static Option<BaseDistance> headDistance(Model model) {
        return model.repo()
                .currentBranch()
                .flatMap(branch -> model.repo().baseDistance().get(branch.name()));
    }

    /**
     * How far {@code branch} is from its base, once that is read; a remote branch has no base (ADR 46).
     */
    public static Option<BaseDistance> distance(Model model, BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> model.repo().baseDistance().get(name);
            case BranchRef.Remote _ -> Option.none();
        };
    }

    /**
     * The commits of {@code branch} that are not on its base, once they are read for the log of it shown and it has
     * one.
     */
    public static Option<Ahead> branch(Model model, BranchRef branch) {
        Loadable<List<Commit>> log = model.repoView()
                .preview()
                .branchLog()
                .map(Preview::shown)
                .filter(shown -> shown.key().equals(branch))
                .map(Preview::value)
                .getOrElse(Loadable.loading());
        return model.repoView()
                .preview()
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
    public static LoadAhead ofHead(Model model) {
        return load(
                model,
                Option.none(),
                model.repo().status().toOption().flatMap(RepoStatus::headCommit),
                model.repo().currentBranch().map(Branch::name));
    }

    /**
     * The load of the commits of {@code branch} that are not on its base.
     */
    public static LoadAhead of(Model model, BranchRef branch) {
        return load(
                model, Option.of(branch), LoadUpdate.tipOf(model, branch).flatMap(tip -> tip), Option.of(name(branch)));
    }

    /**
     * The load of the commits from {@code tip}, of {@code branch} or of HEAD, named {@code named}, that are not on
     * its base: the first of the bases of ADR 20 the branches read last have, where they have it (ADR 49).
     */
    private static LoadAhead load(Model model, Option<BranchRef> branch, Option<Sha> tip, Option<BranchName> named) {
        return new LoadAhead(
                branch,
                tip,
                BranchRef.bases(
                                named,
                                named.flatMap(name -> model.repo().parents().get(name)),
                                main(model))
                        .iterator()
                        .flatMap(base -> LoadUpdate.tipOf(model, base)
                                .flatMap(found -> found)
                                .map(at -> new BaseTip(base, at)))
                        .headOption());
    }

    /**
     * Keeps what {@code load} read of the log of {@code branch} just read, so that the log is shown with it at once.
     */
    static Next branchLogLoaded(Model model, BranchRef branch, LoadAhead load, Either<Failure, Option<Ahead>> ahead) {
        Model reading = model.mapRepoView(repoView ->
                repoView.mapPreview(previews -> previews.withBranchAhead(Option.of(previews.branchAhead()
                        .filter(preview -> preview.key().equals(branch))
                        .getOrElse(() -> new Preview<>(branch, Loadable.loading()))))));
        return read(reading, load, ahead);
    }

    /**
     * Keeps what {@code load} read with a log, or that it failed, as {@link #loaded} and {@link #failed} do. It was
     * asked for with the log, so the branches read since may tell another base: then it is read again for that one.
     */
    static Next read(Model model, LoadAhead load, Either<Failure, Option<Ahead>> ahead) {
        Next kept = ahead.fold(failure -> Next.of(failed(model, load, failure)), read -> loaded(model, load, read));
        LoadAhead current = load.branch().fold(() -> ofHead(kept.model()), branch -> of(kept.model(), branch));
        return current.equals(load) ? kept : kept.and(current);
    }

    /**
     * Keeps what {@code load} read on its own, not with a log, when it is the load the model makes now; what was read
     * for a branch no longer shown is dropped. Read from where the model had HEAD, the branch or its base then, it can
     * be outdated by a read of the status, the branches or the parents since, which no tick would read again: then it
     * is dropped, as there is no log to show it with, and the load of now is run, unless it runs already, on its own
     * or with a log (ADR 49).
     */
    static Next aheadLoaded(Model model, LoadAhead load, Option<Ahead> ahead) {
        boolean shown = load.branch()
                .forAll(branch -> model.repoView()
                        .preview()
                        .branchAhead()
                        .exists(preview -> preview.key().equals(branch)));
        LoadAhead current = load.branch().fold(() -> ofHead(model), branch -> of(model, branch));
        return !shown
                ? Next.of(model)
                : current.equals(load)
                        ? loaded(model, load, ahead)
                        : model.jobs().runs(reads(current)) ? Next.of(model) : Next.of(model, current);
    }

    /**
     * Whether a command reads what {@code load} reads: it, or the log of HEAD or of a branch read with it.
     */
    private static Predicate<Cmd> reads(LoadAhead load) {
        return cmd -> cmd.equals(load)
                || cmd instanceof LoadCommits(var with) && with.equals(load)
                || cmd instanceof LoadBranchLog log && log.ahead().equals(load);
    }

    /**
     * Keeps what {@code load} read, unless it was for a branch no longer shown.
     */
    static Next loaded(Model model, LoadAhead load, Option<Ahead> ahead) {
        return load.branch()
                .fold(
                        () -> Next.of(model.mapJobs(jobs -> jobs.withoutFailedRead(load))
                                .mapRepo(repo -> repo.withAhead(repo.ahead().reload(ahead)))),
                        branch -> Next.of(model.mapRepoView(repoView ->
                                repoView.mapPreview(previews -> previews.withBranchAhead(Preview.updated(
                                        previews.branchAhead(), branch, preview -> preview.reload(ahead)))))));
    }

    /**
     * Keeps that {@code load} failed, unless it was for a branch no longer shown.
     */
    static Model failed(Model model, LoadAhead load, Failure failure) {
        return load.branch()
                .fold(
                        () -> model.mapJobs(jobs -> jobs.withFailedRead(load))
                                .mapRepo(repo -> repo.withAhead(repo.ahead().failed(failure))),
                        branch -> model.mapRepoView(repoView ->
                                repoView.mapPreview(previews -> previews.withBranchAhead(Preview.updated(
                                        previews.branchAhead(), branch, preview -> preview.failed(failure))))));
    }

    /**
     * The loads to run again once the branches or the parents were read: those whose base is another one now, or
     * whose base moved, as their load tells. Not those whose log is read again anyway, as that reads them too: of
     * HEAD, when it is not where its log was read; of the highlighted branch, when it moved.
     *
     * @param before the model before the branches or the parents were read
     */
    static List<Cmd> basesRead(Model before, Model next) {
        Option<Cmd> head = Option.when(logOfHead(next) && !ofHead(before).equals(ofHead(next)), () -> ofHead(next));
        Option<Cmd> branch = next.repoView()
                .preview()
                .branchAhead()
                .map(Preview::key)
                .filter(shown -> LoadUpdate.tipOf(before, shown).equals(LoadUpdate.tipOf(next, shown)))
                .filter(shown -> !of(before, shown).equals(of(next, shown)))
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
    public static List<Load> retries(Model model) {
        return List.<Load>empty()
                .appendAll(Option.when(
                        model.repo().ahead().isFailed() || model.jobs().headAheadFailed(), () -> ofHead(model)))
                .appendAll(model.repoView()
                        .preview()
                        .branchAhead()
                        .filter(preview -> preview.value().isFailed())
                        .map(preview -> of(model, preview.key())));
    }

    /**
     * The main branch, as the branches read last tell, if they were read and have one.
     */
    public static Option<BranchName> main(Model model) {
        return model.repo().branches().toOption().flatMap(AllBranches::main);
    }

    private static BranchName name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(var remote) -> remote.branch();
        };
    }
}
