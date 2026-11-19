package net.vanfleteren.jazygit.features.changes.commit.search;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.CommitQuery;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.Target;
import net.vanfleteren.jazygit.kernel.state.load.Targeted;
import net.vanfleteren.jazygit.lib.lens.Lens;

/**
 * Reads the commits of the log of HEAD that {@code query} finds.
 */
public record SearchCommits(CommitQuery query) implements Targeted<List<Commit>> {

    /**
     * The load that reads the results of {@code search}.
     */
    public static SearchCommits of(CommitSearch search) {
        return new SearchCommits(search.query());
    }

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("changes.work.search"));
    }

    /**
     * The results of the search, while it is the one that ran.
     */
    @Override
    public Target<List<Commit>> target() {
        return Target.inPart(
                CommitSearchView.PART,
                Lens.where(
                                CommitSearchView::search,
                                CommitSearchView::withSearch,
                                search -> of(search).equals(this))
                        .andThen(Lens.of(CommitSearch::results, CommitSearch::withResults)));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .search(query)
                .mapLeft(Failure::of)
                .fold(this::failed, commits -> new Found(query, commits));
    }

    /**
     * The commits {@code query} found.
     */
    public record Found(CommitQuery query, List<Commit> commits) implements LoadMsg.Read<List<Commit>> {

        @Override
        public List<Commit> value() {
            return commits;
        }

        @Override
        public SearchCommits cmd() {
            return new SearchCommits(query);
        }
    }
}
