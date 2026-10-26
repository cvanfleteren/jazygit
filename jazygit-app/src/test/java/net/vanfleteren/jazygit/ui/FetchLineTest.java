package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Freshness;
import net.vanfleteren.jazygit.state.LoadPullRequests;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.prs.PrOverview;
import org.junit.jupiter.api.Test;

/**
 * The line saying which remote is being fetched from, or that the pull requests are being read.
 */
class FetchLineTest {

    private static String rendered(Model model) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> FetchLine.render(model))) {
            return RenderedText.of(runner, () -> FetchLine.render(model)).strip();
        }
    }

    @Test
    void showsTheRemoteBeingFetchedFrom() throws Exception {
        assertEquals(
                "Fetching origin",
                rendered(TestModels.loaded()
                        .mapJobs(jobs ->
                                jobs.withFetching(Option.of(new Fetching(Option.of("origin"), List.empty()))))));
    }

    @Test
    void showsThePullRequestsBeingReadWhenNoRemoteIsBeingFetchedFrom() throws Exception {
        Model reading = TestModels.loaded()
                .mapGitHub(gitHub -> gitHub.withReading(io.vavr.collection.HashSet.of(new LoadPullRequests())));

        assertEquals("Reading pull requests from GitHub", rendered(reading));
        assertEquals(
                "Fetching origin",
                rendered(reading.mapJobs(
                        jobs -> jobs.withFetching(Option.of(new Fetching(Option.of("origin"), List.empty()))))));
    }

    @Test
    void isBlankWhileNoRemoteIsBeingFetchedFrom() throws Exception {
        assertEquals("", rendered(TestModels.loaded()));
        assertEquals("", rendered(TestModels.loaded().mapJobs(jobs -> jobs.withFetching(Option.of(Fetching.LISTING)))));
    }

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Test
    void sayHowLongAgoTheRemotesWereFetchedFromAndWhetherTheLastFetchFailed() {
        Model model = TestModels.loaded().withNow(NOW);
        Freshness fetched = Freshness.NEVER.succeeded("origin", NOW.minusSeconds(120));

        assertEquals("", FetchLine.freshness(model));
        assertEquals("fetched 2m ago", FetchLine.freshness(model.mapJobs(jobs -> jobs.withLastFetch(fetched))));
        assertEquals(
                "fetched 2m ago · last fetch failed",
                FetchLine.freshness(model.mapJobs(jobs -> jobs.withLastFetch(fetched.failed("upstream")))));
        assertEquals(
                "fetched 2m ago",
                FetchLine.freshness(model.mapJobs(jobs ->
                        jobs.withLastFetch(fetched.failed("upstream").succeeded("upstream", NOW.minusSeconds(120))))));
        assertEquals(
                "last fetch failed",
                FetchLine.freshness(model.mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.failed("origin")))));
    }

    @Test
    void onThePullRequestsScreenSaysHowLongAgoTheyWereRead() {
        Model prs = TestModels.loaded()
                .withNow(NOW)
                .withScreen(Screen.PULL_REQUESTS)
                .mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", NOW)))
                .withPrOverview(PrOverview.INITIAL.withFreshness(Freshness.NEVER
                        .succeeded("mine", NOW.minusSeconds(3 * 3600))
                        .failed("repository")));

        assertEquals("read 3h ago · last read failed", FetchLine.freshness(prs));
    }

    @Test
    void countsBy10sUnderAMinute() {
        assertEquals("fetched just now", fetchedAgo(Duration.ofSeconds(5)));
        assertEquals("fetched 10s ago", fetchedAgo(Duration.ofSeconds(10)));
        assertEquals("fetched 10s ago", fetchedAgo(Duration.ofSeconds(15)));
        assertEquals("fetched 50s ago", fetchedAgo(Duration.ofSeconds(55)));
        assertEquals("fetched 1m ago", fetchedAgo(Duration.ofSeconds(60)));
    }

    @Test
    void aStepShowsFromTheMomentItIsReachedAsTheTimesAreExact() {
        assertEquals("fetched just now", fetchedAgo(Duration.ofMillis(9_999)));
        assertEquals("fetched 10s ago", fetchedAgo(Duration.ofMillis(10_000)));
        assertEquals("fetched 10s ago", fetchedAgo(Duration.ofMillis(19_999)));
        assertEquals("fetched 20s ago", fetchedAgo(Duration.ofMillis(20_000)));
        assertEquals("fetched 50s ago", fetchedAgo(Duration.ofMillis(59_999)));
        assertEquals("fetched 1m ago", fetchedAgo(Duration.ofMillis(60_000)));
    }

    private static String fetchedAgo(Duration since) {
        return FetchLine.freshness(TestModels.loaded()
                .withNow(NOW)
                .mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", NOW.minus(since)))));
    }
}
