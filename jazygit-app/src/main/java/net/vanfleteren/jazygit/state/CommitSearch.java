package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitQuery;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * A search of the log of HEAD, whose results the commits pane lists in place of the log.
 *
 * @param text    the query as the user typed it
 * @param query   what it looks for
 * @param notOn   the branch whose commits it leaves out, if any
 * @param results the commits it found, newest first
 */
@With
public record CommitSearch(String text, CommitQuery query, Option<BranchRef> notOn, Loadable<List<Commit>> results) {

    /**
     * The search {@code text} types, leaving out the commits on {@code notOn}, its results still to be read.
     */
    public static CommitSearch loading(String text, Option<BranchRef> notOn) {
        return new CommitSearch(text, CommitQuery.parse(text), notOn, Loadable.loading());
    }

    /**
     * The load that reads the results of this search.
     */
    public Cmd.SearchCommits load() {
        return new Cmd.SearchCommits(query, notOn);
    }

    /**
     * The commit {@code sha}, if it is one of the results read.
     */
    public Option<Commit> commit(Sha sha) {
        return results.toOption()
                .flatMap(found -> found.find(commit -> commit.sha().equals(sha)));
    }
}
