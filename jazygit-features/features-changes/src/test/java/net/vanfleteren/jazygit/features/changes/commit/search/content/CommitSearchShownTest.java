package net.vanfleteren.jazygit.features.changes.commit.search.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearch;
import net.vanfleteren.jazygit.features.changes.commit.search.CommitSearchMsg;
import net.vanfleteren.jazygit.features.changes.commit.search.SearchedModels;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsListing;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;
import org.junit.jupiter.api.Test;

/**
 * What the search of the log shows in the commits pane, through its keys (ADR 172).
 */
class CommitSearchShownTest {

    @Test
    void withoutASearchItShowsNothing() {
        assertEquals(Option.none(), CommitSearchShown.line(TestModels.loaded()));
        assertEquals(Option.none(), CommitSearchShown.listing(TestModels.loaded()));
    }

    @Test
    void theOpenLineSendsTheMessagesOfTheSearch() {
        Model opened =
                Update.update(TestModels.loaded(), new CommitSearchMsg.Opened()).model();
        PaneLine line = CommitSearchShown.line(opened).get();

        assertEquals("", line.text());
        assertEquals(new CommitSearchMsg.Edited("fix"), line.edited().apply("fix"));
        assertEquals(new CommitSearchMsg.Submitted(), line.submitted());
        assertEquals(new CommitSearchMsg.Cancelled(), line.cancelled());
    }

    @Test
    void whatTheSearchFoundIsListedUntilItIsCleared() {
        Model searched = SearchedModels.searched(TestModels.loaded(), "fix", List.of(commit("s0")));
        CommitsListing listing = CommitSearchShown.listing(searched).get();

        assertEquals(Option.none(), CommitSearchShown.line(searched));
        assertEquals("Commits: /fix", listing.title());
        assertEquals(new Loadable.Loaded<>(List.of(commit("s0"))), listing.commits());
        assertEquals("No commits match", listing.emptyText());
        assertEquals(new CommitSearchMsg.Cleared(), listing.cleared());
    }

    @Test
    void theTitleSaysWhatWasSearchedAndWhenThereWereMoreThanItLists() {
        CommitSearch search = CommitSearch.loading("fix");

        assertEquals("Commits: /fix", CommitSearchShown.title(search));
        assertEquals(
                "Commits: /fix (first 3000)",
                CommitSearchShown.title(search.withResults(
                        new Loadable.Loaded<>(List.range(0, History.MAX_COMMITS).map(i -> commit("s" + i))))));
    }

    private static Commit commit(String sha) {
        return Fixtures.commit(
                new Sha(sha + "0000"), "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, "", List.empty());
    }
}
