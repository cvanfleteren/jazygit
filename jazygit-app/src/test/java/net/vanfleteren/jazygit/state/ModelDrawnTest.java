package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import org.junit.jupiter.api.Test;

/**
 * What runs in the background draws the spinners and the fetch line: only what merely starts the loads is left out
 * of what is drawn.
 */
class ModelDrawnTest {

    private final Model model = TestModels.loaded();

    @Test
    void aBusyBranchIsDrawn() {
        Model busy = model.withBusyBranch(
                new BranchRef.Local(new BranchName("main")), Operation.of("log.push", "error.push"));

        assertNotEquals(model.drawn(), busy.drawn());
    }

    @Test
    void aFetchIsDrawn() {
        Model fetching = model.mapJobs(jobs -> jobs.withFetching(Option.of(new Fetching(Option.none(), List.empty()))));

        assertNotEquals(model.drawn(), fetching.drawn());
    }

    @Test
    void whenTheRemotesWereFetchedFromIsDrawn() {
        Model fetched = model.mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", Instant.EPOCH)));

        assertNotEquals(model.drawn(), fetched.drawn());
    }

    @Test
    void theLoadsRunningAndWhenTheyReadAreNotDrawn() {
        Model loading = model.mapJobs(jobs -> jobs.withRefreshing(HashSet.of(new Cmd.LoadStatus()))
                .withWorktreeChangesRead(Option.of(Instant.EPOCH))
                .ticked()
                .withRead(new Cmd.LoadStatus(), true));

        assertEquals(model.drawn(), loading.drawn());
    }
}
