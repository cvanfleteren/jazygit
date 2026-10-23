package net.vanfleteren.jazygit.feature.commit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import org.junit.jupiter.api.Test;

/**
 * Going through the summaries of earlier commits with Up and Down.
 */
class SummaryHistoryTest {

    private static Commit commit(String summary) {
        return Fixtures.commit(
                new Sha("aaaa"), "Ada", "ada@example.com", Instant.EPOCH, summary, "", List.of(new Sha("bbbb")));
    }

    private static Commit merge(String summary) {
        return Fixtures.commit(
                new Sha("cccc"),
                "Ada",
                "ada@example.com",
                Instant.EPOCH,
                summary,
                "",
                List.of(new Sha("aaaa"), new Sha("bbbb")));
    }

    private static final SummaryHistory HISTORY =
            SummaryHistory.of(List.of(commit("third"), commit("second"), commit("first")));

    @Test
    void upGoesToOlderSummaries() {
        SummaryHistory once = HISTORY.older("typed").get();
        SummaryHistory twice = once.older("third").get();

        assertEquals("third", once.shown());
        assertEquals("second", twice.shown());
    }

    @Test
    void downGoesBackToNewerSummariesAndThenToWhatWasTyped() {
        SummaryHistory twice = HISTORY.older("typed").get().older("third").get();
        SummaryHistory back = twice.newer().get();

        assertEquals("third", back.shown());
        assertEquals("typed", back.newer().get().shown());
    }

    @Test
    void upStopsAtTheOldestSummary() {
        SummaryHistory oldest = HISTORY.older("typed")
                .get()
                .older("third")
                .get()
                .older("second")
                .get();

        assertEquals("first", oldest.shown());
        assertEquals(Option.none(), oldest.older("first"));
    }

    @Test
    void downDoesNothingBeforeUp() {
        assertEquals(Option.none(), HISTORY.newer());
    }

    @Test
    void anEditToARecalledSummaryIsDropped() {
        SummaryHistory recalled = HISTORY.older("typed").get();

        assertEquals("second", recalled.older("third, edited").get().shown());
        assertEquals("typed", recalled.newer().get().shown());
    }

    @Test
    void withoutCommitsUpDoesNothing() {
        assertEquals(Option.none(), SummaryHistory.of(List.empty()).older("typed"));
    }

    @Test
    void mergesAreLeftOut() {
        SummaryHistory history = SummaryHistory.of(List.of(merge("Merge branch 'x'"), commit("work")));

        assertEquals("work", history.older("").get().shown());
        assertEquals(Option.none(), history.older("").get().older("work"));
    }

    @Test
    void aSummaryIsOnlyShownOnceWhereItIsNewest() {
        SummaryHistory history = SummaryHistory.of(List.of(commit("wip"), commit("fix"), commit("wip")));

        SummaryHistory oldest = history.older("").get().older("wip").get();
        assertEquals("fix", oldest.shown());
        assertEquals(Option.none(), oldest.older("fix"));
    }

    @Test
    void theSummaryUpStartsFromIsSkipped() {
        SummaryHistory rewording = HISTORY.older("third").get();

        assertEquals("second", rewording.shown());
        assertTrue(rewording.older("second").get().older("first").isEmpty());
    }
}
