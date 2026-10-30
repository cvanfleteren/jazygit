package net.vanfleteren.jazygit.feature.fetch;

import static net.vanfleteren.jazygit.state.TestModels.done;
import static net.vanfleteren.jazygit.state.TestModels.loaded;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static net.vanfleteren.jazygit.state.TestModels.stepped;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.state.Cmd.LoadBranches;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.prs.LoadRepositoryPullRequests;
import org.junit.jupiter.api.Test;

/**
 * Fetching from the remotes every few seconds, one after the other.
 */
class FetchUpdateTest {

    private static final FetchCmd.ListRemotes LIST = new FetchCmd.ListRemotes();

    private static boolean fetching(Model model) {
        return model.jobs().runs(FetchCmd.class::isInstance);
    }

    private static Model listing() {
        return stepped(loaded(), new FetchMsg.Tick()).model();
    }

    @Test
    void aTickListsTheRemotesUnlessAFetchIsStillRunning() {
        Next started = stepped(loaded(), new FetchMsg.Tick());

        assertEquals(List.of(LIST), started.cmds());
        assertTrue(fetching(started.model()));
        assertEquals(
                List.of(), Update.update(started.model(), new FetchMsg.Tick()).cmds());
    }

    @Test
    void theRemotesAreFetchedOneAfterTheOtherAndThenTheBranchesReload() {
        FetchCmd.Fetch fromOrigin = new FetchCmd.Fetch(new RemoteName("origin"), List.of(new RemoteName("upstream")));
        FetchCmd.Fetch fromUpstream = new FetchCmd.Fetch(new RemoteName("upstream"), List.empty());

        Next origin = done(
                listing(),
                LIST,
                new FetchMsg.RemotesListed(List.of(new RemoteName("origin"), new RemoteName("upstream"))));
        assertEquals(List.of(fromOrigin), origin.cmds());

        // A remote that cannot be fetched from does not stop the others.
        Next upstream = done(
                origin.model(),
                fromOrigin,
                new FetchMsg.FetchFailed(
                        new RemoteName("origin"),
                        List.of(new RemoteName("upstream")),
                        refused("Could not resolve host")));
        assertEquals(List.of(fromUpstream), upstream.cmds());
        assertTrue(fetching(upstream.model()));

        Next done = done(
                upstream.model(), fromUpstream, new FetchMsg.Fetched(new RemoteName("upstream"), List.empty(), true));
        assertFalse(fetching(done.model()));
        assertTrue(done.cmds().contains(new LoadBranches()));
        assertTrue(done.cmds().contains(new LoadRepositoryPullRequests()));
        // Neither logged nor reported.
        assertEquals(List.of(), done.model().outcome().commandLog());
        assertEquals(Option.none(), done.model().outcome().error());
    }

    @Test
    void withoutRemotesOrRemotesToListTheFetchIsOver() {
        assertFalse(fetching(
                done(listing(), LIST, new FetchMsg.RemotesListed(List.empty())).model()));
        Model failed = done(listing(), LIST, new FetchMsg.ListingFailed(refused("boom")))
                .model();
        assertFalse(fetching(failed));
        assertEquals(Option.none(), failed.outcome().error());
        assertEquals(List.of(LIST), Update.update(failed, new FetchMsg.Tick()).cmds());
    }

    @Test
    void aRequestedFetchStartsAtOnceWhenNoneIsRunning() {
        Next started = FetchUpdate.requested(Next.of(loaded()));

        assertEquals(List.of(LIST), started.cmds());
        assertFalse(started.model().jobs().fetchAgain());
    }

    @Test
    void aFetchRequestedTwiceInOneMessageListsTheRemotesOnce() {
        Next twice = FetchUpdate.requested(FetchUpdate.requested(Next.of(loaded())));

        assertEquals(List.of(LIST), twice.cmds());
    }

