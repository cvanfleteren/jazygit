package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Ahead;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
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
                toggled(model.mapRepoView(repoView -> repoView.withOnlyAhead(!repoView.onlyAhead())));
        };
    }

    /**
     * Runs the search again when it now leaves out other commits.
     */
    private static Next toggled(Model model) {
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
     * The commits of HEAD that are not on its base, once they are read and it has one.
     */
    public static Option<Ahead> head(Model model) {
        return model.repo().ahead().toOption().flatMap(ahead -> ahead);
    }

    /**
     * The commits of {@code branch} that are not on its base, once they are read and it has one.
     */
    public static Option<Ahead> branch(Model model, BranchRef branch) {
        return model.repoView()
                .branchAhead()
                .filter(preview -> preview.key().equals(branch))
                .flatMap(preview -> preview.value().toOption())
                .flatMap(ahead -> ahead);
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
     * {@code model} reading what is not on the base for the branch whose log was just read, keeping what was read
     * for it before meanwhile.
     */
    static Next branchLogLoaded(Model model, BranchRef branch) {
        Model loading = model.mapRepoView(repoView -> repoView.withBranchAhead(Option.of(repoView.branchAhead()
                .filter(preview -> preview.key().equals(branch))
                .getOrElse(() -> new Preview<>(branch, Loadable.loading())))));
        return Next.of(loading, of(loading, branch));
    }

    /**
     * Keeps what {@code load} read, unless it was for a branch no longer shown.
     */
    static Model loaded(Model model, Cmd.LoadAhead load, Option<Ahead> ahead) {
        return load.branch()
                .fold(
                        () -> model.mapRepo(repo -> repo.withAhead(repo.ahead().reload(ahead))),
                        branch -> model.mapRepoView(repoView -> repoView.withBranchAhead(
                                Preview.updated(repoView.branchAhead(), branch, preview -> preview.reload(ahead)))));
    }

    /**
     * Keeps that {@code load} failed, unless it was for a branch no longer shown.
     */
    static Model failed(Model model, Cmd.LoadAhead load, Failure failure) {
        return load.branch()
                .fold(
                        () -> model.mapRepo(repo -> repo.withAhead(new Loadable.Failed<>(failure))),
                        branch -> model.mapRepoView(repoView -> repoView.withBranchAhead(
                                Preview.updated(repoView.branchAhead(), branch, preview -> preview.failed(failure)))));
    }

    /**
     * The loads to run again once the branches were read: those whose base is another one now, or whose base moved.
     *
     * @param before the model before the branches were read
     */
    static List<Cmd> branchesLoaded(Model before, Model next) {
        Option<Cmd> head = Option.when(
                next.repo().commits().toOption().isDefined()
                        && (!ofHead(before).equals(ofHead(next)) || baseMoved(before, next, head(next))),
                () -> ofHead(next));
        Option<Cmd> branch = next.repoView()
                .branchAhead()
                .map(Preview::key)
                .filter(shown -> !of(before, shown).equals(of(next, shown))
                        || baseMoved(before, next, AheadUpdate.branch(next, shown)))
                .map(shown -> of(next, shown));
        return head.toList().appendAll(branch);
    }

    /**
     * The loads to run again as they failed.
     */
    static List<Cmd.Load> retries(Model model) {
        return List.<Cmd.Load>empty()
                .appendAll(Option.when(model.repo().ahead() instanceof Loadable.Failed, () -> ofHead(model)))
                .appendAll(model.repoView()
                        .branchAhead()
                        .filter(preview -> preview.value() instanceof Loadable.Failed)
                        .map(preview -> of(model, preview.key())));
    }

    private static boolean baseMoved(Model before, Model next, Option<Ahead> ahead) {
        return ahead.exists(read -> !LoadUpdate.tipOf(before, read.base()).equals(LoadUpdate.tipOf(next, read.base())));
    }

    private static Option<Branch.Name> main(Model model) {
        return model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(Branch::isDefault))
                .map(Branch::name);
    }

    private static Branch.Name name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(Branch.Name name) -> name;
            case BranchRef.Remote(var remote) -> remote.branch();
        };
    }
}
