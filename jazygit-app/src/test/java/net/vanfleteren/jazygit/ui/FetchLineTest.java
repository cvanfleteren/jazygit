package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.fetch.FetchCmd;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Freshness;
import net.vanfleteren.jazygit.state.Jobs;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.prs.PrOverview;
import org.junit.jupiter.api.Test;

/**
 * The line saying what runs, and how fresh the screen is.
 */
class FetchLineTest {

    private static final Instant START = Instant.parse("2026-10-26T10:00:00Z");

    private static String rendered(Model model, Instant now, int width) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> FetchLine.render(model, now, width))) {
            return RenderedText.of(runner, () -> FetchLine.render(model, now, width))
                    .strip();
        }
    }

    private static String rendered(Model model, Instant now) throws Exception {
        return rendered(model, now, 80);
    }

    private static Model running(Model model, Cmd cmd, Instant started) {
        return model.mapJobs(jobs -> jobs.withStarted(List.of(cmd), started));
    }

    private static String spinning(Instant now, String text) {
        return TimeText.spinnerFrame(now) + " " + text;
    }

    @Test
    void isBlankWhileNothingRuns() throws Exception {
        assertEquals("", rendered(TestModels.loaded(), START));
    }

    @Test
    void aCommandShowsOnlyOnceItRanAWhile() throws Exception {
        Model fetching = running(TestModels.loaded(), new FetchCmd.Fetch("origin"), START);

        assertEquals("", rendered(fetching, START.plusMillis(299)));
        assertEquals(spinning(START.plusMillis(300), "Fetching origin"), rendered(fetching, START.plusMillis(300)));
    }

    @Test
    void theCommandThatRanLongestShowsWithHowManyMoreDo() throws Exception {
        Model model = running(
                running(
                        running(TestModels.loaded(), new FetchCmd.Fetch("origin"), START),
                        new Cmd.LoadStatus(),
                        START.plusMillis(100)),
                new Cmd.LoadBranches(),
                START.plusMillis(400));

        // The branches ran too short to count.
        assertEquals(spinning(START.plusMillis(500), "Fetching origin +1"), rendered(model, START.plusMillis(500)));
    }

    @Test
    void whatShowedLingersOnceItIsDone() throws Exception {
        Instant done = START.plusSeconds(1);
        Model model = running(TestModels.loaded(), new FetchCmd.Fetch("origin"), START)
                .mapJobs(jobs -> jobs.withDone(0, done));

        assertEquals(spinning(done.plusMillis(299), "Fetching origin"), rendered(model, done.plusMillis(299)));
        assertEquals("", rendered(model, done.plus(Jobs.LINGER)));
    }

    @Test
    void aCommandThatShowedDoesNotLingerWhileAnotherShows() throws Exception {
        Instant done = START.plusSeconds(1);
        Model model = running(
                        running(TestModels.loaded(), new FetchCmd.Fetch("origin"), START),
                        new Cmd.LoadStatus(),
                        START.plusMillis(100))
                .mapJobs(jobs -> jobs.withDone(0, done));

        assertEquals(Option.none(), model.jobs().lingering());
        assertEquals(spinning(done, "Reading the status"), rendered(model, done));
    }

    @Test
    void aCommandThatNeverShowedDoesNotLinger() {
        Model model = running(TestModels.loaded(), new Cmd.LoadStatus(), START)
                .mapJobs(jobs -> jobs.withDone(0, START.plusMillis(299)));

        assertEquals(Option.none(), model.jobs().lingering());
    }

    @Test
    void comesBeforeHowFreshTheScreenIsWhichIsCutLast() throws Exception {
        Instant now = START.plusSeconds(1);
        Model model = running(
                TestModels.loaded()
                        .withNow(now)
                        .mapJobs(
                                jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", now.minusSeconds(120)))),
                new FetchCmd.Fetch("origin"),
                START);
        String spinner = TimeText.spinnerFrame(now) + " ";

        assertEquals(spinner + "Fetching origin · fetched 2m ago", rendered(model, now));
        // Only the spinner, a few columns of the label and the freshness fit.
        assertEquals(
                spinner + "Fe… · fetched 2m ago",
                rendered(model, now, TextWidth.of(spinner) + 3 + TextWidth.of(" · fetched 2m ago") + 3));
    }

    @Test
    void theLabelsOfCommandsAndOperations() {
        assertEquals("Working…", FetchLine.labelText(Label.WORKING));
        assertEquals(
                "Fetching origin",
                FetchLine.labelText(new FetchCmd.Fetch("origin").label().get()));
        assertEquals("Reading pull request #12345", FetchLine.labelText(Label.of("work.pullRequest", "12345")));
        assertEquals(
                "Push feature", FetchLine.labelText(Label.of("work.operation", Label.of("log.title.push"), "feature")));
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