    @Test
    void aFetchRequestedWhileOneRunsFollowsItOnceItIsDone() {
        FetchCmd.Fetch fromOrigin = new FetchCmd.Fetch(new RemoteName("origin"), List.empty());
        Model fetching = done(listing(), LIST, new FetchMsg.RemotesListed(List.of(new RemoteName("origin"))))
                .model();

        Next requested = FetchUpdate.requested(Next.of(fetching));
        assertEquals(List.of(), requested.cmds());
        assertTrue(requested.model().jobs().fetchAgain());

        Next done =
                done(requested.model(), fromOrigin, new FetchMsg.Fetched(new RemoteName("origin"), List.empty(), true));
        assertTrue(done.cmds().contains(new LoadBranches()));
        assertTrue(done.cmds().contains(LIST), done.cmds().toString());
        assertFalse(done.model().jobs().fetchAgain());

        // That one is not followed by another.
        Model listed = done(done.model(), LIST, new FetchMsg.RemotesListed(List.of(new RemoteName("origin"))))
                .model();
        Next over = done(listed, fromOrigin, new FetchMsg.Fetched(new RemoteName("origin"), List.empty(), true));
        assertFalse(over.cmds().contains(LIST));
        assertFalse(fetching(over.model()));
    }

    @Test
    void aFetchThatChangedNothingReadsGitHubButNotTheRepositoryAgain() {
        FetchCmd.Fetch fromOrigin = new FetchCmd.Fetch(new RemoteName("origin"), List.of(new RemoteName("upstream")));
        FetchCmd.Fetch fromUpstream = new FetchCmd.Fetch(new RemoteName("upstream"), List.empty());
        Model fetching = done(
                        listing(),
                        LIST,
                        new FetchMsg.RemotesListed(List.of(new RemoteName("origin"), new RemoteName("upstream"))))
                .model();
        Model origin = done(
                        fetching, fromOrigin, new FetchMsg.Fetched(new RemoteName("origin"), fromOrigin.rest(), false))
                .model();

        Next nothing =
                done(origin, fromUpstream, new FetchMsg.Fetched(new RemoteName("upstream"), List.empty(), false));
        Next something = done(
                done(fetching, fromOrigin, new FetchMsg.Fetched(new RemoteName("origin"), fromOrigin.rest(), true))
                        .model(),
                fromUpstream,
                new FetchMsg.Fetched(new RemoteName("upstream"), List.empty(), false));

        assertFalse(nothing.cmds().contains(new LoadBranches()), nothing.cmds().toString());
        assertTrue(
                nothing.cmds().contains(new LoadRepositoryPullRequests()),
                nothing.cmds().toString());
        assertTrue(
                something.cmds().contains(new LoadBranches()), something.cmds().toString());
    }

    @Test
    void aFetchRequestedWhileTheRemotesCouldNotBeListedStillRuns() {
        Model listing = FetchUpdate.requested(Next.of(listing())).model();

        Next failed = done(listing, LIST, new FetchMsg.ListingFailed(refused("boom")));

        assertEquals(List.of(LIST), failed.cmds());
        assertFalse(failed.model().jobs().fetchAgain());
    }

    @Test
    void theLastFetchSaysWhenByTheClockARemoteWasFetchedFromAndWhichFailedSince() {
        Instant at = Instant.parse("2026-10-17T12:00:00Z");
        Model listed = done(
                        listing(),
                        LIST,
                        new FetchMsg.RemotesListed(List.of(new RemoteName("origin"), new RemoteName("upstream"))))
                .model();

        Model fetched = done(
                        listed.withNow(at),
                        new FetchCmd.Fetch(new RemoteName("origin"), List.of(new RemoteName("upstream"))),
                        new FetchMsg.Fetched(new RemoteName("origin"), List.of(new RemoteName("upstream")), true))
                .model();
        assertEquals(Option.of(at), fetched.jobs().lastFetch().read());
        assertFalse(fetched.jobs().lastFetch().stale());

        Model failed = done(
                        fetched,
                        new FetchCmd.Fetch(new RemoteName("upstream"), List.empty()),
                        new FetchMsg.FetchFailed(new RemoteName("upstream"), List.empty(), refused("offline")))
                .model();
        assertEquals(Option.of(at), failed.jobs().lastFetch().read());
        assertTrue(failed.jobs().lastFetch().stale());
    }
}
