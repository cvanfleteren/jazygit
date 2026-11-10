package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitQuery;

/**
 * Reads the commits of the log of HEAD that {@code query} finds, leaving out those on {@code notOn}, if given.
 */
public record SearchCommits(CommitQuery query, Option<BranchRef> notOn) implements Targeted<List<Commit>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.search"));
    }

    /**
     * The results of the search, while it is the one shown.
     */
    @Override
    public Target<List<Commit>> target() {
        return Target.inView(Lens.where(
                        RepoView::commitSearch,
                        RepoView::withCommitSearch,
                        search -> search.load().equals(this))
                .andThen(Lens.of(CommitSearch::results, CommitSearch::withResults)));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .search(query, notOn)
                .mapLeft(Failure::of)
                .fold(this::failed, commits -> new LoadMsg.CommitSearchLoaded(query, notOn, commits));
    }
}
