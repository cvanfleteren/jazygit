package net.vanfleteren.jazygit.feature.fetch;

import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.LoadPullRequests;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

/**
 * Fetching from the remotes every few seconds, one after the other.
 */
class FetchUpdateTest {

    @Test
    void aTickListsTheRemotesUnlessAFetchIsStillRunning() {
        Next started = Update.update(loaded(), new FetchMsg.Tick());

        assertEquals(List.of(new FetchCmd.ListRemotes()), started.cmds());
        assertEquals(Option.of(Fetching.LISTING), started.model().jobs().fetching());
        assertEquals(
                List.of(), Update.update(started.model(), new FetchMsg.Tick()).cmds());
    }

    @Test
    void theRemotesAreFetchedOneAfterTheOtherAndThenTheBranchesReload() {
        Model listing = Update.update(loaded(), new FetchMsg.Tick()).model();

        Next origin = Update.update(listing, new FetchMsg.RemotesListed(List.of("origin", "upstream")));
        assertEquals(List.of(new FetchCmd.Fetch("origin")), origin.cmds());
        assertEquals(
                Option.of(new Fetching(Option.of("origin"), List.of("upstream"))),
                origin.model().jobs().fetching());

        // A remote that cannot be fetched from does not stop the others.
        Next upstream =
                Update.update(origin.model(), new FetchMsg.FetchFailed("origin", refused("Could not resolve host")));
        assertEquals(List.of(new FetchCmd.Fetch("upstream")), upstream.cmds());
        assertEquals(
                Option.of(new Fetching(Option.of("upstream"), List.empty())),
                upstream.model().jobs().fetching());

        Next done = Update.update(upstream.model(), new FetchMsg.Fetched("upstream", Instant.EPOCH));
        assertEquals(Option.none(), done.model().jobs().fetching());
        assertTrue(done.cmds().contains(new LoadBranches()));
        assertTrue(done.cmds().contains(new LoadPullRequests()));
        // Neither logged nor reported.
        assertEquals(List.of(), done.model().outcome().commandLog());
        assertEquals(Option.none(), done.model().outcome().error());
    }

    @Test
    void withoutRemotesOrRemotesToListTheFetchIsOver() {
        Model listing = Update.update(loaded(), new FetchMsg.Tick()).model();

        assertEquals(
                Option.none(),
                Update.update(listing, new FetchMsg.RemotesListed(List.empty()))
                        .model()
                        .jobs()
                        .fetching());
        Model failed = Update.update(listing, new FetchMsg.ListingFailed(refused("boom")))
                .model();
        assertEquals(Option.none(), failed.jobs().fetching());
        assertEquals(Option.none(), failed.outcome().error());
        assertEquals(
                List.of(new FetchCmd.ListRemotes()),
                Update.update(failed, new FetchMsg.Tick()).cmds());
    }

    @Test
    void aRequestedFetchStartsAtOnceWhenNoneIsRunning() {
        Next started = FetchUpdate.requested(Next.of(loaded()));

        assertEquals(List.of(new FetchCmd.ListRemotes()), started.cmds());
        assertEquals(Option.of(Fetching.LISTING), started.model().jobs().fetching());
    }

    @Test
    void aFetchRequestedWhileOneRunsFollowsItOnceItIsDone() {
        Model fetching = Update.update(
                        Update.update(loaded(), new FetchMsg.Tick()).model(),
                        new FetchMsg.RemotesListed(List.of("origin")))
                .model();

        Next requested = FetchUpdate.requested(Next.of(fetching));
        assertEquals(List.of(), requested.cmds());
        assertTrue(requested.model().jobs().fetching().exists(Fetching::again));

        Next done = Update.update(requested.model(), new FetchMsg.Fetched("origin", Instant.EPOCH));
        assertTrue(done.cmds().contains(new LoadBranches()));
        assertTrue(done.cmds().contains(new FetchCmd.ListRemotes()), done.cmds().toString());
        assertEquals(Option.of(Fetching.LISTING), done.model().jobs().fetching());

        // That one is not followed by another.
        Model listed = Update.update(done.model(), new FetchMsg.RemotesListed(List.of("origin")))
                .model();
        assertEquals(
                Option.none(),
                Update.update(listed, new FetchMsg.Fetched("origin", Instant.EPOCH))
                        .model()
                        .jobs()
                        .fetching());
    }

    @Test
    void aFetchRequestedWhileTheRemotesCouldNotBeListedStillRuns() {
        Model listing = FetchUpdate.requested(
                        Next.of(Update.update(loaded(), new FetchMsg.Tick()).model()))
                .model();

        Next failed = Update.update(listing, new FetchMsg.ListingFailed(refused("boom")));

        assertEquals(List.of(new FetchCmd.ListRemotes()), failed.cmds());
        assertEquals(Option.of(Fetching.LISTING), failed.model().jobs().fetching());
    }

    @Test
    void theLastFetchSaysWhenARemoteWasFetchedFromAndWhichFailedSince() {
        Instant at = Instant.parse("2026-10-17T12:00:00Z");
        Model listed = Update.update(
                        Update.update(loaded(), new FetchMsg.Tick()).model(),
                        new FetchMsg.RemotesListed(List.of("origin", "upstream")))
                .model();

        Model fetched =
                Update.update(listed, new FetchMsg.Fetched("origin", at)).model();
        assertEquals(Option.of(at), fetched.jobs().lastFetch().read());
        assertFalse(fetched.jobs().lastFetch().stale());

        Model failed = Update.update(fetched, new FetchMsg.FetchFailed("upstream", refused("offline")))
                .model();
        assertEquals(Option.of(at), failed.jobs().lastFetch().read());
        assertTrue(failed.jobs().lastFetch().stale());
    }
}
